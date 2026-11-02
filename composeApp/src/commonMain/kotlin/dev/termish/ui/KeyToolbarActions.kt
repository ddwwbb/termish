package dev.termish.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import dev.termish.data.ToolbarAction
import dev.termish.data.ToolbarKeyColor
import dev.termish.data.ToolbarKeyConfig
import dev.termish.ui.theme.KeyToolbarTokens
import dev.termish.ui.theme.TerminalTheme

/** 快捷键功能分类：决定 [ToolbarKeyColor.AUTO] 时的默认颜色与编辑页分组。 */
enum class KeyCategory(
    val defaultColor: ToolbarKeyColor,
) {
    /** 控制信号（⌃C ⌃D ⌃\ ⌃Z）。 */
    SIGNAL(ToolbarKeyColor.RED),

    /** 编辑（ESC TAB ⇧⇥ ENT DEL / ⌃L ⌃E ⌃R）。 */
    EDIT(ToolbarKeyColor.GREEN),

    /** 功能键 F1–F12。 */
    FUNCTION(ToolbarKeyColor.YELLOW),

    /** 导航（方向键 / HOME / END / PGUP / PGDN）。 */
    NAVIGATION(ToolbarKeyColor.BLUE),

    /** 粘性修饰键 CTRL / ALT。 */
    MODIFIER(ToolbarKeyColor.MAGENTA),

    /** 面板入口（⌨ {} ⎇ PST ▾）。 */
    PANEL(ToolbarKeyColor.CYAN),

    /** 用户自定义键：默认不着色，可单独选色。 */
    CUSTOM(ToolbarKeyColor.NEUTRAL),
}

/** 内置键显示标签（[ToolbarAction.CUSTOM] 用配置里的 label；EMPTY / EXPAND 无文字）。 */
val ToolbarAction.label: String
    get() =
        when (this) {
            ToolbarAction.EMPTY, ToolbarAction.CUSTOM, ToolbarAction.EXPAND -> ""
            ToolbarAction.ESC -> "ESC"
            ToolbarAction.TAB -> "TAB"
            ToolbarAction.SHIFT_TAB -> "⇧TAB"
            ToolbarAction.ENTER -> "ENT"
            ToolbarAction.DEL -> "DEL"
            ToolbarAction.SLASH -> "/"
            ToolbarAction.CTRL_L -> "⌃L"
            ToolbarAction.CTRL_E -> "⌃E"
            ToolbarAction.CTRL_R -> "⌃R"
            ToolbarAction.CTRL -> "CTRL"
            ToolbarAction.ALT -> "ALT"
            ToolbarAction.UP -> "↑"
            ToolbarAction.DOWN -> "↓"
            ToolbarAction.LEFT -> "←"
            ToolbarAction.RIGHT -> "→"
            ToolbarAction.HOME -> "HOME"
            ToolbarAction.END -> "END"
            ToolbarAction.PGUP -> "PGUP"
            ToolbarAction.PGDN -> "PGDN"
            ToolbarAction.CTRL_C -> "⌃C"
            ToolbarAction.CTRL_D -> "⌃D"
            ToolbarAction.CTRL_Z -> "⌃Z"
            ToolbarAction.CTRL_BACKSLASH -> "⌃\\"
            ToolbarAction.F1 -> "F1"
            ToolbarAction.F2 -> "F2"
            ToolbarAction.F3 -> "F3"
            ToolbarAction.F4 -> "F4"
            ToolbarAction.F5 -> "F5"
            ToolbarAction.F6 -> "F6"
            ToolbarAction.F7 -> "F7"
            ToolbarAction.F8 -> "F8"
            ToolbarAction.F9 -> "F9"
            ToolbarAction.F10 -> "F10"
            ToolbarAction.F11 -> "F11"
            ToolbarAction.F12 -> "F12"
            ToolbarAction.PASTE -> "PST"
            ToolbarAction.SNIPPETS -> "{}"
            ToolbarAction.KEYBOARD -> "⌨"
            ToolbarAction.GIT -> "⎇"
        }

/** 功能分类；EMPTY 无分类（不渲染按键）。 */
val ToolbarAction.category: KeyCategory?
    get() =
        when (this) {
            ToolbarAction.EMPTY -> null
            ToolbarAction.CUSTOM -> KeyCategory.CUSTOM
            ToolbarAction.CTRL_C, ToolbarAction.CTRL_D, ToolbarAction.CTRL_Z, ToolbarAction.CTRL_BACKSLASH ->
                KeyCategory.SIGNAL
            ToolbarAction.ESC, ToolbarAction.TAB, ToolbarAction.SHIFT_TAB, ToolbarAction.ENTER, ToolbarAction.DEL,
            ToolbarAction.SLASH, ToolbarAction.CTRL_L, ToolbarAction.CTRL_E, ToolbarAction.CTRL_R,
            -> KeyCategory.EDIT
            ToolbarAction.F1, ToolbarAction.F2, ToolbarAction.F3, ToolbarAction.F4, ToolbarAction.F5, ToolbarAction.F6,
            ToolbarAction.F7, ToolbarAction.F8, ToolbarAction.F9, ToolbarAction.F10, ToolbarAction.F11, ToolbarAction.F12,
            -> KeyCategory.FUNCTION
            ToolbarAction.UP, ToolbarAction.DOWN, ToolbarAction.LEFT, ToolbarAction.RIGHT, ToolbarAction.HOME,
            ToolbarAction.END, ToolbarAction.PGUP, ToolbarAction.PGDN,
            -> KeyCategory.NAVIGATION
            ToolbarAction.CTRL, ToolbarAction.ALT -> KeyCategory.MODIFIER
            ToolbarAction.EXPAND, ToolbarAction.PASTE, ToolbarAction.SNIPPETS, ToolbarAction.KEYBOARD, ToolbarAction.GIT ->
                KeyCategory.PANEL
        }

/** 直接映射为终端转义序列的内置键（CTRL/ALT 为粘性修饰键，单独处理，不在此列）。 */
val ToolbarAction.specialKey: SpecialKey?
    get() =
        when (this) {
            ToolbarAction.ESC -> SpecialKey.ESC
            ToolbarAction.TAB -> SpecialKey.TAB
            ToolbarAction.SHIFT_TAB -> SpecialKey.SHIFT_TAB
            ToolbarAction.ENTER -> SpecialKey.ENTER
            ToolbarAction.DEL -> SpecialKey.DEL
            ToolbarAction.CTRL_L -> SpecialKey.CTRL_L
            ToolbarAction.CTRL_E -> SpecialKey.CTRL_E
            ToolbarAction.CTRL_R -> SpecialKey.CTRL_R
            ToolbarAction.UP -> SpecialKey.UP
            ToolbarAction.DOWN -> SpecialKey.DOWN
            ToolbarAction.LEFT -> SpecialKey.LEFT
            ToolbarAction.RIGHT -> SpecialKey.RIGHT
            ToolbarAction.HOME -> SpecialKey.HOME
            ToolbarAction.END -> SpecialKey.END
            ToolbarAction.PGUP -> SpecialKey.PGUP
            ToolbarAction.PGDN -> SpecialKey.PGDN
            ToolbarAction.CTRL_C -> SpecialKey.CTRL_C
            ToolbarAction.CTRL_D -> SpecialKey.CTRL_D
            ToolbarAction.CTRL_Z -> SpecialKey.CTRL_Z
            ToolbarAction.CTRL_BACKSLASH -> SpecialKey.CTRL_BACKSLASH
            ToolbarAction.F1 -> SpecialKey.F1
            ToolbarAction.F2 -> SpecialKey.F2
            ToolbarAction.F3 -> SpecialKey.F3
            ToolbarAction.F4 -> SpecialKey.F4
            ToolbarAction.F5 -> SpecialKey.F5
            ToolbarAction.F6 -> SpecialKey.F6
            ToolbarAction.F7 -> SpecialKey.F7
            ToolbarAction.F8 -> SpecialKey.F8
            ToolbarAction.F9 -> SpecialKey.F9
            ToolbarAction.F10 -> SpecialKey.F10
            ToolbarAction.F11 -> SpecialKey.F11
            ToolbarAction.F12 -> SpecialKey.F12
            ToolbarAction.EMPTY, ToolbarAction.CUSTOM, ToolbarAction.EXPAND, ToolbarAction.SLASH,
            ToolbarAction.CTRL, ToolbarAction.ALT, ToolbarAction.PASTE, ToolbarAction.SNIPPETS,
            ToolbarAction.KEYBOARD, ToolbarAction.GIT,
            -> null
        }

/** 方向键按住连发（对齐实体键盘手感）。 */
val ToolbarAction.repeats: Boolean
    get() =
        this == ToolbarAction.UP || this == ToolbarAction.DOWN || this == ToolbarAction.LEFT || this == ToolbarAction.RIGHT

/** 优先使用矢量图标渲染的内置键（方向键用带杆箭头，与右上角无杆展开箭头 ▾ 区分）。 */
val ToolbarAction.icon: ImageVector?
    get() =
        when (this) {
            ToolbarAction.UP -> Icons.Filled.ArrowUpward
            ToolbarAction.DOWN -> Icons.Filled.ArrowDownward
            ToolbarAction.LEFT -> Icons.AutoMirrored.Filled.ArrowBack
            ToolbarAction.RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
            ToolbarAction.KEYBOARD -> Icons.Filled.Keyboard
            ToolbarAction.GIT -> Icons.Filled.AccountTree
            else -> null
        }

val ToolbarAction.iconSize: Dp
    get() = KeyToolbarTokens.ActionIconSize

/** 槽位显示标签：自定义键取用户标签，其余取内置标签。 */
val ToolbarKeyConfig.displayLabel: String
    get() = if (action == ToolbarAction.CUSTOM) label else action.label

/** 实际生效颜色：AUTO 解析为分类默认色。 */
fun ToolbarKeyConfig.resolvedColor(): ToolbarKeyColor = if (color == ToolbarKeyColor.AUTO) action.category?.defaultColor ?: ToolbarKeyColor.NEUTRAL else color

/** 颜色 → 主题 ANSI 普通色（1–6）；NEUTRAL / AUTO 返回 null（沿用前景色样式）。 */
fun ToolbarKeyColor.toAnsi(theme: TerminalTheme): Color? =
    when (this) {
        ToolbarKeyColor.AUTO, ToolbarKeyColor.NEUTRAL -> null
        ToolbarKeyColor.RED -> theme.ansi(1)
        ToolbarKeyColor.GREEN -> theme.ansi(2)
        ToolbarKeyColor.YELLOW -> theme.ansi(3)
        ToolbarKeyColor.BLUE -> theme.ansi(4)
        ToolbarKeyColor.MAGENTA -> theme.ansi(5)
        ToolbarKeyColor.CYAN -> theme.ansi(6)
    }

/** 槽位强调色：null = 不着色。 */
fun ToolbarKeyConfig.accentColor(theme: TerminalTheme): Color? = resolvedColor().toAnsi(theme)

/** 自定义键发送内容解析失败：[index] 为出错转义在原文中的起始位置（0 基）。 */
class KeyPayloadException(
    val index: Int,
) : IllegalArgumentException("invalid escape at $index")

/**
 * 解析自定义键发送内容（转义记法 → 原始字节）。
 *
 * - `\n` `\r` `\t` `\e` `\\` `\^`、`\xHH`（原始字节）、`\uXXXX`（Unicode 字符，UTF-8 编码）
 * - `^@`–`^_`、`^a`–`^z`（大小写等价）为控制字符，`^?` 为 DEL（0x7f）
 * - 其余字符按 UTF-8 原样发送；不自动追加回车
 *
 * 非法转义（如 `\q`、`\x4`、末尾孤立的 `\` 或 `^`）返回 [KeyPayloadException]。
 */
fun parseKeyPayload(text: String): Result<ByteArray> {
    val out = ArrayList<Byte>(text.length)
    val pending = StringBuilder()

    fun flush() {
        if (pending.isNotEmpty()) {
            pending.toString().encodeToByteArray().forEach { out.add(it) }
            pending.clear()
        }
    }

    fun hex(
        start: Int,
        count: Int,
    ): Int? {
        if (start + count > text.length) return null
        val digits = text.substring(start, start + count)
        // toIntOrNull(16) 接受前导 +/-，需先逐位校验
        if (!digits.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
        return digits.toInt(16)
    }

    var i = 0
    while (i < text.length) {
        val c = text[i]
        when (c) {
            '\\' -> {
                val next = text.getOrNull(i + 1) ?: return Result.failure(KeyPayloadException(i))
                when (next) {
                    'n' -> pending.append('\n')
                    'r' -> pending.append('\r')
                    't' -> pending.append('\t')
                    'e' -> pending.append('\u001b')
                    '\\' -> pending.append('\\')
                    '^' -> pending.append('^')
                    'x' -> {
                        val v = hex(i + 2, 2) ?: return Result.failure(KeyPayloadException(i))
                        flush()
                        out.add(v.toByte())
                        i += 4
                        continue
                    }
                    'u' -> {
                        val v = hex(i + 2, 4) ?: return Result.failure(KeyPayloadException(i))
                        pending.append(v.toChar())
                        i += 6
                        continue
                    }
                    else -> return Result.failure(KeyPayloadException(i))
                }
                i += 2
            }
            '^' -> {
                val next = text.getOrNull(i + 1) ?: return Result.failure(KeyPayloadException(i))
                val upper = next.uppercaseChar()
                val code =
                    when {
                        next == '?' -> 0x7f
                        upper in '@'..'_' -> upper.code - 0x40
                        else -> return Result.failure(KeyPayloadException(i))
                    }
                pending.append(code.toChar())
                i += 2
            }
            else -> {
                pending.append(c)
                i += 1
            }
        }
    }
    flush()
    return Result.success(out.toByteArray())
}
