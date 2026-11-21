package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.VpnPreferencesRepository
import com.example.model.BuiltInProfiles
import com.example.model.TunnelMode
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

class MutigandaVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val EXTRA_STARTED_BY_APP = "extra_started_by_app"

        private const val NOTIFICATION_CHANNEL_ID = "mutiganda_vpn_channel"
        private const val NOTIFICATION_ID = 7041
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnelJob: Job? = null
    private var telemetryJob: Job? = null
    private var tunInterface: ParcelFileDescriptor? = null
    private val packetIdGenerator = AtomicInteger(100)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            stopTunnel()
            return START_NOT_STICKY
        }

        val startedByApp = intent?.getBooleanExtra(EXTRA_STARTED_BY_APP, false) == true
        val alwaysOnStarted = !startedByApp && intent?.action != ACTION_CONNECT
        val requestedProfileId = intent?.getStringExtra(EXTRA_PROFILE_ID)

        createNotificationChannel()
        val initialNotification = buildNotification(
            title = getString(R.string.app_name),
            content = getString(R.string.vpn_status_connecting),
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        serviceScope.launch {
            startTunnel(requestedProfileId, alwaysOnStarted)
        }

        return START_STICKY
    }

    private suspend fun startTunnel(requestedProfileId: String?, alwaysOnStarted: Boolean) {
        tunnelJob?.cancel()
        telemetryJob?.cancel()
        closeTunFd()

        val repo = VpnPreferencesRepository(applicationContext)
        val prefs = repo.getSnapshot()
        val profile = if (!requestedProfileId.isNullOrBlank()) {
            (BuiltInProfiles.defaults + prefs.customProfiles)
                .find { it.id == requestedProfileId } ?: repo.getActiveProfile()
        } else {
            repo.getActiveProfile()
        }

        val effectivePrimaryDns = prefs.dnsOverride.ifBlank { profile.primaryDns }
        val effectiveMtu = prefs.mtu.coerceIn(1280, 1500)
        val localIpCidr = "${profile.interfaceAddress}/${profile.interfacePrefix}"

        VpnStateHolder.setConnecting(
            profileId = profile.id,
            profileName = profile.name,
            protocol = profile.protocol,
            mode = profile.mode,
            localTunnelIp = localIpCidr,
            activeDns = effectivePrimaryDns,
            activeMtu = effectiveMtu,
            excludedAppsCount = prefs.excludedPackages.size,
            alwaysOnStarted = alwaysOnStarted,
        )

        try {
            val builder = Builder()
                .setSession("Mutiganda Yvan VPN")
                .addAddress(profile.interfaceAddress, profile.interfacePrefix)
                .addDnsServer(effectivePrimaryDns)
                .setMtu(effectiveMtu)

            if (profile.secondaryDns.isNotBlank() && profile.secondaryDns != effectivePrimaryDns) {
                runCatching { builder.addDnsServer(profile.secondaryDns) }
            }

            if (prefs.allowLanBypass) {
                runCatching { builder.allowBypass() }
            }

            // Route DNS server IPs through TUN so DNS queries are intercepted & forwarded
            // through protected sockets cleanly while maintaining full device connectivity.
            runCatching { builder.addRoute(effectivePrimaryDns, 32) }
            if (profile.secondaryDns.isNotBlank()) {
                runCatching { builder.addRoute(profile.secondaryDns, 32) }
            }

            // Apply per-app Split Tunneling (excluded apps bypass the VPN)
            for (pkg in prefs.excludedPackages) {
                try {
                    packageManager.getPackageInfo(pkg, 0)
                    builder.addDisallowedApplication(pkg)
                } catch (_: PackageManager.NameNotFoundException) {
                    // Skip uninstalled apps safely
                }
            }

            val configureIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setConfigureIntent(configureIntent)

            val established = builder.establish()
            if (established == null) {
                VpnStateHolder.setError("VPN permission was revoked or interface could not be established.")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }

            tunInterface = established
            val connectedAt = System.currentTimeMillis()
            VpnStateHolder.setConnected(connectedAt)

            updateNotification(
                title = getString(R.string.vpn_status_connected),
                content = "${profile.protocol.badgeLabel} • ${profile.name}",
            )

            launchTelemetryMonitor()
            launchTunPacketLoop(established, profile, effectivePrimaryDns)
        } catch (t: Throwable) {
            VpnStateHolder.setError(t.message ?: "Failed to establish Mutiganda Yvan VPN tunnel.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun launchTelemetryMonitor() {
        telemetryJob = serviceScope.launch {
            var baseRx = TrafficStats.getTotalRxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0L
            var baseTx = TrafficStats.getTotalTxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0L
            var prevRx = baseRx
            var prevTx = baseTx

            while (isActive) {
                delay(1000L)
                val nowRx = TrafficStats.getTotalRxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: prevRx
                val nowTx = TrafficStats.getTotalTxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: prevTx

                if (nowRx < baseRx) baseRx = nowRx
                if (nowTx < baseTx) baseTx = nowTx

                val deltaRx = (nowRx - prevRx).coerceAtLeast(0L)
                val deltaTx = (nowTx - prevTx).coerceAtLeast(0L)

                prevRx = nowRx
                prevTx = nowTx

                VpnStateHolder.updateTrafficStats(
                    rxBytes = (nowRx - baseRx).coerceAtLeast(0L),
                    txBytes = (nowTx - baseTx).coerceAtLeast(0L),
                    rxBytesPerSec = deltaRx,
                    txBytesPerSec = deltaTx,
                )
            }
        }
    }

    private fun launchTunPacketLoop(
        pfd: ParcelFileDescriptor,
        profile: VpnProfile,
        primaryDns: String,
    ) {
        tunnelJob = serviceScope.launch {
            val input = FileInputStream(pfd.fileDescriptor)
            val output = FileOutputStream(pfd.fileDescriptor)
            val buffer = ByteArray(32767)

            if (profile.mode == TunnelMode.CUSTOM_ENDPOINT && profile.endpointHost.isNotBlank()) {
                launch {
                    runProtectedEndpointProbe(profile)
                }
            }

            while (isActive) {
                val length = try {
                    input.read(buffer)
                } catch (_: Exception) {
                    break
                }
                if (length <= 0) {
                    delay(10L)
                    continue
                }

                val parsed = IpPacketUtils.parseIpv4Packet(buffer, length)
                if (parsed != null) {
                    val isDnsUdp = parsed.protocol == 17 && parsed.destPort == 53 && parsed.payloadLength > 0
                    VpnStateHolder.recordPacket(
                        protocol = parsed.protocolName,
                        destinationIp = parsed.destIp,
                        destinationPort = parsed.destPort,
                        sizeBytes = length,
                        isDnsForwarded = isDnsUdp,
                    )

                    if (isDnsUdp) {
                        val queryPayload = buffer.copyOfRange(
                            parsed.payloadOffset,
                            parsed.payloadOffset + parsed.payloadLength,
                        )
                        launch {
                            forwardDnsOverProtectedSocket(
                                parsed = parsed,
                                queryPayload = queryPayload,
                                fallbackDns = primaryDns,
                                tunOutput = output,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun forwardDnsOverProtectedSocket(
        parsed: ParsedIpv4Packet,
        queryPayload: ByteArray,
        fallbackDns: String,
        tunOutput: FileOutputStream,
    ) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            if (!protect(socket)) return
            socket.soTimeout = 3000

            val targetAddress = runCatching {
                InetAddress.getByAddress(parsed.destIpBytes)
            }.getOrElse {
                InetAddress.getByName(fallbackDns)
            }

            val outgoing = DatagramPacket(queryPayload, queryPayload.size, targetAddress, 53)
            socket.send(outgoing)

            val responseBuf = ByteArray(4096)
            val incoming = DatagramPacket(responseBuf, responseBuf.size)
            socket.receive(incoming)

            val ipv4Response = IpPacketUtils.buildUdpIpv4Response(
                srcIpBytes = parsed.destIpBytes,
                dstIpBytes = parsed.sourceIpBytes,
                srcPort = parsed.destPort,
                dstPort = parsed.sourcePort,
                payload = responseBuf,
                payloadLength = incoming.length,
                packetId = packetIdGenerator.getAndIncrement() and 0xFFFF,
            )

            synchronized(tunOutput) {
                tunOutput.write(ipv4Response)
            }
        } catch (_: Exception) {
            // DNS query timed out or unreachable; socket closed cleanly in finally
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun runProtectedEndpointProbe(profile: VpnProfile) {
        if (profile.protocol == VpnProtocol.WIREGUARD) {
            var udpSocket: DatagramSocket? = null
            try {
                udpSocket = DatagramSocket()
                if (!protect(udpSocket)) return
                udpSocket.soTimeout = 2500
                val start = System.nanoTime()
                val endpointAddr = InetAddress.getByName(profile.endpointHost)
                val keepalive = byteArrayOf(0x04, 0x00, 0x00, 0x00)
                udpSocket.send(
                    DatagramPacket(keepalive, keepalive.size, endpointAddr, profile.endpointPort),
                )
                val rttMs = ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
                VpnStateHolder.setEndpointReachability(rttMs)
                VpnStateHolder.recordPacket(
                    protocol = "WG-UDP",
                    destinationIp = endpointAddr.hostAddress ?: profile.endpointHost,
                    destinationPort = profile.endpointPort,
                    sizeBytes = keepalive.size,
                )
            } catch (_: Exception) {
                VpnStateHolder.setEndpointReachability(null)
            } finally {
                runCatching { udpSocket?.close() }
            }
        } else {
            var tcpSocket: Socket? = null
            try {
                tcpSocket = Socket()
                if (!protect(tcpSocket)) return
                val start = System.nanoTime()
                val endpointAddr = InetAddress.getByName(profile.endpointHost)
                tcpSocket.connect(InetSocketAddress(endpointAddr, profile.endpointPort), 2500)
                val rttMs = ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
                VpnStateHolder.setEndpointReachability(rttMs)
                VpnStateHolder.recordPacket(
                    protocol = profile.protocol.badgeLabel,
                    destinationIp = endpointAddr.hostAddress ?: profile.endpointHost,
                    destinationPort = profile.endpointPort,
                    sizeBytes = 64,
                )
            } catch (_: Exception) {
                VpnStateHolder.setEndpointReachability(null)
            } finally {
                runCatching { tcpSocket?.close() }
            }
        }
    }

    private fun stopTunnel() {
        tunnelJob?.cancel()
        telemetryJob?.cancel()
        closeTunFd()
        VpnStateHolder.setDisconnected()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closeTunFd() {
        runCatching { tunInterface?.close() }
        tunInterface = null
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        tunnelJob?.cancel()
        telemetryJob?.cancel()
        closeTunFd()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.vpn_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.vpn_notification_channel_desc)
                setShowBadge(false)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(title: String, content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MutigandaVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_shield_notif)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.vpn_action_disconnect), disconnectIntent)
            .build()
    }
}
