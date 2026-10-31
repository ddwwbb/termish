package dev.termish.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.MapSettings
import dev.termish.TermishApplication
import dev.termish.data.Host
import dev.termish.data.HostRepository
import dev.termish.ssh.CommandOutput
import dev.termish.ssh.CommandResult
import dev.termish.ssh.SessionInfo
import dev.termish.ssh.SshExecChannel
import dev.termish.ssh.SshSession
import dev.termish.ui.theme.TerminalThemes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TermishApplication::class)
class TerminalSelectionGestureTest {
    @get:Rule
    val compose = createComposeRule()

    private val controllers = mutableListOf<TerminalController>()
    private var keyboardRequests = 0

    @Test
    fun canvasTapRequestsKeyboard() {
        showTerminal()
        compose.onNodeWithTag("terminal").performTouchInput {
            down(center)
            up()
        }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertEquals(1, keyboardRequests, "正文轻点必须请求键盘") }
    }

    @Test
    fun mouseReportingTapRequestsKeyboardAndStillSendsRemoteClick() {
        val transport = attachMouseTransport(showTerminal())
        compose.onNodeWithTag("terminal").performTouchInput {
            down(center)
            up()
        }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle {
            assertEquals(1, keyboardRequests, "TUI 正文轻点也必须请求键盘，且只请求一次")
            assertEquals(2, transport.sent.size, "轻点仍发送远端 Down/Up")
        }
    }

    @AfterTest
    fun destroyControllers() {
        compose.runOnIdle { controllers.forEach { it.destroy() } }
    }

    @Test
    fun firstLongPressDragExtendsSelectionBeforeFingerLifts() {
        val controller = showTerminal()
        compose.onNodeWithTag("terminal").performTouchInput {
            val start = Offset(width * 0.1f, height * 0.25f)
            down(start)
            advanceEventTime(700)
            moveTo(start + Offset(width * 0.4f, 0f), delayMillis = 32)
        }
        compose.runOnIdle {
            assertTrue(controller.selection.isActive, "长按应激活选区")
            assertTrue(controller.selection.endCol > controller.selection.startCol, "同一次长按拖动必须扩展选区")
        }
        compose.onNodeWithTag("terminal").performTouchInput { up() }
        compose.runOnIdle { assertTrue(controller.selection.isActive, "抬起手指后应保留选区") }
    }

    @Test
    fun longPressWithoutMovementRetainsSelectionAndTapClearsIt() {
        val controller = showTerminal()
        compose.onNodeWithTag("terminal").performTouchInput {
            down(center)
            advanceEventTime(700)
            up()
        }
        compose.runOnIdle { assertTrue(controller.selectionActive) }
        compose.onNodeWithTag("terminal").performTouchInput {
            advanceEventTime(400)
            down(Offset(width * 0.1f, height * 0.25f))
            up()
        }
        // 单击回调需等待双击识别窗口结束。
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle {
            assertFalse(controller.selectionActive)
            assertFalse(controller.selection.isActive)
            assertEquals(0, keyboardRequests, "长按和取消选区均不应弹键盘")
        }
    }

    @Test
    fun ordinaryDragAfterLongPressDoesNotExtendOldSelection() {
        val controller = showTerminal()
        compose.onNodeWithTag("terminal").performTouchInput {
            down(center)
            advanceEventTime(700)
            up()
        }
        val endCol = compose.runOnIdle { controller.selection.endCol }
        compose.onNodeWithTag("terminal").performTouchInput {
            advanceEventTime(400)
            swipe(Offset(width * 0.1f, height * 0.5f), Offset(width * 0.6f, height * 0.5f))
        }
        compose.runOnIdle { assertEquals(endCol, controller.selection.endCol) }
    }

    @Test
    fun stationaryEdgeDragScrollsBothDirectionsAndStopsOnRelease() {
        val controller = showTerminal()
        compose.runOnIdle {
            controller.emulator.writeText((0..99).joinToString("\r\n") { "line$it" })
            controller.frame++
        }
        compose.onNodeWithTag("terminal").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveTo(Offset(width * 0.5f, 1f), delayMillis = 32)
        }
        compose.mainClock.advanceTimeBy(200)
        val historyOffset =
            compose.runOnIdle {
                assertTrue(controller.scrollOffset > 0, "顶部应滚向历史")
                assertTrue(controller.selection.endRow < controller.selection.startRow)
                controller.scrollOffset
            }
        compose.onNodeWithTag("terminal").performTouchInput {
            moveTo(Offset(width * 0.5f, height - 1f), delayMillis = 32)
        }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertTrue(controller.scrollOffset < historyOffset, "底部应滚回最新输出") }
        compose.onNodeWithTag("terminal").performTouchInput { up() }
        val releasedOffset = compose.runOnIdle { controller.scrollOffset }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(releasedOffset, controller.scrollOffset, "抬起后停止自动滚动") }
    }

    @Test
    fun normalSwipeScrollsWithoutStartingSelection() {
        val controller = showTerminal()
        compose.runOnIdle {
            controller.emulator.writeText((0..99).joinToString("\r\n") { "line$it" })
            controller.frame++
        }
        compose.onNodeWithTag("terminal").performTouchInput {
            swipe(Offset(width * 0.5f, height * 0.25f), Offset(width * 0.5f, height * 0.75f))
        }
        compose.runOnIdle {
            assertTrue(controller.scrollOffset > 0)
            assertFalse(controller.selectionActive)
            assertEquals(0, keyboardRequests, "滑动回看不应弹键盘")
        }
    }

    @Test
    fun mouseReportingShortDragDoesNotStartLocalSelection() {
        val controller = showTerminal()
        val transport = attachMouseTransport(controller)
        compose.onNodeWithTag("terminal").performTouchInput {
            down(center)
            moveTo(center + Offset(width * 0.25f, 0f), delayMillis = 32)
            up()
        }
        compose.runOnIdle {
            assertFalse(controller.selectionActive)
            assertTrue(transport.sent.any { it.startsWith("\u001b[<32;") }, "短拖动应发送远端鼠标 motion")
            assertEquals(0, keyboardRequests, "远端拖动不应弹键盘")
        }
    }

    @Test
    fun mouseReportingLongPressDragSelectsLocally() {
        val controller = showTerminal()
        val transport = attachMouseTransport(controller)
        compose.onNodeWithTag("terminal").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveTo(center + Offset(width * 0.25f, 0f), delayMillis = 32)
            up()
        }
        compose.runOnIdle {
            assertTrue(controller.selectionActive, "TUI 鼠标模式下长按也应允许本地复制")
            assertTrue(controller.selection.endCol > controller.selection.startCol)
            assertTrue(transport.sent.isEmpty(), "本地长按拖选和松手均不能发送远端鼠标事件")
            assertEquals(0, keyboardRequests, "TUI 长按拖选不应弹键盘")
        }
    }

    @Test
    fun mouseReportingTapClearsLocalSelectionWithoutRemoteClick() {
        val controller = showTerminal()
        val transport = attachMouseTransport(controller)
        compose.runOnIdle { controller.selection.start(0, 0) }
        compose.onNodeWithTag("terminal").performTouchInput {
            down(center)
            up()
        }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle {
            assertFalse(controller.selectionActive)
            assertTrue(transport.sent.isEmpty())
            assertEquals(0, keyboardRequests, "退出选区的轻点不应弹键盘")
        }
        compose.onNodeWithTag("terminal").performTouchInput {
            advanceEventTime(400)
            down(center)
            up()
        }
        compose.runOnIdle { assertEquals(2, transport.sent.size, "退出选择后短按恢复远端 Down/Up") }
    }

    @Test
    fun doubleTapSelectsWordAndKeepsSelectionOnRelease() {
        val controller = showTerminal()
        compose.runOnIdle {
            controller.emulator.writeText("a".repeat(controller.buffer.cols * controller.buffer.rows))
            controller.frame++
        }
        compose.onNodeWithTag("terminal").performTouchInput { doubleClick(center) }
        compose.runOnIdle {
            assertTrue(controller.selectionActive)
            assertEquals("a".repeat(controller.buffer.cols), controller.selection.selectedText())
            assertEquals(0, keyboardRequests, "双击选词不应弹键盘")
        }
    }

    private fun showTerminal(): TerminalController {
        val controller =
            TerminalController(
                host = Host(id = "selection-test", name = "test", hostname = "localhost", username = "test"),
                password = null,
                privateKeyPem = null,
                repository = HostRepository(MapSettings()),
            )
        controllers.add(controller)
        compose.setContent {
            TerminalView(
                controller = controller,
                theme = TerminalThemes.DEFAULT,
                fontSizeSp = 14f,
                onRequestKeyboard = { keyboardRequests++ },
                modifier = Modifier.size(320.dp, 240.dp).testTag("terminal"),
            )
        }
        compose.waitForIdle()
        return controller
    }

    private fun attachMouseTransport(controller: TerminalController): RecordingTransport =
        RecordingTransport().also { transport ->
            compose.runOnIdle {
                controller.buffer.mouseTracking = 1002
                controller.buffer.mouseSgr = true
                controller.session = transport
            }
        }

    /** 只收集 UI 发往传输边界的真实字节；本组测试不建立网络连接。 */
    private class RecordingTransport : SshSession {
        val sent = mutableListOf<String>()

        override fun sendData(data: ByteArray) {
            sent.add(data.decodeToString())
        }

        override fun resize(
            columns: Int,
            rows: Int,
            widthPx: Int,
            heightPx: Int,
        ) = Unit

        override fun close() = Unit

        override fun isActive(): Boolean = true

        override fun connectAndStart(
            columns: Int,
            rows: Int,
        ): SessionInfo = error("UI 测试不得建连")

        override fun connectAndRun(
            command: String,
            timeoutMs: Long,
        ): CommandResult = error("UI 测试不得执行远端命令")

        override fun runCommandDetailed(
            command: String,
            timeoutMs: Long,
        ): CommandOutput = error("UI 测试不得执行远端命令")

        override fun startExec(
            command: String,
            columns: Int,
            rows: Int,
        ): SshExecChannel = error("UI 测试不得打开远端通道")
    }
}
