Eres mi asistente de pruebas. Tengo un celular Android (Samsung Galaxy Z Fold7, SM-F966B) conectado por USB a esta computadora con la depuración USB activada. Quiero que pruebes en él mi app «Aura Hi-Res v2» (paquete `iad1tya.aura.music`, versión instalada 2.0.69-beta8) y que me dejes un informe. Háblame siempre en español.

## Reglas (obligatorias)
1. Usa solo `adb` desde esta computadora: `adb shell input tap/swipe/keyevent`, `adb exec-out screencap -p > archivo.png`, `adb shell uiautomator dump` + `adb shell cat /sdcard/window_dump.xml` para leer lo que hay en pantalla, `adb logcat` para el registro y `adb shell dumpsys media_session` para saber si suena música.
2. **No escribas texto con `input text`**: el teclado de Samsung lo autocorrige. Navega solo tocando.
3. **Nunca cambies nada en mis cuentas** (YouTube, Google, Spotify, Amazon) ni quites suscripciones, ni des «me gusta», ni crees o borres listas, ni migres nada, **sin preguntarme antes** y esperar mi «sí».
4. Antes de cada prueba lee la pantalla (uiautomator dump) para encontrar el botón por su texto o su descripción y tócalo en el centro de sus `bounds`. Si no lo encuentras, desplázate (swipe) y vuelve a mirar. Si sigue sin aparecer, marca la prueba como «NO SE PUDO» con una captura; no inventes resultados.
5. En **Ajustes** las secciones vienen **contraídas**: toca primero el título de la sección para abrirla. «Contenido» está dentro de «Contenido y letras»; «Cuentas» dentro de «Cuentas y sincronización»; «Acerca de» dentro de la sección «Acerca de». A Ajustes se llega desde Inicio ▸ botón de cuenta arriba a la derecha (descripción «Cuenta») ▸ «Ajustes».
6. Si el Fold está **abierto** (pantalla grande), las pestañas están en una barra lateral; si está **cerrado**, abajo. Haz las pruebas con el **Fold cerrado** y, si da tiempo, repite las de pestañas con el Fold abierto.
7. Guarda todo en una carpeta nueva `pruebas_aura_<fecha_hora>`: una captura por prueba (`NN-nombre.png`), el `logcat.txt` completo al final y el informe `informe_aura.md`.
8. Antes de empezar, borra el registro (`adb logcat -c`). Al terminar, busca cierres y bloqueos: `adb logcat -d -b crash` y líneas `ANR in iad1tya.aura.music`.
9. Las pruebas marcadas **[MANUAL]** necesitan que yo haga algo (escuchar, otro aparato, iniciar sesión): pídemelo paso a paso, espera a que te diga «listo» y luego comprueba lo que puedas con capturas.

## Pruebas (mismo número en el informe)

### A. Instalación
1. Versión instalada (`dumpsys package iad1tya.aura.music | grep versionName`), modelo y Android. Abre la app en frío (`am force-stop` y lánzala): debe llegar a Inicio. Si sale la pantalla de Términos, dime que la acepte y anota que salió.

### B. Suscripciones (lo más importante)
2. [MANUAL] Pídeme que use «Pedir música», una radio y Novedades unos minutos; luego que mire en YouTube si apareció alguna suscripción nueva que yo no hice.
3. Ajustes ▸ Cuentas ▸ «Revisar suscripciones»: espera a que cargue, captura, anota el total que muestra y si dice «Lista incompleta». NO quites nada. [MANUAL] Pregúntame si quiero quitar 2-3 sugeridas para probar el segundo plano: si digo que sí, que yo las elija; sal de la app (HOME) y comprueba que hay una notificación «Quitando suscripciones: X de N»; vuelve a la pantalla y comprueba que muestra el progreso.
4. En esa misma pantalla, anota si alguna fila dice «confirmada hace más de 24 h pero aún la lista».
5. En el reproductor completo, desliza el dedo despacio por encima del botón «Suscribirme» sin levantarlo encima (empieza fuera del botón): NO debe suscribir (captura antes y después).
6. Ajustes ▸ Cuentas y sincronización ▸ la pantalla de sincronizar con YouTube Music: captura y anota los números de cada apartado. Ninguno debe ser exactamente 1000 en todos; si una lectura quedó corta debe decir «al menos N · lista incompleta».

### C. Spotify, migrar y enlaces
7. [MANUAL] Importar de Spotify: abre la importación y comprueba que NO viene nada marcado (captura). No importes sin mi permiso; si te lo doy, solo UNA lista.
8. Si aparece «N artistas esperan confirmación», ábrelo: debe listar nombres con casillas, ninguna marcada (captura). No confirmes nada.
9. [MANUAL] Biblioteca ▸ botón Importar ▸ «Migrar lista» ▸ «Amazon Music»: comprueba que aparece la opción y que abre el inicio de sesión de Amazon. El inicio de sesión lo hago yo.
10. [MANUAL] Si migro una lista pequeña, anota cuántas canciones quedaron como «no encontradas».
11. Enlaces: abre con `adb shell am start -a android.intent.action.VIEW -d "<enlace>" iad1tya.aura.music` estos tres y anota qué suena (captura del reproductor): https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b , https://www.deezer.com/track/3135556 , https://music.apple.com/us/album/1440806041?i=1440806768

### D. Reproducción y cola
12. Pon una canción de Inicio y comprueba con `dumpsys media_session` que suena (state=3). Salta 3 canciones con el botón siguiente: todas deben sonar.
13. Abre la cola (captura): no debe haber canciones repetidas visibles.
14. En el menú de una canción toca «Menos de esto»: debe salir un aviso con «Deshacer» (captura). Toca «Deshacer».
15. Ajustes ▸ Reproductor: deben existir «Calidad con Wi-Fi» y «Calidad con datos móviles» (captura).
16. [MANUAL] Corte de red corto: con música sonando, activa el modo avión (`adb shell cmd connectivity airplane-mode enable`) y desactívalo a los 60 s: anota si la música sigue sola.

### E. Sin conexión
17. Con una canción de internet sonando y el mini reproductor visible: activa el modo avión, espera 25 s, captura (debe verse «Sin conexión» en el mini reproductor o en el reproductor). Desactívalo, espera 30 s, captura (el aviso debe desaparecer).
18. Biblioteca ▸ «Descargadas» y «Exportadas»: captura; anota si hay filas que no reproducen.

### F. Escuchar juntos [MANUAL, necesita otro aparato]
19. Pídeme crear una sala en otro aparato y unir este como invitado. Comprueba que cuando el anfitrión da play, este suena solo y la misma canción (captura del reproductor).
20. Siendo invitado, toca el botón aleatorio del reproductor y de la cola: debe salir «En una sala solo el anfitrión decide el orden» y no cambiar. Al salir de la sala, el aleatorio vuelve como estaba.
21. Con la sala en pausa, toca play como invitado: debe salir «La sala está en pausa…».
22. Pídeme un enlace o QR de invitación y ábrelo: debe preguntar «¿Unirte a la sala…?».

### G. Varios aparatos
23. [MANUAL] Control remoto: si tengo otro aparato emparejado, «Reproducir a continuación» y «Añadir a la cola» deben llegar.
24. Abre `adb shell am start -a android.intent.action.VIEW -d "echomusic://pair?code=ABCD-EFGH-JKMN-PQRS-TUVW" iad1tya.aura.music`: debe PREGUNTAR antes de cambiar de sala. Toca «Cancelar».
25. Ajustes ▸ Sincronizar dispositivos: captura y anota qué dice sobre el emparejamiento (con clave maestra debe decir que no se empareja solo).
26. Anota si aparece algún aviso «Revisar» de borrados retenidos.

### H. Sonido y ecualizador [MANUAL, necesito oírlo]
27. Auto-EQ: pídeme encenderlo y apagarlo y que te diga si el volumen vuelve igual.
28. Ecualizador externo (Wavelet/JamesDSP) tras un fundido entre canciones.
29. «Volumen seguro»: música del móvil al mismo nivel que la de internet.
30. Temporizador de apagado con fundido.

### I. Interfaz
31. Pestañas: toca cada pestaña (Inicio, Buscar, Biblioteca, Novedades) y comprueba que queda seleccionada (`selected="true"` en el dump). Luego toca 6 píxeles dentro del borde izquierdo y derecho de cada pestaña: siempre debe abrirse esa pestaña.
32. Abre un álbum desde la búsqueda y vuelve atrás a los resultados: el teclado NO debe taparlos (captura).
33. Ajustes ▸ Contenido y letras ▸ Contenido ▸ «Ocultos» se abre (captura). En una lista, desliza una fila un poco a la izquierda sin soltar y captura: debe verse un icono con texto.
34. Mantén pulsado el icono de Aura en la pantalla de inicio de Android: deben salir accesos «Seguir escuchando», «Aleatorio de Me gusta», «Descargado», Buscar y Biblioteca (captura).
35. Ajustes ▸ Acerca de: lee todo el texto (desplázate hasta el final). NO debe decir «Qobuz» ni «Sin pérdida»; debe explicar «hasta 3 equipos». Copia en el informe la frase de los equipos.

### J. Música del móvil
36. Biblioteca ▸ Local: vuelve a escanear y comprueba que tus canciones siguen ahí (captura antes y después; anota cuántas hay).

### K. Pasos míos (solo recuérdamelos al final)
37. Recordarme: desinstalar la app antigua si sigue en el móvil y revisar Google ▸ Seguridad ▸ Tus dispositivos.

## Informe (archivo `informe_aura.md`, y muéstramelo al final)
Una línea por prueba, con este formato exacto:
`N. Nombre — BIEN | MAL | NO SE PUDO | MANUAL PENDIENTE — qué viste (texto real de la pantalla, número, línea de logcat) — captura NN-nombre.png`
Después de las 37 líneas, añade:
- **Cierres y ANR:** cuántos y las primeras líneas de cada uno.
- **Líneas del registro de Aura** relevantes de los fallos (busca `EXTERNAL_LINK`, `LIB_SYNC`, `DEVICE_SYNC`, `ARTIST_`, `LT_`, `OFFLINE` en el logcat). No copies títulos de canciones, artistas, correos ni claves.
- **Pantalla usada** (Fold cerrado/abierto) y la hora de cada prueba que salió MAL.
Al terminar dime la ruta de la carpeta para que la comprima y se la mande a Claude.
