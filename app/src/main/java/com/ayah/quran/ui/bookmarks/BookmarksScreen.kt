package com.ayah.quran.ui.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.component.EmptyState
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.domain.model.BookmarkedAyah

/** Saved verses; tapping one opens the reader at that ayah. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpenAyah: (surahId: Int, ayahNumber: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bookmarks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.bookmarks_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (!state.isLoading && state.bookmarks.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.BookmarkBorder,
                title = stringResource(R.string.bookmarks_title),
                message = stringResource(R.string.bookmarks_empty),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            items(state.bookmarks, key = { it.bookmarkId }) { bookmark ->
                BookmarkRow(
                    bookmark = bookmark,
                    onClick = { onOpenAyah(bookmark.surahId, bookmark.ayahNumber) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = AyahSpacing.m),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: BookmarkedAyah,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.m),
    ) {
        Text(
            text = bookmark.textUthmani,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = QuranTextFontFamily,
                fontSize = 22.sp,
                lineHeight = (22 * 1.9f).sp,
                textDirection = TextDirection.Rtl,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${bookmark.surahNameTransliterated} ${bookmark.surahId}:${bookmark.ayahNumber}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AyahSpacing.s),
        )
    }
}
