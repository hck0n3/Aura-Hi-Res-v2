package iad1tya.echo.music.ui.newui

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iad1tya.echo.music.constants.HighPerformanceModeKey
import iad1tya.echo.music.utils.DeviceCapabilities
import iad1tya.echo.music.utils.DeviceTier
import iad1tya.echo.music.utils.rememberDeviceThrottle
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.delay

/**
 * Los colores de la portada, animados en toda la app.
 *
 * 🔴 Dueño (2026-10-07): *"no quiero que agarre el color más fuerte de la portada, quiero que agarre los
 * colores de la portada y estén animados en el app"*.
 *
 * Una sola fase global (0..1, un ciclo cada [PERIOD_MS]) que leen en su fase de DIBUJO todos los fondos
 * ([auraBloom]): cada color de la portada es un lóbulo que orbita despacio y "respira", así que los colores
 * se mueven y se mezclan por la pantalla en lugar de quedarse quietos.
 *
 * ## Contrato de calor y batería (AGENTS regla 7, HALLAZGO-055/060)
 *  · **Un solo reloj** para toda la app, a [TICK_MS] (15 fps), no a la tasa del panel: el movimiento es
 *    tan lento (~1,5 px por paso sobre degradados suaves) que más fotogramas no se verían, y en un panel
 *    LTPO deja que la pantalla baje su tasa de refresco.
 *  · **Solo invalida el DIBUJO** del fondo: nada se recompone. [auraBloom] aísla el contenido de la
 *    pantalla en su propia capa, así que cada paso vuelve a pintar solo los lóbulos (tres blits de una
 *    imagen pequeña ya rasterizada), no la pantalla entera.
 *  · **Se para** con la app en segundo plano o la pantalla apagada (`repeatOnLifecycle(STARTED)`), con el
 *    Modo rendimiento, con el móvil caliente ([rememberDeviceThrottle]), en equipos de gama baja, con el
 *    ahorro de batería del sistema y si el usuario quitó las animaciones del sistema. Parado, la fase se
 *    queda donde estaba: el fondo se ve igual de colorido, solo quieto.
 *  · Nada se muestrea: ni la pantalla ni la red.
 */
object AuraAmbientMotion {
    /** Fase del ciclo, 0..1. Léela SOLO en fase de dibujo (`onDrawBehind`), nunca en composición. */
    var phase by mutableFloatStateOf(0f)
        private set

    /** Duración de un ciclo completo de los lóbulos. */
    const val PERIOD_MS = 42_000L

    /** Paso del reloj: 15 fps. */
    const val TICK_MS = 66L

    /** Cada cuántos pasos se vuelve a mirar el ahorro de batería (~5 s): una llamada al sistema barata. */
    private const val POWER_CHECK_TICKS = 75

    internal fun advance(elapsedMs: Long) {
        phase = nextPhase(phase, elapsedMs)
    }

    /** Pura, para test: avanza la fase [elapsedMs] y la mantiene en 0..1. */
    fun nextPhase(current: Float, elapsedMs: Long): Float {
        val next = current + elapsedMs.toFloat() / PERIOD_MS
        return next - kotlin.math.floor(next)
    }

    /** Pura, para test: ¿puede moverse el fondo? */
    fun motionAllowed(
        highPerformanceMode: Boolean,
        deviceHot: Boolean,
        lowTier: Boolean,
        powerSave: Boolean,
        systemAnimationsOff: Boolean,
    ): Boolean = !(highPerformanceMode || deviceHot || lowTier || powerSave || systemAnimationsOff)

    internal fun systemAnimationsOff(context: Context): Boolean = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    internal fun powerSave(context: Context): Boolean = runCatching {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    }.getOrDefault(false)

    @Composable
    internal fun Ticker() {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val highPerformanceMode by rememberPreference(HighPerformanceModeKey, false)
        val deviceHot = rememberDeviceThrottle()
        val lowTier = remember { DeviceCapabilities.tier(context) == DeviceTier.LOW }

        LaunchedEffect(highPerformanceMode, deviceHot, lowTier, lifecycle) {
            if (highPerformanceMode || deviceHot || lowTier) return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var ticks = 0
                var still = !motionAllowed(
                    highPerformanceMode = false,
                    deviceHot = false,
                    lowTier = false,
                    powerSave = powerSave(context),
                    systemAnimationsOff = systemAnimationsOff(context),
                )
                var last = android.os.SystemClock.uptimeMillis()
                while (true) {
                    delay(TICK_MS)
                    val now = android.os.SystemClock.uptimeMillis()
                    if (++ticks >= POWER_CHECK_TICKS) {
                        ticks = 0
                        still = powerSave(context) || systemAnimationsOff(context)
                    }
                    // Clamp: after a long suspension the lobes continue from where they were, no jump.
                    if (!still) advance((now - last).coerceAtMost(TICK_MS * 3))
                    last = now
                }
            }
        }
    }
}

/**
 * Pon esto UNA vez en la raíz de la interfaz nueva (MainActivity, junto a [AuraPaletteSync]): es el único
 * reloj de los fondos animados de toda la app.
 */
@Composable
fun AuraAmbientMotionTicker() = AuraAmbientMotion.Ticker()
