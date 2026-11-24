package iad1tya.echo.music.migration

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aura.migration.source.amazon.AmazonMusicSession
import com.aura.migration.source.amazon.AmazonMusicWeb
import com.aura.migration.source.amazon.AmazonSessionStore
import timber.log.Timber

/**
 * The Amazon Music session vault: the allowlisted renewal cookies plus the minted GraphQL headers,
 * nothing else (see [AmazonMusicSession]). Same storage as [TidalTokenStore] — EncryptedSharedPreferences
 * in its own file — because these cookies grant access to the user's Amazon account: they never go into
 * plain DataStore or prefs, and are never logged (AGENTS rule 4). All calls are expected OFF Main (first
 * touch builds the Keystore-backed prefs).
 */
class EncryptedAmazonSessionStore(private val context: Context) : AmazonSessionStore {

    private val prefs: SharedPreferences by lazy { createPrefs() }

    private fun createPrefs(): SharedPreferences {
        fun build(): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
        return try {
            build()
        } catch (e: Exception) {
            // Unreadable keyset (backup restored onto another phone: the Keystore key never left the
            // original one). A session is re-obtainable with one sign-in, so empty THIS file and rebuild.
            Timber.tag(TAG).w("Encrypted prefs unreadable (%s), resetting the Amazon session", e.javaClass.simpleName)
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().commit()
            build()
        }
    }

    override fun load(): AmazonMusicSession? = AmazonMusicSession.fromJson(prefs.getString(KEY_SESSION, null))

    override fun save(session: AmazonMusicSession) {
        prefs.edit().putString(KEY_SESSION, session.toJson()).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val TAG = "AmazonMusicSession"
        const val FILE = "amazon_music_session"
        const val KEY_SESSION = "session"
    }
}

/**
 * Removes every cookie the login WebView holds for Amazon's Music marketplaces and their parent domains,
 * and nothing else (the shared jar also serves other logins). The WebView's jar is plain storage on
 * disk, so once the minimal session is in the encrypted vault — or the user left without connecting —
 * the full Amazon cookie set must not stay there. Must run on the main thread.
 */
fun expireAmazonWebCookies() {
    val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
    for (host in AmazonMusicWeb.MUSIC_HOSTS) {
        val parent = host.removePrefix("music.")
        for (url in listOf("https://$host/", "https://www.$parent/")) {
            val names = manager.getCookie(url).orEmpty().split(';')
                .mapNotNull { part -> part.substringBefore('=', "").trim().takeIf { it.isNotEmpty() } }
                .toSet()
            for (name in names) {
                val target = url.removeSuffix("/").removePrefix("https://")
                manager.setCookie(url, "$name=; Max-Age=0; Path=/")
                manager.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=.$parent")
                manager.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=.$target")
            }
        }
    }
    manager.flush()
}
