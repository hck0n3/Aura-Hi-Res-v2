package com.aura.migration

import com.aura.migration.resolver.RecordingMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 842 — the recording rules adapted from ahnafnafee/songmirror@e5bad7a (MIT). Most cases are the ones SongMirror's
 * own tests pin (tests/test_recording_versions.py, tests/test_matching.py), with the same inputs.
 */
class RecordingMatcherTest {

    private fun accepts(
        title: String,
        artists: List<String>,
        durationMs: Long?,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationMs: Long?,
    ): Boolean =
        RecordingMatcher.score(title, artists, durationMs, candidateTitle, listOf(candidateArtist), candidateDurationMs).acceptable

    private fun same(title: String, candidate: String) = accepts(title, listOf("Artist"), 180_000L, candidate, "Artist", 180_000L)

    @Test
    fun `the same song is accepted`() {
        assertTrue(same("A Song", "A Song"))
        assertTrue(accepts("Blinding Lights", listOf("The Weeknd"), 200_000L, "Blinding Lights", "The Weeknd", 200_000L))
    }

    @Test
    fun `a live recording is never the studio one, in either direction`() {
        assertFalse(accepts("Post Break-Up Sex", listOf("The Vaccines"), 174_000L, "Post Break-Up Sex (Live in Brighton)", "The Vaccines", 174_000L))
        assertFalse(same("A Song", "A Song (Live)"))
        assertFalse(same("A Song (Live)", "A Song"))
        assertFalse(same("Wonderwall", "Wonderwall - Live at Wembley"))
        assertFalse(same("Canción", "Canción (En Vivo)"))
    }

    @Test
    fun `remix, cover, acoustic, sped up and the other variants are different recordings`() {
        val labels = listOf(
            "Remix", "Cover", "Acoustic", "Akustik", "Acústico", "Unplugged", "Stripped", "Sped Up", "Slowed + Reverb", "Nightcore",
            "Karaoke", "Instrumental", "Demo", "Dub", "Club Mix", "Extended Mix", "Radio Edit", "VIP", "A Cappella", "Orchestral",
            "Re-recorded", "Solo Version", "Piano", "8D Audio",
        )
        for (label in labels) {
            assertFalse("studio vs $label", same("A Song", "A Song ($label)"))
            assertFalse("$label vs studio", same("A Song ($label)", "A Song"))
        }
    }

    @Test
    fun `two named variants of the same family do not match`() {
        assertFalse(same("A Song (First DJ Remix)", "A Song (Second DJ Remix)"))
        assertFalse(same("A Song (Live at Wembley)", "A Song (Live at Glastonbury)"))
        assertFalse(same("A Song (Version 1)", "A Song (Version 2)"))
        assertFalse(same("A Song (Radio Edit)", "A Song (Single Edit)"))
    }

    @Test
    fun `the same variant spelled differently matches`() {
        assertTrue(same("A Song (Acoustic Version)", "A Song - Akustik"))
        assertTrue(accepts("Runaway - Piano Version", listOf("AURORA"), 243_000L, "Runaway (Piano)", "AURORA", 243_500L))
        assertTrue(same("A Song (Live)", "A Song - Live"))
    }

    @Test
    fun `a remaster, clean or explicit label is the same audio`() {
        assertTrue(accepts("Elegia - 2015 Remaster", listOf("New Order"), 293_000L, "Elegia", "New Order", 293_500L))
        assertTrue(same("A Song", "A Song (Remastered 2011)"))
        assertTrue(same("A Song (Explicit)", "A Song"))
        assertTrue(same("A Song", "A Song - Clean"))
        // A remaster is not a different version, but it never hides a creative one.
        assertFalse(same("A Song", "A Song (Live) [2011 Remaster]"))
    }

    @Test
    fun `a featured credit in the title is accepted when the candidate credits that guest`() {
        assertTrue(accepts("A Song (feat. Guest)", listOf("Artist"), 180_000L, "A Song", "Artist, Guest", 180_000L))
        assertTrue(accepts("A Song", listOf("Artist", "Guest"), 180_000L, "A Song (feat. Guest)", "Artist", 180_000L))
        assertTrue(accepts("A Song feat. Guest (Acoustic)", listOf("Artist"), 180_000L, "A Song (Acoustic) [ft. Guest]", "Artist", 180_000L))
        assertTrue(
            accepts("Hands on the Wheel (feat. Asap Rocky)", listOf("ScHoolboy Q"), 197_131L, "Hands On The Wheel (feat. A\$AP Rocky)", "ScHoolboy Q", 197_132L),
        )
    }

    @Test
    fun `a guest the wanted track credits must be on the candidate`() {
        assertFalse(accepts("A Song (feat. Guest)", listOf("Artist"), 180_000L, "A Song", "Artist", 180_000L))
        assertFalse(accepts("A Song (feat. First Guest)", listOf("Artist"), 180_000L, "A Song (feat. Second Guest)", "Artist", 180_000L))
        assertFalse(
            accepts("Hands on the Wheel (feat. Asap Rocky)", listOf("ScHoolboy Q"), 197_131L, "Hands On The Wheel (feat. A\$AP Ferg)", "ScHoolboy Q", 197_132L),
        )
    }

    @Test
    fun `explicit credit separators allow a provider's primary credit`() {
        for (separator in listOf("ft.", "with", "feat.", "&", ",")) {
            assertTrue(separator, accepts("A Song", listOf("Original Artist $separator Guest"), 180_000L, "A Song", "Original Artist", 180_000L))
        }
        assertTrue(accepts("Jeena Sikha De", listOf("Arijit Singh", "Ved Sharma", "Kunaal Vermaa"), 271_000L, "Jeena Sikha De", "Arijit Singh", 271_000L))
    }

    @Test
    fun `a different performer is rejected, also behind a shared guest or a tribute band`() {
        assertFalse(accepts("A Song", listOf("Black Pistol Fire"), 229_320L, "A Song", "Black Math", 229_320L))
        assertFalse(accepts("A Song", listOf("AURORA"), 248_826L, "A Song", "AURORA Tribute Band", 248_826L))
        assertFalse(accepts("A Song", listOf("Original Artist", "Shared Guest"), 180_000L, "A Song", "Different Artist, Shared Guest", 180_000L))
        assertFalse(accepts("Oniket Prantor", listOf("Artcell"), 341_000L, "Oniket Prantor", "Mehedi H Joy", 134_000L))
        assertFalse(accepts("Hello", listOf("Adele"), 295_000L, "Hello", "Lionel Richie", 295_000L))
    }

    @Test
    fun `an artist starting with a separator word is compared whole`() {
        assertTrue(accepts("Renegades", listOf("X Ambassadors"), 200_000L, "Renegades", "X Ambassadors", 200_000L))
        assertFalse(accepts("Renegades", listOf("X Ambassadors"), 200_000L, "Renegades", "X Japan", 200_000L))
    }

    @Test
    fun `a leading article and punctuation in a stage name do not break the artist`() {
        assertTrue(accepts("Blinding Lights", listOf("The Weeknd"), 200_000L, "Blinding Lights", "Weeknd", 200_000L))
        assertTrue(accepts("Thunderstruck", listOf("AC/DC"), 292_000L, "Thunderstruck", "ACDC", 292_000L))
        assertTrue(accepts("A Song", listOf("Adele"), 200_000L, "A Song", "Adele - Topic", 200_000L))
    }

    @Test
    fun `durations further apart than five seconds are different recordings`() {
        assertFalse(accepts("A Song", listOf("Artist"), 180_000L, "A Song", "Artist", 230_000L))
        assertFalse(accepts("Runaway - Piano Version", listOf("AURORA"), 243_000L, "Runaway", "AURORA", 309_000L))
        assertFalse(accepts("A Song", listOf("Artist"), 180_000L, "A Song", "Artist", 185_100L))
        assertTrue(accepts("A Song", listOf("Artist"), 180_000L, "A Song", "Artist", 185_000L))
    }

    @Test
    fun `a title matched only on shared words needs the duration within two and a half seconds`() {
        val title = "Se Le Ve"
        val decorated = "Se Le Ve (Original Motion Picture Soundtrack)"
        assertTrue(accepts(title, listOf("Bad Bunny"), 200_000L, decorated, "Bad Bunny", 202_400L))
        assertFalse(accepts(title, listOf("Bad Bunny"), 200_000L, decorated, "Bad Bunny", 203_000L))
        assertFalse(accepts(title, listOf("Bad Bunny"), null, decorated, "Bad Bunny", 200_000L))
    }

    @Test
    fun `non-Latin titles match exactly and never by an empty string`() {
        assertTrue(accepts("좋은 날", listOf("아이유"), 234_000L, "좋은 날", "아이유", 234_000L))
        assertTrue(accepts("夜に駆ける", listOf("YOASOBI"), 261_000L, "夜に駆ける", "YOASOBI", 261_000L))
        assertTrue(accepts("夜に駆ける", listOf("YOASOBI"), 261_000L, "夜に駆ける (Yoru ni Kakeru)", "YOASOBI", 261_000L))
        assertFalse(accepts("좋은 날", listOf("아이유"), 234_000L, "밤편지", "아이유", 234_000L))
        assertFalse(accepts("Кукла колдуна", listOf("Король и Шут"), 200_000L, "Лесник", "Король и Шут", 200_000L))
    }

    @Test
    fun `rapidfuzz semantics`() {
        assertEquals(1.0, RecordingMatcher.tokenSetRatio("se le ve", "se le ve with arcangel"), 1e-9)
        assertEquals(1.0, RecordingMatcher.tokenSortRatio("bad bunny tito", "tito bad bunny"), 1e-9)
        // fuzz.ratio("love me", "love you") = 2 * 5 / 15
        assertEquals(2.0 * 5 / 15, RecordingMatcher.ratio("love me", "love you"), 1e-9)
        assertEquals(0.0, RecordingMatcher.tokenSetRatio("", "a"), 1e-9)
    }

    @Test
    fun `normalization keeps every script and folds the Turkish dotted I`() {
        assertEquals("iki şeye pişmanım", RecordingMatcher.normalizeText("İki Şeye Pişmanım"))
        assertEquals("嘘", RecordingMatcher.normalizeText("嘘"))
        assertEquals("камин", RecordingMatcher.looseName("Камин (feat. JONY)"))
        assertEquals("song", RecordingMatcher.looseName("Song (feat. Guest)"))
        assertEquals("twin version", RecordingMatcher.looseName("Twin Ver."))
    }
}
