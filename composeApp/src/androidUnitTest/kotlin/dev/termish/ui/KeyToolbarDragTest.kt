package dev.termish.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.termish.TermishApplication
import dev.termish.data.KeyToolbarLayout
import dev.termish.data.ToolbarAction
import dev.termish.data.ToolbarKeyConfig
import dev.termish.data.ToolbarPos
import dev.termish.ui.theme.TerminalThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TermishApplication::class)
class KeyToolbarDragTest {
    @get:Rule
    val compose = createComposeRule()

    private var savedLayout: KeyToolbarLayout? by mutableStateOf(null)
    private var saves = 0

    @Test
    fun longPressDragFollowsFingerAndSavesOnlyOnRelease() {
        showEditor()
        val from = ToolbarPos(0, 0)
        val to = ToolbarPos(0, 1)
        val start = point(from)
        val end = point(to)
        compose.onNodeWithTag("toolbar-preview").performTouchInput {
            down(start)
            advanceEventTime(700)
            moveTo(end, delayMillis = 32)
        }
        compose.onNodeWithTag("toolbar-dragged-key").assertExists()
        compose.runOnIdle { assertEquals(0, saves) }
        compose.onNodeWithTag("toolbar-preview").performTouchInput { up() }
        compose.onNodeWithTag("toolbar-dragged-key").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, saves)
            assertEquals(ToolbarAction.TAB, savedLayout!![from].action)
            assertEquals(ToolbarAction.ESC, savedLayout!![to].action)
        }
        // 再次拖回默认顺序：验证重组后手势读到最新布局，且沿用 null 默认布局语义。
        drag(to, from)
        compose.runOnIdle {
            assertEquals(2, saves)
            assertEquals(null, savedLayout)
        }
    }

    @Test
    fun dragAcrossExpandedAndPinnedRowsUsesActualSlotPositions() {
        showEditor()
        val from = ToolbarPos(2, 0)
        val to = ToolbarPos(1, 6)
        drag(from, to)
        compose.runOnIdle {
            assertEquals(1, saves)
            assertEquals(ToolbarAction.RIGHT, savedLayout!![from].action)
            assertEquals(ToolbarAction.PASTE, savedLayout!![to].action)
        }
    }

    @Test
    fun dragIntoEmptySlotMovesKeyAndLeavesSourceEmpty() {
        val from = ToolbarPos(3, 1)
        val to = ToolbarPos(0, 2)
        showEditor(KeyToolbarLayout.DEFAULT.update(to, ToolbarKeyConfig(ToolbarAction.EMPTY)))
        drag(from, to)
        compose.runOnIdle {
            assertEquals(1, saves)
            assertEquals(ToolbarAction.EMPTY, savedLayout!![from].action)
            assertEquals(ToolbarAction.F2, savedLayout!![to].action)
        }
    }

    @Test
    fun fixedExpandSlotCannotBeDraggedOrReceiveDrop() {
        showEditor()
        drag(KeyToolbarLayout.FIXED, ToolbarPos(0, 0))
        drag(ToolbarPos(0, 0), KeyToolbarLayout.FIXED)
        compose.runOnIdle {
            assertEquals(0, saves)
            assertEquals(null, savedLayout)
        }
    }

    @Test
    fun cancelledDragAndDropOutsideSlotsDoNotSave() {
        showEditor()
        val start = point(ToolbarPos(0, 0))
        val end = point(ToolbarPos(2, 1))
        compose.onNodeWithTag("toolbar-preview").performTouchInput {
            down(start)
            advanceEventTime(700)
            moveTo(end, delayMillis = 32)
            cancel()
        }
        compose.onNodeWithTag("toolbar-dragged-key").assertDoesNotExist()
        compose.onNodeWithTag("toolbar-preview").performTouchInput {
            down(start)
            advanceEventTime(700)
            moveTo(Offset(-10f, -10f), delayMillis = 32)
            up()
        }
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test
    fun quickSwipeDoesNotReorderAndTapStillSelectsForEditing() {
        showEditor()
        val start = point(ToolbarPos(3, 0))
        val end = point(ToolbarPos(3, 1))
        compose.onNodeWithTag("toolbar-preview").performTouchInput {
            down(start)
            moveTo(end, delayMillis = 32)
            up()
        }
        compose.runOnIdle { assertEquals(0, saves) }
        compose.onNodeWithTag("toolbar-preview").performTouchInput { click(start) }
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Edit key slot").assertExists()
    }

    private fun showEditor(initial: KeyToolbarLayout? = null) {
        savedLayout = initial
        compose.setContent {
            MaterialTheme {
                SettingsKeyToolbarScreen(
                    layout = savedLayout,
                    theme = TerminalThemes.ALL.first(),
                    onChangeLayout = {
                        savedLayout = it
                        saves++
                    },
                    onBack = {},
                )
            }
        }
    }

    private fun point(pos: ToolbarPos): Offset {
        val origin =
            compose
                .onNodeWithTag("toolbar-preview")
                .fetchSemanticsNode()
                .boundsInRoot.topLeft
        val slot = compose.onNodeWithTag("toolbar-slot-${pos.row}-${pos.col}").fetchSemanticsNode().boundsInRoot
        return slot.center - origin
    }

    private fun drag(
        from: ToolbarPos,
        to: ToolbarPos,
    ) {
        val start = point(from)
        val end = point(to)
        compose.onNodeWithTag("toolbar-preview").performTouchInput {
            down(start)
            advanceEventTime(700)
            moveTo(end, delayMillis = 32)
            up()
        }
    }
}
