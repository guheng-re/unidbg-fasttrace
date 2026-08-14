package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.EmulatorBuilder;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * LinuxFileSystem wiring for {@code network.interfaces[].duplex} → exact
 * {@code /sys/class/net/<name>/duplex} (UTF-8 lowercase full/half/unknown + LF).
 * Uses the default backend (no Unicorn2) so emulator close does not hit Unicorn2
 * lifecycle crashes. Never infers duplex from {@code speedMbps}, carrier, operState,
 * hardwareType, flags, name, or {@code network.wifi}.
 */
public class LinuxFileSystemNetworkDuplexTest {

    private static final String WLAN_DUPLEX_PATH = "/sys/class/net/wlan0/duplex";
    private static final String ETH_DUPLEX_PATH = "/sys/class/net/eth0/duplex";
    private static final String LO_DUPLEX_PATH = "/sys/class/net/lo/duplex";
    private static final String UNKNOWN_DUPLEX_PATH = "/sys/class/net/not0/duplex";
    private static final String WLAN_SPEED_PATH = "/sys/class/net/wlan0/speed";
    private static final String WLAN_ADDRESS_PATH = "/sys/class/net/wlan0/address";
    private static final String WLAN_MTU_PATH = "/sys/class/net/wlan0/mtu";
    private static final String WLAN_TYPE_PATH = "/sys/class/net/wlan0/type";
    private static final String WLAN_OPERSTATE_PATH = "/sys/class/net/wlan0/operstate";
    private static final String WLAN_CARRIER_PATH = "/sys/class/net/wlan0/carrier";
    private static final String WLAN_FLAGS_PATH = "/sys/class/net/wlan0/flags";
    private static final String WLAN_IFINDEX_PATH = "/sys/class/net/wlan0/ifindex";
    private static final String ETH_SPEED_PATH = "/sys/class/net/eth0/speed";
    private static final String ETH_ADDRESS_PATH = "/sys/class/net/eth0/address";
    private static final String ETH_MTU_PATH = "/sys/class/net/eth0/mtu";
    private static final String ETH_TYPE_PATH = "/sys/class/net/eth0/type";
    private static final String ETH_OPERSTATE_PATH = "/sys/class/net/eth0/operstate";
    private static final String ETH_CARRIER_PATH = "/sys/class/net/eth0/carrier";

    private static final String WLAN_MAC = "02:00:00:00:00:01";
    private static final String ETH_MAC_JSON = "AA:BB:CC:DD:EE:FF";
    private static final String ETH_MAC_CANONICAL = "aa:bb:cc:dd:ee:ff";

    private static final int LO_FLAGS = 73;
    private static final int ETH_FLAGS = 4163;
    private static final int WLAN_FLAGS = 1;
    private static final int WLAN_MTU = 1500;

    private static final String LO_OPERSTATE = "down";
    private static final String ETH_OPERSTATE = "up";

    private static final int LO_HARDWARE_TYPE = 772;
    private static final int ETH_HARDWARE_TYPE = 1;
    private static final int WLAN_HARDWARE_TYPE = 1;

    private static final int WLAN_SPEED = 866;
    private static final int LO_SPEED = -1;
    private static final int ZERO_SPEED = 0;

    private static final String LO_DUPLEX = "unknown";
    private static final String WLAN_DUPLEX = "full";
    private static final String HALF_DUPLEX = "half";
    private static final String LO_DUPLEX_TEXT = LO_DUPLEX + "\n";
    private static final String WLAN_DUPLEX_TEXT = WLAN_DUPLEX + "\n";
    private static final String HALF_DUPLEX_TEXT = HALF_DUPLEX + "\n";

    private static final String[] DUPLEX_VALUES = {"full", "half", "unknown"};

    private static final String TWO_IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"flags\":" + LO_FLAGS
            + ",\"operState\":\"" + LO_OPERSTATE + "\",\"carrier\":true"
            + ",\"hardwareType\":" + LO_HARDWARE_TYPE
            + ",\"speedMbps\":" + LO_SPEED
            + ",\"duplex\":\"" + LO_DUPLEX + "\"},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
            + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU + ",\"flags\":" + WLAN_FLAGS
            + ",\"hardwareType\":" + WLAN_HARDWARE_TYPE
            + ",\"speedMbps\":" + WLAN_SPEED
            + ",\"duplex\":\"" + WLAN_DUPLEX + "\"},"
            + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
            + "\"mac\":\"" + ETH_MAC_JSON + "\",\"flags\":" + ETH_FLAGS
            + ",\"operState\":\"" + ETH_OPERSTATE + "\",\"carrier\":false"
            + ",\"hardwareType\":" + ETH_HARDWARE_TYPE
            + ",\"speedMbps\":" + ZERO_SPEED + "}"
            + "]}}"
            ;

    @Test
    public void testDuplexParseValidAndInvalid() {
        for (int i = 0; i < DUPLEX_VALUES.length; i++) {
            String duplex = DUPLEX_VALUES[i];
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                    + "\"network\":{\"interfaces\":["
                    + "{\"name\":\"if" + i + "\",\"index\":" + (i + 1)
                    + ",\"ipv4\":\"10.0.0." + (i + 1) + "\",\"duplex\":\"" + duplex + "\"}"
                    + "]}}"
            );
            TraceEnvironmentConfig.NetworkInterfaceConfig iface =
                    config.getNetworkInterfaces().get(0);
            assertTrue(duplex, iface.isDuplexConfigured());
            assertEquals(duplex, iface.getDuplex());
            assertFalse(iface.isSpeedMbpsConfigured());
            assertNull(iface.getSpeedMbps());
            assertFalse(iface.isOperStateConfigured());
            assertNull(iface.getOperState());
            assertFalse(iface.isCarrierConfigured());
            assertFalse(iface.isHardwareTypeConfigured());
            assertNull(iface.getFlags());
            assertNull(iface.getMac());
        }

        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        TraceEnvironmentConfig.NetworkInterfaceConfig lo = omitted.getNetworkInterfaces().get(0);
        assertTrue(lo.isDuplexConfigured());
        assertEquals(LO_DUPLEX, lo.getDuplex());
        assertTrue(lo.isSpeedMbpsConfigured());
        assertTrue(lo.isOperStateConfigured());
        assertTrue(lo.isCarrierConfigured());
        TraceEnvironmentConfig.NetworkInterfaceConfig wlan0 = omitted.getNetworkInterfaces().get(1);
        assertTrue(wlan0.isDuplexConfigured());
        assertEquals(WLAN_DUPLEX, wlan0.getDuplex());
        assertEquals(Integer.valueOf(WLAN_FLAGS), wlan0.getFlags());
        assertEquals(WLAN_MAC, wlan0.getMac());
        TraceEnvironmentConfig.NetworkInterfaceConfig eth0 = omitted.getNetworkInterfaces().get(2);
        assertFalse(eth0.isDuplexConfigured());
        assertNull(eth0.getDuplex());
        assertTrue(eth0.isSpeedMbpsConfigured());
        assertEquals(Integer.valueOf(ZERO_SPEED), eth0.getSpeedMbps());
        assertTrue(eth0.isHardwareTypeConfigured());
        assertTrue(eth0.isOperStateConfigured());
        assertTrue(eth0.isCarrierConfigured());
        assertEquals(Integer.valueOf(ETH_FLAGS), eth0.getFlags());

        TraceEnvironmentConfig inferredOnly = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"wifi\":{\"enabled\":true,\"linkSpeedMbps\":866},"
                + "\"interfaces\":[{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.1\","
                + "\"flags\":4163,\"mac\":\"02:00:00:00:00:01\",\"hardwareType\":1,"
                + "\"operState\":\"up\",\"carrier\":true,\"speedMbps\":866}]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig inferredIface =
                inferredOnly.getNetworkInterfaces().get(0);
        assertFalse(inferredIface.isDuplexConfigured());
        assertNull(inferredIface.getDuplex());
        assertEquals("wlan0", inferredIface.getName());
        assertEquals(Integer.valueOf(4163), inferredIface.getFlags());
        assertEquals("02:00:00:00:00:01", inferredIface.getMac());
        assertTrue(inferredIface.isSpeedMbpsConfigured());
        assertTrue(inferredIface.isHardwareTypeConfigured());
        assertTrue(inferredIface.isOperStateConfigured());
        assertTrue(inferredIface.isCarrierConfigured());

        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"duplex\":null}]}}",
                "network.interfaces[0].duplex");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"duplex\":1}]}}",
                "network.interfaces[0].duplex");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"duplex\":true}]}}",
                "network.interfaces[0].duplex");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"duplex\":false}]}}",
                "network.interfaces[0].duplex");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"duplex\":{}}]}}",
                "network.interfaces[0].duplex");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"duplex\":[]}]}}",
                "network.interfaces[0].duplex");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"duplex\":\"FULL\"}]}}",
                "network.interfaces[0].duplex");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"duplex\":\"Full\"}]}}",
                "network.interfaces[0].duplex");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"duplex\":\"full \"}]}}",
                "network.interfaces[0].duplex");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"duplex\":\"HALF\"}]}}",
                "network.interfaces[0].duplex");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"duplex\":\"UNKNOWN\"}]}}",
                "network.interfaces[0].duplex");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"duplex\":\"\"}]}}",
                "network.interfaces[0].duplex");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"duplex\":\"auto\"}]}}",
                "network.interfaces[0].duplex");
    }

    @Test
    public void testConfiguredDuplexOpenAndSidecar64() throws Exception {
        runConfigured(true);
    }

    @Test
    public void testConfiguredDuplexOpenAndSidecar32() throws Exception {
        runConfigured(false);
    }

    private static EmulatorBuilder<AndroidEmulator> emulatorBuilder(boolean is64Bit) {
        return is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
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

            String wlanText = readOpenText(emulator, blocks, WLAN_DUPLEX_PATH);
            assertEquals(WLAN_DUPLEX_TEXT, wlanText);
            assertTrue(wlanText.endsWith("\n"));
            assertEquals(1, countNewlines(wlanText));

            String loText = readOpenText(emulator, blocks, LO_DUPLEX_PATH);
            assertEquals(LO_DUPLEX_TEXT, loText);
            assertTrue(loText.endsWith("\n"));
            assertEquals(1, countNewlines(loText));

            assertEquals(2, countNetworkDuplexReads(sink));
            CapturedEvent wlanEv = findDuplexRead(sink, WLAN_DUPLEX_PATH);
            CapturedEvent loEv = findDuplexRead(sink, LO_DUPLEX_PATH);
            assertDuplexSidecar(wlanEv, WLAN_DUPLEX_PATH, "wlan0",
                    WLAN_DUPLEX_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertDuplexSidecar(loEv, LO_DUPLEX_PATH, "lo",
                    LO_DUPLEX_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsDuplexValueAndUnrelated(wlanEv);
            assertSidecarOmitsDuplexValueAndUnrelated(loEv);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesPriorityOverAutoDuplex() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"files\":{\"" + WLAN_DUPLEX_PATH + "\":\"custom-duplex\\n\"}},"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
                + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU
                + ",\"duplex\":\"" + WLAN_DUPLEX + "\"},"
                + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
                + "\"mac\":\"" + ETH_MAC_JSON + "\",\"duplex\":\"" + HALF_DUPLEX + "\"}"
                + "]}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("custom-duplex\n", readOpenText(emulator, blocks, WLAN_DUPLEX_PATH));
            assertEquals(HALF_DUPLEX_TEXT, readOpenText(emulator, blocks, ETH_DUPLEX_PATH));
            assertTrue(HALF_DUPLEX_TEXT.endsWith("\n"));
            assertEquals(1, countNewlines(HALF_DUPLEX_TEXT));

            boolean sawLinuxFile = false;
            boolean sawEthDuplex = false;
            for (CapturedEvent e : sink.events) {
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
                if (isNetworkDuplexRead(e, WLAN_DUPLEX_PATH)) {
                    fail("wlan0 duplex should not emit network_device read when linux.files wins");
                }
                if (isNetworkDuplexRead(e, ETH_DUPLEX_PATH)) {
                    sawEthDuplex = true;
                    assertDuplexSidecar(e, ETH_DUPLEX_PATH, "eth0",
                            HALF_DUPLEX_TEXT.getBytes(StandardCharsets.UTF_8).length);
                    assertSidecarOmitsDuplexValueAndUnrelated(e);
                }
            }
            assertTrue(sawLinuxFile);
            assertTrue(sawEthDuplex);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testUnknownMissingDuplexWriteDirectoryAndOtherSysPathsNotTakenOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, UNKNOWN_DUPLEX_PATH);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, ETH_DUPLEX_PATH);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, WLAN_SPEED_PATH);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, WLAN_ADDRESS_PATH);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, WLAN_MTU_PATH);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, WLAN_TYPE_PATH);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, WLAN_OPERSTATE_PATH);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, WLAN_CARRIER_PATH);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, WLAN_FLAGS_PATH);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, WLAN_IFINDEX_PATH);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, ETH_SPEED_PATH);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, ETH_ADDRESS_PATH);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, ETH_MTU_PATH);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, ETH_TYPE_PATH);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, ETH_OPERSTATE_PATH);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, ETH_CARRIER_PATH);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, "/sys/class/net/wlan0");
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, WLAN_DUPLEX_PATH + "/");
            assertEquals(WLAN_MAC + "\n", readOpenText(emulator, blocks, WLAN_ADDRESS_PATH));
            assertEquals(WLAN_MTU + "\n", readOpenText(emulator, blocks, WLAN_MTU_PATH));
            assertEquals("2\n", readOpenText(emulator, blocks, WLAN_IFINDEX_PATH));
            assertEquals(0, countNetworkDuplexReads(sink));

            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_DUPLEX_PATH, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_DUPLEX_PATH, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_DUPLEX_PATH, IOConstants.O_DIRECTORY);
            assertEquals(0, countNetworkDuplexReads(sink));

            assertEquals(WLAN_DUPLEX_TEXT, readOpenText(emulator, blocks, WLAN_DUPLEX_PATH));
            assertEquals(1, countNetworkDuplexReads(sink));
            CapturedEvent wlanEv = findDuplexRead(sink, WLAN_DUPLEX_PATH);
            assertDuplexSidecar(wlanEv, WLAN_DUPLEX_PATH, "wlan0",
                    WLAN_DUPLEX_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsDuplexValueAndUnrelated(wlanEv);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, WLAN_DUPLEX_PATH);
            assertEquals(0, countNetworkDuplexReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            TraceEnvironmentConfig inferredOnly = TraceEnvironmentConfig.parse("{"
                    + "\"network\":{\"wifi\":{\"enabled\":true,\"linkSpeedMbps\":866},"
                    + "\"interfaces\":[{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
                    + "\"flags\":4163,\"mac\":\"" + WLAN_MAC + "\",\"hardwareType\":1,"
                    + "\"operState\":\"up\",\"carrier\":true,\"speedMbps\":866}]}}"
            );
            emulator = emulatorBuilder(true).setEnvironmentConfig(inferredOnly).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredDuplex(emulator, blocks, sink, WLAN_DUPLEX_PATH);
            assertEquals(0, countNetworkDuplexReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
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

    private static void assertNotTakenOverAsConfiguredDuplex(AndroidEmulator emulator,
                                                             List<MemoryBlock> blocks,
                                                             CapturingSink sink,
                                                             String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            assertFalse(path + " must not serve configured wlan duplex", WLAN_DUPLEX_TEXT.equals(text));
            assertFalse(path + " must not serve configured lo duplex", LO_DUPLEX_TEXT.equals(text));
            assertFalse(path + " must not serve half duplex", HALF_DUPLEX_TEXT.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected auto duplex sidecar for " + path,
                    isNetworkDuplexRead(e, path));
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
            assertFalse("write/directory open must not serve configured duplex",
                    WLAN_DUPLEX_TEXT.equals(text) || LO_DUPLEX_TEXT.equals(text)
                            || HALF_DUPLEX_TEXT.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isNetworkDuplexRead(e, path));
        }
    }

    private static void assertDuplexSidecar(CapturedEvent e, String path, String name, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",name=" + name + ",format=duplex,bytes=" + bytes,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains(path));
        assertTrue(e.note.contains("读取"));
    }

    private static void assertSidecarOmitsDuplexValueAndUnrelated(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains(WLAN_DUPLEX));
        assertFalse(note.contains(WLAN_DUPLEX));
        assertFalse(value.contains(HALF_DUPLEX));
        assertFalse(note.contains(HALF_DUPLEX));
        assertFalse(value.contains(LO_DUPLEX));
        assertFalse(note.contains(LO_DUPLEX));
        assertFalse(value.contains(WLAN_DUPLEX_TEXT));
        assertFalse(value.contains(LO_DUPLEX_TEXT));
        assertFalse(value.contains(HALF_DUPLEX_TEXT));
        assertFalse(note.contains(WLAN_DUPLEX_TEXT));
        assertFalse(note.contains(LO_DUPLEX_TEXT));
        assertFalse(note.contains(HALF_DUPLEX_TEXT));
        assertFalse(value.contains("speedMbps"));
        assertFalse(note.contains("speedMbps"));
        assertFalse(value.contains(String.valueOf(WLAN_SPEED)));
        assertFalse(note.contains(String.valueOf(WLAN_SPEED)));
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
        assertFalse(value.contains("=" + ETH_OPERSTATE));
        assertFalse(value.contains("=" + LO_OPERSTATE));
        assertFalse(note.contains(LO_OPERSTATE));
        assertFalse(value.contains(String.valueOf(WLAN_MTU)));
        assertFalse(note.contains(String.valueOf(WLAN_MTU)));
        assertFalse(value.contains(String.valueOf(LO_HARDWARE_TYPE)));
        assertFalse(note.contains(String.valueOf(LO_HARDWARE_TYPE)));
        assertFalse(value.contains("hardwareType"));
        assertFalse(note.contains("hardwareType"));
        assertFalse(value.contains("operState"));
        assertFalse(note.contains("operState"));
        assertFalse(value.contains("carrier"));
        assertFalse(note.contains("carrier"));
        assertFalse(value.contains("format=address"));
        assertFalse(value.contains("format=mtu"));
        assertFalse(value.contains("format=ifindex"));
        assertFalse(value.contains("format=flags"));
        assertFalse(value.contains("format=operstate"));
        assertFalse(value.contains("format=carrier"));
        assertFalse(value.contains("format=type"));
        assertFalse(value.contains("format=speed"));
        assertFalse(value.contains("=" + ETH_HARDWARE_TYPE + ","));
        assertFalse(note.contains("true"));
        assertFalse(note.contains("false"));
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

    private static int countNetworkDuplexReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isNetworkDuplexRead(e, WLAN_DUPLEX_PATH)
                    || isNetworkDuplexRead(e, ETH_DUPLEX_PATH)
                    || isNetworkDuplexRead(e, LO_DUPLEX_PATH)
                    || isNetworkDuplexRead(e, UNKNOWN_DUPLEX_PATH)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findDuplexRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isNetworkDuplexRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isNetworkDuplexRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=duplex");
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
