package com.ayah.quran.domain.repository

import kotlinx.coroutines.flow.Flow

/** Reader-specific settings (font size). */
data class ReaderSettings(
    /** Multiplier on the base reader font size (0.5..2.0, default 1.0). */
    val fontScale: Float,
)

interface ReaderPreferencesRepository {

    /** Emits the current reader settings and every subsequent change. */
    val settings: Flow<ReaderSettings>

    /** Atomically applies a transformation to the stored settings. */
    suspend fun update(transform: (ReaderSettings) -> ReaderSettings)
}
