package dev.termish.data

import kotlinx.serialization.Serializable

/**
 * 终端快捷键栏的可编辑键位动作。
 *
 * [EMPTY] 为空槽位（渲染为同尺寸占位，保持列对齐）；[CUSTOM] 为用户自定义键
 * （标签 + 发送内容）；[EXPAND] 为 ▾ 展开键，固定在 [KeyToolbarLayout.FIXED]，
 * 不可编辑也不出现在编辑页的候选里。其余为内置键。
 */
@Serializable
enum class ToolbarAction {
    EMPTY,
    CUSTOM,
    EXPAND,

    // 编辑
    ESC,
    TAB,
    SHIFT_TAB,
    ENTER,
    DEL,
    SLASH,
    CTRL_L,
    CTRL_E,
    CTRL_R,

    // 修饰键
    CTRL,
    ALT,

    // 导航
    UP,
    DOWN,
    LEFT,
    RIGHT,
    HOME,
    END,
    PGUP,
    PGDN,

    // 控制信号
    CTRL_C,
    CTRL_D,
    CTRL_Z,
    CTRL_BACKSLASH,

    // 功能键
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    F10,
    F11,
    F12,

    // 面板入口
    PASTE,
    SNIPPETS,
    KEYBOARD,
    GIT,
}

/**
 * 键颜色：[AUTO] 按功能分类取色；[NEUTRAL] 不着色（前景色）；
 * 其余映射到当前终端主题的 ANSI 普通色 1–6（随主题适配深浅色）。
 */
@Serializable
enum class ToolbarKeyColor { AUTO, NEUTRAL, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN }

/** 单个槽位配置。[label] / [payload] 仅 [ToolbarAction.CUSTOM] 使用；payload 为转义记法原文。 */
@Serializable
data class ToolbarKeyConfig(
    val action: ToolbarAction,
    val label: String = "",
    val payload: String = "",
    val color: ToolbarKeyColor = ToolbarKeyColor.AUTO,
)

/** 槽位坐标（逻辑行号：0/1 常驻行，2/3 展开行）。 */
data class ToolbarPos(
    val row: Int,
    val col: Int,
)

/**
 * 快捷键栏布局：固定 [ROWS] × [COLS] 槽位。行 0/1 为常驻行（行 0 在上），
 * 行 2/3 为 ▾ 展开行（渲染在常驻行上方，覆盖画布）。
 *
 * 持久化在 [AppSettings.keyToolbarLayout]；null 表示使用 [DEFAULT]，
 * 默认布局后续调整时未自定义的用户自动跟随。读取后一律经 [normalized] 修正。
 */
@Serializable
data class KeyToolbarLayout(
    val rows: List<List<ToolbarKeyConfig>>,
) {
    /** 补齐/截断为 4×8，强制固定槽位为 ▾，其他槽位不得出现 ▾。 */
    fun normalized(): KeyToolbarLayout {
        val fixed =
            List(ROWS) { r ->
                val row = rows.getOrNull(r).orEmpty()
                List(COLS) { c ->
                    val pos = ToolbarPos(r, c)
                    val key = row.getOrNull(c) ?: EMPTY_KEY
                    when {
                        pos == FIXED -> EXPAND_KEY
                        key.action == ToolbarAction.EXPAND -> EMPTY_KEY
                        else -> key
                    }
                }
            }
        return KeyToolbarLayout(fixed)
    }

    operator fun get(pos: ToolbarPos): ToolbarKeyConfig = rows[pos.row][pos.col]

    /** 替换某槽位；固定槽位或越界时原样返回。 */
    fun update(
        pos: ToolbarPos,
        key: ToolbarKeyConfig,
    ): KeyToolbarLayout {
        if (!isEditable(pos) || key.action == ToolbarAction.EXPAND) return this
        return KeyToolbarLayout(
            rows.mapIndexed { r, row ->
                if (r != pos.row) row else row.mapIndexed { c, k -> if (c == pos.col) key else k }
            },
        )
    }

    /** 交换两个槽位；任一为固定槽位或越界时原样返回。 */
    fun swap(
        a: ToolbarPos,
        b: ToolbarPos,
    ): KeyToolbarLayout {
        if (!isEditable(a) || !isEditable(b) || a == b) return this
        val ka = this[a]
        val kb = this[b]
        return KeyToolbarLayout(
            rows.mapIndexed { r, row ->
                row.mapIndexed { c, k ->
                    when (ToolbarPos(r, c)) {
                        a -> kb
                        b -> ka
                        else -> k
                    }
                }
            },
        )
    }

    companion object {
        const val ROWS = 4
        const val COLS = 8

        /** ▾ 展开键所在槽位（行 1 右上角拇指区）。 */
        val FIXED = ToolbarPos(0, COLS - 1)

        private val EMPTY_KEY = ToolbarKeyConfig(ToolbarAction.EMPTY)
        private val EXPAND_KEY = ToolbarKeyConfig(ToolbarAction.EXPAND)

        fun isEditable(pos: ToolbarPos): Boolean = pos.row in 0 until ROWS && pos.col in 0 until COLS && pos != FIXED

        private fun row(vararg actions: ToolbarAction) = actions.map { ToolbarKeyConfig(it) }

        /**
         * 默认布局（与引入自定义前的硬编码布局逐格一致）：
         * - 行 1：ESC TAB ⌃C ⌃L {} ↑ ⌨ ▾（↑ 第 6 列与行 2 的 ↓ 上下对齐）
         * - 行 2：CTRL ALT ⌃D / ← ↓ → ENT
         * - 行 3：PST ⇧⇥ DEL HOME END PGUP PGDN ⌃\
         * - 行 4：F1–F7 ⎇
         */
        val DEFAULT =
            KeyToolbarLayout(
                listOf(
                    row(
                        ToolbarAction.ESC,
                        ToolbarAction.TAB,
                        ToolbarAction.CTRL_C,
                        ToolbarAction.CTRL_L,
                        ToolbarAction.SNIPPETS,
                        ToolbarAction.UP,
                        ToolbarAction.KEYBOARD,
                        ToolbarAction.EXPAND,
                    ),
                    row(
                        ToolbarAction.CTRL,
                        ToolbarAction.ALT,
                        ToolbarAction.CTRL_D,
                        ToolbarAction.SLASH,
                        ToolbarAction.LEFT,
                        ToolbarAction.DOWN,
                        ToolbarAction.RIGHT,
                        ToolbarAction.ENTER,
                    ),
                    row(
                        ToolbarAction.PASTE,
                        ToolbarAction.SHIFT_TAB,
                        ToolbarAction.DEL,
                        ToolbarAction.HOME,
                        ToolbarAction.END,
                        ToolbarAction.PGUP,
                        ToolbarAction.PGDN,
                        ToolbarAction.CTRL_BACKSLASH,
                    ),
                    row(
                        ToolbarAction.F1,
                        ToolbarAction.F2,
                        ToolbarAction.F3,
                        ToolbarAction.F4,
                        ToolbarAction.F5,
                        ToolbarAction.F6,
                        ToolbarAction.F7,
                        ToolbarAction.GIT,
                    ),
                ),
            )
    }
}
