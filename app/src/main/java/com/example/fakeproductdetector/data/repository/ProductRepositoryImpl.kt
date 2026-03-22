package com.example.fakeproductdetector.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.fakeproductdetector.data.api.ClaudeVerificationApiImpl
import com.example.fakeproductdetector.data.api.GeminiVisionApiImpl
import com.example.fakeproductdetector.data.local.ScanDao
import com.example.fakeproductdetector.data.local.ScanEntity
import com.example.fakeproductdetector.domain.model.Category
import com.example.fakeproductdetector.domain.model.Product
import com.example.fakeproductdetector.domain.model.ScanEvent
import com.example.fakeproductdetector.domain.model.ScanResult
import com.example.fakeproductdetector.domain.model.Verdict
import com.example.fakeproductdetector.domain.repository.ProductRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NO retry on 429 — Gemini free tier is 15 RPM.
 * Retrying after a 429 immediately just burns more quota and causes more 429s.
 * Instead we throw immediately so the ViewModel shows the countdown banner,
 * and the user waits the full minute before scanning again.
 *
 * We only retry on transient network errors (not rate limits).
 */
private const val MAX_NETWORK_RETRIES = 1
private const val NETWORK_RETRY_DELAY_MS = 3_000L

/**
 * Returns true if this exception represents an API rate-limit or quota error.
 *
 * Used by [withRetry] to skip retry logic for quota-related failures.
 */
internal fun Exception.isRateLimit() =
    message?.contains("429") == true ||
    message?.contains("Too Many Requests", ignoreCase = true) == true ||
    message?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true ||
    message?.contains("quota", ignoreCase = true) == true

/**
 * Returns true if this exception specifically indicates a daily quota exhaustion.
 */
internal fun Exception.isQuotaExhausted() =
    message?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true ||
    message?.contains("quota exceeded", ignoreCase = true) == true

/**
 * Executes [block] with at most [MAX_NETWORK_RETRIES] retry attempts on transient failures.
 *
 * Rate-limit errors are re-thrown immediately without retrying to avoid burning additional quota.
 *
 * @param block The suspending operation to execute with retry support.
 * @return The result of [block] on a successful attempt.
 * @throws Exception the original exception after exhausting all retry attempts.
 */
private suspend fun <T> withRetry(block: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: Exception) {
            if (e.isRateLimit()) throw e
            attempt++
            if (attempt >= MAX_NETWORK_RETRIES) throw e
            delay(NETWORK_RETRY_DELAY_MS)
        }
    }
}

// S: Single Responsibility — orchestrates the scan pipeline and local database persistence
// D: Dependency Inversion — implements ProductRepository interface; ViewModel depends on the interface
/**
 * Concrete implementation of [ProductRepository] that coordinates the two-stage AI scan pipeline
 * (Gemini Vision → Claude cross-verification) and persists results to the local Room database.
 *
 * Handles offline detection, rate-limit errors, and graceful fallback to Gemini-only results
 * when Claude is unavailable.
 *
 * @property scanDao Room DAO for reading and writing scan history records.
 * @property geminiApi Gemini Vision API wrapper for image-based product analysis.
 * @property claudeApi Claude API wrapper for cross-verification of the Gemini analysis.
 * @property context Application context used for network checks and image URI resolution.
 */
@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val scanDao: ScanDao,
    private val geminiApi: GeminiVisionApiImpl,
    private val claudeApi: ClaudeVerificationApiImpl,
    @ApplicationContext private val context: Context
) : ProductRepository {

    companion object { private const val TAG = "ProductRepo" }

    /**
     * Runs the full authenticity scan pipeline for the product image at [imageUri].
     *
     * Emits a [ScanEvent.Progress] for each pipeline step, then emits [ScanEvent.Result]
     * when complete. If the device is offline, returns a placeholder result immediately.
     * If Claude fails, falls back to the Gemini-only result.
     *
     * @param imageUri URI of the captured product image (file:// or content://).
     * @param barcode Optional barcode or QR value detected before capture.
     * @param category Product category used to guide the AI prompts.
     * @return A cold [Flow] of [ScanEvent]s.
     */
    override fun scanProduct(
        imageUri: String,
        barcode: String?,
        category: Category
    ): Flow<ScanEvent> = flow {

        // ── Offline guard ─────────────────────────────────────────────────
        if (!isNetworkAvailable()) {
            val product = Product(
                id = UUID.randomUUID().toString(),
                name = "Unknown Product",
                barcode = barcode,
                imageUri = imageUri,
                category = category,
                scannedAt = System.currentTimeMillis()
            )
            val result = ScanResult(
                id = UUID.randomUUID().toString(),
                product = product,
                authenticityScore = 50f,
                verdict = Verdict.SUSPICIOUS,
                redFlags = emptyList(),
                explanation = "No internet connection. Connect to the internet for full AI analysis.",
                scannedAt = System.currentTimeMillis()
            )
            scanDao.insertScan(result.toEntity())
            emit(ScanEvent.Result(result))
            return@flow
        }

        // ── Step 1: Gemini Vision ─────────────────────────────────────────
        emit(ScanEvent.Progress("Analyzing with Gemini…"))
        val geminiAnalysis = try {
            withRetry { geminiApi.analyze(imageUri, category) }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini FAILED: ${e.message}")
            throw e
        }
        Log.d(TAG, "Gemini SUCCESS — score: ${geminiAnalysis.authenticityScore}, verdict: ${geminiAnalysis.verdict}")

        val product = Product(
            id = UUID.randomUUID().toString(),
            name = geminiAnalysis.productName,
            barcode = barcode,
            imageUri = imageUri,
            category = category,
            scannedAt = System.currentTimeMillis()
        )

        // ── Step 2: Claude cross-verification ────────────────────────────
        emit(ScanEvent.Progress("Verifying with Claude…"))
        val scanResult = try {
            withRetry { claudeApi.verify(geminiAnalysis, product) }.also {
                Log.d(TAG, "Claude SUCCESS — final score: ${it.authenticityScore}, verdict: ${it.verdict}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Claude FAILED (falling back to Gemini-only): ${e.message}")
            ScanResult(
                id = UUID.randomUUID().toString(),
                product = product,
                authenticityScore = geminiAnalysis.authenticityScore,
                verdict = geminiAnalysis.verdict,
                redFlags = geminiAnalysis.redFlags,
                explanation = "[Gemini only — Claude verification skipped: ${e.message}]\n\n${geminiAnalysis.explanation}",
                scannedAt = System.currentTimeMillis()
            )
        }

        scanDao.insertScan(scanResult.toEntity())
        emit(ScanEvent.Result(scanResult))
    }

    /**
     * Returns a live [Flow] of all persisted scan results, ordered newest-first.
     *
     * @return A [Flow] of [ScanResult] lists that updates on every database change.
     */
    override fun getScanHistory(): Flow<List<ScanResult>> =
        scanDao.getAllScans().map { entities -> entities.map { it.toDomain() } }

    /**
     * Returns a live [Flow] for the scan result identified by [id].
     *
     * @param id Unique identifier of the scan to observe.
     * @return A [Flow] emitting the matching [ScanResult], or null if not found.
     */
    override fun getScanById(id: String): Flow<ScanResult?> =
        scanDao.getScanById(id).map { it?.toDomain() }

    /**
     * Deletes the scan record identified by [id] and attempts to delete its associated local image.
     *
     * @param id Unique identifier of the scan to delete.
     */
    override suspend fun deleteScan(id: String) {
        val entity = scanDao.getScanByIdOnce(id)
        scanDao.deleteScan(id)
        entity?.let { deleteLocalImage(it.product.imageUri) }
    }

    /**
     * Checks whether the device currently has an active internet connection.
     *
     * @return `true` if a network with internet capability is active, `false` otherwise.
     */
    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork?.let {
            cm.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } ?: false
    }

    /**
     * Attempts to delete the local image file or content URI associated with a scan.
     *
     * This is a best-effort operation; any failure is silently ignored since the
     * database record has already been removed.
     *
     * @param imageUri The URI string of the image to delete.
     */
    private fun deleteLocalImage(imageUri: String) {
        try {
            val uri = Uri.parse(imageUri)
            when (uri.scheme) {
                "file" -> File(uri.path ?: return).delete()
                "content" -> context.contentResolver.delete(uri, null, null)
            }
        } catch (_: Exception) {
            // Best-effort deletion; non-critical if it fails
        }
    }
}

/**
 * Maps a [ScanResult] domain model to its [ScanEntity] database representation.
 *
 * Stores [verdict] as its enum name string so it can be reconstructed via [Verdict.valueOf].
 */
private fun ScanResult.toEntity(): ScanEntity = ScanEntity(
    id = id,
    product = product,
    authenticityScore = authenticityScore,
    verdict = verdict.name,
    redFlags = redFlags,
    explanation = explanation,
    scannedAt = scannedAt
)

/**
 * Maps a [ScanEntity] database record back to its [ScanResult] domain representation.
 *
 * Converts the stored verdict string back to the [Verdict] enum value.
 */
private fun ScanEntity.toDomain(): ScanResult = ScanResult(
    id = id,
    product = product,
    authenticityScore = authenticityScore,
    verdict = Verdict.valueOf(verdict),
    redFlags = redFlags,
    explanation = explanation,
    scannedAt = scannedAt
)