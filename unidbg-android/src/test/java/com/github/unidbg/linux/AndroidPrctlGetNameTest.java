package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Focused tests for {@code prctl(PR_GET_NAME)} on ARM32/ARM64:
 * {@code process.threadName} with current Java thread-name fallback,
 * at most 15 UTF-8 bytes + NUL (no mid-code-point split), no sidecar.
 */
public class AndroidPrctlGetNameTest {

    private static final int PR_GET_NAME = 16;
    private static final int TASK_COMM_BUF = 16; // 15 UTF-8 bytes + NUL
    private static final String CONFIGURED_NAME = "cfg_thread";
    private static final String LONG_ASCII = "0123456789abcdef"; // 16 ASCII → 15 bytes
    private static final String TRUNCATED_ASCII = "0123456789abcde";
    /** 6 CJK chars × 3 UTF-8 bytes = 18 → keep 5 chars = 15 bytes. */
    private static final String LONG_CJK = "一二三四五六";
    private static final String TRUNCATED_CJK = "一二三四五";
    /** Mixed: 1 ASCII + 5 CJK = 1+15=16 → keep 1 ASCII + 4 CJK = 13 bytes. */
    private static final String MIXED = "a一二三四五";
    private static final String TRUNCATED_MIXED = "a一二三四";

    @Test
    public void testGetNameConfigured32() throws Exception {
        runConfigured(false, CONFIGURED_NAME, CONFIGURED_NAME);
    }

    @Test
    public void testGetNameConfigured64() throws Exception {
        runConfigured(true, CONFIGURED_NAME, CONFIGURED_NAME);
    }

    @Test
    public void testGetNameAsciiTruncated32() throws Exception {
        runConfiguredExact16Buffer(false, LONG_ASCII, TRUNCATED_ASCII);
    }

    @Test
    public void testGetNameAsciiTruncated64() throws Exception {
        runConfiguredExact16Buffer(true, LONG_ASCII, TRUNCATED_ASCII);
    }

    @Test
    public void testGetNameCjkUtf8Truncated32() throws Exception {
        runConfiguredExact16Buffer(false, LONG_CJK, TRUNCATED_CJK);
    }

    @Test
    public void testGetNameCjkUtf8Truncated64() throws Exception {
        runConfiguredExact16Buffer(true, LONG_CJK, TRUNCATED_CJK);
    }

    @Test
    public void testGetNameMixedUtf8NoSplit32() throws Exception {
        runConfiguredExact16Buffer(false, MIXED, TRUNCATED_MIXED);
    }

    @Test
    public void testGetNameMixedUtf8NoSplit64() throws Exception {
        runConfiguredExact16Buffer(true, MIXED, TRUNCATED_MIXED);
    }

    @Test
    public void testGetNameFallback32() throws Exception {
        runFallback(false);
    }

    @Test
    public void testGetNameFallback64() throws Exception {
        runFallback(true);
    }

    @Test
    public void testTruncateHelperUtf8Boundaries() {
        assertEquals("", AndroidSyscallHandler.truncatePrctlThreadNameUtf8(""));
        assertEquals("abc", AndroidSyscallHandler.truncatePrctlThreadNameUtf8("abc"));
        assertEquals(TRUNCATED_ASCII, AndroidSyscallHandler.truncatePrctlThreadNameUtf8(LONG_ASCII));
        assertEquals(TRUNCATED_CJK, AndroidSyscallHandler.truncatePrctlThreadNameUtf8(LONG_CJK));
        assertEquals(TRUNCATED_MIXED, AndroidSyscallHandler.truncatePrctlThreadNameUtf8(MIXED));
        byte[] cjk = TRUNCATED_CJK.getBytes(StandardCharsets.UTF_8);
        assertEquals(15, cjk.length);
        // truncated form is valid UTF-8 and a prefix of the original
        assertTrue(LONG_CJK.startsWith(TRUNCATED_CJK));
        assertTrue(MIXED.startsWith(TRUNCATED_MIXED));
    }

    private static void runConfigured(boolean is64Bit, String configName, String expected)
            throws Exception {
        TraceEnvironmentConfig config = parseThreadNameConfig(configName);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);

            MemoryBlock buf = malloc(emulator, blocks, 32);
            int ret = invokePrctlGetName(emulator, buf.getPointer());
            assertEquals(0, ret);
            assertEquals(expected, buf.getPointer().getString(0));
            assertEquals(0, countApiContaining(sink.events, "PR_GET_NAME"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    /**
     * Uses an exact 16-byte buffer (TASK_COMM_LEN). Fills with 0xA5, then verifies
     * written UTF-8 + NUL fit and match expected, with no mid-sequence split.
     */
    private static void runConfiguredExact16Buffer(boolean is64Bit, String configName, String expected)
            throws Exception {
        byte[] expectedUtf8 = expected.getBytes(StandardCharsets.UTF_8);
        assertTrue("expected UTF-8 length must be <= 15, got " + expectedUtf8.length,
                expectedUtf8.length <= 15);

        TraceEnvironmentConfig config = parseThreadNameConfig(configName);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);

            MemoryBlock buf = malloc(emulator, blocks, TASK_COMM_BUF);
            UnidbgPointer ptr = buf.getPointer();
            byte[] poison = new byte[TASK_COMM_BUF];
            Arrays.fill(poison, (byte) 0xA5);
            ptr.write(0, poison, 0, TASK_COMM_BUF);

            int ret = invokePrctlGetName(emulator, ptr);
            assertEquals(0, ret);
            assertEquals(expected, ptr.getString(0));

            byte[] written = ptr.getByteArray(0, TASK_COMM_BUF);
            for (int i = 0; i < expectedUtf8.length; i++) {
                assertEquals("byte[" + i + "]", expectedUtf8[i], written[i]);
            }
            assertEquals("NUL terminator", 0, written[expectedUtf8.length]);
            // remaining slots after NUL stay poison only if we did not write past
            // (setString may only write name+NUL; rest of buffer can stay 0xA5)
            for (int i = expectedUtf8.length + 1; i < TASK_COMM_BUF; i++) {
                assertEquals("unused[" + i + "]", (byte) 0xA5, written[i]);
            }
            assertEquals(0, countApiContaining(sink.events, "PR_GET_NAME"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runFallback(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"process\":{\"processName\":\"com.demo.app\"}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        String javaName = java.lang.Thread.currentThread().getName();
        String expected = AndroidSyscallHandler.truncatePrctlThreadNameUtf8(javaName);
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);

            MemoryBlock buf = malloc(emulator, blocks, TASK_COMM_BUF);
            int ret = invokePrctlGetName(emulator, buf.getPointer());
            assertEquals(0, ret);
            assertEquals(expected, buf.getPointer().getString(0));
            assertEquals(0, countApiContaining(sink.events, "PR_GET_NAME"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static TraceEnvironmentConfig parseThreadNameConfig(String threadName) {
        // Escape for JSON string
        StringBuilder sb = new StringBuilder(threadName.length() * 6);
        for (int i = 0; i < threadName.length(); ) {
            int cp = threadName.codePointAt(i);
            if (cp == '\\' || cp == '"') {
                sb.append('\\').append((char) cp);
            } else if (cp < 0x20) {
                sb.append(String.format("\\u%04x", cp));
            } else if (cp > 0x7E) {
                sb.append(String.format("\\u%04x", cp));
            } else {
                sb.append((char) cp);
            }
            i += Character.charCount(cp);
        }
        return TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"threadName\":\"" + sb + "\"}"
                + "}");
    }

    private static int invokePrctlGetName(AndroidEmulator emulator, UnidbgPointer buffer)
            throws Exception {
        Backend backend = emulator.getBackend();
        if (emulator.is32Bit()) {
            backend.reg_write(ArmConst.UC_ARM_REG_R0, PR_GET_NAME);
            backend.reg_write(ArmConst.UC_ARM_REG_R1, buffer.peer);
            ARM32SyscallHandler handler = (ARM32SyscallHandler) emulator.getSyscallHandler();
            Method m = ARM32SyscallHandler.class.getDeclaredMethod(
                    "prctl", Backend.class, Emulator.class);
            m.setAccessible(true);
            return (Integer) m.invoke(handler, backend, emulator);
        }
        backend.reg_write(Arm64Const.UC_ARM64_REG_X0, PR_GET_NAME);
        backend.reg_write(Arm64Const.UC_ARM64_REG_X1, buffer.peer);
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

    private static int countApiContaining(List<CapturedEvent> events, String fragment) {
        int n = 0;
        for (CapturedEvent e : events) {
            if (e.api != null && String.valueOf(e.api).contains(fragment)) {
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
