package iad1tya.echo.music.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.Player
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.models.MediaMetadata as AppMediaMetadata
import iad1tya.echo.music.ui.utils.resize
import iad1tya.echo.music.utils.YTPlayerUtils
import iad1tya.echo.music.utils.isLocalMediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import android.os.Handler
import android.os.Looper

/**
 * Manages Google Cast connections and media playback on Cast devices.
 * This class handles the entire Cast lifecycle including:
 * - Device discovery
 * - Session management
 * - Media loading and playback control
 * - Synchronization between local and remote playback
 *
 * THREAD CONTRACT (crash fix, registry row 195): every Cast/GMS callback below can fire on a
 * Google-Play-Services executor thread — the SAME thread family the R8 stack of the 2026-08-29
 * crash showed (ExecutorsRegistrar lambda → ExoPlayerImpl.updatePlaybackInfo → IllegalStateException,
 * deobfuscated with the gms/release mapping). ExoPlayer demands ALL player calls on its own
 * application thread; touching player.* from the GMS callback thread is the crash. Every player
 * touch in this class now goes through [onPlayerThread] (or a main-dispatcher coroutine), so no
 * GMS callback can ever reach the player on the wrong thread again.
 */
class CastConnectionHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val musicService: MusicService
) {
    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var mediaRouter: MediaRouter? = null
    private var routeSelector: MediaRouteSelector? = null
    private var remoteMediaClient: RemoteMediaClient? = null
    private var castSession: CastSession? = null

    /** The player's application thread — created once; every player touch hops through it. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Runs [block] on the player's application thread. Safe from any GMS callback thread. */
    private inline fun onPlayerThread(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }
    
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()
    
    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()
    
    private val _castDeviceName = MutableStateFlow<String?>(null)
    val castDeviceName: StateFlow<String?> = _castDeviceName.asStateFlow()
    
    private val _castPosition = MutableStateFlow(0L)
    val castPosition: StateFlow<Long> = _castPosition.asStateFlow()
    
    private val _castDuration = MutableStateFlow(0L)
    val castDuration: StateFlow<Long> = _castDuration.asStateFlow()
    
    private val _castIsPlaying = MutableStateFlow(false)
    val castIsPlaying: StateFlow<Boolean> = _castIsPlaying.asStateFlow()
    
    private val _castIsBuffering = MutableStateFlow(false)
    val castIsBuffering: StateFlow<Boolean> = _castIsBuffering.asStateFlow()
    
    private val _castVolume = MutableStateFlow(1.0f)
    val castVolume: StateFlow<Float> = _castVolume.asStateFlow()
    
    private var positionUpdateJob: Job? = null
    private var currentMediaId: String? = null
    private var lastCastItemId: Int = -1
    private var isReloadingQueue: Boolean = false

    // What the TV was last told to play for the CURRENT track: its id and whether it is the VIDEO
    // rendition. Mode changes compare against THIS (our own bookkeeping), not against MediaStatus,
    // which lags the load by a second or more and would make a quick toggle look like a no-op.
    private var loadedCurrentMediaId: String? = null
    private var loadedCurrentAsVideo: Boolean = false

    // The user flipped Song/Video while a queue load was still settling: replay the decision once it is.
    private var reconcilePending: Boolean = false
    private var videoModeJob: Job? = null
    
    // Flag to prevent reverse sync when Cast triggers local player update
    var isSyncingFromCast: Boolean = false
        private set
    
    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            remoteMediaClient?.let { client ->
                val mediaStatus = client.mediaStatus
                val playerState = mediaStatus?.playerState
                // Show as "playing" when playing OR buffering/loading (so pause icon shows during buffering)
                _castIsPlaying.value = playerState == MediaStatus.PLAYER_STATE_PLAYING ||
                                       playerState == MediaStatus.PLAYER_STATE_BUFFERING ||
                                       playerState == MediaStatus.PLAYER_STATE_LOADING
                _castIsBuffering.value = playerState == MediaStatus.PLAYER_STATE_BUFFERING || 
                                         playerState == MediaStatus.PLAYER_STATE_LOADING
                _castDuration.value = client.streamDuration
                
                // Check if the current Cast item changed (user skipped on Cast widget)
                val currentItemId = mediaStatus?.currentItemId ?: -1
                if (currentItemId != -1 && currentItemId != lastCastItemId && lastCastItemId != -1 && !isReloadingQueue && mediaStatus != null) {
                    Timber.d("Cast item changed: $lastCastItemId -> $currentItemId")
                    handleCastItemChanged(mediaStatus)
                }
                lastCastItemId = currentItemId
                
                Timber.d("Cast status updated: playing=${_castIsPlaying.value}, buffering=${_castIsBuffering.value}, itemId=$currentItemId")
            }
        }
        
        override fun onMediaError(error: com.google.android.gms.cast.MediaError) {
            Timber.e("Cast media error: ${error.reason}")
        }
        
        override fun onQueueStatusUpdated() {
            Timber.d("Cast queue status updated")
        }
    }
    
    // Job for resetting sync flag
    private var syncResetJob: Job? = null
    
    /**
     * Handle when Cast changes to a different item (user pressed next/prev on Cast widget)
     * This syncs the local player - we don't reload the queue since the item is already loaded
     */
    private fun handleCastItemChanged(mediaStatus: MediaStatus) {
        val queueItems = mediaStatus.queueItems
        if (queueItems.isEmpty()) return
        val currentItemId = mediaStatus.currentItemId
        val currentIndex = queueItems.indexOfFirst { it.itemId == currentItemId }

        if (currentIndex < 0) return

        // Get the mediaId from the current Cast item's custom data
        val currentQueueItem = queueItems[currentIndex]
        val customData = currentQueueItem.media?.customData
        val castMediaId = customData?.optString("mediaId")

        Timber.d("Cast switched to item: index=$currentIndex, mediaId=$castMediaId, queueSize=${queueItems.size}")

        if (castMediaId != null && castMediaId != currentMediaId) {
            currentMediaId = castMediaId
            loadedCurrentMediaId = castMediaId
            loadedCurrentAsVideo = currentQueueItem.media?.customData?.optBoolean("video", false) == true

            // Cancel any pending sync reset
            syncResetJob?.cancel()

            // Set flag immediately to prevent reverse sync
            isSyncingFromCast = true

            // THREAD CONTRACT (registry row 195): this runs from a RemoteMediaClient callback,
            // which GMS fires on its own executor thread. EVERY player touch must hop to the
            // player's application thread — reading the queue, pausing and seeking included.
            onPlayerThread {
                // Find this song in the local player queue and switch to it
                val player = musicService.player
                val playerItemCount = player.mediaItemCount

                // Find the matching item in local player
                for (i in 0 until playerItemCount) {
                    val mediaItem = player.getMediaItemAt(i)
                    if (mediaItem.mediaId == castMediaId) {
                        Timber.d("Syncing local player to index $i (mediaId=$castMediaId)")

                        // Ensure local player is paused before seeking
                        player.pause()

                        // Move local player to match Cast (just for metadata sync)
                        player.seekTo(i, 0)

                        // Make absolutely sure local player stays paused
                        player.pause()

                        // Extend queue if needed (in background)
                        val itemsAhead = queueItems.size - 1 - currentIndex
                        val itemsBehind = currentIndex

                        if (itemsAhead < 2 || itemsBehind < 2) {
                            scope.launch {
                                val metadata = mediaItem.metadata
                                if (metadata != null) {
                                    extendQueueIfNeeded(i, playerItemCount, queueItems)
                                }
                            }
                        }
                        break
                    }
                }
            }

            // Reset flag after a short delay
            syncResetJob = scope.launch {
                delay(300)
                isSyncingFromCast = false
            }
        }
    }
    
    /**
     * Extend the Cast queue by adding more items at the edges if needed
     * This avoids a full queue reload which causes the widget to refresh
     */
    private suspend fun extendQueueIfNeeded(localPlayerIndex: Int, playerItemCount: Int, currentCastQueue: List<MediaQueueItem>) {
        if (isReloadingQueue) return
        
        val client = remoteMediaClient ?: return
        val currentCastIndex = currentCastQueue.indexOfFirst { 
            it.media?.customData?.optString("mediaId") == currentMediaId 
        }
        if (currentCastIndex < 0) return
        
        isReloadingQueue = true
        
        try {
            // Add more items to the end of queue if needed
            val itemsAhead = currentCastQueue.size - 1 - currentCastIndex
            if (itemsAhead < 2) {
                // Find what songs we need to add
                val lastCastItem = currentCastQueue.lastOrNull()
                val lastMediaId = lastCastItem?.media?.customData?.optString("mediaId")
                
                // Find the index of the last Cast item in local player
                var lastLocalIndex = -1
                for (i in 0 until playerItemCount) {
                    if (musicService.player.getMediaItemAt(i).mediaId == lastMediaId) {
                        lastLocalIndex = i
                        break
                    }
                }
                
                // Add next items from local player
                if (lastLocalIndex >= 0 && lastLocalIndex < playerItemCount - 1) {
                    val itemsToAdd = mutableListOf<MediaQueueItem>()
                    val addCount = minOf(2, playerItemCount - lastLocalIndex - 1)
                    
                    for (i in 1..addCount) {
                        val nextItem = musicService.player.getMediaItemAt(lastLocalIndex + i)
                        nextItem.metadata?.let { metadata ->
                            buildMediaInfo(metadata)?.let { mediaInfo ->
                                itemsToAdd.add(MediaQueueItem.Builder(mediaInfo).build())
                            }
                        }
                    }
                    
                    if (itemsToAdd.isNotEmpty()) {
                        Timber.d("Appending ${itemsToAdd.size} items to Cast queue")
                        withContext(Dispatchers.Main) {
                            client.queueAppendItem(itemsToAdd.first(), null)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extend Cast queue")
        } finally {
            delay(500)
            isReloadingQueue = false
        }
    }
    
    /**
     * Reload the Cast queue centered on the current item
     * This updates prev/next context after a skip
     * Respects shuffle mode when determining prev/next items
     */
    private fun reloadQueueForCurrentItem(metadata: AppMediaMetadata) {
        if (!_isCasting.value || isReloadingQueue) return
        
        isReloadingQueue = true
        scope.launch {
            try {
                val player = musicService.player
                val currentIndex = player.currentMediaItemIndex
                val shuffleEnabled = player.shuffleModeEnabled
                val timeline = player.currentTimeline
                
                // Build new queue items: up to 2 previous, current, and up to 2 next
                val queueItems = mutableListOf<MediaQueueItem>()
                
                // Get previous items respecting shuffle order
                val prevItems = mutableListOf<androidx.media3.common.MediaItem>()
                if (!timeline.isEmpty) {
                    var prevIdx = currentIndex
                    for (i in 0 until 2) {
                        prevIdx = timeline.getPreviousWindowIndex(prevIdx, Player.REPEAT_MODE_OFF, shuffleEnabled)
                        if (prevIdx == androidx.media3.common.C.INDEX_UNSET) break
                        prevItems.add(0, player.getMediaItemAt(prevIdx))
                    }
                }
                
                // Add previous items
                for (prevItem in prevItems) {
                    prevItem.metadata?.let { prevMetadata ->
                        buildMediaInfo(prevMetadata)?.let { mediaInfo ->
                            queueItems.add(MediaQueueItem.Builder(mediaInfo).build())
                        }
                    }
                }
                val startIndex = queueItems.size // Current item index after previous items
                
                // Add current item
                val currentMediaInfo = buildMediaInfo(metadata)
                if (currentMediaInfo != null) {
                    queueItems.add(MediaQueueItem.Builder(currentMediaInfo).build())
                }
                
                // Get next items respecting shuffle order
                if (!timeline.isEmpty) {
                    var nextIdx = currentIndex
                    for (i in 0 until 2) {
                        nextIdx = timeline.getNextWindowIndex(nextIdx, Player.REPEAT_MODE_OFF, shuffleEnabled)
                        if (nextIdx == androidx.media3.common.C.INDEX_UNSET) break
                        val nextItem = player.getMediaItemAt(nextIdx)
                        nextItem.metadata?.let { nextMetadata ->
                            buildMediaInfo(nextMetadata)?.let { mediaInfo ->
                                queueItems.add(MediaQueueItem.Builder(mediaInfo).build())
                            }
                        }
                    }
                }
                
                if (queueItems.isNotEmpty()) {
                    Timber.d("Reloading Cast queue: ${queueItems.size} items, startIndex=$startIndex, shuffle=$shuffleEnabled")
                    
                    withContext(Dispatchers.Main) {
                        remoteMediaClient?.queueLoad(
                            queueItems.toTypedArray(),
                            startIndex,
                            MediaStatus.REPEAT_MODE_REPEAT_OFF,
                            0L, // Start from beginning since Cast already has position
                            org.json.JSONObject()
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to reload Cast queue")
            } finally {
                // Delay before allowing another reload to prevent rapid reloads
                delay(1000)
                isReloadingQueue = false
            }
        }
    }
    
    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            Timber.d("Cast session starting")
            _isConnecting.value = true
        }
        
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            Timber.d("Cast session started: $sessionId")
            _isCasting.value = true
            _isConnecting.value = false
            _castDeviceName.value = session.castDevice?.friendlyName
            castSession = session
            remoteMediaClient = session.remoteMediaClient
            remoteMediaClient?.registerCallback(remoteMediaClientCallback)
            
            // Get initial volume (and keep it in sync with changes made on the device itself). The raw
            // read was also unclamped and unguarded — session.volume throws if the session is not fully
            // connected, which would have taken down the whole started-callback.
            adoptSessionVolume(session)

            // Start position updates
            startPositionUpdates()
            
            // Load current media
            loadCurrentMedia()
        }
        
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            Timber.e("Cast session start failed: $error")
            _isCasting.value = false
            _isConnecting.value = false
            // The listener may already be attached (a session can fail AFTER we adopted it). It holds this
            // handler, which holds the service, while the CastSession is owned process-wide — so leaving
            // it attached on a failure path outlives the session it belongs to.
            runCatching { session.removeCastListener(castVolumeListener) }
        }
        
        override fun onSessionEnding(session: CastSession) {
            Timber.d("Cast session ending")
            // Capture Cast position before session ends
            val castPosition = remoteMediaClient?.approximateStreamPosition ?: _castPosition.value
            if (castPosition > 0) {
                // Seek local player to Cast position so playback can continue from there.
                // THREAD CONTRACT (registry row 195): SessionManagerListener callbacks fire on a
                // GMS executor thread — hop to the player's application thread before touching it.
                onPlayerThread { musicService.player.seekTo(castPosition) }
                Timber.d("Saved Cast position: $castPosition")
            }
        }
        
        override fun onSessionEnded(session: CastSession, error: Int) {
            Timber.d("Cast session ended: error=$error")
            _isCasting.value = false
            _isConnecting.value = false
            _castDeviceName.value = null
            // Detach the volume listener before dropping the session, or it outlives it.
            runCatching { session.removeCastListener(castVolumeListener) }
            castSession = null

            remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
            remoteMediaClient = null
            
            stopPositionUpdates()
            
            // Pause local playback when disconnecting from Cast.
            // THREAD CONTRACT (registry row 195): this callback fires on a GMS executor thread —
            // hop to the player's application thread.
            onPlayerThread {
                musicService.player.pause()
                // The TV was showing the video; hand it back to the phone (paused, same position).
                musicService.videoCoordinator.reapplyVideoAfterCast()
            }
            loadedCurrentMediaId = null
            loadedCurrentAsVideo = false
        }
        
        override fun onSessionResuming(session: CastSession, sessionId: String) {
            _isConnecting.value = true
        }
        
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            _isCasting.value = true
            _isConnecting.value = false
            _castDeviceName.value = session.castDevice?.friendlyName

            // WITHOUT this, setVolume() writes to a null session and silently does nothing: the slider
            // moves, the speaker does not. onSessionStarted was the ONLY assignment, so every session
            // that was RESUMED (app returned to foreground, process restarted while casting) had volume
            // control dead with no error anywhere.
            castSession = session
            adoptSessionVolume(session)

            remoteMediaClient = session.remoteMediaClient
            remoteMediaClient?.registerCallback(remoteMediaClientCallback)

            startPositionUpdates()
        }
        
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            _isConnecting.value = false
            // Same reasoning as onSessionStartFailed: never leave the volume listener on a dead session.
            runCatching { session.removeCastListener(castVolumeListener) }
        }
        
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }
    
    fun initialize(): Boolean {
        return try {
            castContext = CastContext.getSharedInstance(context)
            sessionManager = castContext?.sessionManager
            mediaRouter = MediaRouter.getInstance(context)
            routeSelector = MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                .build()
            
            sessionManager?.addSessionManagerListener(sessionManagerListener, CastSession::class.java)

            // Song <-> Video follows the TV: the same switch that changes what the phone shows now
            // changes what the TV plays (video file vs. audio only), from the position it is at.
            videoModeJob?.cancel()
            videoModeJob = scope.launch {
                musicService.videoMode.drop(1).distinctUntilChanged().collect { reconcileVideoMode() }
            }
            
            // Check if already connected
            sessionManager?.currentCastSession?.let { session ->
                _isCasting.value = true
                _castDeviceName.value = session.castDevice?.friendlyName
                // Same omission as onSessionResumed: without this, adopting an already-running session
                // leaves volume control writing to null.
                castSession = session
                adoptSessionVolume(session)
                remoteMediaClient = session.remoteMediaClient
                remoteMediaClient?.registerCallback(remoteMediaClientCallback)
                startPositionUpdates()
            }
            
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Cast")
            false
        }
    }
    
    fun getAvailableRoutes(): List<MediaRouter.RouteInfo> {
        val router = mediaRouter ?: return emptyList()
        val selector = routeSelector ?: return emptyList()
        
        return router.routes.filter { route ->
            route.matchesSelector(selector) && !route.isDefault
        }
    }
    
    /**
     * Selects [route] so the Cast SDK's SessionManager can start a session on it.
     *
     * Aura drives its OWN picker sheet (see CastButton/CastPickerSheet) instead of the system
     * MediaRouteChooserDialog, so this manual selectRoute() is what actually connects — it cannot
     * simply be dropped. It is, however, called with a route SNAPSHOT the sheet captured earlier, and
     * that route can be gone by the time the user taps it (device sleeps, Wi-Fi blips, discovery
     * refreshes). Some OEM MediaRouter implementations — Xiaomi's notably — throw from selectRoute()
     * on a removed route ("Ignoring attempt to select removed route") instead of ignoring it, which
     * crashed the app straight out of the device picker.
     *
     * So: re-resolve the route by id against the LIVE router right before selecting (the stale object
     * is never handed back to the framework), and treat any OEM throw as a failed connection attempt
     * rather than a crash. The user simply sees the picker not connect, and can retry.
     */
    fun connectToRoute(route: MediaRouter.RouteInfo) {
        // Ensure we're initialized before trying to connect
        if (mediaRouter == null) {
            initialize()
        }
        val router = mediaRouter
        if (router == null) {
            Timber.w("connectToRoute: MediaRouter unavailable")
            _isConnecting.value = false
            return
        }
        // Re-query: the snapshot the picker handed us may already be removed.
        val live = runCatching { router.routes.firstOrNull { it.id == route.id } }.getOrNull()
        if (live == null) {
            Timber.w("connectToRoute: route ${route.id} is no longer available; ignoring")
            _isConnecting.value = false
            return
        }
        _isConnecting.value = true
        runCatching { router.selectRoute(live) }
            .onFailure {
                // OEM MediaRouter threw on a route that vanished between the re-query and the select.
                Timber.w(it, "selectRoute failed for ${route.id}")
                _isConnecting.value = false
            }
    }
    
    fun disconnect() {
        sessionManager?.endCurrentSession(true)
    }
    
    fun loadCurrentMedia() {
        val metadata = musicService.currentMediaMetadata.value ?: return
        loadMediaWithQueue(metadata)
    }
    
    fun loadMedia(metadata: AppMediaMetadata) {
        loadMediaWithQueue(metadata)
    }
    
    /**
     * Whether the TV should get the VIDEO rendition of [mediaId]: only the track that is current on the
     * phone AND only while the Song/Video switch is on Video. Neighbours in the Cast queue (prev/next)
     * stay audio-only, exactly like the phone ("music first": video is never carried to the next track).
     * Reads only StateFlows, so it is safe from any thread.
     */
    private fun wantsVideo(mediaId: String): Boolean =
        MusicService.VIDEO_PROVIDERS_ENABLED &&
            musicService.videoMode.value &&
            musicService.currentMediaMetadata.value?.id == mediaId

    /**
     * The video rendition the Cast Default Media Receiver can actually play: a MUXED progressive MP4
     * (itag 22 = 720p, 18 = 360p; picture and sound in one file). The phone's own video mode prefers
     * video-only adaptive streams and merges a separate audio track on the device — the receiver cannot
     * do that merge, so asking it for those would give a silent or blank TV. null = none available.
     */
    private suspend fun resolveCastVideoUrl(mediaId: String): String? =
        withContext(Dispatchers.IO) {
            YTPlayerUtils.adaptiveVideoStreamNewPipe(
                mediaId,
                musicService.connectivityManager,
                maxHeight = 720,
                forceMuxed = true,
            ).getOrNull()?.url?.takeIf { it.isNotEmpty() }
        }

    /**
     * Build MediaInfo for a single track. With [asVideo] the TV is sent the video file (and told it is a
     * movie so it shows the picture, not an album-art card); if no playable video exists the track
     * falls back to audio and [onVideoUnavailable] is told, so the caller can put the switch back.
     */
    private suspend fun buildMediaInfo(
        metadata: AppMediaMetadata,
        asVideo: Boolean = false,
        onVideoUnavailable: () -> Unit = {},
    ): MediaInfo? {
        if (asVideo) {
            // Local files, podcasts and direct URLs have no cloud video the TV could fetch.
            val videoUrl = if (metadata.id.isLocalMediaId() || metadata.id.startsWith("http", ignoreCase = true)) {
                null
            } else {
                runCatching { resolveCastVideoUrl(metadata.id) }.getOrNull()
            }
            if (videoUrl != null) {
                val movie = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                    putString(MediaMetadata.KEY_TITLE, metadata.title)
                    putString(MediaMetadata.KEY_SUBTITLE, metadata.artists.joinToString(", ") { it.name })
                    metadata.thumbnailUrl?.let { addImage(WebImage(Uri.parse(it.resize(1080, 1080)))) }
                }
                return MediaInfo.Builder(videoUrl)
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType("video/mp4")
                    .setMetadata(movie)
                    .setCustomData(org.json.JSONObject().put("mediaId", metadata.id).put("video", true))
                    .build()
            }
            Timber.w("Cast: no playable video rendition, sending audio only")
            onVideoUnavailable()
        }

        val streamUrl = musicService.getStreamUrl(metadata.id) ?: return null
        
        val castMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, metadata.title)
            putString(MediaMetadata.KEY_ARTIST, metadata.artists.joinToString(", ") { it.name })
            metadata.album?.title?.let { putString(MediaMetadata.KEY_ALBUM_TITLE, it) }
            metadata.thumbnailUrl?.let { thumbUrl ->
                // Use high quality thumbnail (1080x1080) for Cast display
                val highQualityUrl = thumbUrl.resize(1080, 1080)
                addImage(WebImage(Uri.parse(highQualityUrl)))
            }
        }
        
        return MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("audio/mp4")
            .setMetadata(castMetadata)
            .setCustomData(org.json.JSONObject().put("mediaId", metadata.id).put("video", false))
            .build()
    }

    /**
     * The Song/Video switch moved (or the track under it did). If what the TV is playing no longer
     * matches what the switch asks for, reload ONLY the current track in the other rendition from where
     * the TV is, so the song carries on instead of restarting.
     */
    private fun reconcileVideoMode() {
        if (!_isCasting.value) return
        val current = musicService.currentMediaMetadata.value ?: return
        if (isReloadingQueue) {
            reconcilePending = true
            return
        }
        if (current.id == loadedCurrentMediaId && wantsVideo(current.id) == loadedCurrentAsVideo) return
        val position = remoteMediaClient?.approximateStreamPosition?.takeIf { it > 0 }
        loadMediaWithQueue(current, startPositionOverride = position)
    }
    
    /**
     * Load media with queue context to enable skip prev/next buttons on Cast widget
     * Loads up to 5 items: 2 previous, current, and 2 next for smoother transitions
     * Respects shuffle mode when determining prev/next items
     */
    private fun loadMediaWithQueue(metadata: AppMediaMetadata, startPositionOverride: Long? = null) {
        if (!_isCasting.value) return
        
        isReloadingQueue = true // Prevent sync logic from triggering during load
        scope.launch {
            try {
                currentMediaId = metadata.id
                _castIsBuffering.value = true
                lastCastItemId = -1 // Reset to prevent false change detection
                
                val player = musicService.player
                val currentIndex = player.currentMediaItemIndex
                val mediaItemCount = player.mediaItemCount
                val shuffleEnabled = player.shuffleModeEnabled
                val timeline = player.currentTimeline
                
                // Build queue items: up to 2 previous, current, and up to 2 next songs
                val queueItems = mutableListOf<MediaQueueItem>()
                
                // Get previous items respecting shuffle order
                val prevItems = mutableListOf<androidx.media3.common.MediaItem>()
                if (!timeline.isEmpty) {
                    var prevIdx = currentIndex
                    for (i in 0 until 2) {
                        prevIdx = timeline.getPreviousWindowIndex(prevIdx, Player.REPEAT_MODE_OFF, shuffleEnabled)
                        if (prevIdx == androidx.media3.common.C.INDEX_UNSET) break
                        prevItems.add(0, player.getMediaItemAt(prevIdx)) // Add at beginning to maintain order
                    }
                }
                
                // Add previous items
                for (prevItem in prevItems) {
                    prevItem.metadata?.let { prevMetadata ->
                        buildMediaInfo(prevMetadata)?.let { mediaInfo ->
                            queueItems.add(MediaQueueItem.Builder(mediaInfo).build())
                        }
                    }
                }
                val startIndex = queueItems.size // Current item index after previous items
                
                // Add current item
                val wantVideo = wantsVideo(metadata.id)
                var videoFellBack = false
                val currentMediaInfo = buildMediaInfo(metadata, asVideo = wantVideo) { videoFellBack = true }
                if (currentMediaInfo == null) {
                    Timber.e("Failed to get stream URL for Cast")
                    _castIsBuffering.value = false
                    return@launch
                }
                queueItems.add(MediaQueueItem.Builder(currentMediaInfo).build())
                loadedCurrentMediaId = metadata.id
                loadedCurrentAsVideo = wantVideo && !videoFellBack
                
                // Get next items respecting shuffle order
                if (!timeline.isEmpty) {
                    var nextIdx = currentIndex
                    for (i in 0 until 2) {
                        nextIdx = timeline.getNextWindowIndex(nextIdx, Player.REPEAT_MODE_OFF, shuffleEnabled)
                        if (nextIdx == androidx.media3.common.C.INDEX_UNSET) break
                        val nextItem = player.getMediaItemAt(nextIdx)
                        nextItem.metadata?.let { nextMetadata ->
                            buildMediaInfo(nextMetadata)?.let { mediaInfo ->
                                queueItems.add(MediaQueueItem.Builder(mediaInfo).build())
                            }
                        }
                    }
                }
                
                // Get current position from local player if same song
                val startPosition = startPositionOverride ?: if (player.currentMediaItem?.mediaId == metadata.id) {
                    player.currentPosition
                } else {
                    0L
                }
                
                Timber.d("Loading Cast queue: ${queueItems.size} items, startIndex=$startIndex, shuffle=$shuffleEnabled")
                
                withContext(Dispatchers.Main) {
                    val client = remoteMediaClient ?: return@withContext
                    
                    // Load the queue
                    client.queueLoad(
                        queueItems.toTypedArray(),
                        startIndex,
                        MediaStatus.REPEAT_MODE_REPEAT_OFF,
                        startPosition,
                        org.json.JSONObject()
                    )
                    
                    // Pause local playback
                    musicService.player.pause()

                    if (videoFellBack) {
                        // The TV got audio only: say so, and put the switch back on Song so the app
                        // never claims a video that is not on the screen.
                        android.widget.Toast.makeText(
                            context,
                            context.getString(iad1tya.echo.music.R.string.cast_video_unavailable),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                        musicService.exitVideoMode()
                    } else if (wantVideo) {
                        // The picture lives on the TV now: free the phone's own video decoder/stream.
                        musicService.videoCoordinator.releaseLocalVideoForCast()
                    }
                }
                
                Timber.d("Loaded media on Cast: ${metadata.title}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load media on Cast")
                _castIsBuffering.value = false
            } finally {
                // Allow sync logic after a delay
                delay(1500)
                isReloadingQueue = false
                if (reconcilePending) {
                    reconcilePending = false
                    reconcileVideoMode()
                }
            }
        }
    }
    
    fun play() {
        remoteMediaClient?.play()
    }
    
    fun pause() {
        remoteMediaClient?.pause()
    }
    
    fun seekTo(position: Long) {
        val seekOptions = MediaSeekOptions.Builder()
            .setPosition(position)
            .build()
        remoteMediaClient?.seek(seekOptions)
    }
    
    /**
     * Set the Cast device volume (0.0 to 1.0)
     */
    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        val session = castSession
        if (session == null) {
            // Do NOT move the mirror in this case: publishing a volume we could not apply is what made
            // the failure invisible — the UI slider tracked a value the speaker never received.
            Timber.w("Cast setVolume($clampedVolume) ignored: no active session")
            return
        }
        try {
            session.volume = clampedVolume.toDouble()
            _castVolume.value = clampedVolume
            Timber.d("Set Cast volume to $clampedVolume")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set Cast volume")
        }
    }

    /**
     * Mirrors volume changes made ON THE DEVICE ITSELF (the TV remote, the speaker's buttons, another
     * phone casting to it). Without this the in-app slider only ever moved when the app moved it, so it
     * drifted out of sync with reality and the next drag jumped the volume.
     */
    private val castVolumeListener = object : com.google.android.gms.cast.Cast.Listener() {
        override fun onVolumeChanged() {
            castSession?.let { adoptSessionVolume(it) }
        }
    }

    /**
     * Seeds the volume mirror from the LIVE session, so the in-app slider starts where the device
     * actually is instead of at whatever value the mirror happened to hold, and keeps it in sync from
     * then on. Best-effort: reading volume throws if the session is not fully connected yet.
     */
    private fun adoptSessionVolume(session: CastSession) {
        runCatching { _castVolume.value = session.volume.toFloat().coerceIn(0f, 1f) }
            .onFailure { Timber.w(it, "Could not read Cast session volume") }
        // addCastListener is idempotent per instance in the GMS SDK, but guard anyway so repeated
        // resume callbacks cannot stack duplicate listeners on one session.
        runCatching {
            session.removeCastListener(castVolumeListener)
            session.addCastListener(castVolumeListener)
        }.onFailure { Timber.w(it, "Could not attach Cast volume listener") }
    }
    
    /**
     * Try to navigate to a media item if it's already in the Cast queue
     * Returns true if successful, false if the item isn't in the queue
     */
    fun navigateToMediaIfInQueue(mediaId: String): Boolean {
        val client = remoteMediaClient ?: return false
        val mediaStatus = client.mediaStatus ?: return false
        val queueItems = mediaStatus.queueItems
        if (queueItems.isEmpty()) return false
        
        // Find the item in Cast queue
        val targetIndex = queueItems.indexOfFirst { 
            it.media?.customData?.optString("mediaId") == mediaId 
        }
        
        if (targetIndex < 0) {
            Timber.d("Media $mediaId not found in Cast queue")
            return false
        }
        
        // The Cast queue holds neighbours as audio. If the Song/Video switch wants the VIDEO of this
        // track (or the audio of a track that was loaded as video), jumping to the stored item would
        // show the wrong rendition: report "not in the queue" so the caller reloads it properly.
        val storedAsVideo = queueItems[targetIndex].media?.customData?.optBoolean("video", false) == true
        if (storedAsVideo != wantsVideo(mediaId)) {
            Timber.d("Media $mediaId is queued as ${if (storedAsVideo) "video" else "audio"}, needs the other rendition")
            return false
        }

        val currentItemId = mediaStatus.currentItemId
        val currentIndex = queueItems.indexOfFirst { it.itemId == currentItemId }
        
        loadedCurrentMediaId = mediaId
        loadedCurrentAsVideo = storedAsVideo

        if (targetIndex == currentIndex) {
            // Already on this item - ensure local player is paused
            currentMediaId = mediaId
            // THREAD CONTRACT (registry row 195): public entry point — hop to the player's
            // application thread; on Main (every current caller) this runs inline, unchanged.
            onPlayerThread { musicService.player.pause() }
            return true
        }
        
        // Navigate to the item on Cast
        val targetItem = queueItems[targetIndex]
        Timber.d("Navigating Cast to item at index $targetIndex (mediaId=$mediaId)")
        
        // Set flag to prevent reverse sync loop
        isSyncingFromCast = true
        
        // Update local player to match (for UI sync) - find the item in local queue.
        // THREAD CONTRACT (registry row 195): every player touch hops to the player's
        // application thread; the Boolean return only depends on the Cast status read above,
        // so it stays valid whether this runs inline (Main) or deferred.
        onPlayerThread {
            val player = musicService.player
            for (i in 0 until player.mediaItemCount) {
                if (player.getMediaItemAt(i).mediaId == mediaId) {
                    player.seekTo(i, 0)
                    break
                }
            }
            player.pause()
        }
        
        // Navigate Cast
        client.queueJumpToItem(targetItem.itemId, org.json.JSONObject())
        currentMediaId = mediaId
        
        // Reset sync flag after a short delay
        scope.launch {
            delay(300)
            isSyncingFromCast = false
        }
        
        return true
    }
    
    fun skipToNext() {
        // First try to use Cast queue
        val client = remoteMediaClient
        val mediaStatus = client?.mediaStatus
        if (mediaStatus != null && mediaStatus.queueItemCount > 0) {
            // Check if there's a next item in Cast queue
            val currentItemId = mediaStatus.currentItemId
            val queueItems = mediaStatus.queueItems
            val currentIndex = queueItems.indexOfFirst { it.itemId == currentItemId }
            if (currentIndex >= 0 && currentIndex < queueItems.size - 1) {
                // There's a next item in Cast queue, use it
                client.queueNext(org.json.JSONObject())
                // Ensure local player stays paused. THREAD CONTRACT (registry row 195): hop to
                // the player's application thread (inline on Main, deferred otherwise).
                onPlayerThread { musicService.player.pause() }
                return
            }
        }
        
        // Fall back to loading from MusicService queue.
        // THREAD CONTRACT (registry row 195): hop every player touch to the player's thread.
        onPlayerThread {
            val player = musicService.player
            if (player.hasNextMediaItem()) {
                // Pause first, then seek
                player.pause()
                player.seekToNextMediaItem()
                // The player listener will handle loading the new media to Cast
            }
        }
    }
    
    fun skipToPrevious() {
        // First try to use Cast queue
        val client = remoteMediaClient
        val mediaStatus = client?.mediaStatus
        if (mediaStatus != null && mediaStatus.queueItemCount > 0) {
            // Check if there's a previous item in Cast queue
            val currentItemId = mediaStatus.currentItemId
            val queueItems = mediaStatus.queueItems
            val currentIndex = queueItems.indexOfFirst { it.itemId == currentItemId }
            if (currentIndex > 0) {
                // There's a previous item in Cast queue, use it
                client.queuePrev(org.json.JSONObject())
                // Ensure local player stays paused. THREAD CONTRACT (registry row 195): hop to
                // the player's application thread (inline on Main, deferred otherwise).
                onPlayerThread { musicService.player.pause() }
                return
            }
        }
        
        // Fall back to loading from MusicService queue.
        // THREAD CONTRACT (registry row 195): hop every player touch to the player's thread.
        onPlayerThread {
            val player = musicService.player
            if (player.hasPreviousMediaItem()) {
                // Pause first, then seek
                player.pause()
                player.seekToPreviousMediaItem()
            }
        }
    }
    
    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive && _isCasting.value) {
                remoteMediaClient?.let { client ->
                    _castPosition.value = client.approximateStreamPosition
                }
                delay(500)
            }
        }
    }
    
    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }
    
    fun release() {
        videoModeJob?.cancel()
        videoModeJob = null
        stopPositionUpdates()
        remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
        sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
    }
}
