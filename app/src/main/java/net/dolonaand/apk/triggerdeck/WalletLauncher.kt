package net.dolonaand.apk.triggerdeck

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast

/** Google Wallet を起動する。Wallet 内部の情報には一切アクセスしない。 */
object WalletLauncher {
    const val WALLET_PACKAGE = "com.google.android.apps.walletnfcrel"

    enum class Result { LAUNCHED, NOT_INSTALLED, FAILED }

    fun isInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(WALLET_PACKAGE, 0) }.isSuccess

    fun versionName(context: Context): String? =
        try {
            context.packageManager.getPackageInfo(WALLET_PACKAGE, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    fun launchIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(WALLET_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    fun launch(context: Context): Result {
        TriggerMonitor.log("Wallet launch requested")
        val intent = launchIntent(context)
        if (intent == null) {
            TriggerMonitor.log("Wallet not installed")
            Toast.makeText(context, R.string.wallet_not_installed, Toast.LENGTH_SHORT).show()
            return Result.NOT_INSTALLED
        }
        return try {
            context.startActivity(intent)
            TriggerMonitor.log("Wallet launched")
            Result.LAUNCHED
        } catch (e: Exception) {
            TriggerMonitor.log("Wallet launch failed: ${e.javaClass.simpleName}")
            Toast.makeText(context, R.string.wallet_launch_failed, Toast.LENGTH_SHORT).show()
            Result.FAILED
        }
    }
}
