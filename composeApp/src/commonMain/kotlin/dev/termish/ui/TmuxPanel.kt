package dev.termish.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.termish.tmux.TmuxActionResult
import dev.termish.tmux.TmuxCommands
import dev.termish.tmux.TmuxDiscovery
import dev.termish.tmux.TmuxSessionInfo
import dev.termish.ui.theme.Spacing
import dev.termish.ui.theme.TerminalTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * tmux 管理命令执行器：复用 GitCommandRunner 的通道策略——SSH 复用已认证连接的
 * 独立 exec 通道，mosh 走控制面连接；**绝不注入交互终端**。
 *
 * tmux 命令不依赖工作目录（与会话路径无关），故 workdir 恒为 null：
 * runner 仅使用其 run() 的通道能力，不触发 GitWorkdirUnknownException 路径。
 */
internal class TerminalTmuxCommandRunner(
    private val delegate: TerminalGitCommandRunner,
) {
    val herdrMode: Boolean get() = delegate.herdrMode

    suspend fun run(
        command: String,
        timeoutMs: Long = TMUX_TIMEOUT_MS,
    ): String = delegate.runTmuxCommand(command, timeoutMs)

    companion object {
        const val TMUX_TIMEOUT_MS = 8_000L
    }
}

/** tmux 面板状态机：把「未安装 / 失败 / 零会话 / 正常」分开，失败可重试。 */
private enum class TmuxPhase { IDLE, CHECKING, READY, NOT_INSTALLED, ERROR }

/** 一个会话行的管理动作。 */
private enum class TmuxRowAction { RENAME, DETACH, KILL }

/**
 * tmux 管理面板：终端画布上的 ModalBottomSheet（与 Git 面板同层）。
 *
 * 列表 / 附加 / 新建 / rename / detach / kill 全部走独立 exec 通道；
 * 「附加」用终端会话本体（TMUX 启动模式，静默+清屏注入或 mosh `-- sh -c` 直接 exec），
 * 面板内管理命令绝不进 TUI。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TmuxOverlay(
    connected: Boolean,
    runner: TerminalTmuxCommandRunner,
    theme: TerminalTheme,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    /** 附加到指定会话：由调用方以 TMUX 启动模式打开/复用终端会话。 */
    onAttach: (String) -> Unit,
    bottomInset: Dp,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    if (!connected) return

    var phase by remember { mutableStateOf(TmuxPhase.IDLE) }
    var sessions by remember { mutableStateOf<List<TmuxSessionInfo>>(emptyList()) }
    var negotiatedTerm by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var newOpen by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<TmuxSessionInfo?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var killTarget by remember { mutableStateOf<TmuxSessionInfo?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    PlatformBackHandler(enabled = open) { onOpenChange(false) }

    suspend fun refresh() {
        if (busy) return
        busy = true
        phase = TmuxPhase.CHECKING
        message = null
        try {
            val out = runner.run(TmuxCommands.discoverCmd)
            when (val d = TmuxCommands.parseDiscovery(out)) {
                is TmuxDiscovery.Ready -> {
                    phase = TmuxPhase.READY
                    sessions = d.sessions
                    negotiatedTerm = d.term
                }
                TmuxDiscovery.NotInstalled -> phase = TmuxPhase.NOT_INSTALLED
                TmuxDiscovery.Malformed -> {
                    phase = TmuxPhase.ERROR
                    message = s.tmux.error
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            phase = TmuxPhase.ERROR
            message = e.message ?: s.tmux.error
        } finally {
            busy = false
        }
    }

    /** 执行管理命令并刷新列表；失败时错误留在 message。 */
    suspend fun action(command: String): TmuxActionResult? {
        if (busy) return null
        busy = true
        try {
            val raw = runner.run(TmuxCommands.actionCmd(command))
            val res = TmuxCommands.parseAction(raw)
            if (res == null) {
                message = s.tmux.error
            } else if (!res.ok) {
                message = "${s.tmux.actionFailed}: ${res.output.take(200)}"
            }
            return res
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: s.tmux.error
            return null
        } finally {
            busy = false
        }
    }

    LaunchedEffect(open) {
        if (open) refresh()
    }

    if (open) {
        ModalBottomSheet(
            onDismissRequest = { onOpenChange(false) },
            sheetState = sheetState,
            containerColor = theme.background(),
            contentColor = theme.foreground(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.66f)
                    .imePadding()
                    .padding(bottom = bottomInset),
            ) {
                // 标题栏
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        s.tmux.menuLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.foreground(),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { scope.launch { refresh() } }, enabled = !busy) {
                        Icon(Icons.Filled.Refresh, contentDescription = s.tmux.retry, tint = theme.foreground())
                    }
                    IconButton(onClick = { onOpenChange(false) }) {
                        Icon(Icons.Filled.Close, contentDescription = s.tmux.close, tint = theme.foreground())
                    }
                }
                HorizontalDivider(color = theme.ansi(8))

                when (phase) {
                    TmuxPhase.IDLE, TmuxPhase.CHECKING ->
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = theme.cursor())
                                Spacer(Modifier.height(12.dp))
                                Text(s.tmux.loading, style = MaterialTheme.typography.bodyMedium, color = theme.foreground())
                            }
                        }

                    TmuxPhase.NOT_INSTALLED ->
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                s.tmux.notInstalled,
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.foreground(),
                            )
                        }

                    TmuxPhase.ERROR ->
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(message ?: s.tmux.error, style = MaterialTheme.typography.bodyMedium, color = theme.ansi(1))
                                Spacer(Modifier.height(12.dp))
                                TextButton(onClick = { scope.launch { refresh() } }) { Text(s.tmux.retry) }
                            }
                        }

                    TmuxPhase.READY ->
                        Column(Modifier.fillMaxSize()) {
                            message?.let { msg ->
                                Text(
                                    msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.ansi(1),
                                    modifier = Modifier.padding(horizontal = Spacing.Md, vertical = 4.dp),
                                )
                            }
                            if (sessions.isEmpty()) {
                                Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text(s.tmux.empty, style = MaterialTheme.typography.bodyMedium, color = theme.foreground())
                                }
                            } else {
                                LazyColumn(Modifier.weight(1f)) {
                                    items(sessions, key = { it.id }) { session ->
                                        TmuxSessionRow(
                                            session = session,
                                            theme = theme,
                                            onAttach = {
                                                onToast(s.tmux.attached(session.name))
                                                onAttach(session.name)
                                                onOpenChange(false)
                                            },
                                            onRename = {
                                                renameTarget = session
                                                renameValue = session.name
                                            },
                                            onDetach = {
                                                scope.launch {
                                                    if (action(TmuxCommands.detachCmd(session.id))?.ok == true) {
                                                        onToast(s.tmux.detached)
                                                        refresh()
                                                    }
                                                }
                                            },
                                            onKill = { killTarget = session },
                                        )
                                        HorizontalDivider(color = theme.ansi(8).copy(alpha = 0.4f))
                                    }
                                }
                            }
                            // 新建入口
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.Md),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = {
                                    newName = ""
                                    newOpen = true
                                }, enabled = !busy) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = theme.cursor())
                                    Spacer(Modifier.width(4.dp))
                                    Text(s.tmux.newSession, color = theme.foreground())
                                }
                            }
                        }
                }
            }
        }
    }

    // 新建会话对话框
    if (newOpen) {
        TmuxNameDialog(
            title = s.tmux.newSessionTitle,
            placeholder = s.tmux.namePlaceholder,
            initial = TmuxCommands.defaultSessionName(runner.let { "" }),
            confirmLabel = s.tmux.create,
            cancelLabel = s.tmux.cancel,
            invalidLabel = s.tmux.nameInvalid,
            onDismiss = { newOpen = false },
        ) { name ->
            newOpen = false
            scope.launch {
                if (action(TmuxCommands.newCmd(name))?.ok == true) {
                    onToast(s.tmux.created(name))
                    onAttach(name)
                    onOpenChange(false)
                } else {
                    refresh()
                }
            }
        }
    }

    // 重命名对话框
    renameTarget?.let { target ->
        TmuxNameDialog(
            title = s.tmux.rename,
            placeholder = s.tmux.namePlaceholder,
            initial = target.name,
            confirmLabel = s.tmux.rename,
            cancelLabel = s.tmux.cancel,
            invalidLabel = s.tmux.nameInvalid,
            onDismiss = { renameTarget = null },
        ) { name ->
            renameTarget = null
            scope.launch {
                if (action(TmuxCommands.renameCmd(target.id, name))?.ok == true) {
                    onToast(s.tmux.renamed)
                    refresh()
                }
            }
        }
    }

    // 终止确认对话框
    killTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { killTarget = null },
            title = { Text(s.tmux.killConfirmTitle) },
            text = { Text(s.tmux.killConfirmBody(target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    killTarget = null
                    scope.launch {
                        if (action(TmuxCommands.killCmd(target.id))?.ok == true) {
                            onToast(s.tmux.killed)
                            refresh()
                        }
                    }
                }) { Text(s.tmux.kill, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { killTarget = null }) { Text(s.tmux.cancel) }
            },
        )
    }
}

/** 会话行：名称 + 元信息 + 行内管理动作。 */
@Composable
private fun TmuxSessionRow(
    session: TmuxSessionInfo,
    theme: TerminalTheme,
    onAttach: () -> Unit,
    onRename: () -> Unit,
    onDetach: () -> Unit,
    onKill: () -> Unit,
) {
    val s = LocalAppStrings.current
    val created =
        remember(session.created) {
            runCatching {
                val tz = TimeZone.currentSystemDefault()
                val d = Instant.fromEpochSeconds(session.created).toLocalDateTime(tz)
                val pad = { v: Int -> v.toString().padStart(2, '0') }
                "${pad(d.monthNumber)}-${pad(d.dayOfMonth)} ${pad(d.hour)}:${pad(d.minute)}"
            }.getOrDefault("")
        }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onAttach)
            .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    session.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.foreground(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildList {
                        add(s.tmux.windowsLabel(session.windows))
                        if (session.clients > 0) add(s.tmux.clientsLabel(session.clients))
                        if (created.isNotBlank()) add(created)
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.foreground().copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onAttach, enabled = true) {
                Icon(Icons.Filled.Login, contentDescription = s.tmux.attach, tint = theme.cursor())
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = s.tmux.rename, tint = theme.foreground().copy(alpha = 0.7f))
            }
            IconButton(onClick = onDetach) {
                Icon(Icons.Filled.LinkOff, contentDescription = s.tmux.detach, tint = theme.foreground().copy(alpha = 0.7f))
            }
            IconButton(onClick = onKill) {
                Icon(Icons.Filled.Delete, contentDescription = s.tmux.kill, tint = theme.ansi(1).copy(alpha = 0.8f))
            }
        }
    }
}

/** 会话名输入对话框：创建 / 重命名共用；提交前校验名称合法性。 */
@Composable
private fun TmuxNameDialog(
    title: String,
    placeholder: String,
    initial: String,
    confirmLabel: String,
    cancelLabel: String,
    invalidLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val valid = TmuxCommands.isValidSessionName(value.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    isError = value.isNotBlank() && !valid,
                    supportingText = {
                        if (value.isNotBlank() && !valid) Text(invalidLabel, color = MaterialTheme.colorScheme.error)
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = valid,
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelLabel) }
        },
    )
}
