package com.github.unidbg.linux.android;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.github.unidbg.unix.UnixEmulator;
import com.sun.jna.Pointer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Focused ARM32/ARM64 tests for {@code libc.so!getifaddrs}/{@code freeifaddrs}
 * backed only by {@code network.interfaces}. Never enumerates host NICs.
 */
public class GetifaddrsHookTest {

    private static final String LO_NAME = "lo";
    private static final String WLAN_NAME = "wlan0";
    private static final String ETH_NAME = "eth0";

    private static final String LO_IPV4 = "127.0.0.1";
    private static final String WLAN_IPV4 = "192.168.1.100";
    private static final String WLAN_BROADCAST = "192.168.1.255";
    private static final String ETH_IPV4 = "10.0.0.2";

    private static final String LO_MAC = "00:00:00:00:00:00";
    private static final String WLAN_MAC = "02:54:52:41:43:45";

    private static final byte[] LO_IPV4_BYTES = new byte[] {127, 0, 0, 1};
    private static final byte[] WLAN_IPV4_BYTES = new byte[] {(byte) 192, (byte) 168, 1, 100};
    private static final byte[] WLAN_BROADCAST_BYTES = new byte[] {(byte) 192, (byte) 168, 1, (byte) 255};
    private static final byte[] ETH_IPV4_BYTES = new byte[] {10, 0, 0, 2};
    private static final byte[] LO_MAC_BYTES = new byte[6];
    private static final byte[] WLAN_MAC_BYTES = new byte[] {0x02, 0x54, 0x52, 0x41, 0x43, 0x45};

    private static final int LO_FLAGS = 73;
    private static final int WLAN_FLAGS = 4355;
    private static final int LO_INDEX = 1;
    private static final int WLAN_INDEX = 2;
    private static final int ETH_INDEX = 3;
    private static final int LO_HTYPE = 772;
    private static final int WLAN_HTYPE = 1;

    private static final String TWO_IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"" + LO_NAME + "\",\"index\":" + LO_INDEX + ",\"ipv4\":\"" + LO_IPV4
            + "\",\"flags\":" + LO_FLAGS + ",\"mac\":\"" + LO_MAC + "\",\"hardwareType\":" + LO_HTYPE + "},"
            + "{\"name\":\"" + WLAN_NAME + "\",\"index\":" + WLAN_INDEX + ",\"ipv4\":\"" + WLAN_IPV4
            + "\",\"broadcast\":\"" + WLAN_BROADCAST + "\",\"flags\":" + WLAN_FLAGS
            + ",\"mac\":\"" + WLAN_MAC + "\",\"hardwareType\":" + WLAN_HTYPE + "}"
            + "]}}";

    private static final String NO_FLAGS_NO_MAC_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"" + ETH_NAME + "\",\"index\":" + ETH_INDEX + ",\"ipv4\":\"" + ETH_IPV4 + "\"}"
            + "]}}";

    private static final String EMPTY_JSON = "{\"network\":{\"interfaces\":[]}}";
    private static final String ABSENT_JSON = "{\"android\":{\"packageName\":\"com.demo.app\"}}";
    private static final String ABSENT_NETWORK_JSON = "{\"network\":{\"wifi\":{\"enabled\":true}}}";

    @Test
    public void testListIpv4MacFlagsArm32() throws Exception {
        runListIpv4MacFlags(false);
    }

    @Test
    public void testListIpv4MacFlagsArm64() throws Exception {
        runListIpv4MacFlags(true);
    }

    @Test
    public void testEmptyArrayArm32() throws Exception {
        runEmptyArray(false);
    }

    @Test
    public void testEmptyArrayArm64() throws Exception {
        runEmptyArray(true);
    }

    @Test
    public void testAbsenceArm32() throws Exception {
        runAbsence(false);
    }

    @Test
    public void testAbsenceArm64() throws Exception {
        runAbsence(true);
    }

    @Test
    public void testFlagsOmittedNoMacArm32() throws Exception {
        runFlagsOmittedNoMac(false);
    }

    @Test
    public void testFlagsOmittedNoMacArm64() throws Exception {
        runFlagsOmittedNoMac(true);
    }

    @Test
    public void testFreeifaddrsLifecycleArm32() throws Exception {
        runFreeifaddrsLifecycle(false);
    }

    @Test
    public void testFreeifaddrsLifecycleArm64() throws Exception {
        runFreeifaddrsLifecycle(true);
    }

    @Test
    public void testExactMatchAndNullIfapArm32() throws Exception {
        runExactMatchAndNullIfap(false);
    }

    @Test
    public void testExactMatchAndNullIfapArm64() throws Exception {
        runExactMatchAndNullIfap(true);
    }

    @Test
    public void testIsolationAndNoRawSidecarArm32() throws Exception {
        runIsolationAndNoRawSidecar(false);
    }

    @Test
    public void testIsolationAndNoRawSidecarArm64() throws Exception {
        runIsolationAndNoRawSidecar(true);
    }

    private static void runListIpv4MacFlags(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = builder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertTrue(GetifaddrsHook.shouldRegister(emulator));
            GetifaddrsHook hook = new GetifaddrsHook(emulator);

            MemoryBlock out = emulator.getMemory().malloc(emulator.getPointerSize(), true);
            blocks.add(out);
            out.getPointer().setPointer(0, null);

            Integer rc = hook.tryGetifaddrs(emulator, out.getPointer());
            assertEquals(Integer.valueOf(0), rc);

            Pointer head = out.getPointer().getPointer(0);
            assertNotNull(head);
            long peer = UnidbgPointer.nativeValue(head);
            assertTrue(hook.isTracked(peer));
            assertEquals(1, hook.trackedCount());

            List<Walked> walked = walk(emulator, head, is64Bit);
            assertEquals(4, walked.size());

            Walked loPacket = walked.get(0);
            assertEquals(LO_NAME, loPacket.name);
            assertEquals(LO_FLAGS, loPacket.flags);
            assertEquals(GetifaddrsHook.AF_PACKET, loPacket.family);
            assertEquals(LO_INDEX, loPacket.ifindex);
            assertEquals(LO_HTYPE, loPacket.hatype);
            assertArrayEquals(LO_MAC_BYTES, loPacket.mac);
            assertNull(loPacket.ipv4);
            assertNull(loPacket.broadcast);
            assertTrue(loPacket.netmaskNull);
            assertTrue(loPacket.dataNull);

            Walked loInet = walked.get(1);
            assertEquals(LO_NAME, loInet.name);
            assertEquals(LO_FLAGS, loInet.flags);
            assertEquals(GetifaddrsHook.AF_INET, loInet.family);
            assertArrayEquals(LO_IPV4_BYTES, loInet.ipv4);
            assertNull(loInet.broadcast);
            assertTrue(loInet.netmaskNull);
            assertTrue(loInet.dataNull);
            assertEquals(loPacket.namePeer, loInet.namePeer);

            Walked wlanPacket = walked.get(2);
            assertEquals(WLAN_NAME, wlanPacket.name);
            assertEquals(WLAN_FLAGS, wlanPacket.flags);
            assertEquals(GetifaddrsHook.AF_PACKET, wlanPacket.family);
            assertEquals(WLAN_INDEX, wlanPacket.ifindex);
            assertEquals(WLAN_HTYPE, wlanPacket.hatype);
            assertArrayEquals(WLAN_MAC_BYTES, wlanPacket.mac);

            Walked wlanInet = walked.get(3);
            assertEquals(WLAN_NAME, wlanInet.name);
            assertEquals(WLAN_FLAGS, wlanInet.flags);
            assertEquals(GetifaddrsHook.AF_INET, wlanInet.family);
            assertArrayEquals(WLAN_IPV4_BYTES, wlanInet.ipv4);
            assertArrayEquals(WLAN_BROADCAST_BYTES, wlanInet.broadcast);
            assertNull(wlanInet.next);

            CapturedEvent ev = findLast(sink.events, "network_device", "getifaddrs");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("result=0,interfaces=2,entries=4", String.valueOf(ev.value));
            assertSidecarOmitsSecrets(ev);
            assertEquals(1, countApi(sink.events, "getifaddrs"));
            assertEquals(0, countApi(sink.events, "freeifaddrs"));
        } finally {
            close(emulator, sink, blocks);
        }
    }

    private static void runEmptyArray(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(EMPTY_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = builder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertTrue(GetifaddrsHook.isNetworkInterfacesConfigured(emulator));
            GetifaddrsHook hook = new GetifaddrsHook(emulator);

            MemoryBlock out = emulator.getMemory().malloc(emulator.getPointerSize(), true);
            blocks.add(out);
            MemoryBlock poison = emulator.getMemory().malloc(8, true);
            blocks.add(poison);
            out.getPointer().setPointer(0, poison.getPointer());

            assertEquals(Integer.valueOf(0), hook.tryGetifaddrs(emulator, out.getPointer()));
            assertNull(out.getPointer().getPointer(0));
            assertEquals(0, hook.trackedCount());
            assertTrue(hook.tryFreeifaddrs(null));

            CapturedEvent ev = findLast(sink.events, "network_device", "getifaddrs");
            assertNotNull(ev);
            assertEquals("result=0,interfaces=0,entries=0", String.valueOf(ev.value));
            assertEquals(0, countApi(sink.events, "freeifaddrs"));
        } finally {
            close(emulator, sink, blocks);
        }
    }

    private static void runAbsence(boolean is64Bit) throws Exception {
        for (String json : new String[] {ABSENT_JSON, ABSENT_NETWORK_JSON}) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
            AndroidEmulator emulator = null;
            CapturingSink sink = new CapturingSink();
            List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
            try {
                emulator = builder(is64Bit).setEnvironmentConfig(config).build();
                TraceEnvironmentEventSink.register(emulator, sink);
                assertFalse(GetifaddrsHook.shouldRegister(emulator));

                GetifaddrsHook hook = new GetifaddrsHook(emulator);
                MemoryBlock out = emulator.getMemory().malloc(emulator.getPointerSize(), true);
                blocks.add(out);
                MemoryBlock poison = emulator.getMemory().malloc(8, true);
                blocks.add(poison);
                out.getPointer().setPointer(0, poison.getPointer());
                Pointer before = out.getPointer().getPointer(0);

                assertNull(hook.tryGetifaddrs(emulator, out.getPointer()));
                assertEquals(UnidbgPointer.nativeValue(before),
                        UnidbgPointer.nativeValue(out.getPointer().getPointer(0)));
                assertEquals(0, hook.trackedCount());
                assertEquals(0L, hook.hook(emulator.getSvcMemory(),
                        GetifaddrsHook.LIBRARY, GetifaddrsHook.GETIFADDRS, 0x4000L));
                for (CapturedEvent e : sink.events) {
                    assertFalse("unexpected getifaddrs event",
                            "network_device".equals(e.kind) && "getifaddrs".equals(e.api));
                }
            } finally {
                close(emulator, sink, blocks);
            }
        }
    }

    private static void runFlagsOmittedNoMac(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_FLAGS_NO_MAC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = builder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            GetifaddrsHook hook = new GetifaddrsHook(emulator);
            MemoryBlock out = emulator.getMemory().malloc(emulator.getPointerSize(), true);
            blocks.add(out);

            assertEquals(Integer.valueOf(0), hook.tryGetifaddrs(emulator, out.getPointer()));
            List<Walked> walked = walk(emulator, out.getPointer().getPointer(0), is64Bit);
            assertEquals(1, walked.size());
            Walked only = walked.get(0);
            assertEquals(ETH_NAME, only.name);
            assertEquals(0, only.flags);
            assertEquals(GetifaddrsHook.AF_INET, only.family);
            assertArrayEquals(ETH_IPV4_BYTES, only.ipv4);
            assertNull(only.broadcast);
            assertNull(only.mac);
            assertTrue(only.netmaskNull);
            assertTrue(only.dataNull);

            CapturedEvent ev = findLast(sink.events, "network_device", "getifaddrs");
            assertEquals("result=0,interfaces=1,entries=1", String.valueOf(ev.value));
            assertSidecarOmitsSecrets(ev);
        } finally {
            close(emulator, sink, blocks);
        }
    }

    private static void runFreeifaddrsLifecycle(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = builder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            GetifaddrsHook hook = new GetifaddrsHook(emulator);
            MemoryBlock out = emulator.getMemory().malloc(emulator.getPointerSize(), true);
            blocks.add(out);

            assertEquals(Integer.valueOf(0), hook.tryGetifaddrs(emulator, out.getPointer()));
            Pointer head = out.getPointer().getPointer(0);
            long peer = UnidbgPointer.nativeValue(head);
            assertTrue(hook.isTracked(peer));

            assertTrue(hook.tryFreeifaddrs(head));
            assertFalse(hook.isTracked(peer));
            assertEquals(0, hook.trackedCount());
            assertEquals(0, countApi(sink.events, "freeifaddrs"));

            assertFalse(hook.tryFreeifaddrs(head));
            MemoryBlock foreign = emulator.getMemory().malloc(8, true);
            blocks.add(foreign);
            assertFalse(hook.tryFreeifaddrs(foreign.getPointer()));
            assertTrue(hook.tryFreeifaddrs(null));
        } finally {
            close(emulator, sink, blocks);
        }
    }

    private static void runExactMatchAndNullIfap(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = builder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            GetifaddrsHook hook = new GetifaddrsHook(emulator);
            long old = 0x4000L;

            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libselinux.so",
                    GetifaddrsHook.GETIFADDRS, old));
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), GetifaddrsHook.LIBRARY,
                    "sysconf", old));
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), GetifaddrsHook.LIBRARY,
                    "getifaddrs64", old));

            long getAddr = hook.hook(emulator.getSvcMemory(),
                    GetifaddrsHook.LIBRARY, GetifaddrsHook.GETIFADDRS, old);
            assertTrue(getAddr != 0L);
            long freeAddr = hook.hook(emulator.getSvcMemory(),
                    GetifaddrsHook.LIBRARY, GetifaddrsHook.FREEIFADDRS, old);
            assertTrue(freeAddr != 0L);

            emulator.getMemory().setErrno(0);
            assertEquals(Integer.valueOf(-1), hook.tryGetifaddrs(emulator, null));
            assertEquals(UnixEmulator.EFAULT, emulator.getMemory().getLastErrno());
            for (CapturedEvent e : sink.events) {
                assertFalse("getifaddrs event on null ifap",
                        "network_device".equals(e.kind) && "getifaddrs".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIsolationAndNoRawSidecar(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(ABSENT_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        List<MemoryBlock> blocksA = new ArrayList<MemoryBlock>();
        List<MemoryBlock> blocksB = new ArrayList<MemoryBlock>();
        try {
            emulatorA = builder(is64Bit).setEnvironmentConfig(configA).build();
            emulatorB = builder(is64Bit).setEnvironmentConfig(configB).build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            GetifaddrsHook hookA = new GetifaddrsHook(emulatorA);
            GetifaddrsHook hookB = new GetifaddrsHook(emulatorB);

            MemoryBlock outA = emulatorA.getMemory().malloc(emulatorA.getPointerSize(), true);
            blocksA.add(outA);
            assertEquals(Integer.valueOf(0), hookA.tryGetifaddrs(emulatorA, outA.getPointer()));
            CapturedEvent evA = findLast(sinkA.events, "network_device", "getifaddrs");
            assertNotNull(evA);
            assertSidecarOmitsSecrets(evA);

            MemoryBlock outB = emulatorB.getMemory().malloc(emulatorB.getPointerSize(), true);
            blocksB.add(outB);
            assertNull(hookB.tryGetifaddrs(emulatorB, outB.getPointer()));
            for (CapturedEvent e : sinkB.events) {
                assertFalse("leak getifaddrs to B",
                        "network_device".equals(e.kind) && "getifaddrs".equals(e.api));
            }
            Pointer headA = outA.getPointer().getPointer(0);
            assertFalse(hookB.tryFreeifaddrs(headA));
            assertTrue(hookA.isTracked(UnidbgPointer.nativeValue(headA)));
            assertTrue(hookA.tryFreeifaddrs(headA));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
            }
            freeAll(blocksA);
            freeAll(blocksB);
            if (emulatorA != null) {
                emulatorA.close();
            }
            if (emulatorB != null) {
                emulatorB.close();
            }
        }
    }

    private static AndroidEmulatorBuilder builder(boolean is64Bit) {
        return is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
    }

    private static List<Walked> walk(AndroidEmulator emulator, Pointer head, boolean is64Bit) {
        List<Walked> list = new ArrayList<Walked>();
        Pointer cur = head;
        int guard = 0;
        int offNext = GetifaddrsHook.offsetNext(is64Bit);
        int offName = GetifaddrsHook.offsetName(is64Bit);
        int offFlags = GetifaddrsHook.offsetFlags(is64Bit);
        int offAddr = GetifaddrsHook.offsetAddr(is64Bit);
        int offNetmask = GetifaddrsHook.offsetNetmask(is64Bit);
        int offIfu = GetifaddrsHook.offsetIfu(is64Bit);
        int offData = GetifaddrsHook.offsetData(is64Bit);
        while (cur != null) {
            assertTrue("cycle or too many ifaddrs", guard++ < 16);
            Walked w = new Walked();
            Pointer namePtr = cur.getPointer(offName);
            assertNotNull(namePtr);
            w.name = namePtr.getString(0);
            w.namePeer = UnidbgPointer.nativeValue(namePtr);
            w.flags = cur.getInt(offFlags) & 0xffff;
            w.netmaskNull = cur.getPointer(offNetmask) == null;
            w.dataNull = cur.getPointer(offData) == null;
            Pointer addr = cur.getPointer(offAddr);
            assertNotNull(addr);
            w.family = addr.getShort(0) & 0xffff;
            if (w.family == GetifaddrsHook.AF_INET) {
                w.ipv4 = addr.getByteArray(4, 4);
                Pointer broad = cur.getPointer(offIfu);
                if (broad != null) {
                    assertEquals(GetifaddrsHook.AF_INET, broad.getShort(0) & 0xffff);
                    w.broadcast = broad.getByteArray(4, 4);
                }
            } else if (w.family == GetifaddrsHook.AF_PACKET) {
                w.ifindex = addr.getInt(4);
                w.hatype = addr.getShort(8) & 0xffff;
                assertEquals(0, addr.getByte(10));
                assertEquals(6, addr.getByte(11) & 0xff);
                w.mac = addr.getByteArray(12, 6);
                assertNull(cur.getPointer(offIfu));
            }
            w.next = cur.getPointer(offNext);
            list.add(w);
            cur = w.next;
        }
        return list;
    }

    private static void assertSidecarOmitsSecrets(CapturedEvent ev) {
        String value = String.valueOf(ev.value);
        String note = ev.note == null ? "" : ev.note;
        String blob = value + note;
        assertFalse(blob.contains(LO_IPV4));
        assertFalse(blob.contains(WLAN_IPV4));
        assertFalse(blob.contains(WLAN_BROADCAST));
        assertFalse(blob.contains(ETH_IPV4));
        assertFalse(blob.contains(WLAN_MAC));
        assertFalse(blob.contains(LO_MAC));
        assertFalse(blob.toLowerCase().contains("02:54:52:41:43:45"));
        assertFalse(blob.contains("192.168"));
        assertFalse(blob.contains("10.0.0"));
    }

    private static void close(AndroidEmulator emulator, CapturingSink sink, List<MemoryBlock> blocks)
            throws Exception {
        if (emulator != null) {
            TraceEnvironmentEventSink.unregister(emulator, sink);
        }
        freeAll(blocks);
        if (emulator != null) {
            emulator.close();
        }
    }

    private static void freeAll(List<MemoryBlock> blocks) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            try {
                blocks.get(i).free();
            } catch (Throwable ignored) {
                // best-effort
            }
        }
        blocks.clear();
    }

    private static CapturedEvent findLast(List<CapturedEvent> events, String kind, String api) {
        CapturedEvent found = null;
        for (CapturedEvent e : events) {
            if (kind.equals(e.kind) && api.equals(e.api)) {
                found = e;
            }
        }
        return found;
    }

    private static int countApi(List<CapturedEvent> events, String api) {
        int n = 0;
        for (CapturedEvent e : events) {
            if (api.equals(e.api)) {
                n++;
            }
        }
        return n;
    }

    private static final class Walked {
        String name;
        long namePeer;
        int flags;
        int family;
        int ifindex;
        int hatype;
        byte[] mac;
        byte[] ipv4;
        byte[] broadcast;
        boolean netmaskNull;
        boolean dataNull;
        Pointer next;
    }

    private static final class CapturedEvent {
        final String kind;
        final String api;
        final Object value;
        final String source;
        final String note;

        CapturedEvent(String kind, String api, Object value, String source, String note) {
            this.kind = kind;
            this.api = api;
            this.value = value;
            this.source = source;
            this.note = note;
        }
    }

    private static final class CapturingSink implements TraceEnvironmentEventSink {
        final List<CapturedEvent> events = new ArrayList<CapturedEvent>();

        @Override
        public void emitEnvironmentEvent(String kind, String api, Object value, String source,
                                         String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}
