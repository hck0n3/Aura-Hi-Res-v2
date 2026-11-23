package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 734 (UX audit P22C): the sleep timer took ~5 taps, gave no sign it was on and cut the music dead. The
 * wiring of the fix is pinned as source text; the math is in `playback/SleepTimerMathTest`.
 */
class SleepTimerUxGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String): String =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the service fades the volume out before pausing and always gives the volume back`() {
        val svc = main("playback/MusicService.kt")
        val timer = svc.substring(svc.indexOf("fun startSleepTimer(minutes: Int)"), svc.indexOf("fun startSleepTimerAfterSong()"))
        assertTrue(timer.contains("SleepTimerMath.waitBeforeFadeMs(deadline - android.os.SystemClock.elapsedRealtime())"))
        assertTrue(timer.contains("fadeOutAndFireSleepTimer(deadline)"))
        assertTrue(timer.contains("sleepFadeGain = SleepTimerMath.stepGain(step)"))
        assertTrue(timer.contains("player.volume = base * sleepFadeGain"))
        assertFalse("nunca dispara 10 s antes por estar en búfer o en pausa", timer.contains("player.isPlaying"))
        assertTrue("no pelea con el crossfade ni con el fundido de entrada",
            timer.contains("if (!isCrossfading && manualFadeInJob?.isActive != true) {"))
        val fin = timer.substring(timer.indexOf("} finally {"))
        assertTrue(fin.contains("sleepFadeGain = 1f"))
        assertTrue("el volumen vuelve siempre, también al apagar o alargar a mitad del fundido",
            fin.contains("player.volume = if (isMuted.value) 0f else playerVolume.value"))
        // Every repeating volume writer composes with the sleep fade instead of overwriting it.
        val xf = main("playback/CrossfadeEngine.kt")
        assertTrue(xf.contains("player.volume = startVolume * fadeIn * xfHeadroom * sleepFadeGain"))
        assertTrue(xf.contains("fp?.volume = startVolume * fadeOut * xfHeadroom * sleepFadeGain"))
        assertTrue("el finally del crossfade respeta el fundido", xf.contains("player.volume = sleepFadeGain * when {"))
        assertTrue(xf.contains("player.volume = target * ManualFadeIn.stepGain(i) * sleepFadeGain"))
        assertTrue(xf.contains(")?.let { player.volume = it * sleepFadeGain }"))
        assertTrue(svc.contains("decision.volume?.let { player.volume = it * sleepFadeGain }"))
        assertTrue(timer.contains("fun extendSleepTimer(minutes: Int)"))
        // Fire still pauses every player of a crossfade (row 370).
        assertTrue(svc.contains("secondaryPlayer?.let { runCatching { it.pause() } }"))
        assertTrue(svc.contains("fadingPlayer?.let { runCatching { it.pause() } }"))
    }

    @Test
    fun `one tap from the player menu, and the full dialog row is kept`() {
        val menu = main("ui/newui/AuraPlayerMenu.kt")
        assertTrue(menu.contains("AuraSleepTimerPresets(onPicked = onDismiss"))
        assertTrue("UI_INVENTORY regla 5: la fila del diálogo no se quita", menu.contains("onClick = { ActionLog.tap(ActionId.PMENU_SLEEP_TIMER); showSleepTimerDialog = true },"))
        val presets = main("ui/newui/AuraSleepTimer.kt")
        assertTrue(presets.contains("SleepTimerMath.PRESET_MINUTES.forEach"))
        assertTrue(presets.contains("service.startSleepTimerAfterSong()"))
    }

    @Test
    fun `the full player shows the running timer and ticks once a second only while visible`() {
        val player = main("ui/newui/AuraPlayer.kt")
        assertTrue(player.contains("AuraSleepTimerChip(visible = state.isExpanded)"))
        val chip = main("ui/newui/AuraSleepTimer.kt").substringAfter("internal fun AuraSleepTimerChip(")
        assertTrue(chip.contains("if (deadline == null || !visible) return@LaunchedEffect"))
        assertTrue(chip.contains("lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)"))
        assertTrue(chip.contains("delay(SleepTimerMath.nextTickDelayMs(left))"))
        assertFalse("nada por fotograma (regla 7)", chip.contains("withFrameNanos") || chip.contains("withFrameMillis"))
        assertTrue("dispara al levantar el dedo dentro de la hoja (filas 372/396)", chip.contains(".unstealableClick(open)"))
        assertTrue(chip.contains("SleepTimerDialog(onDismiss = { showDialog = false })"))
        val dialog = main("ui/component/SleepTimerDialog.kt")
        assertTrue(dialog.contains("service.extendSleepTimer(SleepTimerMath.EXTEND_MINUTES)"))
    }
}
