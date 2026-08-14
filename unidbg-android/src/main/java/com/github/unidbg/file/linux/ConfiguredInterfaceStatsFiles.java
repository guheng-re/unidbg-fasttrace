package com.github.unidbg.file.linux;

import com.github.unidbg.env.TraceEnvironmentConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Pure static renderer for Linux {@code /proc/net/dev} text and exact
 * {@code /sys/class/net/<name>/statistics/<field>} files from
 * {@code network.interfaceStats}. No Emulator/FileIO access, no sidecar events,
 * and no host network or sysfs reads. Counters are never inferred from
 * interfaces, routes, wifi, or other fields.
 */
public final class ConfiguredInterfaceStatsFiles {

    /**
     * Stable Linux {@code /proc/net/dev} two-line header ({@code net/core/net-procfs.c}
     * {@code dev_seq_show} token lines). Each line ends with LF.
     */
    public static final String HEADER =
            "Inter-|   Receive                                                |  Transmit\n"
                    + " face |bytes    packets errs drop fifo frame compressed multicast|"
                    + "bytes    packets errs drop fifo colls carrier compressed\n";

    /**
     * Kernel {@code dev_seq_printf_stats} row: right-aligned 6-char name, colon, then
     * sixteen unsigned decimal counters (8 RX then 8 TX) separated by spaces.
     */
    private static final String STATS_LINE_FORMAT =
            "%6s: %7d %7d %4d %4d %4d %5d %10d %9d %8d %7d %4d %4d %4d %5d %7d %10d\n";

    /**
     * Kernel sysfs statistic filenames served from the existing sixteen
     * {@code network.interfaceStats[]} counters. Extra kernel files such as
     * {@code rx_crc_errors} are intentionally absent.
     */
    public static final String[] SYSFS_STATISTIC_FIELDS = {
            "rx_bytes", "rx_packets", "rx_errors", "rx_dropped", "rx_fifo_errors",
            "rx_frame_errors", "rx_compressed", "multicast",
            "tx_bytes", "tx_packets", "tx_errors", "tx_dropped", "tx_fifo_errors",
            "tx_carrier_errors", "tx_compressed", "collisions"
    };

    private ConfiguredInterfaceStatsFiles() {
    }

    /**
     * Render {@code /proc/net/dev} (and self/pid aliases) from config.
     *
     * @return {@code null} when {@code network.interfaceStats} is not configured;
     *         header-only UTF-8 bytes when configured as {@code []};
     *         otherwise UTF-8 table ending with {@code \n}
     */
    public static byte[] renderProcNetDev(TraceEnvironmentConfig config) {
        if (config == null || !config.isNetworkInterfaceStatsConfigured()) {
            return null;
        }
        List<TraceEnvironmentConfig.NetworkInterfaceStatsConfig> stats =
                config.getNetworkInterfaceStats();
        StringBuilder sb = new StringBuilder(HEADER.length() + stats.size() * 96);
        sb.append(HEADER);
        for (TraceEnvironmentConfig.NetworkInterfaceStatsConfig row : stats) {
            sb.append(String.format(Locale.ROOT, STATS_LINE_FORMAT,
                    row.getInterfaceName(),
                    row.getRxBytes(),
                    row.getRxPackets(),
                    row.getRxErrors(),
                    row.getRxDrop(),
                    row.getRxFifo(),
                    row.getRxFrame(),
                    row.getRxCompressed(),
                    row.getRxMulticast(),
                    row.getTxBytes(),
                    row.getTxPackets(),
                    row.getTxErrors(),
                    row.getTxDrop(),
                    row.getTxFifo(),
                    row.getTxCollisions(),
                    row.getTxCarrier(),
                    row.getTxCompressed()));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Render one sysfs statistic file: decimal ASCII of the mapped non-negative
     * long plus a single LF. Returns {@code null} when {@code kernelField} is not
     * one of {@link #SYSFS_STATISTIC_FIELDS}. Never infers values from other
     * network config.
     */
    public static byte[] renderSysfsStatistic(
            TraceEnvironmentConfig.NetworkInterfaceStatsConfig row, String kernelField) {
        if (row == null || kernelField == null) {
            return null;
        }
        Long value = sysfsStatisticValue(row, kernelField);
        if (value == null) {
            return null;
        }
        return (Long.toString(value.longValue()) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Map a kernel sysfs statistic filename onto the matching JSON counter.
     *
     * @return {@code null} when {@code kernelField} is not a supported filename
     */
    public static Long sysfsStatisticValue(
            TraceEnvironmentConfig.NetworkInterfaceStatsConfig row, String kernelField) {
        if (row == null || kernelField == null) {
            return null;
        }
        if ("rx_bytes".equals(kernelField)) {
            return Long.valueOf(row.getRxBytes());
        }
        if ("rx_packets".equals(kernelField)) {
            return Long.valueOf(row.getRxPackets());
        }
        if ("rx_errors".equals(kernelField)) {
            return Long.valueOf(row.getRxErrors());
        }
        if ("rx_dropped".equals(kernelField)) {
            return Long.valueOf(row.getRxDrop());
        }
        if ("rx_fifo_errors".equals(kernelField)) {
            return Long.valueOf(row.getRxFifo());
        }
        if ("rx_frame_errors".equals(kernelField)) {
            return Long.valueOf(row.getRxFrame());
        }
        if ("rx_compressed".equals(kernelField)) {
            return Long.valueOf(row.getRxCompressed());
        }
        if ("multicast".equals(kernelField)) {
            return Long.valueOf(row.getRxMulticast());
        }
        if ("tx_bytes".equals(kernelField)) {
            return Long.valueOf(row.getTxBytes());
        }
        if ("tx_packets".equals(kernelField)) {
            return Long.valueOf(row.getTxPackets());
        }
        if ("tx_errors".equals(kernelField)) {
            return Long.valueOf(row.getTxErrors());
        }
        if ("tx_dropped".equals(kernelField)) {
            return Long.valueOf(row.getTxDrop());
        }
        if ("tx_fifo_errors".equals(kernelField)) {
            return Long.valueOf(row.getTxFifo());
        }
        if ("tx_carrier_errors".equals(kernelField)) {
            return Long.valueOf(row.getTxCarrier());
        }
        if ("tx_compressed".equals(kernelField)) {
            return Long.valueOf(row.getTxCompressed());
        }
        if ("collisions".equals(kernelField)) {
            return Long.valueOf(row.getTxCollisions());
        }
        return null;
    }
}
