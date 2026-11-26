package com.airbombal.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airbombal.app.model.Ap
import com.airbombal.app.model.CaptureFile
import com.airbombal.app.model.Client
import com.airbombal.app.model.EngineState
import com.airbombal.app.model.Iface
import com.airbombal.app.model.Job
import com.airbombal.app.model.LogLine
import com.airbombal.app.model.Notice
import com.airbombal.app.net.EngineClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class AirbombViewModel : ViewModel() {

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    init {
        EngineClient.connect()
        viewModelScope.launch {
            EngineClient.connected.collect { connected ->
                _state.update { it.copy(connected = connected) }
                if (connected) refreshAll()
            }
        }
        viewModelScope.launch {
            EngineClient.events.collect { handle(it) }
        }
        // Hot-plugged adapters are picked up without restarting anything.
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(4000)
                if (EngineClient.connected.value) EngineClient.send("iface.list")
            }
        }
    }

    // -- actions ----------------------------------------------------------

    fun reconnect() = EngineClient.reconnect()

    fun refresh() = refreshAll()

    private fun refreshAll() {
        EngineClient.send("hello")
        EngineClient.send("iface.list")
        EngineClient.send("scan.snapshot")
        EngineClient.send("capture.list")
        EngineClient.send("log.get", "limit" to 200)
    }

    fun selectAp(bssid: String) {
        _state.update { it.copy(selectedBssid = bssid, selectedClient = null) }
    }

    fun selectClient(mac: String?) {
        _state.update { it.copy(selectedClient = mac) }
    }

    fun enableMonitor(iface: String) = EngineClient.send("monitor.enable", "iface" to iface)

    fun disableMonitor(iface: String? = null) =
        EngineClient.send("monitor.disable", "iface" to iface)

    fun refreshIfaces() = EngineClient.send("iface.list")

    fun startScan(band: String, channel: Int?) =
        EngineClient.send("scan.start", "band" to band, "channel" to channel)

    fun stopScan() = EngineClient.send("scan.stop")

    fun startDeauth(count: Int, duration: Int) {
        val current = _state.value
        val bssid = current.selectedBssid ?: return
        EngineClient.send(
            "deauth.start",
            "bssid" to bssid,
            "client" to current.selectedClient,
            "count" to count,
            "duration" to duration,
        )
    }

    fun stopDeauth(id: Int) = EngineClient.send("deauth.stop", "id" to id)

    fun stopAllDeauth() = EngineClient.send("deauth.stop_all")

    fun startCapture() {
        val current = _state.value
        val ap = current.selectedAp ?: return
        EngineClient.send("capture.start", "bssid" to ap.bssid, "channel" to ap.channel)
    }

    fun stopCapture() {
        val bssid = _state.value.selectedBssid ?: return
        EngineClient.send("capture.stop", "bssid" to bssid)
    }

    fun checkCapture() {
        val bssid = _state.value.selectedBssid ?: return
        EngineClient.send("capture.check", "bssid" to bssid)
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun clearLogs() = _state.update { it.copy(logs = emptyList()) }

    // -- event handling ---------------------------------------------------

    private fun handle(msg: JSONObject) {
        when (msg.optString("event")) {
            "hello" -> _state.update {
                it.copy(
                    dryRun = msg.optBoolean("dry_run", it.dryRun),
                    monIface = msg.optString("mon_iface").takeIf { v -> v.isNotBlank() && v != "null" },
                    uplink = msg.optString("uplink", it.uplink),
                )
            }

            "log" -> appendLog(msg.optString("time"), msg.optString("level", "info"), msg.optString("message"))

            "scan" -> applyScan(msg)

            "deauth" -> applyJob(msg, deauth = true)

            "capture" -> applyJob(msg, deauth = false)

            "handshake" -> {
                refreshCaptures()
                notify("Handshake captured for ${msg.optString("bssid")}")
            }

            "engine_error" -> notify(msg.optString("message"), error = true)

            "reply" -> applyReply(msg)
        }
    }

    private fun applyReply(reply: JSONObject) {
        val cmd = reply.optString("cmd")
        val ok = reply.optBoolean("ok", false)
        if (!ok) {
            val error = reply.optString("error").ifBlank { "command failed" }
            notify(error, error = true)
            appendLog("", "error", "$cmd: $error")
            return
        }
        val data = reply.optJSONObject("data") ?: JSONObject()
        when (cmd) {
            "hello" -> _state.update {
                it.copy(
                    dryRun = data.optBoolean("dry_run", it.dryRun),
                    monIface = data.optString("mon_iface").takeIf { v -> v.isNotBlank() && v != "null" },
                    uplink = data.optString("uplink", it.uplink),
                )
            }

            "iface.list" -> _state.update {
                it.copy(
                    ifaces = parseIfaces(data.optJSONArray("interfaces")),
                    uplink = data.optString("uplink", it.uplink),
                    monIface = data.optString("mon_iface").takeIf { v -> v.isNotBlank() && v != "null" },
                )
            }

            "scan.snapshot" -> applyScan(data)

            "scan.start" -> {
                _state.update { it.copy(scanning = true) }
                EngineClient.send("scan.snapshot")
            }

            "scan.stop" -> _state.update { it.copy(scanning = false) }

            "monitor.enable" -> {
                val monitor = data.optString("mon_iface").takeIf { v -> v.isNotBlank() && v != "null" }
                _state.update { it.copy(monIface = monitor) }
                val warning = data.optString("warning").takeIf { it.isNotBlank() && it != "null" }
                if (warning != null) {
                    notify(warning, warning = true)
                } else if (monitor != null) {
                    notify("Monitor mode active on $monitor")
                }
                EngineClient.send("iface.list")
            }

            "monitor.disable" -> {
                _state.update {
                    it.copy(
                        monIface = data.optString("mon_iface").takeIf { v -> v.isNotBlank() && v != "null" },
                    )
                }
                notify("Managed mode restored — internet radio back to normal")
                EngineClient.send("iface.list")
            }

            "capture.list" -> {
                _state.update {
                    it.copy(
                        captures = parseCaptures(data.optJSONArray("files")),
                        captureJobs = parseJobs(data.optJSONArray("active")),
                    )
                }
            }

            "capture.start" -> refreshCaptures()

            "capture.check" -> {
                val handshake = data.optBoolean("handshake", false)
                val bssid = data.optString("bssid")
                notify(
                    if (handshake) "Handshake found for $bssid" else "No handshake yet for $bssid",
                    error = !handshake,
                )
                if (handshake) refreshCaptures()
            }
        }
    }

    private fun applyScan(msg: JSONObject) {
        // scan.start/scan.stop events only carry flags; keep the last snapshot.
        val aps = if (msg.has("aps")) parseAps(msg.optJSONArray("aps")) else null
        val clients = if (msg.has("clients")) parseClients(msg.optJSONArray("clients")) else null
        _state.update { current ->
            val nextClients = clients ?: current.clients
            current.copy(
                scanning = msg.optBoolean("scanning", current.scanning),
                monIface = msg.optString("mon_iface").takeIf { v -> v.isNotBlank() && v != "null" }
                    ?: current.monIface,
                aps = aps ?: current.aps,
                clients = nextClients,
                selectedClient = current.selectedClient?.takeIf { mac -> nextClients.any { it.mac == mac } },
            )
        }
    }

    private fun applyJob(msg: JSONObject, deauth: Boolean) {
        val job = msg.optJSONObject("job") ?: return
        val state = msg.optString("state")
        val parsed = Job(
            id = job.optInt("id"),
            kind = job.optString("kind"),
            target = job.optString("target"),
            count = job.optInt("count"),
            channel = job.optInt("channel", 0).takeIf { it > 0 },
            prefix = job.optString("prefix"),
        )
        _state.update { current ->
            val list = if (deauth) current.deauthJobs else current.captureJobs
            val existing = list.firstOrNull { it.id == parsed.id }
            val updated = when (state) {
                "running" -> if (existing == null) list + parsed else list.map {
                    if (it.id == parsed.id) parsed.copy(since = it.since) else it
                }
                else -> list.filterNot { it.id == parsed.id }
            }
            if (deauth) current.copy(deauthJobs = updated) else current.copy(captureJobs = updated)
        }
        if (!deauth && state != "running") refreshCaptures()
    }

    private fun refreshCaptures() = EngineClient.send("capture.list")

    private fun notify(text: String, error: Boolean = false, warning: Boolean = false) {
        _state.update { it.copy(notice = Notice(text, error, warning)) }
    }

    private fun appendLog(time: String, level: String, message: String) {
        if (message.isBlank()) return
        val line = LogLine(
            time = time.ifBlank { android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis()).toString() },
            level = level,
            message = message,
        )
        _state.update { it.copy(logs = (it.logs + line).takeLast(400)) }
    }

    // -- parsing ----------------------------------------------------------

    private fun parseIfaces(array: JSONArray?): List<Iface> = array?.let { arr ->
        (0 until arr.length()).mapNotNull { index ->
            val o = arr.optJSONObject(index) ?: return@mapNotNull null
            Iface(
                name = o.optString("name"),
                type = o.optString("type", "unknown"),
                up = o.optBoolean("up"),
                mac = o.optString("mac"),
                uplink = o.optBoolean("uplink"),
                monitorCapable = o.optBoolean("monitor_capable"),
            )
        }
    } ?: emptyList()

    private fun parseAps(array: JSONArray?): List<Ap> = array?.let { arr ->
        (0 until arr.length()).mapNotNull { index ->
            val o = arr.optJSONObject(index) ?: return@mapNotNull null
            Ap(
                bssid = o.optString("bssid"),
                ssid = o.optString("ssid", "(hidden)"),
                channel = if (o.isNull("channel")) null else o.optInt("channel"),
                signal = if (o.isNull("signal")) null else o.optInt("signal"),
                privacy = o.optString("privacy", "OPEN"),
                cipher = o.optString("cipher"),
                auth = o.optString("auth"),
                beacons = o.optInt("beacons"),
                data = o.optInt("data"),
                handshake = o.optBoolean("handshake"),
                pmkid = o.optBoolean("pmkid"),
            )
        }
    } ?: emptyList()

    private fun parseClients(array: JSONArray?): List<Client> = array?.let { arr ->
        (0 until arr.length()).mapNotNull { index ->
            val o = arr.optJSONObject(index) ?: return@mapNotNull null
            Client(
                mac = o.optString("mac"),
                bssid = o.optString("bssid").takeIf { v -> v.isNotBlank() && v != "null" },
                signal = if (o.isNull("signal")) null else o.optInt("signal"),
                packets = o.optInt("packets"),
                probes = o.optString("probes"),
            )
        }
    } ?: emptyList()

    private fun parseJobs(array: JSONArray?): List<Job> = array?.let { arr ->
        (0 until arr.length()).mapNotNull { index ->
            val o = arr.optJSONObject(index) ?: return@mapNotNull null
            Job(
                id = o.optInt("id"),
                kind = o.optString("kind"),
                target = o.optString("target"),
                count = o.optInt("count"),
                channel = o.optInt("channel", 0).takeIf { it > 0 },
                prefix = o.optString("prefix"),
            )
        }
    } ?: emptyList()

    private fun parseCaptures(array: JSONArray?): List<CaptureFile> = array?.let { arr ->
        (0 until arr.length()).mapNotNull { index ->
            val o = arr.optJSONObject(index) ?: return@mapNotNull null
            CaptureFile(
                name = o.optString("name"),
                size = o.optLong("size"),
                modified = o.optLong("modified"),
            )
        }
    } ?: emptyList()
}
