package iad1tya.echo.music.smoketest;

import android.content.ComponentName;
import android.os.Looper;
import android.os.SystemClock;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads the app's playback through its media session, the same door the notification, Bluetooth and Android Auto use.
 * Read-only on purpose: it never sends a player command (a PLAY from a controller is subject to the app's cold-restore
 * veto in MediaLibrarySessionCallback and would test that veto, not playback).
 */
final class SessionProbe implements AutoCloseable {

    static final class Snapshot {
        int state;
        boolean playing;
        long positionMs;
        String mediaId;
        /** The player's error CODE only (never its message: it can carry a stream URL with `sig`/`pot`). */
        String error;

        @Override
        public String toString() {
            return "state=" + state + " playing=" + playing + " positionMs=" + positionMs + " mediaId=" + mediaId
                    + " error=" + error;
        }
    }

    interface Condition {
        boolean holds(Snapshot s);
    }

    private final AuraDevice aura;
    private final MediaController controller;

    private SessionProbe(AuraDevice aura, MediaController controller) {
        this.aura = aura;
        this.controller = controller;
    }

    static SessionProbe connect(AuraDevice aura) {
        // Binding to a service of a stopped app would start it without its UI: start it the user's way first.
        if (aura.pid().isEmpty()) aura.launchWarm();
        SessionToken token = new SessionToken(aura.context, new ComponentName(aura.target, AuraDevice.MUSIC_SERVICE));
        AtomicReference<Future<MediaController>> future = new AtomicReference<>();
        aura.instrumentation.runOnMainSync(() -> future.set(
                new MediaController.Builder(aura.context, token)
                        .setApplicationLooper(Looper.getMainLooper())
                        .buildAsync()));
        try {
            return new SessionProbe(aura, future.get().get(20, TimeUnit.SECONDS));
        } catch (Exception e) {
            MediaController.releaseFuture(future.get());
            throw new AssertionError("Could not connect a MediaController to " + AuraDevice.MUSIC_SERVICE, e);
        }
    }

    Snapshot read() {
        Snapshot s = new Snapshot();
        aura.instrumentation.runOnMainSync(() -> {
            s.state = controller.getPlaybackState();
            s.playing = controller.isPlaying();
            s.positionMs = controller.getCurrentPosition();
            MediaItem item = controller.getCurrentMediaItem();
            s.mediaId = item == null ? null : item.mediaId;
            PlaybackException error = controller.getPlayerError();
            s.error = error == null ? null : error.getErrorCodeName();
        });
        return s;
    }

    /** Polls until `condition` holds; fails with the last snapshot otherwise. */
    Snapshot waitUntil(Condition condition, long timeoutMs, String what) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        Snapshot last = read();
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition.holds(last)) return last;
            SystemClock.sleep(500);
            last = read();
        }
        if (condition.holds(last)) return last;
        aura.fail("timed out after " + timeoutMs + " ms waiting for " + what + " (last: " + last + ")");
        return last;
    }

    static boolean readyAndPlaying(Snapshot s) {
        return s.state == Player.STATE_READY && s.playing;
    }

    @Override
    public void close() {
        aura.instrumentation.runOnMainSync(controller::release);
    }
}
