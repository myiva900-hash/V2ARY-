package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.VpnUiState
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.MonoReadoutSmall
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.TunnelCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InspectorScreen(
    uiState: VpnUiState,
    onRunDiagnostics: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleExcludedApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Tunnel Inspector",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Real-time TUN interface diagnostics, captured packet headers, and per-app split tunneling.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Network Diagnostics Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(22.dp),
                        ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.NetworkCheck,
                                    contentDescription = null,
                                    tint = ShieldEmerald,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Network Diagnostics",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }

                            Button(
                                onClick = onRunDiagnostics,
                                enabled = !uiState.isRunningDiagnostics,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("run_diagnostics_button"),
                            ) {
                                if (uiState.isRunningDiagnostics) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Probing")
                                } else {
                                    Text("Run Probe")
                                }
                            }
                        }

                        val report = uiState.diagnosticsReport
                        if (report == null) {
                            Text(
                                text = "Transport: ${uiState.networkTransportLabel}. Tap Run Probe to measure real DNS and endpoint reachability.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DiagRow("Transport", report.transportLabel)
                                DiagRow(
                                    "Internet Validated",
                                    if (report.isInternetValidated) "Yes" else "Unvalidated / Limited",
                                )
                                DiagRow(
                                    "DNS (${report.dnsProbeTarget})",
                                    report.dnsProbeLatencyMs?.let { "$it ms" } ?: "Unreachable",
                                )
                                if (report.endpointTarget != null) {
                                    DiagRow(
                                        "Node (${report.endpointTarget})",
                                        report.endpointLatencyMs?.let { "$it ms" } ?: "Timed out",
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Live TUN Packet Feed Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(22.dp),
                        ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.BugReport,
                                    contentDescription = null,
                                    tint = TunnelCyan,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Live TUN Packets",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = ShieldEmerald.copy(alpha = 0.14f),
                            ) {
                                Text(
                                    text = "${uiState.telemetry.dnsQueriesForwarded} DNS • ${uiState.telemetry.packetsInspected} PKT",
                                    style = MonoReadoutSmall,
                                    color = ShieldEmerald,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }

                        if (uiState.telemetry.recentPackets.isEmpty()) {
                            Text(
                                text = "No TUN packets captured yet. Connect Mutiganda Yvan VPN to inspect live DNS & tunnel traffic.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // Segmented protocol bar
                            val recent = uiState.telemetry.recentPackets
                            val udpCount = recent.count { it.protocol == "UDP" }.coerceAtLeast(0)
                            val tcpCount = recent.count { it.protocol == "TCP" }.coerceAtLeast(0)
                            val otherCount = (recent.size - udpCount - tcpCount).coerceAtLeast(0)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.background),
                            ) {
                                if (udpCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .weight(udpCount.toFloat())
                                            .height(6.dp)
                                            .background(ShieldEmerald),
                                    )
                                }
                                if (tcpCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .weight(tcpCount.toFloat())
                                            .height(6.dp)
                                            .background(TunnelCyan),
                                    )
                                }
                                if (otherCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .weight(otherCount.toFloat())
                                            .height(6.dp)
                                            .background(AmberPulse),
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                recent.take(8).forEach { pkt ->
                                    val protoColor = when (pkt.protocol) {
                                        "UDP" -> ShieldEmerald
                                        "TCP" -> TunnelCyan
                                        else -> AmberPulse
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = protoColor.copy(alpha = 0.16f),
                                            ) {
                                                Text(
                                                    text = pkt.protocol,
                                                    style = MonoReadoutSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = protoColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${pkt.destinationIp}:${pkt.destinationPort}",
                                                style = MonoReadoutSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Text(
                                            text = "${pkt.sizeBytes}B • ${timeFormatter.format(Date(pkt.timestampMillis))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Split Tunneling Header & Search
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Split Tunneling",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = TunnelCyan.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = "${uiState.preferences.excludedPackages.size} bypassed",
                                style = MonoReadoutSmall,
                                color = TunnelCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Text(
                        text = "Toggle ON to let an app bypass the Mutiganda Yvan VPN tunnel. Reconnect VPN to apply changes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = uiState.appsSearchQuery,
                        onValueChange = onSearchQueryChange,
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                        },
                        placeholder = { Text("Filter installed apps…") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("split_tunnel_search"),
                    )
                }
            }

            if (uiState.installedApps.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "No matching launcher apps found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(uiState.installedApps, key = { it.packageName }) { app ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (app.isExcludedFromVpn) {
                                    TunnelCyan.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = RoundedCornerShape(16.dp),
                            ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = app.packageName,
                                    style = MonoReadoutSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = app.isExcludedFromVpn,
                                onCheckedChange = { onToggleExcludedApp(app.packageName) },
                                modifier = Modifier.testTag("split_switch_${app.packageName}"),
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MonoReadoutSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
