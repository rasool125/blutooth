package com.example.videoplayer.Data

private const val DEVICE_CONNECTOR_LOGGER = "DEVICE_CONNECTOR"

@SuppressLint("MissingPermission")
class BluetoothDeviceConnector(
    private val context: Context
) {

    private val bluetoothManager by lazy { context.getSystemService<BluetoothManager>() }
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val hasBluetoothPermission: Boolean
        get() = context.hasBTConnectPermission

    // Connection states
    private val _connectionState = MutableStateFlow(DeviceConnectionState.DISCONNECTED)
    private val _connectedDevice = MutableStateFlow<BluetoothDeviceModel?>(null)
    private val _connectionError = MutableStateFlow<String?>(null)

    // Current connection
    private var bluetoothSocket: BluetoothSocket? = null
    private var connectionJob: Job? = null
    
    // Coroutine scope
    private val connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Public flows
    val connectionState: Flow<DeviceConnectionState> = _connectionState.asStateFlow()
    val connectedDevice: Flow<BluetoothDeviceModel?> = _connectedDevice.asStateFlow()
    val connectionError: Flow<String?> = _connectionError.asStateFlow()

    // Common UUIDs for consumer devices
    companion object {
        // Audio profiles
        private val A2DP_UUID = UUID.fromString("0000110D-0000-1000-8000-00805F9B34FB") // Advanced Audio
        private val HEADSET_UUID = UUID.fromString("00001108-0000-1000-8000-00805f9b34fb") // Headset
        private val HEADSET_AG_UUID = UUID.fromString("00001112-0000-1000-8000-00805F9B34FB") // Headset Audio Gateway
        private val AVRCP_UUID = UUID.fromString("0000110E-0000-1000-8000-00805F9B34FB") // Audio/Video Remote Control
        
        // General profiles
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Serial Port Profile
        private val OBEX_UUID = UUID.fromString("00001105-0000-1000-8000-00805F9B34FB") // Object Exchange (phones)
        private val HID_UUID = UUID.fromString("00001124-0000-1000-8000-00805F9B34FB") // Human Interface Device
        
        // Connection settings
        private const val CONNECTION_TIMEOUT = 15000L // 15 seconds
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY = 2000L // 2 seconds
    }

    /**
     * Connect to a Bluetooth device (speakers, headphones, phones, etc.)
     */
    suspend fun connectToDevice(device: BluetoothDeviceModel): Result<Unit> {
        if (!hasBluetoothPermission) {
            return Result.failure(Exception("Bluetooth permission not granted"))
        }

        if (bluetoothAdapter?.isEnabled != true) {
            return Result.failure(Exception("Bluetooth is not enabled"))
        }

        // Check if already connecting
        if (_connectionState.value == DeviceConnectionState.CONNECTING) {
            Log.d(DEVICE_CONNECTOR_LOGGER, "Already connecting to a device")
            return Result.failure(Exception("Already connecting"))
        }

        // Disconnect current device if connected
        if (_connectionState.value == DeviceConnectionState.CONNECTED) {
            disconnect()
        }

        return try {
            _connectionState.value = DeviceConnectionState.CONNECTING
            _connectionError.value = null
            Log.d(DEVICE_CONNECTOR_LOGGER, "Starting connection to ${device.name} (${device.address})")
            
            connectWithRetry(device)
        } catch (e: Exception) {
            Log.e(DEVICE_CONNECTOR_LOGGER, "Connection failed", e)
            _connectionState.value = DeviceConnectionState.DISCONNECTED
            _connectionError.value = e.message
            Result.failure(e)
        }
    }

    private suspend fun connectWithRetry(device: BluetoothDeviceModel): Result<Unit> = withContext(Dispatchers.IO) {
        val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
            ?: return@withContext Result.failure(Exception("Could not get remote device"))

        // Cancel discovery to improve connection performance
        bluetoothAdapter?.cancelDiscovery()
        delay(500) // Wait for discovery to stop

        // Get UUIDs to try for connection
        val uuidsToTry = getConnectionUUIDs(bluetoothDevice, device.deviceType)
        Log.d(DEVICE_CONNECTOR_LOGGER, "Trying ${uuidsToTry.size} UUIDs for connection")

        var lastException: Exception? = null

        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            Log.d(DEVICE_CONNECTOR_LOGGER, "Connection attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS")

            for ((index, uuid) in uuidsToTry.withIndex()) {
                try {
                    Log.d(DEVICE_CONNECTOR_LOGGER, "Trying UUID ${index + 1}/${uuidsToTry.size}: $uuid")
                    
                    val socket = createSocketWithFallback(bluetoothDevice, uuid)
                    
                    // Try to connect with timeout
                    val connected = withTimeoutOrNull(CONNECTION_TIMEOUT) {
                        socket.connect()
                        socket.isConnected
                    }

                    if (connected == true) {
                        // Connection successful
                        bluetoothSocket = socket
                        _connectedDevice.value = device
                        _connectionState.value = DeviceConnectionState.CONNECTED
                        Log.i(DEVICE_CONNECTOR_LOGGER, "Successfully connected to ${device.name}")
                        return@withContext Result.success(Unit)
                    } else {
                        socket.close()
                        Log.w(DEVICE_CONNECTOR_LOGGER, "Connection timeout for UUID: $uuid")
                    }
                    
                } catch (e: Exception) {
                    Log.w(DEVICE_CONNECTOR_LOGGER, "Connection failed with UUID $uuid: ${e.message}")
                    lastException = e
                }
            }

            // Wait before retry (except for last attempt)
            if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                delay(RETRY_DELAY)
            }
        }

        // All attempts failed
        val errorMessage = "Failed to connect after $MAX_RETRY_ATTEMPTS attempts"
        _connectionState.value = DeviceConnectionState.DISCONNECTED
        _connectionError.value = errorMessage
        Result.failure(lastException ?: Exception(errorMessage))
    }

    private fun getConnectionUUIDs(device: BluetoothDevice, deviceType: BluetoothDeviceType): List<UUID> {
        val uuids = mutableListOf<UUID>()

        // Add device-specific UUIDs first
        device.uuids?.forEach { parcelUuid ->
            uuids.add(parcelUuid.uuid)
        }

        // Add UUIDs based on device type
        when (deviceType) {
            BluetoothDeviceType.HEADPHONES,
            BluetoothDeviceType.EARBUDS,
            BluetoothDeviceType.SPEAKERS -> {
                uuids.addAll(listOf(A2DP_UUID, HEADSET_UUID, HEADSET_AG_UUID, AVRCP_UUID))
            }
            BluetoothDeviceType.HEADSET -> {
                uuids.addAll(listOf(HEADSET_UUID, HEADSET_AG_UUID, A2DP_UUID))
            }
            BluetoothDeviceType.PHONE,
            BluetoothDeviceType.TABLET -> {
                uuids.addAll(listOf(OBEX_UUID, SPP_UUID))
            }
            BluetoothDeviceType.WATCH -> {
                uuids.addAll(listOf(SPP_UUID, HID_UUID))
            }
            BluetoothDeviceType.KEYBOARD,
            BluetoothDeviceType.MOUSE -> {
                uuids.addAll(listOf(HID_UUID, SPP_UUID))
            }
            else -> {
                // For unknown devices, try common UUIDs
                uuids.addAll(listOf(SPP_UUID, A2DP_UUID, OBEX_UUID))
            }
        }

        // Add SPP as fallback if not already added
        if (SPP_UUID !in uuids) {
            uuids.add(SPP_UUID)
        }

        return uuids.distinct()
    }

    private fun createSocketWithFallback(device: BluetoothDevice, uuid: UUID): BluetoothSocket {
        return try {
            Log.d(DEVICE_CONNECTOR_LOGGER, "Creating secure socket")
            device.createRfcommSocketToServiceRecord(uuid)
        } catch (e: Exception) {
            Log.w(DEVICE_CONNECTOR_LOGGER, "Secure socket failed, trying insecure: ${e.message}")
            try {
                device.createInsecureRfcommSocketToServiceRecord(uuid)
            } catch (e2: Exception) {
                Log.w(DEVICE_CONNECTOR_LOGGER, "Insecure socket failed, trying reflection: ${e2.message}")
                createSocketUsingReflection(device)
            }
        }
    }

    private fun createSocketUsingReflection(device: BluetoothDevice): BluetoothSocket {
        return try {
            Log.d(DEVICE_CONNECTOR_LOGGER, "Using reflection to create socket")
            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            method.invoke(device, 1) as BluetoothSocket
        } catch (e: Exception) {
            Log.e(DEVICE_CONNECTOR_LOGGER, "Reflection method failed", e)
            throw Exception("Failed to create socket: ${e.message}")
        }
    }

    /**
     * Disconnect from current device
     */
    fun disconnect(): Result<Unit> {
        return try {
            Log.d(DEVICE_CONNECTOR_LOGGER, "Disconnecting from device")
            
            connectionJob?.cancel()
            bluetoothSocket?.close()
            
            bluetoothSocket = null
            _connectedDevice.value = null
            _connectionState.value = DeviceConnectionState.DISCONNECTED
            _connectionError.value = null
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(DEVICE_CONNECTOR_LOGGER, "Error during disconnect", e)
            Result.failure(e)
        }
    }

    /**
     * Check if device is currently connected
     */
    fun isConnected(): Boolean {
        return _connectionState.value == DeviceConnectionState.CONNECTED && 
               bluetoothSocket?.isConnected == true
    }

    /**
     * Get current connection status
     */
    fun getConnectionStatus(): DeviceConnectionState {
        return _connectionState.value
    }

    /**
     * Send simple data to connected device (for devices that support it)
     */
    suspend fun sendData(data: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val socket = bluetoothSocket
        if (socket == null || !socket.isConnected) {
            return@withContext Result.failure(Exception("Device not connected"))
        }

        return@withContext try {
            val outputStream = socket.outputStream
            outputStream.write(data.toByteArray())
            outputStream.flush()
            Log.d(DEVICE_CONNECTOR_LOGGER, "Data sent successfully: $data")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(DEVICE_CONNECTOR_LOGGER, "Failed to send data", e)
            Result.failure(e)
        }
    }

    /**
     * Pair with device if not already paired
     */
    suspend fun pairDevice(device: BluetoothDeviceModel): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermission) {
            return@withContext Result.failure(Exception("Bluetooth permission not granted"))
        }

        return@withContext try {
            val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                ?: return@withContext Result.failure(Exception("Device not found"))

            if (bluetoothDevice.bondState == BluetoothDevice.BOND_BONDED) {
                Log.d(DEVICE_CONNECTOR_LOGGER, "Device already paired")
                return@withContext Result.success(true)
            }

            Log.d(DEVICE_CONNECTOR_LOGGER, "Starting pairing process")
            val pairResult = bluetoothDevice.createBond()
            
            if (pairResult) {
                // Wait for pairing to complete
                var attempts = 0
                while (bluetoothDevice.bondState == BluetoothDevice.BOND_BONDING && attempts < 30) {
                    delay(1000)
                    attempts++
                }
                
                val success = bluetoothDevice.bondState == BluetoothDevice.BOND_BONDED
                Log.d(DEVICE_CONNECTOR_LOGGER, "Pairing result: $success")
                Result.success(success)
            } else {
                Result.failure(Exception("Failed to initiate pairing"))
            }
        } catch (e: Exception) {
            Log.e(DEVICE_CONNECTOR_LOGGER, "Pairing failed", e)
            Result.failure(e)
        }
    }

    /**
     * Release all resources
     */
    fun release() {
        Log.d(DEVICE_CONNECTOR_LOGGER, "Releasing resources")
        disconnect()
        connectionScope.cancel()
    }
}

// Connection states for consumer devices
enum class DeviceConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

// Extension function for permission check
private val Context.hasBTConnectPermission: Boolean
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
    }

// Usage in Repository or ViewModel
class BluetoothDeviceRepository(private val context: Context) {
    
    private val deviceConnector = BluetoothDeviceConnector(context)
    
    val connectionState = deviceConnector.connectionState
    val connectedDevice = deviceConnector.connectedDevice
    val connectionError = deviceConnector.connectionError
    
    suspend fun connectToDevice(device: BluetoothDeviceModel): Result<Unit> {
        return deviceConnector.connectToDevice(device)
    }
    
    suspend fun pairAndConnect(device: BluetoothDeviceModel): Result<Unit> {
        // First try to pair if not already paired
        val pairResult = deviceConnector.pairDevice(device)
        if (pairResult.isFailure) {
            return Result.failure(pairResult.exceptionOrNull() ?: Exception("Pairing failed"))
        }
        
        // Then connect
        return deviceConnector.connectToDevice(device)
    }
    
    fun disconnect() {
        deviceConnector.disconnect()
    }
    
    fun isConnected(): Boolean {
        return deviceConnector.isConnected()
    }
    
    fun release() {
        deviceConnector.release()
    }
}

// Usage in ViewModel
class BluetoothViewModel(
    private val repository: BluetoothDeviceRepository
) : ViewModel() {
    
    val connectionState = repository.connectionState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        DeviceConnectionState.DISCONNECTED
    )
    
    val connectedDevice = repository.connectedDevice.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        null
    )
    
    fun connectToDevice(device: BluetoothDeviceModel) {
        viewModelScope.launch {
            repository.connectToDevice(device)
        }
    }
    
    fun pairAndConnect(device: BluetoothDeviceModel) {
        viewModelScope.launch {
            repository.pairAndConnect(device)
        }
    }
    
    fun disconnect() {
        repository.disconnect()
    }
    
    override fun onCleared() {
        super.onCleared()
        repository.release()
    }
}