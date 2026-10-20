package cc.tomko.outify.ui.components.navigation

/**
 * Pops the top entry but never the root. Back presses queued while the main thread is stalled
 * each run before recomposition disables the back handlers; unguarded pops can empty the stack
 * and the next frame then crashes on `last()`. Returns whether an entry was removed.
 */
fun <T> MutableList<T>.popKeepingRoot(): Boolean {
    if (size <= 1) return false
    removeAt(lastIndex)
    return true
}

/** Top entry, or [fallback] if the stack is somehow empty. */
fun <T> List<T>.currentOr(fallback: T): T = lastOrNull() ?: fallback
