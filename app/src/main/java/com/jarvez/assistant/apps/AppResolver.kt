package com.jarvez.assistant.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.jarvez.assistant.util.Logger

class AppResolver(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    private val appAliases = mapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "youtube" to listOf("com.google.android.youtube"),
        "chrome" to listOf("com.android.chrome"),
        "browser" to listOf("com.android.chrome", "org.mozilla.firefox"),
        "camera" to listOf("com.android.camera", "com.google.android.GoogleCamera"),
        "calculator" to listOf("com.google.android.calculator", "com.android.calculator2"),
        "settings" to listOf("com.android.settings"),
        "gallery" to listOf("com.google.android.apps.photos", "com.android.gallery3d"),
        "photos" to listOf("com.google.android.apps.photos"),
        "maps" to listOf("com.google.android.apps.maps"),
        "spotify" to listOf("com.spotify.music"),
        "clock" to listOf("com.google.android.deskclock", "com.android.deskclock"),
        "messages" to listOf("com.google.android.apps.messaging", "com.android.mms")
    )

    private val urduAliases = mapOf(
        "tasveer" to "camera",
        "tasaveer" to "gallery",
        "naksha" to "maps",
        "hisab" to "calculator",
        "ghari" to "clock",
        "gaane" to "spotify",
        "paigham" to "messages"
    )

    fun getAllInstalledApps(): List<InstalledApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }

        return resolveInfos.mapNotNull { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            val label = resolveInfo.loadLabel(packageManager).toString()
            val icon = resolveInfo.loadIcon(packageManager)
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                InstalledApp(name = label, packageName = pkg, icon = icon, launchIntent = launchIntent)
            } else {
                null
            }
        }.distinctBy { it.packageName }
    }

    fun findApp(nameOrQuery: String): InstalledApp? {
        val query = nameOrQuery.trim().lowercase()
        val allApps = getAllInstalledApps()

        // 1. Check Urdu aliases translation
        val translated = urduAliases[query] ?: query

        // 2. Check static package alias map
        val candidatePackages = appAliases[translated]
        if (candidatePackages != null) {
            for (pkg in candidatePackages) {
                val found = allApps.firstOrNull { it.packageName.equals(pkg, ignoreCase = true) }
                if (found != null) return found
            }
        }

        // 3. Exact name match
        val exactMatch = allApps.firstOrNull { it.name.equals(translated, ignoreCase = true) }
        if (exactMatch != null) return exactMatch

        // 4. Starts with match
        val startsWithMatch = allApps.firstOrNull { it.name.lowercase().startsWith(translated) }
        if (startsWithMatch != null) return startsWithMatch

        // 5. Contains substring match
        val containsMatch = allApps.firstOrNull { it.name.lowercase().contains(translated) }
        if (containsMatch != null) return containsMatch

        // 6. Package name contains
        return allApps.firstOrNull { it.packageName.lowercase().contains(translated) }
    }
}
