package com.example.adblock

/** Minimal IPv4/IPv6 UDP packet reader/writer for DNS packets carried by the TUN interface. */
internal object DnsPacket {
    private const val UDP_PROTOCOL = 17

    data class Query(
        val version: Int,
        val sourceAddress: ByteArray,
        val destinationAddress: ByteArray,
        val sourcePort: Int,
        val destinationPort: Int,
        val dnsMessage: ByteArray
    )

    fun parse(packet: ByteArray, length: Int): Query? {
        if (length < 1) return null
        return when ((packet[0].toInt() ushr 4) and 0x0f) {
            4 -> parseIpv4(packet, length)
            6 -> parseIpv6(packet, length)
            else -> null
        }
    }

    fun response(query: Query, dnsMessage: ByteArray): ByteArray = when (query.version) {
        4 -> ipv4Response(query, dnsMessage)
        6 -> ipv6Response(query, dnsMessage)
        else -> error("Unsupported IP version")
    }

    private fun parseIpv4(packet: ByteArray, length: Int): Query? {
        if (length < 28) return null
        val headerLength = (packet[0].toInt() and 0x0f) * 4
        val totalLength = ushort(packet, 2)
        if (headerLength < 20 || totalLength < headerLength + 8 || totalLength > length) return null
        if ((packet[9].toInt() and 0xff) != UDP_PROTOCOL) return null
        return queryFromUdp(
            version = 4,
            sourceAddress = packet.copyOfRange(12, 16),
            destinationAddress = packet.copyOfRange(16, 20),
            packet = packet,
            udpOffset = headerLength,
            packetEnd = totalLength
        )
    }

    private fun parseIpv6(packet: ByteArray, length: Int): Query? {
        if (length < 48 || (packet[6].toInt() and 0xff) != UDP_PROTOCOL) return null
        val packetEnd = 40 + ushort(packet, 4)
        if (packetEnd < 48 || packetEnd > length) return null
        return queryFromUdp(
            version = 6,
            sourceAddress = packet.copyOfRange(8, 24),
            destinationAddress = packet.copyOfRange(24, 40),
            packet = packet,
            udpOffset = 40,
            packetEnd = packetEnd
        )
    }

    private fun queryFromUdp(
        version: Int,
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        packet: ByteArray,
        udpOffset: Int,
        packetEnd: Int
    ): Query? {
        if (udpOffset + 8 > packetEnd) return null
        val udpLength = ushort(packet, udpOffset + 4)
        if (udpLength < 8 || udpOffset + udpLength > packetEnd) return null
        val destinationPort = ushort(packet, udpOffset + 2)
        if (destinationPort != 53) return null
        return Query(
            version = version,
            sourceAddress = sourceAddress,
            destinationAddress = destinationAddress,
            sourcePort = ushort(packet, udpOffset),
            destinationPort = destinationPort,
            dnsMessage = packet.copyOfRange(udpOffset + 8, udpOffset + udpLength)
        )
    }

    private fun ipv4Response(query: Query, dnsMessage: ByteArray): ByteArray {
        val udpLength = 8 + dnsMessage.size
        val result = ByteArray(20 + udpLength)
        result[0] = 0x45
        result[8] = 64
        result[9] = UDP_PROTOCOL.toByte()
        putUshort(result, 2, result.size)
        query.destinationAddress.copyInto(result, 12)
        query.sourceAddress.copyInto(result, 16)
        putUshort(result, 10, checksum(result, 0, 20))
        writeUdp(result, 20, query, dnsMessage)
        val udpChecksum = udpChecksumIpv4(result, 20, udpLength)
        putUshort(result, 26, if (udpChecksum == 0) 0xffff else udpChecksum)
        return result
    }

    private fun ipv6Response(query: Query, dnsMessage: ByteArray): ByteArray {
        val udpLength = 8 + dnsMessage.size
        val result = ByteArray(40 + udpLength)
        result[0] = 0x60
        putUshort(result, 4, udpLength)
        result[6] = UDP_PROTOCOL.toByte()
        result[7] = 64
        query.destinationAddress.copyInto(result, 8)
        query.sourceAddress.copyInto(result, 24)
        writeUdp(result, 40, query, dnsMessage)
        putUshort(result, 46, udpChecksumIpv6(result, 40, udpLength))
        return result
    }

    private fun writeUdp(result: ByteArray, offset: Int, query: Query, dnsMessage: ByteArray) {
        putUshort(result, offset, query.destinationPort)
        putUshort(result, offset + 2, query.sourcePort)
        putUshort(result, offset + 4, 8 + dnsMessage.size)
        dnsMessage.copyInto(result, offset + 8)
    }

    private fun udpChecksumIpv4(packet: ByteArray, udpOffset: Int, udpLength: Int): Int {
        var sum = 0L
        for (index in 12 until 20 step 2) sum += ushort(packet, index).toLong()
        sum += UDP_PROTOCOL + udpLength
        return checksumWithSeed(packet, udpOffset, udpLength, sum)
    }

    private fun udpChecksumIpv6(packet: ByteArray, udpOffset: Int, udpLength: Int): Int {
        var sum = 0L
        for (index in 8 until 40 step 2) sum += ushort(packet, index).toLong()
        sum += (udpLength ushr 16) and 0xffff
        sum += udpLength and 0xffff
        sum += UDP_PROTOCOL
        return checksumWithSeed(packet, udpOffset, udpLength, sum)
    }

    private fun checksum(bytes: ByteArray, offset: Int, length: Int): Int =
        checksumWithSeed(bytes, offset, length, 0)

    private fun checksumWithSeed(bytes: ByteArray, offset: Int, length: Int, initial: Long): Int {
        var sum = initial
        var index = offset
        val end = offset + length
        while (index + 1 < end) {
            sum += ushort(bytes, index).toLong()
            index += 2
        }
        if (index < end) sum += ((bytes[index].toInt() and 0xff) shl 8).toLong()
        while (sum ushr 16 != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
    }

    private fun ushort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun putUshort(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }
}
