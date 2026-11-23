# Pendientes y fallos — inventario del 2026-11-23

Lista única de todo lo que quedó a medias, falló o espera revisión tras la beta 2.0.69-beta2. Cada línea dice
quién la desbloquea. Nada de esto llega a los usuarios sin la orden del dueño (AGENTS.md).

## A. Arreglos interrumpidos por el límite de uso (se retoman a las 04:53 UTC)
1. **Suscripciones (3.ª ronda):** dos toques seguidos que vuelven a suscribir (el guardia se suelta antes de que Room
   escriba); el mismo artista con dos identificadores (UC… / MPLAUC…) que la sincronización da de baja; la subida de
   biblioteca que reescribe la fila con una copia vieja; el menú del artista sin protecciones; línea de log por toque;
   vaciar la cola de órdenes al cambiar de cuenta.
2. **Cola sin conexión (2.ª ronda):** canción repetida al volver la red; relleno sin conexión que se queda; filtros
   de «No me gusta», explícito y vídeos; vaivén cada 2-3 min con wifi sin internet; repeticiones con el aleatorio
   mejorado; y lo nuevo aprobado: el **modo sin conexión manual** también sigue con música sin conexión al acabar la cola.

## B. Ecualizador (aprobado por el dueño: «sí a todo») — espera a que el dueño abra el candado
3. Quitar los −2 dB ocultos y subir el tope del preamp a +9 dB.
4. Auto-EQ con los filtros reales del perfil (graves y agudos), aviso «Bypass» y arreglo de apagar/encender.
5. Volumen Seguro: quitar la medición que no hace nada (se mantienen +12 dB y el −7 dB de lo desconocido).
6. Interruptor del filtro de las «eses» (encendido por defecto).

## C. Revisión con ojos frescos que falta (segundas rondas ya fusionadas sin revisar)
7. Cola: deriva de género e historial completo (2.ª ronda).
8. «Pedir música» con artista exacto (2.ª ronda).
9. Escuchar juntos (2.ª ronda).
10. Control entre aparatos (2.ª ronda).
11. Sincronización sin conexión (2.ª ronda).
12. Novedades: radar acumulativo (2.ª ronda) y Sencillo/EP/Álbum en la página del álbum.
13. Reproductor: «Suscribirme» e «Ir a la lista» (2.ª ronda).
14. Ajustes por defecto: enlaces, batería, sincronización con Google (2.ª ronda).
15. Música del móvil: escaneo seguro, volumen ausente, consultas troceadas.
16. Librerías actualizadas (ningún agente independiente las revisó).
17. Sonido: compresor apagado, textos honestos, motivos del offload.

## D. Arreglos encontrados y todavía sin hacer (no necesitan decisión)
18. **Gustos del algoritmo:** los saltos rápidos no cuentan en el perfil; «Menos de esto» solo afecta a esa canción;
    un «No me gusta» de canción no resta nada a su artista.
19. **Audio:** los ecualizadores externos (Wavelet, JamesDSP) pierden la sesión tras el primer fundido; quitar el
    LoudnessEnhancer muerto; el inventario de la interfaz cita «Calidad de sonido», que ya no existe.
20. **ReplayGain** de los archivos del móvil (para que suenen al mismo nivel que lo de internet).
21. **Registro por botón** (identificador estable por acción con número de acción, fases F2/F3).
22. **Experiencia de uso (auditoría):** errores de reproducción en inglés y con jerga; deslizar el reproductor borra
    la cola sin «Deshacer»; acciones que no confirman; botones sin nombre para TalkBack; temporizador difícil; 19 textos
    en inglés; pantallas vacías sin siguiente paso; el buscador de Ajustes no lleva a la opción.
23. Novedades: estrenos que solo vienen de YouTube con 2-6 pistas siguen saliendo «Álbum».

## E. Esperan una decisión del dueño
24. Lista local que, al subirse a la cuenta de Google, cambia de identificador y puede duplicarse en otros aparatos.
25. Servidor de Escuchar juntos (arranque exacto a la vez en todos): exige volver a desplegarlo.
26. Servidor de sincronización: un estado «ahora suena» por aparato.
27. Beta con las actualizaciones grandes (Gradle/AGP, NDK, Material 3 + Compose, motores de YouTube).
28. Pegar los 7 textos de la pantalla de pago (`docs/legal/CAMBIOS_PANTALLA_PAGO.md`).

## F. Plan grande (fases 1-10 de `PLAN_TODO_EN_UNO.md`), sin empezar
Pantallas grandes y plegables, nueva bienvenida, quitar menciones a YouTube, música local, nube (OneDrive y Dropbox),
modo TV, rendimiento por gama, sincronización completa, extras, Wrapped.

## G. Delegado por el dueño el 2026-11-23 («todo esto lo puedes hacer por tu parte sin necesidad mía»)
- **Betas privadas:** las lanza el agente cuando el CI esté en verde y no haya defectos confirmados abiertos (AGENTS.md).
- **Workers de Cloudflare (rooms y sync):** los despliega el agente con sus tests en verde y compatibilidad comprobada.
  Necesita, una sola vez, un token de API de Cloudflare guardado en el entorno (formulario seguro, nunca en el chat).
- **Fases grandes del plan:** el agente las ejecuta por orden (1 → 10) tras cerrar los pendientes actuales.
- **Pruebas en el móvil:** el agente añade pruebas automáticas en un emulador de GitHub; solo lo que exige hardware real
  (Bluetooth, dos aparatos, Fold, Doze) queda para el dueño, en una lista mínima.
- **No delegable por diseño:** abrir el candado de `.claude/hooks/protect-zones.sh` (solo el dueño, a mano).

## H. Búsqueda completa del 2026-11-23: lo que solo decide o hace el dueño

Los arreglos que no necesitan decisión (paquetes D1-D7) ya están en marcha. Responde por número (p. ej. «H3 sí, H12 no»).

### Decisiones
1. Deslizar una fila hace cosas distintas según la pantalla: ¿quieres un único significado por dirección en toda la app, o mantenerlo y mostrar siempre icono y texto?
2. Vibraciones: ¿apagamos por defecto la vibración al desplazar (cada 100 ms) y dejamos solo las que confirman algo? La fila 37 decidió conservarla.
3. ¿Mostramos una insignia visible de calidad (códec, bits, kHz) y una hoja «Ruta de la señal», en lugar de «V.SEGURO ON · −1.0 dBFS»?
4. Notificación y accesos directos: ¿quieres botones de la notificación configurables y accesos directos dinámicos («Seguir escuchando», «Aleatorio de Me gusta», «Descargado»)?
5. Pulido: ¿añadimos atrás predictivo en reproductor y cola, «Guardar como lista» y «Vaciar lo siguiente» en la cola, soporte RTL para árabe, persa y hebreo, y «Porque escuchaste X» en la radio?
6. Estudio comparativo: ¿cuáles entran en el plan? Peticiones por voz y Asistente («Ok Google, pon X en Aura» hoy no hace nada), pantalla «Ocultos» para deshacer «No recomendar», temporizador completo, calidad distinta en wifi y datos, carpetas de listas, listas inteligentes, pódcasts, editor de etiquetas.
7. Botón «Más» de las listas de éxitos: hoy abre el navegador (YouTube Music y Apple Music). ¿Hacemos una pantalla propia dentro de la app?
8. Avisos efímeros (sesión caducada, «cola vacía tras filtros»): ¿los cambiamos por un aviso con botón («Volver a entrar», «Ver filtros»)?
9. Enlace de invitación a Escuchar juntos: hoy une a la sala sin preguntar. ¿Quieres un diálogo de confirmación?
10. Sincronización: los borrados masivos frenados quedan retenidos sin pantalla para aceptarlos o rechazarlos. ¿Hacemos esa pantalla o lo dejamos así?
11. Primer emparejamiento: las bajas antiguas de la sala siguen quitando lo que trae el aparato nuevo (fila 654a). ¿Lo mantenemos así o pasa a la Fase 8?
12. La música vuelve sola hasta 30 minutos después de un corte de red: ¿acortamos esa ventana?
13. Importaciones: las listas «esperan confirmación» y «Solo en Aura» son globales, no por cuenta. ¿Las separamos por cuenta de Google?
14. Una suscripción hecha directamente en YouTube no se ve en Aura hasta la siguiente sincronización (efecto de la fila 700). ¿Lo aceptas o quieres una lectura que no escriba?
15. Fila 716: tras una baja confirmada, si te vuelves a suscribir desde YouTube en menos de 30 días, Aura deshace la suscripción. ¿Añadimos una columna nueva para distinguir «baja confirmada» de «baja fallida»?
16. Compresor suave: quien lo tenía guardado en ON lo sigue oyendo (fila 712). ¿Hacemos una migración única que lo apague?
17. Audio: ¿quieres un modo «Fidelidad» con salida bit-perfect por USB en Android 14+, sin fundido dentro de un álbum en orden, 32 bits por defecto en gamas media y alta, y tempo y tono en archivos Hi-Res locales (hoy fijos a 1.0)? ¿Renombramos la curva de fundido 4?
18. Dependencias retenidas (Cast 23 y MediaRouter 1.9, targetSdk 37, wrangler 4, Firebase, Superpowered 2.8.2, jsoup, jsdom): ¿en qué beta entra cada una?
19. ffmpeg-kit está abandonado, solo se descarga del espejo aliyun y su exportación de vídeo recodificada nunca funciona: ¿lo cambiamos por ffmpeg-kit-audio y tinypinyin por pinyin4j?
20. Propuestas de SimpMusic nunca encargadas (WebView precalentada, quickjs sin WebView, diccionario kuromoji fuera del APK, robustez de Last.fm, descargas inteligentes, letras sobre vídeo): ¿cuáles quieres?
21. Enlace de Gumroad con el nombre viejo JR-MUSIC-PRO: ¿creamos un producto nuevo y cambiamos PRODUCT_ID en el servidor de licencias?
22. Textos legales: ¿quieres añadir tu nombre legal o tu ciudad, o se quedan como «desarrollador independiente con residencia en Honduras»?
23. Vigilante del reproductor (player-watch.yml) en pausa: ¿lo reactivamos con otra forma de leer el player? Requiere un push a main con tu orden.
24. Control remoto: ¿«Reproducir a continuación» y «Añadir a la cola» deben llegar al otro aparato (requiere un comando nuevo en las dos apps)? ¿Confirmas los valores elegidos sin medir de 30 s y 10 s (filas 660 y 663)?
25. Pedir música por voz: el reconocedor se reinicia en cada pausa y puede volver a sonar (fila 611). ¿Lo aceptas como límite?
26. Tu mensaje de la beta 14 llegó cortado («Cuando notifique de las novedades de mis a…», fila 631): ¿qué pedías?
27. Fases del plan: ¿mantenemos «YouTube» en lo funcional (verificación de edad, traspaso desde Apple)? ¿Las canciones locales cuentan como «sin conexión»? ¿Cambiamos «Inicia sesión con YouTube Music» también en Ajustes ▸ Cuentas?
28. Wrapped: ¿cuentan las escuchas de otro aparato, y los artistas invitados como artista principal?
29. «Optimizaciones: resultados compatibles seleccionables»: lo interpretamos como los interruptores por servicio de «Abrir enlaces» (fila 676). ¿Es lo que pedías?
30. «Exportadas» en Biblioteca y la lista automática siguen mostrando MP3 que ya borraste: ¿aplicamos el mismo filtro que en Descargadas?

### Acciones que solo puedes hacer tú
1. Abrir el candado: editar a mano la línea 17 de .claude/hooks/protect-zones.sh para sacar eq/, license/ y main/cpp de la zona. Desbloquea E1-E4 y los textos de pago.
2. Pantalla de pago: pegar los 7 textos de docs/legal/CAMBIOS_PANTALLA_PAGO.md en license/LicenseScreens.kt (precio en :70; :261, :263, :410, :417, :437, :486), y valorar también «Renueva el pago en Gumroad» en :301. O abrir el candado para que lo haga el agente.
3. Revocar en Cloudflare el token pegado en el chat, crear uno nuevo con la plantilla «Edit Cloudflare Workers» y guardarlo solo como Network secret (api.cloudflare.com, Bearer) del entorno Aura-Hi-Res-v2. Sin él no se pueden desplegar los Workers de salas ni de sincronización.
4. Remote Control de la laptop: todas las carpetas aparecen «no disponibles» para la nube; solo tú puedes revisarlo en el equipo.
5. Quitar a mano, en tu cuenta de Google, las suscripciones fantasma que ya existían, o esperar la herramienta de limpieza de la Fase 9.
6. Pedir revisión legal a un abogado en Honduras y en EE. UU.: elusión de protección de streams, Superpowered con GPL-3.0, cláusula 3.5 y ley hondureña sin citar por número.
7. Worker aura-edge: confirmar si se redesplegó el prompt nuevo de /v1/judge (fila 625) y la caché de /v1/ask (fila 574); ese Worker no está en la delegación.

### Pruebas que exigen tu móvil (lo demás lo cubre el emulador de GitHub)
1. Suscripciones: tras «Pedir música», tocar dos veces rápido y usar el chip «Suscribirme» del reproductor, ningún artista debe aparecer suscrito solo (filas 646, 670, 671, 700, 711, 716).
2. Sin conexión: modo avión mientras suena, wifi sin internet, sesión larga con buena red que nunca entre sola, vuelta de la red con «volviste a tu cola», y el modo manual que siga con música sin conexión (filas 665-668, 690, 705, 717).
3. Actualizar sin perder datos: instalar encima un APK firmado aurav2 con versionCode mayor y comprobar OFFLINE_AUDIT listed(+0,-0), sin OFFLINE_AUDIT_DROP (fila 694); el APK de test-build no sirve para esto.
4. Control remoto con dos aparatos: sin saltos solos, cascos y coche, mini reproductor que siga controlando el otro aparato, notificación con el espejo IDLE y tráfico del espejo (filas 657-664, 681-686).
5. Escuchar juntos con 2-3 aparatos: anfitrión o invitado tardío, siguiente canción precargada en todos, código de 6 letras, QR y compatibilidad con el servidor de Metrolist (filas 601, 615-617, 647, 652, 669).
6. Cola inteligente: vallenato/bolero sin salsa, rap cristiano sin reguetón, sin repetir historial, «Pedir música» con Luis Miguel solo de él hasta agotar el catálogo, y cuántas canciones descarta la segunda opinión de la IA (filas 625, 645, 649-651, 655, 656).
7. Novedades: radar acumulativo, «Estás al día», Sencillo/EP/Álbum, que Spotify devuelva tracks.totalCount y que el carrusel ancho no marque como vista una tarjeta asomada (filas 653, 674, 675, 710).
8. Sonido: preamp alto con Volumen Seguro sin «voz rasposa», filtro de 200 Hz del compresor, si el HAL acepta offload, y un ecualizador externo (Wavelet o JamesDSP) tras un fundido (filas 610, 679, 712-714, P19).
9. Enlaces y ajustes por defecto: Spotify y demás apagados por defecto, «Abrir de forma predeterminada», sincronización con Google apagada que aun así baja la biblioteca al iniciar sesión, y sin aviso de batería al empezar (filas 676-678).
10. Música local: reescaneo sin permiso o con la SD sacada no borra nada, y «En caché» con más de 999 canciones en Android 8-11 (filas 638, 694, 755).
11. Veto del PLAY fantasma: está apagado; encenderlo exige probar en el coche que el botón del volante funciona tras restaurar la cola (fila 27).
12. Portada animada: buscar CANVAS_ALBUM_RETRY found=true y CANVAS_MEMO hit en el álbum reportado, y el lienzo horizontal a pantalla completa (filas 578, 598, 644).
13. Icono en el launcher Samsung, y búsqueda que aparecía desde abajo: decir pantalla y consulta si vuelve a pasar (filas 618, 633).
