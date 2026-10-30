package iad1tya.echo.music.devicesync

/** `m:ss` (or `h:mm:ss`) for a position in milliseconds. */
fun clock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
