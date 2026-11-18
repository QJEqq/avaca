package com.mydrop.vpn.core.parse

import com.mydrop.vpn.core.model.NodeGroup
import com.mydrop.vpn.core.model.OperatorConfig
import com.mydrop.vpn.core.model.ProxyNode
import com.mydrop.vpn.core.model.identified
import com.mydrop.vpn.core.model.ProxySettings
import com.mydrop.vpn.core.model.RealityOptions
import com.mydrop.vpn.core.model.TlsOptions
import com.mydrop.vpn.core.model.TransportOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the configuration documents providers hand out instead of a list of share links.
 *
 * Three shapes, three strategies, and the difference between them is not arbitrary:
 *
 * - **sing-box JSON** is the core's own language, so its outbounds are kept *verbatim* as
 *   [ProxySettings.Raw]. Nothing is interpreted and nothing can be lost — including the fields
 *   this app has never heard of.
 * - **Xray / v2ray JSON** describes the same protocols in a different vocabulary, so it has to be
 *   translated field by field. What does not translate is reported rather than silently dropped.
 * - **SIP008** is a Shadowsocks-only list and maps directly.
 *
 * Every parser returns whatever it understood: one unreadable entry in a list of forty is a
 * missing server, not a failed subscription.
 */
object ConfigDocumentParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Outbound tags that exist in every config and dial nothing. */
    private val plumbingTypes = setOf("direct", "block", "dns", "selector", "urltest", "freedom", "blackhole")

    fun parse(document: String, subscriptionId: String?): List<ProxyNode> {
        val trimmed = document.trim()
        val root = runCatching { json.parseToJsonElement(trimmed) }.getOrNull() ?: return emptyList()

        return when {
            // An array is either a list of whole Xray configs (what panels emit per client) or a
            // bare list of outbounds.
            // Either a list of whole configs, or a bare list of outbounds — and the second was
            // being lost entirely. Every element was treated as a document, `fromDocument` found no
            // `outbounds` key in it, and a subscription of forty servers imported as none at all.
            root is JsonArray -> root.flatMap { item ->
                when (item) {
                    // A share link among the documents: Supwave puts the OpenFlux one there,
                    // because a line after the JSON — where it goes in a list of links — breaks
                    // the whole document.
                    is JsonPrimitive -> listOfNotNull(
                        item.takeIf { it.isString }?.let { ProxyUriParser.parse(it.content.trim(), subscriptionId) },
                    )
                    is JsonObject -> if (item["outbounds"] != null || item["servers"] != null) {
                        fromDocument(item, subscriptionId)
                    } else {
                        listOfNotNull(
                            fromOutbound(item, null, subscriptionId)?.let { node ->
                                if (item.isXrayOutbound()) node.copy(operator = OperatorConfig(item.toString())) else node
                            },
                        )
                    }
                    else -> emptyList()
                }
            }
            root is JsonObject -> fromDocument(root, subscriptionId)
            else -> emptyList()
        }
    }

    private fun fromDocument(document: JsonObject, subscriptionId: String?): List<ProxyNode> {
        // SIP008 keeps its servers under "servers" with "server_port"; nothing else uses that pair.
        document["servers"]?.let { servers ->
            // `as?` on every element, not just on the array: a `servers` list holding anything
            // other than objects threw out of `jsonObject` and failed the whole refresh.
            val entries = (servers as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            if (entries.any { it["method"] != null }) {
                return entries.mapNotNull { runCatching { sip008(it, subscriptionId) }.getOrNull() }
            }
        }

        val outbounds = document["outbounds"] as? JsonArray ?: return emptyList()
        val label = document.str("remarks")

        // The operator's own settings ride along with every server the document yields, and the
        // core is handed those rather than a translation of them; see OperatorConfig.
        val byTag = outbounds.mapNotNull { it as? JsonObject }
            .mapNotNull { outbound -> outbound.str("tag")?.takeIf(String::isNotBlank)?.let { it to outbound } }
            .toMap()
        val routing = (document["routing"] as? JsonObject)?.toString()
        val dns = (document["dns"] as? JsonObject)?.toString()
        val roles = rolesOf(document)

        val tagged = outbounds.mapNotNull { element ->
            val outbound = element as? JsonObject ?: return@mapNotNull null
            val node = fromOutbound(outbound, label, subscriptionId) ?: return@mapNotNull null
            val kept = if (outbound.isXrayOutbound()) {
                node.copy(
                    operator = OperatorConfig(
                        outbound = outbound.toString(),
                        chain = chainOf(outbound, byTag).map(JsonObject::toString),
                        routing = routing,
                        dns = dns,
                        roles = roles,
                    ),
                )
            } else {
                node
            }
            outbound.str("tag").orEmpty() to kept
        }
        autoSelect(document, tagged, label)?.let { return listOf(it) }
        return tagged.map { it.second }
    }

    /**
     * The document as one server of several, when it is an auto-select document: a balancer over
     * its outbounds, choosing among them the way the provider set it to. See [NodeGroup] for what
     * reading it as a plain list of outbounds cost.
     *
     * The members are the balancer's own candidates — its `selector` matches tags by prefix, as the
     * core's does — in the provider's order of preference, and its `fallbackTag` last, because that
     * is what the provider made it: the server to use when nothing better answers. Outbounds no
     * balancer names are left out of the group; no route in the document could reach them either.
     *
     * Null for anything that is not a choice between at least two servers, which leaves the
     * document to be read the ordinary way.
     */
    private fun autoSelect(
        document: JsonObject,
        tagged: List<Pair<String, ProxyNode>>,
        label: String?,
    ): ProxyNode? {
        if (tagged.size < 2) return null
        val routing = document["routing"] as? JsonObject ?: return null
        val balancers = (routing["balancers"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        for (balancer in balancers) {
            val selector = (balancer["selector"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNullSafe() }
                .orEmpty()
            val chosen = tagged.filter { (tag, _) -> tag.isNotEmpty() && selector.any { tag.startsWith(it) } }
            val fallbackTag = balancer.str("fallbackTag")
            val fallback = tagged.firstOrNull { it.first.isNotEmpty() && it.first == fallbackTag }
                ?.takeIf { it !in chosen }

            val strategy = balancer["strategy"] as? JsonObject
            val type = strategy?.str("type")?.lowercase().orEmpty()
            val options = strategy?.get("settings") as? JsonObject
            val costs = (options?.get("costs") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            // Stable, so candidates the costs do not tell apart keep the order they were written in.
            val ordered = if (costs.isNotEmpty()) chosen.sortedBy { (tag, _) -> costOf(costs, tag) } else chosen

            val members = (ordered + listOfNotNull(fallback)).map { it.second }.distinctBy { it.id }
            if (members.size < 2) continue
            val first = members.first()
            return first.copy(
                name = label?.takeIf { it.isNotBlank() } ?: first.name,
                // No link says "these seven, chosen this way"; sharing it as its first member's
                // link would hand somebody a different server under the same name.
                sourceUri = null,
                group = NodeGroup(
                    members = members,
                    strategy = if (type == "leastping") NodeGroup.STRATEGY_FASTEST else NodeGroup.STRATEGY_COST,
                    maxDelayMillis = durationMillis(options?.str("maxRTT")),
                ),
            ).identified()
        }
        return null
    }

    /**
     * What a `leastLoad` cost list charges for [tag]: the first entry that matches it, as the core
     * reads them — a regular expression when `regexp` says so, a plain substring otherwise.
     * Unmatched costs nothing, which is also the core's reading.
     */
    private fun costOf(costs: List<JsonObject>, tag: String): Double =
        costs.firstOrNull { cost ->
            val match = cost.str("match") ?: return@firstOrNull false
            if (cost.bool("regexp")) {
                runCatching { Regex(match).containsMatchIn(tag) }.getOrDefault(false)
            } else {
                tag.contains(match)
            }
        }?.let { (it["value"] as? JsonPrimitive)?.doubleOrNull } ?: 0.0

    /** A Go duration as the core writes it — `2s`, `1500ms`, `1m` — in milliseconds; 0 if unreadable. */
    private fun durationMillis(value: String?): Int {
        val match = value?.trim()?.let { DURATION.matchEntire(it) } ?: return 0
        val amount = match.groupValues[1].toDoubleOrNull() ?: return 0
        val scale = when (match.groupValues[2]) {
            "ms" -> 1.0
            "s" -> 1_000.0
            "m" -> 60_000.0
            else -> return 0
        }
        return (amount * scale).toInt().coerceAtLeast(0)
    }

    private val DURATION = Regex("""(\d+(?:\.\d+)?)(ms|s|m)""")

    /**
     * One outbound, whichever dialect it is written in.
     *
     * Wrapped in [runCatching] because everything below reads a document written by somebody else.
     * `jsonObject` and `jsonPrimitive` throw on an element of the wrong kind, and a provider that
     * writes `"port": {}` or `"alpn": [{}]` — a template that rendered wrong, most likely — used to
     * take the whole subscription refresh down with an `IllegalArgumentException`. One unreadable
     * entry in a list of forty is a missing server, not a failed subscription.
     */
    private fun fromOutbound(
        outbound: JsonObject,
        label: String?,
        subscriptionId: String?,
    ): ProxyNode? = runCatching {
        // Xray calls it "protocol", sing-box calls it "type".
        val xray = outbound["protocol"] != null && outbound["settings"] != null
        if (xray) {
            fromXray(outbound, label, subscriptionId)
        } else {
            fromSingBox(outbound, subscriptionId)
        }
    }.getOrNull()

    // ------------------------------------------------------------- sing-box

    /**
     * A sing-box outbound, kept as written.
     *
     * The endpoint is lifted out so the node behaves like any other in the list — it can be named,
     * pinged, sorted and failed over — while the document itself travels untouched to the core.
     */
    private fun fromSingBox(outbound: JsonObject, subscriptionId: String?): ProxyNode? {
        val type = outbound.str("type")?.lowercase() ?: return null
        if (type in plumbingTypes) return null

        val server = outbound.str("server") ?: return null
        val port = outbound.port("server_port") ?: return null
        val name = outbound.str("tag")?.takeIf { it.isNotBlank() } ?: "$server:$port"

        val settings = ProxySettings.Raw(outbound = outbound.toString(), declaredType = type)
        return ProxyNode(
            id = "",
            name = name,
            server = server,
            port = port,
            settings = settings,
            subscriptionId = subscriptionId,
        ).identified()
    }

    // ----------------------------------------------------------------- Xray

    /**
     * An Xray outbound translated into the typed model.
     *
     * Passing this one through raw is not an option the way it is for sing-box: the core would
     * reject `vnext`, `streamSettings` and the rest outright, so the choice is to translate or to
     * refuse the subscription.
     */
    private fun fromXray(outbound: JsonObject, label: String?, subscriptionId: String?): ProxyNode? {
        val protocol = outbound.str("protocol")?.lowercase() ?: return null
        if (protocol in plumbingTypes) return null
        if (protocol == "hysteria") return fromXrayHysteria(outbound, label, subscriptionId)

        val settingsObject = outbound["settings"]?.jsonObject ?: return null
        val vnext = (settingsObject["vnext"] as? JsonArray)?.firstOrNull()?.jsonObject
        val servers = (settingsObject["servers"] as? JsonArray)?.firstOrNull()?.jsonObject
        val endpoint = vnext ?: servers ?: return null

        val server = endpoint.str("address") ?: return null
        val port = endpoint.port("port") ?: return null
        val user = (endpoint["users"] as? JsonArray)?.firstOrNull()?.jsonObject

        val proxySettings = when (protocol) {
            "vless" -> ProxySettings.Vless(
                uuid = user?.str("id", "uuid") ?: return null,
                flow = user.str("flow").orEmpty(),
                packetEncoding = user.str("packetEncoding", "packet_encoding", "packetencoding") ?: "xudp",
            )

            "vmess" -> ProxySettings.Vmess(
                uuid = user?.str("id", "uuid") ?: return null,
                alterId = user["alterId"]?.jsonPrimitive?.intOrNull
                    ?: user["alter_id"]?.jsonPrimitive?.intOrNull
                    ?: user["aid"]?.jsonPrimitive?.intOrNull
                    ?: 0,
                security = user.str("security", "scy", "encryption") ?: "auto",
            )

            "trojan" -> ProxySettings.Trojan(password = endpoint.str("password") ?: return null)

            "shadowsocks" -> ProxySettings.Shadowsocks(
                method = endpoint.str("method", "cipher") ?: return null,
                password = endpoint.str("password") ?: return null,
            )

            // Anything else Xray can express and this model cannot. Reported by absence rather
            // than by a node that would fail to dial.
            else -> return null
        }

        val stream = outbound["streamSettings"]?.jsonObject ?: outbound["stream_settings"]?.jsonObject
        // An Xray document can name a transport sing-box has no implementation of — xhttp and
        // splithttp are the common ones. Translating everything but the transport would produce a
        // server that dials and carries nothing.
        stream?.str("network", "net")?.lowercase()?.let { declared ->
            if (declared !in KNOWN_NETWORKS && declared !in PLAIN_NETWORKS) return null
        }
        return ProxyNode(
            id = "",
            name = label?.takeIf { it.isNotBlank() } ?: outbound.str("tag") ?: "$server:$port",
            server = server,
            port = port,
            settings = proxySettings,
            tls = stream?.let(::xrayTls),
            transport = stream?.let(::xrayTransport),
            subscriptionId = subscriptionId,
        ).identified()
    }

    /**
     * Xray's Hysteria2, which it calls `hysteria` with `version: 2`: the endpoint flat under
     * `settings` rather than in `vnext`/`servers`, the password in `streamSettings.hysteriaSettings`
     * and the obfuscation in `finalmask`.
     *
     * Read so that a Hysteria2 server gets its operator's document like any other — until this, it
     * was the one kind of server a panel's documents described and this app could only take from a
     * share link, which drops what the link format has no room for.
     */
    private fun fromXrayHysteria(outbound: JsonObject, label: String?, subscriptionId: String?): ProxyNode? {
        val settings = outbound["settings"] as? JsonObject ?: return null
        val version = (settings["version"] as? JsonPrimitive)?.intOrNull
        if (version != null && version != 2) return null
        val server = settings.str("address") ?: return null
        val port = settings.port("port") ?: return null
        val stream = outbound["streamSettings"] as? JsonObject ?: return null
        val auth = (stream["hysteriaSettings"] as? JsonObject)?.str("auth") ?: return null
        val salamander = ((stream["finalmask"] as? JsonObject)?.get("udp") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.str("type").equals("salamander", ignoreCase = true) }
        return ProxyNode(
            id = "",
            name = label?.takeIf { it.isNotBlank() } ?: outbound.str("tag") ?: "$server:$port",
            server = server,
            port = port,
            settings = ProxySettings.Hysteria2(
                password = auth,
                obfsType = if (salamander != null) "salamander" else "",
                obfsPassword = (salamander?.get("settings") as? JsonObject)?.str("password").orEmpty(),
            ),
            tls = xrayTls(stream),
            subscriptionId = subscriptionId,
        ).identified()
    }

    /** What sing-box can carry; see [ProxyUriParser] for what happens when it cannot. */
    private val KNOWN_NETWORKS = setOf("ws", "grpc", "httpupgrade", "xhttp", "splithttp")
    private val PLAIN_NETWORKS = setOf("tcp", "raw", "none", "original")

    private fun xrayTls(stream: JsonObject): TlsOptions? {
        val security = stream.str("security")?.lowercase()
        if (security.isNullOrEmpty() || security == "none") return null

        val tlsSettings = stream["tlsSettings"]?.jsonObject
            ?: stream["tls_settings"]?.jsonObject
            ?: stream["tls-settings"]?.jsonObject
        val realitySettings = stream["realitySettings"]?.jsonObject
            ?: stream["reality_settings"]?.jsonObject
            ?: stream["reality-settings"]?.jsonObject
        val active = realitySettings ?: tlsSettings

        return TlsOptions(
            serverName = active?.str("serverName", "server_name", "server-name", "servername", "sni", "peer"),
            insecure = tlsSettings?.bool("allowInsecure", "allow_insecure", "allowinsecure", "skip-cert-verify", "skip_cert_verify", "insecure") ?: false,
            alpn = ((tlsSettings?.get("alpn") ?: tlsSettings?.get("alpn_list")) as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNullSafe() }
                .orEmpty(),
            fingerprint = active?.str("fingerprint", "client_fingerprint", "client-fingerprint", "fp"),
            pinnedCertSha256 = tlsSettings
                ?.str("pinnedPeerCertSha256", "pinned_peer_cert_sha256", "pcs")
                .orEmpty(),
            verifyPeerCertByName = tlsSettings
                ?.str("verifyPeerCertByName", "verify_peer_cert_by_name", "vcn")
                .orEmpty(),
            reality = (realitySettings?.str("publicKey", "public_key", "public-key", "pbk", "publickey", "pubkey", "key"))?.let { key ->
                RealityOptions(
                    publicKey = normalizeRealityKey(key),
                    shortId = realitySettings.str("shortId", "short_id", "short-id", "shortid", "sid").orEmpty(),
                    spiderX = realitySettings.str("spiderX", "spider_x", "spider-x", "spiderx", "spx", "path") ?: "/",
                )
            },
        )
    }

    private fun xrayTransport(stream: JsonObject): TransportOptions? =
        when (stream.str("network", "net")?.lowercase()) {
            "ws" -> {
                val ws = stream["wsSettings"]?.jsonObject
                    ?: stream["ws_settings"]?.jsonObject
                    ?: stream["ws-settings"]?.jsonObject
                val host = ws?.get("headers")?.jsonObject?.str("Host", "host")
                TransportOptions.WebSocket(
                    path = ws?.str("path") ?: "/",
                    headers = host?.let { mapOf("Host" to it) }.orEmpty(),
                )
            }

            "grpc" -> {
                val grpc = stream["grpcSettings"]?.jsonObject
                    ?: stream["grpc_settings"]?.jsonObject
                    ?: stream["grpc-settings"]?.jsonObject
                TransportOptions.Grpc(
                    serviceName = grpc?.str("serviceName", "service_name", "service-name").orEmpty(),
                )
            }

            "httpupgrade" -> {
                val upgrade = stream["httpupgradeSettings"]?.jsonObject
                    ?: stream["httpupgrade_settings"]?.jsonObject
                    ?: stream["httpupgrade-settings"]?.jsonObject
                TransportOptions.HttpUpgrade(
                    host = upgrade?.str("host").orEmpty(),
                    path = upgrade?.str("path") ?: "/",
                )
            }

            "xhttp", "splithttp" -> {
                val xhttp = stream["xhttpSettings"]?.jsonObject
                    ?: stream["splithttpSettings"]?.jsonObject
                    ?: stream["xhttp_settings"]?.jsonObject
                    ?: stream["splithttp_settings"]?.jsonObject
                TransportOptions.Xhttp(
                    path = xhttp?.str("path") ?: "/",
                    host = xhttp?.str("host").orEmpty(),
                    mode = xhttp?.str("mode") ?: "auto",
                    extra = xhttp?.let(::xhttpExtraOf),
                )
            }

            // Every network name this parser accepts has a branch above, and that is now checked
            // rather than assumed. It was not: `http`, `h2` and `quic` were accepted as known and
            // then fell through to null here, so the node was built with no transport at all and
            // dialled as plain TCP — a server that connected, showed a plausible latency and
            // carried nothing. The accepted set and the branches below it have to agree.
            else -> null
        }

    /**
     * What the core reads under `extra`: the document's own object when it has one — the core then
     * ignores everything else but host, path and mode — and otherwise the rest of the settings,
     * which mean the same thing written one level up.
     */
    private fun xhttpExtraOf(settings: JsonObject): String? {
        (settings["extra"] as? JsonObject)?.let { extra -> return extra.takeIf { it.isNotEmpty() }?.toString() }
        val rest = JsonObject(settings.filterKeys { it !in XHTTP_MODELLED })
        return rest.takeIf { it.isNotEmpty() }?.toString()
    }

    private val XHTTP_MODELLED = setOf("host", "path", "mode", "extra")

    private fun JsonObject.isXrayOutbound(): Boolean = this["protocol"] != null && this["settings"] != null

    /**
     * The outbounds [outbound] is dialled through, nearest first: whatever its
     * `streamSettings.sockopt.dialerProxy` or `proxySettings.tag` names, and on down. Stops at a
     * name the document does not define and at a loop, either of which the core would refuse.
     */
    private fun chainOf(outbound: JsonObject, byTag: Map<String, JsonObject>): List<JsonObject> {
        val chain = mutableListOf<JsonObject>()
        val seen = mutableSetOf(outbound.str("tag").orEmpty())
        var current = outbound
        repeat(MAX_CHAIN) {
            val next = nextHop(current) ?: return chain
            if (!seen.add(next)) return chain
            val hop = byTag[next] ?: return chain
            chain += hop
            current = hop
        }
        return chain
    }

    private fun nextHop(outbound: JsonObject): String? =
        ((outbound["streamSettings"] as? JsonObject)?.get("sockopt") as? JsonObject)
            ?.str("dialerProxy")?.takeIf(String::isNotBlank)
            ?: (outbound["proxySettings"] as? JsonObject)?.str("tag")?.takeIf(String::isNotBlank)

    private const val MAX_CHAIN = 4

    /** See [OperatorConfig.roles]. */
    private fun rolesOf(document: JsonObject): Map<String, String> = buildMap {
        (document["inbounds"] as? JsonArray)?.forEach { inbound ->
            (inbound as? JsonObject)?.str("tag")?.takeIf(String::isNotBlank)?.let { put(it, "inbound") }
        }
        (document["outbounds"] as? JsonArray)?.forEach { element ->
            val outbound = element as? JsonObject ?: return@forEach
            val tag = outbound.str("tag")?.takeIf(String::isNotBlank) ?: return@forEach
            when (outbound.str("protocol", "type")?.lowercase()) {
                "freedom", "direct" -> put(tag, "direct")
                "blackhole", "block" -> put(tag, "block")
                "dns" -> put(tag, "dns")
                in PROXY_PROTOCOLS -> put(tag, "proxy")
            }
        }
        ((document["routing"] as? JsonObject)?.get("balancers") as? JsonArray)?.forEach { balancer ->
            (balancer as? JsonObject)?.str("tag")?.takeIf(String::isNotBlank)?.let { put(it, "proxy") }
        }
    }

    private val PROXY_PROTOCOLS = setOf(
        "vless", "vmess", "trojan", "shadowsocks", "socks", "http", "hysteria", "hysteria2", "wireguard", "tuic",
    )

    // -------------------------------------------------------------- SIP008

    private fun sip008(entry: JsonObject, subscriptionId: String?): ProxyNode? {
        val server = entry.str("server") ?: return null
        val port = entry.port("server_port") ?: return null
        val settings = ProxySettings.Shadowsocks(
            method = entry.str("method") ?: return null,
            password = entry.str("password") ?: return null,
            plugin = entry.str("plugin").orEmpty(),
            pluginOptions = entry.str("plugin_opts").orEmpty(),
        )
        return ProxyNode(
            id = "",
            name = entry.str("remarks")?.takeIf { it.isNotBlank() } ?: "$server:$port",
            server = server,
            port = port,
            settings = settings,
            subscriptionId = subscriptionId,
        ).identified()
    }

    // ------------------------------------------------------------- Helpers

    /**
     * A port that can actually be dialled, or null.
     *
     * The range is checked here rather than left to the core. Nothing downstream validates it: a
     * `"port": 0` or `"port": 99999` from a mis-rendered template produced a node that looked
     * ordinary in the list and could never connect, and the range is not a matter of opinion.
     */
    private fun JsonObject.port(key: String): Int? =
        (this[key] as? JsonPrimitive)?.contentOrNullSafe()?.toIntOrNull()?.takeIf { it in 1..65535 }

    private fun JsonObject.str(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.contentOrNullSafe()
        }

    private fun JsonObject.bool(vararg keys: String): Boolean =
        keys.firstNotNullOfOrNull { key ->
            val prim = this[key] as? JsonPrimitive ?: return@firstNotNullOfOrNull null
            prim.booleanOrNull ?: (prim.contentOrNullSafe()?.let { it == "1" || it.equals("true", ignoreCase = true) })
        } ?: false

    /** Null for JSON nulls and for numbers-as-strings that are empty, without throwing. */
    private fun JsonPrimitive.contentOrNullSafe(): String? =
        runCatching { content.takeIf { it.isNotEmpty() && it != "null" } }.getOrNull()
}
