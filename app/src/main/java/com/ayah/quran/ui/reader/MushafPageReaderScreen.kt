package com.ayah.quran.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.MushafSurahBanner
import com.ayah.quran.core.designsystem.component.ReaderAyahActionPill
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.LocalAyahColors
import com.ayah.quran.core.locale.localizedDisplayName
import com.ayah.quran.domain.model.MushafPage
import com.ayah.quran.domain.model.ReadingMode
import com.ayah.quran.domain.usecase.GetMushafPageUseCase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushafPageReaderScreen(
    onBack: () -> Unit,
    onSwitchToContinuous: (surahId: Int, ayah: Int) -> Unit,
    modifier: Modifier = Modifier,
    onCreateWallpaper: ((surahId: Int, ayah: Int) -> Unit)? = null,
    viewModel: MushafPageViewModel = hiltViewModel(),
) {
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val startPage by viewModel.startPage.collectAsStateWithLifecycle()
    val highlight by viewModel.highlight.collectAsStateWithLifecycle()
    val bookmarked by viewModel.observeIsBookmarked().collectAsStateWithLifecycle(false)
    val repeatRange by viewModel.repeatRange.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val extendedColors = LocalAyahColors.current

    val pageCount = GetMushafPageUseCase.LAST_PAGE
    val pagerState = rememberPagerState(
        initialPage = (startPage - 1).coerceIn(0, pageCount - 1),
        pageCount = { pageCount },
    )

    LaunchedEffect(startPage) {
        if (startPage in GetMushafPageUseCase.FIRST_PAGE..GetMushafPageUseCase.LAST_PAGE) {
            pagerState.scrollToPage(startPage - 1)
        }
    }

    val settledPage by remember {
        derivedStateOf { pagerState.settledPage + 1 }
    }
    LaunchedEffect(settledPage, pages) {
        viewModel.ensureLoaded(settledPage)
        viewModel.onPageSettled(settledPage)
    }

    val recitedAyah by viewModel.recitedAyah.collectAsStateWithLifecycle()
    val userSelectionActive by viewModel.userSelectionActive.collectAsStateWithLifecycle()
    LaunchedEffect(recitedAyah, settledPage, userSelectionActive) {
        val target = viewModel.evaluateRecitationFollow(settledPage)
        if (target != null && target in 1..pageCount &&
            target != pagerState.settledPage + 1
        ) {
            viewModel.ensureLoaded(target)
            pagerState.animateScrollToPage(target - 1)
        }
    }

    var pageZoomed by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showRepeatDialog by remember { mutableStateOf(false) }

    val currentPage = pages[pagerState.settledPage + 1]
    val selectedSegment = remember(currentPage, highlight) {
        val target = highlight ?: return@remember null
        currentPage?.segments?.firstOrNull { seg ->
            seg.surah.id == target.first && seg.ayahs.any { it.ayahNumber == target.second }
        }
    }

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

    val currentHighlight = highlight
    if (showRepeatDialog && currentHighlight != null) {
        val maxAyahs = selectedSegment?.surah?.ayahCount ?: 286
        RepeatRangeDialog(
            initialFromAyah = currentHighlight.second,
            maxAyahCount = maxAyahs,
            hasActiveRepeatRange = repeatRange != null,
            onStartRepeat = { range ->
                showRepeatDialog = false
                viewModel.startRepeatRange(currentHighlight.first, range)
            },
            onClearRepeat = viewModel::clearRepeatRange,
            onDismiss = { showRepeatDialog = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(extendedColors.mushafCanvas),
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = extendedColors.mushafCanvas,
            ),
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
                        imageVector = AyahIcons.Back,
                        contentDescription = stringResource(R.string.reader_back),
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        val target = highlight ?: currentPage?.firstAyah?.let {
                            it.surahId to it.ayahNumber
                        } ?: return@IconButton
                        viewModel.setReadingMode(ReadingMode.CONTINUOUS)
                        onSwitchToContinuous(target.first, target.second)
                    },
                    modifier = Modifier.testTag("mushaf_switch_continuous"),
                ) {
                    Icon(
                        imageVector = AyahIcons.ContinuousMode,
                        contentDescription = stringResource(R.string.continuous_mode_cd),
                    )
                }
            },
        )

        HorizontalDivider(
            thickness = AyahTouchTargets.hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
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

        AnimatedVisibility(visible = currentHighlight != null && selectedSegment != null) {
            if (currentHighlight != null && selectedSegment != null) {
                val surahLabel = selectedSegment.surah.localizedDisplayName()
                val refLabel = "$surahLabel · ${AyahNumbering.isolateLtr("${currentHighlight.first}:${currentHighlight.second}")}"
                ReaderAyahActionPill(
                    ayahReferenceLabel = refLabel,
                    isBookmarked = bookmarked,
                    onPlay = { viewModel.playFromAyah(currentHighlight.first, currentHighlight.second) },
                    onToggleBookmark = viewModel::toggleBookmark,
                    onRepeatRange = { showRepeatDialog = true },
                    onCreateWallpaper = onCreateWallpaper?.let { cb ->
                        { cb(currentHighlight.first, currentHighlight.second) }
                    },
                    onDismiss = viewModel::clearSelection,
                    modifier = Modifier.padding(
                        horizontal = AyahSpacing.m,
                        vertical = AyahSpacing.xs,
                    ),
                )
            }
        }

        HorizontalDivider(
            thickness = AyahTouchTargets.hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

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
                .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
        ) {
            page.segments.forEach { segment ->
                if (segment.surahStartsHere) {
                    MushafSurahBanner(
                        surahNameArabic = segment.surah.nameArabic,
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
            Spacer(Modifier.height(AyahSpacing.l))
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
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AyahSpacing.l, vertical = AyahSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrev, enabled = canGoBack) {
            Icon(
                imageVector = AyahIcons.mushafPreviousArrow(isRtl),
                contentDescription = stringResource(R.string.mushaf_prev_page),
            )
        }
        Surface(
            onClick = onIndicatorTap,
            shape = RoundedCornerShape(AyahRadii.pill),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = stringResource(R.string.mushaf_page_indicator, pageNumber, totalPages),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.s),
            )
        }
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(
                imageVector = AyahIcons.mushafNextArrow(isRtl),
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun MushafPage.singleSurahTitle(): String? =
    if (segments.size == 1) segments.first().surah.localizedDisplayName() else null
