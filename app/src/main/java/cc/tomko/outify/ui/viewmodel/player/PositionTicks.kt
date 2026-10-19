package cc.tomko.outify.ui.viewmodel.player

import cc.tomko.outify.playback.PlaybackStateHolder
import cc.tomko.outify.playback.model.PlaybackState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

internal const val POSITION_TICK_MS = 250L

/**
 * Cold position stream: ticks every [intervalMs] only while playing, and emits a single
 * estimate for every state change while paused, so seeks still show up.
 * Nothing runs while it has no collector.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun positionTicks(
    state: Flow<PlaybackState>,
    intervalMs: Long = POSITION_TICK_MS,
    estimate: () -> Long,
): Flow<Long> = state.flatMapLatest { playback ->
    if (playback.isPlaying) {
        flow {
            while (true) {
                emit(estimate())
                delay(intervalMs)
            }
        }
    } else {
        flowOf(estimate())
    }
}

internal fun PlaybackStateHolder.positionTicks(): Flow<Long> =
    positionTicks(state) { estimatePosition().inWholeMilliseconds }
