# Estudio completo de Aura Hi-Res v2 y plan de mejora — para que tú decidas

**Fecha:** 2026-10-06 · **Base:** rama `claude/nifty-ride-ox6396` (2.0.64-beta3 preparada)
**Estado:** propuesta. **Nada de este documento se ha ejecutado ni se ejecutará sin tu orden.** Marca qué
puntos apruebas (por número) y solo esos se harán, cada uno en su propia beta.

---

## 1. Cómo se hizo este estudio (y sus límites, dicho claro)

- Se midió TODO el código de los 14 módulos: 1 027 archivos Kotlin (~275 000 líneas), el motor nativo
  (C++), los 5 flujos de CI, las dependencias y los 142 archivos de pruebas (1 380 casos).
- Se leyó a fondo lo que más importa para lo que tú usas: reproducción y cola (`MusicService`), Pedir
  música, la cadena de audio y el ecualizador, colores de Aura, registro de errores, batería, y tu
  `app.log` real de la 2.0.64-beta1.
- **Límite honesto:** 275 000 líneas no se pueden leer línea por línea en una sesión. Para las zonas que
  no se leyeron enteras (pantallas clásicas, importadores, Listen Together, podcasts…) el estudio usa
  métricas, el registro de regresiones (328 filas) y búsquedas de patrones de riesgo. Donde un punto
  necesita una revisión más profunda, el plan lo dice.
- Desde aquí no hay acceso a YouTube ni a iTunes ni al repositorio de Google (red bloqueada): las
  versiones de dependencias de Google salen de las ramas de Dependabot que ya existen en tu repo.

## 2. El estado general, en una frase por área

| Área | Estado | Comentario |
|---|---|---|
| Reproducción / cola | 🟡 funciona, pero frágil | Todo vive en un archivo de 12 784 líneas con 87 variables compartidas entre hilos. Es la fuente de la mayoría de regresiones del registro. |
| Audio / ecualizador | 🟢 correcto | Cálculos verificados (error de filtros 0,02 dB). Hay margen de calidad en el camino de 16 bits (punto A3). |
| Pruebas | 🟢 buenas | 1 380 pruebas; la puerta de CI las corre. Faltan pruebas de pantalla y de dispositivo. |
| Registro de errores | 🟢 bueno tras esta beta | Todo error reportado llega a `app.log`; se quitó el ruido que lo llenaba. |
| Batería | 🟢 bien, con 3 mejoras posibles | Ver sección 6. |
| Tamaño de la app | 🟡 mejorable | El APK publicado pesa 87 MB; 41 MB son librerías nativas de 3 arquitecturas que casi ningún móvil usa. Solo arm64 pesaría ~46 MB (punto B4). |
| Dependencias | 🟡 algo atrasadas | 15 actualizaciones disponibles; 3 delicadas (sección 5). |
| Dos interfaces (clásica + Aura) | 🟡 coste doble | Cada arreglo se hace dos veces y a veces solo en una (ya pasó, fila #117). |

---

## 3. Lo más importante primero — mejoras de alto valor

### A. Calidad y fiabilidad que se notan

- **A1 · Cifrado de YouTube nuevo (`1f293754`) — acción tuya, sin actualizar la app.** Desde el
  2026-10-06 tu registro dice `No hardcoded config for hash: 1f293754` y el WebView del cifrado falla en
  cada arranque. Hoy no se nota porque el cliente ANDROID_VR reproduce sin cifrado, pero si YouTube lo
  restringe, se dejará de reproducir. La vía rápida de siempre: publicar la config **verificada** de ese
  player en `player_configs.json` de `main` (regla absoluta: nunca valores adivinados). Desde este entorno
  no se puede verificar (YouTube bloqueado). *Riesgo: ninguno si se verifica. Esfuerzo: bajo.*
- **A2 · Que el WebView del cifrado no se cree en cada arranque cuando no hace falta.** Hoy se crea al
  abrir la app aunque la canción se resuelva sin cifrado: CPU y memoria en cada arranque en frío.
  Propuesta: crearlo solo la primera vez que un cliente lo necesite. *Riesgo: medio (zona de las filas
  #28/#30). Esfuerzo: medio.*
- **A3 · Audio en 32 bits también para Opus (la mayoría de tus canciones).** Hoy el audio de Opus pasa
  por el ecualizador en 16 bits y sale en 16 bits con dither; el de alta resolución ya va en 32 bits. Se
  puede llevar Opus por el mismo camino de 32 bits (menos redondeo tras el EQ, sin dither). Mejora
  pequeña pero real en pasajes suaves. *Riesgo: medio-alto — es el corazón del audio; solo con prueba en
  tu teléfono y una beta dedicada. Esfuerzo: medio.*
- **A4 · Un modo claro de verdad para Aura.** Hoy Aura es solo oscura por diseño (todo es blanco
  translúcido sobre fondo oscuro). Un modo claro es un rediseño de colores de todas sus pantallas.
  *Riesgo: medio (legibilidad). Esfuerzo: alto. Necesita que elijas una referencia visual.*
- **A5 · Ocultar canciones no disponibles también en búsqueda y artistas.** Esta beta ya las oculta en
  playlists, Me gusta, biblioteca y listas en línea, y nunca las mete en la cola. Faltan los resultados
  de búsqueda y las páginas de artista (allí también llega la marca de YouTube). *Riesgo: bajo.
  Esfuerzo: bajo.*

### B. Tamaño, velocidad y consumo

- **B1 · Trabajos en segundo plano solo con batería suficiente.** Los 5 trabajos periódicos
  (actualizaciones cada 6 h, recomendaciones diarias, Last.fm diario, Radar semanal, Spotify) solo exigen
  red. Añadir "batería no baja" a los que no son urgentes evita gastar cuando el móvil está al límite.
  Ojo técnico: hay que re-encolarlos con la política `UPDATE` para que el cambio llegue a quien ya los
  tiene programados. *Riesgo: bajo. Esfuerzo: bajo.*
- **B2 · Quitar los 27 `println` del código de producción** (sobre todo `[PLAYBACK_DEBUG]` en la
  resolución de streams): construyen texto en cada resolución aunque nadie lo lea. *Riesgo: nulo.
  Esfuerzo: bajo.*
- **B3 · Registro más largo sin pantalla lenta.** El registro guarda 2 × 256 KB. Ahora que tiene menos
  ruido rinde más; si quieres aún más historia, se puede subir el tamaño y que la pantalla de Registros
  muestre solo el final (compartir seguiría enviándolo entero). *Riesgo: bajo. Esfuerzo: bajo.*
- **B4 · APK solo arm64 para las actualizaciones.** El universal (87 MB, medido en la beta3) incluye las 4
  arquitecturas: arm64 ocupa 13 MB y las otras tres 41 MB. Prácticamente todos los móviles actuales son
  arm64. El actualizador podría bajar el APK arm64 (~46 MB, casi la mitad) y
  dejar el universal para quien lo necesite. Menos datos y menos espera en cada actualización. *Riesgo:
  medio (el actualizador debe elegir bien el archivo; una mala elección deja sin actualizar). Esfuerzo:
  medio. Requiere cambiar el CI de publicación (necesita tu orden).*

### C. Mantenibilidad (lo que evita las regresiones repetidas)

- **C1 · Partir `MusicService.kt` (12 784 líneas).** Ya se empezó (HALLAZGO-021: `PlaybackErrorClassifier`,
  `RadioQueueShaping`). Propuesta por fases, una por beta, sin cambiar comportamiento: (1) motor de
  continuación/radio (`startRadioSeamlessly`, `appendSeed`, `orderedByTaste`, paginación) a su propia
  clase con pruebas; (2) fundido cruzado; (3) resolución de streams y caché de URLs; (4) recuperación de
  errores. Cada fase con el registro de regresiones verificado. *Riesgo: medio (por eso por fases).
  Esfuerzo: alto. Beneficio: el mayor de todo el plan a largo plazo.*
- **C2 · Una sola interfaz.** Mantener la clásica y Aura duplica cada pantalla (por ejemplo, la playlist
  local tenía DOS lógicas de reordenar distintas; esta beta las igualó). Propuesta: decidir si Aura queda
  como la única y, pantalla por pantalla, comprobar con `docs/UI_INVENTORY.md` que nada se pierda antes
  de retirar la clásica. *Decisión 100 % tuya. Esfuerzo: alto.*
- **C3 · Revisar los 141 `!!` (posibles cierres) y los 895 `runCatching`** en busca de los que tragan un
  error sin dejar rastro en el registro. *Riesgo: bajo. Esfuerzo: medio.*
- **C4 · Archivos de pantalla gigantes** (`Player.kt` 3 757, `MainActivity.kt` 3 148, `AuraPlayer.kt`
  3 057, `Lyrics.kt` 2 691, `AxionEqScreen.kt` 2 677, `HomeScreen.kt` 2 638 líneas): partir en piezas
  reduce recomposiciones (fluidez y batería) y errores. *Riesgo: bajo-medio. Esfuerzo: medio.*

### D. Pruebas y CI

- **D1 · Pruebas de pantalla (capturas) para Aura**, para que un cambio de color o de diseño no rompa
  nada sin que se vea. *Esfuerzo: medio.*
- **D2 · Que la rama de cada beta pase la puerta de pruebas automáticamente al subirla** (hoy hay que
  lanzarla a mano). *Esfuerzo: bajo. Necesita tu orden (toca el CI).*
- **D3 · Comprobar en CI que `RELEASE_INFO.md` tiene viñetas** (si no, la pantalla de Novedades sale
  vacía). *Esfuerzo: bajo.*

---

## 4. Lo que NO se toca (tus reglas)

Licencia/suscripción (`license/`), el motor Superpowered salvo para mejorarlo con tu permiso, el fundido
cruzado (curva y duración), la firma, publicar en `main`/tags/`player_configs.json`, y abrir pull
requests. Nada de este plan cambia esas reglas.

## 5. Dependencias que se pueden actualizar

| Dependencia | Tienes | Disponible | Riesgo | Recomendación |
|---|---|---|---|---|
| Kotlin | 2.4.10 | 2.4.20 | Bajo | ✅ Actualizar (corrección menor). |
| Media3 (reproductor) | 1.10.1 | 1.11.1 | **Alto** | ⚠️ Solo en beta propia: varios arreglos están verificados contra el código de la 1.10.1 (veto del PLAY fantasma, camino de 32 bits, `onPlayerCommandRequest`). Hay que re-verificarlos uno por uno. |
| Material 3 | 1.5.0-alpha18 | 1.5.0-alpha28 | Medio | ⚠️ Es alfa: puede cambiar diseño/comportamientos. Beta propia. |
| Material Kolor | 4.1.1 | 5.0.1 | Medio | ⚠️ Versión mayor: genera los colores del tema; revisar que no cambien tus colores. |
| Adaptive (plegables) | 1.3.0-alpha09 | 1.4.0-alpha02 | Medio | ⚠️ Alfa; afecta a plegables (filas #46-#50). |
| Coil (portadas) | 3.5.0 | 3.6.3 | Bajo | ✅ Actualizar. |
| Ktor (red) | 3.5.2 | 3.6.0 | Bajo-medio | ✅ Con la suite de pruebas. |
| Reorderable (arrastrar) | 3.0.0 | 3.1.0 | Bajo | ✅ Probar reordenar playlists. |
| Compose Shimmer | 1.3.3 | 1.5.0 | Bajo | ✅ |
| Firebase BoM (solo GMS) | 34.15.0 | 34.19.0 | Bajo | ✅ |
| Guava | 33.7.1 | 33.7.2 | Nulo | ✅ |
| Protobuf | 4.36.1 | 4.36.2 | Nulo | ✅ |
| Commons Lang3 | 3.20.0 | 3.21.0 | Nulo | ✅ |
| kotlinx-coroutines-test (módulo migration) | 1.10.2 | 1.11.0 | Nulo (solo pruebas) | ✅ |
| Gradle / AGP | 9.7.1 / 9.4.0 | (sin verificar: Google bloqueado) | Medio | Revisar en la próxima ronda con acceso. |

Al día y sin cambios: Hilt 2.60.1, KSP 2.3.12, Timber 5.0.1, Lottie 6.7.1, jsoup 1.23.2, coroutines 1.11.0.
**Nota técnica:** el proyecto bloquea versiones (`gradle.lockfile`), así que cada actualización regenera
el lockfile con la herramienta de Gradle, nunca a mano.

**Propuesta de orden:** (1) las de riesgo nulo/bajo juntas en una beta; (2) Media3 sola en otra beta,
con prueba en tu teléfono (reproducción, fundido, Android Auto, auriculares Bluetooth); (3) Material 3,
Material Kolor y Adaptive juntas en otra, revisando colores y plegables.

## 6. Batería y temperatura — lo ya hecho y lo que queda

**Hecho en la 2.0.64-beta3:** la captura de audio que anima el reproductor seguía activa con la app
fuera de pantalla — ahora se para a los 30 s y vuelve al entrar; el ecualizador ya no escribe una línea
en el registro (a disco) por cada paso mientras mueves una banda; y se quitaron las trazas repetidas de
letras y de Spotify.
**Queda (puntos B1, B2, A2).** Lo que ya estaba bien: la caché de URLs evita resolver dos veces la misma canción, y el aprendizaje de
géneros está acotado y guarda también los fallos para no repetir consultas.

## 7. Orden que propongo (tú decides)

1. **A1** (config del cifrado — solo tu acción de publicar, si se verifica).
2. **B1, B2, A5, D3** — pequeños, seguros, una beta.
3. **Dependencias de riesgo bajo** (sección 5, grupo 1) — una beta.
4. **C1 fase 1** (motor de continuación a su propia clase) — una beta.
5. **A3** (audio 32 bits para Opus) — una beta dedicada con tu prueba de oído.
6. **Media3 1.11** — una beta dedicada.
7. **B4** (APK arm64) — cuando quieras reducir el tamaño de las actualizaciones.
8. **C2 / A4** (una sola interfaz / modo claro) — decisiones de diseño tuyas, cuando quieras.

**Para aprobar:** responde con los números (por ejemplo «apruebo A1, B1, B2, A5 y las dependencias de
riesgo bajo»). Lo que no nombres se queda como está.

---

## 8. Estado de la ejecución (2026-10-07)

Rama `claude/nifty-ride-ox6396`. **Betas 2.0.64-beta4 a beta7 publicadas como prerelease el 2026-10-07** (canal beta). CI en verde: pruebas
unitarias y APK de prueba.

| Punto | Estado | Registro |
|---|---|---|
| B1 trabajos solo con batería | ✅ hecho | #329 |
| B2 `println` / `printStackTrace` | ✅ hecho | #329 |
| B3 registro más largo | ✅ hecho | #329 |
| A5 no disponibles en búsqueda y artistas | ✅ hecho | #329 |
| D2 pruebas automáticas al subir la rama | ✅ hecho | #329 |
| D3 Novedades con viñetas (y se arregló el corte de viñetas) | ✅ hecho | #329 |
| Dependencias de riesgo bajo | ✅ hecho, lockfiles regenerados por Gradle en CI | #333 |
| A2 descifrador solo cuando se usa | ✅ hecho | #330 |
| C3 errores tragados y `!!` peligrosos | ✅ hecho (20 grupos) | #332 |
| C4 recomposiciones (reproductor Aura, ecualizador, letras karaoke) | ✅ hecho | #334, #347 |
| Media3 1.11.1 | ✅ hecho (2 trampas corregidas); validar en el móvil | #335 |
| A3 audio en 32 bits | ✅ hecho como ajuste experimental apagado; validar de oído | #336 |
| B4 APK arm64 | ✅ hecho y publicado en la beta4 (46 MB vs 87 MB) | #331 |
| A1 config del cifrado 1f293754 | ⏳ acción tuya (verificar con el base.js real) | #330 |
| A2b PipePipe sin `sts` verificado | ✅ hecho (aprobado) | #337 |
| Material 3 / Material Kolor / Adaptive (alfas) | ↩️ revertido: Kolor 5, Adaptive 1.4 y Coil 3.6 arrastraban Compose 1.12 (cierre de la beta4); se quedan en Kolor 4.1.1, Adaptive 1.3, Coil 3.5 y Material 3 alfa18 hasta que Material 3 pase a Compose 1.12 | #339, #340 |
| C1 partir `MusicService` | ✅ fases 1–4 hechas y publicadas en la beta10 (continuación, fundido cruzado, resolución de streams y recuperación de errores, cada una en su archivo; 13 300 → 10 300 líneas) | #351, #352 |
| D1 pruebas de pantalla | ⏸ en espera: cambia el classpath de todas las pruebas | — |
| A4 modo claro de Aura | ❌ retirado por orden del dueño (beta5) | #338 |
| C2 una sola interfaz | ✅ Aura es la única interfaz (huecos cerrados y tus decisiones aplicadas); queda borrar código clásico muerto | #348–#350 |
