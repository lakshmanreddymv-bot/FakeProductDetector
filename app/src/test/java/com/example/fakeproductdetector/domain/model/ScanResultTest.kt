package com.example.fakeproductdetector.domain.model

import org.junit.Assert.*
import org.junit.Test

class ScanResultTest {

    @Test
    fun `verdict AUTHENTIC returns correct display name`() {
        assertEquals("AUTHENTIC", Verdict.AUTHENTIC.name)
    }

    @Test
    fun `verdict SUSPICIOUS returns correct display name`() {
        assertEquals("SUSPICIOUS", Verdict.SUSPICIOUS.name)
    }

    @Test
    fun `verdict LIKELY_FAKE returns correct display name`() {
        assertEquals("LIKELY_FAKE", Verdict.LIKELY_FAKE.name)
    }

    @Test
    fun `all three verdicts are distinct`() {
        val verdicts = Verdict.values()
        assertEquals(3, verdicts.size)
        assertEquals(3, verdicts.toSet().size)
    }

    @Test
    fun `category values cover all expected types`() {
        val categories = Category.values()
        assertTrue(categories.any { it == Category.MEDICINE })
        assertTrue(categories.any { it == Category.ELECTRONICS })
        assertTrue(categories.any { it == Category.LUXURY })
        assertTrue(categories.any { it == Category.FOOD })
        assertTrue(categories.any { it == Category.OTHER })
    }

    @Test
    fun `ScanResult holds correct data`() {
        val product = Product(
            id = "1",
            name = "Test Product",
            barcode = "123456789",
            imageUri = "content://test",
            category = Category.MEDICINE,
            scannedAt = 1234567890L
        )
        val result = ScanResult(
            id = "r1",
            product = product,
            authenticityScore = 95.0f,
            verdict = Verdict.AUTHENTIC,
            redFlags = emptyList(),
            explanation = "Looks good",
            scannedAt = 1234567890L
        )
        assertEquals("r1", result.id)
        assertEquals(95.0f, result.authenticityScore)
        assertEquals(Verdict.AUTHENTIC, result.verdict)
        assertTrue(result.redFlags.isEmpty())
        assertEquals("Looks good", result.explanation)
    }

    @Test
    fun `ScanResult with red flags stores them correctly`() {
        val product = Product(
            id = "2",
            name = "Fake Watch",
            barcode = "987654321",
            imageUri = "content://fake",
            category = Category.LUXURY,
            scannedAt = 1234567890L
        )
        val flags = listOf("Blurry logo", "Incorrect font", "Missing hologram")
        val result = ScanResult(
            id = "r2",
            product = product,
            authenticityScore = 15.0f,
            verdict = Verdict.LIKELY_FAKE,
            redFlags = flags,
            explanation = "Multiple counterfeiting signs",
            scannedAt = 1234567890L
        )
        assertEquals(3, result.redFlags.size)
        assertTrue(result.redFlags.contains("Blurry logo"))
        assertEquals(Verdict.LIKELY_FAKE, result.verdict)
    }

    @Test
    fun `Product stores barcode correctly`() {
        val product = Product(
            id = "3",
            name = "Tylenol",
            barcode = "300450122377",
            imageUri = "content://tylenol",
            category = Category.MEDICINE,
            scannedAt = System.currentTimeMillis()
        )
        assertEquals("300450122377", product.barcode)
        assertEquals(Category.MEDICINE, product.category)
    }
}
