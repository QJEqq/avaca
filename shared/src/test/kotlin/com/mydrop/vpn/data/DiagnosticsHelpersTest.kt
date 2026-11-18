package com.mydrop.vpn.data

import com.mydrop.vpn.core.model.AppSettings
import com.mydrop.vpn.core.model.OperatorConfig
import com.mydrop.vpn.core.model.ProxyNode
import com.mydrop.vpn.core.model.ProxySettings
import com.mydrop.vpn.core.model.TlsOptions
import com.mydrop.vpn.core.xray.XrayConfigFactory
import java.io.EOFException
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsHelpersTest {

    // ------------------------------------------------------------------ DNS answers

    @Test
    fun `a resolver that timed out is told from one that answered`() {
        val silent = DnsAnswerLog.parse("UDP:192.0.2.53:53 got answer: example.com. -> [] 4.000671717s <app/dns: record not found>", 1L)!!
        assertEquals("UDP:192.0.2.53:53", silent.server)
        assertEquals("example.com", silent.domain)
        assertFalse(silent.responded)
        assertEquals(4000L, silent.millis)

        val answered = DnsAnswerLog.parse("DOH//1.1.1.1 got answer: example.com. -> [93.184.216.34] 83.512ms", 1L)!!
        assertTrue(answered.responded)
        assertEquals(83L, answered.millis)

        val empty = DnsAnswerLog.parse("UDP:192.0.2.53:53 got answer: nothing.example. -> [] 41ms <app/dns: record not found>", 1L)!!
        assertTrue("a quick empty answer is still an answer", empty.responded)

        assertNull(DnsAnswerLog.parse("DOH//1.1.1.1 cache HIT: example.com -> [93.184.216.34]", 1L))
        assertNull(DnsAnswerLog.parse("[Info] app/dns: querying", 1L))
    }

    @Test
    fun `stats name the silent resolver`() {
        val log = DnsAnswerLog()
        repeat(3) { log.offer("UDP:192.0.2.53:53 got answer: a.example. -> [] 4.0s <app/dns: record not found>", 1_000L) }
        repeat(5) { log.offer("DOH//1.1.1.1 got answer: a.example. -> [203.0.113.1] 90ms", 1_000L) }
        log.offer("DOH//1.1.1.1 got answer: old.example. -> [203.0.113.1] 90ms", 0L)
        val stats = log.stats(windowMillis = 500L, nowMillis = 1_200L)
        assertEquals(listOf("DOH//1.1.1.1", "UDP:192.0.2.53:53"), stats.map { it.server })
        assertEquals(5, stats[0].responded)
        assertEquals(0, stats[1].responded)
        assertEquals(3, stats[1].silent)
        assertEquals("192.0.2.53", DnsAnswerLog.displayName(stats[1].server))
        assertEquals("1.1.1.1 (DoH)", DnsAnswerLog.displayName(stats[0].server))
    }

    @Test
    fun `go durations`() {
        assertEquals(62_500L, DnsAnswerLog.goDurationMillis("1m2.5s"))
        assertEquals(0L, DnsAnswerLog.goDurationMillis("512µs"))
        assertNull(DnsAnswerLog.goDurationMillis("soon"))
    }

    // ------------------------------------------------------------------ Handshake failures

    @Test
    fun `a silent server, a reset and a close are told apart`() {
        assertEquals(ServerDiagnostics.HandshakeFailure.Silent, ServerDiagnostics.classify(SocketTimeoutException("Read timed out")))
        assertEquals(ServerDiagnostics.HandshakeFailure.Reset, ServerDiagnostics.classify(SocketException("Connection reset")))
        assertEquals(ServerDiagnostics.HandshakeFailure.Closed, ServerDiagnostics.classify(SSLHandshakeException("Connection closed by peer")))
        assertEquals(ServerDiagnostics.HandshakeFailure.Closed, ServerDiagnostics.classify(SSLHandshakeException("Remote host terminated the handshake").apply { initCause(EOFException()) }))
        assertEquals(ServerDiagnostics.HandshakeFailure.Refused, ServerDiagnostics.classify(SSLHandshakeException("Chain validation failed")))
    }

    // ------------------------------------------------------------------ Fragmentation

    private fun node(finalMask: String? = null, operator: OperatorConfig? = null) = ProxyNode(
        id = "n1",
        name = "Server",
        server = "203.0.113.1",
        port = 443,
        settings = ProxySettings.Vless(uuid = "11111111-2222-4333-8444-555555555555"),
        tls = TlsOptions(enabled = true, serverName = "edge.example.com"),
        finalMask = finalMask,
        operator = operator,
    )

    @Test
    fun `fragmentation is found wherever the core would get it`() {
        val off = AppSettings(fragmentEnabled = false)
        assertFalse(XrayConfigFactory.fragmentsHandshake(node(), off))
        assertTrue("this app's own setting", XrayConfigFactory.fragmentsHandshake(node(), AppSettings(fragmentEnabled = true)))
        assertTrue(
            "the link's fm",
            XrayConfigFactory.fragmentsHandshake(node(finalMask = """{"tcp":[{"type":"fragment","settings":{"packets":"tlshello"}}]}"""), off),
        )
        assertTrue(
            "the operator's finalmask",
            XrayConfigFactory.fragmentsHandshake(
                node(operator = OperatorConfig(outbound = """{"protocol":"vless","streamSettings":{"finalmask":{"tcp":[{"type":"fragment"}]}}}""")),
                off,
            ),
        )
        assertTrue(
            "a fragmenting hop",
            XrayConfigFactory.fragmentsHandshake(
                node(operator = OperatorConfig(outbound = "{}", chain = listOf("""{"protocol":"freedom","settings":{"fragment":{"packets":"tlshello"}}}"""))),
                off,
            ),
        )
        assertFalse(
            "a UDP-only mask does not touch the TLS hello",
            XrayConfigFactory.fragmentsHandshake(node(finalMask = """{"udp":[{"type":"salamander"}]}"""), off),
        )
    }
}
