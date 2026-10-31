package dev.termish.tmux

/**
 * tmux 侧通道纯逻辑层：命令构造与输出解析，零平台依赖、零状态。
 *
 * 全部命令在已认证 SSH 连接的独立 exec 通道执行（[dev.termish.ssh.SshSession.runCommandDetailed]），
 * 绝不注入交互终端。解析与构造均为纯函数，可直接 JVM 单测。
 *
 * 协议约定（自研，与 moke 无代码关系）：
 * - 探测/列表一次完成：先输出标记行区分「未安装 / 可用」，再输出会话行；
 * - 会话行分隔符用 `:`（tmux 会话名不允许含 `:`，TAB 会被 tmux 格式串替换成 `_`），
 *   数值字段从行尾反向解析，会话名本身可含任意 UTF-8 字符；
 * - `tmux -u` 强制 UTF-8 输出，不依赖远端非交互 shell 的 LANG；
 * - 管理命令包装首行回退出码（`__TERMISH_TMUX_RC__:`），stderr 合入正文供 UI 显示。
 */

/** 远端一个 tmux 会话（list-sessions 解析所得）。 */
data class TmuxSessionInfo(
    /** tmux server 生命周期内的精确句柄（#{session_id}，如 `$0`）。 */
    val id: String,
    /** 会话名：跨连接恢复的用户可读身份。 */
    val name: String,
    val windows: Int,
    /** 当前附加到该会话的客户端数。 */
    val clients: Int,
    /** 创建时间（epoch 秒）。 */
    val created: Long,
)

/** tmux 探测结果：未安装 / 可用（带会话列表与 TERM 协商值）/ 输出不可解析。 */
sealed interface TmuxDiscovery {
    /** 远端没有 tmux 可执行文件。 */
    data object NotInstalled : TmuxDiscovery

    /**
     * 探测成功。[term] 是远端真的可用的最佳 TERM（协商结果）；null = 远端没有判定工具，保持默认。
     */
    data class Ready(
        val sessions: List<TmuxSessionInfo>,
        val term: String? = null,
    ) : TmuxDiscovery

    /** 输出不符合协议（异常 shell 环境 / 命令被拦截等），不与「零会话」混淆。 */
    data object Malformed : TmuxDiscovery
}

/** 一次管理命令（rename/detach/kill…）的结果。 */
data class TmuxActionResult(
    val ok: Boolean,
    val output: String,
)

object TmuxCommands {
    internal const val READY_MARKER = "__TERMISH_TMUX__:ready"
    internal const val MISSING_MARKER = "__TERMISH_TMUX__:missing"
    internal const val ACTION_PREFIX = "__TERMISH_TMUX_RC__:"
    internal const val TERM_PREFIX = "__TERMISH_TMUX_TERM__:"

    /** termish 终端内核对齐 xterm；仅当远端确实没有该条目时才逐级降级。 */
    const val DEFAULT_TERM = "xterm-256color"
    private val TERM_CANDIDATES = listOf("xterm-256color", "screen-256color", "xterm", "vt100")
    private val TERM_SAFE = Regex("""^[A-Za-z0-9._-]{1,32}$""")

    /**
     * TERM 协商：挑出远端真的能用的第一个候选。
     * 用 `tput clear` 判定（有的主机 infocmp 找得到条目但缺 clear 能力，tmux 仍拒绝启动）；
     * 两个工具都没有时不输出，调用方保持默认值。
     */
    private val termProbe: String
        get() =
            "termish_t=''; for t in ${TERM_CANDIDATES.joinToString(" ")}; do " +
                "if command -v tput >/dev/null 2>&1; then " +
                "TERM=\"\$t\" tput clear >/dev/null 2>&1 && { termish_t=\$t; break; }; " +
                "elif infocmp \"\$t\" >/dev/null 2>&1; then termish_t=\$t; break; fi; done; " +
                "[ -n \"\$termish_t\" ] && printf '$TERM_PREFIX%s\\n' \"\$termish_t\"; "

    /**
     * 探测 + 列表一次完成（避免为一次刷新开两条 exec 通道）。
     * `list-sessions` 在「已安装但还没有 tmux server」时退出非零，仍是合法零会话。
     */
    val discoverCmd: String =
        "if ! command -v tmux >/dev/null 2>&1; then " +
            "printf '$MISSING_MARKER\\n'; " +
            "else printf '$READY_MARKER\\n'; " +
            termProbe +
            "tmux -u list-sessions " +
            "-F '#{session_id}:#{session_name}:#{session_windows}:#{session_attached}:#{session_created}' " +
            "2>/dev/null || true; fi"

    /** 解析 [discoverCmd] 输出。 */
    fun parseDiscovery(out: String): TmuxDiscovery {
        val lines = out.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return TmuxDiscovery.Malformed
        val first = lines.first().trim().removeSuffix("\r")
        if (first == MISSING_MARKER) return TmuxDiscovery.NotInstalled
        if (first != READY_MARKER) return TmuxDiscovery.Malformed
        val rest = lines.drop(1)
        val term =
            rest
                .firstOrNull { it.trim().startsWith(TERM_PREFIX) }
                ?.trim()
                ?.removePrefix(TERM_PREFIX)
                ?.takeIf { TERM_SAFE.matches(it) }
        val sessionLines = rest.filterNot { it.trim().startsWith(TERM_PREFIX) }
        val sessions = sessionLines.mapNotNull(::parseSessionLine)
        return if (sessions.size == sessionLines.size) {
            TmuxDiscovery.Ready(sessions, term)
        } else {
            TmuxDiscovery.Malformed
        }
    }

    private fun parseSessionLine(line: String): TmuxSessionInfo? {
        val clean = line.trim().removeSuffix("\r")
        val p = clean.split(':')
        if (p.size < 5) return null
        val id = p[0].trim()
        val windows = p[p.size - 3].trim().toIntOrNull() ?: return null
        val clients = p[p.size - 2].trim().toIntOrNull() ?: return null
        val created = p[p.size - 1].trim().toLongOrNull() ?: return null
        // id 形如 `$0`；会话名可含冒号以外的任意字符，名字段 = 中间全部
        if (!id.startsWith("$")) return null
        return TmuxSessionInfo(
            id = id,
            name = p.subList(1, p.size - 3).joinToString(":"),
            windows = windows,
            clients = clients,
            created = created,
        )
    }

    /** 单引号安全包裹（防远端 shell 对空格 / `$` 等做扩展）。 */
    internal fun shQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    fun newCmd(name: String): String = "tmux new-session -d -s ${shQuote(name)}"

    fun renameCmd(
        id: String,
        name: String,
    ): String = "tmux rename-session -t ${shQuote(id)} ${shQuote(name)}"

    fun detachCmd(id: String): String = "tmux detach-client -s ${shQuote(id)}"

    fun killCmd(id: String): String = "tmux kill-session -t ${shQuote(id)}"

    /**
     * 把管理命令包装成可解析结果：stderr 合入正文（失败原因能在 UI 显示），
     * 首行固定 `ACTION_PREFIX` + 退出码，其余为 tmux 原始输出。
     */
    fun actionCmd(command: String): String =
        "O=\$({ $command; } 2>&1); R=\$?; " +
            "printf '${ACTION_PREFIX}%s\\n' \"\$R\"; printf '%s' \"\$O\""

    fun parseAction(out: String): TmuxActionResult? {
        val firstBreak = out.indexOf('\n')
        val first = (if (firstBreak >= 0) out.substring(0, firstBreak) else out).trim()
        if (!first.startsWith(ACTION_PREFIX)) return null
        val code = first.removePrefix(ACTION_PREFIX).toIntOrNull() ?: return null
        val body = if (firstBreak >= 0) out.substring(firstBreak + 1).trim() else ""
        return TmuxActionResult(ok = code == 0, output = body)
    }

    /**
     * 在一个新的干净终端连接里附加（存在则附加，不存在则创建）。
     *
     * 用 `tmux -u new-session -A`（tmux 自身原子地处理「存在/不存在」），会话名走
     * argv（`\$1`）不参与 shell 解析；TERM 内联协商（附加发生在任何侧通道探测之前）；
     * 不用 `exec tmux`——tmux 非零退出时回落登录壳，错误留在屏幕上而不是通道立刻 EOF。
     * 该串同时适用于 SSH exec 与 mosh 的 `mosh-server … -- …`（mosh-server 直接 execvp，
     * 必须自带 `sh -c`）。
     */
    fun attachOrCreateCmd(
        name: String,
        detachOthers: Boolean = false,
        term: String? = null,
    ): String {
        val flag = if (detachOthers) " -D" else ""
        val candidates =
            (listOfNotNull(term?.takeIf { TERM_SAFE.matches(it) }) + TERM_CANDIDATES)
                .distinct()
                .joinToString(" ")
        return "sh -c '" +
            "for t in $candidates; do " +
            "if command -v tput >/dev/null 2>&1; then " +
            "TERM=\"\$t\" tput clear >/dev/null 2>&1 && { TERM=\$t; export TERM; break; }; " +
            "elif infocmp \"\$t\" >/dev/null 2>&1; then TERM=\$t; export TERM; break; fi; done; " +
            "command -v tmux >/dev/null 2>&1 || " +
            "{ echo \"termish: tmux not found on this host\"; exec \"\${SHELL:-sh}\" -l; }; " +
            "tmux -u new-session -A$flag -s \"\$1\"; " +
            "ec=\$?; [ \$ec -eq 0 ] && exit 0; " +
            "echo \"termish: tmux exited (\$ec)\"; exec \"\${SHELL:-sh}\" -l' sh ${shQuote(name)}"
    }

    /**
     * 由连接名派生默认会话名：tmux 会话名不允许 `:`（目标语法分隔符）与 `.`，
     * 空白折叠成 `-`；全部不可用时回落 `termish`。
     */
    fun defaultSessionName(label: String): String =
        label
            .trim()
            .replace(Regex("""[^\p{L}\p{N}_-]+"""), "-")
            .trim('-')
            .take(32)
            .ifBlank { "termish" }

    /** 名称合法性校验：非空、不含 `:` / `.`、不含控制字符。 */
    fun isValidSessionName(name: String): Boolean = name.isNotBlank() && name.none { it == ':' || it == '.' || it.isWhitespace() || it.isISOControl() }
}
