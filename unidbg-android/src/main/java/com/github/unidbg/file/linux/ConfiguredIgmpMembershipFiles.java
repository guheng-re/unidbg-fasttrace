package com.github.unidbg.file.linux;

import com.github.unidbg.env.TraceEnvironmentConfig;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure static renderer for Linux {@code /proc/net/igmp} text from {@code network.igmpMemberships}.
 * No Emulator/FileIO access, no sidecar events, and no host network reads.
 * Format matches kernel {@code net/ipv4/igmp.c} {@code igmp_mc_seq_show} on Android ARM
 * little-endian ({@code __be32} printed with {@code %08X}).
 */
public final class ConfiguredIgmpMembershipFiles {

    /**
     * Stable Linux {@code /proc/net/igmp} header ({@code igmp_mc_seq_show} token line).
     */
    public static final String HEADER =
            "Idx\tDevice    : Count Querier\tGroup    Users Timer\tReporter\n";

    /**
     * Kernel interface title: {@code %d\t%-10s: %5d %7s} plus LF.
     * Emitted once when an {@code interfaceName} first appears in JSON order.
     */
    private static final String IFACE_LINE_FORMAT = "%d\t%-10s: %5d %7s\n";

    /**
     * Kernel membership row: {@code \t\t\t\t%08X %5d %d:%08X\t\t%d} plus LF.
     */
    private static final String GROUP_LINE_FORMAT = "\t\t\t\t%08X %5d %d:%08X\t\t%d\n";

    private ConfiguredIgmpMembershipFiles() {
    }

    /**
     * Render {@code /proc/net/igmp} (and self/pid aliases) from config.
     *
     * @return {@code null} when {@code network.igmpMemberships} is not configured;
     *         header-only UTF-8 bytes when configured as {@code []};
     *         otherwise UTF-8 table ending with {@code \n}
     */
    public static byte[] renderProcNetIgmp(TraceEnvironmentConfig config) {
        if (config == null || !config.isNetworkIgmpMembershipsConfigured()) {
            return null;
        }
        List<TraceEnvironmentConfig.NetworkIgmpMembershipConfig> entries =
                config.getNetworkIgmpMemberships();
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (TraceEnvironmentConfig.NetworkIgmpMembershipConfig entry : entries) {
            String name = entry.getInterfaceName();
            Integer n = counts.get(name);
            counts.put(name, Integer.valueOf(n == null ? 1 : n.intValue() + 1));
        }
        StringBuilder sb = new StringBuilder(HEADER.length() + entries.size() * 80);
        sb.append(HEADER);
        Set<String> started = new HashSet<String>();
        for (TraceEnvironmentConfig.NetworkIgmpMembershipConfig entry : entries) {
            String name = entry.getInterfaceName();
            if (started.add(name)) {
                int index = findInterfaceIndex(config, name);
                int count = counts.get(name).intValue();
                sb.append(String.format(Locale.ROOT, IFACE_LINE_FORMAT,
                        Integer.valueOf(index), name, Integer.valueOf(count),
                        entry.getQuerierVersion()));
            }
            sb.append(String.format(Locale.ROOT, GROUP_LINE_FORMAT,
                    Long.valueOf(toLinuxIgmpGroupLong(entry.getGroupIpv4())),
                    Integer.valueOf(entry.getUsers()),
                    Integer.valueOf(entry.isTimerRunning() ? 1 : 0),
                    Long.valueOf(entry.getTimerClock()),
                    Integer.valueOf(entry.isReporter() ? 1 : 0)));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Count distinct {@code interfaceName} values in {@code network.igmpMemberships}
     * (structural Count source for sidecar {@code interfaceCount} only).
     */
    public static int countDistinctInterfaces(TraceEnvironmentConfig config) {
        if (config == null || !config.isNetworkIgmpMembershipsConfigured()) {
            return 0;
        }
        Set<String> names = new HashSet<String>();
        for (TraceEnvironmentConfig.NetworkIgmpMembershipConfig entry
                : config.getNetworkIgmpMemberships()) {
            names.add(entry.getInterfaceName());
        }
        return names.size();
    }

    /**
     * Kernel {@code __be32} printed with {@code %08X} on Android ARM little-endian:
     * {@code a.b.c.d} as {@code a|(b<<8)|(c<<16)|(d<<24)}. Example:
     * {@code 224.0.0.251} → {@code FB0000E0}.
     */
    static long toLinuxIgmpGroupLong(String ipv4) {
        String[] parts = ipv4.split("\\.", -1);
        int a = Integer.parseInt(parts[0]);
        int b = Integer.parseInt(parts[1]);
        int c = Integer.parseInt(parts[2]);
        int d = Integer.parseInt(parts[3]);
        return (a & 0xffL) | ((b & 0xffL) << 8) | ((c & 0xffL) << 16) | ((d & 0xffL) << 24);
    }

    private static int findInterfaceIndex(TraceEnvironmentConfig config, String interfaceName) {
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            if (interfaceName.equals(iface.getName())) {
                return iface.getIndex();
            }
        }
        throw new IllegalStateException(
                "network.igmpMemberships interfaceName was not resolved: " + interfaceName);
    }
}
