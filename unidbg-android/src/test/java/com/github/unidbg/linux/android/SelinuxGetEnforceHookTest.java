package com.github.unidbg.linux.android;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Focused ARM32/ARM64 tests for {@code libselinux.so!security_getenforce} backed by
 * {@code android.securitySignals.selinuxEnforced}.
 */
public class SelinuxGetEnforceHookTest {

    private static final String ENFORCED_TRUE_JSON =
            "{\"android\":{\"securitySignals\":{\"selinuxEnforced\":true}}}";
    private static final String ENFORCED_FALSE_JSON =
            "{\"android\":{\"securitySignals\":{\"selinuxEnforced\":false}}}";
    private static final String EMPTY_SIGNALS_JSON =
            "{\"android\":{\"securitySignals\":{}}}";
    private static final String ABSENT_JSON =
            "{\"android\":{\"packageName\":\"com.demo.app\"}}";

    @Test
    public void testEnforcedTrueArm32() throws Exception {
        runConfigured(false, ENFORCED_TRUE_JSON, 1);
    }

    @Test
    public void testEnforcedTrueArm64() throws Exception {
        runConfigured(true, ENFORCED_TRUE_JSON, 1);
    }

    @Test
    public void testEnforcedFalseArm32() throws Exception {
        runConfigured(false, ENFORCED_FALSE_JSON, 0);
    }

    @Test
    public void testEnforcedFalseArm64() throws Exception {
        runConfigured(true, ENFORCED_FALSE_JSON, 0);
    }

    @Test
    public void testEmptyNodeDefaultTrueArm32() throws Exception {
        // empty securitySignals → default selinuxEnforced=true → 1
        runConfigured(false, EMPTY_SIGNALS_JSON, 1);
    }

    @Test
    public void testEmptyNodeDefaultTrueArm64() throws Exception {
        runConfigured(true, EMPTY_SIGNALS_JSON, 1);
    }

    @Test
    public void testNodeAbsenceArm32() throws Exception {
        runAbsent(false);
    }

    @Test
    public void testNodeAbsenceArm64() throws Exception {
        runAbsent(true);
    }

    @Test
    public void testLibrarySymbolExactMatchArm32() throws Exception {
        runLibrarySymbolExactMatch(false);
    }

    @Test
    public void testLibrarySymbolExactMatchArm64() throws Exception {
        runLibrarySymbolExactMatch(true);
    }

    @Test
    public void testCrossEmulatorIsolationArm32() throws Exception {
        runIsolation(false);
    }

    @Test
    public void testCrossEmulatorIsolationArm64() throws Exception {
        runIsolation(true);
    }

    private static void runConfigured(boolean is64Bit, String json, int expected) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            Integer result = SelinuxGetEnforceHook.trySecurityGetenforce(emulator);
            assertNotNull(result);
            assertEquals(expected, result.intValue());
            CapturedEvent ev = findLast(sink.events, "linux_security", "security_getenforce");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("result=" + expected, String.valueOf(ev.value));
            assertEquals(1, countApi(sink.events, "security_getenforce"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsent(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ABSENT_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNull(SelinuxGetEnforceHook.trySecurityGetenforce(emulator));
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected security_getenforce event",
                        "linux_security".equals(e.kind) && "security_getenforce".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runLibrarySymbolExactMatch(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ENFORCED_TRUE_JSON);
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            SelinuxGetEnforceHook hook = new SelinuxGetEnforceHook(emulator);
            long old = 0x2000L;
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libc.so",
                    "security_getenforce", old));
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libselinux.so",
                    "is_selinux_enabled", old));
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libselinux.so",
                    "security_setenforce", old));
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libselinux.so",
                    "getenforce", old));
            long hooked = hook.hook(emulator.getSvcMemory(),
                    SelinuxGetEnforceHook.LIBRARY, SelinuxGetEnforceHook.SYMBOL, old);
            assertTrue("expected non-zero hook address for exact libselinux.so!security_getenforce",
                    hooked != 0L);
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runIsolation(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(ENFORCED_TRUE_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(ENFORCED_FALSE_JSON);
        TraceEnvironmentConfig configC = TraceEnvironmentConfig.parse(ABSENT_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        AndroidEmulator emulatorC = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        CapturingSink sinkC = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configB)
                    .build();
            emulatorC = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configC)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            TraceEnvironmentEventSink.register(emulatorC, sinkC);

            assertEquals(1, SelinuxGetEnforceHook.trySecurityGetenforce(emulatorA).intValue());
            assertEquals(0, SelinuxGetEnforceHook.trySecurityGetenforce(emulatorB).intValue());
            assertNull(SelinuxGetEnforceHook.trySecurityGetenforce(emulatorC));

            assertEquals(1, countApi(sinkA.events, "security_getenforce"));
            assertEquals(1, countApi(sinkB.events, "security_getenforce"));
            for (CapturedEvent e : sinkC.events) {
                assertFalse("leak to absent emulator",
                        "linux_security".equals(e.kind) && "security_getenforce".equals(e.api));
            }
            assertEquals("result=1", String.valueOf(
                    findLast(sinkA.events, "linux_security", "security_getenforce").value));
            assertEquals("result=0", String.valueOf(
                    findLast(sinkB.events, "linux_security", "security_getenforce").value));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
            if (emulatorC != null) {
                TraceEnvironmentEventSink.unregister(emulatorC, sinkC);
                emulatorC.close();
            }
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
