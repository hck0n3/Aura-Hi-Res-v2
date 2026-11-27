// Aura Hi-Res — true-peak lookahead limiter (row 911). Original code written for this app (GPL-3.0, like the app).
//
// What it does, in order, per frame (all channels linked, so the stereo image never shifts):
//  1. TRUE-PEAK DETECTION: each channel's last kTpTaps samples are reconstructed 4x (windowed sinc at 1/4, 2/4 and
//     3/4 of the way between two samples), so a peak that falls BETWEEN samples — the one a DAC or a lossy encoder
//     really produces — is measured, not only the sample values (ITU-R BS.1770 method, own coefficients).
//  2. REQUIRED GAIN: ceiling / peak when the peak passes the ceiling (-1 dBTP), else exactly 1.
//  3. LOOKAHEAD HOLD: the minimum required gain over the next `lookahead` frames (~1.5 ms), so the gain is already
//     down when the peak reaches the output.
//  4. RELEASE: the gain comes back up on a ~100 ms one-pole, and never goes up while a peak is held.
//  5. SMOOTHING: a moving average over `lookahead` frames turns every gain step into a linear ramp. Because each
//     averaged value is <= the held minimum, the output can never pass the ceiling on a detected peak.
//  The audio is delayed by latencyFrames() so the gain computed for a frame is applied to that frame.
//
// Transparent below the ceiling: the gain is EXACTLY 1.0 (bit-exact copy, only delayed) when nothing passes it.
// No allocation, no locks, no syscalls: safe on the audio thread. MUST mirror eq/audio/AuraTruePeakLimiter.kt
// (TruePeakLimiterTest pins both the maths and these constants).
#pragma once

#include <cmath>
#include <cstdint>
#include <cstring>

namespace aura {

constexpr float kTpCeilingDbtp = -1.0f;     // AES TD1008 / streaming services' ceiling for lossy delivery
constexpr float kTpLookaheadMs = 1.5f;
constexpr float kTpReleaseMs = 100.0f;
constexpr float kTpSafetyClamp = 0.989f;    // -0.1 dBFS: last-resort hard clamp after the limiter, never reached by it
constexpr int kTpOversample = 4;
constexpr int kTpTaps = 32;
constexpr int kTpCentre = 15;               // the interval reconstructed is between h[15] and h[16]

/// Windowed-sinc (Blackman) coefficients of the kTpOversample-1 fractional points between h[kTpCentre] and
/// h[kTpCentre + 1], each phase normalised to unity gain at DC.
inline void tpInterpolatorCoefficients(double out[kTpOversample - 1][kTpTaps]) {
    const double pi = 3.14159265358979323846;
    for (int p = 1; p < kTpOversample; ++p) {
        const double frac = (double) p / (double) kTpOversample;
        double sum = 0.0;
        for (int k = 0; k < kTpTaps; ++k) {
            const double t = (double) (k - kTpCentre) - frac;
            const double sinc = std::fabs(t) < 1e-12 ? 1.0 : std::sin(pi * t) / (pi * t);
            const double x = t / (kTpTaps / 2.0);
            const double w = 0.42 + 0.5 * std::cos(pi * x) + 0.08 * std::cos(2.0 * pi * x);
            out[p - 1][k] = sinc * w;
            sum += out[p - 1][k];
        }
        for (int k = 0; k < kTpTaps; ++k) out[p - 1][k] /= sum;
    }
}

/// Measures the true peak (linear) of interleaved audio across consecutive blocks. Used by measureHeadroom.
class TruePeakMeter {
public:
    static constexpr int kMaxChannels = 8;

    TruePeakMeter() {
        tpInterpolatorCoefficients(coef);
        std::memset(hist, 0, sizeof(hist));
    }

    void process(const float* data, int frames, int channels) {
        if (channels < 1 || channels > kMaxChannels) return;
        for (int i = 0; i < frames; ++i) {
            const int base = pos + 1;
            for (int ch = 0; ch < channels; ++ch) {
                const float v = data[(size_t) i * channels + ch];
                hist[ch][pos] = v;
                hist[ch][pos + kTpTaps] = v;
                const float* h = &hist[ch][base];
                double m = std::fabs((double) h[kTpCentre + 1]);
                for (int p = 0; p < kTpOversample - 1; ++p) {
                    double y = 0.0;
                    for (int k = 0; k < kTpTaps; ++k) y += coef[p][k] * h[k];
                    if (std::fabs(y) > m) m = std::fabs(y);
                }
                if (m > peak) peak = m;
            }
            pos = (pos + 1) % kTpTaps;
        }
    }

    double peakLinear() const { return peak; }

private:
    double coef[kTpOversample - 1][kTpTaps];
    float hist[kMaxChannels][2 * kTpTaps];
    int pos = 0;
    double peak = 0.0;
};

class TruePeakLimiter {
public:
    static constexpr int kMaxChannels = 8;
    static constexpr int kMaxLookahead = 2048;                    // frames: 1.5 ms at 768 kHz is 1152
    static constexpr int kDelayCapacity = kMaxLookahead + kTpTaps; // > detection delay + lookahead - 1

    explicit TruePeakLimiter(unsigned int samplerate) { configure(samplerate); }

    void configure(unsigned int samplerate) {
        const double fs = (samplerate < 8000u) ? 48000.0 : (double) samplerate;
        long la = std::lround(fs * (double) kTpLookaheadMs / 1000.0);
        if (la < 1) la = 1;
        if (la > kMaxLookahead) la = kMaxLookahead;
        lookahead = (int) la;
        releaseCoef = 1.0 - std::exp(-1.0 / (fs * (double) kTpReleaseMs / 1000.0));
        ceiling = std::pow(10.0, (double) kTpCeilingDbtp / 20.0);
        tpInterpolatorCoefficients(coef);
        double maxAbsSum = 1.0;
        for (int p = 0; p < kTpOversample - 1; ++p) {
            double s = 0.0;
            for (int k = 0; k < kTpTaps; ++k) s += std::fabs(coef[p][k]);
            if (s > maxAbsSum) maxAbsSum = s;
        }
        // Below this, no sample nor any reconstructed point of a kTpTaps-sample window can pass the ceiling.
        quietThreshold = ceiling / maxAbsSum;
        reset();
    }

    void reset() {
        std::memset(hist, 0, sizeof(hist));
        std::memset(delay, 0, sizeof(delay));
        histPos = 0;
        delayPos = 0;
        prevInterval = 0.0;
        framesSinceLoud = kTpTaps;
        dqHead = 0;
        dqCount = 0;
        for (int i = 0; i < kMaxLookahead; ++i) rRing[i] = 1.0;
        rSum = (double) lookahead;
        rPos = 0;
        rPrev = 1.0;
        counter = 0;
        sinceChain = kSinceChainCap;
        idle = true;
    }

    /// Detection delay (kTpTaps - 1 - kTpCentre) + lookahead - 1.
    int latencyFrames() const { return (kTpTaps - 1 - kTpCentre) + lookahead - 1; }

    /// In place, interleaved. More than kMaxChannels channels is left untouched (the caller's clamp still runs).
    void process(float* data, int frames, int channels) { run(data, frames, channels, true); }

    /// Chain OFF: the frames still go through the same delay line, so the latency is the same on both sides of a
    /// chain on/off edge (no hole of zeros when the chain starts, no frames skipped when it stops). The chain's own
    /// frames still in the line are limited as usual; once they are out and the gain is back at exactly 1, this is a
    /// bit-exact delayed copy and no detection runs (bypass frames are never limited).
    void passThrough(float* data, int frames, int channels) { run(data, frames, channels, false); }

private:
    void run(float* data, int frames, int channels, bool chainOn) {
        if (channels < 1 || channels > kMaxChannels) return;
        const int latency = latencyFrames();
        for (int i = 0; i < frames; ++i) {
            float* frame = data + (size_t) i * channels;
            const int base = histPos + 1;
            float newestAbs = 0.0f;
            for (int ch = 0; ch < channels; ++ch) {
                const float v = frame[ch];
                hist[ch][histPos] = v;
                hist[ch][histPos + kTpTaps] = v;
                delay[delayPos][ch] = v;
                const float a = std::fabs(v);
                if (a > newestAbs) newestAbs = a;
            }
            histPos = (histPos + 1) % kTpTaps;
            if ((double) newestAbs > quietThreshold) framesSinceLoud = 0;
            else if (framesSinceLoud < kTpTaps) ++framesSinceLoud;
            if (chainOn) {
                sinceChain = 0;
                idle = false;
            } else if (sinceChain < kSinceChainCap) {
                ++sinceChain;
            }
            const int readPos = (delayPos - latency + kDelayCapacity) % kDelayCapacity;
            if (idle) {
                for (int ch = 0; ch < channels; ++ch) frame[ch] = delay[readPos][ch];
                delayPos = (delayPos + 1) % kDelayCapacity;
                ++counter;
                continue;
            }

            // 1) True peak of the interval between h[kTpCentre] and h[kTpCentre + 1], all channels. With the chain
            //    off it is measured only while that interval still touches a chain frame.
            double interval = 0.0;
            if (framesSinceLoud < kTpTaps && (chainOn || sinceChain <= kTpTaps - 1 - kTpCentre)) {
                for (int ch = 0; ch < channels; ++ch) {
                    const float* h = &hist[ch][base];
                    double m = std::fabs((double) h[kTpCentre]);
                    const double m1 = std::fabs((double) h[kTpCentre + 1]);
                    if (m1 > m) m = m1;
                    // The three phases in ONE pass over the window: three independent sums (each in the same k order as
                    // one phase at a time), so the multiply-adds no longer wait on a single accumulator (battery, rule 7).
                    static_assert(kTpOversample == 4, "three interpolation phases");
                    double y0 = 0.0, y1 = 0.0, y2 = 0.0;
                    for (int k = 0; k < kTpTaps; ++k) {
                        const double hk = h[k];
                        y0 += coef[0][k] * hk;
                        y1 += coef[1][k] * hk;
                        y2 += coef[2][k] * hk;
                    }
                    const double a0 = std::fabs(y0), a1 = std::fabs(y1), a2 = std::fabs(y2);
                    if (a0 > m) m = a0;
                    if (a1 > m) m = a1;
                    if (a2 > m) m = a2;
                    if (m > interval) interval = m;
                }
            }
            // A sample's peak is the larger of the two intervals around it.
            const double samplePeak = interval > prevInterval ? interval : prevInterval;
            prevInterval = interval;
            // 2) Required gain.
            const double greq = samplePeak > ceiling ? ceiling / samplePeak : 1.0;

            // 3) Sliding minimum over the last `lookahead` required gains (monotonic deque).
            while (dqCount > 0) {
                const int back = (dqHead + dqCount - 1) % kDequeCapacity;
                if (dqVal[back] >= greq) --dqCount; else break;
            }
            {
                const int slot = (dqHead + dqCount) % kDequeCapacity;
                dqVal[slot] = greq;
                dqIdx[slot] = counter;
                ++dqCount;
            }
            while (dqCount > 0 && dqIdx[dqHead] <= counter - lookahead) {
                dqHead = (dqHead + 1) % kDequeCapacity;
                --dqCount;
            }
            const double hold = dqVal[dqHead];

            // 4) Release: down at once, up on the one-pole; snaps to exactly 1 so the bypass is bit-exact again.
            double r = hold < rPrev ? hold : rPrev + (hold - rPrev) * releaseCoef;
            if (hold >= 1.0 && 1.0 - r < 1e-7) r = 1.0;
            rPrev = r;

            // 5) Moving average over `lookahead` (recomputed exactly once per lap so it cannot drift).
            rSum += r - rRing[rPos];
            rRing[rPos] = r;
            if (++rPos == lookahead) {
                rPos = 0;
                double s = 0.0;
                for (int k = 0; k < lookahead; ++k) s += rRing[k];
                rSum = s;
            }
            double g = rSum / (double) lookahead;
            if (g > 1.0) g = 1.0;

            // 6) Output the frame `latency` frames back, at the gain computed for it.
            if (g == 1.0) {
                for (int ch = 0; ch < channels; ++ch) frame[ch] = delay[readPos][ch];
            } else {
                for (int ch = 0; ch < channels; ++ch) frame[ch] = (float) ((double) delay[readPos][ch] * g);
            }
            delayPos = (delayPos + 1) % kDelayCapacity;
            ++counter;

            // Chain off, its last frame already out and the gain exactly 1 again: plain delay from the next frame on.
            if (!chainOn && sinceChain > latency && g == 1.0 && r == 1.0 && hold >= 1.0) {
                idle = true;
                prevInterval = 0.0;
                dqHead = 0;
                dqCount = 0;
                for (int k = 0; k < lookahead; ++k) rRing[k] = 1.0;
                rSum = (double) lookahead;
                rPos = 0;
                rPrev = 1.0;
            }
        }
    }

    static constexpr int kDequeCapacity = kMaxLookahead + 1;
    static constexpr int kSinceChainCap = 1 << 30;

    double coef[kTpOversample - 1][kTpTaps];
    double ceiling = 0.891250938;
    double quietThreshold = 0.5;
    double releaseCoef = 0.0002;
    int lookahead = 72;

    float hist[kMaxChannels][2 * kTpTaps];
    int histPos = 0;
    float delay[kDelayCapacity][kMaxChannels];
    int delayPos = 0;

    double prevInterval = 0.0;
    int framesSinceLoud = kTpTaps;

    double dqVal[kDequeCapacity];
    int64_t dqIdx[kDequeCapacity];
    int dqHead = 0;
    int dqCount = 0;

    double rRing[kMaxLookahead];
    double rSum = 72.0;
    int rPos = 0;
    double rPrev = 1.0;
    int64_t counter = 0;
    int sinceChain = kSinceChainCap;  // frames entered since the last chain frame
    bool idle = true;                  // chain off and nothing left to limit: bit-exact delay only
};

} // namespace aura
