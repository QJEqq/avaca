package com.mydrop.vpn.data

import android.os.Looper
import android.util.Base64
import com.mydrop.vpn.core.model.ProxyNode
import com.mydrop.vpn.core.model.ProxySettings
import com.mydrop.vpn.core.model.TransportOptions
import com.mydrop.vpn.core.net.isNumericAddress
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Finds a server's address through DNS over HTTPS before the tunnel dials it.
 *
 * The phone's own resolver belongs to the operator, and an operator that blocks VPNs can answer
 * for a server's domain with an address that goes nowhere: the tunnel then "connects" to nothing,
 * and every server on that domain looks dead. Asking a DoH server instead — over HTTPS, which the
 * operator can neither read nor rewrite — gets the real address, and the domain still goes into
 * the handshake as the TLS name and the HTTP Host, so nothing on the server side notices.
 *
 * The query is RFC 8484 wire format (`application/dns-message`), which every public DoH server
 * speaks, unlike the JSON dialects. The app is excluded from its own tunnel, so the question goes
 * out over the phone's real network, as it should for an address the tunnel is about to dial.
 */
object ServerAddressResolver {

    const val DEFAULT_DOH = "https://1.1.1.1/dns-query"

    private data class Entry(val address: String, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    /**
     * The same server with its address resolved, and its domain kept where the handshake needs it:
     * as the TLS name when none was set, and as the Host of transports that send one. A group is
     * resolved member by member; an OpenFlux node has no address of its own to resolve.
     */
    fun resolveNode(node: ProxyNode, dohUrl: String): ProxyNode {
        node.group?.let { group ->
            return node.copy(group = group.copy(members = group.members.map { resolveNode(it, dohUrl) }))
        }
        if (node.settings is ProxySettings.Openflux) return node
        val host = node.server.trim()
        if (host.isEmpty() || isNumericAddress(host)) return node
        val address = resolve(host, dohUrl) ?: return node
        return node.copy(
            server = address,
            tls = node.tls?.let { tls ->
                if (tls.enabled && tls.serverName.isNullOrBlank()) tls.copy(serverName = host) else tls
            },
            transport = node.transport?.withHost(host),
        )
    }

    /** An IPv4 address for [host], from the cache while its TTL lasts; null when DoH did not answer. */
    fun resolve(host: String, dohUrl: String): String? {
        val now = System.currentTimeMillis()
        cache[host]?.takeIf { it.expiresAt > now }?.let { return it.address }
        // Never on the main thread: a cached answer is all that may be had from there.
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        val (address, ttl) = runCatching { query(host, dohUrl.ifBlank { DEFAULT_DOH }) }.getOrNull() ?: return null
        cache[host] = Entry(address, now + ttl.coerceIn(60, 3600) * 1000L)
        return address
    }

    private fun query(host: String, dohUrl: String): Pair<String, Int>? {
        val id = Random.nextInt(0, 0xFFFF)
        val packet = Base64.encodeToString(question(id, host), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val url = URL(dohUrl + (if ('?' in dohUrl) "&" else "?") + "dns=" + packet)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/dns-message")
        return try {
            if (connection.responseCode != 200) return null
            val body = connection.inputStream.use { it.readBytes() }
            firstAddress(body, id)
        } finally {
            connection.disconnect()
        }
    }

    /** A recursive query for the A record of [host]. */
    private fun question(id: Int, host: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id ushr 8); out.write(id and 0xFF)
        out.write(0x01); out.write(0x00) // recursion desired
        out.write(0x00); out.write(0x01) // one question
        repeat(6) { out.write(0x00) } // no answers, authorities or additionals
        host.trimEnd('.').split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            require(bytes.size in 1..63) { "bad label in $host" }
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0x00)
        out.write(0x00); out.write(0x01) // type A
        out.write(0x00); out.write(0x01) // class IN
        return out.toByteArray()
    }

    /** The first A record of an answer to query [id], with its TTL. */
    private fun firstAddress(message: ByteArray, id: Int): Pair<String, Int>? {
        if (message.size < 12 || u16(message, 0) != id) return null
        if (message[3].toInt() and 0x0F != 0) return null // the server said no
        val questions = u16(message, 4)
        val answers = u16(message, 6)
        var at = 12
        repeat(questions) { at = skipName(message, at) + 4 }
        repeat(answers) {
            at = skipName(message, at)
            if (at + 10 > message.size) return null
            val type = u16(message, at)
            val ttl = ((u16(message, at + 4) shl 16) or u16(message, at + 6))
            val length = u16(message, at + 8)
            at += 10
            if (type == 1 && length == 4 && at + 4 <= message.size) {
                val address = (0 until 4).joinToString(".") { (message[at + it].toInt() and 0xFF).toString() }
                return address to ttl
            }
            at += length
        }
        return null
    }

    private fun skipName(message: ByteArray, start: Int): Int {
        var at = start
        while (at < message.size) {
            val length = message[at].toInt() and 0xFF
            if (length == 0) return at + 1
            if (length and 0xC0 == 0xC0) return at + 2 // a pointer ends the name
            at += length + 1
        }
        return at
    }

    private fun u16(message: ByteArray, at: Int): Int =
        ((message[at].toInt() and 0xFF) shl 8) or (message[at + 1].toInt() and 0xFF)

    private fun TransportOptions.withHost(host: String): TransportOptions = when (this) {
        is TransportOptions.WebSocket ->
            if (headers.keys.none { it.equals("Host", ignoreCase = true) }) copy(headers = headers + ("Host" to host)) else this
        is TransportOptions.HttpUpgrade -> if (this.host.isBlank()) copy(host = host) else this
        is TransportOptions.Xhttp -> if (this.host.isBlank()) copy(host = host) else this
        is TransportOptions.Http -> if (this.host.isEmpty()) copy(host = listOf(host)) else this
        else -> this
    }

    private const val TIMEOUT_MILLIS = 3_000
}
