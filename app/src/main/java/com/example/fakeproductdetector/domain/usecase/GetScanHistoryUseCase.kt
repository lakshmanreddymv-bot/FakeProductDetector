package com.example.fakeproductdetector.domain.usecase

import com.example.fakeproductdetector.domain.model.ScanResult
import com.example.fakeproductdetector.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// S: Single Responsibility — encapsulates only the "retrieve scan history" operation
// D: Dependency Inversion — depends on the ProductRepository interface, not its implementation
/**
 * Use case that retrieves the full persisted scan history as a live Flow.
 *
 * Wraps [ProductRepository.getScanHistory] and exposes it as an invocable operator function,
 * keeping ViewModels decoupled from repository details.
 *
 * @property repository The repository abstraction used to fetch scan history.
 */
class GetScanHistoryUseCase @Inject constructor(
    private val repository: ProductRepository
) {
    /**
     * Returns a live [Flow] of all scan results ordered newest-first.
     *
     * The Flow updates automatically whenever the underlying database changes.
     *
     * @return A [Flow] emitting the current list of [ScanResult]s on every database change.
     */
    operator fun invoke(): Flow<List<ScanResult>> = repository.getScanHistory()
}