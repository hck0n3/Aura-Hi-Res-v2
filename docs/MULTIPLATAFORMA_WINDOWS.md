# Aura Hi-Res en Windows 11 — plan por fases (Compose Multiplatform)

> Pedido del dueño (2026-10-29): la misma app en Windows 11, **visualmente idéntica y con todas sus funciones**, hecha
> por fases; lo que no sea compatible se investiga hasta encontrar una alternativa; y **un cambio hecho una vez llega a
> Android y a Windows** (y al revés), sin mantener dos copias.
>
> Estado: **plan, sin código todavía.** Nada de esto se publica ni llega a los móviles. Rama: `claude/windows-cmp`.

## 1. La regla que lo hace posible: una sola fuente

Hoy la app entera vive en el módulo `app` (Android). El objetivo es esta forma:

```
shared/            ← código común (commonMain): interfaz Compose, ViewModels, lógica, base de datos, red
  commonMain/        todo lo que se ve y se decide
  androidMain/       lo que solo existe en Android (ExoPlayer, MediaSession, widgets…)
  desktopMain/       lo que solo existe en Windows (motor de audio, SMTC, bandeja…)
androidApp/        ← cáscara fina: MainActivity, manifiesto, servicio (usa shared)
desktopApp/        ← cáscara fina: ventana de Windows, instalador (usa shared)
```

Consecuencia directa: **un cambio en `shared/commonMain` se compila para las dos plataformas.** Solo lo que está en
`androidMain` / `desktopMain` se hace dos veces, y esa parte debe ser lo más pequeña posible (el reproductor, el
ecualizador, las integraciones del sistema). Cada fila nueva del registro de regresiones debe decir en qué capa vive.

## 2. Lo que hay hoy (medido sobre el código, 2026-10-29)

`app/src/main/kotlin/com/music/echo`, 795 archivos Kotlin, ~264 000 líneas; 358 archivos usan Compose.

| Paquete | Archivos | Con `android.*` | Con Media3 | Con Hilt | Con Compose | Lectura |
|---|---:|---:|---:|---:|---:|---|
| `ui` | 339 | 114 | 48 | 6 | 310 | Lo más grande y lo que más importa que sea idéntico. Gran parte pasa tal cual; hay que quitar `Context`, `Toast`, `Intent`… |
| `utils` | 97 | 55 | 6 | 5 | 9 | Mezcla: lo puro sube, lo de Android se queda |
| `playback` | 61 | 17 | 30 | 9 | 0 | Motor de reproducción: se parte en **interfaz común** + implementación por plataforma |
| `db` | 41 | 2 | 0 | 1 | 17 | Room 2.8 ya es multiplataforma → sube casi entero |
| `viewmodels` | 40 | 28 | 1 | 38 | 7 | Dependen de Hilt → se pasa a una inyección común (Koin) |
| `eq` | 23 | 9 | 6 | 3 | 0 | Ecualizador/Superpowered: ver §4 |
| `reco`, `playlistimport`, `spotify`, `lyrics`, `models`, `api`… | ~80 | pocos | 0 | 0 | 0 | Lógica pura: suben casi sin cambios (cola inteligente, Pedir música, etc.) |
| `widget`, `recognition`, `di`, `localmedia`… | ~30 | casi todos | — | — | — | Solo Android (se quedan en `androidMain`) |

Módulos aparte: `innertube` (113 archivos, Ktor + serialización), `lrclib`, `kugou`, `betterlyrics`, `paxsenixlyrics`,
`youlyplus`, `simpmusic`, `shazamkit`, `unison`, `canvas`, `applecanvas`, `artistvideo`: casi todo puro Kotlin; hoy
están declarados como librerías de Android y hay que declararlos multiplataforma.

## 3. Fases (cada una termina con algo que se puede ver y comprobar)

**Fase 0 — Cimientos (sin cambios visibles).**
Pasar los módulos puros (`innertube`, letras, canvas…) a Kotlin Multiplatform; el árbol de Gradle compila Android como
hasta ahora. *Se acepta cuando:* el APK de Android sale idéntico (tests verdes, mismo comportamiento) y los módulos
compilan también para escritorio.

**Fase 1 — Núcleo común.**
Crear `shared/`. Subir: modelos, base de datos (Room KMP + SQLite incluido), preferencias (DataStore KMP), red (Ktor),
lógica (`reco`, `playlistimport`, `spotify`, `lyrics`, `license`), ViewModels (Hilt → Koin). Definir las interfaces de lo que
cambia por plataforma: `Player`, `Equalizer`, `Platform` (abrir enlaces, compartir, notificaciones, almacenamiento),
`Login` (pantallas web), `Cast`. *Se acepta cuando:* Android funciona igual con el núcleo en `shared/` y hay tests de la
lógica que corren también en JVM de escritorio.

**Fase 2 — Interfaz común.**
Subir `ui/` a `shared/commonMain` pantalla por pantalla (Inicio, Buscar, Biblioteca, Novedades, Reproductor, Cola,
Ajustes…), con los componentes de base primero (`AuraPalette`, `AuraType`, `AuraGlassSlab`, tarjetas, chips). Fuentes e
iconos pasan a recursos comunes. *Se acepta cuando:* hay una ventana de Windows con Inicio y Biblioteca reales y una
**comparación de capturas** (Android vs Windows, mismas dimensiones y datos de prueba) sin diferencias visibles.

**Fase 3 — Reproducción en Windows.**
Motor de audio de escritorio detrás de la interfaz `Player`: cola, pausa, siguiente, crossfade, normalización,
reproducción en segundo plano, teclas multimedia y control de Windows (SMTC). *Se acepta cuando:* se oye la misma
canción con la misma cola y los mismos controles que en el móvil, y el registro del reproductor es el mismo.

**Fase 4 — Funciones que dependen del sistema.**
Ecualizador y Hi-Res (ver §4), inicio de sesión (Google/Spotify/Apple), descargas y modo sin conexión, portadas
animadas (vídeo), Escuchar juntos, reconocer música, Cast, ventana mini/bandeja, instalador y actualizador.

**Fase 5 — Paridad y publicación.**
Lista de todas las funciones de `docs/UI_INVENTORY.md` marcada una a una en Windows; ejecutable firmado/instalador;
actualizador propio. Solo se publica con la orden expresa del dueño, como en Android.

## 4. Lo que no es compatible tal cual y la alternativa propuesta (a investigar y confirmar en su fase)

| Hoy en Android | Problema en Windows | Alternativa a probar |
|---|---|---|
| Media3 / ExoPlayer + `MusicService` | Solo Android | Interfaz `Player` común; en Windows **libmpv** (o VLC con VLCJ) con cola y crossfade propios |
| Salida Hi-Res (AudioTrack, flotante) | Distinta API | WASAPI (compartido y exclusivo) vía mpv; medir latencia y bit-perfect |
| Ecualizador Superpowered (C++ vía JNI) | JNI de Android | El SDK de Superpowered declara soporte de Windows: **verificar licencia y compilar con JNA/JNI para Windows**; si no, filtros propios en Kotlin sobre el flujo de mpv |
| Hilt | Solo Android | Koin (multiplataforma) |
| `android.util.Log` / Timber | — | Logger común (misma ruta `app.log` y mismas reglas de privacidad) |
| WebView (login Spotify, edad YouTube, token Apple) | No existe igual | KCEF/JCEF (Chromium embebido) o WebView2 de Windows |
| Cast | Google Cast de Android | Biblioteca de descubrimiento Cast/DLNA para JVM; se prueba en Fase 4 |
| MediaSession / notificaciones / Android Auto | Solo Android | SMTC (controles multimedia de Windows) y notificaciones de Windows vía JNA |
| WorkManager (radar, sincronización) | Solo Android | Planificador de corrutinas dentro de la app + inicio con Windows opcional |
| Widgets, tiles, atajos | Solo Android | Se quedan en Android (en Windows: bandeja / mini ventana) |
| Efecto de cristal (`haze`) | Rendimiento y aspecto | `haze` ya es multiplataforma; **igualar el aspecto con capturas** y medir CPU/GPU |
| Portadas animadas (vídeo en Compose) | Reproductor de vídeo | mpv/VLC dibujando en un `SwingPanel`, o decodificación a texturas |
| Licencia de pago / Worker | — | El Worker y la lógica de `license/` son portables; el canal de cobro en Windows hay que definirlo con el dueño |
| NewPipeExtractor (extracción de YouTube) | — | Es Java: corre tal cual en la JVM de escritorio |

## 5. «Idéntico» se comprueba, no se supone

- Capturas automáticas por pantalla (mismas dimensiones, mismos datos de prueba) en Android y en Windows; el CI falla si
  la diferencia pasa de un umbral acordado.
- `docs/UI_INVENTORY.md` es la lista de control: cada fila lleva «Android ✔ / Windows ✔».
- Mismas reglas del registro de regresiones: el código común las cumple en las dos plataformas; los tests de guardia
  (que leen el código fuente) pasan a apuntar a `shared/`.

## 6. Lo que sigue sin respuesta (se va cerrando fase a fase)

1. Calidad y latencia del motor de audio en Windows (Fase 3): se prueba con un prototipo antes de comprometer nada.
2. Licencia de Superpowered para Windows y si el motor de ecualizador actual se puede reutilizar (Fase 4).
3. Cómo cobra la versión de Windows (hoy la puerta de suscripción depende de la app de Android).
4. Tamaño y consumo de la app de escritorio (la JVM pesa más que Android): se mide en Fase 2.
5. Quién firma el instalador de Windows (certificado de firma de código; sin él Windows avisa al instalar).

## 7. Qué NO cambia mientras tanto

- Android sigue publicándose como hoy desde `main`, con las mismas reglas de AGENTS.md (nada llega a los usuarios sin
  la orden del dueño). Esta rama no se fusiona hasta que una fase esté completa, verificada y con su aprobación.
- Las zonas protegidas (`eq/`, `app/src/main/cpp/`, `license/`) solo se tocan en las fases 1 y 4 y siempre
  dejando intactos el modo demo, la licencia y la suscripción.
