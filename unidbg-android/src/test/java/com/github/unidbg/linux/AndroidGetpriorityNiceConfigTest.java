package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.EmulatorBuilder;
import com.github.unidbg.arm.ARMEmulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.file.ByteArrayFileIO;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.sun.jna.Pointer;
import org.junit.Test;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Focused ARM32/ARM64 tests for optional {@code linux.proc.nice}: JSON parse,
 * raw {@code getpriority} syscall encoding ({@code PRIO_PROCESS} + who 0/current pid),
 * and {@code /proc/self|pid/stat} fields 18/19. Configured hits return
 * {@code rawResult=20-nice} (40..1); Bionic exported {@code getpriority()} is
 * {@code 20-rawResult}. Uses {@link AndroidEmulatorBuilder} default backend and real
 * {@code hook(EXCP_SWI, swi=0)}. Does not exercise scheduler state.
 * Each helper owns one emulator and closes it in {@code try/finally}: unregister sink,
 * free that helper's {@code MemoryBlock}s, then {@code emulator.close()} (not swallowed).
 */
public class AndroidGetpriorityNiceConfigTest {

    private static final int CONFIG_PID = 12345;
    private static final int UNKNOWN_PID = 99999;
    private static final int NICE = 5;
    private static final int NICE_NEGATIVE = -8;

    /** Linux {@code PRIO_PROCESS}. */
    private static final int PRIO_PROCESS = 0;
    /** Linux {@code PRIO_PGRP}. */
    private static final int PRIO_PGRP = 1;

    /** Linux arm {@code __NR_getpriority}. */
    private static final int NR_GETPRIORITY_ARM32 = 96;
    /** Linux arm {@code __NR_setpriority}. */
    private static final int NR_SETPRIORITY_ARM32 = 97;
    /** Linux aarch64 {@code __NR_setpriority}. */
    private static final int NR_SETPRIORITY_ARM64 = 140;
    /** Linux aarch64 {@code __NR_getpriority}. */
    private static final int NR_GETPRIORITY_ARM64 = 141;

    private static final String CONFIGURED_JSON = "{"
            + "\"process\":{\"pid\":" + CONFIG_PID + "},"
            + "\"linux\":{\"proc\":{\"nice\":" + NICE + "}}"
            + "}";

    private static final String MISSING_NICE_JSON = "{"
            + "\"process\":{\"pid\":" + CONFIG_PID + "},"
            + "\"linux\":{\"proc\":{}}"
            + "}";

    @Test
    public void testParseSuccess() {
        TraceEnvironmentConfig missingRoot = TraceEnvironmentConfig.parse("{}");
        assertFalse(missingRoot.isLinuxProcConfigured());

        TraceEnvironmentConfig emptyProc = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{}}}");
        assertTrue(emptyProc.isLinuxProcConfigured());
        assertFalse(emptyProc.getLinuxProcConfig().isNiceConfigured());

        TraceEnvironmentConfig zero = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"nice\":0}}}");
        assertTrue(zero.getLinuxProcConfig().isNiceConfigured());
        assertEquals(0, zero.getLinuxProcConfig().getNice());

        TraceEnvironmentConfig low = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"nice\":-20}}}");
        assertTrue(low.getLinuxProcConfig().isNiceConfigured());
        assertEquals(-20, low.getLinuxProcConfig().getNice());

        TraceEnvironmentConfig high = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"nice\":19}}}");
        assertTrue(high.getLinuxProcConfig().isNiceConfigured());
        assertEquals(19, high.getLinuxProcConfig().getNice());

        TraceEnvironmentConfig mid = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        assertTrue(mid.getLinuxProcConfig().isNiceConfigured());
        assertEquals(NICE, mid.getLinuxProcConfig().getNice());
    }

    @Test
    public void testParseRejected() {
        assertInvalid("{\"linux\":{\"proc\":{\"nice\":null}}}", "linux.proc.nice");
        assertInvalid("{\"linux\":{\"proc\":{\"nice\":\"0\"}}}", "linux.proc.nice");
        assertInvalid("{\"linux\":{\"proc\":{\"nice\":1.5}}}", "linux.proc.nice");
        assertInvalid("{\"linux\":{\"proc\":{\"nice\":true}}}", "linux.proc.nice");
        assertInvalid("{\"linux\":{\"proc\":{\"nice\":false}}}", "linux.proc.nice");
        assertInvalid("{\"linux\":{\"proc\":{\"nice\":-21}}}", "linux.proc.nice");
        assertInvalid("{\"linux\":{\"proc\":{\"nice\":20}}}", "linux.proc.nice");
        assertInvalid("{\"linux\":{\"proc\":{\"notANiceKey\":0}}}", "linux.proc.notANiceKey");
    }

    @Test
    public void testBionicWrapperInvertsRawSyscallEncoding() {
        int[] nices = new int[] { -20, NICE_NEGATIVE, 0, NICE, 19 };
        for (int i = 0; i < nices.length; i++) {
            int nice = nices[i];
            int rawResult = toGetpriorityRawResult(nice);
            assertEquals("rawResult=20-nice for nice=" + nice, 20 - nice, rawResult);
            assertTrue("rawResult in 1..40 for nice=" + nice, rawResult >= 1 && rawResult <= 40);
            assertEquals("Bionic exported getpriority is 20-rawResult for nice=" + nice,
                    nice, bionicExportedGetpriority(rawResult));
        }
    }

    @Test
    public void testConfiguredGetpriority32() throws Exception {
        runConfiguredGetpriorityNice(false, NICE);
    }

    @Test
    public void testConfiguredGetpriority64() throws Exception {
        // Configured nice=0 must return rawResult=20, not unconfigured historical 0.
        runConfiguredGetpriorityNice(true, 0);
    }

    @Test
    public void testNonHitWhoOrWhich32() throws Exception {
        runNonHitWhoOrWhich(false);
    }

    @Test
    public void testNonHitWhoOrWhich64() throws Exception {
        runNonHitWhoOrWhich(true);
    }

    @Test
    public void testSetpriorityUnchanged32() throws Exception {
        runSetpriorityUnchanged(false);
    }

    @Test
    public void testSetpriorityUnchanged64() throws Exception {
        runSetpriorityUnchanged(true);
    }

    @Test
    public void testStatFieldsConfigured32() throws Exception {
        runStatFields(false, CONFIGURED_JSON, 20 + NICE, NICE);
    }

    @Test
    public void testStatFieldsConfigured64() throws Exception {
        runStatFields(true, CONFIGURED_JSON, 20 + NICE, NICE);
    }

    @Test
    public void testStatFieldsMissing32() throws Exception {
        runMissingNice(false);
    }

    @Test
    public void testStatFieldsMissing64() throws Exception {
        runMissingNice(true);
    }

    private static void runConfiguredGetpriorityNice(boolean is64Bit, int nice) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(jsonWithNice(nice));
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            assertEquals(CONFIG_PID, emulator.getPid());
            TraceEnvironmentEventSink.register(emulator, sink);
            int nr = getpriorityNr(is64Bit);
            int rawResult = toGetpriorityRawResult(nice);
            assertEquals(nice, bionicExportedGetpriority(rawResult));

            assertEquals(rawResult, invokeSyscall(emulator, nr, PRIO_PROCESS, 0, 0));
            assertNiceEvent(findLast(sink.events, "linux_proc", "getpriority"),
                    nice, PRIO_PROCESS, 0);

            assertEquals(rawResult, invokeSyscall(emulator, nr, PRIO_PROCESS, CONFIG_PID, 0));
            assertNiceEvent(findLast(sink.events, "linux_proc", "getpriority"),
                    nice, PRIO_PROCESS, CONFIG_PID);
            assertEquals(2, countApi(sink.events, "getpriority"));
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static void runNonHitWhoOrWhich(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            int nr = getpriorityNr(is64Bit);

            assertEquals(0, invokeSyscall(emulator, nr, PRIO_PROCESS, UNKNOWN_PID, 0));
            assertEquals(0, invokeSyscall(emulator, nr, PRIO_PGRP, 0, 0));
            assertEquals(0, invokeSyscall(emulator, nr, PRIO_PGRP, CONFIG_PID, 0));
            assertEquals(0, countApi(sink.events, "getpriority"));
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected getpriority sidecar on non-hit: " + e.value,
                        "getpriority".equals(e.api));
            }
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static void runSetpriorityUnchanged(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"process\":{\"pid\":" + CONFIG_PID + "},"
                        + "\"linux\":{\"proc\":{\"nice\":" + NICE_NEGATIVE + "}}}");
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            int setNr = is64Bit ? NR_SETPRIORITY_ARM64 : NR_SETPRIORITY_ARM32;
            int getNr = getpriorityNr(is64Bit);

            assertEquals(0, invokeSyscall(emulator, setNr, PRIO_PROCESS, 0, 10));
            assertEquals(0, countApi(sink.events, "setpriority"));
            int rawResult = toGetpriorityRawResult(NICE_NEGATIVE);
            assertEquals(rawResult, invokeSyscall(emulator, getNr, PRIO_PROCESS, 0, 0));
            assertEquals(NICE_NEGATIVE, bionicExportedGetpriority(rawResult));
            assertNiceEvent(findLast(sink.events, "linux_proc", "getpriority"),
                    NICE_NEGATIVE, PRIO_PROCESS, 0);
            assertEquals(1, countApi(sink.events, "getpriority"));
            assertEquals(0, countApi(sink.events, "setpriority"));
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static void runStatFields(boolean is64Bit, String json, int expectedPriority,
                                      int expectedNice) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertStatNice(readOpenText(emulator, blocks, "/proc/self/stat"),
                    expectedPriority, expectedNice);
            assertStatNice(readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/stat"),
                    expectedPriority, expectedNice);
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static void runMissingNice(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(MISSING_NICE_JSON);
        assertFalse(config.getLinuxProcConfig().isNiceConfigured());
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            assertEquals(CONFIG_PID, emulator.getPid());
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals(0, invokeSyscall(emulator, getpriorityNr(is64Bit), PRIO_PROCESS, 0, 0));
            assertEquals(0, invokeSyscall(emulator, getpriorityNr(is64Bit),
                    PRIO_PROCESS, CONFIG_PID, 0));
            assertEquals(0, countApi(sink.events, "getpriority"));

            assertStatNice(readOpenText(emulator, blocks, "/proc/self/stat"), 20, 0);
            assertStatNice(readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/stat"),
                    20, 0);
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static String jsonWithNice(int nice) {
        return "{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"nice\":" + nice + "}}"
                + "}";
    }

    /**
     * Linux raw {@code getpriority} success encoding: {@code 20 - nice} (40..1).
     */
    private static int toGetpriorityRawResult(int nice) {
        return 20 - nice;
    }

    /**
     * Bionic exported {@code getpriority()} converts a successful raw syscall
     * result back to user-space nice.
     */
    private static int bionicExportedGetpriority(int rawResult) {
        return 20 - rawResult;
    }

    private static int getpriorityNr(boolean is64Bit) {
        return is64Bit ? NR_GETPRIORITY_ARM64 : NR_GETPRIORITY_ARM32;
    }

    private static int invokeSyscall(AndroidEmulator emulator, int nr, int arg0, int arg1,
                                     int arg2) {
        Backend backend = emulator.getBackend();
        if (emulator.is32Bit()) {
            backend.reg_write(ArmConst.UC_ARM_REG_R0, arg0);
            backend.reg_write(ArmConst.UC_ARM_REG_R1, arg1);
            backend.reg_write(ArmConst.UC_ARM_REG_R2, arg2);
            backend.reg_write(ArmConst.UC_ARM_REG_R7, nr);
            backend.reg_write(ArmConst.UC_ARM_REG_R5, 0);
        } else {
            backend.reg_write(Arm64Const.UC_ARM64_REG_X0, arg0);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X1, arg1);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X2, arg2);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X8, nr);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X16, 0L);
        }
        AndroidSyscallHandler handler = (AndroidSyscallHandler) emulator.getSyscallHandler();
        handler.hook(backend, ARMEmulator.EXCP_SWI, 0, emulator);
        if (emulator.is32Bit()) {
            return backend.reg_read(ArmConst.UC_ARM_REG_R0).intValue();
        }
        return backend.reg_read(Arm64Const.UC_ARM64_REG_X0).intValue();
    }

    private static void assertNiceEvent(CapturedEvent ev, int expectedNice, int which, int who) {
        assertNotNull(ev);
        assertEquals("linux_proc", ev.kind);
        assertEquals("getpriority", ev.api);
        assertEquals("json-config", ev.source);
        String value = String.valueOf(ev.value);
        int rawResult = toGetpriorityRawResult(expectedNice);
        assertEquals("field=nice,nice=" + expectedNice + ",rawResult=" + rawResult
                        + ",which=" + which + ",who=" + who,
                value);
        assertFalse("sidecar must name the syscall encoding rawResult, not result: " + value,
                value.contains("result="));
        assertFalse("sidecar value must not contain addresses: " + value, value.contains("0x"));
        assertTrue("note should mention raw syscall encoding and Bionic, was: " + ev.note,
                ev.note != null && ev.note.contains("原始系统调用编码") && ev.note.contains("Bionic"));
    }

    private static void assertStatNice(String statText, int expectedPriority, int expectedNice) {
        assertTrue(statText.endsWith("\n"));
        String[] fields = parseStatFields(statText.getBytes(StandardCharsets.UTF_8));
        assertEquals(52, fields.length);
        // Linux 1-based field 18 priority / field 19 nice → 0-based index 17 / 18
        assertEquals(Integer.toString(expectedPriority), fields[17]);
        assertEquals(Integer.toString(expectedNice), fields[18]);
    }

    private static String[] parseStatFields(byte[] raw) {
        String line = new String(raw, StandardCharsets.UTF_8).trim();
        int open = line.indexOf('(');
        int close = line.lastIndexOf(')');
        assertTrue(open > 0 && close > open);
        String pid = line.substring(0, open).trim();
        String comm = line.substring(open, close + 1);
        String rest = line.substring(close + 1).trim();
        String[] tail = rest.isEmpty() ? new String[0] : rest.split(" ", -1);
        String[] all = new String[2 + tail.length];
        all[0] = pid;
        all[1] = comm;
        System.arraycopy(tail, 0, all, 2, tail.length);
        return all;
    }

    private static String readOpenText(AndroidEmulator emulator, List<MemoryBlock> blocks,
                                       String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        assertNotNull(result);
        assertTrue("open failed for " + path, result.isSuccess());
        assertNotNull(result.io);
        assertTrue(result.io instanceof ByteArrayFileIO);
        MemoryBlock block = emulator.getMemory().malloc(8192, true);
        blocks.add(block);
        Pointer ptr = block.getPointer();
        int n = result.io.read(emulator.getBackend(), ptr, 8192);
        assertTrue(n > 0);
        return new String(ptr.getByteArray(0, n), StandardCharsets.UTF_8);
    }

    private static void cleanup(AndroidEmulator emulator, CapturingSink sink,
                                List<MemoryBlock> blocks) throws Exception {
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

    private static CapturedEvent findLast(List<CapturedEvent> events, String kind, String api) {
        CapturedEvent found = null;
        for (CapturedEvent e : events) {
            if (kind.equals(e.kind) && api.equals(e.api)) {
                found = e;
            }
        }
        return found;
    }

    private static int countApi(List<CapturedEvent> events, String api) {
        int n = 0;
        for (CapturedEvent e : events) {
            if (api.equals(e.api)) {
                n++;
            }
        }
        return n;
    }

    private static EmulatorBuilder<AndroidEmulator> emulatorBuilder(boolean is64Bit) {
        return is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
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
