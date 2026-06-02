package com.example.model

import java.io.File

enum class FileType {
    IMAGE, VIDEO, AUDIO, DOCUMENT, APK, ZIP, OTHER
}

data class ShareableFile(
    val id: String,
    val name: String,
    val path: String,
    val sizeInBytes: Long,
    val type: FileType,
    val extension: String,
    val addedTime: Long = System.currentTimeMillis()
) {
    val sizeFormatted: String
        get() {
            if (sizeInBytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
            return String.format("%.2f %s", sizeInBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }
}

enum class ConnectionState {
    DISCONNECTED,
    DISCOVERING,
    PAIRING,
    CONNECTING,
    CONNECTED,
    TRANSFERRING,
    COMPLETED,
    FAILED
}

data class NearbyDevice(
    val id: String,
    val name: String,
    val avatarIndex: Int, // Indexes to different modern avatar presets
    val signalStrength: Int, // 1 to 4 (percentage/bars)
    val connectionType: ConnectionType,
    val isFavorite: Boolean = false,
    val pairingCode: String = ""
)

enum class ConnectionType {
    WIFI_DIRECT,
    NEARBY_CONNECTIONS,
    BLUETOOTH,
    LOCAL_HOTSPOT
}

enum class TransferStatus {
    PENDING,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class TransferSession(
    val sessionId: String,
    val deviceName: String,
    val deviceAvatarIndex: Int,
    val isIncoming: Boolean,
    val files: List<TransferFileUpdate>,
    val connectionType: ConnectionType,
    val status: TransferStatus = TransferStatus.PENDING,
    val bytesTransferred: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSec: Long = 0,
    val estimatedTimeRemainingSec: Long = 0
) {
    val progress: Float
        get() = if (totalBytes > 0) bytesTransferred.toFloat() / totalBytes else 0f

    val speedFormatted: String
        get() {
            val kb = speedBytesPerSec / 1024f
            if (kb < 1024) {
                return String.format("%.1f KB/s", kb)
            }
            val mb = kb / 1024f
            return String.format("%.1f MB/s", mb)
        }

    val timeRemainingFormatted: String
        get() {
            if (status != TransferStatus.IN_PROGRESS || speedBytesPerSec <= 0) return "calculating..."
            if (estimatedTimeRemainingSec <= 0) return "completed"
            return if (estimatedTimeRemainingSec < 60) {
                "${estimatedTimeRemainingSec}s"
            } else {
                "${estimatedTimeRemainingSec / 60}m ${estimatedTimeRemainingSec % 60}s"
            }
        }
}

data class TransferFileUpdate(
    val fileId: String,
    val name: String,
    val totalSize: Long,
    val bytesTransferred: Long,
    val status: TransferStatus
)
