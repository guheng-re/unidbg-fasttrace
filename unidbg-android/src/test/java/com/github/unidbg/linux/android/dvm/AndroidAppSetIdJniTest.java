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

/**
 * Coverage for {@code android.identifiers.appSetId} / {@code appSetScope} via
 * AppSet.getClient → getAppSetIdInfo → Task.getResult → AppSetIdInfo.getId/getScope
 * (VarArg 32 + VaList 64). Synchronous completed Task marker only.
 */
public class AndroidAppSetIdJniTest {

    private static final String APP_SET_CLASS = "com/google/android/gms/appset/AppSet";
    private static final String CLIENT_CLASS = "com/google/android/gms/appset/AppSetIdClient";
    private static final String TASK_CLASS = "com/google/android/gms/tasks/Task";
    private static final String INFO_CLASS = "com/google/android/gms/appset/AppSetIdInfo";

    private static final String GET_CLIENT_ARGS =
            "(Landroid/content/Context;)Lcom/google/android/gms/appset/AppSetIdClient;";
    private static final String GET_INFO_ARGS = "()Lcom/google/android/gms/tasks/Task;";
    private static final String GET_RESULT_ARGS = "()Ljava/lang/Object;";
    private static final String GET_RESULT_CLASS_ARGS = "(Ljava/lang/Class;)Ljava/lang/Object;";
    private static final String GET_ID_ARGS = "()Ljava/lang/String;";
    private static final String GET_SCOPE_ARGS = "()I";

    private static final String EXAMPLE_ID = "traceai-app-set-id-000000000001";

    private static final String SCOPE_DEVELOPER_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"identifiers\":{"
            + "\"appSetId\":\"" + EXAMPLE_ID + "\","
            + "\"appSetScope\":2"
            + "}"
            + "}"
            + "}";

    private static final String SCOPE_DEFAULT_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"identifiers\":{"
            + "\"appSetId\":\"" + EXAMPLE_ID + "\""
            + "}"
            + "}"
            + "}";

    private static final String IDENTIFIERS_NO_APP_SET_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"identifiers\":{\"limitAdTracking\":false}"
            + "}"
            + "}";

    private static final String EMPTY_IDENTIFIERS_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"identifiers\":{}"
            + "}"
            + "}";

    private static final String NO_IDENTIFIERS_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testAppSetIdFullVarArg32() throws Exception {
        runConfigured(false, false, SCOPE_DEVELOPER_JSON, EXAMPLE_ID, 2);
    }

    @Test
    public void testAppSetIdFullVaList64() throws Exception {
        runConfigured(true, true, SCOPE_DEVELOPER_JSON, EXAMPLE_ID, 2);
    }

    @Test
    public void testAppSetIdDefaultScopeVarArg32() throws Exception {
        runConfigured(false, false, SCOPE_DEFAULT_JSON, EXAMPLE_ID, 1);
    }

    @Test
    public void testAppSetIdDefaultScopeVaList64() throws Exception {
        runConfigured(true, true, SCOPE_DEFAULT_JSON, EXAMPLE_ID, 1);
    }

    @Test
    public void testAppSetIdMissingDoesNotTakeOverVarArg32() throws Exception {
        runMissing(false, false, NO_IDENTIFIERS_JSON);
        runMissing(false, false, EMPTY_IDENTIFIERS_JSON);
        runMissing(false, false, IDENTIFIERS_NO_APP_SET_JSON);
    }

    @Test
    public void testAppSetIdMissingDoesNotTakeOverVaList64() throws Exception {
        runMissing(true, true, NO_IDENTIFIERS_JSON);
        runMissing(true, true, EMPTY_IDENTIFIERS_JSON);
        runMissing(true, true, IDENTIFIERS_NO_APP_SET_JSON);
    }

    @Test
    public void testAppSetIdConfigConstruction() {
        TraceEnvironmentConfig configured = TraceEnvironmentConfig.parse(SCOPE_DEFAULT_JSON);
        assertTrue(configured.isAndroidIdentifiersConfigured());
        TraceEnvironmentConfig.AndroidIdentifiersConfig ids = configured.getAndroidIdentifiersConfig();
        assertTrue(ids.isAppSetIdConfigured());
        assertEquals(EXAMPLE_ID, ids.getAppSetId());
        assertEquals(1, ids.getAppSetScope());

        TraceEnvironmentConfig scope2 = TraceEnvironmentConfig.parse(SCOPE_DEVELOPER_JSON);
        assertEquals(2, scope2.getAndroidIdentifiersConfig().getAppSetScope());

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(EMPTY_IDENTIFIERS_JSON);
        assertFalse(empty.getAndroidIdentifiersConfig().isAppSetIdConfigured());
        assertNull(empty.getAndroidIdentifiersConfig().getAppSetId());

        assertInvalid("{\"android\":{\"identifiers\":{\"appSetScope\":1}}}",
                "android.identifiers.appSetScope");
        assertInvalid("{\"android\":{\"identifiers\":{\"appSetScope\":2}}}",
                "android.identifiers.appSetScope");
        assertInvalid("{\"android\":{\"identifiers\":{\"appSetId\":\"" + EXAMPLE_ID + "\",\"appSetScope\":0}}}",
                "android.identifiers.appSetScope");
        assertInvalid("{\"android\":{\"identifiers\":{\"appSetId\":\"" + EXAMPLE_ID + "\",\"appSetScope\":3}}}",
                "android.identifiers.appSetScope");
        assertInvalid("{\"android\":{\"identifiers\":{\"appSetId\":\"" + EXAMPLE_ID + "\",\"appSetScope\":1.5}}}",
                "android.identifiers.appSetScope");
        assertInvalid("{\"android\":{\"identifiers\":{\"appSetId\":\"" + EXAMPLE_ID + "\",\"appSetScope\":\"1\"}}}",
                "android.identifiers.appSetScope");
        assertInvalid("{\"android\":{\"identifiers\":{\"appSetId\":\"" + EXAMPLE_ID + "\",\"appSetScope\":true}}}",
                "android.identifiers.appSetScope");
        assertInvalid("{\"android\":{\"identifiers\":{\"appSetId\":\"" + EXAMPLE_ID + "\",\"appSetScope\":null}}}",
                "android.identifiers.appSetScope");

        assertInvalid("{\"android\":{\"identifiers\":{\"appSetId\":null}}}",
                "android.identifiers.appSetId");
        assertInvalid("{\"android\":{\"identifiers\":{\"appSetId\":\"\"}}}",
                "android.identifiers.appSetId");
        assertInvalid("{\"android\":{\"identifiers\":{\"appSetId\":1}}}",
                "android.identifiers.appSetId");
        assertInvalid("{\"android\":{\"identifiers\":{\"appSetId\":true}}}",
                "android.identifiers.appSetId");
        assertInvalid("{\"android\":{\"identifiers\":{\"appSetId\":\"abc\\n\"}}}",
                "android.identifiers.appSetId");
        assertInvalid("{\"android\":{\"identifiers\":{\"appSetId\":\"abc\\r\"}}}",
                "android.identifiers.appSetId");
        assertInvalid("{\"android\":{\"identifiers\":{\"appSetId\":\"abc\\u0000\"}}}",
                "android.identifiers.appSetId");
        assertInvalid("{\"android\":{\"identifiers\":{\"appSetId\":\"应用集合\"}}}",
                "android.identifiers.appSetId");

        StringBuilder max = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            max.append('a');
        }
        TraceEnvironmentConfig maxOk = TraceEnvironmentConfig.parse(
                "{\"android\":{\"identifiers\":{\"appSetId\":\"" + max + "\"}}}");
        assertEquals(150, maxOk.getAndroidIdentifiersConfig().getAppSetId().length());
        assertInvalid("{\"android\":{\"identifiers\":{\"appSetId\":\"" + max + "a\"}}}",
                "android.identifiers.appSetId");
    }

    @Test
    public void testAppSetIdMarkerIsolationVarArg32() throws Exception {
        runMarkerIsolation(false, false);
    }

    @Test
    public void testAppSetIdMarkerIsolationVaList64() throws Exception {
        runMarkerIsolation(true, true);
    }

    @Test
    public void testAppSetIdCrossVmRejectedVarArg32() throws Exception {
        runCrossVmRejected(false, false);
    }

    @Test
    public void testAppSetIdCrossVmRejectedVaList64() throws Exception {
        runCrossVmRejected(true, true);
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList, String json,
                                      String expectedId, int expectedScope) throws Exception {
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

            DvmObject<?> client = invokeGetClient(jni, baseVM, useVaList);
            assertNotNull(client);
            assertEquals(CLIENT_CLASS, client.getObjectType().getClassName());
            assertNotNull(client.getValue());
            assertTrue(client.getValue().getClass().getName().contains("ConfiguredAppSetIdClient"));
            assertEquals(0, countEvents(sink.events, "android_identifier", "AppSet.getClient"));

            DvmObject<?> task = invokeGetAppSetIdInfo(jni, baseVM, useVaList, client);
            assertNotNull(task);
            assertEquals(TASK_CLASS, task.getObjectType().getClassName());
            assertTrue(task.getValue().getClass().getName().contains("ConfiguredAppSetIdTask"));

            DvmObject<?> info = invokeGetResult(jni, baseVM, useVaList, task);
            assertNotNull(info);
            assertEquals(INFO_CLASS, info.getObjectType().getClassName());
            assertTrue(info.getValue().getClass().getName().contains("ConfiguredAppSetIdInfo"));

            assertEquals(expectedId, invokeGetId(jni, baseVM, useVaList, info));
            assertEquals(expectedScope, invokeGetScope(jni, baseVM, useVaList, info));

            CapturedEvent getInfo = findLastEvent(sink.events, "android_identifier",
                    "AppSetIdClient.getAppSetIdInfo");
            assertNotNull(getInfo);
            assertEquals("json-config", getInfo.source);
            assertEquals("appSetIdLength=" + expectedId.length() + ",scope=" + expectedScope,
                    String.valueOf(getInfo.value));
            assertFalse(containsRawId(getInfo, expectedId));
            assertTrue(String.valueOf(getInfo.note).contains("应用集合标识符"));

            CapturedEvent getResult = findLastEvent(sink.events, "android_identifier",
                    "Task.getResult");
            assertNotNull(getResult);
            assertEquals("json-config", getResult.source);
            assertEquals("resultLength=" + expectedId.length(), String.valueOf(getResult.value));
            assertFalse(containsRawId(getResult, expectedId));
            assertTrue(String.valueOf(getResult.note).contains("应用集合标识符"));

            CapturedEvent getId = findLastEvent(sink.events, "android_identifier",
                    "AppSetIdInfo.getId");
            assertNotNull(getId);
            assertEquals("json-config", getId.source);
            assertEquals("appSetIdLength=" + expectedId.length(), String.valueOf(getId.value));
            assertFalse(containsRawId(getId, expectedId));
            assertTrue(String.valueOf(getId.note).contains("应用集合标识符"));

            CapturedEvent getScope = findLastEvent(sink.events, "android_identifier",
                    "AppSetIdInfo.getScope");
            assertNotNull(getScope);
            assertEquals("json-config", getScope.source);
            assertEquals("scope=" + expectedScope, String.valueOf(getScope.value));
            assertFalse(containsRawId(getScope, expectedId));
            assertTrue(String.valueOf(getScope.note).contains("应用集合标识符"));

            for (CapturedEvent e : sink.events) {
                if ("android_identifier".equals(e.kind)) {
                    assertFalse(containsRawId(e, expectedId));
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runMissing(boolean is64Bit, boolean useVaList, String json) throws Exception {
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

            try {
                invokeGetClient(jni, baseVM, useVaList);
                fail("expected UnsupportedOperationException without appSetId");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getClient"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_identifier event when appSetId absent: " + e.api,
                        "android_identifier".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runMarkerIsolation(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SCOPE_DEVELOPER_JSON);
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

            DvmObject<?> client = invokeGetClient(jni, baseVM, useVaList);
            DvmObject<?> task = invokeGetAppSetIdInfo(jni, baseVM, useVaList, client);
            DvmObject<?> info = invokeGetResult(jni, baseVM, useVaList, task);
            int eventsAfterSuccess = sink.events.size();

            DvmObject<?> plainClient = vm.resolveClass(CLIENT_CLASS).newObject(null);
            try {
                invokeGetAppSetIdInfo(jni, baseVM, useVaList, plainClient);
                fail("expected UOE for foreign AppSetIdClient");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAppSetIdInfo"));
            }

            DvmObject<?> plainTask = vm.resolveClass(TASK_CLASS).newObject(null);
            try {
                invokeGetResult(jni, baseVM, useVaList, plainTask);
                fail("expected UOE for foreign Task");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getResult"));
            }

            DvmObject<?> plainInfo = vm.resolveClass(INFO_CLASS).newObject("foreign");
            try {
                invokeGetId(jni, baseVM, useVaList, plainInfo);
                fail("expected UOE for foreign AppSetIdInfo.getId");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getId"));
            }
            try {
                invokeGetScope(jni, baseVM, useVaList, plainInfo);
                fail("expected UOE for foreign AppSetIdInfo.getScope");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getScope"));
            }

            try {
                invokeObjectMethod(jni, baseVM, useVaList, task, TASK_CLASS,
                        "getResult", GET_RESULT_CLASS_ARGS);
                fail("expected UOE for Task.getResult(Class)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getResult"));
            }

            try {
                invokeGetId(jni, baseVM, useVaList, client);
                fail("expected UOE for getId on client marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getId"));
            }

            DvmClass wrongClass = vm.resolveClass(APP_SET_CLASS);
            DvmMethod wrong = new DvmMethod(wrongClass, "getClient", "()Lcom/google/android/gms/appset/AppSetIdClient;", true);
            try {
                if (useVaList) {
                    jni.callStaticObjectMethodV(baseVM, wrongClass, wrong.getSignature(),
                            new TestVaList(baseVM, wrong));
                } else {
                    jni.callStaticObjectMethod(baseVM, wrongClass, wrong.getSignature(),
                            new TestVarArg(baseVM, wrong));
                }
                fail("expected UOE for wrong getClient signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getClient"));
            }

            assertEquals(eventsAfterSuccess, sink.events.size());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runCrossVmRejected(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig shared = TraceEnvironmentConfig.parse(SCOPE_DEVELOPER_JSON);
        AndroidEmulator emuA = null;
        AndroidEmulator emuB = null;
        CapturingSink sinkB = new CapturingSink();
        try {
            emuA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(shared)
                    .build();
            emuB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(shared)
                    .build();
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

            DvmObject<?> clientA = invokeGetClient(jniA, baseA, useVaList);
            DvmObject<?> taskA = invokeGetAppSetIdInfo(jniA, baseA, useVaList, clientA);
            DvmObject<?> infoA = invokeGetResult(jniA, baseA, useVaList, taskA);

            try {
                invokeGetAppSetIdInfo(jniB, baseB, useVaList, clientA);
                fail("expected UOE for cross-VM getAppSetIdInfo");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAppSetIdInfo"));
            }
            try {
                invokeGetResult(jniB, baseB, useVaList, taskA);
                fail("expected UOE for cross-VM Task.getResult");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getResult"));
            }
            try {
                invokeGetId(jniB, baseB, useVaList, infoA);
                fail("expected UOE for cross-VM AppSetIdInfo.getId");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getId"));
            }
            try {
                invokeGetScope(jniB, baseB, useVaList, infoA);
                fail("expected UOE for cross-VM AppSetIdInfo.getScope");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getScope"));
            }
            for (CapturedEvent e : sinkB.events) {
                assertFalse("leak to VM B: " + e.api, "android_identifier".equals(e.kind));
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

    private static DvmObject<?> invokeGetClient(AbstractJni jni, BaseVM vm, boolean useVaList) {
        DvmClass dvmClass = vm.resolveClass(APP_SET_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getClient", GET_CLIENT_ARGS, true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, dvmClass, signature, new TestVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, dvmClass, signature, new TestVarArg(vm, method));
    }

    private static DvmObject<?> invokeGetAppSetIdInfo(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                      DvmObject<?> client) {
        return invokeObjectMethod(jni, vm, useVaList, client, CLIENT_CLASS,
                "getAppSetIdInfo", GET_INFO_ARGS);
    }

    private static DvmObject<?> invokeGetResult(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                DvmObject<?> task) {
        return invokeObjectMethod(jni, vm, useVaList, task, TASK_CLASS,
                "getResult", GET_RESULT_ARGS);
    }

    private static String invokeGetId(AbstractJni jni, BaseVM vm, boolean useVaList, DvmObject<?> info) {
        DvmObject<?> result = invokeObjectMethod(jni, vm, useVaList, info, INFO_CLASS,
                "getId", GET_ID_ARGS);
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static int invokeGetScope(AbstractJni jni, BaseVM vm, boolean useVaList, DvmObject<?> info) {
        DvmClass dvmClass = vm.resolveClass(INFO_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getScope", GET_SCOPE_ARGS, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, info, signature, new TestVaList(vm, method));
        }
        return jni.callIntMethod(vm, info, signature, new TestVarArg(vm, method));
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

    private static void assertInvalid(String json, String expectedPath) {
        try {
            TraceEnvironmentConfig.parse(json);
            fail("expected IllegalArgumentException containing path: " + expectedPath + " for json: " + json);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            assertTrue("message missing path " + expectedPath + ", was: " + message,
                    message != null && message.contains(expectedPath));
        }
    }

    private static boolean containsRawId(CapturedEvent event, String rawId) {
        return String.valueOf(event.value).contains(rawId)
                || String.valueOf(event.note).contains(rawId)
                || String.valueOf(event.api).contains(rawId);
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
