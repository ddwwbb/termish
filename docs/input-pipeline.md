# 输入管线（IME / 键盘 / 触摸）

> **English summary:** how keyboard input reaches the remote shell — a hidden
> `BasicTextField` as the IME entry point, composition-safe CJK input (composing
> text never hits the wire; only committed text is diffed), split backspace
> semantics, sticky CTRL/ALT, bracketed paste, and the touch-to-mouse-event
> mapping for TUIs with mouse reporting enabled.

Termish 的输入管线围绕一个原则设计：**IME 组合态（拼音）是一等公民**——组合态不上线、
提交才发送，远端行永远不会被拼音污染。实现集中在 `ui/TerminalScreen.kt`（状态管理）、
`ui/TerminalView.kt`（触摸手势）、`ui/KeyToolbar.kt`（功能键）。

## 输入接入口：隐藏 BasicTextField

- 一个 1dp 透明 `BasicTextField` 悬浮在工具栏区，仅作为输入法接入口，**不渲染可见 UI**——打字内容以远端回显为准（远端 echo 才是真实状态，本地显示反而重复干扰）。
- `KeyboardType.Text`（不是 Ascii）：保中文候选栏；`capitalization = None`、`autoCorrect = false`。
- `singleLine = false` 多行模式：捕获输入法 Enter（`\n` → 发 CR）。
- 点画布/⌨ 键时 `requestFocus()` + 延迟 150ms `show()`：焦点变更与输入连接建立是异步的，立即 show 常被忽略；`onFocusChanged` 里再兜底 show 一次。
- 画布轻点通过 `onRequestKeyboard` 接入同一个键盘请求；TUI 鼠标模式下仍先发送远端 Down/Up。长按、双击选词、拖动、打开链接和轻点取消选区不请求键盘。
- 输入法组合期间**组合文本绝不发往远端**。

## 提交 diff 算法

已提交文本基线 `committedText` + 输入框当前值做**公共前缀 diff**：

```
p = 最长公共前缀长度
发送 oldText.length - p 个 0x7f（删除尾部差异）
发送 newText.substring(p)（新增部分，经 sendTyped 处理 CTRL/ALT/换行）
```

- 纯尾部增删是精确操作；中间编辑无法映射到远端光标，按"删尾重发"处理。
- 输入框缓冲 >64 字符或含 `\n` 时清空（防长粘贴积压 + Enter 后复位）。

## 退格语义（三段式）

| 场景 | 行为 |
|------|------|
| 输入法组合中 | 不拦截：IME 自己删拼音（组合文本本就没发给远端） |
| 输入框有已提交内容 | 不拦截：平台删除 → onValueChange diff 发送（光标/选区由系统维护） |
| 输入框为空 | `onPreviewKeyEvent` 拦截 Backspace，直接发 0x7f —— Enter 后/快速命令后远端行有内容也删得掉 |

## 字符发送（sendTyped）

- ALT 粘性 → 前缀 `ESC`（0x1b）。
- CTRL 粘性 + a–z → `(ch.code and 0x1f)`。
- `\n` → CR（0x0d）。
- 其余 → UTF-8 字节直发。

**粘性修饰键消费规则**：任何键发出后（工具栏 onKey / onChar / onPaste / onCustom）CTRL/ALT 立即复位，
避免残留导致下个字母变成 ⌃D/⌃C 意外退出会话。

## bracketed paste

工具栏 PST（或系统粘贴）：远端开启 DECSET 2004 时包裹 `ESC[200~ … ESC[201~`，
vim/nvim 不会把粘贴内容当手打（避免自动缩进错乱）。OSC 52 反向通道（远端写剪贴板）
受设置开关控制，静默写入不弹提示。

## 焦点事件

DECSET 1004 开启时，输入框聚焦/失焦发 `CSI I` / `CSI O`（vim/tmux 据此刷新界面状态）。

## 功能键工具栏（4×8 布局：常驻两行 + 展开两行，支持分类着色与自定义编辑）

默认布局（行 3/4 展开时渲染在常驻行 1/2 上方，覆盖画布而不压缩 PTY 尺寸）：

```
行3（展开）: PST   ⇧⇥   DEL  HOME  END  PGUP  PGDN  ⌃\
行4（展开）: F1    F2   F3   F4    F5   F6    F7    ⎇
行1（常驻）: ESC   TAB  ⌃C   ⌃L    {}   ↑     ⌨     ▾
行2（常驻）: CTRL  ALT  ⌃D   /     ←    ↓     →     ENT
```

- ↑ 与 ↓ 在第 6 列上下对齐，⌨ / ▾ / ENT 同在右侧拇指区；右上角 `▾`（展开/收起）位置固定不可编辑，其余 31 个槽位可在「设置 → 快捷按钮」中自定义。
- **分类着色与毛玻璃效果**：默认按功能分类自动取当前终端主题的 ANSI 普通色（控制信号 `ansi(1)` 红、编辑 `ansi(2)` 绿、功能键 `ansi(3)` 黄、导航 `ansi(4)` 蓝、修饰键 `ansi(5)` 紫、面板入口 `ansi(6)` 青），文字与边框着色并叠同色浅底；CTRL/ALT 粘性激活或 `▾` 展开时实心填充。单键可在设置中覆盖颜色或设为中性。工具栏默认开启双层毛玻璃效果（`AppSettings.toolbarFrostedGlass = true`）：悬浮栏底板对后方终端画布做实时高斯模糊（Haze `20.dp` + `70%` 主题底色染色，Android < 12 降级为 `88%` 半透明底板），按钮键帽使用纵向半透明渐变底与顶部高光描边；可在「设置 → 快捷按钮 → 毛玻璃效果」中切换。
- **自定义编辑**（`AppSettings.keyToolbarLayout`，默认 `null` 表示跟随 `KeyToolbarLayout.DEFAULT`）：支持替换为任意内置键（含 `F8–F12`、`⌃E`、`⌃R`、`⌃Z`）、增删改自定义键（1–4 字符标签 + 发送内容）、长按拖拽交换位置（可跨行或拖入空槽，松手保存；拖出槽位或取消不修改布局，固定展开键不可拖动或接收）、清空槽位（渲染同尺寸透明占位保持列对齐）及一键恢复默认。
- **自定义键转义语法**（`parseKeyPayload`）：支持 `\n`、`\r`、`\t`、`\e`（ESC）、`\\`、`\^`、`\xHH`、`\uXXXX` 以及 `^@`–`^_`、`^a`–`^z`、`^?`（0x7f）控制字符；其余字符按 UTF-8 原样发送，不自动追加回车。
- **长按宏与连发**（跟随动作，键移到任意槽位均保留）：方向键按住连发（400ms 后 60ms/次）；长按 `ESC` 双发；长按 `TAB` 发 `⇧⇥`（`ESC [ Z`）；长按 `⌃C`/`⌃D` 发 `⌃\`（SIGQUIT）。
- CTRL/ALT 粘性 + 系统键盘字母即 ⌃A/⌃E/⌃R 等组合；方向键与 HOME/END 在发送时读取当前会话 buffer 的 `applicationCursorKeys`（DECCKM）：普通模式用 `ESC [ A/B/C/D/H/F`（CSI），应用模式用 `ESC O A/B/C/D/H/F`（SS3）。模式由远端 `CSI ? 1 h/l` 控制；存活会话切 tab、退出终端页后重入及健康连接回前台时保留各自的 buffer，不依赖 UI 重组刷新模式。

## 触摸 → 终端鼠标事件（TUI 鼠标上报）

远端开启鼠标上报（1000/1002/1003）时，画布手势被接管并映射为鼠标事件；未开启时不消费
事件，保持聚焦键盘/选择/滚动回看的默认行为。长按不论鼠标模式都进入本地选择，并阻止本次触摸继续发送远端鼠标事件。编码按模式：X10（字节偏移 32，上限 223）、
SGR 1006、urxvt 1015（与 1006 互斥）。

| 手势 | 映射 | 细节 |
|------|------|------|
| 轻点 | 同格 Down + Up | 释放用按下格，避免手指抖动被 herdr 的 1 格拖拽阈值判成拖拽 |
| 单指纵向拖拽 | 滚轮（64/65） | 位移主导方向锁定（防横向抖动误判）；**滚轮行钳制到第 1 行起**——第 0 行在桌面布局下是 herdr 的 tab 栏，滚到 0 会疯狂切 tab |
| 单指横向 / 双指拖拽 | Down + drag-motion(32) + Up | 选字、拖分割线、pane 内拖拽（需 1002/1003） |
| 纵向拖拽抬起 | 惯性滚轮 | 近 150ms 窗口估速（裁剪长按期的静止事件），衰减循环补发 |

手势意图明确前不补发 Down（轻点=点击、纵向=纯滚轮），避免"按下后在其他格抬起"被 herdr
判成拖拽选区并复制 → OSC 52 → 反复弹剪贴板提示。

## 滚动回看（非鼠标模式）

- `scrollable` fling 惯性；右边缘可拖滚动条。
- DECSET 1007 alternate scroll：备用屏无回看可滚，滚动手势转方向键交给远端全屏程序（less/vim）。
- 键盘弹出时画布按光标位置向上平移（不改 PTY 尺寸，避免全屏程序随键盘弹收反复重排），平移计算放 `graphicsLayer` 块内，不触发重组。

## 文本选择与复制

- 长按文字后继续拖动，在同一次触摸中扩展选区；松手保留高亮，双击则选中词。
- 顶部操作条提供复制、粘贴、全选、取消；复制后清除选区，粘贴沿用 bracketed paste。
- 全选范围为当前可见行：向上回看后选择历史视口，不跳到最新输出。视口和选区按会话独立保存。
- 拖到顶部持续向历史滚动，拖到底部持续向最新输出滚动；手指停住也继续滚动，松手即停止。
- 点画布空白处、返回键或取消均清除选区并收起操作条。选区变化统一通知控制器更新高亮。
- 鼠标上报模式下，短按和快速拖动仍交给远端；长按切换为本地选择，同一次拖动和松手不再补发远端点击。已有本地选区时，点画布只清除选区。

## 快速命令与启动命令

- 主机可配 quick commands（如常用运维命令），连接后以 chip 形式显示在画布上方，点按即发。
- startup command（如 `tmux new -A -s main`）连接成功后自动执行，配合自动重连实现服务端会话现场恢复。

## 相关设置项

工具栏显隐、自动重连、TOFU 首次确认、OSC 52 剪贴板、触感反馈——见设置页（apply-on-change 即改即存）。
语音输入总开关关闭时，终端画布隐藏麦克风按钮；开启后显示。

「设置 → 终端 → 更多功能」分别控制 Agent 选中内容分析、Git、tmux、上传文件、远程画面、收藏夹和文件管理的菜单入口。Agent 分析默认关闭，其余默认开启；保持现有菜单相对顺序，全部关闭时收起菜单并隐藏「+」。这些开关只影响更多功能菜单，不影响快捷按钮栏、主机页入口或正在运行的任务。

「设置 → 通用 → 显示 Agent 入口」默认关闭，控制主机菜单、主机及连接列表中的 Agent 会话入口和 SFTP 分析按钮；隐藏会话时同步过滤数量和审批角标，不终止 Agent 任务。终端选中内容分析使用自己的开关，可独立开启并继续向同主机 Agent 传递上下文。
