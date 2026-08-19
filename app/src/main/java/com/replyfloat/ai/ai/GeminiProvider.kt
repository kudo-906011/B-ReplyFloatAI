package com.replyfloat.ai.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.replyfloat.ai.model.AIProviderConfig
import com.replyfloat.ai.model.ReplyRequest
import com.replyfloat.ai.model.ReplySuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiProvider(
    private val config: AIProviderConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) : AIProvider {

    override val id: String = config.id
    override val name: String = config.name

    override suspend fun generateReplies(request: ReplyRequest): Result<List<ReplySuggestion>> =
        withContext(Dispatchers.IO) {
            try {
                val apiKey = config.apiKey
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Gemini API key is missing."))
                }

                val model = config.model.ifBlank { "gemini-2.5-flash" }
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

                val systemPrompt = """
                    You are ReplyFloat AI, an Android floating reply assistant.
                    Generate ${request.count} distinct reply suggestions in the style '${request.style}'.
                    Length: ${request.length}.
                    Return JSON: {"replies": [{"style": "${request.style}", "text": "...", "tone": "..."}]}
                """.trimIndent()

                val payload = JsonObject().apply {
                    val contentsArr = com.google.gson.JsonArray().apply {
                        val partObj = JsonObject().apply {
                            val partsArr = com.google.gson.JsonArray().apply {
                                add(JsonObject().apply {
                                    addProperty("text", "Context: ${request.conversationText}")
                                })
                            }
                            add("parts", partsArr)
                        }
                        add(partObj)
                    }
                    add("contents", contentsArr)
                    
                    val systemInstructionObj = JsonObject().apply {
                        val partsArr = com.google.gson.JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", systemPrompt) })
                        }
                        add("parts", partsArr)
                    }
                    add("systemInstruction", systemInstructionObj)
                }

                val body = payload.toString().toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder().url(url).post(body).build()

                val response = client.newCall(httpRequest).execute()
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: "HTTP error ${response.code}"
                    return@withContext Result.failure(Exception("Gemini API Error (${response.code}): $err"))
                }

                val resBody = response.body?.string() ?: ""
                val rootJson = gson.fromJson(resBody, JsonObject::class.java)
                val candidates = rootJson.getAsJsonArray("candidates")
                val firstCandidate = candidates?.get(0)?.asJsonObject
                val content = firstCandidate?.getAsJsonObject("content")
                val parts = content?.getAsJsonArray("parts")
                val text = parts?.get(0)?.asJsonObject?.get("text")?.asString ?: ""

                val suggestions = parseSuggestions(text, request.style)
                Result.success(suggestions)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun parseSuggestions(rawText: String, defaultStyle: String): List<ReplySuggestion> {
        val list = mutableListOf<ReplySuggestion>()
        try {
            val jsonRegex = Regex("""\{.*\}""", RegexOption.DOT_MATCHES_ALL)
            val match = jsonRegex.find(rawText)?.value
            if (match != null) {
                val json = gson.fromJson(match, JsonObject::class.java)
                val arr = json.getAsJsonArray("replies")
                arr?.forEachIndexed { index, item ->
                    val obj = item.asJsonObject
                    list.add(
                        ReplySuggestion(
                            id = index.toString(),
                            style = obj.get("style")?.asString ?: defaultStyle,
                            text = obj.get("text")?.asString ?: "",
                            tone = obj.get("tone")?.asString ?: defaultStyle
                        )
                    )
                }
            }
        } catch (e: Exception) {
            rawText.lines().filter { it.isNotBlank() }.take(3).forEachIndexed { index, line ->
                list.add(
                    ReplySuggestion(
                        id = index.toString(),
                        style = defaultStyle,
                        text = line.replace(Regex("""^\d+[\.\)]\s*"""), "").trim('"', ' '),
                        tone = defaultStyle
                    )
                )
            }
        }
        return list
    }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        val dummy = ReplyRequest(
            conversationText = "Hello",
            style = "Short",
            count = 1,
            length = "short"
        )
        generateReplies(dummy).map { true }
    }
}
