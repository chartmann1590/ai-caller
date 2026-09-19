package com.charles.localcallagent.debug

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Debug-only UDP SIP mini-registrar (mirrors tools/sip_test_server.py) for
 * headless emulator e2e when host networking (10.0.2.2) is unavailable.
 * Accounts: 1001/1002 password "secret". Realm: 127.0.0.1
 */
class LabSipRegistrar(
    private val bindHost: String = "127.0.0.1",
    private val bindPort: Int = 15060
) {
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var worker: Thread? = null
    private val nonces = ConcurrentHashMap<String, Long>()
    private val users = mapOf("1001" to "secret", "1002" to "secret")

    val port: Int get() = bindPort

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val sock = DatagramSocket(bindPort, InetAddress.getByName(bindHost))
        socket = sock
        worker = thread(name = "LabSipRegistrar", isDaemon = true) {
            Log.i(TAG, "LISTENING udp://$bindHost:$bindPort")
            val buf = ByteArray(65535)
            while (running.get() && !sock.isClosed) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    sock.receive(packet)
                    val data = packet.data.copyOf(packet.length)
                    handle(sock, data, packet.address, packet.port)
                } catch (e: Exception) {
                    if (running.get()) Log.w(TAG, "recv ${e.message}")
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        worker = null
        Log.i(TAG, "STOPPED")
    }

    private fun handle(sock: DatagramSocket, data: ByteArray, addr: InetAddress, port: Int) {
        val text = String(data, Charsets.UTF_8)
        val lines = text.split("\r\n")
        if (lines.isEmpty()) return
        val start = lines[0]
        val headers = linkedMapOf<String, String>()
        var i = 1
        while (i < lines.size && lines[i].isNotEmpty()) {
            val idx = lines[i].indexOf(':')
            if (idx > 0) {
                headers[lines[i].substring(0, idx).trim()] = lines[i].substring(idx + 1).trim()
            }
            i++
        }
        val body = if (i + 1 < lines.size) lines.subList(i + 1, lines.size).joinToString("\r\n") else ""
        Log.i(TAG, "<< $addr:$port $start")

        fun h(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        val base = linkedMapOf(
            "Via" to (h("Via") ?: ""),
            "From" to (h("From") ?: ""),
            "To" to (h("To") ?: ""),
            "Call-ID" to (h("Call-ID") ?: ""),
            "CSeq" to (h("CSeq") ?: "")
        )

        when {
            start.startsWith("REGISTER") -> {
                val uri = start.split(" ").getOrNull(1) ?: "sip:127.0.0.1"
                if (!checkAuth(headers, "REGISTER", uri)) {
                    val nonce = md5("${System.nanoTime()}")
                    nonces[nonce] = System.currentTimeMillis()
                    val resp = base.toMutableMap()
                    resp["WWW-Authenticate"] = "Digest realm=\"127.0.0.1\", nonce=\"$nonce\", algorithm=MD5"
                    respond(sock, addr, port, "SIP/2.0 401 Unauthorized", resp)
                    return
                }
                val resp = base.toMutableMap()
                resp["Contact"] = h("Contact") ?: ""
                resp["Expires"] = h("Expires") ?: "3600"
                if (resp["To"]?.contains("tag=") != true) {
                    resp["To"] = (resp["To"] ?: "") + ";tag=${md5(resp["Call-ID"] ?: "x").take(8)}"
                }
                respond(sock, addr, port, "SIP/2.0 200 OK", resp)
                Log.i(TAG, "REGISTERED ${h("From")}")
            }
            start.startsWith("INVITE") -> {
                val uri = start.split(" ").getOrNull(1) ?: "sip:127.0.0.1"
                if (!checkAuth(headers, "INVITE", uri)) {
                    val nonce = md5("${System.nanoTime()}")
                    nonces[nonce] = System.currentTimeMillis()
                    val resp = base.toMutableMap()
                    resp["WWW-Authenticate"] = "Digest realm=\"127.0.0.1\", nonce=\"$nonce\", algorithm=MD5"
                    respond(sock, addr, port, "SIP/2.0 401 Unauthorized", resp)
                    return
                }
                respond(sock, addr, port, "SIP/2.0 100 Trying", base)
                val toTag = ";tag=echo${md5(base["Call-ID"] ?: "c").take(6)}"
                val ringing = base.toMutableMap()
                if (ringing["To"]?.contains("tag=") != true) {
                    ringing["To"] = (ringing["To"] ?: "") + toTag
                }
                respond(sock, addr, port, "SIP/2.0 180 Ringing", ringing)
                val ok = ringing.toMutableMap()
                ok["Content-Type"] = "application/sdp"
                ok["Contact"] = "<sip:echo@$bindHost:$bindPort>"
                respond(sock, addr, port, "SIP/2.0 200 OK", ok, body)
                Log.i(TAG, "INVITE_ANSWERED ${h("From")}")
            }
            start.startsWith("ACK") || start.startsWith("BYE") || start.startsWith("OPTIONS") -> {
                respond(sock, addr, port, "SIP/2.0 200 OK", base)
            }
            else -> respond(sock, addr, port, "SIP/2.0 501 Not Implemented", base)
        }
    }

    private fun checkAuth(headers: Map<String, String>, method: String, uri: String): Boolean {
        val auth = headers.entries.firstOrNull {
            it.key.equals("Authorization", true) || it.key.equals("Proxy-Authorization", true)
        }?.value ?: return false
        if (!auth.startsWith("Digest ")) return false
        val parts = mutableMapOf<String, String>()
        auth.removePrefix("Digest ").split(",").forEach { item ->
            val t = item.trim()
            val eq = t.indexOf('=')
            if (eq > 0) {
                parts[t.substring(0, eq).trim()] = t.substring(eq + 1).trim().trim('"')
            }
        }
        val user = parts["username"] ?: return false
        val pass = users[user] ?: return false
        val nonce = parts["nonce"] ?: return false
        if (!nonces.containsKey(nonce)) return false
        val realm = parts["realm"] ?: "127.0.0.1"
        val digestUri = parts["uri"] ?: uri
        val ha1 = md5("$user:$realm:$pass")
        val ha2 = md5("$method:$digestUri")
        val expected = md5("$ha1:$nonce:$ha2")
        return expected == parts["response"]
    }

    private fun respond(
        sock: DatagramSocket,
        addr: InetAddress,
        port: Int,
        start: String,
        headers: Map<String, String>,
        body: String = ""
    ) {
        val sb = StringBuilder()
        sb.append(start).append("\r\n")
        headers.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("Content-Length: ").append(body.toByteArray().size).append("\r\n\r\n")
        sb.append(body)
        val bytes = sb.toString().toByteArray()
        sock.send(DatagramPacket(bytes, bytes.size, addr, port))
    }

    private fun md5(s: String): String {
        val dig = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return dig.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val TAG = "LabSipRegistrar"
    }
}
