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

public class AndroidTelephonyJniTest {

    private static final String TELEPHONY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"telephony\":{"
            + "\"phoneCount\":2,"
            + "\"dataNetworkType\":13,"
            + "\"phoneType\":1,"
            + "\"networkRoaming\":false,"
            + "\"networkOperator\":\"46000\","
            + "\"networkOperatorName\":\"TRACEAI_OPERATOR_MARKER_V1\","
            + "\"simOperator\":null,"
            + "\"simOperatorName\":\"China Mobile\","
            + "\"slots\":["
            + "{\"slotIndex\":0,"
            + "\"imei\":\"860000000000001\","
            + "\"meid\":\"A1000000000001\","
            + "\"deviceId\":\"DEV-SLOT0\","
            + "\"subscriberId\":\"460001111111111\","
            + "\"simSerialNumber\":\"89860000000000000001\","
            + "\"simState\":5},"
            + "{\"slotIndex\":1,"
            + "\"imei\":\"TRACEAI_IMEI_MARKER_V1\","
            + "\"meid\":null,"
            + "\"deviceId\":\"DEV-SLOT1\","
            + "\"subscriberId\":\"TRACEAI_SUBSCRIBER_ID_MARKER_V1\","
            + "\"simSerialNumber\":null,"
            + "\"simState\":1}"
            + "]"
            + "}"
            + "}"
            + "}";

    private static final String NETWORK_COUNTRY_ISO_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"telephony\":{"
            + "\"phoneCount\":1,"
            + "\"slots\":[{\"slotIndex\":0}],"
            + "\"networkCountryIso\":\"CN\""
            + "}"
            + "}"
            + "}";

    private static final String NETWORK_COUNTRY_ISO_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"telephony\":{"
            + "\"phoneCount\":1,"
            + "\"slots\":[{\"slotIndex\":0}],"
            + "\"networkCountryIso\":\"\""
            + "}"
            + "}"
            + "}";

    private static final String TELEPHONY_NO_COUNTRY_ISO_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"telephony\":{"
            + "\"phoneCount\":1,"
            + "\"slots\":[{\"slotIndex\":0}],"
            + "\"networkOperator\":\"46000\""
            + "}"
            + "}"
            + "}";

    private static final String SIM_COUNTRY_ISO_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"telephony\":{"
            + "\"phoneCount\":1,"
            + "\"slots\":[{\"slotIndex\":0}],"
            + "\"simCountryIso\":\"CN\""
            + "}"
            + "}"
            + "}";

    private static final String SIM_COUNTRY_ISO_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"telephony\":{"
            + "\"phoneCount\":1,"
            + "\"slots\":[{\"slotIndex\":0}],"
            + "\"simCountryIso\":\"\""
            + "}"
            + "}"
            + "}";

    private static final String TELEPHONY_NO_SIM_COUNTRY_ISO_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"telephony\":{"
            + "\"phoneCount\":1,"
            + "\"slots\":[{\"slotIndex\":0}],"
            + "\"simOperator\":\"46000\""
            + "}"
            + "}"
            + "}";

    private static final String DATA_NETWORK_TYPE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"telephony\":{"
            + "\"phoneCount\":1,"
            + "\"dataNetworkType\":13,"
            + "\"slots\":[{\"slotIndex\":0}]"
            + "}"
            + "}"
            + "}";

    private static final String TELEPHONY_NO_DATA_NETWORK_TYPE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"telephony\":{"
            + "\"phoneCount\":1,"
            + "\"slots\":[{\"slotIndex\":0}],"
            + "\"networkOperator\":\"46000\""
            + "}"
            + "}"
            + "}";

    private static final String DATA_STATE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"telephony\":{"
            + "\"phoneCount\":1,"
            + "\"dataState\":2,"
            + "\"slots\":[{\"slotIndex\":0}]"
            + "}"
            + "}"
            + "}";

    private static final String TELEPHONY_NO_DATA_STATE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"telephony\":{"
            + "\"phoneCount\":1,"
            + "\"slots\":[{\"slotIndex\":0}],"
            + "\"dataNetworkType\":13"
            + "}"
            + "}"
            + "}";

    private static final String DATA_ACTIVITY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"telephony\":{"
            + "\"phoneCount\":1,"
            + "\"dataActivity\":3,"
            + "\"slots\":[{\"slotIndex\":0}]"
            + "}"
            + "}"
            + "}";

    private static final String TELEPHONY_NO_DATA_ACTIVITY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"telephony\":{"
            + "\"phoneCount\":1,"
            + "\"slots\":[{\"slotIndex\":0}],"
            + "\"dataNetworkType\":13,"
            + "\"dataState\":2"
            + "}"
            + "}"
            + "}";

    @Test
    public void testTelephonyIdentifiersVarArg32() throws Exception {
        runTelephonyIdentifiers(false, false);
    }

    @Test
    public void testTelephonyIdentifiersVaList64() throws Exception {
        runTelephonyIdentifiers(true, true);
    }

    @Test
    public void testGetNetworkCountryIsoNormalizeVarArg32() throws Exception {
        runGetCountryIso(false, false, NETWORK_COUNTRY_ISO_JSON, "getNetworkCountryIso",
                "networkCountryIso", "cn");
    }

    @Test
    public void testGetNetworkCountryIsoNormalizeVaList64() throws Exception {
        runGetCountryIso(true, true, NETWORK_COUNTRY_ISO_JSON, "getNetworkCountryIso",
                "networkCountryIso", "cn");
    }

    @Test
    public void testGetNetworkCountryIsoEmptyVarArg32() throws Exception {
        runGetCountryIso(false, false, NETWORK_COUNTRY_ISO_EMPTY_JSON, "getNetworkCountryIso",
                "networkCountryIso", "");
    }

    @Test
    public void testGetNetworkCountryIsoEmptyVaList64() throws Exception {
        runGetCountryIso(true, true, NETWORK_COUNTRY_ISO_EMPTY_JSON, "getNetworkCountryIso",
                "networkCountryIso", "");
    }

    @Test
    public void testGetNetworkCountryIsoMissingKeyVarArg32() throws Exception {
        runGetCountryIsoMissing(false, false, TELEPHONY_NO_COUNTRY_ISO_JSON,
                "getNetworkOperator", "46000", "getNetworkCountryIso");
    }

    @Test
    public void testGetNetworkCountryIsoMissingKeyVaList64() throws Exception {
        runGetCountryIsoMissing(true, true, TELEPHONY_NO_COUNTRY_ISO_JSON,
                "getNetworkOperator", "46000", "getNetworkCountryIso");
    }

    @Test
    public void testGetSimCountryIsoNormalizeVarArg32() throws Exception {
        runGetCountryIso(false, false, SIM_COUNTRY_ISO_JSON, "getSimCountryIso",
                "simCountryIso", "cn");
    }

    @Test
    public void testGetSimCountryIsoNormalizeVaList64() throws Exception {
        runGetCountryIso(true, true, SIM_COUNTRY_ISO_JSON, "getSimCountryIso",
                "simCountryIso", "cn");
    }

    @Test
    public void testGetSimCountryIsoEmptyVarArg32() throws Exception {
        runGetCountryIso(false, false, SIM_COUNTRY_ISO_EMPTY_JSON, "getSimCountryIso",
                "simCountryIso", "");
    }

    @Test
    public void testGetSimCountryIsoEmptyVaList64() throws Exception {
        runGetCountryIso(true, true, SIM_COUNTRY_ISO_EMPTY_JSON, "getSimCountryIso",
                "simCountryIso", "");
    }

    @Test
    public void testGetSimCountryIsoMissingKeyVarArg32() throws Exception {
        runGetCountryIsoMissing(false, false, TELEPHONY_NO_SIM_COUNTRY_ISO_JSON,
                "getSimOperator", "46000", "getSimCountryIso");
    }

    @Test
    public void testGetSimCountryIsoMissingKeyVaList64() throws Exception {
        runGetCountryIsoMissing(true, true, TELEPHONY_NO_SIM_COUNTRY_ISO_JSON,
                "getSimOperator", "46000", "getSimCountryIso");
    }

    @Test
    public void testGetNetworkTypeSameAsDataNetworkTypeVarArg32() throws Exception {
        runGetNetworkType(false, false, DATA_NETWORK_TYPE_JSON, 13);
    }

    @Test
    public void testGetNetworkTypeSameAsDataNetworkTypeVaList64() throws Exception {
        runGetNetworkType(true, true, DATA_NETWORK_TYPE_JSON, 13);
    }

    @Test
    public void testGetNetworkTypeMissingKeyVarArg32() throws Exception {
        runGetNetworkTypeMissing(false, false);
    }

    @Test
    public void testGetNetworkTypeMissingKeyVaList64() throws Exception {
        runGetNetworkTypeMissing(true, true);
    }

    @Test
    public void testGetDataStateVarArg32() throws Exception {
        runGetDataState(false, false, DATA_STATE_JSON, 2);
    }

    @Test
    public void testGetDataStateVaList64() throws Exception {
        runGetDataState(true, true, DATA_STATE_JSON, 2);
    }

    @Test
    public void testGetDataStateMissingKeyVarArg32() throws Exception {
        runGetDataStateMissing(false, false);
    }

    @Test
    public void testGetDataStateMissingKeyVaList64() throws Exception {
        runGetDataStateMissing(true, true);
    }

    @Test
    public void testGetDataActivityVarArg32() throws Exception {
        runGetDataActivity(false, false, DATA_ACTIVITY_JSON, 3);
    }

    @Test
    public void testGetDataActivityVaList64() throws Exception {
        runGetDataActivity(true, true, DATA_ACTIVITY_JSON, 3);
    }

    @Test
    public void testGetDataActivityMissingKeyVarArg32() throws Exception {
        runGetDataActivityMissing(false, false);
    }

    @Test
    public void testGetDataActivityMissingKeyVaList64() throws Exception {
        runGetDataActivityMissing(true, true);
    }

    private static void runTelephonyIdentifiers(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TELEPHONY_JSON);
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> telephony = vm.resolveClass("android/telephony/TelephonyManager").newObject(null);

            // no-slot overloads -> slot 0
            assertEquals("DEV-SLOT0", invokeNoArgString(jni, baseVM, useVaList, telephony, "getDeviceId"));
            assertEquals("860000000000001", invokeNoArgString(jni, baseVM, useVaList, telephony, "getImei"));
            assertEquals("A1000000000001", invokeNoArgString(jni, baseVM, useVaList, telephony, "getMeid"));
            assertEquals("460001111111111", invokeNoArgString(jni, baseVM, useVaList, telephony, "getSubscriberId"));
            assertEquals("89860000000000000001",
                    invokeNoArgString(jni, baseVM, useVaList, telephony, "getSimSerialNumber"));

            // slot-arg overloads
            assertEquals("DEV-SLOT0", invokeSlotString(jni, baseVM, useVaList, telephony, "getDeviceId", 0));
            assertEquals("DEV-SLOT1", invokeSlotString(jni, baseVM, useVaList, telephony, "getDeviceId", 1));
            assertEquals("TRACEAI_IMEI_MARKER_V1",
                    invokeSlotString(jni, baseVM, useVaList, telephony, "getImei", 1));
            assertEquals("TRACEAI_SUBSCRIBER_ID_MARKER_V1",
                    invokeSlotString(jni, baseVM, useVaList, telephony, "getSubscriberId", 1));

            // explicit JSON null is handled and returns Java null
            DvmObject<?> nullMeid = invokeSlotObject(jni, baseVM, useVaList, telephony, "getMeid", 1);
            assertNull(nullMeid);
            DvmObject<?> nullSimSerial = invokeSlotObject(jni, baseVM, useVaList, telephony,
                    "getSimSerialNumber", 1);
            assertNull(nullSimSerial);

            // global operator strings
            assertEquals("46000", invokeNoArgString(jni, baseVM, useVaList, telephony, "getNetworkOperator"));
            assertEquals("TRACEAI_OPERATOR_MARKER_V1",
                    invokeNoArgString(jni, baseVM, useVaList, telephony, "getNetworkOperatorName"));
            assertEquals("China Mobile",
                    invokeNoArgString(jni, baseVM, useVaList, telephony, "getSimOperatorName"));
            assertNull(invokeNoArgObject(jni, baseVM, useVaList, telephony, "getSimOperator"));

            // scalar int / boolean
            assertEquals(2, invokeNoArgInt(jni, baseVM, useVaList, telephony, "getPhoneCount"));
            assertEquals(13, invokeNoArgInt(jni, baseVM, useVaList, telephony, "getDataNetworkType"));
            assertEquals(1, invokeNoArgInt(jni, baseVM, useVaList, telephony, "getPhoneType"));
            assertEquals(5, invokeNoArgInt(jni, baseVM, useVaList, telephony, "getSimState"));
            assertEquals(5, invokeSlotInt(jni, baseVM, useVaList, telephony, "getSimState", 0));
            assertEquals(1, invokeSlotInt(jni, baseVM, useVaList, telephony, "getSimState", 1));
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, telephony, "isNetworkRoaming"));

            // invalid / missing sim slot -> UOE
            try {
                invokeSlotInt(jni, baseVM, useVaList, telephony, "getSimState", 9);
                fail("expected UnsupportedOperationException for missing simState slot");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("TelephonyManager->getSimState"));
            }

            try {
                invokeNoArgObject(jni, baseVM, useVaList, telephony, "getSubscriberId");
            } catch (UnsupportedOperationException unexpected) {
                fail("slot0 getSubscriberId should be configured: " + unexpected.getMessage());
            }
            try {
                invokeSlotObject(jni, baseVM, useVaList, telephony, "getImei", 2);
                fail("expected UnsupportedOperationException for missing slot 2");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("TelephonyManager->getImei"));
            }
            try {
                invokeSlotObject(jni, baseVM, useVaList, telephony, "getSubscriberId", 2);
                fail("expected UnsupportedOperationException for missing slot 2 getSubscriberId");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("TelephonyManager->getSubscriberId"));
            }
            try {
                invokeSlotObject(jni, baseVM, useVaList, telephony, "getSimSerialNumber", 2);
                fail("expected UnsupportedOperationException for missing slot 2 getSimSerialNumber");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("TelephonyManager->getSimSerialNumber"));
            }

            // sparse: deviceId only + one operator; missing imei/operator keys -> UOE
            TraceEnvironmentConfig sparse = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\",\"telephony\":{"
                    + "\"phoneCount\":1,"
                    + "\"networkOperator\":\"46001\","
                    + "\"slots\":[{\"slotIndex\":0,\"deviceId\":\"ONLY\"}]}}}"
            );
            AndroidEmulator sparseEmu = null;
            try {
                sparseEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(sparse)
                        .build();
                VM sparseVm = sparseEmu.createDalvikVM();
                AbstractJni sparseJni = new AbstractJni() {
                };
                sparseVm.setJni(sparseJni);
                BaseVM sparseBase = (BaseVM) sparseVm;
                DvmObject<?> sparseTm = sparseVm.resolveClass("android/telephony/TelephonyManager").newObject(null);
                assertEquals("ONLY", invokeNoArgString(sparseJni, sparseBase, useVaList, sparseTm, "getDeviceId"));
                assertEquals("46001",
                        invokeNoArgString(sparseJni, sparseBase, useVaList, sparseTm, "getNetworkOperator"));
                try {
                    invokeNoArgObject(sparseJni, sparseBase, useVaList, sparseTm, "getImei");
                    fail("expected UnsupportedOperationException for missing imei key");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("TelephonyManager->getImei"));
                }
                try {
                    invokeNoArgObject(sparseJni, sparseBase, useVaList, sparseTm, "getSimOperatorName");
                    fail("expected UnsupportedOperationException for missing simOperatorName");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("TelephonyManager->getSimOperatorName"));
                }
                try {
                    invokeNoArgInt(sparseJni, sparseBase, useVaList, sparseTm, "getDataNetworkType");
                    fail("expected UnsupportedOperationException for missing dataNetworkType");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("TelephonyManager->getDataNetworkType"));
                }
                try {
                    invokeNoArgBoolean(sparseJni, sparseBase, useVaList, sparseTm, "isNetworkRoaming");
                    fail("expected UnsupportedOperationException for missing networkRoaming");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("TelephonyManager->isNetworkRoaming"));
                }
                try {
                    invokeNoArgInt(sparseJni, sparseBase, useVaList, sparseTm, "getSimState");
                    fail("expected UnsupportedOperationException for missing simState");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("TelephonyManager->getSimState"));
                }
            } finally {
                if (sparseEmu != null) {
                    sparseEmu.close();
                }
            }

            // no telephony section at all
            TraceEnvironmentConfig noTel = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\"}}");
            AndroidEmulator noTelEmu = null;
            try {
                noTelEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noTel)
                        .build();
                VM noTelVm = noTelEmu.createDalvikVM();
                AbstractJni noTelJni = new AbstractJni() {
                };
                noTelVm.setJni(noTelJni);
                BaseVM noTelBase = (BaseVM) noTelVm;
                DvmObject<?> noTelTm = noTelVm.resolveClass("android/telephony/TelephonyManager").newObject(null);
                try {
                    invokeNoArgObject(noTelJni, noTelBase, useVaList, noTelTm, "getDeviceId");
                    fail("expected UnsupportedOperationException without telephony config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("TelephonyManager->getDeviceId"));
                }
                try {
                    invokeNoArgObject(noTelJni, noTelBase, useVaList, noTelTm, "getNetworkOperator");
                    fail("expected UnsupportedOperationException for getNetworkOperator without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("TelephonyManager->getNetworkOperator"));
                }
            } finally {
                if (noTelEmu != null) {
                    noTelEmu.close();
                }
            }

            // zero-arg unrelated regression: must not IndexOutOfBounds from eager arg reads
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmClass appClass = app.getObjectType();
            DvmMethod getPackageName = new DvmMethod(appClass, "getPackageName", "()Ljava/lang/String;", false);
            if (useVaList) {
                TestVaList zeroArgs = new TestVaList(baseVM, getPackageName);
                DvmObject<?> pkg = jni.callObjectMethodV(baseVM, app, getPackageName.getSignature(), zeroArgs);
                assertTrue(pkg instanceof StringObject);
                assertEquals("com.demo.app", ((StringObject) pkg).getValue());
            } else {
                TestVarArg zeroArgs = new TestVarArg(baseVM, getPackageName);
                DvmObject<?> pkg = jni.callObjectMethod(baseVM, app, getPackageName.getSignature(), zeroArgs);
                assertTrue(pkg instanceof StringObject);
                assertEquals("com.demo.app", ((StringObject) pkg).getValue());
            }

            // zero-arg int path regression (String.hashCode)
            StringObject strObj = new StringObject(baseVM, "hello");
            DvmClass stringClass = strObj.getObjectType();
            DvmMethod hashCode = new DvmMethod(stringClass, "hashCode", "()I", false);
            if (useVaList) {
                int hc = jni.callIntMethodV(baseVM, strObj, hashCode.getSignature(),
                        new TestVaList(baseVM, hashCode));
                assertEquals("hello".hashCode(), hc);
            } else {
                int hc = jni.callIntMethod(baseVM, strObj, hashCode.getSignature(),
                        new TestVarArg(baseVM, hashCode));
                assertEquals("hello".hashCode(), hc);
            }
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runGetCountryIso(boolean is64Bit, boolean useVaList, String json,
                                         String methodName, String configKey, String expected)
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
            DvmObject<?> telephony = vm.resolveClass("android/telephony/TelephonyManager").newObject(null);

            DvmObject<?> first = invokeNoArgObject(jni, baseVM, useVaList, telephony, methodName);
            assertTrue(first instanceof StringObject);
            assertEquals(expected, ((StringObject) first).getValue());
            DvmObject<?> second = invokeNoArgObject(jni, baseVM, useVaList, telephony, methodName);
            assertTrue(second instanceof StringObject);
            assertEquals(expected, ((StringObject) second).getValue());
            assertTrue("each call must return a fresh StringObject", first != second);

            String api = "TelephonyManager." + methodName;
            CapturedEvent ev = findLastEvent(sink.events, "telephony", api);
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("key=" + configKey + ",result=" + expected, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(2, countEvents(sink.events, "telephony", api));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetCountryIsoMissing(boolean is64Bit, boolean useVaList, String json,
                                                String presentMethod, String presentValue,
                                                String missingMethod) throws Exception {
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
            DvmObject<?> telephony = vm.resolveClass("android/telephony/TelephonyManager").newObject(null);

            assertEquals(presentValue, invokeNoArgString(jni, baseVM, useVaList, telephony,
                    presentMethod));
            try {
                invokeNoArgObject(jni, baseVM, useVaList, telephony, missingMethod);
                fail("expected UnsupportedOperationException when " + missingMethod + " key missing");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains(missingMethod));
            }
            String missingApi = "TelephonyManager." + missingMethod;
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected " + missingMethod + " event when key missing: " + e.api,
                        missingApi.equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetNetworkType(boolean is64Bit, boolean useVaList, String json,
                                          int expected) throws Exception {
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
            DvmObject<?> telephony = vm.resolveClass("android/telephony/TelephonyManager").newObject(null);

            // same fixed value as getDataNetworkType
            assertEquals(expected, invokeNoArgInt(jni, baseVM, useVaList, telephony,
                    "getDataNetworkType"));
            assertEquals(expected, invokeNoArgInt(jni, baseVM, useVaList, telephony,
                    "getNetworkType"));

            CapturedEvent dataEv = findLastEvent(sink.events, "telephony",
                    "TelephonyManager.getDataNetworkType");
            assertNotNull(dataEv);
            assertEquals(String.valueOf(expected), String.valueOf(dataEv.value));

            CapturedEvent netEv = findLastEvent(sink.events, "telephony",
                    "TelephonyManager.getNetworkType");
            assertNotNull(netEv);
            assertEquals("json-config", netEv.source);
            assertEquals("key=dataNetworkType,result=" + expected, String.valueOf(netEv.value));
            assertNotNull(netEv.note);
            assertFalse(netEv.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "telephony",
                    "TelephonyManager.getNetworkType"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetNetworkTypeMissing(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TELEPHONY_NO_DATA_NETWORK_TYPE_JSON);
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
            DvmObject<?> telephony = vm.resolveClass("android/telephony/TelephonyManager").newObject(null);

            assertEquals("46000", invokeNoArgString(jni, baseVM, useVaList, telephony,
                    "getNetworkOperator"));
            try {
                invokeNoArgInt(jni, baseVM, useVaList, telephony, "getNetworkType");
                fail("expected UnsupportedOperationException when dataNetworkType missing");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getNetworkType"));
            }
            try {
                invokeNoArgInt(jni, baseVM, useVaList, telephony, "getDataNetworkType");
                fail("expected UnsupportedOperationException for getDataNetworkType when key missing");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDataNetworkType"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected getNetworkType event when key missing: " + e.api,
                        "TelephonyManager.getNetworkType".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetDataState(boolean is64Bit, boolean useVaList, String json,
                                        int expected) throws Exception {
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
            DvmObject<?> telephony = vm.resolveClass("android/telephony/TelephonyManager").newObject(null);

            assertEquals(expected, invokeNoArgInt(jni, baseVM, useVaList, telephony, "getDataState"));

            CapturedEvent ev = findLastEvent(sink.events, "telephony",
                    "TelephonyManager.getDataState");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("key=dataState,result=" + expected, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "telephony",
                    "TelephonyManager.getDataState"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetDataStateMissing(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TELEPHONY_NO_DATA_STATE_JSON);
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
            DvmObject<?> telephony = vm.resolveClass("android/telephony/TelephonyManager").newObject(null);

            // dataNetworkType present but dataState absent → getDataState UOE
            assertEquals(13, invokeNoArgInt(jni, baseVM, useVaList, telephony, "getDataNetworkType"));
            try {
                invokeNoArgInt(jni, baseVM, useVaList, telephony, "getDataState");
                fail("expected UnsupportedOperationException when dataState missing");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDataState"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected getDataState event when key missing: " + e.api,
                        "TelephonyManager.getDataState".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetDataActivity(boolean is64Bit, boolean useVaList, String json,
                                           int expected) throws Exception {
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
            DvmObject<?> telephony = vm.resolveClass("android/telephony/TelephonyManager").newObject(null);

            assertEquals(expected, invokeNoArgInt(jni, baseVM, useVaList, telephony, "getDataActivity"));

            CapturedEvent ev = findLastEvent(sink.events, "telephony",
                    "TelephonyManager.getDataActivity");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("key=dataActivity,result=" + expected, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "telephony",
                    "TelephonyManager.getDataActivity"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetDataActivityMissing(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TELEPHONY_NO_DATA_ACTIVITY_JSON);
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
            DvmObject<?> telephony = vm.resolveClass("android/telephony/TelephonyManager").newObject(null);

            // dataNetworkType + dataState present but dataActivity absent → UOE / no event
            assertEquals(13, invokeNoArgInt(jni, baseVM, useVaList, telephony, "getDataNetworkType"));
            assertEquals(2, invokeNoArgInt(jni, baseVM, useVaList, telephony, "getDataState"));
            try {
                invokeNoArgInt(jni, baseVM, useVaList, telephony, "getDataActivity");
                fail("expected UnsupportedOperationException when dataActivity missing");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDataActivity"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected getDataActivity event when key missing: " + e.api,
                        "TelephonyManager.getDataActivity".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
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

    private static String invokeNoArgString(AbstractJni jni, BaseVM vm, boolean useVaList,
                                            DvmObject<?> telephony, String methodName) {
        DvmObject<?> result = invokeNoArgObject(jni, vm, useVaList, telephony, methodName);
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static DvmObject<?> invokeNoArgObject(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  DvmObject<?> telephony, String methodName) {
        DvmClass dvmClass = telephony.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, "()Ljava/lang/String;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, telephony, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, telephony, signature, new TestVarArg(vm, method));
    }

    private static String invokeSlotString(AbstractJni jni, BaseVM vm, boolean useVaList,
                                           DvmObject<?> telephony, String methodName, int slot) {
        DvmObject<?> result = invokeSlotObject(jni, vm, useVaList, telephony, methodName, slot);
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static DvmObject<?> invokeSlotObject(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> telephony, String methodName, int slot) {
        DvmClass dvmClass = telephony.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, "(I)Ljava/lang/String;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, telephony, signature, new TestVaList(vm, method, slot));
        }
        return jni.callObjectMethod(vm, telephony, signature, new TestVarArg(vm, method, slot));
    }

    private static int invokeNoArgInt(AbstractJni jni, BaseVM vm, boolean useVaList,
                                      DvmObject<?> telephony, String methodName) {
        DvmClass dvmClass = telephony.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, telephony, signature, new TestVaList(vm, method));
        }
        return jni.callIntMethod(vm, telephony, signature, new TestVarArg(vm, method));
    }

    private static int invokeSlotInt(AbstractJni jni, BaseVM vm, boolean useVaList,
                                     DvmObject<?> telephony, String methodName, int slot) {
        DvmClass dvmClass = telephony.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, "(I)I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, telephony, signature, new TestVaList(vm, method, slot));
        }
        return jni.callIntMethod(vm, telephony, signature, new TestVarArg(vm, method, slot));
    }

    private static boolean invokeNoArgBoolean(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmObject<?> telephony, String methodName) {
        DvmClass dvmClass = telephony.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, telephony, signature, new TestVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, telephony, signature, new TestVarArg(vm, method));
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
