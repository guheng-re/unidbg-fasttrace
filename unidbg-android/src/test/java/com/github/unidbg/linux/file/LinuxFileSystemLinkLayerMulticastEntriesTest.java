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
 * LinuxFileSystem wiring for {@code network.linkLayerMulticastEntries} → exact
 * {@code /proc/net/dev_mcast}, {@code /proc/self/net/dev_mcast}, and
 * {@code /proc/<emulatorPid>/net/dev_mcast}. Default backend (no Unicorn2).
 * Every emulator is closed in {@code finally} without swallowing {@code close}.
 */
public class LinuxFileSystemLinkLayerMulticastEntriesTest {

    private static final int CONFIG_PID = 4242;
    private static final String PROC_NET_DEV_MCAST = "/proc/net/dev_mcast";
    private static final String PROC_SELF_NET_DEV_MCAST = "/proc/self/net/dev_mcast";
    private static final String PROC_PID_NET_DEV_MCAST = "/proc/" + CONFIG_PID + "/net/dev_mcast";

    private static final String LO_IFACE_JSON =
            "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                    + "\"mac\":\"00:00:00:00:00:00\",\"linkLayerBroadcast\":\"ff:ff:ff:ff:ff:ff\"}";
    private static final String WLAN_IFACE_JSON =
            "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\","
                    + "\"mac\":\"02:54:52:41:43:45\",\"linkLayerBroadcast\":\"ff:ff:ff:ff:ff:ff\"}";

    private static final String LO_ALLHOSTS_JSON =
            "{\"interfaceName\":\"lo\",\"mac\":\"01:00:5e:00:00:01\","
                    + "\"referenceCount\":1,\"globalUse\":false}";
    private static final String WLAN_ALLHOSTS_JSON =
            "{\"interfaceName\":\"wlan0\",\"mac\":\"01:00:5e:00:00:01\","
                    + "\"referenceCount\":1,\"globalUse\":false}";
    private static final String WLAN_MDNS_JSON =
            "{\"interfaceName\":\"wlan0\",\"mac\":\"01:00:5e:00:00:fb\","
                    + "\"referenceCount\":2,\"globalUse\":true}";

    private static final String THREE_ENTRIES_JSON = "{"
            + "\"process\":{\"pid\":" + CONFIG_PID + "},"
            + "\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "," + WLAN_IFACE_JSON + "],"
            + "\"linkLayerMulticastEntries\":[" + LO_ALLHOSTS_JSON + ","
            + WLAN_ALLHOSTS_JSON + "," + WLAN_MDNS_JSON + "]}}";

    private static final String EXPECTED_TABLE =
            "1    lo              1     0     01005e000001\n"
                    + "2    wlan0           1     0     01005e000001\n"
                    + "2    wlan0           2     1     01005e0000fb\n";

    @Test
    public void testParseValidAndInvalid() {
        TraceEnvironmentConfig three = TraceEnvironmentConfig.parse(THREE_ENTRIES_JSON);
        assertTrue(three.isNetworkLinkLayerMulticastEntriesConfigured());
        assertEquals(3, three.getNetworkLinkLayerMulticastEntries().size());
        TraceEnvironmentConfig.NetworkLinkLayerMulticastEntryConfig lo =
                three.getNetworkLinkLayerMulticastEntries().get(0);
        assertEquals("lo", lo.getInterfaceName());
        assertEquals("01:00:5e:00:00:01", lo.getMac());
        assertEquals(1, lo.getReferenceCount());
        assertFalse(lo.isGlobalUse());
        TraceEnvironmentConfig.NetworkLinkLayerMulticastEntryConfig wlanAll =
                three.getNetworkLinkLayerMulticastEntries().get(1);
        assertEquals("wlan0", wlanAll.getInterfaceName());
        assertEquals("01:00:5e:00:00:01", wlanAll.getMac());
        assertFalse(wlanAll.isGlobalUse());
        TraceEnvironmentConfig.NetworkLinkLayerMulticastEntryConfig mdns =
                three.getNetworkLinkLayerMulticastEntries().get(2);
        assertEquals("01:00:5e:00:00:fb", mdns.getMac());
        assertEquals(2, mdns.getReferenceCount());
        assertTrue(mdns.isGlobalUse());

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"network\":{\"linkLayerMulticastEntries\":[]}}");
        assertTrue(empty.isNetworkLinkLayerMulticastEntriesConfigured());
        assertTrue(empty.getNetworkLinkLayerMulticastEntries().isEmpty());

        assertFalse(TraceEnvironmentConfig.parse("{}").isNetworkLinkLayerMulticastEntriesConfigured());
        assertFalse(TraceEnvironmentConfig.parse("{\"network\":{}}")
                .isNetworkLinkLayerMulticastEntriesConfigured());
        assertFalse(TraceEnvironmentConfig.parse(
                "{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "]}}")
                .isNetworkLinkLayerMulticastEntriesConfigured());

        TraceEnvironmentConfig bounds = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "," + WLAN_IFACE_JSON + "],"
                + "\"linkLayerMulticastEntries\":["
                + "{\"interfaceName\":\"lo\",\"mac\":\"01:00:5E:00:00:01\","
                + "\"referenceCount\":1,\"globalUse\":false},"
                + "{\"interfaceName\":\"wlan0\",\"mac\":\"33:33:00:00:00:01\","
                + "\"referenceCount\":2147483647,\"globalUse\":true}]}}");
        assertEquals("01:00:5e:00:00:01", bounds.getNetworkLinkLayerMulticastEntries().get(0).getMac());
        assertEquals(1, bounds.getNetworkLinkLayerMulticastEntries().get(0).getReferenceCount());
        assertFalse(bounds.getNetworkLinkLayerMulticastEntries().get(0).isGlobalUse());
        assertEquals("33:33:00:00:00:01", bounds.getNetworkLinkLayerMulticastEntries().get(1).getMac());
        assertEquals(2147483647, bounds.getNetworkLinkLayerMulticastEntries().get(1).getReferenceCount());
        assertTrue(bounds.getNetworkLinkLayerMulticastEntries().get(1).isGlobalUse());

        assertInvalid("{\"network\":{\"linkLayerMulticastEntries\":{}}}",
                "network.linkLayerMulticastEntries");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"linkLayerMulticastEntries\":[\"wlan0\"]}}",
                "network.linkLayerMulticastEntries[0]");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"linkLayerMulticastEntries\":[1]}}",
                "network.linkLayerMulticastEntries[0]");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"linkLayerMulticastEntries\":[null]}}",
                "network.linkLayerMulticastEntries[0]");
        assertInvalid("{\"network\":{\"linkLayerMulticastEntries\":[" + WLAN_MDNS_JSON + "]}}",
                "network.linkLayerMulticastEntries[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"linkLayerMulticastEntries\":[{\"interfaceName\":\"eth0\","
                + "\"mac\":\"01:00:5e:00:00:01\",\"referenceCount\":1,\"globalUse\":false}]}}",
                "network.linkLayerMulticastEntries[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\"},"
                + "{\"name\":\"wlan0\",\"index\":3,\"ipv4\":\"10.0.0.2\"}],"
                + "\"linkLayerMulticastEntries\":[]}}",
                "network.interfaces[1].name");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"linkLayerMulticastEntries\":[" + WLAN_MDNS_JSON + "," + WLAN_MDNS_JSON + "]}}",
                "network.linkLayerMulticastEntries[1].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"linkLayerMulticastEntries\":["
                + "{\"interfaceName\":\"wlan0\",\"mac\":\"01:00:5e:00:00:01\","
                + "\"referenceCount\":1,\"globalUse\":false},"
                + "{\"interfaceName\":\"wlan0\",\"mac\":\"01:00:5E:00:00:01\","
                + "\"referenceCount\":2,\"globalUse\":true}]}}",
                "network.linkLayerMulticastEntries[1].interfaceName");
        assertInvalid(entryWith("\"extra\":1"), "network.linkLayerMulticastEntries[0].extra");
        assertInvalid(entryMissing("interfaceName"),
                "network.linkLayerMulticastEntries[0].interfaceName");
        assertInvalid(entryMissing("mac"), "network.linkLayerMulticastEntries[0].mac");
        assertInvalid(entryMissing("referenceCount"),
                "network.linkLayerMulticastEntries[0].referenceCount");
        assertInvalid(entryMissing("globalUse"),
                "network.linkLayerMulticastEntries[0].globalUse");
        assertInvalid(entryField("mac", "\"01-00-5e-00-00-01\""),
                "network.linkLayerMulticastEntries[0].mac");
        assertInvalid(entryField("mac", "\" 01:00:5e:00:00:01\""),
                "network.linkLayerMulticastEntries[0].mac");
        assertInvalid(entryField("mac", "\"01:00:5e:00:00\""),
                "network.linkLayerMulticastEntries[0].mac");
        assertInvalid(entryField("mac", "\"01005e000001\""),
                "network.linkLayerMulticastEntries[0].mac");
        assertInvalid(entryField("mac", "\"192.168.1.255\""),
                "network.linkLayerMulticastEntries[0].mac");
        assertInvalid(entryField("mac", "\"\""), "network.linkLayerMulticastEntries[0].mac");
        assertInvalid(entryField("mac", "true"), "network.linkLayerMulticastEntries[0].mac");
        assertInvalid(entryField("mac", "1"), "network.linkLayerMulticastEntries[0].mac");
        assertInvalid(entryField("mac", "null"), "network.linkLayerMulticastEntries[0].mac");
        assertInvalid(entryField("referenceCount", "0"),
                "network.linkLayerMulticastEntries[0].referenceCount");
        assertInvalid(entryField("referenceCount", "-1"),
                "network.linkLayerMulticastEntries[0].referenceCount");
        assertInvalid(entryField("referenceCount", "2147483648"),
                "network.linkLayerMulticastEntries[0].referenceCount");
        assertInvalid(entryField("referenceCount", "1.5"),
                "network.linkLayerMulticastEntries[0].referenceCount");
        assertInvalid(entryField("referenceCount", "\"1\""),
                "network.linkLayerMulticastEntries[0].referenceCount");
        assertInvalid(entryField("referenceCount", "true"),
                "network.linkLayerMulticastEntries[0].referenceCount");
        assertInvalid(entryField("referenceCount", "null"),
                "network.linkLayerMulticastEntries[0].referenceCount");
        assertInvalid(entryField("globalUse", "1"),
                "network.linkLayerMulticastEntries[0].globalUse");
        assertInvalid(entryField("globalUse", "0"),
                "network.linkLayerMulticastEntries[0].globalUse");
        assertInvalid(entryField("globalUse", "\"true\""),
                "network.linkLayerMulticastEntries[0].globalUse");
        assertInvalid(entryField("globalUse", "1.0"),
                "network.linkLayerMulticastEntries[0].globalUse");
        assertInvalid(entryField("globalUse", "null"),
                "network.linkLayerMulticastEntries[0].globalUse");
        assertInvalid(entryField("interfaceName", "1"),
                "network.linkLayerMulticastEntries[0].interfaceName");
        assertInvalid(entryField("interfaceName", "\"\""),
                "network.linkLayerMulticastEntries[0].interfaceName");
    }

    @Test
    public void testFullTableBytesAndThreeAliases() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(THREE_ENTRIES_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            assertEquals(CONFIG_PID, emulator.getPid());
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] expected = EXPECTED_TABLE.getBytes(StandardCharsets.UTF_8);
            assertEquals(EXPECTED_TABLE, new String(expected, StandardCharsets.UTF_8));
            assertTrue(EXPECTED_TABLE.startsWith("1    lo              1     0     01005e000001\n"));
            assertTrue(EXPECTED_TABLE.contains("2    wlan0           1     0     01005e000001\n"));
            assertTrue(EXPECTED_TABLE.contains("2    wlan0           2     1     01005e0000fb\n"));
            assertFalse(EXPECTED_TABLE.contains("Device"));
            assertFalse(EXPECTED_TABLE.contains("01:00:5e"));
            assertTrue(EXPECTED_TABLE.endsWith("\n"));
            assertFalse(EXPECTED_TABLE.contains("\r"));
            assertFalse(EXPECTED_TABLE.contains("\t"));

            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_NET_DEV_MCAST));
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_SELF_NET_DEV_MCAST));
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_PID_NET_DEV_MCAST));

            assertEquals(3, countDevMcastReads(sink));
            CapturedEvent e0 = findDevMcastRead(sink, PROC_NET_DEV_MCAST);
            assertDevMcastSidecar(e0, PROC_NET_DEV_MCAST, 3, 2, expected.length);
            assertSidecarRedacted(e0);
            assertDevMcastSidecar(findDevMcastRead(sink, PROC_SELF_NET_DEV_MCAST),
                    PROC_SELF_NET_DEV_MCAST, 3, 2, expected.length);
            assertSidecarRedacted(findDevMcastRead(sink, PROC_SELF_NET_DEV_MCAST));
            assertDevMcastSidecar(findDevMcastRead(sink, PROC_PID_NET_DEV_MCAST),
                    PROC_PID_NET_DEV_MCAST, 3, 2, expected.length);
            assertSidecarRedacted(findDevMcastRead(sink, PROC_PID_NET_DEV_MCAST));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testExplicitEmptyArrayZeroBytes() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"process\":{\"pid\":" + CONFIG_PID + "},"
                        + "\"network\":{\"linkLayerMulticastEntries\":[]}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            byte[] expected = new byte[0];
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_NET_DEV_MCAST));
            assertEquals("", readOpenText(emulator, blocks, PROC_SELF_NET_DEV_MCAST));
            assertEquals(0, readOpenBytes(emulator, blocks, PROC_PID_NET_DEV_MCAST).length);
            CapturedEvent e = findDevMcastRead(sink, PROC_NET_DEV_MCAST);
            assertDevMcastSidecar(e, PROC_NET_DEV_MCAST, 0, 0, 0);
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
            assertNotTakenOverAsConfiguredDevMcast(emulator, blocks, sink, PROC_NET_DEV_MCAST);
            assertNotTakenOverAsConfiguredDevMcast(emulator, blocks, sink, PROC_SELF_NET_DEV_MCAST);
            assertEquals(0, countDevMcastReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredDevMcast(emulator, blocks, sink, PROC_NET_DEV_MCAST);
            assertEquals(0, countDevMcastReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesTakesPriority() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"files\":{\"" + PROC_NET_DEV_MCAST + "\":\"CUSTOM_DEV_MCAST\\n\"}},"
                + THREE_ENTRIES_JSON.substring(THREE_ENTRIES_JSON.indexOf("\"network\"")));
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals("CUSTOM_DEV_MCAST\n", readOpenText(emulator, blocks, PROC_NET_DEV_MCAST));
            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                if (isDevMcastRead(e, PROC_NET_DEV_MCAST)) {
                    fail("network_device linkLayerMulticastEntries sidecar must not fire when linux.files wins");
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
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(THREE_ENTRIES_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_DEV_MCAST, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_DEV_MCAST, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_DEV_MCAST, IOConstants.O_DIRECTORY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_SELF_NET_DEV_MCAST,
                    IOConstants.O_RDONLY | IOConstants.O_DIRECTORY);

            assertNotTakenOverAsConfiguredDevMcast(emulator, blocks, sink, "/proc/net/dev_mcasts");
            assertNotTakenOverAsConfiguredDevMcast(emulator, blocks, sink, "/proc/net/dev_mcast/");
            assertNotTakenOverAsConfiguredDevMcast(emulator, blocks, sink, "/proc/net/dev");
            assertNotTakenOverAsConfiguredDevMcast(emulator, blocks, sink, "/proc/net/igmp6");
            assertNotTakenOverAsConfiguredDevMcast(emulator, blocks, sink, "/proc/self/net/dev_mcasts");
            assertNotTakenOverAsConfiguredDevMcast(emulator, blocks, sink, "/proc/1/net/dev_mcast");
            assertNotTakenOverAsConfiguredDevMcast(emulator, blocks, sink, "/proc/net/Dev_mcast");
            assertEquals(0, countDevMcastReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    private static String entryWith(String extraField) {
        return "{"
                + "\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"linkLayerMulticastEntries\":[{"
                + "\"interfaceName\":\"wlan0\",\"mac\":\"01:00:5e:00:00:fb\","
                + "\"referenceCount\":1,\"globalUse\":false,"
                + extraField + "}]}}";
    }

    private static String entryMissing(String field) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"network\":{\"interfaces\":[").append(WLAN_IFACE_JSON).append("],");
        sb.append("\"linkLayerMulticastEntries\":[{");
        boolean first = true;
        String[] keys = {
                "interfaceName", "mac", "referenceCount", "globalUse"
        };
        String[] values = {
                "\"wlan0\"", "\"01:00:5e:00:00:fb\"", "1", "false"
        };
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

    private static String entryField(String field, String rawValue) {
        String[] keys = {
                "interfaceName", "mac", "referenceCount", "globalUse"
        };
        String[] values = {
                "\"wlan0\"", "\"01:00:5e:00:00:fb\"", "1", "false"
        };
        StringBuilder sb = new StringBuilder();
        sb.append("{\"network\":{\"interfaces\":[").append(WLAN_IFACE_JSON).append("],");
        sb.append("\"linkLayerMulticastEntries\":[{");
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

    private static void assertDevMcastSidecar(CapturedEvent e, String path,
                                              int entryCount, int interfaceCount, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",format=proc-net-dev-mcast,entryCount=" + entryCount
                + ",interfaceCount=" + interfaceCount + ",bytes=" + bytes, String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains("读取"));
        assertTrue(e.note.contains("链路层多播"));
        assertTrue(e.note.contains(path));
    }

    private static void assertSidecarRedacted(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains("interfaceName"));
        assertFalse(note.contains("interfaceName"));
        assertFalse(value.contains("wlan0"));
        assertFalse(note.contains("wlan0"));
        assertFalse(value.contains("01005e"));
        assertFalse(note.contains("01005e"));
        assertFalse(value.contains("01:00:5e"));
        assertFalse(note.contains("01:00:5e"));
        assertFalse(value.contains("referenceCount"));
        assertFalse(note.contains("referenceCount"));
        assertFalse(value.contains("globalUse"));
        assertFalse(note.contains("globalUse"));
        assertFalse(value.contains("index="));
        assertFalse(note.contains("index="));
        assertFalse(value.contains("mac="));
        assertFalse(note.contains("mac="));
    }

    private static void assertNotTakenOverAsConfiguredDevMcast(AndroidEmulator emulator,
                                                               List<MemoryBlock> blocks,
                                                               CapturingSink sink,
                                                               String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 4096);
            assertFalse(path + " must not serve configured linkLayerMulticastEntries table",
                    EXPECTED_TABLE.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected dev_mcast sidecar for " + path, isDevMcastRead(e, path));
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
            assertFalse("write/directory open must not serve configured linkLayerMulticastEntries",
                    EXPECTED_TABLE.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isDevMcastRead(e, path));
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

    private static int countDevMcastReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isDevMcastRead(e, PROC_NET_DEV_MCAST)
                    || isDevMcastRead(e, PROC_SELF_NET_DEV_MCAST)
                    || isDevMcastRead(e, PROC_PID_NET_DEV_MCAST)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findDevMcastRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isDevMcastRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isDevMcastRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=proc-net-dev-mcast");
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
