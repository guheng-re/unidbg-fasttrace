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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Focused JNI tests for {@code android.inputMethods} on SystemService input_method:
 * getInputMethodList / getEnabledInputMethodList / InputMethodInfo.getId,
 * plus typed {@code Application}/{@code Context.getSystemService(Class)} for
 * {@code InputMethodManager}.
 */
public class AndroidInputMethodsJniTest {

    private static final String IMM_CLASS = "android/view/inputmethod/InputMethodManager";
    private static final String INFO_CLASS = "android/view/inputmethod/InputMethodInfo";
    private static final String LIST_ARGS = "()Ljava/util/List;";
    private static final String GET_ID_ARGS = "()Ljava/lang/String;";

    private static final String FULL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"inputMethods\":["
            + "{\"id\":\"com.demo/.ImeA\",\"enabled\":true},"
            + "{\"id\":\"com.demo/.ImeB\",\"enabled\":false},"
            + "{\"id\":\"com.demo/.ImeC\",\"enabled\":true}"
            + "]"
            + "}"
            + "}";

    private static final String EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"inputMethods\":[]"
            + "}"
            + "}";

    private static final String ABSENT_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testInputMethodsFullVarArg32() throws Exception {
        runConfigured(false, false, FULL_JSON, 3, 2);
    }

    @Test
    public void testInputMethodsFullVaList64() throws Exception {
        runConfigured(true, true, FULL_JSON, 3, 2);
    }

    @Test
    public void testInputMethodsEmptyVarArg32() throws Exception {
        runConfigured(false, false, EMPTY_JSON, 0, 0);
    }

    @Test
    public void testInputMethodsEmptyVaList64() throws Exception {
        runConfigured(true, true, EMPTY_JSON, 0, 0);
    }

    @Test
    public void testInputMethodsAbsentVarArg32() throws Exception {
        runAbsent(false, false);
    }

    @Test
    public void testInputMethodsAbsentVaList64() throws Exception {
        runAbsent(true, true);
    }

    @Test
    public void testInputMethodsTypedGetSystemServiceVarArg32() throws Exception {
        runTypedGetSystemService(false, false);
    }

    @Test
    public void testInputMethodsTypedGetSystemServiceVaList64() throws Exception {
        runTypedGetSystemService(true, true);
    }

    @Test
    public void testInputMethodsCrossVmRejectedVarArg32() throws Exception {
        runCrossVmRejected(false, false);
    }

    @Test
    public void testInputMethodsCrossVmRejectedVaList64() throws Exception {
        runCrossVmRejected(true, true);
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList, String json,
                                      int expectedAll, int expectedEnabled) throws Exception {
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

            DvmObject<?> manager = resolveImm(jni, baseVM, useVaList, vm);
            assertTrue(manager instanceof SystemService);
            assertEquals(SystemService.INPUT_METHOD_SERVICE, manager.getValue());

            ArrayListObject all1 = invokeGetList(jni, baseVM, useVaList, manager, "getInputMethodList");
            ArrayListObject all2 = invokeGetList(jni, baseVM, useVaList, manager, "getInputMethodList");
            assertNotSame(all1, all2);
            assertEquals(expectedAll, all1.getValue().size());
            assertEquals(expectedAll, all2.getValue().size());

            ArrayListObject en1 = invokeGetList(jni, baseVM, useVaList, manager,
                    "getEnabledInputMethodList");
            ArrayListObject en2 = invokeGetList(jni, baseVM, useVaList, manager,
                    "getEnabledInputMethodList");
            assertNotSame(en1, en2);
            assertEquals(expectedEnabled, en1.getValue().size());

            if (expectedAll >= 1) {
                DvmObject<?> info0 = all1.getValue().get(0);
                assertTrue(info0.getValue().getClass().getName()
                        .contains("ConfiguredInputMethodInfo"));
                assertEquals("com.demo/.ImeA", invokeGetId(jni, baseVM, useVaList, info0));
            }
            if (expectedAll >= 3) {
                assertEquals("com.demo/.ImeB",
                        invokeGetId(jni, baseVM, useVaList, all1.getValue().get(1)));
                assertEquals("com.demo/.ImeC",
                        invokeGetId(jni, baseVM, useVaList, all1.getValue().get(2)));
                // enabled list: A then C (JSON order, enabled=true only)
                assertEquals(2, en1.getValue().size());
                assertEquals("com.demo/.ImeA",
                        invokeGetId(jni, baseVM, useVaList, en1.getValue().get(0)));
                assertEquals("com.demo/.ImeC",
                        invokeGetId(jni, baseVM, useVaList, en1.getValue().get(1)));
            }

            // plain IMM / plain InputMethodInfo → UOE
            DvmObject<?> plainImm = vm.resolveClass(IMM_CLASS).newObject(null);
            try {
                invokeGetList(jni, baseVM, useVaList, plainImm, "getInputMethodList");
                fail("expected UOE for plain InputMethodManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInputMethodList"));
            }
            DvmObject<?> plainInfo = vm.resolveClass(INFO_CLASS).newObject(null);
            try {
                invokeGetId(jni, baseVM, useVaList, plainInfo);
                fail("expected UOE for plain InputMethodInfo.getId");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getId"));
            }

            // other SystemService not accepted
            DvmObject<?> audio = new SystemService(vm, SystemService.AUDIO_SERVICE);
            try {
                invokeGetList(jni, baseVM, useVaList, audio, "getInputMethodList");
                fail("expected UOE for wrong SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInputMethodList"));
            }

            assertEquals(2, countEvents(sink.events, "android_input_method",
                    "InputMethodManager.getInputMethodList"));
            assertEquals(2, countEvents(sink.events, "android_input_method",
                    "InputMethodManager.getEnabledInputMethodList"));
            CapturedEvent lastAll = findLastEvent(sink.events, "android_input_method",
                    "InputMethodManager.getInputMethodList");
            assertNotNull(lastAll);
            assertEquals("json-config", lastAll.source);
            assertEquals("count=" + expectedAll, String.valueOf(lastAll.value));
            CapturedEvent lastEn = findLastEvent(sink.events, "android_input_method",
                    "InputMethodManager.getEnabledInputMethodList");
            assertNotNull(lastEn);
            assertEquals("count=" + expectedEnabled, String.valueOf(lastEn.value));

            // getId does not emit sidecar
            for (CapturedEvent e : sink.events) {
                if ("android_input_method".equals(e.kind)) {
                    assertTrue(e.api.equals("InputMethodManager.getInputMethodList")
                            || e.api.equals("InputMethodManager.getEnabledInputMethodList"));
                }
            }
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

            DvmObject<?> manager = resolveImm(jni, baseVM, useVaList, vm);
            try {
                invokeGetList(jni, baseVM, useVaList, manager, "getInputMethodList");
                fail("expected UOE without inputMethods config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInputMethodList"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("android_input_method".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runTypedGetSystemService(boolean is64Bit, boolean useVaList) throws Exception {
        runTypedConfigured(is64Bit, useVaList);
        runTypedAbsent(is64Bit, useVaList);
    }

    private static void runTypedConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_JSON);
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
            DvmClass immClass = vm.resolveClass(IMM_CLASS);

            DvmObject<?> fromString = resolveImm(jni, baseVM, useVaList, vm);
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            int eventsBeforeApp = sink.events.size();
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, immClass);
            assertImmMarker(fromApp);
            assertEquals(fromString.getObjectType().getClassName(), fromApp.getObjectType().getClassName());
            assertEquals(fromString.getValue(), fromApp.getValue());
            assertNoInputMethodSidecarSince(sink, eventsBeforeApp);
            ArrayListObject appList = invokeGetList(jni, baseVM, useVaList, fromApp, "getInputMethodList");
            assertEquals(3, appList.getValue().size());
            assertEquals("com.demo/.ImeA", invokeGetId(jni, baseVM, useVaList, appList.getValue().get(0)));

            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            int eventsBeforeCtx = sink.events.size();
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, immClass);
            assertImmMarker(fromCtx);
            assertNoInputMethodSidecarSince(sink, eventsBeforeCtx);
            ArrayListObject ctxList = invokeGetList(jni, baseVM, useVaList, fromCtx, "getInputMethodList");
            assertEquals(3, ctxList.getValue().size());
            assertEquals("com.demo/.ImeA", invokeGetId(jni, baseVM, useVaList, ctxList.getValue().get(0)));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runTypedAbsent(boolean is64Bit, boolean useVaList) throws Exception {
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
            DvmClass immClass = vm.resolveClass(IMM_CLASS);

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, immClass);
            assertImmMarker(fromApp);
            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, immClass);
            assertImmMarker(fromCtx);
            try {
                invokeGetList(jni, baseVM, useVaList, fromApp, "getInputMethodList");
                fail("expected UOE for getInputMethodList without android.inputMethods");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInputMethodList"));
            }
            try {
                invokeGetList(jni, baseVM, useVaList, fromCtx, "getInputMethodList");
                fail("expected UOE for getInputMethodList without android.inputMethods");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInputMethodList"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("android_input_method".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertImmMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals(IMM_CLASS, manager.getObjectType().getClassName());
        assertEquals(SystemService.INPUT_METHOD_SERVICE, manager.getValue());
    }

    private static void assertNoInputMethodSidecarSince(CapturingSink sink, int fromIndex) {
        for (int i = fromIndex; i < sink.events.size(); i++) {
            assertFalse("unexpected android_input_method event on typed lookup: "
                            + sink.events.get(i).api,
                    "android_input_method".equals(sink.events.get(i).kind));
        }
    }

    private static void runCrossVmRejected(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig shared = TraceEnvironmentConfig.parse(FULL_JSON);
        AndroidEmulator emuA = null;
        AndroidEmulator emuB = null;
        CapturingSink sinkB = new CapturingSink();
        try {
            AndroidEmulatorBuilder builderA = is64Bit
                    ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
            AndroidEmulatorBuilder builderB = is64Bit
                    ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
            emuA = builderA.setEnvironmentConfig(shared).build();
            emuB = builderB.setEnvironmentConfig(shared).build();
            TraceEnvironmentEventSink.register(emuB, sinkB);

            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            VM vmA = emuA.createDalvikVM();
            VM vmB = emuB.createDalvikVM();
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> managerA = resolveImm(jniA, baseA, useVaList, vmA);
            ArrayListObject listA = invokeGetList(jniA, baseA, useVaList, managerA,
                    "getInputMethodList");
            DvmObject<?> infoA = listA.getValue().get(0);

            try {
                invokeGetId(jniB, baseB, useVaList, infoA);
                fail("expected UOE for cross-VM InputMethodInfo.getId");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getId"));
            }
            for (CapturedEvent e : sinkB.events) {
                assertFalse("android_input_method".equals(e.kind));
            }
        } finally {
            if (emuA != null) {
                emuA.close();
            }
            if (emuB != null) {
                TraceEnvironmentEventSink.unregister(emuB, sinkB);
                emuB.close();
            }
        }
    }

    private static DvmObject<?> resolveImm(AbstractJni jni, BaseVM vm, boolean useVaList, VM dalvik) {
        DvmObject<?> app = dalvik.resolveClass("android/app/Application").newObject(null);
        int nameHash = vm.addLocalObject(new StringObject(vm, SystemService.INPUT_METHOD_SERVICE));
        DvmClass dvmClass = app.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/String;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, app, signature,
                    new TestObjectVaList(vm, method, nameHash));
        }
        return jni.callObjectMethod(vm, app, signature, new TestObjectVarArg(vm, method, nameHash));
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
                    new TestObjectVaList(vm, method, classHash));
        }
        return jni.callObjectMethod(vm, receiver, signature,
                new TestObjectVarArg(vm, method, classHash));
    }

    private static ArrayListObject invokeGetList(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> manager, String methodName) {
        DvmObject<?> result = invokeObjectMethod(jni, vm, useVaList, manager, IMM_CLASS,
                methodName, LIST_ARGS);
        assertTrue(result instanceof ArrayListObject);
        return (ArrayListObject) result;
    }

    private static String invokeGetId(AbstractJni jni, BaseVM vm, boolean useVaList,
                                      DvmObject<?> info) {
        DvmObject<?> result = invokeObjectMethod(jni, vm, useVaList, info, INFO_CLASS,
                "getId", GET_ID_ARGS);
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static DvmObject<?> invokeObjectMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   DvmObject<?> receiver, String className,
                                                   String methodName, String args) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, receiver, signature, new TestVarArg(vm, method));
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
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }

    private static final class TestObjectVarArg extends VarArg {
        TestObjectVarArg(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
        }
    }

    private static final class TestObjectVaList extends VaList {
        TestObjectVaList(BaseVM vm, DvmMethod method, int objectHash0) {
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
        public void emitEnvironmentEvent(String kind, String api, Object value, String source,
                                         String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}
