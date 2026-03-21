package com.example.fakeproductdetector.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

data class ClaudeRequest(
    val model: String,
    @SerializedName("max_tokens") val maxTokens: Int,
    val messages: List<Message>
) {
    data class Message(val role: String, val content: String)
}

data class ClaudeResponse(
    val id: String?,
    val type: String?,
    val role: String?,
    val content: List<ContentBlock>?,
    val model: String?
) {
    data class ContentBlock(val type: String, val text: String?)
}

interface ClaudeVerificationApi {
    @Headers(
        "anthropic-version: 2023-06-01",
        "content-type: application/json"
    )
    @POST("v1/messages")
    suspend fun verify(
        @Body request: ClaudeRequest
    ): ClaudeResponse
}