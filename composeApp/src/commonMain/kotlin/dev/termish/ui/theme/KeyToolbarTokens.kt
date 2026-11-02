package dev.termish.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 终端快捷键栏视觉 token（现代紧凑键帽风格，分类着色与自定义编辑页共用）。 */
object KeyToolbarTokens {
    /** 未着色键帽相对终端背景的微凸提亮比例。 */
    const val KEYCAP_SURFACE_LIFT = 0.065f

    /** 分类着色键帽在基础键帽底色上混入强调色的比例（柔和低饱和浅底）。 */
    const val ACCENT_SURFACE_BLEND = 0.14f

    /** 按下瞬间键帽底色混入强调色/前景色的比例（按压高亮反馈）。 */
    const val PRESSED_SURFACE_BLEND = 0.28f

    /** 激活态（CTRL/ALT 粘性、▾ 展开）实心填充与背景微调比例。 */
    const val ACTIVE_SURFACE_BLEND = 0.08f

    /** 分类文字在强调色中混入前景色的比例，提升跨深浅主题的对比度与可读性。 */
    const val ACCENT_TEXT_FOREGROUND_BLEND = 0.22f

    /** 分类描边在强调色与前景色之间的混合比例。 */
    const val ACCENT_BORDER_BLEND = 0.65f

    /** 着色键帽细腻描边透明度（告别刺眼霓虹粗框）。 */
    const val ACCENT_BORDER_ALPHA = 0.28f

    /** 按下状态描边透明度。 */
    const val PRESSED_BORDER_ALPHA = 0.55f

    /** 中性键帽（未着色）描边透明度。 */
    const val NEUTRAL_BORDER_ALPHA = 0.18f

    /** 空槽位在编辑页预览中的边框透明度。 */
    const val EMPTY_SLOT_BORDER_ALPHA = 0.18f

    /** 编辑页弱化文字/图标透明度。 */
    const val DIMMED_CONTENT_ALPHA = 0.42f

    /** 次要说明文字透明度。 */
    const val SUBTITLE_ALPHA = 0.6f

    /** 卡片浅描边透明度。 */
    const val CARD_BORDER_ALPHA = 0.12f

    /** 紧凑键帽固定高度（比 1:1 大方块精致，为终端画布留出更多可视高度）。 */
    val KeyHeight = 36.dp

    /** 键宽上限：宽屏/横屏下保持紧凑键帽比例居中排列，不拉成扁长条。 */
    val KeyMaxWidth = 56.dp

    /** 键帽圆角（现代圆角矩形键帽）。 */
    val KeyCorner = 7.dp

    /** 键行间垂直间距。 */
    val RowGap = 4.dp

    /** 键行内水平间距。 */
    val KeyGap = 5.dp

    /** 工具栏水平外边距。 */
    val ToolbarHorizontalPadding = 6.dp

    /** 工具栏顶部内边距。 */
    val ToolbarTopPadding = 3.dp

    /** 工具栏常驻块底部内边距。 */
    val ToolbarBottomPadding = 8.dp

    /** 展开箭头图标尺寸。 */
    val ArrowIconSize = 20.dp

    /** 方向键与面板入口图标尺寸（带杆方向箭头 / 键盘 / Git）。 */
    val ActionIconSize = 18.dp

    /** 编辑页选中槽位的高亮边框宽度。 */
    val SelectedBorder = 2.dp

    /** 拖拽中键帽的悬浮阴影。 */
    val DragElevation = 8.dp

    /** 颜色选择圆点尺寸。 */
    val ColorSwatchSize = 14.dp

    /** 编辑弹窗可滚动内容最大高度。 */
    val DialogContentMaxHeight = 420.dp

    /** 1–3 字符键帽字号（ESC / TAB / ⌃C / F1 等）。 */
    val LabelFontSize = 11.5.sp

    /** 4 字符紧凑键帽字号（CTRL / HOME / PGUP / PGDN）。 */
    val CompactLabelFontSize = 10.sp

    /** 单字符符号键帽字号（/ 等）。 */
    val SymbolFontSize = 14.sp

    /** 4 字符键帽紧凑字距。 */
    val CompactLetterSpacing = (-0.3).sp

    /** 标准键帽字距。 */
    val NormalLetterSpacing = 0.sp

    /** 自定义键标签最大字符数（紧凑键帽内单行可容纳）。 */
    const val CUSTOM_LABEL_MAX_CHARS = 4
}
