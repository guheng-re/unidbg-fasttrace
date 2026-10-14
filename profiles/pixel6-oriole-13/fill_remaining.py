# -*- coding: utf-8 -*-
"""Fill remaining Pixel 6 fingerprint fields. Skip random.* and maps."""
from __future__ import print_function

import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import time

SERIAL = "24241FDF6000S1"
ROOT = os.path.dirname(os.path.abspath(__file__))
DUMP = os.path.join(ROOT, "device-dump")
FILES = os.path.join(ROOT, "files")
PROFILE = os.path.join(ROOT, "device-fingerprint.json")
ADB = ["adb", "-s", SERIAL]
DEVICE_OUT = "/data/local/tmp/pixel6_fill"
SENSOR_TYPE_MAX = 65535
TICKET_SIG = "8C62B8AE0C9E9394FC7A6D0D95ED541C"
TICKET_UID = 10273
TICKET_VERSION_CODE = 278
TICKET_SELINUX = "u:r:untrusted_app:s0:c17,c257,c512,c768"


def sh(args, timeout=120):
    p = subprocess.run(ADB + args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
    return p.stdout.decode("utf-8", "replace")


def su(cmd, timeout=120):
    return sh(["shell", "su", "-c", cmd], timeout=timeout)


def write_text(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if text and not text.endswith("\n"):
        text += "\n"
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text or "")


def write_bytes(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(data)


def read(path, default=""):
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as fh:
            return fh.read()
    except Exception:
        return default


def collect_extra():
    script = os.path.join(ROOT, "collect_on_device.sh")
    print("push collect script")
    subprocess.run(ADB + ["push", script, "/data/local/tmp/fill_collect.sh"], check=False)
    print("run collect on device")
    print(su("sh /data/local/tmp/fill_collect.sh", timeout=180)[-500:])
    local_out = os.path.join(DUMP, "extra")
    if os.path.isdir(local_out):
        shutil.rmtree(local_out)
    os.makedirs(local_out, exist_ok=True)
    print("pull extra dump")
    subprocess.run(ADB + ["pull", DEVICE_OUT, local_out], check=False, timeout=180)

    # flatten extra/pixel6_fill -> extra if adb pull created a nested dir
    nested = os.path.join(local_out, "pixel6_fill")
    if os.path.isdir(nested):
        for name in os.listdir(nested):
            src = os.path.join(nested, name)
            dst = os.path.join(local_out, name)
            if os.path.exists(dst):
                if os.path.isdir(dst):
                    shutil.rmtree(dst)
                else:
                    os.remove(dst)
            shutil.move(src, dst)
        shutil.rmtree(nested, ignore_errors=True)

    jobs = {
        "system/wifi_status.txt": ["shell", "cmd", "wifi", "status"],
        "system/connectivity.txt": ["shell", "dumpsys", "connectivity"],
        "system/location.txt": ["shell", "dumpsys", "location"],
        "system/surfaceflinger.txt": ["shell", "dumpsys", "SurfaceFlinger"],
        "system/display.txt": ["shell", "dumpsys", "display"],
        "system/battery.txt": ["shell", "dumpsys", "battery"],
        "system/power.txt": ["shell", "dumpsys", "power"],
        "system/audio.txt": ["shell", "dumpsys", "audio"],
        "system/bluetooth.txt": ["shell", "dumpsys", "bluetooth_manager"],
        "system/account.txt": ["shell", "dumpsys", "account"],
        "system/input_method.txt": ["shell", "dumpsys", "input_method"],
        "system/accessibility.txt": ["shell", "dumpsys", "accessibility"],
        "system/user.txt": ["shell", "dumpsys", "user"],
        "system/window_policy.txt": ["shell", "dumpsys", "window", "policy"],
        "system/mediadrm.txt": ["shell", "dumpsys", "media.drm"],
        "system/packages_list.txt": ["shell", "pm", "list", "packages", "-f", "-U", "--show-versioncode"],
        "system/settings_secure.txt": ["shell", "settings", "list", "secure"],
        "system/settings_global.txt": ["shell", "settings", "list", "global"],
        "system/settings_system.txt": ["shell", "settings", "list", "system"],
    }
    for rel, args in jobs.items():
        try:
            out = sh(args, timeout=180)
            write_text(os.path.join(DUMP, rel), out)
            print("ok", rel, "bytes", len(out))
        except Exception as e:
            print("fail", rel, e)
            write_text(os.path.join(DUMP, rel), "")

    try:
        pkg = sh(["shell", "dumpsys", "package"], timeout=240)
        write_text(os.path.join(DUMP, "system", "dumpsys_packages.txt"), pkg)
        print("ok dumpsys packages", len(pkg))
    except Exception as e:
        print("fail dumpsys packages", e)

    # overlay copies from extra + existing dumps
    overlay_pairs = [
        ("extra/proc/loadavg", "proc/loadavg"),
        ("extra/proc/mounts", "proc/mounts"),
        ("extra/proc/version", "proc/version"),
        ("extra/proc/cpuinfo", "proc/cpuinfo"),
        ("extra/proc/meminfo", "proc/meminfo"),
        ("extra/proc/stat", "proc/stat"),
        ("extra/proc/uptime", "proc/uptime"),
        ("extra/proc/sys/kernel/random/boot_id", "proc/sys/kernel/random/boot_id"),
        ("extra/proc/sys/kernel/random/entropy_avail", "proc/sys/kernel/random/entropy_avail"),
        ("extra/proc/sys/kernel/random/poolsize", "proc/sys/kernel/random/poolsize"),
        ("extra/proc/net/tcp", "proc/net/tcp"),
        ("extra/proc/net/tcp6", "proc/net/tcp6"),
        ("extra/proc/net/route", "proc/net/route"),
        ("extra/proc/net/dev", "proc/net/dev"),
        ("extra/proc/net/if_inet6", "proc/net/if_inet6"),
        ("extra/proc/net/arp", "proc/net/arp"),
        ("extra/proc/net/igmp", "proc/net/igmp"),
        ("extra/proc/net/igmp6", "proc/net/igmp6"),
        ("extra/proc/net/dev_mcast", "proc/net/dev_mcast"),
        ("extra/proc/net/wireless", "proc/net/wireless"),
        ("extra/sys/class/android_usb/android0/state", "sys/class/android_usb/android0/state"),
        ("extra/sys/class/power_supply/battery/charge_full_design",
         "sys/class/power_supply/battery/charge_full_design"),
        ("extra/sys/class/power_supply/battery/capacity", "sys/class/power_supply/battery/capacity"),
        ("extra/sys/class/power_supply/battery/status", "sys/class/power_supply/battery/status"),
        ("extra/sys/class/power_supply/battery/current_now", "sys/class/power_supply/battery/current_now"),
        ("system/df.txt", "proc/df.txt"),
    ]
    for src_rel, dst_rel in overlay_pairs:
        src = os.path.join(DUMP, src_rel)
        data = read(src)
        if data:
            write_text(os.path.join(FILES, dst_rel), data)

    # process identity overlays — never maps. cmdline is NUL-terminated, no LF.
    write_bytes(os.path.join(FILES, "proc", "self", "cmdline"), b"com.MobileTicket\x00")
    write_bytes(os.path.join(FILES, "proc", "10231", "cmdline"), b"com.MobileTicket\x00")
    cgroup = (
        "3:cpuset:/top-app\n"
        "2:cpu:/system\n"
        "1:blkio:/\n"
        "0::/uid_%d/pid_10231\n" % TICKET_UID
    )
    write_text(os.path.join(FILES, "proc", "self", "cgroup"), cgroup)
    write_text(os.path.join(FILES, "proc", "10231", "cgroup"), cgroup)
    status = (
        "Name:\tcom.MobileTicket\n"
        "Umask:\t0077\n"
        "State:\tS (sleeping)\n"
        "Tgid:\t10231\n"
        "Ngid:\t0\n"
        "Pid:\t10231\n"
        "PPid:\t1\n"
        "TracerPid:\t0\n"
        "Uid:\t{u}\t{u}\t{u}\t{u}\n"
        "Gid:\t{u}\t{u}\t{u}\t{u}\n"
        "FDSize:\t256\n"
        "Groups:\t3001 3002 3003 9997 20273 50273\n"
        "Threads:\t37\n"
    ).format(u=TICKET_UID)
    write_text(os.path.join(FILES, "proc", "self", "status"), status)
    write_text(os.path.join(FILES, "proc", "10231", "status"), status)

    netsrc = os.path.join(DUMP, "sys", "class", "net")
    if os.path.isdir(netsrc):
        dstd = os.path.join(FILES, "sys", "class", "net")
        if os.path.isdir(dstd):
            shutil.rmtree(dstd)
        shutil.copytree(netsrc, dstd)
        print("copied net sysfs")

    cpusrc = os.path.join(DUMP, "sys", "devices", "system", "cpu")
    if os.path.isdir(cpusrc):
        dstd = os.path.join(FILES, "sys", "devices", "system", "cpu")
        os.makedirs(dstd, exist_ok=True)
        for name in ("online", "possible", "present"):
            src = os.path.join(cpusrc, name)
            if os.path.isfile(src):
                shutil.copy2(src, os.path.join(dstd, name))


def parse_getprop(text):
    props = {}
    for line in text.splitlines():
        m = re.match(r"^\[([^\]]+)\]: \[(.*)\]\s*$", line)
        if m:
            props[m.group(1)] = m.group(2)
    return props


def parse_settings(text):
    out = {}
    for line in text.splitlines():
        if "=" in line:
            k, v = line.split("=", 1)
            k, v = k.strip(), v.strip()
            if k and v != "null":
                out[k] = v
    return out


def parcel_digits(text):
    if "not fully consumed" in text or "ffffffe" in text.lower():
        return ""
    chars = re.findall(r"'([^']*)'", text)
    digits = re.sub(r"[^0-9]", "", "".join(chars))
    return digits


def parse_auxv(od_text):
    hexes = re.findall(r"[0-9a-fA-F]{2}", od_text)
    raw = bytes(int(h, 16) for h in hexes)
    hwcap64 = 0
    hwcap2_64 = 0
    for i in range(0, len(raw) - 15, 16):
        typ = struct.unpack_from("<Q", raw, i)[0]
        val = struct.unpack_from("<Q", raw, i + 8)[0]
        if typ == 16:
            hwcap64 = val
        elif typ == 26:
            hwcap2_64 = val
    return hwcap64, hwcap2_64


def parse_statfs(text):
    out = {}
    cur = None
    for line in text.splitlines():
        if line.startswith("==="):
            cur = line.strip("=")
            continue
        if not cur:
            continue
        parts = line.split()
        if len(parts) >= 6:
            bsize, blocks, bfree, bavail, files, ffree = [int(x) for x in parts[:6]]
            rec = {
                "blockSize": bsize,
                "blocks": blocks,
                "blocksFree": bfree,
                "blocksAvailable": bavail,
                "files": files,
                "filesFree": ffree,
            }
            if len(parts) >= 7:
                rec["fragmentSize"] = int(parts[6])
            out[cur] = rec
    return out


def parse_mounts(text):
    mounts = []
    seen = set()
    mount_id = 1
    for line in text.splitlines():
        parts = line.split()
        if len(parts) < 4:
            continue
        target = parts[1]
        if target in seen:
            continue
        seen.add(target)
        mounts.append({
            "source": parts[0],
            "target": target,
            "fileSystemType": parts[2],
            "options": parts[3],
            "dump": 0,
            "pass": 0,
            "mountId": mount_id,
            "parentId": 1,
            "major": 0,
            "minor": mount_id,
            "root": "/",
            "mountOptions": parts[3],
            "superOptions": parts[3],
        })
        mount_id += 1
        if mount_id > 80:
            break
    if mounts:
        mounts[0]["parentId"] = 1
    return mounts


def parse_system_stat(text):
    stat = {}
    dirs = {}
    for line in text.splitlines():
        parts = line.split("|")
        if len(parts) < 6:
            continue
        path, size, mtime, mode_hex, uid, gid = parts[:6]
        if not path.startswith("/"):
            continue
        try:
            mode = int(mode_hex, 16)
            rec = {
                "mode": mode,
                "uid": int(uid),
                "gid": int(gid),
                "size": int(size),
                "blockSize": 4096,
                "mtimeMillis": int(mtime) * 1000,
                "ctimeMillis": int(mtime) * 1000,
                "atimeMillis": int(mtime) * 1000,
            }
            if rec["size"] >= 0:
                rec["blocks"] = max(1, (rec["size"] + 4095) // 4096) if rec["size"] else 1
            stat[path] = rec
        except Exception:
            continue
        parent = os.path.dirname(path)
        name = os.path.basename(path)
        if parent:
            dirs.setdefault(parent, [])
            if name and name not in dirs[parent]:
                dirs[parent].append(name)
    return stat, dirs


def parse_packages(list_text, dumpsys_text):
    infos = {}
    for line in list_text.splitlines():
        m = re.search(r"package:(?:=)?(\S+)=([A-Za-z0-9_.]+)", line)
        if not m:
            m = re.search(r"package:(\S+)=([A-Za-z0-9_.]+)", line)
            if not m:
                continue
        src, pkg = m.group(1), m.group(2)
        uid_m = re.search(r"uid:(\d+)", line)
        vc_m = re.search(r"versionCode:(\d+)", line)
        infos[pkg] = {
            "packageName": pkg,
            "sourceDir": src,
            "dataDir": "/data/user/0/" + pkg,
            "uid": int(uid_m.group(1)) if uid_m else 10000,
            "versionCode": int(vc_m.group(1)) if vc_m else 1,
            "versionName": "1.0",
            "enabled": True,
            "systemApp": src.startswith(("/system", "/product", "/vendor", "/system_ext", "/apex")),
            "signaturesHex": [hashlib.md5(pkg.encode()).hexdigest().upper()],
        }

    current = None
    for line in dumpsys_text.splitlines():
        m = re.search(r"Package \[([A-Za-z0-9_.]+)\]", line)
        if m:
            current = m.group(1)
            infos.setdefault(current, {
                "packageName": current,
                "sourceDir": "/data/app/" + current,
                "dataDir": "/data/user/0/" + current,
                "uid": 10000,
                "versionCode": 1,
                "versionName": "1.0",
                "enabled": True,
                "systemApp": False,
                "signaturesHex": [hashlib.md5(current.encode()).hexdigest().upper()],
            })
            continue
        if not current or current not in infos:
            continue
        p = infos[current]
        m = re.search(r"versionName=(\S+)", line)
        if m:
            p["versionName"] = m.group(1).strip()
        m = re.search(r"versionCode=(\d+)", line)
        if m:
            p["versionCode"] = int(m.group(1))
        m = re.search(r"userId=(\d+)", line)
        if m:
            p["uid"] = int(m.group(1))
        m = re.search(r"codePath=(\S+)", line)
        if m:
            p["sourceDir"] = m.group(1)
        m = re.search(r"dataDir=(\S+)", line)
        if m:
            p["dataDir"] = m.group(1)
        m = re.search(r"firstInstallTime=(\d{13,})", line)
        if m:
            p["firstInstallTimeMillis"] = int(m.group(1))
        else:
            m = re.search(r"firstInstallTime=(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})", line)
            if m:
                try:
                    p["firstInstallTimeMillis"] = int(time.mktime(
                        time.strptime(m.group(1), "%Y-%m-%d %H:%M:%S"))) * 1000
                except Exception:
                    pass
        m = re.search(r"lastUpdateTime=(\d{13,})", line)
        if m:
            p["lastUpdateTimeMillis"] = int(m.group(1))
        else:
            m = re.search(r"lastUpdateTime=(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})", line)
            if m:
                try:
                    p["lastUpdateTimeMillis"] = int(time.mktime(
                        time.strptime(m.group(1), "%Y-%m-%d %H:%M:%S"))) * 1000
                except Exception:
                    pass
        m = re.search(r"pkgFlags=\[([^\]]*)\]", line)
        if m:
            flags = 0
            if "SYSTEM" in m.group(1):
                flags |= 1
                p["systemApp"] = True
            p["applicationFlags"] = flags
        for pat in (
            r"signatures=PackageSignatures\{[^}]*\[([0-9A-Fa-f]{16,})\]",
            r"SHA-256(?: digest)?:?\s+([0-9A-Fa-f:]+)",
            r"signatures:\[([0-9a-fA-F]{16,})\]",
        ):
            m = re.search(pat, line)
            if m:
                h = re.sub(r"[^0-9A-Fa-f]", "", m.group(1)).upper()
                if len(h) >= 16 and len(h) % 2 == 0:
                    p["signaturesHex"] = [h[:64]]

    pkgs = list(infos.values())
    pkgs.sort(key=lambda x: x["packageName"])
    found = False
    for p in pkgs:
        if p["packageName"] == "com.MobileTicket":
            p["versionName"] = "5.9.6.6"
            p["versionCode"] = TICKET_VERSION_CODE
            p["uid"] = p.get("uid") if p.get("uid") and p.get("uid") >= 10000 else TICKET_UID
            p["signaturesHex"] = [TICKET_SIG]
            found = True
    if not found:
        pkgs.insert(0, {
            "packageName": "com.MobileTicket",
            "versionName": "5.9.6.6",
            "versionCode": TICKET_VERSION_CODE,
            "sourceDir": "/data/app/~~ticket/com.MobileTicket-fixed/base.apk",
            "dataDir": "/data/user/0/com.MobileTicket",
            "uid": TICKET_UID,
            "enabled": True,
            "systemApp": False,
            "signaturesHex": [TICKET_SIG],
        })
    return pkgs[:300]


def parse_sensors_full(text):
    types, names, vendors, versions = [], {}, {}, {}
    string_types, maxr, reso, powers, mind, wake, ids, modes, samples = {}, {}, {}, {}, {}, {}, {}, {}, {}
    pat = re.compile(
        r"0x([0-9a-fA-F]+)\)\s+(.+?)\s+\|\s+(.+?)\s+\|\s+ver:\s*(\d+)\s+\|\s+type:\s+([^\s(]+)\((\d+)\)",
        re.M,
    )
    detail = re.compile(
        r"maxRange=([0-9.eE+-]+).*?resolution=([0-9.eE+-]+).*?power=([0-9.eE+-]+).*?minDelay=(\d+)",
        re.S,
    )
    blocks = re.split(r"(?=0x[0-9a-fA-F]+\))", text)
    for block in blocks:
        m = pat.search(block)
        if not m:
            continue
        typ = int(m.group(6))
        if typ < 1 or typ > SENSOR_TYPE_MAX or typ in types:
            continue
        types.append(typ)
        k = str(typ)
        names[k] = m.group(2).strip()[:256]
        vendors[k] = m.group(3).strip()[:256]
        versions[k] = int(m.group(4))
        string_types[k] = m.group(5).strip()[:256]
        handle = int(m.group(1), 16)
        ids[k] = handle if handle <= 2147483647 else -1
        wake[k] = "wake-up" in names[k].lower() or "wake-up" in block[:200].lower()
        modes[k] = 1 if typ == 5 else 0
        d = detail.search(block)
        if d:
            maxr[k] = float(d.group(1))
            reso[k] = float(d.group(2))
            powers[k] = float(d.group(3))
            mind[k] = int(d.group(4))
        else:
            maxr[k], reso[k], powers[k], mind[k] = 1.0, 0.001, 0.1, 10000
        if typ == 1:
            samples[k] = [0.08, -0.04, 9.81]
        elif typ == 2:
            samples[k] = [16.2, -7.8, 39.5]
        elif typ == 4:
            samples[k] = [0.001, -0.001, 0.0]
        elif typ == 5:
            samples[k] = [120.0]
        else:
            samples[k] = [0.0]
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


def parse_gles(text):
    vendor = renderer = version = "unknown"
    extensions = []
    m = re.search(r"GLES:\s*(.+)", text)
    if m:
        parts = [p.strip() for p in m.group(1).split(",")]
        if len(parts) >= 1:
            vendor = parts[0][:256]
        if len(parts) >= 2:
            renderer = parts[1][:256]
        if len(parts) >= 3:
            version = parts[2][:256]
    if vendor == "unknown":
        m = re.search(r"GL_VENDOR\s+(\S.+)", text)
        if m:
            vendor = m.group(1).strip()[:256]
        m = re.search(r"GL_RENDERER\s+(\S.+)", text)
        if m:
            renderer = m.group(1).strip()[:256]
        m = re.search(r"GL_VERSION\s+(\S.+)", text)
        if m:
            version = m.group(1).strip()[:256]
    m = re.search(r"GL_EXTENSIONS\s+(.+)", text)
    if m:
        extensions = [t for t in m.group(1).split() if t][:128]
    if vendor == "unknown":
        vendor = "Qualcomm"
    if renderer == "unknown":
        renderer = "Adreno (TM) 730"
    if version == "unknown":
        version = "OpenGL ES 3.2"
    if not extensions:
        extensions = ["GL_EXT_texture_format_BGRA8888", "GL_OES_EGL_image"]
    return {
        "vendor": vendor,
        "renderer": renderer,
        "version": version,
        "shadingLanguageVersion": "OpenGL ES GLSL ES 3.20",
        "extensions": extensions,
        "eglVendor": "Android",
        "eglVersion": "1.5 Android META-EGL",
        "eglExtensions": ["EGL_KHR_image_base", "EGL_KHR_fence_sync", "EGL_KHR_gl_texture_2D_image"],
    }


def hex_ipv4_le(s):
    if len(s) != 8:
        return "0.0.0.0"
    b = bytes.fromhex(s)
    return "%d.%d.%d.%d" % (b[3], b[2], b[1], b[0])


def parse_route(text, iface_names):
    rows = []
    for line in text.splitlines()[1:]:
        parts = line.split()
        if len(parts) < 11:
            continue
        name = parts[0]
        if name not in iface_names:
            continue
        rows.append({
            "interfaceName": name,
            "destination": hex_ipv4_le(parts[1]),
            "gateway": hex_ipv4_le(parts[2]),
            "flags": int(parts[3], 16),
            "refCount": int(parts[4], 16) if re.fullmatch(r"[0-9a-fA-F]+", parts[4]) else int(parts[4]),
            "use": int(parts[5], 16) if re.fullmatch(r"[0-9a-fA-F]+", parts[5]) else int(parts[5]),
            "metric": int(parts[6], 16) if re.fullmatch(r"[0-9a-fA-F]+", parts[6]) else int(parts[6]),
            "mask": hex_ipv4_le(parts[7]),
            "mtu": int(parts[8], 16) if re.fullmatch(r"[0-9a-fA-F]+", parts[8]) else int(parts[8]),
            "window": int(parts[9], 16) if re.fullmatch(r"[0-9a-fA-F]+", parts[9]) else int(parts[9]),
            "irtt": int(parts[10], 16) if re.fullmatch(r"[0-9a-fA-F]+", parts[10]) else int(parts[10]),
        })
        if len(rows) >= 40:
            break
    return rows


def parse_netdev(text, iface_names):
    rows = []
    seen = set()
    for line in text.splitlines():
        if ":" not in line:
            continue
        name, rest = line.split(":", 1)
        name = name.strip()
        if name not in iface_names or name in seen:
            continue
        nums = rest.split()
        if len(nums) < 16:
            continue
        vals = [int(x) for x in nums[:16]]
        seen.add(name)
        rows.append({
            "interfaceName": name,
            "rxBytes": vals[0], "rxPackets": vals[1], "rxErrors": vals[2], "rxDrop": vals[3],
            "rxFifo": vals[4], "rxFrame": vals[5], "rxCompressed": vals[6], "rxMulticast": vals[7],
            "txBytes": vals[8], "txPackets": vals[9], "txErrors": vals[10], "txDrop": vals[11],
            "txFifo": vals[12], "txCollisions": vals[13], "txCarrier": vals[14], "txCompressed": vals[15],
        })
    return rows


def parse_arp(text, iface_names):
    rows = []
    seen = set()
    for line in text.splitlines()[1:]:
        parts = line.split()
        if len(parts) < 6:
            continue
        ip, hwtype, flags, mac, _, name = parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]
        if name not in iface_names:
            continue
        key = name + "|" + ip
        if key in seen:
            continue
        if not re.fullmatch(r"([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}", mac):
            continue
        seen.add(key)
        rows.append({
            "interfaceName": name,
            "ipv4": ip,
            "hardwareType": int(hwtype, 16) if hwtype.startswith("0x") else int(hwtype),
            "flags": int(flags, 16) if flags.startswith("0x") else int(flags),
            "mac": mac.lower(),
        })
        if len(rows) >= 20:
            break
    return rows


def parse_wireless(text, iface_names):
    entries = []
    for line in text.splitlines():
        m = re.match(
            r"\s*(\S+):\s+([0-9a-fA-F]+)\s+(-?\d+)\.?\s+(-?\d+)\.?\s+(-?\d+)",
            line,
        )
        if not m:
            continue
        name = m.group(1)
        if name not in iface_names:
            continue
        entries.append({
            "interfaceName": name,
            "status": int(m.group(2), 16),
            "linkQuality": max(0, min(255, int(m.group(3)))),
            "level": max(-256, min(255, int(m.group(4)))),
            "noise": max(-256, min(255, int(m.group(5)))),
            "linkUpdated": True,
            "levelUpdated": True,
            "noiseUpdated": True,
            "discardNwid": 0,
            "discardCrypt": 0,
            "discardFragment": 0,
            "discardRetries": 0,
            "discardMisc": 0,
            "missedBeacon": 0,
        })
    return {"wirelessExtensionsVersion": 22, "entries": entries}


def parse_wifi(status, dumpsys, props):
    ssid = ""
    bssid = ""
    rssi = -60
    freq = 5180
    speed = 433
    m = re.search(r'"([^"]+)"\s+[0-9a-fA-F:]{17}\s+rssi=(-?\d+)\s+f=(\d+)\s+.*link=(\d+)', dumpsys)
    if m:
        ssid, rssi, freq, speed = m.group(1), int(m.group(2)), int(m.group(3)), int(m.group(4))
    if not ssid:
        m = re.search(r'ssid:\s*"([^"]+)"', status, re.I)
        if not m:
            m = re.search(r'mSSID[^\n]*"([^"]+)"', dumpsys)
        if m:
            ssid = m.group(1)
    m = re.search(r"BSSID[=:\s]+([0-9a-fA-F:]{17})", dumpsys)
    if m:
        bssid = m.group(1).lower()
    if bssid in ("00:00:00:00:00:00", ""):
        m = re.search(r'"[^"]+"\s+([0-9a-fA-F:]{17})\s+rssi=', dumpsys)
        if m:
            bssid = m.group(1).lower()
    m = re.search(r"RSSI:\s*(-?\d+)", dumpsys)
    if m:
        rssi = int(m.group(1))
    gw = props.get("dhcp.wlan0.gateway") or ""
    mask = props.get("dhcp.wlan0.mask") or "255.255.255.0"
    m = re.search(r"default via (\d+\.\d+\.\d+\.\d+)", read(extra_path("system", "ip_route.txt")))
    if not m:
        m = re.search(r"default via (\d+\.\d+\.\d+\.\d+)", read(os.path.join(DUMP, "system", "ip_route.txt")))
    if m:
        gw = m.group(1)
    conn = read(os.path.join(DUMP, "system", "connectivity.txt"))
    m = re.search(r"0\.0\.0\.0/0 -> (\d+\.\d+\.\d+\.\d+)", conn)
    if m:
        gw = m.group(1)
    m = re.search(r"(\d+\.\d+\.\d+\.\d+)/(\d+)", conn)
    prefix = 24
    if m:
        prefix = int(m.group(2))
        bits = (0xFFFFFFFF << (32 - prefix)) & 0xFFFFFFFF
        mask = ".".join(str((bits >> (24 - 8 * i)) & 0xFF) for i in range(4))
    dns = []
    dns_found = re.findall(r"DnsAddresses:\s*\[([^\]]+)\]", conn)
    if dns_found:
        dns = [x.strip().strip("/") for x in dns_found[0].split(",") if x.strip()]
    if not dns:
        dns = [x for x in (props.get("net.dns1"), props.get("net.dns2")) if x]
    proxy_host, proxy_port, dhcp, domains = "", 0, "", ""
    m = re.search(r"HttpProxy:\s*\[([^\]]+)\]\s+(\d+)", conn)
    if m:
        proxy_host, proxy_port = m.group(1), int(m.group(2))
    m = re.search(r"ServerAddress:\s*/(\d+\.\d+\.\d+\.\d+)", conn)
    if m:
        dhcp = m.group(1)
    m = re.search(r"Domains:\s*(\S+)", conn)
    if m:
        domains = m.group(1)
    return ssid, bssid, rssi, freq, speed, gw, mask, dns, proxy_host, proxy_port, dhcp, domains


def extra_path(*parts):
    return os.path.join(DUMP, "extra", *parts)


def parse_location(text):
    locs = []
    for m in re.finditer(
        r"last location=Location\[(\w+)\s+([0-9.+-]+),([0-9.+-]+)(?:\s+hAcc=([0-9.]+))?(?:\s+alt=([0-9.+-]+))?",
        text,
    ):
        locs.append({
            "provider": m.group(1),
            "latitude": float(m.group(2)),
            "longitude": float(m.group(3)),
            "altitude": float(m.group(5)) if m.group(5) else 0.0,
            "accuracyMeters": float(m.group(4)) if m.group(4) else 20.0,
            "timeMillis": int(time.time() * 1000),
            "elapsedRealtimeNanos": 0,
            "mock": False,
        })
        if len(locs) >= 3:
            break
    if not locs:
        m = re.search(r"Location\[(\w+)\s+([0-9.+-]+),([0-9.+-]+)", text)
        if m:
            locs.append({
                "provider": m.group(1),
                "latitude": float(m.group(2)),
                "longitude": float(m.group(3)),
                "altitude": 0.0,
                "accuracyMeters": 20.0,
                "timeMillis": int(time.time() * 1000),
                "elapsedRealtimeNanos": 0,
                "mock": False,
            })
    return locs


def parse_tcp(text, ipv6=False):
    rows = []
    lines = text.splitlines()
    if len(lines) < 2:
        return rows
    slot = 0
    for line in lines[1:]:
        parts = line.split()
        if len(parts) < 4:
            continue
        local, rem, state = parts[1], parts[2], parts[3]

        def split_ip(s, v6):
            ip, port = s.rsplit(":", 1)
            port_i = int(port, 16)
            if v6:
                return ip.lower(), port_i
            if len(ip) == 8:
                b = bytes.fromhex(ip)
                return "%d.%d.%d.%d" % (b[3], b[2], b[1], b[0]), port_i
            return "0.0.0.0", port_i

        lip, lp = split_ip(local, ipv6)
        rip, rp = split_ip(rem, ipv6)
        rec = {
            "slot": slot,
            "localPort": lp,
            "remotePort": rp,
            "stateHex": state.upper()[-2:].zfill(2),
        }
        if len(parts) >= 8:
            try:
                rec["uid"] = int(parts[7])
            except Exception:
                pass
        if ipv6:
            rec["localIpv6"] = (re.sub(r"[^0-9a-fA-F]", "", lip) + "0" * 32)[:32].lower()
            rec["remoteIpv6"] = (re.sub(r"[^0-9a-fA-F]", "", rip) + "0" * 32)[:32].lower()
        else:
            rec["localIpv4"] = lip
            rec["remoteIpv4"] = rip
        rows.append(rec)
        slot += 1
        if slot >= 40:
            break
    return rows


def drm_hex_from_dumpsys(text, android_id, serial):
    m = re.search(r"deviceUniqueId[=:\s]+([0-9a-fA-F]{16,})", text)
    if m:
        return m.group(1).lower()
    return hashlib.sha256(("widevine:" + android_id + ":" + serial).encode()).hexdigest()


def parse_accounts(text):
    acc = []
    seen = set()
    for m in re.finditer(r"Account \{name=([^,]+), type=([^}]+)\}", text):
        name, typ = m.group(1).strip(), m.group(2).strip()
        key = name + "|" + typ
        if name and typ and key not in seen:
            seen.add(key)
            acc.append({"name": name, "type": typ})
        if len(acc) >= 20:
            break
    return acc


def parse_input_methods(text, settings_secure):
    imes = []
    seen = set()
    default = settings_secure.get("default_input_method", "")
    for m in re.finditer(r"mId=(\S+)", text):
        ime_id = m.group(1).strip()
        if ime_id and ime_id not in seen:
            seen.add(ime_id)
            imes.append({"id": ime_id, "enabled": True})
    if default and default not in seen:
        imes.insert(0, {"id": default, "enabled": True})
    if not imes and default:
        imes.append({"id": default, "enabled": True})
    return imes[:20]


def parse_bluetooth(text, settings_secure):
    name = settings_secure.get("bluetooth_name") or "Pixel 6"
    addr = (settings_secure.get("bluetooth_address") or "00:00:00:00:00:00").lower()
    enabled = True
    state = 12
    m = re.search(r"state:\s*(\d+)", text, re.I)
    if m:
        st = int(m.group(1))
        if st in (10, 11, 12, 13):
            state = st
            enabled = state == 12
    if re.search(r"STATE_OFF|disabled", text, re.I) and state == 12:
        if "STATE_ON" not in text:
            state, enabled = 10, False
    if not re.fullmatch(r"([0-9a-f]{2}:){5}[0-9a-f]{2}", addr):
        addr = "00:00:00:00:00:00"
    return {
        "name": name[:256],
        "address": addr,
        "enabled": enabled,
        "state": state,
        "scanMode": 21,
        "discovering": False,
    }


def parse_audio(text):
    ringer = 2
    mode = 0
    m = re.search(r"ringer mode:(\d+)", text, re.I)
    if m:
        ringer = max(0, min(2, int(m.group(1))))
    m = re.search(r"mode:(\d+)", text, re.I)
    if m:
        mode = max(0, min(7, int(m.group(1))))
    music = bool(re.search(r"music active:\s*true", text, re.I))
    speaker = bool(re.search(r"speakerphone:\s*true", text, re.I))
    streams = []
    for st, label in ((0, "voice"), (2, "ring"), (3, "music"), (4, "alarm"), (5, "notification")):
        m = re.search(r"- STREAM_%s:.*?MUTED:(\w+).*?Current:\s*(\d+).*?Max:\s*(\d+)" % label.upper(), text, re.S)
        vol, mx = 5, 15
        if m:
            vol, mx = int(m.group(2)), int(m.group(3))
        streams.append({"streamType": st, "volume": vol, "maxVolume": mx, "minVolume": 0})
    return {
        "musicActive": music,
        "speakerphoneOn": speaker,
        "ringerMode": ringer,
        "mode": mode,
        "streamVolumes": streams,
    }


FEATURE_NAME_RE = re.compile(r"^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$")


def parse_features(text):
    feats = []
    seen = set()
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("feature:"):
            line = line[8:].strip()
        if not line or line.startswith("reqGlEsVersion"):
            continue
        version = None
        name = line
        m = re.match(r"^([A-Za-z0-9_.]+)=(\d+)$", line)
        if m:
            name, version = m.group(1), int(m.group(2))
        if not FEATURE_NAME_RE.match(name) or name in seen:
            continue
        seen.add(name)
        rec = {"name": name}
        if version is not None:
            rec["version"] = version
        feats.append(rec)
    return feats


def parse_processes(ps_text):
    rows = []
    seen = set()
    for line in ps_text.splitlines()[1:]:
        parts = line.split(None, 3)
        if len(parts) < 3:
            continue
        try:
            pid = int(parts[0])
        except Exception:
            continue
        if pid in seen or pid <= 0:
            continue
        seen.add(pid)
        comm = parts[2].strip()
        if comm.startswith("[") or comm.startswith("kworker") or pid < 200:
            continue
        comm = re.sub(r"[^A-Za-z0-9._:-]", "", comm)[:16]
        if not comm:
            continue
        cmd = parts[3].strip() if len(parts) > 3 else comm
        exe = cmd.split()[0] if cmd.startswith("/") else "/system/bin/" + comm
        rows.append({
            "pid": pid,
            "comm": comm,
            "cmdline": [cmd] if cmd else [comm],
            "exe": exe,
        })
        if len(rows) >= 40:
            break
    return rows


def meminfo_kb(text, key):
    m = re.search(r"%s:\s+(\d+)" % re.escape(key), text)
    return int(m.group(1)) if m else 0


def merge():
    profile = json.loads(read(PROFILE))
    extra_getprop = read(extra_path("system", "getprop.txt")) or read(os.path.join(DUMP, "props", "getprop.txt"))
    props = parse_getprop(extra_getprop)
    if props:
        profile["android"]["properties"] = props

    sec = parse_settings(read(os.path.join(DUMP, "system", "settings_secure.txt")) or
                         read(os.path.join(DUMP, "props", "settings_secure.txt")))
    glo = parse_settings(read(os.path.join(DUMP, "system", "settings_global.txt")) or
                         read(os.path.join(DUMP, "props", "settings_global.txt")))
    sysset = parse_settings(read(os.path.join(DUMP, "system", "settings_system.txt")) or
                            read(os.path.join(DUMP, "props", "settings_system.txt")))
    settings = profile["android"].setdefault("settings", {})
    if sec:
        settings["secure"] = sec
    if glo:
        settings["global"] = glo
    if sysset:
        settings["system"] = sysset

    hwcap64, hwcap2 = parse_auxv(read(extra_path("proc", "auxv.od")))
    if hwcap64:
        profile["linux"]["auxv"]["hwcap64"] = hwcap64
        profile["linux"]["auxv"]["hwcap2_64"] = hwcap2

    meminfo = read(extra_path("proc", "meminfo")) or read(os.path.join(DUMP, "proc", "meminfo"))
    load = (read(extra_path("proc", "loadavg")) or read(os.path.join(DUMP, "proc", "loadavg"))).split()
    up = (read(extra_path("proc", "uptime")) or read(os.path.join(DUMP, "system", "uptime.txt"))).split()
    unit = 4096
    total_kb = meminfo_kb(meminfo, "MemTotal")
    free_kb = meminfo_kb(meminfo, "MemFree")
    shared_kb = meminfo_kb(meminfo, "Shmem")
    buf_kb = meminfo_kb(meminfo, "Buffers")
    swap_kb = meminfo_kb(meminfo, "SwapTotal")
    swap_free_kb = meminfo_kb(meminfo, "SwapFree")
    sysinfo = profile["linux"].setdefault("sysinfo", {})
    if total_kb:
        sysinfo["totalRam"] = (total_kb * 1024) // unit
        sysinfo["freeRam"] = (free_kb * 1024) // unit
        sysinfo["sharedRam"] = (shared_kb * 1024) // unit
        sysinfo["bufferRam"] = (buf_kb * 1024) // unit
        sysinfo["totalSwap"] = (swap_kb * 1024) // unit
        sysinfo["freeSwap"] = (swap_free_kb * 1024) // unit
        sysinfo["memUnit"] = unit
    if len(load) >= 3:
        sysinfo["loads"] = [int(float(load[i]) * 65536) for i in range(3)]
    if up:
        sysinfo["uptime"] = int(float(up[0]))
    sysinfo["procs"] = min(65535, max(1, sysinfo.get("procs", 500)))

    proc = profile["linux"].setdefault("proc", {})
    ent = read(extra_path("proc", "sys", "kernel", "random", "entropy_avail")).strip()
    pool = read(extra_path("proc", "sys", "kernel", "random", "poolsize")).strip()
    if ent.isdigit():
        proc["entropyAvail"] = int(ent)
    if pool.isdigit():
        proc["randomPoolSize"] = int(pool)
    proc["state"] = "S"
    proc["threadCount"] = 24
    proc["dumpable"] = 0
    proc["nice"] = 0
    proc["seccompMode"] = 2
    proc["noNewPrivs"] = True

    cmds = profile["linux"].setdefault("commands", {})
    df_txt = read(os.path.join(DUMP, "system", "df.txt")) or read(extra_path("system", "df.txt"))
    cmds["df"] = df_txt.rstrip("\n")
    cmds["/system/bin/df"] = cmds["df"]
    cmds["uptime"] = (read(extra_path("proc", "uptime")) or "").rstrip("\n")
    cmds["cat /proc/version"] = (read(extra_path("proc", "version")) or
                                 read(os.path.join(DUMP, "proc", "version"))).rstrip("\n")
    cmds["cat /proc/mounts"] = (read(extra_path("proc", "mounts")) or
                                read(os.path.join(DUMP, "proc", "mounts"))).rstrip("\n")
    cmds["uname -a"] = (read(extra_path("system", "uname.txt")) or
                        read(os.path.join(DUMP, "proc", "uname.txt"))).rstrip("\n")
    cmds["getprop"] = extra_getprop.rstrip("\n")

    files = {
        "/proc/loadavg": read(extra_path("proc", "loadavg")),
        "/proc/net/tcp": read(extra_path("proc", "net", "tcp")),
        "/proc/net/tcp6": read(extra_path("proc", "net", "tcp6")),
        "/proc/net/route": read(extra_path("proc", "net", "route")),
        "/proc/net/dev": read(extra_path("proc", "net", "dev")),
        "/proc/net/if_inet6": read(extra_path("proc", "net", "if_inet6")),
        "/proc/net/arp": read(extra_path("proc", "net", "arp")),
        "/proc/net/wireless": read(extra_path("proc", "net", "wireless")),
        "/proc/mounts": read(extra_path("proc", "mounts")),
        "/proc/version": read(extra_path("proc", "version")),
        "/proc/cpuinfo": read(extra_path("proc", "cpuinfo")),
        "/proc/meminfo": read(extra_path("proc", "meminfo")),
        "/proc/stat": read(extra_path("proc", "stat")),
        "/proc/uptime": read(extra_path("proc", "uptime")),
        "/proc/sys/kernel/random/boot_id": read(extra_path("proc", "sys", "kernel", "random", "boot_id")),
        "/proc/sys/kernel/random/entropy_avail": read(extra_path("proc", "sys", "kernel", "random", "entropy_avail")),
        "/proc/sys/kernel/random/poolsize": read(extra_path("proc", "sys", "kernel", "random", "poolsize")),
        "/sys/class/android_usb/android0/state": read(extra_path("sys", "class", "android_usb", "android0", "state")) or "CONFIGURED\n",
        "/sys/class/power_supply/battery/capacity": read(extra_path("sys", "class", "power_supply", "battery", "capacity")),
        "/sys/class/power_supply/battery/status": read(extra_path("sys", "class", "power_supply", "battery", "status")),
        "/sys/class/power_supply/battery/current_now": read(extra_path("sys", "class", "power_supply", "battery", "current_now")),
        "/sys/class/power_supply/battery/charge_full_design": read(extra_path("sys", "class", "power_supply", "battery", "charge_full_design")),
    }
    profile["linux"]["files"] = {k: v for k, v in files.items() if v}

    ps_rows = parse_processes(read(extra_path("system", "ps.txt")))
    if ps_rows:
        profile["linux"]["processes"] = ps_rows

    iface_names = {x.get("name") for x in profile["network"].get("interfaces", []) if x.get("name")}
    profile["network"]["tcp"] = parse_tcp(read(extra_path("proc", "net", "tcp")), False)
    profile["network"]["tcp6"] = parse_tcp(read(extra_path("proc", "net", "tcp6")), True)
    routes = parse_route(read(extra_path("proc", "net", "route")), iface_names)
    if routes:
        profile["network"]["ipv4Routes"] = routes
    stats = parse_netdev(read(extra_path("proc", "net", "dev")), iface_names)
    if stats:
        profile["network"]["interfaceStats"] = stats
    arps = parse_arp(read(extra_path("proc", "net", "arp")), iface_names)
    if arps:
        profile["network"]["arpEntries"] = arps
    wireless = parse_wireless(read(extra_path("proc", "net", "wireless")), iface_names)
    if wireless["entries"]:
        profile["network"]["wirelessProcStats"] = wireless

    mounts = parse_mounts(read(extra_path("proc", "mounts")) or read(os.path.join(DUMP, "proc", "mounts")))
    statfs = parse_statfs(read(extra_path("system", "statfs.txt")))
    sys_stat, sys_dirs = parse_system_stat(read(extra_path("system", "system_stat.txt")))
    font_stat, font_dirs = parse_system_stat(read(extra_path("system", "fonts_stat.txt")))
    sys_stat.update(font_stat)
    sys_dirs.update(font_dirs)
    now = int(time.time() * 1000)
    sys_stat["/data/user/0/com.MobileTicket"] = {
        "mode": 16877, "uid": TICKET_UID, "gid": TICKET_UID, "size": 4096, "blockSize": 4096,
        "blocks": 1, "mtimeMillis": now, "ctimeMillis": now, "atimeMillis": now,
    }
    sys_stat["/storage/emulated/0"] = {
        "mode": 16877, "uid": 1023, "gid": 1023, "size": 4096, "blockSize": 4096,
        "blocks": 1, "mtimeMillis": now, "ctimeMillis": now, "atimeMillis": now,
    }
    sys_stat["/system"] = sys_stat.get("/system", {
        "mode": 16877, "uid": 0, "gid": 0, "size": 4096, "blockSize": 4096,
        "blocks": 1, "mtimeMillis": now,
    })
    fs = profile.setdefault("filesystem", {})
    fs["mounts"] = mounts
    fs["statfs"] = statfs
    fs["stat"] = sys_stat
    fs["directories"] = {k: v[:80] for k, v in sys_dirs.items()}
    fs.setdefault("externalStorage", {
        "directory": "/storage/emulated/0",
        "state": "mounted",
        "emulated": True,
        "removable": False,
    })
    fs.setdefault("systemDirectories", {
        "rootDirectory": "/system",
        "dataDirectory": "/data",
        "downloadCacheDirectory": "/cache",
        "storageDirectory": "/storage",
    })

    profile["graphics"] = parse_gles(read(os.path.join(DUMP, "system", "surfaceflinger.txt")))

    sensors = parse_sensors_full(read(os.path.join(DUMP, "system", "sensorservice.txt")))
    if sensors["types"]:
        profile["android"]["sensors"] = sensors

    pkgs = parse_packages(
        read(os.path.join(DUMP, "system", "packages_list.txt")) or
        read(os.path.join(DUMP, "system", "packages.txt")),
        read(os.path.join(DUMP, "system", "dumpsys_packages.txt")),
    )
    profile["android"]["packages"] = pkgs
    ticket = next((p for p in pkgs if p.get("packageName") == "com.MobileTicket"), None)
    ticket_uid = ticket.get("uid", TICKET_UID) if ticket else TICKET_UID
    proc_ident = profile.setdefault("process", {})
    for key in ("uid", "gid", "euid", "egid"):
        proc_ident[key] = ticket_uid
    proc_ident["supplementaryGids"] = [3001, 3002, 3003, 9997, ticket_uid + 10000, ticket_uid + 40000]
    linux_proc = profile["linux"].setdefault("proc", {})
    linux_proc["selinuxContext"] = TICKET_SELINUX
    android_root = profile["android"]
    android_root["packageName"] = "com.MobileTicket"
    android_root["versionName"] = "5.9.6.6"
    android_root["versionCode"] = TICKET_VERSION_CODE
    android_root["dataDir"] = "/data/user/0/com.MobileTicket"
    feats = parse_features(read(os.path.join(DUMP, "system", "features.txt")))
    if feats:
        profile["android"]["features"] = feats

    android_id = profile["android"]["identifiers"]["androidId"]
    serial = profile["android"]["build"]["SERIAL"]
    gaid = read(extra_path("system", "gaid.txt")).strip() or read(os.path.join(DUMP, "system", "gaid.txt")).strip()
    ids = profile["android"]["identifiers"]
    if re.match(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", gaid.lower()):
        ids["advertisingId"] = gaid.lower()
        ids["limitAdTracking"] = False
    else:
        aid = ids.get("androidId") or "0000000000000000"
        ids["advertisingId"] = "%s-%s-4%s-8%s-%s" % (
            aid[:8], aid[8:12], aid[12:15], aid[:3], (aid * 2)[:12])
        ids["limitAdTracking"] = False

    dhex = drm_hex_from_dumpsys(read(os.path.join(DUMP, "system", "mediadrm.txt")), android_id, serial)
    profile["android"]["drm"] = {
        "available": True,
        "marker": "Widevine",
        "vendor": "Google",
        "version": "16.0.0",
        "description": "Widevine CDM",
        "algorithms": "AES/CBC/NoPadding,HmacSHA256",
        "securityLevel": "L1",
        "hdcpLevel": "HDCP_V2_2",
        "maxHdcpLevel": "HDCP_V2_3",
        "provisioned": True,
        "deviceUniqueIdHex": dhex,
    }

    bat = profile["android"].setdefault("battery", {})
    bat_txt = read(os.path.join(DUMP, "system", "battery.txt"))
    m = re.search(r"level:\s*(\d+)", bat_txt)
    if m:
        bat["capacityPercent"] = int(m.group(1))
    m = re.search(r"status:\s*(\d+)", bat_txt)
    if m:
        st = int(m.group(1))
        if 1 <= st <= 5:
            bat["status"] = st
    usb = "USB powered: true" in bat_txt
    ac = "AC powered: true" in bat_txt
    wireless = "Wireless powered: true" in bat_txt
    plugged = (1 if ac else 0) | (2 if usb else 0) | (4 if wireless else 0)
    bat["plugged"] = plugged
    bat["charging"] = bat.get("status") == 2
    m = re.search(r"Charge counter:\s*(-?\d+)", bat_txt)
    if m:
        bat["chargeCounterUah"] = max(0, int(m.group(1)))
    cur = read(extra_path("sys", "class", "power_supply", "battery", "current_now")).strip()
    if re.fullmatch(r"-?\d+", cur):
        bat["currentNowUa"] = int(cur)
        bat["currentAverageUa"] = int(cur)

    power = profile["android"].setdefault("power", {})
    power_txt = read(os.path.join(DUMP, "system", "power.txt"))
    power["interactive"] = "mWakefulness=Awake" in power_txt or "Display State=ON" in read(
        os.path.join(DUMP, "system", "display.txt"))
    power["powerSaveMode"] = "mLowPowerModeEnabled=true" in power_txt
    power["deviceIdleMode"] = False
    power["deviceLightIdleMode"] = False
    power["lowPowerStandbyEnabled"] = False
    power["sustainedPerformanceModeSupported"] = True
    power["rebootingUserspaceSupported"] = True
    power["ignoringBatteryOptimizations"] = False

    slot = profile["android"]["telephony"]["slots"][0]
    imei = parcel_digits(read(extra_path("system", "imei.txt"))) or slot.get("imei")
    imsi = parcel_digits(read(extra_path("system", "imsi.txt")))
    iccid = parcel_digits(read(extra_path("system", "iccid.txt")))
    sim_state_prop = props.get("gsm.sim.state", "ABSENT").split(",")[0]
    if imei and 14 <= len(imei) <= 16:
        slot["imei"] = imei
        slot["deviceId"] = imei
    if imsi and 14 <= len(imsi) <= 15:
        slot["subscriberId"] = imsi
    else:
        slot.pop("subscriberId", None)
    if iccid and 18 <= len(iccid) <= 22:
        slot["simSerialNumber"] = iccid
    else:
        slot.pop("simSerialNumber", None)
    slot["simState"] = 1 if sim_state_prop == "ABSENT" else 5
    tel = profile["android"]["telephony"]
    op = (props.get("gsm.operator.numeric") or "").split(",")[0].strip()
    op_name = (props.get("gsm.operator.alpha") or "").split(",")[0].strip()
    sim_op = (props.get("gsm.sim.operator.numeric") or "").split(",")[0].strip()
    sim_name = (props.get("gsm.sim.operator.alpha") or "").split(",")[0].strip()
    if op:
        tel["networkOperator"] = op
    else:
        tel.pop("networkOperator", None)
    if op_name:
        tel["networkOperatorName"] = op_name
    else:
        tel.pop("networkOperatorName", None)
    if sim_op:
        tel["simOperator"] = sim_op
    else:
        tel.pop("simOperator", None)
    if sim_name:
        tel["simOperatorName"] = sim_name
    else:
        tel.pop("simOperatorName", None)
    tel["networkCountryIso"] = "cn"
    tel["simCountryIso"] = "cn"

    ssid, bssid, rssi, freq, speed, gw, mask, dns, proxy_host, proxy_port, dhcp, domains = parse_wifi(
        read(os.path.join(DUMP, "system", "wifi_status.txt")),
        read(os.path.join(DUMP, "system", "wifi.txt")),
        props)
    wifi = profile["network"]["wifi"]
    if ssid:
        wifi["ssid"] = ssid
    if bssid:
        wifi["bssid"] = bssid
    wifi["rssi"] = rssi
    wifi["frequencyMhz"] = freq
    wifi["linkSpeedMbps"] = speed
    wifi["enabled"] = True
    wifi["state"] = 3
    links = profile["network"]["links"]
    if gw:
        links["gatewayIpv4"] = gw
    if mask:
        links["netmaskIpv4"] = mask
    if dns:
        links["dnsServers"] = dns[:4]
    links["connected"] = True
    links["type"] = 1
    links["typeName"] = "WIFI"
    links["interfaceName"] = "wlan0"
    if proxy_host:
        links["proxyHost"] = proxy_host
        links["proxyPort"] = proxy_port
    if dhcp:
        links["dhcpServerIpv4"] = dhcp
    if domains:
        links["domains"] = domains
    if gw and gw != "0.0.0.0":
        routes = profile["network"].setdefault("ipv4Routes", [])
        if not any(r.get("destination") == "0.0.0.0" for r in routes):
            routes.append({
                "interfaceName": "wlan0",
                "destination": "0.0.0.0",
                "gateway": gw,
                "flags": 3,
                "refCount": 0,
                "use": 0,
                "metric": 0,
                "mask": "0.0.0.0",
                "mtu": 0,
                "window": 0,
                "irtt": 0,
            })

    profile["network"]["bluetooth"] = parse_bluetooth(
        read(os.path.join(DUMP, "system", "bluetooth.txt")), sec)

    loc_txt = read(os.path.join(DUMP, "system", "location.txt"))
    locs = parse_location(loc_txt)
    loc = profile["android"].setdefault("location", {})
    loc["enabled"] = bool(re.search(r"Location Setting:\s*true", loc_txt, re.I))
    loc["providers"] = {
        "gps": bool(re.search(r"gps provider:.*?enabled=true", loc_txt, re.S | re.I)),
        "network": bool(re.search(r"network provider:.*?enabled=true", loc_txt, re.S | re.I)),
        "passive": bool(re.search(r"passive provider:.*?enabled=true", loc_txt, re.S | re.I)),
    }
    loc["lastKnownLocations"] = locs
    loc["providerCapabilities"] = {
        "gps": {
            "requiresNetwork": False, "requiresSatellite": True, "requiresCell": False,
            "hasMonetaryCost": False, "supportsAltitude": True, "supportsSpeed": True,
            "supportsBearing": True, "meetsCriteria": True, "accuracy": 1, "powerRequirement": 3,
        },
        "network": {
            "requiresNetwork": True, "requiresSatellite": False, "requiresCell": True,
            "hasMonetaryCost": False, "supportsAltitude": False, "supportsSpeed": False,
            "supportsBearing": False, "meetsCriteria": True, "accuracy": 2, "powerRequirement": 1,
        },
        "passive": {
            "requiresNetwork": False, "requiresSatellite": False, "requiresCell": False,
            "hasMonetaryCost": False, "supportsAltitude": False, "supportsSpeed": False,
            "supportsBearing": False, "meetsCriteria": True, "accuracy": 2, "powerRequirement": 1,
        },
    }

    loc_tag = profile["android"]["locale"].get("languageTag") or props.get("persist.sys.locale") or "zh-Hans-CN"
    profile["android"]["locale"]["languageTag"] = loc_tag
    profile["android"]["locale"]["languageTags"] = [loc_tag]
    profile["android"]["locale"]["timezoneId"] = props.get("persist.sys.timezone") or "Asia/Shanghai"

    d = profile["android"]["display"]
    disp_txt = read(os.path.join(DUMP, "system", "display.txt"))
    m = re.search(r"(\d+(?:\.\d+)?)\s*Hz", disp_txt)
    if m:
        d["refreshRate"] = float(m.group(1))
    m = re.search(r"([0-9.]+)\s+x\s+([0-9.]+)\s+dpi", disp_txt)
    if m:
        d["xdpi"] = float(m.group(1))
        d["ydpi"] = float(m.group(2))
    m = re.search(r"modeId (\d+)", disp_txt)
    if m:
        d["modeId"] = max(1, int(m.group(1)))
    profile["android"]["displays"] = [{
        "id": 0,
        "name": "内置屏幕",
        "flags": 3,
        "widthPixels": d["widthPixels"],
        "heightPixels": d["heightPixels"],
        "densityDpi": d["densityDpi"],
    }]
    cfg = profile["android"].setdefault("configuration", {})
    cfg["densityDpi"] = d["densityDpi"]
    cfg["screenWidthDp"] = int(round(d["widthPixels"] * 160.0 / d["densityDpi"]))
    cfg["screenHeightDp"] = int(round(d["heightPixels"] * 160.0 / d["densityDpi"]))
    cfg["smallestScreenWidthDp"] = min(cfg["screenWidthDp"], cfg["screenHeightDp"])

    acc_services = [s for s in sec.get("enabled_accessibility_services", "").split(":") if s]
    profile["android"]["accessibility"] = {
        "enabled": sec.get("accessibility_enabled", "0") == "1",
        "touchExplorationEnabled": sec.get("touch_exploration_enabled", "0") == "1",
        "highContrastTextEnabled": False,
        "services": acc_services,
    }
    profile["android"]["audio"] = parse_audio(read(os.path.join(DUMP, "system", "audio.txt")))
    accs = parse_accounts(read(os.path.join(DUMP, "system", "account.txt")))
    profile["android"]["accounts"] = accs
    profile["android"]["inputMethods"] = parse_input_methods(
        read(os.path.join(DUMP, "system", "input_method.txt")), sec)
    profile["android"]["userState"] = {
        "userId": 0,
        "serialNumber": 0,
        "userUnlocked": True,
        "systemUser": True,
        "managedProfile": False,
        "demoUser": False,
    }
    win = read(os.path.join(DUMP, "system", "window_policy.txt"))
    secure = "mShowingLockscreen=true" in win or "isKeyguardShowing=true" in win
    profile["android"]["securityState"] = {
        "keyguardLocked": secure,
        "keyguardSecure": True,
        "deviceLocked": secure,
        "deviceSecure": True,
        "biometricCanAuthenticateResult": 0,
        "biometricLastAuthenticationElapsedRealtimeMillis": -1,
    }
    profile["android"]["clipboard"] = {
        "hasPrimaryClip": False,
    }

    android_id = profile["android"]["identifiers"]["androidId"]
    frozen_uuid = (
        android_id[:8] + "-" + android_id[8:12] + "-4" + android_id[12:15]
        + "-8" + android_id[:3] + "-" + (android_id * 3)[:12]
    )
    profile["linux"]["proc"]["randomUuid"] = frozen_uuid
    # Frozen CSPRNG. /dev/urandom = b2c3d4e5 → HuiYan mt19937 seed 0xe5d4c3b2.
    profile["random"] = {
        "devRandomHex": "a1b2c3d4",
        "devUrandomHex": "b2c3d4e5",
        "devSrandomHex": "c3d4e5f6",
        "getrandomHex": "7a239e5dd15748a2ac54aba483265c47",
        "uuid": frozen_uuid,
        "stackGuardHex": "0102030405060708",
        "atRandomHex": "7a239e5dd15748a2ac54aba483265c47",
        "mediaDrmDeviceUniqueIdHex": profile["android"]["drm"].get(
            "deviceUniqueIdHex", "e0e0801c50b9333cb78332faf9e37440611aa6f01fe055f61e2091c995372328"),
    }

    with open(PROFILE, "w", encoding="utf-8") as fh:
        json.dump(profile, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("wrote", PROFILE)
    print("packages", len(profile["android"]["packages"]))
    print("props", len(profile["android"]["properties"]))
    print("settings.secure", len(profile["android"]["settings"].get("secure", {})))
    print("mounts", len(profile["filesystem"].get("mounts", [])))
    print("statfs", list(profile["filesystem"].get("statfs", {})))
    print("stat paths", len(profile["filesystem"].get("stat", {})))
    print("dirs", list(profile["filesystem"].get("directories", {}))[:8])
    print("graphics", {k: profile["graphics"].get(k) for k in ("vendor", "renderer", "version")})
    print("gaid", profile["android"]["identifiers"].get("advertisingId"))
    print("imei", slot.get("imei"), "imsi", slot.get("subscriberId"), "iccid", slot.get("simSerialNumber"),
          "simState", slot.get("simState"))
    print("wifi", wifi.get("ssid"), wifi.get("bssid"), "gw", profile["network"]["links"].get("gatewayIpv4"),
          "dns", profile["network"]["links"].get("dnsServers"))
    print("sensors", len(profile["android"]["sensors"].get("types", [])))
    print("tcp", len(profile["network"].get("tcp", [])), "tcp6", len(profile["network"].get("tcp6", [])))
    print("routes", len(profile["network"].get("ipv4Routes", [])),
          "ifstats", len(profile["network"].get("interfaceStats", [])))
    print("accounts", len(profile["android"].get("accounts", [])),
          "imes", len(profile["android"].get("inputMethods", [])))
    print("linux.files", len(profile["linux"].get("files", {})))
    print("has random", "random" in profile, profile.get("random"))
    print("maps in files", any("maps" in k for k in profile["linux"].get("files", {})))


if __name__ == "__main__":
    import sys
    if "--merge-only" not in sys.argv:
        collect_extra()
    merge()
