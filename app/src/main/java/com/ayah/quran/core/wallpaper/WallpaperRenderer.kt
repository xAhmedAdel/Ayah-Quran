package com.ayah.quran.core.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.ayah.quran.R
import com.ayah.quran.domain.model.TextVerticalPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

/** Inputs for one wallpaper render. */
data class RenderParams(
    // Verbatim dataset text; the renderer never transforms it.
    val ayahText: String,
    val referenceLabel: String,
    val template: WallpaperTemplate,
    val widthPx: Int,
    val heightPx: Int,
    /** Multiplier on the template's base text size (0.6..1.6 supported). */
    val fontScale: Float,
    val verticalPosition: TextVerticalPosition,
)

/** Render output plus layout metrics used by tests and validation. */
data class RenderResult(
    val bitmap: Bitmap,
    val textLineCount: Int,
    /** Final text size after the fit-to-content shrink pass. */
    val usedTextSizePx: Int,
    /** The safe content area the text block was placed in. */
    val contentBounds: RectF,
)

/**
 * Bitmap/Canvas wallpaper renderer.
 *
 * Arabic correctness: layout uses Android's text stack (HarfBuzz shaping via
 * StaticLayout) with the bundled Amiri typeface and an explicit RTL text
 * direction; the input string is never reordered or transformed. A shrink
 * pass guarantees the verse fits the safe content area — Quranic text is
 * never ellipsized or clipped.
 */
@Singleton
class WallpaperRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val amiriTypeface: Typeface? by lazy {
        ResourcesCompat.getFont(context, R.font.amiri_regular)
    }

    fun render(params: RenderParams): RenderResult {
        val width = params.widthPx
        val height = params.heightPx
        require(width in 1..MAX_SIDE_PX) { "width out of range: $width" }
        require(height in 1..MAX_SIDE_PX) { "height out of range: $height" }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val template = params.template

        drawBackground(canvas, width, height, template.background)

        val insets = template.contentInsets
        val contentLeft = width * insets.horizontalFraction
        val contentRight = width * (1f - insets.horizontalFraction)
        val contentTop = height * insets.topFraction
        val contentBottom = height * (1f - insets.bottomFraction)
        val contentWidth = (contentRight - contentLeft).toInt()
        val contentHeight = contentBottom - contentTop
        val contentBounds = RectF(contentLeft, contentTop, contentRight, contentBottom)

        // Text layout with a shrink pass that guarantees the verse (and the
        // reference label below it) fit inside the safe area. Never ellipsizes.
        // NOTE: Paint.textAlign must stay LEFT (default) — StaticLayout
        // performs its own RTL-aware centering; a CENTER paint alignment
        // double-offsets RTL glyph runs (verified via line-geometry probe).
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = amiriTypeface
            color = template.textColor
        }
        val referenceTextSize = height * template.baseTextSizeFraction * 0.40f * params.fontScale.coerceIn(0.6f, 1.6f)
        val reservedForReference = referenceTextSize * 2.6f
        val availableTextHeight = contentHeight - reservedForReference

        var textSize = height * template.baseTextSizeFraction * params.fontScale.coerceIn(0.6f, 1.6f)
        var layout = buildLayout(params.ayahText, textPaint, textSize, contentWidth, template)
        while (layout.height > availableTextHeight && textSize > MIN_TEXT_SIZE_FRACTION * height) {
            textSize *= SHRINK_FACTOR
            layout = buildLayout(params.ayahText, textPaint, textSize, contentWidth, template)
        }

        val blockHeight = layout.height + reservedForReference
        val blockTop = when (params.verticalPosition) {
            TextVerticalPosition.TOP -> contentTop
            TextVerticalPosition.BOTTOM -> contentBottom - blockHeight
            TextVerticalPosition.CENTER -> contentTop + (contentHeight - blockHeight) / 2f
        }

        drawDecorations(canvas, width, height, template, contentBounds, blockTop, blockHeight)

        canvas.withTranslation(contentLeft, blockTop) {
            layout.draw(this)
        }

        // Reference label below the verse, above the bottom safe edge.
        // NOTE: `this.textSize` must be explicit — an unqualified assignment
        // would shadow the enclosing local `textSize` (the render size).
        val referencePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = amiriTypeface
            color = template.referenceColor
            this.textSize = referenceTextSize
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.05f
        }
        val referenceY = blockTop + layout.height + referenceTextSize * 1.7f
        canvas.drawText(
            params.referenceLabel,
            width / 2f,
            referenceY,
            referencePaint,
        )

        return RenderResult(
            bitmap = bitmap,
            textLineCount = layout.lineCount,
            usedTextSizePx = textSize.toInt(),
            contentBounds = contentBounds,
        )
    }

    private fun buildLayout(
        text: String,
        paint: TextPaint,
        textSize: Float,
        width: Int,
        template: WallpaperTemplate,
    ): StaticLayout {
        paint.textSize = textSize
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(android.text.TextDirectionHeuristics.RTL)
            // Amiri's natural line metrics are already tall (~2.5x em, reserving
            // room for stacked diacritics); the template multiplier fine-tunes
            // on top and must stay near 1.0.
            .setLineSpacing(0f, template.lineSpacingMultiplier)
            .setIncludePad(true)
            .build()
    }

    private fun drawBackground(
        canvas: Canvas,
        width: Int,
        height: Int,
        background: WallpaperTemplate.BackgroundSpec,
    ) {
        val paint = Paint()
        when (background) {
            is WallpaperTemplate.BackgroundSpec.Gradient -> {
                paint.shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    background.topColor, background.bottomColor,
                    Shader.TileMode.CLAMP,
                )
            }

            is WallpaperTemplate.BackgroundSpec.Solid -> paint.color = background.color
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawDecorations(
        canvas: Canvas,
        width: Int,
        height: Int,
        template: WallpaperTemplate,
        contentBounds: RectF,
        blockTop: Float,
        blockHeight: Float,
    ) {
        val accent = template.accentColor
        val cx = width / 2f
        val cy = contentBounds.centerY()

        for (decoration in template.decorations) {
            when (decoration) {
                WallpaperTemplate.Decoration.RADIAL_GLOW -> {
                    val radius = min(contentBounds.width(), contentBounds.height()) * 0.9f
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(
                            cx, blockTop + blockHeight / 2f, radius,
                            withAlpha(accent, 34), Color.TRANSPARENT,
                            Shader.TileMode.CLAMP,
                        )
                    }
                    canvas.drawPaint(paint)
                }

                WallpaperTemplate.Decoration.FRAME -> {
                    val stroke = min(width, height) * 0.0032f
                    val inset = min(width, height) * 0.035f
                    val paint = strokePaint(accent, alpha = 110, strokeWidth = stroke)
                    canvas.drawRect(
                        inset.toFloat(),
                        inset.toFloat(),
                        (width - inset).toFloat(),
                        (height - inset).toFloat(),
                        paint,
                    )
                }

                WallpaperTemplate.Decoration.RULES -> {
                    val stroke = min(width, height) * 0.002f
                    val paint = strokePaint(accent, alpha = 150, strokeWidth = stroke)
                    val ruleWidth = contentBounds.width() * 0.62f
                    val left = cx - ruleWidth / 2f
                    val right = cx + ruleWidth / 2f
                    canvas.drawLine(left, blockTop - stroke * 9, right, blockTop - stroke * 9, paint)
                    canvas.drawLine(
                        left,
                        blockTop + blockHeight + stroke * 9,
                        right,
                        blockTop + blockHeight + stroke * 9,
                        paint,
                    )
                }

                WallpaperTemplate.Decoration.STARS -> {
                    val random = Random(STARS_SEED) // deterministic layout
                    val count = (width * height / 90_000).coerceIn(24, 160)
                    val starSize = min(width, height) * 0.004f
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 70) }
                    repeat(count) {
                        val x = random.nextFloat() * width
                        val y = random.nextFloat() * height
                        drawEightPointStar(canvas, paint, x, y, starSize * (0.6f + random.nextFloat()))
                    }
                }

                WallpaperTemplate.Decoration.STAR_LATTICE -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    paint.color = withAlpha(accent, 26)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = min(width, height) * 0.0012f
                    val step = height / 9f
                    var y = step / 2f
                    var row = 0
                    while (y < height) {
                        val offset = if (row % 2 == 0) 0f else step / 2f
                        var x = offset
                        while (x < width) {
                            drawEightPointStar(canvas, paint, x, y, step * 0.26f)
                            x += step
                        }
                        y += step
                        row++
                    }
                }

                WallpaperTemplate.Decoration.ARCH -> {
                    val stroke = min(width, height) * 0.0022f
                    val paint = strokePaint(accent, alpha = 70, strokeWidth = stroke)
                    val archWidth = contentBounds.width() * 0.94f
                    val archHeight = contentBounds.height() * 0.94f
                    val left = cx - archWidth / 2f
                    val right = cx + archWidth / 2f
                    val bottom = contentBounds.bottom
                    val top = bottom - archHeight
                    val path = android.graphics.Path()
                    path.moveTo(left, bottom)
                    path.lineTo(left, top + archWidth / 2f)
                    // Pointed arch: two quadratic curves meeting at the apex.
                    path.quadTo(left, top + archWidth * 0.18f, cx, top)
                    path.quadTo(right, top + archWidth * 0.18f, right, top + archWidth / 2f)
                    path.lineTo(right, bottom)
                    canvas.drawPath(path, paint)
                }
            }
        }
    }

    private fun drawEightPointStar(canvas: Canvas, paint: Paint, cx: Float, cy: Float, radius: Float) {
        val path = android.graphics.Path()
        val points = 8
        val inner = radius * 0.42f
        for (i in 0 until points * 2) {
            val angle = Math.PI * i / points
            val r = if (i % 2 == 0) radius else inner
            val x = cx + (r * kotlin.math.cos(angle)).toFloat()
            val y = cy + (r * kotlin.math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun strokePaint(color: Int, alpha: Int, strokeWidth: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = withAlpha(color, alpha)
            this.strokeWidth = strokeWidth
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
        }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private inline fun Canvas.withTranslation(x: Float, y: Float, block: Canvas.() -> Unit) {
        val checkpoint = save()
        translate(x, y)
        try {
            block()
        } finally {
            restoreToCount(checkpoint)
        }
    }

    companion object {
        private const val SHRINK_FACTOR = 0.92f
        private const val MIN_TEXT_SIZE_FRACTION = 0.012f
        private const val STARS_SEED = 0xA71 /* deterministic star field */

        const val MAX_SIDE_PX = 3840
    }
}
