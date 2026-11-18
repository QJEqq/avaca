package com.mydrop.vpn.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleSelectionTest {

    private val alive = setOf("a", "b", "c")

    @Test
    fun `ids that vanished leave the list`() {
        val pruned = StaleSelection.prune(alive = alive, failover = setOf("a", "gone"))!!
        assertEquals(setOf("a"), pruned.failover)
        assertEquals(1, pruned.lostFailover)
        assertFalse(pruned.failoverEmptied)
    }

    @Test
    fun `an unchanged refresh reports nothing`() {
        assertNull(StaleSelection.prune(alive, setOf("a", "b")))
        assertNull(StaleSelection.prune(alive, emptySet()))
    }

    @Test
    fun `an empty profile prunes nothing at all`() {
        assertNull(StaleSelection.prune(emptySet(), setOf("a", "gone")))
    }

    @Test
    fun `a list that lost its last server was emptied`() {
        assertTrue(StaleSelection.prune(alive, setOf("gone"))!!.failoverEmptied)
    }
}
