package dev.termish.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.termish.ui.theme.Corners
import dev.termish.ui.theme.Sizes
import dev.termish.ui.theme.Spacing

/**
 * 终端选择模式浮动操作条：长按/双击进入选择模式后悬浮在画布顶部。
 *
 * 动作全部由调用方实现（复制/粘贴/全选/取消）；条本身无状态。
 * 背景用 Surface elevation 容器色（非终端主题色）：选区高亮是半透明色块，
 * 操作条需要与画布内容明确分层，避免看不清。
 */
@Composable
internal fun SelectionActionBar(
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppStrings.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Corners.Md),
        shadowElevation = Spacing.Xs,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.Xs, vertical = Spacing.Xs / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = s.terminalCopy,
                    modifier = Modifier.size(Sizes.IconMedium),
                )
            }
            IconButton(onClick = onPaste) {
                Icon(
                    Icons.Filled.ContentPaste,
                    contentDescription = s.terminalPaste,
                    modifier = Modifier.size(Sizes.IconMedium),
                )
            }
            IconButton(onClick = onSelectAll) {
                Icon(
                    Icons.Filled.SelectAll,
                    contentDescription = s.terminalSelectAll,
                    modifier = Modifier.size(Sizes.IconMedium),
                )
            }
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = s.terminalCancel,
                    modifier = Modifier.size(Sizes.IconMedium),
                )
            }
        }
    }
}
