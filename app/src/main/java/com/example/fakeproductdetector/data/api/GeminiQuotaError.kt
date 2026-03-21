package com.example.fakeproductdetector.data.api

/**
 * Represents the three distinct Gemini free-tier quota violations
 * that appear in the 429 response body as `quotaId` strings.
 */
sealed class GeminiQuotaError(message: String) : Exception(message) {

    /** Input token limit per minute exceeded — too much image data in one minute. */
    object TokenLimitPerMinute : GeminiQuotaError(
        "Token limit reached — your image data exceeded Gemini's per-minute token quota. " +
        "Please wait 60 seconds and try again."
    )

    /** Request count per minute exceeded — too many scans in one minute. */
    object RequestsPerMinute : GeminiQuotaError(
        "Too many scans — you've hit Gemini's 15 requests-per-minute limit on the free tier. " +
        "Please wait 60 seconds before scanning again."
    )

    /** Daily request quota exhausted — free tier daily limit hit. */
    object DailyLimitExhausted : GeminiQuotaError(
        "Daily quota exhausted — you've used all free Gemini API calls for today. " +
        "Enable billing at aistudio.google.com to continue, or try again tomorrow."
    )

    /** Generic rate limit when the specific quotaId is not recognised. */
    object Generic : GeminiQuotaError(
        "Gemini API rate limit reached. Please wait 60 seconds and try again."
    )

    companion object {
        /**
         * Parses the raw Gemini error response body and returns the most
         * specific [GeminiQuotaError] subtype. Falls back to [Generic].
         */
        fun fromErrorBody(body: String): GeminiQuotaError = when {
            body.contains("GenerateContentInputTokensPerModelPerMinute", ignoreCase = true) ->
                TokenLimitPerMinute
            body.contains("GenerateRequestsPerDayPerProjectPerModel", ignoreCase = true) ->
                DailyLimitExhausted
            body.contains("GenerateRequestsPerMinutePerProjectPerModel", ignoreCase = true) ->
                RequestsPerMinute
            // Fallback — still a quota error, just unrecognised quotaId
            body.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
            body.contains("429", ignoreCase = true) -> Generic
            else -> Generic
        }
    }
}
