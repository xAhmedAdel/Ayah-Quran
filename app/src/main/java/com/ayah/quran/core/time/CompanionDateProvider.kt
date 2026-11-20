package com.ayah.quran.core.time

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface CompanionDateProvider {
    fun todayKey(): String
    fun currentHour(): Int
    fun todayEpochDay(): Long = LocalDate.parse(todayKey()).toEpochDay()
}

@Singleton
class SystemCompanionDateProvider @Inject constructor() : CompanionDateProvider {

    override fun todayKey(): String = LocalDate.now(ZoneId.systemDefault()).toString()

    override fun currentHour(): Int = LocalTime.now(ZoneId.systemDefault()).hour

    override fun todayEpochDay(): Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
}
