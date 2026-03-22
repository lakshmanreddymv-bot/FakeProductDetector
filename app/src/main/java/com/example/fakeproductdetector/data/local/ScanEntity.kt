package com.example.fakeproductdetector.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.fakeproductdetector.domain.model.Product

// S: Single Responsibility — represents a single persisted scan record in the Room database
/**
 * Room database entity that persists a completed product authenticity scan.
 *
 * Mirrors the structure of [com.example.fakeproductdetector.domain.model.ScanResult] but uses
 * Room-compatible types. [Product] and [List<String>] fields are serialised to JSON via
 * [ScanDatabase.Converters].
 *
 * @property id Unique identifier for this scan (UUID); serves as the primary key.
 * @property product The scanned product, serialised to a JSON string by [ScanDatabase.Converters].
 * @property authenticityScore Float 0–100 representing the final authenticity confidence.
 * @property verdict String name of the [com.example.fakeproductdetector.domain.model.Verdict] enum value.
 * @property redFlags List of concern strings, serialised to JSON by [ScanDatabase.Converters].
 * @property explanation Combined analysis text from Gemini and Claude.
 * @property scannedAt Unix timestamp (milliseconds) when the scan was completed.
 */
@Entity(tableName = "scan_history")
data class ScanEntity(
    @PrimaryKey val id: String,
    val product: Product,
    val authenticityScore: Float,
    val verdict: String,
    val redFlags: List<String>,
    val explanation: String,
    val scannedAt: Long
)