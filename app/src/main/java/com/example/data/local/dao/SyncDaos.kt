package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.AuthorizedUserEntity
import com.example.data.local.entity.BackupHistoryEntity
import com.example.data.local.entity.SyncConflictEntity
import com.example.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY clientTimestamp ASC")
    fun getPendingQueue(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY clientTimestamp ASC")
    suspend fun getPendingQueueList(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueAll(items: List<SyncQueueEntity>)

    @Update
    suspend fun update(item: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE queueId = :queueId")
    suspend fun delete(queueId: String)

    @Query("DELETE FROM sync_queue WHERE status = 'SYNCED'")
    suspend fun purgeSynced()

    @Query("DELETE FROM sync_queue")
    suspend fun clearQueue()
}

@Dao
interface SyncConflictDao {
    @Query("SELECT * FROM sync_conflicts ORDER BY timestamp DESC")
    fun getAllConflicts(): Flow<List<SyncConflictEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(conflict: SyncConflictEntity)

    @Update
    suspend fun updateConflict(conflict: SyncConflictEntity)

    @Query("DELETE FROM sync_conflicts WHERE id = :id")
    suspend fun deleteConflict(id: String)

    @Query("DELETE FROM sync_conflicts")
    suspend fun clearAllConflicts()
}

@Dao
interface AuthorizedUserDao {
    @Query("SELECT * FROM authorized_users ORDER BY role ASC, displayName ASC")
    fun getAllAuthorizedUsers(): Flow<List<AuthorizedUserEntity>>

    @Query("SELECT * FROM authorized_users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserByEmail(email: String): AuthorizedUserEntity?

    @Query("SELECT * FROM authorized_users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    fun observeUserByEmail(email: String): Flow<AuthorizedUserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuthorizedUser(user: AuthorizedUserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllAuthorizedUsers(users: List<AuthorizedUserEntity>)

    @Delete
    suspend fun deleteAuthorizedUser(user: AuthorizedUserEntity)

    @Query("DELETE FROM authorized_users WHERE LOWER(email) = LOWER(:email)")
    suspend fun deleteByEmail(email: String)

    @Query("DELETE FROM authorized_users")
    suspend fun deleteAll()
}

@Dao
interface BackupHistoryDao {
    @Query("SELECT * FROM backup_history ORDER BY timestamp DESC")
    fun getAllBackups(): Flow<List<BackupHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackup(backup: BackupHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(backups: List<BackupHistoryEntity>)

    @Query("DELETE FROM backup_history WHERE backupId = :backupId")
    suspend fun deleteBackup(backupId: String)

    @Query("DELETE FROM backup_history")
    suspend fun clearAll()
}
