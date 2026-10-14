# -*- coding: utf-8 -*-
"""Fill Turing DFP slots that the Pixel 6 APK collected but unidbg still missed.

Writes into SO-facing interfaces only (profile JSON / overlay files / linux.commands).
Does not overlay JCE after pack.
Source: phonesit/out/turingdump overlay.json + compare.txt.
"""
from __future__ import print_function

import json
import os

ROOT = os.path.dirname(os.path.abspath(__file__))
PROFILE = os.path.join(ROOT, "device-fingerprint.json")
FILES = os.path.join(ROOT, "files")
DF_FILE = os.path.join(ROOT, "device-dump", "system", "df.txt")
OVERLAY = os.path.join(
    r"D:\project\libhuiyan-http-android\phonesit\out\turingdump", "overlay.json"
)

BOOT_ID = "7350c19f-203c-4be8-b3cf-c4059e0c008b"
RINGTONE = "Your_new_adventure.ogg"
BOARD = "gs101"


def write_text(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if text and not text.endswith("\n"):
        text += "\n"
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


def compact_df_line(line):
    """Keep each df row under 60 chars so SO's 64-byte fgets sees a full line."""
    parts = line.split()
    if len(parts) < 6:
        return line.strip()
    # Filesystem 1K-blocks Used Available Use% Mounted on
    fs, blocks = parts[0], parts[1]
    mounted = parts[-1]
    return "%s %s %s" % (fs, blocks, mounted)


def load_df():
    raw = ""
    if os.path.isfile(DF_FILE):
        with open(DF_FILE, "r", encoding="utf-8", newline="\n") as fh:
            raw = fh.read()
    if not raw:
        raw = (
            "Filesystem 1K-blocks Used Available Use% Mounted on\n"
            "/dev/block/dm-10 846116 843524 0 100% /\n"
            "tmpfs 3878956 1548 3877408 1% /dev\n"
            "tmpfs 3878956 0 3878956 0% /mnt\n"
            "/dev/block/dm-11 356296 355228 0 100% /system_ext\n"
            "/dev/block/dm-12 2600816 2593008 0 100% /product\n"
            "/dev/block/dm-13 511100 509524 0 100% /vendor\n"
            "/dev/block/dm-14 39004 38884 0 100% /vendor_dlkm\n"
            "tmpfs 3878956 32 3878924 1% /apex\n"
        )
    lines = []
    for line in raw.replace("\r\n", "\n").replace("\r", "\n").split("\n"):
        line = line.strip()
        if not line:
            continue
        compact = compact_df_line(line)
        if len(compact) > 60:
            compact = compact[:60]
        lines.append(compact)
    return "\n".join(lines) + "\n"


def main():
    with open(PROFILE, "r", encoding="utf-8") as fh:
        data = json.load(fh)
    overlay = {}
    if os.path.isfile(OVERLAY):
        with open(OVERLAY, "r", encoding="utf-8") as fh:
            overlay = json.load(fh)

    linux = data.setdefault("linux", {})
    proc = linux.setdefault("proc", {})
    proc["bootId"] = overlay.get("148", BOOT_ID)

    stdout = load_df()
    commands = linux.setdefault("commands", {})
    for key in (
        "df",
        "/system/bin/df",
        "df -k",
        "/system/bin/df -k",
        "/system/bin/df -P",
        "df -P",
        "/system/bin/df 2>/dev/null",
        "/system/bin/df -k 2>/dev/null",
        "cat /proc/sys/kernel/random/boot_id",
        "/system/bin/cat /proc/sys/kernel/random/boot_id",
    ):
        if "boot_id" in key:
            commands[key] = proc["bootId"] + "\n"
        else:
            commands[key] = stdout

    files = linux.setdefault("files", {})
    files["/proc/sys/kernel/random/boot_id"] = proc["bootId"] + "\n"
    write_text(
        os.path.join(FILES, "proc", "sys", "kernel", "random", "boot_id"),
        proc["bootId"],
    )

    android = data.setdefault("android", {})
    build = android.setdefault("build", {})
    build["BOARD"] = overlay.get("51", BOARD)

    props = android.setdefault("properties", {})
    props["ro.product.board"] = build["BOARD"]
    props["ro.board.platform"] = build["BOARD"]
    props["ro.boot.hardware.platform"] = build["BOARD"]
    props["ro.hardware"] = build.get("HARDWARE") or "oriole"
    props["ro.config.ringtone"] = overlay.get("15", RINGTONE)

    settings = android.setdefault("settings", {})
    system = settings.setdefault("system", {})
    system["ringtone"] = overlay.get("15", RINGTONE)
    secure = settings.setdefault("secure", {})
    if not secure.get("android_id"):
        identifiers = android.get("identifiers") or {}
        if identifiers.get("androidId"):
            secure["android_id"] = identifiers["androidId"]

    sensors = android.setdefault("sensors", {})
    resolutions = sensors.setdefault("resolutions", {})
    resolutions["1"] = 0.0047856453

    display = android.setdefault("display", {})
    display["widthPixels"] = 1080
    display["heightPixels"] = 2219
    display["densityDpi"] = 420

    android["displays"] = [
        {
            "id": 0,
            "name": "内置屏幕",
            "flags": 131,
            "widthPixels": 1080,
            "heightPixels": 2400,
            "densityDpi": 420,
        }
    ]

    power = android.setdefault("powerProfile", {})
    avg = power.setdefault("averagePower", {})
    avg["battery.capacity"] = 4614.0

    with open(PROFILE, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    print("patched", PROFILE)
    print("bootId", proc["bootId"])
    print("BOARD", build["BOARD"])
    print("ringtone", props["ro.config.ringtone"])
    print("df keys", [k for k in commands if "df" in k])
    print("displays", android["displays"])
    print("battery.capacity", avg["battery.capacity"])


if __name__ == "__main__":
    main()
