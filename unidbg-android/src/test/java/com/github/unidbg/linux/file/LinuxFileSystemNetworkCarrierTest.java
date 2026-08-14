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
 * LinuxFileSystem wiring for {@code network.interfaces[].carrier} → exact
 * {@code /sys/class/net/<name>/carrier} (UTF-8 {@code 1} or {@code 0} + LF).
 * Uses the default backend (no Unicorn2) so emulator close does not hit Unicorn2
 * lifecycle crashes. Never infers carrier from operState, flags, or wifi.
 */
public class LinuxFileSystemNetworkCarrierTest {

    private static final String WLAN_CARRIER_PATH = "/sys/class/net/wlan0/carrier";
    private static final String ETH_CARRIER_PATH = "/sys/class/net/eth0/carrier";
    private static final String LO_CARRIER_PATH = "/sys/class/net/lo/carrier";
    private static final String UNKNOWN_CARRIER_PATH = "/sys/class/net/not0/carrier";
    private static final String WLAN_FLAGS_PATH = "/sys/class/net/wlan0/flags";
    private static final String WLAN_ADDRESS_PATH = "/sys/class/net/wlan0/address";
    private static final String WLAN_MTU_PATH = "/sys/class/net/wlan0/mtu";
    private static final String WLAN_IFINDEX_PATH = "/sys/class/net/wlan0/ifindex";
    private static final String WLAN_OPERSTATE_PATH = "/sys/class/net/wlan0/operstate";
    private static final String ETH_OPERSTATE_PATH = "/sys/class/net/eth0/operstate";

    private static final String WLAN_MAC = "02:00:00:00:00:01";
    private static final String ETH_MAC_JSON = "AA:BB:CC:DD:EE:FF";
    private static final String ETH_MAC_CANONICAL = "aa:bb:cc:dd:ee:ff";

    private static final int LO_FLAGS = 73;
    private static final int ETH_FLAGS = 4163;
    private static final int WLAN_FLAGS = 1;
    private static final int WLAN_MTU = 1500;

    private static final String LO_OPERSTATE = "down";
    private static final String ETH_OPERSTATE = "up";

    private static final String CARRIER_UP_TEXT = "1\n";
    private static final String CARRIER_DOWN_TEXT = "0\n";

    private static final String TWO_IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"flags\":" + LO_FLAGS
            + ",\"operState\":\"" + LO_OPERSTATE + "\",\"carrier\":true},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
            + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU + ",\"flags\":" + WLAN_FLAGS + "},"
            + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
            + "\"mac\":\"" + ETH_MAC_JSON + "\",\"flags\":" + ETH_FLAGS
            + ",\"operState\":\"" + ETH_OPERSTATE + "\",\"carrier\":false}"
            + "]}}"
            ;

    @Test
    public void testCarrierParseValidAndInvalid() {
        TraceEnvironmentConfig trueCfg = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"eth0\",\"index\":1,\"ipv4\":\"10.0.0.1\",\"carrier\":true}"
                + "]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig trueIface =
                trueCfg.getNetworkInterfaces().get(0);
        assertTrue(trueIface.isCarrierConfigured());
        assertTrue(trueIface.isCarrier());
        assertFalse(trueIface.isOperStateConfigured());
        assertNull(trueIface.getOperState());
        assertNull(trueIface.getFlags());

        TraceEnvironmentConfig falseCfg = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"eth0\",\"index\":1,\"ipv4\":\"10.0.0.1\",\"carrier\":false}"
                + "]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig falseIface =
                falseCfg.getNetworkInterfaces().get(0);
        assertTrue(falseIface.isCarrierConfigured());
        assertFalse(falseIface.isCarrier());

        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        TraceEnvironmentConfig.NetworkInterfaceConfig lo = omitted.getNetworkInterfaces().get(0);
        assertTrue(lo.isCarrierConfigured());
        assertTrue(lo.isCarrier());
        assertTrue(lo.isOperStateConfigured());
        assertEquals(LO_OPERSTATE, lo.getOperState());
        TraceEnvironmentConfig.NetworkInterfaceConfig wlan0 = omitted.getNetworkInterfaces().get(1);
        assertFalse(wlan0.isCarrierConfigured());
        assertFalse(wlan0.isCarrier());
        assertFalse(wlan0.isOperStateConfigured());
        assertEquals(Integer.valueOf(WLAN_FLAGS), wlan0.getFlags());
        TraceEnvironmentConfig.NetworkInterfaceConfig eth0 = omitted.getNetworkInterfaces().get(2);
        assertTrue(eth0.isCarrierConfigured());
        assertFalse(eth0.isCarrier());
        assertTrue(eth0.isOperStateConfigured());
        assertEquals(ETH_OPERSTATE, eth0.getOperState());
        assertEquals(Integer.valueOf(ETH_FLAGS), eth0.getFlags());

        TraceEnvironmentConfig flagsWifiOnly = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"wifi\":{\"enabled\":true},"
                + "\"interfaces\":[{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.1\","
                + "\"flags\":1}]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig flagsIface =
                flagsWifiOnly.getNetworkInterfaces().get(0);
        assertFalse(flagsIface.isCarrierConfigured());
        assertFalse(flagsIface.isCarrier());
        assertFalse(flagsIface.isOperStateConfigured());
        assertEquals(Integer.valueOf(1), flagsIface.getFlags());

        TraceEnvironmentConfig operOnly = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.1\","
                + "\"operState\":\"up\"}]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig operIface =
                operOnly.getNetworkInterfaces().get(0);
        assertFalse(operIface.isCarrierConfigured());
        assertFalse(operIface.isCarrier());
        assertTrue(operIface.isOperStateConfigured());
        assertEquals("up", operIface.getOperState());

        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"carrier\":null}]}}",
                "network.interfaces[0].carrier");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"carrier\":1}]}}",
                "network.interfaces[0].carrier");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"carrier\":0}]}}",
                "network.interfaces[0].carrier");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"carrier\":\"true\"}]}}",
                "network.interfaces[0].carrier");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"carrier\":\"false\"}]}}",
                "network.interfaces[0].carrier");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"carrier\":{}}]}}",
                "network.interfaces[0].carrier");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"carrier\":[]}]}}",
                "network.interfaces[0].carrier");
    }

    @Test
    public void testConfiguredCarrierOpenAndSidecar64() throws Exception {
        runConfigured(true);
    }

    @Test
    public void testConfiguredCarrierOpenAndSidecar32() throws Exception {
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

            String ethText = readOpenText(emulator, blocks, ETH_CARRIER_PATH);
            assertEquals(CARRIER_DOWN_TEXT, ethText);
            assertTrue(ethText.endsWith("\n"));
            assertEquals(1, countNewlines(ethText));

            String loText = readOpenText(emulator, blocks, LO_CARRIER_PATH);
            assertEquals(CARRIER_UP_TEXT, loText);
            assertTrue(loText.endsWith("\n"));
            assertEquals(1, countNewlines(loText));

            assertEquals(2, countNetworkCarrierReads(sink));
            CapturedEvent ethEv = findCarrierRead(sink, ETH_CARRIER_PATH);
            CapturedEvent loEv = findCarrierRead(sink, LO_CARRIER_PATH);
            assertCarrierSidecar(ethEv, ETH_CARRIER_PATH, "eth0",
                    CARRIER_DOWN_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertCarrierSidecar(loEv, LO_CARRIER_PATH, "lo",
                    CARRIER_UP_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsCarrierValueAndUnrelated(ethEv);
            assertSidecarOmitsCarrierValueAndUnrelated(loEv);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesPriorityOverAutoCarrier() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"files\":{\"" + WLAN_CARRIER_PATH + "\":\"custom-carrier\\n\"}},"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
                + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU
                + ",\"carrier\":true},"
                + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
                + "\"mac\":\"" + ETH_MAC_JSON + "\",\"carrier\":false}"
                + "]}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("custom-carrier\n", readOpenText(emulator, blocks, WLAN_CARRIER_PATH));
            assertEquals(CARRIER_DOWN_TEXT, readOpenText(emulator, blocks, ETH_CARRIER_PATH));

            boolean sawLinuxFile = false;
            boolean sawEthCarrier = false;
            for (CapturedEvent e : sink.events) {
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
                if (isNetworkCarrierRead(e, WLAN_CARRIER_PATH)) {
                    fail("wlan0 carrier should not emit network_device read when linux.files wins");
                }
                if (isNetworkCarrierRead(e, ETH_CARRIER_PATH)) {
                    sawEthCarrier = true;
                    assertSidecarOmitsCarrierValueAndUnrelated(e);
                }
            }
            assertTrue(sawLinuxFile);
            assertTrue(sawEthCarrier);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testUnknownMissingCarrierWriteDirectoryAndOtherSysPathsNotTakenOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertNotTakenOverAsConfiguredCarrier(emulator, blocks, sink, UNKNOWN_CARRIER_PATH);
            assertNotTakenOverAsConfiguredCarrier(emulator, blocks, sink, WLAN_CARRIER_PATH);
            assertNotTakenOverAsConfiguredCarrier(emulator, blocks, sink, WLAN_FLAGS_PATH);
            assertNotTakenOverAsConfiguredCarrier(emulator, blocks, sink, WLAN_ADDRESS_PATH);
            assertNotTakenOverAsConfiguredCarrier(emulator, blocks, sink, WLAN_MTU_PATH);
            assertNotTakenOverAsConfiguredCarrier(emulator, blocks, sink, WLAN_IFINDEX_PATH);
            assertNotTakenOverAsConfiguredCarrier(emulator, blocks, sink, WLAN_OPERSTATE_PATH);
            assertNotTakenOverAsConfiguredCarrier(emulator, blocks, sink, ETH_OPERSTATE_PATH);
            assertNotTakenOverAsConfiguredCarrier(emulator, blocks, sink, "/sys/class/net/wlan0");
            assertNotTakenOverAsConfiguredCarrier(emulator, blocks, sink, WLAN_CARRIER_PATH + "/");
            assertEquals(WLAN_MAC + "\n", readOpenText(emulator, blocks, WLAN_ADDRESS_PATH));
            assertEquals(WLAN_MTU + "\n", readOpenText(emulator, blocks, WLAN_MTU_PATH));
            assertEquals("2\n", readOpenText(emulator, blocks, WLAN_IFINDEX_PATH));
            assertEquals(0, countNetworkCarrierReads(sink));

            assertWriteOpenNotTakenOver(emulator, blocks, sink, ETH_CARRIER_PATH, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, ETH_CARRIER_PATH, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, ETH_CARRIER_PATH, IOConstants.O_DIRECTORY);
            assertEquals(0, countNetworkCarrierReads(sink));

            assertEquals(CARRIER_DOWN_TEXT, readOpenText(emulator, blocks, ETH_CARRIER_PATH));
            assertEquals(1, countNetworkCarrierReads(sink));
            CapturedEvent ethEv = findCarrierRead(sink, ETH_CARRIER_PATH);
            assertCarrierSidecar(ethEv, ETH_CARRIER_PATH, "eth0",
                    CARRIER_DOWN_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsCarrierValueAndUnrelated(ethEv);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredCarrier(emulator, blocks, sink, ETH_CARRIER_PATH);
            assertEquals(0, countNetworkCarrierReads(sink));
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

    private static void assertNotTakenOverAsConfiguredCarrier(AndroidEmulator emulator,
                                                              List<MemoryBlock> blocks,
                                                              CapturingSink sink,
                                                              String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            assertFalse(path + " must not serve configured eth carrier", CARRIER_DOWN_TEXT.equals(text));
            assertFalse(path + " must not serve configured lo carrier", CARRIER_UP_TEXT.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected auto carrier sidecar for " + path,
                    isNetworkCarrierRead(e, path));
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
            assertFalse("write/directory open must not serve configured carrier",
                    CARRIER_DOWN_TEXT.equals(text) || CARRIER_UP_TEXT.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isNetworkCarrierRead(e, path));
        }
    }

    private static void assertCarrierSidecar(CapturedEvent e, String path, String name, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",name=" + name + ",format=carrier,bytes=" + bytes,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains(path));
        assertTrue(e.note.contains("读取"));
    }

    private static void assertSidecarOmitsCarrierValueAndUnrelated(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains("true"));
        assertFalse(value.contains("false"));
        assertFalse(note.contains("true"));
        assertFalse(note.contains("false"));
        assertFalse(value.contains(CARRIER_UP_TEXT));
        assertFalse(value.contains(CARRIER_DOWN_TEXT));
        assertFalse(note.contains(CARRIER_UP_TEXT));
        assertFalse(note.contains(CARRIER_DOWN_TEXT));
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

    private static int countNetworkCarrierReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isNetworkCarrierRead(e, WLAN_CARRIER_PATH)
                    || isNetworkCarrierRead(e, ETH_CARRIER_PATH)
                    || isNetworkCarrierRead(e, LO_CARRIER_PATH)
                    || isNetworkCarrierRead(e, UNKNOWN_CARRIER_PATH)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findCarrierRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isNetworkCarrierRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isNetworkCarrierRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=carrier");
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
