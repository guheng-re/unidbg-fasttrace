package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.EmulatorBuilder;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.sun.jna.Pointer;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * LinuxFileSystem wiring for {@code network.interfaces} → exact
 * {@code /sys/class/net/<name>/address} (UTF-8 lowercase MAC + LF).
 */
public class LinuxFileSystemNetworkAddressTest {

    private static final String WLAN_PATH = "/sys/class/net/wlan0/address";
    private static final String ETH_PATH = "/sys/class/net/eth0/address";
    private static final String LO_PATH = "/sys/class/net/lo/address";
    private static final String UNKNOWN_PATH = "/sys/class/net/not0/address";
    private static final String OPERSTATE_PATH = "/sys/class/net/wlan0/operstate";

    private static final String WLAN_MAC = "02:00:00:00:00:01";
    private static final String ETH_MAC_JSON = "AA:BB:CC:DD:EE:FF";
    private static final String ETH_MAC_CANONICAL = "aa:bb:cc:dd:ee:ff";

    private static final String TWO_IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"flags\":73},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
            + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":1500},"
            + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
            + "\"mac\":\"" + ETH_MAC_JSON + "\"}"
            + "]}}"
            ;

    @Test
    public void testConfiguredAddressOpenAndSidecar64() throws Exception {
        runConfigured(true);
    }

    @Test
    public void testConfiguredAddressOpenAndSidecar32() throws Exception {
        runConfigured(false);
    }

    private static EmulatorBuilder<AndroidEmulator> emulatorBuilder(boolean is64Bit) {
        return (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                .addBackendFactory(new Unicorn2Factory(true));
    }

    private static void runConfigured(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(is64Bit)
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);

            String wlanText = readOpenText(emulator, blocks, WLAN_PATH);
            assertEquals(WLAN_MAC + "\n", wlanText);
            assertTrue(wlanText.endsWith("\n"));
            assertEquals(wlanText, wlanText.toLowerCase());

            String ethText = readOpenText(emulator, blocks, ETH_PATH);
            assertEquals(ETH_MAC_CANONICAL + "\n", ethText);
            assertTrue(ethText.endsWith("\n"));
            assertEquals(ethText, ethText.toLowerCase());

            assertEquals(2, countNetworkAddressReads(sink));
            CapturedEvent wlanEv = findAddressRead(sink, WLAN_PATH);
            CapturedEvent ethEv = findAddressRead(sink, ETH_PATH);
            assertAddressSidecar(wlanEv, WLAN_PATH, "wlan0",
                    (WLAN_MAC + "\n").getBytes(StandardCharsets.UTF_8).length);
            assertAddressSidecar(ethEv, ETH_PATH, "eth0",
                    (ETH_MAC_CANONICAL + "\n").getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsMac(wlanEv);
            assertSidecarOmitsMac(ethEv);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testLinuxFilesPriorityOverAutoAddress() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"files\":{\"" + WLAN_PATH + "\":\"CUSTOM_MAC\\n\"}},"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\",\"mac\":\"" + WLAN_MAC + "\"},"
                + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\",\"mac\":\"" + ETH_MAC_JSON + "\"}"
                + "]}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("CUSTOM_MAC\n", readOpenText(emulator, blocks, WLAN_PATH));
            assertEquals(ETH_MAC_CANONICAL + "\n", readOpenText(emulator, blocks, ETH_PATH));

            boolean sawLinuxFile = false;
            boolean sawEthAddress = false;
            for (CapturedEvent e : sink.events) {
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
                if (isNetworkAddressRead(e, WLAN_PATH)) {
                    fail("wlan0 address should not emit network_device read when linux.files wins");
                }
                if (isNetworkAddressRead(e, ETH_PATH)) {
                    sawEthAddress = true;
                    assertSidecarOmitsMac(e);
                }
            }
            assertTrue(sawLinuxFile);
            assertTrue(sawEthAddress);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testUnknownInterfaceAndOtherSysPathsNotTakenOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertNotTakenOverAsConfiguredMac(emulator, blocks, sink, UNKNOWN_PATH, WLAN_MAC);
            assertNotTakenOverAsConfiguredMac(emulator, blocks, sink, UNKNOWN_PATH, ETH_MAC_CANONICAL);
            assertNotTakenOverAsConfiguredMac(emulator, blocks, sink, OPERSTATE_PATH, WLAN_MAC);
            assertNotTakenOverAsConfiguredMac(emulator, blocks, sink, LO_PATH, WLAN_MAC);
            assertEquals(0, countNetworkAddressReads(sink));

            assertEquals(WLAN_MAC + "\n", readOpenText(emulator, blocks, WLAN_PATH));
            assertEquals(1, countNetworkAddressReads(sink));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }

        sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredMac(emulator, blocks, sink, WLAN_PATH, WLAN_MAC);
            assertEquals(0, countNetworkAddressReads(sink));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void assertNotTakenOverAsConfiguredMac(AndroidEmulator emulator,
                                                          List<MemoryBlock> blocks,
                                                          CapturingSink sink,
                                                          String path,
                                                          String mac) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            assertFalse(path + " must not serve configured MAC", (mac + "\n").equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected auto address sidecar for " + path,
                    isNetworkAddressRead(e, path));
        }
    }

    private static void assertAddressSidecar(CapturedEvent e, String path, String name, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",name=" + name + ",format=address,bytes=" + bytes,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains(path));
    }

    private static void assertSidecarOmitsMac(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains(WLAN_MAC));
        assertFalse(value.contains(ETH_MAC_JSON));
        assertFalse(value.contains(ETH_MAC_CANONICAL));
        assertFalse(note.contains(WLAN_MAC));
        assertFalse(note.contains(ETH_MAC_JSON));
        assertFalse(note.contains(ETH_MAC_CANONICAL));
    }

    private static int countNetworkAddressReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if ("network_device".equals(e.kind)
                    && String.valueOf(e.api).startsWith("read(\"" + "/sys/class/net/")) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findAddressRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isNetworkAddressRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isNetworkAddressRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api);
    }

    private static String readOpenText(AndroidEmulator emulator, List<MemoryBlock> blocks,
                                       String path) throws Exception {
        return new String(readOpenBytes(emulator, blocks, path), StandardCharsets.UTF_8);
    }

    private static byte[] readOpenBytes(AndroidEmulator emulator, List<MemoryBlock> blocks,
                                        String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        assertNotNull(result);
        assertTrue("open failed for " + path, result.isSuccess());
        assertNotNull(result.io);
        assertTrue(result.io instanceof ByteArrayFileIO);
        return readIoBytes(emulator, blocks, result.io, 8192);
    }

    private static String readIoText(AndroidEmulator emulator, List<MemoryBlock> blocks,
                                     AndroidFileIO io, int max) throws Exception {
        return new String(readIoBytes(emulator, blocks, io, max), StandardCharsets.UTF_8);
    }

    private static byte[] readIoBytes(AndroidEmulator emulator, List<MemoryBlock> blocks,
                                      AndroidFileIO io, int max) throws Exception {
        MemoryBlock block = emulator.getMemory().malloc(max, true);
        blocks.add(block);
        Pointer ptr = block.getPointer();
        int n = io.read(emulator.getBackend(), ptr, max);
        assertTrue(n >= 0);
        if (n == 0) {
            return new byte[0];
        }
        return ptr.getByteArray(0, n);
    }

    private static void freeAll(List<MemoryBlock> blocks) {
        for (MemoryBlock block : blocks) {
            try {
                block.free();
            } catch (Exception ignored) {
                // teardown
            }
        }
        blocks.clear();
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
}
