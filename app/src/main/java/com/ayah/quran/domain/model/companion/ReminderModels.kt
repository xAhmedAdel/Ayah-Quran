package com.ayah.quran.domain.model.companion

import java.time.Duration
import java.time.ZonedDateTime

enum class CompanionReminderType(
    val key: String,
    val uniqueWorkName: String,
    val notificationId: Int,
    val defaultHour: Int,
    val defaultMinute: Int,
    val deepLinkRoute: String,
) {
    MORNING_ADHKAR(
        key = "morning_adhkar",
        uniqueWorkName = "companion_reminder_morning_adhkar",
        notificationId = 4101,
        defaultHour = 7,
        defaultMinute = 0,
        deepLinkRoute = "companion/adhkar/session/morning",
    ),
    EVENING_ADHKAR(
        key = "evening_adhkar",
        uniqueWorkName = "companion_reminder_evening_adhkar",
        notificationId = 4102,
        defaultHour = 17,
        defaultMinute = 30,
        deepLinkRoute = "companion/adhkar/session/evening",
    ),
    DAILY_REFLECTION(
        key = "daily_reflection",
        uniqueWorkName = "companion_reminder_daily_reflection",
        notificationId = 4103,
        defaultHour = 9,
        defaultMinute = 0,
        deepLinkRoute = "home",
    );

    companion object {
        fun fromKey(key: String?): CompanionReminderType? =
            entries.firstOrNull { it.key == key }
    }
}

data class CompanionReminderConfig(
    val type: CompanionReminderType,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
) {
    fun formattedTime(): String = "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
}

data class CompanionRemindersPreferences(
    val morningAdhkar: CompanionReminderConfig = CompanionReminderConfig(
        type = CompanionReminderType.MORNING_ADHKAR,
        enabled = false,
        hour = CompanionReminderType.MORNING_ADHKAR.defaultHour,
        minute = CompanionReminderType.MORNING_ADHKAR.defaultMinute,
    ),
    val eveningAdhkar: CompanionReminderConfig = CompanionReminderConfig(
        type = CompanionReminderType.EVENING_ADHKAR,
        enabled = false,
        hour = CompanionReminderType.EVENING_ADHKAR.defaultHour,
        minute = CompanionReminderType.EVENING_ADHKAR.defaultMinute,
    ),
    val dailyReflection: CompanionReminderConfig = CompanionReminderConfig(
        type = CompanionReminderType.DAILY_REFLECTION,
        enabled = false,
        hour = CompanionReminderType.DAILY_REFLECTION.defaultHour,
        minute = CompanionReminderType.DAILY_REFLECTION.defaultMinute,
    ),
    val permissionRequestedBefore: Boolean = false,
) {
    fun configOf(type: CompanionReminderType): CompanionReminderConfig = when (type) {
        CompanionReminderType.MORNING_ADHKAR -> morningAdhkar
        CompanionReminderType.EVENING_ADHKAR -> eveningAdhkar
        CompanionReminderType.DAILY_REFLECTION -> dailyReflection
    }

    fun allConfigs(): List<CompanionReminderConfig> = listOf(
        morningAdhkar,
        eveningAdhkar,
        dailyReflection,
    )
}

object CompanionReminderTimeCalculator {

    private const val MIN_DELAY_MILLIS = 60_000L

    fun computeInitialDelayMillis(
        now: ZonedDateTime,
        hour: Int,
        minute: Int,
    ): Long {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        var target = now.withHour(safeHour).withMinute(safeMinute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) {
            target = now.plusDays(1).withHour(safeHour).withMinute(safeMinute).withSecond(0).withNano(0)
        }
        return Duration.between(now, target).toMillis().coerceAtLeast(MIN_DELAY_MILLIS)
    }
}
