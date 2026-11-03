package iad1tya.echo.music.listentogether

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Row 649 — MEASURES how long the audio the app hands to the current output really takes to be HEARD.
 *
 * Why it exists (owner 2026-11-03, a Zealot S89 on the tablet and a Harman Kardon Onyx Studio 9 on the phone: «lo sincroniza y al rato
 * pierde la sincronía»): with Bluetooth the delay of each speaker is different, changes between connections (±100 ms) and drifts inside
 * one session (−3…+4 ms/s measured with a microphone). Android exposes no API for it, so [OutputLatency] could only GUESS 180 ms.
 *
 * How: a short chirp is played on the SAME route as the music (media / music attributes) while the microphone records; the chirp is found
 * in the recording with a matched filter. Both clocks are Android's own: `AudioTrack.getTimestamp` says when the chirp frame is presented
 * (the same notion the player's position uses) and `AudioRecord.getTimestamp` says when each recorded frame was captured, so the
 * difference is exactly what the position does NOT account for: the Bluetooth buffer, the speaker, the air. That is the number
 * [OutputLatency] needs.
 */
object OutputLatencyProbe {
    const val SAMPLE_RATE = 48_000
    private const val CHIRP_F0 = 5_000f
    private const val CHIRP_F1 = 7_000f
    private const val CHIRP_MS = 100
    private const val LEAD_MS = 350
    private const val RECORD_MS = 1_900
    private const val DECIMATE = 3

    data class Result(val latencyMs: Int, val quality: Float)

    /** A windowed linear sweep, [amp] of full scale. */
    fun chirp(sampleRate: Int, f0: Float, f1: Float, ms: Int, amp: Float): ShortArray {
        val n = sampleRate * ms / 1000
        val t = ms / 1000.0
        return ShortArray(n) { i ->
            val x = i / sampleRate.toDouble()
            val phase = 2.0 * PI * (f0 * x + (f1 - f0) / (2.0 * t) * x * x)
            val hann = 0.5 - 0.5 * cos(2.0 * PI * i / (n - 1))
            (sin(phase) * hann * amp * 32767.0).toInt().toShort()
        }
    }

    data class Match(val index: Float, val quality: Float)

    /**
     * Pure. Where [ref] (a chirp) starts inside [signal], searched only in [minIndex]..[maxIndex] (samples at [sampleRate]).
     * Both are decimated, band-passed around the chirp band and compared by an energy-NORMALISED correlation, so loud music in the
     * recording does not decide the peak. Returns null when nothing clearly stands out. [Match.index] is in samples at [sampleRate].
     */
    fun matchedPeak(
        signal: ShortArray,
        ref: ShortArray,
        sampleRate: Int,
        f0: Float,
        f1: Float,
        minIndex: Int,
        maxIndex: Int,
    ): Match? {
        val d = DECIMATE
        val fs = sampleRate / d
        val fc = (f0 + f1) / 2f
        val q = fc / (f1 - f0)
        val s = bandPass(decimate(signal, d), fs, fc, q)
        val r = bandPass(decimate(ref, d), fs, fc, q)
        val m = r.size
        val n = s.size - m
        if (n <= 0 || m == 0) return null
        var rEnergy = 0.0
        for (v in r) rEnergy += v.toDouble() * v
        rEnergy = sqrt(rEnergy)
        if (rEnergy < 1e-6) return null
        val prefix = DoubleArray(s.size + 1)
        for (i in s.indices) prefix[i + 1] = prefix[i] + s[i].toDouble() * s[i]

        val lo = max(0, minIndex / d)
        val hi = min(n, maxIndex / d)
        if (hi <= lo) return null
        val rho = FloatArray(hi - lo + 1)
        var best = 0f
        var bestK = -1
        var sum = 0.0
        for (k in lo..hi) {
            var dot = 0.0
            for (i in 0 until m) dot += r[i] * s[k + i]
            val e = sqrt(prefix[k + m] - prefix[k])
            val v = if (e < 1e-6) 0f else (abs(dot) / (rEnergy * e)).toFloat()
            rho[k - lo] = v
            sum += v
            if (v > best) { best = v; bestK = k }
        }
        if (bestK < 0) return null
        val mean = (sum / rho.size).toFloat()
        // A clear single peak: strong in absolute terms and well above the average of the window.
        if (best < 0.2f || best < 4f * mean) return null
        // Row 649: ANOTHER device chirping at the same moment (two devices in one room measuring together) makes two peaks of similar
        // size; picking either would be a coin toss and a wrong delay. A competing peak ≥ 3 ms away from the best and at least 70 % of
        // it means "ambiguous": no measurement.
        val bestIdx = bestK - lo
        val apart = 3 * fs / 1000
        var second = 0f
        for (j in rho.indices) if (abs(j - bestIdx) > apart && rho[j] > second) second = rho[j]
        if (second >= 0.7f * best) return null
        var frac = 0f
        val i0 = bestK - lo
        if (i0 > 0 && i0 < rho.size - 1) {
            val a = rho[i0 - 1]; val b = rho[i0]; val c = rho[i0 + 1]
            val denom = a - 2 * b + c
            if (abs(denom) > 1e-9f) frac = (0.5f * (a - c) / denom).coerceIn(-1f, 1f)
        }
        return Match((bestK + frac) * d, best)
    }

    private fun decimate(x: ShortArray, d: Int): FloatArray {
        val out = FloatArray(x.size / d)
        for (i in out.indices) {
            var s = 0f
            for (j in 0 until d) s += x[i * d + j]
            out[i] = s / d / 32768f
        }
        return out
    }

    /** RBJ band-pass biquad. The same filter is applied to the chirp and to the recording, so its delay cancels. */
    private fun bandPass(x: FloatArray, fs: Int, fc: Float, q: Float): FloatArray {
        val w0 = 2.0 * PI * fc / fs
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        val b0 = alpha / a0
        val b2 = -alpha / a0
        val a1 = -2.0 * cos(w0) / a0
        val a2 = (1.0 - alpha) / a0
        val out = FloatArray(x.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val xi = x[i].toDouble()
            val y = b0 * xi + b2 * x2 - a1 * y1 - a2 * y2
            out[i] = y.toFloat()
            x2 = x1; x1 = xi; y2 = y1; y1 = y
        }
        return out
    }

    /**
     * One measurement on the current output, or null (no timestamp, nothing stood out, or the hardware refused). Needs RECORD_AUDIO,
     * which the caller checks. About 2 s; the chirp is brief and at a moderate level.
     */
    @SuppressLint("MissingPermission")
    suspend fun measure(context: Context): Result? = withContext(Dispatchers.IO) {
        val sr = SAMPLE_RATE
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return@withContext null
        val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC
        val recFrames = sr * RECORD_MS / 1000
        val minRec = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minRec <= 0) return@withContext null
        val record = runCatching {
            AudioRecord(
                source, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                max(minRec * 4, recFrames * 2),
            )
        }.getOrNull() ?: return@withContext null
        if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); return@withContext null }

        val ref = chirp(sr, CHIRP_F0, CHIRP_F1, CHIRP_MS, 0.35f)
        val leadFrames = sr * LEAD_MS / 1000
        val total = leadFrames + ref.size + sr / 2
        val pcm = ShortArray(total).also { ref.copyInto(it, leadFrames) }
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val fmt = AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        val minTrack = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = runCatching {
            AudioTrack(attrs, fmt, max(minTrack * 2, 8192), AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
        }.getOrNull()
        if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track?.release() }; record.release(); return@withContext null
        }

        try {
            val recorded = ShortArray(recFrames)
            val recTs = AudioTimestamp()
            val trackTs = AudioTimestamp()
            var trackTsOk = false
            coroutineScope {
                val reading = async(Dispatchers.IO) {
                    record.startRecording()
                    var off = 0
                    while (off < recFrames) {
                        val got = record.read(recorded, off, min(2048, recFrames - off))
                        if (got <= 0) break
                        off += got
                    }
                }
                delay(200) // microphone running before the chirp starts
                val playing = async(Dispatchers.IO) {
                    track.play()
                    var written = 0
                    while (written < total) {
                        val w = track.write(pcm, written, min(2048, total - written))
                        if (w <= 0) break
                        written += w
                        if (!trackTsOk && written >= 4096) trackTsOk = track.getTimestamp(trackTs) && trackTs.framePosition > 0
                    }
                    // The timestamp becomes valid after the first frames have been played.
                    var tries = 0
                    while (!trackTsOk && tries++ < 40) {
                        delay(15)
                        trackTsOk = track.getTimestamp(trackTs) && trackTs.framePosition > 0
                    }
                }
                playing.await()
                reading.await()
            }
            val recStatus = record.getTimestamp(recTs, AudioTimestamp.TIMEBASE_MONOTONIC)
            if (!trackTsOk || recStatus != AudioRecord.SUCCESS) return@withContext null

            // When (CLOCK_MONOTONIC, ns) the first chirp frame is presented, and when each recorded frame was captured.
            val presentedNs = trackTs.nanoTime + ((leadFrames - trackTs.framePosition) * 1_000_000_000.0 / sr).toLong()
            // The chirp is expected to be heard between a little before its presentation and ~0.9 s after (a Bluetooth buffer).
            val recStartNs = recTs.nanoTime - (recTs.framePosition * 1_000_000_000.0 / sr).toLong()
            val expected = ((presentedNs - recStartNs) * sr / 1_000_000_000.0).toInt()
            val match = matchedPeak(recorded, ref, sr, CHIRP_F0, CHIRP_F1, expected - sr / 20, expected + sr * 9 / 10)
                ?: return@withContext null
            val latencyMs = ((match.index - expected) * 1000.0 / sr).toInt()
            if (latencyMs !in -50..900) return@withContext null
            Result(latencyMs, match.quality)
        } catch (e: Exception) {
            Timber.tag("OutputLatencyProbe").w("LT_CAL probe failed %s", e.javaClass.simpleName)
            null
        } finally {
            runCatching { track.stop() }
            track.release()
            runCatching { record.stop() }
            record.release()
        }
    }
}
