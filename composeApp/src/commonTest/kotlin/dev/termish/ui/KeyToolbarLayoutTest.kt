package dev.termish.ui

import dev.termish.data.AppSettings
import dev.termish.data.KeyToolbarLayout
import dev.termish.data.ToolbarAction
import dev.termish.data.ToolbarKeyColor
import dev.termish.data.ToolbarKeyConfig
import dev.termish.data.ToolbarPos
import dev.termish.ui.theme.TerminalThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KeyToolbarLayoutTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun defaultLayoutMatchesExpectedGrid() {
        val rows = KeyToolbarLayout.DEFAULT.rows
        assertEquals(KeyToolbarLayout.ROWS, rows.size)
        rows.forEach { assertEquals(KeyToolbarLayout.COLS, it.size) }

        assertEquals(
            listOf(
                ToolbarAction.ESC,
                ToolbarAction.TAB,
                ToolbarAction.CTRL_C,
                ToolbarAction.CTRL_L,
                ToolbarAction.SNIPPETS,
                ToolbarAction.UP,
                ToolbarAction.KEYBOARD,
                ToolbarAction.EXPAND,
            ),
            rows[0].map { it.action },
        )
        assertEquals(
            listOf(
                ToolbarAction.CTRL,
                ToolbarAction.ALT,
                ToolbarAction.CTRL_D,
                ToolbarAction.SLASH,
                ToolbarAction.LEFT,
                ToolbarAction.DOWN,
                ToolbarAction.RIGHT,
                ToolbarAction.ENTER,
            ),
            rows[1].map { it.action },
        )
        assertEquals(
            listOf(
                ToolbarAction.PASTE,
                ToolbarAction.SHIFT_TAB,
                ToolbarAction.DEL,
                ToolbarAction.HOME,
                ToolbarAction.END,
                ToolbarAction.PGUP,
                ToolbarAction.PGDN,
                ToolbarAction.CTRL_BACKSLASH,
            ),
            rows[2].map { it.action },
        )
        assertEquals(
            listOf(
                ToolbarAction.F1,
                ToolbarAction.F2,
                ToolbarAction.F3,
                ToolbarAction.F4,
                ToolbarAction.F5,
                ToolbarAction.F6,
                ToolbarAction.F7,
                ToolbarAction.GIT,
            ),
            rows[3].map { it.action },
        )
    }

    @Test
    fun normalizedFixesDimensionsAndEnforcesExpandOnlyAtFixedSlot() {
        val malformed =
            KeyToolbarLayout(
                listOf(
                    listOf(
                        ToolbarKeyConfig(ToolbarAction.EXPAND),
                        ToolbarKeyConfig(ToolbarAction.TAB),
                    ),
                ),
            )
        val norm = malformed.normalized()
        assertEquals(KeyToolbarLayout.ROWS, norm.rows.size)
        norm.rows.forEach { assertEquals(KeyToolbarLayout.COLS, it.size) }
        assertEquals(ToolbarAction.EMPTY, norm[ToolbarPos(0, 0)].action)
        assertEquals(ToolbarAction.TAB, norm[ToolbarPos(0, 1)].action)
        assertEquals(ToolbarAction.EXPAND, norm[KeyToolbarLayout.FIXED].action)
    }

    @Test
    fun updateAndSwapProtectFixedSlot() {
        val base = KeyToolbarLayout.DEFAULT
        val custom = ToolbarKeyConfig(ToolbarAction.CUSTOM, label = "ll", payload = "ls -la\\r")

        // 固定槽位不可修改，其它槽位也不可被写成 EXPAND
        assertEquals(base, base.update(KeyToolbarLayout.FIXED, custom))
        assertEquals(base, base.update(ToolbarPos(0, 0), ToolbarKeyConfig(ToolbarAction.EXPAND)))
        assertEquals(base, base.swap(ToolbarPos(0, 0), KeyToolbarLayout.FIXED))
        assertEquals(base, base.swap(KeyToolbarLayout.FIXED, ToolbarPos(1, 7)))

        // 正常槽位更新与交换
        val updated = base.update(ToolbarPos(0, 0), custom)
        assertEquals(custom, updated[ToolbarPos(0, 0)])

        val swapped = updated.swap(ToolbarPos(0, 0), ToolbarPos(1, 0))
        assertEquals(ToolbarAction.CTRL, swapped[ToolbarPos(0, 0)].action)
        assertEquals(custom, swapped[ToolbarPos(1, 0)])
    }

    @Test
    fun crossRowSwapIntoEmptySlotPreservesCustomKeyAndSurvivesReload() {
        val from = ToolbarPos(2, 0)
        val to = ToolbarPos(1, 7)
        val key = ToolbarKeyConfig(ToolbarAction.CUSTOM, "ll", "ls -la\\r", ToolbarKeyColor.CYAN)
        val layout =
            KeyToolbarLayout.DEFAULT
                .update(from, key)
                .update(to, ToolbarKeyConfig(ToolbarAction.EMPTY))
                .swap(from, to)
        val saved = json.encodeToString(AppSettings(keyToolbarLayout = layout))
        val reloaded = json.decodeFromString<AppSettings>(saved).keyToolbarLayout!!.normalized()

        assertEquals(key, reloaded[to])
        assertEquals(ToolbarAction.EMPTY, reloaded[from].action)
        assertEquals(ToolbarAction.EXPAND, reloaded[KeyToolbarLayout.FIXED].action)
    }

    @Test
    fun legacyAppSettingsJsonDeserializesToNullAndCustomRoundTrips() {
        val legacy = json.decodeFromString<AppSettings>("""{"theme":"DARK"}""")
        assertNull(legacy.keyToolbarLayout)
        assertTrue(legacy.toolbarFrostedGlass)

        val customKey =
            ToolbarKeyConfig(
                action = ToolbarAction.CUSTOM,
                label = "htop",
                payload = "htop\\r",
                color = ToolbarKeyColor.CYAN,
            )
        val modified =
            AppSettings(
                keyToolbarLayout = KeyToolbarLayout.DEFAULT.update(ToolbarPos(3, 6), customKey),
                toolbarFrostedGlass = false,
            )
        val encoded = json.encodeToString(modified)
        val decoded = json.decodeFromString<AppSettings>(encoded)
        assertEquals(customKey, decoded.keyToolbarLayout?.get(ToolbarPos(3, 6)))
        assertEquals(false, decoded.toolbarFrostedGlass)
    }

    @Test
    fun everyActionHasCategoryLabelAndColorMapping() {
        val theme = TerminalThemes.ALL.first()
        val listedBuiltins = BUILTIN_ACTIONS_BY_CATEGORY.flatMap { it.second }.toSet()

        for (action in ToolbarAction.entries) {
            when (action) {
                ToolbarAction.EMPTY -> assertNull(action.category)
                ToolbarAction.CUSTOM -> {
                    assertEquals(KeyCategory.CUSTOM, action.category)
                    assertNull(ToolbarKeyConfig(action).accentColor(theme))
                }
                ToolbarAction.EXPAND -> {
                    assertEquals(KeyCategory.PANEL, action.category)
                    assertEquals(theme.ansi(6), ToolbarKeyConfig(action).accentColor(theme))
                }
                else -> {
                    assertNotNull(action.category, "Missing category for $action")
                    assertTrue(action.label.isNotBlank(), "Blank label for $action")
                    assertTrue(action in listedBuiltins, "$action missing from BUILTIN_ACTIONS_BY_CATEGORY")
                    assertNotNull(ToolbarKeyConfig(action).accentColor(theme), "Missing default accent for $action")
                }
            }
        }

        // 单键颜色覆盖优先于分类默认色
        val neutralEsc = ToolbarKeyConfig(ToolbarAction.ESC, color = ToolbarKeyColor.NEUTRAL)
        assertNull(neutralEsc.accentColor(theme))
        val redEsc = ToolbarKeyConfig(ToolbarAction.ESC, color = ToolbarKeyColor.RED)
        assertEquals(theme.ansi(1), redEsc.accentColor(theme))
    }
}
