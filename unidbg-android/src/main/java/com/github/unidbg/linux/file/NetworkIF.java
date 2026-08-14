package com.github.unidbg.linux.file;

import java.net.Inet4Address;

public class NetworkIF {

    public final int index;
    public final String ifName;
    public final Inet4Address ipv4;
    public final Inet4Address broadcast;
    /**
     * Negative means derive flags in SocketIO from loopback/broadcast;
     * non-negative is an explicit override (including 0).
     */
    public final int configuredFlags;
    /** Configured MAC (lowercase colon hex), or null. */
    public final String mac;
    /** Configured MTU, or null. */
    public final Integer mtu;

    public NetworkIF(int index, String ifName, Inet4Address ipv4) {
        this(index, ifName, ipv4, null);
    }

    public NetworkIF(int index, String ifName, Inet4Address ipv4, Inet4Address broadcast) {
        this(index, ifName, ipv4, broadcast, -1, true);
    }

    /**
     * @param remapHostNames when true, map host OS names (lo0/en0) like the historical host-enumeration path;
     *                       when false, keep the name exactly (for JSON network.interfaces).
     */
    public NetworkIF(int index, String ifName, Inet4Address ipv4, Inet4Address broadcast,
                     int configuredFlags, boolean remapHostNames) {
        this(index, ifName, ipv4, broadcast, configuredFlags, remapHostNames, null, null);
    }

    public NetworkIF(int index, String ifName, Inet4Address ipv4, Inet4Address broadcast,
                     int configuredFlags, boolean remapHostNames, String mac, Integer mtu) {
        this.index = index;
        this.ifName = remapHostNames ? remapHostIfName(ifName) : ifName;
        this.ipv4 = ipv4;
        this.broadcast = broadcast;
        this.configuredFlags = configuredFlags;
        this.mac = mac;
        this.mtu = mtu;
    }

    private static String remapHostIfName(String ifName) {
        if ("lo0".equals(ifName)) {
            return "lo";
        }
        if ("en0".equals(ifName)) {
            return "wlan0";
        }
        return ifName;
    }

    public boolean isLoopback() {
        return ifName != null && ifName.startsWith("lo");
    }

    @Override
    public String toString() {
        if (ipv4 == null) {
            return ifName;
        }
        return ifName + "(" + ipv4.getHostAddress() + ")";
    }
}
