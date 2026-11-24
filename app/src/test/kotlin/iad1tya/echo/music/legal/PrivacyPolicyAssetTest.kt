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
    fun `both policies say Listen Together uses only Aura's own server`() {
        // Row 818 (owner decision B1, 2026-11-24): the app no longer reaches the shared server, so the policies no longer name it
        // (row 774 had added that paragraph for the «Salas de Aura» switch, which is gone).
        val host = ListenTogetherClient.DEFAULT_SERVER_URL.removePrefix("wss://").substringBefore('/')
        val es = file("PRIVACIDAD.md").readText()
        val en = file("PRIVACY_POLICY.md").readText()
        assertFalse(es.contains(host) || en.contains(host))
        assertFalse(es.contains("- **Escuchar juntos con las salas de Aura apagadas:**"))
        assertFalse(en.contains("- **Listen Together with Aura rooms turned off:**"))
        assertTrue(es.contains("| **Escuchar juntos: salas de Aura** (el único servidor de Escuchar juntos) |"))
        assertTrue(en.contains("| **Listen Together: Aura rooms** (the only Listen Together server) |"))
        assertTrue(es.contains("y las salas de Aura de Escuchar juntos, que son el único servidor que usa Escuchar juntos."))
        assertTrue(en.contains("and Aura's own Listen Together rooms, the only server Listen Together uses."))
        assertFalse(es.contains("| **Escuchar juntos** |"))
        assertFalse(en.contains("| **Listen Together** |"))
    }

    @Test
    fun `the reader shows link text and no backticks, and leaves the terms untouched`() {
        assertEquals(
            "La traducción al inglés está en PRIVACY_POLICY.md; cláusula 11 de los Términos y Condiciones.",
            stripInlineMarkdown(
                "La traducción al inglés está en [PRIVACY_POLICY.md](PRIVACY_POLICY.md); cláusula 11 de los " +
                    "[Términos y Condiciones](app/src/main/assets/legal/TERMINOS_Y_CONDICIONES.md).",
            ),
        )
        assertEquals(
            "- **Proyecto:** https://github.com/hck0n3/Aura-Hi-Res-v2",
            stripInlineMarkdown("- **Proyecto:** [https://github.com/hck0n3/Aura-Hi-Res-v2](https://github.com/hck0n3/Aura-Hi-Res-v2)"),
        )
        assertEquals("servidor metroserverx.meowery.eu, que", stripInlineMarkdown("servidor `metroserverx.meowery.eu`, que"))
        for (asset in listOf(PrivacyPolicyInfo.ASSET_ES, PrivacyPolicyInfo.ASSET_EN)) {
            for (line in file("app/src/main/assets/$asset").readLines()) {
                val shown = stripInlineMarkdown(line)
                assertFalse("$asset: $shown", shown.contains("](") || shown.contains('`'))
                assertFalse("$asset: ruta interna visible: $shown", shown.contains("app/src/main/assets"))
            }
        }
        for (line in file("app/src/main/assets/${TermsInfo.ASSET_PATH}").readLines()) {
            assertEquals("los términos se leen igual que antes", line, stripInlineMarkdown(line))
        }
        assertTrue(kt("legal/TermsScreens.kt").contains("    var rest = stripInlineMarkdown(text)\n"))
    }

    @Test
    fun `the MP4 export description says personal use, as terms clause 7_3 does`() {
        val res = File(root, "app/src/main/res")
        val overrides = res.listFiles().orEmpty().filter { it.name == "values" || it.name.startsWith("values-") }
            .flatMap { dir -> dir.listFiles().orEmpty().filter { it.extension == "xml" } }
            .filter { it.readText().contains("name=\"export_as_video_desc\"") }
        assertTrue(overrides.isNotEmpty())
        for (f in overrides) {
            val text = f.readText()
            assertFalse("${f.parentFile.name}/${f.name}", text.contains("redes sociales") || text.contains("social networks"))
        }
        assertTrue(file("app/src/main/res/values-es/echo_strings.xml").readText().contains("<string name=\"export_as_video_desc\">MP4 para uso personal (hasta 1080p) — aparece en Vídeos exportados</string>"))
        assertTrue(file("app/src/main/res/values/echo_strings.xml").readText().contains("<string name=\"export_as_video_desc\">MP4 for personal use (up to 1080p) — appears under Exported videos</string>"))
    }
}
