package com.ayah.quran.data.companion

import com.ayah.quran.domain.model.companion.CompanionReminderType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.EnumMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompanionReminderTypeLocks @Inject constructor() {

    private val mutexes = EnumMap<CompanionReminderType, Mutex>(CompanionReminderType::class.java).apply {
        CompanionReminderType.entries.forEach { put(it, Mutex()) }
    }

    suspend fun <T> withTypeLock(
        type: CompanionReminderType,
        block: suspend () -> T,
    ): T = mutexes.getValue(type).withLock { block() }
}
