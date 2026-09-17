package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dueño (2026-09-17): *"sin importar dónde esté, el botón de inicio del mini reproductor tiene que
 * mandarlo a la pantalla de inicio sí o sí"*.
 */
class HomeShortcutTest {

    private val home = "home"

    @Test
    fun `from anywhere else it navigates`() {
        assertTrue(HomeShortcut.needsNavigation("album/abc", home))
        assertTrue(HomeShortcut.needsNavigation("online_playlist/xyz", home))
        assertTrue(HomeShortcut.needsNavigation("settings", home))
        assertTrue(HomeShortcut.needsNavigation("artist/1", home))
    }

    @Test
    fun `on home itself it does nothing`() {
        assertFalse(HomeShortcut.needsNavigation("home", home))
        // Una ruta con argumentos sigue siendo la misma pantalla.
        assertFalse(HomeShortcut.needsNavigation("home?tab=1", home))
    }

    @Test
    fun `an unknown route counts as not home`() {
        // Preferimos una navegación de más a dejarle el botón muerto, que es el fallo que reportó.
        assertTrue(HomeShortcut.needsNavigation(null, home))
    }

    @Test
    fun `forcing is needed whenever the attempt did not land on home`() {
        assertTrue(HomeShortcut.forceNeeded("album/abc", home))
        assertTrue(HomeShortcut.forceNeeded(null, home))
        assertFalse(HomeShortcut.forceNeeded("home", home))
    }

    @Test
    fun `the log carries the screen name and never the id`() {
        assertEquals("album", HomeShortcut.screenName("album/OLAK5uy_secret"))
        assertEquals("online_playlist", HomeShortcut.screenName("online_playlist/PL123"))
        assertEquals("home", HomeShortcut.screenName("home?tab=1"))
        assertEquals("none", HomeShortcut.screenName(null))
        assertEquals("none", HomeShortcut.screenName(""))
    }
}
