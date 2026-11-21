package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VpnPreferencesRepository
import com.example.data.VpnShareLinkParser
import com.example.model.BuiltInProfiles
import com.example.model.InstalledAppInfo
import com.example.model.NetworkDiagnosticsReport
import com.example.model.TunnelMode
import com.example.model.VpnPreferences
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import com.example.model.VpnStatus
import com.example.model.VpnTelemetryState
import com.example.vpn.MutigandaVpnService
import com.example.vpn.VpnStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

data class VpnUiState(
    val telemetry: VpnTelemetryState = VpnTelemetryState(),
    val preferences: VpnPreferences = VpnPreferences(),
    val allProfiles: List<VpnProfile> = BuiltInProfiles.defaults,
    val selectedProfile: VpnProfile = BuiltInProfiles.defaults.first(),
    val profileLatencies: Map<String, Long?> = emptyMap(),
    val pingingProfileId: String? = null,
    val isPingingAll: Boolean = false,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val appsSearchQuery: String = "",
    val networkTransportLabel: String = "Checking network…",
    val diagnosticsReport: NetworkDiagnosticsReport? = null,
    val isRunningDiagnostics: Boolean = false,
    val statusBannerMessage: String? = null,
)

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VpnPreferencesRepository(application.applicationContext)
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _profileLatencies = MutableStateFlow<Map<String, Long?>>(emptyMap())
    private val _pingingProfileId = MutableStateFlow<String?>(null)
    private val _isPingingAll = MutableStateFlow(false)
    private val _rawApps = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    private val _appsSearchQuery = MutableStateFlow("")
    private val _networkTransport = MutableStateFlow(readCurrentTransportLabel())
    private val _diagnosticsReport = MutableStateFlow<NetworkDiagnosticsReport?>(null)
    private val _isRunningDiagnostics = MutableStateFlow(false)
    private val _statusBanner = MutableStateFlow<String?>(null)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkTransport.value = readCurrentTransportLabel()
        }

        override fun onLost(network: Network) {
            _networkTransport.value = readCurrentTransportLabel()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            _networkTransport.value = readCurrentTransportLabel()
        }
    }

    val uiState: StateFlow<VpnUiState> = combine(
        VpnStateHolder.state,
        repository.preferencesFlow,
        _profileLatencies,
        _pingingProfileId,
        _isPingingAll,
        _rawApps,
        _appsSearchQuery,
        _networkTransport,
        _diagnosticsReport,
        _isRunningDiagnostics,
        _statusBanner,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val telemetry = values[0] as VpnTelemetryState
        val prefs = values[1] as VpnPreferences
        @Suppress("UNCHECKED_CAST")
        val latencies = values[2] as Map<String, Long?>
        val pingingId = values[3] as String?
        val pingingAll = values[4] as Boolean
        @Suppress("UNCHECKED_CAST")
        val rawApps = values[5] as List<Pair<String, String>>
        val query = values[6] as String
        val transport = values[7] as String
        val diagnostics = values[8] as NetworkDiagnosticsReport?
        val runningDiag = values[9] as Boolean
        val banner = values[10] as String?

        val allProfiles = (BuiltInProfiles.defaults + prefs.customProfiles).map { profile ->
            profile.copy(lastLatencyMs = latencies[profile.id])
        }
        val selected = allProfiles.find { it.id == prefs.selectedProfileId } ?: allProfiles.first()
        val apps = rawApps
            .filter { (pkg, name) ->
                query.isBlank() ||
                    name.contains(query, ignoreCase = true) ||
                    pkg.contains(query, ignoreCase = true)
            }
            .map { (pkg, name) ->
                InstalledAppInfo(
                    packageName = pkg,
                    appName = name,
                    isExcludedFromVpn = prefs.excludedPackages.contains(pkg),
                )
            }

        VpnUiState(
            telemetry = telemetry,
            preferences = prefs,
            allProfiles = allProfiles,
            selectedProfile = selected,
            profileLatencies = latencies,
            pingingProfileId = pingingId,
            isPingingAll = pingingAll,
            installedApps = apps,
            appsSearchQuery = query,
            networkTransportLabel = transport,
            diagnosticsReport = diagnostics,
            isRunningDiagnostics = runningDiag,
            statusBannerMessage = banner,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = VpnUiState(),
    )

    init {
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
        loadInstalledApps()
    }

    override fun onCleared() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }

    fun startVpnConnection(overrideProfileId: String? = null) {
        val context = getApplication<Application>().applicationContext
        val profileId = overrideProfileId ?: uiState.value.selectedProfile.id
        val intent = Intent(context, MutigandaVpnService::class.java).apply {
            action = MutigandaVpnService.ACTION_CONNECT
            putExtra(MutigandaVpnService.EXTRA_PROFILE_ID, profileId)
            putExtra(MutigandaVpnService.EXTRA_STARTED_BY_APP, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopVpnConnection() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, MutigandaVpnService::class.java).apply {
            action = MutigandaVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            repository.selectProfile(profileId)
            if (uiState.value.telemetry.status == VpnStatus.CONNECTED) {
                startVpnConnection(overrideProfileId = profileId)
                _statusBanner.value = "Switched active Mutiganda Yvan VPN tunnel"
            }
        }
    }

    fun addCustomProfile(
        name: String,
        endpointHost: String,
        endpointPort: Int,
        publicKey: String,
        primaryDns: String,
        mtu: Int,
        protocol: VpnProtocol = VpnProtocol.WIREGUARD,
    ) {
        viewModelScope.launch {
            val cleanHost = endpointHost.trim()
            val cleanDns = primaryDns.trim().ifBlank { "1.1.1.1" }
            val mode = if (cleanHost.isNotBlank()) TunnelMode.CUSTOM_ENDPOINT else TunnelMode.LOCAL_DNS_SHIELD
            val resolvedProtocol = if (cleanHost.isNotBlank()) protocol else VpnProtocol.DNS_SHIELD
            val profile = VpnProfile(
                id = "custom_${UUID.randomUUID()}",
                name = name.trim().ifBlank { "Mutiganda Custom Node" },
                subtitle = if (cleanHost.isNotBlank()) {
                    "$cleanHost:$endpointPort • ${resolvedProtocol.badgeLabel}"
                } else {
                    "DNS $cleanDns • Local Shield"
                },
                mode = mode,
                protocol = resolvedProtocol,
                primaryDns = cleanDns,
                secondaryDns = "1.0.0.1",
                endpointHost = cleanHost,
                endpointPort = endpointPort.coerceIn(1, 65535),
                publicKey = publicKey.trim(),
                mtu = mtu.coerceIn(1280, 1500),
                isBuiltIn = false,
            )
            repository.saveCustomProfile(profile)
            _statusBanner.value = "Saved profile \"${profile.name}\""
        }
    }

    fun importShareLinks(rawText: String) {
        viewModelScope.launch {
            val parsed = VpnShareLinkParser.parseBatch(rawText)
            if (parsed.isEmpty()) {
                _statusBanner.value = "No valid vmess://, vless://, trojan://, ss://, socks://, or WireGuard config found."
            } else {
                repository.saveCustomProfilesBatch(parsed)
                _statusBanner.value = if (parsed.size == 1) {
                    "Imported \"${parsed.first().name}\""
                } else {
                    "Imported ${parsed.size} profiles to Mutiganda Yvan VPN"
                }
            }
        }
    }

    fun importWireGuardConfig(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val rawText = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                        it.readText()
                    }.orEmpty()
                }.getOrDefault("")
            }
            val parsedList = VpnShareLinkParser.parseBatch(rawText)
            if (parsedList.isNotEmpty()) {
                repository.saveCustomProfilesBatch(parsedList)
                _statusBanner.value = "Imported ${parsedList.size} profile(s) from file"
            } else {
                val fileName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                    ?: "Mutiganda Imported Tunnel"
                val wg = VpnPreferencesRepository.parseWireGuardConfig(fileName, rawText)
                if (wg != null) {
                    repository.saveCustomProfile(wg)
                    _statusBanner.value = "Imported config \"${wg.name}\""
                } else {
                    _statusBanner.value = "Could not parse configuration file."
                }
            }
        }
    }

    fun deleteCustomProfile(profileId: String) {
        viewModelScope.launch {
            repository.deleteCustomProfile(profileId)
        }
    }

    fun pingProfile(profile: VpnProfile) {
        viewModelScope.launch {
            _pingingProfileId.value = profile.id
            val latency = measureProfileLatency(profile)
            _profileLatencies.update { it + (profile.id to latency) }
            _pingingProfileId.value = null
            if (latency == null) {
                val target = profile.endpointHost.ifBlank { profile.primaryDns }
                _statusBanner.value = "$target unreachable from current network"
            }
        }
    }

    fun pingAllProfiles() {
        if (_isPingingAll.value) return
        viewModelScope.launch {
            _isPingingAll.value = true
            val profiles = uiState.value.allProfiles
            for (profile in profiles) {
                _pingingProfileId.value = profile.id
                val latency = measureProfileLatency(profile)
                _profileLatencies.update { it + (profile.id to latency) }
            }
            _pingingProfileId.value = null
            _isPingingAll.value = false
            _statusBanner.value = "Latency test finished for ${profiles.size} profiles"
        }
    }

    private suspend fun measureProfileLatency(profile: VpnProfile): Long? = withContext(Dispatchers.IO) {
        if (profile.endpointHost.isNotBlank()) {
            measureTcpLatency(profile.endpointHost, profile.endpointPort)
                ?: measureTcpLatency(profile.endpointHost, 443)
                ?: measureTcpLatency(profile.primaryDns, 53)
        } else {
            measureTcpLatency(profile.primaryDns, 53)
                ?: measureTcpLatency(profile.primaryDns, 443)
        }
    }

    fun toggleExcludedApp(packageName: String) {
        viewModelScope.launch {
            repository.toggleExcludedPackage(packageName)
        }
    }

    fun updateAppsSearchQuery(query: String) {
        _appsSearchQuery.value = query
    }

    fun updateDnsOverride(dns: String) {
        viewModelScope.launch {
            repository.updateDnsOverride(dns)
            if (uiState.value.telemetry.status == VpnStatus.CONNECTED) {
                startVpnConnection()
            }
        }
    }

    fun updateMtu(mtu: Int) {
        viewModelScope.launch {
            repository.updateMtu(mtu)
            if (uiState.value.telemetry.status == VpnStatus.CONNECTED) {
                startVpnConnection()
            }
        }
    }

    fun updateAllowLanBypass(allow: Boolean) {
        viewModelScope.launch {
            repository.updateAllowLanBypass(allow)
        }
    }

    fun updateDarkTheme(dark: Boolean) {
        viewModelScope.launch {
            repository.updateDarkTheme(dark)
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun runDiagnostics() {
        viewModelScope.launch {
            _isRunningDiagnostics.value = true
            val state = uiState.value
            val dnsTarget = state.preferences.dnsOverride.ifBlank { state.selectedProfile.primaryDns }
            val endpointHost = state.selectedProfile.endpointHost.takeIf { it.isNotBlank() }
            val report = withContext(Dispatchers.IO) {
                val activeNet = connectivityManager.activeNetwork
                val caps = connectivityManager.getNetworkCapabilities(activeNet)
                val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                val dnsMs = measureTcpLatency(dnsTarget, 53) ?: measureTcpLatency(dnsTarget, 443)
                val endpointMs = endpointHost?.let {
                    measureTcpLatency(it, state.selectedProfile.endpointPort) ?: measureTcpLatency(it, 443)
                }
                NetworkDiagnosticsReport(
                    transportLabel = readCurrentTransportLabel(),
                    isInternetValidated = validated,
                    dnsProbeTarget = dnsTarget,
                    dnsProbeLatencyMs = dnsMs,
                    endpointTarget = endpointHost,
                    endpointLatencyMs = endpointMs,
                    checkedAtMillis = System.currentTimeMillis(),
                )
            }
            _diagnosticsReport.value = report
            _isRunningDiagnostics.value = false
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, 0)
            }

            val ownPkg = context.packageName
            val apps = resolved
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == ownPkg) return@mapNotNull null
                    val label = info.loadLabel(pm).toString().trim().ifBlank { pkg }
                    pkg to label
                }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }

            _rawApps.value = apps
        }
    }

    private fun readCurrentTransportLabel(): String {
        val activeNetwork = connectivityManager.activeNetwork ?: return "Offline"
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return "Offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN Tunnel Active"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi Network"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular Data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
    }

    private fun measureTcpLatency(host: String, port: Int): Long? {
        return try {
            val start = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1800)
            }
            ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
        } catch (_: Exception) {
            null
        }
    }
}
