package iad1tya.echo.music.license

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 794 and 822: the payment screen texts listed in docs/legal/CAMBIOS_PANTALLA_PAGO.md. Text only: the
 * Gumroad link and the gate logic stay as they were. Row 822 (owner, 2026-11-24): the same Gumroad product sells four
 * billing periods, so no visible text may promise a monthly-only renewal.
 */
class PaymentScreenTextsGuardTest {

    private val src = File("src/main/kotlin/com/music/echo/license/LicenseScreens.kt").readText()
    private val about = File("src/main/kotlin/com/music/echo/ui/screens/settings/AboutScreen.kt").readText()
    private val terms = File("src/main/assets/legal/TERMINOS_Y_CONDICIONES.md").readText()
    private val termsInfo = File("src/main/kotlin/com/music/echo/legal/TermsInfo.kt").readText()

    private val newTexts = listOf(
        "SUBSCRIPTION_PRICE = \"US\$3.74\"",
        "QUARTERLY_SUBSCRIPTION_PRICE = \"US\$10\"",
        "SEMIANNUAL_SUBSCRIPTION_PRICE = \"US\$15\"",
        "YEARLY_SUBSCRIPTION_PRICE = \"US\$24\"",
        "\"Tu demo de 3 días terminó. Para seguir usando Aura necesitas una suscripción: mensual, trimestral, semestral " +
            "o anual. El pago no es para escuchar música: sostiene el proyecto y da acceso a la app oficial.\"",
        "\"Prueba gratis 3 días, sin tarjeta y sin cobros automáticos. Después, para seguir usando Aura necesitas una " +
            "suscripción: mensual, trimestral, semestral o anual.\"",
        "\"Mensual: \$SUBSCRIPTION_PRICE al mes + impuestos\\n\" +",
        "\"Trimestral: \$QUARTERLY_SUBSCRIPTION_PRICE cada 3 meses + impuestos (US\$3.33 al mes)\\n\" +",
        "\"Semestral: \$SEMIANNUAL_SUBSCRIPTION_PRICE cada 6 meses + impuestos (US\$2.50 al mes)\"",
        "\"Anual: \$YEARLY_SUBSCRIPTION_PRICE al año + impuestos (US\$2 al mes, casi a mitad de precio)\"",
        "\"Suscribirme (eliges el plan al pagar)\"",
        "\"Se renueva sola según el plan que elijas, hasta que la canceles (en Gumroad o escribiendo a " +
            "aurahires@gmail.com). El pago no es para escuchar música: sostiene el proyecto y da acceso a la app " +
            "oficial. No hay reembolsos.\"",
        "\"Tu suscripción no está activa. Renuévala para seguir usando Aura. El pago no es para escuchar " +
            "música: sostiene el proyecto y da acceso a la app oficial.\"",
        "\"Suscribirme (mensual, trimestral, semestral o anual: US\$24 al año, renovación automática)\"",
    )

    private val oldTexts = listOf(
        // Replaced by row 794.
        "SUBSCRIPTION_PRICE = \"\$3.74\"",
        "\"Tu demo de 3 días terminó. Suscríbete para seguir disfrutando de toda la música.\"",
        "\"Prueba gratis 3 días o activa tu suscripción.\"",
        "\"Suscribirme por \$SUBSCRIPTION_PRICE/mes\"",
        "\"Al suscribirte recibes tu licencia para seguir disfrutando de la experiencia.\"",
        "\"Tu suscripción mensual no está activa. Renueva el pago para seguir escuchando.\"",
        "\"Suscribirme (\$SUBSCRIPTION_PRICE/mes)\"",
        // Replaced by row 822 (monthly-only wording).
        "\"Suscribirme: \$SUBSCRIPTION_PRICE al mes + impuestos\"",
        "\"Suscribirme (\$SUBSCRIPTION_PRICE al mes, renovación automática)\"",
        "Tu suscripción mensual no está activa.",
        "necesitas una suscripción mensual",
        // Replaced by row 822 (27bdb48: the yearly plan is billed US$24 a year).
        "\"Suscribirme (desde US\$2 al mes con el plan anual, renovación automática)\"",
    )

    @Test fun ownerPaymentTextsArePresent() {
        newTexts.forEach { assertTrue("missing payment text: $it", src.contains(it)) }
    }

    @Test fun replacedPaymentTextsAreGone() {
        oldTexts.forEach { assertFalse("old payment text still present: $it", src.contains(it)) }
    }

    @Test fun theYearlyPlanIsHighlightedAndThereIsStillOneGumroadLink() {
        val yearly = src.substringAfter("text = \"Anual: ").substringBefore(")\n")
        assertTrue("the yearly plan is the highlighted one", yearly.contains("color = AuraPalette.Teal"))
        assertTrue(yearly.contains("fontWeight = FontWeight.SemiBold"))
        assertEquals("one Gumroad product link", 1, Regex("GUMROAD_URL = ").findAll(src).count())
        assertEquals("the same three Gumroad buttons as before", 3, Regex("openGumroad\\(context\\)").findAll(src).count())
    }

    @Test fun noVisibleTextPromisesAMonthlyOnlyRenewal() {
        val monthlyOnly = Regex("suscripción (de pago )?mensual(?!, trimestral)")
        for ((name, text) in listOf("LicenseScreens.kt" to src, "AboutScreen.kt" to about, "TERMINOS_Y_CONDICIONES.md" to terms)) {
            assertFalse("$name: renewal «cada mes»", text.contains("cada mes"))
            assertFalse("$name: monthly-only subscription", monthlyOnly.containsMatchIn(text))
        }
        assertTrue(about.contains("después suscripción mensual, trimestral, semestral o anual, que se contrata fuera de la app"))
    }

    @Test fun theTermsListTheFourPeriodsAndAskForAcceptanceAgain() {
        assertTrue(terms.contains("**Versión de los Términos: 4**"))
        assertTrue(termsInfo.contains("const val TERMS_VERSION = 4"))
        assertTrue(
            terms.contains(
                "**mensual, USD 3.74 al mes; trimestral, USD 10 cada 3 meses; semestral, USD 15 cada 6 meses; anual, USD 24 al año**",
            ),
        )
        assertTrue(
            terms.contains(
                "**La suscripción es periódica: mensual, trimestral, semestral o anual, según el plan elegido en la página " +
                    "de compra; se renueva automáticamente al final de cada período, con un cargo al mismo método de pago, " +
                    "hasta que usted la cancele.**",
            ),
        )
        assertTrue(terms.contains("Los precios vigentes se muestran en la Aplicación y en la página de compra de Gumroad antes de pagar"))
    }

    @Test fun longSubscribeLabelsCanWrapWithoutClipping() {
        val helper = src.substringAfter("private fun SecondaryOutlinedButton(").substringBefore("@Composable")
        assertTrue("the button height must be a minimum", helper.contains("Modifier.fillMaxWidth().heightIn(min = height)"))
        assertFalse("a fixed height clips the second line of the label", helper.contains(".height(height)"))
        assertTrue("a wrapped label stays centred", helper.contains("textAlign = TextAlign.Center"))
    }
}
