package com.ayah.quran.ui.companion.hadith

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahSearchBar
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahSurfaceCard
import com.ayah.quran.core.designsystem.component.AyahTonalButton
import com.ayah.quran.core.designsystem.component.EmptyState
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.model.companion.HadithCollection
import com.ayah.quran.domain.model.companion.HadithItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HadithLibraryScreen(
    onBack: () -> Unit,
    onOpenHadith: (hadithId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HadithLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val savedMsg = stringResource(R.string.hadith_saved_toast)
    val removedMsg = stringResource(R.string.hadith_removed_toast)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hadith_title)) },
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
                        onClick = viewModel::toggleFavoritesOnly,
                        modifier = Modifier.testTag("hadith_favorites_toggle"),
                    ) {
                        Icon(
                            imageVector = if (state.favoritesOnly) {
                                AyahIcons.BookmarkFilled
                            } else {
                                AyahIcons.BookmarkOutline
                            },
                            contentDescription = stringResource(R.string.hadith_favorites_filter),
                            tint = if (state.favoritesOnly) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(AyahSpacing.m),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = AyahIcons.Search,
                        title = stringResource(R.string.hadith_error_title),
                        message = state.errorMessage.orEmpty(),
                        action = {
                            AyahTonalButton(
                                text = stringResource(R.string.hadith_retry),
                                onClick = viewModel::retryImport,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.s),
                        verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                    ) {
                        AyahSearchBar(
                            query = state.query,
                            onQueryChange = viewModel::onQueryChange,
                            placeholder = stringResource(R.string.hadith_search_hint),
                            onSearch = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            },
                            modifier = Modifier.testTag("hadith_search_bar"),
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            item(key = "filter_all") {
                                FilterChip(
                                    selected = state.selectedCollectionId == null,
                                    onClick = { viewModel.selectCollection(null) },
                                    label = {
                                        Text(
                                            text = stringResource(R.string.hadith_filter_all),
                                            maxLines = 1,
                                        )
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = AyahTouchTargets.min)
                                        .testTag("hadith_filter_all"),
                                )
                            }
                            items(state.collections, key = { it.id }) { collection ->
                                FilterChip(
                                    selected = state.selectedCollectionId == collection.id,
                                    onClick = { viewModel.selectCollection(collection.id) },
                                    label = {
                                        Text(
                                            text = localizedCollectionTitle(collection),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = AyahTouchTargets.min)
                                        .testTag("hadith_filter_${collection.id}"),
                                )
                            }
                        }

                        if (!state.isSearching && state.items.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.hadith_count_summary,
                                    AyahNumbering.isolateLtr(state.items.size.toString()),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("hadith_count_summary"),
                            )
                        }
                    }

                    when {
                        state.isSearching -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        state.items.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(AyahSpacing.m),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    state.query.isNotBlank() -> EmptyState(
                                        icon = AyahIcons.Search,
                                        title = stringResource(R.string.hadith_search_empty),
                                        message = state.query,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("hadith_empty_search"),
                                    )

                                    state.favoritesOnly -> EmptyState(
                                        icon = AyahIcons.BookmarkOutline,
                                        title = stringResource(R.string.hadith_favorites_empty),
                                        message = stringResource(R.string.hadith_favorites_empty_hint),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("hadith_empty_favorites"),
                                    )

                                    else -> EmptyState(
                                        icon = AyahIcons.Search,
                                        title = stringResource(R.string.hadith_empty_title),
                                        message = stringResource(R.string.hadith_empty_hint),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .testTag("hadith_list"),
                                contentPadding = PaddingValues(
                                    horizontal = AyahSpacing.m,
                                    vertical = AyahSpacing.s,
                                ),
                                verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
                            ) {
                                items(state.items, key = { it.id }) { item ->
                                    HadithSummaryCard(
                                        item = item,
                                        collection = state.collectionsById[item.collectionId],
                                        onClick = { onOpenHadith(item.id) },
                                        onToggleFavorite = {
                                            val willSave = !item.isFavorite
                                            viewModel.toggleFavorite(item.id)
                                            scope.launch {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                snackbarHostState.showSnackbar(
                                                    if (willSave) savedMsg else removedMsg,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HadithSummaryCard(
    item: HadithItem,
    collection: HadithCollection?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val gradeText = localizedHadithGrade(item)
    val collectionTitle = localizedCollectionTitle(collection)
    val badgeKind = resolveGradeBadgeKind(item.gradeAr, item.gradeEn)

    AyahSurfaceCard(
        onClick = onClick,
        modifier = Modifier.testTag("hadith_card_${item.id}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
                ) {
                    Text(
                        text = listOfNotNull(
                            collectionTitle.takeIf { it.isNotBlank() },
                            item.hadithNumber.takeIf { it.isNotBlank() }?.let {
                                AyahNumbering.isolateLtr("#$it")
                            },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (gradeText.isNotBlank()) {
                        AyahStatusBadge(
                            label = gradeText,
                            kind = badgeKind,
                        )
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.testTag("hadith_fav_${item.id}"),
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) {
                            AyahIcons.BookmarkFilled
                        } else {
                            AyahIcons.BookmarkOutline
                        },
                        contentDescription = stringResource(
                            if (item.isFavorite) R.string.hadith_unsave_action else R.string.hadith_save_action,
                        ),
                        tint = if (item.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            val title = localizedHadithTitle(item)
            if (title.isNotBlank() && title != item.textAr) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = item.textAr,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = QuranTextFontFamily,
                    textDirection = TextDirection.Rtl,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!AppLocale.isArabic && !item.textEn.isNullOrBlank()) {
                Text(
                    text = item.textEn,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
