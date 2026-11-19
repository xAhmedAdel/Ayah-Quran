package com.ayah.quran.ui.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahInsetDivider
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.designsystem.component.EmptyState
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.model.BookmarkedAyah
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpenAyah: (surahId: Int, ayahNumber: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedMessage = stringResource(R.string.bookmarks_removed_snackbar)
    val undoLabel = stringResource(R.string.action_undo)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bookmarks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AyahIcons.Back,
                            contentDescription = stringResource(R.string.bookmarks_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        BookmarksListContent(
            bookmarks = state.bookmarks,
            isLoading = state.isLoading,
            onOpenAyah = onOpenAyah,
            onRemoveBookmark = { bookmark ->
                viewModel.removeBookmark(bookmark.surahId, bookmark.ayahNumber)
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
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }
}

@Composable
fun BookmarksListContent(
    bookmarks: List<BookmarkedAyah>,
    isLoading: Boolean,
    onOpenAyah: (surahId: Int, ayahNumber: Int) -> Unit,
    onRemoveBookmark: (BookmarkedAyah) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isLoading && bookmarks.isEmpty()) {
        EmptyState(
            icon = AyahIcons.BookmarkOutline,
            title = stringResource(R.string.bookmarks_title),
            message = stringResource(R.string.bookmarks_empty),
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(bookmarks, key = { it.bookmarkId }) { bookmark ->
            BookmarkRow(
                bookmark = bookmark,
                onClick = { onOpenAyah(bookmark.surahId, bookmark.ayahNumber) },
                onRemove = { onRemoveBookmark(bookmark) },
            )
            AyahInsetDivider()
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: BookmarkedAyah,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isArabic = AppLocale.isArabic
    val surahDisplay = if (isArabic) bookmark.surahNameArabic else bookmark.surahNameTransliterated
    val refText = "$surahDisplay · ${AyahNumbering.isolateLtr("${bookmark.surahId}:${bookmark.ayahNumber}")}"

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm)
            .testTag("bookmark_row_${bookmark.surahId}_${bookmark.ayahNumber}"),
        verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AyahStatusBadge(
                label = refText,
                kind = AyahStatusKind.GOLD,
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.testTag("bookmark_remove_${bookmark.surahId}_${bookmark.ayahNumber}"),
            ) {
                Icon(
                    imageVector = AyahIcons.BookmarkFilled,
                    contentDescription = stringResource(R.string.bookmarks_remove),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Text(
            text = bookmark.textUthmani,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = QuranTextFontFamily,
                fontSize = 22.sp,
                lineHeight = 40.sp,
                textDirection = TextDirection.Rtl,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
