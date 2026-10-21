package iad1tya.echo.music.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Biblioteca ▸ «Fijados» (owner 2026-10-20): the playlists, albums and artists the user pinned to the
 * lower part of the Library. A JSON array of `{kind, id, title, subtitle, cover, …}` in PIN ORDER (the
 * order is the user's own, set in «Editar fijados»). See `utils/LibraryPins.kt`.
 *
 * It lives in the DataStore `settings` and NOT in Room on purpose: a Room table is a schema change, and a
 * migration slip crashes every user on update (registry row 89). The DataStore file survives an app update
 * like every other preference (`SettingsSurviveUpdateTest`). It is NOT in the library backup: the backup
 * ZIP carries only `song.db` (registry «Reglas de contenido de las copias de seguridad»), exactly like the
 * pinned podcasts, the sort order and every other list the user keeps in preferences.
 *
 * These keys sit in their own file (not `PreferenceKeys.kt`) so the Library work does not collide with the
 * other streams that append to that shared file.
 */
val LibraryPinsKey = stringPreferencesKey("libraryPinsV1")

/** How the pinned section is ordered: `LibraryPinSort` (MANUAL by default = the user's drag order). */
val LibraryPinSortKey = stringPreferencesKey("libraryPinSort")

/** Direction of the non-manual orders of the pinned section. Ignored by MANUAL. */
val LibraryPinSortDescendingKey = booleanPreferencesKey("libraryPinSortDescending")

/** List or grid of the pinned section (`LibraryViewType`, GRID by default: big covers). */
val LibraryPinsViewTypeKey = stringPreferencesKey("libraryPinsViewType")
