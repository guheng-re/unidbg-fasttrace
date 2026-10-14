# -*- coding: utf-8 -*-
"""Rebuild unidbg profile from Go HUIYAN_FACE_MODELS vivo_V2132A_13 dump."""
from __future__ import print_function

import json
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
GO_DUMP = os.path.abspath(
    os.path.join(HERE, "..", "..", "..", "libhuiyan-http-android", "phonesit", "out", "go_model")
)
SRC_PROFILE = os.path.join(os.path.dirname(HERE), "vivo-V2132A-13", "device-fingerprint.json")
PIXEL_CACHE = os.path.join(
    os.path.dirname(HERE), "pixel6-2", "files", "data", "data", "com.MobileTicket", "cache"
)
OUT_JSON = os.path.join(HERE, "device-fingerprint.json")
FILES = os.path.join(HERE, "files")

ANDROID_ID = "d750f3f8ca9147f9"
OAID = "67a061f9360aaaf2af9d2ed720e5be3e6c2b23199677f9b37e6c6223431aef8a"
APP_UID = 10039
VERSION_CODE = 278
VERSION_NAME = "5.9.6.6"
SIG = "C682468B0FE9EC13DEE781F2EF5CCE21"
INSTALL_MS = 1782799777030
FINGERPRINT = "vivo/PD2132/PD2134:13/TP1A.220624.014/compiler10211842:user/release-keys"
HARDWARE = "erd9815_rt"
CPU_HARDWARE = "SamsungTechnologies,IncExynosE1080"
WLAN_IPV4 = "10.41.20.117"
WLAN_IPV6 = "fe80::303e:8bff:fe86:61e0"
WLAN_MAC = "32:3e:8b:86:61:e0"
DUMMY_IPV6 = "fe80::f877:ecff:fe69:4151"
DUMMY_MAC = "fa:77:ec:69:41:51"
DNS = ["10.41.30.125", "10.41.30.126", "114.114.114.114"]
NET_129 = (
    "10,wlan0;4163;2;fe80::303e:8bff:fe86:61e0;10.41.20.117,"
    "rmnet6;4305;2;fe80::200:ff:fe00:0;2409:8150:74e:30f:18bd:2687:6524:26a4,"
    "rmnet5;4305;3;fe80::200:ff:fe00:0;2409:8950:12f:44b:18bd:c335:d05:ccac;10.214.15.67,"
    "rmnet1;4305;2;fe80::200:ff:fe00:0;2409:8150:6c2:2ec:18bd:8970:2a81:7532,"
    "dummy0;195;1;fe80::f877:ecff:fe69:4151"
)


def ipv6_to_hex(addr):
    parts = addr.split("::")
    head = parts[0].split(":") if parts[0] else []
    tail = parts[1].split(":") if len(parts) > 1 and parts[1] else []
    zeros = 8 - len(head) - len(tail)
    groups = head + ["0"] * zeros + tail
    return "".join(g.zfill(4) for g in groups)


def write_text(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if not text.endswith("\n"):
        text += "\n"
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


def main():
    with open(SRC_PROFILE, "r", encoding="utf-8") as fh:
        profile = json.load(fh)

    tdid = json.load(open(os.path.join(GO_DUMP, "tdid.json"), encoding="utf-8"))
    turing = json.load(open(os.path.join(GO_DUMP, "turing.json"), encoding="utf-8"))
    dev = tdid["^2"]["!3"][0]["$3"]["1"]
    meta = tdid["^2"]["!3"][0]["$3"]["6"]["9"].split(":")
    apk_size = int(meta[0])
    cam = turing["!4"]["!8"]["8"]["102"].replace("%54", ",")

    profile["profileName"] = "go-vivo_V2132A_13"
    proc = profile["process"]
    for k in ("pid", "pgid", "sid", "tid", "uid", "gid", "euid", "egid"):
        proc[k] = APP_UID
    proc["supplementaryGids"] = [APP_UID]
    proc["processName"] = "com.MobileTicket"

    ident = profile["android"]["identifiers"]
    ident["androidId"] = ANDROID_ID
    ident["advertisingId"] = "00000000-0000-4000-8000-" + ANDROID_ID[:12]
    ident["appSetId"] = OAID[:32]

    settings = profile["android"]["settings"]
    settings["secure"]["android_id"] = ANDROID_ID
    settings["secure"]["adb_enabled"] = "0"
    settings["global"]["adb_enabled"] = "0"
    settings["global"]["development_settings_enabled"] = "0"

    build = profile["android"]["build"]
    build["BRAND"] = "vivo"
    build["MANUFACTURER"] = "vivo"
    build["MODEL"] = "V2132A"
    build["DEVICE"] = "PD2134"
    build["PRODUCT"] = "PD2132"
    build["BOARD"] = "s5e9815"
    build["HARDWARE"] = HARDWARE
    build["DISPLAY"] = "PD2134_A_7.9.0"
    build["ID"] = "TP1A.220624.014"
    build["FINGERPRINT"] = FINGERPRINT
    build["VERSION.INCREMENTAL"] = "compiler10211842"
    build["VERSION.RELEASE"] = "13"
    build["VERSION.SDK_INT"] = 33
    build["SERIAL"] = "PD2132" + ANDROID_ID[-8:].upper()

    props = profile["android"]["properties"]
    props.update({
        "ro.product.brand": "vivo",
        "ro.product.manufacturer": "vivo",
        "ro.product.model": "V2132A",
        "ro.product.device": "PD2134",
        "ro.product.name": "PD2132",
        "ro.hardware": HARDWARE,
        "ro.board.platform": "s5e9815",
        "ro.serialno": build["SERIAL"],
        "ro.boot.serialno": build["SERIAL"],
        "ro.build.display.id": "PD2134_A_7.9.0",
        "ro.build.version.incremental": "compiler10211842",
        "ro.build.fingerprint": FINGERPRINT,
        "ro.vivo.product.version": "PD2134_A_7.9.0",
        "gsm.operator.alpha": "中国移动中国移动",
        "gsm.operator.numeric": "46000",
        "gsm.sim.operator.alpha": "中国移动",
        "gsm.sim.operator.numeric": "46000",
        "gsm.sim.state": "READY,READY",
    })

    android = profile["android"]
    android["packageName"] = "com.MobileTicket"
    android["versionName"] = VERSION_NAME
    android["versionCode"] = VERSION_CODE
    android["dataDir"] = "/data/user/0/com.MobileTicket"

    for pkg in android["packages"]:
        if pkg.get("packageName") == "com.MobileTicket":
            pkg["versionName"] = VERSION_NAME
            pkg["versionCode"] = VERSION_CODE
            pkg["uid"] = APP_UID
            pkg["firstInstallTimeMillis"] = INSTALL_MS
            pkg["lastUpdateTimeMillis"] = INSTALL_MS
            pkg["signaturesHex"] = [SIG]

    android["battery"]["capacityPercent"] = 77
    android["battery"]["charging"] = False
    android["display"]["widthPixels"] = 1080
    android["display"]["heightPixels"] = 2376
    android["locale"]["languageTag"] = "zh-Hans-CN"
    android["locale"]["timezoneId"] = "Asia/Shanghai"

    linux = profile["linux"]
    linux["uname"]["release"] = "4.19.113-gexynos1080-vivo"
    linux["proc"]["cmdline"] = ["com.MobileTicket"]
    status_uid = "%d\t%d\t%d\t%d" % (APP_UID, APP_UID, APP_UID, APP_UID)
    linux["proc"]["selinuxContext"] = "u:r:untrusted_app:s0:c512,c768"

    net = profile["network"]
    for iface in net["interfaces"]:
        if iface["name"] == "wlan0":
            iface["ipv4"] = WLAN_IPV4
            iface["mac"] = WLAN_MAC
            iface["flags"] = 4163
            iface["broadcast"] = "10.41.23.255"
        elif iface["name"] == "dummy0":
            iface["mac"] = DUMMY_MAC
            iface["flags"] = 195
            iface["ipv4"] = "169.254.1.1"
    for item in net["ipv6Addresses"]:
        if item["interfaceName"] == "wlan0":
            item["addressHex"] = ipv6_to_hex(WLAN_IPV6)
        elif item["interfaceName"] == "dummy0":
            item["addressHex"] = ipv6_to_hex(DUMMY_IPV6)
    net["wifi"]["ipv4"] = WLAN_IPV4
    net["wifi"]["macAddress"] = WLAN_MAC
    net["wifi"]["bssid"] = WLAN_MAC
    net["links"]["dnsServers"] = DNS
    net["links"]["gatewayIpv4"] = "10.41.20.1"

    if "stat" in profile.get("filesystem", {}):
        apk = "/data/app/~~ticket/com.MobileTicket-fixed/base.apk"
        if apk in profile["filesystem"]["stat"]:
            profile["filesystem"]["stat"][apk]["size"] = apk_size
            profile["filesystem"]["stat"][apk]["mtimeMillis"] = INSTALL_MS

    sidecar = os.path.join(HERE, "go_identity.json")
    ident_out = {
        "androidId": ANDROID_ID,
        "oaid": OAID,
        "appUid": APP_UID,
        "versionCode": VERSION_CODE,
        "versionName": VERSION_NAME,
        "signature": SIG,
        "installTimeMs": INSTALL_MS,
        "fingerprint": FINGERPRINT,
        "hardware": HARDWARE,
        "cpuHardware": CPU_HARDWARE,
        "model": "V2132A",
        "net129": NET_129,
        "camera102": cam,
        "battery": 77,
        "uptimeMs": 1782800466309 - 1782588334698,
        "bootTimeMs": 1782588334698,
        "field8": dev.get("8"),
        "field16": dev.get("16"),
        "field51": dev.get("51"),
        "field129": dev.get("129"),
    }
    with open(sidecar, "w", encoding="utf-8") as fh:
        json.dump(ident_out, fh, ensure_ascii=False, indent=2)
        fh.write("\n")

    with open(OUT_JSON, "w", encoding="utf-8") as fh:
        json.dump(profile, fh, ensure_ascii=False, indent=2)
        fh.write("\n")

    write_text(os.path.join(FILES, "proc", "cpuinfo"),
               "\n".join(["processor\t: %d" % i for i in range(8)] + [
                   "Hardware\t: " + CPU_HARDWARE,
                   "Processor\t: AArch64 Processor rev 0 (aarch64)",
                   "",
               ]))
    write_text(os.path.join(FILES, "proc", "self", "status"),
               "Name:\tmain\nState:\tS (sleeping)\nTgid:\t%d\nPid:\t%d\nPPid:\t1\n"
               "TracerPid:\t0\nUid:\t%s\nGid:\t%s\n" % (APP_UID, APP_UID, status_uid, status_uid))
    write_text(os.path.join(FILES, "sys", "class", "net", "wlan0", "address"), WLAN_MAC)
    write_text(os.path.join(FILES, "sys", "class", "net", "wlan0", "operstate"), "up")
    write_text(os.path.join(FILES, "sys", "class", "net", "dummy0", "address"), DUMMY_MAC)
    write_text(os.path.join(FILES, "system", "build.prop"),
               "ro.product.brand=vivo\nro.product.model=V2132A\n"
               "ro.hardware=%s\nro.build.fingerprint=%s\n" % (HARDWARE, FINGERPRINT))

    cache_dst = os.path.join(FILES, "data", "data", "com.MobileTicket", "cache")
    if os.path.isdir(PIXEL_CACHE):
        os.makedirs(cache_dst, exist_ok=True)
        for name in os.listdir(PIXEL_CACHE):
            src = os.path.join(PIXEL_CACHE, name)
            dst = os.path.join(cache_dst, name)
            if os.path.isfile(src):
                shutil.copy2(src, dst)
        print("copied YT cache", cache_dst)

    print("wrote", OUT_JSON)
    print("sidecar", sidecar)
    print("androidId", ANDROID_ID, "uid", APP_UID, "hw", HARDWARE)


if __name__ == "__main__":
    main()
