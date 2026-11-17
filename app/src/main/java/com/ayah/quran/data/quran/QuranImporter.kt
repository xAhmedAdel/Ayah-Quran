package com.ayah.quran.data.quran

import android.content.Context
import androidx.room.withTransaction
import com.ayah.quran.core.database.AyahDatabase
import com.ayah.quran.core.database.entity.AyahEntity
import com.ayah.quran.core.database.entity.SurahEntity
import com.ayah.quran.di.ApplicationScope
import com.ayah.quran.domain.model.QuranImportState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * First-launch import of the bundled verified dataset into Room.
 *
 * Guarantees:
 * - The dataset is parsed and fully validated BEFORE any database write.
 * - All inserts run in one Room transaction: the database is either empty or
 *   completely imported, never partially.
 * - Idempotent: a fully populated database short-circuits to Completed.
 * - A failed import is surfaced as [QuranImportState.Failed]; success is only
 *   reported after post-insert count verification.
 */
@Singleton
class QuranImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AyahDatabase,
    private val parser: QuranTextParser,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private val importMutex = Mutex()

    private val _importState = MutableStateFlow<QuranImportState>(QuranImportState.NotStarted)
    val importState: StateFlow<QuranImportState> = _importState.asStateFlow()

    /** Convenience for application startup: run the import off the main thread. */
    fun startAsync() {
        appScope.launch { importIfNeeded() }
    }

    suspend fun importIfNeeded() {
        importMutex.withLock {
            val current = _importState.value
            if (current is QuranImportState.Importing || current is QuranImportState.Completed) {
                return
            }

            val surahDao = database.surahDao()
            val ayahDao = database.ayahDao()

            if (surahDao.count() == QuranTextParser.EXPECTED_SURAHS &&
                ayahDao.count() == QuranTextParser.EXPECTED_AYAHS
            ) {
                Log.d(TAG, "database already populated; skipping import")
                _importState.value = QuranImportState.Completed
                return
            }

            _importState.value = QuranImportState.Importing(progress = 0f)
            try {
                val parsed = withContext(Dispatchers.IO) { readAndParse() }
                Log.i(TAG, "dataset parsed: ${parsed.surahs.size} surahs, ${parsed.ayahs.size} ayahs")

                database.withTransaction {
                    surahDao.insertAll(parsed.surahs.map { it.toEntity() })
                    val chunkSize = 1000
                    parsed.ayahs.chunked(chunkSize).forEachIndexed { index, chunk ->
                        ayahDao.insertAll(chunk.map { it.toEntity() })
                        _importState.value = QuranImportState.Importing(
                            progress = (index + 1) * chunkSize / parsed.ayahs.size.toFloat(),
                        )
                    }
                }

                // Only mark success after verifying what actually landed in the DB.
                val surahCount = surahDao.count()
                val ayahCount = ayahDao.count()
                if (surahCount != QuranTextParser.EXPECTED_SURAHS ||
                    ayahCount != QuranTextParser.EXPECTED_AYAHS
                ) {
                    throw QuranParseException(
                        "post-import verification failed: surahs=$surahCount, ayahs=$ayahCount",
                    )
                }
                _importState.value = QuranImportState.Completed
                Log.i(TAG, "import completed: $surahCount surahs, $ayahCount ayahs")
            } catch (t: Throwable) {
                // Room's transaction has rolled back; the DB stays empty and the
                // failure is reported honestly instead of faked as success.
                Log.e(TAG, "Quran import failed", t)
                _importState.value = QuranImportState.Failed(reason = t.message ?: t.javaClass.simpleName)
            }
        }
    }

    private fun readAndParse(): ParsedQuran {
        val ayahText = context.assets.open(QURAN_TEXT_ASSET).bufferedReader(Charsets.UTF_8).readLines()
        val surahMetadata = context.assets.open(SURAH_METADATA_ASSET).bufferedReader(Charsets.UTF_8).readLines()
        val ayahMetrics = context.assets.open(AYAH_METRICS_ASSET).bufferedReader(Charsets.UTF_8).readLines()
        return parser.parse(ayahText, surahMetadata, ayahMetrics)
    }

    private fun ParsedSurah.toEntity() = SurahEntity(
        id = id,
        nameArabic = nameArabic,
        nameTransliterated = nameTransliterated,
        nameEnglish = nameEnglish,
        revelationType = revelationType.lowercase(),
        ayahCount = ayahCount,
    )

    private fun ParsedAyah.toEntity() = AyahEntity(
        id = globalId,
        surahId = surahId,
        ayahNumber = ayahNumber,
        textUthmani = textUthmani,
        juz = juz,
        page = page,
    )

    companion object {
        private const val TAG = "QuranImporter"

        const val QURAN_TEXT_ASSET = "quran/quran-uthmani.txt"
        const val SURAH_METADATA_ASSET = "quran/quran-data.xml"
        const val AYAH_METRICS_ASSET = "quran/ayah-metrics.txt"
    }
}
