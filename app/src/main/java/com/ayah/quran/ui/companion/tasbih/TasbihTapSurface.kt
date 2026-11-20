package com.ayah.quran.ui.companion.tasbih

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.designsystem.motion.MotionDurations
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahThemeTokens
import com.ayah.quran.core.designsystem.theme.withTabularNumbers
import com.ayah.quran.domain.model.companion.TasbihCounter

@Composable
fun TasbihTapSurface(
    counter: TasbihCounter,
    todayTotal: Int,
    goalReached: Boolean,
    remainingToGoal: Int,
    progressFraction: Float,
    motionEnabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = AyahThemeTokens.colors
    val interactionSource = remember { MutableInteractionSource() }
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = MotionDurations.resolve(160, motionEnabled)),
        label = "tasbih_ring_progress",
    )

    val a11yLabel = when {
        counter.targetCount <= 0 -> stringResource(
            R.string.tasbih_counter_a11y_free,
            counter.dhikrTextAr,
            counter.currentCount,
        )
        goalReached -> stringResource(
            R.string.tasbih_counter_a11y_reached,
            counter.dhikrTextAr,
            counter.currentCount,
            counter.targetCount,
        )
        else -> stringResource(
            R.string.tasbih_counter_a11y_goal,
            counter.dhikrTextAr,
            counter.currentCount,
            counter.targetCount,
        )
    }

    Surface(
        shape = RoundedCornerShape(AyahRadii.hero),
        color = tokens.heroSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (goalReached) tokens.heroAccent else tokens.heroOnSurface.copy(alpha = 0.14f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 320.dp)
            .clip(RoundedCornerShape(AyahRadii.hero))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = tokens.heroAccent),
                role = Role.Button,
                onClick = onTap,
            )
            .testTag("tasbih_tap_surface")
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = a11yLabel
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AyahSpacing.l, vertical = AyahSpacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (goalReached) {
                AyahStatusBadge(
                    label = stringResource(R.string.tasbih_goal_reached),
                    kind = AyahStatusKind.GOLD,
                    icon = Icons.Filled.Check,
                    modifier = Modifier.testTag("tasbih_goal_reached_badge"),
                )
                Spacer(Modifier.height(AyahSpacing.sm))
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(216.dp),
            ) {
                val trackColor = tokens.heroOnSurface.copy(alpha = 0.14f)
                val arcColor = tokens.heroAccent
                Canvas(modifier = Modifier.matchParentSize()) {
                    val strokePx = 10.dp.toPx()
                    val inset = strokePx / 2f
                    val arcSize = Size(size.width - strokePx, size.height - strokePx)
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                    if (counter.targetCount > 0 && animatedProgress > 0f) {
                        drawArc(
                            color = arcColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round),
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
                    modifier = Modifier.padding(horizontal = AyahSpacing.m),
                ) {
                    Text(
                        text = AyahNumbering.isolateLtr(counter.currentCount.toString()),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 54.sp,
                            fontWeight = FontWeight.SemiBold,
                        ).withTabularNumbers(),
                        color = tokens.heroOnSurface,
                        maxLines = 1,
                        modifier = Modifier.testTag("tasbih_count_value"),
                    )
                    if (counter.targetCount > 0) {
                        Text(
                            text = AyahNumbering.isolateLtr("${counter.currentCount} / ${counter.targetCount}"),
                            style = MaterialTheme.typography.titleMedium.withTabularNumbers(),
                            color = tokens.heroAccent,
                            maxLines = 1,
                            modifier = Modifier.testTag("tasbih_goal_ratio"),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.tasbih_free_count),
                            style = MaterialTheme.typography.labelLarge,
                            color = tokens.heroMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(Modifier.height(AyahSpacing.m))

            val statusSubtitle = when {
                goalReached -> stringResource(R.string.tasbih_goal_reached_hint)
                counter.targetCount > 0 -> stringResource(R.string.tasbih_remaining, remainingToGoal)
                else -> stringResource(R.string.tasbih_tap_to_count)
            }
            Text(
                text = statusSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.heroOnSurface.copy(alpha = 0.88f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(AyahSpacing.xs))

            Text(
                text = stringResource(
                    R.string.tasbih_today_total,
                    AyahNumbering.isolateLtr(todayTotal.toString()),
                ),
                style = MaterialTheme.typography.labelMedium.withTabularNumbers(),
                color = tokens.heroMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("tasbih_today_total"),
            )
        }
    }
}
