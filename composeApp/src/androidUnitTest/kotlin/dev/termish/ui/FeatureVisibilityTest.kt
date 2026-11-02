package dev.termish.ui

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.russhwolf.settings.MapSettings
import dev.termish.TermishApplication
import dev.termish.data.AppSettings
import dev.termish.data.Host
import dev.termish.data.HostRepository
import dev.termish.ssh.SftpEntry
import dev.termish.ui.theme.TerminalThemes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w420dp-h900dp", application = TermishApplication::class)
class FeatureVisibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val host = Host(id = "visibility", name = "test", hostname = "localhost", username = "test")
    private val storage = MapSettings()
    private val repository = HostRepository(storage)
    private val strings = appStringsFor("en")
    private var settings by mutableStateOf(AppSettings())
    private var controller: TerminalController? = null
    private var backRequests = 0
    private var agentContext: AgentLaunchContext? = null
    private lateinit var backDispatcher: OnBackPressedDispatcher
    private val allHidden =
        AppSettings(
            showTerminalGit = false,
            showTerminalTmux = false,
            showTerminalUpload = false,
            showTerminalScreen = false,
            showTerminalFavorites = false,
            showTerminalFileManager = false,
        )
    private val labels =
        listOf(
            strings.nativeAgents.askSelectedOutput,
            strings.git.menuLabel,
            strings.tmux.menuLabel,
            strings.upload.menuLabel,
            strings.screen.menuLabel,
            strings.sftpExt.favoritesTitle,
            strings.upload.fileManagerLabel,
        )

    @BeforeTest
    fun initializeComposeResources() {
        // Robolectric 不会随应用自动启动此 provider；通过清单中的真实 authority 初始化。
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(
            context.contentResolver.acquireContentProviderClient("dev.termish.app.resources.AndroidContextProvider"),
        ).close()
    }

    @AfterTest
    fun destroyController() {
        compose.runOnIdle { controller?.destroy() }
    }

    @Test
    fun defaultsHideAgentAndKeepTheOtherSixTerminalEntries() {
        showTerminal()
        openTools()
        compose.onNodeWithText(labels.first()).assertDoesNotExist()
        labels.drop(1).forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun eachTerminalSettingShowsOnlyItsOwnMenuEntry() {
        settings = allHidden
        showTerminal()
        val cases =
            listOf(
                allHidden.copy(showTerminalAgentAnalysis = true),
                allHidden.copy(showTerminalGit = true),
                allHidden.copy(showTerminalTmux = true),
                allHidden.copy(showTerminalUpload = true),
                allHidden.copy(showTerminalScreen = true),
                allHidden.copy(showTerminalFavorites = true),
                allHidden.copy(showTerminalFileManager = true),
            )
        cases.forEachIndexed { index, value ->
            compose.runOnIdle { settings = allHidden }
            compose.onNodeWithContentDescription(strings.featureVisibility.terminalTools).assertDoesNotExist()
            compose.runOnIdle { settings = value }
            openTools()
            labels.forEachIndexed { labelIndex, label ->
                if (index == labelIndex) {
                    compose.onNodeWithText(label).assertExists()
                } else {
                    compose.onNodeWithText(label).assertDoesNotExist()
                }
            }
        }
    }

    @Test
    fun hidingAllToolsClosesMenuAndDoesNotConsumeTheNextBack() {
        showTerminal()
        openTools()
        compose.runOnIdle { settings = allHidden }
        compose.onNodeWithContentDescription(strings.featureVisibility.terminalTools).assertDoesNotExist()
        labels.forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(1, backRequests) }
        compose.runOnIdle { settings = allHidden.copy(showTerminalTmux = true) }
        compose.onNodeWithContentDescription(strings.featureVisibility.terminalTools).assertExists()
        compose.onNodeWithText(strings.tmux.menuLabel).assertDoesNotExist()
        openTools()
        compose.onNodeWithText(strings.tmux.menuLabel).assertExists()
    }

    @Test
    fun terminalAgentWorksWhenMainAgentEntriesAreHidden() {
        settings = allHidden.copy(showTerminalAgentAnalysis = true)
        showTerminal()
        compose.runOnIdle {
            val terminal = assertNotNull(controller)
            terminal.emulator.writeText("build failed")
            terminal.selection.start(0, 0)
            terminal.selection.extend(0, 11)
            terminal.frame++
        }
        openTools()
        compose.onNodeWithText(strings.nativeAgents.askSelectedOutput).performClick()
        compose.waitUntil { agentContext != null }
        compose.runOnIdle {
            assertFalse(settings.showAgentEntry)
            assertTrue(assertNotNull(agentContext).prompt.contains("build failed"))
        }
    }

    @Test
    fun settingsRowsUpdateIndependentlyAndPersistImmediately() {
        compose.setContent {
            val current by repository.appSettings.collectAsState()
            MaterialTheme {
                SettingsTerminalToolsScreen(current, repository::saveSettings, {})
            }
        }
        labels.forEach { compose.onNodeWithText(it).performClick() }
        compose.runOnIdle {
            val saved = repository.loadSettings()
            assertEquals(allHidden.copy(showTerminalAgentAnalysis = true), saved)
            assertEquals(saved, HostRepository(storage).loadSettings())
        }
    }

    @Test
    fun hostMenuAgentEntryCanBeShownWithoutEnablingTerminalAnalysis() {
        compose.setContent {
            MaterialTheme {
                HostListScreen(
                    hosts = listOf(host),
                    showAgentEntry = settings.showAgentEntry,
                    hostSessions = emptyMap(),
                    onAdd = {},
                    onEdit = {},
                    onConnect = {},
                    onOpenHerdr = {},
                    onConnectBatch = {},
                    onDisconnect = {},
                    onDelete = {},
                    onAgents = {},
                    onOpenSession = {},
                    onOpenSftp = { _, _ -> },
                    onOpenScreenSession = {},
                    onStartSftp = {},
                    onOpenScreen = {},
                    onCloseAllSessions = {},
                )
            }
        }
        compose.onNodeWithContentDescription(strings.navMore).performClick()
        compose.onNodeWithText(strings.hostsActionAgents).assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(showAgentEntry = true) }
        compose.onNodeWithText(strings.hostsActionAgents).assertExists()
        compose.runOnIdle { assertFalse(settings.showTerminalAgentAnalysis) }
    }

    @Test
    fun sftpAgentEntryTracksMainSwitchAndPassesSelectedPaths() {
        val state =
            SftpUiState().apply {
                path = "/srv/app"
                entries = listOf(SftpEntry("build.log", false, "-rw-r--r--", 12, 0, false))
                selection.add("build.log")
                autoReconnectAttempted = true
            }
        compose.setContent {
            MaterialTheme {
                SftpContent(
                    host = host,
                    session = null,
                    state = state,
                    onBack = {},
                    onReconnect = {},
                    showAgentEntry = settings.showAgentEntry,
                    onAskAgent = { agentContext = it },
                )
            }
        }
        compose.onNodeWithContentDescription(strings.nativeAgents.askAboutFiles).assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(showAgentEntry = true) }
        compose.onNodeWithContentDescription(strings.nativeAgents.askAboutFiles).performClick()
        compose.runOnIdle {
            val context = assertNotNull(agentContext)
            assertTrue(context.prompt.contains("/srv/app/build.log"))
            assertEquals("/srv/app", context.directory)
            assertTrue(state.selection.isEmpty())
        }
    }

    private fun showTerminal() {
        val terminal = TerminalController(host, null, null, repository = repository)
        controller = terminal
        val tab = SessionTab.Terminal(terminal)
        compose.setContent {
            backDispatcher = assertNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            MaterialTheme {
                TerminalScreen(
                    tabs = listOf(tab),
                    current = tab,
                    theme = TerminalThemes.DEFAULT,
                    settings = settings,
                    repository = repository,
                    onBack = { backRequests++ },
                    onSwitchTab = {},
                    onAddSession = {},
                    onCloseTab = {},
                    onOpenSftpPicker = {},
                    onAskAgent = { _, context -> agentContext = context },
                )
            }
        }
    }

    private fun openTools() {
        compose.onNodeWithContentDescription(strings.featureVisibility.terminalTools).performClick()
        compose.mainClock.advanceTimeBy(300)
    }
}
