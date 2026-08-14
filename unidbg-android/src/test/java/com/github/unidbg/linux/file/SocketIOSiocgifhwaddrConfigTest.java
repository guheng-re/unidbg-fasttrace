package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.EmulatorBuilder;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.struct.IFReq;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.github.unidbg.unix.UnixEmulator;
import com.sun.jna.Pointer;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * SocketIO wiring for {@code ioctl(SIOCGIFHWADDR)} from explicit JSON
 * {@code network.interfaces[].mac} and {@code hardwareType} only.
 * Uses the default backend (no Unicorn2) so emulator close does not hit Unicorn2
 * lifecycle crashes. Never infers MAC or hardwareType from name, flags, operState,
 * or carrier, and never queries host {@code NetworkInterface}.
 */
public class SocketIOSiocgifhwaddrConfigTest {

    private static final int IFNAMSIZ = 16;
    private static final int SOCKADDR_LEN = 16;
    private static final int IFREQ_HWADDR_BYTES = IFNAMSIZ + SOCKADDR_LEN;
    private static final byte POISON = (byte) 0x5a;

    private static final String ETH_NAME = "eth0";
    private static final String LO_NAME = "lo";
    private static final String WLAN_NAME = "wlan0";
    private static final String UNKNOWN_NAME = "rmnet0";
    private static final String MISMATCH_NAME = "ETH0";

    private static final String ETH_MAC_JSON = "AA:BB:CC:DD:EE:FF";
    private static final String ETH_MAC_CANONICAL = "aa:bb:cc:dd:ee:ff";
    private static final byte[] ETH_MAC_BYTES = new byte[] {
            (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff
    };
    private static final String LO_MAC = "00:00:00:00:00:00";

    private static final int ETH_HARDWARE_TYPE = 772;
    private static final int WLAN_HARDWARE_TYPE = 1;

    private static final int LO_FLAGS = 73;
    private static final int ETH_FLAGS = 4163;
    private static final int WLAN_FLAGS = 4355;
    private static final int ETH_MTU = 1500;

    private static final String LO_IPV4 = "127.0.0.1";
    private static final String WLAN_IPV4 = "192.168.1.100";
    private static final String ETH_IPV4 = "10.0.0.2";

    private static final String LO_OPERSTATE = "unknown";
    private static final String ETH_OPERSTATE = "up";

    private static final String TWO_IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"" + LO_NAME + "\",\"index\":1,\"ipv4\":\"" + LO_IPV4 + "\",\"flags\":" + LO_FLAGS
            + ",\"mac\":\"" + LO_MAC + "\",\"operState\":\"" + LO_OPERSTATE + "\",\"carrier\":false},"
            + "{\"name\":\"" + WLAN_NAME + "\",\"index\":2,\"ipv4\":\"" + WLAN_IPV4 + "\","
            + "\"broadcast\":\"192.168.1.255\",\"flags\":" + WLAN_FLAGS
            + ",\"hardwareType\":" + WLAN_HARDWARE_TYPE + ",\"operState\":\"" + ETH_OPERSTATE + "\",\"carrier\":true},"
            + "{\"name\":\"" + ETH_NAME + "\",\"index\":3,\"ipv4\":\"" + ETH_IPV4 + "\","
            + "\"mac\":\"" + ETH_MAC_JSON + "\",\"mtu\":" + ETH_MTU + ",\"flags\":" + ETH_FLAGS
            + ",\"operState\":\"" + ETH_OPERSTATE + "\",\"carrier\":false"
            + ",\"hardwareType\":" + ETH_HARDWARE_TYPE + "}"
            + "]}}"
            ;

    @Test
    public void testSiocgifhwaddrFromConfig32() throws Exception {
        assertEth0IfreqAndSidecar(false);
    }

    @Test
    public void testSiocgifhwaddrFromConfig64() throws Exception {
        assertEth0IfreqAndSidecar(true);
    }

    @Test
    public void testMissingMacDoesNotTakeOver() throws Exception {
        assertNotTakenOver(WLAN_NAME, UnixEmulator.EOPNOTSUPP);
    }

    @Test
    public void testMissingHardwareTypeDoesNotTakeOver() throws Exception {
        assertNotTakenOver(LO_NAME, UnixEmulator.EOPNOTSUPP);
    }

    @Test
    public void testUnknownInterfaceDoesNotTakeOver() throws Exception {
        assertNotTakenOver(UNKNOWN_NAME, 19);
    }

    @Test
    public void testNameMismatchDoesNotTakeOver() throws Exception {
        assertNotTakenOver(MISMATCH_NAME, 19);
    }

    private static EmulatorBuilder<AndroidEmulator> emulatorBuilder(boolean is64Bit) {
        return is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
    }

    private static void assertEth0IfreqAndSidecar(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        TestSocketIO socket = null;
        MemoryBlock block = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(is64Bit)
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            socket = new TestSocketIO();
            block = emulator.getMemory().malloc(64, true);
            Pointer p = block.getPointer();
            writeIfreqName(emulator, p, ETH_NAME);
            poisonSockaddr(p);

            assertEquals(0, socket.ioctl(emulator, AndroidFileIO.SIOCGIFHWADDR,
                    UnidbgPointer.nativeValue(p)));
            assertEquals(ETH_HARDWARE_TYPE, p.getShort(IFNAMSIZ) & 0xffff);
            assertArrayEquals(ETH_MAC_BYTES, p.getByteArray(IFNAMSIZ + 2, 6));
            assertArrayEquals(new byte[8], p.getByteArray(IFNAMSIZ + 8, 8));

            assertEquals(1, countHwaddrSidecars(sink));
            CapturedEvent ev = findHwaddrSidecar(sink);
            assertHwaddrSidecar(ev, ETH_NAME);
            assertSidecarOmitsSecrets(ev);
        } finally {
            closeAll(block, socket, emulator, sink);
        }
    }

    private static void assertNotTakenOver(String ifName, int expectedErrno) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        TestSocketIO socket = null;
        MemoryBlock block = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true)
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            socket = new TestSocketIO();
            block = emulator.getMemory().malloc(64, true);
            Pointer p = block.getPointer();
            writeIfreqName(emulator, p, ifName);
            poisonSockaddr(p);
            byte[] before = p.getByteArray(IFNAMSIZ, SOCKADDR_LEN);

            emulator.getMemory().setErrno(0);
            assertEquals(-1, socket.ioctl(emulator, AndroidFileIO.SIOCGIFHWADDR,
                    UnidbgPointer.nativeValue(p)));
            assertEquals(expectedErrno, emulator.getMemory().getLastErrno());
            assertArrayEquals(before, p.getByteArray(IFNAMSIZ, SOCKADDR_LEN));
            assertEquals(0, countHwaddrSidecars(sink));
        } finally {
            closeAll(block, socket, emulator, sink);
        }
    }

    private static void writeIfreqName(AndroidEmulator emulator, Pointer p, String name) {
        IFReq req = IFReq.createIFReq(emulator, p);
        req.setName(name);
        req.pack();
    }

    private static void poisonSockaddr(Pointer p) {
        byte[] poison = new byte[SOCKADDR_LEN];
        for (int i = 0; i < poison.length; i++) {
            poison[i] = POISON;
        }
        p.write(IFNAMSIZ, poison, 0, poison.length);
    }

    private static void assertHwaddrSidecar(CapturedEvent e, String name) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("ioctl(SIOCGIFHWADDR)", e.api);
        assertEquals("json-config", e.source);
        assertEquals("name=" + name + ",format=ifreq-hwaddr,bytes=" + IFREQ_HWADDR_BYTES,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains("读取"));
    }

    private static void assertSidecarOmitsSecrets(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains("hardwareType"));
        assertFalse(note.contains("hardwareType"));
        assertFalse(value.contains(String.valueOf(ETH_HARDWARE_TYPE)));
        assertFalse(note.contains(String.valueOf(ETH_HARDWARE_TYPE)));
        assertFalse(value.contains(String.valueOf(WLAN_HARDWARE_TYPE)));
        assertFalse(note.contains(String.valueOf(WLAN_HARDWARE_TYPE)));
        assertFalse(value.contains(ETH_MAC_JSON));
        assertFalse(value.contains(ETH_MAC_CANONICAL));
        assertFalse(value.contains("aabbccddeeff"));
        assertFalse(note.contains(ETH_MAC_JSON));
        assertFalse(note.contains(ETH_MAC_CANONICAL));
        assertFalse(note.contains("aabbccddeeff"));
        assertFalse(value.contains(LO_MAC));
        assertFalse(note.contains(LO_MAC));
        assertFalse(value.contains(ETH_IPV4));
        assertFalse(value.contains(WLAN_IPV4));
        assertFalse(value.contains(LO_IPV4));
        assertFalse(note.contains(ETH_IPV4));
        assertFalse(note.contains(WLAN_IPV4));
        assertFalse(note.contains(LO_IPV4));
        assertFalse(value.contains(String.valueOf(ETH_FLAGS)));
        assertFalse(value.contains(String.valueOf(LO_FLAGS)));
        assertFalse(value.contains(String.valueOf(WLAN_FLAGS)));
        assertFalse(value.contains("0x1043"));
        assertFalse(note.contains(String.valueOf(ETH_FLAGS)));
        assertFalse(note.contains(String.valueOf(LO_FLAGS)));
        assertFalse(note.contains(String.valueOf(WLAN_FLAGS)));
        assertFalse(note.contains("0x1043"));
        assertFalse(value.contains("operState"));
        assertFalse(note.contains("operState"));
        assertFalse(value.contains(ETH_OPERSTATE));
        assertFalse(note.contains(ETH_OPERSTATE));
        assertFalse(value.contains("carrier"));
        assertFalse(note.contains("carrier"));
        assertFalse(value.contains(String.valueOf(ETH_MTU)));
        assertFalse(note.contains(String.valueOf(ETH_MTU)));
        assertFalse(value.contains("family="));
        assertFalse(value.contains("mac="));
        assertFalse(value.contains("flags="));
        assertFalse(value.contains("NetworkInterface"));
        assertFalse(note.contains("NetworkInterface"));
        assertFalse(value.contains("format=address"));
        assertFalse(value.contains("format=type"));
    }

    private static int countHwaddrSidecars(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isHwaddrSidecar(e)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findHwaddrSidecar(CapturingSink sink) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isHwaddrSidecar(e)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isHwaddrSidecar(CapturedEvent e) {
        return "network_device".equals(e.kind)
                && "ioctl(SIOCGIFHWADDR)".equals(e.api)
                && String.valueOf(e.value).contains("format=ifreq-hwaddr");
    }

    /**
     * Close emulator last; never swallow {@link AndroidEmulator#close()} failures.
     */
    private static void closeAll(MemoryBlock block, TestSocketIO socket,
                                 AndroidEmulator emulator, CapturingSink sink) throws Exception {
        try {
            if (block != null) {
                block.free();
            }
        } finally {
            try {
                if (socket != null) {
                    socket.close();
                }
            } finally {
                if (emulator != null) {
                    try {
                        TraceEnvironmentEventSink.unregister(emulator, sink);
                    } finally {
                        emulator.close();
                    }
                }
            }
        }
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
        public void emitEnvironmentEvent(String kind, String api, Object value, String source, String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }

    private static final class TestSocketIO extends SocketIO {
        @Override
        protected int getTcpNoDelay() {
            return 0;
        }

        @Override
        protected void setTcpNoDelay(int tcpNoDelay) {
        }

        @Override
        protected void setReuseAddress(int reuseAddress) {
        }

        @Override
        protected void setKeepAlive(int keepAlive) {
        }

        @Override
        protected void setSendBufferSize(int size) {
        }

        @Override
        protected void setReceiveBufferSize(int size) {
        }

        @Override
        protected InetSocketAddress getLocalSocketAddress() {
            return null;
        }

        @Override
        protected int connect_ipv6(Pointer addr, int addrlen) {
            return -1;
        }

        @Override
        protected int connect_ipv4(Pointer addr, int addrlen) {
            return -1;
        }

        @Override
        public void close() {
        }

        @Override
        public int write(byte[] data) {
            return data == null ? 0 : data.length;
        }

        @Override
        public int read(com.github.unidbg.arm.backend.Backend backend, Pointer buffer, int count) {
            return 0;
        }
    }
}
