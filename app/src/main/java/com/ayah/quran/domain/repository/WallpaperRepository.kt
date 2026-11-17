package com.ayah.quran.domain.repository

import com.ayah.quran.core.wallpaper.WallpaperTemplate
import com.ayah.quran.domain.model.Ayah
import com.ayah.quran.domain.model.SelectionMode
import com.ayah.quran.domain.model.WallpaperApplyStatus
import com.ayah.quran.domain.model.WallpaperDestination
import com.ayah.quran.domain.model.WallpaperHistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * Generation, application, and history of Quranic wallpapers.
 *
 * Implementations must only report SUCCESS after Android's WallpaperManager
 * has accepted the bitmap; every attempt (success or failure) is recorded.
 */
interface WallpaperRepository {

    /**
     * Renders [ayah] with [template] at the device wallpaper dimensions and
     * applies it to [destinations]. The returned result reflects what Android
     * actually accepted.
     */
    suspend fun applyWallpaper(
        ayah: Ayah,
        template: WallpaperTemplate,
        destinations: WallpaperDestination,
        fontScale: Float,
        verticalPosition: com.ayah.quran.domain.model.TextVerticalPosition,
    ): WallpaperApplyStatus

    /** Renders at preview resolution without touching the system wallpaper. */
    suspend fun renderPreview(
        ayahText: String,
        referenceLabel: String,
        template: WallpaperTemplate,
        widthPx: Int,
        heightPx: Int,
        fontScale: Float,
        verticalPosition: com.ayah.quran.domain.model.TextVerticalPosition,
    ): android.graphics.Bitmap

    /** All history entries, newest first. */
    fun observeHistory(): Flow<List<WallpaperHistoryEntry>>

    /** Deletes one history entry (metadata only; never touches the system wallpaper). */
    suspend fun deleteHistoryEntry(id: Long)

    /** Clears all history entries (metadata only; never touches the system wallpaper). */
    suspend fun clearHistory()

    /** Re-applies a previous entry using the currently saved settings. */
    suspend fun reapplyFromHistory(entry: WallpaperHistoryEntry): WallpaperApplyStatus

    /** Loads one verse from the verified dataset by global id, or null. */
    suspend fun loadAyahByGlobalId(globalId: Long): Ayah?

    /** Loads one verse from the verified dataset by reference, or null. */
    suspend fun loadAyah(surahId: Int, ayahNumber: Int): Ayah?

    /** The [surahId]'s global-id range (contiguous), or null if unknown. */
    suspend fun surahGlobalIdRange(surahId: Int): Pair<Long, Long>?

    /** All global verse ids (the whole-mushaf selection pool). */
    suspend fun allGlobalIds(): List<Long>

    /** Current screen dimensions (width, height) in pixels. */
    suspend fun screenDimensions(): Pair<Int, Int>

    /** Transliterated surah name for reference labels, or null if unknown. */
    suspend fun surahName(surahId: Int): String?

    /** Records a selection for recent-ayah avoidance. */
    suspend fun recordSelection(surahId: Int, ayahNumber: Int, mode: SelectionMode)

    /** The most recent [limit] selected verse global IDs for avoidance. */
    suspend fun recentSelections(limit: Int = RECENT_AVOIDANCE_WINDOW): Set<Long>

    /** The user's selected ayah IDs ("surah:ayah" composite format). */
    val selectedAyahIds: Flow<Set<String>>

    /** Adds an ayah to the user's selection. No-op if already present. */
    suspend fun addSelectedAyah(surahId: Int, ayahNumber: Int)

    /** Removes an ayah from the user's selection. No-op if not present. */
    suspend fun removeSelectedAyah(surahId: Int, ayahNumber: Int)

    /** Clears all user-selected ayahs. */
    suspend fun clearSelectedAyahs()

    /** Persistent sequential cursor for SEQUENTIAL mode. */
    suspend fun sequentialCursor(): Long
    suspend fun saveSequentialCursor(cursor: Long)

    companion object {
        /** Number of most-recent selections avoided in random modes. */
        const val RECENT_AVOIDANCE_WINDOW = 50
    }
}
