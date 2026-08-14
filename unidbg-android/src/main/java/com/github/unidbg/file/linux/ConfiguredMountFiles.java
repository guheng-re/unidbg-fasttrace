package com.github.unidbg.file.linux;

import com.github.unidbg.env.TraceEnvironmentConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Pure static renderer for {@code /proc/mounts} and {@code /proc/self/mountinfo} text from
 * {@code filesystem.mounts} config. No Emulator/FileIO access and no sidecar events.
 */
public final class ConfiguredMountFiles {

    private static final byte[] EMPTY = new byte[0];

    private ConfiguredMountFiles() {
    }

    /**
     * Render {@code /proc/mounts} lines from config.
     *
     * @return {@code null} when {@code filesystem.mounts} is not configured;
     *         empty array when configured as {@code []};
     *         otherwise UTF-8 bytes of fstab-style lines ending with {@code \n}
     */
    public static byte[] renderProcMounts(TraceEnvironmentConfig config) {
        if (config == null || !config.isFilesystemMountsConfigured()) {
            return null;
        }
        List<TraceEnvironmentConfig.FileSystemMountConfig> mounts = config.getFilesystemMounts();
        if (mounts.isEmpty()) {
            return EMPTY.clone();
        }
        StringBuilder sb = new StringBuilder(mounts.size() * 64);
        for (TraceEnvironmentConfig.FileSystemMountConfig m : mounts) {
            sb.append(escapeMountField(m.getSource()));
            sb.append(' ');
            sb.append(escapeMountField(m.getTarget()));
            sb.append(' ');
            sb.append(m.getFileSystemType());
            sb.append(' ');
            sb.append(m.getOptions());
            sb.append(' ');
            sb.append(m.getDump());
            sb.append(' ');
            sb.append(m.getPass());
            sb.append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Render {@code /proc/self/mountinfo} lines from config.
     * If any entry lacks a complete mountinfo field group ({@code mountId}/{@code parentId}/
     * {@code major}/{@code minor}), returns {@code null} for the whole render (never a partial list).
     *
     * @return {@code null} when mounts not configured, or any entry is incomplete for mountinfo;
     *         empty array when configured as {@code []};
     *         otherwise UTF-8 bytes of mountinfo lines ending with {@code \n}
     */
    public static byte[] renderMountInfo(TraceEnvironmentConfig config) {
        if (config == null || !config.isFilesystemMountsConfigured()) {
            return null;
        }
        List<TraceEnvironmentConfig.FileSystemMountConfig> mounts = config.getFilesystemMounts();
        if (mounts.isEmpty()) {
            return EMPTY.clone();
        }
        for (TraceEnvironmentConfig.FileSystemMountConfig m : mounts) {
            if (!m.isMountInfoConfigured()) {
                return null;
            }
        }
        StringBuilder sb = new StringBuilder(mounts.size() * 96);
        for (TraceEnvironmentConfig.FileSystemMountConfig m : mounts) {
            sb.append(m.getMountId().intValue());
            sb.append(' ');
            sb.append(m.getParentId().intValue());
            sb.append(' ');
            sb.append(m.getMajor().intValue());
            sb.append(':');
            sb.append(m.getMinor().intValue());
            sb.append(' ');
            sb.append(escapeMountField(m.getRoot()));
            sb.append(' ');
            sb.append(escapeMountField(m.getTarget()));
            sb.append(' ');
            sb.append(m.getMountOptions());
            List<String> optional = m.getOptionalFields();
            for (String field : optional) {
                sb.append(' ');
                sb.append(field);
            }
            sb.append(" - ");
            sb.append(m.getFileSystemType());
            sb.append(' ');
            sb.append(escapeMountField(m.getSource()));
            sb.append(' ');
            sb.append(m.getSuperOptions());
            sb.append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Linux mount-table field escaping for path-like fields (source/target/root).
     * Processes character-by-character so escape sequences are not re-escaped:
     * {@code \}→{@code \134}, space→{@code \040}, TAB→{@code \011}, LF→{@code \012}.
     */
    static String escapeMountField(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        StringBuilder sb = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            String repl;
            switch (c) {
                case '\\':
                    repl = "\\134";
                    break;
                case ' ':
                    repl = "\\040";
                    break;
                case '\t':
                    repl = "\\011";
                    break;
                case '\n':
                    repl = "\\012";
                    break;
                default:
                    repl = null;
                    break;
            }
            if (repl != null) {
                if (sb == null) {
                    sb = new StringBuilder(value.length() + 8);
                    sb.append(value, 0, i);
                }
                sb.append(repl);
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? value : sb.toString();
    }
}
