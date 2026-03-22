package com.example.fakeproductdetector.domain.repository

import com.example.fakeproductdetector.domain.model.Category
import com.example.fakeproductdetector.domain.model.ScanEvent
import com.example.fakeproductdetector.domain.model.ScanResult
import kotlinx.coroutines.flow.Flow

// I: Interface Segregation — exposes only the operations needed by the domain layer
// D: Dependency Inversion — domain and UI depend on this abstraction, not on the data-layer implementation
/**
 * Contract for all product authenticity scanning and scan history operations.
 *
 * Implementations coordinate the AI pipeline (Gemini + Claude) and the local persistence layer.
 * The domain layer and ViewModels depend only on this interface, never on the concrete
 * [com.example.fakeproductdetector.data.repository.ProductRepositoryImpl].
 */
interface ProductRepository {

    /**
     * Runs the full authenticity scan pipeline for the given product image.
     *
     * Emits [ScanEvent.Progress] events during processing and a terminal [ScanEvent.Result]
     * when the pipeline completes. The result is also persisted to the local database.
     *
     * @param imageUri URI of the captured product image (file:// or content://).
     * @param barcode Optional barcode/QR value detected before capture; null if none.
     * @param category Product category used to tune the AI analysis prompts.
     * @return A cold [Flow] of [ScanEvent]s that drives UI loading and success states.
     */
    fun scanProduct(
        imageUri: String,
        barcode: String?,
        category: Category
    ): Flow<ScanEvent>

    /**
     * Returns a live [Flow] of all persisted scan results, ordered newest-first.
     *
     * The Flow updates automatically whenever the underlying Room table changes.
     *
     * @return A hot [Flow] emitting the current scan history list on every change.
     */
    fun getScanHistory(): Flow<List<ScanResult>>

    /**
     * Returns a live [Flow] for a single scan result identified by [id].
     *
     * Emits `null` if no record with the given [id] exists.
     *
     * @param id The unique identifier of the scan result to retrieve.
     * @return A [Flow] emitting the matching [ScanResult], or null if not found.
     */
    fun getScanById(id: String): Flow<ScanResult?>

    /**
     * Permanently deletes the scan record with the given [id] and its associated local image.
     *
     * @param id The unique identifier of the scan to delete.
     */
    suspend fun deleteScan(id: String)
}