package com.ayah.quran.ui.prayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.component.AyahGroupCard
import com.ayah.quran.core.designsystem.component.AyahInsetDivider
import com.ayah.quran.core.designsystem.component.AyahSelectionSheet
import com.ayah.quran.core.designsystem.component.AyahSettingRow
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.data.prayer.AdhanPreviewState
import com.ayah.quran.data.prayer.BundledAdhanCatalog
import com.ayah.quran.domain.model.prayer.AdhanRecording
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerVoiceSelection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhanVoiceScreen(
    onBack: () -> Unit,
    onOpenLibrary: (Prayer) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdhanVoiceViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val preview by viewModel.previewState.collectAsStateWithLifecycle()
    val arabic = AppLocale.isArabic
    var showApplyAllSheet by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopPreview() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prayer_voice_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopPreview()
                        onBack()
                    }) {
                        Icon(
                            imageVector = AyahIcons.Back,
                            contentDescription = stringResource(R.string.prayer_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
        ) {
            AyahGroupCard(title = stringResource(R.string.prayer_settings_adhan)) {
                AyahSettingRow(
                    title = stringResource(R.string.prayer_voice_apply_all),
                    subtitle = stringResource(R.string.prayer_voice_apply_all_subtitle),
                    leadingIcon = AyahIcons.Audio,
                    onClick = { showApplyAllSheet = true },
                )
            }

            val selection = prefs?.voiceSelection ?: PrayerVoiceSelection()
            AyahGroupCard(title = stringResource(R.string.prayer_voice_title)) {
                val prayers = listOf(
                    Prayer.FAJR,
                    Prayer.DHUHR,
                    Prayer.ASR,
                    Prayer.MAGHRIB,
                    Prayer.ISHA,
                )
                prayers.forEachIndexed { index, prayer ->
                    VoiceRow(
                        prayer = prayer,
                        voiceId = selection.voiceOf(prayer),
                        catalog = viewModel.catalog,
                        preview = preview,
                        onPreviewToggle = { recording ->
                            val active = preview.recording?.id == recording.id
                            when {
                                active && preview.isPlaying -> viewModel.pausePreview()
                                active && preview.isPaused -> viewModel.resumePreview()
                                else -> viewModel.startPreview(recording)
                            }
                        },
                        onOpenLibrary = { onOpenLibrary(prayer) },
                    )
                    if (index != prayers.lastIndex) {
                        AyahInsetDivider()
                    }
                }
            }
        }
    }

    if (showApplyAllSheet) {
        val bundled = remember(viewModel.catalog) {
            viewModel.catalog.recordings.filter { it.isBundled }
        }
        val currentId = prefs?.voiceSelection?.fajr ?: bundled.firstOrNull()?.id
        val selected = bundled.firstOrNull { it.id == currentId } ?: bundled.firstOrNull()
        if (selected != null) {
            AyahSelectionSheet(
                title = stringResource(R.string.prayer_voice_apply_all),
                options = bundled,
                selected = selected,
                labelFor = { rec -> if (arabic) rec.displayNameAr else rec.displayNameEn },
                onSelect = { rec -> viewModel.selectVoiceForAll(rec.id) },
                onDismiss = { showApplyAllSheet = false },
            )
        }
    }
}

@Composable
private fun VoiceRow(
    prayer: Prayer,
    voiceId: String,
    catalog: BundledAdhanCatalog,
    preview: AdhanPreviewState,
    onPreviewToggle: (AdhanRecording) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val arabic = AppLocale.isArabic
    val recording = catalog.byId(voiceId)
    val voiceName = recording?.let {
        if (arabic) it.displayNameAr else it.displayNameEn
    } ?: stringResource(R.string.prayer_voice_user_file)
    val isPlayingThis = recording != null && preview.recording?.id == recording.id && preview.isPlaying

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prayerName(prayer),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = QuranTextFontFamily,
                    fontSize = 20.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = voiceName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (recording != null) {
            IconButton(onClick = { onPreviewToggle(recording) }) {
                Icon(
                    imageVector = if (isPlayingThis) AyahIcons.Pause else AyahIcons.Play,
                    contentDescription = stringResource(
                        if (isPlayingThis) {
                            R.string.prayer_voice_preview_pause
                        } else {
                            R.string.prayer_voice_preview_play
                        },
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        TextButton(onClick = onOpenLibrary) {
            Text(stringResource(R.string.prayer_voice_open_library))
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
