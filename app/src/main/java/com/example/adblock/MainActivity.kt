package com.example.adblock

import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val BLOCKING_DNS = "dns.adguard-dns.com"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdBlockTheme {
                AdBlockScreen { enabled -> setPrivateDns(enabled) }
            }
        }
    }

    private fun setPrivateDns(enabled: Boolean) {
        try {
            if (enabled) {
                Settings.Global.putString(contentResolver, "private_dns_mode", "hostname")
                Settings.Global.putString(contentResolver, "private_dns_specifier", BLOCKING_DNS)
            } else {
                Settings.Global.putString(contentResolver, "private_dns_mode", "off")
            }
            Toast.makeText(this, if (enabled) "Ad blocking enabled" else "Ad blocking disabled", Toast.LENGTH_SHORT).show()
        } catch (securityException: SecurityException) {
            Toast.makeText(this, "Permission required: grant WRITE_SECURE_SETTINGS using ADB or Shizuku", Toast.LENGTH_LONG).show()
        } catch (exception: Exception) {
            Toast.makeText(this, "Unable to change Private DNS", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun AdBlockScreen(onToggle: (Boolean) -> Unit) {
    var enabled by rememberSaveable { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101827)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp), verticalArrangement = Arrangement.Center) {
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF202C40), RoundedCornerShape(28.dp)).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(modifier = Modifier.size(64.dp), shape = RoundedCornerShape(18.dp), color = Color(0xFFB9F6D4)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("✓", color = Color(0xFF123B2B), fontSize = 34.sp)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AdBlock", color = Color.White, fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(if (enabled) "Ads are blocked" else "Ads are not blocked", color = Color(0xFFB5C0D0), fontSize = 15.sp)
                    }
                    Switch(checked = enabled, onCheckedChange = { value -> enabled = value; onToggle(value) })
                }
            }
        }
    }
}

@Composable
private fun AdBlockTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
