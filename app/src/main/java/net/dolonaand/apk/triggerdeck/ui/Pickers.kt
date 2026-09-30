package net.dolonaand.apk.triggerdeck.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import net.dolonaand.apk.triggerdeck.model.ActionBinding
import net.dolonaand.apk.triggerdeck.model.TriggerAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val label: String, val packageName: String)

/** ランチャーから起動できるアプリの一覧。null の間は読み込み中 */
@Composable
fun rememberLaunchableApps(): List<AppEntry>? {
    val context = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.Default) { loadLaunchableApps(context) }
    }
    return apps
}

private fun loadLaunchableApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

fun bindingLabel(binding: ActionBinding, apps: List<AppEntry>?): String =
    if (binding.action == TriggerAction.OPEN_APP) {
        val pkg = binding.packageName
        apps?.firstOrNull { it.packageName == pkg }?.label ?: pkg ?: "?"
    } else {
        binding.action.label
    }

/** 割り当ての選択。「機能」タブと「アプリ」タブ (検索付き) から選ぶ */
@Composable
fun ActionPickerDialog(
    title: String,
    current: ActionBinding,
    apps: List<AppEntry>?,
    onDismiss: () -> Unit,
    onSelect: (ActionBinding) -> Unit,
) {
    var tab by remember { mutableIntStateOf(if (current.action == TriggerAction.OPEN_APP) 1 else 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        title = { Text(title) },
        text = {
            Column {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("機能") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("アプリ") })
                }
                Spacer(Modifier.height(8.dp))
                if (tab == 0) {
                    LazyColumn(Modifier.heightIn(max = 440.dp)) {
                        items(TriggerAction.entries.filter { it != TriggerAction.OPEN_APP }) { action ->
                            ChoiceRow(
                                selected = current.action == action,
                                onClick = { onSelect(ActionBinding(action)) },
                            ) { Text(action.label) }
                        }
                    }
                } else {
                    AppList(
                        apps = apps,
                        selectedPackage = current.packageName.takeIf { current.action == TriggerAction.OPEN_APP },
                        onSelect = { onSelect(ActionBinding(TriggerAction.OPEN_APP, it)) },
                    )
                }
            }
        },
    )
}

/**
 * アプリをまとめて選ぶ (ホワイトリスト・ブラックリスト)。
 * 現在の登録内容にチェックを付けた状態で開き、「完了」で選択結果をまとめて返す。
 *
 * @param notes パッケージ名ごとの補足 (もう一方のリストに登録済みなど)
 */
@Composable
fun MultiAppPickerDialog(
    title: String,
    apps: List<AppEntry>?,
    initial: Set<String>,
    notes: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("完了 (${selected.size})") } },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("アプリを検索") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                if (apps == null) {
                    CircularProgressIndicator(Modifier.padding(24.dp).align(Alignment.CenterHorizontally))
                    return@Column
                }
                // 選択中のアプリを先頭に並べる (開いた時点の選択で固定し、チェックのたびに並びが変わらないようにする)
                val ordered = remember(apps) { apps.sortedByDescending { it.packageName in initial } }
                val filtered = remember(query, ordered) {
                    val q = query.trim().lowercase()
                    if (q.isEmpty()) ordered
                    else ordered.filter { q in it.label.lowercase() || q in it.packageName.lowercase() }
                }
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        val toggle = {
                            selected = if (checked) selected - app.packageName else selected + app.packageName
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = toggle)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { toggle() })
                            AppIcon(app.packageName)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    notes[app.packageName] ?: app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (app.packageName in notes) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun AppList(apps: List<AppEntry>?, selectedPackage: String?, onSelect: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("アプリを検索") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        if (apps == null) {
            CircularProgressIndicator(Modifier.padding(24.dp).align(Alignment.CenterHorizontally))
            return@Column
        }
        val filtered = remember(query, apps) {
            val q = query.trim().lowercase()
            if (q.isEmpty()) apps else apps.filter { q in it.label.lowercase() || q in it.packageName.lowercase() }
        }
        LazyColumn(Modifier.heightIn(max = 400.dp)) {
            items(filtered, key = { it.packageName }) { app ->
                ChoiceRow(selected = app.packageName == selectedPackage, onClick = { onSelect(app.packageName) }) {
                    AppIcon(app.packageName)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        content()
    }
}

@Composable
fun AppIcon(packageName: String, size: Int = 36) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(size.dp))
    else Spacer(Modifier.size(size.dp))
}
