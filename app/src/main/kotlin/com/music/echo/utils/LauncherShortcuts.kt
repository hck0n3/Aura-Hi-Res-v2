package iad1tya.echo.music.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import iad1tya.echo.music.MainActivity
import iad1tya.echo.music.R
import timber.log.Timber

/**
 * Registry row 785: the launcher's long-press shortcuts (Buscar / Biblioteca). They lived in `res/xml/shortcuts.xml` with
 * a target package written by hand (`iad1tya.echo.music`) — the OLD, frozen app — while this build is `iad1tya.aura.music`
 * (`.debug`, `.dev` with -Pnosub), so the shortcut opened the old app or nothing. A resource file cannot follow the build's
 * applicationId; the intent built here takes it from the running app ([Intent.setClassName] with a Context).
 */
object LauncherShortcuts {
    private const val TAG = "LauncherShortcuts"

    fun publish(context: Context, searchAction: String, libraryAction: String) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val shortcuts = listOf(
            // New ids: a pinned copy of a removed manifest shortcut stays immutable, and reusing its id would make the call throw.
            shortcut(context, "search_v2", searchAction, R.string.search, R.drawable.shortcut_search),
            shortcut(context, "library_v2", libraryAction, R.string.filter_library, R.drawable.shortcut_library),
        )
        runCatching { manager.setDynamicShortcuts(shortcuts) }
            .onFailure { Timber.tag(TAG).w(it, "Launcher shortcuts not published") }
    }

    private fun shortcut(context: Context, id: String, action: String, label: Int, icon: Int): ShortcutInfo =
        ShortcutInfo.Builder(context, id)
            .setShortLabel(context.getString(label))
            .setIcon(Icon.createWithResource(context, icon))
            .setIntent(Intent(action).setClassName(context, MainActivity::class.java.name))
            .build()
}
