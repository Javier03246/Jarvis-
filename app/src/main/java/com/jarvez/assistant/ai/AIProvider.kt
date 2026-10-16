package com.jarvez.assistant.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = false)
data class Part(
    @param:Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = false)
data class Content(
    @param:Json(name = "parts") val parts: List<Part>? = null,
    @param:Json(name = "role") val role: String? = null
)

@JsonClass(generateAdapter = false)
data class GenerationConfig(
    @param:Json(name = "temperature") val temperature: Float? = 0.4f,
    @param:Json(name = "topP") val topP: Float? = 0.95f,
    @param:Json(name = "topK") val topK: Int? = 40,
    @param:Json(name = "maxOutputTokens") val maxOutputTokens: Int? = 1024
)

@JsonClass(generateAdapter = false)
data class GenerateContentRequest(
    @param:Json(name = "contents") val contents: List<Content>,
    @param:Json(name = "systemInstruction") val systemInstruction: Content? = null,
    @param:Json(name = "generationConfig") val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = false)
data class Candidate(
    @param:Json(name = "content") val content: Content? = null,
    @param:Json(name = "finishReason") val finishReason: String? = null
)

@JsonClass(generateAdapter = false)
data class GenerateContentResponse(
    @param:Json(name = "candidates") val candidates: List<Candidate>? = null
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}
