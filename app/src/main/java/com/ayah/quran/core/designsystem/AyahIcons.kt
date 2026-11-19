package com.ayah.quran.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowLeft as PhysicalArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight as PhysicalArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.ui.graphics.vector.ImageVector

object AyahIcons {
    val Back: ImageVector get() = Icons.AutoMirrored.Filled.ArrowBack
    val ChevronEnd: ImageVector get() = Icons.AutoMirrored.Filled.KeyboardArrowRight
    val ChevronStart: ImageVector get() = Icons.AutoMirrored.Filled.KeyboardArrowLeft
    val Close: ImageVector get() = Icons.Filled.Close
    val Search: ImageVector get() = Icons.Outlined.Search
    val Settings: ImageVector get() = Icons.Outlined.Settings
    val Tune: ImageVector get() = Icons.Outlined.Tune
    val More: ImageVector get() = Icons.Filled.MoreHoriz

    val Home: ImageVector get() = Icons.Outlined.Home
    val MushafMode: ImageVector get() = Icons.Outlined.AutoStories
    val ContinuousMode: ImageVector get() = Icons.AutoMirrored.Outlined.Article
    val FontSize: ImageVector get() = Icons.Outlined.FormatSize
    val AutoFollowRecitation: ImageVector get() = Icons.Outlined.Sync
    val Prayer: ImageVector get() = Icons.Outlined.Schedule
    val Audio: ImageVector get() = Icons.Outlined.GraphicEq
    val Wallpaper: ImageVector get() = Icons.Outlined.Wallpaper

    val Play: ImageVector get() = Icons.Filled.PlayArrow
    val Pause: ImageVector get() = Icons.Filled.Pause
    val Repeat: ImageVector get() = Icons.Outlined.Repeat
    val BookmarkFilled: ImageVector get() = Icons.Filled.Bookmark
    val BookmarkOutline: ImageVector get() = Icons.Outlined.BookmarkBorder
    val Download: ImageVector get() = Icons.Outlined.Download
    val OfflineReady: ImageVector get() = Icons.Outlined.CloudDone

    val StatusSuccess: ImageVector get() = Icons.Filled.CheckCircle
    val StatusWarning: ImageVector get() = Icons.Filled.WarningAmber
    val StatusError: ImageVector get() = Icons.Filled.ErrorOutline

    @Suppress("DEPRECATION")
    fun mushafPreviousArrow(isRtl: Boolean): ImageVector =
        if (isRtl) Icons.Filled.PhysicalArrowRight else Icons.Filled.PhysicalArrowLeft

    @Suppress("DEPRECATION")
    fun mushafNextArrow(isRtl: Boolean): ImageVector =
        if (isRtl) Icons.Filled.PhysicalArrowLeft else Icons.Filled.PhysicalArrowRight
}
