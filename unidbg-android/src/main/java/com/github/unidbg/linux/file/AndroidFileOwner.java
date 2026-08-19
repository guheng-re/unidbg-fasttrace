package com.github.unidbg.linux.file;

import com.github.unidbg.Emulator;
import com.github.unidbg.env.TraceEnvironmentConfig;

/**
 * Default {@code st_uid}/{@code st_gid} for guest files when
 * {@code filesystem.stat} does not override them.
 * <p>
 * Hardcoding {@code 0} for every path made {@code /data/app/...} look like
 * a file the current process created. Packers compare APK {@code st_uid}
 * to {@code getuid()} / {@code 2000} (shell) and abort if they match.
 * Installed packages on Android are owned by {@code AID_SYSTEM} (1000).
 */
public final class AndroidFileOwner {

    public static final int AID_SYSTEM = 1000;

    private AndroidFileOwner() {
    }

    public static int uid(Emulator<?> emulator, String path) {
        int processUid = 0;
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null) {
            processUid = config.getUid(0);
        }
        return uid(path, processUid);
    }

    public static int uid(String path, int processUid) {
        if (path == null || path.isEmpty()) {
            return 0;
        }
        if (isSystemOwned(path)) {
            return AID_SYSTEM;
        }
        if (path.startsWith("/data/data/") || path.startsWith("/data/user/")) {
            return processUid;
        }
        return 0;
    }

    public static int gid(Emulator<?> emulator, String path) {
        return uid(emulator, path);
    }

    public static void fillAnon(Emulator<?> emulator,
                                com.github.unidbg.file.linux.StatStructure stat,
                                int mode, String path) {
        stat.st_dev = 1;
        stat.st_ino = path == null ? 1 : Math.max(1, path.hashCode() & 0x7fffffff);
        stat.st_mode = mode;
        stat.st_nlink = 1;
        stat.st_uid = uid(emulator, path);
        stat.st_gid = gid(emulator, path);
        stat.st_size = 0;
        stat.st_blksize = emulator.getPageAlign();
        stat.st_blocks = 0;
        stat.setLastModification(System.currentTimeMillis());
        stat.pack();
    }

    private static boolean isSystemOwned(String path) {
        return path.startsWith("/system/")
                || path.startsWith("/vendor/")
                || path.startsWith("/product/")
                || path.startsWith("/data/app/")
                || path.startsWith("/data/app-lib/")
                || path.startsWith("/data/app-private/");
    }
}
