package net.dolonaand.apk.triggerdeck.backend

import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import net.dolonaand.apk.triggerdeck.model.RawKeyEvent

/**
 * AccessibilityService#onKeyEvent 経由でキー入力を受け取る Backend。
 * TriggerService から onKeyEvent を転送してもらう。
 */
class AccessibilityBackend : TriggerBackend {
    override val name = "Accessibility"
    override val isAvailable = true

    private var listener: ((RawKeyEvent) -> Boolean)? = null

    override fun start(listener: (RawKeyEvent) -> Boolean) {
        this.listener = listener
    }

    override fun stop() {
        listener = null
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val l = listener ?: return false
        val raw = RawKeyEvent(
            keyCode = event.keyCode,
            scanCode = event.scanCode,
            action = event.action,
            repeatCount = event.repeatCount,
            eventTime = event.eventTime,
            deviceId = event.deviceId,
            deviceName = InputDevice.getDevice(event.deviceId)?.name,
            source = event.source,
            displayId = displayIdOf(event),
            backend = name,
        )
        return l(raw)
    }

    private fun displayIdOf(event: KeyEvent): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { KeyEvent::class.java.getMethod("getDisplayId").invoke(event) as Int }.getOrNull()
        } else {
            null
        }
}
