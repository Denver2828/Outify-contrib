package cc.tomko.outify.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioWriteWatchdogTest {

    private class FakeOutput(var position: Long? = 0L) {
        var interrupts = 0
    }

    private class Harness(threshold: Long = 5_000L) {
        var now = 0L
        val watchdog = AudioWriteWatchdog<FakeOutput>(
            position = { it.position },
            interrupt = { it.interrupts++ },
            thresholdMs = threshold,
            clock = { now },
        )

        /** Advances time in one-second monitor ticks, returning the first non-null check. */
        fun tick(seconds: Int): Long? {
            var result: Long? = null
            repeat(seconds) {
                now += 1_000L
                val r = watchdog.check()
                if (result == null) result = r
            }
            return result
        }
    }

    @Test
    fun writeWithoutProgressPastThresholdIsInterruptedOnce() {
        val h = Harness()
        val out = FakeOutput()
        h.watchdog.beginWrite(out)

        val stuck = h.tick(10)

        assertEquals(5_000L, stuck)
        assertEquals(1, out.interrupts)
        assertEquals(1L, h.watchdog.hangs)
        assertTrue(h.watchdog.endWrite(out))
    }

    @Test
    fun writeUnderThresholdIsLeftAlone() {
        val h = Harness()
        val out = FakeOutput()
        h.watchdog.beginWrite(out)

        assertNull(h.tick(4))

        assertEquals(0, out.interrupts)
        assertFalse(h.watchdog.endWrite(out))
    }

    @Test
    fun outputThatKeepsPlayingIsNeverInterrupted() {
        val h = Harness()
        val out = FakeOutput()
        h.watchdog.beginWrite(out)

        repeat(30) {
            out.position = out.position!! + 44_100L
            assertNull(h.tick(1))
        }

        assertEquals(0, out.interrupts)
        assertFalse(h.watchdog.endWrite(out))
    }

    @Test
    fun outputWithUnknownPositionIsNeverInterrupted() {
        val h = Harness()
        val out = FakeOutput(position = null)
        h.watchdog.beginWrite(out)

        assertNull(h.tick(30))

        assertEquals(0, out.interrupts)
        assertFalse(h.watchdog.endWrite(out))
    }

    @Test
    fun nothingInFlightIsNeverInterrupted() {
        val h = Harness()
        val out = FakeOutput()
        h.watchdog.beginWrite(out)
        h.tick(2)
        assertFalse(h.watchdog.endWrite(out))

        assertNull(h.tick(30))

        assertEquals(0, out.interrupts)
    }

    @Test
    fun stuckTimeCountsFromTheStartOfTheCurrentWrite() {
        val h = Harness()
        val out = FakeOutput()
        // An earlier write on the same output, then a long idle gap without progress.
        h.watchdog.beginWrite(out)
        h.tick(1)
        h.watchdog.endWrite(out)
        h.now += 60_000L

        h.watchdog.beginWrite(out)
        assertNull(h.tick(4))
        assertEquals(0, out.interrupts)

        assertEquals(5_000L, h.tick(2))
        assertEquals(1, out.interrupts)
    }

    @Test
    fun hungFlagIsConsumedByEndWrite() {
        val h = Harness()
        val out = FakeOutput()
        h.watchdog.beginWrite(out)
        h.tick(10)
        assertTrue(h.watchdog.endWrite(out))

        // The same output written again (e.g. never rebuilt) is judged afresh.
        h.watchdog.beginWrite(out)
        out.position = 1L
        assertNull(h.tick(1))
        assertFalse(h.watchdog.endWrite(out))
    }

    @Test
    fun replacementOutputIsWatchedFromScratch() {
        val h = Harness()
        val dead = FakeOutput()
        h.watchdog.beginWrite(dead)
        h.tick(10)
        assertTrue(h.watchdog.endWrite(dead))

        val fresh = FakeOutput()
        h.watchdog.beginWrite(fresh)
        assertNull(h.tick(4))

        assertEquals(0, fresh.interrupts)
        assertFalse(h.watchdog.endWrite(fresh))
        assertEquals(1L, h.watchdog.hangs)
    }
}
