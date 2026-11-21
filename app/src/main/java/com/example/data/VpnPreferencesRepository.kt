package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.BuiltInProfiles
import com.example.model.TunnelMode
import com.example.model.VpnPreferences
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.vpnDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mutiganda_yvan_vpn_prefs"
)

class VpnPreferencesRepository(private val context: Context) {

    private object Keys {
        val SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
        val CUSTOM_PROFILES_JSON = stringPreferencesKey("custom_profiles_json")
        val EXCLUDED_PACKAGES = stringSetPreferencesKey("excluded_packages")
        val DNS_OVERRIDE = stringPreferencesKey("dns_override")
        val MTU = intPreferencesKey("mtu")
        val ALLOW_LAN_BYPASS = booleanPreferencesKey("allow_lan_bypass")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    val preferencesFlow: Flow<VpnPreferences> = context.vpnDataStore.data.map { prefs ->
        VpnPreferences(
            selectedProfileId = prefs[Keys.SELECTED_PROFILE_ID] ?: BuiltInProfiles.defaults.first().id,
            customProfiles = decodeProfiles(prefs[Keys.CUSTOM_PROFILES_JSON]),
            excludedPackages = prefs[Keys.EXCLUDED_PACKAGES] ?: emptySet(),
            dnsOverride = prefs[Keys.DNS_OVERRIDE] ?: "",
            mtu = prefs[Keys.MTU] ?: 1420,
            allowLanBypass = prefs[Keys.ALLOW_LAN_BYPASS] ?: true,
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
        )
    }

    suspend fun getSnapshot(): VpnPreferences = preferencesFlow.first()

    suspend fun getActiveProfile(): VpnProfile {
        val prefs = getSnapshot()
        val all = BuiltInProfiles.defaults + prefs.customProfiles
        return all.find { it.id == prefs.selectedProfileId } ?: BuiltInProfiles.defaults.first()
    }

    suspend fun selectProfile(profileId: String) {
        context.vpnDataStore.edit { it[Keys.SELECTED_PROFILE_ID] = profileId }
    }

    suspend fun saveCustomProfile(profile: VpnProfile) {
        context.vpnDataStore.edit { prefs ->
            val current = decodeProfiles(prefs[Keys.CUSTOM_PROFILES_JSON]).toMutableList()
            val idx = current.indexOfFirst { it.id == profile.id }
            if (idx >= 0) {
                current[idx] = profile
            } else {
                current.add(0, profile)
            }
            prefs[Keys.CUSTOM_PROFILES_JSON] = encodeProfiles(current)
            prefs[Keys.SELECTED_PROFILE_ID] = profile.id
        }
    }

    suspend fun saveCustomProfilesBatch(profiles: List<VpnProfile>) {
        if (profiles.isEmpty()) return
        context.vpnDataStore.edit { prefs ->
            val current = decodeProfiles(prefs[Keys.CUSTOM_PROFILES_JSON]).toMutableList()
            profiles.reversed().forEach { p ->
                current.add(0, p)
            }
            prefs[Keys.CUSTOM_PROFILES_JSON] = encodeProfiles(current)
            prefs[Keys.SELECTED_PROFILE_ID] = profiles.first().id
        }
    }

    suspend fun deleteCustomProfile(profileId: String) {
        context.vpnDataStore.edit { prefs ->
            val current = decodeProfiles(prefs[Keys.CUSTOM_PROFILES_JSON])
                .filterNot { it.id == profileId }
            prefs[Keys.CUSTOM_PROFILES_JSON] = encodeProfiles(current)
            if (prefs[Keys.SELECTED_PROFILE_ID] == profileId) {
                prefs[Keys.SELECTED_PROFILE_ID] = BuiltInProfiles.defaults.first().id
            }
        }
    }

    suspend fun toggleExcludedPackage(packageName: String) {
        context.vpnDataStore.edit { prefs ->
            val current = (prefs[Keys.EXCLUDED_PACKAGES] ?: emptySet()).toMutableSet()
            if (current.contains(packageName)) {
                current.remove(packageName)
            } else {
                current.add(packageName)
            }
            prefs[Keys.EXCLUDED_PACKAGES] = current
        }
    }

    suspend fun updateDnsOverride(dns: String) {
        context.vpnDataStore.edit { it[Keys.DNS_OVERRIDE] = dns.trim() }
    }

    suspend fun updateMtu(mtu: Int) {
        context.vpnDataStore.edit { it[Keys.MTU] = mtu.coerceIn(1280, 1500) }
    }

    suspend fun updateAllowLanBypass(allow: Boolean) {
        context.vpnDataStore.edit { it[Keys.ALLOW_LAN_BYPASS] = allow }
    }

    suspend fun updateDarkTheme(dark: Boolean) {
        context.vpnDataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    companion object {
        fun parseWireGuardConfig(profileName: String, rawConfig: String): VpnProfile? {
            var address = "10.8.0.2"
            var prefix = 24
            var dns1 = "1.1.1.1"
            var dns2 = "1.0.0.1"
            var mtu = 1420
            var endpointHost = ""
            var endpointPort = 51820
            var publicKey = ""
            var hasRecognizedField = false

            for (rawLine in rawConfig.lines()) {
                val line = rawLine.substringBefore("#").trim()
                if (line.isEmpty() || line.startsWith("[")) continue
                val parts = line.split("=", limit = 2)
                if (parts.size != 2) continue
                val key = parts[0].trim().lowercase()
                val value = parts[1].trim()

                when (key) {
                    "address" -> {
                        val firstAddr = value.split(",").firstOrNull()?.trim().orEmpty()
                        val cidr = firstAddr.split("/")
                        if (cidr.isNotEmpty() && cidr[0].contains(".")) {
                            address = cidr[0].trim()
                            prefix = cidr.getOrNull(1)?.toIntOrNull()?.coerceIn(8, 32) ?: 24
                            hasRecognizedField = true
                        }
                    }
                    "dns" -> {
                        val dnsServers = value.split(",").map { it.trim() }.filter { it.contains(".") }
                        if (dnsServers.isNotEmpty()) {
                            dns1 = dnsServers[0]
                            hasRecognizedField = true
                        }
                        if (dnsServers.size > 1) dns2 = dnsServers[1]
                    }
                    "mtu" -> {
                        val parsedMtu = value.toIntOrNull()
                        if (parsedMtu != null) {
                            mtu = parsedMtu.coerceIn(1280, 1500)
                            hasRecognizedField = true
                        }
                    }
                    "publickey" -> {
                        if (value.isNotBlank()) {
                            publicKey = value
                            hasRecognizedField = true
                        }
                    }
                    "endpoint" -> {
                        if (value.isNotBlank()) {
                            val lastColon = value.lastIndexOf(':')
                            if (lastColon > 0) {
                                endpointHost = value.substring(0, lastColon).trim('[', ']')
                                endpointPort = value.substring(lastColon + 1).toIntOrNull() ?: 51820
                            } else {
                                endpointHost = value
                            }
                            hasRecognizedField = true
                        }
                    }
                }
            }

            if (!hasRecognizedField) {
                return null
            }

            val mode = if (endpointHost.isNotBlank()) {
                TunnelMode.CUSTOM_ENDPOINT
            } else {
                TunnelMode.LOCAL_DNS_SHIELD
            }

            return VpnProfile(
                id = "custom_${UUID.randomUUID()}",
                name = profileName.ifBlank { "Mutiganda WireGuard Profile" },
                subtitle = if (endpointHost.isNotBlank()) {
                    "$endpointHost:$endpointPort • WireGuard .conf"
                } else {
                    "DNS $dns1 • Imported .conf"
                },
                mode = mode,
                protocol = if (endpointHost.isNotBlank()) VpnProtocol.WIREGUARD else VpnProtocol.DNS_SHIELD,
                primaryDns = dns1,
                secondaryDns = dns2,
                interfaceAddress = address,
                interfacePrefix = prefix,
                endpointHost = endpointHost,
                endpointPort = endpointPort,
                publicKey = publicKey,
                mtu = mtu,
                isBuiltIn = false,
            )
        }

        private fun encodeProfiles(profiles: List<VpnProfile>): String {
            val array = JSONArray()
            profiles.forEach { p ->
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("subtitle", p.subtitle)
                    put("mode", p.mode.name)
                    put("protocol", p.protocol.name)
                    put("primaryDns", p.primaryDns)
                    put("secondaryDns", p.secondaryDns)
                    put("interfaceAddress", p.interfaceAddress)
                    put("interfacePrefix", p.interfacePrefix)
                    put("endpointHost", p.endpointHost)
                    put("endpointPort", p.endpointPort)
                    put("publicKey", p.publicKey)
                    put("sni", p.sni)
                    put("transportNetwork", p.transportNetwork)
                    put("securityType", p.securityType)
                    put("mtu", p.mtu)
                }
                array.put(obj)
            }
            return array.toString()
        }

        private fun decodeProfiles(json: String?): List<VpnProfile> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val mode = runCatching {
                            TunnelMode.valueOf(obj.optString("mode", TunnelMode.CUSTOM_ENDPOINT.name))
                        }.getOrDefault(TunnelMode.CUSTOM_ENDPOINT)
                        val protocol = runCatching {
                            VpnProtocol.valueOf(obj.optString("protocol", VpnProtocol.WIREGUARD.name))
                        }.getOrDefault(VpnProtocol.WIREGUARD)
                        add(
                            VpnProfile(
                                id = obj.getString("id"),
                                name = obj.getString("name"),
                                subtitle = obj.optString("subtitle", ""),
                                mode = mode,
                                protocol = protocol,
                                primaryDns = obj.optString("primaryDns", "1.1.1.1"),
                                secondaryDns = obj.optString("secondaryDns", "1.0.0.1"),
                                interfaceAddress = obj.optString("interfaceAddress", "10.8.0.2"),
                                interfacePrefix = obj.optInt("interfacePrefix", 24),
                                endpointHost = obj.optString("endpointHost", ""),
                                endpointPort = obj.optInt("endpointPort", 51820),
                                publicKey = obj.optString("publicKey", ""),
                                sni = obj.optString("sni", ""),
                                transportNetwork = obj.optString("transportNetwork", "udp"),
                                securityType = obj.optString("securityType", "none"),
                                mtu = obj.optInt("mtu", 1420),
                                isBuiltIn = false,
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
