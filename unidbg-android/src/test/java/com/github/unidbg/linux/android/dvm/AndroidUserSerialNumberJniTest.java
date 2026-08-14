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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AndroidUserSerialNumberJniTest {

    private static final String USER_HANDLE_CLASS = "android/os/UserHandle";
    private static final String USER_MANAGER_CLASS = "android/os/UserManager";
    private static final String MY_USER_HANDLE_ARGS = "()Landroid/os/UserHandle;";
    private static final String GET_SERIAL_ARGS = "(Landroid/os/UserHandle;)J";
    private static final String GET_HANDLE_FOR_SERIAL_ARGS = "(J)Landroid/os/UserHandle;";

    private static final String EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"userState\":{}"
            + "}"
            + "}";

    private static final String MAX_SERIAL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"userState\":{\"serialNumber\":" + Long.MAX_VALUE + "}"
            + "}"
            + "}";

    private static final String USER_ID_AND_SERIAL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"userState\":{\"userId\":10,\"serialNumber\":99}"
            + "}"
            + "}";

    private static final String ABSENT_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testSerialNumberEmptyDefaultVarArg32() throws Exception {
        runConfigured(false, false, EMPTY_JSON, 0L);
    }

    @Test
    public void testSerialNumberEmptyDefaultVaList64() throws Exception {
        runConfigured(true, true, EMPTY_JSON, 0L);
    }

    @Test
    public void testSerialNumberMaxVarArg32() throws Exception {
        runConfigured(false, false, MAX_SERIAL_JSON, Long.MAX_VALUE);
    }

    @Test
    public void testSerialNumberMaxVaList64() throws Exception {
        runConfigured(true, true, MAX_SERIAL_JSON, Long.MAX_VALUE);
    }

    @Test
    public void testSerialNumberAbsentVarArg32() throws Exception {
        runAbsent(false, false);
    }

    @Test
    public void testSerialNumberAbsentVaList64() throws Exception {
        runAbsent(true, true);
    }

    @Test
    public void testGetUserHandleForSerialEmptyDefaultVarArg32() throws Exception {
        runGetUserHandleForSerial(false, false, EMPTY_JSON, 0L, 0);
    }

    @Test
    public void testGetUserHandleForSerialEmptyDefaultVaList64() throws Exception {
        runGetUserHandleForSerial(true, true, EMPTY_JSON, 0L, 0);
    }

    @Test
    public void testGetUserHandleForSerialMaxVarArg32() throws Exception {
        runGetUserHandleForSerial(false, false, MAX_SERIAL_JSON, Long.MAX_VALUE, 0);
    }

    @Test
    public void testGetUserHandleForSerialMaxVaList64() throws Exception {
        runGetUserHandleForSerial(true, true, MAX_SERIAL_JSON, Long.MAX_VALUE, 0);
    }

    @Test
    public void testGetUserHandleForSerialMatchAndRoundTripVarArg32() throws Exception {
        runGetUserHandleForSerial(false, false, USER_ID_AND_SERIAL_JSON, 99L, 10);
    }

    @Test
    public void testGetUserHandleForSerialMatchAndRoundTripVaList64() throws Exception {
        runGetUserHandleForSerial(true, true, USER_ID_AND_SERIAL_JSON, 99L, 10);
    }

    @Test
    public void testGetUserHandleForSerialAbsentVarArg32() throws Exception {
        runGetUserHandleForSerialAbsent(false, false);
    }

    @Test
    public void testGetUserHandleForSerialAbsentVaList64() throws Exception {
        runGetUserHandleForSerialAbsent(true, true);
    }

    /**
     * Same TraceEnvironmentConfig instance on two emulators: marker from A must not authorize B.
     */
    @Test
    public void testSerialNumberCrossEmulatorSameConfigRejectedVarArg32() throws Exception {
        runCrossEmulatorSameConfigRejected(false, false);
    }

    @Test
    public void testSerialNumberCrossEmulatorSameConfigRejectedVaList64() throws Exception {
        runCrossEmulatorSameConfigRejected(true, true);
    }

    private static void runCrossEmulatorSameConfigRejected(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig shared = TraceEnvironmentConfig.parse(USER_ID_AND_SERIAL_JSON);
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
                invokeGetSerialNumberForUser(jniB, baseB, useVaList, managerB, markerFromA);
                fail("expected UnsupportedOperationException for cross-emulator ConfiguredUserHandle");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSerialNumberForUser"));
            }
            for (CapturedEvent e : sinkB.events) {
                assertFalse("B must not emit getSerialNumberForUser for foreign marker",
                        "android_user".equals(e.kind)
                                && "UserManager.getSerialNumberForUser".equals(e.api));
            }

            // Foreign marker getIdentifier on B must UOE (not authorized)
            try {
                invokeGetIdentifier(jniB, baseB, useVaList, markerFromA);
                fail("expected UnsupportedOperationException for getIdentifier on foreign marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getIdentifier"));
            }

            // Owner A still succeeds
            DvmObject<?> managerA = vmA.resolveClass(USER_MANAGER_CLASS).newObject(null);
            assertEquals(99L,
                    invokeGetSerialNumberForUser(jniA, baseA, useVaList, managerA, markerFromA));
            CapturedEvent serialA = findLastEvent(sinkA.events, "android_user",
                    "UserManager.getSerialNumberForUser");
            assertNotNull(serialA);
            assertEquals("json-config", serialA.source);
            assertEquals("result=99", String.valueOf(serialA.value));
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
                                      long expectedSerial) throws Exception {
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

            DvmObject<?> markerHandle = invokeMyUserHandle(jni, baseVM, useVaList);
            assertNotNull(markerHandle);
            assertTrue(markerHandle.getValue().getClass().getName().contains("ConfiguredUserHandle"));

            DvmObject<?> userManager = vm.resolveClass(USER_MANAGER_CLASS).newObject(null);
            assertEquals(expectedSerial,
                    invokeGetSerialNumberForUser(jni, baseVM, useVaList, userManager, markerHandle));

            // null handle rejected
            try {
                invokeGetSerialNumberForUser(jni, baseVM, useVaList, userManager, null);
                fail("expected UnsupportedOperationException for null UserHandle");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSerialNumberForUser"));
            }

            // plain UserHandle rejected
            DvmObject<?> plain = vm.resolveClass(USER_HANDLE_CLASS).newObject(null);
            try {
                invokeGetSerialNumberForUser(jni, baseVM, useVaList, userManager, plain);
                fail("expected UnsupportedOperationException for plain UserHandle");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSerialNumberForUser"));
            }

            CapturedEvent serialEv = findLastEvent(sink.events, "android_user",
                    "UserManager.getSerialNumberForUser");
            assertNotNull(serialEv);
            assertEquals("json-config", serialEv.source);
            assertEquals("result=" + expectedSerial, String.valueOf(serialEv.value));
            assertNotNull(serialEv.note);
            assertFalse(serialEv.note.isEmpty());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetUserHandleForSerial(boolean is64Bit, boolean useVaList, String json,
                                                  long matchSerial, int expectedUserId)
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

            // match → ConfiguredUserHandle marker
            DvmObject<?> handle = invokeGetUserHandleForSerialNumber(
                    jni, baseVM, useVaList, userManager, matchSerial);
            assertNotNull(handle);
            assertTrue(handle.getValue().getClass().getName().contains("ConfiguredUserHandle"));
            assertEquals(expectedUserId, invokeGetIdentifier(jni, baseVM, useVaList, handle));
            // round-trip through getSerialNumberForUser
            assertEquals(matchSerial,
                    invokeGetSerialNumberForUser(jni, baseVM, useVaList, userManager, handle));

            // mismatch → handled null
            long mismatchSerial = matchSerial == 0L ? 1L : 0L;
            DvmObject<?> miss = invokeGetUserHandleForSerialNumber(
                    jni, baseVM, useVaList, userManager, mismatchSerial);
            assertNull(miss);

            CapturedEvent hitEv = null;
            CapturedEvent missEv = null;
            int lookupCount = 0;
            for (CapturedEvent e : sink.events) {
                if ("android_user".equals(e.kind)
                        && "UserManager.getUserHandleForSerialNumber".equals(e.api)) {
                    lookupCount++;
                    if (String.valueOf(e.value).contains("result=null")) {
                        missEv = e;
                    } else if (String.valueOf(e.value).contains("userId=")) {
                        hitEv = e;
                    }
                }
            }
            assertEquals(2, lookupCount);
            assertNotNull(hitEv);
            assertEquals("json-config", hitEv.source);
            assertEquals("serialNumber=" + matchSerial + ",userId=" + expectedUserId,
                    String.valueOf(hitEv.value));
            assertNotNull(hitEv.note);
            assertFalse(hitEv.note.isEmpty());

            assertNotNull(missEv);
            assertEquals("json-config", missEv.source);
            assertEquals("serialNumber=" + mismatchSerial + ",result=null",
                    String.valueOf(missEv.value));
            assertNotNull(missEv.note);
            assertFalse(missEv.note.isEmpty());

            // Long.MAX_VALUE must not be truncated when used as the match input
            if (matchSerial == Long.MAX_VALUE) {
                assertTrue(String.valueOf(hitEv.value).contains(Long.toString(Long.MAX_VALUE)));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetUserHandleForSerialAbsent(boolean is64Bit, boolean useVaList)
            throws Exception {
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
            try {
                invokeGetUserHandleForSerialNumber(jni, baseVM, useVaList, userManager, 0L);
                fail("expected UnsupportedOperationException without userState");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getUserHandleForSerialNumber"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected getUserHandleForSerialNumber event when absent: " + e.api,
                        "android_user".equals(e.kind)
                                && "UserManager.getUserHandleForSerialNumber".equals(e.api));
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

            DvmObject<?> userManager = vm.resolveClass(USER_MANAGER_CLASS).newObject(null);
            DvmObject<?> plain = vm.resolveClass(USER_HANDLE_CLASS).newObject(null);
            try {
                invokeGetSerialNumberForUser(jni, baseVM, useVaList, userManager, plain);
                fail("expected UnsupportedOperationException without userState");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSerialNumberForUser"));
            }
            try {
                invokeGetSerialNumberForUser(jni, baseVM, useVaList, userManager, null);
                fail("expected UnsupportedOperationException for null without userState");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSerialNumberForUser"));
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
        DvmMethod method = new DvmMethod(dvmClass, "myUserHandle", MY_USER_HANDLE_ARGS, true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, dvmClass, signature, new TestVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, dvmClass, signature, new TestVarArg(vm, method));
    }

    private static int invokeGetIdentifier(AbstractJni jni, BaseVM vm, boolean useVaList,
                                           DvmObject<?> handle) {
        DvmClass dvmClass = vm.resolveClass(USER_HANDLE_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getIdentifier", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, handle, signature, new TestVaList(vm, method));
        }
        return jni.callIntMethod(vm, handle, signature, new TestVarArg(vm, method));
    }

    private static long invokeGetSerialNumberForUser(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                     DvmObject<?> userManager, DvmObject<?> handle) {
        // Object args are passed as local-object hashes (same as PackageManager tests).
        int handleHash = handle == null ? 0 : vm.addLocalObject(handle);
        DvmClass dvmClass = userManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSerialNumberForUser", GET_SERIAL_ARGS, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callLongMethodV(vm, userManager, signature, new TestVaList(vm, method, handleHash));
        }
        return jni.callLongMethod(vm, userManager, signature, new TestVarArg(vm, method, handleHash));
    }

    private static DvmObject<?> invokeGetUserHandleForSerialNumber(AbstractJni jni, BaseVM vm,
                                                                   boolean useVaList,
                                                                   DvmObject<?> userManager,
                                                                   long serialNumber) {
        DvmClass dvmClass = userManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getUserHandleForSerialNumber",
                GET_HANDLE_FOR_SERIAL_ARGS, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, userManager, signature,
                    new TestVaList(vm, method, serialNumber));
        }
        return jni.callObjectMethod(vm, userManager, signature,
                new TestVarArg(vm, method, serialNumber));
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

    private static final class TestVarArg extends VarArg {
        TestVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
        }

        /** Long arg must be boxed as {@link Long} — never truncated to int. */
        TestVarArg(BaseVM vm, DvmMethod method, long longArg0) {
            super(vm, method);
            args.add(Long.valueOf(longArg0));
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

        /** Long arg must be boxed as {@link Long} — never truncated to int. */
        TestVaList(BaseVM vm, DvmMethod method, long longArg0) {
            super(vm, method);
            args.add(Long.valueOf(longArg0));
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
