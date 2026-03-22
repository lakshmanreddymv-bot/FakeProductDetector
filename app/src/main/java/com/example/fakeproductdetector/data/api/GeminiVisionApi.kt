package com.example.fakeproductdetector.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

// S: Single Responsibility — models the Gemini generateContent request/response DTOs and Retrofit interface

/**
 * Request body sent to the Gemini `generateContent` endpoint.
 *
 * @property contents List of content parts that make up the multimodal prompt (image + text).
 */
data class GeminiRequest(
    val contents: List<Content>
) {
    /**
     * A single turn in the Gemini conversation, composed of one or more [Part]s.
     *
     * @property parts The ordered list of content parts (inline image data and/or text).
     */
    data class Content(val parts: List<Part>)

    /**
     * A single content part — either an inline image or a text string.
     *
     * @property inlineData Base64-encoded image data with MIME type; null for text-only parts.
     * @property text Prompt text; null for image-only parts.
     */
    data class Part(
        @SerializedName("inlineData") val inlineData: InlineData? = null,
        val text: String? = null
    )

    /**
     * Container for a base64-encoded image payload.
     *
     * @property mimeType MIME type of the image (e.g. "image/jpeg").
     * @property data Base64-encoded image bytes (NO_WRAP encoding).
     */
    data class InlineData(
        @SerializedName("mimeType") val mimeType: String,
        val data: String
    )
}

/**
 * Response body returned by the Gemini `generateContent` endpoint.
 *
 * @property candidates List of generated response candidates; may be null on error.
 */
data class GeminiResponse(
    val candidates: List<Candidate>?
) {
    /** A single response candidate produced by the model. */
    data class Candidate(val content: Content?)

    /** The content container within a candidate. */
    data class Content(val parts: List<Part>?)

    /** A single text part within the model's response content. */
    data class Part(val text: String?)
}

// I: Interface Segregation — exposes only the single endpoint needed for vision-based content generation
/**
 * Retrofit interface for the Gemini `generateContent` REST endpoint.
 *
 * The API key is appended as a query parameter by the OkHttp interceptor configured in
 * [com.example.fakeproductdetector.di.AppModule].
 */
interface GeminiVisionApi {
    /**
     * Sends a multimodal prompt (image + text) to the Gemini model and returns the generated response.
     *
     * @param request The structured request containing image data and the analysis prompt.
     * @return The model's generated content response.
     */
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Body request: GeminiRequest
    ): GeminiResponse
}