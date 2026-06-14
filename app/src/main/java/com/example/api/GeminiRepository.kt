package com.example.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class GeminiRepository {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val nutritionFactAdapter = moshi.adapter(NutritionFact::class.java)

    // Schema to enforce structured JSON output from Gemini
    private val nutritionSchema = ResponseSchema(
        type = "OBJECT",
        properties = mapOf(
            "foodName" to PropertySchema("STRING", "Descriptive title of the scanned food or label item (e.g. Greek Yogurt, Whole Milk, Pizza Slice)"),
            "calories" to PropertySchema("NUMBER", "Energy/Calories contained in this item (kcal)"),
            "carbs" to PropertySchema("NUMBER", "Total carbohydrates (grams)"),
            "protein" to PropertySchema("NUMBER", "Total protein (grams)"),
            "fat" to PropertySchema("NUMBER", "Total fat (grams)"),
            "sodium" to PropertySchema("NUMBER", "Total sodium (milligrams)"),
            "sugar" to PropertySchema("NUMBER", "Total sugar (grams)"),
            "fiber" to PropertySchema("NUMBER", "Total dietary fiber (grams)"),
            "vitaminC" to PropertySchema("NUMBER", "Vitamin C content (milligrams)"),
            "vitaminA" to PropertySchema("NUMBER", "Vitamin A content (micrograms, mcg)"),
            "calcium" to PropertySchema("NUMBER", "Calcium content (milligrams)"),
            "iron" to PropertySchema("NUMBER", "Iron content (milligrams)"),
            "potassium" to PropertySchema("NUMBER", "Potassium content (milligrams)"),
            "servingSize" to PropertySchema("STRING", "Portion size description or label weight (e.g., 1 Container, 100g, 1 Slice)")
        ),
        required = listOf("foodName", "calories", "carbs", "protein", "fat", "servingSize")
    )

    suspend fun analyzeFoodImage(bitmap: Bitmap, isLabel: Boolean): NutritionFact? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiRepository", "API Key is missing!")
            return@withContext null
        }

        val base64Image = bitmap.toBase64()
        val prompt = if (isLabel) {
            "You are a registered dietitian. Scan this nutrition facts label image and extract the nutrient content exactly as listed. Fit it into the requested JSON schema."
        } else {
            "You are a computer vision nutrition expert. Identify the food item in this image, estimate its size/portion to the best of your ability, and calculate its nutrition. Fit it into the requested JSON schema."
        }

        val request = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = nutritionSchema,
                temperature = 0.4
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                Log.d("GeminiRepository", "Raw Response: $jsonText")
                return@withContext nutritionFactAdapter.fromJson(jsonText)
            }
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Error sending image to Gemini API", e)
        }
        return@withContext null
    }

    suspend fun analyzeFoodText(description: String): NutritionFact? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiRepository", "API Key is missing!")
            return@withContext null
        }

        val prompt = "Estimate the nutrient values for the following food item or meal description: \"$description\". Be as accurate as possible for the described portion and fit it into the requested JSON schema."

        val request = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(text = prompt))
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = nutritionSchema,
                temperature = 0.5
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                Log.d("GeminiRepository", "Raw Response: $jsonText")
                return@withContext nutritionFactAdapter.fromJson(jsonText)
            }
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Error sending text to Gemini API", e)
        }
        return@withContext null
    }

    suspend fun generateRecommendations(
        name: String,
        weight: Double,
        targetWeight: Double,
        age: Int,
        gender: String,
        activity: String,
        todayMeals: List<String>,
        waterIntakeMl: Int
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiRepository", "API Key is missing!")
            return@withContext "API Key is missing or default! Set your GEMINI_API_KEY secure secret to generate live personalized expert tips."
        }

        val mealsStr = if (todayMeals.isEmpty()) "No meals logged yet today." else todayMeals.joinToString(", ")
        val goalStr = if (targetWeight < weight) "Weight Loss / Caloric Deficit" else "Lean Muscle Gain / Active Bulking"
        val prompt = ("" +
            "You are a licensed dietician, certified sports coach, and health expert. " +
            "Provide highly personalized dietary suggestions and expert recommendations for $name based on their fitness attributes:\n" +
            "- Age: $age years\n" +
            "- Gender: $gender\n" +
            "- Current Weight: $weight kg\n" +
            "- Goal Weight: $targetWeight kg\n" +
            "- Fitness Target: $goalStr\n" +
            "- Daily Activity Profile: $activity\n" +
            "- Hydration Progress today: $waterIntakeMl ml (Daily target: 2500 ml)\n" +
            "- Foods Tracked today: $mealsStr\n\n" +
            "Output your specialized assessment response in three succinct and beautiful segments:\n" +
            "1. **Metabolic State & Caloric Thresholds**: Estimate recommended caloric and water limits for this profile context.\n" +
            "2. **Strategic Macronutrient Goals**: Provide 3 high-impact, actionable nutrition tips designed to hit their fitness target.\n" +
            "3. **Recommended Meal Suggestion**: Describe a specific, easy-to-create custom meal or breakfast choice tailored to their $activity lifestyle.\n\n" +
            "Keep the response inspiring, conversational, professional, and structured strictly using clean bullets or simple headings. Do not use markdown headers (# or ##), but you can use double asterisks (**) for premium highlighting.")

        val request = GeminiRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(
                temperature = 0.6
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            return@withContext response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Error generating personalized coaching recommendations from Gemini API", e)
        }
        return@withContext "We experienced trouble contacting the dietary AI advisor right now. Please try again in a moment."
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        // Downscale image to max 1024px to keep bytes small, reducing upload time and potential timeout
        val maxDim = 1024
        val ratio = Math.min(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val finalBitmap = if (ratio < 1.0f) {
            Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
        } else {
            this
        }
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
