package com.ayah.quran.ui.quran

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
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
import com.ayah.quran.core.designsystem.component.AyahCard
import com.ayah.quran.core.designsystem.component.AyahInsetDivider
import com.ayah.quran.core.designsystem.component.AyahSearchBar
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.designsystem.component.EmptyState
import com.ayah.quran.core.designsystem.component.SurahNumberEmblem
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahSurahDisplayStyle
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.designsystem.theme.withTabularNumbers
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.core.locale.revelationLabel
import com.ayah.quran.domain.model.Surah
import com.ayah.quran.ui.bookmarks.BookmarksListContent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranScreen(
    onOpenSurah: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenAyah: (surahId: Int, ayahNumber: Int) -> Unit = { surahId, _ -> onOpenSurah(surahId) },
    onOpenPage: (page: Int) -> Unit = {},
    viewModel: QuranViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedMessage = stringResource(R.string.bookmarks_removed_snackbar)
    val undoLabel = stringResource(R.string.action_undo)

    QuranHubContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onRetryImport = viewModel::retryImport,
        onOpenSurah = onOpenSurah,
        onOpenAyah = onOpenAyah,
        onOpenPage = onOpenPage,
        onRemoveBookmark = { surahId, ayahNumber ->
            viewModel.removeBookmark(surahId, ayahNumber)
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                    message = removedMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoLastRemove()
                }
            }
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuranHubContent(
    state: QuranListUiState,
    onQueryChange: (String) -> Unit,
    onRetryImport: () -> Unit,
    onOpenSurah: (Int) -> Unit,
    onOpenAyah: (surahId: Int, ayahNumber: Int) -> Unit,
    onOpenPage: (page: Int) -> Unit,
    onRemoveBookmark: (surahId: Int, ayahNumber: Int) -> Unit,
    modifier: Modifier = Modifier,
    initialTab: QuranHubTab = QuranHubTab.SURAHS,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var activeTab by rememberSaveable { mutableStateOf(initialTab) }
    var showPageJumpDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quran_title)) },
                actions = {
                    FilledTonalButton(
                        onClick = { showPageJumpDialog = true },
                        shape = RoundedCornerShape(AyahRadii.pill),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        modifier = Modifier
                            .padding(end = AyahSpacing.s)
                            .testTag("quran_page_jump_button"),
                    ) {
                        Icon(
                            imageVector = AyahIcons.MushafMode,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(AyahSpacing.xs))
                        Text(
                            text = stringResource(R.string.mushaf_page_jump_title),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AyahSearchBar(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.quran_search_hint),
                modifier = Modifier
                    .padding(horizontal = AyahSpacing.m)
                    .testTag("quran_search_bar"),
            )

            Spacer(Modifier.height(AyahSpacing.sm))

            QuranHubTabSwitcher(
                selectedTab = activeTab,
                bookmarkCount = state.totalBookmarkCount,
                onSelectTab = { activeTab = it },
                modifier = Modifier.padding(horizontal = AyahSpacing.m),
            )

            Spacer(Modifier.height(AyahSpacing.s))

            when {
                state.importError != null -> ImportFailedState(
                    reason = state.importError.orEmpty(),
                    onRetry = onRetryImport,
                )

                state.isLoading -> ImportingState(progress = state.importProgress)

                else -> when (activeTab) {
                    QuranHubTab.SURAHS -> SurahList(
                        surahs = state.surahs,
                        startPages = state.surahStartPages,
                        query = state.query,
                        onOpenSurah = onOpenSurah,
                    )

                    QuranHubTab.JUZ -> JuzList(
                        juzList = state.juzList,
                        query = state.query,
                        onOpenJuz = { juz -> onOpenAyah(juz.startSurahId, juz.startAyahNumber) },
                    )

                    QuranHubTab.RUB -> RubList(
                        rubList = state.rubList,
                        query = state.query,
                        onOpenRub = { rub -> onOpenAyah(rub.startSurahId, rub.startAyahNumber) },
                    )

                    QuranHubTab.BOOKMARKS -> BookmarksListContent(
                        bookmarks = state.bookmarks,
                        isLoading = false,
                        onOpenAyah = onOpenAyah,
                        onRemoveBookmark = { bookmark ->
                            onRemoveBookmark(bookmark.surahId, bookmark.ayahNumber)
                        },
                    )
                }
            }
        }
    }

    if (showPageJumpDialog) {
        QuranPageJumpDialog(
            onConfirm = { page ->
                showPageJumpDialog = false
                onOpenPage(page)
            },
            onDismiss = { showPageJumpDialog = false },
        )
    }
}

private data class QuranTabItem(
    val tab: QuranHubTab,
    val label: String,
    val accessibleLabel: String,
    val testTag: String,
)

@Composable
private fun QuranHubTabSwitcher(
    selectedTab: QuranHubTab,
    bookmarkCount: Int,
    onSelectTab: (QuranHubTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surahsLabel = stringResource(R.string.quran_tab_surahs)
    val juzLabel = stringResource(R.string.quran_tab_juz)
    val rubLabel = stringResource(R.string.quran_tab_rub)
    val bookmarksFull = stringResource(R.string.quran_tab_bookmarks)
    val bookmarksShort = stringResource(R.string.quran_tab_bookmarks_short)
    val bookmarksAccessible = if (bookmarkCount > 0) {
        stringResource(R.string.quran_tab_bookmarks_count_cd, bookmarkCount)
    } else {
        bookmarksFull
    }
    val fontScale = LocalDensity.current.fontScale

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compactBookmarks = maxWidth < 350.dp || fontScale > 1.15f
        val hideCountInLabel = (maxWidth < 340.dp && fontScale > 1.2f) || fontScale > 1.35f
        val bookmarksBase = if (compactBookmarks) bookmarksShort else bookmarksFull
        val bookmarksVisible = if (bookmarkCount > 0 && !hideCountInLabel) {
            "$bookmarksBase (${AyahNumbering.isolateLtr(bookmarkCount.toString())})"
        } else {
            bookmarksBase
        }
        val tabs = listOf(
            QuranTabItem(QuranHubTab.SURAHS, surahsLabel, surahsLabel, "quran_tab_surahs"),
            QuranTabItem(QuranHubTab.JUZ, juzLabel, juzLabel, "quran_tab_juz"),
            QuranTabItem(QuranHubTab.RUB, rubLabel, rubLabel, "quran_tab_rub"),
            QuranTabItem(QuranHubTab.BOOKMARKS, bookmarksVisible, bookmarksAccessible, "quran_tab_bookmarks"),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quran_tab_switcher"),
            shape = RoundedCornerShape(AyahRadii.pill),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(AyahTouchTargets.hairline, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AyahSpacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.xxs),
            ) {
                tabs.forEach { item ->
                    val isSelected = item.tab == selectedTab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(AyahRadii.pill))
                            .semantics {
                                role = Role.Tab
                                selected = isSelected
                                contentDescription = item.accessibleLabel
                            }
                            .clickable { onSelectTab(item.tab) }
                            .testTag(item.testTag),
                        shape = RoundedCornerShape(AyahRadii.pill),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(
                                horizontal = AyahSpacing.xs,
                                vertical = AyahSpacing.s,
                            ),
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportingState(progress: Float?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AyahSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = progress?.let {
                stringResource(R.string.import_progress_format, (it * 100).toInt())
            } ?: stringResource(R.string.import_preparing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AyahSpacing.l),
        )
    }
}

@Composable
private fun ImportFailedState(
    reason: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AyahSpacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AyahCard {
            Column(
                modifier = Modifier.padding(AyahSpacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.import_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = AyahSpacing.s),
                )
                Button(onClick = onRetry, modifier = Modifier.padding(top = AyahSpacing.m)) {
                    Text(stringResource(R.string.import_retry))
                }
            }
        }
    }
}

@Composable
private fun SurahList(
    surahs: List<Surah>,
    startPages: Map<Int, Int>,
    query: String,
    onOpenSurah: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (surahs.isEmpty()) {
        val message = if (query.isNotBlank()) {
            stringResource(R.string.quran_search_no_results, query)
        } else {
            stringResource(R.string.quran_list_empty)
        }
        EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = stringResource(R.string.quran_title),
            message = message,
            modifier = modifier
                .fillMaxSize()
                .padding(AyahSpacing.xl),
        )
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(surahs, key = { it.id }) { surah ->
            SurahRow(
                surah = surah,
                startPage = startPages[surah.id],
                onClick = { onOpenSurah(surah.id) },
            )
            AyahInsetDivider(startIndent = 70.dp)
        }
    }
}

@Composable
private fun SurahRow(
    surah: Surah,
    startPage: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isArabic = AppLocale.isArabic
    val ayahCountText = pluralStringResource(
        R.plurals.ayah_count_plurals,
        surah.ayahCount,
        surah.ayahCount,
    )
    val revelationText = stringResource(revelationLabel(surah.revelationType))

    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm)
            .testTag("surah_row_${surah.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.m),
    ) {
        SurahNumberEmblem(number = surah.id)

        if (isArabic) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = surah.nameArabic,
                    style = AyahSurahDisplayStyle.copy(fontSize = 23.sp, lineHeight = 32.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$revelationText · $ayahCountText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            startPage?.let { page ->
                AyahStatusBadge(
                    label = stringResource(R.string.quran_page_badge, page),
                    kind = AyahStatusKind.NEUTRAL,
                )
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = surah.nameTransliterated,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${surah.nameEnglish} · $revelationText · $ayahCountText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = surah.nameArabic,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = QuranTextFontFamily,
                        fontSize = 22.sp,
                        textDirection = TextDirection.Rtl,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                startPage?.let { page ->
                    Text(
                        text = stringResource(R.string.quran_page_badge, page),
                        style = MaterialTheme.typography.labelSmall.withTabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun JuzList(
    juzList: List<JuzSummary>,
    query: String,
    onOpenJuz: (JuzSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (juzList.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = stringResource(R.string.quran_tab_juz),
            message = stringResource(R.string.quran_search_no_results, query),
            modifier = modifier
                .fillMaxSize()
                .padding(AyahSpacing.xl),
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("quran_juz_list"),
    ) {
        items(juzList, key = { it.juzNumber }) { juz ->
            JuzRow(
                juz = juz,
                onClick = { onOpenJuz(juz) },
            )
            AyahInsetDivider(startIndent = 70.dp)
        }
    }
}

@Composable
private fun JuzRow(
    juz: JuzSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isArabic = AppLocale.isArabic
    val surahDisplay = if (isArabic) juz.startSurahNameAr else juz.startSurahNameTransliterated
    val pageRange = AyahNumbering.isolateLtr("${juz.startPage}–${juz.endPage}")

    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm)
            .testTag("juz_row_${juz.juzNumber}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.m),
    ) {
        SurahNumberEmblem(
            number = juz.juzNumber,
            tint = MaterialTheme.colorScheme.secondary,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.xxs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.quran_juz_title, juz.juzNumber),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AyahStatusBadge(
                    label = stringResource(R.string.quran_page_range_badge, pageRange),
                    kind = AyahStatusKind.NEUTRAL,
                )
            }
            Text(
                text = "$surahDisplay · ${AyahNumbering.isolateLtr("${juz.startSurahId}:${juz.startAyahNumber}")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (juz.startAyahText.isNotBlank()) {
                Text(
                    text = juz.startAyahText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = QuranTextFontFamily,
                        fontSize = 18.sp,
                        lineHeight = 30.sp,
                        textDirection = TextDirection.Rtl,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RubList(
    rubList: List<RubSummary>,
    query: String,
    onOpenRub: (RubSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rubList.isEmpty()) {
        val message = if (query.isNotBlank()) {
            stringResource(R.string.quran_search_no_results, query)
        } else {
            stringResource(R.string.quran_list_empty)
        }
        EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = stringResource(R.string.quran_tab_rub),
            message = message,
            modifier = modifier
                .fillMaxSize()
                .padding(AyahSpacing.xl),
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("quran_rub_list"),
    ) {
        items(rubList, key = { it.rubNumber }) { rub ->
            RubRow(
                rub = rub,
                onClick = { onOpenRub(rub) },
            )
            AyahInsetDivider(startIndent = 70.dp)
        }
    }
}

@Composable
private fun RubRow(
    rub: RubSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isArabic = AppLocale.isArabic
    val surahDisplay = if (isArabic) rub.startSurahNameAr else rub.startSurahNameTransliterated
    val divisionMetaRes = when (rub.quarterInHizb) {
        1 -> R.string.quran_rub_hizb_start
        2 -> R.string.quran_rub_hizb_quarter
        3 -> R.string.quran_rub_hizb_half
        else -> R.string.quran_rub_hizb_three_quarters
    }
    val divisionMeta = stringResource(divisionMetaRes, rub.hizbNumber, rub.juzNumber)
    val rowDescription = stringResource(
        R.string.quran_rub_row_cd,
        rub.rubNumber,
        divisionMeta,
        surahDisplay,
        rub.startAyahNumber,
        rub.startPage,
    )

    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
            }
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm)
            .testTag("rub_row_${rub.rubNumber}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.m),
    ) {
        SurahNumberEmblem(
            number = rub.rubNumber,
            tint = MaterialTheme.colorScheme.secondary,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.xxs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.quran_rub_title, rub.rubNumber),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AyahStatusBadge(
                    label = stringResource(R.string.quran_page_badge, rub.startPage),
                    kind = AyahStatusKind.NEUTRAL,
                )
            }
            Text(
                text = "$surahDisplay · ${AyahNumbering.isolateLtr("${rub.startSurahId}:${rub.startAyahNumber}")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = divisionMeta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (rub.startAyahText.isNotBlank()) {
                Text(
                    text = rub.startAyahText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = QuranTextFontFamily,
                        fontSize = 18.sp,
                        lineHeight = 28.sp,
                        textDirection = TextDirection.Rtl,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun QuranPageJumpDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val parsed = input.trim().toIntOrNull()
    val valid = parsed != null && parsed in 1..604
    val showError = input.isNotBlank() && !valid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mushaf_page_jump_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.mushaf_page_jump_hint)) },
                    singleLine = true,
                    isError = showError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (showError) {
                    Text(
                        text = stringResource(R.string.mushaf_invalid_page, 1, 604),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = AyahSpacing.xs),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = valid,
            ) {
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
