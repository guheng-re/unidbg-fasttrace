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
 * {@code /sys/class/net/<name>/ifindex} (UTF-8 decimal index + LF).
 */
public class LinuxFileSystemNetworkIfindexTest {

    private static final String WLAN_IFINDEX_PATH = "/sys/class/net/wlan0/ifindex";
    private static final String ETH_IFINDEX_PATH = "/sys/class/net/eth0/ifindex";
    private static final String LO_IFINDEX_PATH = "/sys/class/net/lo/ifindex";
    private static final String UNKNOWN_IFINDEX_PATH = "/sys/class/net/not0/ifindex";
    private static final String OPERSTATE_PATH = "/sys/class/net/wlan0/operstate";
    private static final String WLAN_ADDRESS_PATH = "/sys/class/net/wlan0/address";
    private static final String WLAN_MTU_PATH = "/sys/class/net/wlan0/mtu";

    private static final String WLAN_MAC = "02:00:00:00:00:01";
    private static final String ETH_MAC_JSON = "AA:BB:CC:DD:EE:FF";
    private static final String ETH_MAC_CANONICAL = "aa:bb:cc:dd:ee:ff";

    private static final int LO_INDEX = 1;
    private static final int WLAN_INDEX = 2;
    private static final int ETH_INDEX = 3;
    private static final int WLAN_MTU = 1500;
    private static final int ETH_FLAGS = 4163;

    private static final String TWO_IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":" + LO_INDEX + ",\"ipv4\":\"127.0.0.1\",\"flags\":73},"
            + "{\"name\":\"wlan0\",\"index\":" + WLAN_INDEX + ",\"ipv4\":\"192.168.1.100\","
            + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU + "},"
            + "{\"name\":\"eth0\",\"index\":" + ETH_INDEX + ",\"ipv4\":\"10.0.0.2\","
            + "\"mac\":\"" + ETH_MAC_JSON + "\",\"flags\":" + ETH_FLAGS + "}"
            + "]}}"
            ;

    @Test
    public void testConfiguredIfindexOpenAndSidecar64() throws Exception {
        runConfigured(true);
    }

    @Test
    public void testConfiguredIfindexOpenAndSidecar32() throws Exception {
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

            String wlanText = readOpenText(emulator, blocks, WLAN_IFINDEX_PATH);
            assertEquals(WLAN_INDEX + "\n", wlanText);
            assertTrue(wlanText.endsWith("\n"));
            assertEquals(1, countNewlines(wlanText));

            String ethText = readOpenText(emulator, blocks, ETH_IFINDEX_PATH);
            assertEquals(ETH_INDEX + "\n", ethText);
            assertTrue(ethText.endsWith("\n"));
            assertEquals(1, countNewlines(ethText));

            String loText = readOpenText(emulator, blocks, LO_IFINDEX_PATH);
            assertEquals(LO_INDEX + "\n", loText);
            assertTrue(loText.endsWith("\n"));
            assertEquals(1, countNewlines(loText));

            assertEquals(3, countNetworkIfindexReads(sink));
            CapturedEvent wlanEv = findIfindexRead(sink, WLAN_IFINDEX_PATH);
            CapturedEvent ethEv = findIfindexRead(sink, ETH_IFINDEX_PATH);
            CapturedEvent loEv = findIfindexRead(sink, LO_IFINDEX_PATH);
            assertIfindexSidecar(wlanEv, WLAN_IFINDEX_PATH, "wlan0",
                    (WLAN_INDEX + "\n").getBytes(StandardCharsets.UTF_8).length);
            assertIfindexSidecar(ethEv, ETH_IFINDEX_PATH, "eth0",
                    (ETH_INDEX + "\n").getBytes(StandardCharsets.UTF_8).length);
            assertIfindexSidecar(loEv, LO_IFINDEX_PATH, "lo",
                    (LO_INDEX + "\n").getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsUnrelated(wlanEv);
            assertSidecarOmitsUnrelated(ethEv);
            assertSidecarOmitsUnrelated(loEv);
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
    public void testLinuxFilesPriorityOverAutoIfindex() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"files\":{\"" + WLAN_IFINDEX_PATH + "\":\"99\\n\"}},"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":" + WLAN_INDEX + ",\"ipv4\":\"192.168.1.100\","
                + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU + "},"
                + "{\"name\":\"eth0\",\"index\":" + ETH_INDEX + ",\"ipv4\":\"10.0.0.2\","
                + "\"mac\":\"" + ETH_MAC_JSON + "\"}"
                + "]}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("99\n", readOpenText(emulator, blocks, WLAN_IFINDEX_PATH));
            assertEquals(ETH_INDEX + "\n", readOpenText(emulator, blocks, ETH_IFINDEX_PATH));

            boolean sawLinuxFile = false;
            boolean sawEthIfindex = false;
            for (CapturedEvent e : sink.events) {
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
                if (isNetworkIfindexRead(e, WLAN_IFINDEX_PATH)) {
                    fail("wlan0 ifindex should not emit network_device read when linux.files wins");
                }
                if (isNetworkIfindexRead(e, ETH_IFINDEX_PATH)) {
                    sawEthIfindex = true;
                    assertSidecarOmitsUnrelated(e);
                }
            }
            assertTrue(sawLinuxFile);
            assertTrue(sawEthIfindex);
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

            String expectedIfindexText = WLAN_INDEX + "\n";
            assertNotTakenOverAsConfiguredIfindex(emulator, blocks, sink, UNKNOWN_IFINDEX_PATH, expectedIfindexText);
            assertNotTakenOverAsConfiguredIfindex(emulator, blocks, sink, OPERSTATE_PATH, expectedIfindexText);
            assertNotTakenOverAsConfiguredIfindex(emulator, blocks, sink, WLAN_ADDRESS_PATH, expectedIfindexText);
            assertNotTakenOverAsConfiguredIfindex(emulator, blocks, sink, WLAN_MTU_PATH, expectedIfindexText);
            assertEquals(WLAN_MAC + "\n", readOpenText(emulator, blocks, WLAN_ADDRESS_PATH));
            assertEquals(WLAN_MTU + "\n", readOpenText(emulator, blocks, WLAN_MTU_PATH));
            assertEquals(0, countNetworkIfindexReads(sink));

            assertEquals(expectedIfindexText, readOpenText(emulator, blocks, WLAN_IFINDEX_PATH));
            assertEquals(1, countNetworkIfindexReads(sink));
            CapturedEvent wlanEv = findIfindexRead(sink, WLAN_IFINDEX_PATH);
            assertIfindexSidecar(wlanEv, WLAN_IFINDEX_PATH, "wlan0",
                    expectedIfindexText.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsUnrelated(wlanEv);
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
            assertNotTakenOverAsConfiguredIfindex(emulator, blocks, sink, WLAN_IFINDEX_PATH, WLAN_INDEX + "\n");
            assertEquals(0, countNetworkIfindexReads(sink));
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

    private static void assertNotTakenOverAsConfiguredIfindex(AndroidEmulator emulator,
                                                              List<MemoryBlock> blocks,
                                                              CapturingSink sink,
                                                              String path,
                                                              String ifindexText) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            assertFalse(path + " must not serve configured ifindex", ifindexText.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected auto ifindex sidecar for " + path,
                    isNetworkIfindexRead(e, path));
        }
    }

    private static void assertIfindexSidecar(CapturedEvent e, String path, String name, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",name=" + name + ",format=ifindex,bytes=" + bytes,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains(path));
    }

    private static void assertSidecarOmitsUnrelated(CapturedEvent e) {
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
        assertFalse(value.contains("127.0.0.1"));
        assertFalse(value.contains("flags="));
        assertFalse(value.contains(String.valueOf(ETH_FLAGS)));
        assertFalse(value.contains("format=address"));
        assertFalse(value.contains("format=mtu"));
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

    private static int countNetworkIfindexReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isNetworkIfindexRead(e, WLAN_IFINDEX_PATH) || isNetworkIfindexRead(e, ETH_IFINDEX_PATH)
                    || isNetworkIfindexRead(e, LO_IFINDEX_PATH) || isNetworkIfindexRead(e, UNKNOWN_IFINDEX_PATH)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findIfindexRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isNetworkIfindexRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isNetworkIfindexRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=ifindex");
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
