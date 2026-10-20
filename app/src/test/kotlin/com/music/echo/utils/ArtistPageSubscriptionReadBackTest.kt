package iad1tya.echo.music.utils

import iad1tya.echo.music.db.entities.ArtistEntity
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-20: «los cantantes a los que ya estoy suscrito, en algunos al meterme me aparece que no
 * estoy suscrito; no es con todos».
 *
 * The artist screens showed only the LOCAL `followedByUserAt`, which the library sync stamps by exact id
 * and taps write. An artist the account follows but Room had not stamped for the id the page was opened
 * under read "Suscribirse". The page now asks the signed-in account (its subscribe button) and folds a
 * "yes" into Room through [ArtistSyncPolicy.healFromRemoteSubscription]. These tests pin the decision
 * (it may only move a row TOWARDS followed, and must never undo an unfollow that is still in flight) and
 * the wiring (both screens read the ViewModel's single answer).
 */
class ArtistPageSubscriptionReadBackTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 20, 12, 0)
    private val longAgo: LocalDateTime = now.minusDays(3)

    private fun fresh(id: String = "UCabc") = ArtistEntity(id = id, name = "Artist", channelId = id)

    @Test
    fun `an artist with no row is created as a follow in sync with the account`() {
        val healed = ArtistSyncPolicy.healFromRemoteSubscription(null, fresh(), now)
        assertNotNull(healed)
        assertEquals(now, healed!!.followedByUserAt)
        assertEquals(now, healed.ytmSyncedAt)
        assertEquals(now, healed.bookmarkedAt)
        assertNull(healed.unfollowedByUserAt)
    }

    @Test
    fun `a row an incidental song created is promoted to a follow`() {
        val existing = fresh().copy(bookmarkedAt = null)
        val healed = ArtistSyncPolicy.healFromRemoteSubscription(existing, fresh(), now)!!
        assertEquals(now, healed.followedByUserAt)
        assertEquals("UCabc", healed.id)
    }

    @Test
    fun `a row that is already followed is left alone`() {
        val existing = fresh().copy(followedByUserAt = longAgo, ytmSyncedAt = longAgo)
        assertNull(ArtistSyncPolicy.healFromRemoteSubscription(existing, fresh(), now))
    }

    @Test
    fun `a local artist is never touched`() {
        val existing = fresh("LAabcdefgh").copy(isLocal = true)
        assertNull(ArtistSyncPolicy.healFromRemoteSubscription(existing, fresh("LAabcdefgh"), now))
    }

    @Test
    fun `a pending unsubscribe the uploader will deliver is not overruled`() {
        // marker + ytmSyncedAt + no follow = ArtistSyncPolicy.mayUnsubscribe: the account is about to change.
        val pending = fresh().copy(unfollowedByUserAt = now.minusDays(1), ytmSyncedAt = longAgo)
        assertTrue(ArtistSyncPolicy.mayUnsubscribe(pending, now))
        assertNull(ArtistSyncPolicy.healFromRemoteSubscription(pending, fresh(), now))
    }

    @Test
    fun `an unfollow tapped a moment ago is not undone by a lagging answer`() {
        val justNow = fresh().copy(unfollowedByUserAt = now.minusMinutes(2))
        assertNull(ArtistSyncPolicy.healFromRemoteSubscription(justNow, fresh(), now))
    }

    @Test
    fun `an old unfollow that cannot be retried gives way to the account's newer subscription`() {
        // The user unfollowed days ago, then subscribed again on YouTube itself. ytmSyncedAt is null, so
        // artistsPendingUnsubscribe can never see this row: the marker is dead weight and the account wins.
        val stale = fresh().copy(unfollowedByUserAt = longAgo)
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(stale, now))
        val healed = ArtistSyncPolicy.healFromRemoteSubscription(stale, fresh(), now)!!
        assertEquals(now, healed.followedByUserAt)
        assertNull(healed.unfollowedByUserAt)
    }

    @Test
    fun `healing never leaves a row the uploader would act on`() {
        val healed = ArtistSyncPolicy.healFromRemoteSubscription(fresh().copy(unfollowedByUserAt = longAgo), fresh(), now)!!
        assertFalse("not a pending subscribe", ArtistSyncPolicy.maySubscribe(healed))
        assertFalse("not a pending unsubscribe", ArtistSyncPolicy.mayUnsubscribe(healed, now))
    }

    @Test
    fun `a channel id and its library spelling name the same artist`() {
        assertEquals(listOf("UCabc", "MPLAUCabc"), ArtistSyncPolicy.aliasIds("UCabc"))
        assertEquals(listOf("MPLAUCabc", "UCabc"), ArtistSyncPolicy.aliasIds("MPLAUCabc"))
    }

    @Test
    fun `ids that are not channels have no alias`() {
        assertEquals(listOf("LAabcdefgh"), ArtistSyncPolicy.aliasIds("LAabcdefgh"))
        assertEquals(listOf("FEmusic_library_privately_owned_artist_x"), ArtistSyncPolicy.aliasIds("FEmusic_library_privately_owned_artist_x"))
        assertEquals(listOf("MPLAxyz"), ArtistSyncPolicy.aliasIds("MPLAxyz"))
    }

    // ---- wiring (no Android SDK in the unit job, so pinned as source text) -----------------------

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `both artist screens show and toggle the ViewModel's single answer`() {
        for (screen in listOf("ui/newui/AuraArtistScreen.kt", "ui/screens/artist/ArtistScreen.kt")) {
            val s = main(screen)
            assertTrue("$screen lee isSubscribed", s.contains("viewModel.isSubscribed"))
            assertTrue("$screen alterna por el ViewModel", s.contains("viewModel.toggleSubscription()"))
            assertFalse(
                "$screen volvió a leer solo la fila del id abierto",
                s.contains("libraryArtist?.artist?.followedByUserAt"),
            )
        }
    }

    @Test
    fun `the page asks the signed-in account after it loads and never acts on a no`() {
        val vm = main("viewmodels/ArtistViewModel.kt")
        assertTrue(vm.contains("reconcileRemoteSubscription(filteredPage.artist)"))
        assertTrue(vm.contains("if (YouTube.artistSubscribed(artistId).getOrNull() != true) return@launch"))
        assertTrue(vm.contains("ArtistSyncPolicy.healFromRemoteSubscription("))
        // the request is logged in; the anonymous artist() page always says "not subscribed"
        val yt = File(root, "innertube/src/main/kotlin/com/music/innertube/YouTube.kt").readText()
        val call = yt.substringAfter("suspend fun artistSubscribed(").substringBefore("\n    }\n")
        assertTrue(call.contains("setLogin = true"))
    }
}
