package dev.termish.ui

import com.russhwolf.settings.MapSettings
import dev.termish.TermishApplication
import dev.termish.data.Host
import dev.termish.data.HostRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TermishApplication::class)
class TerminalSelectionStateTest {
    private val controllers = mutableListOf<TerminalController>()

    @AfterTest
    fun destroyControllers() {
        controllers.forEach { it.destroy() }
    }

    @Test
    fun everySelectionChangeUpdatesVisibilityAndRedraw() {
        val controller = controller()
        var lastFrame = controller.frame
        controller.selection.start(0, 1)
        assertTrue(controller.selectionActive)
        assertTrue(controller.frame > lastFrame)
        lastFrame = controller.frame
        controller.selection.extend(1, 3)
        assertTrue(controller.frame > lastFrame)
        lastFrame = controller.frame
        // 返回键、复制、取消和 Agent 入口均通过同一 clear 路径退出。
        controller.selection.clear()
        assertFalse(controller.selectionActive)
        assertTrue(controller.frame > lastFrame)
    }

    @Test
    fun selectAllUsesVisibleHistoryAndInvalidatesCanvas() {
        val controller = controller()
        controller.buffer.resize(8, 3)
        controller.emulator.writeText((0..7).joinToString("\r\n") { "row$it" })
        controller.scrollOffset = 3
        val frame = controller.frame

        controller.selectVisibleText()

        assertEquals("row2\nrow3\nrow4", controller.selection.selectedText())
        assertTrue(controller.selectionActive)
        assertTrue(controller.frame > frame)
        controller.scrollOffset = 0
        controller.selectVisibleText()
        assertEquals("row5\nrow6\nrow7", controller.selection.selectedText())
    }

    @Test
    fun sessionsKeepIndependentSelectionAndViewport() {
        val first = controller()
        val second = controller()
        first.selection.start(0, 0)
        first.scrollOffset = 2

        assertTrue(first.selectionActive)
        assertFalse(second.selectionActive)
        assertEquals(0, second.scrollOffset)
        second.selection.start(0, 0)
        first.selection.clear()
        assertTrue(second.selectionActive)
        assertFalse(first.selectionActive)
    }

    private fun controller(): TerminalController =
        TerminalController(
            host = Host(id = "selection-${controllers.size}", name = "test", hostname = "localhost", username = "test"),
            password = null,
            privateKeyPem = null,
            repository = HostRepository(MapSettings()),
        ).also { controllers.add(it) }
}
