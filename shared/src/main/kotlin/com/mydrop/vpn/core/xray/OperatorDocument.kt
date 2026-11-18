package com.mydrop.vpn.core.xray

import com.mydrop.vpn.core.model.AppSettings
import com.mydrop.vpn.core.model.OperatorConfig
import com.mydrop.vpn.core.model.ProxyNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * An operator's Xray document put to work inside this app's own, the way Happ runs the same
 * document: their outbound as they wrote it, their rules pointed at our outbounds, their resolvers
 * under our tag.
 *
 * What is changed on the way, and only that:
 *  - tags, which have to be ours for the balancer, the byte counters and the server switch to find
 *    the outbound, and references between outbounds, renamed alongside;
 *  - settings the core has removed and refuses the *whole* document over rather than ignoring
 *    (`allowInsecure`, a Trojan `flow`, freedom's old `noise`), because a tunnel that will not start
 *    serves nobody;
 *  - geo references the databases on disk cannot resolve, for the same reason;
 *  - the server's address, when this app resolved it itself (ServerAddressResolver).
 */
internal object OperatorDocument {

    /** Tags of the hops a server is dialled through; outside the balancer's `proxy-` prefix on purpose. */
    const val CHAIN_TAG_PREFIX = "chain-"

    fun parseObject(text: String?): JsonObject? =
        text?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() as? JsonObject }

    // ------------------------------------------------------------------ Outbounds

    /**
     * The outbound for [node] followed by the hops it is dialled through. Null when the stored
     * text no longer reads as JSON, and the caller builds from the typed model instead.
     */
    fun outbounds(node: ProxyNode, operator: OperatorConfig): List<JsonObject>? {
        val outbound = parseObject(operator.outbound) ?: return null
        val hops = operator.chain.map { parseObject(it) ?: return null }
        val renamed = LinkedHashMap<String, String>()
        hops.forEachIndexed { index, hop ->
            hop.text("tag")?.let { renamed.putIfAbsent(it, "$CHAIN_TAG_PREFIX${node.id}-${index + 1}") }
        }
        val main = outbound.cleaned(renamed)
            .with("tag", JsonPrimitive(XrayConfigFactory.nodeTag(node.id)))
            .withEndpoint(node)
        return listOf(main) + hops.mapNotNull { hop ->
            val tag = hop.text("tag")?.let(renamed::get) ?: return@mapNotNull null
            hop.cleaned(renamed).with("tag", JsonPrimitive(tag))
        }
    }

    private fun JsonObject.cleaned(renamed: Map<String, String>): JsonObject {
        var result = this
        val protocol = text("protocol")?.lowercase()

        (result["settings"] as? JsonObject)?.let { settings ->
            val kept = when (protocol) {
                "trojan" -> settings.mapObjects("servers") { it.without("flow") }
                "freedom" -> settings.without("noise")
                else -> settings
            }
            result = result.with("settings", kept)
        }

        (result["streamSettings"] as? JsonObject)?.let { stream ->
            var kept = stream
            (kept["tlsSettings"] as? JsonObject)?.let { kept = kept.with("tlsSettings", it.without("allowInsecure")) }
            (kept["sockopt"] as? JsonObject)?.let { sockopt ->
                val via = sockopt.text("dialerProxy")?.let(renamed::get)
                val next = if (via != null) sockopt.with("dialerProxy", JsonPrimitive(via)) else sockopt.without("dialerProxy")
                kept = if (next.isEmpty()) kept.without("sockopt") else kept.with("sockopt", next)
            }
            result = result.with("streamSettings", kept)
        }

        (result["proxySettings"] as? JsonObject)?.let { proxy ->
            val via = proxy.text("tag")?.let(renamed::get)
            result = if (via != null) result.with("proxySettings", proxy.with("tag", JsonPrimitive(via))) else result.without("proxySettings")
        }
        return result
    }

    /** The address this app dialled, when it is not the one written; the name stays as SNI and Host. */
    private fun JsonObject.withEndpoint(node: ProxyNode): JsonObject {
        val settings = this["settings"] as? JsonObject ?: return this
        val key = listOf("vnext", "servers").firstOrNull { (settings[it] as? JsonArray)?.firstOrNull() is JsonObject }
        // Hysteria keeps its endpoint flat under `settings`, the others in a list.
        val written = if (key != null) {
            ((settings[key] as JsonArray).first() as JsonObject).text("address")
        } else {
            settings.text("address")
        } ?: return this
        if (written.equals(node.server, ignoreCase = true)) return this

        val movedSettings = if (key != null) {
            val list = settings[key] as JsonArray
            val first = list.first() as JsonObject
            settings.with(key, JsonArray(listOf(first.with("address", JsonPrimitive(node.server))) + list.drop(1)))
        } else {
            settings.with("address", JsonPrimitive(node.server))
        }
        var result = with("settings", movedSettings)
        val stream = result["streamSettings"] as? JsonObject ?: return result
        var kept = stream
        if (stream.text("security")?.lowercase() == "tls") {
            val tls = stream["tlsSettings"] as? JsonObject ?: JsonObject(emptyMap())
            if (tls.text("serverName").isNullOrBlank()) kept = kept.with("tlsSettings", tls.with("serverName", JsonPrimitive(written)))
        }
        HOST_CARRYING.forEach { transportKey ->
            val transport = kept[transportKey] as? JsonObject ?: return@forEach
            if (transport.text("host").isNullOrBlank()) kept = kept.with(transportKey, transport.with("host", JsonPrimitive(written)))
        }
        return result.with("streamSettings", kept)
    }

    private val HOST_CARRYING = listOf("wsSettings", "httpupgradeSettings", "xhttpSettings", "splithttpSettings")

    // ------------------------------------------------------------------ Routing and DNS

    /**
     * The operator's routing and resolvers, translated for this document.
     *
     * @property rules their rules, each pointed at this app's outbound of the same role.
     * @property dropped rules left out: pointed at nothing this document has, matching on the
     *   process (which a tunnel never sees), or naming a category the databases do not hold.
     * @property hasRouting whether the document had rules at all; when it did, they replace this
     *   app's own "Russian sites direct" defaults, as they do in Happ.
     * @property dns their resolvers under this app's tag, or null to keep this app's.
     */
    data class Plan(
        val rules: List<JsonObject>,
        val dropped: Int,
        val hasRouting: Boolean,
        val dns: JsonObject?,
    )

    /**
     * Null when there is nothing of the operator's to apply.
     *
     * @param useDns false when the user chose a resolver on the DNS screen, which is theirs to
     *   choose and outranks the operator's.
     * @param geo whether a list entry can be resolved here; see [GeoCatalog].
     */
    fun plan(operator: OperatorConfig?, settings: AppSettings, useDns: Boolean, geo: (String) -> Boolean): Plan? {
        operator ?: return null
        val routing = parseObject(operator.routing)
        val dnsDocument = if (useDns) parseObject(operator.dns) else null
        val dns = dnsDocument?.let { dns(it, settings, geo) }
        val operatorDnsTag = dnsDocument?.text("tag") ?: parseObject(operator.dns)?.text("tag")

        val written = (routing?.get("rules") as? JsonArray).orEmpty()
        val rules = written.mapNotNull { rule ->
            (rule as? JsonObject)?.let { translate(it, operator.roles, operatorDnsTag, geo) }
        }
        if (routing == null && dns == null) return null
        return Plan(rules = rules, dropped = written.size - rules.size, hasRouting = routing != null, dns = dns)
    }

    private fun translate(
        rule: JsonObject,
        roles: Map<String, String>,
        dnsTag: String?,
        geo: (String) -> Boolean,
    ): JsonObject? {
        // Any balancer of theirs is a choice between their servers, which is what ours is.
        val target = if (rule.text("balancerTag") != null) {
            "balancerTag" to XrayConfigFactory.BALANCER_TAG
        } else {
            when (roles[rule.text("outboundTag")]) {
                "proxy" -> "balancerTag" to XrayConfigFactory.BALANCER_TAG
                "direct" -> "outboundTag" to XrayConfigFactory.DIRECT_TAG
                "block" -> "outboundTag" to XrayConfigFactory.BLOCK_TAG
                "dns" -> "outboundTag" to XrayConfigFactory.DNS_TAG
                else -> return null
            }
        }

        val fields = LinkedHashMap<String, JsonElement>()
        for ((key, value) in rule) {
            when (key) {
                "outboundTag", "balancerTag", "type" -> Unit
                // The tunnel never learns which app a packet came from by process name.
                "process" -> return null
                "inboundTag" -> {
                    val tags = value.strings().map { tag ->
                        when {
                            roles[tag] == "inbound" -> XrayConfigFactory.TUN_TAG
                            dnsTag != null && tag == dnsTag -> XrayConfigFactory.DNS_IN_TAG
                            else -> tag
                        }
                    }.distinct()
                    fields[key] = JsonArray(tags.map(::JsonPrimitive))
                }
                else -> {
                    val listed = value.strings()
                    if (listed.isEmpty() || listed.none(::isGeoReference)) {
                        fields[key] = value
                    } else {
                        val kept = listed.filter(geo)
                        // A matcher emptied by the filter would match everything, which is the
                        // opposite of what the rule said; the rule goes instead.
                        if (kept.isEmpty()) return null
                        fields[key] = JsonArray(kept.map(::JsonPrimitive))
                    }
                }
            }
        }
        return JsonObject(mapOf("type" to JsonPrimitive("field")) + fields + (target.first to JsonPrimitive(target.second)))
    }

    private fun dns(document: JsonObject, settings: AppSettings, geo: (String) -> Boolean): JsonObject? {
        val servers = (document["servers"] as? JsonArray).orEmpty().mapNotNull { server ->
            when (server) {
                is JsonPrimitive -> server.takeUnless { it.content.trim().equals("fakedns", ignoreCase = true) }
                is JsonObject -> server.takeUnless { it.text("address")?.trim().equals("fakedns", ignoreCase = true) }
                    ?.let { geoFiltered(it, geo) }
                else -> null
            }
        }
        if (servers.isEmpty()) return null

        // This app's own resolver races the operator's, never waits behind them. A phone measured
        // it: both resolvers of an operator's template timed out through the server the tunnel was
        // on — four seconds each, one after the other — so every site took eight seconds to fail
        // while messengers, which dial by address, carried on. Happ never showed it. With the
        // operator's servers and this one in a single parallel group the first answer wins
        // (`app/dns/dns.go`, parallelQuery), so a resolver that answers is used and one that
        // does not costs nothing.
        val ours = XrayConfigFactory.xrayResolver(settings.remoteDns)
            ?.takeIf { resolver -> servers.none { (it as? JsonPrimitive)?.content == resolver } }
        val racing = if (ours != null) servers + JsonPrimitive(ours) else servers

        val hosts = (document["hosts"] as? JsonObject)?.let { written ->
            JsonObject(written.filterKeys { name -> !isGeoReference(name) || geo(name) })
        }
        val fields = LinkedHashMap<String, JsonElement>()
        document.forEach { (key, value) -> if (key.lowercase() !in DNS_OWN) fields[key] = value }
        fields["servers"] = JsonArray(racing)
        fields["enableParallelQuery"] = JsonPrimitive(true)
        hosts?.let { fields["hosts"] = it }
        // The document decides IPv4 or both only while this side can carry IPv6 at all.
        fields["queryStrategy"] = JsonPrimitive(
            if (settings.enableIpv6) document.text("queryStrategy") ?: "UseIP" else "UseIPv4",
        )
        fields["tag"] = JsonPrimitive(XrayConfigFactory.DNS_IN_TAG)
        return JsonObject(fields)
    }

    private val DNS_OWN = setOf("servers", "hosts", "querystrategy", "tag", "fakedns", "enableparallelquery")

    /** A resolver entry whose name or answer lists lost their last geo entry is dropped, not widened. */
    private fun geoFiltered(server: JsonObject, geo: (String) -> Boolean): JsonObject? {
        var result = server
        for (key in listOf("domains", "expectIPs", "expectedIPs", "unexpectedIPs")) {
            val listed = (server[key] as? JsonArray)?.strings() ?: continue
            if (listed.none(::isGeoReference)) continue
            val kept = listed.filter(geo)
            if (kept.isEmpty()) return null
            result = result.with(key, JsonArray(kept.map(::JsonPrimitive)))
        }
        return result
    }

    fun isGeoReference(entry: String): Boolean {
        val value = entry.trim().lowercase()
        return value.startsWith("geosite:") || value.startsWith("geoip:") || value.startsWith("ext:")
    }

    // ------------------------------------------------------------------ JSON helpers

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.with(key: String, value: JsonElement): JsonObject = JsonObject(this + (key to value))

    /** Without [key] in any capitalisation: the core's JSON reader matches field names that way. */
    private fun JsonObject.without(key: String): JsonObject = JsonObject(filterKeys { !it.equals(key, ignoreCase = true) })

    private fun JsonObject.mapObjects(key: String, transform: (JsonObject) -> JsonObject): JsonObject {
        val list = this[key] as? JsonArray ?: return this
        return with(key, JsonArray(list.map { (it as? JsonObject)?.let(transform) ?: it }))
    }

    /** A list as the core reads one: an array of strings, or one string of comma-separated entries. */
    private fun JsonElement.strings(): List<String> = when (this) {
        is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        is JsonPrimitive -> if (isString) content.split(',').map(String::trim).filter(String::isNotEmpty) else emptyList()
        else -> emptyList()
    }
}
