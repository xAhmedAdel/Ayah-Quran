package com.ayah.quran.background

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ayah.quran.domain.usecase.ApplyWallpaperAutomaticallyUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic wallpaper update. Runs entirely off the UI, selects the next
 * verse via the configured mode, renders with the saved template/settings,
 * applies to the configured destinations, and records the outcome. A failed
 * cycle is reported (and recorded in history) as failure — never as success.
 */
@HiltWorker
class WallpaperWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val applyWallpaperAutomatically: ApplyWallpaperAutomaticallyUseCase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val status = applyWallpaperAutomatically()
            if (status == com.ayah.quran.domain.model.WallpaperApplyStatus.SUCCESS) {
                Log.i(TAG, "automatic wallpaper update applied")
                Result.success()
            } else {
                // Deterministic failure (render/validation); retrying the same
                // input would fail again. Recorded in history either way.
                Log.w(TAG, "automatic wallpaper update failed")
                Result.failure()
            }
        } catch (t: Throwable) {
            // Unexpected error (e.g. transient IO): let WorkManager retry with
            // its exponential backoff.
            Log.e(TAG, "automatic wallpaper update crashed", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "WallpaperWorker"

        const val UNIQUE_WORK_NAME = "wallpaper_periodic_update"
    }
}
