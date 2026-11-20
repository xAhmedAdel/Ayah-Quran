package com.ayah.quran.di

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.ReadScope
import androidx.datastore.core.Storage
import androidx.datastore.core.StorageConnection
import androidx.datastore.core.WriteScope
import androidx.datastore.core.createSingleProcessCoordinator
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.buffer
import okio.sink
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreCorruptionRecoveryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testKey = stringPreferencesKey("theme_mode")
    private val versionKey = intPreferencesKey("companion_content_version")

    private fun nioBackedPreferencesStorage(file: File): Storage<Preferences> = object : Storage<Preferences> {
        override fun createConnection(): StorageConnection<Preferences> {
            val mutex = Mutex()
            val coordinator = createSingleProcessCoordinator(file.canonicalPath)
            return object : StorageConnection<Preferences> {
                override val coordinator = coordinator

                override suspend fun <R> readScope(block: suspend ReadScope<Preferences>.(locked: Boolean) -> R): R {
                    val locked = mutex.tryLock()
                    return try {
                        val scope = object : ReadScope<Preferences> {
                            override suspend fun readData(): Preferences {
                                if (!file.exists()) return emptyPreferences()
                                return file.source().buffer().use { src ->
                                    PreferencesSerializer.readFrom(src)
                                }
                            }
                            override fun close() = Unit
                        }
                        scope.block(locked)
                    } finally {
                        if (locked) mutex.unlock()
                    }
                }

                override suspend fun writeScope(block: suspend WriteScope<Preferences>.() -> Unit) {
                    mutex.withLock {
                        val tmp = File(file.parentFile, "${file.name}.tmp")
                        val scope = object : WriteScope<Preferences> {
                            override suspend fun readData(): Preferences {
                                if (!file.exists()) return emptyPreferences()
                                return file.source().buffer().use { src ->
                                    PreferencesSerializer.readFrom(src)
                                }
                            }

                            override suspend fun writeData(value: Preferences) {
                                tmp.sink().buffer().use { out ->
                                    PreferencesSerializer.writeTo(value, out)
                                }
                                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                            }
                            override fun close() = Unit
                        }
                        scope.block()
                    }
                }

                override fun close() = Unit
            }
        }
    }

    @Test
    fun corruptedPreferencesFileRecoversToSafeEmptyDefaultsAndAllowsSubsequentWrites() = runTest {
        val file = File(tempFolder.root, "corrupt_prefs.preferences_pb")
        file.writeBytes(byteArrayOf(0x00, 0xFF.toByte(), 0x7F, 0x12, 0x34, 0x56, 0x78))

        val storeScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val dataStore = DataStoreFactory.create(
            storage = nioBackedPreferencesStorage(file),
            corruptionHandler = createPreferencesCorruptionHandler(),
            scope = storeScope.backgroundScope,
        )

        val recovered = dataStore.data.first()
        assertNull(recovered[testKey])
        assertNull(recovered[versionKey])

        dataStore.edit { prefs ->
            prefs[testKey] = "DARK"
            prefs[versionKey] = 2
        }

        val persisted = dataStore.data.first()
        assertEquals("DARK", persisted[testKey])
        assertEquals(2, persisted[versionKey])
    }

    @Test
    fun normalPreferencePersistencePreservesUnrelatedKeys() = runTest {
        val file = File(tempFolder.root, "normal_prefs.preferences_pb")
        val storeScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val dataStore = DataStoreFactory.create(
            storage = nioBackedPreferencesStorage(file),
            corruptionHandler = createPreferencesCorruptionHandler(),
            scope = storeScope.backgroundScope,
        )

        dataStore.edit { prefs ->
            prefs[testKey] = "LIGHT"
        }
        dataStore.edit { prefs ->
            prefs[versionKey] = 2
        }

        val current = dataStore.data.first()
        assertEquals("LIGHT", current[testKey])
        assertEquals(2, current[versionKey])
    }

    @Test
    fun corruptionHandlerProducesEmptyPreferencesOnDirectCorruptionException() = runTest {
        val handler = createPreferencesCorruptionHandler()
        val recovered = handler.handleCorruption(CorruptionException("truncated protobuf"))
        assertEquals(0, recovered.asMap().size)
    }

    @Test
    fun ioFailureFromDataStoreFlowCanBeHandledSafely() = runTest {
        val failingStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("disk unavailable") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                throw IOException("disk unavailable")
            }
        }
        var caughtIo = false
        try {
            failingStore.data.first()
        } catch (_: IOException) {
            caughtIo = true
        }
        assertEquals(true, caughtIo)
    }
}
