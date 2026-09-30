package net.dolonaand.apk.triggerdeck

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 起動完了を記録する。TriggerService (AccessibilityService) 自体はシステムが再バインドするため、
 * ここでは再起動後にサービスが復帰したかを診断画面で確認できるよう起動時刻を保存する。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        TriggerSettings(context).lastBootAt = System.currentTimeMillis()
        TriggerMonitor.init(context)
        TriggerMonitor.log("BOOT_COMPLETED received")
    }
}
