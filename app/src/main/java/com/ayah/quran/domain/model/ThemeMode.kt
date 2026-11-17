package com.ayah.quran.domain.model

/**
 * User-facing appearance preference.
 *
 * Stored as the enum name string in DataStore so new values can be added
 * without schema changes; unknown stored values degrade to [SYSTEM].
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromStorage(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}
