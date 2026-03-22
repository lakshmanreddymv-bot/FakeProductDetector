package com.example.fakeproductdetector.data.api

import com.example.fakeproductdetector.domain.model.Product
import com.example.fakeproductdetector.domain.model.ScanResult
import com.example.fakeproductdetector.domain.model.Verdict
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// S: Single Responsibility — responsible only for Claude API communication and cross-verification response parsing
// D: Dependency Inversion — depends on ClaudeVerificationApi interface, not on a concrete HTTP client
/**
 * Wraps the [ClaudeVerificationApi] Retrofit interface with prompt construction and response parsing.
 *
 * Takes the initial [GeminiAnalysis] produced by [GeminiVisionApiImpl], builds a structured
 * cross-verification prompt, calls the Claude API, and parses the JSON response into a
 * final [ScanResult].
 *
 * @property api Retrofit-generated [ClaudeVerificationApi] implementation.
 * @property gson Gson instance used to parse the JSON response from Claude.
 */
@Singleton
class ClaudeVerificationApiImpl @Inject constructor(
    private val api: ClaudeVerificationApi,
    private val gson: Gson
) {
    /**
     * Cross-verifies the Gemini analysis using Claude and returns a refined [ScanResult].
     *
     * Constructs a prompt that presents Gemini's initial findings to Claude, then parses
     * Claude's JSON response into a final [ScanResult] associated with the given [product].
     *
     * @param geminiAnalysis The initial authenticity assessment produced by Gemini.
     * @param product The product being evaluated (carries identity and metadata).
     * @return A refined [ScanResult] combining insights from both Gemini and Claude.
     * @throws Exception when Claude returns an empty or unparseable response.
     */
    suspend fun verify(geminiAnalysis: GeminiAnalysis, product: Product): ScanResult {
        val redFlagsSummary = if (geminiAnalysis.redFlags.isEmpty()) {
            "None identified"
        } else {
            geminiAnalysis.redFlags.joinToString("; ")
        }

        val prompt = """
            You are a product authenticity expert. A vision AI analyzed this ${product.category.name.lowercase()} product and produced the following assessment:

            Product Name: ${geminiAnalysis.productName}
            Initial Score: ${geminiAnalysis.authenticityScore}/100
            Initial Verdict: ${geminiAnalysis.verdict.name}
            Red Flags: $redFlagsSummary
            Initial Analysis: ${geminiAnalysis.explanation}

            Cross-verify this analysis and provide a refined, comprehensive authenticity assessment.
            Respond ONLY with a valid JSON object (no markdown, no code blocks):
            {
              "authenticityScore": 75.5,
              "verdict": "AUTHENTIC",
              "redFlags": ["specific concern 1", "specific concern 2"],
              "explanation": "comprehensive explanation combining both analyses"
            }
            Rules:
            - verdict must be exactly one of: AUTHENTIC, SUSPICIOUS, LIKELY_FAKE
            - authenticityScore is a float from 0 to 100 (100 = definitely authentic)
            - redFlags is an array of strings (empty array if no concerns found)
        """.trimIndent()

        val request = ClaudeRequest(
            model = "claude-haiku-4-5",
            maxTokens = 1024,
            messages = listOf(ClaudeRequest.Message(role = "user", content = prompt))
        )

        val response = api.verify(request)

        val responseText = response.content
            ?.firstOrNull { it.type == "text" }?.text
            ?: throw Exception("Empty or null response from Claude API")

        return parseResponse(responseText, product)
    }

    /**
     * Parses the raw text response from Claude into a [ScanResult].
     *
     * Extracts a JSON object from the response text (handling optional markdown code fences),
     * then maps each field to the corresponding [ScanResult] property.
     *
     * @param text Raw text response from the Claude API.
     * @param product The product to associate with the resulting [ScanResult].
     * @return Parsed [ScanResult]; uses safe defaults for any missing or malformed fields.
     */
    private fun parseResponse(text: String, product: Product): ScanResult {
        val json = extractJson(text)
        val obj = gson.fromJson(json, JsonObject::class.java)

        val score = obj.get("authenticityScore")?.asFloat?.coerceIn(0f, 100f) ?: 50f
        val verdict = obj.get("verdict")?.asString
            ?.let { runCatching { Verdict.valueOf(it) }.getOrNull() }
            ?: Verdict.SUSPICIOUS
        val redFlagsType = object : TypeToken<List<String>>() {}.type
        val redFlags: List<String> = runCatching {
            gson.fromJson<List<String>>(obj.get("redFlags"), redFlagsType) ?: emptyList()
        }.getOrDefault(emptyList())
        val explanation = obj.get("explanation")?.asString ?: "No explanation provided"

        return ScanResult(
            id = UUID.randomUUID().toString(),
            product = product,
            authenticityScore = score,
            verdict = verdict,
            redFlags = redFlags,
            explanation = explanation,
            scannedAt = System.currentTimeMillis()
        )
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