package com.airbombal.app.model

/** A wireless interface reported by the engine. */
data class Iface(
    val name: String,
    val type: String,
    val up: Boolean,
    val mac: String,
    val uplink: Boolean,
    val monitorCapable: Boolean,
)

/** One access point from the passive scan. */
data class Ap(
    val bssid: String,
    val ssid: String,
    val channel: Int?,
    val signal: Int?,
    val privacy: String,
    val cipher: String,
    val auth: String,
    val beacons: Int,
    val data: Int,
    val handshake: Boolean,
    val pmkid: Boolean,
)

/** One client observed by the scan. */
data class Client(
    val mac: String,
    val bssid: String?,
    val signal: Int?,
    val packets: Int,
    val probes: String,
)

/** A running engine job (deauth burst or capture). */
data class Job(
    val id: Int,
    val kind: String,
    val target: String,
    val count: Int = 0,
    val channel: Int? = null,
    val prefix: String = "",
    val since: Long = System.currentTimeMillis(),
)

data class LogLine(
    val time: String,
    val level: String,
    val message: String,
)

data class CaptureFile(
    val name: String,
    val size: Long,
    val modified: Long,
)

data class Notice(
    val text: String,
    val error: Boolean = false,
    val warning: Boolean = false,
)

data class EngineState(
    val connected: Boolean = false,
    val dryRun: Boolean = false,
    val monIface: String? = null,
    val uplink: String = "wlan0",
    val scanning: Boolean = false,
    val ifaces: List<Iface> = emptyList(),
    val aps: List<Ap> = emptyList(),
    val clients: List<Client> = emptyList(),
    val selectedBssid: String? = null,
    val selectedClient: String? = null,
    val deauthJobs: List<Job> = emptyList(),
    val captureJobs: List<Job> = emptyList(),
    val captures: List<CaptureFile> = emptyList(),
    val logs: List<LogLine> = emptyList(),
    val notice: Notice? = null,
) {
    val selectedAp: Ap? get() = aps.firstOrNull { it.bssid == selectedBssid }
    fun clientsFor(bssid: String?): List<Client> =
        clients.filter { bssid != null && it.bssid == bssid }
}
