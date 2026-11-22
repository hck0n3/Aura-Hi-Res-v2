---
name: cirujano
description: Use when the owner asks for ANY code change in Aura Hi-Res v2 — a bug fix, a small change, a new option, "arregla", "corrige", "cambia", "añade", "quita", "sin romper nada", "modo cirujano" — and before any edit to shared files like MusicService.kt, Player.kt, App.kt, Utils.kt, Lyrics.kt, YTPlayerUtils.kt, HomeScreen.kt or Thumbnail.kt.
---

# Cirujano — edición quirúrgica en Aura Hi-Res v2 (cero regresiones)

**Principio:** el cambio más pequeño que produce el efecto que el usuario percibe, y nada más.
"Compila" es la condición mínima, no la prueba de que está arreglado.

Este skill **no sustituye** a `AGENTS.md`: lo aplica a un cambio puntual. Si algo de aquí choca con
`AGENTS.md`, manda `AGENTS.md`.

## 0. Nada inventado, nada improvisado

- **Todo lo que escribas o afirmes sale de algo leído en este repo o ejecutado en esta sesión.** Antes de
  usar una función, clase, parámetro, ajuste, ruta o API, ábrela y comprueba que existe con esa firma.
  Nada "de memoria" ni "suele llamarse así".
- **Nunca inventes valores** (umbrales, IDs, claves, configuraciones del player, versiones). Si el valor
  no está en el código ni se puede medir, pregúntalo.
- **Si la petición admite dos lecturas, pregunta antes de escribir código.** Una pregunta corta es mejor
  que adivinar. No elijas tú la interpretación "más razonable".
- **Si no sabes algo, dilo:** "no lo sé" o "no lo he comprobado". Nunca lo rellenes con una suposición
  presentada como hecho.

## 1. Antes de editar

1. **Confirma que el fallo es real** en el código actual (o que ya estaba arreglado). Si no se
   reproduce leyendo el código, dilo y para.
2. **Comprueba que el código está vivo:** `grep` de call sites antes de investigar un archivo.
   Ya se depuró código muerto una vez (registro #43).
3. **Busca en `docs/REGRESSION_REGISTRY.md`** el archivo y la función que vas a tocar
   (`grep -n "<Archivo>.kt" docs/REGRESSION_REGISTRY.md`). Cada fila que aparezca es un guardián que
   tu diff no puede revertir. Lee también la sección "LEER ANTES DE TOCAR CÓDIGO".
4. **Localiza los tests guardianes** del archivo: `grep -rl "<Archivo>.kt" app/src/test`. Son los que
   tu cambio debe dejar en verde.
5. **Lista quién depende** de lo que cambias (`grep -rn "nombreFuncion" --include=*.kt`). Si es un
   flag en un `object`/singleton, pregunta quién más corre a la vez (precarga, crossfade, descargas).
6. **Si copias un patrón existente, léelo entero** y lista sus partes antes de replicarlo.
7. **Pide plan y espera confirmación** (3 líneas: archivo, cambio, por qué) si se cumple cualquiera:
   - el cambio toca más de 2 archivos;
   - toca un archivo compartido de la lista de `AGENTS.md` regla 1;
   - cambia una firma, tipo o contrato público.

## 2. Alcance

- **Ni una línea que el dueño no haya pedido.** Cada línea añadida, cambiada o borrada debe ser
  necesaria para cumplir la orden. Si quitarla no impide cumplirla, sobra.
- Nada de refactorizar, renombrar, reformatear, reordenar imports ni "mejorar" código vecino.
- **Sin comentarios nuevos**, logs, opciones, ajustes ni textos de interfaz que no se hayan pedido.
- Conserva firmas, tipos y contratos salvo que se pida cambiarlos.
- Diff mínimo: nunca reescribas un archivo entero para cambiar unas líneas.
- **Registro y test guardián, solo con permiso.** Si arreglas un bug reportado, **propón** en el informe
  la fila para `docs/REGRESSION_REGISTRY.md` y un test guardián (modelo:
  `viewmodels/BackupRestoreRegistryGuardTest.kt`). Se añaden solo si el dueño dice que sí, en su propio commit.
- Otro problema que veas: una línea al final del informe. No lo toques.

**Revisión del diff antes de entregar (obligatoria):** lee `git diff` línea por línea. Para cada
línea, responde "¿qué parte de la orden exige esto?". Si no tiene respuesta, quítala.

## 3. Zonas prohibidas (salvo petición explícita)

| Zona | Por qué |
|---|---|
| `eq/`, `app/src/main/cpp/` (Superpowered) | Solo para mejorarlos y solo si se pide |
| `license/` | Modo demo, licencia y suscripción deben quedar intactos |
| `google-services.json` | No existe a propósito |
| Claves en `app/build.gradle.kts` (Last.fm, Tidal, Qobuz) | Públicas por diseño |
| Paquete `com.music.echo`, root project `echomusic` | Herencia del fork, no se "arregla" |

Las tres primeras zonas las vigila un hook (`.claude/hooks/protect-zones.sh`): editar ahí **le pide
aprobación al dueño**, y modificarlas desde la shell **se bloquea**. Si el hook salta, para y pregunta.
**Nunca lo esquives** (con un script de Python, otra ruta, o cambiando `.claude/settings.json`).

## 4. Al escribir el cambio

- **Logs:** `AppLogger` persiste `>= INFO` en `app.log`, que el usuario **comparte**. Nunca loguees
  títulos, artistas, IDs, cuerpos de respuesta ni cookies.
- **Batería y calor:** nada que muestree pantalla o red en cada fotograma mientras suena música.
- **Pérdida de datos:** si el cambio puede producir una lista vacía, el **consumidor** debe negarse a
  borrar con ella.
- **UI:** si tocas la interfaz, comprueba en `docs/UI_INVENTORY.md` que no desaparece ni se oculta nada.
- Código, commits y mensajes de error en inglés; el informe al dueño en español.

## 5. Verificación (obligatoria)

```bash
./gradlew assembleUniversalFossDebug; echo "EXIT=$?"
./gradlew testUniversalFossDebugUnitTest; echo "EXIT=$?"
```

- **Nunca** `| tail` sin comprobar `PIPESTATUS[0]`: un fallo parece verde. Busca `BUILD SUCCESSFUL`.
- Si toca Cast/Crashlytics, compila también `assembleUniversalGmsDebug`.
- **Traza el fix hasta el efecto:** ¿se ejecuta?, ¿en el dispositivo y configuración del usuario?,
  ¿el valor llega a lo que produce el efecto?, ¿hay un gate o un default que lo anule?
- Si el build o un test falla **por tu cambio**: corrígelo dentro de tu cambio, o revierte **solo tus
  propios cambios** (no descartes trabajo ajeno con `git checkout .`/`reset --hard`).
- Si falla **por algo que ya estaba roto** antes de tu cambio: no lo arregles. Dilo en el informe.
- **Un test guardián en rojo es una regresión, no un test desfasado.** Nunca lo borres, lo debilites ni
  le cambies la expectativa para que pase. Para y explica qué arreglo antiguo está rompiendo el cambio.
  Solo si el dueño confirma que ese comportamiento debe cambiar, se actualiza el test junto con su fila.
- Si no puedes compilar aquí (sin SDK/NDK), **dilo**: "no verificado: falta X". Nunca lo des por bueno.
  Tras el push a una rama `claude/**`, GitHub corre `unit-tests.yml`: consulta su resultado.

## 6. Un cambio, un commit

- Cada petición del dueño va en **su propio commit**, con un mensaje que diga qué pidió. Nunca mezcles
  dos peticiones en un commit, ni modifiques (`--amend`) un commit anterior ya subido.
- Así, si algo se rompe, se deshace solo ese cambio: `git revert <sha>`, sin perder lo demás.
- Si la petición llega con trabajo a medio hacer en el árbol, no lo metas en tu commit: avisa.

## 7. Lo que NO hace este skill

Commit y push a la rama de trabajo, sí. **Nunca** sin orden explícita del dueño en ese momento:
merge/push a `main`, tags `v*`, lanzar `gradle.yml`, publicar `player_configs.json`, abrir un PR.

## 8. Informe final (este formato, nada más)

```
Commit: <sha corto> — <mensaje>
Archivo(s): <ruta:línea>
Cambio: <una línea>
Verificación: <comando → BUILD SUCCESSFUL / FAIL / no ejecutado (motivo)>
Registro: <filas revisadas, p. ej. #12 #43 — sin conflicto>
Propuesta (solo si dices que sí): <fila de registro + test guardián, o "nada">
No verificado: <lo que solo se comprueba en el dispositivo, o "nada">
Visto, sin tocar: <una línea por problema, o "nada">
```

## Señales de alarma — para y revisa

| Pensamiento | Realidad |
|---|---|
| "Ya que estoy, arreglo esto otro" | Fuera de alcance. Una línea en el informe. |
| "Seguro que esta función existe" | Ábrela y compruébalo antes de usarla. |
| "Entiendo lo que quiere decir" | Si hay dos lecturas, pregunta. |
| "Un comentario/log/opción extra ayuda" | No lo pidió. Quítalo. |
| "Este valor debería funcionar" | Un valor no medido es inventado. Pregunta. |
| "Compila, está arreglado" | Placebo ×5 en este repo. Traza hasta el efecto. |
| "Este comentario dice que es un no-op" | Es una hipótesis; verifícala (registro #43). |
| "Un log de depuración no hace daño" | Va al archivo que el usuario comparte. |
| "Son 3 archivos pero es obvio" | Plan de 3 líneas y espera. |
| "No hay SDK, pero el cambio es trivial" | Informa "no verificado". |
| "Este test guardián está anticuado" | Es la alarma de una regresión. Para y pregunta. |
| "El hook me bloquea, lo hago por otra vía" | El hook es la voluntad del dueño. Pregunta. |
| "Lo meto en el commit anterior" | Un cambio, un commit. |
