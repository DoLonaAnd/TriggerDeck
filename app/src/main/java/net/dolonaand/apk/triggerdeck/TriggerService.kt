package net.dolonaand.apk.triggerdeck

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import net.dolonaand.apk.triggerdeck.backend.AccessibilityBackend
import net.dolonaand.apk.triggerdeck.backend.PowerButtonBackend
import net.dolonaand.apk.triggerdeck.model.TriggerEvent
import net.dolonaand.apk.triggerdeck.model.TriggerGesture
import net.dolonaand.apk.triggerdeck.model.TriggerSource

/**
 * バックグラウンドでトリガーを監視するサービス。
 * システムにバインドされる AccessibilityService として動作し、入力取得は Backend に委譲する。
 * 有効化済みのアクセシビリティサービスは端末再起動後にシステムが自動で再バインドする。
 */
class TriggerService : AccessibilityService() {
    private lateinit var settings: TriggerSettings
    private lateinit var dispatcher: TriggerDispatcher
    private lateinit var resolver: ActionResolver
    private lateinit var executor: ActionExecutor
    private lateinit var foreground: ForegroundAppTracker
    private lateinit var tgk: TgkController
    private val backend = AccessibilityBackend()
    private val powerBackend = PowerButtonBackend({ settings.powerDoublePressMs }, ::onPowerDoublePress)
    private val handler = Handler(Looper.getMainLooper())

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == TriggerSettings.KEY_ENABLED || key == TriggerSettings.KEY_SYSTEM_WIDE) reapplyTgk("settings")
    }

    /** 電源ボタン検出と、画面 ON / ロック解除後のトリガー再有効化 */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            powerBackend.onScreenIntent(action)
            if (action != Intent.ACTION_SCREEN_OFF) reapplyTgk(action.substringAfterLast('.'))
        }
    }

    /** GameSpace のゲーム開始・終了でシステムがトリガー状態を切り替えるため、その後に再適用する */
    private val gameSceneObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = reapplyTgk("game_scene")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        TriggerMonitor.init(this)
        settings = TriggerSettings(this)
        resolver = ActionResolver(settings)
        foreground = ForegroundAppTracker(this, settings) { reapplyTgk("foreground") }
        executor = ActionExecutor(this) { foreground.foregroundPackage }
        tgk = TgkController(this)
        dispatcher = TriggerDispatcher(settings, foreground::suppressReason, ::onTriggerEvent)
        backend.start(dispatcher::dispatch)
        settings.registerListener(prefsListener)
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(ForegroundAppTracker.GAME_SCENE_KEY), false, gameSceneObserver,
        )
        reapplyTgk("connected")
        settings.lastServiceConnectedAt = System.currentTimeMillis()
        instance = this
        TriggerMonitor.setServiceConnected(true)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = backend.onKeyEvent(event)

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && ::foreground.isInitialized) {
            val pkg = event.packageName?.toString() ?: return
            foreground.onWindowStateChanged(pkg, event.className?.toString())
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    /**
     * トリガーを有効化する。ゲーム・除外アプリが前面のときは GameSpace 側の制御に任せて触らない。
     * システムの切り替え処理と競合しないよう少し遅らせて適用する。
     */
    private fun reapplyTgk(reason: String) {
        if (!::tgk.isInitialized) return
        handler.removeCallbacks(applyRunnable)
        pendingReason = reason
        handler.postDelayed(applyRunnable, APPLY_DELAY_MS)
    }

    private var pendingReason = ""
    private val applyRunnable = Runnable {
        if (settings.enabled && settings.systemWideTriggers && foreground.suppressReason() == null) {
            tgk.apply(pendingReason)
        }
    }

    private fun onPowerDoublePress(screenWasOff: Boolean) {
        if (!settings.enabled) return
        val event = TriggerEvent(TriggerSource.POWER, TriggerGesture.DOUBLE)
        TriggerMonitor.setTriggerState(TriggerSource.POWER, TriggerGesture.DOUBLE.label)
        TriggerMonitor.log("${event.label} (screenWasOff=$screenWasOff)")
        onTriggerEvent(event)
    }

    private fun shutdown() {
        if (instance === this) instance = null
        backend.stop()
        handler.removeCallbacksAndMessages(null)
        if (::dispatcher.isInitialized) dispatcher.release()
        if (::executor.isInitialized) executor.release()
        if (::settings.isInitialized) {
            settings.unregisterListener(prefsListener)
            runCatching { unregisterReceiver(screenReceiver) }
            contentResolver.unregisterContentObserver(gameSceneObserver)
        }
        if (TriggerMonitor.serviceConnected.value) TriggerMonitor.setServiceConnected(false)
    }

    private fun onTriggerEvent(event: TriggerEvent) {
        resolver.resolve(event)?.let(executor::execute)
    }

    companion object {
        private const val APPLY_DELAY_MS = 300L
        private var instance: TriggerService? = null

        /** 画面から戻ったときにトリガーを再有効化する */
        fun refresh() {
            instance?.reapplyTgk("app")
        }

        fun isEnabledInSettings(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, TriggerService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
