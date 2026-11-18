package com.mydrop.vpn.data

import com.mydrop.vpn.core.xray.OperatorDocument
import com.mydrop.vpn.core.xray.XrayConfigFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where each connection went: through which server, direct, blocked — read from the core's access
 * lines, newest first, the last [capacity] of them.
 *
 * For people running their own servers, who need to see that a rule matched, that a site went
 * where they meant it to, that DNS went through the tunnel. Kept in memory only: it is a list of
 * every site visited, and it dies with the process. Fed only while developer mode is on.
 */
class ConnectionLog(private val capacity: Int = 500) {

    enum class Route { Proxy, Direct, Block, Dns, Other }

    data class Entry(
        val atMillis: Long,
        val network: String,
        val host: String,
        val port: Int,
        /** The inbound it arrived on: `tun-in` for apps, `dns-in` for the core's lookups, `probe-in` for this app's checks. */
        val inbound: String,
        /** The outbound that carried it, as tagged in the document. */
        val outbound: String,
        val route: Route,
        val accepted: Boolean,
        val reason: String,
    ) {
        /** The node id, when [outbound] is a server's own tag. */
        val nodeId: String? get() = outbound.takeIf { it.startsWith(NODE_PREFIX) }?.removePrefix(NODE_PREFIX)
    }

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    /** Takes [line] if it is an access line; returns whether it was one. */
    fun offer(line: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val entry = parse(line, nowMillis) ?: return false
        synchronized(this) {
            _entries.value = buildList(capacity) {
                add(entry)
                addAll(_entries.value.take(capacity - 1))
            }
        }
        return true
    }

    fun clear() {
        synchronized(this) { _entries.value = emptyList() }
    }

    companion object {
        private val NODE_PREFIX = XrayConfigFactory.nodeTag("")

        /**
         * `from <src> accepted|rejected [tcp:|udp:]<host>:<port> [<in> -> <out>] <reason>`, the shape
         * of `AccessMessage.String()` in `common/log/access.go`. The arrow is `->` for a rule,
         * `>>` for the default route and `==>` for an override; only what is on either side of it
         * matters here.
         */
        private val ACCESS = Regex(
            """^from (\S+) (accepted|rejected) (?:(tcp|udp):)?(\S+?):(\d+)(?: \[([^\]]*)])?(?: (.*?))?(?: email: \S+)?$""",
        )
        private val DETOUR = Regex("""^(.*?)\s*(?:==>|->|>>)\s*(.+)$""")

        fun parse(line: String, nowMillis: Long): Entry? {
            val match = ACCESS.matchEntire(line.trim()) ?: return null
            val (_, status, network, host, port, detour, reason) = match.destructured
            val hop = DETOUR.matchEntire(detour.trim())
            val inbound = hop?.groupValues?.get(1)?.trim().orEmpty()
            val outbound = (hop?.groupValues?.get(2) ?: detour).trim()
            return Entry(
                atMillis = nowMillis,
                network = network.ifEmpty { "tcp" },
                host = host.removePrefix("[").removeSuffix("]"),
                port = port.toIntOrNull() ?: return null,
                inbound = inbound,
                outbound = outbound,
                route = routeOf(outbound),
                accepted = status == "accepted",
                reason = reason.trim(),
            )
        }

        fun routeOf(outbound: String): Route = when {
            outbound == XrayConfigFactory.BALANCER_TAG || outbound.startsWith(NODE_PREFIX) ||
                outbound.startsWith(OperatorDocument.CHAIN_TAG_PREFIX) -> Route.Proxy
            outbound == XrayConfigFactory.DIRECT_TAG || outbound == XrayConfigFactory.FRAGMENT_TAG -> Route.Direct
            outbound == XrayConfigFactory.BLOCK_TAG -> Route.Block
            outbound == XrayConfigFactory.DNS_TAG -> Route.Dns
            else -> Route.Other
        }
    }
}
