package cc.tomko.outify.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

/** A real scrolling row, so hit targets move with the text and remain individually clickable. */
@Composable
fun DraggableMarquee(
    contentKey: Any?,
    expansionFractionProvider: () -> Float,
    modifier: Modifier = Modifier,
    canScroll: Boolean = true,
    velocity: Dp = 25.dp,
    initialDelayMillis: Long = 2_000L,
    resumeDelayMillis: Long = 3_000L,
    scrollState: ScrollState = remember(contentKey) { ScrollState(0) },
    content: @Composable RowScope.() -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val expansionProvider by rememberUpdatedState(expansionFractionProvider)
    val expanded by remember { derivedStateOf { expansionProvider() > 0.99f } }
    val maxScroll = scrollState.maxValue
    val overflowing = scrollState.viewportSize > 0 && maxScroll > 0 && maxScroll != Int.MAX_VALUE
    val velocityPx = with(LocalDensity.current) { velocity.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var dragging by remember(scrollState) { mutableStateOf(false) }
    var interactionCount by remember(scrollState) { mutableIntStateOf(0) }

    require(velocityPx.isFinite() && velocityPx > 0) { "Marquee velocity must be positive" }
    require(initialDelayMillis >= 0 && resumeDelayMillis >= 0) { "Marquee delays must not be negative" }

    LaunchedEffect(scrollState) {
        scrollState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    dragging = true
                    interactionCount++
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    dragging = false
                    interactionCount++
                }
            }
        }
    }

    LaunchedEffect(
        scrollState, maxScroll, overflowing, canScroll, expanded, dragging, interactionCount,
        velocityPx, initialDelayMillis, resumeDelayMillis, lifecycle,
    ) {
        if (!overflowing || !canScroll || !expanded || dragging) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { scrollState.isScrollInProgress }.first { !it }
            delay(if (interactionCount > 0) resumeDelayMillis else initialDelayMillis)
            snapshotFlow { scrollState.isScrollInProgress }.first { !it }
            var towardsEnd = scrollState.value < maxScroll
            while (isActive) {
                val target = if (towardsEnd) maxScroll else 0
                try {
                    scrollState.animateScrollTo(
                        target,
                        tween(
                            durationMillis = marqueeDurationMillis(abs(target - scrollState.value), velocityPx),
                            easing = LinearEasing,
                        ),
                    )
                    towardsEnd = !towardsEnd
                    delay(initialDelayMillis)
                } catch (e: CancellationException) {
                    // User-input scrolling can cancel the animation before its drag interaction
                    // is observed. Recover from that too, but never swallow lifecycle cancellation.
                    currentCoroutineContext().ensureActive()
                    snapshotFlow { scrollState.isScrollInProgress }.first { !it }
                    delay(resumeDelayMillis)
                    towardsEnd = scrollState.value < maxScroll
                }
            }
        }
    }

    val fade = if (overflowing) Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val edge = minOf(24.dp.toPx(), size.width / 2)
            val fadeLeft = if (rtl) scrollState.canScrollForward else scrollState.canScrollBackward
            val fadeRight = if (rtl) scrollState.canScrollBackward else scrollState.canScrollForward
            if (fadeLeft) drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, edge),
                size = Size(edge, size.height),
                blendMode = BlendMode.DstIn,
            )
            if (fadeRight) drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), size.width - edge, size.width),
                topLeft = Offset(size.width - edge, 0f),
                size = Size(edge, size.height),
                blendMode = BlendMode.DstIn,
            )
        } else Modifier

    Box(modifier.fillMaxWidth().clipToBounds().then(fade)) {
        Row(
            modifier = Modifier.horizontalScroll(scrollState, enabled = overflowing && expanded),
            content = content,
        )
    }
}

internal fun marqueeDurationMillis(distancePx: Int, velocityPxPerSecond: Float): Int {
    require(velocityPxPerSecond.isFinite() && velocityPxPerSecond > 0)
    return (abs(distancePx.toDouble()) / velocityPxPerSecond * 1_000)
        .coerceIn(1.0, Int.MAX_VALUE.toDouble())
        .roundToInt()
}
