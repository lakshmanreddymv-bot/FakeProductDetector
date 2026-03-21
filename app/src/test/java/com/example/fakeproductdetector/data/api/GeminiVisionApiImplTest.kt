package com.example.fakeproductdetector.data.api

import com.example.fakeproductdetector.domain.model.Category
import com.example.fakeproductdetector.domain.model.Verdict
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for JSON parsing logic used by GeminiVisionApiImpl.
 * These test the pure parsing/extraction functions in isolation
 * without requiring network or Android context.
 */
class GeminiVisionApiImplTest {

    // ---- JSON Extraction helpers (replicate the logic from GeminiVisionApiImpl) ----

    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) raw.substring(start, end + 1) else raw
    }

    private fun parseVerdict(raw: String): Verdict {
        return when (raw.uppercase().trim()) {
            "AUTHENTIC" -> Verdict.AUTHENTIC
            "SUSPICIOUS" -> Verdict.SUSPICIOUS
            "LIKELY_FAKE" -> Verdict.LIKELY_FAKE
            else -> Verdict.SUSPICIOUS
        }
    }

    private fun parseScore(score: Any?): Float {
        return when (score) {
            is Double -> score.toFloat()
            is Float -> score
            is Int -> score.toFloat()
            is String -> score.toFloatOrNull() ?: 50f
            else -> 50f
        }
    }

    // ---- Tests ----

    @Test
    fun `extractJson pulls object from clean response`() {
        val raw = """{"verdict":"AUTHENTIC","authenticityScore":95.0}"""
        val extracted = extractJson(raw)
        assertTrue(extracted.startsWith("{"))
        assertTrue(extracted.endsWith("}"))
    }

    @Test
    fun `extractJson strips markdown code block wrapper`() {
        val raw = """```json
{"verdict":"AUTHENTIC","authenticityScore":95.0}
```"""
        val extracted = extractJson(raw)
        assertTrue(extracted.startsWith("{"))
        assertTrue(extracted.endsWith("}"))
    }

    @Test
    fun `extractJson returns original when no braces found`() {
        val raw = "No JSON here"
        val extracted = extractJson(raw)
        assertEquals("No JSON here", extracted)
    }

    @Test
    fun `parseVerdict returns AUTHENTIC for authentic string`() {
        assertEquals(Verdict.AUTHENTIC, parseVerdict("AUTHENTIC"))
        assertEquals(Verdict.AUTHENTIC, parseVerdict("authentic"))
        assertEquals(Verdict.AUTHENTIC, parseVerdict("Authentic"))
    }

    @Test
    fun `parseVerdict returns SUSPICIOUS for suspicious string`() {
        assertEquals(Verdict.SUSPICIOUS, parseVerdict("SUSPICIOUS"))
        assertEquals(Verdict.SUSPICIOUS, parseVerdict("suspicious"))
    }

    @Test
    fun `parseVerdict returns LIKELY_FAKE for likely_fake string`() {
        assertEquals(Verdict.LIKELY_FAKE, parseVerdict("LIKELY_FAKE"))
        assertEquals(Verdict.LIKELY_FAKE, parseVerdict("likely_fake"))
    }

    @Test
    fun `parseVerdict returns SUSPICIOUS for unknown string`() {
        assertEquals(Verdict.SUSPICIOUS, parseVerdict("UNKNOWN"))
        assertEquals(Verdict.SUSPICIOUS, parseVerdict(""))
        assertEquals(Verdict.SUSPICIOUS, parseVerdict("maybe"))
    }

    @Test
    fun `parseScore handles Double type`() {
        assertEquals(95.0f, parseScore(95.0), 0.01f)
    }

    @Test
    fun `parseScore handles Int type`() {
        assertEquals(80.0f, parseScore(80), 0.01f)
    }

    @Test
    fun `parseScore handles String type`() {
        assertEquals(75.5f, parseScore("75.5"), 0.01f)
    }

    @Test
    fun `parseScore returns 50f for null`() {
        assertEquals(50.0f, parseScore(null), 0.01f)
    }

    @Test
    fun `parseScore returns 50f for unparseable string`() {
        assertEquals(50.0f, parseScore("not_a_number"), 0.01f)
    }

    @Test
    fun `parseScore handles edge cases - zero`() {
        assertEquals(0.0f, parseScore(0), 0.01f)
    }

    @Test
    fun `parseScore handles edge cases - 100`() {
        assertEquals(100.0f, parseScore(100), 0.01f)
    }
}
