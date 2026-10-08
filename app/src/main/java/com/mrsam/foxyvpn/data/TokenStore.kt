package com.mrsam.foxyvpn.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mrsam.foxyvpn.data.model.RuntimeAuth
import com.mrsam.foxyvpn.data.model.VpnAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.util.UUID

private const val TAG = "TokenStore"

class TokenStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "MozillaVPN_tokens",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val lock = Any()

    private val _accountsFlow: MutableStateFlow<List<VpnAccount>>
    val accountsFlow: StateFlow<List<VpnAccount>>

    private val _activeAccountIdFlow: MutableStateFlow<String?>
    val activeAccountIdFlow: StateFlow<String?>

    private val _autoSwitchOnLimitFlow: MutableStateFlow<Boolean>
    val autoSwitchOnLimitFlow: StateFlow<Boolean>

    init {
        val initialAccounts = loadAccountsInternal()
        val initialActiveId = loadActiveAccountIdInternal(initialAccounts)
        val initialAutoSwitch = prefs.getBoolean(KEY_AUTO_SWITCH_ON_LIMIT, true)

        _accountsFlow = MutableStateFlow(initialAccounts)
        accountsFlow = _accountsFlow

        _activeAccountIdFlow = MutableStateFlow(initialActiveId)
        activeAccountIdFlow = _activeAccountIdFlow

        _autoSwitchOnLimitFlow = MutableStateFlow(initialAutoSwitch)
        autoSwitchOnLimitFlow = _autoSwitchOnLimitFlow

        // If legacy token exists but accounts were empty, migrate
        if (initialAccounts.isEmpty()) {
            val legacyAccess = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
            if (legacyAccess != null) {
                val legacyRefresh = prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
                val legacyExpiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
                val migrated = VpnAccount(
                    id = UUID.randomUUID().toString(),
                    email = "Account 1",
                    auth = RuntimeAuth(legacyAccess, legacyRefresh, legacyExpiresAt),
                )
                saveAccountsAndActiveInternal(listOf(migrated), migrated.id)
            }
        }
    }

    var autoSwitchOnLimit: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SWITCH_ON_LIMIT, true)
        set(value) {
            synchronized(lock) {
                prefs.edit().putBoolean(KEY_AUTO_SWITCH_ON_LIMIT, value).apply()
                _autoSwitchOnLimitFlow.value = value
            }
        }

    fun getAccounts(): List<VpnAccount> = synchronized(lock) { _accountsFlow.value }

    fun getActiveAccountId(): String? = synchronized(lock) { _activeAccountIdFlow.value }

    fun getActiveAccount(): VpnAccount? = synchronized(lock) {
        val activeId = _activeAccountIdFlow.value
        _accountsFlow.value.firstOrNull { it.id == activeId } ?: _accountsFlow.value.firstOrNull()
    }

    fun getAccount(id: String): VpnAccount? = synchronized(lock) {
        _accountsFlow.value.firstOrNull { it.id == id }
    }

    fun saveAuth(auth: RuntimeAuth) {
        synchronized(lock) {
            val accounts = _accountsFlow.value.toMutableList()
            val activeId = _activeAccountIdFlow.value

            val existingIndex = accounts.indexOfFirst { it.id == activeId }
            val updatedActiveId: String
            if (existingIndex >= 0) {
                val existing = accounts[existingIndex]
                accounts[existingIndex] = existing.copy(auth = auth)
                updatedActiveId = existing.id
            } else if (accounts.isNotEmpty()) {
                val first = accounts[0]
                accounts[0] = first.copy(auth = auth)
                updatedActiveId = first.id
            } else {
                val newAcc = VpnAccount(
                    id = UUID.randomUUID().toString(),
                    email = "Account 1",
                    auth = auth,
                )
                accounts.add(newAcc)
                updatedActiveId = newAcc.id
            }

            saveAccountsAndActiveInternal(accounts, updatedActiveId)
        }
    }

    fun saveOrUpdateAccount(email: String, auth: RuntimeAuth, uid: String = ""): VpnAccount {
        synchronized(lock) {
            val accounts = _accountsFlow.value.toMutableList()
            val trimmedEmail = email.trim()

            // Check if account with this email exists
            val existingIndex = accounts.indexOfFirst { it.email.equals(trimmedEmail, ignoreCase = true) }
            val savedAccount: VpnAccount

            if (existingIndex >= 0) {
                val existing = accounts[existingIndex]
                savedAccount = existing.copy(
                    auth = auth,
                    uid = uid.ifBlank { existing.uid },
                    isLimited = false,
                    limitedReason = null,
                )
                accounts[existingIndex] = savedAccount
            } else {
                if (accounts.size >= MAX_ACCOUNTS) {
                    throw IllegalStateException("Maximum limit of $MAX_ACCOUNTS accounts reached. Please remove an account first.")
                }
                savedAccount = VpnAccount(
                    id = UUID.randomUUID().toString(),
                    email = if (trimmedEmail.isNotBlank()) trimmedEmail else "Account ${accounts.size + 1}",
                    auth = auth,
                    uid = uid,
                )
                accounts.add(savedAccount)
            }

            saveAccountsAndActiveInternal(accounts, savedAccount.id)
            return savedAccount
        }
    }

    fun switchAccount(accountId: String): Boolean {
        synchronized(lock) {
            val accounts = _accountsFlow.value
            val target = accounts.firstOrNull { it.id == accountId } ?: return false
            saveAccountsAndActiveInternal(accounts, target.id)
            AppLogger.i(TAG, "Switched active account to: ${target.email} (${target.id})")
            return true
        }
    }

    fun switchNextAvailableAccount(): VpnAccount? {
        synchronized(lock) {
            val accounts = _accountsFlow.value
            if (accounts.isEmpty()) return null
            val currentId = _activeAccountIdFlow.value

            // 1. Look for other accounts that are NOT limited
            val nextHealthy = accounts.firstOrNull { it.id != currentId && !it.isLimited }
            if (nextHealthy != null) {
                saveAccountsAndActiveInternal(accounts, nextHealthy.id)
                AppLogger.i(TAG, "Failover: switched to healthy account ${nextHealthy.email}")
                return nextHealthy
            }

            // 2. If all others are marked limited, look for any other account
            val anyOther = accounts.firstOrNull { it.id != currentId }
            if (anyOther != null) {
                saveAccountsAndActiveInternal(accounts, anyOther.id)
                AppLogger.i(TAG, "Failover: switched to alternate account ${anyOther.email}")
                return anyOther
            }

            return null
        }
    }

    fun markAccountLimited(accountId: String, reason: String) {
        synchronized(lock) {
            val accounts = _accountsFlow.value.toMutableList()
            val index = accounts.indexOfFirst { it.id == accountId }
            if (index >= 0) {
                accounts[index] = accounts[index].copy(
                    isLimited = true,
                    limitedReason = reason,
                    quotaRemaining = 0L,
                )
                saveAccountsAndActiveInternal(accounts, _activeAccountIdFlow.value)
                AppLogger.w(TAG, "Account ${accounts[index].email} marked limited: $reason")
            }
        }
    }

    fun resetAccountLimit(accountId: String) {
        synchronized(lock) {
            val accounts = _accountsFlow.value.toMutableList()
            val index = accounts.indexOfFirst { it.id == accountId }
            if (index >= 0) {
                accounts[index] = accounts[index].copy(isLimited = false, limitedReason = null)
                saveAccountsAndActiveInternal(accounts, _activeAccountIdFlow.value)
            }
        }
    }

    fun updateAccountQuota(
        accountId: String,
        quotaRemaining: Long?,
        uid: String? = null,
        quotaMax: Long? = null,
    ) {
        synchronized(lock) {
            val accounts = _accountsFlow.value.toMutableList()
            val index = accounts.indexOfFirst { it.id == accountId }
            if (index >= 0) {
                val current = accounts[index]
                accounts[index] = current.copy(
                    quotaRemaining = quotaRemaining ?: current.quotaRemaining,
                    quotaMax = quotaMax?.takeIf { it > 0 } ?: current.quotaMax,
                    uid = uid?.ifBlank { null } ?: current.uid,
                )
                saveAccountsAndActiveInternal(accounts, _activeAccountIdFlow.value)
            }
        }
    }

    fun removeAccount(accountId: String): Boolean {
        synchronized(lock) {
            val accounts = _accountsFlow.value.toMutableList()
            val index = accounts.indexOfFirst { it.id == accountId }
            if (index < 0) return false

            val removed = accounts.removeAt(index)
            AppLogger.i(TAG, "Removed account: ${removed.email} (${removed.id})")

            val currentActiveId = _activeAccountIdFlow.value
            val newActiveId = if (currentActiveId == accountId) {
                accounts.firstOrNull()?.id
            } else {
                currentActiveId
            }

            saveAccountsAndActiveInternal(accounts, newActiveId)
            return true
        }
    }

    fun loadAuth(): RuntimeAuth? = synchronized(lock) {
        getActiveAccount()?.auth ?: runCatching {
            val access = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val refresh = prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
            val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
            RuntimeAuth(access, refresh, expiresAt)
        }.onFailure {
            AppLogger.w(TAG, "could not read stored session", it)
        }.getOrNull()
    }

    fun hasValidAccessToken(): Boolean {
        val auth = loadAuth() ?: return false
        if (auth.expiresAtEpochSeconds <= 0L) return false
        val nowSeconds = System.currentTimeMillis() / 1000
        return auth.expiresAtEpochSeconds - nowSeconds > CLOCK_SKEW_TOLERANCE_SECONDS
    }

    fun hasStoredSession(): Boolean = synchronized(lock) {
        _accountsFlow.value.isNotEmpty() || loadAuth() != null
    }

    fun hasRefreshToken(): Boolean = loadAuth()?.refreshToken != null

    @Deprecated(
        "Checks only access-token freshness; prefer hasValidAccessToken() or FxaAuthRepository.restoreSession().",
        ReplaceWith("hasValidAccessToken()"),
    )
    fun hasValidSession(): Boolean = hasValidAccessToken()

    fun clearAll() {
        synchronized(lock) {
            prefs.edit().clear().apply()
            _accountsFlow.value = emptyList()
            _activeAccountIdFlow.value = null
        }
    }

    fun clear() {
        clearAll()
    }

    private fun loadAccountsInternal(): List<VpnAccount> {
        val jsonStr = prefs.getString(KEY_ACCOUNTS_JSON, null)?.takeIf { it.isNotBlank() } ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<VpnAccount>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(VpnAccount.fromJson(obj))
            }
            list
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to parse accounts json", e)
            emptyList()
        }
    }

    private fun loadActiveAccountIdInternal(accounts: List<VpnAccount>): String? {
        val storedId = prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null)?.takeIf { it.isNotBlank() }
        if (storedId != null && accounts.any { it.id == storedId }) {
            return storedId
        }
        return accounts.firstOrNull()?.id
    }

    private fun saveAccountsAndActiveInternal(accounts: List<VpnAccount>, activeId: String?) {
        val editor = prefs.edit()
        val array = JSONArray()
        accounts.forEach { array.put(it.toJson()) }

        editor.putString(KEY_ACCOUNTS_JSON, array.toString())

        val resolvedActiveId = if (activeId != null && accounts.any { it.id == activeId }) {
            activeId
        } else {
            accounts.firstOrNull()?.id
        }

        if (resolvedActiveId != null) {
            editor.putString(KEY_ACTIVE_ACCOUNT_ID, resolvedActiveId)
            val activeAcc = accounts.firstOrNull { it.id == resolvedActiveId }
            if (activeAcc != null) {
                editor.putString(KEY_ACCESS_TOKEN, activeAcc.auth.accessToken)
                editor.putString(KEY_REFRESH_TOKEN, activeAcc.auth.refreshToken)
                editor.putLong(KEY_EXPIRES_AT, activeAcc.auth.expiresAtEpochSeconds)
            }
        } else {
            editor.remove(KEY_ACTIVE_ACCOUNT_ID)
            editor.remove(KEY_ACCESS_TOKEN)
            editor.remove(KEY_REFRESH_TOKEN)
            editor.remove(KEY_EXPIRES_AT)
        }

        editor.apply()

        _accountsFlow.value = accounts
        _activeAccountIdFlow.value = resolvedActiveId
    }

    companion object {
        const val MAX_ACCOUNTS = 10

        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_ACCOUNTS_JSON = "accounts_json"
        private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
        private const val KEY_AUTO_SWITCH_ON_LIMIT = "auto_switch_on_limit"

        private const val CLOCK_SKEW_TOLERANCE_SECONDS = 60L
    }
}
