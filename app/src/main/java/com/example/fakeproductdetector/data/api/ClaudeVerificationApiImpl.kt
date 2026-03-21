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

@Singleton
class ClaudeVerificationApiImpl @Inject constructor(
    private val api: ClaudeVerificationApi,
    private val gson: Gson
) {
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

private fun extractJson(text: String): String {
    val codeBlock = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)
    if (codeBlock != null) return codeBlock.groupValues[1].trim()
    val jsonObject = Regex("\\{[\\s\\S]*\\}").find(text)
    if (jsonObject != null) return jsonObject.value.trim()
    return text.trim()
}