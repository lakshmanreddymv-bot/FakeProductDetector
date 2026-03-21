package com.example.fakeproductdetector.domain.repository

import com.example.fakeproductdetector.domain.model.Category
import com.example.fakeproductdetector.domain.model.ScanResult
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun scanProduct(
        imageUri: String,
        barcode: String?,
        category: Category
    ): Flow<ScanResult>

    fun getScanHistory(): Flow<List<ScanResult>>

    fun getScanById(id: String): Flow<ScanResult?>

    suspend fun deleteScan(id: String)
}