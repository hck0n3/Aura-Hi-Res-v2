package iad1tya.echo.music.legal

import iad1tya.echo.music.listentogether.ListenTogetherClient
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 774: the privacy policy can be read inside the app. The repository-root files stay canonical and the
 * app assets must be byte-identical copies of them, so the text a user reads is the text that is published.
 */
class PrivacyPolicyAssetTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun file(path: String) = File(root, path).also { assertTrue("$path se movió", it.isFile) }

    private fun kt(path: String) = file("app/src/main/kotlin/com/music/echo/$path").readText()

    @Test
    fun `the in-app policies are byte-identical copies of the root files`() {
        for ((rootName, asset) in listOf("PRIVACIDAD.md" to PrivacyPolicyInfo.ASSET_ES, "PRIVACY_POLICY.md" to PrivacyPolicyInfo.ASSET_EN)) {
            assertArrayEquals(
                "app/src/main/assets/$asset difiere de $rootName: copia el archivo raíz",
                file(rootName).readBytes(),
                file("app/src/main/assets/$asset").readBytes(),
            )
        }
    }

    @Test
    fun `Spanish app languages read the Spanish policy and every other one the English translation`() {
        assertEquals("legal/PRIVACIDAD.md", PrivacyPolicyInfo.assetFor("es"))
        assertEquals("legal/PRIVACY_POLICY.md", PrivacyPolicyInfo.assetFor("en"))
        assertEquals("legal/PRIVACY_POLICY.md", PrivacyPolicyInfo.assetFor("pt"))
        assertEquals("legal/PRIVACY_POLICY.md", PrivacyPolicyInfo.assetFor(""))
    }

    @Test
    fun `the viewer is reachable from Privacidad and Acerca de and reuses the terms reader`() {
        val screens = kt("legal/TermsScreens.kt")
        assertTrue(screens.contains("fun PrivacyPolicyScreen("))
        assertTrue(screens.contains("markdown = rememberTermsMarkdown(PrivacyPolicyInfo.assetFor(language)),"))
        assertTrue(screens.contains("context.assets.open(assetPath)"))
        // The terms keep their own asset and their acceptance footer.
        assertTrue(screens.contains("private fun rememberTermsMarkdown(assetPath: String = TermsInfo.ASSET_PATH): String {"))
        assertTrue(screens.contains("R.string.terms_accepted_on,"))
        assertTrue(kt("ui/screens/NavigationBuilder.kt").contains("composable(\"settings/privacy_policy\") {\n        iad1tya.echo.music.legal.PrivacyPolicyScreen(navController, scrollBehavior)"))
        assertTrue(kt("ui/screens/settings/PrivacySettings.kt").contains("onClick = { navController.navigate(\"settings/privacy_policy\") }"))
        assertTrue(kt("ui/screens/settings/AboutScreen.kt").contains("onClick = { navController.navigate(\"settings/privacy_policy\") },"))
        assertTrue(file("app/src/main/res/values-es/privacy_policy_strings.xml").readText().contains("<string name=\"privacy_policy_title\">Política de privacidad</string>"))
        assertTrue(file("app/src/main/res/values/privacy_policy_strings.xml").readText().contains("<string name=\"privacy_policy_title\">"))
    }

    @Test
    fun `both policies name the third-party Listen Together server the app falls back to`() {
        val host = ListenTogetherClient.DEFAULT_SERVER_URL.removePrefix("wss://").substringBefore('/')
        assertEquals("metroserverx.meowery.eu", host)
        val es = file("PRIVACIDAD.md").readText()
        val en = file("PRIVACY_POLICY.md").readText()
        assertTrue(es.contains("- **Escuchar juntos con las salas de Aura apagadas:**") && es.contains("`$host`, que **no operamos nosotros**"))
        assertTrue(en.contains("- **Listen Together with Aura rooms turned off:**") && en.contains("`$host`, which **we do not run**"))
        assertTrue(es.contains("| **Escuchar juntos: salas de Aura** (**activadas por defecto**;"))
        assertTrue(en.contains("| **Listen Together: Aura rooms** (**on by default**;"))
        assertFalse(es.contains("| **Escuchar juntos** |"))
        assertFalse(en.contains("| **Listen Together** |"))
    }
}
