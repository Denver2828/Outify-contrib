package cc.tomko.outify.playback

/**
 * Detects a blocking audio write that never returns.
 *
 * Some outputs stop consuming samples (seen on a Sony Android TV after an underrun restart):
 * `AudioTrack.write(..., WRITE_BLOCKING)` then waits forever, holding the PCM thread and the
 * engine's write lock until Spotify marks the device inactive. A monitor thread calls [check]
 * periodically; once a write has been in flight for [thresholdMs] while the output's playback
 * position did not move, the output is interrupted and [endWrite] reports the write as hung so
 * the caller can rebuild the output.
 *
 * Every method takes only this class's own lock, never the engine's write lock, which the hung
 * write is holding. [interrupt] runs under that lock, so it must not wait on the writer.
 *
 * @param position frames played so far by an output, or null when it cannot tell; an output
 *   whose position is unknown is never interrupted.
 * @param interrupt wakes a write blocked on the output (AudioTrack.pause does).
 */
class AudioWriteWatchdog<T : Any>(
    private val position: (T) -> Long?,
    private val interrupt: (T) -> Unit,
    private val thresholdMs: Long = DEFAULT_THRESHOLD_MS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    companion object {
        const val DEFAULT_THRESHOLD_MS = 5_000L
    }

    private val lock = Any()

    private var inFlight: T? = null
    private var inFlightSinceMs = 0L
    private var hung: T? = null

    private var watched: T? = null
    private var watchedPosition = 0L
    private var watchedProgressAtMs = 0L

    /** Number of writes interrupted so far. */
    var hangs = 0L
        get() = synchronized(lock) { field }
        private set

    /** Marks a write to [output] as started. Pair every call with [endWrite]. */
    fun beginWrite(output: T) = synchronized(lock) {
        inFlightSinceMs = clock()
        inFlight = output
    }

    /**
     * Marks the write to [output] as finished. Returns true when [check] interrupted it, in
     * which case the output should be treated as dead and rebuilt.
     */
    fun endWrite(output: T): Boolean = synchronized(lock) {
        inFlight = null
        val wasHung = hung === output
        if (wasHung) hung = null
        wasHung
    }

    /**
     * Interrupts the in-flight write when it has run for [thresholdMs] without playback
     * progress. Returns how long it was stuck when it interrupted it, null otherwise.
     */
    fun check(): Long? = synchronized(lock) {
        val current = inFlight
        if (current == null) {
            watched = null
            return null
        }
        val now = clock()
        val pos = position(current) ?: return null
        if (current !== watched || pos != watchedPosition) {
            watched = current
            watchedPosition = pos
            watchedProgressAtMs = now
            return null
        }
        if (hung === current) return null
        val stuckMs = now - maxOf(watchedProgressAtMs, inFlightSinceMs)
        if (stuckMs < thresholdMs) return null
        hung = current
        hangs++
        interrupt(current)
        stuckMs
    }
}
