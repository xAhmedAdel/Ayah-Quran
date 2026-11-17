package com.ayah.quran.domain.repository

import com.ayah.quran.domain.model.ReadingPosition
import kotlinx.coroutines.flow.Flow

/** The user's most recent reading position ("Continue Reading"). */
interface ReadingProgressRepository {

    /** Emits the saved position, or null when nothing was read yet. */
    fun observeReadingPosition(): Flow<ReadingPosition?>

    /** Atomically replaces the stored position (single-row upsert). */
    suspend fun saveReadingPosition(surahId: Int, ayahNumber: Int)
}
