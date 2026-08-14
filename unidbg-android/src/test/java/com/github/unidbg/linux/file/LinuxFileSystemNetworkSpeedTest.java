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
 * LinuxFileSystem wiring for {@code network.interfaces[].speedMbps} → exact
 * {@code /sys/class/net/<name>/speed} (decimal ASCII Mbps + LF).
 * Uses the default backend (no Unicorn2) so emulator close does not hit Unicorn2
 * lifecycle crashes. Never infers speedMbps from {@code network.wifi.linkSpeedMbps},
 * name, flags, MAC, hardwareType, operState, or carrier.
 */
public class LinuxFileSystemNetworkSpeedTest {

    private static final String WLAN_SPEED_PATH = "/sys/class/net/wlan0/speed";
    private static final String ETH_SPEED_PATH = "/sys/class/net/eth0/speed";
    private static final String LO_SPEED_PATH = "/sys/class/net/lo/speed";
    private static final String UNKNOWN_SPEED_PATH = "/sys/class/net/not0/speed";
    private static final String WLAN_FLAGS_PATH = "/sys/class/net/wlan0/flags";
    private static final String WLAN_ADDRESS_PATH = "/sys/class/net/wlan0/address";
    private static final String WLAN_MTU_PATH = "/sys/class/net/wlan0/mtu";
    private static final String WLAN_IFINDEX_PATH = "/sys/class/net/wlan0/ifindex";
    private static final String WLAN_OPERSTATE_PATH = "/sys/class/net/wlan0/operstate";
    private static final String WLAN_CARRIER_PATH = "/sys/class/net/wlan0/carrier";
    private static final String WLAN_TYPE_PATH = "/sys/class/net/wlan0/type";
    private static final String ETH_OPERSTATE_PATH = "/sys/class/net/eth0/operstate";
    private static final String ETH_CARRIER_PATH = "/sys/class/net/eth0/carrier";
    private static final String ETH_TYPE_PATH = "/sys/class/net/eth0/type";
    private static final String ETH_ADDRESS_PATH = "/sys/class/net/eth0/address";
    private static final String ETH_MTU_PATH = "/sys/class/net/eth0/mtu";

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

    private static final String WLAN_SPEED_TEXT = WLAN_SPEED + "\n";
    private static final String LO_SPEED_TEXT = LO_SPEED + "\n";
    private static final String ZERO_SPEED_TEXT = ZERO_SPEED + "\n";

    private static final String TWO_IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"flags\":" + LO_FLAGS
            + ",\"operState\":\"" + LO_OPERSTATE + "\",\"carrier\":true"
            + ",\"hardwareType\":" + LO_HARDWARE_TYPE
            + ",\"speedMbps\":" + LO_SPEED + "},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
            + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU + ",\"flags\":" + WLAN_FLAGS
            + ",\"hardwareType\":" + WLAN_HARDWARE_TYPE
            + ",\"speedMbps\":" + WLAN_SPEED + "},"
            + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
            + "\"mac\":\"" + ETH_MAC_JSON + "\",\"flags\":" + ETH_FLAGS
            + ",\"operState\":\"" + ETH_OPERSTATE + "\",\"carrier\":false"
            + ",\"hardwareType\":" + ETH_HARDWARE_TYPE + "}"
            + "]}}"
            ;

    @Test
    public void testSpeedMbpsParseValidAndInvalid() {
        TraceEnvironmentConfig unknown = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"speedMbps\":-1}"
                + "]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig loIface =
                unknown.getNetworkInterfaces().get(0);
        assertTrue(loIface.isSpeedMbpsConfigured());
        assertEquals(Integer.valueOf(-1), loIface.getSpeedMbps());
        assertFalse(loIface.isHardwareTypeConfigured());
        assertNull(loIface.getHardwareType());
        assertFalse(loIface.isOperStateConfigured());
        assertNull(loIface.getOperState());
        assertFalse(loIface.isCarrierConfigured());
        assertNull(loIface.getFlags());
        assertNull(loIface.getMac());

        TraceEnvironmentConfig wifiSpeed = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\",\"speedMbps\":866}"
                + "]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig wlanIface =
                wifiSpeed.getNetworkInterfaces().get(0);
        assertTrue(wlanIface.isSpeedMbpsConfigured());
        assertEquals(Integer.valueOf(WLAN_SPEED), wlanIface.getSpeedMbps());

        TraceEnvironmentConfig zero = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"tun0\",\"index\":4,\"ipv4\":\"10.0.0.4\",\"speedMbps\":0}"
                + "]}}"
        );
        assertTrue(zero.getNetworkInterfaces().get(0).isSpeedMbpsConfigured());
        assertEquals(Integer.valueOf(0), zero.getNetworkInterfaces().get(0).getSpeedMbps());

        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"max0\",\"index\":5,\"ipv4\":\"10.0.0.5\",\"speedMbps\":"
                + Integer.MAX_VALUE + "}"
                + "]}}"
        );
        assertTrue(max.getNetworkInterfaces().get(0).isSpeedMbpsConfigured());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE),
                max.getNetworkInterfaces().get(0).getSpeedMbps());

        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        TraceEnvironmentConfig.NetworkInterfaceConfig lo = omitted.getNetworkInterfaces().get(0);
        assertTrue(lo.isSpeedMbpsConfigured());
        assertEquals(Integer.valueOf(LO_SPEED), lo.getSpeedMbps());
        assertTrue(lo.isHardwareTypeConfigured());
        assertTrue(lo.isOperStateConfigured());
        assertTrue(lo.isCarrierConfigured());
        TraceEnvironmentConfig.NetworkInterfaceConfig wlan0 = omitted.getNetworkInterfaces().get(1);
        assertTrue(wlan0.isSpeedMbpsConfigured());
        assertEquals(Integer.valueOf(WLAN_SPEED), wlan0.getSpeedMbps());
        assertEquals(Integer.valueOf(WLAN_FLAGS), wlan0.getFlags());
        assertEquals(WLAN_MAC, wlan0.getMac());
        TraceEnvironmentConfig.NetworkInterfaceConfig eth0 = omitted.getNetworkInterfaces().get(2);
        assertFalse(eth0.isSpeedMbpsConfigured());
        assertNull(eth0.getSpeedMbps());
        assertTrue(eth0.isHardwareTypeConfigured());
        assertTrue(eth0.isOperStateConfigured());
        assertTrue(eth0.isCarrierConfigured());
        assertEquals(Integer.valueOf(ETH_FLAGS), eth0.getFlags());

        TraceEnvironmentConfig inferredOnly = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"wifi\":{\"enabled\":true,\"linkSpeedMbps\":866},"
                + "\"interfaces\":[{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
                + "\"flags\":4163,\"mac\":\"02:00:00:00:00:01\",\"hardwareType\":1,"
                + "\"operState\":\"up\",\"carrier\":true}]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig inferredIface =
                inferredOnly.getNetworkInterfaces().get(0);
        assertFalse(inferredIface.isSpeedMbpsConfigured());
        assertNull(inferredIface.getSpeedMbps());
        assertEquals("wlan0", inferredIface.getName());
        assertEquals(Integer.valueOf(4163), inferredIface.getFlags());
        assertEquals("02:00:00:00:00:01", inferredIface.getMac());
        assertTrue(inferredIface.isHardwareTypeConfigured());
        assertTrue(inferredIface.isOperStateConfigured());
        assertTrue(inferredIface.isCarrierConfigured());

        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"speedMbps\":-2}]}}",
                "network.interfaces[0].speedMbps");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"speedMbps\":2147483648}]}}",
                "network.interfaces[0].speedMbps");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"speedMbps\":1.5}]}}",
                "network.interfaces[0].speedMbps");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"speedMbps\":\"866\"}]}}",
                "network.interfaces[0].speedMbps");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"speedMbps\":\"-1\"}]}}",
                "network.interfaces[0].speedMbps");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"speedMbps\":true}]}}",
                "network.interfaces[0].speedMbps");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"speedMbps\":false}]}}",
                "network.interfaces[0].speedMbps");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"speedMbps\":null}]}}",
                "network.interfaces[0].speedMbps");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"speedMbps\":{}}]}}",
                "network.interfaces[0].speedMbps");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"speedMbps\":[]}]}}",
                "network.interfaces[0].speedMbps");
    }

    @Test
    public void testConfiguredSpeedOpenAndSidecar64() throws Exception {
        runConfigured(true);
    }

    @Test
    public void testConfiguredSpeedOpenAndSidecar32() throws Exception {
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

            String wlanText = readOpenText(emulator, blocks, WLAN_SPEED_PATH);
            assertEquals(WLAN_SPEED_TEXT, wlanText);
            assertTrue(wlanText.endsWith("\n"));
            assertEquals(1, countNewlines(wlanText));

            String loText = readOpenText(emulator, blocks, LO_SPEED_PATH);
            assertEquals(LO_SPEED_TEXT, loText);
            assertTrue(loText.endsWith("\n"));
            assertEquals(1, countNewlines(loText));

            assertEquals(2, countNetworkSpeedReads(sink));
            CapturedEvent wlanEv = findSpeedRead(sink, WLAN_SPEED_PATH);
            CapturedEvent loEv = findSpeedRead(sink, LO_SPEED_PATH);
            assertSpeedSidecar(wlanEv, WLAN_SPEED_PATH, "wlan0",
                    WLAN_SPEED_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSpeedSidecar(loEv, LO_SPEED_PATH, "lo",
                    LO_SPEED_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsSpeedValueAndUnrelated(wlanEv);
            assertSidecarOmitsSpeedValueAndUnrelated(loEv);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesPriorityOverAutoSpeed() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"files\":{\"" + WLAN_SPEED_PATH + "\":\"custom-speed\\n\"}},"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
                + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU
                + ",\"speedMbps\":" + WLAN_SPEED + "},"
                + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
                + "\"mac\":\"" + ETH_MAC_JSON + "\",\"speedMbps\":" + ZERO_SPEED + "}"
                + "]}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("custom-speed\n", readOpenText(emulator, blocks, WLAN_SPEED_PATH));
            assertEquals(ZERO_SPEED_TEXT, readOpenText(emulator, blocks, ETH_SPEED_PATH));
            assertTrue(ZERO_SPEED_TEXT.endsWith("\n"));
            assertEquals(1, countNewlines(ZERO_SPEED_TEXT));

            boolean sawLinuxFile = false;
            boolean sawEthSpeed = false;
            for (CapturedEvent e : sink.events) {
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
                if (isNetworkSpeedRead(e, WLAN_SPEED_PATH)) {
                    fail("wlan0 speed should not emit network_device read when linux.files wins");
                }
                if (isNetworkSpeedRead(e, ETH_SPEED_PATH)) {
                    sawEthSpeed = true;
                    assertSpeedSidecar(e, ETH_SPEED_PATH, "eth0",
                            ZERO_SPEED_TEXT.getBytes(StandardCharsets.UTF_8).length);
                    assertSidecarOmitsSpeedValueAndUnrelated(e);
                }
            }
            assertTrue(sawLinuxFile);
            assertTrue(sawEthSpeed);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testUnknownMissingSpeedWriteDirectoryAndOtherSysPathsNotTakenOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, UNKNOWN_SPEED_PATH);
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, ETH_SPEED_PATH);
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, WLAN_FLAGS_PATH);
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, WLAN_ADDRESS_PATH);
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, WLAN_MTU_PATH);
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, WLAN_IFINDEX_PATH);
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, WLAN_OPERSTATE_PATH);
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, WLAN_CARRIER_PATH);
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, WLAN_TYPE_PATH);
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, ETH_OPERSTATE_PATH);
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, ETH_CARRIER_PATH);
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, ETH_TYPE_PATH);
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, ETH_ADDRESS_PATH);
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, ETH_MTU_PATH);
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, "/sys/class/net/wlan0");
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, WLAN_SPEED_PATH + "/");
            assertEquals(WLAN_MAC + "\n", readOpenText(emulator, blocks, WLAN_ADDRESS_PATH));
            assertEquals(WLAN_MTU + "\n", readOpenText(emulator, blocks, WLAN_MTU_PATH));
            assertEquals("2\n", readOpenText(emulator, blocks, WLAN_IFINDEX_PATH));
            assertEquals(0, countNetworkSpeedReads(sink));

            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_SPEED_PATH, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_SPEED_PATH, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_SPEED_PATH, IOConstants.O_DIRECTORY);
            assertEquals(0, countNetworkSpeedReads(sink));

            assertEquals(WLAN_SPEED_TEXT, readOpenText(emulator, blocks, WLAN_SPEED_PATH));
            assertEquals(1, countNetworkSpeedReads(sink));
            CapturedEvent wlanEv = findSpeedRead(sink, WLAN_SPEED_PATH);
            assertSpeedSidecar(wlanEv, WLAN_SPEED_PATH, "wlan0",
                    WLAN_SPEED_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsSpeedValueAndUnrelated(wlanEv);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, WLAN_SPEED_PATH);
            assertEquals(0, countNetworkSpeedReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            TraceEnvironmentConfig wifiOnly = TraceEnvironmentConfig.parse("{"
                    + "\"network\":{\"wifi\":{\"enabled\":true,\"linkSpeedMbps\":866},"
                    + "\"interfaces\":[{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
                    + "\"flags\":4163,\"mac\":\"" + WLAN_MAC + "\",\"hardwareType\":1,"
                    + "\"operState\":\"up\",\"carrier\":true}]}}"
            );
            emulator = emulatorBuilder(true).setEnvironmentConfig(wifiOnly).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredSpeed(emulator, blocks, sink, WLAN_SPEED_PATH);
            assertEquals(0, countNetworkSpeedReads(sink));
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

    private static void assertNotTakenOverAsConfiguredSpeed(AndroidEmulator emulator,
                                                            List<MemoryBlock> blocks,
                                                            CapturingSink sink,
                                                            String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            assertFalse(path + " must not serve configured wlan speed", WLAN_SPEED_TEXT.equals(text));
            assertFalse(path + " must not serve configured lo speed", LO_SPEED_TEXT.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected auto speed sidecar for " + path,
                    isNetworkSpeedRead(e, path));
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
            assertFalse("write/directory open must not serve configured speed",
                    WLAN_SPEED_TEXT.equals(text) || LO_SPEED_TEXT.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isNetworkSpeedRead(e, path));
        }
    }

    private static void assertSpeedSidecar(CapturedEvent e, String path, String name, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",name=" + name + ",format=speed,bytes=" + bytes,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains(path));
        assertTrue(e.note.contains("读取"));
    }

    private static void assertSidecarOmitsSpeedValueAndUnrelated(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains("speedMbps"));
        assertFalse(note.contains("speedMbps"));
        assertFalse(value.contains(String.valueOf(WLAN_SPEED)));
        assertFalse(note.contains(String.valueOf(WLAN_SPEED)));
        assertFalse(value.contains(String.valueOf(LO_SPEED)));
        assertFalse(note.contains(String.valueOf(LO_SPEED)));
        assertFalse(value.contains(WLAN_SPEED_TEXT));
        assertFalse(value.contains(LO_SPEED_TEXT));
        assertFalse(note.contains(WLAN_SPEED_TEXT));
        assertFalse(note.contains(LO_SPEED_TEXT));
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
        assertFalse(value.contains(ETH_OPERSTATE));
        assertFalse(value.contains(LO_OPERSTATE));
        assertFalse(note.contains(ETH_OPERSTATE));
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

    private static int countNetworkSpeedReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isNetworkSpeedRead(e, WLAN_SPEED_PATH)
                    || isNetworkSpeedRead(e, ETH_SPEED_PATH)
                    || isNetworkSpeedRead(e, LO_SPEED_PATH)
                    || isNetworkSpeedRead(e, UNKNOWN_SPEED_PATH)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findSpeedRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isNetworkSpeedRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isNetworkSpeedRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=speed");
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
