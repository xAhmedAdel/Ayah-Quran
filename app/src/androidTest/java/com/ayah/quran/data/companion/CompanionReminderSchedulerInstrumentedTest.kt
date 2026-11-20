package com.ayah.quran.data.companion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ayah.quran.background.CompanionReminderNotifier
import com.ayah.quran.domain.model.companion.CompanionReminderConfig
import com.ayah.quran.domain.model.companion.CompanionReminderType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompanionReminderSchedulerInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val workManager = WorkManager.getInstance(context)
    private val scheduler = WorkManagerCompanionReminderScheduler(context)

    @After
    fun tearDown() {
        CompanionReminderType.entries.forEach { scheduler.cancel(it) }
    }

    @Test
    fun workManagerSchedulesReplacesWithoutDuplicatesAndCancelsIndependently() {
        val morning = CompanionReminderConfig(CompanionReminderType.MORNING_ADHKAR, true, 7, 0)
        val evening = CompanionReminderConfig(CompanionReminderType.EVENING_ADHKAR, true, 17, 30)

        scheduler.schedule(morning)
        scheduler.schedule(evening)

        val morningBefore = workManager.getWorkInfosForUniqueWork(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).get()
        assertEquals(1, morningBefore.count { it.state == WorkInfo.State.ENQUEUED })
        val firstId = morningBefore.first { it.state == WorkInfo.State.ENQUEUED }.id

        scheduler.schedule(morning.copy(hour = 6, minute = 45))
        val morningAfterUpdate = workManager.getWorkInfosForUniqueWork(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).get()
        assertEquals(1, morningAfterUpdate.count { it.state == WorkInfo.State.ENQUEUED })
        val secondId = morningAfterUpdate.first { it.state == WorkInfo.State.ENQUEUED }.id
        assertTrue(firstId != secondId)

        scheduler.cancel(CompanionReminderType.MORNING_ADHKAR)
        val morningCancelled = workManager.getWorkInfosForUniqueWork(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).get()
        assertEquals(0, morningCancelled.count { it.state == WorkInfo.State.ENQUEUED })

        val eveningStillActive = workManager.getWorkInfosForUniqueWork(CompanionReminderType.EVENING_ADHKAR.uniqueWorkName).get()
        assertEquals(1, eveningStillActive.count { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun launchIntentsCarryExactCompanionDeepLinkRoutes() {
        CompanionReminderType.entries.forEach { type ->
            val intent = CompanionReminderNotifier.buildLaunchIntent(context, type)
            assertEquals(
                type.deepLinkRoute,
                intent.getStringExtra(CompanionReminderNotifier.EXTRA_COMPANION_ROUTE),
            )
        }
    }
}
