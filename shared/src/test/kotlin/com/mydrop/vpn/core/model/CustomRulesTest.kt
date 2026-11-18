package com.mydrop.vpn.core.model

import com.mydrop.vpn.core.xray.GeoCatalog
import com.mydrop.vpn.core.xray.XrayConfigFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomRulesTest {

    @Test
    fun `what people type becomes what the core reads`() {
        assertEquals("domain:youtube.com", CustomRules.normalize("YouTube.com"))
        assertEquals("domain:www.youtube.com", CustomRules.normalize("https://www.youtube.com/watch?v=x"))
        assertEquals("domain:example.com", CustomRules.normalize("example.com:8443"))
        assertEquals("geosite:category-ru", CustomRules.normalize("geosite:category-ru"))
        assertEquals("full:api.example.com", CustomRules.normalize("FULL:api.example.com"))
        assertEquals("1.2.3.4", CustomRules.normalize("1.2.3.4"))
        assertEquals("10.0.0.0/8", CustomRules.normalize("10.0.0.0/8"))
        assertEquals("2001:db8::/32", CustomRules.normalize("2001:db8::/32"))
        assertNull(CustomRules.normalize("10.0.0.0/40"))
        assertNull(CustomRules.normalize("not a site"))
        assertNull(CustomRules.normalize("geosite:"))
        assertTrue(CustomRules.isAddress("geoip:ru"))
        assertTrue(CustomRules.isAddress("10.0.0.0/8"))
        assertFalse(CustomRules.isAddress("domain:example.com"))
    }

    @Test
    fun `an entry lives in one list at a time`() {
        val rules = CustomRules().adding(CustomRules.Action.Direct, "domain:a.com").adding(CustomRules.Action.Proxy, "domain:a.com")
        assertEquals(listOf("domain:a.com"), rules.proxy)
        assertTrue(rules.direct.isEmpty())
        assertTrue(rules.removing(CustomRules.Action.Proxy, "domain:a.com").isEmpty)
    }

    @Test
    fun `the user's rules come before the defaults, blocked first, unknown categories left out`() {
        val node = ProxyNode(
            id = "n1",
            name = "Server",
            server = "203.0.113.1",
            port = 443,
            settings = ProxySettings.Vless(uuid = "11111111-2222-4333-8444-555555555555"),
            tls = TlsOptions(enabled = true, serverName = "edge.example.com"),
        )
        val settings = AppSettings(
            routingMode = RoutingMode.Rules,
            customRules = CustomRules(
                proxy = listOf("domain:vk.com", "geosite:not-on-disk"),
                direct = listOf("10.20.0.0/16"),
                block = listOf("domain:ads.example"),
            ),
        )
        val document = XrayConfigFactory.build(
            nodes = listOf(node),
            selected = node,
            settings = settings,
            geoAvailable = true,
            geoCatalog = GeoCatalog(sites = setOf("category-ru"), ips = setOf("ru")),
        )
        val rules = Json.parseToJsonElement(document.json).jsonObject["routing"]!!.jsonObject["rules"]!!.jsonArray.map { it.jsonObject }
        val block = rules.indexOfFirst { it.toString().contains("ads.example") }
        val proxy = rules.indexOfFirst { it.toString().contains("domain:vk.com") }
        val direct = rules.indexOfFirst { it.toString().contains("10.20.0.0/16") }
        val ruDefault = rules.indexOfFirst { it.toString().contains("geosite:category-ru") }
        assertTrue(block in 0 until proxy && proxy < direct && direct < ruDefault)
        assertEquals("block", rules[block]["outboundTag"]!!.jsonPrimitive.content)
        assertEquals(XrayConfigFactory.BALANCER_TAG, rules[proxy]["balancerTag"]!!.jsonPrimitive.content)
        assertFalse(document.json.contains("not-on-disk"))
    }
}
