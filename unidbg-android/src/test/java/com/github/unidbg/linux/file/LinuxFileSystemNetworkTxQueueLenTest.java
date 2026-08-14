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
 * LinuxFileSystem wiring for {@code network.interfaces[].txQueueLen} → exact
 * {@code /sys/class/net/<name>/tx_queue_len} (decimal ASCII + single LF).
 * Uses the default backend (no Unicorn2) so emulator close does not hit Unicorn2
 * lifecycle crashes. Never infers txQueueLen from mtu, speedMbps, duplex,
 * hardwareType, linkIndex, index, flags, carrier, operState, name, or wifi.
 */
public class LinuxFileSystemNetworkTxQueueLenTest {

    private static final String WLAN_TXQL_PATH = "/sys/class/net/wlan0/tx_queue_len";
    private static final String ETH_TXQL_PATH = "/sys/class/net/eth0/tx_queue_len";
    private static final String LO_TXQL_PATH = "/sys/class/net/lo/tx_queue_len";
    private static final String TUN_TXQL_PATH = "/sys/class/net/tun1/tx_queue_len";
    private static final String UNKNOWN_TXQL_PATH = "/sys/class/net/not0/tx_queue_len";
    private static final String WLAN_IFLINK_PATH = "/sys/class/net/wlan0/iflink";
    private static final String WLAN_IFINDEX_PATH = "/sys/class/net/wlan0/ifindex";
    private static final String WLAN_SPEED_PATH = "/sys/class/net/wlan0/speed";
    private static final String WLAN_DUPLEX_PATH = "/sys/class/net/wlan0/duplex";
    private static final String WLAN_ADDRESS_PATH = "/sys/class/net/wlan0/address";
    private static final String WLAN_MTU_PATH = "/sys/class/net/wlan0/mtu";
    private static final String WLAN_TYPE_PATH = "/sys/class/net/wlan0/type";
    private static final String WLAN_OPERSTATE_PATH = "/sys/class/net/wlan0/operstate";
    private static final String WLAN_CARRIER_PATH = "/sys/class/net/wlan0/carrier";
    private static final String WLAN_FLAGS_PATH = "/sys/class/net/wlan0/flags";
    private static final String ETH_IFINDEX_PATH = "/sys/class/net/eth0/ifindex";
    private static final String ETH_SPEED_PATH = "/sys/class/net/eth0/speed";
    private static final String ETH_DUPLEX_PATH = "/sys/class/net/eth0/duplex";
    private static final String ETH_ADDRESS_PATH = "/sys/class/net/eth0/address";
    private static final String ETH_MTU_PATH = "/sys/class/net/eth0/mtu";
    private static final String ETH_TYPE_PATH = "/sys/class/net/eth0/type";
    private static final String ETH_OPERSTATE_PATH = "/sys/class/net/eth0/operstate";
    private static final String ETH_CARRIER_PATH = "/sys/class/net/eth0/carrier";

    private static final String WLAN_MAC = "02:00:00:00:00:01";
    private static final String ETH_MAC_JSON = "AA:BB:CC:DD:EE:FF";
    private static final String ETH_MAC_CANONICAL = "aa:bb:cc:dd:ee:ff";
    private static final String TUN_MAC = "02:00:00:00:00:03";

    private static final int LO_FLAGS = 73;
    private static final int ETH_FLAGS = 4163;
    private static final int WLAN_FLAGS = 4163;
    private static final int TUN_FLAGS = 4305;
    private static final int WLAN_MTU = 1500;
    private static final int ETH_MTU = 9000;

    private static final String LO_OPERSTATE = "down";
    private static final String ETH_OPERSTATE = "up";
    private static final String WLAN_OPERSTATE = "up";
    private static final String TUN_OPERSTATE = "unknown";
    private static final String WLAN_DUPLEX = "full";
    private static final String ETH_DUPLEX = "half";
    private static final String LO_DUPLEX = "unknown";

    private static final int LO_HARDWARE_TYPE = 772;
    private static final int ETH_HARDWARE_TYPE = 1;
    private static final int WLAN_HARDWARE_TYPE = 1;
    private static final int TUN_HARDWARE_TYPE = 65534;
    private static final int WLAN_SPEED = 866;
    private static final int LO_SPEED = -1;

    private static final int LO_INDEX = 1;
    private static final int WLAN_INDEX = 2;
    private static final int ETH_INDEX = 3;
    private static final int TUN_INDEX = 4;
    private static final int LO_LINK_INDEX = 1;
    private static final int WLAN_LINK_INDEX = 99;

    private static final int WLAN_TXQL = 1000;
    private static final int LO_TXQL = 1000;
    private static final int ZERO_TXQL = 0;

    private static final String WLAN_TXQL_TEXT = WLAN_TXQL + "\n";
    private static final String LO_TXQL_TEXT = LO_TXQL + "\n";
    private static final String ZERO_TXQL_TEXT = ZERO_TXQL + "\n";

    private static final String TWO_IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":" + LO_INDEX + ",\"ipv4\":\"127.0.0.1\",\"flags\":" + LO_FLAGS
            + ",\"operState\":\"" + LO_OPERSTATE + "\",\"carrier\":true"
            + ",\"hardwareType\":" + LO_HARDWARE_TYPE
            + ",\"speedMbps\":" + LO_SPEED
            + ",\"duplex\":\"" + LO_DUPLEX + "\""
            + ",\"linkIndex\":" + LO_LINK_INDEX
            + ",\"txQueueLen\":" + LO_TXQL + "},"
            + "{\"name\":\"wlan0\",\"index\":" + WLAN_INDEX + ",\"ipv4\":\"192.168.1.100\","
            + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU + ",\"flags\":" + WLAN_FLAGS
            + ",\"hardwareType\":" + WLAN_HARDWARE_TYPE
            + ",\"operState\":\"" + WLAN_OPERSTATE + "\",\"carrier\":true"
            + ",\"speedMbps\":" + WLAN_SPEED
            + ",\"duplex\":\"" + WLAN_DUPLEX + "\""
            + ",\"linkIndex\":" + WLAN_LINK_INDEX
            + ",\"txQueueLen\":" + WLAN_TXQL + "},"
            + "{\"name\":\"eth0\",\"index\":" + ETH_INDEX + ",\"ipv4\":\"10.0.0.2\","
            + "\"mac\":\"" + ETH_MAC_JSON + "\",\"mtu\":" + ETH_MTU + ",\"flags\":" + ETH_FLAGS
            + ",\"operState\":\"" + ETH_OPERSTATE + "\",\"carrier\":false"
            + ",\"hardwareType\":" + ETH_HARDWARE_TYPE
            + ",\"speedMbps\":1000"
            + ",\"duplex\":\"" + ETH_DUPLEX + "\"},"
            + "{\"name\":\"tun1\",\"index\":" + TUN_INDEX + ",\"ipv4\":\"10.8.0.2\","
            + "\"mac\":\"" + TUN_MAC + "\",\"flags\":" + TUN_FLAGS
            + ",\"operState\":\"" + TUN_OPERSTATE + "\",\"carrier\":false"
            + ",\"hardwareType\":" + TUN_HARDWARE_TYPE
            + ",\"txQueueLen\":" + ZERO_TXQL + "}"
            + "]}}"
            ;

    @Test
    public void testTxQueueLenParseValidAndInvalid() {
        TraceEnvironmentConfig zero = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"txQueueLen\":0}"
                + "]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig zeroIface = zero.getNetworkInterfaces().get(0);
        assertTrue(zeroIface.isTxQueueLenConfigured());
        assertEquals(Integer.valueOf(0), zeroIface.getTxQueueLen());
        assertFalse(zeroIface.isHardwareTypeConfigured());
        assertNull(zeroIface.getHardwareType());
        assertFalse(zeroIface.isSpeedMbpsConfigured());
        assertNull(zeroIface.getSpeedMbps());
        assertFalse(zeroIface.isDuplexConfigured());
        assertNull(zeroIface.getDuplex());
        assertFalse(zeroIface.isLinkIndexConfigured());
        assertNull(zeroIface.getLinkIndex());
        assertFalse(zeroIface.isOperStateConfigured());
        assertNull(zeroIface.getOperState());
        assertFalse(zeroIface.isCarrierConfigured());
        assertNull(zeroIface.getFlags());
        assertNull(zeroIface.getMac());
        assertNull(zeroIface.getMtu());

        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"max0\",\"index\":5,\"ipv4\":\"10.0.0.5\",\"txQueueLen\":"
                + Integer.MAX_VALUE + "}"
                + "]}}"
        );
        assertTrue(max.getNetworkInterfaces().get(0).isTxQueueLenConfigured());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE),
                max.getNetworkInterfaces().get(0).getTxQueueLen());

        TraceEnvironmentConfig mixed = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        TraceEnvironmentConfig.NetworkInterfaceConfig lo = mixed.getNetworkInterfaces().get(0);
        assertTrue(lo.isTxQueueLenConfigured());
        assertEquals(Integer.valueOf(LO_TXQL), lo.getTxQueueLen());
        TraceEnvironmentConfig.NetworkInterfaceConfig wlan0 = mixed.getNetworkInterfaces().get(1);
        assertTrue(wlan0.isTxQueueLenConfigured());
        assertEquals(Integer.valueOf(WLAN_TXQL), wlan0.getTxQueueLen());
        assertEquals(Integer.valueOf(WLAN_MTU), wlan0.getMtu());
        assertTrue(wlan0.isSpeedMbpsConfigured());
        TraceEnvironmentConfig.NetworkInterfaceConfig eth0 = mixed.getNetworkInterfaces().get(2);
        assertFalse(eth0.isTxQueueLenConfigured());
        assertNull(eth0.getTxQueueLen());
        assertEquals(Integer.valueOf(ETH_MTU), eth0.getMtu());
        assertTrue(eth0.isSpeedMbpsConfigured());
        assertEquals(Integer.valueOf(1000), eth0.getSpeedMbps());
        assertTrue(eth0.isDuplexConfigured());
        assertTrue(eth0.isHardwareTypeConfigured());
        assertTrue(eth0.isOperStateConfigured());
        assertTrue(eth0.isCarrierConfigured());
        assertEquals(Integer.valueOf(ETH_FLAGS), eth0.getFlags());
        assertEquals(ETH_MAC_CANONICAL, eth0.getMac());
        TraceEnvironmentConfig.NetworkInterfaceConfig tun1 = mixed.getNetworkInterfaces().get(3);
        assertTrue(tun1.isTxQueueLenConfigured());
        assertEquals(Integer.valueOf(ZERO_TXQL), tun1.getTxQueueLen());

        TraceEnvironmentConfig inferredOnly = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"wifi\":{\"enabled\":true,\"linkSpeedMbps\":866},"
                + "\"interfaces\":[{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
                + "\"flags\":4163,\"mac\":\"02:00:00:00:00:01\",\"mtu\":1500,\"hardwareType\":1,"
                + "\"operState\":\"up\",\"carrier\":true,\"speedMbps\":866,\"duplex\":\"full\","
                + "\"linkIndex\":2}]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig inferredIface =
                inferredOnly.getNetworkInterfaces().get(0);
        assertFalse(inferredIface.isTxQueueLenConfigured());
        assertNull(inferredIface.getTxQueueLen());
        assertEquals("wlan0", inferredIface.getName());
        assertEquals(2, inferredIface.getIndex());
        assertEquals(Integer.valueOf(4163), inferredIface.getFlags());
        assertEquals("02:00:00:00:00:01", inferredIface.getMac());
        assertEquals(Integer.valueOf(1500), inferredIface.getMtu());
        assertTrue(inferredIface.isHardwareTypeConfigured());
        assertTrue(inferredIface.isSpeedMbpsConfigured());
        assertTrue(inferredIface.isDuplexConfigured());
        assertTrue(inferredIface.isLinkIndexConfigured());
        assertTrue(inferredIface.isOperStateConfigured());
        assertTrue(inferredIface.isCarrierConfigured());

        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"txQueueLen\":-1}]}}",
                "network.interfaces[0].txQueueLen");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"txQueueLen\":-2}]}}",
                "network.interfaces[0].txQueueLen");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"txQueueLen\":2147483648}]}}",
                "network.interfaces[0].txQueueLen");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"txQueueLen\":1.5}]}}",
                "network.interfaces[0].txQueueLen");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"txQueueLen\":\"1000\"}]}}",
                "network.interfaces[0].txQueueLen");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"txQueueLen\":\"0\"}]}}",
                "network.interfaces[0].txQueueLen");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"txQueueLen\":true}]}}",
                "network.interfaces[0].txQueueLen");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"txQueueLen\":false}]}}",
                "network.interfaces[0].txQueueLen");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"txQueueLen\":null}]}}",
                "network.interfaces[0].txQueueLen");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"txQueueLen\":{}}]}}",
                "network.interfaces[0].txQueueLen");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"txQueueLen\":[]}]}}",
                "network.interfaces[0].txQueueLen");
    }

    @Test
    public void testConfiguredTxQueueLenOpenAndSidecar64() throws Exception {
        runConfigured(true);
    }

    @Test
    public void testConfiguredTxQueueLenOpenAndSidecar32() throws Exception {
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

            String wlanText = readOpenText(emulator, blocks, WLAN_TXQL_PATH);
            assertEquals(WLAN_TXQL_TEXT, wlanText);
            assertTrue(wlanText.endsWith("\n"));
            assertEquals(1, countNewlines(wlanText));

            String loText = readOpenText(emulator, blocks, LO_TXQL_PATH);
            assertEquals(LO_TXQL_TEXT, loText);
            assertTrue(loText.endsWith("\n"));
            assertEquals(1, countNewlines(loText));

            String zeroText = readOpenText(emulator, blocks, TUN_TXQL_PATH);
            assertEquals(ZERO_TXQL_TEXT, zeroText);
            assertTrue(zeroText.endsWith("\n"));
            assertEquals(1, countNewlines(zeroText));
            assertEquals("0\n", zeroText);

            assertEquals(3, countNetworkTxQueueLenReads(sink));
            CapturedEvent wlanEv = findTxQueueLenRead(sink, WLAN_TXQL_PATH);
            CapturedEvent loEv = findTxQueueLenRead(sink, LO_TXQL_PATH);
            CapturedEvent tunEv = findTxQueueLenRead(sink, TUN_TXQL_PATH);
            assertTxQueueLenSidecar(wlanEv, WLAN_TXQL_PATH, "wlan0",
                    WLAN_TXQL_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertTxQueueLenSidecar(loEv, LO_TXQL_PATH, "lo",
                    LO_TXQL_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertTxQueueLenSidecar(tunEv, TUN_TXQL_PATH, "tun1",
                    ZERO_TXQL_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsTxQueueLenValueAndUnrelated(wlanEv, true);
            assertSidecarOmitsTxQueueLenValueAndUnrelated(loEv, true);
            assertSidecarOmitsTxQueueLenValueAndUnrelated(tunEv, false);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesPriorityOverAutoTxQueueLen() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"files\":{\"" + WLAN_TXQL_PATH + "\":\"custom-txql\\n\"}},"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":" + WLAN_INDEX + ",\"ipv4\":\"192.168.1.100\","
                + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU
                + ",\"txQueueLen\":" + WLAN_TXQL + "},"
                + "{\"name\":\"lo\",\"index\":" + LO_INDEX + ",\"ipv4\":\"127.0.0.1\","
                + "\"txQueueLen\":" + LO_TXQL + "}"
                + "]}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("custom-txql\n", readOpenText(emulator, blocks, WLAN_TXQL_PATH));
            assertEquals(LO_TXQL_TEXT, readOpenText(emulator, blocks, LO_TXQL_PATH));
            assertTrue(LO_TXQL_TEXT.endsWith("\n"));
            assertEquals(1, countNewlines(LO_TXQL_TEXT));

            boolean sawLinuxFile = false;
            boolean sawLoTxql = false;
            for (CapturedEvent e : sink.events) {
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
                if (isNetworkTxQueueLenRead(e, WLAN_TXQL_PATH)) {
                    fail("wlan0 tx_queue_len should not emit network_device read when linux.files wins");
                }
                if (isNetworkTxQueueLenRead(e, LO_TXQL_PATH)) {
                    sawLoTxql = true;
                    assertTxQueueLenSidecar(e, LO_TXQL_PATH, "lo",
                            LO_TXQL_TEXT.getBytes(StandardCharsets.UTF_8).length);
                    assertSidecarOmitsTxQueueLenValueAndUnrelated(e, true);
                }
            }
            assertTrue(sawLinuxFile);
            assertTrue(sawLoTxql);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testUnknownMissingTxQueueLenWriteDirectoryAndOtherSysPathsNotTakenOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, UNKNOWN_TXQL_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, ETH_TXQL_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, WLAN_IFLINK_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, WLAN_IFINDEX_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, WLAN_SPEED_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, WLAN_DUPLEX_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, WLAN_ADDRESS_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, WLAN_MTU_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, WLAN_TYPE_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, WLAN_OPERSTATE_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, WLAN_CARRIER_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, WLAN_FLAGS_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, ETH_IFINDEX_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, ETH_SPEED_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, ETH_DUPLEX_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, ETH_ADDRESS_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, ETH_MTU_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, ETH_TYPE_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, ETH_OPERSTATE_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, ETH_CARRIER_PATH);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, "/sys/class/net/wlan0");
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, WLAN_TXQL_PATH + "/");
            assertEquals(WLAN_MAC + "\n", readOpenText(emulator, blocks, WLAN_ADDRESS_PATH));
            assertEquals(WLAN_MTU + "\n", readOpenText(emulator, blocks, WLAN_MTU_PATH));
            assertEquals(WLAN_INDEX + "\n", readOpenText(emulator, blocks, WLAN_IFINDEX_PATH));
            assertEquals(0, countNetworkTxQueueLenReads(sink));

            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_TXQL_PATH, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_TXQL_PATH, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_TXQL_PATH, IOConstants.O_DIRECTORY);
            assertEquals(0, countNetworkTxQueueLenReads(sink));

            assertEquals(WLAN_TXQL_TEXT, readOpenText(emulator, blocks, WLAN_TXQL_PATH));
            assertEquals(1, countNetworkTxQueueLenReads(sink));
            CapturedEvent wlanEv = findTxQueueLenRead(sink, WLAN_TXQL_PATH);
            assertTxQueueLenSidecar(wlanEv, WLAN_TXQL_PATH, "wlan0",
                    WLAN_TXQL_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsTxQueueLenValueAndUnrelated(wlanEv, true);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, WLAN_TXQL_PATH);
            assertEquals(0, countNetworkTxQueueLenReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            TraceEnvironmentConfig inferredOnly = TraceEnvironmentConfig.parse("{"
                    + "\"network\":{\"wifi\":{\"enabled\":true,\"linkSpeedMbps\":866},"
                    + "\"interfaces\":[{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
                    + "\"flags\":4163,\"mac\":\"" + WLAN_MAC + "\",\"mtu\":1500,\"hardwareType\":1,"
                    + "\"operState\":\"up\",\"carrier\":true,\"speedMbps\":866,\"duplex\":\"full\","
                    + "\"linkIndex\":2}]}}"
            );
            emulator = emulatorBuilder(true).setEnvironmentConfig(inferredOnly).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredTxQueueLen(emulator, blocks, sink, WLAN_TXQL_PATH);
            assertEquals(0, countNetworkTxQueueLenReads(sink));
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

    private static void assertNotTakenOverAsConfiguredTxQueueLen(AndroidEmulator emulator,
                                                                 List<MemoryBlock> blocks,
                                                                 CapturingSink sink,
                                                                 String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            assertFalse(path + " must not serve configured wlan tx_queue_len",
                    WLAN_TXQL_TEXT.equals(text) && path.contains("/tx_queue_len"));
            if (path.contains("/tx_queue_len")) {
                assertFalse(path + " must not serve configured lo tx_queue_len",
                        LO_TXQL_TEXT.equals(text));
                assertFalse(path + " must not serve configured tun tx_queue_len",
                        ZERO_TXQL_TEXT.equals(text));
            }
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected auto tx_queue_len sidecar for " + path,
                    isNetworkTxQueueLenRead(e, path));
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
            assertFalse("write/directory open must not serve configured tx_queue_len",
                    WLAN_TXQL_TEXT.equals(text) || LO_TXQL_TEXT.equals(text)
                            || ZERO_TXQL_TEXT.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isNetworkTxQueueLenRead(e, path));
        }
    }

    private static void assertTxQueueLenSidecar(CapturedEvent e, String path, String name, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",name=" + name + ",format=tx_queue_len,bytes=" + bytes,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains(path));
        assertTrue(e.note.contains("读取"));
    }

    private static void assertSidecarOmitsTxQueueLenValueAndUnrelated(CapturedEvent e,
                                                                     boolean omitConfiguredThousand) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains("txQueueLen"));
        assertFalse(note.contains("txQueueLen"));
        if (omitConfiguredThousand) {
            assertFalse(value.contains(String.valueOf(WLAN_TXQL)));
            assertFalse(note.contains(String.valueOf(WLAN_TXQL)));
            assertFalse(value.contains(WLAN_TXQL_TEXT));
            assertFalse(value.contains(LO_TXQL_TEXT));
            assertFalse(note.contains(WLAN_TXQL_TEXT));
            assertFalse(note.contains(LO_TXQL_TEXT));
        }
        assertFalse(value.contains(ZERO_TXQL_TEXT));
        assertFalse(note.contains(ZERO_TXQL_TEXT));
        assertFalse(value.contains(WLAN_MAC));
        assertFalse(value.contains(ETH_MAC_JSON));
        assertFalse(value.contains(ETH_MAC_CANONICAL));
        assertFalse(value.contains(TUN_MAC));
        assertFalse(note.contains(WLAN_MAC));
        assertFalse(note.contains(ETH_MAC_JSON));
        assertFalse(note.contains(ETH_MAC_CANONICAL));
        assertFalse(note.contains(TUN_MAC));
        assertFalse(value.contains("192.168.1.100"));
        assertFalse(value.contains("10.0.0.2"));
        assertFalse(value.contains("127.0.0.1"));
        assertFalse(value.contains("10.8.0.2"));
        assertFalse(note.contains("192.168.1.100"));
        assertFalse(note.contains("10.0.0.2"));
        assertFalse(note.contains("127.0.0.1"));
        assertFalse(note.contains("10.8.0.2"));
        assertFalse(value.contains(String.valueOf(ETH_FLAGS)));
        assertFalse(value.contains(String.valueOf(LO_FLAGS)));
        assertFalse(value.contains(String.valueOf(TUN_FLAGS)));
        assertFalse(value.contains("0x1043"));
        assertFalse(value.contains("0x49"));
        assertFalse(note.contains(String.valueOf(ETH_FLAGS)));
        assertFalse(note.contains(String.valueOf(LO_FLAGS)));
        assertFalse(note.contains(String.valueOf(TUN_FLAGS)));
        assertFalse(note.contains("0x1043"));
        assertFalse(note.contains("0x49"));
        assertFalse(value.contains(WLAN_DUPLEX));
        assertFalse(value.contains(ETH_DUPLEX));
        assertFalse(value.contains(LO_DUPLEX));
        assertFalse(note.contains(WLAN_DUPLEX));
        assertFalse(note.contains(ETH_DUPLEX));
        assertFalse(note.contains(LO_DUPLEX));
        assertFalse(value.contains(String.valueOf(WLAN_SPEED)));
        assertFalse(note.contains(String.valueOf(WLAN_SPEED)));
        assertFalse(value.contains(String.valueOf(WLAN_MTU)));
        assertFalse(note.contains(String.valueOf(WLAN_MTU)));
        assertFalse(value.contains(String.valueOf(ETH_MTU)));
        assertFalse(note.contains(String.valueOf(ETH_MTU)));
        assertFalse(value.contains(String.valueOf(LO_HARDWARE_TYPE)));
        assertFalse(note.contains(String.valueOf(LO_HARDWARE_TYPE)));
        assertFalse(value.contains(String.valueOf(TUN_HARDWARE_TYPE)));
        assertFalse(note.contains(String.valueOf(TUN_HARDWARE_TYPE)));
        assertFalse(value.contains(String.valueOf(WLAN_LINK_INDEX)));
        assertFalse(note.contains(String.valueOf(WLAN_LINK_INDEX)));
        assertFalse(value.contains("hardwareType"));
        assertFalse(note.contains("hardwareType"));
        assertFalse(value.contains("speedMbps"));
        assertFalse(note.contains("speedMbps"));
        assertFalse(value.contains("linkIndex"));
        assertFalse(note.contains("linkIndex"));
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
        assertFalse(value.contains("format=duplex"));
        assertFalse(value.contains("format=iflink"));
        assertFalse(note.contains("true"));
        assertFalse(note.contains("false"));
        assertFalse(note.contains(ETH_OPERSTATE));
        assertFalse(note.contains(LO_OPERSTATE));
        assertFalse(note.contains(TUN_OPERSTATE));
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

    private static int countNetworkTxQueueLenReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isNetworkTxQueueLenRead(e, WLAN_TXQL_PATH)
                    || isNetworkTxQueueLenRead(e, ETH_TXQL_PATH)
                    || isNetworkTxQueueLenRead(e, LO_TXQL_PATH)
                    || isNetworkTxQueueLenRead(e, TUN_TXQL_PATH)
                    || isNetworkTxQueueLenRead(e, UNKNOWN_TXQL_PATH)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findTxQueueLenRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isNetworkTxQueueLenRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isNetworkTxQueueLenRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=tx_queue_len");
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
