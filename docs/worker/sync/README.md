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

Comprobar: `https://aura-sync.<tu-usuario>.workers.dev/health` debe responder `{"ok":true}`.

## Pruebas
`npm test` ejecuta el Worker de verdad (workerd, vía Miniflare) con varios dispositivos conectados: presencia, unión y reinicio del
aleatorio, estado «sonando ahora», comandos entre dispositivos y aislamiento entre grupos.

## Protocolo (WebSocket JSON, `wss://…/sync?g=<sha256 hex>`)
| Cliente → sala | Respuesta / reenvío |
|---|---|
| `{t:"hello", device:{id,name,kind}}` | `hello_ok {devices, now, serverTime}`; a los demás `devices` |
| `{t:"shuffle", ctx, epoch, add:[ids], last}` | `shuffle_ack`; a los demás `shuffle` (solo lo nuevo; `reset:true` si cambió la vuelta); `shuffle_full` si el cliente iba atrasado |
| `{t:"shuffle_get"}` | `shuffle_full {items:[{ctx,epoch,ids,last}]}` |
| `{t:"now", state:{queue,index,positionMs,playing,…}}` | a los demás `now` |
| `{t:"cmd", to, cmd, args}` | al dispositivo `to`: `cmd {from, cmd, args}`; si no está: `cmd_fail` |
| `{t:"library", what}` | a los demás `library` |
| `{t:"ping"}` | `pong` |
