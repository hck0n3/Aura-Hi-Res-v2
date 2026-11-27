// Aura Hi-Res — psychoacoustic bass for the phone's own speaker (registry row 920). Original code written for this app
// (GPL-3.0, like the app); it ports no third-party code. The method is the published one: a small speaker cannot move
// the air below its cutoff, but the ear hears the pitch of a missing fundamental from its harmonics (Larsen & Aarts,
// "Reproducing Low-Pitched Signals through Small Loudspeakers", JAES 50(3), 2002).
//
// What it does, per frame:
//  1. SPEAKER PATH: every channel is high-passed at kVbCutoffHz (4th-order Butterworth), so the bass the speaker cannot
//     play no longer drives its membrane into distortion.
//  2. LOW BAND: the mean of the channels, band-passed kVbLowFloorHz..kVbCutoffHz (2nd-order high-pass, 4th-order
//     low-pass) — the fundamentals that are about to be removed.
//  3. CONTROLLED NONLINEARITY: the low band divided by its own peak envelope (instant attack, kVbReleaseMs release) is
//     u in [-1, 1]; Chebyshev polynomials T2(u) = 2u^2 - 1 and T3(u) = 4u^3 - 3u turn a sine cos(t) into EXACTLY
//     cos(2t) and cos(3t), and multiplying back by the envelope makes the harmonics follow the bass level linearly
//     (a soft bass makes soft harmonics, no gating, no fixed distortion). Below kVbGate nothing is generated.
//  4. BAND-LIMIT: the harmonics are high-passed at kVbCutoffHz (2nd order: no harmonic the speaker cannot play either,
//     and the DC of T2 goes) and low-passed at kVbHarmonicTopHz (2nd order), then added to every channel.
//  On/off glides over kVbRampMs (dry <-> wet crossfade) so the switch, a route change or the stage stopping never clicks.
//  Its extra peak on sines and band-limited mixes, measured (VirtualBassTest), stays under kVbMaxExtraDb, which
//  «Headroom automático» counts. A full-scale square bass (clipped master) overshoots more, as through any high-pass:
//  the true-peak limiter after this stage still holds -1 dBTP.
//
// No allocation, no locks, no syscalls: safe on the audio thread. `setRequested` is the only call from another thread
// (one relaxed atomic). MUST mirror the JVM copy in VirtualBassTest (it pins these constants).
#pragma once

#include <atomic>
#include <cmath>

namespace aura {

constexpr float kVbCutoffHz = 150.0f;        // a phone speaker plays little below ~120-150 Hz (plan, point 8)
constexpr float kVbLowFloorHz = 40.0f;       // fundamentals below this make harmonics the speaker cannot play either
constexpr float kVbHarmonicTopHz = 500.0f;   // the harmonics band: ~150-500 Hz
constexpr float kVbSecondHarmonic = 0.5f;    // -6 dB re. the removed fundamental
constexpr float kVbThirdHarmonic = 0.35f;    // -9.1 dB
constexpr float kVbReleaseMs = 200.0f;
constexpr float kVbRampMs = 30.0f;
constexpr float kVbGate = 1.0e-5f;           // -100 dBFS: below it the low band is silence
constexpr float kVbMaxExtraDb = 2.0f;        // measured peak growth on sines and band-limited mixes (<= 1.7 dB)
constexpr int kVbMaxChannels = 8;

/// RBJ biquad, transposed direct form II, double state per channel.
class VbBiquad {
public:
    void lowPass(double sampleRate, double hz, double q) { design(sampleRate, hz, q, false); }
    void highPass(double sampleRate, double hz, double q) { design(sampleRate, hz, q, true); }

    double run(int ch, double x) {
        const double y = b0 * x + z1[ch];
        z1[ch] = b1 * x - a1 * y + z2[ch];
        z2[ch] = b2 * x - a2 * y;
        return y;
    }

    void reset() {
        for (int ch = 0; ch < kVbMaxChannels; ++ch) { z1[ch] = 0.0; z2[ch] = 0.0; }
    }

private:
    void design(double sampleRate, double hz, double q, bool high) {
        const double pi = 3.14159265358979323846;
        const double w0 = 2.0 * pi * hz / sampleRate;
        const double c = std::cos(w0);
        const double alpha = std::sin(w0) / (2.0 * q);
        const double a0 = 1.0 + alpha;
        const double k = high ? (1.0 + c) / 2.0 : (1.0 - c) / 2.0;
        b0 = k / a0;
        b1 = (high ? -2.0 * k : 2.0 * k) / a0;
        b2 = k / a0;
        a1 = -2.0 * c / a0;
        a2 = (1.0 - alpha) / a0;
        reset();
    }

    double b0 = 1.0, b1 = 0.0, b2 = 0.0, a1 = 0.0, a2 = 0.0;
    double z1[kVbMaxChannels] = {};
    double z2[kVbMaxChannels] = {};
};

class VirtualBass {
public:
    explicit VirtualBass(unsigned int sampleRate) {
        const double sr = sampleRate > 0 ? (double) sampleRate : 48000.0;
        // 4th-order Butterworth = two 2nd-order sections with these Q values.
        const double q1 = 0.54119610014619698, q2 = 1.3065629648763766, qb = 0.70710678118654752;
        speakerHp[0].highPass(sr, kVbCutoffHz, q1);
        speakerHp[1].highPass(sr, kVbCutoffHz, q2);
        lowLp[0].lowPass(sr, kVbCutoffHz, q1);
        lowLp[1].lowPass(sr, kVbCutoffHz, q2);
        lowHp.highPass(sr, kVbLowFloorHz, qb);
        harmonicHp.highPass(sr, kVbCutoffHz, qb);
        harmonicLp.lowPass(sr, kVbHarmonicTopHz, qb);
        release = std::exp(-1.0 / (sr * kVbReleaseMs / 1000.0));
        rampStep = (float) (1.0 / (sr * kVbRampMs / 1000.0));
    }

    /// Any thread: what the user's switch and the output route ask for. The audio thread follows it with a ramp.
    void setRequested(bool on) { requested.store(on, std::memory_order_relaxed); }

    /// Audio thread: the stage has to run (asked for, or still ramping out).
    bool wanted() const { return requested.load(std::memory_order_relaxed) || mix > 0.0f; }

    /// Audio thread: empties the filters and the envelope (flush). The on/off ramp is kept.
    void reset() {
        for (auto& f : speakerHp) f.reset();
        for (auto& f : lowLp) f.reset();
        lowHp.reset();
        harmonicHp.reset();
        harmonicLp.reset();
        envelope = 0.0;
    }

    /// Audio thread, in place, interleaved. A no-op (bit-exact) while not wanted.
    void process(float* data, int frames, int channels) {
        if (channels < 1 || channels > kVbMaxChannels) return;
        const bool on = requested.load(std::memory_order_relaxed);
        if (!on && mix <= 0.0f) return;
        if (idle) { reset(); idle = false; } // starts from clean state, under the ramp
        const double inv = 1.0 / (double) channels;
        for (int i = 0; i < frames; ++i) {
            if (on) { if (mix < 1.0f) { mix += rampStep; if (mix > 1.0f) mix = 1.0f; } }
            else { mix -= rampStep; if (mix < 0.0f) mix = 0.0f; }
            float* frame = data + (long) i * channels;
            double mono = 0.0;
            for (int ch = 0; ch < channels; ++ch) mono += frame[ch];
            mono *= inv;
            const double low = lowHp.run(0, lowLp[1].run(0, lowLp[0].run(0, mono)));
            const double a = std::fabs(low);
            envelope *= release;
            if (a > envelope) envelope = a;
            double harmonics = 0.0;
            if (envelope > kVbGate) {
                double u = low / envelope;
                if (u > 1.0) u = 1.0; else if (u < -1.0) u = -1.0;
                const double t2 = 2.0 * u * u - 1.0;
                const double t3 = (4.0 * u * u - 3.0) * u;
                harmonics = envelope * (kVbSecondHarmonic * t2 + kVbThirdHarmonic * t3);
            }
            harmonics = harmonicLp.run(0, harmonicHp.run(0, harmonics));
            const double m = mix;
            for (int ch = 0; ch < channels; ++ch) {
                const double x = frame[ch];
                const double wet = speakerHp[1].run(ch, speakerHp[0].run(ch, x)) + harmonics;
                frame[ch] = (float) (x + m * (wet - x));
            }
        }
        if (!on && mix <= 0.0f) idle = true;
    }

private:
    std::atomic<bool> requested{false};
    float mix = 0.0f;
    float rampStep = 0.0f;
    bool idle = true;
    double envelope = 0.0;
    double release = 0.0;
    VbBiquad speakerHp[2];
    VbBiquad lowLp[2];
    VbBiquad lowHp;
    VbBiquad harmonicHp;
    VbBiquad harmonicLp;
};

} // namespace aura
