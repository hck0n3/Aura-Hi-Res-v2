

package iad1tya.echo.music.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import iad1tya.echo.music.extensions.toEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Collections
import java.util.WeakHashMap
import kotlin.properties.ReadOnlyProperty

private val Context.rawSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// HALLAZGO-013 (2026-08-25): el único punto de entrada al DataStore de ajustes ahora envuelve el
// store real con EncryptedSecretsDataStore, que cifra/descifra las credenciales de forma
// transparente. Los ~560 sitios de lectura/escritura no cambian; el archivo en disco queda
// cifrado con una clave del Keystore ligada al dispositivo. Un solo wrapper por applicationContext
// (el store real ya es un singleton por proceso; el wrapper no tiene estado propio).
private val encryptedDataStores: MutableMap<Context, DataStore<Preferences>> =
    Collections.synchronizedMap(WeakHashMap())

val Context.dataStore: DataStore<Preferences>
    get() = encryptedDataStores.getOrPut(applicationContext) {
        EncryptedSecretsDataStore(applicationContext.rawSettingsDataStore)
    }

// HALLAZGO-060 (diferido de la fila 178) + auditoría de latencia de entrada: snapshot de proceso
// NO BLOQUEANTE del DataStore de ajustes. Un único colector (arrancado en App.onCreate, solo el
// proceso por defecto, en IO) publica aquí cada emisión; las ~640 semillas de rememberPreference y
// las lecturas calientes de MusicService/ViewModels leen el valor SIN el viaje runBlocking por el
// actor del DataStore (que además espera detrás de cualquier edit{} en vuelo). La primera emisión
// tarda unas decenas de ms en el arranque frío (disco + descifrado), así que hasta entonces peek
// devuelve null y los llamadores caen a la lectura bloqueante de siempre: la corrección es idéntica
// a hoy y el bloqueo queda confinado, como mucho, al primer frame del proceso. El snapshot es la
// vista DESCIFRADA memoizada por EncryptedSecretsDataStore (una pasada de descifrado por emisión,
// compartida por todos los lectores — el contrato del HALLAZGO-062 queda intacto).
object PrefsBridge {
    @Volatile
    private var snapshot: Preferences? = null

    fun publish(prefs: Preferences) {
        snapshot = prefs
    }

    val isReady: Boolean
        get() = snapshot != null

    fun <T> peek(key: Preferences.Key<T>): T? = snapshot?.get(key)
}

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? =
    runBlocking {
        data.first()[key]
    }

fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T =
    runBlocking {
        data.first()[key] ?: defaultValue
    }

// Lectura no bloqueante para rutas del hilo principal: usa el snapshot del proceso si ya llegó la
// primera emisión y solo cae a la lectura bloqueante antes de eso (arranque frío). Sustituto directo
// de get(key, defaultValue) en sitios que corren en Main (composición, onClick, callbacks del player).
fun <T> DataStore<Preferences>.getNonBlocking(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = PrefsBridge.peek(key) ?: get(key, defaultValue)

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key] ?: defaultValue }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key].toEnum(defaultValue) }

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ANR: `context.dataStore[key]` is a runBlocking { data.first() } DataStore read — it goes through
    // the DataStore actor and waits behind any in-flight edit{}. Left un-remembered it ran on EVERY
    // recomposition, and with ~560 call sites app-wide (46 on one settings screen alone) a single frame
    // could stack dozens of blocking disk reads on the main thread. It is only the seed for
    // collectAsState (the flow below drives every later value), so computing it once per composition is
    // semantically identical. Keyed like the flow's own remember (no keys) so both stay in lockstep.
    //
    // HALLAZGO-060 (diferido): la semilla sigue siendo bloqueante aunque esté remember-ada, y cada
    // navegación desecha la composición y la re-paga. getNonBlocking lee el snapshot de proceso
    // (PrefsBridge) sin bloquear; solo cae a la lectura bloqueante antes de la primera emisión.
    val initialValue = remember { context.dataStore.getNonBlocking(key, defaultValue) }
    val state =
        remember {
            context.dataStore.data
                .map { it[key] ?: defaultValue }
                .distinctUntilChanged()
        }.collectAsState(initialValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[key] = value
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Same blocking-read-per-recomposition fix as rememberPreference above.
    // HALLAZGO-060 (diferido): semilla no bloqueante vía snapshot de proceso (PrefsBridge).
    val initialValue =
        remember {
            (PrefsBridge.peek(key) ?: context.dataStore[key]).toEnum(defaultValue = defaultValue)
        }
    val state =
        remember {
            context.dataStore.data
                .map { it[key].toEnum(defaultValue = defaultValue) }
                .distinctUntilChanged()
        }.collectAsState(initialValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[key] = value.name
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
