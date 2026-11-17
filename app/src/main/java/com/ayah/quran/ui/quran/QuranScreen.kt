package com.ayah.quran.ui.quran

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.core.locale.localizedDisplayName
import com.ayah.quran.core.locale.revelationLabel
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.component.AyahCard
import com.ayah.quran.core.designsystem.component.EmptyState
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.domain.model.Surah

/**
 * The Surah index. Backed by the imported verified dataset; shows import
 * progress, an honest failure state, and name search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranScreen(
    onOpenSurah: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuranViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.quran_title)) })

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(R.string.quran_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AyahSpacing.m),
        )

        when {
            state.importError != null -> ImportFailedState(
                reason = state.importError.orEmpty(),
                onRetry = viewModel::retryImport,
            )

            state.isLoading -> ImportingState(progress = state.importProgress)

            else -> SurahList(
                surahs = state.surahs,
                query = state.query,
                onOpenSurah = onOpenSurah,
            )
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
                onClick = { onOpenSurah(surah.id) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = AyahSpacing.m),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun SurahRow(
    surah: Surah,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.m),
    ) {
        Text(
            text = surah.id.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = surah.localizedDisplayName(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    R.string.surah_meta_format,
                    stringResource(revelationLabel(surah.revelationType)),
                    surah.ayahCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = surah.nameArabic,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = QuranTextFontFamily,
                fontSize = 24.sp,
                textDirection = TextDirection.Rtl,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
