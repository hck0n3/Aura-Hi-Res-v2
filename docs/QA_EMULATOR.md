# Pruebas automáticas en emulador (GitHub)

> Delegado por el dueño el 2026-11-23 (`docs/plan/PENDIENTES_2026-11-23.md`, sección G): *«el agente añade pruebas
> automáticas en un emulador de GitHub; solo lo que exige hardware real queda para el dueño, en una lista mínima»*.
> Registro de regresiones: filas **780** y **781**.

## Qué es y cómo se lanza

`.github/workflows/emulator-smoke.yml` arranca un emulador Android (x86_64, imagen `google_apis`, API 35 o 36) en un
runner de GitHub con KVM, instala la app y le hace las pruebas que hasta ahora el dueño hacía a mano en su móvil.

- **Solo se lanza a mano**: GitHub ▸ Actions ▸ *Emulator smoke (debug, no release)* ▸ *Run workflow*. Nunca corre
  en un push ni en un pull request.
- **No publica nada**: permisos `contents: read`, ningún secret, ninguna release, ningún aviso. Solo sube un
  artefacto (`emulator-smoke-results`) con resultados, registros y capturas.
- **No cambia la app**: prueba el APK de **depuración** sin la puerta de suscripción (`-Pnosub=true`, paquete
  `iad1tya.aura.music.dev.debug`), firmado con la clave de depuración del propio runner. La keystore de release
  no se toca. El módulo de pruebas (`smoketest/`) solo existe cuando una compilación lo pide con
  `-PauraEmulatorSmoke=true` (`settings.gradle.kts`): la app publicada, la release y los tests unitarios se compilan
  exactamente igual que antes.
- **Batería y calor (regla 7)**: nada de esto está dentro de la app; no añade ni un ciclo en el móvil de nadie.

Opciones al lanzarlo:

| Opción | Por defecto | Qué hace |
|---|---|---|
| `previous_ref` | vacío | Versión ANTERIOR para la prueba de actualización. Vacío = el último tag `v*` anterior al commit; si no hay, el merge-base con `main`. |
| `api_level` | 35 | Android del emulador (35 o 36). |
| `run_update` | sí | Compila también la versión anterior y prueba la actualización. |
| `run_streaming` | sí | Intenta una canción de YouTube (puede fallar, ver escenario 6). |
| `stream_video_id` | `_KZEkEb_dvA` | Id de la canción para el escenario 6 (el mismo id real que usa `ShareLinksTest`). |

## Qué comprueba

Cada escenario es una clase de `smoketest/src/main/java/iad1tya/echo/music/smoketest/`. Las pruebas manejan la app
**desde fuera** (como un usuario): UI Automator para la pantalla, un `MediaController` conectado a la sesión de
medios de la app (la misma puerta que usan la notificación, el Bluetooth y Android Auto) para saber qué suena, y
comandos de `adb` para el aparato. Los textos se leen de los recursos de la propia app, así que no dependen del
idioma del emulador.

| # | Escenario | Clase | Pasa si… |
|---|---|---|---|
| 1 | **Arranque en frío** (primera vez tras instalar) | `ColdStartTest` | `am start` termina bien, se atraviesa la bienvenida (los «Omitir» / «Siguiente: YouTube Music» / «Comenzar» del onboarding, el tour y el aviso de enlaces) y se llega a Inicio con su barra, sin cierre ni ANR. |
| 2 | **Navegación** | `NavigationTest` | Biblioteca (con su tarjeta «Local»), Buscar (con su campo de texto), Novedades y Ajustes (cog de arriba ▸ «Ajustes» ▸ lista con «Apariencia») abren, se ven y se vuelve atrás con la app viva. |
| 3 | **Música local** | `LocalPlaybackTest` | Un tono de 90 s que el workflow deja en `/sdcard/Music` lo importa el **escaneo de la propia app** (Biblioteca ▸ Local ▸ «Escanear dispositivo»), suena al tocarlo, la sesión dice `STATE_READY` + `isPlaying` y la posición avanza (≥ 4,5 s en 7 s). |
| 4 | **Sin conexión** | `OfflinePlaybackTest` | Con el tono sonando se cortan modo avión + Wi-Fi + datos (y se **comprueba** que el emulador se quedó sin red; si no, la prueba falla en vez de aprobar en falso); la misma canción sigue sonando y avanza ≥ 7 s en 10 s, sin cierres. La red vuelve siempre al terminar. |
| 5 | **Actualizar sin perder datos** | `UpdateSurvivalTest` | Se instala la versión ANTERIOR, se crea la lista «Aura smoke playlist» desde la Biblioteca, se instala la nueva encima (`adb install -r`, misma firma) y la lista sigue en Biblioteca ▸ Listas. Además se copia la base de datos (`song.db`) antes y después: la lista tiene que estar en las dos y la versión de la base tiene que ser la nueva (la migración corrió). |
| 6 | **Streaming** (puede fallar) | `StreamingSmokeTest` | La canción `stream_video_id`, abierta por el enlace `music.youtube.com/watch?v=…` de la propia app, llega a sonar y avanza. **Permitido fallar**: YouTube suele rechazar las IP de centros de datos; un fallo sale como `WARN`, no pone el job en rojo. |

Además, en cada fase se lee el logcat: **cualquier cierre (`FATAL EXCEPTION`, también de sus subprocesos) o ANR del
paquete de la app** cuenta como fallo de la versión nueva (en la fase de la versión anterior solo avisa).

El job queda en **rojo** si falla cualquiera de los escenarios 1-5 o aparece un cierre/ANR de la versión nueva.

## Dónde mirar el resultado

En la ejecución, el resumen (*Summary*) trae la tabla de escenarios. El artefacto `emulator-smoke-results` contiene:

- `summary.md`, `results.txt`: la misma tabla.
- `instrument-<escenario>.txt`: la salida de cada prueba (el motivo exacto del fallo).
- `screen-<escenario>.png` y `captures/` (capturas y árbol de la pantalla en el momento de cada paso y de cada fallo).
- `logcat-<fase>.txt`, `crashes-<fase>.txt`, `smoke-steps.txt` (los pasos que fue dando la prueba, etiqueta `AuraSmoke`).
- `app-<fase>.log`: el `app.log` de la app (el mismo que el dueño comparte desde Ajustes ▸ Registros).
- `db-before-update/`, `db-after-update/`: las copias de la base para el escenario 5.
- `media_session-*.txt`: `dumpsys media_session` (estado de la sesión de medios al terminar cada fase).
- `smoketest-gradle.lockfile`: las versiones exactas que usó el módulo de pruebas (ver «Pendiente» abajo).

## Lo que el emulador NO puede probar (lista mínima para el dueño, en su móvil)

| Qué | Por qué no |
|---|---|
| **Bluetooth** (auriculares, coche por BT, botones, reanudar al conectar) | El emulador no tiene radio Bluetooth. |
| **Dos aparatos** (control remoto, sincronizar dispositivos, Escuchar juntos) | Un solo emulador por job; hace falta un segundo aparato real con la misma cuenta. |
| **Plegables** (posturas del Fold, bisagra, abrir/cerrar con música) | Las posturas del emulador no reproducen el hardware ni la capa de Samsung. |
| **Batería, Doze y el «matador» de cada fabricante** (Samsung, Xiaomi…) | El emulador no aplica las restricciones de segundo plano de cada OEM ni el consumo real. |
| **Android Auto** | Necesita la app de Android Auto y una unidad (o el DHU) conectada. |
| **Cast** | El sabor GMS y un Chromecast en la misma red; el emulador prueba el sabor FOSS. |
| **Ecualizador Superpowered** | La clave de licencia es un secret que este workflow no usa a propósito: el EQ sale como «no disponible». |
| **La release firmada** (R8, certificado `CN=Aura Hi-Res v2`, que YouTube resuelva con ESE certificado) | Aquí se prueba la build de depuración; la regla de `AGENTS.md` (confirmar en el móvil que el certificado vigente resuelve streams) sigue igual. |
| **Calidad de sonido real** (offload, bit-perfect, volumen seguro, transiciones al oído) | El audio del emulador no es el DAC del teléfono. |
| **Cuenta de Google / YouTube Music** (iniciar sesión, sincronizar biblioteca) | El emulador no tiene la cuenta del dueño ni debe tenerla. |

## Limitaciones conocidas (honestas)

- **`[NO VERIFICADO]` hasta la primera ejecución en GitHub.** El agente que lo escribió no tiene KVM ni puede llegar a
  YouTube, y por norma no corre Gradle en el contenedor compartido. Lo comprobado aquí: las pruebas Java compilan
  contra `android.jar` 36 y las librerías exactas que declara `smoketest/build.gradle.kts`; las herramientas del
  runner (`scripts/emulator_smoke/`) tienen sus tests (`test_smoke_tools.py`) y el guion se ensayó con un `adb`
  simulado. Lo que solo se sabe en GitHub: que AGP 9.4.1 configura el módulo `com.android.test` auto-instrumentado,
  que el emulador reproduce audio con el backend sin sonido del runner, y los tiempos.
- **La primera vez puede fallar por la propia prueba**, no por la app (un texto que no aparece donde se esperaba, un
  tiempo corto). La captura y el árbol de la pantalla del artefacto dicen cuál de las dos.
- La posición que da un `MediaController` se extrapola entre actualizaciones de la sesión; por eso las ventanas son
  de 7-10 s y exigen avanzar casi todo ese tiempo: un reproductor parado no las pasa por extrapolación.
- El escenario 5 depende de que la versión anterior compile en el runner (tag antiguo con dependencias que ya no
  existan, por ejemplo). Si no compila, el escenario sale en rojo con ese motivo y los demás siguen.
- El onboarding usa textos en español escritos en el código («Omitir», «Comenzar»…): si cambian, hay que cambiar
  `AuraDevice.settleOnHome` a la vez.

## Pendiente (decisión del dueño)

- **Fijar las versiones del módulo de pruebas.** Todo el repo usa `gradle.lockfile` (HALLAZGO-011). El módulo
  `smoketest/` todavía no tiene el suyo: el workflow lo genera en su copia desechable (`--write-locks`) y lo sube en el
  artefacto (`smoketest-gradle.lockfile`). Para fijarlo basta con copiarlo a `smoketest/gradle.lockfile` y quitar
  `--write-locks` del paso «Build the smoke APK». La app y la release siguen validando sus locks como siempre.
