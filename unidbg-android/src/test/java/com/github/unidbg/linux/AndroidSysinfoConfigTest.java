package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.arm.ARMEmulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.struct.SysInfo32;
import com.github.unidbg.linux.struct.SysInfo64;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgStructure;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.sun.jna.Pointer;
import org.junit.Test;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Parse + ARM32/ARM64 {@code sysinfo} tests for {@code linux.sysinfo}.
 * Missing node keeps the historical all-zero struct. Configured values never
 * come from the host.
 */
public class AndroidSysinfoConfigTest {

    private static final int NR_SYSINFO_ARM32 = 116;
    private static final int NR_SYSINFO_ARM64 = 179;

    private static final long UPTIME = 384221L;
    private static final long LOAD1 = 65536L;
    private static final long LOAD5 = 58982L;
    private static final long LOAD15 = 52428L;
    private static final long TOTAL_RAM = 1907344L;
    private static final long FREE_RAM = 524288L;
    private static final long SHARED_RAM = 16384L;
    private static final long BUFFER_RAM = 32768L;
    private static final long TOTAL_SWAP = 0L;
    private static final long FREE_SWAP = 0L;
    private static final int PROCS = 412;
    private static final int MEM_UNIT = 4096;

    private static final String CONFIGURED_JSON = "{"
            + "\"linux\":{\"sysinfo\":{"
            + "\"uptime\":" + UPTIME + ","
            + "\"loads\":[" + LOAD1 + "," + LOAD5 + "," + LOAD15 + "],"
            + "\"totalRam\":" + TOTAL_RAM + ","
            + "\"freeRam\":" + FREE_RAM + ","
            + "\"sharedRam\":" + SHARED_RAM + ","
            + "\"bufferRam\":" + BUFFER_RAM + ","
            + "\"totalSwap\":" + TOTAL_SWAP + ","
            + "\"freeSwap\":" + FREE_SWAP + ","
            + "\"procs\":" + PROCS + ","
            + "\"memUnit\":" + MEM_UNIT
            + "}}}";

    @Test
    public void testParseSuccessAndAbsence() {
        TraceEnvironmentConfig configured = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        assertTrue(configured.isLinuxSysinfoConfigured());
        TraceEnvironmentConfig.LinuxSysinfoConfig sys = configured.getLinuxSysinfoConfig();
        assertNotNull(sys);
        assertEquals(UPTIME, sys.getUptime());
        assertArrayEquals(new long[] {LOAD1, LOAD5, LOAD15}, sys.getLoads());
        assertEquals(TOTAL_RAM, sys.getTotalRam());
        assertEquals(FREE_RAM, sys.getFreeRam());
        assertEquals(SHARED_RAM, sys.getSharedRam());
        assertEquals(BUFFER_RAM, sys.getBufferRam());
        assertEquals(TOTAL_SWAP, sys.getTotalSwap());
        assertEquals(FREE_SWAP, sys.getFreeSwap());
        assertEquals(PROCS, sys.getProcs());
        assertEquals(MEM_UNIT, sys.getMemUnit());
        assertTrue(sys.fitsArm32NativeFields());

        TraceEnvironmentConfig arm32Max = TraceEnvironmentConfig.parse(arm32MaxJson());
        assertTrue(arm32Max.getLinuxSysinfoConfig().fitsArm32NativeFields());
        assertEquals((long) Integer.MAX_VALUE, arm32Max.getLinuxSysinfoConfig().getUptime());
        assertEquals(TraceEnvironmentConfig.LINUX_SYSINFO_U32_MAX,
                arm32Max.getLinuxSysinfoConfig().getTotalRam());
        assertEquals(TraceEnvironmentConfig.LINUX_SYSINFO_U32_MAX,
                arm32Max.getLinuxSysinfoConfig().getMemUnit());

        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{\"linux\":{}}");
        assertFalse(missing.isLinuxSysinfoConfigured());
        assertNull(missing.getLinuxSysinfoConfig());

        TraceEnvironmentConfig emptyRoot = TraceEnvironmentConfig.parse("{}");
        assertFalse(emptyRoot.isLinuxSysinfoConfigured());
        assertNull(emptyRoot.getLinuxSysinfoConfig());
    }

    @Test
    public void testInvalidParseRejected() {
        assertInvalid("{\"linux\":{\"sysinfo\":null}}", "linux.sysinfo");
        assertInvalid("{\"linux\":{\"sysinfo\":1}}", "linux.sysinfo");
        assertInvalid("{\"linux\":{\"sysinfo\":[]}}", "linux.sysinfo");
        assertInvalid("{\"linux\":{\"sysinfo\":\"uptime\"}}", "linux.sysinfo");
        assertInvalid("{\"linux\":{\"sysinfo\":{}}}", "linux.sysinfo.uptime");
        assertInvalid("{\"linux\":{\"sysinfo\":{\"uptime\":1,\"extra\":0}}}",
                "linux.sysinfo.extra");
        assertInvalid(configuredWith("\"loads\":[1,2]"), "linux.sysinfo.loads");
        assertInvalid(configuredWith("\"loads\":[1,2,3,4]"), "linux.sysinfo.loads");
        assertInvalid(configuredWith("\"loads\":\"1 2 3\""), "linux.sysinfo.loads");
        assertInvalid(configuredWith("\"loads\":[1,2,1.5]"), "linux.sysinfo.loads[2]");
        assertInvalid(configuredWith("\"uptime\":-1"), "linux.sysinfo.uptime");
        assertInvalid(configuredWith("\"uptime\":\"1\""), "linux.sysinfo.uptime");
        assertInvalid(configuredWith("\"procs\":65536"), "linux.sysinfo.procs");
        assertInvalid(configuredWith("\"procs\":-1"), "linux.sysinfo.procs");
        assertInvalid(configuredWith("\"memUnit\":0"), "linux.sysinfo.memUnit");
        assertInvalid(configuredWith("\"memUnit\":-1"), "linux.sysinfo.memUnit");
        assertInvalid(configuredWith("\"totalRam\":1.5"), "linux.sysinfo.totalRam");
    }

    @Test
    public void testParseRejectsArm32Overflow() {
        assertInvalid(jsonUptime("2147483648"), "linux.sysinfo.uptime");
        assertInvalid(jsonUptime("9223372036854775807"), "linux.sysinfo.uptime");
        assertInvalid(jsonLoads("[4294967296,0,0]"), "linux.sysinfo.loads[0]");
        assertInvalid(jsonLoads("[0,4294967296,0]"), "linux.sysinfo.loads[1]");
        assertInvalid(jsonLoads("[0,0,4294967296]"), "linux.sysinfo.loads[2]");
        assertInvalid(jsonField("totalRam", "4294967296"), "linux.sysinfo.totalRam");
        assertInvalid(jsonField("freeRam", "4294967296"), "linux.sysinfo.freeRam");
        assertInvalid(jsonField("sharedRam", "4294967296"), "linux.sysinfo.sharedRam");
        assertInvalid(jsonField("bufferRam", "4294967296"), "linux.sysinfo.bufferRam");
        assertInvalid(jsonField("totalSwap", "4294967296"), "linux.sysinfo.totalSwap");
        assertInvalid(jsonField("freeSwap", "4294967296"), "linux.sysinfo.freeSwap");
        assertInvalid(jsonField("memUnit", "4294967296"), "linux.sysinfo.memUnit");
        assertInvalid(jsonField("memUnit", "9223372036854775807"), "linux.sysinfo.memUnit");
    }

    @Test
    public void testArm32MaxUnsignedWritesExactBits() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(arm32MaxJson());
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = builder(false).setEnvironmentConfig(config).build();
            MemoryBlock block = emulator.getMemory().malloc(128, true);
            blocks.add(block);
            Pointer ptr = block.getPointer();
            poison(ptr, 128);
            assertEquals(0, invokeSysinfo(emulator, ptr));
            SysInfo32 packed = new SysInfo32(ptr);
            packed.unpack();
            assertEquals(Integer.MAX_VALUE, packed.uptime);
            assertEquals(-1, packed.loads[0]);
            assertEquals(-1, packed.totalRam);
            assertEquals(-1, packed.freeRam);
            assertEquals(-1, packed.sharedRam);
            assertEquals(-1, packed.bufferRam);
            assertEquals(-1, packed.totalSwap);
            assertEquals(-1, packed.freeSwap);
            assertEquals(-1, packed.mem_unit);
            assertEquals(TraceEnvironmentConfig.LINUX_SYSINFO_U32_MAX, packed.totalRam & 0xffffffffL);
            assertEquals(TraceEnvironmentConfig.LINUX_SYSINFO_U32_MAX, packed.mem_unit & 0xffffffffL);
        } finally {
            close(emulator, null, blocks);
        }
    }

    @Test
    public void testConfiguredSysinfoArm32() throws Exception {
        runConfigured(false);
    }

    @Test
    public void testConfiguredSysinfoArm64() throws Exception {
        runConfigured(true);
    }

    @Test
    public void testUnconfiguredKeepsZerosArm32() throws Exception {
        runUnconfiguredZeros(false);
    }

    @Test
    public void testUnconfiguredKeepsZerosArm64() throws Exception {
        runUnconfiguredZeros(true);
    }

    @Test
    public void testStructSizes() {
        assertEquals(64, UnidbgStructure.calculateSize(SysInfo32.class));
        assertEquals(112, UnidbgStructure.calculateSize(SysInfo64.class));
    }

    private static void runConfigured(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = builder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            MemoryBlock block = emulator.getMemory().malloc(128, true);
            blocks.add(block);
            Pointer ptr = block.getPointer();
            poison(ptr, 128);

            int ret = invokeSysinfo(emulator, ptr);
            assertEquals(0, ret);
            if (is64Bit) {
                SysInfo64 packed = new SysInfo64(ptr);
                packed.unpack();
                assertEquals(UPTIME, packed.uptime);
                assertArrayEquals(new long[] {LOAD1, LOAD5, LOAD15}, packed.loads);
                assertEquals(TOTAL_RAM, packed.totalRam);
                assertEquals(FREE_RAM, packed.freeRam);
                assertEquals(SHARED_RAM, packed.sharedRam);
                assertEquals(BUFFER_RAM, packed.bufferRam);
                assertEquals(TOTAL_SWAP, packed.totalSwap);
                assertEquals(FREE_SWAP, packed.freeSwap);
                assertEquals(PROCS, packed.procs);
                assertEquals(0, packed.pad);
                assertEquals(0L, packed.totalHigh);
                assertEquals(0L, packed.freeHigh);
                assertEquals(MEM_UNIT, packed.mem_unit);
            } else {
                SysInfo32 packed = new SysInfo32(ptr);
                packed.unpack();
                assertEquals((int) UPTIME, packed.uptime);
                assertArrayEquals(new int[] {(int) LOAD1, (int) LOAD5, (int) LOAD15}, packed.loads);
                assertEquals((int) TOTAL_RAM, packed.totalRam);
                assertEquals((int) FREE_RAM, packed.freeRam);
                assertEquals((int) SHARED_RAM, packed.sharedRam);
                assertEquals((int) BUFFER_RAM, packed.bufferRam);
                assertEquals((int) TOTAL_SWAP, packed.totalSwap);
                assertEquals((int) FREE_SWAP, packed.freeSwap);
                assertEquals(PROCS, packed.procs);
                assertEquals(0, packed.pad);
                assertEquals(0, packed.totalHigh);
                assertEquals(0, packed.freeHigh);
                assertEquals(MEM_UNIT, packed.mem_unit);
            }

            CapturedEvent ev = findLast(sink.events, "linux_sys", "sysinfo");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("uptime=" + UPTIME
                            + ",loads=" + LOAD1 + "/" + LOAD5 + "/" + LOAD15
                            + ",totalRam=" + TOTAL_RAM
                            + ",freeRam=" + FREE_RAM
                            + ",sharedRam=" + SHARED_RAM
                            + ",bufferRam=" + BUFFER_RAM
                            + ",totalSwap=" + TOTAL_SWAP
                            + ",freeSwap=" + FREE_SWAP
                            + ",procs=" + PROCS
                            + ",memUnit=" + MEM_UNIT,
                    String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("0x"));
            assertEquals(1, countApi(sink.events, "sysinfo"));
        } finally {
            close(emulator, sink, blocks);
        }
    }

    private static void runUnconfiguredZeros(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{\"linux\":{}}");
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = builder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            MemoryBlock block = emulator.getMemory().malloc(128, true);
            blocks.add(block);
            Pointer ptr = block.getPointer();
            poison(ptr, 128);

            assertEquals(0, invokeSysinfo(emulator, ptr));
            byte[] after = ptr.getByteArray(0, is64Bit ? 112 : 64);
            for (int i = 0; i < after.length; i++) {
                assertEquals("offset " + i + " should stay zero", 0, after[i]);
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected sysinfo event",
                        "linux_sys".equals(e.kind) && "sysinfo".equals(e.api));
            }
        } finally {
            close(emulator, sink, blocks);
        }
    }

    private static String arm32MaxJson() {
        long u32 = TraceEnvironmentConfig.LINUX_SYSINFO_U32_MAX;
        return "{"
                + "\"linux\":{\"sysinfo\":{"
                + "\"uptime\":" + Integer.MAX_VALUE + ","
                + "\"loads\":[" + u32 + "," + u32 + "," + u32 + "],"
                + "\"totalRam\":" + u32 + ","
                + "\"freeRam\":" + u32 + ","
                + "\"sharedRam\":" + u32 + ","
                + "\"bufferRam\":" + u32 + ","
                + "\"totalSwap\":" + u32 + ","
                + "\"freeSwap\":" + u32 + ","
                + "\"procs\":65535,"
                + "\"memUnit\":" + u32
                + "}}}";
    }

    private static String jsonUptime(String uptime) {
        return jsonField("uptime", uptime);
    }

    private static String jsonLoads(String loadsArray) {
        return "{"
                + "\"linux\":{\"sysinfo\":{"
                + "\"uptime\":" + UPTIME + ","
                + "\"loads\":" + loadsArray + ","
                + "\"totalRam\":" + TOTAL_RAM + ","
                + "\"freeRam\":" + FREE_RAM + ","
                + "\"sharedRam\":" + SHARED_RAM + ","
                + "\"bufferRam\":" + BUFFER_RAM + ","
                + "\"totalSwap\":" + TOTAL_SWAP + ","
                + "\"freeSwap\":" + FREE_SWAP + ","
                + "\"procs\":" + PROCS + ","
                + "\"memUnit\":" + MEM_UNIT
                + "}}}";
    }

    private static String jsonField(String name, String value) {
        return "{"
                + "\"linux\":{\"sysinfo\":{"
                + "\"uptime\":" + (name.equals("uptime") ? value : String.valueOf(UPTIME)) + ","
                + "\"loads\":[" + LOAD1 + "," + LOAD5 + "," + LOAD15 + "],"
                + "\"totalRam\":" + (name.equals("totalRam") ? value : String.valueOf(TOTAL_RAM)) + ","
                + "\"freeRam\":" + (name.equals("freeRam") ? value : String.valueOf(FREE_RAM)) + ","
                + "\"sharedRam\":" + (name.equals("sharedRam") ? value : String.valueOf(SHARED_RAM)) + ","
                + "\"bufferRam\":" + (name.equals("bufferRam") ? value : String.valueOf(BUFFER_RAM)) + ","
                + "\"totalSwap\":" + (name.equals("totalSwap") ? value : String.valueOf(TOTAL_SWAP)) + ","
                + "\"freeSwap\":" + (name.equals("freeSwap") ? value : String.valueOf(FREE_SWAP)) + ","
                + "\"procs\":" + PROCS + ","
                + "\"memUnit\":" + (name.equals("memUnit") ? value : String.valueOf(MEM_UNIT))
                + "}}}";
    }

    private static String configuredWith(String replacementField) {
        return "{"
                + "\"linux\":{\"sysinfo\":{"
                + "\"uptime\":" + UPTIME + ","
                + "\"loads\":[" + LOAD1 + "," + LOAD5 + "," + LOAD15 + "],"
                + "\"totalRam\":" + TOTAL_RAM + ","
                + "\"freeRam\":" + FREE_RAM + ","
                + "\"sharedRam\":" + SHARED_RAM + ","
                + "\"bufferRam\":" + BUFFER_RAM + ","
                + "\"totalSwap\":" + TOTAL_SWAP + ","
                + "\"freeSwap\":" + FREE_SWAP + ","
                + "\"procs\":" + PROCS + ","
                + "\"memUnit\":" + MEM_UNIT + ","
                + replacementField
                + "}}}";
    }

    private static void assertInvalid(String json, String pathFragment) {
        try {
            TraceEnvironmentConfig.parse(json);
            fail("expected IllegalArgumentException containing " + pathFragment);
        } catch (IllegalArgumentException e) {
            assertTrue("message should mention " + pathFragment + ", was: " + e.getMessage(),
                    e.getMessage() != null && e.getMessage().contains(pathFragment));
        }
    }

    private static AndroidEmulatorBuilder builder(boolean is64Bit) {
        return is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
    }

    private static int invokeSysinfo(AndroidEmulator emulator, Pointer info) {
        Backend backend = emulator.getBackend();
        long peer = com.github.unidbg.pointer.UnidbgPointer.nativeValue(info);
        if (emulator.is32Bit()) {
            backend.reg_write(ArmConst.UC_ARM_REG_R0, peer);
            backend.reg_write(ArmConst.UC_ARM_REG_R7, NR_SYSINFO_ARM32);
            backend.reg_write(ArmConst.UC_ARM_REG_R5, 0);
        } else {
            backend.reg_write(Arm64Const.UC_ARM64_REG_X0, peer);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X8, NR_SYSINFO_ARM64);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X16, 0L);
        }
        AndroidSyscallHandler handler = (AndroidSyscallHandler) emulator.getSyscallHandler();
        handler.hook(backend, ARMEmulator.EXCP_SWI, 0, emulator);
        if (emulator.is32Bit()) {
            return backend.reg_read(ArmConst.UC_ARM_REG_R0).intValue();
        }
        return backend.reg_read(Arm64Const.UC_ARM64_REG_X0).intValue();
    }

    private static void poison(Pointer ptr, int length) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 0xaa);
        ptr.write(0, bytes, 0, bytes.length);
    }

    private static void close(AndroidEmulator emulator, CapturingSink sink, List<MemoryBlock> blocks)
            throws Exception {
        if (emulator != null && sink != null) {
            TraceEnvironmentEventSink.unregister(emulator, sink);
        }
        for (int i = blocks.size() - 1; i >= 0; i--) {
            try {
                blocks.get(i).free();
            } catch (Throwable ignored) {
                // best-effort
            }
        }
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

        CapturedEvent(String kind, String api, Object value, String source) {
            this.kind = kind;
            this.api = api;
            this.value = value;
            this.source = source;
        }
    }

    private static final class CapturingSink implements TraceEnvironmentEventSink {
        final List<CapturedEvent> events = new ArrayList<CapturedEvent>();

        @Override
        public void emitEnvironmentEvent(String kind, String api, Object value, String source,
                                         String note) {
            events.add(new CapturedEvent(kind, api, value, source));
        }
    }
}
