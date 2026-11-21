package com.ayah.quran.prayer

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

interface AlarmWakeLockLease {
    val tokenId: Long
    fun release()
}

internal interface PlatformWakeLock {
    fun acquire(timeoutMs: Long)
    fun release()
    fun isHeld(): Boolean
}

internal fun interface PlatformWakeLockFactory {
    fun create(tag: String): PlatformWakeLock
}

@Singleton
class PrayerAlarmWakeLock internal constructor(
    private val wakeLockFactory: PlatformWakeLockFactory,
    private val elapsedRealtimeMs: () -> Long,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        wakeLockFactory = createAndroidFactory(context.applicationContext),
        elapsedRealtimeMs = SystemClock::elapsedRealtime,
    )

    private data class ManagedLease(
        val tokenId: Long,
        val platformWakeLock: PlatformWakeLock,
        val deadlineElapsedMs: Long,
        val released: AtomicBoolean = AtomicBoolean(false),
    ) {
        fun releasePlatformLockOnce() {
            if (!released.compareAndSet(false, true)) return
            runCatching {
                if (platformWakeLock.isHeld()) {
                    platformWakeLock.release()
                }
            }.onFailure { e ->
                Log.w(
                    PrayerAlarmLog.TAG,
                    "WAKELOCK_RELEASE_ERROR | token=$tokenId | error=${e.javaClass.simpleName}",
                )
            }
        }
    }

    private val lock = Any()
    private var nextTokenId: Long = 0L
    private val activeLeases = LinkedHashMap<Long, ManagedLease>()

    fun acquireHandover(timeoutMs: Long = HANDOVER_WAKELOCK_MAX_MILLIS): AlarmWakeLockLease {
        val effectiveTimeout = timeoutMs.coerceIn(1L, HANDOVER_WAKELOCK_MAX_MILLIS)
        val now = elapsedRealtimeMs()
        val expiredToRelease = mutableListOf<ManagedLease>()
        var assignedTokenId = INVALID_TOKEN_ID

        synchronized(lock) {
            collectExpiredLocked(now, expiredToRelease)
            val candidateTokenId = ++nextTokenId
            val platformLock = wakeLockFactory.create(WAKE_LOCK_TAG)
            val acquired = runCatching {
                platformLock.acquire(effectiveTimeout)
                true
            }.onFailure { e ->
                Log.w(
                    PrayerAlarmLog.TAG,
                    "WAKELOCK_ACQUIRE_ERROR | token=$candidateTokenId | error=${e.javaClass.simpleName}",
                )
            }.getOrDefault(false)
            if (acquired) {
                assignedTokenId = candidateTokenId
                activeLeases[assignedTokenId] = ManagedLease(
                    tokenId = assignedTokenId,
                    platformWakeLock = platformLock,
                    deadlineElapsedMs = now + effectiveTimeout,
                )
            }
        }

        expiredToRelease.forEach { it.releasePlatformLockOnce() }

        return object : AlarmWakeLockLease {
            override val tokenId: Long = assignedTokenId
            override fun release() {
                if (assignedTokenId > INVALID_TOKEN_ID) {
                    releaseToken(assignedTokenId)
                }
            }
        }
    }

    fun releaseToken(tokenId: Long) {
        if (tokenId <= INVALID_TOKEN_ID) return
        val now = elapsedRealtimeMs()
        val toRelease = mutableListOf<ManagedLease>()
        synchronized(lock) {
            activeLeases.remove(tokenId)?.let { toRelease += it }
            collectExpiredLocked(now, toRelease)
        }
        toRelease.forEach { it.releasePlatformLockOnce() }
    }

    fun releaseTokens(tokenIds: Collection<Long>) {
        if (tokenIds.isEmpty()) return
        val now = elapsedRealtimeMs()
        val toRelease = mutableListOf<ManagedLease>()
        synchronized(lock) {
            for (tokenId in tokenIds) {
                if (tokenId > INVALID_TOKEN_ID) {
                    activeLeases.remove(tokenId)?.let { toRelease += it }
                }
            }
            collectExpiredLocked(now, toRelease)
        }
        toRelease.forEach { it.releasePlatformLockOnce() }
    }

    internal fun activeLeaseCountForTest(): Int = synchronized(lock) { activeLeases.size }

    private fun collectExpiredLocked(now: Long, target: MutableList<ManagedLease>) {
        val iterator = activeLeases.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.deadlineElapsedMs <= now) {
                iterator.remove()
                target += entry
            }
        }
    }

    private class AndroidPlatformWakeLock(
        powerManager: PowerManager?,
        tag: String,
    ) : PlatformWakeLock {
        private val wakeLock: PowerManager.WakeLock? = runCatching {
            powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)?.apply {
                setReferenceCounted(false)
            }
        }.getOrNull()

        override fun acquire(timeoutMs: Long) {
            wakeLock?.acquire(timeoutMs)
        }

        override fun release() {
            wakeLock?.release()
        }

        override fun isHeld(): Boolean = wakeLock?.isHeld == true
    }

    companion object {
        const val WAKE_LOCK_TAG = "Ayah:PrayerAlarm"
        const val HANDOVER_WAKELOCK_MAX_MILLIS = 15_000L
        const val INVALID_TOKEN_ID = 0L

        private fun createAndroidFactory(appContext: Context): PlatformWakeLockFactory {
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return PlatformWakeLockFactory { tag -> AndroidPlatformWakeLock(powerManager, tag) }
        }
    }
}
