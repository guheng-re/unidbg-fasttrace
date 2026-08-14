package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.arm.ARMEmulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.pointer.UnidbgPointer;
import org.junit.Test;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AndroidElfLoaderAuxvTest {

    private static final int AT_NULL = 0;
    private static final int AT_PAGESZ = 6;
    private static final int AT_PLATFORM = 15;
    private static final int AT_HWCAP = 16;
    private static final int AT_RANDOM = 25;
    private static final int AT_HWCAP2 = 26;
    private static final int AT_EXECFN = 31;

    private static final String PROCESS_NAME = "com.demo.auxv";

    @Test
    public void testAuxvConfigured32() throws Exception {
        runConfiguredAuxv(false);
    }

    @Test
    public void testAuxvConfigured64() throws Exception {
        runConfiguredAuxv(true);
    }

    @Test
    public void testAuxvAbsent32() throws Exception {
        runAbsentAuxv(false);
    }

    @Test
    public void testAuxvAbsent64() throws Exception {
        runAbsentAuxv(true);
    }

    @Test
    public void testAuxvExecFnNullFallback32() throws Exception {
        runExecFnNullFallback(false);
    }

    @Test
    public void testAuxvExecFnNullFallback64() throws Exception {
        runExecFnNullFallback(true);
    }

    private static void runConfiguredAuxv(boolean is64Bit) throws Exception {
        String json = is64Bit
                ? "{\"process\":{\"processName\":\"" + PROCESS_NAME + "\"},"
                + "\"linux\":{\"auxv\":{"
                + "\"hwcap64\":" + Long.MAX_VALUE + ","
                + "\"hwcap2_64\":4294967296,"
                + "\"platform64\":\"aarch64\","
                + "\"execFn\":\"/system/bin/app_process64\""
                + "}}}"
                : "{\"process\":{\"processName\":\"" + PROCESS_NAME + "\"},"
                + "\"linux\":{\"auxv\":{"
                + "\"hwcap32\":4294967295,"
                + "\"hwcap2_32\":1,"
                + "\"platform32\":\"v7l\","
                + "\"execFn\":\"/system/bin/app_process32\""
                + "}}}";
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setProcessName(PROCESS_NAME)
                    .setEnvironmentConfig(config)
                    .build();

            Map<Long, Long> pairs = readAuxvPairs(emulator);
            assertEquals(Long.valueOf(ARMEmulator.PAGE_ALIGN), pairs.get((long) AT_PAGESZ));
            assertNotNull(pairs.get((long) AT_RANDOM));
            assertTrue(pairs.get((long) AT_RANDOM) != 0L);

            if (is64Bit) {
                assertEquals(Long.valueOf(Long.MAX_VALUE), pairs.get((long) AT_HWCAP));
                assertEquals(Long.valueOf(0x100000000L), pairs.get((long) AT_HWCAP2));
                assertEquals("aarch64", readCString(emulator, pairs.get((long) AT_PLATFORM)));
                assertEquals("/system/bin/app_process64", readCString(emulator, pairs.get((long) AT_EXECFN)));
            } else {
                assertEquals(Long.valueOf(0xffffffffL), pairs.get((long) AT_HWCAP));
                assertEquals(Long.valueOf(1L), pairs.get((long) AT_HWCAP2));
                assertEquals("v7l", readCString(emulator, pairs.get((long) AT_PLATFORM)));
                assertEquals("/system/bin/app_process32", readCString(emulator, pairs.get((long) AT_EXECFN)));
            }
            assertTrue(pairs.containsKey((long) AT_NULL) || isTerminatedByNull(emulator));
            assertEquals(6, countPairsUntilNull(emulator)); // RANDOM PAGESZ HWCAP HWCAP2 PLATFORM EXECFN then NULL
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runAbsentAuxv(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"process\":{\"processName\":\"" + PROCESS_NAME + "\"}}");
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setProcessName(PROCESS_NAME)
                    .setEnvironmentConfig(config)
                    .build();

            Map<Long, Long> pairs = readAuxvPairs(emulator);
            assertEquals(2, pairs.size());
            assertEquals(Long.valueOf(ARMEmulator.PAGE_ALIGN), pairs.get((long) AT_PAGESZ));
            assertNotNull(pairs.get((long) AT_RANDOM));
            assertTrue(pairs.get((long) AT_RANDOM) != 0L);
            assertNull(pairs.get((long) AT_HWCAP));
            assertNull(pairs.get((long) AT_PLATFORM));
            assertNull(pairs.get((long) AT_EXECFN));
            // zero memory after the two pairs acts as AT_NULL
            assertEquals(2, countPairsUntilNull(emulator));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runExecFnNullFallback(boolean is64Bit) throws Exception {
        String json = "{\"process\":{\"processName\":\"" + PROCESS_NAME + "\"},"
                + "\"linux\":{\"auxv\":{\"execFn\":null}}}";
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setProcessName(PROCESS_NAME)
                    .setEnvironmentConfig(config)
                    .build();

            Map<Long, Long> pairs = readAuxvPairs(emulator);
            assertEquals(PROCESS_NAME, readCString(emulator, pairs.get((long) AT_EXECFN)));
            assertEquals(is64Bit ? "aarch64" : "v7l",
                    readCString(emulator, pairs.get((long) AT_PLATFORM)));
            assertEquals(Long.valueOf(0L), pairs.get((long) AT_HWCAP));
            assertEquals(Long.valueOf(0L), pairs.get((long) AT_HWCAP2));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    /**
     * Walk TLS → argv → auxv without using package-private loader APIs.
     */
    private static UnidbgPointer resolveAuxvPointer(Emulator<?> emulator) {
        Backend backend = emulator.getBackend();
        long tlsPeer = emulator.is32Bit()
                ? backend.reg_read(ArmConst.UC_ARM_REG_C13_C0_3).longValue()
                : backend.reg_read(Arm64Const.UC_ARM64_REG_TPIDR_EL0).longValue();
        UnidbgPointer tls = UnidbgPointer.pointer(emulator, tlsPeer);
        assertNotNull(tls);
        int ps = emulator.getPointerSize();
        UnidbgPointer argv = tls.getPointer(3L * ps);
        assertNotNull(argv);
        UnidbgPointer auxv = argv.getPointer(3L * ps);
        assertNotNull(auxv);
        return auxv;
    }

    private static Map<Long, Long> readAuxvPairs(Emulator<?> emulator) {
        UnidbgPointer auxv = resolveAuxvPointer(emulator);
        int ps = emulator.getPointerSize();
        Map<Long, Long> map = new LinkedHashMap<Long, Long>();
        for (int i = 0; i < 32; i++) {
            long type;
            long value;
            long base = (long) i * 2L * ps;
            if (ps == 4) {
                type = auxv.getInt(base) & 0xffffffffL;
                value = auxv.getInt(base + 4) & 0xffffffffL;
            } else {
                type = auxv.getLong(base);
                value = auxv.getLong(base + 8);
            }
            if (type == AT_NULL) {
                break;
            }
            map.put(type, value);
        }
        return map;
    }

    private static int countPairsUntilNull(Emulator<?> emulator) {
        UnidbgPointer auxv = resolveAuxvPointer(emulator);
        int ps = emulator.getPointerSize();
        int count = 0;
        for (int i = 0; i < 32; i++) {
            long type;
            long base = (long) i * 2L * ps;
            if (ps == 4) {
                type = auxv.getInt(base) & 0xffffffffL;
            } else {
                type = auxv.getLong(base);
            }
            if (type == AT_NULL) {
                return count;
            }
            count++;
        }
        return count;
    }

    private static boolean isTerminatedByNull(Emulator<?> emulator) {
        return countPairsUntilNull(emulator) < 32;
    }

    private static String readCString(Emulator<?> emulator, Long peer) {
        assertNotNull(peer);
        UnidbgPointer p = UnidbgPointer.pointer(emulator, peer);
        assertNotNull(p);
        return p.getString(0);
    }
}
