package iad1tya.echo.music.license

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 794: the payment screen texts listed in docs/legal/CAMBIOS_PANTALLA_PAGO.md. Text only: the Gumroad
 * link and the gate logic stay as they were.
 */
class PaymentScreenTextsGuardTest {

    private val src = File("src/main/kotlin/com/music/echo/license/LicenseScreens.kt").readText()

    private val newTexts = listOf(
        "SUBSCRIPTION_PRICE = \"US\$3.74\"",
        "\"Tu demo de 3 días terminó. Para seguir usando Aura necesitas una suscripción mensual. El pago no es para " +
            "escuchar música: sostiene el proyecto y da acceso a la app oficial.\"",
        "\"Prueba gratis 3 días, sin tarjeta y sin cobros automáticos. Después, para seguir usando Aura necesitas una " +
            "suscripción mensual.\"",
        "\"Suscribirme: \$SUBSCRIPTION_PRICE al mes + impuestos\"",
        "\"Se renueva sola cada mes hasta que la canceles (en Gumroad o escribiendo a aurahires@gmail.com). El pago no " +
            "es para escuchar música: sostiene el proyecto y da acceso a la app oficial. No hay reembolsos.\"",
        "\"Tu suscripción mensual no está activa. Renuévala para seguir usando Aura. El pago no es para escuchar " +
            "música: sostiene el proyecto y da acceso a la app oficial.\"",
        "\"Suscribirme (\$SUBSCRIPTION_PRICE al mes, renovación automática)\"",
    )

    private val oldTexts = listOf(
        "SUBSCRIPTION_PRICE = \"\$3.74\"",
        "\"Tu demo de 3 días terminó. Suscríbete para seguir disfrutando de toda la música.\"",
        "\"Prueba gratis 3 días o activa tu suscripción.\"",
        "\"Suscribirme por \$SUBSCRIPTION_PRICE/mes\"",
        "\"Al suscribirte recibes tu licencia para seguir disfrutando de la experiencia.\"",
        "\"Tu suscripción mensual no está activa. Renueva el pago para seguir escuchando.\"",
        "\"Suscribirme (\$SUBSCRIPTION_PRICE/mes)\"",
    )

    @Test fun ownerPaymentTextsArePresent() {
        newTexts.forEach { assertTrue("missing payment text: $it", src.contains(it)) }
    }

    @Test fun replacedPaymentTextsAreGone() {
        oldTexts.forEach { assertFalse("old payment text still present: $it", src.contains(it)) }
    }

    @Test fun longSubscribeLabelsCanWrapWithoutClipping() {
        val helper = src.substringAfter("private fun SecondaryOutlinedButton(").substringBefore("@Composable")
        assertTrue("the button height must be a minimum", helper.contains("Modifier.fillMaxWidth().heightIn(min = height)"))
        assertFalse("a fixed height clips the second line of the label", helper.contains(".height(height)"))
        assertTrue("a wrapped label stays centred", helper.contains("textAlign = TextAlign.Center"))
    }
}
