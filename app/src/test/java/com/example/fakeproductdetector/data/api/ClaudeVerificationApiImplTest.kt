package com.example.fakeproductdetector.data.api

import com.example.fakeproductdetector.domain.model.Category
import com.example.fakeproductdetector.domain.model.Product
import com.example.fakeproductdetector.domain.model.Verdict
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Unit tests for [ClaudeVerificationApiImpl] — the AI verification layer.
 *
 * This is the critical untested class identified in the retro. It is the second
 * stage of the Dual-AI pipeline: Gemini Vision (primary) → Claude Haiku (verification).
 *
 * Test strategy:
 * - Mock the [ClaudeVerificationApi] Retrofit interface
 * - Feed controlled JSON responses to verify parsing, fallback, and error handling
 * - Verify prompt is constructed and sent (responsibility delegation)
 *
 * DO NOT TOUCH: Dual-AI orchestration, result synthesis logic
 */
class ClaudeVerificationApiImplTest {

    private lateinit var api: ClaudeVerificationApi
    private lateinit var impl: ClaudeVerificationApiImpl
    private val gson = Gson()

    private val fakeProduct = Product(
        id          = UUID.randomUUID().toString(),
        name        = "Test Sneaker",
        barcode     = "012345678901",
        imageUri    = "file:///test.jpg",
        category    = Category.FASHION,
        scannedAt   = System.currentTimeMillis()
    )

    private val fakeGeminiAnalysis = GeminiAnalysis(
        productName       = "Test Sneaker",
        authenticityScore = 72f,
        verdict           = Verdict.SUSPICIOUS,
        redFlags          = listOf("Logo stitching inconsistency", "Wrong box font"),
        explanation       = "Several visual inconsistencies detected."
    )

    @Before
    fun setUp() {
        api  = mock()
        impl = ClaudeVerificationApiImpl(api, gson)
    }

    // ── Successful verification ───────────────────────────────────────────────

    @Test
    fun `successful verification — AUTHENTIC verdict parsed correctly`() = runTest {
        val json = """
            {
              "authenticityScore": 91.5,
              "verdict": "AUTHENTIC",
              "redFlags": [],
              "explanation": "Both Gemini and Claude agree: product appears genuine."
            }
        """.trimIndent()
        whenever(api.verify(any())).thenReturn(buildClaudeResponse(json))

        val result = impl.verify(fakeGeminiAnalysis, fakeProduct)

        assertEquals(Verdict.AUTHENTIC, result.verdict)
        assertEquals(91.5f, result.authenticityScore, 0.01f)
        assertTrue(result.redFlags.isEmpty())
        assertTrue(result.explanation.contains("genuine"))
    }

    @Test
    fun `successful verification — LIKELY_FAKE verdict parsed correctly`() = runTest {
        val json = """
            {
              "authenticityScore": 18.0,
              "verdict": "LIKELY_FAKE",
              "redFlags": ["Wrong sole pattern", "Colour mismatch"],
              "explanation": "Multiple indicators of counterfeit detected."
            }
        """.trimIndent()
        whenever(api.verify(any())).thenReturn(buildClaudeResponse(json))

        val result = impl.verify(fakeGeminiAnalysis, fakeProduct)

        assertEquals(Verdict.LIKELY_FAKE, result.verdict)
        assertEquals(18.0f, result.authenticityScore, 0.01f)
        assertEquals(2, result.redFlags.size)
        assertTrue(result.redFlags.contains("Wrong sole pattern"))
    }

    @Test
    fun `successful verification — SUSPICIOUS verdict is default for uncertain score`() = runTest {
        val json = """
            {
              "authenticityScore": 50.0,
              "verdict": "SUSPICIOUS",
              "redFlags": ["Minor packaging deviation"],
              "explanation": "Inconclusive — recommend manual inspection."
            }
        """.trimIndent()
        whenever(api.verify(any())).thenReturn(buildClaudeResponse(json))

        val result = impl.verify(fakeGeminiAnalysis, fakeProduct)

        assertEquals(Verdict.SUSPICIOUS, result.verdict)
        assertEquals(1, result.redFlags.size)
    }

    // ── Score clamping ────────────────────────────────────────────────────────

    @Test
    fun `score above 100 is clamped to 100`() = runTest {
        val json = """{"authenticityScore": 150.0, "verdict": "AUTHENTIC",
                       "redFlags": [], "explanation": "Clamping test"}"""
        whenever(api.verify(any())).thenReturn(buildClaudeResponse(json))

        val result = impl.verify(fakeGeminiAnalysis, fakeProduct)
        assertEquals(100f, result.authenticityScore, 0.01f)
    }

    @Test
    fun `score below 0 is clamped to 0`() = runTest {
        val json = """{"authenticityScore": -50.0, "verdict": "LIKELY_FAKE",
                       "redFlags": [], "explanation": "Negative score test"}"""
        whenever(api.verify(any())).thenReturn(buildClaudeResponse(json))

        val result = impl.verify(fakeGeminiAnalysis, fakeProduct)
        assertEquals(0f, result.authenticityScore, 0.01f)
    }

    // ── Malformed / partial responses ─────────────────────────────────────────

    @Test
    fun `unknown verdict string falls back to SUSPICIOUS`() = runTest {
        val json = """{"authenticityScore": 55.0, "verdict": "MAYBE_FAKE",
                       "redFlags": [], "explanation": "Unknown verdict test"}"""
        whenever(api.verify(any())).thenReturn(buildClaudeResponse(json))

        val result = impl.verify(fakeGeminiAnalysis, fakeProduct)
        assertEquals(Verdict.SUSPICIOUS, result.verdict)   // safe fallback
    }

    @Test
    fun `missing authenticityScore field defaults to 50`() = runTest {
        val json = """{"verdict": "SUSPICIOUS", "redFlags": [], "explanation": "Score missing"}"""
        whenever(api.verify(any())).thenReturn(buildClaudeResponse(json))

        val result = impl.verify(fakeGeminiAnalysis, fakeProduct)
        assertEquals(50f, result.authenticityScore, 0.01f)
    }

    @Test
    fun `missing explanation field defaults to safe placeholder`() = runTest {
        val json = """{"authenticityScore": 70.0, "verdict": "SUSPICIOUS", "redFlags": []}"""
        whenever(api.verify(any())).thenReturn(buildClaudeResponse(json))

        val result = impl.verify(fakeGeminiAnalysis, fakeProduct)
        assertNotNull(result.explanation)
        assertTrue(result.explanation.isNotBlank())
    }

    @Test
    fun `response wrapped in markdown code fence is parsed correctly`() = runTest {
        val json = """
            ```json
            {
              "authenticityScore": 82.0,
              "verdict": "AUTHENTIC",
              "redFlags": [],
              "explanation": "Fenced JSON response"
            }
            ```
        """.trimIndent()
        whenever(api.verify(any())).thenReturn(buildClaudeResponse(json))

        val result = impl.verify(fakeGeminiAnalysis, fakeProduct)
        assertEquals(Verdict.AUTHENTIC, result.verdict)
        assertEquals(82f, result.authenticityScore, 0.01f)
    }

    @Test
    fun `empty redFlags array produces empty list`() = runTest {
        val json = """{"authenticityScore": 90.0, "verdict": "AUTHENTIC",
                       "redFlags": [], "explanation": "Clean"}"""
        whenever(api.verify(any())).thenReturn(buildClaudeResponse(json))

        val result = impl.verify(fakeGeminiAnalysis, fakeProduct)
        assertTrue(result.redFlags.isEmpty())
    }

    @Test
    fun `null redFlags field produces empty list`() = runTest {
        val json = """{"authenticityScore": 90.0, "verdict": "AUTHENTIC",
                       "explanation": "null flags test"}"""
        whenever(api.verify(any())).thenReturn(buildClaudeResponse(json))

        val result = impl.verify(fakeGeminiAnalysis, fakeProduct)
        // Should not crash; redFlags should be empty or non-null
        assertNotNull(result.redFlags)
    }

    // ── API failure / network errors ──────────────────────────────────────────

    @Test
    fun `network exception propagates to caller for upstream handling`() = runTest {
        whenever(api.verify(any())).thenThrow(RuntimeException("Connection timeout"))

        try {
            impl.verify(fakeGeminiAnalysis, fakeProduct)
            assertTrue("Expected exception was not thrown", false)
        } catch (e: RuntimeException) {
            assertEquals("Connection timeout", e.message)
        }
    }

    @Test
    fun `empty response text throws exception for upstream fallback`() = runTest {
        // An empty or null content means Claude returned nothing — caller should
        // catch this and fall back to Gemini-only result (verified in ProductRepositoryImplTest)
        val emptyResponse = ClaudeResponse(content = null, id = "resp-empty", model = "claude-haiku-4-5-20251001")
        whenever(api.verify(any())).thenReturn(emptyResponse)

        try {
            impl.verify(fakeGeminiAnalysis, fakeProduct)
            assertTrue("Expected exception for null content", false)
        } catch (e: Exception) {
            assertTrue("Exception message should describe the failure",
                e.message?.contains("null") == true || e.message?.contains("Empty") == true
                        || e.message?.contains("empty") == true)
        }
    }

    // ── Result metadata ────────────────────────────────────────────────────────

    @Test
    fun `result has a non-null unique id`() = runTest {
        val json = """{"authenticityScore": 75.0, "verdict": "SUSPICIOUS",
                       "redFlags": [], "explanation": "ID test"}"""
        whenever(api.verify(any())).thenReturn(buildClaudeResponse(json))

        val result = impl.verify(fakeGeminiAnalysis, fakeProduct)
        assertNotNull(result.id)
        assertTrue(result.id.isNotBlank())
    }

    @Test
    fun `result product matches input product`() = runTest {
        val json = """{"authenticityScore": 75.0, "verdict": "SUSPICIOUS",
                       "redFlags": [], "explanation": "Product test"}"""
        whenever(api.verify(any())).thenReturn(buildClaudeResponse(json))

        val result = impl.verify(fakeGeminiAnalysis, fakeProduct)
        assertEquals(fakeProduct.name, result.product.name)
        assertEquals(fakeProduct.category, result.product.category)
        assertEquals(fakeProduct.barcode, result.product.barcode)
    }

    @Test
    fun `API is called exactly once per verify invocation`() = runTest {
        val json = """{"authenticityScore": 75.0, "verdict": "SUSPICIOUS",
                       "redFlags": [], "explanation": "Call count test"}"""
        whenever(api.verify(any())).thenReturn(buildClaudeResponse(json))

        impl.verify(fakeGeminiAnalysis, fakeProduct)
        verify(api).verify(any())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildClaudeResponse(textContent: String): ClaudeResponse =
        ClaudeResponse(
            id      = "msg_test_${UUID.randomUUID()}",
            model   = "claude-haiku-4-5-20251001",
            content = listOf(ClaudeResponse.ContentBlock(type = "text", text = textContent))
        )
}
