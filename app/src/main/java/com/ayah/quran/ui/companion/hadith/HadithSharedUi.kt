package com.ayah.quran.ui.companion.hadith

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.model.companion.HadithCollection
import com.ayah.quran.domain.model.companion.HadithItem

internal fun localizedCollectionTitle(collection: HadithCollection?): String {
    if (collection == null) return ""
    return if (AppLocale.isArabic) collection.titleAr else collection.titleEn.ifBlank { collection.titleAr }
}

internal fun localizedHadithTitle(item: HadithItem): String =
    if (AppLocale.isArabic) item.titleAr else item.titleEn.ifBlank { item.titleAr }

internal fun localizedHadithGrade(item: HadithItem): String =
    if (AppLocale.isArabic) item.gradeAr else item.gradeEn.ifBlank { item.gradeAr }

internal fun localizedHadithReference(item: HadithItem): String =
    if (AppLocale.isArabic) item.referenceAr else item.referenceEn.ifBlank { item.referenceAr }

internal fun localizedHadithChapter(item: HadithItem): String =
    if (AppLocale.isArabic) item.chapterTitleAr else item.chapterTitleEn.ifBlank { item.chapterTitleAr }

internal fun localizedHadithExplanation(item: HadithItem): String? =
    if (AppLocale.isArabic) {
        item.explanationAr?.takeIf { it.isNotBlank() }
    } else {
        item.explanationEn?.takeIf { it.isNotBlank() } ?: item.explanationAr?.takeIf { it.isNotBlank() }
    }

internal fun resolveGradeBadgeKind(gradeAr: String, gradeEn: String): AyahStatusKind {
    val ar = gradeAr.trim()
    val en = gradeEn.trim().lowercase()
    return when {
        ar.contains("ضعيف") || en.contains("weak") || en.contains("da'if") -> AyahStatusKind.WARNING
        ar.contains("مُبيَّن") || en.contains("stated within") -> AyahStatusKind.NEUTRAL
        ar.contains("صحيح") || en.contains("authentic") || en.contains("sahih") -> AyahStatusKind.SUCCESS
        ar.contains("حسن") || en.contains("hasan") || en.contains("good") -> AyahStatusKind.GOLD
        else -> AyahStatusKind.NEUTRAL
    }
}

internal fun copyHadithText(
    context: Context,
    item: HadithItem,
    collection: HadithCollection?,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val parts = buildList {
        add(item.textAr)
        if (!AppLocale.isArabic && !item.textEn.isNullOrBlank()) {
            add(item.textEn)
        }
        val meta = listOfNotNull(
            localizedCollectionTitle(collection).takeIf { it.isNotBlank() },
            localizedHadithReference(item).takeIf { it.isNotBlank() },
            localizedHadithGrade(item).takeIf { it.isNotBlank() },
            item.graderAttribution.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (meta.isNotBlank()) add(meta)
    }
    clipboard.setPrimaryClip(ClipData.newPlainText("ayah_hadith", parts.joinToString("\n\n")))
}
