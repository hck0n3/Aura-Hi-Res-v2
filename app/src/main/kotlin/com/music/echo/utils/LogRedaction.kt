package iad1tya.echo.music.utils

/**
 * Strips credentials out of any text on its way into a log the USER can send us.
 *
 * WHY A CHOKEPOINT AND NOT ~157 CALL SITES: the shareable log is written by exactly three places —
 * [AppLogger]'s Timber tree (`logs/app.log`), [CrashHandler]'s report (`logs/last_crash.txt` AND the
 * copy handed to CrashActivity), and [ExitReasonReporter] (`logs/exit_reasons.txt`). Redacting at
 * those three points means a leak cannot be introduced later by someone logging a URL from a new call
 * site: the value is scrubbed on the way to disk no matter who wrote it. Redacting per-call-site is
 * the strategy that already failed once — PoTokenWebView logged a full dataSyncId at ERROR for
 * releases before anyone noticed.
 *
 * OVER-REDACTION IS THE SAFE DIRECTION. Losing a search term or a DRM UUID out of a diagnostic log
 * costs one round-trip with the customer; leaking their session cookie costs their account. Every
 * ambiguous case here resolves toward redacting.
 *
 * COST: run once per persisted line, on [AppLogger]'s IO executor — never on the main thread and never
 * on a per-buffer path. All patterns are precompiled, anchored on a literal, and use negated character
 * classes with no nested quantifiers, so none of them can backtrack catastrophically.
 */
object LogRedaction {

    private const val MASK = "<redacted>"

    /**
     * Credential-bearing URL query parameters. This is the one that matters most in practice: a
     * stream URL is `https://…googlevideo.com/videoplayback?…&pot=<PoToken>&sig=…`, and an IOException
     * from that request embeds the whole URL in its message.
     *
     * Longest alternatives first so the intended (not the shortest) name wins the match.
     */
    private val QUERY_PARAM = Regex(
        "([?&](?:" +
            "po_token|potoken|pot|" +
            "signature|api_sig|sig|" +
            "access_token|refresh_token|id_token|session_token|token|" +
            "visitor_data|visitordata|datasyncid|data_sync_id|pageid|" +
            "licence_key|license_key|licence|license|" +
            "api_key|apikey|key|auth|authuser|password|pwd|session|sid|sk|s" +
            ")=)[^&\\s\"'\\\\]+",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Header-shaped values, which run to the END OF THE LINE on purpose: a `Cookie:` value is a
     * `;`-separated list of pairs, so stopping at the first delimiter would leave every cookie after
     * the first one in the clear. `.` does not cross a newline here (DOT_MATCHES_ALL is not set), so
     * this consumes the header's own line and nothing after it.
     */
    private val HEADER_VALUE = Regex(
        "(\\b(?:set-cookie|cookie|authorization|x-goog-visitor-id|x-goog-pageid|x-goog-authuser|x-api-key|x-amzn-session-id)" +
            "\\s*[:=]\\s*).*",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Structured field assignments — JSON (`"dataSyncId":"…"`), Kotlin string templates
     * (`dataSyncId=…`) and log prose alike. Bounded to one token so it does not eat a whole line of
     * unrelated context the way [HEADER_VALUE] deliberately does.
     */
    private val SECRET_FIELD = Regex(
        "(\"?\\b(?:" +
            "datasyncid|data_sync_id|visitordata|visitor_data|" +
            "potoken|po_token|access_?token|refresh_?token|id_?token|session_?token|" +
            "licence_?key|license_?key|api_?key|password|passwd|pwd|secret" +
            ")\"?\\s*[:=]\\s*\"?)[^\\s,;\"'}\\])]+",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Google auth cookies written as bare `NAME=VALUE` pairs, with no `Cookie:` prefix to key off.
     * Longest names first: `SID` is a substring of nothing here thanks to `\b`, but ordering keeps the
     * intent obvious to the next reader.
     */
    private val COOKIE_PAIR = Regex(
        "\\b(__Secure-[13]PAPISID|__Secure-[13]PSIDCC|__Secure-[13]PSID|__Host-[A-Za-z0-9_-]+|" +
            "LOGIN_INFO|SAPISID|APISID|SIDCC|HSID|SSID|SID|PREF)=[^;\\s&\"']+",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Amazon Music session cookies written as bare pairs (rows 844-845): the account/session families
     * the Amazon migration keeps (`at-main`, `sess-at-acbes`, `ubid-main`, `x-main`, `session-token`…).
     * Not preceded by a word character or a hyphen, so `dropped-at-5=…` in prose is not mistaken for one.
     */
    private val AMAZON_COOKIE_PAIR = Regex(
        "(?<![A-Za-z0-9_-])((?:sess-at|sso-state|sst|ubid|at|x)-[a-z0-9]+(?:-[a-z0-9]+)*|" +
            "session-id-time|session-id|session-token|am-token)=[^;\\s&\"']+",
        RegexOption.IGNORE_CASE,
    )

    /**
     * `Authorization: Bearer …` survives above, but these also appear bare in exception text. `AmznMusic`
     * is Amazon Music's web-player scheme (a base64 token that may contain `/`, which the blob rule skips).
     */
    private val AUTH_SCHEME = Regex(
        "\\b(Bearer|SAPISIDHASH|Basic|OAuth|AmznMusic)\\s+[^\\s,;\"']+",
        RegexOption.IGNORE_CASE,
    )

    /** An account email identifies the customer; the owner never needs to know WHICH account. */
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    /**
     * Licence keys: the Gumroad grouped form (`XXXXXXXX-XXXXXXXX-XXXXXXXX-XXXXXXXX`) and the plain
     * UUID form. This also catches DRM/session UUIDs that media3 sometimes prints — an acceptable
     * trade, since a leaked licence key is directly monetisable and a DRM UUID is rarely the thing
     * that cracks a case.
     */
    private val LICENSE_KEY = Regex(
        "\\b(?:[0-9A-Z]{8}-[0-9A-Z]{8}-[0-9A-Z]{8}-[0-9A-Z]{8}|" +
            "[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})\\b",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Backstop for an opaque blob dumped with no key at all (a raw poToken or visitorData printed on
     * its own). The threshold is deliberately high and the character class excludes `/`, `:` and `.`
     * so a long URL or a stack frame is broken into short runs and never matches — but a 100+ char
     * base64 token, which nothing else in this app legitimately logs, does. Video ids are 11 chars and
     * playlist ids ~34, so real diagnostic identifiers are far below the bar.
     */
    private val OPAQUE_BLOB = Regex("[A-Za-z0-9_%=+-]{100,}")

    private val URL_USERINFO = Regex("(\\b[A-Za-z][A-Za-z0-9+.-]{0,15}://)[^/\\s:@\"']+:[^/\\s@\"']+@")

    private val CONTENT_URI = Regex("(content://([^/\\s\"']+)/)[^\\s\"'<>]+")

    /**
     * Rest of a file path: file names carry spaces, so it runs to the `: ` that ends a
     * FileNotFoundException path, a quote/bracket, or the end of the line. The atomic groups below stop
     * a second pass from re-splitting an already masked root, so the rules stay idempotent.
     */
    private const val PATH_REST = "[^\\r\\n\"'<>]+?(?=: |[\\r\\n\"'<>]|\\z)"

    /** `/storage/<volume>/…` and `/sdcard/…`: the volume root stays, the folders and file name go. */
    private val STORAGE_PATH = Regex(
        "(/(?>storage/(?:emulated/\\d+|self/primary|[^/\\s\"'<>]+)|sdcard)/)$PATH_REST",
    )

    /** Any other `file://` URI keeps only its first path segment. */
    private val FILE_URI = Regex("(file://(?>(?:/[^/\\s\"'<>]+)?)/)$PATH_REST")

    private val MEDIA_STORE_URI = Regex("content://media/(?:[A-Za-z0-9_.-]+/)+\\d+")

    private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")
    private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]{0,15})://")

    fun forLog(id: String?): String {
        if (id == null) return "null"
        if (YOUTUBE_ID.matches(id)) return id
        if (MEDIA_STORE_URI.matches(id)) return id
        val scheme = SCHEME.find(id)?.groupValues?.get(1) ?: "id"
        return "$scheme#${Integer.toHexString(id.hashCode())}"
    }

    /**
     * Scrubs [text]. Returns the input unchanged when it holds nothing secret, so the common case
     * costs only the pattern scans and allocates nothing.
     */
    fun redact(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        // Order matters: HEADER_VALUE eats to end-of-line, so run the bounded patterns that might sit
        // on the same line BEFORE it only where they'd otherwise be swallowed silently — in practice
        // each pattern is independent and idempotent, and a value masked twice stays masked once.
        out = QUERY_PARAM.replace(out, "$1$MASK")
        out = URL_USERINFO.replace(out, "$1$MASK@")
        out = CONTENT_URI.replace(out) {
            if (it.groupValues[2] == "media") it.value else "${it.groupValues[1]}$MASK"
        }
        out = STORAGE_PATH.replace(out, "$1$MASK")
        out = FILE_URI.replace(out) {
            if (it.groupValues[1] == "file:///storage/" || it.groupValues[1] == "file:///sdcard/") it.value else "${it.groupValues[1]}$MASK"
        }
        out = HEADER_VALUE.replace(out, "$1$MASK")
        out = SECRET_FIELD.replace(out, "$1$MASK")
        out = COOKIE_PAIR.replace(out) { "${it.groupValues[1]}=$MASK" }
        out = AMAZON_COOKIE_PAIR.replace(out) { "${it.groupValues[1]}=$MASK" }
        out = AUTH_SCHEME.replace(out) { "${it.groupValues[1]} $MASK" }
        out = LICENSE_KEY.replace(out, MASK)
        out = EMAIL.replace(out, MASK)
        out = OPAQUE_BLOB.replace(out, MASK)
        return out
    }
}
