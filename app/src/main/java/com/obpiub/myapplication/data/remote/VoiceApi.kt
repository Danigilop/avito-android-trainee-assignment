package com.obpiub.myapplication.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

interface TaskGenerationApi {
    @POST("v1/chat/completions")
    suspend fun complete(@Header("Authorization") token: String, @Body request: ChatRequest): ChatDto
}

data class ChatMessage(val role: String, val content: String)
data class ChatRequest(
    val messages: List<ChatMessage>,
    val model: String = "GigaChat-2",
    val stream: Boolean = false,
    @SerializedName("max_tokens") val maxTokens: Int = 256,
)
data class ChatDto(val choices: List<ChatChoice>?)
data class ChatChoice(val message: ChatAnswer?, @SerializedName("finish_reason") val finishReason: String?)
data class ChatAnswer(val content: String?)
