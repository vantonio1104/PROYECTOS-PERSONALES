package com.englishtutor.app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/** Endpoints de Groq que usamos. Base URL: https://api.groq.com/openai/v1/ */
interface GroqApi {

    @Multipart
    @POST("audio/transcriptions")
    suspend fun transcribe(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("language") language: RequestBody,
    ): TranscriptionResponse

    @POST("chat/completions")
    suspend fun chat(@Body request: ChatRequest): ChatResponse

    /** Devuelve el audio .wav en bruto. */
    @POST("audio/speech")
    suspend fun speech(@Body request: SpeechRequest): ResponseBody
}
