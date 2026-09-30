package net.dolonaand.apk.triggerdeck

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import android.widget.Toast
import net.dolonaand.apk.triggerdeck.model.ActionBinding
import net.dolonaand.apk.triggerdeck.model.TriggerAction

/** 割り当てられたアクションを実行する。 */
class ActionExecutor(
    private val service: AccessibilityService,
    private val foregroundPackage: () -> String?,
) {
    private val powerManager = service.getSystemService(PowerManager::class.java)
    private val keyguard = service.getSystemService(KeyguardManager::class.java)
    private val cameraManager = service.getSystemService(CameraManager::class.java)
    private val audioManager = service.getSystemService(AudioManager::class.java)
    private var torchOn = false
    private val lastLaunchAt = mutableMapOf<ActionBinding, Long>()

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) torchOn = enabled
        }
    }
    private val torchCameraId: String? by lazy {
        runCatching {
            cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    init {
        runCatching { cameraManager.registerTorchCallback(torchCallback, null) }
    }

    fun release() {
        runCatching { cameraManager.unregisterTorchCallback(torchCallback) }
    }

    fun execute(binding: ActionBinding) {
        TriggerMonitor.log("Action: ${binding.action.name}${binding.packageName?.let { " ($it)" } ?: ""}")
        if (binding.action.launchesActivity && inCooldown(binding)) {
            TriggerMonitor.log("Action skipped (cooldown)")
            return
        }
        when (binding.action) {
            TriggerAction.NONE -> Unit
            TriggerAction.OPEN_GOOGLE_WALLET -> launchWallet()
            TriggerAction.OPEN_CAMERA -> launchCamera()
            TriggerAction.OPEN_APP -> binding.packageName?.let(::launchApp)
            TriggerAction.FLASHLIGHT -> toggleTorch()
            TriggerAction.SCREENSHOT -> global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            TriggerAction.MEDIA_PLAY_PAUSE -> mediaPlayPause()
            TriggerAction.NOTIFICATIONS -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            TriggerAction.QUICK_SETTINGS -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            TriggerAction.RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
            TriggerAction.HOME -> global(AccessibilityService.GLOBAL_ACTION_HOME)
            TriggerAction.BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK)
            TriggerAction.LOCK_SCREEN -> global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    /** 連打で何度も起動しないよう、同じ起動アクションは一定時間内に1回だけ実行する */
    private fun inCooldown(binding: ActionBinding): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastLaunchAt[binding]
        if (last != null && now - last < LAUNCH_COOLDOWN_MS) return true
        lastLaunchAt[binding] = now
        return false
    }

    private fun launchWallet() {
        if (foregroundPackage() == WalletLauncher.WALLET_PACKAGE) {
            TriggerMonitor.log("Wallet already in foreground, skipped")
            return
        }
        if (!powerManager.isInteractive || keyguard.isKeyguardLocked) {
            // Wallet はロック解除が必要。中継アクティビティで画面を点けてから起動する
            WalletLauncher.launchIntent(service)?.let(::startFromLockedOrOff) ?: WalletLauncher.launch(service)
            return
        }
        WalletLauncher.launch(service)
    }

    /** ロック中はロック画面上で使えるセキュアカメラを起動する */
    private fun launchCamera() {
        val secure = keyguard.isKeyguardLocked || !powerManager.isInteractive
        val intent = Intent(
            if (secure) MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (secure) startFromLockedOrOff(intent) else start(intent)
    }

    private fun launchApp(pkg: String) {
        if (foregroundPackage() == pkg) {
            TriggerMonitor.log("$pkg already in foreground, skipped")
            return
        }
        val intent = service.packageManager.getLaunchIntentForPackage(pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (intent == null) {
            TriggerMonitor.log("App not found: $pkg")
            Toast.makeText(service, service.getString(R.string.app_not_found, pkg), Toast.LENGTH_SHORT).show()
            return
        }
        if (!powerManager.isInteractive || keyguard.isKeyguardLocked) startFromLockedOrOff(intent) else start(intent)
    }

    private fun start(intent: Intent, label: String = describe(intent)) {
        runCatching { service.startActivity(intent) }
            .onSuccess { TriggerMonitor.log("Launched $label") }
            .onFailure { TriggerMonitor.log("Launch failed: ${it.javaClass.simpleName}") }
    }

    private fun startFromLockedOrOff(intent: Intent) =
        start(WakeLaunchActivity.intent(service, intent), "${describe(intent)} via WakeLaunchActivity")

    private fun describe(intent: Intent): String =
        intent.component?.flattenToShortString() ?: intent.action ?: intent.`package` ?: "?"

    private fun toggleTorch() {
        val id = torchCameraId ?: run {
            TriggerMonitor.log("No flash available")
            return
        }
        runCatching { cameraManager.setTorchMode(id, !torchOn) }
            .onFailure { TriggerMonitor.log("Torch failed: ${it.javaClass.simpleName}") }
    }

    private fun mediaPlayPause() {
        listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach {
            audioManager.dispatchMediaKeyEvent(KeyEvent(it, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        }
    }

    private fun global(action: Int) {
        if (!service.performGlobalAction(action)) TriggerMonitor.log("Global action $action failed")
    }

    companion object {
        private const val LAUNCH_COOLDOWN_MS = 1500L
    }
}
