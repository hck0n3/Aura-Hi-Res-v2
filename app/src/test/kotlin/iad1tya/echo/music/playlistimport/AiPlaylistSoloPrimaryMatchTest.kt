package iad1tya.echo.music.playlistimport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks [AiPlaylistGenerator.soloPrimaryMatch] — the STRICT solo-artist gate behind the owner's
 * "AI must not improvise" directive: when the user asked for ONE artist, a resolved song where
 * that artist is only a DEEP featured credit must NOT enter the playlist.
 *
 * The old `artists.any { artistMatches(…) }` gate passed "Jhayco, Bad Bunny" (a Jhayco song with a
 * Bad Bunny guest verse) as "solo Bad Bunny". [SongResolver.artistMatches] itself was already
 * strict; the hole was the POSITION of the credit, not the name comparison.
 */
class AiPlaylistSoloPrimaryMatchTest {

    // --- the primary-credit cases (must keep working exactly as before) ---

    @Test fun nullSoloArtistAcceptsEverything() {
        assertTrue(AiPlaylistGenerator.soloPrimaryMatch(listOf("J Balvin"), null))
        assertTrue(AiPlaylistGenerator.soloPrimaryMatch(listOf("J Balvin", "Bad Bunny", "Feid"), null))
    }

    @Test fun blankSoloArtistAcceptsEverything() {
        assertTrue(AiPlaylistGenerator.soloPrimaryMatch(listOf("Anyone"), " "))
    }

    @Test fun soloArtistFirstCreditIsAccepted() {
        assertTrue(AiPlaylistGenerator.soloPrimaryMatch(listOf("Bad Bunny"), "Bad Bunny"))
        assertTrue(AiPlaylistGenerator.soloPrimaryMatch(listOf("Bad Bunny", "Jhayco"), "Bad Bunny"))
    }

    @Test fun accentAndCaseStillFold() {
        assertTrue(AiPlaylistGenerator.soloPrimaryMatch(listOf("bad bunny"), "Bad Bunny"))
        assertTrue(AiPlaylistGenerator.soloPrimaryMatch(listOf("José José"), "Jose Jose"))
    }

    @Test fun shortCollaborationShapeIsAcceptedInAnyOrder() {
        // "A & B" duet: the requested artist as the SECOND credit is still a primary credit.
        assertTrue(AiPlaylistGenerator.soloPrimaryMatch(listOf("Jhayco", "Bad Bunny"), "Bad Bunny"))
    }

    // --- the gap this gate closes: deep-featured credits ---

    @Test fun deepFeaturePositionIsRejected() {
        // Jhayco's song, Bad Bunny only as a late guest credit → NOT a "solo Bad Bunny" song.
        assertFalse(
            AiPlaylistGenerator.soloPrimaryMatch(
                listOf("Jhayco", "Tainy", "Mora", "Bad Bunny"),
                "Bad Bunny",
            ),
        )
    }

    @Test fun thirdCreditInLongListIsRejected() {
        assertFalse(
            AiPlaylistGenerator.soloPrimaryMatch(
                listOf("Tainy", "Mora", "Bad Bunny", "Feid"),
                "Bad Bunny",
            ),
        )
    }

    @Test fun otherArtistAloneIsRejected() {
        assertFalse(AiPlaylistGenerator.soloPrimaryMatch(listOf("J Balvin"), "Bad Bunny"))
    }

    @Test fun collabCreditWithArtistNameIsAccepted() {
        // "Bad Bunny & Jhayco" style single credit string still folds through artistMatches.
        assertTrue(AiPlaylistGenerator.soloPrimaryMatch(listOf("Bad Bunny & Jhayco"), "Bad Bunny"))
    }

    @Test fun emptyCreditsRejectedWhenSoloRequested() {
        assertFalse(AiPlaylistGenerator.soloPrimaryMatch(emptyList(), "Bad Bunny"))
    }
}
