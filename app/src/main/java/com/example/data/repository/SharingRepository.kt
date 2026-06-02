package com.example.data.repository

import android.content.Context
import android.os.Environment
import com.example.data.db.FavoriteDeviceEntity
import com.example.data.db.HistoryEntity
import com.example.data.db.SharingDao
import com.example.model.FileType
import com.example.model.ShareableFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class SharingRepository(
    private val context: Context,
    private val dao: SharingDao
) {
    val allHistory: Flow<List<HistoryEntity>> = dao.getAllHistory()
    val favoriteDevices: Flow<List<FavoriteDeviceEntity>> = dao.getAllFavorites()

    suspend fun saveTransferToHistory(
        fileName: String,
        fileSize: Long,
        fileType: FileType,
        deviceName: String,
        deviceAvatarIndex: Int,
        isIncoming: Boolean,
        status: String,
        path: String
    ) = withContext(Dispatchers.IO) {
        dao.insertHistory(
            HistoryEntity(
                fileName = fileName,
                fileSize = fileSize,
                fileType = fileType.name,
                deviceName = deviceName,
                deviceAvatarIndex = deviceAvatarIndex,
                isIncoming = isIncoming,
                timestamp = System.currentTimeMillis(),
                status = status,
                path = path
            )
        )
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.clearAllHistory()
    }

    suspend fun deleteHistoryById(id: Int) = withContext(Dispatchers.IO) {
        dao.deleteHistoryById(id)
    }

    // Favorite/unfavorite toggles
    suspend fun addFavoriteDevice(id: String, name: String, avatarIndex: Int) = withContext(Dispatchers.IO) {
        dao.insertFavorite(
            FavoriteDeviceEntity(deviceId = id, name = name, avatarIndex = avatarIndex)
        )
    }

    suspend fun removeFavoriteDevice(id: String) = withContext(Dispatchers.IO) {
        dao.deleteFavoriteById(id)
    }

    fun isFavorite(deviceId: String): Flow<Boolean> = dao.isFavorite(deviceId)

    // Load actual or simulated local files for the share selection lists
    suspend fun loadDeviceFiles(): List<ShareableFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<ShareableFile>()

        // 1. In a production app, we would query MediaStore.
        // Let's add some actual files from standard directories if they exist:
        try {
            val publicDirectories = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            )

            for (dir in publicDirectories) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val ext = file.extension.lowercase()
                            val type = getFileTypeByExtension(ext)
                            files.add(
                                ShareableFile(
                                    id = UUID.randomUUID().toString(),
                                    name = file.name,
                                    path = file.absolutePath,
                                    sizeInBytes = file.length(),
                                    type = type,
                                    extension = file.extension
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Always inject or fallback with high-quality demo files
        // so that the app is instantly ready for a sending sequence.
        if (files.size < 5) {
            val demoList = listOf(
                ShareableFile("f1", "Trip_to_Switzerland.mov", "/storage/movies/Trip_to_Switzerland.mov", 482 * 1024 * 1024L, FileType.VIDEO, "mov"),
                ShareableFile("f2", "Profile_Picture_HD.jpg", "/storage/pictures/Profile_Picture_HD.jpg", (4.1 * 1024 * 1024).toLong(), FileType.IMAGE, "jpg"),
                ShareableFile("f3", "Financial_Statement_Q3.xlsx", "/storage/documents/Financial_Statement_Q3.xlsx", (1.8 * 1024 * 1024).toLong(), FileType.DOCUMENT, "xlsx"),
                ShareableFile("f4", "Minecraft_Launcher.apk", "/storage/apks/Minecraft_Launcher.apk", 124 * 1024 * 1024L, FileType.APK, "apk"),
                ShareableFile("f5", "Family_Archive_2025.zip", "/storage/archives/Family_Archive_2025.zip", (2.2 * 1024 * 1024 * 1024).toLong(), FileType.ZIP, "zip"),
                ShareableFile("f6", "Lofi_Beats_Study.mp3", "/storage/audio/Lofi_Beats_Study.mp3", (11.2 * 1024 * 1024).toLong(), FileType.AUDIO, "mp3"),
                ShareableFile("f7", "Resume_Application.pdf", "/storage/documents/Resume_Application.pdf", 450 * 1024L, FileType.DOCUMENT, "pdf"),
                ShareableFile("f8", "Offline_File_Sharer_v2.apk", "/storage/apks/Offline_File_Sharer_v2.apk", 34 * 1024 * 1024L, FileType.APK, "apk"),
                ShareableFile("f9", "Drone_4K_Skyline.mp4", "/storage/movies/Drone_4K_Skyline.mp4", 982 * 1024 * 1024L, FileType.VIDEO, "mp4"),
                ShareableFile("f10", "Scenic_Sunset_Beach.png", "/storage/pictures/Scenic_Sunset_Beach.png", (6.8 * 1024 * 1024).toLong(), FileType.IMAGE, "png")
            )
            files.addAll(demoList)
        }

        files.distinctBy { it.name }
    }

    private fun getFileTypeByExtension(ext: String): FileType {
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "heic" -> FileType.IMAGE
            "mp4", "mkv", "mov", "avi", "webm", "3gp" -> FileType.VIDEO
            "mp3", "wav", "m4a", "flac", "ogg" -> FileType.AUDIO
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt" -> FileType.DOCUMENT
            "apk" -> FileType.APK
            "zip", "tar", "gz", "rar", "7z" -> FileType.ZIP
            else -> FileType.OTHER
        }
    }
}
