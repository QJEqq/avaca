package com.mydrop.vpn.core.xray

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream

/**
 * Which categories the geo databases on disk actually hold, lower-cased.
 *
 * Needed the moment a document carries references this app did not write. Xray resolves every
 * `geosite:`/`geoip:` while parsing and refuses the whole configuration over one it cannot find
 * (`common/geodata/geodat_loader.go`), so an operator rule naming a category from a different
 * build of the lists — runetfreedom's `ru-blocked` against v2fly's `dlc.dat`, say — is not one
 * rule that fails to match, it is a tunnel that will not start. Checking the name against the file
 * is the only way to know beforehand.
 */
data class GeoCatalog(val sites: Set<String>, val ips: Set<String>) {

    /** Whether a `geosite:`/`geoip:` reference can be resolved here; anything else is not ours to judge. */
    fun resolves(entry: String): Boolean {
        val value = entry.trim()
        return when {
            value.startsWith("geosite:", ignoreCase = true) ->
                value.substringAfter(':').substringBefore('@').trim().lowercase() in sites
            value.startsWith("geoip:", ignoreCase = true) ->
                value.substringAfter(':').removePrefix("!").trim().lowercase() in ips
            // `ext:<file>:<code>` names a file by name; only the two this app keeps can answer.
            value.startsWith("ext:", ignoreCase = true) -> {
                val parts = value.split(':')
                parts.size >= 3 && when (parts[1].trim().lowercase()) {
                    "geosite.dat" -> parts[2].substringBefore('@').trim().lowercase() in sites
                    "geoip.dat" -> parts[2].removePrefix("!").trim().lowercase() in ips
                    else -> false
                }
            }
            else -> true
        }
    }

    companion object {
        /**
         * The codes in one `.dat` file: a protobuf `GeoSiteList` or `GeoIPList`, which share a shape
         * — a repeated message at field 1, whose own field 1 is the code. Read as a stream, taking
         * the code from the head of each entry and skipping the rest, because `geoip.dat` runs to
         * tens of megabytes and only the names are wanted.
         */
        fun codes(input: InputStream): Set<String> {
            val stream = BufferedInputStream(input, 64 * 1024)
            val codes = HashSet<String>()
            while (true) {
                val key = readVarint(stream, atStart = true) ?: break
                if (key and 7L != 2L) throw IllegalArgumentException("not a geo database")
                var remaining = readVarint(stream) ?: throw EOFException()
                if (remaining > 0) {
                    val inner = readVarint(stream) ?: throw EOFException()
                    remaining -= varintSize(inner)
                    if (inner == CODE_KEY) {
                        val size = readVarint(stream) ?: throw EOFException()
                        remaining -= varintSize(size) + size
                        if (size !in 0..MAX_CODE_BYTES) throw IllegalArgumentException("not a geo database")
                        val bytes = ByteArray(size.toInt())
                        readFully(stream, bytes)
                        codes += String(bytes, Charsets.UTF_8).lowercase()
                    }
                }
                skipFully(stream, remaining)
            }
            return codes
        }

        /** Field 1, length-delimited. */
        private const val CODE_KEY = 0x0AL
        private const val MAX_CODE_BYTES = 256L

        private fun readVarint(stream: InputStream, atStart: Boolean = false): Long? {
            var result = 0L
            var shift = 0
            while (shift < 64) {
                val byte = stream.read()
                if (byte < 0) {
                    if (atStart && shift == 0) return null
                    throw EOFException()
                }
                result = result or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
            }
            throw IllegalArgumentException("varint too long")
        }

        private fun varintSize(value: Long): Long {
            var size = 1L
            var rest = value ushr 7
            while (rest != 0L) {
                size++
                rest = rest ushr 7
            }
            return size
        }

        private fun readFully(stream: InputStream, into: ByteArray) {
            var read = 0
            while (read < into.size) {
                val n = stream.read(into, read, into.size - read)
                if (n < 0) throw EOFException()
                read += n
            }
        }

        private fun skipFully(stream: InputStream, count: Long) {
            if (count < 0) throw IllegalArgumentException("not a geo database")
            var left = count
            while (left > 0) {
                val skipped = stream.skip(left)
                if (skipped > 0) {
                    left -= skipped
                } else {
                    if (stream.read() < 0) throw EOFException()
                    left--
                }
            }
        }
    }
}
