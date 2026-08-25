package iad1tya.echo.music.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import iad1tya.echo.music.constants.AccountChannelHandleKey
import iad1tya.echo.music.constants.AccountEmailKey
import iad1tya.echo.music.constants.AccountNameKey
import iad1tya.echo.music.constants.DataSyncIdKey
import iad1tya.echo.music.constants.DeeplApiKey
import iad1tya.echo.music.constants.DiscordTokenKey
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.LastFMSessionKey
import iad1tya.echo.music.constants.LastFMUsernameKey
import iad1tya.echo.music.constants.ListenBrainzTokenKey
import iad1tya.echo.music.constants.ListenTogetherSessionTokenKey
import iad1tya.echo.music.constants.OpenRouterApiKey
import iad1tya.echo.music.constants.ProxyPasswordKey
import iad1tya.echo.music.constants.SpotifyAccessTokenKey
import iad1tya.echo.music.constants.SpotifySpDcKey
import iad1tya.echo.music.constants.SpotifySpKeyKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * HALLAZGO-013 (2026-08-25): credenciales en texto plano en el DataStore.
 *
 * Cifrado AES-256-GCM respaldado por el Android Keystore para los valores sensibles del DataStore
 * de ajustes. La clave NUNCA sale del hardware (Keystore), así que el cifrado es por-dispositivo:
 * eso encaja exactamente con la política existente — `settings.preferences_pb` ya no viaja en el
 * backup de Google ni en el backup in-app (BackupRestoreViewModel lo excluye a propósito, e
 * InstallOrigin.kt documenta que la cookie "no debe salir jamás del dispositivo"), así que no hay
 * camino de migración entre dispositivos que este cambio pueda romper.
 *
 * Regla de disponibilidad primero: si el Keystore no existe o falla (emulador, JVM de tests,
 * dispositivo roto), TODO se degrada a texto plano — la app jamás pierde una sesión por culpa del
 * cifrado. Nunca se sobreescribe un valor cifrado que no se pudo descifrar: se presenta vacío,
 * pero el ciphertext original se conserva en disco.
 */
object SecretCrypto {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "aura_echo_secrets_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    // Prefijo con NUL: ninguna credencial real (cookies base64, tokens hex) empieza así.
    private const val ENCRYPTED_PREFIX = "\u0000ENC1:"

    private val keystoreReady: Boolean by lazy {
        try {
            getOrCreateSecretKey()
            true
        } catch (t: Throwable) {
            // Sin Keystore (tests JVM, emulador sin soporte, dispositivo dañado): modo plano.
            // Solo el nombre de la excepción — nada del usuario llega a este log.
            timber.log.Timber.tag("EncryptedSecrets").w(
                "Keystore unavailable, secrets stay plaintext: ${t.javaClass.simpleName}"
            )
            false
        }
    }

    val isAvailable: Boolean
        get() = keystoreReady

    fun isEncrypted(value: String): Boolean = value.startsWith(ENCRYPTED_PREFIX)

    /** Cifra si hay Keystore disponible; si no, devuelve el valor sin tocar (modo plano). */
    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty() || !keystoreReady) return plaintext
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val payload = cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(payload)
        } catch (t: Throwable) {
            plaintext
        }
    }

    /**
     * Devuelve el valor en claro. Los valores SIN prefijo se devuelven tal cual (migración
     * pendiente: eran plaintext antes de HALLAZGO-013). Devuelve null solo cuando el valor ESTÁ
     * cifrado y no se pudo descifrar (Keystore reiniciado, backup restaurado sin su clave...).
     */
    fun decryptOrNull(stored: String): String? {
        if (stored.isEmpty() || !isEncrypted(stored)) return stored
        return try {
            val payload = Base64.getDecoder().decode(stored.substring(ENCRYPTED_PREFIX.length))
            check(payload.size > GCM_IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_BITS, payload, 0, GCM_IV_BYTES),
            )
            String(cipher.doFinal(payload, GCM_IV_BYTES, payload.size - GCM_IV_BYTES), Charsets.UTF_8)
        } catch (t: Throwable) {
            null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}

/**
 * Registro de las claves sensibles del DataStore (inventario de la FASE 18, línea R8). Solo estas
 * claves se cifran; las ~340 restantes (orden, vistas, flags) no llevan secretos y se quedan como
 * están para no arriesgar nada.
 */
private val SENSITIVE_KEYS: List<Preferences.Key<String>> =
    listOf(
        // La sesión de Google: de ella dependen el login y la reproducción con cuenta.
        InnerTubeCookieKey,
        DataSyncIdKey,
        AccountNameKey,
        AccountEmailKey,
        AccountChannelHandleKey,
        // Spotify (importación de biblioteca).
        SpotifySpDcKey,
        SpotifySpKeyKey,
        SpotifyAccessTokenKey,
        // Last.fm (scrobbling).
        LastFMSessionKey,
        LastFMUsernameKey,
        // Resto de credenciales opt-in.
        DiscordTokenKey,
        ListenTogetherSessionTokenKey,
        ListenBrainzTokenKey,
        OpenRouterApiKey,
        DeeplApiKey,
        ProxyPasswordKey,
    )

private val SENSITIVE_KEY_NAMES: Set<String> = SENSITIVE_KEYS.map { it.name }.toHashSet()

/**
 * Envoltorio transparente: los ~560 sitios que leen/escriben `context.dataStore` NO cambian. En el
 * camino de LECTURA descifra las claves sensibles; en el de ESCRITURA las cifra antes de tocar el
 * disco. El archivo `settings.preferences_pb` queda con ciphertext; la vista en memoria es la de
 * siempre.
 */
class EncryptedSecretsDataStore(
    private val delegate: DataStore<Preferences>,
) : DataStore<Preferences> {

    override val data: Flow<Preferences> = delegate.data.map { it.decryptedView(mutableSetOf()) }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val locked = mutableSetOf<String>()
        val updated =
            delegate.updateData { stored ->
                val view = stored.decryptedView(locked)
                val transformed = transform(view)
                transformed.reencrypted(stored, locked)
            }
        return updated.decryptedView(mutableSetOf())
    }

    /** Migración one-time (HALLAZGO-013): cifra los valores plaintext que ya estaban guardados. */
    suspend fun migrateSecrets() {
        if (!SecretCrypto.isAvailable) return
        val pending =
            delegate.data.first().let { stored ->
                SENSITIVE_KEYS.filter { key ->
                    stored[key]?.let { it.isNotEmpty() && !SecretCrypto.isEncrypted(it) } == true
                }
            }
        if (pending.isEmpty()) return
        delegate.updateData { current ->
            val mutable = current.toMutablePreferences()
            for (key in pending) {
                val value = current[key]
                if (value != null && value.isNotEmpty() && !SecretCrypto.isEncrypted(value)) {
                    mutable[key] = SecretCrypto.encrypt(value)
                }
            }
            mutable.toPreferences()
        }
    }

    /**
     * Vista descifrada para los consumidores. Un valor cifrado que NO se puede descifrar
     * (Keystore reiniciado, datos movidos sin su clave) se presenta vacío — el usuario ve "sin
     * sesión" y puede volver a iniciarla — pero el ciphertext se conserva: [reencrypted] nunca lo
     * sobreescribe mientras el consumidor no escriba algo nuevo encima.
     */
    private fun Preferences.decryptedView(locked: MutableSet<String>): Preferences {
        val mutable = this.toMutablePreferences()
        for (key in SENSITIVE_KEYS) {
            val storedValue = this[key] ?: continue
            val decrypted = SecretCrypto.decryptOrNull(storedValue)
            if (decrypted == null) {
                locked += key.name
                mutable[key] = ""
            } else if (decrypted != storedValue) {
                mutable[key] = decrypted
            }
        }
        return mutable.toPreferences()
    }

    /**
     * Vuelve a cifrar las claves sensibles tras el transform. Un valor bloqueado que el transform
     * no tocó (sigue vacío, como se presentó) conserva su ciphertext original: jamás se destruye
     * algo que no pudimos leer.
     */
    private fun Preferences.reencrypted(
        stored: Preferences,
        locked: Set<String>,
    ): Preferences {
        val mutable = this.toMutablePreferences()
        for (key in SENSITIVE_KEYS) {
            val newValue = this[key] ?: continue
            val storedValue = stored[key]
            val toStore =
                if (key.name in locked && newValue.isEmpty() && storedValue != null) {
                    storedValue
                } else {
                    SecretCrypto.encrypt(newValue)
                }
            mutable[key] = toStore
        }
        return mutable.toPreferences()
    }
}

/** Punto de entrada de la migración para App.onCreate (best-effort, nunca lanza). */
suspend fun Context.migrateEncryptedSecrets() {
    (dataStore as? EncryptedSecretsDataStore)?.migrateSecrets()
}

/** Acceso de solo-verificación para tests: qué claves se consideran sensibles. */
internal fun sensitiveKeyNamesForTest(): Set<String> = SENSITIVE_KEY_NAMES

/** Solo tests: fabrica un valor con pinta de cifrado para ejercitar el camino "bloqueado". */
internal fun fakeEncryptedValueForTest(body: String): String = "\u0000ENC1:$body"
