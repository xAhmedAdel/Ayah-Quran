package com.ayah.quran.data.wallpapers

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.ayah.quran.core.database.dao.AyahDao
import com.ayah.quran.core.database.dao.SelectionHistoryDao
import com.ayah.quran.core.database.dao.SurahDao
import com.ayah.quran.core.database.dao.WallpaperHistoryDao
import com.ayah.quran.core.database.entity.AyahEntity
import com.ayah.quran.core.database.entity.SelectionHistoryEntity
import com.ayah.quran.core.database.entity.WallpaperHistoryEntity
import com.ayah.quran.core.wallpaper.RenderParams
import com.ayah.quran.core.wallpaper.WallpaperRenderer
import com.ayah.quran.core.wallpaper.WallpaperTemplate
import com.ayah.quran.domain.model.Ayah
import com.ayah.quran.domain.model.SelectionMode
import com.ayah.quran.domain.model.TextVerticalPosition
import com.ayah.quran.domain.model.WallpaperApplyStatus
import com.ayah.quran.core.locale.localizedDisplayName
import com.ayah.quran.domain.model.WallpaperDestination
import com.ayah.quran.domain.model.WallpaperHistoryEntry
import com.ayah.quran.domain.repository.WallpaperRepository
import com.ayah.quran.domain.repository.WallpaperSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderer: WallpaperRenderer,
    private val ayahDao: AyahDao,
    private val surahDao: SurahDao,
    private val wallpaperHistoryDao: WallpaperHistoryDao,
    private val selectionHistoryDao: SelectionHistoryDao,
    private val settingsRepository: WallpaperSettingsRepository,
    private val dataStore: DataStore<Preferences>,
) : WallpaperRepository {

    override suspend fun applyWallpaper(
        ayah: Ayah,
        template: WallpaperTemplate,
        destinations: WallpaperDestination,
        fontScale: Float,
        verticalPosition: TextVerticalPosition,
    ): WallpaperApplyStatus = withContext(Dispatchers.Default) {
        val manager = WallpaperManager.getInstance(context)
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
            .coerceIn(MIN_WALLPAPER_WIDTH, WallpaperRenderer.MAX_SIDE_PX)
        val height = metrics.heightPixels
            .coerceIn(MIN_WALLPAPER_HEIGHT, WallpaperRenderer.MAX_SIDE_PX)

        val bitmap = try {
            renderer.render(
                RenderParams(
                    ayahText = ayah.textUthmani,
                    referenceLabel = referenceLabel(ayah.surahId, ayah.ayahNumber),
                    template = template,
                    widthPx = width,
                    heightPx = height,
                    fontScale = fontScale,
                    verticalPosition = verticalPosition,
                ),
            ).bitmap
        } catch (t: Throwable) {
            Log.e(TAG, "render failed for ${ayah.surahId}:${ayah.ayahNumber}", t)
            recordHistory(ayah.surahId, ayah.ayahNumber, template.id, destinations, WallpaperApplyStatus.FAILED, t.message)
            return@withContext WallpaperApplyStatus.FAILED
        }

        try {
            val which = when (destinations) {
                WallpaperDestination.SYSTEM -> WallpaperManager.FLAG_SYSTEM
                WallpaperDestination.LOCK -> WallpaperManager.FLAG_LOCK
                WallpaperDestination.BOTH ->
                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            manager.setBitmap(bitmap, null, false, which)
            Log.i(TAG, "wallpaper applied to $which (${ayah.surahId}:${ayah.ayahNumber})")
            recordHistory(ayah.surahId, ayah.ayahNumber, template.id, destinations, WallpaperApplyStatus.SUCCESS, null)
            WallpaperApplyStatus.SUCCESS
        } catch (e: IOException) {
            Log.e(TAG, "WallpaperManager rejected the bitmap", e)
            recordHistory(ayah.surahId, ayah.ayahNumber, template.id, destinations, WallpaperApplyStatus.FAILED, e.message)
            WallpaperApplyStatus.FAILED
        } finally {
            // Do NOT recycle immediately: Media3/lock-screen may process the
            // bitmap asynchronously on some devices. GC will reclaim it.
        }
    }

    override suspend fun renderPreview(
        ayahText: String,
        referenceLabel: String,
        template: WallpaperTemplate,
        widthPx: Int,
        heightPx: Int,
        fontScale: Float,
        verticalPosition: TextVerticalPosition,
    ): Bitmap = withContext(Dispatchers.Default) {
        renderer.render(
            RenderParams(
                ayahText = ayahText,
                referenceLabel = referenceLabel,
                template = template,
                widthPx = widthPx,
                heightPx = heightPx,
                fontScale = fontScale,
                verticalPosition = verticalPosition,
            ),
        ).bitmap
    }

    override fun observeHistory(): Flow<List<WallpaperHistoryEntry>> =
        wallpaperHistoryDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun deleteHistoryEntry(id: Long) {
        wallpaperHistoryDao.deleteById(id)
    }

    override suspend fun clearHistory() {
        wallpaperHistoryDao.clearAll()
    }

    override suspend fun reapplyFromHistory(entry: WallpaperHistoryEntry): WallpaperApplyStatus {
        val ayahEntity = ayahDao.getAyah(entry.surahId, entry.ayahNumber) ?: run {
            Log.w(TAG, "reapply: verse ${entry.surahId}:${entry.ayahNumber} missing from dataset")
            return WallpaperApplyStatus.FAILED
        }
        val settings = settingsRepository.settings.first()
        return applyWallpaper(
            ayah = ayahEntity.toDomain(),
            template = WallpaperTemplate.byId(entry.templateId),
            destinations = entry.destinations,
            fontScale = settings.fontScale,
            verticalPosition = settings.verticalPosition,
        )
    }

    override suspend fun loadAyahByGlobalId(globalId: Long): Ayah? =
        ayahDao.getAyahById(globalId)?.toDomain()

    override suspend fun loadAyah(surahId: Int, ayahNumber: Int): Ayah? =
        ayahDao.getAyah(surahId, ayahNumber)?.toDomain()

    override suspend fun surahGlobalIdRange(surahId: Int): Pair<Long, Long>? =
        ayahDao.surahGlobalIdRange(surahId)?.let { it.minId to it.maxId }

    override suspend fun allGlobalIds(): List<Long> = ayahDao.globalIdsInRange(1L, 6236L)

    override suspend fun screenDimensions(): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    override suspend fun surahName(surahId: Int): String? =
        surahDao.getSurah(surahId)?.localizedDisplayName()

    override val selectedAyahIds: Flow<Set<String>>
        get() = dataStore.data.map { prefs ->
            prefs[SELECTED_AYAHS_KEY] ?: emptySet()
        }

    override suspend fun addSelectedAyah(surahId: Int, ayahNumber: Int) {
        dataStore.edit { prefs ->
            val current = prefs[SELECTED_AYAHS_KEY] ?: emptySet()
            prefs[SELECTED_AYAHS_KEY] = current + "$surahId:$ayahNumber"
        }
    }

    override suspend fun removeSelectedAyah(surahId: Int, ayahNumber: Int) {
        dataStore.edit { prefs ->
            val current = prefs[SELECTED_AYAHS_KEY] ?: emptySet()
            prefs[SELECTED_AYAHS_KEY] = current - "$surahId:$ayahNumber"
        }
    }

    override suspend fun clearSelectedAyahs() {
        dataStore.edit { prefs ->
            prefs.remove(SELECTED_AYAHS_KEY)
        }
    }

    override suspend fun recordSelection(surahId: Int, ayahNumber: Int, mode: SelectionMode) {
        val globalId = ayahDao.getAyah(surahId, ayahNumber)?.id?.toLong() ?: return
        selectionHistoryDao.insert(
            SelectionHistoryEntity(
                surahId = surahId,
                ayahNumber = ayahNumber,
                globalId = globalId,
                selectedAt = System.currentTimeMillis(),
                mode = mode.name,
            ),
        )
        selectionHistoryDao.trimTo(RECENT_WINDOW_KEEP)
    }

    override suspend fun recentSelections(limit: Int): Set<Long> =
        selectionHistoryDao.recent(limit).map { it.globalId }.toSet()

    override suspend fun sequentialCursor(): Long =
        dataStore.data.first()[SEQUENTIAL_CURSOR_KEY] ?: 0L

    override suspend fun saveSequentialCursor(cursor: Long) {
        dataStore.edit { it[SEQUENTIAL_CURSOR_KEY] = cursor }
    }

    private fun AyahEntity.toDomain() = Ayah(
        id = id,
        surahId = surahId,
        ayahNumber = ayahNumber,
        textUthmani = textUthmani,
        juz = juz,
        page = page,
    )

    private fun WallpaperHistoryEntity.toDomain() = WallpaperHistoryEntry(
        id = id,
        surahId = surahId,
        ayahNumber = ayahNumber,
        templateId = templateId,
        destinations = WallpaperDestination.entries.firstOrNull { it.name == destinations }
            ?: WallpaperDestination.BOTH,
        appliedAtMillis = appliedAt,
        status = WallpaperApplyStatus.entries.firstOrNull { it.name == status }
            ?: WallpaperApplyStatus.FAILED,
        failureReason = failureReason,
    )

    private suspend fun recordHistory(
        surahId: Int,
        ayahNumber: Int,
        templateId: String,
        destinations: WallpaperDestination,
        status: WallpaperApplyStatus,
        failureReason: String?,
    ) {
        wallpaperHistoryDao.insert(
            WallpaperHistoryEntity(
                surahId = surahId,
                ayahNumber = ayahNumber,
                templateId = templateId,
                destinations = destinations.name,
                appliedAt = System.currentTimeMillis(),
                status = status.name,
                failureReason = failureReason,
            ),
        )
        wallpaperHistoryDao.trimTo(HISTORY_RETENTION)
    }

    private suspend fun referenceLabel(surahId: Int, ayahNumber: Int): String {
        val name = surahDao.getSurah(surahId)?.localizedDisplayName() ?: "Surah $surahId"
        return "$name · $surahId:$ayahNumber"
    }

    companion object {
        private const val TAG = "WallpaperRepository"
        private const val MIN_WALLPAPER_WIDTH = 720
        private const val MIN_WALLPAPER_HEIGHT = 1280
        private const val RECENT_WINDOW_KEEP = 100
        const val HISTORY_RETENTION = 100

        val SEQUENTIAL_CURSOR_KEY = longPreferencesKey("wallpaper_sequential_cursor")
        val SELECTED_AYAHS_KEY = stringSetPreferencesKey("wallpaper_selected_ayahs")
    }
}
