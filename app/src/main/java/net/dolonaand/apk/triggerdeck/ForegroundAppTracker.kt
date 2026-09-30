package net.dolonaand.apk.triggerdeck

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/**
 * 前面アプリのパッケージ名を追跡し、トリガーのアクションを抑止すべきか判定する。
 * FPS などでトリガーを連打しても誤作動しないよう、ゲームや除外アプリでは抑止する。
 */
class ForegroundAppTracker(
    private val context: Context,
    private val settings: TriggerSettings,
    private val onChanged: (String) -> Unit,
) {
    var foregroundPackage: String? = null
        private set

    private val gameCache = mutableMapOf<String, Boolean>()

    /**
     * GameSpace のフローティングウィンドウ (cn.nubia.gameassist 等) でも TYPE_WINDOW_STATE_CHANGED が届くため、
     * 原則として Activity への遷移だけを前面アプリの変化として扱う。
     * ただしゲームは起動直後にスプラッシュをダイアログで出すことがあるため (荒野行動など)、
     * ゲーム・リスト登録済みアプリのウィンドウは Activity でなくても前面アプリとみなす。
     */
    fun onWindowStateChanged(pkg: String, className: String?) {
        if (pkg in IGNORED_PACKAGES || isInputMethod(pkg) || pkg == foregroundPackage) return
        val isActivityWindow = className != null && isActivity(pkg, className)
        if (!isActivityWindow && !isGameOrListed(pkg)) {
            TriggerMonitor.log("Window ignored: $pkg/${className ?: "?"}")
            return
        }
        foregroundPackage = pkg
        TriggerMonitor.log("Foreground: $pkg/$className game=${isGame(pkg)}")
        TriggerMonitor.setForegroundPackage(pkg)
        onChanged(pkg)
    }

    /**
     * 優先順位: ホワイトリスト > ブラックリスト > ゲーム自動判定。
     * 前面アプリの判定が間に合わない場合に備え、REDMAGIC の GameSpace がゲーム中を示す
     * nubia_game_scene=1 のときもゲーム中として扱う (他機種ではこの設定が存在せず常に 0)。
     */
    fun suppressReason(): String? {
        val pkg = foregroundPackage
        if (pkg != null && pkg in settings.alwaysEnabledPackages) return null
        if (pkg != null && pkg in settings.excludedPackages) return "除外アプリ $pkg"
        if (!settings.autoExcludeGames) return null
        if (pkg != null && isGame(pkg)) return "ゲーム $pkg"
        if (isGameSceneActive()) {
            val scenePkg = Settings.System.getString(context.contentResolver, GAME_SCENE_PACKAGE_KEY)
            if (scenePkg != null && scenePkg in settings.alwaysEnabledPackages) return null
            return "GameSpace のゲーム中 ${scenePkg ?: ""}".trim()
        }
        return null
    }

    private fun isGameSceneActive(): Boolean =
        runCatching { Settings.Global.getInt(context.contentResolver, GAME_SCENE_KEY, 0) == 1 }.getOrDefault(false)

    private fun isGameOrListed(pkg: String): Boolean =
        pkg in settings.alwaysEnabledPackages || pkg in settings.excludedPackages || isGame(pkg)

    private fun isActivity(pkg: String, className: String): Boolean = try {
        context.packageManager.getActivityInfo(ComponentName(pkg, className), 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun isGame(pkg: String): Boolean = gameCache.getOrPut(pkg) { isGame(context, pkg) }

    private fun isInputMethod(pkg: String): Boolean =
        context.getSystemService(InputMethodManager::class.java)
            ?.inputMethodList?.any { it.packageName == pkg } == true

    companion object {
        private val IGNORED_PACKAGES = setOf("com.android.systemui", "android")
        const val GAME_SCENE_KEY = "nubia_game_scene"
        private const val GAME_SCENE_PACKAGE_KEY = "nubia_game_scene_package_name"

        fun isGame(context: Context, pkg: String): Boolean = try {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            @Suppress("DEPRECATION")
            info.category == ApplicationInfo.CATEGORY_GAME || info.flags and ApplicationInfo.FLAG_IS_GAME != 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
