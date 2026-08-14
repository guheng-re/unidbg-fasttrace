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
 * LinuxFileSystem wiring for {@code network.interfaces[].operState} → exact
 * {@code /sys/class/net/<name>/operstate} (UTF-8 lowercase IF_OPER_* token + LF).
 * Uses the default backend (no Unicorn2) so emulator close does not hit Unicorn2
 * lifecycle crashes. Never infers operstate from flags or wifi.
 */
public class LinuxFileSystemNetworkOperstateTest {

    private static final String WLAN_OPERSTATE_PATH = "/sys/class/net/wlan0/operstate";
    private static final String ETH_OPERSTATE_PATH = "/sys/class/net/eth0/operstate";
    private static final String LO_OPERSTATE_PATH = "/sys/class/net/lo/operstate";
    private static final String UNKNOWN_OPERSTATE_PATH = "/sys/class/net/not0/operstate";
    private static final String WLAN_FLAGS_PATH = "/sys/class/net/wlan0/flags";
    private static final String WLAN_ADDRESS_PATH = "/sys/class/net/wlan0/address";
    private static final String WLAN_MTU_PATH = "/sys/class/net/wlan0/mtu";
    private static final String WLAN_IFINDEX_PATH = "/sys/class/net/wlan0/ifindex";

    private static final String WLAN_MAC = "02:00:00:00:00:01";
    private static final String ETH_MAC_JSON = "AA:BB:CC:DD:EE:FF";
    private static final String ETH_MAC_CANONICAL = "aa:bb:cc:dd:ee:ff";

    private static final int LO_FLAGS = 73;
    private static final int ETH_FLAGS = 4163;
    private static final int WLAN_FLAGS = 1;
    private static final int WLAN_MTU = 1500;

    private static final String LO_OPERSTATE = "down";
    private static final String ETH_OPERSTATE = "up";
    private static final String LO_OPERSTATE_TEXT = LO_OPERSTATE + "\n";
    private static final String ETH_OPERSTATE_TEXT = ETH_OPERSTATE + "\n";

    private static final String[] OPER_STATES = {
            "unknown", "notpresent", "down", "lowerlayerdown", "testing", "dormant", "up"
    };

    private static final String TWO_IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"flags\":" + LO_FLAGS
            + ",\"operState\":\"" + LO_OPERSTATE + "\"},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
            + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU + ",\"flags\":" + WLAN_FLAGS + "},"
            + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
            + "\"mac\":\"" + ETH_MAC_JSON + "\",\"flags\":" + ETH_FLAGS
            + ",\"operState\":\"" + ETH_OPERSTATE + "\"}"
            + "]}}"
            ;

    @Test
    public void testOperStateParseValidAndInvalid() {
        for (int i = 0; i < OPER_STATES.length; i++) {
            String state = OPER_STATES[i];
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                    + "\"network\":{\"interfaces\":["
                    + "{\"name\":\"if" + i + "\",\"index\":" + (i + 1)
                    + ",\"ipv4\":\"10.0.0." + (i + 1) + "\",\"operState\":\"" + state + "\"}"
                    + "]}}"
            );
            TraceEnvironmentConfig.NetworkInterfaceConfig iface =
                    config.getNetworkInterfaces().get(0);
            assertTrue(state, iface.isOperStateConfigured());
            assertEquals(state, iface.getOperState());
        }

        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        TraceEnvironmentConfig.NetworkInterfaceConfig lo = omitted.getNetworkInterfaces().get(0);
        assertTrue(lo.isOperStateConfigured());
        assertEquals(LO_OPERSTATE, lo.getOperState());
        TraceEnvironmentConfig.NetworkInterfaceConfig wlan0 = omitted.getNetworkInterfaces().get(1);
        assertFalse(wlan0.isOperStateConfigured());
        assertNull(wlan0.getOperState());
        assertEquals(Integer.valueOf(WLAN_FLAGS), wlan0.getFlags());
        TraceEnvironmentConfig.NetworkInterfaceConfig eth0 = omitted.getNetworkInterfaces().get(2);
        assertTrue(eth0.isOperStateConfigured());
        assertEquals(ETH_OPERSTATE, eth0.getOperState());
        assertEquals(Integer.valueOf(ETH_FLAGS), eth0.getFlags());

        TraceEnvironmentConfig flagsOnly = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"wifi\":{\"enabled\":true},"
                + "\"interfaces\":[{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.1\","
                + "\"flags\":1}]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig flagsIface =
                flagsOnly.getNetworkInterfaces().get(0);
        assertFalse(flagsIface.isOperStateConfigured());
        assertNull(flagsIface.getOperState());
        assertEquals(Integer.valueOf(1), flagsIface.getFlags());

        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"operState\":null}]}}",
                "network.interfaces[0].operState");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"operState\":1}]}}",
                "network.interfaces[0].operState");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"operState\":true}]}}",
                "network.interfaces[0].operState");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"operState\":{}}]}}",
                "network.interfaces[0].operState");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"operState\":[]}]}}",
                "network.interfaces[0].operState");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"operState\":\"UP\"}]}}",
                "network.interfaces[0].operState");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"operState\":\"Up\"}]}}",
                "network.interfaces[0].operState");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"operState\":\"up \"}]}}",
                "network.interfaces[0].operState");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"operState\":\"\"}]}}",
                "network.interfaces[0].operState");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"operState\":\"running\"}]}}",
                "network.interfaces[0].operState");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"operState\":\"UNKNOWN\"}]}}",
                "network.interfaces[0].operState");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"operState\":\"lowerLayerDown\"}]}}",
                "network.interfaces[0].operState");
    }

    @Test
    public void testConfiguredOperstateOpenAndSidecar64() throws Exception {
        runConfigured(true);
    }

    @Test
    public void testConfiguredOperstateOpenAndSidecar32() throws Exception {
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

            String ethText = readOpenText(emulator, blocks, ETH_OPERSTATE_PATH);
            assertEquals(ETH_OPERSTATE_TEXT, ethText);
            assertTrue(ethText.endsWith("\n"));
            assertEquals(1, countNewlines(ethText));

            String loText = readOpenText(emulator, blocks, LO_OPERSTATE_PATH);
            assertEquals(LO_OPERSTATE_TEXT, loText);
            assertTrue(loText.endsWith("\n"));
            assertEquals(1, countNewlines(loText));

            assertEquals(2, countNetworkOperstateReads(sink));
            CapturedEvent ethEv = findOperstateRead(sink, ETH_OPERSTATE_PATH);
            CapturedEvent loEv = findOperstateRead(sink, LO_OPERSTATE_PATH);
            assertOperstateSidecar(ethEv, ETH_OPERSTATE_PATH, "eth0",
                    ETH_OPERSTATE_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertOperstateSidecar(loEv, LO_OPERSTATE_PATH, "lo",
                    LO_OPERSTATE_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsOperstateValueAndUnrelated(ethEv);
            assertSidecarOmitsOperstateValueAndUnrelated(loEv);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesPriorityOverAutoOperstate() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"files\":{\"" + WLAN_OPERSTATE_PATH + "\":\"custom-oper\\n\"}},"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
                + "\"mac\":\"" + WLAN_MAC + "\",\"mtu\":" + WLAN_MTU
                + ",\"operState\":\"" + ETH_OPERSTATE + "\"},"
                + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
                + "\"mac\":\"" + ETH_MAC_JSON + "\",\"operState\":\"" + ETH_OPERSTATE + "\"}"
                + "]}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("custom-oper\n", readOpenText(emulator, blocks, WLAN_OPERSTATE_PATH));
            assertEquals(ETH_OPERSTATE_TEXT, readOpenText(emulator, blocks, ETH_OPERSTATE_PATH));

            boolean sawLinuxFile = false;
            boolean sawEthOperstate = false;
            for (CapturedEvent e : sink.events) {
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
                if (isNetworkOperstateRead(e, WLAN_OPERSTATE_PATH)) {
                    fail("wlan0 operstate should not emit network_device read when linux.files wins");
                }
                if (isNetworkOperstateRead(e, ETH_OPERSTATE_PATH)) {
                    sawEthOperstate = true;
                    assertSidecarOmitsOperstateValueAndUnrelated(e);
                }
            }
            assertTrue(sawLinuxFile);
            assertTrue(sawEthOperstate);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testUnknownMissingOperstateWriteDirectoryAndOtherSysPathsNotTakenOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_IFACES_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertNotTakenOverAsConfiguredOperstate(emulator, blocks, sink, UNKNOWN_OPERSTATE_PATH);
            assertNotTakenOverAsConfiguredOperstate(emulator, blocks, sink, WLAN_OPERSTATE_PATH);
            assertNotTakenOverAsConfiguredOperstate(emulator, blocks, sink, WLAN_FLAGS_PATH);
            assertNotTakenOverAsConfiguredOperstate(emulator, blocks, sink, WLAN_ADDRESS_PATH);
            assertNotTakenOverAsConfiguredOperstate(emulator, blocks, sink, WLAN_MTU_PATH);
            assertNotTakenOverAsConfiguredOperstate(emulator, blocks, sink, WLAN_IFINDEX_PATH);
            assertNotTakenOverAsConfiguredOperstate(emulator, blocks, sink, "/sys/class/net/wlan0");
            assertNotTakenOverAsConfiguredOperstate(emulator, blocks, sink, WLAN_OPERSTATE_PATH + "/");
            assertEquals(WLAN_MAC + "\n", readOpenText(emulator, blocks, WLAN_ADDRESS_PATH));
            assertEquals(WLAN_MTU + "\n", readOpenText(emulator, blocks, WLAN_MTU_PATH));
            assertEquals("2\n", readOpenText(emulator, blocks, WLAN_IFINDEX_PATH));
            assertEquals(0, countNetworkOperstateReads(sink));

            assertWriteOpenNotTakenOver(emulator, blocks, sink, ETH_OPERSTATE_PATH, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, ETH_OPERSTATE_PATH, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, ETH_OPERSTATE_PATH, IOConstants.O_DIRECTORY);
            assertEquals(0, countNetworkOperstateReads(sink));

            assertEquals(ETH_OPERSTATE_TEXT, readOpenText(emulator, blocks, ETH_OPERSTATE_PATH));
            assertEquals(1, countNetworkOperstateReads(sink));
            CapturedEvent ethEv = findOperstateRead(sink, ETH_OPERSTATE_PATH);
            assertOperstateSidecar(ethEv, ETH_OPERSTATE_PATH, "eth0",
                    ETH_OPERSTATE_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsOperstateValueAndUnrelated(ethEv);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(true).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredOperstate(emulator, blocks, sink, ETH_OPERSTATE_PATH);
            assertEquals(0, countNetworkOperstateReads(sink));
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

    private static void assertNotTakenOverAsConfiguredOperstate(AndroidEmulator emulator,
                                                                List<MemoryBlock> blocks,
                                                                CapturingSink sink,
                                                                String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            assertFalse(path + " must not serve configured eth operstate", ETH_OPERSTATE_TEXT.equals(text));
            assertFalse(path + " must not serve configured lo operstate", LO_OPERSTATE_TEXT.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected auto operstate sidecar for " + path,
                    isNetworkOperstateRead(e, path));
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
            assertFalse("write/directory open must not serve configured operstate",
                    ETH_OPERSTATE_TEXT.equals(text) || LO_OPERSTATE_TEXT.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isNetworkOperstateRead(e, path));
        }
    }

    private static void assertOperstateSidecar(CapturedEvent e, String path, String name, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",name=" + name + ",format=operstate,bytes=" + bytes,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains(path));
        assertTrue(e.note.contains("读取"));
    }

    private static void assertSidecarOmitsOperstateValueAndUnrelated(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
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
        assertFalse(value.contains(ETH_OPERSTATE_TEXT.trim()));
        assertFalse(value.contains(LO_OPERSTATE_TEXT.trim()));
        assertFalse(note.contains(ETH_OPERSTATE_TEXT.trim()));
        assertFalse(note.contains(LO_OPERSTATE_TEXT.trim()));
        assertFalse(value.contains("format=address"));
        assertFalse(value.contains("format=mtu"));
        assertFalse(value.contains("format=ifindex"));
        assertFalse(value.contains("format=flags"));
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

    private static int countNetworkOperstateReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isNetworkOperstateRead(e, WLAN_OPERSTATE_PATH)
                    || isNetworkOperstateRead(e, ETH_OPERSTATE_PATH)
                    || isNetworkOperstateRead(e, LO_OPERSTATE_PATH)
                    || isNetworkOperstateRead(e, UNKNOWN_OPERSTATE_PATH)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findOperstateRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isNetworkOperstateRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isNetworkOperstateRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=operstate");
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
