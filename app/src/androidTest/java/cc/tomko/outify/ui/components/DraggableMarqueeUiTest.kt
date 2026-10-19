package cc.tomko.outify.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DraggableMarqueeUiTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var scrollState: ScrollState
    private var clicks = 0
    private var singleLineHeight = 0.dp
    private val expansion = mutableFloatStateOf(1f)

    private fun showMarquee(autoScroll: Boolean = true) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val density = LocalDensity.current
            scrollState = remember { ScrollState(0) }
            DraggableMarquee(
                contentKey = "artists",
                expansionFractionProvider = { expansion.floatValue },
                modifier = Modifier.width(160.dp).testTag("artists"),
                canScroll = autoScroll,
                initialDelayMillis = 500L,
                resumeDelayMillis = 3_000L,
                velocity = 50.dp,
                scrollState = scrollState,
            ) {
                repeat(30) { index ->
                    Text(
                        text = "Artist $index, ",
                        fontSize = 20.sp,
                        maxLines = 1,
                        softWrap = false,
                        onTextLayout = {
                            if (index == 0) singleLineHeight = with(density) { it.size.height.toDp() }
                        },
                        modifier = Modifier.clickable { clicks++ },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun dragArtists() {
        compose.onNodeWithTag("artists").performTouchInput {
            down(center)
            moveBy(Offset(-80f, 0f), delayMillis = 300L)
            // Let velocity decay before release, so this checks the idle delay, not a fling.
            advanceEventTime(300L)
            up()
        }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun longArtistListStaysOnOneLineAndArtistsRemainTappable() {
        showMarquee(autoScroll = false)
        compose.onNodeWithTag("artists").assertHeightIsEqualTo(singleLineHeight)
        compose.runOnIdle { assertTrue(scrollState.maxValue > 0) }
        compose.onNodeWithTag("artists").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun draggingPausesAutomaticScrollingThenResumesWithoutTriggeringArtistClicks() {
        showMarquee()
        compose.mainClock.advanceTimeBy(1_500L)
        compose.runOnIdle { assertTrue(scrollState.value > 0) }
        dragArtists()
        val draggedOffset = compose.runOnIdle { scrollState.value }
        compose.mainClock.advanceTimeBy(2_000L)
        compose.runOnIdle {
            assertEquals(draggedOffset, scrollState.value)
            assertEquals(0, clicks)
        }
        compose.mainClock.advanceTimeBy(2_000L)
        compose.runOnIdle { assertTrue(scrollState.value > draggedOffset) }
    }

    @Test
    fun pausedPlaybackStillAllowsManualScrollingButDoesNotAutoScroll() {
        showMarquee(autoScroll = false)
        compose.mainClock.advanceTimeBy(5_000L)
        compose.runOnIdle { assertEquals(0, scrollState.value) }
        dragArtists()
        val draggedOffset = compose.runOnIdle { scrollState.value }
        assertTrue(draggedOffset > 0)
        compose.mainClock.advanceTimeBy(5_000L)
        compose.runOnIdle { assertEquals(draggedOffset, scrollState.value) }
    }

    @Test
    fun collapsedPlayerDoesNotAutoScroll() {
        expansion.floatValue = 0f
        showMarquee()
        compose.mainClock.advanceTimeBy(5_000L)
        compose.runOnIdle { assertEquals(0, scrollState.value) }
        compose.runOnIdle { expansion.floatValue = 1f }
        compose.mainClock.advanceTimeBy(1_500L)
        compose.runOnIdle { assertTrue(scrollState.value > 0) }
    }
}
