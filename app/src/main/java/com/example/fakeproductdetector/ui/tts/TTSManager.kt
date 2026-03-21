package com.example.fakeproductdetector.ui.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.fakeproductdetector.domain.model.ScanResult
import kotlinx.coroutines.delay
import java.util.Locale

private const val TAG = "TTSManager"
private const val READY_POLL_INTERVAL_MS = 300L
private const val READY_POLL_MAX_ATTEMPTS = 10

class TTSManager(
    context: Context,
    ttsFactory: (Context, TextToSpeech.OnInitListener) -> TextToSpeech =
        { ctx, listener -> TextToSpeech(ctx, listener) }
) : TextToSpeech.OnInitListener {

    private val tts: TextToSpeech = ttsFactory(context, this)
    private var ready = false
    private val pending = mutableListOf<String>()

    var isMuted: Boolean = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            ready = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            if (ready) {
                pending.forEach { speakNow(it) }
                pending.clear()
            } else {
                Log.w(TAG, "TTS language not supported (result=$result)")
            }
        } else {
            Log.e(TAG, "TTS init failed (status=$status)")
        }
    }

    fun speak(text: String) {
        if (isMuted) return
        if (ready) speakNow(text) else pending.add(text)
    }

    /**
     * Suspending variant: polls [isReady] up to [READY_POLL_MAX_ATTEMPTS] times
     * (total wait ≈ 3 s) before giving up. Solves the race where [speak] is
     * called from a LaunchedEffect before [onInit] has completed.
     */
    suspend fun speakWhenReady(text: String) {
        var attempts = 0
        while (!ready && attempts < READY_POLL_MAX_ATTEMPTS) {
            delay(READY_POLL_INTERVAL_MS)
            attempts++
        }
        if (ready) speak(text)
        else Log.w(TAG, "speakWhenReady: TTS not ready after ${attempts * READY_POLL_INTERVAL_MS}ms — skipping")
    }

    /** Builds the result announcement and speaks it via [speakWhenReady]. */
    suspend fun speakResultWhenReady(result: ScanResult) {
        speakWhenReady(buildSpeechText(result))
    }

    /**
     * Synchronous convenience kept for use-cases that know TTS is already ready
     * (e.g. replay-on-demand). Tests exercise this path directly.
     */
    fun speakResult(result: ScanResult) {
        speak(buildSpeechText(result))
    }

    fun stop() {
        tts.stop()
    }

    fun isReady(): Boolean = ready

    fun shutdown() {
        ready = false
        pending.clear()
        tts.stop()
        tts.shutdown()
    }

    private fun speakNow(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun buildSpeechText(result: ScanResult): String {
        val score = result.authenticityScore.toInt()
        return buildString {
            append("Product: ${result.product.name}. ")
            append("Score: $score out of 100. ")
            append("Verdict: ${result.verdict.displayName}.")
            if (result.redFlags.isNotEmpty()) {
                append(" Warning: ${result.redFlags.joinToString(", ")}.")
            }
        }
    }
}