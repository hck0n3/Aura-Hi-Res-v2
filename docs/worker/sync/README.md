# Aura Hi-Res — Worker de sincronización entre dispositivos

Un Worker **aparte** del de la licencia (no lo toca y no usa sus claves). Una sala (Durable Object) por grupo de dispositivos.

## Qué hace
- **Aleatorio sincronizado:** qué canciones de una lista barajada ya sonaron; la unión de todos los dispositivos, con reinicio cuando
  una vuelta completa termina en alguno (`epoch`).
- **Seguir donde lo dejaste (como Spotify Connect):** el dispositivo que suena publica qué suena y en qué segundo; desde otro dispositivo
  se pulsa «Pasar a este dispositivo» o se manda play / pausa / siguiente / anterior al que suena.
- **Aviso de cambios:** «he cambiado mis me gusta / listas»: los otros dispositivos se actualizan al momento.

## Privacidad
- El grupo es el **SHA-256 de un código secreto** que solo conocen tus dispositivos: el Worker no ve el código ni tu cuenta.
- **Biblioteca (me gusta, no me gusta, listas):** guarda ids de canciones, los **nombres de tus listas** y, de cada canción que otro dispositivo pueda no tener, **título, artista y portada**. Es lo que hace falta para mostrarlas en el otro dispositivo; nunca cuentas ni cookies. Límite de 150 000 elementos por grupo; una biblioteca grande de golpe consume escrituras del plan gratuito de Durable Objects (100 000 filas al día), pero solo la primera vez.
- Guarda los ids de canciones del aleatorio, los nombres de tus dispositivos y el estado «sonando ahora», que **sí lleva títulos, artistas y enlaces de portada** (hace falta para mostrar y retomar la cola en otro dispositivo). Nunca cuentas, cookies ni el código.

## Despliegue (una vez, lo haces tú con tu cuenta de Cloudflare)
```bash
cd docs/worker/sync
npm install
npx wrangler login
npx wrangler deploy        # crea el Worker «aura-sync» y su Durable Object (plan gratuito, SQLite)
```
Te da una dirección tipo `https://aura-sync.<tu-usuario>.workers.dev`. En la app: **Ajustes ▸ Sincronizar dispositivos ▸ Servidor**
pegas esa dirección (sin `/sync`) en cada dispositivo, creas el grupo en uno y metes el código en los demás.

Comprobar: `https://aura-sync.<tu-usuario>.workers.dev/health` debe responder `{"ok":true,"lib":3}`. Si responde solo `{"ok":true}`, sigue desplegada una versión vieja que no entiende la sincronización de biblioteca: vuelve a ejecutar `npx wrangler deploy` desde la carpeta con el código nuevo. Con `"lib":2` la biblioteca funciona, pero sin el acuse por operación (fila 900).

## Pruebas
`npm test` ejecuta el Worker de verdad (workerd, vía Miniflare) con varios dispositivos conectados: presencia, unión y reinicio del
aleatorio, estado «sonando ahora», comandos entre dispositivos y aislamiento entre grupos.

## Protocolo (WebSocket JSON, `wss://…/sync?g=<sha256 hex>`)
| Cliente → sala | Respuesta / reenvío |
|---|---|
| `{t:"hello", device:{id,name,kind}}` | `hello_ok {devices, now, nows, serverTime}`; a los demás `devices`. `nows`: el último «now» de cada dispositivo de los últimos 10 min; `now`: el mejor de ellos (el más reciente que suena; si ninguno suena, el más reciente), para las apps que solo conocen ese campo |
| `{t:"shuffle", ctx, epoch, add:[ids], last}` | `shuffle_ack`; a los demás `shuffle` (solo lo nuevo; `reset:true` si cambió la vuelta); `shuffle_full` si el cliente iba atrasado |
| `{t:"shuffle_get"}` | `shuffle_full {items:[{ctx,epoch,ids,last}]}` |
| `{t:"now", state:{queue,index,positionMs,playing,…}}` | a los demás `now`. La sala guarda uno por dispositivo. Un `now` en pausa de un dispositivo que no ha sonado en 10 min, mientras otro suena, se guarda marcado `stale` y no se reenvía ni se entrega |
| `{t:"cmd", to, cmd, args}` | al dispositivo `to`: `cmd {from, cmd, args}` y al que la mandó `cmd_ok {to, cmd}`; si no está: `cmd_fail` |
| `{t:"library", what}` | a los demás `library` |
| `{t:"lib_push", ops:[[key, ts, value, id?]…]}` | `lib_ack {seq, accepted, received, ids}`; a los demás `lib_ops {seq}` (solo la señal). Gana el `ts` más reciente (empate: el valor mayor). Hasta 400 por mensaje. `ids` (fila 900): los `id` de las operaciones con las que la sala ya terminó (aplicadas, más viejas que lo que tiene, repetidas o mal formadas); la app solo da por enviadas esas; el resto (sala llena) lo guarda y no lo vuelve a ofrecer hasta la siguiente conexión o una hora. Con la sala llena el acuse lleva además `full: true`. Una app que manda `[key, ts, value]` sin `id` recibe lo mismo de antes |
| `{t:"lib_pull", since}` | `lib_state {items, seq, head, more, reset}`: lo que cambió desde `since`, en páginas de 400; `reset` si el cliente va por delante de la sala. La sala apunta hasta dónde llegó cada dispositivo (`ld:<id>` = `since`): es su acuse del registro para la limpieza (fila 901) |

**Limpieza (fila 901).** Para que la sala no crezca para siempre, como mucho una vez cada 12 h y solo cuando un `lib_pull` mueve a
algún dispositivo, la sala olvida los **borrados** (`"0"` de `lk:`, `dk:`, `pl:`, `pm:`, `ab:`, `pn:`, y la posición `po:` de la
canción que una lista perdió) que **todos** los dispositivos ya bajaron y que tienen más de 30 días. La primera limpieza de una sala
espera 180 días desde el primer `lib_pull` que la sala apunta (`libpruneSince`): un dispositivo emparejado antes del despliegue no
tiene aún su `ld:` y así tiene una ventana entera para decir hasta dónde llegó. Un dispositivo que no vuelve en 180 días deja de
frenarla: si vuelve, no se le borra nada (el borrado que se perdió simplemente no le llega) y nada vuelve en los demás, porque de cada
borrado olvidado queda una lápida (`t:<key>` = `{ts, v}`, no cuenta para el límite y nunca se entrega) y una operación más vieja que
ella se rechaza (con su `id` en el acuse) en vez de crearse de nuevo.
Las altas, los nombres, las posiciones vivas y el historial (`hv:`) no se tocan. Las apps antiguas siguen igual: mismos mensajes,
mismas formas.
| `{t:"ping"}` | `pong` |
