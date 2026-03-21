package com.example.fakeproductdetector.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.fakeproductdetector.data.api.ClaudeVerificationApiImpl
import com.example.fakeproductdetector.data.api.GeminiVisionApiImpl
import com.example.fakeproductdetector.data.local.ScanDao
import com.example.fakeproductdetector.data.local.ScanEntity
import com.example.fakeproductdetector.data.ml.ProductClassifier
import com.example.fakeproductdetector.data.ml.ProductClassifierInterface
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

// TFLite confidence thresholds for bypassing the cloud pipeline.
// The model outputs fake-probability in [0, 1]:
//   score < 0.05 → ≥95% confident AUTHENTIC → skip Gemini
//   score > 0.90 → ≥90% confident LIKELY_FAKE → skip Gemini
private const val TFLITE_AUTHENTIC_THRESHOLD = 0.05f
private const val TFLITE_FAKE_THRESHOLD = 0.90f

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
    private val classifier: ProductClassifierInterface,
    @ApplicationContext private val context: Context
) : ProductRepository {

    companion object { private const val TAG = "ProductRepo" }

    override fun scanProduct(
        imageUri: String,
        barcode: String?,
        category: Category,
        bitmap: android.graphics.Bitmap?
    ): Flow<ScanEvent> = flow {

        // ── Step 1: TFLite pre-scan (offline, instant, free) ──────────────
        // Bitmap is decoded in ScanScreen right after CameraX confirms file
        // is fully written — avoids Samsung libjpeg error 122.
        if (bitmap != null) {
            val tfliteScore = classifier.classify(bitmap)
            Log.d(TAG, "TFLite score: $tfliteScore")
            if (tfliteScore < TFLITE_AUTHENTIC_THRESHOLD || tfliteScore > TFLITE_FAKE_THRESHOLD) {
                val verdict = ProductClassifier.scoreToVerdict(tfliteScore)
                Log.d(TAG, "TFLite high-confidence ($tfliteScore) → $verdict, skipping cloud")
                val result = buildTfliteResult(imageUri, barcode, category, tfliteScore, verdict)
                scanDao.insertScan(result.toEntity())
                emit(ScanEvent.Result(result))
                return@flow
            }
            Log.d(TAG, "TFLite uncertain ($tfliteScore) → proceeding to cloud")
        }

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

    private fun loadBitmap(imageUri: String): android.graphics.Bitmap? {
        return try {
            val uri = Uri.parse(imageUri)
            val filePath = when (uri.scheme) {
                "file" -> uri.path ?: return null
                "content" -> {
                    // For content URIs decode via stream — no rotation fix needed
                    return context.contentResolver
                        .openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
                else -> imageUri
            }

            // Decode with inJustDecodeBounds first to get dimensions, then subsample if huge
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, opts)
            val rawW = opts.outWidth
            val rawH = opts.outHeight
            val sampleSize = if (rawW > 1024 || rawH > 1024) {
                maxOf(rawW / 1024, rawH / 1024)
            } else 1

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }

            // Use ExifInterface to read rotation — avoids libjpeg error 122 on Samsung devices
            val exifDegrees = try {
                val exif = androidx.exifinterface.media.ExifInterface(filePath)
                when (exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                )) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } catch (e: Exception) { 0 }

            // Decode via FileInputStream to avoid strict-mode JPEG parser
            val bitmap = java.io.FileInputStream(filePath).use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            // Apply rotation if needed
            if (exifDegrees != 0) {
                val matrix = android.graphics.Matrix().apply { postRotate(exifDegrees.toFloat()) }
                android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }.also {
                Log.d(TAG, "loadBitmap: decoded ${it.width}x${it.height} rotation=$exifDegrees°")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load bitmap for TFLite: ${e.message}")
            null
        }
    }

    private fun buildTfliteResult(
        imageUri: String,
        barcode: String?,
        category: Category,
        score: Float,
        verdict: Verdict
    ): ScanResult {
        // Authenticity score: (1 - fake_probability) * 100 so 0=fake, 100=authentic
        val authenticityScore = (1f - score) * 100f
        val confidence = if (verdict == Verdict.AUTHENTIC) 1f - score else score
        val product = Product(
            id = UUID.randomUUID().toString(),
            name = category.name.lowercase().replaceFirstChar { it.uppercase() } + " Product",
            barcode = barcode,
            imageUri = imageUri,
            category = category,
            scannedAt = System.currentTimeMillis()
        )
        return ScanResult(
            id = UUID.randomUUID().toString(),
            product = product,
            authenticityScore = authenticityScore,
            verdict = verdict,
            redFlags = emptyList(),
            explanation = "On-device pre-scan result (${(confidence * 100).toInt()}% confidence). " +
                "Cloud verification was skipped.",
            scannedAt = System.currentTimeMillis()
        )
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