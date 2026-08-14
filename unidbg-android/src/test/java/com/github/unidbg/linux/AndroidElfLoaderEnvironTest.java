package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.pointer.UnidbgPointer;
import com.sun.jna.Pointer;
import org.junit.Test;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Loader behavior for optional {@code linux.environ}: absent → exact four built-in defaults;
 * present → replace list (including empty array). Walks TLS → argv → environ without package-private APIs.
 */
public class AndroidElfLoaderEnvironTest {

    private static final String PROCESS_NAME = "com.demo.environ";

    private static final List<String> BUILTIN_DEFAULTS = Arrays.asList(
            "ANDROID_DATA=/data",
            "ANDROID_ROOT=/system",
            "PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin",
            "NO_ADDR_COMPAT_LAYOUT_FIXUP=1"
    );

    @Test
    public void testEnvironAbsentUsesBuiltinDefaults32() throws Exception {
        runAbsent(false);
    }

    @Test
    public void testEnvironAbsentUsesBuiltinDefaults64() throws Exception {
        runAbsent(true);
    }

    @Test
    public void testEnvironConfiguredReplacesDefaults32() throws Exception {
        runConfigured(false);
    }

    @Test
    public void testEnvironConfiguredReplacesDefaults64() throws Exception {
        runConfigured(true);
    }

    @Test
    public void testEnvironEmptyArray32() throws Exception {
        runEmptyArray(false);
    }

    @Test
    public void testEnvironEmptyArray64() throws Exception {
        runEmptyArray(true);
    }

    private static void runAbsent(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"process\":{\"processName\":\"" + PROCESS_NAME + "\"}}");
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setProcessName(PROCESS_NAME)
                    .setEnvironmentConfig(config)
                    .build();
            assertEquals(BUILTIN_DEFAULTS, readEnviron(emulator));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runConfigured(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"" + PROCESS_NAME + "\"},"
                + "\"linux\":{\"environ\":["
                + "\"CUSTOM_A=1\","
                + "\"CUSTOM_B=x=y\","
                + "\"EMPTY=\""
                + "]}}"
        );
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setProcessName(PROCESS_NAME)
                    .setEnvironmentConfig(config)
                    .build();
            List<String> envs = readEnviron(emulator);
            assertEquals(3, envs.size());
            assertEquals("CUSTOM_A=1", envs.get(0));
            assertEquals("CUSTOM_B=x=y", envs.get(1));
            assertEquals("EMPTY=", envs.get(2));
            // must not retain any built-in default entry
            for (String builtin : BUILTIN_DEFAULTS) {
                assertTrue("must not keep builtin " + builtin, !envs.contains(builtin));
            }
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runEmptyArray(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"" + PROCESS_NAME + "\"},"
                + "\"linux\":{\"environ\":[]}"
                + "}");
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setProcessName(PROCESS_NAME)
                    .setEnvironmentConfig(config)
                    .build();
            List<String> envs = readEnviron(emulator);
            assertTrue(envs.isEmpty());
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    /**
     * Walk TLS → argv → environ pointer array (null-terminated) without package-private APIs.
     */
    private static List<String> readEnviron(Emulator<?> emulator) {
        Backend backend = emulator.getBackend();
        long tlsPeer = emulator.is32Bit()
                ? backend.reg_read(ArmConst.UC_ARM_REG_C13_C0_3).longValue()
                : backend.reg_read(Arm64Const.UC_ARM64_REG_TPIDR_EL0).longValue();
        UnidbgPointer tls = UnidbgPointer.pointer(emulator, tlsPeer);
        assertNotNull(tls);
        int ps = emulator.getPointerSize();
        UnidbgPointer argv = tls.getPointer(3L * ps);
        assertNotNull(argv);
        UnidbgPointer environ = argv.getPointer(2L * ps);
        assertNotNull(environ);

        List<String> out = new ArrayList<String>();
        for (int i = 0; i < 256; i++) {
            Pointer entry = environ.getPointer((long) i * ps);
            if (entry == null) {
                break;
            }
            out.add(entry.getString(0));
        }
        return out;
    }
}
