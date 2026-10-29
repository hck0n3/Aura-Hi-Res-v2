package iad1tya.echo.music.ui.newui

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ── Rediseño 2026-10-20: «la parte de novedades no me gusta, se ve muy básico» ─────────────────────

    @Test
    fun `la pildora de antiguedad solo existe para fechas reales y recientes`() {
        val today = LocalDate.of(2026, 10, 20)
        assertEquals(ReleaseAge.Today, NovedadesShelves.ageOf(today, today))
        assertEquals(ReleaseAge.Yesterday, NovedadesShelves.ageOf(today.minusDays(1), today))
        assertEquals(ReleaseAge.ThisWeek, NovedadesShelves.ageOf(today.minusDays(2), today))
        assertEquals(ReleaseAge.ThisWeek, NovedadesShelves.ageOf(today.minusDays(6), today))
        assertEquals(ReleaseAge.ThisMonth, NovedadesShelves.ageOf(today.minusDays(7), today))
        assertEquals(ReleaseAge.ThisMonth, NovedadesShelves.ageOf(today.minusDays(29), today))
        // Más viejo o con fecha futura: ninguna etiqueta antes que una etiqueta falsa.
        assertNull(NovedadesShelves.ageOf(today.minusDays(30), today))
        assertNull(NovedadesShelves.ageOf(today.plusDays(1), today))
    }

    @Test
    fun `cada filtro deja pasar solo su tipo`() {
        val all = AuraContentKind.values()
        assertTrue(all.all { NovedadesShelves.matches(NovedadesFilter.All, it) })
        assertEquals(listOf(AuraContentKind.Album), all.filter { NovedadesShelves.matches(NovedadesFilter.Albums, it) })
        assertEquals(
            listOf(AuraContentKind.Ep, AuraContentKind.Single).toSet(),
            all.filter { NovedadesShelves.matches(NovedadesFilter.SinglesAndEps, it) }.toSet(),
        )
        assertEquals(
            listOf(AuraContentKind.Song, AuraContentKind.Video).toSet(),
            all.filter { NovedadesShelves.matches(NovedadesFilter.Songs, it) }.toSet(),
        )
        // «Próximamente» enseña solo su propio estante, ningún elemento de los demás.
        assertTrue(all.none { NovedadesShelves.matches(NovedadesFilter.Upcoming, it) })
    }

    @Test
    fun `no se ofrece un filtro que dejaria la pantalla vacia`() {
        assertEquals(
            listOf(NovedadesFilter.All),
            NovedadesShelves.visibleFilters(hasAlbums = true, hasSinglesAndEps = false, hasSongs = false, hasUpcoming = false),
        )
        assertEquals(
            listOf(NovedadesFilter.All, NovedadesFilter.Albums, NovedadesFilter.Songs),
            NovedadesShelves.visibleFilters(hasAlbums = true, hasSinglesAndEps = false, hasSongs = true, hasUpcoming = false),
        )
        assertEquals(
            NovedadesFilter.values().toList(),
            NovedadesShelves.visibleFilters(true, true, true, true),
        )
    }

    @Test
    fun `un estante filtrado, expandido o corto se dibuja como rejilla`() {
        assertFalse(NovedadesShelves.asGrid(filtered = false, expanded = false, count = 12))
        assertTrue(NovedadesShelves.asGrid(filtered = true, expanded = false, count = 12))
        assertTrue(NovedadesShelves.asGrid(filtered = false, expanded = true, count = 12))
        assertTrue(NovedadesShelves.asGrid(filtered = false, expanded = false, count = 2))
        assertEquals(0, NovedadesShelves.rowCount(0, 3))
        assertEquals(1, NovedadesShelves.rowCount(3, 3))
        assertEquals(2, NovedadesShelves.rowCount(4, 3))
    }

    /**
     * El rediseño no puede perder lo que Novedades ya hacía (UI_INVENTORY / filas 141, 348, 366, 431): se
     * comprueba en el código fuente que sigue cableado, no solo que compila.
     */
    @Test
    fun `el rediseno conserva las funciones de Novedades`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val src = File(root, "app/src/main/kotlin/com/music/echo/ui/newui/AuraNovedadesScreen.kt").readText()
        // Datos: las mismas siete fuentes del view-model y la deduplicación de «primer id visto gana».
        // Fila 517: Novedades solo habla de estrenos de artistas seguidos (radar + «Próximamente»); lo demás se fue.
        for (flow in listOf("radarReleases", "upcoming")) {
            assertTrue("falta $flow", src.contains("viewModel.$flow.collectAsState()"))
        }
        for (gone in listOf("newAlbums", "featuredSongs", "momentSongs", "viewModel.listening", "NovedadesLocalSongCard")) {
            assertFalse("Novedades ya no lleva $gone", src.contains(gone))
        }
        val homeSrc = File(root, "app/src/main/kotlin/com/music/echo/ui/newui/AuraHomeScreen.kt").readText()
        assertTrue(homeSrc.contains("aura_home_moment") && homeSrc.contains("viewModel.momentSongs.collectAsState()"))
        assertTrue(src.contains("seen.claimUnique("))
        // Actualizar (tirar), reselección, estados, menús y navegación.
        assertTrue(src.contains("onRefresh = viewModel::refresh"))
        assertTrue(src.contains("AuraScrollToTopOnReselect(navController)"))
        assertTrue(src.contains("offlineState.offline"))
        assertTrue(src.contains("isRefreshing && allShelvesEmpty"))
        assertTrue(src.contains("!isRefreshing && allShelvesEmpty"))
        assertTrue(src.contains("YouTubeAlbumMenu(") && src.contains("YouTubeSongMenu(") && src.contains("YouTubePlaylistMenu(") && src.contains("SongMenu("))
        assertTrue(src.contains("navController.navigate(\"album/"))
        assertTrue(src.contains("navController.navigate(\"artist/"))
        // Fila 513: el «Top diario» vive en Inicio, no en Novedades (orden del dueño 2026-10-29).
        assertFalse(src.contains("daily_top") || src.contains("topSongs") || src.contains("top_playlist/"))
        val home = File(root, "app/src/main/kotlin/com/music/echo/ui/newui/AuraHomeScreen.kt").readText()
        assertTrue(home.contains("aura_home_daily_top") && home.contains("viewModel.dailyTop.collectAsState()"))
        assertTrue(src.contains("viewModel.togglePresave(item)"))
        assertTrue(src.contains("collapseTrailing = true"))
        // El botón ▶ del héroe resuelve el álbum en el toque (los del radar no traen playlistId), nunca por fotograma.
        assertTrue(src.contains("YouTubeAlbumRadio(playlistId, contextId = ShuffleContexts.album(album.id))"))
        // Calor/batería: el fondo del héroe es la portada diminuta estirada, sin RenderEffect.
        assertFalse(src.contains(".blur("))
    }
}
