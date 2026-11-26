package iad1tya.echo.music.cloud

import java.time.OffsetDateTime
import org.json.JSONArray
import org.json.JSONObject

/*
 * Registry row 888 — the rc (remote control) protocol of the rclone daemon, as Aura speaks it. Pure (org.json only), so
 * the request/response mapping is tested on the JVM. Shapes read in the rclone source of the pinned version (v1.76.0):
 * fs/config/rc.go (the config methods), lib/oauthutil/oauthutil.go (config/oauthstatus, config/oauthstop), fs/operations/rc.go and
 * lsjson.go (operations/list), fs/rc/jobs/job.go (_async, job/status, job/stop), fs/backend_config.go (ConfigOut).
 */

/**
 * The ONLY rc methods Aura may call (registry row 888). Aura is read-only toward the cloud: nothing under `operations/`
 * but `list`, no `sync/`, no copy/move/delete/mkdir/upload, no `rc/` or `options/` (which could switch the daemon's own
 * settings), no `core/command`. The config methods touch only the local, encrypted rclone.conf. Wave 2 (rows 891-892) adds
 * `serve/start` for streaming, built only by [RcRequests.serveStart] (read-only, loopback, random
 * credentials). [RcClient] refuses anything not listed before a byte leaves the app.
 */
object RcAllowList {
    val METHODS: Set<String> = setOf(
        "core/version",
        "core/quit",
        "config/create",
        "config/update",
        "config/delete",
        "config/listremotes",
        "config/oauthstatus",
        "config/oauthstop",
        "operations/list",
        "job/status",
        "job/stop",
        // Row 891/892: an HTTP server of ONE remote on 127.0.0.1, always started read-only (RcRequests.serveStart); it
        // ends with the daemon (row 888), so no serve/stop is needed.
        "serve/start",
    )

    fun isAllowed(method: String): Boolean = method in METHODS

    fun require(method: String) {
        if (!isAllowed(method)) throw RcForbiddenMethodException(method)
    }
}

class RcForbiddenMethodException(method: String) : IllegalArgumentException("rc method not allowed: $method")

/** One rc request: POST `/<method>` with a JSON body. */
class RcCall(val method: String, val body: JSONObject)

object RcRequests {
    fun version() = RcCall("core/version", JSONObject())
    fun quit() = RcCall("core/quit", JSONObject())
    fun listRemotes() = RcCall("config/listremotes", JSONObject())
    fun deleteRemote(name: String) = RcCall("config/delete", JSONObject().put("name", name))
    fun oauthStatus() = RcCall("config/oauthstatus", JSONObject())
    fun oauthStop() = RcCall("config/oauthstop", JSONObject())
    fun jobStatus(jobId: Long) = RcCall("job/status", JSONObject().put("jobid", jobId))
    fun jobStop(jobId: Long) = RcCall("job/stop", JSONObject().put("jobid", jobId))

    /** One directory of [remote] (a remote NAME, without the colon); [path] is relative to its root, "" = the root. */
    fun list(remote: String, path: String) = RcCall(
        "operations/list",
        JSONObject()
            .put("fs", "$remote:")
            .put("remote", path)
            .put("opt", JSONObject().put("noModTime", false).put("noMimeType", false)),
    )

    /**
     * Row 891/892: serves [remote] over HTTP on 127.0.0.1:[port] behind the random [user]/[pass] pair, read-only (rclone
     * answers 405 to any write), without the VFS cache (media3's cache keeps the bytes), without directory pages or zip
     * downloads: only a file whose path is known can be fetched, with Range requests. Params read in cmd/serve/rc.go and
     * cmd/serve/http/http.go of v1.76.0 (flat names, as `rclone rc serve/start` takes them).
     */
    fun serveStart(remote: String, port: Int, user: String, pass: String) = RcCall(
        "serve/start",
        JSONObject()
            .put("type", "http")
            .put("fs", "$remote:")
            .put("addr", "127.0.0.1:$port")
            .put("user", user)
            .put("pass", pass)
            .put("read_only", true)
            .put("vfs_cache_mode", "off")
            .put("disable_zip", true)
            .put("disable_dir_list", true),
    )

    /**
     * Starts creating [name] in the background (`_async`): with the OAuth answers in [parameters] it reaches rclone's
     * local OAuth webserver and blocks there until the browser comes back, so it cannot be a synchronous call.
     * `nonInteractive`: a question rclone cannot answer from [parameters] comes back in the job's output instead of
     * being asked on a terminal nobody sees.
     */
    fun createRemoteAsync(name: String, type: String, parameters: Map<String, String>) = RcCall(
        "config/create",
        JSONObject()
            .put("name", name)
            .put("type", type)
            .put("parameters", JSONObject(parameters))
            .put("opt", JSONObject().put("nonInteractive", true))
            .put("_async", true),
    )

    /** Answers the question a non-interactive config stopped at ([state] as rclone returned it, [result] the answer). */
    fun continueConfig(name: String, state: String, result: String, parameters: Map<String, String>) = RcCall(
        "config/update",
        JSONObject()
            .put("name", name)
            .put("parameters", JSONObject(parameters))
            .put(
                "opt",
                JSONObject().put("nonInteractive", true).put("continue", true).put("state", state).put("result", result),
            ),
    )
}

/** One entry of a cloud folder, as `operations/list` returns it. [modTimeMs] is null when rclone gives none. */
data class CloudEntry(
    val name: String,
    val path: String,
    val size: Long,
    val mimeType: String?,
    val modTimeMs: Long?,
    val isDir: Boolean,
)

object RcResponses {
    fun version(o: JSONObject): String = o.optString("version")

    fun remotes(o: JSONObject): List<String> = o.optJSONArray("remotes").strings()

    fun entries(o: JSONObject): List<CloudEntry> {
        val list = o.optJSONArray("list") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val item = list.optJSONObject(i) ?: return@mapNotNull null
            CloudEntry(
                name = item.optString("Name"),
                path = item.optString("Path"),
                size = item.optLong("Size", -1L),
                mimeType = item.optString("MimeType").takeIf { it.isNotEmpty() },
                modTimeMs = parseTime(item.optString("ModTime")),
                isDir = item.optBoolean("IsDir", false),
            )
        }
    }

    /** RFC 3339 with up to nanoseconds; rclone's zero time (year 1) and anything unparsable is "no time". */
    fun parseTime(text: String): Long? =
        runCatching { OffsetDateTime.parse(text) }.getOrNull()
            ?.takeIf { it.year > 1970 }
            ?.toInstant()?.toEpochMilli()

    fun jobId(o: JSONObject): Long = o.optLong("jobid", -1L)

    data class JobStatus(val finished: Boolean, val success: Boolean, val error: String, val output: JSONObject?)

    fun jobStatus(o: JSONObject) = JobStatus(
        finished = o.optBoolean("finished", false),
        success = o.optBoolean("success", false),
        error = o.optString("error"),
        output = o.optJSONObject("output"),
    )

    /** `authUrl` is only present while rclone's OAuth webserver runs. */
    data class OAuthStatus(val running: Boolean, val authUrl: String?)

    fun oauthStatus(o: JSONObject) = OAuthStatus(
        running = o.optString("status") == "running",
        authUrl = o.optString("authUrl").takeIf { it.isNotEmpty() },
    )

    /** A question rclone's config stopped at: the option [name] and its choices (value, help). */
    data class ConfigQuestion(val name: String, val examples: List<Pair<String, String>>)

    /** fs.ConfigOut reshaped by rc: an empty [state] means the config is finished. */
    data class ConfigOut(val state: String, val question: ConfigQuestion?, val error: String)

    fun configOut(o: JSONObject?): ConfigOut {
        if (o == null) return ConfigOut("", null, "")
        val option = o.optJSONObject("Option")
        val question = option?.let { opt ->
            val examples = opt.optJSONArray("Examples")
            ConfigQuestion(
                name = opt.optString("Name"),
                examples = (0 until (examples?.length() ?: 0)).mapNotNull { i ->
                    examples!!.optJSONObject(i)?.let { it.optString("Value") to it.optString("Help") }
                },
            )
        }
        return ConfigOut(state = o.optString("State"), question = question, error = o.optString("Error"))
    }

    /** The `error` field of an rc error body (it also echoes the input: never log the body). */
    fun errorMessage(body: String?): String =
        runCatching { JSONObject(body ?: "").optString("error") }.getOrNull().orEmpty()

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }
}
