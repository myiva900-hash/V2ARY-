package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.VpnStatus
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.MonoReadoutSmall
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.TunnelCyan
import java.util.Locale

object VpnFormatters {

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec < 1024L) return "$bytesPerSec B/s"
        val kb = bytesPerSec / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB/s", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB/s", mb)
    }

    fun formatDuration(elapsedMillis: Long): String {
        val totalSeconds = (elapsedMillis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}

@Composable
fun MutigandaBrandHeader(
    networkTransportLabel: String,
    vpnStatus: VpnStatus,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_mutiganda_vpn_badge),
                contentDescription = "Mutiganda Yvan VPN emblem",
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Mutiganda Yvan VPN",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Private Tunnel Engine",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        val pillColor = when (vpnStatus) {
            VpnStatus.CONNECTED -> ShieldEmerald
            VpnStatus.CONNECTING -> AmberPulse
            VpnStatus.ERROR -> CoralAlert
            VpnStatus.DISCONNECTED -> TunnelCyan
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.border(
                width = 1.dp,
                color = pillColor.copy(alpha = 0.4f),
                shape = CircleShape,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(pillColor),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.NetworkCheck,
                    contentDescription = null,
                    tint = pillColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = networkTransportLabel,
                    style = MonoReadoutSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
