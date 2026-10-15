package iad1tya.echo.music.ui.newui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.component.PlayerProgressSlider
import iad1tya.echo.music.ui.screens.Screens
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.makeTimeString
import kotlinx.coroutines.delay

/**
 * # Chrome de pantalla ancha de Aura — riel de navegación y panel «sonando ahora»
 *
 * 🔴 Dueño (2026-10-15): *«en el modo plegable o pantallas grandes o tablets no me gusta la interfaz, no se
 * parece a la interfaz normal; quiero que adecúes la interfaz plegable a lo mejor que hay en la actualidad»*.
 *
 * ## Causa raíz
 * Con la interfaz nueva encendida, una ventana ancha (plegable abierto, tableta, móvil en horizontal) seguía
 * dibujando el **riel de navegación de Material** (`AppNavigationRail`) y el **panel lateral clásico**
 * (`NowPlayingSidePanel`): superficies grises de Material, un `Slider` plano y botones rellenos, justo al
 * lado de pantallas Aura con fondo de portada. Las dos piezas existían solo en su versión clásica, y el
 * resto de la interfaz ancha (el reproductor dividido, el listado de cola) ya era Aura: de ahí el «no se
 * parece a la normal».
 *
 * ## Qué hay aquí (el patrón de Spotify / YouTube Music / Apple Music en tableta)
 *  · [AuraNavigationRail] — la barra de abajo girada a vertical: las MISMAS celdas (glifo + etiqueta),
 *    la misma tinta y la misma pastilla indicadora, sobre el suelo del color de la portada.
 *  · [AuraNowPlayingPanel] — el panel persistente de la derecha: portada grande, título y artista con la
 *    tipografía del reproductor, la línea de tiempo con los colores de la portada (la MISMA
 *    [PlayerProgressSlider] que el reproductor y la píldora) y el transporte Aura.
 *
 * Nada de esto cambia QUÉ hace cada control: las acciones son las que ya tenían el riel y el panel
 * clásicos (`onItemClick` del shell, `playerConnection.*`). Solo cambia cómo se dibujan, y solo con la
 * interfaz nueva encendida: con ella apagada MainActivity sigue llamando a los clásicos.
 *
 * ## Calor / batería (regla 7)
 * El fondo se pinta en la fase de DIBUJO leyendo [AuraPalette] (un cambio de portada repinta, no
 * recompone). El panel muestrea la posición cada 500 ms, igual que el clásico, y no tiene reloj propio:
 * la animación de la línea de tiempo cuelga del reloj ambiental compartido.
 */

/** Ancho del riel. 80 dp = el de `NavigationRail` de Material, que es el que `MainActivity` ya reserva. */
val AuraRailWidth = 80.dp

/**
 * Riel de navegación vertical de Aura.
 *
 * @param items los MISMOS `navigationItems` que recibe la barra de abajo.
 * @param isSelected misma regla que la barra de abajo.
 * @param onItemClick `(destino, yaSeleccionado)` — el contrato de siempre del shell.
 * @param onSearchLongClick pulsación larga en Buscar (reconocimiento de música), como en el riel clásico.
 */
@Composable
fun AuraNavigationRail(
    items: List<Screens>,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSearchLongClick: (() -> Unit)? = null,
) {
    // El shell lo compone `MainActivity` fuera de cualquier puerta: idempotente, ver [AuraNavigationBar].
    AuraPaletteSync()
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(AuraRailWidth)
            // Suelo + filete en la fase de dibujo: un cambio de portada repinta, no recompone el riel.
            .drawBehind {
                drawRect(AuraPalette.Ground)
                val hairline = 1.dp.toPx()
                drawRect(
                    color = AuraPalette.Divider,
                    topLeft = Offset(size.width - hairline, 0f),
                    size = Size(hairline, size.height),
                )
            }
            .windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        items.forEach { screen ->
            val selected = isSelected(screen)
            val isSearch = screen == Screens.Search && onSearchLongClick != null
            AuraRailItem(
                icon = auraRailIcon(screen),
                label = stringResource(screen.titleId),
                selected = selected,
                onClick = { onItemClick(screen, selected) },
                onLongClick = if (isSearch) onSearchLongClick else null,
            )
        }
    }
}

/** Destino → glifo. Exhaustivo a propósito, como [AuraNavigationBar]: un destino nuevo sin glifo no compila. */
private fun auraRailIcon(screen: Screens): ImageVector = when (screen) {
    Screens.Home -> AuraIcons.Home
    Screens.Search -> AuraIcons.Search
    Screens.Library -> AuraIcons.Library
    Screens.ListenTogether -> AuraIcons.People
    Screens.Novedades -> AuraIcons.Novedades
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AuraRailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) AuraPalette.Teal else AuraPalette.NavInactive,
        animationSpec = AuraMotion.color(),
        label = "auraRailTint",
    )
    val pill by animateColorAsState(
        targetValue = if (selected) AuraPalette.NavIndicator else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = AuraMotion.color(),
        label = "auraRailPill",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(64.dp)
            .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
            .tvFocusable(rememberIsTvOrCar(), AuraShapes.Highlight, scaleFocused = 1f)
            .clip(AuraShapes.Highlight)
            .drawBehind { drawRect(pill) }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.foundation.LocalIndication.current,
                onClickLabel = label,
                role = Role.Tab,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics { this.selected = selected }
            .padding(vertical = 8.dp),
    ) {
        AuraIconGlyph(icon = icon, contentDescription = null, size = 24.dp, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = AuraType.NavLabel,
            color = tint,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = AuraDefaultOverflow,
        )
    }
}

/**
 * Panel persistente «sonando ahora» de las pantallas anchas, en lenguaje Aura. Tocar la portada abre el
 * reproductor completo ([onExpand]). No dibuja nada si no hay canción.
 */
@Composable
fun AuraNowPlayingPanel(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val meta = mediaMetadata ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val liked = currentSong?.song?.liked == true
    val disliked by playerConnection.currentSongDisliked.collectAsState()
    val isTvOrCar = rememberIsTvOrCar()
    AuraPaletteSync()

    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    // 500 ms, like the classic panel: this surface is on screen for the whole session of a wide window.
    LaunchedEffect(playerConnection, meta.id) {
        while (true) {
            position = playerConnection.player.currentPosition.coerceAtLeast(0L)
            duration = playerConnection.player.duration.let { if (it > 0L) it else 0L }
            delay(500)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .drawBehind {
                drawRect(AuraPalette.GroundRaised)
                val hairline = 1.dp.toPx()
                drawRect(
                    color = AuraPalette.Divider,
                    topLeft = Offset(0f, 0f),
                    size = Size(hairline, size.height),
                )
            }
            .windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.End),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(AuraShapes.Card)
                    .background(AuraPalette.coverPlaceholder(meta.id))
                    // tvFocusable BEFORE clickable (its onFocusChanged must be an ancestor of the target).
                    .tvFocusable(isTvOrCar, AuraShapes.Card)
                    .clickable(onClick = onExpand),
            ) {
                AuraStableCoverImage(
                    url = meta.thumbnailUrl,
                    contentScale = ContentScale.Crop,
                    decodeTo = 512,
                    seed = meta.id,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = meta.title,
                style = AuraType.PlayerTitle,
                color = AuraPalette.OnGround,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = AuraDefaultOverflow,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = meta.artists.joinToString { it.name },
                style = AuraType.PlayerArtist,
                color = AuraPalette.OnGroundMuted,
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = AuraDefaultOverflow,
            )

            Spacer(Modifier.height(10.dp))
            val safeDuration = duration.coerceAtLeast(1L)
            val shownPosition = (sliderPosition ?: position).coerceIn(0L, safeDuration)
            // The SAME shared timeline the full player and the pill use: one implementation, the user's
            // «Estilo de la barra de progreso» and the cover-colour paint apply here too.
            PlayerProgressSlider(
                modifier = Modifier.tvFocusable(isTvOrCar, AuraShapes.Highlight),
                value = shownPosition.toFloat(),
                valueRange = 0f..safeDuration.toFloat(),
                onValueChange = { sliderPosition = it.toLong() },
                onValueChangeFinished = {
                    sliderPosition?.let { playerConnection.seekTo(it) }
                    sliderPosition = null
                },
                colors = auraSliderColors(),
                isPlaying = isPlaying,
                activeTrackPaint = remember(isPlaying) { AuraTimelinePaint(animated = isPlaying) },
                // Never null on the DEFAULT style: null swaps the rail for Material's native, thicker one.
                bufferedFraction = 0f,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AuraTechnicalText(text = makeTimeString(shownPosition), style = AuraType.Timecode)
                AuraTechnicalText(
                    text = if (duration > 0L) makeTimeString(duration) else "",
                    style = AuraType.Timecode,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AuraIconButton(
                    icon = AuraIcons.ThumbDown,
                    contentDescription = stringResource(R.string.action_dislike),
                    // Toggle + live state, parity with the player's thumbs.
                    onClick = { playerConnection.toggleDislikeCurrentSong() },
                    size = 20.dp,
                    tint = if (disliked) AuraPalette.Teal else AuraPalette.OnGround.copy(alpha = 0.7f),
                    modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                )
                AuraIconButton(
                    icon = AuraIcons.SkipPrevious,
                    contentDescription = stringResource(R.string.previous),
                    onClick = { playerConnection.seekToPrevious() },
                    enabled = canSkipPrevious,
                    size = 30.dp,
                    tint = AuraPalette.OnGround.copy(alpha = 0.9f),
                    modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                )
                AuraPlayButton(
                    diameter = 60.dp,
                    isPlaying = isPlaying,
                    // PlayerConnection.togglePlayPause() routes to the cast device when casting.
                    onClick = { playerConnection.togglePlayPause() },
                    modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                )
                AuraIconButton(
                    icon = AuraIcons.SkipNext,
                    contentDescription = stringResource(R.string.next),
                    onClick = { playerConnection.seekToNext() },
                    enabled = canSkipNext,
                    size = 30.dp,
                    tint = AuraPalette.OnGround.copy(alpha = 0.9f),
                    modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                )
                AuraIconButton(
                    icon = if (liked) AuraIcons.HeartFilled else AuraIcons.Heart,
                    contentDescription = stringResource(R.string.action_like),
                    onClick = { playerConnection.toggleLike() },
                    size = 20.dp,
                    tint = if (liked) AuraPalette.Teal else AuraPalette.OnGround.copy(alpha = 0.7f),
                    modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                )
            }
        }
    }
}

/** Widest window width the Inicio card scale follows — a large phone. Beyond it cards stop growing. */
internal val AURA_HOME_REFERENCE_MAX_WIDTH = 430.dp

/**
 * Columns of a "see all" grid (favourite albums, an artist's albums/videos…). Two on a phone — the look that
 * shipped — and more as the pane grows, so a card keeps about its phone size instead of stretching to a
 * 300 dp tile on an unfolded foldable. Video-heavy lists (wide 16:9 cards) are one column on a phone.
 * Pure, for test.
 */
internal fun auraSeeAllColumns(paneWidth: androidx.compose.ui.unit.Dp, videoHeavy: Boolean = false): Int {
    val spacing = 14.dp
    val available = paneWidth - AuraSpacing.Gutter * 2
    val minCard = if (videoHeavy) 300.dp else 168.dp
    val fit = ((available + spacing) / (minCard + spacing)).toInt()
    return fit.coerceAtLeast(if (videoHeavy) 1 else 2)
}
