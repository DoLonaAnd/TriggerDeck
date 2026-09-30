package net.dolonaand.apk.triggerdeck.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.dolonaand.apk.triggerdeck.BuildConfig
import net.dolonaand.apk.triggerdeck.R
import net.dolonaand.apk.triggerdeck.TriggerMonitor
import net.dolonaand.apk.triggerdeck.TriggerService
import net.dolonaand.apk.triggerdeck.TriggerSettings
import net.dolonaand.apk.triggerdeck.model.TriggerAction
import net.dolonaand.apk.triggerdeck.model.TriggerGesture
import net.dolonaand.apk.triggerdeck.model.TriggerSource

@Composable
fun HomeScreen(settings: TriggerSettings, resumeTick: Int) {
    val context = LocalContext.current
    val connected by TriggerMonitor.serviceConnected.collectAsStateWithLifecycle()
    val serviceEnabled = remember(resumeTick, connected) { TriggerService.isEnabledInSettings(context) }
    val batteryOk = remember(resumeTick) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val apps = rememberLaunchableApps()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!connected || !batteryOk) SetupCard(connected, serviceEnabled, batteryOk)
        StatusCard(settings, connected)
        BindingsSection(settings, apps)
        ExclusionSection(settings, apps)
        TimingSection(settings)
        AdvancedSection(settings)
        AboutSection()
        Text(
            "バージョン ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
    }
}

/** 初回セットアップ。未完了の項目があるときだけ表示する */
@Composable
private fun SetupCard(connected: Boolean, serviceEnabled: Boolean, batteryOk: Boolean) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    var disclosureOpen by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("はじめに設定してください", style = MaterialTheme.typography.titleMedium)
            SetupStep(
                done = connected,
                title = "1. ユーザー補助サービスを有効にする",
                description = when {
                    connected -> "有効です"
                    serviceEnabled -> "有効ですが未接続です。端末を再起動するか、一度オフ→オンにしてください"
                    else -> "トリガーの検出に必要です"
                },
                action = "設定を開く",
                onAction = { disclosureOpen = true },
            )
            SetupStep(
                done = batteryOk,
                title = "2. 電池の最適化を解除する",
                description = if (batteryOk) "解除済みです" else "バックグラウンドで停止されないようにします",
                action = "設定を開く",
                onAction = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
            )
        }
    }
    if (disclosureOpen) {
        AlertDialog(
            onDismissRequest = { disclosureOpen = false },
            title = { Text("ユーザー補助の利用について") },
            text = {
                Text(
                    "このアプリはユーザー補助サービスを使って、次の情報だけを取得します。\n\n" +
                        "・ショルダートリガーのキー入力\n" +
                        "・前面にあるアプリのパッケージ名 (ゲーム中の誤作動防止のため)\n\n" +
                        "画面の内容・入力した文字・決済情報は取得しません。取得した情報は端末の外に送信しません。\n\n" +
                        "次の画面で「$appName」を選び、オンにしてください。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    disclosureOpen = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("同意して設定を開く") }
            },
            dismissButton = { TextButton(onClick = { disclosureOpen = false }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun SetupStep(done: Boolean, title: String, description: String, action: String, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot(done)
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 18.dp))
        if (!done) {
            Button(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}

@Composable
private fun StatusCard(settings: TriggerSettings, connected: Boolean) {
    var enabled by remember { mutableStateOf(settings.enabled) }
    SectionCard {
        SwitchRow(
            title = "トリガーを有効にする",
            subtitle = when {
                !connected -> "ユーザー補助サービスが未接続です"
                enabled -> "動作中"
                else -> "停止中"
            },
            checked = enabled,
        ) {
            enabled = it
            settings.enabled = it
        }
    }
}

@Composable
private fun BindingsSection(settings: TriggerSettings, apps: List<AppEntry>?) {
    // 初期設定に戻したときに各行を読み直すためのキー
    var version by remember { mutableIntStateOf(0) }
    var resetOpen by remember { mutableStateOf(false) }

    SectionHeader("割り当て")
    TriggerSource.entries.forEach { source ->
        SectionCard(title = source.label) {
            source.gestures.forEach { gesture ->
                BindingItem(settings, source, gesture, apps, version)
            }
            if (source == TriggerSource.POWER) {
                Text(
                    "画面が点いた状態で素早く2回押すと実行します。画面が消えているときは、1回押して点灯してから2回押してください。\n" +
                        "ロック中はロック画面上で使えるカメラが起動します。それ以外のアプリはロック解除が必要で、ロックを回避することはできません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
    TextButton(onClick = { resetOpen = true }) { Text("割り当てを初期設定に戻す") }

    if (resetOpen) {
        AlertDialog(
            onDismissRequest = { resetOpen = false },
            title = { Text("初期設定に戻しますか？") },
            text = { Text("左トリガーのダブル → Google Wallet、電源ボタンの2回押し → カメラ、その他はなしに戻します。") },
            confirmButton = {
                TextButton(onClick = {
                    settings.resetBindings()
                    version++
                    resetOpen = false
                }) { Text("戻す") }
            },
            dismissButton = { TextButton(onClick = { resetOpen = false }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun BindingItem(
    settings: TriggerSettings,
    source: TriggerSource,
    gesture: TriggerGesture,
    apps: List<AppEntry>?,
    version: Int,
) {
    var binding by remember(version) { mutableStateOf(settings.binding(source, gesture)) }
    var pickerOpen by remember { mutableStateOf(false) }
    val title = if (source == TriggerSource.POWER) "2回押し" else gesture.label

    ListRow(
        title = title,
        onClick = { pickerOpen = true },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (binding.action == TriggerAction.OPEN_APP) binding.packageName?.let { AppIcon(it, size = 24) }
                ValueText(bindingLabel(binding, apps), highlighted = binding.action != TriggerAction.NONE)
            }
        },
    )
    if (pickerOpen) {
        ActionPickerDialog(
            title = "${source.label}：$title",
            current = binding,
            apps = apps,
            onDismiss = { pickerOpen = false },
            onSelect = {
                pickerOpen = false
                binding = it
                settings.setBinding(source, gesture, it)
            },
        )
    }
}

@Composable
private fun ExclusionSection(settings: TriggerSettings, apps: List<AppEntry>?) {
    var autoGames by remember { mutableStateOf(settings.autoExcludeGames) }
    // 片方のリストへの追加でもう一方から外れることがあるため、変更のたびに両方を読み直す
    var whitelist by remember { mutableStateOf(settings.alwaysEnabledPackages) }
    var blacklist by remember { mutableStateOf(settings.excludedPackages) }
    val reload = {
        whitelist = settings.alwaysEnabledPackages
        blacklist = settings.excludedPackages
    }

    SectionHeader("ゲーム中の誤作動防止")
    SectionCard {
        SwitchRow(
            title = "ゲーム中はショルダートリガーを無効にする",
            subtitle = "トリガーの入力はそのままゲームに渡します",
            checked = autoGames,
        ) {
            autoGames = it
            settings.autoExcludeGames = it
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        AppListBlock(
            title = "常に有効にするアプリ（ホワイトリスト）",
            description = "ゲームと判定されても、ショルダートリガーの割り当てを実行します",
            packages = whitelist,
            otherPackages = blacklist,
            otherNote = "ブラックリストから移動します",
            apps = apps,
        ) {
            settings.alwaysEnabledPackages = it
            reload()
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        AppListBlock(
            title = "除外するアプリ（ブラックリスト）",
            description = "ゲームとして判定されないアプリでも、ショルダートリガーの割り当てを実行しません",
            packages = blacklist,
            otherPackages = whitelist,
            otherNote = "ホワイトリストから移動します",
            apps = apps,
        ) {
            settings.excludedPackages = it
            reload()
        }
    }
}

/** ホワイトリスト・ブラックリストの1つ分。一覧表示と、まとめて選択するダイアログ */
@Composable
private fun AppListBlock(
    title: String,
    description: String,
    packages: Set<String>,
    otherPackages: Set<String>,
    otherNote: String,
    apps: List<AppEntry>?,
    onChange: (Set<String>) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val labelOf = { pkg: String -> apps?.firstOrNull { it.packageName == pkg }?.label ?: pkg }

    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    packages.sortedBy(labelOf).forEach { pkg ->
        ListRow(
            title = labelOf(pkg),
            subtitle = pkg,
            leading = { AppIcon(pkg) },
            trailing = { TextButton(onClick = { onChange(packages - pkg) }) { Text("削除") } },
        )
    }
    if (packages.isEmpty()) {
        Text(
            "登録なし",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    TextButton(onClick = { pickerOpen = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text("アプリを選択")
    }
    if (pickerOpen) {
        MultiAppPickerDialog(
            title = title,
            apps = apps,
            initial = packages,
            notes = otherPackages.associateWith { otherNote },
            onDismiss = { pickerOpen = false },
            onConfirm = {
                pickerOpen = false
                onChange(it)
            },
        )
    }
}

@Composable
private fun AdvancedSection(settings: TriggerSettings) {
    val context = LocalContext.current
    var systemWide by remember { mutableStateOf(settings.systemWideTriggers) }
    var consume by remember { mutableStateOf(settings.consumeTriggerKeys) }

    SectionHeader("詳細設定")
    SectionCard {
        SwitchRow(
            title = "GameSpace 外でもトリガーを使う",
            subtitle = "オフにすると GameSpace でトリガーを有効にしたゲームの中でのみ反応します",
            checked = systemWide,
        ) {
            systemWide = it
            settings.systemWideTriggers = it
        }
        SwitchRow(
            title = "トリガーの入力を他のアプリに渡さない",
            subtitle = "除外中のアプリには常に渡します",
            checked = consume,
        ) {
            consume = it
            settings.consumeTriggerKeys = it
        }
        ListRow(
            title = "アプリ情報",
            subtitle = "自動起動・バックグラウンド実行の許可",
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            },
        )
    }
}

@Composable
private fun TimingSection(settings: TriggerSettings) {
    SectionHeader("判定時間")
    SectionCard {
        TimingRow(
            title = "電源ボタンの2回押し",
            subtitle = "短いほど誤作動しにくく、長いほどゆっくり押しても反応します",
            timing = TriggerSettings.Timing.POWER_DOUBLE,
            initial = settings.powerDoublePressMs,
        ) { settings.powerDoublePressMs = it }
        TimingRow(
            title = "トリガーのダブルタップ",
            subtitle = "ダブルに割り当てがあるとき、シングルはこの時間だけ遅れて実行されます",
            timing = TriggerSettings.Timing.TRIGGER_DOUBLE,
            initial = settings.triggerDoubleTapMs,
        ) { settings.triggerDoubleTapMs = it }
        TimingRow(
            title = "トリガーの長押し",
            subtitle = null,
            timing = TriggerSettings.Timing.LONG_PRESS,
            initial = settings.longPressMs,
        ) { settings.longPressMs = it }
    }
}

@Composable
private fun TimingRow(
    title: String,
    subtitle: String?,
    timing: TriggerSettings.Timing,
    initial: Int,
    onChange: (Int) -> Unit,
) {
    var value by remember { mutableFloatStateOf(initial.toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${value.toInt()} ms", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.toInt()) },
            valueRange = timing.min.toFloat()..timing.max.toFloat(),
            steps = (timing.max - timing.min) / timing.step - 1,
        )
        TextButton(
            onClick = {
                value = timing.default.toFloat()
                onChange(timing.default)
            },
            modifier = Modifier.align(Alignment.End),
        ) { Text("既定 (${timing.default} ms) に戻す") }
    }
}

@Composable
private fun AboutSection() {
    var open by remember { mutableStateOf(false) }
    SectionHeader("このアプリについて")
    SectionCard {
        ListRow(title = "免責事項・プライバシー", subtitle = "非公式ツールとしての注意事項", onClick = { open = true })
        ListRow(title = "注意", subtitle = "アプリを強制停止したり、端末のクリーンアップ機能で停止されると、ユーザー補助がオフになります。動かなくなったら、ユーザー補助を再度オンにしてください。")
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("免責事項・プライバシー") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(DISCLAIMER, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("閉じる") } },
        )
    }
}

private val DISCLAIMER = """
■ 非公式ツールです
本アプリは個人が開発したサードパーティ製ツールです。nubia・REDMAGIC・ASUS・ROG・Google などの各社とは関係がなく、承認や保証も受けていません。記載している製品名・サービス名は各社の商標です。

■ 動作確認について
動作を確認しているのは RED MAGIC 11 Pro (NX809J / Android 16) のみです。他の機種や OS バージョンでは動作しない、または想定外の動作をする可能性があります。

■ OS の非公開機能について
ゲーム外でショルダートリガーを使うため、OS の非公開機能を呼び出しています。OS のアップデートなどで予告なく使えなくなる可能性があります。動作確認していない機種では、内部の呼び出し番号を推測で使うことはしません。

■ 責任の範囲
本アプリは現状のまま提供され、動作や結果を保証しません。本アプリの利用によって生じたいかなる損害についても、開発者は責任を負いません。ご自身の判断と責任でご利用ください。

■ セキュリティ
・ロック画面を回避する機能はありません。ロック中に起動できるのはロック画面用のカメラのみで、それ以外のアプリはロック解除が必要です。
・決済は Google Wallet の中で行われます。本アプリがカード情報や決済情報に触れることはありません。

■ プライバシー
・取得する情報: ショルダートリガーのキー入力、前面にあるアプリのパッケージ名 (ゲーム中の誤作動防止のため)
・取得しない情報: 画面の内容、入力した文字、トリガー以外のキー入力、個人情報、決済情報
・インターネット接続の権限を持たないため、情報を端末の外へ送信できません
・設定は端末内にのみ保存され、バックアップにも含めません
""".trimIndent()
