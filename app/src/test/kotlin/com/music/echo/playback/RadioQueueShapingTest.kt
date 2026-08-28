package iad1tya.echo.music.playback

import androidx.media3.common.MediaItem
import com.google.common.collect.ImmutableList
import iad1tya.echo.music.dislike.DislikeStore
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.reco.TasteProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization of the two stateless post-sort passes that shape the radio queue order, extracted
 * verbatim from MusicService.orderedByTaste into RadioQueueShaping (HALLAZGO-021 split, phase A).
 *
 * Pinned facts: spacing keys on the PRIMARY (first) artist only, case-insensitively, over a sliding
 * window of the last 2 placed slots, with take-the-head fallback when every remaining candidate repeats
 * (never drops, never dead-ends); the exploration quota reserves every 15th slot for an artist the taste
 * profile does not know, [blocked] ids may not claim a reserved slot but are never dropped, and an
 * all-blocked fresh partition is a strict no-op (registry #39/#41).
 */
class RadioQueueShapingTest {

    // MediaItem cannot be built through its public Builder on the plain JVM unit-test source set: the
    // Builder only keeps the tag when a URI is set, and setUri(String) calls android.net.Uri.parse,
    // which is a stub there. Both package-private constructors are pure field assignments though
    // (verified in the media3-common 1.10.1 bytecode: no checkNotNull, no android.* calls — even the
    // class initializers are pure), so reflection builds an item whose `metadata` extension (which is
    // all these two passes read, plus the public mediaId field) works exactly like production.
    private fun mediaItem(id: String, artist: String? = null): MediaItem =
        mediaItem(id, if (artist != null) listOf(artist) else emptyList())

    private fun mediaItem(id: String, artists: List<String>): MediaItem {
        val tag = MediaMetadata(
            id = id,
            title = id,
            artists = artists.map { MediaMetadata.Artist(id = null, name = it) },
            duration = 0,
        )
        val localConfigurationCtor = MediaItem.LocalConfiguration::class.java.getDeclaredConstructor(
            android.net.Uri::class.java,
            String::class.java,
            MediaItem.DrmConfiguration::class.java,
            MediaItem.AdsConfiguration::class.java,
            List::class.java,
            String::class.java,
            ImmutableList::class.java,
            Object::class.java,
            Long::class.javaPrimitiveType,
        )
        localConfigurationCtor.isAccessible = true
        val localConfiguration = localConfigurationCtor.newInstance(
            null, null, null, null,
            emptyList<MediaItem.SubtitleConfiguration>(),
            null,
            ImmutableList.of<MediaItem.SubtitleConfiguration>(),
            tag,
            0L,
        )
        val mediaItemCtor = MediaItem::class.java.getDeclaredConstructor(
            String::class.java,
            MediaItem.ClippingProperties::class.java,
            MediaItem.LocalConfiguration::class.java,
            MediaItem.LiveConfiguration::class.java,
            androidx.media3.common.MediaMetadata::class.java,
            MediaItem.RequestMetadata::class.java,
        )
        mediaItemCtor.isAccessible = true
        return mediaItemCtor.newInstance(id, null, localConfiguration, null, null, null)
    }

    private fun profileWithKnownArtists(vararg knownArtists: String): TasteProfile = TasteProfile(
        artistWeightById = emptyMap(),
        artistWeightByName = knownArtists.associate { it.lowercase() to 1.0 },
        laneWeight = emptyMap(),
        genreWeight = emptyMap(),
        artistGenres = emptyMap(),
        disliked = DislikeStore.Disliked(),
        maxArtistWeight = 1.0,
        maxGenreWeight = 1.0,
    )

    private fun List<MediaItem>.ids(): List<String> = map { it.mediaId }

    // ------------------------------------------------------------- spacedByArtist

    @Test
    fun spacedByArtistUnderThreeItemsIsUnchanged() {
        val two = listOf(mediaItem("a", "A"), mediaItem("b", "A"))
        assertSame(two, RadioQueueShaping.spacedByArtist(two))
        assertSame(emptyList<MediaItem>(), RadioQueueShaping.spacedByArtist(emptyList()))
    }

    @Test
    fun spacedByArtistKeepsOrderWithoutRepeats() {
        val items = listOf(mediaItem("1", "A"), mediaItem("2", "B"), mediaItem("3", "C"))
        assertEquals(listOf("1", "2", "3"), RadioQueueShaping.spacedByArtist(items).ids())
    }

    @Test
    fun spacedByArtistBreaksSameArtistStreaks() {
        val items = listOf(
            mediaItem("a1", "Artist"),
            mediaItem("a2", "Artist"),
            mediaItem("a3", "Artist"),
            mediaItem("b1", "Other"),
        )
        // a2/a3 are skipped while "artist" is still in the 2-slot window; b1 is lifted next, then the
        // tail falls back to the head of the remainder.
        assertEquals(listOf("a1", "b1", "a2", "a3"), RadioQueueShaping.spacedByArtist(items).ids())
    }

    @Test
    fun spacedByArtistWindowIsTwoSlots() {
        val items = listOf(
            mediaItem("a1", "A"),
            mediaItem("a2", "A"),
            mediaItem("b1", "B"),
            mediaItem("b2", "B"),
        )
        assertEquals(listOf("a1", "b1", "a2", "b2"), RadioQueueShaping.spacedByArtist(items).ids())
    }

    /** The fallback is take-the-head: a single-artist batch comes out in its incoming order, never empty. */
    @Test
    fun spacedByArtistFallsBackToHeadWhenEverythingRepeats() {
        val items = listOf(mediaItem("a1", "A"), mediaItem("a2", "A"), mediaItem("a3", "A"))
        assertEquals(listOf("a1", "a2", "a3"), RadioQueueShaping.spacedByArtist(items).ids())
    }

    @Test
    fun spacedByArtistMatchingIsCaseInsensitive() {
        val items = listOf(mediaItem("a1", "beat"), mediaItem("a2", "BEAT"), mediaItem("b1", "other"))
        assertEquals(listOf("a1", "b1", "a2"), RadioQueueShaping.spacedByArtist(items).ids())
    }

    /** An item with no primary artist is always eligible (a == null branch), so it can break a streak. */
    @Test
    fun spacedByArtistItemWithoutArtistIsAlwaysEligible() {
        val items = listOf(mediaItem("a1", "A"), mediaItem("a2", "A"), mediaItem("x"))
        assertEquals(listOf("a1", "x", "a2"), RadioQueueShaping.spacedByArtist(items).ids())
    }

    /** Spacing keys on the FIRST artist only — a shared second artist does not count as a repeat. */
    @Test
    fun spacedByArtistKeysOnPrimaryArtistOnly() {
        val items = listOf(
            mediaItem("ab1", listOf("A", "B")),
            mediaItem("ac2", listOf("A", "C")),
            mediaItem("d1", listOf("D")),
        )
        assertEquals(listOf("ab1", "d1", "ac2"), RadioQueueShaping.spacedByArtist(items).ids())
    }

    @Test
    fun spacedByArtistPreservesLengthAndMembership() {
        val items = listOf(
            mediaItem("a1", "A"), mediaItem("a2", "A"), mediaItem("a3", "A"),
            mediaItem("b1", "B"), mediaItem("c1", "C"),
        )
        val out = RadioQueueShaping.spacedByArtist(items)
        assertEquals(items.size, out.size)
        assertEquals(items.ids().toSet(), out.ids().toSet())
    }

    // --------------------------------------------------------- withExplorationQuota

    @Test
    fun quotaNullProfileIsUnchanged() {
        val items = (1..10).map { mediaItem("k$it", "Known") }
        assertSame(items, RadioQueueShaping.withExplorationQuota(items, null))
    }

    @Test
    fun quotaUnderEightItemsIsUnchanged() {
        val profile = profileWithKnownArtists("Known")
        val items = (1..6).map { mediaItem("k$it", "Known") } + mediaItem("f1", "Fresh")
        assertSame(items, RadioQueueShaping.withExplorationQuota(items, profile))
    }

    @Test
    fun quotaAllKnownOrAllFreshIsUnchanged() {
        val profile = profileWithKnownArtists("Known")
        val allKnown = (1..10).map { mediaItem("k$it", "Known") }
        assertSame(allKnown, RadioQueueShaping.withExplorationQuota(allKnown, profile))

        val allFresh = (1..10).map { mediaItem("f$it", "Fresh$it") }
        assertSame(allFresh, RadioQueueShaping.withExplorationQuota(allFresh, profile))
    }

    @Test
    fun quotaFreshCandidateTakesTheFifteenthSlot() {
        val profile = profileWithKnownArtists("Known")
        val items = (1..20).map { mediaItem("k$it", "Known") } +
            listOf(mediaItem("f1", "Fresh1"), mediaItem("f2", "Fresh2"))
        val out = RadioQueueShaping.withExplorationQuota(items, profile)
        assertEquals(22, out.size)
        // pos % 15 == 14 → the first reserved slot is index 14; the known partition keeps its order.
        assertEquals((1..14).map { "k$it" }, out.take(14).ids())
        assertEquals("f1", out[14].mediaId)
        assertEquals((15..20).map { "k$it" } + "f2", out.drop(15).ids())
    }

    /**
     * The steer-protection rule: a blocked id is moved OUT of the fresh partition (it joins `known` in
     * input order) but the reserved slot goes to an unblocked fresh candidate — the quota can no longer
     * undo the genre steer by lifting the very song it just demoted.
     */
    @Test
    fun quotaBlockedFreshCannotClaimReservedSlot() {
        val profile = profileWithKnownArtists("Known")
        val items = (1..14).map { mediaItem("k$it", "Known") } +
            listOf(mediaItem("f1", "Fresh1"), mediaItem("f2", "Fresh2"))
        val out = RadioQueueShaping.withExplorationQuota(items, profile, blocked = setOf("f1"))
        assertEquals(16, out.size)
        assertEquals("f2", out[14].mediaId)
        // f1 keeps its sorted position right behind the known partition — never dropped.
        assertEquals("f1", out[15].mediaId)
    }

    /** Registry #39/#41 boundary: if EVERY fresh candidate is blocked the interleave no-ops entirely. */
    @Test
    fun quotaAllFreshBlockedIsANoOp() {
        val profile = profileWithKnownArtists("Known")
        val items = (1..14).map { mediaItem("k$it", "Known") } +
            listOf(mediaItem("f1", "Fresh1"), mediaItem("f2", "Fresh2"))
        assertSame(items, RadioQueueShaping.withExplorationQuota(items, profile, blocked = setOf("f1", "f2")))
    }

    /** Freshness requires a non-null primary artist: an artist-less item joins the known partition. */
    @Test
    fun quotaArtistlessItemIsNeverFresh() {
        val profile = profileWithKnownArtists("Known")
        val items = (1..8).map { mediaItem("k$it", "Known") } + mediaItem("x")
        assertSame(items, RadioQueueShaping.withExplorationQuota(items, profile))
    }

    @Test
    fun quotaPreservesLengthAndMembership() {
        val profile = profileWithKnownArtists("Known")
        val items = (1..20).map { mediaItem("k$it", "Known") } +
            (1..5).map { mediaItem("f$it", "Fresh$it") }
        val out = RadioQueueShaping.withExplorationQuota(items, profile)
        assertEquals(items.size, out.size)
        assertEquals(items.ids().toSet(), out.ids().toSet())
        assertTrue("no duplicates", out.ids().toSet().size == out.size)
    }
}
