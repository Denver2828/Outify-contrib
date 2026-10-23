package cc.tomko.outify.ui.components.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackStackOpsTest {

    @Test
    fun `pops the top entry above the root`() {
        val stack = mutableListOf("home", "album", "artist")

        assertTrue(stack.popKeepingRoot())
        assertEquals(listOf("home", "album"), stack)
    }

    @Test
    fun `queued back presses stop at the root`() {
        val stack = mutableListOf("home", "album")

        val popped = List(5) { stack.popKeepingRoot() }

        assertEquals(listOf(true, false, false, false, false), popped)
        assertEquals(listOf("home"), stack)
    }

    @Test
    fun `an empty stack is left alone`() {
        val stack = mutableListOf<String>()

        assertFalse(stack.popKeepingRoot())
        assertTrue(stack.isEmpty())
    }

    @Test
    fun `current route is the top entry or the fallback when empty`() {
        assertEquals("album", listOf("home", "album").currentOr("home"))
        assertEquals("home", emptyList<String>().currentOr("home"))
    }
}
