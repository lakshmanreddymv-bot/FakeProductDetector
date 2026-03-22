package com.example.fakeproductdetector.domain.model

// S: Single Responsibility — enumerates supported product categories used to tune AI analysis prompts
/**
 * Product category selected by the user before scanning.
 *
 * The selected category is included in the Gemini Vision prompt so the AI can apply
 * category-specific authenticity heuristics (e.g. hologram checks for medicine,
 * serial-number patterns for electronics).
 */
enum class Category {
    /** Pharmaceutical and health products. */
    MEDICINE,

    /** Consumer electronics and accessories. */
    ELECTRONICS,

    /** High-end branded goods (watches, handbags, etc.). */
    LUXURY,

    /** Packaged food and beverages. */
    FOOD,

    /** Any product that does not fit the categories above. */
    OTHER
}