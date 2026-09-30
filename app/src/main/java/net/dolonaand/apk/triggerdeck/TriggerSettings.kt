package net.dolonaand.apk.triggerdeck

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.core.content.edit
import net.dolonaand.apk.triggerdeck.model.ActionBinding
import net.dolonaand.apk.triggerdeck.model.TriggerAction
import net.dolonaand.apk.triggerdeck.model.TriggerGesture
import net.dolonaand.apk.triggerdeck.model.TriggerSource

/** ユーザー設定。SharedPreferences に保存する。 */
class TriggerSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("trigger_settings", Context.MODE_PRIVATE)

    init {
        // 旧バージョンの「トリガーごとに1つ」形式の設定を削除する
        val legacy = prefs.all.keys.filter { it.startsWith("action_") || it.startsWith("gesture_") }
        if (legacy.isNotEmpty()) prefs.edit { legacy.forEach(::remove) }
    }

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    /** トリガーキーを消費して前面アプリに渡さないか */
    var consumeTriggerKeys: Boolean
        get() = prefs.getBoolean(KEY_CONSUME, false)
        set(value) = prefs.edit { putBoolean(KEY_CONSUME, value) }

    /** REDMAGIC の入力 API でトリガーを有効化し、GameSpace 外でも使えるようにする */
    var systemWideTriggers: Boolean
        get() = prefs.getBoolean(KEY_SYSTEM_WIDE, true)
        set(value) = prefs.edit { putBoolean(KEY_SYSTEM_WIDE, value) }

    /** ゲームアプリ (カテゴリ=ゲーム) が前面のときはショルダートリガーのアクションを実行しない */
    var autoExcludeGames: Boolean
        get() = prefs.getBoolean(KEY_AUTO_EXCLUDE_GAMES, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_EXCLUDE_GAMES, value) }

    /** ショルダートリガーを無効にするアプリ (ブラックリスト) */
    var excludedPackages: Set<String>
        get() = prefs.getStringSet(KEY_EXCLUDED, emptySet())!!.toSet()
        set(value) = prefs.edit {
            putStringSet(KEY_EXCLUDED, value)
            // 同じアプリを両方のリストに入れない
            putStringSet(KEY_ALWAYS_ENABLED, alwaysEnabledPackages - value)
        }

    /** ゲームと判定されてもショルダートリガーを常に有効にするアプリ (ホワイトリスト) */
    var alwaysEnabledPackages: Set<String>
        get() = prefs.getStringSet(KEY_ALWAYS_ENABLED, emptySet())!!.toSet()
        set(value) = prefs.edit {
            putStringSet(KEY_ALWAYS_ENABLED, value)
            putStringSet(KEY_EXCLUDED, excludedPackages - value)
        }

    /** 電源ボタン2回押しの判定時間 (画面 OFF→ON の間隔) */
    var powerDoublePressMs: Int
        get() = prefs.getInt(KEY_POWER_DOUBLE_MS, Timing.POWER_DOUBLE.default)
        set(value) = prefs.edit { putInt(KEY_POWER_DOUBLE_MS, Timing.POWER_DOUBLE.clamp(value)) }

    /** ショルダートリガーのダブルタップ判定時間 */
    var triggerDoubleTapMs: Int
        get() = prefs.getInt(KEY_TRIGGER_DOUBLE_MS, Timing.TRIGGER_DOUBLE.default)
        set(value) = prefs.edit { putInt(KEY_TRIGGER_DOUBLE_MS, Timing.TRIGGER_DOUBLE.clamp(value)) }

    /** ショルダートリガーの長押し判定時間 */
    var longPressMs: Int
        get() = prefs.getInt(KEY_LONG_PRESS_MS, Timing.LONG_PRESS.default)
        set(value) = prefs.edit { putInt(KEY_LONG_PRESS_MS, Timing.LONG_PRESS.clamp(value)) }

    /** 判定時間の既定値と設定範囲 (ms) */
    enum class Timing(val default: Int, val min: Int, val max: Int, val step: Int) {
        POWER_DOUBLE(400, 150, 800, 25),
        TRIGGER_DOUBLE(300, 150, 600, 25),
        LONG_PRESS(500, 300, 1500, 50),
        ;

        fun clamp(value: Int) = value.coerceIn(min, max)
    }

    fun keyCode(source: TriggerSource): Int =
        prefs.getInt(keyCodeKey(source), defaultKeyCode(source))

    fun setKeyCode(source: TriggerSource, keyCode: Int) =
        prefs.edit { putInt(keyCodeKey(source), keyCode) }

    fun binding(source: TriggerSource, gesture: TriggerGesture): ActionBinding {
        val key = bindingKey(source, gesture)
        val action = prefs.getString(key, null)
            ?.let { runCatching { TriggerAction.valueOf(it) }.getOrNull() }
            ?: return defaultBinding(source, gesture)
        return ActionBinding(action, prefs.getString("${key}_package", null))
    }

    fun setBinding(source: TriggerSource, gesture: TriggerGesture, binding: ActionBinding) {
        val key = bindingKey(source, gesture)
        prefs.edit {
            putString(key, binding.action.name)
            putString("${key}_package", binding.packageName)
        }
    }

    /** すべての割り当てを初期設定に戻す */
    fun resetBindings() {
        val keys = prefs.all.keys.filter { it.startsWith("binding_") }
        prefs.edit { keys.forEach(::remove) }
    }

    fun hasBinding(source: TriggerSource, gesture: TriggerGesture): Boolean =
        binding(source, gesture).action != TriggerAction.NONE

    var lastBootAt: Long
        get() = prefs.getLong(KEY_LAST_BOOT, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_BOOT, value) }

    var lastServiceConnectedAt: Long
        get() = prefs.getLong(KEY_LAST_CONNECTED, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_CONNECTED, value) }

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_SYSTEM_WIDE = "system_wide_triggers"
        private const val KEY_CONSUME = "consume_trigger_keys"
        private const val KEY_AUTO_EXCLUDE_GAMES = "auto_exclude_games"
        private const val KEY_EXCLUDED = "excluded_packages"
        private const val KEY_ALWAYS_ENABLED = "always_enabled_packages"
        private const val KEY_POWER_DOUBLE_MS = "power_double_press_ms"
        private const val KEY_TRIGGER_DOUBLE_MS = "trigger_double_tap_ms"
        private const val KEY_LONG_PRESS_MS = "long_press_ms"
        private const val KEY_LAST_BOOT = "last_boot_at"
        private const val KEY_LAST_CONNECTED = "last_service_connected_at"

        private fun keyCodeKey(source: TriggerSource) = "key_code_${source.name}"
        private fun bindingKey(source: TriggerSource, gesture: TriggerGesture) = "binding_${source.name}_${gesture.name}"

        /** NX809J で確認した割り当て (左=KEY_F7, 右=KEY_F8) */
        fun defaultKeyCode(source: TriggerSource): Int = when (source) {
            TriggerSource.LEFT -> KeyEvent.KEYCODE_F7
            TriggerSource.RIGHT -> KeyEvent.KEYCODE_F8
            TriggerSource.POWER -> KeyEvent.KEYCODE_POWER
        }

        fun defaultBinding(source: TriggerSource, gesture: TriggerGesture): ActionBinding = when {
            source == TriggerSource.LEFT && gesture == TriggerGesture.DOUBLE -> ActionBinding(TriggerAction.OPEN_GOOGLE_WALLET)
            source == TriggerSource.POWER && gesture == TriggerGesture.DOUBLE -> ActionBinding(TriggerAction.OPEN_CAMERA)
            else -> ActionBinding.NONE
        }
    }
}
