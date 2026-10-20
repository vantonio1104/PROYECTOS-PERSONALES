package com.englishtutor.app.data.remote

import kotlinx.serialization.Serializable

// ---- Transcripción (Whisper) ----
@Serializable
data class TranscriptionResponse(val text: String)

// ---- Chat completions (formato compatible con OpenAI) ----
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val response_format: ResponseFormat = ResponseFormat("json_object"),
    val temperature: Double = 0.7,
)

@Serializable
data class ApiMessage(val role: String, val content: String)

@Serializable
data class ResponseFormat(val type: String)

@Serializable
data class ChatResponse(val choices: List<Choice>)

@Serializable
data class Choice(val message: ApiMessage)

// ---- Texto a voz (Orpheus) ----
@Serializable
data class SpeechRequest(
    val model: String,
    val voice: String,
    val input: String,
    val response_format: String = "wav", // Orpheus solo acepta wav
)
