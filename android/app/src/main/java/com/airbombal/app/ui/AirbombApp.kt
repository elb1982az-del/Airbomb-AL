package com.airbombal.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbombal.app.AirbombViewModel
import com.airbombal.app.R
import com.airbombal.app.model.EngineState
import com.airbombal.app.model.Notice
import com.airbombal.app.ui.components.Pill
import com.airbombal.app.ui.screens.AttackScreen
import com.airbombal.app.ui.screens.CaptureScreen
import com.airbombal.app.ui.screens.LogScreen
import com.airbombal.app.ui.screens.ScanScreen
import com.airbombal.app.ui.theme.AirbombTheme
import com.airbombal.app.ui.theme.Amber
import com.airbombal.app.ui.theme.Danger
import com.airbombal.app.ui.theme.Ink
import com.airbombal.app.ui.theme.Line
import com.airbombal.app.ui.theme.Mist
import com.airbombal.app.ui.theme.MistDim
import com.airbombal.app.ui.theme.Mono
import com.airbombal.app.ui.theme.Neon
import com.airbombal.app.ui.theme.OnNeon
import com.airbombal.app.ui.theme.Panel
import com.airbombal.app.ui.theme.PanelHigh
import kotlinx.coroutines.delay

@Composable
fun AirbombApp(vm: AirbombViewModel) {
    var accepted by rememberSaveable { mutableStateOf(false) }
    AirbombTheme {
        if (accepted) {
            MainScreen(vm)
        } else {
            DisclaimerScreen(onAccept = { accepted = true })
        }
    }
}

// ---------------------------------------------------------------- disclaimer

@Composable
private fun DisclaimerScreen(onAccept: () -> Unit) {
    var checked by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B1A15), Ink, Color(0xFF040807)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.airbomb_logo),
                contentDescription = "Airbomb-Al dragon",
                modifier = Modifier.size(120.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "AIRBOMB-AL",
                color = Mist,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
            )
            Text(
                "WIRELESS SECURITY AUDIT CONSOLE",
                color = Neon,
                fontFamily = Mono,
                fontSize = 9.sp,
                letterSpacing = 2.4.sp,
            )

            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Danger.copy(alpha = 0.08f))
                    .border(1.5.dp, Danger.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Danger,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "LEGAL NOTICE",
                        color = Danger,
                        fontFamily = Mono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.disclaimer),
                    color = Mist,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp,
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { checked = !checked }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Neon,
                        uncheckedColor = MistDim,
                        checkmarkColor = OnNeon,
                    ),
                )
                Text(
                    "I understand that I am solely responsible for how I use this tool, " +
                        "and that I will only target networks I own or am authorized to test.",
                    color = MistDim,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onAccept,
                enabled = checked,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Neon,
                    contentColor = OnNeon,
                    disabledContainerColor = Line.copy(alpha = 0.5f),
                    disabledContentColor = MistDim,
                ),
            ) {
                Text(
                    "ACKNOWLEDGE & CONTINUE",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Local engine · 127.0.0.1:8767 · no cloud, no telemetry",
                color = MistDim,
                fontFamily = Mono,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------- main shell

private data class Tab(val label: String, val icon: ImageVector)

@Composable
private fun MainScreen(vm: AirbombViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf(
        Tab("SCAN", Icons.Filled.Search),
        Tab("DEAUTH", Icons.Filled.Bolt),
        Tab("CAPTURE", Icons.Filled.Download),
        Tab("LOGS", Icons.AutoMirrored.Filled.List),
    )

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(20.dp)) },
                        label = { Text(item.label, fontSize = 9.sp, fontFamily = Mono, letterSpacing = 0.6.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Neon,
                            selectedTextColor = Neon,
                            indicatorColor = Neon.copy(alpha = 0.12f),
                            unselectedIconColor = MistDim,
                            unselectedTextColor = MistDim,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Header(state, onReconnect = vm::reconnect)
            state.notice?.let { notice ->
                NoticeBanner(notice, onDismiss = vm::dismissNotice)
            }
            if (!state.connected) {
                OfflineBanner(onRetry = vm::reconnect)
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> ScanScreen(
                        state = state,
                        onEnableMonitor = vm::enableMonitor,
                        onDisableMonitor = vm::disableMonitor,
                        onRefreshIfaces = vm::refreshIfaces,
                        onStartScan = { band -> vm.startScan(band, null) },
                        onStopScan = vm::stopScan,
                        onSelect = vm::selectAp,
                    )

                    1 -> AttackScreen(
                        state = state,
                        onSelectClient = vm::selectClient,
                        onStart = vm::startDeauth,
                        onStop = vm::stopDeauth,
                        onStopAll = vm::stopAllDeauth,
                    )

                    2 -> CaptureScreen(
                        state = state,
                        onStart = vm::startCapture,
                        onStop = vm::stopCapture,
                        onCheck = vm::checkCapture,
                    )

                    else -> LogScreen(state = state, onClear = vm::clearLogs)
                }
            }
        }
    }
}

@Composable
private fun Header(state: EngineState, onReconnect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(PanelHigh, Panel))),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.airbomb_logo),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "AIRBOMB-AL",
                        color = Mist,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.4.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Pill("v1.0.0", MistDim)
                    if (state.dryRun) {
                        Spacer(Modifier.width(5.dp))
                        Pill("sim", Amber, filled = true)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(if (state.connected) Neon else Danger, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (state.connected) {
                            "ENGINE ONLINE · ${state.monIface ?: "MANAGED RADIO"}"
                        } else {
                            "ENGINE OFFLINE · 127.0.0.1:8767"
                        },
                        color = if (state.connected) Neon else Danger,
                        fontFamily = Mono,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onReconnect) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Reconnect",
                    tint = if (state.connected) MistDim else Danger,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}

@Composable
private fun NoticeBanner(notice: Notice, onDismiss: () -> Unit) {
    LaunchedEffect(notice) {
        delay(5000)
        onDismiss()
    }
    val color = when {
        notice.error -> Danger
        notice.warning -> Amber
        else -> Neon
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f))
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when {
                notice.error -> Icons.Filled.ErrorOutline
                notice.warning -> Icons.Filled.Warning
                else -> Icons.Filled.CheckCircle
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            notice.text,
            color = color,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = color, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun OfflineBanner(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Danger.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = Danger,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Start the engine: airbomb-engine start",
            color = Danger,
            fontFamily = Mono,
            fontSize = 10.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text("RETRY", color = Danger, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
