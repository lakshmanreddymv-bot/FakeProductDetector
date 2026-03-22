package com.example.fakeproductdetector.domain.model

// S: Single Responsibility — represents a scanned product's identity and metadata only
/**
 * Domain model representing a physical product that has been submitted for an authenticity scan.
 *
 * @property id Unique identifier for this product instance (UUID).
 * @property name Human-readable product name detected by the AI pipeline.
 * @property barcode Raw barcode or QR code value scanned from the product, or null if none detected.
 * @property imageUri URI pointing to the captured product image (file:// or content://).
 * @property category Product category used to guide AI analysis prompts.
 * @property scannedAt Unix timestamp (milliseconds) when the product was submitted for scanning.
 */
data class Product(
    val id: String,
    val name: String,
    val barcode: String?,
    val imageUri: String,
    val category: Category,
    val scannedAt: Long
)