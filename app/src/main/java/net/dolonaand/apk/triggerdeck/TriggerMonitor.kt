package net.dolonaand.apk.triggerdeck

import android.content.Context
import android.util.Log
import net.dolonaand.apk.triggerdeck.model.RawKeyEvent
import net.dolonaand.apk.triggerdeck.model.TriggerSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 診断画面に表示する状態をプロセス内で共有する。
 * 記録するのはトリガー入力の種類と時刻のみ。
 */
object TriggerMonitor {
    private const val TAG = "TriggerDeck"
    private const val MAX_LOG = 200
    private const val LOG_FILE = "diag.log"
    private const val MAX_FILE_BYTES = 512 * 1024

    data class LogEntry(val time: Long, val message: String) {
        override fun toString() = "${TIME_FORMAT.format(Date(time))}  $message"
    }

    private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val FILE_TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _lastKeyEvent = MutableStateFlow<Pair<RawKeyEvent, Long>?>(null)
    val lastKeyEvent: StateFlow<Pair<RawKeyEvent, Long>?> = _lastKeyEvent.asStateFlow()

    private val _triggerState = MutableStateFlow<Map<TriggerSource, String>>(emptyMap())
    val triggerState: StateFlow<Map<TriggerSource, String>> = _triggerState.asStateFlow()

    private val _foregroundPackage = MutableStateFlow<String?>(null)
    val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()

    /** 学習モード: 次に押されたキーをこのトリガーに割り当てる */
    private val _learning = MutableStateFlow<TriggerSource?>(null)
    val learning: StateFlow<TriggerSource?> = _learning.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
        log(if (connected) "TriggerBackend initialized (Accessibility)" else "TriggerService disconnected")
    }

    fun onKeyEvent(event: RawKeyEvent) {
        _lastKeyEvent.value = event to System.currentTimeMillis()
    }

    fun setTriggerState(source: TriggerSource, state: String) {
        _triggerState.update { it + (source to "$state  (${TIME_FORMAT.format(Date())})") }
    }

    fun setForegroundPackage(pkg: String) {
        _foregroundPackage.value = pkg
    }

    fun startLearning(source: TriggerSource?) {
        _learning.value = source
    }

    fun finishLearning(): TriggerSource? = _learning.getAndUpdateNull()

    private var logFile: File? = null

    /** デバッグビルドのみ、診断ログをアプリ内ファイルにも書き出す (adb shell run-as で読める) */
    fun init(context: Context) {
        if (BuildConfig.DEBUG && logFile == null) logFile = File(context.filesDir, LOG_FILE)
    }

    fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
        val entry = LogEntry(System.currentTimeMillis(), message)
        _logs.update { (listOf(entry) + it).take(MAX_LOG) }
        logFile?.let { f ->
            runCatching {
                if (f.length() > MAX_FILE_BYTES) f.writeText("")
                f.appendText("${FILE_TIME_FORMAT.format(Date(entry.time))}  $message\n")
            }
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun <T> MutableStateFlow<T?>.getAndUpdateNull(): T? {
        while (true) {
            val current = value
            if (compareAndSet(current, null)) return current
        }
    }
}
