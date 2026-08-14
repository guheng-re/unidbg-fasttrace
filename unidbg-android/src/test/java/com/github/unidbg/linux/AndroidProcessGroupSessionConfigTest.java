package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.EmulatorBuilder;
import com.github.unidbg.arm.ARMEmulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.arm.backend.Unicorn2Factory;
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Focused ARM32/ARM64 tests for fixed {@code process.pgid}/{@code process.sid} reads:
 * {@code getpgrp}/{@code getpgid}/{@code getsid} and {@code /proc/self|pid/stat} fields 5/6.
 * Uses {@link Unicorn2Factory}{@code (true)} and real {@code hook(EXCP_SWI, swi=0)}.
 * Does not exercise {@code setpgid}/{@code setsid}. Each helper owns one emulator and
 * closes it in {@code try/finally}: unregister sink, free that helper's {@code MemoryBlock}s,
 * then {@code emulator.close()}.
 */
public class AndroidProcessGroupSessionConfigTest {

    private static final int CONFIG_PID = 12345;
    private static final int CONFIG_PPID = 11111;
    private static final int CONFIG_PGID = 23456;
    private static final int CONFIG_SID = 34567;
    private static final int UNKNOWN_PID = 99999;

    /** Linux arm {@code __NR_getpgrp}. AArch64 has no independent getpgrp. */
    private static final int NR_GETPGRP_ARM32 = 65;
    /** Linux arm {@code __NR_getpgid}. */
    private static final int NR_GETPGID_ARM32 = 132;
    /** Linux arm {@code __NR_getsid}. */
    private static final int NR_GETSID_ARM32 = 147;
    /** Linux aarch64 {@code __NR_getpgid}. */
    private static final int NR_GETPGID_ARM64 = 155;
    /** Linux aarch64 {@code __NR_getsid}. */
    private static final int NR_GETSID_ARM64 = 156;

    private static final String CONFIGURED_JSON = "{"
            + "\"process\":{"
            + "\"pid\":" + CONFIG_PID + ","
            + "\"ppid\":" + CONFIG_PPID + ","
            + "\"pgid\":" + CONFIG_PGID + ","
            + "\"sid\":" + CONFIG_SID
            + "},"
            + "\"linux\":{\"proc\":{}}"
            + "}";

    private static final String MISSING_GROUP_JSON = "{"
            + "\"process\":{"
            + "\"pid\":" + CONFIG_PID + ","
            + "\"ppid\":" + CONFIG_PPID
            + "},"
            + "\"linux\":{\"proc\":{}}"
            + "}";

    @Test
    public void testInvalidParseRejected() {
        assertInvalid("{\"process\":{\"pgid\":null}}", "process.pgid");
        assertInvalid("{\"process\":{\"sid\":null}}", "process.sid");
        assertInvalid("{\"process\":{\"pgid\":1.5}}", "process.pgid");
        assertInvalid("{\"process\":{\"sid\":1.5}}", "process.sid");
        assertInvalid("{\"process\":{\"pgid\":\"23456\"}}", "process.pgid");
        assertInvalid("{\"process\":{\"sid\":\"34567\"}}", "process.sid");
        assertInvalid("{\"process\":{\"pgid\":0}}", "process.pgid");
        assertInvalid("{\"process\":{\"sid\":0}}", "process.sid");
        assertInvalid("{\"process\":{\"pgid\":-1}}", "process.pgid");
        assertInvalid("{\"process\":{\"sid\":-2}}", "process.sid");
        assertInvalid("{\"process\":{\"pgid\":2147483648}}", "process.pgid");
        assertInvalid("{\"process\":{\"sid\":2147483648}}", "process.sid");
        assertInvalid("{\"process\":{\"pgid\":true}}", "process.pgid");
        assertInvalid("{\"process\":{\"sid\":false}}", "process.sid");

        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(
                "{\"process\":{\"pgid\":1,\"sid\":" + Integer.MAX_VALUE + "}}");
        assertEquals(1, ok.getPgid(7));
        assertEquals(Integer.MAX_VALUE, ok.getSid(3));
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{\"process\":{\"pid\":9}}");
        assertEquals(9, missing.getPgid(9));
        assertEquals(3, missing.getSid(3));
    }

    @Test
    public void testConfiguredSyscallReads32() throws Exception {
        runConfiguredReads(false);
    }

    @Test
    public void testConfiguredSyscallReads64() throws Exception {
        runConfiguredReads(true);
    }

    @Test
    public void testStatFields32() throws Exception {
        runStatFields(false, CONFIGURED_JSON, CONFIG_PGID, CONFIG_SID);
    }

    @Test
    public void testStatFields64() throws Exception {
        runStatFields(true, CONFIGURED_JSON, CONFIG_PGID, CONFIG_SID);
    }

    @Test
    public void testMissingKeysDefaultToPid32() throws Exception {
        runMissingKeysDefaultToPid(false);
    }

    @Test
    public void testMissingKeysDefaultToPid64() throws Exception {
        runMissingKeysDefaultToPid(true);
    }

    @Test
    public void testUnknownPid32() throws Exception {
        runUnknownPid(false);
    }

    @Test
    public void testUnknownPid64() throws Exception {
        runUnknownPid(true);
    }

    private static void runConfiguredReads(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        assertEquals(CONFIG_PGID, config.getPgid(-1));
        assertEquals(CONFIG_SID, config.getSid(-1));
        CapturingSink sink = new CapturingSink();
        AndroidEmulator emulator = null;
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            assertEquals(CONFIG_PID, emulator.getPid());
            TraceEnvironmentEventSink.register(emulator, sink);

            if (!is64Bit) {
                int pgrp = invokeSyscall(emulator, NR_GETPGRP_ARM32, 0);
                assertEquals(CONFIG_PGID, pgrp);
                assertIdentityEvent(findLast(sink.events, "process_identity", "getpgrp"),
                        "getpgrp", CONFIG_PGID, "json-config");
            }

            int pgidNr = is64Bit ? NR_GETPGID_ARM64 : NR_GETPGID_ARM32;
            int sidNr = is64Bit ? NR_GETSID_ARM64 : NR_GETSID_ARM32;

            assertEquals(CONFIG_PGID, invokeSyscall(emulator, pgidNr, 0));
            assertIdentityEvent(findLast(sink.events, "process_identity", "getpgid"),
                    "getpgid", CONFIG_PGID, "json-config");
            assertEquals(CONFIG_PGID, invokeSyscall(emulator, pgidNr, CONFIG_PID));
            assertIdentityEvent(findLast(sink.events, "process_identity", "getpgid"),
                    "getpgid", CONFIG_PGID, "json-config");

            assertEquals(CONFIG_SID, invokeSyscall(emulator, sidNr, 0));
            assertIdentityEvent(findLast(sink.events, "process_identity", "getsid"),
                    "getsid", CONFIG_SID, "json-config");
            assertEquals(CONFIG_SID, invokeSyscall(emulator, sidNr, CONFIG_PID));
            assertIdentityEvent(findLast(sink.events, "process_identity", "getsid"),
                    "getsid", CONFIG_SID, "json-config");
        } finally {
            try {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                }
            } finally {
                if (emulator != null) {
                    emulator.close();
                }
            }
        }
    }

    private static void runStatFields(boolean is64Bit, String json, int expectedPgid, int expectedSid)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            String selfStat = readOpenText(emulator, blocks, "/proc/self/stat");
            String pidStat = readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/stat");
            assertStatPgidSid(selfStat, expectedPgid, expectedSid);
            assertStatPgidSid(pidStat, expectedPgid, expectedSid);
        } finally {
            try {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                }
            } finally {
                try {
                    freeAll(blocks);
                } finally {
                    if (emulator != null) {
                        emulator.close();
                    }
                }
            }
        }
    }

    private static void runMissingKeysDefaultToPid(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(MISSING_GROUP_JSON);
        assertEquals(CONFIG_PID, config.getPgid(CONFIG_PID));
        assertEquals(CONFIG_PID, config.getSid(CONFIG_PID));
        assertNotEquals(CONFIG_PPID, config.getPgid(CONFIG_PID));
        assertNotEquals(CONFIG_PPID, config.getSid(CONFIG_PID));

        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            assertEquals(CONFIG_PID, emulator.getPid());
            TraceEnvironmentEventSink.register(emulator, sink);

            int pgidNr = is64Bit ? NR_GETPGID_ARM64 : NR_GETPGID_ARM32;
            int sidNr = is64Bit ? NR_GETSID_ARM64 : NR_GETSID_ARM32;

            if (!is64Bit) {
                assertEquals(CONFIG_PID, invokeSyscall(emulator, NR_GETPGRP_ARM32, 0));
                assertIdentityEvent(findLast(sink.events, "process_identity", "getpgrp"),
                        "getpgrp", CONFIG_PID, "unidbg-default");
            }
            assertEquals(CONFIG_PID, invokeSyscall(emulator, pgidNr, 0));
            assertIdentityEvent(findLast(sink.events, "process_identity", "getpgid"),
                    "getpgid", CONFIG_PID, "unidbg-default");
            assertEquals(CONFIG_PID, invokeSyscall(emulator, sidNr, CONFIG_PID));
            assertIdentityEvent(findLast(sink.events, "process_identity", "getsid"),
                    "getsid", CONFIG_PID, "unidbg-default");
            assertNotEquals(CONFIG_PPID, invokeSyscall(emulator, pgidNr, 0));
            assertNotEquals(CONFIG_PPID, invokeSyscall(emulator, sidNr, 0));

            assertStatPgidSid(readOpenText(emulator, blocks, "/proc/self/stat"),
                    CONFIG_PID, CONFIG_PID);
            assertStatPgidSid(readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/stat"),
                    CONFIG_PID, CONFIG_PID);
        } finally {
            try {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                }
            } finally {
                try {
                    freeAll(blocks);
                } finally {
                    if (emulator != null) {
                        emulator.close();
                    }
                }
            }
        }
    }

    private static void runUnknownPid(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        CapturingSink sink = new CapturingSink();
        AndroidEmulator emulator = null;
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            int pgidNr = is64Bit ? NR_GETPGID_ARM64 : NR_GETPGID_ARM32;
            int sidNr = is64Bit ? NR_GETSID_ARM64 : NR_GETSID_ARM32;
            int pgidRet = invokeSyscall(emulator, pgidNr, UNKNOWN_PID);
            int sidRet = invokeSyscall(emulator, sidNr, UNKNOWN_PID);
            assertNotEquals(CONFIG_PGID, pgidRet);
            assertNotEquals(CONFIG_SID, pgidRet);
            assertNotEquals(CONFIG_PGID, sidRet);
            assertNotEquals(CONFIG_SID, sidRet);
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected process_identity event for unknown pid: " + e.api,
                        "process_identity".equals(e.kind)
                                && ("getpgrp".equals(e.api)
                                || "getpgid".equals(e.api)
                                || "getsid".equals(e.api)));
            }
        } finally {
            try {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                }
            } finally {
                if (emulator != null) {
                    emulator.close();
                }
            }
        }
    }

    private static int invokeSyscall(AndroidEmulator emulator, int nr, int pidArg) {
        Backend backend = emulator.getBackend();
        if (emulator.is32Bit()) {
            backend.reg_write(ArmConst.UC_ARM_REG_R0, pidArg);
            backend.reg_write(ArmConst.UC_ARM_REG_R7, nr);
            backend.reg_write(ArmConst.UC_ARM_REG_R5, 0);
        } else {
            backend.reg_write(Arm64Const.UC_ARM64_REG_X0, pidArg);
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

    private static void assertIdentityEvent(CapturedEvent ev, String api, int expectedValue,
                                            String expectedSource) {
        assertNotNull(ev);
        assertEquals("process_identity", ev.kind);
        assertEquals(api, ev.api);
        assertEquals(expectedSource, ev.source);
        assertEquals(Integer.toString(expectedValue), String.valueOf(ev.value));
        assertTrue("note should be Chinese and mention " + api + ", was: " + ev.note,
                ev.note != null && ev.note.contains("读取") && ev.note.contains(api));
    }

    private static void assertStatPgidSid(String statText, int expectedPgid, int expectedSid) {
        assertTrue(statText.endsWith("\n"));
        String[] fields = parseStatFields(statText.getBytes(StandardCharsets.UTF_8));
        assertEquals(52, fields.length);
        // Linux 1-based field 5 pgrp / field 6 session → 0-based index 4 / 5
        assertEquals(Integer.toString(expectedPgid), fields[4]);
        assertEquals(Integer.toString(expectedSid), fields[5]);
        assertNotEquals(Integer.toString(CONFIG_PPID), fields[4]);
        assertNotEquals(Integer.toString(CONFIG_PPID), fields[5]);
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

    private static String readOpenText(AndroidEmulator emulator, List<MemoryBlock> blocks, String path)
            throws Exception {
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

    private static EmulatorBuilder<AndroidEmulator> emulatorBuilder(boolean is64Bit) {
        return (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                .addBackendFactory(new Unicorn2Factory(true));
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
