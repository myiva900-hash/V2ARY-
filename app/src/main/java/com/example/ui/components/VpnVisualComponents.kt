package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.VpnStatus
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.MonoReadoutSmall
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.TunnelCyan

@Composable
fun AtmosphericVpnBackground(
    vpnStatus: VpnStatus,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val glowColor by animateColorAsState(
        targetValue = when (vpnStatus) {
            VpnStatus.CONNECTED -> ShieldEmerald
            VpnStatus.CONNECTING -> AmberPulse
            VpnStatus.ERROR -> CoralAlert
            VpnStatus.DISCONNECTED -> TunnelCyan
        },
        animationSpec = tween(durationMillis = 500),
        label = "ambientGlow",
    )

    val bgBase = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgBase)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.16f),
                            glowColor.copy(alpha = 0.04f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.14f),
                        radius = size.maxDimension * 0.65f,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            TunnelCyan.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.82f),
                        radius = size.maxDimension * 0.45f,
                    ),
                )
            },
    ) {
        content()
    }
}

@Composable
fun OrbitalShieldControl(
    vpnStatus: VpnStatus,
    accentColor: Color,
    onToggleConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnected = vpnStatus == VpnStatus.CONNECTED
    val isConnecting = vpnStatus == VpnStatus.CONNECTING

    val infiniteTransition = rememberInfiniteTransition(label = "orbital")
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    isConnecting -> 2200
                    isConnected -> 9000
                    else -> 22000
                },
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "outerOrbit",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.58f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isConnecting) 700 else 1800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (isConnected) 1.04f else 1.0f,
        animationSpec = spring(),
        label = "shieldScale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(214.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f

            // Soft radial halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (isConnected || isConnecting) pulseAlpha * 0.5f else 0.12f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = maxR,
                ),
                radius = maxR,
                center = center,
            )

            // Outer dashed orbital ring
            rotate(degrees = outerAngle, pivot = center) {
                drawCircle(
                    color = accentColor.copy(alpha = if (isConnected) 0.42f else 0.2f),
                    radius = maxR - 10.dp.toPx(),
                    center = center,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f),
                    ),
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            accentColor.copy(alpha = 0.85f),
                            TunnelCyan.copy(alpha = 0.85f),
                            Color.Transparent,
                        ),
                        center = center,
                    ),
                    startAngle = -35f,
                    sweepAngle = 115f,
                    useCenter = false,
                    topLeft = Offset(10.dp.toPx(), 10.dp.toPx()),
                    size = Size(size.width - 20.dp.toPx(), size.height - 20.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // Inner counter-rotating precision arc
            rotate(degrees = -outerAngle * 0.65f, pivot = center) {
                val inset = 24.dp.toPx()
                drawArc(
                    color = accentColor.copy(alpha = if (isConnected) 0.65f else 0.28f),
                    startAngle = 30f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2f, size.height - inset * 2f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    color = accentColor.copy(alpha = if (isConnected) 0.65f else 0.28f),
                    startAngle = 210f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2f, size.height - inset * 2f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = if (isConnected) accentColor else MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 16.dp,
            modifier = Modifier
                .size(134.dp)
                .scale(buttonScale)
                .minimumInteractiveComponentSize()
                .testTag("vpn_connect_button")
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(accentColor, TunnelCyan),
                    ),
                    shape = CircleShape,
                )
                .clickable(onClick = onToggleConnection),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = if (isConnected) {
                        "Disconnect Mutiganda Yvan VPN"
                    } else {
                        "Connect Mutiganda Yvan VPN"
                    },
                    tint = if (isConnected) MaterialTheme.colorScheme.onPrimary else accentColor,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (vpnStatus) {
                        VpnStatus.CONNECTED -> "PROTECTED"
                        VpnStatus.CONNECTING -> "LINKING"
                        else -> "CONNECT"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isConnected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
fun ThroughputSparklineCard(
    rxBytes: Long,
    txBytes: Long,
    rxBytesPerSec: Long,
    txBytesPerSec: Long,
    rxHistory: List<Long>,
    txHistory: List<Long>,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        ),
        modifier = modifier
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SpeedStatPill(
                    label = "DOWNLOAD",
                    speed = VpnFormatters.formatSpeed(rxBytesPerSec),
                    total = VpnFormatters.formatBytes(rxBytes),
                    icon = Icons.Filled.ArrowDownward,
                    accent = ShieldEmerald,
                    modifier = Modifier.weight(1f),
                )
                SpeedStatPill(
                    label = "UPLOAD",
                    speed = VpnFormatters.formatSpeed(txBytesPerSec),
                    total = VpnFormatters.formatBytes(txBytes),
                    icon = Icons.Filled.ArrowUpward,
                    accent = TunnelCyan,
                    modifier = Modifier.weight(1f),
                )
            }

            // Live Real-Time TrafficStats Sparkline
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            ) {
                val maxSample = maxOf(
                    rxHistory.maxOrNull() ?: 0L,
                    txHistory.maxOrNull() ?: 0L,
                    1024L,
                ).toFloat()

                // Baseline grid guide
                drawLine(
                    color = Color.White.copy(alpha = 0.06f),
                    start = Offset(0f, size.height * 0.5f),
                    end = Offset(size.width, size.height * 0.5f),
                    strokeWidth = 1.dp.toPx(),
                )

                drawSparkSeries(
                    values = rxHistory,
                    maxValue = maxSample,
                    lineColor = ShieldEmerald,
                    fillArea = true,
                )
                drawSparkSeries(
                    values = txHistory,
                    maxValue = maxSample,
                    lineColor = TunnelCyan,
                    fillArea = false,
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSparkSeries(
    values: List<Long>,
    maxValue: Float,
    lineColor: Color,
    fillArea: Boolean,
) {
    if (values.size < 2) return
    val stepX = size.width / (values.size - 1).coerceAtLeast(1)
    val linePath = Path()
    val areaPath = Path()

    values.forEachIndexed { idx, v ->
        val x = idx * stepX
        val ratio = (v.toFloat() / maxValue).coerceIn(0f, 1f)
        val y = size.height - (ratio * (size.height - 6f)) - 3f
        if (idx == 0) {
            linePath.moveTo(x, y)
            areaPath.moveTo(x, size.height)
            areaPath.lineTo(x, y)
        } else {
            val prevX = (idx - 1) * stepX
            val prevRatio = (values[idx - 1].toFloat() / maxValue).coerceIn(0f, 1f)
            val prevY = size.height - (prevRatio * (size.height - 6f)) - 3f
            val midX = (prevX + x) / 2f
            linePath.cubicTo(midX, prevY, midX, y, x, y)
            areaPath.cubicTo(midX, prevY, midX, y, x, y)
        }
    }

    if (fillArea) {
        areaPath.lineTo(size.width, size.height)
        areaPath.close()
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.28f),
                    Color.Transparent,
                ),
            ),
        )
    }

    drawPath(
        path = linePath,
        color = lineColor,
        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
    )
}

@Composable
private fun SpeedStatPill(
    label: String,
    speed: String,
    total: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = speed,
                style = MonoReadoutSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = total,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                maxLines = 1,
            )
        }
    }
}
