package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiRepository
import com.example.api.NutritionFact
import com.example.db.DailyTarget
import com.example.db.FoodLog
import com.example.db.FoodItem
import com.example.db.NutrientDatabase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class NutrientViewModel(application: Application) : AndroidViewModel(application) {

    private val db = NutrientDatabase.getDatabase(application)
    private val dao = db.dao()
    private val geminiRepository = GeminiRepository()

    // Active tracking date (YYYY-MM-DD)
    private val _selectedDate = MutableStateFlow(getCurrentDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Splash, Onboarding & Auth States
    private val _isSplashActive = MutableStateFlow(true)
    val isSplashActive: StateFlow<Boolean> = _isSplashActive.asStateFlow()

    private val _isOnboardingCompleted = MutableStateFlow(false)
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // SignUp / Login Fields
    private val _userProfileName = MutableStateFlow("Emily Carter")
    val userProfileName: StateFlow<String> = _userProfileName.asStateFlow()

    private val _userProfileEmail = MutableStateFlow("emily.carter@nutrisense.com")
    val userProfileEmail: StateFlow<String> = _userProfileEmail.asStateFlow()

    // User Profile Health Metrics
    private val _userProfileWeight = MutableStateFlow(68.5) // kg
    val userProfileWeight: StateFlow<Double> = _userProfileWeight.asStateFlow()

    private val _userProfileTargetWeight = MutableStateFlow(62.0) // kg
    val userProfileTargetWeight: StateFlow<Double> = _userProfileTargetWeight.asStateFlow()

    private val _userProfileGender = MutableStateFlow("Female")
    val userProfileGender: StateFlow<String> = _userProfileGender.asStateFlow()

    private val _userProfileAge = MutableStateFlow(26)
    val userProfileAge: StateFlow<Int> = _userProfileAge.asStateFlow()

    private val _userProfileActivity = MutableStateFlow("Moderately Active")
    val userProfileActivity: StateFlow<String> = _userProfileActivity.asStateFlow()

    // Water Intake state (ml)
    private val _waterIntake = MutableStateFlow(1250) // start with some water logged in
    val waterIntake: StateFlow<Int> = _waterIntake.asStateFlow()
    val waterTarget = 2500 // ml

    // Daily smart reminders
    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    fun setSplashActive(active: Boolean) {
        _isSplashActive.value = active
    }

    fun setOnboardingCompleted(completed: Boolean) {
        _isOnboardingCompleted.value = completed
    }

    fun setLoggedIn(loggedIn: Boolean) {
        _isLoggedIn.value = loggedIn
    }

    fun registerUser(name: String, email: String) {
        _userProfileName.value = name.ifBlank { "User" }
        _userProfileEmail.value = email.ifBlank { "user@nutrisense.com" }
        _isLoggedIn.value = true
    }

    fun updateProfile(name: String, email: String, weight: Double, target: Double, gender: String, age: Int, activity: String) {
        _userProfileName.value = name
        _userProfileEmail.value = email
        _userProfileWeight.value = weight
        _userProfileTargetWeight.value = target
        _userProfileGender.value = gender
        _userProfileAge.value = age
        _userProfileActivity.value = activity
    }

    fun addWater(amount: Int) {
        _waterIntake.value = (_waterIntake.value + amount).coerceAtLeast(0)
    }

    fun resetWater() {
        _waterIntake.value = 0
    }

    fun toggleNotifications() {
        _notificationsEnabled.value = !_notificationsEnabled.value
    }

    // Logs for the active date
    val foodLogsForSelectedDate: StateFlow<List<FoodLog>> = _selectedDate
        .flatMapLatest { date ->
            dao.getLogsByDate(date)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All logs across all time (for historical view)
    val allFoodLogs: StateFlow<List<FoodLog>> = dao.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Daily target configuration
    val dailyTarget: StateFlow<DailyTarget> = dao.getDailyTargetFlow()
        .map { it ?: DailyTarget() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyTarget())

    // Local embedded food database search
    private val _dbSearchQuery = MutableStateFlow("")
    val dbSearchQuery: StateFlow<String> = _dbSearchQuery.asStateFlow()

    val searchResults: StateFlow<List<FoodItem>> = _dbSearchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                dao.getAllFoodItemsFlow()
            } else {
                dao.searchFoodItems("%$query%")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Scanning / Analysis states
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _scanResult = MutableStateFlow<NutritionFact?>(null)
    val scanResult: StateFlow<NutritionFact?> = _scanResult.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    init {
        // Hydrate default goal values if empty
        viewModelScope.launch {
            if (dao.getDailyTarget() == null) {
                dao.updateDailyTarget(DailyTarget())
            }
            // Populate robust food database of 25 items if empty
            if (dao.getFoodItemsCount() == 0) {
                populatePredefinedFoods()
            }
        }
    }

    private suspend fun populatePredefinedFoods() {
        Log.d("NutrientViewModel", "Pre-populating robust foods database...")
        val items = listOf(
            FoodItem(
                name = "Red Apple",
                category = "Fruits",
                calories = 52.0,
                carbs = 14.0,
                protein = 0.3,
                fat = 0.2,
                sodium = 1.0,
                sugar = 10.0,
                fiber = 2.4,
                vitaminC = 4.6,
                vitaminA = 3.0,
                calcium = 6.0,
                iron = 0.1,
                potassium = 107.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Banana",
                category = "Fruits",
                calories = 89.0,
                carbs = 23.0,
                protein = 1.1,
                fat = 0.3,
                sodium = 1.0,
                sugar = 12.0,
                fiber = 2.6,
                vitaminC = 8.7,
                vitaminA = 3.0,
                calcium = 5.0,
                iron = 0.3,
                potassium = 358.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Orange",
                category = "Fruits",
                calories = 47.0,
                carbs = 12.0,
                protein = 0.9,
                fat = 0.1,
                sodium = 0.0,
                sugar = 9.0,
                fiber = 2.4,
                vitaminC = 53.2,
                vitaminA = 11.0,
                calcium = 40.0,
                iron = 0.1,
                potassium = 181.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Broccoli",
                category = "Vegetables",
                calories = 34.0,
                carbs = 7.0,
                protein = 2.8,
                fat = 0.4,
                sodium = 33.0,
                sugar = 1.7,
                fiber = 2.6,
                vitaminC = 89.2,
                vitaminA = 31.0,
                calcium = 47.0,
                iron = 0.7,
                potassium = 316.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Spinach",
                category = "Vegetables",
                calories = 23.0,
                carbs = 3.6,
                protein = 2.9,
                fat = 0.4,
                sodium = 79.0,
                sugar = 0.4,
                fiber = 2.2,
                vitaminC = 28.1,
                vitaminA = 469.0,
                calcium = 99.0,
                iron = 2.7,
                potassium = 558.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Avocado",
                category = "Vegetables",
                calories = 160.0,
                carbs = 8.5,
                protein = 2.0,
                fat = 15.0,
                sodium = 7.0,
                sugar = 0.7,
                fiber = 6.7,
                vitaminC = 10.0,
                vitaminA = 7.0,
                calcium = 12.0,
                iron = 0.6,
                potassium = 485.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Chicken Breast",
                category = "Meats",
                calories = 165.0,
                carbs = 0.0,
                protein = 31.0,
                fat = 3.6,
                sodium = 74.0,
                sugar = 0.0,
                fiber = 0.0,
                vitaminC = 0.0,
                vitaminA = 5.0,
                calcium = 15.0,
                iron = 1.0,
                potassium = 256.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Salmon Fillet",
                category = "Meats",
                calories = 208.0,
                carbs = 0.0,
                protein = 20.0,
                fat = 13.0,
                sodium = 59.0,
                sugar = 0.0,
                fiber = 0.0,
                vitaminC = 0.0,
                vitaminA = 12.0,
                calcium = 9.0,
                iron = 0.3,
                potassium = 363.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Beef Steak",
                category = "Meats",
                calories = 250.0,
                carbs = 0.0,
                protein = 26.0,
                fat = 15.0,
                sodium = 54.0,
                sugar = 0.0,
                fiber = 0.0,
                vitaminC = 0.0,
                vitaminA = 0.0,
                calcium = 12.0,
                iron = 2.4,
                potassium = 318.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Whole Chicken Egg",
                category = "Dairy/Protein",
                calories = 78.0,
                carbs = 0.6,
                protein = 6.0,
                fat = 5.0,
                sodium = 62.0,
                sugar = 0.5,
                fiber = 0.0,
                vitaminC = 0.0,
                vitaminA = 80.0,
                calcium = 25.0,
                iron = 0.6,
                potassium = 63.0,
                servingSize = "1 Egg (50g)"
            ),
            FoodItem(
                name = "Greek Yogurt",
                category = "Dairy/Protein",
                calories = 59.0,
                carbs = 3.6,
                protein = 10.0,
                fat = 0.4,
                sodium = 36.0,
                sugar = 3.2,
                fiber = 0.0,
                vitaminC = 0.0,
                vitaminA = 0.0,
                calcium = 110.0,
                iron = 0.1,
                potassium = 141.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Whole Milk",
                category = "Dairy/Protein",
                calories = 61.0,
                carbs = 4.8,
                protein = 3.2,
                fat = 3.3,
                sodium = 44.0,
                sugar = 5.0,
                fiber = 0.0,
                vitaminC = 0.0,
                vitaminA = 46.0,
                calcium = 113.0,
                iron = 0.1,
                potassium = 143.0,
                servingSize = "100ml"
            ),
            FoodItem(
                name = "White Rice",
                category = "Grains",
                calories = 130.0,
                carbs = 28.0,
                protein = 2.7,
                fat = 0.3,
                sodium = 1.0,
                sugar = 0.0,
                fiber = 0.4,
                vitaminC = 0.0,
                vitaminA = 0.0,
                calcium = 10.0,
                iron = 0.2,
                potassium = 35.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Whole Wheat Bread",
                category = "Grains",
                calories = 69.0,
                carbs = 12.0,
                protein = 3.6,
                fat = 0.9,
                sodium = 130.0,
                sugar = 1.4,
                fiber = 1.9,
                vitaminC = 0.0,
                vitaminA = 0.0,
                calcium = 26.0,
                iron = 0.7,
                potassium = 70.0,
                servingSize = "1 Slice (28g)"
            ),
            FoodItem(
                name = "Oatmeal",
                category = "Grains",
                calories = 68.0,
                carbs = 12.0,
                protein = 2.4,
                fat = 1.4,
                sodium = 2.0,
                sugar = 0.3,
                fiber = 1.7,
                vitaminC = 0.0,
                vitaminA = 0.0,
                calcium = 8.0,
                iron = 0.6,
                potassium = 61.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Sweet Potato",
                category = "Vegetables",
                calories = 86.0,
                carbs = 20.0,
                protein = 1.6,
                fat = 0.1,
                sodium = 55.0,
                sugar = 4.2,
                fiber = 3.0,
                vitaminC = 2.4,
                vitaminA = 709.0,
                calcium = 30.0,
                iron = 0.6,
                potassium = 337.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Cheddar Cheese",
                category = "Dairy/Protein",
                calories = 403.0,
                carbs = 1.3,
                protein = 25.0,
                fat = 33.0,
                sodium = 621.0,
                sugar = 0.5,
                fiber = 0.0,
                vitaminC = 0.0,
                vitaminA = 265.0,
                calcium = 721.0,
                iron = 0.1,
                potassium = 98.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Almonds",
                category = "Nuts/Seeds",
                calories = 579.0,
                carbs = 22.0,
                protein = 21.0,
                fat = 49.0,
                sodium = 1.0,
                sugar = 4.3,
                fiber = 12.0,
                vitaminC = 0.0,
                vitaminA = 0.0,
                calcium = 269.0,
                iron = 3.7,
                potassium = 733.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Tofu",
                category = "Dairy/Protein",
                calories = 76.0,
                carbs = 1.9,
                protein = 8.0,
                fat = 4.8,
                sodium = 7.0,
                sugar = 0.5,
                fiber = 0.3,
                vitaminC = 0.1,
                vitaminA = 0.0,
                calcium = 350.0,
                iron = 5.4,
                potassium = 121.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Potato",
                category = "Vegetables",
                calories = 77.0,
                carbs = 17.0,
                protein = 2.0,
                fat = 0.1,
                sodium = 6.0,
                sugar = 0.8,
                fiber = 2.2,
                vitaminC = 19.7,
                vitaminA = 1.0,
                calcium = 12.0,
                iron = 0.8,
                potassium = 421.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Peanut Butter",
                category = "Nuts/Seeds",
                calories = 588.0,
                carbs = 20.0,
                protein = 25.0,
                fat = 50.0,
                sodium = 429.0,
                sugar = 9.0,
                fiber = 6.0,
                vitaminC = 0.0,
                vitaminA = 0.0,
                calcium = 43.0,
                iron = 1.9,
                potassium = 649.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Blueberries",
                category = "Fruits",
                calories = 57.0,
                carbs = 14.0,
                protein = 0.7,
                fat = 0.3,
                sodium = 1.0,
                sugar = 10.0,
                fiber = 2.4,
                vitaminC = 9.7,
                vitaminA = 3.0,
                calcium = 6.0,
                iron = 0.3,
                potassium = 77.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Tomato",
                category = "Vegetables",
                calories = 18.0,
                carbs = 3.9,
                protein = 0.9,
                fat = 0.2,
                sodium = 5.0,
                sugar = 2.6,
                fiber = 1.2,
                vitaminC = 13.7,
                vitaminA = 42.0,
                calcium = 10.0,
                iron = 0.3,
                potassium = 237.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Carrot",
                category = "Vegetables",
                calories = 41.0,
                carbs = 10.0,
                protein = 0.9,
                fat = 0.2,
                sodium = 69.0,
                sugar = 4.7,
                fiber = 2.8,
                vitaminC = 5.9,
                vitaminA = 835.0,
                calcium = 33.0,
                iron = 0.3,
                potassium = 320.0,
                servingSize = "100g"
            ),
            FoodItem(
                name = "Greek Salad",
                category = "Salads",
                calories = 110.0,
                carbs = 5.0,
                protein = 2.5,
                fat = 9.0,
                sodium = 280.0,
                sugar = 2.0,
                fiber = 1.0,
                vitaminC = 6.0,
                vitaminA = 25.0,
                calcium = 70.0,
                iron = 0.5,
                potassium = 160.0,
                servingSize = "100g"
            )
        )
        dao.insertFoodItems(items)
    }

    fun setSelectedDate(dateString: String) {
        _selectedDate.value = dateString
    }

    fun clearResult() {
        _scanResult.value = null
        _scanError.value = null
    }

    fun setSearchQuery(query: String) {
        _dbSearchQuery.value = query
    }

    // Capture visual meal or label scanning
    fun scanImage(bitmap: Bitmap, isLabel: Boolean) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _scanError.value = null
            _scanResult.value = null
            try {
                val result = geminiRepository.analyzeFoodImage(bitmap, isLabel)
                if (result != null) {
                    _scanResult.value = result
                } else {
                    _scanError.value = "Failed to extract nutrients. Make sure the food or label is visible and bright."
                }
            } catch (e: Exception) {
                Log.e("NutrientViewModel", "Scan error", e)
                _scanError.value = "Scanning failed: ${e.localizedMessage}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    // Text estimation scan
    fun analyzeTextDescription(description: String) {
        if (description.isBlank()) return
        viewModelScope.launch {
            _isAnalyzing.value = true
            _scanError.value = null
            _scanResult.value = null
            try {
                val result = geminiRepository.analyzeFoodText(description)
                if (result != null) {
                    _scanResult.value = result
                } else {
                    _scanError.value = "Could not estimate nutrition for \"$description\"."
                }
            } catch (e: Exception) {
                Log.e("NutrientViewModel", "Text estimate error", e)
                _scanError.value = "Analysis failed: ${e.localizedMessage}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    // Save active preview result to SQLite DB
    fun saveScanResult(customPortionMultiplier: Double = 1.0) {
        val currentFact = _scanResult.value ?: return
        viewModelScope.launch {
            val log = FoodLog(
                foodName = currentFact.foodName,
                calories = currentFact.calories * customPortionMultiplier,
                carbs = currentFact.carbs * customPortionMultiplier,
                protein = currentFact.protein * customPortionMultiplier,
                fat = currentFact.fat * customPortionMultiplier,
                sodium = currentFact.sodium * customPortionMultiplier,
                sugar = currentFact.sugar * customPortionMultiplier,
                fiber = currentFact.fiber * customPortionMultiplier,
                servingSize = if (customPortionMultiplier == 1.0) currentFact.servingSize else "${String.format(Locale.US, "%.1f", customPortionMultiplier)}x ${currentFact.servingSize}",
                dateString = _selectedDate.value,
                vitaminC = currentFact.vitaminC * customPortionMultiplier,
                vitaminA = currentFact.vitaminA * customPortionMultiplier,
                calcium = currentFact.calcium * customPortionMultiplier,
                iron = currentFact.iron * customPortionMultiplier,
                potassium = currentFact.potassium * customPortionMultiplier
            )
            dao.insertLog(log)
            clearResult()
        }
    }

    // Direct manual log entry
    fun logManualFood(
        name: String,
        kcal: Double,
        carbs: Double,
        protein: Double,
        fat: Double,
        sodium: Double = 0.0,
        sugar: Double = 0.0,
        fiber: Double = 0.0,
        vitaminC: Double = 0.0,
        vitaminA: Double = 0.0,
        calcium: Double = 0.0,
        iron: Double = 0.0,
        potassium: Double = 0.0,
        serving: String
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val log = FoodLog(
                foodName = name,
                calories = kcal,
                carbs = carbs,
                protein = protein,
                fat = fat,
                sodium = sodium,
                sugar = sugar,
                fiber = fiber,
                servingSize = serving,
                dateString = _selectedDate.value,
                vitaminC = vitaminC,
                vitaminA = vitaminA,
                calcium = calcium,
                iron = iron,
                potassium = potassium
            )
            dao.insertLog(log)
        }
    }

    // Log predefined FoodItem directly
    fun logFoodItem(item: FoodItem, multiplier: Double = 1.0) {
        viewModelScope.launch {
            val log = FoodLog(
                foodName = item.name,
                calories = item.calories * multiplier,
                carbs = item.carbs * multiplier,
                protein = item.protein * multiplier,
                fat = item.fat * multiplier,
                sodium = item.sodium * multiplier,
                sugar = item.sugar * multiplier,
                fiber = item.fiber * multiplier,
                servingSize = if (multiplier == 1.0) item.servingSize else "${String.format(Locale.US, "%.1f", multiplier)}x ${item.servingSize}",
                dateString = _selectedDate.value,
                vitaminC = item.vitaminC * multiplier,
                vitaminA = item.vitaminA * multiplier,
                calcium = item.calcium * multiplier,
                iron = item.iron * multiplier,
                potassium = item.potassium * multiplier
            )
            dao.insertLog(log)
        }
    }

    // Add new FoodItem to the custom Food Items database dictionary
    fun addNewDatabaseFoodItem(
        name: String,
        category: String,
        kcal: Double,
        carbs: Double,
        protein: Double,
        fat: Double,
        sodium: Double = 0.0,
        sugar: Double = 0.0,
        fiber: Double = 0.0,
        vitaminC: Double = 0.0,
        vitaminA: Double = 0.0,
        calcium: Double = 0.0,
        iron: Double = 0.0,
        potassium: Double = 0.0,
        serving: String
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newItem = FoodItem(
                name = name,
                category = category,
                calories = kcal,
                carbs = carbs,
                protein = protein,
                fat = fat,
                sodium = sodium,
                sugar = sugar,
                fiber = fiber,
                vitaminC = vitaminC,
                vitaminA = vitaminA,
                calcium = calcium,
                iron = iron,
                potassium = potassium,
                servingSize = serving
            )
            dao.insertFoodItem(newItem)
        }
    }

    fun deleteFoodLog(log: FoodLog) {
        viewModelScope.launch {
            dao.deleteLog(log)
        }
    }

    fun updateDailyGoals(
        calories: Double, 
        carbs: Double, 
        protein: Double, 
        fat: Double,
        vitaminC: Double = 90.0,
        vitaminA: Double = 900.0,
        calcium: Double = 1000.0,
        iron: Double = 18.0,
        potassium: Double = 3500.0
    ) {
        viewModelScope.launch {
            dao.updateDailyTarget(
                DailyTarget(
                    calories = calories,
                    carbs = carbs,
                    protein = protein,
                    fat = fat,
                    vitaminC = vitaminC,
                    vitaminA = vitaminA,
                    calcium = calcium,
                    iron = iron,
                    potassium = potassium
                )
            )
        }
    }

    companion object {
        fun getCurrentDateString(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return sdf.format(Date())
        }
    }
}
