package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.github.unidbg.unix.UnixEmulator;
import org.junit.Test;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AndroidSchedAffinityConfigTest {

    @Test
    public void testConfiguredAffinity32() throws Exception {
        runConfiguredAffinity(false);
    }

    @Test
    public void testConfiguredAffinity64() throws Exception {
        runConfiguredAffinity(true);
    }

    @Test
    public void testDefaultMaskAndErrors32() throws Exception {
        runDefaultMaskAndErrors(false);
    }

    @Test
    public void testDefaultMaskAndErrors64() throws Exception {
        runDefaultMaskAndErrors(true);
    }

    @Test
    public void testAbsentLegacySetGet32() throws Exception {
        runAbsentLegacySetGet(false);
    }

    @Test
    public void testAbsentLegacySetGet64() throws Exception {
        runAbsentLegacySetGet(true);
    }

    private static void runConfiguredAffinity(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"cpu\":{\"affinityMaskHex\":\"0a0b\"}}}");
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            AndroidSyscallHandler handler = (AndroidSyscallHandler) emulator.getSyscallHandler();

            MemoryBlock buf = malloc(emulator, blocks, 8);
            // poison buffer so zero-fill is observable
            buf.getPointer().write(0, new byte[]{
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff
            }, 0, 8);

            long ret = callGetAffinity(emulator, handler, 0, 8, buf.getPointer());
            assertEquals(8, ret);
            byte[] out = buf.getPointer().getByteArray(0, 8);
            assertArrayEquals(new byte[]{0x0a, 0x0b, 0, 0, 0, 0, 0, 0}, out);

            CapturedEvent ok = findLast(sink.events, "linux_cpu", "sched_getaffinity");
            assertNotNull(ok);
            assertEquals("json-config", ok.source);
            assertTrue(String.valueOf(ok.value).contains("cpusetsize=8"));
            assertTrue(String.valueOf(ok.value).contains("maskHex=0a0b"));
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runDefaultMaskAndErrors(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"cpu\":{}}}");
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            AndroidSyscallHandler handler = (AndroidSyscallHandler) emulator.getSyscallHandler();

            // default affinityMaskHex=01
            MemoryBlock buf = malloc(emulator, blocks, 4);
            buf.getPointer().write(0, new byte[]{9, 9, 9, 9}, 0, 4);
            long ret = callGetAffinity(emulator, handler, 1, 4, buf.getPointer());
            assertEquals(4, ret);
            assertArrayEquals(new byte[]{0x01, 0, 0, 0}, buf.getPointer().getByteArray(0, 4));

            // short buffer (mask is 1 byte, cpusetsize 0 invalid; use 2-byte mask)
            TraceEnvironmentConfig twoByte = TraceEnvironmentConfig.parse(
                    "{\"linux\":{\"cpu\":{\"affinityMaskHex\":\"0102\"}}}");
            AndroidEmulator emulator2 = null;
            CapturingSink sink2 = new CapturingSink();
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(twoByte)
                        .build();
                TraceEnvironmentEventSink.register(emulator2, sink2);
                AndroidSyscallHandler h2 = (AndroidSyscallHandler) emulator2.getSyscallHandler();
                MemoryBlock shortBuf = emulator2.getMemory().malloc(1, true);
                try {
                    shortBuf.getPointer().write(0, new byte[]{(byte) 0xaa}, 0, 1);
                    long shortRet = callGetAffinity(emulator2, h2, 0, 1, shortBuf.getPointer());
                    assertEquals(-UnixEmulator.EINVAL, shortRet);
                    // must not write
                    assertEquals((byte) 0xaa, shortBuf.getPointer().getByte(0));
                    CapturedEvent err = findLast(sink2.events, "linux_cpu", "sched_getaffinity");
                    assertNotNull(err);
                    assertTrue(String.valueOf(err.value).contains("EINVAL"));
                    assertTrue(String.valueOf(err.value).contains("cpusetsize_too_small"));
                } finally {
                    shortBuf.free();
                }

                assertEquals(-UnixEmulator.EINVAL,
                        callGetAffinity(emulator2, h2, 0, 0, null));
                assertEquals(-UnixEmulator.EINVAL,
                        callGetAffinity(emulator2, h2, 0, 1025, null));
                assertEquals(-UnixEmulator.EFAULT,
                        callGetAffinity(emulator2, h2, 0, 4, null));
                CapturedEvent fault = findLast(sink2.events, "linux_cpu", "sched_getaffinity");
                assertNotNull(fault);
                assertTrue(String.valueOf(fault.value).contains("EFAULT")
                        || String.valueOf(fault.value).contains("null_mask"));
            } finally {
                if (emulator2 != null) {
                    TraceEnvironmentEventSink.unregister(emulator2, sink2);
                    emulator2.close();
                }
            }
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentLegacySetGet(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"process\":{\"processName\":\"com.demo.app\"}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            AndroidSyscallHandler handler = (AndroidSyscallHandler) emulator.getSyscallHandler();

            MemoryBlock setBuf = malloc(emulator, blocks, 4);
            setBuf.getPointer().write(0, new byte[]{0x11, 0x22, 0x33, 0x44}, 0, 4);
            long setRet = callSetAffinity(emulator, handler, 0, 4, setBuf.getPointer());
            assertEquals(0, setRet);

            MemoryBlock getBuf = malloc(emulator, blocks, 4);
            getBuf.getPointer().write(0, new byte[]{0, 0, 0, 0}, 0, 4);
            long getRet = callGetAffinity(emulator, handler, 0, 4, getBuf.getPointer());
            assertEquals(4, getRet);
            assertArrayEquals(new byte[]{0x11, 0x22, 0x33, 0x44},
                    getBuf.getPointer().getByteArray(0, 4));
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static long callGetAffinity(Emulator<?> emulator, AndroidSyscallHandler handler,
                                        int pid, int cpusetsize, UnidbgPointer mask) {
        writeArgs(emulator, pid, cpusetsize, mask);
        return handler.sched_getaffinity((Emulator) emulator);
    }

    private static long callSetAffinity(Emulator<?> emulator, AndroidSyscallHandler handler,
                                        int pid, int cpusetsize, UnidbgPointer mask) {
        writeArgs(emulator, pid, cpusetsize, mask);
        return handler.sched_setaffinity((Emulator) emulator);
    }

    private static void writeArgs(Emulator<?> emulator, int pid, int cpusetsize, UnidbgPointer mask) {
        Backend backend = emulator.getBackend();
        long maskPeer = mask == null ? 0L : mask.peer;
        if (emulator.is32Bit()) {
            backend.reg_write(ArmConst.UC_ARM_REG_R0, pid);
            backend.reg_write(ArmConst.UC_ARM_REG_R1, cpusetsize);
            backend.reg_write(ArmConst.UC_ARM_REG_R2, (int) maskPeer);
        } else {
            backend.reg_write(Arm64Const.UC_ARM64_REG_X0, pid);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X1, cpusetsize);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X2, maskPeer);
        }
    }

    private static MemoryBlock malloc(AndroidEmulator emulator, List<MemoryBlock> blocks, int size) {
        MemoryBlock block = emulator.getMemory().malloc(size, true);
        blocks.add(block);
        return block;
    }

    private static void freeAll(List<MemoryBlock> blocks) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            blocks.get(i).free();
        }
        blocks.clear();
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
        public void emitEnvironmentEvent(String kind, String api, Object value, String source, String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}
