package iad1tya.echo.music.eq.audio

import kotlin.math.tan

/**
 * Row 918: JVM mirror of `app/src/main/cpp/superpowered/AuraLoudnessCompensation.h`, line for line (same constants,
 * same order of operations, float in and out, double inside). [LoudnessCompensationStageTest] pins it to the header.
 */
class AuraLoudnessStageMirror(sampleRate: Int) {
    companion object {
        const val BASS_CORNER_HZ = 100.0
        const val TREBLE_CORNER_HZ = 10000.0
        const val GLIDE_MS = 10.0
        const val MAX_CHANNELS = 8
    }

    private val kBass: Double
    private val kTreble: Double
    private val glideFrames: Int
    private var glideLeft = 0
    var cBass = 1.0; private set
    var cTreble = 1.0; private set
    var cTrim = 1.0; private set
    private var tBass = 1.0; private var tTreble = 1.0; private var tTrim = 1.0
    private var dBass = 0.0; private var dTreble = 0.0; private var dTrim = 0.0
    private val sBass = DoubleArray(MAX_CHANNELS)
    private val sTreble = DoubleArray(MAX_CHANNELS)

    init {
        val fs = sampleRate.toDouble()
        val pi = 3.14159265358979323846
        val fb = BASS_CORNER_HZ
        var ft = TREBLE_CORNER_HZ
        if (ft > fs * 0.45) ft = fs * 0.45
        val gb = tan(pi * fb / fs)
        val gt = tan(pi * ft / fs)
        kBass = gb / (1.0 + gb)
        kTreble = gt / (1.0 + gt)
        glideFrames = (fs * GLIDE_MS / 1000.0).toInt().coerceAtLeast(1)
        clearState()
    }

    fun setTarget(bass: Double, treble: Double, trim: Double) {
        if (bass == tBass && treble == tTreble && trim == tTrim) return
        if (!engaged()) clearState()
        tBass = bass; tTreble = treble; tTrim = trim
        dBass = (tBass - cBass) / glideFrames
        dTreble = (tTreble - cTreble) / glideFrames
        dTrim = (tTrim - cTrim) / glideFrames
        glideLeft = glideFrames
    }

    fun engaged(): Boolean = glideLeft > 0 || cBass != 1.0 || cTreble != 1.0 || cTrim != 1.0

    fun clearState() { sBass.fill(0.0); sTreble.fill(0.0) }

    fun process(data: FloatArray, offsetFrames: Int, frames: Int, channels: Int) {
        if (channels < 1 || channels > MAX_CHANNELS) return
        for (f in 0 until frames) {
            if (glideLeft > 0) {
                cBass += dBass; cTreble += dTreble; cTrim += dTrim
                if (--glideLeft == 0) { cBass = tBass; cTreble = tTreble; cTrim = tTrim }
            }
            val eb = cBass - 1.0
            val et = cTreble - 1.0
            val tr = cTrim
            val base = (offsetFrames + f) * channels
            for (ch in 0 until channels) {
                val x = data[base + ch].toDouble()
                val vb = (x - sBass[ch]) * kBass
                val lp = vb + sBass[ch]
                sBass[ch] = lp + vb
                val vt = (x - sTreble[ch]) * kTreble
                val lpt = vt + sTreble[ch]
                sTreble[ch] = lpt + vt
                data[base + ch] = (tr * (x + eb * lp + et * (x - lpt))).toFloat()
            }
        }
        for (ch in 0 until channels) {
            if (!sBass[ch].isFinite() || !sTreble[ch].isFinite()) { clearState(); break }
        }
    }
}
