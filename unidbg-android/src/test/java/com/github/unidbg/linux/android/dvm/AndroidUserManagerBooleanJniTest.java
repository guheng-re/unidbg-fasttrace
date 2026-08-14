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

public class AndroidUserManagerBooleanJniTest {

    private static final String USER_MANAGER_CLASS = "android/os/UserManager";
    private static final String USER_HANDLE_CLASS = "android/os/UserHandle";
    private static final String IS_USER_UNLOCKED_HANDLE_ARGS = "(Landroid/os/UserHandle;)Z";

    private static final String FULL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"userState\":{"
            + "\"userId\":7,"
            + "\"userUnlocked\":false,"
            + "\"systemUser\":false,"
            + "\"managedProfile\":true,"
            + "\"demoUser\":true"
            + "}"
            + "}"
            + "}";

    private static final String EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"userState\":{}"
            + "}"
            + "}";

    private static final String ABSENT_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testUserManagerBooleanFullVarArg32() throws Exception {
        runConfigured(false, false, FULL_JSON, false, false, true, true, 7);
    }

    @Test
    public void testUserManagerBooleanFullVaList64() throws Exception {
        runConfigured(true, true, FULL_JSON, false, false, true, true, 7);
    }

    @Test
    public void testUserManagerBooleanEmptyDefaultsVarArg32() throws Exception {
        runConfigured(false, false, EMPTY_JSON, true, true, false, false, 0);
    }

    @Test
    public void testUserManagerBooleanEmptyDefaultsVaList64() throws Exception {
        runConfigured(true, true, EMPTY_JSON, true, true, false, false, 0);
    }

    @Test
    public void testUserManagerBooleanAbsentVarArg32() throws Exception {
        runAbsent(false, false);
    }

    @Test
    public void testUserManagerBooleanAbsentVaList64() throws Exception {
        runAbsent(true, true);
    }

    /**
     * Same TraceEnvironmentConfig on two emulators: ConfiguredUserHandle from A must not authorize B's
     * {@code isUserUnlocked(UserHandle)} overload.
     */
    @Test
    public void testIsUserUnlockedHandleCrossEmulatorRejectedVarArg32() throws Exception {
        runIsUserUnlockedHandleCrossEmulatorRejected(false, false);
    }

    @Test
    public void testIsUserUnlockedHandleCrossEmulatorRejectedVaList64() throws Exception {
        runIsUserUnlockedHandleCrossEmulatorRejected(true, true);
    }

    private static void runIsUserUnlockedHandleCrossEmulatorRejected(boolean is64Bit, boolean useVaList)
            throws Exception {
        final int userId = 10;
        final boolean userUnlocked = false;
        TraceEnvironmentConfig shared = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"packageName\":\"com.demo.app\","
                + "\"userState\":{"
                + "\"userId\":" + userId + ","
                + "\"userUnlocked\":" + userUnlocked
                + "}"
                + "}"
                + "}");
        AndroidEmulator emuA = null;
        AndroidEmulator emuB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            AndroidEmulatorBuilder builderA = is64Bit
                    ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
            AndroidEmulatorBuilder builderB = is64Bit
                    ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
            emuA = builderA.setEnvironmentConfig(shared).build();
            emuB = builderB.setEnvironmentConfig(shared).build();
            TraceEnvironmentEventSink.register(emuA, sinkA);
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

            DvmObject<?> markerFromA = invokeMyUserHandle(jniA, baseA, useVaList);
            assertNotNull(markerFromA);

            DvmObject<?> managerB = vmB.resolveClass(USER_MANAGER_CLASS).newObject(null);
            try {
                invokeIsUserUnlockedWithHandle(jniB, baseB, useVaList, managerB, markerFromA);
                fail("expected UnsupportedOperationException for cross-emulator ConfiguredUserHandle");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isUserUnlocked"));
            }
            for (CapturedEvent e : sinkB.events) {
                assertFalse("B must not emit isUserUnlocked(UserHandle) for foreign marker",
                        "android_user".equals(e.kind)
                                && "UserManager.isUserUnlocked(UserHandle)".equals(e.api));
            }
            assertEquals(0, countEvents(sinkB.events, "android_user",
                    "UserManager.isUserUnlocked(UserHandle)"));

            // Owner A still succeeds with the same marker and overload
            DvmObject<?> managerA = vmA.resolveClass(USER_MANAGER_CLASS).newObject(null);
            assertEquals(userUnlocked,
                    invokeIsUserUnlockedWithHandle(jniA, baseA, useVaList, managerA, markerFromA));
            CapturedEvent unlockedA = findLastEvent(sinkA.events, "android_user",
                    "UserManager.isUserUnlocked(UserHandle)");
            assertNotNull(unlockedA);
            assertEquals("json-config", unlockedA.source);
            assertEquals("userId=" + userId + ",result=" + userUnlocked,
                    String.valueOf(unlockedA.value));
            assertEquals(1, countEvents(sinkA.events, "android_user",
                    "UserManager.isUserUnlocked(UserHandle)"));
        } finally {
            if (emuA != null) {
                TraceEnvironmentEventSink.unregister(emuA, sinkA);
                emuA.close();
            }
            if (emuB != null) {
                TraceEnvironmentEventSink.unregister(emuB, sinkB);
                emuB.close();
            }
        }
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList, String json,
                                      boolean expectedUnlocked, boolean expectedSystemUser,
                                      boolean expectedManagedProfile, boolean expectedDemoUser,
                                      int expectedUserId)
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

            DvmObject<?> userManager = vm.resolveClass(USER_MANAGER_CLASS).newObject(null);
            assertEquals(expectedUnlocked,
                    invokeNoArgBoolean(jni, baseVM, useVaList, userManager, "isUserUnlocked"));
            assertEquals(expectedSystemUser,
                    invokeNoArgBoolean(jni, baseVM, useVaList, userManager, "isSystemUser"));
            assertEquals(expectedManagedProfile,
                    invokeNoArgBoolean(jni, baseVM, useVaList, userManager, "isManagedProfile"));
            assertEquals(expectedDemoUser,
                    invokeNoArgBoolean(jni, baseVM, useVaList, userManager, "isDemoUser"));

            // marker from myUserHandle → isUserUnlocked(UserHandle) overload
            DvmObject<?> markerHandle = invokeMyUserHandle(jni, baseVM, useVaList);
            assertNotNull(markerHandle);
            assertTrue(markerHandle.getValue().getClass().getName().contains("ConfiguredUserHandle"));
            assertEquals(expectedUnlocked,
                    invokeIsUserUnlockedWithHandle(jni, baseVM, useVaList, userManager, markerHandle));

            CapturedEvent unlockedHandleEv = findLastEvent(sink.events, "android_user",
                    "UserManager.isUserUnlocked(UserHandle)");
            assertNotNull(unlockedHandleEv);
            assertEquals("json-config", unlockedHandleEv.source);
            assertEquals("userId=" + expectedUserId + ",result=" + expectedUnlocked,
                    String.valueOf(unlockedHandleEv.value));
            assertNotNull(unlockedHandleEv.note);
            assertFalse(unlockedHandleEv.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_user",
                    "UserManager.isUserUnlocked(UserHandle)"));

            // plain UserHandle / null → UOE, no extra overload event
            int beforeReject = countEvents(sink.events, "android_user",
                    "UserManager.isUserUnlocked(UserHandle)");
            DvmObject<?> plain = vm.resolveClass(USER_HANDLE_CLASS).newObject(null);
            try {
                invokeIsUserUnlockedWithHandle(jni, baseVM, useVaList, userManager, plain);
                fail("expected UnsupportedOperationException for plain UserHandle");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isUserUnlocked"));
            }
            try {
                invokeIsUserUnlockedWithHandle(jni, baseVM, useVaList, userManager, null);
                fail("expected UnsupportedOperationException for null UserHandle");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isUserUnlocked"));
            }
            assertEquals(beforeReject, countEvents(sink.events, "android_user",
                    "UserManager.isUserUnlocked(UserHandle)"));

            // unsupported exact-signature rejects
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, userManager, "isGuestUser");
                fail("expected UnsupportedOperationException for isGuestUser");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isGuestUser"));
            }

            CapturedEvent unlocked = findLastEvent(sink.events, "android_user",
                    "UserManager.isUserUnlocked");
            assertNotNull(unlocked);
            assertEquals("json-config", unlocked.source);
            assertEquals("result=" + expectedUnlocked, String.valueOf(unlocked.value));
            assertNotNull(unlocked.note);
            assertFalse(unlocked.note.isEmpty());

            CapturedEvent systemUser = findLastEvent(sink.events, "android_user",
                    "UserManager.isSystemUser");
            assertNotNull(systemUser);
            assertEquals("json-config", systemUser.source);
            assertEquals("result=" + expectedSystemUser, String.valueOf(systemUser.value));

            CapturedEvent managed = findLastEvent(sink.events, "android_user",
                    "UserManager.isManagedProfile");
            assertNotNull(managed);
            assertEquals("json-config", managed.source);
            assertEquals("result=" + expectedManagedProfile, String.valueOf(managed.value));

            CapturedEvent demoUser = findLastEvent(sink.events, "android_user",
                    "UserManager.isDemoUser");
            assertNotNull(demoUser);
            assertEquals("json-config", demoUser.source);
            assertEquals("result=" + expectedDemoUser, String.valueOf(demoUser.value));
            assertNotNull(demoUser.note);
            assertFalse(demoUser.note.isEmpty());

            // no-arg isUserUnlocked api remains distinct from handle overload
            assertEquals(1, countEvents(sink.events, "android_user", "UserManager.isUserUnlocked"));
            assertEquals(1, countEvents(sink.events, "android_user", "UserManager.isSystemUser"));
            assertEquals(1, countEvents(sink.events, "android_user", "UserManager.isManagedProfile"));
            assertEquals(1, countEvents(sink.events, "android_user", "UserManager.isDemoUser"));
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

            DvmObject<?> userManager = vm.resolveClass(USER_MANAGER_CLASS).newObject(null);
            for (String methodName : new String[] {
                    "isUserUnlocked", "isSystemUser", "isManagedProfile", "isDemoUser", "isGuestUser"
            }) {
                try {
                    invokeNoArgBoolean(jni, baseVM, useVaList, userManager, methodName);
                    fail("expected UnsupportedOperationException for " + methodName + " without userState");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains(methodName));
                }
            }

            // handle overload also UOE without userState (even with a plain handle)
            DvmObject<?> plain = vm.resolveClass(USER_HANDLE_CLASS).newObject(null);
            try {
                invokeIsUserUnlockedWithHandle(jni, baseVM, useVaList, userManager, plain);
                fail("expected UnsupportedOperationException for isUserUnlocked(UserHandle) without userState");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isUserUnlocked"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_user event when config absent: " + e.api,
                        "android_user".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static DvmObject<?> invokeMyUserHandle(AbstractJni jni, BaseVM vm, boolean useVaList) {
        DvmClass dvmClass = vm.resolveClass(USER_HANDLE_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "myUserHandle", "()Landroid/os/UserHandle;", true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, dvmClass, signature, new TestVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, dvmClass, signature, new TestVarArg(vm, method));
    }

    private static boolean invokeIsUserUnlockedWithHandle(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                          DvmObject<?> userManager, DvmObject<?> handle) {
        int handleHash = handle == null ? 0 : vm.addLocalObject(handle);
        DvmClass dvmClass = userManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "isUserUnlocked", IS_USER_UNLOCKED_HANDLE_ARGS, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, userManager, signature,
                    new TestVaList(vm, method, handleHash));
        }
        return jni.callBooleanMethod(vm, userManager, signature,
                new TestVarArg(vm, method, handleHash));
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
