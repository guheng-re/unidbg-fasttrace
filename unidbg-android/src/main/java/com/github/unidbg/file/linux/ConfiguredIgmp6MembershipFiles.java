package com.github.unidbg.file.linux;

import com.github.unidbg.env.TraceEnvironmentConfig;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure static renderer for Linux {@code /proc/net/igmp6} text from {@code network.igmp6Memberships}.
 * No Emulator/FileIO access, no sidecar events, and no host network reads.
 * Format matches kernel {@code net/ipv6/mcast.c} {@code igmp6_mc_seq_show}:
 * {@code %-4d %-15s %pi6 %5d %08X %ld} plus LF. IPv6 is 32 uppercase hex digits
 * without colons (network byte order); there is no header.
 */
public final class ConfiguredIgmp6MembershipFiles {

    /**
     * Kernel igmp6 row: {@code %-4d %-15s <32 uppercase hex> %5d %08X %ld} plus LF.
     * {@code %pi6} is emitted as 32 uppercase hex characters (no colons).
     */
    private static final String LINE_FORMAT = "%-4d %-15s %s %5d %08X %d\n";

    private ConfiguredIgmp6MembershipFiles() {
    }

    /**
     * Render {@code /proc/net/igmp6} (and self/pid aliases) from config.
     *
     * @return {@code null} when {@code network.igmp6Memberships} is not configured;
     *         zero-length bytes when configured as {@code []} (no header);
     *         otherwise UTF-8 lines ending with {@code \n} in JSON array order
     */
    public static byte[] renderProcNetIgmp6(TraceEnvironmentConfig config) {
        if (config == null || !config.isNetworkIgmp6MembershipsConfigured()) {
            return null;
        }
        List<TraceEnvironmentConfig.NetworkIgmp6MembershipConfig> entries =
                config.getNetworkIgmp6Memberships();
        if (entries.isEmpty()) {
            return new byte[0];
        }
        StringBuilder sb = new StringBuilder(entries.size() * 80);
        for (TraceEnvironmentConfig.NetworkIgmp6MembershipConfig entry : entries) {
            int index = findInterfaceIndex(config, entry.getInterfaceName());
            sb.append(String.format(Locale.ROOT, LINE_FORMAT,
                    Integer.valueOf(index),
                    entry.getInterfaceName(),
                    entry.getGroupIpv6Hex(),
                    Integer.valueOf(entry.getUsers()),
                    Long.valueOf(entry.getFlags()),
                    Long.valueOf(entry.getTimer())));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Count distinct {@code interfaceName} values in {@code network.igmp6Memberships}
     * (structural source for sidecar {@code interfaceCount} only).
     */
    public static int countDistinctInterfaces(TraceEnvironmentConfig config) {
        if (config == null || !config.isNetworkIgmp6MembershipsConfigured()) {
            return 0;
        }
        Set<String> names = new HashSet<String>();
        for (TraceEnvironmentConfig.NetworkIgmp6MembershipConfig entry
                : config.getNetworkIgmp6Memberships()) {
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
                "network.igmp6Memberships interfaceName was not resolved: " + interfaceName);
    }
}
