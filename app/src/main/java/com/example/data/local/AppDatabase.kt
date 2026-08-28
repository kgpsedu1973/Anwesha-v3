package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.*
import com.example.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        StudentEntity::class,
        CustomFieldEntity::class,
        FormulaRuleEntity::class,
        AttendanceEntity::class,
        RoutineItemEntity::class,
        DocumentTemplateEntity::class,
        SurveyEntity::class,
        SchoolInfoEntity::class,
        UserEntity::class,
        ExamResultEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun customFieldDao(): CustomFieldDao
    abstract fun formulaRuleDao(): FormulaRuleDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun routineDao(): RoutineDao
    abstract fun documentTemplateDao(): DocumentTemplateDao
    abstract fun surveyDao(): SurveyDao
    abstract fun schoolInfoDao(): SchoolInfoDao
    abstract fun userDao(): UserDao
    abstract fun examResultDao(): ExamResultDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "anwesha_school_db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Seed initial database in background coroutine
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    populateInitialData(database)
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun populateInitialData(db: AppDatabase) {
            SampleData.seedDatabase(db)
        }
    }
}
