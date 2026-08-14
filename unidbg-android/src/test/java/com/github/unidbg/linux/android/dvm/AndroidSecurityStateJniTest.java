package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 覆盖 {@code android.securityState} 对 KeyguardManager 无参布尔与
 * {@code BiometricManager.canAuthenticate} / {@code getLastAuthenticationTime} 的 JNI 接线。
 */
public class AndroidSecurityStateJniTest {

    private static final String KEYGUARD_MANAGER_CLASS = "android/app/KeyguardManager";
    private static final String BIOMETRIC_MANAGER_CLASS = "android/hardware/biometrics/BiometricManager";

    private static final String FULL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"securityState\":{"
            + "\"keyguardLocked\":true,"
            + "\"keyguardSecure\":false,"
            + "\"deviceLocked\":true,"
            + "\"deviceSecure\":false,"
            + "\"biometricCanAuthenticateResult\":0,"
            + "\"biometricLastAuthenticationElapsedRealtimeMillis\":1234567890"
            + "}"
            + "}"
            + "}";

    private static final String EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"securityState\":{}"
            + "}"
            + "}";

    private static final String ABSENT_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testSecurityStateFullVarArg32() throws Exception {
        runConfigured(false, false, FULL_JSON, true, false, true, false, 0, 1234567890L);
    }

    @Test
    public void testSecurityStateFullVaList64() throws Exception {
        runConfigured(true, true, FULL_JSON, true, false, true, false, 0, 1234567890L);
    }

    @Test
    public void testSecurityStateEmptyDefaultsVarArg32() throws Exception {
        runConfigured(false, false, EMPTY_JSON, false, false, false, false, 12, -1L);
    }

    @Test
    public void testSecurityStateEmptyDefaultsVaList64() throws Exception {
        runConfigured(true, true, EMPTY_JSON, false, false, false, false, 12, -1L);
    }

    @Test
    public void testSecurityStateAbsentVarArg32() throws Exception {
        runAbsent(false, false);
    }

    @Test
    public void testSecurityStateAbsentVaList64() throws Exception {
        runAbsent(true, true);
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList, String json,
                                      boolean expectedKeyguardLocked, boolean expectedKeyguardSecure,
                                      boolean expectedDeviceLocked, boolean expectedDeviceSecure,
                                      int expectedBiometric, long expectedLastAuth)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> keyguard = vm.resolveClass(KEYGUARD_MANAGER_CLASS).newObject(null);
            assertEquals(expectedKeyguardLocked,
                    invokeNoArgBoolean(jni, baseVM, useVaList, keyguard, "isKeyguardLocked"));
            // 废弃公共别名，与 isKeyguardLocked 同读 keyguardLocked
            assertEquals(expectedKeyguardLocked,
                    invokeNoArgBoolean(jni, baseVM, useVaList, keyguard,
                            "inKeyguardRestrictedInputMode"));
            assertEquals(expectedKeyguardSecure,
                    invokeNoArgBoolean(jni, baseVM, useVaList, keyguard, "isKeyguardSecure"));
            assertEquals(expectedDeviceLocked,
                    invokeNoArgBoolean(jni, baseVM, useVaList, keyguard, "isDeviceLocked"));
            assertEquals(expectedDeviceSecure,
                    invokeNoArgBoolean(jni, baseVM, useVaList, keyguard, "isDeviceSecure"));

            DvmObject<?> biometric = vm.resolveClass(BIOMETRIC_MANAGER_CLASS).newObject(null);
            assertEquals(expectedBiometric,
                    invokeCanAuthenticateNoArg(jni, baseVM, useVaList, biometric));
            assertEquals(expectedBiometric,
                    invokeCanAuthenticateWithAuthenticators(jni, baseVM, useVaList, biometric, 15));
            assertEquals(expectedLastAuth,
                    invokeGetLastAuthenticationTime(jni, baseVM, useVaList, biometric, 255));

            assertEvent(sink, "KeyguardManager.isKeyguardLocked",
                    "field=keyguardLocked,result=" + expectedKeyguardLocked,
                    "读取配置的锁屏锁定状态");
            assertEvent(sink, "KeyguardManager.inKeyguardRestrictedInputMode",
                    "field=keyguardLocked,result=" + expectedKeyguardLocked,
                    "读取配置的锁屏锁定状态（兼容别名）");
            assertEvent(sink, "KeyguardManager.isKeyguardSecure",
                    "field=keyguardSecure,result=" + expectedKeyguardSecure);
            assertEvent(sink, "KeyguardManager.isDeviceLocked",
                    "field=deviceLocked,result=" + expectedDeviceLocked);
            assertEvent(sink, "KeyguardManager.isDeviceSecure",
                    "field=deviceSecure,result=" + expectedDeviceSecure);

            // 无参与 int 重载各一次；分别按 value 形态校验
            assertEquals(2, countEvents(sink.events, "android_security_state",
                    "BiometricManager.canAuthenticate"));
            CapturedEvent bioNoArgEv = null;
            CapturedEvent bioIntEv = null;
            for (CapturedEvent e : sink.events) {
                if ("android_security_state".equals(e.kind)
                        && "BiometricManager.canAuthenticate".equals(e.api)) {
                    String v = String.valueOf(e.value);
                    if (v.startsWith("result=") && !v.contains("authenticators=")) {
                        bioNoArgEv = e;
                    } else if (v.contains("authenticators=")) {
                        bioIntEv = e;
                    }
                }
            }
            assertNotNull(bioNoArgEv);
            assertEquals("json-config", bioNoArgEv.source);
            assertEquals("result=" + expectedBiometric, String.valueOf(bioNoArgEv.value));
            assertNotNull(bioNoArgEv.note);
            assertFalse(bioNoArgEv.note.isEmpty());
            assertNotNull(bioIntEv);
            assertEquals("json-config", bioIntEv.source);
            assertEquals("result=" + expectedBiometric + ",authenticators=15",
                    String.valueOf(bioIntEv.value));

            assertEquals(1, countEvents(sink.events, "android_security_state",
                    "BiometricManager.getLastAuthenticationTime"));
            CapturedEvent lastAuthEv = findLastEvent(sink.events, "android_security_state",
                    "BiometricManager.getLastAuthenticationTime");
            assertNotNull(lastAuthEv);
            assertEquals("json-config", lastAuthEv.source);
            assertEquals("result=" + expectedLastAuth + ",authenticators=255",
                    String.valueOf(lastAuthEv.value));
            assertNotNull(lastAuthEv.note);
            assertFalse(lastAuthEv.note.isEmpty());
            assertTrue(lastAuthEv.note.contains("最近生物识别认证时间"));

            assertEquals(1, countEvents(sink.events, "android_security_state",
                    "KeyguardManager.isKeyguardLocked"));
            assertEquals(1, countEvents(sink.events, "android_security_state",
                    "KeyguardManager.inKeyguardRestrictedInputMode"));
            assertEquals(1, countEvents(sink.events, "android_security_state",
                    "KeyguardManager.isKeyguardSecure"));
            assertEquals(1, countEvents(sink.events, "android_security_state",
                    "KeyguardManager.isDeviceLocked"));
            assertEquals(1, countEvents(sink.events, "android_security_state",
                    "KeyguardManager.isDeviceSecure"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsent(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ABSENT_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> keyguard = vm.resolveClass(KEYGUARD_MANAGER_CLASS).newObject(null);
            for (String methodName : new String[] {
                    "isKeyguardLocked", "inKeyguardRestrictedInputMode",
                    "isKeyguardSecure", "isDeviceLocked", "isDeviceSecure"
            }) {
                try {
                    invokeNoArgBoolean(jni, baseVM, useVaList, keyguard, methodName);
                    fail("expected UnsupportedOperationException for " + methodName
                            + " without securityState");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains(methodName));
                }
            }
            DvmObject<?> biometric = vm.resolveClass(BIOMETRIC_MANAGER_CLASS).newObject(null);
            try {
                invokeCanAuthenticateNoArg(jni, baseVM, useVaList, biometric);
                fail("expected UnsupportedOperationException for canAuthenticate() without securityState");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("canAuthenticate"));
            }
            try {
                invokeCanAuthenticateWithAuthenticators(jni, baseVM, useVaList, biometric, 15);
                fail("expected UnsupportedOperationException for canAuthenticate(I) without securityState");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("canAuthenticate"));
            }
            try {
                invokeGetLastAuthenticationTime(jni, baseVM, useVaList, biometric, 255);
                fail("expected UnsupportedOperationException for getLastAuthenticationTime without securityState");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLastAuthenticationTime"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_security_state event when config absent: " + e.api,
                        "android_security_state".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertEvent(CapturingSink sink, String api, String expectedValue) {
        assertEvent(sink, api, expectedValue, null);
    }

    private static void assertEvent(CapturingSink sink, String api, String expectedValue,
                                    String expectedNote) {
        CapturedEvent ev = findLastEvent(sink.events, "android_security_state", api);
        assertNotNull(ev);
        assertEquals("json-config", ev.source);
        assertEquals(expectedValue, String.valueOf(ev.value));
        assertNotNull(ev.note);
        assertFalse(ev.note.isEmpty());
        if (expectedNote != null) {
            assertEquals(expectedNote, ev.note);
        }
    }

    private static boolean invokeNoArgBoolean(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmObject<?> target, String methodName) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestVarArg(vm, method));
    }

    private static int invokeCanAuthenticateNoArg(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  DvmObject<?> target) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "canAuthenticate", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature, new TestVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, signature, new TestVarArg(vm, method));
    }

    private static int invokeCanAuthenticateWithAuthenticators(AbstractJni jni, BaseVM vm,
                                                               boolean useVaList, DvmObject<?> target,
                                                               int authenticators) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "canAuthenticate", "(I)I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature,
                    new TestVaList(vm, method, authenticators));
        }
        return jni.callIntMethod(vm, target, signature,
                new TestVarArg(vm, method, authenticators));
    }

    private static long invokeGetLastAuthenticationTime(AbstractJni jni, BaseVM vm,
                                                        boolean useVaList, DvmObject<?> target,
                                                        int authenticators) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getLastAuthenticationTime", "(I)J", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callLongMethodV(vm, target, signature,
                    new TestVaList(vm, method, authenticators));
        }
        return jni.callLongMethod(vm, target, signature,
                new TestVarArg(vm, method, authenticators));
    }

    private static CapturedEvent findLastEvent(List<CapturedEvent> events, String kind, String api) {
        CapturedEvent found = null;
        for (CapturedEvent e : events) {
            if (kind.equals(e.kind) && api.equals(e.api)) {
                found = e;
            }
        }
        return found;
    }

    private static int countEvents(List<CapturedEvent> events, String kind, String api) {
        int n = 0;
        for (CapturedEvent e : events) {
            if (kind.equals(e.kind) && api.equals(e.api)) {
                n++;
            }
        }
        return n;
    }

    private static final class TestVarArg extends VarArg {
        TestVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int int0) {
            super(vm, method);
            args.add(int0);
        }
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVaList(BaseVM vm, DvmMethod method, int int0) {
            super(vm, method);
            args.add(int0);
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
        public void emitEnvironmentEvent(String kind, String api, Object value, String source, String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}
