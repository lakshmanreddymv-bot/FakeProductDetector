package com.example.fakeproductdetector.domain.usecase

import com.example.fakeproductdetector.domain.model.Category
import com.example.fakeproductdetector.domain.model.ScanEvent
import com.example.fakeproductdetector.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ScanProductUseCase @Inject constructor(
    private val repository: ProductRepository
) {
    operator fun invoke(
        imageUri: String,
        barcode: String?,
        category: Category,
        bitmap: android.graphics.Bitmap? = null
    ): Flow<ScanEvent> = repository.scanProduct(imageUri, barcode, category, bitmap)
}