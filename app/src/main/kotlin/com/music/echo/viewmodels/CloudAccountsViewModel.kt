package iad1tya.echo.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iad1tya.echo.music.cloud.CloudAccountStatus
import iad1tya.echo.music.cloud.CloudFailure
import iad1tya.echo.music.cloud.CloudLoginEvent
import iad1tya.echo.music.cloud.CloudLoginMachine
import iad1tya.echo.music.cloud.CloudLoginPolicy
import iad1tya.echo.music.cloud.CloudLoginState
import iad1tya.echo.music.cloud.CloudProvider
import iad1tya.echo.music.cloud.CloudRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Registry row 890 — Ajustes ▸ Cuentas y sincronización ▸ «Nube (OneDrive y Dropbox)». Lives as long as the page: leaving
 * it cancels a sign-in in progress (the repository then stops rclone's webserver and removes the half-made account).
 */
@HiltViewModel
class CloudAccountsViewModel @Inject constructor(
    private val repository: CloudRepository,
) : ViewModel() {

    data class Ui(
        /** null while checking. */
        val available: Boolean? = null,
        val loading: Boolean = true,
        /** Accounts rclone has, with what a listing of their root said. */
        val accounts: Map<CloudProvider, CloudAccountStatus> = emptyMap(),
        val login: CloudLoginState = CloudLoginState.Idle,
        /** A refresh or a disconnect that did not work. */
        val problem: CloudFailure? = null,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    private var loginJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val available = repository.isAvailable()
            _ui.update { it.copy(available = available, loading = available, problem = null) }
            if (!available) return@launch
            try {
                val accounts = repository.listRemotes()
                    .mapNotNull(CloudProvider::ofRemote)
                    .associateWith { CloudLoginPolicy.statusOf(repository.check(it)) }
                _ui.update { it.copy(loading = false, accounts = accounts) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.update { it.copy(loading = false, problem = CloudFailure.OTHER) }
            }
        }
    }

    fun connect(provider: CloudProvider) {
        if (_ui.value.login.busy) return
        loginJob = viewModelScope.launch {
            // After a cancel, a reply already in flight must not overwrite «cancelled».
            val end = repository.connect(provider) { state -> if (isActive) _ui.update { it.copy(login = state) } }
            if (end is CloudLoginState.Connected) refresh()
        }
    }

    /** «Cancelar», or the page left: the sign-in stops and is shown as cancelled. */
    fun cancelLogin() {
        val job = loginJob ?: return
        if (!_ui.value.login.busy) return
        job.cancel()
        _ui.update { it.copy(login = CloudLoginMachine.reduce(it.login, CloudLoginEvent.Cancel)) }
    }

    fun dismissLogin() {
        if (!_ui.value.login.busy) _ui.update { it.copy(login = CloudLoginState.Idle) }
    }

    fun disconnect(provider: CloudProvider) {
        viewModelScope.launch {
            try {
                repository.disconnect(provider)
                _ui.update { it.copy(accounts = it.accounts - provider, login = CloudLoginState.Idle) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.update { it.copy(problem = CloudFailure.OTHER) }
            }
        }
    }
}
