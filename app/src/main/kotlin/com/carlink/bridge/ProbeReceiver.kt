package com.carlink.bridge

import com.carlink.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

internal object ProbeReceiver {
    data class State(
        val running: Boolean = false,
        val token: String = "",
        val addresses: List<String> = emptyList(),
        val receipts: List<String> = emptyList(),
        val error: String? = null,
    )

    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var server: ProbeServer? = null

    @Synchronized
    fun start() {
        if (!BuildConfig.DEBUG || server != null) return
        val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val candidate = ProbeServer(token) { receipt ->
            synchronized(this) {
                if (server != null && mutable.value.token == token) {
                    mutable.value = mutable.value.copy(receipts = (listOf(receipt) + mutable.value.receipts).take(50))
                }
            }
        }
        try {
            val port = candidate.start()
            val addresses = NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>().filter { !it.isLoopbackAddress }
                .map { "http://${it.hostAddress}:$port" }.distinct()
            server = candidate
            mutable.value = State(running = true, token = token, addresses = addresses)
        } catch (e: Exception) {
            candidate.close()
            mutable.value = State(error = "启动失败：${e.message}")
        }
    }

    @Synchronized
    fun stop() {
        server?.close()
        server = null
        mutable.value = mutable.value.copy(running = false, token = "", addresses = emptyList())
    }
}
