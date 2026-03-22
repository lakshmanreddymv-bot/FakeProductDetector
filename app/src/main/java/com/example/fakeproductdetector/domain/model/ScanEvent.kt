package com.example.fakeproductdetector.domain.model

// S: Single Responsibility — models the two distinct events emitted during the scan pipeline flow
/**
 * Sealed hierarchy of events emitted by the scan pipeline [kotlinx.coroutines.flow.Flow].
 *
 * The Flow emits zero or more [Progress] events followed by exactly one [Result] event,
 * allowing the UI to display live status messages while the pipeline is running.
 */
sealed class ScanEvent {
    /**
     * Intermediate status update emitted while the pipeline is still processing.
     *
     * Use to drive loading messages in the UI (e.g. "Analyzing with Gemini…").
     *
     * @property message Human-readable description of the current pipeline step.
     */
    data class Progress(val message: String) : ScanEvent()

    /**
     * Terminal event emitted when the pipeline has finished and produced a final result.
     *
     * No further events are emitted after this one.
     *
     * @property scanResult The completed authenticity analysis result.
     */
    data class Result(val scanResult: ScanResult) : ScanEvent()
}