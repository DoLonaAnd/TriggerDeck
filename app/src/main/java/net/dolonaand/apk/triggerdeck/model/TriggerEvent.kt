package net.dolonaand.apk.triggerdeck.model

enum class TriggerGesture(val label: String) {
    DOWN("押下"),
    UP("解放"),
    SINGLE("シングル"),
    DOUBLE("ダブル"),
    LONG("長押し"),
}

/**
 * トリガーの入力元。
 * 電源ボタンは OS が先に処理するため KeyEvent を受け取れず、画面 ON/OFF の連続から2回押しだけを検出する。
 */
enum class TriggerSource(val label: String, val gestures: List<TriggerGesture>, val isShoulder: Boolean) {
    RIGHT("右トリガー", listOf(TriggerGesture.SINGLE, TriggerGesture.DOUBLE, TriggerGesture.LONG), true),
    LEFT("左トリガー", listOf(TriggerGesture.SINGLE, TriggerGesture.DOUBLE, TriggerGesture.LONG), true),
    POWER("電源ボタン", listOf(TriggerGesture.DOUBLE), false),
}

/** アプリ内のトリガーイベント。例: RIGHT_TRIGGER_SINGLE, POWER_BUTTON_DOUBLE */
data class TriggerEvent(val source: TriggerSource, val gesture: TriggerGesture) {
    val label: String
        get() = if (source.isShoulder) "${source.name}_TRIGGER_${gesture.name}" else "${source.name}_BUTTON_${gesture.name}"
}

/** Backend から受け取る生のキー入力。決済情報などは一切含まない。 */
data class RawKeyEvent(
    val keyCode: Int,
    val scanCode: Int,
    val action: Int,
    val repeatCount: Int,
    val eventTime: Long,
    val deviceId: Int,
    val deviceName: String?,
    val source: Int,
    val displayId: Int?,
    val backend: String,
)
