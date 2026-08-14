package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.ConfiguredWirelessProcStatsFiles;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * LinuxFileSystem wiring for {@code network.wirelessProcStats} → exact
 * {@code /proc/net/wireless}, {@code /proc/self/net/wireless}, and
 * {@code /proc/<emulatorPid>/net/wireless}. Default backend (no Unicorn2).
 * Every emulator is closed in {@code finally} without swallowing {@code close}.
 */
public class LinuxFileSystemWirelessProcStatsTest {

    private static final int CONFIG_PID = 4242;
    private static final String PROC_NET_WIRELESS = "/proc/net/wireless";
    private static final String PROC_SELF_NET_WIRELESS = "/proc/self/net/wireless";
    private static final String PROC_PID_NET_WIRELESS = "/proc/" + CONFIG_PID + "/net/wireless";

    private static final String LO_IFACE_JSON =
            "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"}";
    private static final String WLAN_IFACE_JSON =
            "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\"}";

    private static final String LO_ENTRY_JSON =
            "{\"interfaceName\":\"lo\",\"status\":0,\"linkQuality\":0,\"level\":0,"
                    + "\"noise\":0,\"linkUpdated\":false,\"levelUpdated\":false,"
                    + "\"noiseUpdated\":false,\"discardNwid\":0,\"discardCrypt\":0,"
                    + "\"discardFragment\":0,\"discardRetries\":0,\"discardMisc\":0,"
                    + "\"missedBeacon\":0}";

    private static final String WLAN_ENTRY_JSON =
            "{\"interfaceName\":\"wlan0\",\"status\":0,\"linkQuality\":70,\"level\":-50,"
                    + "\"noise\":-90,\"linkUpdated\":true,\"levelUpdated\":true,"
                    + "\"noiseUpdated\":true,\"discardNwid\":0,\"discardCrypt\":0,"
                    + "\"discardFragment\":0,\"discardRetries\":1,\"discardMisc\":0,"
                    + "\"missedBeacon\":2}";

    private static final String TWO_ENTRIES_JSON = "{"
            + "\"process\":{\"pid\":" + CONFIG_PID + "},"
            + "\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "," + WLAN_IFACE_JSON + "],"
            + "\"wirelessProcStats\":{\"wirelessExtensionsVersion\":22,"
            + "\"entries\":[" + LO_ENTRY_JSON + "," + WLAN_ENTRY_JSON + "]}}}";

    private static final String EXPECTED_TABLE =
            ConfiguredWirelessProcStatsFiles.renderHeader(22)
                    + "    lo: 0000    0     0     0        0      0      0      0      0        0\n"
                    + " wlan0: 0000   70.  -50.  -90.       0      0      0      1      0        2\n";

    @Test
    public void testParseValidAndInvalid() {
        TraceEnvironmentConfig two = TraceEnvironmentConfig.parse(TWO_ENTRIES_JSON);
        assertTrue(two.isNetworkWirelessProcStatsConfigured());
        assertNotNull(two.getNetworkWirelessProcStats());
        assertEquals(22, two.getNetworkWirelessProcStats().getWirelessExtensionsVersion());
        assertEquals(2, two.getNetworkWirelessProcStatsEntries().size());
        TraceEnvironmentConfig.NetworkWirelessProcStatsEntryConfig lo =
                two.getNetworkWirelessProcStatsEntries().get(0);
        assertEquals("lo", lo.getInterfaceName());
        assertEquals(0, lo.getStatus());
        assertEquals(0, lo.getLinkQuality());
        assertEquals(0, lo.getLevel());
        assertEquals(0, lo.getNoise());
        assertFalse(lo.isLinkUpdated());
        assertFalse(lo.isLevelUpdated());
        assertFalse(lo.isNoiseUpdated());
        assertEquals(0L, lo.getDiscardNwid());
        assertEquals(0L, lo.getDiscardCrypt());
        assertEquals(0L, lo.getDiscardFragment());
        assertEquals(0L, lo.getDiscardRetries());
        assertEquals(0L, lo.getDiscardMisc());
        assertEquals(0L, lo.getMissedBeacon());
        TraceEnvironmentConfig.NetworkWirelessProcStatsEntryConfig wlan =
                two.getNetworkWirelessProcStatsEntries().get(1);
        assertEquals("wlan0", wlan.getInterfaceName());
        assertEquals(70, wlan.getLinkQuality());
        assertEquals(-50, wlan.getLevel());
        assertEquals(-90, wlan.getNoise());
        assertTrue(wlan.isLinkUpdated());
        assertTrue(wlan.isLevelUpdated());
        assertTrue(wlan.isNoiseUpdated());
        assertEquals(1L, wlan.getDiscardRetries());
        assertEquals(2L, wlan.getMissedBeacon());

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"network\":{\"wirelessProcStats\":{\"wirelessExtensionsVersion\":0,"
                        + "\"entries\":[]}}}");
        assertTrue(empty.isNetworkWirelessProcStatsConfigured());
        assertEquals(0, empty.getNetworkWirelessProcStats().getWirelessExtensionsVersion());
        assertTrue(empty.getNetworkWirelessProcStatsEntries().isEmpty());

        assertFalse(TraceEnvironmentConfig.parse("{}").isNetworkWirelessProcStatsConfigured());
        assertFalse(TraceEnvironmentConfig.parse("{\"network\":{}}").isNetworkWirelessProcStatsConfigured());
        assertFalse(TraceEnvironmentConfig.parse(
                "{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "]}}")
                .isNetworkWirelessProcStatsConfigured());
        assertNull(TraceEnvironmentConfig.parse("{}").getNetworkWirelessProcStats());

        TraceEnvironmentConfig bounds = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"wirelessProcStats\":{\"wirelessExtensionsVersion\":999,\"entries\":[{"
                + "\"interfaceName\":\"wlan0\",\"status\":65535,\"linkQuality\":255,"
                + "\"level\":-256,\"noise\":255,\"linkUpdated\":true,\"levelUpdated\":false,"
                + "\"noiseUpdated\":true,\"discardNwid\":4294967295,\"discardCrypt\":0,"
                + "\"discardFragment\":0,\"discardRetries\":0,\"discardMisc\":0,"
                + "\"missedBeacon\":4294967295}]}}}");
        assertEquals(999, bounds.getNetworkWirelessProcStats().getWirelessExtensionsVersion());
        TraceEnvironmentConfig.NetworkWirelessProcStatsEntryConfig max =
                bounds.getNetworkWirelessProcStatsEntries().get(0);
        assertEquals(65535, max.getStatus());
        assertEquals(255, max.getLinkQuality());
        assertEquals(-256, max.getLevel());
        assertEquals(255, max.getNoise());
        assertTrue(max.isLinkUpdated());
        assertFalse(max.isLevelUpdated());
        assertTrue(max.isNoiseUpdated());
        assertEquals(4294967295L, max.getDiscardNwid());
        assertEquals(4294967295L, max.getMissedBeacon());

        assertInvalid("{\"network\":{\"wirelessProcStats\":[]}}", "network.wirelessProcStats");
        assertInvalid("{\"network\":{\"wirelessProcStats\":1}}", "network.wirelessProcStats");
        assertInvalid("{\"network\":{\"wirelessProcStats\":null}}", "network.wirelessProcStats");
        assertInvalid("{\"network\":{\"wirelessProcStats\":{}}}",
                "network.wirelessProcStats.wirelessExtensionsVersion");
        assertInvalid("{\"network\":{\"wirelessProcStats\":{\"wirelessExtensionsVersion\":22}}}",
                "network.wirelessProcStats.entries");
        assertInvalid("{\"network\":{\"wirelessProcStats\":{\"entries\":[]}}}",
                "network.wirelessProcStats.wirelessExtensionsVersion");
        assertInvalid("{\"network\":{\"wirelessProcStats\":{\"wirelessExtensionsVersion\":22,"
                + "\"entries\":[],\"extra\":1}}}", "network.wirelessProcStats.extra");
        assertInvalid(statsWithVersion("-1"), "network.wirelessProcStats.wirelessExtensionsVersion");
        assertInvalid(statsWithVersion("1000"), "network.wirelessProcStats.wirelessExtensionsVersion");
        assertInvalid(statsWithVersion("1.5"), "network.wirelessProcStats.wirelessExtensionsVersion");
        assertInvalid(statsWithVersion("\"22\""), "network.wirelessProcStats.wirelessExtensionsVersion");
        assertInvalid(statsWithVersion("true"), "network.wirelessProcStats.wirelessExtensionsVersion");
        assertInvalid(statsWithVersion("null"), "network.wirelessProcStats.wirelessExtensionsVersion");
        assertInvalid("{\"network\":{\"wirelessProcStats\":{\"wirelessExtensionsVersion\":22,"
                + "\"entries\":{}}}}", "network.wirelessProcStats.entries");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"wirelessProcStats\":{\"wirelessExtensionsVersion\":22,"
                + "\"entries\":[\"wlan0\"]}}}", "network.wirelessProcStats.entries[0]");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"wirelessProcStats\":{\"wirelessExtensionsVersion\":22,"
                + "\"entries\":[1]}}}", "network.wirelessProcStats.entries[0]");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"wirelessProcStats\":{\"wirelessExtensionsVersion\":22,"
                + "\"entries\":[null]}}}", "network.wirelessProcStats.entries[0]");
        assertInvalid("{\"network\":{\"wirelessProcStats\":{\"wirelessExtensionsVersion\":22,"
                + "\"entries\":[" + WLAN_ENTRY_JSON + "]}}}",
                "network.wirelessProcStats.entries[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"wirelessProcStats\":{\"wirelessExtensionsVersion\":22,\"entries\":[{"
                + "\"interfaceName\":\"eth0\",\"status\":0,\"linkQuality\":0,\"level\":0,"
                + "\"noise\":0,\"linkUpdated\":false,\"levelUpdated\":false,"
                + "\"noiseUpdated\":false,\"discardNwid\":0,\"discardCrypt\":0,"
                + "\"discardFragment\":0,\"discardRetries\":0,\"discardMisc\":0,"
                + "\"missedBeacon\":0}]}}}",
                "network.wirelessProcStats.entries[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\"},"
                + "{\"name\":\"wlan0\",\"index\":3,\"ipv4\":\"10.0.0.2\"}],"
                + "\"wirelessProcStats\":{\"wirelessExtensionsVersion\":22,\"entries\":[]}}}",
                "network.interfaces[1].name");
        assertInvalid("{\"network\":{\"interfaces\":[" + LO_IFACE_JSON + "],"
                + "\"wirelessProcStats\":{\"wirelessExtensionsVersion\":22,"
                + "\"entries\":[" + LO_ENTRY_JSON + "," + LO_ENTRY_JSON + "]}}}",
                "network.wirelessProcStats.entries[1].interfaceName");
        assertInvalid(entryWith("\"extra\":1"), "network.wirelessProcStats.entries[0].extra");
        assertInvalid(entryMissing("interfaceName"),
                "network.wirelessProcStats.entries[0].interfaceName");
        assertInvalid(entryMissing("status"), "network.wirelessProcStats.entries[0].status");
        assertInvalid(entryMissing("linkQuality"),
                "network.wirelessProcStats.entries[0].linkQuality");
        assertInvalid(entryMissing("level"), "network.wirelessProcStats.entries[0].level");
        assertInvalid(entryMissing("noise"), "network.wirelessProcStats.entries[0].noise");
        assertInvalid(entryMissing("linkUpdated"),
                "network.wirelessProcStats.entries[0].linkUpdated");
        assertInvalid(entryMissing("missedBeacon"),
                "network.wirelessProcStats.entries[0].missedBeacon");
        assertInvalid(entryField("status", "-1"), "network.wirelessProcStats.entries[0].status");
        assertInvalid(entryField("status", "65536"), "network.wirelessProcStats.entries[0].status");
        assertInvalid(entryField("status", "1.5"), "network.wirelessProcStats.entries[0].status");
        assertInvalid(entryField("status", "\"0\""), "network.wirelessProcStats.entries[0].status");
        assertInvalid(entryField("status", "true"), "network.wirelessProcStats.entries[0].status");
        assertInvalid(entryField("status", "null"), "network.wirelessProcStats.entries[0].status");
        assertInvalid(entryField("linkQuality", "-1"),
                "network.wirelessProcStats.entries[0].linkQuality");
        assertInvalid(entryField("linkQuality", "256"),
                "network.wirelessProcStats.entries[0].linkQuality");
        assertInvalid(entryField("level", "-257"), "network.wirelessProcStats.entries[0].level");
        assertInvalid(entryField("level", "256"), "network.wirelessProcStats.entries[0].level");
        assertInvalid(entryField("level", "1.5"), "network.wirelessProcStats.entries[0].level");
        assertInvalid(entryField("level", "\"-50\""), "network.wirelessProcStats.entries[0].level");
        assertInvalid(entryField("level", "true"), "network.wirelessProcStats.entries[0].level");
        assertInvalid(entryField("noise", "-257"), "network.wirelessProcStats.entries[0].noise");
        assertInvalid(entryField("noise", "256"), "network.wirelessProcStats.entries[0].noise");
        assertInvalid(entryField("linkUpdated", "1"),
                "network.wirelessProcStats.entries[0].linkUpdated");
        assertInvalid(entryField("linkUpdated", "\"true\""),
                "network.wirelessProcStats.entries[0].linkUpdated");
        assertInvalid(entryField("linkUpdated", "null"),
                "network.wirelessProcStats.entries[0].linkUpdated");
        assertInvalid(entryField("levelUpdated", "0"),
                "network.wirelessProcStats.entries[0].levelUpdated");
        assertInvalid(entryField("noiseUpdated", "1"),
                "network.wirelessProcStats.entries[0].noiseUpdated");
        assertInvalid(entryField("discardNwid", "-1"),
                "network.wirelessProcStats.entries[0].discardNwid");
        assertInvalid(entryField("discardNwid", "4294967296"),
                "network.wirelessProcStats.entries[0].discardNwid");
        assertInvalid(entryField("discardNwid", "1.5"),
                "network.wirelessProcStats.entries[0].discardNwid");
        assertInvalid(entryField("discardNwid", "\"0\""),
                "network.wirelessProcStats.entries[0].discardNwid");
        assertInvalid(entryField("discardNwid", "true"),
                "network.wirelessProcStats.entries[0].discardNwid");
        assertInvalid(entryField("discardNwid", "null"),
                "network.wirelessProcStats.entries[0].discardNwid");
        assertInvalid(entryField("missedBeacon", "-1"),
                "network.wirelessProcStats.entries[0].missedBeacon");
        assertInvalid(entryField("missedBeacon", "4294967296"),
                "network.wirelessProcStats.entries[0].missedBeacon");
        assertInvalid(entryField("interfaceName", "1"),
                "network.wirelessProcStats.entries[0].interfaceName");
        assertInvalid(entryField("interfaceName", "\"\""),
                "network.wirelessProcStats.entries[0].interfaceName");
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
            assertTrue(EXPECTED_TABLE.startsWith(ConfiguredWirelessProcStatsFiles.HEADER_LINE1));
            assertTrue(EXPECTED_TABLE.contains(
                    " face | tus | link level noise |  nwid  crypt   frag  retry   misc | beacon | 22\n"));
            int loPos = EXPECTED_TABLE.indexOf("lo:");
            int wlanPos = EXPECTED_TABLE.indexOf("wlan0:");
            assertTrue(loPos > 0);
            assertTrue(wlanPos > loPos);
            assertTrue(EXPECTED_TABLE.contains(" 70."));
            assertTrue(EXPECTED_TABLE.contains("-50."));
            assertTrue(EXPECTED_TABLE.contains("-90."));
            assertTrue(EXPECTED_TABLE.endsWith("\n"));
            assertFalse(EXPECTED_TABLE.contains("\r"));

            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_NET_WIRELESS));
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_SELF_NET_WIRELESS));
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_PID_NET_WIRELESS));

            assertEquals(3, countWirelessReads(sink));
            CapturedEvent e0 = findWirelessRead(sink, PROC_NET_WIRELESS);
            assertWirelessSidecar(e0, PROC_NET_WIRELESS, 2, expected.length);
            assertSidecarRedacted(e0);
            assertWirelessSidecar(findWirelessRead(sink, PROC_SELF_NET_WIRELESS),
                    PROC_SELF_NET_WIRELESS, 2, expected.length);
            assertSidecarRedacted(findWirelessRead(sink, PROC_SELF_NET_WIRELESS));
            assertWirelessSidecar(findWirelessRead(sink, PROC_PID_NET_WIRELESS),
                    PROC_PID_NET_WIRELESS, 2, expected.length);
            assertSidecarRedacted(findWirelessRead(sink, PROC_PID_NET_WIRELESS));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testExplicitEmptyEntriesHeaderOnly() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"process\":{\"pid\":" + CONFIG_PID + "},"
                        + "\"network\":{\"wirelessProcStats\":{\"wirelessExtensionsVersion\":22,"
                        + "\"entries\":[]}}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            String header = ConfiguredWirelessProcStatsFiles.renderHeader(22);
            byte[] expected = header.getBytes(StandardCharsets.UTF_8);
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_NET_WIRELESS));
            assertEquals(header, readOpenText(emulator, blocks, PROC_SELF_NET_WIRELESS));
            assertTrue(header.endsWith("\n"));
            assertTrue(header.startsWith(ConfiguredWirelessProcStatsFiles.HEADER_LINE1));
            assertTrue(header.contains("| 22\n"));
            assertFalse(header.contains("wlan0"));
            assertFalse(header.contains("lo:"));
            CapturedEvent e = findWirelessRead(sink, PROC_NET_WIRELESS);
            assertWirelessSidecar(e, PROC_NET_WIRELESS, 0, expected.length);
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
            assertNotTakenOverAsConfiguredWireless(emulator, blocks, sink, PROC_NET_WIRELESS);
            assertNotTakenOverAsConfiguredWireless(emulator, blocks, sink, PROC_SELF_NET_WIRELESS);
            assertEquals(0, countWirelessReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredWireless(emulator, blocks, sink, PROC_NET_WIRELESS);
            assertEquals(0, countWirelessReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesTakesPriority() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"files\":{\"" + PROC_NET_WIRELESS + "\":\"CUSTOM_WIRELESS\\n\"}},"
                + TWO_ENTRIES_JSON.substring(TWO_ENTRIES_JSON.indexOf("\"network\"")));
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals("CUSTOM_WIRELESS\n", readOpenText(emulator, blocks, PROC_NET_WIRELESS));
            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                if (isWirelessRead(e, PROC_NET_WIRELESS)) {
                    fail("network_device wirelessProcStats sidecar must not fire when linux.files wins");
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

            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_WIRELESS, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_WIRELESS, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_WIRELESS, IOConstants.O_DIRECTORY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_SELF_NET_WIRELESS,
                    IOConstants.O_RDONLY | IOConstants.O_DIRECTORY);

            assertNotTakenOverAsConfiguredWireless(emulator, blocks, sink, "/proc/net/wirelesss");
            assertNotTakenOverAsConfiguredWireless(emulator, blocks, sink, "/proc/net/wireless/");
            assertNotTakenOverAsConfiguredWireless(emulator, blocks, sink, "/proc/net/dev");
            assertNotTakenOverAsConfiguredWireless(emulator, blocks, sink, "/proc/net/igmp6");
            assertNotTakenOverAsConfiguredWireless(emulator, blocks, sink, "/proc/self/net/wirelesss");
            assertNotTakenOverAsConfiguredWireless(emulator, blocks, sink, "/proc/1/net/wireless");
            assertNotTakenOverAsConfiguredWireless(emulator, blocks, sink, "/proc/net/Wireless");
            assertEquals(0, countWirelessReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    private static String statsWithVersion(String rawVersion) {
        return "{\"network\":{\"wirelessProcStats\":{\"wirelessExtensionsVersion\":"
                + rawVersion + ",\"entries\":[]}}}";
    }

    private static String entryWith(String extraField) {
        return "{"
                + "\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"wirelessProcStats\":{\"wirelessExtensionsVersion\":22,\"entries\":[{"
                + "\"interfaceName\":\"wlan0\",\"status\":0,\"linkQuality\":0,\"level\":0,"
                + "\"noise\":0,\"linkUpdated\":false,\"levelUpdated\":false,"
                + "\"noiseUpdated\":false,\"discardNwid\":0,\"discardCrypt\":0,"
                + "\"discardFragment\":0,\"discardRetries\":0,\"discardMisc\":0,"
                + "\"missedBeacon\":0,"
                + extraField + "}]}}}";
    }

    private static String entryMissing(String field) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"network\":{\"interfaces\":[").append(WLAN_IFACE_JSON).append("],");
        sb.append("\"wirelessProcStats\":{\"wirelessExtensionsVersion\":22,\"entries\":[{");
        boolean first = true;
        String[] keys = ENTRY_KEYS;
        String[] values = ENTRY_ZERO_VALUES;
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
        sb.append("}]}}}");
        return sb.toString();
    }

    private static String entryField(String field, String rawValue) {
        String[] keys = ENTRY_KEYS;
        String[] values = ENTRY_ZERO_VALUES;
        StringBuilder sb = new StringBuilder();
        sb.append("{\"network\":{\"interfaces\":[").append(WLAN_IFACE_JSON).append("],");
        sb.append("\"wirelessProcStats\":{\"wirelessExtensionsVersion\":22,\"entries\":[{");
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(keys[i]).append("\":");
            sb.append(keys[i].equals(field) ? rawValue : values[i]);
        }
        sb.append("}]}}}");
        return sb.toString();
    }

    private static final String[] ENTRY_KEYS = {
            "interfaceName", "status", "linkQuality", "level", "noise",
            "linkUpdated", "levelUpdated", "noiseUpdated",
            "discardNwid", "discardCrypt", "discardFragment", "discardRetries",
            "discardMisc", "missedBeacon"
    };

    private static final String[] ENTRY_ZERO_VALUES = {
            "\"wlan0\"", "0", "0", "0", "0",
            "false", "false", "false",
            "0", "0", "0", "0",
            "0", "0"
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

    private static void assertWirelessSidecar(CapturedEvent e, String path, int interfaceCount, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",format=proc-net-wireless,interfaceCount=" + interfaceCount
                + ",bytes=" + bytes, String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains("读取"));
        assertTrue(e.note.contains("无线"));
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
        assertFalse(value.contains("wirelessExtensionsVersion"));
        assertFalse(note.contains("wirelessExtensionsVersion"));
        assertFalse(value.contains("linkQuality"));
        assertFalse(note.contains("linkQuality"));
        assertFalse(value.contains("discardNwid"));
        assertFalse(note.contains("discardNwid"));
        assertFalse(value.contains("missedBeacon"));
        assertFalse(note.contains("missedBeacon"));
        assertFalse(value.contains("-50"));
        assertFalse(note.contains("-50"));
        assertFalse(value.contains("-90"));
        assertFalse(note.contains("-90"));
        assertFalse(value.contains("70."));
        assertFalse(note.contains("70."));
        assertFalse(value.contains("192.168.50"));
        assertFalse(note.contains("192.168.50"));
    }

    private static void assertNotTakenOverAsConfiguredWireless(AndroidEmulator emulator,
                                                              List<MemoryBlock> blocks,
                                                              CapturingSink sink,
                                                              String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 4096);
            assertFalse(path + " must not serve configured wirelessProcStats table",
                    EXPECTED_TABLE.equals(text));
            assertFalse(path + " must not serve configured wirelessProcStats header-only",
                    ConfiguredWirelessProcStatsFiles.renderHeader(22).equals(text)
                            && path.toLowerCase().contains("wireless"));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected wirelessProcStats sidecar for " + path, isWirelessRead(e, path));
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
            assertFalse("write/directory open must not serve configured wirelessProcStats",
                    EXPECTED_TABLE.equals(text)
                            || ConfiguredWirelessProcStatsFiles.renderHeader(22).equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isWirelessRead(e, path));
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

    private static int countWirelessReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isWirelessRead(e, PROC_NET_WIRELESS)
                    || isWirelessRead(e, PROC_SELF_NET_WIRELESS)
                    || isWirelessRead(e, PROC_PID_NET_WIRELESS)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findWirelessRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isWirelessRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isWirelessRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=proc-net-wireless");
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
