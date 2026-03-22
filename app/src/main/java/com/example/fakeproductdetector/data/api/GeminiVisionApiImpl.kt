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

// S: Single Responsibility — handles image preparation, Gemini API communication, and response parsing only
/**
 * Structured result of the Gemini vision analysis step, before Claude cross-verification.
 *
 * @property productName Product name detected from the image by Gemini.
 * @property authenticityScore Initial authenticity score (0–100) assigned by Gemini.
 * @property verdict Initial categorical verdict from Gemini.
 * @property redFlags List of specific concerns identified by Gemini; empty if none.
 * @property explanation Gemini's human-readable analysis of the product image.
 */
data class GeminiAnalysis(
    val productName: String,
    val authenticityScore: Float,
    val verdict: Verdict,
    val redFlags: List<String>,
    val explanation: String
)

/** Maximum dimension (width or height) in pixels that images are scaled down to before sending. */
private const val MAX_IMAGE_DIMENSION = 1024

/** JPEG compression quality used when re-encoding the scaled image. */
private const val JPEG_QUALITY = 85

// S: Single Responsibility — responsible only for Gemini Vision API communication and image pre-processing
// D: Dependency Inversion — depends on GeminiVisionApi interface, not on a concrete HTTP client
/**
 * Wraps the [GeminiVisionApi] Retrofit interface with image pre-processing and response parsing.
 *
 * Reads the product image from a URI, compresses and base64-encodes it, builds the structured
 * JSON prompt, calls the Gemini API, and parses the JSON response into a [GeminiAnalysis].
 *
 * @property context Application context used to resolve content:// and file:// image URIs.
 * @property api Retrofit-generated [GeminiVisionApi] implementation.
 * @property gson Gson instance used to parse the JSON response from Gemini.
 */
@Singleton
class GeminiVisionApiImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: GeminiVisionApi,
    private val gson: Gson
) {

    /**
     * Analyses the product image at [imageUri] for authenticity within the given [category].
     *
     * Compresses the image, constructs a structured prompt, calls the Gemini API, and returns
     * a parsed [GeminiAnalysis]. Throws [GeminiQuotaError] on rate-limit (HTTP 429) responses.
     *
     * @param imageUri URI of the product image (file:// or content://).
     * @param category Product category used to specialise the Gemini prompt.
     * @return Parsed [GeminiAnalysis] containing the initial authenticity assessment.
     * @throws GeminiQuotaError when the Gemini free-tier quota is exceeded.
     * @throws IOException when the image cannot be read from the provided URI.
     */
    suspend fun analyze(imageUri: String, category: Category): GeminiAnalysis {
        val uri = Uri.parse(imageUri)

        // contentResolver.openInputStream fails on file:// URIs — handle both schemes
        val rawBytes = when (uri.scheme) {
            "file" -> uri.path?.let { java.io.File(it).readBytes() }
            else   -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: throw IOException("Cannot read image from URI: $imageUri")

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

    /**
     * Scales and JPEG-compresses the raw image bytes to reduce the base64 payload size.
     *
     * If either dimension exceeds [MAX_IMAGE_DIMENSION], the image is proportionally scaled down.
     *
     * @param rawBytes Raw bytes of the original image.
     * @return Compressed JPEG byte array; returns [rawBytes] unchanged if decoding fails.
     */
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

    /**
     * Parses the raw text response from Gemini into a [GeminiAnalysis].
     *
     * Extracts a JSON object from the response (handling optional markdown code fences),
     * then maps each field to the corresponding [GeminiAnalysis] property.
     *
     * @param text Raw text response from the Gemini API.
     * @return Parsed [GeminiAnalysis]; uses safe defaults for any missing or malformed fields.
     */
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

/**
 * Extracts a JSON object string from [text], stripping optional markdown code fences.
 *
 * Tries to find a ```json ... ``` block first, then falls back to the first bare `{ ... }` match.
 *
 * @param text Raw text that may contain a JSON object, possibly wrapped in markdown fences.
 * @return The extracted JSON string, or [text] trimmed if no JSON structure is found.
 */
private fun extractJson(text: String): String {
    val codeBlock = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)
    if (codeBlock != null) return codeBlock.groupValues[1].trim()
    val jsonObject = Regex("\\{[\\s\\S]*\\}").find(text)
    if (jsonObject != null) return jsonObject.value.trim()
    return text.trim()
}