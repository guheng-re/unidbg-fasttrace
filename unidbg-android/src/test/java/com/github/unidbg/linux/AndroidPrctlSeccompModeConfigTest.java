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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Focused tests for {@code linux.proc.seccompMode} → {@code prctl(PR_GET_SECCOMP)} on ARM32/ARM64.
 */
public class AndroidPrctlSeccompModeConfigTest {

    /** Linux/Android {@code PR_GET_SECCOMP} (linux/prctl.h). */
    private static final int PR_GET_SECCOMP = 21;
    /** Linux/Android {@code PR_SET_SECCOMP} — must stay unconfigured/unchanged. */
    private static final int PR_SET_SECCOMP = 22;
    private static final int PR_GET_DUMPABLE = 3;

    @Test
    public void testGetSeccompMode0Arm32() throws Exception {
        runConfiguredGet(false, 0);
    }

    @Test
    public void testGetSeccompMode0Arm64() throws Exception {
        runConfiguredGet(true, 0);
    }

    @Test
    public void testGetSeccompMode1Arm32() throws Exception {
        runConfiguredGet(false, 1);
    }

    @Test
    public void testGetSeccompMode1Arm64() throws Exception {
        runConfiguredGet(true, 1);
    }

    @Test
    public void testGetSeccompMode2Arm32() throws Exception {
        runConfiguredGet(false, 2);
    }

    @Test
    public void testGetSeccompMode2Arm64() throws Exception {
        runConfiguredGet(true, 2);
    }

    @Test
    public void testGetSeccompAbsentArm32() throws Exception {
        runAbsent(false);
    }

    @Test
    public void testGetSeccompAbsentArm64() throws Exception {
        runAbsent(true);
    }

    @Test
    public void testSetSeccompUnchangedArm32() throws Exception {
        runSetUnchanged(false);
    }

    @Test
    public void testSetSeccompUnchangedArm64() throws Exception {
        runSetUnchanged(true);
    }

    @Test
    public void testIndependentOfDumpableArm32() throws Exception {
        runIndependentOfDumpable(false);
    }

    @Test
    public void testIndependentOfDumpableArm64() throws Exception {
        runIndependentOfDumpable(true);
    }

    @Test
    public void testCrossEmulatorIsolationArm32() throws Exception {
        runIsolation(false);
    }

    @Test
    public void testCrossEmulatorIsolationArm64() throws Exception {
        runIsolation(true);
    }

    private static void runConfiguredGet(boolean is64Bit, int mode) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"seccompMode\":" + mode + "}}}");
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);

            int ret = invokePrctl(emulator, PR_GET_SECCOMP);
            assertEquals(mode, ret);

            CapturedEvent ev = findLast(sink.events, "linux_proc", "prctl(PR_GET_SECCOMP)");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=seccompMode,result=" + mode, String.valueOf(ev.value));
            assertTrue(ev.note != null && ev.note.contains("seccomp"));
            assertEquals(1, countApi(sink.events, "prctl(PR_GET_SECCOMP)"));
            // exact option: PR_GET_SECCOMP is 21
            assertEquals(21, PR_GET_SECCOMP);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsent(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"state\":\"S\",\"dumpable\":1}}}");
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            try {
                invokePrctl(emulator, PR_GET_SECCOMP);
                fail("expected UnsupportedOperationException when seccompMode absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("option="));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected PR_GET_SECCOMP event",
                        "linux_proc".equals(e.kind) && "prctl(PR_GET_SECCOMP)".equals(e.api));
            }
            // dumpable still works independently
            assertEquals(1, invokePrctl(emulator, PR_GET_DUMPABLE));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSetUnchanged(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"seccompMode\":2}}}");
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            try {
                invokePrctl(emulator, PR_SET_SECCOMP);
                fail("expected UnsupportedOperationException for PR_SET_SECCOMP");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("option="));
            }
            // GET still returns configured value; no SET event
            assertEquals(2, invokePrctl(emulator, PR_GET_SECCOMP));
            assertEquals(1, countApi(sink.events, "prctl(PR_GET_SECCOMP)"));
            assertEquals(0, countApi(sink.events, "prctl(PR_SET_SECCOMP)"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIndependentOfDumpable(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"dumpable\":0,\"seccompMode\":1}}}");
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(0, invokePrctl(emulator, PR_GET_DUMPABLE));
            assertEquals(1, invokePrctl(emulator, PR_GET_SECCOMP));
            assertEquals(1, countApi(sink.events, "prctl(PR_GET_DUMPABLE)"));
            assertEquals(1, countApi(sink.events, "prctl(PR_GET_SECCOMP)"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIsolation(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"seccompMode\":2}}}");
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"state\":\"S\"}}}");
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configB)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);

            assertEquals(2, invokePrctl(emulatorA, PR_GET_SECCOMP));
            assertEquals(1, countApi(sinkA.events, "prctl(PR_GET_SECCOMP)"));

            try {
                invokePrctl(emulatorB, PR_GET_SECCOMP);
                fail("expected UOE on emulator B without seccompMode");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("option="));
            }
            for (CapturedEvent e : sinkB.events) {
                assertFalse("leak to B",
                        "linux_proc".equals(e.kind) && "prctl(PR_GET_SECCOMP)".equals(e.api));
            }
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static int invokePrctl(AndroidEmulator emulator, int option) throws Exception {
        Backend backend = emulator.getBackend();
        if (emulator.is32Bit()) {
            backend.reg_write(ArmConst.UC_ARM_REG_R0, option);
            ARM32SyscallHandler handler = (ARM32SyscallHandler) emulator.getSyscallHandler();
            Method m = ARM32SyscallHandler.class.getDeclaredMethod(
                    "prctl", Backend.class, Emulator.class);
            m.setAccessible(true);
            try {
                return (Integer) m.invoke(handler, backend, emulator);
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
