package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.db.BypassedApp
import com.example.db.ConnectionSession
import com.example.db.VpnDatabase
import com.example.db.VpnRepository
import com.example.model.VpnServer
import com.example.vpn.MyVpnService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.InetSocketAddress
import java.net.Socket

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val db = androidx.room.Room.databaseBuilder(
        application,
        VpnDatabase::class.java,
        "siamvpn_db"
    ).fallbackToDestructiveMigration().build()

    private val repository = VpnRepository(db.vpnDao())

    // UI state states
    private val _servers = MutableStateFlow<List<VpnServer>>(emptyList())
    val servers: StateFlow<List<VpnServer>> = _servers.asStateFlow()

    private val _selectedServer = MutableStateFlow<VpnServer?>(null)
    val selectedServer: StateFlow<VpnServer?> = _selectedServer.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _bytesUploaded = MutableStateFlow(0L)
    val bytesUploaded: StateFlow<Long> = _bytesUploaded.asStateFlow()

    private val _bytesDownloaded = MutableStateFlow(0L)
    val bytesDownloaded: StateFlow<Long> = _bytesDownloaded.asStateFlow()

    private val _connectionDuration = MutableStateFlow(0L) // in seconds
    val connectionDuration: StateFlow<Long> = _connectionDuration.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // Real-time Speed history for graphs
    private val _uploadSpeedHistory = MutableStateFlow<List<Float>>(List(15) { 0f })
    val uploadSpeedHistory: StateFlow<List<Float>> = _uploadSpeedHistory.asStateFlow()

    private val _downloadSpeedHistory = MutableStateFlow<List<Float>>(List(15) { 0f })
    val downloadSpeedHistory: StateFlow<List<Float>> = _downloadSpeedHistory.asStateFlow()

    // Installed apps for split-tunneling
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _isTestingPing = MutableStateFlow(false)
    val isTestingPing: StateFlow<Boolean> = _isTestingPing.asStateFlow()

    // Multi-protocol/Node configuration flows
    private val _selectedProtocol = MutableStateFlow("SSH SSL/TLS")
    val selectedProtocol: StateFlow<String> = _selectedProtocol.asStateFlow()

    private val _isCustomConfigEnabled = MutableStateFlow(false)
    val isCustomConfigEnabled: StateFlow<Boolean> = _isCustomConfigEnabled.asStateFlow()

    private val _customHost = MutableStateFlow("")
    val customHost: StateFlow<String> = _customHost.asStateFlow()

    private val _customPort = MutableStateFlow("443")
    val customPort: StateFlow<String> = _customPort.asStateFlow()

    private val _customUsername = MutableStateFlow("")
    val customUsername: StateFlow<String> = _customUsername.asStateFlow()

    private val _customPassword = MutableStateFlow("")
    val customPassword: StateFlow<String> = _customPassword.asStateFlow()

    private val _customSni = MutableStateFlow("m.tiktok.com")
    val customSni: StateFlow<String> = _customSni.asStateFlow()

    private val _customPayload = MutableStateFlow("CONNECT [host_port] HTTP/1.1\\r\\nHost: line.me\\r\\nConnection: keep-alive\\r\\n\\r\\n")
    val customPayload: StateFlow<String> = _customPayload.asStateFlow()

    // Notification/Alert state for Connection Drops & Timeouts
    data class VpnAlert(
        val title: String,
        val message: String,
        val isTimeout: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _vpnAlert = MutableStateFlow<VpnAlert?>(null)
    val vpnAlert: StateFlow<VpnAlert?> = _vpnAlert.asStateFlow()

    private var wasConnected = false
    private var isUserInitiatedDisconnect = false
    private var connectionTimeoutJob: Job? = null

    // DB flows
    val connectionHistory: StateFlow<List<ConnectionSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dbBypassedApps: StateFlow<List<BypassedApp>> = repository.bypassedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var statsJob: Job? = null
    private var pingJob: Job? = null

    // Inner model for listing apps in UI
    data class AppInfo(
        val packageName: String,
        val appName: String,
        val isBypassed: Boolean
    )

    init {
        // Initialize servers list
        initServers()
        
        // Listen to active VPN service state changes
        MyVpnService.registerListener {
            updateVpnState()
        }
        updateVpnState()

        // Load installed apps for split-tunneling
        loadInstalledApps()

        // Sync bypassed apps from DB to current app list whenever DB updates
        viewModelScope.launch {
            combine(repository.bypassedApps, _installedApps) { dbApps, currentList ->
                if (currentList.isEmpty()) return@combine
                val dbMap = dbApps.associateBy { it.packageName }
                val updated = currentList.map { app ->
                    val dbApp = dbMap[app.packageName]
                    app.copy(isBypassed = dbApp?.isBypassed ?: false)
                }
                _installedApps.value = updated
            }.collect()
        }
    }

    private fun initServers() {
        _servers.value = listOf(
            VpnServer("th_bangkok", "เซิร์ฟเวอร์ประเทศไทย (กรุงเทพฯ)", "Thailand", "th", "1.1.1.1", 80, "103.22.181.5", isPremium = false, protocol = "OpenVPN"),
            VpnServer("sg_marine", "เซิร์ฟเวอร์สิงคโปร์ (Marina Bay)", "Singapore", "sg", "8.8.8.8", 80, "119.81.28.140", isPremium = false, protocol = "SSH SSL/TLS"),
            VpnServer("jp_tokyo", "เซิร์ฟเวอร์ญี่ปุ่น (โตเกียว)", "Japan", "jp", "jp.pool.ntp.org", 80, "210.140.10.11", isPremium = false, protocol = "V2Ray VMess"),
            VpnServer("us_west", "เซิร์ฟเวอร์สหรัฐอเมริกา (แคลิฟอร์เนีย)", "United States", "us", "us.pool.ntp.org", 80, "104.244.42.1", isPremium = true, protocol = "V2Ray VLess"),
            VpnServer("kr_seoul", "เซิร์ฟเวอร์เกาหลีใต้ (โซล)", "South Korea", "kr", "kr.pool.ntp.org", 80, "112.216.55.20", isPremium = true, protocol = "Trojan"),
            VpnServer("de_frankfurt", "เซิร์ฟเวอร์เยอรมนี (แฟรงก์เฟิร์ต)", "Germany", "de", "de.pool.ntp.org", 80, "46.165.211.17", isPremium = true, protocol = "Shadowsocks"),
            VpnServer("gb_london", "เซิร์ฟเวอร์สหราชอาณาจักร (ลอนดอน)", "United Kingdom", "gb", "uk.pool.ntp.org", 80, "195.154.122.50", isPremium = true, protocol = "SSH Direct")
        )
        // Select Thailand as default server
        val defaultServer = _servers.value.first()
        _selectedServer.value = defaultServer
        _selectedProtocol.value = defaultServer.protocol
    }

    private fun updateVpnState() {
        val connected = MyVpnService.isConnected
        
        // Detect unexpected connection drop
        if (wasConnected && !connected && !isUserInitiatedDisconnect) {
            _vpnAlert.value = VpnAlert(
                title = "⚠️ คำเตือน: การเชื่อมต่อ VPN หลุด! (Connection Dropped)",
                message = "สัญญาณอุโมงค์ VPN ขาดการติดต่อกะทันหัน กรุณาตรวจสอบการเชื่อมต่ออินเทอร์เน็ตหรือเชื่อมต่อใหม่",
                isTimeout = false
            )
            MyVpnService.log("⚠️ แจ้งเตือน: สัญญาณ VPN ขาดการติดต่อกะทันหัน (Connection Drop Warning)")
        }

        if (connected) {
            isUserInitiatedDisconnect = false
            connectionTimeoutJob?.cancel()
            _isConnecting.value = false
        }

        wasConnected = connected
        _isConnected.value = connected
        _logs.value = synchronized(MyVpnService.logs) { ArrayList(MyVpnService.logs) }

        if (connected) {
            _isConnecting.value = false
            // Start polling statistics
            if (statsJob == null || !statsJob!!.isActive) {
                startStatsPolling()
            }
        } else {
            // Stop stats job if not connected
            statsJob?.cancel()
            statsJob = null
            _connectionDuration.value = 0L
            _bytesUploaded.value = 0L
            _bytesDownloaded.value = 0L
        }
    }

    private fun startStatsPolling() {
        statsJob = viewModelScope.launch(Dispatchers.Default) {
            var lastUploaded = 0L
            var lastDownloaded = 0L
            
            while (isActive && MyVpnService.isConnected) {
                val currentUploaded = MyVpnService.bytesUploaded
                val currentDownloaded = MyVpnService.bytesDownloaded
                
                // Calculate speeds (bytes per second)
                val upSpeedKb = ((currentUploaded - lastUploaded) / 1024f).coerceAtLeast(0f)
                val dlSpeedKb = ((currentDownloaded - lastDownloaded) / 1024f).coerceAtLeast(0f)

                lastUploaded = currentUploaded
                lastDownloaded = currentDownloaded

                _bytesUploaded.value = currentUploaded
                _bytesDownloaded.value = currentDownloaded
                
                if (MyVpnService.connectionStartTime > 0) {
                    _connectionDuration.value = (System.currentTimeMillis() - MyVpnService.connectionStartTime) / 1000
                }

                // Update Speed Graphs history
                _uploadSpeedHistory.update { history ->
                    history.drop(1) + upSpeedKb
                }
                _downloadSpeedHistory.update { history ->
                    history.drop(1) + dlSpeedKb
                }

                delay(1000)
            }
        }
    }

    fun selectServer(server: VpnServer) {
        if (!_isConnected.value && !_isConnecting.value) {
            _selectedServer.value = server
            _selectedProtocol.value = server.protocol
        }
    }

    fun setProtocol(protocol: String) {
        _selectedProtocol.value = protocol
    }

    fun setCustomConfigEnabled(enabled: Boolean) {
        _isCustomConfigEnabled.value = enabled
    }

    fun setCustomHost(host: String) {
        _customHost.value = host
    }

    fun setCustomPort(port: String) {
        _customPort.value = port
    }

    fun setCustomUsername(username: String) {
        _customUsername.value = username
    }

    fun setCustomPassword(password: String) {
        _customPassword.value = password
    }

    fun setCustomSni(sni: String) {
        _customSni.value = sni
    }

    fun setCustomPayload(payload: String) {
        _customPayload.value = payload
    }

    fun toggleVpnConnection(context: Context) {
        if (_isConnecting.value) return

        if (_isConnected.value) {
            // User manually requested disconnect
            isUserInitiatedDisconnect = true
            val intent = Intent(context, MyVpnService::class.java).apply {
                action = MyVpnService.ACTION_DISCONNECT
            }
            context.startService(intent)

            // Save history session log in database
            viewModelScope.launch(Dispatchers.IO) {
                val server = _selectedServer.value ?: return@launch
                val duration = _connectionDuration.value
                val uploaded = _bytesUploaded.value
                val downloaded = _bytesDownloaded.value
                
                if (duration > 2) { // Only save sessions longer than 2 seconds
                    repository.insertSession(
                        ConnectionSession(
                            serverName = server.name,
                            countryCode = server.countryCode,
                            durationSecs = duration,
                            bytesUploaded = uploaded,
                            bytesDownloaded = downloaded
                        )
                    )
                }
            }
        } else {
            // Trigger connection sequence
            isUserInitiatedDisconnect = false
            _isConnecting.value = true
            MyVpnService.resetStats()
            MyVpnService.log("เริ่มต้นกระบวนการยืนยันการเชื่อมต่อ...")

            // Check VpnService prepare consent
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                // Return to activity to launch permission dialog
                MyVpnService.log("ต้องการความยินยอมการเปิดใช้งานอุโมงค์ VPN จากผู้ใช้...")
                _isConnecting.value = false
                // Trigger action for Activity
                _vpnPrepareIntent.value = prepareIntent
            } else {
                // Already have permission, start service immediately!
                startVpnService(context)
            }
        }
    }

    private val _vpnPrepareIntent = MutableStateFlow<Intent?>(null)
    val vpnPrepareIntent: StateFlow<Intent?> = _vpnPrepareIntent.asStateFlow()

    fun clearPrepareIntent() {
        _vpnPrepareIntent.value = null
    }

    fun startVpnService(context: Context) {
        _isConnecting.value = true
        isUserInitiatedDisconnect = false
        
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = viewModelScope.launch {
            delay(800) // Beautiful transition layout delay
            val server = _selectedServer.value ?: return@launch
            
            // Get currently bypassed apps to send to the VPN service builder
            val bypassedPackages = _installedApps.value
                .filter { it.isBypassed }
                .map { it.packageName }

            val intent = Intent(context, MyVpnService::class.java).apply {
                action = MyVpnService.ACTION_CONNECT
                putExtra("server_name", server.name)
                putExtra("server_ip", server.fakeIp)
                putExtra("country_code", server.countryCode)
                putStringArrayListExtra("bypass_packages", ArrayList(bypassedPackages))
                
                // Pass protocol details
                putExtra("protocol", _selectedProtocol.value)
                putExtra("is_custom", _isCustomConfigEnabled.value)
                putExtra("custom_host", _customHost.value)
                putExtra("custom_port", _customPort.value)
                putExtra("custom_user", _customUsername.value)
                putExtra("custom_pass", _customPassword.value)
                putExtra("custom_sni", _customSni.value)
                putExtra("custom_payload", _customPayload.value)
            }
            context.startService(intent)

            // Connection Timeout Monitor (15s timeout)
            delay(15000)
            if (_isConnecting.value && !MyVpnService.isConnected) {
                cancelConnection()
                _vpnAlert.value = VpnAlert(
                    title = "⚠️ การเชื่อมต่อหมดเวลา (Connection Timeout)",
                    message = "ไม่ได้รับการตอบรับจากเซิร์ฟเวอร์ปลายทาง (${server.name}) ภายใน 15 วินาที กรุณาตรวจสอบอินเทอร์เน็ตหรือสลับเซิร์ฟเวอร์",
                    isTimeout = true
                )
                MyVpnService.log("⚠️ หมดเวลา: เซิร์ฟเวอร์ปลายทางไม่ตอบสนอง (Connection Timeout)")
            }
        }
    }

    fun cancelConnection() {
        connectionTimeoutJob?.cancel()
        _isConnecting.value = false
        MyVpnService.log("ยกเลิกการสถาปนาเครือข่าย")
    }

    fun simulateConnectionDrop() {
        if (_isConnected.value) {
            isUserInitiatedDisconnect = false // force unexpected drop detection
            val intent = Intent(getApplication(), MyVpnService::class.java).apply {
                action = MyVpnService.ACTION_DISCONNECT
            }
            getApplication<Application>().startService(intent)
        } else {
            _vpnAlert.value = VpnAlert(
                title = "⚠️ คำเตือน: การเชื่อมต่อ VPN หลุด! (Simulated Drop)",
                message = "ระบบตรวจพบสัญญาณอุโมงค์ VPN ขาดการติดต่อกะทันหัน กรุณาตรวจสอบการเชื่อมต่ออินเทอร์เน็ต",
                isTimeout = false
            )
            MyVpnService.log("⚠️ จำลองสถานการณ์การเชื่อมต่อหลุด (Simulated VPN Drop Alert)")
        }
    }

    fun simulateConnectionTimeout() {
        _vpnAlert.value = VpnAlert(
            title = "⚠️ หมดเวลาการเชื่อมต่อ (Simulated Timeout)",
            message = "ไม่ได้รับตอบรับจากเซิร์ฟเวอร์ปลายทางตามเวลาที่กำหนด (Timeout 15s) กรุณาลองเปลี่ยนเซิร์ฟเวอร์",
            isTimeout = true
        )
        MyVpnService.log("⚠️ จำลองสถานการณ์การเชื่อมต่อหมดเวลา (Simulated Timeout Alert)")
    }

    fun dismissAlert() {
        _vpnAlert.value = null
    }

    fun runPingTests() {
        if (_isTestingPing.value) return
        _isTestingPing.value = true

        pingJob = viewModelScope.launch(Dispatchers.Default) {
            val currentServers = _servers.value
            for (server in currentServers) {
                val ping = pingHost(server.host, server.port)
                // Update server list with tested ping
                _servers.update { list ->
                    list.map { item ->
                        if (item.id == server.id) {
                            item.copy(currentPing = ping)
                        } else {
                            item
                        }
                    }
                }
                // Update selected server in case it was pinged
                if (_selectedServer.value?.id == server.id) {
                    _selectedServer.update { it?.copy(currentPing = ping) }
                }
                delay(100) // Brief spacing between pings
            }
            _isTestingPing.value = false
        }
    }

    private suspend fun pingHost(host: String, port: Int): Int {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 1200) // 1.2s timeout
                    val endTime = System.currentTimeMillis()
                    (endTime - startTime).toInt()
                }
            } catch (e: Exception) {
                -1 // timeout or socket error
            }
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            
            val appList = packages
                .filter { app ->
                    // Exclude system apps to keep the list clean and actionable
                    (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                }
                .map { app ->
                    val appName = pm.getApplicationLabel(app).toString()
                    val packageName = app.packageName
                    AppInfo(
                        packageName = packageName,
                        appName = appName,
                        isBypassed = false
                    )
                }
                .sortedBy { it.appName }

            _installedApps.value = appList
        }
    }

    fun toggleAppBypass(app: AppInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val isCurrentlyBypassed = app.isBypassed
            val newBypassState = !isCurrentlyBypassed
            
            // Update local state immediately
            _installedApps.update { list ->
                list.map { item ->
                    if (item.packageName == app.packageName) {
                        item.copy(isBypassed = newBypassState)
                    } else {
                        item
                    }
                }
            }

            // Sync with Room DB
            if (newBypassState) {
                repository.addBypassedApp(
                    BypassedApp(packageName = app.packageName, appName = app.appName, isBypassed = true)
                )
            } else {
                repository.removeBypassedApp(app.packageName)
            }
        }
    }

    fun clearSessionLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        MyVpnService.unregisterListener { updateVpnState() }
        statsJob?.cancel()
        pingJob?.cancel()
    }
}
