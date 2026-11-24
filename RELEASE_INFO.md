# Aura Hi-Res v2.0.69-beta4 — beta privada: Escuchar juntos que suena a la vez, aviso «Sin conexión» en el reproductor y tus decisiones aplicadas

Beta solo para probar, sobre la 2.0.69-beta3 que tienes instalada (incluye todo lo de la beta3, la beta2 y la beta1; sus notas siguen valiendo y se juntarán en la versión estable). Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma. Te llega por el actualizador solo con «Recibir versiones beta» encendido (Ajustes ▸ Actualizaciones).

## Modo sin conexión

- **El reproductor avisa mientras sigue sin conexión:** al pasar a modo avión (o perder internet), el reproductor completo muestra «Sin conexión · sonando tu música sin conexión» todo el rato, no solo un aviso de un segundo. Tócalo y te explica qué está sonando y que tu cola vuelve sola con la red.
- **El mini reproductor también lo dice:** pone «Sin conexión» en la línea del artista.
- **Con el modo sin conexión manual,** el aviso ofrece «Volver online» para apagarlo desde ahí.
- **Con wifi sin internet** (por ejemplo, la de un hotel que pide registrarse) el aviso se queda mientras siga sonando solo tu música sin conexión.
- **No sale cuando suena otro aparato:** si controlas otro dispositivo o envías a Cast, el aviso no aparece, porque lo que oyes no es la música de este móvil.

## Escuchar juntos

- **Ya no se queda cada uno en una canción distinta:** si al invitado le falla una canción, se le acaba antes o salta sola, Aura le vuelve a poner la de la sala (hasta 3 intentos, separados unos segundos). Si aun así no puede, se pone en pausa en vez de seguir con otra canción que la sala no eligió.
- **Menos saltos hacia atrás:** el invitado ya no retrocede hasta 15 segundos al reconectar ni al recibir de nuevo el estado de la sala.
- **«Uno da play y el otro no», corregido:** cuando el invitado se quedaba en pausa por un fallo (sin red, un error o porque Android no dejó arrancar la música en segundo plano), desde ahí cargaba cada canción nueva de la sala sin darle play. Ahora una pausa por un fallo ya no deja al invitado fuera de la sala; sí lo siguen haciendo tu pausa, una llamada, desconectar los auriculares y el temporizador, para que la sala no suene encima.
- **Reintentos con tope tras un fallo:** si una canción no puede sonar en el aparato del invitado, Aura la reintenta como mucho 3 veces por cada orden del anfitrión, cada vez con más espera, y después se queda en pausa y avisa «No se pudo reproducir esta canción en este aparato; toca play para reintentar». Antes lo reintentaba cada 15 segundos sin parar, gastando batería.
- **Aviso cuando la sala está en pausa:** si el invitado pulsa play con la sala pausada, ahora ve «La sala está en pausa: solo el anfitrión puede reanudar» en vez de un botón que parecía no funcionar.
- **El registro que me mandas dice por qué se desincronizó:** cuánto se desvió el invitado, qué decidió Aura y si el anfitrión mandó el play tras cambiar de canción. Sin títulos ni artistas.
- **Solo salas de Aura:** se deja de usar el servidor compartido de Metrolist. En Ajustes ▸ Escuchar juntos desaparecen el interruptor «Salas de Aura», la «URL del servidor» y «Elegir servidor».
- **Un código antiguo de 8 caracteres se rechaza** con el mensaje «Ese código no es de una sala de Aura…»: pide al anfitrión un código nuevo de 6 caracteres. La política de privacidad se actualiza a la versión 4 para decirlo.
- **Abrir un enlace o un QR de invitación ahora pregunta** «¿Unirte a la sala …?» con «Unirme» o «Cancelar», en vez de meterte en la sala sin preguntar.

## Control entre aparatos

- **«Reproducir a continuación» y «Añadir a la cola» llegan al otro aparato** cuando lo controlas a distancia (antes se rechazaban). Si el otro no contesta en 5 segundos, te avisa.
- **Borrados retenidos con pantalla propia:** si otro aparato quita muchas cosas de golpe, Aura no las borra aquí por seguridad y ahora te avisa con «Revisar». Ahí ves cuántas de cada tipo y eliges «Aplicar» o «Descartar». También está en Ajustes ▸ Sincronizar dispositivos ▸ «Borrados retenidos».

## Suscripciones y cuenta

- **Nueva herramienta para limpiar suscripciones fantasma:** Ajustes ▸ Cuentas ▸ «Revisar suscripciones» (con la sesión iniciada) lista los artistas a los que está suscrita tu cuenta y marca «Probablemente no la hiciste tú» a los que no tienen me gusta, no están en tus listas ni en tu biblioteca y casi no has escuchado aquí.
- **No se quita nada sin tu confirmación:** eliges cuáles (o «Seleccionar las sugeridas»), confirmas y se quitan una a una, con progreso, «Detener», el resumen «Quitadas · Fallidas · Sin intentar» y «Reintentar las fallidas». Cuenta como quitada solo cuando YouTube lo confirma.
- **Una baja confirmada ya no se deshace sola:** si dejas de seguir a un artista y luego te vuelves a suscribir desde YouTube, Aura ya no te da de baja otra vez. Una baja que falló se sigue reintentando como antes.
- **Las listas de importación «Esperan confirmación» y «Solo en Aura» son de cada cuenta de Google:** lo que respondiste con una cuenta ya no vale para otra.
- **Textos de inicio de sesión:** ahora dicen «Inicia sesión con tu cuenta de Google» y los textos visibles ya no nombran «YouTube Music» (en Ajustes ▸ Cuentas el grupo se llama «Cuenta de Google»).

## Sonido y ecualizador

- **Al apagar Auto-EQ vuelve tu preamp de antes:** el preamp del perfil de los auriculares ya no se queda aplicado al ecualizador manual (antes la música podía quedar varios dB más baja). Al volver a encenderlo, recupera el del perfil.
- **El compresor suave («pegamento») se apaga una vez al actualizar** si lo tenías guardado encendido. Después el interruptor de Ajustes ▸ Sonido vuelve a ser tuyo.
- **La curva de fundido 4 se llama «Cruce natural (una baja mientras la otra sube)»,** que es lo que hace. Antes decía «Ascenso (entra rápido, sale lento)». Tu curva guardada no cambia.

## Reproducción y calidad

- **Calidad distinta con Wi-Fi y con datos móviles:** en Ajustes ▸ Reproductor eliges «Calidad con Wi-Fi» y «Calidad con datos móviles» («Alta» o «Ahorro»). Las dos vienen en «Alta», así que nada cambia si no las tocas. El modo ahorro de datos sigue mandando sobre las dos.
- **Motores de reproducción de SimpMusic 2.6.0:** Aura usa los mismos motores que SimpMusic actualizó el 14-11-2026 para arreglar canciones que dejaban de sonar. Es lo que más necesito que pruebes.
- **La música vuelve sola tras un corte de red solo si vuelve en menos de 5 minutos** (antes, hasta 30). Pasado ese tiempo carga, pero se queda en pausa hasta que tú le des play.

## Biblioteca y avisos

- **Pantalla «Ocultos»:** en Ajustes ▸ Contenido ves todo lo que ocultaste con «No me gusta», «No recomendar» o «Menos de esto» y lo deshaces con un toque.
- **«Menos de esto» con Deshacer:** al marcar una canción aparece un aviso con «Deshacer» que quita la marca de la canción y de su artista (solo lo que marcó ese toque).
- **«Exportadas» ya no muestra MP3 borrados del teléfono,** ni en Biblioteca ni en la lista automática.
- **Avisos con botón:** «Tu sesión caducó» trae «Volver a entrar» y «No hay nada que reproducir por tus filtros» trae «Ver filtros», en vez de un mensaje que desaparecía.
- **Más accesos al mantener pulsado el icono de Aura:** «Seguir escuchando», «Aleatorio de Me gusta» y «Descargado», además de Buscar y Biblioteca.

## Gestos

- **La vibración al desplazarte viene apagada:** se quedan las vibraciones que confirman algo. Si la quieres, actívala en Ajustes ▸ Apariencia ▸ «Vibrar al desplazarte».
- **Al deslizar una fila ves el icono y el texto** de lo que va a pasar («Reproducir a continuación», «Añadir a la cola», «Me gusta»…), no solo un icono.

## Mantenimiento de mi parte

- **Vigilante diario de SimpMusic:** cada día reviso de forma automática si SimpMusic publica un arreglo de reproducción, para traerlo a Aura antes. Solo avisa: no cambia la app ni publica nada por su cuenta.

## Para que lo pruebes y me cuentes

- No pude probar nada de esto en un aparato: todo está revisado leyendo el código y con pruebas automáticas, pero falta tu móvil.
- **Escuchar juntos con 2 aparatos:** el anfitrión da play y el invitado debe empezar a sonar solo, con la misma canción. Cambia de canción, pausa y reanuda varias veces; con la app del invitado en segundo plano también.
- **Modo avión mientras suena:** debe salir «Sin conexión» en el reproductor y en el mini, y desaparecer al volver la red.
- **Limpieza de suscripciones:** abre Ajustes ▸ Cuentas ▸ «Revisar suscripciones», mira si las sugeridas son de verdad las que no hiciste, quita unas pocas y comprueba en YouTube que se fueron.
- **Auto-EQ:** apunta tu preamp, enciende Auto-EQ con tus auriculares, apágalo y mira que vuelve el preamp que tenías; enciéndelo otra vez y debe volver el del perfil.
- **Canciones que suenan con los motores nuevos:** pon las canciones que antes fallaban o tardaban y dime si alguna no suena.
- Si algo falla, manda el registro desde Ajustes ▸ Registros con la canción y la hora.
