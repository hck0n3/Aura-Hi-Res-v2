package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bloquea el punto 9 del dueño (2026-09-17): *"asegurando que haya variedad de contenido, no solo un
 * elemento"*.
 *
 * El "solo un elemento" no era falta de catálogo: el héroe de Novedades se construye como
 * `(radarAlbums + newAlbums).take(8)` y, con la deduplicación de "primer id visto gana", reclama esos
 * ocho ids **antes** que los estantes que se alimentan de esas mismas dos listas. Si YouTube devolvió
 * nueve álbumes, al estante le quedaba uno — y se dibujaba igual, porque su única guarda era
 * `isEmpty()`.
 */
class NovedadesShelvesTest {

    /** Un estante con una sola tarjeta no se dibuja: eso no es variedad, es una sección rota. */
    @Test
    fun `un estante con una sola tarjeta no se dibuja`() {
        assertFalse(NovedadesShelves.worthShowing(0))
        assertFalse(NovedadesShelves.worthShowing(1))
    }

    /**
     * Dos tampoco: estos estantes son carruseles horizontales y con dos tarjetas no hay nada que
     * desplazar — se lee igual de roto que con una.
     */
    @Test
    fun `dos tampoco hacen un carrusel`() {
        assertFalse(NovedadesShelves.worthShowing(2))
        assertTrue(NovedadesShelves.worthShowing(3))
    }

    /**
     * Y el mínimo no puede subir sin límite: pasado cierto punto empezaría a esconder secciones con
     * contenido legítimo en cuentas o regiones con menos catálogo.
     */
    @Test
    fun `el minimo no esconde secciones legitimas`() {
        assertTrue(NovedadesShelves.MIN_SHELF_ITEMS in 3..4)
        assertTrue(NovedadesShelves.worthShowing(12))
    }
}
