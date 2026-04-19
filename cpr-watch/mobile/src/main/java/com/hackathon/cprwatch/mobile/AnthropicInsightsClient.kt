package com.hackathon.cprwatch.mobile

import com.hackathon.cprwatch.shared.insights.AiSessionSummary
import com.hackathon.cprwatch.shared.insights.ScorecardAlignedStats
import com.hackathon.cprwatch.shared.insights.SessionSummary
import com.hackathon.cprwatch.shared.insights.buildClaudeUserPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Calls Anthropic Messages API with a single tool: one narrative session summary.
 */
object AnthropicInsightsClient {

    private val summaryToolDefinition: JSONObject by lazy {
        JSONObject().apply {
            put("name", "session_summary")
            put(
                "description",
                "Return one short narrative recap of the session."
            )
            put(
                "input_schema",
                JSONObject().apply {
                    put("type", "object")
                    put(
                        "properties",
                        JSONObject().apply {
                            put(
                                "summary",
                                JSONObject().apply {
                                    put("type", "string")
                                    put(
                                        "description",
                                        "Hard cap: **≤5 sentences**, ideally **3–4**. Aim **≤50 words**. " +
                                            "Coach debrief—warm, human, **no lists**, **no statistic inventory**; " +
                                            "**at most one or two numbers** only if essential. " +
                                            "Optional: **at most one short phrase each** for what to **start**, **continue**, and **stop** doing—" +
                                            "only when the metrics clearly support each; skip any that do not."
                                    )
                                }
                            )
                        }
                    )
                    put("required", JSONArray().put("summary"))
                }
            )
        }
    }

    private val systemPrompt = """
Very short CPR drill debrief (not medical advice). Targets: rate 100–120 BPM, depth 50–60 mm, full recoil.

Return only `session_summary.summary`: few sentences, under ~50 words, encouraging. You may briefly name what to start / continue / stop doing when the data supports it—otherwise omit.
""".trimIndent()

    private val httpClient: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    suspend fun fetchSessionSummary(
        apiKey: String,
        summary: SessionSummary,
        scorecard: ScorecardAlignedStats,
    ): Result<AiSessionSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val userPrompt = summary.buildClaudeUserPrompt(scorecard)
                val body = JSONObject().apply {
                    put("model", DevApiKeys.anthropicModelOrDefault())
                    put("max_tokens", 180)
                    put("system", systemPrompt)
                    put(
                        "messages",
                        JSONArray().put(
                            JSONObject().put("role", "user").put("content", userPrompt)
                        )
                    )
                    put("tools", JSONArray().put(summaryToolDefinition))
                    put(
                        "tool_choice",
                        JSONObject().put("type", "tool").put("name", "session_summary")
                    )
                }

                val req = Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    val responseText = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        error("HTTP ${resp.code}: $responseText")
                    }
                    parseSummaryToolResponse(responseText)
                }
            }
        }

    private fun parseSummaryToolResponse(responseJson: String): AiSessionSummary {
        val root = JSONObject(responseJson)
        val content = root.getJSONArray("content")
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "tool_use" &&
                block.optString("name") == "session_summary"
            ) {
                val input = block.getJSONObject("input")
                return AiSessionSummary(summary = input.getString("summary").trim())
            }
        }
        error("No session_summary tool_use block in Claude response")
    }
}
