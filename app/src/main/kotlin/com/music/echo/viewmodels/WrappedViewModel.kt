package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.reco.GenreCache
import iad1tya.echo.music.stats.ListeningStats
import iad1tya.echo.music.stats.WrappedBuilder
import iad1tya.echo.music.stats.statsInput
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Registry row 910 — «Tu año en Aura» / «Tu mes». Reads the phone's own listening history once, when the screen opens,
 * and builds the cards with [WrappedBuilder]. No network, no AI: genres come from what the phone already knows
 * ([GenreCache.snapshot]), and a failure is written to the local log by class name only (rule 4).
 */
@HiltViewModel
class WrappedViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    sealed interface State {
        data object Loading : State
        data object Invalid : State
        data class Done(val period: WrappedBuilder.Period, val story: WrappedBuilder.Story) : State
    }

    val period: WrappedBuilder.Period? = WrappedBuilder.parse(savedStateHandle.get<String>("period"))

    private val _state = MutableStateFlow<State>(if (period == null) State.Invalid else State.Loading)
    val state: StateFlow<State> = _state

    init {
        val p = period
        if (p != null) {
            viewModelScope.launch {
                val story = withContext(Dispatchers.IO) {
                    try {
                        WrappedBuilder.build(summarize(p))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w("Wrapped failed: %s", e.javaClass.simpleName)
                        null
                    }
                }
                _state.value = if (story == null) State.Invalid else State.Done(p, story)
            }
        }
    }

    private suspend fun summarize(p: WrappedBuilder.Period): ListeningStats.Summary {
        val range = p.range
        val from = range.startStored
        val to = range.endStored
        val (plays, songs) = statsInput(
            database.statsPlaysInRange(from, to),
            database.statsSongsInRange(from, to),
            database.statsSongArtistsInRange(from, to),
        )
        val firstPlays = ListeningStats.artistFirstPlays(
            database.statsArtistFirstPlays().map { Triple(it.artistId, it.name, ListeningStats.fromStored(it.firstPlayed)) },
        )
        return ListeningStats.summarize(
            range = range,
            plays = plays,
            songs = songs,
            genreByArtistName = GenreCache.snapshot(context),
            artistFirstPlayed = firstPlays,
            today = LocalDate.now(),
        )
    }
}
