package com.example.fakeproductdetector.ui.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import com.example.fakeproductdetector.domain.model.Category
import com.example.fakeproductdetector.domain.model.Product
import com.example.fakeproductdetector.domain.model.ScanResult
import com.example.fakeproductdetector.domain.model.Verdict
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class TTSManagerTest {

    private lateinit var mockTts: TextToSpeech
    private lateinit var manager: TTSManager
    private lateinit var initListener: TextToSpeech.OnInitListener

    private val mockContext: Context = mock()

    @Before
    fun setUp() {
        mockTts = mock()
        manager = TTSManager(mockContext) { _, listener ->
            initListener = listener
            mockTts
        }
    }

    // Triggers the async init callback synchronously in tests
    private fun initTts() = initListener.onInit(TextToSpeech.SUCCESS)

    // ── speak() ───────────────────────────────────────────────────────────────

    @Test
    fun `speak does nothing when TTS is not initialized`() {
        manager.speak("Hello")

        verify(mockTts, never()).speak(any(), any(), any(), any())
    }

    @Test
    fun `speak does nothing when muted`() {
        initTts()
        manager.isMuted = true

        manager.speak("Hello")

        verify(mockTts, never()).speak(any(), any(), any(), any())
    }

    // ── stop() ────────────────────────────────────────────────────────────────

    @Test
    fun `stop calls tts stop`() {
        manager.stop()

        verify(mockTts).stop()
    }

    // ── isReady() ─────────────────────────────────────────────────────────────

    @Test
    fun `isReady returns false before init`() {
        assertFalse(manager.isReady())
    }

    @Test
    fun `isReady returns true after successful init`() {
        // mockTts setLanguage returns 0 by default, which is neither
        // LANG_MISSING_DATA (-1) nor LANG_NOT_SUPPORTED (-2), so ready = true
        initTts()

        assertTrue(manager.isReady())
    }

    // ── shutdown() ────────────────────────────────────────────────────────────

    @Test
    fun `shutdown calls tts shutdown`() {
        manager.shutdown()

        verify(mockTts).shutdown()
    }

    // ── speakResult() — speech string format ──────────────────────────────────

    @Test
    fun `speakResult builds correct string for AUTHENTIC verdict with no red flags`() {
        initTts()

        manager.speakResult(
            makeScanResult(name = "Premium Watch", score = 90f, verdict = Verdict.AUTHENTIC)
        )

        verify(mockTts).speak(
            "Product: Premium Watch. Score: 90 out of 100. Verdict: Authentic.",
            TextToSpeech.QUEUE_FLUSH,
            null,
            null
        )
    }

    @Test
    fun `speakResult includes red flags warning when redFlags list is not empty`() {
        initTts()

        manager.speakResult(
            makeScanResult(
                name = "Headphones",
                score = 45f,
                verdict = Verdict.SUSPICIOUS,
                redFlags = listOf("missing serial number", "poor packaging")
            )
        )

        verify(mockTts).speak(
            "Product: Headphones. Score: 45 out of 100. Verdict: Suspicious." +
                    " Warning: missing serial number, poor packaging.",
            TextToSpeech.QUEUE_FLUSH,
            null,
            null
        )
    }

    @Test
    fun `speakResult uses correct format for LIKELY_FAKE verdict`() {
        initTts()

        manager.speakResult(
            makeScanResult(name = "Counterfeit Bag", score = 12f, verdict = Verdict.LIKELY_FAKE)
        )

        verify(mockTts).speak(
            "Product: Counterfeit Bag. Score: 12 out of 100. Verdict: Likely Fake.",
            TextToSpeech.QUEUE_FLUSH,
            null,
            null
        )
    }

    // ── speakWhenReady() ──────────────────────────────────────────────────────

    @Test
    fun `speakWhenReady speaks immediately when TTS is already ready`() = runTest {
        initTts()

        manager.speakWhenReady("Hello ready")

        verify(mockTts).speak("Hello ready", TextToSpeech.QUEUE_FLUSH, null, null)
    }

    @Test
    fun `speakWhenReady does not speak if TTS never becomes ready`() = runTest {
        // onInit is never called — ready stays false
        manager.speakWhenReady("Hello never")

        verify(mockTts, never()).speak(any(), any(), any(), any())
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeScanResult(
        name: String = "Test Product",
        score: Float = 85f,
        verdict: Verdict = Verdict.AUTHENTIC,
        redFlags: List<String> = emptyList()
    ) = ScanResult(
        id = "test-id",
        product = Product(
            id = "p1",
            name = name,
            barcode = null,
            imageUri = "",
            category = Category.OTHER,
            scannedAt = 0L
        ),
        authenticityScore = score,
        verdict = verdict,
        redFlags = redFlags,
        explanation = "Test explanation",
        scannedAt = 0L
    )
}