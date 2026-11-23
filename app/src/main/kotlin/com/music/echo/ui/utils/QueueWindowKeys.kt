package iad1tya.echo.music.ui.utils

/**
 * Collision-free LazyColumn keys for queue rows (registry row 767). `Timeline.Window.uid.hashCode()` can collide
 * between two different windows, and a duplicate key crashes the list. Equal uids (the same window across timeline
 * refreshes) share one key; different uids never do, because keys come from a counter that is never reused.
 */
object QueueWindowKeys {
    private val keys = HashMap<Any, Long>()
    private var next = 0L

    @Synchronized
    fun keyOf(uid: Any): Long = keys.getOrPut(uid) { next++ }

    /** Forgets windows that left the queue, so the map stays the size of the queue. */
    @Synchronized
    fun retainOnly(uids: Collection<Any>) {
        keys.keys.retainAll(uids.toHashSet())
    }
}
