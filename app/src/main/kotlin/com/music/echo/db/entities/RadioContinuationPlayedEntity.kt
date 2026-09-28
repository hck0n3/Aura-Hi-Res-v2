

package iad1tya.echo.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index

/**
 * Persistent no-repeat memory for the SMART INFINITE QUEUE CONTINUATION that follows a finished
 * collection (album/playlist/EP/single) — deliberately separate from [EnhancedShufflePlayedEntity],
 * which tracks the collection's OWN finite tracklist. This table tracks the songs the RADIO added
 * AFTER that collection ended, keyed by the same [contextId] Enhanced Shuffle already uses
 * ("AL:<id>"/"PL:<id>"/"OL:<browseId>", see [iad1tya.echo.music.playback.ShuffleContexts]).
 *
 * Ronda 10 (dueño): "si vuelvo a poner el mismo álbum, misma playlist, o mismo EP o single, la cola
 * no tiene que volver a repetir las mismas canciones que ya sonaron". The in-session memory
 * (`sessionPlayedIds`/`sessionPlayedDedupKeys` in MusicService) already covers this WITHIN one
 * continuous app process, but is lost whenever the process dies (background kill, phone restart) —
 * common on Android, and exactly the case the owner is asking to also cover. No TTL/cycle reset by
 * design, same philosophy as Enhanced Shuffle: "already played after this album" stays true forever,
 * not just for a session or a few minutes.
 *
 * No foreign keys on purpose, same reasoning as [EnhancedShufflePlayedEntity]: [contextId] is
 * synthetic and [songId] is a raw YouTube id that is not necessarily present in the `song` table.
 */
@Immutable
@Entity(
    tableName = "radio_continuation_played",
    primaryKeys = ["contextId", "songId"],
    indices = [Index(value = ["contextId"])],
)
data class RadioContinuationPlayedEntity(
    val contextId: String,
    val songId: String,
    val playedAt: Long,
)
