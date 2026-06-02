package com.example.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Historical logs of files shared
@Entity(tableName = "transfer_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val fileSize: Long,
    val fileType: String, // String representation of FileType
    val deviceName: String,
    val deviceAvatarIndex: Int,
    val isIncoming: Boolean,
    val timestamp: Long,
    val status: String, // PENDING, IN_PROGRESS, COMPLETED, CANCELLED, FAILED
    val path: String
)

// List of favorite/known devices
@Entity(tableName = "favorite_devices")
data class FavoriteDeviceEntity(
    @PrimaryKey val deviceId: String,
    val name: String,
    val avatarIndex: Int,
    val addedTime: Long = System.currentTimeMillis()
)

// DAO for database methods
@Dao
interface SharingDao {
    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entity: HistoryEntity)

    @Query("DELETE FROM transfer_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Int)

    @Query("DELETE FROM transfer_history")
    suspend fun clearAllHistory()

    // Favorite Device queries
    @Query("SELECT * FROM favorite_devices ORDER BY addedTime DESC")
    fun getAllFavorites(): Flow<List<FavoriteDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(device: FavoriteDeviceEntity)

    @Delete
    suspend fun deleteFavorite(device: FavoriteDeviceEntity)

    @Query("DELETE FROM favorite_devices WHERE deviceId = :deviceId")
    suspend fun deleteFavoriteById(deviceId: String)

    @Query("SELECT EXISTS(SELECT * FROM favorite_devices WHERE deviceId = :deviceId)")
    fun isFavorite(deviceId: String): Flow<Boolean>
}

@Database(entities = [HistoryEntity::class, FavoriteDeviceEntity::class], version = 1, exportSchema = false)
abstract class OfflineSharingDatabase : RoomDatabase() {
    abstract val sharingDao: SharingDao

    companion object {
        @Volatile
        private var INSTANCE: OfflineSharingDatabase? = null

        fun getDatabase(context: Context): OfflineSharingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfflineSharingDatabase::class.java,
                    "offline_sharing_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
