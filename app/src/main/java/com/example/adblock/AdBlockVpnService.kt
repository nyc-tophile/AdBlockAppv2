package com.example.adblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A DNS-only VPN. The VPN's only routes are its two synthetic DNS server addresses, so ordinary
 * app traffic continues on the device's normal network. UDP DNS sent to those addresses is read
 * from the TUN interface, checked against [DnsFilter], and either answered locally or forwarded.
 */
class AdBlockVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var worker: ExecutorService? = null
    private val outputLock = Any()

    @Volatile
    private var running = false

    @Volatile
    private var failed = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            VpnControl.ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            VpnControl.ACTION_START, null -> startVpnIfNeeded()
        }
        return START_NOT_STICKY
    }

    private fun startVpnIfNeeded() {
        if (running) return
        if (VpnService.prepare(this) != null) {
            fail("VPN permission is required. Open AdBlock and tap Enable.")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
            val descriptor = Builder()
                .setSession(getString(R.string.vpn_session_name))
                .setMtu(MTU)
                .addAddress(LOCAL_DNS_IPV4, 32)
                .addRoute(LOCAL_DNS_IPV4_SERVER, 32)
                .addDnsServer(LOCAL_DNS_IPV4_SERVER)
                .addAddress(LOCAL_DNS_IPV6, 128)
                .addRoute(LOCAL_DNS_IPV6_SERVER, 128)
                .addDnsServer(LOCAL_DNS_IPV6_SERVER)
                .setBlocking(true)
                .establish() ?: throw IOException("Android did not create the VPN interface")

            vpnInterface = descriptor
            running = true
            failed = false
            AdBlockStatusStore.set(this, AdBlockStatus.ON)
            worker = Executors.newSingleThreadExecutor().also { executor ->
                executor.execute { readPackets(descriptor) }
            }
        } catch (exception: Exception) {
            fail("Error starting VPN: ${exception.message ?: exception.javaClass.simpleName}", exception)
        }
    }

    private fun readPackets(descriptor: ParcelFileDescriptor) {
        try {
            FileInputStream(descriptor.fileDescriptor).use { input ->
                FileOutputStream(descriptor.fileDescriptor).use { output ->
                    val buffer = ByteArray(MAX_PACKET_SIZE)
                    while (running) {
                        val count = input.read(buffer)
                        if (count <= 0) continue
                        val query = DnsPacket.parse(buffer, count) ?: continue
                        val answer = if (DnsFilter.shouldBlock(query.dnsMessage)) {
                            DnsFilter.nxdomain(query.dnsMessage)
                        } else {
                            forwardDns(query.dnsMessage)
                        } ?: continue

                        val response = DnsPacket.response(query, answer)
                        synchronized(outputLock) {
                            output.write(response)
                            output.flush()
                        }
                    }
                }
            }
        } catch (exception: IOException) {
            if (running) fail("DNS VPN stopped unexpectedly: ${exception.message}", exception)
        } catch (exception: RuntimeException) {
            if (running) fail("DNS VPN failed: ${exception.message}", exception)
        }
    }

    /** Forwards a raw DNS message over protected UDP, so this socket bypasses this VPN. */
    private fun forwardDns(query: ByteArray): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                if (!protect(socket)) {
                    Log.e(TAG, "Could not protect DNS upstream socket")
                    return null
                }
                socket.soTimeout = UPSTREAM_TIMEOUT_MS
                socket.connect(UPSTREAM_DNS, DNS_PORT)
                socket.send(DatagramPacket(query, query.size))
                val response = ByteArray(MAX_DNS_MESSAGE_SIZE)
                val packet = DatagramPacket(response, response.size)
                socket.receive(packet)
                if (!sameTransaction(query, response)) return null
                response.copyOf(packet.length)
            }
        } catch (exception: IOException) {
            Log.w(TAG, "Upstream DNS request failed", exception)
            null
        }
    }

    private fun sameTransaction(query: ByteArray, response: ByteArray): Boolean =
        query.size >= 2 && response.size >= 2 && query[0] == response[0] && query[1] == response[1]

    override fun onRevoke() {
        AdBlockStatusStore.set(this, AdBlockStatus.ERROR, "Android revoked the VPN connection")
        failed = true
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        vpnInterface?.close()
        vpnInterface = null
        worker?.shutdownNow()
        worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (!failed) AdBlockStatusStore.set(this, AdBlockStatus.OFF)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    private fun fail(message: String, exception: Exception? = null) {
        Log.e(TAG, message, exception)
        failed = true
        running = false
        AdBlockStatusStore.set(this, AdBlockStatus.ERROR, message)
        stopSelf()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        return Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_adblock)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "AdBlockVpnService"
        private const val NOTIFICATION_CHANNEL = "adblock_vpn"
        private const val NOTIFICATION_ID = 1001
        private const val MTU = 1500
        private const val DNS_PORT = 53
        private const val UPSTREAM_TIMEOUT_MS = 5_000
        private const val MAX_PACKET_SIZE = 32_767
        private const val MAX_DNS_MESSAGE_SIZE = 4_096

        // Reserved private addresses used only inside this VPN interface.
        private const val LOCAL_DNS_IPV4 = "10.23.0.1"
        private const val LOCAL_DNS_IPV4_SERVER = "10.23.0.2"
        private const val LOCAL_DNS_IPV6 = "fd23:ad:bc::1"
        private const val LOCAL_DNS_IPV6_SERVER = "fd23:ad:bc::2"
        private val UPSTREAM_DNS: InetAddress = InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))
    }
}
