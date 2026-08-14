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
 * {@code /sys/class/net/<name>/flags} (UTF-8 lowercase {@code 0x}-prefixed hex + LF).
 */
public class LinuxFileSystemNetworkFlagsTest {

    private static final String WLAN_FLAGS_PATH = "/sys/class/net/wlan0/flags";
    private static final String ETH_FLAGS_PATH = "/sys/class/net/eth0/flags";
    private static final String LO_FLAGS_PATH = "/sys/class/net/lo/flags";
    private static final String UNKNOWN_FLAGS_PATH = "/sys/class/net/not0/flags";
    private static final String OPERSTATE_PATH = "/sys/class/net/wlan0/operstate";
    private static final String WLAN_ADDRESS_PATH = "/sys/class/net/wlan0/address";
    private static final String WLAN_MTU_PATH = "/sys/class/net/wlan0/mtu";
    private static final String WLAN_IFINDEX_PATH = "/sys/class/net/wlan0/ifindex";

    private static final String WLAN_MAC = "02:00:00:00:00:01";
    private static final String ETH_MAC_JSON = "AA:BB:CC:DD:EE:FF";
    private static final String ETH_MAC_CANONICAL = "aa:bb:cc:dd:ee:ff";

    private static final int LO_FLAGS = 73;
    private static final int ETH_FLAGS = 4163;
    private static final int WLAN_MTU = 1500;
    private static final String LO_FLAGS_TEXT = "0x49\n";
    private static final String ETH_FLAGS_TEXT = "0x1043\n";

    private static final String TWO_IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"flags\":" + LO_FLAGS + "},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
            + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU + "},"
            + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
            + "\"mac\":\"" + ETH_MAC_JSON + "\",\"flags\":" + ETH_FLAGS + "}"
            + "]}}"
            ;

    @Test
    public void testConfiguredFlagsOpenAndSidecar64() throws Exception {
        runConfigured(true);
    }

    @Test
    public void testConfiguredFlagsOpenAndSidecar32() throws Exception {
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

            String ethText = readOpenText(emulator, blocks, ETH_FLAGS_PATH);
            assertEquals(ETH_FLAGS_TEXT, ethText);
            assertTrue(ethText.startsWith("0x"));
            assertFalse(ethText.startsWith("0X"));
            assertTrue(ethText.endsWith("\n"));
            assertEquals(1, countNewlines(ethText));

            String loText = readOpenText(emulator, blocks, LO_FLAGS_PATH);
            assertEquals(LO_FLAGS_TEXT, loText);
            assertTrue(loText.startsWith("0x"));
            assertFalse(loText.startsWith("0X"));
            assertTrue(loText.endsWith("\n"));
            assertEquals(1, countNewlines(loText));

            assertEquals(2, countNetworkFlagsReads(sink));
            CapturedEvent ethEv = findFlagsRead(sink, ETH_FLAGS_PATH);
            CapturedEvent loEv = findFlagsRead(sink, LO_FLAGS_PATH);
            assertFlagsSidecar(ethEv, ETH_FLAGS_PATH, "eth0",
                    ETH_FLAGS_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertFlagsSidecar(loEv, LO_FLAGS_PATH, "lo",
                    LO_FLAGS_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsFlagsValueAndUnrelated(ethEv);
            assertSidecarOmitsFlagsValueAndUnrelated(loEv);
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
    public void testLinuxFilesPriorityOverAutoFlags() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"files\":{\"" + WLAN_FLAGS_PATH + "\":\"0xdead\\n\"}},"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
                + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU + ",\"flags\":" + ETH_FLAGS + "},"
                + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
                + "\"mac\":\"" + ETH_MAC_JSON + "\",\"flags\":" + ETH_FLAGS + "}"
                + "]}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("0xdead\n", readOpenText(emulator, blocks, WLAN_FLAGS_PATH));
            assertEquals(ETH_FLAGS_TEXT, readOpenText(emulator, blocks, ETH_FLAGS_PATH));

            boolean sawLinuxFile = false;
            boolean sawEthFlags = false;
            for (CapturedEvent e : sink.events) {
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
                if (isNetworkFlagsRead(e, WLAN_FLAGS_PATH)) {
                    fail("wlan0 flags should not emit network_device read when linux.files wins");
                }
                if (isNetworkFlagsRead(e, ETH_FLAGS_PATH)) {
                    sawEthFlags = true;
                    assertSidecarOmitsFlagsValueAndUnrelated(e);
                }
            }
            assertTrue(sawLinuxFile);
            assertTrue(sawEthFlags);
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
    public void testUnknownMissingFlagsAndOtherSysPathsNotTakenOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertNotTakenOverAsConfiguredFlags(emulator, blocks, sink, UNKNOWN_FLAGS_PATH);
            assertNotTakenOverAsConfiguredFlags(emulator, blocks, sink, OPERSTATE_PATH);
            assertNotTakenOverAsConfiguredFlags(emulator, blocks, sink, WLAN_FLAGS_PATH);
            assertNotTakenOverAsConfiguredFlags(emulator, blocks, sink, WLAN_ADDRESS_PATH);
            assertNotTakenOverAsConfiguredFlags(emulator, blocks, sink, WLAN_MTU_PATH);
            assertNotTakenOverAsConfiguredFlags(emulator, blocks, sink, WLAN_IFINDEX_PATH);
            assertEquals(WLAN_MAC + "\n", readOpenText(emulator, blocks, WLAN_ADDRESS_PATH));
            assertEquals(WLAN_MTU + "\n", readOpenText(emulator, blocks, WLAN_MTU_PATH));
            assertEquals("2\n", readOpenText(emulator, blocks, WLAN_IFINDEX_PATH));
            assertEquals(0, countNetworkFlagsReads(sink));

            assertEquals(ETH_FLAGS_TEXT, readOpenText(emulator, blocks, ETH_FLAGS_PATH));
            assertEquals(1, countNetworkFlagsReads(sink));
            CapturedEvent ethEv = findFlagsRead(sink, ETH_FLAGS_PATH);
            assertFlagsSidecar(ethEv, ETH_FLAGS_PATH, "eth0",
                    ETH_FLAGS_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsFlagsValueAndUnrelated(ethEv);
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
            assertNotTakenOverAsConfiguredFlags(emulator, blocks, sink, ETH_FLAGS_PATH);
            assertEquals(0, countNetworkFlagsReads(sink));
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

    private static void assertNotTakenOverAsConfiguredFlags(AndroidEmulator emulator,
                                                            List<MemoryBlock> blocks,
                                                            CapturingSink sink,
                                                            String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            assertFalse(path + " must not serve configured eth flags", ETH_FLAGS_TEXT.equals(text));
            assertFalse(path + " must not serve configured lo flags", LO_FLAGS_TEXT.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected auto flags sidecar for " + path,
                    isNetworkFlagsRead(e, path));
        }
    }

    private static void assertFlagsSidecar(CapturedEvent e, String path, String name, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",name=" + name + ",format=flags,bytes=" + bytes,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains(path));
        assertTrue(e.note.contains("读取"));
    }

    private static void assertSidecarOmitsFlagsValueAndUnrelated(CapturedEvent e) {
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
        assertFalse(note.contains("192.168.1.100"));
        assertFalse(note.contains("10.0.0.2"));
        assertFalse(note.contains("127.0.0.1"));
        assertFalse(value.contains(String.valueOf(ETH_FLAGS)));
        assertFalse(value.contains(String.valueOf(LO_FLAGS)));
        assertFalse(value.contains("0x1043"));
        assertFalse(value.contains("0x49"));
        assertFalse(note.contains(String.valueOf(ETH_FLAGS)));
        assertFalse(note.contains(String.valueOf(LO_FLAGS)));
        assertFalse(note.contains("0x1043"));
        assertFalse(note.contains("0x49"));
        assertFalse(value.contains("format=address"));
        assertFalse(value.contains("format=mtu"));
        assertFalse(value.contains("format=ifindex"));
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

    private static int countNetworkFlagsReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isNetworkFlagsRead(e, WLAN_FLAGS_PATH) || isNetworkFlagsRead(e, ETH_FLAGS_PATH)
                    || isNetworkFlagsRead(e, LO_FLAGS_PATH) || isNetworkFlagsRead(e, UNKNOWN_FLAGS_PATH)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findFlagsRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isNetworkFlagsRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isNetworkFlagsRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=flags");
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
