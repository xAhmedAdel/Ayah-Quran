package com.ayah.quran.data.prayer

import com.ayah.quran.domain.model.prayer.AsrMethod
import com.ayah.quran.domain.model.prayer.CalculationMethod
import com.ayah.quran.domain.model.prayer.HighLatitudeRulePref
import com.ayah.quran.domain.model.prayer.PrayerAdjustments
import com.ayah.quran.domain.prayer.PrayerTimesCalculator
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Validates the wrapped adhan-java engine against an INDEPENDENT trusted
 * reference service (Aladhan, api.aladhan.com) for 5 locations × 5 dates ×
 * the 5 major calculation methods (fixture: prayer/aladhan-reference.json).
 *
 * Accepted tolerance: ±2 minutes per prayer — both implementations follow the
 * same published sun-position methodology; sub-minute rounding differences
 * are expected.
 */
class PrayerTimesCalculatorReferenceTest {

    private val calculator = AdhanPrayerTimesCalculator()

    private fun fixtures(): JSONArray {
        val candidates = listOf(
            File("src/test/resources/prayer/aladhan-reference.json"),
            File("app/src/test/resources/prayer/aladhan-reference.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("aladhan-reference.json fixture not found")
        return JSONArray(file.readText())
    }

    private fun methodOf(id: Int): CalculationMethod = when (id) {
        1 -> CalculationMethod.KARACHI
        2 -> CalculationMethod.ISNA
        3 -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        4 -> CalculationMethod.UMM_AL_QURA
        5 -> CalculationMethod.EGYPTIAN
        else -> error("unknown fixture method $id")
    }

    private fun parse(time: String): LocalTime {
        val parts = time.split(":")
        return LocalTime.of(parts[0].toInt(), parts[1].toInt())
    }

    private fun minutesDelta(a: LocalTime, b: LocalTime): Long =
        java.time.Duration.between(a, b).abs().toMinutes()

    @Test
    fun `all fixture calculations match the reference within tolerance`() {
        val fixtures = fixtures()
        assertTrue(fixtures.length() >= 25)
        var worst = 0L
        var worstCase = ""

        for (i in 0 until fixtures.length()) {
            val f = fixtures.getJSONObject(i)
            val date = LocalDate.of(
                f.getString("date").split("-")[2].toInt(),
                f.getString("date").split("-")[1].toInt(),
                f.getString("date").split("-")[0].toInt(),
            )
            val zone = ZoneId.of(f.getString("timezone"))
            val times = calculator.calculate(
                date = date,
                latitude = f.getDouble("lat"),
                longitude = f.getDouble("lng"),
                timezoneId = zone,
                method = methodOf(f.getInt("methodId")),
                asrMethod = AsrMethod.STANDARD,
                highLatitudeRule = HighLatitudeRulePref.ANGLE_BASED,
                adjustments = PrayerAdjustments(),
            )

            val checks = listOf(
                Triple("Fajr", parse(f.getString("Fajr")), times.fajr),
                Triple("Sunrise", parse(f.getString("Sunrise")), times.sunrise),
                Triple("Dhuhr", parse(f.getString("Dhuhr")), times.dhuhr),
                Triple("Asr", parse(f.getString("Asr")), times.asr),
                Triple("Maghrib", parse(f.getString("Maghrib")), times.maghrib),
                Triple("Isha", parse(f.getString("Isha")), times.isha),
            )
            for ((name, reference, actual) in checks) {
                val actualTime = LocalTime.of(actual.hour, actual.minute)
                val delta = minutesDelta(reference, actualTime)
                if (delta > worst) {
                    worst = delta
                    worstCase = "${f.getString("city")} ${f.getString("date")} $name"
                }
                assertTrue(
                    "${f.getString("city")} ${f.getString("date")} $name: " +
                        "reference=$reference actual=$actualTime delta=${delta}min",
                    delta <= PrayerTimesCalculator.REFERENCE_TOLERANCE_MINUTES,
                )
            }
        }
        println("worst observed delta: ${worst}min ($worstCase)")
    }

    @Test
    fun `hanafi asr shifts the asr time later`() {
        val zone = ZoneId.of("Africa/Cairo")
        val date = LocalDate.of(2026, 11, 17)
        val standard = calculator.calculate(
            date, 30.0444, 31.2357, zone, CalculationMethod.EGYPTIAN,
            AsrMethod.STANDARD, HighLatitudeRulePref.ANGLE_BASED, PrayerAdjustments(),
        )
        val hanafi = calculator.calculate(
            date, 30.0444, 31.2357, zone, CalculationMethod.EGYPTIAN,
            AsrMethod.HANAFI, HighLatitudeRulePref.ANGLE_BASED, PrayerAdjustments(),
        )
        assertTrue(hanafi.asr.isAfter(standard.asr))
        // The other prayers are unaffected by the madhab (minute precision —
        // adhan-java's raw epoch instants drift by sub-millisecond rounding).
        assertEqualsMinutes(standard.fajr, hanafi.fajr)
        assertEqualsMinutes(standard.maghrib, hanafi.maghrib)
    }

    @Test
    fun `manual adjustments shift exactly the targeted prayer`() {
        val zone = ZoneId.of("Africa/Cairo")
        val date = LocalDate.of(2026, 11, 17)
        val base = calculator.calculate(
            date, 30.0444, 31.2357, zone, CalculationMethod.EGYPTIAN,
            AsrMethod.STANDARD, HighLatitudeRulePref.ANGLE_BASED, PrayerAdjustments(),
        )
        val adjusted = calculator.calculate(
            date, 30.0444, 31.2357, zone, CalculationMethod.EGYPTIAN,
            AsrMethod.STANDARD, HighLatitudeRulePref.ANGLE_BASED,
            PrayerAdjustments(dhuhr = 5, maghrib = -3),
        )
        assertTrue(java.time.Duration.between(base.dhuhr, adjusted.dhuhr).toMinutes() == 5L)
        assertTrue(java.time.Duration.between(base.maghrib, adjusted.maghrib).toMinutes() == -3L)
        assertEqualsMinutes(base.fajr, adjusted.fajr)
        assertEqualsMinutes(base.isha, adjusted.isha)
    }

    private fun assertEqualsMinutes(a: java.time.LocalDateTime, b: java.time.LocalDateTime) {
        org.junit.Assert.assertEquals(
            a.toLocalTime().withSecond(0).withNano(0),
            b.toLocalTime().withSecond(0).withNano(0),
        )
    }

}
