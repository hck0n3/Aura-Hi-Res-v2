package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Fila 360 — WHICH cover the app's colours follow.
 *
 * 🔴 Dueño (2026-10-08): *"cada vez que entre a un álbum, playlist, single, donde sale el listado de
 * canciones, que tenga el color de la portada allí también; y si algo se está reproduciendo, que siempre
 * mantenga el color de la portada donde entro, a menos que esté en Inicio o en el reproductor: ahí muestra
 * el color de lo que se está reproduciendo"*.
 *
 * Until now every colour (ground, the moving lobes, accents, the cover-tinted ink) followed the song PLAYING,
 * so opening an album painted it with another record's colours. Now a screen with its own cover (album,
 * playlist, single, artist) declares it with [AuraCoverFocusEffect]; [AuraPaletteSync] and the screen's bloom
 * follow it while that screen is on top. Inicio declares nothing (now playing), and the full player always
 * wins ([playerExpanded]): opening the player over an album shows what is playing.
 *
 * Main-thread snapshot state; one owner at a time — the screen that entered last (navigating album → album
 * hands over cleanly, and a screen leaving never clears the focus of the one that replaced it).
 */
object AuraCoverFocus {
    data class Cover(val key: String, val url: String)

    private var owner: Any? = null
    private var screenCover by mutableStateOf<Cover?>(null)

    /** Mirrored by the Aura player sheet: while it is expanded the colours are the now-playing ones. */
    var playerExpanded by mutableStateOf(false)

    /** The cover the colours follow now, or null = the song playing. */
    val active: Cover? get() = if (playerExpanded) null else screenCover

    fun enter(cover: Cover, by: Any) {
        owner = by
        screenCover = cover
    }

    fun leave(by: Any) {
        if (owner !== by) return
        owner = null
        screenCover = null
    }

    /** Cache key of a screen cover — distinct from media ids so a cover is extracted once per url. */
    fun keyOf(url: String): String = "cover:$url"
}

/** Declares this screen's own cover as the one the app's colours follow while the screen is shown. */
@Composable
fun AuraCoverFocusEffect(coverUrl: String?) {
    DisposableEffect(coverUrl) {
        val token = Any()
        if (!coverUrl.isNullOrBlank()) {
            AuraCoverFocus.enter(AuraCoverFocus.Cover(AuraCoverFocus.keyOf(coverUrl), coverUrl), token)
        }
        onDispose { AuraCoverFocus.leave(token) }
    }
}

/**
 * The bloom of a screen that has its own cover: that cover's colours, or — while it has none — the song
 * playing, exactly like [rememberAuraBloom].
 */
@Composable
fun rememberAuraBloomForCover(coverUrl: String?, nowPlayingId: String?): AuraBloomState =
    if (coverUrl.isNullOrBlank()) {
        rememberAuraBloom(nowPlayingId)
    } else {
        rememberAuraBloomFromUrl(AuraCoverFocus.keyOf(coverUrl), coverUrl)
    }
