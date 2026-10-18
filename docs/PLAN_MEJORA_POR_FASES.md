# Plan de mejora por fases — Aura Hi-Res v2

> Pedido por el dueño el 2026-10-18: *«armar un plan en el que cada fase se complete hasta que esté auditada, luego
> por fase una beta y probar la beta, y solo se lanza a la siguiente fase hasta que se corrija todo lo de cada beta
> que lances»*.
>
> Estado: **PROPUESTA — ninguna fase ha empezado.** Se arranca cuando el dueño dé el visto bueno al orden.

---

## 1. La regla del ciclo (se repite igual en CADA fase)

```
 ┌─ 1. Implementar la fase (rama claude/…; commits pequeños; una fila de registro por cambio)
 │
 ├─ 2. AUDITORÍA INTERNA (puerta A) ──► si algo falla, se corrige y se vuelve a auditar
 │
 ├─ 3. CI verde: Unit tests + CodeQL en el último commit
 │
 ├─ 4. PEDIR PERMISO al dueño para lanzar la beta (orden permanente 2026-09-15: nada sale sin que lo pida)
 │
 ├─ 5. BETA (prerelease `-betaN`) + verificación del certificado. Aura la DETECTA y la descarga desde el actualizador
 │     si el interruptor «Actualizaciones beta» está encendido; con él apagado solo ve la estable
 │
 ├─ 6. PRUEBA DEL DUEÑO: actualiza desde Aura (sin bajar el APK a mano) y pasa la lista de la fase (puerta B)
 │
 ├─ 7. El dueño reporta ► se corrige TODO lo reportado ► nueva beta (betaN+1) ► vuelta al paso 6
 │
 └─ 8. CIERRE DE FASE: cero reportes abiertos + el dueño dice «fase cerrada» ► solo entonces empieza la siguiente
```

**No se salta ningún paso y no se solapan fases.** Una fase abierta bloquea a la siguiente, aunque esa ya esté
«casi lista». Lo nuevo que aparezca a mitad de una fase (un fallo urgente) se atiende como corrección dentro de la
beta en curso, no como fase nueva.

### Puerta A — qué significa «auditada» (la hago yo antes de pedir la beta)

1. **Registro de regresiones:** leído y comprobado, por grep o lectura, que ningún cambio revierte una fila
   (archivos compartidos: `MusicService.kt`, `Player.kt`, `App.kt`, `Utils.kt`, `Lyrics.kt`, `YTPlayerUtils.kt`,
   `BackupRestoreViewModel.kt`, `HomeScreen.kt`, `ArtistItemsViewModel.kt`, `Thumbnail.kt`).
2. **Traza hasta el efecto visible:** por cada arreglo, ¿ese código se ejecuta en el móvil del dueño?, ¿el valor
   llega hasta lo que se percibe?, ¿hay un gate o valor por defecto que lo anule? Se escribe en la fila.
3. **Calor y batería:** nada que muestree pantalla o red por fotograma mientras suena música.
4. **Datos del usuario:** nada de títulos, artistas, IDs, cookies o cuerpos de respuesta en `app.log`.
5. **Inventario de interfaz:** `docs/UI_INVENTORY.md` revisado; nada se pierde ni se oculta sin decirlo.
6. **Zonas protegidas intactas:** ecualizador y Superpowered (`eq/`, `cpp/`) y paquete `license/` (demo, licencia y
   suscripción siguen funcionando).
7. **Pruebas:** cada lógica nueva con su test; el CI verde en el último commit.
8. **Notas de versión** (`RELEASE_INFO.md`) escritas para el usuario, con viñetas.

### Puerta B — qué significa «probada» (la haces tú)

Cada fase trae su **lista de prueba** (abajo): pasos cortos, en tu móvil, con resultado esperado. Reportas lo que no
coincida. Una fila del registro solo pasa de «⚠️ pendiente dispositivo» a «✅ verificado» cuando tú confirmas esa
lista; si no la probaste, sigue pendiente (no se da por buena).

### Cómo llega cada beta a tu móvil (el actualizador de Aura)
- Se publica como **prerelease** (`-beta`). El actualizador ya la ofrece cuando **Ajustes ▸ Actualizaciones ▸
  Actualizaciones beta** está **encendido**; el aviso de segundo plano (`UpdateCheckWorker`) usa la misma comprobación.
- Con el interruptor **apagado** (lo normal para cualquier usuario) la app solo ve la estable. Así tú pruebas rápido y
  nadie recibe una beta sin haberla pedido. Tú lo dejas encendido siempre.
- Orden de versiones: una beta nueva se compara por número (`beta10` > `beta9`) y una estable supera a sus propias
  betas (arreglado en la fila 315).
- **Aviso:** el interruptor es visible en Ajustes para cualquiera. Quien lo encienda recibe las betas; es una decisión
  suya, pero no es «privado» en sentido estricto. Si algún día quieres que las betas no se vean ni siquiera así,
  habría que esconder el interruptor (decisión tuya, no la tomo yo).
- Mismo paquete y misma firma, así que se instala encima conservando datos.

### Numeración de betas
Se parte de `2.0.65-beta12`. Fase N → `2.0.(65+N)-beta1`; cada ronda de correcciones sube el número
(`-beta2`, `-beta3`…). `versionCode` siempre creciente, nunca por debajo de 955.

### Lo que este plan NO hace
- No publica versión estable (las betas sí salen como prerelease al pedirlo, ver arriba). La estable es una decisión aparte, con la lista del `AGENTS.md`
  (incluida la confirmación del dueño de que el certificado resuelve streams).
- No toca el ecualizador, Superpowered ni `license/` salvo petición explícita.
- No abre pull requests ni hace merge a `main` sin orden expresa.
- No publica `player_configs.json` con valores adivinados.

---

## 2. Las seis fases (en este orden, porque cada una apoya a la siguiente)

| Fase | Tema | Por qué en este lugar | Beta |
|---|---|---|---|
| **F1** | Verificar en dispositivo (herramientas de prueba) | Sin esto, las demás fases vuelven a acumular «pendiente dispositivo». Es la base | 2.0.66-beta1 |
| **F2** | Streaming que no falle | Es lo que más daña a una app de pago; ya con F1 se puede medir | 2.0.67-beta1 |
| **F3** | Quitar o conectar lo que no hace nada | Limpiar antes de construir encima | 2.0.68-beta1 |
| **F4** | Confianza y vida diaria (copia de seguridad, reportar problema, español, descarga inteligente) | Protege los datos del dueño antes de cambios grandes de interfaz | 2.0.69-beta1 |
| **F5** | Plegable y tableta como producto | Cambio visual grande: va con las capturas automáticas de F1 | 2.0.70-beta1 |
| **F6** | Descubrimiento y cola | Necesita datos y medición del dispositivo; es la más subjetiva | 2.0.71-beta1 |

---

### F1 — Verificar en dispositivo
**Objetivo:** que cada cambio futuro se pueda comprobar sin depender de «compila y parece correcto».
**Alcance**
- Capturas automáticas en CI de las pantallas clave en móvil, plegable abierto y tableta (Paparazzi o Roborazzi);
  el CI avisa si una pantalla cambia sin querer.
- Medición de arranque y desplazamiento (Macrobenchmark) y un baseline profile; cifras de partida guardadas en `docs/`.
- Plantilla de **lista de prueba** (formato fijo, 10 pasos máximo) y una hoja que lleve la cuenta de las filas del
  registro «pendiente dispositivo» (hoy ≈201 de ≈400).
- Reverificar con el código actual qué filas pendientes ya se cumplen y cuáles no.
**Entregable al dueño:** la beta no cambia la app a la vista; trae la primera lista de prueba grande para cerrar
filas atrasadas (cola sin repetir, Cast con vídeo, hojas del plegable, carrusel de Inicio, Pedir música).
**Auditoría específica:** que las herramientas de prueba no entren en el APK de release ni lo hagan crecer.
**Riesgo:** la infraestructura de capturas puede alargar el CI; se limita a pocas pantallas.
**Se cierra cuando:** CI genera las capturas y las cifras; el dueño pasó la lista atrasada y todo lo que falló
está corregido o abierto como fila de registro con dueño y fase.

### F2 — Streaming que no falle
**Alcance**
- **Prueba diaria de reproducción** en GitHub (resuelve vídeos públicos con la config actual) que avisa al dueño
  antes de que fallen los usuarios. *Un workflow programado solo corre en `main`: se activa cuando el dueño ordene
  el merge; hasta entonces se puede lanzar a mano en la rama.*
- Cadena de clientes con reintento: si uno da 403 o no resuelve, se prueba otro antes de saltar la canción.
- Panel de salud de reproducción (qué cliente resolvió, tasa de fallos): **sin guardar datos del usuario**.
- Precarga de la siguiente canción con wifi; recuperación de cortes al cambiar entre wifi y datos móviles.
**Auditoría específica:** cada reintento con tope y espera (nada de bucles que gasten batería, lección del bucle
de Discord de SimpMusic); precarga solo en wifi y respetando el ahorro de datos; el registro no lleva IDs.
**Lista de prueba:** reproducir 20 canciones seguidas; cortar el wifi a mitad; reproducir con datos; forzar
una canción difícil; leer el panel de salud.
**Se cierra cuando:** ni un salto de canción por error de resolución en la prueba del dueño durante una sesión larga.

### F3 — Quitar o conectar lo que no hace nada
**Alcance (tomando la sección 23 del inventario, previa reverificación porque puede estar desactualizada)**
- Visualizador de espectro vacío (y su preferencia encendida de fábrica); botones con `onClick = {}`; diálogos con
  bandera que nunca se activa; pantallas inalcanzables; composables huérfanos; ruta de commits que apunta a un repo ajeno.
- Por cada elemento, una decisión **conectar o quitar**, escrita en el registro. El dueño aprueba la lista antes de tocar.
**Auditoría específica:** nada que el usuario use hoy desaparece sin decirlo («ocultar también es perder»);
`UI_INVENTORY.md` actualizado.
**Se cierra cuando:** el inventario no lista placebos abiertos y el dueño confirma que no echa nada en falta.

### F4 — Confianza y vida diaria
**Alcance**
- Copia de seguridad automática (lista, historial, ajustes) y restauración probada.
- Botón «Reportar problema» que empaqueta log, dispositivo y versión en un toque (sin datos del usuario).
- Español completo (textos a mano en inglés), descripciones para TalkBack, escala de letra.
- Descarga inteligente: favoritas y recientes sin conexión hasta un límite de espacio que elige el usuario.
- Fundido de volumen del temporizador de sueño (queda de la lista de SimpMusic; toca `MusicService`, así que va con
  auditoría reforzada de transiciones: fila 208 y las de crossfade).
**Auditoría específica:** la copia no incluye cookies ni claves; restaurar no pisa datos nuevos sin avisar; la
descarga inteligente no corre en segundo plano sin wifi/carga cuando el usuario lo limita.
**Lista de prueba:** copiar → borrar datos → restaurar; reportar un problema y leer el archivo; ajustes en español.
**Se cierra cuando:** una restauración completa deja la app idéntica y el reporte no contiene datos personales.

### F5 — Plegable y tableta como producto
**Alcance:** dos paneles (lista y detalle) en Biblioteca y búsqueda; modo mesa del reproductor al doblar a medias;
arrastrar y soltar canciones a una lista; pasar por el catálogo de pantallas con las capturas de F1.
**Auditoría específica:** nada cambia en móvil normal; rendimiento medido con las cifras de F1 (sin empeorar).
**Lista de prueba:** abrir y cerrar el plegable con música sonando en cada pantalla principal.
**Se cierra cuando:** ninguna pantalla se corta, se estira ni pierde estado al plegar y desplegar.

### F6 — Descubrimiento y cola
**Alcance:** memoria de gusto con recencia y frecuencia, diversidad formal (MMR), ánimo por hora; resumen anual
(Wrapped) y recibo de escucha compartible.
**Auditoría específica:** cada cambio de ordenación con prueba unitaria; cola sin repeticiones intacta (filas 397-401);
sin cálculo por fotograma.
**Lista de prueba:** una semana de uso normal y la valoración del dueño («¿la cola acierta más que antes?»).
**Se cierra cuando:** el dueño la prefiere a la anterior; si no, se deja apagada y documentada.

---

### Fuera del plan (decisión del dueño, 2026-10-18)
- **Calidad Hi-Res real** (insignia con códec/tasa de bits/fuente reales, salida limpia a DAC USB, normalización de
  volumen entre fuentes, auditoría de Qobuz y Tidal): **el dueño la descartó.** No se hace; si algún día se retoma,
  empieza por la auditoría de las integraciones.

## 3. Seguimiento

Cada fase tiene su cabecera en este archivo con su estado:

| Fase | Estado | Beta en curso | Reportes abiertos |
|---|---|---|---|
| F1 | ⏳ sin empezar | — | — |
| F2 | ⏳ sin empezar | — | — |
| F3 | ⏳ sin empezar | — | — |
| F4 | ⏳ sin empezar | — | — |
| F5 | ⏳ sin empezar | — | — |
| F6 | ⏳ sin empezar | — | — |

Estados posibles: ⏳ sin empezar · 🔨 implementando · 🔍 en auditoría · 🧪 beta en prueba del dueño · 🔧 corrigiendo
reportes · ✅ cerrada por el dueño.
