package com.workoutrec.setup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Custom Tabs helpers (research R14). Private ("ephemeral") tabs are requested with the same
 * intent extra and capability category that AndroidX Browser 1.9 uses, so no toolchain upgrade is
 * needed; browsers that do not support them simply report no support.
 */
object Browser {
    private const val EXTRA_EPHEMERAL = "androidx.browser.customtabs.extra.ENABLE_EPHEMERAL_BROWSING"
    private const val CATEGORY_EPHEMERAL = "androidx.browser.customtabs.category.EphemeralBrowsing"
    private const val CUSTOM_TABS_SERVICE = "android.support.customtabs.action.CustomTabsService"

    /** Whether the default Custom Tabs browser can open a private tab. */
    fun supportsPrivateTab(context: Context): Boolean {
        val pkg = CustomTabsClient.getPackageName(context, null) ?: return false
        val query = Intent(CUSTOM_TABS_SERVICE).setPackage(pkg).addCategory(CATEGORY_EPHEMERAL)
        return context.packageManager.queryIntentServices(query, 0).isNotEmpty()
    }

    /** Custom Tabs share the browser's Google sign-in. */
    fun open(context: Context, url: String) {
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    }

    /** A private tab: no existing sign-ins, so the account signed in there is the only one. */
    fun openPrivate(context: Context, url: String) {
        val intent = CustomTabsIntent.Builder().build()
        intent.intent.putExtra(EXTRA_EPHEMERAL, true)
        intent.launchUrl(context, Uri.parse(url))
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
