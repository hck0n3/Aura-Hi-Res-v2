# Aura Hi-Res v2.0.69-beta7 — beta privada: emparejamiento exacto de canciones, Amazon Music para migrar y enlaces compartidos que abren la canción correcta

Beta solo para probar, sobre la 2.0.69-beta6 que tienes instalada (incluye todo lo de las betas anteriores; sus notas siguen valiendo y se juntarán en la versión estable). Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma. Te llega por el actualizador solo con «Recibir versiones beta» encendido (Ajustes ▸ Actualizaciones).

## Emparejamiento exacto de canciones

- **Nunca pone otra versión en lugar de la que pediste:** al migrar listas o importar de Spotify, una versión en vivo, remix, cover, acústica, karaoke, instrumental, «sped up» o «slowed» ya no se acepta en lugar de la canción original (ni al revés).
- **Sí reconoce la misma grabación con otro nombre:** «Remastered 2009», «Clean», «Explicit» o un «feat.» en el título ya no impiden encontrarla.
- **Artista y duración exigentes:** el artista tiene que coincidir y la duración no puede variar más de 2,5 segundos (5 en remasterizaciones). Los títulos en coreano, japonés, ruso, etc. se comparan bien.
- **Puede que veas más canciones en «no encontradas»:** es a propósito. Es mejor que te pregunte a que ponga una canción equivocada.
- **Una sola vez, al actualizar,** se borran los emparejamientos automáticos que estaban guardados de antes para que se vuelvan a calcular con estas reglas. Los que elegiste tú a mano se conservan.
- Reglas adaptadas del proyecto libre SongMirror (licencia MIT).

## Amazon Music para migrar

- **Nueva fuente en Migrar lista:** «Amazon Music». Inicias sesión en Amazon dentro de Aura, ves tus listas y tus «Canciones que te gustan», eliges cuáles pasar a YouTube Music y se emparejan con las reglas exactas de arriba.
- **Solo lectura:** Aura nunca crea, cambia ni borra nada en tu cuenta de Amazon.
- **Tu sesión de Amazon se guarda cifrada** en el teléfono y nunca va al registro. Puedes cerrarla con «Cerrar sesión de Amazon»; si Amazon la rechaza a mitad, Aura te pide entrar de nuevo.

## Enlaces que te comparten

- **Abrir un enlace de Deezer o Apple Music pone la canción exacta:** Aura lee el título, el artista y la duración del catálogo público de ese servicio y busca la misma grabación en YouTube Music.
- **Spotify:** también lee la duración y todos los artistas de la canción del enlace.
- **Los álbumes solo se abren si coinciden el artista y el título;** si no, te dice «no encontrado» y te lleva a la búsqueda, en vez de abrir otro álbum. Los sencillos y EP de Apple Music se reconocen.
- **Ya no se usa el servicio de Odesli para abrir enlaces** (cerró su acceso gratuito). Compartir desde Aura sigue igual: el enlace song.link abre la canción en la app de quien lo recibe.

## Para que lo pruebes y me cuentes

- No pude probar nada de esto en un aparato: está revisado leyendo el código y con pruebas automáticas, pero falta tu móvil.
- **Amazon Music:** Biblioteca ▸ botón Importar ▸ «Migrar lista» ▸ «Amazon Music»: inicia sesión, mira que salgan tus listas y migra una pequeña. Dime si el inicio de sesión de Amazon funciona dentro de la app.
- **Migrar o importar de Spotify:** comprueba que no se cuelan versiones en vivo o remix y mira cuántas quedan en «no encontradas».
- **Enlaces:** pídele a alguien un enlace de Spotify, Deezer o Apple Music y ábrelo con Aura: debe sonar esa misma canción.
- Si algo falla, manda el registro desde Ajustes ▸ Registros con la hora.
