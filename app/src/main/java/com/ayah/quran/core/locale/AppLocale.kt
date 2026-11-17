package com.ayah.quran.core.locale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Applies the user's persisted app language to a [Context].
 *
 * The tag lives in SharedPreferences (not DataStore) because
 * [android.content.Context.attachBaseContext] runs before any async storage
 * can be read; the same tag is mirrored into DataStore by
 * [com.ayah.quran.data.settings.DataStoreUserPreferencesRepository] for the
 * Settings UI and Flow observers.
 *
 * Arabic is the default on a fresh install (null stored tag), per spec — the
 * system language is deliberately not consulted.
 */
object AppLocale {

    const val ARABIC = "ar"
    const val ENGLISH = "en"

    const val PREFS_NAME = "ayah_sync_prefs"
    const val KEY_LANGUAGE = "app_language_tag"

    /** The stored tag, or null when the user has never chosen (→ Arabic). */
    fun savedTag(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)

    /**
     * True when the process locale (set by [wrap]) is Arabic. UI code uses
     * this to pick presentation-only data such as surah display names.
     */
    val isArabic: Boolean
        get() = Locale.getDefault().language == ARABIC

    /** Wraps [base] so its resources resolve against the persisted language. */
    fun wrap(base: Context): Context {
        val tag = savedTag(base) ?: ARABIC
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(android.os.LocaleList(locale))
        // setLocales also derives layout direction, so Arabic flips the UI to RTL.
        return base.createConfigurationContext(config)
    }
}
