package com.ayah.quran.domain.model

/**
 * State of the first-launch Quran dataset import.
 *
 * [Failed] carries the reason; the UI must never treat a failed import as
 * available data (no partial or fake Quran content is ever shown).
 */
sealed interface QuranImportState {
    data object NotStarted : QuranImportState
    data class Importing(val progress: Float) : QuranImportState
    data object Completed : QuranImportState
    data class Failed(val reason: String) : QuranImportState
}
