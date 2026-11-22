# Cambios de texto para la pantalla de pago (`license/LicenseScreens.kt`)

La carpeta `license/` está protegida por el guardián del proyecto (`.claude/hooks/protect-zones.sh`), así que estos
cambios los aplica el dueño a mano. **Solo cambian frases; la lógica de demo, licencia y suscripción no se toca.**

## Cómo aplicarlos desde el móvil (web de GitHub)
1. Abre `https://github.com/hck0n3/Aura-Hi-Res-v2/blob/claude/compassionate-gates-1dotj5/app/src/main/kotlin/com/music/echo/license/LicenseScreens.kt`
2. Pulsa el lápiz (Editar).
3. Busca cada texto de la columna «Ahora» (en el menú ⋯ del editor: *Buscar*) y sustitúyelo por el de «Queda así».
   Copia solo lo que va entre comillas; no toques lo que hay alrededor.
4. Abajo, *Commit changes* en la **misma rama** `claude/compassionate-gates-1dotj5` (no en `main`).

Los números de línea son aproximados; busca por el texto.

| Línea aprox. | Ahora | Queda así |
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

Cuando los hayas pegado, avisa: se comprueba que compila en GitHub y entra en la siguiente beta.
