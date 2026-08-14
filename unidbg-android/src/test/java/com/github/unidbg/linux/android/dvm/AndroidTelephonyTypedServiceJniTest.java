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
 * 覆盖类型化 {@code Application}/{@code Context.getSystemService(TelephonyManager.class)}：
 * 返回既有 {@code SystemService} phone 标记；lookup 不发 sidecar；
 * {@code getImei} 等读取仍由 {@code android.telephony} 门控。
 */
public class AndroidTelephonyTypedServiceJniTest {

    private static final String TELEPHONY_MANAGER_CLASS = "android/telephony/TelephonyManager";
    private static final String SLOT0_IMEI = "860000000000001";

    private static final String TELEPHONY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"telephony\":{"
            + "\"phoneCount\":1,"
            + "\"slots\":[{\"slotIndex\":0,\"imei\":\"" + SLOT0_IMEI + "\"}]"
            + "}"
            + "}"
            + "}";

    private static final String NO_TELEPHONY_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testTelephonyTypedGetSystemServiceVarArg32() throws Exception {
        runTypedGetSystemService(false, false);
    }

    @Test
    public void testTelephonyTypedGetSystemServiceVaList64() throws Exception {
        runTypedGetSystemService(true, true);
    }

    private static void runTypedGetSystemService(boolean is64Bit, boolean useVaList) throws Exception {
        runTypedConfigured(is64Bit, useVaList);
        runTypedAbsent(is64Bit, useVaList);
    }

    private static void runTypedConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TELEPHONY_JSON);
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
            DvmClass telephonyClass = vm.resolveClass(TELEPHONY_MANAGER_CLASS);

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            int eventsBeforeApp = sink.events.size();
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, telephonyClass);
            assertTelephonyManagerMarker(fromApp);
            assertNoTelephonySince(sink, eventsBeforeApp);
            assertConfiguredImeiFromMarker(jni, baseVM, useVaList, fromApp, sink);

            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            int eventsBeforeCtx = sink.events.size();
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, telephonyClass);
            assertTelephonyManagerMarker(fromCtx);
            assertNoTelephonySince(sink, eventsBeforeCtx);
            assertConfiguredImeiFromMarker(jni, baseVM, useVaList, fromCtx, sink);

            assertEquals(2, countEvents(sink.events, "telephony", "TelephonyManager.getImei"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runTypedAbsent(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_TELEPHONY_JSON);
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
            DvmClass telephonyClass = vm.resolveClass(TELEPHONY_MANAGER_CLASS);

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, telephonyClass);
            assertTelephonyManagerMarker(fromApp);
            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, telephonyClass);
            assertTelephonyManagerMarker(fromCtx);
            assertGetImeiUnsupported(jni, baseVM, useVaList, fromApp);
            assertGetImeiUnsupported(jni, baseVM, useVaList, fromCtx);
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected telephony sidecar: " + e.api, "telephony".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertTelephonyManagerMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals(TELEPHONY_MANAGER_CLASS, manager.getObjectType().getClassName());
        assertEquals(SystemService.TELEPHONY_SERVICE, manager.getValue());
        assertEquals("phone", manager.getValue());
    }

    private static void assertNoTelephonySince(CapturingSink sink, int fromIndex) {
        for (int i = fromIndex; i < sink.events.size(); i++) {
            assertFalse("unexpected telephony sidecar on typed lookup: " + sink.events.get(i).api,
                    "telephony".equals(sink.events.get(i).kind));
        }
    }

    private static void assertConfiguredImeiFromMarker(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> manager, CapturingSink sink) {
        DvmObject<?> result = invokeNoArgObject(jni, vm, useVaList, manager, "getImei");
        assertTrue(result instanceof StringObject);
        assertEquals(SLOT0_IMEI, ((StringObject) result).getValue());

        CapturedEvent ev = findLastEvent(sink.events, "telephony", "TelephonyManager.getImei");
        assertNotNull(ev);
        assertEquals("json-config", ev.source);
        assertEquals("slot=0,key=imei,value=" + SLOT0_IMEI, String.valueOf(ev.value));
        assertNotNull(ev.note);
        assertFalse(ev.note.isEmpty());
    }

    private static void assertGetImeiUnsupported(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> manager) {
        try {
            invokeNoArgObject(jni, vm, useVaList, manager, "getImei");
            fail("expected UOE for getImei without android.telephony");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("TelephonyManager->getImei"));
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

    private static DvmObject<?> invokeNoArgObject(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  DvmObject<?> target, String methodName) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, "()Ljava/lang/String;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, target, signature, new TestVarArg(vm, method));
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
