package net.dolonaand.apk.triggerdeck

import net.dolonaand.apk.triggerdeck.model.ActionBinding
import net.dolonaand.apk.triggerdeck.model.TriggerAction
import net.dolonaand.apk.triggerdeck.model.TriggerEvent

/** トリガーイベントとユーザー設定を対応付ける。DOWN / UP にはアクションを割り当てない。 */
class ActionResolver(private val settings: TriggerSettings) {
    fun resolve(event: TriggerEvent): ActionBinding? {
        if (event.gesture !in event.source.gestures) return null
        return settings.binding(event.source, event.gesture).takeIf { it.action != TriggerAction.NONE }
    }
}
