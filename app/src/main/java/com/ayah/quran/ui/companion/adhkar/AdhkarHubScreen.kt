package com.ayah.quran.ui.companion.adhkar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahGroupCard
import com.ayah.quran.core.designsystem.component.AyahHeroCard
import com.ayah.quran.core.designsystem.component.AyahInsetDivider
import com.ayah.quran.core.designsystem.component.AyahSearchBar
import com.ayah.quran.core.designsystem.component.AyahSettingRow
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.designsystem.component.AyahSurfaceCard
import com.ayah.quran.core.designsystem.component.EmptyState
import com.ayah.quran.core.designsystem.component.SectionHeader
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahThemeTokens
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.designsystem.theme.withTabularNumbers
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.model.companion.DhikrCategory
import com.ayah.quran.domain.model.companion.DhikrItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhkarHubScreen(
    onBack: () -> Unit,
    onOpenSession: (categoryId: String, itemId: String?) -> Unit,
    onOpenDuas: () -> Unit,
    onOpenTasbih: () -> Unit = {},
    onOpenHadith: () -> Unit = {},
    onOpenSurahInMushaf: (surahId: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdhkarHubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDayKey()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.adhkar_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AyahIcons.Back,
                            contentDescription = stringResource(R.string.reader_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onOpenTasbih,
                        modifier = Modifier.testTag("adhkar_open_tasbih"),
                    ) {
                        Text(stringResource(R.string.tasbih_title))
                    }
                    TextButton(
                        onClick = onOpenDuas,
                        modifier = Modifier.testTag("adhkar_open_duas"),
                    ) {
                        Text(stringResource(R.string.duas_title))
                    }
                    IconButton(
                        onClick = viewModel::toggleSavedFilter,
                        modifier = Modifier.testTag("adhkar_saved_toggle"),
                    ) {
                        Icon(
                            imageVector = if (state.savedOnly) {
                                AyahIcons.BookmarkFilled
                            } else {
                                AyahIcons.BookmarkOutline
                            },
                            contentDescription = stringResource(R.string.adhkar_saved_filter),
                            tint = if (state.savedOnly) {
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
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
        ) {
            item(key = "search") {
                AyahSearchBar(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    placeholder = stringResource(R.string.adhkar_search_hint),
                )
            }

            when {
                state.query.isNotBlank() -> {
                    if (state.searchResults.isEmpty()) {
                        item(key = "search_empty") {
                            EmptyState(
                                icon = AyahIcons.Search,
                                title = stringResource(R.string.adhkar_search_empty),
                                message = state.query,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        items(state.searchResults, key = { "search_${it.id}" }) { item ->
                            DhikrSearchRowCard(
                                item = item,
                                isFavorite = item.id in state.favoriteIds,
                                onOpen = { onOpenSession(item.categoryId, item.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(item.id) },
                            )
                        }
                    }
                }

                state.savedOnly -> {
                    if (state.favoriteItems.isEmpty()) {
                        item(key = "saved_empty") {
                            EmptyState(
                                icon = AyahIcons.BookmarkOutline,
                                title = stringResource(R.string.adhkar_saved_filter),
                                message = stringResource(R.string.adhkar_saved_empty),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        items(state.favoriteItems, key = { "fav_${it.id}" }) { item ->
                            DhikrSearchRowCard(
                                item = item,
                                isFavorite = true,
                                onOpen = { onOpenSession(item.categoryId, item.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(item.id) },
                            )
                        }
                    }
                }

                else -> {
                    state.recommendedSession?.let { recommended ->
                        item(key = "recommended") {
                            RecommendedSessionHero(
                                summary = recommended,
                                onStart = { onOpenSession(recommended.category.id, null) },
                            )
                        }
                    }

                    if (state.dailySessions.isNotEmpty()) {
                        item(key = "daily_header") {
                            SectionHeader(text = stringResource(R.string.adhkar_daily_sessions))
                        }
                        items(
                            items = state.dailySessions.chunked(2),
                            key = { pair -> "row_${pair.first().category.id}" },
                        ) { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                            ) {
                                pair.forEach { summary ->
                                    DailySessionCard(
                                        summary = summary,
                                        onClick = { onOpenSession(summary.category.id, null) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    if (state.sunnahSurahs.isNotEmpty()) {
                        item(key = "sunnah_surahs") {
                            AyahGroupCard(title = stringResource(R.string.adhkar_sunnah_surahs)) {
                                state.sunnahSurahs.forEachIndexed { index, surahItem ->
                                    val surahId = surahItem.surahId
                                    if (surahId != null) {
                                        AyahSettingRow(
                                            title = localizedItemTitle(surahItem),
                                            subtitle = localizedItemSource(surahItem),
                                            leadingIcon = Icons.Outlined.MenuBook,
                                            onClick = { onOpenSurahInMushaf(surahId) },
                                        )
                                        if (index != state.sunnahSurahs.lastIndex) {
                                            AyahInsetDivider()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "tasbih_entry") {
                        AyahSurfaceCard(
                            onClick = onOpenTasbih,
                            modifier = Modifier.testTag("adhkar_tasbih_card"),
                        ) {
                            AyahSettingRow(
                                title = stringResource(R.string.tasbih_title),
                                subtitle = stringResource(R.string.tasbih_open_subtitle),
                                leadingIcon = Icons.Outlined.TouchApp,
                                onClick = onOpenTasbih,
                            )
                        }
                    }

                    item(key = "hadith_entry") {
                        AyahSurfaceCard(
                            onClick = onOpenHadith,
                            modifier = Modifier.testTag("adhkar_hadith_card"),
                        ) {
                            AyahSettingRow(
                                title = stringResource(R.string.hadith_title),
                                subtitle = stringResource(R.string.hadith_open_subtitle),
                                leadingIcon = Icons.Outlined.MenuBook,
                                onClick = onOpenHadith,
                            )
                        }
                    }

                    item(key = "duas_entry") {
                        AyahSurfaceCard(onClick = onOpenDuas) {
                            AyahSettingRow(
                                title = stringResource(R.string.duas_title),
                                subtitle = stringResource(R.string.duas_open_hub_subtitle),
                                leadingIcon = Icons.Outlined.AutoAwesome,
                                onClick = onOpenDuas,
                            )
                        }
                    }

                    if (state.generalCategories.isNotEmpty()) {
                        item(key = "more_categories") {
                            AyahGroupCard(title = stringResource(R.string.adhkar_more_categories)) {
                                state.generalCategories.forEachIndexed { index, summary ->
                                    AyahSettingRow(
                                        title = localizedCategoryTitle(summary.category),
                                        subtitle = AyahNumbering.isolateLtr(
                                            "${summary.completedItems}/${summary.totalItems}",
                                        ),
                                        onClick = { onOpenSession(summary.category.id, null) },
                                    )
                                    if (index != state.generalCategories.lastIndex) {
                                        AyahInsetDivider()
                                    }
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
private fun RecommendedSessionHero(
    summary: AdhkarCategorySummary,
    onStart: () -> Unit,
) {
    val tokens = AyahThemeTokens.colors
    val progress = if (summary.totalItems > 0) {
        summary.completedItems / summary.totalItems.toFloat()
    } else {
        0f
    }
    AyahHeroCard(onClick = onStart) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.adhkar_recommended_now),
                style = MaterialTheme.typography.labelLarge,
                color = tokens.heroAccent,
            )
            Text(
                text = AyahNumbering.isolateLtr("${summary.completedItems} / ${summary.totalItems}"),
                style = MaterialTheme.typography.labelLarge.withTabularNumbers(),
                color = tokens.heroAccent,
            )
        }
        Text(
            text = localizedCategoryTitle(summary.category),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = QuranTextFontFamily),
            color = tokens.heroOnSurface,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(AyahRadii.pill)),
            color = tokens.heroAccent,
            trackColor = tokens.heroOnSurface.copy(alpha = 0.18f),
        )
        Spacer(Modifier.height(AyahSpacing.xs))
        Button(
            onClick = onStart,
            shape = RoundedCornerShape(AyahRadii.pill),
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.heroAccent,
                contentColor = tokens.heroSurfaceDeep,
            ),
            modifier = Modifier.defaultMinSize(minHeight = AyahTouchTargets.min),
        ) {
            Text(
                text = if (summary.completedItems > 0 && summary.completedItems < summary.totalItems) {
                    stringResource(R.string.adhkar_continue_session)
                } else {
                    stringResource(R.string.adhkar_start_session)
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun DailySessionCard(
    summary: AdhkarCategorySummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (summary.totalItems > 0) {
        summary.completedItems / summary.totalItems.toFloat()
    } else {
        0f
    }
    val allDone = summary.totalItems > 0 && summary.completedItems >= summary.totalItems
    AyahSurfaceCard(
        onClick = onClick,
        modifier = modifier.testTag("adhkar_cat_${summary.category.id}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 96.dp)
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            Text(
                text = localizedCategoryTitle(summary.category),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = AyahNumbering.isolateLtr("${summary.completedItems}/${summary.totalItems}"),
                    style = MaterialTheme.typography.labelMedium.withTabularNumbers(),
                    color = if (allDone) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (allDone) {
                    AyahStatusBadge(
                        label = stringResource(R.string.adhkar_item_completed),
                        kind = AyahStatusKind.SUCCESS,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(AyahRadii.pill)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

@Composable
private fun DhikrSearchRowCard(
    item: DhikrItem,
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    AyahSurfaceCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
            ) {
                Text(
                    text = localizedItemTitle(item),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = localizedItemSource(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AyahStatusBadge(
                label = AyahNumbering.isolateLtr("${item.repeatCount}×"),
                kind = AyahStatusKind.GOLD,
                icon = null,
            )
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) AyahIcons.BookmarkFilled else AyahIcons.BookmarkOutline,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.adhkar_unfavorite else R.string.adhkar_favorite,
                    ),
                    tint = if (isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

internal fun localizedCategoryTitle(category: DhikrCategory): String =
    if (AppLocale.isArabic) category.titleAr else category.titleEn

internal fun localizedItemTitle(item: DhikrItem): String =
    if (AppLocale.isArabic) item.titleAr else item.titleEn

internal fun localizedItemSource(item: DhikrItem): String =
    if (AppLocale.isArabic) item.sourceAr else item.sourceEn
