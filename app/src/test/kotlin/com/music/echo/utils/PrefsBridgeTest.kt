package iad1tya.echo.music.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HALLAZGO-060 (diferido de la fila 178, implementado 2026-08-27): el snapshot de proceso del
 * DataStore de ajustes (PrefsBridge) es lo que permite que las semillas de rememberPreference y
 * las lecturas calientes de Main no paguen el viaje runBlocking por el actor del DataStore.
 * Estos tests fijan su contrato: el snapshot es de reemplazo completo (una sola fuente publica),
 * peek es null para claves ausentes o antes de la primera emisión, y getNonBlocking SOLO cae a
 * la lectura bloqueante cuando el snapshot no tiene la clave — jamás cuando la tiene.
 *
 * NOTA: PrefsBridge es un singleton de proceso; cada test publica primero el snapshot exacto que
 * necesita y usa claves propias para no depender del orden de ejecución.
 */
class PrefsBridgeTest {

    private class FixedStore(private val prefs: Preferences) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(prefs)

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            throw UnsupportedOperationException("PrefsBridge nunca escribe")
        }
    }

    /** Si getNonBlocking tocara el store, este test fallaría con la excepción de data. */
    private class ThrowingStore : DataStore<Preferences> {
        override val data: Flow<Preferences>
            get() = throw AssertionError("getNonBlocking no debe tocar el store cuando el snapshot tiene la clave")

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            throw UnsupportedOperationException("PrefsBridge nunca escribe")
        }
    }

    @Test
    fun `snapshot publicado se lee con peek`() {
        val key = stringPreferencesKey("bridge_peek")
        PrefsBridge.publish(preferencesOf(key to "hola"))

        assertTrue(PrefsBridge.isReady)
        assertEquals("hola", PrefsBridge.peek(key))
    }

    @Test
    fun `snapshot es de reemplazo completo no de merge`() {
        val keyA = intPreferencesKey("bridge_replace_a")
        val keyB = intPreferencesKey("bridge_replace_b")
        PrefsBridge.publish(preferencesOf(keyA to 1))
        PrefsBridge.publish(preferencesOf(keyB to 2))

        assertNull(PrefsBridge.peek(keyA))
        assertEquals(2, PrefsBridge.peek(keyB))
    }

    @Test
    fun `clave nunca publicada devuelve null`() {
        PrefsBridge.publish(preferencesOf(stringPreferencesKey("bridge_other") to "x"))

        assertNull(PrefsBridge.peek(stringPreferencesKey("bridge_never_published")))
    }

    @Test
    fun `getNonBlocking lee del snapshot sin tocar el store`() {
        val key = stringPreferencesKey("bridge_nonblocking")
        PrefsBridge.publish(preferencesOf(key to "bridge"))

        assertEquals("bridge", ThrowingStore().getNonBlocking(key, "fallback"))
    }

    @Test
    fun `getNonBlocking cae al store cuando la clave no esta en el snapshot`() {
        val key = stringPreferencesKey("bridge_fallback")
        PrefsBridge.publish(preferencesOf(stringPreferencesKey("bridge_unrelated") to "x"))

        assertEquals("disco", FixedStore(preferencesOf(key to "disco")).getNonBlocking(key, "defecto"))
    }

    @Test
    fun `getNonBlocking devuelve el default si la clave no esta en ninguno`() {
        PrefsBridge.publish(preferencesOf(stringPreferencesKey("bridge_unrelated_2") to "x"))

        assertEquals("defecto", FixedStore(emptyPreferences()).getNonBlocking(stringPreferencesKey("bridge_missing"), "defecto"))
    }
}
