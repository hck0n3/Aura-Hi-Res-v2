package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 439 (owner 2026-10-18: the charge must read «donación por suscripción» and the terms must tell the user
 * everything needed so the creator is protected). Three things must not drift apart: the in-app wording, the terms text,
 * and the terms VERSION (bumping it is what re-shows the blocking acceptance screen to everyone).
 */
class DonationTermsGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun src(path: String) = File(root, path).also { assertTrue("$path se movió", it.isFile) }.readText()

    private val terms get() = src("app/src/main/assets/legal/TERMINOS_Y_CONDICIONES.md")

    @Test
    fun `the terms version in the asset header matches the constant that gates acceptance`() {
        val constant = Regex("TERMS_VERSION = (\\d+)").find(src("app/src/main/kotlin/com/music/echo/legal/TermsInfo.kt"))!!
            .groupValues[1]
        assertTrue("la cabecera del texto debe decir la misma versión", terms.contains("**Versión de los Términos: $constant**"))
    }

    @Test
    fun `the terms disclose what a paying user and a regulator expect to be told`() {
        val t = terms
        for (needle in listOf(
            "donación por suscripción", // what it is called
            "se renueva automáticamente cada mes", // auto-renewal
            "sin necesidad de indicar ningún medio de pago", // the demo never charges
            "no es deducible de impuestos", // not a tax-deductible gift
            "Derecho de desistimiento", // withdrawal
            "Cancelación", // how to cancel
            "Discontinuación", // what happens if the app ends
            "Menores de edad", // minors paying
            "Datos del servidor de licencias", // controller, retention, rights
            "Cloudflare", // international processors
            "Uso seguro y salud auditiva", // hearing / driving
            "Inteligencia artificial", // AI output disclaimer
            "Versiones beta",
        )) assertTrue("faltan en los términos: $needle", t.contains(needle, ignoreCase = true))
    }

    @Test
    fun `the terms never claim crash reports leave the device while saying the opposite elsewhere`() {
        assertTrue(!terms.contains("reportes anónimos de fallos"))
    }

    @Test
    fun `every screen that names the charge calls it a donation por suscripcion and states the renewal`() {
        val lic = src("app/src/main/kotlin/com/music/echo/license/LicenseScreens.kt")
        assertTrue(lic.contains("Donación por suscripción"))
        assertTrue("el botón ya no debe decir solo «Suscribirme»", !lic.contains("\"Suscribirme"))
        assertTrue("la pantalla debe decir que se renueva sola", lic.contains("Se renueva solo cada mes"))
        assertTrue(src("app/src/main/kotlin/com/music/echo/ui/screens/settings/SettingsScreen.kt").contains("val subscribeText = \"Donación por suscripción\""))
        assertTrue(src("app/src/main/kotlin/com/music/echo/ui/newui/AuraSettingsScreen.kt").contains("val subscribeText = \"Donación por suscripción\""))
    }

    @Test
    fun `the licence logic was not touched by the wording change`() {
        val lic = src("app/src/main/kotlin/com/music/echo/license/LicenseScreens.kt")
        assertTrue(lic.contains("const val SUBSCRIPTION_PRICE = \"\$3.74\""))
        assertTrue(lic.contains("LicenseManager.activateSubscription"))
        assertTrue(lic.contains("LicenseManager.reverify"))
    }
}
