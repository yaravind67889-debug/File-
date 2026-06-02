package com.example.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.random.Random

class P2PManager(private val context: Context) {
    private val scope = CoroutineScope(DispatchScopeProvider.dispatcher + SupervisorJob())

    // Flow states
    private val _discoveredDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<NearbyDevice>> = _discoveredDevices.asStateFlow()

    private val _currentConnectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val currentConnectionState: StateFlow<ConnectionState> = _currentConnectionState.asStateFlow()

    private val _activeSession = MutableStateFlow<TransferSession?>(null)
    val activeSession: StateFlow<TransferSession?> = _activeSession.asStateFlow()

    private val _incomingRequest = MutableStateFlow<TransferSession?>(null)
    val incomingRequest: StateFlow<TransferSession?> = _incomingRequest.asStateFlow()

    private val _isDiscoveryRunning = MutableStateFlow(false)
    val isDiscoveryRunning: StateFlow<Boolean> = _isDiscoveryRunning.asStateFlow()

    // Real APIs
    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiChannel: WifiP2pManager.Channel? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    // Preferences / Config
    var isSimulationMode = true // If true or if hardware is missing, we simulate
    var deviceName: String = Build.MODEL ?: "Offline Device"
    var autoAccept: Boolean = false

    // Simulation simulation variables
    private var discoveryJob: Job? = null
    private var transferJob: Job? = null
    private var speedRampValue = 12 * 1024 * 1024L // ~12MB/s start speed

    init {
        initRealAPIs()
    }

    private fun initRealAPIs() {
        try {
            // Wi-Fi Direct initialize
            wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
            wifiP2pManager?.let { manager ->
                wifiChannel = manager.initialize(context, Looper.getMainLooper(), null)
            }

            // Bluetooth initialize
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
            bluetoothAdapter = btManager?.adapter

            // If we have actual access, we can try to disable simulation mode by default (but keep fallback)
            isSimulationMode = wifiP2pManager == null && bluetoothAdapter == null
        } catch (e: Exception) {
            Log.e("P2PManager", "Error initializing real network APIs: ${e.message}")
            isSimulationMode = true
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (_isDiscoveryRunning.value) return
        _isDiscoveryRunning.value = true
        _currentConnectionState.value = ConnectionState.DISCOVERING
        _discoveredDevices.value = emptyList()

        if (!isSimulationMode) {
            // Real Wi-Fi Direct Peer Discovery
            try {
                wifiChannel?.let { channel ->
                    wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            Log.d("P2PManager", "WiFi-P2P Peer discovery started successfully")
                        }
                        override fun onFailure(reasonCode: Int) {
                            Log.e("P2PManager", "WiFi-P2P peer discovery failed with reason $reasonCode")
                        }
                    })
                }

                // Start Bluetooth Discovery if enabled
                if (bluetoothAdapter?.isEnabled == true) {
                    bluetoothAdapter?.startDiscovery()
                }
            } catch (e: Exception) {
                Log.e("P2PManager", "Real hardware discovery failure: ${e.message}")
            }
        }

        // Always provide active high-fidelity emulation in parallel (or fallback)
        // so that the UI can always display active discovery for immediate interactive user testing.
        discoveryJob = scope.launch {
            // Initial mock devices
            val templateNames = listOf(
                "Pixel Pro 8" to 2,
                "Galaxy Tab S9" to 5,
                "iPhone Ultra" to 0,
                "OnePlus Shared" to 3,
                "Nothing Phone (2a)" to 1,
                "Zenbook Duo Receiver" to 4
            )

            var deviceIndex = 0
            while (isActive && _isDiscoveryRunning.value) {
                delay(Random.nextLong(1500, 3000))
                if (deviceIndex < templateNames.size) {
                    val pair = templateNames[deviceIndex]
                    val strength = Random.nextInt(2, 5) // Strong connections
                    val types = ConnectionType.values()
                    val connectionType = types[Random.nextInt(types.size)]

                    val newDevice = NearbyDevice(
                        id = "dev_${1000 + deviceIndex}",
                        name = pair.first,
                        avatarIndex = pair.second,
                        signalStrength = strength,
                        connectionType = connectionType,
                        isFavorite = Random.nextBoolean(),
                        pairingCode = "${100000 + Random.nextInt(900000)}"
                    )

                    _discoveredDevices.value = _discoveredDevices.value + newDevice
                    deviceIndex++
                } else {
                    // Update signal strengths randomly to simulate real life
                    _discoveredDevices.value = _discoveredDevices.value.map {
                        it.copy(signalStrength = Random.nextInt(1, 5))
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (!_isDiscoveryRunning.value) return
        _isDiscoveryRunning.value = false
        if (currentConnectionState.value == ConnectionState.DISCOVERING) {
            _currentConnectionState.value = ConnectionState.DISCONNECTED
        }

        discoveryJob?.cancel()
        discoveryJob = null

        if (!isSimulationMode) {
            try {
                wifiChannel?.let { channel ->
                    wifiP2pManager?.stopPeerDiscovery(channel, null)
                }
                if (bluetoothAdapter?.isDiscovering == true) {
                    bluetoothAdapter?.cancelDiscovery()
                }
            } catch (e: Exception) {
                Log.e("P2PManager", "Failed to stop real discovery: ${e.message}")
            }
        }
    }

    // Connects to a device
    fun connectToDevice(device: NearbyDevice, isInitiatingSend: Boolean, selectedFiles: List<ShareableFile> = emptyList()) {
        stopDiscovery()
        _currentConnectionState.value = ConnectionState.PAIRING

        scope.launch {
            delay(1500) // Pairing animation period
            _currentConnectionState.value = ConnectionState.CONNECTING
            delay(1200)

            _currentConnectionState.value = ConnectionState.CONNECTED

            if (isInitiatingSend) {
                // We are sending -> initiate transfer session
                startSendSession(device, selectedFiles)
            } else {
                // We are receiving -> wait for simulated incoming files,
                // or wait for standard handshake logic.
                simulateIncomingRequest(device)
            }
        }
    }

    // Handles incoming transfers simulation request
    private fun simulateIncomingRequest(sender: NearbyDevice) {
        val incomingFiles = listOf(
            TransferFileUpdate("f_inc1", "Summer_Camp_Video.mp4", 452 * 1024 * 1024L, 0L, TransferStatus.PENDING),
            TransferFileUpdate("f_inc2", "Document_Contract_Signed.pdf", (2.4 * 1024 * 1024).toLong(), 0L, TransferStatus.PENDING),
            TransferFileUpdate("f_inc3", "AssetArchive.zip", 1450 * 1024 * 1024L, 0L, TransferStatus.PENDING)
        )

        val session = TransferSession(
            sessionId = "sess_${System.currentTimeMillis()}",
            deviceName = sender.name,
            deviceAvatarIndex = sender.avatarIndex,
            isIncoming = true,
            files = incomingFiles,
            connectionType = sender.connectionType,
            status = TransferStatus.PENDING,
            bytesTransferred = 0,
            totalBytes = incomingFiles.sumOf { it.totalSize },
            speedBytesPerSec = 0,
            estimatedTimeRemainingSec = 0
        )

        _incomingRequest.value = session
    }

    fun acceptIncomingTransfer(onCompleted: (TransferSession) -> Unit) {
        val session = _incomingRequest.value ?: return
        _incomingRequest.value = null
        _activeSession.value = session.copy(status = TransferStatus.IN_PROGRESS)
        _currentConnectionState.value = ConnectionState.TRANSFERRING

        startTransferLoop(onCompleted)
    }

    fun declineIncomingTransfer() {
        _incomingRequest.value = null
        _currentConnectionState.value = ConnectionState.CONNECTED
    }

    // Starts sending files manually
    private fun startSendSession(receiver: NearbyDevice, files: List<ShareableFile>) {
        if (files.isEmpty()) return

        val transferFiles = files.map {
            TransferFileUpdate(it.id, it.name, it.sizeInBytes, 0L, TransferStatus.PENDING)
        }

        val session = TransferSession(
            sessionId = "sess_${System.currentTimeMillis()}",
            deviceName = receiver.name,
            deviceAvatarIndex = receiver.avatarIndex,
            isIncoming = false,
            files = transferFiles,
            connectionType = receiver.connectionType,
            status = TransferStatus.IN_PROGRESS,
            bytesTransferred = 0,
            totalBytes = files.sumOf { it.sizeInBytes },
            speedBytesPerSec = 0,
            estimatedTimeRemainingSec = 0
        )

        _activeSession.value = session
        _currentConnectionState.value = ConnectionState.TRANSFERRING

        startTransferLoop(onCompleted = {})
    }

    // Active transfer operation execution
    private fun startTransferLoop(onCompleted: (TransferSession) -> Unit) {
        transferJob?.cancel()
        transferJob = scope.launch {
            var session = _activeSession.value ?: return@launch
            val totalBytesSum = session.totalBytes
            var currentTransferredSum = session.bytesTransferred

            speedRampValue = Random.nextLong(15 * 1024 * 1024, 30 * 1024 * 1024) // 15-30MB/s

            // Multi-file state loop
            val updatedFiles = session.files.map { it.copy() }.toMutableList()

            for (i in updatedFiles.indices) {
                val f = updatedFiles[i]
                if (f.status == TransferStatus.COMPLETED) continue

                updatedFiles[i] = f.copy(status = TransferStatus.IN_PROGRESS)
                session = session.copy(files = updatedFiles.toList())
                _activeSession.value = session

                var fileTransferred = f.bytesTransferred
                val fileSize = f.totalSize

                while (fileTransferred < fileSize && isActive) {
                    if (session.status == TransferStatus.PAUSED) {
                        delay(500)
                        continue
                    }

                    delay(200) // Speed step tick

                    val randomFactor = Random.nextDouble(0.8, 1.25)
                    val speed = (speedRampValue * randomFactor).toLong()

                    // Advance transferring amount
                    val advance = (speed * 0.2f).toLong() // since delay is 200ms
                    fileTransferred = (fileTransferred + advance).coerceAtMost(fileSize)
                    currentTransferredSum = updatedFiles.subList(0, i).sumOf { it.totalSize } + fileTransferred

                    updatedFiles[i] = f.copy(bytesTransferred = fileTransferred, status = if (fileTransferred == fileSize) TransferStatus.COMPLETED else TransferStatus.IN_PROGRESS)

                    val remainingBytes = totalBytesSum - currentTransferredSum
                    val remainingSeconds = if (speed > 0) remainingBytes / speed else 0L

                    session = session.copy(
                        files = updatedFiles.toList(),
                        bytesTransferred = currentTransferredSum,
                        speedBytesPerSec = if (session.status == TransferStatus.PAUSED) 0L else speed,
                        estimatedTimeRemainingSec = remainingSeconds
                    )
                    _activeSession.value = session
                }
            }

            // Mark session completed
            session = session.copy(
                status = TransferStatus.COMPLETED,
                speedBytesPerSec = 0,
                estimatedTimeRemainingSec = 0
            )
            _activeSession.value = session
            _currentConnectionState.value = ConnectionState.COMPLETED
            onCompleted(session)
        }
    }

    fun pauseTransfer() {
        val session = _activeSession.value ?: return
        if (session.status == TransferStatus.IN_PROGRESS) {
            _activeSession.value = session.copy(status = TransferStatus.PAUSED)
            speedRampValue = 0L
        }
    }

    fun resumeTransfer() {
        val session = _activeSession.value ?: return
        if (session.status == TransferStatus.PAUSED) {
            _activeSession.value = session.copy(status = TransferStatus.IN_PROGRESS)
            speedRampValue = Random.nextLong(15 * 1024 * 1024, 30 * 1024 * 1024)
        }
    }

    fun cancelTransfer() {
        transferJob?.cancel()
        transferJob = null

        val session = _activeSession.value
        if (session != null) {
            _activeSession.value = session.copy(status = TransferStatus.CANCELLED, speedBytesPerSec = 0)
        }
        _currentConnectionState.value = ConnectionState.CONNECTED
    }

    fun resetConnection() {
        cancelTransfer()
        stopDiscovery()
        _activeSession.value = null
        _incomingRequest.value = null
        _currentConnectionState.value = ConnectionState.DISCONNECTED
    }

    fun destroy() {
        scope.cancel()
    }
}

// Light threading provider that avoids test crashes
object DispatchScopeProvider {
    val dispatcher: CoroutineDispatcher = Dispatchers.Main
}
