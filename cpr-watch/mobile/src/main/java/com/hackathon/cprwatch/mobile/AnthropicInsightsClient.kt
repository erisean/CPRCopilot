package com.hackathon.cprwatch.mobile

import com.hackathon.cprwatch.shared.insights.InsightItem
import com.hackathon.cprwatch.shared.insights.InsightStatus
import com.hackathon.cprwatch.shared.insights.OverallGrade
import com.hackathon.cprwatch.shared.insights.SessionInsights
import com.hackathon.cprwatch.shared.insights.SessionSummary
import com.hackathon.cprwatch.shared.insights.buildUserPromptForClaude
import com.hackathon.cprwatch.shared.insights.parseInsightCategory
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
 * Calls Anthropic Messages API with the same tool schema as [data-pipeline/generate_insights.py].
 */
object AnthropicInsightsClient {

    private val toolDefinition: JSONObject by lazy {
        JSONObject(
            "{\"name\":\"session_insights\",\"description\":\"Post-session CPR performance insights for the rescuer scorecard\",\"input_schema\":{\"type\":\"object\",\"properties\":{\"overall_summary\":{\"type\":\"string\",\"description\":\"2-3 sentence overall session summary. Lead with the most important finding. Be specific with numbers.\"},\"overall_grade\":{\"type\":\"string\",\"enum\":[\"A\",\"B\",\"C\",\"D\",\"F\"],\"description\":\"A=excellent, B=good, C=adequate, D=needs work, F=poor\"},\"insights\":{\"type\":\"array\",\"description\":\"Multiple insight cards (usually 3-6 for uneven sessions): one distinct category per entry when applicable. Split rate / depth / recoil / CPR fraction / fatigue rather than merging into one block. Omit noise on near-perfect sessions.\",\"items\":{\"type\":\"object\",\"properties\":{\"category\":{\"type\":\"string\",\"enum\":[\"compression_rate\",\"compression_depth\",\"recoil\",\"cpr_fraction\",\"fatigue\",\"consistency\",\"overall_technique\"],\"description\":\"Which metric this insight is about\"},\"title\":{\"type\":\"string\",\"description\":\"Short title for the insight card, 5-8 words.\"},\"status\":{\"type\":\"string\",\"enum\":[\"good\",\"warning\",\"critical\"],\"description\":\"good=in guideline, warning=marginal or trending bad, critical=out of guideline\"},\"metric_value\":{\"type\":\"string\",\"description\":\"The key number(s) for this category.\"},\"explanation\":{\"type\":\"string\",\"description\":\"2-4 sentences.\"},\"recommendation\":{\"type\":\"string\",\"description\":\"1-2 sentences.\"}},\"required\":[\"category\",\"title\",\"status\",\"metric_value\",\"explanation\",\"recommendation\"]}},\"top_priority\":{\"type\":\"string\",\"description\":\"Single most impactful thing to focus on next session. One sentence.\"}},\"required\":[\"overall_summary\",\"overall_grade\",\"insights\",\"top_priority\"]}}"
        )
    }

    private val systemPrompt = """
You are a CPR performance coach analyzing post-session data from a smartwatch-based CPR training app.

Your audience is the rescuer who just finished a CPR session (on a mannequin or in a drill). They want to know how they did and what to improve. They are NOT medical professionals by default — use plain language.

Guidelines you evaluate against (AHA 2020):
- Compression rate: 100-120 BPM
- Compression depth: 50-60mm (5-6 cm)
- Chest recoil: full release between compressions (>= 95% in our system)
- CPR fraction: >= 80% (minimize hands-off time)
- Rescuer swap: every 2 minutes to prevent fatigue

Tone rules:
- Be specific with numbers. "Your depth dropped 40%" not "your depth decreased."
- Connect metrics to root causes. Don't just report — explain WHY.
- When multiple metrics degrade together, name the shared cause (often fatigue) **and** still split actionable feedback across separate cards where it helps (rate vs depth vs recoil vs swap timing).
- Give credit where due. If rate was perfect, say so — even if depth was bad.
- Recommendations must be concrete and actionable. "Practice 3-minute continuous sets" not "try to push harder."
- Never be discouraging. Frame weakness as opportunity.
- Do NOT pad with generic CPR education. Every sentence should reference THIS session's data.

Insight cards (critical for the UI):
- Return **multiple entries** in the insights array whenever several guideline areas moved off-target (typically **3–6 cards** for messy sessions).
- Use **one card per distinct theme** (compression_rate, compression_depth, recoil, cpr_fraction, fatigue, consistency, overall_technique). Do **not** collapse everything into a single mega-card unless the session truly has only one issue.
- Brief "good news" cards are fine when a metric was solid — the scorecard expects several scannable tiles, not one wall of text.
""".trimIndent()

    private val httpClient: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    suspend fun fetchInsights(apiKey: String, summary: SessionSummary): Result<SessionInsights> =
        withContext(Dispatchers.IO) {
            runCatching {
                val userPrompt = summary.buildUserPromptForClaude()
                val body = JSONObject().apply {
                    put("model", DevApiKeys.anthropicModelOrDefault())
                    put("max_tokens", 4096)
                    put("system", systemPrompt)
                    put(
                        "messages",
                        JSONArray().put(
                            JSONObject().put("role", "user").put("content", userPrompt)
                        )
                    )
                    put("tools", JSONArray().put(toolDefinition))
                    put(
                        "tool_choice",
                        JSONObject().put("type", "tool").put("name", "session_insights")
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
                    parseToolUseResponse(responseText)
                }
            }
        }

    private fun parseToolUseResponse(responseJson: String): SessionInsights {
        val root = JSONObject(responseJson)
        val content = root.getJSONArray("content")
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "tool_use" &&
                block.optString("name") == "session_insights"
            ) {
                val input = block.getJSONObject("input")
                return mapToolInput(input)
            }
        }
        error("No session_insights tool_use block in Claude response")
    }

    private fun mapToolInput(input: JSONObject): SessionInsights {
        val insightsJson = input.getJSONArray("insights")
        val items = mutableListOf<InsightItem>()
        for (i in 0 until insightsJson.length()) {
            val o = insightsJson.getJSONObject(i)
            val statusStr = o.getString("status").lowercase()
            val status = when (statusStr) {
                "good" -> InsightStatus.GOOD
                "warning" -> InsightStatus.WARNING
                else -> InsightStatus.CRITICAL
            }
            items.add(
                InsightItem(
                    category = parseInsightCategory(o.getString("category")),
                    title = o.getString("title"),
                    status = status,
                    metricValue = o.getString("metric_value"),
                    explanation = o.getString("explanation"),
                    recommendation = o.getString("recommendation")
                )
            )
        }

        val gradeRaw = input.getString("overall_grade").trim().uppercase()
        val gradeLetter = gradeRaw.firstOrNull { it in 'A'..'F' }?.toString() ?: "C"
        val grade = OverallGrade.entries.find { it.name == gradeLetter }
            ?: OverallGrade.C

        return SessionInsights(
            overallSummary = input.getString("overall_summary"),
            overallGrade = grade,
            insights = items,
            topPriority = input.getString("top_priority")
        )
    }
}
