package com.example.fakeproductdetector.ui.scan

import com.example.fakeproductdetector.domain.model.ScanResult

sealed class ScanUiState {
    object Idle : ScanUiState()
    data class Loading(val message: String = "Analyzing product...") : ScanUiState()
    data class Success(val result: ScanResult) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
    /** Rate-limited state: shows a live countdown until the user can retry. */
    data class RateLimited(
        val secondsRemaining: Int,
        val isQuotaExhausted: Boolean = false,
        val title: String = "API rate limit reached",
        val subtitle: String = "Ready to scan again in"
    ) : ScanUiState()
}