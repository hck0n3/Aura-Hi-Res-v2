package com.music.innertube.pages

import com.music.innertube.models.MusicResponsiveHeaderRenderer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Registry row 726: the album page header's subtitle starts with the release type («Single • 2024»,
 * «EP • 2023», «Album • 2021»); the year is its last run (see [AlbumPage.getYear]). Fixtures follow the
 * 2024 `musicResponsiveHeaderRenderer` layout, decoded with InnerTube's own JSON settings.
 */
class AlbumPageTypeTextTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun header(subtitleRuns: String): MusicResponsiveHeaderRenderer = json.decodeFromString(
        """
        {
          "buttons": [],
          "title": {"runs": [{"text": "Some Release"}]},
          "subtitle": {"runs": $subtitleRuns},
          "secondSubtitle": {"runs": [{"text": "4 songs"}, {"text": " • "}, {"text": "13 minutes"}]},
          "straplineTextOne": {"runs": [{"text": "Some Artist"}]}
        }
        """.trimIndent(),
    )

    @Test
    fun `the first subtitle run is the type text`() {
        assertEquals("Single", AlbumPage.getTypeText(header("""[{"text": "Single"}, {"text": " • "}, {"text": "2024"}]""")))
        assertEquals("EP", AlbumPage.getTypeText(header("""[{"text": "EP"}, {"text": " • "}, {"text": "2023"}]""")))
        assertEquals("Album", AlbumPage.getTypeText(header("""[{"text": "Album"}, {"text": " • "}, {"text": "2021"}]""")))
        assertEquals("Sencillo", AlbumPage.getTypeText(header("""[{"text": "Sencillo"}, {"text": " • "}, {"text": "2024"}]""")))
    }

    @Test
    fun `a header without subtitle runs gives no type`() {
        assertNull(AlbumPage.getTypeText(header("[]")))
        assertNull(AlbumPage.getTypeText(header("null")))
        assertNull(AlbumPage.getTypeText(null))
    }
}
