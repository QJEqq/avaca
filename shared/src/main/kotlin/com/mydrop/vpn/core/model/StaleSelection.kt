package com.mydrop.vpn.core.model

/**
 * Failover choices that point at servers no longer in the profile, taken out after a refresh.
 *
 * An empty profile prunes nothing: that is a refresh that failed, not a provider that dropped every
 * server, and emptying the list over it would throw away a choice that is still good.
 */
object StaleSelection {

    fun prune(alive: Set<String>, failover: Set<String>): Pruned? {
        if (alive.isEmpty()) return null
        val kept = failover intersect alive
        val lost = failover.size - kept.size
        if (lost == 0) return null
        return Pruned(failover = kept, lostFailover = lost)
    }

    data class Pruned(val failover: Set<String>, val lostFailover: Int) {
        /** The list lost its last server here — as opposed to one that was empty already. */
        val failoverEmptied: Boolean get() = lostFailover > 0 && failover.isEmpty()
    }
}
