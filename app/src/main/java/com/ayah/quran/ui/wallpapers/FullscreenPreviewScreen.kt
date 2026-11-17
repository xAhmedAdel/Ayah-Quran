package com.ayah.quran.ui.wallpapers

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.motion.MotionDurations
import com.ayah.quran.core.designsystem.motion.rememberMotionEnabled
import com.ayah.quran.domain.model.WallpaperApplyStatus

/**
 * Immersive full-screen wallpaper preview: production renderer output at
 * (near) screen dimensions, navigation chrome hidden by the navigation
 * layer, with a clear Apply action. Respects reduced-motion settings.
 */
@Composable
fun FullscreenPreviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FullscreenPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val motionEnabled = rememberMotionEnabled()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val bitmap = state.bitmap
        when {
            state.busy -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.preview_wallpaper_image),
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.preview_close),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            state.ayahLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = viewModel::applyWallpaper,
                enabled = !state.applying && bitmap != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                if (state.applying) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(stringResource(R.string.preview_apply))
            }
            state.applyOutcome?.let { outcome ->
                Text(
                    text = if (outcome == WallpaperApplyStatus.SUCCESS) {
                        stringResource(
                            R.string.wallpapers_apply_success,
                            stringResource(R.string.wallpapers_apply_both),
                        )
                    } else {
                        stringResource(R.string.wallpapers_apply_failed, "-")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (outcome == WallpaperApplyStatus.SUCCESS) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
