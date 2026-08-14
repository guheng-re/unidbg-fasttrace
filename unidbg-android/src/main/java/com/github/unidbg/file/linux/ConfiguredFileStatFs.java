package com.github.unidbg.file.linux;

import com.github.unidbg.Emulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.struct.StatFS;
import com.github.unidbg.trace.TraceEnvironmentEventSink;

/**
 * Applies {@code filesystem.statfs} JSON config onto a {@link StatFS}.
 * Config-only overlay via longest mount match ({@link TraceEnvironmentConfig#getFilesystemStatFs(String)});
 * emits one {@code filesystem_statfs} sidecar event on successful apply.
 */
public final class ConfiguredFileStatFs {

    private ConfiguredFileStatFs() {
    }

    /**
     * Overlay configured filesystem metadata for {@code pathname} onto {@code statFS} ({@code api=statfs}).
     * Uses longest path-boundary mount match from {@link TraceEnvironmentConfig#getFilesystemStatFs(String)}.
     *
     * @return {@code false} when config is absent or no mount matches (structure unchanged);
     *         {@code true} after applying configured fields and {@link StatFS#pack()}
     * @throws IllegalArgumentException if {@code statFS} is {@code null}
     */
    public static boolean apply(Emulator<?> emulator, String pathname, StatFS statFS) {
        return apply(emulator, pathname, statFS, "statfs");
    }

    /**
     * Overlay configured filesystem metadata for {@code pathname} onto {@code statFS}
     * with an explicit sidecar {@code api} (path-based callers use {@code statfs};
     * fd-based {@code fstatfs}/{@code fstatfs64} uses {@code fstatfs}).
     *
     * @return {@code false} when config is absent or no mount matches (structure unchanged);
     *         {@code true} after applying configured fields and {@link StatFS#pack()}
     * @throws IllegalArgumentException if {@code statFS} is {@code null}
     */
    public static boolean apply(Emulator<?> emulator, String pathname, StatFS statFS, String api) {
        if (statFS == null) {
            throw new IllegalArgumentException("statFS must not be null");
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null || !config.isFilesystemStatFsConfigured()) {
            return false;
        }
        TraceEnvironmentConfig.FileStatFsConfig entry = config.getFilesystemStatFs(pathname);
        if (entry == null) {
            return false;
        }

        if (entry.isTypeConfigured()) {
            // low 32 bits: 4294967295L → int -1
            statFS.setType(entry.getType().intValue());
        }
        if (entry.isBlockSizeConfigured()) {
            statFS.setBlockSize(entry.getBlockSize().intValue());
        }
        if (entry.isBlocksConfigured()) {
            statFS.f_blocks = entry.getBlocks().longValue();
        }
        if (entry.isBlocksFreeConfigured()) {
            statFS.f_bfree = entry.getBlocksFree().longValue();
        }
        if (entry.isBlocksAvailableConfigured()) {
            statFS.f_bavail = entry.getBlocksAvailable().longValue();
        }
        if (entry.isFilesConfigured()) {
            statFS.f_files = entry.getFiles().longValue();
        }
        if (entry.isFilesFreeConfigured()) {
            statFS.f_ffree = entry.getFilesFree().longValue();
        }
        if (entry.isFsidConfigured()) {
            long[] fsid = entry.getFsid();
            if (fsid != null && fsid.length >= 2) {
                if (statFS.f_fsid == null || statFS.f_fsid.length < 2) {
                    statFS.f_fsid = new int[2];
                }
                // low 32 bits each
                statFS.f_fsid[0] = (int) fsid[0];
                statFS.f_fsid[1] = (int) fsid[1];
            }
        }
        if (entry.isNameLengthConfigured()) {
            statFS.setNameLen(entry.getNameLength().intValue());
        }
        if (entry.isFragmentSizeConfigured()) {
            statFS.setFrSize(entry.getFragmentSize().intValue());
        }
        if (entry.isFlagsConfigured()) {
            // low 32 bits: 4294967295L → int -1
            statFS.setFlags(entry.getFlags().intValue());
        }

        statFS.pack();

        String mountPoint = entry.getMountPoint();
        TraceEnvironmentEventSink.emit(emulator, "filesystem_statfs", api,
                buildValueSummary(entry), "json-config",
                "读取配置的文件系统 statfs " + mountPoint);
        return true;
    }

    /**
     * Stable one-line summary: mountPoint plus configured fields only, in fixed order
     * type,blockSize,blocks,blocksFree,blocksAvailable,files,filesFree,fsid,nameLength,fragmentSize,flags.
     */
    private static String buildValueSummary(TraceEnvironmentConfig.FileStatFsConfig entry) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("mountPoint=").append(entry.getMountPoint());
        if (entry.isTypeConfigured()) {
            sb.append(",type=").append(entry.getType());
        }
        if (entry.isBlockSizeConfigured()) {
            sb.append(",blockSize=").append(entry.getBlockSize());
        }
        if (entry.isBlocksConfigured()) {
            sb.append(",blocks=").append(entry.getBlocks());
        }
        if (entry.isBlocksFreeConfigured()) {
            sb.append(",blocksFree=").append(entry.getBlocksFree());
        }
        if (entry.isBlocksAvailableConfigured()) {
            sb.append(",blocksAvailable=").append(entry.getBlocksAvailable());
        }
        if (entry.isFilesConfigured()) {
            sb.append(",files=").append(entry.getFiles());
        }
        if (entry.isFilesFreeConfigured()) {
            sb.append(",filesFree=").append(entry.getFilesFree());
        }
        if (entry.isFsidConfigured()) {
            long[] fsid = entry.getFsid();
            sb.append(",fsid=[");
            if (fsid != null && fsid.length >= 2) {
                sb.append(fsid[0]).append(',').append(fsid[1]);
            }
            sb.append(']');
        }
        if (entry.isNameLengthConfigured()) {
            sb.append(",nameLength=").append(entry.getNameLength());
        }
        if (entry.isFragmentSizeConfigured()) {
            sb.append(",fragmentSize=").append(entry.getFragmentSize());
        }
        if (entry.isFlagsConfigured()) {
            sb.append(",flags=").append(entry.getFlags());
        }
        return sb.toString();
    }
}
