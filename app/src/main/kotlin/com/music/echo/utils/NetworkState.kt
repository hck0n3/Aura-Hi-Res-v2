package iad1tya.echo.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 🔴 ¿HAY RED AHORA MISMO? — una sola respuesta para todo el proceso, observable desde Compose.
 *
 * Existe porque [NetworkConnectivityObserver], el observador que ya había, expone un
 * `Channel(CONFLATED).receiveAsFlow()`: eso es de **un solo consumidor**, cada emisión se la lleva
 * uno. Añadirle colectores desde la interfaz le robaría los avisos a quien ya los usa
 * (`LyricsHelper`, `MusicService`, `NetworkReload`), así que el modo sin conexión automático trae su
 * propio callback y un [StateFlow], que sí es para muchos y además tiene valor actual — lo que una
 * pantalla necesita al recomponerse.
 *
 * ── Qué cuenta como "hay red" ─────────────────────────────────────────────────────────────────────
 * Un transporte activo, **sin** exigir `NET_CAPABILITY_VALIDATED`, la misma lectura que
 * [isInternetAvailable]. Y es a propósito: el propio [NetworkConnectivityObserver] tiene escrito por
 * qué (portales cautivos ya firmados, VPN corporativas, IPv6 puro, redes donde la sonda de
 * validación está bloqueada — llevan tráfico perfectamente y nunca reportan VALIDATED). Aquí el
 * error caro es declarar "sin conexión" con red buena, porque eso le esconde su biblioteca en línea;
 * el error barato es lo contrario, que solo deja la pantalla de error que ya existía. Así que se
 * peca de optimista.
 *
 * ── El retardo ────────────────────────────────────────────────────────────────────────────────────
 * Perder la red espera [AutoOfflinePolicy.OFFLINE_DEBOUNCE_MS] y **vuelve a mirar** antes de
 * declararlo; recuperarla es inmediato. El razonamiento está en [AutoOfflinePolicy].
 */
object NetworkState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _online = MutableStateFlow(true)

    /** true = hay red. Arranca en true: sin haber mirado todavía, lo seguro es NO esconderle nada. */
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private var connectivityManager: ConnectivityManager? = null
    private var pendingOffline: Job? = null
    private var started = false

    /** Llamado una vez desde `App.onCreate`. Repetirlo no registra un segundo callback. */
    fun start(context: Context) {
        if (started) return
        started = true
        val cm = context.getSystemService<ConnectivityManager>() ?: return
        connectivityManager = cm
        _online.value = readNow(cm)
        val callback = object : ConnectivityManager.NetworkCallback() {
            // Los tres hacen lo mismo — releer y decidir — porque el callback dice "algo cambió" y la
            // verdad está en `readNow`, no en cuál de los tres llegó. Un `onAvailable` de una red sin
            // transporte útil no debe encender nada, y un `onLost` de la wifi mientras quedan datos
            // no debe apagar nada.
            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) = refresh()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onFailure {
                // Sin callback no hay automático: se queda en "hay red" y el interruptor manual sigue
                // siendo la vía. Nunca al contrario.
                _online.value = true
            }
    }

    private fun refresh() {
        val cm = connectivityManager ?: return
        if (readNow(cm)) {
            pendingOffline?.cancel()
            pendingOffline = null
            _online.value = true
            return
        }
        // Ya hay una cuenta atrás en marcha: dejarla correr. Reiniciarla con cada aviso de un aparato
        // que va y viene (los `onLost` en ráfaga de un cambio de red) la haría no llegar nunca.
        if (pendingOffline?.isActive == true) return
        pendingOffline = scope.launch {
            delay(AutoOfflinePolicy.OFFLINE_DEBOUNCE_MS)
            // Segunda lectura: el hueco puede haberse cerrado solo mientras esperábamos.
            if (!readNow(cm)) _online.value = false
        }
    }

    private fun readNow(cm: ConnectivityManager): Boolean {
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
            ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}
