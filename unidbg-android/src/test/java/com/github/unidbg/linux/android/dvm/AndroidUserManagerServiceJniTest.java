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
 * 覆盖 {@code Context.USER_SERVICE}、{@code Application.getSystemService("user")}
 * 与类型化 {@code Application}/{@code Context.getSystemService(UserManager.class)}：
 * 返回同一 {@code SystemService} user 标记；lookup 不发 sidecar；
 * 真正的 UserManager 读取仍由 {@code android.userState} 门控。
 */
public class AndroidUserManagerServiceJniTest {

    private static final String USER_MANAGER_CLASS = "android/os/UserManager";
    private static final String USER_HANDLE_CLASS = "android/os/UserHandle";
    private static final String GET_SERIAL_ARGS = "(Landroid/os/UserHandle;)J";

    private static final int USER_ID = 7;
    private static final long SERIAL_NUMBER = 99L;

    private static final String USER_STATE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"userState\":{"
            + "\"userId\":" + USER_ID + ","
            + "\"serialNumber\":" + SERIAL_NUMBER + ","
            + "\"userUnlocked\":false,"
            + "\"systemUser\":false,"
            + "\"managedProfile\":true,"
            + "\"demoUser\":true"
            + "}"
            + "}"
            + "}";

    private static final String NO_USER_STATE_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testUserManagerServiceVarArg32() throws Exception {
        runUserManagerService(false, false);
    }

    @Test
    public void testUserManagerServiceVaList64() throws Exception {
        runUserManagerService(true, true);
    }

    private static void runUserManagerService(boolean is64Bit, boolean useVaList) throws Exception {
        runConfigured(is64Bit, useVaList);
        runAbsent(is64Bit, useVaList);
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(USER_STATE_JSON);
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

            DvmClass contextClass = vm.resolveClass("android/content/Context");
            int eventsBeforeField = sink.events.size();
            DvmObject<?> serviceName = jni.getStaticObjectField(baseVM, contextClass,
                    "android/content/Context->USER_SERVICE:Ljava/lang/String;");
            assertTrue(serviceName instanceof StringObject);
            assertEquals(SystemService.USER_SERVICE, ((StringObject) serviceName).getValue());
            assertEquals("user", ((StringObject) serviceName).getValue());
            assertNoAndroidUserSince(sink, eventsBeforeField);

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            DvmClass userManagerClass = vm.resolveClass(USER_MANAGER_CLASS);

            int eventsBeforeStringApp = sink.events.size();
            DvmObject<?> fromStringApp = invokeGetSystemServiceString(jni, baseVM, useVaList, app,
                    SystemService.USER_SERVICE);
            assertUserManagerMarker(fromStringApp);
            assertNoAndroidUserSince(sink, eventsBeforeStringApp);

            int eventsBeforeTypedApp = sink.events.size();
            DvmObject<?> fromTypedApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app,
                    userManagerClass);
            assertUserManagerMarker(fromTypedApp);
            assertNoAndroidUserSince(sink, eventsBeforeTypedApp);

            int eventsBeforeTypedCtx = sink.events.size();
            DvmObject<?> fromTypedCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context,
                    userManagerClass);
            assertUserManagerMarker(fromTypedCtx);
            assertNoAndroidUserSince(sink, eventsBeforeTypedCtx);

            DvmObject<?> handle = invokeMyUserHandle(jni, baseVM, useVaList);
            assertNotNull(handle);
            assertTrue(handle.getValue().getClass().getName().contains("ConfiguredUserHandle"));

            assertConfiguredUserManagerFromMarker(jni, baseVM, useVaList, fromStringApp, handle, sink);
            assertConfiguredUserManagerFromMarker(jni, baseVM, useVaList, fromTypedApp, handle, sink);
            assertConfiguredUserManagerFromMarker(jni, baseVM, useVaList, fromTypedCtx, handle, sink);

            assertEquals(3, countEvents(sink.events, "android_user", "UserManager.isUserUnlocked"));
            assertEquals(3, countEvents(sink.events, "android_user", "UserManager.isSystemUser"));
            assertEquals(3, countEvents(sink.events, "android_user", "UserManager.isManagedProfile"));
            assertEquals(3, countEvents(sink.events, "android_user", "UserManager.isDemoUser"));
            assertEquals(3, countEvents(sink.events, "android_user",
                    "UserManager.getSerialNumberForUser"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsent(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_USER_STATE_JSON);
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

            DvmClass contextClass = vm.resolveClass("android/content/Context");
            DvmObject<?> serviceName = jni.getStaticObjectField(baseVM, contextClass,
                    "android/content/Context->USER_SERVICE:Ljava/lang/String;");
            assertTrue(serviceName instanceof StringObject);
            assertEquals("user", ((StringObject) serviceName).getValue());

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            DvmClass userManagerClass = vm.resolveClass(USER_MANAGER_CLASS);

            DvmObject<?> fromStringApp = invokeGetSystemServiceString(jni, baseVM, useVaList, app,
                    SystemService.USER_SERVICE);
            assertUserManagerMarker(fromStringApp);
            DvmObject<?> fromTypedApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app,
                    userManagerClass);
            assertUserManagerMarker(fromTypedApp);
            DvmObject<?> fromTypedCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context,
                    userManagerClass);
            assertUserManagerMarker(fromTypedCtx);

            assertUserManagerGettersUnsupported(jni, baseVM, useVaList, fromStringApp);
            assertUserManagerGettersUnsupported(jni, baseVM, useVaList, fromTypedApp);
            assertUserManagerGettersUnsupported(jni, baseVM, useVaList, fromTypedCtx);
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_user sidecar: " + e.api,
                        "android_user".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertUserManagerMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals(USER_MANAGER_CLASS, manager.getObjectType().getClassName());
        assertEquals(SystemService.USER_SERVICE, manager.getValue());
        assertEquals("user", manager.getValue());
    }

    private static void assertNoAndroidUserSince(CapturingSink sink, int fromIndex) {
        for (int i = fromIndex; i < sink.events.size(); i++) {
            assertFalse("unexpected android_user sidecar on lookup: " + sink.events.get(i).api,
                    "android_user".equals(sink.events.get(i).kind));
        }
    }

    private static void assertConfiguredUserManagerFromMarker(AbstractJni jni, BaseVM vm,
                                                              boolean useVaList,
                                                              DvmObject<?> manager,
                                                              DvmObject<?> handle,
                                                              CapturingSink sink) {
        assertEquals(false, invokeNoArgBoolean(jni, vm, useVaList, manager, "isUserUnlocked"));
        CapturedEvent unlocked = findLastEvent(sink.events, "android_user",
                "UserManager.isUserUnlocked");
        assertNotNull(unlocked);
        assertEquals("json-config", unlocked.source);
        assertEquals("result=false", String.valueOf(unlocked.value));
        assertNotNull(unlocked.note);
        assertFalse(unlocked.note.isEmpty());

        assertEquals(false, invokeNoArgBoolean(jni, vm, useVaList, manager, "isSystemUser"));
        CapturedEvent systemUser = findLastEvent(sink.events, "android_user",
                "UserManager.isSystemUser");
        assertNotNull(systemUser);
        assertEquals("json-config", systemUser.source);
        assertEquals("result=false", String.valueOf(systemUser.value));

        assertEquals(true, invokeNoArgBoolean(jni, vm, useVaList, manager, "isManagedProfile"));
        CapturedEvent managed = findLastEvent(sink.events, "android_user",
                "UserManager.isManagedProfile");
        assertNotNull(managed);
        assertEquals("json-config", managed.source);
        assertEquals("result=true", String.valueOf(managed.value));

        assertEquals(true, invokeNoArgBoolean(jni, vm, useVaList, manager, "isDemoUser"));
        CapturedEvent demoUser = findLastEvent(sink.events, "android_user",
                "UserManager.isDemoUser");
        assertNotNull(demoUser);
        assertEquals("json-config", demoUser.source);
        assertEquals("result=true", String.valueOf(demoUser.value));
        assertNotNull(demoUser.note);
        assertFalse(demoUser.note.isEmpty());

        assertEquals(SERIAL_NUMBER,
                invokeGetSerialNumberForUser(jni, vm, useVaList, manager, handle));
        CapturedEvent serial = findLastEvent(sink.events, "android_user",
                "UserManager.getSerialNumberForUser");
        assertNotNull(serial);
        assertEquals("json-config", serial.source);
        assertEquals("result=" + SERIAL_NUMBER, String.valueOf(serial.value));
        assertNotNull(serial.note);
        assertFalse(serial.note.isEmpty());
    }

    private static void assertUserManagerGettersUnsupported(AbstractJni jni, BaseVM vm,
                                                            boolean useVaList,
                                                            DvmObject<?> manager) {
        for (String methodName : new String[] {
                "isUserUnlocked", "isSystemUser", "isManagedProfile", "isDemoUser"
        }) {
            try {
                invokeNoArgBoolean(jni, vm, useVaList, manager, methodName);
                fail("expected UOE for " + methodName + " without android.userState");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains(methodName));
            }
        }
        DvmObject<?> plain = vm.resolveClass(USER_HANDLE_CLASS).newObject(null);
        try {
            invokeGetSerialNumberForUser(jni, vm, useVaList, manager, plain);
            fail("expected UOE for getSerialNumberForUser without android.userState");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getSerialNumberForUser"));
        }
    }

    private static DvmObject<?> invokeGetSystemServiceString(AbstractJni jni, BaseVM vm,
                                                             boolean useVaList,
                                                             DvmObject<?> receiver,
                                                             String serviceName) {
        int nameHash = vm.addLocalObject(new StringObject(vm, serviceName));
        DvmClass dvmClass = receiver.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/String;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, signature,
                    new TestVaList(vm, method, nameHash));
        }
        return jni.callObjectMethod(vm, receiver, signature,
                new TestVarArg(vm, method, nameHash));
    }

    private static DvmObject<?> invokeGetSystemServiceClass(AbstractJni jni, BaseVM vm,
                                                            boolean useVaList,
                                                            DvmObject<?> receiver,
                                                            DvmClass serviceClass) {
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

    private static DvmObject<?> invokeMyUserHandle(AbstractJni jni, BaseVM vm, boolean useVaList) {
        DvmClass dvmClass = vm.resolveClass(USER_HANDLE_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "myUserHandle",
                "()Landroid/os/UserHandle;", true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, dvmClass, signature, new TestVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, dvmClass, signature, new TestVarArg(vm, method));
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

    private static long invokeGetSerialNumberForUser(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                     DvmObject<?> userManager, DvmObject<?> handle) {
        int handleHash = handle == null ? 0 : vm.addLocalObject(handle);
        DvmClass dvmClass = userManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSerialNumberForUser", GET_SERIAL_ARGS, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callLongMethodV(vm, userManager, signature,
                    new TestVaList(vm, method, handleHash));
        }
        return jni.callLongMethod(vm, userManager, signature,
                new TestVarArg(vm, method, handleHash));
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
