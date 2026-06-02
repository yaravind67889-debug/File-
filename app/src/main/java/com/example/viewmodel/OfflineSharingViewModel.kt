package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.FavoriteDeviceEntity
import com.example.data.db.HistoryEntity
import com.example.data.db.OfflineSharingDatabase
import com.example.data.repository.SharingRepository
import com.example.model.*
import com.example.service.P2PManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class OfflineSharingViewModel(application: Application) : AndroidViewModel(application) {
    private val database = OfflineSharingDatabase.getDatabase(application)
    private val repository = SharingRepository(application, database.sharingDao)
    val p2pManager = P2PManager(application)

    // UI Configuration / Settings States
    private val _darkMode = MutableStateFlow(true)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    private val _deviceName = MutableStateFlow(p2pManager.deviceName)
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _autoAcceptTransfers = MutableStateFlow(false)
    val autoAcceptTransfers: StateFlow<Boolean> = _autoAcceptTransfers.asStateFlow()

    private val _storageLocationLabel = MutableStateFlow("Internal Storage/Downloads/OfflineSharing")
    val storageLocationLabel: StateFlow<String> = _storageLocationLabel.asStateFlow()

    private val _currentLanguage = MutableStateFlow("English")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    // Loaded Local Files Selection List
    private val _localFiles = MutableStateFlow<List<ShareableFile>>(emptyList())
    val localFiles: StateFlow<List<ShareableFile>> = _localFiles.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<ShareableFile>>(emptyList())
    val selectedFiles: StateFlow<List<ShareableFile>> = _selectedFiles.asStateFlow()

    // History and Favorites from Room DB
    val transferHistory: StateFlow<List<HistoryEntity>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDevices: StateFlow<List<FavoriteDeviceEntity>> = repository.favoriteDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredHistory: StateFlow<List<HistoryEntity>> = combine(transferHistory, searchQuery) { history, query ->
        if (query.isBlank()) {
            history
        } else {
            history.filter { it.fileName.contains(query, ignoreCase = true) || it.deviceName.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Simulated Blocklist (to block unwanted devices)
    private val _blockedDevices = MutableStateFlow<Set<String>>(emptySet())
    val blockedDevices: StateFlow<Set<String>> = _blockedDevices.asStateFlow()

    // Storage info estimates
    private val _storageTotalBytes = MutableStateFlow(128 * 1024 * 1024 * 1024L) // 128 GB
    private val _storageFreeBytes = MutableStateFlow(42 * 1024 * 1024 * 1024L) // 42 GB
    private val _storageAppUsedBytes = MutableStateFlow((5.4 * 1024 * 1024 * 1024).toLong()) // 5.4 GB
    val storageTotalBytes: StateFlow<Long> = _storageTotalBytes.asStateFlow()
    val storageFreeBytes: StateFlow<Long> = _storageFreeBytes.asStateFlow()
    val storageAppUsedBytes: StateFlow<Long> = _storageAppUsedBytes.asStateFlow()

    init {
        // Read defaults from Local SharedPreferences if any
        val sp = application.getSharedPreferences("offline_share_prefs", Context.MODE_PRIVATE)
        _darkMode.value = sp.getBoolean("dark_mode", true)
        _autoAcceptTransfers.value = sp.getBoolean("auto_accept", false)
        val savedName = sp.getString("device_name", null)
        if (savedName != null) {
            _deviceName.value = savedName
            p2pManager.deviceName = savedName
        }
        _storageLocationLabel.value = sp.getString("storage_loc", "Internal Storage/Downloads/OfflineSharing") ?: "Internal Storage/Downloads/OfflineSharing"
        _currentLanguage.value = sp.getString("language", "English") ?: "English"

        p2pManager.autoAccept = _autoAcceptTransfers.value

        // Load files
        refreshLocalFiles()

        // Sync actual storage measurements on device
        updateRealStorageInfo()
    }

    private fun updateRealStorageInfo() {
        try {
            val file = getApplication<Application>().filesDir
            _storageTotalBytes.value = file.totalSpace
            _storageFreeBytes.value = file.freeSpace

            // Count download files size
            val appShareFolder = File(getApplication<Application>().filesDir, "OfflineSharing")
            val appUsage = if (appShareFolder.exists()) calculateDirSize(appShareFolder) else 0L
            _storageAppUsedBytes.value = (5.4 * 1024 * 1024 * 1024).toLong() + appUsage // preset simulation constant + actual downloads
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun calculateDirSize(file: File): Long {
        if (!file.exists()) return 0
        if (file.isFile) return file.length()
        var size = 0L
        file.listFiles()?.forEach { size += calculateDirSize(it) }
        return size
    }

    fun refreshLocalFiles() {
        viewModelScope.launch {
            _localFiles.value = repository.loadDeviceFiles()
        }
    }

    // Settings actions
    fun setDarkMode(enabled: Boolean) {
        _darkMode.value = enabled
        savePref("dark_mode", enabled)
    }

    fun setDeviceName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            _deviceName.value = trimmed
            p2pManager.deviceName = trimmed
            savePref("device_name", trimmed)
        }
    }

    fun setAutoAcceptTransfers(enabled: Boolean) {
        _autoAcceptTransfers.value = enabled
        p2pManager.autoAccept = enabled
        savePref("auto_accept", enabled)
    }

    fun setStorageLocation(label: String) {
        _storageLocationLabel.value = label
        savePref("storage_loc", label)
    }

    fun setLanguage(language: String) {
        _currentLanguage.value = language
        savePref("language", language)
    }

    // Selection changes
    fun toggleFileSelection(file: ShareableFile) {
        val current = _selectedFiles.value.toMutableList()
        if (current.any { it.id == file.id }) {
            current.removeAll { it.id == file.id }
        } else {
            current.add(file)
        }
        _selectedFiles.value = current
    }

    fun clearFileSelection() {
        _selectedFiles.value = emptyList()
    }

    // Connections
    fun startDiscovery() {
        p2pManager.startDiscovery()
    }

    fun stopDiscovery() {
        p2pManager.stopDiscovery()
    }

    fun connectAndSend(device: NearbyDevice) {
        val filesToSend = _selectedFiles.value.toList()
        p2pManager.connectToDevice(device, isInitiatingSend = true, selectedFiles = filesToSend)
        clearFileSelection()
    }

    fun connectAndReceive(device: NearbyDevice) {
        p2pManager.connectToDevice(device, isInitiatingSend = false)
    }

    fun acceptIncoming() {
        p2pManager.acceptIncomingTransfer { completedSession ->
            saveSessionToHistory(completedSession)
        }
    }

    fun declineIncoming() {
        p2pManager.declineIncomingTransfer()
    }

    fun cancelActiveTransfer() {
        p2pManager.cancelTransfer()
    }

    fun pauseActiveTransfer() {
        p2pManager.pauseTransfer()
    }

    fun resumeActiveTransfer() {
        p2pManager.resumeTransfer()
    }

    fun resetConnectionAndP2P() {
        p2pManager.resetConnection()
    }

    // Favorite management
    fun toggleDeviceFavorite(deviceId: String, name: String, avatarIndex: Int) {
        viewModelScope.launch {
            val isFav = favoriteDevices.value.any { it.deviceId == deviceId }
            if (isFav) {
                repository.removeFavoriteDevice(deviceId)
            } else {
                repository.addFavoriteDevice(deviceId, name, avatarIndex)
            }
        }
    }

    // Devices blocking list
    fun toggleDeviceBlock(deviceId: String) {
        val current = _blockedDevices.value.toMutableSet()
        if (current.contains(deviceId)) {
            current.remove(deviceId)
        } else {
            current.add(deviceId)
        }
        _blockedDevices.value = current
    }

    // Delete Log item
    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteHistoryById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun saveSessionToHistory(session: TransferSession) {
        viewModelScope.launch {
            session.files.forEach { file ->
                repository.saveTransferToHistory(
                    fileName = file.name,
                    fileSize = file.totalSize,
                    fileType = getFileTypeByName(file.name),
                    deviceName = session.deviceName,
                    deviceAvatarIndex = session.deviceAvatarIndex,
                    isIncoming = session.isIncoming,
                    status = file.status.name,
                    path = if (session.isIncoming) "${_storageLocationLabel.value}/${file.name}" else "/storage/sent/${file.name}"
                )
            }
            updateRealStorageInfo()
        }
    }

    private fun getFileTypeByName(name: String): FileType {
        val ext = name.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "webp" -> FileType.IMAGE
            "mp4", "mkv", "mov" -> FileType.VIDEO
            "mp3", "wav", "m4a" -> FileType.AUDIO
            "pdf", "docx", "xlsx", "docx" -> FileType.DOCUMENT
            "apk" -> FileType.APK
            "zip" -> FileType.ZIP
            else -> FileType.OTHER
        }
    }

    // Direct helper
    private fun savePref(key: String, value: Any) {
        val sp = getApplication<Application>().getSharedPreferences("offline_share_prefs", Context.MODE_PRIVATE)
        val edit = sp.edit()
        when (value) {
            is Boolean -> edit.putBoolean(key, value)
            is String -> edit.putString(key, value)
            is Int -> edit.putInt(key, value)
        }
        edit.apply()
    }

    override fun onCleared() {
        super.onCleared()
        p2pManager.destroy()
    }
}
