package com.airbombal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbombal.app.model.EngineState
import com.airbombal.app.ui.components.EmptyState
import com.airbombal.app.ui.components.Hint
import com.airbombal.app.ui.components.MonoText
import com.airbombal.app.ui.components.Panel
import com.airbombal.app.ui.components.Pill
import com.airbombal.app.ui.components.SectionLabel
import com.airbombal.app.ui.theme.Cyan
import com.airbombal.app.ui.theme.Line
import com.airbombal.app.ui.theme.Mist
import com.airbombal.app.ui.theme.MistDim
import com.airbombal.app.ui.theme.Mono
import com.airbombal.app.ui.theme.Neon
import com.airbombal.app.ui.theme.OnNeon
import com.airbombal.app.ui.theme.PanelHigh
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CaptureScreen(
    state: EngineState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCheck: () -> Unit,
) {
    val ap = state.selectedAp
    if (ap == null) {
        EmptyState(
            icon = Icons.Filled.Wifi,
            title = "No target selected",
            body = "Select an access point on the SCAN tab to capture its WPA handshake.",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val active = state.captureJobs.firstOrNull { it.target == ap.bssid }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active?.id) {
        while (active != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = active?.let { ((now - it.since) / 1000L).coerceAtLeast(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Panel {
                SectionLabel("Target", trailing = { Pill("CH ${ap.channel ?: "?"}", Cyan) })
                Spacer(Modifier.height(8.dp))
                Text(ap.ssid, color = Mist, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                MonoText(ap.bssid, color = MistDim, size = 12)
                Spacer(Modifier.height(10.dp))
                if (ap.handshake) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Neon,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "HANDSHAKE CAPTURED",
                            color = Neon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                } else {
                    Hint("No handshake yet for this access point.")
                }
            }
        }

        item {
            Panel(accent = if (active != null) Neon.copy(alpha = 0.5f) else null) {
                SectionLabel("Capture", trailing = {
                    if (active != null) Pill("recording", Neon, filled = true)
                })
                Spacer(Modifier.height(10.dp))
                if (active != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(Neon, RoundedCornerShape(50)),
                        )
                        Spacer(Modifier.width(8.dp))
                        MonoText("${elapsed}s elapsed", color = Mist, size = 12)
                        Spacer(Modifier.weight(1f))
                        Text("${active.prefix.substringAfterLast('/')}", color = MistDim, fontSize = 9.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("STOP CAPTURE", fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = onStart,
                        enabled = state.connected,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Neon,
                            contentColor = OnNeon,
                            disabledContainerColor = Line.copy(alpha = 0.4f),
                            disabledContentColor = MistDim,
                        ),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("START CAPTURE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCheck,
                    enabled = state.connected,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Neon,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("CHECK HANDSHAKE", fontSize = 12.sp, color = Neon)
                }
                Spacer(Modifier.height(10.dp))
                Hint(
                    "A handshake needs a client to reconnect. Run a short deauth burst from " +
                        "the DEAUTH tab, then check again.",
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "CAPTURE FILES",
                    color = Neon,
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp,
                    modifier = Modifier.weight(1f),
                )
                MonoText("${state.captures.size}", color = MistDim, size = 10)
            }
        }

        if (state.captures.isEmpty()) {
            item {
                Panel {
                    Hint("No capture files yet. Handshakes land in backend/captures/.")
                }
            }
        } else {
            items(state.captures.size) { index ->
                val file = state.captures[index]
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            MonoText(file.name, color = Mist, size = 11)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${formatSize(file.size)} · ${formatTime(file.modified)}",
                                color = MistDim,
                                fontSize = 10.sp,
                                fontFamily = Mono,
                            )
                        }
                        Pill("CAP", Cyan)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(6.dp)) }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatTime(epochSeconds: Long): String =
    SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000L))
