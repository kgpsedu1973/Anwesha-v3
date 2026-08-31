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
        StudentDocumentEntity::class,
        CustomFieldEntity::class,
        FormulaRuleEntity::class,
        AttendanceEntity::class,
        RoutineItemEntity::class,
        DocumentTemplateEntity::class,
        SchoolInfoEntity::class,
        UserEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun studentDocumentDao(): StudentDocumentDao
    abstract fun customFieldDao(): CustomFieldDao
    abstract fun formulaRuleDao(): FormulaRuleDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun routineDao(): RoutineDao
    abstract fun documentTemplateDao(): DocumentTemplateDao
    abstract fun schoolInfoDao(): SchoolInfoDao
    abstract fun userDao(): UserDao


    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val backupManager = com.example.util.InternalAutoBackupManager.getInstance(appContext)

                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "anwesha_school_db"
                )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // When database is created, only auto-restore if user previously had a completed setup snapshot
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    if (backupManager.isInitialSetupCompleted()) {
                                        backupManager.restorePersistentSnapshotIfEmpty(database)
                                    }
                                }
                            }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    // Safeguard: Check if tables are unexpectedly empty and restore only if setup was previously completed
                                    if (backupManager.isInitialSetupCompleted()) {
                                        backupManager.restorePersistentSnapshotIfEmpty(database)
                                    }
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

        fun resetDatabaseInstance() {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                INSTANCE = null
            }
        }
    }
}
