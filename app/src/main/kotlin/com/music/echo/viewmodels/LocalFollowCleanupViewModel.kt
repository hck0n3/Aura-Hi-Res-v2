package iad1tya.echo.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iad1tya.echo.music.utils.LocalFollowCleanup
import iad1tya.echo.music.utils.LocalFollowCleanupPolicy
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Registry row 905 — the «Seguidos solo en Aura» part of «Revisar suscripciones»: the follows an import or a restore
 * wrote in Aura and the user never confirmed ([LocalFollowCleanupPolicy]). Read once when the screen opens; nothing is
 * removed without a selection and the preview dialog; «Deshacer» puts the last removal back. Never touches the account.
 */
@HiltViewModel
class LocalFollowCleanupViewModel @Inject constructor(
    private val cleanup: LocalFollowCleanup,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val failed: Boolean = false,
        val candidates: List<LocalFollowCleanupPolicy.Candidate> = emptyList(),
        val selected: Set<String> = emptySet(),
        val working: Boolean = false,
        /** Channels the last removal cleared (shown with «Deshacer» while [undoRows] > 0). */
        val lastRemoved: Int = 0,
        val undoRows: Int = 0,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = combine(_state, cleanup.undo) { s, undo -> s.copy(undoRows = undo.size) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _state.value.copy(undoRows = cleanup.undo.value.size))

    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        if (job?.isActive == true) return
        job = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(loading = true, failed = false) }
            val found = cleanup.candidates()
            _state.update { s ->
                if (found == null) {
                    s.copy(loading = false, failed = true, candidates = emptyList(), selected = emptySet())
                } else {
                    s.copy(loading = false, failed = false, candidates = found, selected = s.selected intersect found.map { it.id }.toSet())
                }
            }
        }
    }

    fun toggle(id: String) = _state.update { s ->
        if (s.working || s.candidates.none { it.id == id }) s
        else s.copy(selected = if (id in s.selected) s.selected - id else s.selected + id)
    }

    fun selectAll() = _state.update { s -> if (s.working) s else s.copy(selected = LocalFollowCleanupPolicy.selectAll(s.candidates)) }

    fun selectSuggested() = _state.update { s ->
        if (s.working) s else s.copy(selected = LocalFollowCleanupPolicy.suggestedSelection(s.candidates))
    }

    fun clearSelection() = _state.update { s -> if (s.working) s else s.copy(selected = emptySet()) }

    /** The preview dialog's «Quitar de Aura». The ONLY way a local removal starts. */
    fun removeChosen() {
        val s = _state.value
        if (s.working || job?.isActive == true) return
        val plan = LocalFollowCleanupPolicy.plan(s.candidates, s.selected)
        if (plan.isEmpty()) return
        _state.update { it.copy(working = true) }
        job = viewModelScope.launch(Dispatchers.IO) {
            val removed = cleanup.remove(plan)
            val found = cleanup.candidates()
            _state.update {
                it.copy(
                    working = false,
                    lastRemoved = removed,
                    candidates = found ?: it.candidates,
                    selected = emptySet(),
                )
            }
        }
    }

    /** «Deshacer». */
    fun undoLast() {
        if (_state.value.working || job?.isActive == true) return
        _state.update { it.copy(working = true) }
        job = viewModelScope.launch(Dispatchers.IO) {
            cleanup.undo()
            val found = cleanup.candidates()
            _state.update { it.copy(working = false, lastRemoved = 0, candidates = found ?: it.candidates, selected = emptySet()) }
        }
    }
}
