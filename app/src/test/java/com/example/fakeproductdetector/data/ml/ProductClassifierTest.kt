package com.example.fakeproductdetector.data.ml

import android.content.Context
import android.graphics.Bitmap
import com.example.fakeproductdetector.domain.model.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * JVM unit tests for [ProductClassifier].
 *
 * Strategy:
 * - scoreToVerdict tests: pure companion-object function, zero Android dependencies.
 * - Interpreter tests: use the internal testing constructor to inject a mock [Interpreter]
 *   and call [ProductClassifier.classifyBuffer] directly, bypassing Android Bitmap /
 *   TensorImage preprocessing (which requires Robolectric or instrumented tests).
 * - Null-interpreter tests: verify the safe-default path when the model file is absent.
 */
class ProductClassifierTest {

    private lateinit var mockContext: Context
    private lateinit var mockInterpreter: Interpreter

    @Before
    fun setUp() {
        mockContext = mock()
        mockInterpreter = mock()
    }

    // ── scoreToVerdict ────────────────────────────────────────────────────────

    @Test
    fun `scoreToVerdict maps score 0_1 to AUTHENTIC`() {
        assertEquals(Verdict.AUTHENTIC, ProductClassifier.scoreToVerdict(0.1f))
    }

    @Test
    fun `scoreToVerdict maps score 0_5 to SUSPICIOUS`() {
        assertEquals(Verdict.SUSPICIOUS, ProductClassifier.scoreToVerdict(0.5f))
    }

    @Test
    fun `scoreToVerdict maps score 0_9 to LIKELY_FAKE`() {
        assertEquals(Verdict.LIKELY_FAKE, ProductClassifier.scoreToVerdict(0.9f))
    }

    @Test
    fun `scoreToVerdict maps boundary 0_3 to SUSPICIOUS not AUTHENTIC`() {
        // 0.3 is NOT strictly less than 0.3, so it falls into the SUSPICIOUS range.
        assertEquals(Verdict.SUSPICIOUS, ProductClassifier.scoreToVerdict(0.3f))
    }

    @Test
    fun `scoreToVerdict maps boundary 0_7 to LIKELY_FAKE not SUSPICIOUS`() {
        // 0.7 is NOT strictly less than 0.7, so it falls into the LIKELY_FAKE range.
        assertEquals(Verdict.LIKELY_FAKE, ProductClassifier.scoreToVerdict(0.7f))
    }

    // ── Null / unavailable model ──────────────────────────────────────────────

    @Test
    fun `classify returns NEUTRAL_SCORE when model is not loaded`() {
        // Internal constructor with null interpreter simulates a missing/placeholder model.
        val classifier = ProductClassifier(mockContext, null)
        val mockBitmap = mock<Bitmap>() // bitmap is never accessed when interpreter is null
        assertEquals(
            "Expected NEUTRAL_SCORE when TFLite model is absent",
            ProductClassifier.NEUTRAL_SCORE,
            classifier.classify(mockBitmap),
            0.0f
        )
    }

    @Test
    fun `null model returns score that maps to SUSPICIOUS (safe default)`() {
        // NEUTRAL_SCORE (0.5) sits in the 0.3–0.7 SUSPICIOUS range — safe middle-ground.
        assertEquals(
            Verdict.SUSPICIOUS,
            ProductClassifier.scoreToVerdict(ProductClassifier.NEUTRAL_SCORE)
        )
    }

    // ── Tensor shape ──────────────────────────────────────────────────────────

    @Test
    fun `input tensor shape is 1 x 224 x 224 x 3 float32 (602112 bytes)`() {
        // Verify the compile-time constant matches the expected shape:
        // batch=1, height=224, width=224, channels=3, dtype=float32 (4 bytes)
        val expected = 1 * 224 * 224 * 3 * 4
        assertEquals(
            "INPUT_BUFFER_BYTES must match [1, 224, 224, 3] float32 tensor",
            expected,
            ProductClassifier.INPUT_BUFFER_BYTES
        )
    }

    // ── Mock interpreter — inference path ─────────────────────────────────────

    @Test
    fun `classifyBuffer returns score written by interpreter`() {
        // Arrange: mock interpreter writes 0.85f to output[0][0]
        doAnswer { invocation ->
            val output = invocation.getArgument<Array<FloatArray>>(1)
            output[0][0] = 0.85f
            null
        }.whenever(mockInterpreter).run(any(), any<Array<FloatArray>>())

        val classifier = ProductClassifier(mockContext, mockInterpreter)
        val input = allocateInputBuffer()

        // Act
        val score = classifier.classifyBuffer(input)

        // Assert
        assertEquals(0.85f, score, 0.0001f)
        verify(mockInterpreter).run(any(), any<Array<FloatArray>>())
    }

    @Test
    fun `confidence score returned by classifier is always between 0_0 and 1_0`() {
        // The model uses a sigmoid output, so values are in [0, 1].
        // We test several representative scores returned by the mock.
        val testScores = listOf(0.0f, 0.02f, 0.3f, 0.5f, 0.75f, 0.95f, 1.0f)

        testScores.forEach { expected ->
            doAnswer { invocation ->
                val output = invocation.getArgument<Array<FloatArray>>(1)
                output[0][0] = expected
                null
            }.whenever(mockInterpreter).run(any(), any<Array<FloatArray>>())

            val classifier = ProductClassifier(mockContext, mockInterpreter)
            val score = classifier.classifyBuffer(allocateInputBuffer())

            assertTrue(
                "Score $score is outside [0.0, 1.0] for input $expected",
                score in 0.0f..1.0f
            )
        }
    }

    @Test
    fun `low interpreter score 0_02 maps to AUTHENTIC verdict`() {
        doAnswer { invocation ->
            (invocation.getArgument<Array<FloatArray>>(1))[0][0] = 0.02f
            null
        }.whenever(mockInterpreter).run(any(), any<Array<FloatArray>>())

        val classifier = ProductClassifier(mockContext, mockInterpreter)
        val score = classifier.classifyBuffer(allocateInputBuffer())

        assertEquals(Verdict.AUTHENTIC, ProductClassifier.scoreToVerdict(score))
    }

    @Test
    fun `high interpreter score 0_95 maps to LIKELY_FAKE verdict`() {
        doAnswer { invocation ->
            (invocation.getArgument<Array<FloatArray>>(1))[0][0] = 0.95f
            null
        }.whenever(mockInterpreter).run(any(), any<Array<FloatArray>>())

        val classifier = ProductClassifier(mockContext, mockInterpreter)
        val score = classifier.classifyBuffer(allocateInputBuffer())

        assertEquals(Verdict.LIKELY_FAKE, ProductClassifier.scoreToVerdict(score))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Allocates a correctly-sized float32 ByteBuffer for the [1, 224, 224, 3] input tensor. */
    private fun allocateInputBuffer(): ByteBuffer =
        ByteBuffer.allocateDirect(ProductClassifier.INPUT_BUFFER_BYTES)
            .order(ByteOrder.nativeOrder())
}