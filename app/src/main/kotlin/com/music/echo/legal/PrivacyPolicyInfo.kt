package iad1tya.echo.music.legal

/**
 * The in-app privacy policy (registry row 774). The assets are byte-identical copies of PRIVACIDAD.md and
 * PRIVACY_POLICY.md at the repository root, which stay the canonical text: change the root files and copy them
 * here (PrivacyPolicyAssetTest fails while they differ).
 */
object PrivacyPolicyInfo {
    /** The primary (Spanish) version. */
    const val ASSET_ES = "legal/PRIVACIDAD.md"

    /** The English translation, shown for every other app language. */
    const val ASSET_EN = "legal/PRIVACY_POLICY.md"

    /** [language] is the ISO 639 code of the app's current locale (es-419, es-ES… all start with "es"). */
    fun assetFor(language: String): String = if (language.equals("es", ignoreCase = true)) ASSET_ES else ASSET_EN
}
