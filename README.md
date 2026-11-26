**⚠️ DISCLAIMER: FOR AUTHORIZED SECURITY TESTING AND EDUCATIONAL USE ONLY — THE AUTHOR IS NOT RESPONSIBLE FOR ANY MISUSE OR DAMAGE**

<div align="center">

<img src="art/dragon-icon.png" width="128" alt="Airbomb-Al dragon"/>

# Airbomb-Al

**A local-only wireless security audit console for Android + Kali NetHunter.**

[![android](https://img.shields.io/badge/Android-8.0%2B-3BFFA9?style=flat-square&logo=android&logoColor=white)](#requirements)
[![python](https://img.shields.io/badge/Engine-Python%203.9%2B-52E5FF?style=flat-square&logo=python&logoColor=white)](#architecture)
[![nethunter](https://img.shields.io/badge/Kali-NetHunter-FFC857?style=flat-square&logo=kalilinux&logoColor=black)](#install)
[![license](https://img.shields.io/badge/License-MIT-7FA396?style=flat-square)](#license)

</div>

Airbomb-Al turns a NetHunter phone into a self-contained Wi-Fi audit console. A privileged
Python engine owns the radio; a Compose UI is a thin client that talks to it over loopback.
Nothing leaves the device — there is no cloud service, no telemetry and no remote host.

It is a ground-up Android re-imagining of the architecture used by
[Airgorah](https://github.com/martin-olivier/airgorah): a privileged agent that validates every
request, keeps interface state, and streams access points and clients to a passive front end.

---

## Features

| | |
|---|---|
| **Live scan** | 2.4 GHz, 5 GHz or both, with channel display, RSSI bars and per-AP client counts |
| **Adapter control** | Switch any detected radio between **managed** and **monitor** mode from the UI, on demand |
| **Hot-plug detection** | `wlan1`, `wlan2`, USB adapters — the list refreshes automatically, no restarts or flags |
| **Targeted deauth** | Per-AP or per-client bursts with frame count and duration controls, live session list |
| **Handshake capture** | Targeted `airodump-ng` capture, one-tap `aircrack-ng` verification, capture browser |
| **Safety gates** | Deauth only against BSSIDs in the current scan; clients validated against their AP |
| **Local by design** | Engine binds `127.0.0.1:8767` only — no LAN listener, no pairing, no accounts |
| **Audit log** | Every command, refusal and capture is streamed into an in-app log |

> **Switching the internet radio to monitor mode will drop Wi-Fi.** The app asks for
> confirmation when you do this on the uplink interface and shows a warning banner. Switch it
> back to **MANAGED** to restore connectivity.

---

## Architecture

```
┌───────────────────────────── Android phone ─────────────────────────────┐
│                                                                          │
│   ┌──────────────────────┐        NDJSON over TCP        ┌────────────┐  │
│   │  Airbomb-Al APK      │  ───────────────────────────►  │  Engine    │  │
│   │  Jetpack Compose UI  │  ◄───────────────────────────  │  Python    │  │
│   └──────────────────────┘        127.0.0.1:8767         └─────┬──────┘  │
│                                                                 │         │
│                                                     subprocess  │         │
│                                                                 ▼         │
│                              iw · airmon-ng · airodump-ng · aireplay-ng   │
│                                          · aircrack-ng                    │
└──────────────────────────────────────────────────────────────────────────┘
```

* **Engine** (`backend/airbomb_engine.py`) — stdlib-only asyncio TCP server. Owns interface
  state, scan processes, deauth jobs and capture files. Re-reads the kernel on every
  `iface.list`, so hot-plugged adapters appear without restarting anything.
* **UI** (`android/`) — Kotlin/Compose client. Reconnecting NDJSON socket, ordered command
  queue, no privileges of its own.

The full wire protocol is documented in [`backend/protocol.md`](backend/protocol.md).

---

## Requirements

* Kali NetHunter (rooted Android 8.0+) with a wireless adapter that supports monitor mode
* `aircrack-ng`, `iw`, `wireless-tools`, `python3` — installed automatically by `install.sh`
* For building: JDK 17+, Android SDK 34, Gradle 8.9 (or the bundled wrapper)

---

## Install

### One command (NetHunter)

```bash
git clone https://github.com/<your-user>/Airbomb-Al.git
cd Airbomb-Al
sudo ./install.sh
```

`install.sh` installs the wireless tooling, deploys the engine to `/opt/Airbomb-Al`, installs
the `airbomb-engine` service wrapper, copies the APK to `/sdcard/Download/Airbomb-Al.apk` and
starts the engine on `127.0.0.1:8767`. It **never** changes your radio mode.

From a release bundle (`Airbomb-Al.apk` + `backend/` + `install.sh` in one folder) the same
command works offline.

### Engine control

```bash
airbomb-engine start      # background engine
airbomb-engine status     # pid + endpoint
airbomb-engine logs       # follow logs
airbomb-engine stop
```

### Install the APK

```bash
adb install -r /sdcard/Download/Airbomb-Al.apk
# or open /sdcard/Download in a file manager and tap the APK
```

---

## First run

1. Read the disclaimer and tick the acknowledgement.
2. The header shows **ENGINE ONLINE** once the loopback socket connects. If it says offline,
   run `airbomb-engine start`.
3. Open **SCAN → Radio**. Every detected adapter is listed with its mode.
4. Tap **MONITOR** on the adapter you want to scan with. If it is your internet radio, confirm
   the warning.
5. Choose a band and tap **START SCAN**. Access points stream in as they are heard.
6. Tap an AP to select it, then use **DEAUTH** to force a reconnect and **CAPTURE** to record
   the handshake and verify it with `aircrack-ng`.
7. When finished, stop the scan and tap **MANAGED** on the adapter to restore normal Wi-Fi.

---

## Build from source

```bash
./scripts/build_apk.sh        # debug → dist/Airbomb-Al-debug.apk
./scripts/build_release.sh    # signed → dist/Airbomb-Al.apk
```

`build_release.sh` creates `android/airbomb-release.jks` and `keystore.properties` on first run.
**Back them up** — updates to the same package must be signed with the same key. Both files are
gitignored.

For an offline NetHunter build, pass `--offline` (Gradle cache + `aapt2` from build-tools 34.0.0).

### GitHub releases

`.github/workflows/android.yml` runs the engine tests, builds a release APK on every push, and
attaches `Airbomb-Al.apk` to a GitHub Release when you push a `v*` tag:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

CI builds are signed with the debug key unless you provide `ANDROID_KEYSTORE_BASE64`,
`ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` repository secrets.

---

## Safety model

Airbomb-Al is deliberately narrow:

* **Loopback only.** The engine binds `127.0.0.1`; there is no option to expose it.
* **Target validation.** `deauth.start` and `capture.start` reject any BSSID that is not in the
  latest scan. A client MAC must be observed associated with that BSSID.
* **Bounded sessions.** Deauth bursts are capped at 64 frames and 120 seconds, then self-stop.
* **Explicit mode changes.** Monitor mode is only entered when you tap **MONITOR** and confirm.
  The engine never flips a radio on its own, and `monitor.disable` restores managed mode and
  brings the link back up.
* **No credential harvesting.** Airbomb-Al captures handshakes for verification; it does not run
  captive portals, phishing pages or credential collectors.

You are still responsible for what you transmit. See the disclaimer at the top.

---

## Project layout

```
Airbomb-Al/
├── install.sh                  # one-command NetHunter installer
├── backend/
│   ├── airbomb_engine.py       # local NDJSON engine (stdlib only)
│   ├── protocol.md             # command/event reference
│   ├── requirements.txt
│   └── test_engine.py          # 22 mocked unit tests, no radio access
├── android/
│   └── app/src/main/java/com/airbombal/app/
│       ├── MainActivity.kt
│       ├── AirbombViewModel.kt
│       ├── net/EngineClient.kt
│       ├── model/Models.kt
│       └── ui/                 # theme, components, screens
├── scripts/
│   ├── build_apk.sh
│   ├── build_release.sh
│   └── make_icon.py            # regenerates the dragon artwork
├── art/dragon-icon.png
└── dist/                       # build output (APK)
```

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| Header says **ENGINE OFFLINE** | `airbomb-engine start`, then tap the refresh icon |
| No adapters listed | Plug the adapter in and tap the refresh icon in **SCAN → Radio** |
| **START SCAN** is disabled | No monitor interface yet — tap **MONITOR** on an adapter |
| Wi-Fi/internet dropped | Your uplink radio is in monitor mode — tap **MANAGED** on it |
| No handshake found | Run a short deauth burst so a client reconnects, then **CHECK HANDSHAKE** |
| `airmon-ng` fails | The engine falls back to `iw dev <if> set type monitor` automatically |
| Port 8767 already in use | Another local engine is running — stop it, then `airbomb-engine restart` |

---

## Legal

Airbomb-Al is provided for **authorized security testing and education only**. Using it against
networks you do not own or have explicit written permission to test is illegal in most
jurisdictions. The author assumes no liability for misuse or damage.

---

## Credits

* [Airgorah](https://github.com/martin-olivier/airgorah) — architecture reference for the
  privileged-agent model, scan plan and handshake workflow.
* [aircrack-ng](https://www.aircrack-ng.org/) — the wireless tooling the engine drives.
* Jetpack Compose, Material 3.

## License

MIT — see [`LICENSE`](LICENSE).
