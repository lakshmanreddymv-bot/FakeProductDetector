package com.example.fakeproductdetector.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.fakeproductdetector.domain.model.Verdict
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

interface ProductClassifierInterface {
    /**
     * Runs inference on [bitmap] and returns a confidence score in [0.0, 1.0].
     * Score < 0.3  → AUTHENTIC
     * Score 0.3–0.7 → SUSPICIOUS
     * Score > 0.7  → LIKELY_FAKE
     *
     * Returns [NEUTRAL_SCORE] (0.5) if the model file is not yet available.
     */
    fun classify(bitmap: Bitmap): Float

    fun close()
}

@Singleton
class ProductClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) : ProductClassifierInterface {

    /**
     * Testing constructor — skips asset loading and uses [testInterpreter] directly.
     * This avoids the need for a real `.tflite` file in JVM unit tests.
     */
    internal constructor(
        context: Context,
        testInterpreter: Interpreter?
    ) : this(context) {
        _useTestInterpreter = true
        _testInterpreter = testInterpreter
    }

    // Set by the testing constructor before any classify() call can trigger the lazy.
    private var _useTestInterpreter = false
    private var _testInterpreter: Interpreter? = null

    // Lazy so we don't crash at injection time with the placeholder file.
    private val interpreter: Interpreter? by lazy {
        if (_useTestInterpreter) return@lazy _testInterpreter
        try {
            Interpreter(loadModelFile())
        } catch (e: Exception) {
            Log.w(TAG, "TFLite model not loaded (placeholder in place?): ${e.message}")
            null
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val afd = context.assets.openFd(MODEL_FILE)
        return FileInputStream(afd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            afd.startOffset,
            afd.declaredLength
        )
    }

    override fun classify(bitmap: Bitmap): Float {
        // Short-circuit before touching the bitmap — no point preprocessing if no model.
        if (interpreter == null) return NEUTRAL_SCORE
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        return classifyBuffer(bitmapToFloatBuffer(scaled))
    }

    /** Scales and normalises [bitmap] into a float32 [0,1] ByteBuffer ready for inference. */
    private fun bitmapToFloatBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(INPUT_BUFFER_BYTES)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)  // R
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)   // G
            buffer.putFloat((pixel and 0xFF) / 255.0f)            // B
        }
        return buffer
    }

    /**
     * Runs inference on a pre-processed [ByteBuffer] (float32, shape [1, 224, 224, 3]).
     * Exposed as `internal` so unit tests can drive inference without going through
     * Android Bitmap / TensorImage preprocessing.
     *
     * Returns [NEUTRAL_SCORE] if the model is not loaded.
     */
    internal fun classifyBuffer(input: ByteBuffer): Float {
        val interp = interpreter ?: return NEUTRAL_SCORE
        val output = Array(1) { FloatArray(1) }
        interp.run(input, output)
        return output[0][0]
    }

    override fun close() {
        interpreter?.close()
    }

    companion object {
        private const val TAG = "ProductClassifier"
        private const val MODEL_FILE = "product_classifier.tflite"
        private const val INPUT_SIZE = 224

        /**
         * Byte capacity of the float32 input tensor: [1, 224, 224, 3] × 4 bytes.
         * Used to validate preprocessing output size in tests.
         */
        const val INPUT_BUFFER_BYTES = INPUT_SIZE * INPUT_SIZE * 3 * 4   // = 602,112

        const val NEUTRAL_SCORE = 0.5f

        fun scoreToVerdict(score: Float): Verdict = when {
            score < 0.3f -> Verdict.AUTHENTIC
            score < 0.7f -> Verdict.SUSPICIOUS
            else -> Verdict.LIKELY_FAKE
        }
    }
}