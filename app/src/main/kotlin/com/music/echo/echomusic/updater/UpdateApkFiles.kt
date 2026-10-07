package iad1tya.echo.music.echomusic.updater

/**
 * Pure filename / version / completeness logic for the in-app updater.
 *
 * It lives apart from the Android plumbing for one reason: the updater's failure mode is SILENT. When
 * the installer is pointed at the wrong file the user sees a normal install prompt, installs, and stays
 * on the old build — no crash, no error, and every fix shipped to them is invisible. That class of bug
 * has to be pinned by tests, and none of the decisions below need a device to be checked.
 *
 * Nothing here touches the filesystem; callers pass in names and byte counts.
 */
object UpdateApkFiles {

    /** Prefix of every APK this app writes, matching the name of the GitHub release asset. */
    const val FILE_PREFIX = "Aura-Hi-Res-v2-"
    const val APK_SUFFIX = ".apk"

    /**
     * In-progress downloads carry this suffix instead of `.apk`. A partial file is therefore never
     * listed as an installable APK and can never be handed to the package installer — an interrupted
     * download cannot masquerade as a finished one.
     */
    const val PART_SUFFIX = ".apk.part"

    /** Fixed names written by updater versions <= 0.6.146. Recognised so they can be cleaned up. */
    const val LEGACY_APK_NAME = "echomusic.apk"
    const val LEGACY_ZIP_NAME = "echo_temp.zip"

    /** Marker of the owner's private no-subscription builds; those are never auto-deleted. */
    private const val NOSUB_MARKER = "NOSUB"

    private val DOT_RUN = Regex("\\.{2,}")

    /**
     * Strips the release-tag decoration so a GitHub tag (`v0.6.145`, `b5.0.1`) and an APK's own
     * `versionName` (`0.6.145`) can be compared. A leading `b` is only dropped when a digit follows, so
     * a hypothetical `beta9` tag survives intact.
     */
    fun normalizeVersion(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("v", ignoreCase = true)) {
            s = s.substring(1)
        } else if (s.length > 1 && (s[0] == 'b' || s[0] == 'B') && s[1].isDigit()) {
            s = s.substring(1)
        }
        return s.trim()
    }

    /** True when two version strings name the same release, ignoring tag decoration and case. */
    fun sameVersion(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        return normalizeVersion(a).equals(normalizeVersion(b), ignoreCase = true)
    }

    /**
     * The version string reaches us from the GitHub API, so it is untrusted input that ends up in a
     * file path. Anything outside `[A-Za-z0-9._-]` becomes `_` (which removes both separators), runs
     * of dots collapse to one, and leading dots are dropped: no `..` or `/` fragment survives, so the
     * name cannot address anything outside the update directory.
     */
    private fun sanitize(version: String): String {
        val sb = StringBuilder(version.length)
        for (c in version) {
            val safe = (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') ||
                c == '.' || c == '-' || c == '_'
            sb.append(if (safe) c else '_')
        }
        // Separators are already gone, but a `..` run is collapsed as well so no traversal fragment
        // survives in any form. No real version string contains one.
        val cleaned = sb.toString().replace(DOT_RUN, ".").trimStart('.')
        return cleaned.ifBlank { "unknown" }
    }

    /** Name of the finished APK for [version]. Two versions can never collide on disk. */
    fun apkFileName(version: String): String = FILE_PREFIX + sanitize(normalizeVersion(version)) + APK_SUFFIX

    /** Name of the in-progress download for [version]. */
    fun partFileName(version: String): String = FILE_PREFIX + sanitize(normalizeVersion(version)) + PART_SUFFIX

    /** Name of the in-progress nightly zip for [version] (extracted into [apkFileName] afterwards). */
    fun zipPartFileName(version: String): String = FILE_PREFIX + sanitize(normalizeVersion(version)) + ".zip.part"

    /**
     * True only for files this app itself wrote into its private update directory. Deleting the wrong
     * file on a user's device is worse than the bug being fixed, so every cleanup path filters on this.
     */
    fun isOwnUpdateFile(name: String): Boolean =
        name == LEGACY_APK_NAME || name == LEGACY_ZIP_NAME ||
            (
                name.startsWith(FILE_PREFIX) &&
                    (name.endsWith(APK_SUFFIX, ignoreCase = true) ||
                        name.endsWith(PART_SUFFIX, ignoreCase = true) ||
                        name.endsWith(".zip.part", ignoreCase = true))
                )

    /**
     * Which files in the private update directory may be removed while [keepVersion] is being fetched
     * or has just been fetched. Everything the app itself wrote for any OTHER version is dead weight:
     * it can only waste space or be installed by mistake. Files we did not write are never returned.
     */
    fun deletableNames(names: List<String>, keepVersion: String?): List<String> {
        val keepApk = keepVersion?.let { apkFileName(it) }
        val keepPart = keepVersion?.let { partFileName(it) }
        val keepZip = keepVersion?.let { zipPartFileName(it) }
        return names.filter { isOwnUpdateFile(it) && it != keepApk && it != keepPart && it != keepZip }
    }

    /**
     * Which copies in the user's public Downloads folder are ours to remove. Stricter than
     * [isOwnUpdateFile] on purpose — that folder holds the user's own files:
     *  - the name must be one this app could have produced (`Aura-Hi-Res-v2-<version>.apk`);
     *  - `-NOSUB` builds are excluded (the owner's private test APKs, which the app never writes);
     *  - anything in [keepNames] survives.
     * Legacy fixed names are NOT matched here: `echomusic.apk` in Downloads could be anyone's.
     */
    fun publicApksToDelete(names: List<String>, keepNames: Set<String>): List<String> =
        names.filter { name ->
            name.startsWith(FILE_PREFIX) &&
                name.endsWith(APK_SUFFIX, ignoreCase = true) &&
                !name.contains(NOSUB_MARKER, ignoreCase = true) &&
                name !in keepNames
        }

    /** What to do with the bytes already on disk for the version being downloaded. */
    enum class PartState {
        /** Nothing usable on disk (or it is unusable): start from byte 0. */
        RESTART,

        /** A prefix of the file is on disk: ask the server for the rest with a Range request. */
        RESUME,

        /** Exactly the expected number of bytes: the download is finished. */
        COMPLETE,
    }

    /**
     * Classifies a partial download. [expectedBytes] is the release asset's size as reported by GitHub;
     * pass 0 when it is unknown.
     *
     * A file LONGER than expected is restarted rather than resumed — that is what the old fixed-name
     * updater produced when it appended a new release's tail onto the previous release's APK.
     */
    fun partState(bytesOnDisk: Long, expectedBytes: Long): PartState = when {
        bytesOnDisk <= 0L -> PartState.RESTART
        expectedBytes <= 0L -> PartState.RESUME
        bytesOnDisk == expectedBytes -> PartState.COMPLETE
        bytesOnDisk > expectedBytes -> PartState.RESTART
        else -> PartState.RESUME
    }

    /** Verdict on an APK sitting on disk, read out of the archive itself. */
    enum class ApkVerdict {
        /** The archive declares exactly the version we were told to fetch. Install it. */
        MATCHES_TARGET,

        /**
         * The archive's name does not match the tag, but its versionCode is above the installed one.
         * The release tag and the built `versionName` can legitimately drift; refusing here would turn
         * a cosmetic mismatch into an app that can never update. Install, but say so in the log.
         */
        NEWER_THAN_INSTALLED,

        /** Corrupt, truncated, older, or the build already installed. Delete it and fetch again. */
        REJECT,
    }

    /**
     * The only check a stale or truncated file cannot fool: what the APK on disk says about ITSELF.
     * [apkVersionName] / [apkVersionCode] come from `PackageManager.getPackageArchiveInfo`, which
     * returns null for anything it cannot parse — that null is the truncation check.
     */
    fun verdictFor(
        targetVersion: String,
        apkVersionName: String?,
        apkVersionCode: Long,
        installedVersionCode: Long,
    ): ApkVerdict = when {
        apkVersionName.isNullOrBlank() -> ApkVerdict.REJECT
        sameVersion(apkVersionName, targetVersion) -> ApkVerdict.MATCHES_TARGET
        apkVersionCode > installedVersionCode -> ApkVerdict.NEWER_THAN_INSTALLED
        else -> ApkVerdict.REJECT
    }

    /** How the downloaded APK's signing certificate compares with the running app's. */
    enum class SignatureCheck {
        /** Same certificate set: the system installer will accept the update. */
        MATCH,

        /** Both sides were read and they differ: this install can never take this APK. */
        MISMATCH,

        /** One side could not be read on this device. Not evidence of tampering. */
        UNREADABLE,
    }

    fun signatureCheck(installed: Set<String>, downloaded: Set<String>): SignatureCheck = when {
        installed.isEmpty() || downloaded.isEmpty() -> SignatureCheck.UNREADABLE
        installed == downloaded -> SignatureCheck.MATCH
        else -> SignatureCheck.MISMATCH
    }

    /** What to do with a finished download. */
    enum class InstallGate {
        /** Hand it to the system installer. */
        INSTALL,

        /** The bytes are wrong (truncated, other version): delete and fetch again. */
        REDOWNLOAD,

        /**
         * The certificate differs from the installed app's. Re-downloading returns the same bytes, so
         * this must stop the flow with an explanation — never delete-and-retry.
         */
        SIGNATURE_MISMATCH,
    }

    /**
     * OWNER REPORT 2026-09-14 ("llega al 100, entra en un bucle y vuelve a descargar"): a signature
     * failure used to be treated like a corrupt file — delete, retry, download 87 MB again, fail again.
     * A certificate that differs is deterministic, so it ends the flow; one that cannot be READ is not
     * treated as a failure at all, because the system installer enforces the certificate anyway.
     */
    fun installGate(verdict: ApkVerdict, signature: SignatureCheck): InstallGate = when {
        verdict == ApkVerdict.REJECT -> InstallGate.REDOWNLOAD
        signature == SignatureCheck.MISMATCH -> InstallGate.SIGNATURE_MISMATCH
        else -> InstallGate.INSTALL
    }

    /** Tag carried by the download work so a run started for another release is never mistaken for it. */
    fun versionTag(version: String): String = "update_version:" + normalizeVersion(version)

    /**
     * Is [target] a release the user on [current] should be offered?
     *
     * The check used to be `current != target`, which offers ANY release that is merely different —
     * including an older one. That is not hypothetical here: `releases/latest` skips prereleases, so
     * anyone running a `-beta` build was told the previous STABLE release was "a new update", and
     * installing it is a downgrade. Same visible symptom as the stale-file bug, different cause.
     *
     * Numeric parts compare numerically (so 0.6.9 < 0.6.10), and a prerelease suffix ranks BELOW the
     * same version without one (0.6.146-beta1 < 0.6.146), with two prereleases compared by text.
     */
    fun isNewerRelease(target: String, current: String): Boolean {
        val (targetNums, targetPre) = splitRelease(target)
        val (currentNums, currentPre) = splitRelease(current)
        for (i in 0 until maxOf(targetNums.size, currentNums.size)) {
            val t = targetNums.getOrElse(i) { 0 }
            val c = currentNums.getOrElse(i) { 0 }
            if (t != c) return t > c
        }
        return when {
            targetPre == currentPre -> false
            currentPre.isEmpty() -> false // a prerelease never supersedes the finished release
            targetPre.isEmpty() -> true // the finished release supersedes its own prereleases
            else -> comparePrerelease(targetPre, currentPre) > 0
        }
    }

    /**
     * Dueño (2026-09-28): "no me salió la beta en el actualizador". The suffix used to be compared as
     * plain TEXT, and in text "beta10" < "beta9" ('1' < '9') — so from beta9 the updater saw beta10 as
     * OLDER and never offered it, and among releases it kept picking beta9 as "the newest". Natural
     * order instead: runs of digits compare as numbers, everything else as case-insensitive text.
     */
    private fun comparePrerelease(a: String, b: String): Int {
        val ra = Regex("\\d+|\\D+").findAll(a.lowercase()).map { it.value }.toList()
        val rb = Regex("\\d+|\\D+").findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ra.size, rb.size)) {
            val x = ra[i]
            val y = rb[i]
            val nx = x.toBigIntegerOrNull()
            val ny = y.toBigIntegerOrNull()
            val c = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y)
            if (c != 0) return c
        }
        return ra.size - rb.size
    }

    /** "0.6.146-beta1" -> ([0, 6, 146], "beta1"). Non-numeric junk in a part counts as 0. */
    private fun splitRelease(raw: String): Pair<List<Int>, String> {
        val v = normalizeVersion(raw)
        val dash = v.indexOf('-')
        val numeric = if (dash >= 0) v.substring(0, dash) else v
        val pre = if (dash >= 0) v.substring(dash + 1) else ""
        val parts = numeric.split(".").map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
        return parts to pre
    }

    // ---------------------------------------------------------------- release asset choice (plan B4)

    /** One APK attached to a GitHub release. */
    data class ReleaseApk(val name: String, val url: String, val size: Long)

    enum class ApkKind { ARM64, UNIVERSAL, UNLABELED }

    /** An asset the updater may install: an APK, never a debug or no-subscription test build. */
    fun isInstallableAssetName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(APK_SUFFIX) && !n.contains("debug") && !n.contains("nosub")
    }

    /**
     * NAMING CONTRACT (row 331): the CI names the universal APK `…-universal.apk` and the arm64 one
     * `…-arm64.apk`. Every release before B4 shipped one UNLABELED universal APK, and every installed
     * version picks with "arm64 device → first name WITHOUT 'universal', else first WITH it" — so the
     * universal must always carry "universal" and the arm64 one must never carry it.
     */
    fun kindOf(name: String): ApkKind {
        val n = name.lowercase()
        return when {
            n.contains("universal") -> ApkKind.UNIVERSAL
            n.contains("arm64") -> ApkKind.ARM64
            else -> ApkKind.UNLABELED
        }
    }

    /**
     * The APK this device should download. Only a device whose PRIMARY ABI is arm64-v8a gets the arm64
     * APK (~46 MB instead of ~87); every other device — 32-bit TVs and boxes, x86 Chromebooks or WSA
     * that merely translate ARM — gets the universal (or the unlabeled one older releases ship). An APK
     * the device cannot run is never returned, and the result never depends on the API's list order.
     */
    fun pickApkAsset(assets: List<ReleaseApk>, supportedAbis: List<String>): ReleaseApk? {
        val candidates = assets.filter { isInstallableAssetName(it.name) }.sortedBy { it.name }
        val primaryArm64 = supportedAbis.firstOrNull()?.equals("arm64-v8a", ignoreCase = true) == true
        if (primaryArm64) candidates.firstOrNull { kindOf(it.name) == ApkKind.ARM64 }?.let { return it }
        return candidates.firstOrNull { kindOf(it.name) == ApkKind.UNIVERSAL }
            ?: candidates.firstOrNull { kindOf(it.name) == ApkKind.UNLABELED }
    }
}
