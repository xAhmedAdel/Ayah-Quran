package com.ayah.quran.ui.home

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.core.quran.AyahSelector
import com.ayah.quran.core.wallpaper.WallpaperTemplate
import com.ayah.quran.domain.model.QuranImportState
import com.ayah.quran.domain.model.ReadingPosition
import com.ayah.quran.domain.model.TextVerticalPosition
import com.ayah.quran.domain.model.WallpaperApplyStatus
import com.ayah.quran.domain.model.WallpaperHistoryEntry
import com.ayah.quran.domain.model.WallpaperScheduleStatus
import com.ayah.quran.domain.model.prayer.AdhanMode
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.repository.WallpaperRepository
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import com.ayah.quran.domain.usecase.GetSurahUseCase
import com.ayah.quran.domain.usecase.ObserveBookmarkedAyahsUseCase
import com.ayah.quran.domain.usecase.ObserveQuranImportStateUseCase
import com.ayah.quran.domain.usecase.ObserveReadingPositionUseCase
import com.ayah.quran.domain.usecase.ObserveWallpaperHistoryUseCase
import com.ayah.quran.domain.usecase.ObserveWallpaperScheduleStatusUseCase
import com.ayah.quran.domain.usecase.PageForAyahUseCase
import com.ayah.quran.prayer.PrayerTimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale
import javax.inject.Inject

data class ContinueReadingState(
    val surahId: Int,
    val surahNameAr: String,
    val surahNameTransliterated: String,
    val ayahNumber: Int,
    val surahAyahCount: Int,
    val pageNumber: Int?,
    val progressFraction: Float,
)

data class WallpaperCardState(
    val thumbnail: Bitmap?,
    val surahNameAr: String,
    val surahNameTransliterated: String,
    val surahId: Int,
    val ayahNumber: Int,
    val templateId: String,
    val appliedAtMillis: Long,
)

data class HomeDailyAyahState(
    val surahId: Int,
    val ayahNumber: Int,
    val textUthmani: String,
    val surahNameAr: String,
    val surahNameTransliterated: String,
)

data class HomeNextPrayerState(
    val nextPrayer: Prayer,
    val countdownText: String,
    val nextTimeText: String,
    val cityAr: String,
    val cityEn: String,
    val adhanEnabled: Boolean,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val hijriDateText: String = "",
    val continueReading: ContinueReadingState? = null,
    val nextPrayer: HomeNextPrayerState? = null,
    val dailyAyah: HomeDailyAyahState? = null,
    val wallpaperCard: WallpaperCardState? = null,
    val scheduleStatus: WallpaperScheduleStatus =
        WallpaperScheduleStatus(isScheduled = false, intervalMinutes = null),
    val bookmarkCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    observeReadingPosition: ObserveReadingPositionUseCase,
    observeWallpaperHistory: ObserveWallpaperHistoryUseCase,
    observeScheduleStatus: ObserveWallpaperScheduleStatusUseCase,
    observeBookmarkedAyahs: ObserveBookmarkedAyahsUseCase,
    observeQuranImportState: ObserveQuranImportStateUseCase,
    prayerPreferencesRepository: PrayerPreferencesRepository,
    private val prayerManager: PrayerTimeManager,
    private val ayahSelector: AyahSelector,
    private val pageForAyah: PageForAyahUseCase,
    private val getSurah: GetSurahUseCase,
    private val wallpaperRepository: WallpaperRepository,
) : ViewModel() {

    private var cachedThumbnailKey: String? = null
    private var cachedThumbnail: Bitmap? = null

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    private val readingFlow = observeReadingPosition()
        .distinctUntilChanged()
        .flatMapLatest { position ->
            flow { emit(position?.let { buildContinueReading(it) }) }
        }

    private val wallpaperFlow = observeWallpaperHistory()
        .distinctUntilChanged()
        .flatMapLatest { history ->
            flow { emit(buildWallpaperCard(history)) }
        }

    private val dailyAyahFlow = observeQuranImportState()
        .map { it is QuranImportState.Completed }
        .distinctUntilChanged()
        .flatMapLatest {
            flow {
                val pool = wallpaperRepository.allGlobalIds()
                if (pool.isEmpty()) {
                    emit(null)
                    return@flow
                }
                val globalId = ayahSelector.selectDaily(
                    epochDay = LocalDate.now().toEpochDay(),
                    poolSize = pool.size,
                )
                val ayah = wallpaperRepository.loadAyahByGlobalId(globalId)
                if (ayah == null) {
                    emit(null)
                    return@flow
                }
                val surah = getSurah(ayah.surahId)
                emit(
                    HomeDailyAyahState(
                        surahId = ayah.surahId,
                        ayahNumber = ayah.ayahNumber,
                        textUthmani = ayah.textUthmani,
                        surahNameAr = surah?.nameArabic ?: "",
                        surahNameTransliterated = surah?.nameTransliterated ?: "Surah ${ayah.surahId}",
                    ),
                )
            }
        }

    private val prayerSummaryFlow = combine(
        prayerPreferencesRepository.preferences,
        prayerManager.todayTimes,
        prayerManager.tomorrowFajr,
        ticker,
    ) { prefs, today, tomorrowFajr, _ ->
        val location = prefs.location
        val zone = location?.let { ZoneId.of(it.timezoneId) } ?: ZoneId.systemDefault()
        val hijri = formatHijriDate(
            LocalDate.now(zone).plusDays(prefs.hijriAdjustmentDays.toLong()),
        )
        val next = if (location != null && today != null && tomorrowFajr != null) {
            val countdown = prayerManager.countdown(today, tomorrowFajr, zone)
            val total = countdown.durationMillis.coerceAtLeast(0L)
            val h = total / 3_600_000L
            val m = (total / 60_000L) % 60L
            val s = (total / 1_000L) % 60L
            val adhanEnabled = Prayer.entries.any {
                prefs.adhanConfig.modeOf(it) != AdhanMode.DISABLED
            }
            HomeNextPrayerState(
                nextPrayer = countdown.next,
                countdownText = "%02d:%02d:%02d".format(h, m, s),
                nextTimeText = "%02d:%02d".format(countdown.nextTime.hour, countdown.nextTime.minute),
                cityAr = location.cityAr,
                cityEn = location.cityEn,
                adhanEnabled = adhanEnabled,
            )
        } else {
            null
        }
        hijri to next
    }

    private val bookmarkCountFlow = observeBookmarkedAyahs().map { it.size }.distinctUntilChanged()

    private val coreReadingAndWallpaperFlow = combine(
        readingFlow,
        wallpaperFlow,
        observeScheduleStatus(),
        dailyAyahFlow,
    ) { reading, wallpaper, schedule, daily ->
        CoreHomeData(reading, wallpaper, schedule, daily)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        coreReadingAndWallpaperFlow,
        prayerSummaryFlow,
        bookmarkCountFlow,
    ) { core, (hijri, nextPrayer), bookmarkCount ->
        HomeUiState(
            isLoading = false,
            hijriDateText = hijri,
            continueReading = core.reading,
            nextPrayer = nextPrayer,
            dailyAyah = core.dailyAyah,
            wallpaperCard = core.wallpaperCard,
            scheduleStatus = core.scheduleStatus,
            bookmarkCount = bookmarkCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true),
    )

    private suspend fun buildContinueReading(position: ReadingPosition): ContinueReadingState? {
        val surah = getSurah(position.surahId) ?: return null
        val page = pageForAyah(position.surahId, position.ayahNumber)
        return ContinueReadingState(
            surahId = surah.id,
            surahNameAr = surah.nameArabic,
            surahNameTransliterated = surah.nameTransliterated,
            ayahNumber = position.ayahNumber,
            surahAyahCount = surah.ayahCount,
            pageNumber = page,
            progressFraction = if (surah.ayahCount > 0) {
                (position.ayahNumber / surah.ayahCount.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            },
        )
    }

    private suspend fun buildWallpaperCard(history: List<WallpaperHistoryEntry>): WallpaperCardState? {
        val latest = history.firstOrNull { it.status == WallpaperApplyStatus.SUCCESS } ?: return null
        val surah = getSurah(latest.surahId)
        val template = WallpaperTemplate.byId(latest.templateId)
        val key = "${latest.id}:${latest.templateId}:${latest.surahId}:${latest.ayahNumber}"
        val thumbnail = if (cachedThumbnailKey == key && cachedThumbnail != null) {
            cachedThumbnail
        } else {
            val ayah = wallpaperRepository.loadAyah(latest.surahId, latest.ayahNumber)
            ayah?.let {
                val surahLabel = if (AppLocale.isArabic) {
                    surah?.nameArabic ?: it.surahId.toString()
                } else {
                    surah?.nameTransliterated ?: it.surahId.toString()
                }
                wallpaperRepository.renderPreview(
                    ayahText = it.textUthmani,
                    referenceLabel = "$surahLabel · ${it.surahId}:${it.ayahNumber}",
                    template = template,
                    widthPx = THUMBNAIL_WIDTH_PX,
                    heightPx = THUMBNAIL_HEIGHT_PX,
                    fontScale = 1.0f,
                    verticalPosition = TextVerticalPosition.CENTER,
                ).also { bmp ->
                    cachedThumbnailKey = key
                    cachedThumbnail = bmp
                }
            }
        }
        return WallpaperCardState(
            thumbnail = thumbnail,
            surahNameAr = surah?.nameArabic ?: "",
            surahNameTransliterated = surah?.nameTransliterated ?: "Surah ${latest.surahId}",
            surahId = latest.surahId,
            ayahNumber = latest.ayahNumber,
            templateId = template.id,
            appliedAtMillis = latest.appliedAtMillis,
        )
    }

    private fun formatHijriDate(localDate: LocalDate): String {
        val hijri = HijrahDate.from(localDate)
        val locale = Locale.forLanguageTag(if (AppLocale.isArabic) AppLocale.ARABIC else AppLocale.ENGLISH)
        val formatter = DateTimeFormatterBuilder()
            .appendValue(ChronoField.DAY_OF_MONTH)
            .appendLiteral(" ")
            .appendPattern("MMMM")
            .appendLiteral(" ")
            .appendValue(ChronoField.YEAR_OF_ERA)
            .toFormatter(locale)
            .withChronology(HijrahChronology.INSTANCE)
        val suffix = if (AppLocale.isArabic) " هـ" else " AH"
        return formatter.format(hijri) + suffix
    }

    private data class CoreHomeData(
        val reading: ContinueReadingState?,
        val wallpaperCard: WallpaperCardState?,
        val scheduleStatus: WallpaperScheduleStatus,
        val dailyAyah: HomeDailyAyahState?,
    )

    private companion object {
        const val THUMBNAIL_WIDTH_PX = 360
        const val THUMBNAIL_HEIGHT_PX = 760
    }
}
