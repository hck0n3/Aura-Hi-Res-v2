package iad1tya.echo.music.backup

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Registry row 908: «Copia automática» — how often. Stored by NAME in AutoBackupFrequencyKey: never rename a constant. */
enum class AutoBackupFrequency(val days: Long) { DAILY(1), WEEKLY(7) }

/**
 * Registry row 908: the pure rules of the automatic backup (no Android), tested as they are. The ZIP itself is the manual
 * backup's (BackupRestoreViewModel.LibraryBackupZip): this file only names it, keeps the last [KEEP] and lists what may
 * never be inside it.
 */
object AutoBackupPolicy {
    /** How many automatic copies stay in the folder; older ones are deleted, files the user put there never are. */
    const val KEEP = 5

    private const val PREFIX = "Aura_auto_"
    private const val SUFFIX = ".backup"
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    private val NAME = Regex("^Aura_auto_(\\d{14})(?: \\(\\d+\\))?\\.backup$")

    /** «Aura_auto_20261127093000.backup»: sortable by name, restorable from Importar ▸ «Importar desde archivo local». */
    fun fileName(now: LocalDateTime): String = PREFIX + now.format(STAMP) + SUFFIX

    /** True only for a file this feature wrote (a provider may add « (1)» to a clashing name). */
    fun isAutoBackup(name: String): Boolean = NAME.matches(name)

    /** The automatic copies to delete so that only the newest [keep] stay; never a file of any other name. */
    fun toDelete(names: List<String>, keep: Int = KEEP): List<String> {
        val ours = names.filter(::isAutoBackup).sortedByDescending { NAME.find(it)!!.groupValues[1] + "|" + it }
        return if (ours.size <= keep) emptyList() else ours.drop(keep.coerceAtLeast(0))
    }
}

/**
 * Registry row 908 (task: «la copia excluye cookies, la sesión de Google, visitorData, la licencia/clave maestra y todo lo de
 * license/»). The backup ZIP is written ONLY with entries this allows. Today it holds song.db and a whitelist of EQ and
 * appearance SharedPreferences (row 15); this is the second lock: even if that whitelist grew by mistake, none of these would
 * get in.
 */
object BackupExclusions {
    /**
     * Exact entries that never go in. `settings.preferences_pb` is the DataStore that holds the session: InnerTubeCookieKey
     * (cookies), VisitorDataKey, DataSyncIdKey and the account's name and e-mail. `jr_license` is license/'s SharedPreferences
     * (the licence key, the master key, the demo record).
     */
    val FORBIDDEN_ENTRIES: Set<String> = setOf(
        "settings.preferences_pb",
        "datastore/settings.preferences_pb",
        "shared_prefs/jr_license.xml",
    )

    /** Words no entry name may contain (case-insensitive): a session, a token, a licence or a cloud credential. */
    val FORBIDDEN_WORDS: List<String> = listOf(
        "preferences_pb", "license", "licence", "master", "cookie", "session", "token", "visitor", "datasync", "rclone",
        "no_backup", "credential", "secret", "password",
    )

    fun allows(entryName: String): Boolean {
        val name = entryName.trim().replace('\\', '/').trimStart('/')
        if (name.isEmpty() || name.contains("..")) return false
        if (name in FORBIDDEN_ENTRIES) return false
        val lower = name.lowercase()
        return FORBIDDEN_WORDS.none { it in lower }
    }
}
