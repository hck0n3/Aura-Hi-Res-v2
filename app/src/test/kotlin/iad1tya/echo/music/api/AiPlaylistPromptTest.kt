package iad1tya.echo.music.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPlaylistPromptTest {

    @Test fun buildsSystemThenUserMessage() {
        val messages = AiPlaylistPrompt.buildMessages("rock para correr de noche", 20)
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
    }

    @Test fun userMessageContainsPromptAndCount() {
        val messages = AiPlaylistPrompt.buildMessages("rock para correr de noche", 20)
        assertTrue(messages[1].content.contains("rock para correr de noche"))
        assertTrue(messages[0].content.contains("20") || messages[1].content.contains("20"))
    }

    @Test fun systemMessageAsksForJsonWithTheRequestedCount() {
        val messages = AiPlaylistPrompt.buildMessages("boleros tristes", 30)
        assertTrue(messages[0].content.contains("JSON"))
        assertTrue(messages[0].content.contains("30"))
    }

    @Test fun systemMessageAsksForAYearAndACounterExample() {
        val system = AiPlaylistPrompt.buildMessages("rock", 10)[0].content
        assertTrue(system.contains("\"year\": number"))
        assertTrue(system.contains("PROHIBIDO") || system.contains("Bad Bunny"))
    }

    @Test fun systemMessageForbidsImprovisation() {
        val system = AiPlaylistPrompt.buildMessages("solo Bad Bunny", 10)[0].content
        assertTrue(system.contains("PROHIBIDO") || system.contains("improvis"))
        assertTrue(system.contains("EXACTO") || system.contains("OBLIGATORIO"))
    }

    @Test fun soloArtistPromptLocksUserMessage() {
        val user = AiPlaylistPrompt.buildMessages("solo Bad Bunny", 12)[1].content
        assertTrue(user.contains("RESTRICCIÓN BLOQUEANTE"))
        assertTrue(user.contains("Bad Bunny"))
        assertTrue(user.contains("Cero improvisación") || user.contains("improvis"))
    }

    // --- anti-hallucination (owner directive 2026-08-30: "FUNCIONA pero NO improvise") ---------

    @Test fun systemMessageCarriesAntiHallucinationRules() {
        val system = AiPlaylistPrompt.buildMessages("solo Bad Bunny", 10)[0].content
        assertTrue(system.contains("CERTAIN exist"))
        assertTrue(system.contains("NEVER invent or approximate titles"))
        assertTrue(system.contains("SKIP it and suggest another well-known one"))
        assertTrue(system.contains("MOST POPULAR/streamed tracks"))
        assertTrue(system.contains("Output ONLY from your certain knowledge"))
    }

    @Test fun userMessageRepeatsTheCertaintyRequirement() {
        val user = AiPlaylistPrompt.buildMessages("rock", 10)[1].content
        assertTrue(user.contains("EXISTIR de verdad"))
        assertTrue(user.contains("Si dudas de una canción, omítela"))
    }

    @Test fun soloLockAsksForFamousVerifiableTracks() {
        val user = AiPlaylistPrompt.buildMessages("solo Feid", 10)[1].content
        assertTrue(user.contains("FAMOSAS y verificables"))
    }

    // --- top-up exclusions: structured, never prompt-concatenated -----------------------------

    @Test fun exclusionsTravelInUserMessageNotInThePromptText() {
        val messages = AiPlaylistPrompt.buildMessages(
            prompt = "solo Bad Bunny",
            count = 10,
            excludeTitles = listOf("Tití Me Preguntó", "Moscow Mule"),
        )
        // The system prompt must stay byte-stable for every consumer (the Aura Worker's
        // AI_PLAYLIST_SYSTEM_MARKER detects playlist requests by its literal opening).
        assertTrue(messages[0].content.startsWith("Eres un ejecutor EXACTO"))
        // The PROMPT itself is untouched: the old top-up concatenated "NO incluyas…: A, B" onto a
        // "solo X" prompt, which extractSoloArtist then mis-parsed as the artist name (the solo
        // lock silently died on the top-up round). Exclusions must live in their own block.
        assertTrue(messages[1].content.contains("Petición EXACTA (copia literal — no la cambies): \"solo Bad Bunny\""))
        assertFalse(messages[1].content.contains("NO incluyas ninguna de estas canciones"))
        assertTrue(messages[1].content.contains("Canciones YA elegidas"))
        assertTrue(messages[1].content.contains("Tití Me Preguntó"))
        assertTrue(messages[1].content.contains("Moscow Mule"))
    }

    @Test fun exclusionBlockAsksForHonestFewerNotPadding() {
        val user = AiPlaylistPrompt.buildMessages("rock", 5, excludeTitles = listOf("A"))[1].content
        assertTrue(user.contains("Do NOT pad with uncertain songs"))
        assertTrue(user.contains("PROHIBIDO repetirlas"))
    }

    @Test fun noExclusionBlockWhenListEmpty() {
        val user = AiPlaylistPrompt.buildMessages("rock", 5)[1].content
        assertFalse(user.contains("Canciones YA elegidas"))
        // And blanks/duplicates are dropped, not serialized.
        val deduped = AiPlaylistPrompt.buildMessages("rock", 5, excludeTitles = listOf("", "X", "X"))[1].content
        assertTrue(deduped.contains("\"X\""))
        assertFalse(deduped.contains("\"\""))
    }

    @Test fun exclusionListIsCapped() {
        val many = (1..(AiPlaylistPrompt.MAX_EXCLUDE_TITLES + 20)).map { "Song $it" }
        val user = AiPlaylistPrompt.buildMessages("rock", 5, excludeTitles = many)[1].content
        assertTrue(user.contains("Song ${AiPlaylistPrompt.MAX_EXCLUDE_TITLES}"))
        assertFalse(user.contains("Song ${AiPlaylistPrompt.MAX_EXCLUDE_TITLES + 1}"))
    }

    @Test fun modifyPromptAlsoForbidsInventedAdditions() {
        val system = AiPlaylistPrompt.buildModifyMessages(listOf(TrackQuery("Uno", "A")), "x")[0].content
        assertTrue(system.contains("If unsure a song"))
        assertTrue(system.contains("NEVER invent or approximate titles"))
    }

    // --- modify -----------------------------------------------------------------------------

    @Test fun modifyNumbersTheTracksOneBasedAndCarriesTheInstruction() {
        val messages = AiPlaylistPrompt.buildModifyMessages(
            listOf(TrackQuery("Uno", "A"), TrackQuery("Dos", "B")),
            "quita las lentas",
        )
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        val user = messages[1].content
        assertTrue(user.contains("1. \"Uno\" — A"))
        assertTrue(user.contains("2. \"Dos\" — B"))
        assertTrue(user.contains("quita las lentas"))
    }

    @Test fun modifyAsksForRemoveAndAdditionsSchema() {
        val system = AiPlaylistPrompt.buildModifyMessages(listOf(TrackQuery("Uno", "A")), "x")[0].content
        assertTrue(system.contains("\"remove\""))
        assertTrue(system.contains("\"additions\""))
    }

    /** The AI must only ever see positions — never a database id. */
    @Test fun modifyCapsTheSerializedPlaylist() {
        val many = (1..(AiPlaylistPrompt.MAX_MODIFY_TRACKS + 40)).map { TrackQuery("T$it", "A$it") }
        val user = AiPlaylistPrompt.buildModifyMessages(many, "quita las lentas")[1].content
        assertTrue(user.contains("${AiPlaylistPrompt.MAX_MODIFY_TRACKS}. \"T${AiPlaylistPrompt.MAX_MODIFY_TRACKS}\""))
        assertTrue(!user.contains("${AiPlaylistPrompt.MAX_MODIFY_TRACKS + 1}. \"T${AiPlaylistPrompt.MAX_MODIFY_TRACKS + 1}\""))
    }
}
