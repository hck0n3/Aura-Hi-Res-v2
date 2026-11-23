# Cambios de texto para la pantalla de pago (`license/LicenseScreens.kt`)

> **APLICADO (2026-11-23, fila 794 del registro).** El dueño abrió el candado de `license/` (commit `866d026`) y los
> 7 cambios de la tabla están ya en `LicenseScreens.kt`. No hace falta pegarlos a mano. Lo vigila
> `app/src/test/kotlin/iad1tya/echo/music/license/PaymentScreenTextsGuardTest.kt` (textos nuevos presentes, antiguos fuera).

**Solo cambian frases; la lógica de demo, licencia y suscripción no se toca.**

| Línea aprox. | Antes | Queda así |
|---|---|---|
| 70 | `"$3.74"` | `"US$3.74"` |
| 261 | `"Tu demo de 3 días terminó. Suscríbete para seguir disfrutando de toda la música."` | `"Tu demo de 3 días terminó. Para seguir usando Aura necesitas una suscripción mensual. El pago no es para escuchar música: sostiene el proyecto y da acceso a la app oficial."` |
| 263 | `"Prueba gratis 3 días o activa tu suscripción."` | `"Prueba gratis 3 días, sin tarjeta y sin cobros automáticos. Después, para seguir usando Aura necesitas una suscripción mensual."` |
| 410 | `"Suscribirme por $SUBSCRIPTION_PRICE/mes"` | `"Suscribirme: $SUBSCRIPTION_PRICE al mes + impuestos"` |
| 417 | `"Al suscribirte recibes tu licencia para seguir disfrutando de la experiencia."` | `"Se renueva sola cada mes hasta que la canceles (en Gumroad o escribiendo a aurahires@gmail.com). El pago no es para escuchar música: sostiene el proyecto y da acceso a la app oficial. No hay reembolsos."` |
| 437 | `"Tu suscripción mensual no está activa. Renueva el pago para seguir escuchando."` | `"Tu suscripción mensual no está activa. Renuévala para seguir usando Aura. El pago no es para escuchar música: sostiene el proyecto y da acceso a la app oficial."` |
| 486 | `"Suscribirme ($SUBSCRIPTION_PRICE/mes)"` | `"Suscribirme ($SUBSCRIPTION_PRICE al mes, renovación automática)"` |

El enlace de Gumroad (`GUMROAD_URL`, línea ~72) **no se cambia**: si se cambiara el producto, también habría que
cambiar el `PRODUCT_ID` del servidor de licencias.

Se comprueba que compila en GitHub y entra en la siguiente beta.
