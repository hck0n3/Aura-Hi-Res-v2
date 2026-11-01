# Plan de arreglos tras probar la beta 9 (dueño, 2026-10-31)

Regla de trabajo: una fase a la vez; cada fase se sube a la rama y se espera a que GitHub dé VERDE antes de la siguiente. Nada se
publica sin la orden del dueño. Todo lo que el dueño pida después se agrega aquí.

| Fase | Contenido | Estado |
|---|---|---|
| 1 | Aleatorio («Empezar desde cero» con «26 de 29»), interruptor de aleatorio y volumen en remoto, «Ir a la lista» en remoto, botón de borrar registros lejos de Compartir (fila 577) | Hecha, verde |
| 2 | Portada animada del álbum que no se anima en el reproductor (fila 578) | Hecha, verde |
| 3 | Biblioteca: los botones de los atajos («Canciones que me gustan», «Álbumes favoritos»…) con el estilo que le gusta; los botones que se despliegan arriba (cápsula de cristal) también en Inicio, Novedades y Buscar (fila 579) | En curso (cápsula hecha; atajos: falta captura del dueño) |
| 4 | Lo repetido: sección «Local» (MP3/vídeos importados, caché, descargas), pie de «Relacionados» duplicado con la pestaña, submenús y botones duplicados | Pendiente |
| 5 | Listas fijadas (Inicio y Biblioteca) sincronizadas entre dispositivos; reiniciar el aleatorio en remoto | Pendiente |
| 6 | Cola infinita inteligente: lista = todos sus géneros; álbum = el estilo del álbum; canción suelta = el estilo de esa canción; fijo hasta que el usuario cambie de canción/álbum/lista; nunca se acaba. «Relacionados» y «canciones sugeridas» concordando con lo que suena (mismo filtro de estilo y fe), sin enseñar estilo a la cola | Pendiente |
| 7 | Revisar que el aleatorio funcione igual en todas las pantallas | Pendiente |
| 8 | Cierre: pruebas, registro de regresiones, notas de versión; la beta la lanza el dueño | Pendiente |

Nota (corrección del 2026-10-31): el «cambio de estilo en 2 segundos» del registro del cel NO era deriva: eran dos rellenos a la vez tras un
traspaso, ambos descartados por `QUEUE_RACE_GUARD`. La deriva real sigue sin demostrarse; hace falta un registro nuevo de la beta 9.
