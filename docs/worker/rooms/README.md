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
Cada mensaje es un texto JSON `{"type": …, "payload": {…}}`. Los campos del `payload` se llaman **igual que las clases de la app** (`app/…/listentogether/Protocol.kt`: `CreateRoomPayload`, `PlaybackActionPayload`, `RoomState`…), así que la app solo cambia su códec binario por JSON (`MessageCodec(json = true)`).
- Al abrir, el servidor manda `server_capabilities` (`supportsProtobuf:false`); el `client_capabilities` de la app se acepta sin más.
- Crear: `wss://…/rooms/create` → `create_room {username}` → `room_created {roomCode, userId, sessionToken}` (código de 6 letras).
- Entrar: `wss://…/rooms/join?code=ABC123` → `join_room {roomCode, username}` → el anfitrión recibe `join_request`, aprueba (`approve_join {userId}`) y el invitado recibe `join_approved {roomCode, userId, sessionToken, state}` (con el `RoomState` completo).
- Reconectar: mismo `…/rooms/join?code=…` y `reconnect {sessionToken}` → `reconnected {roomCode, userId, state, isHost}`. El `sessionToken` es `userId.secreto`.
- Reloj: `ping {clientTime, sequence}` → `pong {clientTime, serverReceiveTime, serverSendTime, sequence}`; toda orden de reproducción (`sync_playback`) lleva `serverTime`, `capturedAtServerTime` y `position`.
- Tras `buffer_wait {trackId, waitingFor}` y `buffer_complete {trackId}` el servidor manda a todos un `sync_playback` `play` con la posición actual para arrancar juntos.
- Los errores llevan `code` y un `message` en español para mostrar tal cual.

## Reglas
Solo el anfitrión controla la reproducción; las sugerencias pasan por él; una canción nueva o un salto espera a que todos estén listos (`buffer_wait` → `buffer_ready` → `buffer_complete`, o 10 s); si el anfitrión se cae pasa el mando al presente más antiguo tras 60 s; una sala vacía se borra a los 5 min. Máximo 12 personas, cola de 200, mensajes de 64 KB.
