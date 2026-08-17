package com.github.unidbg.file.linux;

import com.github.unidbg.env.TraceEnvironmentConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Pure static renderer for Linux {@code /proc/net/tcp} and {@code /proc/net/tcp6}
 * from {@code network.tcp} / {@code network.tcp6}. No host sockets, no sidecar.
 */
public final class ConfiguredTcpFiles {

    public static final String HEADER4 =
            "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode\n";
    public static final String HEADER6 =
            "  sl  local_address                         remote_address                        st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode\n";

    private ConfiguredTcpFiles() {
    }

    public static byte[] renderProcNetTcp(TraceEnvironmentConfig config) {
        if (config == null || !config.isNetworkTcpConfigured()) {
            return null;
        }
        return render(HEADER4, config.getNetworkTcp(), false);
    }

    public static byte[] renderProcNetTcp6(TraceEnvironmentConfig config) {
        if (config == null || !config.isNetworkTcp6Configured()) {
            return null;
        }
        return render(HEADER6, config.getNetworkTcp6(), true);
    }

    private static byte[] render(String header, List<TraceEnvironmentConfig.NetworkTcpConfig> rows,
                                 boolean ipv6) {
        StringBuilder sb = new StringBuilder(header.length() + rows.size() * 96);
        sb.append(header);
        for (int i = 0; i < rows.size(); i++) {
            TraceEnvironmentConfig.NetworkTcpConfig row = rows.get(i);
            sb.append(String.format(Locale.ROOT, "%4d: ", row.getSlot()));
            sb.append(formatAddress(row.getLocalAddress(), row.getLocalPort(), ipv6));
            sb.append(' ');
            sb.append(formatAddress(row.getRemoteAddress(), row.getRemotePort(), ipv6));
            sb.append(' ');
            sb.append(row.getStateHex());
            sb.append(' ');
            sb.append(String.format(Locale.ROOT, "%08X:%08X",
                    row.getTxQueue() & 0xffffffffL, row.getRxQueue() & 0xffffffffL));
            sb.append(" 00:00000000 00000000 ");
            sb.append(String.format(Locale.ROOT, "%5d %8d %d",
                    row.getUid(), row.getTimeout(), row.getInode()));
            sb.append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String formatAddress(String address, int port, boolean ipv6) {
        if (ipv6) {
            return address.toUpperCase(Locale.ROOT) + String.format(Locale.ROOT, ":%04X", port);
        }
        return toLinuxIpv4Hex(address) + String.format(Locale.ROOT, ":%04X", port);
    }

    public static String toLinuxIpv4Hex(String ipv4) {
        String[] parts = ipv4.split("\\.", -1);
        int a = Integer.parseInt(parts[0]);
        int b = Integer.parseInt(parts[1]);
        int c = Integer.parseInt(parts[2]);
        int d = Integer.parseInt(parts[3]);
        long le = (a & 0xffL) | ((b & 0xffL) << 8) | ((c & 0xffL) << 16) | ((d & 0xffL) << 24);
        return String.format(Locale.ROOT, "%08X", le);
    }
}
