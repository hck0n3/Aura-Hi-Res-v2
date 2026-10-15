

package iad1tya.echo.music.ui.utils

/** Canonical list-surface cover size (grids, queue, notifications). Cache audit H2 (HALLAZGO-037). */
const val COVER_LIST_BUCKET = 544

/** Canonical player cover size. Cache audit H2 (HALLAZGO-037). */
const val COVER_PLAYER_BUCKET = 1200

/**
 * Cache audit H2 (HALLAZGO-037): every distinct size in a googleusercontent URL is a distinct
 * Coil disk-cache entry, and covers were requested in up to 6 sizes — one cover could be
 * downloaded 6 times. Bucket every request to one of two canonical sizes (544 list / 1200
 * player, the exact variants the DB and the queue already use), so each logical cover has at
 * most two cache keys. Pure decision — the URL rewrite stays in [resize].
 */
fun canonicalCoverSize(requested: Int, lean: Boolean = false): Int {
    // Lean mode (registry 395): smaller renditions. The sizes in the URL are what is DOWNLOADED — Coil downsamples
    // to the view afterwards, so a 48 dp row that asks for 256 px still pulled the 544 px file (and the player the
    // 1200 px one). On a slow connection and a slow disk that is the cost that shows in a scroll.
    val list = if (lean) COVER_LIST_BUCKET_LEAN else COVER_LIST_BUCKET
    val player = if (lean) COVER_PLAYER_BUCKET_LEAN else COVER_PLAYER_BUCKET
    return when {
        requested <= 0 -> requested
        requested <= (list + player) / 2 -> list
        else -> player
    }
}

/** Lean-mode list cover size (grids, queue, notifications). */
const val COVER_LIST_BUCKET_LEAN = 384

/** Lean-mode player / hero cover size. */
const val COVER_PLAYER_BUCKET_LEAN = 800

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this





    if (this.contains("i.ytimg.com")) {
        // Always use hqdefault for YouTube VIDEO thumbnails: maxresdefault and sddefault do NOT exist for many
        // videos (404 / gray placeholder), which left the player cover and background BLACK when playing a
        // video's audio. hqdefault.jpg exists for every video, so the thumbnail always shows.
        return this.replace(
            Regex("(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.(jpg|webp)"),
            "hqdefault.$2",
        )
    }


    if (this.contains("googleusercontent.com") && this.contains("=w")) {
        val baseUrl = this.split("=w")[0]
        val lean = iad1tya.echo.music.utils.LeanMode.isOn()
        val w = canonicalCoverSize(width ?: 0, lean)
        val h = canonicalCoverSize(height ?: width ?: 0, lean)

        return "$baseUrl=w$w-h$h-p-l90-rj"
    }


    if (this.contains("yt3.ggpht.com")) {
        // Artist/channel AVATARS. The old code did split("=")[0].split("-s")[0], and split("-s") chops
        // the opaque token at any "-s" inside it → a 404 → blank circle (bug: suggested AND local artist
        // photos didn't load). The token is not safely rewritable, so return the URL RAW — Coil samples
        // the native avatar down to the (small, circular) target bounds. Fixes every artist-image site.
        return this
    }


    "https://lh\\d\\.googleusercontent\\.com/.*".toRegex().matchEntire(this)?.let {
        val lean = iad1tya.echo.music.utils.LeanMode.isOn()
        val w = canonicalCoverSize(width ?: 0, lean)
        val h = canonicalCoverSize(height ?: width ?: 0, lean)
        return "${this.split("=")[0]}=w$w-h$h-p-l90-rj"
    }

    return this
}
