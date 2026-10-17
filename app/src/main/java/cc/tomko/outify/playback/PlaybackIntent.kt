package cc.tomko.outify.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** User intent survives player/service recreation and is not inferred from native events. */
class PlaybackIntent {
    data class PauseState(
        val userPaused: Boolean = false,
        val focusPaused: Boolean = false,
        val generation: Long = 0,
    ) {
        val canPlay: Boolean get() = !userPaused && !focusPaused
    }

    private val _state = MutableStateFlow(PauseState())
    val state = _state.asStateFlow()
    val canPlay: Boolean get() = _state.value.canPlay

    @Synchronized
    fun playByUser() {
        _state.value = PauseState(generation = _state.value.generation + 1)
    }

    @Synchronized
    fun pauseByUser() {
        _state.update { it.copy(userPaused = true, generation = it.generation + 1) }
    }

    @Synchronized
    fun pauseForAudioFocus() {
        _state.update { it.copy(focusPaused = true) }
    }

    /** Only an interrupted playback, never an explicit user pause, may auto-resume. */
    @Synchronized
    fun resumeAfterAudioFocus(): Boolean {
        val previous = _state.value
        _state.update { it.copy(focusPaused = false) }
        return previous.focusPaused && !previous.userPaused
    }
}
