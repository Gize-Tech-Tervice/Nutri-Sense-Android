package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Request Models ---

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: ResponseSchema? = null,
    val temperature: Double? = null
)

@JsonClass(generateAdapter = true)
data class ResponseSchema(
    val type: String,
    val properties: Map<String, PropertySchema>,
    val required: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class PropertySchema(
    val type: String,
    val description: String? = null
)

// --- Response Models ---

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: ResponseContent? = null
)

@JsonClass(generateAdapter = true)
data class ResponseContent(
    val parts: List<ResponsePart>? = null
)

@JsonClass(generateAdapter = true)
data class ResponsePart(
    val text: String? = null
)

// --- Extracted Nutrient Model ---

@JsonClass(generateAdapter = true)
data class NutritionFact(
    val foodName: String,
    val calories: Double,
    val carbs: Double,
    val protein: Double,
    val fat: Double,
    val sodium: Double = 0.0,
    val sugar: Double = 0.0,
    val fiber: Double = 0.0,
    val vitaminC: Double = 0.0,
    val vitaminA: Double = 0.0,
    val calcium: Double = 0.0,
    val iron: Double = 0.0,
    val potassium: Double = 0.0,
    val servingSize: String
)

// --- Retrofit Setup ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val moshi = com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}
