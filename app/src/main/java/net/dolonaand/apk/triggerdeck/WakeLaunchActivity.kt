package net.dolonaand.apk.triggerdeck

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.IntentCompat

/**
 * 画面 OFF / ロック中にアクティビティを起動するための中継。
 * 自身が画面を点灯してロック画面上に表示され、目的の Intent を起動してすぐ終了する。
 */
class WakeLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val target = IntentCompat.getParcelableExtra(intent, EXTRA_TARGET, Intent::class.java)
        if (target != null) {
            runCatching { startActivity(target) }
                .onFailure { TriggerMonitor.log("Wake launch failed: ${it.javaClass.simpleName}") }
        }
        finish()
    }

    companion object {
        private const val EXTRA_TARGET = "target"

        fun intent(context: Context, target: Intent): Intent =
            Intent(context, WakeLaunchActivity::class.java)
                .putExtra(EXTRA_TARGET, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
