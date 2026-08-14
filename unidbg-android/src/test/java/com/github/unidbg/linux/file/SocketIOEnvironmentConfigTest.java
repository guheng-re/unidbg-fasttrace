package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.struct.IFReq;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.unix.UnixEmulator;
import com.sun.jna.Pointer;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SocketIOEnvironmentConfigTest {

    private static final int ARPHRD_ETHER = 1;
    private static final int ARPHRD_LOOPBACK = 772;

    private static final String TWO_IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"flags\":73,"
            + "\"mac\":\"00:00:00:00:00:00\",\"mtu\":65536,\"hardwareType\":772},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\",\"broadcast\":\"192.168.1.255\","
            + "\"flags\":4355,\"mac\":\"02:00:00:00:00:01\",\"mtu\":1500,\"hardwareType\":1}"
            + "]}"
            + "}";

    private static final String MISSING_MAC_MTU_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\"}"
            + "]}"
            + "}";

    private static final String EMPTY_IFACES_JSON = "{\"network\":{\"interfaces\":[]}}";

    @Test
    public void testConfiguredNetworkInterfaces32() throws Exception {
        assertConfiguredInterfaces(false);
    }

    @Test
    public void testConfiguredNetworkInterfaces64() throws Exception {
        assertConfiguredInterfaces(true);
    }

    @Test
    public void testEmptyConfiguredNetworkInterfaces() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(EMPTY_IFACES_JSON);
        assertTrue(config.isNetworkInterfacesConfigured());
        assertTrue(config.getNetworkInterfaces().isEmpty());

        AndroidEmulator emulator = null;
        TestSocketIO socket = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit()
                    .setEnvironmentConfig(config)
                    .build();
            socket = new TestSocketIO();
            List<NetworkIF> list = socket.exposeNetworkIFs(emulator);
            assertTrue(list.isEmpty());
        } finally {
            if (socket != null) {
                socket.close();
            }
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testHwAddrAndMtuIoctlUnknownInterface() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        TestSocketIO socket = null;
        MemoryBlock block = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            socket = new TestSocketIO();
            block = emulator.getMemory().malloc(40, true);
            IFReq req = IFReq.createIFReq(emulator, block.getPointer());
            req.setName("rmnet0");
            req.pack();
            long argp = UnidbgPointer.nativeValue(block.getPointer());
            emulator.getMemory().setErrno(0);
            assertEquals(-1, socket.ioctl(emulator, AndroidFileIO.SIOCGIFHWADDR, argp));
            assertEquals(19, emulator.getMemory().getLastErrno());
            emulator.getMemory().setErrno(0);
            assertEquals(-1, socket.ioctl(emulator, AndroidFileIO.SIOCGIFMTU, argp));
            assertEquals(19, emulator.getMemory().getLastErrno());
        } finally {
            if (block != null) {
                block.free();
            }
            if (socket != null) {
                socket.close();
            }
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testHwAddrAndMtuIoctlMissingFields() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(MISSING_MAC_MTU_JSON);
        AndroidEmulator emulator = null;
        TestSocketIO socket = null;
        MemoryBlock block = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            socket = new TestSocketIO();
            block = emulator.getMemory().malloc(40, true);
            IFReq req = IFReq.createIFReq(emulator, block.getPointer());
            req.setName("wlan0");
            req.pack();
            long argp = UnidbgPointer.nativeValue(block.getPointer());
            emulator.getMemory().setErrno(0);
            assertEquals(-1, socket.ioctl(emulator, AndroidFileIO.SIOCGIFHWADDR, argp));
            assertEquals(UnixEmulator.EOPNOTSUPP, emulator.getMemory().getLastErrno());
            emulator.getMemory().setErrno(0);
            assertEquals(-1, socket.ioctl(emulator, AndroidFileIO.SIOCGIFMTU, argp));
            assertEquals(UnixEmulator.EOPNOTSUPP, emulator.getMemory().getLastErrno());
        } finally {
            if (block != null) {
                block.free();
            }
            if (socket != null) {
                socket.close();
            }
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void assertConfiguredInterfaces(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        TestSocketIO socket = null;
        MemoryBlock block = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            socket = new TestSocketIO();
            List<NetworkIF> list = socket.exposeNetworkIFs(emulator);
            assertEquals(2, list.size());

            NetworkIF lo = list.get(0);
            assertEquals("lo", lo.ifName);
            assertEquals(1, lo.index);
            assertEquals("127.0.0.1", lo.ipv4.getHostAddress());
            assertNull(lo.broadcast);
            assertEquals(73, lo.configuredFlags);
            assertEquals("00:00:00:00:00:00", lo.mac);
            assertEquals(Integer.valueOf(65536), lo.mtu);

            NetworkIF wlan0 = list.get(1);
            assertEquals("wlan0", wlan0.ifName);
            assertEquals(2, wlan0.index);
            assertEquals("192.168.1.100", wlan0.ipv4.getHostAddress());
            assertEquals("192.168.1.255", wlan0.broadcast.getHostAddress());
            assertEquals(4355, wlan0.configuredFlags);
            assertEquals("02:00:00:00:00:01", wlan0.mac);
            assertEquals(Integer.valueOf(1500), wlan0.mtu);

            block = emulator.getMemory().malloc(40, true);
            Pointer p = block.getPointer();
            long argp = UnidbgPointer.nativeValue(p);

            // lo HWADDR
            IFReq req = IFReq.createIFReq(emulator, p);
            req.setName("lo");
            req.pack();
            assertEquals(0, socket.ioctl(emulator, AndroidFileIO.SIOCGIFHWADDR, argp));
            assertEquals(ARPHRD_LOOPBACK, p.getShort(16) & 0xffff);
            assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 0}, p.getByteArray(18, 6));

            // lo MTU
            req = IFReq.createIFReq(emulator, p);
            req.setName("lo");
            req.pack();
            assertEquals(0, socket.ioctl(emulator, AndroidFileIO.SIOCGIFMTU, argp));
            assertEquals(65536, p.getInt(16));

            // wlan0 HWADDR
            req = IFReq.createIFReq(emulator, p);
            req.setName("wlan0");
            req.pack();
            assertEquals(0, socket.ioctl(emulator, AndroidFileIO.SIOCGIFHWADDR, argp));
            assertEquals(ARPHRD_ETHER, p.getShort(16) & 0xffff);
            assertArrayEquals(new byte[]{0x02, 0x00, 0x00, 0x00, 0x00, 0x01}, p.getByteArray(18, 6));

            // wlan0 MTU
            req = IFReq.createIFReq(emulator, p);
            req.setName("wlan0");
            req.pack();
            assertEquals(0, socket.ioctl(emulator, AndroidFileIO.SIOCGIFMTU, argp));
            assertEquals(1500, p.getInt(16));
        } finally {
            if (block != null) {
                block.free();
            }
            if (socket != null) {
                socket.close();
            }
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    /** Same-package subclass exposing {@link SocketIO#getNetworkIFs}. */
    private static final class TestSocketIO extends SocketIO {
        List<NetworkIF> exposeNetworkIFs(Emulator<?> emulator) throws SocketException {
            return getNetworkIFs(emulator);
        }

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
