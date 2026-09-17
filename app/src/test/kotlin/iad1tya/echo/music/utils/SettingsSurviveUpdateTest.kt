package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * # Los ajustes del usuario no se pierden al actualizar
 *
 * 🔴 OWNER ORDER (2026-09-16): *"que las configuraciones y preferencias del usuario nunca se pierdan
 * después de actualizar"*.
 *
 * Auditado el día de la orden, y la respuesta era que **ya no se pierden**: las preferencias viven en el
 * DataStore `settings`, que Android conserva a través de una actualización (mismo paquete, misma firma);
 * ningún punto del código las borra; no hay handler de corrupción que las reemplace en silencio; y el
 * único `fallbackToDestructiveMigration` del repo está sobre una caché recalculable, nunca sobre la base
 * de datos de música.
 *
 * Auditar no es garantizar. Esto fija las cuatro reglas de las que depende esa respuesta, porque cada una
 * se puede romper con una línea inocente y **ninguna falla de forma ruidosa**: el usuario simplemente
 * abre la app un día y tiene los ajustes de fábrica. Eso es lo que este archivo existe para impedir.
 *
 * Se analiza el FUENTE y no el runtime a propósito, por la misma razón que [
 * iad1tya.echo.music.db.MigrationCoverageTest]: lo que se rompe es un *sitio de llamada*, y ninguna
 * reflexión puede ver con qué se construyó un builder sin levantar la base de datos en un dispositivo.
 */
class SettingsSurviveUpdateTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private fun source(relative: String): String {
        val file = File(repoRoot, relative)
        assertTrue("$relative has moved — update this test to follow it", file.isFile)
        return file.readLines().joinToString("\n") { it.substringBefore("//") }
    }

    private val dataStoreSource by lazy { source("app/src/main/kotlin/com/music/echo/utils/DataStore.kt") }
    private val appModuleSource by lazy { source("app/src/main/kotlin/com/music/echo/di/AppModule.kt") }

    /**
     * 1. El archivo de preferencias se llama `settings` y no puede cambiar de nombre.
     *
     * Renombrarlo no da ningún error: DataStore crea uno nuevo, vacío, y el antiguo queda huérfano en el
     * disco con todo dentro. Para el usuario es indistinguible de "se borró todo al actualizar", y es
     * irreversible en la práctica porque nadie sabe que el archivo viejo sigue ahí.
     */
    @Test
    fun `the settings store keeps its name`() {
        assertTrue(
            "The preferences file was renamed. Every existing user would silently start from factory " +
                "defaults on update, with their real settings orphaned on disk under the old name.",
            // Escaped string, not a raw one: a raw string ending in a quote needs four quotes in a
            // row, which is legal Kotlin and unreadable for exactly the reviewer this test is for.
            Regex("preferencesDataStore\\(\\s*name\\s*=\\s*\"settings\"").containsMatchIn(dataStoreSource),
        )
    }

    /**
     * 2. Nada reemplaza un DataStore corrupto por uno vacío.
     *
     * `ReplaceFileCorruptionHandler { emptyPreferences() }` es la receta que se encuentra en cualquier
     * respuesta de internet, y es exactamente lo que el dueño NO quiere: convierte un archivo dañado en
     * un borrado silencioso de todos sus ajustes. Sin handler, DataStore lanza, que es ruidoso y
     * recuperable. Si algún día hace falta uno, tendrá que preservar lo que se pueda leer, no vaciar.
     */
    @Test
    fun `a corrupt preferences file is never silently replaced by an empty one`() {
        assertFalse(
            "A corruption handler that returns emptyPreferences() turns a damaged file into a silent " +
                "wipe of every user setting. Throwing is loud and recoverable; wiping is neither.",
            dataStoreSource.contains("ReplaceFileCorruptionHandler") ||
                Regex("""corruptionHandler""").containsMatchIn(dataStoreSource),
        )
    }

    /**
     * 3. La base de datos que Hilt inyecta no tiene salida destructiva.
     *
     * `fallbackToDestructiveMigration` convierte "falta una migración" en "se borró la biblioteca":
     * favoritos, listas, historial y descargas. Un fallo de migración tiene que ser un crash que se
     * arregla, no una pérdida de datos que se descubre. (El único uso legítimo del repo está en
     * `MigrationModule`, sobre una caché de correspondencias recalculable — otro archivo, otra base.)
     */
    @Test
    fun `the injected music database has no destructive fallback`() {
        val builder = appModuleSource.substringAfter("databaseBuilder", "")
        assertTrue("AppModule no longer builds the database with databaseBuilder", builder.isNotEmpty())
        assertFalse(
            "fallbackToDestructiveMigration on the injected database turns a missing migration into the " +
                "user losing their library. A migration failure must crash and be fixed, not delete.",
            builder.contains("fallbackToDestructiveMigration"),
        )
    }

    /**
     * 4. Nadie borra los datos de la app desde dentro, salvo un almacén de credenciales conocido.
     *
     * `clearApplicationUserData` no tiene ningún uso legítimo aquí: borra TODO, que es literalmente lo
     * que la orden prohíbe. `deleteSharedPreferences` sí tiene exactamente uno, y está en la lista de
     * abajo: [TidalTokenStore] borra **su propio** archivo de tokens cuando el keyset cifrado es
     * ilegible (caso clásico: una copia de seguridad restaurada en otro teléfono, donde la clave del
     * Keystore que lo envuelve nunca salió del hardware original). Eso cuesta un login, no los ajustes.
     *
     * La lista es el punto: una llamada NUEVA en cualquier otro archivo rompe este test y se revisa
     * antes de llegar al teléfono del dueño, en vez de descubrirse porque un día abrió la app y estaba
     * de fábrica.
     */
    @Test
    fun `nothing wipes the user's data outside the one credential store that may`() {
        val allowedToDeleteOwnFile = setOf(
            "app/src/main/kotlin/com/music/echo/migration/TidalTokenStore.kt",
        )
        val offenders = mutableListOf<String>()
        File(repoRoot, "app/src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readLines().joinToString("\n") { it.substringBefore("//") }
                val path = file.relativeTo(repoRoot).path.replace(File.separatorChar, '/')
                if (text.contains("clearApplicationUserData")) {
                    offenders += "$path (clearApplicationUserData)"
                }
                if (text.contains("deleteSharedPreferences") && path !in allowedToDeleteOwnFile) {
                    offenders += "$path (deleteSharedPreferences)"
                }
            }

        assertTrue(
            "These call sites erase stored user data: $offenders. If one of them is a credential store " +
                "that can be rebuilt with a login, add it to the allowlist above WITH its reason. If it " +
                "touches preferences, it must not ship.",
            offenders.isEmpty(),
        )
    }
}
