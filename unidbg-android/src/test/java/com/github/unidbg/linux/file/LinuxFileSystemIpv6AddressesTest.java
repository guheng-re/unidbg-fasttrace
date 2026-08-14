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
 * LinuxFileSystem wiring for {@code network.ipv6Addresses} → exact
 * {@code /proc/net/if_inet6}, {@code /proc/self/net/if_inet6}, and
 * {@code /proc/<emulatorPid>/net/if_inet6}. Default backend (no Unicorn2).
 * Every emulator is closed in {@code finally} without swallowing {@code close}.
 */
public class LinuxFileSystemIpv6AddressesTest {

    private static final int CONFIG_PID = 4242;
    private static final String PROC_NET_IF_INET6 = "/proc/net/if_inet6";
    private static final String PROC_SELF_NET_IF_INET6 = "/proc/self/net/if_inet6";
    private static final String PROC_PID_NET_IF_INET6 = "/proc/" + CONFIG_PID + "/net/if_inet6";

    private static final String LO_IFACE_JSON =
            "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"}";
    private static final String WLAN_IFACE_JSON =
            "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\"}";

    private static final String LO_ADDR_JSON =
            "{\"interfaceName\":\"lo\",\"addressHex\":\"00000000000000000000000000000001\","
                    + "\"prefixLength\":128,\"scope\":16,\"flags\":128}";
    private static final String WLAN_ADDR_JSON =
            "{\"interfaceName\":\"wlan0\",\"addressHex\":\"fe800000000000000000000000000001\","
                    + "\"prefixLength\":64,\"scope\":32,\"flags\":128}";

    private static final String TWO_ADDRS_JSON = "{"
            + "\"process\":{\"pid\":" + CONFIG_PID + "},"
            + "\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "," + WLAN_IFACE_JSON + "],"
            + "\"ipv6Addresses\":[" + LO_ADDR_JSON + "," + WLAN_ADDR_JSON + "]}}";

    private static final String EXPECTED_TABLE =
            "00000000000000000000000000000001 01 80 10 80 lo\n"
                    + "fe800000000000000000000000000001 02 40 20 80 wlan0\n";

    @Test
    public void testParseValidAndInvalid() {
        TraceEnvironmentConfig two = TraceEnvironmentConfig.parse(TWO_ADDRS_JSON);
        assertTrue(two.isNetworkIpv6AddressesConfigured());
        assertEquals(2, two.getNetworkIpv6Addresses().size());
        TraceEnvironmentConfig.NetworkIpv6AddressConfig lo = two.getNetworkIpv6Addresses().get(0);
        assertEquals("lo", lo.getInterfaceName());
        assertEquals("00000000000000000000000000000001", lo.getAddressHex());
        assertEquals(128, lo.getPrefixLength());
        assertEquals(16, lo.getScope());
        assertEquals(128, lo.getFlags());
        TraceEnvironmentConfig.NetworkIpv6AddressConfig wlan = two.getNetworkIpv6Addresses().get(1);
        assertEquals("wlan0", wlan.getInterfaceName());
        assertEquals("fe800000000000000000000000000001", wlan.getAddressHex());
        assertEquals(64, wlan.getPrefixLength());
        assertEquals(32, wlan.getScope());
        assertEquals(128, wlan.getFlags());

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{\"network\":{\"ipv6Addresses\":[]}}");
        assertTrue(empty.isNetworkIpv6AddressesConfigured());
        assertTrue(empty.getNetworkIpv6Addresses().isEmpty());

        assertFalse(TraceEnvironmentConfig.parse("{}").isNetworkIpv6AddressesConfigured());
        assertFalse(TraceEnvironmentConfig.parse("{\"network\":{}}").isNetworkIpv6AddressesConfigured());
        assertFalse(TraceEnvironmentConfig.parse(
                "{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "]}}")
                .isNetworkIpv6AddressesConfigured());

        TraceEnvironmentConfig mixedCase = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "],"
                + "\"ipv6Addresses\":[{\"interfaceName\":\"lo\","
                + "\"addressHex\":\"0000000000000000000000000000000A\","
                + "\"prefixLength\":0,\"scope\":0,\"flags\":255}]}}");
        assertEquals("0000000000000000000000000000000a",
                mixedCase.getNetworkIpv6Addresses().get(0).getAddressHex());
        assertEquals(0, mixedCase.getNetworkIpv6Addresses().get(0).getPrefixLength());
        assertEquals(0, mixedCase.getNetworkIpv6Addresses().get(0).getScope());
        assertEquals(255, mixedCase.getNetworkIpv6Addresses().get(0).getFlags());

        assertInvalid("{\"network\":{\"ipv6Addresses\":{}}}", "network.ipv6Addresses");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"ipv6Addresses\":[\"wlan0\"]}}", "network.ipv6Addresses[0]");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"ipv6Addresses\":[1]}}", "network.ipv6Addresses[0]");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"ipv6Addresses\":[null]}}", "network.ipv6Addresses[0]");
        assertInvalid("{\"network\":{\"ipv6Addresses\":[" + LO_ADDR_JSON + "]}}",
                "network.ipv6Addresses[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"ipv6Addresses\":[{\"interfaceName\":\"eth0\","
                + "\"addressHex\":\"00000000000000000000000000000001\","
                + "\"prefixLength\":128,\"scope\":16,\"flags\":128}]}}",
                "network.ipv6Addresses[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\"},"
                + "{\"name\":\"wlan0\",\"index\":3,\"ipv4\":\"10.0.0.2\"}],"
                + "\"ipv6Addresses\":[]}}",
                "network.interfaces[1].name");
        assertInvalid("{\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "],"
                + "\"ipv6Addresses\":[" + LO_ADDR_JSON + "," + LO_ADDR_JSON + "]}}",
                "network.ipv6Addresses[1].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "],"
                + "\"ipv6Addresses\":[" + LO_ADDR_JSON + ","
                + "{\"interfaceName\":\"lo\",\"addressHex\":\"00000000000000000000000000000001\","
                + "\"prefixLength\":64,\"scope\":0,\"flags\":0}]}}",
                "network.ipv6Addresses[1].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "],"
                + "\"ipv6Addresses\":["
                + "{\"interfaceName\":\"lo\",\"addressHex\":\"0000000000000000000000000000000A\","
                + "\"prefixLength\":128,\"scope\":16,\"flags\":128},"
                + "{\"interfaceName\":\"lo\",\"addressHex\":\"0000000000000000000000000000000a\","
                + "\"prefixLength\":64,\"scope\":0,\"flags\":0}]}}",
                "network.ipv6Addresses[1].interfaceName");
        assertInvalid(addrWith("\"extra\":1"), "network.ipv6Addresses[0].extra");
        assertInvalid(addrMissing("addressHex"), "network.ipv6Addresses[0].addressHex");
        assertInvalid(addrMissing("prefixLength"), "network.ipv6Addresses[0].prefixLength");
        assertInvalid(addrMissing("scope"), "network.ipv6Addresses[0].scope");
        assertInvalid(addrMissing("flags"), "network.ipv6Addresses[0].flags");
        assertInvalid(addrField("addressHex", "\"0000000000000000000000000000000\""),
                "network.ipv6Addresses[0].addressHex");
        assertInvalid(addrField("addressHex", "\"000000000000000000000000000000001\""),
                "network.ipv6Addresses[0].addressHex");
        assertInvalid(addrField("addressHex", "\"0000000000000000000000000000000g\""),
                "network.ipv6Addresses[0].addressHex");
        assertInvalid(addrField("addressHex", "\"fe80:0000000000000000000000000001\""),
                "network.ipv6Addresses[0].addressHex");
        assertInvalid(addrField("addressHex", "\"0000000000000000000000000000000 \""),
                "network.ipv6Addresses[0].addressHex");
        assertInvalid(addrField("addressHex", "\"fe80::1\""),
                "network.ipv6Addresses[0].addressHex");
        assertInvalid(addrField("addressHex", "\"127.0.0.1\""),
                "network.ipv6Addresses[0].addressHex");
        assertInvalid(addrField("addressHex", "\"localhost\""),
                "network.ipv6Addresses[0].addressHex");
        assertInvalid(addrField("addressHex", "1"), "network.ipv6Addresses[0].addressHex");
        assertInvalid(addrField("addressHex", "true"), "network.ipv6Addresses[0].addressHex");
        assertInvalid(addrField("addressHex", "null"), "network.ipv6Addresses[0].addressHex");
        assertInvalid(addrField("prefixLength", "-1"), "network.ipv6Addresses[0].prefixLength");
        assertInvalid(addrField("prefixLength", "129"), "network.ipv6Addresses[0].prefixLength");
        assertInvalid(addrField("prefixLength", "1.5"), "network.ipv6Addresses[0].prefixLength");
        assertInvalid(addrField("prefixLength", "\"64\""), "network.ipv6Addresses[0].prefixLength");
        assertInvalid(addrField("prefixLength", "true"), "network.ipv6Addresses[0].prefixLength");
        assertInvalid(addrField("prefixLength", "false"), "network.ipv6Addresses[0].prefixLength");
        assertInvalid(addrField("prefixLength", "null"), "network.ipv6Addresses[0].prefixLength");
        assertInvalid(addrField("scope", "-1"), "network.ipv6Addresses[0].scope");
        assertInvalid(addrField("scope", "256"), "network.ipv6Addresses[0].scope");
        assertInvalid(addrField("scope", "1.5"), "network.ipv6Addresses[0].scope");
        assertInvalid(addrField("scope", "\"16\""), "network.ipv6Addresses[0].scope");
        assertInvalid(addrField("scope", "true"), "network.ipv6Addresses[0].scope");
        assertInvalid(addrField("scope", "null"), "network.ipv6Addresses[0].scope");
        assertInvalid(addrField("flags", "-1"), "network.ipv6Addresses[0].flags");
        assertInvalid(addrField("flags", "256"), "network.ipv6Addresses[0].flags");
        assertInvalid(addrField("flags", "1.5"), "network.ipv6Addresses[0].flags");
        assertInvalid(addrField("flags", "\"128\""), "network.ipv6Addresses[0].flags");
        assertInvalid(addrField("flags", "true"), "network.ipv6Addresses[0].flags");
        assertInvalid(addrField("flags", "null"), "network.ipv6Addresses[0].flags");
        assertInvalid(addrField("interfaceName", "1"), "network.ipv6Addresses[0].interfaceName");

        TraceEnvironmentConfig highIndexWithoutIpv6 = TraceEnvironmentConfig.parse(
                "{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":256,\"ipv4\":\"127.0.0.1\"}]}}");
        assertFalse(highIndexWithoutIpv6.isNetworkIpv6AddressesConfigured());
        TraceEnvironmentConfig highIndexEmptyIpv6 = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":256,\"ipv4\":\"127.0.0.1\"}],"
                + "\"ipv6Addresses\":[]}}");
        assertTrue(highIndexEmptyIpv6.isNetworkIpv6AddressesConfigured());
        assertTrue(highIndexEmptyIpv6.getNetworkIpv6Addresses().isEmpty());
        TraceEnvironmentConfig index255 = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":255,\"ipv4\":\"127.0.0.1\"}],"
                + "\"ipv6Addresses\":[" + LO_ADDR_JSON + "]}}");
        assertEquals(255, index255.getNetworkInterfaces().get(0).getIndex());
        assertInvalid("{\"network\":{\"interfaces\":["
                + "{\"name\":\"lo\",\"index\":256,\"ipv4\":\"127.0.0.1\"}],"
                + "\"ipv6Addresses\":[" + LO_ADDR_JSON + "]}}",
                "network.ipv6Addresses[0].interfaceName");
    }

    @Test
    public void testFullTableBytesAndThreeAliases() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_ADDRS_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            assertEquals(CONFIG_PID, emulator.getPid());
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] expected = EXPECTED_TABLE.getBytes(StandardCharsets.UTF_8);
            assertEquals(EXPECTED_TABLE, new String(expected, StandardCharsets.UTF_8));
            int loPos = EXPECTED_TABLE.indexOf("00000000000000000000000000000001");
            int wlanPos = EXPECTED_TABLE.indexOf("fe800000000000000000000000000001");
            assertTrue(loPos == 0);
            assertTrue(wlanPos > loPos);
            assertTrue(EXPECTED_TABLE.contains(" 01 80 10 80 lo\n"));
            assertTrue(EXPECTED_TABLE.contains(" 02 40 20 80 wlan0\n"));
            assertTrue(EXPECTED_TABLE.endsWith("\n"));
            assertFalse(EXPECTED_TABLE.contains("\r"));
            assertFalse(EXPECTED_TABLE.contains("::"));
            assertFalse(EXPECTED_TABLE.contains("\t"));
            assertIndexColumnExactlyTwoLowerHexDigits(EXPECTED_TABLE);

            byte[] actualNet = readOpenBytes(emulator, blocks, PROC_NET_IF_INET6);
            assertArrayEqualsBytes(expected, actualNet);
            assertIndexColumnExactlyTwoLowerHexDigits(new String(actualNet, StandardCharsets.UTF_8));
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_SELF_NET_IF_INET6));
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_PID_NET_IF_INET6));

            assertEquals(3, countInet6Reads(sink));
            CapturedEvent e0 = findInet6Read(sink, PROC_NET_IF_INET6);
            assertInet6Sidecar(e0, PROC_NET_IF_INET6, 2, expected.length);
            assertSidecarRedacted(e0);
            assertInet6Sidecar(findInet6Read(sink, PROC_SELF_NET_IF_INET6),
                    PROC_SELF_NET_IF_INET6, 2, expected.length);
            assertSidecarRedacted(findInet6Read(sink, PROC_SELF_NET_IF_INET6));
            assertInet6Sidecar(findInet6Read(sink, PROC_PID_NET_IF_INET6),
                    PROC_PID_NET_IF_INET6, 2, expected.length);
            assertSidecarRedacted(findInet6Read(sink, PROC_PID_NET_IF_INET6));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testIndex255RendersFf() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"lo\",\"index\":255,\"ipv4\":\"127.0.0.1\"}],"
                + "\"ipv6Addresses\":[" + LO_ADDR_JSON + "]}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            String text = readOpenText(emulator, blocks, PROC_NET_IF_INET6);
            assertEquals("00000000000000000000000000000001 ff 80 10 80 lo\n", text);
            assertIndexColumnExactlyTwoLowerHexDigits(text);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testExplicitEmptyArrayZeroBytes() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"process\":{\"pid\":" + CONFIG_PID + "},\"network\":{\"ipv6Addresses\":[]}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            byte[] expected = new byte[0];
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_NET_IF_INET6));
            assertEquals("", readOpenText(emulator, blocks, PROC_SELF_NET_IF_INET6));
            assertEquals(0, readOpenBytes(emulator, blocks, PROC_PID_NET_IF_INET6).length);
            CapturedEvent e = findInet6Read(sink, PROC_NET_IF_INET6);
            assertInet6Sidecar(e, PROC_NET_IF_INET6, 0, 0);
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
            assertNotTakenOverAsConfiguredInet6(emulator, blocks, sink, PROC_NET_IF_INET6);
            assertNotTakenOverAsConfiguredInet6(emulator, blocks, sink, PROC_SELF_NET_IF_INET6);
            assertEquals(0, countInet6Reads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredInet6(emulator, blocks, sink, PROC_NET_IF_INET6);
            assertEquals(0, countInet6Reads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesTakesPriority() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"files\":{\"" + PROC_NET_IF_INET6 + "\":\"CUSTOM_INET6\\n\"}},"
                + TWO_ADDRS_JSON.substring(TWO_ADDRS_JSON.indexOf("\"network\"")));
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals("CUSTOM_INET6\n", readOpenText(emulator, blocks, PROC_NET_IF_INET6));
            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                if (isInet6Read(e, PROC_NET_IF_INET6)) {
                    fail("network_device ipv6Addresses sidecar must not fire when linux.files wins");
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
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_ADDRS_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_IF_INET6, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_IF_INET6, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_IF_INET6, IOConstants.O_DIRECTORY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_SELF_NET_IF_INET6,
                    IOConstants.O_RDONLY | IOConstants.O_DIRECTORY);

            assertNotTakenOverAsConfiguredInet6(emulator, blocks, sink, "/proc/net/if_inet6s");
            assertNotTakenOverAsConfiguredInet6(emulator, blocks, sink, "/proc/net/if_inet6/");
            assertNotTakenOverAsConfiguredInet6(emulator, blocks, sink, "/proc/net/ipv6_route");
            assertNotTakenOverAsConfiguredInet6(emulator, blocks, sink, "/proc/net/route");
            assertNotTakenOverAsConfiguredInet6(emulator, blocks, sink, "/proc/net/dev");
            assertNotTakenOverAsConfiguredInet6(emulator, blocks, sink, "/proc/self/net/if_inet6s");
            assertNotTakenOverAsConfiguredInet6(emulator, blocks, sink, "/proc/1/net/if_inet6");
            assertNotTakenOverAsConfiguredInet6(emulator, blocks, sink, "/proc/net/If_inet6");
            assertEquals(0, countInet6Reads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    private static String addrWith(String extraField) {
        return "{"
                + "\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"ipv6Addresses\":[{"
                + "\"interfaceName\":\"wlan0\",\"addressHex\":\"fe800000000000000000000000000001\","
                + "\"prefixLength\":64,\"scope\":32,\"flags\":128,"
                + extraField + "}]}}";
    }

    private static String addrMissing(String field) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"network\":{\"interfaces\":[").append(WLAN_IFACE_JSON).append("],");
        sb.append("\"ipv6Addresses\":[{");
        boolean first = true;
        String[] keys = ADDR_KEYS;
        String[] values = ADDR_VALUES;
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

    private static String addrField(String field, String rawValue) {
        String[] keys = ADDR_KEYS;
        String[] values = ADDR_VALUES;
        StringBuilder sb = new StringBuilder();
        sb.append("{\"network\":{\"interfaces\":[").append(WLAN_IFACE_JSON).append("],");
        sb.append("\"ipv6Addresses\":[{");
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

    private static final String[] ADDR_KEYS = {
            "interfaceName", "addressHex", "prefixLength", "scope", "flags"
    };

    private static final String[] ADDR_VALUES = {
            "\"wlan0\"", "\"fe800000000000000000000000000001\"", "64", "32", "128"
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

    private static void assertIndexColumnExactlyTwoLowerHexDigits(String table) {
        String[] lines = table.split("\n", -1);
        int dataLines = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                continue;
            }
            dataLines++;
            String[] cols = line.split(" ", -1);
            assertEquals("if_inet6 line must have 6 columns: " + line, 6, cols.length);
            assertEquals("index column must be exactly two digits: " + line, 2, cols[1].length());
            for (int c = 0; c < 2; c++) {
                char ch = cols[1].charAt(c);
                boolean hex = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f');
                assertTrue("index column must be lowercase hex: " + cols[1], hex);
            }
        }
        assertTrue("expected at least one if_inet6 data line", dataLines > 0);
    }

    private static void assertInet6Sidecar(CapturedEvent e, String path, int addressCount, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",format=proc-net-if-inet6,addressCount=" + addressCount
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
        assertFalse(value.contains("interfaceName"));
        assertFalse(note.contains("interfaceName"));
        assertFalse(value.contains("addressHex"));
        assertFalse(note.contains("addressHex"));
        assertFalse(value.contains("prefixLength"));
        assertFalse(note.contains("prefixLength"));
        assertFalse(value.contains("00000000000000000000000000000001"));
        assertFalse(note.contains("00000000000000000000000000000001"));
        assertFalse(value.contains("fe800000000000000000000000000001"));
        assertFalse(note.contains("fe800000000000000000000000000001"));
        assertFalse(value.contains("fe80::"));
        assertFalse(note.contains("fe80::"));
        assertFalse(value.contains("::1"));
        assertFalse(note.contains("::1"));
        assertFalse(value.contains("192.168.50"));
        assertFalse(note.contains("192.168.50"));
        assertFalse(value.contains("127.0.0.1"));
        assertFalse(note.contains("127.0.0.1"));
        assertFalse(value.contains("02:54:52"));
        assertFalse(note.contains("02:54:52"));
        assertFalse(value.contains(",scope="));
        assertFalse(note.contains("scope"));
        assertFalse(value.contains(",flags="));
        assertFalse(note.contains("flags"));
        assertFalse(value.contains("prefix"));
        assertFalse(note.contains("prefix"));
    }

    private static void assertNotTakenOverAsConfiguredInet6(AndroidEmulator emulator,
                                                            List<MemoryBlock> blocks,
                                                            CapturingSink sink,
                                                            String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 4096);
            assertFalse(path + " must not serve configured ipv6Addresses table",
                    EXPECTED_TABLE.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected ipv6Addresses sidecar for " + path, isInet6Read(e, path));
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
            assertFalse("write/directory open must not serve configured ipv6Addresses",
                    EXPECTED_TABLE.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isInet6Read(e, path));
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

    private static int countInet6Reads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isInet6Read(e, PROC_NET_IF_INET6)
                    || isInet6Read(e, PROC_SELF_NET_IF_INET6)
                    || isInet6Read(e, PROC_PID_NET_IF_INET6)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findInet6Read(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isInet6Read(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isInet6Read(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=proc-net-if-inet6");
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
