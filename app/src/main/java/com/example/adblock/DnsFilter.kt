package com.example.adblock

import java.util.Locale

/**
 * A deliberately small, bundled block list. Entries match the exact domain and its subdomains.
 * Keeping this local makes the filtering decision work without downloading or trusting a list.
 */
object DnsFilter {
    private val blockedDomains = setOf(
        "2mdn.net",
        "adnxs.com",
        "adsafeprotected.com",
        "adsrvr.org",
        "adsystem.com",
        "advertising.com",
        "amazon-adsystem.com",
        "app-measurement.com",
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "google-analytics.com",
        "moatads.com",
        "scorecardresearch.com",
        "taboola.com"
    )

    fun shouldBlock(query: ByteArray): Boolean {
        val name = queryName(query) ?: return false
        return blockedDomains.any { blocked -> name == blocked || name.endsWith(".$blocked") }
    }

    /** Returns the first question name in a DNS message, or null for a malformed query. */
    private fun queryName(message: ByteArray): String? {
        if (message.size < DNS_HEADER_SIZE) return null
        val questionCount = unsignedShort(message, 4)
        if (questionCount == 0) return null

        var offset = DNS_HEADER_SIZE
        val labels = mutableListOf<String>()
        while (offset < message.size) {
            val length = message[offset].toInt() and 0xff
            if (length == 0) {
                return labels.joinToString(".").lowercase(Locale.US)
            }
            // Compression pointers are not valid in the QNAME of a normal query. Do not follow
            // them here; rejecting them avoids parser loops and we simply forward the query.
            if (length and 0xc0 != 0 || length > 63 || offset + 1 + length > message.size) return null
            labels += message.copyOfRange(offset + 1, offset + 1 + length)
                .toString(Charsets.US_ASCII)
            offset += length + 1
        }
        return null
    }

    fun nxdomain(query: ByteArray): ByteArray {
        if (query.size < DNS_HEADER_SIZE) return query
        val response = query.copyOf()
        // QR=1, RD copied from request, RA=1, RCODE=NXDOMAIN.
        val requestFlags = unsignedShort(query, 2)
        val flags = 0x8000 or (requestFlags and 0x0100) or 0x0080 or 0x0003
        response[2] = (flags ushr 8).toByte()
        response[3] = flags.toByte()
        response[6] = 0
        response[7] = 0
        response[8] = 0
        response[9] = 0
        response[10] = 0
        response[11] = 0
        return response
    }

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private const val DNS_HEADER_SIZE = 12
}
