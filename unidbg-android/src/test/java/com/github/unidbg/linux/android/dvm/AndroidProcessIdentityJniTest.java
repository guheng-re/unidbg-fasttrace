package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * {@code Process.myPid}/{@code myTid}/{@code myUid} identity via existing
 * {@code process.pid}/{@code tid}/{@code uid}. VarArg and VaList must match.
 * Does not read host process identity.
 */
public class AndroidProcessIdentityJniTest {

    private static final String PROCESS_JSON = "{"
            + "\"process\":{\"pid\":12345,\"tid\":12346,\"uid\":1000}"
            + "}";

    @Test
    public void testConfiguredIdentityVarArg32() throws Exception {
        runConfigured(false, false);
    }

    @Test
    public void testConfiguredIdentityVaList64() throws Exception {
        runConfigured(true, true);
    }

    @Test
    public void testAbsentConfigVarArg32() throws Exception {
        runAbsent(false, false);
    }

    @Test
    public void testAbsentConfigVaList64() throws Exception {
        runAbsent(true, true);
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROCESS_JSON);
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            assertEquals(12345, invokeStaticInt(jni, baseVM, useVaList, "myPid"));
            assertEquals(12346, invokeStaticInt(jni, baseVM, useVaList, "myTid"));
            assertEquals(1000, invokeStaticInt(jni, baseVM, useVaList, "myUid"));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runAbsent(boolean is64Bit, boolean useVaList) throws Exception {
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .build();
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            int emulatorPid = emulator.getPid();
            assertEquals(emulatorPid, invokeStaticInt(jni, baseVM, useVaList, "myPid"));
            assertEquals(emulatorPid, invokeStaticInt(jni, baseVM, useVaList, "myTid"));
            assertEquals(0, invokeStaticInt(jni, baseVM, useVaList, "myUid"));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static int invokeStaticInt(AbstractJni jni, BaseVM vm, boolean useVaList, String methodName) {
        DvmClass processClass = vm.resolveClass("android/os/Process");
        DvmMethod method = new DvmMethod(processClass, methodName, "()I", true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticIntMethodV(vm, processClass, signature, new TestVaList(vm, method));
        }
        return jni.callStaticIntMethod(vm, processClass, signature, new TestVarArg(vm, method));
    }

    private static final class TestVarArg extends VarArg {
        TestVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }
}
