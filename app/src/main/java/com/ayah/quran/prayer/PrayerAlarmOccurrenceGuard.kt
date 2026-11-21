package com.ayah.quran.prayer

import android.util.Log
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

object AlarmOccurrenceKey {

    fun normalizeDayKey(dayKey: String?): String? {
        if (dayKey == null || dayKey.contains('\n') || dayKey.contains('\r')) return null
        val trimmed = dayKey.trim().takeIf { it.isNotBlank() } ?: return null
        return runCatching { LocalDate.parse(trimmed).toString() }.getOrNull()
    }

    fun build(
        prayer: Prayer,
        dayKey: String?,
        scheduledAtMillis: Long,
        fallbackNowMillis: Long,
    ): String {
        val cleanDay = normalizeDayKey(dayKey)
        return when {
            cleanDay != null && scheduledAtMillis > 0L -> "${prayer.name}|$cleanDay|$scheduledAtMillis"
            cleanDay != null -> "${prayer.name}|$cleanDay"
            scheduledAtMillis > 0L -> "${prayer.name}|ts:$scheduledAtMillis"
            else -> "${prayer.name}|fallback:${fallbackNowMillis / 60_000L}"
        }
    }

    fun extractDayKey(occurrenceKey: String): LocalDate? {
        val parts = occurrenceKey.split('|')
        if (parts.size < 2) return null
        return runCatching { LocalDate.parse(parts[1]) }.getOrNull()
    }
}

@Singleton
class PrayerAlarmOccurrenceGuard @Inject constructor(
    private val preferencesRepository: PrayerPreferencesRepository,
) {

    private val guardMutex = Mutex()
    private val memoryCache = LinkedHashSet<String>()

    suspend fun tryMarkFired(
        occurrenceKey: String,
        retentionCutoffDate: LocalDate? = null,
    ): Boolean = guardMutex.withLock {
        pruneMemoryCache(retentionCutoffDate)
        if (occurrenceKey in memoryCache) {
            return@withLock false
        }
        return@withLock try {
            val newlyMarked = preferencesRepository.tryMarkAlarmOccurrenceFired(
                occurrenceKey = occurrenceKey,
                retentionCutoffDate = retentionCutoffDate,
            )
            rememberInMemory(occurrenceKey)
            newlyMarked
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: IOException) {
            rememberInMemory(occurrenceKey)
            Log.w(
                PrayerAlarmLog.TAG,
                "ALARM_GUARD_PERSIST_FAILED | fallback=in_memory | error=${e.javaClass.simpleName}",
            )
            true
        }
    }

    internal fun inMemorySizeForTest(): Int = memoryCache.size

    private fun rememberInMemory(key: String) {
        memoryCache.remove(key)
        memoryCache.add(key)
        while (memoryCache.size > MAX_ENTRIES) {
            val oldest = memoryCache.iterator().next()
            memoryCache.remove(oldest)
        }
    }

    private fun pruneMemoryCache(cutoffDate: LocalDate?) {
        if (cutoffDate == null) return
        val iterator = memoryCache.iterator()
        while (iterator.hasNext()) {
            val date = AlarmOccurrenceKey.extractDayKey(iterator.next())
            if (date != null && date.isBefore(cutoffDate)) {
                iterator.remove()
            }
        }
    }

    companion object {
        const val MAX_ENTRIES = 40
        const val RETENTION_DAYS = 7L
    }
}
