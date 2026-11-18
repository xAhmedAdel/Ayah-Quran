package com.ayah.quran.ui.prayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.data.prayer.BundledAdhanCatalog
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerVoiceSelection

/**
 * Per-prayer adhan voice selection (Phase 13): five prayer cards, each
 * showing the currently selected recording with a button that opens the
 * built-in library. The library offers Play Preview (one at a time, with
 * progress, stopping when the screen closes) and per-prayer selection.
 * Preview never modifies selections and never touches scheduled playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhanVoiceScreen(
    onBack: () -> Unit,
    onOpenLibrary: (Prayer) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdhanVoiceViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    // Leaving the screen always stops preview playback (spec §5).
    DisposableEffect(Unit) {
        onDispose { viewModel.stopPreview() }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.prayer_voice_title)) },
            navigationIcon = {
                IconButton(onClick = {
                    viewModel.stopPreview()
                    onBack()
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.prayer_back),
                    )
                }
            },
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
        ) {
            val selection = prefs?.voiceSelection ?: PrayerVoiceSelection()
            listOf(
                Prayer.FAJR,
                Prayer.DHUHR,
                Prayer.ASR,
                Prayer.MAGHRIB,
                Prayer.ISHA,
            ).forEach { prayer ->
                VoiceCard(
                    prayer = prayer,
                    voiceId = selection.voiceOf(prayer),
                    catalog = viewModel.catalog,
                    onOpenLibrary = onOpenLibrary,
                )
            }
        }
    }


}

@Composable
private fun VoiceCard(
    prayer: Prayer,
    voiceId: String,
    catalog: BundledAdhanCatalog,
    onOpenLibrary: (Prayer) -> Unit,
) {
    val arabic = AppLocale.isArabic
    val recording = catalog.byId(voiceId)
    val voiceName = recording?.let {
        if (arabic) it.displayNameAr else it.displayNameEn
    } ?: stringResource(R.string.prayer_voice_user_file)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
        ) {
            Text(
                text = prayerName(prayer),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = QuranTextFontFamily,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.prayer_voice_selected_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = voiceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = AyahSpacing.xs),
                    )
                }
                TextButton(onClick = { onOpenLibrary(prayer) }) {
                    Text(stringResource(R.string.prayer_voice_open_library))
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
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
