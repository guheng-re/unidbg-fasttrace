package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.arm.ARMEmulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.sun.jna.Pointer;
import org.junit.Test;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Focused ARM32/ARM64 tests for read-only {@code capget} V3 backed by
 * {@code linux.proc.capInheritableHex}/{@code capPermittedHex}/{@code capEffectiveHex}.
 * Covers direct {@code tryCapget} and real syscall dispatch (ARM32 NR=184, ARM64 NR=90).
 */
public class AndroidCapgetConfigTest {

    private static final int CONFIG_PID = 4242;
    /** Sample 64-bit masks: high 32 | low 32. */
    private static final String INH = "0000000a0000000b";
    private static final String PRM = "0000001fffffffff";
    private static final String EFF = "abcdef01000000ff";
    private static final int V3 = AndroidSyscallHandler.LINUX_CAPABILITY_VERSION_3;
    private static final int V1 = 0x19980330;
    private static final int DATA_SIZE = 24; // 2 * 12-byte cap_user_data_struct
    /** Linux arm {@code __NR_capget}. */
    private static final int NR_CAPGET_ARM32 = 184;
    /** Linux aarch64 {@code __NR_capget}. */
    private static final int NR_CAPGET_ARM64 = 90;

    @Test
    public void testCapgetLayoutArm32() throws Exception {
        runLayout(false);
    }

    @Test
    public void testCapgetLayoutArm64() throws Exception {
        runLayout(true);
    }

    @Test
    public void testPartialConfigArm32() throws Exception {
        runPartial(false);
    }

    @Test
    public void testPartialConfigArm64() throws Exception {
        runPartial(true);
    }

    @Test
    public void testAbsenceArm32() throws Exception {
        runAbsence(false);
    }

    @Test
    public void testAbsenceArm64() throws Exception {
        runAbsence(true);
    }

    @Test
    public void testInvalidPidAndVersionArm32() throws Exception {
        runInvalidPidVersion(false);
    }

    @Test
    public void testInvalidPidAndVersionArm64() throws Exception {
        runInvalidPidVersion(true);
    }

    @Test
    public void testNoRawMaskInSidecarArm32() throws Exception {
        runNoRawSidecar(false);
    }

    @Test
    public void testNoRawMaskInSidecarArm64() throws Exception {
        runNoRawSidecar(true);
    }

    @Test
    public void testSyscallDispatchLayoutArm32() throws Exception {
        runSyscallDispatchLayout(false);
    }

    @Test
    public void testSyscallDispatchLayoutArm64() throws Exception {
        runSyscallDispatchLayout(true);
    }

    @Test
    public void testSyscallDispatchFallbackArm32() throws Exception {
        runSyscallDispatchFallback(false);
    }

    @Test
    public void testSyscallDispatchFallbackArm64() throws Exception {
        runSyscallDispatchFallback(true);
    }

    private static void runLayout(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{"
                + "\"capInheritableHex\":\"" + INH + "\","
                + "\"capPermittedHex\":\"" + PRM + "\","
                + "\"capEffectiveHex\":\"" + EFF + "\""
                + "}}}"
        );
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            assertEquals(CONFIG_PID, emulator.getPid());
            TraceEnvironmentEventSink.register(emulator, sink);

            // pid=0 (self)
            CapBuffers buf0 = allocateBuffers(emulator, blocks, V3, 0);
            Integer rc0 = invokeCapget(emulator, buf0.header, buf0.data);
            assertEquals(Integer.valueOf(0), rc0);
            assertCapSlots(buf0.data, EFF, PRM, INH);

            // pid=configured
            CapBuffers bufPid = allocateBuffers(emulator, blocks, V3, CONFIG_PID);
            Integer rcPid = invokeCapget(emulator, bufPid.header, bufPid.data);
            assertEquals(Integer.valueOf(0), rcPid);
            assertCapSlots(bufPid.data, EFF, PRM, INH);

            CapturedEvent ev = findLast(sink.events, "linux_proc", "capget");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            String value = String.valueOf(ev.value);
            assertTrue(value.startsWith("result=0,version=3,"));
            assertTrue(value.contains("slots=2"));
            assertTrue(value.contains("fields=inh|prm|eff")
                    || (value.contains("inh") && value.contains("prm") && value.contains("eff")));
            assertFalse(value.contains(INH));
            assertFalse(value.contains(PRM));
            assertFalse(value.contains(EFF));
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static void runPartial(boolean is64Bit) throws Exception {
        // only CapEff configured → inh/prm zeros in slots
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"capEffectiveHex\":\"" + EFF + "\"}}"
                + "}");
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            CapBuffers buf = allocateBuffers(emulator, blocks, V3, 0);
            assertEquals(Integer.valueOf(0), invokeCapget(emulator, buf.header, buf.data));
            assertCapSlots(buf.data, EFF, "0000000000000000", "0000000000000000");
            CapturedEvent ev = findLast(sink.events, "linux_proc", "capget");
            assertNotNull(ev);
            assertEquals("result=0,version=3,pid=0,slots=2,fields=eff", String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains(EFF));
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static void runAbsence(boolean is64Bit) throws Exception {
        for (String json : new String[]{
                "{\"android\":{\"packageName\":\"com.demo\"}}",
                "{\"linux\":{\"proc\":{\"state\":\"S\"}}}",
                // CapBnd/CapAmb alone must not enable capget
                "{\"linux\":{\"proc\":{\"capBoundingHex\":\"0000003fffffffff\","
                        + "\"capAmbientHex\":\"0000000000000000\"}}}"
        }) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
            AndroidEmulator emulator = null;
            CapturingSink sink = new CapturingSink();
            List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
            try {
                emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                TraceEnvironmentEventSink.register(emulator, sink);
                CapBuffers buf = allocateBuffers(emulator, blocks, V3, 0);
                // poison data so we can detect accidental writes
                for (int i = 0; i < DATA_SIZE; i++) {
                    buf.data.setByte(i, (byte) 0x5a);
                }
                assertNull(invokeCapget(emulator, buf.header, buf.data));
                for (int i = 0; i < DATA_SIZE; i++) {
                    assertEquals("data must not change when unhandled",
                            (byte) 0x5a, buf.data.getByte(i));
                }
                for (CapturedEvent e : sink.events) {
                    assertFalse("linux_proc".equals(e.kind) && "capget".equals(e.api));
                }
            } finally {
                cleanup(emulator, sink, blocks);
            }
        }
    }

    private static void runInvalidPidVersion(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{"
                + "\"capEffectiveHex\":\"" + EFF + "\""
                + "}}}"
        );
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);

            // wrong version
            CapBuffers badVer = allocateBuffers(emulator, blocks, V1, 0);
            for (int i = 0; i < DATA_SIZE; i++) {
                badVer.data.setByte(i, (byte) 0x11);
            }
            assertNull(invokeCapget(emulator, badVer.header, badVer.data));
            assertEquals((byte) 0x11, badVer.data.getByte(0));

            // wrong pid
            CapBuffers badPid = allocateBuffers(emulator, blocks, V3, CONFIG_PID + 99);
            for (int i = 0; i < DATA_SIZE; i++) {
                badPid.data.setByte(i, (byte) 0x22);
            }
            assertNull(invokeCapget(emulator, badPid.header, badPid.data));
            assertEquals((byte) 0x22, badPid.data.getByte(0));

            // null header / null data via registers
            Backend backend = emulator.getBackend();
            if (is64Bit) {
                backend.reg_write(Arm64Const.UC_ARM64_REG_X0, 0L);
                backend.reg_write(Arm64Const.UC_ARM64_REG_X1, 0L);
            } else {
                backend.reg_write(ArmConst.UC_ARM_REG_R0, 0);
                backend.reg_write(ArmConst.UC_ARM_REG_R1, 0);
            }
            assertNull(invokeCapgetRaw(emulator));

            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind) && "capget".equals(e.api));
            }
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static void runNoRawSidecar(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{"
                + "\"capInheritableHex\":\"" + INH + "\","
                + "\"capPermittedHex\":\"" + PRM + "\","
                + "\"capEffectiveHex\":\"" + EFF + "\""
                + "}}}"
        );
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            CapBuffers buf = allocateBuffers(emulator, blocks, V3, 0);
            assertEquals(Integer.valueOf(0), invokeCapget(emulator, buf.header, buf.data));
            for (CapturedEvent e : sink.events) {
                if ("capget".equals(e.api)) {
                    String v = String.valueOf(e.value);
                    assertFalse(v.contains(INH));
                    assertFalse(v.contains(PRM));
                    assertFalse(v.contains(EFF));
                    assertFalse(v.contains("abcdef"));
                    assertFalse(v.contains("1fffffff"));
                    if (e.note != null) {
                        assertFalse(e.note.contains(EFF));
                    }
                }
            }
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    /**
     * Real syscall switch path: set NR (R7/X8) + args, {@code hook(EXCP_SWI, swi=0)}.
     * Proves ARM32 case 184 / ARM64 case 90 call the handler, return 0, fill V3 buffers.
     */
    private static void runSyscallDispatchLayout(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{"
                + "\"capInheritableHex\":\"" + INH + "\","
                + "\"capPermittedHex\":\"" + PRM + "\","
                + "\"capEffectiveHex\":\"" + EFF + "\""
                + "}}}"
        );
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            assertEquals(CONFIG_PID, emulator.getPid());
            TraceEnvironmentEventSink.register(emulator, sink);

            CapBuffers buf0 = allocateBuffers(emulator, blocks, V3, 0);
            int ret0 = invokeCapgetSyscall(emulator, buf0.header, buf0.data);
            assertEquals(0, ret0);
            assertCapSlots(buf0.data, EFF, PRM, INH);

            CapBuffers bufPid = allocateBuffers(emulator, blocks, V3, CONFIG_PID);
            int retPid = invokeCapgetSyscall(emulator, bufPid.header, bufPid.data);
            assertEquals(0, retPid);
            assertCapSlots(bufPid.data, EFF, PRM, INH);

            assertEquals(2, countApi(sink.events, "capget"));
            CapturedEvent ev = findLast(sink.events, "linux_proc", "capget");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            String value = String.valueOf(ev.value);
            assertTrue(value.contains("result=0"));
            assertTrue(value.contains("version=3"));
            assertTrue(value.contains("slots=2"));
            assertFalse(value.contains(EFF));
            assertFalse(value.contains(PRM));
            assertFalse(value.contains(INH));

            // NR constants must match production switch cases
            assertEquals(184, NR_CAPGET_ARM32);
            assertEquals(90, NR_CAPGET_ARM64);
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    /**
     * Syscall dispatch when no Inh/Prm/Eff configured: switch branch falls through,
     * buffers unchanged, no capget sidecar.
     */
    private static void runSyscallDispatchFallback(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"state\":\"S\",\"capBoundingHex\":\"0000003fffffffff\"}}}");
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);

            CapBuffers buf = allocateBuffers(emulator, blocks, V3, 0);
            for (int i = 0; i < DATA_SIZE; i++) {
                buf.data.setByte(i, (byte) 0x5a);
            }
            // unhandled path: may log warn; must not rewrite V3 data
            invokeCapgetSyscall(emulator, buf.header, buf.data);
            for (int i = 0; i < DATA_SIZE; i++) {
                assertEquals((byte) 0x5a, buf.data.getByte(i));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind) && "capget".equals(e.api));
            }
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static void assertCapSlots(Pointer data, String effHex, String prmHex, String inhHex) {
        int[] eff = splitMask(effHex);
        int[] prm = splitMask(prmHex);
        int[] inh = splitMask(inhHex);
        // slot 0 = low 32
        assertEquals(eff[0], data.getInt(0));
        assertEquals(prm[0], data.getInt(4));
        assertEquals(inh[0], data.getInt(8));
        // slot 1 = high 32
        assertEquals(eff[1], data.getInt(12));
        assertEquals(prm[1], data.getInt(16));
        assertEquals(inh[1], data.getInt(20));
    }

    /** @return [low32, high32] as signed ints matching guest u32 bits */
    private static int[] splitMask(String hex) {
        int high = (int) Long.parseLong(hex.substring(0, 8), 16);
        int low = (int) Long.parseLong(hex.substring(8, 16), 16);
        return new int[]{low, high};
    }

    private static CapBuffers allocateBuffers(AndroidEmulator emulator, List<MemoryBlock> blocks,
                                              int version, int pid) {
        MemoryBlock headerBlock = emulator.getMemory().malloc(8, true);
        MemoryBlock dataBlock = emulator.getMemory().malloc(DATA_SIZE, true);
        blocks.add(headerBlock);
        blocks.add(dataBlock);
        Pointer header = headerBlock.getPointer();
        Pointer data = dataBlock.getPointer();
        header.setInt(0, version);
        header.setInt(4, pid);
        return new CapBuffers(header, data);
    }

    private static Integer invokeCapget(AndroidEmulator emulator, Pointer header, Pointer data)
            throws Exception {
        writeCapgetArgs(emulator, header, data);
        return invokeCapgetRaw(emulator);
    }

    private static Integer invokeCapgetRaw(AndroidEmulator emulator) throws Exception {
        AndroidSyscallHandler handler = (AndroidSyscallHandler) emulator.getSyscallHandler();
        Method m = AndroidSyscallHandler.class.getDeclaredMethod("tryCapget", Emulator.class);
        m.setAccessible(true);
        return (Integer) m.invoke(handler, emulator);
    }

    /**
     * Dispatch through {@link AndroidSyscallHandler#hook} with {@code EXCP_SWI} and
     * {@code swi=0}, NR in R7 (ARM32) or X8 (ARM64) — same path as guest {@code svc #0}.
     */
    private static int invokeCapgetSyscall(AndroidEmulator emulator, Pointer header, Pointer data) {
        writeCapgetArgs(emulator, header, data);
        Backend backend = emulator.getBackend();
        if (emulator.is32Bit()) {
            backend.reg_write(ArmConst.UC_ARM_REG_R7, NR_CAPGET_ARM32);
            // avoid accidental pre/post-callback paths when NR were ever 0
            backend.reg_write(ArmConst.UC_ARM_REG_R5, 0);
        } else {
            backend.reg_write(Arm64Const.UC_ARM64_REG_X8, NR_CAPGET_ARM64);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X16, 0L);
        }
        AndroidSyscallHandler handler = (AndroidSyscallHandler) emulator.getSyscallHandler();
        // hook() takes the emulator as the InterruptHook user object
        handler.hook(backend, ARMEmulator.EXCP_SWI, 0, emulator);
        if (emulator.is32Bit()) {
            return backend.reg_read(ArmConst.UC_ARM_REG_R0).intValue();
        }
        return backend.reg_read(Arm64Const.UC_ARM64_REG_X0).intValue();
    }

    private static void writeCapgetArgs(AndroidEmulator emulator, Pointer header, Pointer data) {
        Backend backend = emulator.getBackend();
        long h = UnidbgPointer.nativeValue(header);
        long d = UnidbgPointer.nativeValue(data);
        if (emulator.is32Bit()) {
            backend.reg_write(ArmConst.UC_ARM_REG_R0, (int) h);
            backend.reg_write(ArmConst.UC_ARM_REG_R1, (int) d);
        } else {
            backend.reg_write(Arm64Const.UC_ARM64_REG_X0, h);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X1, d);
        }
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

    private static void cleanup(AndroidEmulator emulator, CapturingSink sink,
                                List<MemoryBlock> blocks) throws Exception {
        if (emulator != null) {
            TraceEnvironmentEventSink.unregister(emulator, sink);
        }
        for (int i = blocks.size() - 1; i >= 0; i--) {
            try {
                blocks.get(i).free();
            } catch (Throwable ignored) {
                // best-effort
            }
        }
        blocks.clear();
        if (emulator != null) {
            emulator.close();
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

    private static final class CapBuffers {
        final Pointer header;
        final Pointer data;

        CapBuffers(Pointer header, Pointer data) {
            this.header = header;
            this.data = data;
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
