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
 * LinuxFileSystem wiring for {@code network.igmp6Memberships} → exact
 * {@code /proc/net/igmp6}, {@code /proc/self/net/igmp6}, and
 * {@code /proc/<emulatorPid>/net/igmp6}. Default backend (no Unicorn2).
 * Every emulator is closed in {@code finally} without swallowing {@code close}.
 */
public class LinuxFileSystemIgmp6MembershipsTest {

    private static final int CONFIG_PID = 4242;
    private static final String PROC_NET_IGMP6 = "/proc/net/igmp6";
    private static final String PROC_SELF_NET_IGMP6 = "/proc/self/net/igmp6";
    private static final String PROC_PID_NET_IGMP6 = "/proc/" + CONFIG_PID + "/net/igmp6";

    private static final String LO_IFACE_JSON =
            "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"}";
    private static final String WLAN_IFACE_JSON =
            "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\"}";

    private static final String LO_ALL_NODES_JSON =
            "{\"interfaceName\":\"lo\",\"groupIpv6\":\"ff02::1\","
                    + "\"users\":1,\"flags\":0,\"timer\":0}";
    private static final String WLAN_ALL_NODES_JSON =
            "{\"interfaceName\":\"wlan0\",\"groupIpv6\":\"ff02::1\","
                    + "\"users\":1,\"flags\":0,\"timer\":0}";
    private static final String WLAN_MDNS_JSON =
            "{\"interfaceName\":\"wlan0\",\"groupIpv6\":\"ff02::fb\","
                    + "\"users\":2,\"flags\":1,\"timer\":10}";

    private static final String THREE_ENTRIES_JSON = "{"
            + "\"process\":{\"pid\":" + CONFIG_PID + "},"
            + "\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "," + WLAN_IFACE_JSON + "],"
            + "\"igmp6Memberships\":[" + LO_ALL_NODES_JSON + ","
            + WLAN_ALL_NODES_JSON + "," + WLAN_MDNS_JSON + "]}}";

    private static final String EXPECTED_TABLE =
            "1    lo              FF020000000000000000000000000001     1 00000000 0\n"
                    + "2    wlan0           FF020000000000000000000000000001     1 00000000 0\n"
                    + "2    wlan0           FF0200000000000000000000000000FB     2 00000001 10\n";

    @Test
    public void testParseValidAndInvalid() {
        TraceEnvironmentConfig three = TraceEnvironmentConfig.parse(THREE_ENTRIES_JSON);
        assertTrue(three.isNetworkIgmp6MembershipsConfigured());
        assertEquals(3, three.getNetworkIgmp6Memberships().size());
        TraceEnvironmentConfig.NetworkIgmp6MembershipConfig lo = three.getNetworkIgmp6Memberships().get(0);
        assertEquals("lo", lo.getInterfaceName());
        assertEquals("ff02::1", lo.getGroupIpv6());
        assertEquals("FF020000000000000000000000000001", lo.getGroupIpv6Hex());
        assertEquals(1, lo.getUsers());
        assertEquals(0L, lo.getFlags());
        assertEquals(0L, lo.getTimer());
        TraceEnvironmentConfig.NetworkIgmp6MembershipConfig wlanAll = three.getNetworkIgmp6Memberships().get(1);
        assertEquals("wlan0", wlanAll.getInterfaceName());
        assertEquals("ff02::1", wlanAll.getGroupIpv6());
        assertEquals(0L, wlanAll.getFlags());
        TraceEnvironmentConfig.NetworkIgmp6MembershipConfig mdns = three.getNetworkIgmp6Memberships().get(2);
        assertEquals("ff02::fb", mdns.getGroupIpv6());
        assertEquals("FF0200000000000000000000000000FB", mdns.getGroupIpv6Hex());
        assertEquals(2, mdns.getUsers());
        assertEquals(1L, mdns.getFlags());
        assertEquals(10L, mdns.getTimer());

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{\"network\":{\"igmp6Memberships\":[]}}");
        assertTrue(empty.isNetworkIgmp6MembershipsConfigured());
        assertTrue(empty.getNetworkIgmp6Memberships().isEmpty());

        assertFalse(TraceEnvironmentConfig.parse("{}").isNetworkIgmp6MembershipsConfigured());
        assertFalse(TraceEnvironmentConfig.parse("{\"network\":{}}").isNetworkIgmp6MembershipsConfigured());
        assertFalse(TraceEnvironmentConfig.parse(
                "{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "]}}")
                .isNetworkIgmp6MembershipsConfigured());

        TraceEnvironmentConfig bounds = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "," + WLAN_IFACE_JSON + "],"
                + "\"igmp6Memberships\":["
                + "{\"interfaceName\":\"lo\",\"groupIpv6\":\"ff00::\","
                + "\"users\":0,\"flags\":0,\"timer\":0},"
                + "{\"interfaceName\":\"wlan0\",\"groupIpv6\":\"ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff\","
                + "\"users\":2147483647,\"flags\":4294967295,\"timer\":9223372036854775807}]}}");
        assertEquals("ff00::", bounds.getNetworkIgmp6Memberships().get(0).getGroupIpv6());
        assertEquals("FF000000000000000000000000000000",
                bounds.getNetworkIgmp6Memberships().get(0).getGroupIpv6Hex());
        assertEquals(0, bounds.getNetworkIgmp6Memberships().get(0).getUsers());
        assertEquals(2147483647, bounds.getNetworkIgmp6Memberships().get(1).getUsers());
        assertEquals(4294967295L, bounds.getNetworkIgmp6Memberships().get(1).getFlags());
        assertEquals(9223372036854775807L, bounds.getNetworkIgmp6Memberships().get(1).getTimer());

        TraceEnvironmentConfig expanded = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "],"
                + "\"igmp6Memberships\":["
                + "{\"interfaceName\":\"lo\",\"groupIpv6\":\"FF02:0:0:0:0:0:0:1\","
                + "\"users\":1,\"flags\":0,\"timer\":0}]}}");
        assertEquals("FF020000000000000000000000000001",
                expanded.getNetworkIgmp6Memberships().get(0).getGroupIpv6Hex());

        assertInvalid("{\"network\":{\"igmp6Memberships\":{}}}", "network.igmp6Memberships");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmp6Memberships\":[\"wlan0\"]}}", "network.igmp6Memberships[0]");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmp6Memberships\":[1]}}", "network.igmp6Memberships[0]");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmp6Memberships\":[null]}}", "network.igmp6Memberships[0]");
        assertInvalid("{\"network\":{\"igmp6Memberships\":[" + WLAN_MDNS_JSON + "]}}",
                "network.igmp6Memberships[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmp6Memberships\":[{\"interfaceName\":\"eth0\",\"groupIpv6\":\"ff02::fb\","
                + "\"users\":1,\"flags\":0,\"timer\":0}]}}",
                "network.igmp6Memberships[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\"},"
                + "{\"name\":\"wlan0\",\"index\":3,\"ipv4\":\"10.0.0.2\"}],"
                + "\"igmp6Memberships\":[]}}",
                "network.interfaces[1].name");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmp6Memberships\":[" + WLAN_MDNS_JSON + "," + WLAN_MDNS_JSON + "]}}",
                "network.igmp6Memberships[1].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmp6Memberships\":["
                + "{\"interfaceName\":\"wlan0\",\"groupIpv6\":\"ff02::1\","
                + "\"users\":1,\"flags\":0,\"timer\":0},"
                + "{\"interfaceName\":\"wlan0\",\"groupIpv6\":\"FF02::1\","
                + "\"users\":2,\"flags\":0,\"timer\":0}]}}",
                "network.igmp6Memberships[1].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmp6Memberships\":["
                + "{\"interfaceName\":\"wlan0\",\"groupIpv6\":\"ff02::1\","
                + "\"users\":1,\"flags\":0,\"timer\":0},"
                + "{\"interfaceName\":\"wlan0\",\"groupIpv6\":\"ff02:0:0:0:0:0:0:1\","
                + "\"users\":2,\"flags\":0,\"timer\":0}]}}",
                "network.igmp6Memberships[1].interfaceName");
        assertInvalid(entryWith("\"extra\":1"), "network.igmp6Memberships[0].extra");
        assertInvalid(entryMissing("interfaceName"), "network.igmp6Memberships[0].interfaceName");
        assertInvalid(entryMissing("groupIpv6"), "network.igmp6Memberships[0].groupIpv6");
        assertInvalid(entryMissing("users"), "network.igmp6Memberships[0].users");
        assertInvalid(entryMissing("flags"), "network.igmp6Memberships[0].flags");
        assertInvalid(entryMissing("timer"), "network.igmp6Memberships[0].timer");
        assertInvalid(entryField("groupIpv6", "\"ff02::1 \""), "network.igmp6Memberships[0].groupIpv6");
        assertInvalid(entryField("groupIpv6", "\"ff02:::1\""), "network.igmp6Memberships[0].groupIpv6");
        assertInvalid(entryField("groupIpv6", "\"ff02:\""), "network.igmp6Memberships[0].groupIpv6");
        assertInvalid(entryField("groupIpv6", "\"gg02::1\""), "network.igmp6Memberships[0].groupIpv6");
        assertInvalid(entryField("groupIpv6", "\"ff02::1%lo\""), "network.igmp6Memberships[0].groupIpv6");
        assertInvalid(entryField("groupIpv6", "\"::ffff:192.0.2.1\""), "network.igmp6Memberships[0].groupIpv6");
        assertInvalid(entryField("groupIpv6", "\"224.0.0.251\""), "network.igmp6Memberships[0].groupIpv6");
        assertInvalid(entryField("groupIpv6", "\"fe80::1\""), "network.igmp6Memberships[0].groupIpv6");
        assertInvalid(entryField("groupIpv6", "\"::1\""), "network.igmp6Memberships[0].groupIpv6");
        assertInvalid(entryField("groupIpv6", "\"FF020000000000000000000000000001\""),
                "network.igmp6Memberships[0].groupIpv6");
        assertInvalid(entryField("groupIpv6", "true"), "network.igmp6Memberships[0].groupIpv6");
        assertInvalid(entryField("groupIpv6", "1"), "network.igmp6Memberships[0].groupIpv6");
        assertInvalid(entryField("groupIpv6", "null"), "network.igmp6Memberships[0].groupIpv6");
        assertInvalid(entryField("users", "-1"), "network.igmp6Memberships[0].users");
        assertInvalid(entryField("users", "2147483648"), "network.igmp6Memberships[0].users");
        assertInvalid(entryField("users", "1.5"), "network.igmp6Memberships[0].users");
        assertInvalid(entryField("users", "\"1\""), "network.igmp6Memberships[0].users");
        assertInvalid(entryField("users", "true"), "network.igmp6Memberships[0].users");
        assertInvalid(entryField("users", "null"), "network.igmp6Memberships[0].users");
        assertInvalid(entryField("flags", "-1"), "network.igmp6Memberships[0].flags");
        assertInvalid(entryField("flags", "4294967296"), "network.igmp6Memberships[0].flags");
        assertInvalid(entryField("flags", "1.5"), "network.igmp6Memberships[0].flags");
        assertInvalid(entryField("flags", "\"0\""), "network.igmp6Memberships[0].flags");
        assertInvalid(entryField("flags", "true"), "network.igmp6Memberships[0].flags");
        assertInvalid(entryField("flags", "null"), "network.igmp6Memberships[0].flags");
        assertInvalid(entryField("timer", "-1"), "network.igmp6Memberships[0].timer");
        assertInvalid(entryField("timer", "9223372036854775808"), "network.igmp6Memberships[0].timer");
        assertInvalid(entryField("timer", "1.5"), "network.igmp6Memberships[0].timer");
        assertInvalid(entryField("timer", "\"0\""), "network.igmp6Memberships[0].timer");
        assertInvalid(entryField("timer", "true"), "network.igmp6Memberships[0].timer");
        assertInvalid(entryField("timer", "null"), "network.igmp6Memberships[0].timer");
        assertInvalid(entryField("interfaceName", "1"), "network.igmp6Memberships[0].interfaceName");
        assertInvalid(entryField("interfaceName", "\"\""), "network.igmp6Memberships[0].interfaceName");
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
            assertTrue(EXPECTED_TABLE.startsWith(
                    "1    lo              FF020000000000000000000000000001     1 00000000 0\n"));
            assertTrue(EXPECTED_TABLE.contains(
                    "2    wlan0           FF020000000000000000000000000001     1 00000000 0\n"));
            assertTrue(EXPECTED_TABLE.contains(
                    "2    wlan0           FF0200000000000000000000000000FB     2 00000001 10\n"));
            assertFalse(EXPECTED_TABLE.contains("Idx"));
            assertFalse(EXPECTED_TABLE.contains("ff02::"));
            assertFalse(EXPECTED_TABLE.contains(":"));
            assertTrue(EXPECTED_TABLE.endsWith("\n"));
            assertFalse(EXPECTED_TABLE.contains("\r"));
            assertFalse(EXPECTED_TABLE.contains("\t"));

            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_NET_IGMP6));
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_SELF_NET_IGMP6));
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_PID_NET_IGMP6));

            assertEquals(3, countIgmp6Reads(sink));
            CapturedEvent e0 = findIgmp6Read(sink, PROC_NET_IGMP6);
            assertIgmp6Sidecar(e0, PROC_NET_IGMP6, 3, 2, expected.length);
            assertSidecarRedacted(e0);
            assertIgmp6Sidecar(findIgmp6Read(sink, PROC_SELF_NET_IGMP6),
                    PROC_SELF_NET_IGMP6, 3, 2, expected.length);
            assertSidecarRedacted(findIgmp6Read(sink, PROC_SELF_NET_IGMP6));
            assertIgmp6Sidecar(findIgmp6Read(sink, PROC_PID_NET_IGMP6),
                    PROC_PID_NET_IGMP6, 3, 2, expected.length);
            assertSidecarRedacted(findIgmp6Read(sink, PROC_PID_NET_IGMP6));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testExplicitEmptyArrayZeroBytes() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"process\":{\"pid\":" + CONFIG_PID + "},\"network\":{\"igmp6Memberships\":[]}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            byte[] expected = new byte[0];
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_NET_IGMP6));
            assertEquals("", readOpenText(emulator, blocks, PROC_SELF_NET_IGMP6));
            assertEquals(0, readOpenBytes(emulator, blocks, PROC_PID_NET_IGMP6).length);
            CapturedEvent e = findIgmp6Read(sink, PROC_NET_IGMP6);
            assertIgmp6Sidecar(e, PROC_NET_IGMP6, 0, 0, 0);
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
            assertNotTakenOverAsConfiguredIgmp6(emulator, blocks, sink, PROC_NET_IGMP6);
            assertNotTakenOverAsConfiguredIgmp6(emulator, blocks, sink, PROC_SELF_NET_IGMP6);
            assertEquals(0, countIgmp6Reads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredIgmp6(emulator, blocks, sink, PROC_NET_IGMP6);
            assertEquals(0, countIgmp6Reads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesTakesPriority() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"files\":{\"" + PROC_NET_IGMP6 + "\":\"CUSTOM_IGMP6\\n\"}},"
                + THREE_ENTRIES_JSON.substring(THREE_ENTRIES_JSON.indexOf("\"network\"")));
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals("CUSTOM_IGMP6\n", readOpenText(emulator, blocks, PROC_NET_IGMP6));
            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                if (isIgmp6Read(e, PROC_NET_IGMP6)) {
                    fail("network_device igmp6Memberships sidecar must not fire when linux.files wins");
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

            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_IGMP6, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_IGMP6, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_IGMP6, IOConstants.O_DIRECTORY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_SELF_NET_IGMP6,
                    IOConstants.O_RDONLY | IOConstants.O_DIRECTORY);

            assertNotTakenOverAsConfiguredIgmp6(emulator, blocks, sink, "/proc/net/igmp6s");
            assertNotTakenOverAsConfiguredIgmp6(emulator, blocks, sink, "/proc/net/igmp6/");
            assertNotTakenOverAsConfiguredIgmp6(emulator, blocks, sink, "/proc/net/igmp");
            assertNotTakenOverAsConfiguredIgmp6(emulator, blocks, sink, "/proc/net/route");
            assertNotTakenOverAsConfiguredIgmp6(emulator, blocks, sink, "/proc/self/net/igmp6s");
            assertNotTakenOverAsConfiguredIgmp6(emulator, blocks, sink, "/proc/1/net/igmp6");
            assertNotTakenOverAsConfiguredIgmp6(emulator, blocks, sink, "/proc/net/Igmp6");
            assertEquals(0, countIgmp6Reads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    private static String entryWith(String extraField) {
        return "{"
                + "\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmp6Memberships\":[{"
                + "\"interfaceName\":\"wlan0\",\"groupIpv6\":\"ff02::fb\","
                + "\"users\":1,\"flags\":0,\"timer\":0,"
                + extraField + "}]}}";
    }

    private static String entryMissing(String field) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"network\":{\"interfaces\":[").append(WLAN_IFACE_JSON).append("],");
        sb.append("\"igmp6Memberships\":[{");
        boolean first = true;
        String[] keys = {
                "interfaceName", "groupIpv6", "users", "flags", "timer"
        };
        String[] values = {
                "\"wlan0\"", "\"ff02::fb\"", "1", "0", "0"
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
                "interfaceName", "groupIpv6", "users", "flags", "timer"
        };
        String[] values = {
                "\"wlan0\"", "\"ff02::fb\"", "1", "0", "0"
        };
        StringBuilder sb = new StringBuilder();
        sb.append("{\"network\":{\"interfaces\":[").append(WLAN_IFACE_JSON).append("],");
        sb.append("\"igmp6Memberships\":[{");
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

    private static void assertIgmp6Sidecar(CapturedEvent e, String path,
                                           int membershipCount, int interfaceCount, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",format=proc-net-igmp6,membershipCount=" + membershipCount
                + ",interfaceCount=" + interfaceCount + ",bytes=" + bytes, String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains("读取"));
        assertTrue(e.note.contains("IGMP6"));
        assertTrue(e.note.contains(path));
    }

    private static void assertSidecarRedacted(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains("interfaceName"));
        assertFalse(note.contains("interfaceName"));
        assertFalse(value.contains("wlan0"));
        assertFalse(note.contains("wlan0"));
        assertFalse(value.contains("ff02"));
        assertFalse(note.contains("ff02"));
        assertFalse(value.contains("FF02"));
        assertFalse(note.contains("FF02"));
        assertFalse(value.contains("00000000"));
        assertFalse(note.contains("00000000"));
        assertFalse(value.contains("flags="));
        assertFalse(note.contains("flags="));
        assertFalse(value.contains("timer="));
        assertFalse(note.contains("timer="));
        assertFalse(value.contains("groupIpv6"));
        assertFalse(note.contains("groupIpv6"));
        assertFalse(value.contains("users="));
        assertFalse(value.contains("index="));
        assertFalse(note.contains("index="));
    }

    private static void assertNotTakenOverAsConfiguredIgmp6(AndroidEmulator emulator,
                                                            List<MemoryBlock> blocks,
                                                            CapturingSink sink,
                                                            String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 4096);
            assertFalse(path + " must not serve configured igmp6Memberships table",
                    EXPECTED_TABLE.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected igmp6 sidecar for " + path, isIgmp6Read(e, path));
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
            assertFalse("write/directory open must not serve configured igmp6Memberships",
                    EXPECTED_TABLE.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isIgmp6Read(e, path));
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

    private static int countIgmp6Reads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isIgmp6Read(e, PROC_NET_IGMP6)
                    || isIgmp6Read(e, PROC_SELF_NET_IGMP6)
                    || isIgmp6Read(e, PROC_PID_NET_IGMP6)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findIgmp6Read(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isIgmp6Read(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isIgmp6Read(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=proc-net-igmp6");
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
