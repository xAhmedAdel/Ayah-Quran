package com.ayah.quran

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.core.designsystem.theme.AyahTheme
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.navigation.AyahApp
import com.ayah.quran.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

/** The single activity; all UI is Compose destinations inside [AyahApp]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /**
     * Wrapping here (not only in [AyahApplication]) means a language change
     * persisted by Settings takes effect on the very next [recreate] call,
     * without waiting for a full process restart.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            AyahTheme(themeMode = themeMode) {
                AyahApp()
            }
        }
    }
}
