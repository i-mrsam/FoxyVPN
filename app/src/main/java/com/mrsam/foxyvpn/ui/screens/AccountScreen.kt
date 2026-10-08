package com.mrsam.foxyvpn.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrsam.foxyvpn.data.FxaAuthRepository
import com.mrsam.foxyvpn.data.GUARDIAN_ENDPOINT_DEFAULT
import com.mrsam.foxyvpn.data.GuardianClient
import com.mrsam.foxyvpn.data.TokenStore
import com.mrsam.foxyvpn.data.formatBytes
import com.mrsam.foxyvpn.data.model.ConnectionState
import com.mrsam.foxyvpn.data.model.Entitlement
import com.mrsam.foxyvpn.data.model.VpnAccount
import com.mrsam.foxyvpn.vpn.MozillaVPNService
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    tokenStore: TokenStore,
    authRepository: FxaAuthRepository,
    onAddAccount: () -> Unit,
    onRegisterNewAccount: () -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val accounts by tokenStore.accountsFlow.collectAsState()
    val activeAccountId by tokenStore.activeAccountIdFlow.collectAsState()
    val autoSwitchOnLimit by tokenStore.autoSwitchOnLimitFlow.collectAsState()
    val vpnState by MozillaVPNService.state.collectAsState()

    var activeEntitlement by remember { mutableStateOf<Entitlement?>(null) }
    var isLoadingInfo by remember { mutableStateOf(false) }
    var infoErrorMessage by remember { mutableStateOf<String?>(null) }

    var accountToDelete by remember { mutableStateOf<VpnAccount?>(null) }
    var showAddOptionsDialog by remember { mutableStateOf(false) }

    fun refreshActiveAccountInfo() {
        val active = tokenStore.getActiveAccount()
        if (active == null) {
            activeEntitlement = null
            return
        }
        isLoadingInfo = true
        infoErrorMessage = null
        scope.launch {
            val accessToken = authRepository.currentAccessToken()
            if (accessToken == null) {
                isLoadingInfo = false
                infoErrorMessage = "Session token unavailable"
                return@launch
            }
            runCatching { GuardianClient().fetchUserInfo(GUARDIAN_ENDPOINT_DEFAULT, accessToken) }
                .onSuccess { ent ->
                    activeEntitlement = ent
                    tokenStore.updateAccountQuota(active.id, ent.quotaRemaining, ent.uid, ent.maxBytes)
                }
                .onFailure { infoErrorMessage = it.message ?: "Failed to load account info" }
            isLoadingInfo = false
        }
    }

    LaunchedEffect(activeAccountId) {
        refreshActiveAccountInfo()
    }

    if (accountToDelete != null) {
        val target = accountToDelete!!
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Remove Account") },
            text = { Text("Are you sure you want to remove \"${target.email}\"? Your proxy/server settings will not be affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        tokenStore.removeAccount(target.id)
                        if (vpnState == ConnectionState.CONNECTED) {
                            MozillaVPNService.switchAccount(context)
                        }
                        accountToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (showAddOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showAddOptionsDialog = false },
            title = {
                Text(
                    "افزودن حساب کاربری (${accounts.size + 1}/${TokenStore.MAX_ACCOUNTS})",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "می‌توانید یک اکانت رایگان جدید در فایرفاکس بسازید یا با اکانت موجود لاگین نمایید:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Card(
                        onClick = {
                            showAddOptionsDialog = false
                            onRegisterNewAccount()
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ListItem(
                            headlineContent = { Text("ساخت اکانت رایگان فایرفاکس", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("ثبت‌نام مستقیم در accounts.firefox.com با ۵۰ گیگ حجم") },
                            leadingContent = {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                        )
                    }

                    Card(
                        onClick = {
                            showAddOptionsDialog = false
                            onAddAccount()
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ListItem(
                            headlineContent = { Text("ورود با حساب کاربری موجود", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("ورود مستقیم با ایمیل و رمز عبور") },
                            leadingContent = {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddOptionsDialog = false }) {
                    Text("انصراف")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts (${accounts.size}/${TokenStore.MAX_ACCOUNTS})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshActiveAccountInfo() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Multi-Account Pool", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Connect & switch across up to ${TokenStore.MAX_ACCOUNTS} accounts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Button(
                        onClick = { showAddOptionsDialog = true },
                        enabled = accounts.size < TokenStore.MAX_ACCOUNTS,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Auto-switch toggle card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = { Text("Auto-switch on limit", fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text("When an account reaches 50 GB quota or is restricted, seamlessly failover to the next account without disconnecting or losing location settings.")
                        },
                        trailingContent = {
                            Switch(
                                checked = autoSwitchOnLimit,
                                onCheckedChange = { tokenStore.autoSwitchOnLimit = it },
                            )
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Configured Accounts",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (accounts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Filled.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No accounts added", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { showAddOptionsDialog = true }) {
                                Text("Add First Account")
                            }
                        }
                    }
                }
            } else {
                items(accounts, key = { it.id }) { acc ->
                    val isActive = acc.id == activeAccountId

                    AccountCardItem(
                        account = acc,
                        isActive = isActive,
                        onSelect = {
                            if (!isActive) {
                                tokenStore.switchAccount(acc.id)
                                if (vpnState == ConnectionState.CONNECTED) {
                                    MozillaVPNService.switchAccount(context)
                                }
                            }
                        },
                        onResetLimit = {
                            tokenStore.resetAccountLimit(acc.id)
                        },
                        onDelete = {
                            accountToDelete = acc
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            // Active Account Details Section
            item {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Text(
                    "Active Account Information",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))

                when {
                    isLoadingInfo -> Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                    infoErrorMessage != null -> Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                infoErrorMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { refreshActiveAccountInfo() }) {
                                Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                    activeEntitlement != null -> {
                        val info = activeEntitlement!!
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                AccountInfoRow("Subscription", if (info.subscribed) "Active" else "Free 50GB Tier")
                                AccountInfoRow("Account ID", info.uid.ifBlank { "\u2014" })
                                AccountInfoRow(
                                    "Data remaining",
                                    when {
                                        !info.limitedBandwidth -> "Unlimited"
                                        info.quotaRemaining != null -> formatBytes(info.quotaRemaining)
                                        info.maxBytes != null -> "Limited to ${formatBytes(info.maxBytes)}"
                                        else -> "50 GB Free Allowance"
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GUARDIAN_ENDPOINT_DEFAULT)))
                            },
                        ) {
                            Text("Manage subscription at Mozilla")
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AccountCardItem(
    account: VpnAccount,
    isActive: Boolean,
    onSelect: () -> Unit,
    onResetLimit: () -> Unit,
    onDelete: () -> Unit,
) {
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, borderColor) else null,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (account.isLimited) MaterialTheme.colorScheme.errorContainer
                                else if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (account.isLimited) Icons.Filled.Warning else Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = if (account.isLimited) MaterialTheme.colorScheme.error
                            else if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = account.email,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isActive) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(end = 6.dp),
                                ) {
                                    Text(
                                        "ACTIVE",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            if (account.isLimited) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(4.dp),
                                ) {
                                    Text(
                                        account.limitedReason ?: "LIMITED",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            } else if (!isActive) {
                                Text(
                                    "Ready to connect",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (account.quotaRemaining != null || account.isLimited || !isActive) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (account.quotaRemaining != null) {
                        Text(
                            "Quota left: ${formatBytes(account.quotaRemaining)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Row {
                        if (account.isLimited) {
                            TextButton(onClick = onResetLimit) {
                                Text("Reset limit flag", fontSize = 12.sp)
                            }
                        }
                        if (!isActive) {
                            FilledTonalButton(onClick = onSelect) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Switch to this", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountInfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
