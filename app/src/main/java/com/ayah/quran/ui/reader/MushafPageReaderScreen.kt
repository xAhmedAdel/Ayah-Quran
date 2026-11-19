package com.ayah.quran.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.component.AyahCard
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.locale.localizedDisplayName
import com.ayah.quran.domain.model.MushafPage
import com.ayah.quran.domain.usecase.GetMushafPageUseCase
import kotlinx.coroutines.launch

/**
 * Authentic Madinah mushaf reader: 604 pages with their ORIGINAL page
 * boundaries (from the verified Madani page mapping), original surah-start
 * headers and bismillah placement, rendered as justified Uthmani text.
 *
 * This is a page-boundary-accurate mushaf, not a line-for-line print
 * reproduction — the printed line breaks are not part of the verified data.
 * Pinch to zoom; the boundaries never change with zoom or font settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushafPageReaderScreen(
    onBack: () -> Unit,
    onSwitchToContinuous: (surahId: Int, ayah: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MushafPageViewModel = hiltViewModel(),
) {
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val startPage by viewModel.startPage.collectAsStateWithLifecycle()
    val highlight by viewModel.highlight.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val pageCount = GetMushafPageUseCase.LAST_PAGE
    val pagerState = rememberPagerState(
        initialPage = (startPage - 1).coerceIn(0, pageCount - 1),
        pageCount = { pageCount },
    )

    // Jump once the start page resolves from navigation args (surah/ayah).
    // A resolved page is always 1..604; 0 means "still resolving".
    LaunchedEffect(startPage) {
        if (startPage in GetMushafPageUseCase.FIRST_PAGE..GetMushafPageUseCase.LAST_PAGE) {
            pagerState.scrollToPage(startPage - 1)
        }
    }

    // Load content for the settled page and its neighbors.
    val settledPage by remember {
        derivedStateOf { pagerState.settledPage + 1 }
    }
    // Re-runs when the pages map updates, so the position of the FIRST page
    // opened is saved once its content arrives (settledPage itself is stable).
    LaunchedEffect(settledPage, pages) {
        viewModel.ensureLoaded(settledPage)
        viewModel.onPageSettled(settledPage)
    }

    // Pinch-zoom disables pager swiping while zoomed in.
    var pageZoomed by remember { mutableStateOf(false) }

    var showJumpDialog by remember { mutableStateOf(false) }
    if (showJumpDialog) {
        PageJumpDialog(
            onPageSelected = { target ->
                showJumpDialog = false
                if (target in 1..pageCount) {
                    scope.launch { pagerState.scrollToPage(target - 1) }
                }
            },
            onDismiss = { showJumpDialog = false },
        )
    }

    val currentPage = pages[pagerState.settledPage + 1]
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                val title = currentPage?.singleSurahTitle()
                Text(
                    text = title ?: stringResource(R.string.quran_title),
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
                val bookmarked = viewModel
                    .observeIsBookmarked()
                    .collectAsStateWithLifecycle(false)
                    .value
                val bookmarkEnabled = highlight != null
                IconButton(onClick = viewModel::toggleBookmark, enabled = bookmarkEnabled) {
                    val selectedHere = highlight != null && currentPage?.contains(highlight) == true
                    Icon(
                        imageVector = if (bookmarked && selectedHere) {
                            Icons.Filled.Bookmark
                        } else {
                            Icons.Filled.BookmarkBorder
                        },
                        contentDescription = stringResource(R.string.reader_bookmark),
                    )
                }
                IconButton(
                    onClick = {
                        val target = highlight ?: return@IconButton
                        viewModel.playFromAyah(target.first, target.second)
                    },
                    enabled = highlight != null,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.audio_play_surah),
                        tint = if (highlight != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        },
                    )
                }
                IconButton(onClick = {
                    val target = highlight ?: currentPage?.firstAyah?.let {
                        it.surahId to it.ayahNumber
                    } ?: return@IconButton
                    viewModel.setReadingMode(
                        com.ayah.quran.domain.model.ReadingMode.CONTINUOUS,
                    )
                    onSwitchToContinuous(target.first, target.second)
                }) {
                    Icon(
                        Icons.Outlined.TextFields,
                        contentDescription = stringResource(R.string.continuous_mode_cd),
                    )
                }
            },
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = !pageZoomed,
        ) { index ->
            val pageNumber = index + 1
            val page = pages[pageNumber]
            if (page == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                    LaunchedEffect(pageNumber) { viewModel.ensureLoaded(pageNumber) }
                }
            } else {
                ZoomableMushafPage(
                    page = page,
                    highlight = highlight,
                    onZoomChanged = { pageZoomed = it },
                    onAyahTap = viewModel::onAyahTap,
                )
            }
        }

        MushafPageNavBar(
            pageNumber = pagerState.settledPage + 1,
            totalPages = pageCount,
            canGoBack = pagerState.settledPage > 0,
            canGoForward = pagerState.settledPage < pageCount - 1,
            onPrev = {
                scope.launch { pagerState.animateScrollToPage(pagerState.settledPage - 1) }
            },
            onNext = {
                scope.launch { pagerState.animateScrollToPage(pagerState.settledPage + 1) }
            },
            onIndicatorTap = { showJumpDialog = true },
        )
    }
}

@Composable
private fun ZoomableMushafPage(
    page: MushafPage,
    highlight: Pair<Int, Int>?,
    onZoomChanged: (Boolean) -> Unit,
    onAyahTap: (Int, Int) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Pinch-zoom that yields to the pager: single-finger drags are
            // only consumed while zoomed in, so HorizontalPager keeps
            // receiving page swipes at rest scale.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        when {
                            event.changes.size > 1 || zoom != 1f -> {
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                scale = newScale
                                if (newScale == 1f) {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                                event.changes.forEach { it.consume() }
                                onZoomChanged(newScale > 1f)
                            }

                            scale > 1f -> {
                                offsetX += pan.x
                                offsetY += pan.y
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        onZoomChanged(false)
                    },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
                .verticalScroll(scrollState)
                .padding(horizontal = AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
        ) {
            page.segments.forEach { segment ->
                if (segment.surahStartsHere) {
                    SurahStartHeader(
                        surahName = segment.surah.nameArabic,
                        bismillah = segment.bismillah,
                    )
                }
                MushafText(
                    ayahs = segment.ayahs.map {
                        MushafAyahData(it.surahId, it.ayahNumber, it.displayText)
                    },
                    highlightSurahId = highlight?.first ?: 0,
                    highlightAyahNumber = highlight?.second,
                    fontSize = 22.sp,
                    onAyahTap = { range -> onAyahTap(range.surahId, range.ayahNumber) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Surah header shown when a surah begins on the page (printed convention). */
@Composable
private fun SurahStartHeader(
    surahName: String,
    bismillah: String?,
) {
    AyahCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AyahSpacing.l, horizontal = AyahSpacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = surahName,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = QuranTextFontFamily,
                    fontSize = 30.sp,
                ),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
            )
            bismillah?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = QuranTextFontFamily,
                    ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = AyahSpacing.s),
                )
            }
        }
    }
}

@Composable
private fun MushafPageNavBar(
    pageNumber: Int,
    totalPages: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onIndicatorTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AyahSpacing.l, vertical = AyahSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrev, enabled = canGoBack) {
            // RTL-aware: "previous" points toward earlier pages. In an RTL
            // mushaf, earlier pages lie to the RIGHT — arrow must not be
            // blindly mirrored by auto-mirroring icons.
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.mushaf_prev_page),
            )
        }
        TextButton(onClick = onIndicatorTap) {
            Text(
                text = stringResource(R.string.mushaf_page_indicator, pageNumber, totalPages),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(
                Icons.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.mushaf_next_page),
            )
        }
    }
}

@Composable
private fun PageJumpDialog(
    onPageSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val parsed = text.toIntOrNull()
    val valid = parsed != null &&
        parsed in GetMushafPageUseCase.FIRST_PAGE..GetMushafPageUseCase.LAST_PAGE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mushaf_page_jump_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { value -> text = value.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.mushaf_page_jump_hint)) },
                    isError = text.isNotEmpty() && !valid,
                    singleLine = true,
                )
                if (text.isNotEmpty() && !valid) {
                    Text(
                        text = stringResource(
                            R.string.mushaf_invalid_page,
                            GetMushafPageUseCase.FIRST_PAGE,
                            GetMushafPageUseCase.LAST_PAGE,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onPageSelected) }, enabled = valid) {
                Text(stringResource(R.string.mushaf_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reader_back))
            }
        },
    )
}

/** The surah name for single-surah pages; null when the page spans surahs. */
private fun MushafPage.singleSurahTitle(): String? =
    if (segments.size == 1) segments.first().surah.localizedDisplayName() else null

private fun MushafPage.contains(highlight: Pair<Int, Int>?): Boolean =
    highlight != null && segments.any { segment ->
        segment.surah.id == highlight.first &&
            segment.ayahs.any { it.ayahNumber == highlight.second }
    }
