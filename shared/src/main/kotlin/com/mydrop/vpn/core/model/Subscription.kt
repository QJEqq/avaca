package com.mydrop.vpn.core.model

import kotlinx.serialization.Serializable

/**
 * Traffic and expiry reported by the provider through the `subscription-userinfo` response
 * header, which is the de-facto standard Happ/v2rayN/Clash clients all read:
 *
 * `subscription-userinfo: upload=0; download=1234; total=107374182400; expire=1735689600`
 *
 * Every field is optional because providers implement arbitrary subsets of it.
 */
@Serializable
data class SubscriptionUserInfo(
    val uploadBytes: Long? = null,
    val downloadBytes: Long? = null,
    val totalBytes: Long? = null,
    val expiresAtEpochSeconds: Long? = null,
) {
    val usedBytes: Long?
        get() = when {
            uploadBytes == null && downloadBytes == null -> null
            else -> (uploadBytes ?: 0L) + (downloadBytes ?: 0L)
        }

    /** Fraction of the plan consumed, or null when the provider does not publish a quota. */
    val usedFraction: Float?
        get() {
            val used = usedBytes ?: return null
            val total = totalBytes?.takeIf { it > 0 } ?: return null
            return (used.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }

    val remainingBytes: Long?
        get() {
            val used = usedBytes ?: return null
            val total = totalBytes?.takeIf { it > 0 } ?: return null
            return (total - used).coerceAtLeast(0L)
        }
}

/**
 * Settings a provider sets for its servers through subscription headers, under the names Happ uses
 * for them — so one set of headers in a panel (Remnawave's custom response headers) configures Happ
 * and Avaca alike. Null means "not said": the user's own setting stays.
 *
 * Applied only to the provider's own servers, on top of the user's settings, and never written into
 * them: switching to another provider's server, or to one added by hand, brings the user's choices
 * back untouched.
 */
@Serializable
data class ProviderSettings(
    val fragmentEnabled: Boolean? = null,
    val fragmentPackets: String? = null,
    val fragmentLength: String? = null,
    val fragmentInterval: String? = null,
    val muxEnabled: Boolean? = null,
    val muxConcurrency: Int? = null,
    val muxXudpConcurrency: Int? = null,
    val pingMode: PingMode? = null,
    val resolveEnabled: Boolean? = null,
    val resolveDohUrl: String? = null,
    val updateIntervalHours: Int? = null,
) {
    val isEmpty: Boolean get() = this == ProviderSettings()

    companion object {
        /** Parsed from response headers, lower-cased names. Happ's rule: "true" or "1" is on. */
        fun fromHeaders(headers: Map<String, String>): ProviderSettings? {
            fun flag(name: String) = headers[name]?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { it.equals("true", ignoreCase = true) || it == "1" }
            fun text(name: String) = headers[name]?.trim()?.takeIf { it.isNotEmpty() && it.length <= 64 }
            fun number(name: String, range: IntRange) = headers[name]?.trim()?.toIntOrNull()?.takeIf { it in range }
            val doh = text("server-address-resolve-dns-domain")
                ?.let { if (it.startsWith("https://")) it else "https://$it" }
                ?.let { url ->
                    // A bootstrap address stands in for the DoH host, so the DoH server needs no DNS
                    // of its own; public resolvers' certificates cover their addresses.
                    val ip = text("server-address-resolve-dns-ip")
                    if (ip != null && ip.all { it.isDigit() || it == '.' }) {
                        url.replaceFirst(Regex("^https://[^/]+"), "https://$ip")
                    } else {
                        url
                    }
                }
            val settings = ProviderSettings(
                fragmentEnabled = flag("fragmentation-enable"),
                fragmentPackets = text("fragmentation-packets"),
                fragmentLength = text("fragmentation-length"),
                fragmentInterval = text("fragmentation-interval"),
                muxEnabled = flag("mux-enable"),
                muxConcurrency = number("mux-tcp-connections", 1..1024),
                muxXudpConcurrency = number("mux-xudp-connections", 1..1024),
                pingMode = when (headers["ping-type"]?.trim()?.lowercase()) {
                    "proxy", "proxy-head" -> PingMode.Proxy
                    "tcp", "icmp" -> PingMode.Tcp
                    else -> null
                },
                resolveEnabled = flag("server-address-resolve-enable"),
                resolveDohUrl = doh,
                updateIntervalHours = number("profile-update-interval", 1..24 * 30),
            )
            return settings.takeUnless { it.isEmpty }
        }
    }
}

/** The user's settings with what [provider] set laid over them; the user's stay where it said nothing. */
fun AppSettings.withProvider(provider: ProviderSettings?): AppSettings = if (provider == null) this else copy(
    fragmentEnabled = provider.fragmentEnabled ?: fragmentEnabled,
    fragmentPackets = provider.fragmentPackets ?: fragmentPackets,
    fragmentLength = provider.fragmentLength ?: fragmentLength,
    fragmentInterval = provider.fragmentInterval ?: fragmentInterval,
    muxEnabled = provider.muxEnabled ?: muxEnabled,
    muxConcurrency = provider.muxConcurrency ?: muxConcurrency,
    muxXudpConcurrency = provider.muxXudpConcurrency ?: muxXudpConcurrency,
    pingMode = provider.pingMode ?: pingMode,
    resolveServerAddress = provider.resolveEnabled ?: resolveServerAddress,
    resolveDohUrl = provider.resolveDohUrl ?: resolveDohUrl,
)

@Serializable
data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val nodeIds: List<String> = emptyList(),
    val userInfo: SubscriptionUserInfo? = null,
    val lastUpdatedEpochMillis: Long? = null,
    val lastError: String? = null,
    val enabled: Boolean = true,
    /** Provider's own name for the bundle, from the `profile-title` header when present. */
    val remoteTitle: String? = null,
    /** Provider support/renewal page, from the `profile-web-page-url` header. */
    val webPageUrl: String? = null,
    /**
     * The provider's note for this subscription, from the `announce` header — what Happ shows under
     * the subscription's name (Remnawave fills it from its subscription settings). Replaced on every
     * successful refresh, so a note the provider takes down disappears here too.
     */
    val announce: String? = null,
    /** Where to ask for help, from the `support-url` header (a site or a Telegram link). */
    val supportUrl: String? = null,
    /** What the provider set for its own servers through the subscription's headers. */
    val provider: ProviderSettings? = null,
    /** Where to fetch the subscription when its own address does not answer (`fallback-url`). */
    val fallbackUrl: String? = null,

    /**
     * Client to claim to be when fetching this one, overriding the app's own agent.
     *
     * Panels choose the format of their answer from the User-Agent, and not all of them know
     * about this app: the same link can return share links to one client, Clash YAML to another
     * and a refusal to a third. When a provider only serves a client we are not, saying so is the
     * difference between a working subscription and an unexplained empty list.
     */
    val userAgentOverride: String? = null,

    /**
     * Extra request headers, `name: value` per entry. Panels behind an authenticating gateway ask
     * for a token this way, and there is no other place to put one.
     */
    val headers: Map<String, String> = emptyMap(),
)

/** Outcome of a refresh, so the UI can distinguish "no change" from "failed" from "updated". */
sealed interface SubscriptionUpdate {
    data class Success(
        val subscription: Subscription,
        val nodes: List<ProxyNode>,
        val addedCount: Int,
        val removedCount: Int,
    ) : SubscriptionUpdate

    data class Failure(val subscriptionId: String, val message: String) : SubscriptionUpdate
}
