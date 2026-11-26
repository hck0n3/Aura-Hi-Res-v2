package iad1tya.echo.music.cloud

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Registry row 888: the rc request/response mapping and the read-only allow-list. Shapes from rclone v1.76.0. */
class CloudRcProtocolTest {

    @Test
    fun `write methods are refused before any request is built`() {
        val writes = listOf(
            "operations/deletefile", "operations/delete", "operations/purge", "operations/rmdir", "operations/rmdirs",
            "operations/mkdir", "operations/copyfile", "operations/movefile", "operations/uploadfile", "operations/copyurl",
            "operations/settier", "operations/cleanup", "sync/sync", "sync/copy", "sync/move", "sync/bisync",
            "core/command", "options/set", "rc/noopauth", "serve/list", "serve/types", "serve/stop", "mount/mount", "backend/command", "config/setpath",
            "config/password", "config/dump", "config/get", "config/unlock",
        )
        for (method in writes) {
            assertFalse(method, RcAllowList.isAllowed(method))
            try {
                RcAllowList.require(method)
                fail("$method must be refused")
            } catch (e: RcForbiddenMethodException) {
                assertTrue(e.message!!.contains(method))
            }
        }
        // The client refuses it too, without a daemon: the check comes before the URL.
        try {
            RcClient(1, "u", "p").call(RcCall("operations/deletefile", JSONObject()))
            fail("the client must refuse a write")
        } catch (_: RcForbiddenMethodException) {
        }
    }

    @Test
    fun `the allow-list is exactly the read-only set`() {
        assertEquals(
            setOf(
                "core/version", "core/quit", "config/create", "config/update", "config/delete", "config/listremotes",
                "config/oauthstatus", "config/oauthstop", "operations/list", "job/status", "job/stop",
                // Row 891/892: the read-only HTTP serve the index and the player read files through.
                "serve/start",
            ),
            RcAllowList.METHODS,
        )
        assertTrue(RcAllowList.METHODS.none { it.startsWith("sync/") })
        assertEquals(listOf("serve/start"), RcAllowList.METHODS.filter { it.startsWith("serve/") })
        assertEquals(listOf("operations/list"), RcAllowList.METHODS.filter { it.startsWith("operations/") })
    }

    @Test
    fun `a serve is always read-only, on loopback, behind its own credentials, with no listing or cache`() {
        // Params read in cmd/serve/rc.go and cmd/serve/http/http.go of rclone v1.76.0; checked against that binary:
        // a PUT answers 405, no credentials 401, a directory 404 (no listing), a Range request 206.
        val call = RcRequests.serveStart("aura-onedrive", 40500, "u", "p")
        assertEquals("serve/start", call.method)
        val body = call.body
        assertEquals("http", body.getString("type"))
        assertEquals("aura-onedrive:", body.getString("fs"))
        assertEquals("127.0.0.1:40500", body.getString("addr"))
        assertEquals("u", body.getString("user"))
        assertEquals("p", body.getString("pass"))
        assertTrue(body.getBoolean("read_only"))
        assertEquals("off", body.getString("vfs_cache_mode"))
        assertTrue(body.getBoolean("disable_zip"))
        assertTrue(body.getBoolean("disable_dir_list"))
        assertEquals(setOf("type", "fs", "addr", "user", "pass", "read_only", "vfs_cache_mode", "disable_zip", "disable_dir_list"), body.keySet())
        // The only place a serve is started: random port and credentials per serve, never logged.
        val streams = File(root(), "app/src/main/kotlin/com/music/echo/cloud/CloudStreams.kt").readText()
        assertTrue(streams.contains("val port = RcloneDaemon.freeLoopbackPort()"))
        assertTrue(streams.contains("val user = RcloneDaemon.randomToken(16)"))
        assertTrue(streams.contains("val pass = RcloneDaemon.randomToken(32)"))
        assertFalse(streams.contains("Timber"))
        val starters = File(root(), "app/src/main/kotlin/com/music/echo").walkTopDown().filter { it.extension == "kt" }
            .filter { it.readText().contains("RcRequests.serveStart(") }.map { it.name }.toList()
        assertEquals(listOf("CloudStreams.kt"), starters)
    }

    @Test
    fun `every rc method named in the cloud package is on the allow-list`() {
        val dir = File(root(), "app/src/main/kotlin/com/music/echo/cloud")
        val named = dir.walkTopDown().filter { it.extension == "kt" }
            .flatMap { f -> Regex("""RcCall\(\s*"([^"]+)"""").findAll(f.readText()).map { it.groupValues[1] } }
            .toSet()
        assertTrue("the requests are built here", named.size >= 10)
        assertEquals("named but not allowed: ${named - RcAllowList.METHODS}", emptySet<String>(), named - RcAllowList.METHODS)
        val client = File(dir, "RcClient.kt").readText()
        assertTrue("checked first thing", client.contains("fun call(call: RcCall): JSONObject {\n        RcAllowList.require(call.method)"))
    }

    @Test
    fun `requests carry the shapes rclone reads`() {
        val list = RcRequests.list("aura-onedrive", "Music/Album")
        assertEquals("operations/list", list.method)
        assertEquals("aura-onedrive:", list.body.getString("fs"))
        assertEquals("Music/Album", list.body.getString("remote"))
        assertFalse(list.body.getJSONObject("opt").getBoolean("noModTime"))

        val create = RcRequests.createRemoteAsync("aura-dropbox", "dropbox", mapOf("config_is_local" to "true"))
        assertEquals("config/create", create.method)
        assertTrue("runs as a job", create.body.getBoolean("_async"))
        assertTrue(create.body.getJSONObject("opt").getBoolean("nonInteractive"))
        assertEquals("dropbox", create.body.getString("type"))
        assertEquals("true", create.body.getJSONObject("parameters").getString("config_is_local"))

        val cont = RcRequests.continueConfig("aura-onedrive", "driveid_final", "b!abc", mapOf("config_drive_ok" to "true"))
        assertEquals("config/update", cont.method)
        val opt = cont.body.getJSONObject("opt")
        assertTrue(opt.getBoolean("continue"))
        assertTrue(opt.getBoolean("nonInteractive"))
        assertEquals("driveid_final", opt.getString("state"))
        assertEquals("b!abc", opt.getString("result"))
        assertFalse("a continue is answered synchronously", cont.body.has("_async"))

        assertEquals(7L, RcRequests.jobStatus(7).body.getLong("jobid"))
        assertEquals("aura-dropbox", RcRequests.deleteRemote("aura-dropbox").body.getString("name"))
    }

    @Test
    fun `responses map to typed values`() {
        val listed = RcResponses.entries(
            JSONObject(
                """{"list":[
                  {"Path":"Music/a.flac","Name":"a.flac","Size":1234,"MimeType":"audio/flac","ModTime":"2017-05-31T16:15:57.034468261+01:00","IsDir":false},
                  {"Path":"Music/Sub","Name":"Sub","Size":-1,"MimeType":"inode/directory","ModTime":"0001-01-01T00:00:00Z","IsDir":true},
                  {"Path":"x","Name":"x","Size":0,"ModTime":"not a time","IsDir":false}
                ]}""",
            ),
        )
        assertEquals(3, listed.size)
        val file = listed[0]
        assertEquals("a.flac", file.name)
        assertEquals("Music/a.flac", file.path)
        assertEquals(1234L, file.size)
        assertEquals("audio/flac", file.mimeType)
        assertEquals(1496243757034L, file.modTimeMs)
        assertFalse(file.isDir)
        assertTrue(listed[1].isDir)
        assertNull("rclone's zero time is no time", listed[1].modTimeMs)
        assertNull(listed[2].modTimeMs)
        assertNull(listed[2].mimeType)
        assertEquals(emptyList<CloudEntry>(), RcResponses.entries(JSONObject()))

        assertEquals(listOf("aura-dropbox", "aura-onedrive"), RcResponses.remotes(JSONObject("""{"remotes":["aura-dropbox","aura-onedrive"]}""")))
        assertEquals(emptyList<String>(), RcResponses.remotes(JSONObject("""{"remotes":null}""")))
        assertEquals(42L, RcResponses.jobId(JSONObject("""{"jobid":42}""")))

        val running = RcResponses.oauthStatus(JSONObject("""{"status":"running","authUrl":"http://127.0.0.1:53682/auth?state=xyz"}"""))
        assertTrue(running.running)
        assertEquals("http://127.0.0.1:53682/auth?state=xyz", running.authUrl)
        val stopped = RcResponses.oauthStatus(JSONObject("""{"status":"stopped"}"""))
        assertFalse(stopped.running)
        assertNull(stopped.authUrl)

        val job = RcResponses.jobStatus(JSONObject("""{"finished":true,"success":false,"error":"boom","output":{}}"""))
        assertTrue(job.finished)
        assertFalse(job.success)
        assertEquals("boom", job.error)
    }

    @Test
    fun `a config question and the end of a config are told apart`() {
        val asked = RcResponses.configOut(
            JSONObject(
                """{"State":"driveid_final","Option":{"Name":"config_driveid","Examples":[
                    {"Value":"b!1","Help":"OneDrive (business)"},{"Value":"b!2","Help":"Docs (documentLibrary)"}]},"Error":"","Result":""}""",
            ),
        )
        assertEquals("driveid_final", asked.state)
        assertEquals("config_driveid", asked.question!!.name)
        assertEquals(listOf("b!1" to "OneDrive (business)", "b!2" to "Docs (documentLibrary)"), asked.question!!.examples)
        val done = RcResponses.configOut(JSONObject("""{"State":"","Option":null,"Error":"","Result":""}"""))
        assertEquals("", done.state)
        assertNull(done.question)
        assertEquals("", RcResponses.configOut(null).state)
        assertEquals("didn't find section", RcResponses.errorMessage("""{"error":"didn't find section","input":{"fs":"x:"},"status":500}"""))
        assertEquals("", RcResponses.errorMessage("<html>"))
        assertEquals("", RcResponses.errorMessage(null))
    }

    @Test
    fun `the client posts JSON with basic auth to loopback and maps rc errors`() {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        var seenAuth: String? = null
        var seenBody: String? = null
        var seenMethod: String? = null
        server.createContext("/") { ex ->
            seenAuth = ex.requestHeaders.getFirst("Authorization")
            seenMethod = ex.requestMethod
            seenBody = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val (code, body) = when (ex.requestURI.path) {
                "/core/version" -> 200 to """{"version":"v1.76.0"}"""
                "/config/delete" -> 500 to """{"error":"secret path here","input":{"name":"aura-x"},"status":500}"""
                else -> 404 to "{}"
            }
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = RcClient(server.address.port, "user1", "pass2")
            assertEquals("v1.76.0", RcResponses.version(client.call(RcRequests.version())))
            assertEquals("POST", seenMethod)
            assertEquals("{}", seenBody)
            assertEquals("Basic " + Base64.getEncoder().encodeToString("user1:pass2".toByteArray()), seenAuth)
            try {
                client.call(RcRequests.deleteRemote("aura-x"))
                fail("an rc error is an exception")
            } catch (e: RcException) {
                assertEquals(500, e.status)
                assertEquals("secret path here", e.rcError)
                assertFalse("the message never carries rclone's text", e.message!!.contains("secret"))
            }
        } finally {
            server.stop(0)
        }
    }

    private fun root(): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
}
