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

## Planes de pago: mensual, trimestral, semestral y anual (2026-11-24, fila 822 del registro)

El dueño activó en el **mismo producto** de Gumroad cuatro periodos de pago, con precios definitivos (todos
**+ impuestos**): mensual **US$3.74**, trimestral **US$10** (≈US$3.33 al mes, 11 % menos), semestral **US$15**
(US$2.50 al mes, 33 % menos) y anual **US$24** (US$2 al mes, 47 % menos). Sin plan de 2 años y sin «paga lo que
quieras». Sigue habiendo un solo enlace (`GUMROAD_URL`): el periodo se elige en la página de Gumroad. La
verificación de la licencia no depende del periodo, así que el Worker y `PRODUCT_ID` no cambian.

| Dónde | Antes | Queda así |
|---|---|---|
| Bienvenida (`hint`) | «…necesitas una suscripción mensual.» | «…necesitas una suscripción: mensual, trimestral, semestral o anual.» |
| Demo vencida (`hint`) | «…necesitas una suscripción mensual. El pago no es…» | «…necesitas una suscripción: mensual, trimestral, semestral o anual. El pago no es…» |
| Alta: lista de planes (nueva) | — | «Mensual: US$3.74 al mes + impuestos» / «Trimestral: US$10 cada 3 meses + impuestos (US$3.33 al mes)» / «Semestral: US$15 cada 6 meses + impuestos (US$2.50 al mes)» |
| Alta: plan anual, destacado (nuevo) | — | «Anual: US$24 al año + impuestos (US$2 al mes, casi a mitad de precio)» |
| Alta: botón de Gumroad | «Suscribirme: US$3.74 al mes + impuestos» | «Suscribirme (eliges el plan al pagar)» |
| Alta: nota bajo el botón | «Se renueva sola cada mes hasta que la canceles…» | «Se renueva sola según el plan que elijas, hasta que la canceles…» (sigue «No hay reembolsos.») |
| Renovación (`hint`) | «Tu suscripción mensual no está activa…» | «Tu suscripción no está activa…» |
| Equipo bloqueado: botón | «Suscribirme (US$3.74 al mes, renovación automática)» | «Suscribirme (desde US$2 al mes con el plan anual, renovación automática)» |

Fuera de esta pantalla: Acerca de ▸ «Suscripción y demo», el `README.md` y los Términos (versión 4, cláusulas
«Lo más importante», 10.5 y el resumen) dicen los cuatro periodos y la renovación al final de cada periodo.
Lo vigila `PaymentScreenTextsGuardTest.kt` (los cuatro precios, el anual destacado y ningún «cada mes»).
