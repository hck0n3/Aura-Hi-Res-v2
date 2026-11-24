/*
 * Adapted from ahnafnafee/songmirror@e5bad7a (MIT): `list_playlists`, `playlist_tracks` and
 * `favorite_tracks` of songmirror/engine/targets/amazon_music.py (web-player branch, READ side only).
 * Copyright (c) 2026 Ahnaf An Nafee. The MIT permission notice is reproduced in full in AmazonMusicWeb.kt.
 */

package com.aura.migration.source.amazon

import com.aura.migration.model.CollectionKind
import com.aura.migration.model.SourcePlaylist
import com.aura.migration.model.SourceTrack
import com.aura.migration.model.SourceType
import com.aura.migration.source.PlaylistSource
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Amazon Music as a migration SOURCE, read only: the signed-in user's own playlists plus their liked
 * songs, every page to the end. Matching to YouTube Music is the engine's job, exactly as for Tidal.
 */
class AmazonMusicSource(private val client: AmazonMusicClient) : PlaylistSource {

    override val type = SourceType.AMAZON
    override val displayName = "Amazon Music"

    /** Library only: there is no link import (a shared Amazon link is not readable without its owner). */
    override fun accepts(input: String): Boolean = false

    /** «Canciones que te gustan» first, then every playlist the user owns. */
    override suspend fun listPlaylists(input: String?): List<SourcePlaylist> {
        val out = mutableListOf(
            SourcePlaylist(
                id = COLLECTION_LIKED_TRACKS,
                name = "Canciones que te gustan",
                // Amazon's liked-tracks connection carries no total; 0 is "unknown", as Tidal reports it.
                trackCount = 0,
                origin = type,
                kind = CollectionKind.FAVORITE_TRACKS,
            ),
        )
        val seen = mutableSetOf<String>()
        readAllPages { cursor ->
            val data = client.query(
                AmazonMusicWeb.PLAYLISTS_OPERATION,
                AmazonMusicWeb.PLAYLISTS_QUERY,
                variables(cursor),
            )
            val page = AmazonMusicWeb.parsePlaylistsPage(data, cursor)
            page.items.filterTo(out) { seen.add(it.id) }
            page.nextCursor
        }
        return out
    }

    override suspend fun fetchTracks(playlistId: String): List<SourceTrack> {
        val tracks = mutableListOf<SourceTrack>()
        readAllPages { cursor ->
            val page = if (playlistId == COLLECTION_LIKED_TRACKS) {
                val data = client.query(
                    AmazonMusicWeb.LIKED_TRACKS_OPERATION,
                    AmazonMusicWeb.LIKED_TRACKS_QUERY,
                    variables(cursor),
                )
                AmazonMusicWeb.parseLikedTracksPage(data, cursor, tracks.size)
            } else {
                val data = client.query(
                    AmazonMusicWeb.PLAYLIST_TRACKS_OPERATION,
                    AmazonMusicWeb.PLAYLIST_TRACKS_QUERY,
                    variables(cursor, id = playlistId),
                )
                AmazonMusicWeb.parsePlaylistTracksPage(data, cursor, tracks.size)
            }
            tracks += page.items
            page.nextCursor
        }
        return tracks
    }

    /**
     * Follows continuation tokens to the end. A token seen before (a cycle, not just a repeat of the last
     * one, which the parser already rejects) fails closed instead of looping or truncating.
     */
    private suspend fun readAllPages(readPage: suspend (cursor: String?) -> String?) {
        val seen = mutableSetOf<String>()
        var cursor: String? = null
        do {
            cursor = readPage(cursor)
            if (cursor != null && !seen.add(cursor)) {
                throw AmazonMusicException.incomplete("continuation token cycled")
            }
        } while (cursor != null)
    }

    private fun variables(cursor: String?, id: String? = null) = JsonObject(
        buildMap {
            id?.let { put("id", JsonPrimitive(it)) }
            put("cursor", cursor?.let { JsonPrimitive(it) } ?: JsonNull)
            put("limit", JsonPrimitive(AmazonMusicWeb.PAGE_SIZE))
        },
    )

    companion object {
        const val COLLECTION_LIKED_TRACKS = "__amazon_liked__"
    }
}
