package net.dolonaand.apk.triggerdeck.backend

import android.content.Intent
import android.os.SystemClock
import net.dolonaand.apk.triggerdeck.TriggerMonitor

/**
 * 電源ボタンの2回押しを検出する。
 *
 * 電源キーは OS (PhoneWindowManager) が先に処理するため AccessibilityService には届かない。
 * 代わりに、電源ボタンを押すたびに画面が切り替わることを利用し、
 * SCREEN_OFF → SCREEN_ON (画面 ON から2回押し) または SCREEN_ON → SCREEN_OFF (画面 OFF から2回押し)
 * が短時間に続いたら2回押しとみなす。
 */
class PowerButtonBackend(
    /** 2回押しとみなす画面切り替えの最大間隔 (ms) */
    private val thresholdMs: () -> Int,
    private val onDoublePress: (screenWasOff: Boolean) -> Unit,
) {
    private var lastAction: String? = null
    private var lastAt = 0L

    fun onScreenIntent(action: String) {
        if (action != Intent.ACTION_SCREEN_ON && action != Intent.ACTION_SCREEN_OFF) return
        val now = SystemClock.elapsedRealtime()
        val interval = now - lastAt
        val isToggle = lastAction != null && lastAction != action
        if (isToggle && interval <= thresholdMs()) {
            TriggerMonitor.log("Power double press detected (${action.substringAfterLast('_')} after ${interval}ms)")
            // 2回目で画面が OFF になった = 画面 OFF の状態から2回押した
            onDoublePress(action == Intent.ACTION_SCREEN_OFF)
            lastAction = null
            lastAt = 0L
            return
        }
        if (isToggle && interval < 2000) TriggerMonitor.log("Power screen toggle ignored (${interval}ms > ${thresholdMs()}ms)")
        lastAction = action
        lastAt = now
    }
}
