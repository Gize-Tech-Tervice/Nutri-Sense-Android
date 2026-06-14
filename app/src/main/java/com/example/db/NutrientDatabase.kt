package com.example.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "food_logs")
data class FoodLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val foodName: String,
    val calories: Double,
    val carbs: Double, // in grams
    val protein: Double, // in grams
    val fat: Double, // in grams
    val sodium: Double, // in milligrams
    val sugar: Double, // in grams
    val fiber: Double, // in grams
    val servingSize: String,
    val timestamp: Long = System.currentTimeMillis(),
    val dateString: String, // YYYY-MM-DD for straightforward daily queries
    
    // Vitamins and minerals
    val vitaminC: Double = 0.0, // in mg
    val vitaminA: Double = 0.0, // in mcg
    val calcium: Double = 0.0, // in mg
    val iron: Double = 0.0, // in mg
    val potassium: Double = 0.0 // in mg
)

@Entity(tableName = "food_database")
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String, // e.g. Fruits, Vegetables, Dairy/Protein, Meats, Grains, Snacks
    val calories: Double,
    val carbs: Double, // in grams
    val protein: Double, // in grams
    val fat: Double, // in grams
    val sodium: Double = 0.0, // in mg
    val sugar: Double = 0.0, // in grams
    val fiber: Double = 0.0, // in grams
    val vitaminC: Double = 0.0, // in mg
    val vitaminA: Double = 0.0, // in mcg
    val calcium: Double = 0.0, // in mg
    val iron: Double = 0.0, // in mg
    val potassium: Double = 0.0, // in mg
    val servingSize: String
)

@Entity(tableName = "daily_targets")
data class DailyTarget(
    @PrimaryKey val id: Int = 1, // Single row config
    val calories: Double = 2000.0,
    val carbs: Double = 250.0,
    val protein: Double = 130.0,
    val fat: Double = 70.0,
    
    // Daily targets for vitamins and minerals
    val vitaminC: Double = 90.0, // mg
    val vitaminA: Double = 900.0, // mcg
    val calcium: Double = 1000.0, // mg
    val iron: Double = 18.0, // mg
    val potassium: Double = 3500.0 // mg
)

@Dao
interface NutrientDao {
    @Query("SELECT * FROM food_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<FoodLog>>

    @Query("SELECT * FROM food_logs WHERE dateString = :date ORDER BY timestamp DESC")
    fun getLogsByDate(date: String): Flow<List<FoodLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: FoodLog)

    @Delete
    suspend fun deleteLog(log: FoodLog)

    @Query("SELECT * FROM daily_targets WHERE id = 1 LIMIT 1")
    fun getDailyTargetFlow(): Flow<DailyTarget?>

    @Query("SELECT * FROM daily_targets WHERE id = 1 LIMIT 1")
    suspend fun getDailyTarget(): DailyTarget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateDailyTarget(target: DailyTarget)

    // Robust Food Database
    @Query("SELECT * FROM food_database ORDER BY name ASC")
    fun getAllFoodItemsFlow(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_database WHERE name LIKE :searchQuery OR category LIKE :searchQuery ORDER BY name ASC")
    fun searchFoodItems(searchQuery: String): Flow<List<FoodItem>>

    @Query("SELECT COUNT(*) FROM food_database")
    suspend fun getFoodItemsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodItem(item: FoodItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodItems(items: List<FoodItem>)
}

@Database(entities = [FoodLog::class, DailyTarget::class, FoodItem::class], version = 2, exportSchema = false)
abstract class NutrientDatabase : RoomDatabase() {
    abstract fun dao(): NutrientDao

    companion object {
        @Volatile
        private var INSTANCE: NutrientDatabase? = null

        fun getDatabase(context: Context): NutrientDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NutrientDatabase::class.java,
                    "nutrient_tracker_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
