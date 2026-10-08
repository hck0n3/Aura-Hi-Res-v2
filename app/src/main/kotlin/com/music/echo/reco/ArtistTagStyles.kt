package iad1tya.echo.music.reco

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import iad1tya.echo.music.constants.GenreEnrichOnMobileKey
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.lastfm.LastFM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fila 356 — el estilo de cada ARTISTA según las etiquetas de Last.fm (lo que sus oyentes llaman a su música).
 *
 * Por qué (dueño 2026-10-08, punto 1 de la mejora aprobada): iTunes pone a casi todos los artistas cristianos
 * latinos en "Christian & Gospel" o "Latin" y muchos títulos no dicen su estilo, así que la primera vez que
 * aparecía un artista la cola no sabía si era cumbia o salsa. Las etiquetas de Last.fm sí lo dicen
 * ("cumbia", "salsa", "reggaeton", "rock en español"…) y se piden UNA vez por artista: el resultado — un
 * estilo o "ninguno" — se guarda para siempre; un fallo de red no se guarda (se reintenta otro día).
 *
 * Mismas reglas de red y batería que [GenreCache]: lotes de [MAX_BATCH], 4 a la vez, se corta tras
 * [MAX_CONSECUTIVE_FAILURES] fallos seguidos, respeta "usar datos móviles". Nada va al registro (regla 4).
 */
object ArtistTagStyles {
    private const val PREFS = "artist_lastfm_style"
    private const val MAX_BATCH = 12
    private const val MAX_CONSECUTIVE_FAILURES = 3

    /** A concrete style must carry at least this share of the artist's style-tag weight… */
    const val MIN_SHARE = 0.6

    /** …and at least this much Last.fm weight (0-100) on its own, so one stray tag decides nothing. */
    const val MIN_WEIGHT = 10

    private val failedThisSession: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * The artist's style from its Last.fm tags, or null. Pure. Each tag is read like a title
     * ([MusicStyle.fromText]): "christian", "latin", "seen live" say no style and are ignored; the styles the
     * tags do name are summed by weight, and the leader must clearly lead.
     */
    fun styleFromTags(tags: List<Pair<String, Int>>): String? {
        val weights = HashMap<String, Int>()
        for ((tag, count) in tags) {
            val style = MusicStyle.fromText(tag) ?: continue
            weights[style] = (weights[style] ?: 0) + count.coerceAtLeast(1)
        }
        val total = weights.values.sum()
        if (total == 0) return null
        val (best, weight) = weights.maxByOrNull { it.value } ?: return null
        return if (weight >= MIN_WEIGHT && weight.toDouble() / total >= MIN_SHARE) best else null
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** artist key → style id; artists Last.fm had nothing for are left out. One disk read; off the main thread. */
    fun snapshot(context: Context): Map<String, String> =
        prefs(context).all.mapNotNull { (k, v) -> (v as? String)?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap()

    private fun hasNetwork(context: Context, wifiOnly: Boolean): Boolean {
        val cm = context.getSystemService<ConnectivityManager>() ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return !wifiOnly || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** Looks up the artists never asked before (bounded). Safe to call often; returns quickly when all known. */
    suspend fun enrich(context: Context, artistNames: List<String>) = withContext(Dispatchers.IO) {
        if (!LastFM.isInitialized()) return@withContext
        val prefs = prefs(context)
        val pending = artistNames
            .mapNotNull { ArtistStyleMemory.key(it) }
            .distinct()
            .filter { !prefs.contains(it) && it !in failedThisSession }
            .take(MAX_BATCH)
        if (pending.isEmpty()) return@withContext
        val allowMobile = context.dataStore.data.first()[GenreEnrichOnMobileKey] ?: true
        if (!hasNetwork(context, wifiOnly = !allowMobile)) return@withContext
        val sem = Semaphore(4)
        val failures = AtomicInteger(0)
        val results = coroutineScope {
            pending.map { key ->
                async {
                    sem.withPermit {
                        if (failures.get() >= MAX_CONSECUTIVE_FAILURES) return@withPermit null
                        val tags = LastFM.getArtistTopTags(key)
                        if (tags == null) {
                            failedThisSession.add(key)
                            failures.incrementAndGet()
                            null
                        } else {
                            failures.set(0)
                            key to (styleFromTags(tags) ?: "")
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (results.isEmpty()) return@withContext
        prefs.edit().also { e -> results.forEach { (k, style) -> e.putString(k, style) } }.apply()
    }
}
