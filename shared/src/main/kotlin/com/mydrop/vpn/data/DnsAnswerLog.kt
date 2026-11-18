package com.mydrop.vpn.data

/**
 * How each resolver the core asks has been answering, read from the core's own `got answer:` lines,
 * the last [capacity] of them.
 *
 * For telling the person running the servers which of their DNS servers is silent through which
 * node. That is how it went once: a template's resolvers, allow-listed by address, never answered
 * nodes added after the list was written, and every site waited eight seconds for nothing while
 * nothing on screen said which resolver it was waiting on. In memory only; fed while developer mode
 * is on, like ConnectionLog.
 */
class DnsAnswerLog(private val capacity: Int = 400) {

    data class Answer(
        val atMillis: Long,
        val server: String,
        val domain: String,
        /** It came back with addresses, or with an empty answer quickly: the resolver is there. */
        val responded: Boolean,
        val millis: Long?,
    )

    data class ServerStat(val server: String, val responded: Int, val silent: Int, val typicalMillis: Long?)

    private val answers = ArrayDeque<Answer>()

    /** Takes [line] if it is a resolver's answer; returns whether it was one. */
    fun offer(line: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val answer = parse(line, nowMillis) ?: return false
        synchronized(answers) {
            answers.addLast(answer)
            while (answers.size > capacity) answers.removeFirst()
        }
        return true
    }

    fun clear() = synchronized(answers) { answers.clear() }

    /** Per resolver over the last [windowMillis], the busiest first. */
    fun stats(windowMillis: Long, nowMillis: Long = System.currentTimeMillis()): List<ServerStat> {
        val recent = synchronized(answers) { answers.filter { nowMillis - it.atMillis <= windowMillis } }
        return recent.groupBy { it.server }
            .map { (server, list) ->
                val heard = list.filter { it.responded }
                val times = heard.mapNotNull { it.millis }.sorted()
                ServerStat(
                    server = server,
                    responded = heard.size,
                    silent = list.size - heard.size,
                    typicalMillis = times.getOrNull(times.size / 2),
                )
            }
            .sortedByDescending { it.responded + it.silent }
    }

    companion object {
        /** `<server> got answer: <domain> -> [<ips>] <elapsed> <error>`, as `common/log/dns.go` writes it. */
        private val LINE = Regex("""^(\S+) got answer: (\S+) -> \[([^\]]*)\](?: (\S+))?(?: <.*>)?$""")

        /**
         * Longer than this with nothing to show is the resolver not answering at all: the core gives
         * each one four seconds and reports the timeout as an empty answer.
         */
        private const val SILENT_AFTER_MILLIS = 3_500L

        fun parse(line: String, nowMillis: Long): Answer? {
            val match = LINE.matchEntire(line.trim()) ?: return null
            val (server, domain, addresses, elapsed) = match.destructured
            val millis = goDurationMillis(elapsed)
            return Answer(
                atMillis = nowMillis,
                server = server,
                domain = domain.trimEnd('.'),
                responded = addresses.isNotBlank() || (millis != null && millis < SILENT_AFTER_MILLIS),
                millis = millis,
            )
        }

        /** How a resolver is named on screen: `UDP:1.2.3.4:53` → `1.2.3.4`, `DOH//1.1.1.1` → `1.1.1.1 (DoH)`. */
        fun displayName(server: String): String {
            val kind = server.substringBefore("//", missingDelimiterValue = "")
            return when {
                server.startsWith("UDP:", ignoreCase = true) -> server.removePrefix("UDP:").removeSuffix(":53")
                kind.isNotEmpty() -> server.substringAfter("//").removeSuffix(":53") + " (" + when (kind.uppercase()) {
                    "DOH" -> "DoH"
                    "DOHL" -> "DoH, напрямую"
                    "TCP" -> "TCP"
                    "TCPL" -> "TCP, напрямую"
                    else -> kind
                } + ")"
                else -> server
            }
        }

        /** Go's `time.Duration.String()`: `4.000671717s`, `83.512ms`, `512µs`, `1m2.5s`. */
        internal fun goDurationMillis(text: String): Long? {
            if (text.isBlank()) return null
            var total = 0.0
            var consumed = 0
            for (part in DURATION_PART.findAll(text)) {
                val value = part.groupValues[1].toDoubleOrNull() ?: return null
                total += value * when (part.groupValues[2]) {
                    "h" -> 3_600_000.0
                    "m" -> 60_000.0
                    "s" -> 1_000.0
                    "ms" -> 1.0
                    "µs", "us" -> 0.001
                    "ns" -> 0.000_001
                    else -> return null
                }
                consumed += part.value.length
            }
            return if (consumed == text.length) total.toLong() else null
        }

        private val DURATION_PART = Regex("""([0-9]*\.?[0-9]+)(h|ms|m|s|µs|us|ns)""")
    }
}
