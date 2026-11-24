# Vigilante de SimpMusic

Orden del dueño (2026-11-24), literal: *«quiero que cuando falle, si hay un vigilante, que tome la solución de
SimpMusic»*. Aura usa los mismos extractores que SimpMusic (PipePipeExtractor y BravePipeExtractor de
maxrave-dev), así que cuando SimpMusic saca un arreglo de reproducción, lo normal es que el mismo arreglo sirva aquí.
Este vigilante **solo avisa**: dice qué cambió en SimpMusic y en qué archivos de Aura vive lo mismo. No toca el
código, no escribe `player_configs.json` y no publica nada.

## Cómo se usa

```bash
python3 -I scripts/simpmusic_watch.py                 # imprime un JSON; "changed": true si hay algo nuevo
python3 -I scripts/simpmusic_watch.py --update-state  # además apunta lo visto en simpmusic_seen.json
cd scripts && python3 -m unittest test_simpmusic_watch -v
```

Solo usa la biblioteca estándar de Python y `git`. Clona sin blobs (`--filter=blob:none --no-checkout`) en una
carpeta temporal que se borra al terminar, así que no descarga los APK de `simpmusic-files`. Sale con código 0 aunque
haya novedades (se leen en `"changed"`) y con 2 si no pudo leer algún repositorio (`"errors"`); con errores,
`--update-state` no escribe nada.

`simpmusic_seen.json` guarda lo último **visto**: el commit de SimpMusic, el de `core` que fija su submódulo, los
pines de los dos extractores y el commit del registro de `simpmusic-files`. Que algo esté visto no quiere decir que
Aura ya lo tenga: por eso S1 compara también con los pines de Aura (`aura_differs`).

## Qué vigila

| Señal | Dónde | Qué cuenta como novedad |
|---|---|---|
| **S1** | `gradle/libs.versions.toml` de `maxrave-dev/SimpMusic` (`pipepipe`, `brave-newpipe`) | Un pin distinto del visto (`since_seen`) o del de Aura (`aura_differs`, contra `pipepipeextractor`/`bravenewpipeextractor` de nuestro `gradle/libs.versions.toml`) |
| **S2** | `maxrave-dev/core`, en el commit que fija el submódulo `core` de SimpMusic | Commits y diff, desde el commit visto, que tocan los archivos de reproducción de la tabla de abajo |
| **S3** | `registry/player_configs.json` de `maxrave-dev/simpmusic-files` | Reproductores (id y alias) que nuestro `player_configs.json` no tiene: `new_since_seen` (los que su registro ha ganado desde el commit visto; son los que marcan `"changed"`) y el atraso total (`backlog_count`, con los más nuevos en `backlog_newest`) |

## De SimpMusic a Aura

Rutas de `core` relativas a `service/kotlinYtmusicScraper/src/` (salvo la última).

| SimpMusic (`core`) | Qué hace allí | Dónde vive lo mismo en Aura |
|---|---|---|
| `commonMain/…/YouTube.kt` (`player()`, `newPipePlayer`) | Pide `/player`, elige cliente y cae al extractor | `app/…/utils/YTPlayerUtils.kt` (`playerResponseForPlaybackImpl`, `resolvePlaybackData`, `newPipeAudioRescue`) e `innertube/…/YouTube.kt` |
| `commonMain/…/Ytmusic.kt` | Cliente HTTP de InnerTube | `innertube/…/InnerTube.kt` |
| `commonMain/…/models/YouTubeClient.kt` | Identidades de cliente (versión, user agent) | `innertube/…/models/YouTubeClient.kt` y la elección de cliente en `YTPlayerUtils.kt` |
| `androidMain/…/extractor/` (`Extractor.android.kt`, `NewPipeUtils.kt`, `BraveNewPipeUtils.kt`) | Uso de PipePipe y del respaldo BravePipe | `innertube/…/pages/NewPipe.kt`, `innertube/…/pages/BraveNewPipe.kt` |
| `androidMain/…/extractor/FaradayJsDecoder.kt` | Decodificador local registrado en PipePipe | `app/…/utils/cipher/PipePipeLocalCipherDecoder.kt` |
| `commonMain/…/cipher/` (`FaradayCipherEngine`, `QuickJsEngine`, `ZemerCipherSolver`) | Resolver `sig`/`n` en el móvil | `app/…/utils/cipher/CipherWebView.kt`, `CipherDeobfuscator.kt`, `PlayerJsFetcher.kt`, `app/…/utils/sabr/EjsNTransformSolver.kt` |
| `commonMain/…/cipher/RemotePlayerConfig*`, `PlayerConfigRepository.kt` | Tabla remota de reproductores | `app/…/utils/cipher/RemotePlayerConfig.kt` y `player_configs.json` |
| `commonMain/assets/po_token.html` | Generar el PoToken | `app/…/utils/potoken/` |
| `data/…/repository/StreamRepositoryImpl.kt` | Orquesta la resolución del stream | `YTPlayerUtils.kt` (`resolvePlaybackData`) |

`app/…` es `app/src/main/kotlin/com/music/echo/` e `innertube/…` es `innertube/src/main/kotlin/com/music/innertube/`.
La misma tabla está en `AURA_MAP` dentro del script (cada commit de S2 sale con sus `aura_targets`), y un test
comprueba que los archivos de Aura que nombra existen.

## Reglas al portar un arreglo

- **Licencia.** SimpMusic y su `core` son GPL-3.0, igual que Aura: se puede adaptar su código, **conservando la
  autoría**. En el commit, `Adapted from maxrave-dev/<repo>@<sha> (GPL-3.0)`, y el aviso de copyright si se copia
  un trozo literal.
- **Todo cambio de código pasa por la skill `cirujano`** y por `docs/REGRESSION_REGISTRY.md`: lo que funciona en
  SimpMusic puede revertir un guardián de Aura (por ejemplo, la fila 337 sobre cuándo se registra el decodificador
  local).
- **Subir un extractor** es tocar `gradle/libs.versions.toml`: hay que regenerar los `gradle.lockfile` con
  `test-build.yml` (`write_locks`), nunca a mano. Sin eso, todo build de CI falla.
- **⛔ S3 son pistas, no valores.** Jamás se publican valores de `player_configs.json` sin verificar (regla
  absoluta de `AGENTS.md`). Cada id de S3 entra **solo en la rama**, y solo después de pasar
  `scripts/verify/player_verify.mjs` contra el `base.js` real. Llevarlo a `main` (que la app lee en vivo para todos
  los usuarios) o sacar una versión estable es **solo por orden del dueño**, en ese momento y con esas palabras.
