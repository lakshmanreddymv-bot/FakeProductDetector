package com.example.fakeproductdetector.domain.model

/** Events emitted by the scan pipeline flow. */
sealed class ScanEvent {
    /** Intermediate status update — use to drive loading messages in the UI. */
    data class Progress(val message: String) : ScanEvent()

    /** Terminal event — the pipeline has finished and produced a result. */
    data class Result(val scanResult: ScanResult) : ScanEvent()
}