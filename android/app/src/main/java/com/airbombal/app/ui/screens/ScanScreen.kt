package com.airbombal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbombal.app.model.Ap
import com.airbombal.app.model.EngineState
import com.airbombal.app.model.Iface
import com.airbombal.app.ui.components.EmptyState
import com.airbombal.app.ui.components.Hint
import com.airbombal.app.ui.components.MonoText
import com.airbombal.app.ui.components.Panel
import com.airbombal.app.ui.components.Pill
import com.airbombal.app.ui.components.SectionLabel
import com.airbombal.app.ui.components.SignalBars
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    state: EngineState,
    onEnableMonitor: (String) -> Unit,
    onDisableMonitor: (String?) -> Unit,
    onRefreshIfaces: () -> Unit,
    onStartScan: (String) -> Unit,
    onStopScan: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var band by rememberSaveable { mutableStateOf("both") }
    var pendingMonitor by remember { mutableStateOf<Iface?>(null) }
    val bandArg = when (band) {
        "2.4" -> "bg"
        "5" -> "a"
        else -> "abg"
    }
    val adapters = state.ifaces.filter { candidate ->
        (candidate.type == "managed" || candidate.type == "monitor") &&
            !candidate.name.startsWith("p2p") &&
            !candidate.name.contains("aware") &&
            candidate.name != "lo"
    }

    pendingMonitor?.let { target ->
        MonitorConfirmDialog(
            target = target,
            onConfirm = {
                pendingMonitor = null
                onEnableMonitor(target.name)
            },
            onDismiss = { pendingMonitor = null },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Panel {
                SectionLabel("Radio", trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onRefreshIfaces, modifier = Modifier.size(26.dp)) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Rescan adapters",
                                tint = MistDim,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Pill(
                            text = if (state.monIface != null) "monitor" else "managed",
                            color = if (state.monIface != null) Neon else Amber,
                            filled = true,
                        )
                    }
                })
                Spacer(Modifier.height(8.dp))
                Hint(
                    "Adapters are detected automatically — plug in a USB radio and it appears " +
                        "here. Switch any interface between managed and monitor mode on demand.",
                )
                Spacer(Modifier.height(10.dp))

                if (adapters.isEmpty()) {
                    Hint("No wireless adapter detected. Plug one in, then tap refresh.")
                }

                adapters.forEach { iface ->
                    AdapterRow(
                        iface = iface,
                        onEnable = { pendingMonitor = iface },
                        onDisable = { onDisableMonitor(iface.name) },
                    )
                }

                if (state.dryRun) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "SIMULATION MODE — no radio will be touched",
                        color = Amber,
                        fontSize = 10.sp,
                        fontFamily = Mono,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }

        item {
            Panel {
                SectionLabel("Passive scan", trailing = {
                    MonoText(
                        if (state.scanning) "live" else "idle",
                        color = if (state.scanning) Neon else MistDim,
                        size = 10,
                    )
                })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("2.4" to "2.4 GHz", "5" to "5 GHz", "both" to "BOTH").forEach { (key, label) ->
                        FilterChip(
                            selected = band == key,
                            onClick = { band = key },
                            enabled = !state.scanning,
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Transparent,
                                labelColor = MistDim,
                                selectedContainerColor = Neon.copy(alpha = 0.16f),
                                selectedLabelColor = Neon,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { if (state.scanning) onStopScan() else onStartScan(bandArg) },
                    enabled = state.connected && (state.scanning || state.monIface != null),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.scanning) PanelHigh else Neon,
                        contentColor = if (state.scanning) Neon else OnNeon,
                        disabledContainerColor = Line.copy(alpha = 0.4f),
                        disabledContentColor = MistDim,
                    ),
                ) {
                    Icon(
                        if (state.scanning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.scanning) "STOP SCAN" else "START SCAN",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
                if (state.scanning) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = Neon,
                        trackColor = Line,
                    )
                }
                if (state.monIface == null && !state.scanning) {
                    Spacer(Modifier.height(8.dp))
                    Hint("Pick an adapter above and tap MONITOR to begin scanning.")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("APS", state.aps.size.toString(), Neon, Modifier.weight(1f))
                StatCard("CLIENTS", state.clients.size.toString(), Cyan, Modifier.weight(1f))
                StatCard(
                    "HANDSHAKES",
                    state.aps.count { it.handshake }.toString(),
                    Amber,
                    Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "ACCESS POINTS",
                    color = Neon,
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp,
                    modifier = Modifier.weight(1f),
                )
                MonoText("${state.aps.size}", color = MistDim, size = 10)
            }
        }

        if (state.aps.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Wifi,
                    title = if (state.scanning) "Listening for beacons…" else "No scan data",
                    body = if (state.scanning) {
                        "Access points will appear here as the radio hears them."
                    } else {
                        "Switch an adapter to monitor mode, then start a scan to discover networks."
                    },
                )
            }
        }

        items(state.aps, key = { it.bssid }) { ap ->
            ApRow(
                ap = ap,
                selected = state.selectedBssid == ap.bssid,
                clientCount = state.clients.count { it.bssid == ap.bssid },
                onClick = { onSelect(ap.bssid) },
            )
        }

        item { Spacer(Modifier.height(6.dp)) }
    }
}

@Composable
private fun AdapterRow(iface: Iface, onEnable: () -> Unit, onDisable: () -> Unit) {
    val isMonitor = iface.type == "monitor"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isMonitor) Neon.copy(alpha = 0.06f) else Color.Transparent)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Wifi,
            contentDescription = null,
            tint = if (isMonitor) Neon else MistDim,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoText(iface.name, color = if (isMonitor) Neon else Mist, size = 14, weight = FontWeight.Bold)
                if (iface.uplink) {
                    Spacer(Modifier.width(6.dp))
                    Pill("uplink", Amber, filled = true)
                }
                if (isMonitor) {
                    Spacer(Modifier.width(6.dp))
                    Pill("monitor", Neon, filled = true)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${iface.type} · ${if (iface.up) "up" else "down"} · ${iface.mac.ifBlank { "no mac" }}",
                color = MistDim,
                fontSize = 10.sp,
                fontFamily = Mono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = if (isMonitor) onDisable else onEnable,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isMonitor) PanelHigh else Neon.copy(alpha = 0.16f),
                contentColor = if (isMonitor) Neon else Neon,
            ),
        ) {
            Text(
                if (isMonitor) "MANAGED" else "MONITOR",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

@Composable
private fun MonitorConfirmDialog(target: Iface, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelHigh,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = Amber) },
        title = {
            Text(
                if (target.uplink) "Switch ${target.name} to monitor mode?" else "Enable monitor mode?",
                color = Mist,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                if (target.uplink) {
                    "${target.name} currently carries your internet connection. While it is in " +
                        "monitor mode, Wi-Fi and internet access will drop. Switch it back to " +
                        "MANAGED when you are done."
                } else {
                    "${target.name} will stop normal Wi-Fi operation until you switch it back to " +
                        "managed mode."
                },
                color = MistDim,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("SWITCH", color = Danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = MistDim, fontSize = 12.sp)
            }
        },
    )
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PanelHigh)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = MistDim,
            fontSize = 8.sp,
            fontFamily = Mono,
            letterSpacing = 1.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun ApRow(ap: Ap, selected: Boolean, clientCount: Int, onClick: () -> Unit) {
    Panel(
        accent = if (selected) Neon else null,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Neon.copy(alpha = 0.12f) else PanelHigh)
                    .padding(8.dp),
            ) {
                SignalBars(ap.signal)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (ap.privacy == "OPEN") Icons.Filled.LockOpen else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (ap.privacy == "OPEN") Amber else MistDim,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        ap.ssid,
                        color = if (selected) Neon else Mist,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(3.dp))
                MonoText(ap.bssid, color = MistDim, size = 11)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill("CH ${ap.channel ?: "?"}", Cyan)
                    if (ap.handshake) Pill("HS", Neon, filled = true)
                }
                Spacer(Modifier.height(4.dp))
                MonoText(
                    "${ap.signal ?: 0} dBm" + if (clientCount > 0) " · $clientCount cli" else "",
                    color = MistDim,
                    size = 9,
                )
            }
        }
        if (selected) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "TARGET SELECTED",
                    color = Neon,
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Open the DEAUTH tab to engage",
                    color = MistDim,
                    fontSize = 10.sp,
                )
            }
        }
    }
}
