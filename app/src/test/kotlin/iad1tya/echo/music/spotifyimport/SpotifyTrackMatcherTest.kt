package iad1tya.echo.music.spotifyimport

import com.aura.migration.model.SourceTrack
import com.aura.migration.model.YtmCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Registry row 832 — the Spotify import only takes the song the track really is (owner 2026-11-24: «quiero que la sincronización
 * sea exacta»). The old 0.5 floor on SpotifyMapper.matchScore accepted another song of the same artist with the same duration and
 * the same title by any artist, and gave 0.8 to any non-Latin candidate.
 */
class SpotifyTrackMatcherTest {

    private fun src(title: String, vararg artists: String, durationMs: Long? = 200_000L) =
        SourceTrack(title = title, artists = artists.toList(), durationMs = durationMs)

    private fun cand(id: String, title: String, vararg artists: String, durationMs: Long? = 200_000L, isSong: Boolean = true) =
        id to YtmCandidate(videoId = id, title = title, artists = artists.toList(), durationMs = durationMs, isSong = isSong)

    @Test
    fun `the exact song is accepted`() {
        assertEquals(
            "a",
            SpotifyTrackMatcher.pick(src("Blinding Lights", "The Weeknd"), listOf(cand("a", "Blinding Lights", "The Weeknd"))),
        )
    }

    @Test
    fun `another song by the same artist with the same duration is rejected`() {
        assertNull(
            SpotifyTrackMatcher.pick(
                src("Blinding Lights", "The Weeknd"),
                listOf(cand("a", "Save Your Tears", "The Weeknd", durationMs = 201_000L)),
            ),
        )
    }

    @Test
    fun `a title with one word swapped is another song`() {
        assertNull(
            SpotifyTrackMatcher.pick(src("Love Me", "Artist"), listOf(cand("a", "Love You", "Artist", durationMs = 201_000L))),
        )
    }

    @Test
    fun `a title with extra words on one side still matches`() {
        assertEquals(
            "a",
            SpotifyTrackMatcher.pick(src("Se Le Ve", "Bad Bunny"), listOf(cand("a", "Se Le Ve (with Arcangel)", "Bad Bunny"))),
        )
        assertEquals(
            "b",
            SpotifyTrackMatcher.pick(
                src("夜に駆ける", "YOASOBI", durationMs = 261_000L),
                listOf(cand("b", "夜に駆ける (Yoru ni Kakeru)", "YOASOBI", durationMs = 261_000L)),
            ),
        )
    }

    @Test
    fun `the same title by a different artist is rejected`() {
        assertNull(
            SpotifyTrackMatcher.pick(
                src("Hello", "Adele", durationMs = 295_000L),
                listOf(cand("a", "Hello", "Lionel Richie", durationMs = 295_000L)),
            ),
        )
    }

    @Test
    fun `a non-Latin exact match is accepted`() {
        assertEquals(
            "a",
            SpotifyTrackMatcher.pick(src("좋은 날", "아이유", durationMs = 234_000L), listOf(cand("a", "좋은 날", "아이유", durationMs = 234_000L))),
        )
        assertEquals(
            "b",
            SpotifyTrackMatcher.pick(src("夜に駆ける", "YOASOBI", durationMs = 261_000L), listOf(cand("b", "夜に駆ける", "YOASOBI", durationMs = 261_000L))),
        )
    }

    @Test
    fun `a different non-Latin song by the same artist is rejected`() {
        assertNull(
            SpotifyTrackMatcher.pick(
                src("좋은 날", "아이유", durationMs = 234_000L),
                listOf(cand("a", "밤편지", "아이유", durationMs = 234_000L)),
            ),
        )
        assertNull(
            SpotifyTrackMatcher.pick(
                src("Кукла колдуна", "Король и Шут"),
                listOf(cand("a", "Лесник", "Король и Шут")),
            ),
        )
    }

    @Test
    fun `an empty normalized title or artist never matches`() {
        // Source side: nothing left after normalizing.
        assertNull(SpotifyTrackMatcher.pick(src("!!!", "Artist"), listOf(cand("a", "!!!", "Artist"))))
        assertNull(SpotifyTrackMatcher.pick(src("Song", "???"), listOf(cand("a", "Song", "???"))))
        assertNull(SpotifyTrackMatcher.pick(src("Song"), listOf(cand("a", "Song", "Artist"))))
        // Candidate side.
        assertNull(SpotifyTrackMatcher.pick(src("Song", "Artist"), listOf(cand("a", "★", "Artist"))))
        assertNull(SpotifyTrackMatcher.pick(src("Song", "Artist"), listOf(cand("a", "Song", "…"))))
    }

    @Test
    fun `the same recording uploaded twice is not ambiguity`() {
        assertEquals(
            "album",
            SpotifyTrackMatcher.pick(
                src("Blinding Lights", "The Weeknd"),
                listOf(
                    cand("album", "Blinding Lights", "The Weeknd", durationMs = 200_000L),
                    cand("single", "Blinding Lights", "The Weeknd", durationMs = 201_000L),
                ),
            ),
        )
    }

    @Test
    fun `two different versions too close to tell apart are rejected`() {
        // No duration on the source: both score the same, and they are 15 s apart (not the same recording).
        assertNull(
            SpotifyTrackMatcher.pick(
                src("Hello", "Adele", durationMs = null),
                listOf(
                    cand("a", "Hello", "Adele", durationMs = 295_000L),
                    cand("b", "Hello", "Adele", durationMs = 310_000L),
                ),
            ),
        )
    }

    @Test
    fun `a featured artist credited on only one side still matches`() {
        assertEquals(
            "a",
            SpotifyTrackMatcher.pick(
                src("Se Le Ve", "Bad Bunny", "Chencho Corleone"),
                listOf(cand("a", "Se Le Ve", "Chencho Corleone")),
            ),
        )
    }

    @Test
    fun `the right song wins over a same-artist neighbour`() {
        assertEquals(
            "right",
            SpotifyTrackMatcher.pick(
                src("Blinding Lights", "The Weeknd"),
                listOf(
                    cand("wrong", "Save Your Tears", "The Weeknd", durationMs = 200_000L),
                    cand("right", "Blinding Lights", "The Weeknd", durationMs = 200_000L),
                ),
            ),
        )
    }
}
