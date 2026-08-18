package com.github.unidbg.linux.android;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.Arm64Hook;
import com.github.unidbg.arm.ArmHook;
import com.github.unidbg.arm.HookStatus;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.hook.HookListener;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.memory.SvcMemory;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.EnvAccessProbe;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.github.unidbg.unix.UnixEmulator;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native Bionic {@code libc.so} {@code getifaddrs}/{@code freeifaddrs} backed by
 * {@code TraceEnvironmentConfig.network.interfaces} and, when present,
 * {@code network.ipv6Addresses} on the same interface name.
 * <ul>
 *   <li>Node present (including {@code []}) — intercept; never enumerate host NICs</li>
 *   <li>Node absent — do not intercept (guest libc / netlink keeps the old path)</li>
 *   <li>IPv4, optional MAC ({@code AF_PACKET}), optional flags / broadcast / hardwareType</li>
 *   <li>Optional {@code AF_INET6} from {@code network.ipv6Addresses} (same interface name)</li>
 *   <li>ARM32 {@code sizeof(ifaddrs)=28}; ARM64 {@code sizeof(ifaddrs)=56}</li>
 * </ul>
 * Does not emit netmask, {@code ifa_data}, or netlink snapshots.
 */
public class GetifaddrsHook implements HookListener {

    private static final Logger log = LoggerFactory.getLogger(GetifaddrsHook.class);

    public static final String LIBRARY = "libc.so";
    public static final String GETIFADDRS = "getifaddrs";
    public static final String FREEIFADDRS = "freeifaddrs";

    /** Linux {@code AF_INET}. */
    public static final int AF_INET = 2;
    /** Linux {@code AF_INET6}. */
    public static final int AF_INET6 = 10;
    /** Linux {@code AF_PACKET} (not BSD {@code AF_LINK}). */
    public static final int AF_PACKET = 17;

    public static final int SOCKADDR_IN_SIZE = 16;
    public static final int SOCKADDR_IN6_SIZE = 28;
    public static final int SOCKADDR_LL_SIZE = 20;

    private static final int IFADDRS_SIZE_32 = 28;
    private static final int IFADDRS_SIZE_64 = 56;

    private final Emulator<?> emulator;
    /** Head peer of each successful getifaddrs arena allocated by this instance. */
    private final Map<Long, MemoryBlock> trackedArenas = new ConcurrentHashMap<Long, MemoryBlock>();

    public GetifaddrsHook(Emulator<?> emulator) {
        this.emulator = emulator;
    }

    /** Register when {@code network.interfaces} is present, including an explicit empty array. */
    public static boolean shouldRegister(Emulator<?> emulator) {
        return isNetworkInterfacesConfigured(emulator);
    }

    public static boolean isNetworkInterfacesConfigured(Emulator<?> emulator) {
        if (emulator == null) {
            return false;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config != null && config.isNetworkInterfacesConfigured();
    }

    public static int ifaddrsSize(boolean is64Bit) {
        return is64Bit ? IFADDRS_SIZE_64 : IFADDRS_SIZE_32;
    }

    public static int offsetNext(boolean is64Bit) {
        return 0;
    }

    public static int offsetName(boolean is64Bit) {
        return is64Bit ? 8 : 4;
    }

    public static int offsetFlags(boolean is64Bit) {
        return is64Bit ? 16 : 8;
    }

    public static int offsetAddr(boolean is64Bit) {
        return is64Bit ? 24 : 12;
    }

    public static int offsetNetmask(boolean is64Bit) {
        return is64Bit ? 32 : 16;
    }

    public static int offsetIfu(boolean is64Bit) {
        return is64Bit ? 40 : 20;
    }

    public static int offsetData(boolean is64Bit) {
        return is64Bit ? 48 : 24;
    }

    /**
     * {@code getifaddrs}: when {@code network.interfaces} is configured, write a Bionic
     * {@code ifaddrs} list (or {@code NULL} for {@code []}) and return {@code 0}.
     * {@code ifap == null} returns {@code -1}/{@code EFAULT}. Returns {@code null} when
     * the node is absent (caller keeps the prior path; no host fallback).
     */
    public Integer tryGetifaddrs(Emulator<?> emulator, Pointer ifap) {
        if (emulator == null || !isNetworkInterfacesConfigured(emulator)) {
            return null;
        }
        if (ifap == null) {
            emulator.getMemory().setErrno(UnixEmulator.EFAULT);
            return Integer.valueOf(-1);
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        List<TraceEnvironmentConfig.NetworkInterfaceConfig> interfaces =
                config.getNetworkInterfaces();
        List<PlannedEntry> planned = planEntries(config, interfaces);
        if (planned.isEmpty()) {
            ifap.setPointer(0, null);
            emitGetifaddrsSidecar(emulator, 0, 0);
            return Integer.valueOf(0);
        }
        MemoryBlock arena = writeArena(emulator, planned);
        UnidbgPointer head = arena.getPointer();
        ifap.setPointer(0, head);
        long peer = UnidbgPointer.nativeValue(head);
        trackedArenas.put(peer, arena);
        emitGetifaddrsSidecar(emulator, interfaces.size(), planned.size());
        if (log.isDebugEnabled()) {
            log.debug("getifaddrs interfaces={}, entries={}, peer=0x{}",
                    interfaces.size(), planned.size(), Long.toHexString(peer));
        }
        return Integer.valueOf(0);
    }

    /**
     * {@code freeifaddrs}: frees only arenas created by {@link #tryGetifaddrs} on this
     * instance. {@code NULL} is a no-op when this hook is active. Returns {@code true}
     * if handled (do not chain); {@code false} to keep the original symbol.
     * Emits no sidecar.
     */
    public boolean tryFreeifaddrs(Pointer list) {
        if (list == null) {
            return isNetworkInterfacesConfigured(emulator);
        }
        long peer = UnidbgPointer.nativeValue(list);
        if (peer == 0L) {
            return isNetworkInterfacesConfigured(emulator);
        }
        MemoryBlock arena = trackedArenas.remove(peer);
        if (arena == null) {
            return false;
        }
        arena.free();
        if (log.isDebugEnabled()) {
            log.debug("freeifaddrs peer=0x{}", Long.toHexString(peer));
        }
        return true;
    }

    public boolean isTracked(long peer) {
        return trackedArenas.containsKey(peer);
    }

    public int trackedCount() {
        return trackedArenas.size();
    }

    @Override
    public long hook(SvcMemory svcMemory, String libraryName, String symbolName, final long old) {
        if (!LIBRARY.equals(libraryName)) {
            return 0;
        }
        if (!isNetworkInterfacesConfigured(emulator) && !EnvAccessProbe.isEnabled()) {
            return 0;
        }
        if (GETIFADDRS.equals(symbolName)) {
            log.debug("Hook {}!{}", libraryName, symbolName);
            if (emulator.is64Bit()) {
                return svcMemory.registerSvc(new Arm64Hook() {
                    @Override
                    protected HookStatus hook(Emulator<?> emulator) {
                        return handleGetifaddrs(emulator, old);
                    }
                }).peer;
            }
            return svcMemory.registerSvc(new ArmHook() {
                @Override
                protected HookStatus hook(Emulator<?> emulator) {
                    return handleGetifaddrs(emulator, old);
                }
            }).peer;
        }
        if (FREEIFADDRS.equals(symbolName)) {
            log.debug("Hook {}!{}", libraryName, symbolName);
            if (emulator.is64Bit()) {
                return svcMemory.registerSvc(new Arm64Hook() {
                    @Override
                    protected HookStatus hook(Emulator<?> emulator) {
                        return handleFreeifaddrs(emulator, old);
                    }
                }).peer;
            }
            return svcMemory.registerSvc(new ArmHook() {
                @Override
                protected HookStatus hook(Emulator<?> emulator) {
                    return handleFreeifaddrs(emulator, old);
                }
            }).peer;
        }
        return 0;
    }

    private HookStatus handleGetifaddrs(Emulator<?> emulator, long old) {
        RegisterContext context = emulator.getContext();
        Pointer ifap = context.getPointerArg(0);
        Integer result = tryGetifaddrs(emulator, ifap);
        if (result != null) {
            return HookStatus.LR(emulator, result.intValue() & 0xffffffffL);
        }
        EnvAccessProbe.miss(emulator, GETIFADDRS, "ifap=" + (ifap == null ? "null" : "set"),
                "目标读取未配置的网卡列表");
        return HookStatus.RET(emulator, old);
    }

    private HookStatus handleFreeifaddrs(Emulator<?> emulator, long old) {
        RegisterContext context = emulator.getContext();
        Pointer list = context.getPointerArg(0);
        if (tryFreeifaddrs(list)) {
            return HookStatus.LR(emulator, 0);
        }
        return HookStatus.RET(emulator, old);
    }

    private static void emitGetifaddrsSidecar(Emulator<?> emulator, int interfaces, int entries) {
        TraceEnvironmentEventSink.emit(emulator, "network_device",
                GETIFADDRS,
                "result=0,interfaces=" + interfaces + ",entries=" + entries,
                "json-config",
                "读取配置的网卡地址列表（native getifaddrs）");
    }

    private static List<PlannedEntry> planEntries(
            TraceEnvironmentConfig config,
            List<TraceEnvironmentConfig.NetworkInterfaceConfig> interfaces) {
        List<PlannedEntry> planned = new ArrayList<PlannedEntry>();
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : interfaces) {
            int flags = iface.getFlags() == null ? 0 : iface.getFlags().intValue();
            String mac = iface.getMac();
            if (mac != null) {
                planned.add(PlannedEntry.packet(iface.getName(), flags, iface.getIndex(),
                        parseMacBytes(mac), iface.isHardwareTypeConfigured()
                                ? iface.getHardwareType() : null));
            }
            planned.add(PlannedEntry.inet(iface.getName(), flags, iface.getIndex(),
                    parseIpv4Bytes(iface.getIpv4()),
                    iface.getBroadcast() == null ? null : parseIpv4Bytes(iface.getBroadcast())));
            if (config != null && config.isNetworkIpv6AddressesConfigured()) {
                List<TraceEnvironmentConfig.NetworkIpv6AddressConfig> ipv6 =
                        config.getNetworkIpv6Addresses();
                for (int i = 0; i < ipv6.size(); i++) {
                    TraceEnvironmentConfig.NetworkIpv6AddressConfig addr = ipv6.get(i);
                    if (iface.getName().equals(addr.getInterfaceName())) {
                        planned.add(PlannedEntry.inet6(iface.getName(), flags, iface.getIndex(),
                                parseIpv6Bytes(addr.getAddressHex()), iface.getIndex()));
                    }
                }
            }
        }
        return planned;
    }

    private MemoryBlock writeArena(Emulator<?> emulator, List<PlannedEntry> planned) {
        boolean is64 = emulator.is64Bit();
        int ifaddrsSize = ifaddrsSize(is64);
        int n = planned.size();

        LinkedHashMap<String, byte[]> names = new LinkedHashMap<String, byte[]>();
        for (PlannedEntry entry : planned) {
            if (!names.containsKey(entry.name)) {
                names.put(entry.name, entry.name.getBytes(StandardCharsets.UTF_8));
            }
        }

        int namesBytes = 0;
        for (byte[] raw : names.values()) {
            namesBytes += raw.length + 1;
        }
        int sockaddrBytes = 0;
        for (PlannedEntry entry : planned) {
            if (entry.packet) {
                sockaddrBytes += SOCKADDR_LL_SIZE;
            } else if (entry.ipv6 != null) {
                sockaddrBytes += SOCKADDR_IN6_SIZE;
            } else {
                sockaddrBytes += SOCKADDR_IN_SIZE;
                if (entry.broadcast != null) {
                    sockaddrBytes += SOCKADDR_IN_SIZE;
                }
            }
        }

        int namesStart = align4(n * ifaddrsSize);
        int sockStart = align4(namesStart + namesBytes);
        int total = sockStart + sockaddrBytes;
        MemoryBlock arena = emulator.getMemory().malloc(total, true);
        UnidbgPointer base = arena.getPointer();

        Map<String, UnidbgPointer> namePtrs = new LinkedHashMap<String, UnidbgPointer>();
        int nameCursor = namesStart;
        for (Map.Entry<String, byte[]> e : names.entrySet()) {
            byte[] raw = e.getValue();
            UnidbgPointer namePtr = base.share(nameCursor, raw.length + 1L);
            namePtr.write(0, raw, 0, raw.length);
            namePtr.setByte(raw.length, (byte) 0);
            namePtrs.put(e.getKey(), namePtr);
            nameCursor += raw.length + 1;
        }

        int sockCursor = sockStart;
        int offNext = offsetNext(is64);
        int offName = offsetName(is64);
        int offFlags = offsetFlags(is64);
        int offAddr = offsetAddr(is64);
        int offNetmask = offsetNetmask(is64);
        int offIfu = offsetIfu(is64);
        int offData = offsetData(is64);

        for (int i = 0; i < n; i++) {
            PlannedEntry entry = planned.get(i);
            UnidbgPointer node = base.share((long) i * ifaddrsSize, ifaddrsSize);
            if (i + 1 < n) {
                node.setPointer(offNext, base.share((long) (i + 1) * ifaddrsSize, ifaddrsSize));
            } else {
                node.setPointer(offNext, null);
            }
            node.setPointer(offName, namePtrs.get(entry.name));
            node.setInt(offFlags, entry.flags);
            node.setPointer(offNetmask, null);
            node.setPointer(offData, null);

            if (entry.packet) {
                UnidbgPointer ll = base.share(sockCursor, SOCKADDR_LL_SIZE);
                writeSockaddrLl(ll, entry);
                node.setPointer(offAddr, ll);
                node.setPointer(offIfu, null);
                sockCursor += SOCKADDR_LL_SIZE;
            } else if (entry.ipv6 != null) {
                UnidbgPointer in6 = base.share(sockCursor, SOCKADDR_IN6_SIZE);
                writeSockaddrIn6(in6, entry.ipv6, entry.scopeId);
                node.setPointer(offAddr, in6);
                node.setPointer(offIfu, null);
                sockCursor += SOCKADDR_IN6_SIZE;
            } else {
                UnidbgPointer in = base.share(sockCursor, SOCKADDR_IN_SIZE);
                writeSockaddrIn(in, entry.ipv4);
                node.setPointer(offAddr, in);
                sockCursor += SOCKADDR_IN_SIZE;
                if (entry.broadcast != null) {
                    UnidbgPointer broad = base.share(sockCursor, SOCKADDR_IN_SIZE);
                    writeSockaddrIn(broad, entry.broadcast);
                    node.setPointer(offIfu, broad);
                    sockCursor += SOCKADDR_IN_SIZE;
                } else {
                    node.setPointer(offIfu, null);
                }
            }
        }
        return arena;
    }

    private static void writeSockaddrIn(Pointer p, byte[] ipv4) {
        p.setShort(0, (short) AF_INET);
        p.setShort(2, (short) 0);
        p.write(4, ipv4, 0, 4);
        p.write(8, new byte[8], 0, 8);
    }

    private static void writeSockaddrIn6(Pointer p, byte[] ipv6, int scopeId) {
        p.setShort(0, (short) AF_INET6);
        p.setShort(2, (short) 0);
        p.setInt(4, 0);
        p.write(8, ipv6, 0, 16);
        p.setInt(24, scopeId);
    }

    private static void writeSockaddrLl(Pointer p, PlannedEntry entry) {
        p.setShort(0, (short) AF_PACKET);
        p.setShort(2, (short) 0);
        p.setInt(4, entry.index);
        p.setShort(8, (short) (entry.hardwareType == null ? 0 : entry.hardwareType.intValue()));
        p.setByte(10, (byte) 0);
        p.setByte(11, (byte) 6);
        byte[] addr = new byte[8];
        System.arraycopy(entry.mac, 0, addr, 0, 6);
        p.write(12, addr, 0, 8);
    }

    private static int align4(int value) {
        return (value + 3) & ~3;
    }

    /** No DNS: dotted-decimal to 4 network-order bytes. */
    static byte[] parseIpv4Bytes(String text) {
        try {
            String[] parts = text.split("\\.", -1);
            if (parts.length != 4) {
                throw new IllegalStateException("invalid configured IPv4: " + text);
            }
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                int v = Integer.parseInt(parts[i]);
                if (v < 0 || v > 255) {
                    throw new IllegalStateException("invalid configured IPv4: " + text);
                }
                bytes[i] = (byte) v;
            }
            return bytes;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("invalid configured IPv4: " + text, e);
        }
    }

    static byte[] parseIpv6Bytes(String addressHex) {
        if (addressHex == null || addressHex.length() != 32) {
            throw new IllegalStateException("invalid configured IPv6 hex: " + addressHex);
        }
        byte[] bytes = new byte[16];
        for (int i = 0; i < 16; i++) {
            int high = Character.digit(addressHex.charAt(i * 2), 16);
            int low = Character.digit(addressHex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalStateException("invalid configured IPv6 hex: " + addressHex);
            }
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }

    static byte[] parseMacBytes(String mac) {
        String[] parts = mac.split(":", -1);
        if (parts.length != 6) {
            throw new IllegalStateException("invalid configured MAC: " + mac);
        }
        byte[] bytes = new byte[6];
        for (int i = 0; i < 6; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return bytes;
    }

    private static final class PlannedEntry {
        final String name;
        final int flags;
        final int index;
        final boolean packet;
        final byte[] mac;
        final Integer hardwareType;
        final byte[] ipv4;
        final byte[] broadcast;
        final byte[] ipv6;
        final int scopeId;

        private PlannedEntry(String name, int flags, int index, boolean packet,
                             byte[] mac, Integer hardwareType, byte[] ipv4, byte[] broadcast,
                             byte[] ipv6, int scopeId) {
            this.name = name;
            this.flags = flags;
            this.index = index;
            this.packet = packet;
            this.mac = mac;
            this.hardwareType = hardwareType;
            this.ipv4 = ipv4;
            this.broadcast = broadcast;
            this.ipv6 = ipv6;
            this.scopeId = scopeId;
        }

        static PlannedEntry packet(String name, int flags, int index, byte[] mac,
                                   Integer hardwareType) {
            return new PlannedEntry(name, flags, index, true, mac, hardwareType, null, null, null, 0);
        }

        static PlannedEntry inet(String name, int flags, int index, byte[] ipv4, byte[] broadcast) {
            return new PlannedEntry(name, flags, index, false, null, null, ipv4, broadcast, null, 0);
        }

        static PlannedEntry inet6(String name, int flags, int index, byte[] ipv6, int scopeId) {
            return new PlannedEntry(name, flags, index, false, null, null, null, null, ipv6, scopeId);
        }
    }
}
