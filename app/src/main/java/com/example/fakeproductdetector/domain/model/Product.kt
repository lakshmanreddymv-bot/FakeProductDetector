package com.example.fakeproductdetector.domain.model

data class Product(
    val id: String,
    val name: String,
    val barcode: String?,
    val imageUri: String,
    val category: Category,
    val scannedAt: Long
)