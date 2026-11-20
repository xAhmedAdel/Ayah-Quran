package com.ayah.quran.ui.companion.duas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahOutlinedButton
import com.ayah.quran.core.designsystem.component.AyahSearchBar
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.designsystem.component.AyahTonalButton
import com.ayah.quran.core.designsystem.component.EmptyState
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.LocalAyahColors
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.model.companion.DhikrItem
import com.ayah.quran.domain.model.companion.ResolvedQuranicDua
import com.ayah.quran.ui.companion.adhkar.copyDhikrText
import com.ayah.quran.ui.companion.adhkar.localizedCategoryTitle
import com.ayah.quran.ui.companion.adhkar.localizedItemSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuasHubScreen(
    onBack: () -> Unit,
    onOpenInMushaf: (surahId: Int, ayahNumber: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DuasHubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.adhkar_copied)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.duas_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AyahIcons.Back,
                            contentDescription = stringResource(R.string.reader_back),
                        )
                    }
                },
                actions = {
                    if (state.activeTab != DuaTab.QURANIC) {
                        IconButton(
                            onClick = viewModel::toggleSavedFilter,
                            modifier = Modifier.testTag("duas_saved_toggle"),
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
                    placeholder = stringResource(R.string.duas_search_hint),
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val tabs = listOf(
                        DuaTab.QURANIC to R.string.duas_tab_quranic,
                        DuaTab.PROPHETIC to R.string.duas_tab_prophetic,
                        DuaTab.SITUATIONAL to R.string.duas_tab_situational,
                    )
                    tabs.forEachIndexed { index, (tab, labelRes) ->
                        SegmentedButton(
                            selected = state.activeTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                            label = { Text(stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.testTag("duas_tab_${tab.name.lowercase()}"),
                        )
                    }
                }

                when (state.activeTab) {
                    DuaTab.QURANIC -> {
                        val quranicFilters = listOf(
                            null to R.string.duas_filter_all,
                            "dua_guidance" to R.string.duas_cat_guidance,
                            "dua_forgiveness" to R.string.duas_cat_forgiveness,
                            "dua_family" to R.string.duas_cat_family,
                            "dua_gratitude" to R.string.duas_cat_gratitude,
                            "dua_anxiety" to R.string.duas_cat_anxiety,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
                            items(quranicFilters) { (catId, labelRes) ->
                                FilterChip(
                                    selected = state.selectedQuranicCategory == catId,
                                    onClick = { viewModel.selectQuranicCategory(catId) },
                                    label = { Text(stringResource(labelRes), maxLines = 1) },
                                )
                            }
                        }
                    }

                    DuaTab.SITUATIONAL -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
                            item {
                                FilterChip(
                                    selected = state.selectedSituationalCategory == null,
                                    onClick = { viewModel.selectSituationalCategory(null) },
                                    label = { Text(stringResource(R.string.duas_filter_all), maxLines = 1) },
                                )
                            }
                            items(state.situationalCategories, key = { it.id }) { cat ->
                                FilterChip(
                                    selected = state.selectedSituationalCategory == cat.id,
                                    onClick = { viewModel.selectSituationalCategory(cat.id) },
                                    label = { Text(localizedCategoryTitle(cat), maxLines = 1) },
                                )
                            }
                        }
                    }

                    DuaTab.PROPHETIC -> Unit
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(AyahSpacing.m),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
            ) {
                when (state.activeTab) {
                    DuaTab.QURANIC -> {
                        if (state.quranicDuas.isEmpty()) {
                            item(key = "q_empty") {
                                EmptyState(
                                    icon = AyahIcons.Search,
                                    title = stringResource(R.string.duas_empty_search),
                                    message = state.query,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            items(state.quranicDuas, key = { it.pointer.id }) { dua ->
                                QuranicDuaCard(
                                    dua = dua,
                                    onOpenInMushaf = {
                                        onOpenInMushaf(dua.pointer.surahId, dua.pointer.startAyah)
                                    },
                                    onCopy = {
                                        val ref = formatQuranicRef(dua)
                                        copyDhikrText(context, dua.textUthmani, ref)
                                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                                    },
                                )
                            }
                        }
                    }

                    DuaTab.PROPHETIC -> {
                        if (state.propheticItems.isEmpty()) {
                            item(key = "p_empty") {
                                EmptyState(
                                    icon = if (state.savedOnly) AyahIcons.BookmarkOutline else AyahIcons.Search,
                                    title = stringResource(
                                        if (state.savedOnly) R.string.duas_empty_saved else R.string.duas_empty_search,
                                    ),
                                    message = state.query,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            items(state.propheticItems, key = { it.id }) { item ->
                                PropheticDuaCard(
                                    item = item,
                                    isFavorite = item.id in state.favoriteIds,
                                    onToggleFavorite = { viewModel.toggleFavorite(item.id) },
                                    onCopy = {
                                        copyDhikrText(context, item.textAr, localizedItemSource(item))
                                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                                    },
                                )
                            }
                        }
                    }

                    DuaTab.SITUATIONAL -> {
                        if (state.situationalItems.isEmpty()) {
                            item(key = "s_empty") {
                                EmptyState(
                                    icon = if (state.savedOnly) AyahIcons.BookmarkOutline else AyahIcons.Search,
                                    title = stringResource(
                                        if (state.savedOnly) R.string.duas_empty_saved else R.string.duas_empty_search,
                                    ),
                                    message = state.query,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            items(state.situationalItems, key = { it.id }) { item ->
                                PropheticDuaCard(
                                    item = item,
                                    isFavorite = item.id in state.favoriteIds,
                                    onToggleFavorite = { viewModel.toggleFavorite(item.id) },
                                    onCopy = {
                                        copyDhikrText(context, item.textAr, localizedItemSource(item))
                                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
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

@Composable
private fun QuranicDuaCard(
    dua: ResolvedQuranicDua,
    onOpenInMushaf: () -> Unit,
    onCopy: () -> Unit,
) {
    val extended = LocalAyahColors.current
    val title = if (AppLocale.isArabic) dua.pointer.titleAr else dua.pointer.titleEn
    val ref = formatQuranicRef(dua)

    Surface(
        shape = RoundedCornerShape(AyahRadii.card),
        color = extended.mushafCanvas,
        border = BorderStroke(AyahTouchTargets.hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quranic_dua_${dua.pointer.id}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AyahStatusBadge(
                    label = stringResource(R.string.duas_badge_quranic),
                    kind = AyahStatusKind.SUCCESS,
                    icon = null,
                )
            }

            Text(
                text = dua.textUthmani,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = QuranTextFontFamily,
                    fontSize = 21.sp,
                    lineHeight = 36.sp,
                    textDirection = TextDirection.Rtl,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = ref,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
            ) {
                AyahTonalButton(
                    text = stringResource(R.string.adhkar_open_in_mushaf),
                    onClick = onOpenInMushaf,
                    icon = AyahIcons.MushafMode,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_mushaf_${dua.pointer.id}"),
                )
                AyahOutlinedButton(
                    text = stringResource(R.string.adhkar_copy),
                    onClick = onCopy,
                    icon = Icons.Outlined.ContentCopy,
                )
            }
        }
    }
}

@Composable
private fun PropheticDuaCard(
    item: DhikrItem,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onCopy: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(AyahRadii.card),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(AyahTouchTargets.hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.titleAr,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AyahStatusBadge(
                    label = stringResource(R.string.duas_badge_prophetic),
                    kind = AyahStatusKind.GOLD,
                    icon = null,
                )
            }

            Text(
                text = item.textAr,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = QuranTextFontFamily,
                    fontSize = 20.sp,
                    lineHeight = 35.sp,
                    textDirection = TextDirection.Rtl,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!item.textEn.isNullOrBlank() && !AppLocale.isArabic) {
                Text(
                    text = item.textEn,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = localizedItemSource(item),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AyahOutlinedButton(
                    text = stringResource(R.string.adhkar_copy),
                    onClick = onCopy,
                    icon = Icons.Outlined.ContentCopy,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.defaultMinSize(
                        minWidth = AyahTouchTargets.min,
                        minHeight = AyahTouchTargets.min,
                    ),
                ) {
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
}

private fun formatQuranicRef(dua: ResolvedQuranicDua): String {
    val surahName = if (AppLocale.isArabic) dua.surahNameArabic else dua.surahNameEnglish
    val range = if (dua.pointer.startAyah == dua.pointer.endAyah) {
        "${dua.pointer.surahId}:${dua.pointer.startAyah}"
    } else {
        "${dua.pointer.surahId}:${dua.pointer.startAyah}-${dua.pointer.endAyah}"
    }
    return "$surahName · ${AyahNumbering.isolateLtr(range)}"
}
