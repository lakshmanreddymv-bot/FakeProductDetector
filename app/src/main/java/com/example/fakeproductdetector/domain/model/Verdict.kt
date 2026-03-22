package com.example.fakeproductdetector.domain.model

// S: Single Responsibility — enumerates the possible authenticity verdicts with display metadata
/**
 * Categorical verdict produced by the AI authenticity pipeline for a scanned product.
 *
 * Each entry carries UI-ready display metadata so the presentation layer does not need
 * to map enum values to strings or colours independently.
 *
 * @property displayName Localised label shown in the UI (e.g. "Likely Fake").
 * @property color Hex colour string used to tint verdict badges and score arcs.
 */
enum class Verdict(val displayName: String, val color: String) {
    /** Product shows strong signs of being genuine. */
    AUTHENTIC("Authentic", "#4CAF50"),

    /** Product has some concerning indicators but is not conclusively fake. */
    SUSPICIOUS("Suspicious", "#FF9800"),

    /** Product shows clear signs of being counterfeit. */
    LIKELY_FAKE("Likely Fake", "#F44336")
}