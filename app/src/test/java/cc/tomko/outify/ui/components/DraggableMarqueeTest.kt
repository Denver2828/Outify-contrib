package cc.tomko.outify.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DraggableMarqueeTest {
    @Test
    fun `scroll duration maintains a constant velocity`() {
        assertEquals(10_000, marqueeDurationMillis(250, 25f))
        assertEquals(5_000, marqueeDurationMillis(125, 25f))
    }

    @Test
    fun `scroll speed is independent of screen density`() {
        assertEquals(marqueeDurationMillis(250, 25f), marqueeDurationMillis(750, 75f))
    }

    @Test
    fun `scrolling back uses the same duration`() {
        assertEquals(marqueeDurationMillis(250, 25f), marqueeDurationMillis(-250, 25f))
    }

    @Test
    fun `zero distance and very long text have bounded durations`() {
        assertEquals(1, marqueeDurationMillis(0, 25f))
        assertEquals(Int.MAX_VALUE, marqueeDurationMillis(Int.MAX_VALUE, 0.0001f))
    }

    @Test
    fun `invalid scroll velocities are rejected`() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { velocity ->
            assertThrows(IllegalArgumentException::class.java) {
                marqueeDurationMillis(250, velocity)
            }
        }
    }
}
