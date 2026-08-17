package com.github.unidbg.file.linux;

import com.github.unidbg.env.TraceEnvironmentConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Pure static renderer for {@code linux.processes} cmdline/comm snapshots.
 */
public final class ConfiguredProcessFiles {

    private ConfiguredProcessFiles() {
    }

    public static byte[] renderCmdline(TraceEnvironmentConfig.LinuxProcessConfig process) {
        if (process == null) {
            return null;
        }
        List<String> cmdline = process.getCmdline();
        if (cmdline == null || cmdline.isEmpty()) {
            return new byte[0];
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cmdline.size(); i++) {
            if (i > 0) {
                sb.append('\0');
            }
            sb.append(cmdline.get(i));
        }
        sb.append('\0');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] renderComm(TraceEnvironmentConfig.LinuxProcessConfig process) {
        if (process == null || process.getComm() == null) {
            return null;
        }
        return (process.getComm() + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
