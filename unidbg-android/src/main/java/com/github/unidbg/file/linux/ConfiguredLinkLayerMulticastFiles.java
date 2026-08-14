package com.github.unidbg.file.linux;

import com.github.unidbg.env.TraceEnvironmentConfig;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure static renderer for Linux {@code /proc/net/dev_mcast} text from
 * {@code network.linkLayerMulticastEntries}.
 * No Emulator/FileIO access, no sidecar events, and no host network reads.
 * Format matches kernel {@code net/core/net-procfs.c} {@code dev_mc_seq_show}:
 * {@code %-4d %-15s %-5d %-5d %phN} plus LF. {@code %phN} on a 6-byte MAC is 12
 * consecutive lowercase hex digits without colons; there is no header.
 * Entries are never inferred from {@code interfaces.mac}, {@code linkLayerBroadcast},
 * wifi, or other configuration.
 */
public final class ConfiguredLinkLayerMulticastFiles {

    /**
     * Kernel dev_mcast row: {@code %-4d %-15s %-5d %-5d <12 lowercase hex>} plus LF.
     */
    private static final String LINE_FORMAT = "%-4d %-15s %-5d %-5d %s\n";

    private ConfiguredLinkLayerMulticastFiles() {
    }

    /**
     * Render {@code /proc/net/dev_mcast} (and self/pid aliases) from config.
     *
     * @return {@code null} when {@code network.linkLayerMulticastEntries} is not configured;
     *         zero-length bytes when configured as {@code []} (no header);
     *         otherwise UTF-8 lines ending with {@code \n} in JSON array order
     */
    public static byte[] renderProcNetDevMcast(TraceEnvironmentConfig config) {
        if (config == null || !config.isNetworkLinkLayerMulticastEntriesConfigured()) {
            return null;
        }
        List<TraceEnvironmentConfig.NetworkLinkLayerMulticastEntryConfig> entries =
                config.getNetworkLinkLayerMulticastEntries();
        if (entries.isEmpty()) {
            return new byte[0];
        }
        StringBuilder sb = new StringBuilder(entries.size() * 48);
        for (TraceEnvironmentConfig.NetworkLinkLayerMulticastEntryConfig entry : entries) {
            int index = findInterfaceIndex(config, entry.getInterfaceName());
            sb.append(String.format(Locale.ROOT, LINE_FORMAT,
                    Integer.valueOf(index),
                    entry.getInterfaceName(),
                    Integer.valueOf(entry.getReferenceCount()),
                    Integer.valueOf(entry.isGlobalUse() ? 1 : 0),
                    entry.getMac().replace(":", "")));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Count distinct {@code interfaceName} values in {@code network.linkLayerMulticastEntries}
     * (structural source for sidecar {@code interfaceCount} only).
     */
    public static int countDistinctInterfaces(TraceEnvironmentConfig config) {
        if (config == null || !config.isNetworkLinkLayerMulticastEntriesConfigured()) {
            return 0;
        }
        Set<String> names = new HashSet<String>();
        for (TraceEnvironmentConfig.NetworkLinkLayerMulticastEntryConfig entry
                : config.getNetworkLinkLayerMulticastEntries()) {
            names.add(entry.getInterfaceName());
        }
        return names.size();
    }

    private static int findInterfaceIndex(TraceEnvironmentConfig config, String interfaceName) {
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            if (interfaceName.equals(iface.getName())) {
                return iface.getIndex();
            }
        }
        throw new IllegalStateException(
                "network.linkLayerMulticastEntries interfaceName was not resolved: " + interfaceName);
    }
}
