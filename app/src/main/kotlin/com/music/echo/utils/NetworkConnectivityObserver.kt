

package iad1tya.echo.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow


class NetworkConnectivityObserver(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = Channel<Boolean>(Channel.CONFLATED)
    val networkStatus = _networkStatus.receiveAsFlow()

    // Ronda 9 (dueño): "cuando activo el wifi ya no reproduce nada que no esté en caché — con datos
    // sí funciona". Causa real: el teléfono suele mantener DOS redes activas a la vez (datos + wifi)
    // mientras Android decide cuál es la default, y onLost() se dispara POR RED, no por "¿queda
    // alguna que sirva?". Al activar wifi, Android termina bajando la red de datos (ya no la
    // necesita) — eso dispara onLost(datos), y el código de antes lo leía como "sin internet" en
    // general aunque wifi siguiera perfectamente conectada. Se seguía UN solo booleano por evento;
    // ahora se sigue el CONJUNTO de redes que cumplen el filtro, y solo se avisa "sin conexión"
    // cuando ese conjunto queda vacío de verdad.
    private val activeNetworks = java.util.Collections.synchronizedSet(mutableSetOf<Network>())

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        // Emit "connected" on plain availability (NOT gated on NET_CAPABILITY_VALIDATED). Many real streaming
        // networks — campus/hotel captive portals already signed into, corporate VPNs, IPv6-only, or setups
        // where the validation probe is blocked — carry traffic fine yet never report VALIDATED. Gating on it
        // left them permanently "offline", so the retry guard never fired and playback never recovered.
        // (isCurrentlyConnected() still uses VALIDATED for its one-shot initial read; that is pre-existing.)
        override fun onAvailable(network: Network) {
            activeNetworks.add(network)
            _networkStatus.trySend(true)
        }

        override fun onLost(network: Network) {
            activeNetworks.remove(network)
            _networkStatus.trySend(activeNetworks.isNotEmpty())
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
        
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            
            _networkStatus.trySend(true)
        }
        
        
        val isInitiallyConnected = isCurrentlyConnected()
        _networkStatus.trySend(isInitiallyConnected)
    }

    fun unregister() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
    
    
    fun isCurrentlyConnected(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            
            val hasInternet = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            
            
            val isValidated =
                networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

            hasInternet && isValidated
        } catch (e: Exception) {
            false
        }
    }
}
