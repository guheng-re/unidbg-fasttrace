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
 * LinuxFileSystem wiring for {@code network.interfaces[].hardwareType} → exact
 * {@code /sys/class/net/<name>/type} (UTF-8 decimal ARPHRD + LF).
 * Uses the default backend (no Unicorn2) so emulator close does not hit Unicorn2
 * lifecycle crashes. Never infers hardwareType from name, flags, MAC, operState,
 * or carrier.
 */
public class LinuxFileSystemNetworkHardwareTypeTest {

    private static final String WLAN_TYPE_PATH = "/sys/class/net/wlan0/type";
    private static final String ETH_TYPE_PATH = "/sys/class/net/eth0/type";
    private static final String LO_TYPE_PATH = "/sys/class/net/lo/type";
    private static final String UNKNOWN_TYPE_PATH = "/sys/class/net/not0/type";
    private static final String WLAN_FLAGS_PATH = "/sys/class/net/wlan0/flags";
    private static final String WLAN_ADDRESS_PATH = "/sys/class/net/wlan0/address";
    private static final String WLAN_MTU_PATH = "/sys/class/net/wlan0/mtu";
    private static final String WLAN_IFINDEX_PATH = "/sys/class/net/wlan0/ifindex";
    private static final String WLAN_OPERSTATE_PATH = "/sys/class/net/wlan0/operstate";
    private static final String WLAN_CARRIER_PATH = "/sys/class/net/wlan0/carrier";
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

    private static final String LO_TYPE_TEXT = LO_HARDWARE_TYPE + "\n";
    private static final String ETH_TYPE_TEXT = ETH_HARDWARE_TYPE + "\n";

    private static final String TWO_IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"flags\":" + LO_FLAGS
            + ",\"operState\":\"" + LO_OPERSTATE + "\",\"carrier\":true"
            + ",\"hardwareType\":" + LO_HARDWARE_TYPE + "},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
            + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU + ",\"flags\":" + WLAN_FLAGS + "},"
            + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
            + "\"mac\":\"" + ETH_MAC_JSON + "\",\"flags\":" + ETH_FLAGS
            + ",\"operState\":\"" + ETH_OPERSTATE + "\",\"carrier\":false"
            + ",\"hardwareType\":" + ETH_HARDWARE_TYPE + "}"
            + "]}}"
            ;

    @Test
    public void testHardwareTypeParseValidAndInvalid() {
        TraceEnvironmentConfig ether = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"eth0\",\"index\":1,\"ipv4\":\"10.0.0.1\",\"hardwareType\":1}"
                + "]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig etherIface =
                ether.getNetworkInterfaces().get(0);
        assertTrue(etherIface.isHardwareTypeConfigured());
        assertEquals(Integer.valueOf(1), etherIface.getHardwareType());
        assertFalse(etherIface.isOperStateConfigured());
        assertNull(etherIface.getOperState());
        assertFalse(etherIface.isCarrierConfigured());
        assertNull(etherIface.getFlags());
        assertNull(etherIface.getMac());

        TraceEnvironmentConfig loopback = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"hardwareType\":772}"
                + "]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig loopIface =
                loopback.getNetworkInterfaces().get(0);
        assertTrue(loopIface.isHardwareTypeConfigured());
        assertEquals(Integer.valueOf(772), loopIface.getHardwareType());

        TraceEnvironmentConfig zero = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"tun0\",\"index\":4,\"ipv4\":\"10.0.0.4\",\"hardwareType\":0}"
                + "]}}"
        );
        assertTrue(zero.getNetworkInterfaces().get(0).isHardwareTypeConfigured());
        assertEquals(Integer.valueOf(0), zero.getNetworkInterfaces().get(0).getHardwareType());

        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"max0\",\"index\":5,\"ipv4\":\"10.0.0.5\",\"hardwareType\":65535}"
                + "]}}"
        );
        assertTrue(max.getNetworkInterfaces().get(0).isHardwareTypeConfigured());
        assertEquals(Integer.valueOf(65535), max.getNetworkInterfaces().get(0).getHardwareType());

        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        TraceEnvironmentConfig.NetworkInterfaceConfig lo = omitted.getNetworkInterfaces().get(0);
        assertTrue(lo.isHardwareTypeConfigured());
        assertEquals(Integer.valueOf(LO_HARDWARE_TYPE), lo.getHardwareType());
        assertTrue(lo.isOperStateConfigured());
        assertTrue(lo.isCarrierConfigured());
        TraceEnvironmentConfig.NetworkInterfaceConfig wlan0 = omitted.getNetworkInterfaces().get(1);
        assertFalse(wlan0.isHardwareTypeConfigured());
        assertNull(wlan0.getHardwareType());
        assertEquals(Integer.valueOf(WLAN_FLAGS), wlan0.getFlags());
        assertEquals(WLAN_MAC, wlan0.getMac());
        TraceEnvironmentConfig.NetworkInterfaceConfig eth0 = omitted.getNetworkInterfaces().get(2);
        assertTrue(eth0.isHardwareTypeConfigured());
        assertEquals(Integer.valueOf(ETH_HARDWARE_TYPE), eth0.getHardwareType());
        assertTrue(eth0.isOperStateConfigured());
        assertTrue(eth0.isCarrierConfigured());
        assertEquals(Integer.valueOf(ETH_FLAGS), eth0.getFlags());

        TraceEnvironmentConfig inferredOnly = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"wifi\":{\"enabled\":true},"
                + "\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                + "\"flags\":73,\"mac\":\"00:00:00:00:00:00\",\"operState\":\"unknown\","
                + "\"carrier\":false}]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig inferredIface =
                inferredOnly.getNetworkInterfaces().get(0);
        assertFalse(inferredIface.isHardwareTypeConfigured());
        assertNull(inferredIface.getHardwareType());
        assertEquals("lo", inferredIface.getName());
        assertEquals(Integer.valueOf(73), inferredIface.getFlags());
        assertEquals("00:00:00:00:00:00", inferredIface.getMac());
        assertTrue(inferredIface.isOperStateConfigured());
        assertTrue(inferredIface.isCarrierConfigured());

        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"hardwareType\":-1}]}}",
                "network.interfaces[0].hardwareType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"hardwareType\":65536}]}}",
                "network.interfaces[0].hardwareType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"hardwareType\":1.5}]}}",
                "network.interfaces[0].hardwareType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"hardwareType\":\"1\"}]}}",
                "network.interfaces[0].hardwareType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"hardwareType\":\"772\"}]}}",
                "network.interfaces[0].hardwareType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"hardwareType\":true}]}}",
                "network.interfaces[0].hardwareType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"hardwareType\":null}]}}",
                "network.interfaces[0].hardwareType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"hardwareType\":{}}]}}",
                "network.interfaces[0].hardwareType");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"hardwareType\":[]}]}}",
                "network.interfaces[0].hardwareType");
    }

    @Test
    public void testConfiguredHardwareTypeOpenAndSidecar64() throws Exception {
        runConfigured(true);
    }

    @Test
    public void testConfiguredHardwareTypeOpenAndSidecar32() throws Exception {
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

            String ethText = readOpenText(emulator, blocks, ETH_TYPE_PATH);
            assertEquals(ETH_TYPE_TEXT, ethText);
            assertTrue(ethText.endsWith("\n"));
            assertEquals(1, countNewlines(ethText));

            String loText = readOpenText(emulator, blocks, LO_TYPE_PATH);
            assertEquals(LO_TYPE_TEXT, loText);
            assertTrue(loText.endsWith("\n"));
            assertEquals(1, countNewlines(loText));

            assertEquals(2, countNetworkTypeReads(sink));
            CapturedEvent ethEv = findTypeRead(sink, ETH_TYPE_PATH);
            CapturedEvent loEv = findTypeRead(sink, LO_TYPE_PATH);
            assertTypeSidecar(ethEv, ETH_TYPE_PATH, "eth0",
                    ETH_TYPE_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertTypeSidecar(loEv, LO_TYPE_PATH, "lo",
                    LO_TYPE_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsHardwareTypeValueAndUnrelated(ethEv);
            assertSidecarOmitsHardwareTypeValueAndUnrelated(loEv);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesPriorityOverAutoHardwareType() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"files\":{\"" + WLAN_TYPE_PATH + "\":\"custom-type\\n\"}},"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
                + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU
                + ",\"hardwareType\":1},"
                + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
                + "\"mac\":\"" + ETH_MAC_JSON + "\",\"hardwareType\":772}"
                + "]}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("custom-type\n", readOpenText(emulator, blocks, WLAN_TYPE_PATH));
            assertEquals("772\n", readOpenText(emulator, blocks, ETH_TYPE_PATH));

            boolean sawLinuxFile = false;
            boolean sawEthType = false;
            for (CapturedEvent e : sink.events) {
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
                if (isNetworkTypeRead(e, WLAN_TYPE_PATH)) {
                    fail("wlan0 type should not emit network_device read when linux.files wins");
                }
                if (isNetworkTypeRead(e, ETH_TYPE_PATH)) {
                    sawEthType = true;
                    assertSidecarOmitsHardwareTypeValueAndUnrelated(e);
                }
            }
            assertTrue(sawLinuxFile);
            assertTrue(sawEthType);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testUnknownMissingHardwareTypeWriteDirectoryAndOtherSysPathsNotTakenOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertNotTakenOverAsConfiguredType(emulator, blocks, sink, UNKNOWN_TYPE_PATH);
            assertNotTakenOverAsConfiguredType(emulator, blocks, sink, WLAN_TYPE_PATH);
            assertNotTakenOverAsConfiguredType(emulator, blocks, sink, WLAN_FLAGS_PATH);
            assertNotTakenOverAsConfiguredType(emulator, blocks, sink, WLAN_ADDRESS_PATH);
            assertNotTakenOverAsConfiguredType(emulator, blocks, sink, WLAN_MTU_PATH);
            assertNotTakenOverAsConfiguredType(emulator, blocks, sink, WLAN_IFINDEX_PATH);
            assertNotTakenOverAsConfiguredType(emulator, blocks, sink, WLAN_OPERSTATE_PATH);
            assertNotTakenOverAsConfiguredType(emulator, blocks, sink, WLAN_CARRIER_PATH);
            assertNotTakenOverAsConfiguredType(emulator, blocks, sink, ETH_OPERSTATE_PATH);
            assertNotTakenOverAsConfiguredType(emulator, blocks, sink, ETH_CARRIER_PATH);
            assertNotTakenOverAsConfiguredType(emulator, blocks, sink, "/sys/class/net/wlan0");
            assertNotTakenOverAsConfiguredType(emulator, blocks, sink, WLAN_TYPE_PATH + "/");
            assertEquals(WLAN_MAC + "\n", readOpenText(emulator, blocks, WLAN_ADDRESS_PATH));
            assertEquals(WLAN_MTU + "\n", readOpenText(emulator, blocks, WLAN_MTU_PATH));
            assertEquals("2\n", readOpenText(emulator, blocks, WLAN_IFINDEX_PATH));
            assertEquals(0, countNetworkTypeReads(sink));

            assertWriteOpenNotTakenOver(emulator, blocks, sink, ETH_TYPE_PATH, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, ETH_TYPE_PATH, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, ETH_TYPE_PATH, IOConstants.O_DIRECTORY);
            assertEquals(0, countNetworkTypeReads(sink));

            assertEquals(ETH_TYPE_TEXT, readOpenText(emulator, blocks, ETH_TYPE_PATH));
            assertEquals(1, countNetworkTypeReads(sink));
            CapturedEvent ethEv = findTypeRead(sink, ETH_TYPE_PATH);
            assertTypeSidecar(ethEv, ETH_TYPE_PATH, "eth0",
                    ETH_TYPE_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsHardwareTypeValueAndUnrelated(ethEv);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredType(emulator, blocks, sink, ETH_TYPE_PATH);
            assertEquals(0, countNetworkTypeReads(sink));
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

    private static void assertNotTakenOverAsConfiguredType(AndroidEmulator emulator,
                                                           List<MemoryBlock> blocks,
                                                           CapturingSink sink,
                                                           String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            assertFalse(path + " must not serve configured eth hardwareType", ETH_TYPE_TEXT.equals(text));
            assertFalse(path + " must not serve configured lo hardwareType", LO_TYPE_TEXT.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected auto type sidecar for " + path,
                    isNetworkTypeRead(e, path));
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
            assertFalse("write/directory open must not serve configured hardwareType",
                    ETH_TYPE_TEXT.equals(text) || LO_TYPE_TEXT.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isNetworkTypeRead(e, path));
        }
    }

    private static void assertTypeSidecar(CapturedEvent e, String path, String name, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",name=" + name + ",format=type,bytes=" + bytes,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains(path));
        assertTrue(e.note.contains("读取"));
    }

    private static void assertSidecarOmitsHardwareTypeValueAndUnrelated(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains("hardwareType"));
        assertFalse(note.contains("hardwareType"));
        assertFalse(value.contains(String.valueOf(LO_HARDWARE_TYPE)));
        assertFalse(note.contains(String.valueOf(LO_HARDWARE_TYPE)));
        assertFalse(value.contains(ETH_TYPE_TEXT));
        assertFalse(value.contains(LO_TYPE_TEXT));
        assertFalse(note.contains(ETH_TYPE_TEXT));
        assertFalse(note.contains(LO_TYPE_TEXT));
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
        assertFalse(value.contains("format=address"));
        assertFalse(value.contains("format=mtu"));
        assertFalse(value.contains("format=ifindex"));
        assertFalse(value.contains("format=flags"));
        assertFalse(value.contains("format=operstate"));
        assertFalse(value.contains("format=carrier"));
        // ETH_HARDWARE_TYPE is 1; assert it is not a standalone sidecar field value.
        assertFalse(value.contains("=" + ETH_HARDWARE_TYPE + ","));
        assertFalse(value.contains("=" + ETH_HARDWARE_TYPE));
        assertFalse(note.contains(String.valueOf(ETH_HARDWARE_TYPE)));
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

    private static int countNetworkTypeReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isNetworkTypeRead(e, WLAN_TYPE_PATH)
                    || isNetworkTypeRead(e, ETH_TYPE_PATH)
                    || isNetworkTypeRead(e, LO_TYPE_PATH)
                    || isNetworkTypeRead(e, UNKNOWN_TYPE_PATH)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findTypeRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isNetworkTypeRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isNetworkTypeRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=type");
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
