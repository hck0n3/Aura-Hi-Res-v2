// Aura Hi-Res — loudness compensation at low volume (row 918). Original code written for this app (GPL-3.0, like the app).
//
// Two gentle first-order shelves whose gains Kotlin derives from the ISO 226 equal-loudness contours and the system
// music volume (eq/audio/LoudnessCompensation.kt), plus an optional overall trim (the automatic headroom):
//
//     out = trim x (in + (bass - 1) x LP1(in, 100 Hz) + (treble - 1) x HP1(in, 10 kHz))
//
// LP1 / HP1 are one-pole bilinear (topology-preserving) filters, so each term is an exact first-order shelf: flat at
// 0 dB in the middle, `bass` below ~100 Hz, `treble` above ~10 kHz, monotonic in between, no dip. The three
// parameters glide linearly over kLcGlideMs when the volume moves (no step, no click). At bass = treble = trim = 1
// and no glide the stage is not run at all (engaged() is false): the samples are untouched.
//
// No allocation, no locks, no syscalls: safe on the audio thread. MUST mirror the JVM copy in
// app/src/test/.../eq/audio/AuraLoudnessStageMirror.kt (LoudnessCompensationStageTest pins both).
#pragma once

#include <cmath>
#include <cstring>

namespace aura {

constexpr double kLcBassCornerHz = 100.0;
constexpr double kLcTrebleCornerHz = 10000.0;
constexpr double kLcGlideMs = 10.0;          // same glide as the EQ bands and the preamp (row 912)

class LoudnessCompensation {
public:
    static constexpr int kMaxChannels = 8;

    explicit LoudnessCompensation(unsigned int samplerate) {
        const double fs = (double) samplerate;
        const double pi = 3.14159265358979323846;
        double fb = kLcBassCornerHz, ft = kLcTrebleCornerHz;
        if (ft > fs * 0.45) ft = fs * 0.45;
        const double gb = std::tan(pi * fb / fs);
        const double gt = std::tan(pi * ft / fs);
        kBass = gb / (1.0 + gb);
        kTreble = gt / (1.0 + gt);
        glideFrames = (int) (fs * kLcGlideMs / 1000.0);
        if (glideFrames < 1) glideFrames = 1;
        clearState();
    }

    /// AUDIO THREAD (snapshot intake). Linear gains: bass and treble plateaus, overall trim; 1 = untouched.
    void setTarget(double bass, double treble, double trim) {
        if (bass == tBass && treble == tTreble && trim == tTrim) return;
        // Idle stages are not run, so their filter state is stale: start clean when one starts again.
        if (!engaged()) clearState();
        tBass = bass; tTreble = treble; tTrim = trim;
        dBass = (tBass - cBass) / (double) glideFrames;
        dTreble = (tTreble - cTreble) / (double) glideFrames;
        dTrim = (tTrim - cTrim) / (double) glideFrames;
        glideLeft = glideFrames;
    }

    /// Something to do: a gain away from 1 or a glide still running.
    bool engaged() const { return glideLeft > 0 || cBass != 1.0 || cTreble != 1.0 || cTrim != 1.0; }

    /// Empties the filters (flush); the gains stay where they are.
    void clearState() {
        std::memset(sBass, 0, sizeof(sBass));
        std::memset(sTreble, 0, sizeof(sTreble));
    }

    double currentBass() const { return cBass; }
    double currentTreble() const { return cTreble; }
    double currentTrim() const { return cTrim; }

    void process(float* data, int frames, int channels) {
        if (channels < 1 || channels > kMaxChannels) return;
        for (int f = 0; f < frames; ++f) {
            if (glideLeft > 0) {
                cBass += dBass; cTreble += dTreble; cTrim += dTrim;
                if (--glideLeft == 0) { cBass = tBass; cTreble = tTreble; cTrim = tTrim; }
            }
            const double eb = cBass - 1.0, et = cTreble - 1.0, tr = cTrim;
            float* frame = data + f * channels;
            for (int ch = 0; ch < channels; ++ch) {
                const double x = frame[ch];
                const double vb = (x - sBass[ch]) * kBass;
                const double lp = vb + sBass[ch];
                sBass[ch] = lp + vb;
                const double vt = (x - sTreble[ch]) * kTreble;
                const double lpt = vt + sTreble[ch];
                sTreble[ch] = lpt + vt;
                frame[ch] = (float) (tr * (x + eb * lp + et * (x - lpt)));
            }
        }
        for (int ch = 0; ch < channels; ++ch) {
            if (!std::isfinite(sBass[ch]) || !std::isfinite(sTreble[ch])) { clearState(); break; }
        }
    }

private:
    double kBass = 0.0, kTreble = 0.0;
    int glideFrames = 1;
    int glideLeft = 0;
    double cBass = 1.0, cTreble = 1.0, cTrim = 1.0;     // in use
    double tBass = 1.0, tTreble = 1.0, tTrim = 1.0;     // target
    double dBass = 0.0, dTreble = 0.0, dTrim = 0.0;     // per-frame step
    double sBass[kMaxChannels] = {};
    double sTreble[kMaxChannels] = {};
};

}  // namespace aura
