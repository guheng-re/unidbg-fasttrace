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

public class AndroidAdvertisingIdJniTest {

    private static final String CLIENT_CLASS =
            "com/google/android/gms/ads/identifier/AdvertisingIdClient";
    private static final String INFO_CLASS =
            "com/google/android/gms/ads/identifier/AdvertisingIdClient$Info";
    private static final String GET_INFO_ARGS =
            "(Landroid/content/Context;)Lcom/google/android/gms/ads/identifier/AdvertisingIdClient$Info;";
    private static final String GET_ID_ARGS = "()Ljava/lang/String;";
    private static final String IS_LIMIT_ARGS = "()Z";

    private static final String FULL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"identifiers\":{"
            + "\"advertisingId\":\"a1b2c3d4-e5f6-7890-abcd-ef1234567890\","
            + "\"limitAdTracking\":true"
            + "}"
            + "}"
            + "}";

    private static final String EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"identifiers\":{}"
            + "}"
            + "}";

    private static final String ABSENT_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    private static final String CROSS_A_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String CROSS_B_ID = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
    private static final String CROSS_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"identifiers\":{"
            + "\"advertisingId\":\"" + CROSS_B_ID + "\","
            + "\"limitAdTracking\":false"
            + "}"
            + "}"
            + "}";

    @Test
    public void testAdvertisingIdFullVarArg32() throws Exception {
        runConfigured(false, false, FULL_JSON,
                "a1b2c3d4-e5f6-7890-abcd-ef1234567890", true);
    }

    @Test
    public void testAdvertisingIdFullVaList64() throws Exception {
        runConfigured(true, true, FULL_JSON,
                "a1b2c3d4-e5f6-7890-abcd-ef1234567890", true);
    }

    @Test
    public void testAdvertisingIdEmptyDefaultsVarArg32() throws Exception {
        runConfigured(false, false, EMPTY_JSON,
                "00000000-0000-0000-0000-00000000a001", false);
    }

    @Test
    public void testAdvertisingIdEmptyDefaultsVaList64() throws Exception {
        runConfigured(true, true, EMPTY_JSON,
                "00000000-0000-0000-0000-00000000a001", false);
    }

    @Test
    public void testAdvertisingIdAbsentVarArg32() throws Exception {
        runAbsent(false, false);
    }

    @Test
    public void testAdvertisingIdAbsentVaList64() throws Exception {
        runAbsent(true, true);
    }

    @Test
    public void testAdvertisingIdCrossVmRejectedVarArg32() throws Exception {
        runCrossVmRejected(false, false);
    }

    @Test
    public void testAdvertisingIdCrossVmRejectedVaList64() throws Exception {
        runCrossVmRejected(true, true);
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList, String json,
                                      String expectedId, boolean expectedLimit) throws Exception {
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

            DvmObject<?> info = invokeGetAdvertisingIdInfo(jni, baseVM, useVaList);
            assertNotNull(info);
            assertEquals(INFO_CLASS, info.getObjectType().getClassName());
            assertNotNull(info.getValue());
            assertTrue(info.getValue().getClass().getName().contains("ConfiguredAdvertisingIdInfo"));

            assertEquals(expectedId, invokeGetId(jni, baseVM, useVaList, info));
            assertEquals(expectedLimit, invokeIsLimitAdTrackingEnabled(jni, baseVM, useVaList, info));

            // unknown methods on marker must UOE (no generic fallback)
            try {
                invokeObjectMethod(jni, baseVM, useVaList, info, INFO_CLASS,
                        "toString", "()Ljava/lang/String;");
                fail("expected UnsupportedOperationException for unknown object method on marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("toString"));
            }
            try {
                invokeBooleanMethod(jni, baseVM, useVaList, info, INFO_CLASS,
                        "isEmpty", "()Z");
                fail("expected UnsupportedOperationException for unknown boolean method on marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isEmpty"));
            }

            // provenance isolation: plain Info without marker is not configured path
            DvmObject<?> plainInfo = vm.resolveClass(INFO_CLASS).newObject(null);
            try {
                invokeGetId(jni, baseVM, useVaList, plainInfo);
                fail("expected UnsupportedOperationException for getId without marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getId"));
            }
            try {
                invokeIsLimitAdTrackingEnabled(jni, baseVM, useVaList, plainInfo);
                fail("expected UnsupportedOperationException for isLimitAdTrackingEnabled without marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isLimitAdTrackingEnabled"));
            }

            CapturedEvent getInfo = findLastEvent(sink.events, "android_identifier",
                    "AdvertisingIdClient.getAdvertisingIdInfo");
            assertNotNull(getInfo);
            assertEquals("json-config", getInfo.source);
            assertTrue(String.valueOf(getInfo.value).contains("advertisingId=" + expectedId));
            assertTrue(String.valueOf(getInfo.value).contains("limitAdTracking=" + expectedLimit));

            CapturedEvent getId = findLastEvent(sink.events, "android_identifier",
                    "AdvertisingIdInfo.getId");
            assertNotNull(getId);
            assertEquals("json-config", getId.source);
            assertEquals("result=" + expectedId, String.valueOf(getId.value));

            CapturedEvent limitEv = findLastEvent(sink.events, "android_identifier",
                    "AdvertisingIdInfo.isLimitAdTrackingEnabled");
            assertNotNull(limitEv);
            assertEquals("json-config", limitEv.source);
            assertEquals("result=" + expectedLimit, String.valueOf(limitEv.value));
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
                invokeGetAdvertisingIdInfo(jni, baseVM, useVaList);
                fail("expected UnsupportedOperationException without identifiers config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAdvertisingIdInfo"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_identifier event when config absent: " + e.api,
                        "android_identifier".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runCrossVmRejected(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(FULL_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(CROSS_B_JSON);
        AndroidEmulator emuA = null;
        AndroidEmulator emuB = null;
        CapturingSink sinkB = new CapturingSink();
        try {
            emuA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            emuB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configB)
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

            DvmObject<?> infoA = invokeGetAdvertisingIdInfo(jniA, baseA, useVaList);
            assertNotNull(infoA);
            assertTrue(infoA.getValue().getClass().getName().contains("ConfiguredAdvertisingIdInfo"));

            try {
                invokeGetId(jniB, baseB, useVaList, infoA);
                fail("expected UnsupportedOperationException for cross-VM getId");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getId"));
            }
            try {
                invokeIsLimitAdTrackingEnabled(jniB, baseB, useVaList, infoA);
                fail("expected UnsupportedOperationException for cross-VM isLimitAdTrackingEnabled");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isLimitAdTrackingEnabled"));
            }
            for (CapturedEvent e : sinkB.events) {
                assertFalse("leak to VM B during reject: " + e.api,
                        "android_identifier".equals(e.kind));
                String value = String.valueOf(e.value);
                assertFalse("VM B sidecar must not contain VM A advertising id during reject: " + value,
                        value.contains(CROSS_A_ID));
            }

            DvmObject<?> infoB = invokeGetAdvertisingIdInfo(jniB, baseB, useVaList);
            assertNotNull(infoB);
            assertEquals(CROSS_B_ID, invokeGetId(jniB, baseB, useVaList, infoB));
            assertFalse(invokeIsLimitAdTrackingEnabled(jniB, baseB, useVaList, infoB));

            for (CapturedEvent e : sinkB.events) {
                String value = String.valueOf(e.value);
                assertFalse("VM B sidecar must not contain VM A advertising id: " + value,
                        value.contains(CROSS_A_ID));
            }
            CapturedEvent getInfo = findLastEvent(sinkB.events, "android_identifier",
                    "AdvertisingIdClient.getAdvertisingIdInfo");
            assertNotNull(getInfo);
            assertEquals("json-config", getInfo.source);
            assertTrue(String.valueOf(getInfo.value).contains("advertisingId=" + CROSS_B_ID));
            assertTrue(String.valueOf(getInfo.value).contains("limitAdTracking=false"));

            CapturedEvent getId = findLastEvent(sinkB.events, "android_identifier",
                    "AdvertisingIdInfo.getId");
            assertNotNull(getId);
            assertEquals("json-config", getId.source);
            assertEquals("result=" + CROSS_B_ID, String.valueOf(getId.value));

            CapturedEvent limitEv = findLastEvent(sinkB.events, "android_identifier",
                    "AdvertisingIdInfo.isLimitAdTrackingEnabled");
            assertNotNull(limitEv);
            assertEquals("json-config", limitEv.source);
            assertEquals("result=false", String.valueOf(limitEv.value));
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

    private static DvmObject<?> invokeGetAdvertisingIdInfo(AbstractJni jni, BaseVM vm, boolean useVaList) {
        DvmClass dvmClass = vm.resolveClass(CLIENT_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getAdvertisingIdInfo", GET_INFO_ARGS, true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, dvmClass, signature, new TestVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, dvmClass, signature, new TestVarArg(vm, method));
    }

    private static String invokeGetId(AbstractJni jni, BaseVM vm, boolean useVaList, DvmObject<?> info) {
        DvmObject<?> result = invokeObjectMethod(jni, vm, useVaList, info, INFO_CLASS, "getId", GET_ID_ARGS);
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static boolean invokeIsLimitAdTrackingEnabled(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                          DvmObject<?> info) {
        return invokeBooleanMethod(jni, vm, useVaList, info, INFO_CLASS,
                "isLimitAdTrackingEnabled", IS_LIMIT_ARGS);
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

    private static boolean invokeBooleanMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                               DvmObject<?> receiver, String className,
                                               String methodName, String args) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, receiver, signature, new TestVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, receiver, signature, new TestVarArg(vm, method));
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
