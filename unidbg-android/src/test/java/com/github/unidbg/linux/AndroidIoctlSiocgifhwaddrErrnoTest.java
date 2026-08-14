package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.EmulatorBuilder;
import com.github.unidbg.arm.ARMEmulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.BaseAndroidFileIO;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.file.SocketIO;
import com.github.unidbg.linux.struct.IFReq;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.github.unidbg.unix.UnixEmulator;
import com.sun.jna.Pointer;
import org.junit.Test;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Real ARM32/ARM64 {@code ioctl} syscall entry for configured
 * {@code SocketIO} {@code SIOCGIFHWADDR} errno. Direct {@code SocketIO.ioctl}
 * already kept {@code ENODEV}/{@code EOPNOTSUPP}; the syscall handlers must
 * not overwrite those with {@code ENOTTY} when {@code network.interfaces}
 * is configured. Uses the default backend (no Unicorn2) so emulator close
 * does not hit Unicorn2 lifecycle crashes. Invokes
 * {@code hook(EXCP_SWI, swi=0)}.
 */
public class AndroidIoctlSiocgifhwaddrErrnoTest {

    /** Linux arm {@code __NR_ioctl}. */
    private static final int NR_IOCTL_ARM32 = 54;
    /** Linux aarch64 {@code __NR_ioctl}. */
    private static final int NR_IOCTL_ARM64 = 29;

    private static final int ENODEV = 19;
    private static final int IFNAMSIZ = 16;
    private static final int SOCKADDR_LEN = 16;
    private static final int IFREQ_HWADDR_BYTES = IFNAMSIZ + SOCKADDR_LEN;
    private static final byte POISON = (byte) 0x5a;

    private static final String ETH_NAME = "eth0";
    private static final String LO_NAME = "lo";
    private static final String WLAN_NAME = "wlan0";
    private static final String UNKNOWN_NAME = "rmnet0";

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

    private static final String TWO_IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"" + LO_NAME + "\",\"index\":1,\"ipv4\":\"" + LO_IPV4 + "\",\"flags\":" + LO_FLAGS
            + ",\"mac\":\"" + LO_MAC + "\"},"
            + "{\"name\":\"" + WLAN_NAME + "\",\"index\":2,\"ipv4\":\"" + WLAN_IPV4 + "\","
            + "\"broadcast\":\"192.168.1.255\",\"flags\":" + WLAN_FLAGS
            + ",\"hardwareType\":" + WLAN_HARDWARE_TYPE + "},"
            + "{\"name\":\"" + ETH_NAME + "\",\"index\":3,\"ipv4\":\"" + ETH_IPV4 + "\","
            + "\"mac\":\"" + ETH_MAC_JSON + "\",\"mtu\":" + ETH_MTU + ",\"flags\":" + ETH_FLAGS
            + ",\"hardwareType\":" + ETH_HARDWARE_TYPE + "}"
            + "]}}"
            ;

    @Test
    public void testConfiguredSuccess32() throws Exception {
        assertConfiguredSuccess(false);
    }

    @Test
    public void testConfiguredSuccess64() throws Exception {
        assertConfiguredSuccess(true);
    }

    @Test
    public void testMissingMacPreservesEopnotsupp32() throws Exception {
        assertFailureErrno(false, WLAN_NAME, UnixEmulator.EOPNOTSUPP);
    }

    @Test
    public void testMissingMacPreservesEopnotsupp64() throws Exception {
        assertFailureErrno(true, WLAN_NAME, UnixEmulator.EOPNOTSUPP);
    }

    @Test
    public void testMissingHardwareTypePreservesEopnotsupp32() throws Exception {
        assertFailureErrno(false, LO_NAME, UnixEmulator.EOPNOTSUPP);
    }

    @Test
    public void testMissingHardwareTypePreservesEopnotsupp64() throws Exception {
        assertFailureErrno(true, LO_NAME, UnixEmulator.EOPNOTSUPP);
    }

    @Test
    public void testUnknownInterfacePreservesEnodev32() throws Exception {
        assertFailureErrno(false, UNKNOWN_NAME, ENODEV);
    }

    @Test
    public void testUnknownInterfacePreservesEnodev64() throws Exception {
        assertFailureErrno(true, UNKNOWN_NAME, ENODEV);
    }

    @Test
    public void testEnottyWithoutNetworkInterfacesOrNonSocketIO32() throws Exception {
        assertHistoricalEnotty(false);
    }

    @Test
    public void testEnottyWithoutNetworkInterfacesOrNonSocketIO64() throws Exception {
        assertHistoricalEnotty(true);
    }

    private static void assertConfiguredSuccess(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        CapturingSink sink = new CapturingSink();
        AndroidEmulator emulator = null;
        TestSocketIO socket = null;
        MemoryBlock block = null;
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            socket = new TestSocketIO();
            int fd = emulator.getSyscallHandler().addFileIO(socket);
            block = emulator.getMemory().malloc(64, true);
            Pointer p = block.getPointer();
            writeIfreqName(emulator, p, ETH_NAME);
            poisonSockaddr(p);

            emulator.getMemory().setErrno(0);
            assertEquals(0, invokeIoctl(emulator, fd, AndroidFileIO.SIOCGIFHWADDR,
                    UnidbgPointer.nativeValue(p)));
            assertEquals(ETH_HARDWARE_TYPE, p.getShort(IFNAMSIZ) & 0xffff);
            assertArrayEquals(ETH_MAC_BYTES, p.getByteArray(IFNAMSIZ + 2, 6));
            assertArrayEquals(new byte[8], p.getByteArray(IFNAMSIZ + 8, 8));

            assertEquals(1, countHwaddrConfigEvents(sink));
            CapturedEvent ev = findHwaddrConfigEvent(sink);
            assertHwaddrSidecar(ev, ETH_NAME);
            assertSidecarOmitsSecrets(ev);
            assertNoSecretLeak(sink);
        } finally {
            closeAll(block, socket, emulator, sink);
        }
    }

    private static void assertFailureErrno(boolean is64Bit, String ifName, int expectedErrno)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        CapturingSink sink = new CapturingSink();
        AndroidEmulator emulator = null;
        TestSocketIO socket = null;
        MemoryBlock block = null;
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            socket = new TestSocketIO();
            int fd = emulator.getSyscallHandler().addFileIO(socket);
            block = emulator.getMemory().malloc(64, true);
            Pointer p = block.getPointer();
            writeIfreqName(emulator, p, ifName);
            poisonSockaddr(p);
            byte[] before = p.getByteArray(IFNAMSIZ, SOCKADDR_LEN);

            emulator.getMemory().setErrno(0);
            assertEquals(-1, invokeIoctl(emulator, fd, AndroidFileIO.SIOCGIFHWADDR,
                    UnidbgPointer.nativeValue(p)));
            assertEquals(expectedErrno, emulator.getMemory().getLastErrno());
            assertArrayEquals(before, p.getByteArray(IFNAMSIZ, SOCKADDR_LEN));
            assertEquals(0, countHwaddrConfigEvents(sink));
            assertNoSecretLeak(sink);
        } finally {
            closeAll(block, socket, emulator, sink);
        }
    }

    private static void assertHistoricalEnotty(boolean is64Bit) throws Exception {
        assertEnottyNoNetworkInterfaces(is64Bit);
        assertEnottyNonSocketIOWithConfig(is64Bit);
        assertEnottyOtherIoctlWithConfig(is64Bit);
    }

    /** No {@code network.interfaces}: non-SocketIO and SocketIO -1 still become ENOTTY. */
    private static void assertEnottyNoNetworkInterfaces(boolean is64Bit) throws Exception {
        CapturingSink sink = new CapturingSink();
        AndroidEmulator emulator = null;
        TestSocketIO socket = null;
        MemoryBlock block = null;
        try {
            emulator = emulatorBuilder(is64Bit).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            block = emulator.getMemory().malloc(64, true);
            Pointer p = block.getPointer();
            writeIfreqName(emulator, p, ETH_NAME);

            FailingIoctlFileIO other = new FailingIoctlFileIO();
            int otherFd = emulator.getSyscallHandler().addFileIO(other);
            emulator.getMemory().setErrno(0);
            assertEquals(-1, invokeIoctl(emulator, otherFd, AndroidFileIO.SIOCGIFHWADDR,
                    UnidbgPointer.nativeValue(p)));
            assertEquals(UnixEmulator.ENOTTY, emulator.getMemory().getLastErrno());

            socket = new HistoricalFailingSocketIO();
            int socketFd = emulator.getSyscallHandler().addFileIO(socket);
            emulator.getMemory().setErrno(0);
            assertEquals(-1, invokeIoctl(emulator, socketFd, AndroidFileIO.SIOCGIFHWADDR,
                    UnidbgPointer.nativeValue(p)));
            assertEquals(UnixEmulator.ENOTTY, emulator.getMemory().getLastErrno());

            assertEquals(0, countHwaddrConfigEvents(sink));
            assertNoSecretLeak(sink);
        } finally {
            closeAll(block, socket, emulator, sink);
        }
    }

    /** Configured {@code network.interfaces} does not preserve errno for non-SocketIO. */
    private static void assertEnottyNonSocketIOWithConfig(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        CapturingSink sink = new CapturingSink();
        AndroidEmulator emulator = null;
        MemoryBlock block = null;
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            block = emulator.getMemory().malloc(64, true);
            Pointer p = block.getPointer();
            writeIfreqName(emulator, p, ETH_NAME);

            int fd = emulator.getSyscallHandler().addFileIO(new FailingIoctlFileIO());
            emulator.getMemory().setErrno(0);
            assertEquals(-1, invokeIoctl(emulator, fd, AndroidFileIO.SIOCGIFHWADDR,
                    UnidbgPointer.nativeValue(p)));
            assertEquals(UnixEmulator.ENOTTY, emulator.getMemory().getLastErrno());
            assertEquals(0, countHwaddrConfigEvents(sink));
            assertNoSecretLeak(sink);
        } finally {
            closeAll(block, null, emulator, sink);
        }
    }

    /** Other ioctl on SocketIO still maps {@code ret == -1} to ENOTTY. */
    private static void assertEnottyOtherIoctlWithConfig(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        CapturingSink sink = new CapturingSink();
        AndroidEmulator emulator = null;
        TestSocketIO socket = null;
        MemoryBlock block = null;
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            socket = new TestSocketIO();
            int fd = emulator.getSyscallHandler().addFileIO(socket);
            block = emulator.getMemory().malloc(64, true);
            Pointer p = block.getPointer();
            writeIfreqName(emulator, p, WLAN_NAME);

            emulator.getMemory().setErrno(0);
            assertEquals(-1, invokeIoctl(emulator, fd, AndroidFileIO.SIOCGIFMTU,
                    UnidbgPointer.nativeValue(p)));
            assertEquals(UnixEmulator.ENOTTY, emulator.getMemory().getLastErrno());
            assertEquals(0, countHwaddrConfigEvents(sink));
            assertNoSecretLeak(sink);
        } finally {
            closeAll(block, socket, emulator, sink);
        }
    }

    private static int invokeIoctl(AndroidEmulator emulator, int fd, long request, long argp) {
        Backend backend = emulator.getBackend();
        int nr = emulator.is32Bit() ? NR_IOCTL_ARM32 : NR_IOCTL_ARM64;
        if (emulator.is32Bit()) {
            backend.reg_write(ArmConst.UC_ARM_REG_R0, fd);
            backend.reg_write(ArmConst.UC_ARM_REG_R1, (int) request);
            backend.reg_write(ArmConst.UC_ARM_REG_R2, (int) argp);
            backend.reg_write(ArmConst.UC_ARM_REG_R7, nr);
            backend.reg_write(ArmConst.UC_ARM_REG_R5, 0);
        } else {
            backend.reg_write(Arm64Const.UC_ARM64_REG_X0, fd);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X1, request);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X2, argp);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X8, nr);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X16, 0L);
        }
        AndroidSyscallHandler handler = (AndroidSyscallHandler) emulator.getSyscallHandler();
        handler.hook(backend, ARMEmulator.EXCP_SWI, 0, emulator);
        if (emulator.is32Bit()) {
            return backend.reg_read(ArmConst.UC_ARM_REG_R0).intValue();
        }
        return backend.reg_read(Arm64Const.UC_ARM64_REG_X0).intValue();
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
        assertNoSecretInText(String.valueOf(e.value), true);
        assertNoSecretInText(String.valueOf(e.note), true);
    }

    private static void assertNoSecretLeak(CapturingSink sink) {
        for (CapturedEvent e : sink.events) {
            // WLAN hardwareType is 1; skip it on generic events that may contain ret=-1.
            assertNoSecretInText(String.valueOf(e.value), false);
            assertNoSecretInText(String.valueOf(e.note), false);
        }
    }

    private static void assertNoSecretInText(String text, boolean checkWlanHardwareTypeOne) {
        assertFalse(text.contains("hardwareType"));
        assertFalse(text.contains(String.valueOf(ETH_HARDWARE_TYPE)));
        if (checkWlanHardwareTypeOne) {
            assertFalse(text.contains(String.valueOf(WLAN_HARDWARE_TYPE)));
        }
        assertFalse(text.contains(ETH_MAC_JSON));
        assertFalse(text.contains(ETH_MAC_CANONICAL));
        assertFalse(text.contains("aabbccddeeff"));
        assertFalse(text.contains(LO_MAC));
        assertFalse(text.contains(ETH_IPV4));
        assertFalse(text.contains(WLAN_IPV4));
        assertFalse(text.contains(LO_IPV4));
        assertFalse(text.contains(String.valueOf(ETH_FLAGS)));
        assertFalse(text.contains(String.valueOf(LO_FLAGS)));
        assertFalse(text.contains(String.valueOf(WLAN_FLAGS)));
        assertFalse(text.contains("0x1043"));
        assertFalse(text.contains("family="));
        assertFalse(text.contains("mac="));
        assertFalse(text.contains("flags="));
        assertFalse(text.contains("addr="));
    }

    private static int countHwaddrConfigEvents(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isHwaddrConfigEvent(e)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findHwaddrConfigEvent(CapturingSink sink) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isHwaddrConfigEvent(e)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isHwaddrConfigEvent(CapturedEvent e) {
        return "network_device".equals(e.kind)
                && "ioctl(SIOCGIFHWADDR)".equals(e.api)
                && "json-config".equals(e.source)
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

    private static EmulatorBuilder<AndroidEmulator> emulatorBuilder(boolean is64Bit) {
        return is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
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

    /**
     * Non-SocketIO that returns -1 after setting a distinctive errno that the
     * syscall layer must overwrite with ENOTTY outside the preserve predicate.
     */
    private static final class FailingIoctlFileIO extends BaseAndroidFileIO {
        FailingIoctlFileIO() {
            super(IOConstants.O_RDWR);
        }

        @Override
        public int ioctl(Emulator<?> emulator, long request, long argp) {
            emulator.getMemory().setErrno(ENODEV);
            return -1;
        }

        @Override
        public void close() {
        }
    }

    /**
     * SocketIO that returns -1 without taking over SIOCGIFHWADDR, used only
     * for the unconfigured historical {@code ret == -1} → ENOTTY path.
     */
    private static final class HistoricalFailingSocketIO extends TestSocketIO {
        @Override
        public int ioctl(Emulator<?> emulator, long request, long argp) {
            emulator.getMemory().setErrno(ENODEV);
            return -1;
        }
    }

    private static class TestSocketIO extends SocketIO {
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
