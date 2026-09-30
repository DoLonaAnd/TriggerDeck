package net.dolonaand.apk.triggerdeck.backend

import net.dolonaand.apk.triggerdeck.model.RawKeyEvent

/**
 * トリガー入力を取得する抽象インターフェース。
 * listener の戻り値が true の場合、そのキーイベントを消費する。
 */
interface TriggerBackend {
    val name: String
    val isAvailable: Boolean
    fun start(listener: (RawKeyEvent) -> Boolean)
    fun stop()
}
