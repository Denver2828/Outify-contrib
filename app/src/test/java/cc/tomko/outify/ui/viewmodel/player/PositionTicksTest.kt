package cc.tomko.outify.ui.viewmodel.player

import cc.tomko.outify.playback.model.PlaybackState
import cc.tomko.outify.playback.model.PositionInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PositionTicksTest {

    private class Harness(playing: Boolean) {
        val state = MutableStateFlow(PlaybackState(isPlaying = playing))
        var estimates = 0
        val emitted = mutableListOf<Long>()
        val ticks = positionTicks(state, intervalMs = 250L) { (++estimates).toLong() }
    }

    @Test
    fun pausedEmitsOnceAndThenStaysQuiet() = runTest {
        val h = Harness(playing = false)
        backgroundScope.launch { h.ticks.collect { h.emitted += it } }
        runCurrent()
        advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(listOf(1L), h.emitted)
    }

    @Test
    fun playingTicksEveryInterval() = runTest {
        val h = Harness(playing = true)
        backgroundScope.launch { h.ticks.collect { h.emitted += it } }
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        // t = 0, 250, 500, 750, 1000
        assertEquals(5, h.emitted.size)
    }

    @Test
    fun pausingStopsTheTicker() = runTest {
        val h = Harness(playing = true)
        backgroundScope.launch { h.ticks.collect { h.emitted += it } }
        runCurrent()
        advanceTimeBy(500L)
        runCurrent()
        val beforePause = h.emitted.size

        h.state.value = h.state.value.copy(isPlaying = false)
        runCurrent()
        advanceTimeBy(60_000L)
        runCurrent()

        // Exactly one emission for the pause itself, then nothing.
        assertEquals(beforePause + 1, h.emitted.size)
    }

    @Test
    fun seekWhilePausedEmitsAgain() = runTest {
        val h = Harness(playing = false)
        backgroundScope.launch { h.ticks.collect { h.emitted += it } }
        runCurrent()

        h.state.value = h.state.value.copy(position = PositionInfo.EMPTY.copy(active = 30.seconds))
        runCurrent()

        assertEquals(2, h.emitted.size)
    }

    @Test
    fun noCollectorMeansNoEstimates() = runTest {
        val h = Harness(playing = true)
        advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(0, h.estimates)
    }
}
