package com.mrsam.foxyvpn

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.mrsam.foxyvpn.data.CrashReporter
import com.mrsam.foxyvpn.data.FxaAuthRepository
import com.mrsam.foxyvpn.data.GuardianClient
import com.mrsam.foxyvpn.data.ProxyStateStore
import com.mrsam.foxyvpn.data.ServerListClient
import com.mrsam.foxyvpn.data.SettingsStore
import com.mrsam.foxyvpn.data.TokenStore
import com.mrsam.foxyvpn.vpn.upstream.NettyLoggingBridge
import org.conscrypt.Conscrypt
import java.security.Security

class MozillaVPNApp : Application() {

    lateinit var tokenStore: TokenStore
    lateinit var proxyStateStore: ProxyStateStore
    lateinit var settingsStore: SettingsStore
    lateinit var guardianClient: GuardianClient
    lateinit var serverListClient: ServerListClient
    lateinit var authRepository: FxaAuthRepository

    override fun onCreate() {
        super.onCreate()

        CrashReporter.install(this)
        CrashReporter.replayLastCrashIfAny(this)

        NettyLoggingBridge.install()
        installConscrypt()
        tokenStore = TokenStore(this)
        proxyStateStore = ProxyStateStore(this)
        settingsStore = SettingsStore(this)
        guardianClient = GuardianClient()
        serverListClient = ServerListClient()
        authRepository = FxaAuthRepository(tokenStore)
        createNotificationChannel()
    }

    private fun installConscrypt() {
        runCatching {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }.onFailure {
            Log.w("MozillaVPNApp", "failed to install Conscrypt security provider", it)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            VPN_NOTIFICATION_CHANNEL_ID,
            "VPN status",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val VPN_NOTIFICATION_CHANNEL_ID = "MozillaVPN_status"
    }
}
