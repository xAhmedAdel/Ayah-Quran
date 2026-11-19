package com.ayah.quran.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands the downloaded update APK to the SYSTEM package installer.
 *
 * Android forbids silent installs by design: the user reviews and confirms
 * in the installer UI. On first update the user must once allow
 * "install unknown apps" for Ayah (system screen) — we deep-link there.
 */
object AppUpdateInstaller {

    /**
     * @return true when the installer was launched; false when the user was
     *         redirected to grant "install unknown apps" first (they retry
     *         after granting).
     */
    fun installOrGuide(context: Context, apk: File): Boolean {
        val packageManager = context.packageManager
        if (!packageManager.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return false
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK,
            )
        }
        context.startActivity(intent)
        return true
    }
}
