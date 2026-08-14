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
 * {@code /sys/class/net/<name>/mtu} (UTF-8 decimal MTU + LF).
 */
public class LinuxFileSystemNetworkMtuTest {

    private static final String WLAN_MTU_PATH = "/sys/class/net/wlan0/mtu";
    private static final String ETH_MTU_PATH = "/sys/class/net/eth0/mtu";
    private static final String LO_MTU_PATH = "/sys/class/net/lo/mtu";
    private static final String UNKNOWN_MTU_PATH = "/sys/class/net/not0/mtu";
    private static final String OPERSTATE_PATH = "/sys/class/net/wlan0/operstate";
    private static final String WLAN_ADDRESS_PATH = "/sys/class/net/wlan0/address";

    private static final String WLAN_MAC = "02:00:00:00:00:01";
    private static final String ETH_MAC_JSON = "AA:BB:CC:DD:EE:FF";
    private static final String ETH_MAC_CANONICAL = "aa:bb:cc:dd:ee:ff";

    private static final int WLAN_MTU = 1500;
    private static final int ETH_MTU = 9000;

    private static final String TWO_IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"flags\":73},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
            + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU + "},"
            + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
            + "\"mac\":\"" + ETH_MAC_JSON + "\",\"mtu\":" + ETH_MTU + "}"
            + "]}}"
            ;

    private static final String MISSING_MTU_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"flags\":73},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
            + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU + "},"
            + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
            + "\"mac\":\"" + ETH_MAC_JSON + "\"}"
            + "]}}"
            ;

    @Test
    public void testConfiguredMtuOpenAndSidecar64() throws Exception {
        runConfigured(true);
    }

    @Test
    public void testConfiguredMtuOpenAndSidecar32() throws Exception {
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

            String wlanText = readOpenText(emulator, blocks, WLAN_MTU_PATH);
            assertEquals(WLAN_MTU + "\n", wlanText);
            assertTrue(wlanText.endsWith("\n"));
            assertEquals(1, countNewlines(wlanText));

            String ethText = readOpenText(emulator, blocks, ETH_MTU_PATH);
            assertEquals(ETH_MTU + "\n", ethText);
            assertTrue(ethText.endsWith("\n"));
            assertEquals(1, countNewlines(ethText));

            assertEquals(2, countNetworkMtuReads(sink));
            CapturedEvent wlanEv = findMtuRead(sink, WLAN_MTU_PATH);
            CapturedEvent ethEv = findMtuRead(sink, ETH_MTU_PATH);
            assertMtuSidecar(wlanEv, WLAN_MTU_PATH, "wlan0",
                    (WLAN_MTU + "\n").getBytes(StandardCharsets.UTF_8).length);
            assertMtuSidecar(ethEv, ETH_MTU_PATH, "eth0",
                    (ETH_MTU + "\n").getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsMacAndUnrelated(wlanEv);
            assertSidecarOmitsMacAndUnrelated(ethEv);
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
    public void testLinuxFilesPriorityOverAutoMtu() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"files\":{\"" + WLAN_MTU_PATH + "\":\"9999\\n\"}},"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
                + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU + "},"
                + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
                + "\"mac\":\"" + ETH_MAC_JSON + "\",\"mtu\":" + ETH_MTU + "}"
                + "]}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("9999\n", readOpenText(emulator, blocks, WLAN_MTU_PATH));
            assertEquals(ETH_MTU + "\n", readOpenText(emulator, blocks, ETH_MTU_PATH));

            boolean sawLinuxFile = false;
            boolean sawEthMtu = false;
            for (CapturedEvent e : sink.events) {
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
                if (isNetworkMtuRead(e, WLAN_MTU_PATH)) {
                    fail("wlan0 mtu should not emit network_device read when linux.files wins");
                }
                if (isNetworkMtuRead(e, ETH_MTU_PATH)) {
                    sawEthMtu = true;
                    assertSidecarOmitsMacAndUnrelated(e);
                }
            }
            assertTrue(sawLinuxFile);
            assertTrue(sawEthMtu);
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
    public void testUnknownMissingMtuAndOtherSysPathsNotTakenOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(MISSING_MTU_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            String expectedMtuText = WLAN_MTU + "\n";
            assertNotTakenOverAsConfiguredMtu(emulator, blocks, sink, UNKNOWN_MTU_PATH, expectedMtuText);
            assertNotTakenOverAsConfiguredMtu(emulator, blocks, sink, OPERSTATE_PATH, expectedMtuText);
            assertNotTakenOverAsConfiguredMtu(emulator, blocks, sink, LO_MTU_PATH, expectedMtuText);
            assertNotTakenOverAsConfiguredMtu(emulator, blocks, sink, ETH_MTU_PATH, expectedMtuText);
            assertNotTakenOverAsConfiguredMtu(emulator, blocks, sink, WLAN_ADDRESS_PATH, expectedMtuText);
            assertEquals(0, countNetworkMtuReads(sink));

            assertEquals(expectedMtuText, readOpenText(emulator, blocks, WLAN_MTU_PATH));
            assertEquals(1, countNetworkMtuReads(sink));
            CapturedEvent wlanEv = findMtuRead(sink, WLAN_MTU_PATH);
            assertMtuSidecar(wlanEv, WLAN_MTU_PATH, "wlan0",
                    expectedMtuText.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsMacAndUnrelated(wlanEv);
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
            assertNotTakenOverAsConfiguredMtu(emulator, blocks, sink, WLAN_MTU_PATH, WLAN_MTU + "\n");
            assertEquals(0, countNetworkMtuReads(sink));
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

    private static void assertNotTakenOverAsConfiguredMtu(AndroidEmulator emulator,
                                                          List<MemoryBlock> blocks,
                                                          CapturingSink sink,
                                                          String path,
                                                          String mtuText) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            assertFalse(path + " must not serve configured MTU", mtuText.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected auto mtu sidecar for " + path,
                    isNetworkMtuRead(e, path));
        }
    }

    private static void assertMtuSidecar(CapturedEvent e, String path, String name, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",name=" + name + ",format=mtu,bytes=" + bytes,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains(path));
    }

    private static void assertSidecarOmitsMacAndUnrelated(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains(WLAN_MAC));
        assertFalse(value.contains(ETH_MAC_JSON));
        assertFalse(value.contains(ETH_MAC_CANONICAL));
        assertFalse(note.contains(WLAN_MAC));
        assertFalse(note.contains(ETH_MAC_JSON));
        assertFalse(note.contains(ETH_MAC_CANONICAL));
        assertFalse(value.contains("192.168.1.100"));
        assertFalse(value.contains("10.0.0.2"));
        assertFalse(value.contains("format=address"));
    }

    private static int countNewlines(String text) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    private static int countNetworkMtuReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isNetworkMtuRead(e, WLAN_MTU_PATH) || isNetworkMtuRead(e, ETH_MTU_PATH)
                    || isNetworkMtuRead(e, LO_MTU_PATH) || isNetworkMtuRead(e, UNKNOWN_MTU_PATH)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findMtuRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isNetworkMtuRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isNetworkMtuRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=mtu");
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
