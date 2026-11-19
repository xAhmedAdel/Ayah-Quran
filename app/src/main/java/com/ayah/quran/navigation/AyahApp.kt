package com.ayah.quran.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ayah.quran.core.designsystem.motion.MotionDurations
import com.ayah.quran.core.designsystem.motion.rememberMotionEnabled
import com.ayah.quran.domain.model.ReadingMode
import com.ayah.quran.ui.audio.AudioScreen
import com.ayah.quran.ui.bookmarks.BookmarksScreen
import com.ayah.quran.ui.home.HomeScreen
import com.ayah.quran.ui.prayer.PrayerTimesScreen
import com.ayah.quran.ui.quran.QuranScreen
import com.ayah.quran.ui.reader.MushafPageReaderScreen
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.ui.prayer.AdhanLibraryScreen
import com.ayah.quran.ui.prayer.AdhanVoiceScreen
import com.ayah.quran.ui.prayer.PrayerOnboardingScreen
import com.ayah.quran.ui.prayer.PrayerSettingsScreen
import com.ayah.quran.ui.reader.ReaderScreen
import com.ayah.quran.ui.settings.SettingsScreen
import com.ayah.quran.ui.main.MainViewModel
import com.ayah.quran.ui.wallpapers.FullscreenPreviewScreen
import com.ayah.quran.ui.wallpapers.WallpapersScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

object Routes {
    const val READER = "reader/{surahId}?ayah={ayah}"
    const val MUSHAF = "mushaf?surahId={surahId}&page={page}&ayah={ayah}"
    const val BOOKMARKS = "bookmarks"
    const val PRAYER_SETTINGS = "prayer/settings"
    const val PRAYER_VOICE = "prayer/adhan-voice"
    const val PRAYER_ONBOARDING = "prayer/onboarding"
    const val PRAYER_ADHAN_LIBRARY = "prayer/adhan-library/{prayer}"
    const val NOW_PLAYING = "audio/now-playing"
    const val FULLSCREEN_PREVIEW = "preview/{surahId}/{ayahNumber}"

    fun reader(surahId: Int, ayahNumber: Int? = null): String =
        "reader/$surahId" + (ayahNumber?.let { "?ayah=$it" } ?: "")

    /**
     * The authentic 604-page mushaf reader. Pass either [surahId] (+ optional
     * [ayahNumber]) or an explicit [page]; the reader resolves the rest.
     */
    fun mushaf(surahId: Int = 0, page: Int = 0, ayahNumber: Int = 0): String =
        "mushaf?surahId=$surahId&page=$page&ayah=$ayahNumber"

    fun fullscreenPreview(surahId: Int, ayahNumber: Int): String =
        "preview/$surahId/$ayahNumber"
}

/**
 * Single-activity app scaffold. The bottom bar is shown ONLY on the five
 * top-level destinations; reader, bookmarks and the immersive preview are
 * full-height stack destinations.
 *
 * Navigation transitions fade/slide and collapse to no-motion when the
 * system animation scale is 0 (reduced-motion accessibility setting).
 */
@Composable
fun AyahApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }
    val motionEnabled = rememberMotionEnabled()
    val mainViewModel: MainViewModel = hiltViewModel()
    val readingMode by mainViewModel.readingMode.collectAsStateWithLifecycle()
    // Phase 14: onboarding entry — a prominent Home card (visible until the
    // onboarding is finished or skipped) plus the Settings dashboard. A
    // hard auto-redirect on launch was deliberately avoided: it is brittle
    // under instrumentation and violates "skip and continue using the app".
    val permissionsViewModel: com.ayah.quran.ui.prayer.PrayerPermissionsViewModel = hiltViewModel()
    val onboardingCompleted by permissionsViewModel.onboardingCompleted.collectAsStateWithLifecycle()

    /** Reader route honoring the user's reading mode. */
    fun openReadingPosition(surahId: Int, ayahNumber: Int?) {
        if (readingMode == ReadingMode.MUSHAF) {
            navController.navigate(Routes.mushaf(surahId = surahId, ayahNumber = ayahNumber ?: 0))
        } else {
            navController.navigate(Routes.reader(surahId, ayahNumber))
        }
    }

    /** Collapses a duration to zero when the system disables animations. */
    fun dur(millis: Int): Int = if (motionEnabled) millis else 0

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.unselectedIcon
                                    },
                                    contentDescription = stringResource(destination.labelRes),
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        // Phase 17 slice 2: persistent mini player above the destination —
        // hidden on the Audio tab (its Now Playing surface owns controls).
        Column(modifier = Modifier.padding(innerPadding)) {
        if (currentRoute != TopLevelDestination.AUDIO.route) {
            val miniViewModel: com.ayah.quran.ui.audio.MiniPlayerViewModel =
                hiltViewModel()
            val miniState by miniViewModel.uiState.collectAsStateWithLifecycle()
            com.ayah.quran.ui.audio.MiniPlayerBar(
                state = miniState,
                onOpen = { navController.navigate(Routes.NOW_PLAYING) },
                onPauseResume = {
                    when {
                        // Restored snapshot: "play" resumes that surah.
                        miniState.playback.restorable != null &&
                            miniState.playback.surah == 0 ->
                            miniViewModel.resumeLast()
                        miniState.playback.isPlaying -> miniViewModel.pause()
                        else -> miniViewModel.resume()
                    }
                },
            )
        }
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier,
            enterTransition = {
                fadeIn(tween(dur(MotionDurations.MEDIUM))) +
                    slideInVertically(tween(dur(MotionDurations.MEDIUM))) { it / 12 }
            },
            exitTransition = { fadeOut(tween(dur(MotionDurations.FAST))) },
            popEnterTransition = { fadeIn(tween(dur(MotionDurations.FAST))) },
            popExitTransition = {
                fadeOut(tween(dur(MotionDurations.FAST))) +
                    slideOutVertically(tween(dur(MotionDurations.MEDIUM))) { it / 12 }
            },
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(
                    onOpenQuran = { navController.navigateToTopLevel(TopLevelDestination.QURAN.route) },
                    onContinueReading = { surahId, ayahNumber ->
                        openReadingPosition(surahId, ayahNumber)
                    },
                    onOpenBookmarks = { navController.navigate(Routes.BOOKMARKS) },
                    onOpenWallpapers = { navController.navigateToTopLevel(TopLevelDestination.WALLPAPERS.route) },
                    showPrayerOnboardingCard = !onboardingCompleted,
                    onOpenPrayerOnboarding = { navController.navigate(Routes.PRAYER_ONBOARDING) },
                )
            }
            composable(TopLevelDestination.QURAN.route) {
                QuranScreen(
                    onOpenSurah = { surahId -> openReadingPosition(surahId, null) },
                )
            }
            composable(TopLevelDestination.PRAYER.route) {
                PrayerTimesScreen(
                    onOpenSettings = { navController.navigate(Routes.PRAYER_SETTINGS) },
                )
            }
            composable(TopLevelDestination.WALLPAPERS.route) {
                WallpapersScreen(
                    onOpenFullscreenPreview = { surahId, ayahNumber ->
                        navController.navigate(Routes.fullscreenPreview(surahId, ayahNumber))
                    },
                )
            }
            composable(TopLevelDestination.AUDIO.route) {
                AudioScreen()
            }
            composable(Routes.NOW_PLAYING) {
                com.ayah.quran.ui.audio.NowPlayingScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen()
            }

            composable(
                route = Routes.READER,
                arguments = listOf(
                    navArgument("surahId") { type = NavType.IntType },
                    navArgument("ayah") {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                ),
            ) {
                ReaderScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateSurah = { surahId ->
                        // Replace the current reader entry so back returns to
                        // the list, not every previously visited surah.
                        val currentId = navController.currentBackStackEntry?.destination?.id
                        navController.navigate(Routes.reader(surahId)) {
                            if (currentId != null) {
                                popUpTo(currentId) { inclusive = true }
                            }
                            launchSingleTop = true
                        }
                    },
                    onSwitchToMushaf = { surahId, ayah ->
                        // Replace the current reader entry so back returns to
                        // wherever we came from, not the other reader mode.
                        val currentId = navController.currentBackStackEntry?.destination?.id
                        navController.navigate(Routes.mushaf(surahId = surahId, ayahNumber = ayah)) {
                            if (currentId != null) {
                                popUpTo(currentId) { inclusive = true }
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                route = Routes.MUSHAF,
                arguments = listOf(
                    navArgument("surahId") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("page") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("ayah") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                ),
            ) {
                MushafPageReaderScreen(
                    onBack = { navController.popBackStack() },
                    onSwitchToContinuous = { surahId, ayah ->
                        // Replace the current entry so back returns to
                        // wherever we came from, not the other reader mode.
                        val currentId = navController.currentBackStackEntry?.destination?.id
                        navController.navigate(Routes.reader(surahId, ayah)) {
                            if (currentId != null) {
                                popUpTo(currentId) { inclusive = true }
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(route = Routes.PRAYER_SETTINGS) {
                PrayerSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenVoices = { navController.navigate(Routes.PRAYER_VOICE) },
                    onOpenOnboarding = { navController.navigate(Routes.PRAYER_ONBOARDING) },
                )
            }

            composable(route = Routes.PRAYER_ONBOARDING) {
                PrayerOnboardingScreen(
                    onSkip = { navController.popBackStack() },
                    onOpenAdhanSettings = {
                        navController.popBackStack()
                        navController.navigate(Routes.PRAYER_SETTINGS)
                    },
                )
            }

            composable(route = Routes.PRAYER_VOICE) {
                AdhanVoiceScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLibrary = { prayer ->
                        navController.navigate("prayer/adhan-library/${prayer.name}")
                    },
                )
            }

            composable(
                route = Routes.PRAYER_ADHAN_LIBRARY,
                arguments = listOf(
                    androidx.navigation.navArgument("prayer") {
                        type = NavType.StringType
                        defaultValue = Prayer.FAJR.name
                    },
                ),
            ) { entry ->
                AdhanLibraryScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(route = Routes.BOOKMARKS) {
                BookmarksScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAyah = { surahId, ayahNumber ->
                        openReadingPosition(surahId, ayahNumber)
                    },
                )
            }

            composable(
                route = Routes.FULLSCREEN_PREVIEW,
                arguments = listOf(
                    navArgument("surahId") { type = NavType.IntType },
                    navArgument("ayahNumber") { type = NavType.IntType },
                ),
                enterTransition = {
                    if (motionEnabled) {
                        slideInVertically(tween(MotionDurations.SLOW)) { it } + fadeIn(tween(MotionDurations.SLOW))
                    } else {
                        fadeIn(tween(0))
                    }
                },
                popExitTransition = {
                    if (motionEnabled) {
                        slideOutVertically(tween(MotionDurations.SLOW)) { it } + fadeOut(tween(MotionDurations.FAST))
                    } else {
                        fadeOut(tween(0))
                    }
                },
            ) {
                FullscreenPreviewScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
        }
    }
}

private fun Int.durationSafe(): Int = this

/** Standard bottom-navigation navigation: single top, state preserved. */
private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
