package com.ayah.quran.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahCard
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

/**
 * Full-screen surah reader.
 *
 * Arabic rendering requirements (spec §6.6): proper shaping and RTL come
 * from the platform text stack — Compose applies HarfBuzz shaping; the
 * paragraph direction is explicitly RTL and the text is never reversed or
 * otherwise transformed. Amiri supplies Arabic display typography with a
 * generous line height so diacritics and Quranic marks never clip.
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
    val listState = rememberLazyListState()

    // Restore a target ayah (from a bookmark or Continue Reading) once the
    // surah content is available. Item 0 is the surah header, so ayah N sits
    // at list index N.
    LaunchedEffect(state.ayahs, viewModel.targetAyah) {
        if (state.ayahs.isNotEmpty() && viewModel.targetAyah in 1..state.ayahs.size) {
            listState.scrollToItem(viewModel.targetAyah)
        }
    }

    // Persist reading progress while scrolling, debounced to avoid a write
    // per frame during flings.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .drop(1)
            .collectLatest { index ->
                delay(READING_SAVE_DEBOUNCE_MS)
                val visibleAyah = if (index == 0) 1 else index
                if (visibleAyah <= state.ayahs.size) {
                    viewModel.onVisibleAyahChanged(visibleAyah)
                }
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

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    SurahHeader(state = state)
                }

                items(
                    count = state.ayahs.size,
                    key = { index -> state.ayahs[index].ayahNumber },
                ) { index ->
                    val readerAyah = state.ayahs[index]
                    AyahRow(
                        ayah = readerAyah,
                        onToggleBookmark = { viewModel.toggleBookmark(readerAyah.ayahNumber) },
                        observeIsBookmarked = viewModel::observeIsBookmarked,
                    )
                }
            }
        }
    }
}

@Composable
private fun SurahHeader(state: ReaderUiState, modifier: Modifier = Modifier) {
    val surah = state.surah ?: return
    AyahCard(modifier = modifier.padding(AyahSpacing.m)) {
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
                    textDirection = TextDirection.Rtl,
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

            state.bismillahHeader?.let { bismillah ->
                Text(
                    text = bismillah,
                    style = QuranTextStyle(fontSize = 24.sp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = AyahSpacing.m),
                )
            }
        }
    }
}

@Composable
private fun AyahRow(
    ayah: ReaderAyah,
    onToggleBookmark: () -> Unit,
    observeIsBookmarked: (Int) -> Flow<Boolean>,
    modifier: Modifier = Modifier,
) {
    val isBookmarked by produceState(false, ayah.ayahNumber) {
        observeIsBookmarked(ayah.ayahNumber).collect { value = it }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = AyahSpacing.m),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.m),
            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            Text(
                text = ayah.displayText,
                style = QuranTextStyle(fontSize = QURAN_TEXT_SIZE_SP),
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
            ) {
                IconToggleButton(
                    checked = isBookmarked,
                    onCheckedChange = { onToggleBookmark() },
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(R.string.reader_bookmark),
                        tint = if (isBookmarked) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Text(
                    text = stringResource(
                        R.string.reader_ayah_number_format,
                        AyahNumbering.toArabicIndic(ayah.ayahNumber),
                    ),
                    style = QuranTextStyle(fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

/** Amiri + explicit RTL paragraph direction + tall line height for marks. */
private fun QuranTextStyle(fontSize: androidx.compose.ui.unit.TextUnit): TextStyle =
    TextStyle(
        fontFamily = QuranTextFontFamily,
        fontSize = fontSize,
        lineHeight = fontSize * QURAN_TEXT_LINE_HEIGHT_FACTOR,
        textDirection = TextDirection.Rtl,
    )

private const val READING_SAVE_DEBOUNCE_MS = 600L
private val QURAN_TEXT_SIZE_SP = 26.sp
private const val QURAN_TEXT_LINE_HEIGHT_FACTOR = 1.9f
