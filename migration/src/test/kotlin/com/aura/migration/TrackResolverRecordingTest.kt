package com.aura.migration

import com.aura.migration.db.MatchCache
import com.aura.migration.db.MatchCacheDao
import com.aura.migration.db.MatchCacheEntity
import com.aura.migration.model.MatchResult
import com.aura.migration.model.SourceTrack
import com.aura.migration.model.YtmCandidate
import com.aura.migration.resolver.Scorer
import com.aura.migration.resolver.TrackResolver
import com.aura.migration.resolver.YtmClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 843 — the migration only inserts on its own a candidate that is the same recording. The Scorer alone gives a
 * «(Piano)» or «(Nightcore)» upload of the same artist and length ~108 (those words are not in its VERSION_TAGS), over ACCEPT.
 */
class TrackResolverRecordingTest {

    private class FakeYtm(private val results: List<YtmCandidate>) : YtmClient {
        override suspend fun searchSongs(query: String, limit: Int) = results
        override suspend fun searchAll(query: String, limit: Int) = results
        override suspend fun createPlaylist(name: String, description: String?) = ""
        override suspend fun addToPlaylist(playlistId: String, videoIds: List<String>) {}
    }

    private class EmptyDao : MatchCacheDao {
        override suspend fun get(key: String): MatchCacheEntity? = null
        override suspend fun getAll(keys: List<String>) = emptyList<MatchCacheEntity>()
        override suspend fun upsert(entity: MatchCacheEntity) {}
        override suspend fun clearAutomatic() {}
    }

    private val source = SourceTrack(title = "A Song", artists = listOf("Artist"), durationMs = 200_000L)

    private fun resolve(vararg candidates: YtmCandidate): MatchResult = runBlocking {
        TrackResolver(FakeYtm(candidates.toList()), MatchCache(EmptyDao())).resolve(source)
    }

    private fun cand(id: String, title: String, artist: String = "Artist", durationMs: Long = 200_000L) =
        YtmCandidate(videoId = id, title = title, artists = listOf(artist), durationMs = durationMs)

    @Test
    fun `the studio song is still inserted`() {
        val result = resolve(cand("studio", "A Song"))
        assertTrue(result is MatchResult.Matched)
        assertEquals("studio", (result as MatchResult.Matched).videoId)
    }

    @Test
    fun `another recording that the Scorer would accept goes to review instead`() {
        for (label in listOf("Piano", "Nightcore", "A Cappella", "Orchestral", "8D Audio", "Dub")) {
            val variant = cand("variant", "A Song ($label)")
            assertTrue("Scorer alone accepts $label", Scorer.score(source, variant) >= Scorer.ACCEPT)
            val result = resolve(variant)
            assertTrue("$label must not be inserted: $result", result is MatchResult.Ambiguous)
        }
    }

    @Test
    fun `a tribute band with the same title and length goes to review`() {
        val tribute = cand("tribute", "A Song", artist = "Artist Tribute Band")
        val result = resolve(tribute)
        assertTrue("$result", result !is MatchResult.Matched)
    }

    @Test
    fun `a remastered title from a source without durations is still inserted`() {
        val noDuration = SourceTrack(title = "Yesterday - Remastered 2009", artists = listOf("The Beatles"))
        val original = YtmCandidate(videoId = "song", title = "Yesterday", artists = listOf("The Beatles"), durationMs = 125_000L)
        assertTrue(Scorer.score(noDuration, original) >= Scorer.ACCEPT)
        val result = runBlocking { TrackResolver(FakeYtm(listOf(original)), MatchCache(EmptyDao())).resolve(noDuration) }
        assertTrue("$result", result is MatchResult.Matched)
        assertEquals("song", (result as MatchResult.Matched).videoId)
    }

    /** Answers only the album query with the studio song; every other query gets the piano upload. */
    private class AlbumOnlyYtm(private val albumQuery: String) : YtmClient {
        val queries = mutableListOf<String>()
        private val piano = YtmCandidate(videoId = "piano", title = "A Song (Piano)", artists = listOf("Artist"), durationMs = 200_000L)
        private val studio = YtmCandidate(
            videoId = "studio", title = "A Song", artists = listOf("Artist"), album = "The Album", durationMs = 200_000L,
        )
        override suspend fun searchSongs(query: String, limit: Int): List<YtmCandidate> {
            queries += query
            return if (query == albumQuery) listOf(studio) else listOf(piano)
        }
        override suspend fun searchAll(query: String, limit: Int) = listOf(piano)
        override suspend fun createPlaylist(name: String, description: String?) = ""
        override suspend fun addToPlaylist(playlistId: String, videoIds: List<String>) {}
    }

    @Test
    fun `a rejected recording above ACCEPT does not stop the album query from finding the studio song`() {
        val withAlbum = source.copy(album = "The Album")
        val ytm = AlbumOnlyYtm("A Song The Album")
        val result = runBlocking { TrackResolver(ytm, MatchCache(EmptyDao())).resolve(withAlbum) }
        assertTrue("album query was not issued: ${ytm.queries}", "A Song The Album" in ytm.queries)
        assertTrue("$result", result is MatchResult.Matched)
        assertEquals("studio", (result as MatchResult.Matched).videoId)
    }
}
