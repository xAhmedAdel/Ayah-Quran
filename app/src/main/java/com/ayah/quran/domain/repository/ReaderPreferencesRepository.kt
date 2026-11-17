package com.ayah.quran.domain.repository

import com.ayah.quran.domain.model.ReadingMode
import kotlinx.coroutines.flow.Flow

/** Reader-specific settings (font size, reading mode). */
data class ReaderSettings(
    /** Multiplier on the base reader font size (0.5..2.0, default 1.0). */
    val fontScale: Float,
    /** MUSHAF (authentic 604-page mode, default) or CONTINUOUS. */
    val readingMode: ReadingMode,
)

interface ReaderPreferencesRepository {

    /** Emits the current reader settings and every subsequent change. */
    val settings: Flow<ReaderSettings>

    /** Atomically applies a transformation to the stored settings. */
    suspend fun update(transform: (ReaderSettings) -> ReaderSettings)
}
