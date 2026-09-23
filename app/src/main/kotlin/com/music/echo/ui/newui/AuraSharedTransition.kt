
@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package iad1tya.echo.music.ui.newui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Ronda 5 (premium UX, dueño): la transición "hero" — la portada de un álbum crece visualmente desde
 * la tarjeta en Inicio hasta el header de AlbumScreen, en vez de que la navegación simplemente
 * reemplace la pantalla. Alcance decidido con el dueño (2026-09-23): SOLO álbum, SOLO desde Inicio,
 * para probarla en dispositivo antes de extenderla a Artista/Playlist/Biblioteca/Buscar — ver el
 * comentario en MainActivity.kt sobre por qué esto no se pudo verificar visualmente desde aquí.
 *
 * [SharedTransitionScope] solo existe dentro de un `SharedTransitionLayout` (armado una vez en
 * MainActivity.kt, envolviendo el NavHost — nunca se reconstruye por pantalla) y
 * [AnimatedVisibilityScope] solo existe dentro del `composable { }` de cada destino de Navigation
 * Compose. Ninguno de los dos llega por defecto a un composable anidado varios niveles adentro (una
 * tarjeta de portada dentro de un estante dentro de una pantalla), así que se publican una vez aquí y
 * cada composable que los necesita los lee — sin tener que agregar un parámetro nuevo a toda la cadena
 * de llamadas entre el NavHost y la tarjeta.
 *
 * `null` por defecto (no `error()`): un composable de portada puede vivir fuera de cualquier pantalla
 * navegable (por ejemplo dentro de un diálogo, o en un preview) y ahí simplemente no hay transición que
 * animar — [auraSharedAlbumCoverElement] ya maneja ese caso devolviendo el modifier sin tocar.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Publica ambos locals para todo lo que se componga dentro de [content] — se llama una vez por cada
 * destino de Navigation Compose que participe de una transición compartida (hoy: Inicio como origen,
 * Álbum como destino), pasando el propio `AnimatedVisibilityScope` que el `composable { }` de ese
 * destino ya trae como receptor.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ProvideNavSharedTransition(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSharedTransitionScope provides sharedTransitionScope,
        LocalNavAnimatedVisibilityScope provides animatedVisibilityScope,
        content = content,
    )
}

/**
 * La portada de ESTE álbum participa en la transición hero si — y solo si — hay un
 * [SharedTransitionScope] y un [AnimatedVisibilityScope] publicados (este destino/origen fue envuelto
 * con [ProvideNavSharedTransition]); si no, devuelve el modifier intacto — el mismo comportamiento de
 * siempre, sin animación, para cualquier tarjeta de portada que no participe todavía (que es casi
 * todas: el alcance de esta ronda es solo Inicio → Álbum).
 *
 * `sharedBounds`, no `sharedElement`: el origen (tarjeta cuadrada 1:1) y el destino (hero, a veces
 * banda ancha en pantallas grandes — ver AuraAlbumHero) no comparten la misma proporción, y
 * `sharedElement` asume que sí. `sharedBounds` anima el CONTENEDOR entre ambas formas; el contenido de
 * adentro (la imagen) se recorta a lo que quepa en cada instante, que es exactamente cómo se ve el
 * mismo gesto en Spotify/Apple Music.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.auraSharedAlbumCoverElement(albumId: String): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(sharedTransitionScope) {
        this@auraSharedAlbumCoverElement.sharedBounds(
            rememberSharedContentState(key = "aura-album-cover-$albumId"),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}
