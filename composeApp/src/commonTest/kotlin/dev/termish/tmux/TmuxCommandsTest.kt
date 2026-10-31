package dev.termish.tmux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TmuxCommands 命令构造与输出解析单元测试。 */
class TmuxCommandsTest {
    // ---------- parseDiscovery ----------

    @Test
    fun discoveryReadyWithSessions() {
        val out =
            """
            __TERMISH_TMUX__:ready
            __TERMISH_TMUX_TERM__:xterm-256color
            ${'$'}0:main:3:1:1700000000
            ${'$'}1:dev:work:2:0:1700000100
            """.trimIndent()
        val d = TmuxCommands.parseDiscovery(out)
        val ready = assertIs<TmuxDiscovery.Ready>(d)
        assertEquals("xterm-256color", ready.term)
        assertEquals(2, ready.sessions.size)
        assertEquals("$0", ready.sessions[0].id)
        assertEquals("main", ready.sessions[0].name)
        assertEquals(3, ready.sessions[0].windows)
        assertEquals(1, ready.sessions[0].clients)
        assertEquals(1700000000L, ready.sessions[0].created)
        // 名称含冒号：中间字段全部归名，数值从行尾反向解析
        assertEquals("dev:work", ready.sessions[1].name)
    }

    @Test
    fun discoveryZeroSessionsIsValid() {
        val out = "__TERMISH_TMUX__:ready\n"
        val d = TmuxCommands.parseDiscovery(out)
        val ready = assertIs<TmuxDiscovery.Ready>(d)
        assertTrue(ready.sessions.isEmpty())
        assertNull(ready.term)
    }

    @Test
    fun discoveryMissingMarker() {
        assertEquals(TmuxDiscovery.NotInstalled, TmuxCommands.parseDiscovery("__TERMISH_TMUX__:missing\n"))
    }

    @Test
    fun discoveryMalformedOutputs() {
        assertEquals(TmuxDiscovery.Malformed, TmuxCommands.parseDiscovery(""))
        assertEquals(TmuxDiscovery.Malformed, TmuxCommands.parseDiscovery("garbage\n"))
        assertEquals(TmuxDiscovery.Malformed, TmuxCommands.parseDiscovery("__TERMISH_TMUX__:ready\nbad-line\n"))
        assertEquals(TmuxDiscovery.Malformed, TmuxCommands.parseDiscovery("__TERMISH_TMUX__:ready\n$0:short:1\n"))
    }

    @Test
    fun discoveryChineseSessionName() {
        val out = "__TERMISH_TMUX__:ready\n$2:项目-开发:1:0:1700000000\n"
        val d = TmuxCommands.parseDiscovery(out)
        val ready = assertIs<TmuxDiscovery.Ready>(d)
        assertEquals("项目-开发", ready.sessions.single().name)
    }

    // ---------- action 包装 ----------

    @Test
    fun actionOkAndFailure() {
        val cmd = TmuxCommands.actionCmd(TmuxCommands.killCmd("$1"))
        assertTrue(cmd.contains("tmux kill-session -t '$1'"))
        val ok = TmuxCommands.parseAction("__TERMISH_TMUX_RC__:0\n")
        assertEquals(true, ok?.ok)
        val fail = TmuxCommands.parseAction("__TERMISH_TMUX_RC__:1\ncan't find session: $1\n")
        assertEquals(false, fail?.ok)
        assertEquals("can't find session: $1", fail?.output)
        assertNull(TmuxCommands.parseAction("unexpected output"))
        assertNull(TmuxCommands.parseAction("__TERMISH_TMUX_RC__:abc\n"))
    }

    // ---------- 名称处理 ----------

    @Test
    fun defaultSessionNameSanitizes() {
        assertEquals("my-proj", TmuxCommands.defaultSessionName("my proj"))
        assertEquals("a-b-c", TmuxCommands.defaultSessionName("a:b.c"))
        assertEquals("termish", TmuxCommands.defaultSessionName("///"))
        assertEquals(32, TmuxCommands.defaultSessionName("x".repeat(64)).length)
    }

    @Test
    fun validSessionNameRules() {
        assertTrue(TmuxCommands.isValidSessionName("work"))
        assertTrue(TmuxCommands.isValidSessionName("项目-1"))
        assertFalse(TmuxCommands.isValidSessionName("a:b"))
        assertFalse(TmuxCommands.isValidSessionName("a.b"))
        assertFalse(TmuxCommands.isValidSessionName("a b"))
        assertFalse(TmuxCommands.isValidSessionName(""))
    }

    // ---------- 附加命令 ----------

    @Test
    fun attachCommandShape() {
        val cmd = TmuxCommands.attachOrCreateCmd("work")
        assertTrue(cmd.startsWith("sh -c '"))
        assertTrue(cmd.contains("tmux -u new-session -A -s \"\$1\""))
        assertTrue(cmd.contains("xterm-256color"))
        assertTrue(cmd.endsWith("' sh 'work'"))
        // detachOthers 加 -D；协商 term 插到候选首位
        assertTrue(TmuxCommands.attachOrCreateCmd("w", detachOthers = true).contains(" -A -D"))
        val negotiated = TmuxCommands.attachOrCreateCmd("w", term = "screen-256color")
        val first = negotiated.substringAfter("for t in ").substringBefore(";")
        assertEquals("screen-256color", first.substringBefore(' '))
    }
}
