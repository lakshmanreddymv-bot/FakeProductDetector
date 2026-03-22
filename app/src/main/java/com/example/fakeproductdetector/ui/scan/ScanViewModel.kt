package com.example.fakeproductdetector.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fakeproductdetector.data.api.GeminiQuotaError
import com.example.fakeproductdetector.domain.model.Category
import com.example.fakeproductdetector.domain.model.ScanEvent
import com.example.fakeproductdetector.domain.usecase.ScanProductUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Cooldown duration (seconds) for per-minute Gemini quota violations. */
private const val RPM_COOLDOWN_SECONDS   = 60   // per-minute quota window

/** Cooldown duration (seconds) shown for daily quota exhaustion (placeholder — actual reset is midnight). */
private const val DAILY_COOLDOWN_SECONDS = 300  // 5 min placeholder for daily exhaustion

// S: Single Responsibility — manages scan lifecycle state and rate-limit countdowns only
// D: Dependency Inversion — depends on ScanProductUseCase (domain layer), not on repository or API directly
/**
 * Follows Unidirectional Data Flow (UDF) pattern:
 * - Events flow UP from [ScanScreen] via public functions ([scanProduct], [reset])
 * - State flows DOWN to [ScanScreen] via [uiState] StateFlow
 * - No direct state mutation from the UI layer
 */
/**
 * ViewModel for [ScanScreen] that drives the product authenticity scan pipeline.
 *
 * Manages the full scan lifecycle: initiating scans via [ScanProductUseCase], mapping
 * pipeline events to [ScanUiState], handling [GeminiQuotaError] variants with appropriate
 * countdowns, and exposing a single immutable [uiState] for the UI to observe.
 *
 * @property scanProductUseCase Use case that executes the Gemini + Claude scan pipeline.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanProductUseCase: ScanProductUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)

    /** Immutable state observed by [ScanScreen]. Never mutated directly from the UI. */
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /** Guard flag preventing concurrent scan launches. */
    private var isScanning = false

    /** Coroutine job that ticks the rate-limit countdown; cancelled on reset. */
    private var countdownJob: Job? = null

    /**
     * Initiates a product authenticity scan for the image at [imageUri].
     *
     * No-ops if a scan is already in progress or if the UI is currently rate-limited.
     * Maps [ScanEvent.Progress] to [ScanUiState.Loading] and [ScanEvent.Result] to
     * [ScanUiState.Success]. Maps [GeminiQuotaError] subtypes to [ScanUiState.RateLimited]
     * with appropriate countdown durations; all other errors map to [ScanUiState.Error].
     *
     * @param imageUri URI of the captured product image (file:// or content://).
     * @param barcode Optional barcode or QR value detected before capture; null if none.
     * @param category Product category selected by the user to guide AI analysis.
     */
    fun scanProduct(imageUri: String, barcode: String?, category: Category) {
        if (isScanning) return
        if (_uiState.value is ScanUiState.RateLimited) return
        isScanning = true

        viewModelScope.launch {
            _uiState.value = ScanUiState.Loading("Scanning…")
            scanProductUseCase(imageUri, barcode, category)
                .catch { e ->
                    isScanning = false
                    when (e) {
                        // ── Token limit per minute ─────────────────────────────────────
                        is GeminiQuotaError.TokenLimitPerMinute -> startCountdown(
                            seconds  = RPM_COOLDOWN_SECONDS,
                            isQuotaExhausted = false,
                            title    = "Token limit reached",
                            subtitle = "Image data exceeded free-tier token quota. Ready in"
                        )
                        // ── Requests per minute ────────────────────────────────────────
                        is GeminiQuotaError.RequestsPerMinute -> startCountdown(
                            seconds  = RPM_COOLDOWN_SECONDS,
                            isQuotaExhausted = false,
                            title    = "Too many scans",
                            subtitle = "Free tier allows 15 requests/min. Ready in"
                        )
                        // ── Daily quota fully exhausted ────────────────────────────────
                        is GeminiQuotaError.DailyLimitExhausted -> startCountdown(
                            seconds  = DAILY_COOLDOWN_SECONDS,
                            isQuotaExhausted = true,
                            title    = "Daily quota exhausted",
                            subtitle = "Enable billing at aistudio.google.com — or retry in"
                        )
                        // ── Generic / unrecognised quota error ─────────────────────────
                        is GeminiQuotaError -> startCountdown(
                            seconds  = RPM_COOLDOWN_SECONDS,
                            isQuotaExhausted = false,
                            title    = "API rate limit reached",
                            subtitle = "Ready to scan again in"
                        )
                        // ── Non-quota errors ───────────────────────────────────────────
                        else -> {
                            val msg = when {
                                e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                                e.message?.contains("No address associated", ignoreCase = true) == true ||
                                e.message?.contains("failed to connect", ignoreCase = true) == true ||
                                e.message?.contains("network", ignoreCase = true) == true ->
                                    "No internet connection. Please connect and try again."
                                else -> e.message ?: "Unknown error occurred"
                            }
                            _uiState.value = ScanUiState.Error(msg)
                        }
                    }
                }
                .collect { event ->
                    when (event) {
                        is ScanEvent.Progress -> _uiState.value = ScanUiState.Loading(event.message)
                        is ScanEvent.Result   -> {
                            isScanning = false
                            _uiState.value = ScanUiState.Success(event.scanResult)
                        }
                    }
                }
        }
    }

    /**
     * Starts a countdown that ticks [seconds] down to zero, emitting a [ScanUiState.RateLimited]
     * state on each tick. Automatically resets to [ScanUiState.Idle] when the countdown finishes.
     *
     * Any previously running countdown is cancelled before the new one starts.
     *
     * @param seconds Total number of seconds to count down.
     * @param isQuotaExhausted `true` for daily quota exhaustion; affects banner colour and messaging.
     * @param title Short headline for the rate-limit banner.
     * @param subtitle Descriptive sub-label shown before the countdown timer.
     */
    private fun startCountdown(
        seconds: Int,
        isQuotaExhausted: Boolean,
        title: String,
        subtitle: String
    ) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                _uiState.value = ScanUiState.RateLimited(
                    secondsRemaining = remaining,
                    isQuotaExhausted = isQuotaExhausted,
                    title            = title,
                    subtitle         = subtitle
                )
                delay(1_000L)
            }
            _uiState.value = ScanUiState.Idle
        }
    }

    /**
     * Cancels any active countdown or scan and returns the UI to [ScanUiState.Idle].
     *
     * Called by [ScanScreen] after a [ScanUiState.Success] or [ScanUiState.Error] state
     * has been handled (e.g. after navigation or Snackbar display).
     */
    fun reset() {
        countdownJob?.cancel()
        isScanning = false
        _uiState.value = ScanUiState.Idle
    }

    /**
     * Cancels the countdown job when the ViewModel is cleared to prevent coroutine leaks.
     */
    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}