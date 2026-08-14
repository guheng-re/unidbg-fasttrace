package com.github.unidbg.file.linux;

import com.github.unidbg.env.TraceEnvironmentConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Pure static renderer for Linux {@code /proc/net/if_inet6} text from {@code network.ipv6Addresses}.
 * No Emulator/FileIO access, no sidecar events, and no host network reads.
 * Interface index is reused from the matching {@code network.interfaces[].index};
 * address, prefix, scope, and flags are never inferred from other fields.
 */
public final class ConfiguredIpv6AddressFiles {

    private ConfiguredIpv6AddressFiles() {
    }

    /**
     * Render {@code /proc/net/if_inet6} (and self/pid aliases) from config.
     *
     * @return {@code null} when {@code network.ipv6Addresses} is not configured;
     *         zero-length bytes when configured as {@code []} (no header);
     *         otherwise UTF-8 lines ending with {@code \n} in JSON array order
     */
    public static byte[] renderProcNetIfInet6(TraceEnvironmentConfig config) {
        if (config == null || !config.isNetworkIpv6AddressesConfigured()) {
            return null;
        }
        List<TraceEnvironmentConfig.NetworkIpv6AddressConfig> addresses =
                config.getNetworkIpv6Addresses();
        if (addresses.isEmpty()) {
            return new byte[0];
        }
        StringBuilder sb = new StringBuilder(addresses.size() * 64);
        for (TraceEnvironmentConfig.NetworkIpv6AddressConfig addr : addresses) {
            int index = findInterfaceIndex(config, addr.getInterfaceName());
            sb.append(addr.getAddressHex());
            sb.append(' ');
            sb.append(toLowerHex2(index));
            sb.append(' ');
            sb.append(toLowerHex2(addr.getPrefixLength()));
            sb.append(' ');
            sb.append(toLowerHex2(addr.getScope()));
            sb.append(' ');
            sb.append(toLowerHex2(addr.getFlags()));
            sb.append(' ');
            sb.append(addr.getInterfaceName());
            sb.append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Linux {@code /proc/net/if_inet6} two-digit lowercase hex ({@code %02x}).
     * Prefix/scope/flags are {@code 0..255}. Referenced interface index is
     * constrained at parse time to {@code 0..255} and reused as two-digit hex.
     */
    static String toLowerHex2(int value) {
        return String.format(Locale.ROOT, "%02x", value);
    }

    private static int findInterfaceIndex(TraceEnvironmentConfig config, String interfaceName) {
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            if (interfaceName.equals(iface.getName())) {
                return iface.getIndex();
            }
        }
        throw new IllegalStateException(
                "network.ipv6Addresses interfaceName was not resolved: " + interfaceName);
    }
}
