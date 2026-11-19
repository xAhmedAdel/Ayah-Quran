package com.ayah.quran.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahBannerStrip
import com.ayah.quran.core.designsystem.component.AyahBannerTone
import com.ayah.quran.core.designsystem.component.AyahHeroCard
import com.ayah.quran.core.designsystem.component.AyahOutlinedButton
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.designsystem.component.AyahSurfaceCard
import com.ayah.quran.core.designsystem.component.AyahTonalButton
import com.ayah.quran.core.designsystem.component.SectionHeader
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahSurahDisplayStyle
import com.ayah.quran.core.designsystem.theme.AyahThemeTokens
import com.ayah.quran.core.designsystem.theme.AyahWordmarkStyle
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.designsystem.theme.withTabularNumbers
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.ui.wallpapers.labelResForId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun formatAppliedTime(millis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d · HH:mm", Locale.getDefault())
    return formatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}

@Composable
fun HomeScreen(
    onOpenQuran: () -> Unit,
    onContinueReading: (surahId: Int, ayahNumber: Int) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenWallpapers: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenPrayer: () -> Unit = {},
    onOpenAudio: () -> Unit = {},
    onOpenWallpaperPreview: (surahId: Int, ayahNumber: Int) -> Unit = { _, _ -> onOpenWallpapers() },
    showPrayerOnboardingCard: Boolean = false,
    onOpenPrayerOnboarding: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.m),
        verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
    ) {
        HomeHeader(
            hijriDateText = state.hijriDateText,
            onOpenSettings = onOpenSettings,
        )

        if (showPrayerOnboardingCard) {
            AyahBannerStrip(
                message = stringResource(R.string.prayer_onboarding_home_card_hint),
                tone = AyahBannerTone.WARNING,
                actionLabel = stringResource(R.string.prayer_onboarding_home_card_action),
                onAction = onOpenPrayerOnboarding,
            )
        }

        ContinueReadingHero(
            state = state,
            onContinueReading = onContinueReading,
            onOpenQuran = onOpenQuran,
        )

        NextPrayerHomeCard(
            nextPrayer = state.nextPrayer,
            onOpenPrayer = onOpenPrayer,
        )

        state.dailyAyah?.let { daily ->
            DailyAyahCard(
                dailyAyah = daily,
                onOpenAyah = { onContinueReading(daily.surahId, daily.ayahNumber) },
                onCreateWallpaper = { onOpenWallpaperPreview(daily.surahId, daily.ayahNumber) },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
            SectionHeader(text = stringResource(R.string.home_wallpaper_card_title))
            WallpaperHomeCard(
                state = state,
                onOpenWallpapers = onOpenWallpapers,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val bookmarksLabel = if (state.bookmarkCount > 0) {
                "${stringResource(R.string.home_open_bookmarks)} (${state.bookmarkCount})"
            } else {
                stringResource(R.string.home_open_bookmarks)
            }
            AyahTonalButton(
                text = bookmarksLabel,
                onClick = onOpenBookmarks,
                icon = AyahIcons.BookmarkOutline,
                modifier = Modifier.weight(1f),
            )
            AyahOutlinedButton(
                text = stringResource(R.string.audio_header_tagline),
                onClick = onOpenAudio,
                icon = AyahIcons.Audio,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(AyahSpacing.s))
    }
}

@Composable
private fun HomeHeader(
    hijriDateText: String,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = AyahWordmarkStyle,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = hijriDateText.ifEmpty { stringResource(R.string.app_tagline) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalIconButton(
            onClick = onOpenSettings,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = Modifier.testTag("home_settings_button"),
        ) {
            Icon(
                imageVector = AyahIcons.Settings,
                contentDescription = stringResource(R.string.nav_settings),
            )
        }
    }
}

@Composable
private fun ContinueReadingHero(
    state: HomeUiState,
    onContinueReading: (surahId: Int, ayahNumber: Int) -> Unit,
    onOpenQuran: () -> Unit,
) {
    val tokens = AyahThemeTokens.colors
    val reading = state.continueReading
    val isArabic = AppLocale.isArabic

    AyahHeroCard(
        onClick = {
            if (reading != null) {
                onContinueReading(reading.surahId, reading.ayahNumber)
            } else {
                onOpenQuran()
            }
        },
        modifier = Modifier.testTag("home_continue_reading_card"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_continue_reading_title),
                style = MaterialTheme.typography.labelLarge,
                color = tokens.heroAccent,
            )
            if (reading != null) {
                val pct = (reading.progressFraction * 100f).toInt().coerceIn(0, 100)
                Text(
                    text = AyahNumbering.isolateLtr("$pct%"),
                    style = MaterialTheme.typography.labelMedium.withTabularNumbers(),
                    color = tokens.heroAccent,
                )
            }
        }

        Spacer(Modifier.height(AyahSpacing.s))

        if (reading == null) {
            Text(
                text = stringResource(R.string.quran_title),
                style = AyahSurahDisplayStyle,
                color = tokens.heroOnSurface,
            )
            Spacer(Modifier.height(AyahSpacing.xs))
            Text(
                text = stringResource(R.string.home_continue_reading_pending),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.heroMuted,
            )
            Spacer(Modifier.height(AyahSpacing.m))
            Button(
                onClick = onOpenQuran,
                shape = RoundedCornerShape(AyahRadii.pill),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.heroAccent,
                    contentColor = Color(0xFF151C18),
                ),
            ) {
                Icon(
                    imageVector = AyahIcons.MushafMode,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(AyahSpacing.s))
                Text(
                    text = stringResource(R.string.home_open_quran),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        } else {
            val surahDisplay = if (isArabic) reading.surahNameAr else reading.surahNameTransliterated
            Text(
                text = surahDisplay,
                style = AyahSurahDisplayStyle,
                color = tokens.heroOnSurface,
            )
            Spacer(Modifier.height(AyahSpacing.xs))
            Text(
                text = stringResource(
                    R.string.home_continue_reading_format,
                    surahDisplay,
                    reading.ayahNumber,
                    reading.surahAyahCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.heroMuted,
            )
            reading.pageNumber?.let { page ->
                Text(
                    text = stringResource(R.string.home_page_short, page),
                    style = MaterialTheme.typography.labelSmall.withTabularNumbers(),
                    color = tokens.heroAccent,
                    modifier = Modifier.padding(top = AyahSpacing.xxs),
                )
            }
            Spacer(Modifier.height(AyahSpacing.sm))
            val progressLabel = stringResource(R.string.home_continue_reading_progress)
            LinearProgressIndicator(
                progress = { reading.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(AyahRadii.pill))
                    .semantics { contentDescription = progressLabel },
                color = tokens.heroAccent,
                trackColor = tokens.heroOnSurface.copy(alpha = 0.18f),
            )
            Spacer(Modifier.height(AyahSpacing.m))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onContinueReading(reading.surahId, reading.ayahNumber) },
                    shape = RoundedCornerShape(AyahRadii.pill),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.heroAccent,
                        contentColor = Color(0xFF151C18),
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.home_continue_reading_button),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                TextButton(
                    onClick = onOpenQuran,
                    colors = ButtonDefaults.textButtonColors(contentColor = tokens.heroMuted),
                ) {
                    Text(
                        text = stringResource(R.string.home_open_quran),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun NextPrayerHomeCard(
    nextPrayer: HomeNextPrayerState?,
    onOpenPrayer: () -> Unit,
) {
    val isArabic = AppLocale.isArabic
    AyahSurfaceCard(
        onClick = onOpenPrayer,
        modifier = Modifier.testTag("home_next_prayer_card"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(AyahRadii.s))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AyahIcons.Prayer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (nextPrayer == null) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.prayer_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.home_prayer_setup_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AyahStatusBadge(
                    label = stringResource(R.string.home_prayer_setup_action),
                    kind = AyahStatusKind.GOLD,
                )
            } else {
                val city = if (isArabic) nextPrayer.cityAr else nextPrayer.cityEn
                val header = if (city.isNotBlank()) {
                    "${stringResource(R.string.prayer_next)} · $city"
                } else {
                    stringResource(R.string.prayer_next)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = header,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = prayerName(nextPrayer.nextPrayer),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = QuranTextFontFamily,
                            fontSize = 22.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = AyahNumbering.isolateLtr(nextPrayer.countdownText),
                        style = MaterialTheme.typography.titleMedium.withTabularNumbers(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "${AyahNumbering.isolateLtr(nextPrayer.nextTimeText)} · ${
                            stringResource(
                                if (nextPrayer.adhanEnabled) {
                                    R.string.prayer_adhan_status_on
                                } else {
                                    R.string.prayer_adhan_status_off
                                },
                            )
                        }",
                        style = MaterialTheme.typography.labelSmall.withTabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyAyahCard(
    dailyAyah: HomeDailyAyahState,
    onOpenAyah: () -> Unit,
    onCreateWallpaper: () -> Unit,
) {
    val isArabic = AppLocale.isArabic
    val surahDisplay = if (isArabic) dailyAyah.surahNameAr else dailyAyah.surahNameTransliterated

    AyahSurfaceCard(
        onClick = onOpenAyah,
        modifier = Modifier.testTag("home_daily_ayah_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AyahStatusBadge(
                    label = stringResource(R.string.home_daily_ayah_title),
                    kind = AyahStatusKind.GOLD,
                )
                IconButton(onClick = onCreateWallpaper) {
                    Icon(
                        imageVector = AyahIcons.Wallpaper,
                        contentDescription = stringResource(R.string.reader_make_wallpaper),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = dailyAyah.textUthmani,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = QuranTextFontFamily,
                    fontSize = 21.sp,
                    lineHeight = 38.sp,
                    textDirection = TextDirection.Rtl,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "$surahDisplay · ${AyahNumbering.isolateLtr("${dailyAyah.surahId}:${dailyAyah.ayahNumber}")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WallpaperHomeCard(
    state: HomeUiState,
    onOpenWallpapers: () -> Unit,
) {
    val isArabic = AppLocale.isArabic
    val automationText = if (state.scheduleStatus.isScheduled && state.scheduleStatus.intervalMinutes != null) {
        stringResource(R.string.home_wallpaper_automation_on, state.scheduleStatus.intervalMinutes)
    } else {
        stringResource(R.string.home_wallpaper_automation_off)
    }

    AyahSurfaceCard(
        onClick = onOpenWallpapers,
        modifier = Modifier.testTag("home_wallpaper_card"),
    ) {
        val card = state.wallpaperCard
        if (card == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AyahSpacing.m),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(AyahRadii.s))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = AyahIcons.Wallpaper,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.home_wallpaper_card_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AyahStatusBadge(
                        label = automationText,
                        kind = if (state.scheduleStatus.isScheduled) {
                            AyahStatusKind.SUCCESS
                        } else {
                            AyahStatusKind.NEUTRAL
                        },
                    )
                    TextButton(onClick = onOpenWallpapers) {
                        Text(stringResource(R.string.home_open_wallpapers))
                    }
                }
            }
        } else {
            val surahDisplay = if (isArabic) card.surahNameAr else card.surahNameTransliterated
            Row(
                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AyahSpacing.m),
            ) {
                card.thumbnail?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.wallpapers_preview_caption),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(76.dp)
                            .height(142.dp)
                            .clip(RoundedCornerShape(AyahRadii.s))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "$surahDisplay · ${AyahNumbering.isolateLtr("${card.surahId}:${card.ayahNumber}")}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.home_wallpaper_template_format,
                            stringResource(labelResForId(card.templateId)),
                            formatAppliedTime(card.appliedAtMillis),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AyahStatusBadge(
                        label = automationText,
                        kind = if (state.scheduleStatus.isScheduled) {
                            AyahStatusKind.SUCCESS
                        } else {
                            AyahStatusKind.NEUTRAL
                        },
                    )
                    TextButton(onClick = onOpenWallpapers) {
                        Text(stringResource(R.string.home_open_wallpapers))
                    }
                }
            }
        }
    }
}

@Composable
private fun prayerName(prayer: Prayer): String = stringResource(
    when (prayer) {
        Prayer.FAJR -> R.string.prayer_name_fajr
        Prayer.DHUHR -> R.string.prayer_name_dhuhr
        Prayer.ASR -> R.string.prayer_name_asr
        Prayer.MAGHRIB -> R.string.prayer_name_maghrib
        Prayer.ISHA -> R.string.prayer_name_isha
    },
)
