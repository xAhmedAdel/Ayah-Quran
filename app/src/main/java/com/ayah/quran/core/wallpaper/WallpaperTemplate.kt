package com.ayah.quran.core.wallpaper

/**
 * A wallpaper template, expressed entirely as structured data (spec §6.5) so
 * the renderer stays generic and new templates require no rendering code.
 *
 * Colors are ARGB ints (template spec format), fractions are relative to
 * canvas dimensions. Quran text readability always wins over decoration:
 * decoration is drawn at low alpha and never overlaps the text block.
 */
data class WallpaperTemplate(
    val id: String,
    val displayName: String,
    val background: BackgroundSpec,
    val decorations: List<Decoration>,
    /** Uthmani text color (ARGB). */
    val textColor: Int,
    /** Surah/reference label color (ARGB). */
    val referenceColor: Int,
    /** Ornament/accent color (ARGB). */
    val accentColor: Int,
    /**
     * Base text size as a fraction of the canvas height, before the user's
     * font-scale adjustment and before the fit-to-content shrink pass.
     */
    val baseTextSizeFraction: Float,
    val lineSpacingMultiplier: Float,
    /**
     * Safe content area as fractions of canvas dimensions. Accounts for the
     * clock region (top), gesture/dock area (bottom), and punch-hole/cutout
     * (top + horizontal insets).
     */
    val contentInsets: ContentInsets,
) {
    data class ContentInsets(
        val topFraction: Float,
        val bottomFraction: Float,
        val horizontalFraction: Float,
    )

    sealed interface BackgroundSpec {
        data class Gradient(val topColor: Int, val bottomColor: Int) : BackgroundSpec
        data class Solid(val color: Int) : BackgroundSpec
    }

    enum class Decoration {
        /** Thin gold frame around the content area. */
        FRAME,
        /** Horizontal rules above and below the text block. */
        RULES,
        /** Soft radial glow behind the text block. */
        RADIAL_GLOW,
        /** Sparse 8-point star field. */
        STARS,
        /** Repeated geometric star lattice across the background. */
        STAR_LATTICE,
        /** Pointed-arch outline motif behind the text block. */
        ARCH,
    }

    companion object {
        const val EMERALD_LUXURY_ID = "emerald_luxury"
        const val MINIMAL_CREAM_ID = "minimal_cream"
        const val MIDNIGHT_GOLD_ID = "midnight_gold"
        const val ISLAMIC_ARCHITECTURE_ID = "islamic_architecture"
        const val GEOMETRIC_ART_ID = "geometric_art"

        private const val GOLD = 0xFFD5B56A.toInt()
        private const val GOLD_SOFT = 0xFFE8D3A2.toInt()
        private const val CREAM = 0xFFF7F2E8.toInt()
        private const val EMERALD_DEEP = 0xFF0E2B23.toInt()
        private const val EMERALD_BRIGHT = 0xFF1E5A49.toInt()
        private const val INK_TEXT = 0xFF17201B.toInt()
        private const val TEXT_ON_DARK = 0xFFF5F2EA.toInt()

        /** The five premium launch templates. */
        val ALL: List<WallpaperTemplate> = listOf(
            WallpaperTemplate(
                id = EMERALD_LUXURY_ID,
                displayName = "Emerald Luxury",
                background = BackgroundSpec.Gradient(EMERALD_DEEP, EMERALD_BRIGHT),
                decorations = listOf(Decoration.RADIAL_GLOW, Decoration.FRAME),
                textColor = GOLD_SOFT,
                referenceColor = GOLD,
                accentColor = GOLD,
                baseTextSizeFraction = 0.055f,
                lineSpacingMultiplier = 1.0f,
                contentInsets = ContentInsets(topFraction = 0.14f, bottomFraction = 0.12f, horizontalFraction = 0.10f),
            ),
            WallpaperTemplate(
                id = MINIMAL_CREAM_ID,
                displayName = "Minimal Cream",
                background = BackgroundSpec.Solid(CREAM),
                decorations = listOf(Decoration.RULES),
                textColor = INK_TEXT,
                referenceColor = 0xFF153D32.toInt(),
                accentColor = 0xFF153D32.toInt(),
                baseTextSizeFraction = 0.05f,
                lineSpacingMultiplier = 1.05f,
                contentInsets = ContentInsets(topFraction = 0.16f, bottomFraction = 0.14f, horizontalFraction = 0.12f),
            ),
            WallpaperTemplate(
                id = MIDNIGHT_GOLD_ID,
                displayName = "Midnight Gold",
                background = BackgroundSpec.Gradient(0xFF0B0F0D.toInt(), 0xFF161D19.toInt()),
                decorations = listOf(Decoration.STARS, Decoration.FRAME),
                textColor = GOLD,
                referenceColor = GOLD_SOFT,
                accentColor = GOLD,
                baseTextSizeFraction = 0.055f,
                lineSpacingMultiplier = 1.0f,
                contentInsets = ContentInsets(topFraction = 0.14f, bottomFraction = 0.12f, horizontalFraction = 0.10f),
            ),
            WallpaperTemplate(
                id = ISLAMIC_ARCHITECTURE_ID,
                displayName = "Islamic Architecture",
                background = BackgroundSpec.Gradient(0xFF0F3038.toInt(), 0xFF155E63.toInt()),
                decorations = listOf(Decoration.ARCH, Decoration.FRAME),
                textColor = TEXT_ON_DARK,
                referenceColor = GOLD_SOFT,
                accentColor = GOLD,
                baseTextSizeFraction = 0.052f,
                lineSpacingMultiplier = 1.02f,
                contentInsets = ContentInsets(topFraction = 0.15f, bottomFraction = 0.13f, horizontalFraction = 0.12f),
            ),
            WallpaperTemplate(
                id = GEOMETRIC_ART_ID,
                displayName = "Geometric Art",
                background = BackgroundSpec.Solid(0xFF101815.toInt()),
                decorations = listOf(Decoration.STAR_LATTICE, Decoration.FRAME),
                textColor = GOLD_SOFT,
                referenceColor = GOLD,
                accentColor = GOLD,
                baseTextSizeFraction = 0.053f,
                lineSpacingMultiplier = 1.0f,
                contentInsets = ContentInsets(topFraction = 0.14f, bottomFraction = 0.12f, horizontalFraction = 0.10f),
            ),
        )

        fun byId(id: String): WallpaperTemplate =
            ALL.firstOrNull { it.id == id } ?: ALL.first()
    }
}
