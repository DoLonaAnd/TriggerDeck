package net.dolonaand.apk.triggerdeck

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import net.dolonaand.apk.triggerdeck.model.RawKeyEvent
import net.dolonaand.apk.triggerdeck.model.TriggerEvent
import net.dolonaand.apk.triggerdeck.model.TriggerGesture
import net.dolonaand.apk.triggerdeck.model.TriggerSource

/**
 * ショルダートリガーの生のキー入力をアプリ内イベント (RIGHT_TRIGGER_DOWN / UP / SINGLE / DOUBLE / LONG) に変換する。
 * ダブルタップに割り当てがあるトリガーでは、2回目を待ってからシングルを確定する。
 */
class TriggerDispatcher(
    private val settings: TriggerSettings,
    /** アクションを抑止すべき理由 (除外アプリが前面など)。null なら抑止しない */
    private val suppressReason: () -> String?,
    private val onTriggerEvent: (TriggerEvent) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val pressed = mutableMapOf<TriggerSource, Long>()
    private val longFired = mutableSetOf<TriggerSource>()
    private val longRunnables = mutableMapOf<TriggerSource, Runnable>()
    private val pendingSingles = mutableMapOf<TriggerSource, Runnable>()

    /** @return true ならキーイベントを消費する */
    fun dispatch(raw: RawKeyEvent): Boolean {
        val matched = SHOULDERS.firstOrNull { settings.keyCode(it) == raw.keyCode }
        // 外付けキーボードの文字入力などを残さないよう、リリース版ではトリガーと学習中のキーだけを記録する
        if (matched != null || TriggerMonitor.learning.value != null || BuildConfig.DEBUG) {
            TriggerMonitor.onKeyEvent(raw)
            TriggerMonitor.log(
                "KeyEvent received keyCode=${raw.keyCode}(${KeyEvent.keyCodeToString(raw.keyCode)}) " +
                    "scan=${raw.scanCode} action=${actionName(raw.action)} deviceId=${raw.deviceId} " +
                    "device=${raw.deviceName} source=0x${Integer.toHexString(raw.source)}"
            )
        }

        if (raw.action == KeyEvent.ACTION_DOWN && raw.repeatCount == 0) {
            TriggerMonitor.finishLearning()?.let { source ->
                settings.setKeyCode(source, raw.keyCode)
                TriggerMonitor.log("${source.name} trigger assigned to keyCode=${raw.keyCode}")
                return true
            }
        }

        val source = matched ?: return false
        if (!settings.enabled) return false
        suppressReason()?.let { reason ->
            if (raw.action == KeyEvent.ACTION_DOWN && raw.repeatCount == 0) {
                TriggerMonitor.setTriggerState(source, "抑止: $reason")
                TriggerMonitor.log("${source.name} trigger suppressed: $reason")
            }
            reset(source)
            return false
        }

        when (raw.action) {
            KeyEvent.ACTION_DOWN -> if (raw.repeatCount == 0) onDown(source, raw.eventTime)
            KeyEvent.ACTION_UP -> onUp(source, raw.eventTime)
        }
        return settings.consumeTriggerKeys
    }

    private fun onDown(source: TriggerSource, time: Long) {
        pressed[source] = time
        longFired.remove(source)
        emit(source, TriggerGesture.DOWN)
        val r = Runnable {
            longFired.add(source)
            cancelPendingSingle(source)
            emit(source, TriggerGesture.LONG)
        }
        longRunnables.put(source, r)?.let(handler::removeCallbacks)
        handler.postDelayed(r, settings.longPressMs.toLong())
    }

    private fun onUp(source: TriggerSource, time: Long) {
        longRunnables.remove(source)?.let(handler::removeCallbacks)
        emit(source, TriggerGesture.UP)
        val downAt = pressed.remove(source) ?: return
        val isTap = source !in longFired && time - downAt < settings.longPressMs
        longFired.remove(source)
        if (!isTap) return

        if (cancelPendingSingle(source)) {
            emit(source, TriggerGesture.DOUBLE)
        } else if (settings.hasBinding(source, TriggerGesture.DOUBLE)) {
            val r = Runnable {
                pendingSingles.remove(source)
                emit(source, TriggerGesture.SINGLE)
            }
            pendingSingles[source] = r
            handler.postDelayed(r, settings.triggerDoubleTapMs.toLong())
        } else {
            emit(source, TriggerGesture.SINGLE)
        }
    }

    private fun cancelPendingSingle(source: TriggerSource): Boolean {
        val r = pendingSingles.remove(source) ?: return false
        handler.removeCallbacks(r)
        return true
    }

    private fun reset(source: TriggerSource) {
        longRunnables.remove(source)?.let(handler::removeCallbacks)
        cancelPendingSingle(source)
        pressed.remove(source)
        longFired.remove(source)
    }

    private fun emit(source: TriggerSource, gesture: TriggerGesture) {
        val event = TriggerEvent(source, gesture)
        TriggerMonitor.setTriggerState(source, gesture.label)
        TriggerMonitor.log(event.label)
        onTriggerEvent(event)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        pressed.clear()
        longFired.clear()
        longRunnables.clear()
        pendingSingles.clear()
    }

    companion object {
        private val SHOULDERS = TriggerSource.entries.filter { it.isShoulder }

        fun actionName(action: Int) = when (action) {
            KeyEvent.ACTION_DOWN -> "DOWN"
            KeyEvent.ACTION_UP -> "UP"
            else -> action.toString()
        }
    }
}
