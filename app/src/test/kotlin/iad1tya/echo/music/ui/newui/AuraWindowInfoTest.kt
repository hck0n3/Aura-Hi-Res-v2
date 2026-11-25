package iad1tya.echo.music.ui.newui

import iad1tya.echo.music.ui.utils.WIDE_LAYOUT_BREAKPOINT_DP
import iad1tya.echo.music.ui.utils.WIDE_LAYOUT_MIN_HEIGHT_DP
import iad1tya.echo.music.ui.utils.isWideLayoutWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 869 (plan, phase 1 item 1): ONE classifier decides the window classes from numbers. These are the
 * numbers in, the classes out — and the proof that the wide rule the app already had is the same rule, expressed with
 * the classes, so moving the call sites to the new layer changed no layout.
 */
class AuraWindowInfoTest {
    private fun hinge(flat: Boolean, vertical: Boolean) =
        AuraHinge(0f, 0f, 10f, 10f, isFlat = flat, isVertical = vertical, isSeparating = !flat, isOccluding = false)

    @Test
    fun `width classes use Material's lines and the lower bounds are inclusive`() {
        assertEquals(AuraWidthClass.Compact, auraWidthClassOf(0f))
        assertEquals(AuraWidthClass.Compact, auraWidthClassOf(599.9f))
        assertEquals(AuraWidthClass.Medium, auraWidthClassOf(600f))
        assertEquals(AuraWidthClass.Medium, auraWidthClassOf(839.9f))
        assertEquals(AuraWidthClass.Expanded, auraWidthClassOf(840f))
        assertEquals(AuraWidthClass.Expanded, auraWidthClassOf(1600f))
    }

    @Test
    fun `the owner's Z Fold7 cover is compact and its inner screen is not`() {
        // Cover screen, portrait: ~360-410 dp wide depending on the display-size setting.
        assertEquals(AuraWidthClass.Compact, auraWidthClassOf(360f))
        assertEquals(AuraWidthClass.Compact, auraWidthClassOf(410f))
        // Inner screen: ~750 dp at a large display size, 840+ dp at the default one.
        assertEquals(AuraWidthClass.Medium, auraWidthClassOf(750f))
        assertEquals(AuraWidthClass.Expanded, auraWidthClassOf(884f))
    }

    @Test
    fun `compact height is a phone on its side and 480 itself is not compact`() {
        assertTrue(isAuraCompactHeight(360f))
        assertTrue(isAuraCompactHeight(479.9f))
        assertFalse(isAuraCompactHeight(480f))
        assertFalse(isAuraCompactHeight(832f))
    }

    @Test
    fun `the wide rule the app already had is medium-or-more width and not compact height`() {
        assertEquals(AURA_MEDIUM_MIN_WIDTH_DP, WIDE_LAYOUT_BREAKPOINT_DP)
        assertEquals(AURA_COMPACT_HEIGHT_BELOW_DP, WIDE_LAYOUT_MIN_HEIGHT_DP)
        val widths = listOf(0f, 360f, 410f, 599.9f, 600f, 750f, 800f, 839.9f, 840f, 915f, 1280f)
        val heights = listOf(0f, 300f, 360f, 412f, 479.9f, 480f, 600f, 832f, 915f)
        for (w in widths) for (h in heights) {
            val fromClasses = auraWidthClassOf(w) != AuraWidthClass.Compact && !isAuraCompactHeight(h)
            assertEquals("$w x $h", isWideLayoutWindow(w, h), fromClasses)
        }
    }

    @Test
    fun `landscape is wider than tall, as the shell's rail always measured it`() {
        assertTrue(AuraWindowInfo(800f, 360f, 800, 360).isLandscape)
        assertFalse(AuraWindowInfo(750f, 832f, 750, 832).isLandscape)
        assertFalse("a square window is not landscape", AuraWindowInfo(700f, 700f, 700, 700).isLandscape)
    }

    @Test
    fun `the window info answers with the container size, not the configuration`() {
        val info = AuraWindowInfo(widthDp = 599.5f, heightDp = 900f, configWidthDp = 600, configHeightDp = 900)
        assertEquals(AuraWidthClass.Compact, info.widthClass)
        assertFalse(info.isCompactHeight)
        assertEquals(AuraPosture.Flat, info.posture)
        assertTrue(info.hinges.isEmpty())
    }

    @Test
    fun `posture comes from the hinges and a flat or missing hinge is flat`() {
        assertEquals(AuraPosture.Flat, auraPostureOf(emptyList()))
        assertEquals(AuraPosture.Flat, auraPostureOf(listOf(hinge(flat = true, vertical = true))))
        assertEquals(AuraPosture.Flat, auraPostureOf(listOf(hinge(flat = true, vertical = false))))
        assertEquals(AuraPosture.Tabletop, auraPostureOf(listOf(hinge(flat = false, vertical = false))))
        assertEquals(AuraPosture.Book, auraPostureOf(listOf(hinge(flat = false, vertical = true))))
        assertEquals(
            "a half-opened horizontal fold is tabletop even next to a vertical one",
            AuraPosture.Tabletop,
            auraPostureOf(listOf(hinge(flat = false, vertical = true), hinge(flat = false, vertical = false))),
        )
    }
}
