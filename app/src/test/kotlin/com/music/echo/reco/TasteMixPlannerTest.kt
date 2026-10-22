package iad1tya.echo.music.reco

import iad1tya.echo.music.reco.TasteMixPlanner.Candidate
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-20 (Inicio, tile «Aleatorio · Algo de tu gusto»): *«lo vuelvo a tocar y repite el contenido… no sé cómo
 * estará funcionando bien el gusto mío… no me gusta que salga nada repetido»*. The planner is the pure half of the fix.
 */
class TasteMixPlannerTest {

    private fun song(
        n: Int,
        artist: String = "Artist ${n % 25}",
        affinity: Double = 0.0,
        liked: Boolean = false,
        playTimeMs: Long = 0L,
        title: String = "Song $n",
        id: String = "id$n",
    ) = Candidate(id, title, artist, affinity, liked, playTimeMs)

    private fun pool(count: Int) = (0 until count).map { song(it) }

    @Test
    fun `two consecutive taps share no songs while the pool allows it`() {
        val library = pool(200)
        val first = TasteMixPlanner.plan(library, memory = emptyMap(), random = Random(1))
        assertEquals(TasteMixPlanner.DEFAULT_SIZE, first.picks.size)
        assertFalse(first.cycleReset)

        // What the tile does after a tap: the whole served queue goes into the memory.
        val memory = first.picks.associate { it.id to 1_000L }
        val second = TasteMixPlanner.plan(library, memory, random = Random(2))

        assertEquals(TasteMixPlanner.DEFAULT_SIZE, second.picks.size)
        assertFalse(second.cycleReset)
        assertTrue("el segundo toque repitió canciones", first.picks.map { it.id }.intersect(second.picks.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun `a queue never holds the same song twice, even under two ids`() {
        val library = pool(80) +
            song(900, title = "Hello", artist = "Adele", id = "hello-audio") +
            song(901, title = "Hello (Official Video)", artist = "Adele", id = "hello-video") +
            song(902, title = "Hello - Remastered 2011", artist = "Adele - Topic", id = "hello-remaster")
        repeat(20) { seed ->
            val picks = TasteMixPlanner.plan(library, emptyMap(), random = Random(seed)).picks
            assertEquals(picks.size, picks.map { it.id }.toSet().size)
            assertTrue("tres versiones de la misma canción en una cola", picks.count { it.id.startsWith("hello") } <= 1)
        }
    }

    @Test
    fun `hearing one version blocks the other versions of the same song`() {
        val library = pool(40) +
            song(900, title = "Hello", artist = "Adele", id = "hello-audio") +
            song(901, title = "Hello (Official Video)", artist = "Adele", id = "hello-video")
        val plan = TasteMixPlanner.plan(library, memory = mapOf("hello-audio" to 5L), size = 10, random = Random(3))
        assertFalse(plan.cycleReset)
        assertTrue(plan.picks.none { it.id.startsWith("hello") })
    }

    @Test
    fun `an exhausted pool resets the lap and brings the least recently heard back first`() {
        val library = pool(60)
        val first = TasteMixPlanner.plan(library, emptyMap(), random = Random(4))
        // Heard in the order served: the first song is the OLDEST memory.
        val memory = first.picks.withIndex().associate { (i, c) -> c.id to (i + 1) * 1_000L }

        val second = TasteMixPlanner.plan(library, memory, random = Random(5))

        assertTrue("con 10 sin oír y 50 en la cola hay que cerrar la vuelta", second.cycleReset)
        assertEquals(TasteMixPlanner.DEFAULT_SIZE, second.picks.size)
        assertEquals(second.picks.size, second.picks.map { it.id }.toSet().size)
        val unheard = library.map { it.id }.toSet() - memory.keys
        val oldest40 = first.picks.take(40).map { it.id }.toSet()
        assertEquals(unheard + oldest40, second.picks.map { it.id }.toSet())
    }

    @Test
    fun `a pool smaller than a queue plays whole instead of failing`() {
        val plan = TasteMixPlanner.plan(pool(12), emptyMap(), random = Random(6))
        assertEquals(12, plan.picks.size)
        assertFalse(plan.cycleReset)
        assertTrue(TasteMixPlanner.plan(emptyList(), emptyMap()).picks.isEmpty())
    }

    @Test
    fun `taste decides - liked and most listened artists dominate a mix over neutral ones`() {
        val loved = (0 until 100).map {
            song(it, artist = "Loved ${it % 20}", affinity = 1.5, liked = true, playTimeMs = 40 * 60_000L)
        }
        val neutral = (100 until 200).map { song(it, artist = "Neutral ${it % 20}", affinity = 0.0, playTimeMs = 60_000L) }
        var lovedPicks = 0
        val seeds = 10
        repeat(seeds) { seed ->
            val picks = TasteMixPlanner.plan(loved + neutral, emptyMap(), size = 30, explorationShare = 0.0, random = Random(seed)).picks
            lovedPicks += picks.count { it.id.removePrefix("id").toInt() < 100 }
        }
        assertTrue("solo $lovedPicks de ${30 * seeds} fueron favoritas", lovedPicks >= 28 * seeds)
    }

    @Test
    fun `taste is a probability, not a fixed top - the same taste gives different mixes`() {
        val library = (0 until 150).map { song(it, affinity = (it % 10) / 5.0, playTimeMs = 120_000L) }
        val a = TasteMixPlanner.plan(library, emptyMap(), random = Random(11)).picks.map { it.id }
        val b = TasteMixPlanner.plan(library, emptyMap(), random = Random(12)).picks.map { it.id }
        assertTrue(a != b)
    }

    @Test
    fun `an unknown song can still come up - exploration takes its share from songs never listened to`() {
        val familiar = (0 until 100).map { song(it, affinity = 1.0, playTimeMs = 600_000L) }
        val unheard = (100 until 200).map { song(it, affinity = 0.0, playTimeMs = 0L) }
        val plan = TasteMixPlanner.plan(familiar + unheard, emptyMap(), random = Random(7))
        val explorationSlots = (TasteMixPlanner.DEFAULT_SIZE * TasteMixPlanner.EXPLORATION_SHARE).toInt()
        assertEquals(explorationSlots, plan.exploration)
        assertTrue(plan.picks.count { it.playTimeMs == 0L } >= explorationSlots)
    }

    @Test
    fun `without enough never-listened songs the exploration slots go back to taste, never to a shorter queue`() {
        val library = (0 until 80).map { song(it, affinity = 1.0, liked = true, playTimeMs = 300_000L) }
        val plan = TasteMixPlanner.plan(library, emptyMap(), random = Random(8))
        assertEquals(TasteMixPlanner.DEFAULT_SIZE, plan.picks.size)
        assertEquals(0, plan.exploration)
    }

    @Test
    fun `the same artist is kept apart while the pool allows it`() {
        val library = (0 until 120).map { song(it, artist = "Artist ${it % 12}") }
        repeat(10) { seed ->
            val picks = TasteMixPlanner.plan(library, emptyMap(), random = Random(seed)).picks
            picks.windowed(TasteMixPlanner.ARTIST_GAP + 1).forEach { window ->
                assertEquals("mismo artista a menos de ${TasteMixPlanner.ARTIST_GAP} canciones", window.size, window.map { it.artist }.toSet().size)
            }
        }
    }

    @Test
    fun `a single artist pool still plays - spacing relaxes instead of dropping songs`() {
        val library = (0 until 20).map { song(it, artist = "Only One") }
        val picks = TasteMixPlanner.plan(library, emptyMap(), size = 15, random = Random(9)).picks
        assertEquals(15, picks.size)
    }

    @Test
    fun `disliked songs never enter the mix`() {
        val library = pool(60) + song(500, affinity = TasteProfile.AVOID, id = "disliked")
        repeat(10) { seed ->
            assertTrue(TasteMixPlanner.plan(library, emptyMap(), random = Random(seed)).picks.none { it.id == "disliked" })
        }
    }

    @Test
    fun `the session memory is bounded and forgets the oldest first`() {
        TasteMixSession.clear()
        TasteMixSession.remember((0 until 900).map { "s$it" }, now = 1L)
        val snapshot = TasteMixSession.snapshot()
        assertEquals(800, snapshot.size)
        assertFalse(snapshot.containsKey("s0"))
        assertTrue(snapshot.containsKey("s899"))
        TasteMixSession.clear()
        assertTrue(TasteMixSession.snapshot().isEmpty())
    }

    // ── wiring guards: the tile must reach the planner, with ONE stable no-repeat bucket ─────────────────────────────

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the tile builds a taste mix on one stable context and falls back only for a tiny library`() {
        val home = main("ui/newui/AuraHomeScreen.kt")
        assertTrue(home.contains("viewModel.buildTasteMix("))
        assertTrue(home.contains("viewModel.commitTasteMix(mix)"))
        assertTrue("el respaldo sigue siendo la elección antigua", home.contains("viewModel.getRandomItem()"))
        assertTrue("pulsar mientras carga cancela", home.contains("running.cancel()"))

        val vm = main("viewmodels/HomeViewModel.kt")
        assertTrue(vm.contains("contextId = ctx"))
        assertTrue(vm.contains("ShuffleContexts.TASTE_MIX"))
        assertTrue(vm.contains("TasteMixPlanner.plan("))

        val ctx = main("playback/ShuffleContexts.kt")
        assertTrue("`AP:` porque la poda de huérfanos solo toca `PL:`", ctx.contains("const val TASTE_MIX = \"AP:taste-mix\""))

        val layout = main("ui/newui/AuraHomeLayout.kt")
        assertTrue("el subtítulo describe lo que hace", layout.contains("home_quick_surprise_built"))
    }
}
