package com.example.adblock

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var screenState by mutableStateOf(AdBlockScreenState(AdBlockStatus.OFF, null, false))
    private var receiverRegistered = false

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && VpnControl.hasPermission(this)) {
            VpnControl.start(this)
        } else {
            AdBlockStatusStore.set(this, AdBlockStatus.PERMISSION_REQUIRED)
        }
        refreshState()
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshState()
        registerStatusReceiver()
        setContent {
            MaterialTheme {
                AdBlockHome(
                    state = screenState,
                    onToggle = ::toggleAdBlock
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(statusReceiver)
        super.onDestroy()
    }

    private fun toggleAdBlock() {
        if (screenState.status == AdBlockStatus.ON || screenState.status == AdBlockStatus.STARTING) {
            VpnControl.stop(this)
            refreshState()
            return
        }

        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent == null) {
            VpnControl.start(this)
        } else {
            AdBlockStatusStore.set(this, AdBlockStatus.PERMISSION_REQUIRED)
            vpnPermission.launch(prepareIntent)
        }
        refreshState()
    }

    private fun refreshState() {
        val snapshot = AdBlockStatusStore.snapshot(this)
        val granted = VpnControl.hasPermission(this)
        screenState = AdBlockScreenState(
            status = if (!granted && snapshot.status == AdBlockStatus.OFF) {
                AdBlockStatus.PERMISSION_REQUIRED
            } else {
                snapshot.status
            },
            detail = snapshot.detail,
            permissionGranted = granted
        )
    }

    private fun registerStatusReceiver() {
        val filter = IntentFilter(AdBlockStatusStore.ACTION_STATUS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(statusReceiver, filter)
        }
        receiverRegistered = true
    }
}

private data class AdBlockScreenState(
    val status: AdBlockStatus,
    val detail: String?,
    val permissionGranted: Boolean
)

@androidx.compose.runtime.Composable
private fun AdBlockHome(state: AdBlockScreenState, onToggle: () -> Unit) {
    val enabled = state.status == AdBlockStatus.ON || state.status == AdBlockStatus.STARTING
    val statusText = when (state.status) {
        AdBlockStatus.ON -> "AdBlock is ON"
        AdBlockStatus.OFF -> "AdBlock is OFF"
        AdBlockStatus.STARTING -> "Starting AdBlock…"
        AdBlockStatus.PERMISSION_REQUIRED -> "VPN permission required"
        AdBlockStatus.ERROR -> "VPN service unavailable"
    }
    val buttonText = if (enabled) "Disable AdBlock" else "Enable AdBlock"

    Surface(color = Color(0xFF101827)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101827))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("AdBlock", color = Color.White, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(20.dp))
            Text(
                text = statusText,
                color = if (enabled) Color(0xFF86EFAC) else Color(0xFFFDE68A),
                style = MaterialTheme.typography.titleLarge
            )
            state.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Spacer(Modifier.height(8.dp))
                Text(detail, color = Color(0xFFFCA5A5), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.status != AdBlockStatus.STARTING
            ) {
                Text(buttonText)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "AdBlock uses Android's official VPN permission to send DNS lookups through a local filter. " +
                    "Allowed DNS queries are forwarded to a resolver; normal traffic does not pass through this app.",
                color = Color(0xFFB5C0D0),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (state.permissionGranted) {
                    "VPN permission has been granted. You can also toggle AdBlock from Quick Settings."
                } else {
                    "Android will show its official VPN confirmation when you enable AdBlock. No ADB or Private DNS setting is used."
                },
                color = Color(0xFFB5C0D0),
                textAlign = TextAlign.Center
            )
        }
    }
}
