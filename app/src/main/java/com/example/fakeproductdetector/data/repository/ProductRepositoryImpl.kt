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

internal fun Exception.isRateLimit() =
    message?.contains("429") == true ||
    message?.contains("Too Many Requests", ignoreCase = true) == true ||
    message?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true ||
    message?.contains("quota", ignoreCase = true) == true

internal fun Exception.isQuotaExhausted() =
    message?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true ||
    message?.contains("quota exceeded", ignoreCase = true) == true

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

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val scanDao: ScanDao,
    private val geminiApi: GeminiVisionApiImpl,
    private val claudeApi: ClaudeVerificationApiImpl,
    @ApplicationContext private val context: Context
) : ProductRepository {

    companion object { private const val TAG = "ProductRepo" }

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

    override fun getScanHistory(): Flow<List<ScanResult>> =
        scanDao.getAllScans().map { entities -> entities.map { it.toDomain() } }

    override fun getScanById(id: String): Flow<ScanResult?> =
        scanDao.getScanById(id).map { it?.toDomain() }

    override suspend fun deleteScan(id: String) {
        val entity = scanDao.getScanByIdOnce(id)
        scanDao.deleteScan(id)
        entity?.let { deleteLocalImage(it.product.imageUri) }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork?.let {
            cm.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } ?: false
    }

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

private fun ScanResult.toEntity(): ScanEntity = ScanEntity(
    id = id,
    product = product,
    authenticityScore = authenticityScore,
    verdict = verdict.name,
    redFlags = redFlags,
    explanation = explanation,
    scannedAt = scannedAt
)

private fun ScanEntity.toDomain(): ScanResult = ScanResult(
    id = id,
    product = product,
    authenticityScore = authenticityScore,
    verdict = Verdict.valueOf(verdict),
    redFlags = redFlags,
    explanation = explanation,
    scannedAt = scannedAt
)