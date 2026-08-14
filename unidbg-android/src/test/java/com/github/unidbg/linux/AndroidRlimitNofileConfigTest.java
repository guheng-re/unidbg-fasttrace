package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.EmulatorBuilder;
import com.github.unidbg.arm.ARMEmulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.struct.RLimit64;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.sun.jna.Pointer;
import org.junit.Test;
import unicorn.Arm64Const;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Focused ARM64 tests for optional {@code linux.rlimits.nofile}:
 * parse validation and {@code getrlimit64} ({@code NR=163}, {@code RLIMIT_NOFILE=7}).
 * Uses {@link Unicorn2Factory}{@code (true)}. Successful {@code NR=163} calls use real
 * {@code hook(EXCP_SWI, swi=0)}. Unconfigured {@code RLIMIT_NOFILE} invokes the handler
 * method directly (same pattern as prctl UOE tests) so {@code hook}'s {@code emu_stop}
 * does not tear down Unicorn before {@code close}.
 * Does not exercise {@code setrlimit}/{@code prlimit64}/ARM32/other resources.
 * Each helper owns one emulator and closes it in {@code try/finally}: unregister sink,
 * free that helper's {@code MemoryBlock}s, then {@code emulator.close()}.
 */
public class AndroidRlimitNofileConfigTest {

    /** Linux aarch64 {@code __NR_getrlimit}. */
    private static final int NR_GETRLIMIT64 = 163;
    private static final int RLIMIT_STACK = 3;
    private static final int RLIMIT_NOFILE = 7;

    private static final long NOFILE_SOFT = 32768L;
    private static final long NOFILE_HARD = 65536L;

    private static final String CONFIGURED_JSON = "{"
            + "\"linux\":{\"rlimits\":{\"nofile\":{\"soft\":" + NOFILE_SOFT
            + ",\"hard\":" + NOFILE_HARD + "}}}"
            + "}";

    private static final String MISSING_NOFILE_JSON = "{"
            + "\"linux\":{\"rlimits\":{}}"
            + "}";

    @Test
    public void testParseSuccess() {
        TraceEnvironmentConfig configured = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        assertTrue(configured.isLinuxRlimitsConfigured());
        assertTrue(configured.isLinuxRlimitsNofileConfigured());
        TraceEnvironmentConfig.LinuxRlimitsConfig rlimits = configured.getLinuxRlimitsConfig();
        assertNotNull(rlimits);
        assertTrue(rlimits.isNofileConfigured());
        assertEquals(NOFILE_SOFT, rlimits.getNofileSoft());
        assertEquals(NOFILE_HARD, rlimits.getNofileHard());

        TraceEnvironmentConfig equal = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"rlimits\":{\"nofile\":{\"soft\":0,\"hard\":0}}}}");
        assertTrue(equal.isLinuxRlimitsNofileConfigured());
        assertEquals(0L, equal.getLinuxRlimitsConfig().getNofileSoft());
        assertEquals(0L, equal.getLinuxRlimitsConfig().getNofileHard());

        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"rlimits\":{\"nofile\":{\"soft\":0,\"hard\":"
                        + Long.MAX_VALUE + "}}}}");
        assertTrue(max.isLinuxRlimitsNofileConfigured());
        assertEquals(0L, max.getLinuxRlimitsConfig().getNofileSoft());
        assertEquals(Long.MAX_VALUE, max.getLinuxRlimitsConfig().getNofileHard());

        TraceEnvironmentConfig emptyRlimits = TraceEnvironmentConfig.parse(MISSING_NOFILE_JSON);
        assertTrue(emptyRlimits.isLinuxRlimitsConfigured());
        assertFalse(emptyRlimits.isLinuxRlimitsNofileConfigured());
        assertFalse(emptyRlimits.getLinuxRlimitsConfig().isNofileConfigured());

        TraceEnvironmentConfig missingRlimits = TraceEnvironmentConfig.parse("{\"linux\":{}}");
        assertFalse(missingRlimits.isLinuxRlimitsConfigured());
        assertFalse(missingRlimits.isLinuxRlimitsNofileConfigured());
        assertNull(missingRlimits.getLinuxRlimitsConfig());

        TraceEnvironmentConfig emptyRoot = TraceEnvironmentConfig.parse("{}");
        assertFalse(emptyRoot.isLinuxRlimitsConfigured());
        assertFalse(emptyRoot.isLinuxRlimitsNofileConfigured());
        assertNull(emptyRoot.getLinuxRlimitsConfig());
    }

    @Test
    public void testInvalidParseRejected() {
        assertInvalid("{\"linux\":{\"rlimits\":null}}", "linux.rlimits");
        assertInvalid("{\"linux\":{\"rlimits\":1}}", "linux.rlimits");
        assertInvalid("{\"linux\":{\"rlimits\":[]}}", "linux.rlimits");
        assertInvalid("{\"linux\":{\"rlimits\":\"nofile\"}}", "linux.rlimits");
        assertInvalid("{\"linux\":{\"rlimits\":true}}", "linux.rlimits");
        assertInvalid("{\"linux\":{\"rlimits\":{\"stack\":{\"soft\":1,\"hard\":1}}}}",
                "linux.rlimits.stack");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":null}}}", "linux.rlimits.nofile");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":1}}}", "linux.rlimits.nofile");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":[]}}}", "linux.rlimits.nofile");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":\"1024\"}}}", "linux.rlimits.nofile");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":true}}}", "linux.rlimits.nofile");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":{}}}}", "linux.rlimits.nofile.soft");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":{\"hard\":1}}}}",
                "linux.rlimits.nofile.soft");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":{\"soft\":1}}}}",
                "linux.rlimits.nofile.hard");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":{\"soft\":1,\"hard\":2,\"extra\":0}}}}",
                "linux.rlimits.nofile.extra");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":{\"soft\":null,\"hard\":1}}}}",
                "linux.rlimits.nofile.soft");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":{\"soft\":\"1\",\"hard\":1}}}}",
                "linux.rlimits.nofile.soft");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":{\"soft\":true,\"hard\":1}}}}",
                "linux.rlimits.nofile.soft");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":{\"soft\":1.5,\"hard\":2}}}}",
                "linux.rlimits.nofile.soft");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":{\"soft\":-1,\"hard\":1}}}}",
                "linux.rlimits.nofile.soft");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":{\"soft\":1,\"hard\":-1}}}}",
                "linux.rlimits.nofile.hard");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":{\"soft\":2,\"hard\":1}}}}",
                "linux.rlimits.nofile.soft");
        assertInvalid("{\"linux\":{\"rlimits\":{\"nofile\":{\"soft\":9223372036854775808,\"hard\":1}}}}",
                "linux.rlimits.nofile.soft");
    }

    @Test
    public void testConfiguredNofileWritesAndReturnsZero() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        assertTrue(config.isLinuxRlimitsNofileConfigured());
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = emulatorBuilder().setEnvironmentConfig(config).build();
            MemoryBlock block = malloc(emulator, blocks, 16);
            Pointer ptr = block.getPointer();
            byte[] sentinel = new byte[16];
            for (int i = 0; i < sentinel.length; i++) {
                sentinel[i] = (byte) 0xaa;
            }
            ptr.write(0, sentinel, 0, sentinel.length);

            long ret = invokeGetrlimit64(emulator, RLIMIT_NOFILE, ptr);
            assertEquals(0L, ret);
            RLimit64 rlimit64 = new RLimit64(ptr);
            rlimit64.unpack();
            assertEquals(NOFILE_SOFT, rlimit64.rlim_cur);
            assertEquals(NOFILE_HARD, rlimit64.rlim_max);
        } finally {
            cleanup(emulator, null, blocks);
        }
    }

    @Test
    public void testUnconfiguredNofileKeepsUnsupported() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(MISSING_NOFILE_JSON);
        assertFalse(config.isLinuxRlimitsNofileConfigured());
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = emulatorBuilder().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            MemoryBlock block = malloc(emulator, blocks, 16);
            Pointer ptr = block.getPointer();
            byte[] sentinel = new byte[16];
            for (int i = 0; i < sentinel.length; i++) {
                sentinel[i] = (byte) 0xcc;
            }
            ptr.write(0, sentinel, 0, sentinel.length);
            try {
                invokeGetrlimit64Direct(emulator, RLIMIT_NOFILE, ptr);
                fail("expected UnsupportedOperationException for unconfigured RLIMIT_NOFILE");
            } catch (UnsupportedOperationException e) {
                String message = e.getMessage();
                assertTrue("message should mention getrlimit64 resource=7, was: " + message,
                        message != null && message.contains("getrlimit64")
                                && message.contains("resource=" + RLIMIT_NOFILE));
            }
            byte[] after = ptr.getByteArray(0, 16);
            for (int i = 0; i < sentinel.length; i++) {
                assertEquals(sentinel[i], after[i]);
            }
            assertEquals(0, countApi(sink.events, "getrlimit64"));
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    @Test
    public void testRlimitStackReturnsSimulatedStack() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = emulatorBuilder().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            MemoryBlock block = malloc(emulator, blocks, 16);
            Pointer ptr = block.getPointer();
            long ret = invokeGetrlimit64(emulator, RLIMIT_STACK, ptr);
            assertEquals(0L, ret);
            long expected = (long) Memory.STACK_SIZE_OF_PAGE * emulator.getPageAlign();
            RLimit64 rlimit64 = new RLimit64(ptr);
            rlimit64.unpack();
            assertEquals(expected, rlimit64.rlim_cur);
            assertEquals(expected, rlimit64.rlim_max);
            assertFalse(expected == NOFILE_SOFT || expected == NOFILE_HARD);
            assertEquals(0, countApi(sink.events, "getrlimit64"));
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    @Test
    public void testSidecarEventFields() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = emulatorBuilder().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            MemoryBlock block = malloc(emulator, blocks, 16);
            Pointer ptr = block.getPointer();
            long peer = ((UnidbgPointer) ptr).peer;
            long ret = invokeGetrlimit64(emulator, RLIMIT_NOFILE, ptr);
            assertEquals(0L, ret);

            assertEquals(1, sink.events.size());
            CapturedEvent ev = findLast(sink.events, "linux_proc", "getrlimit64");
            assertNotNull(ev);
            assertEquals("linux_proc", ev.kind);
            assertEquals("getrlimit64", ev.api);
            assertEquals("json-config", ev.source);
            String value = String.valueOf(ev.value);
            assertEquals("resource=" + RLIMIT_NOFILE + ",soft=" + NOFILE_SOFT
                    + ",hard=" + NOFILE_HARD, value);
            assertFalse(value.contains("0x"));
            assertFalse(value.contains(Long.toHexString(peer)));
            assertNotNull(ev.note);
            assertTrue("note should be Chinese and mention getrlimit64 RLIMIT_NOFILE, was: "
                            + ev.note,
                    ev.note.contains("读取") && ev.note.contains("getrlimit64")
                            && ev.note.contains("RLIMIT_NOFILE"));
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static long invokeGetrlimit64(AndroidEmulator emulator, int resource, Pointer ptr) {
        prepareGetrlimit64Regs(emulator, resource, ptr);
        Backend backend = emulator.getBackend();
        AndroidSyscallHandler handler = (AndroidSyscallHandler) emulator.getSyscallHandler();
        handler.hook(backend, ARMEmulator.EXCP_SWI, 0, emulator);
        return backend.reg_read(Arm64Const.UC_ARM64_REG_X0).longValue();
    }

    /**
     * Call {@code getrlimit64} without {@code hook()} so a thrown UOE does not
     * {@code emu_stop()} the Unicorn backend before {@code close()}.
     */
    private static long invokeGetrlimit64Direct(AndroidEmulator emulator, int resource, Pointer ptr)
            throws Exception {
        prepareGetrlimit64Regs(emulator, resource, ptr);
        ARM64SyscallHandler handler = (ARM64SyscallHandler) emulator.getSyscallHandler();
        Method method = ARM64SyscallHandler.class.getDeclaredMethod("getrlimit64", Emulator.class);
        method.setAccessible(true);
        try {
            Object ret = method.invoke(handler, emulator);
            return ((Number) ret).longValue();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UnsupportedOperationException) {
                throw (UnsupportedOperationException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw e;
        }
    }

    private static void prepareGetrlimit64Regs(AndroidEmulator emulator, int resource, Pointer ptr) {
        Backend backend = emulator.getBackend();
        long ptrPeer = ptr == null ? 0L : ((UnidbgPointer) ptr).peer;
        backend.reg_write(Arm64Const.UC_ARM64_REG_X0, resource);
        backend.reg_write(Arm64Const.UC_ARM64_REG_X1, ptrPeer);
        backend.reg_write(Arm64Const.UC_ARM64_REG_X8, NR_GETRLIMIT64);
        backend.reg_write(Arm64Const.UC_ARM64_REG_X16, 0L);
    }

    private static MemoryBlock malloc(AndroidEmulator emulator, List<MemoryBlock> blocks, int size) {
        MemoryBlock block = emulator.getMemory().malloc(size, true);
        blocks.add(block);
        return block;
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

    private static EmulatorBuilder<AndroidEmulator> emulatorBuilder() {
        return AndroidEmulatorBuilder.for64Bit().addBackendFactory(new Unicorn2Factory(true));
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
