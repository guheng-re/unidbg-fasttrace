package com.github.unidbg.file.linux;

import com.github.unidbg.env.TraceEnvironmentConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Pure static renderer for Linux {@code /proc/net/route} text from {@code network.ipv4Routes}.
 * No Emulator/FileIO access, no sidecar events, and no host network reads.
 */
public final class ConfiguredIpv4RouteFiles {

    /**
     * Stable Linux {@code /proc/net/route} header (kernel {@code fib_route_seq_show} token line
     * without seq_file width padding): {@code Gateway} has a trailing space; {@code Mask} is
     * followed by two tabs.
     */
    public static final String HEADER =
            "Iface\tDestination\tGateway \tFlags\tRefCnt\tUse\tMetric\tMask\t\tMTU\tWindow\tIRTT\n";

    private ConfiguredIpv4RouteFiles() {
    }

    /**
     * Render {@code /proc/net/route} (and self/pid aliases) from config.
     *
     * @return {@code null} when {@code network.ipv4Routes} is not configured;
     *         header-only UTF-8 bytes when configured as {@code []};
     *         otherwise UTF-8 table ending with {@code \n}
     */
    public static byte[] renderProcNetRoute(TraceEnvironmentConfig config) {
        if (config == null || !config.isNetworkIpv4RoutesConfigured()) {
            return null;
        }
        List<TraceEnvironmentConfig.NetworkIpv4RouteConfig> routes = config.getNetworkIpv4Routes();
        StringBuilder sb = new StringBuilder(HEADER.length() + routes.size() * 64);
        sb.append(HEADER);
        for (TraceEnvironmentConfig.NetworkIpv4RouteConfig route : routes) {
            sb.append(route.getInterfaceName());
            sb.append('\t');
            sb.append(toLinuxRouteHex(route.getDestination()));
            sb.append('\t');
            sb.append(toLinuxRouteHex(route.getGateway()));
            sb.append('\t');
            sb.append(toLinuxRouteFlagsHex(route.getFlags()));
            sb.append('\t');
            sb.append(Long.toString(route.getRefCount()));
            sb.append('\t');
            sb.append(Long.toString(route.getUse()));
            sb.append('\t');
            sb.append(Long.toString(route.getMetric()));
            sb.append('\t');
            sb.append(toLinuxRouteHex(route.getMask()));
            sb.append('\t');
            sb.append(Long.toString(route.getMtu()));
            sb.append('\t');
            sb.append(Long.toString(route.getWindow()));
            sb.append('\t');
            sb.append(Long.toString(route.getIrtt()));
            sb.append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Linux route table IPv4: little-endian {@code %08X} of the four octets
     * {@code a.b.c.d} as {@code a|(b<<8)|(c<<16)|(d<<24)}.
     */
    static String toLinuxRouteHex(String ipv4) {
        String[] parts = ipv4.split("\\.", -1);
        int a = Integer.parseInt(parts[0]);
        int b = Integer.parseInt(parts[1]);
        int c = Integer.parseInt(parts[2]);
        int d = Integer.parseInt(parts[3]);
        long le = (a & 0xffL) | ((b & 0xffL) << 8) | ((c & 0xffL) << 16) | ((d & 0xffL) << 24);
        return String.format(Locale.ROOT, "%08X", le);
    }

    /** Linux route {@code Flags} column: {@code %04X} of the unsigned 32-bit value. */
    static String toLinuxRouteFlagsHex(long flags) {
        return String.format(Locale.ROOT, "%04X", flags);
    }
}
