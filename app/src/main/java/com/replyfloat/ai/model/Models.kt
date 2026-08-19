package com.replyfloat.ai.model

data class ReplyRequest(
    val conversationText: String,
    val style: String = "Logical",
    val count: Int = 3,
    val length: String = "balanced",
    val contextApp: String = ""
)

data class ReplySuggestion(
    val id: String,
    val style: String,
    val text: String,
    val tone: String = "",
    val confidence: Float = 0.95f
)

data class AIProviderConfig(
    val id: String = "gemini",
    val name: String = "Google Gemini",
    val type: String = "gemini", // "gemini" | "openai" | "custom"
    val endpoint: String = "",
    val model: String = "gemini-2.5-flash",
    val apiKey: String = "",
    val enabled: Boolean = true,
    val isDefault: Boolean = false
)

enum class ReplyStyle(val title: String, val description: String) {
    LOGICAL("Logical", "Objective, rational, balanced reasoning"),
    CASUAL("Casual", "Relaxed, everyday natural conversational tone"),
    FRIENDLY("Friendly", "Warm, encouraging, and supportive"),
    FORMAL("Formal", "Polite, professional, structured communication"),
    SHORT("Short", "Quick, concise, to the point"),
    DETAILED("Detailed", "Comprehensive, in-depth explanation"),
    FUNNY("Funny", "Witty, humorous, lighthearted punchlines"),
    DEBATE("Debate", "Persuasive, strong rhetorical counter-points"),
    RESPECTFUL("Respectful", "Empathetic, validating, considerate"),
    COUNTERARGUMENT("Counterargument", "Constructive critical challenge to the premise")
}
