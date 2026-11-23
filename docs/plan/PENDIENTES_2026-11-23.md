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
