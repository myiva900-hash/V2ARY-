package com.example

import com.example.data.VpnPreferencesRepository
import com.example.data.VpnShareLinkParser
import com.example.model.TunnelMode
import com.example.model.VpnProtocol
import com.example.ui.components.VpnFormatters
import com.example.vpn.IpPacketUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VpnCoreUnitTest {

    @Test
    fun `parseWireGuardConfig parses endpoint, dns, address, and mtu`() {
        val sampleConfig = """
            [Interface]
            Address = 10.66.66.2/32
            DNS = 9.9.9.9, 1.1.1.1
            MTU = 1380

            [Peer]
            PublicKey = abcdef1234567890
            Endpoint = 198.51.100.24:51820
        """.trimIndent()

        val parsed = VpnPreferencesRepository.parseWireGuardConfig("Mutiganda Kigali Node", sampleConfig)
        assertNotNull(parsed)
        assertEquals("Mutiganda Kigali Node", parsed!!.name)
        assertEquals(TunnelMode.CUSTOM_ENDPOINT, parsed.mode)
        assertEquals(VpnProtocol.WIREGUARD, parsed.protocol)
        assertEquals("10.66.66.2", parsed.interfaceAddress)
        assertEquals(32, parsed.interfacePrefix)
        assertEquals("9.9.9.9", parsed.primaryDns)
        assertEquals("1.1.1.1", parsed.secondaryDns)
        assertEquals(1380, parsed.mtu)
        assertEquals("198.51.100.24", parsed.endpointHost)
        assertEquals(51820, parsed.endpointPort)
        assertEquals("abcdef1234567890", parsed.publicKey)
    }

    @Test
    fun `parseWireGuardConfig returns null for empty invalid text`() {
        val parsed = VpnPreferencesRepository.parseWireGuardConfig("Empty", "")
        assertNull(parsed)
    }

    @Test
    fun `VpnShareLinkParser parses vless, trojan, shadowsocks, and vmess URIs`() {
        val vless = VpnShareLinkParser.parseSingleShareUri(
            "vless://11111111-2222-3333-4444-555555555555@edge.mutiganda.net:443?security=tls&sni=edge.mutiganda.net&type=ws#Mutiganda%20VLESS"
        )
        assertNotNull(vless)
        assertEquals(VpnProtocol.VLESS, vless!!.protocol)
        assertEquals("edge.mutiganda.net", vless.endpointHost)
        assertEquals(443, vless.endpointPort)
        assertEquals("Mutiganda VLESS", vless.name)
        assertEquals("ws", vless.transportNetwork)
        assertEquals("tls", vless.securityType)

        val trojan = VpnShareLinkParser.parseSingleShareUri(
            "trojan://secretpass@tr.mutiganda.net:8443?sni=tr.mutiganda.net#TrojanNode"
        )
        assertNotNull(trojan)
        assertEquals(VpnProtocol.TROJAN, trojan!!.protocol)
        assertEquals("tr.mutiganda.net", trojan.endpointHost)
        assertEquals(8443, trojan.endpointPort)
        assertEquals("TrojanNode", trojan.name)

        // Base64 for {"add":"203.0.113.10","port":"443","id":"abc-uuid","net":"ws","tls":"tls","ps":"Kigali VMess"}
        val vmessJson = """{"add":"203.0.113.10","port":"443","id":"abc-uuid","net":"ws","tls":"tls","ps":"Kigali VMess"}"""
        val vmessB64 = java.util.Base64.getEncoder().encodeToString(vmessJson.toByteArray())
        val vmess = VpnShareLinkParser.parseSingleShareUri("vmess://$vmessB64")
        assertNotNull(vmess)
        assertEquals(VpnProtocol.VMESS, vmess!!.protocol)
        assertEquals("203.0.113.10", vmess.endpointHost)
        assertEquals(443, vmess.endpointPort)
        assertEquals("Kigali VMess", vmess.name)

        val batch = VpnShareLinkParser.parseBatch(
            """
            vless://uuid@a.example.com:443#Node1
            ss://YWVzLTI1Ni1nY206cGFzcw==@b.example.com:8388#Node2
            """.trimIndent(),
        )
        assertEquals(2, batch.size)
        assertEquals(VpnProtocol.SHADOWSOCKS, batch[1].protocol)
        assertEquals("b.example.com", batch[1].endpointHost)
        assertEquals(8388, batch[1].endpointPort)
    }

    @Test
    fun `buildUdpIpv4Response and parseIpv4Packet round-trip accurately`() {
        val srcIp = byteArrayOf(1, 1, 1, 1)
        val dstIp = byteArrayOf(10, 8, 0, 2)
        val payload = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01)

        val packet = IpPacketUtils.buildUdpIpv4Response(
            srcIpBytes = srcIp,
            dstIpBytes = dstIp,
            srcPort = 53,
            dstPort = 44556,
            payload = payload,
            payloadLength = payload.size,
            packetId = 42,
        )

        val parsed = IpPacketUtils.parseIpv4Packet(packet, packet.size)
        assertNotNull(parsed)
        assertEquals("UDP", parsed!!.protocolName)
        assertEquals("1.1.1.1", parsed.sourceIp)
        assertEquals("10.8.0.2", parsed.destIp)
        assertEquals(53, parsed.sourcePort)
        assertEquals(44556, parsed.destPort)
        assertEquals(payload.size, parsed.payloadLength)
    }

    @Test
    fun `formatters output clean readable units`() {
        assertEquals("512 B", VpnFormatters.formatBytes(512L))
        assertEquals("2.0 KB", VpnFormatters.formatBytes(2048L))
        assertEquals("1.00 MB/s", VpnFormatters.formatSpeed(1024L * 1024L))
        assertEquals("01:02:03", VpnFormatters.formatDuration((3600L + 120L + 3L) * 1000L))
    }
}
