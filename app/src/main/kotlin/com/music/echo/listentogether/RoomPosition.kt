package iad1tya.echo.music.listentogether

import kotlin.math.max

/** When this guest received the room's timeline ([position] at [anchorServerTime], [playing]), on its own monotonic clock. */
internal data class RoomTimelineReceipt(
    val position: Long,
    val anchorServerTime: Long,
    val playing: Boolean,
    val receivedAtMs: Long,
)

/**
 * Row 796: where the room is NOW on its track, for a guest that re-applies the room's last known state (the 15 s heartbeat, a
 * realign after buffering, a track recheck).
 *
 * With the server clock calibrated and an anchor, this is [ServerClock.positionAt]. Without either — a socket that has not had its
 * first pong yet (the clock is reset on every new socket), or a command that carried no capture time — that falls back to the RAW
 * position, which is right for a command that just arrived but wrong for one re-applied seconds later: the heartbeat then took the
 * room to be where it was when the last command arrived and jumped the guest BACK by up to 15 s, every 15 s. So the fallback is the
 * time since this device received that same timeline, on its monotonic clock ([receipt]): off by one network leg at most, never by
 * the time since the last command. A receipt for another position or anchor, or for a paused room, is not used.
 */
internal fun roomPositionNow(
    position: Long,
    playing: Boolean,
    anchorServerTime: Long,
    serverNow: Long?,
    receipt: RoomTimelineReceipt?,
    nowMs: Long,
): Long {
    if (!playing) return position
    if (anchorServerTime > 0L && serverNow != null) return position + max(0L, serverNow - anchorServerTime)
    if (receipt != null && receipt.playing && receipt.position == position && receipt.anchorServerTime == anchorServerTime) {
        return position + max(0L, nowMs - receipt.receivedAtMs)
    }
    return position
}
