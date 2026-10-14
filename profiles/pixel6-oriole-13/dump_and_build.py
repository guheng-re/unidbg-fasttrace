# -*- coding: utf-8 -*-
"""Dump the USB Pixel 6 and build a unidbg device-fingerprint profile.

Clock fields are intentionally omitted so unidbg uses the host wall/monotonic
clock and both increment.
"""
from __future__ import print_function

import hashlib
import json
import os
import re
import struct
import subprocess
import time

SERIAL = "24241FDF6000S1"
ROOT = os.path.dirname(os.path.abspath(__file__))
DUMP = os.path.join(ROOT, "device-dump")
FILES = os.path.join(ROOT, "files")
ADB = ["adb", "-s", SERIAL]


def sh(args, timeout=60):
    cmd = ADB + args
    p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
    out = p.stdout.decode("utf-8", "replace")
    if p.returncode != 0 and not out:
        err = p.stderr.decode("utf-8", "replace")
        raise RuntimeError("cmd failed %s: %s" % (cmd, err[:300]))
    return out


def su(cmd, timeout=60):
    return sh(["shell", "su", "-c", cmd], timeout=timeout)


def write_text(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if not text.endswith("\n"):
        text += "\n"
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


def write_bytes(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(data)


def parse_getprop(text):
    props = {}
    for line in text.splitlines():
        m = re.match(r"^\[([^\]]+)\]: \[([\s\S]*?)\]\s*$", line)
        if m:
            props[m.group(1)] = m.group(2)
    return props


def parse_kv_settings(text):
    out = {}
    for line in text.splitlines():
        if "=" in line:
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def hex16(s):
    h = re.sub(r"[^0-9a-fA-F]", "", s or "")
    if len(h) >= 16:
        return h[:16].lower()
    return (h + "0" * 16)[:16]


def parse_ip_addr(text):
    ifaces = []
    cur = None
    for line in text.splitlines():
        m = re.match(r"^\d+:\s+([^:@]+)(?:@[^:]+)?:\s+<([^>]*)>\s+.*mtu\s+(\d+)", line)
        if m:
            name = m.group(1)
            flags_s = m.group(2).split(",")
            flags = 0
            # IFF bits commonly used by unidbg
            mapping = {
                "UP": 0x1, "BROADCAST": 0x2, "DEBUG": 0x4, "LOOPBACK": 0x8,
                "POINTOPOINT": 0x10, "NOTRAILERS": 0x20, "RUNNING": 0x40,
                "NOARP": 0x80, "PROMISC": 0x100, "ALLMULTI": 0x200,
                "MASTER": 0x400, "SLAVE": 0x800, "MULTICAST": 0x1000,
                "LOWER_UP": 0x10000,
            }
            for f in flags_s:
                flags |= mapping.get(f, 0)
            flags &= 0xFFFF  # schema 0..65535
            cur = {
                "name": name,
                "index": len(ifaces) + 1,
                "ipv4": "0.0.0.0",
                "flags": flags,
                "mac": "00:00:00:00:00:00",
                "hardwareType": 772 if name == "lo" else 1,
                "mtu": int(m.group(3)),
                "operState": "up" if "UP" in flags_s else "down",
                "carrier": "LOWER_UP" in flags_s or "UP" in flags_s,
            }
            if "LOOPBACK" in flags_s:
                cur["ipv4"] = "127.0.0.1"
            ifaces.append(cur)
            continue
        if cur is None:
            continue
        m = re.search(r"link/ether\s+([0-9a-fA-F:]{17})", line)
        if m:
            cur["mac"] = m.group(1).lower()
        m = re.search(r"inet\s+(\d+\.\d+\.\d+\.\d+)/(\d+)", line)
        if m:
            cur["ipv4"] = m.group(1)
            plen = int(m.group(2))
            mask = (0xFFFFFFFF << (32 - plen)) & 0xFFFFFFFF
            cur["broadcast"] = ".".join(str((int(cur["ipv4"].split(".")[i]) | ((~mask >> (24 - 8 * i)) & 0xFF)) & 0xFF) for i in range(4)) if plen < 32 else cur["ipv4"]
    # ensure ipv4 present
    for it in ifaces:
        if not it.get("ipv4"):
            it["ipv4"] = "169.254.1.1"
            it["virtual"] = True
    return ifaces


def parse_ipv6(text):
    addrs = []
    # /proc/net/if_inet6: addr iface plen scope flags name
    for line in text.splitlines():
        parts = line.split()
        if len(parts) < 6:
            continue
        addrs.append({
            "interfaceName": parts[5],
            "addressHex": parts[0].lower(),
            "prefixLength": int(parts[2], 16),
            "scope": int(parts[3], 16),
            "flags": int(parts[4], 16) & 0xFFFF,
        })
    return addrs


def parse_sensors(text):
    # dumpsys sensorservice
    types, names, vendors, versions = [], {}, {}, {}
    string_types, maxr, reso, powers, mind, wake, ids, modes, samples = {}, {}, {}, {}, {}, {}, {}, {}, {}
    # Handle "0x00000001) LSM6DSO Accel  | ST | ver: 1 | type: android.sensor.accelerometer(1)"
    pat = re.compile(
        r"0x([0-9a-fA-F]+)\)\s+(.+?)\s+\|\s+(.+?)\s+\|\s+ver:\s*(\d+)\s+\|\s+type:\s+([^\s(]+)\((\d+)\)",
        re.M,
    )
    for m in pat.finditer(text):
        handle = int(m.group(1), 16)
        name = m.group(2).strip()
        vendor = m.group(3).strip()
        ver = int(m.group(4))
        stype = m.group(5).strip()
        typ = int(m.group(6))
        if typ in types:
            continue
        types.append(typ)
        k = str(typ)
        names[k] = name
        vendors[k] = vendor
        versions[k] = ver
        string_types[k] = stype
        ids[k] = handle if handle < 2**31 else -1
        wake[k] = False
        modes[k] = 0
        # defaults; refined below
        if typ == 1:
            maxr[k], reso[k], powers[k], mind[k] = 78.4532, 0.0024, 0.15, 2500
            samples[k] = [0.1, -0.05, 9.81]
        elif typ == 2:
            maxr[k], reso[k], powers[k], mind[k] = 4900.0, 0.6, 0.35, 10000
            samples[k] = [15.0, -8.0, 40.0]
        elif typ == 4:
            maxr[k], reso[k], powers[k], mind[k] = 34.9, 0.0005, 0.25, 2500
            samples[k] = [0.0, 0.0, 0.0]
        elif typ == 5:
            maxr[k], reso[k], powers[k], mind[k] = 65535.0, 1.0, 0.09, 0
            samples[k] = [80.0]
            modes[k] = 1
        else:
            maxr[k], reso[k], powers[k], mind[k] = 1.0, 0.001, 0.1, 10000
            samples[k] = [0.0]
    if not types:
        types = [1, 2, 4, 5]
        names = {"1": "Accel", "2": "Mag", "4": "Gyro", "5": "Light"}
        vendors = {"1": "Google", "2": "Google", "4": "Google", "5": "Google"}
        versions = {"1": 1, "2": 1, "4": 1, "5": 1}
        string_types = {
            "1": "android.sensor.accelerometer",
            "2": "android.sensor.magnetic_field",
            "4": "android.sensor.gyroscope",
            "5": "android.sensor.light",
        }
        maxr = {"1": 78.45, "2": 4900.0, "4": 34.9, "5": 65535.0}
        reso = {"1": 0.0024, "2": 0.6, "4": 0.0005, "5": 1.0}
        powers = {"1": 0.15, "2": 0.35, "4": 0.25, "5": 0.09}
        mind = {"1": 2500, "2": 10000, "4": 2500, "5": 0}
        wake = {"1": False, "2": False, "4": False, "5": False}
        ids = {"1": -1, "2": -1, "4": -1, "5": -1}
        modes = {"1": 0, "2": 0, "4": 0, "5": 1}
        samples = {"1": [0.1, -0.05, 9.81], "2": [15.0, -8.0, 40.0], "4": [0.0, 0.0, 0.0], "5": [80.0]}
    return {
        "types": types,
        "names": names,
        "vendors": vendors,
        "versions": versions,
        "stringTypes": string_types,
        "maximumRanges": maxr,
        "resolutions": reso,
        "powers": powers,
        "minDelaysMicros": mind,
        "wakeUpSensors": wake,
        "sensorIds": ids,
        "reportingModes": modes,
        "samples": samples,
    }


def pkg_sig_hex(pkg):
    text = sh(["shell", "dumpsys", "package", pkg], timeout=90)
    # Signing keyset / signatures
    for pat in [
        r"signatures:\[([0-9a-fA-F]{16,})\]",
        r"PackageSignatures\{[^}]*([0-9A-Fa-f]{32})",
        r"sha256[:\s]+([0-9a-fA-F]{32,64})",
        r"SHA-256 digest:\s+([0-9a-fA-F:]+)",
    ]:
        m = re.search(pat, text)
        if m:
            h = re.sub(r"[^0-9a-fA-F]", "", m.group(1))
            if len(h) >= 32:
                return h[:32].upper()
    # fallback: md5 of dumpsys snippet
    return hashlib.md5(text[:200].encode()).hexdigest().upper()


def dumpsys_field(text, key):
    m = re.search(r"%s[=:]\s*(\S+)" % re.escape(key), text)
    return m.group(1) if m else ""


def parse_display(text):
    w = h = dpi = 0
    refresh = 60.0
    m = re.search(r"real\s+(\d+)\s+x\s+(\d+)", text)
    if m:
        w, h = int(m.group(1)), int(m.group(2))
    m = re.search(r"density[= ]+(\d+)", text)
    if m:
        dpi = int(m.group(1))
    m = re.search(r"(\d+(?:\.\d+)?)\s*Hz", text)
    if m:
        refresh = float(m.group(1))
    if w == 0:
        w, h, dpi = 1080, 2400, 420
    return {
        "widthPixels": w,
        "heightPixels": h,
        "densityDpi": dpi,
        "scaledDensity": round(dpi / 160.0, 2),
        "xdpi": float(dpi),
        "ydpi": float(dpi),
        "refreshRate": refresh,
        "rotation": 0,
        "modeId": 1,
    }


def parse_battery(text):
    def gi(k, default=0):
        m = re.search(r"%s:\s*(-?\d+)" % k, text)
        return int(m.group(1)) if m else default

    level = gi("level", 50)
    status = gi("status", 3)
    plugged = gi("plugged", 0)
    return {
        "capacityPercent": level,
        "charging": plugged != 0,
        "chargeCounterUah": gi("charge counter", 0),
        "currentNowUa": gi("current now", 0),
        "currentAverageUa": 0,
        "energyCounterNwh": 0,
        "status": status,
        "chargeTimeRemainingMillis": -1,
    }


def collect():
    os.makedirs(DUMP, exist_ok=True)
    print("dumping", SERIAL)
    mapping = {
        os.path.join(DUMP, "props", "getprop.txt"): ["shell", "getprop"],
        os.path.join(DUMP, "props", "android_id.txt"): ["shell", "settings", "get", "secure", "android_id"],
        os.path.join(DUMP, "props", "settings_secure.txt"): ["shell", "settings", "list", "secure"],
        os.path.join(DUMP, "props", "settings_global.txt"): ["shell", "settings", "list", "global"],
        os.path.join(DUMP, "props", "settings_system.txt"): ["shell", "settings", "list", "system"],
        os.path.join(DUMP, "proc", "cpuinfo"): ["shell", "cat", "/proc/cpuinfo"],
        os.path.join(DUMP, "proc", "meminfo"): ["shell", "cat", "/proc/meminfo"],
        os.path.join(DUMP, "proc", "version"): ["shell", "cat", "/proc/version"],
        os.path.join(DUMP, "proc", "mounts"): ["shell", "cat", "/proc/mounts"],
        os.path.join(DUMP, "proc", "uname.txt"): ["shell", "uname", "-a"],
        os.path.join(DUMP, "proc", "sys", "kernel", "random", "boot_id"): ["shell", "cat", "/proc/sys/kernel/random/boot_id"],
        os.path.join(DUMP, "proc", "if_inet6"): ["shell", "cat", "/proc/net/if_inet6"],
        os.path.join(DUMP, "proc", "ip_addr.txt"): ["shell", "ip", "addr"],
        os.path.join(DUMP, "system", "df.txt"): ["shell", "df"],
        os.path.join(DUMP, "system", "fonts_ls.txt"): ["shell", "ls", "-l", "/system/fonts"],
        os.path.join(DUMP, "system", "display.txt"): ["shell", "dumpsys", "display"],
        os.path.join(DUMP, "system", "battery.txt"): ["shell", "dumpsys", "battery"],
        os.path.join(DUMP, "system", "sensorservice.txt"): ["shell", "dumpsys", "sensorservice"],
        os.path.join(DUMP, "system", "telephony.txt"): ["shell", "dumpsys", "telephony.registry"],
        os.path.join(DUMP, "system", "wifi.txt"): ["shell", "dumpsys", "wifi"],
        os.path.join(DUMP, "system", "wm.txt"): ["shell", "wm", "size"],
        os.path.join(DUMP, "system", "density.txt"): ["shell", "wm", "density"],
        os.path.join(DUMP, "system", "features.txt"): ["shell", "pm", "list", "features"],
        os.path.join(DUMP, "system", "packages.txt"): ["shell", "pm", "list", "packages", "-f"],
    }
    for path, args in mapping.items():
        try:
            write_text(path, sh(args, timeout=90))
            print(" ok", os.path.relpath(path, DUMP))
        except Exception as e:
            print(" fail", args, e)
            write_text(path, "")

    # net sysfs
    nets = sh(["shell", "ls", "/sys/class/net"]).split()
    for n in nets:
        n = n.strip()
        if not n:
            continue
        base = os.path.join(DUMP, "sys", "class", "net", n)
        for f in ("address", "carrier", "flags", "ifindex", "mtu", "operstate", "type"):
            try:
                write_text(os.path.join(base, f), sh(["shell", "cat", "/sys/class/net/%s/%s" % (n, f)]))
            except Exception:
                pass

    # cpu
    for f in ("online", "possible", "present"):
        try:
            write_text(os.path.join(DUMP, "sys", "devices", "system", "cpu", f),
                       sh(["shell", "cat", "/sys/devices/system/cpu/" + f]))
        except Exception:
            pass

    # overlays
    for src, dst in [
        ("/proc/cpuinfo", os.path.join(FILES, "proc", "cpuinfo")),
        ("/proc/meminfo", os.path.join(FILES, "proc", "meminfo")),
        ("/proc/version", os.path.join(FILES, "proc", "version")),
        ("/proc/mounts", os.path.join(FILES, "proc", "mounts")),
        ("/proc/sys/kernel/random/boot_id", os.path.join(FILES, "proc", "sys", "kernel", "random", "boot_id")),
    ]:
        try:
            write_text(dst, sh(["shell", "cat", src]))
        except Exception:
            pass

    # copy pixel fonts from existing vivo overlay if present (already from Pixel)
    src_fonts = os.path.join(os.path.dirname(ROOT), "vivo-V2132A-13", "files", "system", "fonts")
    dst_fonts = os.path.join(FILES, "system", "fonts")
    if os.path.isdir(src_fonts) and not os.path.isdir(dst_fonts):
        import shutil
        shutil.copytree(src_fonts, dst_fonts)
        print(" copied fonts from vivo overlay")


def read(path, default=""):
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as fh:
            return fh.read()
    except Exception:
        return default


def first_int(text, default=0):
    m = re.search(r"(\d+)", text)
    return int(m.group(1)) if m else default


def build():
    props = parse_getprop(read(os.path.join(DUMP, "props", "getprop.txt")))
    android_id = hex16(read(os.path.join(DUMP, "props", "android_id.txt")).strip())
    secure = parse_kv_settings(read(os.path.join(DUMP, "props", "settings_secure.txt")))
    glob = parse_kv_settings(read(os.path.join(DUMP, "props", "settings_global.txt")))
    cpuinfo = read(os.path.join(DUMP, "proc", "cpuinfo"))
    meminfo = read(os.path.join(DUMP, "proc", "meminfo"))
    version = read(os.path.join(DUMP, "proc", "version")).strip()
    boot_id = read(os.path.join(DUMP, "proc", "sys", "kernel", "random", "boot_id")).strip()
    uname_a = read(os.path.join(DUMP, "proc", "uname.txt")).strip()
    ip_addr = read(os.path.join(DUMP, "proc", "ip_addr.txt"))
    if6 = read(os.path.join(DUMP, "proc", "if_inet6"))
    display = parse_display(read(os.path.join(DUMP, "system", "display.txt")) + "\n" +
                            read(os.path.join(DUMP, "system", "wm.txt")) + "\n" +
                            read(os.path.join(DUMP, "system", "density.txt")))
    battery = parse_battery(read(os.path.join(DUMP, "system", "battery.txt")))
    sensors = parse_sensors(read(os.path.join(DUMP, "system", "sensorservice.txt")))

    mem_total_kb = 0
    m = re.search(r"MemTotal:\s+(\d+)", meminfo)
    if m:
        mem_total_kb = int(m.group(1))
    mem_free_kb = 0
    m = re.search(r"MemFree:\s+(\d+)", meminfo)
    if m:
        mem_free_kb = int(m.group(1))

    # processors
    ncpu = len(re.findall(r"^processor\s*:", cpuinfo, re.M)) or 8
    online = read(os.path.join(DUMP, "sys", "devices", "system", "cpu", "online")).strip() or ("0-%d" % (ncpu - 1))

    uname_rel = props.get("ro.kernel.version") or ""
    if not uname_rel and uname_a:
        parts = uname_a.split()
        uname_rel = parts[2] if len(parts) > 2 else "5.10.0"
    uname_ver = version or "#1 SMP PREEMPT"

    ifaces = parse_ip_addr(ip_addr)
    # attach mac from sysfs
    netroot = os.path.join(DUMP, "sys", "class", "net")
    if os.path.isdir(netroot):
        for it in ifaces:
            addr = read(os.path.join(netroot, it["name"], "address")).strip()
            if addr:
                it["mac"] = addr
            try:
                it["index"] = int(read(os.path.join(netroot, it["name"], "ifindex")).strip() or it["index"])
            except Exception:
                pass
            oper = read(os.path.join(netroot, it["name"], "operstate")).strip()
            if oper:
                it["operState"] = oper
    ipv6 = parse_ipv6(if6)

    wlan = next((x for x in ifaces if x["name"] == "wlan0"), None)
    wifi_txt = read(os.path.join(DUMP, "system", "wifi.txt"))
    ssid = ""
    bssid = ""
    rssi = -60
    m = re.search(r'mSSID[^\n]*"([^"]+)"', wifi_txt)
    if m:
        ssid = m.group(1)
    m = re.search(r"BSSID:\s*([0-9a-fA-F:]{17})", wifi_txt, re.I)
    if m:
        bssid = m.group(1).lower()
    m = re.search(r"rssi[=:\s]+(-?\d+)", wifi_txt, re.I)
    if m:
        rssi = int(m.group(1))

    features = []
    for line in read(os.path.join(DUMP, "system", "features.txt")).splitlines():
        line = line.strip()
        if line.startswith("feature:"):
            features.append({"name": line.split(":", 1)[1].strip()})
    if not features:
        features = [{"name": n} for n in [
            "android.hardware.camera", "android.hardware.wifi",
            "android.hardware.touchscreen", "android.hardware.telephony",
            "android.hardware.fingerprint",
        ]]

    # packages: pixel system + emulated 12306
    want = [
        "android", "com.android.systemui", "com.android.settings",
        "com.android.phone", "com.android.vending", "com.google.android.gms",
        "com.MobileTicket",
    ]
    packages = []
    for pkg in want:
        text = ""
        try:
            text = sh(["shell", "dumpsys", "package", pkg], timeout=90)
        except Exception:
            text = ""
        if pkg == "com.MobileTicket" and "Unable to find" in text or (pkg == "com.MobileTicket" and "versionName" not in text and "versionName=" not in text):
            packages.append({
                "packageName": "com.MobileTicket",
                "versionName": "5.9.6.6",
                "versionCode": 5966,
                "sourceDir": "/data/app/~~ticket/com.MobileTicket-fixed/base.apk",
                "dataDir": "/data/user/0/com.MobileTicket",
                "uid": 10231,
                "enabled": True,
                "systemApp": False,
                "firstInstallTimeMillis": int(time.time() * 1000),
                "lastUpdateTimeMillis": int(time.time() * 1000),
                "signaturesHex": ["8C62B8AE0C9E9394FC7A6D0D95ED541C"],
            })
            continue
        ver = re.search(r"versionName=(\S+)", text)
        code = re.search(r"versionCode=(\d+)", text)
        src = re.search(r"codePath=(\S+)", text) or re.search(r"path: (\S+)", text)
        data_dir = re.search(r"dataDir=(\S+)", text)
        uid_m = re.search(r"userId=(\d+)", text)
        sig = pkg_sig_hex(pkg) if text else hashlib.md5(pkg.encode()).hexdigest().upper()
        packages.append({
            "packageName": pkg,
            "versionName": ver.group(1) if ver else "1.0",
            "versionCode": int(code.group(1)) if code else 1,
            "sourceDir": src.group(1) if src else "/system/app/%s/%s.apk" % (pkg, pkg),
            "dataDir": data_dir.group(1) if data_dir else "/data/user/0/" + pkg,
            "uid": int(uid_m.group(1)) if uid_m else 1000,
            "enabled": True,
            "systemApp": pkg != "com.MobileTicket",
            "signaturesHex": [sig if re.fullmatch(r"[0-9A-F]+", sig) else hashlib.md5(sig.encode()).hexdigest().upper()],
        })
        print(" pkg", pkg, packages[-1]["versionName"], packages[-1]["signaturesHex"][0][:16])

    # telephony
    tel = read(os.path.join(DUMP, "system", "telephony.txt"))
    imei = ""
    try:
        raw = su("service call iphonesubinfo 1")
        # Parcel chars
        chars = re.findall(r"'([^']*)'", raw)
        imei = re.sub(r"[^0-9]", "", "".join(chars))
    except Exception as e:
        print("imei fail", e)
    mcc = props.get("gsm.sim.operator.numeric", props.get("gsm.operator.numeric", "46000")).split(",")[0]
    mcc_name = props.get("gsm.sim.operator.alpha", props.get("gsm.operator.alpha", "")).split(",")[0]

    locale = props.get("persist.sys.locale") or "zh-Hans-CN"
    tzid = props.get("persist.sys.timezone") or "Asia/Shanghai"

    fingerprint = props.get("ro.build.fingerprint") or ""
    model = props.get("ro.product.model") or "Pixel 6"
    brand = props.get("ro.product.brand") or "google"
    device = props.get("ro.product.device") or "oriole"
    product = props.get("ro.product.name") or "oriole"
    hardware = props.get("ro.hardware") or "oriole"
    serial = props.get("ro.serialno") or SERIAL
    sdk = int(props.get("ro.build.version.sdk") or "33")
    release = props.get("ro.build.version.release") or "13"

    build_time = int(props.get("ro.build.date.utc") or "0") * 1000

    pages = max(1, (mem_total_kb * 1024) // 4096)
    free_pages = max(1, (mem_free_kb * 1024) // 4096)

    uptime_s = 0
    try:
        uptime_s = int(float(sh(["shell", "cat", "/proc/uptime"]).split()[0]))
    except Exception:
        uptime_s = 3600

    profile = {
        "schemaVersion": "traceai-device-fingerprint/v1",
        "profileName": "pixel6-oriole-13",
        "fileOverlayRoot": "files",
        "process": {
            "processName": "com.MobileTicket",
            "pid": 10231,
            "pgid": 10231,
            "sid": 10231,
            "ppid": 1,
            "tid": 10231,
            "uid": 10231,
            "gid": 10231,
            "euid": 10231,
            "egid": 10231,
            "supplementaryGids": [10231],
            "threadName": "main",
        },
        # time omitted: unidbg uses host wall/monotonic and both increment
        "time": {
            "timezoneMinutesWest": -480
        },
        # random frozen so /dev/urandom and getrandom do not increment
        "random": {
            "devRandomHex": "a1b2c3d4",
            "devUrandomHex": "b2c3d4e5",
            "devSrandomHex": "c3d4e5f6",
            "getrandomHex": "7a239e5dd15748a2ac54aba483265c47",
            "uuid": "3e08b104-8d42-41c6-83e0-3e08b1048d42",
            "stackGuardHex": "0102030405060708",
            "atRandomHex": "7a239e5dd15748a2ac54aba483265c47",
        },
        "linux": {
            "uname": {
                "sysname": "Linux",
                "nodename": "localhost",
                "release": uname_rel,
                "version": uname_ver[:180],
                "machine32": "armv8l",
                "machine64": "aarch64",
                "domainname": "(none)",
            },
            "cpu": {
                "affinityMaskHex": format((1 << ncpu) - 1, "x"),
                "online": online,
                "present": online,
                "possible": online,
                "configuredProcessorCount": ncpu,
                "onlineProcessorCount": ncpu,
            },
            "auxv": {
                "hwcap32": 1,
                "hwcap2_32": 2,
                "hwcap64": 3219913727,
                "hwcap2_64": 2,
                "platform32": "v8l",
                "platform64": "aarch64",
                "execFn": "/system/bin/app_process64",
            },
            "environ": [
                "ANDROID_DATA=/data",
                "ANDROID_ROOT=/system",
                "PATH=/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin",
                "TMPDIR=/data/local/tmp",
            ],
            "rlimits": {"nofile": {"soft": 32768, "hard": 32768}},
            "sysinfo": {
                "uptime": uptime_s,
                "loads": [65536, 58982, 52428],
                "totalRam": pages,
                "freeRam": free_pages,
                "sharedRam": 8192,
                "bufferRam": 2048,
                "totalSwap": 0,
                "freeSwap": 0,
                "procs": 500,
                "memUnit": 4096,
            },
            "proc": {
                "cmdline": ["com.MobileTicket"],
                "cgroups": ["0::/"],
                "comm": "main",
                "tracerPid": 0,
                "bootId": boot_id or "00000000-0000-0000-0000-000000000000",
                "selinuxContext": "u:r:untrusted_app:s0:c512,c768",
                "fileSelinuxContexts": {
                    "/data/user/0/com.MobileTicket": "u:object_r:app_data_file:s0"
                },
            },
            "commands": {
                "/system/bin/df": read(os.path.join(DUMP, "system", "df.txt")).rstrip("\n"),
                "df": read(os.path.join(DUMP, "system", "df.txt")).rstrip("\n"),
            },
        },
        "android": {
            "packageName": "com.MobileTicket",
            "versionName": "5.9.6.6",
            "versionCode": 5966,
            "apkPath": "/data/app/~~ticket/com.MobileTicket-fixed/base.apk",
            "dataDir": "/data/user/0/com.MobileTicket",
            "nativeLibraryDir32": "/data/app/~~ticket/com.MobileTicket-fixed/lib/arm",
            "nativeLibraryDir64": "/data/app/~~ticket/com.MobileTicket-fixed/lib/arm64",
            "build": {
                "BRAND": brand,
                "MANUFACTURER": props.get("ro.product.manufacturer") or brand,
                "MODEL": model,
                "DEVICE": device,
                "PRODUCT": product,
                "BOARD": props.get("ro.product.board") or hardware,
                "HARDWARE": hardware,
                "BOOTLOADER": props.get("ro.bootloader") or "unknown",
                "DISPLAY": props.get("ro.build.display.id") or "",
                "ID": props.get("ro.build.id") or "",
                "HOST": props.get("ro.build.host") or "",
                "USER": props.get("ro.build.user") or "",
                "TAGS": props.get("ro.build.tags") or "release-keys",
                "TYPE": props.get("ro.build.type") or "user",
                "TIME": build_time,
                "FINGERPRINT": fingerprint,
                "CPU_ABI": props.get("ro.product.cpu.abi") or "arm64-v8a",
                "CPU_ABI2": "armeabi-v7a",
                "SERIAL": serial,
                "SUPPORTED_ABIS": ["arm64-v8a", "armeabi-v7a"],
                "SUPPORTED_32_BIT_ABIS": ["armeabi-v7a"],
                "SUPPORTED_64_BIT_ABIS": ["arm64-v8a"],
                "VERSION.RELEASE": release,
                "VERSION.SDK_INT": sdk,
                "VERSION.INCREMENTAL": props.get("ro.build.version.incremental") or "",
                "VERSION.CODENAME": props.get("ro.build.version.codename") or "REL",
                "VERSION.SECURITY_PATCH": props.get("ro.build.version.security_patch") or "",
            },
            "properties": {
                "ro.product.brand": brand,
                "ro.product.manufacturer": props.get("ro.product.manufacturer") or brand,
                "ro.product.model": model,
                "ro.product.device": device,
                "ro.product.name": product,
                "ro.product.board": props.get("ro.product.board") or hardware,
                "ro.product.cpu.abi": props.get("ro.product.cpu.abi") or "arm64-v8a",
                "ro.hardware": hardware,
                "ro.board.platform": props.get("ro.board.platform") or hardware,
                "ro.serialno": serial,
                "ro.boot.serialno": props.get("ro.boot.serialno") or serial,
                "ro.bootloader": props.get("ro.bootloader") or "unknown",
                "ro.build.id": props.get("ro.build.id") or "",
                "ro.build.display.id": props.get("ro.build.display.id") or "",
                "ro.build.version.incremental": props.get("ro.build.version.incremental") or "",
                "ro.build.version.sdk": str(sdk),
                "ro.build.version.release": release,
                "ro.build.version.codename": props.get("ro.build.version.codename") or "REL",
                "ro.build.version.security_patch": props.get("ro.build.version.security_patch") or "",
                "ro.build.type": props.get("ro.build.type") or "user",
                "ro.build.tags": props.get("ro.build.tags") or "release-keys",
                "ro.build.user": props.get("ro.build.user") or "",
                "ro.build.host": props.get("ro.build.host") or "",
                "ro.build.fingerprint": fingerprint,
                "ro.debuggable": props.get("ro.debuggable") or "0",
                "ro.secure": props.get("ro.secure") or "1",
                "ro.boot.flash.locked": props.get("ro.boot.flash.locked") or "1",
                "ro.boot.verifiedbootstate": props.get("ro.boot.verifiedbootstate") or "green",
                "ro.boot.veritymode": props.get("ro.boot.veritymode") or "enforcing",
                "persist.sys.timezone": tzid,
                "persist.sys.locale": locale,
                "wifi.interface": props.get("wifi.interface") or "wlan0",
                "gsm.operator.alpha": props.get("gsm.operator.alpha") or mcc_name,
                "gsm.operator.numeric": props.get("gsm.operator.numeric") or mcc,
                "gsm.sim.operator.alpha": props.get("gsm.sim.operator.alpha") or mcc_name,
                "gsm.sim.operator.numeric": props.get("gsm.sim.operator.numeric") or mcc,
                "gsm.sim.state": props.get("gsm.sim.state") or "READY",
                "ro.config.ringtone": props.get("ro.config.ringtone") or "Ring_Synth_04",
                "ro.config.notification_sound": props.get("ro.config.notification_sound") or "pixiedust",
                "ro.config.alarm_alert": props.get("ro.config.alarm_alert") or "Alarm_Classic",
            },
            "settings": {
                "secure": {
                    "android_id": android_id,
                    "accessibility_enabled": secure.get("accessibility_enabled", "0"),
                    "enabled_accessibility_services": secure.get("enabled_accessibility_services", ""),
                    "adb_enabled": "0",
                },
                "global": {
                    "adb_enabled": "0",
                    "development_settings_enabled": glob.get("development_settings_enabled", "0"),
                    "airplane_mode_on": glob.get("airplane_mode_on", "0"),
                },
            },
            "identifiers": {
                "androidId": android_id,
                "advertisingId": "00000000-0000-4000-8000-" + android_id,
                "limitAdTracking": False,
                "appSetId": "pixel6-app-set-" + android_id[:8],
                "appSetScope": 1,
            },
            "runtime": {
                "systemProperties": {
                    "os.name": "Linux",
                    "os.arch": "aarch64",
                    "java.vm.name": "Dalvik",
                },
                "environmentVariables": {
                    "ANDROID_DATA": "/data",
                    "ANDROID_ROOT": "/system",
                    "PATH": "/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin",
                    "TMPDIR": "/data/local/tmp",
                },
                "availableProcessors": ncpu,
                "maxMemoryBytes": 268435456,
                "totalMemoryBytes": 134217728,
                "freeMemoryBytes": 67108864,
            },
            "packages": packages,
            "features": features[:40],
            "locale": {"languageTag": locale, "timezoneId": tzid},
            "display": display,
            "configuration": {
                "orientation": 1,
                "screenLayout": 34,
                "uiMode": 17,
                "fontScale": 1.0,
                "densityDpi": display["densityDpi"],
                "screenWidthDp": int(display["widthPixels"] * 160 / max(display["densityDpi"], 1)),
                "screenHeightDp": int(display["heightPixels"] * 160 / max(display["densityDpi"], 1)),
                "smallestScreenWidthDp": int(min(display["widthPixels"], display["heightPixels"]) * 160 / max(display["densityDpi"], 1)),
                "keyboard": 1,
                "navigation": 1,
                "keyboardHidden": 1,
                "hardKeyboardHidden": 1,
                "navigationHidden": 1,
            },
            "battery": battery,
            "power": {
                "interactive": True,
                "powerSaveMode": False,
                "deviceIdleMode": False,
                "deviceLightIdleMode": False,
            },
            "thermal": {"currentThermalStatus": 0, "headroom": 0.8},
            "sensors": sensors,
            "telephony": {
                "phoneCount": 2 if "," in (props.get("gsm.sim.state") or "") else 1,
                "networkOperator": mcc,
                "networkOperatorName": mcc_name or "unknown",
                "simOperator": mcc,
                "simOperatorName": mcc_name or "unknown",
                "networkCountryIso": (props.get("gsm.operator.iso-country") or "cn").split(",")[0],
                "simCountryIso": (props.get("gsm.sim.operator.iso-country") or "cn").split(",")[0],
                "dataNetworkType": 13,
                "dataState": 2,
                "phoneType": 1,
                "networkRoaming": False,
                "slots": [
                    {
                        "slotIndex": 0,
                        "imei": imei or "000000000000000",
                        "deviceId": imei or "000000000000000",
                        "subscriberId": (props.get("gsm.sim.operator.numeric") or mcc).split(",")[0] + "0000000000",
                        "simSerialNumber": "89000000000000000000",
                        "simState": 5,
                    }
                ],
            },
            "cameras": {
                "count": 2,
                "infos": [
                    {"facing": 1, "orientation": 270, "canDisableShutterSound": True},
                    {"facing": 0, "orientation": 90, "canDisableShutterSound": False},
                ],
            },
            "location": {
                "enabled": True,
                "providers": {"gps": True, "network": True, "passive": True},
                "lastKnownLocations": [],
            },
            "securitySignals": {
                "debuggerConnected": False,
                "waitingForDebugger": False,
                "debuggerTracing": False,
                "selinuxEnabled": True,
                "selinuxEnforced": True,
                "userAMonkey": False,
                "userTestHarness": False,
            },
            "tee": {
                "available": True,
                "securityLevel": 1,
                "strongBoxAvailable": True,
                "marker": "PIXEL6_TEE_ORIOLE",
            },
            "drm": {
                "available": True,
                "marker": "PIXEL6_WIDEVINE",
                "deviceUniqueIdHex": hashlib.sha256((android_id + serial).encode()).hexdigest()[:64],
            },
        },
        "network": {
            "interfaces": ifaces if ifaces else [
                {"name": "lo", "index": 1, "ipv4": "127.0.0.1", "flags": 73, "mac": "00:00:00:00:00:00",
                 "hardwareType": 772, "mtu": 65536, "operState": "unknown", "carrier": False}
            ],
            "ipv6Addresses": ipv6,
            "wifi": {
                "enabled": True,
                "state": 3,
                "ssid": ssid or "unknown",
                "bssid": bssid or (wlan["mac"] if wlan else "00:00:00:00:00:00"),
                "macAddress": wlan["mac"] if wlan else "00:00:00:00:00:00",
                "ipv4": wlan["ipv4"] if wlan else "0.0.0.0",
                "rssi": rssi,
                "linkSpeedMbps": 433,
                "frequencyMhz": 5180,
                "networkId": 1,
            },
            "links": {
                "connected": True,
                "type": 1,
                "typeName": "WIFI",
                "interfaceName": "wlan0" if wlan else "lo",
                "dnsServers": [props.get("net.dns1") or "8.8.8.8", props.get("net.dns2") or "8.8.4.4"],
                "gatewayIpv4": props.get("dhcp.wlan0.gateway") or "0.0.0.0",
                "netmaskIpv4": props.get("dhcp.wlan0.mask") or "255.255.255.0",
                "mtu": wlan["mtu"] if wlan else 1500,
            },
            "capabilities": {
                "transportTypes": [1],
                "networkCapabilities": [12, 13, 14, 15, 16, 18, 19, 20, 21],
            },
        },
        "filesystem": {
            "externalStorage": {
                "directory": "/storage/emulated/0",
                "state": "mounted",
                "emulated": True,
                "removable": False,
            },
            "systemDirectories": {
                "rootDirectory": "/system",
                "dataDirectory": "/data",
                "downloadCacheDirectory": "/cache",
                "storageDirectory": "/storage",
            },
        },
    }

    out = os.path.join(ROOT, "device-fingerprint.json")
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(profile, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("wrote", out)
    print("model", model, "androidId", android_id, "serial", serial)
    print("display", display)
    print("ifaces", [(i["name"], i["ipv4"], i["mac"], i["flags"]) for i in profile["network"]["interfaces"]])
    print("sensors", sensors.get("names"))
    print("imei", imei)
    print("time section", profile["time"])


if __name__ == "__main__":
    collect()
    build()
