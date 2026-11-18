package com.mydrop.vpn.core.model

import com.mydrop.vpn.core.net.isNumericAddress
import kotlinx.serialization.Serializable

/**
 * Where the user sends particular sites and addresses, whatever the routing mode and whatever an
 * operator's document says: three lists, the way Happ keeps them.
 *
 * Each entry is written the way the core reads it — `domain:example.com`, `geosite:category-ru`,
 * `10.0.0.0/8`, `geoip:ru` — and [normalize] turns what people actually type or paste into that.
 */
@Serializable
data class CustomRules(
    val proxy: List<String> = emptyList(),
    val direct: List<String> = emptyList(),
    val block: List<String> = emptyList(),
) {
    enum class Action { Proxy, Direct, Block }

    fun of(action: Action): List<String> = when (action) {
        Action.Proxy -> proxy
        Action.Direct -> direct
        Action.Block -> block
    }

    /** With [entry] added to [action]'s list, and taken out of the other two: one place per entry. */
    fun adding(action: Action, entry: String): CustomRules {
        val others = copy(
            proxy = proxy - entry,
            direct = direct - entry,
            block = block - entry,
        )
        return when (action) {
            Action.Proxy -> others.copy(proxy = others.proxy + entry)
            Action.Direct -> others.copy(direct = others.direct + entry)
            Action.Block -> others.copy(block = others.block + entry)
        }
    }

    fun removing(action: Action, entry: String): CustomRules = when (action) {
        Action.Proxy -> copy(proxy = proxy - entry)
        Action.Direct -> copy(direct = direct - entry)
        Action.Block -> copy(block = block - entry)
    }

    val isEmpty: Boolean get() = proxy.isEmpty() && direct.isEmpty() && block.isEmpty()

    val size: Int get() = proxy.size + direct.size + block.size

    companion object {
        /**
         * What was typed, as the core reads it; null when it is not a site, an address or a
         * category. A pasted link becomes its host. A bare name becomes `domain:` — the name and
         * everything under it — because a bare word is a substring match to the core, and
         * `vk.com` would then also catch `notvk.com.example`.
         */
        fun normalize(raw: String): String? {
            var value = raw.trim().trimEnd('/')
            if (value.isEmpty() || value.any { it.isWhitespace() }) return null

            val prefix = value.substringBefore(':', missingDelimiterValue = "").lowercase()
            if (prefix in KEPT_PREFIXES) {
                val rest = value.substringAfter(':').trim()
                return if (rest.isEmpty()) null else "$prefix:$rest"
            }

            // A subnet, before anything below reads the slash as the start of a path.
            val slash = value.indexOf('/')
            if (slash > 0 && "://" !in value) {
                val address = value.substring(0, slash).trim('[', ']')
                val bits = value.substring(slash + 1).toIntOrNull()
                if (isNumericAddress(address)) {
                    val widest = if (':' in address) 128 else 32
                    return if (bits != null && bits in 0..widest) "$address/$bits" else null
                }
            }

            if ("://" in value) value = value.substringAfter("://")
            value = value.substringBefore('/').substringBefore('?').substringBefore('#')
            value = value.substringAfter('@')

            return when {
                isNumericAddress(value) -> value
                value.startsWith("[") && value.endsWith("]") && isNumericAddress(value.trim('[', ']')) -> value.trim('[', ']')
                else -> {
                    val host = value.substringBeforeLast(':').takeIf { value.count { it == ':' } == 1 } ?: value
                    host.lowercase().trimEnd('.').takeIf { HOST.matches(it) }?.let { "domain:$it" }
                }
            }
        }

        /** Whether the core matches [entry] against addresses rather than names. */
        fun isAddress(entry: String): Boolean {
            val value = entry.trim().lowercase()
            if (value.startsWith("geoip:")) return true
            if (value.startsWith("ext:")) return value.split(':').getOrNull(1)?.contains("ip") == true
            val bare = value.substringBefore('/')
            return isNumericAddress(bare)
        }

        private val KEPT_PREFIXES = setOf("domain", "full", "keyword", "regexp", "geosite", "geoip", "ext")
        private val HOST = Regex("""^(?=.{1,253}$)([a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?\.)*[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$""")
    }
}
