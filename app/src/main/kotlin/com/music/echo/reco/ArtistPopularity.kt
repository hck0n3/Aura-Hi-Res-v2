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
 * Fila 361 — ¿es un artista REAL? Oyentes de Last.fm por artista, para apartar los canales anónimos.
 *
 * 🔴 Dueño (2026-10-09): *"le pedí reggaeton cristiano y puso reggaeton cristiano pero de artistas que no son
 * conocidos, generados por IA"*. YouTube está lleno de canciones generadas por IA subidas por canales que
 * nadie escucha; la búsqueda las devuelve igual que a un artista de verdad. Last.fm cuenta oyentes reales:
 * un canal de IA tiene cero o casi cero; hasta un artista cristiano local tiene cientos.
 *
 * Una consulta por artista, guardada (el número cambia despacio; se vuelve a pedir pasados [REFRESH_DAYS]);
 * un fallo de red no se guarda. Mismas reglas de red y batería que [GenreCache]. Nada al registro (regla 4).
 */
object ArtistPopularity {
    private const val PREFS = "artist_lastfm_listeners"
    private const val MAX_BATCH = 16
    private const val MAX_CONSECUTIVE_FAILURES = 3
    private const val REFRESH_DAYS = 60L

    /** Below this many Last.fm listeners an artist counts as unknown (anonymous / AI-generated uploads). */
    const val MIN_LISTENERS = 100L

    private val failedThisSession: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** artist key → listeners. Stored as "listeners:epochDay". One disk read; off the main thread. */
    fun snapshot(context: Context): Map<String, Long> =
        prefs(context).all.mapNotNull { (k, v) ->
            (v as? String)?.substringBefore(':')?.toLongOrNull()?.let { k to it }
        }.toMap()

    /** True only when Last.fm ANSWERED and the artist has fewer than [MIN_LISTENERS]. Unknown → false. Pure. */
    fun isUnknownArtist(listeners: Map<String, Long>, artist: String?): Boolean {
        val n = listeners[ArtistStyleMemory.key(artist) ?: return false] ?: return false
        return n < MIN_LISTENERS
    }

    private fun hasNetwork(context: Context, wifiOnly: Boolean): Boolean {
        val cm = context.getSystemService<ConnectivityManager>() ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return !wifiOnly || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    suspend fun enrich(context: Context, artistNames: List<String>) = withContext(Dispatchers.IO) {
        if (!LastFM.isInitialized()) return@withContext
        val prefs = prefs(context)
        val today = System.currentTimeMillis() / 86_400_000L
        val pending = artistNames
            .mapNotNull { ArtistStyleMemory.key(it) }
            .distinct()
            .filter { key ->
                key !in failedThisSession &&
                    prefs.getString(key, null)?.substringAfter(':', "")?.toLongOrNull()
                        ?.let { today - it > REFRESH_DAYS } != false
            }
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
                        val n = LastFM.getArtistListeners(key)
                        if (n == null) {
                            failedThisSession.add(key)
                            failures.incrementAndGet()
                            null
                        } else {
                            failures.set(0)
                            key to n
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (results.isEmpty()) return@withContext
        prefs.edit().also { e -> results.forEach { (k, n) -> e.putString(k, "$n:$today") } }.apply()
    }
}
