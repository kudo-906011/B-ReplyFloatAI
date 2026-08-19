package com.replyfloat.ai.ai

import com.replyfloat.ai.model.ReplyRequest
import com.replyfloat.ai.model.ReplySuggestion

interface AIProvider {
    val id: String
    val name: String
    suspend fun generateReplies(request: ReplyRequest): Result<List<ReplySuggestion>>
    suspend fun testConnection(): Result<Boolean>
}
