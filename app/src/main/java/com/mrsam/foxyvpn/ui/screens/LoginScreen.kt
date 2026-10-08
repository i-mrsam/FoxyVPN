package com.mrsam.foxyvpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mrsam.foxyvpn.data.FxaAuthRepository
import com.mrsam.foxyvpn.data.TokenStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authRepository: FxaAuthRepository,
    tokenStore: TokenStore? = null,
    isAddingAccount: Boolean = false,
    onSignedIn: () -> Unit,
    onBack: (() -> Unit)? = null,
    onNavigateToRegister: (() -> Unit)? = null,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var awaitingTwoFactor by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    val currentAccountsCount = tokenStore?.getAccounts()?.size ?: 0
    val reachedMax = isAddingAccount && currentAccountsCount >= TokenStore.MAX_ACCOUNTS

    fun performLogin() {
        if (email.isBlank() || password.isBlank() || isLoading) return
        focusManager.clearFocus()
        errorMessage = null
        isLoading = true
        scope.launch {
            val result = authRepository.startLogin(email.trim(), password)
            isLoading = false
            result.onSuccess { needsVerification ->
                if (needsVerification) awaitingTwoFactor = true else onSignedIn()
            }.onFailure { errorMessage = it.message ?: "Sign-in failed" }
        }
    }

    fun performVerifyCode() {
        if (code.isBlank() || isLoading) return
        focusManager.clearFocus()
        errorMessage = null
        isLoading = true
        scope.launch {
            val result = authRepository.submitTwoFactorCode(code.trim())
            isLoading = false
            result.onSuccess { onSignedIn() }
                .onFailure { errorMessage = it.message ?: "Verification failed" }
        }
    }

    Scaffold(
        topBar = {
            if (onBack != null || isAddingAccount) {
                TopAppBar(
                    title = {
                        val title = if (isAddingAccount) {
                            "افزودن اکانت (${currentAccountsCount + 1}/${TokenStore.MAX_ACCOUNTS})"
                        } else {
                            "ورود به حساب فایرفاکس"
                        }
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val titleText = if (isAddingAccount) {
                "افزودن حساب کاربری دیگر"
            } else {
                "ورود با حساب فایرفاکس"
            }
            Text(
                titleText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            val descText = if (isAddingAccount) {
                "امکان افزودن تا ${TokenStore.MAX_ACCOUNTS} اکانت مجزا. در صورت بروز محدودیت مصرف یا خطای ریت‌لیمیت، سوییچ بین اکانت‌ها بدون قطعی تانل و بدون تغییر لوکیشن انجام می‌شود."
            } else {
                "سرویس تانل بر روی ۵۰ گیگابایت ترافیک ماهانه رایگان موزیلا اجرا می‌شود و هیچ هزینه‌ای ندارد."
            }
            Text(
                descText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))

            if (reachedMax) {
                Text(
                    "شما به سقف مجاز ${TokenStore.MAX_ACCOUNTS} اکانت رسیده‌اید. برای افزودن اکانت جدید، ابتدا یکی از اکانت‌های قبلی را حذف کنید.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (!awaitingTwoFactor) {
                // Email field
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("ایمیل اکانت فایرفاکس") },
                    leadingIcon = {
                        Icon(Icons.Filled.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))

                // Password field with visibility toggle eye icon
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("رمز عبور") },
                    leadingIcon = {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description = if (passwordVisible) "مخفی کردن رمز عبور" else "نمایش رمز عبور"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { performLogin() },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { performLogin() },
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("در حال اعتبارسنجی...")
                    } else {
                        Text("ورود به برنامه", fontWeight = FontWeight.Bold)
                    }
                }

                // Dedicated button to create free account if not registered yet
                if (onNavigateToRegister != null) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            " یا ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = if (isAddingAccount) "نیاز به ساخت حساب کاربری جدید دارید؟" else "هنوز حساب کاربری فایرفاکس ندارید؟",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onNavigateToRegister,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                Icons.Filled.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            val regText = if (isAddingAccount) {
                                "ساخت اکانت جدید در فایرفاکس"
                            } else {
                                "ساخت اکانت رایگان فایرفاکس"
                            }
                            Text(
                                text = regText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            } else {
                Text(
                    "کد تأیید ۶ رقمی ارسال‌شده به ایمیل خود را وارد نمایید:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("کد تأیید (Confirmation Code)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { performVerifyCode() },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { performVerifyCode() },
                    enabled = !isLoading && code.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("در حال تأیید...")
                    } else {
                        Text("تأیید و ورود", fontWeight = FontWeight.Bold)
                    }
                }
            }

            errorMessage?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
