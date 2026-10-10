package com.workoutrec.diag

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.core.content.FileProvider

/** What decides whether Android lets background sync run (quickstart-results.md issue 1). */
object DeviceState {

    fun describe(context: Context): String =
        "network: ${network(context)}, idle: ${idle(context)}, power saver: ${powerSaver(context)}, standby: ${standby(context)}"

    private fun network(context: Context): String = try {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity?.activeNetwork?.let(connectivity::getNetworkCapabilities)
        if (caps == null) {
            "none"
        } else {
            network(
                internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            )
        }
    } catch (e: SecurityException) {
        "unknown"
    }

    fun network(internet: Boolean, validated: Boolean, vpn: Boolean): String {
        val state = when {
            !internet -> "no internet"
            validated -> "internet (validated)"
            else -> "internet (not validated)"
        }
        return if (vpn) "$state, vpn" else state
    }

    private fun idle(context: Context): Boolean? = context.getSystemService(PowerManager::class.java)?.isDeviceIdleMode

    private fun powerSaver(context: Context): Boolean? = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode

    private fun standby(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "n/a"
        return when (val bucket = context.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working set"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
            45 -> "restricted" // STANDBY_BUCKET_RESTRICTED, API 30
            else -> "$bucket"
        }
    }
}

/** Opens the share sheet with the log file (account menu, quickstart-results.md issue 1). */
fun shareLogIntent(context: Context, log: DiagnosticLog): Intent {
    if (!log.file.exists()) log.write("log shared before anything was written")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", log.file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, log.file.name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return Intent.createChooser(send, null)
}
