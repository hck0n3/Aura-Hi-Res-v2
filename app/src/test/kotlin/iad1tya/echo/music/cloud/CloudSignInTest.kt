package iad1tya.echo.music.cloud

import iad1tya.echo.music.cloud.CloudLoginEvent.AuthUrl
import iad1tya.echo.music.cloud.CloudLoginEvent.BrowserDone
import iad1tya.echo.music.cloud.CloudLoginEvent.Cancel
import iad1tya.echo.music.cloud.CloudLoginEvent.Fail
import iad1tya.echo.music.cloud.CloudLoginEvent.Start
import iad1tya.echo.music.cloud.CloudLoginEvent.Timeout
import iad1tya.echo.music.cloud.CloudLoginEvent.Verified
import iad1tya.echo.music.cloud.CloudLoginState.Connected
import iad1tya.echo.music.cloud.CloudLoginState.Failed
import iad1tya.echo.music.cloud.CloudLoginState.Finishing
import iad1tya.echo.music.cloud.CloudLoginState.Idle
import iad1tya.echo.music.cloud.CloudLoginState.Starting
import iad1tya.echo.music.cloud.CloudLoginState.WaitingForBrowser
import iad1tya.echo.music.cloud.CloudProvider.DROPBOX
import iad1tya.echo.music.cloud.CloudProvider.ONEDRIVE
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 890: the OneDrive / Dropbox sign-in, pure part, and its wiring into Ajustes. */
class CloudSignInTest {
    private val url = "http://127.0.0.1:53682/auth?state=vL3ZnHigsFpUKzHp2WTvAQ"

    private fun run(vararg events: CloudLoginEvent, from: CloudLoginState = Idle) =
        events.fold(from) { s, e -> CloudLoginMachine.reduce(s, e) }

    @Test
    fun `the happy path ends connected`() {
        assertEquals(Starting(ONEDRIVE), run(Start(ONEDRIVE)))
        assertEquals(WaitingForBrowser(ONEDRIVE, url), run(Start(ONEDRIVE), AuthUrl(url)))
        assertEquals(Finishing(ONEDRIVE), run(Start(ONEDRIVE), AuthUrl(url), BrowserDone))
        assertEquals(Connected(ONEDRIVE), run(Start(ONEDRIVE), AuthUrl(url), BrowserDone, Verified))
        // A job that ends before the page was seen (rclone answered at once) still finishes.
        assertEquals(Finishing(DROPBOX), run(Start(DROPBOX), BrowserDone))
    }

    @Test
    fun `cancel, timeout, browser closed and errors end failed with their reason`() {
        assertEquals(Failed(DROPBOX, CloudFailure.CANCELLED), run(Start(DROPBOX), AuthUrl(url), Cancel))
        assertEquals("browser closed: the wait runs out", Failed(DROPBOX, CloudFailure.TIMEOUT), run(Start(DROPBOX), AuthUrl(url), Timeout))
        assertEquals(Failed(ONEDRIVE, CloudFailure.TIMEOUT), run(Start(ONEDRIVE), Timeout))
        assertEquals(Failed(ONEDRIVE, CloudFailure.NO_NETWORK), run(Start(ONEDRIVE), Fail(CloudFailure.NO_NETWORK)))
        assertEquals(Failed(ONEDRIVE, CloudFailure.RECONNECT), run(Start(ONEDRIVE), AuthUrl(url), BrowserDone, Fail(CloudFailure.RECONNECT)))
        assertEquals("verifying is past the browser: no timeout there", Finishing(ONEDRIVE), run(Start(ONEDRIVE), BrowserDone, Timeout))
    }

    @Test
    fun `only rclone's own loopback page is ever opened`() {
        assertEquals(Failed(ONEDRIVE, CloudFailure.OTHER), run(Start(ONEDRIVE), AuthUrl("https://evil.example/auth?state=x")))
        assertTrue(CloudLoginPolicy.isLoopbackAuthUrl(url))
        for (bad in listOf(
            "https://127.0.0.1:53682/auth?state=x", "http://localhost:53682/auth?state=x", "http://127.0.0.1:53683/auth?state=x",
            "http://127.0.0.1:53682/other?state=x", "http://127.0.0.1:53682/auth?code=x", "http://user@127.0.0.1:53682/auth?state=x",
            "javascript:alert(1)", "", "http://127.0.0.1.evil.com:53682/auth?state=x",
        )) {
            assertFalse(bad, CloudLoginPolicy.isLoopbackAuthUrl(bad))
        }
    }

    @Test
    fun `terminal and busy states ignore stray events, and one sign-in runs at a time`() {
        val connected = Connected(DROPBOX)
        for (e in listOf(AuthUrl(url), BrowserDone, Verified, Cancel, Timeout, Fail(CloudFailure.OTHER))) {
            assertEquals(connected, CloudLoginMachine.reduce(connected, e))
            assertEquals(Idle, CloudLoginMachine.reduce(Idle, e))
        }
        assertEquals("a second Start while busy is ignored", WaitingForBrowser(ONEDRIVE, url), run(Start(ONEDRIVE), AuthUrl(url), Start(DROPBOX)))
        assertEquals("a new sign-in after a failure", Starting(DROPBOX), run(Start(DROPBOX), from = Failed(ONEDRIVE, CloudFailure.CANCELLED)))
        assertTrue(Starting(ONEDRIVE).busy && WaitingForBrowser(ONEDRIVE, url).busy && Finishing(ONEDRIVE).busy)
        assertFalse(Idle.busy || connected.busy || Failed(ONEDRIVE, CloudFailure.OTHER).busy)
        assertFalse("the sign-in page never reaches a log through toString", WaitingForBrowser(ONEDRIVE, url).toString().contains("state="))
        assertFalse(AuthUrl(url).toString().contains("127.0.0.1"))
    }

    @Test
    fun `rclone's questions are answered or the sign-in fails, never loops`() {
        val drives = RcResponses.ConfigQuestion(
            "config_driveid",
            listOf("b!lib" to "Documents (documentLibrary)", "b!me" to "OneDrive (personal)"),
        )
        assertEquals("the user's own drive first", "b!me", CloudLoginPolicy.answer(ONEDRIVE, drives))
        assertEquals("b!lib", CloudLoginPolicy.pickDrive(listOf("b!lib" to "Documents (documentLibrary)")))
        assertNull(CloudLoginPolicy.pickDrive(emptyList()))
        assertEquals("true", CloudLoginPolicy.answer(ONEDRIVE, RcResponses.ConfigQuestion("config_drive_ok", emptyList())))
        assertEquals("onedrive", CloudLoginPolicy.answer(ONEDRIVE, RcResponses.ConfigQuestion("config_type", emptyList())))
        assertNull(CloudLoginPolicy.answer(DROPBOX, RcResponses.ConfigQuestion("config_type", emptyList())))
        assertNull("an unknown question fails the sign-in", CloudLoginPolicy.answer(ONEDRIVE, RcResponses.ConfigQuestion("config_site_url", emptyList())))
        assertTrue(CloudLoginPolicy.MAX_QUESTIONS in 1..6)
    }

    @Test
    fun `the parameters ask for the browser flow and, for OneDrive, read-only scopes`() {
        val od = CloudLoginPolicy.createParameters(ONEDRIVE)
        assertEquals("Files.Read Files.Read.All offline_access", od["access_scopes"])
        assertFalse(od.getValue("access_scopes").contains("Write"))
        assertEquals("true", od["config_is_local"])
        assertEquals("rclone must not try to open a browser itself", "true", od["config_auth_no_browser"])
        assertEquals("onedrive", od["config_type"])
        val db = CloudLoginPolicy.createParameters(DROPBOX)
        assertEquals(mapOf("config_is_local" to "true", "config_auth_no_browser" to "true"), db)
        assertEquals(listOf("aura-onedrive", "aura-dropbox"), CloudProvider.entries.map { it.remoteName })
        assertEquals(ONEDRIVE, CloudProvider.ofRemote("aura-onedrive"))
        assertNull(CloudProvider.ofRemote("someone-elses"))
        assertTrue("bounded wait", CloudLoginPolicy.OAUTH_TIMEOUT_MS <= 10 * 60_000L && CloudLoginPolicy.POLL_MS >= 1_000L)
    }

    @Test
    fun `rclone errors map to what the page says`() {
        assertEquals(CloudFailure.CANCELLED, CloudLoginPolicy.classify("config failed to refresh token: oauth authentication was cancelled"))
        assertEquals(CloudFailure.DENIED, CloudLoginPolicy.classify("Error: access_denied: The user has denied access"))
        assertEquals(CloudFailure.RECONNECT, CloudLoginPolicy.classify("couldn't fetch token: invalid_grant: AADSTS70000: revoked"))
        assertEquals(CloudFailure.RECONNECT, CloudLoginPolicy.classify("expired_access_token/"))
        assertEquals(CloudFailure.NO_NETWORK, CloudLoginPolicy.classify("Get \"https://graph.microsoft.com\": dial tcp: lookup graph.microsoft.com: no such host"))
        assertEquals(CloudFailure.OTHER, CloudLoginPolicy.classify("something else"))
        assertEquals(CloudAccountStatus.CONNECTED, CloudLoginPolicy.statusOf(null))
        assertEquals(CloudAccountStatus.RECONNECT, CloudLoginPolicy.statusOf(CloudFailure.RECONNECT))
        assertEquals("no network says nothing about the account", CloudAccountStatus.UNCHECKED, CloudLoginPolicy.statusOf(CloudFailure.NO_NETWORK))
    }

    // ── Wiring, pinned on the source (no Android SDK in the unit job) ──

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun `the page is reachable from Cuentas y sincronizacion and from search`() {
        val settings = main("ui/newui/AuraSettingsScreen.kt")
        val accounts = settings.substringAfter("title = groupAccounts,").substringBefore("title = storageText,")
        assertTrue(accounts.contains("AuraSettingsEntry(cloudText, description = cloudDescText) {\n                            navController.navigate(\"settings/cloud\")\n                        }.opensPage(\"settings/cloud\")"))
        assertTrue(settings.contains("val cloudText = stringResource(R.string.cloud_title)"))
        val nav = main("ui/screens/NavigationBuilder.kt")
        assertTrue(nav.contains("composable(\"settings/cloud\") {\n        AuraSettingsPaneFrame(navController, it) {\n            iad1tya.echo.music.ui.screens.settings.CloudAccountsScreen(navController, scrollBehavior)"))
        assertTrue(main("ui/newui/AuraSettingsPanes.kt").contains("    \"settings/cloud\",\n"))
        val index = main("ui/screens/settings/SearchableSettings.kt")
        for (s in listOf("cloud_connect_onedrive", "cloud_connect_dropbox", "cloud_intro")) {
            assertTrue(s, index.contains("Triple(stringResource(R.string.$s), stringResource(R.string.cloud_title), \"settings/cloud\"),"))
        }
        assertFalse("INDEX-ECHO RULE", index.contains("Triple(stringResource(R.string.cloud_title),"))
    }

    @Test
    fun `the page's texts exist in both languages and never name the tool`() {
        val names = Regex("""R\.string\.(cloud_\w+)""").findAll(
            main("ui/screens/settings/CloudAccountsScreen.kt") + main("ui/newui/AuraSettingsScreen.kt"),
        ).map { it.groupValues[1] }.toSet()
        assertTrue(names.size >= 20)
        for (dir in listOf("values", "values-es")) {
            val xml = File(root, "app/src/main/res/$dir/strings.xml").readText()
            for (n in names) assertTrue("$n in $dir", xml.contains("<string name=\"$n\">"))
            val cloud = Regex("""<string name="cloud_\w+">([^<]*)</string>""").findAll(xml).map { it.groupValues[1] }.toList()
            assertTrue(cloud.none { it.contains("rclone", ignoreCase = true) })
        }
    }

    @Test
    fun `leaving the page cancels the sign-in, and a failed one leaves no half-made account`() {
        val vm = main("viewmodels/CloudAccountsViewModel.kt")
        assertTrue(vm.contains("loginJob = viewModelScope.launch {"))
        assertTrue("a late reply cannot overwrite «cancelled»", vm.contains("{ state -> if (isActive) _ui.update { it.copy(login = state) } }"))
        val repo = main("cloud/CloudRepository.kt")
        assertTrue(repo.contains("if (daemonReached && outcome !is CloudLoginState.Connected) {\n                withContext(NonCancellable) { cleanUp(provider) }"))
        val clean = repo.substringAfter("private suspend fun cleanUp(provider: CloudProvider) {").substringBefore("\n    private suspend fun <T> rc(")
        assertTrue(clean.contains("RcRequests.oauthStop()"))
        assertTrue(clean.contains("RcRequests.deleteRemote(provider.remoteName)"))
        // The wait is bounded and only steps while a sign-in runs.
        val wait = repo.substringAfter("private suspend fun awaitJob(").substringBefore("\n    /** After anything but success")
        assertTrue(wait.contains("while (System.currentTimeMillis() - started < CloudLoginPolicy.OAUTH_TIMEOUT_MS) {"))
        assertTrue(wait.contains("delay(CloudLoginPolicy.POLL_MS)"))
        assertTrue(wait.contains("if (!CloudLoginPolicy.isLoopbackAuthUrl(url)) return null"))
        // Logs: the provider and an outcome, never the url, a path or rclone's text.
        val logs = Regex("""Timber\.tag\(TAG\)\.[a-z]\(([^)]*)""").findAll(repo).map { it.groupValues[1] }.toList()
        assertTrue(logs.isNotEmpty())
        for (line in logs) for (bad in listOf("url", "path", "rcError", "error", "remote")) {
            assertFalse("logs $bad: $line", line.substringAfter("\",", "").contains(bad, ignoreCase = true))
        }
    }
}
