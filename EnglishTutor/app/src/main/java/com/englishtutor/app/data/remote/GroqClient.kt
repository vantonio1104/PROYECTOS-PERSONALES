package com.englishtutor.app.data.remote

import com.englishtutor.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object GroqClient {
    private const val BASE_URL = "https://api.groq.com/openai/v1/"

    val json = Json {
        ignoreUnknownKeys = true // Groq devuelve muchos campos que no necesitamos
        encodeDefaults = true    // para que se envíen response_format y temperature
        isLenient = true
    }

    fun create(): GroqApi {
        val okHttp = OkHttpClient.Builder()
            // Agrega "Authorization: Bearer <key>" a cada petición
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    // BASIC: método, URL, código y tiempo (sin la key ni el audio) en Logcat
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
                }
            )
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GroqApi::class.java)
    }
}
