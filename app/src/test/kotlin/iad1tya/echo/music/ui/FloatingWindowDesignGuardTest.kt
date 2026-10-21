package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-19: «todas las ventanas que salen flotantes en modo plegable no me terminan de convencer… las veo muy toscas
 * y básicas… no las veo estéticas en modo plegable desplegado». In a wide window every floating window is a CARD (centred,
 * four large corners, soft shadow, lit edge, spring entry, no drag handle) built from ONE recipe (AuraWindowRecipe.kt) that
 * the three hosts share. There is no Android SDK in the unit job, so the wiring is pinned as source text.
 */
class FloatingWindowDesignGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private val recipe get() = main("ui/newui/AuraWindowRecipe.kt")

    private val hosts get() = mapOf(
        "BottomSheetMenu" to main("ui/component/BottomSheetMenu.kt"),
        "BottomSheetPage" to main("ui/component/BottomSheetPage.kt"),
        "AuraInWindowDialog" to main("ui/newui/AuraInWindowDialog.kt"),
    )

    @Test
    fun `the recipe is the same width and height test as the wide shell and costs nothing on a phone`() {
        val r = recipe
        assertTrue(r.contains("isWideLayoutWindow(width.toFloat(), height.toFloat())"))
        assertTrue("a phone on its side (wide but short) is not a wide window", r.contains("rememberAuraWideWindows"))
        // the phone keeps the slide from the bottom it always had
        assertTrue(r.contains("else -> slideInVertically(animationSpec = tween(220)) { it } + fadeIn(animationSpec = tween(180))"))
        assertTrue(r.contains("else -> slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(160))"))
    }

    @Test
    fun `a wide window card has large equal corners, a lit edge and a shadow that never draws inside the glass`() {
        val r = recipe
        assertTrue(r.contains("val AuraWindowShapeWide = RoundedCornerShape(24.dp)"))
        assertTrue("the edge is lighter at the top", r.contains("Brush.verticalGradient(") && r.contains("this.border(1.dp, brush, shape)"))
        assertTrue("the shadow is cut out of the outline", r.contains("clipPath(path, clipOp = ClipOp.Difference)"))
        assertFalse("not the platform elevation shadow (it would sit under the translucent glass)", r.contains("Modifier.shadow("))
    }

    @Test
    fun `the entry is a short spring rise with a scale and a fade, and the performance mode shows no motion at all`() {
        val r = recipe
        assertTrue(r.contains("initialScale = 0.96f"))
        assertTrue(r.contains("Spring.DampingRatioNoBouncy"))
        assertTrue(r.contains("wide && lean -> EnterTransition.None"))
        assertTrue(r.contains("wide && lean -> ExitTransition.None"))
        assertTrue(r.contains("rememberLeanMode()"))
    }

    @Test
    fun `the three hosts share the one recipe and a wide card has no drag handle`() {
        for ((name, src) in hosts) {
            assertTrue("$name usa la receta ancha", src.contains("rememberAuraWideWindows()"))
            assertTrue("$name usa la entrada compartida", src.contains("rememberAuraWindowEnter(wide)"))
            assertTrue("$name usa la salida compartida", src.contains("rememberAuraWindowExit(wide)"))
            assertTrue("$name dibuja la sombra suave", src.contains("auraWindowShadow(") && src.contains("enabled = wide"))
            assertTrue("$name dibuja el filo de luz", src.contains("auraWindowBorder("))
            assertTrue("$name se coloca dentro de barras y teclado", src.contains("auraWideWindowArea()"))
            // the phone keeps its drag handle and its drag offset (AuraSheetDragTest pins both as well)
            assertTrue("$name conserva la barrita en el móvil", src.contains("AuraSheetHandle("))
            assertTrue("$name conserva el arrastre en el móvil", src.contains("auraSheetDragOffset("))
        }
        assertTrue(main("ui/component/BottomSheetMenu.kt").contains("A card has no drag handle"))
        assertTrue(main("ui/newui/AuraInWindowDialog.kt").contains("if (!center && !wide) {"))
    }

    @Test
    fun `a wide card floats centred with a width that fits its kind and never taller than 80 percent of the window`() {
        val r = recipe
        assertTrue(r.contains("const val AuraWideMenuWidthDp = 500"))
        assertTrue(r.contains("const val AuraWidePageWidthDp = 560"))
        assertTrue(r.contains("const val AuraWideCompactWidthDp = 460"))
        assertTrue(r.contains("const val AuraWideWindowMaxHeightFraction = 0.80f"))
        assertTrue(main("ui/component/BottomSheetMenu.kt").contains("AuraWideMenuWidthDp.dp"))
        assertTrue(main("ui/component/BottomSheetPage.kt").contains("AuraWidePageWidthDp.dp"))
        assertTrue(main("ui/newui/AuraInWindowDialog.kt").contains("AuraWideCompactWidthDp"))
        // the quick search (full screen) keeps its own shape
        assertTrue(main("ui/component/BottomSheetPage.kt").contains("rememberAuraWideWindows() && !state.fullScreen"))
    }

    @Test
    fun `the glass colours and transparency are not decided by the design recipe`() {
        val r = recipe
        assertFalse(r.contains("FLOATING_WINDOW_GLASS_ALPHA"))
        assertFalse(r.contains("WindowGlassTint"))
        assertFalse(r.contains("hazeChild"))
        assertEquals("the recipe leaves the glass to floatingWindowGlass", 3, hosts.values.count { it.contains("floatingWindowGlass(") })
    }

    @Test
    fun `dialogs get a little more air in a wide window`() {
        assertTrue(main("ui/component/Dialog.kt").contains("rememberAuraWideWindows()) 28.dp else 24.dp"))
    }
}
