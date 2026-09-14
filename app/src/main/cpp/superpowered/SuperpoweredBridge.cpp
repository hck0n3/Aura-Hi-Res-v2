#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <cstdlib>
#include <algorithm>

#if __has_include("Superpowered.h")
#define HAS_SUPERPOWERED 1
#include "Superpowered.h"
#include "SuperpoweredFilter.h"
#include "SuperpoweredSimple.h"
#include "SuperpoweredLimiter.h"
#include "SuperpoweredSpatializer.h"
#include "SuperpoweredReverb.h"
#include "SuperpoweredCompressor.h"
#else
#define HAS_SUPERPOWERED 0
#endif

#define LOG_TAG "SuperpoweredBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#if HAS_SUPERPOWERED
#define MAX_AUDIO_FRAMES 16384
#define MAX_AUDIO_CHANNELS 2
#define MAX_BUFFER_SIZE (MAX_AUDIO_FRAMES * MAX_AUDIO_CHANNELS)

static std::mutex globalInitMutex;
static bool isSuperpoweredInitialized = false;

// Engine health, decided ONCE right after Superpowered::Initialize() by an empirical DSP probe.
// Superpowered::Initialize() returns void and the SDK exposes no "am I licensed?" query, so a rejected
// or disabled license key is otherwise completely silent: initSuperpowered still returns a valid
// pointer and processAudio still runs, while the EQ and Safe Volume quietly stop altering the audio.
// The probe below answers the question the SDK refuses to: does the DSP actually change audio?
// Values are mirrored by ENGINE_* in CustomEqualizerAudioProcessor.kt.
#define SP_ENGINE_UNKNOWN  0
#define SP_ENGINE_HEALTHY  1
#define SP_ENGINE_DEGRADED 2
static int superpoweredEngineHealth = SP_ENGINE_UNKNOWN;

/// Empirical proof-of-life for the Superpowered DSP.
///
/// Pushes a 1 kHz sine through a throwaway +12 dB parametric filter centred on that exact frequency and
/// checks the output actually got louder. A licensed, working engine multiplies the level by ~4x; an
/// engine that has been disabled, silenced or turned into a pass-through does not. The margin is
/// enormous (4.0 expected vs a 1.05 threshold), so this cannot realistically false-alarm on a healthy
/// engine, and it catches silence, pass-through and NaN/garbage output alike.
///
/// Runs exactly once per process, on a throwaway Filter instance — it never touches the EQ's own filters,
/// the limiter, the user's audio or any tuned DSP parameter. Cost is ~8k samples through one biquad
/// (tens of microseconds, once), so it is thermally and battery-wise free.
static bool probeSuperpoweredDsp(unsigned int samplerate) {
    // Defensive: a nonsense sample rate would make the probe tone meaningless and could report a
    // healthy engine as broken. Refusing to judge is always better than a false accusation here,
    // because this verdict can grey out the user's EQ.
    if (samplerate < 8000 || samplerate > 768000) {
        LOGI("Superpowered DSP probe skipped: implausible sample rate %u Hz.", samplerate);
        return true;
    }

    const unsigned int frames = 256;   // multiple of 4, >= 64, as the SDK recommends
    const unsigned int blocks = 16;    // let the filter's parameter smoothing settle before measuring
    const float probeHz = 1000.0f;
    const float amplitude = 0.25f;     // well clear of full scale, so a +12 dB boost cannot clip

    Superpowered::Filter probe(Superpowered::Filter::Parametric, samplerate);
    probe.frequency = probeHz;
    probe.octave = 1.0f;
    probe.decibel = 12.0f;
    probe.enabled = true;

    std::vector<float> in(frames * 2), out(frames * 2);
    const double step = 2.0 * M_PI * (double)probeHz / (double)samplerate;
    double phase = 0.0;
    bool processed = false;
    double rmsIn = 0.0, rmsOut = 0.0;

    for (unsigned int b = 0; b < blocks; ++b) {
        for (unsigned int i = 0; i < frames; ++i) {
            const float s = amplitude * (float)sin(phase);
            phase += step;
            in[i * 2] = s;
            in[i * 2 + 1] = s;
        }
        // Zero the output each block so a no-op process() (which leaves output untouched) reads as
        // silence rather than as whatever the previous block happened to leave behind.
        std::fill(out.begin(), out.end(), 0.0f);
        processed = probe.process(in.data(), out.data(), frames);

        if (b == blocks - 1) { // measure only the settled block
            for (unsigned int i = 0; i < frames * 2; ++i) {
                rmsIn += (double)in[i] * (double)in[i];
                rmsOut += (double)out[i] * (double)out[i];
            }
            rmsIn = sqrt(rmsIn / (double)(frames * 2));
            rmsOut = sqrt(rmsOut / (double)(frames * 2));
        }
    }

    if (!processed) {
        LOGE("Superpowered DSP probe: process() returned false - engine is not producing output.");
        return false;
    }
    if (!std::isfinite(rmsOut) || !std::isfinite(rmsIn) || rmsIn <= 0.0) {
        LOGE("Superpowered DSP probe: non-finite or empty signal - engine output is unusable.");
        return false;
    }
    const double ratio = rmsOut / rmsIn;
    if (ratio < 1.05) {
        LOGE("Superpowered DSP probe FAILED: +12 dB boost produced a level ratio of only %.3f "
             "(expected ~4.0). The engine is not processing audio - the license key is the likely cause.",
             ratio);
        return false;
    }
    LOGI("Superpowered DSP probe passed: level ratio %.3f (expected ~4.0).", ratio);
    return true;
}

// Native filter slots: 24-band EQ + AutoEQ bands.
#define NUM_EQ_FILTERS 64

/// RBJ cookbook biquad for one EQ band, normalized to a0 = 1: out = {b0, b1, b2, a1, a2}.
/// MUST stay identical to Kotlin EqResponse.coefficients — the EQ graph, the in-app verification and this
/// engine share one definition. typeCode: 0 peak, 1 low shelf, 2 high shelf, 3 low-pass, 4 high-pass.
/// Shelves take their slope from Q: S = clamp(Q / 1.414, 0.05, 1).
static void auraRbjCoefficients(double fs, float frequency, float gainDb, float qIn, int typeCode, float out[5]) {
    double q = qIn < 1e-4f ? 1e-4 : (double) qIn;
    double f0 = frequency;
    if (f0 < 1.0) f0 = 1.0;
    if (f0 > fs * 0.49) f0 = fs * 0.49;
    const double w0 = 2.0 * 3.14159265358979323846 * f0 / fs;
    const double sinW0 = std::sin(w0), cosW0 = std::cos(w0);
    double b0, b1, b2, a0, a1, a2;
    if (typeCode == 1 || typeCode == 2) {
        const double A = std::sqrt(std::pow(10.0, gainDb / 20.0));
        double S = q / 1.414;
        if (S < 0.05) S = 0.05; else if (S > 1.0) S = 1.0;
        const double alpha = sinW0 / 2.0 * std::sqrt((A + 1.0 / A) * (1.0 / S - 1.0) + 2.0);
        const double t = 2.0 * std::sqrt(A) * alpha;
        if (typeCode == 1) {
            b0 = A * ((A + 1) - (A - 1) * cosW0 + t);
            b1 = 2.0 * A * ((A - 1) - (A + 1) * cosW0);
            b2 = A * ((A + 1) - (A - 1) * cosW0 - t);
            a0 = (A + 1) + (A - 1) * cosW0 + t;
            a1 = -2.0 * ((A - 1) + (A + 1) * cosW0);
            a2 = (A + 1) + (A - 1) * cosW0 - t;
        } else {
            b0 = A * ((A + 1) + (A - 1) * cosW0 + t);
            b1 = -2.0 * A * ((A - 1) + (A + 1) * cosW0);
            b2 = A * ((A + 1) + (A - 1) * cosW0 - t);
            a0 = (A + 1) - (A - 1) * cosW0 + t;
            a1 = 2.0 * ((A - 1) - (A + 1) * cosW0);
            a2 = (A + 1) - (A - 1) * cosW0 - t;
        }
    } else if (typeCode == 3 || typeCode == 4) {
        const double alpha = sinW0 / (2.0 * q);
        const double k = (typeCode == 3) ? (1.0 - cosW0) : (1.0 + cosW0);
        b0 = k / 2.0;
        b1 = (typeCode == 3) ? k : -k;
        b2 = k / 2.0;
        a0 = 1.0 + alpha;
        a1 = -2.0 * cosW0;
        a2 = 1.0 - alpha;
    } else {
        const double A = std::pow(10.0, gainDb / 40.0);
        const double alpha = sinW0 / (2.0 * q);
        b0 = 1.0 + alpha * A;
        b1 = -2.0 * cosW0;
        b2 = 1.0 - alpha * A;
        a0 = 1.0 + alpha / A;
        a1 = -2.0 * cosW0;
        a2 = 1.0 - alpha / A;
    }
    out[0] = (float) (b0 / a0); out[1] = (float) (b1 / a0); out[2] = (float) (b2 / a0);
    out[3] = (float) (a1 / a0); out[4] = (float) (a2 / a0);
}

/// EQ band biquad (transposed direct form II, one state per channel) running the RBJ coefficients above.
/// Replaces Superpowered custom-coefficient filters for the EQ bands: with those the owner's phone played
/// silence. A non-finite state (never expected) resets the band instead of muting the whole chain.
struct AuraBiquad {
    float b0 = 1.0f, b1 = 0.0f, b2 = 0.0f, a1 = 0.0f, a2 = 0.0f;
    float z1[2] = {0.0f, 0.0f};
    float z2[2] = {0.0f, 0.0f};
    void set(const float c[5]) { b0 = c[0]; b1 = c[1]; b2 = c[2]; a1 = c[3]; a2 = c[4]; }
    void reset() { z1[0] = z1[1] = z2[0] = z2[1] = 0.0f; }
    void process(float* data, int frames, int channels) {
        const int chs = channels < 2 ? channels : 2;
        for (int ch = 0; ch < chs; ++ch) {
            float s1 = z1[ch], s2 = z2[ch];
            for (int i = 0; i < frames; ++i) {
                const int idx = i * channels + ch;
                const float x = data[idx];
                const float y = b0 * x + s1;
                s1 = b1 * x - a1 * y + s2;
                s2 = b2 * x - a2 * y;
                data[idx] = y;
            }
            if (!std::isfinite(s1) || !std::isfinite(s2)) { s1 = 0.0f; s2 = 0.0f; }
            z1[ch] = s1;
            z2[ch] = s2;
        }
    }
};

/// One EQ band's COMPLETE parameter set, as a plain value type.
/// Data only — it never references a Superpowered object, so it can be copied freely between threads.
struct BandParams {
    float frequency = 1000.0f;
    float decibel = 0.0f;
    float octave = 2.0f;
    float slope = 0.6f;
    float resonance = 0.1f; // resonant low/high-pass only: Superpowered resonance = Q / 10
    float q = 1.414f;       // raw band Q and type code: the audio thread derives RBJ coefficients from them
    int typeCode = 0;
    Superpowered::Filter::FilterType type = Superpowered::Filter::Parametric;
    bool enabled = false;
};

/// Everything the audio thread needs in order to configure the DSP for a block. Published as ONE
/// indivisible unit (see the triple buffer below), so the audio thread can never act on a mixture of
/// an old and a new configuration.
struct EqSnapshot {
    BandParams bands[NUM_EQ_FILTERS];
    float preampMultiplier = 1.0f;
    bool safeVolumeEnabled = false;
    float safeVolumeGain = 1.0f;
    bool tidalSimulationEnabled = false;
    /// Spatial stage (opt-in). Disabled by default so EQ-only playback stays bit-identical in this
    /// branch. Parameter layout is owned by Kotlin SpatialAudioProfile.toNativeParams().
    bool spatialEnabled = false;
    int spatialAlgorithm = 0; // 0 HRTF, 1 crossfeed, 2 speaker M/S
    float azL = 330.0f;
    float azR = 30.0f;
    float elL = 0.0f;
    float elR = 0.0f;
    float rearAzL = 250.0f;
    float rearAzR = 110.0f;
    float rearEl = 0.0f;
    float phantomGain = 0.0f;
    float phantomDelayMs = 12.0f;
    float reverbMix = 0.0f;
    float reverbWidth = 1.0f;
    float reverbDamp = 0.5f;
    float reverbRoomSize = 0.8f;
    float reverbPredelayMs = 0.0f;
    float reverbLowCutHz = 0.0f;
    bool sound2 = false;
    float inputVolume = 1.0f;
    float crossfeedAmt = 0.32f;
    float speakerWidth = 1.0f;
    /// MASTERING stage (owner directive 2026-09-13), each one a user toggle:
    ///  - compressorEnabled: gentle 2:1 "glue" compression after the EQ, before the limiter.
    ///  - ditherEnabled: TPDF dither + first-order noise shaping on the float -> 16-bit output.
    ///  - speakerBassProtect: sub-bass low shelf, set by Kotlin only while the phone speaker is the route.
    bool compressorEnabled = false;
    bool ditherEnabled = false;
    bool speakerBassProtect = false;
    /// Stereo width (Mid/Side): 1.0 = untouched, > 1 widens the sides, < 1 narrows. The centre (Mid) is kept.
    float stereoWidth = 1.0f;
    /// Bumped by writers ONLY when a band actually changed (setEqBand / disableAllBands). Lets the audio
    /// thread skip re-writing all 64 filters for a publication that only moved a scalar — most importantly
    /// the per-track Safe Volume update, which must not touch EQ coefficients at all.
    uint32_t bandsRevision = 0;
};

class SuperpoweredProcessor {
public:
    std::vector<Superpowered::Filter*> filters;
    AuraBiquad eqBiquads[NUM_EQ_FILTERS];
    bool eqBiquadOn[NUM_EQ_FILTERS] = {};
    Superpowered::Limiter* limiter = nullptr;
    Superpowered::Filter* deEsser = nullptr;
    Superpowered::Filter* deEsserDetector = nullptr;

    Superpowered::Filter* tidalLowShelf = nullptr;
    Superpowered::Filter* tidalMidParam = nullptr;
    Superpowered::Filter* tidalHighMidParam = nullptr;
    Superpowered::Filter* tidalDeEsser = nullptr;
    Superpowered::Filter* tidalHighShelf = nullptr;

    Superpowered::Spatializer* spatFrontL = nullptr;
    Superpowered::Spatializer* spatFrontR = nullptr;
    Superpowered::Spatializer* spatRearL = nullptr;
    Superpowered::Spatializer* spatRearR = nullptr;
    Superpowered::Reverb* spatialRoom = nullptr;

    // MASTERING stage objects + audio-thread-private dither state.
    Superpowered::Compressor* glueCompressor = nullptr;
    Superpowered::Filter* speakerBassShelf = nullptr;
    float ditherErr[2] = {0.0f, 0.0f};
    uint32_t ditherSeed = 0x9E3779B9u;

    float spInL[MAX_AUDIO_FRAMES];
    float spInR[MAX_AUDIO_FRAMES];
    float spOutL[MAX_AUDIO_FRAMES];
    float spOutR[MAX_AUDIO_FRAMES];
    float spDelayL[2048];
    float spDelayR[2048];
    int spDelayPos = 0;
    float xfLpL = 0.0f;
    float xfLpR = 0.0f;

    float conversionBuffer[MAX_BUFFER_SIZE];
    float deEsserBuffer[MAX_BUFFER_SIZE];

    unsigned int currentSamplerate = 44100;

    // ------------------------------------------------------------------------------------------
    // AUDIO-THREAD-OWNED STATE. Touched ONLY from processAudio. No other thread may read or write
    // it, so it needs no synchronization of any kind.
    // ------------------------------------------------------------------------------------------
    float currentDeEsserDb = 0.0f;
    // De-esser detector envelope (RMS follower over the sibilance band), for smoother, level-relative
    // detection than the old block-peak binary gate.
    float deEsserEnv = 0.0f;
    // TARGET gain (what Kotlin asked for, mirrored into activeSafeVolumeGain by the snapshot) and the
    // CURRENT gain actually being applied. They differ only while ramping — see the Safe Volume stage in
    // processAudio. Stepping straight to a new target is what makes a mid-song gain change audible as a
    // jump, and now that the gain can BOOST (not just attenuate) those steps can be +16 dB or more: the
    // sanctioned "real loudness arrived within 8 s" upgrade goes from the unknown-default attenuation
    // straight to a boosted value, and toggling Safe Volume on is an instant jump from unity. Ramping
    // makes every one of them inaudible.
    float safeVolumeGainCurrent = 1.0f;
    // The most recently APPLIED snapshot values (the audio thread's private working copy).
    float activePreampMultiplier = 1.0f;
    // Optional "Safe Volume" stage (user opt-in): a per-track loudness-normalization gain + the
    // limiter/soft-clip below, run even when the EQ is off so loud masters are brought toward a
    // reference instead of blasting at full native level.
    bool activeSafeVolumeEnabled = false;
    float activeSafeVolumeGain = 1.0f;
    bool activeTidalSimulationEnabled = false;
    bool activeSpatialEnabled = false;
    int activeSpatialAlgorithm = 0;
    float activeAzL = 330.0f;
    float activeAzR = 30.0f;
    float activeElL = 0.0f;
    float activeElR = 0.0f;
    float activeRearAzL = 250.0f;
    float activeRearAzR = 110.0f;
    float activeRearEl = 0.0f;
    float activePhantomGain = 0.0f;
    float activePhantomDelayMs = 12.0f;
    float activeReverbMix = 0.0f;
    float activeReverbWidth = 1.0f;
    float activeReverbDamp = 0.5f;
    float activeReverbRoomSize = 0.8f;
    float activeReverbPredelayMs = 0.0f;
    float activeReverbLowCutHz = 0.0f;
    bool activeSound2 = false;
    float activeInputVolume = 1.0f;
    float activeCrossfeedAmt = 0.32f;
    float activeSpeakerWidth = 1.0f;
    bool activeCompressorEnabled = false;
    bool activeDitherEnabled = false;
    bool activeSpeakerBassProtect = false;
    float activeStereoWidth = 1.0f;
    /// bandsRevision of the snapshot whose band parameters are currently loaded into the filter objects.
    uint32_t appliedBandsRevision = 0;

    // ------------------------------------------------------------------------------------------
    // LOCK-FREE PARAMETER PUBLICATION  (UI / service threads  ->  audio thread)
    //
    // The audio thread MUST NOT take a lock. A UI thread preempted by the scheduler while holding it
    // stalls the audio callback for as long as it takes to be scheduled again, which is a dropout —
    // and the UI hammers these setters at slider-drag rate, so the window is hit often.
    //
    // But the audio thread must ALSO never observe a half-updated coefficient set: a torn read of a
    // filter's parameters (new frequency against an old type, say) is an audible CLICK. That is what
    // the old mutex bought, and it may not be given up.
    //
    // Both are satisfied by a three-slot single-producer/single-consumer buffer:
    //
    //  * The three slot indices {0,1,2} are permanently partitioned between `writerSlot` (owned by
    //    writers), the index parked inside `readySlot`, and `audioSlot` (owned by the audio thread).
    //    Both handovers are a SINGLE atomic exchange, so an index can never be duplicated or lost.
    //    A writer therefore can never write the slot the audio thread is reading, and vice versa —
    //    with no lock between them and no possibility of the reader waiting.
    //
    //  * A writer fills its private slot with a COMPLETE set, then release-exchanges its index into
    //    `readySlot`. The audio thread acquire-loads `readySlot` and, only if the dirty bit is set,
    //    acquire-exchanges its own index in. That acquire synchronizes-with the writer's release, so
    //    every store into the slot happens-before the audio thread's reads of it. The audio thread
    //    sees the whole new set or the whole previous one — never a mixture.
    //
    // The release/acquire pairing is load-bearing, not decoration. Every shipped ABI is ARM
    // (armeabi-v7a / arm64-v8a), which is WEAKLY ORDERED: with a plain field, a `volatile`, or a
    // RELAXED atomic, both the compiler and the CPU are free to make the flag store visible before
    // the coefficient stores that precede it, and the audio thread would then apply a mixture of the
    // two sets — exactly the click this design exists to prevent.
    //
    // Cost on the audio thread: one acquire load per block, plus one exchange only on blocks where
    // parameters actually changed. No allocation, no waiting, no CAS retry loop — wait-free.
    // Writers still serialize among THEMSELVES on `writerMutex`; the audio thread never touches it.
    // ------------------------------------------------------------------------------------------
    static constexpr uint32_t kSlotMask = 0x3u;
    static constexpr uint32_t kDirtyFlag = 0x4u;

    EqSnapshot slots[3];
    // Own cache line: keeps the audio thread's per-block acquire load off any line a writer is dirtying.
    alignas(64) std::atomic<uint32_t> readySlot{0};

    int writerSlot = 1;      // owned by writers (only valid under writerMutex)
    int audioSlot = 2;       // owned by the audio thread
    EqSnapshot staging;      // writer-side accumulator (only valid under writerMutex)
    std::mutex writerMutex;  // writers only — the audio thread NEVER takes this
    int batchDepth = 0;      // >0 while a multi-call apply is being accumulated

    /// Publish `staging` as the new complete set. Caller MUST hold writerMutex.
    void publishLocked() {
        slots[writerSlot] = staging;
        const uint32_t prev =
                readySlot.exchange((uint32_t) writerSlot | kDirtyFlag, std::memory_order_acq_rel);
        writerSlot = (int) (prev & kSlotMask);
    }

    /// Publish unless we are inside a begin/endEqBatch pair. Caller MUST hold writerMutex.
    void publishIfNotBatchingLocked() {
        if (batchDepth == 0) publishLocked();
    }

    /// AUDIO THREAD ONLY. Picks up a newly published set, if any, and applies it to the DSP objects.
    ///
    /// Applying happens HERE, on the audio thread, rather than in the JNI setters, because
    /// SuperpoweredFilter.h:32 requires it: "Changing the filter type often involves changing other
    /// parameters as well. Therefore in a real-time context change the parameters and the type in the
    /// same thread with the process() call." Individual property writes are documented as safe from
    /// any thread (SuperpoweredFilter.h:49) but a type + companion-parameter change is NOT, and this
    /// EQ changes type (peak <-> shelf) together with slope/octave. Doing the whole apply on the
    /// process() thread satisfies the header's stronger requirement.
    void consumeAndApplySnapshot() {
        if ((readySlot.load(std::memory_order_acquire) & kDirtyFlag) == 0) return;
        const uint32_t prev = readySlot.exchange((uint32_t) audioSlot, std::memory_order_acq_rel);
        audioSlot = (int) (prev & kSlotMask);

        const EqSnapshot& s = slots[audioSlot];

        // Scalars are cheap and always refreshed.
        activePreampMultiplier = s.preampMultiplier;
        activeSafeVolumeEnabled = s.safeVolumeEnabled;
        activeSafeVolumeGain = s.safeVolumeGain;
        activeTidalSimulationEnabled = s.tidalSimulationEnabled;
        activeSpatialEnabled = s.spatialEnabled;
        activeSpatialAlgorithm = s.spatialAlgorithm;
        activeAzL = s.azL;
        activeAzR = s.azR;
        activeElL = s.elL;
        activeElR = s.elR;
        activeRearAzL = s.rearAzL;
        activeRearAzR = s.rearAzR;
        activeRearEl = s.rearEl;
        activePhantomGain = s.phantomGain;
        activePhantomDelayMs = s.phantomDelayMs;
        activeReverbMix = s.reverbMix;
        activeReverbWidth = s.reverbWidth;
        activeReverbDamp = s.reverbDamp;
        activeReverbRoomSize = s.reverbRoomSize;
        activeReverbPredelayMs = s.reverbPredelayMs;
        activeReverbLowCutHz = s.reverbLowCutHz;
        activeSound2 = s.sound2;
        activeInputVolume = s.inputVolume;
        activeCrossfeedAmt = s.crossfeedAmt;
        activeSpeakerWidth = s.speakerWidth;
        activeCompressorEnabled = s.compressorEnabled;
        activeDitherEnabled = s.ditherEnabled;
        activeSpeakerBassProtect = s.speakerBassProtect;
        activeStereoWidth = s.stereoWidth;
        applySpatializerProperties();

        // Filter objects are touched ONLY when a band genuinely changed. A publication caused purely by
        // Safe Volume (which happens on every track) therefore leaves the EQ's coefficients completely
        // alone, exactly as before this change.
        if (s.bandsRevision == appliedBandsRevision) return;
        appliedBandsRevision = s.bandsRevision;

        for (int i = 0; i < NUM_EQ_FILTERS; ++i) {
            const BandParams& b = s.bands[i];
            if (b.enabled) {
                // RBJ coefficients — the same formulas the EQ graph draws — into the band's own biquad.
                float c[5];
                auraRbjCoefficients((double) currentSamplerate, b.frequency, b.decibel, b.q, b.typeCode, c);
                if (!eqBiquadOn[i]) eqBiquads[i].reset();
                eqBiquads[i].set(c);
            }
            eqBiquadOn[i] = b.enabled;
            filters[i]->enabled = false; // the Superpowered filter objects no longer run the EQ bands
        }
    }

    static float clampf(float v, float lo, float hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    void configureOneSpatializer(Superpowered::Spatializer* sp, float azimuth, float elevation) {
        if (!sp) return;
        sp->samplerate = currentSamplerate;
        sp->azimuth = clampf(azimuth, 0.0f, 360.0f);
        sp->elevation = clampf(elevation, -90.0f, 90.0f);
        sp->reverbmix = 0.0f; // global Spatializer reverb is process-wide; we use a per-processor Reverb
        sp->occlusion = 0.0f;
        sp->sound2 = activeSound2;
        sp->inputVolume = clampf(activeInputVolume, 0.05f, 1.5f);
    }

    void applySpatializerProperties() {
        if (!activeSpatialEnabled || activeSpatialAlgorithm != 0) return;
        configureOneSpatializer(spatFrontL, activeAzL, activeElL);
        configureOneSpatializer(spatFrontR, activeAzR, activeElR);
        configureOneSpatializer(spatRearL, activeRearAzL, activeRearEl);
        configureOneSpatializer(spatRearR, activeRearAzR, activeRearEl);
        if (spatialRoom) {
            spatialRoom->samplerate = currentSamplerate;
            spatialRoom->width = clampf(activeReverbWidth, 0.0f, 1.0f);
            spatialRoom->damp = clampf(activeReverbDamp, 0.0f, 1.0f);
            spatialRoom->roomSize = clampf(activeReverbRoomSize, 0.0f, 1.0f);
            spatialRoom->predelayMs = clampf(activeReverbPredelayMs, 0.0f, 500.0f);
            spatialRoom->lowCutHz = clampf(activeReverbLowCutHz, 0.0f, 2000.0f);
            spatialRoom->mix = clampf(activeReverbMix, 0.0f, 0.45f);
            spatialRoom->enabled = activeReverbMix > 0.001f;
        }
    }

    void processCrossfeed(float* interleaved, int frames) {
        const float amt = clampf(activeCrossfeedAmt, 0.0f, 0.6f);
        const float dry = 1.0f - amt;
        const float a = 1.0f - expf(-2.0f * 3.14159265f * 700.0f / (float) currentSamplerate);
        for (int i = 0; i < frames; ++i) {
            float L = interleaved[i * 2];
            float R = interleaved[i * 2 + 1];
            xfLpL += a * (R - xfLpL);
            xfLpR += a * (L - xfLpR);
            interleaved[i * 2] = L * dry + xfLpL * amt;
            interleaved[i * 2 + 1] = R * dry + xfLpR * amt;
        }
    }

    void processSpeakerMs(float* interleaved, int frames) {
        const float width = clampf(activeSpeakerWidth, 0.85f, 1.45f);
        if (fabsf(width - 1.0f) < 0.001f) return;
        Superpowered::StereoToMidSide(interleaved, interleaved, (unsigned int) frames);
        for (int i = 0; i < frames; ++i) {
            interleaved[i * 2 + 1] *= width;
        }
        Superpowered::MidSideToStereo(interleaved, interleaved, (unsigned int) frames);
    }

    void processHrtfChunk(float* interleaved, int frames) {
        Superpowered::DeInterleave(interleaved, spInL, spInR, (unsigned int) frames);
        bool okL = spatFrontL && spatFrontL->process(spInL, spInL, spOutL, spOutR, (unsigned int) frames, false);
        bool okR = spatFrontR && spatFrontR->process(spInR, spInR, spOutL, spOutR, (unsigned int) frames, true);
        if (!okL && !okR) return;

        if (activePhantomGain > 0.001f && spatRearL && spatRearR) {
            const int delayLen = (int) clampf(activePhantomDelayMs * (float) currentSamplerate / 1000.0f, 1.0f, 2047.0f);
            for (int i = 0; i < frames; ++i) {
                int read = spDelayPos - delayLen;
                if (read < 0) read += 2048;
                float dL = spDelayL[read] * activePhantomGain;
                float dR = spDelayR[read] * activePhantomGain;
                spDelayL[spDelayPos] = spInL[i];
                spDelayR[spDelayPos] = spInR[i];
                spDelayPos = (spDelayPos + 1) & 2047;
                spInL[i] = dL;
                spInR[i] = dR;
            }
            spatRearL->process(spInL, spInL, spOutL, spOutR, (unsigned int) frames, true);
            spatRearR->process(spInR, spInR, spOutL, spOutR, (unsigned int) frames, true);
        }
        Superpowered::Interleave(spOutL, spOutR, interleaved, (unsigned int) frames);
        if (spatialRoom && activeReverbMix > 0.001f) {
            spatialRoom->process(interleaved, interleaved, (unsigned int) frames);
        }
    }

    void processSpatial(float* interleaved, int frames) {
        if (!activeSpatialEnabled || frames < 64) return;
        switch (activeSpatialAlgorithm) {
            case 1:
                processCrossfeed(interleaved, frames);
                return;
            case 2:
                processSpeakerMs(interleaved, frames);
                return;
            default:
                break;
        }
        int offset = 0;
        while (offset < frames) {
            int chunk = frames - offset;
            if (chunk > 8192) chunk = 8192;
            if (chunk < 64) break;
            processHrtfChunk(interleaved + offset * 2, chunk);
            offset += chunk;
        }
    }

    SuperpoweredProcessor(unsigned int samplerate) : currentSamplerate(samplerate) {
        // Allocate 64 filters to support 24-band EQ + AutoEQ bands
        for (int i = 0; i < NUM_EQ_FILTERS; ++i) {
            auto* filter = new Superpowered::Filter(Superpowered::Filter::Parametric, currentSamplerate);
            filter->enabled = false; // Disabled until configured
            filters.push_back(filter);
        }
        
        limiter = new Superpowered::Limiter(samplerate);
        limiter->ceilingDb = -0.3f; // true-peak safety ceiling (-0.3 dBFS) for Opus/AAC decoded masters
        // Threshold below the ceiling so the limiter gain-rides EARLIER and rounds peaks smoothly,
        // instead of the old thresholdDb=0 all-or-nothing catch right at full scale (which forced the
        // tanh soft-clip to mop up = harshness). -3 dB gives gentler, more transparent limiting.
        limiter->thresholdDb = -3.0f;
        limiter->releaseSec = 0.05f; // slightly longer release avoids pumping at the lower threshold
        limiter->enabled = true;

        deEsser = new Superpowered::Filter(Superpowered::Filter::Parametric, samplerate);
        deEsser->frequency = 6500.0f;
        deEsser->octave = 0.5f;
        deEsser->decibel = 0.0f; // Starts flat
        deEsser->enabled = true;

        deEsserDetector = new Superpowered::Filter(Superpowered::Filter::Bandlimited_Bandpass, samplerate);
        deEsserDetector->frequency = 6500.0f;
        deEsserDetector->octave = 1.0f;
        deEsserDetector->enabled = true;

        tidalLowShelf = new Superpowered::Filter(Superpowered::Filter::LowShelf, samplerate);
        tidalLowShelf->frequency = 60.0f; // Más bajo para dar profundidad sin barro (antes 85)
        tidalLowShelf->slope = 0.5f;
        tidalLowShelf->decibel = 1.0f; // Sutil golpe sub-grave
        tidalLowShelf->enabled = true;

        tidalMidParam = new Superpowered::Filter(Superpowered::Filter::Parametric, samplerate);
        tidalMidParam->frequency = 250.0f;
        tidalMidParam->octave = 1.0f;
        tidalMidParam->decibel = -0.5f; // Corte leve para quitar la sensación de caja/barro
        tidalMidParam->enabled = true;

        tidalHighMidParam = new Superpowered::Filter(Superpowered::Filter::Parametric, samplerate);
        tidalHighMidParam->frequency = 3000.0f;
        tidalHighMidParam->octave = 1.2f;
        tidalHighMidParam->decibel = 0.5f; // Leve presencia vocal para acercar las voces (antes -1.0)
        tidalHighMidParam->enabled = true;

        tidalDeEsser = new Superpowered::Filter(Superpowered::Filter::Parametric, samplerate);
        tidalDeEsser->frequency = 6500.0f;
        tidalDeEsser->octave = 0.5f;
        tidalDeEsser->decibel = 0.0f; // Desactivado (plano) para no quitar brillo
        tidalDeEsser->enabled = true;

        tidalHighShelf = new Superpowered::Filter(Superpowered::Filter::HighShelf, samplerate);
        tidalHighShelf->frequency = 12000.0f;
        tidalHighShelf->slope = 0.5f;
        tidalHighShelf->decibel = 1.5f; // Aire y separación estéreo (Premium)
        tidalHighShelf->enabled = true;

        spatFrontL = new Superpowered::Spatializer(samplerate);
        spatFrontR = new Superpowered::Spatializer(samplerate);
        spatRearL = new Superpowered::Spatializer(samplerate);
        spatRearR = new Superpowered::Spatializer(samplerate);
        spatialRoom = new Superpowered::Reverb(samplerate);
        spatialRoom->enabled = false;
        spatialRoom->mix = 0.0f;
        memset(spDelayL, 0, sizeof(spDelayL));
        memset(spDelayR, 0, sizeof(spDelayR));

        // GLUE COMPRESSOR — deliberately gentle: the goal is cohesion on 160 kbps Opus, not loudness.
        // 2:1 (the SDK rounds to 1.5/2/3/4/5/10), -12 dB threshold, 10 ms attack keeps transients,
        // 200 ms release avoids pumping; no makeup gain so the limiter headroom is unchanged.
        glueCompressor = new Superpowered::Compressor(samplerate);
        glueCompressor->ratio = 2.0f;
        glueCompressor->thresholdDb = -12.0f;
        glueCompressor->attackSec = 0.010f;
        glueCompressor->releaseSec = 0.200f;
        glueCompressor->inputGainDb = 0.0f;
        glueCompressor->outputGainDb = 0.0f;
        glueCompressor->wet = 1.0f;
        glueCompressor->hpCutOffHz = 1.0f;
        glueCompressor->enabled = true;

        // SPEAKER SUB-BASS PROTECTION — a phone speaker cannot reproduce ~31 Hz; boosting it only
        // excurses the membrane. A -5 dB low shelf at 55 Hz takes the sub-bass out of the speaker path
        // while leaving the audible bass untouched.
        speakerBassShelf = new Superpowered::Filter(Superpowered::Filter::LowShelf, samplerate);
        speakerBassShelf->frequency = 55.0f;
        speakerBassShelf->slope = 0.7f;
        speakerBassShelf->decibel = -5.0f;
        speakerBassShelf->enabled = true;
    }

    ~SuperpoweredProcessor() {
        for (auto* filter : filters) {
            delete filter;
        }
        filters.clear();
        
        if (limiter) { delete limiter; limiter = nullptr; }
        if (deEsser) { delete deEsser; deEsser = nullptr; }
        if (deEsserDetector) { delete deEsserDetector; deEsserDetector = nullptr; }
        if (tidalLowShelf) { delete tidalLowShelf; tidalLowShelf = nullptr; }
        if (tidalMidParam) { delete tidalMidParam; tidalMidParam = nullptr; }
        if (tidalHighMidParam) { delete tidalHighMidParam; tidalHighMidParam = nullptr; }
        if (tidalDeEsser) { delete tidalDeEsser; tidalDeEsser = nullptr; }
        if (tidalHighShelf) { delete tidalHighShelf; tidalHighShelf = nullptr; }
        if (spatFrontL) { delete spatFrontL; spatFrontL = nullptr; }
        if (spatFrontR) { delete spatFrontR; spatFrontR = nullptr; }
        if (spatRearL) { delete spatRearL; spatRearL = nullptr; }
        if (spatRearR) { delete spatRearR; spatRearR = nullptr; }
        if (spatialRoom) { delete spatialRoom; spatialRoom = nullptr; }
        if (glueCompressor) { delete glueCompressor; glueCompressor = nullptr; }
        if (speakerBassShelf) { delete speakerBassShelf; speakerBassShelf = nullptr; }
    }
};
#endif

extern "C" JNIEXPORT jlong JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_initSuperpowered(JNIEnv *env, jobject thiz, jstring license_key, jint samplerate) {
#if HAS_SUPERPOWERED
    {
        std::lock_guard<std::mutex> lock(globalInitMutex);
        if (!isSuperpoweredInitialized) {
            const char *key = env->GetStringUTFChars(license_key, 0);
            // NEVER log `key` (or any prefix/length of it) - it is a credential and the app log is
            // user-shareable. Only the pass/fail VERDICT below is ever recorded.
            Superpowered::Initialize(key);
            env->ReleaseStringUTFChars(license_key, key);
            isSuperpoweredInitialized = true;
            superpoweredEngineHealth =
                    probeSuperpoweredDsp((unsigned int) samplerate) ? SP_ENGINE_HEALTHY
                                                                    : SP_ENGINE_DEGRADED;
            LOGI("Superpowered initialized globally. Engine health: %s",
                 superpoweredEngineHealth == SP_ENGINE_HEALTHY ? "HEALTHY" : "DEGRADED");
        }
    }

    auto* processor = new SuperpoweredProcessor(samplerate);
    LOGI("Superpowered processor instantiated for sample rate %d Hz", samplerate);
    return reinterpret_cast<jlong>(processor);
#else
    LOGE("Superpowered SDK headers not found! Please add them to cpp/superpowered_sdk");
    return 0L;
#endif
}

/// Result of the one-shot DSP probe run at Initialize time. See SP_ENGINE_* above.
/// Returns SP_ENGINE_UNKNOWN if the SDK is absent or initSuperpowered has not run yet.
extern "C" JNIEXPORT jint JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_getEngineHealth(JNIEnv *env, jobject thiz) {
#if HAS_SUPERPOWERED
    std::lock_guard<std::mutex> lock(globalInitMutex);
    return (jint) superpoweredEngineHealth;
#else
    return (jint) 0;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setEqBand(JNIEnv *env, jobject thiz, jlong ptr, jint index, jfloat frequency, jfloat gainDb, jfloat Q, jint filterType) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;

    std::lock_guard<std::mutex> lock(processor->writerMutex);
    if (index >= 0 && index < NUM_EQ_FILTERS) {
        // Stage the band's parameters. They reach the DSP when the set is published (below, or at
        // endEqBatch) and are applied by the audio thread itself — see consumeAndApplySnapshot.
        BandParams& b = processor->staging.bands[index];
        b.frequency = frequency;
        b.decibel = gainDb;
        // filterType matches the Kotlin FilterType mapping: 1 = low shelf, 2 = high shelf, else parametric.
        // Shelves (SDK LowShelf/HighShelf) hold their gain out to DC / Nyquist instead of rolling off like a
        // peak — the correct choice for the lowest/highest graphic-EQ bands (fixes the "edges roll off oddly"
        // and makes the audio match the shelf curve drawn in the UI).
        if (filterType == 3 || filterType == 4) {
            // LPQ / HPQ from AutoEQ or imported parametric profiles. They used to fall through to the
            // parametric branch, so a low-pass cut sounded like a bell boost of 0 dB (i.e. nothing).
            b.type = filterType == 3 ? Superpowered::Filter::Resonant_Lowpass : Superpowered::Filter::Resonant_Highpass;
            float resonance = Q / 10.0f; // SDK: resonance = Q / 10, limit 0.01..1
            if (!(resonance >= 0.01f)) resonance = 0.01f; else if (resonance > 1.0f) resonance = 1.0f;
            b.resonance = resonance;
        } else if (filterType == 1 || filterType == 2) {
            b.type = filterType == 1 ? Superpowered::Filter::LowShelf : Superpowered::Filter::HighShelf;
            // Shelves honour the band's Q too (the manual graphic EQ exposes it): slope scales with Q
            // around the historic 0.6 at the default Q 1.414, so an untouched band sounds exactly as
            // before; a lower Q is a gentler shelf, a higher Q a steeper one. SDK limit: 0.001..1.
            float slope = 0.6f * (Q / 1.414f);
            if (!(slope >= 0.05f)) slope = 0.05f; else if (slope > 1.0f) slope = 1.0f;
            b.slope = slope;
        } else {
            b.type = Superpowered::Filter::Parametric;
            // Convert Q -> octave BANDWIDTH (the SDK's Parametric width unit). Q and octave are different
            // physical quantities; passing Q raw as `octave` made every band wider/more overlapping than
            // designed, and made a high-Q parametric notch (Q=10) turn into the WIDEST filter (octave clamped
            // to 5). BW_oct = (2/ln2)*asinh(1/(2Q)); clamp to the SDK's [0.05, 5].
            float oct = 2.0f;
            if (Q > 0.0001f) oct = (2.0f / logf(2.0f)) * asinhf(1.0f / (2.0f * Q));
            if (oct < 0.05f) oct = 0.05f; else if (oct > 5.0f) oct = 5.0f;
            b.octave = oct;
        }
        b.q = Q;
        b.typeCode = filterType;
        b.enabled = true;
        processor->staging.bandsRevision++;
    }
    processor->publishIfNotBatchingLocked();
#endif
}

/// Begin accumulating parameter changes WITHOUT publishing them. Every setter called until the
/// matching endEqBatch lands in the staging set only, so the whole profile reaches the audio thread
/// as ONE atomic publication.
///
/// This also removes a long-standing artifact: applyProfile used to issue disableAllBands + setPreamp
/// + N x setEqBand as separate locked calls, so the audio thread could run a block in the middle of
/// the sequence — most audibly right after disableAllBands, when every filter was off and that block
/// lost all EQ shaping (a dip on preset switch). Batched, no such intermediate state is ever visible.
/// Nesting is counted, and Kotlin calls endEqBatch from a finally block, so a throw mid-apply can
/// never leave the EQ permanently unpublished.
extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_beginEqBatch(JNIEnv *env, jobject thiz, jlong ptr) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;

    std::lock_guard<std::mutex> lock(processor->writerMutex);
    processor->batchDepth++;
#endif
}

/// Close a beginEqBatch and publish the accumulated set (when the outermost batch closes).
extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_endEqBatch(JNIEnv *env, jobject thiz, jlong ptr) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;

    std::lock_guard<std::mutex> lock(processor->writerMutex);
    if (processor->batchDepth > 0) processor->batchDepth--;
    if (processor->batchDepth == 0) processor->publishLocked();
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setPreamp(JNIEnv *env, jobject thiz, jlong ptr, jfloat preampDb) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;
    
    std::lock_guard<std::mutex> lock(processor->writerMutex);
    processor->staging.preampMultiplier = powf(10.0f, preampDb / 20.0f);
    processor->publishIfNotBatchingLocked();
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setSafeVolume(JNIEnv *env, jobject thiz, jlong ptr, jboolean enabled, jfloat gainLinear) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;

    std::lock_guard<std::mutex> lock(processor->writerMutex);
    processor->staging.safeVolumeEnabled = (enabled == JNI_TRUE);
    // Safe Volume levels in BOTH directions: attenuate loud masters AND bring quiet tracks up toward the
    // reference, which is what makes a library play at a consistent volume. This used to clamp to < 1.0,
    // silently discarding every boost — so the feature only ever turned things DOWN, and a quiet track
    // stayed quiet forever. (The Kotlin side computed the makeup correctly but handed it to a dead stub
    // processor, so nothing applied it either; both halves are fixed together.)
    //
    // Boosting is safe here because the limiter below runs for Safe Volume too (ceiling -1 dBFS,
    // threshold -3 dB) and catches the resulting peaks — that is the documented design. The ceiling
    // matches loudnessMakeupDb's +12 dB cap; anything beyond that is a bug upstream, not a louder track.
    // CLAMP, never fall back to unity: an out-of-range value must become a bounded gain, not a sudden jump
    // to 1.0 (that would be an audible level change if the cap upstream ever moves). NaN fails both
    // comparisons and lands on 1.0, which is the correct safe default.
    const float kMaxSafeVolumeGain = 4.0f; // +12 dB, matching loudnessMakeupDb's cap
    processor->staging.safeVolumeGain =
        (gainLinear > 0.0f) ? ((gainLinear > kMaxSafeVolumeGain) ? kMaxSafeVolumeGain : gainLinear) : 1.0f;
    processor->publishIfNotBatchingLocked();
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setSpatial(JNIEnv *env, jobject thiz, jlong ptr, jboolean enabled, jint algorithm, jfloatArray params) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;
    std::lock_guard<std::mutex> lock(processor->writerMutex);
    processor->staging.spatialEnabled = (enabled == JNI_TRUE);
    processor->staging.spatialAlgorithm = algorithm;
    if (params != nullptr) {
        const jsize n = env->GetArrayLength(params);
        if (n >= 19) {
            jfloat* p = env->GetFloatArrayElements(params, nullptr);
            if (p) {
                processor->staging.azL = p[0];
                processor->staging.azR = p[1];
                processor->staging.elL = p[2];
                processor->staging.elR = p[3];
                processor->staging.rearAzL = p[4];
                processor->staging.rearAzR = p[5];
                processor->staging.rearEl = p[6];
                processor->staging.phantomGain = p[7];
                processor->staging.phantomDelayMs = p[8];
                processor->staging.reverbMix = p[9];
                processor->staging.reverbWidth = p[10];
                processor->staging.reverbDamp = p[11];
                processor->staging.reverbRoomSize = p[12];
                processor->staging.reverbPredelayMs = p[13];
                processor->staging.reverbLowCutHz = p[14];
                processor->staging.sound2 = p[15] > 0.5f;
                processor->staging.inputVolume = p[16];
                processor->staging.crossfeedAmt = p[17];
                processor->staging.speakerWidth = p[18];
                env->ReleaseFloatArrayElements(params, p, JNI_ABORT);
            }
        }
    }
    processor->publishIfNotBatchingLocked();
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setMasteringOptions(JNIEnv *env, jobject thiz, jlong ptr, jboolean compressor, jboolean dither, jboolean speakerBassProtect) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;
    std::lock_guard<std::mutex> lock(processor->writerMutex);
    processor->staging.compressorEnabled = (compressor == JNI_TRUE);
    processor->staging.ditherEnabled = (dither == JNI_TRUE);
    processor->staging.speakerBassProtect = (speakerBassProtect == JNI_TRUE);
    processor->publishIfNotBatchingLocked();
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setStereoWidth(JNIEnv *env, jobject thiz, jlong ptr, jfloat width) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;
    std::lock_guard<std::mutex> lock(processor->writerMutex);
    float w = width;
    if (!(w >= 0.0f)) w = 1.0f; else if (w > 2.0f) w = 2.0f;
    processor->staging.stereoWidth = w;
    processor->publishIfNotBatchingLocked();
#endif
}

/// VERIFICATION (sine test): runs a sine at each test frequency through a private cascade built from
/// [bands] (4 floats per band: frequency, gain dB, Q, type code) with the SAME coefficient code the live
/// chain uses, and returns the measured gain in dB per frequency. Touches no live processor state.
extern "C" JNIEXPORT jfloatArray JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_measureEqResponse(JNIEnv *env, jobject thiz, jint samplerate, jfloatArray bands, jfloatArray freqs) {
    jsize nf = env->GetArrayLength(freqs);
    jfloatArray result = env->NewFloatArray(nf);
#if HAS_SUPERPOWERED
    jsize nb = env->GetArrayLength(bands) / 4;
    std::vector<float> bandData((size_t) nb * 4);
    std::vector<float> freqData((size_t) nf);
    if (nb > 0) env->GetFloatArrayRegion(bands, 0, nb * 4, bandData.data());
    env->GetFloatArrayRegion(freqs, 0, nf, freqData.data());
    const unsigned int sr = samplerate > 0 ? (unsigned int) samplerate : 48000u;
    std::vector<float> out((size_t) nf, 0.0f);
    const int block = 512;
    std::vector<float> buf((size_t) block * 2);
    for (jsize k = 0; k < nf; ++k) {
        std::vector<AuraBiquad> cascade((size_t) nb);
        for (jsize i = 0; i < nb; ++i) {
            float c[5];
            auraRbjCoefficients((double) sr, bandData[i * 4], bandData[i * 4 + 1], bandData[i * 4 + 2], (int) bandData[i * 4 + 3], c);
            cascade[(size_t) i].set(c);
        }
        const double w = 2.0 * 3.14159265358979323846 * freqData[k] / sr;
        const int settle = (int) (sr * 0.4f);
        const int total = settle + (int) (sr * 0.6f);
        double phase = 0.0, inEnergy = 0.0, outEnergy = 0.0;
        for (int pos = 0; pos < total; pos += block) {
            for (int i = 0; i < block; ++i) {
                const float x = 0.25f * (float) std::sin(phase);
                phase += w;
                buf[i * 2] = x;
                buf[i * 2 + 1] = x;
                if (pos + i >= settle) inEnergy += (double) x * x;
            }
            for (auto& bq : cascade) bq.process(buf.data(), block, 2);
            for (int i = 0; i < block; ++i) {
                if (pos + i >= settle) outEnergy += (double) buf[i * 2] * buf[i * 2];
            }
        }
        out[k] = (inEnergy > 0.0 && outEnergy > 0.0) ? (float) (10.0 * std::log10(outEnergy / inEnergy)) : -120.0f;
    }
    env->SetFloatArrayRegion(result, 0, nf, out.data());
#endif
    return result;
}

/// VERIFICATION (headroom): pink noise normalized to -0.1 dBFS peak -> preamp front gain -> EQ cascade ->
/// limiter (live settings) -> soft knee. Returns {peak in, peak after EQ, peak out} in dBFS.
extern "C" JNIEXPORT jfloatArray JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_measureHeadroom(JNIEnv *env, jobject thiz, jint samplerate, jfloatArray bands, jfloat preampDb) {
    jfloatArray result = env->NewFloatArray(3);
#if HAS_SUPERPOWERED
    jsize nb = env->GetArrayLength(bands) / 4;
    std::vector<float> bandData((size_t) nb * 4);
    if (nb > 0) env->GetFloatArrayRegion(bands, 0, nb * 4, bandData.data());
    const unsigned int sr = samplerate > 0 ? (unsigned int) samplerate : 48000u;
    const int frames = (int) sr * 4;
    std::vector<float> noise((size_t) frames * 2);
    uint32_t seed = 0x12345678u;
    double b[2][7] = {{0}};
    float maxAbs = 0.0f;
    for (int i = 0; i < frames; ++i) {
        for (int ch = 0; ch < 2; ++ch) {
            seed ^= seed << 13; seed ^= seed >> 17; seed ^= seed << 5;
            const double white = ((double) (seed & 0xFFFFFF) / 16777215.0) * 2.0 - 1.0;
            double* p = b[ch];
            p[0] = 0.99886 * p[0] + white * 0.0555179;
            p[1] = 0.99332 * p[1] + white * 0.0750759;
            p[2] = 0.96900 * p[2] + white * 0.1538520;
            p[3] = 0.86650 * p[3] + white * 0.3104856;
            p[4] = 0.55000 * p[4] + white * 0.5329522;
            p[5] = -0.7616 * p[5] - white * 0.0168980;
            const double pink = p[0] + p[1] + p[2] + p[3] + p[4] + p[5] + p[6] + white * 0.5362;
            p[6] = white * 0.115926;
            const float v = (float) pink;
            noise[(size_t) i * 2 + ch] = v;
            if (std::abs(v) > maxAbs) maxAbs = std::abs(v);
        }
    }
    const float target = (float) std::pow(10.0, -0.1 / 20.0);
    const float norm = maxAbs > 0.0f ? target / maxAbs : 1.0f;
    for (auto& v : noise) v *= norm;

    std::vector<AuraBiquad> cascade((size_t) nb);
    for (jsize i = 0; i < nb; ++i) {
        float c[5];
        auraRbjCoefficients((double) sr, bandData[i * 4], bandData[i * 4 + 1], bandData[i * 4 + 2], (int) bandData[i * 4 + 3], c);
        cascade[(size_t) i].set(c);
    }
    auto* limiter = new Superpowered::Limiter(sr);
    limiter->ceilingDb = -0.3f;
    limiter->thresholdDb = -3.0f;
    limiter->releaseSec = 0.05f;
    limiter->enabled = true;

    const float frontGain = (float) std::pow(10.0, preampDb / 20.0) * 0.7943f;
    const int block = 512;
    const int skip = (int) (sr * 0.5f); // let filters/limiter settle before measuring
    float peakIn = 0.0f, peakEq = 0.0f, peakOut = 0.0f;
    std::vector<float> buf((size_t) block * 2);
    for (int pos = 0; pos + block <= frames; pos += block) {
        for (int i = 0; i < block * 2; ++i) {
            const float x = noise[(size_t) pos * 2 + i];
            if (pos >= skip && std::abs(x) > peakIn) peakIn = std::abs(x);
            buf[(size_t) i] = x * frontGain;
        }
        for (auto& bq : cascade) bq.process(buf.data(), block, 2);
        for (int i = 0; i < block * 2; ++i) if (pos >= skip && std::abs(buf[(size_t) i]) > peakEq) peakEq = std::abs(buf[(size_t) i]);
        limiter->process(buf.data(), buf.data(), (unsigned int) block);
        for (int i = 0; i < block * 2; ++i) {
            float x = buf[(size_t) i];
            const float absX = std::abs(x);
            if (absX > 0.97f) {
                const float o = 0.97f + std::tanh(absX - 0.97f) * 0.03f;
                x = (x > 0) ? o : -o;
            }
            if (pos >= skip && std::abs(x) > peakOut) peakOut = std::abs(x);
        }
    }
    delete limiter;
    auto toDb = [](float v) { return v > 0.0f ? (float) (20.0 * std::log10(v)) : -120.0f; };
    float values[3] = {toDb(peakIn), toDb(peakEq), toDb(peakOut)};
    env->SetFloatArrayRegion(result, 0, 3, values);
#endif
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_disableAllBands(JNIEnv *env, jobject thiz, jlong ptr) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;
    
    std::lock_guard<std::mutex> lock(processor->writerMutex);
    for (int i = 0; i < NUM_EQ_FILTERS; ++i) {
        processor->staging.bands[i].enabled = false;
    }
    processor->staging.bandsRevision++;
    processor->publishIfNotBatchingLocked();
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_processAudio(JNIEnv *env, jobject thiz, jlong ptr, jobject input_buffer, jobject output_buffer, jint num_frames, jint encoding, jint channels, jboolean enabled) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    
    void* input = env->GetDirectBufferAddress(input_buffer);
    void* output = env->GetDirectBufferAddress(output_buffer);

    if (!input || !output || num_frames <= 0) return;

    // Wait-free parameter intake. One acquire load; the exchange + apply only run on blocks where the
    // UI actually published something new. Done BEFORE any decision that depends on EQ / Safe Volume
    // state so a freshly published set takes effect on this very block. No lock is taken here — see
    // the publication contract on SuperpoweredProcessor.
    if (processor) processor->consumeAndApplySnapshot();

    int requiredSize = num_frames * channels;
    if (requiredSize > MAX_BUFFER_SIZE) {
        // Oversized block: the fixed native scratch arrays (conversionBuffer/deEsserBuffer) can't hold
        // it, so the DSP chain can't run. Do NOT bare-return: media3 reuses the same output ByteBuffer
        // and the caller always flips/publishes it, so returning without writing republishes the
        // previous block's PCM (or uninitialized memory) as an audible click/burst. Emit a bit-exact
        // pass-through instead — this rare block is played unprocessed (no EQ/Safe Volume) but clean.
        // Same layout as the #else fallback below: 4 bytes/sample float (encoding==4) else 2 (16-bit),
        // for num_frames*channels interleaved samples. memcpy on already-mapped direct buffers is
        // allocation-free and safe on the audio thread.
        LOGE("Audio buffer size %d exceeds MAX_BUFFER_SIZE %d. Passing through unprocessed.", requiredSize, MAX_BUFFER_SIZE);
        int bytesPerSample = (encoding == 4) ? 4 : 2;
        memcpy(output, input, (size_t)requiredSize * bytesPerSample);
        return;
    }

    float* workBuffer = nullptr;

    if (encoding == 4) { // C.ENCODING_PCM_FLOAT -> FLOAT
        float* inFloat = (float*)input;
        float* outFloat = (float*)output;
        memcpy(outFloat, inFloat, requiredSize * sizeof(float));
        workBuffer = outFloat;
    } else { // C.ENCODING_PCM_16BIT -> 16BIT
        short* inShort = (short*)input;
        if (processor) {
            workBuffer = processor->conversionBuffer;
            Superpowered::ShortIntToFloat(inShort, workBuffer, num_frames, channels);
        } else {
            return;
        }
    }

    // Run the DSP block if the EQ is on, Safe Volume is on (or still ramping), OR spatial is on.
    // When ALL of those are off the block is skipped (pure float pass-through) so default playback
    // stays bit-perfect.
    bool runEq = (enabled == JNI_TRUE);
    // Also run while Safe Volume is ramping BACK to unity after being switched off — otherwise disabling it
    // would skip the chain outright and drop the gain in one step, the very jump the ramp exists to avoid.
    bool runSpatial = processor && processor->activeSpatialEnabled;
    bool runChain = runEq ||
        (processor && (processor->activeSafeVolumeEnabled || processor->safeVolumeGainCurrent != 1.0f)) ||
        runSpatial ||
        (processor && processor->activeTidalSimulationEnabled) ||
        (processor && (processor->activeCompressorEnabled || processor->activeSpeakerBassProtect ||
                       processor->activeStereoWidth != 1.0f));
    if (runChain && workBuffer && processor) {
        // NO LOCK HERE. Parameters were taken in above via consumeAndApplySnapshot, which is wait-free
        // and delivers a complete set or nothing at all. Everything read below is either that applied
        // set or audio-thread-private state.

        // FRONT gain = the EQ preamp ONLY. The limiter below (thresholdDb -3) catches its peaks, and a
        // positive preamp raises the body audibly.
        //
        // Safe Volume is deliberately NOT folded in here — it runs as its own stage AFTER the EQ bands
        // (see below). Putting it at the front compounds with the band gains, and the preamp
        // auto-headroom in CustomEqualizerAudioProcessor (which trims the preamp by the positive EQ boost
        // on the premise that the limiter absorbs ~1.5 dB transparently) has no knowledge of it — so a
        // quiet track's makeup, a preamp and a boost preset would stack straight into the limiter. That
        // is worst exactly on DYNAMIC masters (classical/jazz/hi-res): low integrated loudness means a
        // large makeup while peaks already sit near full scale, so the limiter would ride ~10 dB of gain
        // reduction on every transient — the "saturation / boxy / pumping" complaint on record.
        float frontGain = processor->activePreampMultiplier * 0.7943f; // -2.0 dB headroom
        if (frontGain != 1.0f) {
            for (int i = 0; i < num_frames * channels; ++i) {
                workBuffer[i] *= frontGain;
            }
        }

        if (runEq) {
            // Apply EQ bands (own TDF-II biquads with the graph's RBJ coefficients).
            for (int bi = 0; bi < NUM_EQ_FILTERS; ++bi) {
                if (processor->eqBiquadOn[bi]) processor->eqBiquads[bi].process(workBuffer, num_frames, channels);
            }

            // Dynamic De-Esser (only with EQ on — it exists to tame EQ-boosted sibilance). Improved
            // detection: RMS energy of the 6.5 kHz band through a one-pole envelope (fast attack / slow
            // release), with a PROPORTIONAL cut (up to -5 dB) instead of the old binary -4/0 peak gate —
            // less twitchy, less likely to audibly dull the top on loud bright material.
            if (processor->deEsser && processor->deEsserDetector) {
                memcpy(processor->deEsserBuffer, workBuffer, requiredSize * sizeof(float));
                if (channels == 1) processor->deEsserDetector->processMono(processor->deEsserBuffer, processor->deEsserBuffer, num_frames);
                else processor->deEsserDetector->process(processor->deEsserBuffer, processor->deEsserBuffer, num_frames);

                double sumSq = 0.0;
                for (int i = 0; i < requiredSize; ++i) {
                    float v = processor->deEsserBuffer[i];
                    sumSq += (double)v * v;
                }
                float rms = (requiredSize > 0) ? sqrtf((float)(sumSq / (double)requiredSize)) : 0.0f;
                if (rms > processor->deEsserEnv) processor->deEsserEnv += (rms - processor->deEsserEnv) * 0.5f;
                else processor->deEsserEnv += (rms - processor->deEsserEnv) * 0.1f;

                float t = (processor->deEsserEnv - 0.05f) / 0.15f; // 0 at ~-26 dBFS RMS, 1 at ~-14 dBFS
                if (t < 0.0f) t = 0.0f; else if (t > 1.0f) t = 1.0f;
                float targetDb = -5.0f * t;
                float diff = targetDb - processor->currentDeEsserDb;
                if (diff < 0) processor->currentDeEsserDb += diff * 0.5f; // fast attack
                else processor->currentDeEsserDb += diff * 0.1f;         // slow release
                processor->deEsser->decibel = processor->currentDeEsserDb;

                if (channels == 1) processor->deEsser->processMono(workBuffer, workBuffer, num_frames);
                else processor->deEsser->process(workBuffer, workBuffer, num_frames);
            }
        }

        // SAFE VOLUME — its own stage, AFTER the EQ bands and immediately BEFORE the limiter, so the limiter
        // sees the levelled signal and the EQ's preamp auto-headroom stays valid (nothing it doesn't know
        // about is added upstream of the bands).
        //
        // Levels in BOTH directions: attenuates loud masters and brings quiet ones up toward the reference,
        // which is what makes a library play at one consistent volume. The makeup side is capped at +3 dB
        // upstream (loudnessMakeupDb) — deliberately NOT the historical +12, which was validated against a
        // STATIC tanh knee that cannot pump, whereas the limiter below rides gain with a 0.1 s release.
        //
        // Applied through a RAMP, never as a step: the target can change mid-song (the sanctioned
        // real-loudness-arrived upgrade, or the user toggling Safe Volume), and an instantaneous multiplier
        // change is exactly the audible level jump this feature exists to avoid.
        if (processor->activeSafeVolumeEnabled || processor->safeVolumeGainCurrent != 1.0f) {
            float target = processor->activeSafeVolumeEnabled ? processor->activeSafeVolumeGain : 1.0f;
            // MONO: never amplify. The limiter below is stereo-only, so a boost here would have nothing but
            // the 0.95 tanh knee between it and audible distortion — and that knee is a hard clipper above
            // ~1.5x, not a limiter. ATTENUATION stays allowed (it cannot clip), so a loud mono master is
            // still tamed; only the makeup is withheld. Stereo — what this player is actually used for —
            // gets the full two-way levelling.
            //
            // NOTE this clamps ONLY the Safe Volume gain. An earlier version clamped the combined FRONT gain,
            // which silently swallowed the user's EQ preamp on mono (a +3 dB preamp became 0 dB) — a change
            // to EQ behaviour that was never in scope. Keeping the stages separate makes that impossible.
            if (channels != 2 && target > 1.0f) target = 1.0f;

            float cur = processor->safeVolumeGainCurrent;
            if (cur != target) {
                // ~300 ms one-pole glide at 48 kHz; per-BLOCK so the cost is one lerp, not one per sample.
                const float kGlide = 0.15f;
                cur += (target - cur) * kGlide;
                if (std::abs(target - cur) < 1e-4f) cur = target; // settle exactly, no dangling epsilon
                processor->safeVolumeGainCurrent = cur;
            }

            if (cur != 1.0f) {
                for (int i = 0; i < num_frames * channels; ++i) {
                    workBuffer[i] *= cur;
                }
            }
        }

        // STEREO WIDTH (M/S, user control): only the Side part is scaled, so voice and kick (Mid) stay put.
        if (processor->activeStereoWidth != 1.0f && channels == 2) {
            const float w = processor->activeStereoWidth;
            for (int i = 0; i < num_frames; ++i) {
                const float l = workBuffer[i * 2];
                const float r = workBuffer[i * 2 + 1];
                const float mid = (l + r) * 0.5f;
                const float side = (l - r) * 0.5f * w;
                workBuffer[i * 2] = mid + side;
                workBuffer[i * 2 + 1] = mid - side;
            }
        }

        // SPATIAL — after EQ / Safe Volume so the HRTF (or crossfeed / speaker width) sees the
        // levelled signal, and BEFORE the limiter so peaks from summing virtual sources are caught.
        // Stereo only: a Spatializer instance is a 3D point source feeding two ears.
        if (processor->activeSpatialEnabled && channels == 2 && workBuffer) {
            processor->processSpatial(workBuffer, num_frames);
        }

        // TIDAL SIMULATOR
        if (processor->activeTidalSimulationEnabled) {
            if (processor->tidalLowShelf) {
                if (channels == 1) processor->tidalLowShelf->processMono(workBuffer, workBuffer, num_frames);
                else processor->tidalLowShelf->process(workBuffer, workBuffer, num_frames);
            }
            if (processor->tidalMidParam) {
                if (channels == 1) processor->tidalMidParam->processMono(workBuffer, workBuffer, num_frames);
                else processor->tidalMidParam->process(workBuffer, workBuffer, num_frames);
            }
            if (processor->tidalHighMidParam) {
                if (channels == 1) processor->tidalHighMidParam->processMono(workBuffer, workBuffer, num_frames);
                else processor->tidalHighMidParam->process(workBuffer, workBuffer, num_frames);
            }
            if (processor->tidalDeEsser) {
                if (channels == 1) processor->tidalDeEsser->processMono(workBuffer, workBuffer, num_frames);
                else processor->tidalDeEsser->process(workBuffer, workBuffer, num_frames);
            }
            if (processor->tidalHighShelf) {
                if (channels == 1) processor->tidalHighShelf->processMono(workBuffer, workBuffer, num_frames);
                else processor->tidalHighShelf->process(workBuffer, workBuffer, num_frames);
            }
            
            // Headroom Gain: 1.0 (0 dB).
            // Al haber limpiado los filtros (máximo +1.5dB y cortes en zonas de barro),
            // la suma total no satura agresivamente. El limitador del Volumen Seguro
            // absorberá cualquier pico milimétrico natural sin perder el nivel general de volumen.
            float tidalGain = 1.0f; 
            for (int i = 0; i < num_frames * channels; ++i) {
                workBuffer[i] *= tidalGain;
            }
        }

        // Limiter (peak safety) — runs for EQ OR Safe Volume. Stereo only (Superpowered Limiter is stereo);
        // mono relies on the soft-clip knee below, which runs for every channel count.
        // SPEAKER SUB-BASS PROTECTION (user toggle; Kotlin arms it only while the phone speaker plays).
        if (processor->activeSpeakerBassProtect && processor->speakerBassShelf) {
            if (channels == 1) processor->speakerBassShelf->processMono(workBuffer, workBuffer, num_frames);
            else processor->speakerBassShelf->process(workBuffer, workBuffer, num_frames);
        }

        // GLUE COMPRESSOR (user toggle) — after EQ/spatial, immediately before the limiter.
        // The SDK compressor is stereo-interleaved only.
        if (processor->activeCompressorEnabled && processor->glueCompressor && channels == 2) {
            processor->glueCompressor->process(workBuffer, workBuffer, num_frames);
        }

        if (processor->limiter && processor->limiter->enabled && channels == 2) {
            processor->limiter->process(workBuffer, workBuffer, num_frames);
        } else if (processor->limiter && processor->limiter->enabled && channels == 1 &&
                   requiredSize * 2 <= MAX_BUFFER_SIZE) {
            // MONO LIMITING (2026-09-13): the SDK limiter is stereo-only, so mono tracks used to reach the
            // output with nothing but the tanh knee below. Run the mono signal as a dual-mono pair through
            // the same limiter (deEsserBuffer is free scratch at this point) and take one channel back.
            float* scratch = processor->deEsserBuffer;
            for (int i = 0; i < num_frames; ++i) {
                scratch[i * 2] = workBuffer[i];
                scratch[i * 2 + 1] = workBuffer[i];
            }
            processor->limiter->process(scratch, scratch, num_frames);
            for (int i = 0; i < num_frames; ++i) {
                workBuffer[i] = scratch[i * 2];
            }
        }

        // Soft-clip final safety net (rarely fires now that the limiter threshold is -3 dB).
        for (int i = 0; i < num_frames * channels; ++i) {
            float x = workBuffer[i];
            float absX = std::abs(x);
            // Knee sits ABOVE the limiter ceiling (0.966 = -0.3 dBFS) so it never shapes limited audio.
            if (absX > 0.97f) {
                float out = 0.97f + std::tanh(absX - 0.97f) * 0.03f;
                workBuffer[i] = (x > 0) ? out : -out;
            }
        }
    }

    if (encoding != 4 && workBuffer) {
        short* outShort = (short*)output;
        if (runChain && processor && processor->activeDitherEnabled) {
            // DITHER (user toggle, 2026-09-13): the 32-bit float DSP result is requantized to 16-bit here.
            // Plain truncation turns the quantization error into signal-correlated distortion on quiet
            // passages and reverb tails. TPDF dither (difference of two uniform randoms, +-1 LSB) decorrelates
            // it, and first-order error feedback pushes the remaining noise toward high frequencies where
            // the ear is least sensitive. Only when the chain actually processed — an untouched 16-bit
            // input round-trips exactly and needs no dither.
            const int total = num_frames * channels;
            uint32_t seed = processor->ditherSeed;
            for (int i = 0; i < total; ++i) {
                const int ch = (channels == 2) ? (i & 1) : 0;
                seed ^= seed << 13; seed ^= seed >> 17; seed ^= seed << 5;
                const float r1 = (float)(seed & 0xFFFF) / 65535.0f;
                seed ^= seed << 13; seed ^= seed >> 17; seed ^= seed << 5;
                const float r2 = (float)(seed & 0xFFFF) / 65535.0f;
                const float shaped = workBuffer[i] * 32767.0f - processor->ditherErr[ch];
                float q = std::round(shaped + (r1 - r2));
                if (q > 32767.0f) q = 32767.0f;
                else if (q < -32768.0f) q = -32768.0f;
                processor->ditherErr[ch] = q - shaped;
                outShort[i] = (short)q;
            }
            processor->ditherSeed = seed;
        } else {
            Superpowered::FloatToShortInt(workBuffer, outShort, num_frames, channels);
        }
    }
#else
    void* input = env->GetDirectBufferAddress(input_buffer);
    void* output = env->GetDirectBufferAddress(output_buffer);
    if (input && output && num_frames > 0) {
        int bytesPerSample = (encoding == 4) ? 4 : 2;
        memcpy(output, input, num_frames * channels * bytesPerSample);
    }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_releaseSuperpowered(JNIEnv *env, jobject thiz, jlong ptr) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (processor) {
        delete processor;
    }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setTidalSimulationEnabled(JNIEnv *env, jobject thiz, jlong ptr, jboolean enabled) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;

    std::lock_guard<std::mutex> lock(processor->writerMutex);
    processor->staging.tidalSimulationEnabled = (enabled == JNI_TRUE);
    processor->publishIfNotBatchingLocked();
#endif
}
