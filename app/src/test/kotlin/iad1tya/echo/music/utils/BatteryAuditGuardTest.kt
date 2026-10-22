package iad1tya.echo.music.utils

import iad1tya.echo.music.ui.newui.AURA_RHYTHM_SETTLED_EPSILON
import iad1tya.echo.music.ui.newui.AURA_RHYTHM_STEPS
import iad1tya.echo.music.ui.newui.auraRhythmSettled
import iad1tya.echo.music.utils.sabr.EjsSolverIdlePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Battery / heat audit (2026-10-20, owner: «Galaxy la reporta como la app que más consume batería, un 33 %»).
 *
 * Each test pins one thing that was FIXED, so a later "simplification" cannot quietly put it back: the pure policies
 * by value, and the wiring (which compiles and looks right either way) by reading the source.
 */
class BatteryAuditGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun src(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    // ── EJS solver WebView: released after a quiet spell ───────────────────────────────────────────────────────

    @Test
    fun `the EJS solver is released after ten idle minutes and not before`() {
        assertEquals(10 * 60 * 1000L, EjsSolverIdlePolicy.IDLE_RELEASE_MS)
        assertFalse(EjsSolverIdlePolicy.releaseDue(0L))
        assertFalse(EjsSolverIdlePolicy.releaseDue(EjsSolverIdlePolicy.IDLE_RELEASE_MS - 1))
        assertTrue(EjsSolverIdlePolicy.releaseDue(EjsSolverIdlePolicy.IDLE_RELEASE_MS))
        assertTrue(EjsSolverIdlePolicy.releaseDue(6 * 60 * 60 * 1000L))
    }

    @Test
    fun `the idle window never trips while songs keep being resolved`() {
        // Streaming resolves a song every few minutes; the window must stay comfortably above that so the hot path
        // never pays a rebuild, and far below the 6 h age limit it complements.
        val perSong = 5 * 60 * 1000L
        assertTrue(EjsSolverIdlePolicy.IDLE_RELEASE_MS > perSong)
        assertTrue(EjsSolverIdlePolicy.IDLE_RELEASE_MS < 30 * 60 * 1000L)
    }

    @Test
    fun `the idle release runs under the solver mutex and every solve restarts the clock`() {
        val text = src("utils/sabr/EjsNTransformSolver.kt")
        val release = text.substringAfter("private suspend fun releaseIfIdle()").substringBefore("\n    }\n")
        assertTrue("the release must take solverMutex (a solve in flight finishes first)", release.contains("solverMutex.withLock"))
        assertTrue(release.contains("EjsSolverIdlePolicy.releaseDue("))
        val solve = text.substringAfter("private suspend fun solve(kind: String, value: String)").substringBefore("// Callers MUST hold [solverMutex]. The double-check")
        assertTrue("solve must re-arm the idle clock under the lock", solve.indexOf("armIdleRelease()") > solve.indexOf("solverMutex.withLock"))
        assertTrue("close() must stop the idle job", text.substringAfter("suspend fun close()").substringBefore("// ── Idle release").contains("idleJob?.cancel()"))
    }

    // ── Apple Music token: back off after a failed extraction ──────────────────────────────────────────────────

    @Test
    fun `the Apple token retry curve is 5, 15, 45 minutes then an hour at most`() {
        assertEquals(0L, AppleTokenRetryPolicy.retryAfterMs(0))
        assertEquals(5 * 60_000L, AppleTokenRetryPolicy.retryAfterMs(1))
        assertEquals(15 * 60_000L, AppleTokenRetryPolicy.retryAfterMs(2))
        assertEquals(45 * 60_000L, AppleTokenRetryPolicy.retryAfterMs(3))
        assertEquals(60 * 60_000L, AppleTokenRetryPolicy.retryAfterMs(4))
        assertEquals(60 * 60_000L, AppleTokenRetryPolicy.retryAfterMs(500))
    }

    @Test
    fun `all three Apple token providers remember a failure instead of re-downloading the web player`() {
        val app = src("utils/AppleMusicTokenProvider.kt")
        assertTrue(app.contains("nextAttemptAtMs"))
        assertTrue("the guard must come before the network fetch", app.indexOf("< nextAttemptAtMs") in 1 until app.indexOf("httpClient.get("))
        for (module in listOf("canvas/src/main/kotlin/com/music/echo/canvas", "applecanvas/src/main/kotlin/com/music/echo/applecanvas")) {
            val text = File(root, "$module/AppleMusicTokenProvider.kt").readText()
            assertTrue("$module: back-off state", text.contains("lastFailureAtMs"))
            assertTrue("$module: guard before the fetch", text.indexOf("< RETRY_AFTER_MS") in 1 until text.indexOf("httpClient.get("))
        }
    }

    // ── The rhythm frame loop stops when there is nothing to animate ──────────────────────────────────────────

    @Test
    fun `the rhythm loop settles only when paused and decayed to nothing`() {
        assertFalse("playing is never settled", auraRhythmSettled(playing = true, smoothed = 0f))
        assertFalse(auraRhythmSettled(playing = false, smoothed = 0.2f))
        assertTrue(auraRhythmSettled(playing = false, smoothed = 0f))
        assertTrue(auraRhythmSettled(playing = false, smoothed = AURA_RHYTHM_SETTLED_EPSILON / 2))
        assertFalse(auraRhythmSettled(playing = false, smoothed = AURA_RHYTHM_SETTLED_EPSILON))
    }

    @Test
    fun `settling is invisible because it sits below the first quantization step`() {
        // quantizeAuraRhythmIntensity rounds to bucket 0 until the level reaches half a bucket.
        assertTrue(AURA_RHYTHM_SETTLED_EPSILON < 0.5f / AURA_RHYTHM_STEPS)
    }

    // ── Foreground gating of delay()-driven loops (Compose's paused frame clock does not stop them) ───────────

    @Test
    fun `the position tickers only run while the app is on screen`() {
        val aura = src("ui/newui/AuraPlayer.kt")
        val auraTicker = aura.substringAfter("val appInForeground = rememberIsAppInForeground()").substringBefore("Owner directive 2026-09-13")
        assertTrue(auraTicker.contains("LaunchedEffect(isPlaying, isCasting, highPerfMode, appInForeground)"))
        assertTrue(auraTicker.contains("isPlaying && appInForeground"))

        val classic = src("ui/player/Player.kt")
        val classicTicker = classic.substringAfter("val positionTickerInForeground = rememberIsAppInForeground()").substringBefore("Mirror the player sheet's expanded")
        assertTrue(classicTicker.contains("positionTickerInForeground)"))
        assertTrue(classicTicker.contains("isPlaying && positionTickerInForeground"))

        val panel = src("ui/player/NowPlayingSidePanel.kt")
        assertTrue(panel.contains("LaunchedEffect(playerConnection, meta.id, panelInForeground)"))
    }

    @Test
    fun `the rhythm loop ends when settled instead of asking for frames forever`() {
        val text = src("ui/newui/AuraRhythm.kt")
        assertTrue(text.contains("while (isActive && !settled)"))
        assertTrue(text.contains("settled = auraRhythmSettled(playing, smoothed)"))
    }

    @Test
    fun `animated cover lookups wait for the app to be on screen and never wedge their in-flight flag`() {
        for (path in listOf("ui/player/Thumbnail.kt", "ui/player/Player.kt")) {
            val text = src(path)
            val effect = text.substringAfter("CanvasArtworkPlaybackCache.get(").substringBefore("withContext(Dispatchers.IO)")
            assertTrue("$path: lookup gated on the foreground", effect.contains("InForeground) return@LaunchedEffect") || effect.contains("InForeground)) return@LaunchedEffect"))
            assertFalse("$path: the old guard wedged a run cancelled mid-fetch", effect.contains("if (canvasFetchInFlight) return@LaunchedEffect"))
        }
    }

    @Test
    fun `the cache-list refresh loop parks while the app is off screen`() {
        val vm = src("viewmodels/CachePlaylistViewModel.kt")
        assertTrue(vm.contains("AppForeground.visible.first { it }"))
        val main = src("MainActivity.kt")
        assertTrue(main.substringAfter("override fun onStart()").substringBefore("override fun onStop()").contains("AppForeground.set(true)"))
        assertTrue(main.substringAfter("override fun onStop()").substringBefore("override fun onDestroy()").contains("AppForeground.set(false)"))
    }

    // ── Battery timeline: the line the owner shares ────────────────────────────────────────────────────────────

    private fun sample(elapsed: Long, cpu: Long, batt: Int, screen: Boolean, playing: Boolean) = PowerTimeline.Sample(
        elapsedMs = elapsed, processAgeMs = elapsed, cpuMs = cpu, batteryPct = batt, charging = false,
        screenOn = screen, playing = playing, network = "wifi", thermalStatus = 0, batterySaver = false,
    )

    @Test
    fun `a timeline line carries the cpu share of the phase that just ended`() {
        val before = sample(elapsed = 600_000L, cpu = 40_000L, batt = 80, screen = false, playing = true)
        val now = sample(elapsed = 1_200_000L, cpu = 58_400L, batt = 77, screen = false, playing = true)
        val line = PowerTimeline.formatLine("tick", now, before, "screen_off")
        assertEquals(
            "POWER event=tick screen=off playing=yes batt=77% chg=no net=wifi thermal=0 saver=off | " +
                "since=screen_off wall=600.0s cpu=+18.4s (3.1%) dBatt=-3%",
            line,
        )
    }

    @Test
    fun `the first line of a process reports totals and a missing sample never divides by zero`() {
        val first = PowerTimeline.formatLine("play", sample(elapsed = 100_000L, cpu = 5_000L, batt = -1, screen = true, playing = true), null, null)
        assertTrue(first, first.contains("batt=?"))
        assertTrue(first, first.contains("cpuTotal=5.0s processAge=100.0s (5.0%)"))
        assertEquals("?", PowerTimeline.cpuPercent(1_000L, 0L))
    }

    @Test
    fun `the timeline format has no field that could carry user data`() {
        val line = PowerTimeline.formatLine("play", sample(1L, 1L, 50, true, true), sample(0L, 0L, 50, true, true), "pause")
        // Only keys from a closed list, no free text.
        val keys = Regex("([a-zA-Z]+)=").findAll(line).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf("event", "screen", "playing", "batt", "chg", "net", "thermal", "saver", "since", "wall", "cpu", "dBatt"),
            keys,
        )
    }

    @Test
    fun `the timeline is fed from the screen, play-pause and the existing playing tick - no loop of its own`() {
        val service = src("playback/MusicService.kt")
        assertTrue(Regex("""PowerTimeline\.log\(\s*context, "screen_off"""").containsMatchIn(service))
        assertTrue(Regex("""PowerTimeline\.log\(\s*context, "screen_on"""").containsMatchIn(service))
        assertTrue(service.contains("PowerTimeline.log(this, if (isPlaying) \"play\" else \"pause\""))
        assertTrue(service.contains("PowerTimeline.log(this@MusicService, \"tick\""))
        val timeline = src("utils/PowerTimeline.kt")
        assertFalse("no loop, no timer", timeline.contains("while (") || timeline.contains("delay(") || timeline.contains("Handler"))
        assertTrue(src("utils/DiagnosticHeader.kt").contains("PowerTimeline.headerLine(context)"))
    }
}
