# aura-rooms — «Escuchar juntos» propio (solo entre usuarios de Aura)

Un Durable Object por sala, WebSockets con hibernación, mensajes JSON de texto. Guarda ids y datos de pantalla de las canciones (videoId, título, artista…), nunca audio ni cuentas. Los nombres de los mensajes (`create_room`, `join_request`, `sync_playback`, `buffer_wait`…) son los del protocolo actual de la app para poder enlazar uno con otro.

## Desplegar
```
cd docs/worker/rooms
npm install
npx wrangler deploy
curl https://aura-rooms.<tu-cuenta>.workers.dev/health   # {"ok":true,"rooms":1}
```

## Cómo se conecta
- Crear: `wss://…/rooms/create` → `create_room {username}` → `room_created {roomCode, userId, token}` (código de 6 letras).
- Entrar: `wss://…/rooms/join?code=ABC123` → `join_room {username}` → el anfitrión recibe `join_request`, aprueba (`approve_join`) y el invitado recibe `join_approved {state, users, token}`.
- Reconectar: `reconnect {userId, token}` (el token lo dio el servidor al entrar).
- Reloj: `ping {t0}` → `pong {t0, serverTime}`; toda orden de reproducción lleva `serverTime` y `positionMs`.

## Reglas
Solo el anfitrión controla la reproducción; las sugerencias pasan por él; una canción nueva o un salto espera a que todos estén listos (`buffer_wait` → `buffer_ready` → `buffer_complete`, o 10 s); si el anfitrión se cae pasa el mando al presente más antiguo tras 60 s; una sala vacía se borra a los 5 min. Máximo 12 personas, cola de 200, mensajes de 64 KB.
