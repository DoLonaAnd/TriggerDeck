package net.dolonaand.apk.triggerdeck

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.IBinder
import android.os.Parcel

/**
 * REDMAGIC OS の IInputManager 拡張 API でショルダートリガー (TGK) を制御する。
 *
 * REDMAGIC OS はゲーム外ではネイティブ入力層で F7/F8 を握りつぶす (setConsumeTgkKey(true))。
 * 以下を呼ぶことで、トリガーのキーイベントが AccessibilityService まで届くようになる。
 *
 *   enableLeftTgkDrive(true) / enableRightTgkDrive(true)      トリガーのセンサーを駆動 (sysfs へ書き込み)
 *   setLeftGameKeyEnable(true) / setRightGameKeyEnable(true)  入力層でトリガーを有効化
 *   setConsumeTgkKey(false)                                   キーの握りつぶしを解除
 *
 * いずれも system_server 側で権限チェックがないため、通常アプリから呼べる。
 * システムはゲームの開始・終了や画面 ON/OFF で状態を戻すため、TriggerService が適宜再適用する。
 *
 * トランザクション番号は機種・OS バージョンで変わりうる。番号がずれると無関係な別の API を
 * 呼んでしまうため、名前で解決できないときの決め打ちは動作確認済みの機種・OS に限る。
 */
class TgkController(private val context: Context) {
    private val inputManager = context.getSystemService(InputManager::class.java)

    fun apply(reason: String) {
        val results = listOf(
            call("enableLeftTgkDrive", true),
            call("enableRightTgkDrive", true),
            call("setLeftGameKeyEnable", true),
            call("setRightGameKeyEnable", true),
            call("setConsumeTgkKey", false),
        )
        val ok = results.all { it }
        if (ok != lastOk) {
            TriggerMonitor.log("TGK apply ($reason): ${if (ok) "OK via $lastMethod" else "FAILED"}")
            lastOk = ok
        }
    }

    /** InputManager の非公開メソッドを直接呼び、ダメならバインダーを直接叩く */
    private fun call(name: String, value: Boolean): Boolean =
        callReflection(name, value) || callBinder(name, value)

    private fun callReflection(name: String, value: Boolean): Boolean = try {
        InputManager::class.java.getMethod(name, Boolean::class.javaPrimitiveType).invoke(inputManager, value)
        lastMethod = "reflection"
        true
    } catch (_: ReflectiveOperationException) {
        false
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun callBinder(name: String, value: Boolean): Boolean {
        val binder = inputBinder() ?: return false
        val code = transactionCode(name) ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(if (value) 1 else 0)
            binder.transact(code, data, reply, 0)
            reply.readException()
            lastMethod = "binder"
            true
        } catch (e: Exception) {
            TriggerMonitor.log("TGK $name failed: ${e.javaClass.simpleName} ${e.message}")
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun inputBinder(): IBinder? = cachedBinder ?: runCatching {
        Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, Context.INPUT_SERVICE) as IBinder
    }.getOrNull()?.also { cachedBinder = it }

    /** IInputManager$Stub.TRANSACTION_xxx を読む。読めなければ動作確認済みの機種・OS に限り決め打ちの値を使う */
    @SuppressLint("PrivateApi")
    private fun transactionCode(name: String): Int? = runCatching {
        Class.forName("$DESCRIPTOR\$Stub").getDeclaredField("TRANSACTION_$name")
            .apply { isAccessible = true }.getInt(null)
    }.getOrNull() ?: FALLBACK_CODES[name]?.takeIf { isVerifiedDevice }

    companion object {
        private const val DESCRIPTOR = "android.hardware.input.IInputManager"

        /** 決め打ちの番号を確認した機種: RED MAGIC 11 Pro (NX809J) / Android 16 */
        val isVerifiedDevice: Boolean
            get() = Build.MODEL == "NX809J" && Build.VERSION.SDK_INT == Build.VERSION_CODES.BAKLAVA

        private val FALLBACK_CODES = mapOf(
            "setConsumeTgkKey" to 101,
            "setLeftGameKeyEnable" to 102,
            "setRightGameKeyEnable" to 103,
            "enableLeftTgkDrive" to 147,
            "enableRightTgkDrive" to 148,
        )

        private var cachedBinder: IBinder? = null
        private var lastMethod = "-"
        private var lastOk: Boolean? = null
    }
}
