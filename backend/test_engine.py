#!/usr/bin/env python3
"""Unit tests for the Airbomb-Al engine.

SAFETY: every test runs with `ae.run` and `subprocess.run` patched. A test that
tries to touch the real radio fails loudly instead of changing interface state.
No monitor-mode command is ever executed against the host.
"""

from __future__ import annotations

import asyncio
import os
import tempfile
import unittest
from unittest import mock

import airbomb_engine as ae

CSV = """BSSID, First time seen, Last time seen, channel, Speed, Privacy, Cipher, Authentication, Power, # beacons, # IV, LAN IP, ID-length, ESSID, Key
AA:BB:CC:11:22:33, 2026-01-01 10:00:00, 2026-01-01 10:00:10,  6,  54, WPA2, CCMP, PSK, -41,       120,        4,   0.0.0.0,         12, DragonNet,

Station MAC, First time seen, Last time seen, Power, # packets, BSSID, Probed ESSIDs
DE:AD:BE:EF:00:01, 2026-01-01 10:00:00, 2026-01-01 10:00:09, -55,       14, AA:BB:CC:11:22:33, DragonNet
"""

ALL_TOOLS = {"iw", "airmon-ng", "airodump-ng", "aireplay-ng", "aircrack-ng"}


def iface(name: str, kind: str = "managed", up: bool = True, uplink: bool = False) -> dict:
    return {
        "name": name,
        "type": kind,
        "up": up,
        "mac": "AA:BB:CC:00:00:01",
        "uplink": uplink,
        "monitor_capable": not uplink,
    }


class RadioHarness(unittest.TestCase):
    """An Engine whose radio is completely fake."""

    dry_run = True

    def setUp(self) -> None:
        self.tmp = tempfile.mkdtemp()
        self.ifaces = [iface("wlan0", uplink=True), iface("wlan1", up=False)]
        self.route = "wlan0"
        self.tools = set(ALL_TOOLS)
        self.calls: list[list[str]] = []
        self.outputs: dict[tuple[str, ...], tuple[int, str]] = {}
        self.on_run = None

        self._patches = [
            mock.patch.object(ae, "have", side_effect=lambda t: t in self.tools),
            mock.patch.object(ae, "run", side_effect=self._fake_run),
            mock.patch.object(ae.subprocess, "run",
                              side_effect=AssertionError("real subprocess call in tests")),
            mock.patch.object(ae.Engine, "interfaces", side_effect=lambda: self.ifaces),
            mock.patch.object(ae.Engine, "default_route_iface", side_effect=lambda: self.route),
        ]
        for patch in self._patches:
            patch.start()

    def tearDown(self) -> None:
        for patch in reversed(self._patches):
            patch.stop()

    def _fake_run(self, cmd, timeout=20):
        self.calls.append(list(cmd))
        key = tuple(cmd[:2])
        if self.on_run is not None:
            self.on_run(cmd)
        return self.outputs.get(key, (0, ""))

    def make(self, dry_run: bool | None = None, iface_arg: str | None = None) -> ae.Engine:
        argv = ["--workdir", self.tmp]
        if (self.dry_run if dry_run is None else dry_run):
            argv.append("--dry-run")
        if iface_arg:
            argv += ["--iface", iface_arg]
        return ae.Engine(ae.parse_args(argv))


class MacTests(unittest.TestCase):
    def test_accepts_valid(self):
        self.assertEqual(ae.norm_mac("aa:bb:cc:dd:ee:ff"), "AA:BB:CC:DD:EE:FF")
        self.assertEqual(ae.norm_mac("AA-BB-CC-DD-EE-FF"), "AA:BB:CC:DD:EE:FF")

    def test_rejects_invalid(self):
        for bad in ("", "nope", "AA:BB:CC:DD:EE", "GG:BB:CC:DD:EE:FF", None, 42):
            self.assertIsNone(ae.norm_mac(bad), bad)


class CsvTests(RadioHarness):
    def test_parses_aps_and_clients(self):
        eng = self.make()
        eng.scan_prefix = os.path.join(eng.captures, "scan-test")
        with open(eng.scan_prefix + "-01.csv", "w", encoding="utf-8") as fh:
            fh.write(CSV)
        eng._read_csv()
        ap = eng.aps["AA:BB:CC:11:22:33"]
        self.assertEqual(ap.ssid, "DragonNet")
        self.assertEqual(ap.channel, 6)
        self.assertEqual(ap.signal, -41)
        self.assertEqual(ap.privacy, "WPA2")
        client = eng.clients["DE:AD:BE:EF:00:01"]
        self.assertEqual(client.bssid, "AA:BB:CC:11:22:33")
        self.assertEqual(client.packets, 14)

    def test_missing_file_is_harmless(self):
        eng = self.make()
        eng.scan_prefix = os.path.join(eng.captures, "nope")
        eng._read_csv()
        self.assertEqual(eng.aps, {})


class MonitorModeTests(RadioHarness):
    """Monitor switching is exercised against mocks only."""

    def test_enable_in_dry_run_touches_nothing(self):
        eng = self.make(dry_run=True)
        out = asyncio.run(eng.cmd_monitor_enable({"iface": "wlan0"}))
        self.assertEqual(out["mon_iface"], "wlan0mon")
        self.assertIsNotNone(out["warning"], "uplink switch must warn")
        self.assertEqual(self.calls, [], "dry run must not invoke any tool")

    def test_enable_uplink_via_airmon_ng_is_allowed_with_warning(self):
        def rename(cmd):
            if cmd[:2] == ["airmon-ng", "start"]:
                self.ifaces = [iface("wlan0mon", "monitor"), iface("wlan1", up=False)]
                self.route = None  # internet drops while in monitor mode

        self.outputs[("airmon-ng", "start")] = (0, "monitor mode enabled on wlan0mon)")
        self.on_run = rename
        eng = self.make(dry_run=False)
        out = asyncio.run(eng.cmd_monitor_enable({"iface": "wlan0"}))
        self.assertEqual(out["mon_iface"], "wlan0mon")
        self.assertIn("internet will drop", out["warning"])
        self.assertIn(["airmon-ng", "start", "wlan0"], self.calls)
        self.assertEqual(eng.mon_iface, "wlan0mon")

    def test_enable_falls_back_to_iw(self):
        self.tools.discard("airmon-ng")
        self.outputs[("iw", "dev")] = (0, "")
        eng = self.make(dry_run=False)

        def flip(cmd):
            if cmd[:4] == ["iw", "dev", "wlan1", "set"]:
                self.ifaces = [iface("wlan0", uplink=True), iface("wlan1", "monitor")]

        self.on_run = flip
        out = asyncio.run(eng.cmd_monitor_enable({"iface": "wlan1"}))
        self.assertEqual(out["mon_iface"], "wlan1")
        self.assertIn(["iw", "dev", "wlan1", "set", "type", "monitor"], self.calls)

    def test_enable_rejects_missing_adapter(self):
        eng = self.make(dry_run=False)
        with self.assertRaises(ae.CommandError):
            asyncio.run(eng.cmd_monitor_enable({"iface": "wlan9"}))

    def test_disable_restores_managed(self):
        def restore(cmd):
            if cmd[:2] == ["airmon-ng", "stop"]:
                self.ifaces = [iface("wlan0", uplink=True), iface("wlan1", up=False)]

        self.outputs[("airmon-ng", "stop")] = (0, "")
        self.on_run = restore
        eng = self.make(dry_run=False)
        eng.mon_iface = "wlan0mon"
        self.ifaces = [iface("wlan0mon", "monitor"), iface("wlan1", up=False)]
        out = asyncio.run(eng.cmd_monitor_disable({}))
        self.assertIsNone(out["mon_iface"])
        self.assertIn(["airmon-ng", "stop", "wlan0mon"], self.calls)

    def test_disable_falls_back_to_iw(self):
        self.tools.discard("airmon-ng")
        eng = self.make(dry_run=False)
        eng.mon_iface = "wlan1"
        self.ifaces = [iface("wlan0", uplink=True), iface("wlan1", "monitor")]

        def flip(cmd):
            if cmd[:4] == ["iw", "dev", "wlan1", "set"]:
                self.ifaces = [iface("wlan0", uplink=True), iface("wlan1")]

        self.on_run = flip
        asyncio.run(eng.cmd_monitor_disable({}))
        self.assertIn(["iw", "dev", "wlan1", "set", "type", "managed"], self.calls)

    def test_disable_refused_while_scanning(self):
        eng = self.make(dry_run=False)
        eng.mon_iface = "wlan0mon"
        eng.scanning = True
        with self.assertRaises(ae.CommandError):
            asyncio.run(eng.cmd_monitor_disable({}))


class InterfaceDetectionTests(RadioHarness):
    def test_auto_detects_uplink_from_default_route(self):
        eng = self.make(iface_arg=None)
        self.assertEqual(eng.uplink, "wlan0")

    def test_explicit_override_wins(self):
        eng = self.make(iface_arg="wlan1")
        self.assertEqual(eng.uplink, "wlan1")

    def test_hotplugged_adapter_appears(self):
        eng = self.make()
        self.ifaces = self.ifaces + [iface("wlan2", up=True)]
        data = asyncio.run(eng.cmd_iface_list({}))
        names = [item["name"] for item in data["interfaces"]]
        self.assertIn("wlan2", names)
        self.assertTrue(data["auto"])

    def test_uplink_follows_default_route_change(self):
        eng = self.make()
        self.route = "wlan1"
        data = asyncio.run(eng.cmd_iface_list({}))
        self.assertEqual(data["uplink"], "wlan1")


class ScanAndAttackTests(RadioHarness):
    def test_scan_needs_monitor(self):
        eng = self.make(dry_run=False)
        eng.mon_iface = None
        self.ifaces = [iface("wlan0", uplink=True)]
        with self.assertRaises(ae.CommandError):
            asyncio.run(eng.cmd_scan_start({}))

    def test_bad_band(self):
        eng = self.make(dry_run=True)
        with self.assertRaises(ae.CommandError):
            asyncio.run(eng.cmd_scan_start({"band": "z"}))

    def test_deauth_requires_scanned_target(self):
        eng = self.make(dry_run=True)
        with self.assertRaises(ae.CommandError):
            asyncio.run(eng.cmd_deauth_start({"bssid": "AA:BB:CC:11:22:33"}))

    def test_deauth_rejects_client_of_other_ap(self):
        eng = self.make(dry_run=True)
        asyncio.run(eng.cmd_scan_start({}))
        with self.assertRaises(ae.CommandError):
            asyncio.run(eng.cmd_deauth_start(
                {"bssid": "AA:BB:CC:11:22:33", "client": "DE:AD:BE:EF:00:02"}))

    def test_deauth_allows_known_pair(self):
        eng = self.make(dry_run=True)
        asyncio.run(eng.cmd_scan_start({}))
        out = asyncio.run(eng.cmd_deauth_start(
            {"bssid": "AA:BB:CC:11:22:33", "client": "DE:AD:BE:EF:00:01", "duration": 3}))
        self.assertEqual(out["job"]["target"], "AA:BB:CC:11:22:33")
        asyncio.run(eng.cmd_deauth_stop_all({}))

    def test_deauth_rejects_bad_mac(self):
        eng = self.make(dry_run=True)
        with self.assertRaises(ae.CommandError):
            asyncio.run(eng.cmd_deauth_start({"bssid": "not-a-mac"}))

    def test_scan_start_stop_dry_run(self):
        eng = self.make(dry_run=True)
        started = asyncio.run(eng.cmd_scan_start({"band": "bg"}))
        self.assertTrue(started["scanning"])
        self.assertEqual(self.calls, [])
        stopped = asyncio.run(eng.cmd_scan_stop({}))
        self.assertFalse(stopped["scanning"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
