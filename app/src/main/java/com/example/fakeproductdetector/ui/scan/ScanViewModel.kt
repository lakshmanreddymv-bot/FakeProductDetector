package com.example.fakeproductdetector.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fakeproductdetector.data.api.GeminiQuotaError
import com.example.fakeproductdetector.domain.model.Category
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

private const val RPM_COOLDOWN_SECONDS   = 60   // per-minute quota window
private const val DAILY_COOLDOWN_SECONDS = 300  // 5 min placeholder for daily exhaustion

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanProductUseCase: ScanProductUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var isScanning = false
    private var countdownJob: Job? = null

    fun scanProduct(imageUri: String, barcode: String?, category: Category) {
        if (isScanning) return
        if (_uiState.value is ScanUiState.RateLimited) return
        isScanning = true

        viewModelScope.launch {
            _uiState.value = ScanUiState.Loading("Analyzing with Gemini Vision…")
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
                        else -> _uiState.value = ScanUiState.Error(
                            e.message ?: "Unknown error occurred"
                        )
                    }
                }
                .collect { result ->
                    isScanning = false
                    _uiState.value = ScanUiState.Success(result)
                }
        }
    }

    /** Ticks down every second; auto-resets to [ScanUiState.Idle] when done. */
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

    fun reset() {
        countdownJob?.cancel()
        isScanning = false
        _uiState.value = ScanUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}
