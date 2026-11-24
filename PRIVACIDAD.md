# Política de privacidad — Aura Hi-Res v2

**Versión:** 4 · **Fecha de entrada en vigor:** 24 de noviembre de 2026

Esta es la versión principal de la política de privacidad de **Aura Hi-Res v2** (la "aplicación"). La traducción al inglés está en [PRIVACY_POLICY.md](PRIVACY_POLICY.md); si las dos no coinciden, prevalece esta versión en español. Completa la cláusula 11 de los [Términos y Condiciones](app/src/main/assets/legal/TERMINOS_Y_CONDICIONES.md).

## Quién es el responsable

**El desarrollador independiente de Aura Hi-Res v2**, con residencia en **Honduras** ("nosotros"). Contacto para todo lo relacionado con tus datos: **aurahires@gmail.com**.

## En resumen

- La aplicación **no tiene analítica, rastreo, publicidad ni perfiles publicitarios**, y no nos envía informes de fallos ni datos de uso de forma automática.
- No tienes una cuenta con nosotros. Tu biblioteca, tu historial, tus listas, tus búsquedas y tus sesiones de YouTube o Spotify **se guardan en tu dispositivo**.
- Aun así, **hay datos que salen de tu dispositivo**, y aquí están todos: los que reciben los servicios de música y otros terceros, y los que reciben nuestros propios servicios (no es uno solo: son cuatro).
- Dos funciones que envían datos están **activadas por defecto**: los **Servicios Aura** y la **traducción automática de letras**. Además, la **sincronización entre dispositivos** se activa sola cuando activas tu licencia con el correo de la compra. Las tres se pueden apagar.
- **No vendemos tus datos** ni los compartimos para publicidad.

## 1. Lo que se queda en tu dispositivo

- Lo que escuchas, pausas y saltas, y tu historial de escucha (para las estadísticas y las recomendaciones que se calculan en el propio dispositivo).
- Tus listas, tus me gusta, tu historial de búsqueda, tus ajustes y tus descargas.
- El acceso a tus archivos de música locales.
- Las cookies y los tokens de sesión de los servicios de terceros en los que inicies sesión. **No se incluyen** en las copias de seguridad ni en la sincronización.
- El correo de la compra, si lo escribes al activar la licencia.

Lo borras todo borrando los datos de la aplicación o desinstalándola. Lo que ya se haya enviado a nuestros servicios (apartado 2) se borra como se explica en el apartado 6.

## 2. Lo que reciben nuestros servicios

Operamos cuatro servicios en línea sobre la infraestructura de Cloudflare: el servidor de licencias (que también atiende las funciones de IA), los Servicios Aura, la sincronización entre dispositivos y las salas de Aura de Escuchar juntos, que son el único servidor que usa Escuchar juntos.

| Servicio | Qué recibe | Cuándo | Cuánto tiempo se conserva |
|---|---|---|---|
| **Licencias** | Tu clave de licencia y un identificador técnico del dispositivo (ANDROID_ID, o uno aleatorio si no está disponible). Si lo escribes, el correo de la compra, que se **compara** con el de Gumroad y **no se guarda** en el servidor. | Siempre: al iniciar la demo, al activar la licencia y al abrir la aplicación. | El vínculo entre tu clave y tus dispositivos (con la fecha de último uso), mientras exista tu licencia. El registro de demo de cada dispositivo se conserva para que la demo no se reinicie al borrar los datos. |
| **IA** (el mismo servidor) | Lo que envía cada función de IA (apartado 4) y tu dirección IP. | Solo cuando usas una función de IA, o cuando la traducción de letras llega a su último recurso. | El contenido no se guarda. La IP se guarda un máximo de **48 horas**, solo para limitar el número de peticiones diarias. |
| **Servicios Aura** (**activados por defecto**; se apagan en Ajustes ▸ Servicios Aura) | La petición de configuración firmada (interruptores de funciones y avisos), sin datos tuyos. Nombres de artistas, para obtener sus estilos. Artista y álbum, para buscar portadas animadas. En "Pedir música", una huella cifrada (SHA-256) de la petición y la lista que propuso la IA, **nunca el texto que escribiste**. | Mientras usas la aplicación. | Estilos de artista: 30 días. Portadas animadas: 7 días. Respuestas de "Pedir música": 3 días. Son cachés compartidas que no te identifican. |
| Servicios Aura: **copia en la nube** | Tu copia de seguridad (biblioteca e historial), **cifrada en tu dispositivo** con una clave derivada de un secreto que solo tú tienes. No podemos leerla. | Solo cuando la creas. | Las **3 últimas** copias; las anteriores se borran. |
| Servicios Aura: **registro de diagnóstico** | El registro técnico de la aplicación. No incluye títulos, artistas, nombres de listas, cuentas, correos ni claves; sí puede incluir identificadores de canciones y de listas. Una huella de tu IP para limitar envíos. | Solo cuando pulsas **"Enviar registro"**. | **7 días**. |
| Servicios Aura: **segunda opinión de IA de la cola** (**desactivada por defecto**) | Título y artista de lo que suena y de las canciones candidatas dudosas, hasta cuatro estilos, el idioma y si es música cristiana. Tu IP. | Solo si la activas. | El contenido no se guarda. La IP, un máximo de 48 horas. |
| **Sincronización entre dispositivos** (se activa sola al activar la licencia **con el correo de la compra**, o al emparejar con un código; se apaga en Ajustes ▸ Sincronizar dispositivos) | Tus me gusta y no me gusta, los nombres de tus listas y el título, artista y portada de sus canciones; los nombres de tus dispositivos; lo que está sonando (título, artista, portada y posición). Tu grupo se identifica con una huella cifrada (SHA-256) de tu clave y tu correo, o de tu código. **No** guarda cuentas, cookies, el correo ni la clave. | Mientras está activa. | Mientras exista tu grupo. Puedes pedirnos que lo borremos. |
| **Escuchar juntos: salas de Aura** (el único servidor de Escuchar juntos) | El nombre que elijas, el código de la sala y los datos de las canciones que suenan en ella y en la cola del anfitrión (identificador, título, artista, álbum, duración y portada). | Solo cuando creas una sala o te unes a una. | Una sala vacía se borra a los **5 minutos**. |
| **Reconocimiento de canciones** (relé) | Una firma de audio calculada a partir del micrófono. | Solo mientras usas el reconocimiento, y solo si la conexión directa con Shazam falla. | Se reenvía a Shazam; no la guardamos. |

## 3. Lo que reciben terceros

Estos terceros reciben las peticiones y las tratan según **sus propias** políticas de privacidad. Todos ven tu dirección IP.

**Siempre, porque la aplicación no funciona sin ellos:**

- **YouTube / YouTube Music (Google):** búsquedas, navegación, reproducción, identificadores de vídeo y, si inicias sesión, las cookies de tu cuenta. Es inevitable en un cliente de YouTube Music.
- **Servidores de imágenes de Google:** carátulas y miniaturas.
- **GitHub:** la descarga de la configuración que mantiene funcionando la reproducción (sin datos tuyos) y la comprobación de actualizaciones (activada por defecto; se puede apagar en los ajustes).
- **Gumroad:** cuando pagas, Gumroad recibe tus datos de pago y de facturación según su propia política. Nosotros no recibimos ni guardamos datos de tarjeta; vemos en nuestro panel de Gumroad los datos de la venta que Gumroad nos facilita (por ejemplo, el correo, la fecha, el importe y el estado de la suscripción).

**Traducción automática de letras (activada por defecto; se puede apagar en los ajustes):** el **texto de la letra** va a **Google Translate**. Si no hay traducción, se consulta a **SimpMusic** con el identificador de la canción, y como último recurso el texto de la letra va a nuestro servidor de IA (apartado 2).

**Según las funciones que uses:**

- **Proveedores de letras:** título, artista, álbum y duración de la canción, hasta que uno tiene la letra. Puedes elegir el orden.
- **iTunes (Apple), Apple Music y otros proveedores de metadatos y portadas animadas:** nombres de canción, artista y álbum.
- **Wikipedia:** el nombre del artista, cuando ves su información.
- **Shazam:** la firma de audio del reconocimiento de canciones, solo mientras lo usas.
- **Tu propio proveedor de IA:** si configuras tu propia clave, el texto de tus peticiones de IA va a ese proveedor en lugar de a nuestro servidor.

**Integraciones opcionales (no envían nada hasta que las activas):** Last.fm (scrobbling e importación de gustos), ListenBrainz, importación desde Spotify, SponsorBlock, AutoEQ, podcasts y similares. Cada una envía solo lo que necesita (por ejemplo, la canción que suena, un modelo de auriculares o un término de búsqueda). Al iniciar sesión en YouTube, Spotify o Last.fm, tus credenciales van directamente a ese servicio.

## 4. Inteligencia artificial

La aplicación se desarrolla con ayuda de herramientas de IA bajo la dirección del desarrollador. Esas herramientas **no están conectadas a la aplicación** ni reciben tus datos de forma automática. Si nos envías un registro de diagnóstico o un reporte, podemos analizarlo con su ayuda; por eso el registro no incluye títulos, artistas, cuentas, correos ni claves.

Dentro de la aplicación, las funciones de IA envían a nuestro servidor de IA (modelos abiertos de terceros alojados en Cloudflare) solo esto:

| Función | Por defecto | Qué envía |
|---|---|---|
| **Lista IA y "Pedir música"** | Disponible; solo envía cuando la usas | El texto que escribes y, al modificar una lista, los títulos y artistas de sus canciones. |
| **"Recomendado para ti (IA)"** | **Desactivada** | Al activarla, periódicamente: hasta 16 canciones (título y artista) de las que más escuchas y de tus me gusta, hasta 8 de tus artistas más frecuentes, tus géneros dominantes y hasta 24 títulos de la lista anterior. |
| **Segunda opinión de IA de la cola** | **Desactivada** | Lo indicado en el apartado 2. |
| **Traducción de letras** | **Activada** | Solo como último recurso: el texto de la letra (apartado 3). |

## 5. Para qué usamos los datos

Solo para que la aplicación funcione: reproducir, buscar, mostrar letras y carátulas, verificar tu licencia o tu demo, sincronizar tus dispositivos, las salas de Escuchar juntos, las funciones de IA que uses y la investigación de fallos cuando tú nos envías un registro. **No** los usamos para publicidad, no elaboramos perfiles y no los vendemos ni los compartimos para publicidad. Solo los revelaríamos si una ley o una autoridad competente nos obligara.

## 6. Tus derechos

Puedes pedirnos, escribiendo a **aurahires@gmail.com**:

- **Acceso:** saber qué datos tuyos guardan nuestros servicios y obtener una copia.
- **Rectificación:** corregirlos.
- **Supresión:** borrarlos (por ejemplo, el vínculo de tu licencia con tus dispositivos, tu grupo de sincronización o tus copias en la nube). El registro de demo de cada dispositivo (su identificador técnico y la fecha de inicio) es una excepción: lo conservamos mientras sea necesario para impedir que la demo se repita.
- **Oposición:** que dejemos de tratarlos para una finalidad concreta. Además, puedes apagar tú mismo los Servicios Aura, la sincronización, la traducción automática de letras y las funciones de IA.

Para comprobar que la petición es tuya, te pediremos que nos escribas desde el correo de la compra o que nos indiques tu clave de licencia. Te responderemos en un plazo máximo de **30 días**.

En Honduras, el **artículo 182 de la Constitución** reconoce la garantía de **hábeas data**: el derecho a acceder a la información sobre ti que conste en archivos o registros, y a pedir que se corrija, se actualice o se suprima. Si resides en otro país, conservas los derechos que te reconozca su ley, y puedes reclamar ante su autoridad de protección de datos o de protección al consumidor.

## 7. Seguridad

Las comunicaciones con nuestros servicios van cifradas (HTTPS). La copia en la nube se cifra en tu dispositivo (AES-256-GCM) antes de enviarse. Tu grupo de sincronización y el identificador de tu copia en la nube son huellas cifradas que no revelan tu secreto. Ningún sistema es perfectamente seguro; si detectamos un incidente que afecte a tus datos, lo comunicaremos en la aplicación.

## 8. Menores

La aplicación no está dirigida a menores de 13 años y no recogemos a sabiendas datos de menores de 13 años. Si eres padre, madre o tutor y crees que un menor nos ha enviado datos, escríbenos y los borraremos.

## 9. Dónde se tratan los datos

Nuestros servicios funcionan en la red global de Cloudflare, así que tus peticiones pueden procesarse fuera de tu país. Los terceros del apartado 3 tratan los datos en sus propias ubicaciones.

## 10. Cambios en esta política

Cada versión lleva su número y su fecha de entrada en vigor. Si un cambio amplía los datos que se envían o cómo se usan, te avisaremos en la aplicación antes de aplicarlo y, cuando afecte a los Términos y Condiciones, se te pedirá aceptarlos de nuevo.

## 11. Contacto

- **Correo:** aurahires@gmail.com
- **Responsable:** el desarrollador independiente de Aura Hi-Res v2 (Honduras) · aurahires@gmail.com
- **Proyecto:** [https://github.com/hck0n3/Aura-Hi-Res-v2](https://github.com/hck0n3/Aura-Hi-Res-v2)

Cuando usas **Reporte de errores y sugerencias** en la aplicación, se abre tu propia aplicación de correo con un mensaje dirigido a aurahires@gmail.com. Solo recibimos lo que decidas enviar (y los registros o capturas que decidas adjuntar).
