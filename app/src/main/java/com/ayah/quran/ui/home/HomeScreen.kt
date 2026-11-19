package com.ayah.quran.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.component.AyahCard
import com.ayah.quran.core.designsystem.component.SectionHeader
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahWordmarkStyle
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.ui.wallpapers.labelResForId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d · HH:mm", Locale.getDefault())

/**
 * Home: brand hero, live Continue Reading (real saved position + progress),
 * current-wallpaper card (thumbnail rendered from the last successful apply,
 * automation status), and quick actions. All data comes from repositories —
 * pending features show honest empty states, never fabricated data.
 */
@Composable
fun HomeScreen(
    onOpenQuran: () -> Unit,
    onContinueReading: (surahId: Int, ayahNumber: Int) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenWallpapers: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = AyahWordmarkStyle,
                    fontFamily = QuranTextFontFamily,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("home_settings_button"),
            ) {
                Icon(
                    imageVector = AyahIcons.Settings,
                    contentDescription = stringResource(R.string.nav_settings),
                )
            }
        }

        if (showPrayerOnboardingCard) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AyahSpacing.m),
                    verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                ) {
                    Text(
                        text = stringResource(R.string.prayer_onboarding_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.prayer_onboarding_home_card_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    FilledTonalButton(onClick = onOpenPrayerOnboarding) {
                        Text(stringResource(R.string.prayer_onboarding_home_card_action))
                    }
                }
            }
        }

        SectionHeader(text = stringResource(R.string.home_continue_reading_title))
        ContinueReadingCard(state = state, onContinueReading = onContinueReading, onOpenQuran = onOpenQuran)

        SectionHeader(text = stringResource(R.string.home_wallpaper_card_title))
        WallpaperCard(
            state = state,
            onOpenWallpapers = onOpenWallpapers,
            onOpenQuran = onOpenQuran,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.m),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalButton(
                onClick = onOpenQuran,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Outlined.FormatQuote,
                    contentDescription = null,
                    modifier = Modifier.padding(end = AyahSpacing.s),
                )
                Text(stringResource(R.string.home_open_quran))
            }
            OutlinedButton(
                onClick = onOpenBookmarks,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Outlined.Bookmarks,
                    contentDescription = null,
                    modifier = Modifier.padding(end = AyahSpacing.s),
                )
                Text(stringResource(R.string.home_open_bookmarks))
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(
    state: HomeUiState,
    onContinueReading: (surahId: Int, ayahNumber: Int) -> Unit,
    onOpenQuran: () -> Unit,
) {
    AyahCard {
        val reading = state.continueReading
        if (reading == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AyahSpacing.m),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
            ) {
                Icon(
                    Icons.Outlined.FormatQuote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.home_continue_reading_pending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenQuran) {
                    Text(stringResource(R.string.home_open_quran))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AyahSpacing.m),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
            ) {
                Text(
                    text = stringResource(
                        R.string.home_continue_reading_format,
                        reading.surahDisplayName,
                        reading.ayahNumber,
                        reading.surahAyahCount,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = QuranTextFontFamily,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // REAL progress: ayah ÷ surah ayah count.
                val progressLabel = stringResource(R.string.home_continue_reading_progress)
                LinearProgressIndicator(
                    progress = { reading.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .semantics { contentDescription = progressLabel },
                )
                TextButton(onClick = { onContinueReading(reading.surahId, reading.ayahNumber) }) {
                    Text(stringResource(R.string.home_continue_reading_button))
                }
            }
        }
    }
}

@Composable
private fun WallpaperCard(
    state: HomeUiState,
    onOpenWallpapers: () -> Unit,
    onOpenQuran: () -> Unit,
) {
    AyahCard {
        val card = state.wallpaperCard
        if (card == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AyahSpacing.m),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
            ) {
                Icon(
                    Icons.Outlined.Wallpaper,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.home_wallpaper_card_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenWallpapers) {
                    Text(stringResource(R.string.home_open_wallpapers))
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.m),
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
                            .width(96.dp)
                            .height(192.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp),
                            ),
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(
                            R.string.home_wallpaper_ayah_format,
                            card.surahDisplayName,
                            card.surahId,
                            card.ayahNumber,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.home_wallpaper_template_format,
                            stringResource(labelResForId(card.templateId)),
                            TIME_FORMAT.format(Instant.ofEpochMilli(card.appliedAtMillis).atZone(ZoneId.systemDefault())),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val automationText = if (state.scheduleStatus.isScheduled && state.scheduleStatus.intervalMinutes != null) {
                        stringResource(R.string.home_wallpaper_automation_on, state.scheduleStatus.intervalMinutes)
                    } else {
                        stringResource(R.string.home_wallpaper_automation_off)
                    }
                    Text(
                        text = automationText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    TextButton(onClick = onOpenWallpapers) {
                        Text(stringResource(R.string.home_open_wallpapers))
                    }
                }
            }
        }
    }
}
