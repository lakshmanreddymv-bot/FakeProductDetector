package com.example.fakeproductdetector.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

// S: Single Responsibility — models the Claude Messages API request/response DTOs and Retrofit interface

/**
 * Request body sent to the Claude `messages` endpoint.
 *
 * @property model Claude model ID to use (e.g. "claude-haiku-4-5").
 * @property maxTokens Maximum number of tokens the model may generate in its response.
 * @property messages Ordered list of conversation turns (currently always a single user message).
 */
data class ClaudeRequest(
    val model: String,
    @SerializedName("max_tokens") val maxTokens: Int,
    val messages: List<Message>
) {
    /**
     * A single conversation message.
     *
     * @property role Conversation role — "user" or "assistant".
     * @property content The text content of the message.
     */
    data class Message(val role: String, val content: String)
}

/**
 * Response body returned by the Claude `messages` endpoint.
 *
 * @property id Unique identifier for this API response.
 * @property type Response type (typically "message").
 * @property role The role of the responder (typically "assistant").
 * @property content List of content blocks in the response; text blocks carry the generated text.
 * @property model The Claude model ID that produced this response.
 */
data class ClaudeResponse(
    val id: String?,
    val type: String?,
    val role: String?,
    val content: List<ContentBlock>?,
    val model: String?
) {
    /**
     * A single content block in the Claude response.
     *
     * @property type Block type — "text" for generated text, or other types for tool use.
     * @property text The generated text; null for non-text block types.
     */
    data class ContentBlock(val type: String, val text: String?)
}

// I: Interface Segregation — exposes only the single endpoint needed for text-based verification
/**
 * Retrofit interface for the Claude `messages` REST endpoint.
 *
 * Required headers (`anthropic-version`, `content-type`) are declared statically.
 * The `x-api-key` authentication header is injected by the OkHttp interceptor configured in
 * [com.example.fakeproductdetector.di.AppModule].
 */
interface ClaudeVerificationApi {
    /**
     * Sends a text prompt to Claude and returns its generated response.
     *
     * @param request The request body containing the model, token limit, and message list.
     * @return Claude's response containing the generated text content blocks.
     */
    @Headers(
        "anthropic-version: 2023-06-01",
        "content-type: application/json"
    )
    @POST("v1/messages")
    suspend fun verify(
        @Body request: ClaudeRequest
    ): ClaudeResponse
}