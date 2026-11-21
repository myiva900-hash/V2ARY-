package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.TunnelMode
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import com.example.ui.VpnUiState
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.MonoReadoutSmall
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.TunnelCyan

private enum class ProfileFilter(val label: String) {
    ALL("All"),
    DNS("DNS Shield"),
    XRAY("VLESS / VMess"),
    WIREGUARD("WireGuard"),
    OTHER("Trojan / SS"),
}

@Composable
fun ProfilesScreen(
    uiState: VpnUiState,
    onSelectProfile: (String) -> Unit,
    onPingProfile: (VpnProfile) -> Unit,
    onPingAllProfiles: () -> Unit,
    onAddCustomProfile: (
        name: String,
        host: String,
        port: Int,
        publicKey: String,
        dns: String,
        mtu: Int,
        protocol: VpnProtocol,
    ) -> Unit,
    onImportShareLinks: (String) -> Unit,
    onImportWireGuardUri: (Uri) -> Unit,
    onDeleteProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showPasteUriDialog by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf(ProfileFilter.ALL) }
    val clipboardManager = LocalClipboardManager.current

    val openConfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onImportWireGuardUri(uri)
        }
    }

    val filteredProfiles = remember(uiState.allProfiles, activeFilter) {
        uiState.allProfiles.filter { profile ->
            when (activeFilter) {
                ProfileFilter.ALL -> true
                ProfileFilter.DNS -> profile.protocol == VpnProtocol.DNS_SHIELD
                ProfileFilter.XRAY -> profile.protocol == VpnProtocol.VLESS || profile.protocol == VpnProtocol.VMESS
                ProfileFilter.WIREGUARD -> profile.protocol == VpnProtocol.WIREGUARD
                ProfileFilter.OTHER -> profile.protocol == VpnProtocol.TROJAN ||
                    profile.protocol == VpnProtocol.SHADOWSOCKS ||
                    profile.protocol == VpnProtocol.SOCKS5
            }
        }
    }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Tunnel Profiles",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Encrypted DNS shields, vmess://, vless://, trojan://, ss://, and WireGuard nodes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onPingAllProfiles,
                        enabled = !uiState.isPingingAll,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("ping_all_profiles_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (uiState.isPingingAll) "Testing" else "Test All")
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("add_profile_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Node")
                        }

                        OutlinedButton(
                            onClick = { showPasteUriDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("paste_uri_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentPasteGo,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste URI")
                        }
                    }

                    OutlinedButton(
                        onClick = { openConfLauncher.launch(arrayOf("text/plain", "*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("import_conf_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import .conf or Share-Link Text File")
                    }
                }
            }

            // Protocol Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ProfileFilter.entries) { filter ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { activeFilter = filter },
                            label = { Text(filter.label) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }
            }

            if (filteredProfiles.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(20.dp),
                            ),
                    ) {
                        Text(
                            text = "No profiles in ${activeFilter.label}. Tap Add Node or Paste URI to import one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
            } else {
                items(filteredProfiles, key = { it.id }) { profile ->
                    val isSelected = profile.id == uiState.selectedProfile.id
                    val isPinging = uiState.pingingProfileId == profile.id
                    ProfileCard(
                        profile = profile,
                        isSelected = isSelected,
                        isPinging = isPinging,
                        onSelect = { onSelectProfile(profile.id) },
                        onPing = { onPingProfile(profile) },
                        onDelete = if (!profile.isBuiltIn) {
                            { onDeleteProfile(profile.id) }
                        } else null,
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddCustomProfileDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, host, port, pubKey, dns, mtu, protocol ->
                onAddCustomProfile(name, host, port, pubKey, dns, mtu, protocol)
                showAddDialog = false
            },
        )
    }

    if (showPasteUriDialog) {
        PasteShareUriDialog(
            initialText = clipboardManager.getText()?.text.orEmpty(),
            onDismiss = { showPasteUriDialog = false },
            onImport = { raw ->
                onImportShareLinks(raw)
                showPasteUriDialog = false
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: VpnProfile,
    isSelected: Boolean,
    isPinging: Boolean,
    onSelect: () -> Unit,
    onPing: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val borderColor = if (isSelected) ShieldEmerald else MaterialTheme.colorScheme.outline
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_card_${profile.id}")
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(22.dp),
            )
            .clickable(onClick = onSelect),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (profile.mode == TunnelMode.CUSTOM_ENDPOINT) {
                                TunnelCyan.copy(alpha = 0.16f)
                            } else {
                                ShieldEmerald.copy(alpha = 0.16f)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (profile.mode == TunnelMode.CUSTOM_ENDPOINT) {
                            Icons.Filled.VpnKey
                        } else {
                            Icons.Filled.Security
                        },
                        contentDescription = null,
                        tint = if (profile.mode == TunnelMode.CUSTOM_ENDPOINT) TunnelCyan else ShieldEmerald,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Surface(
                            shape = CircleShape,
                            color = TunnelCyan.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = profile.protocol.badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = TunnelCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = profile.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isSelected) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected profile",
                        tint = ShieldEmerald,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                    ) {
                        Text(
                            text = "DNS ${profile.primaryDns}",
                            style = MonoReadoutSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }

                    profile.lastLatencyMs?.let { ms ->
                        val latencyColor = when {
                            ms < 85L -> ShieldEmerald
                            ms < 190L -> AmberPulse
                            else -> CoralAlert
                        }
                        Surface(
                            shape = CircleShape,
                            color = latencyColor.copy(alpha = 0.14f),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(latencyColor),
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "$ms ms",
                                    style = MonoReadoutSmall.copy(fontWeight = FontWeight.Bold),
                                    color = latencyColor,
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = onPing,
                        enabled = !isPinging,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("ping_profile_${profile.id}"),
                    ) {
                        if (isPinging) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.NetworkPing,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPinging) "Testing" else "Ping")
                    }

                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_profile_${profile.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete custom profile",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCustomProfileDialog(
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        host: String,
        port: Int,
        publicKey: String,
        dns: String,
        mtu: Int,
        protocol: VpnProtocol,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var publicKey by remember { mutableStateOf("") }
    var dns by remember { mutableStateOf("1.1.1.1") }
    var mtu by remember { mutableStateOf("1420") }
    var selectedProtocol by remember { mutableStateOf(VpnProtocol.VLESS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom VPN Node") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(VpnProtocol.VLESS, VpnProtocol.VMESS, VpnProtocol.WIREGUARD).forEach { proto ->
                        FilterChip(
                            selected = selectedProtocol == proto,
                            onClick = { selectedProtocol = proto },
                            label = { Text(proto.badgeLabel) },
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    placeholder = { Text("Mutiganda Private Server") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_profile_name_input"),
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Endpoint Host / IP (optional)") },
                    placeholder = { Text("vpn.example.com") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_profile_host_input"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit) },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = mtu,
                        onValueChange = { mtu = it.filter(Char::isDigit) },
                        label = { Text("MTU") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = dns,
                    onValueChange = { dns = it },
                    label = { Text("Primary DNS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = publicKey,
                    onValueChange = { publicKey = it },
                    label = { Text("UUID / Public Key (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        host,
                        port.toIntOrNull() ?: 443,
                        publicKey,
                        dns,
                        mtu.toIntOrNull() ?: 1420,
                        selectedProtocol,
                    )
                },
                modifier = Modifier.testTag("save_custom_profile_button"),
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PasteShareUriDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var uriText by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import VPN Share Links") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paste one or more vless://, vmess://, trojan://, ss://, socks:// links or WireGuard .conf text:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = uriText,
                    onValueChange = { uriText = it },
                    placeholder = { Text("vless://... or vmess://...") },
                    minLines = 4,
                    maxLines = 7,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste_uri_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(uriText) },
                enabled = uriText.isNotBlank(),
                modifier = Modifier.testTag("confirm_import_uri_button"),
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
