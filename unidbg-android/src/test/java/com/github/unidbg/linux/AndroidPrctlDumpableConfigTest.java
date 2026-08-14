package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Focused tests for {@code linux.proc.dumpable} → {@code prctl(PR_GET_DUMPABLE)} on ARM32/ARM64.
 */
public class AndroidPrctlDumpableConfigTest {

    private static final int PR_GET_DUMPABLE = 3;
    private static final int PR_SET_DUMPABLE = 4;

    @Test
    public void testGetDumpableConfigured32() throws Exception {
        runConfiguredGet(false, 1);
    }

    @Test
    public void testGetDumpableConfigured64() throws Exception {
        runConfiguredGet(true, 2);
    }

    @Test
    public void testGetDumpableZeroConfigured32() throws Exception {
        runConfiguredGet(false, 0);
    }

    @Test
    public void testGetDumpableAbsent32FixedReturn() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"state\":\"S\"}}}");
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for32Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            int ret = invokePrctlGetDumpable(emulator);
            assertEquals(0, ret);
            for (CapturedEvent e : sink.events) {
                assertTrue(!"linux_proc".equals(e.kind)
                        || !"prctl(PR_GET_DUMPABLE)".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testGetDumpableAbsent64Unsupported() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"state\":\"S\"}}}");
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            try {
                invokePrctlGetDumpable(emulator);
                fail("expected UnsupportedOperationException when dumpable absent on ARM64");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("option="));
            }
            for (CapturedEvent e : sink.events) {
                assertTrue(!"linux_proc".equals(e.kind)
                        || !"prctl(PR_GET_DUMPABLE)".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testSetDumpableUnchanged32() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"dumpable\":2}}}");
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for32Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            int ret = invokePrctl(emulator, PR_SET_DUMPABLE);
            assertEquals(0, ret);
            // GET still returns configured value (no mutation from SET)
            assertEquals(2, invokePrctlGetDumpable(emulator));
            assertEquals(1, countApi(sink.events, "prctl(PR_GET_DUMPABLE)"));
            assertEquals(0, countApi(sink.events, "prctl(PR_SET_DUMPABLE)"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredGet(boolean is64Bit, int dumpable) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"dumpable\":" + dumpable + "}}}");
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);

            int ret = invokePrctlGetDumpable(emulator);
            assertEquals(dumpable, ret);

            CapturedEvent ev = findLast(sink.events, "linux_proc", "prctl(PR_GET_DUMPABLE)");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("result=" + dumpable, String.valueOf(ev.value));
            assertTrue(ev.note != null && ev.note.contains("dumpable"));
            assertEquals(1, countApi(sink.events, "prctl(PR_GET_DUMPABLE)"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static int invokePrctlGetDumpable(AndroidEmulator emulator) throws Exception {
        return invokePrctl(emulator, PR_GET_DUMPABLE);
    }

    private static int invokePrctl(AndroidEmulator emulator, int option) throws Exception {
        Backend backend = emulator.getBackend();
        if (emulator.is32Bit()) {
            backend.reg_write(ArmConst.UC_ARM_REG_R0, option);
            ARM32SyscallHandler handler = (ARM32SyscallHandler) emulator.getSyscallHandler();
            Method m = ARM32SyscallHandler.class.getDeclaredMethod(
                    "prctl", Backend.class, Emulator.class);
            m.setAccessible(true);
            return (Integer) m.invoke(handler, backend, emulator);
        }
        backend.reg_write(Arm64Const.UC_ARM64_REG_X0, option);
        ARM64SyscallHandler handler = (ARM64SyscallHandler) emulator.getSyscallHandler();
        Method m = ARM64SyscallHandler.class.getDeclaredMethod("prctl", Emulator.class);
        m.setAccessible(true);
        try {
            return (Integer) m.invoke(handler, emulator);
        } catch (java.lang.reflect.InvocationTargetException e) {
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
