package com.example.fakeproductdetector.ui.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.fakeproductdetector.domain.model.ScanResult
import java.util.Locale

private const val TAG = "TTSManager"

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

    fun speakResult(result: ScanResult) {
        val score = result.authenticityScore.toInt()
        val speech = buildString {
            append("Product: ${result.product.name}. ")
            append("Score: $score out of 100. ")
            append("Verdict: ${result.verdict.displayName}.")
            if (result.redFlags.isNotEmpty()) {
                append(" Warning: ${result.redFlags.joinToString(", ")}.")
            }
        }
        speak(speech)
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
}