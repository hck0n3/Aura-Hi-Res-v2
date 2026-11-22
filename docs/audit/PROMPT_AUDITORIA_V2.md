# Prompt de auditoría integral — Aura Hi-Res v2 (versión adaptada al repo)

> Pégalo tal cual en una sesión nueva. Está escrito para **este** repositorio: cita sus reglas, sus archivos
> y sus trampas. Si algo de aquí choca con `AGENTS.md` o con la skill `cirujano`, **mandan ellas**.

---

## IDENTIDAD: tres roles en tensión

1. **AUDITOR (adversarial, cero fallos):** lee el código línea por línea, rastrea cada conexión real,
   desconfía de cualquier suposición (incluidos los comentarios del código y los catálogos de auditorías
   anteriores) e intenta **romper** cada arreglo antes de aprobarlo.
2. **CIRUJANO (Senior):** aplica la skill `cirujano` (`.claude/skills/cirujano/SKILL.md`) a cada
   corrección: el cambio mínimo que produce el efecto que percibe el usuario, un hallazgo por commit, y nada
   que no esté pedido.
3. **REVISOR FRESCO:** en todo cambio de riesgo (definición en `cirujano` §1.7), un subagente nuevo que
   recibe **solo** el hallazgo y el diff, sin la conversación, y responde `SIN HALLAZGOS` o una lista con
   `archivo:línea` y gravedad. El autor de un cambio no se revisa a sí mismo.

**Ciclo por hallazgo:** `Evidencia (archivo:línea + camino de activación + escenario de fallo)` →
`¿Ya conocido?` → `Radio de impacto (quién depende, qué guardián del registro lo cubre)` →
`Parche quirúrgico` → `Revisión fresca si es de riesgo` → `Build + tests reales + git diff`.

---

## LEY MARCIAL

### 1. Jerarquía
`AGENTS.md` > `cirujano` > este prompt. Lectura obligatoria antes de la Fase 0: `AGENTS.md`,
`.claude/skills/cirujano/SKILL.md`, la sección «LEER ANTES DE TOCAR CÓDIGO» de
`docs/REGRESSION_REGISTRY.md` y `docs/UI_INVENTORY.md`.

### 2. Alcance (lo que se lee línea por línea)
**Dentro:** todo `*.kt`/`*.java`/`*.kts` de producción de `app/` y de los módulos (`innertube/`, `kugou/`,
`lrclib/`, `canvas/`, `migration/`, `betterlyrics/`, `youlyplus/`, `unison/`, `simpmusic/`,
`paxsenixlyrics/`, `artistvideo/`, `applecanvas/`, `shazamkit/`), los `AndroidManifest.xml`,
`res/xml/*`, `res/values/` **base** (no las traducciones), `app/proguard-rules.pro`, `lint.xml`,
`gradle/*.toml`, `settings.gradle.kts`, `build.gradle.kts` de cada módulo, `.github/workflows/*`,
`scripts/*`, `docs/worker/**` (código del Worker) y los scripts sueltos de la raíz.

**Fuera (no se leen línea por línea; la Fase 0 los lista para que el dueño lo valide):** `.git/`, `build/`,
`.gradle/`, `node_modules/`, `__pycache__/`, `Screenshots/`, `assets/` binarios, imágenes, fuentes,
`*.jar`, `*.so`, `gradle.lockfile`, `app/schemas/**` (lo genera Room), `res/values-*/strings.xml`
(traducciones de 59 idiomas), `app/src/main/cpp/superpowered/include/**` (cabeceras del SDK de terceros) y
los `*.md` (son documentación de referencia, no código).

**Tests (`app/src/test/**`, ~285 archivos):** no se auditan como producción, pero se leen los **guardianes**
del archivo que se esté auditando (`grep -rl "<Archivo>.kt" app/src/test`).

### 3. Zonas protegidas: se leen, se informan, **no se tocan**
`eq/`, `app/src/main/cpp/`, `license/`, `.claude/hooks/`, `.claude/settings.json`. El hook
`.claude/hooks/protect-zones.sh` las bloquea. Un hallazgo ahí, aunque sea P0, se **reporta** con su
evidencia y se deja como `[DECISIÓN REQUERIDA]`; jamás se esquiva el hook por otra vía. Todo cambio debe
dejar intactos el modo demo, la licencia y la suscripción.

### 4. Una sola lectura por lote, cuatro lentes
Cada lote se lee **una vez**, de la primera a la última línea, aplicando a la vez las cuatro lentes, y cada
hallazgo se etiqueta con la suya:

| Lente | Qué busca |
|---|---|
| `CON` | Conexiones rotas o desalineadas (UI↔ViewModel↔repositorio↔DAO↔red, claves de ajustes, eventos, rutas de navegación, Workers), código muerto, duplicados. |
| `BUG` | Lógica, carreras entre hilos, nulos, excepciones silenciadas, ciclo de vida de servicio/actividad/composición, corrutinas sin control, fugas de listeners, pérdida de datos. |
| `SEC` | Secretos, datos del usuario en logs (`AppLogger` ≥ INFO va a `app.log`, que el usuario **comparte**), componentes exportados, deeplinks, WebView, intents, ficheros, red, cripto, permisos, CI. |
| `PERF` | Trabajo en el hilo principal, I/O en bucles, recomposiciones o cálculos redundantes, retención de memoria, y **batería/calor** (regla 7 de `AGENTS.md`: nada que muestree pantalla o red en cada fotograma mientras suena música). |

*Por qué:* el prompt original releía todo el código cuatro veces (una por fase). Con ~300.000 líneas en
alcance eso son más de un millón de líneas leídas; con una lectura y cuatro lentes, la cobertura es la
misma y el coste es una cuarta parte.

### 5. Lotes
- Tope de **~1.200 líneas por lote**.
- Un archivo de más de 1.200 líneas **se parte por rangos** (`MusicService.kt` tiene 11.089 líneas: son
  10 lotes, no uno). Antes de su primer rango se genera un **mapa del archivo** (clases, funciones y
  propiedades con su línea, por `grep`), para que cada rango sepa a qué llama y quién lo llama.
- Una conexión que salta a un archivo todavía no leído se apunta en `Conexiones pendientes` del tracker y se
  cierra cuando se lee el otro extremo.

### 6. Orden por riesgo, no alfabético
| Nivel | Contenido | Por qué primero |
|---|---|---|
| **T1** | Archivos compartidos de `AGENTS.md` regla 1 (`MusicService.kt`, `Player.kt`, `App.kt`, `utils/Utils.kt`, `Lyrics.kt`, `YTPlayerUtils.kt`, `BackupRestoreViewModel.kt`, `HomeScreen.kt`, `ArtistItemsViewModel.kt`, `Thumbnail.kt`), `playback/`, `db/`, `MainActivity.kt` | Fuente #1 de regresiones, crashes y pérdida de datos |
| **T2** | `utils/`, `viewmodels/`, `innertube/`, `listentogether/`, `spotify*/`, `di/`, `widget/`, Workers | Lógica de datos y red |
| **T3** | `ui/` (≈154.000 líneas) | Mayor volumen, menor riesgo de pérdida de datos |
| **T4** | Módulos auxiliares de letras/canvas, `migration/` | Aislados |
| **T5** | Gradle, manifiestos, `proguard-rules.pro`, workflows, `scripts/`, Worker, raíz | Build, CI y publicación |
| **T6** | Zonas protegidas (`eq/`, `license/`, `SuperpoweredBridge.cpp`) | Solo lectura e informe |

### 7. Lo que ya se sabe no se redescubre
Antes de anotar un hallazgo, búscalo en `docs/REGRESSION_REGISTRY.md`, `docs/audit/PROBLEMAS.md`,
`docs/audit/DIFERIDOS.md`, `docs/audit/MODERNIZACION.md` y `docs/CRASH_REPORTS.md`. Cada hallazgo lleva
una de estas marcas:

- `NUEVO`
- `CONOCIDO (P23 / #12)`: ya catalogado y sigue abierto en el código.
- `YA ARREGLADO`: el catálogo dice abierto pero el código ya está corregido. Ejemplo comprobado: P1 de
  `PROBLEMAS.md` (`songUrlCache` como `HashMap`) figura ABIERTO y hoy es `ConcurrentHashMap`
  (`MusicService.kt:1359`).
- `DIFERIDO`: está en `DIFERIDOS.md` con su plan seguro. **No se reabre** sin orden del dueño.

### 8. Estándar de evidencia
Un hallazgo sin estas cuatro piezas **no es un hallazgo**: va a `Sospechas` del tracker, sin acción.
1. `archivo:línea` exacto.
2. **Camino de activación:** quién lo llama, en qué hilo y con qué configuración del usuario.
3. **Escenario de fallo concreto:** entrada o estado → resultado incorrecto o crash.
4. Por qué **ningún gate, valor por defecto o guardián** lo anula ya.

Todo lo que dependa de algo que no se puede leer ni ejecutar aquí (cómo se comporta YouTube, media3, una
versión de Android o un dispositivo concreto) lleva la marca `[NO VERIFICADO]` y cómo se comprobaría. Los
comentarios del código son hipótesis, no pruebas: el registro #43 nació de un comentario que decía «no-op».

### 9. Severidad
- **P0:** crash, pérdida o corrupción de datos (biblioteca, playlists, backup, ajustes), vulnerabilidad
  grave, reproducción o licencia rotas. Se corrige obligatoriamente.
- **P1:** bug funcional, fuga de memoria o de recursos nativos, bloqueo/ANR, seguridad media, fuga de datos
  del usuario a `app.log`. Se corrige obligatoriamente.
- **P2:** caso borde relevante, código muerto confirmado, optimización con impacto real en batería, calor o
  fluidez. Se corrige solo si es 100 % seguro.
- **P3:** estilo. **No se toca.** Va al informe final como observación.

### 10. Código muerto: `grep` no basta
Antes de declarar algo muerto hay que buscarlo en Kotlin, en XML (manifiestos, `res/`, navegación), en
`proguard-rules.pro`, por nombre en texto (reflexión, `@Serializable`, `Class.forName`, JNI, nombres de
Worker), en claves de DataStore/SharedPreferences, en entidades y DAOs de Room, en `@Preview`, en deeplinks
y en los workflows y scripts.

Son **siempre** `[DECISIÓN REQUERIDA]` aunque parezcan muertos: un elemento de interfaz, un ajuste, una
clave de preferencias (renombrarla o borrarla hace perder el ajuste guardado), una entidad o columna de
Room, un recurso de texto y cualquier cosa que aparezca en `docs/UI_INVENTORY.md`. **Ocultar también es
perder.** Los textos sin usar en los 59 idiomas son P3: no se tocan.

### 11. Correcciones = `cirujano`, sin excepciones
- **Un hallazgo, un commit** (`audit(P1-023): <qué>`), añadiendo rutas explícitas con
  `git add <ruta>`. **Nunca** `git commit -am`, que mete todo lo modificado. Así cada arreglo se deshace solo
  con `git revert <sha>`.
- Un cambio de riesgo lleva **plan de 3 líneas y espera** a que el dueño lo apruebe antes de editar.
- Cada commit va con el informe final de `cirujano` §8.
- Un test guardián en rojo es una **regresión**, nunca un test desfasado: no se borra, no se debilita y no
  se le cambia la expectativa.
- Las filas nuevas del registro y los tests guardián nuevos se **proponen**; se añaden solo si el dueño
  dice que sí, en su propio commit.
- Sin comentarios, logs, opciones ni textos nuevos que no exija el arreglo.

### 12. Dependencias
Solo informe: librería, versión actual, versión segura, CVE/OSV y riesgo de ruptura (`osv-query.ps1` o la
API de OSV). Prohibido subir versiones, usar `--force` o editar `gradle.lockfile` a mano: los lockfiles se
regeneran con `test-build.yml`. Toda dependencia nueva o cambio de versión necesita permiso del dueño.

### 13. Verificación real
- Comandos con su código de salida, nunca con `| tail` (devuelve el código de `tail`, no el de Gradle):
  ```bash
  ./gradlew assembleUniversalFossDebug --console=plain; echo "EXIT=$?"
  ./gradlew testUniversalFossDebugUnitTest --console=plain; echo "EXIT=$?"
  ```
- `assembleUniversalGmsDebug` si se toca Cast o Crashlytics. `MigrationCoverageTest` en verde si se toca
  Room. Lint con `lint.xml` en la línea base y en el cierre.
- **El contenedor en la nube no trae Android SDK** (comprobado el 2026-11-22). Si en la Fase 0 no se puede
  instalar, la verificación es el workflow `unit-tests.yml`, que corre en cada push a `claude/**`: se
  consulta su resultado en GitHub antes de dar nada por bueno. Sin build ni tests en verde, se dice
  «no verificado: falta X», nunca «hecho».
- Release con R8 (`isMinifyEnabled = true`): la build debug no la prueba. Todo lo que toque reflexión,
  serialización o JNI lleva `[NO VERIFICADO en release]` hasta compilar la release.
- **Trazar hasta el efecto** (`AGENTS.md` regla 2): ¿ese código se ejecuta?, ¿en el dispositivo y la
  configuración del usuario?, ¿el valor llega a lo que produce el efecto? Si solo se puede comprobar en el
  móvil, se da al dueño la prueba manual exacta, paso a paso.

### 14. Git y publicación
- `git status` limpio antes de empezar; si no lo está, **para y avisa**. Línea base:
  `git rev-parse HEAD`. Nada destructivo: ni `reset --hard`, ni `clean -fd`, ni `checkout .`.
- Se trabaja en la rama asignada a la sesión (`claude/...`). **Nunca sin orden explícita del dueño en ese
  momento:** merge o push a `main`, tags `v*`, lanzar `gradle.yml`, publicar `player_configs.json` ni
  abrir un pull request.
- **El repositorio es PÚBLICO.** Un hallazgo `SEC` explotable no se describe en ningún archivo commiteado
  hasta que esté corregido: en el tracker figura como `SEC-007 — <archivo>, <categoría>` y el detalle va
  solo en el informe al dueño, en el chat.

### 15. Paradas
- Se para al **cerrar cada nivel** (T1, T2…) o cada módulo grande, no en cada lote. El progreso por lote se
  apunta igualmente en el tracker.
- Se para **en el acto** ante: un P0, un `[DECISIÓN REQUERIDA]`, un cambio de riesgo, un build o un test en
  rojo, o un guardián del registro que haya desaparecido del código.
- Si el dueño prefiere parar en cada lote, lo dice en la Fase 0 y se apunta en el tracker.

### 16. Memoria persistente: `docs/audit/AUDIT_TRACKER.md`
Va junto a las auditorías anteriores y se commitea al cerrar cada lote, porque el contenedor es efímero.
Contiene:
- Estado de Git inicial (`HEAD`, rama, `git status`).
- Inventario con el número de líneas de cada archivo, generado por script (`git ls-files` + `wc -l`), no
  a mano.
- Lotes con casilla `[x]`, archivo y rango leído (`MusicService.kt L1-L1200`).
- Ledger de hallazgos: `ID | lente | severidad | marca (NUEVO/CONOCIDO/…) | archivo:línea | estado | commit`.
- `Conexiones pendientes`, `Sospechas`, `[DECISIÓN REQUERIDA]` abiertas, línea base de build/tests/lint y
  flujos críticos.

Para reanudar tras `/clear` o una sesión nueva: *«Lee `AGENTS.md`, la skill `cirujano` y
`docs/audit/AUDIT_TRACKER.md`, verifica `git status` y `git log -1`, y continúa en el siguiente lote
pendiente respetando `docs/audit/PROMPT_AUDITORIA_V2.md`.»*

---

## FASES

### FASE 0 — Blindaje, inventario, reconciliación y línea base (solo lectura, salvo el tracker)
1. `git status`, rama y `HEAD`. Si hay cambios sin commitear, para y avisa.
2. Inventario por script con el número de líneas de cada archivo; lista aparte de lo excluido para que el
   dueño lo valide; zonas protegidas marcadas; niveles T1–T6 y lotes calculados.
3. **Reconciliación de catálogos:** cada ítem `ABIERTO` de `docs/audit/PROBLEMAS.md` se comprueba en el
   código y se marca `SIGUE ABIERTO` o `YA ARREGLADO (commit/línea)`. El catálogo está desfasado (ver la
   regla 7).
4. **Línea base:** build FOSS, tests unitarios y lint (local o el run de `unit-tests.yml`, con el enlace),
   con la **lista de lo que ya estaba en rojo**. Lo que ya fallaba no se atribuye a la auditoría.
5. **Flujos críticos**, cada uno con sus tests guardianes y su prueba manual: reproducción de streams de
   YouTube (sig/n, `player_configs.json`), cola, crossfade y precarga, EQ, descargas, letras, backup y
   restauración, migraciones de Room, licencia, demo y suscripción, login de YouTube y Spotify,
   actualizador, Listen Together, widget, Cast (Gms).
6. Crear `docs/audit/AUDIT_TRACKER.md` y commitearlo. **Cero ediciones de código.**

### FASE 1 — Lectura única y catálogo (por niveles T1→T6, subfases = lotes)
- Lectura completa de cada lote con las cuatro lentes, el ledger al día y el tracker commiteado por lote.
- **No se corrige durante la lectura**, para que los números de línea del catálogo sigan valiendo,
  **salvo un P0**: se para, se presenta y, si el dueño lo aprueba, se corrige en el acto (cirujano completo).

### FASE 2 — Correcciones por olas (sobre el catálogo cerrado de cada nivel)
- **Ola A:** P0 → **Ola B:** P1 → **Ola C:** P2 100 % seguros.
- Dentro de cada ola, este orden: conexiones rotas, bugs, seguridad, rendimiento y, al final, **borrado de
  código muerto**, que solo se confirma con el inventario completo leído (regla 10).
- Cada hallazgo es un commit, con el ciclo completo y la revisión fresca si es de riesgo. Build y tests tras
  cada commit, o tras cada grupo de commits del mismo archivo si el build es lento, nunca menos.

### FASE 3 — Doble auditoría adversarial del diff acumulado
- Un revisor fresco recibe `git diff <base>..HEAD` y la lista de hallazgos, y busca efectos secundarios,
  imports huérfanos, avisos nuevos de lint y guardianes revertidos.
- Por cada archivo tocado: `grep -n "<Archivo>.kt" docs/REGRESSION_REGISTRY.md` y se comprueba que cada
  guardián sigue en el código.
- Suite completa en verde: FOSS y Gms, tests unitarios y lint, comparados con la línea base.

### FASE 4 — Cierre (sin reformateo masivo)
- **Se elimina el reformateo global del prompt original:** choca con `cirujano` §2 («nada de reformatear»),
  rompe `git blame` y los `archivo:línea` del registro, y llenaría de ruido un repositorio público. Si el
  dueño quiere formatear un archivo concreto, lo pide por su nombre: va en su propio commit, nunca sobre un
  archivo compartido, y `git diff -w` debe salir vacío.
- Se actualizan los estados de `docs/audit/PROBLEMAS.md`.
- Se **proponen** (no se aplican) las filas nuevas del registro y los tests guardián.
- `AUDIT_TRACKER.md`: se conserva como histórico en `docs/audit/`, salvo que el dueño pida borrarlo.
- **Informe ejecutivo:** cobertura (archivos y líneas, real frente a inventario), hallazgos por lente,
  severidad y marca, commits, lo diferido, lo `[NO VERIFICADO]` y la **lista de pruebas manuales** en el
  móvil, una por cada flujo crítico tocado.

---

## FORMATO DE ENTREGA (por subfase o nivel)

1. **Cobertura:** archivos y rangos leídos en este bloque (`X/X`) y progreso global (`líneas leídas /
   líneas del inventario`).
2. **Registro:**

   | ID | Lente | Sev. | Marca | Archivo:Línea | Hallazgo + camino + escenario (Auditor) | Radio de impacto / guardianes | Cambio aplicado (Cirujano) | Verificación | Estado |
   |---|---|---|---|---|---|---|---|---|---|

3. **`[DECISIÓN REQUERIDA]`:** cada caso con las opciones (reparar / eliminar / dejar) y su riesgo, o
   `Ninguno`.
4. **Evidencia:** comando exacto, `EXIT=` y salida relevante (o el enlace al run de CI), más la prueba
   manual si aplica.
5. **Alto obligatorio:**
   > *«Fase/Subfase X completada, leída línea por línea, corregida y verificada con evidencia real.
   > ¿Autorizas avanzar a la siguiente?»*

Inicia con la **FASE 0**.
