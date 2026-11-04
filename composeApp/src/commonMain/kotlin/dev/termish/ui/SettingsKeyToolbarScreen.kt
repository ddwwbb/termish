package dev.termish.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.termish.data.KeyToolbarLayout
import dev.termish.data.ToolbarAction
import dev.termish.data.ToolbarKeyColor
import dev.termish.data.ToolbarKeyConfig
import dev.termish.data.ToolbarPos
import dev.termish.ui.theme.Corners
import dev.termish.ui.theme.KeyToolbarTokens
import dev.termish.ui.theme.Sizes
import dev.termish.ui.theme.Spacing
import dev.termish.ui.theme.StatusColors
import dev.termish.ui.theme.TerminalTheme
import dev.termish.util.monospaceFontFamily

/** 按功能分类组织的内置候选键（不含 EMPTY / CUSTOM / EXPAND）。 */
internal val BUILTIN_ACTIONS_BY_CATEGORY: List<Pair<KeyCategory, List<ToolbarAction>>> =
    listOf(
        KeyCategory.SIGNAL to
            listOf(
                ToolbarAction.CTRL_C,
                ToolbarAction.CTRL_D,
                ToolbarAction.CTRL_BACKSLASH,
                ToolbarAction.CTRL_Z,
            ),
        KeyCategory.EDIT to
            listOf(
                ToolbarAction.ESC,
                ToolbarAction.TAB,
                ToolbarAction.SHIFT_TAB,
                ToolbarAction.ENTER,
                ToolbarAction.DEL,
                ToolbarAction.SLASH,
                ToolbarAction.CTRL_L,
                ToolbarAction.CTRL_E,
                ToolbarAction.CTRL_R,
            ),
        KeyCategory.NAVIGATION to
            listOf(
                ToolbarAction.UP,
                ToolbarAction.DOWN,
                ToolbarAction.LEFT,
                ToolbarAction.RIGHT,
                ToolbarAction.HOME,
                ToolbarAction.END,
                ToolbarAction.PGUP,
                ToolbarAction.PGDN,
            ),
        KeyCategory.MODIFIER to
            listOf(
                ToolbarAction.CTRL,
                ToolbarAction.ALT,
            ),
        KeyCategory.FUNCTION to
            listOf(
                ToolbarAction.F1,
                ToolbarAction.F2,
                ToolbarAction.F3,
                ToolbarAction.F4,
                ToolbarAction.F5,
                ToolbarAction.F6,
                ToolbarAction.F7,
                ToolbarAction.F8,
                ToolbarAction.F9,
                ToolbarAction.F10,
                ToolbarAction.F11,
                ToolbarAction.F12,
            ),
        KeyCategory.PANEL to
            listOf(
                ToolbarAction.KEYBOARD,
                ToolbarAction.SNIPPETS,
                ToolbarAction.PASTE,
                ToolbarAction.GIT,
            ),
    )

/**
 * 终端快捷按钮自定义二级页：
 * 真实终端配色预览 4×8 网格（展开 2 行 + 常驻 2 行），点选槽位后支持编辑、
 * 长按拖拽交换位置、清空与一键恢复默认。
 */
@Composable
fun SettingsKeyToolbarScreen(
    layout: KeyToolbarLayout?,
    theme: TerminalTheme,
    frostedGlass: Boolean = true,
    onChangeFrostedGlass: (Boolean) -> Unit = {},
    onChangeLayout: (KeyToolbarLayout?) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalAppStrings.current
    val kts = s.keyToolbar
    val current = remember(layout) { (layout ?: KeyToolbarLayout.DEFAULT).normalized() }
    var selectedPos by remember { mutableStateOf(ToolbarPos(0, 0)) }
    var editing by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    fun commit(next: KeyToolbarLayout) {
        val normalized = next.normalized()
        onChangeLayout(if (normalized == KeyToolbarLayout.DEFAULT) null else normalized)
    }

    val hasKeyboardKey =
        remember(current) {
            current.rows.any { row -> row.any { it.action == ToolbarAction.KEYBOARD } }
        }
    val selectedKey = current[selectedPos]

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 页头：返回 + 标题 + 恢复默认
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.Xs, vertical = Spacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = s.navBack)
            }
            Text(
                kts.settingsTitle,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = monospaceFontFamily(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { confirmReset = true },
                enabled = layout != null,
            ) {
                Text(kts.reset)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.Lg),
        ) {
            Text(
                kts.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = KeyToolbarTokens.SUBTITLE_ALPHA),
                modifier = Modifier.padding(top = Spacing.Sm, bottom = Spacing.Md),
            )

            KeyToolbarLayoutPreview(
                layout = current,
                selectedPos = selectedPos,
                theme = theme,
                frostedGlass = frostedGlass,
                onSelect = { selectedPos = it },
                onSwap = { from, to ->
                    commit(current.swap(from, to))
                    selectedPos = to
                },
            )

            if (!hasKeyboardKey) {
                Spacer(Modifier.height(Spacing.Sm))
                Text(
                    kts.keyboardMissingWarning,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusColors.Warning,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Corners.Sm))
                            .border(
                                Sizes.BorderThin,
                                StatusColors.Warning.copy(alpha = KeyToolbarTokens.ACCENT_BORDER_ALPHA),
                                RoundedCornerShape(Corners.Sm),
                            ).padding(Spacing.Md),
                )
            }

            Spacer(Modifier.height(Spacing.Lg))

            // 选中槽位操作区：编辑 / 清空
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Corners.Md),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = Spacing.None),
                border =
                    BorderStroke(
                        Sizes.BorderThin,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = KeyToolbarTokens.CARD_BORDER_ALPHA),
                    ),
            ) {
                Column(Modifier.padding(Spacing.Md), verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                    ) {
                        val slotTitle =
                            when (selectedKey.action) {
                                ToolbarAction.EMPTY -> kts.typeEmpty
                                ToolbarAction.CUSTOM -> "${selectedKey.label} (${selectedKey.payload})"
                                else -> selectedKey.displayLabel
                            }
                        Text(
                            slotTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(onClick = { editing = true }) {
                            Text(kts.actionEdit)
                        }
                        OutlinedButton(
                            onClick = { commit(current.update(selectedPos, ToolbarKeyConfig(ToolbarAction.EMPTY))) },
                            enabled = selectedKey.action != ToolbarAction.EMPTY,
                        ) {
                            Text(kts.actionClear)
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.Md))

            // 毛玻璃效果开关
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Corners.Md),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = Spacing.None),
                border =
                    BorderStroke(
                        Sizes.BorderThin,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = KeyToolbarTokens.CARD_BORDER_ALPHA),
                    ),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Lg, vertical = Spacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Md),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                        Text(
                            kts.frostedGlassTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            kts.frostedGlassSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = KeyToolbarTokens.SUBTITLE_ALPHA),
                        )
                    }
                    Switch(
                        checked = frostedGlass,
                        onCheckedChange = onChangeFrostedGlass,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.Xl))
        }
    }

    if (editing) {
        EditToolbarSlotDialog(
            initial = selectedKey,
            theme = theme,
            onDismiss = { editing = false },
            onSave = { updated ->
                commit(current.update(selectedPos, updated))
                editing = false
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(kts.resetConfirmTitle) },
            text = { Text(kts.resetConfirmBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChangeLayout(null)
                        confirmReset = false
                    },
                ) { Text(kts.reset) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(kts.cancel) }
            },
        )
    }
}

private enum class SlotEditMode { BUILTIN, CUSTOM, EMPTY }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditToolbarSlotDialog(
    initial: ToolbarKeyConfig,
    theme: TerminalTheme,
    onDismiss: () -> Unit,
    onSave: (ToolbarKeyConfig) -> Unit,
) {
    val kts = LocalAppStrings.current.keyToolbar
    var mode by remember {
        mutableStateOf(
            when (initial.action) {
                ToolbarAction.EMPTY -> SlotEditMode.EMPTY
                ToolbarAction.CUSTOM -> SlotEditMode.CUSTOM
                else -> SlotEditMode.BUILTIN
            },
        )
    }
    var selectedBuiltIn by remember {
        mutableStateOf(
            if (initial.action != ToolbarAction.EMPTY &&
                initial.action != ToolbarAction.CUSTOM &&
                initial.action != ToolbarAction.EXPAND
            ) {
                initial.action
            } else {
                ToolbarAction.ESC
            },
        )
    }
    var customLabel by remember { mutableStateOf(initial.label) }
    var customPayload by remember { mutableStateOf(initial.payload) }
    var selectedColor by remember { mutableStateOf(initial.color) }

    val payloadResult = remember(customPayload) { parseKeyPayload(customPayload) }
    val payloadErrorIndex = (payloadResult.exceptionOrNull() as? KeyPayloadException)?.index
    val canSave =
        when (mode) {
            SlotEditMode.EMPTY -> true
            SlotEditMode.BUILTIN -> true
            SlotEditMode.CUSTOM ->
                customLabel.trim().isNotEmpty() &&
                    customPayload.isNotEmpty() &&
                    payloadResult.isSuccess
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(kts.editDialogTitle) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = KeyToolbarTokens.DialogContentMaxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
            ) {
                Text(
                    kts.keyTypeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = KeyToolbarTokens.SUBTITLE_ALPHA),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                    FilterChip(
                        selected = mode == SlotEditMode.BUILTIN,
                        onClick = { mode = SlotEditMode.BUILTIN },
                        label = { Text(kts.typeBuiltIn) },
                    )
                    FilterChip(
                        selected = mode == SlotEditMode.CUSTOM,
                        onClick = { mode = SlotEditMode.CUSTOM },
                        label = { Text(kts.typeCustom) },
                    )
                    FilterChip(
                        selected = mode == SlotEditMode.EMPTY,
                        onClick = { mode = SlotEditMode.EMPTY },
                        label = { Text(kts.typeEmpty) },
                    )
                }

                when (mode) {
                    SlotEditMode.BUILTIN -> {
                        BUILTIN_ACTIONS_BY_CATEGORY.forEach { (category, actions) ->
                            Text(
                                categoryTitle(category, kts),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = KeyToolbarTokens.SUBTITLE_ALPHA),
                                modifier = Modifier.padding(top = Spacing.Xs),
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
                                verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
                            ) {
                                actions.forEach { action ->
                                    FilterChip(
                                        selected = selectedBuiltIn == action,
                                        onClick = { selectedBuiltIn = action },
                                        label = {
                                            Text(
                                                action.label,
                                                fontFamily = FontFamily.Monospace,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                    SlotEditMode.CUSTOM -> {
                        OutlinedTextField(
                            value = customLabel,
                            onValueChange = {
                                if (it.length <= KeyToolbarTokens.CUSTOM_LABEL_MAX_CHARS) {
                                    customLabel = it
                                }
                            },
                            label = { Text(kts.customLabelField) },
                            placeholder = { Text(kts.customLabelPlaceholder) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = customPayload,
                            onValueChange = { customPayload = it },
                            label = { Text(kts.customPayloadField) },
                            placeholder = { Text(kts.customPayloadPlaceholder) },
                            isError = customPayload.isNotEmpty() && payloadErrorIndex != null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (customPayload.isNotEmpty() && payloadErrorIndex != null) {
                            Text(
                                kts.customPayloadError(payloadErrorIndex),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            kts.customPayloadHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = KeyToolbarTokens.SUBTITLE_ALPHA),
                        )
                    }
                    SlotEditMode.EMPTY -> Unit
                }

                if (mode != SlotEditMode.EMPTY) {
                    Text(
                        kts.colorLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = KeyToolbarTokens.SUBTITLE_ALPHA),
                        modifier = Modifier.padding(top = Spacing.Xs),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
                    ) {
                        ToolbarKeyColor.entries.forEach { colorOption ->
                            val swatch = colorOption.toAnsi(theme) ?: theme.foreground()
                            FilterChip(
                                selected = selectedColor == colorOption,
                                onClick = { selectedColor = colorOption },
                                leadingIcon = {
                                    Box(
                                        Modifier
                                            .size(KeyToolbarTokens.ColorSwatchSize)
                                            .clip(CircleShape)
                                            .background(swatch),
                                    )
                                },
                                label = { Text(colorLabel(colorOption, kts)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val result =
                        when (mode) {
                            SlotEditMode.EMPTY -> ToolbarKeyConfig(ToolbarAction.EMPTY)
                            SlotEditMode.BUILTIN ->
                                ToolbarKeyConfig(
                                    action = selectedBuiltIn,
                                    color = selectedColor,
                                )
                            SlotEditMode.CUSTOM ->
                                ToolbarKeyConfig(
                                    action = ToolbarAction.CUSTOM,
                                    label = customLabel.trim(),
                                    payload = customPayload,
                                    color = selectedColor,
                                )
                        }
                    onSave(result)
                },
            ) {
                Text(kts.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(kts.cancel)
            }
        },
    )
}

private fun categoryTitle(
    category: KeyCategory,
    kts: KeyToolbarStrings,
): String =
    when (category) {
        KeyCategory.SIGNAL -> kts.categorySignal
        KeyCategory.EDIT -> kts.categoryEdit
        KeyCategory.NAVIGATION -> kts.categoryNavigation
        KeyCategory.MODIFIER -> kts.categoryModifier
        KeyCategory.FUNCTION -> kts.categoryFunction
        KeyCategory.PANEL -> kts.categoryPanel
        KeyCategory.CUSTOM -> kts.typeCustom
    }

private fun colorLabel(
    color: ToolbarKeyColor,
    kts: KeyToolbarStrings,
): String =
    when (color) {
        ToolbarKeyColor.AUTO -> kts.colorAuto
        ToolbarKeyColor.NEUTRAL -> kts.colorNeutral
        ToolbarKeyColor.RED -> kts.colorRed
        ToolbarKeyColor.GREEN -> kts.colorGreen
        ToolbarKeyColor.YELLOW -> kts.colorYellow
        ToolbarKeyColor.BLUE -> kts.colorBlue
        ToolbarKeyColor.MAGENTA -> kts.colorMagenta
        ToolbarKeyColor.CYAN -> kts.colorCyan
    }
