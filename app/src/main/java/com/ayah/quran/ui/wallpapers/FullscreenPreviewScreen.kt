package com.ayah.quran.ui.wallpapers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.component.AyahPrimaryButton
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.domain.model.WallpaperApplyStatus
import com.ayah.quran.domain.model.WallpaperDestination

private val PreviewScrimDark = Color(0xFF0C1210)
private val PreviewLightInk = Color(0xFFF6F2E9)
private val PreviewMutedInk = Color(0xFFD5CEBF)
private val PreviewGoldHairline = Color(0x45C4A052)
private val PreviewGoldAccent = Color(0xFFC4A052)
private val PreviewSuccessAccent = Color(0xFF5FB892)
private val PreviewErrorAccent = Color(0xFFF09898)

@Composable
fun FullscreenPreviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FullscreenPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PreviewScrimDark),
    ) {
        val bitmap = state.bitmap
        when {
            state.busy -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = PreviewGoldAccent)
            }

            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.preview_wallpaper_image),
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.56f),
            border = BorderStroke(AyahTouchTargets.hairline, Color.White.copy(alpha = 0.24f)),
            modifier = Modifier
                .statusBarsPadding()
                .padding(AyahSpacing.m)
                .size(44.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = AyahIcons.Close,
                    contentDescription = stringResource(R.string.preview_close),
                    tint = Color.White,
                )
            }
        }

        Surface(
            color = PreviewScrimDark.copy(alpha = 0.88f),
            shape = RoundedCornerShape(topStart = AyahRadii.sheet, topEnd = AyahRadii.sheet),
            border = BorderStroke(AyahTouchTargets.hairline, PreviewGoldHairline),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = AyahSpacing.ml, vertical = AyahSpacing.m),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
            ) {
                state.ayahLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = PreviewMutedInk,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val destinations = listOf(
                        WallpaperDestination.SYSTEM to R.string.wallpapers_dest_home,
                        WallpaperDestination.LOCK to R.string.wallpapers_dest_lock,
                        WallpaperDestination.BOTH to R.string.wallpapers_dest_both,
                    )
                    destinations.forEachIndexed { index, (destination, labelRes) ->
                        SegmentedButton(
                            selected = state.destinations == destination,
                            onClick = { viewModel.setDestinations(destination) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = destinations.size,
                            ),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = Color(0xFF1D4D3F),
                                activeContentColor = PreviewLightInk,
                                inactiveContainerColor = Color(0xFF141E19),
                                inactiveContentColor = PreviewMutedInk,
                                activeBorderColor = PreviewGoldHairline,
                                inactiveBorderColor = PreviewGoldHairline,
                            ),
                            label = { Text(stringResource(labelRes), maxLines = 1) },
                        )
                    }
                }

                AyahPrimaryButton(
                    text = if (state.applying) {
                        stringResource(R.string.wallpapers_apply_working)
                    } else {
                        stringResource(R.string.preview_apply)
                    },
                    onClick = viewModel::applyWallpaper,
                    enabled = !state.applying && bitmap != null,
                    loading = state.applying,
                    modifier = Modifier.fillMaxWidth(),
                )

                AnimatedVisibility(
                    visible = state.applyOutcome != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    state.applyOutcome?.let { outcome ->
                        val success = outcome == WallpaperApplyStatus.SUCCESS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (success) Icons.Filled.Check else Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = if (success) PreviewSuccessAccent else PreviewErrorAccent,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = if (success) {
                                    stringResource(
                                        R.string.wallpapers_apply_success,
                                        wallpaperDestinationLabel(state.destinations),
                                    )
                                } else {
                                    stringResource(R.string.wallpapers_apply_failed, "-")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (success) PreviewLightInk else PreviewErrorAccent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun wallpaperDestinationLabel(destination: WallpaperDestination): String = stringResource(
    when (destination) {
        WallpaperDestination.SYSTEM -> R.string.wallpapers_apply_home
        WallpaperDestination.LOCK -> R.string.wallpapers_apply_lock
        WallpaperDestination.BOTH -> R.string.wallpapers_apply_both
    },
)
