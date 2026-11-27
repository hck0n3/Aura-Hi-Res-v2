// Aura Hi-Res — split-band dynamic de-esser (row 917). Original code written for this app (GPL-3.0, like the app).
//
// What it does, per frame (every channel linked, so the stereo image never shifts):
//  1. SIBILANCE KEY: each channel through a 4th-order Butterworth high-pass at 5 kHz and a 2nd-order Butterworth
//     low-pass at 9 kHz; the powers are averaged over the channels.
//  2. REFERENCE KEY: the same channels through a 2nd-order high-pass at 200 Hz (the bass does not decide, like the
//     glue compressor's key, row 610), averaged the same way.
//  3. ENVELOPES: both powers follow a one-pole envelope with HALF the time constants, so the RMS levels they
//     stand for have a 2 ms attack and an 80 ms release.
//  4. RATIO DETECTION: every kDsControlFrames frames, reduction = (10 log10(sibilance / reference) - threshold) x
//     slope, clamped to [0, kDsMaxReductionDb]. A passage that is loud everywhere has a LOW ratio and is never
//     touched for being loud; only a 5-9 kHz band that stands out from the rest of the mix is lowered. Below
//     kDsFloorPower in the band nothing happens. The gain ramps linearly to the new value over the next
//     kDsControlFrames frames.
//  5. SPLIT BAND (dynamic EQ): out = in - (1 - gain) x bandpass(in), with a 0 dB-peak band-pass whose -3 dB points
//     are exactly 5 and 9 kHz at any sample rate (both edges pre-warped). Only that band is lowered; outside it the
//     response stays at 0 dB.
//
// Transparent when there is no sibilance: the gain is EXACTLY 1.0 and the samples are left untouched (bit-exact).
// No allocation, no locks, no syscalls: safe on the audio thread. MUST mirror the JVM copy in
// app/src/test/.../eq/audio/AuraDeEsserMirror.kt (DeEsserTest pins both the maths and these constants).
#pragma once

#include <cmath>
#include <cstring>

namespace aura {

constexpr double kDsBandLowHz = 5000.0;
constexpr double kDsBandHighHz = 9000.0;
constexpr double kDsKeyHighPassHz = 200.0;
constexpr double kDsThresholdDb = -12.0;
constexpr double kDsSlope = 0.75;           // 4:1 above the threshold
constexpr double kDsMaxReductionDb = 6.0;
constexpr double kDsAttackMs = 2.0;
constexpr double kDsReleaseMs = 80.0;
constexpr double kDsFloorPower = 1e-6;      // -60 dBFS RMS in the band
constexpr int kDsControlFrames = 32;

/// Transposed direct form II biquad, double precision, one state per channel.
struct DsBiquad {
    static constexpr int kMaxChannels = 8;
    double b0 = 1.0, b1 = 0.0, b2 = 0.0, a1 = 0.0, a2 = 0.0;
    double z1[kMaxChannels] = {};
    double z2[kMaxChannels] = {};

    /// RBJ cookbook. kind: 0 low-pass, 1 high-pass, 2 band-pass with 0 dB peak gain.
    void design(int kind, double fs, double f0, double q) {
        if (f0 > fs * 0.45) f0 = fs * 0.45;
        const double w0 = 2.0 * 3.14159265358979323846 * f0 / fs;
        const double c = std::cos(w0);
        const double alpha = std::sin(w0) / (2.0 * q);
        const double a0 = 1.0 + alpha;
        if (kind == 0) {
            b0 = (1.0 - c) / 2.0; b1 = 1.0 - c; b2 = (1.0 - c) / 2.0;
        } else if (kind == 1) {
            b0 = (1.0 + c) / 2.0; b1 = -(1.0 + c); b2 = (1.0 + c) / 2.0;
        } else {
            b0 = alpha; b1 = 0.0; b2 = -alpha;
        }
        b0 /= a0; b1 /= a0; b2 /= a0;
        a1 = -2.0 * c / a0;
        a2 = (1.0 - alpha) / a0;
    }
    inline double run(int ch, double x) {
        const double y = b0 * x + z1[ch];
        z1[ch] = b1 * x - a1 * y + z2[ch];
        z2[ch] = b2 * x - a2 * y;
        return y;
    }
    void clear() {
        std::memset(z1, 0, sizeof(z1));
        std::memset(z2, 0, sizeof(z2));
    }
};

class DeEsser {
public:
    static constexpr int kMaxChannels = DsBiquad::kMaxChannels;

    explicit DeEsser(unsigned int samplerate) {
        const double fs = (double) samplerate;
        // The band must fit under Nyquist with room to spare; below 22.05 kHz there is no sibilance band to treat.
        usable = fs >= 22050.0;
        // Band edges pre-warped (bilinear transform), so the -3 dB points land on 5 and 9 kHz exactly.
        const double pi = 3.14159265358979323846;
        const double wl = std::tan(pi * kDsBandLowHz / fs);
        const double wh = std::tan(pi * kDsBandHighHz / fs);
        const double w0 = std::sqrt(wl * wh);
        hpA.design(1, fs, kDsBandLowHz, 0.54119610014619698);   // 4th-order Butterworth = Q 0.5412 + Q 1.3066
        hpB.design(1, fs, kDsBandLowHz, 1.3065629648763766);
        lp.design(0, fs, kDsBandHighHz, 0.70710678118654752);
        ref.design(1, fs, kDsKeyHighPassHz, 0.70710678118654752);
        band.design(2, fs, std::atan(w0) * fs / pi, w0 / (wh - wl));
        // Power envelopes: half the time constants of the RMS level they follow.
        attack = 1.0 - std::exp(-2.0 / (fs * kDsAttackMs / 1000.0));
        release = 1.0 - std::exp(-2.0 / (fs * kDsReleaseMs / 1000.0));
        reset();
    }

    /// Back to flat with empty filters (switched off, flush).
    void reset() {
        hpA.clear(); hpB.clear(); lp.clear(); ref.clear(); band.clear();
        envKey = 0.0;
        envRef = 0.0;
        gain = 1.0;
        target = 1.0;
        step = 0.0;
        rampLeft = 0;
        countdown = kDsControlFrames;
    }

    /// The gain currently applied to the 5-9 kHz band (1 = untouched). For tests and measurements.
    double currentGain() const { return gain; }

    void process(float* data, int frames, int channels) {
        if (!usable || channels < 1 || channels > kMaxChannels) return;
        const double inv = 1.0 / (double) channels;
        double bp[kMaxChannels];
        for (int f = 0; f < frames; ++f) {
            float* frame = data + f * channels;
            double pKey = 0.0, pRef = 0.0;
            for (int ch = 0; ch < channels; ++ch) {
                const double x = frame[ch];
                const double k = lp.run(ch, hpB.run(ch, hpA.run(ch, x)));
                const double r = ref.run(ch, x);
                pKey += k * k;
                pRef += r * r;
                bp[ch] = band.run(ch, x);
            }
            pKey *= inv;
            pRef *= inv;
            envKey += (pKey - envKey) * (pKey > envKey ? attack : release);
            envRef += (pRef - envRef) * (pRef > envRef ? attack : release);

            if (--countdown == 0) {
                countdown = kDsControlFrames;
                double reductionDb = 0.0;
                if (envKey > kDsFloorPower && envRef > 0.0) {
                    const double ratioDb = 10.0 * std::log10(envKey / envRef);
                    reductionDb = (ratioDb - kDsThresholdDb) * kDsSlope;
                    if (reductionDb > kDsMaxReductionDb) reductionDb = kDsMaxReductionDb;
                }
                const double next = reductionDb > 0.0 ? std::pow(10.0, -reductionDb / 20.0) : 1.0;
                if (next != target || gain != target) {
                    target = next;
                    step = (target - gain) / (double) kDsControlFrames;
                    rampLeft = kDsControlFrames;
                }
            }
            if (rampLeft > 0) {
                gain += step;
                if (--rampLeft == 0) gain = target;
            }
            if (gain != 1.0) {
                const double cut = 1.0 - gain;
                for (int ch = 0; ch < channels; ++ch) frame[ch] = (float) ((double) frame[ch] - cut * bp[ch]);
            }
        }
        if (!std::isfinite(envKey) || !std::isfinite(envRef) || !std::isfinite(gain)) reset();
    }

private:
    bool usable = true;
    DsBiquad hpA, hpB, lp, ref, band;
    double attack = 0.0, release = 0.0;
    double envKey = 0.0, envRef = 0.0;
    double gain = 1.0, target = 1.0, step = 0.0;
    int rampLeft = 0;
    int countdown = kDsControlFrames;
};

}  // namespace aura
