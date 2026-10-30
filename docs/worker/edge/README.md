# aura-edge — Worker «edge» de Aura Hi-Res

Tres funciones del plan (`docs/PLAN_WORKER_9_FUNCIONES.md`): **1** interruptores remotos y avisos, **2** vigilante de YouTube, **8** diagnóstico con un toque. Sin cuentas ni identificadores de usuario. **Nunca** manda código a los móviles: un interruptor solo apaga o enciende código que la app ya tiene.

## Desplegar (una vez)
```
cd docs/worker/edge
npm install
npx wrangler kv namespace create EDGE        # pega el id en wrangler.toml
npm run keygen                               # imprime la clave PRIVADA y la PÚBLICA
npx wrangler secret put SIGNING_KEY          # pega la PRIVADA
npx wrangler secret put ADMIN_TOKEN          # una cadena larga que solo tú sabes
npx wrangler deploy
```
La clave **pública** va a la app (`EDGE_PUBLIC_KEY` en `edge/EdgeConfig.kt`; para probar, se puede pegar en Ajustes ▸ Servicios Aura). La privada no se commitea nunca.

## Rutas
| Ruta | Quién | Qué |
|---|---|---|
| `GET /v1/config` | la app | `{payload, sig}`; la app verifica `sig` (ECDSA P-256) y descarta lo que no verifique. `ETag`, 304. |
| `PUT /v1/admin/config` | tú (`Authorization: Bearer ADMIN_TOKEN`) | `{flags:{letras:false}, notice:{id,titulo,texto,hasta}, minVersion}`. Solo se guardan los flags de la lista cerrada. |
| `POST /v1/diag` | la app, **solo si el usuario pulsa «Enviar registro»** | cuerpo ≤ 1 MB, 7 días, 6 por IP y hora → `{code}` |
| `GET /v1/diag/<code>` | tú | el registro |
| cron `*/30` | — | mira si YouTube cambió el id del reproductor y avisa **una vez** por correo |

Ejemplo para apagar una función sin publicar versión:
```
curl -X PUT https://TU-WORKER/v1/admin/config -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"flags":{"letras":false},"notice":{"id":"mant-1","titulo":"Aviso","texto":"Mantenimiento esta noche","hasta":0}}'
```
Flags válidos: `cola_inteligente, portadas_animadas, pedir_musica, letras, sync_dispositivos, sync_biblioteca, radar_lanzamientos, copia_nube`. **La reproducción y la licencia no tienen flag.**

## Correo del vigilante
Cloudflare Email Routing: verifica `aurahires@gmail.com` una vez en el panel y descomenta `[[send_email]]` y `ALERT_FROM` en `wrangler.toml`. Sin eso el cambio queda pendiente y se reintenta en la siguiente pasada (no se pierde).

## Privacidad
Se guarda: la configuración que tú pones, los registros que el usuario decide enviar (7 días) y el último id del reproductor de YouTube. No se guarda IP (solo un contador por hash de IP durante una hora).

## Pruebas
`npm test` (workerd real vía Miniflare). También corren en CI.
