package com.mrsam.foxyvpn.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds

object PingUtil {
    private const val TIMEOUT_MS = 1_500

    suspend fun measureTcpLatencyMs(host: String, port: Int): Int? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TIMEOUT_MS.toLong().milliseconds) {
            runCatching {
                val start = System.nanoTime()
                Socket().use { socket ->
                    socket.bind(InetSocketAddress(0))
                    com.mrsam.foxyvpn.data.ControlPlaneHttp.socketProtector?.invoke(socket)
                    socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
                }
                ((System.nanoTime() - start) / 1_000_000L).toInt()
            }.onFailure {
                com.mrsam.foxyvpn.data.AppLogger.d("PingUtil", "ping failed to $host:$port: ${it.message}")
            }.getOrNull()
        }
    }
}
