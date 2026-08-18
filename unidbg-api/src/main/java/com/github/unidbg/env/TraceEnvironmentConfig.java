package com.github.unidbg.env;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.github.unidbg.Emulator;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IllformedLocaleException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Pattern;

public class TraceEnvironmentConfig {

    public static final String KEY = TraceEnvironmentConfig.class.getName();
    public static final String SYSTEM_PROPERTY = "unidbg.env.config";

    /**
     * Immutable view of one {@code network.interfaces[]} entry after parse validation.
     */
    public static final class NetworkInterfaceConfig {
        private final String name;
        private final int index;
        private final String ipv4;
        private final String broadcast;
        private final Integer flags;
        private final String mac;
        private final Integer mtu;
        private final String displayName;
        private final boolean displayNameConfigured;
        private final boolean virtual;
        private final boolean virtualConfigured;
        private final String operState;
        private final boolean operStateConfigured;
        private final boolean carrier;
        private final boolean carrierConfigured;
        private final Integer hardwareType;
        private final boolean hardwareTypeConfigured;
        private final Integer speedMbps;
        private final boolean speedMbpsConfigured;
        private final String duplex;
        private final boolean duplexConfigured;
        private final Integer linkIndex;
        private final boolean linkIndexConfigured;
        private final Integer txQueueLen;
        private final boolean txQueueLenConfigured;
        private final Integer addressAssignType;
        private final boolean addressAssignTypeConfigured;
        private final Integer nameAssignType;
        private final boolean nameAssignTypeConfigured;
        private final String linkLayerBroadcast;
        private final boolean linkLayerBroadcastConfigured;

        private NetworkInterfaceConfig(String name, int index, String ipv4, String broadcast, Integer flags,
                                       String mac, Integer mtu, String displayName,
                                       boolean displayNameConfigured,
                                       boolean virtual, boolean virtualConfigured,
                                       String operState, boolean operStateConfigured,
                                       boolean carrier, boolean carrierConfigured,
                                       Integer hardwareType, boolean hardwareTypeConfigured,
                                       Integer speedMbps, boolean speedMbpsConfigured,
                                       String duplex, boolean duplexConfigured,
                                       Integer linkIndex, boolean linkIndexConfigured,
                                       Integer txQueueLen, boolean txQueueLenConfigured,
                                       Integer addressAssignType, boolean addressAssignTypeConfigured,
                                       Integer nameAssignType, boolean nameAssignTypeConfigured,
                                       String linkLayerBroadcast, boolean linkLayerBroadcastConfigured) {
            this.name = name;
            this.index = index;
            this.ipv4 = ipv4;
            this.broadcast = broadcast;
            this.flags = flags;
            this.mac = mac;
            this.mtu = mtu;
            this.displayName = displayName;
            this.displayNameConfigured = displayNameConfigured;
            this.virtual = virtual;
            this.virtualConfigured = virtualConfigured;
            this.operState = operState;
            this.operStateConfigured = operStateConfigured;
            this.carrier = carrier;
            this.carrierConfigured = carrierConfigured;
            this.hardwareType = hardwareType;
            this.hardwareTypeConfigured = hardwareTypeConfigured;
            this.speedMbps = speedMbps;
            this.speedMbpsConfigured = speedMbpsConfigured;
            this.duplex = duplex;
            this.duplexConfigured = duplexConfigured;
            this.linkIndex = linkIndex;
            this.linkIndexConfigured = linkIndexConfigured;
            this.txQueueLen = txQueueLen;
            this.txQueueLenConfigured = txQueueLenConfigured;
            this.addressAssignType = addressAssignType;
            this.addressAssignTypeConfigured = addressAssignTypeConfigured;
            this.nameAssignType = nameAssignType;
            this.nameAssignTypeConfigured = nameAssignTypeConfigured;
            this.linkLayerBroadcast = linkLayerBroadcast;
            this.linkLayerBroadcastConfigured = linkLayerBroadcastConfigured;
        }

        public String getName() {
            return name;
        }

        public int getIndex() {
            return index;
        }

        public String getIpv4() {
            return ipv4;
        }

        public String getBroadcast() {
            return broadcast;
        }

        public Integer getFlags() {
            return flags;
        }

        public String getMac() {
            return mac;
        }

        public Integer getMtu() {
            return mtu;
        }

        /**
         * Whether {@code displayName} was present in JSON (including explicit {@code null}).
         * Omitted key is false so JNI may keep UOE; never inferred from {@link #getName()}.
         */
        public boolean isDisplayNameConfigured() {
            return displayNameConfigured;
        }

        /**
         * Configured display name, or {@code null} when omitted or explicit JSON null.
         * Callers must use {@link #isDisplayNameConfigured()} to distinguish omit vs null.
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Whether {@code virtual} was present in JSON. Omitted key is false so JNI may keep UOE;
         * never inferred from name, flags, address, index, or other fields.
         */
        public boolean isVirtualConfigured() {
            return virtualConfigured;
        }

        /**
         * Configured virtual-interface marker for {@code NetworkInterface.isVirtual()}.
         * Meaningful only when {@link #isVirtualConfigured()} is true; never inferred.
         */
        public boolean isVirtual() {
            return virtual;
        }

        /**
         * Whether {@code operState} was present in JSON. Omitted key is false so sysfs
         * {@code /operstate} is not served; never inferred from flags, wifi, or other fields.
         */
        public boolean isOperStateConfigured() {
            return operStateConfigured;
        }

        /**
         * Configured sysfs operstate string, or {@code null} when omitted.
         * Meaningful only when {@link #isOperStateConfigured()} is true; never inferred
         * from {@link #getFlags()}, wifi, or other fields.
         */
        public String getOperState() {
            return operState;
        }

        /**
         * Whether {@code carrier} was present in JSON. Omitted key is false so sysfs
         * {@code /carrier} is not served; never inferred from operState, flags, wifi,
         * or other fields.
         */
        public boolean isCarrierConfigured() {
            return carrierConfigured;
        }

        /**
         * Configured sysfs carrier marker for {@code /sys/class/net/<name>/carrier}.
         * Meaningful only when {@link #isCarrierConfigured()} is true; never inferred
         * from {@link #getOperState()}, {@link #getFlags()}, wifi, or other fields.
         */
        public boolean isCarrier() {
            return carrier;
        }

        /**
         * Whether {@code hardwareType} was present in JSON. Omitted key is false so sysfs
         * {@code /type} is not served; never inferred from name, flags, MAC, operState,
         * carrier, or other fields.
         */
        public boolean isHardwareTypeConfigured() {
            return hardwareTypeConfigured;
        }

        /**
         * Configured sysfs ARPHRD type for {@code /sys/class/net/<name>/type}, or {@code null}
         * when omitted. Meaningful only when {@link #isHardwareTypeConfigured()} is true;
         * never inferred from {@link #getName()}, {@link #getFlags()}, {@link #getMac()},
         * {@link #getOperState()}, {@link #isCarrier()}, or other fields.
         */
        public Integer getHardwareType() {
            return hardwareType;
        }

        /**
         * Whether {@code speedMbps} was present in JSON. Omitted key is false so sysfs
         * {@code /speed} is not served; never inferred from {@code network.wifi.linkSpeedMbps},
         * name, flags, MAC, hardwareType, operState, carrier, or other fields.
         */
        public boolean isSpeedMbpsConfigured() {
            return speedMbpsConfigured;
        }

        /**
         * Configured sysfs link speed in Mbps for {@code /sys/class/net/<name>/speed}, or
         * {@code null} when omitted. {@code -1} means unknown (Linux {@code SPEED_UNKNOWN}).
         * Meaningful only when {@link #isSpeedMbpsConfigured()} is true; never inferred from
         * wifi {@code linkSpeedMbps}, {@link #getName()}, {@link #getFlags()}, {@link #getMac()},
         * {@link #getHardwareType()}, {@link #getOperState()}, {@link #isCarrier()}, or other
         * fields. Distinguishes omitted key from explicit {@code -1}/{@code 0}/positive.
         */
        public Integer getSpeedMbps() {
            return speedMbps;
        }

        /**
         * Whether {@code duplex} was present in JSON. Omitted key is false so sysfs
         * {@code /duplex} is not served; never inferred from {@code speedMbps}, carrier,
         * operState, hardwareType, flags, name, {@code network.wifi}, or other fields.
         */
        public boolean isDuplexConfigured() {
            return duplexConfigured;
        }

        /**
         * Configured sysfs duplex string for {@code /sys/class/net/<name>/duplex}, or
         * {@code null} when omitted. Meaningful only when {@link #isDuplexConfigured()} is
         * true; never inferred from {@link #getSpeedMbps()}, {@link #isCarrier()},
         * {@link #getOperState()}, {@link #getHardwareType()}, {@link #getFlags()},
         * {@link #getName()}, wifi, or other fields. Distinguishes omitted key from
         * explicit {@code full}/{@code half}/{@code unknown}.
         */
        public String getDuplex() {
            return duplex;
        }

        /**
         * Whether {@code linkIndex} was present in JSON. Omitted key is false so sysfs
         * {@code /iflink} is not served; never inferred from {@link #getIndex()}, name,
         * hardwareType, speedMbps, duplex, flags, carrier, operState, MAC, wifi, or
         * other fields.
         */
        public boolean isLinkIndexConfigured() {
            return linkIndexConfigured;
        }

        /**
         * Configured sysfs iflink for {@code /sys/class/net/<name>/iflink}, or
         * {@code null} when omitted. Meaningful only when {@link #isLinkIndexConfigured()}
         * is true; never inferred from {@link #getIndex()}, {@link #getName()},
         * {@link #getHardwareType()}, {@link #getSpeedMbps()}, {@link #getDuplex()},
         * {@link #getFlags()}, {@link #isCarrier()}, {@link #getOperState()},
         * {@link #getMac()}, wifi, or other fields. Distinguishes omitted key from
         * explicit values in {@code 1..Integer.MAX_VALUE}. Independent of required
         * {@code index}; the two are never auto-equalized.
         */
        public Integer getLinkIndex() {
            return linkIndex;
        }

        /**
         * Whether {@code txQueueLen} was present in JSON. Omitted key is false so sysfs
         * {@code /tx_queue_len} is not served; never inferred from {@code mtu},
         * {@code speedMbps}, {@code duplex}, {@code hardwareType}, {@code linkIndex},
         * {@link #getIndex()}, {@link #getFlags()}, {@link #isCarrier()},
         * {@link #getOperState()}, {@link #getName()}, wifi, or other fields.
         */
        public boolean isTxQueueLenConfigured() {
            return txQueueLenConfigured;
        }

        /**
         * Configured sysfs transmit queue length for
         * {@code /sys/class/net/<name>/tx_queue_len}, or {@code null} when omitted.
         * Meaningful only when {@link #isTxQueueLenConfigured()} is true; never inferred
         * from {@link #getMtu()}, {@link #getSpeedMbps()}, {@link #getDuplex()},
         * {@link #getHardwareType()}, {@link #getLinkIndex()}, {@link #getIndex()},
         * {@link #getFlags()}, {@link #isCarrier()}, {@link #getOperState()},
         * {@link #getName()}, wifi, or other fields. Distinguishes omitted key from
         * explicit {@code 0} and other values in {@code 0..Integer.MAX_VALUE}.
         * Independent of {@code mtu} and {@code speedMbps}.
         */
        public Integer getTxQueueLen() {
            return txQueueLen;
        }

        /**
         * Whether {@code addressAssignType} was present in JSON. Omitted key is false so sysfs
         * {@code /addr_assign_type} is not served; never inferred from {@link #getMac()},
         * {@link #getName()}, {@link #getHardwareType()}, {@link #getFlags()},
         * {@link #isCarrier()}, {@link #getOperState()}, {@link #getSpeedMbps()},
         * {@link #getDuplex()}, {@link #getLinkIndex()}, {@link #getTxQueueLen()}, wifi,
         * or other fields, and never from how the MAC was generated.
         */
        public boolean isAddressAssignTypeConfigured() {
            return addressAssignTypeConfigured;
        }

        /**
         * Configured sysfs address assignment type for
         * {@code /sys/class/net/<name>/addr_assign_type}, or {@code null} when omitted.
         * Meaningful only when {@link #isAddressAssignTypeConfigured()} is true; never
         * inferred from {@link #getMac()}, {@link #getName()}, {@link #getHardwareType()},
         * {@link #getFlags()}, {@link #isCarrier()}, {@link #getOperState()},
         * {@link #getSpeedMbps()}, {@link #getDuplex()}, {@link #getLinkIndex()},
         * {@link #getTxQueueLen()}, wifi, or other fields, and never from MAC generation
         * style. Distinguishes omitted key from explicit {@code 0}. Linux
         * {@code NET_ADDR_*}: {@code 0} permanent, {@code 1} random, {@code 2} stolen,
         * {@code 3} set. Independent of {@code mac}.
         */
        public Integer getAddressAssignType() {
            return addressAssignType;
        }

        /**
         * Whether {@code nameAssignType} was present in JSON. Omitted key is false so sysfs
         * {@code /name_assign_type} is not served; never inferred from {@link #getName()},
         * {@link #getMac()}, {@link #getAddressAssignType()}, {@link #getHardwareType()},
         * {@link #getFlags()}, {@link #isCarrier()}, {@link #getOperState()},
         * {@link #getSpeedMbps()}, {@link #getDuplex()}, {@link #getLinkIndex()},
         * {@link #getTxQueueLen()}, wifi, or other fields.
         */
        public boolean isNameAssignTypeConfigured() {
            return nameAssignTypeConfigured;
        }

        /**
         * Configured sysfs name assignment type for
         * {@code /sys/class/net/<name>/name_assign_type}, or {@code null} when omitted.
         * Meaningful only when {@link #isNameAssignTypeConfigured()} is true; never
         * inferred from {@link #getName()}, {@link #getMac()},
         * {@link #getAddressAssignType()}, {@link #getHardwareType()},
         * {@link #getFlags()}, {@link #isCarrier()}, {@link #getOperState()},
         * {@link #getSpeedMbps()}, {@link #getDuplex()}, {@link #getLinkIndex()},
         * {@link #getTxQueueLen()}, wifi, or other fields. Distinguishes omitted key
         * from explicit {@code 0}. Linux {@code NET_NAME_*}: {@code 0} unknown,
         * {@code 1} enum, {@code 2} predictable, {@code 3} user, {@code 4} renamed.
         */
        public Integer getNameAssignType() {
            return nameAssignType;
        }

        /**
         * Whether {@code linkLayerBroadcast} was present in JSON. Omitted key is false so
         * sysfs {@code /broadcast} is not served; never inferred from {@link #getMac()},
         * IPv4 {@link #getBroadcast()}, {@link #getHardwareType()}, {@link #getFlags()},
         * or other interface fields.
         */
        public boolean isLinkLayerBroadcastConfigured() {
            return linkLayerBroadcastConfigured;
        }

        /**
         * Configured link-layer broadcast MAC for {@code /sys/class/net/<name>/broadcast},
         * or {@code null} when omitted. Stored as Locale.ROOT lowercase colon form, same
         * rules as {@link #getMac()}. Meaningful only when
         * {@link #isLinkLayerBroadcastConfigured()} is true; never inferred from
         * {@link #getMac()}, IPv4 {@link #getBroadcast()}, {@link #getHardwareType()},
         * {@link #getFlags()}, or other interface fields. Independent of those fields;
         * no consistency checks.
         */
        public String getLinkLayerBroadcast() {
            return linkLayerBroadcast;
        }
    }

    /**
     * Immutable view of one {@code network.ipv4Routes[]} entry after parse validation.
     * All eleven whitelist fields are required. Values are never inferred from
     * {@code network.interfaces}, {@code network.wifi}, or other fields.
     * Never exposes JSONObject/JSONArray.
     */
    public static final class NetworkIpv4RouteConfig {
        private final String interfaceName;
        private final String destination;
        private final String gateway;
        private final long flags;
        private final long refCount;
        private final long use;
        private final long metric;
        private final String mask;
        private final long mtu;
        private final long window;
        private final long irtt;

        private NetworkIpv4RouteConfig(String interfaceName, String destination, String gateway,
                                       long flags, long refCount, long use, long metric,
                                       String mask, long mtu, long window, long irtt) {
            this.interfaceName = interfaceName;
            this.destination = destination;
            this.gateway = gateway;
            this.flags = flags;
            this.refCount = refCount;
            this.use = use;
            this.metric = metric;
            this.mask = mask;
            this.mtu = mtu;
            this.window = window;
            this.irtt = irtt;
        }

        /** Configured interface name; must match a {@code network.interfaces[]} {@code name}. */
        public String getInterfaceName() {
            return interfaceName;
        }

        /** Validated IPv4 destination (dotted decimal). */
        public String getDestination() {
            return destination;
        }

        /** Validated IPv4 gateway (dotted decimal). */
        public String getGateway() {
            return gateway;
        }

        /** Route flags as unsigned 32-bit ({@code 0..4294967295}). */
        public long getFlags() {
            return flags;
        }

        /** Reference count as unsigned 32-bit ({@code 0..4294967295}). */
        public long getRefCount() {
            return refCount;
        }

        /** Use count as unsigned 32-bit ({@code 0..4294967295}). */
        public long getUse() {
            return use;
        }

        /** Metric as unsigned 32-bit ({@code 0..4294967295}). */
        public long getMetric() {
            return metric;
        }

        /** Validated IPv4 mask (dotted decimal). */
        public String getMask() {
            return mask;
        }

        /** MTU as unsigned 32-bit ({@code 0..4294967295}). */
        public long getMtu() {
            return mtu;
        }

        /** Window as unsigned 32-bit ({@code 0..4294967295}). */
        public long getWindow() {
            return window;
        }

        /** Initial RTT as unsigned 32-bit ({@code 0..4294967295}). */
        public long getIrtt() {
            return irtt;
        }
    }

    /**
     * Immutable view of one {@code network.interfaceStats[]} entry after parse validation.
     * All seventeen whitelist fields are required. Counters are never inferred from
     * {@code network.interfaces}, {@code network.ipv4Routes}, {@code network.wifi}, or other fields.
     * Never exposes JSONObject/JSONArray.
     */
    public static final class NetworkInterfaceStatsConfig {
        private final String interfaceName;
        private final long rxBytes;
        private final long rxPackets;
        private final long rxErrors;
        private final long rxDrop;
        private final long rxFifo;
        private final long rxFrame;
        private final long rxCompressed;
        private final long rxMulticast;
        private final long txBytes;
        private final long txPackets;
        private final long txErrors;
        private final long txDrop;
        private final long txFifo;
        private final long txCollisions;
        private final long txCarrier;
        private final long txCompressed;

        private NetworkInterfaceStatsConfig(String interfaceName,
                                            long rxBytes, long rxPackets, long rxErrors, long rxDrop,
                                            long rxFifo, long rxFrame, long rxCompressed, long rxMulticast,
                                            long txBytes, long txPackets, long txErrors, long txDrop,
                                            long txFifo, long txCollisions, long txCarrier, long txCompressed) {
            this.interfaceName = interfaceName;
            this.rxBytes = rxBytes;
            this.rxPackets = rxPackets;
            this.rxErrors = rxErrors;
            this.rxDrop = rxDrop;
            this.rxFifo = rxFifo;
            this.rxFrame = rxFrame;
            this.rxCompressed = rxCompressed;
            this.rxMulticast = rxMulticast;
            this.txBytes = txBytes;
            this.txPackets = txPackets;
            this.txErrors = txErrors;
            this.txDrop = txDrop;
            this.txFifo = txFifo;
            this.txCollisions = txCollisions;
            this.txCarrier = txCarrier;
            this.txCompressed = txCompressed;
        }

        /** Configured interface name; must match a {@code network.interfaces[]} {@code name}. */
        public String getInterfaceName() {
            return interfaceName;
        }

        /** RX bytes as unsigned long ({@code 0..Long.MAX_VALUE}). */
        public long getRxBytes() {
            return rxBytes;
        }

        /** RX packets as unsigned long ({@code 0..Long.MAX_VALUE}). */
        public long getRxPackets() {
            return rxPackets;
        }

        /** RX errors as unsigned long ({@code 0..Long.MAX_VALUE}). */
        public long getRxErrors() {
            return rxErrors;
        }

        /** RX dropped as unsigned long ({@code 0..Long.MAX_VALUE}). */
        public long getRxDrop() {
            return rxDrop;
        }

        /** RX fifo errors as unsigned long ({@code 0..Long.MAX_VALUE}). */
        public long getRxFifo() {
            return rxFifo;
        }

        /** RX frame errors as unsigned long ({@code 0..Long.MAX_VALUE}). */
        public long getRxFrame() {
            return rxFrame;
        }

        /** RX compressed as unsigned long ({@code 0..Long.MAX_VALUE}). */
        public long getRxCompressed() {
            return rxCompressed;
        }

        /** RX multicast as unsigned long ({@code 0..Long.MAX_VALUE}). */
        public long getRxMulticast() {
            return rxMulticast;
        }

        /** TX bytes as unsigned long ({@code 0..Long.MAX_VALUE}). */
        public long getTxBytes() {
            return txBytes;
        }

        /** TX packets as unsigned long ({@code 0..Long.MAX_VALUE}). */
        public long getTxPackets() {
            return txPackets;
        }

        /** TX errors as unsigned long ({@code 0..Long.MAX_VALUE}). */
        public long getTxErrors() {
            return txErrors;
        }

        /** TX dropped as unsigned long ({@code 0..Long.MAX_VALUE}). */
        public long getTxDrop() {
            return txDrop;
        }

        /** TX fifo errors as unsigned long ({@code 0..Long.MAX_VALUE}). */
        public long getTxFifo() {
            return txFifo;
        }

        /** TX collisions as unsigned long ({@code 0..Long.MAX_VALUE}). */
        public long getTxCollisions() {
            return txCollisions;
        }

        /** TX carrier errors as unsigned long ({@code 0..Long.MAX_VALUE}). */
        public long getTxCarrier() {
            return txCarrier;
        }

        /** TX compressed as unsigned long ({@code 0..Long.MAX_VALUE}). */
        public long getTxCompressed() {
            return txCompressed;
        }
    }

    /**
     * Immutable view of one {@code network.ipv6Addresses[]} entry after parse validation.
     * All five whitelist fields are required. Address, prefix, scope, and flags are never
     * inferred from {@code network.interfaces}, {@code network.ipv4Routes},
     * {@code network.interfaceStats}, {@code network.wifi}, or other fields.
     * Interface index for {@code /proc/net/if_inet6} is reused from the matching
     * {@code network.interfaces[].index} at render time; a referenced interface
     * index must be {@code 0..255} so the file stays six columns of two-digit hex.
     * Ordinary {@code network.interfaces[].index} is not tightened when this node
     * is absent or the interface is not referenced. Never exposes JSONObject/JSONArray.
     */
    public static final class NetworkIpv6AddressConfig {
        private final String interfaceName;
        private final String addressHex;
        private final int prefixLength;
        private final int scope;
        private final int flags;

        private NetworkIpv6AddressConfig(String interfaceName, String addressHex,
                                         int prefixLength, int scope, int flags) {
            this.interfaceName = interfaceName;
            this.addressHex = addressHex;
            this.prefixLength = prefixLength;
            this.scope = scope;
            this.flags = flags;
        }

        /** Configured interface name; must match a {@code network.interfaces[]} {@code name}. */
        public String getInterfaceName() {
            return interfaceName;
        }

        /**
         * Normalized lowercase 32-character ASCII hex IPv6 address (no colons).
         * Never inferred from dotted IPv4, DNS, or compressed IPv6 text.
         */
        public String getAddressHex() {
            return addressHex;
        }

        /** Prefix length as exact JSON integer {@code 0..128}. */
        public int getPrefixLength() {
            return prefixLength;
        }

        /** IPv6 scope as exact JSON integer {@code 0..255}. */
        public int getScope() {
            return scope;
        }

        /** IPv6 interface-address flags as exact JSON integer {@code 0..255}. */
        public int getFlags() {
            return flags;
        }
    }

    /**
     * One {@code network.tcp[]} / {@code network.tcp6[]} row after parse validation.
     * Addresses are never inferred from {@code network.interfaces} or routes.
     */
    public static final class NetworkTcpConfig {
        private final int slot;
        private final String localAddress;
        private final int localPort;
        private final String remoteAddress;
        private final int remotePort;
        private final String stateHex;
        private final long txQueue;
        private final long rxQueue;
        private final int uid;
        private final int timeout;
        private final long inode;
        private final boolean ipv6;

        private NetworkTcpConfig(int slot, String localAddress, int localPort,
                                 String remoteAddress, int remotePort, String stateHex,
                                 long txQueue, long rxQueue, int uid, int timeout,
                                 long inode, boolean ipv6) {
            this.slot = slot;
            this.localAddress = localAddress;
            this.localPort = localPort;
            this.remoteAddress = remoteAddress;
            this.remotePort = remotePort;
            this.stateHex = stateHex;
            this.txQueue = txQueue;
            this.rxQueue = rxQueue;
            this.uid = uid;
            this.timeout = timeout;
            this.inode = inode;
            this.ipv6 = ipv6;
        }

        public int getSlot() { return slot; }
        public String getLocalAddress() { return localAddress; }
        public int getLocalPort() { return localPort; }
        public String getRemoteAddress() { return remoteAddress; }
        public int getRemotePort() { return remotePort; }
        public String getStateHex() { return stateHex; }
        public long getTxQueue() { return txQueue; }
        public long getRxQueue() { return rxQueue; }
        public int getUid() { return uid; }
        public int getTimeout() { return timeout; }
        public long getInode() { return inode; }
        public boolean isIpv6() { return ipv6; }
    }

    /**
     * {@code network.capabilities} transport/capability sets. Membership is never inferred
     * from {@code network.links} or wifi.
     */
    public static final class NetworkCapabilitiesConfig {
        private final List<Integer> transportTypes;
        private final List<Integer> networkCapabilities;

        private NetworkCapabilitiesConfig(List<Integer> transportTypes,
                                          List<Integer> networkCapabilities) {
            this.transportTypes = transportTypes;
            this.networkCapabilities = networkCapabilities;
        }

        public List<Integer> getTransportTypes() {
            return transportTypes;
        }

        public List<Integer> getNetworkCapabilities() {
            return networkCapabilities;
        }

        public boolean hasTransport(int transport) {
            return transportTypes.contains(Integer.valueOf(transport));
        }

        public boolean hasCapability(int capability) {
            return networkCapabilities.contains(Integer.valueOf(capability));
        }

        public long transportBitset() {
            long bits = 0L;
            for (int i = 0; i < transportTypes.size(); i++) {
                int t = transportTypes.get(i).intValue();
                if (t >= 0 && t < 63) {
                    bits |= 1L << t;
                }
            }
            return bits;
        }

        public long capabilityBitset() {
            long bits = 0L;
            for (int i = 0; i < networkCapabilities.size(); i++) {
                int c = networkCapabilities.get(i).intValue();
                if (c >= 0 && c < 63) {
                    bits |= 1L << c;
                }
            }
            return bits;
        }
    }

    /**
     * One {@code linux.processes[]} snapshot. Never inferred from {@code process.pid}.
     */
    public static final class LinuxProcessConfig {
        private final int pid;
        private final List<String> cmdline;
        private final String comm;
        private final String exe;

        private LinuxProcessConfig(int pid, List<String> cmdline, String comm, String exe) {
            this.pid = pid;
            this.cmdline = cmdline;
            this.comm = comm;
            this.exe = exe;
        }

        public int getPid() { return pid; }
        public List<String> getCmdline() { return cmdline; }
        public String getComm() { return comm; }
        public String getExe() { return exe; }
    }

    /**
     * One {@code android.displays[]} entry. Independent of {@code android.display}.
     */
    public static final class AndroidDisplayEntryConfig {
        private final int id;
        private final String name;
        private final int flags;
        private final int widthPixels;
        private final int heightPixels;
        private final int densityDpi;

        private AndroidDisplayEntryConfig(int id, String name, int flags,
                                          int widthPixels, int heightPixels, int densityDpi) {
            this.id = id;
            this.name = name;
            this.flags = flags;
            this.widthPixels = widthPixels;
            this.heightPixels = heightPixels;
            this.densityDpi = densityDpi;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public int getFlags() { return flags; }
        public int getWidthPixels() { return widthPixels; }
        public int getHeightPixels() { return heightPixels; }
        public int getDensityDpi() { return densityDpi; }
    }

    /**
     * Immutable view of one {@code network.arpEntries[]} entry after parse validation.
     * All five whitelist fields are required. Values are never inferred from
     * {@code network.interfaces}, {@code network.ipv4Routes},
     * {@code network.interfaceStats}, {@code network.ipv6Addresses},
     * {@code network.wifi}, or other fields. Never exposes JSONObject/JSONArray.
     */
    public static final class NetworkArpEntryConfig {
        private final String interfaceName;
        private final String ipv4;
        private final long hardwareType;
        private final long flags;
        private final String mac;

        private NetworkArpEntryConfig(String interfaceName, String ipv4,
                                      long hardwareType, long flags, String mac) {
            this.interfaceName = interfaceName;
            this.ipv4 = ipv4;
            this.hardwareType = hardwareType;
            this.flags = flags;
            this.mac = mac;
        }

        /** Configured interface name; must match a {@code network.interfaces[]} {@code name}. */
        public String getInterfaceName() {
            return interfaceName;
        }

        /** Validated IPv4 address (dotted decimal). Never inferred from interfaces or routes. */
        public String getIpv4() {
            return ipv4;
        }

        /** ARP hardware type as unsigned 32-bit ({@code 0..4294967295}). */
        public long getHardwareType() {
            return hardwareType;
        }

        /** ARP flags as unsigned 32-bit ({@code 0..4294967295}). */
        public long getFlags() {
            return flags;
        }

        /**
         * Normalized lowercase colon-separated MAC ({@code aa:bb:cc:dd:ee:ff}).
         * Same rules as {@code network.interfaces[].mac}; never inferred from that field.
         */
        public String getMac() {
            return mac;
        }
    }

    /**
     * Immutable view of one {@code network.igmpMemberships[]} entry after parse validation.
     * All seven whitelist fields are required. Group, querier, users, timer, and reporter
     * are never inferred from {@code network.interfaces}, {@code network.ipv4Routes},
     * {@code network.interfaceStats}, {@code network.ipv6Addresses},
     * {@code network.arpEntries}, {@code network.wifi}, or other fields.
     * Interface index for {@code /proc/net/igmp} is reused from the matching
     * {@code network.interfaces[].index} at render time. Never exposes JSONObject/JSONArray.
     */
    public static final class NetworkIgmpMembershipConfig {
        private final String interfaceName;
        private final String groupIpv4;
        private final String querierVersion;
        private final int users;
        private final boolean timerRunning;
        private final long timerClock;
        private final boolean reporter;

        private NetworkIgmpMembershipConfig(String interfaceName, String groupIpv4,
                                            String querierVersion, int users,
                                            boolean timerRunning, long timerClock,
                                            boolean reporter) {
            this.interfaceName = interfaceName;
            this.groupIpv4 = groupIpv4;
            this.querierVersion = querierVersion;
            this.users = users;
            this.timerRunning = timerRunning;
            this.timerClock = timerClock;
            this.reporter = reporter;
        }

        /** Configured interface name; must match a {@code network.interfaces[]} {@code name}. */
        public String getInterfaceName() {
            return interfaceName;
        }

        /**
         * Validated IPv4 multicast group in {@code 224.0.0.0/4} (dotted decimal).
         * Never inferred from interfaces, routes, or host network.
         */
        public String getGroupIpv4() {
            return groupIpv4;
        }

        /**
         * IGMP querier token {@code V1}, {@code V2}, or {@code V3}.
         * All entries that share an {@code interfaceName} must use the same value.
         */
        public String getQuerierVersion() {
            return querierVersion;
        }

        /** Membership user count as exact JSON integer {@code 0..2147483647}. */
        public int getUsers() {
            return users;
        }

        /** Whether the IGMP timer is running; kernel prints {@code 0} or {@code 1}. */
        public boolean isTimerRunning() {
            return timerRunning;
        }

        /**
         * Timer clock as unsigned 32-bit ({@code 0..4294967295}).
         * Must be {@code 0} when {@link #isTimerRunning()} is false.
         */
        public long getTimerClock() {
            return timerClock;
        }

        /** Reporter flag; kernel prints {@code 0} or {@code 1}. */
        public boolean isReporter() {
            return reporter;
        }
    }

    /**
     * Immutable view of one {@code network.igmp6Memberships[]} entry after parse validation.
     * All five whitelist fields are required. Group, users, flags, and timer are never
     * inferred from {@code network.interfaces}, {@code network.ipv4Routes},
     * {@code network.interfaceStats}, {@code network.ipv6Addresses},
     * {@code network.arpEntries}, {@code network.igmpMemberships},
     * {@code network.wifi}, or other fields.
     * Interface index for {@code /proc/net/igmp6} is reused from the matching
     * {@code network.interfaces[].index} at render time. Never exposes JSONObject/JSONArray.
     */
    public static final class NetworkIgmp6MembershipConfig {
        private final String interfaceName;
        private final String groupIpv6;
        private final String groupIpv6Hex;
        private final int users;
        private final long flags;
        private final long timer;

        private NetworkIgmp6MembershipConfig(String interfaceName, String groupIpv6,
                                             String groupIpv6Hex, int users,
                                             long flags, long timer) {
            this.interfaceName = interfaceName;
            this.groupIpv6 = groupIpv6;
            this.groupIpv6Hex = groupIpv6Hex;
            this.users = users;
            this.flags = flags;
            this.timer = timer;
        }

        /** Configured interface name; must match a {@code network.interfaces[]} {@code name}. */
        public String getInterfaceName() {
            return interfaceName;
        }

        /**
         * Validated strict IPv6 multicast group text in {@code ff00::/8}.
         * Never inferred from interfaces, routes, {@code ipv6Addresses}, or host network.
         */
        public String getGroupIpv6() {
            return groupIpv6;
        }

        /**
         * Network-order 32-character uppercase hex of {@link #getGroupIpv6()}
         * (no colons), matching kernel {@code %pi6} width for {@code /proc/net/igmp6}.
         */
        public String getGroupIpv6Hex() {
            return groupIpv6Hex;
        }

        /** Membership user count as exact JSON integer {@code 0..2147483647}. */
        public int getUsers() {
            return users;
        }

        /** MLD/igmp6 flags as unsigned 32-bit ({@code 0..4294967295}). */
        public long getFlags() {
            return flags;
        }

        /** Timer as exact JSON integer {@code 0..9223372036854775807} (kernel {@code %ld}). */
        public long getTimer() {
            return timer;
        }
    }

    /**
     * Immutable view of one {@code network.linkLayerMulticastEntries[]} entry after parse
     * validation. All four whitelist fields are required. MAC, reference count, and
     * {@code globalUse} are never inferred from {@code network.interfaces} (including
     * {@code mac} and {@code linkLayerBroadcast}), {@code network.ipv4Routes},
     * {@code network.interfaceStats}, {@code network.ipv6Addresses},
     * {@code network.arpEntries}, {@code network.igmpMemberships},
     * {@code network.igmp6Memberships}, {@code network.wifi}, or other fields.
     * Interface index for {@code /proc/net/dev_mcast} is reused from the matching
     * {@code network.interfaces[].index} at render time. Never exposes JSONObject/JSONArray.
     */
    public static final class NetworkLinkLayerMulticastEntryConfig {
        private final String interfaceName;
        private final String mac;
        private final int referenceCount;
        private final boolean globalUse;

        private NetworkLinkLayerMulticastEntryConfig(String interfaceName, String mac,
                                                     int referenceCount, boolean globalUse) {
            this.interfaceName = interfaceName;
            this.mac = mac;
            this.referenceCount = referenceCount;
            this.globalUse = globalUse;
        }

        /** Configured interface name; must match a {@code network.interfaces[]} {@code name}. */
        public String getInterfaceName() {
            return interfaceName;
        }

        /**
         * Normalized lowercase colon-separated MAC ({@code aa:bb:cc:dd:ee:ff}).
         * Same rules as {@code network.interfaces[].mac}; never inferred from that field
         * or from {@code linkLayerBroadcast}.
         */
        public String getMac() {
            return mac;
        }

        /** Hardware-address reference count as exact JSON integer {@code 1..2147483647}. */
        public int getReferenceCount() {
            return referenceCount;
        }

        /** Kernel {@code global_use}; printed as {@code 0} or {@code 1}. */
        public boolean isGlobalUse() {
            return globalUse;
        }
    }

    /**
     * Immutable view of optional {@code network.wirelessProcStats} after parse validation.
     * Only {@code wirelessExtensionsVersion} and {@code entries} are allowed, and both are
     * required. Values are never inferred from {@code network.interfaces},
     * {@code network.ipv4Routes}, {@code network.interfaceStats},
     * {@code network.ipv6Addresses}, {@code network.arpEntries},
     * {@code network.igmpMemberships}, {@code network.igmp6Memberships},
     * {@code network.linkLayerMulticastEntries}, {@code network.wifi}, or other fields.
     * Never exposes JSONObject/JSONArray.
     */
    public static final class NetworkWirelessProcStatsConfig {
        private final int wirelessExtensionsVersion;
        private final List<NetworkWirelessProcStatsEntryConfig> entries;

        private NetworkWirelessProcStatsConfig(int wirelessExtensionsVersion,
                                               List<NetworkWirelessProcStatsEntryConfig> entries) {
            this.wirelessExtensionsVersion = wirelessExtensionsVersion;
            this.entries = entries;
        }

        /** Wireless Extensions version printed in the second header column ({@code 0..999}). */
        public int getWirelessExtensionsVersion() {
            return wirelessExtensionsVersion;
        }

        /** Immutable {@code entries} in JSON array order. */
        public List<NetworkWirelessProcStatsEntryConfig> getEntries() {
            return entries;
        }
    }

    /**
     * Immutable view of one {@code network.wirelessProcStats.entries[]} object after parse
     * validation. All fourteen whitelist fields are required. {@code level} and {@code noise}
     * are the final signed /proc numbers; iw_statistics raw bytes and DBM conversion are
     * not simulated. Never exposes JSONObject/JSONArray.
     */
    public static final class NetworkWirelessProcStatsEntryConfig {
        private final String interfaceName;
        private final int status;
        private final int linkQuality;
        private final int level;
        private final int noise;
        private final boolean linkUpdated;
        private final boolean levelUpdated;
        private final boolean noiseUpdated;
        private final long discardNwid;
        private final long discardCrypt;
        private final long discardFragment;
        private final long discardRetries;
        private final long discardMisc;
        private final long missedBeacon;

        private NetworkWirelessProcStatsEntryConfig(String interfaceName, int status,
                                                    int linkQuality, int level, int noise,
                                                    boolean linkUpdated, boolean levelUpdated,
                                                    boolean noiseUpdated, long discardNwid,
                                                    long discardCrypt, long discardFragment,
                                                    long discardRetries, long discardMisc,
                                                    long missedBeacon) {
            this.interfaceName = interfaceName;
            this.status = status;
            this.linkQuality = linkQuality;
            this.level = level;
            this.noise = noise;
            this.linkUpdated = linkUpdated;
            this.levelUpdated = levelUpdated;
            this.noiseUpdated = noiseUpdated;
            this.discardNwid = discardNwid;
            this.discardCrypt = discardCrypt;
            this.discardFragment = discardFragment;
            this.discardRetries = discardRetries;
            this.discardMisc = discardMisc;
            this.missedBeacon = missedBeacon;
        }

        /** Configured interface name; must match a {@code network.interfaces[]} {@code name}. */
        public String getInterfaceName() {
            return interfaceName;
        }

        /** Status as exact JSON integer {@code 0..65535} (kernel {@code %04x}). */
        public int getStatus() {
            return status;
        }

        /** Link quality as exact JSON integer {@code 0..255}. */
        public int getLinkQuality() {
            return linkQuality;
        }

        /**
         * Final signed level printed in {@code /proc/net/wireless} ({@code -256..255}).
         * Not an iw_statistics raw byte and not DBM-converted.
         */
        public int getLevel() {
            return level;
        }

        /**
         * Final signed noise printed in {@code /proc/net/wireless} ({@code -256..255}).
         * Not an iw_statistics raw byte and not DBM-converted.
         */
        public int getNoise() {
            return noise;
        }

        /** When true, print {@code '.'} after link quality; otherwise a space. */
        public boolean isLinkUpdated() {
            return linkUpdated;
        }

        /** When true, print {@code '.'} after level; otherwise a space. */
        public boolean isLevelUpdated() {
            return levelUpdated;
        }

        /** When true, print {@code '.'} after noise; otherwise a space. */
        public boolean isNoiseUpdated() {
            return noiseUpdated;
        }

        /** Discarded nwid as unsigned 32-bit ({@code 0..4294967295}). */
        public long getDiscardNwid() {
            return discardNwid;
        }

        /** Discarded crypt as unsigned 32-bit ({@code 0..4294967295}). */
        public long getDiscardCrypt() {
            return discardCrypt;
        }

        /** Discarded fragment as unsigned 32-bit ({@code 0..4294967295}). */
        public long getDiscardFragment() {
            return discardFragment;
        }

        /** Discarded retries as unsigned 32-bit ({@code 0..4294967295}). */
        public long getDiscardRetries() {
            return discardRetries;
        }

        /** Discarded misc as unsigned 32-bit ({@code 0..4294967295}). */
        public long getDiscardMisc() {
            return discardMisc;
        }

        /** Missed beacon as unsigned 32-bit ({@code 0..4294967295}). */
        public long getMissedBeacon() {
            return missedBeacon;
        }
    }

    /**
     * Immutable view of optional {@code network.bluetooth} after parse validation (v1 subset).
     * Independently presence-tracked fields; never exposes JSONObject/JSONArray.
     * Explicit JSON null for name/address is configured with getter returning null.
     * {@code state}/{@code scanMode} are fixed int markers (not inferred from {@code enabled}).
     * {@code discovering} is a strict Boolean marker (not inferred from {@code enabled}/{@code state}).
     */
    public static final class NetworkBluetoothConfig {
        private final String name;
        private final boolean nameConfigured;
        private final String address;
        private final boolean addressConfigured;
        private final boolean enabled;
        private final boolean enabledConfigured;
        private final int state;
        private final boolean stateConfigured;
        private final int scanMode;
        private final boolean scanModeConfigured;
        private final boolean discovering;
        private final boolean discoveringConfigured;

        private NetworkBluetoothConfig(String name, boolean nameConfigured,
                                       String address, boolean addressConfigured,
                                       boolean enabled, boolean enabledConfigured,
                                       int state, boolean stateConfigured,
                                       int scanMode, boolean scanModeConfigured,
                                       boolean discovering, boolean discoveringConfigured) {
            this.name = name;
            this.nameConfigured = nameConfigured;
            this.address = address;
            this.addressConfigured = addressConfigured;
            this.enabled = enabled;
            this.enabledConfigured = enabledConfigured;
            this.state = state;
            this.stateConfigured = stateConfigured;
            this.scanMode = scanMode;
            this.scanModeConfigured = scanModeConfigured;
            this.discovering = discovering;
            this.discoveringConfigured = discoveringConfigured;
        }

        public boolean isNameConfigured() {
            return nameConfigured;
        }

        /** Bluetooth adapter name, or {@code null} if missing or explicit JSON null. */
        public String getName() {
            return name;
        }

        public boolean isAddressConfigured() {
            return addressConfigured;
        }

        /**
         * Bluetooth MAC address in Locale.ROOT lowercase after validation, or {@code null}
         * if missing or explicit JSON null.
         */
        public String getAddress() {
            return address;
        }

        public boolean isEnabledConfigured() {
            return enabledConfigured;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isStateConfigured() {
            return stateConfigured;
        }

        /**
         * Fixed {@code BluetoothAdapter.getState()} marker
         * ({@code 10}/{@code 11}/{@code 12}/{@code 13}). Not inferred from {@code enabled}.
         */
        public int getState() {
            return state;
        }

        public boolean isScanModeConfigured() {
            return scanModeConfigured;
        }

        /**
         * Fixed {@code BluetoothAdapter.getScanMode()} marker
         * ({@code 20}/{@code 21}/{@code 23}). Not inferred from other fields.
         */
        public int getScanMode() {
            return scanMode;
        }

        public boolean isDiscoveringConfigured() {
            return discoveringConfigured;
        }

        /**
         * Fixed {@code BluetoothAdapter.isDiscovering()} marker.
         * Not inferred from {@code enabled} or {@code state}; default {@code false} when key absent.
         */
        public boolean isDiscovering() {
            return discovering;
        }

        /**
         * True when at least one of name/address/enabled/state/scanMode/discovering is present
         * (including null strings).
         */
        public boolean hasAnyFieldConfigured() {
            return nameConfigured || addressConfigured || enabledConfigured
                    || stateConfigured || scanModeConfigured || discoveringConfigured;
        }
    }

    /**
     * Immutable view of one {@code android.packages[]} entry after parse validation.
     * Never exposes JSONObject/JSONArray.
     * Optional-field presence is tracked separately from values so explicit JSON null
     * (configured, getter null) is distinct from a missing key (not configured, getter null).
     */
    public static final class PackageConfig {
        private final String packageName;
        private final String versionName;
        private final boolean versionNameConfigured;
        private final Integer versionCode;
        private final boolean versionCodeConfigured;
        private final String sourceDir;
        private final boolean sourceDirConfigured;
        private final String dataDir;
        private final boolean dataDirConfigured;
        private final Integer uid;
        private final boolean uidConfigured;
        private final Boolean enabled;
        private final boolean enabledConfigured;
        private final Boolean systemApp;
        private final boolean systemAppConfigured;
        private final Integer applicationFlags;
        private final boolean applicationFlagsConfigured;
        private final String installerPackageName;
        private final boolean installerPackageNameConfigured;
        private final String initiatingPackageName;
        private final boolean initiatingPackageNameConfigured;
        private final String originatingPackageName;
        private final boolean originatingPackageNameConfigured;
        private final Long firstInstallTimeMillis;
        private final boolean firstInstallTimeMillisConfigured;
        private final Long lastUpdateTimeMillis;
        private final boolean lastUpdateTimeMillisConfigured;
        private final Map<String, Boolean> permissions;
        private final boolean permissionsConfigured;
        private final List<String> signatureHexes;
        private final boolean signaturesConfigured;
        private final List<String> signingCertificateHistoryHexes;
        private final boolean signingCertificateHistoryConfigured;

        private PackageConfig(String packageName,
                              String versionName, boolean versionNameConfigured,
                              Integer versionCode, boolean versionCodeConfigured,
                              String sourceDir, boolean sourceDirConfigured,
                              String dataDir, boolean dataDirConfigured,
                              Integer uid, boolean uidConfigured,
                              Boolean enabled, boolean enabledConfigured,
                              Boolean systemApp, boolean systemAppConfigured,
                              Integer applicationFlags, boolean applicationFlagsConfigured,
                              String installerPackageName, boolean installerPackageNameConfigured,
                              String initiatingPackageName, boolean initiatingPackageNameConfigured,
                              String originatingPackageName, boolean originatingPackageNameConfigured,
                              Long firstInstallTimeMillis, boolean firstInstallTimeMillisConfigured,
                              Long lastUpdateTimeMillis, boolean lastUpdateTimeMillisConfigured,
                              Map<String, Boolean> permissions, boolean permissionsConfigured,
                              List<String> signatureHexes, boolean signaturesConfigured,
                              List<String> signingCertificateHistoryHexes,
                              boolean signingCertificateHistoryConfigured) {
            this.packageName = packageName;
            this.versionName = versionName;
            this.versionNameConfigured = versionNameConfigured;
            this.versionCode = versionCode;
            this.versionCodeConfigured = versionCodeConfigured;
            this.sourceDir = sourceDir;
            this.sourceDirConfigured = sourceDirConfigured;
            this.dataDir = dataDir;
            this.dataDirConfigured = dataDirConfigured;
            this.uid = uid;
            this.uidConfigured = uidConfigured;
            this.enabled = enabled;
            this.enabledConfigured = enabledConfigured;
            this.systemApp = systemApp;
            this.systemAppConfigured = systemAppConfigured;
            this.applicationFlags = applicationFlags;
            this.applicationFlagsConfigured = applicationFlagsConfigured;
            this.installerPackageName = installerPackageName;
            this.installerPackageNameConfigured = installerPackageNameConfigured;
            this.initiatingPackageName = initiatingPackageName;
            this.initiatingPackageNameConfigured = initiatingPackageNameConfigured;
            this.originatingPackageName = originatingPackageName;
            this.originatingPackageNameConfigured = originatingPackageNameConfigured;
            this.firstInstallTimeMillis = firstInstallTimeMillis;
            this.firstInstallTimeMillisConfigured = firstInstallTimeMillisConfigured;
            this.lastUpdateTimeMillis = lastUpdateTimeMillis;
            this.lastUpdateTimeMillisConfigured = lastUpdateTimeMillisConfigured;
            this.permissions = permissions;
            this.permissionsConfigured = permissionsConfigured;
            this.signatureHexes = signatureHexes;
            this.signaturesConfigured = signaturesConfigured;
            this.signingCertificateHistoryHexes = signingCertificateHistoryHexes;
            this.signingCertificateHistoryConfigured = signingCertificateHistoryConfigured;
        }

        public String getPackageName() {
            return packageName;
        }

        /** Configured string, or {@code null} when missing or explicit JSON null. */
        public String getVersionName() {
            return versionName;
        }

        /** Whether {@code versionName} was present (including explicit JSON null). */
        public boolean isVersionNameConfigured() {
            return versionNameConfigured;
        }

        /** Configured int, or {@code null} when the field is absent. */
        public Integer getVersionCode() {
            return versionCode;
        }

        /** Whether {@code versionCode} was present. */
        public boolean isVersionCodeConfigured() {
            return versionCodeConfigured;
        }

        /** Absolute path string, or {@code null} when missing or explicit JSON null. */
        public String getSourceDir() {
            return sourceDir;
        }

        /** Whether {@code sourceDir} was present (including explicit JSON null). */
        public boolean isSourceDirConfigured() {
            return sourceDirConfigured;
        }

        /** Absolute path string, or {@code null} when missing or explicit JSON null. */
        public String getDataDir() {
            return dataDir;
        }

        /** Whether {@code dataDir} was present (including explicit JSON null). */
        public boolean isDataDirConfigured() {
            return dataDirConfigured;
        }

        /** Configured int, or {@code null} when the field is absent. */
        public Integer getUid() {
            return uid;
        }

        /** Whether {@code uid} was present. */
        public boolean isUidConfigured() {
            return uidConfigured;
        }

        /** Configured boolean, or {@code null} when the field is absent. */
        public Boolean getEnabled() {
            return enabled;
        }

        /** Whether {@code enabled} was present. */
        public boolean isEnabledConfigured() {
            return enabledConfigured;
        }

        /** Configured boolean, or {@code null} when the field is absent. */
        public Boolean getSystemApp() {
            return systemApp;
        }

        /** Whether {@code systemApp} was present. */
        public boolean isSystemAppConfigured() {
            return systemAppConfigured;
        }

        public boolean isApplicationFlagsConfigured() {
            return applicationFlagsConfigured;
        }

        /**
         * Exact {@code ApplicationInfo.flags} integer when configured. When omitted, JNI
         * may still project {@code FLAG_SYSTEM} from {@code systemApp}.
         */
        public Integer getApplicationFlags() {
            return applicationFlags;
        }

        /** Configured installer package name, or {@code null} when missing or explicit JSON null. */
        public String getInstallerPackageName() {
            return installerPackageName;
        }

        /** Whether {@code installerPackageName} was present (including explicit JSON null). */
        public boolean isInstallerPackageNameConfigured() {
            return installerPackageNameConfigured;
        }

        /** Configured initiating package name, or {@code null} when missing or explicit JSON null. */
        public String getInitiatingPackageName() {
            return initiatingPackageName;
        }

        /** Whether {@code initiatingPackageName} was present (including explicit JSON null). */
        public boolean isInitiatingPackageNameConfigured() {
            return initiatingPackageNameConfigured;
        }

        /** Configured originating package name, or {@code null} when missing or explicit JSON null. */
        public String getOriginatingPackageName() {
            return originatingPackageName;
        }

        /** Whether {@code originatingPackageName} was present (including explicit JSON null). */
        public boolean isOriginatingPackageNameConfigured() {
            return originatingPackageNameConfigured;
        }

        /** Configured first-install epoch millis, or {@code null} when the field is absent. */
        public Long getFirstInstallTimeMillis() {
            return firstInstallTimeMillis;
        }

        /** Whether {@code firstInstallTimeMillis} was present. */
        public boolean isFirstInstallTimeMillisConfigured() {
            return firstInstallTimeMillisConfigured;
        }

        /** Configured last-update epoch millis, or {@code null} when the field is absent. */
        public Long getLastUpdateTimeMillis() {
            return lastUpdateTimeMillis;
        }

        /** Whether {@code lastUpdateTimeMillis} was present. */
        public boolean isLastUpdateTimeMillisConfigured() {
            return lastUpdateTimeMillisConfigured;
        }

        /**
         * Whether {@code permissions} was present (including an explicit empty object).
         * Missing node is false; empty object is true with an empty map.
         */
        public boolean isPermissionsConfigured() {
            return permissionsConfigured;
        }

        /**
         * Immutable permission map (permission name → granted). Empty when missing or empty object.
         * Never exposes JSONObject.
         */
        public Map<String, Boolean> getPermissions() {
            return permissions;
        }

        /**
         * Whether the given permission key is present under {@code permissions}.
         * Rejects null/blank/invalid permission names.
         */
        public boolean isPermissionConfigured(String permission) {
            requirePermissionApiArg(permission);
            return permissions.containsKey(permission);
        }

        /**
         * Granted state for {@code permission}, or {@code fallback} when the key is absent
         * (including when the whole {@code permissions} node is missing).
         * Rejects null/blank/invalid permission names.
         */
        public boolean getPermissionGranted(String permission, boolean fallback) {
            requirePermissionApiArg(permission);
            Boolean value = permissions.get(permission);
            return value == null ? fallback : value.booleanValue();
        }

        /**
         * Whether {@code signaturesHex} was present (including an explicit empty array).
         * Missing node is false; empty array is true with an empty list.
         */
        public boolean isSignaturesConfigured() {
            return signaturesConfigured;
        }

        /**
         * Immutable lowercase hex signature strings in config order.
         * Empty when the field is missing or an explicit empty array. Never exposes JSONArray.
         */
        public List<String> getSignatureHexes() {
            return signatureHexes;
        }

        /**
         * Whether {@code signingCertificateHistoryHex} was present (including an explicit empty array).
         * Certificate rotation history; current signing certs remain {@code signaturesHex}.
         * Missing node is false; empty array is true with an empty list.
         */
        public boolean isSigningCertificateHistoryConfigured() {
            return signingCertificateHistoryConfigured;
        }

        /**
         * Immutable lowercase hex certificate-history strings in config order.
         * Empty when the field is missing or an explicit empty array. Never exposes JSONArray.
         */
        public List<String> getSigningCertificateHistoryHexes() {
            return signingCertificateHistoryHexes;
        }
    }

    /**
     * Immutable view of one {@code android.features[]} entry after parse validation.
     * Never exposes JSONObject/JSONArray.
     * Optional {@code version} presence is tracked separately from the value.
     */
    public static final class FeatureConfig {
        private final String name;
        private final Integer version;
        private final boolean versionConfigured;

        private FeatureConfig(String name, Integer version, boolean versionConfigured) {
            this.name = name;
            this.version = version;
            this.versionConfigured = versionConfigured;
        }

        /** Feature name (dot-separated {@code [A-Za-z0-9_]} segments). */
        public String getName() {
            return name;
        }

        /** Configured version, or {@code null} when the field is absent. */
        public Integer getVersion() {
            return version;
        }

        /** Whether {@code version} was present in JSON. */
        public boolean isVersionConfigured() {
            return versionConfigured;
        }
    }

    /**
     * Immutable view of one {@code android.accounts[]} entry after parse validation.
     * Exactly {@code name} and {@code type} (both nonempty String). Never exposes JSONObject.
     */
    public static final class AndroidAccountConfig {
        private final String name;
        private final String type;

        private AndroidAccountConfig(String name, String type) {
            this.name = name;
            this.type = type;
        }

        /** Account name ({@code Account.name}). */
        public String getName() {
            return name;
        }

        /** Account type ({@code Account.type}). */
        public String getType() {
            return type;
        }
    }

    /**
     * Immutable view of one {@code android.inputMethods[]} entry after parse validation.
     * Exactly nonempty String {@code id} and Boolean {@code enabled}. Never exposes JSONObject.
     */
    public static final class AndroidInputMethodConfig {
        private final String id;
        private final boolean enabled;

        private AndroidInputMethodConfig(String id, boolean enabled) {
            this.id = id;
            this.enabled = enabled;
        }

        /** Input method id ({@code InputMethodInfo.getId()}). */
        public String getId() {
            return id;
        }

        /** Whether this IME is enabled for {@code getEnabledInputMethodList}. */
        public boolean isEnabled() {
            return enabled;
        }
    }

    /**
     * Immutable view of one {@code filesystem.stat} path entry after parse validation.
     * JSON shape is a path-keyed object ({@code filesystem.stat."/abs/path"}), not an array.
     * Never exposes JSONObject/JSONArray.
     * Optional-field presence is tracked separately from values (missing key vs present).
     * JSON field names (not C struct short names): {@code device}, {@code inode}, {@code mode},
     * {@code uid}, {@code gid}, {@code size}, {@code blockSize}, {@code blocks},
     * {@code atimeMillis}, {@code mtimeMillis}, {@code ctimeMillis}.
     * Config-only model in this round: no syscall/FileIO wiring.
     */
    public static final class FileStatConfig {
        private final String path;
        private final Long device;
        private final boolean deviceConfigured;
        private final Long inode;
        private final boolean inodeConfigured;
        private final Long mode;
        private final boolean modeConfigured;
        private final Long uid;
        private final boolean uidConfigured;
        private final Long gid;
        private final boolean gidConfigured;
        private final Long size;
        private final boolean sizeConfigured;
        private final Integer blockSize;
        private final boolean blockSizeConfigured;
        private final Long blocks;
        private final boolean blocksConfigured;
        private final Long atimeMillis;
        private final boolean atimeMillisConfigured;
        private final Long mtimeMillis;
        private final boolean mtimeMillisConfigured;
        private final Long ctimeMillis;
        private final boolean ctimeMillisConfigured;

        private FileStatConfig(String path,
                               Long device, boolean deviceConfigured,
                               Long inode, boolean inodeConfigured,
                               Long mode, boolean modeConfigured,
                               Long uid, boolean uidConfigured,
                               Long gid, boolean gidConfigured,
                               Long size, boolean sizeConfigured,
                               Integer blockSize, boolean blockSizeConfigured,
                               Long blocks, boolean blocksConfigured,
                               Long atimeMillis, boolean atimeMillisConfigured,
                               Long mtimeMillis, boolean mtimeMillisConfigured,
                               Long ctimeMillis, boolean ctimeMillisConfigured) {
            this.path = path;
            this.device = device;
            this.deviceConfigured = deviceConfigured;
            this.inode = inode;
            this.inodeConfigured = inodeConfigured;
            this.mode = mode;
            this.modeConfigured = modeConfigured;
            this.uid = uid;
            this.uidConfigured = uidConfigured;
            this.gid = gid;
            this.gidConfigured = gidConfigured;
            this.size = size;
            this.sizeConfigured = sizeConfigured;
            this.blockSize = blockSize;
            this.blockSizeConfigured = blockSizeConfigured;
            this.blocks = blocks;
            this.blocksConfigured = blocksConfigured;
            this.atimeMillis = atimeMillis;
            this.atimeMillisConfigured = atimeMillisConfigured;
            this.mtimeMillis = mtimeMillis;
            this.mtimeMillisConfigured = mtimeMillisConfigured;
            this.ctimeMillis = ctimeMillis;
            this.ctimeMillisConfigured = ctimeMillisConfigured;
        }

        /** Absolute path key for this stat entry. */
        public String getPath() {
            return path;
        }

        public boolean isDeviceConfigured() {
            return deviceConfigured;
        }

        /** Device id ({@code st_dev}), or {@code null} when absent. */
        public Long getDevice() {
            return device;
        }

        public boolean isInodeConfigured() {
            return inodeConfigured;
        }

        /** Inode number ({@code st_ino}), or {@code null} when absent. */
        public Long getInode() {
            return inode;
        }

        public boolean isModeConfigured() {
            return modeConfigured;
        }

        /** File mode ({@code st_mode}), or {@code null} when absent. Exact non-negative long. */
        public Long getMode() {
            return mode;
        }

        public boolean isUidConfigured() {
            return uidConfigured;
        }

        /** Owner uid ({@code st_uid}), or {@code null} when absent. Exact non-negative long. */
        public Long getUid() {
            return uid;
        }

        public boolean isGidConfigured() {
            return gidConfigured;
        }

        /** Owner gid ({@code st_gid}), or {@code null} when absent. Exact non-negative long. */
        public Long getGid() {
            return gid;
        }

        public boolean isSizeConfigured() {
            return sizeConfigured;
        }

        /** Size in bytes ({@code st_size}), or {@code null} when absent. */
        public Long getSize() {
            return size;
        }

        public boolean isBlockSizeConfigured() {
            return blockSizeConfigured;
        }

        /** Preferred I/O block size ({@code st_blksize}), or {@code null} when absent. */
        public Integer getBlockSize() {
            return blockSize;
        }

        public boolean isBlocksConfigured() {
            return blocksConfigured;
        }

        /** Allocated 512-byte blocks ({@code st_blocks}), or {@code null} when absent. */
        public Long getBlocks() {
            return blocks;
        }

        public boolean isAtimeMillisConfigured() {
            return atimeMillisConfigured;
        }

        /** Access time epoch millis ({@code st_atim}), or {@code null} when absent. */
        public Long getAtimeMillis() {
            return atimeMillis;
        }

        public boolean isMtimeMillisConfigured() {
            return mtimeMillisConfigured;
        }

        /** Modification time epoch millis ({@code st_mtim}), or {@code null} when absent. */
        public Long getMtimeMillis() {
            return mtimeMillis;
        }

        public boolean isCtimeMillisConfigured() {
            return ctimeMillisConfigured;
        }

        /** Status-change time epoch millis ({@code st_ctim}), or {@code null} when absent. */
        public Long getCtimeMillis() {
            return ctimeMillis;
        }
    }

    /**
     * Immutable view of one {@code filesystem.statfs} mount-point entry after parse validation.
     * JSON shape is a mount-point-keyed object ({@code filesystem.statfs."/data"}), not an array.
     * Never exposes JSONObject/JSONArray.
     * Optional-field presence is tracked separately from values (missing key vs present).
     * Config-only model in this round: no syscall/StatFs wiring; no longest-prefix path match.
     */
    public static final class FileStatFsConfig {
        private final String mountPoint;
        private final Long type;
        private final boolean typeConfigured;
        private final Integer blockSize;
        private final boolean blockSizeConfigured;
        private final Long blocks;
        private final boolean blocksConfigured;
        private final Long blocksFree;
        private final boolean blocksFreeConfigured;
        private final Long blocksAvailable;
        private final boolean blocksAvailableConfigured;
        private final Long files;
        private final boolean filesConfigured;
        private final Long filesFree;
        private final boolean filesFreeConfigured;
        private final long[] fsid;
        private final boolean fsidConfigured;
        private final Integer nameLength;
        private final boolean nameLengthConfigured;
        private final Integer fragmentSize;
        private final boolean fragmentSizeConfigured;
        private final Long flags;
        private final boolean flagsConfigured;

        private FileStatFsConfig(String mountPoint,
                                 Long type, boolean typeConfigured,
                                 Integer blockSize, boolean blockSizeConfigured,
                                 Long blocks, boolean blocksConfigured,
                                 Long blocksFree, boolean blocksFreeConfigured,
                                 Long blocksAvailable, boolean blocksAvailableConfigured,
                                 Long files, boolean filesConfigured,
                                 Long filesFree, boolean filesFreeConfigured,
                                 long[] fsid, boolean fsidConfigured,
                                 Integer nameLength, boolean nameLengthConfigured,
                                 Integer fragmentSize, boolean fragmentSizeConfigured,
                                 Long flags, boolean flagsConfigured) {
            this.mountPoint = mountPoint;
            this.type = type;
            this.typeConfigured = typeConfigured;
            this.blockSize = blockSize;
            this.blockSizeConfigured = blockSizeConfigured;
            this.blocks = blocks;
            this.blocksConfigured = blocksConfigured;
            this.blocksFree = blocksFree;
            this.blocksFreeConfigured = blocksFreeConfigured;
            this.blocksAvailable = blocksAvailable;
            this.blocksAvailableConfigured = blocksAvailableConfigured;
            this.files = files;
            this.filesConfigured = filesConfigured;
            this.filesFree = filesFree;
            this.filesFreeConfigured = filesFreeConfigured;
            this.fsid = fsid;
            this.fsidConfigured = fsidConfigured;
            this.nameLength = nameLength;
            this.nameLengthConfigured = nameLengthConfigured;
            this.fragmentSize = fragmentSize;
            this.fragmentSizeConfigured = fragmentSizeConfigured;
            this.flags = flags;
            this.flagsConfigured = flagsConfigured;
        }

        /** POSIX-normalized absolute mount point key for this entry. */
        public String getMountPoint() {
            return mountPoint;
        }

        public boolean isTypeConfigured() {
            return typeConfigured;
        }

        /** Filesystem type ({@code f_type}), or {@code null} when absent. Exact long 0..0xffffffff. */
        public Long getType() {
            return type;
        }

        public boolean isBlockSizeConfigured() {
            return blockSizeConfigured;
        }

        /** Optimal transfer block size ({@code f_bsize}), or {@code null} when absent. */
        public Integer getBlockSize() {
            return blockSize;
        }

        public boolean isBlocksConfigured() {
            return blocksConfigured;
        }

        /** Total data blocks ({@code f_blocks}), or {@code null} when absent. */
        public Long getBlocks() {
            return blocks;
        }

        public boolean isBlocksFreeConfigured() {
            return blocksFreeConfigured;
        }

        /** Free blocks in fs ({@code f_bfree}), or {@code null} when absent. */
        public Long getBlocksFree() {
            return blocksFree;
        }

        public boolean isBlocksAvailableConfigured() {
            return blocksAvailableConfigured;
        }

        /** Free blocks for unprivileged users ({@code f_bavail}), or {@code null} when absent. */
        public Long getBlocksAvailable() {
            return blocksAvailable;
        }

        public boolean isFilesConfigured() {
            return filesConfigured;
        }

        /** Total file nodes ({@code f_files}), or {@code null} when absent. */
        public Long getFiles() {
            return files;
        }

        public boolean isFilesFreeConfigured() {
            return filesFreeConfigured;
        }

        /** Free file nodes ({@code f_ffree}), or {@code null} when absent. */
        public Long getFilesFree() {
            return filesFree;
        }

        public boolean isFsidConfigured() {
            return fsidConfigured;
        }

        /**
         * Filesystem id ({@code f_fsid}) as two unsigned 32-bit values packed in {@code long},
         * or {@code null} when absent. Returns a defensive {@code long[2]} copy.
         */
        public long[] getFsid() {
            if (!fsidConfigured || fsid == null) {
                return null;
            }
            return Arrays.copyOf(fsid, fsid.length);
        }

        public boolean isNameLengthConfigured() {
            return nameLengthConfigured;
        }

        /** Maximum filename length ({@code f_namelen}), or {@code null} when absent. */
        public Integer getNameLength() {
            return nameLength;
        }

        public boolean isFragmentSizeConfigured() {
            return fragmentSizeConfigured;
        }

        /** Fragment size ({@code f_frsize}), or {@code null} when absent. */
        public Integer getFragmentSize() {
            return fragmentSize;
        }

        public boolean isFlagsConfigured() {
            return flagsConfigured;
        }

        /** Mount flags ({@code f_flags}), or {@code null} when absent. Exact long 0..0xffffffff. */
        public Long getFlags() {
            return flags;
        }
    }

    /**
     * Immutable view of one {@code filesystem.mounts[]} entry after parse validation.
     * Models a fstab / mountinfo-style mount record for future procfs generation.
     * Never exposes JSONObject/JSONArray. Config-only in this round (no runtime wiring).
     */
    public static final class FileSystemMountConfig {
        private final String source;
        private final String target;
        private final String fileSystemType;
        private final String options;
        private final int dump;
        private final boolean dumpConfigured;
        private final int pass;
        private final boolean passConfigured;
        private final Integer mountId;
        private final Integer parentId;
        private final Integer major;
        private final Integer minor;
        private final boolean mountInfoConfigured;
        private final String root;
        private final boolean rootConfigured;
        private final String mountOptions;
        private final boolean mountOptionsConfigured;
        private final List<String> optionalFields;
        private final boolean optionalFieldsConfigured;
        private final String superOptions;
        private final boolean superOptionsConfigured;

        private FileSystemMountConfig(String source,
                                      String target,
                                      String fileSystemType,
                                      String options,
                                      int dump, boolean dumpConfigured,
                                      int pass, boolean passConfigured,
                                      Integer mountId, Integer parentId, Integer major, Integer minor,
                                      boolean mountInfoConfigured,
                                      String root, boolean rootConfigured,
                                      String mountOptions, boolean mountOptionsConfigured,
                                      List<String> optionalFields, boolean optionalFieldsConfigured,
                                      String superOptions, boolean superOptionsConfigured) {
            this.source = source;
            this.target = target;
            this.fileSystemType = fileSystemType;
            this.options = options;
            this.dump = dump;
            this.dumpConfigured = dumpConfigured;
            this.pass = pass;
            this.passConfigured = passConfigured;
            this.mountId = mountId;
            this.parentId = parentId;
            this.major = major;
            this.minor = minor;
            this.mountInfoConfigured = mountInfoConfigured;
            this.root = root;
            this.rootConfigured = rootConfigured;
            this.mountOptions = mountOptions;
            this.mountOptionsConfigured = mountOptionsConfigured;
            this.optionalFields = optionalFields;
            this.optionalFieldsConfigured = optionalFieldsConfigured;
            this.superOptions = superOptions;
            this.superOptionsConfigured = superOptionsConfigured;
        }

        /** Device/source field (fstab column 1). */
        public String getSource() {
            return source;
        }

        /** POSIX-normalized absolute mount target (fstab column 2). */
        public String getTarget() {
            return target;
        }

        /** Filesystem type token (fstab column 3). */
        public String getFileSystemType() {
            return fileSystemType;
        }

        /** Mount options comma-list as configured (fstab column 4). */
        public String getOptions() {
            return options;
        }

        public boolean isDumpConfigured() {
            return dumpConfigured;
        }

        /** Dump frequency (fstab column 5); defaults to {@code 0} when absent. */
        public int getDump() {
            return dump;
        }

        public boolean isPassConfigured() {
            return passConfigured;
        }

        /** fsck pass number (fstab column 6); defaults to {@code 0} when absent. */
        public int getPass() {
            return pass;
        }

        /**
         * Whether mountinfo extension fields {@code mountId}/{@code parentId}/{@code major}/{@code minor}
         * were present as a group.
         */
        public boolean isMountInfoConfigured() {
            return mountInfoConfigured;
        }

        /** Mount id, or {@code null} when mountinfo group absent. */
        public Integer getMountId() {
            return mountId;
        }

        /** Parent mount id, or {@code null} when mountinfo group absent. */
        public Integer getParentId() {
            return parentId;
        }

        /** Device major, or {@code null} when mountinfo group absent. */
        public Integer getMajor() {
            return major;
        }

        /** Device minor, or {@code null} when mountinfo group absent. */
        public Integer getMinor() {
            return minor;
        }

        public boolean isRootConfigured() {
            return rootConfigured;
        }

        /** Mount root within the filesystem; defaults to {@code /} when absent. */
        public String getRoot() {
            return root;
        }

        public boolean isMountOptionsConfigured() {
            return mountOptionsConfigured;
        }

        /** Per-mount options; defaults to {@link #getOptions()} when absent. */
        public String getMountOptions() {
            return mountOptions;
        }

        public boolean isOptionalFieldsConfigured() {
            return optionalFieldsConfigured;
        }

        /**
         * Optional mountinfo tags (e.g. {@code shared:1}); empty immutable list when absent.
         * Never exposes JSONArray; list is unmodifiable.
         */
        public List<String> getOptionalFields() {
            return optionalFields;
        }

        public boolean isSuperOptionsConfigured() {
            return superOptionsConfigured;
        }

        /** Superblock options; defaults to {@link #getOptions()} when absent. */
        public String getSuperOptions() {
            return superOptions;
        }
    }

    /**
     * Immutable view of one {@code filesystem.links} entry after parse validation.
     * JSON shape is a path-keyed object ({@code filesystem.links."/abs/link"} → target String).
     * Link path keys are POSIX-normalized; targets are stored as raw strings (no path normalize).
     * Runtime: shared {@code UnixSyscallHandler.readlink} prefers exact config hits for
     * {@code readlink} / {@code readlinkat(AT_FDCWD)}; lookup may rewrite
     * {@code /proc/<pid>/…} → {@code /proc/self/…} for the query only (no prefix match beyond
     * that alias). Does not create host symlink nodes or change open/stat path existence.
     */
    public static final class FileSystemLinkConfig {
        private final String path;
        private final String target;

        private FileSystemLinkConfig(String path, String target) {
            this.path = path;
            this.target = target;
        }

        /** POSIX-normalized absolute path of the symlink itself. */
        public String getPath() {
            return path;
        }

        /**
         * Raw symlink target string as configured (absolute or relative; may contain spaces
         * and backslashes). Not path-normalized.
         */
        public String getTarget() {
            return target;
        }
    }

    /**
     * Immutable view of optional {@code filesystem.systemDirectories} after parse validation.
     * Optional absolute paths for Android {@code Environment.getRootDirectory} /
     * {@code getDataDirectory} / {@code getDownloadCacheDirectory} / {@code getStorageDirectory}.
     * Each field is presence-tracked independently; no defaults and no host directory / FileIO
     * creation. Never exposes JSONObject/JSONArray.
     */
    public static final class FileSystemSystemDirectoriesConfig {
        private final String rootDirectory;
        private final boolean rootDirectoryConfigured;
        private final String dataDirectory;
        private final boolean dataDirectoryConfigured;
        private final String downloadCacheDirectory;
        private final boolean downloadCacheDirectoryConfigured;
        private final String storageDirectory;
        private final boolean storageDirectoryConfigured;

        private FileSystemSystemDirectoriesConfig(String rootDirectory,
                                                  boolean rootDirectoryConfigured,
                                                  String dataDirectory,
                                                  boolean dataDirectoryConfigured,
                                                  String downloadCacheDirectory,
                                                  boolean downloadCacheDirectoryConfigured,
                                                  String storageDirectory,
                                                  boolean storageDirectoryConfigured) {
            this.rootDirectory = rootDirectory;
            this.rootDirectoryConfigured = rootDirectoryConfigured;
            this.dataDirectory = dataDirectory;
            this.dataDirectoryConfigured = dataDirectoryConfigured;
            this.downloadCacheDirectory = downloadCacheDirectory;
            this.downloadCacheDirectoryConfigured = downloadCacheDirectoryConfigured;
            this.storageDirectory = storageDirectory;
            this.storageDirectoryConfigured = storageDirectoryConfigured;
        }

        /** Whether {@code rootDirectory} was present in JSON. */
        public boolean isRootDirectoryConfigured() {
            return rootDirectoryConfigured;
        }

        /**
         * POSIX-normalized absolute root directory path, or {@code null} when the key was absent.
         * Meaningful only when {@link #isRootDirectoryConfigured()} is true.
         */
        public String getRootDirectory() {
            return rootDirectory;
        }

        /** Whether {@code dataDirectory} was present in JSON. */
        public boolean isDataDirectoryConfigured() {
            return dataDirectoryConfigured;
        }

        /**
         * POSIX-normalized absolute data directory path, or {@code null} when the key was absent.
         * Meaningful only when {@link #isDataDirectoryConfigured()} is true.
         */
        public String getDataDirectory() {
            return dataDirectory;
        }

        /** Whether {@code downloadCacheDirectory} was present in JSON. */
        public boolean isDownloadCacheDirectoryConfigured() {
            return downloadCacheDirectoryConfigured;
        }

        /**
         * POSIX-normalized absolute download-cache directory path, or {@code null} when the key
         * was absent. Meaningful only when {@link #isDownloadCacheDirectoryConfigured()} is true.
         */
        public String getDownloadCacheDirectory() {
            return downloadCacheDirectory;
        }

        /** Whether {@code storageDirectory} was present in JSON. */
        public boolean isStorageDirectoryConfigured() {
            return storageDirectoryConfigured;
        }

        /**
         * POSIX-normalized absolute storage directory path, or {@code null} when the key was
         * absent. Meaningful only when {@link #isStorageDirectoryConfigured()} is true.
         */
        public String getStorageDirectory() {
            return storageDirectory;
        }
    }

    /**
     * Immutable view of optional {@code filesystem.externalStorage} after parse validation.
     * Primary external storage directory / state / emulated / removable for static
     * {@code Environment.getExternalStorageDirectory}/{@code getExternalStorageState}/
     * {@code isExternalStorageEmulated}/{@code isExternalStorageRemovable} (no-arg and primary-dir
     * File overloads). {@code Environment.getStorageDirectory} is under
     * {@code filesystem.systemDirectories}, not this node. Never exposes JSONObject/JSONArray;
     * does not create host directories or FileIO entries.
     */
    public static final class FileSystemExternalStorageConfig {
        private final String directory;
        private final boolean directoryConfigured;
        private final String state;
        private final boolean stateConfigured;
        private final boolean emulated;
        private final boolean emulatedConfigured;
        private final boolean removable;
        private final boolean removableConfigured;

        private FileSystemExternalStorageConfig(String directory, boolean directoryConfigured,
                                                String state, boolean stateConfigured,
                                                boolean emulated, boolean emulatedConfigured,
                                                boolean removable, boolean removableConfigured) {
            this.directory = directory;
            this.directoryConfigured = directoryConfigured;
            this.state = state;
            this.stateConfigured = stateConfigured;
            this.emulated = emulated;
            this.emulatedConfigured = emulatedConfigured;
            this.removable = removable;
            this.removableConfigured = removableConfigured;
        }

        /** Whether {@code directory} was present in JSON. */
        public boolean isDirectoryConfigured() {
            return directoryConfigured;
        }

        /** POSIX-normalized absolute external storage directory path. */
        public String getDirectory() {
            return directory;
        }

        /** Whether {@code state} was present in JSON. */
        public boolean isStateConfigured() {
            return stateConfigured;
        }

        /**
         * External storage media state string (e.g. {@code mounted}, {@code unmounted}).
         * One of the allowed Android {@code Environment.MEDIA_*} string constants.
         */
        public String getState() {
            return state;
        }

        /** Whether {@code emulated} was present in JSON. */
        public boolean isEmulatedConfigured() {
            return emulatedConfigured;
        }

        public boolean isEmulated() {
            return emulated;
        }

        /** Whether {@code removable} was present in JSON. */
        public boolean isRemovableConfigured() {
            return removableConfigured;
        }

        public boolean isRemovable() {
            return removable;
        }
    }

    /**
     * Immutable view of optional {@code linux.proc} after parse validation.
     * Config-only model for future /proc/self/status|cmdline|cgroup|stat overlays.
     * Never exposes JSONObject/JSONArray. Does not alter {@code linux.uname}/{@code linux.files}.
     */
    public static final class LinuxProcConfig {
        private final String state;
        private final boolean stateConfigured;
        private final int tracerPid;
        private final boolean tracerPidConfigured;
        private final int threadCount;
        private final boolean threadCountConfigured;
        private final List<String> cmdline;
        private final boolean cmdlineConfigured;
        private final List<String> cgroups;
        private final boolean cgroupsConfigured;
        private final long startTimeTicks;
        private final boolean startTimeTicksConfigured;
        private final long virtualMemoryBytes;
        private final boolean virtualMemoryBytesConfigured;
        private final long residentSetPages;
        private final boolean residentSetPagesConfigured;
        private final long rchar;
        private final boolean rcharConfigured;
        private final long wchar;
        private final boolean wcharConfigured;
        private final long syscr;
        private final boolean syscrConfigured;
        private final long syscw;
        private final boolean syscwConfigured;
        private final long readBytes;
        private final boolean readBytesConfigured;
        private final long writeBytes;
        private final boolean writeBytesConfigured;
        private final long cancelledWriteBytes;
        private final boolean cancelledWriteBytesConfigured;
        private final String bootId;
        private final boolean bootIdConfigured;
        private final String randomUuid;
        private final boolean randomUuidConfigured;
        private final int entropyAvail;
        private final boolean entropyAvailConfigured;
        private final int randomPoolSize;
        private final boolean randomPoolSizeConfigured;
        private final int writeWakeupThreshold;
        private final boolean writeWakeupThresholdConfigured;
        private final int urandomMinReseedSecs;
        private final boolean urandomMinReseedSecsConfigured;
        private final int oomScoreAdj;
        private final boolean oomScoreAdjConfigured;
        private final int oomScore;
        private final boolean oomScoreConfigured;
        private final int oomAdj;
        private final boolean oomAdjConfigured;
        private final String selinuxContext;
        private final boolean selinuxContextConfigured;
        /** Path → SELinux file context; empty when absent or empty object. */
        private final Map<String, String> fileSelinuxContexts;
        private final boolean fileSelinuxContextsConfigured;
        private final String comm;
        private final boolean commConfigured;
        private final String wchan;
        private final boolean wchanConfigured;
        private final int dumpable;
        private final boolean dumpableConfigured;
        private final int nice;
        private final boolean niceConfigured;
        private final int seccompMode;
        private final boolean seccompModeConfigured;
        private final boolean noNewPrivs;
        private final boolean noNewPrivsConfigured;
        /** Lowercase 16-hex CapEff mask; null when absent. */
        private final String capEffectiveHex;
        private final boolean capEffectiveHexConfigured;
        /** Lowercase 16-hex CapInh mask; null when absent. Independent of other Cap*. */
        private final String capInheritableHex;
        private final boolean capInheritableHexConfigured;
        /** Lowercase 16-hex CapPrm mask; null when absent. Independent of CapEff. */
        private final String capPermittedHex;
        private final boolean capPermittedHexConfigured;
        /** Lowercase 16-hex CapBnd mask; null when absent. Independent of CapPrm/CapEff. */
        private final String capBoundingHex;
        private final boolean capBoundingHexConfigured;
        /** Lowercase 16-hex CapAmb mask; null when absent. Independent of other Cap lines. */
        private final String capAmbientHex;
        private final boolean capAmbientHexConfigured;
        /** Lowercase 16-hex SigBlk mask; null when absent. */
        private final String signalBlockedHex;
        private final boolean signalBlockedHexConfigured;
        /** Lowercase 16-hex SigIgn mask; null when absent. */
        private final String signalIgnoredHex;
        private final boolean signalIgnoredHexConfigured;
        /** Lowercase 16-hex SigCgt mask; null when absent. */
        private final String signalCaughtHex;
        private final boolean signalCaughtHexConfigured;
        /** Ordered limits lines; empty when absent or explicit {@code []}. */
        private final List<String> limits;
        private final boolean limitsConfigured;

        private LinuxProcConfig(String state, boolean stateConfigured,
                                int tracerPid, boolean tracerPidConfigured,
                                int threadCount, boolean threadCountConfigured,
                                List<String> cmdline, boolean cmdlineConfigured,
                                List<String> cgroups, boolean cgroupsConfigured,
                                long startTimeTicks, boolean startTimeTicksConfigured,
                                long virtualMemoryBytes, boolean virtualMemoryBytesConfigured,
                                long residentSetPages, boolean residentSetPagesConfigured,
                                long rchar, boolean rcharConfigured,
                                long wchar, boolean wcharConfigured,
                                long syscr, boolean syscrConfigured,
                                long syscw, boolean syscwConfigured,
                                long readBytes, boolean readBytesConfigured,
                                long writeBytes, boolean writeBytesConfigured,
                                long cancelledWriteBytes, boolean cancelledWriteBytesConfigured,
                                String bootId, boolean bootIdConfigured,
                                String randomUuid, boolean randomUuidConfigured,
                                int entropyAvail, boolean entropyAvailConfigured,
                                int randomPoolSize, boolean randomPoolSizeConfigured,
                                int writeWakeupThreshold, boolean writeWakeupThresholdConfigured,
                                int urandomMinReseedSecs, boolean urandomMinReseedSecsConfigured,
                                int oomScoreAdj, boolean oomScoreAdjConfigured,
                                int oomScore, boolean oomScoreConfigured,
                                int oomAdj, boolean oomAdjConfigured,
                                String selinuxContext, boolean selinuxContextConfigured,
                                Map<String, String> fileSelinuxContexts,
                                boolean fileSelinuxContextsConfigured,
                                String comm, boolean commConfigured,
                                String wchan, boolean wchanConfigured,
                                int dumpable, boolean dumpableConfigured,
                                int nice, boolean niceConfigured,
                                int seccompMode, boolean seccompModeConfigured,
                                boolean noNewPrivs, boolean noNewPrivsConfigured,
                                String capEffectiveHex, boolean capEffectiveHexConfigured,
                                String capInheritableHex, boolean capInheritableHexConfigured,
                                String capPermittedHex, boolean capPermittedHexConfigured,
                                String capBoundingHex, boolean capBoundingHexConfigured,
                                String capAmbientHex, boolean capAmbientHexConfigured,
                                String signalBlockedHex, boolean signalBlockedHexConfigured,
                                String signalIgnoredHex, boolean signalIgnoredHexConfigured,
                                String signalCaughtHex, boolean signalCaughtHexConfigured,
                                List<String> limits, boolean limitsConfigured) {
            this.state = state;
            this.stateConfigured = stateConfigured;
            this.tracerPid = tracerPid;
            this.tracerPidConfigured = tracerPidConfigured;
            this.threadCount = threadCount;
            this.threadCountConfigured = threadCountConfigured;
            this.cmdline = cmdline;
            this.cmdlineConfigured = cmdlineConfigured;
            this.cgroups = cgroups;
            this.cgroupsConfigured = cgroupsConfigured;
            this.startTimeTicks = startTimeTicks;
            this.startTimeTicksConfigured = startTimeTicksConfigured;
            this.virtualMemoryBytes = virtualMemoryBytes;
            this.virtualMemoryBytesConfigured = virtualMemoryBytesConfigured;
            this.residentSetPages = residentSetPages;
            this.residentSetPagesConfigured = residentSetPagesConfigured;
            this.rchar = rchar;
            this.rcharConfigured = rcharConfigured;
            this.wchar = wchar;
            this.wcharConfigured = wcharConfigured;
            this.syscr = syscr;
            this.syscrConfigured = syscrConfigured;
            this.syscw = syscw;
            this.syscwConfigured = syscwConfigured;
            this.readBytes = readBytes;
            this.readBytesConfigured = readBytesConfigured;
            this.writeBytes = writeBytes;
            this.writeBytesConfigured = writeBytesConfigured;
            this.cancelledWriteBytes = cancelledWriteBytes;
            this.cancelledWriteBytesConfigured = cancelledWriteBytesConfigured;
            this.bootId = bootId;
            this.bootIdConfigured = bootIdConfigured;
            this.randomUuid = randomUuid;
            this.randomUuidConfigured = randomUuidConfigured;
            this.entropyAvail = entropyAvail;
            this.entropyAvailConfigured = entropyAvailConfigured;
            this.randomPoolSize = randomPoolSize;
            this.randomPoolSizeConfigured = randomPoolSizeConfigured;
            this.writeWakeupThreshold = writeWakeupThreshold;
            this.writeWakeupThresholdConfigured = writeWakeupThresholdConfigured;
            this.urandomMinReseedSecs = urandomMinReseedSecs;
            this.urandomMinReseedSecsConfigured = urandomMinReseedSecsConfigured;
            this.oomScoreAdj = oomScoreAdj;
            this.oomScoreAdjConfigured = oomScoreAdjConfigured;
            this.oomScore = oomScore;
            this.oomScoreConfigured = oomScoreConfigured;
            this.oomAdj = oomAdj;
            this.oomAdjConfigured = oomAdjConfigured;
            this.selinuxContext = selinuxContext;
            this.selinuxContextConfigured = selinuxContextConfigured;
            this.fileSelinuxContexts = fileSelinuxContexts;
            this.fileSelinuxContextsConfigured = fileSelinuxContextsConfigured;
            this.comm = comm;
            this.commConfigured = commConfigured;
            this.wchan = wchan;
            this.wchanConfigured = wchanConfigured;
            this.dumpable = dumpable;
            this.dumpableConfigured = dumpableConfigured;
            this.nice = nice;
            this.niceConfigured = niceConfigured;
            this.seccompMode = seccompMode;
            this.seccompModeConfigured = seccompModeConfigured;
            this.noNewPrivs = noNewPrivs;
            this.noNewPrivsConfigured = noNewPrivsConfigured;
            this.capEffectiveHex = capEffectiveHex;
            this.capEffectiveHexConfigured = capEffectiveHexConfigured;
            this.capInheritableHex = capInheritableHex;
            this.capInheritableHexConfigured = capInheritableHexConfigured;
            this.capPermittedHex = capPermittedHex;
            this.capPermittedHexConfigured = capPermittedHexConfigured;
            this.capBoundingHex = capBoundingHex;
            this.capBoundingHexConfigured = capBoundingHexConfigured;
            this.capAmbientHex = capAmbientHex;
            this.capAmbientHexConfigured = capAmbientHexConfigured;
            this.signalBlockedHex = signalBlockedHex;
            this.signalBlockedHexConfigured = signalBlockedHexConfigured;
            this.signalIgnoredHex = signalIgnoredHex;
            this.signalIgnoredHexConfigured = signalIgnoredHexConfigured;
            this.signalCaughtHex = signalCaughtHex;
            this.signalCaughtHexConfigured = signalCaughtHexConfigured;
            this.limits = limits;
            this.limitsConfigured = limitsConfigured;
        }

        public boolean isStateConfigured() {
            return stateConfigured;
        }

        /** Process state letter; defaults to {@code S} when absent. */
        public String getState() {
            return state;
        }

        public boolean isTracerPidConfigured() {
            return tracerPidConfigured;
        }

        /** Tracer pid; defaults to {@code 0} when absent. */
        public int getTracerPid() {
            return tracerPid;
        }

        public boolean isThreadCountConfigured() {
            return threadCountConfigured;
        }

        /** Thread count; defaults to {@code 1} when absent. */
        public int getThreadCount() {
            return threadCount;
        }

        public boolean isCmdlineConfigured() {
            return cmdlineConfigured;
        }

        /**
         * Cmdline argv list when the key is present (may be empty). Empty immutable list when
         * the key is absent — callers should treat {@link #isCmdlineConfigured()} as whether to
         * use this list vs derive from process name later.
         */
        public List<String> getCmdline() {
            return cmdline;
        }

        public boolean isCgroupsConfigured() {
            return cgroupsConfigured;
        }

        /**
         * Cgroup lines when the key is present (may be empty). Empty immutable list when absent —
         * callers may default to {@code 0::/} later when not configured.
         */
        public List<String> getCgroups() {
            return cgroups;
        }

        public boolean isStartTimeTicksConfigured() {
            return startTimeTicksConfigured;
        }

        /** Start time in clock ticks; defaults to {@code 0} when absent. */
        public long getStartTimeTicks() {
            return startTimeTicks;
        }

        public boolean isVirtualMemoryBytesConfigured() {
            return virtualMemoryBytesConfigured;
        }

        /** Virtual memory size in bytes; defaults to {@code 0} when absent. */
        public long getVirtualMemoryBytes() {
            return virtualMemoryBytes;
        }

        public boolean isResidentSetPagesConfigured() {
            return residentSetPagesConfigured;
        }

        /** Resident set size in pages; defaults to {@code 0} when absent. */
        public long getResidentSetPages() {
            return residentSetPages;
        }

        public boolean isRcharConfigured() {
            return rcharConfigured;
        }

        /** Characters read (rchar); defaults to {@code 0} when absent. */
        public long getRchar() {
            return rchar;
        }

        public boolean isWcharConfigured() {
            return wcharConfigured;
        }

        /** Characters written (wchar); defaults to {@code 0} when absent. */
        public long getWchar() {
            return wchar;
        }

        public boolean isSyscrConfigured() {
            return syscrConfigured;
        }

        /** Read syscalls (syscr); defaults to {@code 0} when absent. */
        public long getSyscr() {
            return syscr;
        }

        public boolean isSyscwConfigured() {
            return syscwConfigured;
        }

        /** Write syscalls (syscw); defaults to {@code 0} when absent. */
        public long getSyscw() {
            return syscw;
        }

        public boolean isReadBytesConfigured() {
            return readBytesConfigured;
        }

        /** Bytes read from storage (read_bytes); defaults to {@code 0} when absent. */
        public long getReadBytes() {
            return readBytes;
        }

        public boolean isWriteBytesConfigured() {
            return writeBytesConfigured;
        }

        /** Bytes written to storage (write_bytes); defaults to {@code 0} when absent. */
        public long getWriteBytes() {
            return writeBytes;
        }

        public boolean isCancelledWriteBytesConfigured() {
            return cancelledWriteBytesConfigured;
        }

        /**
         * Cancelled write bytes (cancelled_write_bytes); defaults to {@code 0} when absent.
         */
        public long getCancelledWriteBytes() {
            return cancelledWriteBytes;
        }

        public boolean isBootIdConfigured() {
            return bootIdConfigured;
        }

        /**
         * Kernel {@code /proc/sys/kernel/random/boot_id} UUID (canonical lowercase), or
         * {@code null} when the key is absent. Distinct from {@code random.uuid} and
         * {@link #getRandomUuid()}; never inferred or generated.
         */
        public String getBootId() {
            return bootId;
        }

        public boolean isRandomUuidConfigured() {
            return randomUuidConfigured;
        }

        /**
         * Kernel {@code /proc/sys/kernel/random/uuid} fixed marker UUID (canonical lowercase),
         * or {@code null} when the key is absent. Distinct from {@code random.uuid} and
         * {@link #getBootId()}; never inferred, generated, or rotated per read.
         */
        public String getRandomUuid() {
            return randomUuid;
        }

        public boolean isEntropyAvailConfigured() {
            return entropyAvailConfigured;
        }

        /**
         * Kernel {@code /proc/sys/kernel/random/entropy_avail} value in range
         * {@code 0..Integer.MAX_VALUE}, or {@code 0} when the key is absent. Callers must use
         * {@link #isEntropyAvailConfigured()} — no default inference for open/render.
         * Independent of {@link #getRandomPoolSize()}, {@link #getWriteWakeupThreshold()},
         * {@link #getUrandomMinReseedSecs()}, {@link #getBootId()}, and
         * {@link #getRandomUuid()}; never read from the host entropy pool.
         */
        public int getEntropyAvail() {
            return entropyAvail;
        }

        public boolean isRandomPoolSizeConfigured() {
            return randomPoolSizeConfigured;
        }

        /**
         * Kernel {@code /proc/sys/kernel/random/poolsize} value in range
         * {@code 0..Integer.MAX_VALUE}, or {@code 0} when the key is absent. Callers must use
         * {@link #isRandomPoolSizeConfigured()} — no default inference for open/render.
         * Independent of {@link #getEntropyAvail()}, {@link #getWriteWakeupThreshold()},
         * {@link #getUrandomMinReseedSecs()}, {@link #getBootId()}, and
         * {@link #getRandomUuid()}; never read from the host entropy pool.
         */
        public int getRandomPoolSize() {
            return randomPoolSize;
        }

        public boolean isWriteWakeupThresholdConfigured() {
            return writeWakeupThresholdConfigured;
        }

        /**
         * Kernel {@code /proc/sys/kernel/random/write_wakeup_threshold} value in range
         * {@code 0..Integer.MAX_VALUE}, or {@code 0} when the key is absent. Callers must use
         * {@link #isWriteWakeupThresholdConfigured()} — no default inference for open/render.
         * Independent of {@link #getEntropyAvail()}, {@link #getRandomPoolSize()},
         * {@link #getUrandomMinReseedSecs()}, {@link #getBootId()}, and
         * {@link #getRandomUuid()}; never read from the host entropy pool.
         */
        public int getWriteWakeupThreshold() {
            return writeWakeupThreshold;
        }

        public boolean isUrandomMinReseedSecsConfigured() {
            return urandomMinReseedSecsConfigured;
        }

        /**
         * Kernel {@code /proc/sys/kernel/random/urandom_min_reseed_secs} value in range
         * {@code 0..Integer.MAX_VALUE}, or {@code 0} when the key is absent. Callers must use
         * {@link #isUrandomMinReseedSecsConfigured()} — no default inference for open/render.
         * Independent of {@link #getEntropyAvail()}, {@link #getRandomPoolSize()},
         * {@link #getWriteWakeupThreshold()}, {@link #getBootId()}, and
         * {@link #getRandomUuid()}; never read from the host entropy pool.
         */
        public int getUrandomMinReseedSecs() {
            return urandomMinReseedSecs;
        }

        public boolean isOomScoreAdjConfigured() {
            return oomScoreAdjConfigured;
        }

        /**
         * {@code /proc/self|pid/oom_score_adj} value in range {@code -1000..1000}, or
         * {@code 0} when the key is absent. Callers must use
         * {@link #isOomScoreAdjConfigured()} — no default inference for open/render.
         * Independent of {@link #getOomScore()}.
         */
        public int getOomScoreAdj() {
            return oomScoreAdj;
        }

        public boolean isOomScoreConfigured() {
            return oomScoreConfigured;
        }

        /**
         * {@code /proc/self|pid/oom_score} value in range {@code 0..2000}, or {@code 0} when the
         * key is absent. Callers must use {@link #isOomScoreConfigured()} — no default inference
         * and never derived from {@link #getOomScoreAdj()}.
         */
        public int getOomScore() {
            return oomScore;
        }

        public boolean isOomAdjConfigured() {
            return oomAdjConfigured;
        }

        /**
         * Legacy {@code /proc/self|pid/oom_adj} value: {@code -17} or {@code -16..15},
         * or {@code 0} when the key is absent. Callers must use
         * {@link #isOomAdjConfigured()} — no default inference for open/render.
         * Independent of {@link #getOomScoreAdj()} and {@link #getOomScore()}; never
         * converted from or into those fields.
         */
        public int getOomAdj() {
            return oomAdj;
        }

        public boolean isSelinuxContextConfigured() {
            return selinuxContextConfigured;
        }

        /**
         * SELinux context for {@code /proc/self|pid/attr/current}, or {@code null} when the key
         * is absent. Never inferred or generated.
         */
        public String getSelinuxContext() {
            return selinuxContext;
        }

        /**
         * Whether {@code fileSelinuxContexts} was explicitly present under {@code linux.proc}
         * (including an empty object). Key omission leaves this false (no default).
         */
        public boolean isFileSelinuxContextsConfigured() {
            return fileSelinuxContextsConfigured;
        }

        /**
         * Immutable path → file SELinux context map (normalized absolute paths). Empty when the
         * key is absent or configured as {@code {}}. Never inferred from {@link #getSelinuxContext()}.
         */
        public Map<String, String> getFileSelinuxContexts() {
            return fileSelinuxContexts;
        }

        /**
         * Lookup configured file context for {@code path}: exact key first, then POSIX absolute
         * path normalization. Returns {@code null} when the map is absent, path is null/empty,
         * path is not absolute/normalizable, or the path is not a configured key.
         */
        public String lookupFileSelinuxContext(String path) {
            if (!fileSelinuxContextsConfigured || path == null || path.isEmpty()) {
                return null;
            }
            String direct = fileSelinuxContexts.get(path);
            if (direct != null) {
                return direct;
            }
            try {
                String normalized = normalizePosixAbsolutePath(path, "linux.proc.fileSelinuxContexts");
                return fileSelinuxContexts.get(normalized);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        public boolean isCommConfigured() {
            return commConfigured;
        }

        /**
         * Task {@code comm} for {@code /proc/self|pid/comm} and matching
         * {@code .../task/<tid>/comm}, or {@code null} when the key is absent.
         * Never inferred from processName/threadName.
         */
        public String getComm() {
            return comm;
        }

        public boolean isWchanConfigured() {
            return wchanConfigured;
        }

        /**
         * Wait channel marker for {@code /proc/self|pid/wchan} and matching
         * {@code .../task/<tid>/wchan}, or {@code null} when the key is absent.
         * Fixed analysis string only — never inferred or derived from scheduler state.
         */
        public String getWchan() {
            return wchan;
        }

        public boolean isDumpableConfigured() {
            return dumpableConfigured;
        }

        /**
         * Process dumpable flag for {@code prctl(PR_GET_DUMPABLE)} in range {@code 0..2}, or
         * {@code 0} when the key is absent. Callers must use {@link #isDumpableConfigured()} —
         * no default inference; never mutates set-dumpable state.
         */
        public int getDumpable() {
            return dumpable;
        }

        /**
         * Whether {@code nice} was explicitly present under {@code linux.proc}.
         * Key omission leaves this false (no default; independent of other fields).
         */
        public boolean isNiceConfigured() {
            return niceConfigured;
        }

        /**
         * Process nice value for {@code getpriority} and {@code /proc/self|pid/stat}
         * fields 18/19, in range {@code -20..19}, or {@code 0} when the key is absent.
         * Callers must use {@link #isNiceConfigured()} — no default inference; never
         * mutates {@code setpriority} or scheduler state.
         */
        public int getNice() {
            return nice;
        }

        /**
         * Whether {@code seccompMode} was explicitly present under {@code linux.proc}.
         * Key omission leaves this false (no default; independent of dumpable and other fields).
         */
        public boolean isSeccompModeConfigured() {
            return seccompModeConfigured;
        }

        /**
         * Seccomp mode for {@code prctl(PR_GET_SECCOMP)} in range {@code 0..2}, or {@code 0}
         * when the key is absent. Callers must use {@link #isSeccompModeConfigured()} —
         * no default inference; does not simulate BPF filters, {@code PR_SET_SECCOMP}, or
         * seccomp syscalls.
         */
        public int getSeccompMode() {
            return seccompMode;
        }

        /**
         * Whether {@code noNewPrivs} was explicitly present under {@code linux.proc}.
         * Key omission leaves this false (no default; independent of seccompMode/dumpable).
         */
        public boolean isNoNewPrivsConfigured() {
            return noNewPrivsConfigured;
        }

        /**
         * Configured {@code no_new_privs} flag for {@code prctl(PR_GET_NO_NEW_PRIVS)}, or
         * {@code false} when the key is absent. Callers must use {@link #isNoNewPrivsConfigured()}.
         * Does not derive from seccomp; does not alter {@code PR_SET_NO_NEW_PRIVS}.
         */
        public boolean isNoNewPrivs() {
            return noNewPrivs;
        }

        /**
         * Whether {@code capEffectiveHex} was explicitly present under {@code linux.proc}.
         * Key omission leaves this false (no default; independent of CapPrm and other Cap*).
         */
        public boolean isCapEffectiveHexConfigured() {
            return capEffectiveHexConfigured;
        }

        /**
         * Effective capability mask for {@code /proc/self|pid/status} {@code CapEff} line:
         * fixed-width lowercase 16 hex digits, or {@code null} when the key is absent.
         * Never derived from capget/host; independent of CapInh/CapPrm/CapBnd/CapAmb.
         */
        public String getCapEffectiveHex() {
            return capEffectiveHex;
        }

        /**
         * Whether {@code capInheritableHex} was explicitly present under {@code linux.proc}.
         * Key omission leaves this false (no default; independent of other Cap*).
         */
        public boolean isCapInheritableHexConfigured() {
            return capInheritableHexConfigured;
        }

        /**
         * Inheritable capability mask for {@code /proc/self|pid/status} {@code CapInh} line:
         * fixed-width lowercase 16 hex digits, or {@code null} when the key is absent.
         * Never derived from other Cap lines, capget, or host; independent of CapPrm/CapEff/CapBnd/CapAmb.
         */
        public String getCapInheritableHex() {
            return capInheritableHex;
        }

        /**
         * Whether {@code capPermittedHex} was explicitly present under {@code linux.proc}.
         * Key omission leaves this false (no default; independent of CapEff and other Cap*).
         */
        public boolean isCapPermittedHexConfigured() {
            return capPermittedHexConfigured;
        }

        /**
         * Permitted capability mask for {@code /proc/self|pid/status} {@code CapPrm} line:
         * fixed-width lowercase 16 hex digits, or {@code null} when the key is absent.
         * Never derived from other Cap lines, capget, or host; independent of CapInh/CapEff/CapBnd/CapAmb.
         */
        public String getCapPermittedHex() {
            return capPermittedHex;
        }

        /**
         * Whether {@code capBoundingHex} was explicitly present under {@code linux.proc}.
         * Key omission leaves this false (no default; independent of CapPrm/CapEff).
         */
        public boolean isCapBoundingHexConfigured() {
            return capBoundingHexConfigured;
        }

        /**
         * Bounding capability mask for {@code /proc/self|pid/status} {@code CapBnd} line:
         * fixed-width lowercase 16 hex digits, or {@code null} when the key is absent.
         * Never derived from other Cap lines, capget, or host; independent of CapInh/CapPrm/CapEff/CapAmb.
         */
        public String getCapBoundingHex() {
            return capBoundingHex;
        }

        /**
         * Whether {@code capAmbientHex} was explicitly present under {@code linux.proc}.
         * Key omission leaves this false (no default; independent of other Cap lines).
         */
        public boolean isCapAmbientHexConfigured() {
            return capAmbientHexConfigured;
        }

        /**
         * Ambient capability mask for {@code /proc/self|pid/status} {@code CapAmb} line:
         * fixed-width lowercase 16 hex digits, or {@code null} when the key is absent.
         * Never derived from other Cap lines, capget, host, or prctl ambient changes.
         */
        public String getCapAmbientHex() {
            return capAmbientHex;
        }

        /**
         * Whether {@code signalBlockedHex} was explicitly present under {@code linux.proc}.
         * Key omission leaves this false (no default; independent of other Sig* fields).
         */
        public boolean isSignalBlockedHexConfigured() {
            return signalBlockedHexConfigured;
        }

        /**
         * Blocked signal mask for {@code /proc/self|pid/status} {@code SigBlk} line:
         * fixed-width lowercase 16 hex digits, or {@code null} when absent.
         * Status marker only — never delivers signals or mutates sigprocmask state.
         */
        public String getSignalBlockedHex() {
            return signalBlockedHex;
        }

        /**
         * Whether {@code signalIgnoredHex} was explicitly present under {@code linux.proc}.
         * Key omission leaves this false (no default; independent of other Sig* fields).
         */
        public boolean isSignalIgnoredHexConfigured() {
            return signalIgnoredHexConfigured;
        }

        /**
         * Ignored signal mask for {@code /proc/self|pid/status} {@code SigIgn} line:
         * fixed-width lowercase 16 hex digits, or {@code null} when absent.
         * Status marker only — never implements SIG_IGN delivery or sigaction.
         */
        public String getSignalIgnoredHex() {
            return signalIgnoredHex;
        }

        /**
         * Whether {@code signalCaughtHex} was explicitly present under {@code linux.proc}.
         * Key omission leaves this false (no default; independent of other Sig* fields).
         */
        public boolean isSignalCaughtHexConfigured() {
            return signalCaughtHexConfigured;
        }

        /**
         * Caught signal mask for {@code /proc/self|pid/status} {@code SigCgt} line:
         * fixed-width lowercase 16 hex digits, or {@code null} when absent.
         * Status marker only — never implements handlers, sigaction, or pthread_sigmask.
         */
        public String getSignalCaughtHex() {
            return signalCaughtHex;
        }

        /**
         * Whether {@code limits} was explicitly present under {@code linux.proc}
         * (including an empty array). Key omission leaves this false (no default;
         * no getrlimit/setrlimit/prlimit enforcement).
         */
        public boolean isLimitsConfigured() {
            return limitsConfigured;
        }

        /**
         * Ordered {@code /proc/self|pid/limits} lines when the key is present (may be empty).
         * Empty immutable list when absent — callers must use {@link #isLimitsConfigured()}.
         * Fixed analysis content only.
         */
        public List<String> getLimits() {
            return limits;
        }
    }

    /**
     * Immutable view of optional {@code linux.auxv} after parse validation.
     * Never exposes JSONObject/JSONArray or mutable shared state.
     */
    public static final class LinuxAuxvConfig {
        private final long hwcap32;
        private final boolean hwcap32Configured;
        private final long hwcap2_32;
        private final boolean hwcap2_32Configured;
        private final long hwcap64;
        private final boolean hwcap64Configured;
        private final long hwcap2_64;
        private final boolean hwcap2_64Configured;
        private final String platform32;
        private final boolean platform32Configured;
        private final String platform64;
        private final boolean platform64Configured;
        private final String execFn;
        private final boolean execFnConfigured;

        private LinuxAuxvConfig(long hwcap32, boolean hwcap32Configured,
                                long hwcap2_32, boolean hwcap2_32Configured,
                                long hwcap64, boolean hwcap64Configured,
                                long hwcap2_64, boolean hwcap2_64Configured,
                                String platform32, boolean platform32Configured,
                                String platform64, boolean platform64Configured,
                                String execFn, boolean execFnConfigured) {
            this.hwcap32 = hwcap32;
            this.hwcap32Configured = hwcap32Configured;
            this.hwcap2_32 = hwcap2_32;
            this.hwcap2_32Configured = hwcap2_32Configured;
            this.hwcap64 = hwcap64;
            this.hwcap64Configured = hwcap64Configured;
            this.hwcap2_64 = hwcap2_64;
            this.hwcap2_64Configured = hwcap2_64Configured;
            this.platform32 = platform32;
            this.platform32Configured = platform32Configured;
            this.platform64 = platform64;
            this.platform64Configured = platform64Configured;
            this.execFn = execFn;
            this.execFnConfigured = execFnConfigured;
        }

        public boolean isHwcap32Configured() {
            return hwcap32Configured;
        }

        /** AT_HWCAP for 32-bit; defaults to {@code 0} when absent. */
        public long getHwcap32() {
            return hwcap32;
        }

        public boolean isHwcap2_32Configured() {
            return hwcap2_32Configured;
        }

        /** AT_HWCAP2 for 32-bit; defaults to {@code 0} when absent. */
        public long getHwcap2_32() {
            return hwcap2_32;
        }

        public boolean isHwcap64Configured() {
            return hwcap64Configured;
        }

        /** AT_HWCAP for 64-bit; defaults to {@code 0} when absent. */
        public long getHwcap64() {
            return hwcap64;
        }

        public boolean isHwcap2_64Configured() {
            return hwcap2_64Configured;
        }

        /** AT_HWCAP2 for 64-bit; defaults to {@code 0} when absent. */
        public long getHwcap2_64() {
            return hwcap2_64;
        }

        public boolean isPlatform32Configured() {
            return platform32Configured;
        }

        /** AT_PLATFORM for 32-bit; defaults to {@code v7l} when absent. */
        public String getPlatform32() {
            return platform32;
        }

        public boolean isPlatform64Configured() {
            return platform64Configured;
        }

        /** AT_PLATFORM for 64-bit; defaults to {@code aarch64} when absent. */
        public String getPlatform64() {
            return platform64;
        }

        public boolean isExecFnConfigured() {
            return execFnConfigured;
        }

        /**
         * AT_EXECFN string, or {@code null} when the key is absent or explicitly JSON null.
         * When not configured, loaders may fall back to the process name.
         */
        public String getExecFn() {
            return execFn;
        }
    }

    /**
     * Immutable view of optional {@code linux.cpu} after parse validation (affinity + optional
     * sysfs CPU-list subset + optional sysconf processor counts). Never exposes JSONObject/JSONArray
     * or mutable shared state. {@code online}/{@code offline}/{@code present}/{@code possible},
     * {@code affinityMaskHex}, {@code configuredProcessorCount}, and {@code onlineProcessorCount}
     * are independent of each other (never cross-derived).
     */
    public static final class LinuxCpuConfig {
        private final byte[] affinityMask;
        private final boolean affinityMaskConfigured;
        private final String online;
        private final boolean onlineConfigured;
        private final String offline;
        private final boolean offlineConfigured;
        private final String present;
        private final boolean presentConfigured;
        private final String possible;
        private final boolean possibleConfigured;
        private final int configuredProcessorCount;
        private final boolean configuredProcessorCountConfigured;
        private final int onlineProcessorCount;
        private final boolean onlineProcessorCountConfigured;

        private LinuxCpuConfig(byte[] affinityMask, boolean affinityMaskConfigured,
                               String online, boolean onlineConfigured,
                               String offline, boolean offlineConfigured,
                               String present, boolean presentConfigured,
                               String possible, boolean possibleConfigured,
                               int configuredProcessorCount, boolean configuredProcessorCountConfigured,
                               int onlineProcessorCount, boolean onlineProcessorCountConfigured) {
            this.affinityMask = affinityMask;
            this.affinityMaskConfigured = affinityMaskConfigured;
            this.online = online;
            this.onlineConfigured = onlineConfigured;
            this.offline = offline;
            this.offlineConfigured = offlineConfigured;
            this.present = present;
            this.presentConfigured = presentConfigured;
            this.possible = possible;
            this.possibleConfigured = possibleConfigured;
            this.configuredProcessorCount = configuredProcessorCount;
            this.configuredProcessorCountConfigured = configuredProcessorCountConfigured;
            this.onlineProcessorCount = onlineProcessorCount;
            this.onlineProcessorCountConfigured = onlineProcessorCountConfigured;
        }

        public boolean isAffinityMaskConfigured() {
            return affinityMaskConfigured;
        }

        /**
         * Defensive copy of decoded affinity mask bytes from {@code affinityMaskHex}.
         * Never null when the config node is present (defaults to a single {@code 0x01} byte).
         */
        public byte[] getAffinityMaskBytes() {
            return Arrays.copyOf(affinityMask, affinityMask.length);
        }

        public boolean isOnlineConfigured() {
            return onlineConfigured;
        }

        /** Canonical CPU-list text for {@code /sys/devices/system/cpu/online}, or null if omitted. */
        public String getOnline() {
            return online;
        }

        public boolean isOfflineConfigured() {
            return offlineConfigured;
        }

        /** Canonical CPU-list text for {@code /sys/devices/system/cpu/offline}, or null if omitted. */
        public String getOffline() {
            return offline;
        }

        public boolean isPresentConfigured() {
            return presentConfigured;
        }

        /** Canonical CPU-list text for {@code /sys/devices/system/cpu/present}, or null if omitted. */
        public String getPresent() {
            return present;
        }

        public boolean isPossibleConfigured() {
            return possibleConfigured;
        }

        /** Canonical CPU-list text for {@code /sys/devices/system/cpu/possible}, or null if omitted. */
        public String getPossible() {
            return possible;
        }

        /**
         * Whether {@code configuredProcessorCount} was explicitly present under {@code linux.cpu}.
         * Key omission leaves this false (no default; not inferred from CPU-lists or affinity).
         */
        public boolean isConfiguredProcessorCountConfigured() {
            return configuredProcessorCountConfigured;
        }

        /**
         * Configured value for {@code sysconf(_SC_NPROCESSORS_CONF)}, range {@code 1..4096}.
         * Meaningful only when {@link #isConfiguredProcessorCountConfigured()} is true.
         */
        public int getConfiguredProcessorCount() {
            return configuredProcessorCount;
        }

        /**
         * Whether {@code onlineProcessorCount} was explicitly present under {@code linux.cpu}.
         * Key omission leaves this false (no default; not inferred from CPU-lists or affinity).
         */
        public boolean isOnlineProcessorCountConfigured() {
            return onlineProcessorCountConfigured;
        }

        /**
         * Configured value for {@code sysconf(_SC_NPROCESSORS_ONLN)}, range {@code 1..4096}.
         * Meaningful only when {@link #isOnlineProcessorCountConfigured()} is true.
         */
        public int getOnlineProcessorCount() {
            return onlineProcessorCount;
        }
    }

    /**
     * Immutable view of optional {@code linux.rlimits} after parse validation.
     * Currently only optional {@code nofile} ({@code RLIMIT_NOFILE}); never exposes JSONObject
     * or invents defaults when {@code nofile} is omitted.
     */
    public static final class LinuxRlimitsConfig {
        private final boolean nofileConfigured;
        private final long nofileSoft;
        private final long nofileHard;

        private LinuxRlimitsConfig(boolean nofileConfigured, long nofileSoft, long nofileHard) {
            this.nofileConfigured = nofileConfigured;
            this.nofileSoft = nofileSoft;
            this.nofileHard = nofileHard;
        }

        /**
         * Whether {@code linux.rlimits.nofile} was explicitly present.
         * Key omission leaves this false (no default).
         */
        public boolean isNofileConfigured() {
            return nofileConfigured;
        }

        /**
         * Soft limit ({@code rlim_cur}) for {@code RLIMIT_NOFILE}.
         * Meaningful only when {@link #isNofileConfigured()} is true.
         */
        public long getNofileSoft() {
            return nofileSoft;
        }

        /**
         * Hard limit ({@code rlim_max}) for {@code RLIMIT_NOFILE}.
         * Meaningful only when {@link #isNofileConfigured()} is true.
         */
        public long getNofileHard() {
            return nofileHard;
        }
    }

    /**
     * Immutable view of optional {@code linux.sysinfo} after parse validation.
     * All whitelist fields are required when the node is present. Never exposes
     * JSONObject/JSONArray. Values are never inferred from {@code /proc/meminfo},
     * {@code android.runtime} memory, or the host.
     */
    public static final class LinuxSysinfoConfig {
        private final long uptime;
        private final long[] loads;
        private final long totalRam;
        private final long freeRam;
        private final long sharedRam;
        private final long bufferRam;
        private final long totalSwap;
        private final long freeSwap;
        private final int procs;
        private final long memUnit;

        private LinuxSysinfoConfig(long uptime, long[] loads, long totalRam, long freeRam,
                                   long sharedRam, long bufferRam, long totalSwap, long freeSwap,
                                   int procs, long memUnit) {
            this.uptime = uptime;
            this.loads = loads;
            this.totalRam = totalRam;
            this.freeRam = freeRam;
            this.sharedRam = sharedRam;
            this.bufferRam = bufferRam;
            this.totalSwap = totalSwap;
            this.freeSwap = freeSwap;
            this.procs = procs;
            this.memUnit = memUnit;
        }

        /** Seconds since boot ({@code sysinfo.uptime}). */
        public long getUptime() {
            return uptime;
        }

        /**
         * 1 / 5 / 15 minute load averages ({@code sysinfo.loads}), SI_LOAD_SHIFT=16 units.
         * Defensive copy; never null.
         */
        public long[] getLoads() {
            return Arrays.copyOf(loads, loads.length);
        }

        public long getTotalRam() {
            return totalRam;
        }

        public long getFreeRam() {
            return freeRam;
        }

        public long getSharedRam() {
            return sharedRam;
        }

        public long getBufferRam() {
            return bufferRam;
        }

        public long getTotalSwap() {
            return totalSwap;
        }

        public long getFreeSwap() {
            return freeSwap;
        }

        /** Process count ({@code sysinfo.procs}), range {@code 0..65535}. */
        public int getProcs() {
            return procs;
        }

        /** Memory unit in bytes ({@code sysinfo.mem_unit}), ARM32 {@code __u32} range {@code 1..4294967295}. */
        public long getMemUnit() {
            return memUnit;
        }

        /**
         * Whether every numeric field fits ARM32 C {@code struct sysinfo}:
         * signed 32-bit {@code uptime}, unsigned 32-bit loads/RAM/swap, {@code __u32} {@code mem_unit}.
         * Public JSON is validated to this range so a valid config never needs truncation.
         */
        public boolean fitsArm32NativeFields() {
            if (uptime < 0L || uptime > Integer.MAX_VALUE) {
                return false;
            }
            if (loads == null || loads.length != 3) {
                return false;
            }
            for (int i = 0; i < loads.length; i++) {
                if (!fitsArm32Unsigned32(loads[i])) {
                    return false;
                }
            }
            return fitsArm32Unsigned32(totalRam)
                    && fitsArm32Unsigned32(freeRam)
                    && fitsArm32Unsigned32(sharedRam)
                    && fitsArm32Unsigned32(bufferRam)
                    && fitsArm32Unsigned32(totalSwap)
                    && fitsArm32Unsigned32(freeSwap)
                    && procs >= 0 && procs <= 65535
                    && memUnit >= 1L && memUnit <= LINUX_SYSINFO_U32_MAX;
        }
    }

    /** ARM32 {@code __kernel_ulong_t} / {@code __u32} inclusive max. */
    public static final long LINUX_SYSINFO_U32_MAX = 4294967295L;

    public static boolean fitsArm32Unsigned32(long value) {
        return value >= 0L && value <= LINUX_SYSINFO_U32_MAX;
    }

    /**
     * Immutable view of optional {@code android.display} after parse validation (v1 metrics subset).
     * Never exposes JSONObject/JSONArray or mutable shared state.
     */
    public static final class AndroidDisplayConfig {
        private final int widthPixels;
        private final boolean widthPixelsConfigured;
        private final int heightPixels;
        private final boolean heightPixelsConfigured;
        private final int densityDpi;
        private final boolean densityDpiConfigured;
        private final float scaledDensity;
        private final boolean scaledDensityConfigured;
        private final float xdpi;
        private final boolean xdpiConfigured;
        private final float ydpi;
        private final boolean ydpiConfigured;
        private final float refreshRate;
        private final boolean refreshRateConfigured;
        private final int rotation;
        private final boolean rotationConfigured;
        private final int modeId;
        private final boolean modeIdConfigured;
        private final String uniqueId;
        private final boolean uniqueIdConfigured;

        private AndroidDisplayConfig(int widthPixels, boolean widthPixelsConfigured,
                                     int heightPixels, boolean heightPixelsConfigured,
                                     int densityDpi, boolean densityDpiConfigured,
                                     float scaledDensity, boolean scaledDensityConfigured,
                                     float xdpi, boolean xdpiConfigured,
                                     float ydpi, boolean ydpiConfigured,
                                     float refreshRate, boolean refreshRateConfigured,
                                     int rotation, boolean rotationConfigured,
                                     int modeId, boolean modeIdConfigured,
                                     String uniqueId, boolean uniqueIdConfigured) {
            this.widthPixels = widthPixels;
            this.widthPixelsConfigured = widthPixelsConfigured;
            this.heightPixels = heightPixels;
            this.heightPixelsConfigured = heightPixelsConfigured;
            this.densityDpi = densityDpi;
            this.densityDpiConfigured = densityDpiConfigured;
            this.scaledDensity = scaledDensity;
            this.scaledDensityConfigured = scaledDensityConfigured;
            this.xdpi = xdpi;
            this.xdpiConfigured = xdpiConfigured;
            this.ydpi = ydpi;
            this.ydpiConfigured = ydpiConfigured;
            this.refreshRate = refreshRate;
            this.refreshRateConfigured = refreshRateConfigured;
            this.rotation = rotation;
            this.rotationConfigured = rotationConfigured;
            this.modeId = modeId;
            this.modeIdConfigured = modeIdConfigured;
            this.uniqueId = uniqueId;
            this.uniqueIdConfigured = uniqueIdConfigured;
        }

        public boolean isWidthPixelsConfigured() {
            return widthPixelsConfigured;
        }

        public int getWidthPixels() {
            return widthPixels;
        }

        public boolean isHeightPixelsConfigured() {
            return heightPixelsConfigured;
        }

        public int getHeightPixels() {
            return heightPixels;
        }

        public boolean isDensityDpiConfigured() {
            return densityDpiConfigured;
        }

        public int getDensityDpi() {
            return densityDpi;
        }

        /**
         * Logical density derived as {@code densityDpi / 160f} (Android density formula).
         * Not a JSON field; always recomputed from the effective {@code densityDpi}.
         */
        public float getDensity() {
            return densityDpi / 160f;
        }

        public boolean isScaledDensityConfigured() {
            return scaledDensityConfigured;
        }

        public float getScaledDensity() {
            return scaledDensity;
        }

        public boolean isXdpiConfigured() {
            return xdpiConfigured;
        }

        public float getXdpi() {
            return xdpi;
        }

        public boolean isYdpiConfigured() {
            return ydpiConfigured;
        }

        public float getYdpi() {
            return ydpi;
        }

        public boolean isRefreshRateConfigured() {
            return refreshRateConfigured;
        }

        public float getRefreshRate() {
            return refreshRate;
        }

        public boolean isRotationConfigured() {
            return rotationConfigured;
        }

        public int getRotation() {
            return rotation;
        }

        public boolean isModeIdConfigured() {
            return modeIdConfigured;
        }

        public int getModeId() {
            return modeId;
        }

        public boolean isUniqueIdConfigured() {
            return uniqueIdConfigured;
        }

        /**
         * Optional {@code android.display.uniqueId}. When the key is omitted the JNI layer
         * uses the deterministic default {@code local:0} (not stored here).
         */
        public String getUniqueId() {
            return uniqueId;
        }
    }

    /**
     * Immutable view of optional {@code android.configuration} after parse validation.
     * Never exposes JSONObject/JSONArray or mutable shared state.
     * {@code densityDpi} is independent of {@code android.display.densityDpi} (never cross-derived).
     * {@code screenWidthDp}/{@code screenHeightDp}/{@code smallestScreenWidthDp} are independent of
     * display metrics and of each other (never cross-derived).
     * {@code keyboard}/{@code navigation}/{@code keyboardHidden}/{@code hardKeyboardHidden}/
     * {@code navigationHidden} are independent of all other configuration fields
     * (never cross-derived; default {@code 0} when omitted).
     */
    public static final class AndroidConfigurationConfig {
        private final int orientation;
        private final boolean orientationConfigured;
        private final int screenLayout;
        private final boolean screenLayoutConfigured;
        private final int uiMode;
        private final boolean uiModeConfigured;
        private final float fontScale;
        private final boolean fontScaleConfigured;
        private final int densityDpi;
        private final boolean densityDpiConfigured;
        private final int screenWidthDp;
        private final boolean screenWidthDpConfigured;
        private final int screenHeightDp;
        private final boolean screenHeightDpConfigured;
        private final int smallestScreenWidthDp;
        private final boolean smallestScreenWidthDpConfigured;
        private final int keyboard;
        private final boolean keyboardConfigured;
        private final int navigation;
        private final boolean navigationConfigured;
        private final int keyboardHidden;
        private final boolean keyboardHiddenConfigured;
        private final int hardKeyboardHidden;
        private final boolean hardKeyboardHiddenConfigured;
        private final int navigationHidden;
        private final boolean navigationHiddenConfigured;

        private AndroidConfigurationConfig(int orientation, boolean orientationConfigured,
                                           int screenLayout, boolean screenLayoutConfigured,
                                           int uiMode, boolean uiModeConfigured,
                                           float fontScale, boolean fontScaleConfigured,
                                           int densityDpi, boolean densityDpiConfigured,
                                           int screenWidthDp, boolean screenWidthDpConfigured,
                                           int screenHeightDp, boolean screenHeightDpConfigured,
                                           int smallestScreenWidthDp,
                                           boolean smallestScreenWidthDpConfigured,
                                           int keyboard, boolean keyboardConfigured,
                                           int navigation, boolean navigationConfigured,
                                           int keyboardHidden, boolean keyboardHiddenConfigured,
                                           int hardKeyboardHidden,
                                           boolean hardKeyboardHiddenConfigured,
                                           int navigationHidden,
                                           boolean navigationHiddenConfigured) {
            this.orientation = orientation;
            this.orientationConfigured = orientationConfigured;
            this.screenLayout = screenLayout;
            this.screenLayoutConfigured = screenLayoutConfigured;
            this.uiMode = uiMode;
            this.uiModeConfigured = uiModeConfigured;
            this.fontScale = fontScale;
            this.fontScaleConfigured = fontScaleConfigured;
            this.densityDpi = densityDpi;
            this.densityDpiConfigured = densityDpiConfigured;
            this.screenWidthDp = screenWidthDp;
            this.screenWidthDpConfigured = screenWidthDpConfigured;
            this.screenHeightDp = screenHeightDp;
            this.screenHeightDpConfigured = screenHeightDpConfigured;
            this.smallestScreenWidthDp = smallestScreenWidthDp;
            this.smallestScreenWidthDpConfigured = smallestScreenWidthDpConfigured;
            this.keyboard = keyboard;
            this.keyboardConfigured = keyboardConfigured;
            this.navigation = navigation;
            this.navigationConfigured = navigationConfigured;
            this.keyboardHidden = keyboardHidden;
            this.keyboardHiddenConfigured = keyboardHiddenConfigured;
            this.hardKeyboardHidden = hardKeyboardHidden;
            this.hardKeyboardHiddenConfigured = hardKeyboardHiddenConfigured;
            this.navigationHidden = navigationHidden;
            this.navigationHiddenConfigured = navigationHiddenConfigured;
        }

        public boolean isOrientationConfigured() {
            return orientationConfigured;
        }

        public int getOrientation() {
            return orientation;
        }

        public boolean isScreenLayoutConfigured() {
            return screenLayoutConfigured;
        }

        public int getScreenLayout() {
            return screenLayout;
        }

        public boolean isUiModeConfigured() {
            return uiModeConfigured;
        }

        public int getUiMode() {
            return uiMode;
        }

        public boolean isFontScaleConfigured() {
            return fontScaleConfigured;
        }

        public float getFontScale() {
            return fontScale;
        }

        public boolean isDensityDpiConfigured() {
            return densityDpiConfigured;
        }

        /**
         * Configuration densityDpi marker ({@code 0..1000}). Default {@code 0} when key omitted.
         * Not derived from {@code android.display}.
         */
        public int getDensityDpi() {
            return densityDpi;
        }

        public boolean isScreenWidthDpConfigured() {
            return screenWidthDpConfigured;
        }

        /**
         * Configuration screenWidthDp marker ({@code 0..10000}). Default {@code 0} when key
         * omitted. Independent of display metrics and of other screen*Dp fields.
         */
        public int getScreenWidthDp() {
            return screenWidthDp;
        }

        public boolean isScreenHeightDpConfigured() {
            return screenHeightDpConfigured;
        }

        /**
         * Configuration screenHeightDp marker ({@code 0..10000}). Default {@code 0} when key
         * omitted. Independent of display metrics and of other screen*Dp fields.
         */
        public int getScreenHeightDp() {
            return screenHeightDp;
        }

        public boolean isSmallestScreenWidthDpConfigured() {
            return smallestScreenWidthDpConfigured;
        }

        /**
         * Configuration smallestScreenWidthDp marker ({@code 0..10000}). Default {@code 0} when
         * key omitted. Independent of display metrics and of other screen*Dp fields.
         */
        public int getSmallestScreenWidthDp() {
            return smallestScreenWidthDp;
        }

        public boolean isKeyboardConfigured() {
            return keyboardConfigured;
        }

        /**
         * Configuration keyboard marker ({@code 0..3}: KEYBOARD_UNDEFINED/NOKEYS/QWERTY/12KEY).
         * Default {@code 0} when key omitted. Independent of all other configuration fields.
         */
        public int getKeyboard() {
            return keyboard;
        }

        public boolean isNavigationConfigured() {
            return navigationConfigured;
        }

        /**
         * Configuration navigation marker ({@code 0..4}:
         * NAVIGATION_UNDEFINED/NONAV/DPAD/TRACKBALL/WHEEL).
         * Default {@code 0} when key omitted. Independent of all other configuration fields.
         */
        public int getNavigation() {
            return navigation;
        }

        public boolean isKeyboardHiddenConfigured() {
            return keyboardHiddenConfigured;
        }

        /**
         * Configuration keyboardHidden marker ({@code 0..2}:
         * KEYBOARDHIDDEN_UNDEFINED/NO/YES). Default {@code 0} when key omitted.
         * Independent of keyboard and other configuration fields.
         */
        public int getKeyboardHidden() {
            return keyboardHidden;
        }

        public boolean isHardKeyboardHiddenConfigured() {
            return hardKeyboardHiddenConfigured;
        }

        /**
         * Configuration hardKeyboardHidden marker ({@code 0..2}:
         * HARDKEYBOARDHIDDEN_UNDEFINED/NO/YES). Default {@code 0} when key omitted.
         * Independent of keyboard/keyboardHidden and other fields.
         */
        public int getHardKeyboardHidden() {
            return hardKeyboardHidden;
        }

        public boolean isNavigationHiddenConfigured() {
            return navigationHiddenConfigured;
        }

        /**
         * Configuration navigationHidden marker ({@code 0..2}:
         * NAVIGATIONHIDDEN_UNDEFINED/NO/YES). Default {@code 0} when key omitted.
         * Independent of navigation and other configuration fields.
         */
        public int getNavigationHidden() {
            return navigationHidden;
        }
    }

    /**
     * Immutable view of optional {@code android.power} after parse validation (v1 subset).
     * Never exposes JSONObject/JSONArray or mutable shared state.
     */
    public static final class AndroidPowerConfig {
        private final boolean interactive;
        private final boolean interactiveConfigured;
        private final boolean powerSaveMode;
        private final boolean powerSaveModeConfigured;
        private final boolean deviceIdleMode;
        private final boolean deviceIdleModeConfigured;
        private final boolean deviceLightIdleMode;
        private final boolean deviceLightIdleModeConfigured;
        private final boolean lowPowerStandbyEnabled;
        private final boolean lowPowerStandbyEnabledConfigured;
        private final boolean sustainedPerformanceModeSupported;
        private final boolean sustainedPerformanceModeSupportedConfigured;
        private final boolean rebootingUserspaceSupported;
        private final boolean rebootingUserspaceSupportedConfigured;
        private final boolean ignoringBatteryOptimizations;
        private final boolean ignoringBatteryOptimizationsConfigured;

        private AndroidPowerConfig(boolean interactive, boolean interactiveConfigured,
                                   boolean powerSaveMode, boolean powerSaveModeConfigured,
                                   boolean deviceIdleMode, boolean deviceIdleModeConfigured,
                                   boolean deviceLightIdleMode, boolean deviceLightIdleModeConfigured,
                                   boolean lowPowerStandbyEnabled, boolean lowPowerStandbyEnabledConfigured,
                                   boolean sustainedPerformanceModeSupported,
                                   boolean sustainedPerformanceModeSupportedConfigured,
                                   boolean rebootingUserspaceSupported,
                                   boolean rebootingUserspaceSupportedConfigured,
                                   boolean ignoringBatteryOptimizations,
                                   boolean ignoringBatteryOptimizationsConfigured) {
            this.interactive = interactive;
            this.interactiveConfigured = interactiveConfigured;
            this.powerSaveMode = powerSaveMode;
            this.powerSaveModeConfigured = powerSaveModeConfigured;
            this.deviceIdleMode = deviceIdleMode;
            this.deviceIdleModeConfigured = deviceIdleModeConfigured;
            this.deviceLightIdleMode = deviceLightIdleMode;
            this.deviceLightIdleModeConfigured = deviceLightIdleModeConfigured;
            this.lowPowerStandbyEnabled = lowPowerStandbyEnabled;
            this.lowPowerStandbyEnabledConfigured = lowPowerStandbyEnabledConfigured;
            this.sustainedPerformanceModeSupported = sustainedPerformanceModeSupported;
            this.sustainedPerformanceModeSupportedConfigured = sustainedPerformanceModeSupportedConfigured;
            this.rebootingUserspaceSupported = rebootingUserspaceSupported;
            this.rebootingUserspaceSupportedConfigured = rebootingUserspaceSupportedConfigured;
            this.ignoringBatteryOptimizations = ignoringBatteryOptimizations;
            this.ignoringBatteryOptimizationsConfigured = ignoringBatteryOptimizationsConfigured;
        }

        public boolean isInteractiveConfigured() {
            return interactiveConfigured;
        }

        public boolean isInteractive() {
            return interactive;
        }

        public boolean isPowerSaveModeConfigured() {
            return powerSaveModeConfigured;
        }

        public boolean isPowerSaveMode() {
            return powerSaveMode;
        }

        public boolean isDeviceIdleModeConfigured() {
            return deviceIdleModeConfigured;
        }

        public boolean isDeviceIdleMode() {
            return deviceIdleMode;
        }

        public boolean isDeviceLightIdleModeConfigured() {
            return deviceLightIdleModeConfigured;
        }

        public boolean isDeviceLightIdleMode() {
            return deviceLightIdleMode;
        }

        public boolean isLowPowerStandbyEnabledConfigured() {
            return lowPowerStandbyEnabledConfigured;
        }

        public boolean isLowPowerStandbyEnabled() {
            return lowPowerStandbyEnabled;
        }

        public boolean isSustainedPerformanceModeSupportedConfigured() {
            return sustainedPerformanceModeSupportedConfigured;
        }

        public boolean isSustainedPerformanceModeSupported() {
            return sustainedPerformanceModeSupported;
        }

        public boolean isRebootingUserspaceSupportedConfigured() {
            return rebootingUserspaceSupportedConfigured;
        }

        public boolean isRebootingUserspaceSupported() {
            return rebootingUserspaceSupported;
        }

        public boolean isIgnoringBatteryOptimizationsConfigured() {
            return ignoringBatteryOptimizationsConfigured;
        }

        public boolean isIgnoringBatteryOptimizations() {
            return ignoringBatteryOptimizations;
        }
    }

    /**
     * Immutable view of optional {@code android.thermal} after parse validation (v1 subset).
     * Never exposes JSONObject/JSONArray or mutable shared state.
     */
    public static final class AndroidThermalConfig {
        private final int currentThermalStatus;
        private final boolean currentThermalStatusConfigured;
        private final float headroom;
        private final boolean headroomConfigured;

        private AndroidThermalConfig(int currentThermalStatus, boolean currentThermalStatusConfigured,
                                     float headroom, boolean headroomConfigured) {
            this.currentThermalStatus = currentThermalStatus;
            this.currentThermalStatusConfigured = currentThermalStatusConfigured;
            this.headroom = headroom;
            this.headroomConfigured = headroomConfigured;
        }

        public boolean isCurrentThermalStatusConfigured() {
            return currentThermalStatusConfigured;
        }

        public int getCurrentThermalStatus() {
            return currentThermalStatus;
        }

        public boolean isHeadroomConfigured() {
            return headroomConfigured;
        }

        public float getHeadroom() {
            return headroom;
        }
    }

    /**
     * Immutable view of optional top-level {@code graphics} after parse validation (v1 subset).
     * Optional GLES / EGL query strings; never exposes JSONObject/JSONArray.
     * Fields are independent and never inferred from each other or from {@code android.display}.
     * Does not model native libGLES/libEGL, contexts, or Vulkan.
     */
    public static final class GraphicsConfig {
        private final String vendor;
        private final boolean vendorConfigured;
        private final String renderer;
        private final boolean rendererConfigured;
        private final String version;
        private final boolean versionConfigured;
        private final String shadingLanguageVersion;
        private final boolean shadingLanguageVersionConfigured;
        private final List<String> extensions;
        private final boolean extensionsConfigured;
        private final String eglVendor;
        private final boolean eglVendorConfigured;
        private final String eglVersion;
        private final boolean eglVersionConfigured;
        private final List<String> eglExtensions;
        private final boolean eglExtensionsConfigured;

        private GraphicsConfig(String vendor, boolean vendorConfigured,
                               String renderer, boolean rendererConfigured,
                               String version, boolean versionConfigured,
                               String shadingLanguageVersion, boolean shadingLanguageVersionConfigured,
                               List<String> extensions, boolean extensionsConfigured,
                               String eglVendor, boolean eglVendorConfigured,
                               String eglVersion, boolean eglVersionConfigured,
                               List<String> eglExtensions, boolean eglExtensionsConfigured) {
            this.vendor = vendor;
            this.vendorConfigured = vendorConfigured;
            this.renderer = renderer;
            this.rendererConfigured = rendererConfigured;
            this.version = version;
            this.versionConfigured = versionConfigured;
            this.shadingLanguageVersion = shadingLanguageVersion;
            this.shadingLanguageVersionConfigured = shadingLanguageVersionConfigured;
            this.extensions = extensions == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(extensions));
            this.extensionsConfigured = extensionsConfigured;
            this.eglVendor = eglVendor;
            this.eglVendorConfigured = eglVendorConfigured;
            this.eglVersion = eglVersion;
            this.eglVersionConfigured = eglVersionConfigured;
            this.eglExtensions = eglExtensions == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(eglExtensions));
            this.eglExtensionsConfigured = eglExtensionsConfigured;
        }

        public boolean isVendorConfigured() {
            return vendorConfigured;
        }

        public String getVendor() {
            return vendor;
        }

        public boolean isRendererConfigured() {
            return rendererConfigured;
        }

        public String getRenderer() {
            return renderer;
        }

        public boolean isVersionConfigured() {
            return versionConfigured;
        }

        public String getVersion() {
            return version;
        }

        public boolean isShadingLanguageVersionConfigured() {
            return shadingLanguageVersionConfigured;
        }

        public String getShadingLanguageVersion() {
            return shadingLanguageVersion;
        }

        public boolean isExtensionsConfigured() {
            return extensionsConfigured;
        }

        /** Immutable extension tokens in JSON order; empty when omitted or {@code []}. */
        public List<String> getExtensions() {
            return extensions;
        }

        /** Space-joined {@code GL_EXTENSIONS} text; {@code null} when the key is omitted. */
        public String getExtensionsJoined() {
            return extensionsConfigured ? joinGraphicsExtensions(extensions) : null;
        }

        public boolean isEglVendorConfigured() {
            return eglVendorConfigured;
        }

        public String getEglVendor() {
            return eglVendor;
        }

        public boolean isEglVersionConfigured() {
            return eglVersionConfigured;
        }

        public String getEglVersion() {
            return eglVersion;
        }

        public boolean isEglExtensionsConfigured() {
            return eglExtensionsConfigured;
        }

        public List<String> getEglExtensions() {
            return eglExtensions;
        }

        /** Space-joined {@code EGL_EXTENSIONS} text; {@code null} when the key is omitted. */
        public String getEglExtensionsJoined() {
            return eglExtensionsConfigured ? joinGraphicsExtensions(eglExtensions) : null;
        }
    }

    /**
     * Immutable view of optional {@code android.battery} after parse validation (v1 subset).
     * Never exposes JSONObject/JSONArray or mutable shared state. Fields are independent
     * (never cross-inferred).
     */
    public static final class AndroidBatteryConfig {
        private final int capacityPercent;
        private final boolean capacityPercentConfigured;
        private final boolean charging;
        private final boolean chargingConfigured;
        private final int chargeCounterUah;
        private final boolean chargeCounterUahConfigured;
        private final int currentNowUa;
        private final boolean currentNowUaConfigured;
        private final int currentAverageUa;
        private final boolean currentAverageUaConfigured;
        private final long energyCounterNwh;
        private final boolean energyCounterNwhConfigured;
        private final int status;
        private final boolean statusConfigured;
        private final long chargeTimeRemainingMillis;
        private final boolean chargeTimeRemainingMillisConfigured;
        private final int plugged;
        private final boolean pluggedConfigured;
        private final int health;
        private final boolean healthConfigured;
        private final int voltageMv;
        private final boolean voltageMvConfigured;
        private final int temperatureTenthsC;
        private final boolean temperatureTenthsCConfigured;

        private AndroidBatteryConfig(int capacityPercent, boolean capacityPercentConfigured,
                                     boolean charging, boolean chargingConfigured,
                                     int chargeCounterUah, boolean chargeCounterUahConfigured,
                                     int currentNowUa, boolean currentNowUaConfigured,
                                     int currentAverageUa, boolean currentAverageUaConfigured,
                                     long energyCounterNwh, boolean energyCounterNwhConfigured,
                                     int status, boolean statusConfigured,
                                     long chargeTimeRemainingMillis,
                                     boolean chargeTimeRemainingMillisConfigured,
                                     int plugged, boolean pluggedConfigured,
                                     int health, boolean healthConfigured,
                                     int voltageMv, boolean voltageMvConfigured,
                                     int temperatureTenthsC, boolean temperatureTenthsCConfigured) {
            this.capacityPercent = capacityPercent;
            this.capacityPercentConfigured = capacityPercentConfigured;
            this.charging = charging;
            this.chargingConfigured = chargingConfigured;
            this.chargeCounterUah = chargeCounterUah;
            this.chargeCounterUahConfigured = chargeCounterUahConfigured;
            this.currentNowUa = currentNowUa;
            this.currentNowUaConfigured = currentNowUaConfigured;
            this.currentAverageUa = currentAverageUa;
            this.currentAverageUaConfigured = currentAverageUaConfigured;
            this.energyCounterNwh = energyCounterNwh;
            this.energyCounterNwhConfigured = energyCounterNwhConfigured;
            this.status = status;
            this.statusConfigured = statusConfigured;
            this.chargeTimeRemainingMillis = chargeTimeRemainingMillis;
            this.chargeTimeRemainingMillisConfigured = chargeTimeRemainingMillisConfigured;
            this.plugged = plugged;
            this.pluggedConfigured = pluggedConfigured;
            this.health = health;
            this.healthConfigured = healthConfigured;
            this.voltageMv = voltageMv;
            this.voltageMvConfigured = voltageMvConfigured;
            this.temperatureTenthsC = temperatureTenthsC;
            this.temperatureTenthsCConfigured = temperatureTenthsCConfigured;
        }

        public boolean isCapacityPercentConfigured() {
            return capacityPercentConfigured;
        }

        public int getCapacityPercent() {
            return capacityPercent;
        }

        public boolean isChargingConfigured() {
            return chargingConfigured;
        }

        public boolean isCharging() {
            return charging;
        }

        public boolean isChargeCounterUahConfigured() {
            return chargeCounterUahConfigured;
        }

        /**
         * Battery charge counter in microampere-hours ({@code BATTERY_PROPERTY_CHARGE_COUNTER}).
         * Only meaningful when {@link #isChargeCounterUahConfigured()} is true; not defaulted and
         * never inferred from {@link #getCapacityPercent()} / {@link #isCharging()}.
         */
        public int getChargeCounterUah() {
            return chargeCounterUah;
        }

        public boolean isCurrentNowUaConfigured() {
            return currentNowUaConfigured;
        }

        /**
         * Instantaneous battery current in microamperes ({@code BATTERY_PROPERTY_CURRENT_NOW}).
         * Signed 32-bit; only meaningful when {@link #isCurrentNowUaConfigured()} is true; not
         * defaulted and never inferred from capacity/charging/chargeCounter.
         */
        public int getCurrentNowUa() {
            return currentNowUa;
        }

        public boolean isCurrentAverageUaConfigured() {
            return currentAverageUaConfigured;
        }

        /**
         * Average battery current in microamperes ({@code BATTERY_PROPERTY_CURRENT_AVERAGE}).
         * Signed 32-bit; only meaningful when {@link #isCurrentAverageUaConfigured()} is true; not
         * defaulted and never inferred from currentNow/capacity/charging/chargeCounter.
         */
        public int getCurrentAverageUa() {
            return currentAverageUa;
        }

        public boolean isEnergyCounterNwhConfigured() {
            return energyCounterNwhConfigured;
        }

        /**
         * Battery energy counter in nanowatt-hours ({@code BATTERY_PROPERTY_ENERGY_COUNTER}).
         * Non-negative signed 64-bit long; only meaningful when
         * {@link #isEnergyCounterNwhConfigured()} is true; not defaulted and never inferred from
         * capacity/charge/current fields. Exposed only via {@code getLongProperty} (not int).
         */
        public long getEnergyCounterNwh() {
            return energyCounterNwh;
        }

        public boolean isStatusConfigured() {
            return statusConfigured;
        }

        /**
         * Battery status ({@code BATTERY_PROPERTY_STATUS}): Android constants 1..5
         * (UNKNOWN/CHARGING/DISCHARGING/NOT_CHARGING/FULL). Only meaningful when
         * {@link #isStatusConfigured()} is true; not defaulted and never inferred from
         * {@link #isCharging()} / capacity/current/energy. Exposed only via
         * {@code getIntProperty} (not long).
         */
        public int getStatus() {
            return status;
        }

        public boolean isChargeTimeRemainingMillisConfigured() {
            return chargeTimeRemainingMillisConfigured;
        }

        /**
         * Fixed remaining charge time in milliseconds for
         * {@code BatteryManager.computeChargeTimeRemaining()}. {@code -1} is the documented
         * unable-to-compute marker; nonnegative values are allowed. Only meaningful when
         * {@link #isChargeTimeRemainingMillisConfigured()} is true; not defaulted and never
         * calculated from charging/capacity/current/status/other fields.
         */
        public long getChargeTimeRemainingMillis() {
            return chargeTimeRemainingMillis;
        }

        public boolean isPluggedConfigured() {
            return pluggedConfigured;
        }

        /**
         * {@code BatteryManager.EXTRA_PLUGGED}: {@code 0} none, {@code 1} AC, {@code 2} USB,
         * {@code 4} wireless. Never inferred from {@link #isCharging()} or {@code status}.
         */
        public int getPlugged() {
            return plugged;
        }

        public boolean isHealthConfigured() {
            return healthConfigured;
        }

        /**
         * {@code BatteryManager.EXTRA_HEALTH}: Android constants 1..7. Never inferred from
         * status/charging/capacity.
         */
        public int getHealth() {
            return health;
        }

        public boolean isVoltageMvConfigured() {
            return voltageMvConfigured;
        }

        /**
         * Battery voltage in millivolts. Never inferred from health/status/capacity.
         */
        public int getVoltageMv() {
            return voltageMv;
        }

        public boolean isTemperatureTenthsCConfigured() {
            return temperatureTenthsCConfigured;
        }

        /**
         * Battery temperature in tenths of a degree Celsius. Never inferred from other battery fields.
         */
        public int getTemperatureTenthsC() {
            return temperatureTenthsC;
        }
    }

    /**
     * One {@code android.telephony.cellInfo[]} row after parse validation.
     * Fields are independent and never inferred from {@code networkOperator} / slots.
     */
    public static final class CellInfoConfig {
        private final String type;
        private final boolean registered;
        private final boolean registeredConfigured;
        private final String mcc;
        private final boolean mccConfigured;
        private final String mnc;
        private final boolean mncConfigured;
        private final int ci;
        private final boolean ciConfigured;
        private final int pci;
        private final boolean pciConfigured;
        private final int tac;
        private final boolean tacConfigured;
        private final int earfcn;
        private final boolean earfcnConfigured;
        private final String alphaLong;
        private final boolean alphaLongConfigured;
        private final String alphaShort;
        private final boolean alphaShortConfigured;

        private CellInfoConfig(String type,
                               boolean registered, boolean registeredConfigured,
                               String mcc, boolean mccConfigured,
                               String mnc, boolean mncConfigured,
                               int ci, boolean ciConfigured,
                               int pci, boolean pciConfigured,
                               int tac, boolean tacConfigured,
                               int earfcn, boolean earfcnConfigured,
                               String alphaLong, boolean alphaLongConfigured,
                               String alphaShort, boolean alphaShortConfigured) {
            this.type = type;
            this.registered = registered;
            this.registeredConfigured = registeredConfigured;
            this.mcc = mcc;
            this.mccConfigured = mccConfigured;
            this.mnc = mnc;
            this.mncConfigured = mncConfigured;
            this.ci = ci;
            this.ciConfigured = ciConfigured;
            this.pci = pci;
            this.pciConfigured = pciConfigured;
            this.tac = tac;
            this.tacConfigured = tacConfigured;
            this.earfcn = earfcn;
            this.earfcnConfigured = earfcnConfigured;
            this.alphaLong = alphaLong;
            this.alphaLongConfigured = alphaLongConfigured;
            this.alphaShort = alphaShort;
            this.alphaShortConfigured = alphaShortConfigured;
        }

        public String getType() {
            return type;
        }

        public boolean isRegisteredConfigured() {
            return registeredConfigured;
        }

        public boolean isRegistered() {
            return registered;
        }

        public boolean isMccConfigured() {
            return mccConfigured;
        }

        public String getMcc() {
            return mcc;
        }

        public boolean isMncConfigured() {
            return mncConfigured;
        }

        public String getMnc() {
            return mnc;
        }

        public boolean isCiConfigured() {
            return ciConfigured;
        }

        public int getCi() {
            return ci;
        }

        public boolean isPciConfigured() {
            return pciConfigured;
        }

        public int getPci() {
            return pci;
        }

        public boolean isTacConfigured() {
            return tacConfigured;
        }

        public int getTac() {
            return tac;
        }

        public boolean isEarfcnConfigured() {
            return earfcnConfigured;
        }

        public int getEarfcn() {
            return earfcn;
        }

        public boolean isAlphaLongConfigured() {
            return alphaLongConfigured;
        }

        public String getAlphaLong() {
            return alphaLong;
        }

        public boolean isAlphaShortConfigured() {
            return alphaShortConfigured;
        }

        public String getAlphaShort() {
            return alphaShort;
        }
    }

    /**
     * One {@code network.wifi.scanResults[]} row after parse validation.
     * Never inferred from {@code network.wifi} connection fields.
     */
    public static final class WifiScanResultConfig {
        private final String ssid;
        private final boolean ssidConfigured;
        private final String bssid;
        private final boolean bssidConfigured;
        private final int rssi;
        private final boolean rssiConfigured;
        private final int frequencyMhz;
        private final boolean frequencyMhzConfigured;

        private WifiScanResultConfig(String ssid, boolean ssidConfigured,
                                     String bssid, boolean bssidConfigured,
                                     int rssi, boolean rssiConfigured,
                                     int frequencyMhz, boolean frequencyMhzConfigured) {
            this.ssid = ssid;
            this.ssidConfigured = ssidConfigured;
            this.bssid = bssid;
            this.bssidConfigured = bssidConfigured;
            this.rssi = rssi;
            this.rssiConfigured = rssiConfigured;
            this.frequencyMhz = frequencyMhz;
            this.frequencyMhzConfigured = frequencyMhzConfigured;
        }

        public boolean isSsidConfigured() {
            return ssidConfigured;
        }

        public String getSsid() {
            return ssid;
        }

        public boolean isBssidConfigured() {
            return bssidConfigured;
        }

        public String getBssid() {
            return bssid;
        }

        public boolean isRssiConfigured() {
            return rssiConfigured;
        }

        public int getRssi() {
            return rssi;
        }

        public boolean isFrequencyMhzConfigured() {
            return frequencyMhzConfigured;
        }

        public int getFrequencyMhz() {
            return frequencyMhz;
        }
    }

    /**
     * Immutable view of optional {@code android.powerProfile} after parse validation.
     * {@code averagePower} is a name→watts map; missing names do not take over.
     */
    public static final class AndroidPowerProfileConfig {
        private final Map<String, Double> averagePower;
        private final boolean averagePowerConfigured;

        private AndroidPowerProfileConfig(Map<String, Double> averagePower, boolean averagePowerConfigured) {
            this.averagePower = averagePower;
            this.averagePowerConfigured = averagePowerConfigured;
        }

        public boolean isAveragePowerConfigured() {
            return averagePowerConfigured;
        }

        public boolean hasAveragePower(String name) {
            return averagePowerConfigured && name != null && averagePower.containsKey(name);
        }

        public Double getAveragePower(String name) {
            if (!hasAveragePower(name)) {
                return null;
            }
            return averagePower.get(name);
        }

        public Map<String, Double> getAveragePowerMap() {
            return averagePower;
        }
    }

    /**
     * Immutable view of one {@code android.cameras.infos[i]} entry after parse validation.
     * Fields {@code facing} and {@code orientation} are always present on a valid entry.
     * Optional {@code canDisableShutterSound} is presence-tracked and never defaulted.
     * Never exposes JSONObject.
     */
    public static final class AndroidCameraInfoConfig {
        private final int facing;
        private final int orientation;
        private final boolean canDisableShutterSound;
        private final boolean canDisableShutterSoundConfigured;

        private AndroidCameraInfoConfig(int facing, int orientation,
                                        boolean canDisableShutterSound,
                                        boolean canDisableShutterSoundConfigured) {
            this.facing = facing;
            this.orientation = orientation;
            this.canDisableShutterSound = canDisableShutterSound;
            this.canDisableShutterSoundConfigured = canDisableShutterSoundConfigured;
        }

        /**
         * Camera facing ({@code Camera.CameraInfo.CAMERA_FACING_*}): 0 back, 1 front, 2 external.
         */
        public int getFacing() {
            return facing;
        }

        /**
         * Sensor orientation degrees: only 0, 90, 180, or 270.
         */
        public int getOrientation() {
            return orientation;
        }

        /**
         * Whether {@code canDisableShutterSound} was present in JSON. Omitted key is false so JNI
         * may keep UOE; never inferred from facing, orientation, or other cameras.
         */
        public boolean isCanDisableShutterSoundConfigured() {
            return canDisableShutterSoundConfigured;
        }

        /**
         * Configured {@code Camera.CameraInfo.canDisableShutterSound} marker.
         * Meaningful only when {@link #isCanDisableShutterSoundConfigured()} is true;
         * never inferred or defaulted when the key is absent.
         */
        public boolean getCanDisableShutterSound() {
            return canDisableShutterSound;
        }
    }

    /**
     * Immutable view of optional {@code android.cameras} after parse validation (v1 subset).
     * Presence-tracked integer {@code count} and optional {@code infos} list; never exposes JSONObject.
     */
    /**
     * One {@code android.cameras.streams[]} row: optional NV21 preview and/or JPEG still
     * for a single {@code cameraId}. Hex bytes are copied at parse time; overlay file
     * paths are stored and resolved later. Never inferred from {@code infos} or other cameras.
     */
    public static final class AndroidCameraStreamConfig {
        private final int cameraId;
        private final int width;
        private final int height;
        private final byte[] previewNv21;
        private final boolean previewConfigured;
        private final String previewFile;
        private final byte[] jpeg;
        private final boolean jpegConfigured;
        private final String jpegFile;

        private AndroidCameraStreamConfig(int cameraId, int width, int height,
                                          byte[] previewNv21, boolean previewConfigured,
                                          String previewFile,
                                          byte[] jpeg, boolean jpegConfigured,
                                          String jpegFile) {
            this.cameraId = cameraId;
            this.width = width;
            this.height = height;
            this.previewNv21 = previewNv21;
            this.previewConfigured = previewConfigured;
            this.previewFile = previewFile;
            this.jpeg = jpeg;
            this.jpegConfigured = jpegConfigured;
            this.jpegFile = jpegFile;
        }

        public int getCameraId() {
            return cameraId;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public boolean isPreviewConfigured() {
            return previewConfigured;
        }

        /**
         * NV21 preview bytes from {@code previewHex}, or {@code null} when only {@code previewFile}
         * is set. Callers must not mutate the returned array. Use
         * {@link TraceEnvironmentConfig#resolveCameraPreview(AndroidCameraStreamConfig)} to also
         * load overlay files.
         */
        public byte[] getPreviewNv21() {
            return previewNv21;
        }

        public String getPreviewFile() {
            return previewFile;
        }

        public boolean isJpegConfigured() {
            return jpegConfigured;
        }

        /**
         * JPEG bytes from {@code jpegHex}, or {@code null} when only {@code jpegFile} is set.
         */
        public byte[] getJpeg() {
            return jpeg;
        }

        public String getJpegFile() {
            return jpegFile;
        }
    }

    public static final class AndroidCamerasConfig {
        private final int count;
        private final boolean countConfigured;
        private final List<AndroidCameraInfoConfig> infos;
        private final boolean infosConfigured;
        private final List<AndroidCameraStreamConfig> streams;
        private final boolean streamsConfigured;

        private AndroidCamerasConfig(int count, boolean countConfigured,
                                     List<AndroidCameraInfoConfig> infos, boolean infosConfigured,
                                     List<AndroidCameraStreamConfig> streams, boolean streamsConfigured) {
            this.count = count;
            this.countConfigured = countConfigured;
            this.infos = infos;
            this.infosConfigured = infosConfigured;
            this.streams = streams;
            this.streamsConfigured = streamsConfigured;
        }

        public boolean isCountConfigured() {
            return countConfigured;
        }

        /**
         * Number of cameras. Default {@code 0} when key omitted under present {@code android.cameras}
         * (and {@code infos} is not present). When {@code infos} is present, {@code count} is required
         * and equals {@code infos} length.
         */
        public int getCount() {
            return count;
        }

        public boolean isInfosConfigured() {
            return infosConfigured;
        }

        /**
         * Immutable camera info entries when {@code infos} was present; empty list when omitted.
         * Never exposes JSONArray/JSONObject.
         */
        public List<AndroidCameraInfoConfig> getInfos() {
            return infos;
        }

        /**
         * Whether {@code streams} was present (including an explicit empty array).
         */
        public boolean isStreamsConfigured() {
            return streamsConfigured;
        }

        /**
         * Immutable stream rows when {@code streams} was present; empty list when omitted.
         */
        public List<AndroidCameraStreamConfig> getStreams() {
            return streams;
        }

        /**
         * Stream for {@code cameraId}, or {@code null} when that id has no row.
         */
        public AndroidCameraStreamConfig findStream(int cameraId) {
            for (int i = 0; i < streams.size(); i++) {
                AndroidCameraStreamConfig row = streams.get(i);
                if (row.getCameraId() == cameraId) {
                    return row;
                }
            }
            return null;
        }
    }

    /**
     * Immutable view of optional {@code android.sensors} after parse validation (v1 types subset).
     * Ordered unique positive sensor type integers; never exposes JSONObject/JSONArray.
     * {@code dynamicTypes} follows the same rules as {@code types} and may overlap it.
     * {@code dynamicDiscoverySupported} is an independent strict Boolean defaulting to false.
     * Optional {@code names} maps listed types to explicit non-empty strings; never inferred
     * from {@code types} / {@code dynamicTypes} and never used to populate those lists.
     * Optional {@code vendors} 同样把已列出的 type 映射到显式非空厂商名；不从
     * {@code types} / {@code dynamicTypes} / {@code names} 推导，也不反向写入这些字段。
     * Optional {@code versions} 把已列出的 type 映射到显式精确整数版本号
     * {@code 0..Integer.MAX_VALUE}；不从 {@code types} / {@code dynamicTypes} /
     * {@code names} / {@code vendors} 推导，也不反向写入这些字段。
     * Optional {@code minDelaysMicros} 把已列出的 type 映射到显式精确整数最小延迟
     * （微秒，{@code -1..Integer.MAX_VALUE}；{@code -1} 为一次性传感器）；不从 {@code types} / {@code dynamicTypes} /
     * {@code names} / {@code vendors} / {@code versions} / {@code stringTypes} /
     * {@code maximumRanges} / {@code resolutions} / {@code powers} /
     * {@code maxDelaysMicros} 推导，也不反向写入这些字段。
     * Optional {@code maxDelaysMicros} 把已列出的 type 映射到显式精确整数最大延迟
     * （微秒，{@code 0..Integer.MAX_VALUE}）；不从 {@code types} / {@code dynamicTypes} /
     * {@code names} / {@code vendors} / {@code versions} / {@code stringTypes} /
     * {@code maximumRanges} / {@code resolutions} / {@code powers} /
     * {@code minDelaysMicros} / {@code fifoReservedEventCounts} / {@code fifoMaxEventCounts}
     * 推导，也不反向写入这些字段；不校验 {@code max>=min}。
     * Optional {@code fifoReservedEventCounts} 把已列出的 type 映射到显式精确整数
     * FIFO 预留事件数（{@code 0..Integer.MAX_VALUE}）；不从 {@code types} /
     * {@code dynamicTypes} / {@code names} / {@code vendors} / {@code versions} /
     * {@code stringTypes} / {@code maximumRanges} / {@code resolutions} / {@code powers} /
     * {@code minDelaysMicros} / {@code maxDelaysMicros} / {@code fifoMaxEventCounts}
     * 推导，也不反向写入这些字段；不校验与 {@code fifoMaxEventCounts} 的大小关系。
     * Optional {@code fifoMaxEventCounts} 把已列出的 type 映射到显式精确整数
     * FIFO 最大事件数（{@code 0..Integer.MAX_VALUE}）；不从 {@code types} /
     * {@code dynamicTypes} / {@code names} / {@code vendors} / {@code versions} /
     * {@code stringTypes} / {@code maximumRanges} / {@code resolutions} / {@code powers} /
     * {@code minDelaysMicros} / {@code maxDelaysMicros} / {@code fifoReservedEventCounts}
     * 推导，也不反向写入这些字段；不校验与 {@code fifoReservedEventCounts} 的大小关系。
     * Optional {@code wakeUpSensors} 把已列出的 type 映射到显式严格 Boolean
     * {@code Sensor.isWakeUpSensor()}；不从 {@code types} / {@code dynamicTypes} /
     * {@code names} / {@code vendors} / {@code versions} / {@code stringTypes} /
     * {@code maximumRanges} / {@code resolutions} / {@code powers} /
     * {@code minDelaysMicros} / {@code maxDelaysMicros} / {@code fifoReservedEventCounts} /
     * {@code fifoMaxEventCounts} 推导，也不反向写入这些字段。显式 {@code false} 与缺失不同。
     * Optional {@code sensorIds} 把已列出的 type 映射到显式精确整数 {@code Sensor.getId()}
     * （{@code -1..Integer.MAX_VALUE}，含 {@code -1}/{@code 0}）；
     * 不从其它传感器字段推导。显式 {@code 0}/{@code -1} 与缺失不同。
     * Optional {@code reportingModes} 把已列出的 type 映射到显式精确整数
     * {@code Sensor.getReportingMode()}（{@code 0..3}）；不从其它传感器字段推导。
     * Optional {@code dynamicSensors} 把已列出的 type 映射到显式严格 Boolean
     * {@code Sensor.isDynamicSensor()}；**不**从 {@code dynamicTypes} 推断。
     * 显式 {@code false} 与缺失不同。
     * Optional {@code requiredPermissions} 把已列出的 type 映射到显式 String
     * {@code Sensor.getRequiredPermission()}（{@code 0..256} UTF-16，允许空串，
     * 禁 NUL/CR/LF）；不从其它传感器字段推导。
     * Optional {@code additionalInfoSupported} 把已列出的 type 映射到显式严格 Boolean
     * {@code Sensor.isAdditionalInfoSupported()}；不从其它传感器字段推导。
     * 显式 {@code false} 与缺失不同。
     * Optional {@code highestDirectReportRateLevels} 把已列出的 type 映射到显式精确整数
     * {@code Sensor.getHighestDirectReportRateLevel()}（{@code 0..3}）；
     * **不**从 {@code reportingModes} 或其它传感器字段推导。
     * Optional {@code directChannelTypesSupported} 把已列出的 type 映射到显式
     * {@code SensorDirectChannel} 类型集合（仅 {@code 1}/{@code 2}＝
     * {@code TYPE_MEMORY_FILE}/{@code TYPE_HARDWARE_BUFFER}）；**不**从
     * {@code highestDirectReportRateLevels} 或其它传感器字段推导。
     * **不**物化直接通道对象或 create/configure API。
     */
    public static final class AndroidSensorsConfig {
        private final List<Integer> types;
        private final boolean typesConfigured;
        private final List<Integer> dynamicTypes;
        private final boolean dynamicTypesConfigured;
        private final boolean dynamicDiscoverySupported;
        private final Map<Integer, String> names;
        private final boolean namesConfigured;
        private final Map<Integer, String> vendors;
        private final boolean vendorsConfigured;
        private final Map<Integer, Integer> versions;
        private final boolean versionsConfigured;
        private final Map<Integer, String> stringTypes;
        private final boolean stringTypesConfigured;
        private final Map<Integer, Float> maximumRanges;
        private final boolean maximumRangesConfigured;
        private final Map<Integer, Float> resolutions;
        private final boolean resolutionsConfigured;
        private final Map<Integer, Float> powers;
        private final boolean powersConfigured;
        private final Map<Integer, Integer> minDelaysMicros;
        private final boolean minDelaysMicrosConfigured;
        private final Map<Integer, Integer> maxDelaysMicros;
        private final boolean maxDelaysMicrosConfigured;
        private final Map<Integer, Integer> fifoReservedEventCounts;
        private final boolean fifoReservedEventCountsConfigured;
        private final Map<Integer, Integer> fifoMaxEventCounts;
        private final boolean fifoMaxEventCountsConfigured;
        private final Map<Integer, Boolean> wakeUpSensors;
        private final boolean wakeUpSensorsConfigured;
        private final Map<Integer, Integer> sensorIds;
        private final boolean sensorIdsConfigured;
        private final Map<Integer, Integer> reportingModes;
        private final boolean reportingModesConfigured;
        private final Map<Integer, Boolean> dynamicSensors;
        private final boolean dynamicSensorsConfigured;
        private final Map<Integer, String> requiredPermissions;
        private final boolean requiredPermissionsConfigured;
        private final Map<Integer, Boolean> additionalInfoSupported;
        private final boolean additionalInfoSupportedConfigured;
        private final Map<Integer, Integer> highestDirectReportRateLevels;
        private final boolean highestDirectReportRateLevelsConfigured;
        private final Map<Integer, Set<Integer>> directChannelTypesSupported;
        private final boolean directChannelTypesSupportedConfigured;

        private AndroidSensorsConfig(List<Integer> types, boolean typesConfigured,
                                     List<Integer> dynamicTypes, boolean dynamicTypesConfigured,
                                     boolean dynamicDiscoverySupported,
                                     Map<Integer, String> names, boolean namesConfigured,
                                     Map<Integer, String> vendors, boolean vendorsConfigured,
                                     Map<Integer, Integer> versions, boolean versionsConfigured,
                                     Map<Integer, String> stringTypes, boolean stringTypesConfigured,
                                     Map<Integer, Float> maximumRanges, boolean maximumRangesConfigured,
                                     Map<Integer, Float> resolutions, boolean resolutionsConfigured,
                                     Map<Integer, Float> powers, boolean powersConfigured,
                                     Map<Integer, Integer> minDelaysMicros,
                                     boolean minDelaysMicrosConfigured,
                                     Map<Integer, Integer> maxDelaysMicros,
                                     boolean maxDelaysMicrosConfigured,
                                     Map<Integer, Integer> fifoReservedEventCounts,
                                     boolean fifoReservedEventCountsConfigured,
                                     Map<Integer, Integer> fifoMaxEventCounts,
                                     boolean fifoMaxEventCountsConfigured,
                                     Map<Integer, Boolean> wakeUpSensors,
                                     boolean wakeUpSensorsConfigured,
                                     Map<Integer, Integer> sensorIds, boolean sensorIdsConfigured,
                                     Map<Integer, Integer> reportingModes,
                                     boolean reportingModesConfigured,
                                     Map<Integer, Boolean> dynamicSensors,
                                     boolean dynamicSensorsConfigured,
                                     Map<Integer, String> requiredPermissions,
                                     boolean requiredPermissionsConfigured,
                                     Map<Integer, Boolean> additionalInfoSupported,
                                     boolean additionalInfoSupportedConfigured,
                                     Map<Integer, Integer> highestDirectReportRateLevels,
                                     boolean highestDirectReportRateLevelsConfigured,
                                     Map<Integer, Set<Integer>> directChannelTypesSupported,
                                     boolean directChannelTypesSupportedConfigured) {
            this.types = types;
            this.typesConfigured = typesConfigured;
            this.dynamicTypes = dynamicTypes;
            this.dynamicTypesConfigured = dynamicTypesConfigured;
            this.dynamicDiscoverySupported = dynamicDiscoverySupported;
            this.names = names;
            this.namesConfigured = namesConfigured;
            this.vendors = vendors;
            this.vendorsConfigured = vendorsConfigured;
            this.versions = versions;
            this.versionsConfigured = versionsConfigured;
            this.stringTypes = stringTypes;
            this.stringTypesConfigured = stringTypesConfigured;
            this.maximumRanges = maximumRanges;
            this.maximumRangesConfigured = maximumRangesConfigured;
            this.resolutions = resolutions;
            this.resolutionsConfigured = resolutionsConfigured;
            this.powers = powers;
            this.powersConfigured = powersConfigured;
            this.minDelaysMicros = minDelaysMicros;
            this.minDelaysMicrosConfigured = minDelaysMicrosConfigured;
            this.maxDelaysMicros = maxDelaysMicros;
            this.maxDelaysMicrosConfigured = maxDelaysMicrosConfigured;
            this.fifoReservedEventCounts = fifoReservedEventCounts;
            this.fifoReservedEventCountsConfigured = fifoReservedEventCountsConfigured;
            this.fifoMaxEventCounts = fifoMaxEventCounts;
            this.fifoMaxEventCountsConfigured = fifoMaxEventCountsConfigured;
            this.wakeUpSensors = wakeUpSensors;
            this.wakeUpSensorsConfigured = wakeUpSensorsConfigured;
            this.sensorIds = sensorIds;
            this.sensorIdsConfigured = sensorIdsConfigured;
            this.reportingModes = reportingModes;
            this.reportingModesConfigured = reportingModesConfigured;
            this.dynamicSensors = dynamicSensors;
            this.dynamicSensorsConfigured = dynamicSensorsConfigured;
            this.requiredPermissions = requiredPermissions;
            this.requiredPermissionsConfigured = requiredPermissionsConfigured;
            this.additionalInfoSupported = additionalInfoSupported;
            this.additionalInfoSupportedConfigured = additionalInfoSupportedConfigured;
            this.highestDirectReportRateLevels = highestDirectReportRateLevels;
            this.highestDirectReportRateLevelsConfigured = highestDirectReportRateLevelsConfigured;
            this.directChannelTypesSupported = directChannelTypesSupported;
            this.directChannelTypesSupportedConfigured = directChannelTypesSupportedConfigured;
        }

        public boolean isTypesConfigured() {
            return typesConfigured;
        }

        /**
         * Configured sensor type integers in JSON order. Empty when key omitted under present
         * {@code android.sensors} or when explicitly {@code []}. Never inferred.
         */
        public List<Integer> getTypes() {
            return types;
        }

        /** Whether {@code sensorType} is present in the configured types list. */
        public boolean containsType(int sensorType) {
            return types.contains(Integer.valueOf(sensorType));
        }

        public boolean isDynamicTypesConfigured() {
            return dynamicTypesConfigured;
        }

        /**
         * Configured dynamic sensor type integers in JSON order. Empty when key omitted under
         * present {@code android.sensors} or when explicitly {@code []}. Never inferred;
         * independent of {@link #getTypes()} (may overlap).
         */
        public List<Integer> getDynamicTypes() {
            return dynamicTypes;
        }

        /** Whether {@code sensorType} is present in the configured dynamicTypes list. */
        public boolean containsDynamicType(int sensorType) {
            return dynamicTypes.contains(Integer.valueOf(sensorType));
        }

        /**
         * Strict Boolean {@code dynamicDiscoverySupported}; {@code false} when the key is omitted
         * under present {@code android.sensors}. Independent of {@link #getDynamicTypes()}.
         */
        public boolean isDynamicDiscoverySupported() {
            return dynamicDiscoverySupported;
        }

        /**
         * Whether {@code names} was present under {@code android.sensors}
         * (including an explicit empty object). Not inferred from {@code types} /
         * {@code dynamicTypes}.
         */
        public boolean isNamesConfigured() {
            return namesConfigured;
        }

        /** Whether {@code sensorType} has an explicit configured name. */
        public boolean isNameConfigured(int sensorType) {
            return names.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * Configured name for {@code sensorType}, or {@code null} when that type has no
         * explicit {@code names} entry. Never derived from the type integer.
         */
        public String getName(int sensorType) {
            return names.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code vendors} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从 {@code types} / {@code dynamicTypes} / {@code names} 推断。
         */
        public boolean isVendorsConfigured() {
            return vendorsConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的厂商名。 */
        public boolean isVendorConfigured(int sensorType) {
            return vendors.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置厂商名；无显式 {@code vendors} 条目时为 {@code null}。
         * 不从 type 整数或 {@code names} 推导。
         */
        public String getVendor(int sensorType) {
            return vendors.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code versions} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从 {@code types} / {@code dynamicTypes} / {@code names} / {@code vendors} 推断。
         */
        public boolean isVersionsConfigured() {
            return versionsConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的版本号。 */
        public boolean isVersionConfigured(int sensorType) {
            return versions.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置版本号；无显式 {@code versions} 条目时为 {@code null}。
         * 不从 type 整数或 {@code names} / {@code vendors} 推导。显式 {@code 0} 与缺失不同。
         */
        public Integer getVersion(int sensorType) {
            return versions.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code stringTypes} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从 {@code types} / {@code dynamicTypes} / {@code names} / {@code vendors} /
         * {@code versions} 推断。
         */
        public boolean isStringTypesConfigured() {
            return stringTypesConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的 string type。 */
        public boolean isStringTypeConfigured(int sensorType) {
            return stringTypes.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置 string type；无显式 {@code stringTypes} 条目时为 {@code null}。
         * 不从 type 整数或 {@code names} / {@code vendors} / {@code versions} 推导。
         */
        public String getStringType(int sensorType) {
            return stringTypes.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code maximumRanges} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从 {@code types} / {@code dynamicTypes} / {@code names} / {@code vendors} /
         * {@code versions} / {@code stringTypes} / {@code powers} 推断。
         */
        public boolean isMaximumRangesConfigured() {
            return maximumRangesConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的最大量程。 */
        public boolean isMaximumRangeConfigured(int sensorType) {
            return maximumRanges.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置最大量程；无显式 {@code maximumRanges} 条目时为 {@code null}。
         * 不从 type 整数或 {@code names} / {@code vendors} / {@code versions} /
         * {@code stringTypes} / {@code resolutions} / {@code powers} 推导。显式 {@code 0.0} 与缺失不同。
         */
        public Float getMaximumRange(int sensorType) {
            return maximumRanges.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code resolutions} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从 {@code types} / {@code dynamicTypes} / {@code names} / {@code vendors} /
         * {@code versions} / {@code stringTypes} / {@code maximumRanges} / {@code powers} 推断。
         */
        public boolean isResolutionsConfigured() {
            return resolutionsConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的分辨率。 */
        public boolean isResolutionConfigured(int sensorType) {
            return resolutions.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置分辨率；无显式 {@code resolutions} 条目时为 {@code null}。
         * 不从 type 整数或 {@code names} / {@code vendors} / {@code versions} /
         * {@code stringTypes} / {@code maximumRanges} / {@code powers} 推导。显式 {@code 0.0} 与缺失不同。
         */
        public Float getResolution(int sensorType) {
            return resolutions.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code powers} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从 {@code types} / {@code dynamicTypes} / {@code names} / {@code vendors} /
         * {@code versions} / {@code stringTypes} / {@code maximumRanges} / {@code resolutions} 推断。
         */
        public boolean isPowersConfigured() {
            return powersConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的功耗。 */
        public boolean isPowerConfigured(int sensorType) {
            return powers.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置功耗；无显式 {@code powers} 条目时为 {@code null}。
         * 不从 type 整数或 {@code names} / {@code vendors} / {@code versions} /
         * {@code stringTypes} / {@code maximumRanges} / {@code resolutions} 推导。
         * 显式 {@code 0.0} 与缺失不同。
         */
        public Float getPower(int sensorType) {
            return powers.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code minDelaysMicros} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从 {@code types} / {@code dynamicTypes} / {@code names} / {@code vendors} /
         * {@code versions} / {@code stringTypes} / {@code maximumRanges} / {@code resolutions} /
         * {@code powers} / {@code maxDelaysMicros} / {@code fifoReservedEventCounts} /
         * {@code fifoMaxEventCounts} 推断。
         */
        public boolean isMinDelaysMicrosConfigured() {
            return minDelaysMicrosConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的最小延迟（微秒）。 */
        public boolean isMinDelayMicrosConfigured(int sensorType) {
            return minDelaysMicros.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置最小延迟（微秒）；无显式 {@code minDelaysMicros} 条目时为
         * {@code null}。不从 type 整数或 {@code names} / {@code vendors} / {@code versions} /
         * {@code stringTypes} / {@code maximumRanges} / {@code resolutions} / {@code powers} /
         * {@code maxDelaysMicros} / {@code fifoReservedEventCounts} / {@code fifoMaxEventCounts}
         * 推导。显式 {@code -1}/{@code 0} 与缺失不同。
         */
        public Integer getMinDelayMicros(int sensorType) {
            return minDelaysMicros.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code maxDelaysMicros} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从 {@code types} / {@code dynamicTypes} / {@code names} / {@code vendors} /
         * {@code versions} / {@code stringTypes} / {@code maximumRanges} / {@code resolutions} /
         * {@code powers} / {@code minDelaysMicros} / {@code fifoReservedEventCounts} /
         * {@code fifoMaxEventCounts} 推断。
         */
        public boolean isMaxDelaysMicrosConfigured() {
            return maxDelaysMicrosConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的最大延迟（微秒）。 */
        public boolean isMaxDelayMicrosConfigured(int sensorType) {
            return maxDelaysMicros.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置最大延迟（微秒）；无显式 {@code maxDelaysMicros} 条目时为
         * {@code null}。不从 type 整数或 {@code names} / {@code vendors} / {@code versions} /
         * {@code stringTypes} / {@code maximumRanges} / {@code resolutions} / {@code powers} /
         * {@code minDelaysMicros} / {@code fifoReservedEventCounts} / {@code fifoMaxEventCounts}
         * 推导。不校验 {@code max>=min}。显式 {@code 0} 与缺失不同。
         */
        public Integer getMaxDelayMicros(int sensorType) {
            return maxDelaysMicros.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code fifoReservedEventCounts} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从 {@code types} / {@code dynamicTypes} / {@code names} / {@code vendors} /
         * {@code versions} / {@code stringTypes} / {@code maximumRanges} / {@code resolutions} /
         * {@code powers} / {@code minDelaysMicros} / {@code maxDelaysMicros} /
         * {@code fifoMaxEventCounts} 推断。
         */
        public boolean isFifoReservedEventCountsConfigured() {
            return fifoReservedEventCountsConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的 FIFO 预留事件数。 */
        public boolean isFifoReservedEventCountConfigured(int sensorType) {
            return fifoReservedEventCounts.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置 FIFO 预留事件数；无显式 {@code fifoReservedEventCounts}
         * 条目时为 {@code null}。不从 type 整数或 {@code names} / {@code vendors} /
         * {@code versions} / {@code stringTypes} / {@code maximumRanges} / {@code resolutions} /
         * {@code powers} / {@code minDelaysMicros} / {@code maxDelaysMicros} /
         * {@code fifoMaxEventCounts} 推导。不校验与 {@code fifoMaxEventCounts} 的大小关系。
         * 显式 {@code 0} 与缺失不同。
         */
        public Integer getFifoReservedEventCount(int sensorType) {
            return fifoReservedEventCounts.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code fifoMaxEventCounts} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从 {@code types} / {@code dynamicTypes} / {@code names} / {@code vendors} /
         * {@code versions} / {@code stringTypes} / {@code maximumRanges} / {@code resolutions} /
         * {@code powers} / {@code minDelaysMicros} / {@code maxDelaysMicros} /
         * {@code fifoReservedEventCounts} 推断。
         */
        public boolean isFifoMaxEventCountsConfigured() {
            return fifoMaxEventCountsConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的 FIFO 最大事件数。 */
        public boolean isFifoMaxEventCountConfigured(int sensorType) {
            return fifoMaxEventCounts.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置 FIFO 最大事件数；无显式 {@code fifoMaxEventCounts}
         * 条目时为 {@code null}。不从 type 整数或 {@code names} / {@code vendors} /
         * {@code versions} / {@code stringTypes} / {@code maximumRanges} / {@code resolutions} /
         * {@code powers} / {@code minDelaysMicros} / {@code maxDelaysMicros} /
         * {@code fifoReservedEventCounts} 推导。不校验与 {@code fifoReservedEventCounts}
         * 的大小关系。显式 {@code 0} 与缺失不同。
         */
        public Integer getFifoMaxEventCount(int sensorType) {
            return fifoMaxEventCounts.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code wakeUpSensors} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从 {@code types} / {@code dynamicTypes} / {@code names} / {@code vendors} /
         * {@code versions} / {@code stringTypes} / {@code maximumRanges} / {@code resolutions} /
         * {@code powers} / {@code minDelaysMicros} / {@code maxDelaysMicros} /
         * {@code fifoReservedEventCounts} / {@code fifoMaxEventCounts} 推断。
         */
        public boolean isWakeUpSensorsConfigured() {
            return wakeUpSensorsConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的 {@code isWakeUpSensor} 布尔。 */
        public boolean isWakeUpSensorConfigured(int sensorType) {
            return wakeUpSensors.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置 {@code isWakeUpSensor}；无显式 {@code wakeUpSensors}
         * 条目时为 {@code null}。不从 type 整数或其它传感器字段推导。
         * 显式 {@code false} 与缺失不同。
         */
        public Boolean getWakeUpSensor(int sensorType) {
            return wakeUpSensors.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code sensorIds} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从其它传感器字段推断。
         */
        public boolean isSensorIdsConfigured() {
            return sensorIdsConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的 {@code getId}。 */
        public boolean isIdConfigured(int sensorType) {
            return sensorIds.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置 {@code getId}；无显式 {@code sensorIds} 条目时为 {@code null}。
         * 不从 type 整数或其它传感器字段推导。显式 {@code 0}/{@code -1} 与缺失不同。
         */
        public Integer getId(int sensorType) {
            return sensorIds.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code reportingModes} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从其它传感器字段推断。
         */
        public boolean isReportingModesConfigured() {
            return reportingModesConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的 {@code getReportingMode}。 */
        public boolean isReportingModeConfigured(int sensorType) {
            return reportingModes.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置 {@code getReportingMode}；无显式 {@code reportingModes}
         * 条目时为 {@code null}。不从其它传感器字段推导。
         */
        public Integer getReportingMode(int sensorType) {
            return reportingModes.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code dynamicSensors} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * **不**从 {@code dynamicTypes} 或其它传感器字段推断。
         */
        public boolean isDynamicSensorsConfigured() {
            return dynamicSensorsConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的 {@code isDynamicSensor} 布尔。 */
        public boolean isDynamicSensorConfigured(int sensorType) {
            return dynamicSensors.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置 {@code isDynamicSensor}；无显式 {@code dynamicSensors}
         * 条目时为 {@code null}。**不**从 {@code dynamicTypes} 推导。
         * 显式 {@code false} 与缺失不同。
         */
        public Boolean getDynamicSensor(int sensorType) {
            return dynamicSensors.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code requiredPermissions} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从其它传感器字段推断。
         */
        public boolean isRequiredPermissionsConfigured() {
            return requiredPermissionsConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的 {@code getRequiredPermission}。 */
        public boolean isRequiredPermissionConfigured(int sensorType) {
            return requiredPermissions.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置 {@code getRequiredPermission}；无显式
         * {@code requiredPermissions} 条目时为 {@code null}。空串是合法已配置值。
         * 不从其它传感器字段推导。
         */
        public String getRequiredPermission(int sensorType) {
            return requiredPermissions.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code additionalInfoSupported} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从其它传感器字段推断。
         */
        public boolean isAdditionalInfoSupportedConfigured() {
            return additionalInfoSupportedConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的 {@code isAdditionalInfoSupported} 布尔。 */
        public boolean isAdditionalInfoSupportedConfigured(int sensorType) {
            return additionalInfoSupported.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置 {@code isAdditionalInfoSupported}；无显式
         * {@code additionalInfoSupported} 条目时为 {@code null}。
         * 不从其它传感器字段推导。显式 {@code false} 与缺失不同。
         */
        public Boolean getAdditionalInfoSupported(int sensorType) {
            return additionalInfoSupported.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code highestDirectReportRateLevels} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从其它传感器字段推断。
         */
        public boolean isHighestDirectReportRateLevelsConfigured() {
            return highestDirectReportRateLevelsConfigured;
        }

        /** 该 {@code sensorType} 是否有显式配置的 {@code getHighestDirectReportRateLevel}。 */
        public boolean isHighestDirectReportRateLevelConfigured(int sensorType) {
            return highestDirectReportRateLevels.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 的配置 {@code getHighestDirectReportRateLevel}；无显式
         * {@code highestDirectReportRateLevels} 条目时为 {@code null}。
         * 不从其它传感器字段推导。
         */
        public Integer getHighestDirectReportRateLevel(int sensorType) {
            return highestDirectReportRateLevels.get(Integer.valueOf(sensorType));
        }

        /**
         * {@code directChannelTypesSupported} 是否出现在 {@code android.sensors} 下（含显式空对象）。
         * 不从其它传感器字段推断。
         */
        public boolean isDirectChannelTypesSupportedConfigured() {
            return directChannelTypesSupportedConfigured;
        }

        /**
         * 该 {@code sensorType} 是否有显式配置的 {@code isDirectChannelTypeSupported} 集合。
         * 显式空数组与键缺失不同：空数组表示该 type 已配置且不支持任何通道类型。
         */
        public boolean isDirectChannelTypeSupportedConfigured(int sensorType) {
            return directChannelTypesSupported.containsKey(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 配置的直接通道类型集合；无显式
         * {@code directChannelTypesSupported} 条目时为 {@code null}。
         * 不从其它传感器字段推导。永不暴露可变集合。
         */
        public Set<Integer> getDirectChannelTypesSupported(int sensorType) {
            return directChannelTypesSupported.get(Integer.valueOf(sensorType));
        }

        /**
         * 该 {@code sensorType} 是否支持 {@code sharedMemType}；无显式条目时为 {@code null}。
         * 条目存在时：集合包含则 {@code true}，否则 {@code false}（含未知 {@code sharedMemType}）。
         */
        public Boolean isDirectChannelTypeSupported(int sensorType, int sharedMemType) {
            Set<Integer> supported = directChannelTypesSupported.get(Integer.valueOf(sensorType));
            if (supported == null) {
                return null;
            }
            return Boolean.valueOf(supported.contains(Integer.valueOf(sharedMemType)));
        }
    }

    /**
     * Immutable view of optional {@code android.clipboard} after parse validation (v1 subset).
     * Presence-tracked {@code hasPrimaryClip}, optional {@code primaryText}, optional
     * {@code primaryLabel}, and optional {@code timestampMillis}; never exposes JSONObject.
     * {@code primaryText} is independent of other nodes but, when present, requires
     * {@code hasPrimaryClip} to be explicitly {@code true}.
     * {@code primaryLabel} is allowed only together with an explicitly configured
     * {@code primaryText} (and therefore also requires {@code hasPrimaryClip} explicitly
     * {@code true}); it is never inferred from {@code primaryText}.
     * {@code timestampMillis} is a property of the configured primary ClipData and is
     * allowed only together with an explicitly configured {@code primaryText}; it is never
     * inferred from {@code primaryText} or host time. Omitted key is valid (JNI returns 0).
     */
    public static final class AndroidClipboardConfig {
        private final boolean hasPrimaryClip;
        private final boolean hasPrimaryClipConfigured;
        private final String primaryText;
        private final boolean primaryTextConfigured;
        private final String primaryLabel;
        private final boolean primaryLabelConfigured;
        private final long timestampMillis;
        private final boolean timestampMillisConfigured;

        private AndroidClipboardConfig(boolean hasPrimaryClip, boolean hasPrimaryClipConfigured,
                                       String primaryText, boolean primaryTextConfigured,
                                       String primaryLabel, boolean primaryLabelConfigured,
                                       long timestampMillis, boolean timestampMillisConfigured) {
            this.hasPrimaryClip = hasPrimaryClip;
            this.hasPrimaryClipConfigured = hasPrimaryClipConfigured;
            this.primaryText = primaryText;
            this.primaryTextConfigured = primaryTextConfigured;
            this.primaryLabel = primaryLabel;
            this.primaryLabelConfigured = primaryLabelConfigured;
            this.timestampMillis = timestampMillis;
            this.timestampMillisConfigured = timestampMillisConfigured;
        }

        public boolean isHasPrimaryClipConfigured() {
            return hasPrimaryClipConfigured;
        }

        /**
         * Whether a primary clip is present. Default {@code false} when key omitted.
         * Not inferred from other config nodes.
         */
        public boolean hasPrimaryClip() {
            return hasPrimaryClip;
        }

        public boolean isPrimaryTextConfigured() {
            return primaryTextConfigured;
        }

        /**
         * Configured primary clip plain text (may be empty). {@code null} when key omitted.
         * Never inferred; not a host clipboard read.
         */
        public String getPrimaryText() {
            return primaryText;
        }

        public boolean isPrimaryLabelConfigured() {
            return primaryLabelConfigured;
        }

        /**
         * Configured {@code ClipDescription} label (may be empty). {@code null} when key omitted.
         * Never inferred from {@code primaryText}; not a host clipboard read.
         */
        public String getPrimaryLabel() {
            return primaryLabel;
        }

        public boolean isTimestampMillisConfigured() {
            return timestampMillisConfigured;
        }

        /**
         * Configured {@code ClipDescription.getTimestamp()} millis (may be {@code 0}).
         * {@code 0} when the key is omitted. Never inferred from {@code primaryText} or host time.
         */
        public long getTimestampMillis() {
            return timestampMillis;
        }
    }

    /**
     * Immutable view of one {@code android.accessibility.services[]} entry after parse validation.
     * Exactly nonempty String {@code id} and Boolean {@code enabled}. Never exposes JSONObject.
     */
    public static final class AndroidAccessibilityServiceConfig {
        private final String id;
        private final boolean enabled;

        private AndroidAccessibilityServiceConfig(String id, boolean enabled) {
            this.id = id;
            this.enabled = enabled;
        }

        /** Service id ({@code AccessibilityServiceInfo.getId()}). */
        public String getId() {
            return id;
        }

        /** Whether included in {@code getEnabledAccessibilityServiceList}. */
        public boolean isEnabled() {
            return enabled;
        }
    }

    /**
     * Immutable view of optional {@code android.accessibility} after parse validation (v1 subset).
     * Independent presence-tracked booleans {@code enabled}, {@code touchExplorationEnabled}, and
     * {@code highContrastTextEnabled}; optional {@code services} list. Never exposes JSONObject.
     * Fields are not cross-inferred and not inferred from service lists.
     */
    public static final class AndroidAccessibilityConfig {
        private final boolean enabled;
        private final boolean enabledConfigured;
        private final boolean touchExplorationEnabled;
        private final boolean touchExplorationEnabledConfigured;
        private final boolean highContrastTextEnabled;
        private final boolean highContrastTextEnabledConfigured;
        private final List<AndroidAccessibilityServiceConfig> services;
        private final boolean servicesConfigured;

        private AndroidAccessibilityConfig(boolean enabled, boolean enabledConfigured,
                                           boolean touchExplorationEnabled,
                                           boolean touchExplorationEnabledConfigured,
                                           boolean highContrastTextEnabled,
                                           boolean highContrastTextEnabledConfigured,
                                           List<AndroidAccessibilityServiceConfig> services,
                                           boolean servicesConfigured) {
            this.enabled = enabled;
            this.enabledConfigured = enabledConfigured;
            this.touchExplorationEnabled = touchExplorationEnabled;
            this.touchExplorationEnabledConfigured = touchExplorationEnabledConfigured;
            this.highContrastTextEnabled = highContrastTextEnabled;
            this.highContrastTextEnabledConfigured = highContrastTextEnabledConfigured;
            this.services = services;
            this.servicesConfigured = servicesConfigured;
        }

        public boolean isEnabledConfigured() {
            return enabledConfigured;
        }

        /**
         * Whether accessibility is enabled. Default {@code false} when key omitted under a present
         * node. Not inferred from service lists, {@link #isTouchExplorationEnabled()},
         * {@link #isHighContrastTextEnabled()}, or other config nodes.
         */
        public boolean isEnabled() {
            return enabled;
        }

        public boolean isTouchExplorationEnabledConfigured() {
            return touchExplorationEnabledConfigured;
        }

        /**
         * Whether touch exploration is enabled. Default {@code false} when key omitted under a
         * present node. Independent of {@link #isEnabled()} /
         * {@link #isHighContrastTextEnabled()}; never cross-inferred.
         */
        public boolean isTouchExplorationEnabled() {
            return touchExplorationEnabled;
        }

        public boolean isHighContrastTextEnabledConfigured() {
            return highContrastTextEnabledConfigured;
        }

        /**
         * Whether high-contrast text is enabled. Default {@code false} when key omitted under a
         * present node. Independent of {@link #isEnabled()} /
         * {@link #isTouchExplorationEnabled()}; never cross-inferred.
         */
        public boolean isHighContrastTextEnabled() {
            return highContrastTextEnabled;
        }

        /**
         * Whether {@code services} was present under {@code android.accessibility}
         * (including an explicit empty array). When false, service-list JNI keeps legacy behavior.
         */
        public boolean isServicesConfigured() {
            return servicesConfigured;
        }

        /**
         * Immutable {@code services} entries in JSON order. Empty when key omitted or {@code []}.
         * Never inferred from enabled/touchExploration/highContrast flags.
         */
        public List<AndroidAccessibilityServiceConfig> getServices() {
            return services;
        }
    }

    /**
     * Immutable view of one {@code android.audio.streamVolumes[]} entry after parse validation.
     * Independent of other audio fields; never exposes JSONObject.
     */
    public static final class AndroidStreamVolumeConfig {
        private final int streamType;
        private final int volume;
        private final int maxVolume;
        private final int minVolume;
        private final boolean minVolumeConfigured;

        private AndroidStreamVolumeConfig(int streamType, int volume, int maxVolume,
                                          int minVolume, boolean minVolumeConfigured) {
            this.streamType = streamType;
            this.volume = volume;
            this.maxVolume = maxVolume;
            this.minVolume = minVolume;
            this.minVolumeConfigured = minVolumeConfigured;
        }

        /** Stream type integer ({@code AudioManager.STREAM_*}); unique within the array. */
        public int getStreamType() {
            return streamType;
        }

        /** Current volume level; {@code 0 <= volume <= maxVolume}. */
        public int getVolume() {
            return volume;
        }

        /** Maximum volume level for this stream; {@code >= 0}. */
        public int getMaxVolume() {
            return maxVolume;
        }

        /**
         * Whether {@code minVolume} was present on this entry. Omitted key stays unconfigured
         * so JNI {@code getStreamMinVolume} keeps UOE; never defaulted to {@code 0}.
         */
        public boolean isMinVolumeConfigured() {
            return minVolumeConfigured;
        }

        /**
         * Configured stream minimum volume. Meaningful only when
         * {@link #isMinVolumeConfigured()} is true; never inferred or defaulted when absent.
         */
        public int getMinVolume() {
            return minVolume;
        }
    }

    /**
     * Immutable view of optional {@code android.audio} after parse validation (v1 subset).
     * Independent presence-tracked fields {@code musicActive}, {@code speakerphoneOn},
     * {@code ringerMode}, {@code mode}, optional {@code properties} map, and optional
     * {@code streamVolumes} list; never exposes JSONObject. Fields are not cross-inferred.
     */
    public static final class AndroidAudioConfig {
        private final boolean musicActive;
        private final boolean musicActiveConfigured;
        private final boolean speakerphoneOn;
        private final boolean speakerphoneOnConfigured;
        private final int ringerMode;
        private final boolean ringerModeConfigured;
        private final int mode;
        private final boolean modeConfigured;
        private final Map<String, String> properties;
        private final boolean propertiesConfigured;
        private final List<AndroidStreamVolumeConfig> streamVolumes;
        private final boolean streamVolumesConfigured;

        private AndroidAudioConfig(boolean musicActive, boolean musicActiveConfigured,
                                   boolean speakerphoneOn, boolean speakerphoneOnConfigured,
                                   int ringerMode, boolean ringerModeConfigured,
                                   int mode, boolean modeConfigured,
                                   Map<String, String> properties, boolean propertiesConfigured,
                                   List<AndroidStreamVolumeConfig> streamVolumes,
                                   boolean streamVolumesConfigured) {
            this.musicActive = musicActive;
            this.musicActiveConfigured = musicActiveConfigured;
            this.speakerphoneOn = speakerphoneOn;
            this.speakerphoneOnConfigured = speakerphoneOnConfigured;
            this.ringerMode = ringerMode;
            this.ringerModeConfigured = ringerModeConfigured;
            this.mode = mode;
            this.modeConfigured = modeConfigured;
            this.properties = properties;
            this.propertiesConfigured = propertiesConfigured;
            this.streamVolumes = streamVolumes;
            this.streamVolumesConfigured = streamVolumesConfigured;
        }

        public boolean isMusicActiveConfigured() {
            return musicActiveConfigured;
        }

        /**
         * Whether music is active. Default {@code false} when key omitted under present
         * {@code android.audio}. Not inferred from other config nodes.
         */
        public boolean isMusicActive() {
            return musicActive;
        }

        public boolean isSpeakerphoneOnConfigured() {
            return speakerphoneOnConfigured;
        }

        /**
         * Whether speakerphone is on. Default {@code false} when key omitted under present
         * {@code android.audio}. Independent of {@link #isMusicActive()}; never cross-inferred.
         */
        public boolean isSpeakerphoneOn() {
            return speakerphoneOn;
        }

        public boolean isRingerModeConfigured() {
            return ringerModeConfigured;
        }

        /**
         * Ringer mode ({@code AudioManager.RINGER_MODE_*}): 0 silent, 1 vibrate, 2 normal.
         * Default {@code 2} when key omitted under present {@code android.audio}.
         * Independent of {@link #isMusicActive()} / {@link #isSpeakerphoneOn()}; never cross-inferred.
         */
        public int getRingerMode() {
            return ringerMode;
        }

        public boolean isModeConfigured() {
            return modeConfigured;
        }

        /**
         * Audio mode ({@code AudioManager.MODE_*}): 0..7 inclusive
         * ({@code MODE_NORMAL} .. {@code MODE_ASSISTANT_CONVERSATION}).
         * Default {@code 0} when key omitted under present {@code android.audio}.
         * Independent of other audio fields; never cross-inferred.
         */
        public int getMode() {
            return mode;
        }

        /**
         * Whether {@code properties} was present under {@code android.audio}
         * (including an explicit empty object).
         */
        public boolean isPropertiesConfigured() {
            return propertiesConfigured;
        }

        /**
         * Immutable map of configured audio property keys to String or {@code null}
         * (explicit JSON null). Empty when {@code properties} key absent or {@code {}}.
         * Never exposes JSONObject or a live mutable map.
         */
        public Map<String, String> getProperties() {
            return properties;
        }

        /**
         * Whether {@code key} is present in the configured {@code properties} map
         * (including explicit JSON null values).
         */
        public boolean isPropertyConfigured(String key) {
            return key != null && properties.containsKey(key);
        }

        /**
         * Configured value for {@code key}, or {@code null} when the key is absent or the
         * configured value is explicit JSON null. Use {@link #isPropertyConfigured(String)}
         * to distinguish absence from explicit null.
         */
        public String getProperty(String key) {
            if (key == null || !properties.containsKey(key)) {
                return null;
            }
            return properties.get(key);
        }

        /**
         * Whether {@code streamVolumes} was present under {@code android.audio}
         * (including an explicit empty array). When false, stream-volume JNI keeps UOE.
         * Independent of other audio fields; never cross-inferred.
         */
        public boolean isStreamVolumesConfigured() {
            return streamVolumesConfigured;
        }

        /**
         * Immutable {@code streamVolumes} entries in JSON order. Empty when key omitted or
         * {@code []}. Never exposes JSONObject/JSONArray.
         */
        public List<AndroidStreamVolumeConfig> getStreamVolumes() {
            return streamVolumes;
        }

        /**
         * Exact {@code streamType} match in configured {@code streamVolumes}, or {@code null}
         * when not configured / no match.
         */
        public AndroidStreamVolumeConfig findStreamVolume(int streamType) {
            if (!streamVolumesConfigured) {
                return null;
            }
            for (AndroidStreamVolumeConfig entry : streamVolumes) {
                if (entry.getStreamType() == streamType) {
                    return entry;
                }
            }
            return null;
        }
    }

    /**
     * Immutable view of optional {@code android.location} parent after parse validation (v1 subset).
     * Independent presence-tracked boolean {@code enabled} (not inferred from providers).
     * Never exposes JSONObject.
     */
    public static final class AndroidLocationConfig {
        private final boolean enabled;
        private final boolean enabledConfigured;

        private AndroidLocationConfig(boolean enabled, boolean enabledConfigured) {
            this.enabled = enabled;
            this.enabledConfigured = enabledConfigured;
        }

        public boolean isEnabledConfigured() {
            return enabledConfigured;
        }

        /**
         * Master location switch. Default {@code false} when key omitted under present
         * {@code android.location}. Never inferred from {@code providers}.
         */
        public boolean isEnabled() {
            return enabled;
        }
    }

    /**
     * Immutable view of optional {@code android.location.providers} after parse validation (v1 subset).
     * Three presence-tracked booleans {@code gps}/{@code network}/{@code passive}; never exposes JSONObject.
     */
    public static final class AndroidLocationProvidersConfig {
        private final boolean gps;
        private final boolean gpsConfigured;
        private final boolean network;
        private final boolean networkConfigured;
        private final boolean passive;
        private final boolean passiveConfigured;

        private AndroidLocationProvidersConfig(boolean gps, boolean gpsConfigured,
                                               boolean network, boolean networkConfigured,
                                               boolean passive, boolean passiveConfigured) {
            this.gps = gps;
            this.gpsConfigured = gpsConfigured;
            this.network = network;
            this.networkConfigured = networkConfigured;
            this.passive = passive;
            this.passiveConfigured = passiveConfigured;
        }

        public boolean isGpsConfigured() {
            return gpsConfigured;
        }

        /** Whether the {@code gps} provider is enabled. Default {@code false} when key omitted. */
        public boolean isGps() {
            return gps;
        }

        public boolean isNetworkConfigured() {
            return networkConfigured;
        }

        /** Whether the {@code network} provider is enabled. Default {@code false} when key omitted. */
        public boolean isNetwork() {
            return network;
        }

        public boolean isPassiveConfigured() {
            return passiveConfigured;
        }

        /** Whether the {@code passive} provider is enabled. Default {@code false} when key omitted. */
        public boolean isPassive() {
            return passive;
        }
    }

    /**
     * Immutable view of one {@code android.location.lastKnownLocations[]} entry after parse validation.
     * Independent of {@code enabled}/{@code providers}; never exposes JSONObject.
     */
    public static final class AndroidLastKnownLocationConfig {
        private final String provider;
        private final double latitude;
        private final double longitude;
        private final double altitude;
        private final boolean altitudeConfigured;
        private final float accuracyMeters;
        private final boolean accuracyMetersConfigured;
        private final long timeMillis;
        private final boolean timeMillisConfigured;
        private final long elapsedRealtimeNanos;
        private final boolean elapsedRealtimeNanosConfigured;
        private final boolean mock;
        private final boolean mockConfigured;
        private final float speedMetersPerSecond;
        private final boolean speedMetersPerSecondConfigured;
        private final float bearingDegrees;
        private final boolean bearingDegreesConfigured;
        private final float verticalAccuracyMeters;
        private final boolean verticalAccuracyMetersConfigured;
        private final float speedAccuracyMetersPerSecond;
        private final boolean speedAccuracyMetersPerSecondConfigured;
        private final float bearingAccuracyDegrees;
        private final boolean bearingAccuracyDegreesConfigured;

        private AndroidLastKnownLocationConfig(String provider,
                                               double latitude,
                                               double longitude,
                                               double altitude,
                                               boolean altitudeConfigured,
                                               float accuracyMeters,
                                               boolean accuracyMetersConfigured,
                                               long timeMillis,
                                               boolean timeMillisConfigured,
                                               long elapsedRealtimeNanos,
                                               boolean elapsedRealtimeNanosConfigured,
                                               boolean mock,
                                               boolean mockConfigured,
                                               float speedMetersPerSecond,
                                               boolean speedMetersPerSecondConfigured,
                                               float bearingDegrees,
                                               boolean bearingDegreesConfigured,
                                               float verticalAccuracyMeters,
                                               boolean verticalAccuracyMetersConfigured,
                                               float speedAccuracyMetersPerSecond,
                                               boolean speedAccuracyMetersPerSecondConfigured,
                                               float bearingAccuracyDegrees,
                                               boolean bearingAccuracyDegreesConfigured) {
            this.provider = provider;
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.altitudeConfigured = altitudeConfigured;
            this.accuracyMeters = accuracyMeters;
            this.accuracyMetersConfigured = accuracyMetersConfigured;
            this.timeMillis = timeMillis;
            this.timeMillisConfigured = timeMillisConfigured;
            this.elapsedRealtimeNanos = elapsedRealtimeNanos;
            this.elapsedRealtimeNanosConfigured = elapsedRealtimeNanosConfigured;
            this.mock = mock;
            this.mockConfigured = mockConfigured;
            this.speedMetersPerSecond = speedMetersPerSecond;
            this.speedMetersPerSecondConfigured = speedMetersPerSecondConfigured;
            this.bearingDegrees = bearingDegrees;
            this.bearingDegreesConfigured = bearingDegreesConfigured;
            this.verticalAccuracyMeters = verticalAccuracyMeters;
            this.verticalAccuracyMetersConfigured = verticalAccuracyMetersConfigured;
            this.speedAccuracyMetersPerSecond = speedAccuracyMetersPerSecond;
            this.speedAccuracyMetersPerSecondConfigured = speedAccuracyMetersPerSecondConfigured;
            this.bearingAccuracyDegrees = bearingAccuracyDegrees;
            this.bearingAccuracyDegreesConfigured = bearingAccuracyDegreesConfigured;
        }

        /** Nonempty provider id used for exact {@code getLastKnownLocation} match. */
        public String getProvider() {
            return provider;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public boolean isAltitudeConfigured() {
            return altitudeConfigured;
        }

        /** Altitude meters when configured; otherwise {@code 0}. */
        public double getAltitude() {
            return altitude;
        }

        public boolean isAccuracyMetersConfigured() {
            return accuracyMetersConfigured;
        }

        /** Horizontal accuracy meters when configured; otherwise {@code 0}. */
        public float getAccuracyMeters() {
            return accuracyMeters;
        }

        public boolean isTimeMillisConfigured() {
            return timeMillisConfigured;
        }

        /** Location wall-clock time millis; default {@code 0} when omitted. */
        public long getTimeMillis() {
            return timeMillis;
        }

        public boolean isElapsedRealtimeNanosConfigured() {
            return elapsedRealtimeNanosConfigured;
        }

        /** Elapsed realtime nanos; default {@code 0} when omitted. */
        public long getElapsedRealtimeNanos() {
            return elapsedRealtimeNanos;
        }

        public boolean isMockConfigured() {
            return mockConfigured;
        }

        /** Whether this location is from a mock provider; default {@code false} when omitted. */
        public boolean isMock() {
            return mock;
        }

        public boolean isSpeedMetersPerSecondConfigured() {
            return speedMetersPerSecondConfigured;
        }

        /**
         * Horizontal speed in meters per second when configured; otherwise {@code 0}.
         * Independent of other location fields.
         */
        public float getSpeedMetersPerSecond() {
            return speedMetersPerSecond;
        }

        public boolean isBearingDegreesConfigured() {
            return bearingDegreesConfigured;
        }

        /**
         * Bearing in degrees ({@code [0, 360)}) when configured; otherwise {@code 0}.
         * Independent of other location fields.
         */
        public float getBearingDegrees() {
            return bearingDegrees;
        }

        public boolean isVerticalAccuracyMetersConfigured() {
            return verticalAccuracyMetersConfigured;
        }

        /**
         * Vertical accuracy meters when configured; otherwise {@code 0}.
         * Independent of {@link #getAccuracyMeters()} and other fields; never cross-inferred.
         */
        public float getVerticalAccuracyMeters() {
            return verticalAccuracyMeters;
        }

        public boolean isSpeedAccuracyMetersPerSecondConfigured() {
            return speedAccuracyMetersPerSecondConfigured;
        }

        /**
         * Speed accuracy meters per second when configured; otherwise {@code 0}.
         * Independent of {@link #getSpeedMetersPerSecond()} and other fields; never cross-inferred.
         */
        public float getSpeedAccuracyMetersPerSecond() {
            return speedAccuracyMetersPerSecond;
        }

        public boolean isBearingAccuracyDegreesConfigured() {
            return bearingAccuracyDegreesConfigured;
        }

        /**
         * Bearing accuracy degrees when configured; otherwise {@code 0}.
         * Independent of {@link #getBearingDegrees()} and other fields; never cross-inferred.
         */
        public float getBearingAccuracyDegrees() {
            return bearingAccuracyDegrees;
        }
    }

    /**
     * Immutable view of one {@code android.location.providerCapabilities} provider entry
     * after parse validation. Currently {@code requiresNetwork},
     * {@code requiresSatellite}, {@code requiresCell}, {@code hasMonetaryCost},
     * {@code supportsAltitude}, {@code supportsSpeed}, {@code supportsBearing},
     * {@code meetsCriteria}
     * (each presence-tracked independently), optional {@code accuracy}
     * ({@code ProviderProperties.ACCURACY_FINE=1} or {@code ACCURACY_COARSE=2}),
     * and optional {@code powerRequirement}
     * ({@code POWER_USAGE_LOW=1}, {@code POWER_USAGE_MEDIUM=2}, or
     * {@code POWER_USAGE_HIGH=3}).
     * {@code meetsCriteria} is a fixed JSON Boolean flag for
     * {@code LocationProvider.meetsCriteria(Criteria)}; it does not parse, store, or
     * derive {@code Criteria} fields.
     * Never exposes JSONObject/JSONArray. Independent of {@code providers} booleans.
     */
    public static final class AndroidLocationProviderCapabilityEntry {
        private final boolean requiresNetwork;
        private final boolean requiresNetworkConfigured;
        private final boolean requiresSatellite;
        private final boolean requiresSatelliteConfigured;
        private final boolean requiresCell;
        private final boolean requiresCellConfigured;
        private final boolean hasMonetaryCost;
        private final boolean hasMonetaryCostConfigured;
        private final boolean supportsAltitude;
        private final boolean supportsAltitudeConfigured;
        private final boolean supportsSpeed;
        private final boolean supportsSpeedConfigured;
        private final boolean supportsBearing;
        private final boolean supportsBearingConfigured;
        private final boolean meetsCriteria;
        private final boolean meetsCriteriaConfigured;
        private final int accuracy;
        private final boolean accuracyConfigured;
        private final int powerRequirement;
        private final boolean powerRequirementConfigured;

        private AndroidLocationProviderCapabilityEntry(boolean requiresNetwork,
                                                       boolean requiresNetworkConfigured,
                                                       boolean requiresSatellite,
                                                       boolean requiresSatelliteConfigured,
                                                       boolean requiresCell,
                                                       boolean requiresCellConfigured,
                                                       boolean hasMonetaryCost,
                                                       boolean hasMonetaryCostConfigured,
                                                       boolean supportsAltitude,
                                                       boolean supportsAltitudeConfigured,
                                                       boolean supportsSpeed,
                                                       boolean supportsSpeedConfigured,
                                                       boolean supportsBearing,
                                                       boolean supportsBearingConfigured,
                                                       boolean meetsCriteria,
                                                       boolean meetsCriteriaConfigured,
                                                       int accuracy,
                                                       boolean accuracyConfigured,
                                                       int powerRequirement,
                                                       boolean powerRequirementConfigured) {
            this.requiresNetwork = requiresNetwork;
            this.requiresNetworkConfigured = requiresNetworkConfigured;
            this.requiresSatellite = requiresSatellite;
            this.requiresSatelliteConfigured = requiresSatelliteConfigured;
            this.requiresCell = requiresCell;
            this.requiresCellConfigured = requiresCellConfigured;
            this.hasMonetaryCost = hasMonetaryCost;
            this.hasMonetaryCostConfigured = hasMonetaryCostConfigured;
            this.supportsAltitude = supportsAltitude;
            this.supportsAltitudeConfigured = supportsAltitudeConfigured;
            this.supportsSpeed = supportsSpeed;
            this.supportsSpeedConfigured = supportsSpeedConfigured;
            this.supportsBearing = supportsBearing;
            this.supportsBearingConfigured = supportsBearingConfigured;
            this.meetsCriteria = meetsCriteria;
            this.meetsCriteriaConfigured = meetsCriteriaConfigured;
            this.accuracy = accuracy;
            this.accuracyConfigured = accuracyConfigured;
            this.powerRequirement = powerRequirement;
            this.powerRequirementConfigured = powerRequirementConfigured;
        }

        public boolean isRequiresNetworkConfigured() {
            return requiresNetworkConfigured;
        }

        /**
         * {@code LocationProvider.requiresNetwork()} value when the JSON field is present.
         * Default {@code false} when omitted under a present provider entry; JNI still treats
         * omission as unconfigured (notHandled / UOE).
         */
        public boolean isRequiresNetwork() {
            return requiresNetwork;
        }

        public boolean isRequiresSatelliteConfigured() {
            return requiresSatelliteConfigured;
        }

        /**
         * {@code LocationProvider.requiresSatellite()} value when the JSON field is present.
         * Default {@code false} when omitted under a present provider entry; JNI still treats
         * omission as unconfigured (notHandled / UOE). Independent of {@link #isRequiresNetwork()}.
         */
        public boolean isRequiresSatellite() {
            return requiresSatellite;
        }

        public boolean isRequiresCellConfigured() {
            return requiresCellConfigured;
        }

        /**
         * {@code LocationProvider.requiresCell()} value when the JSON field is present.
         * Default {@code false} when omitted under a present provider entry; JNI still treats
         * omission as unconfigured (notHandled / UOE). Independent of
         * {@link #isRequiresNetwork()} and {@link #isRequiresSatellite()}.
         */
        public boolean isRequiresCell() {
            return requiresCell;
        }

        public boolean isHasMonetaryCostConfigured() {
            return hasMonetaryCostConfigured;
        }

        /**
         * {@code LocationProvider.hasMonetaryCost()} value when the JSON field is present.
         * Default {@code false} when omitted under a present provider entry; JNI still treats
         * omission as unconfigured (notHandled / UOE). Independent of
         * {@link #isRequiresNetwork()}, {@link #isRequiresSatellite()}, and
         * {@link #isRequiresCell()}.
         */
        public boolean isHasMonetaryCost() {
            return hasMonetaryCost;
        }

        public boolean isSupportsAltitudeConfigured() {
            return supportsAltitudeConfigured;
        }

        /**
         * {@code LocationProvider.supportsAltitude()} value when the JSON field is present.
         * Default {@code false} when omitted under a present provider entry; JNI still treats
         * omission as unconfigured (notHandled / UOE). Independent of
         * {@link #isRequiresNetwork()}, {@link #isRequiresSatellite()},
         * {@link #isRequiresCell()}, and {@link #isHasMonetaryCost()}.
         */
        public boolean isSupportsAltitude() {
            return supportsAltitude;
        }

        public boolean isSupportsSpeedConfigured() {
            return supportsSpeedConfigured;
        }

        /**
         * {@code LocationProvider.supportsSpeed()} value when the JSON field is present.
         * Default {@code false} when omitted under a present provider entry; JNI still treats
         * omission as unconfigured (notHandled / UOE). Independent of
         * {@link #isRequiresNetwork()}, {@link #isRequiresSatellite()},
         * {@link #isRequiresCell()}, {@link #isHasMonetaryCost()}, and
         * {@link #isSupportsAltitude()}.
         */
        public boolean isSupportsSpeed() {
            return supportsSpeed;
        }

        public boolean isSupportsBearingConfigured() {
            return supportsBearingConfigured;
        }

        /**
         * {@code LocationProvider.supportsBearing()} value when the JSON field is present.
         * Default {@code false} when omitted under a present provider entry; JNI still treats
         * omission as unconfigured (notHandled / UOE). Independent of
         * {@link #isRequiresNetwork()}, {@link #isRequiresSatellite()},
         * {@link #isRequiresCell()}, {@link #isHasMonetaryCost()},
         * {@link #isSupportsAltitude()}, and {@link #isSupportsSpeed()}.
         */
        public boolean isSupportsBearing() {
            return supportsBearing;
        }

        public boolean isMeetsCriteriaConfigured() {
            return meetsCriteriaConfigured;
        }

        /**
         * Fixed {@code LocationProvider.meetsCriteria(Criteria)} flag when the JSON field is
         * present. Default {@code false} when omitted under a present provider entry; JNI still
         * treats omission as unconfigured (notHandled / UOE). Independent of
         * {@link #isRequiresNetwork()}, {@link #isRequiresSatellite()},
         * {@link #isRequiresCell()}, {@link #isHasMonetaryCost()},
         * {@link #isSupportsAltitude()}, {@link #isSupportsSpeed()},
         * {@link #isSupportsBearing()}, {@link #getAccuracy()}, and
         * {@link #getPowerRequirement()}.
         * This is not a Criteria-field match: the view does not parse, store, or derive
         * {@code android.location.Criteria} contents.
         */
        public boolean isMeetsCriteria() {
            return meetsCriteria;
        }

        public boolean isAccuracyConfigured() {
            return accuracyConfigured;
        }

        /**
         * {@code LocationProvider.getAccuracy()I} value when the JSON field is present:
         * {@code ProviderProperties.ACCURACY_FINE=1} or {@code ACCURACY_COARSE=2}.
         * View default {@code 0} when omitted under a present provider entry; JNI still treats
         * omission as unconfigured (notHandled / UOE). Independent of the Boolean capability
         * fields and of {@link #getPowerRequirement()}. Distinct from
         * {@code Location.getAccuracy()F} (meters on last-known Location).
         */
        public int getAccuracy() {
            return accuracy;
        }

        public boolean isPowerRequirementConfigured() {
            return powerRequirementConfigured;
        }

        /**
         * {@code LocationProvider.getPowerRequirement()I} value when the JSON field is present:
         * {@code ProviderProperties.POWER_USAGE_LOW=1}, {@code POWER_USAGE_MEDIUM=2}, or
         * {@code POWER_USAGE_HIGH=3}.
         * View default {@code 0} when omitted under a present provider entry; JNI still treats
         * omission as unconfigured (notHandled / UOE). Independent of the Boolean capability
         * fields and of {@link #getAccuracy()}.
         */
        public int getPowerRequirement() {
            return powerRequirement;
        }
    }

    /**
     * Immutable view of optional {@code android.location.providerCapabilities} after parse
     * validation. Only {@code gps}/{@code network}/{@code passive} entries; never exposes
     * JSONObject/JSONArray. Presence of this node does not change {@code enabled},
     * {@code providers}, or {@code lastKnownLocations}.
     */
    public static final class AndroidLocationProviderCapabilitiesConfig {
        private final AndroidLocationProviderCapabilityEntry gps;
        private final boolean gpsConfigured;
        private final AndroidLocationProviderCapabilityEntry network;
        private final boolean networkConfigured;
        private final AndroidLocationProviderCapabilityEntry passive;
        private final boolean passiveConfigured;

        private AndroidLocationProviderCapabilitiesConfig(
                AndroidLocationProviderCapabilityEntry gps, boolean gpsConfigured,
                AndroidLocationProviderCapabilityEntry network, boolean networkConfigured,
                AndroidLocationProviderCapabilityEntry passive, boolean passiveConfigured) {
            this.gps = gps;
            this.gpsConfigured = gpsConfigured;
            this.network = network;
            this.networkConfigured = networkConfigured;
            this.passive = passive;
            this.passiveConfigured = passiveConfigured;
        }

        public boolean isGpsConfigured() {
            return gpsConfigured;
        }

        /** {@code gps} entry, or {@code null} when the key is omitted. */
        public AndroidLocationProviderCapabilityEntry getGps() {
            return gps;
        }

        public boolean isNetworkConfigured() {
            return networkConfigured;
        }

        /** {@code network} entry, or {@code null} when the key is omitted. */
        public AndroidLocationProviderCapabilityEntry getNetwork() {
            return network;
        }

        public boolean isPassiveConfigured() {
            return passiveConfigured;
        }

        /** {@code passive} entry, or {@code null} when the key is omitted. */
        public AndroidLocationProviderCapabilityEntry getPassive() {
            return passive;
        }

        /**
         * Lookup by {@code gps}/{@code network}/{@code passive}, or {@code null} when that
         * provider key is omitted or {@code provider} is not one of the three allowed names.
         */
        public AndroidLocationProviderCapabilityEntry get(String provider) {
            if ("gps".equals(provider)) {
                return gps;
            }
            if ("network".equals(provider)) {
                return network;
            }
            if ("passive".equals(provider)) {
                return passive;
            }
            return null;
        }
    }

    /**
     * Immutable view of optional {@code android.identifiers} after parse validation (v1 subset).
     * Never exposes JSONObject/JSONArray or mutable shared state.
     */
    public static final class AndroidIdentifiersConfig {
        private final String advertisingId;
        private final boolean advertisingIdConfigured;
        private final boolean limitAdTracking;
        private final boolean limitAdTrackingConfigured;
        private final String androidId;
        private final boolean androidIdConfigured;
        private final String appSetId;
        private final boolean appSetIdConfigured;
        private final int appSetScope;

        private AndroidIdentifiersConfig(String advertisingId, boolean advertisingIdConfigured,
                                         boolean limitAdTracking, boolean limitAdTrackingConfigured,
                                         String androidId, boolean androidIdConfigured,
                                         String appSetId, boolean appSetIdConfigured,
                                         int appSetScope) {
            this.advertisingId = advertisingId;
            this.advertisingIdConfigured = advertisingIdConfigured;
            this.limitAdTracking = limitAdTracking;
            this.limitAdTrackingConfigured = limitAdTrackingConfigured;
            this.androidId = androidId;
            this.androidIdConfigured = androidIdConfigured;
            this.appSetId = appSetId;
            this.appSetIdConfigured = appSetIdConfigured;
            this.appSetScope = appSetScope;
        }

        public boolean isAdvertisingIdConfigured() {
            return advertisingIdConfigured;
        }

        public String getAdvertisingId() {
            return advertisingId;
        }

        public boolean isLimitAdTrackingConfigured() {
            return limitAdTrackingConfigured;
        }

        public boolean isLimitAdTracking() {
            return limitAdTracking;
        }

        /**
         * Whether {@code androidId} was explicitly present under {@code android.identifiers}.
         * Distinct from default advertising fields: key omission leaves this false (no default ID).
         */
        public boolean isAndroidIdConfigured() {
            return androidIdConfigured;
        }

        /**
         * Normalized lowercase 16-char hex Android secure ID, or {@code null} when the key is absent.
         * Never a host/device-generated value.
         */
        public String getAndroidId() {
            return androidId;
        }

        /**
         * Whether {@code appSetId} was explicitly present under {@code android.identifiers}.
         * Key omission leaves this false (no default and no derivation from other identifiers).
         */
        public boolean isAppSetIdConfigured() {
            return appSetIdConfigured;
        }

        /**
         * Configured App Set ID string, or {@code null} when the key is absent.
         * Never derived from {@code advertisingId}, {@code androidId}, or package name.
         */
        public String getAppSetId() {
            return appSetId;
        }

        /**
         * App Set ID scope: {@code 1} (SCOPE_APP) or {@code 2} (SCOPE_DEVELOPER).
         * When {@code appSetId} is configured and {@code appSetScope} is omitted, this is {@code 1}.
         */
        public int getAppSetScope() {
            return appSetScope;
        }
    }

    /**
     * Immutable view of optional {@code android.userState} after parse validation (v1 subset).
     * Never exposes JSONObject/JSONArray or mutable shared state.
     */
    public static final class AndroidUserStateConfig {
        private final int userId;
        private final boolean userIdConfigured;
        private final long serialNumber;
        private final boolean serialNumberConfigured;
        private final boolean userUnlocked;
        private final boolean userUnlockedConfigured;
        private final boolean systemUser;
        private final boolean systemUserConfigured;
        private final boolean managedProfile;
        private final boolean managedProfileConfigured;
        private final boolean demoUser;
        private final boolean demoUserConfigured;

        private AndroidUserStateConfig(int userId, boolean userIdConfigured,
                                       long serialNumber, boolean serialNumberConfigured,
                                       boolean userUnlocked, boolean userUnlockedConfigured,
                                       boolean systemUser, boolean systemUserConfigured,
                                       boolean managedProfile, boolean managedProfileConfigured,
                                       boolean demoUser, boolean demoUserConfigured) {
            this.userId = userId;
            this.userIdConfigured = userIdConfigured;
            this.serialNumber = serialNumber;
            this.serialNumberConfigured = serialNumberConfigured;
            this.userUnlocked = userUnlocked;
            this.userUnlockedConfigured = userUnlockedConfigured;
            this.systemUser = systemUser;
            this.systemUserConfigured = systemUserConfigured;
            this.managedProfile = managedProfile;
            this.managedProfileConfigured = managedProfileConfigured;
            this.demoUser = demoUser;
            this.demoUserConfigured = demoUserConfigured;
        }

        public boolean isUserIdConfigured() {
            return userIdConfigured;
        }

        public int getUserId() {
            return userId;
        }

        public boolean isSerialNumberConfigured() {
            return serialNumberConfigured;
        }

        public long getSerialNumber() {
            return serialNumber;
        }

        public boolean isUserUnlockedConfigured() {
            return userUnlockedConfigured;
        }

        public boolean isUserUnlocked() {
            return userUnlocked;
        }

        public boolean isSystemUserConfigured() {
            return systemUserConfigured;
        }

        public boolean isSystemUser() {
            return systemUser;
        }

        public boolean isManagedProfileConfigured() {
            return managedProfileConfigured;
        }

        public boolean isManagedProfile() {
            return managedProfile;
        }

        public boolean isDemoUserConfigured() {
            return demoUserConfigured;
        }

        public boolean isDemoUser() {
            return demoUser;
        }
    }

    /**
     * Immutable view of optional {@code android.securitySignals} after parse validation (v1 subset).
     * Never exposes JSONObject/JSONArray or mutable shared state.
     */
    public static final class AndroidSecuritySignalsConfig {
        private final boolean debuggerConnected;
        private final boolean debuggerConnectedConfigured;
        private final boolean waitingForDebugger;
        private final boolean waitingForDebuggerConfigured;
        private final boolean debuggerTracing;
        private final boolean debuggerTracingConfigured;
        private final boolean selinuxEnabled;
        private final boolean selinuxEnabledConfigured;
        private final boolean selinuxEnforced;
        private final boolean selinuxEnforcedConfigured;
        private final boolean userAMonkey;
        private final boolean userAMonkeyConfigured;
        private final boolean userTestHarness;
        private final boolean userTestHarnessConfigured;

        private AndroidSecuritySignalsConfig(boolean debuggerConnected,
                                             boolean debuggerConnectedConfigured,
                                             boolean waitingForDebugger,
                                             boolean waitingForDebuggerConfigured,
                                             boolean debuggerTracing,
                                             boolean debuggerTracingConfigured,
                                             boolean selinuxEnabled,
                                             boolean selinuxEnabledConfigured,
                                             boolean selinuxEnforced,
                                             boolean selinuxEnforcedConfigured,
                                             boolean userAMonkey,
                                             boolean userAMonkeyConfigured,
                                             boolean userTestHarness,
                                             boolean userTestHarnessConfigured) {
            this.debuggerConnected = debuggerConnected;
            this.debuggerConnectedConfigured = debuggerConnectedConfigured;
            this.waitingForDebugger = waitingForDebugger;
            this.waitingForDebuggerConfigured = waitingForDebuggerConfigured;
            this.debuggerTracing = debuggerTracing;
            this.debuggerTracingConfigured = debuggerTracingConfigured;
            this.selinuxEnabled = selinuxEnabled;
            this.selinuxEnabledConfigured = selinuxEnabledConfigured;
            this.selinuxEnforced = selinuxEnforced;
            this.selinuxEnforcedConfigured = selinuxEnforcedConfigured;
            this.userAMonkey = userAMonkey;
            this.userAMonkeyConfigured = userAMonkeyConfigured;
            this.userTestHarness = userTestHarness;
            this.userTestHarnessConfigured = userTestHarnessConfigured;
        }

        public boolean isDebuggerConnectedConfigured() {
            return debuggerConnectedConfigured;
        }

        public boolean isDebuggerConnected() {
            return debuggerConnected;
        }

        public boolean isWaitingForDebuggerConfigured() {
            return waitingForDebuggerConfigured;
        }

        public boolean isWaitingForDebugger() {
            return waitingForDebugger;
        }

        public boolean isDebuggerTracingConfigured() {
            return debuggerTracingConfigured;
        }

        public boolean isDebuggerTracing() {
            return debuggerTracing;
        }

        public boolean isSelinuxEnabledConfigured() {
            return selinuxEnabledConfigured;
        }

        public boolean isSelinuxEnabled() {
            return selinuxEnabled;
        }

        public boolean isSelinuxEnforcedConfigured() {
            return selinuxEnforcedConfigured;
        }

        public boolean isSelinuxEnforced() {
            return selinuxEnforced;
        }

        public boolean isUserAMonkeyConfigured() {
            return userAMonkeyConfigured;
        }

        /**
         * Whether the current user is a monkey (UI automator / test harness signal).
         * Default {@code false} when key omitted under a present node. Independent of debugger and
         * SELinux fields; never cross-inferred.
         */
        public boolean isUserAMonkey() {
            return userAMonkey;
        }

        public boolean isUserTestHarnessConfigured() {
            return userTestHarnessConfigured;
        }

        /**
         * Whether the process is running in a user test harness.
         * Default {@code false} when key omitted under a present node. Independent of
         * {@link #isUserAMonkey()}, debugger, and SELinux fields; never cross-inferred.
         */
        public boolean isUserTestHarness() {
            return userTestHarness;
        }
    }

    /**
     * Immutable view of optional {@code android.securityState} after parse validation (v1 subset).
     * Four independent Keyguard/device lock booleans plus optional biometric canAuthenticate
     * and lastAuthenticationTime markers; never exposes JSONObject/JSONArray.
     */
    public static final class AndroidSecurityStateConfig {
        private final boolean keyguardLocked;
        private final boolean keyguardLockedConfigured;
        private final boolean keyguardSecure;
        private final boolean keyguardSecureConfigured;
        private final boolean deviceLocked;
        private final boolean deviceLockedConfigured;
        private final boolean deviceSecure;
        private final boolean deviceSecureConfigured;
        private final int biometricCanAuthenticateResult;
        private final boolean biometricCanAuthenticateResultConfigured;
        private final long biometricLastAuthenticationElapsedRealtimeMillis;
        private final boolean biometricLastAuthenticationElapsedRealtimeMillisConfigured;

        private AndroidSecurityStateConfig(boolean keyguardLocked, boolean keyguardLockedConfigured,
                                           boolean keyguardSecure, boolean keyguardSecureConfigured,
                                           boolean deviceLocked, boolean deviceLockedConfigured,
                                           boolean deviceSecure, boolean deviceSecureConfigured,
                                           int biometricCanAuthenticateResult,
                                           boolean biometricCanAuthenticateResultConfigured,
                                           long biometricLastAuthenticationElapsedRealtimeMillis,
                                           boolean biometricLastAuthenticationElapsedRealtimeMillisConfigured) {
            this.keyguardLocked = keyguardLocked;
            this.keyguardLockedConfigured = keyguardLockedConfigured;
            this.keyguardSecure = keyguardSecure;
            this.keyguardSecureConfigured = keyguardSecureConfigured;
            this.deviceLocked = deviceLocked;
            this.deviceLockedConfigured = deviceLockedConfigured;
            this.deviceSecure = deviceSecure;
            this.deviceSecureConfigured = deviceSecureConfigured;
            this.biometricCanAuthenticateResult = biometricCanAuthenticateResult;
            this.biometricCanAuthenticateResultConfigured = biometricCanAuthenticateResultConfigured;
            this.biometricLastAuthenticationElapsedRealtimeMillis =
                    biometricLastAuthenticationElapsedRealtimeMillis;
            this.biometricLastAuthenticationElapsedRealtimeMillisConfigured =
                    biometricLastAuthenticationElapsedRealtimeMillisConfigured;
        }

        public boolean isKeyguardLockedConfigured() {
            return keyguardLockedConfigured;
        }

        public boolean isKeyguardLocked() {
            return keyguardLocked;
        }

        public boolean isKeyguardSecureConfigured() {
            return keyguardSecureConfigured;
        }

        public boolean isKeyguardSecure() {
            return keyguardSecure;
        }

        public boolean isDeviceLockedConfigured() {
            return deviceLockedConfigured;
        }

        public boolean isDeviceLocked() {
            return deviceLocked;
        }

        public boolean isDeviceSecureConfigured() {
            return deviceSecureConfigured;
        }

        public boolean isDeviceSecure() {
            return deviceSecure;
        }

        public boolean isBiometricCanAuthenticateResultConfigured() {
            return biometricCanAuthenticateResultConfigured;
        }

        /**
         * Framework {@code BiometricManager.canAuthenticate} result code marker
         * (e.g. 0 success, 12 no hardware). Not inferred from other fields.
         */
        public int getBiometricCanAuthenticateResult() {
            return biometricCanAuthenticateResult;
        }

        public boolean isBiometricLastAuthenticationElapsedRealtimeMillisConfigured() {
            return biometricLastAuthenticationElapsedRealtimeMillisConfigured;
        }

        /**
         * Fixed marker for {@code BiometricManager.getLastAuthenticationTime(int)}:
         * {@code -1} ({@code BIOMETRIC_NO_AUTHENTICATION}) or a nonnegative elapsedRealtime
         * millis value. Not derived from time config or other securityState fields.
         */
        public long getBiometricLastAuthenticationElapsedRealtimeMillis() {
            return biometricLastAuthenticationElapsedRealtimeMillis;
        }
    }

    /**
     * Immutable view of optional {@code android.locale} after parse validation.
     * Never exposes JSONObject/JSONArray or mutable shared state.
     */
    public static final class AndroidLocaleConfig {
        private final String languageTag;
        private final boolean languageTagConfigured;
        private final List<String> languageTags;
        private final boolean languageTagsConfigured;
        private final String timezoneId;
        private final boolean timezoneIdConfigured;

        private AndroidLocaleConfig(String languageTag, boolean languageTagConfigured,
                                    List<String> languageTags, boolean languageTagsConfigured,
                                    String timezoneId, boolean timezoneIdConfigured) {
            this.languageTag = languageTag;
            this.languageTagConfigured = languageTagConfigured;
            this.languageTags = languageTags;
            this.languageTagsConfigured = languageTagsConfigured;
            this.timezoneId = timezoneId;
            this.timezoneIdConfigured = timezoneIdConfigured;
        }

        public boolean isLanguageTagConfigured() {
            return languageTagConfigured;
        }

        /**
         * Canonical BCP 47 language tag ({@link Locale#toLanguageTag()}), never null when configured node present.
         */
        public String getLanguageTag() {
            return languageTag;
        }

        public boolean isLanguageTagsConfigured() {
            return languageTagsConfigured;
        }

        /**
         * Configured BCP 47 tags in JSON order. Empty when the key is omitted or {@code []}.
         * Never inferred from {@link #getLanguageTag()}.
         */
        public List<String> getLanguageTags() {
            return languageTags;
        }

        /**
         * Fresh {@link Locale} view of the canonical language tag (not a shared mutable reference).
         */
        public Locale getLocale() {
            return Locale.forLanguageTag(languageTag);
        }

        public boolean isTimezoneIdConfigured() {
            return timezoneIdConfigured;
        }

        /**
         * Canonical timezone ID ({@link ZoneId#getId()}), never null when configured node present.
         */
        public String getTimezoneId() {
            return timezoneId;
        }

        /**
         * Raw GMT offset in milliseconds for the canonical {@code timezoneId}
         * ({@link TimeZone#getTimeZone(String)} then {@link TimeZone#getRawOffset()}).
         * Excludes DST and current-time {@code getOffset}; does not read {@link TimeZone#getDefault()}.
         */
        public int getRawOffsetMillis() {
            return TimeZone.getTimeZone(timezoneId).getRawOffset();
        }
    }

    /**
     * Immutable view of optional {@code android.drm} after parse validation.
     * Config-only MediaDrm analysis marker model; never exposes JSONObject/JSONArray.
     */
    public static final class AndroidDrmConfig {
        private final boolean available;
        private final boolean availableConfigured;
        private final String marker;
        private final boolean markerConfigured;
        private final List<String> schemeUuids;
        private final boolean schemeUuidsConfigured;
        private final String vendor;
        private final boolean vendorConfigured;
        private final String version;
        private final boolean versionConfigured;
        private final String description;
        private final boolean descriptionConfigured;
        private final String algorithms;
        private final boolean algorithmsConfigured;
        private final String securityLevel;
        private final boolean securityLevelConfigured;
        private final String hdcpLevel;
        private final boolean hdcpLevelConfigured;
        private final String maxHdcpLevel;
        private final boolean maxHdcpLevelConfigured;
        private final boolean provisioned;
        private final boolean provisionedConfigured;
        private final byte[] deviceUniqueId;
        private final boolean deviceUniqueIdConfigured;
        private final byte[] sessionId;
        private final boolean sessionIdConfigured;

        private AndroidDrmConfig(boolean available, boolean availableConfigured,
                                 String marker, boolean markerConfigured,
                                 List<String> schemeUuids, boolean schemeUuidsConfigured,
                                 String vendor, boolean vendorConfigured,
                                 String version, boolean versionConfigured,
                                 String description, boolean descriptionConfigured,
                                 String algorithms, boolean algorithmsConfigured,
                                 String securityLevel, boolean securityLevelConfigured,
                                 String hdcpLevel, boolean hdcpLevelConfigured,
                                 String maxHdcpLevel, boolean maxHdcpLevelConfigured,
                                 boolean provisioned, boolean provisionedConfigured,
                                 byte[] deviceUniqueId, boolean deviceUniqueIdConfigured,
                                 byte[] sessionId, boolean sessionIdConfigured) {
            this.available = available;
            this.availableConfigured = availableConfigured;
            this.marker = marker;
            this.markerConfigured = markerConfigured;
            this.schemeUuids = schemeUuids;
            this.schemeUuidsConfigured = schemeUuidsConfigured;
            this.vendor = vendor;
            this.vendorConfigured = vendorConfigured;
            this.version = version;
            this.versionConfigured = versionConfigured;
            this.description = description;
            this.descriptionConfigured = descriptionConfigured;
            this.algorithms = algorithms;
            this.algorithmsConfigured = algorithmsConfigured;
            this.securityLevel = securityLevel;
            this.securityLevelConfigured = securityLevelConfigured;
            this.hdcpLevel = hdcpLevel;
            this.hdcpLevelConfigured = hdcpLevelConfigured;
            this.maxHdcpLevel = maxHdcpLevel;
            this.maxHdcpLevelConfigured = maxHdcpLevelConfigured;
            this.provisioned = provisioned;
            this.provisionedConfigured = provisionedConfigured;
            this.deviceUniqueId = deviceUniqueId;
            this.deviceUniqueIdConfigured = deviceUniqueIdConfigured;
            this.sessionId = sessionId;
            this.sessionIdConfigured = sessionIdConfigured;
        }

        public boolean isAvailableConfigured() {
            return availableConfigured;
        }

        public boolean isAvailable() {
            return available;
        }

        public boolean isMarkerConfigured() {
            return markerConfigured;
        }

        public String getMarker() {
            return marker;
        }

        public boolean isSchemeUuidsConfigured() {
            return schemeUuidsConfigured;
        }

        /** Immutable list of lowercase canonical UUID strings. */
        public List<String> getSchemeUuids() {
            return schemeUuids;
        }

        public boolean isVendorConfigured() {
            return vendorConfigured;
        }

        public String getVendor() {
            return vendor;
        }

        public boolean isVersionConfigured() {
            return versionConfigured;
        }

        public String getVersion() {
            return version;
        }

        public boolean isDescriptionConfigured() {
            return descriptionConfigured;
        }

        public String getDescription() {
            return description;
        }

        public boolean isAlgorithmsConfigured() {
            return algorithmsConfigured;
        }

        public String getAlgorithms() {
            return algorithms;
        }

        public boolean isSecurityLevelConfigured() {
            return securityLevelConfigured;
        }

        public String getSecurityLevel() {
            return securityLevel;
        }

        public boolean isHdcpLevelConfigured() {
            return hdcpLevelConfigured;
        }

        public String getHdcpLevel() {
            return hdcpLevel;
        }

        public boolean isMaxHdcpLevelConfigured() {
            return maxHdcpLevelConfigured;
        }

        public String getMaxHdcpLevel() {
            return maxHdcpLevel;
        }

        public boolean isProvisionedConfigured() {
            return provisionedConfigured;
        }

        public boolean isProvisioned() {
            return provisioned;
        }

        public boolean isDeviceUniqueIdConfigured() {
            return deviceUniqueIdConfigured;
        }

        /** Defensive copy of decoded device unique id, or {@code null} when absent. */
        public byte[] getDeviceUniqueId() {
            if (!deviceUniqueIdConfigured || deviceUniqueId == null) {
                return null;
            }
            return Arrays.copyOf(deviceUniqueId, deviceUniqueId.length);
        }

        public boolean isSessionIdConfigured() {
            return sessionIdConfigured;
        }

        /** Defensive copy of decoded session id, or {@code null} when absent. */
        public byte[] getSessionId() {
            if (!sessionIdConfigured || sessionId == null) {
                return null;
            }
            return Arrays.copyOf(sessionId, sessionId.length);
        }
    }

    private static final String[] ANDROID_PACKAGES_ALLOWED_KEYS = {
            "packageName", "versionName", "versionCode", "sourceDir", "dataDir",
            "uid", "enabled", "systemApp", "applicationFlags",
            "installerPackageName", "initiatingPackageName", "originatingPackageName",
            "firstInstallTimeMillis", "lastUpdateTimeMillis",
            "permissions", "signaturesHex", "signingCertificateHistoryHex"
    };

    private static final String[] ANDROID_FEATURES_ALLOWED_KEYS = {
            "name", "version"
    };

    /**
     * Allowed keys under root {@code filesystem}:
     * {@code stat}, {@code statfs}, {@code mounts}, {@code links}, {@code externalStorage},
     * {@code systemDirectories}.
     */
    private static final String[] FILESYSTEM_ALLOWED_KEYS = {
            "stat", "statfs", "mounts", "links", "externalStorage", "systemDirectories", "directories"
    };

    /** Allowed fields under {@code filesystem.externalStorage}. */
    private static final String[] FILESYSTEM_EXTERNAL_STORAGE_ALLOWED_KEYS = {
            "directory", "state", "emulated", "removable"
    };

    /** Allowed fields under {@code filesystem.systemDirectories}. */
    private static final String[] FILESYSTEM_SYSTEM_DIRECTORIES_ALLOWED_KEYS = {
            "rootDirectory", "dataDirectory", "downloadCacheDirectory", "storageDirectory"
    };

    private static final String FILESYSTEM_EXTERNAL_STORAGE_DEFAULT_DIRECTORY =
            "/storage/emulated/0";
    private static final String FILESYSTEM_EXTERNAL_STORAGE_DEFAULT_STATE = "mounted";
    private static final boolean FILESYSTEM_EXTERNAL_STORAGE_DEFAULT_EMULATED = true;
    private static final boolean FILESYSTEM_EXTERNAL_STORAGE_DEFAULT_REMOVABLE = false;

    /**
     * Allowed {@code filesystem.externalStorage.state} values
     * (Android {@code Environment.MEDIA_*} string constants).
     */
    private static final Set<String> FILESYSTEM_EXTERNAL_STORAGE_STATES;
    static {
        Set<String> states = new HashSet<String>();
        states.add("unknown");
        states.add("removed");
        states.add("unmounted");
        states.add("checking");
        states.add("nofs");
        states.add("mounted");
        states.add("mounted_ro");
        states.add("shared");
        states.add("bad_removal");
        states.add("unmountable");
        FILESYSTEM_EXTERNAL_STORAGE_STATES = Collections.unmodifiableSet(states);
    }

    /**
     * Allowed fields under each path entry of {@code filesystem.stat} (path is the object key).
     * Uses descriptive JSON names: {@code device}/{@code inode}/{@code blockSize}
     * (not C short names {@code dev}/{@code ino}/{@code blksize}).
     */
    private static final String[] FILESYSTEM_STAT_ALLOWED_KEYS = {
            "device", "inode", "mode", "uid", "gid", "size", "blockSize", "blocks",
            "atimeMillis", "mtimeMillis", "ctimeMillis"
    };

    /**
     * Allowed fields under each mount-point entry of {@code filesystem.statfs}.
     */
    private static final String[] FILESYSTEM_STATFS_ALLOWED_KEYS = {
            "type", "blockSize", "blocks", "blocksFree", "blocksAvailable",
            "files", "filesFree", "fsid", "nameLength", "fragmentSize", "flags"
    };

    /**
     * Allowed fields under each {@code filesystem.mounts[]} entry.
     */
    private static final String[] FILESYSTEM_MOUNTS_ALLOWED_KEYS = {
            "source", "target", "fileSystemType", "options", "dump", "pass",
            "mountId", "parentId", "major", "minor", "root", "mountOptions",
            "optionalFields", "superOptions"
    };

    private static final String[] RANDOM_HEX_KEYS = {
            "devRandomHex",
            "devUrandomHex",
            "devSrandomHex",
            "getrandomHex",
            "stackGuardHex",
            "atRandomHex",
            "mediaDrmDeviceUniqueIdHex"
    };

    public static TraceEnvironmentConfig get(Emulator<?> emulator) {
        return emulator == null ? null : emulator.get(KEY);
    }

    public static TraceEnvironmentConfig fromSystemProperty() {
        String path = System.getProperty(SYSTEM_PROPERTY);
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        return load(path);
    }

    public static TraceEnvironmentConfig load(String path) {
        return load(new File(path));
    }

    public static TraceEnvironmentConfig load(File file) {
        try {
            return parse(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("load trace environment config failed: " + file, e);
        }
    }

    public static TraceEnvironmentConfig parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("json is empty");
        }
        // OrderedField: preserve JSON object key order for map snapshots
        // (systemProperties / environmentVariables / similar LinkedHashMap views).
        JSONObject root = JSON.parseObject(json, Feature.OrderedField);
        TraceEnvironmentConfig config = new TraceEnvironmentConfig(root);
        config.validate();
        return config;
    }

    private final JSONObject root;
    /**
     * Explicit {@code process.pgid} after parse, or {@code null} when the key is absent.
     * Never inferred from pid/ppid/sid.
     */
    private Integer processPgid;
    /**
     * Explicit {@code process.sid} after parse, or {@code null} when the key is absent.
     * Never inferred from pid/ppid/pgid.
     */
    private Integer processSid;
    /**
     * Whether {@code process.supplementaryGids} was present (including an explicit {@code []}).
     * Never inferred from {@code gid}/{@code egid}.
     */
    private boolean supplementaryGidsConfigured;
    /**
     * Immutable supplementary gids in JSON array order; empty when the key is absent or {@code []}.
     */
    private List<Integer> supplementaryGids = Collections.emptyList();
    private List<NetworkInterfaceConfig> networkInterfaces = Collections.emptyList();
    private List<NetworkIpv4RouteConfig> networkIpv4Routes = Collections.emptyList();
    private List<NetworkInterfaceStatsConfig> networkInterfaceStats = Collections.emptyList();
    private List<NetworkIpv6AddressConfig> networkIpv6Addresses = Collections.emptyList();
    private boolean networkTcpConfigured;
    private List<NetworkTcpConfig> networkTcp = Collections.emptyList();
    private boolean networkTcp6Configured;
    private List<NetworkTcpConfig> networkTcp6 = Collections.emptyList();
    private boolean networkCapabilitiesConfigured;
    private NetworkCapabilitiesConfig networkCapabilitiesConfig;
    private boolean linuxProcessesConfigured;
    private List<LinuxProcessConfig> linuxProcesses = Collections.emptyList();
    private boolean linuxCommandsConfigured;
    private Map<String, String> linuxCommands = Collections.emptyMap();
    private boolean linuxMincoreConfigured;
    private boolean linuxMincoreResident;
    private List<NetworkArpEntryConfig> networkArpEntries = Collections.emptyList();
    private List<NetworkIgmpMembershipConfig> networkIgmpMemberships = Collections.emptyList();
    private List<NetworkIgmp6MembershipConfig> networkIgmp6Memberships = Collections.emptyList();
    private List<NetworkLinkLayerMulticastEntryConfig> networkLinkLayerMulticastEntries =
            Collections.emptyList();
    private NetworkWirelessProcStatsConfig networkWirelessProcStats;

    /** Whether {@code network.bluetooth} was present (including explicit empty object). */
    private boolean networkBluetoothConfigured;
    private NetworkBluetoothConfig networkBluetoothConfig;
    private List<String> networkLinkDnsServers = Collections.emptyList();
    private List<PackageConfig> androidPackages = Collections.emptyList();
    private List<FeatureConfig> androidFeatures = Collections.emptyList();
    /** Whether {@code android.accounts} was present (including an explicit empty array). */
    private boolean androidAccountsConfigured;
    private List<AndroidAccountConfig> androidAccounts = Collections.emptyList();
    /** Whether {@code android.inputMethods} was present (including an explicit empty array). */
    private boolean androidInputMethodsConfigured;
    private List<AndroidInputMethodConfig> androidInputMethods = Collections.emptyList();
    private List<FileStatConfig> filesystemStats = Collections.emptyList();
    private List<FileStatFsConfig> filesystemStatFsEntries = Collections.emptyList();
    private List<FileSystemMountConfig> filesystemMounts = Collections.emptyList();
    private List<FileSystemLinkConfig> filesystemLinks = Collections.emptyList();
    private boolean filesystemDirectoriesConfigured;
    private Map<String, List<String>> filesystemDirectories = Collections.emptyMap();

    /** Whether {@code filesystem.externalStorage} was present (including explicit empty object). */
    private boolean filesystemExternalStorageConfigured;
    private FileSystemExternalStorageConfig filesystemExternalStorageConfig;

    /**
     * Whether {@code filesystem.systemDirectories} was present (including explicit empty object).
     * Individual path fields remain independently presence-tracked on the config object.
     */
    private boolean filesystemSystemDirectoriesConfigured;
    private FileSystemSystemDirectoriesConfig filesystemSystemDirectoriesConfig;

    /** Whether {@code linux.proc} was present (including explicit empty object). */
    private boolean linuxProcConfigured;
    private LinuxProcConfig linuxProcConfig;

    /** Whether {@code linux.auxv} was present (including explicit empty object). */
    private boolean linuxAuxvConfigured;
    private LinuxAuxvConfig linuxAuxvConfig;

    /** Whether {@code linux.cpu} was present (including explicit empty object). */
    private boolean linuxCpuConfigured;
    private LinuxCpuConfig linuxCpuConfig;

    /** Whether {@code linux.rlimits} was present (including explicit empty object). */
    private boolean linuxRlimitsConfigured;
    private LinuxRlimitsConfig linuxRlimitsConfig;

    /** Whether {@code linux.sysinfo} was present. */
    private boolean linuxSysinfoConfigured;
    private LinuxSysinfoConfig linuxSysinfoConfig;

    /**
     * Whether {@code linux.environ} was present (including an explicit empty array).
     * When false, loaders preserve built-in default environ entries.
     */
    private boolean linuxEnvironConfigured;
    /** Immutable list of {@code KEY=VALUE} entries; empty when configured as {@code []}. */
    private List<String> linuxEnviron;

    private static final String[] LINUX_PROC_ALLOWED_KEYS = {
            "state", "tracerPid", "threadCount", "cmdline", "cgroups",
            "startTimeTicks", "virtualMemoryBytes", "residentSetPages",
            "rchar", "wchar", "syscr", "syscw", "readBytes", "writeBytes", "cancelledWriteBytes",
            "bootId", "randomUuid", "entropyAvail", "randomPoolSize",
            "writeWakeupThreshold", "urandomMinReseedSecs",
            "oomScoreAdj", "oomScore", "oomAdj", "selinuxContext", "fileSelinuxContexts",
            "comm", "wchan", "dumpable", "nice", "seccompMode", "noNewPrivs", "capEffectiveHex",
            "capInheritableHex", "capPermittedHex", "capBoundingHex", "capAmbientHex",
            "signalBlockedHex", "signalIgnoredHex", "signalCaughtHex", "limits"
    };

    /** Exactly 36 ASCII chars: 8-4-4-4-12 hex digits with hyphens (case-insensitive). */
    private static final Pattern LINUX_PROC_BOOT_ID_CANONICAL = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final String[] LINUX_AUXV_ALLOWED_KEYS = {
            "hwcap32", "hwcap2_32", "hwcap64", "hwcap2_64",
            "platform32", "platform64", "execFn"
    };

    private static final String[] LINUX_CPU_ALLOWED_KEYS = {
            "affinityMaskHex", "online", "offline", "present", "possible",
            "configuredProcessorCount", "onlineProcessorCount"
    };

    private static final String[] LINUX_RLIMITS_ALLOWED_KEYS = {
            "nofile"
    };

    private static final String[] LINUX_RLIMITS_NOFILE_ALLOWED_KEYS = {
            "soft", "hard"
    };

    private static final String[] LINUX_SYSINFO_ALLOWED_KEYS = {
            "uptime", "loads", "totalRam", "freeRam", "sharedRam", "bufferRam",
            "totalSwap", "freeSwap", "procs", "memUnit"
    };
    private static final int LINUX_SYSINFO_PROCS_MAX = 65535;
    private static final long LINUX_SYSINFO_MEM_UNIT_MIN = 1L;
    /** ARM32 {@code __kernel_long_t} max for non-negative {@code uptime}. */
    private static final long LINUX_SYSINFO_UPTIME_MAX = Integer.MAX_VALUE;

    private static final int LINUX_CPU_PROCESSOR_COUNT_MIN = 1;
    private static final int LINUX_CPU_PROCESSOR_COUNT_MAX = 4096;

    /** Env key: {@code [A-Za-z_][A-Za-z0-9_]*}. */
    private static final Pattern LINUX_ENVIRON_KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final int LINUX_ENVIRON_ENTRY_MAX_CHARS = 4096;

    /**
     * Exact built-in default environ entries used by {@code AndroidElfLoader} and
     * {@code /proc/self|pid/environ} when {@code linux.environ} is absent. Immutable.
     */
    public static final List<String> LINUX_ENVIRON_BUILTIN_DEFAULTS =
            Collections.unmodifiableList(Arrays.asList(
                    "ANDROID_DATA=/data",
                    "ANDROID_ROOT=/system",
                    "PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin",
                    "NO_ADDR_COMPAT_LAYOUT_FIXUP=1"
            ));

    private static final String LINUX_AUXV_DEFAULT_PLATFORM32 = "v7l";
    private static final String LINUX_AUXV_DEFAULT_PLATFORM64 = "aarch64";
    private static final int LINUX_AUXV_PLATFORM_UTF8_MAX = 64;
    private static final int LINUX_AUXV_EXECFN_UTF8_MAX = 4096;
    /** Max for 32-bit AT_HWCAP/AT_HWCAP2 so values fit without silent truncation. */
    private static final long LINUX_AUXV_HWCAP32_MAX = 0xffffffffL;

    private static final String LINUX_CPU_DEFAULT_AFFINITY_MASK_HEX = "01";
    private static final int LINUX_CPU_AFFINITY_MASK_BYTES_MIN = 1;
    private static final int LINUX_CPU_AFFINITY_MASK_BYTES_MAX = 1024;

    private static final Set<String> LINUX_PROC_STATES;
    static {
        Set<String> states = new HashSet<String>();
        states.add("R");
        states.add("S");
        states.add("D");
        states.add("Z");
        states.add("T");
        states.add("t");
        states.add("X");
        states.add("I");
        LINUX_PROC_STATES = Collections.unmodifiableSet(states);
    }

    /** Whether {@code android.tee} was present (including explicit empty object). */
    private boolean androidTeeConfigured;
    private Boolean teeAvailable;
    private boolean teeAvailableConfigured;
    private String teeSecurityLevel;
    private boolean teeSecurityLevelConfigured;
    private Integer teeKeymasterVersion;
    private boolean teeKeymasterVersionConfigured;
    private Boolean teeStrongBoxAvailable;
    private boolean teeStrongBoxAvailableConfigured;
    private String teeMarker;
    private boolean teeMarkerConfigured;
    private byte[] teeKeyBlob;
    private boolean teeKeyBlobConfigured;
    private String teeKeyAlgorithm;
    private boolean teeKeyAlgorithmConfigured;
    private String teeKeyFormat;
    private boolean teeKeyFormatConfigured;

    /** Whether {@code android.drm} was present (including explicit empty object). */
    private boolean androidDrmConfigured;
    private AndroidDrmConfig androidDrmConfig;

    /** Whether {@code android.locale} was present (including explicit empty object). */
    private boolean androidLocaleConfigured;
    private AndroidLocaleConfig androidLocaleConfig;

    /** Whether {@code android.display} was present (including explicit empty object). */
    private boolean androidDisplayConfigured;
    private AndroidDisplayConfig androidDisplayConfig;
    /** Whether {@code android.displays} was present (including an explicit empty array). */
    private boolean androidDisplaysConfigured;
    private List<AndroidDisplayEntryConfig> androidDisplays = Collections.emptyList();

    /** Whether {@code android.configuration} was present (including explicit empty object). */
    private boolean androidConfigurationConfigured;
    private AndroidConfigurationConfig androidConfigurationConfig;

    /** Whether {@code android.power} was present (including explicit empty object). */
    private boolean androidPowerConfigured;
    private AndroidPowerConfig androidPowerConfig;

    /** Whether {@code android.powerProfile} was present (including explicit empty object). */
    private boolean androidPowerProfileConfigured;
    private AndroidPowerProfileConfig androidPowerProfileConfig;

    /** Whether {@code android.telephony.cellInfo} was present (including an explicit empty array). */
    private boolean androidCellInfoConfigured;
    private List<CellInfoConfig> androidCellInfo = Collections.emptyList();

    /** Whether {@code network.wifi.scanResults} was present (including an explicit empty array). */
    private boolean networkWifiScanResultsConfigured;
    private List<WifiScanResultConfig> networkWifiScanResults = Collections.emptyList();

    /** Whether {@code android.thermal} was present (including explicit empty object). */
    private boolean androidThermalConfigured;
    private AndroidThermalConfig androidThermalConfig;

    /** Whether top-level {@code graphics} was present (including an explicit empty object). */
    private boolean graphicsConfigured;
    private GraphicsConfig graphicsConfig;

    /** Set only by {@link DeviceFingerprintProfile}; existing {@link #parse} stays false/null. */
    private boolean profileLoaded;
    private String profileName;
    private ProfileFileOverlay profileFileOverlay;
    private Map<String, Object> profileReservedFields = Collections.emptyMap();

    /** Whether {@code android.battery} was present (including explicit empty object). */
    private boolean androidBatteryConfigured;
    private AndroidBatteryConfig androidBatteryConfig;

    /** Whether {@code android.cameras} was present (including explicit empty object). */
    private boolean androidCamerasConfigured;
    private AndroidCamerasConfig androidCamerasConfig;

    /** Whether {@code android.sensors} was present (including explicit empty object). */
    private boolean androidSensorsConfigured;
    private AndroidSensorsConfig androidSensorsConfig;
    private boolean androidSensorSamplesConfigured;
    private Map<Integer, float[]> androidSensorSamples = Collections.emptyMap();

    /** Whether {@code android.clipboard} was present (including explicit empty object). */
    private boolean androidClipboardConfigured;
    private AndroidClipboardConfig androidClipboardConfig;

    /** Whether {@code android.accessibility} was present (including explicit empty object). */
    private boolean androidAccessibilityConfigured;
    private AndroidAccessibilityConfig androidAccessibilityConfig;

    /** Whether {@code android.audio} was present (including explicit empty object). */
    private boolean androidAudioConfigured;
    private AndroidAudioConfig androidAudioConfig;

    /**
     * Whether {@code android.build.SUPPORTED_ABIS} was present as a validated non-empty string array.
     * Independent of other generic scalar {@code android.build} fields and of 32-bit ABIs.
     */
    private boolean androidBuildSupportedAbisConfigured;
    private List<String> androidBuildSupportedAbis;

    /**
     * Whether {@code android.build.SUPPORTED_32_BIT_ABIS} was present as a validated non-empty string
     * array. Independent of {@code SUPPORTED_ABIS} (never cross-inferred).
     */
    private boolean androidBuildSupported32BitAbisConfigured;
    private List<String> androidBuildSupported32BitAbis;

    /**
     * Whether {@code android.build.SUPPORTED_64_BIT_ABIS} was present as a validated non-empty string
     * array. Independent of {@code SUPPORTED_ABIS} / {@code SUPPORTED_32_BIT_ABIS} (never cross-inferred).
     */
    private boolean androidBuildSupported64BitAbisConfigured;
    private List<String> androidBuildSupported64BitAbis;

    /**
     * Whether {@code android.build.TIME} was present and validated as exact nonnegative long.
     * Independent of scalar string/int build fields and ABI arrays.
     */
    private boolean androidBuildTimeConfigured;
    private long androidBuildTime;

    /**
     * Whether {@code android.runtime.systemProperties} was present (including explicit empty
     * object). Independent of host JVM properties and of environmentVariables; no fallback.
     */
    private boolean androidRuntimeSystemPropertiesConfigured;
    private Map<String, String> androidRuntimeSystemProperties;

    /**
     * Whether {@code android.runtime.environmentVariables} was present (including explicit empty
     * object). Independent of systemProperties and of {@code linux.environ}; no host fallback.
     */
    private boolean androidRuntimeEnvironmentVariablesConfigured;
    private Map<String, String> androidRuntimeEnvironmentVariables;

    /**
     * Whether {@code android.runtime.availableProcessors} was present and validated as exact int
     * {@code 1..4096}. Independent of systemProperties/environmentVariables and of
     * {@code linux.cpu}; no host {@code Runtime.availableProcessors()} fallback.
     */
    private boolean androidRuntimeAvailableProcessorsConfigured;
    private int androidRuntimeAvailableProcessors;

    /**
     * 是否存在已校验的 {@code android.runtime.maxMemoryBytes}（精确 JSON 整数
     * {@code 1..Long.MAX_VALUE}）。与 systemProperties / environmentVariables /
     * availableProcessors / {@code linux.cpu} / 宿主 JVM 独立；无宿主
     * {@code Runtime.maxMemory()} 回落。
     */
    private boolean androidRuntimeMaxMemoryBytesConfigured;
    private long androidRuntimeMaxMemoryBytes;

    /**
     * 是否存在已校验的 {@code android.runtime.totalMemoryBytes}（精确 JSON 整数
     * {@code 1..Long.MAX_VALUE}）。与 systemProperties / environmentVariables /
     * availableProcessors / {@code linux.cpu} / 宿主 JVM 独立；无宿主
     * {@code Runtime.totalMemory()} 回落。若与 {@code maxMemoryBytes} 同时显式配置，
     * 解析阶段拒绝 {@code totalMemoryBytes > maxMemoryBytes}。
     */
    private boolean androidRuntimeTotalMemoryBytesConfigured;
    private long androidRuntimeTotalMemoryBytes;

    /**
     * 是否存在已校验的 {@code android.runtime.freeMemoryBytes}（精确 JSON 整数
     * {@code 0..Long.MAX_VALUE}）。不能单独出现：解析期要求
     * {@code android.runtime.totalMemoryBytes} 已显式配置，并拒绝
     * {@code freeMemoryBytes > totalMemoryBytes}。若同时存在 {@code maxMemoryBytes}，
     * 已有 {@code total <= max} 检查继续生效，因此
     * {@code 0 <= free <= total <= max}。与 systemProperties / environmentVariables /
     * availableProcessors / {@code linux.cpu} / 宿主 JVM 独立；无宿主
     * {@code Runtime.freeMemory()} 回落。
     */
    private boolean androidRuntimeFreeMemoryBytesConfigured;
    private long androidRuntimeFreeMemoryBytes;

    /** Whether {@code android.location} was present (including explicit empty object). */
    private boolean androidLocationConfigured;
    private AndroidLocationConfig androidLocationConfig;

    /**
     * Whether {@code android.location.providers} was present (including explicit empty object).
     * Parent {@code android.location} without {@code providers} leaves this false.
     */
    private boolean androidLocationProvidersConfigured;
    private AndroidLocationProvidersConfig androidLocationProvidersConfig;

    /**
     * Whether {@code android.location.lastKnownLocations} was present (including explicit empty array).
     * Parent {@code android.location} without the key leaves this false. Independent of
     * {@code enabled}/{@code providers}.
     */
    private boolean androidLocationLastKnownLocationsConfigured;
    private List<AndroidLastKnownLocationConfig> androidLocationLastKnownLocations;

    /**
     * Whether {@code android.location.providerCapabilities} was present (including explicit empty
     * object). Parent {@code android.location} without the key leaves this false. Independent of
     * {@code enabled}/{@code providers}/{@code lastKnownLocations}.
     */
    private boolean androidLocationProviderCapabilitiesConfigured;
    private AndroidLocationProviderCapabilitiesConfig androidLocationProviderCapabilitiesConfig;

    /** Whether {@code android.identifiers} was present (including explicit empty object). */
    private boolean androidIdentifiersConfigured;
    private AndroidIdentifiersConfig androidIdentifiersConfig;

    /** Whether {@code android.userState} was present (including explicit empty object). */
    private boolean androidUserStateConfigured;
    private AndroidUserStateConfig androidUserStateConfig;

    /** Whether {@code android.securitySignals} was present (including explicit empty object). */
    private boolean androidSecuritySignalsConfigured;
    private AndroidSecuritySignalsConfig androidSecuritySignalsConfig;

    /** Whether {@code android.securityState} was present (including explicit empty object). */
    private boolean androidSecurityStateConfigured;
    private AndroidSecurityStateConfig androidSecurityStateConfig;

    private static final String[] ANDROID_TEE_ALLOWED_KEYS = {
            "available", "securityLevel", "keymasterVersion",
            "strongBoxAvailable", "marker", "keyBlobHex",
            "keyAlgorithm", "keyFormat"
    };

    private static final String[] ANDROID_DRM_ALLOWED_KEYS = {
            "available", "marker", "schemeUuids", "vendor", "version", "description",
            "algorithms", "securityLevel", "hdcpLevel", "maxHdcpLevel", "provisioned",
            "deviceUniqueIdHex", "sessionIdHex"
    };

    private static final String[] ANDROID_LOCALE_ALLOWED_KEYS = {
            "languageTag", "languageTags", "timezoneId"
    };

    private static final String[] ANDROID_DISPLAY_ALLOWED_KEYS = {
            "widthPixels", "heightPixels", "densityDpi", "scaledDensity", "xdpi", "ydpi",
            "refreshRate", "rotation", "modeId", "uniqueId"
    };

    private static final String[] ANDROID_CONFIGURATION_ALLOWED_KEYS = {
            "orientation", "screenLayout", "uiMode", "fontScale", "densityDpi",
            "screenWidthDp", "screenHeightDp", "smallestScreenWidthDp",
            "keyboard", "navigation",
            "keyboardHidden", "hardKeyboardHidden", "navigationHidden"
    };

    private static final String[] ANDROID_POWER_ALLOWED_KEYS = {
            "interactive", "powerSaveMode", "deviceIdleMode", "deviceLightIdleMode",
            "lowPowerStandbyEnabled", "sustainedPerformanceModeSupported",
            "rebootingUserspaceSupported", "ignoringBatteryOptimizations"
    };

    private static final String[] GRAPHICS_ALLOWED_KEYS = {
            "vendor", "renderer", "version", "shadingLanguageVersion", "extensions",
            "eglVendor", "eglVersion", "eglExtensions"
    };
    private static final int GRAPHICS_STRING_MAX_LEN = 256;
    private static final int GRAPHICS_EXTENSIONS_MAX = 128;

    private static final String[] ANDROID_THERMAL_ALLOWED_KEYS = {
            "currentThermalStatus", "headroom"
    };

    private static final String[] ANDROID_BATTERY_ALLOWED_KEYS = {
            "capacityPercent", "charging", "chargeCounterUah", "currentNowUa", "currentAverageUa",
            "energyCounterNwh", "status", "chargeTimeRemainingMillis", "plugged",
            "health", "voltageMv", "temperatureTenthsC"
    };

    private static final String[] ANDROID_POWER_PROFILE_ALLOWED_KEYS = {
            "averagePower"
    };

    private static final String[] ANDROID_CELL_INFO_ALLOWED_KEYS = {
            "type", "registered", "mcc", "mnc", "ci", "pci", "tac", "earfcn",
            "alphaLong", "alphaShort"
    };

    private static final String[] ANDROID_CELL_INFO_TYPES = {
            "gsm", "cdma", "lte", "wcdma", "nr"
    };

    private static final String[] NETWORK_WIFI_SCAN_RESULT_ALLOWED_KEYS = {
            "ssid", "bssid", "rssi", "frequencyMhz"
    };

    private static final String[] ANDROID_CAMERAS_ALLOWED_KEYS = {
            "count", "infos", "streams"
    };

    private static final String[] ANDROID_CAMERA_STREAM_ALLOWED_KEYS = {
            "cameraId", "width", "height", "previewHex", "jpegHex", "previewFile", "jpegFile"
    };
    private static final int ANDROID_CAMERA_FILE_PATH_MAX = 256;
    private static final int ANDROID_CAMERA_STREAM_SIZE_MIN = 1;
    private static final int ANDROID_CAMERA_STREAM_SIZE_MAX = 8192;

    private static final String[] ANDROID_CAMERA_INFO_ALLOWED_KEYS = {
            "facing", "orientation", "canDisableShutterSound"
    };

    private static final String[] ANDROID_SENSORS_ALLOWED_KEYS = {
            "types", "dynamicTypes", "dynamicDiscoverySupported", "names", "vendors", "versions",
            "stringTypes", "maximumRanges", "resolutions", "powers", "minDelaysMicros",
            "maxDelaysMicros", "fifoReservedEventCounts", "fifoMaxEventCounts", "wakeUpSensors",
            "sensorIds", "reportingModes", "dynamicSensors", "requiredPermissions",
            "additionalInfoSupported", "highestDirectReportRateLevels",
            "directChannelTypesSupported", "samples"
    };

    private static final String[] ANDROID_CLIPBOARD_ALLOWED_KEYS = {
            "hasPrimaryClip", "primaryText", "primaryLabel", "timestampMillis"
    };

    private static final String[] ANDROID_ACCESSIBILITY_ALLOWED_KEYS = {
            "enabled", "touchExplorationEnabled", "highContrastTextEnabled", "services"
    };

    private static final String[] ANDROID_AUDIO_ALLOWED_KEYS = {
            "musicActive", "speakerphoneOn", "ringerMode", "mode", "properties", "streamVolumes"
    };

    /** Allowed fields under each {@code android.audio.streamVolumes[]} entry. */
    private static final String[] ANDROID_AUDIO_STREAM_VOLUME_ENTRY_ALLOWED_KEYS = {
            "streamType", "volume", "maxVolume", "minVolume"
    };

    /** Allowed fields under {@code android.location} (enabled + providers + lastKnownLocations + providerCapabilities). */
    private static final String[] ANDROID_LOCATION_ALLOWED_KEYS = {
            "enabled", "providers", "lastKnownLocations", "providerCapabilities"
    };

    /** Allowed fields under {@code android.location.providers}. */
    private static final String[] ANDROID_LOCATION_PROVIDERS_ALLOWED_KEYS = {
            "gps", "network", "passive"
    };

    /** Allowed fields under each {@code android.location.providerCapabilities} provider entry. */
    private static final String[] ANDROID_LOCATION_PROVIDER_CAPABILITY_ENTRY_ALLOWED_KEYS = {
            "requiresNetwork", "requiresSatellite", "requiresCell", "hasMonetaryCost",
            "supportsAltitude", "supportsSpeed", "supportsBearing", "meetsCriteria",
            "accuracy", "powerRequirement"
    };

    /** Allowed fields under each {@code android.location.lastKnownLocations[]} entry. */
    private static final String[] ANDROID_LOCATION_LAST_KNOWN_ENTRY_ALLOWED_KEYS = {
            "provider", "latitude", "longitude", "altitude", "accuracyMeters",
            "timeMillis", "elapsedRealtimeNanos", "mock",
            "speedMetersPerSecond", "bearingDegrees",
            "verticalAccuracyMeters", "speedAccuracyMetersPerSecond", "bearingAccuracyDegrees"
    };

    private static final double ANDROID_LOCATION_LATITUDE_MIN = -90.0d;
    private static final double ANDROID_LOCATION_LATITUDE_MAX = 90.0d;
    private static final double ANDROID_LOCATION_LONGITUDE_MIN = -180.0d;
    private static final double ANDROID_LOCATION_LONGITUDE_MAX = 180.0d;
    private static final long ANDROID_LOCATION_DEFAULT_TIME_MILLIS = 0L;
    private static final long ANDROID_LOCATION_DEFAULT_ELAPSED_REALTIME_NANOS = 0L;
    private static final boolean ANDROID_LOCATION_DEFAULT_MOCK = false;
    private static final double ANDROID_LOCATION_DEFAULT_ALTITUDE = 0.0d;
    private static final float ANDROID_LOCATION_DEFAULT_ACCURACY_METERS = 0.0f;
    private static final float ANDROID_LOCATION_DEFAULT_SPEED_METERS_PER_SECOND = 0.0f;
    private static final float ANDROID_LOCATION_DEFAULT_BEARING_DEGREES = 0.0f;
    private static final float ANDROID_LOCATION_DEFAULT_VERTICAL_ACCURACY_METERS = 0.0f;
    private static final float ANDROID_LOCATION_DEFAULT_SPEED_ACCURACY_METERS_PER_SECOND = 0.0f;
    private static final float ANDROID_LOCATION_DEFAULT_BEARING_ACCURACY_DEGREES = 0.0f;
    private static final float ANDROID_LOCATION_BEARING_MIN_INCLUSIVE = 0.0f;
    private static final float ANDROID_LOCATION_BEARING_MAX_EXCLUSIVE = 360.0f;

    private static final String[] ANDROID_IDENTIFIERS_ALLOWED_KEYS = {
            "advertisingId", "limitAdTracking", "androidId", "appSetId", "appSetScope"
    };

    private static final int ANDROID_IDENTIFIERS_ANDROID_ID_HEX_LEN = 16;
    private static final int ANDROID_IDENTIFIERS_APP_SET_ID_MIN_LEN = 1;
    private static final int ANDROID_IDENTIFIERS_APP_SET_ID_MAX_LEN = 150;
    private static final int ANDROID_IDENTIFIERS_APP_SET_SCOPE_APP = 1;
    private static final int ANDROID_IDENTIFIERS_APP_SET_SCOPE_DEVELOPER = 2;
    private static final int ANDROID_IDENTIFIERS_DEFAULT_APP_SET_SCOPE =
            ANDROID_IDENTIFIERS_APP_SET_SCOPE_APP;

    private static final String[] ANDROID_USER_STATE_ALLOWED_KEYS = {
            "userId", "serialNumber", "userUnlocked", "systemUser", "managedProfile", "demoUser"
    };

    private static final String[] ANDROID_SECURITY_SIGNALS_ALLOWED_KEYS = {
            "debuggerConnected", "waitingForDebugger", "debuggerTracing",
            "selinuxEnabled", "selinuxEnforced", "userAMonkey", "userTestHarness"
    };

    private static final String[] ANDROID_SECURITY_STATE_ALLOWED_KEYS = {
            "keyguardLocked", "keyguardSecure", "deviceLocked", "deviceSecure",
            "biometricCanAuthenticateResult",
            "biometricLastAuthenticationElapsedRealtimeMillis"
    };

    private static final boolean ANDROID_POWER_DEFAULT_INTERACTIVE = true;
    private static final boolean ANDROID_POWER_DEFAULT_POWER_SAVE_MODE = false;
    private static final boolean ANDROID_POWER_DEFAULT_DEVICE_IDLE_MODE = false;
    private static final boolean ANDROID_POWER_DEFAULT_DEVICE_LIGHT_IDLE_MODE = false;
    private static final boolean ANDROID_POWER_DEFAULT_LOW_POWER_STANDBY_ENABLED = false;
    private static final boolean ANDROID_POWER_DEFAULT_SUSTAINED_PERFORMANCE_MODE_SUPPORTED = false;
    private static final boolean ANDROID_POWER_DEFAULT_REBOOTING_USERSPACE_SUPPORTED = false;
    private static final boolean ANDROID_POWER_DEFAULT_IGNORING_BATTERY_OPTIMIZATIONS = false;

    private static final int ANDROID_THERMAL_DEFAULT_CURRENT_THERMAL_STATUS = 0;
    private static final int ANDROID_THERMAL_STATUS_MIN = 0;
    private static final int ANDROID_THERMAL_STATUS_MAX = 6;
    private static final float ANDROID_THERMAL_DEFAULT_HEADROOM = 1.0f;

    private static final int ANDROID_BATTERY_DEFAULT_CAPACITY_PERCENT = 73;
    private static final int ANDROID_BATTERY_CAPACITY_PERCENT_MIN = 0;
    private static final int ANDROID_BATTERY_CAPACITY_PERCENT_MAX = 100;
    private static final boolean ANDROID_BATTERY_DEFAULT_CHARGING = false;
    /** No default: field must be present for propertyId=1 JNI. Range 0..Integer.MAX_VALUE. */
    private static final int ANDROID_BATTERY_CHARGE_COUNTER_UAH_MIN = 0;
    private static final int ANDROID_BATTERY_CHARGE_COUNTER_UAH_MAX = Integer.MAX_VALUE;
    /** No default: field must be present for propertyId=2 JNI. Full signed int range. */
    private static final int ANDROID_BATTERY_CURRENT_NOW_UA_MIN = Integer.MIN_VALUE;
    private static final int ANDROID_BATTERY_CURRENT_NOW_UA_MAX = Integer.MAX_VALUE;
    /** No default: field must be present for propertyId=3 JNI. Full signed int range. */
    private static final int ANDROID_BATTERY_CURRENT_AVERAGE_UA_MIN = Integer.MIN_VALUE;
    private static final int ANDROID_BATTERY_CURRENT_AVERAGE_UA_MAX = Integer.MAX_VALUE;
    /** No default: field must be present for getLongProperty propertyId=5. Range 0..Long.MAX_VALUE. */
    private static final long ANDROID_BATTERY_ENERGY_COUNTER_NWH_MIN = 0L;
    private static final long ANDROID_BATTERY_ENERGY_COUNTER_NWH_MAX = Long.MAX_VALUE;
    /**
     * No default: field must be present for getIntProperty propertyId=6.
     * Android BATTERY_STATUS_*: UNKNOWN=1 .. FULL=5.
     */
    private static final int ANDROID_BATTERY_STATUS_MIN = 1;
    private static final int ANDROID_BATTERY_STATUS_MAX = 5;
    /**
     * No default: field must be present for computeChargeTimeRemaining()J.
     * -1 = unable to compute (Android documented marker); otherwise nonnegative millis.
     */
    private static final long ANDROID_BATTERY_CHARGE_TIME_REMAINING_MILLIS_MIN = -1L;
    private static final long ANDROID_BATTERY_CHARGE_TIME_REMAINING_MILLIS_MAX = Long.MAX_VALUE;
    /** BatteryManager.BATTERY_HEALTH_UNKNOWN..COLD (1..7). */
    private static final int ANDROID_BATTERY_HEALTH_MIN = 1;
    private static final int ANDROID_BATTERY_HEALTH_MAX = 7;
    private static final int ANDROID_BATTERY_VOLTAGE_MV_MIN = 0;
    private static final int ANDROID_BATTERY_VOLTAGE_MV_MAX = Integer.MAX_VALUE;
    private static final int ANDROID_BATTERY_TEMPERATURE_TENTHS_C_MIN = -2000;
    private static final int ANDROID_BATTERY_TEMPERATURE_TENTHS_C_MAX = 2000;

    private static final int ANDROID_CAMERAS_DEFAULT_COUNT = 0;
    private static final int ANDROID_CAMERAS_COUNT_MIN = 0;
    private static final int ANDROID_CAMERAS_COUNT_MAX = 16;
    private static final int ANDROID_CAMERA_FACING_MIN = 0;
    private static final int ANDROID_CAMERA_FACING_MAX = 2;
    /** Camera.CameraInfo orientation degrees: 0, 90, 180, 270. */
    private static final Set<Integer> ANDROID_CAMERA_ORIENTATIONS;
    static {
        Set<Integer> orientations = new HashSet<Integer>();
        orientations.add(0);
        orientations.add(90);
        orientations.add(180);
        orientations.add(270);
        ANDROID_CAMERA_ORIENTATIONS = Collections.unmodifiableSet(orientations);
    }

    private static final int ANDROID_SENSORS_TYPE_MIN = 1;
    private static final int ANDROID_SENSORS_TYPE_MAX = 65535;
    /** {@code android.sensors.names} / {@code android.sensors.vendors} 值：1..256 个 UTF-16 码元。 */
    private static final int ANDROID_SENSORS_NAME_MAX_LEN = 256;
    /**
     * {@code Sensor.getReportingMode()}：{@code REPORTING_MODE_CONTINUOUS}/{@code ON_CHANGE}/
     * {@code ONE_SHOT}/{@code SPECIAL_TRIGGER} = {@code 0..3}。
     */
    private static final int ANDROID_SENSOR_REPORTING_MODE_MIN = 0;
    private static final int ANDROID_SENSOR_REPORTING_MODE_MAX = 3;
    /**
     * {@code Sensor.getHighestDirectReportRateLevel()}：{@code RATE_STOP}/{@code NORMAL}/
     * {@code FAST}/{@code VERY_FAST} = {@code 0..3}。
     */
    private static final int ANDROID_SENSOR_DIRECT_REPORT_RATE_MIN = 0;
    private static final int ANDROID_SENSOR_DIRECT_REPORT_RATE_MAX = 3;
    /**
     * {@code SensorDirectChannel.TYPE_MEMORY_FILE} / {@code TYPE_HARDWARE_BUFFER} = {@code 1}/{@code 2}。
     * 仅用于 {@code isDirectChannelTypeSupported} 查询；不物化通道对象。
     */
    private static final int ANDROID_SENSOR_DIRECT_CHANNEL_TYPE_MIN = 1;
    private static final int ANDROID_SENSOR_DIRECT_CHANNEL_TYPE_MAX = 2;

    private static final boolean ANDROID_CLIPBOARD_DEFAULT_HAS_PRIMARY_CLIP = false;
    private static final boolean ANDROID_ACCESSIBILITY_DEFAULT_ENABLED = false;
    private static final boolean ANDROID_ACCESSIBILITY_DEFAULT_TOUCH_EXPLORATION_ENABLED = false;
    private static final boolean ANDROID_ACCESSIBILITY_DEFAULT_HIGH_CONTRAST_TEXT_ENABLED = false;
    private static final boolean ANDROID_AUDIO_DEFAULT_MUSIC_ACTIVE = false;
    private static final boolean ANDROID_AUDIO_DEFAULT_SPEAKERPHONE_ON = false;
    /** {@code AudioManager.RINGER_MODE_NORMAL}. */
    private static final int ANDROID_AUDIO_DEFAULT_RINGER_MODE = 2;
    private static final int ANDROID_AUDIO_RINGER_MODE_MIN = 0;
    private static final int ANDROID_AUDIO_RINGER_MODE_MAX = 2;
    /** {@code AudioManager.MODE_NORMAL}. */
    private static final int ANDROID_AUDIO_DEFAULT_MODE = 0;
    private static final int ANDROID_AUDIO_MODE_MIN = 0;
    private static final int ANDROID_AUDIO_MODE_MAX = 7;

    private static final boolean ANDROID_LOCATION_DEFAULT_ENABLED = false;
    private static final boolean ANDROID_LOCATION_PROVIDERS_DEFAULT_GPS = false;
    private static final boolean ANDROID_LOCATION_PROVIDERS_DEFAULT_NETWORK = false;
    private static final boolean ANDROID_LOCATION_PROVIDERS_DEFAULT_PASSIVE = false;
    private static final boolean ANDROID_LOCATION_DEFAULT_REQUIRES_NETWORK = false;
    private static final boolean ANDROID_LOCATION_DEFAULT_REQUIRES_SATELLITE = false;
    private static final boolean ANDROID_LOCATION_DEFAULT_REQUIRES_CELL = false;
    private static final boolean ANDROID_LOCATION_DEFAULT_HAS_MONETARY_COST = false;
    private static final boolean ANDROID_LOCATION_DEFAULT_SUPPORTS_ALTITUDE = false;
    private static final boolean ANDROID_LOCATION_DEFAULT_SUPPORTS_SPEED = false;
    private static final boolean ANDROID_LOCATION_DEFAULT_SUPPORTS_BEARING = false;
    private static final boolean ANDROID_LOCATION_DEFAULT_MEETS_CRITERIA = false;
    /** View default when {@code accuracy} is omitted; JNI still notHandled. */
    private static final int ANDROID_LOCATION_DEFAULT_ACCURACY = 0;
    /** {@code android.location.provider.ProviderProperties.ACCURACY_FINE}. */
    private static final int ANDROID_LOCATION_PROVIDER_ACCURACY_FINE = 1;
    /** {@code android.location.provider.ProviderProperties.ACCURACY_COARSE}. */
    private static final int ANDROID_LOCATION_PROVIDER_ACCURACY_COARSE = 2;
    /** View default when {@code powerRequirement} is omitted; JNI still notHandled. */
    private static final int ANDROID_LOCATION_DEFAULT_POWER_REQUIREMENT = 0;
    /** {@code android.location.provider.ProviderProperties.POWER_USAGE_LOW}. */
    private static final int ANDROID_LOCATION_PROVIDER_POWER_USAGE_LOW = 1;
    /** {@code android.location.provider.ProviderProperties.POWER_USAGE_MEDIUM}. */
    private static final int ANDROID_LOCATION_PROVIDER_POWER_USAGE_MEDIUM = 2;
    /** {@code android.location.provider.ProviderProperties.POWER_USAGE_HIGH}. */
    private static final int ANDROID_LOCATION_PROVIDER_POWER_USAGE_HIGH = 3;

    private static final String ANDROID_IDENTIFIERS_DEFAULT_ADVERTISING_ID =
            "00000000-0000-0000-0000-00000000a001";
    private static final boolean ANDROID_IDENTIFIERS_DEFAULT_LIMIT_AD_TRACKING = false;

    private static final int ANDROID_USER_STATE_DEFAULT_USER_ID = 0;
    private static final long ANDROID_USER_STATE_DEFAULT_SERIAL_NUMBER = 0L;
    private static final boolean ANDROID_USER_STATE_DEFAULT_USER_UNLOCKED = true;
    private static final boolean ANDROID_USER_STATE_DEFAULT_SYSTEM_USER = true;
    private static final boolean ANDROID_USER_STATE_DEFAULT_MANAGED_PROFILE = false;
    private static final boolean ANDROID_USER_STATE_DEFAULT_DEMO_USER = false;
    private static final int ANDROID_USER_STATE_USER_ID_MIN = 0;
    private static final int ANDROID_USER_STATE_USER_ID_MAX = 99999;
    private static final long ANDROID_USER_STATE_SERIAL_NUMBER_MIN = 0L;

    private static final boolean ANDROID_SECURITY_SIGNALS_DEFAULT_DEBUGGER_CONNECTED = false;
    private static final boolean ANDROID_SECURITY_SIGNALS_DEFAULT_WAITING_FOR_DEBUGGER = false;
    private static final boolean ANDROID_SECURITY_SIGNALS_DEFAULT_DEBUGGER_TRACING = false;
    private static final boolean ANDROID_SECURITY_SIGNALS_DEFAULT_SELINUX_ENABLED = true;
    private static final boolean ANDROID_SECURITY_SIGNALS_DEFAULT_SELINUX_ENFORCED = true;
    private static final boolean ANDROID_SECURITY_SIGNALS_DEFAULT_USER_A_MONKEY = false;
    private static final boolean ANDROID_SECURITY_SIGNALS_DEFAULT_USER_TEST_HARNESS = false;

    private static final boolean ANDROID_SECURITY_STATE_DEFAULT_KEYGUARD_LOCKED = false;
    private static final boolean ANDROID_SECURITY_STATE_DEFAULT_KEYGUARD_SECURE = false;
    private static final boolean ANDROID_SECURITY_STATE_DEFAULT_DEVICE_LOCKED = false;
    private static final boolean ANDROID_SECURITY_STATE_DEFAULT_DEVICE_SECURE = false;
    /** BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE */
    private static final int ANDROID_SECURITY_STATE_DEFAULT_BIOMETRIC_CAN_AUTHENTICATE = 12;

    /** BiometricManager.BIOMETRIC_NO_AUTHENTICATION */
    private static final long ANDROID_SECURITY_STATE_DEFAULT_BIOMETRIC_LAST_AUTH_ELAPSED = -1L;

    /**
     * Allowed {@code BiometricManager.canAuthenticate} framework result codes only
     * (0,1,11,12,15,20,21).
     */
    private static final Set<Integer> ANDROID_BIOMETRIC_CAN_AUTHENTICATE_RESULTS;
    static {
        Set<Integer> codes = new HashSet<Integer>();
        codes.add(0);
        codes.add(1);
        codes.add(11);
        codes.add(12);
        codes.add(15);
        codes.add(20);
        codes.add(21);
        ANDROID_BIOMETRIC_CAN_AUTHENTICATE_RESULTS = Collections.unmodifiableSet(codes);
    }

    private static final String ANDROID_LOCALE_DEFAULT_LANGUAGE_TAG = "en-US";
    private static final String ANDROID_LOCALE_DEFAULT_TIMEZONE_ID = "UTC";
    private static final int ANDROID_LOCALE_STRING_MAX = 128;

    private static final int ANDROID_CONFIGURATION_DEFAULT_ORIENTATION = 1;
    private static final int ANDROID_CONFIGURATION_DEFAULT_SCREEN_LAYOUT = 34;
    private static final int ANDROID_CONFIGURATION_DEFAULT_UI_MODE = 17;
    private static final float ANDROID_CONFIGURATION_DEFAULT_FONT_SCALE = 1.0f;
    private static final int ANDROID_CONFIGURATION_DEFAULT_DENSITY_DPI = 0;
    private static final int ANDROID_CONFIGURATION_DEFAULT_SCREEN_WIDTH_DP = 0;
    private static final int ANDROID_CONFIGURATION_DEFAULT_SCREEN_HEIGHT_DP = 0;
    private static final int ANDROID_CONFIGURATION_DEFAULT_SMALLEST_SCREEN_WIDTH_DP = 0;
    private static final int ANDROID_CONFIGURATION_DEFAULT_KEYBOARD = 0;
    private static final int ANDROID_CONFIGURATION_DEFAULT_NAVIGATION = 0;
    private static final int ANDROID_CONFIGURATION_DEFAULT_KEYBOARD_HIDDEN = 0;
    private static final int ANDROID_CONFIGURATION_DEFAULT_HARD_KEYBOARD_HIDDEN = 0;
    private static final int ANDROID_CONFIGURATION_DEFAULT_NAVIGATION_HIDDEN = 0;
    private static final int ANDROID_CONFIGURATION_ORIENTATION_MIN = 0;
    private static final int ANDROID_CONFIGURATION_ORIENTATION_MAX = 3;
    private static final int ANDROID_CONFIGURATION_DENSITY_DPI_MIN = 0;
    private static final int ANDROID_CONFIGURATION_DENSITY_DPI_MAX = 1000;
    private static final int ANDROID_CONFIGURATION_SCREEN_DP_MIN = 0;
    private static final int ANDROID_CONFIGURATION_SCREEN_DP_MAX = 10000;
    /** Configuration.KEYBOARD_UNDEFINED..KEYBOARD_12KEY */
    private static final int ANDROID_CONFIGURATION_KEYBOARD_MIN = 0;
    private static final int ANDROID_CONFIGURATION_KEYBOARD_MAX = 3;
    /** Configuration.NAVIGATION_UNDEFINED..NAVIGATION_WHEEL */
    private static final int ANDROID_CONFIGURATION_NAVIGATION_MIN = 0;
    private static final int ANDROID_CONFIGURATION_NAVIGATION_MAX = 4;
    /** Configuration.KEYBOARDHIDDEN_UNDEFINED/NO/YES */
    private static final int ANDROID_CONFIGURATION_KEYBOARD_HIDDEN_MIN = 0;
    private static final int ANDROID_CONFIGURATION_KEYBOARD_HIDDEN_MAX = 2;
    /** Configuration.HARDKEYBOARDHIDDEN_UNDEFINED/NO/YES */
    private static final int ANDROID_CONFIGURATION_HARD_KEYBOARD_HIDDEN_MIN = 0;
    private static final int ANDROID_CONFIGURATION_HARD_KEYBOARD_HIDDEN_MAX = 2;
    /** Configuration.NAVIGATIONHIDDEN_UNDEFINED/NO/YES */
    private static final int ANDROID_CONFIGURATION_NAVIGATION_HIDDEN_MIN = 0;
    private static final int ANDROID_CONFIGURATION_NAVIGATION_HIDDEN_MAX = 2;
    private static final float ANDROID_CONFIGURATION_FONT_SCALE_MAX = 10f;

    private static final int ANDROID_DISPLAY_DEFAULT_WIDTH_PIXELS = 1080;
    private static final int ANDROID_DISPLAY_DEFAULT_HEIGHT_PIXELS = 2400;
    private static final int ANDROID_DISPLAY_DEFAULT_DENSITY_DPI = 420;
    private static final float ANDROID_DISPLAY_DEFAULT_XDPI = 411.0f;
    private static final float ANDROID_DISPLAY_DEFAULT_YDPI = 411.0f;
    private static final float ANDROID_DISPLAY_DEFAULT_REFRESH_RATE = 60.0f;
    private static final int ANDROID_DISPLAY_DEFAULT_ROTATION = 0;
    private static final int ANDROID_DISPLAY_DEFAULT_MODE_ID = 1;
    private static final int ANDROID_DISPLAY_PIXELS_MIN = 1;
    private static final int ANDROID_DISPLAY_PIXELS_MAX = 32768;
    private static final int ANDROID_DISPLAY_DENSITY_DPI_MIN = 1;
    private static final int ANDROID_DISPLAY_DENSITY_DPI_MAX = 10000;
    private static final float ANDROID_DISPLAY_FLOAT_MAX = 10000f;
    private static final float ANDROID_DISPLAY_REFRESH_RATE_MAX = 1000f;
    private static final int ANDROID_DISPLAY_ROTATION_MIN = 0;
    private static final int ANDROID_DISPLAY_ROTATION_MAX = 3;
    private static final int ANDROID_DISPLAY_UNIQUE_ID_MAX_LEN = 128;
    private static final int ANDROID_DISPLAY_MODE_ID_MIN = 1;

    private static final String ANDROID_DRM_DEFAULT_MARKER = "TRACEAI_DRM_MARKER_V1";
    private static final String ANDROID_DRM_DEFAULT_WIDEVINE_UUID = "edef8ba9-79d6-4ace-a3c8-27dcd51d21ed";
    private static final int ANDROID_DRM_MARKER_MAX = 128;
    private static final int ANDROID_DRM_STRING_MAX = 256;
    private static final int ANDROID_DRM_DEVICE_UNIQUE_ID_HEX_MAX = 2097152;
    private static final int ANDROID_DRM_SESSION_ID_HEX_MAX = 128;

    private static final Set<String> ANDROID_DRM_SECURITY_LEVELS;
    private static final Set<String> ANDROID_DRM_HDCP_LEVELS;
    static {
        Set<String> sec = new HashSet<String>();
        sec.add("L1");
        sec.add("L2");
        sec.add("L3");
        sec.add("UNKNOWN");
        ANDROID_DRM_SECURITY_LEVELS = Collections.unmodifiableSet(sec);
        Set<String> hdcp = new HashSet<String>();
        hdcp.add("HDCP_NONE");
        hdcp.add("HDCP_V1");
        hdcp.add("HDCP_V2");
        hdcp.add("HDCP_V2_1");
        hdcp.add("HDCP_V2_2");
        hdcp.add("HDCP_V2_3");
        hdcp.add("HDCP_NO_DIGITAL_OUTPUT");
        hdcp.add("HDCP_LEVEL_UNKNOWN");
        ANDROID_DRM_HDCP_LEVELS = Collections.unmodifiableSet(hdcp);
    }

    private static final Set<String> ANDROID_TEE_SECURITY_LEVELS;
    static {
        Set<String> levels = new HashSet<String>();
        levels.add("SOFTWARE");
        levels.add("TRUSTED_ENVIRONMENT");
        levels.add("STRONGBOX");
        ANDROID_TEE_SECURITY_LEVELS = Collections.unmodifiableSet(levels);
    }

    /** Max hex character length for {@code android.tee.keyBlobHex} (1 MiB binary). */
    private static final int ANDROID_TEE_KEY_BLOB_HEX_MAX_CHARS = 2097152;

    private TraceEnvironmentConfig(JSONObject root) {
        this.root = root == null ? new JSONObject() : root;
    }

    private void validate() {
        for (String key : RANDOM_HEX_KEYS) {
            String hex = getRandomHex(key);
            if (hex != null) {
                parseHex(key, hex);
            }
        }
        String uuid = getRandomString("uuid");
        if (uuid != null) {
            UUID.fromString(uuid);
        }
        validateProcess();
        validateNetworkInterfaces();
        validateNetworkIpv4Routes();
        validateNetworkInterfaceStats();
        validateNetworkIpv6Addresses();
        validateNetworkArpEntries();
        validateNetworkIgmpMemberships();
        validateNetworkIgmp6Memberships();
        validateNetworkLinkLayerMulticastEntries();
        validateNetworkWirelessProcStats();
        validateNetworkWifi();
        validateNetworkBluetooth();
        validateNetworkLinks();
        validateNetworkTcp(false);
        validateNetworkTcp(true);
        validateNetworkCapabilities();
        validateLinuxProcesses();
        validateLinuxCommands();
        validateLinuxMincore();
        validateAndroidDisplays();
        validateAndroidSettings();
        validateAndroidTelephony();
        validateAndroidPackages();
        validateAndroidFeatures();
        validateAndroidTee();
        validateAndroidDrm();
        validateAndroidLocale();
        validateAndroidDisplay();
        validateAndroidConfiguration();
        validateAndroidPower();
        validateAndroidPowerProfile();
        validateAndroidThermal();
        validateGraphics();
        validateAndroidBattery();
        validateAndroidCameras();
        validateAndroidSensors();
        validateAndroidClipboard();
        validateAndroidAccessibility();
        validateAndroidAudio();
        validateAndroidLocation();
        validateAndroidIdentifiers();
        validateAndroidAccounts();
        validateAndroidInputMethods();
        validateAndroidUserState();
        validateAndroidSecuritySignals();
        validateAndroidSecurityState();
        validateAndroidBuildAbiArrays();
        validateAndroidBuildTime();
        validateAndroidRuntime();
        validateFilesystem();
        validateLinuxProc();
        validateLinuxAuxv();
        validateLinuxCpu();
        validateLinuxRlimits();
        validateLinuxSysinfo();
        validateLinuxEnviron();
    }

    /**
     * Parse-time rules for optional {@code process.pgid} / {@code process.sid} /
     * {@code process.supplementaryGids}. Missing {@code pgid}/{@code sid} leave getters on the
     * caller fallback (typically {@code emulator.getPid()}). Present pgid/sid values must be
     * exact JSON Number integers in {@code 1..Integer.MAX_VALUE}; never inferred from pid/ppid
     * or from each other. {@code supplementaryGids} must be a JSONArray when present (including
     * {@code []}); elements are exact non-negative integers in {@code 0..Integer.MAX_VALUE},
     * unique, and stored in array order. Never inferred from {@code gid}/{@code egid}.
     * The parsed model is immutable.
     */
    private void validateProcess() {
        JSONObject process = section("process");
        if (process == null) {
            this.processPgid = null;
            this.processSid = null;
            this.supplementaryGidsConfigured = false;
            this.supplementaryGids = Collections.emptyList();
            return;
        }
        if (process.containsKey("pgid")) {
            this.processPgid = Integer.valueOf(requireExactJsonNumberIntField(
                    process, "pgid", "process.pgid", 1, Integer.MAX_VALUE));
        } else {
            this.processPgid = null;
        }
        if (process.containsKey("sid")) {
            this.processSid = Integer.valueOf(requireExactJsonNumberIntField(
                    process, "sid", "process.sid", 1, Integer.MAX_VALUE));
        } else {
            this.processSid = null;
        }
        if (process.containsKey("supplementaryGids")) {
            Object raw = process.get("supplementaryGids");
            if (!(raw instanceof JSONArray)) {
                throw new IllegalArgumentException(
                        "process.supplementaryGids must be a JSONArray");
            }
            JSONArray array = (JSONArray) raw;
            List<Integer> built = new ArrayList<Integer>(array.size());
            Set<Integer> seen = new HashSet<Integer>();
            for (int i = 0; i < array.size(); i++) {
                String itemPath = "process.supplementaryGids[" + i + "]";
                int gidValue = requireExactJsonNumberIntValue(array.get(i), itemPath,
                        0, Integer.MAX_VALUE);
                Integer boxed = Integer.valueOf(gidValue);
                if (seen.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " duplicates gid " + gidValue);
                }
                seen.add(boxed);
                built.add(boxed);
            }
            this.supplementaryGidsConfigured = true;
            this.supplementaryGids = Collections.unmodifiableList(built);
        } else {
            this.supplementaryGidsConfigured = false;
            this.supplementaryGids = Collections.emptyList();
        }
    }

    /**
     * Parse-time rules for optional {@code linux.environ} when present.
     * Missing key leaves {@link #isLinuxEnvironConfigured()} false so loaders keep built-in defaults.
     * Present key (including {@code []}) replaces the default environ list entirely.
     * Never retains JSONArray.
     */
    private void validateLinuxEnviron() {
        JSONObject linux = section("linux");
        if (linux == null || !linux.containsKey("environ")) {
            this.linuxEnvironConfigured = false;
            this.linuxEnviron = Collections.emptyList();
            return;
        }
        Object raw = linux.get("environ");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("linux.environ must be a JSONArray");
        }
        JSONArray array = (JSONArray) raw;
        List<String> built = new ArrayList<String>(array.size());
        Set<String> seenKeys = new HashSet<String>();
        for (int i = 0; i < array.size(); i++) {
            String itemPath = "linux.environ[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof String)) {
                throw new IllegalArgumentException(itemPath
                        + " must be a String KEY=VALUE (no NUL/CR/LF, max "
                        + LINUX_ENVIRON_ENTRY_MAX_CHARS + ")");
            }
            String entry = (String) item;
            if (entry.length() > LINUX_ENVIRON_ENTRY_MAX_CHARS) {
                throw new IllegalArgumentException(itemPath
                        + " must be a String KEY=VALUE (no NUL/CR/LF, max "
                        + LINUX_ENVIRON_ENTRY_MAX_CHARS + ")");
            }
            if (entry.indexOf('\0') >= 0 || entry.indexOf('\r') >= 0 || entry.indexOf('\n') >= 0) {
                throw new IllegalArgumentException(itemPath
                        + " must be a String KEY=VALUE (no NUL/CR/LF, max "
                        + LINUX_ENVIRON_ENTRY_MAX_CHARS + ")");
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException(itemPath
                        + " must be a String KEY=VALUE with KEY matching [A-Za-z_][A-Za-z0-9_]*");
            }
            String key = entry.substring(0, eq);
            if (!LINUX_ENVIRON_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException(itemPath
                        + " must be a String KEY=VALUE with KEY matching [A-Za-z_][A-Za-z0-9_]*");
            }
            if (!seenKeys.add(key)) {
                throw new IllegalArgumentException(itemPath
                        + " duplicates KEY \"" + key + "\"");
            }
            built.add(entry);
        }
        this.linuxEnvironConfigured = true;
        this.linuxEnviron = Collections.unmodifiableList(built);
    }

    /**
     * Parse-time rules for optional {@code linux.cpu} when present (affinity + optional sysfs
     * CPU-list subset). Missing node leaves {@link #isLinuxCpuConfigured()} false; explicit empty
     * object is configured with default {@code affinityMaskHex=01} and no CPU-list keys.
     * Materializes {@link LinuxCpuConfig}; never retains JSONObject.
     */
    private void validateLinuxCpu() {
        JSONObject linux = section("linux");
        if (linux == null || !linux.containsKey("cpu")) {
            this.linuxCpuConfigured = false;
            this.linuxCpuConfig = null;
            return;
        }
        Object raw = linux.get("cpu");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("linux.cpu must be a JSONObject");
        }
        JSONObject cpu = (JSONObject) raw;
        for (String key : cpu.keySet()) {
            if (!isAllowedLinuxCpuKey(key)) {
                throw new IllegalArgumentException("linux.cpu." + key
                        + " is not an allowed key (affinityMaskHex|online|offline|present|possible|"
                        + "configuredProcessorCount|onlineProcessorCount)");
            }
        }

        boolean affinityMaskConfigured = cpu.containsKey("affinityMaskHex");
        byte[] affinityMask;
        if (affinityMaskConfigured) {
            affinityMask = requireLinuxCpuAffinityMaskHex(cpu.get("affinityMaskHex"),
                    "linux.cpu.affinityMaskHex");
        } else {
            affinityMask = requireLinuxCpuAffinityMaskHex(LINUX_CPU_DEFAULT_AFFINITY_MASK_HEX,
                    "linux.cpu.affinityMaskHex");
        }

        boolean onlineConfigured = cpu.containsKey("online");
        String online = null;
        if (onlineConfigured) {
            online = requireCanonicalLinuxCpuList(cpu.get("online"), "linux.cpu.online");
        }
        boolean offlineConfigured = cpu.containsKey("offline");
        String offline = null;
        if (offlineConfigured) {
            offline = requireCanonicalLinuxCpuList(cpu.get("offline"), "linux.cpu.offline");
        }
        boolean presentConfigured = cpu.containsKey("present");
        String present = null;
        if (presentConfigured) {
            present = requireCanonicalLinuxCpuList(cpu.get("present"), "linux.cpu.present");
        }
        boolean possibleConfigured = cpu.containsKey("possible");
        String possible = null;
        if (possibleConfigured) {
            possible = requireCanonicalLinuxCpuList(cpu.get("possible"), "linux.cpu.possible");
        }

        boolean configuredProcessorCountConfigured = cpu.containsKey("configuredProcessorCount");
        int configuredProcessorCount = 0;
        if (configuredProcessorCountConfigured) {
            configuredProcessorCount = requireExactJsonNumberIntField(cpu, "configuredProcessorCount",
                    "linux.cpu.configuredProcessorCount",
                    LINUX_CPU_PROCESSOR_COUNT_MIN, LINUX_CPU_PROCESSOR_COUNT_MAX);
        }
        boolean onlineProcessorCountConfigured = cpu.containsKey("onlineProcessorCount");
        int onlineProcessorCount = 0;
        if (onlineProcessorCountConfigured) {
            onlineProcessorCount = requireExactJsonNumberIntField(cpu, "onlineProcessorCount",
                    "linux.cpu.onlineProcessorCount",
                    LINUX_CPU_PROCESSOR_COUNT_MIN, LINUX_CPU_PROCESSOR_COUNT_MAX);
        }

        this.linuxCpuConfigured = true;
        this.linuxCpuConfig = new LinuxCpuConfig(
                affinityMask, affinityMaskConfigured,
                online, onlineConfigured,
                offline, offlineConfigured,
                present, presentConfigured,
                possible, possibleConfigured,
                configuredProcessorCount, configuredProcessorCountConfigured,
                onlineProcessorCount, onlineProcessorCountConfigured);
    }

    /**
     * Parse-time rules for optional {@code linux.rlimits} when present.
     * Missing node leaves {@link #isLinuxRlimitsConfigured()} false and
     * {@link #isLinuxRlimitsNofileConfigured()} false (no default {@code nofile}).
     * Explicit empty object is configured with {@code nofile} still omitted.
     * Materializes {@link LinuxRlimitsConfig}; never retains JSONObject.
     */
    private void validateLinuxRlimits() {
        JSONObject linux = section("linux");
        if (linux == null || !linux.containsKey("rlimits")) {
            this.linuxRlimitsConfigured = false;
            this.linuxRlimitsConfig = null;
            return;
        }
        Object raw = linux.get("rlimits");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("linux.rlimits must be a JSONObject");
        }
        JSONObject rlimits = (JSONObject) raw;
        for (String key : rlimits.keySet()) {
            if (!isAllowedLinuxRlimitsKey(key)) {
                throw new IllegalArgumentException("linux.rlimits." + key
                        + " is not an allowed key (nofile)");
            }
        }

        boolean nofileConfigured = rlimits.containsKey("nofile");
        long nofileSoft = 0L;
        long nofileHard = 0L;
        if (nofileConfigured) {
            Object nofileRaw = rlimits.get("nofile");
            if (!(nofileRaw instanceof JSONObject)) {
                throw new IllegalArgumentException("linux.rlimits.nofile must be a JSONObject");
            }
            JSONObject nofile = (JSONObject) nofileRaw;
            for (String key : nofile.keySet()) {
                if (!isAllowedLinuxRlimitsNofileKey(key)) {
                    throw new IllegalArgumentException("linux.rlimits.nofile." + key
                            + " is not an allowed key (soft|hard)");
                }
            }
            nofileSoft = requireExactJsonNumberLongField(nofile, "soft",
                    "linux.rlimits.nofile.soft", 0L, Long.MAX_VALUE);
            nofileHard = requireExactJsonNumberLongField(nofile, "hard",
                    "linux.rlimits.nofile.hard", 0L, Long.MAX_VALUE);
            if (nofileSoft > nofileHard) {
                throw new IllegalArgumentException("linux.rlimits.nofile.soft must be <= hard ("
                        + nofileSoft + " > " + nofileHard + ")");
            }
        }

        this.linuxRlimitsConfigured = true;
        this.linuxRlimitsConfig = new LinuxRlimitsConfig(nofileConfigured, nofileSoft, nofileHard);
    }

    /**
     * Parse-time rules for optional {@code linux.sysinfo}. Missing node leaves
     * {@link #isLinuxSysinfoConfigured()} false so ARM32/ARM64 {@code sysinfo} keep the
     * historical all-zero struct (no sidecar). Present object requires the full whitelist
     * (uptime, loads[3], RAM, swap, procs, memUnit) in ARM32 C field ranges
     * (no silent long-to-int truncation). Never retains JSONObject/JSONArray.
     */
    private void validateLinuxSysinfo() {
        JSONObject linux = section("linux");
        if (linux == null || !linux.containsKey("sysinfo")) {
            this.linuxSysinfoConfigured = false;
            this.linuxSysinfoConfig = null;
            return;
        }
        Object raw = linux.get("sysinfo");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("linux.sysinfo must be a JSONObject");
        }
        JSONObject sysinfo = (JSONObject) raw;
        for (String key : sysinfo.keySet()) {
            if (!isAllowedLinuxSysinfoKey(key)) {
                throw new IllegalArgumentException("linux.sysinfo." + key
                        + " is not an allowed key (uptime|loads|totalRam|freeRam|sharedRam|"
                        + "bufferRam|totalSwap|freeSwap|procs|memUnit)");
            }
        }
        long uptime = requireExactJsonNumberLongField(sysinfo, "uptime",
                "linux.sysinfo.uptime", 0L, LINUX_SYSINFO_UPTIME_MAX);
        Object loadsRaw = sysinfo.get("loads");
        if (!(loadsRaw instanceof JSONArray)) {
            throw new IllegalArgumentException("linux.sysinfo.loads must be a JSONArray of 3 integers");
        }
        JSONArray loadsArray = (JSONArray) loadsRaw;
        if (loadsArray.size() != 3) {
            throw new IllegalArgumentException("linux.sysinfo.loads must contain exactly 3 integers");
        }
        long[] loads = new long[3];
        for (int i = 0; i < 3; i++) {
            loads[i] = requireExactJsonNumberLongValue(loadsArray.get(i),
                    "linux.sysinfo.loads[" + i + "]", 0L, LINUX_SYSINFO_U32_MAX);
        }
        long totalRam = requireExactJsonNumberLongField(sysinfo, "totalRam",
                "linux.sysinfo.totalRam", 0L, LINUX_SYSINFO_U32_MAX);
        long freeRam = requireExactJsonNumberLongField(sysinfo, "freeRam",
                "linux.sysinfo.freeRam", 0L, LINUX_SYSINFO_U32_MAX);
        long sharedRam = requireExactJsonNumberLongField(sysinfo, "sharedRam",
                "linux.sysinfo.sharedRam", 0L, LINUX_SYSINFO_U32_MAX);
        long bufferRam = requireExactJsonNumberLongField(sysinfo, "bufferRam",
                "linux.sysinfo.bufferRam", 0L, LINUX_SYSINFO_U32_MAX);
        long totalSwap = requireExactJsonNumberLongField(sysinfo, "totalSwap",
                "linux.sysinfo.totalSwap", 0L, LINUX_SYSINFO_U32_MAX);
        long freeSwap = requireExactJsonNumberLongField(sysinfo, "freeSwap",
                "linux.sysinfo.freeSwap", 0L, LINUX_SYSINFO_U32_MAX);
        int procs = requireExactJsonNumberIntField(sysinfo, "procs",
                "linux.sysinfo.procs", 0, LINUX_SYSINFO_PROCS_MAX);
        long memUnit = requireExactJsonNumberLongField(sysinfo, "memUnit",
                "linux.sysinfo.memUnit", LINUX_SYSINFO_MEM_UNIT_MIN, LINUX_SYSINFO_U32_MAX);
        this.linuxSysinfoConfigured = true;
        this.linuxSysinfoConfig = new LinuxSysinfoConfig(uptime, loads, totalRam, freeRam,
                sharedRam, bufferRam, totalSwap, freeSwap, procs, memUnit);
    }

    /**
     * Canonical Linux CPU-list grammar (sysfs style): nonempty comma-separated decimal IDs or
     * ascending inclusive ranges ({@code N} or {@code N-M} with {@code N < M}); no whitespace,
     * leading zeros (except literal {@code 0}), duplicates, descending ranges, or unmerged
     * overlap/adjacency. Preserves the input string as stored text when valid.
     */
    private static String requireCanonicalLinuxCpuList(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a nonempty canonical Linux CPU-list String");
        }
        String list = (String) raw;
        if (list.isEmpty()) {
            throw new IllegalArgumentException(path
                    + " must be a nonempty canonical Linux CPU-list String");
        }
        for (int i = 0; i < list.length(); i++) {
            char c = list.charAt(i);
            if (c <= ' ' || c == '\t' || c == '\n' || c == '\r') {
                throw new IllegalArgumentException(path
                        + " must not contain whitespace");
            }
        }
        validateCanonicalLinuxCpuList(list, path);
        return list;
    }

    private static void validateCanonicalLinuxCpuList(String list, String path) {
        String[] tokens = list.split(",", -1);
        if (tokens.length == 0) {
            throw new IllegalArgumentException(path
                    + " must be a nonempty canonical Linux CPU-list String");
        }
        long prevEnd = -2L;
        for (int t = 0; t < tokens.length; t++) {
            String token = tokens[t];
            if (token.isEmpty()) {
                throw new IllegalArgumentException(path
                        + " must not contain empty tokens or trailing/leading commas");
            }
            long start;
            long end;
            int dash = token.indexOf('-');
            if (dash < 0) {
                start = end = parseLinuxCpuId(token, path);
            } else {
                if (dash == 0 || dash != token.lastIndexOf('-') || dash == token.length() - 1) {
                    throw new IllegalArgumentException(path
                            + " range token must be N-M with decimal N and M");
                }
                start = parseLinuxCpuId(token.substring(0, dash), path);
                end = parseLinuxCpuId(token.substring(dash + 1), path);
                if (start >= end) {
                    throw new IllegalArgumentException(path
                            + " ranges must be ascending with N < M (use a single ID for one CPU)");
                }
            }
            if (start <= prevEnd) {
                throw new IllegalArgumentException(path
                        + " must be strictly ascending without overlap or duplicate IDs");
            }
            if (prevEnd >= 0L && start == prevEnd + 1L) {
                throw new IllegalArgumentException(path
                        + " adjacent ranges/IDs must be canonically merged");
            }
            prevEnd = end;
        }
    }

    /** Decimal CPU id: digits only; no leading zeros except literal {@code 0}; {@code 0..Integer.MAX_VALUE}. */
    private static long parseLinuxCpuId(String token, String path) {
        if (token.isEmpty()) {
            throw new IllegalArgumentException(path + " CPU id must be a decimal integer");
        }
        if (token.charAt(0) == '0' && token.length() > 1) {
            throw new IllegalArgumentException(path
                    + " CPU id must not have leading zeros (except literal 0)");
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException(path
                        + " CPU id must be a decimal integer without signs or prefixes");
            }
        }
        try {
            long value = Long.parseLong(token);
            if (value < 0L || value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(path
                        + " CPU id must be in range 0.." + Integer.MAX_VALUE);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(path
                    + " CPU id must be a decimal integer in range 0.." + Integer.MAX_VALUE);
        }
    }

    private static boolean isAllowedLinuxCpuKey(String key) {
        for (String allowed : LINUX_CPU_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedLinuxRlimitsKey(String key) {
        for (String allowed : LINUX_RLIMITS_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedLinuxSysinfoKey(String key) {
        for (String allowed : LINUX_SYSINFO_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedLinuxRlimitsNofileKey(String key) {
        for (String allowed : LINUX_RLIMITS_NOFILE_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Non-empty even-length pure hex String; decoded length {@code 1..1024} bytes.
     * Case-insensitive input; stored as decoded bytes (not the original String).
     */
    private static byte[] requireLinuxCpuAffinityMaskHex(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty even-length hex String");
        }
        String hex = (String) raw;
        if (hex.isEmpty() || (hex.length() & 1) != 0) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty even-length hex String");
        }
        int maxHexChars = LINUX_CPU_AFFINITY_MASK_BYTES_MAX * 2;
        if (hex.length() > maxHexChars) {
            throw new IllegalArgumentException(path + " decoded length must be <= "
                    + LINUX_CPU_AFFINITY_MASK_BYTES_MAX + " bytes");
        }
        for (int i = 0; i < hex.length(); i++) {
            if (Character.digit(hex.charAt(i), 16) < 0) {
                throw new IllegalArgumentException(path
                        + " must contain only hexadecimal characters");
            }
        }
        String lower = hex.toLowerCase(Locale.ROOT);
        int n = lower.length() / 2;
        if (n < LINUX_CPU_AFFINITY_MASK_BYTES_MIN || n > LINUX_CPU_AFFINITY_MASK_BYTES_MAX) {
            throw new IllegalArgumentException(path + " decoded length must be in range "
                    + LINUX_CPU_AFFINITY_MASK_BYTES_MIN + ".." + LINUX_CPU_AFFINITY_MASK_BYTES_MAX
                    + " bytes");
        }
        byte[] data = new byte[n];
        for (int i = 0; i < n; i++) {
            int high = Character.digit(lower.charAt(i * 2), 16);
            int low = Character.digit(lower.charAt(i * 2 + 1), 16);
            data[i] = (byte) ((high << 4) | low);
        }
        return data;
    }

    /**
     * Parse-time rules for optional {@code linux.auxv} when present.
     * Missing node leaves {@link #isLinuxAuxvConfigured()} false; explicit empty object is configured
     * with deterministic defaults. Materializes {@link LinuxAuxvConfig}; never retains JSONObject.
     */
    private void validateLinuxAuxv() {
        JSONObject linux = section("linux");
        if (linux == null || !linux.containsKey("auxv")) {
            this.linuxAuxvConfigured = false;
            this.linuxAuxvConfig = null;
            return;
        }
        Object raw = linux.get("auxv");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("linux.auxv must be a JSONObject");
        }
        JSONObject auxv = (JSONObject) raw;
        for (String key : auxv.keySet()) {
            if (!isAllowedLinuxAuxvKey(key)) {
                throw new IllegalArgumentException("linux.auxv." + key
                        + " is not an allowed key (hwcap32|hwcap2_32|hwcap64|hwcap2_64|"
                        + "platform32|platform64|execFn)");
            }
        }

        boolean hwcap32Configured = auxv.containsKey("hwcap32");
        long hwcap32 = 0L;
        if (hwcap32Configured) {
            hwcap32 = requireExactJsonNumberLongField(auxv, "hwcap32",
                    "linux.auxv.hwcap32", 0L, LINUX_AUXV_HWCAP32_MAX);
        }

        boolean hwcap2_32Configured = auxv.containsKey("hwcap2_32");
        long hwcap2_32 = 0L;
        if (hwcap2_32Configured) {
            hwcap2_32 = requireExactJsonNumberLongField(auxv, "hwcap2_32",
                    "linux.auxv.hwcap2_32", 0L, LINUX_AUXV_HWCAP32_MAX);
        }

        boolean hwcap64Configured = auxv.containsKey("hwcap64");
        long hwcap64 = 0L;
        if (hwcap64Configured) {
            hwcap64 = requireExactJsonNumberLongField(auxv, "hwcap64",
                    "linux.auxv.hwcap64", 0L, Long.MAX_VALUE);
        }

        boolean hwcap2_64Configured = auxv.containsKey("hwcap2_64");
        long hwcap2_64 = 0L;
        if (hwcap2_64Configured) {
            hwcap2_64 = requireExactJsonNumberLongField(auxv, "hwcap2_64",
                    "linux.auxv.hwcap2_64", 0L, Long.MAX_VALUE);
        }

        boolean platform32Configured = auxv.containsKey("platform32");
        String platform32 = LINUX_AUXV_DEFAULT_PLATFORM32;
        if (platform32Configured) {
            platform32 = requireLinuxAuxvPlatformString(auxv.get("platform32"),
                    "linux.auxv.platform32");
        }

        boolean platform64Configured = auxv.containsKey("platform64");
        String platform64 = LINUX_AUXV_DEFAULT_PLATFORM64;
        if (platform64Configured) {
            platform64 = requireLinuxAuxvPlatformString(auxv.get("platform64"),
                    "linux.auxv.platform64");
        }

        boolean execFnConfigured = auxv.containsKey("execFn");
        String execFn = null;
        if (execFnConfigured) {
            Object execRaw = auxv.get("execFn");
            if (execRaw == null) {
                execFn = null;
            } else {
                execFn = requireLinuxAuxvExecFnString(execRaw, "linux.auxv.execFn");
            }
        }

        this.linuxAuxvConfigured = true;
        this.linuxAuxvConfig = new LinuxAuxvConfig(
                hwcap32, hwcap32Configured,
                hwcap2_32, hwcap2_32Configured,
                hwcap64, hwcap64Configured,
                hwcap2_64, hwcap2_64Configured,
                platform32, platform32Configured,
                platform64, platform64Configured,
                execFn, execFnConfigured);
    }

    private static boolean isAllowedLinuxAuxvKey(String key) {
        for (String allowed : LINUX_AUXV_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Non-empty String without NUL/CR/LF; UTF-8 length {@code <= 64} bytes.
     */
    private static String requireLinuxAuxvPlatformString(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without NUL/CR/LF");
        }
        String value = (String) raw;
        if (value.isEmpty()) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without NUL/CR/LF");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\0' || c == '\r' || c == '\n') {
                throw new IllegalArgumentException(path
                        + " must be a non-empty String without NUL/CR/LF");
            }
        }
        int utf8Bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (utf8Bytes > LINUX_AUXV_PLATFORM_UTF8_MAX) {
            throw new IllegalArgumentException(path + " UTF-8 length must be <= "
                    + LINUX_AUXV_PLATFORM_UTF8_MAX + " bytes, got " + utf8Bytes);
        }
        return value;
    }

    /**
     * Non-null String without NUL/CR/LF; UTF-8 length {@code <= 4096} bytes. Empty rejected.
     */
    private static String requireLinuxAuxvExecFnString(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a String or null without NUL/CR/LF");
        }
        String value = (String) raw;
        if (value.isEmpty()) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without NUL/CR/LF when not null");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\0' || c == '\r' || c == '\n') {
                throw new IllegalArgumentException(path
                        + " must be a non-empty String without NUL/CR/LF when not null");
            }
        }
        int utf8Bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (utf8Bytes > LINUX_AUXV_EXECFN_UTF8_MAX) {
            throw new IllegalArgumentException(path + " UTF-8 length must be <= "
                    + LINUX_AUXV_EXECFN_UTF8_MAX + " bytes, got " + utf8Bytes);
        }
        return value;
    }

    /**
     * Parse-time rules for optional {@code android.configuration} when present.
     * Missing node leaves {@link #isAndroidConfigurationConfigured()} false; explicit empty object is
     * configured with deterministic defaults. Materializes {@link AndroidConfigurationConfig};
     * never retains JSONObject.
     */
    private void validateAndroidConfiguration() {
        JSONObject android = android();
        if (android == null || !android.containsKey("configuration")) {
            this.androidConfigurationConfigured = false;
            this.androidConfigurationConfig = null;
            return;
        }
        Object raw = android.get("configuration");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.configuration must be a JSONObject");
        }
        JSONObject configuration = (JSONObject) raw;
        for (String key : configuration.keySet()) {
            if (!isAllowedAndroidConfigurationKey(key)) {
                throw new IllegalArgumentException("android.configuration." + key
                        + " is not an allowed key (orientation|screenLayout|uiMode|fontScale|"
                        + "densityDpi|screenWidthDp|screenHeightDp|smallestScreenWidthDp|"
                        + "keyboard|navigation|keyboardHidden|hardKeyboardHidden|"
                        + "navigationHidden)");
            }
        }

        boolean orientationConfigured = configuration.containsKey("orientation");
        int orientation = ANDROID_CONFIGURATION_DEFAULT_ORIENTATION;
        if (orientationConfigured) {
            orientation = requireExactJsonNumberIntField(configuration, "orientation",
                    "android.configuration.orientation",
                    ANDROID_CONFIGURATION_ORIENTATION_MIN, ANDROID_CONFIGURATION_ORIENTATION_MAX);
        }

        boolean screenLayoutConfigured = configuration.containsKey("screenLayout");
        int screenLayout = ANDROID_CONFIGURATION_DEFAULT_SCREEN_LAYOUT;
        if (screenLayoutConfigured) {
            screenLayout = requireExactJsonNumberIntField(configuration, "screenLayout",
                    "android.configuration.screenLayout",
                    0, Integer.MAX_VALUE);
        }

        boolean uiModeConfigured = configuration.containsKey("uiMode");
        int uiMode = ANDROID_CONFIGURATION_DEFAULT_UI_MODE;
        if (uiModeConfigured) {
            uiMode = requireExactJsonNumberIntField(configuration, "uiMode",
                    "android.configuration.uiMode",
                    0, Integer.MAX_VALUE);
        }

        boolean fontScaleConfigured = configuration.containsKey("fontScale");
        float fontScale = ANDROID_CONFIGURATION_DEFAULT_FONT_SCALE;
        if (fontScaleConfigured) {
            fontScale = requireAndroidConfigurationFontScale(configuration.get("fontScale"),
                    "android.configuration.fontScale");
        }

        boolean densityDpiConfigured = configuration.containsKey("densityDpi");
        int densityDpi = ANDROID_CONFIGURATION_DEFAULT_DENSITY_DPI;
        if (densityDpiConfigured) {
            densityDpi = requireExactJsonNumberIntField(configuration, "densityDpi",
                    "android.configuration.densityDpi",
                    ANDROID_CONFIGURATION_DENSITY_DPI_MIN, ANDROID_CONFIGURATION_DENSITY_DPI_MAX);
        }

        boolean screenWidthDpConfigured = configuration.containsKey("screenWidthDp");
        int screenWidthDp = ANDROID_CONFIGURATION_DEFAULT_SCREEN_WIDTH_DP;
        if (screenWidthDpConfigured) {
            screenWidthDp = requireExactJsonNumberIntField(configuration, "screenWidthDp",
                    "android.configuration.screenWidthDp",
                    ANDROID_CONFIGURATION_SCREEN_DP_MIN, ANDROID_CONFIGURATION_SCREEN_DP_MAX);
        }

        boolean screenHeightDpConfigured = configuration.containsKey("screenHeightDp");
        int screenHeightDp = ANDROID_CONFIGURATION_DEFAULT_SCREEN_HEIGHT_DP;
        if (screenHeightDpConfigured) {
            screenHeightDp = requireExactJsonNumberIntField(configuration, "screenHeightDp",
                    "android.configuration.screenHeightDp",
                    ANDROID_CONFIGURATION_SCREEN_DP_MIN, ANDROID_CONFIGURATION_SCREEN_DP_MAX);
        }

        boolean smallestScreenWidthDpConfigured =
                configuration.containsKey("smallestScreenWidthDp");
        int smallestScreenWidthDp = ANDROID_CONFIGURATION_DEFAULT_SMALLEST_SCREEN_WIDTH_DP;
        if (smallestScreenWidthDpConfigured) {
            smallestScreenWidthDp = requireExactJsonNumberIntField(configuration,
                    "smallestScreenWidthDp",
                    "android.configuration.smallestScreenWidthDp",
                    ANDROID_CONFIGURATION_SCREEN_DP_MIN, ANDROID_CONFIGURATION_SCREEN_DP_MAX);
        }

        boolean keyboardConfigured = configuration.containsKey("keyboard");
        int keyboard = ANDROID_CONFIGURATION_DEFAULT_KEYBOARD;
        if (keyboardConfigured) {
            keyboard = requireExactJsonNumberIntField(configuration, "keyboard",
                    "android.configuration.keyboard",
                    ANDROID_CONFIGURATION_KEYBOARD_MIN, ANDROID_CONFIGURATION_KEYBOARD_MAX);
        }

        boolean navigationConfigured = configuration.containsKey("navigation");
        int navigation = ANDROID_CONFIGURATION_DEFAULT_NAVIGATION;
        if (navigationConfigured) {
            navigation = requireExactJsonNumberIntField(configuration, "navigation",
                    "android.configuration.navigation",
                    ANDROID_CONFIGURATION_NAVIGATION_MIN, ANDROID_CONFIGURATION_NAVIGATION_MAX);
        }

        boolean keyboardHiddenConfigured = configuration.containsKey("keyboardHidden");
        int keyboardHidden = ANDROID_CONFIGURATION_DEFAULT_KEYBOARD_HIDDEN;
        if (keyboardHiddenConfigured) {
            keyboardHidden = requireExactJsonNumberIntField(configuration, "keyboardHidden",
                    "android.configuration.keyboardHidden",
                    ANDROID_CONFIGURATION_KEYBOARD_HIDDEN_MIN,
                    ANDROID_CONFIGURATION_KEYBOARD_HIDDEN_MAX);
        }

        boolean hardKeyboardHiddenConfigured = configuration.containsKey("hardKeyboardHidden");
        int hardKeyboardHidden = ANDROID_CONFIGURATION_DEFAULT_HARD_KEYBOARD_HIDDEN;
        if (hardKeyboardHiddenConfigured) {
            hardKeyboardHidden = requireExactJsonNumberIntField(configuration,
                    "hardKeyboardHidden",
                    "android.configuration.hardKeyboardHidden",
                    ANDROID_CONFIGURATION_HARD_KEYBOARD_HIDDEN_MIN,
                    ANDROID_CONFIGURATION_HARD_KEYBOARD_HIDDEN_MAX);
        }

        boolean navigationHiddenConfigured = configuration.containsKey("navigationHidden");
        int navigationHidden = ANDROID_CONFIGURATION_DEFAULT_NAVIGATION_HIDDEN;
        if (navigationHiddenConfigured) {
            navigationHidden = requireExactJsonNumberIntField(configuration, "navigationHidden",
                    "android.configuration.navigationHidden",
                    ANDROID_CONFIGURATION_NAVIGATION_HIDDEN_MIN,
                    ANDROID_CONFIGURATION_NAVIGATION_HIDDEN_MAX);
        }

        this.androidConfigurationConfigured = true;
        this.androidConfigurationConfig = new AndroidConfigurationConfig(
                orientation, orientationConfigured,
                screenLayout, screenLayoutConfigured,
                uiMode, uiModeConfigured,
                fontScale, fontScaleConfigured,
                densityDpi, densityDpiConfigured,
                screenWidthDp, screenWidthDpConfigured,
                screenHeightDp, screenHeightDpConfigured,
                smallestScreenWidthDp, smallestScreenWidthDpConfigured,
                keyboard, keyboardConfigured,
                navigation, navigationConfigured,
                keyboardHidden, keyboardHiddenConfigured,
                hardKeyboardHidden, hardKeyboardHiddenConfigured,
                navigationHidden, navigationHiddenConfigured);
    }

    private static boolean isAllowedAndroidConfigurationKey(String key) {
        for (String allowed : ANDROID_CONFIGURATION_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse-time rules for optional {@code android.power} when present (v1 subset).
     * Missing node leaves {@link #isAndroidPowerConfigured()} false; explicit empty object is
     * configured with defaults {@code interactive=true}, {@code powerSaveMode=false},
     * {@code deviceIdleMode=false}, {@code deviceLightIdleMode=false},
     * {@code lowPowerStandbyEnabled=false}, {@code sustainedPerformanceModeSupported=false},
     * {@code rebootingUserspaceSupported=false}, {@code ignoringBatteryOptimizations=false}.
     * Materializes {@link AndroidPowerConfig}; never retains JSONObject.
     */
    private void validateAndroidPower() {
        JSONObject android = android();
        if (android == null || !android.containsKey("power")) {
            this.androidPowerConfigured = false;
            this.androidPowerConfig = null;
            return;
        }
        Object raw = android.get("power");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.power must be a JSONObject");
        }
        JSONObject power = (JSONObject) raw;
        for (String key : power.keySet()) {
            if (!isAllowedAndroidPowerKey(key)) {
                throw new IllegalArgumentException("android.power." + key
                        + " is not an allowed key (interactive|powerSaveMode|deviceIdleMode|"
                        + "deviceLightIdleMode|lowPowerStandbyEnabled|sustainedPerformanceModeSupported|"
                        + "rebootingUserspaceSupported|ignoringBatteryOptimizations)");
            }
        }

        boolean interactiveConfigured = power.containsKey("interactive");
        boolean interactive = ANDROID_POWER_DEFAULT_INTERACTIVE;
        if (interactiveConfigured) {
            interactive = requireAndroidPowerBoolean(power.get("interactive"),
                    "android.power.interactive");
        }

        boolean powerSaveModeConfigured = power.containsKey("powerSaveMode");
        boolean powerSaveMode = ANDROID_POWER_DEFAULT_POWER_SAVE_MODE;
        if (powerSaveModeConfigured) {
            powerSaveMode = requireAndroidPowerBoolean(power.get("powerSaveMode"),
                    "android.power.powerSaveMode");
        }

        boolean deviceIdleModeConfigured = power.containsKey("deviceIdleMode");
        boolean deviceIdleMode = ANDROID_POWER_DEFAULT_DEVICE_IDLE_MODE;
        if (deviceIdleModeConfigured) {
            deviceIdleMode = requireAndroidPowerBoolean(power.get("deviceIdleMode"),
                    "android.power.deviceIdleMode");
        }

        boolean deviceLightIdleModeConfigured = power.containsKey("deviceLightIdleMode");
        boolean deviceLightIdleMode = ANDROID_POWER_DEFAULT_DEVICE_LIGHT_IDLE_MODE;
        if (deviceLightIdleModeConfigured) {
            deviceLightIdleMode = requireAndroidPowerBoolean(power.get("deviceLightIdleMode"),
                    "android.power.deviceLightIdleMode");
        }

        boolean lowPowerStandbyEnabledConfigured = power.containsKey("lowPowerStandbyEnabled");
        boolean lowPowerStandbyEnabled = ANDROID_POWER_DEFAULT_LOW_POWER_STANDBY_ENABLED;
        if (lowPowerStandbyEnabledConfigured) {
            lowPowerStandbyEnabled = requireAndroidPowerBoolean(power.get("lowPowerStandbyEnabled"),
                    "android.power.lowPowerStandbyEnabled");
        }

        boolean sustainedPerformanceModeSupportedConfigured =
                power.containsKey("sustainedPerformanceModeSupported");
        boolean sustainedPerformanceModeSupported =
                ANDROID_POWER_DEFAULT_SUSTAINED_PERFORMANCE_MODE_SUPPORTED;
        if (sustainedPerformanceModeSupportedConfigured) {
            sustainedPerformanceModeSupported = requireAndroidPowerBoolean(
                    power.get("sustainedPerformanceModeSupported"),
                    "android.power.sustainedPerformanceModeSupported");
        }

        boolean rebootingUserspaceSupportedConfigured =
                power.containsKey("rebootingUserspaceSupported");
        boolean rebootingUserspaceSupported = ANDROID_POWER_DEFAULT_REBOOTING_USERSPACE_SUPPORTED;
        if (rebootingUserspaceSupportedConfigured) {
            rebootingUserspaceSupported = requireAndroidPowerBoolean(
                    power.get("rebootingUserspaceSupported"),
                    "android.power.rebootingUserspaceSupported");
        }

        boolean ignoringBatteryOptimizationsConfigured =
                power.containsKey("ignoringBatteryOptimizations");
        boolean ignoringBatteryOptimizations =
                ANDROID_POWER_DEFAULT_IGNORING_BATTERY_OPTIMIZATIONS;
        if (ignoringBatteryOptimizationsConfigured) {
            ignoringBatteryOptimizations = requireAndroidPowerBoolean(
                    power.get("ignoringBatteryOptimizations"),
                    "android.power.ignoringBatteryOptimizations");
        }

        this.androidPowerConfigured = true;
        this.androidPowerConfig = new AndroidPowerConfig(
                interactive, interactiveConfigured,
                powerSaveMode, powerSaveModeConfigured,
                deviceIdleMode, deviceIdleModeConfigured,
                deviceLightIdleMode, deviceLightIdleModeConfigured,
                lowPowerStandbyEnabled, lowPowerStandbyEnabledConfigured,
                sustainedPerformanceModeSupported, sustainedPerformanceModeSupportedConfigured,
                rebootingUserspaceSupported, rebootingUserspaceSupportedConfigured,
                ignoringBatteryOptimizations, ignoringBatteryOptimizationsConfigured);
    }

    private static boolean isAllowedAndroidPowerKey(String key) {
        for (String allowed : ANDROID_POWER_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * JSON Boolean only. Rejects null/String/Number and other non-Boolean types.
     */
    private static boolean requireAndroidPowerBoolean(Object raw, String path) {
        if (!(raw instanceof Boolean)) {
            throw new IllegalArgumentException(path + " must be a Boolean");
        }
        return ((Boolean) raw).booleanValue();
    }

    /**
     * Parse-time rules for optional {@code android.thermal} when present (v1 subset).
     * Missing node leaves {@link #isAndroidThermalConfigured()} false; explicit empty object is
     * configured with defaults {@code currentThermalStatus=0}, {@code headroom=1.0}.
     * Materializes {@link AndroidThermalConfig}; never retains JSONObject.
     */
    private void validateAndroidThermal() {
        JSONObject android = android();
        if (android == null || !android.containsKey("thermal")) {
            this.androidThermalConfigured = false;
            this.androidThermalConfig = null;
            return;
        }
        Object raw = android.get("thermal");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.thermal must be a JSONObject");
        }
        JSONObject thermal = (JSONObject) raw;
        for (String key : thermal.keySet()) {
            if (!isAllowedAndroidThermalKey(key)) {
                throw new IllegalArgumentException("android.thermal." + key
                        + " is not an allowed key (currentThermalStatus|headroom)");
            }
        }

        boolean currentThermalStatusConfigured = thermal.containsKey("currentThermalStatus");
        int currentThermalStatus = ANDROID_THERMAL_DEFAULT_CURRENT_THERMAL_STATUS;
        if (currentThermalStatusConfigured) {
            currentThermalStatus = requireExactJsonNumberIntField(thermal, "currentThermalStatus",
                    "android.thermal.currentThermalStatus",
                    ANDROID_THERMAL_STATUS_MIN, ANDROID_THERMAL_STATUS_MAX);
        }

        boolean headroomConfigured = thermal.containsKey("headroom");
        float headroom = ANDROID_THERMAL_DEFAULT_HEADROOM;
        if (headroomConfigured) {
            headroom = requireAndroidThermalHeadroom(thermal.get("headroom"),
                    "android.thermal.headroom");
        }

        this.androidThermalConfigured = true;
        this.androidThermalConfig = new AndroidThermalConfig(
                currentThermalStatus, currentThermalStatusConfigured,
                headroom, headroomConfigured);
    }

    /**
     * JSON Number → finite non-negative float. Rejects null/String/Boolean/NaN/Infinity/negative.
     * Preserves {@link Number#floatValue()} for valid inputs.
     */
    private static float requireAndroidThermalHeadroom(Object raw, String path) {
        if (!(raw instanceof Number) || raw instanceof Boolean) {
            throw new IllegalArgumentException(path
                    + " must be a JSON Number finite non-negative float");
        }
        float value = ((Number) raw).floatValue();
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0f) {
            throw new IllegalArgumentException(path
                    + " must be a finite non-negative float");
        }
        return value;
    }

    private static boolean isAllowedAndroidThermalKey(String key) {
        for (String allowed : ANDROID_THERMAL_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse-time rules for optional top-level {@code graphics} (v1 GLES/EGL query subset).
     * Missing node leaves {@link #isGraphicsConfigured()} false. Explicit {@code {}} is
     * configured with every field unconfigured. Materializes {@link GraphicsConfig};
     * never retains JSONObject/JSONArray. Does not infer across fields or from display.
     */
    private void validateGraphics() {
        if (!root.containsKey("graphics")) {
            this.graphicsConfigured = false;
            this.graphicsConfig = null;
            return;
        }
        Object raw = root.get("graphics");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("graphics must be a JSONObject");
        }
        JSONObject graphics = (JSONObject) raw;
        for (String key : graphics.keySet()) {
            if (!isAllowedGraphicsKey(key)) {
                throw new IllegalArgumentException("graphics." + key
                        + " is not an allowed key (vendor, renderer, version, shadingLanguageVersion,"
                        + " extensions, eglVendor, eglVersion, eglExtensions)");
            }
        }

        boolean vendorConfigured = graphics.containsKey("vendor");
        String vendor = vendorConfigured
                ? requireGraphicsString(graphics.get("vendor"), "graphics.vendor") : null;
        boolean rendererConfigured = graphics.containsKey("renderer");
        String renderer = rendererConfigured
                ? requireGraphicsString(graphics.get("renderer"), "graphics.renderer") : null;
        boolean versionConfigured = graphics.containsKey("version");
        String version = versionConfigured
                ? requireGraphicsString(graphics.get("version"), "graphics.version") : null;
        boolean shadingLanguageVersionConfigured = graphics.containsKey("shadingLanguageVersion");
        String shadingLanguageVersion = shadingLanguageVersionConfigured
                ? requireGraphicsString(graphics.get("shadingLanguageVersion"),
                "graphics.shadingLanguageVersion") : null;
        boolean extensionsConfigured = graphics.containsKey("extensions");
        List<String> extensions = extensionsConfigured
                ? requireGraphicsExtensionList(graphics.get("extensions"), "graphics.extensions")
                : Collections.<String>emptyList();
        boolean eglVendorConfigured = graphics.containsKey("eglVendor");
        String eglVendor = eglVendorConfigured
                ? requireGraphicsString(graphics.get("eglVendor"), "graphics.eglVendor") : null;
        boolean eglVersionConfigured = graphics.containsKey("eglVersion");
        String eglVersion = eglVersionConfigured
                ? requireGraphicsString(graphics.get("eglVersion"), "graphics.eglVersion") : null;
        boolean eglExtensionsConfigured = graphics.containsKey("eglExtensions");
        List<String> eglExtensions = eglExtensionsConfigured
                ? requireGraphicsExtensionList(graphics.get("eglExtensions"), "graphics.eglExtensions")
                : Collections.<String>emptyList();

        this.graphicsConfigured = true;
        this.graphicsConfig = new GraphicsConfig(vendor, vendorConfigured,
                renderer, rendererConfigured, version, versionConfigured,
                shadingLanguageVersion, shadingLanguageVersionConfigured,
                extensions, extensionsConfigured, eglVendor, eglVendorConfigured,
                eglVersion, eglVersionConfigured, eglExtensions, eglExtensionsConfigured);
    }

    private static boolean isAllowedGraphicsKey(String key) {
        for (String allowed : GRAPHICS_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Non-empty JSON String, 1..256 UTF-16, no NUL/CR/LF. No trim; no default.
     */
    private static String requireGraphicsString(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String (1.." + GRAPHICS_STRING_MAX_LEN
                    + " UTF-16 code units, no NUL/CR/LF)");
        }
        String value = (String) raw;
        int len = value.length();
        if (len < 1 || len > GRAPHICS_STRING_MAX_LEN) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String (1.." + GRAPHICS_STRING_MAX_LEN
                    + " UTF-16 code units, no NUL/CR/LF)");
        }
        if (value.indexOf('\0') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String (1.." + GRAPHICS_STRING_MAX_LEN
                    + " UTF-16 code units, no NUL/CR/LF)");
        }
        return value;
    }

    /**
     * JSONArray of unique non-empty tokens 1..256 UTF-16, no NUL/CR/LF/space. Allows {@code []}.
     */
    private static List<String> requireGraphicsExtensionList(Object raw, String path) {
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException(path + " must be a JSONArray of unique extension tokens");
        }
        JSONArray array = (JSONArray) raw;
        if (array.size() > GRAPHICS_EXTENSIONS_MAX) {
            throw new IllegalArgumentException(path + " must contain at most "
                    + GRAPHICS_EXTENSIONS_MAX + " tokens");
        }
        List<String> built = new ArrayList<String>(array.size());
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < array.size(); i++) {
            String itemPath = path + "[" + i + "]";
            String token = requireGraphicsString(array.get(i), itemPath);
            for (int c = 0; c < token.length(); c++) {
                if (Character.isWhitespace(token.charAt(c))) {
                    throw new IllegalArgumentException(itemPath
                            + " must not contain whitespace (space-joined at query time)");
                }
            }
            if (seen.contains(token)) {
                throw new IllegalArgumentException(itemPath + " duplicates extension token");
            }
            seen.add(token);
            built.add(token);
        }
        return Collections.unmodifiableList(built);
    }

    private static String joinGraphicsExtensions(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(tokens.get(i));
        }
        return sb.toString();
    }

    /**
     * Parse-time rules for optional {@code android.battery} when present (v1 subset).
     * Missing node leaves {@link #isAndroidBatteryConfigured()} false; explicit empty object is
     * configured with defaults {@code capacityPercent=73}, {@code charging=false}, and optional
     * counter/current/energy/status/charge-time keys unconfigured (no defaults; propertyId=1/2/3
     * stay UOE until present; propertyId=5 long-only when {@code energyCounterNwh} present;
     * propertyId=6 int-only when {@code status} present; computeChargeTimeRemaining when
     * {@code chargeTimeRemainingMillis} present). Materializes {@link AndroidBatteryConfig};
     * never retains JSONObject.
     */
    private void validateAndroidBattery() {
        JSONObject android = android();
        if (android == null || !android.containsKey("battery")) {
            this.androidBatteryConfigured = false;
            this.androidBatteryConfig = null;
            return;
        }
        Object raw = android.get("battery");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.battery must be a JSONObject");
        }
        JSONObject battery = (JSONObject) raw;
        for (String key : battery.keySet()) {
            if (!isAllowedAndroidBatteryKey(key)) {
                throw new IllegalArgumentException("android.battery." + key
                        + " is not an allowed key"
                        + " (capacityPercent|charging|chargeCounterUah|currentNowUa|"
                        + "currentAverageUa|energyCounterNwh|status|chargeTimeRemainingMillis|"
                        + "plugged|health|voltageMv|temperatureTenthsC)");
            }
        }

        boolean capacityPercentConfigured = battery.containsKey("capacityPercent");
        int capacityPercent = ANDROID_BATTERY_DEFAULT_CAPACITY_PERCENT;
        if (capacityPercentConfigured) {
            capacityPercent = requireExactJsonNumberIntField(battery, "capacityPercent",
                    "android.battery.capacityPercent",
                    ANDROID_BATTERY_CAPACITY_PERCENT_MIN, ANDROID_BATTERY_CAPACITY_PERCENT_MAX);
        }

        boolean chargingConfigured = battery.containsKey("charging");
        boolean charging = ANDROID_BATTERY_DEFAULT_CHARGING;
        if (chargingConfigured) {
            charging = requireAndroidPowerBoolean(battery.get("charging"),
                    "android.battery.charging");
        }

        boolean chargeCounterUahConfigured = battery.containsKey("chargeCounterUah");
        int chargeCounterUah = 0;
        if (chargeCounterUahConfigured) {
            chargeCounterUah = requireExactJsonNumberIntField(battery, "chargeCounterUah",
                    "android.battery.chargeCounterUah",
                    ANDROID_BATTERY_CHARGE_COUNTER_UAH_MIN, ANDROID_BATTERY_CHARGE_COUNTER_UAH_MAX);
        }

        boolean currentNowUaConfigured = battery.containsKey("currentNowUa");
        int currentNowUa = 0;
        if (currentNowUaConfigured) {
            currentNowUa = requireExactJsonNumberIntField(battery, "currentNowUa",
                    "android.battery.currentNowUa",
                    ANDROID_BATTERY_CURRENT_NOW_UA_MIN, ANDROID_BATTERY_CURRENT_NOW_UA_MAX);
        }

        boolean currentAverageUaConfigured = battery.containsKey("currentAverageUa");
        int currentAverageUa = 0;
        if (currentAverageUaConfigured) {
            currentAverageUa = requireExactJsonNumberIntField(battery, "currentAverageUa",
                    "android.battery.currentAverageUa",
                    ANDROID_BATTERY_CURRENT_AVERAGE_UA_MIN, ANDROID_BATTERY_CURRENT_AVERAGE_UA_MAX);
        }

        boolean energyCounterNwhConfigured = battery.containsKey("energyCounterNwh");
        long energyCounterNwh = 0L;
        if (energyCounterNwhConfigured) {
            energyCounterNwh = requireExactJsonNumberLongField(battery, "energyCounterNwh",
                    "android.battery.energyCounterNwh",
                    ANDROID_BATTERY_ENERGY_COUNTER_NWH_MIN, ANDROID_BATTERY_ENERGY_COUNTER_NWH_MAX);
        }

        boolean statusConfigured = battery.containsKey("status");
        int status = 0;
        if (statusConfigured) {
            status = requireExactJsonNumberIntField(battery, "status",
                    "android.battery.status",
                    ANDROID_BATTERY_STATUS_MIN, ANDROID_BATTERY_STATUS_MAX);
        }

        boolean chargeTimeRemainingMillisConfigured =
                battery.containsKey("chargeTimeRemainingMillis");
        long chargeTimeRemainingMillis = 0L;
        if (chargeTimeRemainingMillisConfigured) {
            chargeTimeRemainingMillis = requireExactJsonNumberLongField(battery,
                    "chargeTimeRemainingMillis",
                    "android.battery.chargeTimeRemainingMillis",
                    ANDROID_BATTERY_CHARGE_TIME_REMAINING_MILLIS_MIN,
                    ANDROID_BATTERY_CHARGE_TIME_REMAINING_MILLIS_MAX);
        }

        boolean pluggedConfigured = battery.containsKey("plugged");
        int plugged = 0;
        if (pluggedConfigured) {
            plugged = requireExactJsonNumberIntField(battery, "plugged",
                    "android.battery.plugged", 0, 4);
            if (plugged != 0 && plugged != 1 && plugged != 2 && plugged != 4) {
                throw new IllegalArgumentException(
                        "android.battery.plugged must be 0, 1, 2, or 4");
            }
        }

        boolean healthConfigured = battery.containsKey("health");
        int health = 0;
        if (healthConfigured) {
            health = requireExactJsonNumberIntField(battery, "health",
                    "android.battery.health",
                    ANDROID_BATTERY_HEALTH_MIN, ANDROID_BATTERY_HEALTH_MAX);
        }

        boolean voltageMvConfigured = battery.containsKey("voltageMv");
        int voltageMv = 0;
        if (voltageMvConfigured) {
            voltageMv = requireExactJsonNumberIntField(battery, "voltageMv",
                    "android.battery.voltageMv",
                    ANDROID_BATTERY_VOLTAGE_MV_MIN, ANDROID_BATTERY_VOLTAGE_MV_MAX);
        }

        boolean temperatureTenthsCConfigured = battery.containsKey("temperatureTenthsC");
        int temperatureTenthsC = 0;
        if (temperatureTenthsCConfigured) {
            temperatureTenthsC = requireExactJsonNumberIntField(battery, "temperatureTenthsC",
                    "android.battery.temperatureTenthsC",
                    ANDROID_BATTERY_TEMPERATURE_TENTHS_C_MIN,
                    ANDROID_BATTERY_TEMPERATURE_TENTHS_C_MAX);
        }

        this.androidBatteryConfigured = true;
        this.androidBatteryConfig = new AndroidBatteryConfig(
                capacityPercent, capacityPercentConfigured,
                charging, chargingConfigured,
                chargeCounterUah, chargeCounterUahConfigured,
                currentNowUa, currentNowUaConfigured,
                currentAverageUa, currentAverageUaConfigured,
                energyCounterNwh, energyCounterNwhConfigured,
                status, statusConfigured,
                chargeTimeRemainingMillis, chargeTimeRemainingMillisConfigured,
                plugged, pluggedConfigured,
                health, healthConfigured,
                voltageMv, voltageMvConfigured,
                temperatureTenthsC, temperatureTenthsCConfigured);
    }

    private static boolean isAllowedAndroidBatteryKey(String key) {
        for (String allowed : ANDROID_BATTERY_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Optional {@code android.powerProfile}. Missing node does not take over.
     * Explicit empty object is configured with no averagePower names.
     */
    private void validateAndroidPowerProfile() {
        JSONObject android = android();
        if (android == null || !android.containsKey("powerProfile")) {
            this.androidPowerProfileConfigured = false;
            this.androidPowerProfileConfig = null;
            return;
        }
        Object raw = android.get("powerProfile");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.powerProfile must be a JSONObject");
        }
        JSONObject profile = (JSONObject) raw;
        for (String key : profile.keySet()) {
            if (!isAllowedKey(key, ANDROID_POWER_PROFILE_ALLOWED_KEYS)) {
                throw new IllegalArgumentException("android.powerProfile." + key
                        + " is not an allowed key (averagePower)");
            }
        }
        boolean averagePowerConfigured = profile.containsKey("averagePower");
        Map<String, Double> averagePower = Collections.emptyMap();
        if (averagePowerConfigured) {
            Object mapRaw = profile.get("averagePower");
            if (!(mapRaw instanceof JSONObject)) {
                throw new IllegalArgumentException("android.powerProfile.averagePower must be a JSONObject");
            }
            JSONObject map = (JSONObject) mapRaw;
            Map<String, Double> built = new LinkedHashMap<String, Double>();
            for (String name : map.keySet()) {
                if (name == null || name.isEmpty() || name.indexOf('\0') >= 0
                        || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
                    throw new IllegalArgumentException(
                            "android.powerProfile.averagePower key must be a non-empty string without NUL/CR/LF");
                }
                Object valueRaw = map.get(name);
                if (!(valueRaw instanceof Number) || valueRaw instanceof Boolean) {
                    throw new IllegalArgumentException("android.powerProfile.averagePower." + name
                            + " must be a finite JSON Number");
                }
                double value = ((Number) valueRaw).doubleValue();
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    throw new IllegalArgumentException("android.powerProfile.averagePower." + name
                            + " must be a finite JSON Number");
                }
                built.put(name, Double.valueOf(value));
            }
            averagePower = Collections.unmodifiableMap(built);
        }
        this.androidPowerProfileConfigured = true;
        this.androidPowerProfileConfig = new AndroidPowerProfileConfig(averagePower, averagePowerConfigured);
    }

    private void validateAndroidCellInfo(JSONObject telephony) {
        if (!telephony.containsKey("cellInfo")) {
            this.androidCellInfoConfigured = false;
            this.androidCellInfo = Collections.emptyList();
            return;
        }
        Object raw = telephony.get("cellInfo");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("android.telephony.cellInfo must be a JSON array");
        }
        JSONArray array = (JSONArray) raw;
        List<CellInfoConfig> built = new ArrayList<CellInfoConfig>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String path = "android.telephony.cellInfo[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(path + " must be a JSONObject");
            }
            JSONObject row = (JSONObject) item;
            for (String field : row.keySet()) {
                if (!isAllowedKey(field, ANDROID_CELL_INFO_ALLOWED_KEYS)) {
                    throw new IllegalArgumentException(path + "." + field
                            + " is not an allowed field (type|registered|mcc|mnc|ci|pci|tac|earfcn|"
                            + "alphaLong|alphaShort)");
                }
            }
            if (!row.containsKey("type")) {
                throw new IllegalArgumentException(path + ".type is required");
            }
            Object typeRaw = row.get("type");
            if (!(typeRaw instanceof String)) {
                throw new IllegalArgumentException(path + ".type must be a String (gsm|cdma|lte|wcdma|nr)");
            }
            String type = ((String) typeRaw);
            if (!isAllowedKey(type, ANDROID_CELL_INFO_TYPES)) {
                throw new IllegalArgumentException(path + ".type must be gsm|cdma|lte|wcdma|nr");
            }

            boolean registeredConfigured = row.containsKey("registered");
            boolean registered = false;
            if (registeredConfigured) {
                Object registeredRaw = row.get("registered");
                if (!(registeredRaw instanceof Boolean)) {
                    throw new IllegalArgumentException(path + ".registered must be a Boolean");
                }
                registered = ((Boolean) registeredRaw).booleanValue();
            }

            boolean mccConfigured = row.containsKey("mcc");
            String mcc = null;
            if (mccConfigured) {
                mcc = requireOptionalNullableNoTrimString(row.get("mcc"), path + ".mcc");
            }
            boolean mncConfigured = row.containsKey("mnc");
            String mnc = null;
            if (mncConfigured) {
                mnc = requireOptionalNullableNoTrimString(row.get("mnc"), path + ".mnc");
            }

            boolean ciConfigured = row.containsKey("ci");
            int ci = 0;
            if (ciConfigured) {
                ci = requireExactJsonNumberIntField(row, "ci", path + ".ci", 0, Integer.MAX_VALUE);
            }
            boolean pciConfigured = row.containsKey("pci");
            int pci = 0;
            if (pciConfigured) {
                pci = requireExactJsonNumberIntField(row, "pci", path + ".pci", 0, Integer.MAX_VALUE);
            }
            boolean tacConfigured = row.containsKey("tac");
            int tac = 0;
            if (tacConfigured) {
                tac = requireExactJsonNumberIntField(row, "tac", path + ".tac", 0, Integer.MAX_VALUE);
            }
            boolean earfcnConfigured = row.containsKey("earfcn");
            int earfcn = 0;
            if (earfcnConfigured) {
                earfcn = requireExactJsonNumberIntField(row, "earfcn", path + ".earfcn", 0, Integer.MAX_VALUE);
            }

            boolean alphaLongConfigured = row.containsKey("alphaLong");
            String alphaLong = null;
            if (alphaLongConfigured) {
                alphaLong = requireOptionalNullableNoTrimString(row.get("alphaLong"), path + ".alphaLong");
            }
            boolean alphaShortConfigured = row.containsKey("alphaShort");
            String alphaShort = null;
            if (alphaShortConfigured) {
                alphaShort = requireOptionalNullableNoTrimString(row.get("alphaShort"), path + ".alphaShort");
            }

            built.add(new CellInfoConfig(type, registered, registeredConfigured,
                    mcc, mccConfigured, mnc, mncConfigured,
                    ci, ciConfigured, pci, pciConfigured, tac, tacConfigured,
                    earfcn, earfcnConfigured,
                    alphaLong, alphaLongConfigured, alphaShort, alphaShortConfigured));
        }
        this.androidCellInfoConfigured = true;
        this.androidCellInfo = Collections.unmodifiableList(built);
    }

    private void validateNetworkWifiScanResults(JSONObject wifi) {
        if (!wifi.containsKey("scanResults")) {
            this.networkWifiScanResultsConfigured = false;
            this.networkWifiScanResults = Collections.emptyList();
            return;
        }
        Object raw = wifi.get("scanResults");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("network.wifi.scanResults must be a JSON array");
        }
        JSONArray array = (JSONArray) raw;
        List<WifiScanResultConfig> built = new ArrayList<WifiScanResultConfig>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String path = "network.wifi.scanResults[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(path + " must be a JSONObject");
            }
            JSONObject row = (JSONObject) item;
            for (String field : row.keySet()) {
                if (!isAllowedKey(field, NETWORK_WIFI_SCAN_RESULT_ALLOWED_KEYS)) {
                    throw new IllegalArgumentException(path + "." + field
                            + " is not an allowed field (ssid|bssid|rssi|frequencyMhz)");
                }
            }
            boolean ssidConfigured = row.containsKey("ssid");
            String ssid = null;
            if (ssidConfigured) {
                ssid = requireOptionalNullableNoTrimString(row.get("ssid"), path + ".ssid");
            }
            boolean bssidConfigured = row.containsKey("bssid");
            String bssid = null;
            if (bssidConfigured) {
                Object bssidRaw = row.get("bssid");
                if (bssidRaw != null) {
                    bssid = requireMacStringValue(bssidRaw, path + ".bssid");
                }
            }
            boolean rssiConfigured = row.containsKey("rssi");
            int rssi = 0;
            if (rssiConfigured) {
                rssi = requireExactJsonNumberIntField(row, "rssi", path + ".rssi", -127, 0);
            }
            boolean frequencyMhzConfigured = row.containsKey("frequencyMhz");
            int frequencyMhz = 0;
            if (frequencyMhzConfigured) {
                frequencyMhz = requireExactJsonNumberIntField(row, "frequencyMhz",
                        path + ".frequencyMhz", 0, 100000);
            }
            built.add(new WifiScanResultConfig(ssid, ssidConfigured, bssid, bssidConfigured,
                    rssi, rssiConfigured, frequencyMhz, frequencyMhzConfigured));
        }
        this.networkWifiScanResultsConfigured = true;
        this.networkWifiScanResults = Collections.unmodifiableList(built);
    }

    private static String requireAndroidDisplayUniqueId(Object raw) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException("android.display.uniqueId must be a String");
        }
        String value = (String) raw;
        if (value.isEmpty() || value.length() > ANDROID_DISPLAY_UNIQUE_ID_MAX_LEN
                || value.indexOf('\0') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "android.display.uniqueId must be a non-empty string of at most "
                            + ANDROID_DISPLAY_UNIQUE_ID_MAX_LEN + " chars without NUL/CR/LF");
        }
        return value;
    }

    /**
     * Parse-time rules for optional {@code android.cameras} when present (count + infos +
     * optional {@code streams} subset). Missing node leaves
     * {@link #isAndroidCamerasConfigured()} false; explicit empty object is configured with
     * default {@code count=0} and infos/streams unconfigured. When {@code infos} is present,
     * {@code count} must be explicit and {@code infos.length == count}. Each info entry requires
     * {@code facing} and {@code orientation}; optional {@code canDisableShutterSound} is a strict
     * JSON Boolean with independent presence (absent is not configured, never defaulted).
     * {@code streams} is independently presence-gated: omitted does not take over preview/JPEG;
     * explicit {@code []} is an empty snapshot. Each stream requires {@code cameraId} (unique,
     * {@code 0..count-1}), {@code width}/{@code height}, and at least one of {@code previewHex}
     * (NV21, length {@code width*height*3/2}), {@code previewFile} (POSIX overlay path to NV21),
     * {@code jpegHex}, or {@code jpegFile}. {@code previewHex} and {@code previewFile} are
     * mutually exclusive; {@code jpegHex} and {@code jpegFile} are mutually exclusive.
     * Overlay file bytes are resolved at delivery via {@link #resolveCameraPreview} /
     * {@link #resolveCameraJpeg}, not at parse time. Materializes
     * {@link AndroidCamerasConfig}; never retains JSONObject/JSONArray.
     */
    private void validateAndroidCameras() {
        JSONObject android = android();
        if (android == null || !android.containsKey("cameras")) {
            this.androidCamerasConfigured = false;
            this.androidCamerasConfig = null;
            return;
        }
        Object raw = android.get("cameras");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.cameras must be a JSONObject");
        }
        JSONObject cameras = (JSONObject) raw;
        for (String key : cameras.keySet()) {
            if (!isAllowedAndroidCamerasKey(key)) {
                throw new IllegalArgumentException("android.cameras." + key
                        + " is not an allowed key (count, infos, streams)");
            }
        }

        boolean infosConfigured = cameras.containsKey("infos");
        boolean countConfigured = cameras.containsKey("count");
        int count = ANDROID_CAMERAS_DEFAULT_COUNT;
        if (countConfigured) {
            count = requireExactJsonNumberIntField(cameras, "count",
                    "android.cameras.count",
                    ANDROID_CAMERAS_COUNT_MIN, ANDROID_CAMERAS_COUNT_MAX);
        }

        List<AndroidCameraInfoConfig> infos;
        if (!infosConfigured) {
            infos = Collections.emptyList();
        } else {
            if (!countConfigured) {
                throw new IllegalArgumentException(
                        "android.cameras.count is required when android.cameras.infos is present");
            }
            Object infosRaw = cameras.get("infos");
            if (infosRaw == null || !(infosRaw instanceof JSONArray)) {
                throw new IllegalArgumentException("android.cameras.infos must be a JSONArray");
            }
            JSONArray array = (JSONArray) infosRaw;
            if (array.size() != count) {
                throw new IllegalArgumentException(
                        "android.cameras.infos length must equal android.cameras.count");
            }
            List<AndroidCameraInfoConfig> built = new ArrayList<AndroidCameraInfoConfig>(array.size());
            for (int i = 0; i < array.size(); i++) {
                String itemPath = "android.cameras.infos[" + i + "]";
                Object item = array.get(i);
                if (!(item instanceof JSONObject)) {
                    throw new IllegalArgumentException(itemPath + " must be a JSONObject");
                }
                JSONObject info = (JSONObject) item;
                for (String key : info.keySet()) {
                    if (!isAllowedAndroidCameraInfoKey(key)) {
                        throw new IllegalArgumentException(itemPath + "." + key
                                + " is not an allowed key (facing, orientation, canDisableShutterSound)");
                    }
                }
                int facing = requireExactJsonNumberIntField(info, "facing",
                        itemPath + ".facing",
                        ANDROID_CAMERA_FACING_MIN, ANDROID_CAMERA_FACING_MAX);
                int orientation = requireExactJsonNumberIntField(info, "orientation",
                        itemPath + ".orientation",
                        0, 270);
                if (!ANDROID_CAMERA_ORIENTATIONS.contains(Integer.valueOf(orientation))) {
                    throw new IllegalArgumentException(itemPath + ".orientation"
                            + " must be an integer in {0,90,180,270}");
                }
                boolean canDisableShutterSoundConfigured = info.containsKey("canDisableShutterSound");
                boolean canDisableShutterSound = false;
                if (canDisableShutterSoundConfigured) {
                    Object shutterRaw = info.get("canDisableShutterSound");
                    if (!(shutterRaw instanceof Boolean)) {
                        throw new IllegalArgumentException(itemPath
                                + ".canDisableShutterSound must be a Boolean");
                    }
                    canDisableShutterSound = ((Boolean) shutterRaw).booleanValue();
                }
                built.add(new AndroidCameraInfoConfig(facing, orientation,
                        canDisableShutterSound, canDisableShutterSoundConfigured));
            }
            infos = Collections.unmodifiableList(built);
        }

        boolean streamsConfigured = cameras.containsKey("streams");
        List<AndroidCameraStreamConfig> streams;
        if (!streamsConfigured) {
            streams = Collections.emptyList();
        } else {
            Object streamsRaw = cameras.get("streams");
            if (!(streamsRaw instanceof JSONArray)) {
                throw new IllegalArgumentException("android.cameras.streams must be a JSON array");
            }
            JSONArray array = (JSONArray) streamsRaw;
            List<AndroidCameraStreamConfig> built = new ArrayList<AndroidCameraStreamConfig>(array.size());
            Set<Integer> seenIds = new HashSet<Integer>();
            for (int i = 0; i < array.size(); i++) {
                String itemPath = "android.cameras.streams[" + i + "]";
                Object item = array.get(i);
                if (!(item instanceof JSONObject)) {
                    throw new IllegalArgumentException(itemPath + " must be a JSONObject");
                }
                JSONObject row = (JSONObject) item;
                for (String key : row.keySet()) {
                    if (!isAllowedAndroidCameraStreamKey(key)) {
                        throw new IllegalArgumentException(itemPath + "." + key
                                + " is not an allowed field "
                                + "(cameraId|width|height|previewHex|jpegHex|previewFile|jpegFile)");
                    }
                }
                if (!row.containsKey("cameraId")) {
                    throw new IllegalArgumentException(itemPath + ".cameraId is required");
                }
                int cameraId = requireExactJsonNumberIntField(row, "cameraId",
                        itemPath + ".cameraId", 0, ANDROID_CAMERAS_COUNT_MAX - 1);
                if (cameraId >= count) {
                    throw new IllegalArgumentException(itemPath
                            + ".cameraId must be less than android.cameras.count");
                }
                if (!seenIds.add(Integer.valueOf(cameraId))) {
                    throw new IllegalArgumentException(itemPath + ".cameraId must be unique");
                }
                if (!row.containsKey("width") || !row.containsKey("height")) {
                    throw new IllegalArgumentException(itemPath
                            + " requires width and height");
                }
                int width = requireExactJsonNumberIntField(row, "width",
                        itemPath + ".width",
                        ANDROID_CAMERA_STREAM_SIZE_MIN, ANDROID_CAMERA_STREAM_SIZE_MAX);
                int height = requireExactJsonNumberIntField(row, "height",
                        itemPath + ".height",
                        ANDROID_CAMERA_STREAM_SIZE_MIN, ANDROID_CAMERA_STREAM_SIZE_MAX);

                boolean previewHexConfigured = row.containsKey("previewHex");
                boolean previewFileConfigured = row.containsKey("previewFile");
                if (previewHexConfigured && previewFileConfigured) {
                    throw new IllegalArgumentException(itemPath
                            + " previewHex and previewFile are mutually exclusive");
                }
                byte[] previewNv21 = null;
                String previewFile = null;
                if (previewHexConfigured) {
                    previewNv21 = parseHex(itemPath + ".previewHex",
                            requireHexString(row.get("previewHex"), itemPath + ".previewHex"));
                    requireEvenCameraPreviewSize(itemPath, "previewHex", width, height);
                    requireCameraPreviewNv21Length(itemPath, ".previewHex",
                            width, height, previewNv21.length);
                }
                if (previewFileConfigured) {
                    previewFile = requireCameraOverlayPath(row.get("previewFile"),
                            itemPath + ".previewFile");
                    requireEvenCameraPreviewSize(itemPath, "previewFile", width, height);
                }
                boolean previewConfigured = previewHexConfigured || previewFileConfigured;

                boolean jpegHexConfigured = row.containsKey("jpegHex");
                boolean jpegFileConfigured = row.containsKey("jpegFile");
                if (jpegHexConfigured && jpegFileConfigured) {
                    throw new IllegalArgumentException(itemPath
                            + " jpegHex and jpegFile are mutually exclusive");
                }
                byte[] jpeg = null;
                String jpegFile = null;
                if (jpegHexConfigured) {
                    jpeg = parseHex(itemPath + ".jpegHex",
                            requireHexString(row.get("jpegHex"), itemPath + ".jpegHex"));
                    if (jpeg.length < 1) {
                        throw new IllegalArgumentException(itemPath + ".jpegHex must not be empty");
                    }
                }
                if (jpegFileConfigured) {
                    jpegFile = requireCameraOverlayPath(row.get("jpegFile"),
                            itemPath + ".jpegFile");
                }
                boolean jpegConfigured = jpegHexConfigured || jpegFileConfigured;
                if (!previewConfigured && !jpegConfigured) {
                    throw new IllegalArgumentException(itemPath
                            + " requires previewHex, previewFile, jpegHex and/or jpegFile");
                }
                built.add(new AndroidCameraStreamConfig(cameraId, width, height,
                        previewNv21, previewConfigured, previewFile,
                        jpeg, jpegConfigured, jpegFile));
            }
            streams = Collections.unmodifiableList(built);
        }

        this.androidCamerasConfigured = true;
        this.androidCamerasConfig = new AndroidCamerasConfig(
                count, countConfigured, infos, infosConfigured, streams, streamsConfigured);
    }

    private static boolean isAllowedAndroidCameraStreamKey(String key) {
        for (String allowed : ANDROID_CAMERA_STREAM_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static String requireHexString(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path + " must be a hex String");
        }
        return (String) raw;
    }

    /**
     * Absolute POSIX overlay path, 1..{@link #ANDROID_CAMERA_FILE_PATH_MAX}. Same guest-path
     * rules as {@link ProfileFileOverlay}: no {@code ..}, backslash, drive letter, or whitespace.
     * Bytes are not loaded here; delivery uses {@link #resolveCameraPreview} / {@link #resolveCameraJpeg}.
     */
    private static String requireCameraOverlayPath(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be an absolute POSIX overlay path (1.."
                    + ANDROID_CAMERA_FILE_PATH_MAX + ")");
        }
        String value = (String) raw;
        if (value.length() < 1 || value.length() > ANDROID_CAMERA_FILE_PATH_MAX) {
            throw new IllegalArgumentException(path
                    + " must be an absolute POSIX overlay path (1.."
                    + ANDROID_CAMERA_FILE_PATH_MAX + ")");
        }
        String normalized = ProfileFileOverlay.normalizeGuestPath(value);
        if (normalized == null) {
            throw new IllegalArgumentException(path
                    + " must be an absolute POSIX overlay path (1.."
                    + ANDROID_CAMERA_FILE_PATH_MAX + ")");
        }
        return normalized;
    }

    private static void requireEvenCameraPreviewSize(String itemPath, String field,
                                                     int width, int height) {
        if ((width & 1) != 0 || (height & 1) != 0) {
            throw new IllegalArgumentException(itemPath
                    + " " + field + " requires even width and height (NV21)");
        }
    }

    private static void requireCameraPreviewNv21Length(String itemPath, String fieldSuffix,
                                                       int width, int height, int length) {
        long expected = cameraPreviewNv21Length(width, height);
        if (expected < 0 || length != (int) expected) {
            throw new IllegalArgumentException(itemPath
                    + fieldSuffix + " length must be width*height*3/2");
        }
    }

    private static long cameraPreviewNv21Length(int width, int height) {
        long expected = (long) width * (long) height * 3L / 2L;
        return expected > Integer.MAX_VALUE ? -1L : expected;
    }

    private static boolean isAllowedAndroidCamerasKey(String key) {
        for (String allowed : ANDROID_CAMERAS_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedAndroidCameraInfoKey(String key) {
        for (String allowed : ANDROID_CAMERA_INFO_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse-time rules for optional {@code android.sensors} when present (v1 types subset).
     * Missing node leaves {@link #isAndroidSensorsConfigured()} false; explicit empty object or
     * omitted {@code types} / {@code dynamicTypes} yields an empty corresponding list; explicit
     * {@code []} is allowed. Types are unique ordered positive integers {@code 1..65535};
     * {@code dynamicTypes} follows the same rules and may overlap {@code types}.
     * {@code dynamicDiscoverySupported} is a strict Boolean defaulting to {@code false} when
     * omitted, independent of {@code dynamicTypes}. Optional {@code names} is a JSONObject of
     * strict decimal type keys ({@code 1..65535}, no whitespace/sign/decimal/leading zero) to
     * non-empty Strings (1..256 UTF-16, no NUL/CR/LF); each key must already appear in
     * {@code types} or {@code dynamicTypes}; explicit {@code {}} is allowed. Never inferred
     * in either direction. Optional {@code vendors} 使用与 {@code names} 相同的键值语法，
     * 且每个 type 必须已出现在 {@code types} 或 {@code dynamicTypes}；允许显式 {@code {}}。
     * {@code vendors} 与 {@code names} 互不推导。Optional {@code versions} 键规则与
     * {@code names}/{@code vendors} 相同，值为精确 JSON 整数 {@code 0..Integer.MAX_VALUE}；
     * 每个 type 必须已出现在 {@code types} 或 {@code dynamicTypes}；允许显式 {@code {}}。
     * {@code versions} 与 {@code names}/{@code vendors}/lists 互不推导。
     * Optional {@code stringTypes} 使用与 {@code names}/{@code vendors} 相同的键值语法，
     * 且每个 type 必须已出现在 {@code types} 或 {@code dynamicTypes}；允许显式 {@code {}}。
     * {@code stringTypes} 与 {@code names}/{@code vendors}/{@code versions}/lists 互不推导。
     * Optional {@code maximumRanges} 键规则与 {@code names}/{@code vendors} 相同，值为有限
     * JSON Number，按 {@link Number#floatValue()} 转为 Java float，范围
     * {@code 0.0..Float.MAX_VALUE}；每个 type 必须已出现在 {@code types} 或
     * {@code dynamicTypes}；允许显式 {@code {}}。{@code maximumRanges} 与
     * {@code names}/{@code vendors}/{@code versions}/{@code stringTypes}/{@code resolutions}/lists
     * 互不推导。Optional {@code resolutions} 键规则与 {@code names}/{@code vendors} 相同，值为有限
     * JSON Number，按 {@link Number#floatValue()} 转为 Java float，范围
     * {@code 0.0..Float.MAX_VALUE}；每个 type 必须已出现在 {@code types} 或
     * {@code dynamicTypes}；允许显式 {@code {}}。{@code resolutions} 与
     * {@code names}/{@code vendors}/{@code versions}/{@code stringTypes}/{@code maximumRanges}/lists
     * 互不推导，绝不从 {@code maximumRanges} 或其他字段推导。
     * Optional {@code powers} 键规则与 {@code names}/{@code vendors} 相同，值为有限
     * JSON Number，按 {@link Number#floatValue()} 转为 Java float，范围
     * {@code 0.0..Float.MAX_VALUE}；每个 type 必须已出现在 {@code types} 或
     * {@code dynamicTypes}；允许显式 {@code {}}。{@code powers} 与
     * {@code names}/{@code vendors}/{@code versions}/{@code stringTypes}/{@code maximumRanges}/
     * {@code resolutions}/lists 互不推导，绝不从其它传感器字段推导。
     * Optional {@code minDelaysMicros} 键规则与 {@code names}/{@code vendors} 相同，值为精确
     * JSON 整数 {@code -1..Integer.MAX_VALUE}（{@code -1} 为一次性传感器）；每个 type 必须已出现在 {@code types} 或
     * {@code dynamicTypes}；允许显式 {@code {}}。{@code minDelaysMicros} 与
     * {@code names}/{@code vendors}/{@code versions}/{@code stringTypes}/{@code maximumRanges}/
     * {@code resolutions}/{@code powers}/{@code maxDelaysMicros}/lists 互不推导，绝不从其它传感器字段推导。
     * Optional {@code maxDelaysMicros} 键规则与 {@code names}/{@code vendors} 相同，值为精确
     * JSON 整数 {@code 0..Integer.MAX_VALUE}；每个 type 必须已出现在 {@code types} 或
     * {@code dynamicTypes}；允许显式 {@code {}}。{@code maxDelaysMicros} 与
     * {@code names}/{@code vendors}/{@code versions}/{@code stringTypes}/{@code maximumRanges}/
     * {@code resolutions}/{@code powers}/{@code minDelaysMicros}/{@code fifoReservedEventCounts}/lists
     * 互不推导，绝不从其它传感器字段推导，也不校验 {@code max>=min}。
     * Optional {@code fifoReservedEventCounts} 键规则与 {@code names}/{@code vendors} 相同，值为精确
     * JSON 整数 {@code 0..Integer.MAX_VALUE}；每个 type 必须已出现在 {@code types} 或
     * {@code dynamicTypes}；允许显式 {@code {}}。{@code fifoReservedEventCounts} 与
     * {@code names}/{@code vendors}/{@code versions}/{@code stringTypes}/{@code maximumRanges}/
     * {@code resolutions}/{@code powers}/{@code minDelaysMicros}/{@code maxDelaysMicros}/
     * {@code fifoMaxEventCounts}/lists 互不推导，绝不从其它传感器字段推导，也不校验与
     * {@code fifoMaxEventCounts} 的大小关系。
     * Optional {@code fifoMaxEventCounts} 键规则与 {@code names}/{@code vendors} 相同，值为精确
     * JSON 整数 {@code 0..Integer.MAX_VALUE}；每个 type 必须已出现在 {@code types} 或
     * {@code dynamicTypes}；允许显式 {@code {}}。{@code fifoMaxEventCounts} 与
     * {@code names}/{@code vendors}/{@code versions}/{@code stringTypes}/{@code maximumRanges}/
     * {@code resolutions}/{@code powers}/{@code minDelaysMicros}/{@code maxDelaysMicros}/
     * {@code fifoReservedEventCounts}/lists 互不推导，绝不从其它传感器字段推导，也不校验与
     * {@code fifoReservedEventCounts} 的大小关系。
     * Optional {@code wakeUpSensors} 键规则与 {@code names}/{@code vendors} 相同，值为严格
     * JSON Boolean；每个 type 必须已出现在 {@code types} 或 {@code dynamicTypes}；允许显式
     * {@code {}}。{@code wakeUpSensors} 与 lists / {@code names} / {@code vendors} /
     * {@code versions} / {@code stringTypes} / {@code maximumRanges} / {@code resolutions} /
     * {@code powers} / {@code minDelaysMicros} / {@code maxDelaysMicros} /
     * {@code fifoReservedEventCounts} / {@code fifoMaxEventCounts} 互不推导。
     * 显式 {@code false} 与键缺失不同。
     * Optional {@code sensorIds} 键规则与 {@code names}/{@code vendors} 相同，值为精确 JSON 整数
     * {@code -1..Integer.MAX_VALUE}（含 {@code -1}/{@code 0}）；每个 type
     * 必须已出现在 {@code types} 或 {@code dynamicTypes}；允许显式 {@code {}}。
     * Optional {@code reportingModes} 键规则相同，值为精确 JSON 整数 {@code 0..3}。
     * Optional {@code dynamicSensors} 键规则相同，值为严格 JSON Boolean；**不**从
     * {@code dynamicTypes} 推断。显式 {@code false} 与缺失不同。
     * Optional {@code requiredPermissions} 键规则相同，值为 String {@code 0..256}
     * UTF-16（允许空串，禁 NUL/CR/LF）。
     * Optional {@code additionalInfoSupported} 键规则相同，值为严格 JSON Boolean。
     * Optional {@code highestDirectReportRateLevels} 键规则相同，值为精确 JSON 整数
     * {@code 0..3}（RATE_STOP/NORMAL/FAST/VERY_FAST）；**不**从 {@code reportingModes} 推断。
     * Optional {@code directChannelTypesSupported} 键规则相同，值为 JSONArray of 唯一精确整数
     * {@code 1}/{@code 2}（TYPE_MEMORY_FILE/TYPE_HARDWARE_BUFFER）；允许显式 {@code []}。
     * **不**从 {@code highestDirectReportRateLevels} 推断。不物化直接通道对象。
     * 以上七字段与 lists / FIFO / {@code wakeUpSensors} / 其它描述字段互不推导。
     * 物化为 {@link AndroidSensorsConfig}；不保留 JSONObject/JSONArray。
     */
    private void validateAndroidSensors() {
        JSONObject android = android();
        if (android == null || !android.containsKey("sensors")) {
            this.androidSensorsConfigured = false;
            this.androidSensorsConfig = null;
            this.androidSensorSamplesConfigured = false;
            this.androidSensorSamples = Collections.emptyMap();
            return;
        }
        Object raw = android.get("sensors");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.sensors must be a JSONObject");
        }
        JSONObject sensors = (JSONObject) raw;
        for (String key : sensors.keySet()) {
            if (!isAllowedAndroidSensorsKey(key)) {
                throw new IllegalArgumentException("android.sensors." + key
                        + " is not an allowed key (types, dynamicTypes, dynamicDiscoverySupported, names, vendors, versions, stringTypes, maximumRanges, resolutions, powers, minDelaysMicros, maxDelaysMicros, fifoReservedEventCounts, fifoMaxEventCounts, wakeUpSensors, sensorIds, reportingModes, dynamicSensors, requiredPermissions, additionalInfoSupported, highestDirectReportRateLevels, directChannelTypesSupported, samples)");
            }
        }

        boolean typesConfigured = sensors.containsKey("types");
        List<Integer> types;
        if (!typesConfigured) {
            types = Collections.emptyList();
        } else {
            Object typesRaw = sensors.get("types");
            if (typesRaw == null || !(typesRaw instanceof JSONArray)) {
                throw new IllegalArgumentException(
                        "android.sensors.types must be a JSONArray of unique positive Integers");
            }
            JSONArray array = (JSONArray) typesRaw;
            List<Integer> built = new ArrayList<Integer>(array.size());
            Set<Integer> seen = new HashSet<Integer>();
            for (int i = 0; i < array.size(); i++) {
                String itemPath = "android.sensors.types[" + i + "]";
                int typeValue = requireExactJsonNumberIntValue(array.get(i), itemPath,
                        ANDROID_SENSORS_TYPE_MIN, ANDROID_SENSORS_TYPE_MAX);
                Integer boxed = Integer.valueOf(typeValue);
                if (seen.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " duplicates sensor type " + typeValue);
                }
                seen.add(boxed);
                built.add(boxed);
            }
            types = Collections.unmodifiableList(built);
        }

        boolean dynamicTypesConfigured = sensors.containsKey("dynamicTypes");
        List<Integer> dynamicTypes;
        if (!dynamicTypesConfigured) {
            dynamicTypes = Collections.emptyList();
        } else {
            Object dynamicRaw = sensors.get("dynamicTypes");
            if (dynamicRaw == null || !(dynamicRaw instanceof JSONArray)) {
                throw new IllegalArgumentException(
                        "android.sensors.dynamicTypes must be a JSONArray of unique positive Integers");
            }
            JSONArray array = (JSONArray) dynamicRaw;
            List<Integer> built = new ArrayList<Integer>(array.size());
            Set<Integer> seen = new HashSet<Integer>();
            for (int i = 0; i < array.size(); i++) {
                String itemPath = "android.sensors.dynamicTypes[" + i + "]";
                int typeValue = requireExactJsonNumberIntValue(array.get(i), itemPath,
                        ANDROID_SENSORS_TYPE_MIN, ANDROID_SENSORS_TYPE_MAX);
                Integer boxed = Integer.valueOf(typeValue);
                if (seen.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " duplicates sensor type " + typeValue);
                }
                seen.add(boxed);
                built.add(boxed);
            }
            dynamicTypes = Collections.unmodifiableList(built);
        }

        boolean dynamicDiscoverySupported = false;
        if (sensors.containsKey("dynamicDiscoverySupported")) {
            dynamicDiscoverySupported = requireAndroidPowerBoolean(
                    sensors.get("dynamicDiscoverySupported"),
                    "android.sensors.dynamicDiscoverySupported");
        }

        boolean namesConfigured = sensors.containsKey("names");
        Map<Integer, String> names;
        if (!namesConfigured) {
            names = Collections.emptyMap();
        } else {
            Object namesRaw = sensors.get("names");
            if (!(namesRaw instanceof JSONObject)) {
                throw new IllegalArgumentException("android.sensors.names must be a JSONObject");
            }
            JSONObject namesObj = (JSONObject) namesRaw;
            Map<Integer, String> built = new LinkedHashMap<Integer, String>(namesObj.size());
            for (String typeKey : namesObj.keySet()) {
                String itemPath = "android.sensors.names." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                String name = requireAndroidSensorName(namesObj.get(typeKey), itemPath);
                built.put(boxed, name);
            }
            names = Collections.unmodifiableMap(built);
        }

        boolean vendorsConfigured = sensors.containsKey("vendors");
        Map<Integer, String> vendors;
        if (!vendorsConfigured) {
            vendors = Collections.emptyMap();
        } else {
            Object vendorsRaw = sensors.get("vendors");
            if (!(vendorsRaw instanceof JSONObject)) {
                throw new IllegalArgumentException("android.sensors.vendors must be a JSONObject");
            }
            JSONObject vendorsObj = (JSONObject) vendorsRaw;
            Map<Integer, String> built = new LinkedHashMap<Integer, String>(vendorsObj.size());
            for (String typeKey : vendorsObj.keySet()) {
                String itemPath = "android.sensors.vendors." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                String vendor = requireAndroidSensorName(vendorsObj.get(typeKey), itemPath);
                built.put(boxed, vendor);
            }
            vendors = Collections.unmodifiableMap(built);
        }

        boolean versionsConfigured = sensors.containsKey("versions");
        Map<Integer, Integer> versions;
        if (!versionsConfigured) {
            versions = Collections.emptyMap();
        } else {
            Object versionsRaw = sensors.get("versions");
            if (!(versionsRaw instanceof JSONObject)) {
                throw new IllegalArgumentException("android.sensors.versions must be a JSONObject");
            }
            JSONObject versionsObj = (JSONObject) versionsRaw;
            Map<Integer, Integer> built = new LinkedHashMap<Integer, Integer>(versionsObj.size());
            for (String typeKey : versionsObj.keySet()) {
                String itemPath = "android.sensors.versions." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                int version = requireAndroidSensorVersion(versionsObj.get(typeKey), itemPath);
                built.put(boxed, Integer.valueOf(version));
            }
            versions = Collections.unmodifiableMap(built);
        }

        boolean stringTypesConfigured = sensors.containsKey("stringTypes");
        Map<Integer, String> stringTypes;
        if (!stringTypesConfigured) {
            stringTypes = Collections.emptyMap();
        } else {
            Object stringTypesRaw = sensors.get("stringTypes");
            if (!(stringTypesRaw instanceof JSONObject)) {
                throw new IllegalArgumentException("android.sensors.stringTypes must be a JSONObject");
            }
            JSONObject stringTypesObj = (JSONObject) stringTypesRaw;
            Map<Integer, String> built = new LinkedHashMap<Integer, String>(stringTypesObj.size());
            for (String typeKey : stringTypesObj.keySet()) {
                String itemPath = "android.sensors.stringTypes." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                String stringType = requireAndroidSensorName(stringTypesObj.get(typeKey), itemPath);
                built.put(boxed, stringType);
            }
            stringTypes = Collections.unmodifiableMap(built);
        }

        boolean maximumRangesConfigured = sensors.containsKey("maximumRanges");
        Map<Integer, Float> maximumRanges;
        if (!maximumRangesConfigured) {
            maximumRanges = Collections.emptyMap();
        } else {
            Object maximumRangesRaw = sensors.get("maximumRanges");
            if (!(maximumRangesRaw instanceof JSONObject)) {
                throw new IllegalArgumentException("android.sensors.maximumRanges must be a JSONObject");
            }
            JSONObject maximumRangesObj = (JSONObject) maximumRangesRaw;
            Map<Integer, Float> built = new LinkedHashMap<Integer, Float>(maximumRangesObj.size());
            for (String typeKey : maximumRangesObj.keySet()) {
                String itemPath = "android.sensors.maximumRanges." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                float maximumRange = requireAndroidSensorMaximumRange(
                        maximumRangesObj.get(typeKey), itemPath);
                built.put(boxed, Float.valueOf(maximumRange));
            }
            maximumRanges = Collections.unmodifiableMap(built);
        }

        boolean resolutionsConfigured = sensors.containsKey("resolutions");
        Map<Integer, Float> resolutions;
        if (!resolutionsConfigured) {
            resolutions = Collections.emptyMap();
        } else {
            Object resolutionsRaw = sensors.get("resolutions");
            if (!(resolutionsRaw instanceof JSONObject)) {
                throw new IllegalArgumentException("android.sensors.resolutions must be a JSONObject");
            }
            JSONObject resolutionsObj = (JSONObject) resolutionsRaw;
            Map<Integer, Float> built = new LinkedHashMap<Integer, Float>(resolutionsObj.size());
            for (String typeKey : resolutionsObj.keySet()) {
                String itemPath = "android.sensors.resolutions." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                float resolution = requireAndroidSensorResolution(
                        resolutionsObj.get(typeKey), itemPath);
                built.put(boxed, Float.valueOf(resolution));
            }
            resolutions = Collections.unmodifiableMap(built);
        }

        boolean powersConfigured = sensors.containsKey("powers");
        Map<Integer, Float> powers;
        if (!powersConfigured) {
            powers = Collections.emptyMap();
        } else {
            Object powersRaw = sensors.get("powers");
            if (!(powersRaw instanceof JSONObject)) {
                throw new IllegalArgumentException("android.sensors.powers must be a JSONObject");
            }
            JSONObject powersObj = (JSONObject) powersRaw;
            Map<Integer, Float> built = new LinkedHashMap<Integer, Float>(powersObj.size());
            for (String typeKey : powersObj.keySet()) {
                String itemPath = "android.sensors.powers." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                float power = requireAndroidSensorPower(powersObj.get(typeKey), itemPath);
                built.put(boxed, Float.valueOf(power));
            }
            powers = Collections.unmodifiableMap(built);
        }

        boolean minDelaysMicrosConfigured = sensors.containsKey("minDelaysMicros");
        Map<Integer, Integer> minDelaysMicros;
        if (!minDelaysMicrosConfigured) {
            minDelaysMicros = Collections.emptyMap();
        } else {
            Object minDelaysRaw = sensors.get("minDelaysMicros");
            if (!(minDelaysRaw instanceof JSONObject)) {
                throw new IllegalArgumentException("android.sensors.minDelaysMicros must be a JSONObject");
            }
            JSONObject minDelaysObj = (JSONObject) minDelaysRaw;
            Map<Integer, Integer> built = new LinkedHashMap<Integer, Integer>(minDelaysObj.size());
            for (String typeKey : minDelaysObj.keySet()) {
                String itemPath = "android.sensors.minDelaysMicros." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                int minDelay = requireAndroidSensorMinDelayMicros(minDelaysObj.get(typeKey), itemPath);
                built.put(boxed, Integer.valueOf(minDelay));
            }
            minDelaysMicros = Collections.unmodifiableMap(built);
        }

        boolean maxDelaysMicrosConfigured = sensors.containsKey("maxDelaysMicros");
        Map<Integer, Integer> maxDelaysMicros;
        if (!maxDelaysMicrosConfigured) {
            maxDelaysMicros = Collections.emptyMap();
        } else {
            Object maxDelaysRaw = sensors.get("maxDelaysMicros");
            if (!(maxDelaysRaw instanceof JSONObject)) {
                throw new IllegalArgumentException("android.sensors.maxDelaysMicros must be a JSONObject");
            }
            JSONObject maxDelaysObj = (JSONObject) maxDelaysRaw;
            Map<Integer, Integer> built = new LinkedHashMap<Integer, Integer>(maxDelaysObj.size());
            for (String typeKey : maxDelaysObj.keySet()) {
                String itemPath = "android.sensors.maxDelaysMicros." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                int maxDelay = requireAndroidSensorMaxDelayMicros(maxDelaysObj.get(typeKey), itemPath);
                built.put(boxed, Integer.valueOf(maxDelay));
            }
            maxDelaysMicros = Collections.unmodifiableMap(built);
        }

        boolean fifoReservedEventCountsConfigured = sensors.containsKey("fifoReservedEventCounts");
        Map<Integer, Integer> fifoReservedEventCounts;
        if (!fifoReservedEventCountsConfigured) {
            fifoReservedEventCounts = Collections.emptyMap();
        } else {
            Object fifoReservedRaw = sensors.get("fifoReservedEventCounts");
            if (!(fifoReservedRaw instanceof JSONObject)) {
                throw new IllegalArgumentException(
                        "android.sensors.fifoReservedEventCounts must be a JSONObject");
            }
            JSONObject fifoReservedObj = (JSONObject) fifoReservedRaw;
            Map<Integer, Integer> built = new LinkedHashMap<Integer, Integer>(fifoReservedObj.size());
            for (String typeKey : fifoReservedObj.keySet()) {
                String itemPath = "android.sensors.fifoReservedEventCounts." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                int fifoReserved = requireAndroidSensorFifoReservedEventCount(
                        fifoReservedObj.get(typeKey), itemPath);
                built.put(boxed, Integer.valueOf(fifoReserved));
            }
            fifoReservedEventCounts = Collections.unmodifiableMap(built);
        }

        boolean fifoMaxEventCountsConfigured = sensors.containsKey("fifoMaxEventCounts");
        Map<Integer, Integer> fifoMaxEventCounts;
        if (!fifoMaxEventCountsConfigured) {
            fifoMaxEventCounts = Collections.emptyMap();
        } else {
            Object fifoMaxRaw = sensors.get("fifoMaxEventCounts");
            if (!(fifoMaxRaw instanceof JSONObject)) {
                throw new IllegalArgumentException(
                        "android.sensors.fifoMaxEventCounts must be a JSONObject");
            }
            JSONObject fifoMaxObj = (JSONObject) fifoMaxRaw;
            Map<Integer, Integer> built = new LinkedHashMap<Integer, Integer>(fifoMaxObj.size());
            for (String typeKey : fifoMaxObj.keySet()) {
                String itemPath = "android.sensors.fifoMaxEventCounts." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                int fifoMax = requireAndroidSensorFifoMaxEventCount(
                        fifoMaxObj.get(typeKey), itemPath);
                built.put(boxed, Integer.valueOf(fifoMax));
            }
            fifoMaxEventCounts = Collections.unmodifiableMap(built);
        }

        boolean wakeUpSensorsConfigured = sensors.containsKey("wakeUpSensors");
        Map<Integer, Boolean> wakeUpSensors;
        if (!wakeUpSensorsConfigured) {
            wakeUpSensors = Collections.emptyMap();
        } else {
            Object wakeUpRaw = sensors.get("wakeUpSensors");
            if (!(wakeUpRaw instanceof JSONObject)) {
                throw new IllegalArgumentException(
                        "android.sensors.wakeUpSensors must be a JSONObject");
            }
            JSONObject wakeUpObj = (JSONObject) wakeUpRaw;
            Map<Integer, Boolean> built = new LinkedHashMap<Integer, Boolean>(wakeUpObj.size());
            for (String typeKey : wakeUpObj.keySet()) {
                String itemPath = "android.sensors.wakeUpSensors." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                boolean wakeUp = requireAndroidPowerBoolean(wakeUpObj.get(typeKey), itemPath);
                built.put(boxed, Boolean.valueOf(wakeUp));
            }
            wakeUpSensors = Collections.unmodifiableMap(built);
        }

        boolean sensorIdsConfigured = sensors.containsKey("sensorIds");
        Map<Integer, Integer> sensorIds;
        if (!sensorIdsConfigured) {
            sensorIds = Collections.emptyMap();
        } else {
            Object idsRaw = sensors.get("sensorIds");
            if (!(idsRaw instanceof JSONObject)) {
                throw new IllegalArgumentException("android.sensors.sensorIds must be a JSONObject");
            }
            JSONObject idsObj = (JSONObject) idsRaw;
            Map<Integer, Integer> built = new LinkedHashMap<Integer, Integer>(idsObj.size());
            for (String typeKey : idsObj.keySet()) {
                String itemPath = "android.sensors.sensorIds." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                int id = requireAndroidSensorId(idsObj.get(typeKey), itemPath);
                built.put(boxed, Integer.valueOf(id));
            }
            sensorIds = Collections.unmodifiableMap(built);
        }

        boolean reportingModesConfigured = sensors.containsKey("reportingModes");
        Map<Integer, Integer> reportingModes;
        if (!reportingModesConfigured) {
            reportingModes = Collections.emptyMap();
        } else {
            Object reportingRaw = sensors.get("reportingModes");
            if (!(reportingRaw instanceof JSONObject)) {
                throw new IllegalArgumentException(
                        "android.sensors.reportingModes must be a JSONObject");
            }
            JSONObject reportingObj = (JSONObject) reportingRaw;
            Map<Integer, Integer> built = new LinkedHashMap<Integer, Integer>(reportingObj.size());
            for (String typeKey : reportingObj.keySet()) {
                String itemPath = "android.sensors.reportingModes." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                int mode = requireAndroidSensorReportingMode(reportingObj.get(typeKey), itemPath);
                built.put(boxed, Integer.valueOf(mode));
            }
            reportingModes = Collections.unmodifiableMap(built);
        }

        boolean dynamicSensorsConfigured = sensors.containsKey("dynamicSensors");
        Map<Integer, Boolean> dynamicSensors;
        if (!dynamicSensorsConfigured) {
            dynamicSensors = Collections.emptyMap();
        } else {
            Object dynamicRaw = sensors.get("dynamicSensors");
            if (!(dynamicRaw instanceof JSONObject)) {
                throw new IllegalArgumentException(
                        "android.sensors.dynamicSensors must be a JSONObject");
            }
            JSONObject dynamicObj = (JSONObject) dynamicRaw;
            Map<Integer, Boolean> built = new LinkedHashMap<Integer, Boolean>(dynamicObj.size());
            for (String typeKey : dynamicObj.keySet()) {
                String itemPath = "android.sensors.dynamicSensors." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                boolean dynamic = requireAndroidPowerBoolean(dynamicObj.get(typeKey), itemPath);
                built.put(boxed, Boolean.valueOf(dynamic));
            }
            dynamicSensors = Collections.unmodifiableMap(built);
        }

        boolean requiredPermissionsConfigured = sensors.containsKey("requiredPermissions");
        Map<Integer, String> requiredPermissions;
        if (!requiredPermissionsConfigured) {
            requiredPermissions = Collections.emptyMap();
        } else {
            Object permRaw = sensors.get("requiredPermissions");
            if (!(permRaw instanceof JSONObject)) {
                throw new IllegalArgumentException(
                        "android.sensors.requiredPermissions must be a JSONObject");
            }
            JSONObject permObj = (JSONObject) permRaw;
            Map<Integer, String> built = new LinkedHashMap<Integer, String>(permObj.size());
            for (String typeKey : permObj.keySet()) {
                String itemPath = "android.sensors.requiredPermissions." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                String permission = requireAndroidSensorRequiredPermission(
                        permObj.get(typeKey), itemPath);
                built.put(boxed, permission);
            }
            requiredPermissions = Collections.unmodifiableMap(built);
        }

        boolean additionalInfoSupportedConfigured = sensors.containsKey("additionalInfoSupported");
        Map<Integer, Boolean> additionalInfoSupported;
        if (!additionalInfoSupportedConfigured) {
            additionalInfoSupported = Collections.emptyMap();
        } else {
            Object additionalRaw = sensors.get("additionalInfoSupported");
            if (!(additionalRaw instanceof JSONObject)) {
                throw new IllegalArgumentException(
                        "android.sensors.additionalInfoSupported must be a JSONObject");
            }
            JSONObject additionalObj = (JSONObject) additionalRaw;
            Map<Integer, Boolean> built = new LinkedHashMap<Integer, Boolean>(additionalObj.size());
            for (String typeKey : additionalObj.keySet()) {
                String itemPath = "android.sensors.additionalInfoSupported." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                boolean additional = requireAndroidPowerBoolean(additionalObj.get(typeKey), itemPath);
                built.put(boxed, Boolean.valueOf(additional));
            }
            additionalInfoSupported = Collections.unmodifiableMap(built);
        }

        boolean highestDirectReportRateLevelsConfigured =
                sensors.containsKey("highestDirectReportRateLevels");
        Map<Integer, Integer> highestDirectReportRateLevels;
        if (!highestDirectReportRateLevelsConfigured) {
            highestDirectReportRateLevels = Collections.emptyMap();
        } else {
            Object rateRaw = sensors.get("highestDirectReportRateLevels");
            if (!(rateRaw instanceof JSONObject)) {
                throw new IllegalArgumentException(
                        "android.sensors.highestDirectReportRateLevels must be a JSONObject");
            }
            JSONObject rateObj = (JSONObject) rateRaw;
            Map<Integer, Integer> built = new LinkedHashMap<Integer, Integer>(rateObj.size());
            for (String typeKey : rateObj.keySet()) {
                String itemPath = "android.sensors.highestDirectReportRateLevels." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                int rate = requireAndroidSensorHighestDirectReportRateLevel(
                        rateObj.get(typeKey), itemPath);
                built.put(boxed, Integer.valueOf(rate));
            }
            highestDirectReportRateLevels = Collections.unmodifiableMap(built);
        }

        boolean directChannelTypesSupportedConfigured =
                sensors.containsKey("directChannelTypesSupported");
        Map<Integer, Set<Integer>> directChannelTypesSupported;
        if (!directChannelTypesSupportedConfigured) {
            directChannelTypesSupported = Collections.emptyMap();
        } else {
            Object channelRaw = sensors.get("directChannelTypesSupported");
            if (!(channelRaw instanceof JSONObject)) {
                throw new IllegalArgumentException(
                        "android.sensors.directChannelTypesSupported must be a JSONObject");
            }
            JSONObject channelObj = (JSONObject) channelRaw;
            Map<Integer, Set<Integer>> built = new LinkedHashMap<Integer, Set<Integer>>(
                    channelObj.size());
            for (String typeKey : channelObj.keySet()) {
                String itemPath = "android.sensors.directChannelTypesSupported." + typeKey;
                int typeValue = requireAndroidSensorNameTypeKey(typeKey, itemPath);
                Integer boxed = Integer.valueOf(typeValue);
                if (!types.contains(boxed) && !dynamicTypes.contains(boxed)) {
                    throw new IllegalArgumentException(itemPath
                            + " type must appear in android.sensors.types or android.sensors.dynamicTypes");
                }
                Object listRaw = channelObj.get(typeKey);
                if (!(listRaw instanceof JSONArray)) {
                    throw new IllegalArgumentException(itemPath
                            + " must be a JSONArray of unique exact integers in range "
                            + ANDROID_SENSOR_DIRECT_CHANNEL_TYPE_MIN + ".."
                            + ANDROID_SENSOR_DIRECT_CHANNEL_TYPE_MAX);
                }
                JSONArray list = (JSONArray) listRaw;
                Set<Integer> channels = new LinkedHashSet<Integer>();
                for (int i = 0; i < list.size(); i++) {
                    String elemPath = itemPath + "[" + i + "]";
                    int channelType = requireAndroidSensorDirectChannelType(list.get(i), elemPath);
                    Integer channelBoxed = Integer.valueOf(channelType);
                    if (channels.contains(channelBoxed)) {
                        throw new IllegalArgumentException(elemPath
                                + " duplicates direct channel type " + channelType);
                    }
                    channels.add(channelBoxed);
                }
                built.put(boxed, Collections.unmodifiableSet(channels));
            }
            directChannelTypesSupported = Collections.unmodifiableMap(built);
        }

        boolean samplesConfigured = sensors.containsKey("samples");
        Map<Integer, float[]> samples = Collections.emptyMap();
        if (samplesConfigured) {
            Object samplesRaw = sensors.get("samples");
            if (!(samplesRaw instanceof JSONObject)) {
                throw new IllegalArgumentException("android.sensors.samples must be a JSONObject");
            }
            JSONObject samplesObj = (JSONObject) samplesRaw;
            Map<Integer, float[]> builtSamples = new LinkedHashMap<Integer, float[]>();
            Set<Integer> knownTypes = new HashSet<Integer>(types);
            knownTypes.addAll(dynamicTypes);
            for (String sampleKey : samplesObj.keySet()) {
                int typeValue = requireAndroidSensorNameTypeKey(sampleKey,
                        "android.sensors.samples." + sampleKey);
                Integer boxed = Integer.valueOf(typeValue);
                if (!knownTypes.contains(boxed)) {
                    throw new IllegalArgumentException("android.sensors.samples." + sampleKey
                            + " is not listed in types or dynamicTypes");
                }
                Object listRaw = samplesObj.get(sampleKey);
                if (!(listRaw instanceof JSONArray)) {
                    throw new IllegalArgumentException("android.sensors.samples." + sampleKey
                            + " must be a JSONArray of finite numbers");
                }
                JSONArray values = (JSONArray) listRaw;
                float[] floats = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    Object item = values.get(i);
                    if (!(item instanceof Number)) {
                        throw new IllegalArgumentException("android.sensors.samples." + sampleKey
                                + "[" + i + "] must be a finite JSON Number");
                    }
                    float f = ((Number) item).floatValue();
                    if (Float.isNaN(f) || Float.isInfinite(f)) {
                        throw new IllegalArgumentException("android.sensors.samples." + sampleKey
                                + "[" + i + "] must be finite");
                    }
                    floats[i] = f;
                }
                builtSamples.put(boxed, floats);
            }
            samples = Collections.unmodifiableMap(builtSamples);
        }
        this.androidSensorSamplesConfigured = samplesConfigured;
        this.androidSensorSamples = samples;

        this.androidSensorsConfigured = true;
        this.androidSensorsConfig = new AndroidSensorsConfig(types, typesConfigured,
                dynamicTypes, dynamicTypesConfigured, dynamicDiscoverySupported,
                names, namesConfigured, vendors, vendorsConfigured,
                versions, versionsConfigured, stringTypes, stringTypesConfigured,
                maximumRanges, maximumRangesConfigured, resolutions, resolutionsConfigured,
                powers, powersConfigured, minDelaysMicros, minDelaysMicrosConfigured,
                maxDelaysMicros, maxDelaysMicrosConfigured,
                fifoReservedEventCounts, fifoReservedEventCountsConfigured,
                fifoMaxEventCounts, fifoMaxEventCountsConfigured,
                wakeUpSensors, wakeUpSensorsConfigured,
                sensorIds, sensorIdsConfigured, reportingModes, reportingModesConfigured,
                dynamicSensors, dynamicSensorsConfigured,
                requiredPermissions, requiredPermissionsConfigured,
                additionalInfoSupported, additionalInfoSupportedConfigured,
                highestDirectReportRateLevels, highestDirectReportRateLevelsConfigured,
                directChannelTypesSupported, directChannelTypesSupportedConfigured);
    }

    /**
     * Strict decimal sensor-type object key: ASCII digits {@code [1-9][0-9]*}, value in
     * {@code 1..65535}. Rejects empty, whitespace, {@code +}/{@code -}, decimals, and leading
     * zeros (including {@code "0"}). There is no prior sensors-map key grammar; this matches
     * the project's decimal-id style (see CPU ids) except that type {@code 0} is out of range
     * so the literal-{@code 0} exception does not apply.
     */
    private static int requireAndroidSensorNameTypeKey(String key, String path) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException(path
                    + " must be a strict decimal integer key in 1.." + ANDROID_SENSORS_TYPE_MAX);
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException(path
                        + " must be a strict decimal integer key in 1.." + ANDROID_SENSORS_TYPE_MAX
                        + " (no whitespace, sign, decimal, or leading zero)");
            }
        }
        if (key.charAt(0) == '0') {
            throw new IllegalArgumentException(path
                    + " must be a strict decimal integer key in 1.." + ANDROID_SENSORS_TYPE_MAX
                    + " (no whitespace, sign, decimal, or leading zero)");
        }
        int value;
        try {
            value = Integer.parseInt(key);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(path
                    + " must be a strict decimal integer key in 1.." + ANDROID_SENSORS_TYPE_MAX
                    + " (no whitespace, sign, decimal, or leading zero)");
        }
        if (value < ANDROID_SENSORS_TYPE_MIN || value > ANDROID_SENSORS_TYPE_MAX) {
            throw new IllegalArgumentException(path
                    + " must be a strict decimal integer key in 1.." + ANDROID_SENSORS_TYPE_MAX
                    + " (no whitespace, sign, decimal, or leading zero)");
        }
        return value;
    }

    /**
     * JSON Number → finite Java float in {@code 0.0..Float.MAX_VALUE} via
     * {@link Number#floatValue()}. Rejects null/String/Boolean/array/object/NaN/Infinity/
     * negative and values that are non-finite after float conversion (including overflow
     * past {@link Float#MAX_VALUE}).
     */
    private static float requireAndroidSensorMaximumRange(Object raw, String path) {
        final String requirement = path
                + " must be a JSON Number finite float in range 0.0.." + Float.MAX_VALUE;
        if (!(raw instanceof Number) || raw instanceof Boolean) {
            throw new IllegalArgumentException(requirement);
        }
        float value = ((Number) raw).floatValue();
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0f) {
            throw new IllegalArgumentException(requirement);
        }
        return value;
    }

    /**
     * JSON Number → finite Java float in {@code 0.0..Float.MAX_VALUE} via
     * {@link Number#floatValue()}。独立于 {@link #requireAndroidSensorMaximumRange}，
     * 不从 {@code maximumRanges} 或其他字段推导。拒绝 null/String/Boolean/array/object/NaN/
     * Infinity/负数以及 float 转换后非有限（含超过 {@link Float#MAX_VALUE} 的溢出）。
     */
    private static float requireAndroidSensorResolution(Object raw, String path) {
        final String requirement = path
                + " must be a JSON Number finite float in range 0.0.." + Float.MAX_VALUE;
        if (!(raw instanceof Number) || raw instanceof Boolean) {
            throw new IllegalArgumentException(requirement);
        }
        float value = ((Number) raw).floatValue();
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0f) {
            throw new IllegalArgumentException(requirement);
        }
        return value;
    }

    /**
     * JSON Number → finite Java float in {@code 0.0..Float.MAX_VALUE} via
     * {@link Number#floatValue()}。独立于 {@link #requireAndroidSensorMaximumRange} 与
     * {@link #requireAndroidSensorResolution}，不从 {@code maximumRanges} /
     * {@code resolutions} 或其他传感器字段推导。拒绝 null/String/Boolean/array/object/NaN/
     * Infinity/负数以及 float 转换后非有限（含超过 {@link Float#MAX_VALUE} 的溢出）。
     */
    private static float requireAndroidSensorPower(Object raw, String path) {
        final String requirement = path
                + " must be a JSON Number finite float in range 0.0.." + Float.MAX_VALUE;
        if (!(raw instanceof Number) || raw instanceof Boolean) {
            throw new IllegalArgumentException(requirement);
        }
        float value = ((Number) raw).floatValue();
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0f) {
            throw new IllegalArgumentException(requirement);
        }
        return value;
    }

    /**
     * Exact JSON Number integer in {@code 0..Integer.MAX_VALUE} for {@code fifoReservedEventCounts}.
     * Independent of {@link #requireAndroidSensorMinDelayMicros}、
     * {@link #requireAndroidSensorMaxDelayMicros} 与
     * {@link #requireAndroidSensorFifoMaxEventCount}；不从 {@code minDelaysMicros} /
     * {@code maxDelaysMicros} / {@code fifoMaxEventCounts} 或其他传感器字段推导，
     * 也不校验与 {@code fifoMaxEventCounts} 的大小关系。
     * Rejects Float/Double/BigDecimal (including {@code 1.0}), String, Boolean, null,
     * negatives, and overflow.
     */
    private static int requireAndroidSensorFifoReservedEventCount(Object raw, String path) {
        final String requirement = path
                + " must be an exact JSON Number integer in range 0.." + Integer.MAX_VALUE;
        if (!(raw instanceof Number) || raw instanceof Boolean
                || raw instanceof Float || raw instanceof Double || raw instanceof BigDecimal) {
            throw new IllegalArgumentException(requirement);
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(requirement);
            }
        } else {
            value = ((Number) raw).longValue();
        }
        if (value < 0L || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(requirement);
        }
        return (int) value;
    }

    /**
     * Exact JSON Number integer in {@code 0..Integer.MAX_VALUE} for {@code fifoMaxEventCounts}.
     * Independent of {@link #requireAndroidSensorFifoReservedEventCount}、
     * {@link #requireAndroidSensorMinDelayMicros} 与
     * {@link #requireAndroidSensorMaxDelayMicros}；不从 {@code fifoReservedEventCounts} /
     * {@code minDelaysMicros} / {@code maxDelaysMicros} 或其他传感器字段推导，
     * 也不校验与 {@code fifoReservedEventCounts} 的大小关系。
     * Rejects Float/Double/BigDecimal (including {@code 1.0}), String, Boolean, null,
     * negatives, and overflow.
     */
    private static int requireAndroidSensorFifoMaxEventCount(Object raw, String path) {
        final String requirement = path
                + " must be an exact JSON Number integer in range 0.." + Integer.MAX_VALUE;
        if (!(raw instanceof Number) || raw instanceof Boolean
                || raw instanceof Float || raw instanceof Double || raw instanceof BigDecimal) {
            throw new IllegalArgumentException(requirement);
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(requirement);
            }
        } else {
            value = ((Number) raw).longValue();
        }
        if (value < 0L || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(requirement);
        }
        return (int) value;
    }

    /**
     * Exact JSON Number integer in {@code -1..Integer.MAX_VALUE} for
     * {@code sensorIds} / {@code Sensor.getId()}。含 {@code -1} 与 {@code 0}。不从其它传感器字段推导。
     * Rejects Float/Double/BigDecimal (including {@code 1.0}), String, Boolean, null,
     * values below {@code -1}, and overflow.
     */
    private static int requireAndroidSensorId(Object raw, String path) {
        final String requirement = path
                + " must be an exact JSON Number integer in range -1.." + Integer.MAX_VALUE;
        if (!(raw instanceof Number) || raw instanceof Boolean
                || raw instanceof Float || raw instanceof Double || raw instanceof BigDecimal) {
            throw new IllegalArgumentException(requirement);
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(requirement);
            }
        } else {
            value = ((Number) raw).longValue();
        }
        if (value < -1L || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(requirement);
        }
        return (int) value;
    }

    /**
     * Exact JSON Number integer in {@code 0..3} for {@code highestDirectReportRateLevels} /
     * {@code Sensor.getHighestDirectReportRateLevel()}（RATE_STOP/NORMAL/FAST/VERY_FAST）。
     * Independent of {@link #requireAndroidSensorReportingMode}.
     * Rejects Float/Double/BigDecimal (including {@code 1.0}), String, Boolean, null,
     * negatives, and values outside {@code 0..3}.
     */
    private static int requireAndroidSensorHighestDirectReportRateLevel(Object raw, String path) {
        final String requirement = path
                + " must be an exact JSON Number integer in range "
                + ANDROID_SENSOR_DIRECT_REPORT_RATE_MIN + ".."
                + ANDROID_SENSOR_DIRECT_REPORT_RATE_MAX;
        if (!(raw instanceof Number) || raw instanceof Boolean
                || raw instanceof Float || raw instanceof Double || raw instanceof BigDecimal) {
            throw new IllegalArgumentException(requirement);
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(requirement);
            }
        } else {
            value = ((Number) raw).longValue();
        }
        if (value < ANDROID_SENSOR_DIRECT_REPORT_RATE_MIN
                || value > ANDROID_SENSOR_DIRECT_REPORT_RATE_MAX) {
            throw new IllegalArgumentException(requirement);
        }
        return (int) value;
    }

    /**
     * Exact JSON Number integer in {@code 1..2} for {@code directChannelTypesSupported} /
     * {@code Sensor.isDirectChannelTypeSupported(int)}（TYPE_MEMORY_FILE/TYPE_HARDWARE_BUFFER）。
     * Independent of {@link #requireAndroidSensorHighestDirectReportRateLevel}.
     * Rejects Float/Double/BigDecimal (including {@code 1.0}), String, Boolean, null,
     * and values outside {@code 1..2}.
     */
    private static int requireAndroidSensorDirectChannelType(Object raw, String path) {
        final String requirement = path
                + " must be an exact JSON Number integer in range "
                + ANDROID_SENSOR_DIRECT_CHANNEL_TYPE_MIN + ".."
                + ANDROID_SENSOR_DIRECT_CHANNEL_TYPE_MAX;
        if (!(raw instanceof Number) || raw instanceof Boolean
                || raw instanceof Float || raw instanceof Double || raw instanceof BigDecimal) {
            throw new IllegalArgumentException(requirement);
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(requirement);
            }
        } else {
            value = ((Number) raw).longValue();
        }
        if (value < ANDROID_SENSOR_DIRECT_CHANNEL_TYPE_MIN
                || value > ANDROID_SENSOR_DIRECT_CHANNEL_TYPE_MAX) {
            throw new IllegalArgumentException(requirement);
        }
        return (int) value;
    }

    /**
     * Exact JSON Number integer in {@code 0..3} for {@code reportingModes} /
     * {@code Sensor.getReportingMode()}（CONTINUOUS/ON_CHANGE/ONE_SHOT/SPECIAL_TRIGGER）。
     * Rejects Float/Double/BigDecimal (including {@code 1.0}), String, Boolean, null,
     * negatives, and values outside {@code 0..3}.
     */
    private static int requireAndroidSensorReportingMode(Object raw, String path) {
        final String requirement = path
                + " must be an exact JSON Number integer in range "
                + ANDROID_SENSOR_REPORTING_MODE_MIN + ".." + ANDROID_SENSOR_REPORTING_MODE_MAX;
        if (!(raw instanceof Number) || raw instanceof Boolean
                || raw instanceof Float || raw instanceof Double || raw instanceof BigDecimal) {
            throw new IllegalArgumentException(requirement);
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(requirement);
            }
        } else {
            value = ((Number) raw).longValue();
        }
        if (value < ANDROID_SENSOR_REPORTING_MODE_MIN
                || value > ANDROID_SENSOR_REPORTING_MODE_MAX) {
            throw new IllegalArgumentException(requirement);
        }
        return (int) value;
    }

    /**
     * JSON String for {@code requiredPermissions} / {@code Sensor.getRequiredPermission()}：
     * {@code 0..256} UTF-16 code units, empty string allowed, no NUL/CR/LF. No trim.
     */
    private static String requireAndroidSensorRequiredPermission(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a String (0.." + ANDROID_SENSORS_NAME_MAX_LEN
                    + " UTF-16 code units, empty allowed, no NUL/CR/LF)");
        }
        String value = (String) raw;
        int len = value.length();
        if (len > ANDROID_SENSORS_NAME_MAX_LEN) {
            throw new IllegalArgumentException(path
                    + " must be a String (0.." + ANDROID_SENSORS_NAME_MAX_LEN
                    + " UTF-16 code units, empty allowed, no NUL/CR/LF)");
        }
        if (value.indexOf('\0') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(path
                    + " must be a String (0.." + ANDROID_SENSORS_NAME_MAX_LEN
                    + " UTF-16 code units, empty allowed, no NUL/CR/LF)");
        }
        return value;
    }

    /**
     * Exact JSON Number integer in {@code 0..Integer.MAX_VALUE} for {@code maxDelaysMicros}.
     * Independent of {@link #requireAndroidSensorMinDelayMicros}；不从 {@code minDelaysMicros}
     * 或其他传感器字段推导，也不校验 {@code max>=min}。
     * Rejects Float/Double/BigDecimal (including {@code 1.0}), String, Boolean, null,
     * negatives, and overflow.
     */
    private static int requireAndroidSensorMaxDelayMicros(Object raw, String path) {
        final String requirement = path
                + " must be an exact JSON Number integer in range 0.." + Integer.MAX_VALUE;
        if (!(raw instanceof Number) || raw instanceof Boolean
                || raw instanceof Float || raw instanceof Double || raw instanceof BigDecimal) {
            throw new IllegalArgumentException(requirement);
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(requirement);
            }
        } else {
            value = ((Number) raw).longValue();
        }
        if (value < 0L || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(requirement);
        }
        return (int) value;
    }

    /**
     * Exact JSON Number integer in {@code -1..Integer.MAX_VALUE} for {@code minDelaysMicros}.
     * {@code -1} is the Android {@code Sensor.getMinDelay()} value for one-shot sensors.
     * Independent of {@link #requireAndroidSensorVersion}；不从 {@code versions} 或其他传感器字段推导。
     * Rejects Float/Double/BigDecimal (including {@code 1.0}), String, Boolean, null,
     * values below {@code -1}, and overflow.
     */
    private static int requireAndroidSensorMinDelayMicros(Object raw, String path) {
        final String requirement = path
                + " must be an exact JSON Number integer in range -1.." + Integer.MAX_VALUE;
        if (!(raw instanceof Number) || raw instanceof Boolean
                || raw instanceof Float || raw instanceof Double || raw instanceof BigDecimal) {
            throw new IllegalArgumentException(requirement);
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(requirement);
            }
        } else {
            value = ((Number) raw).longValue();
        }
        if (value < -1L || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(requirement);
        }
        return (int) value;
    }

    /**
     * Exact JSON Number integer in {@code 0..Integer.MAX_VALUE}. Rejects Float/Double/BigDecimal
     * (including {@code 1.0}), String, Boolean, null, negatives, and overflow.
     */
    private static int requireAndroidSensorVersion(Object raw, String path) {
        final String requirement = path
                + " must be an exact JSON Number integer in range 0.." + Integer.MAX_VALUE;
        if (!(raw instanceof Number) || raw instanceof Boolean
                || raw instanceof Float || raw instanceof Double || raw instanceof BigDecimal) {
            throw new IllegalArgumentException(requirement);
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(requirement);
            }
        } else {
            value = ((Number) raw).longValue();
        }
        if (value < 0L || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(requirement);
        }
        return (int) value;
    }

    /**
     * Non-empty JSON String, 1..256 UTF-16 code units, no NUL/CR/LF. No trim; no default.
     */
    private static String requireAndroidSensorName(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String (1.." + ANDROID_SENSORS_NAME_MAX_LEN
                    + " UTF-16 code units, no NUL/CR/LF)");
        }
        String value = (String) raw;
        int len = value.length();
        if (len < 1 || len > ANDROID_SENSORS_NAME_MAX_LEN) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String (1.." + ANDROID_SENSORS_NAME_MAX_LEN
                    + " UTF-16 code units, no NUL/CR/LF)");
        }
        if (value.indexOf('\0') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String (1.." + ANDROID_SENSORS_NAME_MAX_LEN
                    + " UTF-16 code units, no NUL/CR/LF)");
        }
        return value;
    }

    private static boolean isAllowedAndroidSensorsKey(String key) {
        for (String allowed : ANDROID_SENSORS_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse-time rules for optional {@code android.clipboard} when present (v1 subset).
     * Missing node leaves {@link #isAndroidClipboardConfigured()} false; explicit empty object is
     * configured with default {@code hasPrimaryClip=false} and no {@code primaryText}/
     * {@code primaryLabel}/{@code timestampMillis}.
     * When {@code primaryText} is present it must be a JSON String (empty allowed, null rejected)
     * and {@code hasPrimaryClip} must be explicitly {@code true}.
     * When {@code primaryLabel} is present it must be a JSON String (empty allowed, null rejected)
     * and {@code primaryText} must be explicitly configured (label-only is rejected).
     * When {@code timestampMillis} is present it must be an exact integral JSON Number in
     * {@code 0..Long.MAX_VALUE} and {@code primaryText} must be explicitly configured
     * (timestamp-only is rejected). Omitted {@code timestampMillis} is valid.
     * Materializes {@link AndroidClipboardConfig}; never retains JSONObject.
     */
    private void validateAndroidClipboard() {
        JSONObject android = android();
        if (android == null || !android.containsKey("clipboard")) {
            this.androidClipboardConfigured = false;
            this.androidClipboardConfig = null;
            return;
        }
        Object raw = android.get("clipboard");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.clipboard must be a JSONObject");
        }
        JSONObject clipboard = (JSONObject) raw;
        for (String key : clipboard.keySet()) {
            if (!isAllowedAndroidClipboardKey(key)) {
                throw new IllegalArgumentException("android.clipboard." + key
                        + " is not an allowed key (hasPrimaryClip|primaryText|primaryLabel|timestampMillis)");
            }
        }

        boolean hasPrimaryClipConfigured = clipboard.containsKey("hasPrimaryClip");
        boolean hasPrimaryClip = ANDROID_CLIPBOARD_DEFAULT_HAS_PRIMARY_CLIP;
        if (hasPrimaryClipConfigured) {
            hasPrimaryClip = requireAndroidPowerBoolean(clipboard.get("hasPrimaryClip"),
                    "android.clipboard.hasPrimaryClip");
        }

        boolean primaryTextConfigured = clipboard.containsKey("primaryText");
        String primaryText = null;
        if (primaryTextConfigured) {
            Object textRaw = clipboard.get("primaryText");
            if (!(textRaw instanceof String)) {
                throw new IllegalArgumentException(
                        "android.clipboard.primaryText must be a String");
            }
            primaryText = (String) textRaw;
            if (!hasPrimaryClipConfigured || !hasPrimaryClip) {
                throw new IllegalArgumentException(
                        "android.clipboard.primaryText requires hasPrimaryClip to be explicitly true");
            }
        }

        boolean primaryLabelConfigured = clipboard.containsKey("primaryLabel");
        String primaryLabel = null;
        if (primaryLabelConfigured) {
            Object labelRaw = clipboard.get("primaryLabel");
            if (!(labelRaw instanceof String)) {
                throw new IllegalArgumentException(
                        "android.clipboard.primaryLabel must be a String");
            }
            primaryLabel = (String) labelRaw;
            if (!primaryTextConfigured) {
                throw new IllegalArgumentException(
                        "android.clipboard.primaryLabel requires primaryText to be explicitly configured");
            }
        }

        boolean timestampMillisConfigured = clipboard.containsKey("timestampMillis");
        long timestampMillis = 0L;
        if (timestampMillisConfigured) {
            timestampMillis = requireExactJsonNumberLongField(clipboard, "timestampMillis",
                    "android.clipboard.timestampMillis", 0L, Long.MAX_VALUE);
            if (!primaryTextConfigured) {
                throw new IllegalArgumentException(
                        "android.clipboard.timestampMillis requires primaryText to be explicitly configured");
            }
        }

        this.androidClipboardConfigured = true;
        this.androidClipboardConfig = new AndroidClipboardConfig(
                hasPrimaryClip, hasPrimaryClipConfigured,
                primaryText, primaryTextConfigured,
                primaryLabel, primaryLabelConfigured,
                timestampMillis, timestampMillisConfigured);
    }

    private static boolean isAllowedAndroidClipboardKey(String key) {
        for (String allowed : ANDROID_CLIPBOARD_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse-time rules for optional {@code android.accessibility} when present (v1 subset).
     * Missing node leaves {@link #isAndroidAccessibilityConfigured()} false; explicit empty object
     * is configured with defaults {@code enabled=false}, {@code touchExplorationEnabled=false},
     * and {@code highContrastTextEnabled=false}; optional {@code services} absent means
     * service lists keep legacy JNI behavior.
     * Fields are independent (never cross-inferred) and not inferred from service lists.
     * Materializes {@link AndroidAccessibilityConfig}; never retains JSONObject.
     */
    private void validateAndroidAccessibility() {
        JSONObject android = android();
        if (android == null || !android.containsKey("accessibility")) {
            this.androidAccessibilityConfigured = false;
            this.androidAccessibilityConfig = null;
            return;
        }
        Object raw = android.get("accessibility");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.accessibility must be a JSONObject");
        }
        JSONObject accessibility = (JSONObject) raw;
        for (String key : accessibility.keySet()) {
            if (!isAllowedAndroidAccessibilityKey(key)) {
                throw new IllegalArgumentException("android.accessibility." + key
                        + " is not an allowed key (enabled, touchExplorationEnabled,"
                        + " highContrastTextEnabled, services)");
            }
        }

        boolean enabledConfigured = accessibility.containsKey("enabled");
        boolean enabled = ANDROID_ACCESSIBILITY_DEFAULT_ENABLED;
        if (enabledConfigured) {
            enabled = requireAndroidPowerBoolean(accessibility.get("enabled"),
                    "android.accessibility.enabled");
        }

        boolean touchExplorationEnabledConfigured =
                accessibility.containsKey("touchExplorationEnabled");
        boolean touchExplorationEnabled = ANDROID_ACCESSIBILITY_DEFAULT_TOUCH_EXPLORATION_ENABLED;
        if (touchExplorationEnabledConfigured) {
            touchExplorationEnabled = requireAndroidPowerBoolean(
                    accessibility.get("touchExplorationEnabled"),
                    "android.accessibility.touchExplorationEnabled");
        }

        boolean highContrastTextEnabledConfigured =
                accessibility.containsKey("highContrastTextEnabled");
        boolean highContrastTextEnabled =
                ANDROID_ACCESSIBILITY_DEFAULT_HIGH_CONTRAST_TEXT_ENABLED;
        if (highContrastTextEnabledConfigured) {
            highContrastTextEnabled = requireAndroidPowerBoolean(
                    accessibility.get("highContrastTextEnabled"),
                    "android.accessibility.highContrastTextEnabled");
        }

        boolean servicesConfigured = accessibility.containsKey("services");
        List<AndroidAccessibilityServiceConfig> services = Collections.emptyList();
        if (servicesConfigured) {
            Object servicesRaw = accessibility.get("services");
            if (!(servicesRaw instanceof JSONArray)) {
                throw new IllegalArgumentException(
                        "android.accessibility.services must be a JSONArray");
            }
            JSONArray array = (JSONArray) servicesRaw;
            List<AndroidAccessibilityServiceConfig> built =
                    new ArrayList<AndroidAccessibilityServiceConfig>(array.size());
            Set<String> seenIds = new HashSet<String>();
            for (int i = 0; i < array.size(); i++) {
                String pathPrefix = "android.accessibility.services[" + i + "]";
                Object item = array.get(i);
                if (!(item instanceof JSONObject)) {
                    throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
                }
                JSONObject svc = (JSONObject) item;
                for (String key : svc.keySet()) {
                    if (!"id".equals(key) && !"enabled".equals(key)) {
                        throw new IllegalArgumentException(pathPrefix + "." + key
                                + " is not an allowed key (id|enabled)");
                    }
                }
                if (!svc.containsKey("id")) {
                    throw new IllegalArgumentException(pathPrefix + ".id is required");
                }
                if (!svc.containsKey("enabled")) {
                    throw new IllegalArgumentException(pathPrefix + ".enabled is required");
                }
                String id = requireAndroidAccountNonEmptyString(svc.get("id"), pathPrefix + ".id");
                if (!seenIds.add(id)) {
                    throw new IllegalArgumentException(pathPrefix + ".id is not unique: " + id);
                }
                boolean svcEnabled = requireAndroidPowerBoolean(svc.get("enabled"),
                        pathPrefix + ".enabled");
                built.add(new AndroidAccessibilityServiceConfig(id, svcEnabled));
            }
            services = Collections.unmodifiableList(built);
        }

        this.androidAccessibilityConfigured = true;
        this.androidAccessibilityConfig = new AndroidAccessibilityConfig(
                enabled, enabledConfigured,
                touchExplorationEnabled, touchExplorationEnabledConfigured,
                highContrastTextEnabled, highContrastTextEnabledConfigured,
                services, servicesConfigured);
    }

    private static boolean isAllowedAndroidAccessibilityKey(String key) {
        for (String allowed : ANDROID_ACCESSIBILITY_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse-time rules for optional {@code android.audio} when present (v1 subset).
     * Missing node leaves {@link #isAndroidAudioConfigured()} false; explicit empty object is
     * configured with defaults {@code musicActive=false}, {@code speakerphoneOn=false},
     * {@code ringerMode=2} ({@code RINGER_MODE_NORMAL}), and {@code mode=0} ({@code MODE_NORMAL});
     * {@code properties} remains unconfigured (empty map).
     * Fields are independent (never cross-inferred).
     * Materializes {@link AndroidAudioConfig}; never retains JSONObject.
     */
    private void validateAndroidAudio() {
        JSONObject android = android();
        if (android == null || !android.containsKey("audio")) {
            this.androidAudioConfigured = false;
            this.androidAudioConfig = null;
            return;
        }
        Object raw = android.get("audio");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.audio must be a JSONObject");
        }
        JSONObject audio = (JSONObject) raw;
        for (String key : audio.keySet()) {
            if (!isAllowedAndroidAudioKey(key)) {
                throw new IllegalArgumentException("android.audio." + key
                        + " is not an allowed key (musicActive, speakerphoneOn, ringerMode, mode,"
                        + " properties, streamVolumes)");
            }
        }

        boolean musicActiveConfigured = audio.containsKey("musicActive");
        boolean musicActive = ANDROID_AUDIO_DEFAULT_MUSIC_ACTIVE;
        if (musicActiveConfigured) {
            musicActive = requireAndroidPowerBoolean(audio.get("musicActive"),
                    "android.audio.musicActive");
        }

        boolean speakerphoneOnConfigured = audio.containsKey("speakerphoneOn");
        boolean speakerphoneOn = ANDROID_AUDIO_DEFAULT_SPEAKERPHONE_ON;
        if (speakerphoneOnConfigured) {
            speakerphoneOn = requireAndroidPowerBoolean(audio.get("speakerphoneOn"),
                    "android.audio.speakerphoneOn");
        }

        boolean ringerModeConfigured = audio.containsKey("ringerMode");
        int ringerMode = ANDROID_AUDIO_DEFAULT_RINGER_MODE;
        if (ringerModeConfigured) {
            ringerMode = requireExactJsonNumberIntValue(audio.get("ringerMode"),
                    "android.audio.ringerMode",
                    ANDROID_AUDIO_RINGER_MODE_MIN, ANDROID_AUDIO_RINGER_MODE_MAX);
        }

        boolean modeConfigured = audio.containsKey("mode");
        int mode = ANDROID_AUDIO_DEFAULT_MODE;
        if (modeConfigured) {
            mode = requireExactJsonNumberIntValue(audio.get("mode"),
                    "android.audio.mode",
                    ANDROID_AUDIO_MODE_MIN, ANDROID_AUDIO_MODE_MAX);
        }

        boolean propertiesConfigured = audio.containsKey("properties");
        Map<String, String> properties = Collections.emptyMap();
        if (propertiesConfigured) {
            Object propertiesRaw = audio.get("properties");
            if (!(propertiesRaw instanceof JSONObject)) {
                throw new IllegalArgumentException(
                        "android.audio.properties must be a JSONObject");
            }
            JSONObject propertiesObj = (JSONObject) propertiesRaw;
            Map<String, String> built =
                    new LinkedHashMap<String, String>(propertiesObj.size());
            for (String propKey : propertiesObj.keySet()) {
                String propPath = "android.audio.properties." + propKey;
                if (propKey == null || propKey.isEmpty()) {
                    throw new IllegalArgumentException(propPath
                            + " key must be a non-empty String");
                }
                Object propValue = propertiesObj.get(propKey);
                if (propValue != null && !(propValue instanceof String)) {
                    throw new IllegalArgumentException(propPath
                            + " value must be a String or JSON null");
                }
                built.put(propKey, propValue == null ? null : (String) propValue);
            }
            properties = Collections.unmodifiableMap(built);
        }

        boolean streamVolumesConfigured = audio.containsKey("streamVolumes");
        List<AndroidStreamVolumeConfig> streamVolumes = Collections.emptyList();
        if (streamVolumesConfigured) {
            Object rawVolumes = audio.get("streamVolumes");
            if (!(rawVolumes instanceof JSONArray)) {
                throw new IllegalArgumentException(
                        "android.audio.streamVolumes must be a JSONArray");
            }
            JSONArray array = (JSONArray) rawVolumes;
            List<AndroidStreamVolumeConfig> built =
                    new ArrayList<AndroidStreamVolumeConfig>(array.size());
            Set<Integer> seenStreamTypes = new HashSet<Integer>();
            for (int i = 0; i < array.size(); i++) {
                String itemPath = "android.audio.streamVolumes[" + i + "]";
                Object item = array.get(i);
                if (!(item instanceof JSONObject)) {
                    throw new IllegalArgumentException(itemPath + " must be a JSONObject");
                }
                JSONObject entry = (JSONObject) item;
                for (String entryKey : entry.keySet()) {
                    if (!isAllowedAndroidAudioStreamVolumeEntryKey(entryKey)) {
                        throw new IllegalArgumentException(itemPath + "." + entryKey
                                + " is not an allowed key (streamType|volume|maxVolume|minVolume)");
                    }
                }
                if (!entry.containsKey("streamType")) {
                    throw new IllegalArgumentException(itemPath + ".streamType is required");
                }
                if (!entry.containsKey("volume")) {
                    throw new IllegalArgumentException(itemPath + ".volume is required");
                }
                if (!entry.containsKey("maxVolume")) {
                    throw new IllegalArgumentException(itemPath + ".maxVolume is required");
                }
                int streamType = requireExactJsonNumberIntValue(entry.get("streamType"),
                        itemPath + ".streamType", 0, Integer.MAX_VALUE);
                if (!seenStreamTypes.add(Integer.valueOf(streamType))) {
                    throw new IllegalArgumentException(itemPath
                            + ".streamType is not unique: " + streamType);
                }
                int volume = requireExactJsonNumberIntValue(entry.get("volume"),
                        itemPath + ".volume", 0, Integer.MAX_VALUE);
                int maxVolume = requireExactJsonNumberIntValue(entry.get("maxVolume"),
                        itemPath + ".maxVolume", 0, Integer.MAX_VALUE);
                if (volume > maxVolume) {
                    throw new IllegalArgumentException(itemPath
                            + ".volume must be <= maxVolume (" + volume + " > " + maxVolume + ")");
                }
                boolean minVolumeConfigured = entry.containsKey("minVolume");
                int minVolume = 0;
                if (minVolumeConfigured) {
                    minVolume = requireExactJsonNumberIntValue(entry.get("minVolume"),
                            itemPath + ".minVolume", 0, Integer.MAX_VALUE);
                    if (minVolume > volume) {
                        throw new IllegalArgumentException(itemPath
                                + ".minVolume must be <= volume (" + minVolume
                                + " > " + volume + ")");
                    }
                }
                built.add(new AndroidStreamVolumeConfig(streamType, volume, maxVolume,
                        minVolume, minVolumeConfigured));
            }
            streamVolumes = Collections.unmodifiableList(built);
        }

        this.androidAudioConfigured = true;
        this.androidAudioConfig = new AndroidAudioConfig(
                musicActive, musicActiveConfigured,
                speakerphoneOn, speakerphoneOnConfigured,
                ringerMode, ringerModeConfigured,
                mode, modeConfigured,
                properties, propertiesConfigured,
                streamVolumes, streamVolumesConfigured);
    }

    private static boolean isAllowedAndroidAudioKey(String key) {
        for (String allowed : ANDROID_AUDIO_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedAndroidAudioStreamVolumeEntryKey(String key) {
        for (String allowed : ANDROID_AUDIO_STREAM_VOLUME_ENTRY_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse-time rules for optional {@code android.location} / {@code android.location.providers}
     * / {@code android.location.lastKnownLocations} / {@code android.location.providerCapabilities}
     * when present.
     * Missing {@code android.location} leaves {@link #isAndroidLocationConfigured()} false,
     * {@link #isAndroidLocationProvidersConfigured()} false,
     * {@link #isAndroidLocationLastKnownLocationsConfigured()} false, and
     * {@link #isAndroidLocationProviderCapabilitiesConfigured()} false.
     * Explicit empty {@code android.location} is configured with {@code enabled=false}; missing
     * {@code providers} leaves providers unconfigured. Explicit empty {@code providers} uses
     * defaults {@code gps=false}, {@code network=false}, {@code passive=false}.
     * Missing {@code lastKnownLocations} leaves last-known unconfigured; explicit {@code []} is
     * configured empty. Missing {@code providerCapabilities} leaves capabilities unconfigured;
     * explicit empty object is configured with no entries. Each provider entry missing
     * {@code requiresNetwork}, {@code requiresSatellite}, {@code requiresCell},
     * {@code hasMonetaryCost}, {@code supportsAltitude}, {@code supportsSpeed},
     * {@code supportsBearing}, {@code meetsCriteria}, {@code accuracy}, or
     * {@code powerRequirement} leaves
     * that field unconfigured independently.
     * {@code enabled}/{@code providers}/{@code lastKnownLocations}/{@code providerCapabilities}
     * are independent (never cross-inferred from each other or from time config).
     * Materializes {@link AndroidLocationConfig} / {@link AndroidLocationProvidersConfig} /
     * {@link AndroidLastKnownLocationConfig} list / {@link AndroidLocationProviderCapabilitiesConfig};
     * never retains JSONObject/JSONArray.
     */
    private void validateAndroidLocation() {
        JSONObject android = android();
        if (android == null || !android.containsKey("location")) {
            this.androidLocationConfigured = false;
            this.androidLocationConfig = null;
            this.androidLocationProvidersConfigured = false;
            this.androidLocationProvidersConfig = null;
            this.androidLocationLastKnownLocationsConfigured = false;
            this.androidLocationLastKnownLocations = Collections.emptyList();
            this.androidLocationProviderCapabilitiesConfigured = false;
            this.androidLocationProviderCapabilitiesConfig = null;
            return;
        }
        Object rawLocation = android.get("location");
        if (!(rawLocation instanceof JSONObject)) {
            throw new IllegalArgumentException("android.location must be a JSONObject");
        }
        JSONObject location = (JSONObject) rawLocation;
        for (String key : location.keySet()) {
            if (!isAllowedAndroidLocationKey(key)) {
                throw new IllegalArgumentException("android.location." + key
                        + " is not an allowed key (enabled|providers|lastKnownLocations|providerCapabilities)");
            }
        }

        boolean enabledConfigured = location.containsKey("enabled");
        boolean enabled = ANDROID_LOCATION_DEFAULT_ENABLED;
        if (enabledConfigured) {
            enabled = requireAndroidPowerBoolean(location.get("enabled"),
                    "android.location.enabled");
        }
        this.androidLocationConfigured = true;
        this.androidLocationConfig = new AndroidLocationConfig(enabled, enabledConfigured);

        if (!location.containsKey("providers")) {
            this.androidLocationProvidersConfigured = false;
            this.androidLocationProvidersConfig = null;
        } else {
            Object rawProviders = location.get("providers");
            if (!(rawProviders instanceof JSONObject)) {
                throw new IllegalArgumentException("android.location.providers must be a JSONObject");
            }
            JSONObject providers = (JSONObject) rawProviders;
            for (String key : providers.keySet()) {
                if (!isAllowedAndroidLocationProvidersKey(key)) {
                    throw new IllegalArgumentException("android.location.providers." + key
                            + " is not an allowed key (gps|network|passive)");
                }
            }

            boolean gpsConfigured = providers.containsKey("gps");
            boolean gps = ANDROID_LOCATION_PROVIDERS_DEFAULT_GPS;
            if (gpsConfigured) {
                gps = requireAndroidPowerBoolean(providers.get("gps"),
                        "android.location.providers.gps");
            }

            boolean networkConfigured = providers.containsKey("network");
            boolean network = ANDROID_LOCATION_PROVIDERS_DEFAULT_NETWORK;
            if (networkConfigured) {
                network = requireAndroidPowerBoolean(providers.get("network"),
                        "android.location.providers.network");
            }

            boolean passiveConfigured = providers.containsKey("passive");
            boolean passive = ANDROID_LOCATION_PROVIDERS_DEFAULT_PASSIVE;
            if (passiveConfigured) {
                passive = requireAndroidPowerBoolean(providers.get("passive"),
                        "android.location.providers.passive");
            }

            this.androidLocationProvidersConfigured = true;
            this.androidLocationProvidersConfig = new AndroidLocationProvidersConfig(
                    gps, gpsConfigured,
                    network, networkConfigured,
                    passive, passiveConfigured);
        }

        if (!location.containsKey("providerCapabilities")) {
            this.androidLocationProviderCapabilitiesConfigured = false;
            this.androidLocationProviderCapabilitiesConfig = null;
        } else {
            Object rawCapabilities = location.get("providerCapabilities");
            if (!(rawCapabilities instanceof JSONObject)) {
                throw new IllegalArgumentException(
                        "android.location.providerCapabilities must be a JSONObject");
            }
            JSONObject capabilities = (JSONObject) rawCapabilities;
            for (String key : capabilities.keySet()) {
                if (!isAllowedAndroidLocationProvidersKey(key)) {
                    throw new IllegalArgumentException("android.location.providerCapabilities." + key
                            + " is not an allowed key (gps|network|passive)");
                }
            }
            AndroidLocationProviderCapabilityEntry gpsEntry =
                    parseAndroidLocationProviderCapabilityEntry(capabilities, "gps");
            AndroidLocationProviderCapabilityEntry networkEntry =
                    parseAndroidLocationProviderCapabilityEntry(capabilities, "network");
            AndroidLocationProviderCapabilityEntry passiveEntry =
                    parseAndroidLocationProviderCapabilityEntry(capabilities, "passive");
            this.androidLocationProviderCapabilitiesConfigured = true;
            this.androidLocationProviderCapabilitiesConfig =
                    new AndroidLocationProviderCapabilitiesConfig(
                            gpsEntry, gpsEntry != null,
                            networkEntry, networkEntry != null,
                            passiveEntry, passiveEntry != null);
        }

        if (!location.containsKey("lastKnownLocations")) {
            this.androidLocationLastKnownLocationsConfigured = false;
            this.androidLocationLastKnownLocations = Collections.emptyList();
            return;
        }
        Object rawLastKnown = location.get("lastKnownLocations");
        if (!(rawLastKnown instanceof JSONArray)) {
            throw new IllegalArgumentException(
                    "android.location.lastKnownLocations must be a JSONArray");
        }
        JSONArray array = (JSONArray) rawLastKnown;
        List<AndroidLastKnownLocationConfig> built =
                new ArrayList<AndroidLastKnownLocationConfig>(array.size());
        Set<String> seenProviders = new HashSet<String>();
        for (int i = 0; i < array.size(); i++) {
            String itemPath = "android.location.lastKnownLocations[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(itemPath + " must be a JSONObject");
            }
            JSONObject entry = (JSONObject) item;
            for (String key : entry.keySet()) {
                if (!isAllowedAndroidLocationLastKnownEntryKey(key)) {
                    throw new IllegalArgumentException(itemPath + "." + key
                            + " is not an allowed key (provider|latitude|longitude|altitude|"
                            + "accuracyMeters|timeMillis|elapsedRealtimeNanos|mock|"
                            + "speedMetersPerSecond|bearingDegrees|verticalAccuracyMeters|"
                            + "speedAccuracyMetersPerSecond|bearingAccuracyDegrees)");
                }
            }
            if (!entry.containsKey("provider")) {
                throw new IllegalArgumentException(itemPath + ".provider is required");
            }
            if (!entry.containsKey("latitude")) {
                throw new IllegalArgumentException(itemPath + ".latitude is required");
            }
            if (!entry.containsKey("longitude")) {
                throw new IllegalArgumentException(itemPath + ".longitude is required");
            }
            String provider = requireAndroidLocationLastKnownProvider(
                    entry.get("provider"), itemPath + ".provider");
            if (!seenProviders.add(provider)) {
                throw new IllegalArgumentException(itemPath + ".provider is not unique: "
                        + provider);
            }
            double latitude = requireExactJsonNumberFiniteDouble(entry.get("latitude"),
                    itemPath + ".latitude",
                    ANDROID_LOCATION_LATITUDE_MIN, ANDROID_LOCATION_LATITUDE_MAX);
            double longitude = requireExactJsonNumberFiniteDouble(entry.get("longitude"),
                    itemPath + ".longitude",
                    ANDROID_LOCATION_LONGITUDE_MIN, ANDROID_LOCATION_LONGITUDE_MAX);

            boolean altitudeConfigured = entry.containsKey("altitude");
            double altitude = ANDROID_LOCATION_DEFAULT_ALTITUDE;
            if (altitudeConfigured) {
                altitude = requireExactJsonNumberFiniteDouble(entry.get("altitude"),
                        itemPath + ".altitude");
            }

            boolean accuracyMetersConfigured = entry.containsKey("accuracyMeters");
            float accuracyMeters = ANDROID_LOCATION_DEFAULT_ACCURACY_METERS;
            if (accuracyMetersConfigured) {
                accuracyMeters = requireExactJsonNumberFiniteNonNegativeFloat(
                        entry.get("accuracyMeters"), itemPath + ".accuracyMeters");
            }

            boolean timeMillisConfigured = entry.containsKey("timeMillis");
            long timeMillis = ANDROID_LOCATION_DEFAULT_TIME_MILLIS;
            if (timeMillisConfigured) {
                timeMillis = requireExactJsonNumberLongValue(entry.get("timeMillis"),
                        itemPath + ".timeMillis", 0L, Long.MAX_VALUE);
            }

            boolean elapsedRealtimeNanosConfigured = entry.containsKey("elapsedRealtimeNanos");
            long elapsedRealtimeNanos = ANDROID_LOCATION_DEFAULT_ELAPSED_REALTIME_NANOS;
            if (elapsedRealtimeNanosConfigured) {
                elapsedRealtimeNanos = requireExactJsonNumberLongValue(
                        entry.get("elapsedRealtimeNanos"),
                        itemPath + ".elapsedRealtimeNanos", 0L, Long.MAX_VALUE);
            }

            boolean mockConfigured = entry.containsKey("mock");
            boolean mock = ANDROID_LOCATION_DEFAULT_MOCK;
            if (mockConfigured) {
                mock = requireAndroidPowerBoolean(entry.get("mock"), itemPath + ".mock");
            }

            boolean speedMetersPerSecondConfigured = entry.containsKey("speedMetersPerSecond");
            float speedMetersPerSecond = ANDROID_LOCATION_DEFAULT_SPEED_METERS_PER_SECOND;
            if (speedMetersPerSecondConfigured) {
                speedMetersPerSecond = requireExactJsonNumberFiniteNonNegativeFloat(
                        entry.get("speedMetersPerSecond"),
                        itemPath + ".speedMetersPerSecond");
            }

            boolean bearingDegreesConfigured = entry.containsKey("bearingDegrees");
            float bearingDegrees = ANDROID_LOCATION_DEFAULT_BEARING_DEGREES;
            if (bearingDegreesConfigured) {
                bearingDegrees = requireExactJsonNumberFiniteFloatHalfOpen(
                        entry.get("bearingDegrees"),
                        itemPath + ".bearingDegrees",
                        ANDROID_LOCATION_BEARING_MIN_INCLUSIVE,
                        ANDROID_LOCATION_BEARING_MAX_EXCLUSIVE);
            }

            boolean verticalAccuracyMetersConfigured =
                    entry.containsKey("verticalAccuracyMeters");
            float verticalAccuracyMeters = ANDROID_LOCATION_DEFAULT_VERTICAL_ACCURACY_METERS;
            if (verticalAccuracyMetersConfigured) {
                verticalAccuracyMeters = requireExactJsonNumberFiniteNonNegativeFloat(
                        entry.get("verticalAccuracyMeters"),
                        itemPath + ".verticalAccuracyMeters");
            }

            boolean speedAccuracyMetersPerSecondConfigured =
                    entry.containsKey("speedAccuracyMetersPerSecond");
            float speedAccuracyMetersPerSecond =
                    ANDROID_LOCATION_DEFAULT_SPEED_ACCURACY_METERS_PER_SECOND;
            if (speedAccuracyMetersPerSecondConfigured) {
                speedAccuracyMetersPerSecond = requireExactJsonNumberFiniteNonNegativeFloat(
                        entry.get("speedAccuracyMetersPerSecond"),
                        itemPath + ".speedAccuracyMetersPerSecond");
            }

            boolean bearingAccuracyDegreesConfigured =
                    entry.containsKey("bearingAccuracyDegrees");
            float bearingAccuracyDegrees = ANDROID_LOCATION_DEFAULT_BEARING_ACCURACY_DEGREES;
            if (bearingAccuracyDegreesConfigured) {
                bearingAccuracyDegrees = requireExactJsonNumberFiniteNonNegativeFloat(
                        entry.get("bearingAccuracyDegrees"),
                        itemPath + ".bearingAccuracyDegrees");
            }

            built.add(new AndroidLastKnownLocationConfig(
                    provider,
                    latitude,
                    longitude,
                    altitude,
                    altitudeConfigured,
                    accuracyMeters,
                    accuracyMetersConfigured,
                    timeMillis,
                    timeMillisConfigured,
                    elapsedRealtimeNanos,
                    elapsedRealtimeNanosConfigured,
                    mock,
                    mockConfigured,
                    speedMetersPerSecond,
                    speedMetersPerSecondConfigured,
                    bearingDegrees,
                    bearingDegreesConfigured,
                    verticalAccuracyMeters,
                    verticalAccuracyMetersConfigured,
                    speedAccuracyMetersPerSecond,
                    speedAccuracyMetersPerSecondConfigured,
                    bearingAccuracyDegrees,
                    bearingAccuracyDegreesConfigured));
        }
        this.androidLocationLastKnownLocationsConfigured = true;
        this.androidLocationLastKnownLocations = Collections.unmodifiableList(built);
    }

    private static boolean isAllowedAndroidLocationKey(String key) {
        for (String allowed : ANDROID_LOCATION_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedAndroidLocationProvidersKey(String key) {
        for (String allowed : ANDROID_LOCATION_PROVIDERS_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedAndroidLocationLastKnownEntryKey(String key) {
        for (String allowed : ANDROID_LOCATION_LAST_KNOWN_ENTRY_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedAndroidLocationProviderCapabilityEntryKey(String key) {
        for (String allowed : ANDROID_LOCATION_PROVIDER_CAPABILITY_ENTRY_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse one {@code android.location.providerCapabilities.<provider>} object, or {@code null}
     * when the provider key is omitted. Empty object is a present entry with unconfigured fields.
     */
    private static AndroidLocationProviderCapabilityEntry parseAndroidLocationProviderCapabilityEntry(
            JSONObject capabilities, String provider) {
        if (!capabilities.containsKey(provider)) {
            return null;
        }
        String path = "android.location.providerCapabilities." + provider;
        Object raw = capabilities.get(provider);
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException(path + " must be a JSONObject");
        }
        JSONObject entry = (JSONObject) raw;
        for (String key : entry.keySet()) {
            if (!isAllowedAndroidLocationProviderCapabilityEntryKey(key)) {
                throw new IllegalArgumentException(path + "." + key
                        + " is not an allowed key (requiresNetwork|requiresSatellite|requiresCell|hasMonetaryCost|supportsAltitude|supportsSpeed|supportsBearing|meetsCriteria|accuracy|powerRequirement)");
            }
        }
        boolean requiresNetworkConfigured = entry.containsKey("requiresNetwork");
        boolean requiresNetwork = ANDROID_LOCATION_DEFAULT_REQUIRES_NETWORK;
        if (requiresNetworkConfigured) {
            requiresNetwork = requireAndroidPowerBoolean(entry.get("requiresNetwork"),
                    path + ".requiresNetwork");
        }
        boolean requiresSatelliteConfigured = entry.containsKey("requiresSatellite");
        boolean requiresSatellite = ANDROID_LOCATION_DEFAULT_REQUIRES_SATELLITE;
        if (requiresSatelliteConfigured) {
            requiresSatellite = requireAndroidPowerBoolean(entry.get("requiresSatellite"),
                    path + ".requiresSatellite");
        }
        boolean requiresCellConfigured = entry.containsKey("requiresCell");
        boolean requiresCell = ANDROID_LOCATION_DEFAULT_REQUIRES_CELL;
        if (requiresCellConfigured) {
            requiresCell = requireAndroidPowerBoolean(entry.get("requiresCell"),
                    path + ".requiresCell");
        }
        boolean hasMonetaryCostConfigured = entry.containsKey("hasMonetaryCost");
        boolean hasMonetaryCost = ANDROID_LOCATION_DEFAULT_HAS_MONETARY_COST;
        if (hasMonetaryCostConfigured) {
            hasMonetaryCost = requireAndroidPowerBoolean(entry.get("hasMonetaryCost"),
                    path + ".hasMonetaryCost");
        }
        boolean supportsAltitudeConfigured = entry.containsKey("supportsAltitude");
        boolean supportsAltitude = ANDROID_LOCATION_DEFAULT_SUPPORTS_ALTITUDE;
        if (supportsAltitudeConfigured) {
            supportsAltitude = requireAndroidPowerBoolean(entry.get("supportsAltitude"),
                    path + ".supportsAltitude");
        }
        boolean supportsSpeedConfigured = entry.containsKey("supportsSpeed");
        boolean supportsSpeed = ANDROID_LOCATION_DEFAULT_SUPPORTS_SPEED;
        if (supportsSpeedConfigured) {
            supportsSpeed = requireAndroidPowerBoolean(entry.get("supportsSpeed"),
                    path + ".supportsSpeed");
        }
        boolean supportsBearingConfigured = entry.containsKey("supportsBearing");
        boolean supportsBearing = ANDROID_LOCATION_DEFAULT_SUPPORTS_BEARING;
        if (supportsBearingConfigured) {
            supportsBearing = requireAndroidPowerBoolean(entry.get("supportsBearing"),
                    path + ".supportsBearing");
        }
        boolean meetsCriteriaConfigured = entry.containsKey("meetsCriteria");
        boolean meetsCriteria = ANDROID_LOCATION_DEFAULT_MEETS_CRITERIA;
        if (meetsCriteriaConfigured) {
            meetsCriteria = requireAndroidPowerBoolean(entry.get("meetsCriteria"),
                    path + ".meetsCriteria");
        }
        boolean accuracyConfigured = entry.containsKey("accuracy");
        int accuracy = ANDROID_LOCATION_DEFAULT_ACCURACY;
        if (accuracyConfigured) {
            accuracy = requireAndroidLocationProviderAccuracy(entry.get("accuracy"),
                    path + ".accuracy");
        }
        boolean powerRequirementConfigured = entry.containsKey("powerRequirement");
        int powerRequirement = ANDROID_LOCATION_DEFAULT_POWER_REQUIREMENT;
        if (powerRequirementConfigured) {
            powerRequirement = requireAndroidLocationProviderPowerRequirement(
                    entry.get("powerRequirement"), path + ".powerRequirement");
        }
        return new AndroidLocationProviderCapabilityEntry(
                requiresNetwork, requiresNetworkConfigured,
                requiresSatellite, requiresSatelliteConfigured,
                requiresCell, requiresCellConfigured,
                hasMonetaryCost, hasMonetaryCostConfigured,
                supportsAltitude, supportsAltitudeConfigured,
                supportsSpeed, supportsSpeedConfigured,
                supportsBearing, supportsBearingConfigured,
                meetsCriteria, meetsCriteriaConfigured,
                accuracy, accuracyConfigured,
                powerRequirement, powerRequirementConfigured);
    }

    /**
     * Exact JSON Number integer: only {@code ProviderProperties.ACCURACY_FINE=1} or
     * {@code ACCURACY_COARSE=2}. Rejects null, Boolean, String, fractions, NaN/Infinity,
     * {@code 0}, {@code 3}, and other values. Distinct from last-known
     * {@code accuracyMeters} / {@code Location.getAccuracy()F}.
     */
    private static int requireAndroidLocationProviderAccuracy(Object raw, String path) {
        final String requirement = path + " must be an exact JSON Number integer 1 or 2";
        if (!(raw instanceof Number) || raw instanceof Boolean) {
            throw new IllegalArgumentException(requirement);
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(requirement);
            }
        } else if (raw instanceof BigDecimal) {
            try {
                value = ((BigDecimal) raw).toBigIntegerExact().longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(requirement);
            }
        } else if (raw instanceof Float || raw instanceof Double) {
            double d = ((Number) raw).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d)) {
                throw new IllegalArgumentException(requirement);
            }
            value = (long) d;
        } else {
            value = ((Number) raw).longValue();
        }
        if (value != ANDROID_LOCATION_PROVIDER_ACCURACY_FINE
                && value != ANDROID_LOCATION_PROVIDER_ACCURACY_COARSE) {
            throw new IllegalArgumentException(requirement);
        }
        return (int) value;
    }

    /**
     * Exact JSON Number integer: only {@code ProviderProperties.POWER_USAGE_LOW=1},
     * {@code POWER_USAGE_MEDIUM=2}, or {@code POWER_USAGE_HIGH=3}. Rejects null, Boolean,
     * String, fractions, NaN/Infinity, {@code 0}, {@code 4}, and other values. Independent
     * of {@code accuracy} / {@code LocationProvider.getAccuracy()I}.
     */
    private static int requireAndroidLocationProviderPowerRequirement(Object raw, String path) {
        final String requirement = path + " must be an exact JSON Number integer 1, 2, or 3";
        if (!(raw instanceof Number) || raw instanceof Boolean) {
            throw new IllegalArgumentException(requirement);
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(requirement);
            }
        } else if (raw instanceof BigDecimal) {
            try {
                value = ((BigDecimal) raw).toBigIntegerExact().longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(requirement);
            }
        } else if (raw instanceof Float || raw instanceof Double) {
            double d = ((Number) raw).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d)) {
                throw new IllegalArgumentException(requirement);
            }
            value = (long) d;
        } else {
            value = ((Number) raw).longValue();
        }
        if (value != ANDROID_LOCATION_PROVIDER_POWER_USAGE_LOW
                && value != ANDROID_LOCATION_PROVIDER_POWER_USAGE_MEDIUM
                && value != ANDROID_LOCATION_PROVIDER_POWER_USAGE_HIGH) {
            throw new IllegalArgumentException(requirement);
        }
        return (int) value;
    }

    /**
     * Nonempty provider String without NUL/CR/LF for last-known location entries.
     */
    private static String requireAndroidLocationLastKnownProvider(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without NUL/CR/LF");
        }
        String value = (String) raw;
        if (value.isEmpty()
                || value.indexOf('\0') >= 0
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without NUL/CR/LF");
        }
        return value;
    }

    /**
     * Finite exact JSON Number (not String/Boolean/null/NaN/Infinity) with no range clamp.
     */
    private static double requireExactJsonNumberFiniteDouble(Object raw, String path) {
        if (!(raw instanceof Number) || raw instanceof Boolean) {
            throw new IllegalArgumentException(path
                    + " must be a finite exact JSON Number");
        }
        double value = ((Number) raw).doubleValue();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(path
                    + " must be a finite exact JSON Number");
        }
        return value;
    }

    /**
     * Finite exact JSON Number in {@code [min, max]} inclusive.
     */
    private static double requireExactJsonNumberFiniteDouble(Object raw, String path,
                                                            double min, double max) {
        double value = requireExactJsonNumberFiniteDouble(raw, path);
        if (value < min || value > max) {
            throw new IllegalArgumentException(path
                    + " must be a finite exact JSON Number in range " + min + ".." + max);
        }
        return value;
    }

    /**
     * Nonnegative finite exact JSON Number as float (for accuracy meters).
     */
    private static float requireExactJsonNumberFiniteNonNegativeFloat(Object raw, String path) {
        if (!(raw instanceof Number) || raw instanceof Boolean) {
            throw new IllegalArgumentException(path
                    + " must be a finite exact JSON Number >= 0");
        }
        float value = ((Number) raw).floatValue();
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0f) {
            throw new IllegalArgumentException(path
                    + " must be a finite exact JSON Number >= 0");
        }
        return value;
    }

    /**
     * Finite exact JSON Number as float in half-open range {@code [minInclusive, maxExclusive)}.
     * Used for bearing degrees ({@code [0, 360)}).
     */
    private static float requireExactJsonNumberFiniteFloatHalfOpen(Object raw, String path,
                                                                   float minInclusive,
                                                                   float maxExclusive) {
        if (!(raw instanceof Number) || raw instanceof Boolean) {
            throw new IllegalArgumentException(path
                    + " must be a finite exact JSON Number in range [" + minInclusive
                    + ", " + maxExclusive + ")");
        }
        float value = ((Number) raw).floatValue();
        if (Float.isNaN(value) || Float.isInfinite(value)
                || value < minInclusive || value >= maxExclusive) {
            throw new IllegalArgumentException(path
                    + " must be a finite exact JSON Number in range [" + minInclusive
                    + ", " + maxExclusive + ")");
        }
        return value;
    }

    /**
     * Parse-time rules for optional {@code android.accounts} when present (v1 AccountManager subset).
     * Missing node leaves {@link #isAndroidAccountsConfigured()} false; explicit {@code []} is
     * configured with an empty list. Each element is a JSONObject with exactly nonempty String
     * {@code name} and {@code type}. Order preserved. Materializes {@link AndroidAccountConfig}
     * list; never retains JSONObject/JSONArray.
     */
    private void validateAndroidAccounts() {
        JSONObject android = android();
        if (android == null || !android.containsKey("accounts")) {
            this.androidAccountsConfigured = false;
            this.androidAccounts = Collections.emptyList();
            return;
        }
        Object raw = android.get("accounts");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("android.accounts must be a JSONArray");
        }
        JSONArray array = (JSONArray) raw;
        List<AndroidAccountConfig> built = new ArrayList<AndroidAccountConfig>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String pathPrefix = "android.accounts[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject account = (JSONObject) item;
            for (String key : account.keySet()) {
                if (!"name".equals(key) && !"type".equals(key)) {
                    throw new IllegalArgumentException(pathPrefix + "." + key
                            + " is not an allowed key (name|type)");
                }
            }
            if (!account.containsKey("name")) {
                throw new IllegalArgumentException(pathPrefix + ".name is required");
            }
            if (!account.containsKey("type")) {
                throw new IllegalArgumentException(pathPrefix + ".type is required");
            }
            String name = requireAndroidAccountNonEmptyString(account.get("name"),
                    pathPrefix + ".name");
            String type = requireAndroidAccountNonEmptyString(account.get("type"),
                    pathPrefix + ".type");
            built.add(new AndroidAccountConfig(name, type));
        }
        this.androidAccountsConfigured = true;
        this.androidAccounts = Collections.unmodifiableList(built);
    }

    private static String requireAndroidAccountNonEmptyString(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path + " must be a nonempty String");
        }
        String value = (String) raw;
        if (value.isEmpty()) {
            throw new IllegalArgumentException(path + " must be a nonempty String");
        }
        return value;
    }

    /**
     * Parse-time rules for optional {@code android.inputMethods} when present (v1 IME list subset).
     * Missing node leaves {@link #isAndroidInputMethodsConfigured()} false; explicit {@code []} is
     * configured with an empty list. Each element is a JSONObject with exactly nonempty String
     * {@code id} and Boolean {@code enabled}. Order preserved. Materializes
     * {@link AndroidInputMethodConfig} list; never retains JSONObject/JSONArray.
     */
    private void validateAndroidInputMethods() {
        JSONObject android = android();
        if (android == null || !android.containsKey("inputMethods")) {
            this.androidInputMethodsConfigured = false;
            this.androidInputMethods = Collections.emptyList();
            return;
        }
        Object raw = android.get("inputMethods");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("android.inputMethods must be a JSONArray");
        }
        JSONArray array = (JSONArray) raw;
        List<AndroidInputMethodConfig> built =
                new ArrayList<AndroidInputMethodConfig>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String pathPrefix = "android.inputMethods[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject ime = (JSONObject) item;
            for (String key : ime.keySet()) {
                if (!"id".equals(key) && !"enabled".equals(key)) {
                    throw new IllegalArgumentException(pathPrefix + "." + key
                            + " is not an allowed key (id|enabled)");
                }
            }
            if (!ime.containsKey("id")) {
                throw new IllegalArgumentException(pathPrefix + ".id is required");
            }
            if (!ime.containsKey("enabled")) {
                throw new IllegalArgumentException(pathPrefix + ".enabled is required");
            }
            String id = requireAndroidAccountNonEmptyString(ime.get("id"), pathPrefix + ".id");
            boolean enabled = requireAndroidPowerBoolean(ime.get("enabled"), pathPrefix + ".enabled");
            built.add(new AndroidInputMethodConfig(id, enabled));
        }
        this.androidInputMethodsConfigured = true;
        this.androidInputMethods = Collections.unmodifiableList(built);
    }

    /**
     * Parse-time rules for optional {@code android.identifiers} when present (advertising + androidId
     * + optional App Set ID). Missing node leaves {@link #isAndroidIdentifiersConfigured()} false;
     * explicit empty object is configured with defaults
     * {@code advertisingId=00000000-0000-0000-0000-00000000a001}, {@code limitAdTracking=false};
     * {@code androidId} and {@code appSetId} stay unconfigured (no default, no derivation).
     * {@code appSetScope} is allowed only when {@code appSetId} is present (default {@code 1}).
     * Materializes {@link AndroidIdentifiersConfig}; never retains JSONObject.
     */
    private void validateAndroidIdentifiers() {
        JSONObject android = android();
        if (android == null || !android.containsKey("identifiers")) {
            this.androidIdentifiersConfigured = false;
            this.androidIdentifiersConfig = null;
            return;
        }
        Object raw = android.get("identifiers");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.identifiers must be a JSONObject");
        }
        JSONObject identifiers = (JSONObject) raw;
        for (String key : identifiers.keySet()) {
            if (!isAllowedAndroidIdentifiersKey(key)) {
                throw new IllegalArgumentException("android.identifiers." + key
                        + " is not an allowed key (advertisingId|limitAdTracking|androidId|appSetId|appSetScope)");
            }
        }

        boolean advertisingIdConfigured = identifiers.containsKey("advertisingId");
        String advertisingId = ANDROID_IDENTIFIERS_DEFAULT_ADVERTISING_ID;
        if (advertisingIdConfigured) {
            advertisingId = requireAndroidIdentifiersAdvertisingId(identifiers.get("advertisingId"),
                    "android.identifiers.advertisingId");
        }

        boolean limitAdTrackingConfigured = identifiers.containsKey("limitAdTracking");
        boolean limitAdTracking = ANDROID_IDENTIFIERS_DEFAULT_LIMIT_AD_TRACKING;
        if (limitAdTrackingConfigured) {
            limitAdTracking = requireAndroidPowerBoolean(identifiers.get("limitAdTracking"),
                    "android.identifiers.limitAdTracking");
        }

        boolean androidIdConfigured = identifiers.containsKey("androidId");
        String androidId = null;
        if (androidIdConfigured) {
            androidId = requireAndroidIdentifiersAndroidId(identifiers.get("androidId"),
                    "android.identifiers.androidId");
        }

        boolean appSetIdConfigured = identifiers.containsKey("appSetId");
        boolean appSetScopeConfigured = identifiers.containsKey("appSetScope");
        if (appSetScopeConfigured && !appSetIdConfigured) {
            throw new IllegalArgumentException("android.identifiers.appSetScope"
                    + " requires android.identifiers.appSetId");
        }
        String appSetId = null;
        int appSetScope = ANDROID_IDENTIFIERS_DEFAULT_APP_SET_SCOPE;
        if (appSetIdConfigured) {
            appSetId = requireAndroidIdentifiersAppSetId(identifiers.get("appSetId"),
                    "android.identifiers.appSetId");
            if (appSetScopeConfigured) {
                appSetScope = requireAndroidIdentifiersAppSetScope(identifiers.get("appSetScope"),
                        "android.identifiers.appSetScope");
            }
        }

        this.androidIdentifiersConfigured = true;
        this.androidIdentifiersConfig = new AndroidIdentifiersConfig(
                advertisingId, advertisingIdConfigured,
                limitAdTracking, limitAdTrackingConfigured,
                androidId, androidIdConfigured,
                appSetId, appSetIdConfigured,
                appSetScope);
    }

    private static boolean isAllowedAndroidIdentifiersKey(String key) {
        for (String allowed : ANDROID_IDENTIFIERS_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse-time rules for optional {@code android.userState} when present (v1 subset).
     * Missing node leaves {@link #isAndroidUserStateConfigured()} false; explicit empty object is
     * configured with defaults userId=0, serialNumber=0, userUnlocked=true, systemUser=true,
     * managedProfile=false, demoUser=false. Materializes {@link AndroidUserStateConfig}; never
     * retains JSONObject.
     */
    private void validateAndroidUserState() {
        JSONObject android = android();
        if (android == null || !android.containsKey("userState")) {
            this.androidUserStateConfigured = false;
            this.androidUserStateConfig = null;
            return;
        }
        Object raw = android.get("userState");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.userState must be a JSONObject");
        }
        JSONObject userState = (JSONObject) raw;
        for (String key : userState.keySet()) {
            if (!isAllowedAndroidUserStateKey(key)) {
                throw new IllegalArgumentException("android.userState." + key
                        + " is not an allowed key (userId|serialNumber|userUnlocked|systemUser|"
                        + "managedProfile|demoUser)");
            }
        }

        boolean userIdConfigured = userState.containsKey("userId");
        int userId = ANDROID_USER_STATE_DEFAULT_USER_ID;
        if (userIdConfigured) {
            userId = requireExactJsonNumberIntField(userState, "userId",
                    "android.userState.userId",
                    ANDROID_USER_STATE_USER_ID_MIN, ANDROID_USER_STATE_USER_ID_MAX);
        }

        boolean serialNumberConfigured = userState.containsKey("serialNumber");
        long serialNumber = ANDROID_USER_STATE_DEFAULT_SERIAL_NUMBER;
        if (serialNumberConfigured) {
            serialNumber = requireExactJsonNumberLongField(userState, "serialNumber",
                    "android.userState.serialNumber",
                    ANDROID_USER_STATE_SERIAL_NUMBER_MIN, Long.MAX_VALUE);
        }

        boolean userUnlockedConfigured = userState.containsKey("userUnlocked");
        boolean userUnlocked = ANDROID_USER_STATE_DEFAULT_USER_UNLOCKED;
        if (userUnlockedConfigured) {
            userUnlocked = requireAndroidPowerBoolean(userState.get("userUnlocked"),
                    "android.userState.userUnlocked");
        }

        boolean systemUserConfigured = userState.containsKey("systemUser");
        boolean systemUser = ANDROID_USER_STATE_DEFAULT_SYSTEM_USER;
        if (systemUserConfigured) {
            systemUser = requireAndroidPowerBoolean(userState.get("systemUser"),
                    "android.userState.systemUser");
        }

        boolean managedProfileConfigured = userState.containsKey("managedProfile");
        boolean managedProfile = ANDROID_USER_STATE_DEFAULT_MANAGED_PROFILE;
        if (managedProfileConfigured) {
            managedProfile = requireAndroidPowerBoolean(userState.get("managedProfile"),
                    "android.userState.managedProfile");
        }

        boolean demoUserConfigured = userState.containsKey("demoUser");
        boolean demoUser = ANDROID_USER_STATE_DEFAULT_DEMO_USER;
        if (demoUserConfigured) {
            demoUser = requireAndroidPowerBoolean(userState.get("demoUser"),
                    "android.userState.demoUser");
        }

        this.androidUserStateConfigured = true;
        this.androidUserStateConfig = new AndroidUserStateConfig(
                userId, userIdConfigured,
                serialNumber, serialNumberConfigured,
                userUnlocked, userUnlockedConfigured,
                systemUser, systemUserConfigured,
                managedProfile, managedProfileConfigured,
                demoUser, demoUserConfigured);
    }

    private static boolean isAllowedAndroidUserStateKey(String key) {
        for (String allowed : ANDROID_USER_STATE_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse-time rules for optional {@code android.securitySignals} when present (v1 subset).
     * Missing node leaves {@link #isAndroidSecuritySignalsConfigured()} false; explicit empty object
     * is configured with default {@code debuggerConnected=false}, {@code waitingForDebugger=false},
     * {@code debuggerTracing=false}, {@code selinuxEnabled=true}, {@code selinuxEnforced=true},
     * {@code userAMonkey=false}, and {@code userTestHarness=false}. Fields are independent
     * (never cross-inferred). Materializes {@link AndroidSecuritySignalsConfig}; never retains
     * JSONObject.
     */
    private void validateAndroidSecuritySignals() {
        JSONObject android = android();
        if (android == null || !android.containsKey("securitySignals")) {
            this.androidSecuritySignalsConfigured = false;
            this.androidSecuritySignalsConfig = null;
            return;
        }
        Object raw = android.get("securitySignals");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.securitySignals must be a JSONObject");
        }
        JSONObject securitySignals = (JSONObject) raw;
        for (String key : securitySignals.keySet()) {
            if (!isAllowedAndroidSecuritySignalsKey(key)) {
                throw new IllegalArgumentException("android.securitySignals." + key
                        + " is not an allowed key (debuggerConnected|waitingForDebugger|debuggerTracing|"
                        + "selinuxEnabled|selinuxEnforced|userAMonkey|userTestHarness)");
            }
        }

        boolean debuggerConnectedConfigured = securitySignals.containsKey("debuggerConnected");
        boolean debuggerConnected = ANDROID_SECURITY_SIGNALS_DEFAULT_DEBUGGER_CONNECTED;
        if (debuggerConnectedConfigured) {
            debuggerConnected = requireAndroidPowerBoolean(securitySignals.get("debuggerConnected"),
                    "android.securitySignals.debuggerConnected");
        }

        boolean waitingForDebuggerConfigured = securitySignals.containsKey("waitingForDebugger");
        boolean waitingForDebugger = ANDROID_SECURITY_SIGNALS_DEFAULT_WAITING_FOR_DEBUGGER;
        if (waitingForDebuggerConfigured) {
            waitingForDebugger = requireAndroidPowerBoolean(securitySignals.get("waitingForDebugger"),
                    "android.securitySignals.waitingForDebugger");
        }

        boolean debuggerTracingConfigured = securitySignals.containsKey("debuggerTracing");
        boolean debuggerTracing = ANDROID_SECURITY_SIGNALS_DEFAULT_DEBUGGER_TRACING;
        if (debuggerTracingConfigured) {
            debuggerTracing = requireAndroidPowerBoolean(securitySignals.get("debuggerTracing"),
                    "android.securitySignals.debuggerTracing");
        }

        boolean selinuxEnabledConfigured = securitySignals.containsKey("selinuxEnabled");
        boolean selinuxEnabled = ANDROID_SECURITY_SIGNALS_DEFAULT_SELINUX_ENABLED;
        if (selinuxEnabledConfigured) {
            selinuxEnabled = requireAndroidPowerBoolean(securitySignals.get("selinuxEnabled"),
                    "android.securitySignals.selinuxEnabled");
        }

        boolean selinuxEnforcedConfigured = securitySignals.containsKey("selinuxEnforced");
        boolean selinuxEnforced = ANDROID_SECURITY_SIGNALS_DEFAULT_SELINUX_ENFORCED;
        if (selinuxEnforcedConfigured) {
            selinuxEnforced = requireAndroidPowerBoolean(securitySignals.get("selinuxEnforced"),
                    "android.securitySignals.selinuxEnforced");
        }

        boolean userAMonkeyConfigured = securitySignals.containsKey("userAMonkey");
        boolean userAMonkey = ANDROID_SECURITY_SIGNALS_DEFAULT_USER_A_MONKEY;
        if (userAMonkeyConfigured) {
            userAMonkey = requireAndroidPowerBoolean(securitySignals.get("userAMonkey"),
                    "android.securitySignals.userAMonkey");
        }

        boolean userTestHarnessConfigured = securitySignals.containsKey("userTestHarness");
        boolean userTestHarness = ANDROID_SECURITY_SIGNALS_DEFAULT_USER_TEST_HARNESS;
        if (userTestHarnessConfigured) {
            userTestHarness = requireAndroidPowerBoolean(securitySignals.get("userTestHarness"),
                    "android.securitySignals.userTestHarness");
        }

        this.androidSecuritySignalsConfigured = true;
        this.androidSecuritySignalsConfig = new AndroidSecuritySignalsConfig(
                debuggerConnected, debuggerConnectedConfigured,
                waitingForDebugger, waitingForDebuggerConfigured,
                debuggerTracing, debuggerTracingConfigured,
                selinuxEnabled, selinuxEnabledConfigured,
                selinuxEnforced, selinuxEnforcedConfigured,
                userAMonkey, userAMonkeyConfigured,
                userTestHarness, userTestHarnessConfigured);
    }

    private static boolean isAllowedAndroidSecuritySignalsKey(String key) {
        for (String allowed : ANDROID_SECURITY_SIGNALS_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse-time rules for optional {@code android.securityState} when present (v1 subset).
     * Missing node leaves {@link #isAndroidSecurityStateConfigured()} false; explicit empty object
     * is configured with defaults {@code keyguardLocked=false}, {@code keyguardSecure=false},
     * {@code deviceLocked=false}, {@code deviceSecure=false},
     * {@code biometricCanAuthenticateResult=12} (NO_HARDWARE),
     * {@code biometricLastAuthenticationElapsedRealtimeMillis=-1} (NO_AUTHENTICATION).
     * Fields are independent (never cross-inferred). Materializes {@link AndroidSecurityStateConfig};
     * never retains JSONObject.
     */
    private void validateAndroidSecurityState() {
        JSONObject android = android();
        if (android == null || !android.containsKey("securityState")) {
            this.androidSecurityStateConfigured = false;
            this.androidSecurityStateConfig = null;
            return;
        }
        Object raw = android.get("securityState");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.securityState must be a JSONObject");
        }
        JSONObject securityState = (JSONObject) raw;
        for (String key : securityState.keySet()) {
            if (!isAllowedAndroidSecurityStateKey(key)) {
                throw new IllegalArgumentException("android.securityState." + key
                        + " is not an allowed key (keyguardLocked|keyguardSecure|"
                        + "deviceLocked|deviceSecure|biometricCanAuthenticateResult|"
                        + "biometricLastAuthenticationElapsedRealtimeMillis)");
            }
        }

        boolean keyguardLockedConfigured = securityState.containsKey("keyguardLocked");
        boolean keyguardLocked = ANDROID_SECURITY_STATE_DEFAULT_KEYGUARD_LOCKED;
        if (keyguardLockedConfigured) {
            keyguardLocked = requireAndroidPowerBoolean(securityState.get("keyguardLocked"),
                    "android.securityState.keyguardLocked");
        }

        boolean keyguardSecureConfigured = securityState.containsKey("keyguardSecure");
        boolean keyguardSecure = ANDROID_SECURITY_STATE_DEFAULT_KEYGUARD_SECURE;
        if (keyguardSecureConfigured) {
            keyguardSecure = requireAndroidPowerBoolean(securityState.get("keyguardSecure"),
                    "android.securityState.keyguardSecure");
        }

        boolean deviceLockedConfigured = securityState.containsKey("deviceLocked");
        boolean deviceLocked = ANDROID_SECURITY_STATE_DEFAULT_DEVICE_LOCKED;
        if (deviceLockedConfigured) {
            deviceLocked = requireAndroidPowerBoolean(securityState.get("deviceLocked"),
                    "android.securityState.deviceLocked");
        }

        boolean deviceSecureConfigured = securityState.containsKey("deviceSecure");
        boolean deviceSecure = ANDROID_SECURITY_STATE_DEFAULT_DEVICE_SECURE;
        if (deviceSecureConfigured) {
            deviceSecure = requireAndroidPowerBoolean(securityState.get("deviceSecure"),
                    "android.securityState.deviceSecure");
        }

        boolean biometricCanAuthenticateResultConfigured =
                securityState.containsKey("biometricCanAuthenticateResult");
        int biometricCanAuthenticateResult =
                ANDROID_SECURITY_STATE_DEFAULT_BIOMETRIC_CAN_AUTHENTICATE;
        if (biometricCanAuthenticateResultConfigured) {
            biometricCanAuthenticateResult = requireAndroidBiometricCanAuthenticateResult(
                    securityState.get("biometricCanAuthenticateResult"),
                    "android.securityState.biometricCanAuthenticateResult");
        }

        boolean biometricLastAuthConfigured =
                securityState.containsKey("biometricLastAuthenticationElapsedRealtimeMillis");
        long biometricLastAuthElapsed =
                ANDROID_SECURITY_STATE_DEFAULT_BIOMETRIC_LAST_AUTH_ELAPSED;
        if (biometricLastAuthConfigured) {
            biometricLastAuthElapsed = requireAndroidBiometricLastAuthenticationElapsedRealtimeMillis(
                    securityState.get("biometricLastAuthenticationElapsedRealtimeMillis"),
                    "android.securityState.biometricLastAuthenticationElapsedRealtimeMillis");
        }

        this.androidSecurityStateConfigured = true;
        this.androidSecurityStateConfig = new AndroidSecurityStateConfig(
                keyguardLocked, keyguardLockedConfigured,
                keyguardSecure, keyguardSecureConfigured,
                deviceLocked, deviceLockedConfigured,
                deviceSecure, deviceSecureConfigured,
                biometricCanAuthenticateResult, biometricCanAuthenticateResultConfigured,
                biometricLastAuthElapsed, biometricLastAuthConfigured);
    }

    /**
     * Exact JSON Number integer restricted to framework
     * {@code BiometricManager.canAuthenticate} result codes {0,1,11,12,15,20,21}.
     */
    private static int requireAndroidBiometricCanAuthenticateResult(Object raw, String path) {
        if (!(raw instanceof Number)) {
            throw new IllegalArgumentException(path
                    + " must be an exact JSON Number integer in {0,1,11,12,15,20,21}");
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(path
                        + " must be an exact JSON Number integer in {0,1,11,12,15,20,21}");
            }
        } else if (raw instanceof BigDecimal) {
            try {
                value = ((BigDecimal) raw).toBigIntegerExact().longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(path
                        + " must be an exact JSON Number integer in {0,1,11,12,15,20,21}");
            }
        } else if (raw instanceof Float || raw instanceof Double) {
            double d = ((Number) raw).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d)) {
                throw new IllegalArgumentException(path
                        + " must be an exact JSON Number integer in {0,1,11,12,15,20,21}");
            }
            value = (long) d;
        } else {
            value = ((Number) raw).longValue();
        }
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE
                || !ANDROID_BIOMETRIC_CAN_AUTHENTICATE_RESULTS.contains(Integer.valueOf((int) value))) {
            throw new IllegalArgumentException(path
                    + " must be an exact JSON Number integer in {0,1,11,12,15,20,21}");
        }
        return (int) value;
    }

    /**
     * Exact JSON Number long: only {@code -1} ({@code BIOMETRIC_NO_AUTHENTICATION}) or
     * nonnegative integral values. Not derived from time config.
     */
    private static long requireAndroidBiometricLastAuthenticationElapsedRealtimeMillis(
            Object raw, String path) {
        if (!(raw instanceof Number) || raw instanceof Boolean) {
            throw new IllegalArgumentException(path
                    + " must be an exact JSON Number integer: -1 or nonnegative");
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(path
                        + " must be an exact JSON Number integer: -1 or nonnegative");
            }
        } else if (raw instanceof BigDecimal) {
            try {
                value = ((BigDecimal) raw).toBigIntegerExact().longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(path
                        + " must be an exact JSON Number integer: -1 or nonnegative");
            }
        } else if (raw instanceof Float || raw instanceof Double) {
            double d = ((Number) raw).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException(path
                        + " must be an exact JSON Number integer: -1 or nonnegative");
            }
            try {
                value = BigDecimal.valueOf(d).toBigIntegerExact().longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(path
                        + " must be an exact JSON Number integer: -1 or nonnegative");
            }
        } else {
            value = ((Number) raw).longValue();
        }
        if (value < -1L) {
            throw new IllegalArgumentException(path
                    + " must be an exact JSON Number integer: -1 or nonnegative");
        }
        return value;
    }

    private static boolean isAllowedAndroidSecurityStateKey(String key) {
        for (String allowed : ANDROID_SECURITY_STATE_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Non-empty String that is a strict 36-char lowercase canonical UUID:
     * {@link UUID#fromString(String)} then {@link UUID#toString()} must equal the input exactly.
     * Rejects null/Boolean/Number, uppercase, non-canonical forms, and control characters.
     */
    private static String requireAndroidIdentifiersAdvertisingId(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty lowercase canonical UUID string");
        }
        String value = (String) raw;
        if (value.isEmpty()) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty lowercase canonical UUID string");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                throw new IllegalArgumentException(path
                        + " must be a non-empty lowercase canonical UUID string");
            }
        }
        final UUID parsed;
        try {
            parsed = UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty lowercase canonical UUID string");
        }
        if (!value.equals(parsed.toString())) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty lowercase canonical UUID string");
        }
        return value;
    }

    /**
     * Android secure ID: exactly 16 hex digits (0-9a-fA-F); normalized to lowercase.
     * Rejects null/non-String/wrong length/non-hex.
     */
    private static String requireAndroidIdentifiersAndroidId(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a 16-character hexadecimal String");
        }
        String value = (String) raw;
        if (value.length() != ANDROID_IDENTIFIERS_ANDROID_ID_HEX_LEN) {
            throw new IllegalArgumentException(path
                    + " must be a 16-character hexadecimal String");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) {
                throw new IllegalArgumentException(path
                        + " must be a 16-character hexadecimal String");
            }
        }
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * Non-empty printable ASCII App Set ID: length {@code 1..150}, characters {@code 0x20..0x7E}
     * (rejects NUL/CR/LF and non-ASCII). Not a UUID requirement; never derived from other identifiers.
     */
    private static String requireAndroidIdentifiersAppSetId(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a nonempty printable ASCII String of length "
                    + ANDROID_IDENTIFIERS_APP_SET_ID_MIN_LEN + ".."
                    + ANDROID_IDENTIFIERS_APP_SET_ID_MAX_LEN + " (no NUL/CR/LF)");
        }
        String value = (String) raw;
        if (value.length() < ANDROID_IDENTIFIERS_APP_SET_ID_MIN_LEN
                || value.length() > ANDROID_IDENTIFIERS_APP_SET_ID_MAX_LEN) {
            throw new IllegalArgumentException(path
                    + " must be a nonempty printable ASCII String of length "
                    + ANDROID_IDENTIFIERS_APP_SET_ID_MIN_LEN + ".."
                    + ANDROID_IDENTIFIERS_APP_SET_ID_MAX_LEN + " (no NUL/CR/LF)");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                throw new IllegalArgumentException(path
                        + " must be a nonempty printable ASCII String of length "
                        + ANDROID_IDENTIFIERS_APP_SET_ID_MIN_LEN + ".."
                        + ANDROID_IDENTIFIERS_APP_SET_ID_MAX_LEN + " (no NUL/CR/LF)");
            }
        }
        return value;
    }

    /**
     * Exact JSON Number integer restricted to Play services App Set scopes {1, 2}
     * ({@code SCOPE_APP}, {@code SCOPE_DEVELOPER}).
     */
    private static int requireAndroidIdentifiersAppSetScope(Object raw, String path) {
        if (!(raw instanceof Number) || raw instanceof Boolean) {
            throw new IllegalArgumentException(path
                    + " must be an exact JSON Number integer in {"
                    + ANDROID_IDENTIFIERS_APP_SET_SCOPE_APP + ","
                    + ANDROID_IDENTIFIERS_APP_SET_SCOPE_DEVELOPER + "}");
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(path
                        + " must be an exact JSON Number integer in {"
                        + ANDROID_IDENTIFIERS_APP_SET_SCOPE_APP + ","
                        + ANDROID_IDENTIFIERS_APP_SET_SCOPE_DEVELOPER + "}");
            }
        } else if (raw instanceof BigDecimal) {
            try {
                value = ((BigDecimal) raw).toBigIntegerExact().longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(path
                        + " must be an exact JSON Number integer in {"
                        + ANDROID_IDENTIFIERS_APP_SET_SCOPE_APP + ","
                        + ANDROID_IDENTIFIERS_APP_SET_SCOPE_DEVELOPER + "}");
            }
        } else if (raw instanceof Float || raw instanceof Double) {
            double d = ((Number) raw).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d)) {
                throw new IllegalArgumentException(path
                        + " must be an exact JSON Number integer in {"
                        + ANDROID_IDENTIFIERS_APP_SET_SCOPE_APP + ","
                        + ANDROID_IDENTIFIERS_APP_SET_SCOPE_DEVELOPER + "}");
            }
            value = (long) d;
        } else {
            value = ((Number) raw).longValue();
        }
        if (value != ANDROID_IDENTIFIERS_APP_SET_SCOPE_APP
                && value != ANDROID_IDENTIFIERS_APP_SET_SCOPE_DEVELOPER) {
            throw new IllegalArgumentException(path
                    + " must be an exact JSON Number integer in {"
                    + ANDROID_IDENTIFIERS_APP_SET_SCOPE_APP + ","
                    + ANDROID_IDENTIFIERS_APP_SET_SCOPE_DEVELOPER + "}");
        }
        return (int) value;
    }

    /**
     * JSON Number → finite float in {@code (0, 10]}. Rejects Boolean/String/null/NaN/infinite/out-of-range.
     */
    private static float requireAndroidConfigurationFontScale(Object raw, String path) {
        if (!(raw instanceof Number) || raw instanceof Boolean) {
            throw new IllegalArgumentException(path
                    + " must be a JSON Number float in range (0, "
                    + (int) ANDROID_CONFIGURATION_FONT_SCALE_MAX + "]");
        }
        float value = ((Number) raw).floatValue();
        if (Float.isNaN(value) || Float.isInfinite(value)
                || value <= 0f || value > ANDROID_CONFIGURATION_FONT_SCALE_MAX) {
            throw new IllegalArgumentException(path
                    + " must be a finite float in range (0, "
                    + (int) ANDROID_CONFIGURATION_FONT_SCALE_MAX + "]");
        }
        return value;
    }

    /**
     * Parse-time rules for optional {@code android.display} when present (v1 metrics subset).
     * Missing node leaves {@link #isAndroidDisplayConfigured()} false; explicit empty object is configured
     * with deterministic defaults. Materializes {@link AndroidDisplayConfig}; never retains JSONObject.
     */
    private void validateAndroidDisplay() {
        JSONObject android = android();
        if (android == null || !android.containsKey("display")) {
            this.androidDisplayConfigured = false;
            this.androidDisplayConfig = null;
            return;
        }
        Object raw = android.get("display");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.display must be a JSONObject");
        }
        JSONObject display = (JSONObject) raw;
        for (String key : display.keySet()) {
            if (!isAllowedAndroidDisplayKey(key)) {
                throw new IllegalArgumentException("android.display." + key
                        + " is not an allowed key (widthPixels|heightPixels|densityDpi|"
                        + "scaledDensity|xdpi|ydpi|refreshRate|rotation|modeId|uniqueId)");
            }
        }

        boolean widthPixelsConfigured = display.containsKey("widthPixels");
        int widthPixels = ANDROID_DISPLAY_DEFAULT_WIDTH_PIXELS;
        if (widthPixelsConfigured) {
            widthPixels = requireExactJsonNumberIntField(display, "widthPixels",
                    "android.display.widthPixels",
                    ANDROID_DISPLAY_PIXELS_MIN, ANDROID_DISPLAY_PIXELS_MAX);
        }

        boolean heightPixelsConfigured = display.containsKey("heightPixels");
        int heightPixels = ANDROID_DISPLAY_DEFAULT_HEIGHT_PIXELS;
        if (heightPixelsConfigured) {
            heightPixels = requireExactJsonNumberIntField(display, "heightPixels",
                    "android.display.heightPixels",
                    ANDROID_DISPLAY_PIXELS_MIN, ANDROID_DISPLAY_PIXELS_MAX);
        }

        boolean densityDpiConfigured = display.containsKey("densityDpi");
        int densityDpi = ANDROID_DISPLAY_DEFAULT_DENSITY_DPI;
        if (densityDpiConfigured) {
            densityDpi = requireExactJsonNumberIntField(display, "densityDpi",
                    "android.display.densityDpi",
                    ANDROID_DISPLAY_DENSITY_DPI_MIN, ANDROID_DISPLAY_DENSITY_DPI_MAX);
        }

        float derivedDensity = densityDpi / 160f;

        boolean scaledDensityConfigured = display.containsKey("scaledDensity");
        float scaledDensity = derivedDensity;
        if (scaledDensityConfigured) {
            scaledDensity = requireAndroidDisplayPositiveFloat(display.get("scaledDensity"),
                    "android.display.scaledDensity");
        }

        boolean xdpiConfigured = display.containsKey("xdpi");
        float xdpi = ANDROID_DISPLAY_DEFAULT_XDPI;
        if (xdpiConfigured) {
            xdpi = requireAndroidDisplayPositiveFloat(display.get("xdpi"), "android.display.xdpi");
        }

        boolean ydpiConfigured = display.containsKey("ydpi");
        float ydpi = ANDROID_DISPLAY_DEFAULT_YDPI;
        if (ydpiConfigured) {
            ydpi = requireAndroidDisplayPositiveFloat(display.get("ydpi"), "android.display.ydpi");
        }

        boolean refreshRateConfigured = display.containsKey("refreshRate");
        float refreshRate = ANDROID_DISPLAY_DEFAULT_REFRESH_RATE;
        if (refreshRateConfigured) {
            refreshRate = requireAndroidDisplayPositiveFloat(display.get("refreshRate"),
                    "android.display.refreshRate", ANDROID_DISPLAY_REFRESH_RATE_MAX);
        }

        boolean rotationConfigured = display.containsKey("rotation");
        int rotation = ANDROID_DISPLAY_DEFAULT_ROTATION;
        if (rotationConfigured) {
            rotation = requireExactJsonNumberIntField(display, "rotation",
                    "android.display.rotation",
                    ANDROID_DISPLAY_ROTATION_MIN, ANDROID_DISPLAY_ROTATION_MAX);
        }

        boolean modeIdConfigured = display.containsKey("modeId");
        int modeId = ANDROID_DISPLAY_DEFAULT_MODE_ID;
        if (modeIdConfigured) {
            modeId = requireExactJsonNumberIntField(display, "modeId",
                    "android.display.modeId",
                    ANDROID_DISPLAY_MODE_ID_MIN, Integer.MAX_VALUE);
        }

        boolean uniqueIdConfigured = display.containsKey("uniqueId");
        String uniqueId = null;
        if (uniqueIdConfigured) {
            uniqueId = requireAndroidDisplayUniqueId(display.get("uniqueId"));
        }

        this.androidDisplayConfigured = true;
        this.androidDisplayConfig = new AndroidDisplayConfig(
                widthPixels, widthPixelsConfigured,
                heightPixels, heightPixelsConfigured,
                densityDpi, densityDpiConfigured,
                scaledDensity, scaledDensityConfigured,
                xdpi, xdpiConfigured,
                ydpi, ydpiConfigured,
                refreshRate, refreshRateConfigured,
                rotation, rotationConfigured,
                modeId, modeIdConfigured,
                uniqueId, uniqueIdConfigured);
    }

    private static boolean isAllowedAndroidDisplayKey(String key) {
        for (String allowed : ANDROID_DISPLAY_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * JSON Number → finite float in {@code (0, 10000]}. Rejects Boolean/String/null/NaN/infinite/out-of-range.
     * Preserves the exact float value produced by {@link Number#floatValue()} for valid inputs.
     */
    private static float requireAndroidDisplayPositiveFloat(Object raw, String path) {
        return requireAndroidDisplayPositiveFloat(raw, path, ANDROID_DISPLAY_FLOAT_MAX);
    }

    /**
     * JSON Number → finite float in {@code (0, max]}. Rejects Boolean/String/null/NaN/infinite/out-of-range
     * (including values that float-convert to {@code <= 0}). Preserves {@link Number#floatValue()}.
     */
    private static float requireAndroidDisplayPositiveFloat(Object raw, String path, float max) {
        if (!(raw instanceof Number) || raw instanceof Boolean) {
            throw new IllegalArgumentException(path
                    + " must be a JSON Number float in range (0, " + max + "]");
        }
        float value = ((Number) raw).floatValue();
        if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0f || value > max) {
            throw new IllegalArgumentException(path
                    + " must be a finite float in range (0, " + max + "]");
        }
        return value;
    }

    /**
     * Parse-time rules for optional {@code android.locale} when present.
     * Missing node leaves {@link #isAndroidLocaleConfigured()} false; explicit empty object is configured
     * with defaults {@code languageTag=en-US} and {@code timezoneId=UTC}.
     * Materializes {@link AndroidLocaleConfig}; never retains JSONObject.
     */
    private void validateAndroidLocale() {
        JSONObject android = android();
        if (android == null || !android.containsKey("locale")) {
            this.androidLocaleConfigured = false;
            this.androidLocaleConfig = null;
            return;
        }
        Object raw = android.get("locale");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.locale must be a JSONObject");
        }
        JSONObject localeNode = (JSONObject) raw;
        for (String key : localeNode.keySet()) {
            if (!isAllowedAndroidLocaleKey(key)) {
                throw new IllegalArgumentException("android.locale." + key
                        + " is not an allowed key (languageTag|languageTags|timezoneId)");
            }
        }

        boolean languageTagConfigured = localeNode.containsKey("languageTag");
        String languageTag = ANDROID_LOCALE_DEFAULT_LANGUAGE_TAG;
        if (languageTagConfigured) {
            languageTag = requireAndroidLocaleLanguageTag(localeNode.get("languageTag"),
                    "android.locale.languageTag");
        }

        boolean languageTagsConfigured = localeNode.containsKey("languageTags");
        List<String> languageTags = Collections.emptyList();
        if (languageTagsConfigured) {
            Object rawTags = localeNode.get("languageTags");
            if (!(rawTags instanceof JSONArray)) {
                throw new IllegalArgumentException("android.locale.languageTags must be a JSONArray");
            }
            JSONArray array = (JSONArray) rawTags;
            List<String> built = new ArrayList<String>(array.size());
            for (int i = 0; i < array.size(); i++) {
                built.add(requireAndroidLocaleLanguageTag(array.get(i),
                        "android.locale.languageTags[" + i + "]"));
            }
            languageTags = Collections.unmodifiableList(built);
        }

        boolean timezoneIdConfigured = localeNode.containsKey("timezoneId");
        String timezoneId = ANDROID_LOCALE_DEFAULT_TIMEZONE_ID;
        if (timezoneIdConfigured) {
            timezoneId = requireAndroidLocaleTimezoneId(localeNode.get("timezoneId"),
                    "android.locale.timezoneId");
        }

        this.androidLocaleConfigured = true;
        this.androidLocaleConfig = new AndroidLocaleConfig(
                languageTag, languageTagConfigured,
                languageTags, languageTagsConfigured,
                timezoneId, timezoneIdConfigured);
    }

    private static boolean isAllowedAndroidLocaleKey(String key) {
        for (String allowed : ANDROID_LOCALE_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Strict string rules then {@link Locale.Builder#setLanguageTag(String)}; stores
     * {@link Locale#toLanguageTag()} canonical form.
     */
    private static String requireAndroidLocaleLanguageTag(Object raw, String path) {
        String tag = requireAndroidLocaleString(raw, path);
        try {
            Locale locale = new Locale.Builder().setLanguageTag(tag).build();
            return locale.toLanguageTag();
        } catch (IllformedLocaleException e) {
            throw new IllegalArgumentException(path + " is not a well-formed language tag: " + tag, e);
        }
    }

    /**
     * Strict string rules then {@link ZoneId#of(String)}; stores {@link ZoneId#getId()} canonical form.
     * Rejects unknown IDs (does not use {@code TimeZone.getTimeZone} silent GMT fallback).
     */
    private static String requireAndroidLocaleTimezoneId(Object raw, String path) {
        String id = requireAndroidLocaleString(raw, path);
        try {
            return ZoneId.of(id).getId();
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(path + " is not a known timezone ID: " + id, e);
        }
    }

    private static String requireAndroidLocaleString(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without leading or trailing whitespace");
        }
        String value = (String) raw;
        if (value.isEmpty() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without leading or trailing whitespace");
        }
        if (value.length() > ANDROID_LOCALE_STRING_MAX) {
            throw new IllegalArgumentException(path + " length must be <= "
                    + ANDROID_LOCALE_STRING_MAX + " characters");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\0' || c == '\r' || c == '\n') {
                throw new IllegalArgumentException(path + " must not contain NUL, CR, or LF");
            }
        }
        return value;
    }

    /**
     * Parse-time rules for optional {@code android.drm} when present.
     * Missing node leaves {@link #isAndroidDrmConfigured()} false; explicit empty object is configured
     * with defaults. Materializes {@link AndroidDrmConfig}; never retains JSONObject.
     */
    private void validateAndroidDrm() {
        JSONObject android = android();
        if (android == null || !android.containsKey("drm")) {
            this.androidDrmConfigured = false;
            this.androidDrmConfig = null;
            return;
        }
        Object raw = android.get("drm");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.drm must be a JSONObject");
        }
        JSONObject drm = (JSONObject) raw;
        for (String key : drm.keySet()) {
            if (!isAllowedAndroidDrmKey(key)) {
                throw new IllegalArgumentException("android.drm." + key
                        + " is not an allowed key (available|marker|schemeUuids|vendor|version|"
                        + "description|algorithms|securityLevel|hdcpLevel|maxHdcpLevel|provisioned|"
                        + "deviceUniqueIdHex|sessionIdHex)");
            }
        }

        boolean availableConfigured = drm.containsKey("available");
        boolean available = true;
        if (availableConfigured) {
            Object v = drm.get("available");
            if (!(v instanceof Boolean)) {
                throw new IllegalArgumentException("android.drm.available must be a Boolean");
            }
            available = ((Boolean) v).booleanValue();
        }

        boolean markerConfigured = drm.containsKey("marker");
        String marker = ANDROID_DRM_DEFAULT_MARKER;
        if (markerConfigured) {
            marker = requireAndroidDrmString(drm.get("marker"), "android.drm.marker",
                    ANDROID_DRM_MARKER_MAX);
        }

        boolean schemeUuidsConfigured = drm.containsKey("schemeUuids");
        List<String> schemeUuids;
        if (schemeUuidsConfigured) {
            schemeUuids = requireAndroidDrmSchemeUuids(drm.get("schemeUuids"), "android.drm.schemeUuids");
        } else {
            schemeUuids = Collections.singletonList(ANDROID_DRM_DEFAULT_WIDEVINE_UUID);
        }

        boolean vendorConfigured = drm.containsKey("vendor");
        String vendor = "TraceAI";
        if (vendorConfigured) {
            vendor = requireAndroidDrmString(drm.get("vendor"), "android.drm.vendor",
                    ANDROID_DRM_STRING_MAX);
        }

        boolean versionConfigured = drm.containsKey("version");
        String version = ANDROID_DRM_DEFAULT_MARKER;
        if (versionConfigured) {
            version = requireAndroidDrmString(drm.get("version"), "android.drm.version",
                    ANDROID_DRM_STRING_MAX);
        }

        boolean descriptionConfigured = drm.containsKey("description");
        String description = "TraceAI DRM analysis marker";
        if (descriptionConfigured) {
            description = requireAndroidDrmString(drm.get("description"), "android.drm.description",
                    ANDROID_DRM_STRING_MAX);
        }

        boolean algorithmsConfigured = drm.containsKey("algorithms");
        String algorithms = "AES/CBC/NoPadding,HmacSHA256";
        if (algorithmsConfigured) {
            algorithms = requireAndroidDrmString(drm.get("algorithms"), "android.drm.algorithms",
                    ANDROID_DRM_STRING_MAX);
        }

        boolean securityLevelConfigured = drm.containsKey("securityLevel");
        String securityLevel = "L3";
        if (securityLevelConfigured) {
            Object v = drm.get("securityLevel");
            if (!(v instanceof String)) {
                throw new IllegalArgumentException(
                        "android.drm.securityLevel must be L1|L2|L3|UNKNOWN");
            }
            securityLevel = (String) v;
            if (!ANDROID_DRM_SECURITY_LEVELS.contains(securityLevel)) {
                throw new IllegalArgumentException(
                        "android.drm.securityLevel must be L1|L2|L3|UNKNOWN");
            }
        }

        boolean hdcpLevelConfigured = drm.containsKey("hdcpLevel");
        String hdcpLevel = "HDCP_NONE";
        if (hdcpLevelConfigured) {
            hdcpLevel = requireAndroidDrmHdcpLevel(drm.get("hdcpLevel"), "android.drm.hdcpLevel");
        }

        boolean maxHdcpLevelConfigured = drm.containsKey("maxHdcpLevel");
        String maxHdcpLevel = "HDCP_NONE";
        if (maxHdcpLevelConfigured) {
            maxHdcpLevel = requireAndroidDrmHdcpLevel(drm.get("maxHdcpLevel"),
                    "android.drm.maxHdcpLevel");
        }

        boolean provisionedConfigured = drm.containsKey("provisioned");
        boolean provisioned = true;
        if (provisionedConfigured) {
            Object v = drm.get("provisioned");
            if (!(v instanceof Boolean)) {
                throw new IllegalArgumentException("android.drm.provisioned must be a Boolean");
            }
            provisioned = ((Boolean) v).booleanValue();
        }

        boolean deviceUniqueIdConfigured = drm.containsKey("deviceUniqueIdHex");
        byte[] deviceUniqueId = null;
        if (deviceUniqueIdConfigured) {
            deviceUniqueId = requireAndroidDrmHexBytes(drm.get("deviceUniqueIdHex"),
                    "android.drm.deviceUniqueIdHex", ANDROID_DRM_DEVICE_UNIQUE_ID_HEX_MAX);
        }

        boolean sessionIdConfigured = drm.containsKey("sessionIdHex");
        byte[] sessionId = null;
        if (sessionIdConfigured) {
            sessionId = requireAndroidDrmHexBytes(drm.get("sessionIdHex"),
                    "android.drm.sessionIdHex", ANDROID_DRM_SESSION_ID_HEX_MAX);
        }

        this.androidDrmConfigured = true;
        this.androidDrmConfig = new AndroidDrmConfig(
                available, availableConfigured,
                marker, markerConfigured,
                schemeUuids, schemeUuidsConfigured,
                vendor, vendorConfigured,
                version, versionConfigured,
                description, descriptionConfigured,
                algorithms, algorithmsConfigured,
                securityLevel, securityLevelConfigured,
                hdcpLevel, hdcpLevelConfigured,
                maxHdcpLevel, maxHdcpLevelConfigured,
                provisioned, provisionedConfigured,
                deviceUniqueId, deviceUniqueIdConfigured,
                sessionId, sessionIdConfigured);
    }

    private static boolean isAllowedAndroidDrmKey(String key) {
        for (String allowed : ANDROID_DRM_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static String requireAndroidDrmString(Object raw, String path, int maxLen) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without NUL/CR/LF");
        }
        String value = (String) raw;
        if (value.isEmpty()) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without NUL/CR/LF");
        }
        if (value.indexOf('\0') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without NUL/CR/LF");
        }
        if (value.length() > maxLen) {
            throw new IllegalArgumentException(path + " length must be <= " + maxLen
                    + " characters");
        }
        return value;
    }

    private static String requireAndroidDrmHdcpLevel(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be HDCP_NONE|HDCP_V1|HDCP_V2|HDCP_V2_1|HDCP_V2_2|HDCP_V2_3|"
                    + "HDCP_NO_DIGITAL_OUTPUT|HDCP_LEVEL_UNKNOWN");
        }
        String value = (String) raw;
        if (!ANDROID_DRM_HDCP_LEVELS.contains(value)) {
            throw new IllegalArgumentException(path
                    + " must be HDCP_NONE|HDCP_V1|HDCP_V2|HDCP_V2_1|HDCP_V2_2|HDCP_V2_3|"
                    + "HDCP_NO_DIGITAL_OUTPUT|HDCP_LEVEL_UNKNOWN");
        }
        return value;
    }

    private static List<String> requireAndroidDrmSchemeUuids(Object raw, String path) {
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException(path + " must be a non-empty JSONArray of UUID strings");
        }
        JSONArray array = (JSONArray) raw;
        if (array.isEmpty()) {
            throw new IllegalArgumentException(path + " must be a non-empty JSONArray of UUID strings");
        }
        List<String> built = new ArrayList<String>(array.size());
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < array.size(); i++) {
            String itemPath = path + "[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof String)) {
                throw new IllegalArgumentException(itemPath + " must be a UUID String");
            }
            String text = (String) item;
            final UUID uuid;
            try {
                uuid = UUID.fromString(text);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(itemPath + " must be a UUID String");
            }
            String canonical = uuid.toString().toLowerCase(Locale.ROOT);
            if (!seen.add(canonical)) {
                throw new IllegalArgumentException(itemPath + " is a duplicate UUID: " + canonical);
            }
            built.add(canonical);
        }
        return Collections.unmodifiableList(built);
    }

    private static byte[] requireAndroidDrmHexBytes(Object raw, String path, int maxHexChars) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty even-length hex String");
        }
        String hex = (String) raw;
        if (hex.isEmpty() || (hex.length() & 1) != 0) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty even-length hex String");
        }
        if (hex.length() > maxHexChars) {
            throw new IllegalArgumentException(path + " length must be <= " + maxHexChars
                    + " hex characters");
        }
        for (int i = 0; i < hex.length(); i++) {
            if (Character.digit(hex.charAt(i), 16) < 0) {
                throw new IllegalArgumentException(path
                        + " must contain only hexadecimal characters");
            }
        }
        String lower = hex.toLowerCase(Locale.ROOT);
        int n = lower.length() / 2;
        byte[] data = new byte[n];
        for (int i = 0; i < n; i++) {
            int high = Character.digit(lower.charAt(i * 2), 16);
            int low = Character.digit(lower.charAt(i * 2 + 1), 16);
            data[i] = (byte) ((high << 4) | low);
        }
        return data;
    }

    /**
     * Parse-time rules for optional {@code linux.proc} when present.
     * Strict field whitelist and types under {@code linux.proc} only;
     * does not whitelist or revalidate sibling {@code linux.uname}/{@code linux.files}.
     */
    private void validateLinuxProc() {
        JSONObject linux = section("linux");
        if (linux == null || !linux.containsKey("proc")) {
            this.linuxProcConfigured = false;
            this.linuxProcConfig = null;
            return;
        }
        Object raw = linux.get("proc");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("linux.proc must be a JSONObject");
        }
        JSONObject proc = (JSONObject) raw;
        for (String key : proc.keySet()) {
            if (!isAllowedLinuxProcKey(key)) {
                throw new IllegalArgumentException("linux.proc." + key
                        + " is not an allowed key (state|tracerPid|threadCount|cmdline|cgroups|"
                        + "startTimeTicks|virtualMemoryBytes|residentSetPages|"
                        + "rchar|wchar|syscr|syscw|readBytes|writeBytes|cancelledWriteBytes|"
                        + "bootId|randomUuid|entropyAvail|randomPoolSize|"
                        + "writeWakeupThreshold|urandomMinReseedSecs|"
                        + "oomScoreAdj|oomScore|oomAdj|selinuxContext|fileSelinuxContexts|"
                        + "comm|wchan|dumpable|nice|seccompMode|noNewPrivs|capEffectiveHex|"
                        + "capInheritableHex|capPermittedHex|capBoundingHex|capAmbientHex|"
                        + "signalBlockedHex|signalIgnoredHex|signalCaughtHex|limits)");
            }
        }

        boolean stateConfigured = proc.containsKey("state");
        String state = "S";
        if (stateConfigured) {
            Object stateRaw = proc.get("state");
            if (!(stateRaw instanceof String)) {
                throw new IllegalArgumentException(
                        "linux.proc.state must be a single-character String (R|S|D|Z|T|t|X|I)");
            }
            state = (String) stateRaw;
            if (state.length() != 1 || !LINUX_PROC_STATES.contains(state)) {
                throw new IllegalArgumentException(
                        "linux.proc.state must be a single-character String (R|S|D|Z|T|t|X|I)");
            }
        }

        boolean tracerPidConfigured = proc.containsKey("tracerPid");
        int tracerPid = 0;
        if (tracerPidConfigured) {
            tracerPid = requireExactJsonNumberIntField(proc, "tracerPid", "linux.proc.tracerPid",
                    0, Integer.MAX_VALUE);
        }

        boolean threadCountConfigured = proc.containsKey("threadCount");
        int threadCount = 1;
        if (threadCountConfigured) {
            threadCount = requireExactJsonNumberIntField(proc, "threadCount", "linux.proc.threadCount",
                    1, Integer.MAX_VALUE);
        }

        boolean cmdlineConfigured = proc.containsKey("cmdline");
        List<String> cmdline;
        if (cmdlineConfigured) {
            cmdline = requireLinuxProcStringArray(proc.get("cmdline"), "linux.proc.cmdline",
                    true);
        } else {
            cmdline = Collections.emptyList();
        }

        boolean cgroupsConfigured = proc.containsKey("cgroups");
        List<String> cgroups;
        if (cgroupsConfigured) {
            cgroups = requireLinuxProcStringArray(proc.get("cgroups"), "linux.proc.cgroups",
                    false);
        } else {
            cgroups = Collections.emptyList();
        }

        boolean startTimeTicksConfigured = proc.containsKey("startTimeTicks");
        long startTimeTicks = 0L;
        if (startTimeTicksConfigured) {
            startTimeTicks = requireExactJsonNumberLongField(proc, "startTimeTicks",
                    "linux.proc.startTimeTicks", 0L, Long.MAX_VALUE);
        }

        boolean virtualMemoryBytesConfigured = proc.containsKey("virtualMemoryBytes");
        long virtualMemoryBytes = 0L;
        if (virtualMemoryBytesConfigured) {
            virtualMemoryBytes = requireExactJsonNumberLongField(proc, "virtualMemoryBytes",
                    "linux.proc.virtualMemoryBytes", 0L, Long.MAX_VALUE);
        }

        boolean residentSetPagesConfigured = proc.containsKey("residentSetPages");
        long residentSetPages = 0L;
        if (residentSetPagesConfigured) {
            residentSetPages = requireExactJsonNumberLongField(proc, "residentSetPages",
                    "linux.proc.residentSetPages", 0L, Long.MAX_VALUE);
        }

        boolean rcharConfigured = proc.containsKey("rchar");
        long rchar = 0L;
        if (rcharConfigured) {
            rchar = requireExactJsonNumberLongField(proc, "rchar",
                    "linux.proc.rchar", 0L, Long.MAX_VALUE);
        }

        boolean wcharConfigured = proc.containsKey("wchar");
        long wchar = 0L;
        if (wcharConfigured) {
            wchar = requireExactJsonNumberLongField(proc, "wchar",
                    "linux.proc.wchar", 0L, Long.MAX_VALUE);
        }

        boolean syscrConfigured = proc.containsKey("syscr");
        long syscr = 0L;
        if (syscrConfigured) {
            syscr = requireExactJsonNumberLongField(proc, "syscr",
                    "linux.proc.syscr", 0L, Long.MAX_VALUE);
        }

        boolean syscwConfigured = proc.containsKey("syscw");
        long syscw = 0L;
        if (syscwConfigured) {
            syscw = requireExactJsonNumberLongField(proc, "syscw",
                    "linux.proc.syscw", 0L, Long.MAX_VALUE);
        }

        boolean readBytesConfigured = proc.containsKey("readBytes");
        long readBytes = 0L;
        if (readBytesConfigured) {
            readBytes = requireExactJsonNumberLongField(proc, "readBytes",
                    "linux.proc.readBytes", 0L, Long.MAX_VALUE);
        }

        boolean writeBytesConfigured = proc.containsKey("writeBytes");
        long writeBytes = 0L;
        if (writeBytesConfigured) {
            writeBytes = requireExactJsonNumberLongField(proc, "writeBytes",
                    "linux.proc.writeBytes", 0L, Long.MAX_VALUE);
        }

        boolean cancelledWriteBytesConfigured = proc.containsKey("cancelledWriteBytes");
        long cancelledWriteBytes = 0L;
        if (cancelledWriteBytesConfigured) {
            cancelledWriteBytes = requireExactJsonNumberLongField(proc, "cancelledWriteBytes",
                    "linux.proc.cancelledWriteBytes", 0L, Long.MAX_VALUE);
        }

        boolean bootIdConfigured = proc.containsKey("bootId");
        String bootId = null;
        if (bootIdConfigured) {
            bootId = requireLinuxProcCanonicalUuid(proc.get("bootId"), "linux.proc.bootId");
        }

        boolean randomUuidConfigured = proc.containsKey("randomUuid");
        String randomUuid = null;
        if (randomUuidConfigured) {
            randomUuid = requireLinuxProcCanonicalUuid(proc.get("randomUuid"),
                    "linux.proc.randomUuid");
        }

        boolean entropyAvailConfigured = proc.containsKey("entropyAvail");
        int entropyAvail = 0;
        if (entropyAvailConfigured) {
            entropyAvail = requireExactJsonNumberIntField(proc, "entropyAvail",
                    "linux.proc.entropyAvail", 0, Integer.MAX_VALUE);
        }

        boolean randomPoolSizeConfigured = proc.containsKey("randomPoolSize");
        int randomPoolSize = 0;
        if (randomPoolSizeConfigured) {
            randomPoolSize = requireExactJsonNumberIntField(proc, "randomPoolSize",
                    "linux.proc.randomPoolSize", 0, Integer.MAX_VALUE);
        }

        boolean writeWakeupThresholdConfigured = proc.containsKey("writeWakeupThreshold");
        int writeWakeupThreshold = 0;
        if (writeWakeupThresholdConfigured) {
            writeWakeupThreshold = requireExactJsonNumberIntField(proc, "writeWakeupThreshold",
                    "linux.proc.writeWakeupThreshold", 0, Integer.MAX_VALUE);
        }

        boolean urandomMinReseedSecsConfigured = proc.containsKey("urandomMinReseedSecs");
        int urandomMinReseedSecs = 0;
        if (urandomMinReseedSecsConfigured) {
            urandomMinReseedSecs = requireExactJsonNumberIntField(proc, "urandomMinReseedSecs",
                    "linux.proc.urandomMinReseedSecs", 0, Integer.MAX_VALUE);
        }

        boolean oomScoreAdjConfigured = proc.containsKey("oomScoreAdj");
        int oomScoreAdj = 0;
        if (oomScoreAdjConfigured) {
            oomScoreAdj = requireExactJsonNumberIntField(proc, "oomScoreAdj",
                    "linux.proc.oomScoreAdj", -1000, 1000);
        }

        boolean oomScoreConfigured = proc.containsKey("oomScore");
        int oomScore = 0;
        if (oomScoreConfigured) {
            oomScore = requireExactJsonNumberIntField(proc, "oomScore",
                    "linux.proc.oomScore", 0, 2000);
        }

        boolean oomAdjConfigured = proc.containsKey("oomAdj");
        int oomAdj = 0;
        if (oomAdjConfigured) {
            // -17 (OOM_DISABLE) or -16..15; equivalent integer set is [-17, 15]
            oomAdj = requireExactJsonNumberIntField(proc, "oomAdj",
                    "linux.proc.oomAdj", -17, 15);
        }

        boolean selinuxContextConfigured = proc.containsKey("selinuxContext");
        String selinuxContext = null;
        if (selinuxContextConfigured) {
            selinuxContext = requireLinuxProcSelinuxContext(proc.get("selinuxContext"),
                    "linux.proc.selinuxContext");
        }

        boolean fileSelinuxContextsConfigured = proc.containsKey("fileSelinuxContexts");
        Map<String, String> fileSelinuxContexts = Collections.emptyMap();
        if (fileSelinuxContextsConfigured) {
            fileSelinuxContexts = requireLinuxProcFileSelinuxContexts(
                    proc.get("fileSelinuxContexts"), "linux.proc.fileSelinuxContexts");
        }

        boolean commConfigured = proc.containsKey("comm");
        String comm = null;
        if (commConfigured) {
            comm = requireLinuxProcComm(proc.get("comm"), "linux.proc.comm");
        }

        boolean wchanConfigured = proc.containsKey("wchan");
        String wchan = null;
        if (wchanConfigured) {
            wchan = requireLinuxProcWchan(proc.get("wchan"), "linux.proc.wchan");
        }

        boolean dumpableConfigured = proc.containsKey("dumpable");
        int dumpable = 0;
        if (dumpableConfigured) {
            dumpable = requireExactJsonNumberIntField(proc, "dumpable",
                    "linux.proc.dumpable", 0, 2);
        }

        boolean niceConfigured = proc.containsKey("nice");
        int nice = 0;
        if (niceConfigured) {
            nice = requireExactJsonNumberIntField(proc, "nice",
                    "linux.proc.nice", -20, 19);
        }

        boolean seccompModeConfigured = proc.containsKey("seccompMode");
        int seccompMode = 0;
        if (seccompModeConfigured) {
            seccompMode = requireExactJsonNumberIntField(proc, "seccompMode",
                    "linux.proc.seccompMode", 0, 2);
        }

        boolean noNewPrivsConfigured = proc.containsKey("noNewPrivs");
        boolean noNewPrivs = false;
        if (noNewPrivsConfigured) {
            noNewPrivs = requireAndroidPowerBoolean(proc.get("noNewPrivs"),
                    "linux.proc.noNewPrivs");
        }

        boolean capEffectiveHexConfigured = proc.containsKey("capEffectiveHex");
        String capEffectiveHex = null;
        if (capEffectiveHexConfigured) {
            capEffectiveHex = requireLinuxProcCapMaskHex(proc.get("capEffectiveHex"),
                    "linux.proc.capEffectiveHex");
        }

        boolean capInheritableHexConfigured = proc.containsKey("capInheritableHex");
        String capInheritableHex = null;
        if (capInheritableHexConfigured) {
            capInheritableHex = requireLinuxProcCapMaskHex(proc.get("capInheritableHex"),
                    "linux.proc.capInheritableHex");
        }

        boolean capPermittedHexConfigured = proc.containsKey("capPermittedHex");
        String capPermittedHex = null;
        if (capPermittedHexConfigured) {
            capPermittedHex = requireLinuxProcCapMaskHex(proc.get("capPermittedHex"),
                    "linux.proc.capPermittedHex");
        }

        boolean capBoundingHexConfigured = proc.containsKey("capBoundingHex");
        String capBoundingHex = null;
        if (capBoundingHexConfigured) {
            capBoundingHex = requireLinuxProcCapMaskHex(proc.get("capBoundingHex"),
                    "linux.proc.capBoundingHex");
        }

        boolean capAmbientHexConfigured = proc.containsKey("capAmbientHex");
        String capAmbientHex = null;
        if (capAmbientHexConfigured) {
            capAmbientHex = requireLinuxProcCapMaskHex(proc.get("capAmbientHex"),
                    "linux.proc.capAmbientHex");
        }

        boolean signalBlockedHexConfigured = proc.containsKey("signalBlockedHex");
        String signalBlockedHex = null;
        if (signalBlockedHexConfigured) {
            signalBlockedHex = requireLinuxProcCapMaskHex(proc.get("signalBlockedHex"),
                    "linux.proc.signalBlockedHex");
        }

        boolean signalIgnoredHexConfigured = proc.containsKey("signalIgnoredHex");
        String signalIgnoredHex = null;
        if (signalIgnoredHexConfigured) {
            signalIgnoredHex = requireLinuxProcCapMaskHex(proc.get("signalIgnoredHex"),
                    "linux.proc.signalIgnoredHex");
        }

        boolean signalCaughtHexConfigured = proc.containsKey("signalCaughtHex");
        String signalCaughtHex = null;
        if (signalCaughtHexConfigured) {
            signalCaughtHex = requireLinuxProcCapMaskHex(proc.get("signalCaughtHex"),
                    "linux.proc.signalCaughtHex");
        }

        boolean limitsConfigured = proc.containsKey("limits");
        List<String> limits = Collections.emptyList();
        if (limitsConfigured) {
            limits = requireLinuxProcLimitsArray(proc.get("limits"), "linux.proc.limits");
        }

        this.linuxProcConfigured = true;
        this.linuxProcConfig = new LinuxProcConfig(
                state, stateConfigured,
                tracerPid, tracerPidConfigured,
                threadCount, threadCountConfigured,
                cmdline, cmdlineConfigured,
                cgroups, cgroupsConfigured,
                startTimeTicks, startTimeTicksConfigured,
                virtualMemoryBytes, virtualMemoryBytesConfigured,
                residentSetPages, residentSetPagesConfigured,
                rchar, rcharConfigured,
                wchar, wcharConfigured,
                syscr, syscrConfigured,
                syscw, syscwConfigured,
                readBytes, readBytesConfigured,
                writeBytes, writeBytesConfigured,
                cancelledWriteBytes, cancelledWriteBytesConfigured,
                bootId, bootIdConfigured,
                randomUuid, randomUuidConfigured,
                entropyAvail, entropyAvailConfigured,
                randomPoolSize, randomPoolSizeConfigured,
                writeWakeupThreshold, writeWakeupThresholdConfigured,
                urandomMinReseedSecs, urandomMinReseedSecsConfigured,
                oomScoreAdj, oomScoreAdjConfigured,
                oomScore, oomScoreConfigured,
                oomAdj, oomAdjConfigured,
                selinuxContext, selinuxContextConfigured,
                fileSelinuxContexts, fileSelinuxContextsConfigured,
                comm, commConfigured,
                wchan, wchanConfigured,
                dumpable, dumpableConfigured,
                nice, niceConfigured,
                seccompMode, seccompModeConfigured,
                noNewPrivs, noNewPrivsConfigured,
                capEffectiveHex, capEffectiveHexConfigured,
                capInheritableHex, capInheritableHexConfigured,
                capPermittedHex, capPermittedHexConfigured,
                capBoundingHex, capBoundingHexConfigured,
                capAmbientHex, capAmbientHexConfigured,
                signalBlockedHex, signalBlockedHexConfigured,
                signalIgnoredHex, signalIgnoredHexConfigured,
                signalCaughtHex, signalCaughtHexConfigured,
                limits, limitsConfigured);
    }

    /**
     * Ordered nonempty printable ASCII lines for {@code /proc/self|pid/limits}
     * ({@code 0x20..0x7E}), no CR/LF/NUL. Explicit empty array allowed. No default.
     */
    private static List<String> requireLinuxProcLimitsArray(Object raw, String path) {
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException(path + " must be a JSONArray");
        }
        JSONArray array = (JSONArray) raw;
        List<String> built = new ArrayList<String>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String itemPath = path + "[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof String)) {
                throw new IllegalArgumentException(itemPath
                        + " must be a nonempty printable ASCII String (no CR/LF)");
            }
            String value = (String) item;
            if (value.isEmpty()) {
                throw new IllegalArgumentException(itemPath
                        + " must be a nonempty printable ASCII String (no CR/LF)");
            }
            for (int c = 0; c < value.length(); c++) {
                char ch = value.charAt(c);
                // printable ASCII 0x20..0x7E (excludes NUL/CR/LF/controls)
                if (ch < 0x20 || ch > 0x7E) {
                    throw new IllegalArgumentException(itemPath
                            + " must be a nonempty printable ASCII String (no CR/LF)");
                }
            }
            built.add(value);
        }
        return Collections.unmodifiableList(built);
    }

    /**
     * Fixed-width 16-hex mask for Cap* and Sig* status fields.
     * Accepts {@code [0-9a-fA-F]} only (no {@code 0x}, no whitespace); stores lowercase.
     * No default/inference between fields.
     */
    private static String requireLinuxProcCapMaskHex(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a fixed-width 16-hex String (lowercase stored)");
        }
        String text = (String) raw;
        if (text.length() != 16) {
            throw new IllegalArgumentException(path
                    + " must be a fixed-width 16-hex String (lowercase stored)");
        }
        for (int i = 0; i < 16; i++) {
            char c = text.charAt(i);
            boolean hex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) {
                throw new IllegalArgumentException(path
                        + " must be a fixed-width 16-hex String (lowercase stored)");
            }
        }
        return text.toLowerCase(Locale.ROOT);
    }

    /**
     * Non-null canonical UUID string normalized to lowercase. Accepts only exactly 36 ASCII
     * characters matching {@code 8-4-4-4-12} hex digits with hyphens (case-insensitive).
     * Rejects JSON null, non-String, leading/trailing whitespace, short groups, extra text,
     * and non-ASCII. Used for {@code bootId} and {@code randomUuid}; never generates a value.
     * Does not use {@link UUID#fromString} (which accepts non-canonical short forms).
     */
    private static String requireLinuxProcCanonicalUuid(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a non-null canonical UUID String");
        }
        String text = (String) raw;
        // Exact length and form only — no trim; UUID.fromString would accept "1-1-1-1-1"
        if (text.length() != 36 || !LINUX_PROC_BOOT_ID_CANONICAL.matcher(text).matches()) {
            throw new IllegalArgumentException(path
                    + " must be a non-null canonical UUID String");
        }
        return text.toLowerCase(Locale.ROOT);
    }

    /**
     * Non-null nonempty SELinux context string: printable ASCII without whitespace or
     * controls, length 1..256. No default/inference; no trim.
     */
    private static String requireLinuxProcSelinuxContext(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a non-null nonempty printable ASCII String"
                    + " (no whitespace/controls, max 256)");
        }
        String text = (String) raw;
        int len = text.length();
        if (len < 1 || len > 256) {
            throw new IllegalArgumentException(path
                    + " must be a non-null nonempty printable ASCII String"
                    + " (no whitespace/controls, max 256)");
        }
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            // printable non-whitespace ASCII: 0x21 ('!') .. 0x7E ('~')
            if (c < 0x21 || c > 0x7E) {
                throw new IllegalArgumentException(path
                        + " must be a non-null nonempty printable ASCII String"
                        + " (no whitespace/controls, max 256)");
            }
        }
        return text;
    }

    /**
     * Path-keyed map of absolute POSIX paths → SELinux file contexts. Keys are normalized
     * (duplicate normalized paths rejected). Values use the same rules as
     * {@link #requireLinuxProcSelinuxContext}. Empty object is allowed. No inference from
     * {@code selinuxContext}. Rejects JSON null / non-object.
     */
    private static Map<String, String> requireLinuxProcFileSelinuxContexts(Object raw, String path) {
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException(path
                    + " must be a JSONObject (path-to-context map)");
        }
        JSONObject map = (JSONObject) raw;
        if (map.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> built = new LinkedHashMap<String, String>(map.size());
        Set<String> seenNormalized = new HashSet<String>();
        for (String pathKey : map.keySet()) {
            String pathPrefix = path + "[\"" + pathKey + "\"]";
            String normalized = normalizePosixAbsolutePath(pathKey, pathPrefix);
            if (!seenNormalized.add(normalized)) {
                throw new IllegalArgumentException(pathPrefix
                        + " normalizes to a duplicate path: " + normalized);
            }
            String context = requireLinuxProcSelinuxContext(map.get(pathKey), pathPrefix);
            built.put(normalized, context);
        }
        return Collections.unmodifiableMap(built);
    }

    /**
     * Non-null nonempty task {@code comm}: printable ASCII ({@code 0x20..0x7E}), no CR/LF
     * (already outside that range), max 15 ASCII bytes ({@code TASK_COMM_LEN - 1}).
     * No default/inference from processName/threadName; no trim.
     */
    private static String requireLinuxProcComm(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a non-null nonempty printable ASCII String"
                    + " (no CR/LF, max 15)");
        }
        String text = (String) raw;
        int len = text.length();
        // pure ASCII: char length == UTF-8/ASCII byte length
        if (len < 1 || len > 15) {
            throw new IllegalArgumentException(path
                    + " must be a non-null nonempty printable ASCII String"
                    + " (no CR/LF, max 15)");
        }
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            // printable ASCII: 0x20 (' ') .. 0x7E ('~') — excludes CR/LF/NUL/controls
            if (c < 0x20 || c > 0x7E) {
                throw new IllegalArgumentException(path
                        + " must be a non-null nonempty printable ASCII String"
                        + " (no CR/LF, max 15)");
            }
        }
        return text;
    }

    /**
     * Non-null nonempty wait-channel marker: printable ASCII ({@code 0x20..0x7E}), no CR/LF,
     * max 255 ASCII bytes. Fixed analysis string only — no default/derivation; no trim.
     */
    private static String requireLinuxProcWchan(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a non-null nonempty printable ASCII String"
                    + " (no CR/LF, max 255)");
        }
        String text = (String) raw;
        int len = text.length();
        // pure ASCII: char length == UTF-8/ASCII byte length
        if (len < 1 || len > 255) {
            throw new IllegalArgumentException(path
                    + " must be a non-null nonempty printable ASCII String"
                    + " (no CR/LF, max 255)");
        }
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            // printable ASCII: 0x20 (' ') .. 0x7E ('~') — excludes CR/LF/NUL/controls
            if (c < 0x20 || c > 0x7E) {
                throw new IllegalArgumentException(path
                        + " must be a non-null nonempty printable ASCII String"
                        + " (no CR/LF, max 255)");
            }
        }
        return text;
    }

    private static boolean isAllowedLinuxProcKey(String key) {
        for (String allowed : LINUX_PROC_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param allowEmptyElement {@code true} for cmdline (empty string argv allowed);
     *                          {@code false} for cgroups (each element non-empty)
     */
    private static List<String> requireLinuxProcStringArray(Object raw, String path,
                                                            boolean allowEmptyElement) {
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException(path + " must be a JSONArray");
        }
        JSONArray array = (JSONArray) raw;
        List<String> built = new ArrayList<String>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String itemPath = path + "[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof String)) {
                throw new IllegalArgumentException(itemPath
                        + " must be a String without NUL/CR/LF");
            }
            String value = (String) item;
            if (!allowEmptyElement && value.isEmpty()) {
                throw new IllegalArgumentException(itemPath
                        + " must be a non-empty String without NUL/CR/LF");
            }
            if (value.indexOf('\0') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw new IllegalArgumentException(itemPath
                        + " must be a String without NUL/CR/LF");
            }
            built.add(value);
        }
        return Collections.unmodifiableList(built);
    }

    private static final String[] NETWORK_WIFI_ALLOWED_KEYS = {
            "enabled", "ssid", "bssid", "macAddress", "ipv4",
            "rssi", "linkSpeedMbps", "frequencyMhz", "networkId", "state",
            "scanResults"
    };
    /** WifiManager.WIFI_STATE_DISABLING..UNKNOWN (0..4). No default; presence-gated. */
    private static final int NETWORK_WIFI_STATE_MIN = 0;
    private static final int NETWORK_WIFI_STATE_MAX = 4;

    private static final String[] NETWORK_WIFI_STRING_KEYS = {
            "ssid", "bssid", "macAddress", "ipv4"
    };

    private static final String[] NETWORK_WIFI_INT_KEYS = {
            "rssi", "linkSpeedMbps", "frequencyMhz", "networkId"
    };

    private static final String[] NETWORK_BLUETOOTH_ALLOWED_KEYS = {
            "name", "address", "enabled", "state", "scanMode", "discovering"
    };

    /**
     * BluetoothAdapter.STATE_OFF / TURNING_ON / ON / TURNING_OFF (10,11,12,13).
     */
    private static final Set<Integer> NETWORK_BLUETOOTH_STATES;
    /**
     * BluetoothAdapter.SCAN_MODE_NONE / CONNECTABLE / CONNECTABLE_DISCOVERABLE (20,21,23).
     */
    private static final Set<Integer> NETWORK_BLUETOOTH_SCAN_MODES;
    static {
        Set<Integer> states = new HashSet<Integer>();
        states.add(10);
        states.add(11);
        states.add(12);
        states.add(13);
        NETWORK_BLUETOOTH_STATES = Collections.unmodifiableSet(states);
        Set<Integer> scanModes = new HashSet<Integer>();
        scanModes.add(20);
        scanModes.add(21);
        scanModes.add(23);
        NETWORK_BLUETOOTH_SCAN_MODES = Collections.unmodifiableSet(scanModes);
    }

    private static final String[] NETWORK_LINKS_ALLOWED_KEYS = {
            "connected", "type", "typeName", "interfaceName", "dnsServers", "gatewayIpv4", "mtu",
            "privateDnsActive", "privateDnsServerName", "proxyHost", "proxyPort",
            "dhcpServerIpv4", "leaseDurationSeconds", "domains", "netmaskIpv4"
    };

    private static final String[] NETWORK_LINKS_STRING_KEYS = {
            "typeName", "interfaceName", "gatewayIpv4",
            "privateDnsServerName", "proxyHost", "dhcpServerIpv4", "domains", "netmaskIpv4"
    };

    private static final String[] NETWORK_LINKS_INT_KEYS = {
            "type", "mtu", "proxyPort", "leaseDurationSeconds"
    };

    private static final String[] NETWORK_LINKS_BOOLEAN_KEYS = {
            "connected", "privateDnsActive"
    };

    private static final String[] ANDROID_SETTINGS_NAMESPACES = { "secure", "system", "global" };

    private static final String[] TELEPHONY_TOP_LEVEL_KEYS = {
            "phoneCount", "slots",
            "networkOperator", "networkOperatorName", "simOperator", "simOperatorName",
            "networkCountryIso", "simCountryIso",
            "dataNetworkType", "dataState", "dataActivity", "phoneType", "networkRoaming",
            "cellInfo"
    };

    private static final String[] TELEPHONY_GLOBAL_STRING_KEYS = {
            "networkOperator", "networkOperatorName", "simOperator", "simOperatorName",
            "networkCountryIso", "simCountryIso"
    };

    private static final String TELEPHONY_NETWORK_COUNTRY_ISO_KEY = "networkCountryIso";
    private static final String TELEPHONY_SIM_COUNTRY_ISO_KEY = "simCountryIso";

    private static final String[] TELEPHONY_GLOBAL_INT_KEYS = {
            "dataNetworkType", "dataState", "dataActivity", "phoneType"
    };

    private static final String[] TELEPHONY_SLOT_IDENTIFIER_KEYS = {
            "imei", "meid", "deviceId", "subscriberId", "simSerialNumber"
    };

    private static final String[] TELEPHONY_SLOT_ALLOWED_KEYS = {
            "slotIndex", "imei", "meid", "deviceId", "subscriberId", "simSerialNumber", "simState"
    };

    /**
     * Parse-time rules for {@code android.settings} when present.
     * Paths in errors: {@code android.settings}, {@code android.settings.<namespace>},
     * or {@code android.settings.<namespace>.<key>}.
     */
    private void validateAndroidSettings() {
        JSONObject android = android();
        if (android == null || !android.containsKey("settings")) {
            return;
        }
        Object settingsRaw = android.get("settings");
        if (!(settingsRaw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.settings must be a JSONObject");
        }
        JSONObject settings = (JSONObject) settingsRaw;
        for (String namespace : settings.keySet()) {
            if (!isAllowedSettingsNamespace(namespace)) {
                throw new IllegalArgumentException("android.settings." + namespace
                        + " is not an allowed namespace (secure|system|global)");
            }
            Object nsRaw = settings.get(namespace);
            if (!(nsRaw instanceof JSONObject)) {
                throw new IllegalArgumentException("android.settings." + namespace + " must be a JSONObject");
            }
            JSONObject ns = (JSONObject) nsRaw;
            for (String key : ns.keySet()) {
                String path = "android.settings." + namespace + "." + key;
                if (key == null || key.isEmpty() || !key.equals(key.trim())) {
                    throw new IllegalArgumentException(path
                            + " key must be a non-empty String without leading or trailing whitespace");
                }
                Object value = ns.get(key);
                if (value != null && !(value instanceof String)) {
                    throw new IllegalArgumentException(path
                            + " value must be a String or JSON null");
                }
            }
        }
    }

    private static boolean isAllowedSettingsNamespace(String namespace) {
        for (String allowed : ANDROID_SETTINGS_NAMESPACES) {
            if (allowed.equals(namespace)) {
                return true;
            }
        }
        return false;
    }

    private static void requireSettingsApiArgs(String namespace, String key) {
        if (!isAllowedSettingsNamespace(namespace)) {
            throw new IllegalArgumentException("namespace must be secure, system, or global: " + namespace);
        }
        if (key == null || key.isEmpty() || !key.equals(key.trim())) {
            throw new IllegalArgumentException("key must be a non-empty String without leading or trailing whitespace");
        }
    }

    /**
     * Whether {@code android.settings.<namespace>.<key>} is present (including explicit JSON null).
     */
    public boolean isAndroidSettingConfigured(String namespace, String key) {
        requireSettingsApiArgs(namespace, key);
        JSONObject ns = androidSettingsNamespace(namespace);
        return ns != null && ns.containsKey(key);
    }

    /**
     * Value of {@code android.settings.<namespace>.<key>}, or null if missing or explicit JSON null.
     * Use {@link #isAndroidSettingConfigured} to distinguish missing vs explicit null.
     */
    public String getAndroidSettingString(String namespace, String key) {
        requireSettingsApiArgs(namespace, key);
        JSONObject ns = androidSettingsNamespace(namespace);
        if (ns == null || !ns.containsKey(key)) {
            return null;
        }
        Object value = ns.get(key);
        if (value == null) {
            return null;
        }
        return (String) value;
    }

    private JSONObject androidSettingsNamespace(String namespace) {
        JSONObject android = android();
        if (android == null) {
            return null;
        }
        JSONObject settings = android.getJSONObject("settings");
        if (settings == null) {
            return null;
        }
        return settings.getJSONObject(namespace);
    }

    /**
     * Parse-time rules for {@code android.telephony} when present
     * (slot identifiers, global operator strings, and scalar state).
     * Paths in errors: {@code android.telephony}, {@code android.telephony.phoneCount},
     * {@code android.telephony.slots}, {@code android.telephony.slots[i].&lt;field&gt;},
     * or {@code android.telephony.networkOperator} / {@code dataNetworkType} (and siblings).
     */
    private void validateAndroidTelephony() {
        JSONObject android = android();
        if (android == null || !android.containsKey("telephony")) {
            return;
        }
        Object telephonyRaw = android.get("telephony");
        if (!(telephonyRaw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.telephony must be a JSONObject");
        }
        JSONObject telephony = (JSONObject) telephonyRaw;
        for (String key : telephony.keySet()) {
            if (!isAllowedTelephonyTopLevelKey(key)) {
                throw new IllegalArgumentException("android.telephony." + key
                        + " is not an allowed key (phoneCount|slots|networkOperator|networkOperatorName|"
                        + "simOperator|simOperatorName|networkCountryIso|simCountryIso|"
                        + "dataNetworkType|dataState|dataActivity|phoneType|networkRoaming|cellInfo)");
            }
        }
        if (!telephony.containsKey("phoneCount")) {
            throw new IllegalArgumentException("android.telephony.phoneCount is required");
        }
        int phoneCount = requireExactJsonNumberIntField(telephony, "phoneCount",
                "android.telephony.phoneCount", 1, 8);

        if (!telephony.containsKey("slots")) {
            throw new IllegalArgumentException("android.telephony.slots is required");
        }
        Object slotsRaw = telephony.get("slots");
        if (!(slotsRaw instanceof JSONArray)) {
            throw new IllegalArgumentException("android.telephony.slots must be a JSON array");
        }
        JSONArray slots = (JSONArray) slotsRaw;
        Set<Integer> seenSlotIndexes = new HashSet<Integer>();
        for (int i = 0; i < slots.size(); i++) {
            String pathPrefix = "android.telephony.slots[" + i + "]";
            Object item = slots.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject slot = (JSONObject) item;
            for (String field : slot.keySet()) {
                if (!isAllowedTelephonySlotKey(field)) {
                    throw new IllegalArgumentException(pathPrefix + "." + field
                            + " is not an allowed field (slotIndex|imei|meid|deviceId|subscriberId|simSerialNumber|simState)");
                }
            }
            if (!slot.containsKey("slotIndex")) {
                throw new IllegalArgumentException(pathPrefix + ".slotIndex is required");
            }
            int slotIndex = requireExactJsonNumberIntField(slot, "slotIndex", pathPrefix + ".slotIndex",
                    0, phoneCount - 1);
            if (!seenSlotIndexes.add(slotIndex)) {
                throw new IllegalArgumentException(pathPrefix + ".slotIndex is not unique: " + slotIndex);
            }
            for (String idKey : TELEPHONY_SLOT_IDENTIFIER_KEYS) {
                if (slot.containsKey(idKey)) {
                    validateTelephonyIdentifierValue(slot.get(idKey), pathPrefix + "." + idKey);
                }
            }
            if (slot.containsKey("simState")) {
                // exact JSON Number integer 0..11; JSON null / String not allowed when present
                requireExactJsonNumberIntField(slot, "simState", pathPrefix + ".simState", 0, 11);
            }
        }
        for (String stringKey : TELEPHONY_GLOBAL_STRING_KEYS) {
            if (telephony.containsKey(stringKey)) {
                if (TELEPHONY_NETWORK_COUNTRY_ISO_KEY.equals(stringKey)
                        || TELEPHONY_SIM_COUNTRY_ISO_KEY.equals(stringKey)) {
                    validateTelephonyCountryIso(telephony.get(stringKey),
                            "android.telephony." + stringKey);
                } else {
                    validateTelephonyIdentifierValue(telephony.get(stringKey),
                            "android.telephony." + stringKey);
                }
            }
        }
        if (telephony.containsKey("dataNetworkType")) {
            requireExactJsonNumberIntField(telephony, "dataNetworkType",
                    "android.telephony.dataNetworkType", 0, 20);
        }
        if (telephony.containsKey("dataState")) {
            // TelephonyManager.DATA_UNKNOWN(-1)..DATA_DISCONNECTING and later values through 5
            requireExactJsonNumberIntField(telephony, "dataState",
                    "android.telephony.dataState", -1, 5);
        }
        if (telephony.containsKey("dataActivity")) {
            // TelephonyManager.DATA_ACTIVITY_NONE/IN/OUT/INOUT/DORMANT (0..4); independent of dataState
            requireExactJsonNumberIntField(telephony, "dataActivity",
                    "android.telephony.dataActivity", 0, 4);
        }
        if (telephony.containsKey("phoneType")) {
            requireExactJsonNumberIntField(telephony, "phoneType",
                    "android.telephony.phoneType", 0, 3);
        }
        if (telephony.containsKey("networkRoaming")) {
            Object roaming = telephony.get("networkRoaming");
            if (!(roaming instanceof Boolean)) {
                throw new IllegalArgumentException(
                        "android.telephony.networkRoaming must be a Boolean");
            }
        }
        validateAndroidCellInfo(telephony);
    }

    /**
     * Parse-time rules for optional {@code android.packages} when present.
     * Paths: {@code android.packages}, {@code android.packages[i]}, {@code android.packages[i].&lt;field&gt;}.
     * Materializes {@link #androidPackages}. Explicit empty array is configured.
     */
    private void validateAndroidPackages() {
        if (!isAndroidPackagesConfigured()) {
            this.androidPackages = Collections.emptyList();
            return;
        }
        JSONObject android = android();
        Object raw = android.get("packages");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("android.packages must be a JSON array");
        }
        JSONArray array = (JSONArray) raw;
        Set<String> seenNames = new HashSet<String>();
        List<PackageConfig> built = new ArrayList<PackageConfig>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String pathPrefix = "android.packages[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject pkg = (JSONObject) item;
            for (String key : pkg.keySet()) {
                if (!isAllowedAndroidPackagesKey(key)) {
                    throw new IllegalArgumentException(pathPrefix + "." + key
                            + " is not an allowed key (packageName|versionName|versionCode|"
                            + "sourceDir|dataDir|uid|enabled|systemApp|"
                            + "installerPackageName|initiatingPackageName|originatingPackageName|"
                            + "firstInstallTimeMillis|lastUpdateTimeMillis|permissions|"
                            + "signaturesHex|signingCertificateHistoryHex)");
                }
            }
            if (!pkg.containsKey("packageName")) {
                throw new IllegalArgumentException(pathPrefix + ".packageName is required");
            }
            String packageName = requireAndroidPackageName(pkg.get("packageName"),
                    pathPrefix + ".packageName");
            if (!seenNames.add(packageName)) {
                throw new IllegalArgumentException(pathPrefix + ".packageName is not unique: "
                        + packageName);
            }

            boolean versionNameConfigured = pkg.containsKey("versionName");
            String versionName = null;
            if (versionNameConfigured) {
                versionName = requireOptionalNullableNoTrimString(pkg.get("versionName"),
                        pathPrefix + ".versionName");
            }

            boolean versionCodeConfigured = pkg.containsKey("versionCode");
            Integer versionCode = null;
            if (versionCodeConfigured) {
                versionCode = requireExactJsonNumberIntField(pkg, "versionCode",
                        pathPrefix + ".versionCode", 0, Integer.MAX_VALUE);
            }

            boolean sourceDirConfigured = pkg.containsKey("sourceDir");
            String sourceDir = null;
            if (sourceDirConfigured) {
                sourceDir = requireOptionalNullableAbsolutePath(pkg.get("sourceDir"),
                        pathPrefix + ".sourceDir");
            }

            boolean dataDirConfigured = pkg.containsKey("dataDir");
            String dataDir = null;
            if (dataDirConfigured) {
                dataDir = requireOptionalNullableAbsolutePath(pkg.get("dataDir"),
                        pathPrefix + ".dataDir");
            }

            boolean uidConfigured = pkg.containsKey("uid");
            Integer uid = null;
            if (uidConfigured) {
                uid = requireExactJsonNumberIntField(pkg, "uid",
                        pathPrefix + ".uid", 0, Integer.MAX_VALUE);
            }

            boolean enabledConfigured = pkg.containsKey("enabled");
            Boolean enabled = null;
            if (enabledConfigured) {
                Object enabledRaw = pkg.get("enabled");
                if (!(enabledRaw instanceof Boolean)) {
                    throw new IllegalArgumentException(pathPrefix + ".enabled must be a Boolean");
                }
                enabled = (Boolean) enabledRaw;
            }

            boolean systemAppConfigured = pkg.containsKey("systemApp");
            Boolean systemApp = null;
            if (systemAppConfigured) {
                Object systemAppRaw = pkg.get("systemApp");
                if (!(systemAppRaw instanceof Boolean)) {
                    throw new IllegalArgumentException(pathPrefix + ".systemApp must be a Boolean");
                }
                systemApp = (Boolean) systemAppRaw;
            }

            boolean applicationFlagsConfigured = pkg.containsKey("applicationFlags");
            Integer applicationFlags = null;
            if (applicationFlagsConfigured) {
                applicationFlags = Integer.valueOf(requireExactJsonNumberIntField(pkg,
                        "applicationFlags", pathPrefix + ".applicationFlags",
                        Integer.MIN_VALUE, Integer.MAX_VALUE));
            }

            boolean installerPackageNameConfigured = pkg.containsKey("installerPackageName");
            String installerPackageName = null;
            if (installerPackageNameConfigured) {
                installerPackageName = requireOptionalNullableAndroidPackageName(
                        pkg.get("installerPackageName"), pathPrefix + ".installerPackageName");
            }

            boolean initiatingPackageNameConfigured = pkg.containsKey("initiatingPackageName");
            String initiatingPackageName = null;
            if (initiatingPackageNameConfigured) {
                initiatingPackageName = requireOptionalNullableAndroidPackageName(
                        pkg.get("initiatingPackageName"), pathPrefix + ".initiatingPackageName");
            }

            boolean originatingPackageNameConfigured = pkg.containsKey("originatingPackageName");
            String originatingPackageName = null;
            if (originatingPackageNameConfigured) {
                originatingPackageName = requireOptionalNullableAndroidPackageName(
                        pkg.get("originatingPackageName"), pathPrefix + ".originatingPackageName");
            }

            boolean firstInstallTimeMillisConfigured = pkg.containsKey("firstInstallTimeMillis");
            Long firstInstallTimeMillis = null;
            if (firstInstallTimeMillisConfigured) {
                firstInstallTimeMillis = requireExactJsonNumberLongField(pkg, "firstInstallTimeMillis",
                        pathPrefix + ".firstInstallTimeMillis", 0L, Long.MAX_VALUE);
            }

            boolean lastUpdateTimeMillisConfigured = pkg.containsKey("lastUpdateTimeMillis");
            Long lastUpdateTimeMillis = null;
            if (lastUpdateTimeMillisConfigured) {
                lastUpdateTimeMillis = requireExactJsonNumberLongField(pkg, "lastUpdateTimeMillis",
                        pathPrefix + ".lastUpdateTimeMillis", 0L, Long.MAX_VALUE);
            }

            if (firstInstallTimeMillisConfigured && lastUpdateTimeMillisConfigured
                    && lastUpdateTimeMillis.longValue() < firstInstallTimeMillis.longValue()) {
                throw new IllegalArgumentException(pathPrefix
                        + ".lastUpdateTimeMillis must be >= firstInstallTimeMillis");
            }

            boolean permissionsConfigured = pkg.containsKey("permissions");
            Map<String, Boolean> permissions = Collections.emptyMap();
            if (permissionsConfigured) {
                Object permissionsRaw = pkg.get("permissions");
                if (!(permissionsRaw instanceof JSONObject)) {
                    throw new IllegalArgumentException(pathPrefix + ".permissions must be a JSONObject");
                }
                JSONObject permissionsObj = (JSONObject) permissionsRaw;
                Map<String, Boolean> builtPermissions =
                        new LinkedHashMap<String, Boolean>(permissionsObj.size());
                for (String permissionKey : permissionsObj.keySet()) {
                    String permissionPath = pathPrefix + ".permissions." + permissionKey;
                    requirePermissionName(permissionKey, permissionPath);
                    Object grantedRaw = permissionsObj.get(permissionKey);
                    if (!(grantedRaw instanceof Boolean)) {
                        throw new IllegalArgumentException(permissionPath
                                + " must be a Boolean");
                    }
                    builtPermissions.put(permissionKey, (Boolean) grantedRaw);
                }
                permissions = Collections.unmodifiableMap(builtPermissions);
            }

            boolean signaturesConfigured = pkg.containsKey("signaturesHex");
            List<String> signatureHexes = Collections.emptyList();
            if (signaturesConfigured) {
                signatureHexes = parsePackageHexStringArray(pkg.get("signaturesHex"),
                        pathPrefix + ".signaturesHex");
            }

            boolean signingCertificateHistoryConfigured = pkg.containsKey("signingCertificateHistoryHex");
            List<String> signingCertificateHistoryHexes = Collections.emptyList();
            if (signingCertificateHistoryConfigured) {
                signingCertificateHistoryHexes = parsePackageHexStringArray(
                        pkg.get("signingCertificateHistoryHex"),
                        pathPrefix + ".signingCertificateHistoryHex");
            }

            built.add(new PackageConfig(packageName,
                    versionName, versionNameConfigured,
                    versionCode, versionCodeConfigured,
                    sourceDir, sourceDirConfigured,
                    dataDir, dataDirConfigured,
                    uid, uidConfigured,
                    enabled, enabledConfigured,
                    systemApp, systemAppConfigured,
                    applicationFlags, applicationFlagsConfigured,
                    installerPackageName, installerPackageNameConfigured,
                    initiatingPackageName, initiatingPackageNameConfigured,
                    originatingPackageName, originatingPackageNameConfigured,
                    firstInstallTimeMillis, firstInstallTimeMillisConfigured,
                    lastUpdateTimeMillis, lastUpdateTimeMillisConfigured,
                    permissions, permissionsConfigured,
                    signatureHexes, signaturesConfigured,
                    signingCertificateHistoryHexes, signingCertificateHistoryConfigured));
        }
        this.androidPackages = Collections.unmodifiableList(built);
    }

    /**
     * Parse a JSON string array of signature/history hex values with the same strict rules as
     * {@code signaturesHex}: non-empty even-length pure hex, lowercased, ordered, unique after normalize.
     */
    private static List<String> parsePackageHexStringArray(Object raw, String fieldPath) {
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException(fieldPath + " must be a JSON array");
        }
        JSONArray array = (JSONArray) raw;
        Set<String> seenHexes = new HashSet<String>();
        List<String> builtHexes = new ArrayList<String>(array.size());
        for (int s = 0; s < array.size(); s++) {
            String hexPath = fieldPath + "[" + s + "]";
            String hex = requirePackageSignatureHex(array.get(s), hexPath);
            if (!seenHexes.add(hex)) {
                throw new IllegalArgumentException(hexPath + " is not unique: " + hex);
            }
            builtHexes.add(hex);
        }
        return Collections.unmodifiableList(builtHexes);
    }

    private static boolean isAllowedAndroidPackagesKey(String key) {
        for (String allowed : ANDROID_PACKAGES_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse-time rules for optional {@code android.features} when present.
     * Paths: {@code android.features}, {@code android.features[i]}, {@code android.features[i].&lt;field&gt;}.
     * Materializes {@link #androidFeatures}. Explicit empty array is configured.
     */
    private void validateAndroidFeatures() {
        if (!isAndroidFeaturesConfigured()) {
            this.androidFeatures = Collections.emptyList();
            return;
        }
        JSONObject android = android();
        Object raw = android.get("features");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("android.features must be a JSON array");
        }
        JSONArray array = (JSONArray) raw;
        Set<String> seenNames = new HashSet<String>();
        List<FeatureConfig> built = new ArrayList<FeatureConfig>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String pathPrefix = "android.features[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject feature = (JSONObject) item;
            for (String key : feature.keySet()) {
                if (!isAllowedAndroidFeaturesKey(key)) {
                    throw new IllegalArgumentException(pathPrefix + "." + key
                            + " is not an allowed key (name|version)");
                }
            }
            if (!feature.containsKey("name")) {
                throw new IllegalArgumentException(pathPrefix + ".name is required");
            }
            String name = requireAndroidPackageName(feature.get("name"), pathPrefix + ".name");
            if (!seenNames.add(name)) {
                throw new IllegalArgumentException(pathPrefix + ".name is not unique: " + name);
            }

            boolean versionConfigured = feature.containsKey("version");
            Integer version = null;
            if (versionConfigured) {
                version = requireExactJsonNumberIntField(feature, "version",
                        pathPrefix + ".version", 0, Integer.MAX_VALUE);
            }

            built.add(new FeatureConfig(name, version, versionConfigured));
        }
        this.androidFeatures = Collections.unmodifiableList(built);
    }

    private static boolean isAllowedAndroidFeaturesKey(String key) {
        for (String allowed : ANDROID_FEATURES_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse-time rules for optional {@code android.tee} when present.
     * Missing node leaves {@link #isAndroidTeeConfigured()} false; explicit empty object is configured.
     * Materializes typed fields; never retains JSONObject.
     */
    private void validateAndroidTee() {
        JSONObject android = android();
        if (android == null || !android.containsKey("tee")) {
            this.androidTeeConfigured = false;
            return;
        }
        Object raw = android.get("tee");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("android.tee must be a JSONObject");
        }
        JSONObject tee = (JSONObject) raw;
        this.androidTeeConfigured = true;
        for (String key : tee.keySet()) {
            if (!isAllowedAndroidTeeKey(key)) {
                throw new IllegalArgumentException("android.tee." + key
                        + " is not an allowed key (available|securityLevel|keymasterVersion|"
                        + "strongBoxAvailable|marker|keyBlobHex|keyAlgorithm|keyFormat)");
            }
        }

        if (tee.containsKey("available")) {
            Object availableRaw = tee.get("available");
            if (!(availableRaw instanceof Boolean)) {
                throw new IllegalArgumentException("android.tee.available must be a Boolean");
            }
            this.teeAvailable = (Boolean) availableRaw;
            this.teeAvailableConfigured = true;
        }

        if (tee.containsKey("securityLevel")) {
            Object levelRaw = tee.get("securityLevel");
            if (!(levelRaw instanceof String)) {
                throw new IllegalArgumentException(
                        "android.tee.securityLevel must be a String enum "
                                + "(SOFTWARE|TRUSTED_ENVIRONMENT|STRONGBOX)");
            }
            String level = (String) levelRaw;
            if (!ANDROID_TEE_SECURITY_LEVELS.contains(level)) {
                throw new IllegalArgumentException(
                        "android.tee.securityLevel must be SOFTWARE|TRUSTED_ENVIRONMENT|STRONGBOX");
            }
            this.teeSecurityLevel = level;
            this.teeSecurityLevelConfigured = true;
        }

        if (tee.containsKey("keymasterVersion")) {
            this.teeKeymasterVersion = requireExactJsonNumberIntField(tee, "keymasterVersion",
                    "android.tee.keymasterVersion", 0, 100);
            this.teeKeymasterVersionConfigured = true;
        }

        if (tee.containsKey("strongBoxAvailable")) {
            Object strongBoxRaw = tee.get("strongBoxAvailable");
            if (!(strongBoxRaw instanceof Boolean)) {
                throw new IllegalArgumentException("android.tee.strongBoxAvailable must be a Boolean");
            }
            this.teeStrongBoxAvailable = (Boolean) strongBoxRaw;
            this.teeStrongBoxAvailableConfigured = true;
        }

        if (tee.containsKey("marker")) {
            Object markerRaw = tee.get("marker");
            if (!(markerRaw instanceof String)) {
                throw new IllegalArgumentException("android.tee.marker must be a String");
            }
            String marker = (String) markerRaw;
            if (marker.isEmpty() || !marker.equals(marker.trim())) {
                throw new IllegalArgumentException(
                        "android.tee.marker must be a non-empty String without leading or trailing whitespace");
            }
            if (marker.length() > 128) {
                throw new IllegalArgumentException(
                        "android.tee.marker length must be <= 128 characters");
            }
            this.teeMarker = marker;
            this.teeMarkerConfigured = true;
        }

        if (tee.containsKey("keyBlobHex")) {
            Object blobRaw = tee.get("keyBlobHex");
            if (!(blobRaw instanceof String)) {
                throw new IllegalArgumentException(
                        "android.tee.keyBlobHex must be a non-empty even-length hex String");
            }
            String hex = (String) blobRaw;
            if (hex.isEmpty() || (hex.length() & 1) != 0) {
                throw new IllegalArgumentException(
                        "android.tee.keyBlobHex must be a non-empty even-length hex String");
            }
            if (hex.length() > ANDROID_TEE_KEY_BLOB_HEX_MAX_CHARS) {
                throw new IllegalArgumentException(
                        "android.tee.keyBlobHex length must be <= " + ANDROID_TEE_KEY_BLOB_HEX_MAX_CHARS
                                + " hex characters");
            }
            for (int i = 0; i < hex.length(); i++) {
                if (Character.digit(hex.charAt(i), 16) < 0) {
                    throw new IllegalArgumentException(
                            "android.tee.keyBlobHex must contain only hexadecimal characters");
                }
            }
            String lower = hex.toLowerCase(Locale.ROOT);
            int n = lower.length() / 2;
            byte[] data = new byte[n];
            for (int i = 0; i < n; i++) {
                int high = Character.digit(lower.charAt(i * 2), 16);
                int low = Character.digit(lower.charAt(i * 2 + 1), 16);
                data[i] = (byte) ((high << 4) | low);
            }
            this.teeKeyBlob = data;
            this.teeKeyBlobConfigured = true;
        }

        if (tee.containsKey("keyAlgorithm")) {
            this.teeKeyAlgorithm = requireAndroidTeeKeyMetaString(tee.get("keyAlgorithm"),
                    "android.tee.keyAlgorithm");
            this.teeKeyAlgorithmConfigured = true;
        }

        if (tee.containsKey("keyFormat")) {
            this.teeKeyFormat = requireAndroidTeeKeyMetaString(tee.get("keyFormat"),
                    "android.tee.keyFormat");
            this.teeKeyFormatConfigured = true;
        }
    }

    /**
     * {@code keyAlgorithm}/{@code keyFormat}: non-empty String, no leading/trailing whitespace,
     * length {@code 1..128}. Never inferred from marker, securityLevel, keyBlobHex, or each other.
     */
    private static String requireAndroidTeeKeyMetaString(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path + " must be a String");
        }
        String value = (String) raw;
        if (value.isEmpty() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(
                    path + " must be a non-empty String without leading or trailing whitespace");
        }
        if (value.length() > 128) {
            throw new IllegalArgumentException(path + " length must be <= 128 characters");
        }
        return value;
    }

    private static boolean isAllowedAndroidTeeKey(String key) {
        for (String allowed : ANDROID_TEE_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse-time rules for optional root {@code filesystem} when present.
     * Allowed keys: {@code stat}, {@code statfs}, {@code mounts}, {@code links},
     * {@code externalStorage}, {@code systemDirectories}. Materializes lists/objects independently
     * — missing one sub-key does not skip others.
     * {@code stat}/{@code statfs}/{@code links} are path-keyed JSONObjects; {@code mounts} is a
     * JSONArray; {@code externalStorage}/{@code systemDirectories} are JSONObjects.
     * Missing {@code filesystem} or missing sub-key leaves that list/object unconfigured;
     * explicit empty object/array is configured (lists empty; externalStorage uses defaults;
     * systemDirectories has no field defaults).
     * Does not create host directories or FileIO entries.
     */
    private void validateFilesystem() {
        if (!root.containsKey("filesystem")) {
            this.filesystemStats = Collections.emptyList();
            this.filesystemStatFsEntries = Collections.emptyList();
            this.filesystemMounts = Collections.emptyList();
            this.filesystemLinks = Collections.emptyList();
            this.filesystemExternalStorageConfigured = false;
            this.filesystemExternalStorageConfig = null;
            this.filesystemSystemDirectoriesConfigured = false;
            this.filesystemSystemDirectoriesConfig = null;
            this.filesystemDirectoriesConfigured = false;
            this.filesystemDirectories = Collections.emptyMap();
            return;
        }
        Object filesystemRaw = root.get("filesystem");
        if (!(filesystemRaw instanceof JSONObject)) {
            throw new IllegalArgumentException("filesystem must be a JSONObject");
        }
        JSONObject filesystem = (JSONObject) filesystemRaw;
        for (String key : filesystem.keySet()) {
            if (!isAllowedFilesystemKey(key)) {
                throw new IllegalArgumentException("filesystem." + key
                        + " is not an allowed key"
                        + " (stat|statfs|mounts|links|externalStorage|systemDirectories|directories)");
            }
        }
        validateFilesystemStat(filesystem);
        validateFilesystemStatFs(filesystem);
        validateFilesystemMounts(filesystem);
        validateFilesystemLinks(filesystem);
        validateFilesystemExternalStorage(filesystem);
        validateFilesystemSystemDirectories(filesystem);
        validateFilesystemDirectories(filesystem);
    }

    private void validateFilesystemStat(JSONObject filesystem) {
        if (!filesystem.containsKey("stat")) {
            this.filesystemStats = Collections.emptyList();
            return;
        }
        Object raw = filesystem.get("stat");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("filesystem.stat must be a JSONObject (path-keyed map)");
        }
        JSONObject statMap = (JSONObject) raw;
        List<FileStatConfig> built = new ArrayList<FileStatConfig>(statMap.size());
        Set<String> seenNormalizedPaths = new HashSet<String>();
        for (String pathKey : statMap.keySet()) {
            String pathPrefix = "filesystem.stat[\"" + pathKey + "\"]";
            // POSIX absolute path normalize; store normalized path only.
            String path = normalizePosixAbsolutePath(pathKey, pathPrefix);
            if (!seenNormalizedPaths.add(path)) {
                throw new IllegalArgumentException(pathPrefix
                        + " normalizes to a duplicate path: " + path);
            }
            Object entryRaw = statMap.get(pathKey);
            if (!(entryRaw instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject entry = (JSONObject) entryRaw;
            if (entry.isEmpty()) {
                throw new IllegalArgumentException(pathPrefix
                        + " must contain at least one stat field");
            }
            for (String field : entry.keySet()) {
                if (!isAllowedFilesystemStatKey(field)) {
                    throw new IllegalArgumentException(pathPrefix + "." + field
                            + " is not an allowed key (device|inode|mode|uid|gid|size|blockSize|blocks|"
                            + "atimeMillis|mtimeMillis|ctimeMillis)");
                }
            }

            // device / inode / mode / uid / gid / size / blocks / *Millis: exact non-negative long
            // blockSize: exact int in 1..Integer.MAX_VALUE
            boolean deviceConfigured = entry.containsKey("device");
            Long device = null;
            if (deviceConfigured) {
                device = requireExactJsonNumberLongField(entry, "device", pathPrefix + ".device",
                        0L, Long.MAX_VALUE);
            }
            boolean inodeConfigured = entry.containsKey("inode");
            Long inode = null;
            if (inodeConfigured) {
                inode = requireExactJsonNumberLongField(entry, "inode", pathPrefix + ".inode",
                        0L, Long.MAX_VALUE);
            }
            boolean modeConfigured = entry.containsKey("mode");
            Long mode = null;
            if (modeConfigured) {
                mode = requireExactJsonNumberLongField(entry, "mode", pathPrefix + ".mode",
                        0L, 0xffffffffL);
            }
            boolean uidConfigured = entry.containsKey("uid");
            Long uid = null;
            if (uidConfigured) {
                uid = requireExactJsonNumberLongField(entry, "uid", pathPrefix + ".uid",
                        0L, 0xffffffffL);
            }
            boolean gidConfigured = entry.containsKey("gid");
            Long gid = null;
            if (gidConfigured) {
                gid = requireExactJsonNumberLongField(entry, "gid", pathPrefix + ".gid",
                        0L, 0xffffffffL);
            }
            boolean sizeConfigured = entry.containsKey("size");
            Long size = null;
            if (sizeConfigured) {
                size = requireExactJsonNumberLongField(entry, "size", pathPrefix + ".size",
                        0L, Long.MAX_VALUE);
            }
            boolean blockSizeConfigured = entry.containsKey("blockSize");
            Integer blockSize = null;
            if (blockSizeConfigured) {
                blockSize = requireExactJsonNumberIntField(entry, "blockSize",
                        pathPrefix + ".blockSize", 1, Integer.MAX_VALUE);
            }
            boolean blocksConfigured = entry.containsKey("blocks");
            Long blocks = null;
            if (blocksConfigured) {
                blocks = requireExactJsonNumberLongField(entry, "blocks", pathPrefix + ".blocks",
                        0L, Long.MAX_VALUE);
            }
            boolean atimeMillisConfigured = entry.containsKey("atimeMillis");
            Long atimeMillis = null;
            if (atimeMillisConfigured) {
                atimeMillis = requireExactJsonNumberLongField(entry, "atimeMillis",
                        pathPrefix + ".atimeMillis", Long.MIN_VALUE, Long.MAX_VALUE);
            }
            boolean mtimeMillisConfigured = entry.containsKey("mtimeMillis");
            Long mtimeMillis = null;
            if (mtimeMillisConfigured) {
                mtimeMillis = requireExactJsonNumberLongField(entry, "mtimeMillis",
                        pathPrefix + ".mtimeMillis", Long.MIN_VALUE, Long.MAX_VALUE);
            }
            boolean ctimeMillisConfigured = entry.containsKey("ctimeMillis");
            Long ctimeMillis = null;
            if (ctimeMillisConfigured) {
                ctimeMillis = requireExactJsonNumberLongField(entry, "ctimeMillis",
                        pathPrefix + ".ctimeMillis", Long.MIN_VALUE, Long.MAX_VALUE);
            }

            built.add(new FileStatConfig(path,
                    device, deviceConfigured,
                    inode, inodeConfigured,
                    mode, modeConfigured,
                    uid, uidConfigured,
                    gid, gidConfigured,
                    size, sizeConfigured,
                    blockSize, blockSizeConfigured,
                    blocks, blocksConfigured,
                    atimeMillis, atimeMillisConfigured,
                    mtimeMillis, mtimeMillisConfigured,
                    ctimeMillis, ctimeMillisConfigured));
        }
        this.filesystemStats = Collections.unmodifiableList(built);
    }

    private void validateFilesystemStatFs(JSONObject filesystem) {
        if (!filesystem.containsKey("statfs")) {
            this.filesystemStatFsEntries = Collections.emptyList();
            return;
        }
        Object raw = filesystem.get("statfs");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException(
                    "filesystem.statfs must be a JSONObject (mount-point-keyed map)");
        }
        JSONObject statfsMap = (JSONObject) raw;
        List<FileStatFsConfig> built = new ArrayList<FileStatFsConfig>(statfsMap.size());
        Set<String> seenNormalizedMounts = new HashSet<String>();
        for (String mountKey : statfsMap.keySet()) {
            String pathPrefix = "filesystem.statfs[\"" + mountKey + "\"]";
            String mountPoint = normalizePosixAbsolutePath(mountKey, pathPrefix);
            if (!seenNormalizedMounts.add(mountPoint)) {
                throw new IllegalArgumentException(pathPrefix
                        + " normalizes to a duplicate path: " + mountPoint);
            }
            Object entryRaw = statfsMap.get(mountKey);
            if (!(entryRaw instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject entry = (JSONObject) entryRaw;
            if (entry.isEmpty()) {
                throw new IllegalArgumentException(pathPrefix
                        + " must contain at least one statfs field");
            }
            for (String field : entry.keySet()) {
                if (!isAllowedFilesystemStatFsKey(field)) {
                    throw new IllegalArgumentException(pathPrefix + "." + field
                            + " is not an allowed key (type|blockSize|blocks|blocksFree|blocksAvailable|"
                            + "files|filesFree|fsid|nameLength|fragmentSize|flags)");
                }
            }

            boolean typeConfigured = entry.containsKey("type");
            Long type = null;
            if (typeConfigured) {
                type = requireExactJsonNumberLongField(entry, "type", pathPrefix + ".type",
                        0L, 0xffffffffL);
            }
            boolean blockSizeConfigured = entry.containsKey("blockSize");
            Integer blockSize = null;
            if (blockSizeConfigured) {
                blockSize = requireExactJsonNumberIntField(entry, "blockSize",
                        pathPrefix + ".blockSize", 1, Integer.MAX_VALUE);
            }
            boolean blocksConfigured = entry.containsKey("blocks");
            Long blocks = null;
            if (blocksConfigured) {
                blocks = requireExactJsonNumberLongField(entry, "blocks", pathPrefix + ".blocks",
                        0L, Long.MAX_VALUE);
            }
            boolean blocksFreeConfigured = entry.containsKey("blocksFree");
            Long blocksFree = null;
            if (blocksFreeConfigured) {
                blocksFree = requireExactJsonNumberLongField(entry, "blocksFree",
                        pathPrefix + ".blocksFree", 0L, Long.MAX_VALUE);
            }
            boolean blocksAvailableConfigured = entry.containsKey("blocksAvailable");
            Long blocksAvailable = null;
            if (blocksAvailableConfigured) {
                blocksAvailable = requireExactJsonNumberLongField(entry, "blocksAvailable",
                        pathPrefix + ".blocksAvailable", 0L, Long.MAX_VALUE);
            }
            boolean filesConfigured = entry.containsKey("files");
            Long files = null;
            if (filesConfigured) {
                files = requireExactJsonNumberLongField(entry, "files", pathPrefix + ".files",
                        0L, Long.MAX_VALUE);
            }
            boolean filesFreeConfigured = entry.containsKey("filesFree");
            Long filesFree = null;
            if (filesFreeConfigured) {
                filesFree = requireExactJsonNumberLongField(entry, "filesFree",
                        pathPrefix + ".filesFree", 0L, Long.MAX_VALUE);
            }
            boolean fsidConfigured = entry.containsKey("fsid");
            long[] fsid = null;
            if (fsidConfigured) {
                fsid = requireExactFsidArray(entry.get("fsid"), pathPrefix + ".fsid");
            }
            boolean nameLengthConfigured = entry.containsKey("nameLength");
            Integer nameLength = null;
            if (nameLengthConfigured) {
                nameLength = requireExactJsonNumberIntField(entry, "nameLength",
                        pathPrefix + ".nameLength", 1, Integer.MAX_VALUE);
            }
            boolean fragmentSizeConfigured = entry.containsKey("fragmentSize");
            Integer fragmentSize = null;
            if (fragmentSizeConfigured) {
                fragmentSize = requireExactJsonNumberIntField(entry, "fragmentSize",
                        pathPrefix + ".fragmentSize", 1, Integer.MAX_VALUE);
            }
            boolean flagsConfigured = entry.containsKey("flags");
            Long flags = null;
            if (flagsConfigured) {
                flags = requireExactJsonNumberLongField(entry, "flags", pathPrefix + ".flags",
                        0L, 0xffffffffL);
            }

            built.add(new FileStatFsConfig(mountPoint,
                    type, typeConfigured,
                    blockSize, blockSizeConfigured,
                    blocks, blocksConfigured,
                    blocksFree, blocksFreeConfigured,
                    blocksAvailable, blocksAvailableConfigured,
                    files, filesConfigured,
                    filesFree, filesFreeConfigured,
                    fsid, fsidConfigured,
                    nameLength, nameLengthConfigured,
                    fragmentSize, fragmentSizeConfigured,
                    flags, flagsConfigured));
        }
        this.filesystemStatFsEntries = Collections.unmodifiableList(built);
    }

    private void validateFilesystemMounts(JSONObject filesystem) {
        if (!filesystem.containsKey("mounts")) {
            this.filesystemMounts = Collections.emptyList();
            return;
        }
        Object raw = filesystem.get("mounts");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("filesystem.mounts must be a JSONArray");
        }
        JSONArray array = (JSONArray) raw;
        List<FileSystemMountConfig> built = new ArrayList<FileSystemMountConfig>(array.size());
        Set<String> seenTargets = new HashSet<String>();
        // Only mountinfo-complete entries participate (all four of mountId/parentId/major/minor).
        Set<Integer> seenMountIds = new HashSet<Integer>();
        for (int i = 0; i < array.size(); i++) {
            String pathPrefix = "filesystem.mounts[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject entry = (JSONObject) item;
            if (entry.isEmpty()) {
                throw new IllegalArgumentException(pathPrefix
                        + " must be a non-empty JSONObject");
            }
            for (String field : entry.keySet()) {
                if (!isAllowedFilesystemMountsKey(field)) {
                    throw new IllegalArgumentException(pathPrefix + "." + field
                            + " is not an allowed key (source|target|fileSystemType|options|dump|pass|"
                            + "mountId|parentId|major|minor|root|mountOptions|optionalFields|superOptions)");
                }
            }

            if (!entry.containsKey("source")) {
                throw new IllegalArgumentException(pathPrefix + ".source is required");
            }
            String source = requireMountSourceString(entry.get("source"), pathPrefix + ".source");

            if (!entry.containsKey("target")) {
                throw new IllegalArgumentException(pathPrefix + ".target is required");
            }
            Object targetRaw = entry.get("target");
            if (!(targetRaw instanceof String)) {
                throw new IllegalArgumentException(pathPrefix + ".target must be an absolute path String");
            }
            String target = normalizePosixAbsolutePath((String) targetRaw, pathPrefix + ".target");
            if (!seenTargets.add(target)) {
                throw new IllegalArgumentException(pathPrefix + ".target normalizes to a duplicate path: "
                        + target);
            }

            if (!entry.containsKey("fileSystemType")) {
                throw new IllegalArgumentException(pathPrefix + ".fileSystemType is required");
            }
            String fileSystemType = requireMountTokenString(entry.get("fileSystemType"),
                    pathPrefix + ".fileSystemType");

            if (!entry.containsKey("options")) {
                throw new IllegalArgumentException(pathPrefix + ".options is required");
            }
            String options = requireMountOptionsString(entry.get("options"), pathPrefix + ".options");

            boolean dumpConfigured = entry.containsKey("dump");
            int dump = 0;
            if (dumpConfigured) {
                dump = requireExactJsonNumberIntField(entry, "dump", pathPrefix + ".dump",
                        0, Integer.MAX_VALUE);
            }

            boolean passConfigured = entry.containsKey("pass");
            int pass = 0;
            if (passConfigured) {
                pass = requireExactJsonNumberIntField(entry, "pass", pathPrefix + ".pass",
                        0, Integer.MAX_VALUE);
            }

            boolean hasMountId = entry.containsKey("mountId");
            boolean hasParentId = entry.containsKey("parentId");
            boolean hasMajor = entry.containsKey("major");
            boolean hasMinor = entry.containsKey("minor");
            int mountInfoCount = (hasMountId ? 1 : 0) + (hasParentId ? 1 : 0)
                    + (hasMajor ? 1 : 0) + (hasMinor ? 1 : 0);
            if (mountInfoCount != 0 && mountInfoCount != 4) {
                throw new IllegalArgumentException(pathPrefix
                        + " mountinfo fields mountId|parentId|major|minor must all be present or all absent");
            }
            boolean mountInfoConfigured = mountInfoCount == 4;
            Integer mountId = null;
            Integer parentId = null;
            Integer major = null;
            Integer minor = null;
            if (mountInfoConfigured) {
                mountId = requireExactJsonNumberIntField(entry, "mountId", pathPrefix + ".mountId",
                        1, Integer.MAX_VALUE);
                if (!seenMountIds.add(mountId)) {
                    throw new IllegalArgumentException(pathPrefix + ".mountId is a duplicate: "
                            + mountId);
                }
                parentId = requireExactJsonNumberIntField(entry, "parentId", pathPrefix + ".parentId",
                        1, Integer.MAX_VALUE);
                major = requireExactJsonNumberIntField(entry, "major", pathPrefix + ".major",
                        0, Integer.MAX_VALUE);
                minor = requireExactJsonNumberIntField(entry, "minor", pathPrefix + ".minor",
                        0, Integer.MAX_VALUE);
            }

            boolean rootConfigured = entry.containsKey("root");
            String root;
            if (rootConfigured) {
                Object rootRaw = entry.get("root");
                if (!(rootRaw instanceof String)) {
                    throw new IllegalArgumentException(pathPrefix + ".root must be an absolute path String");
                }
                root = normalizePosixAbsolutePath((String) rootRaw, pathPrefix + ".root");
            } else {
                root = "/";
            }

            boolean mountOptionsConfigured = entry.containsKey("mountOptions");
            String mountOptions;
            if (mountOptionsConfigured) {
                mountOptions = requireMountOptionsString(entry.get("mountOptions"),
                        pathPrefix + ".mountOptions");
            } else {
                mountOptions = options;
            }

            boolean optionalFieldsConfigured = entry.containsKey("optionalFields");
            List<String> optionalFields;
            if (optionalFieldsConfigured) {
                optionalFields = requireMountOptionalFieldsArray(entry.get("optionalFields"),
                        pathPrefix + ".optionalFields");
            } else {
                optionalFields = Collections.emptyList();
            }

            boolean superOptionsConfigured = entry.containsKey("superOptions");
            String superOptions;
            if (superOptionsConfigured) {
                superOptions = requireMountOptionsString(entry.get("superOptions"),
                        pathPrefix + ".superOptions");
            } else {
                superOptions = options;
            }

            built.add(new FileSystemMountConfig(
                    source, target, fileSystemType, options,
                    dump, dumpConfigured,
                    pass, passConfigured,
                    mountId, parentId, major, minor, mountInfoConfigured,
                    root, rootConfigured,
                    mountOptions, mountOptionsConfigured,
                    optionalFields, optionalFieldsConfigured,
                    superOptions, superOptionsConfigured));
        }
        this.filesystemMounts = Collections.unmodifiableList(built);
    }

    private void validateFilesystemLinks(JSONObject filesystem) {
        if (!filesystem.containsKey("links")) {
            this.filesystemLinks = Collections.emptyList();
            return;
        }
        Object raw = filesystem.get("links");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException(
                    "filesystem.links must be a JSONObject (path-keyed map)");
        }
        JSONObject linkMap = (JSONObject) raw;
        List<FileSystemLinkConfig> built = new ArrayList<FileSystemLinkConfig>(linkMap.size());
        Set<String> seenPaths = new HashSet<String>();
        for (String pathKey : linkMap.keySet()) {
            String pathPrefix = "filesystem.links[\"" + pathKey + "\"]";
            String path = normalizePosixAbsolutePath(pathKey, pathPrefix);
            if (!seenPaths.add(path)) {
                throw new IllegalArgumentException(pathPrefix
                        + " normalizes to a duplicate path: " + path);
            }
            Object targetRaw = linkMap.get(pathKey);
            String target = requireLinkTargetString(targetRaw, pathPrefix);
            built.add(new FileSystemLinkConfig(path, target));
        }
        this.filesystemLinks = Collections.unmodifiableList(built);
    }

    /**
     * Parse-time rules for optional {@code filesystem.externalStorage} when present.
     * Missing key leaves {@link #isFilesystemExternalStorageConfigured()} false; explicit empty
     * object is configured with defaults {@code directory=/storage/emulated/0},
     * {@code state=mounted}, {@code emulated=true}, {@code removable=false}.
     * Materializes {@link FileSystemExternalStorageConfig}; never retains JSONObject.
     * JNI wiring is in {@code AbstractJni}; does not create host directories or FileIO entries.
     * {@code Environment.getStorageDirectory} is configured under
     * {@code filesystem.systemDirectories}, not here.
     */
    private void validateFilesystemExternalStorage(JSONObject filesystem) {
        if (!filesystem.containsKey("externalStorage")) {
            this.filesystemExternalStorageConfigured = false;
            this.filesystemExternalStorageConfig = null;
            return;
        }
        Object raw = filesystem.get("externalStorage");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException(
                    "filesystem.externalStorage must be a JSONObject");
        }
        JSONObject externalStorage = (JSONObject) raw;
        for (String key : externalStorage.keySet()) {
            if (!isAllowedFilesystemExternalStorageKey(key)) {
                throw new IllegalArgumentException("filesystem.externalStorage." + key
                        + " is not an allowed key (directory|state|emulated|removable)");
            }
        }

        boolean directoryConfigured = externalStorage.containsKey("directory");
        String directory = FILESYSTEM_EXTERNAL_STORAGE_DEFAULT_DIRECTORY;
        if (directoryConfigured) {
            Object directoryRaw = externalStorage.get("directory");
            if (!(directoryRaw instanceof String)) {
                throw new IllegalArgumentException(
                        "filesystem.externalStorage.directory must be a String");
            }
            directory = normalizePosixAbsolutePath((String) directoryRaw,
                    "filesystem.externalStorage.directory");
        }

        boolean stateConfigured = externalStorage.containsKey("state");
        String state = FILESYSTEM_EXTERNAL_STORAGE_DEFAULT_STATE;
        if (stateConfigured) {
            Object stateRaw = externalStorage.get("state");
            if (!(stateRaw instanceof String)) {
                throw new IllegalArgumentException(
                        "filesystem.externalStorage.state must be a String");
            }
            state = (String) stateRaw;
            if (!FILESYSTEM_EXTERNAL_STORAGE_STATES.contains(state)) {
                throw new IllegalArgumentException(
                        "filesystem.externalStorage.state must be one of "
                                + "unknown|removed|unmounted|checking|nofs|mounted|"
                                + "mounted_ro|shared|bad_removal|unmountable");
            }
        }

        boolean emulatedConfigured = externalStorage.containsKey("emulated");
        boolean emulated = FILESYSTEM_EXTERNAL_STORAGE_DEFAULT_EMULATED;
        if (emulatedConfigured) {
            emulated = requireAndroidPowerBoolean(externalStorage.get("emulated"),
                    "filesystem.externalStorage.emulated");
        }

        boolean removableConfigured = externalStorage.containsKey("removable");
        boolean removable = FILESYSTEM_EXTERNAL_STORAGE_DEFAULT_REMOVABLE;
        if (removableConfigured) {
            removable = requireAndroidPowerBoolean(externalStorage.get("removable"),
                    "filesystem.externalStorage.removable");
        }

        this.filesystemExternalStorageConfigured = true;
        this.filesystemExternalStorageConfig = new FileSystemExternalStorageConfig(
                directory, directoryConfigured,
                state, stateConfigured,
                emulated, emulatedConfigured,
                removable, removableConfigured);
    }

    private static boolean isAllowedFilesystemExternalStorageKey(String key) {
        for (String allowed : FILESYSTEM_EXTERNAL_STORAGE_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse-time rules for optional {@code filesystem.systemDirectories} when present.
     * Missing key leaves {@link #isFilesystemSystemDirectoriesConfigured()} false; explicit empty
     * object is configured with all path fields unconfigured (JNI keeps historical UOE fallback).
     * Each of {@code rootDirectory}/{@code dataDirectory}/{@code downloadCacheDirectory}/
     * {@code storageDirectory} is an optional non-null String POSIX absolute path (same normalize
     * as {@code filesystem.externalStorage.directory}). No defaults; no host directory / FileIO.
     * Materializes {@link FileSystemSystemDirectoriesConfig}; never retains JSONObject.
     */
    private void validateFilesystemSystemDirectories(JSONObject filesystem) {
        if (!filesystem.containsKey("systemDirectories")) {
            this.filesystemSystemDirectoriesConfigured = false;
            this.filesystemSystemDirectoriesConfig = null;
            return;
        }
        Object raw = filesystem.get("systemDirectories");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException(
                    "filesystem.systemDirectories must be a JSONObject");
        }
        JSONObject systemDirectories = (JSONObject) raw;
        for (String key : systemDirectories.keySet()) {
            if (!isAllowedFilesystemSystemDirectoriesKey(key)) {
                throw new IllegalArgumentException("filesystem.systemDirectories." + key
                        + " is not an allowed key"
                        + " (rootDirectory|dataDirectory|downloadCacheDirectory|storageDirectory)");
            }
        }

        boolean rootConfigured = systemDirectories.containsKey("rootDirectory");
        String rootDirectory = null;
        if (rootConfigured) {
            Object rootRaw = systemDirectories.get("rootDirectory");
            if (!(rootRaw instanceof String)) {
                throw new IllegalArgumentException(
                        "filesystem.systemDirectories.rootDirectory must be a String");
            }
            rootDirectory = normalizePosixAbsolutePath((String) rootRaw,
                    "filesystem.systemDirectories.rootDirectory");
        }

        boolean dataConfigured = systemDirectories.containsKey("dataDirectory");
        String dataDirectory = null;
        if (dataConfigured) {
            Object dataRaw = systemDirectories.get("dataDirectory");
            if (!(dataRaw instanceof String)) {
                throw new IllegalArgumentException(
                        "filesystem.systemDirectories.dataDirectory must be a String");
            }
            dataDirectory = normalizePosixAbsolutePath((String) dataRaw,
                    "filesystem.systemDirectories.dataDirectory");
        }

        boolean downloadConfigured = systemDirectories.containsKey("downloadCacheDirectory");
        String downloadCacheDirectory = null;
        if (downloadConfigured) {
            Object downloadRaw = systemDirectories.get("downloadCacheDirectory");
            if (!(downloadRaw instanceof String)) {
                throw new IllegalArgumentException(
                        "filesystem.systemDirectories.downloadCacheDirectory must be a String");
            }
            downloadCacheDirectory = normalizePosixAbsolutePath((String) downloadRaw,
                    "filesystem.systemDirectories.downloadCacheDirectory");
        }

        boolean storageConfigured = systemDirectories.containsKey("storageDirectory");
        String storageDirectory = null;
        if (storageConfigured) {
            Object storageRaw = systemDirectories.get("storageDirectory");
            if (!(storageRaw instanceof String)) {
                throw new IllegalArgumentException(
                        "filesystem.systemDirectories.storageDirectory must be a String");
            }
            storageDirectory = normalizePosixAbsolutePath((String) storageRaw,
                    "filesystem.systemDirectories.storageDirectory");
        }

        this.filesystemSystemDirectoriesConfigured = true;
        this.filesystemSystemDirectoriesConfig = new FileSystemSystemDirectoriesConfig(
                rootDirectory, rootConfigured,
                dataDirectory, dataConfigured,
                downloadCacheDirectory, downloadConfigured,
                storageDirectory, storageConfigured);
    }

    private static boolean isAllowedFilesystemSystemDirectoriesKey(String key) {
        for (String allowed : FILESYSTEM_SYSTEM_DIRECTORIES_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Symlink target: non-empty String without NUL/CR/LF.
     * Absolute or relative allowed; spaces and backslashes kept as-is (no path normalize).
     */
    private static String requireLinkTargetString(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String target without NUL/CR/LF");
        }
        String value = (String) raw;
        if (value.isEmpty()) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String target without NUL/CR/LF");
        }
        if (value.indexOf('\0') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String target without NUL/CR/LF");
        }
        return value;
    }

    /** source: non-empty String without NUL/CR/LF (spaces allowed). */
    private static String requireMountSourceString(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path + " must be a non-empty String without NUL/CR/LF");
        }
        String value = (String) raw;
        if (value.isEmpty()) {
            throw new IllegalArgumentException(path + " must be a non-empty String without NUL/CR/LF");
        }
        if (value.indexOf('\0') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(path + " must be a non-empty String without NUL/CR/LF");
        }
        return value;
    }

    /** fileSystemType: non-empty token without whitespace or NUL. */
    private static String requireMountTokenString(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty token String without whitespace or NUL");
        }
        String value = (String) raw;
        if (value.isEmpty()) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty token String without whitespace or NUL");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\0' || Character.isWhitespace(c)) {
                throw new IllegalArgumentException(path
                        + " must be a non-empty token String without whitespace or NUL");
            }
        }
        return value;
    }

    /** options / mountOptions / superOptions: non-empty, no whitespace/NUL/CR/LF (commas kept). */
    private static String requireMountOptionsString(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without whitespace, NUL, CR, or LF");
        }
        String value = (String) raw;
        if (value.isEmpty()) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without whitespace, NUL, CR, or LF");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\0' || c == '\r' || c == '\n' || Character.isWhitespace(c)) {
                throw new IllegalArgumentException(path
                        + " must be a non-empty String without whitespace, NUL, CR, or LF");
            }
        }
        return value;
    }

    private static List<String> requireMountOptionalFieldsArray(Object raw, String path) {
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException(path + " must be a JSONArray");
        }
        JSONArray array = (JSONArray) raw;
        List<String> built = new ArrayList<String>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String itemPath = path + "[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof String)) {
                throw new IllegalArgumentException(itemPath
                        + " must be a non-empty String without whitespace, NUL, CR, or LF");
            }
            String value = (String) item;
            if (value.isEmpty()) {
                throw new IllegalArgumentException(itemPath
                        + " must be a non-empty String without whitespace, NUL, CR, or LF");
            }
            for (int j = 0; j < value.length(); j++) {
                char c = value.charAt(j);
                if (c == '\0' || c == '\r' || c == '\n' || Character.isWhitespace(c)) {
                    throw new IllegalArgumentException(itemPath
                            + " must be a non-empty String without whitespace, NUL, CR, or LF");
                }
            }
            built.add(value);
        }
        return Collections.unmodifiableList(built);
    }

    /**
     * {@code fsid} must be a JSONArray of length exactly 2; each element exact long 0..0xffffffff.
     * Returns a private mutable long[2] for storage (getters copy defensively).
     */
    private static long[] requireExactFsidArray(Object raw, String path) {
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException(path + " must be a JSONArray of length 2");
        }
        JSONArray array = (JSONArray) raw;
        if (array.size() != 2) {
            throw new IllegalArgumentException(path + " must be a JSONArray of length 2");
        }
        long[] fsid = new long[2];
        for (int i = 0; i < 2; i++) {
            fsid[i] = requireExactJsonNumberLongValue(array.get(i), path + "[" + i + "]",
                    0L, 0xffffffffL);
        }
        return fsid;
    }

    private static boolean isAllowedFilesystemKey(String key) {
        for (String allowed : FILESYSTEM_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedFilesystemStatKey(String key) {
        for (String allowed : FILESYSTEM_STAT_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedFilesystemStatFsKey(String key) {
        for (String allowed : FILESYSTEM_STATFS_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedFilesystemMountsKey(String key) {
        for (String allowed : FILESYSTEM_MOUNTS_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Unified POSIX absolute path normalization for {@code filesystem.stat} keys and lookups.
     * <ul>
     *   <li>non-null, non-empty, no leading/trailing whitespace</li>
     *   <li>starts with {@code /}</li>
     *   <li>must not contain NUL ({@code \0}) or backslash</li>
     *   <li>split on {@code /}; ignore empty segments and {@code .}</li>
     *   <li>{@code ..} pops one segment; escaping above root is illegal</li>
     *   <li>result is {@code /} for root, otherwise {@code /} + joined segments</li>
     * </ul>
     *
     * @param path      raw path
     * @param errorPath label included in {@link IllegalArgumentException} messages
     * @return normalized absolute path
     */
    private static String normalizePosixAbsolutePath(String path, String errorPath) {
        if (path == null) {
            throw new IllegalArgumentException(errorPath + " must not be null");
        }
        if (path.isEmpty() || !path.equals(path.trim())) {
            throw new IllegalArgumentException(errorPath
                    + " must be a non-empty absolute path String without leading or trailing whitespace");
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException(errorPath
                    + " must be an absolute path starting with '/'");
        }
        if (path.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(errorPath + " must not contain NUL");
        }
        if (path.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(errorPath + " must not contain backslash");
        }
        List<String> segments = new ArrayList<String>();
        int length = path.length();
        int i = 0;
        while (i < length) {
            // skip slash separators
            while (i < length && path.charAt(i) == '/') {
                i++;
            }
            if (i >= length) {
                break;
            }
            int start = i;
            while (i < length && path.charAt(i) != '/') {
                i++;
            }
            String segment = path.substring(start, i);
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (segments.isEmpty()) {
                    throw new IllegalArgumentException(errorPath
                            + " must not escape above filesystem root");
                }
                segments.remove(segments.size() - 1);
                continue;
            }
            segments.add(segment);
        }
        if (segments.isEmpty()) {
            return "/";
        }
        StringBuilder normalized = new StringBuilder(path.length());
        for (int s = 0; s < segments.size(); s++) {
            normalized.append('/').append(segments.get(s));
        }
        return normalized.toString();
    }

    /**
     * Signature hex string: non-empty, even length, only {@code [0-9A-Fa-f]}, returned lowercase.
     * Rejects null, non-String, empty, odd length, and non-hex characters (no separators/whitespace).
     */
    private static String requirePackageSignatureHex(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path + " must be a non-empty even-length hex String");
        }
        String hex = (String) raw;
        if (hex.isEmpty() || (hex.length() & 1) != 0) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty even-length hex String");
        }
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if (Character.digit(c, 16) < 0) {
                throw new IllegalArgumentException(path
                        + " must contain only hexadecimal characters");
            }
        }
        return hex.toLowerCase(Locale.ROOT);
    }

    /**
     * Package name: non-empty, no leading/trailing whitespace, one or more
     * {@code [A-Za-z0-9_]+} segments separated by dots.
     */
    private static String requireAndroidPackageName(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path + " must be a String");
        }
        String name = (String) raw;
        if (name.isEmpty() || !name.equals(name.trim())) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without leading or trailing whitespace");
        }
        if (!name.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")) {
            throw new IllegalArgumentException(path
                    + " must be dot-separated non-empty [A-Za-z0-9_] segments");
        }
        return name;
    }

    /**
     * Permission name: non-empty, no leading/trailing whitespace, one or more
     * {@code [A-Za-z0-9_]+} segments separated by dots
     * (e.g. {@code android.permission.INTERNET}).
     */
    private static String requirePermissionName(String permission, String path) {
        if (permission == null) {
            throw new IllegalArgumentException(path + " must be a non-empty String");
        }
        if (permission.isEmpty() || !permission.equals(permission.trim())) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without leading or trailing whitespace");
        }
        if (!permission.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")) {
            throw new IllegalArgumentException(path
                    + " must be dot-separated non-empty [A-Za-z0-9_] segments");
        }
        return permission;
    }

    /**
     * API permission argument: rejects null, blank, and invalid permission-name syntax.
     */
    private static void requirePermissionApiArg(String permission) {
        if (permission == null || permission.trim().isEmpty()) {
            throw new IllegalArgumentException("permission must be a non-blank String");
        }
        if (!permission.equals(permission.trim())
                || !permission.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")) {
            throw new IllegalArgumentException(
                    "permission must be dot-separated non-empty [A-Za-z0-9_] segments: "
                            + permission);
        }
    }

    /**
     * Package-name String or JSON null; non-null must satisfy {@link #requireAndroidPackageName}.
     */
    private static String requireOptionalNullableAndroidPackageName(Object raw, String path) {
        if (raw == null) {
            return null;
        }
        return requireAndroidPackageName(raw, path);
    }

    /**
     * String or JSON null; non-null must be non-empty without leading/trailing whitespace.
     */
    private static String requireOptionalNullableNoTrimString(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(path + " must be a String or JSON null");
        }
        String text = (String) value;
        if (text.isEmpty() || !text.equals(text.trim())) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without leading or trailing whitespace");
        }
        return text;
    }

    /**
     * Absolute slash-prefixed path String or JSON null; non-null must be non-empty without
     * leading/trailing whitespace and start with {@code /}.
     */
    private static String requireOptionalNullableAbsolutePath(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(path + " must be a String or JSON null");
        }
        String text = (String) value;
        if (text.isEmpty() || !text.equals(text.trim())) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty absolute path String without leading or trailing whitespace");
        }
        if (!text.startsWith("/")) {
            throw new IllegalArgumentException(path
                    + " must be an absolute path starting with '/'");
        }
        return text;
    }

    private static boolean isAllowedTelephonyTopLevelKey(String key) {
        for (String allowed : TELEPHONY_TOP_LEVEL_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedTelephonySlotKey(String key) {
        for (String allowed : TELEPHONY_SLOT_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTelephonyIdentifierKey(String key) {
        for (String allowed : TELEPHONY_SLOT_IDENTIFIER_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static void validateTelephonyIdentifierValue(Object value, String path) {
        if (value == null) {
            return;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(path + " must be a String or JSON null");
        }
        String text = (String) value;
        if (text.isEmpty() || !text.equals(text.trim())) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without leading or trailing whitespace");
        }
    }

    private static void requireTelephonyIdentifierApiArgs(int slotIndex, String key) {
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0: " + slotIndex);
        }
        if (key == null || !isTelephonyIdentifierKey(key)) {
            throw new IllegalArgumentException(
                    "key must be one of imei|meid|deviceId|subscriberId|simSerialNumber: " + key);
        }
    }

    private static boolean isTelephonyGlobalStringKey(String key) {
        for (String allowed : TELEPHONY_GLOBAL_STRING_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static void requireTelephonyGlobalStringKey(String key) {
        if (key == null || !isTelephonyGlobalStringKey(key)) {
            throw new IllegalArgumentException(
                    "key must be one of networkOperator|networkOperatorName|simOperator|simOperatorName|"
                            + "networkCountryIso|simCountryIso: " + key);
        }
    }

    /**
     * Whether {@code android.telephony.<key>} is present for a global telephony string key
     * (including explicit JSON null for operator strings). {@code key} must be one of
     * networkOperator|networkOperatorName|simOperator|simOperatorName|networkCountryIso|simCountryIso.
     */
    public boolean isTelephonyStringConfigured(String key) {
        requireTelephonyGlobalStringKey(key);
        JSONObject telephony = androidTelephony();
        return telephony != null && telephony.containsKey(key);
    }

    /**
     * Global telephony string, or null if missing or explicitly null (operator strings only).
     * {@code networkCountryIso} / {@code simCountryIso} are never null when configured: empty
     * string or exactly two lowercase ASCII letters (nonempty values are normalized to lowercase).
     * Never returns JSONObject/JSONArray/List/Map. Not derived from networkOperator/simOperator.
     */
    public String getTelephonyString(String key) {
        requireTelephonyGlobalStringKey(key);
        JSONObject telephony = androidTelephony();
        if (telephony == null || !telephony.containsKey(key)) {
            return null;
        }
        Object value = telephony.get(key);
        if (value == null) {
            return null;
        }
        String text = (String) value;
        if ((TELEPHONY_NETWORK_COUNTRY_ISO_KEY.equals(key)
                || TELEPHONY_SIM_COUNTRY_ISO_KEY.equals(key))
                && !text.isEmpty()) {
            return text.toLowerCase(Locale.ROOT);
        }
        return text;
    }

    /**
     * Country ISO global strings ({@code networkCountryIso} / {@code simCountryIso}): only a JSON
     * String that is empty or exactly two ASCII letters (A–Z / a–z). Null / non-String / other
     * length / non-letters rejected. Normalization to lowercase happens in
     * {@link #getTelephonyString(String)}.
     */
    private static void validateTelephonyCountryIso(Object value, String path) {
        if (value == null || !(value instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a JSON String (empty or exactly two ASCII letters)");
        }
        String text = (String) value;
        if (text.isEmpty()) {
            return;
        }
        if (text.length() != 2) {
            throw new IllegalArgumentException(path
                    + " must be empty or exactly two ASCII letters");
        }
        char c0 = text.charAt(0);
        char c1 = text.charAt(1);
        if (!isAsciiLetter(c0) || !isAsciiLetter(c1)) {
            throw new IllegalArgumentException(path
                    + " must be empty or exactly two ASCII letters");
        }
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isTelephonyGlobalIntKey(String key) {
        for (String allowed : TELEPHONY_GLOBAL_INT_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static void requireTelephonyGlobalIntKey(String key) {
        if (key == null || !isTelephonyGlobalIntKey(key)) {
            throw new IllegalArgumentException(
                    "key must be one of dataNetworkType|dataState|dataActivity|phoneType: " + key);
        }
    }

    /**
     * Whether {@code android.telephony.<key>} is present for a global int state key.
     * {@code key} must be one of dataNetworkType|dataState|dataActivity|phoneType.
     */
    public boolean isTelephonyIntConfigured(String key) {
        requireTelephonyGlobalIntKey(key);
        JSONObject telephony = androidTelephony();
        return telephony != null && telephony.containsKey(key);
    }

    /**
     * Global telephony int state, or {@code fallback} when missing.
     */
    public int getTelephonyInt(String key, int fallback) {
        requireTelephonyGlobalIntKey(key);
        if (!isTelephonyIntConfigured(key)) {
            return fallback;
        }
        Integer value = getIntegerObject(androidTelephony(), key);
        return value == null ? fallback : value.intValue();
    }

    private static void requireTelephonyBooleanKey(String key) {
        if (!"networkRoaming".equals(key)) {
            throw new IllegalArgumentException("key must be networkRoaming: " + key);
        }
    }

    /**
     * Whether {@code android.telephony.networkRoaming} is present. Only {@code networkRoaming} is accepted.
     */
    public boolean isTelephonyBooleanConfigured(String key) {
        requireTelephonyBooleanKey(key);
        JSONObject telephony = androidTelephony();
        return telephony != null && telephony.containsKey(key);
    }

    /**
     * Global telephony boolean state, or {@code fallback} when missing.
     */
    public boolean getTelephonyBoolean(String key, boolean fallback) {
        requireTelephonyBooleanKey(key);
        if (!isTelephonyBooleanConfigured(key)) {
            return fallback;
        }
        Object value = androidTelephony().get(key);
        if (!(value instanceof Boolean)) {
            return fallback;
        }
        return ((Boolean) value).booleanValue();
    }

    /**
     * Whether the slot at {@code slotIndex} has {@code simState} configured.
     * {@code slotIndex &lt; 0} throws.
     */
    public boolean isTelephonySlotSimStateConfigured(int slotIndex) {
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0: " + slotIndex);
        }
        JSONObject slot = findTelephonySlot(slotIndex);
        return slot != null && slot.containsKey("simState");
    }

    /**
     * Per-slot {@code simState}, or {@code fallback} when the slot/key is missing.
     * {@code slotIndex &lt; 0} throws.
     */
    public int getTelephonySlotSimState(int slotIndex, int fallback) {
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0: " + slotIndex);
        }
        if (!isTelephonySlotSimStateConfigured(slotIndex)) {
            return fallback;
        }
        Integer value = getIntegerObject(findTelephonySlot(slotIndex), "simState");
        return value == null ? fallback : value.intValue();
    }

    /**
     * Whether {@code android.telephony.phoneCount} is present (telephony section configured).
     */
    public boolean isTelephonyPhoneCountConfigured() {
        JSONObject telephony = androidTelephony();
        return telephony != null && telephony.containsKey("phoneCount");
    }

    /**
     * Configured phone count, or {@code fallback} when telephony / phoneCount is absent.
     */
    public int getTelephonyPhoneCount(int fallback) {
        if (!isTelephonyPhoneCountConfigured()) {
            return fallback;
        }
        Integer value = getIntegerObject(androidTelephony(), "phoneCount");
        return value == null ? fallback : value.intValue();
    }

    /**
     * Whether {@code android.telephony.slots[*]} with the given {@code slotIndex} has {@code key}
     * (including explicit JSON null). {@code key} must be one of
     * imei|meid|deviceId|subscriberId|simSerialNumber. {@code slotIndex &lt; 0} or illegal key throws.
     */
    public boolean isTelephonySlotIdentifierConfigured(int slotIndex, String key) {
        requireTelephonyIdentifierApiArgs(slotIndex, key);
        JSONObject slot = findTelephonySlot(slotIndex);
        return slot != null && slot.containsKey(key);
    }

    /**
     * Identifier string for the slot, or null if the slot/key is missing or explicitly null.
     * Never returns JSONObject/JSONArray/List/Map.
     */
    public String getTelephonySlotIdentifier(int slotIndex, String key) {
        requireTelephonyIdentifierApiArgs(slotIndex, key);
        JSONObject slot = findTelephonySlot(slotIndex);
        if (slot == null || !slot.containsKey(key)) {
            return null;
        }
        Object value = slot.get(key);
        if (value == null) {
            return null;
        }
        return (String) value;
    }

    private JSONObject androidTelephony() {
        JSONObject android = android();
        return android == null ? null : android.getJSONObject("telephony");
    }

    private JSONObject findTelephonySlot(int slotIndex) {
        JSONObject telephony = androidTelephony();
        if (telephony == null) {
            return null;
        }
        JSONArray slots = telephony.getJSONArray("slots");
        if (slots == null) {
            return null;
        }
        for (int i = 0; i < slots.size(); i++) {
            Object item = slots.get(i);
            if (!(item instanceof JSONObject)) {
                continue;
            }
            JSONObject slot = (JSONObject) item;
            Integer configured = getIntegerObject(slot, "slotIndex");
            if (configured != null && configured.intValue() == slotIndex) {
                return slot;
            }
        }
        return null;
    }

    /**
     * Strict parse-time rules for {@code network.interfaces} when the key is present.
     * Exception messages always include a {@code network.interfaces[i].field} path (or
     * {@code network.interfaces} / {@code network.interfaces[i]} for structure errors).
     * Also materializes {@link #networkInterfaces}.
     */
    private void validateNetworkInterfaces() {
        if (!isNetworkInterfacesConfigured()) {
            this.networkInterfaces = Collections.emptyList();
            return;
        }
        JSONObject network = section("network");
        Object raw = network.get("interfaces");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("network.interfaces must be a JSON array");
        }
        JSONArray array = (JSONArray) raw;
        Set<String> seenNames = new HashSet<String>();
        Set<Integer> seenIndexes = new HashSet<Integer>();
        List<NetworkInterfaceConfig> built = new ArrayList<NetworkInterfaceConfig>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String pathPrefix = "network.interfaces[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject iface = (JSONObject) item;

            String name = requireInterfaceName(iface, pathPrefix + ".name");
            if (!seenNames.add(name)) {
                throw new IllegalArgumentException(pathPrefix + ".name is not unique: " + name);
            }

            int index = requireUnsignedIntField(iface, "index", pathPrefix + ".index", 1, Integer.MAX_VALUE, true);
            if (!seenIndexes.add(index)) {
                throw new IllegalArgumentException(pathPrefix + ".index is not unique: " + index);
            }

            String ipv4 = requireIpv4StringField(iface, "ipv4", pathPrefix + ".ipv4", true);
            String broadcast = null;
            if (iface.containsKey("broadcast")) {
                broadcast = requireIpv4StringField(iface, "broadcast", pathPrefix + ".broadcast", false);
            }
            Integer flags = null;
            if (iface.containsKey("flags")) {
                flags = requireUnsignedIntField(iface, "flags", pathPrefix + ".flags", 0, 65535, false);
            }
            String mac = null;
            if (iface.containsKey("mac")) {
                mac = requireMacStringField(iface, pathPrefix + ".mac");
            }
            Integer mtu = null;
            if (iface.containsKey("mtu")) {
                mtu = requireUnsignedIntField(iface, "mtu", pathPrefix + ".mtu", 68, 65536, false);
            }
            boolean displayNameConfigured = iface.containsKey("displayName");
            String displayName = null;
            if (displayNameConfigured) {
                displayName = requireNetworkInterfaceDisplayName(iface.get("displayName"),
                        pathPrefix + ".displayName");
            }
            boolean virtualConfigured = iface.containsKey("virtual");
            boolean virtual = false;
            if (virtualConfigured) {
                virtual = requireAndroidPowerBoolean(iface.get("virtual"),
                        pathPrefix + ".virtual");
            }
            boolean operStateConfigured = iface.containsKey("operState");
            String operState = null;
            if (operStateConfigured) {
                operState = requireNetworkInterfaceOperState(iface.get("operState"),
                        pathPrefix + ".operState");
            }
            boolean carrierConfigured = iface.containsKey("carrier");
            boolean carrier = false;
            if (carrierConfigured) {
                carrier = requireAndroidPowerBoolean(iface.get("carrier"),
                        pathPrefix + ".carrier");
            }
            boolean hardwareTypeConfigured = iface.containsKey("hardwareType");
            Integer hardwareType = null;
            if (hardwareTypeConfigured) {
                hardwareType = Integer.valueOf(requireExactJsonNumberIntValue(
                        iface.get("hardwareType"), pathPrefix + ".hardwareType", 0, 65535));
            }
            boolean speedMbpsConfigured = iface.containsKey("speedMbps");
            Integer speedMbps = null;
            if (speedMbpsConfigured) {
                speedMbps = Integer.valueOf(requireExactJsonNumberIntValue(
                        iface.get("speedMbps"), pathPrefix + ".speedMbps",
                        -1, Integer.MAX_VALUE));
            }
            boolean duplexConfigured = iface.containsKey("duplex");
            String duplex = null;
            if (duplexConfigured) {
                duplex = requireNetworkInterfaceDuplex(iface.get("duplex"),
                        pathPrefix + ".duplex");
            }
            boolean linkIndexConfigured = iface.containsKey("linkIndex");
            Integer linkIndex = null;
            if (linkIndexConfigured) {
                linkIndex = Integer.valueOf(requireExactJsonNumberIntValue(
                        iface.get("linkIndex"), pathPrefix + ".linkIndex",
                        1, Integer.MAX_VALUE));
            }
            boolean txQueueLenConfigured = iface.containsKey("txQueueLen");
            Integer txQueueLen = null;
            if (txQueueLenConfigured) {
                txQueueLen = Integer.valueOf(requireExactJsonNumberIntValue(
                        iface.get("txQueueLen"), pathPrefix + ".txQueueLen",
                        0, Integer.MAX_VALUE));
            }
            boolean addressAssignTypeConfigured = iface.containsKey("addressAssignType");
            Integer addressAssignType = null;
            if (addressAssignTypeConfigured) {
                addressAssignType = Integer.valueOf(requireExactJsonNumberIntValue(
                        iface.get("addressAssignType"), pathPrefix + ".addressAssignType",
                        0, 3));
            }
            boolean nameAssignTypeConfigured = iface.containsKey("nameAssignType");
            Integer nameAssignType = null;
            if (nameAssignTypeConfigured) {
                nameAssignType = Integer.valueOf(requireExactJsonNumberIntValue(
                        iface.get("nameAssignType"), pathPrefix + ".nameAssignType",
                        0, 4));
            }
            boolean linkLayerBroadcastConfigured = iface.containsKey("linkLayerBroadcast");
            String linkLayerBroadcast = null;
            if (linkLayerBroadcastConfigured) {
                linkLayerBroadcast = requireMacStringValue(iface.get("linkLayerBroadcast"),
                        pathPrefix + ".linkLayerBroadcast");
            }
            built.add(new NetworkInterfaceConfig(name, index, ipv4, broadcast, flags, mac, mtu,
                    displayName, displayNameConfigured, virtual, virtualConfigured,
                    operState, operStateConfigured, carrier, carrierConfigured,
                    hardwareType, hardwareTypeConfigured,
                    speedMbps, speedMbpsConfigured, duplex, duplexConfigured,
                    linkIndex, linkIndexConfigured, txQueueLen, txQueueLenConfigured,
                    addressAssignType, addressAssignTypeConfigured,
                    nameAssignType, nameAssignTypeConfigured,
                    linkLayerBroadcast, linkLayerBroadcastConfigured));
        }
        this.networkInterfaces = Collections.unmodifiableList(built);
    }

    private static final String[] NETWORK_IPV4_ROUTE_ALLOWED_KEYS = {
            "interfaceName", "destination", "gateway", "flags", "refCount", "use",
            "metric", "mask", "mtu", "window", "irtt"
    };

    private static final long NETWORK_IPV4_ROUTE_UINT32_MAX = 4294967295L;

    /**
     * Strict parse-time rules for optional {@code network.ipv4Routes} when the key is present.
     * Missing key does not take over any route path. Explicit {@code []} is configured empty.
     * Every object uses a required whitelist; values are never inferred from interfaces/wifi.
     * {@code interfaceName} must match a unique configured {@code network.interfaces[]} name.
     */
    private void validateNetworkIpv4Routes() {
        if (!isNetworkIpv4RoutesConfigured()) {
            this.networkIpv4Routes = Collections.emptyList();
            return;
        }
        JSONObject network = section("network");
        Object raw = network.get("ipv4Routes");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("network.ipv4Routes must be a JSON array");
        }
        JSONArray array = (JSONArray) raw;
        Set<String> configuredNames = new HashSet<String>();
        for (NetworkInterfaceConfig iface : networkInterfaces) {
            configuredNames.add(iface.getName());
        }
        List<NetworkIpv4RouteConfig> built = new ArrayList<NetworkIpv4RouteConfig>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String pathPrefix = "network.ipv4Routes[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject entry = (JSONObject) item;
            for (String field : entry.keySet()) {
                if (!isAllowedNetworkIpv4RouteKey(field)) {
                    throw new IllegalArgumentException(pathPrefix + "." + field
                            + " is not an allowed key (interfaceName|destination|gateway|flags|"
                            + "refCount|use|metric|mask|mtu|window|irtt)");
                }
            }
            if (!entry.containsKey("interfaceName")) {
                throw new IllegalArgumentException(pathPrefix + ".interfaceName is required");
            }
            String interfaceName = requireInterfaceNameString(entry.get("interfaceName"),
                    pathPrefix + ".interfaceName");
            if (!configuredNames.contains(interfaceName)) {
                throw new IllegalArgumentException(pathPrefix
                        + ".interfaceName is not a configured network.interfaces name: "
                        + interfaceName);
            }
            String destination = requireIpv4StringField(entry, "destination",
                    pathPrefix + ".destination", true);
            String gateway = requireIpv4StringField(entry, "gateway",
                    pathPrefix + ".gateway", true);
            long flags = requireExactJsonNumberLongField(entry, "flags",
                    pathPrefix + ".flags", 0L, NETWORK_IPV4_ROUTE_UINT32_MAX);
            long refCount = requireExactJsonNumberLongField(entry, "refCount",
                    pathPrefix + ".refCount", 0L, NETWORK_IPV4_ROUTE_UINT32_MAX);
            long use = requireExactJsonNumberLongField(entry, "use",
                    pathPrefix + ".use", 0L, NETWORK_IPV4_ROUTE_UINT32_MAX);
            long metric = requireExactJsonNumberLongField(entry, "metric",
                    pathPrefix + ".metric", 0L, NETWORK_IPV4_ROUTE_UINT32_MAX);
            String mask = requireIpv4StringField(entry, "mask",
                    pathPrefix + ".mask", true);
            long mtu = requireExactJsonNumberLongField(entry, "mtu",
                    pathPrefix + ".mtu", 0L, NETWORK_IPV4_ROUTE_UINT32_MAX);
            long window = requireExactJsonNumberLongField(entry, "window",
                    pathPrefix + ".window", 0L, NETWORK_IPV4_ROUTE_UINT32_MAX);
            long irtt = requireExactJsonNumberLongField(entry, "irtt",
                    pathPrefix + ".irtt", 0L, NETWORK_IPV4_ROUTE_UINT32_MAX);
            built.add(new NetworkIpv4RouteConfig(interfaceName, destination, gateway,
                    flags, refCount, use, metric, mask, mtu, window, irtt));
        }
        this.networkIpv4Routes = Collections.unmodifiableList(built);
    }

    private static boolean isAllowedNetworkIpv4RouteKey(String key) {
        for (String allowed : NETWORK_IPV4_ROUTE_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static final String[] NETWORK_INTERFACE_STATS_ALLOWED_KEYS = {
            "interfaceName", "rxBytes", "rxPackets", "rxErrors", "rxDrop", "rxFifo",
            "rxFrame", "rxCompressed", "rxMulticast", "txBytes", "txPackets", "txErrors",
            "txDrop", "txFifo", "txCollisions", "txCarrier", "txCompressed"
    };

    /**
     * Strict parse-time rules for optional {@code network.interfaceStats} when the key is present.
     * Missing key does not take over any {@code /proc/net/dev} path. Explicit {@code []} is
     * configured empty. Every object uses a required whitelist; counters are never inferred
     * from interfaces, ipv4Routes, wifi, or other fields. {@code interfaceName} must match a
     * unique configured {@code network.interfaces[]} name and must be unique within this array.
     */
    private void validateNetworkInterfaceStats() {
        if (!isNetworkInterfaceStatsConfigured()) {
            this.networkInterfaceStats = Collections.emptyList();
            return;
        }
        JSONObject network = section("network");
        Object raw = network.get("interfaceStats");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("network.interfaceStats must be a JSON array");
        }
        JSONArray array = (JSONArray) raw;
        Set<String> configuredNames = new HashSet<String>();
        for (NetworkInterfaceConfig iface : networkInterfaces) {
            configuredNames.add(iface.getName());
        }
        Set<String> seenStatsNames = new HashSet<String>();
        List<NetworkInterfaceStatsConfig> built =
                new ArrayList<NetworkInterfaceStatsConfig>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String pathPrefix = "network.interfaceStats[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject entry = (JSONObject) item;
            for (String field : entry.keySet()) {
                if (!isAllowedNetworkInterfaceStatsKey(field)) {
                    throw new IllegalArgumentException(pathPrefix + "." + field
                            + " is not an allowed key (interfaceName|rxBytes|rxPackets|rxErrors|"
                            + "rxDrop|rxFifo|rxFrame|rxCompressed|rxMulticast|txBytes|txPackets|"
                            + "txErrors|txDrop|txFifo|txCollisions|txCarrier|txCompressed)");
                }
            }
            if (!entry.containsKey("interfaceName")) {
                throw new IllegalArgumentException(pathPrefix + ".interfaceName is required");
            }
            String interfaceName = requireInterfaceNameString(entry.get("interfaceName"),
                    pathPrefix + ".interfaceName");
            if (!configuredNames.contains(interfaceName)) {
                throw new IllegalArgumentException(pathPrefix
                        + ".interfaceName is not a configured network.interfaces name: "
                        + interfaceName);
            }
            if (!seenStatsNames.add(interfaceName)) {
                throw new IllegalArgumentException(pathPrefix
                        + ".interfaceName is not unique: " + interfaceName);
            }
            long rxBytes = requireExactJsonNumberLongField(entry, "rxBytes",
                    pathPrefix + ".rxBytes", 0L, Long.MAX_VALUE);
            long rxPackets = requireExactJsonNumberLongField(entry, "rxPackets",
                    pathPrefix + ".rxPackets", 0L, Long.MAX_VALUE);
            long rxErrors = requireExactJsonNumberLongField(entry, "rxErrors",
                    pathPrefix + ".rxErrors", 0L, Long.MAX_VALUE);
            long rxDrop = requireExactJsonNumberLongField(entry, "rxDrop",
                    pathPrefix + ".rxDrop", 0L, Long.MAX_VALUE);
            long rxFifo = requireExactJsonNumberLongField(entry, "rxFifo",
                    pathPrefix + ".rxFifo", 0L, Long.MAX_VALUE);
            long rxFrame = requireExactJsonNumberLongField(entry, "rxFrame",
                    pathPrefix + ".rxFrame", 0L, Long.MAX_VALUE);
            long rxCompressed = requireExactJsonNumberLongField(entry, "rxCompressed",
                    pathPrefix + ".rxCompressed", 0L, Long.MAX_VALUE);
            long rxMulticast = requireExactJsonNumberLongField(entry, "rxMulticast",
                    pathPrefix + ".rxMulticast", 0L, Long.MAX_VALUE);
            long txBytes = requireExactJsonNumberLongField(entry, "txBytes",
                    pathPrefix + ".txBytes", 0L, Long.MAX_VALUE);
            long txPackets = requireExactJsonNumberLongField(entry, "txPackets",
                    pathPrefix + ".txPackets", 0L, Long.MAX_VALUE);
            long txErrors = requireExactJsonNumberLongField(entry, "txErrors",
                    pathPrefix + ".txErrors", 0L, Long.MAX_VALUE);
            long txDrop = requireExactJsonNumberLongField(entry, "txDrop",
                    pathPrefix + ".txDrop", 0L, Long.MAX_VALUE);
            long txFifo = requireExactJsonNumberLongField(entry, "txFifo",
                    pathPrefix + ".txFifo", 0L, Long.MAX_VALUE);
            long txCollisions = requireExactJsonNumberLongField(entry, "txCollisions",
                    pathPrefix + ".txCollisions", 0L, Long.MAX_VALUE);
            long txCarrier = requireExactJsonNumberLongField(entry, "txCarrier",
                    pathPrefix + ".txCarrier", 0L, Long.MAX_VALUE);
            long txCompressed = requireExactJsonNumberLongField(entry, "txCompressed",
                    pathPrefix + ".txCompressed", 0L, Long.MAX_VALUE);
            built.add(new NetworkInterfaceStatsConfig(interfaceName,
                    rxBytes, rxPackets, rxErrors, rxDrop, rxFifo, rxFrame, rxCompressed,
                    rxMulticast, txBytes, txPackets, txErrors, txDrop, txFifo, txCollisions,
                    txCarrier, txCompressed));
        }
        this.networkInterfaceStats = Collections.unmodifiableList(built);
    }

    private static boolean isAllowedNetworkInterfaceStatsKey(String key) {
        for (String allowed : NETWORK_INTERFACE_STATS_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static final String[] NETWORK_IPV6_ADDRESS_ALLOWED_KEYS = {
            "interfaceName", "addressHex", "prefixLength", "scope", "flags"
    };

    /**
     * Strict parse-time rules for optional {@code network.ipv6Addresses} when the key is present.
     * Missing key does not take over any {@code /proc/net/if_inet6} path. Explicit {@code []} is
     * configured empty (zero-byte file, no header). Every object uses a required whitelist;
     * address, prefix, scope, and flags are never inferred from interfaces, ipv4Routes,
     * interfaceStats, wifi, or other fields. {@code interfaceName} must match a unique
     * configured {@code network.interfaces[]} name. A referenced interface
     * {@code index} must be {@code 0..255} (existing {@code network.interfaces}
     * lower bound still applies; max is 255) so {@code /proc/net/if_inet6} can
     * emit a two-digit lowercase hex index. Unreferenced interface indexes keep
     * their existing range. {@code interfaceName}+{@code addressHex}
     * pairs must be unique within this array after hex lowercase normalization.
     */
    private void validateNetworkIpv6Addresses() {
        if (!isNetworkIpv6AddressesConfigured()) {
            this.networkIpv6Addresses = Collections.emptyList();
            return;
        }
        JSONObject network = section("network");
        Object raw = network.get("ipv6Addresses");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("network.ipv6Addresses must be a JSON array");
        }
        JSONArray array = (JSONArray) raw;
        Set<String> configuredNames = new HashSet<String>();
        for (NetworkInterfaceConfig iface : networkInterfaces) {
            configuredNames.add(iface.getName());
        }
        Set<String> seenPairs = new HashSet<String>();
        List<NetworkIpv6AddressConfig> built =
                new ArrayList<NetworkIpv6AddressConfig>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String pathPrefix = "network.ipv6Addresses[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject entry = (JSONObject) item;
            for (String field : entry.keySet()) {
                if (!isAllowedNetworkIpv6AddressKey(field)) {
                    throw new IllegalArgumentException(pathPrefix + "." + field
                            + " is not an allowed key (interfaceName|addressHex|prefixLength|"
                            + "scope|flags)");
                }
            }
            if (!entry.containsKey("interfaceName")) {
                throw new IllegalArgumentException(pathPrefix + ".interfaceName is required");
            }
            String interfaceName = requireInterfaceNameString(entry.get("interfaceName"),
                    pathPrefix + ".interfaceName");
            if (!configuredNames.contains(interfaceName)) {
                throw new IllegalArgumentException(pathPrefix
                        + ".interfaceName is not a configured network.interfaces name: "
                        + interfaceName);
            }
            int referencedIndex = -1;
            for (NetworkInterfaceConfig iface : networkInterfaces) {
                if (interfaceName.equals(iface.getName())) {
                    referencedIndex = iface.getIndex();
                    break;
                }
            }
            if (referencedIndex < 0 || referencedIndex > 255) {
                throw new IllegalArgumentException(pathPrefix
                        + ".interfaceName referenced interface index must be an integer in range 0..255");
            }
            if (!entry.containsKey("addressHex")) {
                throw new IllegalArgumentException(pathPrefix + ".addressHex is required");
            }
            String addressHex = requireIpv6AddressHex(entry.get("addressHex"),
                    pathPrefix + ".addressHex");
            String pairKey = interfaceName + '\0' + addressHex;
            if (!seenPairs.add(pairKey)) {
                throw new IllegalArgumentException(pathPrefix
                        + ".interfaceName and addressHex combination is not unique");
            }
            int prefixLength = requireExactJsonNumberIntField(entry, "prefixLength",
                    pathPrefix + ".prefixLength", 0, 128);
            int scope = requireExactJsonNumberIntField(entry, "scope",
                    pathPrefix + ".scope", 0, 255);
            int flags = requireExactJsonNumberIntField(entry, "flags",
                    pathPrefix + ".flags", 0, 255);
            built.add(new NetworkIpv6AddressConfig(interfaceName, addressHex,
                    prefixLength, scope, flags));
        }
        this.networkIpv6Addresses = Collections.unmodifiableList(built);
    }

    private static boolean isAllowedNetworkIpv6AddressKey(String key) {
        for (String allowed : NETWORK_IPV6_ADDRESS_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Strict 32-character ASCII hex IPv6 address for {@code network.ipv6Addresses[].addressHex}.
     * Rejects non-String, other lengths, colons, whitespace, IPv4, DNS, and non-hex characters.
     * Parsed value is normalized to lowercase.
     */
    private static String requireIpv6AddressHex(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path
                    + " must be a String of exactly 32 ASCII hex characters");
        }
        String text = (String) raw;
        if (text.length() != 32) {
            throw new IllegalArgumentException(path
                    + " must be exactly 32 ASCII hex characters");
        }
        for (int i = 0; i < 32; i++) {
            char c = text.charAt(i);
            boolean hex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) {
                throw new IllegalArgumentException(path
                        + " must be exactly 32 ASCII hex characters");
            }
        }
        return text.toLowerCase(Locale.ROOT);
    }

    private static final String[] NETWORK_ARP_ENTRY_ALLOWED_KEYS = {
            "interfaceName", "ipv4", "hardwareType", "flags", "mac"
    };

    private static final long NETWORK_ARP_UINT32_MAX = 4294967295L;

    /**
     * Strict parse-time rules for optional {@code network.arpEntries} when the key is present.
     * Missing key does not take over any {@code /proc/net/arp} path. Explicit {@code []} is
     * configured empty. Every object uses a required whitelist; IPv4, hardware type, flags,
     * and MAC are never inferred from interfaces, ipv4Routes, interfaceStats, ipv6Addresses,
     * wifi, or other fields. {@code interfaceName} must match a unique configured
     * {@code network.interfaces[]} name. {@code interfaceName}+{@code ipv4} pairs must be
     * unique within this array. MAC uses the same colon-hex rules as
     * {@code network.interfaces[].mac} and is stored lowercase.
     */
    private void validateNetworkArpEntries() {
        if (!isNetworkArpEntriesConfigured()) {
            this.networkArpEntries = Collections.emptyList();
            return;
        }
        JSONObject network = section("network");
        Object raw = network.get("arpEntries");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("network.arpEntries must be a JSON array");
        }
        JSONArray array = (JSONArray) raw;
        Set<String> configuredNames = new HashSet<String>();
        for (NetworkInterfaceConfig iface : networkInterfaces) {
            configuredNames.add(iface.getName());
        }
        Set<String> seenPairs = new HashSet<String>();
        List<NetworkArpEntryConfig> built = new ArrayList<NetworkArpEntryConfig>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String pathPrefix = "network.arpEntries[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject entry = (JSONObject) item;
            for (String field : entry.keySet()) {
                if (!isAllowedNetworkArpEntryKey(field)) {
                    throw new IllegalArgumentException(pathPrefix + "." + field
                            + " is not an allowed key (interfaceName|ipv4|hardwareType|flags|mac)");
                }
            }
            if (!entry.containsKey("interfaceName")) {
                throw new IllegalArgumentException(pathPrefix + ".interfaceName is required");
            }
            String interfaceName = requireInterfaceNameString(entry.get("interfaceName"),
                    pathPrefix + ".interfaceName");
            if (!configuredNames.contains(interfaceName)) {
                throw new IllegalArgumentException(pathPrefix
                        + ".interfaceName is not a configured network.interfaces name: "
                        + interfaceName);
            }
            String ipv4 = requireIpv4StringField(entry, "ipv4", pathPrefix + ".ipv4", true);
            String pairKey = interfaceName + '\0' + ipv4;
            if (!seenPairs.add(pairKey)) {
                throw new IllegalArgumentException(pathPrefix
                        + ".interfaceName and ipv4 combination is not unique");
            }
            long hardwareType = requireExactJsonNumberLongField(entry, "hardwareType",
                    pathPrefix + ".hardwareType", 0L, NETWORK_ARP_UINT32_MAX);
            long flags = requireExactJsonNumberLongField(entry, "flags",
                    pathPrefix + ".flags", 0L, NETWORK_ARP_UINT32_MAX);
            if (!entry.containsKey("mac")) {
                throw new IllegalArgumentException(pathPrefix + ".mac is required");
            }
            String mac = requireMacStringValue(entry.get("mac"), pathPrefix + ".mac");
            built.add(new NetworkArpEntryConfig(interfaceName, ipv4, hardwareType, flags, mac));
        }
        this.networkArpEntries = Collections.unmodifiableList(built);
    }

    private static boolean isAllowedNetworkArpEntryKey(String key) {
        for (String allowed : NETWORK_ARP_ENTRY_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static final String[] NETWORK_IGMP_MEMBERSHIP_ALLOWED_KEYS = {
            "interfaceName", "groupIpv4", "querierVersion", "users",
            "timerRunning", "timerClock", "reporter"
    };

    private static final long NETWORK_IGMP_TIMER_CLOCK_MAX = 4294967295L;

    /**
     * Strict parse-time rules for optional {@code network.igmpMemberships} when the key is present.
     * Missing key does not take over any {@code /proc/net/igmp} path. Explicit {@code []} is
     * configured empty. Every object uses a required whitelist; group, querier, users, timer,
     * and reporter are never inferred from interfaces, ipv4Routes, interfaceStats, ipv6Addresses,
     * arpEntries, wifi, or other fields. {@code interfaceName} must match a unique configured
     * {@code network.interfaces[]} name. {@code interfaceName}+{@code groupIpv4} pairs must be
     * unique within this array. {@code querierVersion} must be {@code V1}/{@code V2}/{@code V3}
     * and identical for every entry that shares an interface. {@code timerClock} must be {@code 0}
     * when {@code timerRunning} is false.
     */
    private void validateNetworkIgmpMemberships() {
        if (!isNetworkIgmpMembershipsConfigured()) {
            this.networkIgmpMemberships = Collections.emptyList();
            return;
        }
        JSONObject network = section("network");
        Object raw = network.get("igmpMemberships");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("network.igmpMemberships must be a JSON array");
        }
        JSONArray array = (JSONArray) raw;
        Set<String> configuredNames = new HashSet<String>();
        for (NetworkInterfaceConfig iface : networkInterfaces) {
            configuredNames.add(iface.getName());
        }
        Set<String> seenPairs = new HashSet<String>();
        Map<String, String> querierByInterface = new LinkedHashMap<String, String>();
        List<NetworkIgmpMembershipConfig> built =
                new ArrayList<NetworkIgmpMembershipConfig>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String pathPrefix = "network.igmpMemberships[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject entry = (JSONObject) item;
            for (String field : entry.keySet()) {
                if (!isAllowedNetworkIgmpMembershipKey(field)) {
                    throw new IllegalArgumentException(pathPrefix + "." + field
                            + " is not an allowed key (interfaceName|groupIpv4|querierVersion|"
                            + "users|timerRunning|timerClock|reporter)");
                }
            }
            if (!entry.containsKey("interfaceName")) {
                throw new IllegalArgumentException(pathPrefix + ".interfaceName is required");
            }
            String interfaceName = requireInterfaceNameString(entry.get("interfaceName"),
                    pathPrefix + ".interfaceName");
            if (!configuredNames.contains(interfaceName)) {
                throw new IllegalArgumentException(pathPrefix
                        + ".interfaceName is not a configured network.interfaces name: "
                        + interfaceName);
            }
            String groupIpv4 = requireIpv4StringField(entry, "groupIpv4",
                    pathPrefix + ".groupIpv4", true);
            requireIpv4MulticastGroup(groupIpv4, pathPrefix + ".groupIpv4");
            String pairKey = interfaceName + '\0' + groupIpv4;
            if (!seenPairs.add(pairKey)) {
                throw new IllegalArgumentException(pathPrefix
                        + ".interfaceName and groupIpv4 combination is not unique");
            }
            if (!entry.containsKey("querierVersion")) {
                throw new IllegalArgumentException(pathPrefix + ".querierVersion is required");
            }
            String querierVersion = requireIgmpQuerierVersion(entry.get("querierVersion"),
                    pathPrefix + ".querierVersion");
            String previousQuerier = querierByInterface.get(interfaceName);
            if (previousQuerier == null) {
                querierByInterface.put(interfaceName, querierVersion);
            } else if (!previousQuerier.equals(querierVersion)) {
                throw new IllegalArgumentException(pathPrefix
                        + ".querierVersion must match other entries for the same interface");
            }
            int users = requireExactJsonNumberIntField(entry, "users",
                    pathPrefix + ".users", 0, Integer.MAX_VALUE);
            if (!entry.containsKey("timerRunning")) {
                throw new IllegalArgumentException(pathPrefix + ".timerRunning is required");
            }
            boolean timerRunning = requireAndroidPowerBoolean(entry.get("timerRunning"),
                    pathPrefix + ".timerRunning");
            long timerClock = requireExactJsonNumberLongField(entry, "timerClock",
                    pathPrefix + ".timerClock", 0L, NETWORK_IGMP_TIMER_CLOCK_MAX);
            if (!timerRunning && timerClock != 0L) {
                throw new IllegalArgumentException(pathPrefix
                        + ".timerClock must be 0 when timerRunning is false");
            }
            if (!entry.containsKey("reporter")) {
                throw new IllegalArgumentException(pathPrefix + ".reporter is required");
            }
            boolean reporter = requireAndroidPowerBoolean(entry.get("reporter"),
                    pathPrefix + ".reporter");
            built.add(new NetworkIgmpMembershipConfig(interfaceName, groupIpv4, querierVersion,
                    users, timerRunning, timerClock, reporter));
        }
        this.networkIgmpMemberships = Collections.unmodifiableList(built);
    }

    private static boolean isAllowedNetworkIgmpMembershipKey(String key) {
        for (String allowed : NETWORK_IGMP_MEMBERSHIP_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exact IGMP querier tokens printed on the {@code /proc/net/igmp} interface title line.
     */
    private static String requireIgmpQuerierVersion(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path + " must be a JSON String");
        }
        String text = (String) raw;
        if (!"V1".equals(text) && !"V2".equals(text) && !"V3".equals(text)) {
            throw new IllegalArgumentException(path + " must be one of V1|V2|V3");
        }
        return text;
    }

    /**
     * {@code groupIpv4} must already be a strict dotted IPv4, then restricted to {@code 224.0.0.0/4}.
     */
    private static void requireIpv4MulticastGroup(String ipv4, String path) {
        String[] parts = ipv4.split("\\.", -1);
        int first = Integer.parseInt(parts[0]);
        if (first < 224 || first > 239) {
            throw new IllegalArgumentException(path
                    + " must be an IPv4 multicast address in 224.0.0.0/4");
        }
    }

    private static final String[] NETWORK_IGMP6_MEMBERSHIP_ALLOWED_KEYS = {
            "interfaceName", "groupIpv6", "users", "flags", "timer"
    };

    private static final long NETWORK_IGMP6_FLAGS_MAX = 4294967295L;

    /**
     * Strict parse-time rules for optional {@code network.igmp6Memberships} when the key is present.
     * Missing key does not take over any {@code /proc/net/igmp6} path. Explicit {@code []} is
     * configured empty (zero-byte file, no header). Every object uses a required whitelist;
     * group, users, flags, and timer are never inferred from interfaces, ipv4Routes,
     * interfaceStats, ipv6Addresses, arpEntries, igmpMemberships, wifi, or other fields.
     * {@code interfaceName} must match a unique configured {@code network.interfaces[]} name.
     * {@code interfaceName}+parsed {@code groupIpv6} (16-byte network-order address) pairs must
     * be unique within this array. {@code groupIpv6} must be strict IPv6 text in {@code ff00::/8}.
     */
    private void validateNetworkIgmp6Memberships() {
        if (!isNetworkIgmp6MembershipsConfigured()) {
            this.networkIgmp6Memberships = Collections.emptyList();
            return;
        }
        JSONObject network = section("network");
        Object raw = network.get("igmp6Memberships");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("network.igmp6Memberships must be a JSON array");
        }
        JSONArray array = (JSONArray) raw;
        Set<String> configuredNames = new HashSet<String>();
        for (NetworkInterfaceConfig iface : networkInterfaces) {
            configuredNames.add(iface.getName());
        }
        Set<String> seenPairs = new HashSet<String>();
        List<NetworkIgmp6MembershipConfig> built =
                new ArrayList<NetworkIgmp6MembershipConfig>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String pathPrefix = "network.igmp6Memberships[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject entry = (JSONObject) item;
            for (String field : entry.keySet()) {
                if (!isAllowedNetworkIgmp6MembershipKey(field)) {
                    throw new IllegalArgumentException(pathPrefix + "." + field
                            + " is not an allowed key (interfaceName|groupIpv6|users|flags|timer)");
                }
            }
            if (!entry.containsKey("interfaceName")) {
                throw new IllegalArgumentException(pathPrefix + ".interfaceName is required");
            }
            String interfaceName = requireInterfaceNameString(entry.get("interfaceName"),
                    pathPrefix + ".interfaceName");
            if (!configuredNames.contains(interfaceName)) {
                throw new IllegalArgumentException(pathPrefix
                        + ".interfaceName is not a configured network.interfaces name: "
                        + interfaceName);
            }
            if (!entry.containsKey("groupIpv6")) {
                throw new IllegalArgumentException(pathPrefix + ".groupIpv6 is required");
            }
            Object groupRaw = entry.get("groupIpv6");
            if (!(groupRaw instanceof String)) {
                throw new IllegalArgumentException(pathPrefix + ".groupIpv6 must be a String");
            }
            String groupIpv6 = (String) groupRaw;
            byte[] groupBytes = parseStrictIpv6Text(groupIpv6, pathPrefix + ".groupIpv6");
            if ((groupBytes[0] & 0xff) != 0xff) {
                throw new IllegalArgumentException(pathPrefix
                        + ".groupIpv6 must be an IPv6 multicast address in ff00::/8");
            }
            String groupIpv6Hex = toUpperHex32(groupBytes);
            String pairKey = interfaceName + '\0' + groupIpv6Hex;
            if (!seenPairs.add(pairKey)) {
                throw new IllegalArgumentException(pathPrefix
                        + ".interfaceName and groupIpv6 combination is not unique");
            }
            int users = requireExactJsonNumberIntField(entry, "users",
                    pathPrefix + ".users", 0, Integer.MAX_VALUE);
            long flags = requireExactJsonNumberLongField(entry, "flags",
                    pathPrefix + ".flags", 0L, NETWORK_IGMP6_FLAGS_MAX);
            long timer = requireExactJsonNumberLongField(entry, "timer",
                    pathPrefix + ".timer", 0L, Long.MAX_VALUE);
            built.add(new NetworkIgmp6MembershipConfig(interfaceName, groupIpv6, groupIpv6Hex,
                    users, flags, timer));
        }
        this.networkIgmp6Memberships = Collections.unmodifiableList(built);
    }

    private static boolean isAllowedNetworkIgmp6MembershipKey(String key) {
        for (String allowed : NETWORK_IGMP6_MEMBERSHIP_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static final String[] NETWORK_LINK_LAYER_MULTICAST_ENTRY_ALLOWED_KEYS = {
            "interfaceName", "mac", "referenceCount", "globalUse"
    };

    /**
     * Strict parse-time rules for optional {@code network.linkLayerMulticastEntries} when the
     * key is present. Missing key does not take over any {@code /proc/net/dev_mcast} path.
     * Explicit {@code []} is configured empty (zero-byte file, no header). Every object uses
     * a required whitelist; MAC, reference count, and {@code globalUse} are never inferred
     * from interfaces (including {@code mac} and {@code linkLayerBroadcast}), ipv4Routes,
     * interfaceStats, ipv6Addresses, arpEntries, igmpMemberships, igmp6Memberships, wifi,
     * or other fields. {@code interfaceName} must match a unique configured
     * {@code network.interfaces[]} name. {@code interfaceName}+normalized {@code mac} pairs
     * must be unique within this array. MAC uses the same colon-hex rules as
     * {@code network.interfaces[].mac} and is stored lowercase.
     */
    private void validateNetworkLinkLayerMulticastEntries() {
        if (!isNetworkLinkLayerMulticastEntriesConfigured()) {
            this.networkLinkLayerMulticastEntries = Collections.emptyList();
            return;
        }
        JSONObject network = section("network");
        Object raw = network.get("linkLayerMulticastEntries");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("network.linkLayerMulticastEntries must be a JSON array");
        }
        JSONArray array = (JSONArray) raw;
        Set<String> configuredNames = new HashSet<String>();
        for (NetworkInterfaceConfig iface : networkInterfaces) {
            configuredNames.add(iface.getName());
        }
        Set<String> seenPairs = new HashSet<String>();
        List<NetworkLinkLayerMulticastEntryConfig> built =
                new ArrayList<NetworkLinkLayerMulticastEntryConfig>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String pathPrefix = "network.linkLayerMulticastEntries[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject entry = (JSONObject) item;
            for (String field : entry.keySet()) {
                if (!isAllowedNetworkLinkLayerMulticastEntryKey(field)) {
                    throw new IllegalArgumentException(pathPrefix + "." + field
                            + " is not an allowed key (interfaceName|mac|referenceCount|globalUse)");
                }
            }
            if (!entry.containsKey("interfaceName")) {
                throw new IllegalArgumentException(pathPrefix + ".interfaceName is required");
            }
            String interfaceName = requireInterfaceNameString(entry.get("interfaceName"),
                    pathPrefix + ".interfaceName");
            if (!configuredNames.contains(interfaceName)) {
                throw new IllegalArgumentException(pathPrefix
                        + ".interfaceName is not a configured network.interfaces name: "
                        + interfaceName);
            }
            if (!entry.containsKey("mac")) {
                throw new IllegalArgumentException(pathPrefix + ".mac is required");
            }
            String mac = requireMacStringValue(entry.get("mac"), pathPrefix + ".mac");
            String pairKey = interfaceName + '\0' + mac;
            if (!seenPairs.add(pairKey)) {
                throw new IllegalArgumentException(pathPrefix
                        + ".interfaceName and mac combination is not unique");
            }
            int referenceCount = requireExactJsonNumberIntField(entry, "referenceCount",
                    pathPrefix + ".referenceCount", 1, Integer.MAX_VALUE);
            if (!entry.containsKey("globalUse")) {
                throw new IllegalArgumentException(pathPrefix + ".globalUse is required");
            }
            boolean globalUse = requireAndroidPowerBoolean(entry.get("globalUse"),
                    pathPrefix + ".globalUse");
            built.add(new NetworkLinkLayerMulticastEntryConfig(interfaceName, mac,
                    referenceCount, globalUse));
        }
        this.networkLinkLayerMulticastEntries = Collections.unmodifiableList(built);
    }

    private static boolean isAllowedNetworkLinkLayerMulticastEntryKey(String key) {
        for (String allowed : NETWORK_LINK_LAYER_MULTICAST_ENTRY_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static final String[] NETWORK_WIRELESS_PROC_STATS_ALLOWED_KEYS = {
            "wirelessExtensionsVersion", "entries"
    };

    private static final String[] NETWORK_WIRELESS_PROC_STATS_ENTRY_ALLOWED_KEYS = {
            "interfaceName", "status", "linkQuality", "level", "noise",
            "linkUpdated", "levelUpdated", "noiseUpdated",
            "discardNwid", "discardCrypt", "discardFragment", "discardRetries",
            "discardMisc", "missedBeacon"
    };

    private static final long NETWORK_WIRELESS_DISCARD_MAX = 4294967295L;

    /**
     * Strict parse-time rules for optional {@code network.wirelessProcStats} when the key is
     * present. Missing key does not take over any {@code /proc/net/wireless} path. The object
     * allows only required {@code wirelessExtensionsVersion} and {@code entries}. Explicit
     * empty {@code entries} is configured and renders the two header lines only. Every entry
     * uses a required whitelist; values are never inferred from interfaces, ipv4Routes,
     * interfaceStats, ipv6Addresses, arpEntries, igmpMemberships, igmp6Memberships,
     * linkLayerMulticastEntries, wifi, or other fields. {@code interfaceName} must match a
     * unique configured
     * {@code network.interfaces[]} name and must be unique within {@code entries}.
     * {@code level} and {@code noise} are final signed /proc numbers.
     */
    private void validateNetworkWirelessProcStats() {
        if (!isNetworkWirelessProcStatsConfigured()) {
            this.networkWirelessProcStats = null;
            return;
        }
        JSONObject network = section("network");
        Object raw = network.get("wirelessProcStats");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("network.wirelessProcStats must be a JSONObject");
        }
        JSONObject object = (JSONObject) raw;
        for (String field : object.keySet()) {
            if (!isAllowedNetworkWirelessProcStatsKey(field)) {
                throw new IllegalArgumentException("network.wirelessProcStats." + field
                        + " is not an allowed key (wirelessExtensionsVersion|entries)");
            }
        }
        int wirelessExtensionsVersion = requireExactJsonNumberIntField(object,
                "wirelessExtensionsVersion",
                "network.wirelessProcStats.wirelessExtensionsVersion", 0, 999);
        if (!object.containsKey("entries")) {
            throw new IllegalArgumentException("network.wirelessProcStats.entries is required");
        }
        Object entriesRaw = object.get("entries");
        if (!(entriesRaw instanceof JSONArray)) {
            throw new IllegalArgumentException("network.wirelessProcStats.entries must be a JSON array");
        }
        JSONArray array = (JSONArray) entriesRaw;
        Set<String> configuredNames = new HashSet<String>();
        for (NetworkInterfaceConfig iface : networkInterfaces) {
            configuredNames.add(iface.getName());
        }
        Set<String> seenNames = new HashSet<String>();
        List<NetworkWirelessProcStatsEntryConfig> built =
                new ArrayList<NetworkWirelessProcStatsEntryConfig>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String pathPrefix = "network.wirelessProcStats.entries[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                throw new IllegalArgumentException(pathPrefix + " must be a JSONObject");
            }
            JSONObject entry = (JSONObject) item;
            for (String field : entry.keySet()) {
                if (!isAllowedNetworkWirelessProcStatsEntryKey(field)) {
                    throw new IllegalArgumentException(pathPrefix + "." + field
                            + " is not an allowed key (interfaceName|status|linkQuality|level|"
                            + "noise|linkUpdated|levelUpdated|noiseUpdated|discardNwid|"
                            + "discardCrypt|discardFragment|discardRetries|discardMisc|"
                            + "missedBeacon)");
                }
            }
            if (!entry.containsKey("interfaceName")) {
                throw new IllegalArgumentException(pathPrefix + ".interfaceName is required");
            }
            String interfaceName = requireInterfaceNameString(entry.get("interfaceName"),
                    pathPrefix + ".interfaceName");
            if (!configuredNames.contains(interfaceName)) {
                throw new IllegalArgumentException(pathPrefix
                        + ".interfaceName is not a configured network.interfaces name: "
                        + interfaceName);
            }
            if (!seenNames.add(interfaceName)) {
                throw new IllegalArgumentException(pathPrefix
                        + ".interfaceName is not unique: " + interfaceName);
            }
            int status = requireExactJsonNumberIntField(entry, "status",
                    pathPrefix + ".status", 0, 65535);
            int linkQuality = requireExactJsonNumberIntField(entry, "linkQuality",
                    pathPrefix + ".linkQuality", 0, 255);
            int level = requireExactJsonNumberIntField(entry, "level",
                    pathPrefix + ".level", -256, 255);
            int noise = requireExactJsonNumberIntField(entry, "noise",
                    pathPrefix + ".noise", -256, 255);
            if (!entry.containsKey("linkUpdated")) {
                throw new IllegalArgumentException(pathPrefix + ".linkUpdated is required");
            }
            boolean linkUpdated = requireAndroidPowerBoolean(entry.get("linkUpdated"),
                    pathPrefix + ".linkUpdated");
            if (!entry.containsKey("levelUpdated")) {
                throw new IllegalArgumentException(pathPrefix + ".levelUpdated is required");
            }
            boolean levelUpdated = requireAndroidPowerBoolean(entry.get("levelUpdated"),
                    pathPrefix + ".levelUpdated");
            if (!entry.containsKey("noiseUpdated")) {
                throw new IllegalArgumentException(pathPrefix + ".noiseUpdated is required");
            }
            boolean noiseUpdated = requireAndroidPowerBoolean(entry.get("noiseUpdated"),
                    pathPrefix + ".noiseUpdated");
            long discardNwid = requireExactJsonNumberLongField(entry, "discardNwid",
                    pathPrefix + ".discardNwid", 0L, NETWORK_WIRELESS_DISCARD_MAX);
            long discardCrypt = requireExactJsonNumberLongField(entry, "discardCrypt",
                    pathPrefix + ".discardCrypt", 0L, NETWORK_WIRELESS_DISCARD_MAX);
            long discardFragment = requireExactJsonNumberLongField(entry, "discardFragment",
                    pathPrefix + ".discardFragment", 0L, NETWORK_WIRELESS_DISCARD_MAX);
            long discardRetries = requireExactJsonNumberLongField(entry, "discardRetries",
                    pathPrefix + ".discardRetries", 0L, NETWORK_WIRELESS_DISCARD_MAX);
            long discardMisc = requireExactJsonNumberLongField(entry, "discardMisc",
                    pathPrefix + ".discardMisc", 0L, NETWORK_WIRELESS_DISCARD_MAX);
            long missedBeacon = requireExactJsonNumberLongField(entry, "missedBeacon",
                    pathPrefix + ".missedBeacon", 0L, NETWORK_WIRELESS_DISCARD_MAX);
            built.add(new NetworkWirelessProcStatsEntryConfig(interfaceName, status,
                    linkQuality, level, noise, linkUpdated, levelUpdated, noiseUpdated,
                    discardNwid, discardCrypt, discardFragment, discardRetries,
                    discardMisc, missedBeacon));
        }
        this.networkWirelessProcStats = new NetworkWirelessProcStatsConfig(
                wirelessExtensionsVersion, Collections.unmodifiableList(built));
    }

    private static boolean isAllowedNetworkWirelessProcStatsKey(String key) {
        for (String allowed : NETWORK_WIRELESS_PROC_STATS_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedNetworkWirelessProcStatsEntryKey(String key) {
        for (String allowed : NETWORK_WIRELESS_PROC_STATS_ENTRY_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Strict RFC 4291 IPv6 textual form: eight hextets of 1..4 hex digits, optional single
     * {@code ::} compression. Rejects whitespace, zone ids, dotted IPv4 tails, brackets,
     * prefix length, and DNS names. Does not perform host lookup.
     *
     * @return 16 bytes in network order
     */
    private static byte[] parseStrictIpv6Text(String text, String path) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException(path + " must be a strict IPv6 address string");
        }
        if (!text.equals(text.trim()) || text.indexOf(' ') >= 0 || text.indexOf('\t') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(path
                    + " must be a strict IPv6 address string without whitespace");
        }
        if (text.indexOf('%') >= 0 || text.indexOf('.') >= 0 || text.indexOf('/') >= 0
                || text.indexOf('[') >= 0 || text.indexOf(']') >= 0) {
            throw new IllegalArgumentException(path + " must be a strict IPv6 address string");
        }
        int compression = -1;
        int searchFrom = 0;
        while (true) {
            int at = text.indexOf("::", searchFrom);
            if (at < 0) {
                break;
            }
            if (compression >= 0) {
                throw new IllegalArgumentException(path + " must be a strict IPv6 address string");
            }
            compression = at;
            searchFrom = at + 2;
        }
        String[] head;
        String[] tail;
        if (compression >= 0) {
            head = splitIpv6Hextets(text.substring(0, compression));
            tail = splitIpv6Hextets(text.substring(compression + 2));
            if (head.length + tail.length >= 8) {
                throw new IllegalArgumentException(path + " must be a strict IPv6 address string");
            }
        } else {
            head = splitIpv6Hextets(text);
            tail = new String[0];
            if (head.length != 8) {
                throw new IllegalArgumentException(path + " must be a strict IPv6 address string");
            }
        }
        int[] hextets = new int[8];
        int i = 0;
        for (int h = 0; h < head.length; h++) {
            hextets[i++] = parseIpv6Hextet(head[h], path);
        }
        i += 8 - head.length - tail.length;
        for (int t = 0; t < tail.length; t++) {
            hextets[i++] = parseIpv6Hextet(tail[t], path);
        }
        byte[] out = new byte[16];
        for (int h = 0; h < 8; h++) {
            out[h * 2] = (byte) ((hextets[h] >>> 8) & 0xff);
            out[h * 2 + 1] = (byte) (hextets[h] & 0xff);
        }
        return out;
    }

    private static String[] splitIpv6Hextets(String part) {
        if (part.isEmpty()) {
            return new String[0];
        }
        return part.split(":", -1);
    }

    private static int parseIpv6Hextet(String hextet, String path) {
        if (hextet == null || hextet.isEmpty() || hextet.length() > 4) {
            throw new IllegalArgumentException(path + " must be a strict IPv6 address string");
        }
        int value = 0;
        for (int i = 0; i < hextet.length(); i++) {
            char c = hextet.charAt(i);
            int d;
            if (c >= '0' && c <= '9') {
                d = c - '0';
            } else if (c >= 'a' && c <= 'f') {
                d = c - 'a' + 10;
            } else if (c >= 'A' && c <= 'F') {
                d = c - 'A' + 10;
            } else {
                throw new IllegalArgumentException(path + " must be a strict IPv6 address string");
            }
            value = (value << 4) | d;
        }
        return value;
    }

    private static String toUpperHex32(byte[] addr) {
        char[] hex = new char[32];
        for (int i = 0; i < 16; i++) {
            int v = addr[i] & 0xff;
            hex[i * 2] = "0123456789ABCDEF".charAt(v >>> 4);
            hex[i * 2 + 1] = "0123456789ABCDEF".charAt(v & 0x0f);
        }
        return new String(hex);
    }

    /**
     * Exact lowercase IF_OPER_* sysfs tokens for {@code network.interfaces[].operState}.
     * Explicit JSON values must match one of these strings; never inferred from flags/wifi.
     */
    private static final Set<String> NETWORK_INTERFACE_OPER_STATES;
    static {
        Set<String> states = new HashSet<String>();
        states.add("unknown");
        states.add("notpresent");
        states.add("down");
        states.add("lowerlayerdown");
        states.add("testing");
        states.add("dormant");
        states.add("up");
        NETWORK_INTERFACE_OPER_STATES = Collections.unmodifiableSet(states);
    }

    /**
     * Optional {@code operState}: omitted key is unconfigured; explicit value must be a JSON
     * String equal to one of {@link #NETWORK_INTERFACE_OPER_STATES}. Null, non-String, case
     * variants, and unknown tokens fail parse. Never inferred from flags or wifi.
     */
    private static String requireNetworkInterfaceOperState(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path + " must be a JSON String");
        }
        String text = (String) raw;
        if (!NETWORK_INTERFACE_OPER_STATES.contains(text)) {
            throw new IllegalArgumentException(path
                    + " must be one of unknown|notpresent|down|lowerlayerdown|testing|dormant|up");
        }
        return text;
    }

    /**
     * Exact lowercase DUPLEX_* sysfs tokens for {@code network.interfaces[].duplex}.
     * Explicit JSON values must match one of these strings; never inferred from
     * speedMbps, carrier, operState, hardwareType, flags, name, or wifi.
     */
    private static final Set<String> NETWORK_INTERFACE_DUPLEX_VALUES;
    static {
        Set<String> values = new HashSet<String>();
        values.add("full");
        values.add("half");
        values.add("unknown");
        NETWORK_INTERFACE_DUPLEX_VALUES = Collections.unmodifiableSet(values);
    }

    /**
     * Optional {@code duplex}: omitted key is unconfigured; explicit value must be a JSON
     * String equal to one of {@link #NETWORK_INTERFACE_DUPLEX_VALUES}. Null, non-String, case
     * variants, and unknown tokens fail parse. Never inferred from speedMbps, carrier,
     * operState, hardwareType, flags, name, or wifi.
     */
    private static String requireNetworkInterfaceDuplex(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path + " must be a JSON String");
        }
        String text = (String) raw;
        if (!NETWORK_INTERFACE_DUPLEX_VALUES.contains(text)) {
            throw new IllegalArgumentException(path
                    + " must be one of full|half|unknown");
        }
        return text;
    }

    /**
     * Optional {@code displayName}: explicit JSON null is allowed (returns null); non-null must be
     * a nonempty String of length {@code 1..128}. Never falls back to interface {@code name}.
     */
    private static String requireNetworkInterfaceDisplayName(Object raw, String path) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path + " must be a String or null");
        }
        String text = (String) raw;
        if (text.isEmpty() || text.length() > 128) {
            throw new IllegalArgumentException(path
                    + " must be a nonempty String of length 1..128, or null");
        }
        return text;
    }

    /**
     * Parse-time rules for optional {@code network.wifi} when present.
     * Paths: {@code network.wifi}, {@code network.wifi.&lt;field&gt;}.
     */
    private void validateNetworkWifi() {
        JSONObject network = section("network");
        if (network == null || !network.containsKey("wifi")) {
            return;
        }
        Object wifiRaw = network.get("wifi");
        if (!(wifiRaw instanceof JSONObject)) {
            throw new IllegalArgumentException("network.wifi must be a JSONObject");
        }
        JSONObject wifi = (JSONObject) wifiRaw;
        for (String key : wifi.keySet()) {
            if (!isAllowedNetworkWifiKey(key)) {
                throw new IllegalArgumentException("network.wifi." + key
                        + " is not an allowed key (enabled|ssid|bssid|macAddress|ipv4|"
                        + "rssi|linkSpeedMbps|frequencyMhz|networkId|state|scanResults)");
            }
        }
        if (wifi.containsKey("enabled")) {
            Object enabled = wifi.get("enabled");
            if (!(enabled instanceof Boolean)) {
                throw new IllegalArgumentException("network.wifi.enabled must be a Boolean");
            }
        }
        if (wifi.containsKey("ssid")) {
            validateTelephonyIdentifierValue(wifi.get("ssid"), "network.wifi.ssid");
        }
        if (wifi.containsKey("bssid")) {
            Object bssid = wifi.get("bssid");
            if (bssid != null) {
                wifi.put("bssid", requireMacStringValue(bssid, "network.wifi.bssid"));
            }
        }
        if (wifi.containsKey("macAddress")) {
            Object macAddress = wifi.get("macAddress");
            if (macAddress != null) {
                wifi.put("macAddress", requireMacStringValue(macAddress, "network.wifi.macAddress"));
            }
        }
        if (wifi.containsKey("ipv4")) {
            Object ipv4 = wifi.get("ipv4");
            if (ipv4 != null) {
                if (!(ipv4 instanceof String)) {
                    throw new IllegalArgumentException("network.wifi.ipv4 must be a String or JSON null");
                }
                validateIpv4Literal("network.wifi.ipv4", (String) ipv4);
            }
        }
        if (wifi.containsKey("rssi")) {
            requireExactJsonNumberIntField(wifi, "rssi", "network.wifi.rssi", -127, 0);
        }
        if (wifi.containsKey("linkSpeedMbps")) {
            requireExactJsonNumberIntField(wifi, "linkSpeedMbps", "network.wifi.linkSpeedMbps", 0, 100000);
        }
        if (wifi.containsKey("frequencyMhz")) {
            requireExactJsonNumberIntField(wifi, "frequencyMhz", "network.wifi.frequencyMhz", 0, 100000);
        }
        if (wifi.containsKey("networkId")) {
            requireExactJsonNumberIntField(wifi, "networkId", "network.wifi.networkId", -1, Integer.MAX_VALUE);
        }
        // WIFI_STATE_* 0..4; independent of enabled (no inference)
        if (wifi.containsKey("state")) {
            requireExactJsonNumberIntField(wifi, "state", "network.wifi.state",
                    NETWORK_WIFI_STATE_MIN, NETWORK_WIFI_STATE_MAX);
        }
        validateNetworkWifiScanResults(wifi);
    }

    private static boolean isAllowedNetworkWifiKey(String key) {
        for (String allowed : NETWORK_WIFI_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse-time rules for optional {@code network.bluetooth} when present (v1 subset).
     * Missing node leaves {@link #isNetworkBluetoothConfigured()} false; explicit empty object
     * is configured with no field flags set. Fields are independent (never cross-inferred;
     * state/scanMode never derived from enabled). Materializes {@link NetworkBluetoothConfig};
     * never retains JSONObject.
     */
    private void validateNetworkBluetooth() {
        JSONObject network = section("network");
        if (network == null || !network.containsKey("bluetooth")) {
            this.networkBluetoothConfigured = false;
            this.networkBluetoothConfig = null;
            return;
        }
        Object raw = network.get("bluetooth");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("network.bluetooth must be a JSONObject");
        }
        JSONObject bluetooth = (JSONObject) raw;
        for (String key : bluetooth.keySet()) {
            if (!isAllowedNetworkBluetoothKey(key)) {
                throw new IllegalArgumentException("network.bluetooth." + key
                        + " is not an allowed key (name|address|enabled|state|scanMode|discovering)");
            }
        }

        boolean nameConfigured = bluetooth.containsKey("name");
        String name = null;
        if (nameConfigured) {
            Object nameRaw = bluetooth.get("name");
            if (nameRaw != null) {
                validateTelephonyIdentifierValue(nameRaw, "network.bluetooth.name");
                name = (String) nameRaw;
            }
        }

        boolean addressConfigured = bluetooth.containsKey("address");
        String address = null;
        if (addressConfigured) {
            Object addressRaw = bluetooth.get("address");
            if (addressRaw != null) {
                address = requireMacStringValue(addressRaw, "network.bluetooth.address");
            }
        }

        boolean enabledConfigured = bluetooth.containsKey("enabled");
        boolean enabled = false;
        if (enabledConfigured) {
            Object enabledRaw = bluetooth.get("enabled");
            if (!(enabledRaw instanceof Boolean)) {
                throw new IllegalArgumentException("network.bluetooth.enabled must be a Boolean");
            }
            enabled = ((Boolean) enabledRaw).booleanValue();
        }

        boolean stateConfigured = bluetooth.containsKey("state");
        int state = 0;
        if (stateConfigured) {
            state = requireNetworkBluetoothRestrictedInt(
                    bluetooth.get("state"),
                    "network.bluetooth.state",
                    NETWORK_BLUETOOTH_STATES,
                    "{10,11,12,13}");
        }

        boolean scanModeConfigured = bluetooth.containsKey("scanMode");
        int scanMode = 0;
        if (scanModeConfigured) {
            scanMode = requireNetworkBluetoothRestrictedInt(
                    bluetooth.get("scanMode"),
                    "network.bluetooth.scanMode",
                    NETWORK_BLUETOOTH_SCAN_MODES,
                    "{20,21,23}");
        }

        boolean discoveringConfigured = bluetooth.containsKey("discovering");
        boolean discovering = false;
        if (discoveringConfigured) {
            Object discoveringRaw = bluetooth.get("discovering");
            if (!(discoveringRaw instanceof Boolean)) {
                throw new IllegalArgumentException("network.bluetooth.discovering must be a Boolean");
            }
            discovering = ((Boolean) discoveringRaw).booleanValue();
        }

        this.networkBluetoothConfigured = true;
        this.networkBluetoothConfig = new NetworkBluetoothConfig(
                name, nameConfigured,
                address, addressConfigured,
                enabled, enabledConfigured,
                state, stateConfigured,
                scanMode, scanModeConfigured,
                discovering, discoveringConfigured);
    }

    private static boolean isAllowedNetworkBluetoothKey(String key) {
        for (String allowed : NETWORK_BLUETOOTH_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exact JSON Number integer restricted to the given framework constant set.
     */
    private static int requireNetworkBluetoothRestrictedInt(Object raw, String path,
                                                            Set<Integer> allowed, String allowedLabel) {
        if (!(raw instanceof Number) || raw instanceof Boolean) {
            throw new IllegalArgumentException(path
                    + " must be an exact JSON Number integer in " + allowedLabel);
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(path
                        + " must be an exact JSON Number integer in " + allowedLabel);
            }
        } else if (raw instanceof BigDecimal) {
            try {
                value = ((BigDecimal) raw).toBigIntegerExact().longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(path
                        + " must be an exact JSON Number integer in " + allowedLabel);
            }
        } else if (raw instanceof Float || raw instanceof Double) {
            double d = ((Number) raw).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d)) {
                throw new IllegalArgumentException(path
                        + " must be an exact JSON Number integer in " + allowedLabel);
            }
            value = (long) d;
        } else {
            value = ((Number) raw).longValue();
        }
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE
                || !allowed.contains(Integer.valueOf((int) value))) {
            throw new IllegalArgumentException(path
                    + " must be an exact JSON Number integer in " + allowedLabel);
        }
        return (int) value;
    }

    /**
     * Parse-time rules for optional {@code network.links} when present.
     * Paths: {@code network.links}, {@code network.links.&lt;field&gt;}, {@code network.links.dnsServers[i]}.
     * Materializes {@link #networkLinkDnsServers} when {@code dnsServers} is present.
     */
    private void validateNetworkLinks() {
        JSONObject network = section("network");
        if (network == null || !network.containsKey("links")) {
            this.networkLinkDnsServers = Collections.emptyList();
            return;
        }
        Object linksRaw = network.get("links");
        if (!(linksRaw instanceof JSONObject)) {
            throw new IllegalArgumentException("network.links must be a JSONObject");
        }
        JSONObject links = (JSONObject) linksRaw;
        for (String key : links.keySet()) {
            if (!isAllowedNetworkLinksKey(key)) {
                throw new IllegalArgumentException("network.links." + key
                        + " is not an allowed key (connected|type|typeName|interfaceName|dnsServers|"
                        + "gatewayIpv4|mtu|privateDnsActive|privateDnsServerName|proxyHost|proxyPort|"
                        + "dhcpServerIpv4|leaseDurationSeconds|domains|netmaskIpv4)");
            }
        }
        if (links.containsKey("connected")) {
            Object connected = links.get("connected");
            if (!(connected instanceof Boolean)) {
                throw new IllegalArgumentException("network.links.connected must be a Boolean");
            }
        }
        if (links.containsKey("privateDnsActive")) {
            Object privateDnsActive = links.get("privateDnsActive");
            if (!(privateDnsActive instanceof Boolean)) {
                throw new IllegalArgumentException("network.links.privateDnsActive must be a Boolean");
            }
        }
        if (links.containsKey("type")) {
            requireExactJsonNumberIntField(links, "type", "network.links.type", -1, 17);
        }
        if (links.containsKey("typeName")) {
            Object typeName = links.get("typeName");
            if (!(typeName instanceof String)) {
                throw new IllegalArgumentException("network.links.typeName must be a String");
            }
            String text = (String) typeName;
            if (text.isEmpty() || !text.equals(text.trim())) {
                throw new IllegalArgumentException(
                        "network.links.typeName must be a non-empty String without leading or trailing whitespace");
            }
        }
        if (links.containsKey("interfaceName")) {
            requireInterfaceNameString(links.get("interfaceName"), "network.links.interfaceName");
        }
        if (links.containsKey("dnsServers")) {
            Object dnsRaw = links.get("dnsServers");
            if (!(dnsRaw instanceof JSONArray)) {
                throw new IllegalArgumentException("network.links.dnsServers must be a JSON array");
            }
            JSONArray dnsArray = (JSONArray) dnsRaw;
            Set<String> seen = new HashSet<String>();
            List<String> built = new ArrayList<String>(dnsArray.size());
            for (int i = 0; i < dnsArray.size(); i++) {
                String path = "network.links.dnsServers[" + i + "]";
                Object item = dnsArray.get(i);
                if (!(item instanceof String)) {
                    throw new IllegalArgumentException(path + " must be a String");
                }
                String ip = (String) item;
                validateIpv4Literal(path, ip);
                if (!seen.add(ip)) {
                    throw new IllegalArgumentException(path + " is not unique: " + ip);
                }
                built.add(ip);
            }
            this.networkLinkDnsServers = Collections.unmodifiableList(built);
        } else {
            this.networkLinkDnsServers = Collections.emptyList();
        }
        if (links.containsKey("gatewayIpv4")) {
            validateOptionalNullableIpv4String(links.get("gatewayIpv4"), "network.links.gatewayIpv4");
        }
        if (links.containsKey("mtu")) {
            requireExactJsonNumberIntField(links, "mtu", "network.links.mtu", 68, 65536);
        }
        if (links.containsKey("privateDnsServerName")) {
            validateOptionalNullableNonEmptyString(links.get("privateDnsServerName"),
                    "network.links.privateDnsServerName");
        }
        if (links.containsKey("proxyHost")) {
            validateOptionalNullableNonEmptyString(links.get("proxyHost"), "network.links.proxyHost");
        }
        if (links.containsKey("proxyPort")) {
            requireExactJsonNumberIntField(links, "proxyPort", "network.links.proxyPort", 0, 65535);
        }
        if (links.containsKey("dhcpServerIpv4")) {
            validateOptionalNullableIpv4String(links.get("dhcpServerIpv4"), "network.links.dhcpServerIpv4");
        }
        if (links.containsKey("netmaskIpv4")) {
            validateOptionalNullableIpv4String(links.get("netmaskIpv4"), "network.links.netmaskIpv4");
        }
        if (links.containsKey("leaseDurationSeconds")) {
            requireExactJsonNumberIntField(links, "leaseDurationSeconds",
                    "network.links.leaseDurationSeconds", 0, Integer.MAX_VALUE);
        }
        if (links.containsKey("domains")) {
            validateOptionalNullableNonEmptyString(links.get("domains"), "network.links.domains");
        }
    }

    /** String or JSON null; non-null must be non-empty without leading/trailing whitespace. */
    private static void validateOptionalNullableNonEmptyString(Object value, String path) {
        if (value == null) {
            return;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(path + " must be a String or JSON null");
        }
        String text = (String) value;
        if (text.isEmpty() || !text.equals(text.trim())) {
            throw new IllegalArgumentException(path
                    + " must be a non-empty String without leading or trailing whitespace");
        }
    }

    /** String or JSON null; non-null must be strict dotted-decimal IPv4. */
    private static void validateOptionalNullableIpv4String(Object value, String path) {
        if (value == null) {
            return;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(path + " must be a String or JSON null");
        }
        validateIpv4Literal(path, (String) value);
    }

    private static boolean isAllowedNetworkLinksKey(String key) {
        for (String allowed : NETWORK_LINKS_ALLOWED_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static final String[] NETWORK_TCP_ALLOWED_KEYS = {
            "slot", "localIpv4", "localIpv6", "localPort", "remoteIpv4", "remoteIpv6",
            "remotePort", "stateHex", "txQueue", "rxQueue", "uid", "timeout", "inode"
    };

    private static final String[] NETWORK_CAPABILITIES_ALLOWED_KEYS = {
            "transportTypes", "networkCapabilities"
    };

    private static final String[] LINUX_PROCESS_ALLOWED_KEYS = {
            "pid", "cmdline", "comm", "exe"
    };

    private static final String[] ANDROID_DISPLAY_ENTRY_ALLOWED_KEYS = {
            "id", "name", "flags", "widthPixels", "heightPixels", "densityDpi"
    };

    private void validateNetworkTcp(boolean ipv6) {
        JSONObject network = section("network");
        String key = ipv6 ? "tcp6" : "tcp";
        if (network == null || !network.containsKey(key)) {
            if (ipv6) {
                this.networkTcp6Configured = false;
                this.networkTcp6 = Collections.emptyList();
            } else {
                this.networkTcpConfigured = false;
                this.networkTcp = Collections.emptyList();
            }
            return;
        }
        Object raw = network.get(key);
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("network." + key + " must be a JSONArray");
        }
        JSONArray array = (JSONArray) raw;
        List<NetworkTcpConfig> built = new ArrayList<NetworkTcpConfig>(array.size());
        Set<Integer> slots = new HashSet<Integer>();
        for (int i = 0; i < array.size(); i++) {
            String path = "network." + key + "[" + i + "]";
            Object entryRaw = array.get(i);
            if (!(entryRaw instanceof JSONObject)) {
                throw new IllegalArgumentException(path + " must be a JSONObject");
            }
            JSONObject entry = (JSONObject) entryRaw;
            for (String field : entry.keySet()) {
                if (!isAllowedKey(field, NETWORK_TCP_ALLOWED_KEYS)) {
                    throw new IllegalArgumentException(path + "." + field + " is not an allowed key");
                }
            }
            int slot = requireExactJsonNumberIntField(entry, "slot", path + ".slot", 0, Integer.MAX_VALUE);
            if (!slots.add(Integer.valueOf(slot))) {
                throw new IllegalArgumentException(path + ".slot duplicates " + slot);
            }
            String localAddr;
            String remoteAddr;
            if (ipv6) {
                localAddr = requireHexAddress(entry.get("localIpv6"), path + ".localIpv6", 32);
                remoteAddr = requireHexAddress(entry.get("remoteIpv6"), path + ".remoteIpv6", 32);
            } else {
                localAddr = requireIpv4StringField(entry, "localIpv4", path + ".localIpv4", true);
                remoteAddr = requireIpv4StringField(entry, "remoteIpv4", path + ".remoteIpv4", true);
            }
            int localPort = requireExactJsonNumberIntField(entry, "localPort", path + ".localPort", 0, 65535);
            int remotePort = requireExactJsonNumberIntField(entry, "remotePort", path + ".remotePort", 0, 65535);
            String stateHex = requireTcpStateHex(entry.get("stateHex"), path + ".stateHex");
            long txQueue = entry.containsKey("txQueue")
                    ? requireExactJsonNumberLongField(entry, "txQueue", path + ".txQueue", 0L, 0xffffffffL)
                    : 0L;
            long rxQueue = entry.containsKey("rxQueue")
                    ? requireExactJsonNumberLongField(entry, "rxQueue", path + ".rxQueue", 0L, 0xffffffffL)
                    : 0L;
            int uid = entry.containsKey("uid")
                    ? requireExactJsonNumberIntField(entry, "uid", path + ".uid", 0, Integer.MAX_VALUE)
                    : 0;
            int timeout = entry.containsKey("timeout")
                    ? requireExactJsonNumberIntField(entry, "timeout", path + ".timeout", 0, Integer.MAX_VALUE)
                    : 0;
            long inode = entry.containsKey("inode")
                    ? requireExactJsonNumberLongField(entry, "inode", path + ".inode", 0L, Long.MAX_VALUE)
                    : 0L;
            built.add(new NetworkTcpConfig(slot, localAddr, localPort, remoteAddr, remotePort,
                    stateHex, txQueue, rxQueue, uid, timeout, inode, ipv6));
        }
        if (ipv6) {
            this.networkTcp6Configured = true;
            this.networkTcp6 = Collections.unmodifiableList(built);
        } else {
            this.networkTcpConfigured = true;
            this.networkTcp = Collections.unmodifiableList(built);
        }
    }

    private static String requireTcpStateHex(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path + " must be a 2-digit hex String");
        }
        String text = ((String) raw).toUpperCase(Locale.ROOT);
        if (text.length() != 2) {
            throw new IllegalArgumentException(path + " must be a 2-digit hex String");
        }
        for (int i = 0; i < 2; i++) {
            if (Character.digit(text.charAt(i), 16) < 0) {
                throw new IllegalArgumentException(path + " must be a 2-digit hex String");
            }
        }
        return text;
    }

    private static String requireHexAddress(Object raw, String path, int hexLen) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path + " must be a " + hexLen + "-digit hex String");
        }
        String text = ((String) raw).toLowerCase(Locale.ROOT);
        if (text.length() != hexLen) {
            throw new IllegalArgumentException(path + " must be a " + hexLen + "-digit hex String");
        }
        for (int i = 0; i < hexLen; i++) {
            if (Character.digit(text.charAt(i), 16) < 0) {
                throw new IllegalArgumentException(path + " must be a " + hexLen + "-digit hex String");
            }
        }
        return text;
    }

    private static boolean isAllowedKey(String key, String[] allowed) {
        for (int i = 0; i < allowed.length; i++) {
            if (allowed[i].equals(key)) {
                return true;
            }
        }
        return false;
    }

    private void validateNetworkCapabilities() {
        JSONObject network = section("network");
        if (network == null || !network.containsKey("capabilities")) {
            this.networkCapabilitiesConfigured = false;
            this.networkCapabilitiesConfig = null;
            return;
        }
        Object raw = network.get("capabilities");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("network.capabilities must be a JSONObject");
        }
        JSONObject node = (JSONObject) raw;
        for (String field : node.keySet()) {
            if (!isAllowedKey(field, NETWORK_CAPABILITIES_ALLOWED_KEYS)) {
                throw new IllegalArgumentException("network.capabilities." + field
                        + " is not an allowed key");
            }
        }
        List<Integer> transports = parseUniqueIntArray(node.get("transportTypes"),
                "network.capabilities.transportTypes", 0, 63, true);
        List<Integer> caps = parseUniqueIntArray(node.get("networkCapabilities"),
                "network.capabilities.networkCapabilities", 0, 63, true);
        this.networkCapabilitiesConfigured = true;
        this.networkCapabilitiesConfig = new NetworkCapabilitiesConfig(transports, caps);
    }

    private static List<Integer> parseUniqueIntArray(Object raw, String path, int min, int max,
                                                     boolean allowMissingEmpty) {
        if (raw == null) {
            return Collections.emptyList();
        }
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException(path + " must be a JSONArray");
        }
        JSONArray array = (JSONArray) raw;
        List<Integer> built = new ArrayList<Integer>(array.size());
        Set<Integer> seen = new HashSet<Integer>();
        for (int i = 0; i < array.size(); i++) {
            int value = requireExactJsonNumberIntValue(array.get(i), path + "[" + i + "]", min, max);
            Integer boxed = Integer.valueOf(value);
            if (!seen.add(boxed)) {
                throw new IllegalArgumentException(path + "[" + i + "] duplicates " + value);
            }
            built.add(boxed);
        }
        return Collections.unmodifiableList(built);
    }

    private void validateLinuxProcesses() {
        JSONObject linux = section("linux");
        if (linux == null || !linux.containsKey("processes")) {
            this.linuxProcessesConfigured = false;
            this.linuxProcesses = Collections.emptyList();
            return;
        }
        Object raw = linux.get("processes");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("linux.processes must be a JSONArray");
        }
        JSONArray array = (JSONArray) raw;
        List<LinuxProcessConfig> built = new ArrayList<LinuxProcessConfig>(array.size());
        Set<Integer> pids = new HashSet<Integer>();
        for (int i = 0; i < array.size(); i++) {
            String path = "linux.processes[" + i + "]";
            Object entryRaw = array.get(i);
            if (!(entryRaw instanceof JSONObject)) {
                throw new IllegalArgumentException(path + " must be a JSONObject");
            }
            JSONObject entry = (JSONObject) entryRaw;
            for (String field : entry.keySet()) {
                if (!isAllowedKey(field, LINUX_PROCESS_ALLOWED_KEYS)) {
                    throw new IllegalArgumentException(path + "." + field + " is not an allowed key");
                }
            }
            int pid = requireExactJsonNumberIntField(entry, "pid", path + ".pid", 1, Integer.MAX_VALUE);
            if (!pids.add(Integer.valueOf(pid))) {
                throw new IllegalArgumentException(path + ".pid duplicates " + pid);
            }
            List<String> cmdline = Collections.emptyList();
            if (entry.containsKey("cmdline")) {
                Object cmdRaw = entry.get("cmdline");
                if (!(cmdRaw instanceof JSONArray)) {
                    throw new IllegalArgumentException(path + ".cmdline must be a JSONArray");
                }
                JSONArray cmdArray = (JSONArray) cmdRaw;
                List<String> cmdBuilt = new ArrayList<String>(cmdArray.size());
                for (int c = 0; c < cmdArray.size(); c++) {
                    Object item = cmdArray.get(c);
                    if (!(item instanceof String)) {
                        throw new IllegalArgumentException(path + ".cmdline[" + c + "] must be a String");
                    }
                    cmdBuilt.add((String) item);
                }
                cmdline = Collections.unmodifiableList(cmdBuilt);
            }
            String comm = null;
            if (entry.containsKey("comm")) {
                Object commRaw = entry.get("comm");
                if (!(commRaw instanceof String) || ((String) commRaw).isEmpty()) {
                    throw new IllegalArgumentException(path + ".comm must be a non-empty String");
                }
                comm = (String) commRaw;
            }
            String exe = null;
            if (entry.containsKey("exe")) {
                Object exeRaw = entry.get("exe");
                if (!(exeRaw instanceof String) || ((String) exeRaw).isEmpty()) {
                    throw new IllegalArgumentException(path + ".exe must be a non-empty String");
                }
                exe = (String) exeRaw;
            }
            built.add(new LinuxProcessConfig(pid, cmdline, comm, exe));
        }
        this.linuxProcessesConfigured = true;
        this.linuxProcesses = Collections.unmodifiableList(built);
    }

    private void validateLinuxCommands() {
        JSONObject linux = section("linux");
        if (linux == null || !linux.containsKey("commands")) {
            this.linuxCommandsConfigured = false;
            this.linuxCommands = Collections.emptyMap();
            return;
        }
        Object raw = linux.get("commands");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("linux.commands must be a JSONObject");
        }
        JSONObject node = (JSONObject) raw;
        Map<String, String> built = new LinkedHashMap<String, String>();
        for (String command : node.keySet()) {
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException("linux.commands keys must be non-empty Strings");
            }
            Object value = node.get(command);
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("linux.commands[\"" + command + "\"] must be a String");
            }
            built.put(command, (String) value);
        }
        this.linuxCommandsConfigured = true;
        this.linuxCommands = Collections.unmodifiableMap(built);
    }

    private void validateLinuxMincore() {
        JSONObject linux = section("linux");
        if (linux == null || !linux.containsKey("mincore")) {
            this.linuxMincoreConfigured = false;
            this.linuxMincoreResident = false;
            return;
        }
        Object raw = linux.get("mincore");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("linux.mincore must be a JSONObject");
        }
        JSONObject node = (JSONObject) raw;
        if (node.size() == 1 && node.containsKey("resident")) {
            Object residentRaw = node.get("resident");
            if (!(residentRaw instanceof Boolean)) {
                throw new IllegalArgumentException("linux.mincore.resident must be a Boolean");
            }
            this.linuxMincoreConfigured = true;
            this.linuxMincoreResident = ((Boolean) residentRaw).booleanValue();
            return;
        }
        throw new IllegalArgumentException("linux.mincore only allows key resident");
    }

    private void validateAndroidDisplays() {
        JSONObject android = android();
        if (android == null || !android.containsKey("displays")) {
            this.androidDisplaysConfigured = false;
            this.androidDisplays = Collections.emptyList();
            return;
        }
        Object raw = android.get("displays");
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException("android.displays must be a JSONArray");
        }
        JSONArray array = (JSONArray) raw;
        List<AndroidDisplayEntryConfig> built = new ArrayList<AndroidDisplayEntryConfig>(array.size());
        Set<Integer> ids = new HashSet<Integer>();
        for (int i = 0; i < array.size(); i++) {
            String path = "android.displays[" + i + "]";
            Object entryRaw = array.get(i);
            if (!(entryRaw instanceof JSONObject)) {
                throw new IllegalArgumentException(path + " must be a JSONObject");
            }
            JSONObject entry = (JSONObject) entryRaw;
            for (String field : entry.keySet()) {
                if (!isAllowedKey(field, ANDROID_DISPLAY_ENTRY_ALLOWED_KEYS)) {
                    throw new IllegalArgumentException(path + "." + field + " is not an allowed key");
                }
            }
            int id = requireExactJsonNumberIntField(entry, "id", path + ".id", 0, Integer.MAX_VALUE);
            if (!ids.add(Integer.valueOf(id))) {
                throw new IllegalArgumentException(path + ".id duplicates " + id);
            }
            String name = "";
            if (entry.containsKey("name")) {
                Object nameRaw = entry.get("name");
                if (!(nameRaw instanceof String)) {
                    throw new IllegalArgumentException(path + ".name must be a String");
                }
                name = (String) nameRaw;
            }
            int flags = entry.containsKey("flags")
                    ? requireExactJsonNumberIntField(entry, "flags", path + ".flags", 0, Integer.MAX_VALUE)
                    : 0;
            int width = requireExactJsonNumberIntField(entry, "widthPixels", path + ".widthPixels",
                    ANDROID_DISPLAY_PIXELS_MIN, ANDROID_DISPLAY_PIXELS_MAX);
            int height = requireExactJsonNumberIntField(entry, "heightPixels", path + ".heightPixels",
                    ANDROID_DISPLAY_PIXELS_MIN, ANDROID_DISPLAY_PIXELS_MAX);
            int dpi = requireExactJsonNumberIntField(entry, "densityDpi", path + ".densityDpi",
                    ANDROID_DISPLAY_DENSITY_DPI_MIN, ANDROID_DISPLAY_DENSITY_DPI_MAX);
            built.add(new AndroidDisplayEntryConfig(id, name, flags, width, height, dpi));
        }
        this.androidDisplaysConfigured = true;
        this.androidDisplays = Collections.unmodifiableList(built);
    }

    private void validateFilesystemDirectories(JSONObject filesystem) {
        if (!filesystem.containsKey("directories")) {
            this.filesystemDirectoriesConfigured = false;
            this.filesystemDirectories = Collections.emptyMap();
            return;
        }
        Object raw = filesystem.get("directories");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException("filesystem.directories must be a JSONObject");
        }
        JSONObject node = (JSONObject) raw;
        Map<String, List<String>> built = new LinkedHashMap<String, List<String>>();
        for (String pathKey : node.keySet()) {
            String path = normalizePosixAbsolutePath(pathKey, "filesystem.directories[\"" + pathKey + "\"]");
            Object listRaw = node.get(pathKey);
            if (!(listRaw instanceof JSONArray)) {
                throw new IllegalArgumentException("filesystem.directories[\"" + pathKey
                        + "\"] must be a JSONArray of names");
            }
            JSONArray names = (JSONArray) listRaw;
            List<String> builtNames = new ArrayList<String>(names.size());
            Set<String> seen = new HashSet<String>();
            for (int i = 0; i < names.size(); i++) {
                Object item = names.get(i);
                if (!(item instanceof String) || ((String) item).isEmpty()
                        || ((String) item).indexOf('/') >= 0) {
                    throw new IllegalArgumentException("filesystem.directories[\"" + pathKey
                            + "\"][" + i + "] must be a non-empty single path segment");
                }
                String name = (String) item;
                if (!seen.add(name)) {
                    throw new IllegalArgumentException("filesystem.directories[\"" + pathKey
                            + "\"] duplicates " + name);
                }
                builtNames.add(name);
            }
            built.put(path, Collections.unmodifiableList(builtNames));
        }
        this.filesystemDirectoriesConfigured = true;
        this.filesystemDirectories = Collections.unmodifiableMap(built);
    }

    /**
     * Six colon-separated hex octets (AA:BB:CC:DD:EE:FF). No whitespace or other separators.
     * Returns Locale.ROOT lowercase form.
     */
    private static String requireMacStringField(JSONObject iface, String path) {
        return requireMacStringValue(iface.get("mac"), path);
    }

    private static String requireMacStringValue(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path + " must be a String");
        }
        String text = (String) raw;
        if (!text.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")) {
            throw new IllegalArgumentException(path
                    + " must be six colon-separated two-digit hex octets without whitespace");
        }
        return text.toLowerCase(Locale.ROOT);
    }

    private static String requireInterfaceName(JSONObject iface, String path) {
        if (!iface.containsKey("name")) {
            throw new IllegalArgumentException(path + " is required");
        }
        return requireInterfaceNameString(iface.get("name"), path);
    }

    /**
     * Interface name: non-empty, no leading/trailing whitespace, UTF-8 length &lt;= 15 bytes.
     */
    private static String requireInterfaceNameString(Object raw, String path) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path + " must be a String");
        }
        String name = (String) raw;
        if (name.isEmpty()) {
            throw new IllegalArgumentException(path + " must be a non-empty String without leading or trailing whitespace");
        }
        if (!name.equals(name.trim())) {
            throw new IllegalArgumentException(path + " must be a non-empty String without leading or trailing whitespace");
        }
        int utf8Bytes = name.getBytes(StandardCharsets.UTF_8).length;
        if (utf8Bytes > 15) {
            throw new IllegalArgumentException(path + " UTF-8 length must be <= 15 bytes, got " + utf8Bytes);
        }
        return name;
    }

    /**
     * Present field must be a JSON Number with exact integral value in {@code [min, max]}.
     * Rejects String, Boolean, null, fractions, and overflow. Used by android.telephony,
     * network.wifi, optional {@code network.interfaces[].hardwareType}, optional
     * {@code network.interfaces[].speedMbps}, optional
     * {@code network.interfaces[].linkIndex}, optional
     * {@code network.interfaces[].txQueueLen}, optional
     * {@code network.interfaces[].addressAssignType}, and optional
     * {@code network.interfaces[].nameAssignType}.
     */
    private static int requireExactJsonNumberIntField(JSONObject object, String key, String path,
                                                      int min, int max) {
        if (!object.containsKey(key)) {
            throw new IllegalArgumentException(path + " is required");
        }
        return requireExactJsonNumberIntValue(object.get(key), path, min, max);
    }

    private static int requireExactJsonNumberIntValue(Object raw, String path, int min, int max) {
        if (!(raw instanceof Number)) {
            throw new IllegalArgumentException(path
                    + " must be an exact JSON Number integer in range " + min + ".." + max);
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(path + " must be an integer in range " + min + ".." + max);
            }
        } else if (raw instanceof BigDecimal) {
            try {
                value = ((BigDecimal) raw).toBigIntegerExact().longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(path + " must be an integer in range " + min + ".." + max);
            }
        } else if (raw instanceof Float || raw instanceof Double) {
            double d = ((Number) raw).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d)) {
                throw new IllegalArgumentException(path + " must be an integer in range " + min + ".." + max);
            }
            value = (long) d;
        } else {
            // Integer / Long / Short / Byte
            value = ((Number) raw).longValue();
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(path + " must be an integer in range " + min + ".." + max);
        }
        return (int) value;
    }

    /**
     * Present field must be a JSON Number with exact integral value in {@code [min, max]} as long.
     * Rejects String, Boolean, null, fractions, and overflow beyond long. Does not alter int helpers.
     */
    private static long requireExactJsonNumberLongField(JSONObject object, String key, String path,
                                                        long min, long max) {
        if (!object.containsKey(key)) {
            throw new IllegalArgumentException(path + " is required");
        }
        return requireExactJsonNumberLongValue(object.get(key), path, min, max);
    }

    /**
     * Raw JSON value must be a Number with exact integral value in {@code [min, max]} as long.
     * Used by object fields and array elements (e.g. {@code fsid[i]}).
     */
    private static long requireExactJsonNumberLongValue(Object raw, String path, long min, long max) {
        if (!(raw instanceof Number)) {
            throw new IllegalArgumentException(path
                    + " must be an exact JSON Number integer in range " + min + ".." + max);
        }
        long value;
        if (raw instanceof BigInteger) {
            try {
                value = ((BigInteger) raw).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(path + " must be an integer in range " + min + ".." + max);
            }
        } else if (raw instanceof BigDecimal) {
            try {
                value = ((BigDecimal) raw).toBigIntegerExact().longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(path + " must be an integer in range " + min + ".." + max);
            }
        } else if (raw instanceof Float || raw instanceof Double) {
            double d = ((Number) raw).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException(path + " must be an integer in range " + min + ".." + max);
            }
            // Exact checked conversion: avoids (long) cast saturating at Long.MAX when
            // double(2^63) compares equal to double(Long.MAX) under d > Long.MAX_VALUE.
            try {
                value = BigDecimal.valueOf(d).toBigIntegerExact().longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(path + " must be an integer in range " + min + ".." + max);
            }
        } else {
            // Integer / Long / Short / Byte
            value = ((Number) raw).longValue();
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(path + " must be an integer in range " + min + ".." + max);
        }
        return value;
    }

    /**
     * Accepts JSON Number (integral) or unsigned decimal integer string in {@code [min, max]}.
     * {@link BigInteger} / {@link BigDecimal} use exact conversion; values outside long/int range fail with {@code path}.
     */
    private static int requireUnsignedIntField(JSONObject iface, String key, String path,
                                               int min, int max, boolean required) {
        if (!iface.containsKey(key)) {
            if (required) {
                throw new IllegalArgumentException(path + " is required");
            }
            throw new IllegalStateException("optional field missing: " + path);
        }
        Object raw = iface.get(key);
        long value;
        if (raw instanceof Number) {
            if (raw instanceof BigInteger) {
                try {
                    value = ((BigInteger) raw).longValueExact();
                } catch (ArithmeticException e) {
                    throw new IllegalArgumentException(path + " must be an integer in range " + min + ".." + max);
                }
            } else if (raw instanceof BigDecimal) {
                try {
                    value = ((BigDecimal) raw).toBigIntegerExact().longValueExact();
                } catch (ArithmeticException e) {
                    throw new IllegalArgumentException(path + " must be an integer in range " + min + ".." + max);
                }
            } else if (raw instanceof Float || raw instanceof Double) {
                double d = ((Number) raw).doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d)) {
                    throw new IllegalArgumentException(path + " must be an integer in range " + min + ".." + max);
                }
                value = (long) d;
            } else {
                // Integer / Long / Short / Byte
                value = ((Number) raw).longValue();
            }
        } else if (raw instanceof String) {
            String text = (String) raw;
            if (text.isEmpty() || !text.matches("[0-9]+")) {
                throw new IllegalArgumentException(path + " must be an unsigned decimal integer string in range "
                        + min + ".." + max);
            }
            try {
                value = new BigInteger(text).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(path + " must be an integer in range " + min + ".." + max);
            }
        } else {
            throw new IllegalArgumentException(path + " must be a JSON Number or unsigned decimal integer string");
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(path + " must be an integer in range " + min + ".." + max);
        }
        return (int) value;
    }

    private static String requireIpv4StringField(JSONObject iface, String key, String path, boolean required) {
        if (!iface.containsKey(key)) {
            if (required) {
                throw new IllegalArgumentException(path + " is required");
            }
            return null;
        }
        Object raw = iface.get(key);
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(path + " must be a String");
        }
        String text = (String) raw;
        validateIpv4Literal(path, text);
        return text;
    }

    /**
     * Strict IPv4 dotted decimal: no whitespace, no sign, exactly four decimal octets 0..255.
     */
    private static void validateIpv4Literal(String path, String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException(path + " must be a dotted-decimal IPv4 string");
        }
        if (!text.equals(text.trim()) || text.indexOf(' ') >= 0 || text.indexOf('\t') >= 0) {
            throw new IllegalArgumentException(path + " must be a dotted-decimal IPv4 string without whitespace");
        }
        if (text.indexOf('+') >= 0 || text.indexOf('-') >= 0) {
            throw new IllegalArgumentException(path + " must be a dotted-decimal IPv4 string without sign");
        }
        String[] parts = text.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException(path + " must be a dotted-decimal IPv4 string");
        }
        for (String part : parts) {
            if (part.isEmpty() || !part.matches("[0-9]+")) {
                throw new IllegalArgumentException(path + " must be a dotted-decimal IPv4 string");
            }
            // reject leading-plus already; unsigned only
            int v;
            try {
                v = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(path + " must be a dotted-decimal IPv4 string");
            }
            if (v < 0 || v > 255) {
                throw new IllegalArgumentException(path + " octet out of range 0..255");
            }
        }
    }

    public String getProcessName(String fallback) {
        return getString(section("process"), "processName", fallback);
    }

    public int getPid(int fallback) {
        return getInt(section("process"), "pid", fallback);
    }

    public int getPpid(int fallback) {
        return getInt(section("process"), "ppid", fallback);
    }

    /**
     * Process group id from explicit {@code process.pgid}, or {@code fallback} when the key is
     * absent (callers typically pass {@code emulator.getPid()}). Never inferred from
     * {@code pid}/{@code ppid}/{@code sid}.
     */
    public int getPgid(int fallback) {
        return processPgid != null ? processPgid.intValue() : fallback;
    }

    /**
     * Session id from explicit {@code process.sid}, or {@code fallback} when the key is absent
     * (callers typically pass {@code emulator.getPid()}). Never inferred from
     * {@code pid}/{@code ppid}/{@code pgid}.
     */
    public int getSid(int fallback) {
        return processSid != null ? processSid.intValue() : fallback;
    }

    /**
     * Whether {@code process.supplementaryGids} was present in JSON, including an explicit
     * empty array. Missing key is false so getgroups / status Groups keep legacy behavior.
     * Never inferred from {@code gid}/{@code egid}.
     */
    public boolean isSupplementaryGidsConfigured() {
        return supplementaryGidsConfigured;
    }

    /**
     * Supplementary group ids from explicit {@code process.supplementaryGids}, in JSON array
     * order. Empty when the key is absent or {@code []}; callers must use
     * {@link #isSupplementaryGidsConfigured()} to distinguish omit vs empty. Never inferred
     * from {@code gid}/{@code egid}. Returned list is unmodifiable.
     */
    public List<Integer> getSupplementaryGids() {
        return supplementaryGids;
    }

    public int getTid(int fallback) {
        return getInt(section("process"), "tid", fallback);
    }

    public int getUid(int fallback) {
        return getInt(section("process"), "uid", fallback);
    }

    public int getGid(int fallback) {
        return getInt(section("process"), "gid", fallback);
    }

    public int getEuid(int fallback) {
        return getInt(section("process"), "euid", fallback);
    }

    public int getEgid(int fallback) {
        return getInt(section("process"), "egid", fallback);
    }

    public String getThreadName(String fallback) {
        return getString(section("process"), "threadName", fallback);
    }

    public long getCurrentTimeMillis(long fallback) {
        return getLong(section("time"), "currentTimeMillis", fallback);
    }

    public Long getCurrentTimeMillis() {
        return getLongObject(section("time"), "currentTimeMillis");
    }

    public Long getMonotonicNanos() {
        return getLongObject(section("time"), "monotonicNanos");
    }

    public long getMonotonicNanos(long fallback) {
        Long value = getMonotonicNanos();
        return value == null ? fallback : value;
    }

    public Integer getTimezoneMinutesWest() {
        return getIntegerObject(section("time"), "timezoneMinutesWest");
    }

    public int getTimezoneMinutesWest(int fallback) {
        Integer value = getTimezoneMinutesWest();
        return value == null ? fallback : value;
    }

    public byte[] getRandomSeed(String key) {
        String hex = getRandomHex(key);
        return hex == null ? null : parseHex(key, hex);
    }

    public byte[] getRandomBytes(String key, int length) {
        byte[] seed = getRandomSeed(key);
        return seed == null ? null : repeat(seed, length);
    }

    /**
     * Shared Media DRM {@code deviceUniqueId} bytes for native {@code AMediaDrm} and
     * Java {@code MediaDrm.getPropertyByteArray}. Preference: explicit
     * {@code android.drm.deviceUniqueIdHex}, then {@code random.mediaDrmDeviceUniqueIdHex},
     * then UTF-8 {@code android.drm.marker}. Returns {@code null} when DRM is not configured
     * and no random hex is present.
     */
    public byte[] resolveMediaDrmDeviceUniqueId() {
        if (isAndroidDrmConfigured()) {
            AndroidDrmConfig drm = getAndroidDrmConfig();
            if (drm != null && drm.isDeviceUniqueIdConfigured()) {
                return drm.getDeviceUniqueId();
            }
            byte[] randomId = getRandomBytes("mediaDrmDeviceUniqueIdHex", 0x20);
            if (randomId != null) {
                return randomId;
            }
            if (drm != null && drm.getMarker() != null) {
                return drm.getMarker().getBytes(StandardCharsets.UTF_8);
            }
        }
        return getRandomBytes("mediaDrmDeviceUniqueIdHex", 0x20);
    }

    public UUID getUuid(UUID fallback) {
        String uuid = getRandomString("uuid");
        return uuid == null ? fallback : UUID.fromString(uuid);
    }

    public String getUnameSysname(String fallback) {
        return getString(uname(), "sysname", fallback);
    }

    /**
     * Whether {@code linux.uname.sysname} is explicitly present (including empty String).
     * Used for automatic {@code /proc/sys/kernel/ostype}; never invents a value when absent.
     */
    public boolean isUnameSysnameConfigured() {
        return isUnameFieldPresent("sysname");
    }

    public String getUnameNodename(String fallback) {
        return getString(uname(), "nodename", fallback);
    }

    /**
     * Whether {@code linux.uname.nodename} is explicitly present (including empty String).
     * Used for automatic {@code /proc/sys/kernel/hostname}; never invents a value when absent.
     */
    public boolean isUnameNodenameConfigured() {
        return isUnameFieldPresent("nodename");
    }

    public String getUnameRelease(String fallback) {
        return getString(uname(), "release", fallback);
    }

    /**
     * Whether {@code linux.uname.release} is explicitly present.
     * Used for automatic {@code /proc/sys/kernel/osrelease}; never invents a value when absent.
     */
    public boolean isUnameReleaseConfigured() {
        return isUnameFieldPresent("release");
    }

    public String getUnameVersion(String fallback) {
        return getString(uname(), "version", fallback);
    }

    /**
     * Whether {@code linux.uname.version} is explicitly present.
     * Used for automatic {@code /proc/sys/kernel/version}; never invents a value when absent.
     */
    public boolean isUnameVersionConfigured() {
        return isUnameFieldPresent("version");
    }

    public String getUnameMachine(boolean is64Bit, String fallback) {
        return getString(uname(), is64Bit ? "machine64" : "machine32", fallback);
    }

    public String getUnameDomainname(String fallback) {
        return getString(uname(), "domainname", fallback);
    }

    /**
     * Whether {@code linux.uname.domainname} is explicitly present.
     * Used for automatic {@code /proc/sys/kernel/domainname}; never invents a value when absent.
     */
    public boolean isUnameDomainnameConfigured() {
        return isUnameFieldPresent("domainname");
    }

    private boolean isUnameFieldPresent(String key) {
        JSONObject u = uname();
        return u != null && u.containsKey(key);
    }

    public byte[] getLinuxFileBytes(String pathname) {
        JSONObject files = linuxFiles();
        if (files == null) {
            return null;
        }
        Object value = files.get(pathname);
        if (value == null) {
            return null;
        }
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Whether {@code linux.proc} is present (including an explicit empty object).
     */
    public boolean isLinuxProcConfigured() {
        return linuxProcConfigured;
    }

    /**
     * Immutable {@code linux.proc} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public LinuxProcConfig getLinuxProcConfig() {
        return linuxProcConfig;
    }

    /**
     * Whether {@code linux.auxv} is present (including an explicit empty object).
     */
    public boolean isLinuxAuxvConfigured() {
        return linuxAuxvConfigured;
    }

    /**
     * Immutable {@code linux.auxv} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public LinuxAuxvConfig getLinuxAuxvConfig() {
        return linuxAuxvConfig;
    }

    /**
     * Whether {@code linux.cpu} is present (including an explicit empty object).
     */
    public boolean isLinuxCpuConfigured() {
        return linuxCpuConfigured;
    }

    /**
     * Immutable {@code linux.cpu} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public LinuxCpuConfig getLinuxCpuConfig() {
        return linuxCpuConfig;
    }

    /**
     * Whether {@code linux.rlimits} is present (including an explicit empty object).
     */
    public boolean isLinuxRlimitsConfigured() {
        return linuxRlimitsConfigured;
    }

    /**
     * Immutable {@code linux.rlimits} view, or {@code null} when the key is absent.
     * Never exposes JSONObject.
     */
    public LinuxRlimitsConfig getLinuxRlimitsConfig() {
        return linuxRlimitsConfig;
    }

    /**
     * Whether {@code linux.rlimits.nofile} is explicitly present.
     * Missing {@code linux}, {@code rlimits}, or {@code nofile} all leave this false
     * (no default pair).
     */
    public boolean isLinuxRlimitsNofileConfigured() {
        return linuxRlimitsConfig != null && linuxRlimitsConfig.isNofileConfigured();
    }

    /**
     * Whether {@code linux.sysinfo} is present. Missing key keeps the historical
     * all-zero {@code sysinfo} struct on ARM32/ARM64.
     */
    public boolean isLinuxSysinfoConfigured() {
        return linuxSysinfoConfigured;
    }

    /**
     * Immutable {@code linux.sysinfo} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public LinuxSysinfoConfig getLinuxSysinfoConfig() {
        return linuxSysinfoConfig;
    }

    /**
     * Whether {@code linux.environ} is present (including an explicit empty array).
     * When false, loaders must preserve built-in default environ entries.
     */
    public boolean isLinuxEnvironConfigured() {
        return linuxEnvironConfigured;
    }

    /**
     * Immutable list of configured {@code KEY=VALUE} environ entries.
     * Empty when the key is absent (and {@link #isLinuxEnvironConfigured()} is false) or when
     * configured as {@code []}. Never exposes JSONArray or a live mutable list.
     * Callers that need the effective stack/proc environ should use
     * {@link #getEffectiveLinuxEnviron()}.
     */
    public List<String> getLinuxEnviron() {
        return linuxEnviron;
    }

    /**
     * Effective environ list for stack TLS and {@code /proc/self|pid/environ}:
     * configured list when {@link #isLinuxEnvironConfigured()}, otherwise
     * {@link #LINUX_ENVIRON_BUILTIN_DEFAULTS}. Never null; immutable.
     */
    public List<String> getEffectiveLinuxEnviron() {
        if (linuxEnvironConfigured) {
            return linuxEnviron;
        }
        return LINUX_ENVIRON_BUILTIN_DEFAULTS;
    }

    public String getAndroidPackageName(String fallback) {
        return getString(android(), "packageName", fallback);
    }

    /**
     * Whether {@code android.packages} is present in JSON (including an explicit empty array).
     * When absent, returns false and {@link #getAndroidPackages()} is an empty immutable list.
     */
    public boolean isAndroidPackagesConfigured() {
        JSONObject android = android();
        return android != null && android.containsKey("packages");
    }

    /**
     * Immutable entries under {@code android.packages}.
     * Empty when the key is absent or the array is empty. Never exposes JSONObject/JSONArray.
     */
    public List<PackageConfig> getAndroidPackages() {
        return androidPackages;
    }

    /**
     * Lookup by exact {@code packageName}, or {@code null} when not found.
     * Rejects null or blank API arguments.
     */
    public PackageConfig getAndroidPackage(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("packageName must be a non-blank String");
        }
        for (PackageConfig pkg : androidPackages) {
            if (packageName.equals(pkg.getPackageName())) {
                return pkg;
            }
        }
        return null;
    }

    /**
     * Whether {@code android.accounts} is present in JSON (including an explicit empty array).
     * When absent, returns false and {@link #getAndroidAccounts()} is an empty immutable list.
     */
    public boolean isAndroidAccountsConfigured() {
        return androidAccountsConfigured;
    }

    /**
     * Immutable entries under {@code android.accounts} in JSON order.
     * Empty when the key is absent or the array is empty. Never exposes JSONObject/JSONArray.
     */
    public List<AndroidAccountConfig> getAndroidAccounts() {
        return androidAccounts;
    }

    /**
     * Whether {@code android.inputMethods} is present in JSON (including an explicit empty array).
     * When absent, returns false and {@link #getAndroidInputMethods()} is an empty immutable list.
     */
    public boolean isAndroidInputMethodsConfigured() {
        return androidInputMethodsConfigured;
    }

    /**
     * Immutable entries under {@code android.inputMethods} in JSON order.
     * Empty when the key is absent or the array is empty. Never exposes JSONObject/JSONArray.
     */
    public List<AndroidInputMethodConfig> getAndroidInputMethods() {
        return androidInputMethods;
    }

    /**
     * Whether {@code android.features} is present in JSON (including an explicit empty array).
     * When absent, returns false and {@link #getAndroidFeatures()} is an empty immutable list.
     */
    public boolean isAndroidFeaturesConfigured() {
        JSONObject android = android();
        return android != null && android.containsKey("features");
    }

    /**
     * Immutable entries under {@code android.features}.
     * Empty when the key is absent or the array is empty. Never exposes JSONObject/JSONArray.
     */
    public List<FeatureConfig> getAndroidFeatures() {
        return androidFeatures;
    }

    /**
     * Lookup by exact {@code name}, or {@code null} when not found.
     * Rejects null or blank API arguments.
     */
    public FeatureConfig getAndroidFeature(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must be a non-blank String");
        }
        for (FeatureConfig feature : androidFeatures) {
            if (name.equals(feature.getName())) {
                return feature;
            }
        }
        return null;
    }

    /**
     * Whether {@code android.tee} is present in JSON (including an explicit empty object).
     * Missing node returns false; explicit {@code {}} returns true with no fields configured.
     */
    public boolean isAndroidTeeConfigured() {
        return androidTeeConfigured;
    }

    public boolean isTeeAvailableConfigured() {
        return teeAvailableConfigured;
    }

    /** Configured {@code available}, or {@code fallback} when the field is absent. */
    public boolean getTeeAvailable(boolean fallback) {
        return teeAvailableConfigured ? teeAvailable.booleanValue() : fallback;
    }

    public boolean isTeeSecurityLevelConfigured() {
        return teeSecurityLevelConfigured;
    }

    /**
     * Configured security level enum string, or {@code null} when absent.
     * Values: SOFTWARE | TRUSTED_ENVIRONMENT | STRONGBOX.
     */
    public String getTeeSecurityLevel() {
        return teeSecurityLevel;
    }

    public boolean isTeeKeymasterVersionConfigured() {
        return teeKeymasterVersionConfigured;
    }

    /** Configured keymaster version, or {@code fallback} when absent. */
    public int getTeeKeymasterVersion(int fallback) {
        return teeKeymasterVersionConfigured ? teeKeymasterVersion.intValue() : fallback;
    }

    public boolean isTeeStrongBoxAvailableConfigured() {
        return teeStrongBoxAvailableConfigured;
    }

    /** Configured StrongBox availability, or {@code fallback} when absent. */
    public boolean getTeeStrongBoxAvailable(boolean fallback) {
        return teeStrongBoxAvailableConfigured ? teeStrongBoxAvailable.booleanValue() : fallback;
    }

    public boolean isTeeMarkerConfigured() {
        return teeMarkerConfigured;
    }

    /** Configured marker string, or {@code null} when absent. */
    public String getTeeMarker() {
        return teeMarker;
    }

    public boolean isTeeKeyBlobConfigured() {
        return teeKeyBlobConfigured;
    }

    /**
     * Defensive copy of decoded key blob bytes, or {@code null} when {@code keyBlobHex} is absent.
     */
    public byte[] getTeeKeyBlob() {
        if (!teeKeyBlobConfigured || teeKeyBlob == null) {
            return null;
        }
        return Arrays.copyOf(teeKeyBlob, teeKeyBlob.length);
    }

    /**
     * Whether {@code keyAlgorithm} was present. Omitted key is false so JNI may keep UOE;
     * never inferred from marker, securityLevel, keyBlobHex, or keyFormat.
     */
    public boolean isTeeKeyAlgorithmConfigured() {
        return teeKeyAlgorithmConfigured;
    }

    /**
     * Configured key algorithm for {@code Key.getAlgorithm()}, or {@code null} when absent.
     * Meaningful only when {@link #isTeeKeyAlgorithmConfigured()} is true; never inferred.
     */
    public String getTeeKeyAlgorithm() {
        return teeKeyAlgorithm;
    }

    /**
     * Whether {@code keyFormat} was present. Omitted key is false so JNI may keep UOE;
     * never inferred from marker, securityLevel, keyBlobHex, or keyAlgorithm.
     */
    public boolean isTeeKeyFormatConfigured() {
        return teeKeyFormatConfigured;
    }

    /**
     * Configured key format for {@code Key.getFormat()}, or {@code null} when absent.
     * Meaningful only when {@link #isTeeKeyFormatConfigured()} is true; never inferred.
     */
    public String getTeeKeyFormat() {
        return teeKeyFormat;
    }

    /**
     * Whether {@code android.drm} is present (including an explicit empty object).
     */
    public boolean isAndroidDrmConfigured() {
        return androidDrmConfigured;
    }

    /**
     * Immutable {@code android.drm} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidDrmConfig getAndroidDrmConfig() {
        return androidDrmConfig;
    }

    /**
     * Whether {@code android.locale} is present (including an explicit empty object).
     * Missing node returns false so callers may keep host-locale fallback.
     */
    public boolean isAndroidLocaleConfigured() {
        return androidLocaleConfigured;
    }

    /**
     * Immutable {@code android.locale} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidLocaleConfig getAndroidLocaleConfig() {
        return androidLocaleConfig;
    }

    /**
     * Whether {@code android.display} is present (including an explicit empty object).
     * Missing node returns false so callers may keep host-display fallback.
     */
    public boolean isAndroidDisplayConfigured() {
        return androidDisplayConfigured;
    }

    /**
     * Immutable {@code android.display} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidDisplayConfig getAndroidDisplayConfig() {
        return androidDisplayConfig;
    }

    /**
     * Whether {@code android.configuration} is present (including an explicit empty object).
     * Missing node returns false so callers may keep host Configuration fallback.
     */
    public boolean isAndroidConfigurationConfigured() {
        return androidConfigurationConfigured;
    }

    /**
     * Immutable {@code android.configuration} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidConfigurationConfig getAndroidConfigurationConfig() {
        return androidConfigurationConfig;
    }

    /**
     * Whether {@code android.power} is present (including an explicit empty object).
     * Missing node returns false so callers may keep host PowerManager fallback.
     */
    public boolean isAndroidPowerConfigured() {
        return androidPowerConfigured;
    }

    /**
     * Immutable {@code android.power} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidPowerConfig getAndroidPowerConfig() {
        return androidPowerConfig;
    }

    /**
     * Whether {@code android.thermal} is present (including an explicit empty object).
     * Missing node returns false so callers may keep host thermal fallback.
     */
    public boolean isAndroidThermalConfigured() {
        return androidThermalConfigured;
    }

    /**
     * Immutable {@code android.thermal} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidThermalConfig getAndroidThermalConfig() {
        return androidThermalConfig;
    }

    /**
     * Whether top-level {@code graphics} is present (including an explicit empty object).
     * Missing node returns false so GLES/EGL query JNI stay UOE.
     */
    public boolean isGraphicsConfigured() {
        return graphicsConfigured;
    }

    /**
     * Immutable {@code graphics} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public GraphicsConfig getGraphicsConfig() {
        return graphicsConfig;
    }

    /**
     * Marks this config as coming from a device-fingerprint profile. Existing
     * {@link #parse(String)} / {@link #load(File)} paths never call this.
     */
    void attachDeviceFingerprintProfile(String profileName, ProfileFileOverlay overlay,
                                        Map<String, Object> reservedFields) {
        this.profileLoaded = true;
        this.profileName = profileName;
        this.profileFileOverlay = overlay;
        this.profileReservedFields = reservedFields == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(reservedFields));
    }

    public boolean isProfileLoaded() {
        return profileLoaded;
    }

    public String getProfileName() {
        return profileName;
    }

    public ProfileFileOverlay getProfileFileOverlay() {
        return profileFileOverlay;
    }

    /**
     * Raw overlay bytes for a guest path, or {@code null} when no profile overlay is attached
     * or the path is not present. Never reads host files outside the overlay root.
     */
    public byte[] readProfileOverlayFile(String pathname) {
        return profileFileOverlay == null ? null : profileFileOverlay.read(pathname);
    }

    public String[] listProfileOverlayDirectory(String pathname) {
        return profileFileOverlay == null ? null : profileFileOverlay.list(pathname);
    }

    /**
     * Reserved shortcut-profile payloads kept for later backend wiring. Inactive: does not
     * change current unidbg behavior. Empty when this config was not loaded from a profile.
     */
    public Map<String, Object> getProfileReservedFields() {
        return profileReservedFields;
    }

    /**
     * Whether {@code android.battery} is present (including an explicit empty object).
     * Missing node returns false so callers may keep host battery fallback.
     */
    public boolean isAndroidBatteryConfigured() {
        return androidBatteryConfigured;
    }

    /**
     * Immutable {@code android.battery} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidBatteryConfig getAndroidBatteryConfig() {
        return androidBatteryConfig;
    }

    /**
     * Whether {@code android.powerProfile} is present (including an explicit empty object).
     */
    public boolean isAndroidPowerProfileConfigured() {
        return androidPowerProfileConfigured;
    }

    /**
     * Immutable {@code android.powerProfile} view, or {@code null} when the key is absent.
     */
    public AndroidPowerProfileConfig getAndroidPowerProfileConfig() {
        return androidPowerProfileConfig;
    }

    /**
     * Whether {@code android.telephony.cellInfo} is present (including an explicit empty array).
     * Independent of other telephony fields; missing key does not take over cell JNI.
     */
    public boolean isAndroidCellInfoConfigured() {
        return androidCellInfoConfigured;
    }

    /**
     * Immutable {@code android.telephony.cellInfo} rows. Empty when configured as {@code []}.
     * Never {@code null}.
     */
    public List<CellInfoConfig> getAndroidCellInfo() {
        return androidCellInfo;
    }

    /**
     * Whether {@code network.wifi.scanResults} is present (including an explicit empty array).
     * Independent of other wifi connection fields.
     */
    public boolean isNetworkWifiScanResultsConfigured() {
        return networkWifiScanResultsConfigured;
    }

    /**
     * Immutable {@code network.wifi.scanResults} rows. Empty when configured as {@code []}.
     * Never {@code null}.
     */
    public List<WifiScanResultConfig> getNetworkWifiScanResults() {
        return networkWifiScanResults;
    }

    /**
     * Whether {@code android.cameras} is present (including an explicit empty object).
     * Missing node returns false so callers keep UOE for camera count JNI.
     */
    public boolean isAndroidCamerasConfigured() {
        return androidCamerasConfigured;
    }

    /**
     * Immutable {@code android.cameras} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidCamerasConfig getAndroidCamerasConfig() {
        return androidCamerasConfig;
    }

    /**
     * NV21 preview bytes for a stream: inlined {@code previewHex}, or the overlay file at
     * {@code previewFile}. Returns {@code null} when the stream has no preview, the overlay
     * is missing, or the file length is not {@code width*height*3/2}. Callers must not
     * mutate the returned array.
     */
    public byte[] resolveCameraPreview(AndroidCameraStreamConfig stream) {
        if (stream == null || !stream.isPreviewConfigured()) {
            return null;
        }
        if (stream.getPreviewNv21() != null) {
            return stream.getPreviewNv21();
        }
        String path = stream.getPreviewFile();
        if (path == null) {
            return null;
        }
        byte[] raw = readProfileOverlayFile(path);
        if (raw == null) {
            return null;
        }
        long expected = cameraPreviewNv21Length(stream.getWidth(), stream.getHeight());
        if (expected < 0 || raw.length != (int) expected) {
            return null;
        }
        return raw;
    }

    /**
     * JPEG still bytes for a stream: inlined {@code jpegHex}, or the overlay file at
     * {@code jpegFile}. Returns {@code null} when the stream has no JPEG, the overlay is
     * missing, or the file is empty. Callers must not mutate the returned array.
     */
    public byte[] resolveCameraJpeg(AndroidCameraStreamConfig stream) {
        if (stream == null || !stream.isJpegConfigured()) {
            return null;
        }
        if (stream.getJpeg() != null) {
            return stream.getJpeg();
        }
        String path = stream.getJpegFile();
        if (path == null) {
            return null;
        }
        byte[] raw = readProfileOverlayFile(path);
        if (raw == null || raw.length < 1) {
            return null;
        }
        return raw;
    }

    /**
     * Whether {@code android.sensors} is present (including an explicit empty object).
     * Missing node returns false so callers keep UOE for sensor JNI.
     */
    public boolean isAndroidSensorsConfigured() {
        return androidSensorsConfigured;
    }

    /**
     * Immutable {@code android.sensors} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidSensorsConfig getAndroidSensorsConfig() {
        return androidSensorsConfig;
    }

    /**
     * Whether {@code android.sensors.names} was present (including an explicit empty object).
     * False when the sensors node is absent or {@code names} is omitted.
     */
    public boolean isAndroidSensorNamesConfigured() {
        return androidSensorsConfig != null && androidSensorsConfig.isNamesConfigured();
    }

    /**
     * Whether {@code android.sensors.names} has an explicit entry for {@code sensorType}.
     * False when the sensors node is absent, {@code names} is omitted/empty, or that type
     * has no name. Does not infer a default from the type integer.
     */
    public boolean isAndroidSensorNameConfigured(int sensorType) {
        return androidSensorsConfig != null && androidSensorsConfig.isNameConfigured(sensorType);
    }

    /**
     * Configured sensor name for {@code sensorType}, or {@code null} when that type has no
     * explicit {@code names} entry (including when the sensors node or {@code names} is absent).
     * Never derived from the type integer; never exposes JSONObject.
     */
    public String getAndroidSensorName(int sensorType) {
        return androidSensorsConfig == null ? null : androidSensorsConfig.getName(sensorType);
    }

    /**
     * {@code android.sensors.vendors} 是否存在（含显式空对象）。
     * 传感器节点缺失或省略 {@code vendors} 时为 false。不从 {@code names} 推断。
     */
    public boolean isAndroidSensorVendorsConfigured() {
        return androidSensorsConfig != null && androidSensorsConfig.isVendorsConfigured();
    }

    /**
     * {@code android.sensors.vendors} 是否对该 {@code sensorType} 有显式条目。
     * 节点缺失、{@code vendors} 省略/为空、或该 type 无厂商名时为 false。
     * 不从 type 整数或 {@code names} 推断默认值。
     */
    public boolean isAndroidSensorVendorConfigured(int sensorType) {
        return androidSensorsConfig != null && androidSensorsConfig.isVendorConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置厂商名；无显式 {@code vendors} 条目时为 {@code null}
     *（含传感器节点或 {@code vendors} 缺失）。不从 type 整数或 {@code names} 推导；
     * 不暴露 JSONObject。
     */
    public String getAndroidSensorVendor(int sensorType) {
        return androidSensorsConfig == null ? null : androidSensorsConfig.getVendor(sensorType);
    }

    /**
     * {@code android.sensors.versions} 是否存在（含显式空对象）。
     * 传感器节点缺失或省略 {@code versions} 时为 false。
     * 不从 {@code names} / {@code vendors} / lists 推断。
     */
    public boolean isAndroidSensorVersionsConfigured() {
        return androidSensorsConfig != null && androidSensorsConfig.isVersionsConfigured();
    }

    /**
     * {@code android.sensors.versions} 是否对该 {@code sensorType} 有显式条目。
     * 节点缺失、{@code versions} 省略/为空、或该 type 无版本号时为 false。
     * 不从 type 整数或 {@code names} / {@code vendors} 推断默认值。
     */
    public boolean isAndroidSensorVersionConfigured(int sensorType) {
        return androidSensorsConfig != null && androidSensorsConfig.isVersionConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置版本号；无显式 {@code versions} 条目时为 {@code null}
     *（含传感器节点或 {@code versions} 缺失）。不从 type 整数或 {@code names} / {@code vendors}
     * 推导；不暴露 JSONObject。显式 {@code 0} 与缺失不同。
     */
    public Integer getAndroidSensorVersion(int sensorType) {
        return androidSensorsConfig == null ? null : androidSensorsConfig.getVersion(sensorType);
    }

    /**
     * {@code android.sensors.stringTypes} 是否存在（含显式空对象）。
     * 传感器节点缺失或省略 {@code stringTypes} 时为 false。
     * 不从 {@code names} / {@code vendors} / {@code versions} / lists 推断。
     */
    public boolean isAndroidSensorStringTypesConfigured() {
        return androidSensorsConfig != null && androidSensorsConfig.isStringTypesConfigured();
    }

    /**
     * {@code android.sensors.stringTypes} 是否对该 {@code sensorType} 有显式条目。
     * 节点缺失、{@code stringTypes} 省略/为空、或该 type 无 string type 时为 false。
     * 不从 type 整数或 {@code names} / {@code vendors} / {@code versions} 推断默认值。
     */
    public boolean isAndroidSensorStringTypeConfigured(int sensorType) {
        return androidSensorsConfig != null && androidSensorsConfig.isStringTypeConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置 string type；无显式 {@code stringTypes} 条目时为 {@code null}
     *（含传感器节点或 {@code stringTypes} 缺失）。不从 type 整数或 {@code names} / {@code vendors} /
     * {@code versions} 推导；不暴露 JSONObject。
     */
    public String getAndroidSensorStringType(int sensorType) {
        return androidSensorsConfig == null ? null : androidSensorsConfig.getStringType(sensorType);
    }

    /**
     * {@code android.sensors.maximumRanges} 是否存在（含显式空对象）。
     * 传感器节点缺失或省略 {@code maximumRanges} 时为 false。
     * 不从 {@code names} / {@code vendors} / {@code versions} / {@code stringTypes} /
     * {@code resolutions} / {@code powers} / lists 推断。
     */
    public boolean isAndroidSensorMaximumRangesConfigured() {
        return androidSensorsConfig != null && androidSensorsConfig.isMaximumRangesConfigured();
    }

    /**
     * {@code android.sensors.maximumRanges} 是否对该 {@code sensorType} 有显式条目。
     * 节点缺失、{@code maximumRanges} 省略/为空、或该 type 无量程时为 false。
     * 不从 type 整数或 {@code names} / {@code vendors} / {@code versions} /
     * {@code stringTypes} / {@code resolutions} / {@code powers} 推断默认值。
     */
    public boolean isAndroidSensorMaximumRangeConfigured(int sensorType) {
        return androidSensorsConfig != null
                && androidSensorsConfig.isMaximumRangeConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置最大量程；无显式 {@code maximumRanges} 条目时为 {@code null}
     *（含传感器节点或 {@code maximumRanges} 缺失）。不从 type 整数或 {@code names} /
     * {@code vendors} / {@code versions} / {@code stringTypes} / {@code resolutions} /
     * {@code powers} 推导；不暴露 JSONObject。显式 {@code 0.0} 与缺失不同。
     */
    public Float getAndroidSensorMaximumRange(int sensorType) {
        return androidSensorsConfig == null
                ? null : androidSensorsConfig.getMaximumRange(sensorType);
    }

    /**
     * {@code android.sensors.resolutions} 是否存在（含显式空对象）。
     * 传感器节点缺失或省略 {@code resolutions} 时为 false。
     * 不从 {@code names} / {@code vendors} / {@code versions} / {@code stringTypes} /
     * {@code maximumRanges} / {@code powers} / lists 推断。
     */
    public boolean isAndroidSensorResolutionsConfigured() {
        return androidSensorsConfig != null && androidSensorsConfig.isResolutionsConfigured();
    }

    /**
     * {@code android.sensors.resolutions} 是否对该 {@code sensorType} 有显式条目。
     * 节点缺失、{@code resolutions} 省略/为空、或该 type 无分辨率时为 false。
     * 不从 type 整数或 {@code names} / {@code vendors} / {@code versions} /
     * {@code stringTypes} / {@code maximumRanges} / {@code powers} 推断默认值。
     */
    public boolean isAndroidSensorResolutionConfigured(int sensorType) {
        return androidSensorsConfig != null
                && androidSensorsConfig.isResolutionConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置分辨率；无显式 {@code resolutions} 条目时为 {@code null}
     *（含传感器节点或 {@code resolutions} 缺失）。不从 type 整数或 {@code names} /
     * {@code vendors} / {@code versions} / {@code stringTypes} / {@code maximumRanges} /
     * {@code powers} 推导；不暴露 JSONObject。显式 {@code 0.0} 与缺失不同。
     */
    public Float getAndroidSensorResolution(int sensorType) {
        return androidSensorsConfig == null
                ? null : androidSensorsConfig.getResolution(sensorType);
    }

    /**
     * {@code android.sensors.powers} 是否存在（含显式空对象）。
     * 传感器节点缺失或省略 {@code powers} 时为 false。
     * 不从 {@code names} / {@code vendors} / {@code versions} / {@code stringTypes} /
     * {@code maximumRanges} / {@code resolutions} / lists 推断。
     */
    public boolean isAndroidSensorPowersConfigured() {
        return androidSensorsConfig != null && androidSensorsConfig.isPowersConfigured();
    }

    /**
     * {@code android.sensors.powers} 是否对该 {@code sensorType} 有显式条目。
     * 节点缺失、{@code powers} 省略/为空、或该 type 无功耗时为 false。
     * 不从 type 整数或 {@code names} / {@code vendors} / {@code versions} /
     * {@code stringTypes} / {@code maximumRanges} / {@code resolutions} 推断默认值。
     */
    public boolean isAndroidSensorPowerConfigured(int sensorType) {
        return androidSensorsConfig != null
                && androidSensorsConfig.isPowerConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置功耗；无显式 {@code powers} 条目时为 {@code null}
     *（含传感器节点或 {@code powers} 缺失）。不从 type 整数或 {@code names} /
     * {@code vendors} / {@code versions} / {@code stringTypes} / {@code maximumRanges} /
     * {@code resolutions} 推导；不暴露 JSONObject。显式 {@code 0.0} 与缺失不同。
     */
    public Float getAndroidSensorPower(int sensorType) {
        return androidSensorsConfig == null
                ? null : androidSensorsConfig.getPower(sensorType);
    }

    /**
     * {@code android.sensors.minDelaysMicros} 是否存在（含显式空对象）。
     * 传感器节点缺失或省略 {@code minDelaysMicros} 时为 false。
     * 不从 {@code names} / {@code vendors} / {@code versions} / {@code stringTypes} /
     * {@code maximumRanges} / {@code resolutions} / {@code powers} / {@code maxDelaysMicros} /
     * {@code fifoReservedEventCounts} / lists 推断。
     */
    public boolean isAndroidSensorMinDelaysMicrosConfigured() {
        return androidSensorsConfig != null && androidSensorsConfig.isMinDelaysMicrosConfigured();
    }

    /**
     * {@code android.sensors.minDelaysMicros} 是否对该 {@code sensorType} 有显式条目。
     * 节点缺失、{@code minDelaysMicros} 省略/为空、或该 type 无最小延迟时为 false。
     * 不从 type 整数或 {@code names} / {@code vendors} / {@code versions} /
     * {@code stringTypes} / {@code maximumRanges} / {@code resolutions} / {@code powers} /
     * {@code maxDelaysMicros} / {@code fifoReservedEventCounts} 推断默认值。
     */
    public boolean isAndroidSensorMinDelayMicrosConfigured(int sensorType) {
        return androidSensorsConfig != null
                && androidSensorsConfig.isMinDelayMicrosConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置最小延迟（微秒）；无显式 {@code minDelaysMicros} 条目时为
     * {@code null}（含传感器节点或 {@code minDelaysMicros} 缺失）。不从 type 整数或
     * {@code names} / {@code vendors} / {@code versions} / {@code stringTypes} /
     * {@code maximumRanges} / {@code resolutions} / {@code powers} / {@code maxDelaysMicros} /
     * {@code fifoReservedEventCounts} 推导；不暴露 JSONObject。显式 {@code -1}/{@code 0} 与缺失不同。
     */
    public Integer getAndroidSensorMinDelayMicros(int sensorType) {
        return androidSensorsConfig == null
                ? null : androidSensorsConfig.getMinDelayMicros(sensorType);
    }

    /**
     * {@code android.sensors.maxDelaysMicros} 是否存在（含显式空对象）。
     * 传感器节点缺失或省略 {@code maxDelaysMicros} 时为 false。
     * 不从 {@code names} / {@code vendors} / {@code versions} / {@code stringTypes} /
     * {@code maximumRanges} / {@code resolutions} / {@code powers} / {@code minDelaysMicros} /
     * {@code fifoReservedEventCounts} / lists 推断。
     */
    public boolean isAndroidSensorMaxDelaysMicrosConfigured() {
        return androidSensorsConfig != null && androidSensorsConfig.isMaxDelaysMicrosConfigured();
    }

    /**
     * {@code android.sensors.maxDelaysMicros} 是否对该 {@code sensorType} 有显式条目。
     * 节点缺失、{@code maxDelaysMicros} 省略/为空、或该 type 无最大延迟时为 false。
     * 不从 type 整数或 {@code names} / {@code vendors} / {@code versions} /
     * {@code stringTypes} / {@code maximumRanges} / {@code resolutions} / {@code powers} /
     * {@code minDelaysMicros} / {@code fifoReservedEventCounts} 推断默认值。
     */
    public boolean isAndroidSensorMaxDelayMicrosConfigured(int sensorType) {
        return androidSensorsConfig != null
                && androidSensorsConfig.isMaxDelayMicrosConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置最大延迟（微秒）；无显式 {@code maxDelaysMicros} 条目时为
     * {@code null}（含传感器节点或 {@code maxDelaysMicros} 缺失）。不从 type 整数或
     * {@code names} / {@code vendors} / {@code versions} / {@code stringTypes} /
     * {@code maximumRanges} / {@code resolutions} / {@code powers} / {@code minDelaysMicros} /
     * {@code fifoReservedEventCounts} 推导；不校验 {@code max>=min}；不暴露 JSONObject。
     * 显式 {@code 0} 与缺失不同。
     */
    public Integer getAndroidSensorMaxDelayMicros(int sensorType) {
        return androidSensorsConfig == null
                ? null : androidSensorsConfig.getMaxDelayMicros(sensorType);
    }

    /**
     * {@code android.sensors.fifoReservedEventCounts} 是否存在（含显式空对象）。
     * 传感器节点缺失或省略 {@code fifoReservedEventCounts} 时为 false。
     * 不从 {@code names} / {@code vendors} / {@code versions} / {@code stringTypes} /
     * {@code maximumRanges} / {@code resolutions} / {@code powers} / {@code minDelaysMicros} /
     * {@code maxDelaysMicros} / {@code fifoMaxEventCounts} / lists 推断。
     */
    public boolean isAndroidSensorFifoReservedEventCountsConfigured() {
        return androidSensorsConfig != null
                && androidSensorsConfig.isFifoReservedEventCountsConfigured();
    }

    /**
     * {@code android.sensors.fifoReservedEventCounts} 是否对该 {@code sensorType} 有显式条目。
     * 节点缺失、{@code fifoReservedEventCounts} 省略/为空、或该 type 无 FIFO 预留事件数时为 false。
     * 不从 type 整数或 {@code names} / {@code vendors} / {@code versions} /
     * {@code stringTypes} / {@code maximumRanges} / {@code resolutions} / {@code powers} /
     * {@code minDelaysMicros} / {@code maxDelaysMicros} / {@code fifoMaxEventCounts}
     * 推断默认值。
     */
    public boolean isAndroidSensorFifoReservedEventCountConfigured(int sensorType) {
        return androidSensorsConfig != null
                && androidSensorsConfig.isFifoReservedEventCountConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置 FIFO 预留事件数；无显式 {@code fifoReservedEventCounts}
     * 条目时为 {@code null}（含传感器节点或 {@code fifoReservedEventCounts} 缺失）。
     * 不从 type 整数或 {@code names} / {@code vendors} / {@code versions} /
     * {@code stringTypes} / {@code maximumRanges} / {@code resolutions} / {@code powers} /
     * {@code minDelaysMicros} / {@code maxDelaysMicros} / {@code fifoMaxEventCounts}
     * 推导；不校验与 {@code fifoMaxEventCounts} 的大小关系；不暴露 JSONObject。
     * 显式 {@code 0} 与缺失不同。
     */
    public Integer getAndroidSensorFifoReservedEventCount(int sensorType) {
        return androidSensorsConfig == null
                ? null : androidSensorsConfig.getFifoReservedEventCount(sensorType);
    }

    /**
     * {@code android.sensors.fifoMaxEventCounts} 是否存在（含显式空对象）。
     * 传感器节点缺失或省略 {@code fifoMaxEventCounts} 时为 false。
     * 不从 {@code names} / {@code vendors} / {@code versions} / {@code stringTypes} /
     * {@code maximumRanges} / {@code resolutions} / {@code powers} / {@code minDelaysMicros} /
     * {@code maxDelaysMicros} / {@code fifoReservedEventCounts} / lists 推断。
     */
    public boolean isAndroidSensorFifoMaxEventCountsConfigured() {
        return androidSensorsConfig != null
                && androidSensorsConfig.isFifoMaxEventCountsConfigured();
    }

    /**
     * {@code android.sensors.fifoMaxEventCounts} 是否对该 {@code sensorType} 有显式条目。
     * 节点缺失、{@code fifoMaxEventCounts} 省略/为空、或该 type 无 FIFO 最大事件数时为 false。
     * 不从 type 整数或 {@code names} / {@code vendors} / {@code versions} /
     * {@code stringTypes} / {@code maximumRanges} / {@code resolutions} / {@code powers} /
     * {@code minDelaysMicros} / {@code maxDelaysMicros} / {@code fifoReservedEventCounts}
     * 推断默认值。
     */
    public boolean isAndroidSensorFifoMaxEventCountConfigured(int sensorType) {
        return androidSensorsConfig != null
                && androidSensorsConfig.isFifoMaxEventCountConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置 FIFO 最大事件数；无显式 {@code fifoMaxEventCounts}
     * 条目时为 {@code null}（含传感器节点或 {@code fifoMaxEventCounts} 缺失）。
     * 不从 type 整数或 {@code names} / {@code vendors} / {@code versions} /
     * {@code stringTypes} / {@code maximumRanges} / {@code resolutions} / {@code powers} /
     * {@code minDelaysMicros} / {@code maxDelaysMicros} / {@code fifoReservedEventCounts}
     * 推导；不校验与 {@code fifoReservedEventCounts} 的大小关系；不暴露 JSONObject。
     * 显式 {@code 0} 与缺失不同。
     */
    public Integer getAndroidSensorFifoMaxEventCount(int sensorType) {
        return androidSensorsConfig == null
                ? null : androidSensorsConfig.getFifoMaxEventCount(sensorType);
    }

    /**
     * {@code android.sensors.wakeUpSensors} 是否存在（含显式空对象）。
     * 传感器节点缺失或省略 {@code wakeUpSensors} 时为 false。
     * 不从 {@code names} / {@code vendors} / {@code versions} / {@code stringTypes} /
     * {@code maximumRanges} / {@code resolutions} / {@code powers} / {@code minDelaysMicros} /
     * {@code maxDelaysMicros} / {@code fifoReservedEventCounts} / {@code fifoMaxEventCounts} /
     * lists 推断。
     */
    public boolean isAndroidSensorWakeUpSensorsConfigured() {
        return androidSensorsConfig != null
                && androidSensorsConfig.isWakeUpSensorsConfigured();
    }

    /**
     * {@code android.sensors.wakeUpSensors} 是否对该 {@code sensorType} 有显式条目。
     * 节点缺失、{@code wakeUpSensors} 省略/为空、或该 type 无 wake-up 标记时为 false。
     * 不从 type 整数或其它传感器字段推断默认值。
     */
    public boolean isAndroidSensorWakeUpSensorConfigured(int sensorType) {
        return androidSensorsConfig != null
                && androidSensorsConfig.isWakeUpSensorConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置 {@code isWakeUpSensor}；无显式 {@code wakeUpSensors}
     * 条目时为 {@code null}（含传感器节点或 {@code wakeUpSensors} 缺失）。
     * 不从 type 整数或其它传感器字段推导；不暴露 JSONObject。
     * 显式 {@code false} 与缺失不同。
     */
    public Boolean getAndroidSensorWakeUpSensor(int sensorType) {
        return androidSensorsConfig == null
                ? null : androidSensorsConfig.getWakeUpSensor(sensorType);
    }

    /**
     * {@code android.sensors.sensorIds} 是否存在（含显式空对象）。
     * 传感器节点缺失或省略 {@code sensorIds} 时为 false。不从其它传感器字段推断。
     */
    public boolean isAndroidSensorIdsConfigured() {
        return androidSensorsConfig != null && androidSensorsConfig.isSensorIdsConfigured();
    }

    /**
     * {@code android.sensors.sensorIds} 是否对该 {@code sensorType} 有显式条目。
     * 不从 type 整数或其它传感器字段推断默认值。
     */
    public boolean isAndroidSensorIdConfigured(int sensorType) {
        return androidSensorsConfig != null && androidSensorsConfig.isIdConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置 {@code getId}；无显式 {@code sensorIds} 条目时为 {@code null}。
     * 显式 {@code 0}/{@code -1} 与缺失不同。
     */
    public Integer getAndroidSensorId(int sensorType) {
        return androidSensorsConfig == null ? null : androidSensorsConfig.getId(sensorType);
    }

    /**
     * {@code android.sensors.reportingModes} 是否存在（含显式空对象）。
     * 不从其它传感器字段推断。
     */
    public boolean isAndroidSensorReportingModesConfigured() {
        return androidSensorsConfig != null && androidSensorsConfig.isReportingModesConfigured();
    }

    /**
     * {@code android.sensors.reportingModes} 是否对该 {@code sensorType} 有显式条目。
     */
    public boolean isAndroidSensorReportingModeConfigured(int sensorType) {
        return androidSensorsConfig != null
                && androidSensorsConfig.isReportingModeConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置 {@code getReportingMode}；无显式 {@code reportingModes}
     * 条目时为 {@code null}。
     */
    public Integer getAndroidSensorReportingMode(int sensorType) {
        return androidSensorsConfig == null
                ? null : androidSensorsConfig.getReportingMode(sensorType);
    }

    /**
     * {@code android.sensors.dynamicSensors} 是否存在（含显式空对象）。
     * **不**从 {@code dynamicTypes} 推断。
     */
    public boolean isAndroidSensorDynamicSensorsConfigured() {
        return androidSensorsConfig != null && androidSensorsConfig.isDynamicSensorsConfigured();
    }

    /**
     * {@code android.sensors.dynamicSensors} 是否对该 {@code sensorType} 有显式条目。
     * **不**从 {@code dynamicTypes} 推断。
     */
    public boolean isAndroidSensorDynamicSensorConfigured(int sensorType) {
        return androidSensorsConfig != null
                && androidSensorsConfig.isDynamicSensorConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置 {@code isDynamicSensor}；无显式 {@code dynamicSensors}
     * 条目时为 {@code null}。**不**从 {@code dynamicTypes} 推导。显式 {@code false} 与缺失不同。
     */
    public Boolean getAndroidSensorDynamicSensor(int sensorType) {
        return androidSensorsConfig == null
                ? null : androidSensorsConfig.getDynamicSensor(sensorType);
    }

    /**
     * {@code android.sensors.requiredPermissions} 是否存在（含显式空对象）。
     * 不从其它传感器字段推断。
     */
    public boolean isAndroidSensorRequiredPermissionsConfigured() {
        return androidSensorsConfig != null
                && androidSensorsConfig.isRequiredPermissionsConfigured();
    }

    /**
     * {@code android.sensors.requiredPermissions} 是否对该 {@code sensorType} 有显式条目。
     */
    public boolean isAndroidSensorRequiredPermissionConfigured(int sensorType) {
        return androidSensorsConfig != null
                && androidSensorsConfig.isRequiredPermissionConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置 {@code getRequiredPermission}；无显式
     * {@code requiredPermissions} 条目时为 {@code null}。空串是合法已配置值。
     */
    public String getAndroidSensorRequiredPermission(int sensorType) {
        return androidSensorsConfig == null
                ? null : androidSensorsConfig.getRequiredPermission(sensorType);
    }

    /**
     * {@code android.sensors.additionalInfoSupported} 是否存在（含显式空对象）。
     * 不从其它传感器字段推断。
     */
    public boolean isAndroidSensorAdditionalInfoSupportedConfigured() {
        return androidSensorsConfig != null
                && androidSensorsConfig.isAdditionalInfoSupportedConfigured();
    }

    /**
     * {@code android.sensors.additionalInfoSupported} 是否对该 {@code sensorType} 有显式条目。
     */
    public boolean isAndroidSensorAdditionalInfoSupportedConfigured(int sensorType) {
        return androidSensorsConfig != null
                && androidSensorsConfig.isAdditionalInfoSupportedConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置 {@code isAdditionalInfoSupported}；无显式
     * {@code additionalInfoSupported} 条目时为 {@code null}。
     * 显式 {@code false} 与缺失不同。
     */
    public Boolean getAndroidSensorAdditionalInfoSupported(int sensorType) {
        return androidSensorsConfig == null
                ? null : androidSensorsConfig.getAdditionalInfoSupported(sensorType);
    }

    /**
     * {@code android.sensors.highestDirectReportRateLevels} 是否存在（含显式空对象）。
     * 不从其它传感器字段推断。
     */
    public boolean isAndroidSensorHighestDirectReportRateLevelsConfigured() {
        return androidSensorsConfig != null
                && androidSensorsConfig.isHighestDirectReportRateLevelsConfigured();
    }

    /**
     * {@code android.sensors.highestDirectReportRateLevels} 是否对该 {@code sensorType}
     * 有显式条目。
     */
    public boolean isAndroidSensorHighestDirectReportRateLevelConfigured(int sensorType) {
        return androidSensorsConfig != null
                && androidSensorsConfig.isHighestDirectReportRateLevelConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 的配置 {@code getHighestDirectReportRateLevel}；无显式
     * {@code highestDirectReportRateLevels} 条目时为 {@code null}。
     */
    public Integer getAndroidSensorHighestDirectReportRateLevel(int sensorType) {
        return androidSensorsConfig == null
                ? null : androidSensorsConfig.getHighestDirectReportRateLevel(sensorType);
    }

    /**
     * {@code android.sensors.directChannelTypesSupported} 是否存在（含显式空对象）。
     * 不从其它传感器字段推断。
     */
    public boolean isAndroidSensorDirectChannelTypesSupportedConfigured() {
        return androidSensorsConfig != null
                && androidSensorsConfig.isDirectChannelTypesSupportedConfigured();
    }

    /**
     * {@code android.sensors.directChannelTypesSupported} 是否对该 {@code sensorType}
     * 有显式条目（含显式空数组）。
     */
    public boolean isAndroidSensorDirectChannelTypeSupportedConfigured(int sensorType) {
        return androidSensorsConfig != null
                && androidSensorsConfig.isDirectChannelTypeSupportedConfigured(sensorType);
    }

    /**
     * 该 {@code sensorType} 配置的直接通道类型集合；无显式
     * {@code directChannelTypesSupported} 条目时为 {@code null}。
     */
    public Set<Integer> getAndroidSensorDirectChannelTypesSupported(int sensorType) {
        return androidSensorsConfig == null
                ? null : androidSensorsConfig.getDirectChannelTypesSupported(sensorType);
    }

    /**
     * 该 {@code sensorType} 是否支持 {@code sharedMemType}；无显式条目时为 {@code null}。
     * 条目存在时未知通道类型返回 {@code false}。
     */
    public Boolean isAndroidSensorDirectChannelTypeSupported(int sensorType, int sharedMemType) {
        return androidSensorsConfig == null
                ? null : androidSensorsConfig.isDirectChannelTypeSupported(sensorType, sharedMemType);
    }

    /**
     * Whether {@code android.clipboard} is present (including an explicit empty object).
     * Missing node returns false so callers keep UOE for clipboard JNI.
     */
    public boolean isAndroidClipboardConfigured() {
        return androidClipboardConfigured;
    }

    /**
     * Immutable {@code android.clipboard} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidClipboardConfig getAndroidClipboardConfig() {
        return androidClipboardConfig;
    }

    /**
     * Whether {@code android.accessibility} is present (including an explicit empty object).
     * Missing node returns false so callers keep UOE for accessibility JNI.
     */
    public boolean isAndroidAccessibilityConfigured() {
        return androidAccessibilityConfigured;
    }

    /**
     * Immutable {@code android.accessibility} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidAccessibilityConfig getAndroidAccessibilityConfig() {
        return androidAccessibilityConfig;
    }

    /**
     * Whether {@code android.audio} is present (including an explicit empty object).
     * Missing node returns false so callers keep UOE for audio JNI.
     */
    public boolean isAndroidAudioConfigured() {
        return androidAudioConfigured;
    }

    /**
     * Immutable {@code android.audio} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidAudioConfig getAndroidAudioConfig() {
        return androidAudioConfig;
    }

    /**
     * Whether {@code android.location} is present (including an explicit empty object).
     * Missing node returns false so callers keep UOE for location master-switch JNI.
     */
    public boolean isAndroidLocationConfigured() {
        return androidLocationConfigured;
    }

    /**
     * Immutable {@code android.location} view (enabled field), or {@code null} when absent.
     * Never exposes JSONObject/JSONArray. Independent of providers.
     */
    public AndroidLocationConfig getAndroidLocationConfig() {
        return androidLocationConfig;
    }

    /**
     * Whether {@code android.location.providers} is present (including an explicit empty object).
     * Missing {@code android.location} or missing {@code providers} returns false so callers keep
     * UOE for location-provider JNI.
     */
    public boolean isAndroidLocationProvidersConfigured() {
        return androidLocationProvidersConfigured;
    }

    /**
     * Immutable {@code android.location.providers} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidLocationProvidersConfig getAndroidLocationProvidersConfig() {
        return androidLocationProvidersConfig;
    }

    /**
     * Whether {@code android.location.lastKnownLocations} is present (including an explicit empty
     * array). Missing key returns false so callers keep UOE for {@code getLastKnownLocation}.
     * Independent of {@code enabled}/{@code providers}.
     */
    public boolean isAndroidLocationLastKnownLocationsConfigured() {
        return androidLocationLastKnownLocationsConfigured;
    }

    /**
     * Immutable {@code android.location.lastKnownLocations} list (possibly empty), never
     * {@code null}. Empty when the key is absent or explicitly {@code []}.
     * Never exposes JSONObject/JSONArray.
     */
    public List<AndroidLastKnownLocationConfig> getAndroidLocationLastKnownLocations() {
        return androidLocationLastKnownLocations == null
                ? Collections.<AndroidLastKnownLocationConfig>emptyList()
                : androidLocationLastKnownLocations;
    }

    /**
     * Whether {@code android.location.providerCapabilities} is present (including an explicit empty
     * object). Missing {@code android.location} or missing {@code providerCapabilities} returns
     * false so callers keep UOE for {@code LocationProvider.requiresNetwork} /
     * {@code LocationProvider.requiresSatellite} / {@code LocationProvider.requiresCell} /
     * {@code LocationProvider.hasMonetaryCost} /
     * {@code LocationProvider.supportsAltitude} /
     * {@code LocationProvider.supportsSpeed} /
     * {@code LocationProvider.supportsBearing} /
     * {@code LocationProvider.meetsCriteria} /
     * {@code LocationProvider.getAccuracy}.
     * Independent of {@code enabled}/{@code providers}/{@code lastKnownLocations}.
     */
    public boolean isAndroidLocationProviderCapabilitiesConfigured() {
        return androidLocationProviderCapabilitiesConfigured;
    }

    /**
     * Immutable {@code android.location.providerCapabilities} view, or {@code null} when the key
     * is absent. Never exposes JSONObject/JSONArray.
     */
    public AndroidLocationProviderCapabilitiesConfig getAndroidLocationProviderCapabilitiesConfig() {
        return androidLocationProviderCapabilitiesConfig;
    }

    /**
     * Lookup {@code android.location.providerCapabilities.<name>} ({@code gps}/{@code network}/
     * {@code passive}), or {@code null} when capabilities are unconfigured or that provider key
     * is omitted. Never exposes JSONObject/JSONArray.
     */
    public AndroidLocationProviderCapabilityEntry getAndroidLocationProviderCapability(String name) {
        if (!androidLocationProviderCapabilitiesConfigured
                || androidLocationProviderCapabilitiesConfig == null) {
            return null;
        }
        return androidLocationProviderCapabilitiesConfig.get(name);
    }

    /**
     * Whether {@code android.identifiers} is present (including an explicit empty object).
     * Missing node returns false so callers may keep host advertising-id fallback.
     */
    public boolean isAndroidIdentifiersConfigured() {
        return androidIdentifiersConfigured;
    }

    /**
     * Immutable {@code android.identifiers} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidIdentifiersConfig getAndroidIdentifiersConfig() {
        return androidIdentifiersConfig;
    }

    /**
     * Whether {@code android.userState} is present (including an explicit empty object).
     * Missing node returns false so callers may keep host UserManager fallback.
     */
    public boolean isAndroidUserStateConfigured() {
        return androidUserStateConfigured;
    }

    /**
     * Immutable {@code android.userState} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidUserStateConfig getAndroidUserStateConfig() {
        return androidUserStateConfig;
    }

    /**
     * Whether {@code android.securitySignals} is present (including an explicit empty object).
     * Missing node returns false so callers may keep host debugger/security fallbacks.
     */
    public boolean isAndroidSecuritySignalsConfigured() {
        return androidSecuritySignalsConfigured;
    }

    /**
     * Immutable {@code android.securitySignals} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidSecuritySignalsConfig getAndroidSecuritySignalsConfig() {
        return androidSecuritySignalsConfig;
    }

    /**
     * Whether {@code android.securityState} is present (including an explicit empty object).
     */
    public boolean isAndroidSecurityStateConfigured() {
        return androidSecurityStateConfigured;
    }

    /**
     * Immutable {@code android.securityState} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public AndroidSecurityStateConfig getAndroidSecurityStateConfig() {
        return androidSecurityStateConfig;
    }

    /**
     * Whether {@code filesystem.stat} is present in JSON (including an explicit empty object).
     * Missing {@code filesystem} or missing {@code stat} key returns false.
     */
    public boolean isFilesystemStatConfigured() {
        JSONObject filesystem = section("filesystem");
        return filesystem != null && filesystem.containsKey("stat");
    }

    /**
     * Immutable entries under {@code filesystem.stat}, in JSON key encounter order.
     * Empty when the key is absent or the path map is empty. Never exposes JSONObject/JSONArray.
     */
    public List<FileStatConfig> getFilesystemStats() {
        return filesystemStats;
    }

    /**
     * Lookup by POSIX-normalized absolute path, or {@code null} when not found or path is illegal.
     * Applies {@link #normalizePosixAbsolutePath(String, String)}; null/blank/relative/NUL/backslash/
     * above-root paths return {@code null} without throwing. Equivalent paths after normalization hit.
     */
    public FileStatConfig getFilesystemStat(String path) {
        if (path == null) {
            return null;
        }
        final String normalized;
        try {
            normalized = normalizePosixAbsolutePath(path, "path");
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        for (FileStatConfig stat : filesystemStats) {
            if (normalized.equals(stat.getPath())) {
                return stat;
            }
        }
        return null;
    }

    /**
     * Whether {@code filesystem.statfs} is present in JSON (including an explicit empty object).
     * Missing {@code filesystem} or missing {@code statfs} key returns false.
     */
    public boolean isFilesystemStatFsConfigured() {
        JSONObject filesystem = section("filesystem");
        return filesystem != null && filesystem.containsKey("statfs");
    }

    /**
     * Immutable entries under {@code filesystem.statfs}, in JSON key encounter order.
     * Empty when the key is absent or the mount map is empty. Never exposes JSONObject/JSONArray.
     */
    public List<FileStatFsConfig> getFilesystemStatFsEntries() {
        return filesystemStatFsEntries;
    }

    /**
     * Exact lookup by POSIX-normalized absolute mount point, or {@code null} when not found or
     * the path is illegal. Does <strong>not</strong> perform longest-prefix path matching.
     * Null/blank/relative/NUL/backslash/above-root paths return {@code null} without throwing.
     */
    public FileStatFsConfig getFilesystemStatFsExact(String mountPoint) {
        if (mountPoint == null) {
            return null;
        }
        final String normalized;
        try {
            normalized = normalizePosixAbsolutePath(mountPoint, "mountPoint");
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        for (FileStatFsConfig entry : filesystemStatFsEntries) {
            if (normalized.equals(entry.getMountPoint())) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Longest path-boundary mount match for a query pathname (for future {@code statfs} wiring).
     * Uses the same POSIX absolute path normalization as {@link #getFilesystemStatFsExact(String)}.
     * Illegal, null, blank, relative, NUL, backslash, or above-root paths return {@code null}
     * without throwing.
     * <p>
     * Among configured mount points, selects the longest match where:
     * the query equals the mount point, or the mount point is root {@code /}, or the query
     * starts with {@code mountPoint + "/"}. Path-boundary only: {@code /data} does not match
     * {@code /database}. Exact mount points beat shorter prefixes; {@code /} is the final fallback.
     * Does not modify or replace {@link #getFilesystemStatFsExact(String)}.
     */
    public FileStatFsConfig getFilesystemStatFs(String pathname) {
        if (pathname == null) {
            return null;
        }
        final String normalized;
        try {
            normalized = normalizePosixAbsolutePath(pathname, "pathname");
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        FileStatFsConfig best = null;
        int bestLength = -1;
        for (FileStatFsConfig entry : filesystemStatFsEntries) {
            String mountPoint = entry.getMountPoint();
            if (!isStatFsMountPrefixMatch(normalized, mountPoint)) {
                continue;
            }
            int length = mountPoint.length();
            if (length > bestLength) {
                bestLength = length;
                best = entry;
            }
        }
        return best;
    }

    /**
     * Path-boundary prefix match: equal path, root mount {@code /}, or {@code path} starts with
     * {@code mountPoint + "/"}. Prevents {@code /data} matching {@code /database}.
     */
    private static boolean isStatFsMountPrefixMatch(String path, String mountPoint) {
        if (path.equals(mountPoint)) {
            return true;
        }
        if ("/".equals(mountPoint)) {
            return true;
        }
        return path.startsWith(mountPoint + "/");
    }

    /**
     * Whether {@code filesystem.mounts} is present in JSON (including an explicit empty array).
     * Missing {@code filesystem} or missing {@code mounts} key returns false.
     */
    public boolean isFilesystemMountsConfigured() {
        JSONObject filesystem = section("filesystem");
        return filesystem != null && filesystem.containsKey("mounts");
    }

    /**
     * Immutable entries under {@code filesystem.mounts}, in JSON array order.
     * Empty when the key is absent or the array is empty. Never exposes JSONObject/JSONArray.
     */
    public List<FileSystemMountConfig> getFilesystemMounts() {
        return filesystemMounts;
    }

    /**
     * Whether {@code filesystem.links} is present in JSON (including an explicit empty object).
     * Missing {@code filesystem} or missing {@code links} key returns false.
     */
    public boolean isFilesystemLinksConfigured() {
        JSONObject filesystem = section("filesystem");
        return filesystem != null && filesystem.containsKey("links");
    }

    /**
     * Immutable entries under {@code filesystem.links}, in JSON key encounter order.
     * Empty when the key is absent or the map is empty. Never exposes JSONObject/JSONArray.
     */
    public List<FileSystemLinkConfig> getFilesystemLinks() {
        return filesystemLinks;
    }

    /**
     * Whether {@code filesystem.externalStorage} is present (including an explicit empty object).
     * Missing {@code filesystem} or missing {@code externalStorage} key returns false.
     */
    public boolean isFilesystemExternalStorageConfigured() {
        return filesystemExternalStorageConfigured;
    }

    /**
     * Immutable {@code filesystem.externalStorage} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public FileSystemExternalStorageConfig getFilesystemExternalStorageConfig() {
        return filesystemExternalStorageConfig;
    }

    /**
     * Whether {@code filesystem.systemDirectories} is present (including an explicit empty object).
     * Missing {@code filesystem} or missing {@code systemDirectories} key returns false.
     * Individual path fields may still be unconfigured; JNI wires each API only when that field
     * is explicit.
     */
    public boolean isFilesystemSystemDirectoriesConfigured() {
        return filesystemSystemDirectoriesConfigured;
    }

    /**
     * Immutable {@code filesystem.systemDirectories} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public FileSystemSystemDirectoriesConfig getFilesystemSystemDirectoriesConfig() {
        return filesystemSystemDirectoriesConfig;
    }

    /**
     * Exact lookup by POSIX-normalized absolute link path, or {@code null} when not found or
     * the path is illegal. Does <strong>not</strong> rewrite pid/self aliases or prefix-match.
     * Null/blank/relative/NUL/backslash/above-root paths return {@code null} without throwing.
     */
    public FileSystemLinkConfig getFilesystemLinkExact(String path) {
        if (path == null) {
            return null;
        }
        final String normalized;
        try {
            normalized = normalizePosixAbsolutePath(path, "path");
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        for (FileSystemLinkConfig link : filesystemLinks) {
            if (normalized.equals(link.getPath())) {
                return link;
            }
        }
        return null;
    }

    public String getVersionName(String fallback) {
        return getString(android(), "versionName", fallback);
    }

    public long getVersionCode(long fallback) {
        return getLong(android(), "versionCode", fallback);
    }

    public String getApkPath(String fallback) {
        return getString(android(), "apkPath", fallback);
    }

    public String getDataDir(String fallback) {
        return getString(android(), "dataDir", fallback);
    }

    /**
     * Whether root {@code android.dataDir} is explicitly present as a non-null String.
     * Missing key or explicit JSON null returns false (so {@code Context.getFilesDir}/
     * {@code getCacheDir} keep historical UOE). Distinct from per-package
     * {@code android.packages[].dataDir}.
     */
    public boolean isAndroidDataDirConfigured() {
        JSONObject android = android();
        if (android == null || !android.containsKey("dataDir")) {
            return false;
        }
        return android.get("dataDir") instanceof String;
    }

    /**
     * Non-null root {@code android.dataDir} when {@link #isAndroidDataDirConfigured()} is true;
     * otherwise {@code null}. Does not apply {@link #getDataDir(String)} fallback.
     */
    public String getAndroidDataDir() {
        if (!isAndroidDataDirConfigured()) {
            return null;
        }
        return (String) android().get("dataDir");
    }

    public String getNativeLibraryDir(boolean is64Bit, String fallback) {
        return getString(android(), is64Bit ? "nativeLibraryDir64" : "nativeLibraryDir32", fallback);
    }

    public String getAndroidBuildString(String key) {
        JSONObject build = androidBuild();
        if (build == null) {
            return null;
        }
        // Long Build.TIME uses dedicated accessor; never coerce via String field path.
        if ("TIME".equals(key)) {
            return null;
        }
        Object value = build.get(key);
        if (value == null) {
            return null;
        }
        // Array/object build fields (e.g. SUPPORTED_ABIS) use dedicated accessors, not string coercion.
        if (value instanceof JSONArray || value instanceof JSONObject) {
            return null;
        }
        return String.valueOf(value);
    }

    /**
     * {@code android.build.<key>} when the key is present and the JSON value is a String.
     * Returns the configured text unchanged (no Number/Boolean coercion). Missing key,
     * JSON null, Number, Boolean, array, or object returns {@code null}.
     */
    public String getAndroidBuildStringIfString(String key) {
        JSONObject build = androidBuild();
        if (build == null) {
            return null;
        }
        Object value = build.get(key);
        if (!(value instanceof String)) {
            return null;
        }
        return (String) value;
    }

    public Integer getAndroidBuildInt(String key) {
        // Long Build.TIME is not an int field path.
        if ("TIME".equals(key)) {
            return null;
        }
        return getIntegerObject(androidBuild(), key);
    }

    /**
     * Whether {@code android.build.TIME} is present and validated.
     * Missing key returns false so JNI keeps UOE for {@code Build.TIME:J}.
     */
    public boolean isAndroidBuildTimeConfigured() {
        return androidBuildTimeConfigured;
    }

    /**
     * Configured {@code Build.TIME} millis (nonnegative long), or {@code null} when the key is
     * absent. Never exposes JSONObject.
     */
    public Long getAndroidBuildTime() {
        return androidBuildTimeConfigured ? Long.valueOf(androidBuildTime) : null;
    }

    /**
     * Whether {@code android.runtime.systemProperties} is present (including explicit empty
     * object). Missing node/key returns false so JNI keeps UOE for {@code System.getProperty}
     * and {@code System.getProperties()}. No host JVM property fallback.
     */
    public boolean isAndroidRuntimeSystemPropertiesConfigured() {
        return androidRuntimeSystemPropertiesConfigured;
    }

    /**
     * Immutable map of configured system property keys to String or {@code null} (explicit JSON
     * null). Empty when the key is absent or {@code {}}. Never exposes JSONObject or a live
     * mutable map. Not derived from host {@code System.getProperties()}.
     */
    public Map<String, String> getAndroidRuntimeSystemProperties() {
        return androidRuntimeSystemProperties == null
                ? Collections.<String, String>emptyMap()
                : androidRuntimeSystemProperties;
    }

    /**
     * Whether {@code key} is present in configured {@code systemProperties}
     * (including explicit JSON null values).
     */
    public boolean isAndroidRuntimeSystemPropertyConfigured(String key) {
        return androidRuntimeSystemPropertiesConfigured
                && key != null
                && androidRuntimeSystemProperties != null
                && androidRuntimeSystemProperties.containsKey(key);
    }

    /**
     * Configured value for {@code key}, or {@code null} when the key is absent or the configured
     * value is explicit JSON null. Use {@link #isAndroidRuntimeSystemPropertyConfigured(String)}
     * to distinguish absence from explicit null.
     */
    public String getAndroidRuntimeSystemProperty(String key) {
        if (!isAndroidRuntimeSystemPropertyConfigured(key)) {
            return null;
        }
        return androidRuntimeSystemProperties.get(key);
    }

    /**
     * Whether {@code android.runtime.environmentVariables} is present (including explicit empty
     * object). Missing node/key returns false so JNI keeps UOE for {@code System.getenv(String)}
     * and {@code System.getenv() Map}. Independent of
     * {@link #isAndroidRuntimeSystemPropertiesConfigured()} and {@code linux.environ}; no host
     * env fallback.
     */
    public boolean isAndroidRuntimeEnvironmentVariablesConfigured() {
        return androidRuntimeEnvironmentVariablesConfigured;
    }

    /**
     * Immutable map of configured environment variable keys to String or {@code null}
     * (explicit JSON null). Empty when the key is absent or {@code {}}. Never exposes
     * JSONObject. Not derived from host environment or {@code linux.environ}.
     */
    public Map<String, String> getAndroidRuntimeEnvironmentVariables() {
        return androidRuntimeEnvironmentVariables == null
                ? Collections.<String, String>emptyMap()
                : androidRuntimeEnvironmentVariables;
    }

    /**
     * Whether {@code key} is present in configured {@code environmentVariables}
     * (including explicit JSON null values).
     */
    public boolean isAndroidRuntimeEnvironmentVariableConfigured(String key) {
        return androidRuntimeEnvironmentVariablesConfigured
                && key != null
                && androidRuntimeEnvironmentVariables != null
                && androidRuntimeEnvironmentVariables.containsKey(key);
    }

    /**
     * Configured env value for {@code key}, or {@code null} when the key is absent or the
     * configured value is explicit JSON null. Use
     * {@link #isAndroidRuntimeEnvironmentVariableConfigured(String)} to distinguish absence from
     * explicit null.
     */
    public String getAndroidRuntimeEnvironmentVariable(String key) {
        if (!isAndroidRuntimeEnvironmentVariableConfigured(key)) {
            return null;
        }
        return androidRuntimeEnvironmentVariables.get(key);
    }

    /**
     * Whether {@code android.runtime.availableProcessors} is present (exact int {@code 1..4096}).
     * Missing key returns false so JNI keeps UOE for {@code Runtime.availableProcessors}.
     * {@code Runtime.getRuntime} is enabled when this field, {@code maxMemoryBytes}, or
     * {@code totalMemoryBytes} is present. Independent of {@code linux.cpu} and host Runtime.
     */
    public boolean isAndroidRuntimeAvailableProcessorsConfigured() {
        return androidRuntimeAvailableProcessorsConfigured;
    }

    /**
     * Configured processor count when {@link #isAndroidRuntimeAvailableProcessorsConfigured()} is
     * true; otherwise undefined (callers must check the flag). Range {@code 1..4096}.
     */
    public int getAndroidRuntimeAvailableProcessors() {
        return androidRuntimeAvailableProcessors;
    }

    /**
     * 是否存在 {@code android.runtime.maxMemoryBytes}（精确 JSON 整数 {@code 1..Long.MAX_VALUE}）。
     * 键缺失返回 false，JNI 对 {@code Runtime.maxMemory} 保持 UOE。
     * {@code Runtime.getRuntime} 在本字段、{@code availableProcessors} 或 {@code totalMemoryBytes}
     * 任一显式配置时启用。与 {@code linux.cpu}、宿主 {@code Runtime.maxMemory()} 独立。
     */
    public boolean isAndroidRuntimeMaxMemoryBytesConfigured() {
        return androidRuntimeMaxMemoryBytesConfigured;
    }

    /**
     * 已配置的 maxMemory 字节数；仅当 {@link #isAndroidRuntimeMaxMemoryBytesConfigured()} 为 true
     * 时有定义（调用方须先检查标志）。范围 {@code 1..Long.MAX_VALUE}。
     */
    public long getAndroidRuntimeMaxMemoryBytes() {
        return androidRuntimeMaxMemoryBytes;
    }

    /**
     * 是否存在 {@code android.runtime.totalMemoryBytes}（精确 JSON 整数 {@code 1..Long.MAX_VALUE}）。
     * 键缺失返回 false，JNI 对 {@code Runtime.totalMemory} 保持 UOE。
     * {@code Runtime.getRuntime} 在本字段、{@code availableProcessors} 或 {@code maxMemoryBytes}
     * 任一显式配置时启用。与 {@code linux.cpu}、宿主 {@code Runtime.totalMemory()} 独立。
     */
    public boolean isAndroidRuntimeTotalMemoryBytesConfigured() {
        return androidRuntimeTotalMemoryBytesConfigured;
    }

    /**
     * 已配置的 totalMemory 字节数；仅当 {@link #isAndroidRuntimeTotalMemoryBytesConfigured()} 为 true
     * 时有定义（调用方须先检查标志）。范围 {@code 1..Long.MAX_VALUE}。
     */
    public long getAndroidRuntimeTotalMemoryBytes() {
        return androidRuntimeTotalMemoryBytes;
    }

    /**
     * 是否存在 {@code android.runtime.freeMemoryBytes}（精确 JSON 整数 {@code 0..Long.MAX_VALUE}）。
     * 键缺失返回 false，JNI 对 {@code Runtime.freeMemory} 保持 UOE。
     * 本字段不能独立出现：解析期要求 {@code totalMemoryBytes} 已显式配置，并拒绝
     * {@code freeMemoryBytes > totalMemoryBytes}。
     * {@code Runtime.getRuntime} 仍由 {@code availableProcessors} / {@code maxMemoryBytes} /
     * {@code totalMemoryBytes} 门控（free 必然要求 total，无需额外门控）。
     * 与 {@code linux.cpu}、宿主 {@code Runtime.freeMemory()} 独立。
     */
    public boolean isAndroidRuntimeFreeMemoryBytesConfigured() {
        return androidRuntimeFreeMemoryBytesConfigured;
    }

    /**
     * 已配置的 freeMemory 字节数；仅当 {@link #isAndroidRuntimeFreeMemoryBytesConfigured()} 为 true
     * 时有定义（调用方须先检查标志）。范围 {@code 0..Long.MAX_VALUE}。
     */
    public long getAndroidRuntimeFreeMemoryBytes() {
        return androidRuntimeFreeMemoryBytes;
    }

    /**
     * Whether {@code android.build.SUPPORTED_ABIS} is present and validated.
     * Missing key returns false so JNI keeps UOE for the array field.
     */
    public boolean isAndroidBuildSupportedAbisConfigured() {
        return androidBuildSupportedAbisConfigured;
    }

    /**
     * Immutable ABI name list from {@code android.build.SUPPORTED_ABIS}, or {@code null} when absent.
     * Values are trimmed non-empty strings in JSON order. Never exposes JSONArray.
     */
    public List<String> getAndroidBuildSupportedAbis() {
        return androidBuildSupportedAbis;
    }

    /**
     * Whether {@code android.build.SUPPORTED_32_BIT_ABIS} is present and validated.
     * Independent of {@link #isAndroidBuildSupportedAbisConfigured()}; missing key returns false.
     */
    public boolean isAndroidBuildSupported32BitAbisConfigured() {
        return androidBuildSupported32BitAbisConfigured;
    }

    /**
     * Immutable 32-bit ABI name list from {@code android.build.SUPPORTED_32_BIT_ABIS}, or {@code null}
     * when absent. Values are trimmed non-empty strings in JSON order. Never exposes JSONArray.
     * Not inferred from {@code SUPPORTED_ABIS}.
     */
    public List<String> getAndroidBuildSupported32BitAbis() {
        return androidBuildSupported32BitAbis;
    }

    /**
     * Whether {@code android.build.SUPPORTED_64_BIT_ABIS} is present and validated.
     * Independent of {@link #isAndroidBuildSupportedAbisConfigured()} and
     * {@link #isAndroidBuildSupported32BitAbisConfigured()}; missing key returns false.
     */
    public boolean isAndroidBuildSupported64BitAbisConfigured() {
        return androidBuildSupported64BitAbisConfigured;
    }

    /**
     * Immutable 64-bit ABI name list from {@code android.build.SUPPORTED_64_BIT_ABIS}, or {@code null}
     * when absent. Values are trimmed non-empty strings in JSON order. Never exposes JSONArray.
     * Not inferred from {@code SUPPORTED_ABIS} or {@code SUPPORTED_32_BIT_ABIS}.
     */
    public List<String> getAndroidBuildSupported64BitAbis() {
        return androidBuildSupported64BitAbis;
    }

    public String getAndroidProperty(String key) {
        return getString(androidProperties(), key, null);
    }

    public boolean hasAndroidProperties() {
        JSONObject properties = androidProperties();
        return properties != null && !properties.isEmpty();
    }

    /**
     * Whether {@code network.interfaces} is present in JSON (including an empty array).
     * When true, SocketIO must not fall back to host network enumeration.
     */
    public boolean isNetworkInterfacesConfigured() {
        JSONObject network = section("network");
        return network != null && network.containsKey("interfaces");
    }

    /**
     * Immutable entries under {@code network.interfaces} for SocketIO ioctl
     * (SIOCGIFCONF / SIOCGIFADDR / SIOCGIFFLAGS / SIOCGIFNAME).
     * Returns an unmodifiable list (empty when the key is absent or the array is empty).
     * Never exposes JSONObject/JSONArray.
     */
    public List<NetworkInterfaceConfig> getNetworkInterfaces() {
        return networkInterfaces;
    }

    /**
     * Whether {@code network.ipv4Routes} is present in JSON (including an explicit empty array).
     * Missing key must not take over {@code /proc/net/route} aliases.
     */
    public boolean isNetworkIpv4RoutesConfigured() {
        JSONObject network = section("network");
        return network != null && network.containsKey("ipv4Routes");
    }

    /**
     * Immutable entries under {@code network.ipv4Routes} in JSON array order.
     * Empty when the key is absent or the array is empty. Never exposes JSONObject/JSONArray.
     * Values are never inferred from {@code network.interfaces}, wifi, or other fields.
     */
    public List<NetworkIpv4RouteConfig> getNetworkIpv4Routes() {
        return networkIpv4Routes;
    }

    /**
     * Whether {@code network.interfaceStats} is present in JSON (including an explicit empty array).
     * Missing key must not take over {@code /proc/net/dev} aliases.
     */
    public boolean isNetworkInterfaceStatsConfigured() {
        JSONObject network = section("network");
        return network != null && network.containsKey("interfaceStats");
    }

    /**
     * Immutable entries under {@code network.interfaceStats} in JSON array order.
     * Empty when the key is absent or the array is empty. Never exposes JSONObject/JSONArray.
     * Counters are never inferred from {@code network.interfaces}, ipv4Routes, wifi, or other fields.
     */
    public List<NetworkInterfaceStatsConfig> getNetworkInterfaceStats() {
        return networkInterfaceStats;
    }

    /**
     * Whether {@code network.ipv6Addresses} is present in JSON (including an explicit empty array).
     * Missing key must not take over {@code /proc/net/if_inet6} aliases.
     */
    public boolean isNetworkIpv6AddressesConfigured() {
        JSONObject network = section("network");
        return network != null && network.containsKey("ipv6Addresses");
    }

    /**
     * Immutable entries under {@code network.ipv6Addresses} in JSON array order.
     * Empty when the key is absent or the array is empty. Never exposes JSONObject/JSONArray.
     * Address, prefix, scope, and flags are never inferred from {@code network.interfaces},
     * ipv4Routes, interfaceStats, wifi, or other fields.
     */
    public List<NetworkIpv6AddressConfig> getNetworkIpv6Addresses() {
        return networkIpv6Addresses;
    }

    public boolean isNetworkTcpConfigured() {
        return networkTcpConfigured;
    }

    public List<NetworkTcpConfig> getNetworkTcp() {
        return networkTcp;
    }

    public boolean isNetworkTcp6Configured() {
        return networkTcp6Configured;
    }

    public List<NetworkTcpConfig> getNetworkTcp6() {
        return networkTcp6;
    }

    public boolean isNetworkCapabilitiesConfigured() {
        return networkCapabilitiesConfigured;
    }

    public NetworkCapabilitiesConfig getNetworkCapabilitiesConfig() {
        return networkCapabilitiesConfig;
    }

    public boolean isLinuxProcessesConfigured() {
        return linuxProcessesConfigured;
    }

    public List<LinuxProcessConfig> getLinuxProcesses() {
        return linuxProcesses;
    }

    public LinuxProcessConfig findLinuxProcess(int pid) {
        for (int i = 0; i < linuxProcesses.size(); i++) {
            LinuxProcessConfig process = linuxProcesses.get(i);
            if (process.getPid() == pid) {
                return process;
            }
        }
        return null;
    }

    public boolean isLinuxCommandsConfigured() {
        return linuxCommandsConfigured;
    }

    public Map<String, String> getLinuxCommands() {
        return linuxCommands;
    }

    public String getLinuxCommandStdout(String command) {
        return command == null ? null : linuxCommands.get(command);
    }

    public boolean isLinuxMincoreConfigured() {
        return linuxMincoreConfigured;
    }

    public boolean isLinuxMincoreResident() {
        return linuxMincoreResident;
    }

    public boolean isFilesystemDirectoriesConfigured() {
        return filesystemDirectoriesConfigured;
    }

    public List<String> getFilesystemDirectoryEntries(String path) {
        return filesystemDirectories.get(path);
    }

    public boolean isAndroidDisplaysConfigured() {
        return androidDisplaysConfigured;
    }

    public List<AndroidDisplayEntryConfig> getAndroidDisplays() {
        return androidDisplays;
    }

    public AndroidDisplayEntryConfig findAndroidDisplay(int id) {
        for (int i = 0; i < androidDisplays.size(); i++) {
            AndroidDisplayEntryConfig display = androidDisplays.get(i);
            if (display.getId() == id) {
                return display;
            }
        }
        return null;
    }

    public boolean isAndroidSensorSamplesConfigured() {
        return androidSensorSamplesConfigured;
    }

    public float[] getAndroidSensorSample(int sensorType) {
        float[] sample = androidSensorSamples.get(Integer.valueOf(sensorType));
        if (sample == null) {
            return null;
        }
        return Arrays.copyOf(sample, sample.length);
    }

    /**
     * Whether {@code network.arpEntries} is present in JSON (including an explicit empty array).
     * Missing key must not take over {@code /proc/net/arp} aliases.
     */
    public boolean isNetworkArpEntriesConfigured() {
        JSONObject network = section("network");
        return network != null && network.containsKey("arpEntries");
    }

    /**
     * Immutable entries under {@code network.arpEntries} in JSON array order.
     * Empty when the key is absent or the array is empty. Never exposes JSONObject/JSONArray.
     * Values are never inferred from {@code network.interfaces}, ipv4Routes, interfaceStats,
     * ipv6Addresses, wifi, or other fields.
     */
    public List<NetworkArpEntryConfig> getNetworkArpEntries() {
        return networkArpEntries;
    }

    /**
     * Whether {@code network.igmpMemberships} is present in JSON (including an explicit empty array).
     * Missing key must not take over {@code /proc/net/igmp} aliases.
     */
    public boolean isNetworkIgmpMembershipsConfigured() {
        JSONObject network = section("network");
        return network != null && network.containsKey("igmpMemberships");
    }

    /**
     * Immutable entries under {@code network.igmpMemberships} in JSON array order.
     * Empty when the key is absent or the array is empty. Never exposes JSONObject/JSONArray.
     * Values are never inferred from {@code network.interfaces}, ipv4Routes, interfaceStats,
     * ipv6Addresses, arpEntries, wifi, or other fields.
     */
    public List<NetworkIgmpMembershipConfig> getNetworkIgmpMemberships() {
        return networkIgmpMemberships;
    }

    /**
     * Whether {@code network.igmp6Memberships} is present in JSON (including an explicit empty array).
     * Missing key must not take over {@code /proc/net/igmp6} aliases.
     */
    public boolean isNetworkIgmp6MembershipsConfigured() {
        JSONObject network = section("network");
        return network != null && network.containsKey("igmp6Memberships");
    }

    /**
     * Immutable entries under {@code network.igmp6Memberships} in JSON array order.
     * Empty when the key is absent or the array is empty. Never exposes JSONObject/JSONArray.
     * Values are never inferred from {@code network.interfaces}, ipv4Routes, interfaceStats,
     * ipv6Addresses, arpEntries, igmpMemberships, wifi, or other fields.
     */
    public List<NetworkIgmp6MembershipConfig> getNetworkIgmp6Memberships() {
        return networkIgmp6Memberships;
    }

    /**
     * Whether {@code network.linkLayerMulticastEntries} is present in JSON (including an
     * explicit empty array). Missing key must not take over {@code /proc/net/dev_mcast}
     * aliases.
     */
    public boolean isNetworkLinkLayerMulticastEntriesConfigured() {
        JSONObject network = section("network");
        return network != null && network.containsKey("linkLayerMulticastEntries");
    }

    /**
     * Immutable entries under {@code network.linkLayerMulticastEntries} in JSON array order.
     * Empty when the key is absent or the array is empty. Never exposes JSONObject/JSONArray.
     * Values are never inferred from {@code network.interfaces} (including {@code mac} and
     * {@code linkLayerBroadcast}), ipv4Routes, interfaceStats, ipv6Addresses, arpEntries,
     * igmpMemberships, igmp6Memberships, wifi, or other fields.
     */
    public List<NetworkLinkLayerMulticastEntryConfig> getNetworkLinkLayerMulticastEntries() {
        return networkLinkLayerMulticastEntries;
    }

    /**
     * Whether {@code network.wirelessProcStats} is present in JSON (including an explicit
     * object with empty {@code entries}). Missing key must not take over
     * {@code /proc/net/wireless} aliases.
     */
    public boolean isNetworkWirelessProcStatsConfigured() {
        JSONObject network = section("network");
        return network != null && network.containsKey("wirelessProcStats");
    }

    /**
     * Parsed {@code network.wirelessProcStats} object, or {@code null} when the key is absent.
     * Values are never inferred from {@code network.interfaces}, ipv4Routes, interfaceStats,
     * ipv6Addresses, arpEntries, igmpMemberships, igmp6Memberships,
     * linkLayerMulticastEntries, wifi, or other fields.
     */
    public NetworkWirelessProcStatsConfig getNetworkWirelessProcStats() {
        return networkWirelessProcStats;
    }

    /**
     * Immutable {@code network.wirelessProcStats.entries} in JSON array order.
     * Empty when the key is absent or {@code entries} is empty. Never exposes JSONObject/JSONArray.
     */
    public List<NetworkWirelessProcStatsEntryConfig> getNetworkWirelessProcStatsEntries() {
        if (networkWirelessProcStats == null) {
            return Collections.emptyList();
        }
        return networkWirelessProcStats.getEntries();
    }

    /**
     * @return true when at least one interface object is listed (not merely that the key exists).
     */
    public boolean hasNetworkInterfaces() {
        return !networkInterfaces.isEmpty();
    }

    /**
     * Whether {@code network.bluetooth} is present (including an explicit empty object).
     */
    public boolean isNetworkBluetoothConfigured() {
        return networkBluetoothConfigured;
    }

    /**
     * Immutable {@code network.bluetooth} view, or {@code null} when the key is absent.
     * Never exposes JSONObject/JSONArray.
     */
    public NetworkBluetoothConfig getNetworkBluetoothConfig() {
        return networkBluetoothConfig;
    }

    private JSONObject networkWifi() {
        JSONObject network = section("network");
        return network == null ? null : network.getJSONObject("wifi");
    }

    private static boolean isNetworkWifiStringKey(String key) {
        for (String allowed : NETWORK_WIFI_STRING_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static void requireNetworkWifiStringKey(String key) {
        if (key == null || !isNetworkWifiStringKey(key)) {
            throw new IllegalArgumentException(
                    "key must be one of ssid|bssid|macAddress|ipv4: " + key);
        }
    }

    private static boolean isNetworkWifiIntKey(String key) {
        for (String allowed : NETWORK_WIFI_INT_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static void requireNetworkWifiIntKey(String key) {
        if (key == null || !isNetworkWifiIntKey(key)) {
            throw new IllegalArgumentException(
                    "key must be one of rssi|linkSpeedMbps|frequencyMhz|networkId: " + key);
        }
    }

    /**
     * Whether {@code network.wifi.<key>} is present (including explicit JSON null).
     * {@code key} must be one of ssid|bssid|macAddress|ipv4.
     */
    public boolean isWifiStringConfigured(String key) {
        requireNetworkWifiStringKey(key);
        JSONObject wifi = networkWifi();
        return wifi != null && wifi.containsKey(key);
    }

    /**
     * Wi-Fi string field, or null if missing or explicitly null.
     * MAC fields are stored/returned in Locale.ROOT lowercase after parse validation.
     * Never returns JSONObject/JSONArray/List/Map.
     */
    public String getWifiString(String key) {
        requireNetworkWifiStringKey(key);
        JSONObject wifi = networkWifi();
        if (wifi == null || !wifi.containsKey(key)) {
            return null;
        }
        Object value = wifi.get(key);
        if (value == null) {
            return null;
        }
        return (String) value;
    }

    /**
     * Whether {@code network.wifi.<key>} is present for an int field.
     * {@code key} must be one of rssi|linkSpeedMbps|frequencyMhz|networkId.
     */
    public boolean isWifiIntConfigured(String key) {
        requireNetworkWifiIntKey(key);
        JSONObject wifi = networkWifi();
        return wifi != null && wifi.containsKey(key);
    }

    /**
     * Wi-Fi int field, or {@code fallback} when missing.
     */
    public int getWifiInt(String key, int fallback) {
        requireNetworkWifiIntKey(key);
        if (!isWifiIntConfigured(key)) {
            return fallback;
        }
        Integer value = getIntegerObject(networkWifi(), key);
        return value == null ? fallback : value.intValue();
    }

    /**
     * Whether {@code network.wifi.enabled} is present.
     */
    public boolean isWifiEnabledConfigured() {
        JSONObject wifi = networkWifi();
        return wifi != null && wifi.containsKey("enabled");
    }

    /**
     * {@code network.wifi.enabled}, or {@code fallback} when missing.
     */
    public boolean getWifiEnabled(boolean fallback) {
        if (!isWifiEnabledConfigured()) {
            return fallback;
        }
        Object value = networkWifi().get("enabled");
        if (!(value instanceof Boolean)) {
            return fallback;
        }
        return ((Boolean) value).booleanValue();
    }

    /**
     * Whether {@code network.wifi.state} is present ({@code WifiManager.WIFI_STATE_*} 0..4).
     * Independent of {@link #isWifiEnabledConfigured()}; never inferred from {@code enabled}.
     */
    public boolean isWifiStateConfigured() {
        JSONObject wifi = networkWifi();
        return wifi != null && wifi.containsKey("state");
    }

    /**
     * {@code network.wifi.state} ({@code WIFI_STATE_*} 0..4), or {@code fallback} when missing.
     * Not inferred from {@link #getWifiEnabled(boolean)}.
     */
    public int getWifiState(int fallback) {
        if (!isWifiStateConfigured()) {
            return fallback;
        }
        Integer value = getIntegerObject(networkWifi(), "state");
        return value == null ? fallback : value.intValue();
    }

    private JSONObject networkLinks() {
        JSONObject network = section("network");
        return network == null ? null : network.getJSONObject("links");
    }

    /**
     * Whether {@code network.links} is present in JSON.
     */
    public boolean isNetworkLinksConfigured() {
        JSONObject network = section("network");
        return network != null && network.containsKey("links");
    }

    private static boolean isLinkBooleanKey(String key) {
        for (String allowed : NETWORK_LINKS_BOOLEAN_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static void requireLinkBooleanKey(String key) {
        if (key == null || !isLinkBooleanKey(key)) {
            throw new IllegalArgumentException(
                    "key must be one of connected|privateDnsActive: " + key);
        }
    }

    private static boolean isLinkIntKey(String key) {
        for (String allowed : NETWORK_LINKS_INT_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static void requireLinkIntKey(String key) {
        if (key == null || !isLinkIntKey(key)) {
            throw new IllegalArgumentException(
                    "key must be one of type|mtu|proxyPort|leaseDurationSeconds: " + key);
        }
    }

    private static boolean isLinkStringKey(String key) {
        for (String allowed : NETWORK_LINKS_STRING_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static void requireLinkStringKey(String key) {
        if (key == null || !isLinkStringKey(key)) {
            throw new IllegalArgumentException(
                    "key must be one of typeName|interfaceName|gatewayIpv4|"
                            + "privateDnsServerName|proxyHost|dhcpServerIpv4|domains|netmaskIpv4: "
                            + key);
        }
    }

    /**
     * Whether {@code network.links.&lt;key&gt;} is present for connected|privateDnsActive.
     */
    public boolean isLinkBooleanConfigured(String key) {
        requireLinkBooleanKey(key);
        JSONObject links = networkLinks();
        return links != null && links.containsKey(key);
    }

    /**
     * Link boolean field, or {@code fallback} when missing.
     */
    public boolean getLinkBoolean(String key, boolean fallback) {
        requireLinkBooleanKey(key);
        if (!isLinkBooleanConfigured(key)) {
            return fallback;
        }
        Object value = networkLinks().get(key);
        if (!(value instanceof Boolean)) {
            return fallback;
        }
        return ((Boolean) value).booleanValue();
    }

    /**
     * Whether {@code network.links.<key>} is present for type|mtu|proxyPort|leaseDurationSeconds.
     */
    public boolean isLinkIntConfigured(String key) {
        requireLinkIntKey(key);
        JSONObject links = networkLinks();
        return links != null && links.containsKey(key);
    }

    /**
     * Link int field, or {@code fallback} when missing.
     */
    public int getLinkInt(String key, int fallback) {
        requireLinkIntKey(key);
        if (!isLinkIntConfigured(key)) {
            return fallback;
        }
        Integer value = getIntegerObject(networkLinks(), key);
        return value == null ? fallback : value.intValue();
    }

    /**
     * Whether {@code network.links.<key>} is present (including explicit JSON null where allowed).
     * {@code key} must be typeName|interfaceName|gatewayIpv4|privateDnsServerName|proxyHost|
     * dhcpServerIpv4|domains|netmaskIpv4.
     */
    public boolean isLinkStringConfigured(String key) {
        requireLinkStringKey(key);
        JSONObject links = networkLinks();
        return links != null && links.containsKey(key);
    }

    /**
     * Link string field, or null if missing or explicitly null.
     * Never returns JSONObject/JSONArray/List/Map.
     */
    public String getLinkString(String key) {
        requireLinkStringKey(key);
        JSONObject links = networkLinks();
        if (links == null || !links.containsKey(key)) {
            return null;
        }
        Object value = links.get(key);
        if (value == null) {
            return null;
        }
        return (String) value;
    }

    /**
     * Whether {@code network.links.dnsServers} is present (including an empty array).
     */
    public boolean isLinkDnsServersConfigured() {
        JSONObject links = networkLinks();
        return links != null && links.containsKey("dnsServers");
    }

    /**
     * Configured DNS server IPv4 strings, or an empty immutable list when the key is missing.
     * Never exposes JSONArray.
     */
    public List<String> getLinkDnsServers() {
        return networkLinkDnsServers;
    }

    private String getRandomHex(String key) {
        return getString(section("random"), key, null);
    }

    private String getRandomString(String key) {
        return getString(section("random"), key, null);
    }

    private JSONObject section(String key) {
        return root.getJSONObject(key);
    }

    private JSONObject android() {
        return section("android");
    }

    private JSONObject androidBuild() {
        JSONObject android = android();
        return android == null ? null : android.getJSONObject("build");
    }

    /**
     * Parse-time rules for optional {@code android.build.SUPPORTED_ABIS},
     * {@code android.build.SUPPORTED_32_BIT_ABIS}, and {@code android.build.SUPPORTED_64_BIT_ABIS}
     * when present. Special array fields under existing {@code android.build}; independent of each
     * other and of generic scalar build field lookup. Missing keys leave the corresponding configured
     * flags false. Each requires a non-empty JSONArray of non-blank trimmed strings; never retains
     * JSONArray.
     */
    private void validateAndroidBuildAbiArrays() {
        JSONObject build = androidBuild();
        List<String> supported = parseAndroidBuildAbiArray(build, "SUPPORTED_ABIS");
        if (supported == null) {
            this.androidBuildSupportedAbisConfigured = false;
            this.androidBuildSupportedAbis = null;
        } else {
            this.androidBuildSupportedAbisConfigured = true;
            this.androidBuildSupportedAbis = supported;
        }
        List<String> supported32 = parseAndroidBuildAbiArray(build, "SUPPORTED_32_BIT_ABIS");
        if (supported32 == null) {
            this.androidBuildSupported32BitAbisConfigured = false;
            this.androidBuildSupported32BitAbis = null;
        } else {
            this.androidBuildSupported32BitAbisConfigured = true;
            this.androidBuildSupported32BitAbis = supported32;
        }
        List<String> supported64 = parseAndroidBuildAbiArray(build, "SUPPORTED_64_BIT_ABIS");
        if (supported64 == null) {
            this.androidBuildSupported64BitAbisConfigured = false;
            this.androidBuildSupported64BitAbis = null;
        } else {
            this.androidBuildSupported64BitAbisConfigured = true;
            this.androidBuildSupported64BitAbis = supported64;
        }
    }

    /**
     * Parse-time rules for optional {@code android.build.TIME} when present.
     * Exact JSON Number integer long in {@code 0..Long.MAX_VALUE}. Independent of scalar string
     * fields and ABI arrays. Missing key leaves {@link #isAndroidBuildTimeConfigured()} false.
     * Never retains JSONObject.
     */
    private void validateAndroidBuildTime() {
        JSONObject build = androidBuild();
        if (build == null || !build.containsKey("TIME")) {
            this.androidBuildTimeConfigured = false;
            this.androidBuildTime = 0L;
            return;
        }
        this.androidBuildTime = requireExactJsonNumberLongValue(build.get("TIME"),
                "android.build.TIME", 0L, Long.MAX_VALUE);
        this.androidBuildTimeConfigured = true;
    }

    /**
     * Parse-time rules for optional {@code android.runtime.systemProperties},
     * {@code android.runtime.environmentVariables}, {@code android.runtime.availableProcessors},
     * {@code android.runtime.maxMemoryBytes}, {@code android.runtime.totalMemoryBytes},
     * and {@code android.runtime.freeMemoryBytes} when present. Missing {@code android.runtime}
     * or missing each key leaves the corresponding configured flag false. Explicit empty
     * {@code {}} for maps is configured with an empty map.
     * Map keys are nonempty Strings without NUL/CR/LF; map values are String or explicit JSON null.
     * {@code availableProcessors} is exact int {@code 1..4096}.
     * {@code maxMemoryBytes} / {@code totalMemoryBytes} are exact JSON integer longs
     * {@code 1..Long.MAX_VALUE}. {@code freeMemoryBytes} is an exact JSON integer long
     * {@code 0..Long.MAX_VALUE} and cannot appear alone: it requires explicit
     * {@code totalMemoryBytes} and rejects {@code freeMemoryBytes > totalMemoryBytes}
     * with path {@code android.runtime.freeMemoryBytes}. When both max/total memory fields
     * are explicit, {@code totalMemoryBytes > maxMemoryBytes} is rejected with path
     * {@code android.runtime.totalMemoryBytes}. Together this yields
     * {@code 0 <= free <= total <= max}. The six fields are independent of each other
     * (except those joint memory bounds), of {@code linux.environ}/{@code linux.cpu}, and of host
     * JVM; no host fallback. Materializes immutable maps; never retains JSONObject.
     */
    private void validateAndroidRuntime() {
        JSONObject android = android();
        if (android == null || !android.containsKey("runtime")) {
            this.androidRuntimeSystemPropertiesConfigured = false;
            this.androidRuntimeSystemProperties = Collections.emptyMap();
            this.androidRuntimeEnvironmentVariablesConfigured = false;
            this.androidRuntimeEnvironmentVariables = Collections.emptyMap();
            this.androidRuntimeAvailableProcessorsConfigured = false;
            this.androidRuntimeAvailableProcessors = 0;
            this.androidRuntimeMaxMemoryBytesConfigured = false;
            this.androidRuntimeMaxMemoryBytes = 0L;
            this.androidRuntimeTotalMemoryBytesConfigured = false;
            this.androidRuntimeTotalMemoryBytes = 0L;
            this.androidRuntimeFreeMemoryBytesConfigured = false;
            this.androidRuntimeFreeMemoryBytes = 0L;
            return;
        }
        Object rawRuntime = android.get("runtime");
        if (!(rawRuntime instanceof JSONObject)) {
            throw new IllegalArgumentException("android.runtime must be a JSONObject");
        }
        JSONObject runtime = (JSONObject) rawRuntime;
        for (String key : runtime.keySet()) {
            if (!"systemProperties".equals(key)
                    && !"environmentVariables".equals(key)
                    && !"availableProcessors".equals(key)
                    && !"maxMemoryBytes".equals(key)
                    && !"totalMemoryBytes".equals(key)
                    && !"freeMemoryBytes".equals(key)) {
                throw new IllegalArgumentException("android.runtime." + key
                        + " is not an allowed key"
                        + " (systemProperties|environmentVariables|availableProcessors|maxMemoryBytes|totalMemoryBytes|freeMemoryBytes)");
            }
        }

        if (!runtime.containsKey("systemProperties")) {
            this.androidRuntimeSystemPropertiesConfigured = false;
            this.androidRuntimeSystemProperties = Collections.emptyMap();
        } else {
            this.androidRuntimeSystemProperties = parseAndroidRuntimeStringNullMap(
                    runtime.get("systemProperties"), "android.runtime.systemProperties");
            this.androidRuntimeSystemPropertiesConfigured = true;
        }

        if (!runtime.containsKey("environmentVariables")) {
            this.androidRuntimeEnvironmentVariablesConfigured = false;
            this.androidRuntimeEnvironmentVariables = Collections.emptyMap();
        } else {
            this.androidRuntimeEnvironmentVariables = parseAndroidRuntimeStringNullMap(
                    runtime.get("environmentVariables"), "android.runtime.environmentVariables");
            this.androidRuntimeEnvironmentVariablesConfigured = true;
        }

        if (!runtime.containsKey("availableProcessors")) {
            this.androidRuntimeAvailableProcessorsConfigured = false;
            this.androidRuntimeAvailableProcessors = 0;
        } else {
            this.androidRuntimeAvailableProcessors = requireExactJsonNumberIntField(
                    runtime, "availableProcessors",
                    "android.runtime.availableProcessors",
                    LINUX_CPU_PROCESSOR_COUNT_MIN, LINUX_CPU_PROCESSOR_COUNT_MAX);
            this.androidRuntimeAvailableProcessorsConfigured = true;
        }

        if (!runtime.containsKey("maxMemoryBytes")) {
            this.androidRuntimeMaxMemoryBytesConfigured = false;
            this.androidRuntimeMaxMemoryBytes = 0L;
        } else {
            this.androidRuntimeMaxMemoryBytes = requireExactJsonNumberLongField(
                    runtime, "maxMemoryBytes",
                    "android.runtime.maxMemoryBytes",
                    1L, Long.MAX_VALUE);
            this.androidRuntimeMaxMemoryBytesConfigured = true;
        }

        if (!runtime.containsKey("totalMemoryBytes")) {
            this.androidRuntimeTotalMemoryBytesConfigured = false;
            this.androidRuntimeTotalMemoryBytes = 0L;
        } else {
            this.androidRuntimeTotalMemoryBytes = requireExactJsonNumberLongField(
                    runtime, "totalMemoryBytes",
                    "android.runtime.totalMemoryBytes",
                    1L, Long.MAX_VALUE);
            this.androidRuntimeTotalMemoryBytesConfigured = true;
        }

        if (!runtime.containsKey("freeMemoryBytes")) {
            this.androidRuntimeFreeMemoryBytesConfigured = false;
            this.androidRuntimeFreeMemoryBytes = 0L;
        } else {
            this.androidRuntimeFreeMemoryBytes = requireExactJsonNumberLongField(
                    runtime, "freeMemoryBytes",
                    "android.runtime.freeMemoryBytes",
                    0L, Long.MAX_VALUE);
            this.androidRuntimeFreeMemoryBytesConfigured = true;
        }

        if (this.androidRuntimeFreeMemoryBytesConfigured
                && !this.androidRuntimeTotalMemoryBytesConfigured) {
            throw new IllegalArgumentException(
                    "android.runtime.freeMemoryBytes requires android.runtime.totalMemoryBytes");
        }
        if (this.androidRuntimeFreeMemoryBytesConfigured
                && this.androidRuntimeFreeMemoryBytes > this.androidRuntimeTotalMemoryBytes) {
            throw new IllegalArgumentException(
                    "android.runtime.freeMemoryBytes must be <= totalMemoryBytes ("
                            + this.androidRuntimeFreeMemoryBytes + " > "
                            + this.androidRuntimeTotalMemoryBytes + ")");
        }

        if (this.androidRuntimeMaxMemoryBytesConfigured
                && this.androidRuntimeTotalMemoryBytesConfigured
                && this.androidRuntimeTotalMemoryBytes > this.androidRuntimeMaxMemoryBytes) {
            throw new IllegalArgumentException(
                    "android.runtime.totalMemoryBytes must be <= maxMemoryBytes ("
                            + this.androidRuntimeTotalMemoryBytes + " > "
                            + this.androidRuntimeMaxMemoryBytes + ")");
        }
    }

    /**
     * Parse a JSONObject map of nonempty keys (no NUL/CR/LF) to String or JSON null.
     */
    private static Map<String, String> parseAndroidRuntimeStringNullMap(Object raw, String path) {
        if (!(raw instanceof JSONObject)) {
            throw new IllegalArgumentException(path + " must be a JSONObject");
        }
        JSONObject obj = (JSONObject) raw;
        Map<String, String> built = new LinkedHashMap<String, String>(obj.size());
        for (String mapKey : obj.keySet()) {
            String itemPath = path + "." + mapKey;
            if (mapKey == null || mapKey.isEmpty()
                    || mapKey.indexOf('\0') >= 0
                    || mapKey.indexOf('\r') >= 0
                    || mapKey.indexOf('\n') >= 0) {
                throw new IllegalArgumentException(itemPath
                        + " key must be a non-empty String without NUL/CR/LF");
            }
            Object mapValue = obj.get(mapKey);
            if (mapValue != null && !(mapValue instanceof String)) {
                throw new IllegalArgumentException(itemPath
                        + " value must be a String or JSON null");
            }
            built.put(mapKey, mapValue == null ? null : (String) mapValue);
        }
        return Collections.unmodifiableMap(built);
    }

    /**
     * @return immutable trimmed list when key present and valid; {@code null} when key absent
     */
    private static List<String> parseAndroidBuildAbiArray(JSONObject build, String field) {
        if (build == null || !build.containsKey(field)) {
            return null;
        }
        String path = "android.build." + field;
        Object raw = build.get(field);
        if (raw == null) {
            throw new IllegalArgumentException(
                    path + " must be a non-empty JSONArray of non-empty Strings");
        }
        if (!(raw instanceof JSONArray)) {
            throw new IllegalArgumentException(
                    path + " must be a non-empty JSONArray of non-empty Strings");
        }
        JSONArray array = (JSONArray) raw;
        if (array.isEmpty()) {
            throw new IllegalArgumentException(
                    path + " must be a non-empty JSONArray of non-empty Strings");
        }
        List<String> abis = new ArrayList<String>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String itemPath = path + "[" + i + "]";
            Object item = array.get(i);
            if (!(item instanceof String)) {
                throw new IllegalArgumentException(itemPath
                        + " must be a non-empty String");
            }
            String trimmed = ((String) item).trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException(itemPath
                        + " must be a non-empty String");
            }
            abis.add(trimmed);
        }
        return Collections.unmodifiableList(abis);
    }

    private JSONObject androidProperties() {
        JSONObject android = android();
        return android == null ? null : android.getJSONObject("properties");
    }

    private JSONObject uname() {
        JSONObject linux = section("linux");
        return linux == null ? null : linux.getJSONObject("uname");
    }

    private JSONObject linuxFiles() {
        JSONObject linux = section("linux");
        return linux == null ? null : linux.getJSONObject("files");
    }

    private static String getString(JSONObject object, String key, String fallback) {
        if (object == null) {
            return fallback;
        }
        Object value = object.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int getInt(JSONObject object, String key, int fallback) {
        Integer value = getIntegerObject(object, key);
        return value == null ? fallback : value;
    }

    private static Integer getIntegerObject(JSONObject object, String key) {
        if (object == null) {
            return null;
        }
        Object value = object.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Integer.parseInt(text);
    }

    private static long getLong(JSONObject object, String key, long fallback) {
        Long value = getLongObject(object, key);
        return value == null ? fallback : value;
    }

    private static Long getLongObject(JSONObject object, String key) {
        if (object == null) {
            return null;
        }
        Object value = object.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.parseLong(text);
    }

    private static byte[] parseHex(String key, String hex) {
        String normalized = normalizeHex(hex);
        if (normalized.isEmpty() || (normalized.length() & 1) != 0) {
            throw new IllegalArgumentException("invalid hex length: " + key);
        }
        byte[] data = new byte[normalized.length() / 2];
        for (int i = 0; i < data.length; i++) {
            int high = Character.digit(normalized.charAt(i * 2), 16);
            int low = Character.digit(normalized.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("invalid hex value: " + key);
            }
            data[i] = (byte) ((high << 4) | low);
        }
        return data;
    }

    private static String normalizeHex(String hex) {
        StringBuilder builder = new StringBuilder(hex.length());
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if (Character.isWhitespace(c) || c == ':') {
                continue;
            }
            builder.append(c);
        }
        return builder.toString();
    }

    private static byte[] repeat(byte[] seed, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length=" + length);
        }
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = seed[i % seed.length];
        }
        return data;
    }
}
