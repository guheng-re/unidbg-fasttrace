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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * LinuxFileSystem wiring for {@code linux.cpu} online/offline/present/possible sysfs CPU-lists.
 */
public class LinuxFileSystemCpuListTest {

    private static final String ONLINE = "/sys/devices/system/cpu/online";
    private static final String OFFLINE = "/sys/devices/system/cpu/offline";
    private static final String PRESENT = "/sys/devices/system/cpu/present";
    private static final String POSSIBLE = "/sys/devices/system/cpu/possible";

    private static final String FULL_CPU_JSON = "{"
            + "\"linux\":{\"cpu\":{"
            + "\"online\":\"0-3,8\","
            + "\"offline\":\"4-7\","
            + "\"present\":\"0-7\","
            + "\"possible\":\"0-7\""
            + "}}}"
            ;

    @Test
    public void testCpuListsConfiguredOpenAndSidecar64() throws Exception {
        runConfigured(true);
    }

    @Test
    public void testCpuListsConfiguredOpenAndSidecar32() throws Exception {
        runConfigured(false);
    }

    private static void runConfigured(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_CPU_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("0-3,8\n", readOpenText(emulator, blocks, ONLINE));
            assertEquals("4-7\n", readOpenText(emulator, blocks, OFFLINE));
            assertEquals("0-7\n", readOpenText(emulator, blocks, PRESENT));
            assertEquals("0-7\n", readOpenText(emulator, blocks, POSSIBLE));

            assertEquals(4, sink.events.size());
            assertCpuSidecar(sink.events.get(0), ONLINE, "online",
                    "0-3,8\n".getBytes(StandardCharsets.UTF_8).length);
            assertCpuSidecar(sink.events.get(1), OFFLINE, "offline",
                    "4-7\n".getBytes(StandardCharsets.UTF_8).length);
            assertCpuSidecar(sink.events.get(2), PRESENT, "present",
                    "0-7\n".getBytes(StandardCharsets.UTF_8).length);
            assertCpuSidecar(sink.events.get(3), POSSIBLE, "possible",
                    "0-7\n".getBytes(StandardCharsets.UTF_8).length);
            for (CapturedEvent e : sink.events) {
                assertFalse(String.valueOf(e.value).contains("0-3,8"));
                assertFalse(String.valueOf(e.value).contains("0-7"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void assertCpuSidecar(CapturedEvent e, String path, String format, int bytes) {
        assertEquals("linux_cpu", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",format=" + format + ",bytes=" + bytes,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains(path));
    }

    @Test
    public void testCpuListsAbsentAndIndependent() throws Exception {
        // empty cpu node: no auto lists
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{\"linux\":{\"cpu\":{}}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(empty).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            for (String path : new String[]{ONLINE, OFFLINE, PRESENT, POSSIBLE}) {
                FileResult<AndroidFileIO> result = emulator.getFileSystem()
                        .open(path, IOConstants.O_RDONLY);
                if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
                    String text = readIoText(emulator, blocks, result.io, 64);
                    assertFalse(text.startsWith("0-3,8"));
                }
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_cpu".equals(e.kind)
                        && String.valueOf(e.api).startsWith("read("));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }

        // only present configured
        TraceEnvironmentConfig presentOnly = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"cpu\":{\"present\":\"0-3\"}}}");
        sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(presentOnly).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals("0-3\n", readOpenText(emulator, blocks, PRESENT));
            // online not configured → no automatic list event
            FileResult<AndroidFileIO> online = emulator.getFileSystem()
                    .open(ONLINE, IOConstants.O_RDONLY);
            if (online != null && online.isSuccess() && online.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, online.io, 64);
                // legacy content may exist; must not be from our configured present-only path as online
                assertFalse("0-3\n".equals(text) && sink.events.size() > 1);
            }
            assertEquals(1, sink.events.size());
            assertEquals("present", formatFromValue(sink.events.get(0)));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static String formatFromValue(CapturedEvent e) {
        String v = String.valueOf(e.value);
        int i = v.indexOf("format=");
        if (i < 0) {
            return null;
        }
        int start = i + "format=".length();
        int end = v.indexOf(',', start);
        return end < 0 ? v.substring(start) : v.substring(start, end);
    }

    @Test
    public void testCpuListsLinuxFilesPriorityNoLinuxCpuReadEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{"
                + "\"files\":{\"" + PRESENT + "\":\"CUSTOM_PRESENT\\n\"},"
                + "\"cpu\":{"
                + "\"present\":\"0-7\","
                + "\"online\":\"0-3\""
                + "}"
                + "}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("CUSTOM_PRESENT\n", readOpenText(emulator, blocks, PRESENT));
            assertEquals("0-3\n", readOpenText(emulator, blocks, ONLINE));

            boolean sawLinuxFile = false;
            boolean sawOnlineCpu = false;
            for (CapturedEvent e : sink.events) {
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
                if ("linux_cpu".equals(e.kind)
                        && String.valueOf(e.value).contains("format=present")) {
                    fail("present should not emit linux_cpu read when linux.files wins");
                }
                if ("linux_cpu".equals(e.kind)
                        && String.valueOf(e.value).contains("format=online")) {
                    sawOnlineCpu = true;
                }
            }
            assertTrue(sawLinuxFile);
            assertTrue(sawOnlineCpu);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
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
