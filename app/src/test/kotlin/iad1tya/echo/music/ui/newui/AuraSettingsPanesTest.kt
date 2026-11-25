package iad1tya.echo.music.ui.newui

import iad1tya.echo.music.ui.utils.isWideLayoutWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 873 (plan, phase 1 item 2): which windows get the two-pane Ajustes, how wide the index is, which index row a
 * route highlights and which moves skip the screen transition. Numbers in, decisions out.
 */
class AuraSettingsPanesTest {

    @Test
    fun `the owner's Z Fold7 cover keeps the one-pane Ajustes and the inner screen gets two panes`() {
        // Cover, portrait (~360-410 dp at the display sizes One UI offers) and on its side (wide but short).
        assertFalse(auraSettingsUsesTwoPanes(paneWidthDp = 360f, windowHeightDp = 780f))
        assertFalse(auraSettingsUsesTwoPanes(paneWidthDp = 410f, windowHeightDp = 900f))
        assertFalse(auraSettingsUsesTwoPanes(paneWidthDp = 800f, windowHeightDp = 360f))
        // Inner screen, both orientations and a large display size.
        assertTrue(auraSettingsUsesTwoPanes(paneWidthDp = 884f, windowHeightDp = 1000f))
        assertTrue(auraSettingsUsesTwoPanes(paneWidthDp = 1000f, windowHeightDp = 884f))
        assertTrue(auraSettingsUsesTwoPanes(paneWidthDp = 750f, windowHeightDp = 900f))
    }

    @Test
    fun `the line is the app's one wide rule, on the content pane`() {
        assertFalse(auraSettingsUsesTwoPanes(599.9f, 900f))
        assertTrue(auraSettingsUsesTwoPanes(600f, 900f))
        assertFalse(auraSettingsUsesTwoPanes(900f, 479.9f))
        assertTrue(auraSettingsUsesTwoPanes(900f, 480f))
        // The split player takes the right of the window: the content pane it leaves is what counts.
        assertFalse(auraSettingsUsesTwoPanes(paneWidthDp = 520f, windowHeightDp = 1000f))
        for (w in listOf(320f, 599f, 600f, 700f, 840f, 1200f)) {
            for (h in listOf(300f, 479f, 480f, 900f)) {
                assertEquals("$w x $h", isWideLayoutWindow(w, h), auraSettingsUsesTwoPanes(w, h))
            }
        }
    }

    @Test
    fun `the index is 40 percent of the pane between 300 and 360 dp, so the page keeps the larger share`() {
        assertEquals(300f, auraSettingsIndexWidthDp(600f), 0.01f)
        assertEquals(300f, auraSettingsIndexWidthDp(750f), 0.01f)
        assertEquals(353.6f, auraSettingsIndexWidthDp(884f), 0.01f)
        assertEquals(360f, auraSettingsIndexWidthDp(1200f), 0.01f)
        for (w in listOf(600f, 700f, 884f, 1200f)) {
            assertTrue("$w: the page is at least as wide as the index", w - auraSettingsIndexWidthDp(w) - 1f >= auraSettingsIndexWidthDp(w) - 1f)
        }
    }

    @Test
    fun `a page highlights itself, a page opened from it highlights its parent, anything else is not a pane`() {
        assertEquals("settings/player", auraSettingsPaneSelectionOf("settings/player"))
        assertEquals("settings/storage", auraSettingsPaneSelectionOf("settings/storage?autoOpenExportPicker={autoOpenExportPicker}"))
        assertEquals("settings/ytm_sync", auraSettingsPaneSelectionOf("settings/ytm_sync?onboarding={onboarding}"))
        assertEquals("settings/appearance", auraSettingsPaneSelectionOf("settings/appearance/theme"))
        assertEquals("settings/appearance", auraSettingsPaneSelectionOf("settings/appearance/liquidglass"))
        assertEquals("settings/sound", auraSettingsPaneSelectionOf("settings/sound/autoeq"))
        assertEquals("settings/device_sync", auraSettingsPaneSelectionOf("settings/device_sync/held"))
        for (own in listOf("settings", "settings/equalizer", "settings/spotify_import", "settings/terms", "stats", "migration", "listen_together", null)) {
            assertNull("$own", auraSettingsPaneSelectionOf(own))
        }
        assertTrue(auraSettingsIsPaneRoute("settings"))
        assertTrue(auraSettingsIsPaneRoute("settings/about"))
        assertFalse(auraSettingsIsPaneRoute("settings/equalizer"))
        assertFalse(auraSettingsIsPaneRoute("home"))
    }

    @Test
    fun `every child page hangs from an index page and no route is listed twice`() {
        assertTrue(AURA_SETTINGS_PANE_CHILDREN.values.all { it in AURA_SETTINGS_PANE_ROUTES })
        assertTrue(AURA_SETTINGS_PANE_CHILDREN.keys.none { it in AURA_SETTINGS_PANE_ROUTES })
        assertEquals(AURA_SETTINGS_PANE_ROUTES.size, AURA_SETTINGS_PANE_ROUTES.toSet().size)
        assertFalse(AURA_SETTINGS_ROUTE in AURA_SETTINGS_PANE_ROUTES)
    }

    @Test
    fun `only a move inside the two-pane Ajustes skips the transition`() {
        assertTrue(auraSettingsSwapIsInstant(true, "settings", false, "settings/player", false))
        assertTrue(auraSettingsSwapIsInstant(true, "settings/player", false, "settings/sound", false))
        assertTrue(auraSettingsSwapIsInstant(true, "settings/sound", false, "settings", false))
        // A phone keeps the slide everywhere.
        assertFalse(auraSettingsSwapIsInstant(false, "settings", false, "settings/player", false))
        // Leaving Ajustes, or a page with its own full screen.
        assertFalse(auraSettingsSwapIsInstant(true, "settings", false, "home", false))
        assertFalse(auraSettingsSwapIsInstant(true, "settings/sound", false, "settings/equalizer", false))
        // The welcome's account step is not a pane.
        assertFalse(auraSettingsSwapIsInstant(true, "onboarding_connect", false, "settings/ytm_sync?onboarding={onboarding}", true))
        assertFalse(auraSettingsSwapIsInstant(true, "settings/ytm_sync?onboarding={onboarding}", true, "settings", false))
    }
}
