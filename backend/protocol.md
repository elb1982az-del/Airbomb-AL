# Airbomb-Al engine protocol

Transport: **TCP on `127.0.0.1:8767`**, one JSON object per line (NDJSON), UTF-8.
The socket is bound to loopback only. The Android app is the only intended client.

## Requests

```json
{"id": 7, "cmd": "scan.start", "band": "abg"}
```

`id` is optional and echoed back verbatim.

## Replies

```json
{"id": 7, "ok": true, "data": {"scanning": true, "prefix": "/opt/Airbomb-Al/backend/captures/scan-20260101-120000"}}
{"id": 8, "ok": false, "error": "target is not in the current scan; rescan and try again"}
```

## Events (unsolicited, no `id`)

| event | payload |
|---|---|
| `hello` | `version`, `dry_run`, `mon_iface`, `uplink` |
| `log` | `time`, `level`, `message` |
| `scan` | `scanning`, `aps[]`, `clients[]` |
| `deauth` | `job{...}`, `state` = `running` \| `finished` \| `stopped` |
| `capture` | `job{...}`, `state` |
| `handshake` | `bssid`, `file` |

## Commands

### Discovery

| cmd | args | returns |
|---|---|---|
| `hello` | — | version, tool availability, monitor/uplink interface |
| `ping` | — | `pong` |
| `state` | — | monitor iface, scanning flag, active jobs |
| `iface.list` | — | `interfaces[]` (`name`, `type`, `up`, `mac`, `uplink`, `monitor_capable`), `default_route`, `auto` |
| `log.get` | `limit?` | `lines[]` |

### Monitor mode

| cmd | args | notes |
|---|---|---|
| `monitor.enable` | `iface` | runs `airmon-ng start`, falls back to `iw dev <if> set type monitor`; any detected adapter is allowed. Switching the uplink returns a `warning` because internet will drop until it is switched back |
| `monitor.disable` | `iface?` | runs `airmon-ng stop`, falls back to `iw … set type managed`, brings the link back up; refused while scanning |

### Scanning

| cmd | args | notes |
|---|---|---|
| `scan.start` | `band?` (`a`/`b`/`g`/`ab`/`bg`/`abg`/`2.4`/`5`), `channel?` | spawns `airodump-ng`, streams `scan` events |
| `scan.stop` | — | kills the scan process |
| `scan.snapshot` | — | one-shot `{scanning, mon_iface, aps[], clients[]}` |

`interfaces[]` is re-read from the kernel on every call, so hot-plugged adapters
(`wlan1`, `wlan2`, USB radios) appear without restarting the engine. `uplink` follows
the current default route unless `--iface` was passed explicitly.

`aps[]`: `bssid, ssid, channel, signal, privacy, cipher, auth, beacons, data, handshake, pmkid, last_seen`
`clients[]`: `mac, bssid, signal, packets, probes, last_seen`

### Deauthentication

| cmd | args |
|---|---|
| `deauth.start` | `bssid` (required), `client?`, `count?` (1–64, default 8), `duration?` (3–120 s, default 20) |
| `deauth.stop` | `id` |
| `deauth.stop_all` | — |

Guards: the BSSID must exist in the **current** scan; a client must be observed
associated with that BSSID; the radio must be a monitor interface that is not the
uplink. Sessions self-terminate after `duration`.

### Handshake capture

| cmd | args |
|---|---|
| `capture.start` | `bssid` (required), `channel?` |
| `capture.stop` | `bssid?` (omit to stop all) |
| `capture.check` | `bssid` |
| `capture.list` | — |

`capture.start` runs a targeted `airodump-ng`. `capture.check` runs `aircrack-ng`
against the newest `.cap` and reports `handshake: true/false`.

### Lifecycle

`{"cmd": "shutdown"}` stops every child process and exits the engine.
