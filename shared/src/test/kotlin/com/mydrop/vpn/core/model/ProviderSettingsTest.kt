package com.mydrop.vpn.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSettingsTest {

    @Test
    fun `happ headers become provider settings`() {
        val provider = ProviderSettings.fromHeaders(
            mapOf(
                "fragmentation-enable" to "1",
                "fragmentation-packets" to "tlshello",
                "fragmentation-length" to "50-100",
                "fragmentation-interval" to "10-20",
                "mux-enable" to "true",
                "mux-tcp-connections" to "8",
                "ping-type" to "proxy-head",
                "server-address-resolve-enable" to "1",
                "server-address-resolve-dns-domain" to "https://cloudflare-dns.com/dns-query",
                "server-address-resolve-dns-ip" to "1.1.1.1",
                "profile-update-interval" to "6",
            ),
        )!!
        assertEquals(true, provider.fragmentEnabled)
        assertEquals("50-100", provider.fragmentLength)
        assertEquals(true, provider.muxEnabled)
        assertEquals(8, provider.muxConcurrency)
        assertEquals(PingMode.Proxy, provider.pingMode)
        assertEquals(true, provider.resolveEnabled)
        // The bootstrap address stands in for the DoH host.
        assertEquals("https://1.1.1.1/dns-query", provider.resolveDohUrl)
        assertEquals(6, provider.updateIntervalHours)
    }

    @Test
    fun `anything but true or 1 switches a flag off, and nothing said is null`() {
        val provider = ProviderSettings.fromHeaders(mapOf("fragmentation-enable" to "0", "mux-enable" to "false"))!!
        assertEquals(false, provider.fragmentEnabled)
        assertEquals(false, provider.muxEnabled)
        assertNull(provider.pingMode)
        assertNull(ProviderSettings.fromHeaders(emptyMap()))
        assertNull(ProviderSettings.fromHeaders(mapOf("mux-tcp-connections" to "lots")))
    }

    @Test
    fun `the provider's settings lie over the user's only where it said something`() {
        val user = AppSettings(fragmentEnabled = false, muxEnabled = true, muxConcurrency = 4, pingMode = PingMode.Tcp)
        val effective = user.withProvider(ProviderSettings(fragmentEnabled = true, fragmentLength = "1-5"))
        assertTrue(effective.fragmentEnabled)
        assertEquals("1-5", effective.fragmentLength)
        assertTrue(effective.muxEnabled)
        assertEquals(4, effective.muxConcurrency)
        assertEquals(PingMode.Tcp, effective.pingMode)
        assertEquals(user, user.withProvider(null))
        assertFalse(user.withProvider(ProviderSettings(muxEnabled = false)).muxEnabled)
    }
}
