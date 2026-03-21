package com.example.fakeproductdetector.domain.model

data class ScanResult(
    val id: String,
    val product: Product,
    val authenticityScore: Float,
    val verdict: Verdict,
    val redFlags: List<String>,
    val explanation: String,
    val scannedAt: Long
)