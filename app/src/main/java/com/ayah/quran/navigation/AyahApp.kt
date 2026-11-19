package com.ayah.quran.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ayah.quran.core.designsystem.motion.AyahMotionSpecs
import com.ayah.quran.core.designsystem.motion.MotionDurations
import com.ayah.quran.core.designsystem.motion.rememberMotionEnabled
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.domain.model.ReadingMode
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.ui.audio.AudioScreen
import com.ayah.quran.ui.audio.MiniPlayerBar
import com.ayah.quran.ui.audio.MiniPlayerViewModel
import com.ayah.quran.ui.audio.NowPlayingScreen
import com.ayah.quran.ui.bookmarks.BookmarksScreen
import com.ayah.quran.ui.home.HomeScreen
import com.ayah.quran.ui.main.MainViewModel
import com.ayah.quran.ui.prayer.AdhanLibraryScreen
import com.ayah.quran.ui.prayer.AdhanVoiceScreen
import com.ayah.quran.ui.prayer.PrayerOnboardingScreen
import com.ayah.quran.ui.prayer.PrayerPermissionsViewModel
import com.ayah.quran.ui.prayer.PrayerSettingsScreen
import com.ayah.quran.ui.prayer.PrayerTimesScreen
import com.ayah.quran.ui.quran.QuranScreen
import com.ayah.quran.ui.reader.MushafPageReaderScreen
import com.ayah.quran.ui.reader.ReaderScreen
import com.ayah.quran.ui.settings.SettingsScreen
import com.ayah.quran.ui.wallpapers.FullscreenPreviewScreen
import com.ayah.quran.ui.wallpapers.WallpapersScreen

object Routes {
    const val SETTINGS = "settings"
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

    fun mushaf(surahId: Int = 0, page: Int = 0, ayahNumber: Int = 0): String =
        "mushaf?surahId=$surahId&page=$page&ayah=$ayahNumber"

    fun fullscreenPreview(surahId: Int, ayahNumber: Int): String =
        "preview/$surahId/$ayahNumber"
}

internal fun shouldShowMiniPlayer(route: String?): Boolean = when (route) {
    null,
    Routes.NOW_PLAYING,
    Routes.FULLSCREEN_PREVIEW,
    Routes.MUSHAF,
    Routes.READER,
    -> false
    else -> true
}

@Composable
fun AyahApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }
    val showMiniPlayer = shouldShowMiniPlayer(currentRoute)
    val motionEnabled = rememberMotionEnabled()
    val mainViewModel: MainViewModel = hiltViewModel()
    val readingMode by mainViewModel.readingMode.collectAsStateWithLifecycle()
    val permissionsViewModel: PrayerPermissionsViewModel = hiltViewModel()
    val onboardingCompleted by permissionsViewModel.onboardingCompleted.collectAsStateWithLifecycle()
    val miniViewModel: MiniPlayerViewModel = hiltViewModel()
    val miniState by miniViewModel.uiState.collectAsStateWithLifecycle()

    fun openReadingPosition(surahId: Int, ayahNumber: Int?) {
        if (readingMode == ReadingMode.MUSHAF) {
            navController.navigate(Routes.mushaf(surahId = surahId, ayahNumber = ayahNumber ?: 0))
        } else {
            navController.navigate(Routes.reader(surahId, ayahNumber))
        }
    }

    fun dur(millis: Int): Int = MotionDurations.resolve(millis, motionEnabled)

    Scaffold(
        modifier = modifier,
        contentWindowInsets = if (currentRoute == Routes.FULLSCREEN_PREVIEW) {
            WindowInsets(0, 0, 0, 0)
        } else {
            WindowInsets.navigationBars
        },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = showMiniPlayer && !miniState.isIdle,
                    enter = slideInVertically(
                        animationSpec = tween(
                            durationMillis = dur(MotionDurations.MEDIUM),
                            easing = AyahMotionSpecs.EmphasizedDecelerate,
                        ),
                    ) { it } + fadeIn(tween(dur(MotionDurations.FAST))),
                    exit = slideOutVertically(
                        animationSpec = tween(dur(MotionDurations.FAST)),
                    ) { it } + fadeOut(tween(dur(MotionDurations.MICRO))),
                ) {
                    MiniPlayerBar(
                        state = miniState,
                        onOpen = { navController.navigate(Routes.NOW_PLAYING) },
                        onPauseResume = {
                            when {
                                miniState.playback.restorable != null &&
                                    miniState.playback.surah == 0 ->
                                    miniViewModel.resumeLast()
                                miniState.playback.isPlaying -> miniViewModel.pause()
                                else -> miniViewModel.resume()
                            }
                        },
                        modifier = if (showBottomBar) Modifier else Modifier.navigationBarsPadding(),
                    )
                }
                if (showBottomBar) {
                    HorizontalDivider(
                        thickness = AyahTouchTargets.hairline,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                    ) {
                        TopLevelDestination.entries.forEach { destination ->
                            val selected = currentRoute == destination.route
                            NavigationBarItem(
                                modifier = Modifier.testTag("nav_${destination.route}"),
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
                                label = {
                                    Text(
                                        text = stringResource(destination.labelRes),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                alwaysShowLabel = true,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            enterTransition = {
                fadeIn(tween(dur(MotionDurations.FAST), easing = AyahMotionSpecs.EmphasizedDecelerate))
            },
            exitTransition = { fadeOut(tween(dur(MotionDurations.MICRO))) },
            popEnterTransition = { fadeIn(tween(dur(MotionDurations.FAST))) },
            popExitTransition = { fadeOut(tween(dur(MotionDurations.MICRO))) },
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(
                    onOpenQuran = { navController.navigateToTopLevel(TopLevelDestination.QURAN.route) },
                    onContinueReading = { surahId, ayahNumber ->
                        openReadingPosition(surahId, ayahNumber)
                    },
                    onOpenBookmarks = { navController.navigate(Routes.BOOKMARKS) },
                    onOpenWallpapers = { navController.navigateToTopLevel(TopLevelDestination.WALLPAPERS.route) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenPrayer = { navController.navigateToTopLevel(TopLevelDestination.PRAYER.route) },
                    onOpenAudio = { navController.navigateToTopLevel(TopLevelDestination.AUDIO.route) },
                    onOpenWallpaperPreview = { surahId, ayahNumber ->
                        navController.navigate(Routes.fullscreenPreview(surahId, ayahNumber))
                    },
                    showPrayerOnboardingCard = !onboardingCompleted,
                    onOpenPrayerOnboarding = { navController.navigate(Routes.PRAYER_ONBOARDING) },
                )
            }
            composable(TopLevelDestination.QURAN.route) {
                QuranScreen(
                    onOpenSurah = { surahId -> openReadingPosition(surahId, null) },
                    onOpenAyah = { surahId, ayahNumber -> openReadingPosition(surahId, ayahNumber) },
                    onOpenPage = { page -> navController.navigate(Routes.mushaf(page = page)) },
                )
            }
            composable(TopLevelDestination.PRAYER.route) {
                PrayerTimesScreen(
                    onOpenSettings = { navController.navigate(Routes.PRAYER_SETTINGS) },
                )
            }
            composable(TopLevelDestination.AUDIO.route) {
                AudioScreen()
            }
            composable(TopLevelDestination.WALLPAPERS.route) {
                WallpapersScreen(
                    onOpenFullscreenPreview = { surahId, ayahNumber ->
                        navController.navigate(Routes.fullscreenPreview(surahId, ayahNumber))
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPrayerSettings = { navController.navigate(Routes.PRAYER_SETTINGS) },
                    onOpenAudio = {
                        navController.popBackStack()
                        navController.navigateToTopLevel(TopLevelDestination.AUDIO.route)
                    },
                    onOpenWallpapers = {
                        navController.popBackStack()
                        navController.navigateToTopLevel(TopLevelDestination.WALLPAPERS.route)
                    },
                )
            }
            composable(
                route = Routes.NOW_PLAYING,
                enterTransition = {
                    slideInVertically(tween(dur(MotionDurations.SLOW))) { it / 4 } +
                        fadeIn(tween(dur(MotionDurations.MEDIUM)))
                },
                popExitTransition = {
                    slideOutVertically(tween(dur(MotionDurations.MEDIUM))) { it / 4 } +
                        fadeOut(tween(dur(MotionDurations.FAST)))
                },
            ) {
                NowPlayingScreen(
                    onBack = { navController.popBackStack() },
                )
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
                        val currentId = navController.currentBackStackEntry?.destination?.id
                        navController.navigate(Routes.reader(surahId)) {
                            if (currentId != null) {
                                popUpTo(currentId) { inclusive = true }
                            }
                            launchSingleTop = true
                        }
                    },
                    onSwitchToMushaf = { surahId, ayah ->
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
                    navArgument("prayer") {
                        type = NavType.StringType
                        defaultValue = Prayer.FAJR.name
                    },
                ),
            ) {
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
                        slideInVertically(tween(MotionDurations.SLOW)) { it } +
                            fadeIn(tween(MotionDurations.SLOW))
                    } else {
                        fadeIn(tween(0))
                    }
                },
                popExitTransition = {
                    if (motionEnabled) {
                        slideOutVertically(tween(MotionDurations.SLOW)) { it } +
                            fadeOut(tween(MotionDurations.FAST))
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

private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
