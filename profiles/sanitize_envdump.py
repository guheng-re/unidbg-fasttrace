#!/usr/bin/env python3
"""Make an EnvDump fingerprint loadable by unidbg.

Rules:
  - Never invent device values (no fake ifindex/IPv4/UUID/Build).
  - Overlay files next to the JSON may supply values EnvDump already collected.
  - If a unidbg-required field is still missing or invalid, drop that object.
  - --remap-package copies identity from android.packages[] in the dump itself.

Usage:
  python sanitize_envdump.py --in envdump.zip --out device-fingerprint.json \\
      --overlay files --remap-package com.MobileTicket
"""
from __future__ import annotations

import argparse
import json
import re
import zipfile
from pathlib import Path
from typing import Any

MAC_RE = re.compile(r"(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")
FEATURE_NAME_RE = re.compile(r"^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)*$")
IPV4_RE = re.compile(r"^(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)$")
OPER_STATES = {
    "unknown", "notpresent", "down", "lowerlayerdown", "testing", "dormant", "up",
}
WIFI_KEYS = {
    "enabled", "ssid", "bssid", "macAddress", "ipv4",
    "rssi", "linkSpeedMbps", "frequencyMhz", "networkId", "state", "scanResults",
}
BT_KEYS = {"name", "address", "enabled", "state", "scanMode", "discovering"}
CAP_KEYS = {"transportTypes", "networkCapabilities"}
IFACE_OPTIONAL = {
    "broadcast", "flags", "mac", "mtu", "displayName", "virtual", "operState",
    "carrier", "hardwareType", "speedMbps", "duplex", "linkIndex", "txQueueLen",
    "addressAssignType",
}
STATFS_MAP = {
    "bsize": "blockSize",
    "bfree": "blocksFree",
    "bavail": "blocksAvailable",
    "ffree": "filesFree",
    "type": "type",
    "blocks": "blocks",
    "files": "files",
    "fsid": "fsid",
    "nameLength": "nameLength",
    "fragmentSize": "fragmentSize",
    "flags": "flags",
    "blockSize": "blockSize",
    "blocksFree": "blocksFree",
    "blocksAvailable": "blocksAvailable",
    "filesFree": "filesFree",
}
STAT_KEEP = {
    "device", "inode", "mode", "uid", "gid", "size", "blockSize", "blocks",
    "atimeMillis", "mtimeMillis", "ctimeMillis",
}
SYSINFO_REQUIRED = (
    "uptime", "loads", "totalRam", "freeRam", "sharedRam", "bufferRam",
    "totalSwap", "freeSwap", "procs", "memUnit",
)
PROC_KEYS = {
    "state", "tracerPid", "threadCount", "cmdline", "cgroups",
    "startTimeTicks", "virtualMemoryBytes", "residentSetPages",
    "rchar", "wchar", "syscr", "syscw", "readBytes", "writeBytes", "cancelledWriteBytes",
    "bootId", "randomUuid", "entropyAvail", "randomPoolSize",
    "writeWakeupThreshold", "urandomMinReseedSecs",
    "oomScoreAdj", "oomScore", "oomAdj", "selinuxContext", "fileSelinuxContexts",
    "comm", "wchan", "dumpable", "nice", "seccompMode", "noNewPrivs", "capEffectiveHex",
    "capInheritableHex", "capPermittedHex", "capBoundingHex", "capAmbientHex",
    "signalBlockedHex", "signalIgnoredHex", "signalCaughtHex", "limits",
}
PROCESS_LIST_KEYS = {"pid", "cmdline", "comm", "exe"}
GRAPHICS_KEYS = {
    "vendor", "renderer", "version", "shadingLanguageVersion", "extensions",
    "eglVendor", "eglVersion", "eglExtensions",
}
IDENT_KEYS = {"advertisingId", "limitAdTracking", "androidId", "appSetId", "appSetScope"}
SENSOR_MAP_KEYS = ("names", "vendors", "versions")


class Sanitizer:
    def __init__(self, overlay: Path | None) -> None:
        self.overlay = overlay
        self.skipped: list[dict[str, str]] = []

    def skip(self, path: str, reason: str) -> None:
        self.skipped.append({"path": path, "reason": reason})

    def overlay_text(self, rel: str) -> str | None:
        if self.overlay is None:
            return None
        p = self.overlay.joinpath(*rel.lstrip("/").split("/"))
        if not p.is_file():
            return None
        try:
            return p.read_text(encoding="utf-8", errors="replace")
        except OSError:
            return None

    def parse_ifindex_map(self) -> dict[str, int]:
        text = self.overlay_text("proc/net/if_inet6")
        out: dict[str, int] = {}
        if not text:
            return out
        for line in text.splitlines():
            parts = line.split()
            if len(parts) < 6:
                continue
            try:
                idx = int(parts[1], 16)
            except ValueError:
                continue
            name = parts[5]
            if idx >= 1 and name:
                out[name] = idx
        return out

    def parse_route_ipv4(self) -> dict[str, str]:
        text = self.overlay_text("proc/net/route")
        out: dict[str, str] = {}
        if not text:
            return out
        lines = text.splitlines()
        if len(lines) < 2:
            return out
        for line in lines[1:]:
            cols = line.split()
            if len(cols) < 8:
                continue
            iface, dest_hex, _gw, _flags, _ref, _use, _metric, mask_hex = cols[:8]
            if dest_hex == "00000000" and mask_hex == "00000000":
                continue
            if dest_hex != "00000000":
                continue
            # default route has dest 0; local subnet rows have dest != 0
        # Prefer a non-zero destination on the iface as its network; still not a host IP.
        # /proc/net/route does not give the interface address. Skip inventing one.
        return out

    def keep_mac(self, value: Any) -> str | None:
        if not isinstance(value, str) or not MAC_RE.match(value.strip()):
            return None
        return value.strip().lower()

    def keep_ipv4(self, value: Any) -> str | None:
        if not isinstance(value, str) or not IPV4_RE.match(value.strip()):
            return None
        return value.strip()

    def android_ip_to_dotted(self, value: Any) -> str | None:
        if not isinstance(value, int) or isinstance(value, bool):
            return None
        n = value & 0xFFFFFFFF
        # WifiInfo.getIpAddress is little-endian on Android.
        dotted = "%d.%d.%d.%d" % (n & 255, (n >> 8) & 255, (n >> 16) & 255, (n >> 24) & 255)
        return dotted if IPV4_RE.match(dotted) else None

    def as_int(self, value: Any, lo: int | None = None, hi: int | None = None) -> int | None:
        if isinstance(value, bool):
            return None
        if isinstance(value, int):
            n = value
        elif isinstance(value, str) and re.fullmatch(r"-?\d+", value.strip()):
            n = int(value.strip())
        else:
            return None
        if lo is not None and n < lo:
            return None
        if hi is not None and n > hi:
            return None
        return n

    def sanitize_interfaces(self, ifaces: Any) -> list[dict[str, Any]] | None:
        if not isinstance(ifaces, list):
            self.skip("network.interfaces", "not an array")
            return None
        index_map = self.parse_ifindex_map()
        kept: list[dict[str, Any]] = []
        seen_name: set[str] = set()
        seen_idx: set[int] = set()
        for i, raw in enumerate(ifaces):
            path = "network.interfaces[%d]" % i
            if not isinstance(raw, dict):
                self.skip(path, "not an object")
                continue
            name = raw.get("name")
            if not isinstance(name, str) or not name or name != name.strip() or len(name.encode("utf-8")) > 15:
                self.skip(path + ".name", "missing/invalid")
                continue
            if name in seen_name:
                self.skip(path + ".name", "duplicate")
                continue
            idx = self.as_int(raw.get("index"), 1, 2**31 - 1)
            if idx is None:
                idx = index_map.get(name)
            if idx is None:
                self.skip(path, "no index in dump or overlay if_inet6")
                continue
            if idx in seen_idx:
                self.skip(path + ".index", "duplicate")
                continue
            ipv4 = self.keep_ipv4(raw.get("ipv4"))
            if ipv4 is None:
                self.skip(path, "no ipv4 in dump")
                continue
            item: dict[str, Any] = {"name": name, "index": idx, "ipv4": ipv4}
            mac = self.keep_mac(raw.get("mac"))
            if mac:
                item["mac"] = mac
            elif "mac" in raw:
                self.skip(path + ".mac", "invalid, dropped")
            mtu = self.as_int(raw.get("mtu"), 68, 65536)
            if mtu is not None:
                item["mtu"] = mtu
            elif "mtu" in raw:
                self.skip(path + ".mtu", "invalid, dropped")
            st = raw.get("operState")
            if isinstance(st, str) and st in OPER_STATES:
                item["operState"] = st
            elif "operState" in raw:
                self.skip(path + ".operState", "invalid, dropped")
            if isinstance(raw.get("carrier"), bool):
                item["carrier"] = raw["carrier"]
            for key in IFACE_OPTIONAL:
                if key in item or key not in raw:
                    continue
                if key in ("mac", "mtu", "operState", "carrier"):
                    continue
                # unknown extra collected keys: only keep if already optional and typed
                self.skip(path + "." + key, "not mapped, dropped")
            seen_name.add(name)
            seen_idx.add(idx)
            kept.append(item)
        if not kept:
            self.skip("network.interfaces", "empty after drop")
            return None
        return kept

    def sanitize_wifi(self, wifi: Any) -> dict[str, Any] | None:
        if not isinstance(wifi, dict):
            self.skip("network.wifi", "not an object")
            return None
        out: dict[str, Any] = {}
        if isinstance(wifi.get("enabled"), bool):
            out["enabled"] = wifi["enabled"]
        if isinstance(wifi.get("ssid"), str) and wifi["ssid"].strip() and wifi["ssid"] == wifi["ssid"].strip():
            out["ssid"] = wifi["ssid"]
        bssid = self.keep_mac(wifi.get("bssid"))
        if bssid:
            out["bssid"] = bssid
        mac = self.keep_mac(wifi.get("macAddress")) or self.keep_mac(wifi.get("mac"))
        if mac:
            out["macAddress"] = mac
        ipv4 = self.keep_ipv4(wifi.get("ipv4")) or self.android_ip_to_dotted(wifi.get("ip"))
        if ipv4:
            out["ipv4"] = ipv4
        rssi = self.as_int(wifi.get("rssi"), -127, 0)
        if rssi is not None:
            out["rssi"] = rssi
        for key in ("linkSpeedMbps", "frequencyMhz"):
            n = self.as_int(wifi.get(key), 0, 100000)
            if n is not None:
                out[key] = n
        nid = self.as_int(wifi.get("networkId"), -1, 2**31 - 1)
        if nid is not None:
            out["networkId"] = nid
        st = self.as_int(wifi.get("state"), 0, 4)
        if st is not None:
            out["state"] = st
        for key in wifi:
            mapped = "macAddress" if key == "mac" else "ipv4" if key == "ip" else key
            if mapped not in WIFI_KEYS:
                self.skip("network.wifi." + key, "not a unidbg key, dropped")
        return out or None

    def sanitize_bluetooth(self, bt: Any) -> dict[str, Any] | None:
        if not isinstance(bt, dict):
            self.skip("network.bluetooth", "not an object")
            return None
        out: dict[str, Any] = {}
        if isinstance(bt.get("name"), str) and bt["name"].strip():
            out["name"] = bt["name"]
        addr = self.keep_mac(bt.get("address"))
        if addr:
            out["address"] = addr
        elif "address" in bt:
            self.skip("network.bluetooth.address", "invalid, dropped")
        if isinstance(bt.get("enabled"), bool):
            out["enabled"] = bt["enabled"]
        for key in bt:
            if key not in BT_KEYS:
                self.skip("network.bluetooth." + key, "not a unidbg key, dropped")
        return out or None

    def sanitize_capabilities(self, cap: Any) -> dict[str, Any] | None:
        if not isinstance(cap, dict):
            self.skip("network.capabilities", "not an object")
            return None
        out: dict[str, Any] = {}
        for key in CAP_KEYS:
            raw = cap.get(key)
            if isinstance(raw, list) and all(isinstance(x, int) and not isinstance(x, bool) for x in raw):
                out[key] = raw
        for key in cap:
            if key not in CAP_KEYS:
                self.skip("network.capabilities." + key, "not a unidbg key, dropped")
        return out

    def sanitize_network(self, net: Any) -> dict[str, Any] | None:
        if not isinstance(net, dict):
            return None
        out: dict[str, Any] = {}
        if "interfaces" in net:
            ifaces = self.sanitize_interfaces(net["interfaces"])
            if ifaces:
                out["interfaces"] = ifaces
        if "wifi" in net:
            wifi = self.sanitize_wifi(net["wifi"])
            if wifi:
                out["wifi"] = wifi
        if "bluetooth" in net:
            bt = self.sanitize_bluetooth(net["bluetooth"])
            if bt:
                out["bluetooth"] = bt
        if "capabilities" in net:
            cap = self.sanitize_capabilities(net["capabilities"])
            if cap is not None:
                out["capabilities"] = cap
        for key in net:
            if key not in ("interfaces", "wifi", "bluetooth", "capabilities"):
                # Extra sections (tcp/routes/...) only keep if they already match schema.
                # EnvDump did not emit them; drop unknown.
                self.skip("network." + key, "not produced in a unidbg-valid form, dropped")
        return out or None

    def sanitize_statfs(self, statfs: Any) -> dict[str, Any] | None:
        if not isinstance(statfs, dict):
            return None
        out: dict[str, Any] = {}
        for mount, entry in statfs.items():
            path = 'filesystem.statfs["%s"]' % mount
            if not isinstance(entry, dict) or not mount.startswith("/"):
                self.skip(path, "invalid")
                continue
            mapped: dict[str, Any] = {}
            for src, dst in STATFS_MAP.items():
                if src not in entry:
                    continue
                n = self.as_int(entry[src], 0, None)
                if n is None:
                    self.skip(path + "." + src, "not an int, dropped")
                    continue
                mapped[dst] = n
            for key in entry:
                if key not in STATFS_MAP:
                    self.skip(path + "." + key, "not a unidbg key, dropped")
            if not mapped:
                self.skip(path, "no valid fields")
                continue
            out[mount] = mapped
        return out or None

    def sanitize_stat(self, stat: Any) -> dict[str, Any] | None:
        if not isinstance(stat, dict):
            return None
        out: dict[str, Any] = {}
        for pth, entry in stat.items():
            path = 'filesystem.stat["%s"]' % pth
            if not isinstance(entry, dict) or not pth.startswith("/"):
                self.skip(path, "invalid")
                continue
            mapped: dict[str, Any] = {}
            for key, val in entry.items():
                if key == "name":
                    self.skip(path + ".name", "not a unidbg stat key, dropped")
                    continue
                if key == "mtime" and "mtimeMillis" not in entry:
                    n = self.as_int(val, 0, None)
                    if n is None:
                        self.skip(path + ".mtime", "invalid")
                        continue
                    mapped["mtimeMillis"] = n if n >= 10**12 else n * 1000
                    continue
                if key not in STAT_KEEP:
                    self.skip(path + "." + key, "not a unidbg stat key, dropped")
                    continue
                n = self.as_int(val, 0, None)
                if n is None:
                    self.skip(path + "." + key, "not an int, dropped")
                    continue
                mapped[key] = n
            if not mapped:
                self.skip(path, "no valid fields")
                continue
            out[pth] = mapped
        return out or None

    def sanitize_sensors(self, sensors: Any) -> dict[str, Any] | None:
        if not isinstance(sensors, dict):
            return None
        types_raw = sensors.get("types")
        types: list[int] = []
        if isinstance(types_raw, list):
            seen: set[int] = set()
            for t in types_raw:
                n = self.as_int(t, 1, 65535)
                if n is None:
                    self.skip("android.sensors.types", "type %s out of 1..65535, dropped" % t)
                    continue
                if n in seen:
                    continue
                seen.add(n)
                types.append(n)
        out: dict[str, Any] = {}
        if types:
            out["types"] = types
        allowed = set(types)
        for map_key in SENSOR_MAP_KEYS:
            raw = sensors.get(map_key)
            if not isinstance(raw, dict):
                if map_key in sensors:
                    self.skip("android.sensors." + map_key, "must be a map of type->value")
                continue
            kept: dict[str, Any] = {}
            for k, v in raw.items():
                try:
                    tk = int(k)
                except (TypeError, ValueError):
                    self.skip("android.sensors.%s.%s" % (map_key, k), "bad type key")
                    continue
                if tk not in allowed:
                    self.skip("android.sensors.%s.%s" % (map_key, k), "type not in 1..65535 types")
                    continue
                kept[str(tk)] = v
            if kept:
                out[map_key] = kept
        for key in sensors:
            if key not in ("types",) + SENSOR_MAP_KEYS:
                self.skip("android.sensors." + key, "not mapped, dropped")
        return out or None

    def sanitize_sysinfo(self, info: Any) -> dict[str, Any] | None:
        if not isinstance(info, dict):
            return None
        for key in SYSINFO_REQUIRED:
            if key not in info:
                self.skip("linux.sysinfo", "missing required %s, dropped section" % key)
                return None
        return info

    def sanitize_graphics(self, g: Any) -> dict[str, Any] | None:
        if not isinstance(g, dict):
            return None
        out: dict[str, Any] = {}
        for key, val in g.items():
            if key not in GRAPHICS_KEYS:
                self.skip("graphics." + key, "not a unidbg key")
                continue
            if isinstance(val, str) and val.strip().lower() == "skip":
                self.skip("graphics." + key, "collector placeholder, dropped")
                continue
            out[key] = val
        return out or None

    def sanitize_input_methods(self, arr: Any) -> list[dict[str, Any]] | None:
        if not isinstance(arr, list):
            return None
        kept: list[dict[str, Any]] = []
        for i, item in enumerate(arr):
            path = "android.inputMethods[%d]" % i
            if not isinstance(item, dict):
                self.skip(path, "not an object")
                continue
            if "id" not in item or "enabled" not in item:
                self.skip(path, "missing id or enabled, dropped")
                continue
            kept.append({"id": item["id"], "enabled": item["enabled"]})
        if not kept:
            self.skip("android.inputMethods", "empty after drop")
            return None
        return kept

    def sanitize_features(self, arr: Any) -> list[dict[str, Any]] | None:
        if not isinstance(arr, list):
            return None
        kept: list[dict[str, Any]] = []
        seen: set[str] = set()
        for i, item in enumerate(arr):
            path = "android.features[%d]" % i
            if not isinstance(item, dict):
                self.skip(path, "not an object")
                continue
            name = item.get("name")
            if not isinstance(name, str) or not FEATURE_NAME_RE.match(name):
                self.skip(path + ".name", "invalid feature name, dropped")
                continue
            if name in seen:
                self.skip(path + ".name", "duplicate, dropped")
                continue
            seen.add(name)
            feat: dict[str, Any] = {"name": name}
            if "version" in item:
                n = self.as_int(item["version"], 0, None)
                if n is None:
                    self.skip(path + ".version", "invalid, dropped")
                else:
                    feat["version"] = n
            for k in item:
                if k not in ("name", "version"):
                    self.skip(path + "." + k, "not a unidbg key")
            kept.append(feat)
        return kept

    def sanitize_accounts(self, arr: Any) -> list[dict[str, Any]] | None:
        if not isinstance(arr, list):
            return None
        kept: list[dict[str, Any]] = []
        for i, item in enumerate(arr):
            path = "android.accounts[%d]" % i
            if not isinstance(item, dict):
                self.skip(path, "not an object")
                continue
            if "name" not in item or "type" not in item:
                self.skip(path, "missing name or type, dropped")
                continue
            kept.append({"name": item["name"], "type": item["type"]})
        return kept

    def remap_package(self, root: dict[str, Any], package_name: str) -> None:
        android = root.get("android")
        if not isinstance(android, dict):
            self.skip("remap-package", "no android section")
            return
        found = None
        for pkg in android.get("packages") or []:
            if isinstance(pkg, dict) and pkg.get("packageName") == package_name:
                found = pkg
                break
        if found is None:
            self.skip("remap-package", "package %s not in android.packages" % package_name)
            return
        android["packageName"] = package_name
        if "versionName" in found:
            android["versionName"] = found["versionName"]
        if "versionCode" in found:
            android["versionCode"] = found["versionCode"]
        if "sourceDir" in found:
            android["apkPath"] = found["sourceDir"]
            src = found["sourceDir"]
            if isinstance(src, str) and src.endswith("/base.apk"):
                android["nativeLibraryDir64"] = src[: -len("/base.apk")] + "/lib/arm64"
                android["nativeLibraryDir32"] = src[: -len("/base.apk")] + "/lib/arm"
        if "dataDir" in found:
            android["dataDir"] = found["dataDir"]
        process = root.get("process")
        if isinstance(process, dict):
            process["processName"] = package_name
            uid = found.get("uid")
            if isinstance(uid, int) and not isinstance(uid, bool):
                process["uid"] = uid
                process["euid"] = uid
                process["gid"] = uid
                process["egid"] = uid
        linux = root.get("linux")
        if not isinstance(linux, dict):
            return
        proc = linux.get("proc")
        if isinstance(proc, dict):
            proc["cmdline"] = [package_name]
            comm = package_name[:15]
            if "comm" in proc:
                proc["comm"] = comm
            uid = found.get("uid")
            pid = None
            if isinstance(root.get("process"), dict):
                pid = root["process"].get("pid")
            if isinstance(uid, int) and isinstance(pid, int) and isinstance(proc.get("cgroups"), list):
                new_cgs = []
                for line in proc["cgroups"]:
                    if isinstance(line, str) and line.startswith("0::/uid_"):
                        new_cgs.append("0::/uid_%d/pid_%d" % (uid, pid))
                    else:
                        new_cgs.append(line)
                proc["cgroups"] = new_cgs
            if isinstance(uid, int) and uid >= 10000:
                cat = uid - 10000
                proc["selinuxContext"] = "u:r:untrusted_app:s0:c%d,c256,c512,c768" % cat
        files = linux.get("files")
        if isinstance(files, dict):
            old_cmd = files.get("/proc/self/cmdline")
            pad = 99
            if isinstance(old_cmd, str):
                pad = max(len(old_cmd), len(package_name))
            files["/proc/self/cmdline"] = package_name + "\x00" * (pad - len(package_name))
            files["/proc/self/comm"] = package_name[:15] + "\n"
            uid = found.get("uid")
            pid = root.get("process", {}).get("pid") if isinstance(root.get("process"), dict) else None
            if isinstance(uid, int) and isinstance(pid, int):
                files["/proc/self/cgroup"] = (
                    "3:cpuset:/top-app\n2:cpu:/system\n1:blkio:/\n0::/uid_%d/pid_%d\n" % (uid, pid)
                )
                if uid >= 10000:
                    files["/proc/self/attr/current"] = (
                        "u:r:untrusted_app:s0:c%d,c256,c512,c768\x00" % (uid - 10000)
                    )
            st = files.get("/proc/self/status")
            if isinstance(st, str):
                comm = package_name[:15]
                st = re.sub(r"Name:\t[^\n]*", "Name:\t" + comm, st)
                if isinstance(uid, int):
                    st = re.sub(r"Uid:\t[^\n]*", "Uid:\t%d\t%d\t%d\t%d" % (uid, uid, uid, uid), st)
                    st = re.sub(r"Gid:\t[^\n]*", "Gid:\t%d\t%d\t%d\t%d" % (uid, uid, uid, uid), st)
                if isinstance(pid, int):
                    st = re.sub(r"Tgid:\t\d+", "Tgid:\t%d" % pid, st)
                    st = re.sub(r"Pid:\t\d+", "Pid:\t%d" % pid, st)
                files["/proc/self/status"] = st
        env = linux.get("environ")
        data_dir = found.get("dataDir")
        if isinstance(env, list) and isinstance(data_dir, str):
            linux["environ"] = [
                "TMPDIR=%s/cache" % data_dir if isinstance(x, str) and x.startswith("TMPDIR=") else x
                for x in env
            ]
        processes = linux.get("processes")
        if isinstance(processes, list):
            linux["processes"] = [
                {
                    k: v for k, v in {
                        "pid": (root.get("process") or {}).get("pid"),
                        "comm": package_name[:15],
                        "cmdline": [package_name],
                    }.items() if v is not None
                }
            ]

    def run(self, root: dict[str, Any], remap_package: str | None) -> dict[str, Any]:
        if remap_package:
            self.remap_package(root, remap_package)

        net = self.sanitize_network(root.get("network"))
        if net:
            root["network"] = net
        else:
            root.pop("network", None)
            self.skip("network", "dropped")

        fs = root.get("filesystem")
        if isinstance(fs, dict):
            if "statfs" in fs:
                statfs = self.sanitize_statfs(fs["statfs"])
                if statfs:
                    fs["statfs"] = statfs
                else:
                    fs.pop("statfs", None)
            if "stat" in fs:
                stat = self.sanitize_stat(fs["stat"])
                if stat:
                    fs["stat"] = stat
                else:
                    fs.pop("stat", None)
            root["filesystem"] = fs

        android = root.get("android")
        if isinstance(android, dict):
            if "sensors" in android:
                sensors = self.sanitize_sensors(android["sensors"])
                if sensors:
                    android["sensors"] = sensors
                else:
                    android.pop("sensors", None)
            if "inputMethods" in android:
                im = self.sanitize_input_methods(android["inputMethods"])
                if im is not None:
                    android["inputMethods"] = im
                else:
                    android.pop("inputMethods", None)
            if "accounts" in android:
                ac = self.sanitize_accounts(android["accounts"])
                if ac is not None:
                    android["accounts"] = ac
                else:
                    android.pop("accounts", None)
            if "features" in android:
                feat = self.sanitize_features(android["features"])
                if feat is not None:
                    android["features"] = feat
                else:
                    android.pop("features", None)
            ident = android.get("identifiers")
            if isinstance(ident, dict):
                cleaned = {k: v for k, v in ident.items() if k in IDENT_KEYS}
                for k in ident:
                    if k not in IDENT_KEYS:
                        self.skip("android.identifiers." + k, "not a unidbg key")
                android["identifiers"] = cleaned
            tel = android.get("telephony")
            if isinstance(tel, dict):
                if "phoneCount" not in tel or "slots" not in tel:
                    android.pop("telephony", None)
                    self.skip("android.telephony", "missing required phoneCount/slots, dropped section")
                else:
                    allowed_tel = {
                        "phoneCount", "slots", "networkOperator", "networkOperatorName",
                        "simOperator", "simOperatorName", "networkCountryIso", "simCountryIso",
                        "dataNetworkType", "dataState", "dataActivity", "phoneType",
                        "networkRoaming", "cellInfo",
                    }
                    for k in list(tel):
                        if k not in allowed_tel:
                            self.skip("android.telephony." + k, "not a unidbg key")
                            tel.pop(k, None)
            g = self.sanitize_graphics(root.get("graphics"))
            if g:
                root["graphics"] = g
            else:
                if "graphics" in root:
                    root.pop("graphics", None)
                    self.skip("graphics", "dropped")

        linux = root.get("linux")
        if isinstance(linux, dict):
            if "sysinfo" in linux:
                info = self.sanitize_sysinfo(linux["sysinfo"])
                if info:
                    linux["sysinfo"] = info
                else:
                    linux.pop("sysinfo", None)
            proc = linux.get("proc")
            if isinstance(proc, dict):
                extra = [k for k in list(proc) if k not in PROC_KEYS]
                for k in extra:
                    self.skip("linux.proc." + k, "not a unidbg key")
                    proc.pop(k, None)
            procs = linux.get("processes")
            if isinstance(procs, list):
                kept_p = []
                for i, item in enumerate(procs):
                    if not isinstance(item, dict):
                        continue
                    cleaned = {k: v for k, v in item.items() if k in PROCESS_LIST_KEYS}
                    for k in item:
                        if k not in PROCESS_LIST_KEYS:
                            self.skip("linux.processes[%d].%s" % (i, k), "not a unidbg key")
                    if "pid" in cleaned:
                        kept_p.append(cleaned)
                    else:
                        self.skip("linux.processes[%d]" % i, "missing pid")
                linux["processes"] = kept_p
        return root


def load_input(path: Path) -> dict[str, Any]:
    if path.suffix.lower() == ".zip":
        with zipfile.ZipFile(path) as zf:
            name = "device-fingerprint.json"
            if name not in zf.namelist():
                raise SystemExit("zip missing device-fingerprint.json")
            return json.loads(zf.read(name).decode("utf-8"))
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    ap = argparse.ArgumentParser(description="Drop incomplete EnvDump fields for unidbg")
    ap.add_argument("--in", dest="src", required=True, help="device-fingerprint.json or envdump.zip")
    ap.add_argument("--out", required=True, help="output json path")
    ap.add_argument("--overlay", default=None, help="files/ overlay directory")
    ap.add_argument("--remap-package", default=None,
                    help="set process/android identity from android.packages[]")
    ap.add_argument("--skipped-out", default=None, help="write skip log json")
    args = ap.parse_args()
    src = Path(args.src)
    overlay = Path(args.overlay) if args.overlay else src.parent / "files"
    if not overlay.is_dir():
        overlay = None
    root = load_input(src)
    s = Sanitizer(overlay)
    out = s.run(root, args.remap_package)
    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    skipped_path = Path(args.skipped_out) if args.skipped_out else out_path.parent / "sanitize-skipped.json"
    skipped_path.write_text(json.dumps(s.skipped, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("wrote", out_path)
    print("skipped", len(s.skipped), "->", skipped_path)


if __name__ == "__main__":
    main()
