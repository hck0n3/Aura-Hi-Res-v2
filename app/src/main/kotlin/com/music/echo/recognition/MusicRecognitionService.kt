

package iad1tya.echo.music.recognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import com.music.shazamkit.Shazam
import com.music.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder


object MusicRecognitionService {
    
    
    private const val RECORDING_SAMPLE_RATE = 44100
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT


    // 🔴 IDENTIFICACIÓN PROGRESIVA (dueño, 2026-09-17: *"la manera en que YouTube Music
    // identifica una canción es más rápida que la función que ofrezco yo, y no solo es más rápida:
    // se siente más efectiva"*, y después *"mejóralo bajo tus recomendaciones, sin que sufra daños
    // pero que acelere la detección"*).
    //
    // Aquí vivían `RECORDING_DURATION_FIRST_MS = 10000` y `RECORDING_DURATION_RETRY_MS = 12000`:
    // grabar 10 s fijos SIN mandar nada, una consulta, y si no había coincidencia volver a grabar
    // 12 s DESDE CERO y consultar otra vez. 10 s en el mejor caso, 22 s y dos viajes en el peor, y
    // **un solo intento por captura** — un primer segundo con ruido condenaba la huella entera, que
    // es la mitad de por qué además de lenta se sentía menos certera.
    //
    // La agenda ahora está en [ProgressiveRecognition.CHECKPOINTS_MS] y su último punto son los
    // mismos 12 s: la ventana más larga que ya se sabía que funcionaba. No se le pide al micrófono
    // nada que no hiciera antes — solo se pregunta antes y más veces.
    
    private val _recognitionStatus = MutableStateFlow<RecognitionStatus>(RecognitionStatus.Ready)
    val recognitionStatus: StateFlow<RecognitionStatus> = _recognitionStatus.asStateFlow()

    // Prevents two concurrent mic sessions (e.g. the in-app Recognition screen + the widget service at once)
    // fighting over the microphone and this shared status flow.
    private val inProgress = java.util.concurrent.atomic.AtomicBoolean(false)

    // Job of the session currently holding the mic, so Cancel (or a retry) can actually abort it
    // instead of leaving a zombie ~10s recording that blocks every new attempt.
    @Volatile
    private var activeSessionJob: Job? = null

    // How long a new attempt waits for a cancelled old session to release the mic before giving up.
    private const val TAKEOVER_TIMEOUT_MS = 3_000L

    fun hasRecordPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** True while a session is recording/processing — lets the UI avoid clobbering a live session. */
    fun isInProgress(): Boolean = inProgress.get()

    /**
     * Actually cancels the in-flight recognition session (if any) and returns the status to Ready.
     * Unlike reset(), which only changes the UI state, this releases the microphone so an immediate
     * retry works instead of being swallowed by the in-progress guard.
     *
     * Pass a [context] to also stop [RecognitionForegroundService]: cancelling only the session
     * would leave the mic FGS and its ongoing "Listening…" notification orphaned (the FGS also
     * self-terminates when it observes Ready, but every cancel path should kill it directly too).
     */
    fun cancel(context: Context? = null) {
        activeSessionJob?.cancel()
        _recognitionStatus.value = RecognitionStatus.Ready
        context?.stopService(Intent(context, RecognitionForegroundService::class.java))
    }

    @SuppressLint("MissingPermission")
    suspend fun recognize(context: Context): RecognitionStatus = withContext(Dispatchers.IO) {
        if (!hasRecordPermission(context)) {
            return@withContext RecognitionStatus.Error("Microphone permission not granted")
        }
        if (!inProgress.compareAndSet(false, true)) {
            // A previous session still holds the mic (e.g. a zombie recording after a Cancel that only
            // reset the UI). Don't silently swallow this attempt: cancel the old session and take over.
            activeSessionJob?.cancel()
            val takeoverDeadline = System.currentTimeMillis() + TAKEOVER_TIMEOUT_MS
            while (!inProgress.compareAndSet(false, true)) {
                if (System.currentTimeMillis() >= takeoverDeadline) {
                    return@withContext RecognitionStatus.Error("Recognition is busy. Please try again.")
                }
                delay(50)
            }
        }
        activeSessionJob = coroutineContext[Job]

        _recognitionStatus.value = RecognitionStatus.Listening

        try {
            val result = runProgressiveRecognition(context)
            _recognitionStatus.value = result
            result
        } catch (e: CancellationException) {
            // Cancelled by the user (or a new attempt taking over) — cancel() already set the status
            // to Ready; don't overwrite it with a scary error. Rethrow to finish cancellation cleanly.
            throw e
        } catch (e: Exception) {
            // Surface the exception type so a systematic failure (e.g. a network/TLS error) is diagnosable.
            _recognitionStatus.value = RecognitionStatus.Error("${e.javaClass.simpleName}: ${e.message ?: "Recognition failed"}")
            _recognitionStatus.value
        } finally {
            inProgress.set(false)
        }
    }
    
    /**
     * UNA grabación, VARIAS consultas — la agenda vive en [ProgressiveRecognition].
     *
     * El micrófono arranca una sola vez y **no se detiene mientras una consulta viaja**: esa es la
     * mitad que hace que esto sea rápido de verdad. Cuando la respuesta de los 3 s vuelve (medio
     * segundo, un segundo), la ventana de los 5 s ya está grabada y se puede preguntar en el acto, en
     * vez de empezar a grabar otros cinco segundos desde cero.
     *
     * Grabar y consultar son coroutines distintas a propósito. Hacer la consulta DENTRO del bucle de
     * lectura del `AudioRecord` habría dejado el micrófono sin leerse durante todo el viaje de red, y
     * su búfer interno es pequeño: se habría desbordado y esos cientos de milisegundos de audio
     * perdidos salen justo en medio de la ventana siguiente. La grabación escribe en un búfer
     * compartido y las consultas leen instantáneas recortadas al punto que les toca.
     *
     * Qué resultado gana:
     *  - la PRIMERA coincidencia corta todo (se cancela el micrófono y se devuelve);
     *  - un "no encontrado" intermedio **no** es definitivo: solo lo es el del último punto;
     *  - un ERROR de red intermedio tampoco corta — se guarda y se sigue, porque el punto siguiente
     *    puede tener mejor suerte. Pero se guarda **con preferencia sobre un "no encontrado"**: si
     *    todas las consultas murieron por red, decirle "no se encontró la canción" sería mentira y
     *    encima le haría culpar a la música en vez de a su conexión.
     */
    private suspend fun runProgressiveRecognition(context: Context): RecognitionStatus = coroutineScope {
        val capture = RecordingBuffer()
        val recorder = launch(Dispatchers.IO) {
            try {
                recordInto(context, capture, ProgressiveRecognition.MAX_RECORDING_MS)
            } catch (e: CancellationException) {
                // Una coincidencia temprana cancela al grabador a propósito: eso no es un fallo de
                // captura y NO debe quedar registrado como tal (marcarlo haría que el consumidor
                // lanzara la cancelación como si el micrófono se hubiera roto).
                throw e
            } catch (e: Exception) {
                capture.fail(e)
            } finally {
                capture.finish()
            }
        }

        var lastNoMatch: RecognitionStatus.NoMatch? = null
        var lastError: RecognitionStatus.Error? = null
        try {
            for (checkpointMs in ProgressiveRecognition.CHECKPOINTS_MS) {
                // Esperar a que haya audio suficiente para ESTE punto (o a que la grabación acabe).
                while (
                    !ProgressiveRecognition.readyToQuery(
                        bufferedBytes = capture.size(),
                        checkpointMs = checkpointMs,
                        sampleRate = RECORDING_SAMPLE_RATE,
                        recordingFinished = capture.isFinished(),
                    )
                ) {
                    currentCoroutineContext().ensureActive()
                    capture.failure()?.let { throw it }
                    if (capture.isFinished()) break
                    delay(BUFFER_POLL_MS)
                }
                currentCoroutineContext().ensureActive()
                capture.failure()?.let { throw it }

                val bytes = capture.snapshot(
                    ProgressiveRecognition.bytesFor(checkpointMs, RECORDING_SAMPLE_RATE),
                )
                if (bytes.size < ProgressiveRecognition.MIN_QUERYABLE_BYTES) {
                    // Sin audio utilizable y la grabación ya terminó: el micrófono no entregó nada.
                    if (capture.isFinished()) break else continue
                }

                // El indicador solo pasa a "procesando" en el ÚLTIMO punto. En los intermedios el
                // micrófono sigue abierto de verdad, así que decir "escuchando" es lo que está pasando
                // — y alternar entre los dos estados cuatro veces sería un parpadeo sin información.
                if (ProgressiveRecognition.isFinal(checkpointMs)) {
                    _recognitionStatus.value = RecognitionStatus.Processing
                }

                when (val outcome = queryWindow(bytes)) {
                    is RecognitionStatus.Success -> {
                        recorder.cancel()
                        return@coroutineScope outcome
                    }
                    is RecognitionStatus.NoMatch -> lastNoMatch = outcome
                    is RecognitionStatus.Error -> lastError = outcome
                    else -> Unit
                }
                if (capture.isFinished() && capture.size() <= bytes.size) break
            }
        } finally {
            recorder.cancel()
        }

        // Un error de red pesa más que un "no encontrado": ver el KDoc.
        lastError
            ?: lastNoMatch
            ?: RecognitionStatus.Error("No se pudo capturar audio del micrófono")
    }

    /** Cada cuánto se mira si el búfer ya llegó al punto siguiente. */
    private const val BUFFER_POLL_MS = 60L

    /**
     * Búfer compartido entre la coroutine que graba y la que consulta.
     *
     * `synchronized` y no un `Channel`: el consumidor no quiere el flujo, quiere una FOTO de todo lo
     * grabado hasta ahora, y eso es exactamente lo que un canal no da. El coste es un candado por
     * lectura del micrófono, que ya venía haciendo E/S mucho más cara que eso.
     */
    private class RecordingBuffer {
        private val lock = Any()
        private val stream = ByteArrayOutputStream()
        @Volatile private var finished = false
        @Volatile private var error: Throwable? = null

        fun write(data: ByteArray, length: Int) = synchronized(lock) { stream.write(data, 0, length) }
        fun size(): Int = synchronized(lock) { stream.size() }
        fun finish() { finished = true }
        fun isFinished(): Boolean = finished
        fun fail(t: Throwable) { error = t }
        fun failure(): Throwable? = error

        /** Los primeros [maxBytes] grabados; todo lo que haya si aún no llega. Siempre par. */
        fun snapshot(maxBytes: Int): ByteArray = synchronized(lock) {
            val all = stream.toByteArray()
            val take = minOf(all.size, maxBytes).let { it - (it % 2) }
            if (take == all.size) all else all.copyOf(take)
        }
    }

    /** Remuestrea, genera la huella y pregunta. Devuelve un estado TERMINAL, nunca lanza por un no-match. */
    private suspend fun queryWindow(audioData: ByteArray): RecognitionStatus {
        val decodedAudio = DecodedAudio(
            data = audioData,
            channelCount = 1,
            sampleRate = RECORDING_SAMPLE_RATE,
            pcmEncoding = AUDIO_FORMAT,
        )
        val resampledAudio = AudioResampler.resample(decodedAudio, VibraSignature.REQUIRED_SAMPLE_RATE)
            .getOrElse { error -> return RecognitionStatus.Error("Failed to resample audio: ${error.message}") }

        if (resampledAudio.channelCount != 1 ||
            resampledAudio.sampleRate != VibraSignature.REQUIRED_SAMPLE_RATE ||
            resampledAudio.pcmEncoding != AudioFormat.ENCODING_PCM_16BIT ||
            ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN ||
            resampledAudio.data.isEmpty() ||
            resampledAudio.data.size % 2 != 0
        ) {
            // `require` habría lanzado y matado la sesión entera por una ventana mala; aquí una ventana
            // que no cuadra solo se salta y el punto siguiente sigue teniendo su oportunidad.
            return RecognitionStatus.Error("Invalid audio format for fingerprint generation")
        }

        val signature = try {
            VibraSignature.fromI16(resampledAudio.data)
        } catch (e: Exception) {
            return RecognitionStatus.Error("Failed to generate fingerprint: ${e.message}")
        }

        val sampleDurationMs = (resampledAudio.data.size / 2) * 1000L / VibraSignature.REQUIRED_SAMPLE_RATE

        return Shazam.recognizeWithFallback(signature, sampleDurationMs).fold(
            onSuccess = { RecognitionStatus.Success(it) },
            onFailure = { error ->
                val message = error.message ?: "Unknown error"
                if (message.contains("No match", ignoreCase = true)) {
                    RecognitionStatus.NoMatch("No se encontraron coincidencias. Prueba de nuevo con audio más claro.")
                } else {
                    RecognitionStatus.Error(message)
                }
            },
        )
    }

    @SuppressLint("MissingPermission")
    /**
     * Graba hasta [durationMs] escribiendo en [sink] **según va leyendo**, no al final.
     *
     * Antes devolvía el `ByteArray` entero al terminar, que es lo que obligaba a esperar los diez
     * segundos completos antes de poder preguntar nada. Escribir en un búfer compartido es lo que
     * permite que las consultas de [runProgressiveRecognition] vayan saliendo mientras el micrófono
     * sigue abierto. La captura en sí — fuente sin procesar, AGC y supresor de ruido desactivados,
     * 44.1 kHz mono 16-bit — es **idéntica**: esa parte estaba bien y tocarla habría sido cambiar la
     * calidad de la huella, que es justo lo que pidió que no sufriera.
     */
    private suspend fun recordInto(context: Context, sink: RecordingBuffer, durationMs: Long): Unit = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(
            RECORDING_SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        // Cheap devices can return ERROR(-1)/ERROR_BAD_VALUE(-2) for this format combo; a negative size would
        // crash AudioRecord/ByteArray. Fail with a clear message instead of an obscure crash.
        if (bufferSize <= 0) {
            throw IllegalStateException("Micrófono no compatible con 44.1kHz mono 16-bit (code $bufferSize)")
        }

        // CAPTURE-SOURCE CASCADE: AudioSource.MIC applies AGC + noise suppression tuned for SPEECH, which
        // smears the musical spectrum the Shazam fingerprint relies on. Prefer the cleanest source the
        // device offers, falling back only if construction fails.
        val audioRecord = createCleanAudioRecord(context, bufferSize)

        // Belt-and-suspenders: even on VOICE_RECOGNITION/UNPROCESSED, some OEMs attach AGC/NS to the
        // capture session. Disable both if present — we want RAW music, not voice-cleaned audio. The
        // effect objects are kept alive for the whole capture (releasing them can revert the change).
        val agc = disableAutomaticGainControl(audioRecord.audioSessionId)
        val ns = disableNoiseSuppressor(audioRecord.audioSessionId)

        val buffer = ByteArray(bufferSize)
        val startTime = System.currentTimeMillis()

        try {
            audioRecord.startRecording()

            while (System.currentTimeMillis() - startTime < durationMs && isActive) {
                val bytesRead = audioRecord.read(buffer, 0, bufferSize)
                if (bytesRead > 0) {
                    sink.write(buffer, bytesRead)
                } else if (bytesRead < 0) {
                    // AudioRecord.ERROR_* — stop instead of spinning uselessly on empty reads.
                    break
                }
            }
        } finally {
            runCatching { audioRecord.stop() }
            audioRecord.release()
            runCatching { agc?.release() }
            runCatching { ns?.release() }
        }
    }

    /**
     * Build an AudioRecord from the cleanest available capture source, in cascade:
     *   1. UNPROCESSED — only when the device HARDWARE declares support (no DSP at all; cleanest).
     *   2. VOICE_RECOGNITION — framework guarantees no AGC/NS on this path (unlike MIC).
     *   3. MIC — always available last resort (may apply voice DSP on some OEMs).
     * Returns an INITIALIZED record, or throws if every source failed to construct/initialize.
     */
    @SuppressLint("MissingPermission")
    private fun createCleanAudioRecord(context: Context, bufferSize: Int): AudioRecord {
        val sources = buildList {
            if (deviceSupportsUnprocessed(context)) add(MediaRecorder.AudioSource.UNPROCESSED)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.MIC)
        }.distinct()

        var lastError: Exception? = null
        for (source in sources) {
            try {
                val record = AudioRecord(
                    source,
                    RECORDING_SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) return record
                record.release()
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError
            ?: IllegalStateException("No se pudo inicializar el micrófono (¿lo está usando otra app?)")
    }

    /** True if the device declares hardware support for the UNPROCESSED (no-DSP) capture source. */
    private fun deviceSupportsUnprocessed(context: Context): Boolean = try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
    } catch (e: Exception) {
        false
    }

    /** Best-effort: attach + disable AGC on the capture session; keep the handle alive to hold it off. */
    private fun disableAutomaticGainControl(sessionId: Int): AutomaticGainControl? = try {
        if (AutomaticGainControl.isAvailable()) {
            // AudioEffect.setEnabled() returns an int status (not void), so Kotlin exposes no writable
            // `enabled` property — call the method explicitly.
            AutomaticGainControl.create(sessionId)?.apply { setEnabled(false) }
        } else null
    } catch (e: Exception) {
        null
    }

    /** Best-effort: attach + disable noise suppression on the capture session; keep the handle alive. */
    private fun disableNoiseSuppressor(sessionId: Int): NoiseSuppressor? = try {
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(sessionId)?.apply { setEnabled(false) }
        } else null
    } catch (e: Exception) {
        null
    }

    fun reset() {
        _recognitionStatus.value = RecognitionStatus.Ready
    }
}
