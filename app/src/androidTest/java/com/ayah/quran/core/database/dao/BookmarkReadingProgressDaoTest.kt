package com.ayah.quran.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayah.quran.core.database.AyahDatabase
import com.ayah.quran.core.database.entity.BookmarkEntity
import com.ayah.quran.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO behavior tests: bookmark uniqueness/toggle semantics and the
 * single-row reading-progress upsert. These exercise referential integrity
 * with real verses imported from the bundled assets.
 */
@RunWith(AndroidJUnit4::class)
class BookmarkReadingProgressDaoTest {

    private lateinit var database: AyahDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AyahDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Import through the real importer so foreign keys have real targets.
        runBlocking {
            com.ayah.quran.data.quran.QuranImporter(
                context = context,
                database = database,
                parser = com.ayah.quran.data.quran.QuranTextParser(),
                appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO),
            ).importIfNeeded()
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun bookmark(surahId: Int, ayahNumber: Int) = BookmarkEntity(
        surahId = surahId,
        ayahNumber = ayahNumber,
        createdAt = System.currentTimeMillis(),
    )

    @Test
    fun bookmarkingSameVerseTwiceIsANoOp() = runBlocking {
        val dao = database.bookmarkDao()
        val firstInsert = dao.insert(bookmark(2, 255))
        assertTrue(firstInsert != -1L)
        val secondInsert = dao.insert(bookmark(2, 255))
        assertEquals(-1L, secondInsert)
        assertEquals(1, dao.count())
    }

    @Test
    fun bookmarkDeleteRemovesRow() = runBlocking {
        val dao = database.bookmarkDao()
        dao.insert(bookmark(1, 1))
        assertTrue(dao.observeIsBookmarked(1, 1).first())
        dao.delete(1, 1)
        assertFalse(dao.observeIsBookmarked(1, 1).first())
    }

    @Test
    fun bookmarkListJoinsVerseTextAndSurahNames() = runBlocking {
        val dao = database.bookmarkDao()
        dao.insert(bookmark(2, 255))
        val rows = dao.observeBookmarkedAyahs().first()
        assertEquals(1, rows.size)
        assertEquals("Al-Baqara", rows[0].surahNameTransliterated)
        assertTrue(rows[0].textUthmani.isNotEmpty())
    }

    @Test
    fun readingProgressUpsertKeepsSingleRow() = runBlocking {
        val dao = database.readingProgressDao()
        dao.upsert(ReadingProgressEntity(surahId = 1, ayahNumber = 3, updatedAt = 1L))
        dao.upsert(ReadingProgressEntity(surahId = 2, ayahNumber = 255, updatedAt = 2L))

        val rows = database.openHelper.readableDatabase
            .query("SELECT * FROM reading_progress").use { it.count }
        assertEquals("single-row table must stay at one row", 1, rows)

        val observed = dao.observe().first()
        assertEquals(2, observed?.surahId)
        assertEquals(255, observed?.ayahNumber)
    }
}
