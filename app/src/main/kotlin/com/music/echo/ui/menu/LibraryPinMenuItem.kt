package iad1tya.echo.music.ui.menu

import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.component.Material3MenuItemData
import iad1tya.echo.music.utils.LibraryPin
import iad1tya.echo.music.utils.LibraryPinKind
import iad1tya.echo.music.utils.LibraryPins
import iad1tya.echo.music.utils.LibraryPinsStore
import iad1tya.echo.music.utils.rememberLibraryPins

/**
 * The «Anclar en Biblioteca» row every playlist / album / artist menu carries (owner 2026-10-20: pin them to the Library's
 * lower part). ONE implementation for the six menus — local and YouTube, playlist / album / artist — so the rule (cap, toast,
 * where it is written) cannot drift between them. A menu adds a single `item { Material3MenuGroup(listOf(libraryPinMenuItem(…))) }`.
 *
 * Toggles: pinned → «Quitar de Biblioteca», not pinned → «Anclar en Biblioteca». The write goes through
 * [LibraryPinsStore.updateDetached] (an app-lifetime scope), because the sheet is dismissed right after the tap and a scope
 * tied to the sheet's composition would be cancelled before the DataStore edit lands.
 */
@Composable
fun libraryPinMenuItem(pin: LibraryPin, onDismiss: () -> Unit): Material3MenuItemData {
    val context = LocalContext.current
    val pins by rememberLibraryPins()
    val pinned = LibraryPins.isPinned(pins, pin.kind, pin.id)
    val full = !pinned && !LibraryPins.canAdd(pins)
    val label = stringResource(if (pinned) R.string.library_unpin else R.string.library_pin)
    val toastText = when {
        full -> stringResource(R.string.library_pins_full, LibraryPins.MAX_PINS)
        pinned -> stringResource(R.string.library_unpinned_toast)
        else -> stringResource(R.string.library_pinned_toast)
    }
    return Material3MenuItemData(
        title = { Text(text = label) },
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_push_pin),
                contentDescription = null,
            )
        },
        onClick = {
            if (!full) {
                LibraryPinsStore.updateDetached(context) { current -> LibraryPins.toggled(current, pin) }
            }
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
            onDismiss()
        },
    )
}

/** A local or YouTube PLAYLIST. [local] = [id] is a local row id (the local-playlist menu), not a YouTube id. */
@Composable
fun playlistPinMenuItem(
    id: String,
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    local: Boolean,
    onDismiss: () -> Unit,
): Material3MenuItemData = libraryPinMenuItem(
    LibraryPin(LibraryPinKind.PLAYLIST, id, title, subtitle = subtitle, thumbnailUrl = thumbnailUrl, local = local),
    onDismiss,
)

/** An ALBUM (library or YouTube). [playlistId] lets the pinned album rebuild the YouTube menu outside the library. */
@Composable
fun albumPinMenuItem(
    id: String,
    title: String,
    artists: List<String>,
    thumbnailUrl: String?,
    playlistId: String?,
    onDismiss: () -> Unit,
): Material3MenuItemData = libraryPinMenuItem(
    LibraryPin(
        LibraryPinKind.ALBUM,
        id,
        title,
        subtitle = artists.joinToString(", ").takeIf { it.isNotBlank() },
        thumbnailUrl = thumbnailUrl,
        extra = playlistId?.takeIf { it.isNotBlank() },
    ),
    onDismiss,
)

/** An ARTIST (library or YouTube). */
@Composable
fun artistPinMenuItem(
    id: String,
    name: String,
    thumbnailUrl: String?,
    onDismiss: () -> Unit,
): Material3MenuItemData = libraryPinMenuItem(
    LibraryPin(LibraryPinKind.ARTIST, id, name, thumbnailUrl = thumbnailUrl),
    onDismiss,
)
