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

/**
 * Coverage for no-arg static {@code Build.getRadioVersion()} reading
 * {@code android.build.RADIO} (String only; VarArg + VaList).
 */
public class AndroidBuildRadioJniTest {

    private static final String GET_RADIO_VERSION_SIGNATURE =
            "android/os/Build->getRadioVersion()Ljava/lang/String;";
    private static final String GET_RADIO_VERSION_WRONG_SIGNATURE =
            "android/os/Build->getRadioVersion(Ljava/lang/String;)Ljava/lang/String;";
    private static final String GET_SERIAL_SIGNATURE =
            "android/os/Build->getSerial()Ljava/lang/String;";
    private static final String RADIO_FIELD_SIGNATURE =
            "android/os/Build->RADIO:Ljava/lang/String;";
    private static final String EXPECTED_RADIO = "G5300-00020-220113-B-8050611";
    private static final String EXPECTED_SERIAL = "ABCDEF0123456789";

    private static final String RADIO_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"build\":{"
            + "\"MODEL\":\"Pixel 6\","
            + "\"RADIO\":\"" + EXPECTED_RADIO + "\","
            + "\"SERIAL\":\"" + EXPECTED_SERIAL + "\""
            + "},"
            + "\"properties\":{"
            + "\"ro.baseband\":\"from-props\","
            + "\"gsm.version.baseband\":\"from-gsm\""
            + "}"
            + "}"
            + "}";

    private static final String RADIO_MISSING_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"build\":{\"MODEL\":\"Pixel 6\",\"SERIAL\":\"" + EXPECTED_SERIAL + "\"},"
            + "\"properties\":{\"ro.baseband\":\"from-props\"}"
            + "}"
            + "}";

    private static final String RADIO_NUMERIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"build\":{\"MODEL\":\"Pixel 6\",\"RADIO\":42,\"SERIAL\":\"" + EXPECTED_SERIAL + "\"}"
            + "}"
            + "}";

    @Test
    public void testGetRadioVersionConfiguredVarArg32() throws Exception {
        runGetRadioVersionConfigured(false, false);
    }

    @Test
    public void testGetRadioVersionConfiguredVaList64() throws Exception {
        runGetRadioVersionConfigured(true, true);
    }

    @Test
    public void testGetRadioVersionMissingNoEventVarArg32() throws Exception {
        runGetRadioVersionNotHandled(false, false, RADIO_MISSING_JSON, "getRadioVersion");
    }

    @Test
    public void testGetRadioVersionNumericNoEventVaList64() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(RADIO_NUMERIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit()
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass buildClass = vm.resolveClass("android/os/Build");

            try {
                invokeGetRadioVersion(jni, baseVM, true, buildClass);
                fail("expected UOE for numeric RADIO getRadioVersion");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getRadioVersion"));
            }
            assertEquals(0, countEvents(sink.events, "android_build", "Build.getRadioVersion"));

            DvmObject<?> fieldRadio = jni.getStaticObjectField(baseVM, buildClass, RADIO_FIELD_SIGNATURE);
            assertTrue(fieldRadio instanceof StringObject);
            assertEquals("42", ((StringObject) fieldRadio).getValue());
            assertEquals(0, countEvents(sink.events, "android_build", "Build.getRadioVersion"));

            DvmObject<?> serialObj = invokeGetSerial(jni, baseVM, true, buildClass);
            assertTrue(serialObj instanceof StringObject);
            assertEquals(EXPECTED_SERIAL, ((StringObject) serialObj).getValue());
            assertEquals(0, countEvents(sink.events, "android_build", "Build.getRadioVersion"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testGetRadioVersionWrongSignatureNoEvent32() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(RADIO_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for32Bit()
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass buildClass = vm.resolveClass("android/os/Build");

            try {
                jni.callStaticObjectMethod(baseVM, buildClass, GET_RADIO_VERSION_WRONG_SIGNATURE,
                        new TestNoArgVarArg(baseVM, new DvmMethod(buildClass, "getRadioVersion",
                                "(Ljava/lang/String;)Ljava/lang/String;", true)));
                fail("expected UOE for wrong getRadioVersion signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getRadioVersion"));
            }
            assertEquals(0, countEvents(sink.events, "android_build", "Build.getRadioVersion"));

            DvmObject<?> serialObj = jni.callStaticObjectMethod(baseVM, buildClass, GET_SERIAL_SIGNATURE,
                    new TestNoArgVarArg(baseVM, new DvmMethod(buildClass, "getSerial",
                            "()Ljava/lang/String;", true)));
            assertTrue(serialObj instanceof StringObject);
            assertEquals(EXPECTED_SERIAL, ((StringObject) serialObj).getValue());
            assertEquals(0, countEvents(sink.events, "android_build", "Build.getRadioVersion"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetRadioVersionConfigured(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(RADIO_JSON);
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
            DvmClass buildClass = vm.resolveClass("android/os/Build");

            DvmObject<?> radioObj = invokeGetRadioVersion(jni, baseVM, useVaList, buildClass);
            assertNotNull(radioObj);
            assertTrue(radioObj instanceof StringObject);
            assertEquals(EXPECTED_RADIO, ((StringObject) radioObj).getValue());

            CapturedEvent ev = findLastEvent(sink.events, "android_build", "Build.getRadioVersion");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("radio=" + EXPECTED_RADIO, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_build", "Build.getRadioVersion"));

            int eventsBeforeField = sink.events.size();
            DvmObject<?> fieldRadio = jni.getStaticObjectField(baseVM, buildClass, RADIO_FIELD_SIGNATURE);
            assertTrue(fieldRadio instanceof StringObject);
            assertEquals(EXPECTED_RADIO, ((StringObject) fieldRadio).getValue());
            assertEquals(1, countEvents(sink.events, "android_build", "Build.getRadioVersion"));
            for (int i = eventsBeforeField; i < sink.events.size(); i++) {
                assertFalse("field RADIO must not emit Build.getRadioVersion",
                        "Build.getRadioVersion".equals(sink.events.get(i).api));
            }

            DvmObject<?> serialObj = invokeGetSerial(jni, baseVM, useVaList, buildClass);
            assertTrue(serialObj instanceof StringObject);
            assertEquals(EXPECTED_SERIAL, ((StringObject) serialObj).getValue());
            assertEquals(1, countEvents(sink.events, "android_build", "Build.getRadioVersion"));
            CapturedEvent serialEv = findLastEvent(sink.events, "android_build", "Build.getSerial");
            assertNotNull(serialEv);
            assertEquals("serial=" + EXPECTED_SERIAL, String.valueOf(serialEv.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetRadioVersionNotHandled(boolean is64Bit, boolean useVaList,
                                                     String json, String expectedMessagePart)
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
            DvmClass buildClass = vm.resolveClass("android/os/Build");

            try {
                invokeGetRadioVersion(jni, baseVM, useVaList, buildClass);
                fail("expected UOE for getRadioVersion");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains(expectedMessagePart));
            }
            assertEquals(0, countEvents(sink.events, "android_build", "Build.getRadioVersion"));

            DvmObject<?> serialObj = invokeGetSerial(jni, baseVM, useVaList, buildClass);
            assertTrue(serialObj instanceof StringObject);
            assertEquals(EXPECTED_SERIAL, ((StringObject) serialObj).getValue());
            assertEquals(0, countEvents(sink.events, "android_build", "Build.getRadioVersion"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static DvmObject<?> invokeGetRadioVersion(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                      DvmClass buildClass) {
        DvmMethod method = new DvmMethod(buildClass, "getRadioVersion", "()Ljava/lang/String;", true);
        String signature = method.getSignature();
        assertEquals(GET_RADIO_VERSION_SIGNATURE, signature);
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, buildClass, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, buildClass, signature,
                new TestNoArgVarArg(vm, method));
    }

    private static DvmObject<?> invokeGetSerial(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                DvmClass buildClass) {
        DvmMethod method = new DvmMethod(buildClass, "getSerial", "()Ljava/lang/String;", true);
        String signature = method.getSignature();
        assertEquals(GET_SERIAL_SIGNATURE, signature);
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, buildClass, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, buildClass, signature,
                new TestNoArgVarArg(vm, method));
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

    private static final class TestNoArgVarArg extends VarArg {
        TestNoArgVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }

    private static final class TestNoArgVaList extends VaList {
        TestNoArgVaList(BaseVM vm, DvmMethod method) {
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
