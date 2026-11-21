package com.example.vpn

import com.example.model.CapturedPacketInfo
import com.example.model.TunnelMode
import com.example.model.VpnProtocol
import com.example.model.VpnStatus
import com.example.model.VpnTelemetryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

object VpnStateHolder {

    private val _state = MutableStateFlow(VpnTelemetryState())
    val state: StateFlow<VpnTelemetryState> = _state.asStateFlow()

    private val packetIdSeq = AtomicLong(1L)

    fun setConnecting(
        profileId: String,
        profileName: String,
        protocol: VpnProtocol,
        mode: TunnelMode,
        localTunnelIp: String,
        activeDns: String,
        activeMtu: Int,
        excludedAppsCount: Int,
        alwaysOnStarted: Boolean,
    ) {
        _state.update {
            it.copy(
                status = VpnStatus.CONNECTING,
                activeProfileId = profileId,
                activeProfileName = profileName,
                activeProtocol = protocol,
                activeMode = mode,
                localTunnelIp = localTunnelIp,
                activeDns = activeDns,
                activeMtu = activeMtu,
                excludedAppsCount = excludedAppsCount,
                endpointReachabilityMs = null,
                alwaysOnStarted = alwaysOnStarted,
                errorMessage = null,
                rxBytes = 0L,
                txBytes = 0L,
                rxBytesPerSec = 0L,
                txBytesPerSec = 0L,
                speedHistoryRx = List(24) { 0L },
                speedHistoryTx = List(24) { 0L },
                packetsInspected = 0L,
                dnsQueriesForwarded = 0L,
                recentPackets = emptyList(),
            )
        }
    }

    fun setConnected(connectedSinceMillis: Long) {
        _state.update {
            it.copy(
                status = VpnStatus.CONNECTED,
                connectedSinceMillis = connectedSinceMillis,
                errorMessage = null,
            )
        }
    }

    fun setEndpointReachability(latencyMs: Long?) {
        _state.update { it.copy(endpointReachabilityMs = latencyMs) }
    }

    fun setError(message: String) {
        _state.update {
            it.copy(
                status = VpnStatus.ERROR,
                connectedSinceMillis = null,
                rxBytesPerSec = 0L,
                txBytesPerSec = 0L,
                errorMessage = message,
            )
        }
    }

    fun setDisconnected() {
        _state.update {
            it.copy(
                status = VpnStatus.DISCONNECTED,
                connectedSinceMillis = null,
                rxBytesPerSec = 0L,
                txBytesPerSec = 0L,
                speedHistoryRx = List(24) { 0L },
                speedHistoryTx = List(24) { 0L },
                endpointReachabilityMs = null,
                alwaysOnStarted = false,
                errorMessage = null,
            )
        }
    }

    fun updateTrafficStats(
        rxBytes: Long,
        txBytes: Long,
        rxBytesPerSec: Long,
        txBytesPerSec: Long,
    ) {
        _state.update { current ->
            val nextRxHistory = (current.speedHistoryRx.drop(1) + rxBytesPerSec).takeLast(24)
            val nextTxHistory = (current.speedHistoryTx.drop(1) + txBytesPerSec).takeLast(24)
            current.copy(
                rxBytes = rxBytes,
                txBytes = txBytes,
                rxBytesPerSec = rxBytesPerSec,
                txBytesPerSec = txBytesPerSec,
                speedHistoryRx = nextRxHistory,
                speedHistoryTx = nextTxHistory,
            )
        }
    }

    fun recordPacket(
        protocol: String,
        destinationIp: String,
        destinationPort: Int,
        sizeBytes: Int,
        isDnsForwarded: Boolean = false,
    ) {
        val entry = CapturedPacketInfo(
            id = packetIdSeq.getAndIncrement(),
            protocol = protocol,
            destinationIp = destinationIp,
            destinationPort = destinationPort,
            sizeBytes = sizeBytes,
            timestampMillis = System.currentTimeMillis(),
        )
        _state.update { current ->
            val updatedList = (listOf(entry) + current.recentPackets).take(40)
            current.copy(
                packetsInspected = current.packetsInspected + 1,
                dnsQueriesForwarded = if (isDnsForwarded) current.dnsQueriesForwarded + 1 else current.dnsQueriesForwarded,
                recentPackets = updatedList,
            )
        }
    }
}
