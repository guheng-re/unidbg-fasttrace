package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.ConfiguredProcFiles;
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
 * LinuxFileSystem wiring for {@code linux.proc.entropyAvail} and
 * {@code linux.proc.randomPoolSize} → exact global
 * {@code /proc/sys/kernel/random/entropy_avail} and
 * {@code /proc/sys/kernel/random/poolsize} (decimal ASCII + LF, read-only).
 * Independent of {@code bootId}/{@code randomUuid}. Tests free malloc blocks
 * and close the emulator so they can mix with other lifecycle tests.
 */
public class LinuxFileSystemRandomPoolConfigTest {

    private static final int ENTROPY_VALUE = 1234567;
    private static final int POOLSIZE_VALUE = 7654321;
    private static final String ENTROPY_TEXT = "1234567\n";
    private static final String POOLSIZE_TEXT = "7654321\n";
    private static final String ENTROPY_PATH = "/proc/sys/kernel/random/entropy_avail";
    private static final String POOLSIZE_PATH = "/proc/sys/kernel/random/poolsize";
    private static final String FILES_OVERRIDE = "CUSTOM_ENTROPY\n";

    @Test
    public void testEntropyAvailOnlyExactLfAndSidecar() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"entropyAvail\":" + ENTROPY_VALUE + "}}}");
        assertTrue(config.getLinuxProcConfig().isEntropyAvailConfigured());
        assertEquals(ENTROPY_VALUE, config.getLinuxProcConfig().getEntropyAvail());
        assertFalse(config.getLinuxProcConfig().isRandomPoolSizeConfigured());
        byte[] rendered = ConfiguredProcFiles.renderEntropyAvail(config);
        assertNotNull(rendered);
        assertEquals(ENTROPY_TEXT, new String(rendered, StandardCharsets.US_ASCII));
        assertNull(ConfiguredProcFiles.renderRandomPoolSize(config));

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] raw = readOpenBytes(emulator, blocks, ENTROPY_PATH);
            assertEquals(ENTROPY_TEXT, new String(raw, StandardCharsets.US_ASCII));
            assertEquals(raw.length, ENTROPY_TEXT.getBytes(StandardCharsets.US_ASCII).length);
            assertEquals('\n', raw[raw.length - 1] & 0xff);

            assertNotTakenOverAsConfigured(emulator, blocks, sink, POOLSIZE_PATH,
                    POOLSIZE_TEXT, "random_poolsize");

            assertEquals(1, sink.events.size());
            assertRandomPoolSidecar(sink.events.get(0), ENTROPY_PATH, "entropy_avail",
                    raw.length);
            assertSidecarOmitsConfiguredNumbers(sink.events.get(0));
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

    @Test
    public void testRandomPoolSizeOnlyExactLfAndSidecar() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"randomPoolSize\":" + POOLSIZE_VALUE + "}}}");
        assertTrue(config.getLinuxProcConfig().isRandomPoolSizeConfigured());
        assertEquals(POOLSIZE_VALUE, config.getLinuxProcConfig().getRandomPoolSize());
        assertFalse(config.getLinuxProcConfig().isEntropyAvailConfigured());
        byte[] rendered = ConfiguredProcFiles.renderRandomPoolSize(config);
        assertNotNull(rendered);
        assertEquals(POOLSIZE_TEXT, new String(rendered, StandardCharsets.US_ASCII));
        assertNull(ConfiguredProcFiles.renderEntropyAvail(config));

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] raw = readOpenBytes(emulator, blocks, POOLSIZE_PATH);
            assertEquals(POOLSIZE_TEXT, new String(raw, StandardCharsets.US_ASCII));
            assertEquals('\n', raw[raw.length - 1] & 0xff);

            assertNotTakenOverAsConfigured(emulator, blocks, sink, ENTROPY_PATH,
                    ENTROPY_TEXT, "entropy_avail");

            assertEquals(1, sink.events.size());
            assertRandomPoolSidecar(sink.events.get(0), POOLSIZE_PATH, "random_poolsize",
                    raw.length);
            assertSidecarOmitsConfiguredNumbers(sink.events.get(0));
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

    @Test
    public void testBothFieldsIndependentExactLfAndSidecar() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"entropyAvail\":" + ENTROPY_VALUE + ","
                + "\"randomPoolSize\":" + POOLSIZE_VALUE
                + "}}}"
        );
        assertTrue(config.getLinuxProcConfig().isEntropyAvailConfigured());
        assertTrue(config.getLinuxProcConfig().isRandomPoolSizeConfigured());
        assertEquals(ENTROPY_VALUE, config.getLinuxProcConfig().getEntropyAvail());
        assertEquals(POOLSIZE_VALUE, config.getLinuxProcConfig().getRandomPoolSize());

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] entropyRaw = readOpenBytes(emulator, blocks, ENTROPY_PATH);
            byte[] poolRaw = readOpenBytes(emulator, blocks, POOLSIZE_PATH);
            assertEquals(ENTROPY_TEXT, new String(entropyRaw, StandardCharsets.US_ASCII));
            assertEquals(POOLSIZE_TEXT, new String(poolRaw, StandardCharsets.US_ASCII));
            assertEquals('\n', entropyRaw[entropyRaw.length - 1] & 0xff);
            assertEquals('\n', poolRaw[poolRaw.length - 1] & 0xff);

            assertEquals(2, sink.events.size());
            assertRandomPoolSidecar(sink.events.get(0), ENTROPY_PATH, "entropy_avail",
                    entropyRaw.length);
            assertRandomPoolSidecar(sink.events.get(1), POOLSIZE_PATH, "random_poolsize",
                    poolRaw.length);
            assertSidecarOmitsConfiguredNumbers(sink.events.get(0));
            assertSidecarOmitsConfiguredNumbers(sink.events.get(1));
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

    @Test
    public void testMissingDoesNotTakeOver() throws Exception {
        TraceEnvironmentConfig emptyProc = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{}}}");
        assertFalse(emptyProc.getLinuxProcConfig().isEntropyAvailConfigured());
        assertFalse(emptyProc.getLinuxProcConfig().isRandomPoolSizeConfigured());
        assertNull(ConfiguredProcFiles.renderEntropyAvail(emptyProc));
        assertNull(ConfiguredProcFiles.renderRandomPoolSize(emptyProc));
        assertNull(ConfiguredProcFiles.renderEntropyAvail(null));
        assertNull(ConfiguredProcFiles.renderRandomPoolSize(null));

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(emptyProc).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfigured(emulator, blocks, sink, ENTROPY_PATH,
                    ENTROPY_TEXT, "entropy_avail");
            assertNotTakenOverAsConfigured(emulator, blocks, sink, POOLSIZE_PATH,
                    POOLSIZE_TEXT, "random_poolsize");
            for (CapturedEvent e : sink.events) {
                assertFalse(isRandomPoolLinuxProc(e));
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

        TraceEnvironmentConfig bootOnly = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{\"bootId\":\"a1b2c3d4-e5f6-7890-abcd-ef1234567890\"}}"
                + "}");
        sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(bootOnly).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfigured(emulator, blocks, sink, ENTROPY_PATH,
                    ENTROPY_TEXT, "entropy_avail");
            assertNotTakenOverAsConfigured(emulator, blocks, sink, POOLSIZE_PATH,
                    POOLSIZE_TEXT, "random_poolsize");
            for (CapturedEvent e : sink.events) {
                assertFalse(isRandomPoolLinuxProc(e));
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

    @Test
    public void testLinuxFilesPriorityNoLinuxProcEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{"
                + "\"files\":{\"" + ENTROPY_PATH + "\":\"" + FILES_OVERRIDE.replace("\n", "\\n") + "\"},"
                + "\"proc\":{"
                + "\"entropyAvail\":" + ENTROPY_VALUE + ","
                + "\"randomPoolSize\":" + POOLSIZE_VALUE
                + "}"
                + "}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals(FILES_OVERRIDE, readOpenText(emulator, blocks, ENTROPY_PATH));
            assertEquals(POOLSIZE_TEXT, readOpenText(emulator, blocks, POOLSIZE_PATH));

            boolean sawLinuxFile = false;
            boolean sawPoolProc = false;
            for (CapturedEvent e : sink.events) {
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                    assertEquals("json-config", e.source);
                }
                if ("linux_proc".equals(e.kind)
                        && String.valueOf(e.value).contains("format=entropy_avail")) {
                    fail("linux.files hit must not emit entropy_avail linux_proc");
                }
                if ("linux_proc".equals(e.kind)
                        && String.valueOf(e.value).contains("format=random_poolsize")) {
                    sawPoolProc = true;
                }
            }
            assertTrue(sawLinuxFile);
            assertTrue(sawPoolProc);
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

    @Test
    public void testWriteDirectoryNearbyNotTakenOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"entropyAvail\":" + ENTROPY_VALUE + ","
                + "\"randomPoolSize\":" + POOLSIZE_VALUE
                + "}}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertNotTakenOverAsConfigured(emulator, blocks, sink, ENTROPY_PATH + "/",
                    ENTROPY_TEXT, "entropy_avail");
            assertNotTakenOverAsConfigured(emulator, blocks, sink, POOLSIZE_PATH + "/",
                    POOLSIZE_TEXT, "random_poolsize");
            assertNotTakenOverAsConfigured(emulator, blocks, sink,
                    "/proc/sys/kernel/random/entropy_available", ENTROPY_TEXT, "entropy_avail");
            assertNotTakenOverAsConfigured(emulator, blocks, sink,
                    "/proc/sys/kernel/random/pool_size", POOLSIZE_TEXT, "random_poolsize");
            assertNotTakenOverAsConfigured(emulator, blocks, sink,
                    "/proc/sys/kernel/random", ENTROPY_TEXT, "entropy_avail");
            assertNotTakenOverAsConfigured(emulator, blocks, sink,
                    "/proc/sys/kernel/random/uuid", ENTROPY_TEXT, "entropy_avail");

            assertWriteOpenNotTakenOver(emulator, blocks, sink, ENTROPY_PATH,
                    IOConstants.O_WRONLY, ENTROPY_TEXT);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, ENTROPY_PATH,
                    IOConstants.O_RDWR, ENTROPY_TEXT);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, ENTROPY_PATH,
                    IOConstants.O_DIRECTORY, ENTROPY_TEXT);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, POOLSIZE_PATH,
                    IOConstants.O_WRONLY, POOLSIZE_TEXT);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, POOLSIZE_PATH,
                    IOConstants.O_RDWR, POOLSIZE_TEXT);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, POOLSIZE_PATH,
                    IOConstants.O_DIRECTORY, POOLSIZE_TEXT);

            for (CapturedEvent e : sink.events) {
                assertFalse(isRandomPoolLinuxProc(e));
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

    @Test
    public void testInvalidAndValidParse() {
        TraceEnvironmentConfig zero = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"entropyAvail\":0}}}");
        assertTrue(zero.getLinuxProcConfig().isEntropyAvailConfigured());
        assertEquals(0, zero.getLinuxProcConfig().getEntropyAvail());
        assertFalse(zero.getLinuxProcConfig().isRandomPoolSizeConfigured());
        assertEquals("0\n", new String(ConfiguredProcFiles.renderEntropyAvail(zero),
                StandardCharsets.US_ASCII));

        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"randomPoolSize\":2147483647}}}");
        assertTrue(max.getLinuxProcConfig().isRandomPoolSizeConfigured());
        assertEquals(2147483647, max.getLinuxProcConfig().getRandomPoolSize());
        assertFalse(max.getLinuxProcConfig().isEntropyAvailConfigured());
        assertEquals("2147483647\n", new String(ConfiguredProcFiles.renderRandomPoolSize(max),
                StandardCharsets.US_ASCII));

        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{}}}");
        assertFalse(missing.getLinuxProcConfig().isEntropyAvailConfigured());
        assertFalse(missing.getLinuxProcConfig().isRandomPoolSizeConfigured());

        String[] fields = {"entropyAvail", "randomPoolSize"};
        for (int i = 0; i < fields.length; i++) {
            String field = fields[i];
            String path = "linux.proc." + field;
            assertInvalid("{\"linux\":{\"proc\":{\"" + field + "\":1.5}}}", path);
            assertInvalid("{\"linux\":{\"proc\":{\"" + field + "\":\"0\"}}}", path);
            assertInvalid("{\"linux\":{\"proc\":{\"" + field + "\":true}}}", path);
            assertInvalid("{\"linux\":{\"proc\":{\"" + field + "\":false}}}", path);
            assertInvalid("{\"linux\":{\"proc\":{\"" + field + "\":null}}}", path);
            assertInvalid("{\"linux\":{\"proc\":{\"" + field + "\":-1}}}", path);
            assertInvalid("{\"linux\":{\"proc\":{\"" + field + "\":2147483648}}}", path);
            assertInvalid("{\"linux\":{\"proc\":{\"" + field + "\":{}}}}", path);
            assertInvalid("{\"linux\":{\"proc\":{\"" + field + "\":[]}}}", path);
        }
        assertInvalid("{\"linux\":{\"proc\":{\"entropy_avail\":1}}}", "linux.proc.entropy_avail");
        assertInvalid("{\"linux\":{\"proc\":{\"poolsize\":1}}}", "linux.proc.poolsize");
        assertInvalid("{\"linux\":{\"proc\":{\"entropyAvailExtra\":1}}}",
                "linux.proc.entropyAvailExtra");
    }

    private static void assertRandomPoolSidecar(CapturedEvent e, String path, String format,
                                                int bytes) {
        assertNotNull(e);
        assertEquals("linux_proc", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",format=" + format + ",bytes=" + bytes,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains("读取配置的随机池状态"));
        assertTrue(e.note.contains(path));
    }

    private static void assertSidecarOmitsConfiguredNumbers(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains(String.valueOf(ENTROPY_VALUE)));
        assertFalse(value.contains(String.valueOf(POOLSIZE_VALUE)));
        assertFalse(note.contains(String.valueOf(ENTROPY_VALUE)));
        assertFalse(note.contains(String.valueOf(POOLSIZE_VALUE)));
        assertFalse(value.contains(ENTROPY_TEXT.trim()));
        assertFalse(value.contains(POOLSIZE_TEXT.trim()));
    }

    private static boolean isRandomPoolLinuxProc(CapturedEvent e) {
        String value = String.valueOf(e.value);
        return "linux_proc".equals(e.kind)
                && (value.contains("format=entropy_avail")
                || value.contains("format=random_poolsize"));
    }

    private static void assertNotTakenOverAsConfigured(AndroidEmulator emulator,
                                                       List<MemoryBlock> blocks,
                                                       CapturingSink sink,
                                                       String path, String configuredText,
                                                       String format) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            assertFalse(path + " must not serve configured content", configuredText.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected sidecar for " + path,
                    "linux_proc".equals(e.kind)
                            && ("read(\"" + path + "\")").equals(e.api)
                            && String.valueOf(e.value).contains("format=" + format));
        }
    }

    private static void assertWriteOpenNotTakenOver(AndroidEmulator emulator,
                                                    List<MemoryBlock> blocks,
                                                    CapturingSink sink,
                                                    String path, int oflags,
                                                    String configuredText) throws Exception {
        FileResult<AndroidFileIO> result;
        try {
            result = emulator.getFileSystem().open(path, oflags);
        } catch (RuntimeException ignored) {
            return;
        }
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            assertFalse("write/directory open must not serve configured random pool",
                    configuredText.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isRandomPoolLinuxProc(e));
        }
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
        return new String(readOpenBytes(emulator, blocks, path), StandardCharsets.US_ASCII);
    }

    private static byte[] readOpenBytes(AndroidEmulator emulator, List<MemoryBlock> blocks,
                                        String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        assertNotNull(result);
        assertTrue("open failed for " + path, result.isSuccess());
        assertNotNull(result.io);
        assertTrue(result.io instanceof ByteArrayFileIO);
        return readIoBytes(emulator, blocks, result.io, 64);
    }

    private static String readIoText(AndroidEmulator emulator, List<MemoryBlock> blocks,
                                     AndroidFileIO io, int max) throws Exception {
        return new String(readIoBytes(emulator, blocks, io, max), StandardCharsets.US_ASCII);
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
