# Aura Hi-Res v2.0.19 — traducción que funciona, glass sin crash y con desenfoque real, caché que obedece al instante

BETA PRIVADA para el dueño (v2.0.19 / versionCode 978). Actualización directa sobre
cualquier versión de la identidad v2: mismo paquete (iad1tya.aura.music) y misma
firma (CN=Aura Hi-Res v2), conserva tus datos, tu sesión y tus ajustes.

## Novedades sobre la BETA-024 (mismo día, tarde)

- **Se cerraba al entrar a un artista después de scrollear — ARREGLADO:** tu log mostró 4 cierres nativos (señal 11) con la memoria de video disparada: la barra de títulos nueva estaba re-muestreando el cristal en cada fotograma del scroll — en One UI 8.5 eso es una bomba nativa. Ahora el cristal solo muestrea cuando el scroll está QUIETO; mientras mueves la lista, la placa se congela con el mismo tono (idéntico a la vista) y el coste nativo cae a cero.
- **El cristal ahora SÍ se ve desenfocado:** el desenfoque uniforme sobre la portada leía como "solo transparencia de color". La barra ahora usa desenfoque progresivo (borroso abajo, despejado arriba — el look de las barras de iOS/One UI), que se LEE como desenfoque real y reduce el área muestreada.

## Novedades sobre la BETA-023 (mismo día, noche)

- **El límite de caché obedece AL INSTANTE:** al elegir un tamaño máximo en Ajustes ▸ Almacenamiento, el caché se recorta a ese presupuesto en el mismo momento — lo más viejo se va primero, la canción sonando nunca se toca, sin reiniciar la app.
- **Crash del «Iniciar sesión con Google» — causa encontrada con tu log:** era el conector de Cast (presente solo en la variante de la estable vieja) tocando el reproductor desde un hilo de Google. Esta beta no contiene ese camino del crash y además el conector quedó blindado para el futuro. Tu log de hoy corrió limpio: ningún error.

---

## Traducción de letras (arreglado de raíz)

- **La traducción ahora sale:** el menú de letras muestra «Traducción de letras» SIEMPRE (antes el ítem ni aparecía si no tenías clave de IA — por eso «nunca traducía»). La cadena es Google Translate → letras humanas de SimpMusic → IA local del dueño; el eslabón muerto (Pollinations) se eliminó.
- **Reintenta sola:** si una traducción falla por un instante sin red, reabrir la letra reintenta (antes un fallo bloqueaba esa canción toda la sesión). Sin conexión muestra «Sin conexión a internet» en segundos.
- **Persistida:** la traducción se guarda por canción — no se vuelve a gastar datos.

## Liquid Glass forzado (Samsung One UI 8.5 / S26 Ultra)

- **El toggle de Liquid Glass manda de verdad:** ya no te lo bloquea la capa de Samsung; el interruptor de Ajustes ▸ Apariencia enciende el cristal sin importar la capa de personalización (solo el Modo de Rendimiento puede vetarlo, porque lo enciendes tú).
- **La barra de títulos también es de cristal:** al entrar a un artista, álbum, playlist o canciones, la barra superior donde aparece el título ahora es translúcida con el mismo cristal que tu barra de navegación (antes quedaba de color sólido en los Galaxy).
- En diálogos y menús desplegables, con el toggle encendido obtienes placas translúcidas reales (el desenfoque de ventana que Samsung desactiva no puede forzarse desde ninguna app — es un límite del sistema, documentado en el informe).

## Caché de escucha (estilo Spotify)

- **Lo que escuchas se guarda y SE VE:** cada canción reproducida queda en caché y aparece en la lista «En caché» de tu Biblioteca (estaba rota: guardaba pero la lista mostraba vacío).
- **Sin duplicados peligrosos:** cada canción se escribe UNA sola vez en el caché correcto; antes se escribía dos veces y la copia extra llenaba el caché de descargas sin control.
- **Quitar del caché borra de verdad** los bytes (antes solo desaparecía de la lista).
- Portadas y Canvas ya tenían su caché propio (2 GB y 256 MB); ahora también se respetan tus límites.

## Escuchar Juntos (reemplazo completo por el motor de SimpMusic)

- **Motor nuevo de cero:** se eliminó el motor viejo y se portó el protocolo exacto de SimpMusic (metroproto), con su máquina de estados de sesión, su reloj de sincronización con el servidor y su puente de reproducción — byte-compatible con los servidores que usan SimpMusic y Metrolist.
- **Sugerencias, aprobaciones y sala:** crea sala o únete con código (o enlace de invitación); los invitados pueden sugerir canciones y el anfitrión aprueba; indicador «Cargando…» por cada invitado mientras bufea.
- **Reconexión sin perder la sala:** el botón «Reconectar» cierra el socket sin borrar tu sesión — si te vuelves a conectar, la sala sigue ahí. El crossfade se apaga solo dentro de una sala (antes la desincronizaba en cada transición).
- **Nota honesta:** el chat de sala ya no existe — el protocolo de SimpMusic no tiene chat, y el viejo solo funcionaba entre dos teléfonos con esta misma app. Todo lo demás del diálogo está igual (25 funciones del inventario verificadas).
- **Servidor configurable en Ajustes** (usa el de SimpMusic por defecto).

## Nueva función: Enviar métricas de reproducción a Google

- **Ajustes ▸ Scrobbling ▸ «Enviar métricas de reproducción a Google»** (apagado por defecto): al activarlo, la app envía a YouTube el mismo protocolo que el cliente oficial de YouTube Music — ayuda a que tu historial y recomendaciones se alimenten como si escucharas desde la app oficial. Puertos desde SimpMusic con su formato exacto (verificación 204 en cada paso, latidos de progreso, ping final).

## Nueva función: Letras sincronizadas de Spotify + Canvas de Spotify

- **Letras de Spotify (Ajustes ▸ Contenido ▸ Letras):** la letra sincronizada oficial de Spotify, con colores línea a línea, como fuente adicional del reproductor.
- **Canvas de Spotify (Ajustes ▸ Apariencia):** el video vertical oficial de Spotify como primera opción de portada animada; si la canción no tiene, cae a las que ya tenías (Apple/Tidal).
- Ambos requieren iniciar sesión con Spotify (ver abajo).

## Inicio de sesión con Spotify (pantalla en blanco — arreglado)

- **Causas encontradas y corregidas:** la ventana de login usaba el «disfraz» de navegador de sistema (Spotify le servía una página rota), limpiaba cookies a medias (carrera), y no reintentaba si la primera carga quedaba en blanco. Ahora usa el mismo disfraz de Chrome de escritorio que ya usaba para los tokens, limpia esperando a que termine, y si la página queda blanca reintenta una vez sola.
- **Los toggles de letras/canvas de Spotify se activan solos** al iniciar sesión con tu cuenta. El motor de audio Hi-Res (Superpowered) funciona completo: esta beta firma con tu certificado real (CN=Aura Hi-Res v2), al contrario de las betas de debug de ayer.

## Listas con IA (nunca más «servicio no disponible»)

- Si la IA está ocupada o caída, la lista se construye de todos modos con resultados reales de búsqueda de YouTube Music a partir de tu descripción, avisando «Generada sin IA» — el mismo enfoque que InnerTune/OuterTune/Metrolist. Cuando la IA responde, la lista es la de IA como siempre.

## Biblioteca (nueva apariencia)

- **Tiles «Descargado» y «En caché»** ahora aparecen en la Biblioteca nueva, respetando sus interruptores de Apariencia (antes solo existían en la pantalla clásica).

## Problema pendiente que NECESITA tu ayuda

- **Inicio de sesión con Google se cierra (crash):** el código del login está intacto desde la última versión que funcionaba; la causa más probable es una interacción con el glass nuevo. Para clavarla necesito el registro del teléfono: **Ajustes ▸ Registros** → comparte el archivo `app.log` justo después de que te pase el crash. Con ese log lo arreglamos en minutos.
