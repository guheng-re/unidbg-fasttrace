package com.github.unidbg.file.linux;

import com.github.unidbg.env.TraceEnvironmentConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Pure static renderer for Linux {@code /proc/net/arp} text from {@code network.arpEntries}.
 * No Emulator/FileIO access, no sidecar events, and no host network reads.
 * Format matches kernel {@code net/ipv4/arp.c} {@code arp_seq_show} /
 * {@code arp_format_neigh_entry}.
 */
public final class ConfiguredArpEntryFiles {

    /**
     * Stable Linux {@code /proc/net/arp} header ({@code arp_seq_show} token line).
     */
    public static final String HEADER =
            "IP address       HW type     Flags       HW address            Mask     Device\n";

    /**
     * Kernel {@code arp_format_neigh_entry} row: semantically equivalent to
     * {@code %-16s 0x%-10x0x%-10x%-17s     *        %s} plus LF. Mask is always {@code *}.
     */
    private static final String ARP_LINE_FORMAT =
            "%-16s 0x%-10x0x%-10x%-17s     *        %s\n";

    private ConfiguredArpEntryFiles() {
    }

    /**
     * Render {@code /proc/net/arp} (and self/pid aliases) from config.
     *
     * @return {@code null} when {@code network.arpEntries} is not configured;
     *         header-only UTF-8 bytes when configured as {@code []};
     *         otherwise UTF-8 table ending with {@code \n}
     */
    public static byte[] renderProcNetArp(TraceEnvironmentConfig config) {
        if (config == null || !config.isNetworkArpEntriesConfigured()) {
            return null;
        }
        List<TraceEnvironmentConfig.NetworkArpEntryConfig> entries = config.getNetworkArpEntries();
        StringBuilder sb = new StringBuilder(HEADER.length() + entries.size() * 80);
        sb.append(HEADER);
        for (TraceEnvironmentConfig.NetworkArpEntryConfig entry : entries) {
            sb.append(String.format(Locale.ROOT, ARP_LINE_FORMAT,
                    entry.getIpv4(),
                    Long.valueOf(entry.getHardwareType()),
                    Long.valueOf(entry.getFlags()),
                    entry.getMac(),
                    entry.getInterfaceName()));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
