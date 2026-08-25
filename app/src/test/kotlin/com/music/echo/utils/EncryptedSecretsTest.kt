package iad1tya.echo.music.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import iad1tya.echo.music.constants.DeeplApiKey
import iad1tya.echo.music.constants.DiscordTokenKey
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.LastFMSessionKey
import iad1tya.echo.music.constants.ListenBrainzTokenKey
import iad1tya.echo.music.constants.OpenRouterApiKey
import iad1tya.echo.music.constants.SpotifyAccessTokenKey
import iad1tya.echo.music.constants.SpotifySpDcKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HALLAZGO-013 (2026-08-25): el cifrado de credenciales del DataStore no puede costar una sesión.
 * En la JVM de tests no hay Android Keystore, así que SecretCrypto trabaja en modo plano y estos
 * tests fijan el contrato de SEGURIDAD DE DATOS del envoltorio: nada se corrompe, nada se pierde,
 * un valor cifrado ilegible jamás se sobreescribe, y el logout borra de verdad. El roundtrip real
 * AES/GCM se verifica condicionalmente cuando sí hay Keystore (dispositivo/Robolectric con soporte).
 */
class EncryptedSecretsTest {

    private class InMemorySettingsStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        val stored = MutableStateFlow(initial)
        override val data: Flow<Preferences>
            get() = stored

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(stored.value)
            stored.value = next
            return next
        }
    }

    // El `to` infix de DataStore devuelve un Preferences.Pair opaco; este infix propio construye
    // pares Kotlin normales para el helper de abajo.
    private infix fun Preferences.Key<String>.mapsTo(value: String): Pair<Preferences.Key<String>, String> =
        Pair(this, value)

    private fun prefsOf(vararg pairs: Pair<Preferences.Key<String>, String>): Preferences {
        val mutable = emptyPreferences().toMutablePreferences()
        pairs.forEach { (key, value) -> mutable[key] = value }
        return mutable.toPreferences()
    }

    // ── contrato de datos del envoltorio ─────────────────────────────────────────────────────────

    @Test
    fun existingPlaintextSecretsAreStillReadable() = runBlocking {
        val store = EncryptedSecretsDataStore(
            InMemorySettingsStore(prefsOf(InnerTubeCookieKey mapsTo "SAPISID=abc123/xyz")),
        )
        assertEquals("SAPISID=abc123/xyz", store.data.first()[InnerTubeCookieKey])
    }

    @Test
    fun writesRoundTripThroughTheWrapper() = runBlocking {
        val raw = InMemorySettingsStore()
        val store = EncryptedSecretsDataStore(raw)

        store.updateData { it.toMutablePreferences().apply { this[SpotifySpDcKey] = "sp-dc-value" }.toPreferences() }

        assertEquals("sp-dc-value", store.data.first()[SpotifySpDcKey])
        // El valor que llega al disco: cifrado cuando hay Keystore, idéntico cuando no.
        val onDisk = raw.stored.value[SpotifySpDcKey]
        assertEquals("sp-dc-value", SecretCrypto.decryptOrNull(onDisk ?: ""))
    }

    @Test
    fun anUnrelatedWriteNeverTouchesAStoredSecret() = runBlocking {
        val themeKey = booleanPreferencesKey("darkMode")
        val raw = InMemorySettingsStore(prefsOf(InnerTubeCookieKey mapsTo "SAPISID=original"))
        val store = EncryptedSecretsDataStore(raw)

        store.updateData { it.toMutablePreferences().apply { this[themeKey] = true }.toPreferences() }

        // La migración aún no corrió: el secreto sigue EXACTAMENTE como estaba guardado.
        assertEquals("SAPISID=original", raw.stored.value[InnerTubeCookieKey])
        assertEquals(true, raw.stored.value[themeKey])
    }

    @Test
    fun aLockedCiphertextIsPresentedEmptyButNeverDestroyed() = runBlocking {
        val lockedValue = fakeEncryptedValueForTest("AAAA")
        val raw = InMemorySettingsStore(prefsOf(InnerTubeCookieKey mapsTo lockedValue))
        val store = EncryptedSecretsDataStore(raw)

        // Vista de lectura: sin Keystore legible el valor se presenta vacío (usuario "sin sesión").
        assertEquals("", store.data.first()[InnerTubeCookieKey])

        // Una escritura no relacionada NO debe sobreescribir el ciphertext: podría ser un fallo
        // transitorio del Keystore y destruirlo costaría el login para siempre.
        store.updateData {
            it.toMutablePreferences().apply { this[LastFMSessionKey] = "nueva-sesion" }.toPreferences()
        }
        assertEquals(lockedValue, raw.stored.value[InnerTubeCookieKey])
    }

    @Test
    fun writingOverALockedValueReplacesIt() = runBlocking {
        val lockedValue = fakeEncryptedValueForTest("AAAA")
        val raw = InMemorySettingsStore(prefsOf(InnerTubeCookieKey mapsTo lockedValue))
        val store = EncryptedSecretsDataStore(raw)

        // Re-login: el consumidor escribe un valor nuevo encima del bloqueado.
        store.updateData { it.toMutablePreferences().apply { this[InnerTubeCookieKey] = "SAPISID=nuevo" }.toPreferences() }

        assertEquals("SAPISID=nuevo", SecretCrypto.decryptOrNull(raw.stored.value[InnerTubeCookieKey] ?: ""))
    }

    @Test
    fun logoutRemovalReachesTheRawStore() = runBlocking {
        val raw = InMemorySettingsStore(prefsOf(InnerTubeCookieKey mapsTo "SAPISID=abc"))
        val store = EncryptedSecretsDataStore(raw)

        store.updateData { it.toMutablePreferences().apply { remove(InnerTubeCookieKey) }.toPreferences() }

        assertFalse(raw.stored.value.asMap().containsKey(InnerTubeCookieKey))
    }

    @Test
    fun migrationIsANoOpWithoutKeystore() = runBlocking {
        val raw = InMemorySettingsStore(prefsOf(InnerTubeCookieKey mapsTo "SAPISID=abc"))
        val store = EncryptedSecretsDataStore(raw)

        store.migrateSecrets()

        // Sin Keystore no hay con qué cifrar: el valor queda tal cual (disponibilidad primero).
        if (!SecretCrypto.isAvailable) {
            assertEquals("SAPISID=abc", raw.stored.value[InnerTubeCookieKey])
        } else {
            assertNotEquals("SAPISID=abc", raw.stored.value[InnerTubeCookieKey])
            assertEquals("SAPISID=abc", SecretCrypto.decryptOrNull(raw.stored.value[InnerTubeCookieKey] ?: ""))
        }
    }

    // ── contrato de SecretCrypto ──────────────────────────────────────────────────────────────────

    @Test
    fun encryptDecryptRoundtripWhenKeystoreExists() {
        val plain = "SAPISID=abc123; __Secure-3PAPISID=xyz"
        val stored = SecretCrypto.encrypt(plain)
        if (SecretCrypto.isAvailable) {
            assertNotEquals(plain, stored)
            assertTrue(SecretCrypto.isEncrypted(stored))
            assertEquals(plain, SecretCrypto.decryptOrNull(stored))
        } else {
            assertEquals(plain, stored)
        }
    }

    @Test
    fun emptyValuesAreNeverEncrypted() {
        // El logout por cadena vacía debe seguir viéndose vacío (isNullOrBlank) después del cifrado.
        assertEquals("", SecretCrypto.encrypt(""))
        assertEquals("", SecretCrypto.decryptOrNull(""))
    }

    @Test
    fun plaintextValuesPassThroughDecrypt() {
        assertEquals("valor-plano", SecretCrypto.decryptOrNull("valor-plano"))
    }

    @Test
    fun undecryptableCiphertextReturnsNull() {
        assertNull(SecretCrypto.decryptOrNull(fakeEncryptedValueForTest("AAAA")))
    }

    // ── el registro cubre el inventario de la auditoría (FASE 18, R8) ────────────────────────────

    @Test
    fun sensitiveRegistryCoversTheAuditInventory() {
        val names = sensitiveKeyNamesForTest()
        listOf(
            "innerTubeCookie",
            "dataSyncId",
            "accountName",
            "accountEmail",
            "accountChannelHandle",
            "spotify_sp_dc",
            "spotify_sp_key",
            "spotify_access_token",
            "lastfmSession",
            "lastfmUsername",
            "discordToken",
            "listenTogetherSessionToken",
            "listenBrainzToken",
            "openRouterApiKey",
            "deeplApiKey",
            "proxyPassword",
        ).forEach { name -> assertTrue("falta $name en el registro de sensibles", names.contains(name)) }

        // Las claves reales resuelven a estos nombres (guarda contra renombres silenciosos).
        assertEquals("innerTubeCookie", InnerTubeCookieKey.name)
        assertEquals("spotify_sp_dc", SpotifySpDcKey.name)
        assertEquals("spotify_access_token", SpotifyAccessTokenKey.name)
        assertEquals("discordToken", DiscordTokenKey.name)
        assertEquals("listenBrainzToken", ListenBrainzTokenKey.name)
        assertEquals("openRouterApiKey", OpenRouterApiKey.name)
        assertEquals("deeplApiKey", DeeplApiKey.name)
        assertEquals("lastfmSession", LastFMSessionKey.name)
    }
}
