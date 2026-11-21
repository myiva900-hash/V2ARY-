package com.example.data

import com.example.model.TunnelMode
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

object VpnShareLinkParser {

    fun parseBatch(rawInput: String): List<VpnProfile> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Check if it looks like a WireGuard .conf file
        if (trimmed.contains("[Interface]", ignoreCase = true) ||
            trimmed.contains("[Peer]", ignoreCase = true)
        ) {
            return listOfNotNull(
                VpnPreferencesRepository.parseWireGuardConfig("Mutiganda WireGuard Node", trimmed),
            )
        }

        // Otherwise inspect line-by-line for vless://, vmess://, trojan://, ss://, socks://
        return trimmed.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseSingleShareUri(it) }
    }

    fun parseSingleShareUri(uriText: String): VpnProfile? {
        val clean = uriText.trim()
        return when {
            clean.startsWith("vmess://", ignoreCase = true) -> parseVmess(clean)
            clean.startsWith("vless://", ignoreCase = true) -> parseStandardUri(clean, VpnProtocol.VLESS, 443)
            clean.startsWith("trojan://", ignoreCase = true) -> parseStandardUri(clean, VpnProtocol.TROJAN, 443)
            clean.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(clean)
            clean.startsWith("socks://", ignoreCase = true) ||
                clean.startsWith("socks5://", ignoreCase = true) -> parseStandardUri(clean, VpnProtocol.SOCKS5, 1080)
            else -> null
        }
    }

    private fun parseVmess(uriText: String): VpnProfile? {
        val encoded = uriText.substringAfter("://").trim()
        if (encoded.isEmpty()) return null
        val jsonText = decodeBase64Flexible(encoded) ?: return null
        return try {
            val obj = JSONObject(jsonText)
            val host = obj.optString("add", "").trim()
            val port = obj.opt("port")?.toString()?.toIntOrNull() ?: return null
            if (host.isEmpty()) return null

            val remark = obj.optString("ps", "").trim().ifBlank { "Mutiganda VMess • $host" }
            val net = obj.optString("net", "tcp").ifBlank { "tcp" }
            val tls = obj.optString("tls", "none").ifBlank { "none" }
            val sni = obj.optString("sni", "").ifBlank { obj.optString("host", "") }
            val uuid = obj.optString("id", "")

            VpnProfile(
                id = "custom_${UUID.randomUUID()}",
                name = remark,
                subtitle = "$host:$port • ${net.uppercase()} / ${tls.uppercase()}",
                mode = TunnelMode.CUSTOM_ENDPOINT,
                protocol = VpnProtocol.VMESS,
                primaryDns = "1.1.1.1",
                secondaryDns = "1.0.0.1",
                endpointHost = host,
                endpointPort = port.coerceIn(1, 65535),
                publicKey = uuid,
                sni = sni,
                transportNetwork = net,
                securityType = tls,
                isBuiltIn = false,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseStandardUri(
        uriText: String,
        protocol: VpnProtocol,
        defaultPort: Int,
    ): VpnProfile? {
        return try {
            val parsed = URI(uriText)
            val host = parsed.host?.trim().orEmpty()
            if (host.isEmpty()) return null
            val port = if (parsed.port > 0) parsed.port else defaultPort
            val fragment = parsed.rawFragment?.let { decodeUrl(it) }?.trim().orEmpty()
            val queryParams = parseQueryParams(parsed.rawQuery.orEmpty())

            val network = queryParams["type"] ?: queryParams["net"] ?: "tcp"
            val security = queryParams["security"] ?: if (protocol == VpnProtocol.TROJAN) "tls" else "none"
            val sni = queryParams["sni"] ?: queryParams["peer"] ?: queryParams["host"] ?: ""
            val credential = parsed.userInfo?.trim().orEmpty()
            val name = fragment.ifBlank { "Mutiganda ${protocol.badgeLabel} • $host" }

            VpnProfile(
                id = "custom_${UUID.randomUUID()}",
                name = name,
                subtitle = "$host:$port • ${network.uppercase()} / ${security.uppercase()}",
                mode = TunnelMode.CUSTOM_ENDPOINT,
                protocol = protocol,
                primaryDns = "1.1.1.1",
                secondaryDns = "1.0.0.1",
                endpointHost = host,
                endpointPort = port.coerceIn(1, 65535),
                publicKey = credential,
                sni = sni,
                transportNetwork = network,
                securityType = security,
                isBuiltIn = false,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseShadowsocks(uriText: String): VpnProfile? {
        return try {
            val withoutScheme = uriText.substringAfter("://")
            val remark = if (withoutScheme.contains("#")) {
                decodeUrl(withoutScheme.substringAfter("#")).trim()
            } else {
                ""
            }
            val body = withoutScheme.substringBefore("#").substringBefore("?")

            val (userInfo, hostPort) = if (body.contains("@")) {
                body.substringBefore("@") to body.substringAfter("@")
            } else {
                val decoded = decodeBase64Flexible(body) ?: return null
                decoded.substringBeforeLast("@") to decoded.substringAfterLast("@")
            }

            val lastColon = hostPort.lastIndexOf(':')
            if (lastColon <= 0) return null
            val host = hostPort.substring(0, lastColon).trim('[', ']')
            val port = hostPort.substring(lastColon + 1).toIntOrNull() ?: return null
            if (host.isEmpty()) return null

            val name = remark.ifBlank { "Mutiganda SS • $host" }
            VpnProfile(
                id = "custom_${UUID.randomUUID()}",
                name = name,
                subtitle = "$host:$port • Shadowsocks",
                mode = TunnelMode.CUSTOM_ENDPOINT,
                protocol = VpnProtocol.SHADOWSOCKS,
                primaryDns = "1.1.1.1",
                secondaryDns = "1.0.0.1",
                endpointHost = host,
                endpointPort = port.coerceIn(1, 65535),
                publicKey = userInfo,
                transportNetwork = "tcp",
                securityType = "aead",
                isBuiltIn = false,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery.split("&")
            .mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) {
                    decodeUrl(parts[0]).lowercase() to decodeUrl(parts[1])
                } else {
                    null
                }
            }
            .toMap()
    }

    private fun decodeUrl(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)

    private fun decodeBase64Flexible(encoded: String): String? {
        val clean = encoded.replace('-', '+').replace('_', '/').filter { !it.isWhitespace() }
        if (clean.isEmpty()) return null
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val output = ArrayList<Byte>(clean.length * 3 / 4)
        var buffer = 0
        var bitsLeft = 0
        for (ch in clean) {
            if (ch == '=') break
            val value = alphabet.indexOf(ch)
            if (value < 0) return null
            buffer = (buffer shl 6) or value
            bitsLeft += 6
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return runCatching {
            String(output.toByteArray(), StandardCharsets.UTF_8)
        }.getOrNull()
    }
}
