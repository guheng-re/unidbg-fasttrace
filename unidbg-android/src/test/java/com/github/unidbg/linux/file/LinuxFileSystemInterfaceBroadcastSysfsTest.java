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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * LinuxFileSystem wiring for {@code network.interfaces[].linkLayerBroadcast} → exact
 * {@code /sys/class/net/<name>/broadcast} (UTF-8 lowercase MAC + single LF).
 * Default backend (no Unicorn2). Never infers from {@code mac}, IPv4 {@code broadcast},
 * {@code hardwareType}, {@code flags}, or other interface fields.
 */
public class LinuxFileSystemInterfaceBroadcastSysfsTest {

    private static final String WLAN_BCAST_PATH = "/sys/class/net/wlan0/broadcast";
    private static final String ETH_BCAST_PATH = "/sys/class/net/eth0/broadcast";
    private static final String LO_BCAST_PATH = "/sys/class/net/lo/broadcast";
    private static final String UNKNOWN_BCAST_PATH = "/sys/class/net/not0/broadcast";
    private static final String WLAN_ADDRESS_PATH = "/sys/class/net/wlan0/address";
    private static final String WLAN_FLAGS_PATH = "/sys/class/net/wlan0/flags";

    private static final String WLAN_MAC = "02:00:00:00:00:01";
    private static final String ETH_MAC_JSON = "AA:BB:CC:DD:EE:FF";
    private static final String ETH_MAC_CANONICAL = "aa:bb:cc:dd:ee:ff";
    private static final String WLAN_IPV4 = "192.168.1.100";
    private static final String WLAN_IPV4_BCAST = "192.168.1.255";
    private static final String LL_BCAST_JSON = "FF:FF:FF:FF:FF:FF";
    private static final String LL_BCAST_CANONICAL = "ff:ff:ff:ff:ff:ff";
    private static final String LL_BCAST_TEXT = LL_BCAST_CANONICAL + "\n";
    private static final byte[] LL_BCAST_BYTES = LL_BCAST_TEXT.getBytes(StandardCharsets.UTF_8);

    private static final String CONFIGURED_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"flags\":73,"
            + "\"mac\":\"00:00:00:00:00:00\",\"hardwareType\":772},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"" + WLAN_IPV4 + "\","
            + "\"broadcast\":\"" + WLAN_IPV4_BCAST + "\",\"flags\":4163,"
            + "\"mac\":\"" + WLAN_MAC + "\",\"hardwareType\":1,"
            + "\"linkLayerBroadcast\":\"" + LL_BCAST_JSON + "\"},"
            + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
            + "\"mac\":\"" + ETH_MAC_JSON + "\",\"flags\":4163,\"hardwareType\":1}"
            + "]}}"
            ;

    @Test
    public void testLinkLayerBroadcastParseCanonicalAndThreeInvalid() {
        TraceEnvironmentConfig upper = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"" + WLAN_IPV4 + "\","
                + "\"mac\":\"" + WLAN_MAC + "\",\"broadcast\":\"" + WLAN_IPV4_BCAST + "\","
                + "\"linkLayerBroadcast\":\"" + LL_BCAST_JSON + "\"}"
                + "]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig wlan = upper.getNetworkInterfaces().get(0);
        assertTrue(wlan.isLinkLayerBroadcastConfigured());
        assertEquals(LL_BCAST_CANONICAL, wlan.getLinkLayerBroadcast());
        assertEquals(WLAN_MAC, wlan.getMac());
        assertEquals(WLAN_IPV4_BCAST, wlan.getBroadcast());
        assertFalse(LL_BCAST_CANONICAL.equals(wlan.getMac()));
        assertFalse(LL_BCAST_CANONICAL.equals(wlan.getBroadcast()));

        TraceEnvironmentConfig mixed = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        TraceEnvironmentConfig.NetworkInterfaceConfig lo = mixed.getNetworkInterfaces().get(0);
        assertFalse(lo.isLinkLayerBroadcastConfigured());
        assertNull(lo.getLinkLayerBroadcast());
        assertEquals("00:00:00:00:00:00", lo.getMac());
        TraceEnvironmentConfig.NetworkInterfaceConfig wlan0 = mixed.getNetworkInterfaces().get(1);
        assertTrue(wlan0.isLinkLayerBroadcastConfigured());
        assertEquals(LL_BCAST_CANONICAL, wlan0.getLinkLayerBroadcast());
        assertEquals(WLAN_MAC, wlan0.getMac());
        assertEquals(WLAN_IPV4_BCAST, wlan0.getBroadcast());
        TraceEnvironmentConfig.NetworkInterfaceConfig eth0 = mixed.getNetworkInterfaces().get(2);
        assertFalse(eth0.isLinkLayerBroadcastConfigured());
        assertNull(eth0.getLinkLayerBroadcast());
        assertEquals(ETH_MAC_CANONICAL, eth0.getMac());

        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"linkLayerBroadcast\":null}]}}",
                "network.interfaces[0].linkLayerBroadcast");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"linkLayerBroadcast\":\"\"}]}}",
                "network.interfaces[0].linkLayerBroadcast");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"linkLayerBroadcast\":\"192.168.1.255\"}]}}",
                "network.interfaces[0].linkLayerBroadcast");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"linkLayerBroadcast\":1.5}]}}",
                "network.interfaces[0].linkLayerBroadcast");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"linkLayerBroadcast\":[\"ff:ff:ff:ff:ff:ff\"]}]}}",
                "network.interfaces[0].linkLayerBroadcast");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"linkLayerBroadcast\":\" ff:ff:ff:ff:ff:ff\"}]}}",
                "network.interfaces[0].linkLayerBroadcast");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"linkLayerBroadcast\":\"ff:ff:ff:ff:ff\"}]}}",
                "network.interfaces[0].linkLayerBroadcast");
    }

    @Test
    public void testConfiguredBroadcastOpenCanonicalBytesAndSidecar() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] raw = readOpenBytes(emulator, blocks, WLAN_BCAST_PATH);
            assertArrayEquals(LL_BCAST_BYTES, raw);
            String text = new String(raw, StandardCharsets.UTF_8);
            assertEquals(LL_BCAST_TEXT, text);
            assertEquals(text, text.toLowerCase());
            assertTrue(text.endsWith("\n"));
            assertEquals(1, countNewlines(text));
            assertEquals(18, raw.length);

            assertEquals(1, countNetworkBroadcastReads(sink));
            CapturedEvent ev = findBroadcastRead(sink, WLAN_BCAST_PATH);
            assertBroadcastSidecar(ev, WLAN_BCAST_PATH, "wlan0", LL_BCAST_BYTES.length);
            assertSidecarOmitsAddresses(ev);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testOmittedFieldDoesNotInferFromMacOrIpv4Broadcast() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"" + WLAN_IPV4 + "\","
                + "\"broadcast\":\"" + WLAN_IPV4_BCAST + "\",\"mac\":\"" + WLAN_MAC + "\","
                + "\"flags\":4163,\"hardwareType\":1}"
                + "]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig iface = config.getNetworkInterfaces().get(0);
        assertFalse(iface.isLinkLayerBroadcastConfigured());
        assertNull(iface.getLinkLayerBroadcast());
        assertEquals(WLAN_MAC, iface.getMac());
        assertEquals(WLAN_IPV4_BCAST, iface.getBroadcast());

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredBroadcast(emulator, blocks, sink, WLAN_BCAST_PATH);
            assertEquals(0, countNetworkBroadcastReads(sink));
            assertEquals(WLAN_MAC + "\n", readOpenText(emulator, blocks, WLAN_ADDRESS_PATH));
            assertEquals(0, countNetworkBroadcastReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesPriorityOverAutoBroadcast() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"files\":{\"" + WLAN_BCAST_PATH + "\":\"CUSTOM_BCAST\\n\"}},"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"" + WLAN_IPV4 + "\","
                + "\"broadcast\":\"" + WLAN_IPV4_BCAST + "\",\"mac\":\"" + WLAN_MAC + "\","
                + "\"linkLayerBroadcast\":\"" + LL_BCAST_JSON + "\"},"
                + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
                + "\"mac\":\"" + ETH_MAC_JSON + "\",\"linkLayerBroadcast\":\"" + LL_BCAST_JSON + "\"}"
                + "]}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("CUSTOM_BCAST\n", readOpenText(emulator, blocks, WLAN_BCAST_PATH));
            assertEquals(LL_BCAST_TEXT, readOpenText(emulator, blocks, ETH_BCAST_PATH));

            boolean sawLinuxFile = false;
            boolean sawEthBroadcast = false;
            for (CapturedEvent e : sink.events) {
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
                if (isNetworkBroadcastRead(e, WLAN_BCAST_PATH)) {
                    fail("wlan0 broadcast should not emit network_device read when linux.files wins");
                }
                if (isNetworkBroadcastRead(e, ETH_BCAST_PATH)) {
                    sawEthBroadcast = true;
                    assertBroadcastSidecar(e, ETH_BCAST_PATH, "eth0", LL_BCAST_BYTES.length);
                    assertSidecarOmitsAddresses(e);
                }
            }
            assertTrue(sawLinuxFile);
            assertTrue(sawEthBroadcast);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testWriteDirectoryUnknownAndNearPathsNotTakenOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertNotTakenOverAsConfiguredBroadcast(emulator, blocks, sink, UNKNOWN_BCAST_PATH);
            assertNotTakenOverAsConfiguredBroadcast(emulator, blocks, sink, LO_BCAST_PATH);
            assertNotTakenOverAsConfiguredBroadcast(emulator, blocks, sink, ETH_BCAST_PATH);
            assertNotTakenOverAsConfiguredBroadcast(emulator, blocks, sink, WLAN_ADDRESS_PATH);
            assertNotTakenOverAsConfiguredBroadcast(emulator, blocks, sink, WLAN_FLAGS_PATH);
            assertNotTakenOverAsConfiguredBroadcast(emulator, blocks, sink, "/sys/class/net/wlan0");
            assertNotTakenOverAsConfiguredBroadcast(emulator, blocks, sink, WLAN_BCAST_PATH + "/");
            assertNotTakenOverAsConfiguredBroadcast(emulator, blocks, sink, WLAN_BCAST_PATH + "s");
            assertEquals(0, countNetworkBroadcastReads(sink));

            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_BCAST_PATH, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_BCAST_PATH, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_BCAST_PATH, IOConstants.O_DIRECTORY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, WLAN_BCAST_PATH,
                    IOConstants.O_RDONLY | IOConstants.O_DIRECTORY);
            assertEquals(0, countNetworkBroadcastReads(sink));

            assertEquals(LL_BCAST_TEXT, readOpenText(emulator, blocks, WLAN_BCAST_PATH));
            assertEquals(1, countNetworkBroadcastReads(sink));
            CapturedEvent ev = findBroadcastRead(sink, WLAN_BCAST_PATH);
            assertBroadcastSidecar(ev, WLAN_BCAST_PATH, "wlan0", LL_BCAST_BYTES.length);
            assertSidecarOmitsAddresses(ev);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredBroadcast(emulator, blocks, sink, WLAN_BCAST_PATH);
            assertEquals(0, countNetworkBroadcastReads(sink));
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

    private static void assertNotTakenOverAsConfiguredBroadcast(AndroidEmulator emulator,
                                                                List<MemoryBlock> blocks,
                                                                CapturingSink sink,
                                                                String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            boolean exactBroadcast = path.endsWith("/broadcast") && !path.endsWith("/broadcasts")
                    && !path.endsWith("/broadcast/");
            if (exactBroadcast) {
                assertFalse(path + " must not derive from mac", (WLAN_MAC + "\n").equals(text));
                assertFalse(path + " must not derive from IPv4 broadcast",
                        (WLAN_IPV4_BCAST + "\n").equals(text));
            }
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected auto broadcast sidecar for " + path,
                    isNetworkBroadcastRead(e, path));
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
            assertFalse("write/directory open must not serve configured link-layer broadcast",
                    LL_BCAST_TEXT.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isNetworkBroadcastRead(e, path));
        }
    }

    private static void assertBroadcastSidecar(CapturedEvent e, String path, String name, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("name=" + name + ",format=broadcast,bytes=" + bytes, String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains(path));
        assertTrue(e.note.contains("读取"));
    }

    private static void assertSidecarOmitsAddresses(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains(LL_BCAST_CANONICAL));
        assertFalse(value.contains(LL_BCAST_JSON));
        assertFalse(note.contains(LL_BCAST_CANONICAL));
        assertFalse(note.contains(LL_BCAST_JSON));
        assertFalse(value.contains(WLAN_MAC));
        assertFalse(note.contains(WLAN_MAC));
        assertFalse(value.contains(ETH_MAC_JSON));
        assertFalse(value.contains(ETH_MAC_CANONICAL));
        assertFalse(note.contains(ETH_MAC_JSON));
        assertFalse(note.contains(ETH_MAC_CANONICAL));
        assertFalse(value.contains(WLAN_IPV4));
        assertFalse(note.contains(WLAN_IPV4));
        assertFalse(value.contains(WLAN_IPV4_BCAST));
        assertFalse(note.contains(WLAN_IPV4_BCAST));
        assertFalse(value.contains("10.0.0.2"));
        assertFalse(note.contains("10.0.0.2"));
        assertFalse(value.contains("127.0.0.1"));
        assertFalse(note.contains("127.0.0.1"));
        assertFalse(value.contains("hardwareType"));
        assertFalse(note.contains("hardwareType"));
        assertFalse(value.contains("linkLayerBroadcast"));
        assertFalse(note.contains("linkLayerBroadcast"));
        assertFalse(value.contains("format=address"));
        assertFalse(value.contains("format=flags"));
        assertFalse(value.contains("path="));
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

    private static int countNetworkBroadcastReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isNetworkBroadcastRead(e, WLAN_BCAST_PATH)
                    || isNetworkBroadcastRead(e, ETH_BCAST_PATH)
                    || isNetworkBroadcastRead(e, LO_BCAST_PATH)
                    || isNetworkBroadcastRead(e, UNKNOWN_BCAST_PATH)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findBroadcastRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isNetworkBroadcastRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isNetworkBroadcastRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=broadcast");
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
