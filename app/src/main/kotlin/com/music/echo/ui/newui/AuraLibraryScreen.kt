package iad1tya.echo.music.ui.newui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AiPlaylistEnabledKey
import iad1tya.echo.music.constants.ChipSortTypeKey
import iad1tya.echo.music.constants.FloatingToolbarBottomPadding
import iad1tya.echo.music.constants.LibraryFilter
import iad1tya.echo.music.constants.MiniPlayerBottomSpacing
import iad1tya.echo.music.constants.MiniPlayerHeight
import iad1tya.echo.music.constants.NavigationBarHeight
import iad1tya.echo.music.constants.OfflineModeKey
import iad1tya.echo.music.ui.component.AiPlaylistDialog
import iad1tya.echo.music.ui.component.CreatePlaylistDialog
import iad1tya.echo.music.ui.component.TextFieldDialog
import iad1tya.echo.music.ui.screens.DownloadedOnlyView
import iad1tya.echo.music.ui.screens.library.LocalSongScreen
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference

/**
 * # Biblioteca — "Interfaz nueva"
 *
 * The render's Biblioteca: title, a search glyph, the pill filter chips, and rows whose right edge
 * carries the quality badge or the download tick.
 *
 * ## Same shell, same state, same actions as the classic Library
 * The five tabs are not five routes — they are one screen switched by [ChipSortTypeKey], and this
 * screen reads and writes THAT key, so which tab you were on survives toggling "Interfaz nueva" in
 * either direction. The floating actions open the SAME `CreatePlaylistDialog` / `AiPlaylistDialog` /
 * `TextFieldDialog` with the same parse rules and the same navigation targets.
 *
 * ## Surfaces reused as-is
 *  · The **Local** tab is `LocalSongScreen` verbatim. It is its own inventory section (17.1.7) with a
 *    whole scan sheet — permissions, folder exclusions, a duration slider — and it is not one of the
 *    six screens in this beta.
 *  · The **dialogs** (create / AI / import by URL) are classic. They are modal surfaces, not screens.
 *
 * ## Not drawn here
 * The mini player and the bottom bar in the render's Biblioteca belong to the app skeleton.
 */
@Composable
fun AuraLibraryScreen(navController: NavController) {
    val offlineMode by rememberPreference(OfflineModeKey, false)
    if (offlineMode) {
        DownloadedOnlyView(navController = navController)
        return
    }

    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)

    var showImportMenu by remember { mutableStateOf(false) }
    var showYoutubeImportDialog by remember { mutableStateOf(false) }
    var showSpotifyImportDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showAiPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    // Hoisted here, not per tab, so the open/closed state still survives a tab switch exactly as it
    // did when the toggle lived in the header. `remember` keeps it one instance instead of a new
    // lambda per recomposition of the tab host.
    val toggleSearch = remember { { searchOpen = !searchOpen } }
    val (aiPlaylistEnabled) = rememberPreference(AiPlaylistEnabledKey, true)
    val context = LocalContext.current

    BackHandler(enabled = filterType != LibraryFilter.LIBRARY) {
        filterType = LibraryFilter.LIBRARY
    }

    // Follow the now-playing cover so Biblioteca picks up album-art colour like Home/Player.
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val bloom = rememberAuraBloom(mediaMetadata?.id)

    val isPlaylists = filterType == LibraryFilter.LIBRARY || filterType == LibraryFilter.PLAYLISTS

    // Same trick as the classic screen: on the tabs that show the floating buttons, extend the bottom
    // inset so the list can scroll clear of them.
    val currentInsets = LocalPlayerAwareWindowInsets.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val fabColumnClearance = 220.dp
    val paddedInsets = remember(currentInsets, density, layoutDirection) {
        object : WindowInsets {
            override fun getLeft(density: Density, layoutDirection: LayoutDirection) =
                currentInsets.getLeft(density, layoutDirection)

            override fun getTop(density: Density) = currentInsets.getTop(density)

            override fun getRight(density: Density, layoutDirection: LayoutDirection) =
                currentInsets.getRight(density, layoutDirection)

            override fun getBottom(density: Density) =
                currentInsets.getBottom(density) + with(density) { fabColumnClearance.roundToPx() }
        }
    }
    // The header + chip row are laid out ABOVE the tab list, so they must consume the top inset
    // themselves and the list must not add it a second time. (Before, nothing consumed it: the chips
    // were laid out at y = 0, i.e. behind the status bar and behind the top bar that used to be drawn
    // here, while each tab list still reserved the full top inset below them.)
    val topInsetOnly = remember(currentInsets) { currentInsets.only(WindowInsetsSides.Top) }
    val tabInsets = remember(paddedInsets, currentInsets, isPlaylists) {
        val source = if (isPlaylists) paddedInsets else currentInsets
        object : WindowInsets {
            override fun getLeft(density: Density, layoutDirection: LayoutDirection) =
                source.getLeft(density, layoutDirection)

            override fun getTop(density: Density) = 0

            override fun getRight(density: Density, layoutDirection: LayoutDirection) =
                source.getRight(density, layoutDirection)

            override fun getBottom(density: Density) = source.getBottom(density)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 0.40f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(topInsetOnly),
        ) {
            // Title on its own row so "Biblioteca" is never crushed by AuraTopActions (owner:
            // "Bibliot…"). Actions keep the trailing corner on a denser row above.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AuraSpacing.Gutter, end = AuraSpacing.Gutter, top = 8.dp),
            ) {
                AuraTopActions()
            }
            Text(
                text = stringResource(R.string.filter_library),
                style = AuraType.ScreenTitle,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    start = AuraSpacing.Gutter,
                    end = AuraSpacing.Gutter,
                    top = 4.dp,
                ),
            )
            // The chip row owns the FULL width. It used to share a Row with a search glyph pinned at
            // the right, and that glyph is the owner's "lupa que se ve superpuesta a los chips": the
            // LazyRow clips at its own right edge, which sat flush against the glyph, so a chip
            // scrolling past was cut in half directly under it. It also made the strip change width
            // between tabs, because only three of the five offer search. The glyph now travels with
            // the tab that owns it — see the sort row in AuraLibraryTabs.kt — and nothing is pinned
            // over the chips.
            LazyRow(
                contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                items(auraLibraryChips(), key = { it.first.name }) { (chip, labelRes) ->
                    AuraChip(
                        text = stringResource(labelRes),
                        selected = filterType == chip,
                        // Tapping the chip that is already active returns to the hub — as today.
                        onClick = {
                            filterType = if (filterType == chip) LibraryFilter.LIBRARY else chip
                        },
                    )
                }
            }

            CompositionLocalProvider(
                LocalPlayerAwareWindowInsets provides tabInsets,
            ) {
                Box(Modifier.fillMaxSize()) {
                    when (filterType) {
                        LibraryFilter.LIBRARY -> AuraLibraryHub(
                            navController,
                            onOpenLocal = { filterType = LibraryFilter.LOCAL },
                        )
                        LibraryFilter.PLAYLISTS ->
                            AuraLibraryPlaylistsTab(navController, searchOpen, toggleSearch)

                        LibraryFilter.SONGS ->
                            AuraLibrarySongsTab(navController, searchOpen, toggleSearch)

                        LibraryFilter.ALBUMS -> AuraLibraryAlbumsTab(navController)
                        LibraryFilter.ARTISTS ->
                            AuraLibraryArtistsTab(navController, searchOpen, toggleSearch)

                        // ── Local: the classic screen, but as a BODY and not as a second screen ──────
                        // Its scan sheet (permissions, excluded folders, a minimum-duration slider) is
                        // a whole surface of its own and is not one of the six screens in this beta, so
                        // the screen itself is still reused verbatim. What is NOT reused is its chrome:
                        // it used to draw its own LargeTopAppBar with a "Local" title, a back arrow and
                        // a gear directly under this screen's header — two headers, one on top of the
                        // other, which is what the beta verdict shows. `hideChrome` drops exactly that
                        // (search and the scan gear survive as a bare action row, see LocalSongScreen).
                        //
                        // The colour scheme is the second half of the same problem. The redesign paints
                        // its own dark ground and does NOT install a Material theme, so a user on the
                        // light theme got a classic screen rendering its text and surfaces in LIGHT
                        // colours on top of that dark ground — the "pantalla blanca" of the report. The
                        // embedded screen is therefore given a dark scheme anchored on the Aura palette
                        // for as long as it is embedded; nothing outside this branch sees it.
                        LibraryFilter.LOCAL -> MaterialTheme(
                            colorScheme = AuraEmbeddedScheme,
                            typography = MaterialTheme.typography,
                            shapes = MaterialTheme.shapes,
                        ) {
                            LocalSongScreen(
                                navController,
                                { filterType = LibraryFilter.LIBRARY },
                                isEmbedded = true,
                                hideChrome = true,
                            )
                        }
                    }
                }
            }
        }

        // Floating actions: AI-generate, create, and import a playlist by URL.
        if (isPlaylists) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(
                        end = 16.dp,
                        bottom = FloatingToolbarBottomPadding + NavigationBarHeight +
                            MiniPlayerBottomSpacing + MiniPlayerHeight + 16.dp,
                    ),
            ) {
                // ONE round "+" button (owner 2026-09-14: the three always-open pills covered the shelf
                // and the section title). Every action lives in the same floating Aura menu it opens.
                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                    AuraFab(
                        icon = AuraIcons.Plus,
                        label = null,
                        contentDescription = stringResource(R.string.create_playlist),
                        onClick = { showImportMenu = true },
                    )
                    val importMenuItems: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.create_playlist), color = AuraPalette.OnGround) },
                                leadingIcon = { AuraIconGlyph(AuraIcons.Plus, null, size = 20.dp, tint = AuraPalette.Teal) },
                                onClick = {
                                    showImportMenu = false
                                    showCreatePlaylistDialog = true
                                },
                            )
                            if (aiPlaylistEnabled) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ai_playlist_title), color = AuraPalette.OnGround) },
                                    leadingIcon = { AuraIconGlyph(AuraIcons.Radio, null, size = 20.dp, tint = AuraPalette.Teal) },
                                    onClick = {
                                        showImportMenu = false
                                        showAiPlaylistDialog = true
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.import_from_spotify),
                                        color = AuraPalette.OnGround,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_spotify),
                                        contentDescription = null,
                                        tint = AuraPalette.Teal,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    showImportMenu = false
                                    showSpotifyImportDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.import_from_youtube),
                                        color = AuraPalette.OnGround,
                                    )
                                },
                                leadingIcon = { AuraIconGlyph(AuraIcons.Video, null, size = 20.dp, tint = AuraPalette.Teal) },
                                onClick = {
                                    showImportMenu = false
                                    showYoutubeImportDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.migrate_playlist),
                                        color = AuraPalette.OnGround,
                                    )
                                },
                                leadingIcon = { AuraIconGlyph(AuraIcons.Export, null, size = 20.dp, tint = AuraPalette.Teal) },
                                onClick = {
                                    showImportMenu = false
                                    navController.navigate("migration")
                                },
                            )
                    }
                    // THE MINI-PLAYER GLASS (owner 2026-09-14): a popup window can't blur the app, so with
                    // the glass host the menu is a bottom glass panel like the player's "…" menu.
                    if (LocalOverlayHazeState.current != null && auraOverlayHostAvailable()) {
                        if (showImportMenu) {
                            AuraInWindowDialog(
                                visible = true,
                                onDismiss = { showImportMenu = false },
                                center = false,
                                fullHeight = false,
                            ) {
                                androidx.compose.foundation.layout.Column(
                                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 16.dp),
                                    content = importMenuItems,
                                )
                            }
                        }
                    } else {
                        DropdownMenu(
                            expanded = showImportMenu,
                            onDismissRequest = { showImportMenu = false },
                            shape = AuraShapes.Card,
                            containerColor = AuraPalette.FrostFill,
                            border = BorderStroke(1.dp, AuraPalette.SurfaceLine),
                            content = importMenuItems,
                        )
                    }
                }
            }
        }
    }

    // ── Dialogs: classic, verbatim, same parse rules and same destinations ────────────────────────

    if (showYoutubeImportDialog) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.link), contentDescription = null) },
            title = {
                Column {
                    Text(text = stringResource(R.string.import_youtube_playlist_title))
                    Text(
                        text = stringResource(R.string.import_youtube_playlist_hint),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            },
            initialTextFieldValue = TextFieldValue(""),
            autoFocus = true,
            onDismiss = { showYoutubeImportDialog = false },
            onDone = { finalUrl ->
                val listId = Regex("[?&]list=([a-zA-Z0-9_-]+)").find(finalUrl)?.groupValues?.get(1)
                if (listId != null) {
                    navController.navigate("online_playlist/$listId?autoSave=true")
                } else {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.invalid_playlist_url),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
                showYoutubeImportDialog = false
            },
        )
    }

    if (showSpotifyImportDialog) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.link), contentDescription = null) },
            title = {
                Column {
                    Text(text = stringResource(R.string.spotify_add_by_link))
                    Text(
                        text = stringResource(R.string.spotify_add_by_link_hint),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            },
            initialTextFieldValue = TextFieldValue(""),
            autoFocus = true,
            onDismiss = { showSpotifyImportDialog = false },
            onDone = { finalUrl ->
                val isSpotifyPlaylist =
                    Regex("playlist[:/]([A-Za-z0-9]{22})").containsMatchIn(finalUrl) ||
                        finalUrl.trim().matches(Regex("[A-Za-z0-9]{22}"))
                if (isSpotifyPlaylist) {
                    navController.navigate(
                        "settings/spotify_import?link=" + android.net.Uri.encode(finalUrl.trim()),
                    )
                } else {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.invalid_playlist_url),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
                showSpotifyImportDialog = false
            },
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            initialTextFieldValue = null,
            allowSyncing = true,
            onPlaylistCreated = { playlistId ->
                showCreatePlaylistDialog = false
                navController.navigate("local_playlist/$playlistId")
            },
        )
    }

    if (showAiPlaylistDialog) {
        AiPlaylistDialog(
            onDismiss = { showAiPlaylistDialog = false },
            onPlaylistCreated = { playlistId ->
                showAiPlaylistDialog = false
                navController.navigate("local_playlist/$playlistId")
            },
            onOpenAiSettings = {
                showAiPlaylistDialog = false
                navController.navigate("settings/ai")
            },
        )
    }
}

/**
 * The Material scheme a CLASSIC screen is rendered with while it is embedded in a redesigned one.
 *
 * The redesign draws with [AuraPalette] directly and never installs a Material theme, so a reused
 * classic surface keeps whatever scheme the app is on — light, for most users. Inside the Aura ground
 * that means dark text and pale containers over near-black: unreadable, and the reason the Local tab
 * looked like a separate white screen. Mapping the Material roles the reused screens actually read
 * onto the Aura palette makes them blend instead. Backgrounds are [AuraPalette.Ground] rather than
 * transparent on purpose: the sheets and menus these screens open must stay opaque.
 */
private val AuraEmbeddedScheme = darkColorScheme(
    primary = AuraPalette.Teal,
    onPrimary = AuraPalette.OnAccent,
    secondary = AuraPalette.Blue,
    onSecondary = AuraPalette.OnAccent,
    tertiary = AuraPalette.Violet,
    onTertiary = AuraPalette.OnGround,
    background = AuraPalette.Ground,
    onBackground = AuraPalette.OnGround,
    surface = AuraPalette.Ground,
    onSurface = AuraPalette.OnGround,
    surfaceVariant = AuraPalette.GroundRaised,
    onSurfaceVariant = AuraPalette.OnGroundMuted,
    surfaceContainerLowest = AuraPalette.Ground,
    surfaceContainerLow = AuraPalette.GroundRaised,
    surfaceContainer = AuraPalette.GroundRaised,
    surfaceContainerHigh = AuraPalette.GroundRaised,
    surfaceContainerHighest = AuraPalette.GroundRaised,
    outline = AuraPalette.OnGroundDisabled,
    outlineVariant = AuraPalette.SurfaceLine,
)

/** The five filter chips, in the classic order. */
private fun auraLibraryChips(): List<Pair<LibraryFilter, Int>> = listOf(
    LibraryFilter.PLAYLISTS to R.string.filter_playlists,
    LibraryFilter.SONGS to R.string.filter_songs,
    LibraryFilter.ALBUMS to R.string.filter_albums,
    LibraryFilter.ARTISTS to R.string.filter_artists,
    LibraryFilter.LOCAL to R.string.filter_local,
)

/**
 * A floating action in the new language: a `FloatingFill` pill with a hairline and a teal glyph.
 * [label] `null` draws the small round variant (the AI button); otherwise it is an extended pill.
 *
 * ## Why the fill is [AuraPalette.FloatingFill] and not [AuraPalette.SurfaceFill]
 * These three — "generar playlist con IA", "crear playlist", "importar listas de reproducción" — are
 * the only controls on this screen drawn ABOVE the scrolling list rather than inside it. The screen
 * root does paint an opaque ground (`auraScreenBackground`), so unlike the collapsed mini player these
 * were never compositing onto the NavHost; but the ground is not what is behind THEM. The playlist
 * grid is, and its cells are album covers. The render's 7 % white film over a bright cover is not a
 * surface: the artwork reads straight through the label, which is the owner's "muy transparentes".
 *
 * [AuraPalette.FloatingFill] is the same film pre-composited onto an opaque base, so it looks like the
 * render wherever the ground IS behind it and stays a surface wherever the ground is not. The owner
 * asked for a blur; the opaque plate was the 2026-08 answer because a live backdrop blur is what the
 * thermal contract forbids. The 2026-08-30 directive re-opens it with the pill's OWN technique, which
 * was never a backdrop sample: [AuraFabCoverSkin] paints the current track's cover — one static
 * 128×128 bitmap, blurred once with the native `Modifier.blur`, under the shell's frost tint — over
 * this plate, gated on the Liquid Glass master switch. OFF → the plate, hairline and glyph exactly as
 * they are today. No live sampling, no per-frame work, nothing the thermal contract forbids.
 */
@Composable
private fun AuraFab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // LIQUID GLASS REAL (owner directive 2026-08-31, UNIFIED CONTRACT): the FABs live INSIDE the
    // shell window and sample the SAME haze source the nav bar reads — ALWAYS LIVE, like the
    // chrome (the row-196 crash was the descendant pattern, gone; haze 1.7.2 carries the Samsung
    // shader fix; SimpMusic production pattern). No source (glass OFF / classic / previews) →
    // the opaque FloatingFill plate, hairline and glyph, byte-identical to before.
    // 🔴 EL CRISTAL INTERACTIVO AQUÍ CIERRA LA APP — NO VOLVER A INTENTARLO ASÍ.
    //
    // El punto 2 del dueño (2026-09-17) pedía *"aplicar un diseño interactivo estilo liquid glass"* a
    // este botón, y lo puse. La beta 2.0.45 se cerraba **al entrar a la biblioteca**.
    //
    // La causa es estructural y estaba media escrita aquí abajo: `MainActivity` aplica
    // `Modifier.layerBackdrop(appBackdrop)` **al NavHost**, y este FAB vive DENTRO del NavHost. La
    // receta `liquidGlassInteractive` llama a `drawBackdrop(LocalAppBackdrop.current)`, o sea que un
    // descendiente de la capa grabada intenta muestrear **la capa de la que él mismo forma parte** —
    // una recursión de dibujo. Con `shellGlass` el mismo caso es un no-op silencioso (por eso la nota
    // de abajo solo habla de "glifos desnudos"); con `drawBackdrop` se cae.
    //
    // Para que este botón tuviera cristal interactivo de verdad habría que sacarlo del NavHost al
    // nivel del shell, como la barra de navegación y el minirreproductor — que es exactamente por qué
    // esos dos sí pueden. Eso es mover el botón de sitio, no cambiarle el material, y no es lo que
    // pidió. Queda con la placa + haze de siempre.
    val shellHazeState = LocalShellHazeState.current
    val base = modifier
        .height(52.dp)
        .clip(AuraShapes.Pill)
        .then(
            if (shellHazeState != null) {
                // AUDIT 2026-08-31 (fix P3): these FABs live INSIDE the NavHost, i.e. DESCENDANTS
                // of the haze source — in haze 1.7.2 the descendant filter is a silent no-op (the
                // glass never draws) and the opaque plate was dropped when the glass arrived, so
                // the owner saw naked glyphs floating in a hole. The plate now paints UNDER the
                // glass: if the glass renders (non-descendant contexts) it covers the plate; if
                // it's a no-op, the plate IS the button. Same material as the no-source fallback.
                Modifier
                    .background(AuraPalette.FloatingFill)
                    .shellGlass(shellHazeState)
            } else {
                Modifier
                    .background(AuraPalette.FloatingFill)
                    .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Pill)
            },
        )
        .auraClickableInternal(onClick = onClick, contentDescription = contentDescription)

    if (label == null) {
        Box(base.size(52.dp), contentAlignment = Alignment.Center) {
            AuraIconGlyph(icon, null, size = 22.dp, tint = AuraPalette.Teal)
        }
    } else {
        Box(base, contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                AuraIconGlyph(icon, null, size = 20.dp, tint = AuraPalette.Teal)
                Text(
                    text = label,
                    style = AuraType.Chip,
                    color = AuraPalette.OnGround,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
        }
    }
}
