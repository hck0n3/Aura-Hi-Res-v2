package iad1tya.echo.music.utils

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import timber.log.Timber

/**
 * Whether THIS device can decode VP9/AV1 in hardware — checked before the video fallback ladder is
 * ever allowed to offer those codecs (see [iad1tya.echo.music.playback.VideoFormatFallback.Source.PIPEPIPE_VP9_AV1]).
 *
 * H.264 stays the ladder's first four rungs unconditionally; VP9/AV1 only widens the LAST rung, for
 * the video that has no usable H.264 rendition at all. Gating it on hardware decode is deliberate:
 * software-decoded VP9/AV1 is exactly the stutter-on-low-end-devices problem that motivated excluding
 * both codecs in the first place (YTPlayerUtils.findFormat's own comment, 2026-era). A device with no
 * hardware decoder for the codec simply never reaches this rung — no regression, ladder ends at muxed
 * like it always did.
 */
object HardwareVideoDecoders {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun supportsHardwareDecode(mimeType: String): Boolean = cache.getOrPut(mimeType) {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                !info.isEncoder &&
                    info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } &&
                    isHardwareAccelerated(info)
            }
        }.getOrElse { e ->
            Timber.tag("HardwareVideoDecoders").w(e, "MediaCodecList query failed for $mimeType")
            false
        }
    }

    private fun isHardwareAccelerated(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            // Pre-Q has no reliable API for this — a software codec is virtually always named
            // "OMX.google.*"/"c2.android.*" while a real hardware one carries the vendor's own prefix,
            // so exclude the known-software families instead of trusting every codec that exists.
            !info.name.startsWith("OMX.google.", ignoreCase = true) &&
                !info.name.startsWith("c2.android.", ignoreCase = true)
        }

    const val MIME_VP9 = "video/x-vnd.on2.vp9"
    const val MIME_AV1 = "video/av01"
}
