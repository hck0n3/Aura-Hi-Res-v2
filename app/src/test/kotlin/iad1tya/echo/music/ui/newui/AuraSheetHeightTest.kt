package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bloquea el arreglo de *"la ventana se desplaza muy alto y su contenido es poco"* (dueño,
 * 2026-09-17, con el botón de salida de audio como ejemplo).
 *
 * Lo que hay que impedir que vuelva NO es el cálculo — es trivial — sino que alguien sustituya el
 * tope por un alto FIJO y todas las hojas vuelvan a medir lo mismo tengan tres opciones o treinta.
 * Por eso estas pruebas miran la propiedad, no el número: el valor tiene que ESCALAR con la pantalla
 * y tiene que dejar sitio libre por arriba.
 */
class AuraSheetHeightTest {

    @Test
    fun `el tope escala con la pantalla y nunca es un alto fijo`() {
        val pequena = auraSheetMaxHeightDp(640)
        val grande = auraSheetMaxHeightDp(960)
        assertTrue("un tope fijo es exactamente el fallo que esto arregla", grande > pequena)
        assertEquals(1.5f, grande / pequena, 1e-4f)
    }

    /** Si el tope fuera la pantalla entera, el toque fuera para cerrar no tendría dónde caer. */
    @Test
    fun `siempre queda pantalla libre por arriba`() {
        assertTrue(AuraSheetMaxHeightFraction < 1f)
        assertTrue(auraSheetMaxHeightDp(800) < 800f)
    }

    /** Y tampoco puede ser tan bajo que una hoja con contenido de verdad quede inutilizable. */
    @Test
    fun `el tope sigue dando sitio a una hoja larga`() {
        assertTrue(AuraSheetMaxHeightFraction >= 0.7f)
        assertEquals(680f, auraSheetMaxHeightDp(800), 1e-3f)
    }
}
