package com.ayah.quran.ui.settings

import app.cash.turbine.test
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.model.ThemeMode
import com.ayah.quran.domain.repository.UserPreferencesRepository
import com.ayah.quran.domain.usecase.ObserveAppLanguageUseCase
import com.ayah.quran.domain.usecase.ObserveThemeModeUseCase
import com.ayah.quran.domain.usecase.SetAppLanguageUseCase
import com.ayah.quran.domain.usecase.SetThemeModeUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Verifies the ViewModel -> use case -> repository chains for the functional
 * preferences: appearance (Phase 1) and app language (Phase 10).
 *
 * The thin domain use cases are executed for real (they contain the
 * business seam) with only the repository mocked.
 */
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<UserPreferencesRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `themeMode emits repository value after the SYSTEM placeholder`() = runTest(
        testDispatcher.scheduler,
    ) {
        every { repository.themeMode } returns MutableStateFlow(ThemeMode.DARK)

        val viewModel = createViewModel()

        viewModel.themeMode.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem())
            assertEquals(ThemeMode.DARK, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setThemeMode persists the selection through the repository`() = runTest(
        testDispatcher.scheduler,
    ) {
        every { repository.themeMode } returns MutableStateFlow(ThemeMode.SYSTEM)
        coEvery { repository.setThemeMode(ThemeMode.LIGHT) } just runs

        val viewModel = createViewModel()
        viewModel.setThemeMode(ThemeMode.LIGHT)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setThemeMode(ThemeMode.LIGHT) }
    }

    @Test
    fun `languageTag resolves a never-chosen preference to the Arabic default`() = runTest(
        testDispatcher.scheduler,
    ) {
        every { repository.languageTag } returns MutableStateFlow(null)

        val viewModel = createViewModel()

        viewModel.languageTag.test {
            assertEquals(AppLocale.ARABIC, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `languageTag emits the stored tag`() = runTest(
        testDispatcher.scheduler,
    ) {
        every { repository.languageTag } returns MutableStateFlow(AppLocale.ENGLISH)

        val viewModel = createViewModel()

        viewModel.languageTag.test {
            assertEquals(AppLocale.ARABIC, awaitItem()) // StateFlow placeholder
            assertEquals(AppLocale.ENGLISH, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setLanguage persists the tag and signals the activity to recreate`() = runTest(
        testDispatcher.scheduler,
    ) {
        every { repository.languageTag } returns MutableStateFlow(AppLocale.ARABIC)
        coEvery { repository.setLanguageTag(AppLocale.ENGLISH) } just runs

        val viewModel = createViewModel()

        viewModel.languageApplied.test {
            viewModel.setLanguage(AppLocale.ENGLISH)
            advanceUntilIdle()
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.setLanguageTag(AppLocale.ENGLISH) }
    }

    private fun createViewModel() = SettingsViewModel(
        observeThemeMode = ObserveThemeModeUseCase(repository),
        setThemeModeUseCase = SetThemeModeUseCase(repository),
        observeAppLanguage = ObserveAppLanguageUseCase(repository),
        setAppLanguageUseCase = SetAppLanguageUseCase(repository),
    )
}
