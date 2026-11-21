package com.ayah.quran.data.quran

import android.content.Context
import com.ayah.quran.di.IoDispatcher
import com.ayah.quran.domain.model.RubElHizb
import com.ayah.quran.domain.repository.RubElHizbCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetRubElHizbCatalog @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val parser: RubElHizbParser,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RubElHizbCatalog {

    private val mutex = Mutex()

    @Volatile
    private var cached: List<RubElHizb>? = null

    override suspend fun getAll(): List<RubElHizb> {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return it }
            val parsed = withContext(ioDispatcher) {
                context.assets.open(RUB_ASSET).bufferedReader(Charsets.UTF_8).use { reader ->
                    parser.parse(reader.readLines())
                }
            }
            cached = parsed
            parsed
        }
    }

    companion object {
        const val RUB_ASSET = "quran/rub-el-hizb.txt"
    }
}
