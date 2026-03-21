package com.example.fakeproductdetector.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

data class GeminiRequest(
    val contents: List<Content>
) {
    data class Content(val parts: List<Part>)

    data class Part(
        @SerializedName("inlineData") val inlineData: InlineData? = null,
        val text: String? = null
    )

    data class InlineData(
        @SerializedName("mimeType") val mimeType: String,
        val data: String
    )
}

data class GeminiResponse(
    val candidates: List<Candidate>?
) {
    data class Candidate(val content: Content?)
    data class Content(val parts: List<Part>?)
    data class Part(val text: String?)
}

interface GeminiVisionApi {
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Body request: GeminiRequest
    ): GeminiResponse
}