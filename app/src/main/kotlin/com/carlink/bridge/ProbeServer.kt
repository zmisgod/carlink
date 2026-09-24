package com.carlink.bridge

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Locale

/** Opt-in diagnostic receiver. Accepted actions are recorded, NEVER applied to the vehicle. */
internal class ProbeServer(private val token: String, private val onReceipt: (String) -> Unit) : AutoCloseable {
    private var listener: ServerSocket? = null
    @Volatile private var client: Socket? = null
    private val receipts = LinkedHashMap<String, Pair<String, String>>()

    @Synchronized
    fun start(port: Int = 8765, host: String = "0.0.0.0"): Int {
        check(listener == null)
        require(token.length >= 16)
        val socket = ServerSocket()
        try {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(host, port))
        } catch (e: Exception) {
            socket.close()
            throw e
        }
        listener = socket
        Thread({
            while (!socket.isClosed) {
                try {
                    val connection = socket.accept()
                    client = connection
                    connection.use {
                        it.soTimeout = 2000
                        // close() may race with accept(). Do not process requests after shutdown.
                        if (!socket.isClosed) handle(it)
                    }
                } catch (_: Exception) {
                    // Malformed/disconnected clients must not terminate the listener.
                } finally {
                    client = null
                }
            }
        }, "CarlinkProbe").apply { isDaemon = true; start() }
        return socket.localPort
    }

    private fun handle(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val headers = ByteArrayOutputStream()
            val deadline = System.nanoTime() + 5_000_000_000L
            var tail = 0
            while (true) {
                if (System.nanoTime() > deadline) throw Rejected(408, "request_timeout")
                val byte = input.read()
                if (byte < 0) return
                headers.write(byte)
                if (headers.size() > 8192) throw Rejected(431, "headers_too_large")
                tail = (tail shl 8) or byte
                if (tail == 0x0d0a0d0a) break
            }
            val lines = headers.toString("US-ASCII").split("\r\n")
            val request = lines.first().split(' ')
            if (request.size != 3 || request[2] != "HTTP/1.1") throw Rejected(400, "invalid_request")
            val fields = mutableMapOf<String, String>()
            for (line in lines.drop(1).filter { it.isNotEmpty() }) {
                val colon = line.indexOf(':')
                if (colon < 1) throw Rejected(400, "invalid_header")
                val key = line.substring(0, colon).lowercase(Locale.ROOT)
                if (fields.put(key, line.substring(colon + 1).trim()) != null) throw Rejected(400, "duplicate_header")
            }
            val expected = "Bearer $token".toByteArray()
            if (!MessageDigest.isEqual(expected, fields["authorization"].orEmpty().toByteArray())) {
                throw Rejected(401, "unauthorized")
            }
            if (fields.containsKey("transfer-encoding")) throw Rejected(400, "chunked_not_supported")
            if (request[0] == "GET" && request[1] == "/v1/health") {
                respond(socket, 200, """{"version":1,"service":"carlink-probe","transport":"lan","dryRun":true}""")
                return
            }
            if (request[1] != "/v1/actions") throw Rejected(404, "not_found")
            if (request[0] != "POST") throw Rejected(405, "method_not_allowed")
            if (fields["content-type"]?.substringBefore(';')?.trim() != "application/json") {
                throw Rejected(415, "json_required")
            }
            val length = fields["content-length"]?.toIntOrNull() ?: throw Rejected(411, "length_required")
            if (length !in 1..4096) throw Rejected(413, "invalid_length")
            val data = ByteArray(length)
            var offset = 0
            while (offset < length) {
                if (System.nanoTime() > deadline) throw Rejected(408, "request_timeout")
                val count = input.read(data, offset, length - offset)
                if (count < 0) throw Rejected(400, "incomplete_body")
                offset += count
            }
            respond(socket, 200, accept(String(data, Charsets.UTF_8)))
        } catch (e: Rejected) {
            respond(socket, e.status, JSONObject().put("error", e.message).toString())
        } catch (_: Exception) {
            respond(socket, 400, """{"error":"invalid_request"}""")
        }
    }

    private fun accept(body: String): String {
        val json = JSONObject(body)
        val id = json.optString("id")
        val action = json.optString("action")
        val source = json.optString("source")
        val value = json.opt("value")
        if (json.opt("version") != 1 || json.opt("dryRun") != true ||
            !id.matches(Regex("[a-zA-Z0-9-]{1,64}")) || source !in listOf("iphone", "carplay", "cli")
        ) throw Rejected(422, "invalid_envelope")
        val valid = when (action) {
            "ping" -> value == JSONObject.NULL
            "climate.temperature.set" -> value is Int && value in 16..30
            "lights.cabin.set" -> value is Boolean
            else -> false
        }
        if (!valid) throw Rejected(422, "invalid_action_or_value")
        // Compare semantic content, so harmless JSON whitespace/key ordering is not a conflict.
        val fingerprint = "$source|$action|$value"
        receipts[id]?.let {
            if (it.first != fingerprint) throw Rejected(409, "id_conflict")
            return it.second
        }
        val reply = JSONObject().put("version", 1).put("id", id).put("source", source).put("action", action)
            .put("value", value).put("status", "received").put("dryRun", true)
            .put("transport", "lan").put("receivedAt", System.currentTimeMillis()).toString()
        receipts[id] = fingerprint to reply
        if (receipts.size > 256) receipts.remove(receipts.keys.first())
        onReceipt(reply)
        return reply
    }

    private fun respond(socket: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}\r\n" +
            "Content-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
            "Connection: close\r\nCache-Control: no-store\r\n\r\n"
        socket.getOutputStream().apply { write(head.toByteArray(Charsets.US_ASCII)); write(bytes); flush() }
    }

    @Synchronized
    override fun close() {
        listener?.close()
        client?.close()
        listener = null
    }

    private class Rejected(val status: Int, message: String) : Exception(message)
}
