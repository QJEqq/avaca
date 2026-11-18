package com.mydrop.vpn.core.xray

import com.mydrop.vpn.core.model.AppSettings
import com.mydrop.vpn.core.model.ProxyNode
import com.mydrop.vpn.core.model.ProxySettings
import com.mydrop.vpn.core.model.RoutingMode
import com.mydrop.vpn.core.model.TransportOptions
import com.mydrop.vpn.core.parse.ConfigDocumentParser
import com.mydrop.vpn.core.parse.ProxyUriParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A server from an operator's Xray document runs on the operator's settings, as it does in Happ.
 *
 * The fixture is the shape of a real whitelist server that connected here and carried nothing —
 * its XHTTP lives almost entirely in `extra` — with every name, key and address replaced.
 */
class OperatorPassthroughTest {

    private val extra = """
        {
          "headers": {},
          "mode": "packet-up",
          "scMaxEachPostBytes": "2048-3072",
          "scMinPostsIntervalMs": "4-18",
          "seqKey": "seq",
          "seqPlacement": "query",
          "sessionIDKey": "sid",
          "sessionIDLength": "16",
          "sessionIDPlacement": "cookie",
          "sessionIDTable": "BASE36",
          "uplinkDataKey": "X-Token",
          "uplinkDataPlacement": "header",
          "uplinkHTTPMethod": "GET",
          "xPaddingBytes": "48-320",
          "xPaddingHeader": "X-Pad",
          "xPaddingKey": "q",
          "xPaddingMethod": "tokenish",
          "xPaddingObfsMode": true,
          "xPaddingPlacement": "queryInHeader",
          "xmux": {"cMaxReuseTimes": "256-512", "hKeepAlivePeriod": 0, "hMaxRequestTimes": "100-200", "hMaxReusableSecs": "", "maxConcurrency": "4-8"}
        }
    """.trimIndent()

    private fun whitelistDocument(extraRules: String = "") = """
        {
          "dns": {"queryStrategy": "UseIP", "servers": ["192.0.2.53", "198.51.100.53"]},
          "inbounds": [
            {"listen": "127.0.0.1", "port": 10808, "protocol": "socks", "settings": {"auth": "noauth", "udp": true}, "tag": "socks"},
            {"listen": "127.0.0.1", "port": 10809, "protocol": "http", "settings": {}, "tag": "http"}
          ],
          "meta": null,
          "outbounds": [
            {
              "protocol": "vless",
              "settings": {"vnext": [{"address": "edge.example.com", "port": 443,
                "users": [{"encryption": "none", "flow": "", "id": "11111111-2222-4333-8444-555555555555"}]}]},
              "streamSettings": {
                "network": "xhttp",
                "security": "tls",
                "tlsSettings": {"alpn": ["h2", "http/1.1"], "enableSessionResumption": false,
                  "fingerprint": "firefox", "serverName": "edge.example.com"},
                "xhttpSettings": {"extra": $extra, "host": "edge.example.com", "mode": "packet-up", "path": "/api/v1/stream/"}
              },
              "tag": "proxy"
            },
            {"protocol": "freedom", "tag": "direct"},
            {"protocol": "blackhole", "tag": "block"}
          ],
          "remarks": "Белые Списки №1",
          "routing": {
            "domainMatcher": "hybrid",
            "domainStrategy": "IPIfNonMatch",
            "rules": [
              $extraRules
              {"outboundTag": "direct", "protocol": ["bittorrent"], "type": "field"},
              {"domain": ["geosite:category-ru", "domain:gosuslugi.ru", "domain:avito.ru"], "outboundTag": "direct", "type": "field"}
            ]
          }
        }
    """.trimIndent()

    private val everyCategory = GeoCatalog(sites = setOf("category-ru", "category-ads-all"), ips = setOf("ru", "private"))

    private fun parsed(document: String = whitelistDocument()): ProxyNode =
        ConfigDocumentParser.parse(document, "sub").single()

    private fun build(
        node: ProxyNode,
        settings: AppSettings = AppSettings(),
        dnsOverride: String? = null,
        geoCatalog: GeoCatalog? = everyCategory,
        nodes: List<ProxyNode> = listOf(node),
    ): Pair<XrayConfigFactory.Document, JsonObject> {
        val document = XrayConfigFactory.build(
            nodes = nodes,
            selected = node,
            settings = settings,
            dnsOverride = dnsOverride,
            geoAvailable = true,
            geoCatalog = geoCatalog,
        )
        return document to Json.parseToJsonElement(document.json).jsonObject
    }

    private fun JsonObject.outbound(tag: String): JsonObject =
        this["outbounds"]!!.jsonArray.map { it.jsonObject }.single { it["tag"]?.jsonPrimitive?.content == tag }

    private fun JsonObject.rules(): List<JsonObject> =
        this["routing"]!!.jsonObject["rules"]!!.jsonArray.map { it.jsonObject }

    private fun JsonObject.mentions(text: String): Boolean = toString().contains(text)

    // ------------------------------------------------------------------ The outbound

    @Test
    fun `the operator's XHTTP settings reach the core whole`() {
        val node = parsed()
        assertNotNull(node.operator)
        assertTrue("shown as a JSON server, as Happ marks it", "JSON" in node.badges)
        val modelled = node.transport as TransportOptions.Xhttp
        assertEquals("GET", Json.parseToJsonElement(modelled.extra!!).jsonObject["uplinkHTTPMethod"]!!.jsonPrimitive.content)

        val (_, config) = build(node)
        val outbound = config.outbound(XrayConfigFactory.nodeTag(node.id))
        val stream = outbound["streamSettings"]!!.jsonObject
        assertEquals(Json.parseToJsonElement(extra), stream["xhttpSettings"]!!.jsonObject["extra"])
        assertEquals("firefox", stream["tlsSettings"]!!.jsonObject["fingerprint"]!!.jsonPrimitive.content)
        assertEquals(JsonArray(listOf(JsonPrimitive("h2"), JsonPrimitive("http/1.1"))), stream["tlsSettings"]!!.jsonObject["alpn"])
    }

    @Test
    fun `settings the core refuses the whole document over are taken out, hops are renamed`() {
        val document = """
            {
              "outbounds": [
                {"tag": "proxy", "protocol": "vless",
                 "settings": {"vnext": [{"address": "edge.example.com", "port": 443,
                   "users": [{"id": "11111111-2222-4333-8444-555555555555", "encryption": "none"}]}]},
                 "streamSettings": {"network": "tcp", "security": "tls",
                   "tlsSettings": {"serverName": "edge.example.com", "allowInsecure": true},
                   "sockopt": {"dialerProxy": "frag", "tcpFastOpen": true}}},
                {"tag": "frag", "protocol": "freedom",
                 "settings": {"fragment": {"packets": "tlshello", "length": "100-200", "interval": "10-20"}}},
                {"tag": "direct", "protocol": "freedom"}
              ]
            }
        """.trimIndent()
        val node = parsed(document)
        val (_, config) = build(node)
        val tag = XrayConfigFactory.nodeTag(node.id)
        val stream = config.outbound(tag)["streamSettings"]!!.jsonObject

        assertNull(stream["tlsSettings"]!!.jsonObject["allowInsecure"])
        val hop = stream["sockopt"]!!.jsonObject["dialerProxy"]!!.jsonPrimitive.content
        assertTrue(hop.startsWith(OperatorDocument.CHAIN_TAG_PREFIX))
        assertFalse("a hop must never be a candidate of the balancer", hop.startsWith("proxy-"))
        assertTrue(stream["sockopt"]!!.jsonObject["tcpFastOpen"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("tlshello", config.outbound(hop)["settings"]!!.jsonObject["fragment"]!!.jsonObject["packets"]!!.jsonPrimitive.content)
        assertFalse(config["outbounds"]!!.jsonArray.any { it.jsonObject["tag"]?.jsonPrimitive?.content == "frag" })
    }

    @Test
    fun `a trojan flow, removed from the core, is taken out`() {
        val document = """
            {"outbounds": [{"tag": "proxy", "protocol": "trojan",
              "settings": {"servers": [{"address": "edge.example.com", "port": 443, "password": "secret", "flow": "xtls-rprx-direct"}]},
              "streamSettings": {"network": "tcp", "security": "tls", "tlsSettings": {"serverName": "edge.example.com"}}}]}
        """.trimIndent()
        val node = parsed(document)
        val (_, config) = build(node)
        val server = config.outbound(XrayConfigFactory.nodeTag(node.id))["settings"]!!.jsonObject["servers"]!!.jsonArray[0].jsonObject
        assertNull(server["flow"])
        assertEquals("secret", server["password"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an address this app resolved replaces the written one and the name stays as SNI and Host`() {
        val document = whitelistDocument().replace(
            "\"fingerprint\": \"firefox\", \"serverName\": \"edge.example.com\"",
            "\"fingerprint\": \"firefox\"",
        ).replace("\"host\": \"edge.example.com\", ", "")
        val node = parsed(document)
        val (_, config) = build(node.copy(server = "203.0.113.9"))
        val outbound = config.outbound(XrayConfigFactory.nodeTag(node.id))
        val endpoint = outbound["settings"]!!.jsonObject["vnext"]!!.jsonArray[0].jsonObject
        assertEquals("203.0.113.9", endpoint["address"]!!.jsonPrimitive.content)
        val stream = outbound["streamSettings"]!!.jsonObject
        assertEquals("edge.example.com", stream["tlsSettings"]!!.jsonObject["serverName"]!!.jsonPrimitive.content)
        assertEquals("edge.example.com", stream["xhttpSettings"]!!.jsonObject["host"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a share link's extra reaches the core`() {
        val link = "vless://11111111-2222-4333-8444-555555555555@edge.example.com:443" +
            "?type=xhttp&security=tls&sni=edge.example.com&path=%2Fapi&mode=packet-up" +
            "&extra=%7B%22xPaddingObfsMode%22%3Atrue%2C%22uplinkHTTPMethod%22%3A%22GET%22%7D#Link"
        val node = ProxyUriParser.parse(link, "sub")!!
        val (_, config) = build(node)
        val xhttp = config.outbound(XrayConfigFactory.nodeTag(node.id))["streamSettings"]!!.jsonObject["xhttpSettings"]!!.jsonObject
        assertEquals("GET", xhttp["extra"]!!.jsonObject["uplinkHTTPMethod"]!!.jsonPrimitive.content)
        assertEquals("/api", xhttp["path"]!!.jsonPrimitive.content)
    }

    // ------------------------------------------------------------------ Routing and DNS

    @Test
    fun `the operator's rules replace the app's defaults and point at its outbounds`() {
        val (document, config) = build(parsed())
        val rules = config.rules()
        assertTrue(rules.any { it["protocol"] == JsonArray(listOf(JsonPrimitive("bittorrent"))) && it["outboundTag"]?.jsonPrimitive?.content == "direct" })
        val domains = rules.single { it.mentions("gosuslugi.ru") }
        assertEquals("direct", domains["outboundTag"]!!.jsonPrimitive.content)
        assertTrue(domains.mentions("geosite:category-ru"))
        assertFalse("the app's own Russian rules give way to the operator's", config.mentions("geoip:ru"))
        assertEquals(XrayConfigFactory.BALANCER_TAG, rules.last()["balancerTag"]!!.jsonPrimitive.content)
        assertEquals(2, document.operatorRules)
        assertEquals(0, document.operatorRulesDropped)
    }

    @Test
    fun `a category the databases do not hold is left out instead of failing the tunnel`() {
        val node = parsed(whitelistDocument("""{"domain": ["geosite:ru-blocked"], "outboundTag": "proxy"},"""))
        val (document, config) = build(node, geoCatalog = GeoCatalog(sites = setOf("category-ru"), ips = emptySet()))
        assertFalse(config.mentions("ru-blocked"))
        assertTrue(config.mentions("geosite:category-ru"))
        assertEquals(1, document.operatorRulesDropped)

        val (_, unknown) = build(node, geoCatalog = null)
        assertFalse("nothing unverified goes in", unknown.mentions("geosite:"))
        assertTrue("the plain domains of the same rule stay", unknown.mentions("domain:gosuslugi.ru"))
    }

    @Test
    fun `the operator's resolvers are used unless the user chose one`() {
        val (document, config) = build(parsed(), settings = AppSettings(enableIpv6 = false))
        val dns = config["dns"]!!.jsonObject
        assertEquals(
            "the operator's resolvers, with this app's own racing them",
            JsonArray(listOf(JsonPrimitive("192.0.2.53"), JsonPrimitive("198.51.100.53"), JsonPrimitive(AppSettings().remoteDns))),
            dns["servers"],
        )
        assertEquals("true", dns["enableParallelQuery"]!!.jsonPrimitive.content)
        assertEquals(XrayConfigFactory.DNS_IN_TAG, dns["tag"]!!.jsonPrimitive.content)
        assertEquals("UseIPv4", dns["queryStrategy"]!!.jsonPrimitive.content)
        assertTrue(document.operatorDns)
        val first = config.rules().first()
        assertEquals(JsonArray(listOf(JsonPrimitive(XrayConfigFactory.DNS_IN_TAG))), first["inboundTag"])
        assertEquals(XrayConfigFactory.BALANCER_TAG, first["balancerTag"]!!.jsonPrimitive.content)

        val (chosen, mine) = build(parsed(), dnsOverride = "https://9.9.9.9/dns-query")
        assertFalse(mine["dns"]!!.jsonObject.mentions("192.0.2.53"))
        assertFalse(chosen.operatorDns)
    }

    @Test
    fun `switched off, the outbound stays the operator's and the rest is the app's`() {
        val settings = AppSettings(operatorRouting = false, routingMode = RoutingMode.Rules)
        val node = parsed()
        val (document, config) = build(node, settings = settings)
        assertTrue(config.mentions("geoip:ru"))
        assertFalse(config["dns"]!!.jsonObject.mentions("192.0.2.53"))
        assertEquals(0, document.operatorRules)
        val xhttp = config.outbound(XrayConfigFactory.nodeTag(node.id))["streamSettings"]!!.jsonObject["xhttpSettings"]!!.jsonObject
        assertEquals(Json.parseToJsonElement(extra), xhttp["extra"])
    }

    // ------------------------------------------------------------------ Parsing around it

    @Test
    fun `a share link inside the documents array is read`() {
        val link = "vless://11111111-2222-4333-8444-555555555555@203.0.113.7:443?security=none&type=tcp#Extra"
        val nodes = ConfigDocumentParser.parse("[${whitelistDocument()}, \"$link\"]", "sub")
        assertEquals(listOf("Белые Списки №1", "Extra"), nodes.map { it.name })
    }

    @Test
    fun `the categories of a geo database are read from its entries`() {
        fun varint(value: Int): ByteArray {
            val out = ByteArrayOutputStream()
            var rest = value
            while (rest >= 0x80) {
                out.write((rest and 0x7F) or 0x80)
                rest = rest ushr 7
            }
            out.write(rest)
            return out.toByteArray()
        }
        fun field(number: Int, bytes: ByteArray) = byteArrayOf(((number shl 3) or 2).toByte()) + varint(bytes.size) + bytes
        val domain = byteArrayOf(0x08, 0x02) + field(2, "ya.ru".toByteArray())
        val bigList = (1..400).fold(ByteArray(0)) { acc, _ -> acc + field(2, domain) }
        val database = field(1, field(1, "CATEGORY-RU".toByteArray()) + bigList) +
            field(1, field(1, "google".toByteArray()) + field(2, domain))

        assertEquals(setOf("category-ru", "google"), GeoCatalog.codes(ByteArrayInputStream(database)))
        val catalog = GeoCatalog(sites = setOf("category-ru"), ips = setOf("ru"))
        assertTrue(catalog.resolves("geosite:CATEGORY-RU@cn"))
        assertTrue(catalog.resolves("geoip:!ru"))
        assertTrue(catalog.resolves("ext:geosite.dat:category-ru"))
        assertFalse(catalog.resolves("ext:custom.dat:category-ru"))
        assertFalse(catalog.resolves("geosite:ru-blocked"))
    }

    // ------------------------------------------------------------------ Hysteria2 and the final mask

    private val pin = "AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89"

    private fun encoded(json: String) = java.net.URLEncoder.encode(json, "UTF-8")

    @Test
    fun `a hysteria2 link keeps its final mask and certificate pin`() {
        val fm = encoded("""{"quicParams":{"congestion":"bbr"},"udp":[]}""")
        val link = "hysteria2://secret@hy.example.com:443?sni=hy.example.com&obfs=salamander&obfs-password=pw&pinSHA256=$pin&fm=$fm#Hy"
        val node = ProxyUriParser.parse(link, "sub")!!
        val (_, config) = build(node)
        val stream = config.outbound(XrayConfigFactory.nodeTag(node.id))["streamSettings"]!!.jsonObject
        val mask = stream["finalmask"]!!.jsonObject
        assertEquals("bbr", mask["quicParams"]!!.jsonObject["congestion"]!!.jsonPrimitive.content)
        assertEquals("an empty list in fm does not drop the link's own obfuscation", "salamander", mask["udp"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(pin, stream["tlsSettings"]!!.jsonObject["pinnedPeerCertSha256"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a vless link's final mask reaches the core`() {
        val fm = encoded("""{"tcp":[{"type":"fragment","settings":{"packets":"tlshello","length":"100-200","delay":"10-20"}}]}""")
        val link = "vless://11111111-2222-4333-8444-555555555555@edge.example.com:443?type=tcp&security=tls&sni=edge.example.com&fm=$fm#V"
        val node = ProxyUriParser.parse(link, "sub")!!
        val (_, config) = build(node)
        val mask = config.outbound(XrayConfigFactory.nodeTag(node.id))["streamSettings"]!!.jsonObject["finalmask"]!!.jsonObject
        assertEquals("fragment", mask["tcp"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
    }

    private val hysteriaDocument get() = """
        {"remarks": "Hy", "outbounds": [
          {"tag": "proxy", "protocol": "hysteria", "settings": {"version": 2, "address": "hy.example.com", "port": 443},
           "streamSettings": {"network": "hysteria", "security": "tls",
             "tlsSettings": {"serverName": "hy.example.com", "pinnedPeerCertSha256": "$pin"},
             "hysteriaSettings": {"version": 2, "auth": "secret", "udpIdleTimeout": 60},
             "finalmask": {"udp": [{"type": "salamander", "settings": {"password": "pw"}}], "quicParams": {"congestion": "bbr"}}}},
          {"tag": "direct", "protocol": "freedom"}
        ]}
    """.trimIndent()

    @Test
    fun `an Xray hysteria document runs as written`() {
        val node = parsed(hysteriaDocument)
        assertEquals(ProxySettings.Hysteria2(password = "secret", obfsType = "salamander", obfsPassword = "pw"), node.settings)
        assertNotNull(node.operator)
        assertEquals(pin, node.tls!!.pinnedCertSha256)

        val (_, config) = build(node)
        val outbound = config.outbound(XrayConfigFactory.nodeTag(node.id))
        assertEquals("hysteria", outbound["protocol"]!!.jsonPrimitive.content)
        val stream = outbound["streamSettings"]!!.jsonObject
        assertEquals(60, stream["hysteriaSettings"]!!.jsonObject["udpIdleTimeout"]!!.jsonPrimitive.content.toInt())
        assertEquals("bbr", stream["finalmask"]!!.jsonObject["quicParams"]!!.jsonObject["congestion"]!!.jsonPrimitive.content)

        val (_, resolved) = build(node.copy(server = "203.0.113.9"))
        val settings = resolved.outbound(XrayConfigFactory.nodeTag(node.id))["settings"]!!.jsonObject
        assertEquals("the flat address takes the one this app resolved", "203.0.113.9", settings["address"]!!.jsonPrimitive.content)
    }
}
