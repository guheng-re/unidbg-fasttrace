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
 * LinuxFileSystem wiring for {@code network.interfaces[].nameAssignType} → exact
 * {@code /sys/class/net/<name>/name_assign_type} (decimal ASCII + single LF).
 * Uses the default backend (no Unicorn2) so emulator close does not hit Unicorn2
 * lifecycle crashes. Never infers nameAssignType from name, mac, addressAssignType,
 * hardwareType, flags, carrier, operState, speedMbps, duplex, linkIndex, txQueueLen,
 * or wifi. Linux {@code NET_NAME_*}: 0 unknown, 1 enum, 2 predictable, 3 user,
 * 4 renamed.
 */
public class LinuxFileSystemNetworkNameAssignTypeTest {

    private static final String WLAN_NAT_PATH = "/sys/class/net/wlan0/name_assign_type";
    private static final String ETH_NAT_PATH = "/sys/class/net/eth0/name_assign_type";
    private static final String LO_NAT_PATH = "/sys/class/net/lo/name_assign_type";
    private static final String TUN_NAT_PATH = "/sys/class/net/tun1/name_assign_type";
    private static final String UNKNOWN_NAT_PATH = "/sys/class/net/not0/name_assign_type";
    private static final String WLAN_ADDRESS_PATH = "/sys/class/net/wlan0/address";
    private static final String WLAN_MTU_PATH = "/sys/class/net/wlan0/mtu";
    private static final String WLAN_TYPE_PATH = "/sys/class/net/wlan0/type";
    private static final String WLAN_SPEED_PATH = "/sys/class/net/wlan0/speed";
    private static final String WLAN_DUPLEX_PATH = "/sys/class/net/wlan0/duplex";
    private static final String WLAN_IFLINK_PATH = "/sys/class/net/wlan0/iflink";
    private static final String WLAN_IFINDEX_PATH = "/sys/class/net/wlan0/ifindex";
    private static final String WLAN_TXQL_PATH = "/sys/class/net/wlan0/tx_queue_len";
    private static final String WLAN_AAT_PATH = "/sys/class/net/wlan0/addr_assign_type";
    private static final String WLAN_OPERSTATE_PATH = "/sys/class/net/wlan0/operstate";
    private static final String WLAN_CARRIER_PATH = "/sys/class/net/wlan0/carrier";
    private static final String WLAN_FLAGS_PATH = "/sys/class/net/wlan0/flags";
    private static final String ETH_ADDRESS_PATH = "/sys/class/net/eth0/address";
    private static final String ETH_MTU_PATH = "/sys/class/net/eth0/mtu";
    private static final String ETH_TYPE_PATH = "/sys/class/net/eth0/type";
    private static final String ETH_SPEED_PATH = "/sys/class/net/eth0/speed";
    private static final String ETH_DUPLEX_PATH = "/sys/class/net/eth0/duplex";
    private static final String ETH_IFINDEX_PATH = "/sys/class/net/eth0/ifindex";
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
    private static final int LO_AAT = 0;
    private static final int WLAN_AAT = 1;

    private static final int LO_NAT = 0;
    private static final int WLAN_NAT = 4;
    private static final int TUN_NAT = 3;

    private static final String LO_NAT_TEXT = LO_NAT + "\n";
    private static final String WLAN_NAT_TEXT = WLAN_NAT + "\n";
    private static final String TUN_NAT_TEXT = TUN_NAT + "\n";

    private static final String TWO_IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":" + LO_INDEX + ",\"ipv4\":\"127.0.0.1\",\"flags\":" + LO_FLAGS
            + ",\"operState\":\"" + LO_OPERSTATE + "\",\"carrier\":true"
            + ",\"hardwareType\":" + LO_HARDWARE_TYPE
            + ",\"speedMbps\":" + LO_SPEED
            + ",\"duplex\":\"" + LO_DUPLEX + "\""
            + ",\"linkIndex\":" + LO_LINK_INDEX
            + ",\"txQueueLen\":" + LO_TXQL
            + ",\"addressAssignType\":" + LO_AAT
            + ",\"nameAssignType\":" + LO_NAT + "},"
            + "{\"name\":\"wlan0\",\"index\":" + WLAN_INDEX + ",\"ipv4\":\"192.168.1.100\","
            + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU + ",\"flags\":" + WLAN_FLAGS
            + ",\"hardwareType\":" + WLAN_HARDWARE_TYPE
            + ",\"operState\":\"" + WLAN_OPERSTATE + "\",\"carrier\":true"
            + ",\"speedMbps\":" + WLAN_SPEED
            + ",\"duplex\":\"" + WLAN_DUPLEX + "\""
            + ",\"linkIndex\":" + WLAN_LINK_INDEX
            + ",\"txQueueLen\":" + WLAN_TXQL
            + ",\"addressAssignType\":" + WLAN_AAT
            + ",\"nameAssignType\":" + WLAN_NAT + "},"
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
            + ",\"nameAssignType\":" + TUN_NAT + "}"
            + "]}}"
            ;

    @Test
    public void testNameAssignTypeParseValidAndInvalid() {
        TraceEnvironmentConfig zero = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"nameAssignType\":0}"
                + "]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig zeroIface = zero.getNetworkInterfaces().get(0);
        assertTrue(zeroIface.isNameAssignTypeConfigured());
        assertEquals(Integer.valueOf(0), zeroIface.getNameAssignType());
        assertFalse(zeroIface.isAddressAssignTypeConfigured());
        assertNull(zeroIface.getAddressAssignType());
        assertFalse(zeroIface.isHardwareTypeConfigured());
        assertNull(zeroIface.getHardwareType());
        assertFalse(zeroIface.isSpeedMbpsConfigured());
        assertNull(zeroIface.getSpeedMbps());
        assertFalse(zeroIface.isDuplexConfigured());
        assertNull(zeroIface.getDuplex());
        assertFalse(zeroIface.isLinkIndexConfigured());
        assertNull(zeroIface.getLinkIndex());
        assertFalse(zeroIface.isTxQueueLenConfigured());
        assertNull(zeroIface.getTxQueueLen());
        assertFalse(zeroIface.isOperStateConfigured());
        assertNull(zeroIface.getOperState());
        assertFalse(zeroIface.isCarrierConfigured());
        assertNull(zeroIface.getFlags());
        assertNull(zeroIface.getMac());
        assertNull(zeroIface.getMtu());

        TraceEnvironmentConfig four = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"renamed0\",\"index\":5,\"ipv4\":\"10.0.0.5\",\"nameAssignType\":4}"
                + "]}}"
        );
        assertTrue(four.getNetworkInterfaces().get(0).isNameAssignTypeConfigured());
        assertEquals(Integer.valueOf(4), four.getNetworkInterfaces().get(0).getNameAssignType());

        TraceEnvironmentConfig one = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"enum0\",\"index\":6,\"ipv4\":\"10.0.0.6\",\"nameAssignType\":1}"
                + "]}}"
        );
        assertTrue(one.getNetworkInterfaces().get(0).isNameAssignTypeConfigured());
        assertEquals(Integer.valueOf(1), one.getNetworkInterfaces().get(0).getNameAssignType());

        TraceEnvironmentConfig two = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"pred0\",\"index\":7,\"ipv4\":\"10.0.0.7\",\"nameAssignType\":2}"
                + "]}}"
        );
        assertTrue(two.getNetworkInterfaces().get(0).isNameAssignTypeConfigured());
        assertEquals(Integer.valueOf(2), two.getNetworkInterfaces().get(0).getNameAssignType());

        TraceEnvironmentConfig mixed = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        TraceEnvironmentConfig.NetworkInterfaceConfig lo = mixed.getNetworkInterfaces().get(0);
        assertTrue(lo.isNameAssignTypeConfigured());
        assertEquals(Integer.valueOf(LO_NAT), lo.getNameAssignType());
        assertTrue(lo.isAddressAssignTypeConfigured());
        assertEquals(Integer.valueOf(LO_AAT), lo.getAddressAssignType());
        TraceEnvironmentConfig.NetworkInterfaceConfig wlan0 = mixed.getNetworkInterfaces().get(1);
        assertTrue(wlan0.isNameAssignTypeConfigured());
        assertEquals(Integer.valueOf(WLAN_NAT), wlan0.getNameAssignType());
        assertEquals(WLAN_MAC, wlan0.getMac());
        assertEquals(Integer.valueOf(WLAN_MTU), wlan0.getMtu());
        assertTrue(wlan0.isAddressAssignTypeConfigured());
        assertEquals(Integer.valueOf(WLAN_AAT), wlan0.getAddressAssignType());
        TraceEnvironmentConfig.NetworkInterfaceConfig eth0 = mixed.getNetworkInterfaces().get(2);
        assertFalse(eth0.isNameAssignTypeConfigured());
        assertNull(eth0.getNameAssignType());
        assertEquals(ETH_MAC_CANONICAL, eth0.getMac());
        assertEquals(Integer.valueOf(ETH_MTU), eth0.getMtu());
        assertTrue(eth0.isHardwareTypeConfigured());
        assertTrue(eth0.isSpeedMbpsConfigured());
        assertTrue(eth0.isDuplexConfigured());
        assertTrue(eth0.isOperStateConfigured());
        assertTrue(eth0.isCarrierConfigured());
        TraceEnvironmentConfig.NetworkInterfaceConfig tun1 = mixed.getNetworkInterfaces().get(3);
        assertTrue(tun1.isNameAssignTypeConfigured());
        assertEquals(Integer.valueOf(TUN_NAT), tun1.getNameAssignType());
        assertFalse(tun1.isAddressAssignTypeConfigured());
        assertNull(tun1.getAddressAssignType());

        TraceEnvironmentConfig inferredOnly = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"wifi\":{\"enabled\":true,\"linkSpeedMbps\":866},"
                + "\"interfaces\":[{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
                + "\"flags\":4163,\"mac\":\"02:00:00:00:00:01\",\"mtu\":1500,\"hardwareType\":1,"
                + "\"operState\":\"up\",\"carrier\":true,\"speedMbps\":866,\"duplex\":\"full\","
                + "\"linkIndex\":2,\"txQueueLen\":1000,\"addressAssignType\":1}]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig inferredIface =
                inferredOnly.getNetworkInterfaces().get(0);
        assertFalse(inferredIface.isNameAssignTypeConfigured());
        assertNull(inferredIface.getNameAssignType());
        assertEquals("wlan0", inferredIface.getName());
        assertEquals("02:00:00:00:00:01", inferredIface.getMac());
        assertTrue(inferredIface.isAddressAssignTypeConfigured());
        assertEquals(Integer.valueOf(1), inferredIface.getAddressAssignType());
        assertTrue(inferredIface.isHardwareTypeConfigured());
        assertTrue(inferredIface.isSpeedMbpsConfigured());
        assertTrue(inferredIface.isDuplexConfigured());
        assertTrue(inferredIface.isLinkIndexConfigured());
        assertTrue(inferredIface.isTxQueueLenConfigured());
        assertTrue(inferredIface.isOperStateConfigured());
        assertTrue(inferredIface.isCarrierConfigured());

        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"nameAssignType\":-1}]}}",
                "network.interfaces[0].nameAssignType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"nameAssignType\":5}]}}",
                "network.interfaces[0].nameAssignType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"nameAssignType\":2147483648}]}}",
                "network.interfaces[0].nameAssignType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"nameAssignType\":1.5}]}}",
                "network.interfaces[0].nameAssignType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"nameAssignType\":\"0\"}]}}",
                "network.interfaces[0].nameAssignType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"nameAssignType\":\"4\"}]}}",
                "network.interfaces[0].nameAssignType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"nameAssignType\":true}]}}",
                "network.interfaces[0].nameAssignType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"nameAssignType\":false}]}}",
                "network.interfaces[0].nameAssignType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"nameAssignType\":null}]}}",
                "network.interfaces[0].nameAssignType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"nameAssignType\":{}}]}}",
                "network.interfaces[0].nameAssignType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"nameAssignType\":[]}]}}",
                "network.interfaces[0].nameAssignType");
        assertInvalid("{\"network\":{\"interfaces\":[\"lo\"]}}",
                "network.interfaces[0]");
        assertInvalid("{\"network\":{\"interfaces\":[1]}}",
                "network.interfaces[0]");
        assertInvalid("{\"network\":{\"interfaces\":[null]}}",
                "network.interfaces[0]");
    }

    @Test
    public void testConfiguredNameAssignTypeOpenAndSidecar64() throws Exception {
        runConfigured(true);
    }

    @Test
    public void testConfiguredNameAssignTypeOpenAndSidecar32() throws Exception {
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

            String loText = readOpenText(emulator, blocks, LO_NAT_PATH);
            assertEquals(LO_NAT_TEXT, loText);
            assertEquals("0\n", loText);
            assertTrue(loText.endsWith("\n"));
            assertEquals(1, countNewlines(loText));

            String wlanText = readOpenText(emulator, blocks, WLAN_NAT_PATH);
            assertEquals(WLAN_NAT_TEXT, wlanText);
            assertEquals("4\n", wlanText);
            assertTrue(wlanText.endsWith("\n"));
            assertEquals(1, countNewlines(wlanText));

            assertEquals(2, countNetworkNameAssignTypeReads(sink));
            CapturedEvent loEv = findNameAssignTypeRead(sink, LO_NAT_PATH);
            CapturedEvent wlanEv = findNameAssignTypeRead(sink, WLAN_NAT_PATH);
            assertNameAssignTypeSidecar(loEv, LO_NAT_PATH, "lo",
                    LO_NAT_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertNameAssignTypeSidecar(wlanEv, WLAN_NAT_PATH, "wlan0",
                    WLAN_NAT_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsNameAssignTypeValueAndUnrelated(loEv);
            assertSidecarOmitsNameAssignTypeValueAndUnrelated(wlanEv);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesPriorityOverAutoNameAssignType() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"files\":{\"" + WLAN_NAT_PATH + "\":\"custom-nat\\n\"}},"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":" + WLAN_INDEX + ",\"ipv4\":\"192.168.1.100\","
                + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU
                + ",\"nameAssignType\":" + WLAN_NAT + "},"
                + "{\"name\":\"lo\",\"index\":" + LO_INDEX + ",\"ipv4\":\"127.0.0.1\","
                + "\"nameAssignType\":" + LO_NAT + "}"
                + "]}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("custom-nat\n", readOpenText(emulator, blocks, WLAN_NAT_PATH));
            assertEquals(LO_NAT_TEXT, readOpenText(emulator, blocks, LO_NAT_PATH));
            assertTrue(LO_NAT_TEXT.endsWith("\n"));
            assertEquals(1, countNewlines(LO_NAT_TEXT));

            boolean sawLinuxFile = false;
            boolean sawLoNat = false;
            for (CapturedEvent e : sink.events) {
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
                if (isNetworkNameAssignTypeRead(e, WLAN_NAT_PATH)) {
                    fail("wlan0 name_assign_type should not emit network_device read when linux.files wins");
                }
                if (isNetworkNameAssignTypeRead(e, LO_NAT_PATH)) {
                    sawLoNat = true;
                    assertNameAssignTypeSidecar(e, LO_NAT_PATH, "lo",
                            LO_NAT_TEXT.getBytes(StandardCharsets.UTF_8).length);
                    assertSidecarOmitsNameAssignTypeValueAndUnrelated(e);
                }
            }
            assertTrue(sawLinuxFile);
            assertTrue(sawLoNat);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testUnknownMissingNameAssignTypeWriteDirectoryAndOtherSysPathsNotTakenOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, UNKNOWN_NAT_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, ETH_NAT_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, WLAN_ADDRESS_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, WLAN_MTU_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, WLAN_TYPE_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, WLAN_SPEED_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, WLAN_DUPLEX_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, WLAN_IFLINK_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, WLAN_IFINDEX_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, WLAN_TXQL_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, WLAN_AAT_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, WLAN_OPERSTATE_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, WLAN_CARRIER_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, WLAN_FLAGS_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, ETH_ADDRESS_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, ETH_MTU_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, ETH_TYPE_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, ETH_SPEED_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, ETH_DUPLEX_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, ETH_IFINDEX_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, ETH_OPERSTATE_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, ETH_CARRIER_PATH);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, "/sys/class/net/wlan0");
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, WLAN_NAT_PATH + "/");
            assertEquals(WLAN_MAC + "\n", readOpenText(emulator, blocks, WLAN_ADDRESS_PATH));
            assertEquals(WLAN_MTU + "\n", readOpenText(emulator, blocks, WLAN_MTU_PATH));
            assertEquals(WLAN_INDEX + "\n", readOpenText(emulator, blocks, WLAN_IFINDEX_PATH));
            assertEquals(WLAN_AAT + "\n", readOpenText(emulator, blocks, WLAN_AAT_PATH));
            assertEquals(0, countNetworkNameAssignTypeReads(sink));

            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_NAT_PATH, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_NAT_PATH, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_NAT_PATH, IOConstants.O_DIRECTORY);
            assertEquals(0, countNetworkNameAssignTypeReads(sink));

            assertEquals(WLAN_NAT_TEXT, readOpenText(emulator, blocks, WLAN_NAT_PATH));
            assertEquals(1, countNetworkNameAssignTypeReads(sink));
            CapturedEvent wlanEv = findNameAssignTypeRead(sink, WLAN_NAT_PATH);
            assertNameAssignTypeSidecar(wlanEv, WLAN_NAT_PATH, "wlan0",
                    WLAN_NAT_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsNameAssignTypeValueAndUnrelated(wlanEv);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, WLAN_NAT_PATH);
            assertEquals(0, countNetworkNameAssignTypeReads(sink));
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
                    + "\"linkIndex\":2,\"txQueueLen\":1000,\"addressAssignType\":1}]}}"
            );
            emulator = emulatorBuilder(true).setEnvironmentConfig(inferredOnly).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredNameAssignType(emulator, blocks, sink, WLAN_NAT_PATH);
            assertEquals(0, countNetworkNameAssignTypeReads(sink));
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

    private static void assertNotTakenOverAsConfiguredNameAssignType(AndroidEmulator emulator,
                                                                    List<MemoryBlock> blocks,
                                                                    CapturingSink sink,
                                                                    String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            assertFalse(path + " must not serve configured wlan name_assign_type",
                    WLAN_NAT_TEXT.equals(text) && path.contains("/name_assign_type"));
            if (path.contains("/name_assign_type")) {
                assertFalse(path + " must not serve configured lo name_assign_type",
                        LO_NAT_TEXT.equals(text));
                assertFalse(path + " must not serve configured tun name_assign_type",
                        TUN_NAT_TEXT.equals(text));
            }
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected auto name_assign_type sidecar for " + path,
                    isNetworkNameAssignTypeRead(e, path));
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
            assertFalse("write/directory open must not serve configured name_assign_type",
                    WLAN_NAT_TEXT.equals(text) || LO_NAT_TEXT.equals(text)
                            || TUN_NAT_TEXT.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isNetworkNameAssignTypeRead(e, path));
        }
    }

    private static void assertNameAssignTypeSidecar(CapturedEvent e, String path, String name, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",name=" + name + ",format=name_assign_type,bytes=" + bytes,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains(path));
        assertTrue(e.note.contains("读取"));
    }

    private static void assertSidecarOmitsNameAssignTypeValueAndUnrelated(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains("nameAssignType"));
        assertFalse(note.contains("nameAssignType"));
        assertFalse(value.contains("addressAssignType"));
        assertFalse(note.contains("addressAssignType"));
        assertFalse(value.contains(LO_NAT_TEXT));
        assertFalse(value.contains(WLAN_NAT_TEXT));
        assertFalse(value.contains(TUN_NAT_TEXT));
        assertFalse(note.contains(LO_NAT_TEXT));
        assertFalse(note.contains(WLAN_NAT_TEXT));
        assertFalse(note.contains(TUN_NAT_TEXT));
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
        assertFalse(value.contains("hardwareType"));
        assertFalse(note.contains("hardwareType"));
        assertFalse(value.contains("speedMbps"));
        assertFalse(note.contains("speedMbps"));
        assertFalse(value.contains("duplex"));
        assertFalse(note.contains("duplex"));
        assertFalse(value.contains("linkIndex"));
        assertFalse(note.contains("linkIndex"));
        assertFalse(value.contains("txQueueLen"));
        assertFalse(note.contains("txQueueLen"));
        assertFalse(value.contains("operState"));
        assertFalse(note.contains("operState"));
        assertFalse(value.contains("carrier"));
        assertFalse(note.contains("carrier"));
        assertFalse(value.contains(WLAN_DUPLEX));
        assertFalse(value.contains(ETH_DUPLEX));
        assertFalse(note.contains(WLAN_DUPLEX));
        assertFalse(note.contains(ETH_DUPLEX));
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
        assertFalse(value.contains(String.valueOf(WLAN_TXQL)));
        assertFalse(note.contains(String.valueOf(WLAN_TXQL)));
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
        assertFalse(value.contains("format=tx_queue_len"));
        assertFalse(value.contains("format=addr_assign_type"));
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

    private static int countNetworkNameAssignTypeReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isNetworkNameAssignTypeRead(e, WLAN_NAT_PATH)
                    || isNetworkNameAssignTypeRead(e, ETH_NAT_PATH)
                    || isNetworkNameAssignTypeRead(e, LO_NAT_PATH)
                    || isNetworkNameAssignTypeRead(e, TUN_NAT_PATH)
                    || isNetworkNameAssignTypeRead(e, UNKNOWN_NAT_PATH)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findNameAssignTypeRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isNetworkNameAssignTypeRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isNetworkNameAssignTypeRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=name_assign_type");
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
