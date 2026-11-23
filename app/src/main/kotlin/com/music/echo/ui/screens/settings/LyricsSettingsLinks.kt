package iad1tya.echo.music.ui.screens.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem

/**
 * The four places lyrics settings live (UX audit P22D). They are NOT moved or merged — every control
 * stays where it is (AGENTS.md rule 5: hiding is losing) — they are LINKED.
 */
enum class LyricsSettingsPlace(val route: String) {
    /** Apariencia ▸ Letra: position, size, animation, blur… */
    APPEARANCE("settings/appearance"),
    /** Contenido ▸ Letra: providers and their priority. */
    SOURCES("settings/content"),
    /** Romanización de letras. */
    ROMANIZATION("settings/content/romanization"),
    /** Traducción de letras con IA. */
    AI("settings/ai"),
}

/**
 * «Más ajustes de letras»: the same four rows on each of the four lyrics screens. The other three open
 * their screen; the one for THIS screen says «Estás aquí» and does nothing. Keeping all four visible on
 * every screen is what lets the settings search index them without a ghost entry: each label is on
 * screen at the route it is indexed under (`SearchableSettings.kt`), so searching «letras» returns the
 * four places and each result jumps to this block on its own screen.
 */
@Composable
fun LyricsSettingsLinks(navController: NavController, here: LyricsSettingsPlace) {
    val hereText = stringResource(R.string.lyrics_link_here)
    val hereDescription: @Composable () -> Unit = { Text(hereText) }
    val links = listOf(
        Triple(LyricsSettingsPlace.APPEARANCE, R.drawable.palette, stringResource(R.string.lyrics_link_appearance)),
        Triple(LyricsSettingsPlace.SOURCES, R.drawable.lyrics, stringResource(R.string.lyrics_link_sources)),
        Triple(LyricsSettingsPlace.ROMANIZATION, R.drawable.language_korean_latin, stringResource(R.string.lyrics_link_romanization)),
        Triple(LyricsSettingsPlace.AI, R.drawable.translate, stringResource(R.string.lyrics_link_ai)),
    )
    Material3SettingsGroup(
        title = stringResource(R.string.lyrics_more_settings),
        items = links.map { (place, icon, label) ->
            val isHere = place == here
            Material3SettingsItem(
                icon = painterResource(icon),
                title = { Text(label) },
                description = if (isHere) hereDescription else null,
                onClick = if (isHere) null else ({ navController.navigate(place.route) }),
            )
        },
    )
}
