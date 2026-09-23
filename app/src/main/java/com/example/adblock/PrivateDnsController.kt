package com.example.adblock

import android.content.Context
import android.provider.Settings

object PrivateDnsController {

    private const val DNS_HOSTNAME = "dns.adguard-dns.com"
    private const val PRIVATE_DNS_MODE = "private_dns_mode"
    private const val PRIVATE_DNS_SPECIFIER = "private_dns_specifier"

    fun isEnabled(context: Context): Boolean {
        val mode = Settings.Global.getString(context.contentResolver, PRIVATE_DNS_MODE)
        val hostname = Settings.Global.getString(context.contentResolver, PRIVATE_DNS_SPECIFIER)
        return mode == "hostname" && hostname == DNS_HOSTNAME
    }

    fun enable(context: Context): Boolean {
        val modeUpdated = Settings.Global.putString(
            context.contentResolver,
            PRIVATE_DNS_MODE,
            "hostname"
        )
        val hostnameUpdated = Settings.Global.putString(
            context.contentResolver,
            PRIVATE_DNS_SPECIFIER,
            DNS_HOSTNAME
        )
        return modeUpdated && hostnameUpdated
    }

    fun disable(context: Context): Boolean {
        return Settings.Global.putString(
            context.contentResolver,
            PRIVATE_DNS_MODE,
            "off"
        )
    }

    fun toggle(context: Context): Boolean {
        return if (isEnabled(context)) disable(context) else enable(context)
    }
}
