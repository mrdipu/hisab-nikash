package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransferEntity::class,
        BalanceAdjustmentEntity::class,
        BudgetEntity::class,
        SavingsGoalEntity::class,
        SavingsAllocationEntity::class,
        RecurringTemplateEntity::class,
        RecurringOccurrenceEntity::class,
        AlertHistoryEntity::class,
        BackupMetadataEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class HisabNikashDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transferDao(): TransferDao
    abstract fun balanceAdjustmentDao(): BalanceAdjustmentDao
    abstract fun budgetDao(): BudgetDao
    abstract fun alertHistoryDao(): AlertHistoryDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun recurringDao(): RecurringDao
    abstract fun backupMetadataDao(): BackupMetadataDao

    companion object {
        const val DATABASE_NAME = "hisabnikash_ledger.db"
        const val CURRENT_SCHEMA_VERSION = 1

        // Explicit non-destructive migration scaffold for future schema upgrades.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reserved for future additive schema changes.
            }
        }

        @Volatile
        private var INSTANCE: HisabNikashDatabase? = null

        fun getInstance(context: Context): HisabNikashDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): HisabNikashDatabase {
            return Room.databaseBuilder(
                appContext,
                HisabNikashDatabase::class.java,
                DATABASE_NAME
            )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            SeedData.seedDefaultCategories(getInstance(appContext).categoryDao())
                        }
                    }
                })
                .build()
        }
    }
}

object SeedData {
    fun defaultCategories(now: Long = System.currentTimeMillis()): List<CategoryEntity> {
        val income = listOf(
            Triple("cat_inc_salary", "Salary", "বেতন") to ("payments" to 0xFF16875DL),
            Triple("cat_inc_business", "Business Income", "ব্যবসায়িক আয়") to ("storefront" to 0xFF123D34L),
            Triple("cat_inc_freelance", "Freelancing", "ফ্রিল্যান্সিং") to ("laptop_mac" to 0xFF064AA0L),
            Triple("cat_inc_bonus", "Bonus", "বোনাস") to ("card_giftcard" to 0xFF4DAF83L),
            Triple("cat_inc_gift", "Gifts", "উপহার") to ("redeem" to 0xFF2E7D32L),
            Triple("cat_inc_invest", "Investment Income", "বিনিয়োগ আয়") to ("trending_up" to 0xFF00838FL),
            Triple("cat_inc_rent", "Rental Income", "ভাড়া থেকে আয়") to ("apartment" to 0xFF558B2FL),
            Triple("cat_inc_other", "Other", "অন্যান্য") to ("category" to 0xFF546E7AL)
        ).mapIndexed { index, (ids, style) ->
            CategoryEntity(
                id = ids.first,
                type = CategoryType.INCOME,
                nameEn = ids.second,
                nameBn = ids.third,
                iconKey = style.first,
                colorHex = style.second,
                isSystem = true,
                sortOrder = index,
                createdAt = now,
                updatedAt = now
            )
        }

        val expense = listOf(
            Triple("cat_exp_food", "Food & Restaurants", "খাবার ও রেস্তোরাঁ") to ("restaurant" to 0xFFD95757L),
            Triple("cat_exp_groceries", "Groceries", "বাজার-সদাই") to ("shopping_basket" to 0xFFE67E22L),
            Triple("cat_exp_shopping", "Shopping", "কেনাকাটা") to ("shopping_bag" to 0xFF8E44ADL),
            Triple("cat_exp_transport", "Transport", "যাতায়াত") to ("directions_bus" to 0xFF064AA0L),
            Triple("cat_exp_housing", "Housing/Rent", "বাসা ভাড়া") to ("home" to 0xFF123D34L),
            Triple("cat_exp_electricity", "Electricity", "বিদ্যুৎ বিল") to ("bolt" to 0xFFF39C12L),
            Triple("cat_exp_gas", "Gas", "গ্যাস বিল") to ("local_fire_department" to 0xFFE47769L),
            Triple("cat_exp_water", "Water", "পানি বিল") to ("water_drop" to 0xFF0288D1L),
            Triple("cat_exp_internet", "Internet", "ইন্টারনেট বিল") to ("wifi" to 0xFF3949ABL),
            Triple("cat_exp_mobile", "Mobile Recharge", "মোবাইল রিচার্জ") to ("smartphone" to 0xFF00897BL),
            Triple("cat_exp_education", "Education", "শিক্ষা") to ("school" to 0xFF5E35B1L),
            Triple("cat_exp_health", "Health", "স্বাস্থ্য ও চিকিৎসা") to ("medical_services" to 0xFFC62828L),
            Triple("cat_exp_entertainment", "Entertainment", "বিনোদন") to ("movie" to 0xFFD81B60L),
            Triple("cat_exp_travel", "Travel", "ভ্রমণ") to ("flight" to 0xFF0097A7L),
            Triple("cat_exp_family", "Family", "পরিবার") to ("family_restroom" to 0xFF6D4C41L),
            Triple("cat_exp_personal", "Personal Care", "ব্যক্তিগত যত্ন") to ("spa" to 0xFF7CB342L),
            Triple("cat_exp_other", "Other", "অন্যান্য") to ("category" to 0xFF546E7AL)
        ).mapIndexed { index, (ids, style) ->
            CategoryEntity(
                id = ids.first,
                type = CategoryType.EXPENSE,
                nameEn = ids.second,
                nameBn = ids.third,
                iconKey = style.first,
                colorHex = style.second,
                isSystem = true,
                sortOrder = index,
                createdAt = now,
                updatedAt = now
            )
        }

        return income + expense
    }

    suspend fun seedDefaultCategories(categoryDao: CategoryDao) {
        val existingIds = categoryDao.getAllIncludingDeleted().map { it.id }.toSet()
        val missing = defaultCategories().filter { it.id !in existingIds }
        if (missing.isNotEmpty()) {
            categoryDao.upsertAll(missing)
        }
    }
}
