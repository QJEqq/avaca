package com.mydrop.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionLogTest {

    @Test
    fun `an access line through a server is read with its route`() {
        val entry = ConnectionLog.parse("from tcp:172.19.0.2:51234 accepted tcp:www.example.com:443 [tun-in >> proxy-abc123]", 1L)!!
        assertEquals("www.example.com", entry.host)
        assertEquals(443, entry.port)
        assertEquals("tcp", entry.network)
        assertEquals("tun-in", entry.inbound)
        assertEquals(ConnectionLog.Route.Proxy, entry.route)
        assertEquals("abc123", entry.nodeId)
        assertTrue(entry.accepted)
    }

    @Test
    fun `direct, blocked, dns and rejected lines`() {
        assertEquals(ConnectionLog.Route.Direct, ConnectionLog.parse("from 172.19.0.2:1 accepted udp:[2001:db8::1]:443 [tun-in -> direct]", 1L)!!.route)
        assertEquals("2001:db8::1", ConnectionLog.parse("from 172.19.0.2:1 accepted udp:[2001:db8::1]:443 [tun-in -> direct]", 1L)!!.host)
        assertEquals(ConnectionLog.Route.Block, ConnectionLog.parse("from 172.19.0.2:1 accepted tcp:ads.example:80 [tun-in -> block]", 1L)!!.route)
        val dns = ConnectionLog.parse("from 172.19.0.2:1 accepted udp:172.19.0.1:53 [tun-in -> dns-out]", 1L)!!
        assertEquals(ConnectionLog.Route.Dns, dns.route)
        val rejected = ConnectionLog.parse("from 172.19.0.2:1 rejected tcp:x.example:443 [tun-in -> proxy-n1] connection refused email: a@b", 1L)!!
        assertFalse(rejected.accepted)
        assertEquals("connection refused", rejected.reason)
    }

    @Test
    fun `other lines are not taken and the list is capped, newest first`() {
        val log = ConnectionLog(capacity = 3)
        assertFalse(log.offer("app/dispatcher: default route for tcp:example.com:443"))
        assertNull(ConnectionLog.parse("[Info] something", 1L))
        repeat(5) { i -> log.offer("from 172.19.0.2:1 accepted tcp:h$i.example:443 [tun-in -> direct]", i.toLong()) }
        assertEquals(listOf("h4.example", "h3.example", "h2.example"), log.entries.value.map { it.host })
        log.clear()
        assertTrue(log.entries.value.isEmpty())
    }
}
