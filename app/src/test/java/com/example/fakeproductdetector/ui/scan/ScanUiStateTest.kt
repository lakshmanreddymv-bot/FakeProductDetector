package com.example.fakeproductdetector.ui.scan

import com.example.fakeproductdetector.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ScanUiStateTest {

    @Test
    fun `Idle state is default`() {
        val state: ScanUiState = ScanUiState.Idle
        assertNotNull(state)
    }

    @Test
    fun `Loading state is distinct from Idle`() {
        assertNotEquals(ScanUiState.Idle, ScanUiState.Loading())
    }

    @Test
    fun `Loading state uses default message when none provided`() {
        val state = ScanUiState.Loading()
        assertEquals("Analyzing product...", state.message)
    }

    @Test
    fun `Loading state holds custom message`() {
        val state = ScanUiState.Loading("Verifying with Claude...")
        assertEquals("Verifying with Claude...", state.message)
    }

    @Test
    fun `Error state holds message`() {
        val state = ScanUiState.Error("Network timeout")
        assertEquals("Network timeout", state.message)
    }

    @Test
    fun `Error state with empty message is valid`() {
        val state = ScanUiState.Error("")
        assertEquals("", state.message)
    }

    @Test
    fun `RateLimited state holds countdown and quota flags`() {
        val state = ScanUiState.RateLimited(
            secondsRemaining = 60,
            isQuotaExhausted = false,
            title = "Slow down!",
            subtitle = "Too many requests"
        )
        assertEquals(60, state.secondsRemaining)
        assertFalse(state.isQuotaExhausted)
        assertEquals("Slow down!", state.title)
    }

    @Test
    fun `RateLimited with daily exhaustion shows quota flag`() {
        val state = ScanUiState.RateLimited(
            secondsRemaining = 300,
            isQuotaExhausted = true,
            title = "Daily limit reached",
            subtitle = "Come back tomorrow"
        )
        assertTrue(state.isQuotaExhausted)
        assertEquals(300, state.secondsRemaining)
    }

    @Test
    fun `Success state holds ScanResult`() {
        val product = Product(
            id = "p1",
            name = "Test Product",
            barcode = "111222333",
            imageUri = "content://test",
            category = Category.ELECTRONICS,
            scannedAt = 0L
        )
        val scanResult = ScanResult(
            id = "r1",
            product = product,
            authenticityScore = 88.5f,
            verdict = Verdict.AUTHENTIC,
            redFlags = listOf("Minor packaging inconsistency"),
            explanation = "Mostly authentic with minor concerns",
            scannedAt = 0L
        )
        val state = ScanUiState.Success(scanResult)
        assertEquals(scanResult, state.result)
        assertEquals(Verdict.AUTHENTIC, state.result.verdict)
        assertEquals(88.5f, state.result.authenticityScore)
    }

    @Test
    fun `All states are mutually distinct`() {
        val idle = ScanUiState.Idle
        val loading = ScanUiState.Loading()
        val error = ScanUiState.Error("err")
        val rateLimited = ScanUiState.RateLimited(30, false, "title", "sub")

        assertNotEquals(idle, loading)
        assertNotEquals(idle, error)
        assertNotEquals(loading, error)
        assertNotEquals(loading, rateLimited)
    }

    @Test
    fun `When expression covers all UI states`() {
        val states: List<ScanUiState> = listOf(
            ScanUiState.Idle,
            ScanUiState.Loading(),
            ScanUiState.Error("test"),
            ScanUiState.RateLimited(60, false, "T", "S")
        )
        states.forEach { state ->
            val label = when (state) {
                is ScanUiState.Idle -> "idle"
                is ScanUiState.Loading -> "loading"
                is ScanUiState.Error -> "error"
                is ScanUiState.RateLimited -> "rate_limited"
                is ScanUiState.Success -> "success"
            }
            assertNotNull(label)
        }
    }
}