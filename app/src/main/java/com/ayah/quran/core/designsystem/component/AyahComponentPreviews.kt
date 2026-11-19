package com.ayah.quran.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahSurahDisplayStyle
import com.ayah.quran.core.designsystem.theme.AyahTheme
import com.ayah.quran.core.designsystem.theme.AyahThemeTokens
import com.ayah.quran.domain.model.ThemeMode

@Composable
private fun DesignSystemShowcaseContent() {
    val extended = AyahThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(AyahSpacing.m),
        verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
    ) {
        AyahHeroCard {
            Text(
                text = "سورة الكهف",
                style = AyahSurahDisplayStyle,
                color = extended.heroOnSurface,
            )
            Text(
                text = "صفحة ٢٩٣ · الجزء ١٥",
                style = MaterialTheme.typography.bodySmall,
                color = extended.heroMuted,
            )
        }
        AyahBannerStrip(
            message = "أكمل إعداد تنبيهات الصلاة والأذان",
            tone = AyahBannerTone.WARNING,
            actionLabel = "إعداد",
            onAction = {},
        )
        AyahSearchBar(
            query = "",
            onQueryChange = {},
            placeholder = "ابحث عن سورة",
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SurahNumberEmblem(number = 18, arabicDigits = true)
            AyahStatusBadge(label = "متاح دون اتصال", kind = AyahStatusKind.SUCCESS)
            AyahStatusBadge(label = "يحتاج انتباهك", kind = AyahStatusKind.WARNING)
        }
        MushafSurahBanner(
            surahNameArabic = "سورة الفاتحة",
            subtitle = "مكية · ٧ آيات",
        )
        AyahGroupCard(title = "التفضيلات") {
            AyahSettingRow(
                title = "القراءة والمصحف",
                subtitle = "وضع المصحف (٦٠٤ صفحات)",
                leadingIcon = AyahIcons.MushafMode,
                onClick = {},
            )
            AyahInsetDivider()
            AyahSettingRow(
                title = "مواقيت الصلاة والأذان",
                subtitle = "القاهرة · الهيئة المصرية",
                leadingIcon = AyahIcons.Prayer,
                onClick = {},
            )
        }
        AyahStepProgress(currentStep = 2, totalSteps = 5, label = "إعداد التنبيهات")
        ReaderAyahActionPill(
            ayahReferenceLabel = "الفاتحة · آية ٦",
            isBookmarked = true,
            onPlay = {},
            onToggleBookmark = {},
            onRepeatRange = {},
            onCreateWallpaper = {},
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
            AyahPrimaryButton(text = "متابعة", onClick = {}, modifier = Modifier.weight(1f))
            AyahTonalButton(text = "تصفح", onClick = {}, modifier = Modifier.weight(1f))
        }
    }
}

@Preview(name = "Arabic Light", locale = "ar", showBackground = true)
@Composable
private fun PreviewDesignSystemArabicLight() {
    AyahTheme(themeMode = ThemeMode.LIGHT) {
        DesignSystemShowcaseContent()
    }
}

@Preview(
    name = "Arabic Dark",
    locale = "ar",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun PreviewDesignSystemArabicDark() {
    AyahTheme(themeMode = ThemeMode.DARK) {
        DesignSystemShowcaseContent()
    }
}

@Preview(name = "English Light 1.5x", locale = "en", fontScale = 1.5f, showBackground = true)
@Composable
private fun PreviewDesignSystemEnglishScaled() {
    AyahTheme(themeMode = ThemeMode.LIGHT) {
        DesignSystemShowcaseContent()
    }
}
