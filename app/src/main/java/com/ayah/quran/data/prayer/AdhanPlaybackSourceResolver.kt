package com.ayah.quran.data.prayer

import androidx.annotation.RawRes
import com.ayah.quran.R
import com.ayah.quran.domain.model.prayer.AdhanRecording
import com.ayah.quran.domain.model.prayer.AdhanVoiceDefaults
import java.io.File
import javax.inject.Inject

/** Where a resolved recording's audio actually lives. */
sealed interface AdhanPlaybackSource {
    /** Bundled res/raw resource. */
    data class RawResource(@RawRes val resId: Int) : AdhanPlaybackSource
    /** Downloaded file in app-private storage. */
    data class LocalFile(val file: File) : AdhanPlaybackSource
    /** Remote recording not yet downloaded — callers fall back to default. */
    data object Missing : AdhanPlaybackSource
}

/**
 * Maps a stored voice id to a PLAYABLE source, applying the Phase 13 §3
 * fallback rules: unknown/missing id → default recording; a remote
 * recording that is not downloaded → falls back to the default recording
 * (never silence) and callers surface the explanation in the UI.
 */
class AdhanPlaybackSourceResolver @Inject constructor(
    private val catalog: BundledAdhanCatalog,
    private val downloadRepository: AdhanDownloadRepository,
) {

    fun resolve(voiceId: String?): Pair<AdhanRecording, AdhanPlaybackSource> {
        val recording = catalog.byId(voiceId) ?: catalog.defaultRecording
        return recording to sourceOf(recording)
    }

    fun sourceOf(recording: AdhanRecording): AdhanPlaybackSource = when {
        recording.isBundled -> AdhanPlaybackSource.RawResource(
            AdhanRawResources.resIdOf(recording.id),
        )

        else -> downloadRepository.downloadedFile(recording.id)
            ?.let { AdhanPlaybackSource.LocalFile(it) }
            ?: AdhanPlaybackSource.Missing
    }

    fun isDefault(voiceId: String?): Boolean =
        voiceId == null || voiceId == AdhanVoiceDefaults.DEFAULT_RECORDING_ID
}
