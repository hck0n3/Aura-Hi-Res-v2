package iad1tya.echo.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iad1tya.echo.music.cloud.CloudAccountStatus
import iad1tya.echo.music.cloud.CloudEntry
import iad1tya.echo.music.cloud.CloudFailure
import iad1tya.echo.music.cloud.CloudIndexPolicy
import iad1tya.echo.music.cloud.CloudLibraryIndexer
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
    private val indexer: CloudLibraryIndexer,
) : ViewModel() {

    /**
     * Row 891: «Elegir carpetas de música» of one account. [folders] null while listing; [chosen] the folders ticked so
     * far (saved only with «Guardar»). Only folders are listed, at most [CloudIndexPolicy.FOLDER_BROWSER_LIMIT].
     */
    data class FolderBrowser(
        val provider: CloudProvider,
        val path: String,
        val folders: List<CloudEntry>? = null,
        val truncated: Boolean = false,
        val failed: Boolean = false,
        val chosen: Set<String> = emptySet(),
    )

    /** Row 891: what the last «Actualizar» did ([songs] in the cloud library, [complete] = every chosen folder read). */
    data class IndexResult(val songs: Int, val complete: Boolean)

    data class Ui(
        /** null while checking. */
        val available: Boolean? = null,
        val loading: Boolean = true,
        /** Accounts rclone has, with what a listing of their root said. */
        val accounts: Map<CloudProvider, CloudAccountStatus> = emptyMap(),
        val login: CloudLoginState = CloudLoginState.Idle,
        /** A refresh or a disconnect that did not work. */
        val problem: CloudFailure? = null,
        /** Row 891: the folders chosen per account. */
        val folders: Map<CloudProvider, List<String>> = emptyMap(),
        val browser: FolderBrowser? = null,
        val indexing: Boolean = false,
        val indexResult: IndexResult? = null,
        val indexFailed: Boolean = false,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    private var loginJob: Job? = null

    init {
        refresh()
        viewModelScope.launch { loadFolders() }
    }

    private suspend fun loadFolders() {
        val folders = repository.chosenFolders()
        _ui.update { ui -> ui.copy(folders = CloudProvider.entries.associateWith { folders[it.remoteName].orEmpty() }) }
    }

    /** Row 891: opens the folder browser of [provider] at the top of the account, with what is chosen ticked. */
    fun openFolders(provider: CloudProvider) {
        _ui.update { it.copy(browser = FolderBrowser(provider, "", chosen = it.folders[provider].orEmpty().toSet())) }
        browse("")
    }

    /** Lists the folders inside [path] (one `operations/list`, folders only, bounded). */
    fun browse(path: String) {
        val browser = _ui.value.browser ?: return
        _ui.update { it.copy(browser = browser.copy(path = path, folders = null, failed = false, truncated = false)) }
        viewModelScope.launch {
            val listed = try {
                repository.list(browser.provider.remoteName, path).filter(CloudEntry::isDir).sortedBy { it.name.lowercase() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
            _ui.update { ui ->
                val current = ui.browser?.takeIf { it.provider == browser.provider && it.path == path } ?: return@update ui
                ui.copy(
                    browser = current.copy(
                        folders = listed?.take(CloudIndexPolicy.FOLDER_BROWSER_LIMIT).orEmpty(),
                        truncated = (listed?.size ?: 0) > CloudIndexPolicy.FOLDER_BROWSER_LIMIT,
                        failed = listed == null,
                    ),
                )
            }
        }
    }

    fun browseUp() {
        val path = _ui.value.browser?.path ?: return
        if (path.isNotEmpty()) browse(path.substringBeforeLast('/', missingDelimiterValue = ""))
    }

    fun toggleFolder(path: String) {
        _ui.update { ui ->
            val b = ui.browser ?: return@update ui
            ui.copy(browser = b.copy(chosen = if (path in b.chosen) b.chosen - path else b.chosen + path))
        }
    }

    fun closeFolders() {
        _ui.update { it.copy(browser = null) }
    }

    /** «Guardar»: the choice is saved and the library is indexed right away (row 891: after choosing folders). */
    fun saveFolders() {
        val browser = _ui.value.browser ?: return
        val chosen = CloudIndexPolicy.outermost(browser.chosen)
        viewModelScope.launch {
            repository.saveFolders(browser.provider, chosen)
            _ui.update { it.copy(browser = null, folders = it.folders + (browser.provider to chosen)) }
            index(CloudLibraryIndexer.Trigger.FOLDERS_CHOSEN)
        }
    }

    /** «Actualizar música de la nube». */
    fun indexNow() {
        viewModelScope.launch { index(CloudLibraryIndexer.Trigger.USER) }
    }

    private suspend fun index(trigger: CloudLibraryIndexer.Trigger) {
        if (_ui.value.indexing) return
        _ui.update { it.copy(indexing = true, indexFailed = false) }
        try {
            val summary = indexer.index(trigger)
            _ui.update { it.copy(indexing = false, indexResult = IndexResult(summary.songs, summary.complete)) }
        } catch (e: CancellationException) {
            _ui.update { it.copy(indexing = false) }
            throw e
        } catch (e: Throwable) {
            _ui.update { it.copy(indexing = false, indexFailed = true) }
        }
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
