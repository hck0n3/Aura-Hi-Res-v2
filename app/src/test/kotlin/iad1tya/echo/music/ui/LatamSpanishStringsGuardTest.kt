package iad1tya.echo.music.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Registry row 784: the app seeds «es-419» for Spanish users, and es-419 resolves to `values-es-rUS` BEFORE `values-es`
 * (es-US's parent locale is es-419). `values-es-rUS` held ~420 inherited fork translations that differed from the reviewed
 * `values-es` wording, so every wording review done in `values-es` never reached the owner's screen. Those keys were dropped so
 * es-rUS inherits `values-es`; a key es-rUS still defines must read exactly like `values-es`, or be listed below with its reason.
 */
class LatamSpanishStringsGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    /** es-rUS keys allowed to differ from values-es, each with WHY (here: the values-es text is wrong and es-rUS is right). */
    private val allowedToDiffer = mapOf(
        "string:dislikes" to "values-es says «No me gustas»",
        "string:suggest_to_host" to "values-es says «Sugerir alojar» for «suggest to the host»",
        "string:hosting_room" to "a status line; values-es says «Alojar sala» (an order)",
        "string:resync" to "values-es says «Re sincronizar»",
        "string:tap_to_recognize" to "values-es says «Tap para reconocer»",
        "string:importing_csv" to "a progress title; values-es says «Importar CSV» (an order)",
    )

    /** "tag:name" → its text (plurals and arrays: every item, in order), from every XML file of one values folder. */
    private fun entries(dir: String): Map<String, String> {
        val folder = File(root, "app/src/main/res/$dir")
        assertTrue("$dir se movió", folder.isDirectory)
        val out = LinkedHashMap<String, String>()
        val factory = DocumentBuilderFactory.newInstance()
        folder.listFiles { f -> f.name.endsWith(".xml") }!!.sortedBy { it.name }.forEach { file ->
            val children = factory.newDocumentBuilder().parse(file).documentElement.childNodes
            for (i in 0 until children.length) {
                val el = children.item(i) as? Element ?: continue
                val name = el.getAttribute("name").takeIf { it.isNotEmpty() } ?: continue
                val text = if (el.tagName == "string") {
                    el.textContent.trim()
                } else {
                    val items = el.childNodes
                    (0 until items.length).mapNotNull { items.item(it) as? Element }
                        .joinToString("|") { it.getAttribute("quantity") + "=" + it.textContent.trim() }
                }
                out["${el.tagName}:$name"] = text
            }
        }
        return out
    }

    @Test
    fun `es-rUS defines no key whose text differs from values-es unless it says why`() {
        val es = entries("values-es")
        val latam = entries("values-es-rUS")
        val drifted = latam.filter { (key, text) -> key in es && es[key] != text && key !in allowedToDiffer }
        assertTrue(
            "values-es-rUS shadows values-es for Latin-American Spanish; change values-es and drop (or copy) the es-rUS key: " +
                drifted.keys.joinToString(),
            drifted.isEmpty(),
        )
    }

    @Test
    fun `every allowed exception still exists and still differs`() {
        val es = entries("values-es")
        val latam = entries("values-es-rUS")
        allowedToDiffer.keys.forEach { key ->
            assertTrue("$key: drop it from the allowlist", key in latam && key in es && es[key] != latam[key])
        }
    }
}
