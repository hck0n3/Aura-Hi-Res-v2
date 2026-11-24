package iad1tya.echo.music.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import iad1tya.echo.music.MainActivity
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.EnhancedShuffleKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playback.PlayerConnection
import iad1tya.echo.music.playback.queues.ListQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Registry row 785: the launcher's long-press shortcuts (Buscar / Biblioteca). They lived in `res/xml/shortcuts.xml` with
 * a target package written by hand (`iad1tya.echo.music`) — the OLD, frozen app — while this build is `iad1tya.aura.music`
 * (`.debug`, `.dev` with -Pnosub), so the shortcut opened the old app or nothing. A resource file cannot follow the build's
 * applicationId; the intent built here takes it from the running app ([Intent.setClassName] with a Context).
 *
 * Registry row 810 (owner H4, 2026-11-24): three more — «Seguir escuchando», «Aleatorio de Me gusta» and «Descargado».
 * Published with the others on every MainActivity creation (which is also when a language change takes effect), never
 * on a timer. Their actions are handled by MainActivity.handleDeepLinkIntent.
 */
object LauncherShortcuts {
    private const val TAG = "LauncherShortcuts"

    // Plain action names, not package-shaped: this file must hold no package literal (row 785's guard).
    const val ACTION_RESUME = "aura.launcher.RESUME_QUEUE"
    const val ACTION_SHUFFLE_LIKED = "aura.launcher.SHUFFLE_LIKED"
    const val ACTION_DOWNLOADED = "aura.launcher.OPEN_DOWNLOADED"
    val ACTIONS = setOf(ACTION_RESUME, ACTION_SHUFFLE_LIKED, ACTION_DOWNLOADED)

    /** Same bound as MusicService.RESTORE_STATE_WAIT_MS: how long a cold start may take to bring the saved queue back. */
    private const val RESUME_WAIT_MS = 15_000L

    /** The «Me gusta» auto-playlist's Enhanced Shuffle context (AutoPlaylistScreen: "AP:" + "liked"; the widget uses it too). */
    private const val LIKED_CONTEXT_ID = "AP:liked"

    fun publish(context: Context, searchAction: String, libraryAction: String) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val all = listOf(
            // New ids: a pinned copy of a removed manifest shortcut stays immutable, and reusing its id would make the call throw.
            shortcut(context, "search_v2", searchAction, R.string.search, R.drawable.shortcut_search),
            shortcut(context, "library_v2", libraryAction, R.string.filter_library, R.drawable.shortcut_library),
            shortcut(context, "resume_queue", ACTION_RESUME, R.string.shortcut_resume, R.drawable.shortcut_resume),
            shortcut(context, "shuffle_liked", ACTION_SHUFFLE_LIKED, R.string.shortcut_shuffle_liked, R.drawable.shortcut_shuffle),
            shortcut(context, "downloaded", ACTION_DOWNLOADED, R.string.shortcut_downloaded, R.drawable.shortcut_downloaded),
        )
        // setDynamicShortcuts throws on more than the launcher allows, which would drop ALL of them (row 785's two included).
        val allowed = runCatching { manager.maxShortcutCountPerActivity }.getOrDefault(all.size)
        val shortcuts = all.take(allowed)
        runCatching { manager.setDynamicShortcuts(shortcuts) }
            .onFailure { Timber.tag(TAG).w(it, "Launcher shortcuts not published") }
    }

    /**
     * «Seguir escuchando» / «Aleatorio de Me gusta». Called once the activity holds its [PlayerConnection]. Bounded: a
     * shortcut that finds nothing to play gives up quietly instead of waiting forever.
     */
    suspend fun play(action: String, connection: PlayerConnection, database: MusicDatabase, context: Context) {
        when (action) {
            ACTION_RESUME -> {
                // Cold start: MusicService restores the saved queue asynchronously, so wait for its song to land
                // (a StateFlow emission, not a poll) before pressing play; PlayerConnection.play prepares an idle player.
                // Then the player-STATE restore, which seeks to the saved song and position after the queue lands.
                val restored = withTimeoutOrNull(RESUME_WAIT_MS) {
                    connection.mediaMetadata.first { it != null }
                    connection.service.playerStateRestoreDone.first { it }
                }
                if (restored == null) {
                    Timber.tag(TAG).w("Resume shortcut: no saved queue to resume")
                    return
                }
                connection.play()
            }
            ACTION_SHUFFLE_LIKED -> {
                // Same queue as the «Me gusta» screen's shuffle button: unplayed songs first while Aleatorio mejorado
                // remembers what was heard, and shuffle MODE on so that memory keeps driving the order.
                val songs = withContext(Dispatchers.IO) { database.likedSongsByCreateDateAsc().first() }
                if (songs.isEmpty()) {
                    Timber.tag(TAG).w("Shuffle-liked shortcut: no liked songs")
                    return
                }
                val played = withContext(Dispatchers.IO) {
                    if (context.dataStore.get(EnhancedShuffleKey, true)) {
                        database.playedSongIdsForContextFlow(LIKED_CONTEXT_ID).first().toHashSet()
                    } else {
                        emptySet()
                    }
                }
                val (unheard, heard) = songs.partition { it.id !in played }
                connection.playQueue(
                    ListQueue(
                        title = context.getString(R.string.liked),
                        items = (unheard.shuffled() + heard.shuffled()).map { it.toMediaItem() },
                        contextId = LIKED_CONTEXT_ID,
                        startShuffled = true,
                    ),
                )
            }
        }
    }

    private fun shortcut(context: Context, id: String, action: String, label: Int, icon: Int): ShortcutInfo =
        ShortcutInfo.Builder(context, id)
            .setShortLabel(context.getString(label))
            .setIcon(Icon.createWithResource(context, icon))
            .setIntent(Intent(action).setClassName(context, MainActivity::class.java.name))
            .build()
}
