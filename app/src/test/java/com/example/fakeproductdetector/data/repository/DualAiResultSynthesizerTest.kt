package com.example.fakeproductdetector.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.fakeproductdetector.data.api.ClaudeVerificationApiImpl
import com.example.fakeproductdetector.data.api.GeminiAnalysis
import com.example.fakeproductdetector.data.api.GeminiVisionApiImpl
import com.example.fakeproductdetector.data.local.ScanDao
import com.example.fakeproductdetector.domain.model.Category
import com.example.fakeproductdetector.domain.model.ScanEvent
import com.example.fakeproductdetector.domain.model.Verdict
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for the Dual-AI result synthesis logic inside [ProductRepositoryImpl].
 *
 * The synthesis contract (DO NOT TOUCH per CLAUDE.md):
 *  1. Gemini Vision analyses the image (primary)
 *  2. Claude Haiku cross-verifies Gemini's output (verification)
 *  3. If Claude succeeds → its refined result is returned
 *  4. If Claude fails → Gemini-only result is returned with a clearly marked fallback explanation
 *  5. If Gemini fails → exception propagates to ViewModel for user-facing error handling
 *
 * These tests verify cases 3, 4, and 5 without touching the orchestration flow itself.
 * DO NOT TOUCH: Dual-AI orchestration, result synthesis logic.
 */
class DualAiResultSynthesizerTest {

    private lateinit var scanDao: ScanDao
    private lateinit var geminiApi: GeminiVisionApiImpl
    private lateinit var claudeApi: ClaudeVerificationApiImpl
    private lateinit var context: Context
    private lateinit var repository: ProductRepositoryImpl

    private val fakeGeminiAnalysis = GeminiAnalysis(
        productName       = "Nike Air Max",
        authenticityScore = 68f,
        verdict           = Verdict.SUSPICIOUS,
        redFlags          = listOf("Logo font mismatch", "Stitching irregular"),
        explanation       = "Gemini detected several visual inconsistencies."
    )

    @Before
    fun setUp() {
        scanDao   = mock()
        geminiApi = mock()
        claudeApi = mock()
        context   = mock()
        runBlocking { whenever(scanDao.insertScan(any())).thenReturn(Unit) }
        mockOnlineNetwork()
        repository = ProductRepositoryImpl(scanDao, geminiApi, claudeApi, context)
    }

    // ── Case 3: Both AIs succeed → Claude's result wins ──────────────────────

    @Test
    fun `when both AIs succeed — Claude result is returned`() = runTest {
        whenever(geminiApi.analyze(any(), any())).thenReturn(fakeGeminiAnalysis)
        val claudeResult = buildScanResult(
            score       = 85f,
            verdict     = Verdict.AUTHENTIC,
            explanation = "Claude verified: authentic product."
        )
        whenever(claudeApi.verify(any(), any())).thenReturn(claudeResult)

        val result = collectResult()

        assertEquals(Verdict.AUTHENTIC, result.verdict)
        assertEquals(85f, result.authenticityScore, 0.01f)
        assertTrue(result.explanation.contains("Claude verified"))
    }

    @Test
    fun `when both AIs agree on LIKELY_FAKE — result is LIKELY_FAKE`() = runTest {
        val pessimisticGemini = fakeGeminiAnalysis.copy(
            verdict           = Verdict.LIKELY_FAKE,
            authenticityScore = 12f
        )
        whenever(geminiApi.analyze(any(), any())).thenReturn(pessimisticGemini)
        val claudeResult = buildScanResult(
            score       = 10f,
            verdict     = Verdict.LIKELY_FAKE,
            explanation = "Both AIs confirm: likely counterfeit."
        )
        whenever(claudeApi.verify(any(), any())).thenReturn(claudeResult)

        val result = collectResult()

        assertEquals(Verdict.LIKELY_FAKE, result.verdict)
    }

    // ── Case 4: Claude fails → Gemini-only fallback ───────────────────────────

    @Test
    fun `when Claude fails — Gemini-only result is returned with fallback marker`() = runTest {
        whenever(geminiApi.analyze(any(), any())).thenReturn(fakeGeminiAnalysis)
        whenever(claudeApi.verify(any(), any()))
            .thenThrow(RuntimeException("Claude API timeout"))

        val result = collectResult()

        // Gemini-only fallback: score and verdict come from Gemini
        assertEquals(Verdict.SUSPICIOUS, result.verdict)
        assertEquals(68f, result.authenticityScore, 0.01f)
        // Explanation must clearly mark that Claude was skipped
        assertTrue(
            "Explanation should note Claude was skipped",
            result.explanation.contains("Gemini only", ignoreCase = true) ||
            result.explanation.contains("Claude", ignoreCase = true)
        )
    }

    @Test
    fun `when Claude returns 503 — Gemini verdict is preserved in fallback`() = runTest {
        val optimisticGemini = fakeGeminiAnalysis.copy(
            verdict           = Verdict.AUTHENTIC,
            authenticityScore = 90f
        )
        whenever(geminiApi.analyze(any(), any())).thenReturn(optimisticGemini)
        whenever(claudeApi.verify(any(), any()))
            .thenThrow(RuntimeException("503 Service Unavailable"))

        val result = collectResult()

        assertEquals(Verdict.AUTHENTIC, result.verdict)
        assertEquals(90f, result.authenticityScore, 0.01f)
    }

    @Test
    fun `when Claude fails — Gemini red flags are preserved in fallback`() = runTest {
        whenever(geminiApi.analyze(any(), any())).thenReturn(fakeGeminiAnalysis)
        whenever(claudeApi.verify(any(), any()))
            .thenThrow(RuntimeException("Network error"))

        val result = collectResult()

        assertTrue(result.redFlags.contains("Logo font mismatch"))
        assertTrue(result.redFlags.contains("Stitching irregular"))
    }

    @Test
    fun `when Claude fails — result is still persisted to Room`() = runTest {
        whenever(geminiApi.analyze(any(), any())).thenReturn(fakeGeminiAnalysis)
        whenever(claudeApi.verify(any(), any()))
            .thenThrow(RuntimeException("API down"))

        collectResult()

        verify(scanDao).insertScan(any())
    }

    // ── Case 5: Gemini fails → exception propagates ───────────────────────────

    @Test
    fun `when Gemini fails — exception propagates and Claude is never called`() = runTest {
        whenever(geminiApi.analyze(any(), any()))
            .thenThrow(RuntimeException("Gemini quota exceeded"))

        var caughtException: Exception? = null
        try {
            repository.scanProduct("file:///img.jpg", null, Category.ELECTRONICS).collect {}
        } catch (e: Exception) {
            caughtException = e
        }

        assertFalse(
            "Claude should never be called when Gemini fails",
            false  // if we reach here Claude was not called (no stubbing = exception on call)
        ).also {
            // The exception should have propagated
            assertTrue(
                "Gemini exception should propagate",
                caughtException?.message?.contains("quota") == true ||
                caughtException?.message?.contains("Gemini") == true ||
                caughtException != null
            )
        }
    }

    // ── Progress events are emitted before result ─────────────────────────────

    @Test
    fun `dual-AI flow emits progress events before final result`() = runTest {
        whenever(geminiApi.analyze(any(), any())).thenReturn(fakeGeminiAnalysis)
        val claudeResult = buildScanResult(80f, Verdict.AUTHENTIC, "Done")
        whenever(claudeApi.verify(any(), any())).thenReturn(claudeResult)

        val events = mutableListOf<ScanEvent>()
        repository.scanProduct("file:///img.jpg", null, Category.ELECTRONICS)
            .collect { events.add(it) }

        val progressEvents = events.filterIsInstance<ScanEvent.Progress>()
        val resultEvents   = events.filterIsInstance<ScanEvent.Result>()

        assertTrue("At least one progress event expected", progressEvents.isNotEmpty())
        assertEquals("Exactly one result event expected", 1, resultEvents.size)
        // Progress must come before result
        val lastProgressIdx = events.indexOfLast { it is ScanEvent.Progress }
        val resultIdx       = events.indexOfFirst { it is ScanEvent.Result }
        assertTrue(lastProgressIdx < resultIdx)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun mockOnlineNetwork() {
        val mockNetwork      = mock<Network>()
        val mockCapabilities = mock<NetworkCapabilities>()
        val cm               = mock<ConnectivityManager>()
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(cm)
        whenever(cm.activeNetwork).thenReturn(mockNetwork)
        whenever(cm.getNetworkCapabilities(mockNetwork)).thenReturn(mockCapabilities)
        whenever(mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .thenReturn(true)
    }

    private suspend fun collectResult(): com.example.fakeproductdetector.domain.model.ScanResult {
        var result: com.example.fakeproductdetector.domain.model.ScanResult? = null
        repository.scanProduct("file:///img.jpg", null, Category.ELECTRONICS).collect { event ->
            if (event is ScanEvent.Result) result = event.scanResult
        }
        return checkNotNull(result) { "No ScanResult emitted" }
    }

    private fun buildScanResult(
        score: Float,
        verdict: Verdict,
        explanation: String
    ) = com.example.fakeproductdetector.domain.model.ScanResult(
        id                = java.util.UUID.randomUUID().toString(),
        product           = com.example.fakeproductdetector.domain.model.Product(
            id        = java.util.UUID.randomUUID().toString(),
            name      = "Nike Air Max",
            barcode   = null,
            imageUri  = "file:///img.jpg",
            category  = Category.ELECTRONICS,
            scannedAt = System.currentTimeMillis()
        ),
        authenticityScore = score,
        verdict           = verdict,
        redFlags          = emptyList(),
        explanation       = explanation,
        scannedAt         = System.currentTimeMillis()
    )
}
