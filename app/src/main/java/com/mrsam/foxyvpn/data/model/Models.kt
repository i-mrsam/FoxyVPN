package com.mrsam.foxyvpn.data.model

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

enum class LoginStepState { CREDENTIALS, TWO_FACTOR }

data class VpnProtocol(
    val name: String,
    val host: String = "",
    val port: Int = 0,
    val scheme: String = "",
    val templateString: String = "",
)

data class VpnServerNode(
    val hostname: String,
    val port: Int = 0,
    val quarantined: Boolean = false,
    val protocols: List<VpnProtocol> = emptyList(),
)

data class VpnCity(
    val name: String,
    val code: String,
    val servers: List<VpnServerNode> = emptyList(),
)

data class VpnCountry(
    val name: String,
    val code: String,
    val cities: List<VpnCity> = emptyList(),
)

data class ProxyCandidate(
    val host: String,
    val port: Int,
    val countryCode: String,
    val countryName: String,
    val cityCode: String = "",
) {
    val authority: String get() = "$host:$port"
}

data class Entitlement(
    val subscribed: Boolean,
    val uid: String,
    val maxBytes: Long?,
    val limitedBandwidth: Boolean,
    val quotaRemaining: Long? = null,
)

data class RuntimeAuth(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochSeconds: Long,
)

const val DEFAULT_MAX_QUOTA_BYTES = 50L * 1024L * 1024L * 1024L // 50 GB

data class VpnAccount(
    val id: String = java.util.UUID.randomUUID().toString(),
    val email: String,
    val auth: RuntimeAuth,
    val uid: String = "",
    val quotaMax: Long = DEFAULT_MAX_QUOTA_BYTES,
    val quotaRemaining: Long? = null,
    val isLimited: Boolean = false,
    val limitedReason: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("id", id)
            put("email", email)
            put("access_token", auth.accessToken)
            put("refresh_token", auth.refreshToken ?: "")
            put("expires_at", auth.expiresAtEpochSeconds)
            put("uid", uid)
            put("quota_max", quotaMax)
            if (quotaRemaining != null) put("quota_remaining", quotaRemaining)
            put("is_limited", isLimited)
            put("limited_reason", limitedReason ?: "")
            put("added_at", addedAt)
        }
    }

    companion object {
        fun fromJson(obj: org.json.JSONObject): VpnAccount {
            val auth = RuntimeAuth(
                accessToken = obj.optString("access_token", ""),
                refreshToken = obj.optString("refresh_token", "").takeIf { it.isNotBlank() },
                expiresAtEpochSeconds = obj.optLong("expires_at", 0L),
            )
            val maxBytes = if (obj.has("quota_max") && !obj.isNull("quota_max")) {
                obj.optLong("quota_max", DEFAULT_MAX_QUOTA_BYTES).takeIf { it > 0 } ?: DEFAULT_MAX_QUOTA_BYTES
            } else {
                DEFAULT_MAX_QUOTA_BYTES
            }
            return VpnAccount(
                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                email = obj.optString("email", "Firefox Account"),
                auth = auth,
                uid = obj.optString("uid", ""),
                quotaMax = maxBytes,
                quotaRemaining = if (obj.has("quota_remaining") && !obj.isNull("quota_remaining")) obj.optLong("quota_remaining") else null,
                isLimited = obj.optBoolean("is_limited", false),
                limitedReason = obj.optString("limited_reason", "").takeIf { it.isNotBlank() },
                addedAt = obj.optLong("added_at", System.currentTimeMillis()),
            )
        }
    }
}

