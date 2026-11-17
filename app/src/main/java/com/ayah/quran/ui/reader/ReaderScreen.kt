package com.ayah.quran.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.component.AyahCard
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.ui.reader.MushafText
import com.ayah.quran.ui.reader.MushafAyahData
import kotlinx.coroutines.delay

/**
 * Mushaf-style surah reader: continuous Quran text in a flowing paragraph
 * with inline ayah numbers, tap-to-highlight, and adjustable font size.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    onNavigateSurah: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFontControls by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(state.ayahs, viewModel.targetAyah) {
        if (state.ayahs.isNotEmpty() && viewModel.targetAyah in 1..state.ayahs.size) {
            scrollState.scrollTo(viewModel.targetAyah * 40)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = state.surah?.let { surah ->
                        stringResource(R.string.reader_surah_format, surah.nameTransliterated)
                    } ?: stringResource(R.string.quran_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.reader_back),
                    )
                }
            },
            actions = {
                IconButton(onClick = { showFontControls = !showFontControls }) {
                    Icon(
                        Icons.Outlined.TextFields,
                        contentDescription = "Font size",
                    )
                }
                IconButton(
                    onClick = { onNavigateSurah(viewModel.surahId - 1) },
                    enabled = viewModel.surahId > 1,
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.reader_prev_surah),
                    )
                }
                IconButton(
                    onClick = { onNavigateSurah(viewModel.surahId + 1) },
                    enabled = viewModel.surahId < 114,
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.reader_next_surah),
                    )
                }
            },
        )

        when {
            state.isLoading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AyahSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }

            state.isNotFound -> Text(
                text = stringResource(R.string.reader_surah_not_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AyahSpacing.xl),
                textAlign = TextAlign.Center,
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = AyahSpacing.m),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
            ) {
                SurahHeader(state = state)

                if (showFontControls) {
                    FontSizeControls(
                        fontScale = state.fontScale,
                        onScaleChanged = viewModel::setFontScale,
                    )
                }

                state.bismillahHeader?.let { bismillah ->
                    Text(
                        text = bismillah,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = QuranTextFontFamily,
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AyahSpacing.l),
                    )
                }

                MushafText(
                    ayahs = state.ayahs.map {
                        MushafAyahData(it.ayahNumber, it.displayText)
                    },
                    highlightAyahNumber = state.selectedAyah,
                    fontSize = (state.fontScale * 26).sp,
                    onAyahTap = viewModel::onAyahTap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AyahSpacing.l, vertical = AyahSpacing.l),
                )
            }
        }
    }
}

@Composable
private fun SurahHeader(state: ReaderUiState, modifier: Modifier = Modifier) {
    val surah = state.surah ?: return
    AyahCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AyahSpacing.l, horizontal = AyahSpacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = surah.nameArabic,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = QuranTextFontFamily,
                    fontSize = 34.sp,
                ),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.reader_surah_format, surah.nameTransliterated),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = AyahSpacing.s),
            )
            val juzLabel = if (state.juzs.size == 1) {
                "${state.juzs.first()}"
            } else {
                "${state.juzs.first()}–${state.juzs.last()}"
            }
            Text(
                text = stringResource(
                    R.string.reader_meta_format,
                    surah.revelationType.name.lowercase().replaceFirstChar { it.uppercase() },
                    surah.ayahCount,
                    juzLabel,
                    state.firstPage,
                    state.lastPage,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AyahSpacing.s),
            )
        }
    }
}

@Composable
private fun FontSizeControls(
    fontScale: Float,
    onScaleChanged: (Float) -> Unit,
) {
    AyahCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "A",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = fontScale,
                    onValueChange = onScaleChanged,
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = AyahSpacing.m),
                )
                Text(
                    text = "A",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledIconButton(
                onClick = { },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Filled.Remove,
                    contentDescription = "Close font size controls",
                )
            }
        }
    }
}

private const val READING_SAVE_DELAY_MS = 600L
