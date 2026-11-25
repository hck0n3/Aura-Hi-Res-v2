package iad1tya.echo.music.ui.screens.welcome

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 871 (plan phase 2): after the licence the welcome is «Conecta tu música» → «Listo», with no artists or
 * genres step; those two stay reachable from Ajustes ▸ Contenido (hiding is losing, AGENTS rule 5). There is no Compose
 * runtime in the unit job, so the wiring is pinned as source text.
 */
class WelcomeConnectGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun `the first run opens Conecta tu musica, never the artists step`() {
        val activity = main("MainActivity.kt")
        assertFalse(activity.contains("navController.navigate(\"onboarding_artists\")"))
        assertTrue(activity.contains("navController.navigate(iad1tya.echo.music.ui.screens.welcome.WELCOME_CONNECT_ROUTE)"))
        // The tour still comes last, on arrival at Inicio (row 209 e).
        assertTrue(activity.contains("pendingWelcomeTour = true\n                            navController.navigate(iad1tya.echo.music.ui.screens.welcome.WELCOME_CONNECT_ROUTE)"))
        val nav = main("ui/screens/NavigationBuilder.kt")
        assertTrue(nav.contains("composable(iad1tya.echo.music.ui.screens.welcome.WELCOME_CONNECT_ROUTE)"))
        assertTrue(nav.contains("composable(iad1tya.echo.music.ui.screens.welcome.WELCOME_DONE_ROUTE)"))
        // The onboarding_ prefix keeps the bottom bar away (row 494).
        assertTrue(WELCOME_CONNECT_ROUTE.startsWith("onboarding_"))
        assertTrue(WELCOME_DONE_ROUTE.startsWith("onboarding_"))
    }

    @Test
    fun `every option opens a screen that already exists, and none is a dead row`() {
        val connect = main("ui/screens/welcome/WelcomeConnectScreen.kt")
        val nav = main("ui/screens/NavigationBuilder.kt")
        val routes = Regex("""ActionLog\.tap\(ActionId\.WELCOME_CONNECT_\w+\); open\("([^"]+)"\)""").findAll(connect).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf("settings/ytm_sync?onboarding=true", "local_songs", "settings/spotify_import?onboarding=true", "migration", "settings/backup_restore"),
            routes,
        )
        assertTrue(nav.contains("route = \"settings/ytm_sync?onboarding={onboarding}\""))
        assertTrue(nav.contains("composable(\"local_songs\")"))
        assertTrue(nav.contains("route = \"settings/spotify_import?onboarding={onboarding}&link={link}\""))
        assertTrue(nav.contains("composable(\"migration\")"))
        assertTrue(nav.contains("composable(\"settings/backup_restore\")"))
        // The private cloud arrives with plan phase 5: no row until something is behind it.
        assertEquals("four rows, five destinations, no cloud", 5, routes.size)
        assertFalse(connect.contains("R.drawable.cloud"))
        assertFalse(connect.contains("R.string.welcome_connect_cloud"))
        // Everything is optional: the bottom button goes on to «Listo» with or without a choice.
        assertTrue(connect.contains("ActionLog.tap(ActionId.WELCOME_CONNECT_NEXT); navController.navigate(WELCOME_DONE_ROUTE)"))
    }

    @Test
    fun `the Google and Spotify screens opened from the welcome lead back to it`() {
        listOf("ui/screens/settings/YtmSyncScreen.kt", "ui/screens/SpotifyImportScreen.kt").forEach {
            val src = main(it)
            assertTrue(it, src.contains("navController.popBackStack(iad1tya.echo.music.ui.screens.welcome.WELCOME_CONNECT_ROUTE, inclusive = false)"))
            assertFalse(it, src.contains("popUpTo(\"onboarding_artists\")"))
            assertFalse(it, src.contains("navController.navigate(\"onboarding_youtube\")"))
        }
    }

    @Test
    fun `Listo ends the welcome for good`() {
        val done = main("ui/screens/welcome/WelcomeConnectScreen.kt").substringAfter("fun WelcomeDoneScreen(")
        assertTrue(done.contains("LaunchedEffect(Unit) { context.dataStore.edit { it[WelcomeDoneKey] = true } }"))
        assertTrue(done.contains("popUpTo(WELCOME_CONNECT_ROUTE) { inclusive = true }"))
    }

    @Test
    fun `favourite artists and genres stay reachable from Ajustes - Contenido and come back there`() {
        val content = main("ui/screens/settings/ContentSettings.kt")
        assertTrue(content.contains("onClick = { navController.navigate(\"onboarding_artists\") }"))
        assertTrue(content.contains("onClick = { navController.navigate(\"onboarding_genres\") }"))
        val index = main("ui/screens/settings/SearchableSettings.kt")
        assertTrue(index.contains("R.string.content_favorite_artists"))
        assertTrue(index.contains("R.string.content_favorite_genres"))
        val artists = main("ui/screens/OnboardingArtistsScreen.kt")
        val genres = main("ui/screens/OnboardingGenresScreen.kt")
        assertFalse(artists.contains("navController.navigate(\"onboarding_genres\")"))
        assertFalse(genres.contains("navController.navigate(\"onboarding_spotify\")"))
        assertTrue(artists.contains("navController.popBackStack()"))
        assertTrue(genres.contains("navController.popBackStack()"))
        // Opened as a settings editor, the genres screen starts from what is saved, so «Guardar» never wipes it.
        assertTrue(genres.contains("val saved = context.dataStore.data.first()[OnboardingGenresKey].orEmpty()"))
    }
}
