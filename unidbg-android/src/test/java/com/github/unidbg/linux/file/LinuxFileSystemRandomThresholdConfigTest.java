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
 * LinuxFileSystem wiring for {@code linux.proc.writeWakeupThreshold} and
 * {@code linux.proc.urandomMinReseedSecs} → exact global
 * {@code /proc/sys/kernel/random/write_wakeup_threshold} and
 * {@code /proc/sys/kernel/random/urandom_min_reseed_secs}
 * (decimal ASCII + LF, read-only). Independent of
 * {@code entropyAvail}/{@code randomPoolSize}/{@code bootId}/{@code randomUuid}.
 * Tests free malloc blocks and close the emulator so they can mix with other
 * lifecycle tests.
 */
public class LinuxFileSystemRandomThresholdConfigTest {

    private static final int WAKEUP_VALUE = 2345678;
    private static final int RESEED_VALUE = 8765432;
    private static final String WAKEUP_TEXT = "2345678\n";
    private static final String RESEED_TEXT = "8765432\n";
    private static final String WAKEUP_PATH =
            "/proc/sys/kernel/random/write_wakeup_threshold";
    private static final String RESEED_PATH =
            "/proc/sys/kernel/random/urandom_min_reseed_secs";
    private static final String FILES_OVERRIDE = "CUSTOM_WAKEUP\n";

    @Test
    public void testWriteWakeupThresholdOnlyExactLfAndSidecar() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"writeWakeupThreshold\":" + WAKEUP_VALUE + "}}}");
        assertTrue(config.getLinuxProcConfig().isWriteWakeupThresholdConfigured());
        assertEquals(WAKEUP_VALUE, config.getLinuxProcConfig().getWriteWakeupThreshold());
        assertFalse(config.getLinuxProcConfig().isUrandomMinReseedSecsConfigured());
        assertFalse(config.getLinuxProcConfig().isEntropyAvailConfigured());
        assertFalse(config.getLinuxProcConfig().isRandomPoolSizeConfigured());
        byte[] rendered = ConfiguredProcFiles.renderWriteWakeupThreshold(config);
        assertNotNull(rendered);
        assertEquals(WAKEUP_TEXT, new String(rendered, StandardCharsets.US_ASCII));
        assertNull(ConfiguredProcFiles.renderUrandomMinReseedSecs(config));
        assertNull(ConfiguredProcFiles.renderEntropyAvail(config));
        assertNull(ConfiguredProcFiles.renderRandomPoolSize(config));

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] raw = readOpenBytes(emulator, blocks, WAKEUP_PATH);
            assertEquals(WAKEUP_TEXT, new String(raw, StandardCharsets.US_ASCII));
            assertEquals(raw.length, WAKEUP_TEXT.getBytes(StandardCharsets.US_ASCII).length);
            assertEquals('\n', raw[raw.length - 1] & 0xff);

            assertNotTakenOverAsConfigured(emulator, blocks, sink, RESEED_PATH,
                    RESEED_TEXT, "urandom_min_reseed_secs");

            assertEquals(1, sink.events.size());
            assertRandomThresholdSidecar(sink.events.get(0), WAKEUP_PATH,
                    "write_wakeup_threshold", raw.length);
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
    public void testUrandomMinReseedSecsOnlyExactLfAndSidecar() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"urandomMinReseedSecs\":" + RESEED_VALUE + "}}}");
        assertTrue(config.getLinuxProcConfig().isUrandomMinReseedSecsConfigured());
        assertEquals(RESEED_VALUE, config.getLinuxProcConfig().getUrandomMinReseedSecs());
        assertFalse(config.getLinuxProcConfig().isWriteWakeupThresholdConfigured());
        assertFalse(config.getLinuxProcConfig().isEntropyAvailConfigured());
        assertFalse(config.getLinuxProcConfig().isRandomPoolSizeConfigured());
        byte[] rendered = ConfiguredProcFiles.renderUrandomMinReseedSecs(config);
        assertNotNull(rendered);
        assertEquals(RESEED_TEXT, new String(rendered, StandardCharsets.US_ASCII));
        assertNull(ConfiguredProcFiles.renderWriteWakeupThreshold(config));
        assertNull(ConfiguredProcFiles.renderEntropyAvail(config));
        assertNull(ConfiguredProcFiles.renderRandomPoolSize(config));

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] raw = readOpenBytes(emulator, blocks, RESEED_PATH);
            assertEquals(RESEED_TEXT, new String(raw, StandardCharsets.US_ASCII));
            assertEquals('\n', raw[raw.length - 1] & 0xff);

            assertNotTakenOverAsConfigured(emulator, blocks, sink, WAKEUP_PATH,
                    WAKEUP_TEXT, "write_wakeup_threshold");

            assertEquals(1, sink.events.size());
            assertRandomThresholdSidecar(sink.events.get(0), RESEED_PATH,
                    "urandom_min_reseed_secs", raw.length);
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
                + "\"writeWakeupThreshold\":" + WAKEUP_VALUE + ","
                + "\"urandomMinReseedSecs\":" + RESEED_VALUE
                + "}}}"
        );
        assertTrue(config.getLinuxProcConfig().isWriteWakeupThresholdConfigured());
        assertTrue(config.getLinuxProcConfig().isUrandomMinReseedSecsConfigured());
        assertEquals(WAKEUP_VALUE, config.getLinuxProcConfig().getWriteWakeupThreshold());
        assertEquals(RESEED_VALUE, config.getLinuxProcConfig().getUrandomMinReseedSecs());
        assertFalse(config.getLinuxProcConfig().isEntropyAvailConfigured());
        assertFalse(config.getLinuxProcConfig().isRandomPoolSizeConfigured());

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] wakeupRaw = readOpenBytes(emulator, blocks, WAKEUP_PATH);
            byte[] reseedRaw = readOpenBytes(emulator, blocks, RESEED_PATH);
            assertEquals(WAKEUP_TEXT, new String(wakeupRaw, StandardCharsets.US_ASCII));
            assertEquals(RESEED_TEXT, new String(reseedRaw, StandardCharsets.US_ASCII));
            assertEquals('\n', wakeupRaw[wakeupRaw.length - 1] & 0xff);
            assertEquals('\n', reseedRaw[reseedRaw.length - 1] & 0xff);

            assertEquals(2, sink.events.size());
            assertRandomThresholdSidecar(sink.events.get(0), WAKEUP_PATH,
                    "write_wakeup_threshold", wakeupRaw.length);
            assertRandomThresholdSidecar(sink.events.get(1), RESEED_PATH,
                    "urandom_min_reseed_secs", reseedRaw.length);
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
        assertFalse(emptyProc.getLinuxProcConfig().isWriteWakeupThresholdConfigured());
        assertFalse(emptyProc.getLinuxProcConfig().isUrandomMinReseedSecsConfigured());
        assertNull(ConfiguredProcFiles.renderWriteWakeupThreshold(emptyProc));
        assertNull(ConfiguredProcFiles.renderUrandomMinReseedSecs(emptyProc));
        assertNull(ConfiguredProcFiles.renderWriteWakeupThreshold(null));
        assertNull(ConfiguredProcFiles.renderUrandomMinReseedSecs(null));

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(emptyProc).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfigured(emulator, blocks, sink, WAKEUP_PATH,
                    WAKEUP_TEXT, "write_wakeup_threshold");
            assertNotTakenOverAsConfigured(emulator, blocks, sink, RESEED_PATH,
                    RESEED_TEXT, "urandom_min_reseed_secs");
            for (CapturedEvent e : sink.events) {
                assertFalse(isRandomThresholdLinuxProc(e));
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

        TraceEnvironmentConfig poolOnly = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"entropyAvail\":256,"
                + "\"randomPoolSize\":4096,"
                + "\"bootId\":\"a1b2c3d4-e5f6-7890-abcd-ef1234567890\""
                + "}}}"
        );
        assertFalse(poolOnly.getLinuxProcConfig().isWriteWakeupThresholdConfigured());
        assertFalse(poolOnly.getLinuxProcConfig().isUrandomMinReseedSecsConfigured());
        assertNull(ConfiguredProcFiles.renderWriteWakeupThreshold(poolOnly));
        assertNull(ConfiguredProcFiles.renderUrandomMinReseedSecs(poolOnly));
        sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(poolOnly).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfigured(emulator, blocks, sink, WAKEUP_PATH,
                    WAKEUP_TEXT, "write_wakeup_threshold");
            assertNotTakenOverAsConfigured(emulator, blocks, sink, RESEED_PATH,
                    RESEED_TEXT, "urandom_min_reseed_secs");
            for (CapturedEvent e : sink.events) {
                assertFalse(isRandomThresholdLinuxProc(e));
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
                + "\"files\":{\"" + WAKEUP_PATH + "\":\""
                + FILES_OVERRIDE.replace("\n", "\\n") + "\"},"
                + "\"proc\":{"
                + "\"writeWakeupThreshold\":" + WAKEUP_VALUE + ","
                + "\"urandomMinReseedSecs\":" + RESEED_VALUE
                + "}"
                + "}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals(FILES_OVERRIDE, readOpenText(emulator, blocks, WAKEUP_PATH));
            assertEquals(RESEED_TEXT, readOpenText(emulator, blocks, RESEED_PATH));

            boolean sawLinuxFile = false;
            boolean sawReseedProc = false;
            for (CapturedEvent e : sink.events) {
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                    assertEquals("json-config", e.source);
                }
                if ("linux_proc".equals(e.kind)
                        && String.valueOf(e.value).contains("format=write_wakeup_threshold")) {
                    fail("linux.files hit must not emit write_wakeup_threshold linux_proc");
                }
                if ("linux_proc".equals(e.kind)
                        && String.valueOf(e.value).contains("format=urandom_min_reseed_secs")) {
                    sawReseedProc = true;
                }
            }
            assertTrue(sawLinuxFile);
            assertTrue(sawReseedProc);
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
                + "\"writeWakeupThreshold\":" + WAKEUP_VALUE + ","
                + "\"urandomMinReseedSecs\":" + RESEED_VALUE
                + "}}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertNotTakenOverAsConfigured(emulator, blocks, sink, WAKEUP_PATH + "/",
                    WAKEUP_TEXT, "write_wakeup_threshold");
            assertNotTakenOverAsConfigured(emulator, blocks, sink, RESEED_PATH + "/",
                    RESEED_TEXT, "urandom_min_reseed_secs");
            assertNotTakenOverAsConfigured(emulator, blocks, sink,
                    "/proc/sys/kernel/random/write_wakeup_thresholds",
                    WAKEUP_TEXT, "write_wakeup_threshold");
            assertNotTakenOverAsConfigured(emulator, blocks, sink,
                    "/proc/sys/kernel/random/urandom_min_reseed_sec",
                    RESEED_TEXT, "urandom_min_reseed_secs");
            assertNotTakenOverAsConfigured(emulator, blocks, sink,
                    "/proc/sys/kernel/random/read_wakeup_threshold",
                    WAKEUP_TEXT, "write_wakeup_threshold");
            assertNotTakenOverAsConfigured(emulator, blocks, sink,
                    "/proc/sys/kernel/random", WAKEUP_TEXT, "write_wakeup_threshold");
            assertNotTakenOverAsConfigured(emulator, blocks, sink,
                    "/proc/sys/kernel/random/entropy_avail",
                    WAKEUP_TEXT, "write_wakeup_threshold");
            assertNotTakenOverAsConfigured(emulator, blocks, sink,
                    "/proc/sys/kernel/random/poolsize",
                    RESEED_TEXT, "urandom_min_reseed_secs");

            assertWriteOpenNotTakenOver(emulator, blocks, sink, WAKEUP_PATH,
                    IOConstants.O_WRONLY, WAKEUP_TEXT);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, WAKEUP_PATH,
                    IOConstants.O_RDWR, WAKEUP_TEXT);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, WAKEUP_PATH,
                    IOConstants.O_DIRECTORY, WAKEUP_TEXT);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, RESEED_PATH,
                    IOConstants.O_WRONLY, RESEED_TEXT);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, RESEED_PATH,
                    IOConstants.O_RDWR, RESEED_TEXT);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, RESEED_PATH,
                    IOConstants.O_DIRECTORY, RESEED_TEXT);

            for (CapturedEvent e : sink.events) {
                assertFalse(isRandomThresholdLinuxProc(e));
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
                "{\"linux\":{\"proc\":{\"writeWakeupThreshold\":0}}}");
        assertTrue(zero.getLinuxProcConfig().isWriteWakeupThresholdConfigured());
        assertEquals(0, zero.getLinuxProcConfig().getWriteWakeupThreshold());
        assertFalse(zero.getLinuxProcConfig().isUrandomMinReseedSecsConfigured());
        assertEquals("0\n", new String(ConfiguredProcFiles.renderWriteWakeupThreshold(zero),
                StandardCharsets.US_ASCII));

        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"urandomMinReseedSecs\":2147483647}}}");
        assertTrue(max.getLinuxProcConfig().isUrandomMinReseedSecsConfigured());
        assertEquals(2147483647, max.getLinuxProcConfig().getUrandomMinReseedSecs());
        assertFalse(max.getLinuxProcConfig().isWriteWakeupThresholdConfigured());
        assertEquals("2147483647\n", new String(ConfiguredProcFiles.renderUrandomMinReseedSecs(max),
                StandardCharsets.US_ASCII));

        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{}}}");
        assertFalse(missing.getLinuxProcConfig().isWriteWakeupThresholdConfigured());
        assertFalse(missing.getLinuxProcConfig().isUrandomMinReseedSecsConfigured());

        String[] fields = {"writeWakeupThreshold", "urandomMinReseedSecs"};
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
        assertInvalid("{\"linux\":{\"proc\":{\"write_wakeup_threshold\":1}}}",
                "linux.proc.write_wakeup_threshold");
        assertInvalid("{\"linux\":{\"proc\":{\"urandom_min_reseed_secs\":1}}}",
                "linux.proc.urandom_min_reseed_secs");
        assertInvalid("{\"linux\":{\"proc\":{\"writeWakeupThresholdExtra\":1}}}",
                "linux.proc.writeWakeupThresholdExtra");
        assertInvalid("{\"linux\":{\"proc\":{\"urandomMinReseedSecsExtra\":1}}}",
                "linux.proc.urandomMinReseedSecsExtra");
    }

    private static void assertRandomThresholdSidecar(CapturedEvent e, String path, String format,
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
        assertFalse(value.contains(String.valueOf(WAKEUP_VALUE)));
        assertFalse(value.contains(String.valueOf(RESEED_VALUE)));
        assertFalse(note.contains(String.valueOf(WAKEUP_VALUE)));
        assertFalse(note.contains(String.valueOf(RESEED_VALUE)));
        assertFalse(value.contains(WAKEUP_TEXT.trim()));
        assertFalse(value.contains(RESEED_TEXT.trim()));
    }

    private static boolean isRandomThresholdLinuxProc(CapturedEvent e) {
        String value = String.valueOf(e.value);
        return "linux_proc".equals(e.kind)
                && (value.contains("format=write_wakeup_threshold")
                || value.contains("format=urandom_min_reseed_secs"));
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
            assertFalse("write/directory open must not serve configured random threshold",
                    configuredText.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isRandomThresholdLinuxProc(e));
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
