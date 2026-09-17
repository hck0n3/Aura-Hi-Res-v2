package iad1tya.echo.music.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compensar lo que tarda el sonido en salir por el altavoz.
 *
 * 🔴 PETICIÓN DEL DUEÑO (2026-09-17): *"la compensación de latencia Bluetooth sigue pendiente" →
 * "bueno soluciona esto también"*.
 *
 * El caso que da sentido a todo el diseño es el último: **los dos por Bluetooth**. Mi primer intento
 * compensaba solo en el invitado, y ahí sobrecorregía 180 ms y dejaba la sala PEOR que sin
 * compensar. La regla que sí funciona es que cada extremo reste lo suyo — el anfitrión publica lo
 * que oye, el invitado compara lo que oye — y eso hay que fijarlo, porque el fallo no da ningún
 * error: solo suena mal.
 */
class OutputLatencyTest {

    @Test
    fun `cada tipo de salida tiene su estimacion`() {
        // Las claves las construye EqDeviceProfileStore; esta función se decide por el PREFIJO para
        // poder comprobarse sin un dispositivo.
        assertEquals(OutputLatency.BLUETOOTH_MS, OutputLatency.defaultMsFor("bt:JBL Flip 6"))
        assertEquals(OutputLatency.WIRED_MS, OutputLatency.defaultMsFor("wired"))
        assertEquals(OutputLatency.USB_MS, OutputLatency.defaultMsFor("usb"))
        assertEquals(OutputLatency.SPEAKER_MS, OutputLatency.defaultMsFor("__phone__"))
    }

    @Test
    fun `una salida desconocida no inventa retardo`() {
        // Ante la duda, cero: compensar de más desincroniza tanto como no compensar.
        assertEquals(0, OutputLatency.defaultMsFor("algo_que_no_existe"))
        assertEquals(0, OutputLatency.defaultMsFor(""))
    }

    @Test
    fun `el bluetooth es el unico que compensa de verdad`() {
        // Si esto deja de ser cierto, o sobra la compensación o falta: el Bluetooth es el único
        // camino con un búfer de cientos de milisegundos.
        assertTrue(OutputLatency.BLUETOOTH_MS >= 100)
        assertTrue(OutputLatency.SPEAKER_MS < 50 && OutputLatency.WIRED_MS < 50)
    }

    // ── el error de sincronía, que es donde el signo importa ──────────────────────────────────

    @Test
    fun `sin retardo el error es la diferencia de posiciones`() {
        assertEquals(300L, OutputLatency.syncErrorMs(10_300L, 10_000L, 0))
        assertEquals(-300L, OutputLatency.syncErrorMs(9_700L, 10_000L, 0))
    }

    @Test
    fun `con bluetooth mi decodificador tiene que ir POR DELANTE`() {
        // Lo que oigo va 180 ms detrás de mi decodificador, así que para oír a la vez que el
        // anfitrión tengo que decodificar 180 ms por delante. Estar en su misma posición significa
        // que voy ATRASADO y tengo que acelerar → error negativo.
        val error = OutputLatency.syncErrorMs(10_000L, 10_000L, OutputLatency.BLUETOOTH_MS)
        assertEquals(-OutputLatency.BLUETOOTH_MS.toLong(), error)
        assertTrue("misma posición con BT significa ir atrasado, no sincronizado", error < 0)

        // Y 180 ms por delante es justamente el punto de equilibrio.
        assertEquals(
            0L,
            OutputLatency.syncErrorMs(10_000L + OutputLatency.BLUETOOTH_MS, 10_000L, OutputLatency.BLUETOOTH_MS),
        )
    }

    @Test
    fun `los dos por bluetooth no sobrecorrigen`() {
        // ESTE es el caso que hundió el primer diseño. El anfitrión publica lo que OYE, o sea su
        // posición menos su propio retardo; el invitado resta el suyo. Con el mismo retardo en los
        // dos, la diferencia se cancela y no hay nada que corregir — que es la verdad física.
        val hostDecoder = 10_000L
        val hostPublishes = hostDecoder - OutputLatency.BLUETOOTH_MS // lo que el anfitrión oye
        val guestDecoder = 10_000L                                    // el invitado va igual
        val error = OutputLatency.syncErrorMs(guestDecoder, hostPublishes, OutputLatency.BLUETOOTH_MS)
        assertEquals("con los dos por Bluetooth no hay nada que corregir", 0L, error)
    }

    @Test
    fun `anfitrion por bluetooth e invitado por altavoz`() {
        // El invitado tiene que quedarse ATRÁS: el anfitrión oye su música con 180 ms de retraso.
        val hostPublishes = 10_000L - OutputLatency.BLUETOOTH_MS
        val error = OutputLatency.syncErrorMs(10_000L, hostPublishes, OutputLatency.SPEAKER_MS)
        assertEquals(OutputLatency.BLUETOOTH_MS.toLong(), error)
        assertTrue("el invitado por altavoz va adelantado y tiene que frenar", error > 0)
    }
}
