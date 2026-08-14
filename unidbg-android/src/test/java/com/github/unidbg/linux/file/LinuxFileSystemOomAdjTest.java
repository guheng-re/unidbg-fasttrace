package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.EmulatorBuilder;
import com.github.unidbg.arm.backend.Unicorn2Factory;
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
 * LinuxFileSystem wiring for {@code linux.proc.oomAdj} → exact
 * {@code /proc/self|pid/oom_adj} (UTF-8 decimal + LF, read-only). Independent of
 * {@code oomScoreAdj}/{@code oomScore}. Tests free malloc blocks and close the
 * emulator so they can mix with other lifecycle tests.
 */
public class LinuxFileSystemOomAdjTest {

    private static final int CONFIG_PID = 4242;
    private static final int OOM_ADJ = -17;
    private static final String OOM_ADJ_TEXT = "-17\n";
    private static final String SELF_PATH = "/proc/self/oom_adj";
    private static final String PID_PATH = "/proc/" + CONFIG_PID + "/oom_adj";
    private static final String FILES_OVERRIDE = "CUSTOM_OOM_ADJ\n";

    private static final String CONFIGURED_JSON = "{"
            + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
            + "\"linux\":{\"proc\":{\"oomAdj\":" + OOM_ADJ + "}}"
            + "}";

    @Test
    public void testReadSelfAndPidAndSidecar64() throws Exception {
        runReadSelfAndPidAndSidecar(true);
    }

    @Test
    public void testReadSelfAndPidAndSidecar32() throws Exception {
        runReadSelfAndPidAndSidecar(false);
    }

    @Test
    public void testLinuxFilesPriority64() throws Exception {
        runLinuxFilesPriority(true);
    }

    @Test
    public void testLinuxFilesPriority32() throws Exception {
        runLinuxFilesPriority(false);
    }

    @Test
    public void testMissingUnknownWriteNotTakenOver64() throws Exception {
        runMissingUnknownWriteNotTakenOver(true);
    }

    @Test
    public void testMissingUnknownWriteNotTakenOver32() throws Exception {
        runMissingUnknownWriteNotTakenOver(false);
    }

    @Test
    public void testInvalidAndValidOomAdjParse() {
        TraceEnvironmentConfig min = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomAdj\":-17}}}");
        assertTrue(min.getLinuxProcConfig().isOomAdjConfigured());
        assertEquals(-17, min.getLinuxProcConfig().getOomAdj());
        assertFalse(min.getLinuxProcConfig().isOomScoreAdjConfigured());
        assertFalse(min.getLinuxProcConfig().isOomScoreConfigured());

        TraceEnvironmentConfig adjMin = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomAdj\":-16}}}");
        assertEquals(-16, adjMin.getLinuxProcConfig().getOomAdj());

        TraceEnvironmentConfig zero = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomAdj\":0}}}");
        assertTrue(zero.getLinuxProcConfig().isOomAdjConfigured());
        assertEquals(0, zero.getLinuxProcConfig().getOomAdj());

        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomAdj\":15}}}");
        assertEquals(15, max.getLinuxProcConfig().getOomAdj());

        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{}}}");
        assertFalse(missing.getLinuxProcConfig().isOomAdjConfigured());

        TraceEnvironmentConfig scoreAdjOnly = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomScoreAdj\":100}}}");
        assertTrue(scoreAdjOnly.getLinuxProcConfig().isOomScoreAdjConfigured());
        assertFalse(scoreAdjOnly.getLinuxProcConfig().isOomAdjConfigured());

        assertInvalid("{\"linux\":{\"proc\":{\"oomAdj\":-18}}}", "linux.proc.oomAdj");
        assertInvalid("{\"linux\":{\"proc\":{\"oomAdj\":16}}}", "linux.proc.oomAdj");
        assertInvalid("{\"linux\":{\"proc\":{\"oomAdj\":1.5}}}", "linux.proc.oomAdj");
        assertInvalid("{\"linux\":{\"proc\":{\"oomAdj\":null}}}", "linux.proc.oomAdj");
        assertInvalid("{\"linux\":{\"proc\":{\"oomAdj\":\"0\"}}}", "linux.proc.oomAdj");
        assertInvalid("{\"linux\":{\"proc\":{\"oomAdj\":true}}}", "linux.proc.oomAdj");
    }

    private static EmulatorBuilder<AndroidEmulator> emulatorBuilder(boolean is64Bit) {
        return (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                .addBackendFactory(new Unicorn2Factory(true));
    }

    private static void runReadSelfAndPidAndSidecar(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        assertTrue(config.getLinuxProcConfig().isOomAdjConfigured());
        assertEquals(OOM_ADJ, config.getLinuxProcConfig().getOomAdj());

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            assertEquals(OOM_ADJ_TEXT, readOpenText(emulator, blocks, SELF_PATH));
            assertEquals(OOM_ADJ_TEXT, readOpenText(emulator, blocks, PID_PATH));

            assertEquals(2, sink.events.size());
            assertOomAdjSidecar(sink.events.get(0), SELF_PATH,
                    OOM_ADJ_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertOomAdjSidecar(sink.events.get(1), PID_PATH,
                    OOM_ADJ_TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsOomAdjNumber(sink.events.get(0));
            assertSidecarOmitsOomAdjNumber(sink.events.get(1));
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

    private static void runLinuxFilesPriority(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"x\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"" + SELF_PATH + "\":\"" + FILES_OVERRIDE.replace("\n", "\\n") + "\"},"
                + "\"proc\":{\"oomAdj\":" + OOM_ADJ + "}"
                + "}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals(FILES_OVERRIDE, readOpenText(emulator, blocks, SELF_PATH));
            assertEquals(FILES_OVERRIDE, readOpenText(emulator, blocks, PID_PATH));

            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                assertFalse("linux.files hit must not emit linux_proc",
                        isOomAdjLinuxProc(e));
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
            }
            assertTrue(sawLinuxFile);
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

    private static void runMissingUnknownWriteNotTakenOver(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"x\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"oomScoreAdj\":100}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(missing).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredOomAdj(emulator, blocks, sink, SELF_PATH);
            assertNotTakenOverAsConfiguredOomAdj(emulator, blocks, sink, PID_PATH);
            for (CapturedEvent e : sink.events) {
                assertFalse(isOomAdjLinuxProc(e));
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

        TraceEnvironmentConfig configured = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        sink = new CapturingSink();
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(configured).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            int wrong = CONFIG_PID + 1;
            assertNotTakenOverAsConfiguredOomAdj(emulator, blocks, sink,
                    "/proc/" + wrong + "/oom_adj");
            assertNotTakenOverAsConfiguredOomAdj(emulator, blocks, sink,
                    "/proc/self/oom_adj/");
            assertNotTakenOverAsConfiguredOomAdj(emulator, blocks, sink,
                    "/proc/self/oom_score_adj");
            assertNotTakenOverAsConfiguredOomAdj(emulator, blocks, sink,
                    "/proc/self/task/" + CONFIG_PID + "/oom_adj");
            assertWriteOpenNotTakenOver(emulator, blocks, sink, SELF_PATH, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, SELF_PATH, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, SELF_PATH, IOConstants.O_DIRECTORY);
            for (CapturedEvent e : sink.events) {
                assertFalse(isOomAdjLinuxProc(e));
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

    private static void assertOomAdjSidecar(CapturedEvent e, String path, int bytes) {
        assertNotNull(e);
        assertEquals("linux_proc", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",format=oom_adj,bytes=" + bytes,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains("读取配置的 OOM 调整值"));
        assertTrue(e.note.contains(path));
    }

    private static void assertSidecarOmitsOomAdjNumber(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains(String.valueOf(OOM_ADJ)));
        assertFalse(note.contains(String.valueOf(OOM_ADJ)));
        assertFalse(value.contains(OOM_ADJ_TEXT.trim()));
        assertFalse(value.contains("oomScoreAdj"));
        assertFalse(value.contains("oomScore"));
        assertFalse(note.contains("oomScoreAdj"));
        assertFalse(note.contains("oomScore"));
    }

    private static boolean isOomAdjLinuxProc(CapturedEvent e) {
        return "linux_proc".equals(e.kind)
                && String.valueOf(e.value).contains("format=oom_adj");
    }

    private static void assertNotTakenOverAsConfiguredOomAdj(AndroidEmulator emulator,
                                                             List<MemoryBlock> blocks,
                                                             CapturingSink sink,
                                                             String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 64);
            assertFalse(path + " must not serve configured oomAdj", OOM_ADJ_TEXT.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected oom_adj sidecar for " + path,
                    "linux_proc".equals(e.kind)
                            && ("read(\"" + path + "\")").equals(e.api)
                            && String.valueOf(e.value).contains("format=oom_adj"));
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
            assertFalse("write/directory open must not serve configured oomAdj",
                    OOM_ADJ_TEXT.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isOomAdjLinuxProc(e));
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
        return readIoBytes(emulator, blocks, result.io, 64);
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
