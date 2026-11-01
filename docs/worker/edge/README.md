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
| `GET /v1/pubkey` | cualquiera | la clave pública de verificación |
| `POST /v1/admin/test-alert` | tú | manda un aviso de prueba |
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

## Aviso del vigilante por Telegram (recomendado)
Crea un bot con `@BotFather` (`/newbot`), escríbele un mensaje, y guarda `TELEGRAM_TOKEN` y `TELEGRAM_CHAT` (tu id de chat) con `npx wrangler secret put`. `POST /v1/admin/test-alert` debe responder `"sent":true` y llegarte el mensaje al móvil.

## Correo del vigilante (alternativas)
Sin dominio (lo normal): **ntfy.sh**. `npx wrangler secret put NTFY_TOPIC` con una cadena larga y aleatoria (el tema ES el secreto) y vuelve a desplegar; ntfy reenvía el aviso por correo a `aurahires@gmail.com`. Para comprobarlo: `curl.exe -X POST https://TU-WORKER/v1/admin/test-alert -H "Authorization: Bearer $t"` debe responder `{"sent":true}` y llegar un correo. Con dominio propio, la alternativa es Cloudflare Email Routing (`[[send_email]]` y `ALERT_FROM` en `wrangler.toml`). Sin ninguna de las dos el cambio queda pendiente y se reintenta (no se pierde).

## Clave pública
`GET /v1/pubkey` devuelve la mitad pública de `SIGNING_KEY` (la que va en `EDGE_PUBLIC_KEY`). Es pública por naturaleza.

## Fase C — cachés compartidas
| Ruta | Quién | Qué |
|---|---|---|
| `GET /v1/tags?artist=` | la app | etiquetas de Last.fm del artista: de KV (30 días) o **una** consulta a Last.fm para todos. Solo viaja el nombre del artista. Tope global de 200 consultas nuevas por hora. Necesita el secreto `LASTFM_KEY` (tu clave de la API de Last.fm: `npx wrangler secret put LASTFM_KEY`). |
| `GET /v1/canvas?k=` | la app | portada animada ya encontrada para `al\|artista\|album` (7 días) o `{miss:true}` |
| `PUT /v1/canvas` | la app, solo cuando su cadena **encontró** una | se guarda si el vídeo es `https` de un dominio conocido (apple/mzstatic/tidal/monochrome/boidu) y la portada es **del álbum de esa clave**; la primera respuesta gana; tope 300 escrituras por hora |

Sin respuesta del Worker, la app sigue con su cadena de siempre: estas cachés solo ahorran peticiones y calor.

## Fase D — copia de seguridad cifrada
| Ruta | Qué |
|---|---|
| `PUT /v1/backup/<id>` | guarda una copia (≤ 24 MB, empieza por `AURAB1`); se conservan las **3** últimas y las anteriores se borran de verdad; tope de 60 escrituras por hora |
| `GET /v1/backup/<id>` (`?n=`) / `/list` | la última (o la n) / el índice (tamaño y fecha) |
| `DELETE /v1/backup/<id>` | borra todas las copias de ese id |

`<id>` = SHA-256 de un secreto que solo tiene el usuario (su código de emparejamiento, o licencia + correo) con prefijo propio: no revela el secreto y **no es** el id de la sala de sincronización. La app cifra el ZIP en el móvil (AES-256-GCM, clave PBKDF2 del mismo secreto): el Worker solo guarda bytes que no puede leer. Guarda en **R2** si enlazas un bucket como `BACKUPS` (`[[r2_buckets]] binding = "BACKUPS"` en `wrangler.toml`) y en **KV** si no; la app no cambia.

## Privacidad
Se guarda: la configuración que tú pones, los registros que el usuario decide enviar (7 días) y el último id del reproductor de YouTube. No se guarda IP (solo un contador por hash de IP durante una hora).

## Pruebas
`npm test` (workerd real vía Miniflare). También corren en CI.
