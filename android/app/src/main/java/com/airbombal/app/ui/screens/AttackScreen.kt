package com.airbombal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbombal.app.model.EngineState
import com.airbombal.app.ui.components.EmptyState
import com.airbombal.app.ui.components.Hint
import com.airbombal.app.ui.components.KeyValue
import com.airbombal.app.ui.components.MonoText
import com.airbombal.app.ui.components.Panel
import com.airbombal.app.ui.components.Pill
import com.airbombal.app.ui.components.SectionLabel
import com.airbombal.app.ui.theme.Amber
import com.airbombal.app.ui.theme.Cyan
import com.airbombal.app.ui.theme.Danger
import com.airbombal.app.ui.theme.Line
import com.airbombal.app.ui.theme.Mist
import com.airbombal.app.ui.theme.MistDim
import com.airbombal.app.ui.theme.Mono
import com.airbombal.app.ui.theme.Neon
import com.airbombal.app.ui.theme.OnNeon
import com.airbombal.app.ui.theme.PanelHigh
import kotlinx.coroutines.delay

@Composable
fun AttackScreen(
    state: EngineState,
    onSelectClient: (String?) -> Unit,
    onStart: (Int, Int) -> Unit,
    onStop: (Int) -> Unit,
    onStopAll: () -> Unit,
) {
    val ap = state.selectedAp
    if (ap == null) {
        EmptyState(
            icon = Icons.Filled.Wifi,
            title = "No target selected",
            body = "Pick an access point on the SCAN tab. Deauth is restricted to BSSIDs " +
                "the current scan has actually seen.",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    var frames by rememberSaveable { mutableStateOf(8f) }
    var duration by rememberSaveable { mutableStateOf(20f) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.deauthJobs.size) {
        while (state.deauthJobs.isNotEmpty()) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val clients = state.clientsFor(ap.bssid)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Panel(accent = Danger.copy(alpha = 0.5f)) {
                SectionLabel("Target", trailing = { Pill("${ap.channel ?: "?"}", Cyan) })
                Spacer(Modifier.height(8.dp))
                Text(ap.ssid, color = Mist, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                MonoText(ap.bssid, color = MistDim, size = 12)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(ap.privacy.ifBlank { "OPEN" }, if (ap.privacy == "OPEN") Amber else Neon)
                    Pill("${ap.signal ?: 0} dBm", MistDim)
                    if (ap.handshake) Pill("handshake", Neon, filled = true)
                }
            }
        }

        item {
            Panel {
                SectionLabel("Clients", trailing = {
                    MonoText("${clients.size} seen", color = MistDim, size = 10)
                })
                Spacer(Modifier.height(6.dp))
                ClientRow(
                    title = "All clients (broadcast)",
                    subtitle = "Deauth every station of this access point",
                    selected = state.selectedClient == null,
                    onClick = { onSelectClient(null) },
                )
                clients.forEach { client ->
                    ClientRow(
                        title = client.mac,
                        subtitle = "${client.signal ?: 0} dBm · ${client.packets} pkt" +
                            if (client.probes.isNotBlank()) " · ${client.probes.take(18)}" else "",
                        selected = state.selectedClient == client.mac,
                        onClick = { onSelectClient(client.mac) },
                        mono = true,
                    )
                }
                if (clients.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Hint("No clients seen yet. Broadcast deauth still targets the access point.")
                }
            }
        }

        item {
            Panel {
                SectionLabel("Burst")
                Spacer(Modifier.height(4.dp))
                SliderRow(
                    label = "FRAMES",
                    value = frames,
                    display = frames.toInt().toString(),
                    range = 1f..64f,
                    steps = 62,
                    onChange = { frames = it },
                )
                SliderRow(
                    label = "DURATION",
                    value = duration,
                    display = "${duration.toInt()}s",
                    range = 5f..120f,
                    steps = 22,
                    onChange = { duration = it },
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onStart(frames.toInt(), duration.toInt()) },
                    enabled = state.connected,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Danger,
                        contentColor = OnNeon,
                        disabledContainerColor = Line.copy(alpha = 0.4f),
                        disabledContentColor = MistDim,
                    ),
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("START DEAUTH", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(8.dp))
                Hint(
                    "Only test networks you own or have explicit written authorization to assess. " +
                        "The engine refuses targets that are not in the live scan.",
                    color = MistDim,
                )
            }
        }

        if (state.deauthJobs.isNotEmpty()) {
            item {
                Panel(accent = Amber.copy(alpha = 0.5f)) {
                    SectionLabel("Active sessions", trailing = {
                        Pill("${state.deauthJobs.size} live", Amber, filled = true)
                    })
                    Spacer(Modifier.height(6.dp))
                    state.deauthJobs.forEach { job ->
                        val elapsed = ((now - job.since) / 1000L).coerceAtLeast(0)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                MonoText(job.target, color = Mist, size = 12)
                                Text(
                                    "${elapsed}s · ${job.count} frames",
                                    color = MistDim,
                                    fontSize = 10.sp,
                                    fontFamily = Mono,
                                )
                            }
                            TextButton(onClick = { onStop(job.id) }) {
                                Text("STOP", color = Danger, fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onStopAll,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("STOP ALL", fontSize = 12.sp, color = Danger)
                    }
                }
            }
        }

        item {
            Panel {
                SectionLabel("Session")
                Spacer(Modifier.height(6.dp))
                KeyValue("radio", state.monIface ?: "no monitor")
                KeyValue("client", state.selectedClient ?: "broadcast")
                KeyValue("engine", if (state.connected) "online" else "offline",
                    if (state.connected) Neon else Danger)
            }
        }
    }
}

@Composable
private fun ClientRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    mono: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Neon.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Neon, unselectedColor = Line),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (selected) Neon else Mist,
                fontSize = 12.sp,
                fontFamily = if (mono) Mono else null,
                maxLines = 1,
            )
            Text(subtitle, color = MistDim, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    display: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = MistDim,
            fontFamily = Mono,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.width(76.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Neon,
                activeTrackColor = Neon,
                inactiveTrackColor = PanelHigh,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(44.dp)) {
            Text(display, color = Neon, fontFamily = Mono, fontSize = 12.sp)
        }
    }
}
