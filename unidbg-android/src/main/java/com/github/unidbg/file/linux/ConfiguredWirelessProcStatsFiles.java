package com.github.unidbg.file.linux;

import com.github.unidbg.env.TraceEnvironmentConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Pure static renderer for Linux {@code /proc/net/wireless} text from
 * {@code network.wirelessProcStats}. No Emulator/FileIO access, no sidecar events,
 * and no host network, ioctl, netlink, or Wi-Fi scan. Format matches current
 * kernel {@code net/wireless/wext-proc.c}. {@code level} and {@code noise} are
 * the final signed /proc numbers; iw_statistics raw bytes and DBM conversion
 * are not simulated.
 */
public final class ConfiguredWirelessProcStatsFiles {

    /**
     * First header line from {@code wireless_dev_seq_show} (always ends with LF).
     */
    public static final String HEADER_LINE1 =
            "Inter-| sta-|   Quality        |   Discarded packets               | Missed | WE\n";

    /**
     * Second header line prefix; kernel appends {@code %d} {@code WIRELESS_EXT} then LF.
     */
    public static final String HEADER_LINE2_PREFIX =
            " face | tus | link level noise |  nwid  crypt   frag  retry   misc | beacon | ";

    /**
     * Kernel {@code wireless_seq_printf_stats} row. {@code level}/{@code noise}
     * are printed as configured signed integers; updated flags select {@code '.'}
     * or space after each quality column.
     */
    private static final String STATS_LINE_FORMAT =
            "%6s: %04x  %3d%c  %3d%c  %3d%c  %6d %6d %6d %6d %6d   %6d\n";

    private ConfiguredWirelessProcStatsFiles() {
    }

    /**
     * Two-line {@code /proc/net/wireless} header for the configured WE version.
     */
    public static String renderHeader(int wirelessExtensionsVersion) {
        return HEADER_LINE1 + HEADER_LINE2_PREFIX + wirelessExtensionsVersion + "\n";
    }

    /**
     * Render {@code /proc/net/wireless} (and self/pid aliases) from config.
     *
     * @return {@code null} when {@code network.wirelessProcStats} is not configured;
     *         header-only UTF-8 bytes when {@code entries} is {@code []};
     *         otherwise UTF-8 table ending with {@code \n} in JSON array order
     */
    public static byte[] renderProcNetWireless(TraceEnvironmentConfig config) {
        if (config == null || !config.isNetworkWirelessProcStatsConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.NetworkWirelessProcStatsConfig stats =
                config.getNetworkWirelessProcStats();
        if (stats == null) {
            return null;
        }
        List<TraceEnvironmentConfig.NetworkWirelessProcStatsEntryConfig> entries =
                stats.getEntries();
        String header = renderHeader(stats.getWirelessExtensionsVersion());
        StringBuilder sb = new StringBuilder(header.length() + entries.size() * 96);
        sb.append(header);
        for (TraceEnvironmentConfig.NetworkWirelessProcStatsEntryConfig row : entries) {
            sb.append(String.format(Locale.ROOT, STATS_LINE_FORMAT,
                    row.getInterfaceName(),
                    Integer.valueOf(row.getStatus()),
                    Integer.valueOf(row.getLinkQuality()),
                    Character.valueOf(row.isLinkUpdated() ? '.' : ' '),
                    Integer.valueOf(row.getLevel()),
                    Character.valueOf(row.isLevelUpdated() ? '.' : ' '),
                    Integer.valueOf(row.getNoise()),
                    Character.valueOf(row.isNoiseUpdated() ? '.' : ' '),
                    Long.valueOf(row.getDiscardNwid()),
                    Long.valueOf(row.getDiscardCrypt()),
                    Long.valueOf(row.getDiscardFragment()),
                    Long.valueOf(row.getDiscardRetries()),
                    Long.valueOf(row.getDiscardMisc()),
                    Long.valueOf(row.getMissedBeacon())));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
