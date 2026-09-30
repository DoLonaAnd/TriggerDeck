package net.dolonaand.apk.triggerdeck.model

enum class TriggerAction(val label: String, val launchesActivity: Boolean = false) {
    NONE("なし"),
    OPEN_GOOGLE_WALLET("Google Wallet", launchesActivity = true),
    OPEN_CAMERA("カメラ", launchesActivity = true),
    OPEN_APP("アプリを起動", launchesActivity = true),
    FLASHLIGHT("ライト切替"),
    SCREENSHOT("スクリーンショット"),
    MEDIA_PLAY_PAUSE("再生/一時停止"),
    NOTIFICATIONS("通知パネル"),
    QUICK_SETTINGS("クイック設定"),
    RECENTS("最近のアプリ"),
    HOME("ホーム"),
    BACK("戻る"),
    LOCK_SCREEN("画面ロック"),
}

/** トリガー操作に割り当てたアクション。OPEN_APP のときだけ packageName を使う */
data class ActionBinding(val action: TriggerAction, val packageName: String? = null) {
    companion object {
        val NONE = ActionBinding(TriggerAction.NONE)
    }
}
