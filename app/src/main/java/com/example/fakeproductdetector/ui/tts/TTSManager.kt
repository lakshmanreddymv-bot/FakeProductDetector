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

// S: Single Responsibility — manages Text-to-Speech lifecycle and speech queuing for scan results only
/**
 * Singleton wrapper around Android's [TextToSpeech] engine that handles initialisation,
 * mute state, and speech queuing for product scan result announcements.
 *
 * Speech requests made before the TTS engine has finished initialising are queued and
 * replayed automatically once [onInit] completes. The suspending [speakWhenReady] polls
 * for readiness to solve the race between composable [LaunchedEffect]s and TTS init.
 *
 * @property context Application context passed to the [TextToSpeech] engine.
 * @property ttsFactory Factory function used to create the [TextToSpeech] instance; overrideable in tests.
 */
class TTSManager(
    context: Context,
    ttsFactory: (Context, TextToSpeech.OnInitListener) -> TextToSpeech =
        { ctx, listener -> TextToSpeech(ctx, listener) }
) : TextToSpeech.OnInitListener {

    private val tts: TextToSpeech = ttsFactory(context, this)
    private var ready = false
    private val pending = mutableListOf<String>()

    /**
     * When `true`, all [speak] calls are silently ignored without queuing.
     *
     * Should be toggled via [com.example.fakeproductdetector.ui.result.ResultViewModel.toggleMute].
     */
    var isMuted: Boolean = false

    /**
     * Called by the [TextToSpeech] engine after initialisation completes.
     *
     * Sets [ready] to `true` on success and flushes any pending speech requests.
     * Logs a warning if the language is not supported and an error if initialisation failed.
     *
     * @param status [TextToSpeech.SUCCESS] if the engine initialised successfully, or an error code.
     */
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

    /**
     * Speaks [text] immediately if the TTS engine is ready, or queues it for later.
     *
     * No-ops silently if [isMuted] is `true`.
     *
     * @param text The text to speak.
     */
    fun speak(text: String) {
        if (isMuted) return
        if (ready) speakNow(text) else pending.add(text)
    }

    /**
     * Suspending variant of [speak] that polls for readiness before speaking.
     *
     * Polls [ready] up to [READY_POLL_MAX_ATTEMPTS] times (total wait ≈ 3 s) before giving up.
     * Solves the race where [speak] is called from a LaunchedEffect before [onInit] has completed.
     *
     * @param text The text to speak once the TTS engine is ready.
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

    /**
     * Builds the result announcement text and speaks it via [speakWhenReady].
     *
     * @param result The [ScanResult] whose summary should be spoken aloud.
     */
    suspend fun speakResultWhenReady(result: ScanResult) {
        speakWhenReady(buildSpeechText(result))
    }

    /**
     * Synchronous convenience variant that speaks a [ScanResult] summary immediately.
     *
     * Intended for use-cases where the TTS engine is already known to be ready
     * (e.g. replay-on-demand). Tests exercise this path directly.
     *
     * @param result The [ScanResult] whose summary should be spoken aloud.
     */
    fun speakResult(result: ScanResult) {
        speak(buildSpeechText(result))
    }

    /**
     * Stops any TTS speech currently in progress without changing [isMuted].
     */
    fun stop() {
        tts.stop()
    }

    /**
     * Returns `true` if the TTS engine has successfully initialised and is ready to speak.
     *
     * @return `true` when TTS is ready to accept speech requests.
     */
    fun isReady(): Boolean = ready

    /**
     * Fully shuts down the TTS engine and clears any pending speech requests.
     *
     * Should only be called when the TTS engine is no longer needed (e.g. app termination).
     * Do not call this from individual ViewModels since [TTSManager] is a singleton.
     */
    fun shutdown() {
        ready = false
        pending.clear()
        tts.stop()
        tts.shutdown()
    }

    /**
     * Passes [text] to the TTS engine for immediate speech output, flushing any current utterance.
     *
     * @param text The text to speak.
     */
    private fun speakNow(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    /**
     * Builds the spoken announcement text for a [ScanResult].
     *
     * Includes product name, score, verdict, and any red flags.
     *
     * @param result The [ScanResult] to summarise.
     * @return A natural-language string suitable for TTS playback.
     */
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