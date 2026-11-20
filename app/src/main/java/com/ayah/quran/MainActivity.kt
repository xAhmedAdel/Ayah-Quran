package com.ayah.quran

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.background.CompanionReminderNotifier
import com.ayah.quran.core.designsystem.theme.AyahTheme
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.navigation.AyahApp
import com.ayah.quran.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val pendingDeepLinkRoute = MutableStateFlow<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        captureDeepLink(intent)
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val deepLinkRoute by pendingDeepLinkRoute.collectAsStateWithLifecycle()
            AyahTheme(themeMode = themeMode) {
                AyahApp(
                    pendingDeepLinkRoute = deepLinkRoute,
                    onConsumeDeepLinkRoute = { pendingDeepLinkRoute.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureDeepLink(intent)
    }

    private fun captureDeepLink(intent: Intent?) {
        val route = intent?.getStringExtra(CompanionReminderNotifier.EXTRA_COMPANION_ROUTE)
        if (!route.isNullOrBlank()) {
            pendingDeepLinkRoute.value = route
        }
    }
}
