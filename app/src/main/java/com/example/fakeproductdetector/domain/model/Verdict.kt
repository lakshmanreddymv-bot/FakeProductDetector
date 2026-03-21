package com.example.fakeproductdetector.domain.model

enum class Verdict(val displayName: String, val color: String) {
    AUTHENTIC("Authentic", "#4CAF50"),
    SUSPICIOUS("Suspicious", "#FF9800"),
    LIKELY_FAKE("Likely Fake", "#F44336")
}