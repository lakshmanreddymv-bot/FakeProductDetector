package com.example.fakeproductdetector.domain.usecase

import com.example.fakeproductdetector.domain.model.ScanResult
import com.example.fakeproductdetector.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetScanHistoryUseCase @Inject constructor(
    private val repository: ProductRepository
) {
    operator fun invoke(): Flow<List<ScanResult>> = repository.getScanHistory()
}
