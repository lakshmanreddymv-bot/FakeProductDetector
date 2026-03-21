package com.example.fakeproductdetector.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.fakeproductdetector.domain.model.Product

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