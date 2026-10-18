package iad1tya.echo.music.ui.newui

import androidx.navigation.NavController

/**
 * True when the screen on top of the back stack already IS [route] (`album/<id>`, `artist/<id>`, `local_playlist/<id>`…:
 * same destination pattern, same argument value).
 */
internal fun NavController.isAlreadyAt(route: String): Boolean {
    val id = route.substringAfter('/', "")
    if (id.isEmpty()) return false
    val current = currentBackStackEntry ?: return false
    val args = current.arguments ?: return false
    if (current.destination.route?.startsWith(route.substringBefore('/') + "/") != true) return false
    return args.keySet().any { key -> args.getString(key) == id }
}

/**
 * Navigates to [route] unless you are already there. Owner 2026-10-18 («que la exploración sea lo más lógica posible»):
 * the player's doors to an album / artist / playlist pushed a second copy of the page you were on, so Back had to be
 * pressed twice to leave it. Every door out of the player goes through here.
 */
internal fun NavController.navigateOnce(route: String) {
    if (!isAlreadyAt(route)) navigate(route)
}
