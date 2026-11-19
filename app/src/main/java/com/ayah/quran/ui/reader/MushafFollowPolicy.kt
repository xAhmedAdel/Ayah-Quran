package com.ayah.quran.ui.reader

/**
 * Phase 17 slice 3: decides whether the mushaf page reader should navigate
 * to the page of the currently RECITED ayah.
 *
 * Synchronization policy (predictable, loop-free):
 * - Follow only while a recitation session is active (surah known), even
 *   when paused — a seek while paused is still user intent to move.
 * - Follow only when the RECITED ayah CHANGED since the last navigation we
 *   performed. This is what respects manual page browsing (§4): flipping
 *   away while the SAME ayah keeps reciting/repeating never yanks the
 *   reader back; navigation resumes naturally when playback ADVANCES to a
 *   new ayah.
 * - Same page → nothing to do (also covers "user manually landed on the
 *   playing ayah's page").
 * - An active user ayah-selection (tap) means the reader is serving the
 *   user's intent — do not follow until the selection is cleared.
 *
 * Pure and JVM-testable; the mapping itself stays in
 * `PageForAyahUseCase` (the authentic 604-page source of truth — never
 * recalculated here).
 */
object MushafFollowPolicy {

    /** (surahId, ayahInSurah) of the ayah currently being recited. */
    data class RecitedAyah(val surahId: Int, val ayahInSurah: Int) {
        init {
            require(surahId in 1..114 && ayahInSurah >= 1)
        }

        val key: Pair<Int, Int> get() = surahId to ayahInSurah
    }

    data class Inputs(
        /** 1-based page currently displayed by the pager (null = not settled). */
        val displayedPage: Int?,
        /** 1-based page containing the recited ayah (authentic mapping). */
        val recitedPage: Int?,
        val recitedAyah: RecitedAyah?,
        /** (surahId, ayahInSurah) of the last navigation this policy caused. */
        val lastFollowedAyah: Pair<Int, Int>?,
        /** The user tapped/selected a specific ayah and has not cleared it. */
        val userSelectionActive: Boolean,
    )

    fun shouldNavigate(inputs: Inputs): Boolean {
        val recited = inputs.recitedAyah ?: return false
        val target = inputs.recitedPage ?: return false
        val displayed = inputs.displayedPage ?: return false
        if (displayed !in 1..604) return false

        // Respect the user's explicit selection (their reading intent).
        if (inputs.userSelectionActive) return false

        // Anti-loop + manual-browse respect: only ONE navigation per recited
        // ayah. While the same ayah repeats, the reader stays where the user
        // put it; following resumes when playback advances to a new ayah.
        if (recited.key == inputs.lastFollowedAyah) return false

        return displayed != target
    }

    /** The follow bookkeeping to record after a successful navigation. */
    fun followedKey(recitedAyah: RecitedAyah): Pair<Int, Int> = recitedAyah.key
}
