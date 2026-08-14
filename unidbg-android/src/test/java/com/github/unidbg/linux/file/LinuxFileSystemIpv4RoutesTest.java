package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.ConfiguredIpv4RouteFiles;
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
 * LinuxFileSystem wiring for {@code network.ipv4Routes} → exact
 * {@code /proc/net/route}, {@code /proc/self/net/route}, and
 * {@code /proc/<emulatorPid>/net/route}. Default backend (no Unicorn2).
 * Every emulator is closed in {@code finally} without swallowing {@code close}.
 */
public class LinuxFileSystemIpv4RoutesTest {

    private static final int CONFIG_PID = 4242;
    private static final String PROC_NET_ROUTE = "/proc/net/route";
    private static final String PROC_SELF_NET_ROUTE = "/proc/self/net/route";
    private static final String PROC_PID_NET_ROUTE = "/proc/" + CONFIG_PID + "/net/route";

    private static final String WLAN_IFACE_JSON =
            "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\"}";

    private static final String DEFAULT_ROUTE_JSON =
            "{\"interfaceName\":\"wlan0\",\"destination\":\"0.0.0.0\","
                    + "\"gateway\":\"192.168.50.1\",\"flags\":3,\"refCount\":0,\"use\":0,"
                    + "\"metric\":600,\"mask\":\"0.0.0.0\",\"mtu\":0,\"window\":0,\"irtt\":0}";

    private static final String CONNECTED_ROUTE_JSON =
            "{\"interfaceName\":\"wlan0\",\"destination\":\"192.168.50.0\","
                    + "\"gateway\":\"0.0.0.0\",\"flags\":1,\"refCount\":0,\"use\":0,"
                    + "\"metric\":600,\"mask\":\"255.255.255.0\",\"mtu\":0,\"window\":0,\"irtt\":0}";

    private static final String TWO_ROUTES_JSON = "{"
            + "\"process\":{\"pid\":" + CONFIG_PID + "},"
            + "\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
            + "\"ipv4Routes\":[" + DEFAULT_ROUTE_JSON + "," + CONNECTED_ROUTE_JSON + "]}}";

    private static final String EXPECTED_TABLE =
            ConfiguredIpv4RouteFiles.HEADER
                    + "wlan0\t00000000\t0132A8C0\t0003\t0\t0\t600\t00000000\t0\t0\t0\n"
                    + "wlan0\t0032A8C0\t00000000\t0001\t0\t0\t600\t00FFFFFF\t0\t0\t0\n";

    @Test
    public void testParseValidAndInvalid() {
        TraceEnvironmentConfig two = TraceEnvironmentConfig.parse(TWO_ROUTES_JSON);
        assertTrue(two.isNetworkIpv4RoutesConfigured());
        assertEquals(2, two.getNetworkIpv4Routes().size());
        TraceEnvironmentConfig.NetworkIpv4RouteConfig def = two.getNetworkIpv4Routes().get(0);
        assertEquals("wlan0", def.getInterfaceName());
        assertEquals("0.0.0.0", def.getDestination());
        assertEquals("192.168.50.1", def.getGateway());
        assertEquals(3L, def.getFlags());
        assertEquals(0L, def.getRefCount());
        assertEquals(0L, def.getUse());
        assertEquals(600L, def.getMetric());
        assertEquals("0.0.0.0", def.getMask());
        assertEquals(0L, def.getMtu());
        assertEquals(0L, def.getWindow());
        assertEquals(0L, def.getIrtt());
        TraceEnvironmentConfig.NetworkIpv4RouteConfig connected = two.getNetworkIpv4Routes().get(1);
        assertEquals("192.168.50.0", connected.getDestination());
        assertEquals("0.0.0.0", connected.getGateway());
        assertEquals(1L, connected.getFlags());
        assertEquals("255.255.255.0", connected.getMask());

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{\"network\":{\"ipv4Routes\":[]}}");
        assertTrue(empty.isNetworkIpv4RoutesConfigured());
        assertTrue(empty.getNetworkIpv4Routes().isEmpty());

        assertFalse(TraceEnvironmentConfig.parse("{}").isNetworkIpv4RoutesConfigured());
        assertFalse(TraceEnvironmentConfig.parse("{\"network\":{}}").isNetworkIpv4RoutesConfigured());
        assertFalse(TraceEnvironmentConfig.parse(
                "{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "]}}")
                .isNetworkIpv4RoutesConfigured());

        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"ipv4Routes\":[{\"interfaceName\":\"wlan0\",\"destination\":\"0.0.0.0\","
                + "\"gateway\":\"0.0.0.0\",\"flags\":4294967295,\"refCount\":4294967295,"
                + "\"use\":0,\"metric\":0,\"mask\":\"0.0.0.0\",\"mtu\":0,\"window\":0,\"irtt\":0}]}}");
        assertEquals(4294967295L, max.getNetworkIpv4Routes().get(0).getFlags());
        assertEquals(4294967295L, max.getNetworkIpv4Routes().get(0).getRefCount());

        assertInvalid("{\"network\":{\"ipv4Routes\":{}}}", "network.ipv4Routes");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"ipv4Routes\":[\"wlan0\"]}}", "network.ipv4Routes[0]");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"ipv4Routes\":[1]}}", "network.ipv4Routes[0]");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"ipv4Routes\":[null]}}", "network.ipv4Routes[0]");
        assertInvalid("{\"network\":{\"ipv4Routes\":[" + DEFAULT_ROUTE_JSON + "]}}",
                "network.ipv4Routes[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"ipv4Routes\":[{\"interfaceName\":\"eth0\",\"destination\":\"0.0.0.0\","
                + "\"gateway\":\"0.0.0.0\",\"flags\":1,\"refCount\":0,\"use\":0,\"metric\":0,"
                + "\"mask\":\"0.0.0.0\",\"mtu\":0,\"window\":0,\"irtt\":0}]}}",
                "network.ipv4Routes[0].interfaceName");
        assertInvalid("{\"network\":{\"interfaces\":["
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.50.23\"},"
                + "{\"name\":\"wlan0\",\"index\":3,\"ipv4\":\"10.0.0.2\"}],"
                + "\"ipv4Routes\":[]}}",
                "network.interfaces[1].name");
        assertInvalid(routeWith("\"extra\":1"), "network.ipv4Routes[0].extra");
        assertInvalid(routeMissing("destination"), "network.ipv4Routes[0].destination");
        assertInvalid(routeMissing("flags"), "network.ipv4Routes[0].flags");
        assertInvalid(routeField("flags", "-1"), "network.ipv4Routes[0].flags");
        assertInvalid(routeField("flags", "1.5"), "network.ipv4Routes[0].flags");
        assertInvalid(routeField("flags", "\"3\""), "network.ipv4Routes[0].flags");
        assertInvalid(routeField("flags", "true"), "network.ipv4Routes[0].flags");
        assertInvalid(routeField("flags", "false"), "network.ipv4Routes[0].flags");
        assertInvalid(routeField("flags", "null"), "network.ipv4Routes[0].flags");
        assertInvalid(routeField("flags", "4294967296"), "network.ipv4Routes[0].flags");
        assertInvalid(routeField("metric", "-1"), "network.ipv4Routes[0].metric");
        assertInvalid(routeField("destination", "\"192.168.50\""),
                "network.ipv4Routes[0].destination");
        assertInvalid(routeField("gateway", "true"), "network.ipv4Routes[0].gateway");
        assertInvalid(routeField("mask", "null"), "network.ipv4Routes[0].mask");
        assertInvalid(routeField("interfaceName", "1"), "network.ipv4Routes[0].interfaceName");
    }

    @Test
    public void testFullTableBytesAndThreeAliases() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_ROUTES_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            assertEquals(CONFIG_PID, emulator.getPid());
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] expected = EXPECTED_TABLE.getBytes(StandardCharsets.UTF_8);
            assertEquals(EXPECTED_TABLE, new String(expected, StandardCharsets.UTF_8));
            assertTrue(EXPECTED_TABLE.contains("0132A8C0"));
            assertTrue(EXPECTED_TABLE.contains("0032A8C0"));
            assertTrue(EXPECTED_TABLE.contains("00FFFFFF"));
            assertTrue(EXPECTED_TABLE.endsWith("\n"));

            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_NET_ROUTE));
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_SELF_NET_ROUTE));
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_PID_NET_ROUTE));

            assertEquals(3, countRouteReads(sink));
            CapturedEvent e0 = findRouteRead(sink, PROC_NET_ROUTE);
            assertRouteSidecar(e0, PROC_NET_ROUTE, 2, expected.length);
            assertSidecarRedacted(e0);
            assertRouteSidecar(findRouteRead(sink, PROC_SELF_NET_ROUTE),
                    PROC_SELF_NET_ROUTE, 2, expected.length);
            assertSidecarRedacted(findRouteRead(sink, PROC_SELF_NET_ROUTE));
            assertRouteSidecar(findRouteRead(sink, PROC_PID_NET_ROUTE),
                    PROC_PID_NET_ROUTE, 2, expected.length);
            assertSidecarRedacted(findRouteRead(sink, PROC_PID_NET_ROUTE));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testExplicitEmptyArrayHeaderOnly() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"process\":{\"pid\":" + CONFIG_PID + "},\"network\":{\"ipv4Routes\":[]}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            byte[] expected = ConfiguredIpv4RouteFiles.HEADER.getBytes(StandardCharsets.UTF_8);
            assertArrayEqualsBytes(expected, readOpenBytes(emulator, blocks, PROC_NET_ROUTE));
            assertEquals(ConfiguredIpv4RouteFiles.HEADER,
                    readOpenText(emulator, blocks, PROC_SELF_NET_ROUTE));
            assertTrue(ConfiguredIpv4RouteFiles.HEADER.endsWith("\n"));
            assertFalse(ConfiguredIpv4RouteFiles.HEADER.contains("wlan0"));
            CapturedEvent e = findRouteRead(sink, PROC_NET_ROUTE);
            assertRouteSidecar(e, PROC_NET_ROUTE, 0, expected.length);
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
            assertNotTakenOverAsConfiguredRoute(emulator, blocks, sink, PROC_NET_ROUTE);
            assertNotTakenOverAsConfiguredRoute(emulator, blocks, sink, PROC_SELF_NET_ROUTE);
            assertEquals(0, countRouteReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredRoute(emulator, blocks, sink, PROC_NET_ROUTE);
            assertEquals(0, countRouteReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesTakesPriority() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"files\":{\"" + PROC_NET_ROUTE + "\":\"CUSTOM_ROUTE\\n\"}},"
                + TWO_ROUTES_JSON.substring(TWO_ROUTES_JSON.indexOf("\"network\"")));
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals("CUSTOM_ROUTE\n", readOpenText(emulator, blocks, PROC_NET_ROUTE));
            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                if (isRouteRead(e, PROC_NET_ROUTE)) {
                    fail("network_device route sidecar must not fire when linux.files wins");
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
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TWO_ROUTES_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_ROUTE, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_ROUTE, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_NET_ROUTE, IOConstants.O_DIRECTORY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, PROC_SELF_NET_ROUTE,
                    IOConstants.O_RDONLY | IOConstants.O_DIRECTORY);

            assertNotTakenOverAsConfiguredRoute(emulator, blocks, sink, "/proc/net/routes");
            assertNotTakenOverAsConfiguredRoute(emulator, blocks, sink, "/proc/net/route/");
            assertNotTakenOverAsConfiguredRoute(emulator, blocks, sink, "/proc/net/arp");
            assertNotTakenOverAsConfiguredRoute(emulator, blocks, sink, "/proc/net/dev");
            assertNotTakenOverAsConfiguredRoute(emulator, blocks, sink, "/proc/self/net/routes");
            assertNotTakenOverAsConfiguredRoute(emulator, blocks, sink, "/proc/1/net/route");
            assertNotTakenOverAsConfiguredRoute(emulator, blocks, sink, "/proc/net/Route");
            assertEquals(0, countRouteReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    private static String routeWith(String extraField) {
        return "{"
                + "\"network\":{\"interfaces\":[" + WLAN_IFACE_JSON + "],"
                + "\"ipv4Routes\":[{"
                + "\"interfaceName\":\"wlan0\",\"destination\":\"0.0.0.0\","
                + "\"gateway\":\"0.0.0.0\",\"flags\":1,\"refCount\":0,\"use\":0,"
                + "\"metric\":0,\"mask\":\"0.0.0.0\",\"mtu\":0,\"window\":0,\"irtt\":0,"
                + extraField + "}]}}";
    }

    private static String routeMissing(String field) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"network\":{\"interfaces\":[").append(WLAN_IFACE_JSON).append("],");
        sb.append("\"ipv4Routes\":[{");
        boolean first = true;
        String[] keys = {
                "interfaceName", "destination", "gateway", "flags", "refCount", "use",
                "metric", "mask", "mtu", "window", "irtt"
        };
        String[] values = {
                "\"wlan0\"", "\"0.0.0.0\"", "\"0.0.0.0\"", "1", "0", "0",
                "0", "\"0.0.0.0\"", "0", "0", "0"
        };
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

    private static String routeField(String field, String rawValue) {
        String[] keys = {
                "interfaceName", "destination", "gateway", "flags", "refCount", "use",
                "metric", "mask", "mtu", "window", "irtt"
        };
        String[] values = {
                "\"wlan0\"", "\"0.0.0.0\"", "\"0.0.0.0\"", "1", "0", "0",
                "0", "\"0.0.0.0\"", "0", "0", "0"
        };
        StringBuilder sb = new StringBuilder();
        sb.append("{\"network\":{\"interfaces\":[").append(WLAN_IFACE_JSON).append("],");
        sb.append("\"ipv4Routes\":[{");
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

    private static void assertRouteSidecar(CapturedEvent e, String path, int routeCount, int bytes) {
        assertNotNull(e);
        assertEquals("network_device", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",format=proc-net-route,routeCount=" + routeCount
                + ",bytes=" + bytes, String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains("读取"));
        assertTrue(e.note.contains(path));
    }

    private static void assertSidecarRedacted(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains("destination"));
        assertFalse(note.contains("destination"));
        assertFalse(value.contains("gateway"));
        assertFalse(note.contains("gateway"));
        assertFalse(value.contains("mask"));
        assertFalse(note.contains("mask"));
        assertFalse(value.contains("flags"));
        assertFalse(note.contains("flags"));
        assertFalse(value.contains("wlan0"));
        assertFalse(note.contains("wlan0"));
        assertFalse(value.contains("192.168.50"));
        assertFalse(note.contains("192.168.50"));
        assertFalse(value.contains("0132A8C0"));
        assertFalse(note.contains("0132A8C0"));
        assertFalse(value.contains("0032A8C0"));
        assertFalse(note.contains("0032A8C0"));
        assertFalse(value.contains("00FFFFFF"));
        assertFalse(note.contains("00FFFFFF"));
        assertFalse(value.contains("interfaceName"));
        assertFalse(note.contains("interfaceName"));
        assertFalse(value.contains("255.255.255"));
        assertFalse(note.contains("255.255.255"));
    }

    private static void assertNotTakenOverAsConfiguredRoute(AndroidEmulator emulator,
                                                            List<MemoryBlock> blocks,
                                                            CapturingSink sink,
                                                            String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 4096);
            assertFalse(path + " must not serve configured ipv4Routes table",
                    EXPECTED_TABLE.equals(text));
            assertFalse(path + " must not serve configured ipv4Routes header-only",
                    ConfiguredIpv4RouteFiles.HEADER.equals(text) && path.contains("route"));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected route sidecar for " + path, isRouteRead(e, path));
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
            assertFalse("write/directory open must not serve configured ipv4Routes",
                    EXPECTED_TABLE.equals(text)
                            || ConfiguredIpv4RouteFiles.HEADER.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isRouteRead(e, path));
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

    private static int countRouteReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isRouteRead(e, PROC_NET_ROUTE)
                    || isRouteRead(e, PROC_SELF_NET_ROUTE)
                    || isRouteRead(e, PROC_PID_NET_ROUTE)) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findRouteRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isRouteRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isRouteRead(CapturedEvent e, String path) {
        return "network_device".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api)
                && String.valueOf(e.value).contains("format=proc-net-route");
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
