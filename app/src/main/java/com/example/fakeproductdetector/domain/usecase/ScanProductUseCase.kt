package com.example.fakeproductdetector.domain.usecase

import com.example.fakeproductdetector.domain.model.Category
import com.example.fakeproductdetector.domain.model.ScanEvent
import com.example.fakeproductdetector.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// S: Single Responsibility — encapsulates only the "scan a product" operation
// D: Dependency Inversion — depends on the ProductRepository interface, not its implementation
/**
 * Use case that initiates the full product authenticity scan pipeline.
 *
 * Wraps [ProductRepository.scanProduct] and exposes it as an invocable operator function,
 * keeping ViewModels decoupled from repository details.
 *
 * @property repository The repository abstraction used to perform the scan.
 */
class ScanProductUseCase @Inject constructor(
    private val repository: ProductRepository
) {
    /**
     * Initiates a product authenticity scan and returns a Flow of pipeline events.
     *
     * @param imageUri URI of the captured product image (file:// or content://).
     * @param barcode Optional barcode or QR value; null if none was detected.
     * @param category Product category used to guide the AI prompts.
     * @return A cold [Flow] of [ScanEvent]s: zero or more [ScanEvent.Progress] followed by one [ScanEvent.Result].
     */
    operator fun invoke(
        imageUri: String,
        barcode: String?,
        category: Category
    ): Flow<ScanEvent> = repository.scanProduct(imageUri, barcode, category)
}