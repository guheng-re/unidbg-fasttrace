# -*- coding: utf-8 -*-
"""Apply Pixel 6 USB-collected identity into the unidbg profile.

Fills the four 8117 gaps that still came from a stale / incomplete overlay:
  #6.7  pkg,uid,SELinux   — real uid 10273 + MCS categories after installing 12306
  #10   statfs pair       — create /storage/emulated/0 so the second statfs fires
  #18   /system tree      — materialize filesystem.stat paths under files/
  #26   install UUID      — create dataDir/files so j209302 can persist
"""
from __future__ import print_function

import json
import os
import shutil
import stat as statmod

ROOT = os.path.dirname(os.path.abspath(__file__))
DUMP = os.path.join(ROOT, "device-dump")
FILES = os.path.join(ROOT, "files")
PROFILE = os.path.join(ROOT, "device-fingerprint.json")

SERIAL = "24241FDF6000S1"
PKG = "com.MobileTicket"
APP_UID = 10273
APP_PID = 10231  # keep emulator pid stable; only identity fields change
VERSION_CODE = 278
VERSION_NAME = "5.9.6.6"
INSTALL_MS = 1787035407788
UPDATE_MS = 1787035407820
APK_DIR = "/data/app/~~tRlqz47oJseOtxMBOXJGvg==/com.MobileTicket-DQDrk3km7zNSbGW6GiWxdQ=="
DATA_DIR = "/data/user/0/com.MobileTicket"
SELINUX = "u:r:untrusted_app:s0:c17,c257,c512,c768"
FILE_SELINUX = "u:object_r:app_data_file:s0:c17,c257,c512,c768"
SUPP_GIDS = [3001, 3002, 3003, 9997, 20273, 50273]
CGROUPS = [
    "3:cpuset:/top-app",
    "2:cpu:/system",
    "1:blkio:/",
    "0::/uid_%d/pid_%d" % (APP_UID, APP_PID),
]
APP_FLAGS = 0x4 | 0x40 | 0x100000  # HAS_CODE | ALLOW_CLEAR_USER_DATA | LARGE_HEAP
STATFS_DATA = {
    "blockSize": 4096,
    "blocks": 28905493,
    "blocksFree": 22375892,
    "blocksAvailable": 22343124,
    "files": 13278717,
    "filesFree": 13075346,
    "fragmentSize": 4096,
}


def write_bytes(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(data)


def write_text(path, text):
    if text and not text.endswith("\n"):
        text += "\n"
    write_bytes(path, (text or "").encode("utf-8"))


def load_system_stat():
    for name in ("system_stat2.txt", "system_stat.txt"):
        path = os.path.join(DUMP, "extra", "system", name)
        if os.path.isfile(path):
            return path
    return None


def parse_system_stat_file(stat_txt):
    rows = []
    if not stat_txt:
        return rows
    with open(stat_txt, "r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            parts = line.rstrip("\n").split("|")
            if len(parts) < 6:
                continue
            path, size_s, mtime_s, mode_hex, uid_s, gid_s = parts[:6]
            if not path.startswith("/"):
                continue
            try:
                rows.append({
                    "path": path,
                    "size": int(size_s),
                    "mtime": int(float(mtime_s)),
                    "mode": int(mode_hex, 16),
                    "uid": int(uid_s),
                    "gid": int(gid_s),
                })
            except ValueError:
                continue
    return rows


def parse_system_dirs_file():
    path = os.path.join(DUMP, "extra", "system", "system_dirs2.txt")
    dirs = {}
    if not os.path.isfile(path):
        return dirs
    cur = None
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.rstrip("\n")
            if line.startswith("===") and line.endswith("==="):
                cur = line.strip("=")
                dirs.setdefault(cur, [])
                continue
            if cur and line and line not in (".", "..") and line not in dirs[cur]:
                dirs[cur].append(line)
    return dirs


def materialize_system_tree():
    rows = parse_system_stat_file(load_system_stat())
    created_dir = 0
    created_file = 0
    skipped = 0
    for rec in rows:
        dest = os.path.join(FILES, rec["path"].lstrip("/").replace("/", os.sep))
        is_dir = statmod.S_ISDIR(rec["mode"])
        if is_dir:
            if not os.path.isdir(dest):
                os.makedirs(dest, exist_ok=True)
                created_dir += 1
            else:
                skipped += 1
        else:
            parent = os.path.dirname(dest)
            if parent:
                os.makedirs(parent, exist_ok=True)
            if os.path.exists(dest):
                skipped += 1
                continue
            write_bytes(dest, b"")
            created_file += 1
    print("system tree rows", len(rows), "dirs+", created_dir, "files+", created_file, "existed", skipped)
    return rows, parse_system_dirs_file()


def materialize_app_and_storage():
    for rel in (
        os.path.join("storage", "emulated", "0"),
        os.path.join("data", "user", "0", PKG, "files"),
        os.path.join("data", "user", "0", PKG, "cache"),
        os.path.join("data", "user", "0", PKG, "code_cache"),
        os.path.join("data", "user", "0", PKG, "no_backup"),
        os.path.join("data", "user", "0", PKG, "shared_prefs"),
        os.path.join("data", "user", "0", PKG, "app_turing"),
    ):
        os.makedirs(os.path.join(FILES, rel), exist_ok=True)
    print("created data/storage overlay dirs")


def write_proc_identity():
    status = (
        "Name:\tcom.MobileTicket\n"
        "Umask:\t0077\n"
        "State:\tS (sleeping)\n"
        "Tgid:\t%d\n"
        "Ngid:\t0\n"
        "Pid:\t%d\n"
        "PPid:\t1\n"
        "TracerPid:\t0\n"
        "Uid:\t%d\t%d\t%d\t%d\n"
        "Gid:\t%d\t%d\t%d\t%d\n"
        "FDSize:\t256\n"
        "Groups:\t%s\n"
        "Threads:\t37\n"
        "NoNewPrivs:\t0\n"
        "Seccomp:\t2\n"
        % (
            APP_PID,
            APP_PID,
            APP_UID,
            APP_UID,
            APP_UID,
            APP_UID,
            APP_UID,
            APP_UID,
            APP_UID,
            APP_UID,
            " ".join(str(g) for g in SUPP_GIDS),
        )
    )
    cgroup = "\n".join(CGROUPS) + "\n"
    for name in ("self", str(APP_PID)):
        write_bytes(os.path.join(FILES, "proc", name, "cmdline"), b"com.MobileTicket\x00")
        write_text(os.path.join(FILES, "proc", name, "cgroup"), cgroup)
        write_text(os.path.join(FILES, "proc", name, "status"), status)
    print("wrote proc cmdline/cgroup/status for self and", APP_PID)


def stat_rec(mode, uid, gid, size, mtime_ms):
    return {
        "mode": mode,
        "uid": uid,
        "gid": gid,
        "size": size,
        "blockSize": 4096,
        "blocks": max(1, (size + 4095) // 4096) if size else 1,
        "mtimeMillis": mtime_ms,
        "ctimeMillis": mtime_ms,
        "atimeMillis": mtime_ms,
    }


def patch_profile(stat_rows, dir_map):
    with open(PROFILE, "r", encoding="utf-8") as fh:
        profile = json.load(fh)

    proc = profile["process"]
    proc["uid"] = APP_UID
    proc["gid"] = APP_UID
    proc["euid"] = APP_UID
    proc["egid"] = APP_UID
    proc["supplementaryGids"] = list(SUPP_GIDS)

    linux_proc = profile["linux"]["proc"]
    linux_proc["selinuxContext"] = SELINUX
    linux_proc["cgroups"] = list(CGROUPS)
    linux_proc["fileSelinuxContexts"] = {
        DATA_DIR: FILE_SELINUX,
        DATA_DIR + "/files": FILE_SELINUX,
        DATA_DIR + "/cache": FILE_SELINUX,
        DATA_DIR + "/code_cache": FILE_SELINUX,
        APK_DIR: "u:object_r:apk_data_file:s0",
        APK_DIR + "/base.apk": "u:object_r:apk_data_file:s0",
    }

    android = profile["android"]
    android["packageName"] = PKG
    android["versionName"] = VERSION_NAME
    android["versionCode"] = VERSION_CODE
    android["apkPath"] = APK_DIR + "/base.apk"
    android["dataDir"] = DATA_DIR
    android["nativeLibraryDir32"] = APK_DIR + "/lib/arm"
    android["nativeLibraryDir64"] = APK_DIR + "/lib/arm64"

    found = False
    for pkg in android.get("packages", []):
        if pkg.get("packageName") == PKG:
            pkg["uid"] = APP_UID
            pkg["versionCode"] = VERSION_CODE
            pkg["versionName"] = VERSION_NAME
            pkg["sourceDir"] = APK_DIR
            pkg["dataDir"] = DATA_DIR
            pkg["firstInstallTimeMillis"] = INSTALL_MS
            pkg["lastUpdateTimeMillis"] = UPDATE_MS
            pkg["applicationFlags"] = APP_FLAGS
            pkg["enabled"] = True
            pkg["systemApp"] = False
            found = True
            break
    if not found:
        android.setdefault("packages", []).insert(0, {
            "packageName": PKG,
            "sourceDir": APK_DIR,
            "dataDir": DATA_DIR,
            "uid": APP_UID,
            "versionCode": VERSION_CODE,
            "versionName": VERSION_NAME,
            "enabled": True,
            "systemApp": False,
            "signaturesHex": ["8C62B8AE0C9E9394FC7A6D0D95ED541C"],
            "firstInstallTimeMillis": INSTALL_MS,
            "lastUpdateTimeMillis": UPDATE_MS,
            "applicationFlags": APP_FLAGS,
        })

    fs = profile.setdefault("filesystem", {})
    statfs = fs.setdefault("statfs", {})
    statfs["/data"] = dict(STATFS_DATA)
    statfs["/storage/emulated/0"] = dict(STATFS_DATA)

    stat = fs.setdefault("stat", {})
    for rec in stat_rows:
        stat[rec["path"]] = stat_rec(
            rec["mode"], rec["uid"], rec["gid"], rec["size"], rec["mtime"] * 1000)
    if dir_map:
        dirs = fs.setdefault("directories", {})
        for path, names in dir_map.items():
            dirs[path] = names
    stat["/data"] = stat_rec(0o40771, 1000, 1000, 4096, INSTALL_MS)
    stat["/data/user"] = stat_rec(0o40771, 1000, 1000, 4096, INSTALL_MS)
    stat["/data/user/0"] = stat_rec(0o40771, 1000, 1000, 4096, INSTALL_MS)
    stat[DATA_DIR] = stat_rec(0o40700, APP_UID, APP_UID, 3452, INSTALL_MS)
    stat[DATA_DIR + "/files"] = stat_rec(0o40771, APP_UID, APP_UID, 3452, INSTALL_MS)
    stat["/storage"] = stat_rec(0o40755, 0, 0, 4096, INSTALL_MS)
    stat["/storage/emulated"] = stat_rec(0o40755, 1023, 1023, 4096, INSTALL_MS)
    stat["/storage/emulated/0"] = stat_rec(0o40771, 1023, 1023, 4096, INSTALL_MS)
    stat[APK_DIR] = stat_rec(0o40775, 1000, 1000, 3452, UPDATE_MS)
    stat[APK_DIR + "/base.apk"] = stat_rec(0o100644, 1000, 1000, 180000000, UPDATE_MS)

    with open(PROFILE, "w", encoding="utf-8") as fh:
        json.dump(profile, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("patched", PROFILE)
    print("  process.uid", proc["uid"], "selinux", linux_proc["selinuxContext"])
    print("  ticket uid", APP_UID, "versionCode", VERSION_CODE, "install", INSTALL_MS)
    print("  statfs /data", statfs["/data"]["blocksAvailable"])


def main():
    rows, dir_map = materialize_system_tree()
    materialize_app_and_storage()
    write_proc_identity()
    patch_profile(rows, dir_map)
    print("dir keys", len(dir_map), "stat rows", len(rows))
    print("done")


if __name__ == "__main__":
    main()
