# Plan — las 9 funciones del Worker, integradas de forma funcional

> Pedido del dueño (2026-10-30): *«todas las 9 del Worker las quiero, todas me sirven; crea un plan para integrarlo de manera funcional»*.
> **Estado: PLAN. Nada de esto está construido** salvo la sincronización de dispositivos (fila 528, ya en la rama).
> Reglas que mandan sobre todo el plan (AGENTS.md): nada llega a los usuarios sin orden del dueño en ese momento; el paquete
> `license/` y su Worker no se tocan; `eq/` y `cpp/` no se tocan; nada que muestree red o pantalla por fotograma; el registro
> (`AppLogger`) solo lleva recuentos, nunca títulos, ids ni cuerpos.

---

## 0. Lo que ya existe (y condiciona el diseño)

| Pieza | Dónde | Consecuencia para el plan |
|---|---|---|
| Worker de **licencia** (`/verify`, `/demo`, `/ai`) | `docs/worker/worker.js`, URL en `license/LicenseBackendClient.kt` | **No se le añade nada.** Un fallo en una función nueva no puede dejar a nadie sin licencia. Todo lo nuevo va en Workers aparte. |
| Worker de **sincronización** de dispositivos | `docs/worker/sync/` + `devicesync/` | Ya hecho. Es el modelo: Durable Object + protocolo JSON + tests en workerd. |
| `player_configs.json` en `main` (auto-reparación) | repo | La función 2 solo **detecta**; publicar valores sigue siendo manual y verificado. |
| `releaseradar/ReleaseRadarWorker.kt` (WorkManager) | app | La función 9 **extiende** esto; no se crea otro sistema de avisos. |
| `docs/DRIVE_BACKUP_PLAN.md` | docs | La función 5 hereda sus 3 bloqueantes (ZIP sin secretos, sin licencia, `VACUUM INTO`). |
| Servidor público de **Escuchar juntos** (metroserver, protobuf) | `listentogether/` | La función 6 es opcional y convive: el público sigue por defecto. |

## 1. Arquitectura: dos Workers nuevos, no uno

1. **`aura-edge`** (sin estado, KV + R2 + Cron): funciones **1, 2, 3, 4, 5, 8, 9**. Un solo despliegue, rutas `/v1/...`.
2. **`aura-rooms`** (Durable Objects): función **6** (Escuchar juntos propio). Separado porque su protocolo y su coste son otros.
3. **`aura-sync`** (ya existe): función 7 solo añade el tipo de dispositivo «windows»; no cambia el Worker.

Por qué separados: cada uno se despliega, se apaga y se factura por separado, y ninguno comparte fallo con la licencia.

### Cimientos comunes (Fase A — hacerlos una vez, todo lo demás los usa)
- **`edge/EdgeClient.kt`** en la app: un cliente HTTP con (a) URL base desde ajustes con valor por defecto vacío = **todo apagado**,
  (b) tiempo límite corto, (c) caché con `ETag`, (d) sin reintentos agresivos (máx. 1 por arranque), (e) registro solo con
  códigos de estado y recuentos.
- **Firma de la configuración**: el Worker firma `/v1/config` con Ed25519; la app trae la **clave pública** embebida y descarta
  lo que no verifique. Así, aunque alguien tomara el Worker, no podría apagar ni encender nada en los móviles.
- **Límite de uso** por IP en cada ruta (Cloudflare Rate Limiting o contador en KV) y **tope de tamaño** en cada cuerpo.
- **Interruptor propio de cada función** (ver función 1): cada una de las 9 puede apagarse sin publicar versión.
- **Tests** con miniflare/workerd como los de `sync` (se ejecutan en CI) + tests puros en Kotlin para el parseo y la política.
- **Ajustes ▸ «Servicios Aura»**: pantalla única con el estado de cada función y su interruptor local (el usuario siempre puede apagar).
- **Registro de regresiones**: una fila por función (530 en adelante) y una línea en `UI_INVENTORY.md` por cada pantalla/botón nuevo.

> **Decisión del dueño que hace falta ya:** hoy cada usuario pega *su* URL de Worker (así funciona la sync). Para las funciones
> 1, 3, 4, 8 y 9, que son para **todos** los usuarios, la URL tiene que ir **dentro de la app** (`BuildConfig`). Eso es una
> decisión de publicación: mientras tanto se queda en blanco y solo tú la pones en Ajustes para probar.

---

## 2. Las 9 funciones

### 1) Interruptores remotos y avisos  · Fase B · esfuerzo S
- **Worker**: `GET /v1/config` → JSON firmado `{ flags: {cola_inteligente:true, portadas_animadas:true, …}, notice: {id, titulo, texto, hasta}, min_version }`, en KV, `ETag`.
- **App**: `RemoteConfig.kt` — se consulta al abrir (máx. 1 vez cada 6 h) y se guarda la última buena. Sin red o sin firma válida = se usa la última o los valores por defecto (**todo encendido como hoy**).
- **Alcance**: un flag **solo apaga o enciende código que ya está en la app**; nunca descarga ni ejecuta nada.
- **Aviso**: tarjeta descartable en Inicio (una vez por `id`), nunca una ventana que bloquee.
- **Archivos**: `edge/RemoteConfig.kt`, un `if (RemoteConfig.on("…"))` en los puntos de entrada de cada función; no se tocan `license/` ni `eq/`.
- **Prueba de que funciona**: apagar un flag en el Worker y comprobar en un móvil que la función desaparece sin tocar la app.
- **Riesgo**: que un flag mal puesto apague algo importante → los flags tienen lista cerrada y la reproducción y la licencia **no tienen flag**.

### 2) Vigilante de YouTube · Fase B · esfuerzo S
- **Worker**: Cron cada 30 min: descarga la página del player, extrae la URL de `base.js`, guarda su hash en KV; si cambia, **te avisa por correo al buzón de Aura** (`aurahires@gmail.com`, el mismo de `SupportContact`). *Decisión del dueño 2026-10-30.*
- **Cómo se envía el correo**: Cloudflare Email Routing con un Email Worker (`send_email`), que solo puede escribir a una dirección **verificada** en tu cuenta: la verificas una vez desde el panel. Un solo aviso por cambio (no uno cada 30 min) y un resumen si pasa un día sin cambios cuando antes había uno pendiente.
- **App**: nada. Es solo para ti.
- **Límite deliberado**: solo detecta. Los valores `sig`/`n` siguen verificándose ejecutando el `base.js` real antes de publicar en `player_configs.json` (regla absoluta de AGENTS.md).
- **Extra opcional** (después): el Worker guarda el `base.js` nuevo para que lo verifiques sin buscarlo.

### 3) Caché compartida de portadas animadas · Fase C · esfuerzo M
- **Worker**: `GET /v1/canvas?album=<clave>` → `{videoUrl|null, vence}`. Si no lo tiene, **no busca por el usuario**: responde `miss` y la app sigue con su cadena actual; la app luego envía el resultado (`PUT`, solo clave de álbum normalizada + URL de vídeo pública, sin datos del usuario).
- **Token de Apple**: el Worker lo guarda y lo renueva (hoy hay 3 copias en la app: `app/utils`, `canvas`, `applecanvas`); la app deja de buscar el token por su cuenta cuando el Worker responde.
- **App**: un nivel nuevo **antes** de la cadena en `CanvasAlbumMemo`: memoria local → Worker → cadena actual. Si el Worker falla, todo queda como hoy.
- **Calor/batería**: reduce llamadas (una por álbum en todo el mundo). No añade nada por fotograma.
- **Riesgo**: servir una URL de vídeo vencida → `vence` corto (7 días) y la app ya descarta una URL que no reproduce.

### 4) Caché de estilos de Last.fm para la cola inteligente · Fase C · esfuerzo S
- **Worker**: `GET /v1/tags?artist=<nombre normalizado>` → etiquetas. KV 30 días. La clave de Last.fm es pública por diseño; aun así vive como variable del Worker, no en el código.
- **App**: `ArtistTagStyles` pide primero al Worker y, si no responde en ~1,5 s, usa Last.fm directo como hoy.
- **Privacidad**: la consulta lleva solo el **nombre del artista**, igual que ya hace la app contra Last.fm; no lleva usuario ni sesión.
- **Honestidad**: acelera y abarata; **no arregla por sí sola** el caso de «estilo no detectado». Eso depende del `STYLE_GATE_BASIS` que salga del log de la beta 3.

### 5) Copia de seguridad en la nube · Fase D · esfuerzo L
- **Decisión (me la dejaste a mí, 2026-10-30): R2 como destino, con KV como respaldo automático.** R2 es lo correcto para archivos (10 GB gratis, sin cargos de salida), pero Cloudflare pide una tarjeta para activarlo. Por eso el Worker funciona **sin R2 desde el primer día**: si no hay R2 enlazado, guarda copias pequeñas (ajustes, me gusta, listas, hasta ~20 MB) en KV; cuando enlaces R2, las copias grandes (con historial) se activan solas. Nada cambia en la app.
- **Worker**: `PUT/GET /v1/backup/<id>` con R2 (o KV si es pequeño). El `<id>` sale del código de sincronización (hash distinto al del grupo de sync).
- **Cifrado en el móvil**: la app cifra el ZIP con una clave derivada del código; **el Worker solo ve bytes cifrados**.
- **Contenido** (heredado de `DRIVE_BACKUP_PLAN.md`): ZIP **sin secretos** (sin cookie de YouTube ni tokens), **sin licencia**, base de datos con `VACUUM INTO`.
- **Restauración**: en dispositivo nuevo o vacío (sin conflicto que fundir). La fusión entre dispositivos con datos propios queda para una fase posterior, igual que decidiste en el plan de Drive.
- **Convive con Drive**: son dos destinos del mismo ZIP; el usuario elige. No se rehace la lógica del ZIP.
- **Archivos compartidos**: toca `BackupRestoreViewModel.kt` (archivo de alto riesgo del registro) → se lee el registro entero antes y se añade solo un destino, sin cambiar el flujo actual.
- **Tope**: 25 MB por copia, 3 copias por código, borrado permanente de las viejas.

### 6) «Escuchar juntos» en tu propio servidor · Fase E · esfuerzo XL
- **Primero un estudio**: el cliente actual habla el protocolo protobuf de metroserver. Antes de escribir nada se documenta el protocolo exacto a partir de `listentogether/` y se decide si el Worker lo implementa igual (interoperable con SimpMusic/Metrolist) o si usa JSON propio (más simple, solo Aura).
- **Worker `aura-rooms`**: un Durable Object por sala, WebSocket con hibernación, anfitrión + invitados, aprobación, reloj del servidor para la posición.
- **App**: en `ListenTogetherSettings` ya existe «Servidor» (vacío = público). Se añade la opción «Servidor Aura» sin cambiar el valor por defecto.
- **Riesgo**: es la función más cara y la única con tiempo real para muchos usuarios a la vez; por eso va la última de las que cambian la app y detrás del flag de la función 1.

### 7) App de Windows 11 · Fase E · esfuerzo según el plan de Windows
- **Worker**: ningún cambio. El protocolo de `aura-sync` ya es independiente del cliente (`hello` lleva `kind`).
- **Lo que sí**: documentar el protocolo (ya está en `docs/worker/sync/README.md`), añadir el tipo `windows` al listado y reservar los mensajes de «cola completa» para cuando exista el cliente. Se activa cuando salga la Fase 0 de `docs/MULTIPLATAFORMA_WINDOWS.md` (rama `claude/windows-cmp`, a la espera de tu «empieza»).

### 8) Diagnóstico con un toque · Fase B · esfuerzo S
- **Worker**: `POST /v1/diag` (máx. 1 MB, TTL 7 días) → devuelve un **código de caso** corto. `GET /v1/diag/<caso>` solo con tu token de dueño.
- **App**: botón «Enviar registro» en Ajustes ▸ Registros, **solo si el usuario lo pulsa**; sube el mismo `app.log` que hoy se comparte, ya filtrado por `AppLogger` (regla 4), y muestra el código para que lo copie.
- **Sin envío automático nunca.** No hay telemetría en segundo plano.
- **Ventaja para ti**: dejas de recibir `share_all_diagnostics_N.txt` a mano.

### 9) Aviso de lanzamientos de artistas seguidos · Fase D · esfuerzo M
- **Lo que ya hay**: `ReleaseRadarWorker` (WorkManager) y la pantalla Novedades con el radar de artistas seguidos.
- **Con el Worker**: `POST /v1/releases` con una lista corta de ids de artistas seguidos → el Worker responde las novedades de los últimos N días, con caché compartida (un artista se consulta una vez para todos, no una por usuario).
- **Aviso**: notificación local desde el propio Worker de WorkManager, **sin push real** (la app no lleva Firebase y no se añade). Una consulta cada 12 h, solo con red y batería no baja.
- **Privacidad**: sin cuenta, sin identificador; la lista de artistas viaja en la petición y no se guarda.
- **Riesgo**: spam de avisos → máximo 1 notificación agrupada por día y apagable en Ajustes.

---

## 3. Orden de construcción

| Fase | Qué | Por qué en este orden |
|---|---|---|
| **A** | Cimientos: `EdgeClient`, firma Ed25519, límites, pantalla «Servicios Aura», CI del Worker | Todo lo demás depende de esto. |
| **B** | Funciones **1, 2, 8** | Baratas, sin riesgo para la reproducción y te dan control (apagar, enterarte, recibir logs) antes de añadir más. |
| **C** | Funciones **3, 4** | Cachés con respaldo: si el Worker falla, la app sigue como hoy. |
| **D** | Funciones **9, 5** | Más superficie (notificaciones, copia cifrada, archivo de alto riesgo). |
| **E** | Funciones **6, 7** | Las más grandes; dependen de estudios y del plan de Windows. |

Cada fase termina en rama, con tests verdes en CI y filas en el registro. **Ninguna llega a los usuarios hasta que tú lo ordenes.**

## 4. Coste y límites (a vigilar)
- Plan gratuito de Workers: ~100 000 peticiones/día; KV ~100 000 lecturas/día; los WebSockets de Durable Objects se facturan por tiempo conectado. Con cachés (3, 4, 9) y consulta cada 6–12 h, una beta privada cabe de sobra; con muchos usuarios convendrá el plan de pago (unos 5 USD/mes).
- R2 (función 5) necesita activarse en tu cuenta de Cloudflare.
- Cada función incluye su **medida de coste** en el registro: peticiones por usuario y día.

## 5. Decisiones del dueño y lo que sigue
**Contestadas (2026-10-30):**
1. Vigilante de YouTube → **correo al buzón de Aura** (función 2).
2. Copias → **R2 con respaldo en KV** (función 5; decido yo, ver arriba).

**Siguen abiertas (no bloquean la Fase A):**
1. **URL por defecto** del Worker para las funciones de todos los usuarios: ¿en blanco y la pones tú en Ajustes mientras pruebas?
2. **Escuchar juntos propio (función 6):** ¿compatible con el protocolo actual (más trabajo) o solo entre usuarios de Aura (más simple)?
3. ¿Empiezo por las fases A y B cuando termine la beta 3?

## 6. Licencia en varios equipos (fuera de las 9, pedido el 2026-10-30)
Hecho en la rama como fila 530 (ver el registro). Es **el único cambio en `license/` y en su Worker**, pedido expresamente por el dueño; sigue sin tocarse el modo demo, la clave maestra ni el periodo de gracia.

## 7. Qué NO cubre este plan (a propósito)
- Cualquier OTRO cambio en `license/` o en su Worker.
- Publicar `player_configs.json` o ninguna versión: eso sigue siendo orden tuya en ese momento.
- Registrar lo que escucha cada usuario: ninguna función lo necesita.
