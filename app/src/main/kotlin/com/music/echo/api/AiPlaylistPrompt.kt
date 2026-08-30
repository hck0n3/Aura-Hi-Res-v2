package iad1tya.echo.music.api

/**
 * Builds the chat messages for a text-to-playlist request. Pure (no Android/network) so it is
 * unit-testable; [AiPlaylistService] turns the returned messages into the provider's JSON body.
 */
object AiPlaylistPrompt {
    fun buildMessages(
        prompt: String,
        count: Int,
        excludeTitles: List<String> = emptyList(),
    ): List<ChatMessage> {
        val system = """
            Eres un ejecutor EXACTO de peticiones musicales. NO eres un recomendador.
            Devuelve SOLO un objeto JSON válido, sin texto adicional y sin formato markdown:
            {"name": string, "tracks": [{"title": string, "artist": string, "year": number}]}

            CERO INVENCIÓN — la regla MÁS importante de todas:
            ONLY include songs you are CERTAIN exist and are REAL releases by the stated artist.
            NEVER invent or approximate titles.
            If unsure about a song, SKIP it and suggest another well-known one.
            Prefer each artist's MOST POPULAR/streamed tracks.
            Output ONLY from your certain knowledge.
            Un título "que suena plausible" pero que no recuerdas con certeza es una canción
            INVENTADA: omítela y proponga otra famosa que sí exista.

            PROHIBIDO (si lo haces, falla la tarea):
            - Improvisar, "mejorar", ampliar, reinterpretar o "sugerir similares".
            - Añadir artistas, géneros, épocas o moods que el usuario NO escribió.
            - Sustituir un género por uno "relacionado" (punk≠rock, salsa≠latín, etc.).
            - Rellenar con éxitos genéricos o "clásicos" fuera del brief.
            - Inventar canciones falsas o "aproximar" títulos (medio título, versión
              rara, track inexistente, canción "parecida").

            OBLIGATORIO:
            - Cumple la petición al pie de la letra: cada canción debe ser lógica y verificable
              respecto a lo pedido (artista/género/idioma/época/mood/restricciones).
            - Incluye hasta $count canciones. Si no puedes completar $count canciones CIERTAS
              dentro del brief, devuelve MENOS — NUNCA rellenes con material ajeno ni con
              canciones dudosas.
            - "name" ≤ 40 caracteres, fiel a la petición.
            - "year" = año ORIGINAL (4 dígitos) de esa grabación.
            - Si piden UN artista/grupo: TODAS las pistas son de ese artista (crédito principal).
              "artist" debe ser ese nombre (o colaboración donde figure primero).
            - Si piden "sin X" / "nada de Y": cero track puede ser X/Y.
            - No repitas canciones. No añadas explicaciones.

            Error típico PROHIBIDO: "solo Bad Bunny" → devolver J Balvin / reggaeton genérico;
            "punk de los 70" → devolver rock alternativo de 2010.
        """.trimIndent()
        val solo = AiPlaylistConstraints.extractSoloArtist(prompt)
        val soloLock = if (solo != null) {
            """
            RESTRICCIÓN BLOQUEANTE: artista único = "$solo".
            Cada "artist" DEBE ser "$solo" (o colaboración con $solo primero).
            Cero artistas distintos. Canciones FAMOSAS y verificables de ese artista —
            nunca títulos raros, versiones oscuras ni canciones inventadas.
            Mejor pocas canciones correctas que $count incorrectas.
            """.trimIndent()
        } else {
            ""
        }
        // TOP-UP EXCLUSIONS travel as a separate rule in the USER message, NEVER concatenated into
        // `prompt`: AiPlaylistConstraints.extractSoloArtist re-parses the prompt on every generate
        // call, and a naive "solo X. NO incluyas: A, B, C" made it swallow the whole sentence as
        // the "artist" (too long → null → the solo lock silently died on the top-up round). Keeping
        // the exclusion here leaves the prompt — and the solo-artist extraction — byte-identical.
        val exclusions = excludeTitles.filter { it.isNotBlank() }.distinct().take(MAX_EXCLUDE_TITLES)
        val exclusionBlock = if (exclusions.isEmpty()) {
            ""
        } else {
            """
            Canciones YA elegidas (PROHIBIDO repetirlas — ${exclusions.size}): ${exclusions.joinToString(" | ") { "\"$it\"" }}
            Si no puedes proponer otras $count canciones CIERTAS sin repetir las de arriba,
            devuelve MENOS. Do NOT pad with uncertain songs.
            """.trimIndent()
        }
        val user = """
            Petición EXACTA (copia literal — no la cambies): "$prompt"
            Devuelve canciones que cumplan ESA petición y NADA más.
            Cero improvisación. Cero similares. Cero ampliación del brief.
            Cada canción debe EXISTIR de verdad (título y artista reales y verificables).
            Si dudas de una canción, omítela — mejor menos canciones correctas.
            $soloLock
            $exclusionBlock
        """.trimIndent().trim()
        return listOf(
            ChatMessage(role = "system", content = system),
            ChatMessage(role = "user", content = user),
        )
    }

    /**
     * Upper bound on how many tracks of the playlist are serialized into a modify request. Caps the
     * prompt size (tokens ≈ battery/heat) and keeps the reply small. Honest limitation: on a playlist
     * longer than this, only the first [MAX_MODIFY_TRACKS] are visible to the AI, so only those can be
     * removed. Additions are unaffected.
     */
    const val MAX_MODIFY_TRACKS = 200

    /**
     * Cap on already-chosen titles serialized into a top-up request. The exclusion list exists so the
     * second AI round cannot re-propose what is already in the playlist; a bounded list keeps the
     * token cost of that rule flat (battery/heat rule) even for large targets.
     */
    const val MAX_EXCLUDE_TITLES = 60

    /**
     * Builds the chat messages for "edit this playlist with a text instruction".
     *
     * PRIVACY / CORRECTNESS: the playlist is serialized as a 1-based NUMBERED list and the model is
     * asked to answer with those same positions. Room's autoincrement `PlaylistSongMap.id` (or any
     * other internal identifier) is NEVER sent — the caller maps positions back to rows itself, so a
     * hallucinated number can only ever be an out-of-range position, never a pointer at an unrelated
     * database row.
     *
     * [currentTracks] must be the playlist in display order; the caller applies the returned indices
     * against that exact same snapshot.
     */
    fun buildModifyMessages(currentTracks: List<TrackQuery>, prompt: String): List<ChatMessage> {
        val visible = currentTracks.take(MAX_MODIFY_TRACKS)
        val system = """
            Eres un editor EXACTO de playlists. NO improvises.
            Devuelve SOLO JSON válido, sin markdown:
            {"remove": [number], "additions": [{"title": string, "artist": string}]}
            Reglas:
            - "remove" = números de posición (desde 1) EXACTOS de la lista. Nada inventado.
            - "additions" = SOLO canciones REALES pedidas por la instrucción. If unsure a song
              exists, SKIP it — NEVER invent or approximate titles. No añadas extras "porque
              quedan bien" ni rellenes con material dudoso.
            - Cambio MÍNIMO: solo lo que la instrucción pide. Cero improvisación.
            - Si solo pide quitar → "additions": []. Si solo pide añadir → "remove": [].
            - Error prohibido: ante "quita las lentas", borrar media playlist o canciones que no lo son.
        """.trimIndent()
        val numbered = visible.mapIndexed { index, track ->
            val artist = track.artist.ifBlank { "?" }
            "${index + 1}. \"${track.title}\" — $artist"
        }.joinToString("\n")
        val user = """
            Playlist actual (${visible.size} canciones):
            $numbered

            Instrucción EXACTA (cero improvisación): "$prompt"
        """.trimIndent()
        return listOf(
            ChatMessage(role = "system", content = system),
            ChatMessage(role = "user", content = user),
        )
    }
}
