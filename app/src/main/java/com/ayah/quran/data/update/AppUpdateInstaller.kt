package com.ayah.quran.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ayah.quran.BuildConfig
import java.io.File

sealed interface AppUpdateInstallResult {
    data object Launched : AppUpdateInstallResult
    data object PermissionRequired : AppUpdateInstallResult
    data class Rejected(val reason: String) : AppUpdateInstallResult
}

object AppUpdateInstaller {

    fun installOrGuide(
        context: Context,
        apk: File,
        expectedByteCount: Long? = null,
    ): AppUpdateInstallResult {
        val validation = AppUpdateSecurityValidator.validateApk(
            apkFile = apk,
            expectedByteCount = expectedByteCount,
            installedVersionCode = BuildConfig.VERSION_CODE.toLong(),
            installedSignerSha256Digests = AppUpdateSecurityValidator.readInstalledSignerDigests(context),
            metadataReader = AppUpdateSecurityValidator.androidApkMetadataReader(context),
        )
        if (validation is ApkValidationResult.Invalid) {
            return AppUpdateInstallResult.Rejected(validation.reason)
        }

        val packageManager = context.packageManager
        if (!packageManager.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return AppUpdateInstallResult.PermissionRequired
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
        return AppUpdateInstallResult.Launched
    }
}
