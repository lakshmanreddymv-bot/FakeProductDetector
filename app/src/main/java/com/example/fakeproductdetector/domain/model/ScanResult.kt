package com.example.fakeproductdetector.domain.model

// S: Single Responsibility — holds the complete authenticity analysis result for one product scan
/**
 * Domain model representing the completed authenticity analysis of a scanned product.
 *
 * This is the terminal output of the scan pipeline (Gemini vision + Claude cross-verification)
 * and is persisted to the local database via [com.example.fakeproductdetector.data.local.ScanEntity].
 *
 * @property id Unique identifier for this scan result (UUID).
 * @property product The product that was scanned and analysed.
 * @property authenticityScore Float from 0 to 100 indicating confidence of authenticity (100 = definitely authentic).
 * @property verdict The categorical verdict derived from the AI analysis.
 * @property redFlags List of specific concerns or anomalies detected by the AI; empty when none found.
 * @property explanation Human-readable summary of the analysis combining Gemini and Claude assessments.
 * @property scannedAt Unix timestamp (milliseconds) when the scan was completed.
 */
data class ScanResult(
    val id: String,
    val product: Product,
    val authenticityScore: Float,
    val verdict: Verdict,
    val redFlags: List<String>,
    val explanation: String,
    val scannedAt: Long
)