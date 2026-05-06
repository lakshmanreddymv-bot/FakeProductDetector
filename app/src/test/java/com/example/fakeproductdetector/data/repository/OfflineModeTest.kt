package com.example.fakeproductdetector.data.repository

import android.content.Context
import android.net.ConnectivityManager
import com.example.fakeproductdetector.data.api.ClaudeVerificationApiImpl
import com.example.fakeproductdetector.data.api.GeminiVisionApiImpl
import com.example.fakeproductdetector.data.local.ScanDao
import com.example.fakeproductdetector.domain.model.Category
import com.example.fakeproductdetector.domain.model.Verdict
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Offline-mode tests for [ProductRepositoryImpl].
 *
 * Verifies the dual-AI pipeline behaves correctly when the device has no internet:
 *  - Neither Gemini nor Claude is called
 *  - A SUSPICIOUS offline-placeholder result is returned immediately
 *  - The result is clearly marked as limited (offline explanation text)
 *  - The offline result is persisted to Room for history display
 *
 * testOptions.isReturnDefaultValues = true handles ConnectivityManager stubs on JVM.
 * DO NOT TOUCH: Dual-AI orchestration, result synthesis logic.
 */
class OfflineModeTest {

    private lateinit var scanDao: ScanDao
    private lateinit var geminiApi: GeminiVisionApiImpl
    private lateinit var claudeApi: ClaudeVerificationApiImpl
    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var repository: ProductRepositoryImpl

    @Before
    fun setUp() {
        scanDao             = mock()
        geminiApi           = mock()
        claudeApi           = mock()
        context             = mock()
        connectivityManager = mock()

        // Simulate no active network → isNetworkAvailable() returns false
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE))
            .thenReturn(connectivityManager)
        whenever(connectivityManager.activeNetwork).thenReturn(null)

        repository = ProductRepositoryImpl(scanDao, geminiApi, claudeApi, context)
    }

    // ── Core offline behaviour ────────────────────────────────────────────────

    @Test
    fun `offline scan returns SUSPICIOUS result without calling Gemini`() = runTest {
        whenever(scanDao.insertScan(any())).thenReturn(Unit)

        val flow = repository.scanProduct("file:///test.jpg", null, Category.ELECTRONICS)
        val events = mutableListOf<com.example.fakeproductdetector.domain.model.ScanEvent>()
        flow.collect { events.add(it) }

        verify(geminiApi, never()).analyze(any(), any())
        assertTrue(events.isNotEmpty())
    }

    @Test
    fun `offline scan returns SUSPICIOUS verdict`() = runTest {
        whenever(scanDao.insertScan(any())).thenReturn(Unit)

        val result = collectResult(Category.ELECTRONICS)

        assertEquals(Verdict.SUSPICIOUS, result.verdict)
    }

    @Test
    fun `offline result explanation tells user to connect internet`() = runTest {
        whenever(scanDao.insertScan(any())).thenReturn(Unit)

        val result = collectResult(Category.FASHION)

        assertTrue(
            "Offline explanation should mention internet connection",
            result.explanation.contains("internet", ignoreCase = true) ||
            result.explanation.contains("connect", ignoreCase = true) ||
            result.explanation.contains("offline", ignoreCase = true)
        )
    }

    @Test
    fun `offline result is never null`() = runTest {
        whenever(scanDao.insertScan(any())).thenReturn(Unit)

        val result = collectResult(Category.FOOTWEAR)

        assertNotNull(result)
        assertNotNull(result.id)
        assertNotNull(result.product)
    }

    @Test
    fun `offline result has authenticity score 50 — neutral uncertainty`() = runTest {
        whenever(scanDao.insertScan(any())).thenReturn(Unit)

        val result = collectResult(Category.ACCESSORIES)

        assertEquals(50f, result.authenticityScore, 0.01f)
    }

    @Test
    fun `offline result preserves barcode passed to scan`() = runTest {
        whenever(scanDao.insertScan(any())).thenReturn(Unit)

        val flow = repository.scanProduct("file:///test.jpg", "0123456789012", Category.ELECTRONICS)
        var scanResult: com.example.fakeproductdetector.domain.model.ScanResult? = null
        flow.collect { event ->
            if (event is com.example.fakeproductdetector.domain.model.ScanEvent.Result) {
                scanResult = event.result
            }
        }

        assertEquals("0123456789012", scanResult?.product?.barcode)
    }

    // ── Claude is never called when offline ───────────────────────────────────

    @Test
    fun `offline scan never calls Claude verification API`() = runTest {
        whenever(scanDao.insertScan(any())).thenReturn(Unit)

        repository.scanProduct("file:///test.jpg", null, Category.ELECTRONICS).collect {}

        verify(claudeApi, never()).verify(any(), any())
    }

    // ── Offline result is persisted ───────────────────────────────────────────

    @Test
    fun `offline result is saved to Room for history display`() = runTest {
        whenever(scanDao.insertScan(any())).thenReturn(Unit)

        repository.scanProduct("file:///test.jpg", null, Category.ELECTRONICS).collect {}

        verify(scanDao).insertScan(any())
    }

    // ── Scan history remains accessible offline ───────────────────────────────

    @Test
    fun `getScanHistory works offline — reads from local Room only`() = runTest {
        whenever(scanDao.getAllScans()).thenReturn(flowOf(emptyList()))

        val history = repository.getScanHistory().first()

        assertTrue(history.isEmpty())
        verify(scanDao).getAllScans()
        verify(geminiApi, never()).analyze(any(), any())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun collectResult(
        category: Category
    ): com.example.fakeproductdetector.domain.model.ScanResult {
        var result: com.example.fakeproductdetector.domain.model.ScanResult? = null
        repository.scanProduct("file:///img.jpg", null, category).collect { event ->
            if (event is com.example.fakeproductdetector.domain.model.ScanEvent.Result) {
                result = event.result
            }
        }
        return checkNotNull(result) { "No ScanResult emitted from offline flow" }
    }
}
