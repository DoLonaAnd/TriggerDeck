package net.dolonaand.apk.triggerdeck.ui

import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.dolonaand.apk.triggerdeck.TriggerDispatcher
import net.dolonaand.apk.triggerdeck.TriggerMonitor
import net.dolonaand.apk.triggerdeck.TriggerService
import net.dolonaand.apk.triggerdeck.TriggerSettings
import net.dolonaand.apk.triggerdeck.WalletLauncher
import net.dolonaand.apk.triggerdeck.model.TriggerSource
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 動作確認・不具合調査用の画面 */
@Composable
fun DiagnosticsScreen(settings: TriggerSettings, resumeTick: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ServiceSection(resumeTick)
        LastEventSection()
        KeySection(settings)
        TestSection(resumeTick)
        RebootSection(settings, resumeTick)
        LogSection()
    }
}

@Composable
private fun Body(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        content()
    }
}

@Composable
private fun ServiceSection(resumeTick: Int) {
    val context = LocalContext.current
    val connected by TriggerMonitor.serviceConnected.collectAsStateWithLifecycle()
    val enabled = remember(resumeTick, connected) { TriggerService.isEnabledInSettings(context) }
    val fg by TriggerMonitor.foregroundPackage.collectAsStateWithLifecycle()
    SectionHeader("サービス")
    SectionCard {
        Body {
            StatusLine(connected, if (connected) "接続中" else if (enabled) "有効 (未接続)" else "無効")
            Mono("前面アプリ: ${fg ?: "-"}", size = 12)
        }
    }
}

@Composable
private fun LastEventSection() {
    val last by TriggerMonitor.lastKeyEvent.collectAsStateWithLifecycle()
    val state by TriggerMonitor.triggerState.collectAsStateWithLifecycle()
    SectionHeader("最後の入力")
    SectionCard {
        Body {
            val e = last?.first
            Mono("KeyCode : " + (e?.let { "${it.keyCode} (${KeyEvent.keyCodeToString(it.keyCode)})" } ?: "-"))
            Mono("ScanCode: " + (e?.scanCode ?: "-"))
            Mono("Action  : " + (e?.let { TriggerDispatcher.actionName(it.action) } ?: "-"))
            Mono("Device  : " + (e?.let { "${it.deviceName ?: "?"} (id=${it.deviceId})" } ?: "-"))
            Mono("Source  : " + (e?.let { sourceName(it.source) } ?: "-"))
            Mono("Time    : " + (last?.second?.let { TIME.format(Date(it)) } ?: "-"))
            TriggerSource.entries.forEach { Mono("${it.label}: ${state[it] ?: "-"}") }
        }
    }
}

@Composable
private fun KeySection(settings: TriggerSettings) {
    val learning by TriggerMonitor.learning.collectAsStateWithLifecycle()
    SectionHeader("トリガーのキー")
    SectionCard {
        TriggerSource.entries.filter { it.isShoulder }.forEach { source ->
            // learning が解除されたら割り当て済みキーコードを再読込する
            val keyCode = remember(learning) { settings.keyCode(source) }
            Body {
                Text(source.label)
                Mono("${KeyEvent.keyCodeToString(keyCode)} ($keyCode)", size = 12)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (learning == source) {
                        Button(onClick = { TriggerMonitor.startLearning(null) }) { Text("トリガーを押してください (取消)") }
                    } else {
                        OutlinedButton(onClick = { TriggerMonitor.startLearning(source) }) { Text("キーを学習") }
                        TextButton(onClick = {
                            settings.setKeyCode(source, TriggerSettings.defaultKeyCode(source))
                            TriggerMonitor.startLearning(null)
                        }) { Text("既定に戻す") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TestSection(resumeTick: Int) {
    val context = LocalContext.current
    val installed = remember(resumeTick) { WalletLauncher.isInstalled(context) }
    val version = remember(resumeTick) { WalletLauncher.versionName(context) }
    SectionHeader("動作テスト")
    SectionCard {
        Body {
            StatusLine(installed, if (installed) "Google Wallet ${version ?: ""}" else "Google Wallet 未インストール")
            OutlinedButton(onClick = { WalletLauncher.launch(context) }) { Text("Google Wallet を起動") }
        }
    }
}

@Composable
private fun RebootSection(settings: TriggerSettings, resumeTick: Int) {
    val lastBoot = remember(resumeTick) { settings.lastBootAt }
    val lastConnected = remember(resumeTick) { settings.lastServiceConnectedAt }
    SectionHeader("再起動")
    SectionCard {
        Body {
            Mono("最終起動      : ${formatDateTime(lastBoot)}", size = 12)
            Mono("最終サービス接続: ${formatDateTime(lastConnected)}", size = 12)
            if (lastBoot > 0 && lastConnected < lastBoot) {
                StatusLine(false, "再起動後にサービスが復帰していません")
            }
        }
    }
}

@Composable
private fun LogSection() {
    val logs by TriggerMonitor.logs.collectAsStateWithLifecycle()
    SectionHeader("ログ")
    SectionCard {
        Body {
            TextButton(onClick = TriggerMonitor::clearLogs) { Text("クリア") }
            if (logs.isEmpty()) Mono("-")
            logs.take(100).forEach { Mono(it.toString(), size = 11) }
        }
    }
}

private val TIME = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

private fun sourceName(source: Int): String {
    val names = listOf(
        InputDevice.SOURCE_KEYBOARD to "KEYBOARD",
        InputDevice.SOURCE_GAMEPAD to "GAMEPAD",
        InputDevice.SOURCE_DPAD to "DPAD",
        InputDevice.SOURCE_JOYSTICK to "JOYSTICK",
    ).filter { (flag, _) -> source and flag == flag }.map { it.second }
    return "0x${Integer.toHexString(source)} ${names.joinToString("|")}"
}

private fun formatDateTime(time: Long) =
    if (time == 0L) "-" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(time))
