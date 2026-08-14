package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Focused ARM32/ARM64 tests for {@code linux.cpu.configuredProcessorCount} /
 * {@code onlineProcessorCount} via {@code sysconf(_SC_NPROCESSORS_CONF|_ONLN)}.
 */
public class AndroidSysconfProcessorCountTest {

    private static final String BOTH_JSON = "{"
            + "\"linux\":{\"cpu\":{"
            + "\"configuredProcessorCount\":8,"
            + "\"onlineProcessorCount\":4"
            + "}}"
            + "}";

    private static final String CONF_ONLY_JSON = "{"
            + "\"linux\":{\"cpu\":{\"configuredProcessorCount\":2}}"
            + "}";

    private static final String ONLN_ONLY_JSON = "{"
            + "\"linux\":{\"cpu\":{\"onlineProcessorCount\":3}}"
            + "}";

    private static final String EMPTY_CPU_JSON = "{\"linux\":{\"cpu\":{}}}";

    private static final String ABSENT_JSON = "{\"linux\":{\"uname\":{\"machine64\":\"aarch64\"}}}";

    private static final String INDEPENDENT_JSON = "{"
            + "\"linux\":{\"cpu\":{"
            + "\"affinityMaskHex\":\"0f\","
            + "\"online\":\"0-1\","
            + "\"configuredProcessorCount\":16,"
            + "\"onlineProcessorCount\":1"
            + "}}"
            + "}";

    @Test
    public void testBothFieldsArm32() throws Exception {
        runBothFields(false);
    }

    @Test
    public void testBothFieldsArm64() throws Exception {
        runBothFields(true);
    }

    @Test
    public void testAbsenceAndOtherNameArm32() throws Exception {
        runAbsenceAndOtherName(false);
    }

    @Test
    public void testAbsenceAndOtherNameArm64() throws Exception {
        runAbsenceAndOtherName(true);
    }

    @Test
    public void testIndependenceArm32() throws Exception {
        runIndependence(false);
    }

    @Test
    public void testIndependenceArm64() throws Exception {
        runIndependence(true);
    }

    @Test
    public void testConstantsAndIsolationArm32() throws Exception {
        runConstantsAndIsolation(false);
    }

    @Test
    public void testConstantsAndIsolationArm64() throws Exception {
        runConstantsAndIsolation(true);
    }

    private static void runBothFields(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(BOTH_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            AndroidSyscallHandler handler = (AndroidSyscallHandler) emulator.getSyscallHandler();

            Long conf = callSysconf(emulator, handler, AndroidSyscallHandler.SC_NPROCESSORS_CONF);
            assertNotNull(conf);
            assertEquals(8L, conf.longValue());
            CapturedEvent confEv = findLast(sink.events, "linux_cpu", "sysconf");
            assertNotNull(confEv);
            assertEquals("json-config", confEv.source);
            assertEquals("field=configuredProcessorCount,result=8", String.valueOf(confEv.value));

            sink.events.clear();
            Long onln = callSysconf(emulator, handler, AndroidSyscallHandler.SC_NPROCESSORS_ONLN);
            assertNotNull(onln);
            assertEquals(4L, onln.longValue());
            CapturedEvent onlnEv = findLast(sink.events, "linux_cpu", "sysconf");
            assertNotNull(onlnEv);
            assertEquals("field=onlineProcessorCount,result=4", String.valueOf(onlnEv.value));
            assertEquals(1, countApi(sink.events, "sysconf"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsenceAndOtherName(boolean is64Bit) throws Exception {
        // node absent
        runNotHandled(is64Bit, ABSENT_JSON, AndroidSyscallHandler.SC_NPROCESSORS_CONF);
        runNotHandled(is64Bit, ABSENT_JSON, AndroidSyscallHandler.SC_NPROCESSORS_ONLN);
        // empty cpu object — fields not present
        runNotHandled(is64Bit, EMPTY_CPU_JSON, AndroidSyscallHandler.SC_NPROCESSORS_CONF);
        runNotHandled(is64Bit, EMPTY_CPU_JSON, AndroidSyscallHandler.SC_NPROCESSORS_ONLN);
        // only conf → onln not handled
        runNotHandled(is64Bit, CONF_ONLY_JSON, AndroidSyscallHandler.SC_NPROCESSORS_ONLN);
        // only onln → conf not handled
        runNotHandled(is64Bit, ONLN_ONLY_JSON, AndroidSyscallHandler.SC_NPROCESSORS_CONF);
        // other sysconf name (e.g. _SC_PAGESIZE = 0x0027 on Bionic) never handled
        runNotHandled(is64Bit, BOTH_JSON, 0x0027);
    }

    private static void runNotHandled(boolean is64Bit, String json, int name) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            AndroidSyscallHandler handler = (AndroidSyscallHandler) emulator.getSyscallHandler();
            Long ret = callSysconf(emulator, handler, name);
            assertNull(ret);
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected sysconf event: " + e.value,
                        "linux_cpu".equals(e.kind) && "sysconf".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIndependence(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(INDEPENDENT_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            AndroidSyscallHandler handler = (AndroidSyscallHandler) emulator.getSyscallHandler();

            // counts do not derive from online="0-1" (2 cpus) or affinity
            assertEquals(16L, callSysconf(emulator, handler, AndroidSyscallHandler.SC_NPROCESSORS_CONF)
                    .longValue());
            assertEquals(1L, callSysconf(emulator, handler, AndroidSyscallHandler.SC_NPROCESSORS_ONLN)
                    .longValue());

            // conf-only and onln-only independence
            assertEquals(2L, callSysconfConfigured(is64Bit, CONF_ONLY_JSON,
                    AndroidSyscallHandler.SC_NPROCESSORS_CONF).longValue());
            assertEquals(3L, callSysconfConfigured(is64Bit, ONLN_ONLY_JSON,
                    AndroidSyscallHandler.SC_NPROCESSORS_ONLN).longValue());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static Long callSysconfConfigured(boolean is64Bit, String json, int name)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            AndroidSyscallHandler handler = (AndroidSyscallHandler) emulator.getSyscallHandler();
            return callSysconf(emulator, handler, name);
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runConstantsAndIsolation(boolean is64Bit) throws Exception {
        // Bionic ABI constants (ARM32 and ARM64 share the same name values)
        assertEquals(0x60, AndroidSyscallHandler.SC_NPROCESSORS_CONF);
        assertEquals(0x61, AndroidSyscallHandler.SC_NPROCESSORS_ONLN);
        assertEquals(96, AndroidSyscallHandler.SC_NPROCESSORS_CONF);
        assertEquals(97, AndroidSyscallHandler.SC_NPROCESSORS_ONLN);

        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(BOTH_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(ABSENT_JSON);
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
            AndroidSyscallHandler handlerA = (AndroidSyscallHandler) emulatorA.getSyscallHandler();
            AndroidSyscallHandler handlerB = (AndroidSyscallHandler) emulatorB.getSyscallHandler();

            assertEquals(8L, callSysconf(emulatorA, handlerA, AndroidSyscallHandler.SC_NPROCESSORS_CONF)
                    .longValue());
            assertEquals(1, countApi(sinkA.events, "sysconf"));

            assertNull(callSysconf(emulatorB, handlerB, AndroidSyscallHandler.SC_NPROCESSORS_CONF));
            for (CapturedEvent e : sinkB.events) {
                assertFalse("leak to VM B",
                        "linux_cpu".equals(e.kind) && "sysconf".equals(e.api));
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

    private static Long callSysconf(AndroidEmulator emulator, AndroidSyscallHandler handler, int name)
            throws Exception {
        Backend backend = emulator.getBackend();
        if (emulator.is64Bit()) {
            backend.reg_write(Arm64Const.UC_ARM64_REG_X0, name);
        } else {
            backend.reg_write(ArmConst.UC_ARM_REG_R0, name);
        }
        // Prefer public-ish package method; fall back via reflection if accessibility differs
        return handler.sysconf(emulator);
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
