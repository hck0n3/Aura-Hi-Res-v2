package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 899 (plan phase 7): high refresh while interacting, 60 Hz (or the lowest mode ≥ 60) at rest. */
class RefreshIdlePolicyTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun src(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private val m48 = RefreshMode(1, 1080, 2340, 48f)
    private val m60 = RefreshMode(2, 1080, 2340, 60f)
    private val m90 = RefreshMode(3, 1080, 2340, 90f)
    private val m120 = RefreshMode(4, 1080, 2340, 120f)

    // ── The state machine ──

    @Test
    fun `it starts active and rests only after the idle callback fires`() {
        val p = RefreshIdlePolicy()
        assertTrue(p.active)
        assertFalse("input while already active changes nothing", p.onOtherInput())
        assertTrue("the idle callback moves it to rest", p.onIdleTimeout())
        assertFalse(p.active)
        assertFalse("a second timeout at rest changes nothing", p.onIdleTimeout())
    }

    @Test
    fun `any input wakes it, and a held finger is never sent to rest`() {
        val p = RefreshIdlePolicy()
        p.onIdleTimeout()
        assertTrue("a finger down wakes it", p.onTouchDown())
        assertTrue(p.active)
        assertFalse("the callback firing while the finger is still down does nothing", p.onIdleTimeout())
        assertTrue(p.active)
        assertFalse("lifting the finger keeps it active", p.onTouchEnd())
        assertTrue("after the lift, the next callback rests it", p.onIdleTimeout())
        assertTrue("a D-pad / key event wakes it too", p.onOtherInput())
    }

    @Test
    fun `leaving the screen forgets a finger that was down`() {
        val p = RefreshIdlePolicy()
        p.onTouchDown()
        p.onHidden()
        assertTrue("with no finger left the callback can rest it", p.onIdleTimeout())
    }

    @Test
    fun `a dialog, sheet, menu or the keyboard holds it active until released`() {
        val p = RefreshIdlePolicy()
        p.onIdleTimeout()
        assertTrue("holding at rest wakes it", p.onHeld(true))
        assertFalse("the callback firing while held does nothing", p.onIdleTimeout())
        assertTrue(p.active)
        assertFalse("releasing changes nothing by itself: the caller re-arms", p.onHeld(false))
        assertTrue("after the release, the next callback rests it", p.onIdleTimeout())
    }

    @Test
    fun `other windows and the keyboard feed the policy too`() {
        val main = src("MainActivity.kt")
        val focus = main.substringAfter("override fun onWindowFocusChanged(hasFocus: Boolean) {").substringBefore("@Composable")
        assertTrue("a dialog or sheet window takes the focus: hold", focus.contains("refreshHoldUnfocused = !hasFocus"))
        assertTrue(focus.contains("updateRefreshHold()"))
        val hold = main.substringAfter("private fun updateRefreshHold() {").substringBefore("override fun onWindowFocusChanged(")
        assertTrue(hold.contains("refreshIdle.onHeld(held)"))
        assertTrue("released: re-armed", hold.contains("if (!held) armRefreshIdle()"))
        val ime = main.substringAfter("private fun RefreshImeHold() {").substringBefore("override fun onResume()")
        assertTrue("one boolean, not every animation frame", ime.contains("derivedStateOf { ime.getBottom(density) > 0 }"))
        assertTrue(main.contains("        RefreshImeHold()\n"))
    }

    @Test
    fun `a playing music video holds it active, a paused one does not`() {
        val main = src("MainActivity.kt")
        assertTrue(main.contains("val held = refreshHoldUnfocused || refreshHoldIme || refreshHoldVideo"))
        val video = main.substringAfter("private fun RefreshVideoHold(playerConnection: PlayerConnection?) {").substringBefore("override fun onResume()")
        assertTrue(video.contains("val videoPlaying = videoMode && playing"))
        assertTrue(video.contains("refreshHoldVideo = videoPlaying"))
        assertTrue(main.contains("        RefreshVideoHold(playerConnection)\n"))
    }

    // ── What is asked for ──

    @Test
    fun `active keeps the device ceiling and rest caps it at 60 with a 60 floor`() {
        assertEquals(RefreshRatePlanning.NO_CEILING, RefreshIdlePolicy.ceilingHz(true, RefreshRatePlanning.NO_CEILING), 0f)
        assertEquals(90f, RefreshIdlePolicy.ceilingHz(true, 90f), 0f)
        assertEquals(60f, RefreshIdlePolicy.ceilingHz(false, RefreshRatePlanning.NO_CEILING), 0f)
        assertEquals(60f, RefreshIdlePolicy.ceilingHz(false, 60f), 0f)
        assertEquals(0f, RefreshIdlePolicy.floorHz(true), 0f)
        assertEquals(60f, RefreshIdlePolicy.floorHz(false), 0f)
    }

    @Test
    fun `at rest the panel gets 60, or its lowest mode at or above 60`() {
        val rest = { modes: List<RefreshMode> ->
            RefreshRatePlanning.pick(modes, modes.last(), RefreshIdlePolicy.ceilingHz(false, RefreshRatePlanning.NO_CEILING), RefreshIdlePolicy.floorHz(false))
        }
        assertEquals(m60, rest(listOf(m48, m60, m90, m120)))
        assertEquals(m90, rest(listOf(m90, m120)))
        val m50 = RefreshMode(5, 1080, 2340, 50f)
        val m100 = RefreshMode(6, 1080, 2340, 100f)
        assertEquals("a 50 Hz mode is not «60 or the lowest ≥ 60»", m100, rest(listOf(m50, m100)))
        assertEquals("only modes below 60: the lowest there is", m48, RefreshRatePlanning.pick(listOf(m48), m48, 60f, 60f))
        // While interacting, exactly the previous choice.
        assertEquals(m120, RefreshRatePlanning.pick(listOf(m48, m60, m90, m120), m60, RefreshIdlePolicy.ceilingHz(true, RefreshRatePlanning.NO_CEILING), RefreshIdlePolicy.floorHz(true)))
    }

    @Test
    fun `without a floor the choice is the one it always was`() {
        val modes = listOf(m48, m60, m90, m120)
        for (ceiling in listOf(30f, 48f, 60f, 90f, 120f, RefreshRatePlanning.NO_CEILING)) {
            val allowed = modes.filter { it.hz <= ceiling + 0.5f }
            val old = allowed.maxByOrNull { it.hz } ?: modes.minByOrNull { it.hz }
            assertEquals("ceiling $ceiling", old, RefreshRatePlanning.pick(modes, m60, ceiling))
        }
    }

    @Test
    fun `rest never forces a switch the panel cannot make seamlessly`() {
        assertTrue(RefreshIdlePolicy.seamless(120f, 120f, emptyList()))
        assertTrue(RefreshIdlePolicy.seamless(120f, 60f, listOf(60f, 90f)))
        assertTrue(RefreshIdlePolicy.seamless(120f, 60f, listOf(59.99f)))
        assertFalse(RefreshIdlePolicy.seamless(120f, 60f, emptyList()))
        assertTrue("below API 31 the platform cannot say: previous behaviour", RefreshIdlePolicy.seamless(120f, 60f, null))
    }

    // ── The wiring ──

    @Test
    fun `the activity drives the policy from input with one re-armed callback and no loop`() {
        val main = src("MainActivity.kt")
        val touch = main.substringAfter("override fun dispatchTouchEvent(").substringBefore("override fun onCreate(")
        assertTrue(touch.contains("noteRefreshTouch(ev)"))
        assertTrue("one callback, re-armed", touch.contains("refreshIdleHandler.removeCallbacks(refreshIdleTimeout)\n        refreshIdleHandler.postDelayed(refreshIdleTimeout,"))
        assertTrue(touch.contains("override fun onUserInteraction()"))
        assertTrue("paused: the callback is dropped", touch.substringAfter("override fun onPause()").contains("refreshIdleHandler.removeCallbacks(refreshIdleTimeout)"))
        assertFalse("no polling", touch.contains("while (") || touch.contains("delay("))
        // The existing request path stays: the device's own ceiling, re-asserted on the same keys, never the same mode twice.
        assertTrue(main.contains("RefreshRatePlanning.ceilingHz(powerSave, thermalStatus)"))
        assertTrue(main.contains("LaunchedEffect(enableHighRefreshRate, configuration, refreshCeiling, displayTick)"))
        assertTrue(main.contains("layoutParams.preferredDisplayModeId != target.id"))
        assertTrue("the switch off keeps its own 60 Hz bucket", main.contains("floorHz = if (enableHighRefreshRate) floorHz else 0f,"))
        // A rest switch that would blank the panel asks for the interacting request instead (battery saver and heat kept).
        assertTrue(main.contains("target = iad1tya.echo.music.utils.RefreshRatePlanning.pick(modes, display.mode.plan(), activeCeilingHz)"))
        assertTrue(main.contains("activeCeilingHz = refreshSystemCeiling,"))
        val policy = src("utils/RefreshIdlePolicy.kt")
        assertFalse(policy.contains("while (") || policy.contains("delay(") || policy.contains("Handler"))
    }
}
