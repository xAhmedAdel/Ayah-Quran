package com.ayah.quran.data.companion

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.room.withTransaction
import com.ayah.quran.core.database.AyahDatabase
import com.ayah.quran.core.database.entity.companion.TasbihCounterEntity
import com.ayah.quran.di.ApplicationScope
import com.ayah.quran.domain.model.companion.CompanionImportState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompanionContentImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AyahDatabase,
    private val parser: CompanionContentParser,
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private val importMutex = Mutex()

    private val _importState = MutableStateFlow<CompanionImportState>(CompanionImportState.NotStarted)
    val importState: StateFlow<CompanionImportState> = _importState.asStateFlow()

    fun startAsync() {
        appScope.launch { importIfNeeded() }
    }

    suspend fun importIfNeeded() {
        importMutex.withLock {
            val current = _importState.value
            if (current is CompanionImportState.Importing || current is CompanionImportState.Completed) {
                return
            }

            val adhkarDao = database.adhkarDao()
            val tasbihDao = database.tasbihDao()
            val hadithDao = database.hadithDao()

            val storedVersion = dataStore.data.first()[KEY_COMPANION_CONTENT_VERSION] ?: 0
            if (storedVersion >= CURRENT_CONTENT_VERSION &&
                adhkarDao.itemCount() > 0 &&
                adhkarDao.quranicDuaCount() > 0 &&
                hadithDao.hadithCount() > 0 &&
                tasbihDao.counterCount() > 0
            ) {
                _importState.value = CompanionImportState.Completed
                return
            }

            _importState.value = CompanionImportState.Importing
            try {
                val (adhkar, quranicDuas, hadith) = withContext(Dispatchers.IO) {
                    val adhkarRaw = readAsset(ADHKAR_ASSET)
                    val duasRaw = readAsset(QURANIC_DUAS_ASSET)
                    val hadithRaw = readAsset(HADITH_ASSET)
                    Triple(
                        parser.parseAdhkar(adhkarRaw),
                        parser.parseQuranicDuas(duasRaw),
                        parser.parseHadith(hadithRaw),
                    )
                }

                val nowMillis = System.currentTimeMillis()
                val minKeepDay = LocalDate.now().minusDays(30).toString()

                database.withTransaction {
                    adhkarDao.upsertCategories(adhkar.categories)
                    adhkarDao.upsertItems(adhkar.items)
                    adhkarDao.upsertQuranicDuas(quranicDuas.items)
                    adhkarDao.pruneOldProgress(minKeepDay)

                    hadithDao.upsertCollections(hadith.collections)
                    hadithDao.upsertHadiths(hadith.items)

                    tasbihDao.insertSeedCounters(defaultTasbihCounters(nowMillis))
                }

                if (adhkarDao.itemCount() < adhkar.items.size ||
                    adhkarDao.quranicDuaCount() < quranicDuas.items.size ||
                    hadithDao.hadithCount() < hadith.items.size ||
                    tasbihDao.counterCount() < DEFAULT_TASBIH_SEED_COUNT
                ) {
                    throw CompanionParseException("Post-import verification failed for companion datasets")
                }

                dataStore.edit { prefs ->
                    prefs[KEY_COMPANION_CONTENT_VERSION] = CURRENT_CONTENT_VERSION
                }
                _importState.value = CompanionImportState.Completed
                Log.i(
                    TAG,
                    "companion datasets imported: adhkar=${adhkar.items.size}, " +
                        "quranicDuas=${quranicDuas.items.size}, hadiths=${hadith.items.size}",
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Companion dataset import failed", t)
                _importState.value = CompanionImportState.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun defaultTasbihCounters(nowMillis: Long): List<TasbihCounterEntity> = listOf(
        TasbihCounterEntity(
            stableKey = "subhanallah",
            titleAr = "سُبْحَانَ اللَّهِ",
            titleEn = "SubhanAllah",
            dhikrTextAr = "سُبْحَانَ اللَّهِ",
            targetCount = 33,
            currentCount = 0,
            completedRounds = 0,
            lifetimeCount = 0,
            isCustom = false,
            sortOrder = 1,
            updatedAtMillis = nowMillis,
        ),
        TasbihCounterEntity(
            stableKey = "alhamdulillah",
            titleAr = "الْحَمْدُ لِلَّهِ",
            titleEn = "Alhamdulillah",
            dhikrTextAr = "الْحَمْدُ لِلَّهِ",
            targetCount = 33,
            currentCount = 0,
            completedRounds = 0,
            lifetimeCount = 0,
            isCustom = false,
            sortOrder = 2,
            updatedAtMillis = nowMillis,
        ),
        TasbihCounterEntity(
            stableKey = "allahuakbar",
            titleAr = "اللَّهُ أَكْبَرُ",
            titleEn = "Allahu Akbar",
            dhikrTextAr = "اللَّهُ أَكْبَرُ",
            targetCount = 33,
            currentCount = 0,
            completedRounds = 0,
            lifetimeCount = 0,
            isCustom = false,
            sortOrder = 3,
            updatedAtMillis = nowMillis,
        ),
        TasbihCounterEntity(
            stableKey = "astaghfirullah",
            titleAr = "أَسْتَغْفِرُ اللَّهَ",
            titleEn = "Astaghfirullah",
            dhikrTextAr = "أَسْتَغْفِرُ اللَّهَ",
            targetCount = 100,
            currentCount = 0,
            completedRounds = 0,
            lifetimeCount = 0,
            isCustom = false,
            sortOrder = 4,
            updatedAtMillis = nowMillis,
        ),
        TasbihCounterEntity(
            stableKey = "salawat",
            titleAr = "الصلاة على النبي ﷺ",
            titleEn = "Salawat upon the Prophet",
            dhikrTextAr = "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ",
            targetCount = 10,
            currentCount = 0,
            completedRounds = 0,
            lifetimeCount = 0,
            isCustom = false,
            sortOrder = 5,
            updatedAtMillis = nowMillis,
        ),
        TasbihCounterEntity(
            stableKey = "subhanallah_wabihamdihi",
            titleAr = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
            titleEn = "SubhanAllahi wa bihamdihi",
            dhikrTextAr = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
            targetCount = 100,
            currentCount = 0,
            completedRounds = 0,
            lifetimeCount = 0,
            isCustom = false,
            sortOrder = 6,
            updatedAtMillis = nowMillis,
        ),
    )

    companion object {
        private const val TAG = "CompanionImporter"
        const val CURRENT_CONTENT_VERSION = 1
        private const val DEFAULT_TASBIH_SEED_COUNT = 6
        val KEY_COMPANION_CONTENT_VERSION = intPreferencesKey("companion_content_version")
        const val ADHKAR_ASSET = "companion/adhkar_hisn.json"
        const val QURANIC_DUAS_ASSET = "companion/quranic_duas.json"
        const val HADITH_ASSET = "companion/hadith_starter.json"
    }
}
