package iad1tya.echo.music.ui.newui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.utils.YouTubeUrlParser
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalIsPlayerExpanded
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.OfflineModeKey
import iad1tya.echo.music.constants.PauseSearchHistoryKey
import iad1tya.echo.music.constants.SearchSource
import iad1tya.echo.music.constants.SearchSourceKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.SearchHistory
import iad1tya.echo.music.playback.PlayerConnection
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import timber.log.Timber
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * # Buscar — "Interfaz nueva"
 *
 * The fourth full screen of the redesign and, with Inicio, the most-visited one: it is a bottom-bar
 * tab that had never been rebuilt at all ("cuando toco el botón de inicio y paso al de buscar no me
 * sale personalizado").
 *
 * ## The classic Buscar is FOUR files, not one
 *  · `SearchScreen.kt` — the tab itself: the search bar, the mic, the biblioteca/en-línea source
 *    toggle, and three content tabs (Explorar / Sugerencias / Álbum).
 *  · `OnlineSearchScreen.kt` — the panel that replaces those tabs while the bar is active: search
 *    history (with a per-row delete), YouTube's own query suggestions, and the "mejor resultado"
 *    items. Rebuilt as [AuraOnlineSearchSuggestions].
 *  · `LocalSearchScreen.kt` — the same panel when the source is the LIBRARY: filter chips and live
 *    database results. Rebuilt as [AuraLocalSearchResults].
 *  · `OnlineSearchResult.kt` — the RESULTS route (`search/{query}`), with its own bar and its eight
 *    filter chips. Rebuilt as [AuraSearchResultScreen].
 *
 * All four are presentation only. The queries, the debounce, the link parsing, the history writes and
 * the podcast search all stay in `OnlineSearchSuggestionViewModel`, `LocalSearchViewModel`,
 * `OnlineSearchViewModel`, `MoodAndGenresViewModel`, `ExploreViewModel` and `SuggestionsViewModel` —
 * the same instances the classic screens use, so a fix lands once.
 *
 * ## What is deliberately different
 *  · **The source toggle is not drawn while "Modo sin conexión" is on.** The classic still draws it
 *    there and tapping it flips `SearchSourceKey` while `effectiveSource` stays LOCAL — a control that
 *    visibly changes nothing. Offline, the new bar shows a non-interactive [AuraIcons.CloudOff]
 *    marker instead; the stored preference is untouched and the toggle returns the moment offline mode
 *    is switched off.
 *  · **The field is focused when the panel opens.** The classic builds a `FocusRequester`, never
 *    attaches it to anything and then calls `requestFocus()` inside a `try/catch` that always throws —
 *    i.e. its "focus on first launch" has never worked. Rather than resurrect a behaviour nobody has
 *    ever seen, the new bar focuses the field when the user opens the panel, which is when a keyboard
 *    is actually wanted.
 */
@Composable
fun AuraSearchScreen(
    navController: NavController,
    pureBlack: Boolean,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isPlayerExpanded = LocalIsPlayerExpanded.current
    val playerConnection = LocalPlayerConnection.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
    // "Modo sin conexión" forces the entry to the local database and never hits the network — the same
    // term the classic screen computes, read from the same key.
    val offlineMode by rememberPreference(OfflineModeKey, false)
    val effectiveSource = if (offlineMode) SearchSource.LOCAL else searchSource
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var searchActive by rememberSaveable { mutableStateOf(false) }

    val closePanel = {
        searchActive = false
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    val onSearch: (String) -> Unit = { searchQuery ->
        focusManager.clearFocus()
        keyboardController?.hide()
        auraRunSearch(
            rawQuery = searchQuery,
            playerConnection = playerConnection,
            navController = navController,
            database = database,
            scope = coroutineScope,
            offlineMode = offlineMode,
            pauseSearchHistory = pauseSearchHistory,
            navigateToResults = { encoded -> navController.navigate("search/$encoded") },
        )
    }

    val voice = rememberAuraVoiceSearch(
        onPartial = { spoken ->
            query = TextFieldValue(spoken, TextRange(spoken.length))
        },
        onResult = { spoken ->
            query = TextFieldValue(spoken, TextRange(spoken.length))
            searchActive = true
            onSearch(spoken)
        },
    )

    // Closing the panel with Atrás instead of leaving the tab — the classic bar's back arrow does the
    // same thing, this only adds the system gesture to it.
    BackHandler(enabled = searchActive) { closePanel() }

    LaunchedEffect(searchActive) {
        if (searchActive) runCatching { focusRequester.requestFocus() }
    }

    // Owner directive 2026-09-04: tapping the Buscar tab of the bottom bar must land with the
    // keyboard OPEN and ready to type. Reuses the already-proven false→true path (the one the
    // magnifier tap takes) instead of seeding searchActive=true: the initial composition runs
    // before the FocusRequester is attached on the first frame, so seeding true would have the
    // requestFocus silently swallowed — no keyboard (the classic screen's never-worked focus all
    // over again, see the header note). This also re-arms on every tab re-entry (restoreState),
    // which is exactly "every time I land on Buscar, keyboard ready".
    LaunchedEffect(Unit) {
        searchActive = true
    }

    // HALLAZGO-049: seeded with the now-playing cover so Buscar follows the artwork live (owner:
    // palette on ALL screens); falls back to the brand bloom when nothing plays. The render dims
    // the bloom on the denser screens.
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val bloom = rememberAuraBloom(mediaMetadata?.id)

    val currentInsets = LocalPlayerAwareWindowInsets.current
    val topInsetOnly = remember(currentInsets) { currentInsets.only(WindowInsetsSides.Top) }
    // The header + bar + chips consume the top inset themselves; the body must not reserve it again.
    val bodyInsets = remember(currentInsets) {
        object : WindowInsets {
            override fun getLeft(density: Density, layoutDirection: LayoutDirection) =
                currentInsets.getLeft(density, layoutDirection)

            override fun getTop(density: Density) = 0

            override fun getRight(density: Density, layoutDirection: LayoutDirection) =
                currentInsets.getRight(density, layoutDirection)

            override fun getBottom(density: Density) = currentInsets.getBottom(density)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                // Pure black is a real user setting: drop the ground AND the bloom rather than paint
                // black over a bloom computed for nothing (same rule as the new Cola).
                if (pureBlack) Modifier.background(Color.Black)
                else Modifier.auraScreenBackground(bloom, intensity = 0.40f)
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(topInsetOnly),
        ) {
            AuraScreenHeader(
                title = stringResource(R.string.search),
                trailing = { AuraTopActions() },
            )

            AuraSearchInputBar(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(
                    when (effectiveSource) {
                        SearchSource.LOCAL -> R.string.search_library
                        SearchSource.ONLINE -> R.string.search_yt_music
                    }
                ),
                active = searchActive,
                onLeadingClick = {
                    if (searchActive) {
                        query = TextFieldValue("")
                        closePanel()
                    } else {
                        searchActive = true
                    }
                },
                onSubmit = {
                    onSearch(query.text)
                    // Offline: keep the panel open so the live library results stay visible.
                    if (!offlineMode) closePanel()
                },
                onClear = { query = TextFieldValue("") },
                onVoice = voice,
                offlineMode = offlineMode,
                sourceIsLocal = effectiveSource == SearchSource.LOCAL,
                onToggleSource = {
                    searchSource = if (searchSource == SearchSource.ONLINE) {
                        SearchSource.LOCAL
                    } else {
                        SearchSource.ONLINE
                    }
                },
                focusRequester = focusRequester,
                onFieldTap = { searchActive = true },
            )

            AnimatedVisibility(
                visible = !searchActive,
                enter = expandVertically(animationSpec = AuraMotion.intSize) +
                    fadeIn(animationSpec = AuraMotion.float),
                exit = shrinkVertically(animationSpec = AuraMotion.intSize) +
                    fadeOut(animationSpec = AuraMotion.float),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.Gutter, vertical = 2.dp),
                ) {
                    auraSearchTabs().forEachIndexed { index, labelRes ->
                        AuraChip(
                            text = stringResource(labelRes),
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                        )
                    }
                }
            }

            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides bodyInsets) {
                Box(Modifier.fillMaxSize()) {
                    if (searchActive) {
                        when (effectiveSource) {
                            SearchSource.LOCAL -> AuraLocalSearchResults(
                                query = query.text,
                                navController = navController,
                                onDismiss = { closePanel() },
                            )

                            SearchSource.ONLINE -> AuraOnlineSearchSuggestions(
                                query = query.text,
                                onQueryChange = { query = it },
                                navController = navController,
                                onSearch = { picked ->
                                    onSearch(picked)
                                    if (!offlineMode) closePanel()
                                },
                                onDismiss = { closePanel() },
                            )
                        }
                    } else {
                        when (selectedTabIndex) {
                            0 -> AuraExploreTab(navController)
                            1 -> AuraTrendingTab(navController)
                            else -> AuraNewAlbumsTab(navController)
                        }
                    }
                }
            }
        }
    }

    // Same keyboard hygiene as the classic screen: never leave a keyboard up behind the expanded
    // player, and drop focus when the app is backgrounded.
    DisposableEffect(lifecycleOwner, isPlayerExpanded) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (isPlayerExpanded) {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }

                Lifecycle.Event.ON_PAUSE -> {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (isPlayerExpanded) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/** The three content tabs of Buscar, in the classic order and with the classic labels. */
private fun auraSearchTabs(): List<Int> = listOf(
    R.string.tab_explore,
    R.string.tab_Suggestions,
    R.string.tab_album,
)

// ── The bar ───────────────────────────────────────────────────────────────────────────────────────

/**
 * The render's search field: a `SurfaceFill` card with a hairline, holding every control the classic
 * `SearchBar` carried — leading search/back glyph, the field itself, clear, mic, and the
 * biblioteca/en-línea source toggle.
 */
@Composable
internal fun AuraSearchInputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    active: Boolean,
    onLeadingClick: () -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onVoice: () -> Unit,
    focusRequester: FocusRequester,
    onFieldTap: () -> Unit,
    modifier: Modifier = Modifier,
    /** The results bar has no source picker — its route is online by definition. */
    showSource: Boolean = true,
    offlineMode: Boolean = false,
    sourceIsLocal: Boolean = false,
    onToggleSource: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter, vertical = 12.dp)
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .padding(start = 4.dp, end = 4.dp),
    ) {
        AuraIconButton(
            icon = if (active) AuraIcons.ChevronRight else AuraIcons.Search,
            contentDescription = if (active) {
                stringResource(R.string.dismiss)
            } else {
                stringResource(R.string.search)
            },
            onClick = onLeadingClick,
            size = 18.dp,
            tint = AuraPalette.OnGroundMuted,
            // The chevron points BACK when the panel is open — one glyph, rotated, exactly as the
            // library's close control reuses "+" at 45°.
            modifier = Modifier.graphicsLayer { rotationZ = if (active) 180f else 0f },
        )

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundMuted,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AuraType.RowTitle.copy(color = AuraPalette.OnGround),
                cursorBrush = SolidColor(AuraPalette.Teal),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    // Focusing the field IS opening the panel — no second clickable on top of the
                    // text field, which would swallow the tap that gives it focus in the first place.
                    .onFocusChanged { if (it.isFocused) onFieldTap() },
            )
        }

        if (value.text.isNotEmpty()) {
            AuraIconButton(
                // The render's own close glyph is a "+" turned 45°.
                icon = AuraIcons.Plus,
                contentDescription = stringResource(R.string.search_clear_query),
                onClick = onClear,
                size = 16.dp,
                tint = AuraPalette.OnGroundMuted,
                modifier = Modifier.graphicsLayer { rotationZ = 45f },
            )
        }

        AuraIconButton(
            icon = AuraIcons.Mic,
            contentDescription = stringResource(R.string.voice_search),
            onClick = onVoice,
            size = 18.dp,
            tint = AuraPalette.OnGroundMuted,
        )

        if (showSource) {
            if (offlineMode) {
                // Not a control: offline mode pins the source to the library, so a toggle here would
                // be a button that changes nothing on screen. This states WHY instead.
                AuraIconGlyph(
                    icon = AuraIcons.CloudOff,
                    contentDescription = stringResource(R.string.offline_mode),
                    size = 18.dp,
                    tint = AuraPalette.Teal,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            } else {
                AuraIconButton(
                    icon = if (sourceIsLocal) AuraIcons.Library else AuraIcons.Cloud,
                    contentDescription = stringResource(
                        if (sourceIsLocal) R.string.search_online else R.string.search_library
                    ),
                    onClick = onToggleSource,
                    size = 18.dp,
                    tint = if (sourceIsLocal) AuraPalette.Teal else AuraPalette.OnGroundFaint,
                )
            }
        }
    }
}

// ── Voice search ──────────────────────────────────────────────────────────────────────────────────

/**
 * Voice search: prefer in-process [SpeechRecognizer] + Aura listening UI (partial results fill the
 * search field live). [RecognizerIntent] is last resort when SpeechRecognizer is unavailable.
 * RECORD_AUDIO is requested on demand for the direct path.
 *
 * The dialog is emitted from here so both the Buscar bar and the results bar get it from one place.
 */
@Composable
internal fun rememberAuraVoiceSearch(
    onPartial: ((String) -> Unit)? = null,
    onResult: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current

    val intentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        if (!spoken.isNullOrEmpty()) onResult(spoken)
    }

    var listening by remember { mutableStateOf(false) }
    var liveText by remember { mutableStateOf("") }
    // Voice level 0..1 for the pulsing bars (owner directive 2026-09-04: "la animación del
    // micrófono quiero que se reproduzca mientras está reconociendo la voz — algo más
    // NOTABLE"). onRmsChanged feeds it live: the bars dance with the user's ACTUAL voice,
    // not a fake loop.
    var voiceLevel by remember { mutableStateOf(0f) }
    // Owner report (BETA-038, 2026-09-04): "no hay una animación para identificar que se está
    // usando". Even with the manifest query fixed, the listening dialog only appeared once the
    // OS recognizer SERVICE accepted the bind — a Samsung service that stalls leaves the tap
    // looking dead. "opening" is set the INSTANT the mic is tapped and drives the button's
    // animated state: instant, unconditional, independent of permissions/services/binds.
    var opening by remember { mutableStateOf(false) }
    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }
    DisposableEffect(recognizer) { onDispose { recognizer?.destroy() } }

    val startDirect = {
        val speech = recognizer
        if (speech == null) {
            opening = false
            Toast.makeText(context, R.string.voice_search_unavailable, Toast.LENGTH_SHORT).show()
        } else {
            speech.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    // The service accepted the session: the "opening" spinner hands off to the
                    // live-listening dialog. If the service never gets here, opening keeps the
                    // button alive and the 6s watchdog (below) tells the user what happened.
                    opening = false
                    listening = true
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) {
                    // Feed the pulsing bars: recognizer RMS arrives in dBFS (typically -2..-12
                    // while speaking, near -40 in silence). Map to 0..1 with a floor so silence
                    // still shows a calm idle pulse instead of dead bars.
                    voiceLevel = ((rmsdB + 24f) / 24f).coerceIn(0.06f, 1f)
                }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    listening = false
                    opening = false
                    liveText = ""
                    // ERROR_CLIENT also covers a failed service BIND (Samsung's recognizer
                    // dying mid-connection), not only the user closing the listening dialog —
                    // keep it silent for the user but leave the code in app.log so a device
                    // report can tell the two apart (numbers only, no user data).
                    if (error == SpeechRecognizer.ERROR_CLIENT) {
                        Timber.w("VoiceSearch: recognizer error ERROR_CLIENT (bind or user-cancel)")
                        return
                    }
                    Timber.w("VoiceSearch: recognizer error %d", error)
                    Toast.makeText(context, R.string.voice_search_unavailable, Toast.LENGTH_SHORT)
                        .show()
                }

                override fun onResults(results: android.os.Bundle) {
                    listening = false
                    liveText = ""
                    val spoken = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    if (!spoken.isNullOrEmpty()) onResult(spoken)
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val spoken = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (spoken.isEmpty()) return
                    liveText = spoken
                    onPartial?.invoke(spoken)
                }

                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            })
            liveText = ""
            // The dialog shows from this line — the instant the tap reached startListening —
            // not from the service's first callback. If the bind itself throws (broken Samsung
            // service), fall to the OS dialog instead of a dead silent button.
            listening = true
            try {
                speech.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    },
                )
            } catch (e: Exception) {
                Timber.w(e, "VoiceSearch: startListening threw, falling back to OS dialog")
                // AUDIT FIX (2026-09-04): reset opening too — leaving it true kept our dialog
                // pulsing UNDER the OS dialog until the 6s watchdog killed it with a confusing
                // "unavailable" toast even when the OS dialog had worked.
                listening = false
                opening = false
                try {
                    intentLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.voice_search_unavailable, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    // OWNER REPORT (2026-09-05, from the shared log: "recognizer error 7" + RECORD_AUDIO not
    // granted): a plain toast on denial is invisible to a normal user — the mic then reads as
    // "no disponible" forever with no way forward. On denial we now show an explicit dialog
    // with a one-tap deep link to the app's system permission settings (APPLICATION_DETAILS
    // SETTINGS). If the system prompt was permanently dismissed ("don't ask again"), this is
    // the ONLY path to the toggle — and it must be visible.
    var showMicPermissionDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startDirect()
        } else {
            opening = false
            showMicPermissionDialog = true
        }
    }

    if (showMicPermissionDialog) {
        iad1tya.echo.music.ui.component.DefaultDialog(
            onDismiss = { showMicPermissionDialog = false },
            icon = { Icon(painterResource(R.drawable.mic), contentDescription = null) },
            title = { Text(stringResource(R.string.mic_permission_needed_title)) },
            buttons = {
                TextButton(onClick = {
                    showMicPermissionDialog = false
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    }
                }) {
                    Text(stringResource(R.string.mic_permission_open_settings))
                }
                TextButton(onClick = { showMicPermissionDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            Text(
                stringResource(R.string.mic_permission_needed_body),
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundMuted,
            )
        }
    }

    val requestDirect = {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startDirect()
        } else {
            opening = true
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    // The unconditional tap feedback: opening is set HERE, the instant the mic is touched —
    // before any permission prompt, before any service bind. A stalled Samsung service can
    // no longer make the tap look dead.
    // AUDIT FIX (2026-09-04): this must go through requestDirect() — NOT startDirect(). The
    // e2185bb fix called startDirect directly, which left requestDirect/permissionLauncher as
    // DEAD CODE: a FIRST-TIME user (no RECORD_AUDIO yet) tapped the mic, startListening failed
    // with ERROR_INSUFFICIENT_PERMISSIONS and the OS permission prompt NEVER appeared — voice
    // search broken for every new user (invisible to the owner, whose permission is granted).
    // requestDirect already sets opening=true before launching the permission flow, so the
    // instant feedback is preserved.
    val launch = {
        if (recognizer != null) {
            requestDirect()
        } else {
            // No in-process recognizer visible to us: hand off to the OS dialog if any app can
            // handle it (still better than a silent nothing), opening stays as the feedback
            // until the result comes back.
            opening = true
            try {
                intentLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
                // The OS dialog is its own feedback; ours must not linger under it.
                opening = false
            } catch (e: android.content.ActivityNotFoundException) {
                opening = false
                Toast.makeText(context, R.string.voice_search_unavailable, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    // 6s watchdog: if the recognizer service accepted neither the session (onReadyForSpeech)
    // nor an error by then, the bind is stalled — say so instead of an eternally spinning
    // button. Numbers only in the log (no user data).
    LaunchedEffect(opening) {
        if (!opening) return@LaunchedEffect
        withTimeoutOrNull(6_000L) {
            snapshotFlow { opening }.first { !it }
        } ?: run {
            if (opening) {
                opening = false
                listening = false
                recognizer?.cancel()
                Timber.w("VoiceSearch: recognizer bind stalled >6s, cancelled")
                Toast.makeText(context, R.string.voice_search_unavailable, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    if (listening || opening) {
        DefaultDialog(
            onDismiss = {
                listening = false
                opening = false
                liveText = ""
                recognizer?.cancel()
            },
            title = {
                Text(
                    text = stringResource(R.string.voice_search),
                    style = AuraType.RowTitle,
                    color = AuraPalette.OnGround,
                )
            },
            buttons = {
                TextButton(onClick = {
                    listening = false
                    opening = false
                    liveText = ""
                    recognizer?.cancel()
                }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
            horizontalAlignment = Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pulsing voice bars (owner directive 2026-09-04: "algo más NOTABLE"): 5 bars
                // that dance with the user's ACTUAL voice level (onRmsChanged → voiceLevel),
                // with an infinite idle pulse so even silence shows a living animation.
                VoicePulseBars(level = voiceLevel, active = listening || opening)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        // Single label for both phases (opening → listening): the bars
                        // dialog is the animation the owner asked for — it appears the instant
                        // the mic is touched and stays until a result, an error or the cancel.
                        text = stringResource(R.string.listening),
                        style = AuraType.RowSubtitle,
                        color = AuraPalette.OnGroundMuted,
                    )
                    if (liveText.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = liveText,
                            style = AuraType.RowTitle,
                            color = AuraPalette.OnGround,
                        )
                    }
                }
            }
        }
    }

    return {
        launch()
    }
}

/**
 * Five rounded bars pulsing at staggered phases, scaled by the live voice [level] (0..1).
 * While [active] with no voice signal (opening, or a silent room) an infinite sine idle keeps
 * them visibly alive — the mic can never again look dead while the recognizer runs.
 */
@Composable
private fun VoicePulseBars(level: Float, active: Boolean) {
    val time by rememberInfiniteTransition(label = "voicePulse")
        .animateFloat(
            initialValue = 0f,
            targetValue = 2f * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
            ),
            label = "voicePulsePhase",
        )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.width(34.dp),
    ) {
        val baseHeights = listOf(10f, 18f, 26f, 18f, 10f)
        val phases = listOf(0f, 0.8f, 1.6f, 2.4f, 3.2f)
        baseHeights.forEachIndexed { i, base ->
            // Idle sine wave (always alive) + the live voice level on top; the voice dominates
            // whenever there is signal, the idle keeps motion in silence.
            val idle = (0.35f + 0.35f * kotlin.math.sin((time + phases[i]).toDouble()).toFloat())
            val height = (base * (idle * 0.5f + level * 0.9f)).coerceIn(6f, 34f)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        if (level > 0.35f) AuraPalette.Teal else AuraPalette.OnGroundMuted
                    ),
            )
        }
    }
}

// ── The one search action, shared by the bar and the results bar ───────────────────────────────────

/**
 * Submitting a query. Term for term the classic `onSearch`:
 *  · a YouTube VIDEO link plays straight away, an ARTIST link navigates to the artist,
 *  · anything else opens the results route — unless "Modo sin conexión" is on, in which case the panel
 *    already shows live library results and no network page is opened,
 *  · and the query is written to the history unless "Pausar el historial" is on.
 *
 * [navigateToResults] receives the URL-ENCODED query, because the two call sites navigate differently
 * (the tab pushes, the results screen replaces itself with `popUpTo`).
 */
internal fun auraRunSearch(
    rawQuery: String,
    playerConnection: PlayerConnection?,
    navController: NavController,
    database: MusicDatabase,
    scope: CoroutineScope,
    offlineMode: Boolean,
    pauseSearchHistory: Boolean,
    navigateToResults: (String) -> Unit,
) {
    if (rawQuery.isEmpty()) return

    when (val parsed = YouTubeUrlParser.parse(rawQuery)) {
        is YouTubeUrlParser.ParsedUrl.Video ->
            playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = parsed.id)))

        is YouTubeUrlParser.ParsedUrl.Artist ->
            navController.navigate("artist/${parsed.id}")

        null -> if (!offlineMode) {
            navigateToResults(URLEncoder.encode(rawQuery, "UTF-8"))
        }
    }

    if (!pauseSearchHistory) {
        scope.launch(Dispatchers.IO) {
            database.query { insert(SearchHistory(query = rawQuery)) }
        }
    }
}
