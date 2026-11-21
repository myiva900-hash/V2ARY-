package com.example.vpn

data class ParsedIpv4Packet(
    val headerLength: Int,
    val protocol: Int,
    val protocolName: String,
    val sourceIpBytes: ByteArray,
    val destIpBytes: ByteArray,
    val sourceIp: String,
    val destIp: String,
    val sourcePort: Int,
    val destPort: Int,
    val payloadOffset: Int,
    val payloadLength: Int,
)

object IpPacketUtils {

    fun parseIpv4Packet(buffer: ByteArray, length: Int): ParsedIpv4Packet? {
        if (length < 20) return null
        val version = (buffer[0].toInt() ushr 4) and 0x0F
        if (version != 4) return null

        val ihl = (buffer[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl) return null

        val protocol = buffer[9].toInt() and 0xFF
        val protocolName = when (protocol) {
            1 -> "ICMP"
            6 -> "TCP"
            17 -> "UDP"
            else -> "IP($protocol)"
        }

        val srcBytes = buffer.copyOfRange(12, 16)
        val dstBytes = buffer.copyOfRange(16, 20)
        val srcIp = formatIp(srcBytes)
        val dstIp = formatIp(dstBytes)

        var srcPort = 0
        var dstPort = 0
        var payloadOffset = ihl
        var payloadLength = (length - ihl).coerceAtLeast(0)

        if ((protocol == 6 || protocol == 17) && length >= ihl + 4) {
            srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
            dstPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
            if (protocol == 17 && length >= ihl + 8) {
                val udpLength = ((buffer[ihl + 4].toInt() and 0xFF) shl 8) or (buffer[ihl + 5].toInt() and 0xFF)
                payloadOffset = ihl + 8
                payloadLength = (udpLength - 8).coerceIn(0, length - payloadOffset)
            }
        }

        return ParsedIpv4Packet(
            headerLength = ihl,
            protocol = protocol,
            protocolName = protocolName,
            sourceIpBytes = srcBytes,
            destIpBytes = dstBytes,
            sourceIp = srcIp,
            destIp = dstIp,
            sourcePort = srcPort,
            destPort = dstPort,
            payloadOffset = payloadOffset,
            payloadLength = payloadLength,
        )
    }

    fun buildUdpIpv4Response(
        srcIpBytes: ByteArray,
        dstIpBytes: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
        payloadLength: Int,
        packetId: Int,
    ): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLen = ipHeaderLen + udpHeaderLen + payloadLength
        val packet = ByteArray(totalLen)

        // IPv4 Header
        packet[0] = 0x45.toByte() // Version 4, IHL 5
        packet[1] = 0x00
        packet[2] = ((totalLen ushr 8) and 0xFF).toByte()
        packet[3] = (totalLen and 0xFF).toByte()
        packet[4] = ((packetId ushr 8) and 0xFF).toByte()
        packet[5] = (packetId and 0xFF).toByte()
        packet[6] = 0x40.toByte() // Don't fragment
        packet[7] = 0x00
        packet[8] = 64.toByte() // TTL
        packet[9] = 17.toByte() // UDP
        // Checksum bytes [10..11] left 0 for calculation
        System.arraycopy(srcIpBytes, 0, packet, 12, 4)
        System.arraycopy(dstIpBytes, 0, packet, 16, 4)

        val ipChecksum = computeChecksum(packet, 0, ipHeaderLen)
        packet[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // UDP Header
        val udpLen = udpHeaderLen + payloadLength
        packet[20] = ((srcPort ushr 8) and 0xFF).toByte()
        packet[21] = (srcPort and 0xFF).toByte()
        packet[22] = ((dstPort ushr 8) and 0xFF).toByte()
        packet[23] = (dstPort and 0xFF).toByte()
        packet[24] = ((udpLen ushr 8) and 0xFF).toByte()
        packet[25] = (udpLen and 0xFF).toByte()
        // UDP checksum left 0 before computation

        System.arraycopy(payload, 0, packet, 28, payloadLength)

        val udpChecksum = computeUdpChecksum(srcIpBytes, dstIpBytes, packet, 20, udpLen)
        packet[26] = ((udpChecksum ushr 8) and 0xFF).toByte()
        packet[27] = (udpChecksum and 0xFF).toByte()

        return packet
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFFL).toInt()
    }

    private fun computeUdpChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        packet: ByteArray,
        udpOffset: Int,
        udpLength: Int,
    ): Int {
        var sum = 0L
        for (i in 0..2 step 2) {
            sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
            sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
        }
        sum += 17L // UDP protocol
        sum += udpLength.toLong()

        var i = udpOffset
        val end = udpOffset + udpLength
        while (i + 1 < end) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (packet[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        val result = (sum.inv() and 0xFFFFL).toInt()
        return if (result == 0) 0xFFFF else result
    }

    private fun formatIp(bytes: ByteArray): String =
        bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
}
