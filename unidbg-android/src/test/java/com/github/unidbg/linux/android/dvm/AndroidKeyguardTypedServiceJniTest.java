package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.dvm.api.SystemService;
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
 * 覆盖类型化 {@code Application}/{@code Context.getSystemService(KeyguardManager.class)}：
 * 返回既有 {@code SystemService} keyguard 标记；lookup 不发 sidecar；
 * getter 仍读取 {@code android.securityState}。
 */
public class AndroidKeyguardTypedServiceJniTest {

    /** 四布尔有区分：locked 与 secure / device 字段互不相同，别名只跟 keyguardLocked。 */
    private static final String SECURITY_STATE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"securityState\":{"
            + "\"keyguardLocked\":true,"
            + "\"keyguardSecure\":false,"
            + "\"deviceLocked\":false,"
            + "\"deviceSecure\":true"
            + "}"
            + "}"
            + "}";

    private static final String NO_SECURITY_STATE_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testKeyguardTypedGetSystemServiceVarArg32() throws Exception {
        runTypedGetSystemService(false, false);
    }

    @Test
    public void testKeyguardTypedGetSystemServiceVaList64() throws Exception {
        runTypedGetSystemService(true, true);
    }

    private static void runTypedGetSystemService(boolean is64Bit, boolean useVaList) throws Exception {
        runTypedConfigured(is64Bit, useVaList);
        runTypedAbsent(is64Bit, useVaList);
    }

    private static void runTypedConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SECURITY_STATE_JSON);
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
            DvmClass keyguardClass = vm.resolveClass("android/app/KeyguardManager");

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            int eventsBeforeApp = sink.events.size();
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, keyguardClass);
            assertKeyguardManagerMarker(fromApp);
            assertNoAndroidSecurityStateSince(sink, eventsBeforeApp);
            assertKeyguardGetters(jni, baseVM, useVaList, fromApp, sink);

            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            int eventsBeforeCtx = sink.events.size();
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, keyguardClass);
            assertKeyguardManagerMarker(fromCtx);
            assertNoAndroidSecurityStateSince(sink, eventsBeforeCtx);
            assertKeyguardGetters(jni, baseVM, useVaList, fromCtx, sink);

            assertEquals(2, countEvents(sink.events, "android_security_state",
                    "KeyguardManager.isKeyguardLocked"));
            assertEquals(2, countEvents(sink.events, "android_security_state",
                    "KeyguardManager.inKeyguardRestrictedInputMode"));
            assertEquals(2, countEvents(sink.events, "android_security_state",
                    "KeyguardManager.isKeyguardSecure"));
            assertEquals(2, countEvents(sink.events, "android_security_state",
                    "KeyguardManager.isDeviceLocked"));
            assertEquals(2, countEvents(sink.events, "android_security_state",
                    "KeyguardManager.isDeviceSecure"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runTypedAbsent(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_SECURITY_STATE_JSON);
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
            DvmClass keyguardClass = vm.resolveClass("android/app/KeyguardManager");

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, keyguardClass);
            assertKeyguardManagerMarker(fromApp);
            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, keyguardClass);
            assertKeyguardManagerMarker(fromCtx);
            assertKeyguardGettersUnsupported(jni, baseVM, useVaList, fromApp);
            assertKeyguardGettersUnsupported(jni, baseVM, useVaList, fromCtx);
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_security_state sidecar: " + e.api,
                        "android_security_state".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertKeyguardManagerMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals("android/app/KeyguardManager", manager.getObjectType().getClassName());
        assertEquals(SystemService.KEYGUARD_SERVICE, manager.getValue());
    }

    private static void assertNoAndroidSecurityStateSince(CapturingSink sink, int fromIndex) {
        for (int i = fromIndex; i < sink.events.size(); i++) {
            assertFalse("unexpected android_security_state sidecar on typed lookup: "
                            + sink.events.get(i).api,
                    "android_security_state".equals(sink.events.get(i).kind));
        }
    }

    private static void assertKeyguardGetters(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmObject<?> target, CapturingSink sink) {
        assertEquals(true, invokeNoArgBoolean(jni, vm, useVaList, target, "isKeyguardLocked"));
        assertEquals(true,
                invokeNoArgBoolean(jni, vm, useVaList, target, "inKeyguardRestrictedInputMode"));
        assertEquals(false, invokeNoArgBoolean(jni, vm, useVaList, target, "isKeyguardSecure"));
        assertEquals(false, invokeNoArgBoolean(jni, vm, useVaList, target, "isDeviceLocked"));
        assertEquals(true, invokeNoArgBoolean(jni, vm, useVaList, target, "isDeviceSecure"));

        assertEvent(sink, "KeyguardManager.isKeyguardLocked",
                "field=keyguardLocked,result=true",
                "读取配置的锁屏锁定状态");
        assertEvent(sink, "KeyguardManager.inKeyguardRestrictedInputMode",
                "field=keyguardLocked,result=true",
                "读取配置的锁屏锁定状态（兼容别名）");
        assertEvent(sink, "KeyguardManager.isKeyguardSecure",
                "field=keyguardSecure,result=false",
                "读取配置的安全锁屏状态");
        assertEvent(sink, "KeyguardManager.isDeviceLocked",
                "field=deviceLocked,result=false",
                "读取配置的设备锁定状态");
        assertEvent(sink, "KeyguardManager.isDeviceSecure",
                "field=deviceSecure,result=true",
                "读取配置的设备安全状态");
    }

    private static void assertKeyguardGettersUnsupported(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                         DvmObject<?> target) {
        for (String methodName : new String[] {
                "isKeyguardLocked", "inKeyguardRestrictedInputMode",
                "isKeyguardSecure", "isDeviceLocked", "isDeviceSecure"
        }) {
            try {
                invokeNoArgBoolean(jni, vm, useVaList, target, methodName);
                fail("expected UOE for " + methodName + " without android.securityState");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains(methodName));
            }
        }
    }

    private static DvmObject<?> invokeGetSystemServiceClass(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                            DvmObject<?> receiver, DvmClass serviceClass) {
        int classHash = vm.addLocalObject(serviceClass);
        DvmClass dvmClass = receiver.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/Class;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, signature,
                    new TestVaList(vm, method, classHash));
        }
        return jni.callObjectMethod(vm, receiver, signature,
                new TestVarArg(vm, method, classHash));
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

        TestVarArg(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
        }
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVaList(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
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
