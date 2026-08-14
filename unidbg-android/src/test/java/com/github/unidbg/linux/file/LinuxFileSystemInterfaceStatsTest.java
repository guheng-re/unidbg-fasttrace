package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.ConfiguredInterfaceStatsFiles;
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
 * {@code /proc/net/dev}, {@code /proc/self/net/dev}, and
 * {@code /proc/<emulatorPid>/net/dev}. Default backend (no Unicorn2).
 * Every emulator is closed in {@code finally} without swallowing {@code close}.
 */
public class LinuxFileSystemInterfaceStatsTest {

    private static final int CONFIG_PID = 4242;
    private static final String PROC_NET_DEV = "/proc/net/dev";
    private static final String PROC_SELF_NET_DEV = "/proc/self/net/dev";
    private static final String PROC_PID_NET_DEV = "/proc/" + CONFIG_PID + "/net/dev";

    private static final String LO_IFACE_JSON =
            "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"}";
    private static final String WLAN_IFACE_JSON =
            "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\"}";

    private static final String LO_STATS_JSON =
            "{\"interfaceName\":\"lo\",\"rxBytes\":1280,\"rxPackets\":10,"
                    + "\"rxErrors\":0,\"rxDrop\":0,\"rxFifo\":0,\"rxFrame\":0,"
                    + "\"rxCompressed\":0,\"rxMulticast\":0,\"txBytes\":1280,"
                    + "\"txPackets\":10,\"txErrors\":0,\"txDrop\":0,\"txFifo\":0,"
                    + "\"txCollisions\":0,\"txCarrier\":0,\"txCompressed\":0}";

    private static final String WLAN_STATS_JSON =
            "{\"interfaceName\":\"wlan0\",\"rxBytes\":4096000,\"rxPackets\":3200,"
                    + "\"rxErrors\":0,\"rxDrop\":0,\"rxFifo\":0,\"rxFrame\":0,"
                    + "\"rxCompressed\":0,\"rxMulticast\":24,\"txBytes\":2048000,"
                    + "\"txPackets\":1600,\"txErrors\":0,\"txDrop\":0,\"txFifo\":0,"
                    + "\"txCollisions\":0,\"txCarrier\":0,\"txCompressed\":0}";

    private static final String TWO_STATS_JSON = "{"
            + "\"process\":{\"pid\":" + CONFIG_PID + "},"
            + "\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "," + WLAN_IFACE_JSON + "],"
            + "\"interfaceStats\":[" + LO_STATS_JSON + "," + WLAN_STATS_JSON + "]}}";

    private static final String EXPECTED_TABLE =
            ConfiguredInterfaceStatsFiles.HEADER
                    + "    lo:    1280      10    0    0    0     0          0         0     1280      10    0    0    0     0       0          0\n"
                    + " wlan0: 4096000    3200    0    0    0     0          0        24  2048000    1600    0    0    0     0       0          0\n";

    @Test
    public void testParseValidAndInvalid() {
        TraceEnvironmentConfig two = TraceEnvironmentConfig.parse(TWO_STATS_JSON);
        assertTrue(two.isNetworkInterfaceStatsConfigured());
        assertEquals(2, two.getNetworkInterfaceStats().size());
        TraceEnvironmentConfig.NetworkInterfaceStatsConfig lo = two.getNetworkInterfaceStats().get(0);
        assertEquals("lo", lo.getInterfaceName());
        assertEquals(1280L, lo.getRxBytes());
        assertEquals(10L, lo.getRxPackets());
        assertEquals(0L, lo.getRxErrors());
        assertEquals(0L, lo.getRxDrop());
        assertEquals(0L, lo.getRxFifo());
        assertEquals(0L, lo.getRxFrame());
        assertEquals(0L, lo.getRxCompressed());
        assertEquals(0L, lo.getRxMulticast());
        assertEquals(1280L, lo.getTxBytes());
        assertEquals(10L, lo.getTxPackets());
        assertEquals(0L, lo.getTxErrors());
        assertEquals(0L, lo.getTxDrop());
        assertEquals(0L, lo.getTxFifo());
        assertEquals(0L, lo.getTxCollisions());
        assertEquals(0L, lo.getTxCarrier());
        assertEquals(0L, lo.getTxCompressed());
        TraceEnvironmentConfig.NetworkInterfaceStatsConfig wlan = two.getNetworkInterfaceStats().get(1);
        assertEquals("wlan0", wlan.getInterfaceName());
        assertEquals(4096000L, wlan.getRxBytes());
        assertEquals(3200L, wlan.getRxPackets());
        assertEquals(24L, wlan.getRxMulticast());
        assertEquals(2048000L, wlan.getTxBytes());
        assertEquals(1600L, wlan.getTxPackets());

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{\"network\":{\"interfaceStats\":[]}}");
        assertTrue(empty.isNetworkInterfaceStatsConfigured());
        assertTrue(empty.getNetworkInterfaceStats().isEmpty());

        assertFalse(TraceEnvironmentConfig.parse("{}").isNetworkInterfaceStatsConfigured());
        assertFalse(TraceEnvironmentConfig.parse("{\"network\":{}}").isNetworkInterfaceStatsConfigured());
        assertFalse(TraceEnvironmentConfig.parse(
                "{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "]}}")
                .isNetworkInterfaceStatsConfigured());

        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"interfaceStats\":[{\"interfaceName\":\"wlan0\",\"rxBytes\":9223372036854775807,"
                + "\"rxPackets\":0,\"rxErrors\":0,\"rxDrop\":0,\"rxFifo\":0,\"rxFrame\":0,"
                + "\"rxCompressed\":0,\"rxMulticast\":0,\"txBytes\":0,\"txPackets\":0,"
                + "\"txErrors\":0,\"txDrop\":0,\"txFifo\":0,\"txCollisions\":0,\"txCarrier\":0,"
                + "\"txCompressed\":0}]}}");
        assertEquals(9223372036854775807L, max.getNetworkInterfaceStats().get(0).getRxBytes());

        assertInvalid("{\"network\":{\"interfaceStats\":{}}}", "network.interfaceStats");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"interfaceStats\":[\"wlan0\"]}}", "network.interfaceStats[0]");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"interfaceStats\":[1]}}", "network.interfaceStats[0]");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"interfaceStats\":[null]}}", "network.interfaceStats[0]");
        assertInvalid("{\"network\":{\"interfaceStats\":[" + LO_STATS_JSON + "]}}",
                "network.interfaceStats[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"interfaceStats\":[{\"interfaceName\":\"eth0\",\"rxBytes\":0,\"rxPackets\":0,"
                + "\"rxErrors\":0,\"rxDrop\":0,\"rxFifo\":0,\"rxFrame\":0,\"rxCompressed\":0,"
                + "\"rxMulticast\":0,\"txBytes\":0,\"txPackets\":0,\"txErrors\":0,\"txDrop\":0,"
                + "\"txFifo\":0,\"txCollisions\":0,\"txCarrier\":0,\"txCompressed\":0}]}}",
                "network.interfaceStats[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\"},"
                + "{\"name\":\"wlan0\",\"index\":3,\"ipv4\":\"10.0.0.2\"}],"
                + "\"interfaceStats\":[]}}",
                "network.interfaces[1].name");
        assertInvalid("{\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "],"
                + "\"interfaceStats\":[" + LO_STATS_JSON + "," + LO_STATS_JSON + "]}}",
                "network.interfaceStats[1].interfaceName");
        assertInvalid(statsWith("\"extra\":1"), "network.interfaceStats[0].extra");
        assertInvalid(statsMissing("rxBytes"), "network.interfaceStats[0].rxBytes");
        assertInvalid(statsMissing("txCompressed"), "network.interfaceStats[0].txCompressed");
        assertInvalid(statsField("rxBytes", "-1"), "network.interfaceStats[0].rxBytes");
        assertInvalid(statsField("rxBytes", "1.5"), "network.interfaceStats[0].rxBytes");
        assertInvalid(statsField("rxBytes", "\"3\""), "network.interfaceStats[0].rxBytes");
        assertInvalid(statsField("rxBytes", "true"), "network.interfaceStats[0].rxBytes");
        assertInvalid(statsField("rxBytes", "false"), "network.interfaceStats[0].rxBytes");
        assertInvalid(statsField("rxBytes", "null"), "network.interfaceStats[0].rxBytes");
        assertInvalid(statsField("rxBytes", "9223372036854775808"),
                "network.interfaceStats[0].rxBytes");
        assertInvalid(statsField("txPackets", "-1"), "network.interfaceStats[0].txPackets");
        assertInvalid(statsField("interfaceName", "1"), "network.interfaceStats[0].interfaceName");
    }

    @Test
    public void testFullTableBytesAndThreeAliases() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_STATS_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            assertEquals(CONFIG_PID, emulator.getPid());
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] expected = EXPECTED_TABLE.getBytes(StandardCharsets.UTF_8);
            assertEquals(EXPECTED_TABLE, new String(expected, StandardCharsets.UTF_8));
            assertTrue(EXPECTED_TABLE.startsWith(ConfiguredInterfaceStatsFiles.HEADER));
            int loPos = EXPECTED_TABLE.indexOf("lo:");
            int wlanPos = EXPECTED_TABLE.indexOf("wlan0:");
            assertTrue(loPos > 0);
            assertTrue(wlanPos > loPos);
            assertTrue(EXPECTED_TABLE.contains("1280"));
            assertTrue(EXPECTED_TABLE.contains("4096000"));
            assertTrue(EXPECTED_TABLE.contains("2048000"));
            assertTrue(EXPECTED_TABLE.endsWith("\n"));
            assertFalse(EXPECTED_TABLE.contains("\r"));

            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_NET_DEV));
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_SELF_NET_DEV));
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_PID_NET_DEV));

            assertEquals(3, countDevReads(sink));
            CapturedEvent e0 = findDevRead(sink, PROC_NET_DEV);
            assertDevSidecar(e0, PROC_NET_DEV, 2, expected.length);
            assertSidecarRedacted(e0);
            assertDevSidecar(findDevRead(sink, PROC_SELF_NET_DEV),
                    PROC_SELF_NET_DEV, 2, expected.length);
            assertSidecarRedacted(findDevRead(sink, PROC_SELF_NET_DEV));
            assertDevSidecar(findDevRead(sink, PROC_PID_NET_DEV),
                    PROC_PID_NET_DEV, 2, expected.length);
            assertSidecarRedacted(findDevRead(sink, PROC_PID_NET_DEV));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testExplicitEmptyArrayHeaderOnly() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"process\":{\"pid\":" + CONFIG_PID + "},\"network\":{\"interfaceStats\":[]}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            byte[] expected = ConfiguredInterfaceStatsFiles.HEADER.getBytes(StandardCharsets.UTF_8);
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_NET_DEV));
            assertEquals(ConfiguredInterfaceStatsFiles.HEADER,
                    readOpenText(emulator, blocks, PROC_SELF_NET_DEV));
            assertTrue(ConfiguredInterfaceStatsFiles.HEADER.endsWith("\n"));
            assertFalse(ConfiguredInterfaceStatsFiles.HEADER.contains("wlan0"));
            assertFalse(ConfiguredInterfaceStatsFiles.HEADER.contains("lo:"));
            CapturedEvent e = findDevRead(sink, PROC_NET_DEV);
            assertDevSidecar(e, PROC_NET_DEV, 0, expected.length);
            assertSidecarRedacted(e);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testMissingNodeDoesNotTakeOver() throws Exception {
        TraceEnvironmentConfig interfacesOnly = TraceEnvironmentConfig.parse(
                "{\"process\":{\"pid\":" + CONFIG_PID + "},"
                        + "\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "]}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit()
                    .setEnvironmentConfig(interfacesOnly).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredStats(emulator, blocks, sink, PROC_NET_DEV);
            assertNotTakenOverAsConfiguredStats(emulator, blocks, sink, PROC_SELF_NET_DEV);
            assertEquals(0, countDevReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredStats(emulator, blocks, sink, PROC_NET_DEV);
            assertEquals(0, countDevReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesTakesPriority() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"files\":{\"" + PROC_NET_DEV + "\":\"CUSTOM_DEV\\n\"}},"
                + TWO_STATS_JSON.substring(TWO_STATS_JSON.indexOf("\"network\"")));
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals("CUSTOM_DEV\n", readOpenText(emulator, blocks, PROC_NET_DEV));
            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                if (isDevRead(e, PROC_NET_DEV)) {
                    fail("network_device interfaceStats sidecar must not fire when linux.files wins");
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
    public void testWriteDirectoryAndNearPathsNotTakenOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_STATS_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_DEV, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_DEV, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_DEV, IOConstants.O_DIRECTORY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_SELF_NET_DEV,
                    IOConstants.O_RDONLY | IOConstants.O_DIRECTORY);

            assertNotTakenOverAsConfiguredStats(emulator, blocks, sink, "/proc/net/devs");
            assertNotTakenOverAsConfiguredStats(emulator, blocks, sink, "/proc/net/dev/");
            assertNotTakenOverAsConfiguredStats(emulator, blocks, sink, "/proc/net/arp");
            assertNotTakenOverAsConfiguredStats(emulator, blocks, sink, "/proc/net/route");
            assertNotTakenOverAsConfiguredStats(emulator, blocks, sink, "/proc/self/net/devs");
            assertNotTakenOverAsConfiguredStats(emulator, blocks, sink, "/proc/1/net/dev");
            assertNotTakenOverAsConfiguredStats(emulator, blocks, sink, "/proc/net/Dev");
            assertNotTakenOverAsConfiguredStats(emulator, blocks, sink,
                    "/sys/class/net/wlan0/statistics/rx_bytes");
            assertEquals(0, countDevReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    private static String statsWith(String extraField) {
        return "{"
                + "\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"interfaceStats\":[{"
                + "\"interfaceName\":\"wlan0\",\"rxBytes\":0,\"rxPackets\":0,"
                + "\"rxErrors\":0,\"rxDrop\":0,\"rxFifo\":0,\"rxFrame\":0,"
                + "\"rxCompressed\":0,\"rxMulticast\":0,\"txBytes\":0,\"txPackets\":0,"
                + "\"txErrors\":0,\"txDrop\":0,\"txFifo\":0,\"txCollisions\":0,"
                + "\"txCarrier\":0,\"txCompressed\":0,"
                + extraField + "}]}}";
    }

    private static String statsMissing(String field) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"network\":{\"interfaces\":[").append(WLAN_IFACE_JSON).append("],");
        sb.append("\"interfaceStats\":[{");
        boolean first = true;
        String[] keys = STATS_KEYS;
        String[] values = STATS_ZERO_VALUES;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(field)) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(keys[i]).append("\":").append(values[i]);
        }
        sb.append("}]}}");
        return sb.toString();
    }

    private static String statsField(String field, String rawValue) {
        String[] keys = STATS_KEYS;
        String[] values = STATS_ZERO_VALUES;
        StringBuilder sb = new StringBuilder();
        sb.append("{\"network\":{\"interfaces\":[").append(WLAN_IFACE_JSON).append("],");
        sb.append("\"interfaceStats\":[{");
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(keys[i]).append("\":");
            sb.append(keys[i].equals(field) ? rawValue : values[i]);
        }
        sb.append("}]}}");
        return sb.toString();
    }

    private static final String[] STATS_KEYS = {
            "interfaceName", "rxBytes", "rxPackets", "rxErrors", "rxDrop", "rxFifo",
            "rxFrame", "rxCompressed", "rxMulticast", "txBytes", "txPackets", "txErrors",
            "txDrop", "txFifo", "txCollisions", "txCarrier", "txCompressed"
    };

    private static final String[] STATS_ZERO_VALUES = {
            "\"wlan0\"", "0", "0", "0", "0", "0",
            "0", "0", "0", "0", "0", "0",
            "0", "0", "0", "0", "0"
    };

    private static void assertInvalid(String json, String expectedPath) {
        try {
            TraceEnvironmentConfig.parse(json);
            fail("expected IllegalArgumentException containing path: " + expectedPath
                    + " for json: " + json);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            assertTrue("message missing path " + expectedPath + ", was: " + message,
                    message != null && message.contains(expectedPath));
        }
    }

    private static void assertArrayEqualsBytes(byte[] expected, byte[] actual) {
        assertEquals(new String(expected, StandardCharsets.UTF_8),
                new String(actual, StandardCharsets.UTF_8));
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("byte[" + i + "]", expected[i], actual[i]);
        }
    }

    private static void assertDevSidecar(CapturedEvent e, String path, int interfaceCount, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",format=proc-net-dev,interfaceCount=" + interfaceCount
                + ",bytes=" + bytes, String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains("读取"));
        assertTrue(e.note.contains(path));
    }

    private static void assertSidecarRedacted(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains("wlan0"));
        assertFalse(note.contains("wlan0"));
        assertFalse(value.contains("lo:"));
        assertFalse(note.contains("lo:"));
        assertFalse(value.contains("interfaceName"));
        assertFalse(note.contains("interfaceName"));
        assertFalse(value.contains("rxBytes"));
        assertFalse(note.contains("rxBytes"));
        assertFalse(value.contains("txBytes"));
        assertFalse(note.contains("txBytes"));
        assertFalse(value.contains("1280"));
        assertFalse(note.contains("1280"));
        assertFalse(value.contains("4096000"));
        assertFalse(note.contains("4096000"));
        assertFalse(value.contains("2048000"));
        assertFalse(note.contains("2048000"));
        assertFalse(value.contains("3200"));
        assertFalse(note.contains("3200"));
        assertFalse(value.contains("192.168.50"));
        assertFalse(note.contains("192.168.50"));
        assertFalse(value.contains("02:54:52"));
        assertFalse(note.contains("02:54:52"));
        assertFalse(value.contains("127.0.0.1"));
        assertFalse(note.contains("127.0.0.1"));
    }

    private static void assertNotTakenOverAsConfiguredStats(AndroidEmulator emulator,
                                                            List<MemoryBlock> blocks,
                                                            CapturingSink sink,
                                                            String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 4096);
            assertFalse(path + " must not serve configured interfaceStats table",
                    EXPECTED_TABLE.equals(text));
            assertFalse(path + " must not serve configured interfaceStats header-only",
                    ConfiguredInterfaceStatsFiles.HEADER.equals(text) && path.contains("dev"));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected interfaceStats sidecar for " + path, isDevRead(e, path));
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
            String text = readIoText(emulator, blocks, result.io, 4096);
            assertFalse("write/directory open must not serve configured interfaceStats",
                    EXPECTED_TABLE.equals(text)
                            || ConfiguredInterfaceStatsFiles.HEADER.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isDevRead(e, path));
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

    private static int countDevReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isDevRead(e, PROC_NET_DEV)
                    || isDevRead(e, PROC_SELF_NET_DEV)
                    || isDevRead(e, PROC_PID_NET_DEV)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findDevRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isDevRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isDevRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=proc-net-dev");
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
