package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
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
 * LinuxFileSystem wiring for {@code network.interfaceStats} → exact
 * {@code /sys/class/net/<name>/statistics/<field>} (decimal ASCII + single LF).
 * Default backend (no Unicorn2). Never infers counters from interfaces, routes,
 * wifi, or other fields. Never reads host {@code NetworkInterface} or real sysfs.
 * Negative cases must not pass merely because legacy fallback happened to succeed.
 */
public class LinuxFileSystemInterfaceStatsSysfsTest {

    private static final int CONFIG_PID = 4242;

    private static final String LO_IFACE_JSON =
            "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"}";
    private static final String WLAN_IFACE_JSON =
            "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\"}";
    private static final String ETH_IFACE_JSON =
            "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\"}";

    private static final String LO_STATS_JSON =
            "{\"interfaceName\":\"lo\",\"rxBytes\":1280,\"rxPackets\":10,"
                    + "\"rxErrors\":0,\"rxDrop\":0,\"rxFifo\":0,\"rxFrame\":0,"
                    + "\"rxCompressed\":0,\"rxMulticast\":0,\"txBytes\":1280,"
                    + "\"txPackets\":10,\"txErrors\":0,\"txDrop\":0,\"txFifo\":0,"
                    + "\"txCollisions\":0,\"txCarrier\":0,\"txCompressed\":0}";

    private static final String WLAN_STATS_JSON =
            "{\"interfaceName\":\"wlan0\",\"rxBytes\":4096001,\"rxPackets\":32001,"
                    + "\"rxErrors\":101,\"rxDrop\":202,\"rxFifo\":303,\"rxFrame\":404,"
                    + "\"rxCompressed\":505,\"rxMulticast\":2401,\"txBytes\":2048001,"
                    + "\"txPackets\":16001,\"txErrors\":606,\"txDrop\":707,\"txFifo\":808,"
                    + "\"txCollisions\":909,\"txCarrier\":1111,\"txCompressed\":2222}";

    private static final String TWO_STATS_JSON = "{"
            + "\"process\":{\"pid\":" + CONFIG_PID + "},"
            + "\"network\":{\"interfaces\":["
            + LO_IFACE_JSON + "," + WLAN_IFACE_JSON + "," + ETH_IFACE_JSON + "],"
            + "\"interfaceStats\":[" + LO_STATS_JSON + "," + WLAN_STATS_JSON + "]}}";

    /** Independent expected mapping (kernel filename → lo / wlan0 counters). */
    private static final String[] KERNEL_FIELDS = {
            "rx_bytes", "rx_packets", "rx_errors", "rx_dropped", "rx_fifo_errors",
            "rx_frame_errors", "rx_compressed", "multicast",
            "tx_bytes", "tx_packets", "tx_errors", "tx_dropped", "tx_fifo_errors",
            "tx_carrier_errors", "tx_compressed", "collisions"
    };

    private static final long[] LO_VALUES = {
            1280L, 10L, 0L, 0L, 0L,
            0L, 0L, 0L,
            1280L, 10L, 0L, 0L, 0L,
            0L, 0L, 0L
    };

    private static final long[] WLAN_VALUES = {
            4096001L, 32001L, 101L, 202L, 303L,
            404L, 505L, 2401L,
            2048001L, 16001L, 606L, 707L, 808L,
            1111L, 2222L, 909L
    };

    private static final String[] UNSUPPORTED_KERNEL_FIELDS = {
            "rx_crc_errors", "rx_length_errors", "rx_missed_errors", "rx_nohandler",
            "rx_over_errors", "tx_aborted_errors", "tx_heartbeat_errors", "tx_window_errors"
    };

    private static final String WLAN_RX_BYTES = sysPath("wlan0", "rx_bytes");
    private static final String LO_RX_BYTES = sysPath("lo", "rx_bytes");
    private static final String ETH_RX_BYTES = sysPath("eth0", "rx_bytes");
    private static final String UNKNOWN_RX_BYTES = sysPath("not0", "rx_bytes");

    @Test
    public void testSixteenMappingsExactLfIsolationAndSidecar() throws Exception {
        assertEquals(16, KERNEL_FIELDS.length);
        assertEquals(16, LO_VALUES.length);
        assertEquals(16, WLAN_VALUES.length);
        assertEquals(8, UNSUPPORTED_KERNEL_FIELDS.length);

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_STATS_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            for (int i = 0; i < KERNEL_FIELDS.length; i++) {
                String field = KERNEL_FIELDS[i];
                String wlanPath = sysPath("wlan0", field);
                String loPath = sysPath("lo", field);
                byte[] wlanBytes = readOpenBytes(emulator, blocks, wlanPath);
                byte[] loBytes = readOpenBytes(emulator, blocks, loPath);
                String wlanText = new String(wlanBytes, StandardCharsets.UTF_8);
                String loText = new String(loBytes, StandardCharsets.UTF_8);

                assertEquals(decimalLf(WLAN_VALUES[i]), wlanText);
                assertArrayEqualsBytes(
                        decimalLf(WLAN_VALUES[i]).getBytes(StandardCharsets.UTF_8), wlanBytes);
                assertExactSingleLf(wlanText);
                assertFalse(wlanText.contains("\r"));

                assertEquals(decimalLf(LO_VALUES[i]), loText);
                assertArrayEqualsBytes(
                        decimalLf(LO_VALUES[i]).getBytes(StandardCharsets.UTF_8), loBytes);
                assertExactSingleLf(loText);
                assertFalse(loText.contains("\r"));
                assertFalse(wlanPath + " must not return lo counter", wlanText.equals(loText));

                CapturedEvent wlanEv = findStatsRead(sink, wlanPath, field);
                assertStatsSidecar(wlanEv, wlanPath, "wlan0", field, wlanBytes.length);
                assertSidecarRedacted(wlanEv);
                CapturedEvent loEv = findStatsRead(sink, loPath, field);
                assertStatsSidecar(loEv, loPath, "lo", field, loBytes.length);
                assertSidecarRedacted(loEv);
            }

            assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink, ETH_RX_BYTES);
            assertEquals(0, countStatsReads(sink, ETH_RX_BYTES));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesTakesPriority() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"files\":{\"" + WLAN_RX_BYTES + "\":\"CUSTOM_RX\\n\"}},"
                + TWO_STATS_JSON.substring(TWO_STATS_JSON.indexOf("\"network\"")));
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals("CUSTOM_RX\n", readOpenText(emulator, blocks, WLAN_RX_BYTES));
            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                if (isStatsRead(e, WLAN_RX_BYTES, "rx_bytes")) {
                    fail("network_device interfaceStats sysfs sidecar must not fire when linux.files wins");
                }
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
            }
            assertTrue(sawLinuxFile);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testMissingNodeAndExplicitEmptyArrayDoNotTakeOver() throws Exception {
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "," + WLAN_IFACE_JSON + "]}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(missing).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink, WLAN_RX_BYTES);
            assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink, LO_RX_BYTES);
            assertEquals(0, countAllStatsReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "," + WLAN_IFACE_JSON + "],"
                + "\"interfaceStats\":[]}}");
        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(empty).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink, WLAN_RX_BYTES);
            assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink, LO_RX_BYTES);
            assertEquals(0, countAllStatsReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testWriteDirectoryUnknownNearAndUnsupportedDoNotTakeOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_STATS_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_RX_BYTES, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_RX_BYTES, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_RX_BYTES, IOConstants.O_DIRECTORY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_RX_BYTES,
                    IOConstants.O_RDONLY | IOConstants.O_DIRECTORY);

            assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink, UNKNOWN_RX_BYTES);
            assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink, ETH_RX_BYTES);

            assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink,
                    "/sys/class/net/wlan0/statistics");
            assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink,
                    "/sys/class/net/wlan0/statistics/");
            assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink,
                    "/sys/class/net/wlan0/statistics/rx_bytes/");
            assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink,
                    "/sys/class/net/wlan0/rx_bytes");
            assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink,
                    "/sys/class/net/wlan0/statistics/rxBytes");
            assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink,
                    "/sys/class/net/wlan0/Statistics/rx_bytes");
            assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink,
                    "/sys/class/net/Wlan0/statistics/rx_bytes");
            assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink,
                    "/sys/class/net/wlan0/statistics/rx_byte");
            assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink,
                    "/sys/class/net/wlan0/address");

            for (int i = 0; i < UNSUPPORTED_KERNEL_FIELDS.length; i++) {
                assertNotTakenOverAsConfiguredSysfs(emulator, blocks, sink,
                        sysPath("wlan0", UNSUPPORTED_KERNEL_FIELDS[i]));
            }

            assertEquals(0, countAllStatsReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    private static String sysPath(String iface, String field) {
        return "/sys/class/net/" + iface + "/statistics/" + field;
    }

    private static String decimalLf(long value) {
        return Long.toString(value) + "\n";
    }

    private static void assertExactSingleLf(String text) {
        assertTrue(text.endsWith("\n"));
        assertEquals(1, countNewlines(text));
        String number = text.substring(0, text.length() - 1);
        assertEquals(number, Long.toString(Long.parseLong(number)));
        assertFalse(number.startsWith("+"));
        if (!"0".equals(number)) {
            assertFalse(number.startsWith("0"));
        }
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

    private static void assertArrayEqualsBytes(byte[] expected, byte[] actual) {
        assertEquals(new String(expected, StandardCharsets.UTF_8),
                new String(actual, StandardCharsets.UTF_8));
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("byte[" + i + "]", expected[i], actual[i]);
        }
    }

    private static void assertStatsSidecar(CapturedEvent e, String path, String name,
                                           String field, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",name=" + name + ",format=statistics-" + field
                + ",bytes=" + bytes, String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains("读取"));
        assertTrue(e.note.contains("统计"));
        assertTrue(e.note.contains(path));
    }

    private static void assertSidecarRedacted(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains("rxBytes"));
        assertFalse(note.contains("rxBytes"));
        assertFalse(value.contains("txBytes"));
        assertFalse(note.contains("txBytes"));
        assertFalse(value.contains("interfaceName"));
        assertFalse(note.contains("interfaceName"));
        assertFalse(value.contains("4096001"));
        assertFalse(note.contains("4096001"));
        assertFalse(value.contains("2048001"));
        assertFalse(note.contains("2048001"));
        assertFalse(value.contains("32001"));
        assertFalse(note.contains("32001"));
        assertFalse(value.contains("16001"));
        assertFalse(note.contains("16001"));
        assertFalse(value.contains("192.168.50"));
        assertFalse(note.contains("192.168.50"));
        assertFalse(value.contains("127.0.0.1"));
        assertFalse(note.contains("127.0.0.1"));
        assertFalse(value.contains("10.0.0.2"));
        assertFalse(note.contains("10.0.0.2"));
        assertFalse(value.contains("02:"));
        assertFalse(note.contains("02:"));
        assertFalse(value.contains("flags="));
        assertFalse(note.contains("flags="));
        assertFalse(value.contains("format=proc-net-dev"));
        assertFalse(value.contains("format=address"));
        assertFalse(value.contains("format=mtu"));
    }

    /**
     * Not-taken-over: this feature must not serve configured auto-file bytes and must
     * not emit its sidecar. Open success via legacy fallback is ignored — it is not
     * treated as proof that the feature handled the path.
     */
    private static void assertNotTakenOverAsConfiguredSysfs(AndroidEmulator emulator,
                                                            List<MemoryBlock> blocks,
                                                            CapturingSink sink,
                                                            String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            for (int i = 0; i < KERNEL_FIELDS.length; i++) {
                String wlanText = decimalLf(WLAN_VALUES[i]);
                String loText = decimalLf(LO_VALUES[i]);
                assertFalse(path + " must not serve configured wlan0 " + KERNEL_FIELDS[i],
                        wlanText.equals(text));
                if (LO_VALUES[i] != 0L) {
                    assertFalse(path + " must not serve configured lo " + KERNEL_FIELDS[i],
                            loText.equals(text));
                }
            }
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected interfaceStats sysfs sidecar for " + path,
                    isAnyStatsRead(e, path));
        }
    }

    private static void assertWriteOpenNotTakenOver(AndroidEmulator emulator,
                                                    List<MemoryBlock> blocks,
                                                    CapturingSink sink,
                                                    String path, int oflags) throws Exception {
        FileResult<AndroidFileIO> result;
        try {
            result = emulator.getFileSystem().open(path, oflags);
        } catch (RuntimeException ignored) {
            return;
        }
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            for (int i = 0; i < KERNEL_FIELDS.length; i++) {
                assertFalse("write/directory open must not serve configured sysfs stats",
                        decimalLf(WLAN_VALUES[i]).equals(text));
                if (LO_VALUES[i] != 0L) {
                    assertFalse("write/directory open must not serve configured lo sysfs stats",
                            decimalLf(LO_VALUES[i]).equals(text));
                }
            }
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isAnyStatsRead(e, path));
        }
    }

    private static void closeEmulator(AndroidEmulator emulator, CapturingSink sink,
                                      List<MemoryBlock> blocks) throws Exception {
        try {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static String readOpenText(AndroidEmulator emulator, List<MemoryBlock> blocks, String path)
            throws Exception {
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

    private static int countStatsReads(CapturingSink sink, String path) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isAnyStatsRead(e, path)) {
                n++;
            }
        }
        return n;
    }

    private static int countAllStatsReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if ("network_device".equals(e.kind)
                    && String.valueOf(e.value).contains("format=statistics-")) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findStatsRead(CapturingSink sink, String path, String field) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isStatsRead(e, path, field)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isStatsRead(CapturedEvent e, String path, String field) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=statistics-" + field);
    }

    private static boolean isAnyStatsRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=statistics-");
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
