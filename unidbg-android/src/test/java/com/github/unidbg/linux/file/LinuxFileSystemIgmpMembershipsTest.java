package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.ConfiguredIgmpMembershipFiles;
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
 * LinuxFileSystem wiring for {@code network.igmpMemberships} → exact
 * {@code /proc/net/igmp}, {@code /proc/self/net/igmp}, and
 * {@code /proc/<emulatorPid>/net/igmp}. Default backend (no Unicorn2).
 * Every emulator is closed in {@code finally} without swallowing {@code close}.
 */
public class LinuxFileSystemIgmpMembershipsTest {

    private static final int CONFIG_PID = 4242;
    private static final String PROC_NET_IGMP = "/proc/net/igmp";
    private static final String PROC_SELF_NET_IGMP = "/proc/self/net/igmp";
    private static final String PROC_PID_NET_IGMP = "/proc/" + CONFIG_PID + "/net/igmp";

    private static final String LO_IFACE_JSON =
            "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"}";
    private static final String WLAN_IFACE_JSON =
            "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\"}";

    private static final String LO_ALL_HOSTS_JSON =
            "{\"interfaceName\":\"lo\",\"groupIpv4\":\"224.0.0.1\","
                    + "\"querierVersion\":\"V2\",\"users\":1,"
                    + "\"timerRunning\":false,\"timerClock\":0,\"reporter\":false}";
    private static final String WLAN_MDNS_JSON =
            "{\"interfaceName\":\"wlan0\",\"groupIpv4\":\"224.0.0.251\","
                    + "\"querierVersion\":\"V3\",\"users\":1,"
                    + "\"timerRunning\":false,\"timerClock\":0,\"reporter\":false}";
    private static final String WLAN_SSDP_JSON =
            "{\"interfaceName\":\"wlan0\",\"groupIpv4\":\"239.255.255.250\","
                    + "\"querierVersion\":\"V3\",\"users\":2,"
                    + "\"timerRunning\":true,\"timerClock\":10,\"reporter\":true}";

    private static final String THREE_ENTRIES_JSON = "{"
            + "\"process\":{\"pid\":" + CONFIG_PID + "},"
            + "\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "," + WLAN_IFACE_JSON + "],"
            + "\"igmpMemberships\":[" + LO_ALL_HOSTS_JSON + ","
            + WLAN_MDNS_JSON + "," + WLAN_SSDP_JSON + "]}}";

    private static final String EXPECTED_TABLE =
            ConfiguredIgmpMembershipFiles.HEADER
                    + "1\tlo        :     1      V2\n"
                    + "\t\t\t\t010000E0     1 0:00000000\t\t0\n"
                    + "2\twlan0     :     2      V3\n"
                    + "\t\t\t\tFB0000E0     1 0:00000000\t\t0\n"
                    + "\t\t\t\tFAFFFFEF     2 1:0000000A\t\t1\n";

    @Test
    public void testParseValidAndInvalid() {
        TraceEnvironmentConfig three = TraceEnvironmentConfig.parse(THREE_ENTRIES_JSON);
        assertTrue(three.isNetworkIgmpMembershipsConfigured());
        assertEquals(3, three.getNetworkIgmpMemberships().size());
        TraceEnvironmentConfig.NetworkIgmpMembershipConfig lo = three.getNetworkIgmpMemberships().get(0);
        assertEquals("lo", lo.getInterfaceName());
        assertEquals("224.0.0.1", lo.getGroupIpv4());
        assertEquals("V2", lo.getQuerierVersion());
        assertEquals(1, lo.getUsers());
        assertFalse(lo.isTimerRunning());
        assertEquals(0L, lo.getTimerClock());
        assertFalse(lo.isReporter());
        TraceEnvironmentConfig.NetworkIgmpMembershipConfig mdns = three.getNetworkIgmpMemberships().get(1);
        assertEquals("wlan0", mdns.getInterfaceName());
        assertEquals("224.0.0.251", mdns.getGroupIpv4());
        assertEquals("V3", mdns.getQuerierVersion());
        assertFalse(mdns.isTimerRunning());
        assertEquals(0L, mdns.getTimerClock());
        assertFalse(mdns.isReporter());
        TraceEnvironmentConfig.NetworkIgmpMembershipConfig ssdp = three.getNetworkIgmpMemberships().get(2);
        assertEquals("239.255.255.250", ssdp.getGroupIpv4());
        assertEquals(2, ssdp.getUsers());
        assertTrue(ssdp.isTimerRunning());
        assertEquals(10L, ssdp.getTimerClock());
        assertTrue(ssdp.isReporter());

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{\"network\":{\"igmpMemberships\":[]}}");
        assertTrue(empty.isNetworkIgmpMembershipsConfigured());
        assertTrue(empty.getNetworkIgmpMemberships().isEmpty());

        assertFalse(TraceEnvironmentConfig.parse("{}").isNetworkIgmpMembershipsConfigured());
        assertFalse(TraceEnvironmentConfig.parse("{\"network\":{}}").isNetworkIgmpMembershipsConfigured());
        assertFalse(TraceEnvironmentConfig.parse(
                "{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "]}}")
                .isNetworkIgmpMembershipsConfigured());

        TraceEnvironmentConfig bounds = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "," + WLAN_IFACE_JSON + "],"
                + "\"igmpMemberships\":["
                + "{\"interfaceName\":\"lo\",\"groupIpv4\":\"224.0.0.0\","
                + "\"querierVersion\":\"V1\",\"users\":0,"
                + "\"timerRunning\":true,\"timerClock\":0,\"reporter\":false},"
                + "{\"interfaceName\":\"wlan0\",\"groupIpv4\":\"239.255.255.255\","
                + "\"querierVersion\":\"V3\",\"users\":2147483647,"
                + "\"timerRunning\":true,\"timerClock\":4294967295,\"reporter\":true}]}}");
        assertEquals("V1", bounds.getNetworkIgmpMemberships().get(0).getQuerierVersion());
        assertEquals(0, bounds.getNetworkIgmpMemberships().get(0).getUsers());
        assertEquals(0L, bounds.getNetworkIgmpMemberships().get(0).getTimerClock());
        assertEquals(2147483647, bounds.getNetworkIgmpMemberships().get(1).getUsers());
        assertEquals(4294967295L, bounds.getNetworkIgmpMemberships().get(1).getTimerClock());

        TraceEnvironmentConfig sameGroupDifferentIface = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "," + WLAN_IFACE_JSON + "],"
                + "\"igmpMemberships\":["
                + "{\"interfaceName\":\"lo\",\"groupIpv4\":\"224.0.0.1\","
                + "\"querierVersion\":\"V2\",\"users\":1,"
                + "\"timerRunning\":false,\"timerClock\":0,\"reporter\":false},"
                + "{\"interfaceName\":\"wlan0\",\"groupIpv4\":\"224.0.0.1\","
                + "\"querierVersion\":\"V3\",\"users\":1,"
                + "\"timerRunning\":false,\"timerClock\":0,\"reporter\":false}]}}");
        assertEquals(2, sameGroupDifferentIface.getNetworkIgmpMemberships().size());

        assertInvalid("{\"network\":{\"igmpMemberships\":{}}}", "network.igmpMemberships");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmpMemberships\":[\"wlan0\"]}}", "network.igmpMemberships[0]");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmpMemberships\":[1]}}", "network.igmpMemberships[0]");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmpMemberships\":[null]}}", "network.igmpMemberships[0]");
        assertInvalid("{\"network\":{\"igmpMemberships\":[" + WLAN_MDNS_JSON + "]}}",
                "network.igmpMemberships[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmpMemberships\":[{\"interfaceName\":\"eth0\",\"groupIpv4\":\"224.0.0.251\","
                + "\"querierVersion\":\"V3\",\"users\":1,"
                + "\"timerRunning\":false,\"timerClock\":0,\"reporter\":false}]}}",
                "network.igmpMemberships[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\"},"
                + "{\"name\":\"wlan0\",\"index\":3,\"ipv4\":\"10.0.0.2\"}],"
                + "\"igmpMemberships\":[]}}",
                "network.interfaces[1].name");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmpMemberships\":[" + WLAN_MDNS_JSON + "," + WLAN_MDNS_JSON + "]}}",
                "network.igmpMemberships[1].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmpMemberships\":[" + WLAN_MDNS_JSON + ","
                + "{\"interfaceName\":\"wlan0\",\"groupIpv4\":\"224.0.0.251\","
                + "\"querierVersion\":\"V3\",\"users\":0,"
                + "\"timerRunning\":false,\"timerClock\":0,\"reporter\":true}]}}",
                "network.igmpMemberships[1].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmpMemberships\":[" + WLAN_MDNS_JSON + ","
                + "{\"interfaceName\":\"wlan0\",\"groupIpv4\":\"239.255.255.250\","
                + "\"querierVersion\":\"V2\",\"users\":1,"
                + "\"timerRunning\":false,\"timerClock\":0,\"reporter\":false}]}}",
                "network.igmpMemberships[1].querierVersion");
        assertInvalid(entryWith("\"extra\":1"), "network.igmpMemberships[0].extra");
        assertInvalid(entryMissing("interfaceName"), "network.igmpMemberships[0].interfaceName");
        assertInvalid(entryMissing("groupIpv4"), "network.igmpMemberships[0].groupIpv4");
        assertInvalid(entryMissing("querierVersion"), "network.igmpMemberships[0].querierVersion");
        assertInvalid(entryMissing("users"), "network.igmpMemberships[0].users");
        assertInvalid(entryMissing("timerRunning"), "network.igmpMemberships[0].timerRunning");
        assertInvalid(entryMissing("timerClock"), "network.igmpMemberships[0].timerClock");
        assertInvalid(entryMissing("reporter"), "network.igmpMemberships[0].reporter");
        assertInvalid(entryField("groupIpv4", "\"224.0.0\""), "network.igmpMemberships[0].groupIpv4");
        assertInvalid(entryField("groupIpv4", "\"224.0.0.256\""), "network.igmpMemberships[0].groupIpv4");
        assertInvalid(entryField("groupIpv4", "\"224.0.0.251 \""), "network.igmpMemberships[0].groupIpv4");
        assertInvalid(entryField("groupIpv4", "\"192.168.50.1\""), "network.igmpMemberships[0].groupIpv4");
        assertInvalid(entryField("groupIpv4", "\"223.255.255.255\""), "network.igmpMemberships[0].groupIpv4");
        assertInvalid(entryField("groupIpv4", "\"240.0.0.1\""), "network.igmpMemberships[0].groupIpv4");
        assertInvalid(entryField("groupIpv4", "true"), "network.igmpMemberships[0].groupIpv4");
        assertInvalid(entryField("groupIpv4", "1"), "network.igmpMemberships[0].groupIpv4");
        assertInvalid(entryField("groupIpv4", "null"), "network.igmpMemberships[0].groupIpv4");
        assertInvalid(entryField("querierVersion", "\"v3\""), "network.igmpMemberships[0].querierVersion");
        assertInvalid(entryField("querierVersion", "\"V4\""), "network.igmpMemberships[0].querierVersion");
        assertInvalid(entryField("querierVersion", "\"V3 \""), "network.igmpMemberships[0].querierVersion");
        assertInvalid(entryField("querierVersion", "3"), "network.igmpMemberships[0].querierVersion");
        assertInvalid(entryField("querierVersion", "true"), "network.igmpMemberships[0].querierVersion");
        assertInvalid(entryField("querierVersion", "null"), "network.igmpMemberships[0].querierVersion");
        assertInvalid(entryField("users", "-1"), "network.igmpMemberships[0].users");
        assertInvalid(entryField("users", "2147483648"), "network.igmpMemberships[0].users");
        assertInvalid(entryField("users", "1.5"), "network.igmpMemberships[0].users");
        assertInvalid(entryField("users", "\"1\""), "network.igmpMemberships[0].users");
        assertInvalid(entryField("users", "true"), "network.igmpMemberships[0].users");
        assertInvalid(entryField("users", "null"), "network.igmpMemberships[0].users");
        assertInvalid(entryField("timerClock", "-1"), "network.igmpMemberships[0].timerClock");
        assertInvalid(entryField("timerClock", "4294967296"), "network.igmpMemberships[0].timerClock");
        assertInvalid(entryField("timerClock", "1.5"), "network.igmpMemberships[0].timerClock");
        assertInvalid(entryField("timerClock", "\"0\""), "network.igmpMemberships[0].timerClock");
        assertInvalid(entryField("timerClock", "true"), "network.igmpMemberships[0].timerClock");
        assertInvalid(entryField("timerClock", "null"), "network.igmpMemberships[0].timerClock");
        assertInvalid(entryField("timerRunning", "0"), "network.igmpMemberships[0].timerRunning");
        assertInvalid(entryField("timerRunning", "\"false\""), "network.igmpMemberships[0].timerRunning");
        assertInvalid(entryField("timerRunning", "null"), "network.igmpMemberships[0].timerRunning");
        assertInvalid(entryField("reporter", "1"), "network.igmpMemberships[0].reporter");
        assertInvalid(entryField("reporter", "\"true\""), "network.igmpMemberships[0].reporter");
        assertInvalid(entryField("reporter", "null"), "network.igmpMemberships[0].reporter");
        assertInvalid(entryField("interfaceName", "1"), "network.igmpMemberships[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmpMemberships\":[{\"interfaceName\":\"wlan0\",\"groupIpv4\":\"224.0.0.251\","
                + "\"querierVersion\":\"V3\",\"users\":1,"
                + "\"timerRunning\":false,\"timerClock\":1,\"reporter\":false}]}}",
                "network.igmpMemberships[0].timerClock");
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
            assertEquals(ConfiguredIgmpMembershipFiles.HEADER,
                    EXPECTED_TABLE.substring(0, ConfiguredIgmpMembershipFiles.HEADER.length()));
            assertTrue(EXPECTED_TABLE.startsWith(
                    "Idx\tDevice    : Count Querier\tGroup    Users Timer\tReporter\n"));
            assertTrue(EXPECTED_TABLE.contains("1\tlo        :     1      V2\n"));
            assertTrue(EXPECTED_TABLE.contains("2\twlan0     :     2      V3\n"));
            assertTrue(EXPECTED_TABLE.contains("\t\t\t\t010000E0     1 0:00000000\t\t0\n"));
            assertTrue(EXPECTED_TABLE.contains("\t\t\t\tFB0000E0     1 0:00000000\t\t0\n"));
            assertTrue(EXPECTED_TABLE.contains("\t\t\t\tFAFFFFEF     2 1:0000000A\t\t1\n"));
            assertFalse(EXPECTED_TABLE.contains("E00000FB"));
            assertFalse(EXPECTED_TABLE.contains("E0000001"));
            assertTrue(EXPECTED_TABLE.endsWith("\n"));
            assertFalse(EXPECTED_TABLE.contains("\r"));

            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_NET_IGMP));
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_SELF_NET_IGMP));
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_PID_NET_IGMP));

            assertEquals(3, countIgmpReads(sink));
            CapturedEvent e0 = findIgmpRead(sink, PROC_NET_IGMP);
            assertIgmpSidecar(e0, PROC_NET_IGMP, 3, 2, expected.length);
            assertSidecarRedacted(e0);
            assertIgmpSidecar(findIgmpRead(sink, PROC_SELF_NET_IGMP),
                    PROC_SELF_NET_IGMP, 3, 2, expected.length);
            assertSidecarRedacted(findIgmpRead(sink, PROC_SELF_NET_IGMP));
            assertIgmpSidecar(findIgmpRead(sink, PROC_PID_NET_IGMP),
                    PROC_PID_NET_IGMP, 3, 2, expected.length);
            assertSidecarRedacted(findIgmpRead(sink, PROC_PID_NET_IGMP));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testExplicitEmptyArrayHeaderOnly() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"process\":{\"pid\":" + CONFIG_PID + "},\"network\":{\"igmpMemberships\":[]}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            byte[] expected = ConfiguredIgmpMembershipFiles.HEADER.getBytes(StandardCharsets.UTF_8);
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_NET_IGMP));
            assertEquals(ConfiguredIgmpMembershipFiles.HEADER,
                    readOpenText(emulator, blocks, PROC_SELF_NET_IGMP));
            assertTrue(ConfiguredIgmpMembershipFiles.HEADER.endsWith("\n"));
            assertFalse(ConfiguredIgmpMembershipFiles.HEADER.contains("wlan0"));
            assertFalse(ConfiguredIgmpMembershipFiles.HEADER.contains("FB0000E0"));
            CapturedEvent e = findIgmpRead(sink, PROC_NET_IGMP);
            assertIgmpSidecar(e, PROC_NET_IGMP, 0, 0, expected.length);
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
            assertNotTakenOverAsConfiguredIgmp(emulator, blocks, sink, PROC_NET_IGMP);
            assertNotTakenOverAsConfiguredIgmp(emulator, blocks, sink, PROC_SELF_NET_IGMP);
            assertEquals(0, countIgmpReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredIgmp(emulator, blocks, sink, PROC_NET_IGMP);
            assertEquals(0, countIgmpReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesTakesPriority() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"files\":{\"" + PROC_NET_IGMP + "\":\"CUSTOM_IGMP\\n\"}},"
                + THREE_ENTRIES_JSON.substring(THREE_ENTRIES_JSON.indexOf("\"network\"")));
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals("CUSTOM_IGMP\n", readOpenText(emulator, blocks, PROC_NET_IGMP));
            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                if (isIgmpRead(e, PROC_NET_IGMP)) {
                    fail("network_device igmpMemberships sidecar must not fire when linux.files wins");
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

            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_IGMP, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_IGMP, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_IGMP, IOConstants.O_DIRECTORY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_SELF_NET_IGMP,
                    IOConstants.O_RDONLY | IOConstants.O_DIRECTORY);

            assertNotTakenOverAsConfiguredIgmp(emulator, blocks, sink, "/proc/net/igmps");
            assertNotTakenOverAsConfiguredIgmp(emulator, blocks, sink, "/proc/net/igmp/");
            assertNotTakenOverAsConfiguredIgmp(emulator, blocks, sink, "/proc/net/igmp6");
            assertNotTakenOverAsConfiguredIgmp(emulator, blocks, sink, "/proc/net/route");
            assertNotTakenOverAsConfiguredIgmp(emulator, blocks, sink, "/proc/net/arp");
            assertNotTakenOverAsConfiguredIgmp(emulator, blocks, sink, "/proc/self/net/igmps");
            assertNotTakenOverAsConfiguredIgmp(emulator, blocks, sink, "/proc/1/net/igmp");
            assertNotTakenOverAsConfiguredIgmp(emulator, blocks, sink, "/proc/net/Igmp");
            assertEquals(0, countIgmpReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    private static String entryWith(String extraField) {
        return "{"
                + "\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"igmpMemberships\":[{"
                + "\"interfaceName\":\"wlan0\",\"groupIpv4\":\"224.0.0.251\","
                + "\"querierVersion\":\"V3\",\"users\":1,"
                + "\"timerRunning\":false,\"timerClock\":0,\"reporter\":false,"
                + extraField + "}]}}";
    }

    private static String entryMissing(String field) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"network\":{\"interfaces\":[").append(WLAN_IFACE_JSON).append("],");
        sb.append("\"igmpMemberships\":[{");
        boolean first = true;
        String[] keys = {
                "interfaceName", "groupIpv4", "querierVersion", "users",
                "timerRunning", "timerClock", "reporter"
        };
        String[] values = {
                "\"wlan0\"", "\"224.0.0.251\"", "\"V3\"", "1",
                "false", "0", "false"
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
                "interfaceName", "groupIpv4", "querierVersion", "users",
                "timerRunning", "timerClock", "reporter"
        };
        String[] values = {
                "\"wlan0\"", "\"224.0.0.251\"", "\"V3\"", "1",
                "false", "0", "false"
        };
        StringBuilder sb = new StringBuilder();
        sb.append("{\"network\":{\"interfaces\":[").append(WLAN_IFACE_JSON).append("],");
        sb.append("\"igmpMemberships\":[{");
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

    private static void assertIgmpSidecar(CapturedEvent e, String path,
                                          int membershipCount, int interfaceCount, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",format=proc-net-igmp,membershipCount=" + membershipCount
                + ",interfaceCount=" + interfaceCount + ",bytes=" + bytes, String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains("读取"));
        assertTrue(e.note.contains("IGMP"));
        assertTrue(e.note.contains(path));
    }

    private static void assertSidecarRedacted(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains("interfaceName"));
        assertFalse(note.contains("interfaceName"));
        assertFalse(value.contains("wlan0"));
        assertFalse(note.contains("wlan0"));
        assertFalse(value.contains("224.0.0"));
        assertFalse(note.contains("224.0.0"));
        assertFalse(value.contains("239.255.255"));
        assertFalse(note.contains("239.255.255"));
        assertFalse(value.contains("FB0000E0"));
        assertFalse(note.contains("FB0000E0"));
        assertFalse(value.contains("FAFFFFEF"));
        assertFalse(note.contains("FAFFFFEF"));
        assertFalse(value.contains("010000E0"));
        assertFalse(note.contains("010000E0"));
        assertFalse(value.contains("querier"));
        assertFalse(note.contains("querier"));
        assertFalse(value.contains("V1"));
        assertFalse(note.contains("V1"));
        assertFalse(value.contains("V2"));
        assertFalse(note.contains("V2"));
        assertFalse(value.contains("V3"));
        assertFalse(note.contains("V3"));
        assertFalse(value.contains("users="));
        assertFalse(value.contains("timerRunning"));
        assertFalse(note.contains("timerRunning"));
        assertFalse(value.contains("timerClock"));
        assertFalse(note.contains("timerClock"));
        assertFalse(value.contains("reporter"));
        assertFalse(note.contains("reporter"));
        assertFalse(value.contains("index="));
        assertFalse(note.contains("index="));
        assertFalse(value.contains("groupIpv4"));
        assertFalse(note.contains("groupIpv4"));
    }

    private static void assertNotTakenOverAsConfiguredIgmp(AndroidEmulator emulator,
                                                           List<MemoryBlock> blocks,
                                                           CapturingSink sink,
                                                           String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 4096);
            assertFalse(path + " must not serve configured igmpMemberships table",
                    EXPECTED_TABLE.equals(text));
            assertFalse(path + " must not serve configured igmpMemberships header-only",
                    ConfiguredIgmpMembershipFiles.HEADER.equals(text) && path.contains("igmp"));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected igmp sidecar for " + path, isIgmpRead(e, path));
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
            assertFalse("write/directory open must not serve configured igmpMemberships",
                    EXPECTED_TABLE.equals(text)
                            || ConfiguredIgmpMembershipFiles.HEADER.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isIgmpRead(e, path));
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

    private static int countIgmpReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isIgmpRead(e, PROC_NET_IGMP)
                    || isIgmpRead(e, PROC_SELF_NET_IGMP)
                    || isIgmpRead(e, PROC_PID_NET_IGMP)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findIgmpRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isIgmpRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isIgmpRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=proc-net-igmp");
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
