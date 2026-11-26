package iad1tya.echo.music.ui

import iad1tya.echo.music.utils.DeviceFormChoice
import iad1tya.echo.music.utils.DeviceFormFactor
import iad1tya.echo.music.utils.DeviceFormResolution
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 895 (plan, phase 6.1-6.2): «Tipo de aparato» (Automático / Móvil / Tablet / Plegable / TV) and the TV shell —
 * the side rail of UI_INVENTORY §1.7 with labels and Ajustes, no bottom bar, and a full-screen player.
 */
class DeviceFormTvShellTest {

    // ── The resolution, pure ──────────────────────────────────────────────────────────────────────────────────────────

    private val r = DeviceFormResolution

    @Test
    fun `detection reads the system first, then the fold, then the window`() {
        assertEquals(DeviceFormFactor.TV, r.detect(isTelevision = true, isCar = false, hasHinge = false, wideWindow = true))
        assertEquals(DeviceFormFactor.TV, r.detect(isTelevision = true, isCar = true, hasHinge = false, wideWindow = false))
        assertEquals(DeviceFormFactor.CAR, r.detect(isTelevision = false, isCar = true, hasHinge = false, wideWindow = true))
        assertEquals(DeviceFormFactor.FOLDABLE, r.detect(isTelevision = false, isCar = false, hasHinge = true, wideWindow = true))
        assertEquals(DeviceFormFactor.TABLET, r.detect(isTelevision = false, isCar = false, hasHinge = false, wideWindow = true))
        assertEquals(DeviceFormFactor.PHONE, r.detect(isTelevision = false, isCar = false, hasHinge = false, wideWindow = false))
    }

    @Test
    fun `Automatico uses the detected form and a manual choice wins over any detection`() {
        for (detected in DeviceFormFactor.entries) {
            assertEquals(detected, r.effective(DeviceFormChoice.AUTO, detected))
            assertEquals(DeviceFormFactor.PHONE, r.effective(DeviceFormChoice.PHONE, detected))
            assertEquals(DeviceFormFactor.TABLET, r.effective(DeviceFormChoice.TABLET, detected))
            assertEquals(DeviceFormFactor.FOLDABLE, r.effective(DeviceFormChoice.FOLDABLE, detected))
            assertEquals(DeviceFormFactor.TV, r.effective(DeviceFormChoice.TV, detected))
        }
    }

    @Test
    fun `Automatico on anything but a TV changes nothing`() {
        for (car in listOf(false, true)) for (hinge in listOf(false, true)) for (wide in listOf(false, true)) {
            val form = r.effective(DeviceFormChoice.AUTO, r.detect(isTelevision = false, isCar = car, hasHinge = hinge, wideWindow = wide))
            assertFalse("no TV shell", r.usesTvShell(form))
            assertEquals("the wide chrome keeps the window rule", wide, r.wideShell(DeviceFormChoice.AUTO, form, wide))
            for (remote in listOf(false, true)) {
                assertEquals("the D-pad affordances keep today's answer", remote, r.remoteAffordances(DeviceFormChoice.AUTO, remote))
            }
        }
    }

    @Test
    fun `a manual TV on a tablet produces the TV shell and the remote affordances`() {
        val tablet = r.detect(isTelevision = false, isCar = false, hasHinge = false, wideWindow = true)
        val form = r.effective(DeviceFormChoice.TV, tablet)
        assertTrue(r.usesTvShell(form))
        assertTrue(r.wideShell(DeviceFormChoice.TV, form, wideWindow = true))
        assertTrue(r.remoteAffordances(DeviceFormChoice.TV, detectedRemote = false))
        // A real Android TV in Automático gets it too.
        assertTrue(r.usesTvShell(r.effective(DeviceFormChoice.AUTO, r.detect(true, false, false, true))))
    }

    @Test
    fun `the manual forms pick the chrome, and no manual choice takes the remote away`() {
        assertFalse("Móvil never takes the wide chrome", r.wideShell(DeviceFormChoice.PHONE, DeviceFormFactor.PHONE, wideWindow = true))
        assertTrue("Tablet always does", r.wideShell(DeviceFormChoice.TABLET, DeviceFormFactor.TABLET, wideWindow = false))
        assertTrue("Plegable follows the fold", r.wideShell(DeviceFormChoice.FOLDABLE, DeviceFormFactor.FOLDABLE, wideWindow = true))
        assertFalse(r.wideShell(DeviceFormChoice.FOLDABLE, DeviceFormFactor.FOLDABLE, wideWindow = false))
        for (choice in DeviceFormChoice.entries) {
            assertTrue("$choice on a real TV box keeps the focus ring", r.remoteAffordances(choice, detectedRemote = true))
            assertEquals(choice == DeviceFormChoice.TV, r.usesTvShell(r.effective(choice, DeviceFormFactor.PHONE)))
        }
    }

    // ── The wiring, from the source (no Android SDK here) ─────────────────────────────────────────────────────────────

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the choice is resolved once at the root and reaches the three readers`() {
        val activity = main("MainActivity.kt")
        val content = activity.substringAfter("setContent {").substringBefore("iad1tya.echo.music.legal.TermsGate {")
        assertTrue(content.indexOf("ProvideAuraWindowInfo {") in 0 until content.indexOf("iad1tya.echo.music.ui.utils.ProvideDeviceForm {"))
        val ui = main("ui/utils/DeviceFormUi.kt")
        assertTrue(ui.contains("rememberEnumPreference(DeviceFormChoiceKey, DeviceFormChoice.AUTO)"))
        assertTrue(ui.contains("CompositionLocalProvider(LocalDeviceForm provides state, content = content)"))
        val tv = main("ui/utils/TvUi.kt")
        assertTrue(tv.contains("DeviceFormResolution.remoteAffordances(choice, deviceTvCar || noTouchscreen) || forceSplit"))
        assertTrue(tv.contains("val shell = if (form == null) wide else iad1tya.echo.music.utils.DeviceFormResolution.wideShell(form.choice, form.form, wide)"))
        assertTrue(tv.contains("return forceSplit || shell"))
        assertTrue(main("constants/PreferenceKeys.kt").contains("val DeviceFormChoiceKey = stringPreferencesKey(\"device_form_choice\")"))
    }

    @Test
    fun `the TV shell is the side rail, never the bottom bar or the split player`() {
        val activity = main("MainActivity.kt")
        assertTrue(activity.contains("val tvShell = iad1tya.echo.music.ui.utils.rememberIsTvShell() && newUiShell"))
        assertTrue(activity.contains("val showRail = !newUiShell && (isLandscape || isWideLayout) && !inSearchScreen && currentRoute != \"ambient_mode\" ||\n                    tvRail"))
        assertTrue("no capsule, so no split player", activity.contains("val auraWideBottom = newUiShell && isWideLayout && !tvShell"))
        assertTrue("the classic side panel stays retired", activity.contains("val showSideNowPlaying = showRail && !tvShell &&"))
        // The bottom bar slot is skipped whenever the rail shows (the existing gate).
        assertTrue(activity.contains("if (!showRail && currentRoute != \"update\" && currentRoute != \"ambient_mode\") {"))
    }

    @Test
    fun `the TV rail lists every destination of the normal bar, plus Ajustes`() {
        val activity = main("MainActivity.kt")
        // One list for every bar: the Aura phone bar, the wide capsule, the classic toolbar and the rail all receive
        // `navigationItems` unfiltered.
        assertEquals(3, Regex("""items = navigationItems,""").findAll(activity).count())
        val rail = activity.substringAfter("if (showRail && currentRoute != \"update\") {\n                                AppNavigationRail(")
            .substringBefore(")\n                            }")
        assertTrue(rail.contains("navigationItems = navigationItems,"))
        assertTrue(rail.contains("onSearchLongClick = onRailSearchLongClick,"))
        assertTrue(rail.contains("tvStyle = tvShell,"))
        assertTrue(rail.contains("onSettingsClick = if (tvShell) onRailSettingsClick else null,"))
        // Ajustes is a tab root: from settings/appearance it pops back to the `settings` below, never stacks a second one.
        val settingsClick = activity.substringAfter("val onRailSettingsClick: () -> Unit =").substringBefore("val onRailSearchLongClick")
        assertTrue(settingsClick.contains("navController.currentDestination?.route != \"settings\" &&"))
        assertTrue(settingsClick.contains("!navController.popBackStack(\"settings\", inclusive = false)"))
        assertTrue(settingsClick.contains("navController.navigate(\"settings\") { launchSingleTop = true }"))

        val nav = main("ui/component/AppNavigation.kt")
        assertTrue("every item, no filter", nav.contains("navigationItems.forEach { screen ->"))
        assertFalse(nav.contains("navigationItems.filter"))
        assertTrue("labels on TV", nav.contains("alwaysShowLabel = tvStyle,"))
        assertTrue("same tab codes as the bar", Regex("""auraNavTap\(screen\)""").findAll(nav).count() == 2)
        assertTrue(nav.contains("ActionLog.tap(ActionId.NAV_RAIL_SETTINGS)"))
        assertTrue("the TV rail item wears the D-pad ring", nav.contains("modifier = Modifier.tvFocusable(isTvOrCar, scaleFocused = 1f),"))
    }

    @Test
    fun `the setting lives in Apariencia and is findable`() {
        val appearance = main("ui/screens/settings/AppearanceSettings.kt")
        assertTrue(appearance.contains("title = { Text(stringResource(R.string.device_form_title)) },"))
        assertTrue(appearance.contains("values = iad1tya.echo.music.utils.DeviceFormChoice.entries,"))
        assertTrue(appearance.contains("ActionLog.tap(ActionId.SETAPP_DEVICE_FORM)"))
        assertTrue(main("ui/screens/settings/SearchableSettings.kt")
            .contains("Triple(stringResource(R.string.device_form_title), \"Apariencia\", \"settings/appearance\"),"))
        val es = File(root, "app/src/main/res/values-es/strings.xml").readText()
        assertTrue(es.contains("<string name=\"device_form_title\">Tipo de aparato</string>"))
        assertFalse("decision A1 (row 815)", es.substringAfter("device_form_title").substringBefore("</resources>").contains("YouTube"))
    }
}
