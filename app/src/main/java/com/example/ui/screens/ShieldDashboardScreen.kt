package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.VpnStatus
import com.example.ui.VpnUiState
import com.example.ui.components.MutigandaBrandHeader
import com.example.ui.components.OrbitalShieldControl
import com.example.ui.components.ThroughputSparklineCard
import com.example.ui.components.VpnFormatters
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.MonoReadoutLarge
import com.example.ui.theme.MonoReadoutSmall
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.TunnelCyan
import kotlinx.coroutines.delay

@Composable
fun ShieldDashboardScreen(
    uiState: VpnUiState,
    onToggleConnection: () -> Unit,
    onQuickSelectProfile: (String) -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenInspector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val telemetry = uiState.telemetry
    val isConnected = telemetry.status == VpnStatus.CONNECTED

    var elapsedMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(telemetry.status, telemetry.connectedSinceMillis) {
        val since = telemetry.connectedSinceMillis
        if (telemetry.status == VpnStatus.CONNECTED && since != null) {
            while (true) {
                elapsedMillis = (System.currentTimeMillis() - since).coerceAtLeast(0L)
                delay(1000L)
            }
        } else {
            elapsedMillis = 0L
        }
    }

    val accentColor by animateColorAsState(
        targetValue = when (telemetry.status) {
            VpnStatus.CONNECTED -> ShieldEmerald
            VpnStatus.CONNECTING -> AmberPulse
            VpnStatus.ERROR -> CoralAlert
            VpnStatus.DISCONNECTED -> TunnelCyan
        },
        animationSpec = tween(durationMillis = 360),
        label = "accentColor",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MutigandaBrandHeader(
                networkTransportLabel = uiState.networkTransportLabel,
                vpnStatus = telemetry.status,
            )

            // Hero Orbital Shield Card
            Card(
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = if (isConnected) 0.6f else 0.3f),
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            ),
                        ),
                        shape = RoundedCornerShape(30.dp),
                    ),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_vpn_network_mesh),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(if (isConnected) 0.45f else 0.24f),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    ),
                                ),
                            ),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.14f),
                            modifier = Modifier.border(
                                width = 1.dp,
                                color = accentColor.copy(alpha = 0.45f),
                                shape = CircleShape,
                            ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    imageVector = if (isConnected) Icons.Filled.Lock else Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (telemetry.status) {
                                        VpnStatus.CONNECTED -> "PROTECTED • MUTIGANDA YVAN VPN"
                                        VpnStatus.CONNECTING -> "NEGOTIATING TUN INTERFACE…"
                                        VpnStatus.ERROR -> "TUNNEL ERROR"
                                        VpnStatus.DISCONNECTED -> "READY TO PROTECT"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OrbitalShieldControl(
                            vpnStatus = telemetry.status,
                            accentColor = accentColor,
                            onToggleConnection = onToggleConnection,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = if (isConnected) VpnFormatters.formatDuration(elapsedMillis) else "00:00:00",
                            style = MonoReadoutLarge,
                            color = if (isConnected) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (telemetry.errorMessage != null) {
                                telemetry.errorMessage
                            } else if (isConnected) {
                                if (telemetry.alwaysOnStarted) {
                                    "Always-On System Tunnel Active"
                                } else {
                                    "Encrypted DNS & TUN Interface Secured"
                                }
                            } else {
                                "Tap the orbital shield to activate protection"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (telemetry.errorMessage != null) {
                                CoralAlert
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Quick-Switch Node Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(uiState.allProfiles, key = { it.id }) { profile ->
                    val selected = profile.id == uiState.selectedProfile.id
                    Surface(
                        shape = CircleShape,
                        color = if (selected) {
                            ShieldEmerald.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                color = if (selected) ShieldEmerald else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable { onQuickSelectProfile(profile.id) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = ShieldEmerald,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = profile.name.removePrefix("Mutiganda ").take(22),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) ShieldEmerald else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            profile.lastLatencyMs?.let { ms ->
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${ms}ms",
                                    style = MonoReadoutSmall,
                                    color = TunnelCyan,
                                )
                            }
                        }
                    }
                }
            }

            // Active Profile Summary Card
            val activeProfile = uiState.selectedProfile
            val effectiveDns = uiState.preferences.dnsOverride.ifBlank { activeProfile.primaryDns }
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_profile_card")
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(22.dp),
                    )
                    .clickable(onClick = onOpenProfiles),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ACTIVE TUNNEL • ${activeProfile.protocol.badgeLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = activeProfile.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val rttSuffix = telemetry.endpointReachabilityMs?.let { " • ${it}ms RTT" }.orEmpty()
                        Text(
                            text = "${activeProfile.mode.displayName} • DNS $effectiveDns$rttSuffix",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Manage profiles",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Live Throughput + Sparkline Chart
            ThroughputSparklineCard(
                rxBytes = telemetry.rxBytes,
                txBytes = telemetry.txBytes,
                rxBytesPerSec = telemetry.rxBytesPerSec,
                txBytesPerSec = telemetry.txBytesPerSec,
                rxHistory = telemetry.speedHistoryRx,
                txHistory = telemetry.speedHistoryTx,
            )

            // Tunnel Interface Telemetry Strip
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tunnel_specs_card")
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(22.dp),
                    )
                    .clickable(onClick = onOpenInspector),
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
                                imageVector = Icons.Filled.Dns,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tunnel Interface Telemetry",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = "Inspect →",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SpecColumn(label = "TUN IP", value = telemetry.localTunnelIp)
                        SpecColumn(label = "DNS", value = effectiveDns)
                        SpecColumn(label = "MTU", value = "${uiState.preferences.mtu}")
                        SpecColumn(label = "PACKETS", value = "${telemetry.packetsInspected}")
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = ShieldEmerald,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Mutiganda Yvan VPN • Private Android Tunnel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SpecColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MonoReadoutSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
