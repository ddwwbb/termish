package dev.termish.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.termish.data.AppSettings
import dev.termish.ui.theme.Spacing
import dev.termish.util.monospaceFontFamily

/** 仅控制终端更多功能菜单的显示，不改变功能本身或其他位置的入口。 */
@Composable
fun SettingsTerminalToolsScreen(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalAppStrings.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(Spacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = s.navBack)
            }
            Text(
                s.featureVisibility.terminalTools,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = monospaceFontFamily(),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SettingsSwitchItem(s.nativeAgents.askSelectedOutput, settings.showTerminalAgentAnalysis) {
                onChange(settings.copy(showTerminalAgentAnalysis = it))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsSwitchItem(s.git.menuLabel, settings.showTerminalGit) {
                onChange(settings.copy(showTerminalGit = it))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsSwitchItem(s.tmux.menuLabel, settings.showTerminalTmux) {
                onChange(settings.copy(showTerminalTmux = it))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsSwitchItem(s.upload.menuLabel, settings.showTerminalUpload) {
                onChange(settings.copy(showTerminalUpload = it))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsSwitchItem(s.screen.menuLabel, settings.showTerminalScreen) {
                onChange(settings.copy(showTerminalScreen = it))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsSwitchItem(s.sftpExt.favoritesTitle, settings.showTerminalFavorites) {
                onChange(settings.copy(showTerminalFavorites = it))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsSwitchItem(s.upload.fileManagerLabel, settings.showTerminalFileManager) {
                onChange(settings.copy(showTerminalFileManager = it))
            }
        }
    }
}
