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

/**
 * LinuxFileSystem wiring for one-way {@code linux.rlimits.nofile} →
 * {@code /proc/self|pid/limits} derivation when {@code linux.proc.limits} is absent.
 * Does not exercise {@code getrlimit64}/{@code setrlimit}/{@code prlimit64}/ARM32/other rlimits.
 * Each helper owns one emulator and closes it in {@code try/finally}: unregister sink,
 * free that helper's {@code MemoryBlock}s, then {@code emulator.close()} (close is not swallowed).
 */
public class LinuxFileSystemRlimitNofileLimitsTest {

    private static final int CONFIG_PID = 4242;
    private static final long NOFILE_SOFT = 32768L;
    private static final long NOFILE_HARD = 65536L;
    private static final String SELF_PATH = "/proc/self/limits";
    private static final String PID_PATH = "/proc/" + CONFIG_PID + "/limits";
    private static final String FILES_OVERRIDE = "CUSTOM_LIMITS\n";
    private static final String PROC_LIMITS_ROW = "CUSTOM_LIMITS_ROW";

    /**
     * Android/Linux common column alignment: {@code %-25s %-20s %-20s %-10s} plus LF.
     * Locked as a literal so whitespace cannot drift.
     */
    private static final String DERIVED_NOFILE_LINE =
            "Max open files            32768                65536                files     \n";

    private static final String NOFILE_ONLY_JSON = "{"
            + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
            + "\"linux\":{\"rlimits\":{\"nofile\":{\"soft\":" + NOFILE_SOFT
            + ",\"hard\":" + NOFILE_HARD + "}}}"
            + "}";

    @Test
    public void testNofileOnlyDerivesSelfAndPidAndSidecar() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NOFILE_ONLY_JSON);
        assertFalse(config.isLinuxProcConfigured());
        assertTrue(config.isLinuxRlimitsNofileConfigured());
        byte[] rendered = ConfiguredProcFiles.renderLimits(config);
        assertNotNull(rendered);
        assertEquals(DERIVED_NOFILE_LINE, new String(rendered, StandardCharsets.UTF_8));
        assertEquals(DERIVED_NOFILE_LINE.getBytes(StandardCharsets.UTF_8).length, rendered.length);

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            String self = readOpenText(emulator, blocks, SELF_PATH);
            String pid = readOpenText(emulator, blocks, PID_PATH);
            assertEquals(DERIVED_NOFILE_LINE, self);
            assertEquals(DERIVED_NOFILE_LINE, pid);
            assertEquals(self, pid);
            assertTrue(self.endsWith("\n"));
            assertEquals(1, countNewlines(self));
            assertFalse(self.contains("Limit"));
            assertFalse(self.contains("unlimited"));
            assertFalse(self.contains("Max cpu time"));
            assertFalse(self.contains("0x"));

            assertEquals(2, sink.events.size());
            assertLimitsSidecar(sink.events.get(0), SELF_PATH,
                    DERIVED_NOFILE_LINE.getBytes(StandardCharsets.UTF_8).length);
            assertLimitsSidecar(sink.events.get(1), PID_PATH,
                    DERIVED_NOFILE_LINE.getBytes(StandardCharsets.UTF_8).length);
            assertSidecarOmitsLineAndHost(sink.events.get(0));
            assertSidecarOmitsLineAndHost(sink.events.get(1));
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    @Test
    public void testProcLimitsTextOverridesNofile() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"proc\":{\"limits\":[\"" + PROC_LIMITS_ROW + "\"]},"
                + "\"rlimits\":{\"nofile\":{\"soft\":" + NOFILE_SOFT
                + ",\"hard\":" + NOFILE_HARD + "}}"
                + "}}");
        assertTrue(config.getLinuxProcConfig().isLimitsConfigured());
        assertTrue(config.isLinuxRlimitsNofileConfigured());
        String expected = PROC_LIMITS_ROW + "\n";
        assertEquals(expected, new String(ConfiguredProcFiles.renderLimits(config),
                StandardCharsets.UTF_8));

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(expected, readOpenText(emulator, blocks, SELF_PATH));
            assertEquals(expected, readOpenText(emulator, blocks, PID_PATH));
            assertFalse(expected.equals(DERIVED_NOFILE_LINE));

            assertEquals(2, sink.events.size());
            assertLimitsSidecar(sink.events.get(0), SELF_PATH,
                    expected.getBytes(StandardCharsets.UTF_8).length);
            assertLimitsSidecar(sink.events.get(1), PID_PATH,
                    expected.getBytes(StandardCharsets.UTF_8).length);
            for (CapturedEvent e : sink.events) {
                String value = String.valueOf(e.value);
                String note = String.valueOf(e.note);
                assertFalse(value.contains(PROC_LIMITS_ROW));
                assertFalse(value.contains("Max open files"));
                assertFalse(value.contains(Long.toString(NOFILE_SOFT)));
                assertFalse(value.contains(Long.toString(NOFILE_HARD)));
                assertFalse(note.contains(PROC_LIMITS_ROW));
                assertFalse(note.contains("Max open files"));
            }
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    @Test
    public void testEmptyProcLimitsOverridesNofileAsEmptyFile() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"proc\":{\"limits\":[]},"
                + "\"rlimits\":{\"nofile\":{\"soft\":" + NOFILE_SOFT
                + ",\"hard\":" + NOFILE_HARD + "}}"
                + "}}");
        byte[] rendered = ConfiguredProcFiles.renderLimits(config);
        assertNotNull(rendered);
        assertEquals(0, rendered.length);

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals("", readOpenText(emulator, blocks, SELF_PATH));
            assertEquals("", readOpenText(emulator, blocks, PID_PATH));

            assertEquals(2, sink.events.size());
            assertLimitsSidecar(sink.events.get(0), SELF_PATH, 0);
            assertLimitsSidecar(sink.events.get(1), PID_PATH, 0);
            for (CapturedEvent e : sink.events) {
                assertFalse(String.valueOf(e.value).contains("Max open files"));
                assertFalse(String.valueOf(e.value).contains(Long.toString(NOFILE_SOFT)));
            }
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesExactContentOverridesNofileDerivation() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"" + SELF_PATH + "\":\""
                + FILES_OVERRIDE.replace("\n", "\\n") + "\"},"
                + "\"rlimits\":{\"nofile\":{\"soft\":" + NOFILE_SOFT
                + ",\"hard\":" + NOFILE_HARD + "}}"
                + "}}");
        assertNull(config.getLinuxProcConfig());
        assertTrue(config.isLinuxRlimitsNofileConfigured());

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(FILES_OVERRIDE, readOpenText(emulator, blocks, SELF_PATH));
            assertEquals(FILES_OVERRIDE, readOpenText(emulator, blocks, PID_PATH));

            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                assertFalse("linux.files hit must not emit linux_proc limits",
                        isLimitsLinuxProc(e));
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
            }
            assertTrue(sawLinuxFile);
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    @Test
    public void testBothMissingNotServedByStructuredLimits() throws Exception {
        TraceEnvironmentConfig procWithoutLimits = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"state\":\"S\"}}"
                + "}");
        assertNull(ConfiguredProcFiles.renderLimits(procWithoutLimits));
        assertNotServedAsStructuredLimits(procWithoutLimits);

        TraceEnvironmentConfig rlimitsWithoutNofile = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"rlimits\":{}}"
                + "}");
        assertFalse(rlimitsWithoutNofile.isLinuxRlimitsNofileConfigured());
        assertNull(ConfiguredProcFiles.renderLimits(rlimitsWithoutNofile));
        assertNotServedAsStructuredLimits(rlimitsWithoutNofile);

        TraceEnvironmentConfig emptyRoot = TraceEnvironmentConfig.parse("{}");
        assertNull(ConfiguredProcFiles.renderLimits(emptyRoot));
        assertNotServedAsStructuredLimits(emptyRoot);
    }

    private static void assertNotServedAsStructuredLimits(TraceEnvironmentConfig config)
            throws Exception {
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open(SELF_PATH, IOConstants.O_RDONLY);
            if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, result.io, 8192);
                assertFalse("must not serve derived nofile line when both keys are absent",
                        DERIVED_NOFILE_LINE.equals(text));
            }
            FileResult<AndroidFileIO> pidResult = emulator.getFileSystem()
                    .open(PID_PATH, IOConstants.O_RDONLY);
            if (pidResult != null && pidResult.isSuccess() && pidResult.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, pidResult.io, 8192);
                assertFalse(DERIVED_NOFILE_LINE.equals(text));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse(isLimitsLinuxProc(e));
                assertFalse(String.valueOf(e.value).contains("format=limits"));
            }
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static void assertLimitsSidecar(CapturedEvent e, String path, int bytes) {
        assertNotNull(e);
        assertEquals("linux_proc", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",format=limits,bytes=" + bytes, String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains("读取配置的进程 limits 文件"));
        assertTrue(e.note.contains(path));
    }

    private static void assertSidecarOmitsLineAndHost(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        assertFalse(value.contains("Max open files"));
        assertFalse(value.contains("files     "));
        assertFalse(value.contains(Long.toString(NOFILE_SOFT)));
        assertFalse(value.contains(Long.toString(NOFILE_HARD)));
        assertFalse(value.contains("0x"));
        assertFalse(value.contains("unlimited"));
        assertFalse(note.contains("Max open files"));
        assertFalse(note.contains(Long.toString(NOFILE_SOFT)));
        assertFalse(note.contains(Long.toString(NOFILE_HARD)));
        assertFalse(note.contains("0x"));
        assertFalse(value.contains("host"));
        assertFalse(note.contains("host"));
    }

    private static boolean isLimitsLinuxProc(CapturedEvent e) {
        return "linux_proc".equals(e.kind)
                && String.valueOf(e.api).contains("limits")
                && String.valueOf(e.value).contains("format=limits");
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

    private static void cleanup(AndroidEmulator emulator, CapturingSink sink, List<MemoryBlock> blocks)
            throws Exception {
        try {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
        } finally {
            try {
                if (blocks != null) {
                    for (int i = blocks.size() - 1; i >= 0; i--) {
                        blocks.get(i).free();
                    }
                    blocks.clear();
                }
            } finally {
                if (emulator != null) {
                    emulator.close();
                }
            }
        }
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
        public void emitEnvironmentEvent(String kind, String api, Object value, String source,
                                         String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}
