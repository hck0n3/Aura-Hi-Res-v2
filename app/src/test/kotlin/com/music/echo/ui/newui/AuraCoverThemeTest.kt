package iad1tya.echo.music.ui.newui

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Owner round 2026-10-15 (imagen 2): the classic Ajustes pages must wear the SAME accents and ink as the Aura screens
 * and let the cover's bloom show through. The scheme [AuraCoverThemeScope] provides is pinned here.
 */
class AuraCoverThemeTest {
    private val base = darkColorScheme(primary = Color(0xFF123456))
    private val ground = Color(0xFF101820)
    private val teal = Color(0xFF3FE7CE)
    private val blue = Color(0xFF2FA6F0)
    private val violet = Color(0xFF6A5BFF)
    private val ink = Color(0xFFEAF2FF)
    private val onAccent = Color(0xFF061018)

    private val scheme = auraCoverScheme(base, ground, teal, blue, violet, ink, onAccent)

    @Test
    fun `the accents are the ones every Aura screen draws with, not the app theme's own extraction`() {
        assertEquals(teal, scheme.primary)
        assertEquals(blue, scheme.secondary)
        assertEquals(violet, scheme.tertiary)
        assertEquals(onAccent, scheme.onPrimary)
        assertNotEquals(base.primary, scheme.primary)
    }

    @Test
    fun `background and surface let the shell's cover bloom through`() {
        assertEquals(Color.Transparent, scheme.background)
        assertEquals(Color.Transparent, scheme.surface)
    }

    @Test
    fun `text is the cover ink and cards sit on the live ground`() {
        assertEquals(ink, scheme.onSurface)
        assertEquals(ink, scheme.onBackground)
        assertEquals(ground, scheme.surfaceContainerLowest)
        // a card is lighter than the ground it sits on
        assertNotEquals(ground, scheme.surfaceContainer)
    }
}
