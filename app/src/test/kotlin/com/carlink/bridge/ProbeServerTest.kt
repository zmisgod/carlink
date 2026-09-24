package com.carlink.bridge

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.util.Collections
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ProbeServerTest {
    private val token = "0123456789abcdef0123456789abcdef"
    private val received = Collections.synchronizedList(mutableListOf<String>())
    private lateinit var server: ProbeServer
    private var port = 0

    @Before fun start() {
        server = ProbeServer(token) { received.add(it) }
        port = server.start(0, "127.0.0.1")
    }

    @After fun stop() = server.close()

    @Test fun `real socket request returns correlated dry run receipt`() {
        val (status, reply) = post(body("sample"))
        assertEquals(200, status)
        val json = JSONObject(reply)
        assertEquals("sample", json.getString("id"))
        assertEquals("received", json.getString("status"))
        assertTrue(json.getBoolean("dryRun"))
        assertEquals("lan", json.getString("transport"))
        assertEquals(1, received.size)
    }

    @Test fun `unauthorized invalid and non dry run commands are not recorded`() {
        assertEquals(401, post(body("a"), "wrong").first)
        assertEquals(422, post(body("b").replace("true", "false")).first)
        assertEquals(422, post(body("c").replace("ping", "doors.unlock")).first)
        assertEquals(400, post("{").first)
        assertEquals(422, post(body("d", "climate.temperature.set", "99")).first)
        assertEquals(422, post(body("e", "lights.cabin.set", "1")).first)
        assertTrue(received.isEmpty())
    }

    @Test fun `retry is idempotent but reuse for different action is rejected`() {
        val first = post(body("same"))
        assertEquals(first, post(body("same")))
        assertEquals(409, post(body("same", "lights.cabin.set", "true")).first)
        assertEquals(1, received.size)
    }

    @Test fun `oversized body rejected and listener remains usable`() {
        assertEquals(413, post("x".repeat(4097)).first)
        assertEquals(200, post(body("next")).first)
    }

    @Test fun `close interrupts a partial client and releases port`() {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 3000
            socket.getOutputStream().write("POST ".toByteArray())
            server.close()
            try {
                assertEquals(-1, socket.getInputStream().read())
            } catch (_: java.net.SocketException) {
                // Closing a socket with unread request bytes may send TCP RST instead of FIN.
            }
        }
        ProbeServer(token) {}.use { replacement -> replacement.start(port, "127.0.0.1") }
    }

    @Test fun `shared Swift client interoperates with Android receiver over TCP`() {
        val executable = System.getenv("CARLINK_PROBE_CLIENT")
        assumeTrue("Set CARLINK_PROBE_CLIENT to run cross-language test", !executable.isNullOrBlank())
        val process = ProcessBuilder(executable!!, "http://127.0.0.1:$port", token).redirectErrorStream(true).start()
        try {
            assertTrue("Swift client timed out", process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.exitValue())
            assertEquals(output, 4, received.size)
            assertEquals(output, 4, output.lineSequence().count { it.startsWith("ACK ") })
            println(output)
        } finally {
            process.destroyForcibly()
        }
    }

    private fun body(id: String, action: String = "ping", value: String = "null") =
        """{"version":1,"dryRun":true,"id":"$id","source":"iphone","action":"$action","value":$value}"""

    private fun post(body: String, bearer: String = token): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:$port/v1/actions").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            status to (if (status == 200) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
