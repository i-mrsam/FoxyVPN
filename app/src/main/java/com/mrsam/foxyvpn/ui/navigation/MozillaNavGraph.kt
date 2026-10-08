package com.mrsam.foxyvpn.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mrsam.foxyvpn.MozillaVPNApp
import com.mrsam.foxyvpn.ui.screens.AccountScreen
import com.mrsam.foxyvpn.ui.screens.HomeScreen
import com.mrsam.foxyvpn.ui.screens.LoginScreen
import com.mrsam.foxyvpn.ui.screens.LogsScreen
import com.mrsam.foxyvpn.ui.screens.RegisterScreen
import com.mrsam.foxyvpn.ui.screens.ServerListScreen
import com.mrsam.foxyvpn.ui.screens.SettingsScreen
import com.mrsam.foxyvpn.ui.screens.SplashScreen
import com.mrsam.foxyvpn.ui.theme.ThemeController

object MozillaRoutes {
    const val SPLASH = "splash"
    const val REGISTER = "register"
    const val REGISTER_ADD = "register_add"
    const val LOGIN = "login"
    const val ADD_ACCOUNT = "add_account"
    const val HOME = "home"
    const val SERVERS = "servers"
    const val SETTINGS = "settings"
    const val ACCOUNT = "account"
    const val LOGS = "logs"
}

@Composable
fun MozillaNavGraph(
    navController: NavHostController = rememberNavController(),
    app: MozillaVPNApp,
    themeController: ThemeController,
    onRequestConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    NavHost(navController = navController, startDestination = MozillaRoutes.SPLASH) {
        composable(MozillaRoutes.SPLASH) {
            SplashScreen(
                authRepository = app.authRepository,
                onSignedIn = {
                    navController.navigate(MozillaRoutes.HOME) { popUpTo(MozillaRoutes.SPLASH) { inclusive = true } }
                },
                onNeedsLogin = {
                    // Navigate first to the secure in-app Firefox account registration screen
                    navController.navigate(MozillaRoutes.REGISTER) { popUpTo(MozillaRoutes.SPLASH) { inclusive = true } }
                },
            )
        }
        composable(MozillaRoutes.REGISTER) {
            RegisterScreen(
                tokenStore = app.tokenStore,
                isAddingAccount = false,
                onGoToLogin = {
                    navController.navigate(MozillaRoutes.LOGIN)
                },
                onBack = null,
            )
        }
        composable(MozillaRoutes.LOGIN) {
            LoginScreen(
                authRepository = app.authRepository,
                tokenStore = app.tokenStore,
                isAddingAccount = false,
                onSignedIn = {
                    navController.navigate(MozillaRoutes.HOME) { popUpTo(MozillaRoutes.LOGIN) { inclusive = true } }
                },
                onBack = { navController.popBackStack() },
                onNavigateToRegister = {
                    navController.navigate(MozillaRoutes.REGISTER) {
                        popUpTo(MozillaRoutes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(MozillaRoutes.REGISTER_ADD) {
            RegisterScreen(
                tokenStore = app.tokenStore,
                isAddingAccount = true,
                onGoToLogin = {
                    navController.navigate(MozillaRoutes.ADD_ACCOUNT) {
                        popUpTo(MozillaRoutes.REGISTER_ADD) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(MozillaRoutes.ADD_ACCOUNT) {
            LoginScreen(
                authRepository = app.authRepository,
                tokenStore = app.tokenStore,
                isAddingAccount = true,
                onSignedIn = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onNavigateToRegister = {
                    navController.navigate(MozillaRoutes.REGISTER_ADD) {
                        popUpTo(MozillaRoutes.ADD_ACCOUNT) { inclusive = true }
                    }
                },
            )
        }
        composable(MozillaRoutes.HOME) {
            HomeScreen(
                app = app,
                themeController = themeController,
                onRequestConnect = onRequestConnect,
                onDisconnect = onDisconnect,
                onOpenServers = { navController.navigate(MozillaRoutes.SERVERS) },
                onOpenSettings = { navController.navigate(MozillaRoutes.SETTINGS) },
                onOpenAccount = { navController.navigate(MozillaRoutes.ACCOUNT) },
                onOpenAddAccount = { navController.navigate(MozillaRoutes.ADD_ACCOUNT) },
            )
        }
        composable(MozillaRoutes.SERVERS) {
            ServerListScreen(
                serverListClient = app.serverListClient,
                proxyStateStore = app.proxyStateStore,
                onServerSelected = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(MozillaRoutes.SETTINGS) {
            SettingsScreen(
                settingsStore = app.settingsStore,
                tokenStore = app.tokenStore,
                onOpenLogs = { navController.navigate(MozillaRoutes.LOGS) },
                onOpenAccount = { navController.navigate(MozillaRoutes.ACCOUNT) },
                onSignOut = {
                    onDisconnect()
                    app.tokenStore.clear()
                    navController.navigate(MozillaRoutes.REGISTER) { popUpTo(0) { inclusive = true } }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(MozillaRoutes.ACCOUNT) {
            AccountScreen(
                tokenStore = app.tokenStore,
                authRepository = app.authRepository,
                onAddAccount = { navController.navigate(MozillaRoutes.ADD_ACCOUNT) },
                onRegisterNewAccount = { navController.navigate(MozillaRoutes.REGISTER_ADD) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(MozillaRoutes.LOGS) {
            LogsScreen(onBack = { navController.popBackStack() })
        }
    }
}
