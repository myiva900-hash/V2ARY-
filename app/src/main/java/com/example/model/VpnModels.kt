package com.example.model

enum class VpnStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

enum class TunnelMode(val displayName: String) {
    LOCAL_DNS_SHIELD("Encrypted DNS Shield"),
    CUSTOM_ENDPOINT("Custom Endpoint Tunnel"),
}

enum class VpnProtocol(val badgeLabel: String) {
    DNS_SHIELD("DNS SHIELD"),
    WIREGUARD("WIREGUARD"),
    VLESS("VLESS"),
    VMESS("VMESS"),
    TROJAN("TROJAN"),
    SHADOWSOCKS("SS"),
    SOCKS5("SOCKS5"),
}

data class VpnProfile(
    val id: String,
    val name: String,
    val subtitle: String,
    val mode: TunnelMode,
    val protocol: VpnProtocol = VpnProtocol.DNS_SHIELD,
    val primaryDns: String,
    val secondaryDns: String,
    val interfaceAddress: String = "10.8.0.2",
    val interfacePrefix: Int = 24,
    val endpointHost: String = "",
    val endpointPort: Int = 51820,
    val publicKey: String = "",
    val sni: String = "",
    val transportNetwork: String = "udp",
    val securityType: String = "none",
    val mtu: Int = 1420,
    val isBuiltIn: Boolean = false,
    val lastLatencyMs: Long? = null,
)

data class CapturedPacketInfo(
    val id: Long,
    val protocol: String,
    val destinationIp: String,
    val destinationPort: Int,
    val sizeBytes: Int,
    val timestampMillis: Long,
)

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isExcludedFromVpn: Boolean,
)

data class NetworkDiagnosticsReport(
    val transportLabel: String,
    val isInternetValidated: Boolean,
    val dnsProbeTarget: String,
    val dnsProbeLatencyMs: Long?,
    val endpointTarget: String?,
    val endpointLatencyMs: Long?,
    val checkedAtMillis: Long,
)

data class VpnPreferences(
    val selectedProfileId: String = "mutiganda_cloudflare",
    val customProfiles: List<VpnProfile> = emptyList(),
    val excludedPackages: Set<String> = emptySet(),
    val dnsOverride: String = "",
    val mtu: Int = 1420,
    val allowLanBypass: Boolean = true,
    val darkTheme: Boolean = true,
)

data class VpnTelemetryState(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val activeProfileId: String? = null,
    val activeProfileName: String = "Mutiganda Shield • Fast DNS",
    val activeProtocol: VpnProtocol = VpnProtocol.DNS_SHIELD,
    val activeMode: TunnelMode = TunnelMode.LOCAL_DNS_SHIELD,
    val connectedSinceMillis: Long? = null,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val rxBytesPerSec: Long = 0L,
    val txBytesPerSec: Long = 0L,
    val speedHistoryRx: List<Long> = List(24) { 0L },
    val speedHistoryTx: List<Long> = List(24) { 0L },
    val packetsInspected: Long = 0L,
    val dnsQueriesForwarded: Long = 0L,
    val recentPackets: List<CapturedPacketInfo> = emptyList(),
    val localTunnelIp: String = "10.8.0.2/24",
    val activeDns: String = "1.1.1.1",
    val activeMtu: Int = 1420,
    val excludedAppsCount: Int = 0,
    val endpointReachabilityMs: Long? = null,
    val alwaysOnStarted: Boolean = false,
    val errorMessage: String? = null,
)

object BuiltInProfiles {
    val defaults = listOf(
        VpnProfile(
            id = "mutiganda_cloudflare",
            name = "Mutiganda Shield • Fast DNS",
            subtitle = "Cloudflare 1.1.1.1 • Low-latency encrypted tunnel DNS",
            mode = TunnelMode.LOCAL_DNS_SHIELD,
            protocol = VpnProtocol.DNS_SHIELD,
            primaryDns = "1.1.1.1",
            secondaryDns = "1.0.0.1",
            isBuiltIn = true,
        ),
        VpnProfile(
            id = "mutiganda_adguard",
            name = "Mutiganda Ad & Tracker Guard",
            subtitle = "AdGuard 94.140.14.14 • Blocks ads & telemetry trackers",
            mode = TunnelMode.LOCAL_DNS_SHIELD,
            protocol = VpnProtocol.DNS_SHIELD,
            primaryDns = "94.140.14.14",
            secondaryDns = "94.140.15.15",
            isBuiltIn = true,
        ),
        VpnProfile(
            id = "mutiganda_quad9",
            name = "Mutiganda Quad9 Malware Lock",
            subtitle = "Quad9 9.9.9.9 • Threat intelligence & phishing defense",
            mode = TunnelMode.LOCAL_DNS_SHIELD,
            protocol = VpnProtocol.DNS_SHIELD,
            primaryDns = "9.9.9.9",
            secondaryDns = "149.112.112.112",
            isBuiltIn = true,
        ),
    )
}
