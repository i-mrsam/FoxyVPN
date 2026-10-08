package com.mrsam.foxyvpn.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrsam.foxyvpn.MozillaVPNApp
import com.mrsam.foxyvpn.data.GUARDIAN_ENDPOINT_DEFAULT
import com.mrsam.foxyvpn.data.TokenStore
import com.mrsam.foxyvpn.data.formatBytes
import com.mrsam.foxyvpn.data.model.ConnectionState
import com.mrsam.foxyvpn.data.model.VpnAccount
import com.mrsam.foxyvpn.ui.theme.LocalMozillaStatusColors
import com.mrsam.foxyvpn.ui.theme.ThemeController
import com.mrsam.foxyvpn.ui.theme.ThemeMode
import com.mrsam.foxyvpn.vpn.MozillaVPNService
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    app: MozillaVPNApp,
    themeController: ThemeController,
    onRequestConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit = {},
    onOpenAddAccount: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by MozillaVPNService.state.collectAsState()
    val lastError by MozillaVPNService.lastError.collectAsState()

    val selectedProxy by app.proxyStateStore.selectedProxyFlow.collectAsState()
    val accounts by app.tokenStore.accountsFlow.collectAsState()
    val activeAccountId by app.tokenStore.activeAccountIdFlow.collectAsState()

    val activeAccount = remember(accounts, activeAccountId) {
        accounts.firstOrNull { it.id == activeAccountId } ?: accounts.firstOrNull()
    }

    var showAccountSwitcherDialog by remember { mutableStateOf(false) }
    var isRefreshingQuota by remember { mutableStateOf(false) }

    fun refreshQuota() {
        val active = activeAccount ?: return
        isRefreshingQuota = true
        scope.launch {
            val token = app.authRepository.currentAccessToken()
            if (token != null) {
                runCatching { app.guardianClient.fetchUserInfo(GUARDIAN_ENDPOINT_DEFAULT, token) }
                    .onSuccess { ent ->
                        app.tokenStore.updateAccountQuota(active.id, ent.quotaRemaining, ent.uid, ent.maxBytes)
                    }
            }
            isRefreshingQuota = false
        }
    }

    LaunchedEffect(activeAccountId) {
        refreshQuota()
    }

    val statusColors = LocalMozillaStatusColors.current
    val targetRingColor = when (state) {
        ConnectionState.CONNECTED -> statusColors.connected
        ConnectionState.CONNECTING -> statusColors.connecting
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
    }

    val ringColor by animateColorAsState(targetValue = targetRingColor, label = "connect-ring-color")
    val scale by animateFloatAsState(
        targetValue = if (state == ConnectionState.CONNECTED) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "connect-scale",
    )

    val systemInDarkTheme = isSystemInDarkTheme()
    val haptics = LocalHapticFeedback.current

    // Quota calculations for active account (countdown from 100% to 0% and 50GB to 0GB)
    val totalQuotaBytes = activeAccount?.quotaMax ?: (50L * 1024L * 1024L * 1024L)
    val remainingQuotaBytes = when {
        activeAccount?.isLimited == true -> 0L
        activeAccount?.quotaRemaining != null -> activeAccount.quotaRemaining.coerceIn(0L, totalQuotaBytes)
        else -> totalQuotaBytes
    }
    val fractionRemaining = if (totalQuotaBytes > 0) {
        (remainingQuotaBytes.toDouble() / totalQuotaBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }
    val percentageRemaining = (fractionRemaining * 100).toInt()

    if (showAccountSwitcherDialog) {
        QuickAccountSwitcherDialog(
            accounts = accounts,
            activeAccountId = activeAccountId,
            onSelectAccount = { selectedId ->
                if (selectedId != activeAccountId) {
                    app.tokenStore.switchAccount(selectedId)
                    if (state == ConnectionState.CONNECTED) {
                        MozillaVPNService.switchAccount(context)
                    }
                }
                showAccountSwitcherDialog = false
            },
            onAddAccount = {
                showAccountSwitcherDialog = false
                onOpenAddAccount()
            },
            onManageAccounts = {
                showAccountSwitcherDialog = false
                onOpenAccount()
            },
            onDismiss = { showAccountSwitcherDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FoxyVPN") },
                actions = {
                    val mode = themeController.mode
                    val showingDark = themeController.resolveDark(systemInDarkTheme)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                role = Role.Button,
                                onClick = { themeController.toggle(systemInDarkTheme) },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    themeController.set(ThemeMode.SYSTEM)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = when (mode) {
                                ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                                ThemeMode.LIGHT -> Icons.Filled.LightMode
                                ThemeMode.DARK -> Icons.Filled.DarkMode
                            },
                            contentDescription = when (mode) {
                                ThemeMode.SYSTEM ->
                                    "Theme: follow system. Tap to switch to ${if (showingDark) "light" else "dark"} mode"
                                ThemeMode.LIGHT -> "Theme: light. Tap for dark mode, long press to follow the system"
                                ThemeMode.DARK -> "Theme: dark. Tap for light mode, long press to follow the system"
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(ringColor.copy(alpha = 0.15f))
                    .clickable {
                        if (state == ConnectionState.DISCONNECTED) onRequestConnect() else onDisconnect()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size((124 * scale).dp)
                        .clip(CircleShape)
                        .background(ringColor.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Power,
                        contentDescription = if (state == ConnectionState.DISCONNECTED) "Connect" else "Disconnect",
                        tint = ringColor,
                        modifier = Modifier.size(50.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = when (state) {
                    ConnectionState.CONNECTED -> "Connected"
                    ConnectionState.CONNECTING -> "Connecting\u2026"
                    ConnectionState.DISCONNECTED -> "Disconnected"
                },
                style = MaterialTheme.typography.headlineSmall,
            )

            if (state == ConnectionState.CONNECTING) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap to cancel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val error = lastError
            if (error != null && state != ConnectionState.CONNECTING) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        if (accounts.size > 1 || error.contains("quota", ignoreCase = true) || error.contains("limited", ignoreCase = true) || error.contains("reject", ignoreCase = true)) {
                            Spacer(Modifier.height(6.dp))
                            FilledTonalButton(
                                onClick = { showAccountSwitcherDialog = true },
                                modifier = Modifier.height(32.dp),
                            ) {
                                Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Switch Account", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Active Account Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAccountSwitcherDialog = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (activeAccount?.isLimited == true) Icons.Filled.Warning else Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = if (activeAccount?.isLimited == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Account",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "(${accounts.size}/${TokenStore.MAX_ACCOUNTS})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                activeAccount?.email ?: "No account selected",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (activeAccount?.isLimited == true) {
                                Text(
                                    activeAccount.limitedReason ?: "Quota limit reached",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Switch account",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Location Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenServers),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Location",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                selectedProxy?.let { it.countryName.ifBlank { it.countryCode } } ?: "Recommended",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Change location",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Quota Progress Card (Right under the Location Card)
            QuotaProgressCard(
                account = activeAccount,
                totalBytes = totalQuotaBytes,
                remainingBytes = remainingQuotaBytes,
                fractionRemaining = fractionRemaining,
                percentageRemaining = percentageRemaining,
                isRefreshing = isRefreshingQuota,
                onRefresh = { refreshQuota() },
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable(onClick = onOpenSettings)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun QuotaProgressCard(
    account: VpnAccount?,
    totalBytes: Long,
    remainingBytes: Long,
    fractionRemaining: Float,
    percentageRemaining: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = fractionRemaining,
        animationSpec = tween(durationMillis = 600),
        label = "quota_progress",
    )

    val isLimited = account?.isLimited == true || percentageRemaining == 0
    val isLowQuota = !isLimited && percentageRemaining <= 20

    val progressColor = when {
        isLimited -> MaterialTheme.colorScheme.error
        isLowQuota -> Color(0xFFF57C00) // Amber / Warning
        else -> MaterialTheme.colorScheme.primary
    }

    val badgeBgColor = when {
        isLimited -> MaterialTheme.colorScheme.errorContainer
        isLowQuota -> Color(0xFFFFF3E0)
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val badgeTextColor = when {
        isLimited -> MaterialTheme.colorScheme.onErrorContainer
        isLowQuota -> Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            // Header: Icon, Allowance Title, Countdown Percentage, Refresh Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Filled.DataUsage,
                        contentDescription = null,
                        tint = progressColor,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Monthly Quota",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = account?.email ?: "50 GB Data Allowance",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = badgeBgColor,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = if (isLimited) "0%" else "$percentageRemaining%",
                            color = badgeTextColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }

                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing && account != null,
                        modifier = Modifier.size(32.dp),
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh Quota",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Progress Bar (100% -> 0% countdown fill)
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            // Details Row: Left (remaining volume countdown 50GB -> 0GB), Right (total capacity)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${formatBytes(remainingBytes)} remaining",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isLimited) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "of ${formatBytes(totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isLimited) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Quota limit reached • Tap account above to switch",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun QuickAccountSwitcherDialog(
    accounts: List<VpnAccount>,
    activeAccountId: String?,
    onSelectAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onManageAccounts: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Switch Account")
                Text(
                    "${accounts.size}/${TokenStore.MAX_ACCOUNTS}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column {
                Text(
                    "Switch active account instantly without losing your server location or settings:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(accounts, key = { it.id }) { acc ->
                        val isSelected = acc.id == activeAccountId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectAccount(acc.id) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectAccount(acc.id) },
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    acc.email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(end = 6.dp),
                                        ) {
                                            Text(
                                                "ACTIVE",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                    if (acc.isLimited) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(end = 6.dp),
                                        ) {
                                            Text(
                                                acc.limitedReason ?: "LIMITED",
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                    if (acc.quotaRemaining != null) {
                                        Text(
                                            "Quota: ${formatBytes(acc.quotaRemaining)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = onAddAccount,
                        enabled = accounts.size < TokenStore.MAX_ACCOUNTS,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Account")
                    }

                    TextButton(onClick = onManageAccounts) {
                        Text("Manage all")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
