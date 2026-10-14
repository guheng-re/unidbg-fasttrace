# -*- coding: utf-8 -*-
"""Build vivo_V2132A_13 unidbg profile.

Identity / DFP numbers come from the Go vivo_V2132A_13 template (readme + huiyan_device).
Missing overlay text (fonts, live wlan addresses) is filled from the USB Pixel 6 dump.
"""
from __future__ import print_function

import hashlib
import json
import os
import re

ROOT = os.path.dirname(os.path.abspath(__file__))
DUMP = os.path.join(ROOT, "device-dump")
FILES = os.path.join(ROOT, "files")


def read_text(path):
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        return fh.read()


def write_bytes(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(data)


def write_text(path, text):
    if not text.endswith("\n"):
        text += "\n"
    write_bytes(path, text.encode("utf-8"))


def parse_fonts(ls_text):
    fonts = []
    for line in ls_text.splitlines():
        # -rw-r--r-- 1 root root 4540 2009-01-01 08:00 AndroidClock.ttf
        m = re.match(r"^[-l].*?\s(\d+)\s+\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}\s+(\S+)$", line)
        if not m:
            continue
        size = int(m.group(1))
        name = m.group(2)
        if "->" in line:
            continue
        fonts.append((name, size))
    return fonts


def main():
    fonts = []
    fonts_ls = os.path.join(DUMP, "system", "fonts_ls.txt")
    if os.path.isfile(fonts_ls):
        fonts = parse_fonts(read_text(fonts_ls))

    # Overlay files: vivo identity for /proc; Pixel fonts for /system/fonts.
    cpuinfo = []
    for i in range(8):
        cpuinfo.extend([
            "processor\t: %d" % i,
            "BogoMIPS\t: 26.00",
            "Features\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp",
            "CPU implementer\t: 0x41",
            "CPU architecture: 8",
            "CPU variant\t: 0x1",
            "CPU part\t: 0xd05",
            "CPU revision\t: 0",
            "",
        ])
    cpuinfo.extend([
        "Hardware\t: SamsungTechnologies,IncExynosE1080",
        "Processor\t: AArch64 Processor rev 0 (aarch64)",
        "",
    ])
    write_text(os.path.join(FILES, "proc", "cpuinfo"), "\n".join(cpuinfo))

    meminfo = """MemTotal:        7450468 kB
MemFree:         1824124 kB
MemAvailable:    4210888 kB
Buffers:            8128 kB
Cached:          2614336 kB
SwapCached:            0 kB
Active:          1982464 kB
Inactive:        1763328 kB
Active(anon):     412672 kB
Inactive(anon):   688128 kB
Active(file):    1569792 kB
Inactive(file):  1075200 kB
Unevictable:       24576 kB
Mlocked:           24576 kB
SwapTotal:       2097152 kB
SwapFree:        2097152 kB
Dirty:               384 kB
Writeback:             0 kB
AnonPages:       1101824 kB
Mapped:           512000 kB
Shmem:             32768 kB
KReclaimable:     196608 kB
Slab:             245760 kB
SReclaimable:     196608 kB
SUnreclaim:        49152 kB
KernelStack:       18432 kB
PageTables:        24576 kB
NFS_Unstable:          0 kB
Bounce:                0 kB
WritebackTmp:          0 kB
CommitLimit:     5822384 kB
Committed_AS:    8126464 kB
VmallocTotal:   263061440 kB
VmallocUsed:       65536 kB
VmallocChunk:          0 kB
CmaTotal:         262144 kB
CmaFree:          131072 kB
"""
    write_text(os.path.join(FILES, "proc", "meminfo"), meminfo)

    version = (
        "Linux version 4.19.113-gexynos1080-vivo (android@vivo-host) "
        "(Android (6317467 based on r365631c1) clang version 9.0.8, LLD 9.0.8) "
        "#1 SMP PREEMPT Tue Apr 3 19:00:00 CST 2023"
    )
    write_text(os.path.join(FILES, "proc", "version"), version)

    mounts = """/dev/root / ext4 ro,seclabel,relatime 0 0
tmpfs /dev tmpfs rw,seclabel,nosuid,relatime,mode=755 0 0
tmpfs /mnt tmpfs rw,seclabel,nosuid,nodev,noexec,relatime,mode=755 0 0
/dev/fuse /mnt/installer/0/emulated fuse rw,nosuid,nodev,noexec,noatime 0 0
/dev/block/platform/13100000.ufs/by-name/efs /mnt/vendor/efs ext4 rw,seclabel,nosuid,nodev,noatime 0 0
/dev/block/platform/13100000.ufs/by-name/persist /mnt/vendor/persist ext4 rw,seclabel,nosuid,nodev,noatime 0 0
/dev/block/platform/13100000.ufs/by-name/metadata /metadata ext4 rw,seclabel,nosuid,nodev,noatime 0 0
tmpfs /apex tmpfs rw,seclabel,nosuid,nodev,noexec,relatime,mode=755 0 0
tmpfs /linkerconfig tmpfs rw,seclabel,nosuid,nodev,noexec,relatime,mode=755 0 0
/dev/block/platform/13100000.ufs/by-name/cache /cache ext4 rw,seclabel,nosuid,nodev,noatime 0 0
/dev/fuse /storage/emulated fuse rw,nosuid,nodev,noexec,noatime 0 0
tmpfs /data/data tmpfs rw,seclabel,nosuid,nodev,noatime,mode=771 0 0
tmpfs /data/user tmpfs rw,seclabel,nosuid,nodev,noatime,mode=771 0 0
tmpfs /data/user_de tmpfs rw,seclabel,nosuid,nodev,noatime,mode=771 0 0
tmpfs /data/misc/profiles/cur tmpfs rw,seclabel,nosuid,nodev,noatime 0 0
tmpfs /data/misc/profiles/ref tmpfs rw,seclabel,nosuid,nodev,noatime 0 0
/dev/block/dm-24 /data f2fs rw,nosuid,nodev,noatime,seclabel 0 0
/dev/block/dm-0 /system ext4 ro,seclabel,relatime 0 0
"""
    write_text(os.path.join(FILES, "proc", "mounts"), mounts)
    write_text(os.path.join(FILES, "proc", "self", "cmdline"), "com.MobileTicket")
    write_text(os.path.join(FILES, "proc", "self", "cgroup"), "0::/")
    write_text(
        os.path.join(FILES, "proc", "self", "status"),
        "Name:\tmain\nState:\tS (sleeping)\nTgid:\t10231\nPid:\t10231\nPPid:\t1\n"
        "TracerPid:\t0\nUid:\t10231\t10231\t10231\t10231\nGid:\t10231\t10231\t10231\t10231\n",
    )
    write_text(os.path.join(FILES, "proc", "sys", "kernel", "random", "boot_id"),
               "7a239e5d-d157-48a2-ac54-aba483265c47")
    write_text(os.path.join(FILES, "sys", "devices", "system", "cpu", "online"), "0-7")
    write_text(os.path.join(FILES, "sys", "devices", "system", "cpu", "present"), "0-7")
    write_text(os.path.join(FILES, "sys", "devices", "system", "cpu", "possible"), "0-7")

    # Pixel live Wi-Fi / dummy0 (vivo template has no full #129).
    write_text(os.path.join(FILES, "sys", "class", "net", "wlan0", "address"), "74:74:46:b6:4e:fe")
    write_text(os.path.join(FILES, "sys", "class", "net", "wlan0", "mtu"), "1500")
    write_text(os.path.join(FILES, "sys", "class", "net", "wlan0", "operstate"), "up")
    write_text(os.path.join(FILES, "sys", "class", "net", "wlan0", "carrier"), "1")
    write_text(os.path.join(FILES, "sys", "class", "net", "dummy0", "address"), "fa:cf:78:cd:7c:4b")
    write_text(os.path.join(FILES, "sys", "class", "net", "dummy0", "mtu"), "1500")
    write_text(os.path.join(FILES, "sys", "class", "net", "dummy0", "operstate"), "unknown")
    write_text(os.path.join(FILES, "sys", "class", "net", "dummy0", "carrier"), "1")
    write_text(os.path.join(FILES, "sys", "class", "net", "lo", "address"), "00:00:00:00:00:00")
    write_text(os.path.join(FILES, "sys", "class", "net", "lo", "mtu"), "65536")
    write_text(os.path.join(FILES, "sys", "class", "net", "lo", "operstate"), "unknown")

    # Minimal /system tree so #16/#18 have something to walk. Font bytes from Pixel sizes.
    write_text(os.path.join(FILES, "system", "build.prop"),
               "ro.product.brand=vivo\nro.product.model=V2132A\nro.build.version.release=13\n")
    write_bytes(os.path.join(FILES, "system", "bin", "sh"), b"#!/system/bin/sh\n")
    write_bytes(os.path.join(FILES, "system", "lib64", "libc.so"), b"\x7fELF" + b"\x00" * 60)
    for name, size in fonts:
        path = os.path.join(FILES, "system", "fonts", name)
        payload = bytearray(max(size, 1))
        payload[0] = 0x00
        write_bytes(path, bytes(payload))

    df = (
        "Filesystem      1K-blocks    Used Available Use% Mounted on\n"
        "/dev/block/dm-0   3145728 2100000   1045728  67% /\n"
        "/dev/block/dm-24 228081440 120000000 108081440  53% /data\n"
        "tmpfs               3725234   1234   3724000   1% /dev\n"
        "tmpfs               3725234      0   3725234   0% /mnt\n"
        "/dev/fuse         228081440 120000000 108081440  53% /storage/emulated\n"
    )

    def sig(name):
        return [hashlib.md5(name.encode("utf-8")).hexdigest().upper()]

    packages = [
        {"packageName": "com.MobileTicket", "versionName": "5.9.6.6", "versionCode": 5966,
         "sourceDir": "/data/app/~~ticket/com.MobileTicket-fixed/base.apk",
         "dataDir": "/data/user/0/com.MobileTicket", "uid": 10231, "enabled": True, "systemApp": False,
         "firstInstallTimeMillis": 1712718723845, "lastUpdateTimeMillis": 1712718723845,
         "signaturesHex": ["EF1800AFE86BEFFE7EC9609147F9358A"]},
        {"packageName": "android", "versionName": "13", "versionCode": 33,
         "sourceDir": "/system/framework/framework-res.apk", "dataDir": "/data/system",
         "uid": 1000, "enabled": True, "systemApp": True, "signaturesHex": ["ANDROIDFRAMEWORK01"]},
        {"packageName": "com.android.systemui", "versionName": "13", "versionCode": 33,
         "sourceDir": "/system/priv-app/SystemUI/SystemUI.apk", "dataDir": "/data/user_de/0/com.android.systemui",
         "uid": 1013, "enabled": True, "systemApp": True, "signaturesHex": ["ANDROIDSYSTEMUI01"]},
        {"packageName": "com.android.settings", "versionName": "13", "versionCode": 33,
         "sourceDir": "/system/priv-app/Settings/Settings.apk", "dataDir": "/data/user/0/com.android.settings",
         "uid": 1000, "enabled": True, "systemApp": True, "signaturesHex": ["ANDROIDSETTINGS01"]},
        {"packageName": "com.android.phone", "versionName": "13", "versionCode": 33,
         "sourceDir": "/system/priv-app/TeleService/TeleService.apk", "dataDir": "/data/user_de/0/com.android.phone",
         "uid": 1001, "enabled": True, "systemApp": True, "signaturesHex": ["ANDROIDPHONE01"]},
        {"packageName": "com.android.vending", "versionName": "38.0.0", "versionCode": 83800000,
         "sourceDir": "/product/priv-app/Phonesky/Phonesky.apk", "dataDir": "/data/user/0/com.android.vending",
         "uid": 10140, "enabled": True, "systemApp": True, "signaturesHex": ["VENDING01"]},
        {"packageName": "com.bbk.launcher2", "versionName": "8.0.0.0", "versionCode": 8000000,
         "sourceDir": "/system/priv-app/BBKLauncher/BBKLauncher.apk", "dataDir": "/data/user/0/com.bbk.launcher2",
         "uid": 10020, "enabled": True, "systemApp": True, "signaturesHex": ["VIVOLAUNCHER01"]},
        {"packageName": "com.vivo.abe", "versionName": "5.0.0", "versionCode": 50000,
         "sourceDir": "/system/app/VivoAbe/VivoAbe.apk", "dataDir": "/data/user/0/com.vivo.abe",
         "uid": 10021, "enabled": True, "systemApp": True, "signaturesHex": ["VIVOABE01"]},
        {"packageName": "com.tencent.mm", "versionName": "8.0.44", "versionCode": 2420,
         "sourceDir": "/data/app/~~mm/com.tencent.mm-fixed/base.apk", "dataDir": "/data/user/0/com.tencent.mm",
         "uid": 10240, "enabled": True, "systemApp": False, "signaturesHex": ["TENCENTMM01"]},
        {"packageName": "com.eg.android.AlipayGphone", "versionName": "10.5.0", "versionCode": 1050,
         "sourceDir": "/data/app/~~alipay/com.eg.android.AlipayGphone-fixed/base.apk",
         "dataDir": "/data/user/0/com.eg.android.AlipayGphone", "uid": 10241, "enabled": True,
         "systemApp": False, "signaturesHex": ["ALIPAY01"]},
    ]

    profile = {
        "schemaVersion": "traceai-device-fingerprint/v1",
        "profileName": "vivo-V2132A-13",
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
        "time": {
            "currentTimeMillis": 1712719406778,
            "monotonicNanos": 12897217227000,
            "timezoneMinutesWest": -480,
        },
        "random": {
            "devRandomHex": "a1b2c3d4",
            "devUrandomHex": "b2c3d4e5",
            "devSrandomHex": "c3d4e5f6",
            "getrandomHex": "7a239e5dd15748a2ac54aba483265c47",
            "uuid": "84264b6b-204b-49fc-ac54-aba483265c47",
            "stackGuardHex": "0102030405060708",
            "atRandomHex": "7a239e5dd15748a2ac54aba483265c47",
            "mediaDrmDeviceUniqueIdHex": "0101a642f77c50596027f3ca95cbc16b25aa91853496606f5566e4b650d3a4d4",
        },
        "linux": {
            "uname": {
                "sysname": "Linux",
                "nodename": "localhost",
                "release": "4.19.113-gexynos1080-vivo",
                "version": "#1 SMP PREEMPT Tue Apr 3 19:00:00 CST 2023",
                "machine32": "armv8l",
                "machine64": "aarch64",
                "domainname": "(none)",
            },
            "cpu": {
                "affinityMaskHex": "ff",
                "online": "0-7",
                "present": "0-7",
                "possible": "0-7",
                "configuredProcessorCount": 8,
                "onlineProcessorCount": 8,
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
                "uptime": 12897,
                "loads": [65536, 58982, 52428],
                "totalRam": 1862617,
                "freeRam": 456031,
                "sharedRam": 8192,
                "bufferRam": 2032,
                "totalSwap": 524288,
                "freeSwap": 524288,
                "procs": 486,
                "memUnit": 4096,
            },
            "proc": {
                "cmdline": ["com.MobileTicket"],
                "cgroups": ["0::/"],
                "comm": "main",
                "tracerPid": 0,
                "bootId": "7a239e5d-d157-48a2-ac54-aba483265c47",
                "randomUuid": "84264b6b-204b-49fc-ac54-aba483265c47",
                "selinuxContext": "u:r:untrusted_app:s0:c512,c768",
                "fileSelinuxContexts": {
                    "/data/user/0/com.MobileTicket": "u:object_r:app_data_file:s0"
                },
            },
            "commands": {
                "/system/bin/df": df,
                "df": df,
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
                "BRAND": "vivo",
                "MANUFACTURER": "vivo",
                "MODEL": "V2132A",
                "DEVICE": "PD2134",
                "PRODUCT": "PD2132",
                "BOARD": "s5e9815",
                "HARDWARE": "s5e9815",
                "BOOTLOADER": "unknown",
                "DISPLAY": "PD2134_A_7.9.0",
                "ID": "TP1A.220624.014",
                "HOST": "compiler",
                "USER": "compiler",
                "TAGS": "release-keys",
                "TYPE": "user",
                "TIME": 1680523200000,
                "FINGERPRINT": "vivo/PD2132/PD2134:13/TP1A.220624.014/compiler04031900:user/release-keys",
                "CPU_ABI": "arm64-v8a",
                "CPU_ABI2": "armeabi-v7a",
                "SERIAL": "PD2132A7A239E5D",
                "SUPPORTED_ABIS": ["arm64-v8a", "armeabi-v7a"],
                "SUPPORTED_32_BIT_ABIS": ["armeabi-v7a"],
                "SUPPORTED_64_BIT_ABIS": ["arm64-v8a"],
                "VERSION.RELEASE": "13",
                "VERSION.SDK_INT": 33,
                "VERSION.INCREMENTAL": "compiler04031900",
                "VERSION.CODENAME": "REL",
                "VERSION.SECURITY_PATCH": "2023-04-01",
            },
            "properties": {
                "ro.product.brand": "vivo",
                "ro.product.manufacturer": "vivo",
                "ro.product.model": "V2132A",
                "ro.product.device": "PD2134",
                "ro.product.name": "PD2132",
                "ro.product.board": "s5e9815",
                "ro.product.cpu.abi": "arm64-v8a",
                "ro.hardware": "s5e9815",
                "ro.board.platform": "s5e9815",
                "ro.serialno": "PD2132A7A239E5D",
                "ro.boot.serialno": "PD2132A7A239E5D",
                "ro.bootloader": "unknown",
                "ro.build.id": "TP1A.220624.014",
                "ro.build.display.id": "PD2134_A_7.9.0",
                "ro.build.version.incremental": "compiler04031900",
                "ro.build.version.sdk": "33",
                "ro.build.version.release": "13",
                "ro.build.version.codename": "REL",
                "ro.build.version.security_patch": "2023-04-01",
                "ro.build.type": "user",
                "ro.build.tags": "release-keys",
                "ro.build.user": "compiler",
                "ro.build.host": "compiler",
                "ro.build.fingerprint": "vivo/PD2132/PD2134:13/TP1A.220624.014/compiler04031900:user/release-keys",
                "ro.debuggable": "0",
                "ro.secure": "1",
                "ro.boot.flash.locked": "1",
                "ro.boot.verifiedbootstate": "green",
                "ro.boot.veritymode": "enforcing",
                "persist.sys.timezone": "Asia/Shanghai",
                "persist.sys.locale": "zh-Hans-CN",
                "wifi.interface": "wlan0",
                "gsm.operator.alpha": "中国移动中国移动",
                "gsm.operator.numeric": "46000",
                "gsm.sim.operator.alpha": "中国移动",
                "gsm.sim.operator.numeric": "46000",
                "gsm.sim.state": "READY,READY",
                "ro.vivo.os.build.display.id": "Funtouch OS_14",
                "ro.vivo.os.name": "Funtouch",
                "ro.vivo.product.version": "PD2134_A_7.9.0",
            },
            "settings": {
                "secure": {
                    "android_id": "017ff659ab66d2bd",
                    "accessibility_enabled": "0",
                    "enabled_accessibility_services": "",
                    "adb_enabled": "0",
                },
                "global": {
                    "adb_enabled": "0",
                    "development_settings_enabled": "0",
                    "airplane_mode_on": "0",
                },
            },
            "identifiers": {
                "androidId": "017ff659ab66d2bd",
                "advertisingId": "00000000-0000-4000-8000-017ff659ab66",
                "limitAdTracking": False,
                "appSetId": "vivo-app-set-id-v2132a-0001",
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
                "availableProcessors": 8,
                "maxMemoryBytes": 268435456,
                "totalMemoryBytes": 134217728,
                "freeMemoryBytes": 67108864,
            },
            "packages": packages,
            "features": [
                {"name": "android.hardware.camera"},
                {"name": "android.hardware.wifi"},
                {"name": "android.hardware.touchscreen"},
                {"name": "android.hardware.telephony"},
                {"name": "android.hardware.fingerprint"},
            ],
            "locale": {"languageTag": "zh-Hans-CN", "timezoneId": "Asia/Shanghai"},
            "display": {
                "widthPixels": 1080,
                "heightPixels": 2376,
                "densityDpi": 480,
                "scaledDensity": 3.0,
                "xdpi": 409.0,
                "ydpi": 409.0,
                "refreshRate": 60.0,
                "rotation": 0,
                "modeId": 1,
            },
            "configuration": {
                "orientation": 1,
                "screenLayout": 34,
                "uiMode": 17,
                "fontScale": 1.0,
                "densityDpi": 480,
                "screenWidthDp": 360,
                "screenHeightDp": 792,
                "smallestScreenWidthDp": 360,
                "keyboard": 1,
                "navigation": 1,
                "keyboardHidden": 1,
                "hardKeyboardHidden": 1,
                "navigationHidden": 1,
            },
            "battery": {
                "capacityPercent": 89,
                "charging": False,
                "chargeCounterUah": 3916000,
                "currentNowUa": -280000,
                "currentAverageUa": -220000,
                "energyCounterNwh": 17248000000,
                "status": 3,
                "chargeTimeRemainingMillis": -1,
            },
            "power": {
                "interactive": True,
                "powerSaveMode": False,
                "deviceIdleMode": False,
                "deviceLightIdleMode": False,
            },
            "thermal": {"currentThermalStatus": 0, "headroom": 0.8},
            "sensors": {
                "types": [1, 2, 4, 5],
                "names": {
                    "1": "LSM6DSO Accel",
                    "2": "AK09918 Mag",
                    "4": "LSM6DSO Gyro",
                    "5": "TCS3701 light",
                },
                "vendors": {"1": "ST", "2": "AK", "4": "ST", "5": "AMS"},
                "versions": {"1": 1, "2": 1, "4": 1, "5": 1},
                "stringTypes": {
                    "1": "android.sensor.accelerometer",
                    "2": "android.sensor.magnetic_field",
                    "4": "android.sensor.gyroscope",
                    "5": "android.sensor.light",
                },
                "maximumRanges": {"1": 78.4532, "2": 4900.0, "4": 34.906586, "5": 65535.0},
                "resolutions": {
                    "1": 0.0023950196,
                    "2": 0.6,
                    "4": 0.0005326322,
                    "5": 10.0,
                },
                "powers": {"1": 0.15, "2": 0.35, "4": 0.25, "5": 0.09},
                "minDelaysMicros": {"1": 2500, "2": 10000, "4": 2500, "5": 0},
                "wakeUpSensors": {"1": False, "2": False, "4": False, "5": False},
                "sensorIds": {"1": -1, "2": -1, "4": -1, "5": -1},
                "reportingModes": {"1": 0, "2": 0, "4": 0, "5": 1},
                "samples": {
                    "1": [0.12, -0.08, 9.79],
                    "2": [18.6, -8.4, 41.2],
                    "4": [0.001, -0.002, 0.0],
                    "5": [35.0],
                },
            },
            "telephony": {
                "phoneCount": 2,
                "networkOperator": "46000",
                "networkOperatorName": "中国移动",
                "simOperator": "46000",
                "simOperatorName": "中国移动",
                "networkCountryIso": "cn",
                "simCountryIso": "cn",
                "dataNetworkType": 13,
                "dataState": 2,
                "phoneType": 1,
                "networkRoaming": False,
                "slots": [
                    {
                        "slotIndex": 0,
                        "imei": "860123047890123",
                        "deviceId": "860123047890123",
                        "subscriberId": "460001234567890",
                        "simSerialNumber": "89860012345678901234",
                        "simState": 5,
                    },
                    {
                        "slotIndex": 1,
                        "imei": "860123047890131",
                        "deviceId": "860123047890131",
                        "subscriberId": "460009876543210",
                        "simSerialNumber": "89860098765432109876",
                        "simState": 5,
                    },
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
                "lastKnownLocations": [
                    {
                        "provider": "gps",
                        "latitude": 22.5431,
                        "longitude": 114.0579,
                        "altitude": 18.0,
                        "accuracyMeters": 12.0,
                        "timeMillis": 1712719406778,
                        "elapsedRealtimeNanos": 12897217227000,
                        "mock": False,
                    }
                ],
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
                "strongBoxAvailable": False,
                "marker": "VIVO_SOTER_TEE_V2132A",
            },
            "drm": {
                "available": True,
                "marker": "VIVO_WIDEVINE_V2132A",
                "deviceUniqueIdHex": "0101a642f77c50596027f3ca95cbc16b25aa91853496606f5566e4b650d3a4d4",
            },
        },
        "network": {
            "interfaces": [
                {
                    "name": "lo",
                    "index": 1,
                    "ipv4": "127.0.0.1",
                    "flags": 73,
                    "mac": "00:00:00:00:00:00",
                    "hardwareType": 772,
                    "mtu": 65536,
                    "operState": "unknown",
                    "carrier": False,
                },
                {
                    "name": "dummy0",
                    "index": 2,
                    "ipv4": "169.254.1.1",
                    "flags": 195,
                    "mac": "fa:cf:78:cd:7c:4b",
                    "hardwareType": 1,
                    "mtu": 1500,
                    "operState": "unknown",
                    "carrier": True,
                    "virtual": True,
                },
                {
                    "name": "wlan0",
                    "index": 3,
                    "ipv4": "10.41.20.63",
                    "broadcast": "10.41.23.255",
                    "flags": 4163,
                    "mac": "74:74:46:b6:4e:fe",
                    "hardwareType": 1,
                    "mtu": 1500,
                    "displayName": "wlan0",
                    "operState": "up",
                    "carrier": True,
                    "speedMbps": 433,
                    "duplex": "full",
                },
            ],
            "ipv6Addresses": [
                {
                    "interfaceName": "lo",
                    "addressHex": "00000000000000000000000000000001",
                    "prefixLength": 128,
                    "scope": 16,
                    "flags": 128,
                },
                {
                    "interfaceName": "dummy0",
                    "addressHex": "fe80000000000000f8cf78fffecd7c4b",
                    "prefixLength": 64,
                    "scope": 32,
                    "flags": 128,
                },
                {
                    "interfaceName": "wlan0",
                    "addressHex": "fe8000000000000060da58ac405c38d6",
                    "prefixLength": 64,
                    "scope": 32,
                    "flags": 128,
                },
            ],
            "wifi": {
                "enabled": True,
                "state": 3,
                "ssid": "OfficeWiFi",
                "bssid": "74:74:46:b6:4e:fe",
                "macAddress": "74:74:46:b6:4e:fe",
                "ipv4": "10.41.20.63",
                "rssi": -52,
                "linkSpeedMbps": 433,
                "frequencyMhz": 5180,
                "networkId": 1,
            },
            "links": {
                "connected": True,
                "type": 1,
                "typeName": "WIFI",
                "interfaceName": "wlan0",
                "dnsServers": ["223.5.5.5", "223.6.6.6"],
                "gatewayIpv4": "10.41.16.1",
                "netmaskIpv4": "255.255.248.0",
                "mtu": 1500,
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
            "stat": {
                "/data/app/~~ticket/com.MobileTicket-fixed/base.apk": {
                    "mode": 33188,
                    "uid": 1000,
                    "gid": 1000,
                    "size": 178814915,
                    "blockSize": 4096,
                    "mtimeMillis": 1712718716000,
                },
                "/data/user/0/com.MobileTicket": {
                    "mode": 16877,
                    "uid": 10231,
                    "gid": 10231,
                    "size": 4096,
                    "blockSize": 4096,
                    "mtimeMillis": 1712718723845,
                },
                "/storage/emulated/0": {
                    "mode": 16877,
                    "uid": 1023,
                    "gid": 1023,
                    "size": 4096,
                    "blockSize": 4096,
                    "mtimeMillis": 1712718000000,
                },
            },
            "mounts": [
                {"source": "/dev/block/dm-0", "target": "/system", "fileSystemType": "ext4",
                 "options": "ro,seclabel,relatime"},
                {"source": "/dev/block/dm-24", "target": "/data", "fileSystemType": "f2fs",
                 "options": "rw,nosuid,nodev,noatime,seclabel"},
                {"source": "/dev/fuse", "target": "/storage/emulated/0", "fileSystemType": "fuse",
                 "options": "rw,nosuid,nodev,noexec,noatime"},
            ],
            "statfs": {
                "/data": {
                    "blockSize": 4096,
                    "blocks": 57020360,
                    "blocksFree": 27020360,
                    "blocksAvailable": 27020360,
                    "files": 2000000,
                    "filesFree": 1800000,
                },
                "/storage/emulated/0": {
                    "blockSize": 4096,
                    "blocks": 57020360,
                    "blocksFree": 27020360,
                    "blocksAvailable": 27020360,
                    "files": 2000000,
                    "filesFree": 1800000,
                },
                "/system": {
                    "blockSize": 4096,
                    "blocks": 786432,
                    "blocksFree": 261432,
                    "blocksAvailable": 261432,
                    "files": 80000,
                    "filesFree": 60000,
                },
            },
        },
        "graphics": {
            "vendor": "ARM",
            "renderer": "Mali-G78",
            "version": "OpenGL ES 3.2",
            "shadingLanguageVersion": "OpenGL ES GLSL ES 3.20",
            "extensions": ["GL_EXT_texture_format_BGRA8888"],
            "eglVendor": "Android",
            "eglVersion": "1.5",
            "eglExtensions": ["EGL_KHR_image_base"],
        },
    }

    out = os.path.join(ROOT, "device-fingerprint.json")
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(profile, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("wrote", out)
    print("fonts", len(fonts))


if __name__ == "__main__":
    main()
