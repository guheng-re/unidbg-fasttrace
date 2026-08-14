package com.github.unidbg.file.linux;

import com.github.unidbg.Emulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileIO;
import com.github.unidbg.trace.TraceEnvironmentEventSink;

/**
 * Applies {@code filesystem.stat} JSON config onto a {@link StatStructure}.
 * Config-only overlay; emits one {@code filesystem_stat} sidecar event on successful apply.
 */
public final class ConfiguredFileStat {

    private ConfiguredFileStat() {
    }

    /**
     * Resolve path via {@link FileIO#getPath()} then overlay config ({@code api=fstat}).
     * Returns {@code false} when {@code file} is null, or when {@code getPath()} is missing
     * ({@link AbstractMethodError} / {@link UnsupportedOperationException} only).
     * Null {@code stat} still throws via the path-based apply.
     */
    public static boolean apply(Emulator<?> emulator, FileIO file, StatStructure stat) {
        if (file == null) {
            return false;
        }
        final String path;
        try {
            path = file.getPath();
        } catch (AbstractMethodError e) {
            return false;
        } catch (UnsupportedOperationException e) {
            return false;
        }
        return applyInternal(emulator, path, stat, "fstat");
    }

    /**
     * Overlay configured file metadata for {@code pathname} onto {@code stat} ({@code api=stat}).
     *
     * @return {@code false} when config is absent or path is not configured (structure unchanged);
     *         {@code true} after applying configured fields and {@link StatStructure#pack()}
     * @throws IllegalArgumentException if {@code stat} is {@code null}
     */
    public static boolean apply(Emulator<?> emulator, String pathname, StatStructure stat) {
        return applyInternal(emulator, pathname, stat, "stat");
    }

    private static boolean applyInternal(Emulator<?> emulator, String pathname, StatStructure stat, String api) {
        if (stat == null) {
            throw new IllegalArgumentException("stat must not be null");
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null || !config.isFilesystemStatConfigured()) {
            return false;
        }
        TraceEnvironmentConfig.FileStatConfig entry = config.getFilesystemStat(pathname);
        if (entry == null) {
            return false;
        }

        if (entry.isDeviceConfigured()) {
            stat.st_dev = entry.getDevice().longValue();
        }
        if (entry.isInodeConfigured()) {
            stat.setSt_ino(entry.getInode().longValue());
        }
        if (entry.isModeConfigured()) {
            // low 32 bits: 4294967295L → int -1
            stat.st_mode = entry.getMode().intValue();
        }
        if (entry.isUidConfigured()) {
            stat.st_uid = entry.getUid().intValue();
        }
        if (entry.isGidConfigured()) {
            stat.st_gid = entry.getGid().intValue();
        }
        if (entry.isSizeConfigured()) {
            stat.st_size = entry.getSize().longValue();
        }
        if (entry.isBlockSizeConfigured()) {
            stat.st_blksize = entry.getBlockSize().intValue();
        }
        if (entry.isBlocksConfigured()) {
            stat.st_blocks = entry.getBlocks().longValue();
        }
        if (entry.isAtimeMillisConfigured()) {
            stat.setSt_atim(entry.getAtimeMillis().longValue(), 0L);
        }
        if (entry.isMtimeMillisConfigured()) {
            stat.setSt_mtim(entry.getMtimeMillis().longValue(), 0L);
        }
        if (entry.isCtimeMillisConfigured()) {
            stat.setSt_ctim(entry.getCtimeMillis().longValue(), 0L);
        }

        stat.pack();

        String path = entry.getPath();
        TraceEnvironmentEventSink.emit(emulator, "filesystem_stat", api,
                buildValueSummary(entry), "json-config",
                "读取配置的文件元数据 " + path);
        return true;
    }

    /**
     * Stable one-line summary: normalized path plus configured fields only,
     * in fixed order device,inode,mode,uid,gid,size,blockSize,blocks,atimeMillis,mtimeMillis,ctimeMillis.
     */
    private static String buildValueSummary(TraceEnvironmentConfig.FileStatConfig entry) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("path=").append(entry.getPath());
        if (entry.isDeviceConfigured()) {
            sb.append(",device=").append(entry.getDevice());
        }
        if (entry.isInodeConfigured()) {
            sb.append(",inode=").append(entry.getInode());
        }
        if (entry.isModeConfigured()) {
            sb.append(",mode=").append(entry.getMode());
        }
        if (entry.isUidConfigured()) {
            sb.append(",uid=").append(entry.getUid());
        }
        if (entry.isGidConfigured()) {
            sb.append(",gid=").append(entry.getGid());
        }
        if (entry.isSizeConfigured()) {
            sb.append(",size=").append(entry.getSize());
        }
        if (entry.isBlockSizeConfigured()) {
            sb.append(",blockSize=").append(entry.getBlockSize());
        }
        if (entry.isBlocksConfigured()) {
            sb.append(",blocks=").append(entry.getBlocks());
        }
        if (entry.isAtimeMillisConfigured()) {
            sb.append(",atimeMillis=").append(entry.getAtimeMillis());
        }
        if (entry.isMtimeMillisConfigured()) {
            sb.append(",mtimeMillis=").append(entry.getMtimeMillis());
        }
        if (entry.isCtimeMillisConfigured()) {
            sb.append(",ctimeMillis=").append(entry.getCtimeMillis());
        }
        return sb.toString();
    }
}
