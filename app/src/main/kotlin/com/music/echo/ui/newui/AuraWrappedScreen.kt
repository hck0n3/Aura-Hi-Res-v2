package iad1tya.echo.music.ui.newui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.WrappedHomeDismissedYearKey
import iad1tya.echo.music.stats.ListeningStats
import iad1tya.echo.music.stats.StatsFormat
import iad1tya.echo.music.stats.WrappedBuilder
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog
import iad1tya.echo.music.utils.ComposeToImage
import iad1tya.echo.music.utils.WrappedImage
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.rememberReducedMotion
import iad1tya.echo.music.viewmodels.WrappedViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * # «Tu año en Aura» / «Tu mes» (registry row 910)
 *
 * A sequence of full-screen cards with the period's numbers, all computed on the phone ([WrappedViewModel],
 * [WrappedBuilder]): swipe, or tap the right two thirds to advance and the left third to go back; on a TV the D-pad's
 * left / right (and OK) do the same. With too little data the screen says so instead of showing empty cards.
 *
 * Nothing leaves the phone except the summary image, and only when the user taps «Compartir como imagen» and picks
 * where it goes in the system share sheet. Battery: the only animation is a short entrance of the card that has just
 * settled (none with reduced motion); nothing runs while a card stands still, and the background is a static gradient.
 */
@Composable
fun AuraWrappedScreen(
    navController: NavController,
    viewModel: WrappedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isTvOrCar = rememberIsTvOrCar()
    val background = Brush.verticalGradient(listOf(AuraPalette.Violet.copy(alpha = 0.55f), AuraPalette.Ground))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraPalette.Ground)
            .background(background),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
        ) {
            when (val s = state) {
                WrappedViewModel.State.Loading -> CircularProgressIndicator(
                    color = AuraPalette.Teal,
                    modifier = Modifier.align(Alignment.Center),
                )

                WrappedViewModel.State.Invalid -> AuraWrappedTooLittle(plays = 0, minutes = 0L)

                is WrappedViewModel.State.Done -> when (val story = s.story) {
                    is WrappedBuilder.Story.TooLittle -> AuraWrappedTooLittle(plays = story.plays, minutes = story.minutes)
                    is WrappedBuilder.Story.Ready -> AuraWrappedPager(period = s.period, cards = story.cards)
                }
            }

            AuraIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.close),
                onClick = {
                    ActionLog.tap(ActionId.WRAPPED_CLOSE)
                    navController.navigateUp()
                },
                size = 22.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .tvFocusable(isTvOrCar, CircleShape),
            )
        }
    }
}

@Composable
private fun AuraWrappedTooLittle(plays: Int, minutes: Long) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 72.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.wrapped_too_little_title), style = AuraWrappedBig, color = AuraPalette.OnGround)
        Text(
            stringResource(R.string.wrapped_too_little_body, WrappedBuilder.MIN_PLAYS, WrappedBuilder.MIN_MINUTES.toInt()),
            style = AuraType.CalloutSubtitle,
            color = AuraPalette.OnGroundMuted,
        )
        // What there is so far, so «too little» is a fact and not a guess.
        Text(
            pluralStringResource(R.plurals.wrapped_minutes, minutes.toInt(), minutes.toInt()) + " · " +
                pluralStringResource(R.plurals.n_time, plays, plays),
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundFaint,
        )
        Spacer(Modifier.weight(1f))
    }
}

private val AuraWrappedBig = AuraType.ScreenTitle.copy(fontSize = 40.sp, lineHeight = 46.sp)

@Composable
private fun AuraWrappedPager(period: WrappedBuilder.Period, cards: List<WrappedBuilder.Card>) {
    val pagerState = rememberPagerState(pageCount = { cards.size })
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val reducedMotion = rememberReducedMotion()

    fun go(delta: Int) {
        val target = (pagerState.currentPage + delta).coerceIn(0, cards.size - 1)
        if (target == pagerState.currentPage) return
        scope.launch {
            if (reducedMotion) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target)
        }
    }

    // The D-pad lands here, so left / right turn the cards from the first press. Once, at entry.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        ActionLog.tap(ActionId.WRAPPED_NEXT)
                        go(1)
                        true
                    }
                    Key.DirectionLeft -> {
                        ActionLog.tap(ActionId.WRAPPED_PREVIOUS)
                        go(-1)
                        true
                    }
                    else -> false
                }
            }
            // OK / Enter on the screen itself (a focused button handles its own first).
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                    ActionLog.tap(ActionId.WRAPPED_NEXT)
                    go(1)
                    true
                } else {
                    false
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        val progress = stringResource(R.string.wrapped_progress, pagerState.currentPage + 1, cards.size)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 64.dp, top = 28.dp)
                .semantics { contentDescription = progress },
        ) {
            cards.indices.forEach { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(if (index <= pagerState.currentPage) AuraPalette.Teal else AuraPalette.TrackEmpty),
                )
            }
        }

        val nextLabel = stringResource(R.string.wrapped_next)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            val settled = pagerState.settledPage == page
            val appear by animateFloatAsState(
                targetValue = if (settled) 1f else 0f,
                animationSpec = if (reducedMotion) snap() else tween(durationMillis = 420),
                label = "wrappedCard",
            )
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cards.size) {
                        detectTapGestures { offset ->
                            if (offset.x < size.width / 3f) {
                                ActionLog.tap(ActionId.WRAPPED_PREVIOUS)
                                go(-1)
                            } else {
                                ActionLog.tap(ActionId.WRAPPED_NEXT)
                                go(1)
                            }
                        }
                    }
                    .semantics { contentDescription = nextLabel }
                    .graphicsLayer {
                        alpha = 0.35f + 0.65f * appear
                        translationY = (1f - appear) * 40.dp.toPx()
                    }
                    .padding(horizontal = 28.dp, vertical = 24.dp),
            ) {
                AuraWrappedCard(card = cards[page], period = period)
            }
        }
    }
}

@Composable
private fun auraWrappedPeriodLabel(period: WrappedBuilder.Period): String {
    val month = period.month ?: return WrappedBuilder.yearArg(period.year)
    val locale = Locale.getDefault()
    return remember(month, locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(month) }
}

@Composable
private fun auraWrappedPeriodTitle(period: WrappedBuilder.Period): String =
    stringResource(if (period.kind == WrappedBuilder.Kind.YEAR) R.string.wrapped_year_title else R.string.wrapped_month_intro)

@Composable
private fun AuraWrappedCard(card: WrappedBuilder.Card, period: WrappedBuilder.Period) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        when (card) {
            WrappedBuilder.Card.Intro -> {
                AuraWrappedLabel(auraWrappedPeriodTitle(period))
                AuraWrappedHeadline(auraWrappedPeriodLabel(period))
                AuraWrappedDetail(stringResource(R.string.wrapped_intro_hint))
            }

            is WrappedBuilder.Card.Minutes -> {
                AuraWrappedLabel(stringResource(R.string.wrapped_minutes_title))
                AuraWrappedHeadline(pluralStringResource(R.plurals.wrapped_minutes, card.minutes.toInt(), card.minutes.toInt()))
                AuraWrappedDetail(
                    stringResource(R.string.wrapped_minutes_detail, card.plays, card.listeningDays, card.songs, card.artists),
                )
            }

            is WrappedBuilder.Card.TopArtists -> AuraWrappedTop(stringResource(R.string.wrapped_top_artists), card.entries, showSubtitle = false)
            is WrappedBuilder.Card.TopSongs -> AuraWrappedTop(stringResource(R.string.wrapped_top_songs), card.entries, showSubtitle = true)
            is WrappedBuilder.Card.TopAlbums -> AuraWrappedTop(stringResource(R.string.wrapped_top_albums), card.entries, showSubtitle = true)
            is WrappedBuilder.Card.TopGenres -> AuraWrappedTop(stringResource(R.string.wrapped_top_genres), card.entries, showSubtitle = false)

            is WrappedBuilder.Card.TopDay -> {
                val locale = Locale.getDefault()
                val day = remember(card.day.day, locale) {
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(card.day.day)
                }
                AuraWrappedLabel(stringResource(R.string.wrapped_top_day_title))
                AuraWrappedHeadline(day)
                AuraWrappedDetail(stringResource(R.string.wrapped_top_day_detail, StatsFormat.hoursMinutes(card.day.playTimeMs)))
            }

            is WrappedBuilder.Card.LongestStreak -> {
                val locale = Locale.getDefault()
                val span = remember(card.streak, locale) {
                    val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
                    "${f.format(card.streak.first)} – ${f.format(card.streak.last)}"
                }
                AuraWrappedLabel(stringResource(R.string.wrapped_streak_title))
                AuraWrappedHeadline(pluralStringResource(R.plurals.wrapped_streak_days, card.streak.days, card.streak.days))
                AuraWrappedDetail(span)
            }

            is WrappedBuilder.Card.NewArtists -> {
                AuraWrappedLabel(stringResource(R.string.wrapped_new_artists_title))
                AuraWrappedHeadline(card.count.toString())
                AuraWrappedDetail(stringResource(R.string.wrapped_new_artists_detail, card.count, card.first.name))
            }

            is WrappedBuilder.Card.PersonalityLine -> {
                AuraWrappedLabel(stringResource(R.string.wrapped_personality_title))
                AuraWrappedHeadline(stringResource(auraPersonalityName(card.personality)))
                AuraWrappedDetail(stringResource(auraPersonalityDescription(card.personality)))
            }

            is WrappedBuilder.Card.Final -> AuraWrappedFinal(card, period)
        }
    }
}

@Composable
private fun AuraWrappedTop(title: String, entries: List<ListeningStats.Entry>, showSubtitle: Boolean) {
    AuraWrappedLabel(title)
    entries.forEachIndexed { index, entry ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${index + 1}",
                style = AuraType.ScreenTitle,
                color = AuraPalette.Teal,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
                modifier = Modifier.width(44.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = AuraType.RowTitle, color = AuraPalette.OnGround, maxLines = 2, overflow = AuraDefaultOverflow)
                val subtitle = entry.subtitle
                if (showSubtitle && !subtitle.isNullOrBlank()) {
                    Text(subtitle, style = AuraType.RowSubtitle, color = AuraPalette.OnGroundMuted, maxLines = 1, overflow = AuraDefaultOverflow)
                }
            }
            Text(
                text = StatsFormat.hoursMinutes(entry.playTimeMs),
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundMuted,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun AuraWrappedFinal(card: WrappedBuilder.Card.Final, period: WrappedBuilder.Period) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTvOrCar = rememberIsTvOrCar()
    var sharing by remember { mutableStateOf(false) }

    val title = auraWrappedPeriodTitle(period)
    val subtitle = auraWrappedPeriodLabel(period)
    val lines = listOfNotNull(
        stringResource(R.string.wrapped_final_minutes, card.minutes.toString()),
        card.topArtist?.let { stringResource(R.string.wrapped_final_artist, it) },
        card.topSong?.let { stringResource(R.string.wrapped_final_song, it) },
        card.topGenre?.let { stringResource(R.string.wrapped_final_genre, it) },
        stringResource(R.string.wrapped_final_personality, stringResource(auraPersonalityName(card.personality))),
    )
    val footer = stringResource(R.string.app_name_aura) + " " + stringResource(R.string.app_name_hi_res)
    val top = AuraPalette.Violet.toArgb()
    val bottom = AuraPalette.Ground.toArgb()
    val accent = AuraPalette.Teal.toArgb()

    AuraWrappedLabel(title)
    AuraWrappedHeadline(subtitle)
    lines.forEach { AuraWrappedDetail(it) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(top = 12.dp)
            .clip(AuraShapes.Pill)
            .background(AuraPalette.Teal)
            .tvFocusable(isTvOrCar, AuraShapes.Pill)
            .auraClickableInternal(
                enabled = !sharing,
                onClick = {
                    ActionLog.tap(ActionId.WRAPPED_SHARE)
                    sharing = true
                    scope.launch {
                        try {
                            val image = WrappedImage.render(title, subtitle, lines, footer, top, bottom, accent)
                            val uri = withContext(Dispatchers.IO) {
                                ComposeToImage.saveBitmapAsFile(context, image, "aura_wrapped_${System.currentTimeMillis()}")
                            }
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, context.getString(R.string.wrapped_share_chooser)))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.wrapped_share_failed), Toast.LENGTH_SHORT).show()
                        } finally {
                            sharing = false
                        }
                    }
                },
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.share),
            contentDescription = null,
            tint = AuraPalette.OnAccent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.wrapped_share),
            style = AuraType.Chip,
            color = AuraPalette.OnAccent,
            maxLines = 1,
            overflow = AuraDefaultOverflow,
        )
    }
    AuraWrappedDetail(stringResource(R.string.wrapped_share_note))
}

@Composable
private fun AuraWrappedLabel(text: String) {
    Text(text = text, style = AuraType.CalloutTitle, color = AuraPalette.OnGroundMuted)
}

@Composable
private fun AuraWrappedHeadline(text: String) {
    Text(text = text, style = AuraWrappedBig, color = AuraPalette.OnGround)
}

@Composable
private fun AuraWrappedDetail(text: String) {
    Text(text = text, style = AuraType.CalloutSubtitle, color = AuraPalette.OnGround)
}

private fun auraPersonalityName(p: WrappedBuilder.Personality): Int = when (p) {
    WrappedBuilder.Personality.NIGHT_OWL -> R.string.wrapped_personality_night_owl
    WrappedBuilder.Personality.EARLY_BIRD -> R.string.wrapped_personality_early_bird
    WrappedBuilder.Personality.LOYAL_FAN -> R.string.wrapped_personality_loyal_fan
    WrappedBuilder.Personality.EXPLORER -> R.string.wrapped_personality_explorer
    WrappedBuilder.Personality.MARATHON -> R.string.wrapped_personality_marathon
    WrappedBuilder.Personality.ALL_ROUNDER -> R.string.wrapped_personality_all_rounder
}

private fun auraPersonalityDescription(p: WrappedBuilder.Personality): Int = when (p) {
    WrappedBuilder.Personality.NIGHT_OWL -> R.string.wrapped_personality_night_owl_desc
    WrappedBuilder.Personality.EARLY_BIRD -> R.string.wrapped_personality_early_bird_desc
    WrappedBuilder.Personality.LOYAL_FAN -> R.string.wrapped_personality_loyal_fan_desc
    WrappedBuilder.Personality.EXPLORER -> R.string.wrapped_personality_explorer_desc
    WrappedBuilder.Personality.MARATHON -> R.string.wrapped_personality_marathon_desc
    WrappedBuilder.Personality.ALL_ROUNDER -> R.string.wrapped_personality_all_rounder_desc
}

// ── Inicio: the December / early-January card ─────────────────────────────────────────────────────────────────────

/**
 * The «Tu año en Aura» card of Inicio: only in December and the first days of January ([WrappedBuilder.homeCardYear]),
 * only when that year has enough listens for a summary, and gone for that year once the user hides it (the year is
 * kept in [WrappedHomeDismissedYearKey]; next December it comes back). One COUNT on the local database when Inicio
 * opens in those weeks; nothing at all the rest of the year.
 */
@Composable
internal fun AuraWrappedHomeCard(navController: NavController, modifier: Modifier = Modifier) {
    var dismissedYear by rememberPreference(WrappedHomeDismissedYearKey, 0)
    val today = remember { LocalDate.now() }
    val year = WrappedBuilder.homeCardYear(today, dismissedYear.takeIf { it != 0 }) ?: return
    val database = LocalDatabase.current
    val plays by produceState<Int?>(initialValue = null, year) {
        val range = ListeningStats.year(year)
        value = withContext(Dispatchers.IO) {
            runCatching { database.statsPlayCountInRange(range.startStored, range.endStored) }.getOrNull()
        }
    }
    if ((plays ?: 0) < WrappedBuilder.MIN_PLAYS) return
    val isTvOrCar = rememberIsTvOrCar()
    val yearText = WrappedBuilder.yearArg(year)
    val title = stringResource(R.string.wrapped_home_title, yearText)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        AuraHomeInviteCard(
            title = title,
            subtitle = stringResource(R.string.wrapped_home_body),
            loading = false,
            contentDescription = title,
            onClick = {
                ActionLog.tap(ActionId.HOME_WRAPPED_OPEN)
                navController.navigateOnce("wrapped/$yearText")
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                painter = painterResource(R.drawable.auto_awesome),
                contentDescription = null,
                tint = AuraPalette.OnAccent,
                modifier = Modifier.size(AURA_QUICK_TILE_GLYPH),
            )
        }
        AuraIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.wrapped_home_dismiss),
            onClick = {
                ActionLog.tap(ActionId.HOME_WRAPPED_DISMISS)
                dismissedYear = year
            },
            modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
        )
    }
}

/** The two doors of Estadísticas: this year's summary and this month's ([WrappedBuilder.wrappedYear] / [wrappedMonth]). */
@Composable
internal fun AuraStatsWrappedEntries(navController: NavController, modifier: Modifier = Modifier) {
    val isTvOrCar = rememberIsTvOrCar()
    val today = remember { LocalDate.now() }
    val year = WrappedBuilder.yearArg(WrappedBuilder.wrappedYear(today))
    val month = WrappedBuilder.monthArg(WrappedBuilder.wrappedMonth(today))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter)
            .padding(top = 14.dp),
    ) {
        AuraChip(
            text = stringResource(R.string.wrapped_year_title) + " " + year,
            selected = false,
            onClick = {
                ActionLog.tap(ActionId.STATS_WRAPPED_YEAR)
                navController.navigateOnce("wrapped/$year")
            },
            modifier = Modifier.tvFocusable(isTvOrCar, AuraShapes.Pill, scaleFocused = 1f),
        )
        AuraChip(
            text = stringResource(R.string.wrapped_month_title),
            selected = false,
            onClick = {
                ActionLog.tap(ActionId.STATS_WRAPPED_MONTH)
                navController.navigateOnce("wrapped/$month")
            },
            modifier = Modifier.tvFocusable(isTvOrCar, AuraShapes.Pill, scaleFocused = 1f),
        )
    }
}
