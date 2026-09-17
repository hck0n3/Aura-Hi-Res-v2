package iad1tya.echo.music.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Punto 4 del dueño (2026-09-17): "modo offline automático".
 *
 * Lo que estos casos fijan, por orden de importancia:
 *  · el interruptor manual manda aunque haya red (es suyo, y persistente);
 *  · con red y sin manual NUNCA se esconde nada, ni con el automático encendido;
 *  · el cartel distingue quién nos trajo aquí, porque el "Desactivar" solo sirve en el manual;
 *  · el retardo existe para no parpadear en un túnel corto, y volver es inmediato.
 */
class AutoOfflinePolicyTest {

    @Test
    fun `manual switch wins even with network`() {
        assertTrue(AutoOfflinePolicy.offline(manual = true, auto = false, online = true))
        assertTrue(AutoOfflinePolicy.offline(manual = true, auto = true, online = true))
    }

    @Test
    fun `with network and no manual switch nothing is hidden`() {
        assertFalse(AutoOfflinePolicy.offline(manual = false, auto = true, online = true))
        assertFalse(AutoOfflinePolicy.offline(manual = false, auto = false, online = true))
    }

    @Test
    fun `no network with auto on goes offline`() {
        assertTrue(AutoOfflinePolicy.offline(manual = false, auto = true, online = false))
    }

    @Test
    fun `no network with auto off stays online`() {
        // Quien lo apaga a mano pide ver la pantalla de error de siempre, no la biblioteca local.
        assertFalse(AutoOfflinePolicy.offline(manual = false, auto = false, online = false))
    }

    @Test
    fun `automatic flag is only true when auto put us here`() {
        assertTrue(AutoOfflinePolicy.automatic(manual = false, auto = true, online = false))
        // Con el manual encendido el cartel es el de siempre, aunque además falte la red.
        assertFalse(AutoOfflinePolicy.automatic(manual = true, auto = true, online = false))
        assertFalse(AutoOfflinePolicy.automatic(manual = false, auto = true, online = true))
        assertFalse(AutoOfflinePolicy.automatic(manual = false, auto = false, online = false))
    }

    @Test
    fun `offline is only declared after the debounce`() {
        assertFalse(AutoOfflinePolicy.shouldDeclareOffline(0))
        assertFalse(AutoOfflinePolicy.shouldDeclareOffline(AutoOfflinePolicy.OFFLINE_DEBOUNCE_MS - 1))
        assertTrue(AutoOfflinePolicy.shouldDeclareOffline(AutoOfflinePolicy.OFFLINE_DEBOUNCE_MS))
        assertTrue(AutoOfflinePolicy.shouldDeclareOffline(60_000))
    }

    @Test
    fun `debounce is long enough for a lift ride and short enough to not be a hang`() {
        assertTrue(AutoOfflinePolicy.OFFLINE_DEBOUNCE_MS >= 1_000)
        assertTrue(AutoOfflinePolicy.OFFLINE_DEBOUNCE_MS <= 10_000)
    }
}
