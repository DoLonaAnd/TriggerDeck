package net.dolonaand.apk.triggerdeck.backend

import net.dolonaand.apk.triggerdeck.model.RawKeyEvent

/**
 * REDMAGIC 固有の入力デバイス (例: nubia_tgk_aw_sar, KEY_F7/KEY_F8) を直接読む Backend。
 *
 * 通常アプリの権限では /dev/input/event* を読めない可能性が高いため、初期実装では無効。
 * AccessibilityBackend でトリガーを取得できなかった場合にのみ実装を検討する (設計書 Phase 6)。
 */
class NativeInputBackend : TriggerBackend {
    override val name = "NativeInput"
    override val isAvailable = false

    override fun start(listener: (RawKeyEvent) -> Boolean) = Unit

    override fun stop() = Unit
}
