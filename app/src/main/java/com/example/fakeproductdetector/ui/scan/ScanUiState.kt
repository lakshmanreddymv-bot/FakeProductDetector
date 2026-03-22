package com.example.fakeproductdetector.ui.scan

import com.example.fakeproductdetector.domain.model.ScanResult

// S: Single Responsibility — models all possible UI states for the scan screen
/**
 * Sealed hierarchy representing every possible state of the scan screen UI.
 *
 * [ScanViewModel] exposes this via a [kotlinx.coroutines.flow.StateFlow] and [ScanScreen]
 * renders each subtype. No state mutation is performed by the UI layer.
 */
sealed class ScanUiState {
    /** The screen is idle and ready for the user to initiate a new scan. */
    object Idle : ScanUiState()

    /**
     * The scan pipeline is running; display a loading indicator with the current step message.
     *
     * @property message Human-readable description of the current pipeline step (e.g. "Analyzing with Gemini…").
     */
    data class Loading(val message: String = "Analyzing product...") : ScanUiState()

    /**
     * The scan pipeline completed successfully.
     *
     * @property result The completed [ScanResult] to display or navigate to.
     */
    data class Success(val result: ScanResult) : ScanUiState()

    /**
     * A non-quota error occurred during scanning (e.g. network failure).
     *
     * @property message User-facing error description shown in a Snackbar.
     */
    data class Error(val message: String) : ScanUiState()

    /**
     * The Gemini API rate limit has been hit; shows a live countdown until the user can retry.
     *
     * @property secondsRemaining Seconds remaining until the cooldown expires and scanning is re-enabled.
     * @property isQuotaExhausted `true` for daily quota exhaustion (longer countdown, purple banner).
     * @property title Short headline shown in the rate-limit banner.
     * @property subtitle Descriptive sub-label shown before the countdown timer.
     */
    data class RateLimited(
        val secondsRemaining: Int,
        val isQuotaExhausted: Boolean = false,
        val title: String = "API rate limit reached",
        val subtitle: String = "Ready to scan again in"
    ) : ScanUiState()
}