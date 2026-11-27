package iad1tya.echo.music.tvlink

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 907 («Iniciar sesión en la TV»): source guards for what a unit test cannot run — nothing of the session or the
 * key reaches the log, the TV imports through the normal login, the phone sends only on «Enviar sesión», and the relay and
 * the privacy policies say what they do.
 */
class TvLinkGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun read(path: String) = File(root, path).also { assertTrue("$path se movió", it.isFile) }.readText()
    private fun main(path: String) = read("app/src/main/kotlin/com/music/echo/$path")

    @Test
    fun `the relay logs only status codes and exception class names`() {
        for (path in listOf("tvlink/TvLinkRelay.kt", "tvlink/TvLinkCode.kt", "tvlink/TvLinkPayload.kt", "tvlink/TvLinkSessionSeal.kt", "ui/screens/settings/TvLinkScreens.kt")) {
            val src = main(path)
            val logs = Regex("""Timber[^\n]*""").findAll(src).map { it.value }.filter { "Timber." in it }.toList()
            for (line in logs) {
                assertTrue("$path: $line", line.contains("res.code") || line.contains("javaClass.simpleName"))
                for (secret in listOf("normalized", "session", "cookie", "blob", "hello", "key(", "relayId")) {
                    assertFalse("$path logs $secret: $line", line.substringAfter("\",").contains(secret))
                }
            }
            assertFalse("$path: no AppLogger calls", src.contains("AppLogger."))
        }
        assertEquals(5, Regex("""Timber\.tag\(TAG\)""").findAll(main("tvlink/TvLinkRelay.kt")).count())
        assertTrue("the session never prints itself", main("tvlink/TvLinkPayload.kt").contains("override fun toString(): String = \"TvLinkSession(<redacted>)\""))
    }

    @Test
    fun `the TV signs in through the same completeLogin the normal login uses`() {
        val screen = main("ui/screens/settings/TvLinkScreens.kt")
        assertTrue(screen.contains("import iad1tya.echo.music.ui.screens.completeLogin"))
        assertEquals("one import path", 1, Regex("""\bcompleteLogin\(\s""").findAll(screen).count())
        assertFalse("never writes the cookie key itself", screen.contains("prefs[InnerTubeCookieKey] ="))
        assertTrue(main("ui/screens/LoginScreen.kt").contains("internal fun completeLogin("))
    }

    @Test
    fun `a sign-in that finishes after the user left never pops another screen`() {
        val screen = main("ui/screens/settings/TvLinkScreens.kt")
        val guard = "if (navController.currentDestination?.route == \"settings/accounts/tv_link_receive\") navController.navigateUp()"
        assertTrue(screen.contains(guard))
        assertEquals("only the guarded pop", 1, Regex("""navController\.navigateUp\(\)""").findAll(screen.substringBefore("private sealed interface SendPhase")).count())
        assertTrue("the route the guard compares to", main("ui/screens/NavigationBuilder.kt").contains("composable(\"settings/accounts/tv_link_receive\") {"))
    }

    @Test
    fun `a sign-in that could not start never leaves the TV stuck on Iniciando sesion`() {
        val login = main("ui/screens/LoginScreen.kt")
        assertTrue(login.contains("    onFinished: ((Boolean) -> Unit)?,\n): Boolean {"))
        assertTrue(login.contains("if (!loginCompletionRunning.compareAndSet(false, true)) return false"))
        val screen = main("ui/screens/settings/TvLinkScreens.kt")
        assertTrue(screen.contains("val started = completeLogin("))
        val notStarted = screen.substringAfter("if (!started) {").substringBefore("}")
        assertTrue(notStarted.contains("importing = false"))
        assertTrue("the failure text and the «Código nuevo» button appear", notStarted.contains("signInOk = false"))
    }

    @Test
    fun `the phone sends only from the Enviar sesion button, never from an opened link`() {
        val screen = main("ui/screens/settings/TvLinkScreens.kt")
        assertEquals(1, Regex("""TvLinkRelay\.send\(""").findAll(screen).count())
        val confirm = screen.substringAfter("is SendPhase.Confirm -> {").substringBefore("is SendPhase.Sending ->")
        assertTrue("the send is inside the confirmation", confirm.contains("TvLinkRelay.send(p.code, p.tvKey, session)"))
        assertTrue(confirm.contains("Text(stringResource(R.string.tvlink_send))"))
        val activity = main("MainActivity.kt")
        val branch = activity.substringAfter("if (uri.scheme == \"echomusic\" && uri.host.equals(\"tvlogin\", ignoreCase = true)) {")
            .substringBefore("\n            return\n        }")
        assertTrue(branch.contains("TvLinkCode.fromLink(uri.toString())"))
        assertTrue(branch.contains("navController.navigate(\"settings/accounts/tv_link_send?code=\$normalized\")"))
        assertFalse(branch.contains("TvLinkRelay"))
        assertTrue(read("app/src/main/AndroidManifest.xml").contains("<data android:host=\"tvlogin\" />"))
    }

    @Test
    fun `the relay is the rooms Worker and its tests run`() {
        assertTrue(main("tvlink/TvLinkRelay.kt").contains("ListenTogetherClient.AURA_ROOMS_URL"))
        val worker = read("docs/worker/rooms/src/index.js")
        assertTrue(worker.contains("const TVLINK_TTL_MS = 2 * 60_000;"))
        assertTrue(worker.contains("const MAX_SESSION = 32 * 1024;"))
        assertTrue(worker.contains("const tvlink = await routeTvLink(req, env, url);"))
        assertTrue(read("docs/worker/rooms/wrangler.toml").contains("new_sqlite_classes = [\"TvLink\"]"))
        assertTrue(read("docs/worker/rooms/package.json").contains("test/tvlink.test.mjs"))
        assertEquals(TvLinkCode.TTL_MS, 2 * 60_000L)
    }

    @Test
    fun `both screens are registered as Cuentas pages and indexed`() {
        val nav = main("ui/screens/NavigationBuilder.kt")
        assertTrue(nav.contains("composable(\"settings/accounts/tv_link_receive\") {"))
        assertTrue(nav.contains("route = \"settings/accounts/tv_link_send?code={code}\","))
        val panes = main("ui/newui/AuraSettingsPanes.kt")
        assertTrue(panes.contains("\"settings/accounts/tv_link_receive\" to \"settings/accounts\","))
        assertTrue(panes.contains("\"settings/accounts/tv_link_send\" to \"settings/accounts\","))
        val accounts = main("ui/screens/settings/AccountsScreen.kt")
        assertTrue(accounts.contains("if (!ytLoggedIn || isTvShell) {"))
        assertTrue(accounts.contains("onClick = { navController.navigate(\"settings/accounts/tv_link_receive\") },"))
        assertTrue(accounts.contains("onClick = { navController.navigate(\"settings/accounts/tv_link_send\") },"))
        val index = main("ui/screens/settings/SearchableSettings.kt")
        assertTrue(index.contains("Triple(stringResource(R.string.tvlink_receive_title), \"Cuentas\", \"settings/accounts\"),"))
        assertTrue(index.contains("Triple(stringResource(R.string.tvlink_send_title), \"Cuentas\", \"settings/accounts\"),"))
    }

    @Test
    fun `both privacy policies say what the TV sign-in sends`() {
        assertTrue(read("PRIVACIDAD.md").contains("| Salas de Aura: **Iniciar sesión en la TV** |"))
        assertTrue(read("PRIVACY_POLICY.md").contains("| Aura rooms: **Sign in on the TV** |"))
        assertTrue(read("PRIVACIDAD.md").contains("Solo salen de tu dispositivo si tú envías tu sesión de Google a tu TV"))
        assertTrue(read("PRIVACY_POLICY.md").contains("They only leave your device if you send your Google session to your TV"))
    }
}
