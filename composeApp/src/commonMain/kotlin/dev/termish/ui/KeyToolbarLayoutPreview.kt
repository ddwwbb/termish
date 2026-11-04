package dev.termish.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import dev.termish.data.KeyToolbarLayout
import dev.termish.data.ToolbarAction
import dev.termish.data.ToolbarPos
import dev.termish.ui.theme.Corners
import dev.termish.ui.theme.KeyToolbarTokens
import dev.termish.ui.theme.Sizes
import dev.termish.ui.theme.Spacing
import dev.termish.ui.theme.TerminalTheme
import kotlin.math.roundToInt

/** 真实行序的拖拽预览；只在有效槽位松手后交换并保存，取消或拖出网格不修改布局。 */
@Composable
internal fun KeyToolbarLayoutPreview(
    layout: KeyToolbarLayout,
    selectedPos: ToolbarPos,
    theme: TerminalTheme,
    frostedGlass: Boolean = true,
    onSelect: (ToolbarPos) -> Unit,
    onSwap: (ToolbarPos, ToolbarPos) -> Unit,
) {
    val kts = LocalAppStrings.current.keyToolbar
    val highlight = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val currentLayout by rememberUpdatedState(layout)
    val select by rememberUpdatedState(onSelect)
    val swap by rememberUpdatedState(onSwap)
    val bounds = remember { mutableStateMapOf<ToolbarPos, Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var draggedPos by remember { mutableStateOf<ToolbarPos?>(null) }
    var targetPos by remember { mutableStateOf<ToolbarPos?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    fun slotAt(point: Offset): ToolbarPos? = bounds.entries.firstOrNull { (pos, rect) -> KeyToolbarLayout.isEditable(pos) && rect.contains(point) }?.key

    fun finishDrag() {
        draggedPos = null
        targetPos = null
        dragOffset = Offset.Zero
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corners.Md))
            .background(theme.background())
            .border(Sizes.BorderThin, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Corners.Md))
            .onGloballyPositioned { origin = it.positionInRoot() }
            .testTag("toolbar-preview")
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { point ->
                        val pos = slotAt(origin + point)
                        if (pos != null && currentLayout[pos].action != ToolbarAction.EMPTY) {
                            draggedPos = pos
                            targetPos = pos
                            dragOffset = Offset.Zero
                            select(pos)
                        }
                    },
                    onDrag = { change, amount ->
                        if (draggedPos != null) {
                            change.consume()
                            dragOffset += amount
                            targetPos = slotAt(origin + change.position)
                        }
                    },
                    onDragEnd = {
                        val from = draggedPos
                        val to = targetPos
                        if (from != null && to != null && from != to) swap(from, to)
                        finishDrag()
                    },
                    onDragCancel = { finishDrag() },
                )
            },
    ) {
        Column(Modifier.padding(Spacing.Md)) {
            listOf(2, 3, 0, 1).forEachIndexed { index, row ->
                if (index == 0 || index == 2) {
                    if (index == 2) Spacer(Modifier.height(Spacing.Md))
                    Text(
                        if (index == 0) kts.sectionExpanded else kts.sectionBase,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.foreground().copy(alpha = KeyToolbarTokens.SUBTITLE_ALPHA),
                        modifier = Modifier.padding(bottom = Spacing.Xs),
                    )
                } else {
                    Spacer(Modifier.height(KeyToolbarTokens.RowGap))
                }
                KeyRow {
                    layout.rows[row].forEachIndexed { col, config ->
                        val pos = ToolbarPos(row, col)
                        Box(
                            Modifier
                                .weight(1f)
                                .widthIn(max = KeyToolbarTokens.KeyMaxWidth)
                                .onGloballyPositioned { bounds[pos] = it.boundsInRoot() }
                                .testTag("toolbar-slot-$row-$col")
                                .alpha(if (pos == draggedPos) KeyToolbarTokens.DIMMED_CONTENT_ALPHA else 1f),
                        ) {
                            Row {
                                ToolbarPreviewSlot(
                                    config = config,
                                    theme = theme,
                                    selected = if (draggedPos == null) pos == selectedPos else pos == targetPos,
                                    highlight = highlight,
                                    frostedGlass = frostedGlass,
                                    onClick = { if (KeyToolbarLayout.isEditable(pos)) onSelect(pos) },
                                )
                            }
                        }
                    }
                }
            }
        }

        val from = draggedPos
        val rect = from?.let { bounds[it] }
        if (from != null && rect != null) {
            val topLeft = rect.topLeft - origin + dragOffset
            Row(
                Modifier
                    .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                    .width(with(density) { rect.width.toDp() })
                    .shadow(KeyToolbarTokens.DragElevation, RoundedCornerShape(KeyToolbarTokens.KeyCorner))
                    .testTag("toolbar-dragged-key"),
            ) {
                ToolbarPreviewSlot(
                    config = layout[from],
                    theme = theme,
                    selected = true,
                    highlight = highlight,
                    frostedGlass = frostedGlass,
                    onClick = {},
                )
            }
        }
    }
}
