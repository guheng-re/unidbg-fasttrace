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

public class AndroidUserHandleJniTest {

    private static final String USER_HANDLE_CLASS = "android/os/UserHandle";
    private static final String MY_USER_ID_ARGS = "()I";
    private static final String MY_USER_HANDLE_ARGS = "()Landroid/os/UserHandle;";
    private static final String GET_IDENTIFIER_ARGS = "()I";

    private static final String FULL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"userState\":{\"userId\":10}"
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
    public void testUserHandleFullVarArg32() throws Exception {
        runConfigured(false, false, FULL_JSON, 10);
    }

    @Test
    public void testUserHandleFullVaList64() throws Exception {
        runConfigured(true, true, FULL_JSON, 10);
    }

    @Test
    public void testUserHandleEmptyDefaultVarArg32() throws Exception {
        runConfigured(false, false, EMPTY_JSON, 0);
    }

    @Test
    public void testUserHandleEmptyDefaultVaList64() throws Exception {
        runConfigured(true, true, EMPTY_JSON, 0);
    }

    @Test
    public void testUserHandleAbsentVarArg32() throws Exception {
        runAbsent(false, false);
    }

    @Test
    public void testUserHandleAbsentVaList64() throws Exception {
        runAbsent(true, true);
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList, String json,
                                      int expectedUserId) throws Exception {
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

            assertEquals(expectedUserId, invokeMyUserId(jni, baseVM, useVaList));

            DvmObject<?> handle = invokeMyUserHandle(jni, baseVM, useVaList);
            assertNotNull(handle);
            assertEquals(USER_HANDLE_CLASS, handle.getObjectType().getClassName());
            assertNotNull(handle.getValue());
            assertTrue(handle.getValue().getClass().getName().contains("ConfiguredUserHandle"));
            assertEquals(expectedUserId, invokeGetIdentifier(jni, baseVM, useVaList, handle));

            // unknown int method on marker must UOE
            try {
                invokeIntMethod(jni, baseVM, useVaList, handle, USER_HANDLE_CLASS, "hashCode", "()I");
                fail("expected UnsupportedOperationException for unknown int method on marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hashCode"));
            }

            // provenance isolation: plain UserHandle is not configured path
            DvmObject<?> plain = vm.resolveClass(USER_HANDLE_CLASS).newObject(null);
            try {
                invokeGetIdentifier(jni, baseVM, useVaList, plain);
                fail("expected UnsupportedOperationException for getIdentifier without marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getIdentifier"));
            }

            CapturedEvent myUserId = findLastEvent(sink.events, "android_user", "UserHandle.myUserId");
            assertNotNull(myUserId);
            assertEquals("json-config", myUserId.source);
            assertEquals("result=" + expectedUserId, String.valueOf(myUserId.value));
            assertNotNull(myUserId.note);
            assertFalse(myUserId.note.isEmpty());

            CapturedEvent myUserHandle = findLastEvent(sink.events, "android_user",
                    "UserHandle.myUserHandle");
            assertNotNull(myUserHandle);
            assertEquals("json-config", myUserHandle.source);
            assertEquals("userId=" + expectedUserId, String.valueOf(myUserHandle.value));

            CapturedEvent getIdentifier = findLastEvent(sink.events, "android_user",
                    "UserHandle.getIdentifier");
            assertNotNull(getIdentifier);
            assertEquals("json-config", getIdentifier.source);
            assertEquals("result=" + expectedUserId, String.valueOf(getIdentifier.value));
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

            try {
                invokeMyUserId(jni, baseVM, useVaList);
                fail("expected UnsupportedOperationException for myUserId without userState");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("myUserId"));
            }
            try {
                invokeMyUserHandle(jni, baseVM, useVaList);
                fail("expected UnsupportedOperationException for myUserHandle without userState");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("myUserHandle"));
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

    private static int invokeMyUserId(AbstractJni jni, BaseVM vm, boolean useVaList) {
        DvmClass dvmClass = vm.resolveClass(USER_HANDLE_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "myUserId", MY_USER_ID_ARGS, true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticIntMethodV(vm, dvmClass, signature, new TestVaList(vm, method));
        }
        return jni.callStaticIntMethod(vm, dvmClass, signature, new TestVarArg(vm, method));
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
        return invokeIntMethod(jni, vm, useVaList, handle, USER_HANDLE_CLASS,
                "getIdentifier", GET_IDENTIFIER_ARGS);
    }

    private static int invokeIntMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                       DvmObject<?> receiver, String className,
                                       String methodName, String args) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, receiver, signature, new TestVaList(vm, method));
        }
        return jni.callIntMethod(vm, receiver, signature, new TestVarArg(vm, method));
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
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
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
