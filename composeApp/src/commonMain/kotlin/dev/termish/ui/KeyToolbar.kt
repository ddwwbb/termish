package dev.termish.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import dev.termish.data.KeyToolbarLayout
import dev.termish.data.ToolbarAction
import dev.termish.data.ToolbarKeyConfig
import dev.termish.ui.theme.KeyToolbarTokens
import dev.termish.ui.theme.Sizes
import dev.termish.ui.theme.TerminalTheme
import dev.termish.util.TermLog
import dev.termish.util.monospaceFontFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 特殊键。CTRL/ALT 为粘性修饰键，仅影响后续输入。
 *  低频键（F1–F12 / HOME / END / PGUP / PGDN / DEL / ⌃Z 等）收纳在
 *  工具栏「⋯」溢出面板（MoreKeySheet），不再占常驻键位；
 *  转义映射见下方 specialKeyBytes。 */
enum class SpecialKey(
    val label: String,
) {
    ESC("ESC"),
    TAB("TAB"),
    CTRL("CTRL"),
    ALT("ALT"),
    UP("↑"),
    DOWN("↓"),
    LEFT("←"),
    RIGHT("→"),
    CTRL_C("⌃C"),
    CTRL_D("⌃D"),
    CTRL_L("⌃L"),
    CTRL_E("⌃E"),
    ENTER("ENT"),

    /** Shift+Tab（ESC[Z）：TUI 菜单反选；Claude Code 模式切换刚需（软键盘打不出）。
     *  常驻 Tab 长按触发。 */
    SHIFT_TAB("⇧⇥"),

    /** ⌃\（SIGQUIT）：长按 ⌃C/⌃D 触发，杀连 ⌃C 都不响应的顽固进程。 */
    CTRL_BACKSLASH("⌃\\"),

    // ---- 溢出面板键（⋯ 展开） ----
    CTRL_R("⌃R"),
    CTRL_Z("⌃Z"),
    DEL("DEL"),
    HOME("HOME"),
    END("END"),
    PGUP("PGUP"),
    PGDN("PGDN"),
    F1("F1"),
    F2("F2"),
    F3("F3"),
    F4("F4"),
    F5("F5"),
    F6("F6"),
    F7("F7"),
    F8("F8"),
    F9("F9"),
    F10("F10"),
    F11("F11"),
    F12("F12"),
}

fun specialKeyBytes(
    key: SpecialKey,
    applicationCursorKeys: Boolean,
): ByteArray =
    when (key) {
        SpecialKey.ESC -> byteArrayOf(0x1b)
        SpecialKey.TAB -> byteArrayOf(0x09)
        SpecialKey.UP -> cursorKeySeq('A', applicationCursorKeys)
        SpecialKey.DOWN -> cursorKeySeq('B', applicationCursorKeys)
        SpecialKey.RIGHT -> cursorKeySeq('C', applicationCursorKeys)
        SpecialKey.LEFT -> cursorKeySeq('D', applicationCursorKeys)
        SpecialKey.CTRL_C -> byteArrayOf(0x03)
        SpecialKey.CTRL_D -> byteArrayOf(0x04)
        SpecialKey.CTRL_L -> byteArrayOf(0x0c)
        SpecialKey.CTRL_E -> byteArrayOf(0x05)
        SpecialKey.CTRL_R -> byteArrayOf(0x12)
        SpecialKey.CTRL_Z -> byteArrayOf(0x1a)
        SpecialKey.ENTER -> byteArrayOf(0x0d)
        SpecialKey.SHIFT_TAB -> byteArrayOf(0x1b, 0x5b, 0x5a) // ESC [ Z
        SpecialKey.CTRL_BACKSLASH -> byteArrayOf(0x1c) // SIGQUIT
        SpecialKey.DEL -> byteArrayOf(0x7f)
        SpecialKey.HOME -> cursorKeySeq('H', applicationCursorKeys)
        SpecialKey.END -> cursorKeySeq('F', applicationCursorKeys)
        SpecialKey.PGUP -> escapeSeq("5~")
        SpecialKey.PGDN -> escapeSeq("6~")
        SpecialKey.F1 -> byteArrayOf(0x1b, 0x4f, 0x50) // ESC O P
        SpecialKey.F2 -> byteArrayOf(0x1b, 0x4f, 0x51) // ESC O Q
        SpecialKey.F3 -> byteArrayOf(0x1b, 0x4f, 0x52) // ESC O R
        SpecialKey.F4 -> byteArrayOf(0x1b, 0x4f, 0x53) // ESC O S
        SpecialKey.F5 -> byteArrayOf(0x1b, 0x5b, 0x31, 0x35, 0x7e) // ESC [15~
        SpecialKey.F6 -> byteArrayOf(0x1b, 0x5b, 0x31, 0x37, 0x7e) // ESC [17~
        SpecialKey.F7 -> byteArrayOf(0x1b, 0x5b, 0x31, 0x38, 0x7e) // ESC [18~
        SpecialKey.F8 -> byteArrayOf(0x1b, 0x5b, 0x31, 0x39, 0x7e) // ESC [19~
        SpecialKey.F9 -> byteArrayOf(0x1b, 0x5b, 0x32, 0x30, 0x7e) // ESC [20~
        SpecialKey.F10 -> byteArrayOf(0x1b, 0x5b, 0x32, 0x31, 0x7e) // ESC [21~
        SpecialKey.F11 -> byteArrayOf(0x1b, 0x5b, 0x32, 0x33, 0x7e) // ESC [23~
        SpecialKey.F12 -> byteArrayOf(0x1b, 0x5b, 0x32, 0x34, 0x7e) // ESC [24~
        SpecialKey.CTRL, SpecialKey.ALT -> ByteArray(0)
    }

private fun escapeSeq(suffix: String): ByteArray = ("\u001b[$suffix").encodeToByteArray()

/** DECCKM selects SS3 (ESC O) instead of CSI (ESC [) for cursor keys. */
private fun cursorKeySeq(
    final: Char,
    applicationCursorKeys: Boolean,
): ByteArray = byteArrayOf(0x1b, if (applicationCursorKeys) 0x4f else 0x5b, final.code.toByte())

/** 按住连发参数（对齐实体键盘手感）：先延迟后连发，越按越快不会失控。 */
private const val REPEAT_INITIAL_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 60L

/**
 * 终端功能键工具栏：固定两行 + 可展开到四行，键位由 [layout] 决定
 * （设置 → 快捷按钮可自定义；默认见 [KeyToolbarLayout.DEFAULT]）。
 *
 * 默认布局：
 * 行 1（控制）：ESC TAB ⌃C ⌃L {} ↑ ⌨ ▾（ESC 左上、⌨ 右上拇指区；
 * ▾ 展开/收起行 3/4，会话内保持，固定槽位不可编辑；↑ 第 6 列与行 2 的 ↓ 上下对齐）。
 * 行 2（修饰/编辑）：CTRL ALT ⌃D / ← ↓ → ENT（CTRL/ALT 左下，同实体键盘
 * 底行；↑/↓ 第 6 列上下对齐；/ 由 onChar 直接发送）。
 * 行 3（展开）：PST ⇧⇥ DEL HOME END PGUP PGDN ⌃\\
 * 行 4（展开）：F1–F7 + ⎇（Git 面板；F8–F12 极低频不占位）。
 * 每行均 8 列等宽正方形键；空槽位渲染为同尺寸占位保持列对齐。
 *
 * - 按功能分类着色（控制信号/编辑/功能键/导航/修饰键/面板入口），颜色取当前终端
 *   主题的 ANSI 色；单键可在设置里覆盖（见 [accentColor]）
 * - 方向键支持**按住连发**（400ms 后 60ms/次）
 * - 长按宏：**ESC 双发**（Claude Code 编辑历史）；**TAB → ⇧⇥**（TUI 反选/模式切换）；
 *   **⌃C/⌃D → ⌃\\**（SIGQUIT 杀顽固进程）——跟随动作走，键移到任何槽位都保留
 * - 粘性 CTRL/ALT + 系统键盘字母即可敲出 ⌃A/⌃E/⌃R 等组合（不占键位）
 * - 自定义键发送内容经 [parseKeyPayload] 解析为原始字节，由 [onCustom] 发送
 *
 * 展开状态上提至调用方（[expanded]/[onExpandedChange]），常驻两行高度经
 * [onBaseHeightChanged] 回调：画布只按常驻高度布局，展开行 3/4 覆盖画布
 * 而非压缩画布——不触发 PTY resize，TUI 不重排不闪屏。
 */
@Composable
fun KeyToolbar(
    ctrlActive: Boolean,
    altActive: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onKey: (SpecialKey) -> Unit,
    onToggleKeyboard: () -> Unit = {},
    onPaste: () -> Unit = {},
    /** 命令片段插入面板（默认行 1 的「{}」键位）。 */
    onSnippets: () -> Unit = {},
    /** Git 面板入口（默认行 4 展开的「⎇」键位；画布 FAB 的备用入口）。 */
    onGit: () -> Unit = {},
    /** 普通字符键（如 /），直接作为终端输入发送。 */
    onChar: (String) -> Unit = {},
    /** 自定义键：已解析的原始字节，直接发送。 */
    onCustom: (ByteArray) -> Unit = {},
    /** 键位布局（设置页自定义；调用方传入前无需 normalize）。 */
    layout: KeyToolbarLayout = KeyToolbarLayout.DEFAULT,
    /** 行 3/4 展开状态（上提：画布按常驻高度布局，展开行覆盖画布不 resize）。 */
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    /** 常驻两行高度回调（px；展开不改变）——建连门槛（画布尺寸稳定）用。 */
    onBaseHeightChanged: (Int) -> Unit = {},
    theme: TerminalTheme,
    modifier: Modifier = Modifier,
) {
    val rows = remember(layout) { layout.normalized().rows }
    val handlers =
        ToolbarHandlers(
            ctrlActive = ctrlActive,
            altActive = altActive,
            expanded = expanded,
            onToggleCtrl = onToggleCtrl,
            onToggleAlt = onToggleAlt,
            onKey = onKey,
            onToggleKeyboard = onToggleKeyboard,
            onPaste = onPaste,
            onSnippets = onSnippets,
            onGit = onGit,
            onChar = onChar,
            onCustom = onCustom,
            onExpandedChange = onExpandedChange,
        )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KeyToolbarTokens.RowGap),
    ) {
        // 行 3/4（▾ 展开）：低频键一步直达；每行保持 8 键等宽。
        // 必须放在常驻块【上方】：常驻块贴工具栏底边（位置固定），展开行从
        // 画布底边向上覆盖画布（不压缩画布、不 resize、TUI 不重排）——放下方
        // 会让工具栏整体变高、常驻两行被顶进画布区域盖住终端内容（v1.1.7 回归）
        if (expanded) {
            KeyRow(
                Modifier
                    .padding(horizontal = KeyToolbarTokens.ToolbarHorizontalPadding)
                    .padding(top = KeyToolbarTokens.ToolbarTopPadding),
            ) {
                rows[2].forEach { ToolbarSlot(it, theme, handlers) }
            }
            KeyRow(Modifier.padding(horizontal = KeyToolbarTokens.ToolbarHorizontalPadding)) {
                rows[3].forEach { ToolbarSlot(it, theme, handlers) }
            }
        }
        // 常驻块：行 1/2 贴工具栏底边（位置固定，展开不改变）
        Column(
            Modifier
                .onSizeChanged { onBaseHeightChanged(it.height) }
                .padding(horizontal = KeyToolbarTokens.ToolbarHorizontalPadding)
                .padding(top = KeyToolbarTokens.ToolbarTopPadding, bottom = KeyToolbarTokens.ToolbarBottomPadding),
            verticalArrangement = Arrangement.spacedBy(KeyToolbarTokens.RowGap),
        ) {
            KeyRow { rows[0].forEach { ToolbarSlot(it, theme, handlers) } }
            KeyRow { rows[1].forEach { ToolbarSlot(it, theme, handlers) } }
        } // 常驻块量测结束（行 3/4 在上方，不计入 onBaseHeightChanged）
    }
}

/** 工具栏一行：8 键居中等距排列。 */
@Composable
internal fun KeyRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyToolbarTokens.KeyGap, Alignment.CenterHorizontally),
        content = content,
    )
}

/** 工具栏回调集合（避免槽位分发函数参数爆炸）。 */
private class ToolbarHandlers(
    val ctrlActive: Boolean,
    val altActive: Boolean,
    val expanded: Boolean,
    val onToggleCtrl: () -> Unit,
    val onToggleAlt: () -> Unit,
    val onKey: (SpecialKey) -> Unit,
    val onToggleKeyboard: () -> Unit,
    val onPaste: () -> Unit,
    val onSnippets: () -> Unit,
    val onGit: () -> Unit,
    val onChar: (String) -> Unit,
    val onCustom: (ByteArray) -> Unit,
    val onExpandedChange: (Boolean) -> Unit,
)

/** 槽位分发：按动作决定手感（连发/长按宏/粘性）与回调。 */
@Composable
private fun RowScope.ToolbarSlot(
    config: ToolbarKeyConfig,
    theme: TerminalTheme,
    h: ToolbarHandlers,
) {
    val accent = config.accentColor(theme)
    val label = config.displayLabel
    val action = config.action
    val actionIcon = action.icon
    val actionIconSize = action.iconSize
    when (action) {
        ToolbarAction.EMPTY -> EmptyKeySlot()
        ToolbarAction.EXPAND ->
            KeyButton(
                "",
                h.expanded,
                theme,
                accent = accent,
                icon = if (h.expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                iconSize = KeyToolbarTokens.ArrowIconSize,
            ) { h.onExpandedChange(!h.expanded) }
        ToolbarAction.CTRL -> KeyButton(label, h.ctrlActive, theme, accent = accent) { h.onToggleCtrl() }
        ToolbarAction.ALT -> KeyButton(label, h.altActive, theme, accent = accent) { h.onToggleAlt() }
        ToolbarAction.KEYBOARD ->
            KeyButton(label, false, theme, accent = accent, icon = actionIcon, iconSize = actionIconSize) {
                h.onToggleKeyboard()
            }
        ToolbarAction.SNIPPETS -> KeyButton(label, false, theme, accent = accent) { h.onSnippets() }
        ToolbarAction.GIT ->
            KeyButton(label, false, theme, accent = accent, icon = actionIcon, iconSize = actionIconSize) {
                h.onGit()
            }
        ToolbarAction.PASTE -> KeyButton(label, false, theme, accent = accent) { h.onPaste() }
        ToolbarAction.SLASH -> KeyButton(label, false, theme, accent = accent) { h.onChar("/") }
        ToolbarAction.CUSTOM -> {
            val bytes = remember(config.payload) { parseKeyPayload(config.payload).getOrNull() }
            KeyButton(label, false, theme, accent = accent) {
                if (bytes != null) {
                    h.onCustom(bytes)
                } else {
                    // 编辑页已校验，走到这里说明持久化数据被外部改坏
                    TermLog.w("toolbar") { "invalid custom key payload, label=$label" }
                }
            }
        }
        else -> {
            val key = action.specialKey
            if (key == null) {
                EmptyKeySlot()
            } else {
                val longPress: (() -> Unit)? =
                    when (action) {
                        ToolbarAction.ESC -> {
                            {
                                h.onKey(SpecialKey.ESC)
                                h.onKey(SpecialKey.ESC)
                            }
                        }
                        ToolbarAction.TAB -> {
                            { h.onKey(SpecialKey.SHIFT_TAB) }
                        }
                        ToolbarAction.CTRL_C, ToolbarAction.CTRL_D -> {
                            { h.onKey(SpecialKey.CTRL_BACKSLASH) }
                        }
                        else -> null
                    }
                KeyButton(
                    label,
                    false,
                    theme,
                    accent = accent,
                    icon = actionIcon,
                    iconSize = actionIconSize,
                    repeat = action.repeats,
                    onLongPress = longPress,
                ) { h.onKey(key) }
            }
        }
    }
}

/**
 * 设置页预览槽位：与工具栏同款外观，但无连发/长按，点击仅用于选中。
 * 空槽位显示「+」，▾ 固定槽位不可点。
 */
@Composable
internal fun RowScope.ToolbarPreviewSlot(
    config: ToolbarKeyConfig,
    theme: TerminalTheme,
    selected: Boolean,
    highlight: Color,
    onClick: () -> Unit,
) {
    val border = if (selected) highlight else null
    val action = config.action
    when (action) {
        ToolbarAction.EMPTY ->
            KeyButton(
                "+",
                false,
                theme,
                highlight = border,
                dimmed = true,
                onTap = onClick,
            )
        ToolbarAction.EXPAND ->
            KeyButton(
                "",
                false,
                theme,
                accent = config.accentColor(theme),
                icon = Icons.Filled.KeyboardArrowDown,
                iconSize = KeyToolbarTokens.ArrowIconSize,
                dimmed = true,
            )
        else ->
            KeyButton(
                config.displayLabel,
                false,
                theme,
                accent = config.accentColor(theme),
                icon = action.icon,
                iconSize = action.iconSize,
                highlight = border,
                onTap = onClick,
            )
    }
}

/** 空槽位：与键同尺寸的透明占位，保持列对齐。 */
@Composable
private fun RowScope.EmptyKeySlot() {
    Box(
        Modifier
            .weight(1f)
            .widthIn(max = KeyToolbarTokens.KeyMaxWidth)
            .height(KeyToolbarTokens.KeyHeight),
    )
}

/**
 * 现代紧凑键帽 KeyButton：
 * - 微凸深色键帽底色 + 柔和低饱和分类着色 + 细腻描边 + 按下即时高亮反馈
 * - 固定紧凑高度（[KeyToolbarTokens.KeyHeight]），兼顾触控手感与终端画布可视面积
 * - 方向键/键盘/Git 使用矢量图标，文字标签统一用等宽 SemiBold 字体
 */
@Composable
private fun RowScope.KeyButton(
    label: String,
    active: Boolean,
    theme: TerminalTheme,
    repeat: Boolean = false,
    icon: ImageVector? = null,
    iconSize: Dp = Sizes.IconMedium,
    accent: Color? = null,
    highlight: Color? = null,
    dimmed: Boolean = false,
    // 注意参数顺序：onTap 必须是最后一个参数（调用处大量使用尾随 lambda），
    // 误放 onLongPress 在末尾会让尾随 lambda 绑到长按回调——单击失灵、方向键全废
    onLongPress: (() -> Unit)? = null,
    onTap: () -> Unit = {},
) {
    val currentOnTap by rememberUpdatedState(onTap)
    var pressing by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val clickablePressed by interactionSource.collectIsPressedAsState()
    val isPressed = pressing || clickablePressed

    if (repeat) {
        LaunchedEffect(pressing) {
            if (!pressing) return@LaunchedEffect
            delay(REPEAT_INITIAL_DELAY_MS)
            while (isActive && pressing) {
                currentOnTap()
                delay(REPEAT_INTERVAL_MS)
            }
        }
        DisposableEffect(Unit) {
            onDispose { pressing = false }
        }
    }

    val themeBg = theme.background()
    val themeFg = theme.foreground()
    val neutralCap = lerp(themeBg, themeFg, KeyToolbarTokens.KEYCAP_SURFACE_LIFT)
    val baseCap =
        if (accent != null) {
            lerp(neutralCap, accent, KeyToolbarTokens.ACCENT_SURFACE_BLEND)
        } else {
            neutralCap
        }
    val bg =
        when {
            active -> lerp(accent ?: theme.cursor(), themeBg, KeyToolbarTokens.ACTIVE_SURFACE_BLEND)
            dimmed -> Color.Transparent
            isPressed -> lerp(baseCap, accent ?: themeFg, KeyToolbarTokens.PRESSED_SURFACE_BLEND)
            else -> baseCap
        }
    val tonedAccent =
        if (accent != null) {
            lerp(accent, themeFg, KeyToolbarTokens.ACCENT_TEXT_FOREGROUND_BLEND)
        } else {
            themeFg
        }
    val fg =
        when {
            active -> themeBg
            dimmed -> tonedAccent.copy(alpha = KeyToolbarTokens.DIMMED_CONTENT_ALPHA)
            else -> tonedAccent
        }
    val borderTint =
        if (accent != null) {
            lerp(themeFg, accent, KeyToolbarTokens.ACCENT_BORDER_BLEND)
        } else {
            themeFg
        }
    val borderColor =
        when {
            highlight != null -> highlight
            active -> (accent ?: theme.cursor())
            dimmed -> borderTint.copy(alpha = KeyToolbarTokens.EMPTY_SLOT_BORDER_ALPHA)
            isPressed -> borderTint.copy(alpha = KeyToolbarTokens.PRESSED_BORDER_ALPHA)
            accent != null -> borderTint.copy(alpha = KeyToolbarTokens.ACCENT_BORDER_ALPHA)
            else -> themeFg.copy(alpha = KeyToolbarTokens.NEUTRAL_BORDER_ALPHA)
        }
    val borderWidth = if (highlight != null) KeyToolbarTokens.SelectedBorder else Sizes.BorderThin
    val shape = RoundedCornerShape(KeyToolbarTokens.KeyCorner)

    Box(
        Modifier
            .weight(1f)
            .widthIn(max = KeyToolbarTokens.KeyMaxWidth)
            .height(KeyToolbarTokens.KeyHeight)
            .clip(shape)
            .background(bg)
            .border(borderWidth, borderColor, shape)
            .then(
                if (repeat) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            pressing = true
                            currentOnTap()
                            try {
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val released = ev.changes.all { !it.pressed }
                                    val consumed = ev.changes.any { it.isConsumed }
                                    if (released || consumed) break
                                }
                            } catch (_: CancellationException) {
                            } finally {
                                pressing = false
                            }
                        }
                    }
                } else {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onTap,
                        onLongClick = onLongPress,
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(iconSize))
        } else {
            val fontSize =
                when {
                    label.length == 1 -> KeyToolbarTokens.SymbolFontSize
                    label.length >= 4 -> KeyToolbarTokens.CompactLabelFontSize
                    else -> KeyToolbarTokens.LabelFontSize
                }
            val letterSpacing =
                if (label.length >= 4) {
                    KeyToolbarTokens.CompactLetterSpacing
                } else {
                    KeyToolbarTokens.NormalLetterSpacing
                }
            Text(
                label,
                color = fg,
                fontFamily = monospaceFontFamily(),
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
                letterSpacing = letterSpacing,
                maxLines = 1,
            )
        }
    }
}
