package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.ConfiguredArpEntryFiles;
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
 * LinuxFileSystem wiring for {@code network.arpEntries} → exact
 * {@code /proc/net/arp}, {@code /proc/self/net/arp}, and
 * {@code /proc/<emulatorPid>/net/arp}. Default backend (no Unicorn2).
 * Every emulator is closed in {@code finally} without swallowing {@code close}.
 */
public class LinuxFileSystemArpEntriesTest {

    private static final int CONFIG_PID = 4242;
    private static final String PROC_NET_ARP = "/proc/net/arp";
    private static final String PROC_SELF_NET_ARP = "/proc/self/net/arp";
    private static final String PROC_PID_NET_ARP = "/proc/" + CONFIG_PID + "/net/arp";

    private static final String LO_IFACE_JSON =
            "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"}";
    private static final String WLAN_IFACE_JSON =
            "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\"}";

    private static final String GATEWAY_ARP_JSON =
            "{\"interfaceName\":\"wlan0\",\"ipv4\":\"192.168.50.1\","
                    + "\"hardwareType\":1,\"flags\":2,\"mac\":\"02:00:00:00:00:01\"}";
    private static final String SELF_ARP_JSON =
            "{\"interfaceName\":\"wlan0\",\"ipv4\":\"192.168.50.23\","
                    + "\"hardwareType\":1,\"flags\":2,\"mac\":\"02:54:52:41:43:45\"}";

    private static final String TWO_ENTRIES_JSON = "{"
            + "\"process\":{\"pid\":" + CONFIG_PID + "},"
            + "\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "," + WLAN_IFACE_JSON + "],"
            + "\"arpEntries\":[" + GATEWAY_ARP_JSON + "," + SELF_ARP_JSON + "]}}";

    private static final String EXPECTED_TABLE =
            ConfiguredArpEntryFiles.HEADER
                    + "192.168.50.1     0x1         0x2         02:00:00:00:00:01     *        wlan0\n"
                    + "192.168.50.23    0x1         0x2         02:54:52:41:43:45     *        wlan0\n";

    @Test
    public void testParseValidAndInvalid() {
        TraceEnvironmentConfig two = TraceEnvironmentConfig.parse(TWO_ENTRIES_JSON);
        assertTrue(two.isNetworkArpEntriesConfigured());
        assertEquals(2, two.getNetworkArpEntries().size());
        TraceEnvironmentConfig.NetworkArpEntryConfig gateway = two.getNetworkArpEntries().get(0);
        assertEquals("wlan0", gateway.getInterfaceName());
        assertEquals("192.168.50.1", gateway.getIpv4());
        assertEquals(1L, gateway.getHardwareType());
        assertEquals(2L, gateway.getFlags());
        assertEquals("02:00:00:00:00:01", gateway.getMac());
        TraceEnvironmentConfig.NetworkArpEntryConfig self = two.getNetworkArpEntries().get(1);
        assertEquals("wlan0", self.getInterfaceName());
        assertEquals("192.168.50.23", self.getIpv4());
        assertEquals("02:54:52:41:43:45", self.getMac());

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{\"network\":{\"arpEntries\":[]}}");
        assertTrue(empty.isNetworkArpEntriesConfigured());
        assertTrue(empty.getNetworkArpEntries().isEmpty());

        assertFalse(TraceEnvironmentConfig.parse("{}").isNetworkArpEntriesConfigured());
        assertFalse(TraceEnvironmentConfig.parse("{\"network\":{}}").isNetworkArpEntriesConfigured());
        assertFalse(TraceEnvironmentConfig.parse(
                "{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "]}}")
                .isNetworkArpEntriesConfigured());

        TraceEnvironmentConfig mixedCase = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"arpEntries\":[{\"interfaceName\":\"wlan0\",\"ipv4\":\"10.0.0.1\","
                + "\"hardwareType\":0,\"flags\":0,\"mac\":\"AA:BB:CC:DD:EE:FF\"}]}}");
        assertEquals("aa:bb:cc:dd:ee:ff", mixedCase.getNetworkArpEntries().get(0).getMac());
        assertEquals(0L, mixedCase.getNetworkArpEntries().get(0).getHardwareType());
        assertEquals(0L, mixedCase.getNetworkArpEntries().get(0).getFlags());

        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"arpEntries\":[{\"interfaceName\":\"wlan0\",\"ipv4\":\"0.0.0.0\","
                + "\"hardwareType\":4294967295,\"flags\":4294967295,"
                + "\"mac\":\"ff:ff:ff:ff:ff:ff\"}]}}");
        assertEquals(4294967295L, max.getNetworkArpEntries().get(0).getHardwareType());
        assertEquals(4294967295L, max.getNetworkArpEntries().get(0).getFlags());

        TraceEnvironmentConfig sameIpDifferentIface = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "," + WLAN_IFACE_JSON + "],"
                + "\"arpEntries\":["
                + "{\"interfaceName\":\"lo\",\"ipv4\":\"10.0.0.1\",\"hardwareType\":1,"
                + "\"flags\":2,\"mac\":\"00:00:00:00:00:00\"},"
                + "{\"interfaceName\":\"wlan0\",\"ipv4\":\"10.0.0.1\",\"hardwareType\":1,"
                + "\"flags\":2,\"mac\":\"02:00:00:00:00:01\"}]}}");
        assertEquals(2, sameIpDifferentIface.getNetworkArpEntries().size());

        assertInvalid("{\"network\":{\"arpEntries\":{}}}", "network.arpEntries");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"arpEntries\":[\"wlan0\"]}}", "network.arpEntries[0]");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"arpEntries\":[1]}}", "network.arpEntries[0]");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"arpEntries\":[null]}}", "network.arpEntries[0]");
        assertInvalid("{\"network\":{\"arpEntries\":[" + GATEWAY_ARP_JSON + "]}}",
                "network.arpEntries[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"arpEntries\":[{\"interfaceName\":\"eth0\",\"ipv4\":\"192.168.50.1\","
                + "\"hardwareType\":1,\"flags\":2,\"mac\":\"02:00:00:00:00:01\"}]}}",
                "network.arpEntries[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\"},"
                + "{\"name\":\"wlan0\",\"index\":3,\"ipv4\":\"10.0.0.2\"}],"
                + "\"arpEntries\":[]}}",
                "network.interfaces[1].name");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"arpEntries\":[" + GATEWAY_ARP_JSON + "," + GATEWAY_ARP_JSON + "]}}",
                "network.arpEntries[1].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"arpEntries\":[" + GATEWAY_ARP_JSON + ","
                + "{\"interfaceName\":\"wlan0\",\"ipv4\":\"192.168.50.1\","
                + "\"hardwareType\":0,\"flags\":0,\"mac\":\"aa:bb:cc:dd:ee:ff\"}]}}",
                "network.arpEntries[1].interfaceName");
        assertInvalid(entryWith("\"extra\":1"), "network.arpEntries[0].extra");
        assertInvalid(entryMissing("ipv4"), "network.arpEntries[0].ipv4");
        assertInvalid(entryMissing("hardwareType"), "network.arpEntries[0].hardwareType");
        assertInvalid(entryMissing("flags"), "network.arpEntries[0].flags");
        assertInvalid(entryMissing("mac"), "network.arpEntries[0].mac");
        assertInvalid(entryField("ipv4", "\"192.168.50\""), "network.arpEntries[0].ipv4");
        assertInvalid(entryField("ipv4", "\"192.168.50.256\""), "network.arpEntries[0].ipv4");
        assertInvalid(entryField("ipv4", "\"192.168.50.1 \""), "network.arpEntries[0].ipv4");
        assertInvalid(entryField("ipv4", "true"), "network.arpEntries[0].ipv4");
        assertInvalid(entryField("ipv4", "1"), "network.arpEntries[0].ipv4");
        assertInvalid(entryField("ipv4", "null"), "network.arpEntries[0].ipv4");
        assertInvalid(entryField("mac", "\"02-00-00-00-00-01\""), "network.arpEntries[0].mac");
        assertInvalid(entryField("mac", "\"02:00:00:00:00\""), "network.arpEntries[0].mac");
        assertInvalid(entryField("mac", "\"02:00:00:00:00:gg\""), "network.arpEntries[0].mac");
        assertInvalid(entryField("mac", "\" 02:00:00:00:00:01\""), "network.arpEntries[0].mac");
        assertInvalid(entryField("mac", "1"), "network.arpEntries[0].mac");
        assertInvalid(entryField("mac", "true"), "network.arpEntries[0].mac");
        assertInvalid(entryField("mac", "null"), "network.arpEntries[0].mac");
        assertInvalid(entryField("hardwareType", "-1"), "network.arpEntries[0].hardwareType");
        assertInvalid(entryField("hardwareType", "4294967296"), "network.arpEntries[0].hardwareType");
        assertInvalid(entryField("hardwareType", "1.5"), "network.arpEntries[0].hardwareType");
        assertInvalid(entryField("hardwareType", "\"1\""), "network.arpEntries[0].hardwareType");
        assertInvalid(entryField("hardwareType", "true"), "network.arpEntries[0].hardwareType");
        assertInvalid(entryField("hardwareType", "null"), "network.arpEntries[0].hardwareType");
        assertInvalid(entryField("flags", "-1"), "network.arpEntries[0].flags");
        assertInvalid(entryField("flags", "4294967296"), "network.arpEntries[0].flags");
        assertInvalid(entryField("flags", "1.5"), "network.arpEntries[0].flags");
        assertInvalid(entryField("flags", "\"2\""), "network.arpEntries[0].flags");
        assertInvalid(entryField("flags", "true"), "network.arpEntries[0].flags");
        assertInvalid(entryField("flags", "false"), "network.arpEntries[0].flags");
        assertInvalid(entryField("flags", "null"), "network.arpEntries[0].flags");
        assertInvalid(entryField("interfaceName", "1"), "network.arpEntries[0].interfaceName");
    }

    @Test
    public void testFullTableBytesAndThreeAliases() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_ENTRIES_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            assertEquals(CONFIG_PID, emulator.getPid());
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] expected = EXPECTED_TABLE.getBytes(StandardCharsets.UTF_8);
            assertEquals(EXPECTED_TABLE, new String(expected, StandardCharsets.UTF_8));
            assertEquals(ConfiguredArpEntryFiles.HEADER,
                    EXPECTED_TABLE.substring(0, ConfiguredArpEntryFiles.HEADER.length()));
            assertTrue(EXPECTED_TABLE.startsWith(
                    "IP address       HW type     Flags       HW address            Mask     Device\n"));
            assertTrue(EXPECTED_TABLE.contains(
                    "192.168.50.1     0x1         0x2         02:00:00:00:00:01     *        wlan0\n"));
            assertTrue(EXPECTED_TABLE.contains(
                    "192.168.50.23    0x1         0x2         02:54:52:41:43:45     *        wlan0\n"));
            assertTrue(EXPECTED_TABLE.endsWith("\n"));
            assertFalse(EXPECTED_TABLE.contains("\r"));

            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_NET_ARP));
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_SELF_NET_ARP));
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_PID_NET_ARP));

            assertEquals(3, countArpReads(sink));
            CapturedEvent e0 = findArpRead(sink, PROC_NET_ARP);
            assertArpSidecar(e0, PROC_NET_ARP, 2, expected.length);
            assertSidecarRedacted(e0);
            assertArpSidecar(findArpRead(sink, PROC_SELF_NET_ARP),
                    PROC_SELF_NET_ARP, 2, expected.length);
            assertSidecarRedacted(findArpRead(sink, PROC_SELF_NET_ARP));
            assertArpSidecar(findArpRead(sink, PROC_PID_NET_ARP),
                    PROC_PID_NET_ARP, 2, expected.length);
            assertSidecarRedacted(findArpRead(sink, PROC_PID_NET_ARP));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testExplicitEmptyArrayHeaderOnly() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"process\":{\"pid\":" + CONFIG_PID + "},\"network\":{\"arpEntries\":[]}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            byte[] expected = ConfiguredArpEntryFiles.HEADER.getBytes(StandardCharsets.UTF_8);
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_NET_ARP));
            assertEquals(ConfiguredArpEntryFiles.HEADER,
                    readOpenText(emulator, blocks, PROC_SELF_NET_ARP));
            assertTrue(ConfiguredArpEntryFiles.HEADER.endsWith("\n"));
            assertFalse(ConfiguredArpEntryFiles.HEADER.contains("wlan0"));
            assertFalse(ConfiguredArpEntryFiles.HEADER.contains("0x"));
            CapturedEvent e = findArpRead(sink, PROC_NET_ARP);
            assertArpSidecar(e, PROC_NET_ARP, 0, expected.length);
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
            assertNotTakenOverAsConfiguredArp(emulator, blocks, sink, PROC_NET_ARP);
            assertNotTakenOverAsConfiguredArp(emulator, blocks, sink, PROC_SELF_NET_ARP);
            assertEquals(0, countArpReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredArp(emulator, blocks, sink, PROC_NET_ARP);
            assertEquals(0, countArpReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesTakesPriority() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"files\":{\"" + PROC_NET_ARP + "\":\"CUSTOM_ARP\\n\"}},"
                + TWO_ENTRIES_JSON.substring(TWO_ENTRIES_JSON.indexOf("\"network\"")));
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals("CUSTOM_ARP\n", readOpenText(emulator, blocks, PROC_NET_ARP));
            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                if (isArpRead(e, PROC_NET_ARP)) {
                    fail("network_device arpEntries sidecar must not fire when linux.files wins");
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
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_ENTRIES_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_ARP, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_ARP, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_ARP, IOConstants.O_DIRECTORY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_SELF_NET_ARP,
                    IOConstants.O_RDONLY | IOConstants.O_DIRECTORY);

            assertNotTakenOverAsConfiguredArp(emulator, blocks, sink, "/proc/net/arps");
            assertNotTakenOverAsConfiguredArp(emulator, blocks, sink, "/proc/net/arp/");
            assertNotTakenOverAsConfiguredArp(emulator, blocks, sink, "/proc/net/route");
            assertNotTakenOverAsConfiguredArp(emulator, blocks, sink, "/proc/net/dev");
            assertNotTakenOverAsConfiguredArp(emulator, blocks, sink, "/proc/self/net/arps");
            assertNotTakenOverAsConfiguredArp(emulator, blocks, sink, "/proc/1/net/arp");
            assertNotTakenOverAsConfiguredArp(emulator, blocks, sink, "/proc/net/Arp");
            assertEquals(0, countArpReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    private static String entryWith(String extraField) {
        return "{"
                + "\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"arpEntries\":[{"
                + "\"interfaceName\":\"wlan0\",\"ipv4\":\"192.168.50.1\","
                + "\"hardwareType\":1,\"flags\":2,\"mac\":\"02:00:00:00:00:01\","
                + extraField + "}]}}";
    }

    private static String entryMissing(String field) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"network\":{\"interfaces\":[").append(WLAN_IFACE_JSON).append("],");
        sb.append("\"arpEntries\":[{");
        boolean first = true;
        String[] keys = {
                "interfaceName", "ipv4", "hardwareType", "flags", "mac"
        };
        String[] values = {
                "\"wlan0\"", "\"192.168.50.1\"", "1", "2", "\"02:00:00:00:00:01\""
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
                "interfaceName", "ipv4", "hardwareType", "flags", "mac"
        };
        String[] values = {
                "\"wlan0\"", "\"192.168.50.1\"", "1", "2", "\"02:00:00:00:00:01\""
        };
        StringBuilder sb = new StringBuilder();
        sb.append("{\"network\":{\"interfaces\":[").append(WLAN_IFACE_JSON).append("],");
        sb.append("\"arpEntries\":[{");
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

    private static void assertArpSidecar(CapturedEvent e, String path, int entryCount, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",format=proc-net-arp,entryCount=" + entryCount
                + ",bytes=" + bytes, String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains("读取"));
        assertTrue(e.note.contains(path));
    }

    private static void assertSidecarRedacted(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains("interfaceName"));
        assertFalse(note.contains("interfaceName"));
        assertFalse(value.contains("wlan0"));
        assertFalse(note.contains("wlan0"));
        assertFalse(value.contains("192.168.50"));
        assertFalse(note.contains("192.168.50"));
        assertFalse(value.contains("02:00:00:00:00:01"));
        assertFalse(note.contains("02:00:00:00:00:01"));
        assertFalse(value.contains("02:54:52:41:43:45"));
        assertFalse(note.contains("02:54:52:41:43:45"));
        assertFalse(value.contains("hardwareType"));
        assertFalse(note.contains("hardwareType"));
        assertFalse(value.contains("flags"));
        assertFalse(note.contains("flags"));
        assertFalse(value.contains("aa:bb:cc"));
        assertFalse(note.contains("aa:bb:cc"));
        assertFalse(value.contains("ipv4="));
        assertFalse(value.contains("mac="));
    }

    private static void assertNotTakenOverAsConfiguredArp(AndroidEmulator emulator,
                                                          List<MemoryBlock> blocks,
                                                          CapturingSink sink,
                                                          String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 4096);
            assertFalse(path + " must not serve configured arpEntries table",
                    EXPECTED_TABLE.equals(text));
            assertFalse(path + " must not serve configured arpEntries header-only",
                    ConfiguredArpEntryFiles.HEADER.equals(text) && path.contains("arp"));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected arp sidecar for " + path, isArpRead(e, path));
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
            assertFalse("write/directory open must not serve configured arpEntries",
                    EXPECTED_TABLE.equals(text)
                            || ConfiguredArpEntryFiles.HEADER.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isArpRead(e, path));
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

    private static int countArpReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isArpRead(e, PROC_NET_ARP)
                    || isArpRead(e, PROC_SELF_NET_ARP)
                    || isArpRead(e, PROC_PID_NET_ARP)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findArpRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isArpRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isArpRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=proc-net-arp");
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
