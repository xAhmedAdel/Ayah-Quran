package com.ayah.quran.ui.companion.hadith

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahGroupCard
import com.ayah.quran.core.designsystem.component.AyahInsetDivider
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.designsystem.component.AyahSurfaceCard
import com.ayah.quran.core.designsystem.component.EmptyState
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.LocalAyahColors
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.locale.AppLocale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HadithDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HadithDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMsg = stringResource(R.string.hadith_saved_toast)
    val removedMsg = stringResource(R.string.hadith_removed_toast)
    val copiedMsg = stringResource(R.string.hadith_copied_toast)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = localizedCollectionTitle(state.collection)
                            .ifBlank { stringResource(R.string.hadith_detail_title) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                    val hadith = state.hadith
                    if (hadith != null) {
                        IconButton(
                            onClick = {
                                copyHadithText(context, hadith, state.collection)
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(copiedMsg)
                                }
                            },
                            modifier = Modifier.testTag("hadith_detail_copy"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.hadith_copy),
                            )
                        }
                        IconButton(
                            onClick = {
                                val willSave = !hadith.isFavorite
                                viewModel.toggleFavorite()
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(if (willSave) savedMsg else removedMsg)
                                }
                            },
                            modifier = Modifier.testTag("hadith_detail_favorite"),
                        ) {
                            Icon(
                                imageVector = if (hadith.isFavorite) {
                                    AyahIcons.BookmarkFilled
                                } else {
                                    AyahIcons.BookmarkOutline
                                },
                                contentDescription = stringResource(
                                    if (hadith.isFavorite) R.string.hadith_unsave_action else R.string.hadith_save_action,
                                ),
                                tint = if (hadith.isFavorite) {
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

            state.notFound || state.hadith == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(AyahSpacing.m),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = AyahIcons.Search,
                        title = stringResource(R.string.hadith_not_found),
                        message = "",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            else -> {
                val hadith = state.hadith!!
                val collection = state.collection
                val extended = LocalAyahColors.current
                val gradeText = localizedHadithGrade(hadith)
                val explanationText = localizedHadithExplanation(hadith)
                var explanationExpanded by remember(hadith.id) { mutableStateOf(false) }
                val smallerDesc = stringResource(R.string.hadith_font_smaller)
                val largerDesc = stringResource(R.string.hadith_font_larger)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.s),
                    verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
                ) {
                    Surface(
                        shape = RoundedCornerShape(AyahRadii.card),
                        color = extended.mushafCanvas,
                        border = BorderStroke(AyahTouchTargets.hairline, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AyahSpacing.ml),
                            verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (hadith.hadithNumber.isNotBlank()) {
                                    AyahStatusBadge(
                                        label = AyahNumbering.isolateLtr("#${hadith.hadithNumber}"),
                                        kind = AyahStatusKind.NEUTRAL,
                                    )
                                } else {
                                    Box(modifier = Modifier.weight(1f))
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { viewModel.adjustFontScale(-0.1f) },
                                        contentPadding = PaddingValues(horizontal = AyahSpacing.s),
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .semantics { contentDescription = smallerDesc },
                                    ) {
                                        Text(
                                            text = AyahNumbering.isolateLtr("A-"),
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                    TextButton(
                                        onClick = { viewModel.adjustFontScale(0.1f) },
                                        contentPadding = PaddingValues(horizontal = AyahSpacing.s),
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .semantics { contentDescription = largerDesc },
                                    ) {
                                        Text(
                                            text = AyahNumbering.isolateLtr("A+"),
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }

                            if (gradeText.isNotBlank()) {
                                AyahStatusBadge(
                                    label = gradeText,
                                    kind = resolveGradeBadgeKind(hadith.gradeAr, hadith.gradeEn),
                                    modifier = Modifier.testTag("hadith_detail_grade_badge"),
                                )
                            }

                            val title = localizedHadithTitle(hadith)
                            if (title.isNotBlank() && title != hadith.textAr) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            SelectionContainer {
                                Text(
                                    text = hadith.textAr,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = QuranTextFontFamily,
                                        fontSize = (21f * state.fontScale).sp,
                                        lineHeight = (38f * state.fontScale).sp,
                                        textDirection = TextDirection.Rtl,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("hadith_detail_arabic_text"),
                                )
                            }

                            if (!hadith.textEn.isNullOrBlank()) {
                                HorizontalDivider(
                                    thickness = AyahTouchTargets.hairline,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                                Text(
                                    text = stringResource(R.string.hadith_english_translation),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                SelectionContainer {
                                    Text(
                                        text = hadith.textEn,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = (16f * state.fontScale).sp,
                                            lineHeight = (26f * state.fontScale).sp,
                                            textDirection = TextDirection.Ltr,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("hadith_detail_english_text"),
                                    )
                                }
                            }
                        }
                    }

                    if (!explanationText.isNullOrBlank()) {
                        AyahSurfaceCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.s),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = AyahTouchTargets.min)
                                        .clickable { explanationExpanded = !explanationExpanded }
                                        .testTag("hadith_detail_explanation_toggle"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.hadith_explanation_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Icon(
                                        imageVector = if (explanationExpanded) {
                                            Icons.Outlined.ExpandLess
                                        } else {
                                            Icons.Outlined.ExpandMore
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (explanationExpanded) {
                                    Text(
                                        text = explanationText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            textDirection = if (AppLocale.isArabic) {
                                                TextDirection.Rtl
                                            } else {
                                                TextDirection.Ltr
                                            },
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = AyahSpacing.s),
                                    )
                                }
                            }
                        }
                    }

                    val metaRows = buildList {
                        val colTitle = localizedCollectionTitle(collection)
                        if (colTitle.isNotBlank()) {
                            add(stringResource(R.string.hadith_meta_collection) to colTitle)
                        }
                        val chapter = localizedHadithChapter(hadith)
                        if (chapter.isNotBlank() && chapter != colTitle) {
                            add(stringResource(R.string.hadith_meta_chapter) to chapter)
                        }
                        if (hadith.hadithNumber.isNotBlank()) {
                            add(
                                stringResource(R.string.hadith_meta_number) to
                                    AyahNumbering.isolateLtr(hadith.hadithNumber),
                            )
                        }
                        if (gradeText.isNotBlank()) {
                            add(stringResource(R.string.hadith_meta_grade) to gradeText)
                        }
                        if (hadith.graderAttribution.isNotBlank()) {
                            add(stringResource(R.string.hadith_meta_grader) to hadith.graderAttribution)
                        }
                        val ref = localizedHadithReference(hadith)
                        if (ref.isNotBlank()) {
                            add(stringResource(R.string.hadith_meta_reference) to ref)
                        }
                        val srcAttr = collection?.sourceAttribution.orEmpty()
                        if (srcAttr.isNotBlank()) {
                            add(stringResource(R.string.hadith_meta_source) to srcAttr)
                        }
                        if (hadith.sourceVersion.isNotBlank()) {
                            add(
                                stringResource(R.string.hadith_meta_version) to
                                    AyahNumbering.isolateLtr(hadith.sourceVersion),
                            )
                        }
                    }

                    if (metaRows.isNotEmpty()) {
                        AyahGroupCard(
                            title = stringResource(R.string.hadith_attribution_section),
                            modifier = Modifier.testTag("hadith_detail_attribution_card"),
                        ) {
                            metaRows.forEachIndexed { idx, (label, value) ->
                                HadithMetadataRow(label = label, value = value)
                                if (idx != metaRows.lastIndex) {
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

@Composable
private fun HadithMetadataRow(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
