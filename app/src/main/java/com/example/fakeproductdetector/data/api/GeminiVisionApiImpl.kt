package com.example.fakeproductdetector.data.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.fakeproductdetector.domain.model.Category
import com.example.fakeproductdetector.domain.model.Verdict
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GeminiAPI"

data class GeminiAnalysis(
    val productName: String,
    val authenticityScore: Float,
    val verdict: Verdict,
    val redFlags: List<String>,
    val explanation: String
)

private const val MAX_IMAGE_DIMENSION = 1024
private const val JPEG_QUALITY = 85

@Singleton
class GeminiVisionApiImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: GeminiVisionApi,
    private val gson: Gson
) {

    suspend fun analyze(imageUri: String, category: Category): GeminiAnalysis {
        val uri = Uri.parse(imageUri)

        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Cannot read image from URI: $imageUri")

        val compressed = compressImage(rawBytes)
        val base64Image = Base64.encodeToString(compressed, Base64.NO_WRAP)
        val mimeType = "image/jpeg"

        val prompt = """
            Analyze this ${category.name.lowercase()} product image for authenticity.
            Respond ONLY with a valid JSON object (no markdown, no code blocks):
            {
              "productName": "detected product name",
              "authenticityScore": 75.5,
              "verdict": "AUTHENTIC",
              "redFlags": ["flag1", "flag2"],
              "explanation": "detailed explanation"
            }
            Rules:
            - verdict must be exactly one of: AUTHENTIC, SUSPICIOUS, LIKELY_FAKE
            - authenticityScore is a float from 0 to 100 (100 = definitely authentic)
            - redFlags is an array of strings describing specific concerns (empty array if none)
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiRequest.Content(
                    parts = listOf(
                        GeminiRequest.Part(
                            inlineData = GeminiRequest.InlineData(mimeType, base64Image)
                        ),
                        GeminiRequest.Part(text = prompt)
                    )
                )
            )
        )

        Log.d(TAG, "Sending image to Gemini — size: ${compressed.size / 1024}KB, category: $category")

        val response = try {
            api.generateContent(request)
        } catch (e: retrofit2.HttpException) {
            // Parse the response body to get the exact quotaId and throw a typed error
            val errorBody = e.response()?.errorBody()?.string() ?: ""
            Log.e(TAG, "Gemini HTTP ${e.code()} — body: ${errorBody.take(500)}")
            if (e.code() == 429 || errorBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
                throw GeminiQuotaError.fromErrorBody(errorBody)
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API call failed: ${e.javaClass.simpleName} — ${e.message}")
            throw e
        }

        val responseText = response.candidates
            ?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty or null response from Gemini API")

        Log.d(TAG, "Gemini response received: ${responseText.take(200)}")
        return parseResponse(responseText)
    }

    private fun compressImage(rawBytes: ByteArray): ByteArray {
        val original = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: return rawBytes

        val scale = minOf(
            MAX_IMAGE_DIMENSION.toFloat() / original.width,
            MAX_IMAGE_DIMENSION.toFloat() / original.height,
            1f
        )

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt(),
                (original.height * scale).toInt(),
                true
            )
        } else {
            original
        }

        return ByteArrayOutputStream().also { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }.toByteArray()
    }

    private fun parseResponse(text: String): GeminiAnalysis {
        val json = extractJson(text)
        val obj = gson.fromJson(json, JsonObject::class.java)

        val productName = obj.get("productName")?.asString ?: "Unknown Product"
        val score = obj.get("authenticityScore")?.asFloat?.coerceIn(0f, 100f) ?: 50f
        val verdictStr = obj.get("verdict")?.asString ?: "SUSPICIOUS"
        val verdict = runCatching { Verdict.valueOf(verdictStr) }.getOrDefault(Verdict.SUSPICIOUS)

        val redFlagsType = object : TypeToken<List<String>>() {}.type
        val redFlags: List<String> = runCatching {
            gson.fromJson<List<String>>(obj.get("redFlags"), redFlagsType) ?: emptyList()
        }.getOrDefault(emptyList())

        val explanation = obj.get("explanation")?.asString ?: "No explanation provided"

        return GeminiAnalysis(productName, score, verdict, redFlags, explanation)
    }
}

private fun extractJson(text: String): String {
    val codeBlock = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)
    if (codeBlock != null) return codeBlock.groupValues[1].trim()
    val jsonObject = Regex("\\{[\\s\\S]*\\}").find(text)
    if (jsonObject != null) return jsonObject.value.trim()
    return text.trim()
}
