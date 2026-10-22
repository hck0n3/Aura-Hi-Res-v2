package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.playlistimport.MusicRequestKaraoke.Cand
import iad1tya.echo.music.playlistimport.MusicRequestKaraoke.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dueño (2026-10-19): *«en Pedir música, si necesito una canción en específico en karaoke, que la pueda pedir de ahí para
 * que me ponga la pista para que yo la pueda cantar»*. Lo que se puede probar sin red: leer la petición y elegir, de lo que
 * devuelva el buscador, las pistas de ESA canción. (La escalera de búsqueda de `AiPlaylistGenerator` no corre en la JVM: la
 * fija `MusicRequestKaraokeGuardTest`.)
 */
class MusicRequestKaraokeTest {

    private fun subjectOf(prompt: String): String? = MusicRequestKaraoke.parse(prompt)?.subject

    // ── la petición: qué se quiere cantar ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `karaoke de una cancion`() {
        assertEquals("bohemian rhapsody", subjectOf("karaoke de Bohemian Rhapsody"))
        assertEquals("bohemian rhapsody", subjectOf("Karaoke de Bohemian Rhapsody"))
        assertEquals("despacito", subjectOf("karaoke de Despacito"))
    }

    @Test
    fun `la pista de una cancion para cantar`() {
        assertEquals("hotel california", subjectOf("ponme la pista de Hotel California para cantar"))
        assertEquals("hotel california", subjectOf("pon la pista de Hotel California"))
        assertEquals("hotel california", subjectOf("la pista de Hotel California"))
        assertEquals("hotel california", subjectOf("pista de Hotel California"))
    }

    @Test
    fun `quiero cantar una cancion`() {
        assertEquals("despacito", subjectOf("quiero cantar Despacito, pon el karaoke"))
        assertEquals("despacito", subjectOf("quiero cantar Despacito"))
        assertEquals("despacito", subjectOf("necesito cantar Despacito"))
        assertEquals("despacito", subjectOf("pon la cancion Despacito en karaoke"))
    }

    @Test
    fun `instrumental de una cancion`() {
        val ask = MusicRequestKaraoke.parse("instrumental de La Bamba")!!
        assertEquals("la bamba", ask.subject)
        assertTrue("dijo instrumental, no karaoke", ask.instrumental)
        assertEquals("la bamba instrumental", ask.query)
        assertEquals("la bamba karaoke", ask.altQuery)
    }

    @Test
    fun `la cancion y el artista en cualquier orden`() {
        assertEquals("bohemian rhapsody queen", subjectOf("Bohemian Rhapsody karaoke Queen"))
        assertEquals("juan gabriel amor eterno", subjectOf("pista karaoke de Juan Gabriel Amor eterno"))
        assertEquals("amor eterno de juan gabriel", subjectOf("karaoke de Amor eterno de Juan Gabriel"))
        assertEquals("livin la vida loca", subjectOf("letra y pista de Livin la vida loca"))
    }

    @Test
    fun `un titulo que empieza con articulo o con verbo no pierde nada`() {
        assertEquals("la bamba", subjectOf("karaoke de La Bamba"))
        assertEquals("la bamba", subjectOf("instrumental de La Bamba"))
        assertEquals("te quiero", subjectOf("te quiero karaoke"))
        assertEquals("la cancion del mariachi", subjectOf("karaoke de La Cancion del Mariachi"))
        assertEquals("quiero bailar", subjectOf("karaoke de Quiero Bailar"))
    }

    @Test
    fun `variantes habladas de la misma peticion`() {
        assertEquals("shallow", subjectOf("dame el karaoke de Shallow"))
        assertEquals("shallow", subjectOf("busca la pista karaoke de Shallow"))
        assertEquals("shallow", subjectOf("Shallow en karaoke por favor"))
        assertEquals("shallow", subjectOf("backing track Shallow"))
        assertEquals("shallow", subjectOf("shallow minus one"))
        assertEquals("shallow", subjectOf("version karaoke de Shallow"))
        assertEquals("shallow", subjectOf("play karaoke Shallow"))
        assertEquals("shallow", subjectOf("sing Shallow karaoke"))
    }

    @Test
    fun `la cantidad pedida no ensucia lo que se quiere cantar`() {
        assertEquals("salsa", subjectOf("pon 30 karaokes de salsa"))
        assertEquals("salsa", subjectOf("dame 10 pistas de salsa"))
    }

    @Test
    fun `karaoke de un estilo o de un artista`() {
        val salsa = MusicRequestKaraoke.parse("karaoke de salsa")!!
        assertEquals("salsa", salsa.subject)
        assertTrue(salsa.styleOnly)
        val rock = MusicRequestKaraoke.parse("pon karaoke de rock en español")!!
        assertTrue(rock.styleOnly)
        assertTrue(MusicRequestKaraoke.parse("karaoke de música romántica")!!.styleOnly)
        val queen = MusicRequestKaraoke.parse("karaoke de Queen")!!
        assertEquals("queen", queen.subject)
        assertFalse("un artista no es un estilo", queen.styleOnly)
        assertFalse("una canción cuyo título son palabras sueltas no es un estilo", MusicRequestKaraoke.parse("te quiero karaoke")!!.styleOnly)
    }

    @Test
    fun `lo que NO es pedir la pista de una cancion sigue su camino de siempre`() {
        assertNull(MusicRequestKaraoke.parse("música instrumental para estudiar"))
        assertNull(MusicRequestKaraoke.parse("jazz instrumental"))
        assertNull(MusicRequestKaraoke.parse("canciones para cantar en el carro"))
        assertNull(MusicRequestKaraoke.parse("karaoke"))
        assertNull(MusicRequestKaraoke.parse("pista de baile para la fiesta"))
        assertNull(MusicRequestKaraoke.parse("salsa de los 80"))
        assertNull(MusicRequestKaraoke.parse("ponme Bohemian Rhapsody"))
        assertNull(MusicRequestKaraoke.parse("reggaeton cristiano 2026"))
        assertNull(MusicRequestKaraoke.parse(""))
    }

    // ── lo que devuelve el buscador: solo la pista de ESA canción ─────────────────────────────────────────────────────

    private fun results(vararg rows: Triple<String, String?, String>): List<Cand> =
        rows.map { (title, album, channel) -> Cand(title, album, listOf(channel)) }

    private val bohemianSearch = results(
        Triple("Bohemian Rhapsody - Remastered 2011", "Greatest Hits", "Queen"), // el original: NO es pista
        Triple("Bohemian Rhapsody (Karaoke Version)", "Karaoke Hits", "Sing King"),
        Triple("Bohemian Like You (Karaoke Version)", "Karaoke Hits", "Sing King"), // pista de OTRA canción
        Triple("Bohemian Rhapsody (In the Style of Queen) [Karaoke Version]", "Karaoke", "Karaoke Hits Band"),
        Triple("Bohemian Rhapsody (Piano Version)", "Piano Covers", "Piano Tribute"),
        Triple("Don't Stop Me Now (Karaoke Version)", "Karaoke Hits", "Sing King"), // pista de otra canción
        Triple("Bohemian Rhapsody (Instrumental)", "Instrumentals", "Backing Studio"),
        Triple("Bohemian Rhapsody", "Bohemian Rhapsody (Live)", "Queen"),
    )

    @Test
    fun `una cancion concreta - solo sus pistas, la exacta primero, nunca el original ni otra cancion`() {
        val ask = MusicRequestKaraoke.parse("karaoke de Bohemian Rhapsody")!!
        val pick = MusicRequestKaraoke.select(ask, bohemianSearch, target = 10) { it }
        assertEquals(Kind.SONG, pick.kind)
        val titles = pick.ordered.map { it.title }
        assertEquals("la pista exacta va primero", "Bohemian Rhapsody (Karaoke Version)", titles.first())
        assertFalse("el original no es una pista", titles.any { it.contains("Remastered") || it == "Bohemian Rhapsody" })
        assertFalse("la pista de otra canción no entra", titles.any { it.contains("Like You") || it.contains("Don't Stop") })
        assertTrue("sus alternativas sí", titles.contains("Bohemian Rhapsody (In the Style of Queen) [Karaoke Version]"))
        assertTrue(titles.contains("Bohemian Rhapsody (Instrumental)"))
        assertEquals(pick.ordered.first(), pick.best)
    }

    @Test
    fun `una lista de una cancion es corta - la pista y sus alternativas, no una playlist`() {
        val many = (1..20).map { Cand("Bohemian Rhapsody (Karaoke Version)", "Karaoke $it", listOf("Channel $it")) }
        val ask = MusicRequestKaraoke.parse("karaoke de Bohemian Rhapsody")!!
        val pick = MusicRequestKaraoke.select(ask, many, target = 25) { it }
        assertEquals(Kind.SONG, pick.kind)
        assertEquals(MusicRequestKaraoke.SONG_LIST_MAX, pick.ordered.size)
        assertEquals(
            "alternativas de canales distintos",
            pick.ordered.map { it.artists.first() }.distinct().size, pick.ordered.size,
        )
    }

    @Test
    fun `el artista dado ayuda a elegir y no impide encontrar la pista de un canal sin credito`() {
        val search = results(
            Triple("Bohemian Rhapsody (Karaoke Version)", null, "Sing King"), // sin crédito de Queen
            Triple("Bohemian Rhapsody (Karaoke) - Originally Performed By Queen", null, "Karaoke Band"),
        )
        val ask = MusicRequestKaraoke.parse("Bohemian Rhapsody karaoke Queen")!!
        val pick = MusicRequestKaraoke.select(ask, search, target = 10) { it }
        assertEquals(Kind.SONG, pick.kind)
        assertEquals("con el artista en el crédito primero", "Bohemian Rhapsody (Karaoke) - Originally Performed By Queen", pick.ordered.first().title)
        assertEquals("el otro canal sigue de alternativa", 2, pick.ordered.size)
    }

    @Test
    fun `la pista de otra cancion con una palabra en comun no se cuela`() {
        val search = results(
            Triple("Amor (Karaoke Version)", null, "Sing King"),
            Triple("Amor Eterno (Karaoke Version)", null, "Karaoke Latino"),
            Triple("Amor Eterno (Pista Karaoke) - Juan Gabriel", null, "Pistas Latinas"),
            Triple("Eterno Amor (Karaoke)", null, "Karaoke Latino"),
        )
        val ask = MusicRequestKaraoke.parse("pista karaoke de Juan Gabriel Amor eterno")!!
        val pick = MusicRequestKaraoke.select(ask, search, target = 10) { it }
        assertEquals(Kind.SONG, pick.kind)
        val titles = pick.ordered.map { it.title }
        assertTrue(titles.all { it.startsWith("Amor Eterno") })
        assertTrue("la pista con el crédito del artista, primero", titles.first().contains("Juan Gabriel"))
    }

    @Test
    fun `una palabra de mas en el titulo del buscador no deja fuera la pista`() {
        val search = results(
            Triple("Livin' la Vida Loca (Karaoke Version)", null, "Sing King"),
            Triple("Livin la Vida Loca - Instrumental", null, "Instrumentals R Us"),
        )
        val ask = MusicRequestKaraoke.parse("letra y pista de Livin la vida loca")!!
        val pick = MusicRequestKaraoke.select(ask, search, target = 10) { it }
        assertEquals(Kind.SONG, pick.kind)
        assertEquals(2, pick.ordered.size)
        assertEquals("(Karaoke Version)", "Livin' la Vida Loca (Karaoke Version)", pick.ordered.first().title)
    }

    @Test
    fun `si dijo instrumental, los instrumentales van primero`() {
        val search = results(
            Triple("La Bamba (Karaoke Version)", null, "Sing King"),
            Triple("La Bamba (Instrumental)", null, "Backing Studio"),
        )
        val ask = MusicRequestKaraoke.parse("instrumental de La Bamba")!!
        val pick = MusicRequestKaraoke.select(ask, search, target = 10) { it }
        assertEquals("La Bamba (Instrumental)", pick.ordered.first().title)
        val karaoke = MusicRequestKaraoke.select(MusicRequestKaraoke.parse("karaoke de La Bamba")!!, search, 10) { it }
        assertEquals("La Bamba (Karaoke Version)", karaoke.ordered.first().title)
    }

    @Test
    fun `las versiones de piano o de cuna solo rellenan cuando casi no hay pistas`() {
        val search = results(
            Triple("Hotel California (Karaoke Version)", null, "Sing King"),
            Triple("Hotel California (Karaoke) - Originally Performed By Eagles", null, "Karaoke Band"),
            Triple("Hotel California (Piano Version)", null, "Piano Tribute"),
        )
        val ask = MusicRequestKaraoke.parse("la pista de Hotel California")!!
        val withTwo = MusicRequestKaraoke.select(ask, search, 10) { it }
        assertFalse(withTwo.ordered.any { it.title.contains("Piano") })
        val alone = MusicRequestKaraoke.select(ask, listOf(search[2]), 10) { it }
        assertEquals("con nada mejor, la de piano sirve", 1, alone.ordered.size)
    }

    @Test
    fun `karaoke de un artista - varias canciones, una pista por cancion`() {
        val search = results(
            Triple("We Will Rock You (Karaoke Version) [In the Style of Queen]", null, "Sing King"),
            Triple("Don't Stop Me Now - Karaoke - Originally Performed By Queen", null, "Karaoke Band"),
            Triple("We Will Rock You (Karaoke) - In the Style of Queen", null, "Other Channel"), // repite la canción
            Triple("Bohemian Rhapsody (Karaoke) - Made Famous By Queen", null, "Pista Latina"),
            Triple("Shallow (Karaoke Version) [In the Style of Lady Gaga]", null, "Sing King"), // otro artista
        )
        val ask = MusicRequestKaraoke.parse("karaoke de Queen")!!
        val pick = MusicRequestKaraoke.select(ask, search, target = 10) { it }
        assertEquals(Kind.ARTIST, pick.kind)
        val titles = pick.ordered.map { it.title }
        assertEquals(3, titles.size)
        assertFalse(titles.any { it.contains("Lady Gaga") })
        assertNull("no hay UNA pista elegida", pick.best)
    }

    @Test
    fun `karaoke de un estilo - solo pistas, sin originales`() {
        val search = results(
            Triple("Rock Hits (Original)", null, "Various"),
            Triple("Livin on a Prayer (Karaoke Version)", null, "Sing King"),
            Triple("Come Together (Karaoke)", null, "Karaoke Band"),
            Triple("Cuando Pase el Temblor (Pista Karaoke)", null, "Pista Latina"),
        )
        val ask = MusicRequestKaraoke.parse("karaoke de rock")!!
        val pick = MusicRequestKaraoke.select(ask, search, target = 10) { it }
        assertEquals(Kind.STYLE, pick.kind)
        assertEquals(3, pick.ordered.size)
        assertFalse(pick.ordered.any { it.title.contains("Original") })
    }

    @Test
    fun `sin ninguna pista no se inventa nada`() {
        val onlyOriginals = results(
            Triple("Despacito", "Vida", "Luis Fonsi"),
            Triple("Despacito (Remix)", null, "Justin Bieber"),
        )
        val ask = MusicRequestKaraoke.parse("karaoke de Despacito")!!
        val pick = MusicRequestKaraoke.select(ask, onlyOriginals, 10) { it }
        assertNull(pick.kind)
        assertTrue(pick.ordered.isEmpty())
        val otherSong = results(Triple("Gasolina (Karaoke Version)", null, "Sing King"))
        assertTrue("la pista de otra canción no vale", MusicRequestKaraoke.select(ask, otherSong, 10) { it }.ordered.isEmpty())
    }

    // ── el título de la tarjeta y la cola que sigue ────────────────────────────────────────────────────────────────

    @Test
    fun `la tarjeta lo dice - Karaoke dos puntos la cancion`() {
        val ask = MusicRequestKaraoke.parse("karaoke de Bohemian Rhapsody")!!
        assertEquals("Karaoke: Bohemian Rhapsody", MusicRequestKaraoke.label(ask, Kind.SONG, "Bohemian Rhapsody (Karaoke Version) - Sing King"))
        assertEquals("Karaoke: Queen", MusicRequestKaraoke.label(MusicRequestKaraoke.parse("karaoke de Queen")!!, Kind.ARTIST, null))
        assertEquals("Karaoke: Salsa", MusicRequestKaraoke.label(MusicRequestKaraoke.parse("karaoke de salsa")!!, Kind.STYLE, null))
        assertEquals(
            "Instrumental: La Bamba",
            MusicRequestKaraoke.label(MusicRequestKaraoke.parse("instrumental de La Bamba")!!, Kind.SONG, "La Bamba (Instrumental)"),
        )
        assertTrue(MusicRequestKaraoke.label(ask, Kind.SONG, "x".repeat(100)).length <= 40)
    }

    @Test
    fun `prettyTitle quita lo que envuelve a la cancion`() {
        assertEquals("Bohemian Rhapsody", MusicRequestKaraoke.prettyTitle("Bohemian Rhapsody (Karaoke Version)"))
        assertEquals("Bohemian Rhapsody", MusicRequestKaraoke.prettyTitle("Bohemian Rhapsody - Karaoke Version"))
        assertEquals("Hotel California", MusicRequestKaraoke.prettyTitle("Hotel California [Instrumental]"))
        assertEquals("Hello", MusicRequestKaraoke.prettyTitle("Hello (In the Style of Adele)"))
    }

    @Test
    fun `la lista cerrada de una cancion se recuerda por contexto y solo las ultimas`() {
        RequestKaraokeContexts.markClosed("MR:test-1")
        assertTrue(RequestKaraokeContexts.isClosed("MR:test-1"))
        assertFalse(RequestKaraokeContexts.isClosed("MR:other"))
        assertFalse(RequestKaraokeContexts.isClosed(null))
        for (i in 0 until 40) RequestKaraokeContexts.markClosed("MR:bulk-$i")
        assertFalse("la más vieja ya se olvidó", RequestKaraokeContexts.isClosed("MR:test-1"))
        assertTrue(RequestKaraokeContexts.isClosed("MR:bulk-39"))
    }

    @Test
    fun `el filtro anti-karaoke de siempre sigue quitando las pistas cuando no se piden`() {
        assertFalse(MusicRequestInstrumental.wantsInstrumental("salsa de los 80"))
        assertTrue(MusicRequestInstrumental.wantsInstrumental("karaoke de Despacito"))
        assertTrue("también la ortografía sin h", MusicRequestInstrumental.wantsInstrumental("pon caraoke de Despacito"))
        assertNotNull(MusicRequestKaraoke.parse("pon caraoke de Despacito"))
    }

    // ── la tabla: 32 formas de pedir la pista de una canción (y 8 que no lo son) ─────────────────────────────────────

    @Test
    fun `table of requests - the song to sing`() {
        val rows = listOf(
            "karaoke de Bohemian Rhapsody" to "bohemian rhapsody",
            "karaoke Bohemian Rhapsody" to "bohemian rhapsody",
            "Bohemian Rhapsody karaoke" to "bohemian rhapsody",
            "Bohemian Rhapsody en karaoke" to "bohemian rhapsody",
            "pon karaoke de Bohemian Rhapsody" to "bohemian rhapsody",
            "ponme el karaoke de Bohemian Rhapsody" to "bohemian rhapsody",
            "dame el karaoke de Bohemian Rhapsody por favor" to "bohemian rhapsody",
            "quiero el karaoke de Bohemian Rhapsody" to "bohemian rhapsody",
            "quiero cantar Bohemian Rhapsody" to "bohemian rhapsody",
            "necesito la pista de Bohemian Rhapsody" to "bohemian rhapsody",
            "busca la pista karaoke de Bohemian Rhapsody" to "bohemian rhapsody",
            "pista de Bohemian Rhapsody para cantar" to "bohemian rhapsody",
            "pista para cantar Bohemian Rhapsody" to "bohemian rhapsody",
            "Bohemian Rhapsody pista karaoke" to "bohemian rhapsody",
            "Bohemian Rhapsody sin voz" to "bohemian rhapsody",
            "Bohemian Rhapsody backing track" to "bohemian rhapsody",
            "Bohemian Rhapsody minus one" to "bohemian rhapsody",
            "karaoké de Bohemian Rhapsody" to "bohemian rhapsody",
            "caraoke de Bohemian Rhapsody" to "bohemian rhapsody",
            "karaoke de la canción Bohemian Rhapsody" to "bohemian rhapsody",
            "karaoke del tema Bohemian Rhapsody" to "bohemian rhapsody",
            "i want to sing Bohemian Rhapsody karaoke" to "bohemian rhapsody",
            "play the karaoke of Bohemian Rhapsody" to null, // inglés libre: se queda con su camino de siempre o lo lee mal; no se afirma
            "pon el karaoke de Amor Eterno de Juan Gabriel" to "amor eterno de juan gabriel",
            "karaoke de Hotel California de Eagles" to "hotel california de eagles",
            "karaoke Despacito Luis Fonsi" to "despacito luis fonsi",
            "karaoke de Yesterday de The Beatles" to "yesterday de the beatles",
            "karaoke de Hasta que te conocí" to "hasta que te conoci",
            "karaoke de Mi Gente" to "mi gente",
            "karaoke de 50 Cent In Da Club" to "50 cent in da club",
            "karaoke de Livin' la Vida Loca" to "livin la vida loca",
            "karaoke de 99 Luftballons" to "99 luftballons",
        )
        for ((prompt, expected) in rows) {
            if (expected == null) continue
            assertEquals("«$prompt»", expected, subjectOf(prompt))
        }
        assertTrue(rows.count { it.second != null } >= 30)
    }

    @Test
    fun `table of requests - not a backing track of one song`() {
        val rows = listOf(
            "música instrumental", "música instrumental para dormir", "piano instrumental relajante", "lo mejor del rock",
            "pista de baile", "pon música para cantar en el carro", "quiero música para cantar con mis amigos",
            "ponme algo para cantar bajo la ducha", "el reggaeton de siempre", "salsa romántica de los 90",
        )
        for (prompt in rows) assertNull("«$prompt» no es la pista de una canción", subjectOf(prompt)?.takeIf { prompt.contains("karaoke") })
        // Y los que sí traen una palabra de karaoke pero ninguna canción tampoco producen una petición vacía.
        assertNull(MusicRequestKaraoke.parse("karaoke por favor"))
        assertNull(MusicRequestKaraoke.parse("pon karaoke"))
        assertNull(MusicRequestKaraoke.parse("quiero cantar"))
    }
}
