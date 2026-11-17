package com.ayah.quran.data.wallpapers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ayah.quran.background.WallpaperWorker
import com.ayah.quran.domain.model.WallpaperScheduleStatus
import com.ayah.quran.domain.repository.WallpaperSchedulingRepository
import com.ayah.quran.domain.repository.WallpaperSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager-backed scheduling of [WallpaperWorker] as unique periodic
 * work. KEEP/UPDATE policies prevent duplicate workers; the work spec
 * persists across restarts by design.
 *
 * Interval reporting reads the user's saved setting (the only writer of the
 * schedule interval) — WorkManager exposes no periodic-interval accessor on
 * WorkInfo in the current version.
 */
@Singleton
class WallpaperSchedulingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: WallpaperSettingsRepository,
) : WallpaperSchedulingRepository {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override suspend fun enable(intervalMinutes: Int) {
        require(intervalMinutes in SUPPORTED_INTERVALS) {
            "unsupported interval: $intervalMinutes (supported: $SUPPORTED_INTERVALS)"
        }
        val request = PeriodicWorkRequestBuilder<WallpaperWorker>(
            intervalMinutes.toLong(),
            TimeUnit.MINUTES,
        )
            // Fully offline; skip cycles on critically low battery.
            .setConstraints(
                Constraints.Builder().setRequiresBatteryNotLow(true).build(),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WallpaperWorker.UNIQUE_WORK_NAME,
            // UPDATE applies new intervals without respawning the chain.
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override suspend fun pause() {
        workManager.cancelUniqueWork(WallpaperWorker.UNIQUE_WORK_NAME)
    }

    override fun observeStatus(): Flow<WallpaperScheduleStatus> =
        workManager.getWorkInfosForUniqueWorkFlow(WallpaperWorker.UNIQUE_WORK_NAME)
            .map { workInfos ->
                val scheduled = workInfos.any {
                    it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                }
                WallpaperScheduleStatus(
                    isScheduled = scheduled,
                    intervalMinutes = if (scheduled) {
                        settingsRepository.settings.first().intervalMinutes
                    } else {
                        null
                    },
                )
            }

    companion object {
        val SUPPORTED_INTERVALS = setOf(30, 60, 180, 360, 720, 1440)
    }
}
