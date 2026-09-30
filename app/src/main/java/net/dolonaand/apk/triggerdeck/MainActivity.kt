package net.dolonaand.apk.triggerdeck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import net.dolonaand.apk.triggerdeck.ui.AppTheme
import net.dolonaand.apk.triggerdeck.ui.DiagnosticsScreen
import net.dolonaand.apk.triggerdeck.ui.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TriggerMonitor.init(this)
        val settings = TriggerSettings(this)
        setContent {
            AppTheme { AppRoot(settings) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(settings: TriggerSettings) {
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    // 設定画面から戻ってきたときに状態を再取得する
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        TriggerService.refresh()
        resumeTick++
    }
    BackHandler(enabled = showDiagnostics) { showDiagnostics = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showDiagnostics) "診断" else stringResource(R.string.app_name)) },
                navigationIcon = {
                    if (showDiagnostics) {
                        IconButton(onClick = { showDiagnostics = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    }
                },
                actions = {
                    if (!showDiagnostics) TextButton(onClick = { showDiagnostics = true }) { Text("診断") }
                },
            )
        },
    ) { padding ->
        // 画面ごとにスクロール位置を分ける
        key(showDiagnostics) {
            Box(
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                if (showDiagnostics) DiagnosticsScreen(settings, resumeTick) else HomeScreen(settings, resumeTick)
            }
        }
    }
}
