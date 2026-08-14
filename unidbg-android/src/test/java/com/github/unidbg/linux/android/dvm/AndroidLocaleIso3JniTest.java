package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AndroidLocaleIso3JniTest {

    private static final String LOCALE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"locale\":{"
            + "\"languageTag\":\"zh-Hans-CN\","
            + "\"timezoneId\":\"Asia/Shanghai\""
            + "}"
            + "}"
            + "}";

    private static final String LOCALE_GET_DEFAULT = "()Ljava/util/Locale;";
    private static final String STRING_NO_ARGS = "()Ljava/lang/String;";
    private static final String EXPECTED_ISO3_LANGUAGE = "zho";
    private static final String EXPECTED_ISO3_COUNTRY = "CHN";

    @Test
    public void testLocaleIso3VarArg32() throws Exception {
        runConfiguredIso3(false, false);
    }

    @Test
    public void testLocaleIso3VaList64() throws Exception {
        runConfiguredIso3(true, true);
    }

    @Test
    public void testLocaleIso3AbsentVarArg32() throws Exception {
        runAbsentLocale(false, false);
    }

    @Test
    public void testLocaleIso3AbsentVaList64() throws Exception {
        runAbsentLocale(true, true);
    }

    private static void runConfiguredIso3(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LOCALE_JSON);
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

            DvmObject<?> localeObj = invokeStaticObject(jni, baseVM, useVaList,
                    "java/util/Locale", "getDefault", LOCALE_GET_DEFAULT);
            assertNotNull(localeObj);
            assertFalse("configured Locale must not carry raw host Locale",
                    localeObj.getValue() instanceof Locale);
            assertTrue(localeObj.getValue().getClass().getName().contains("ConfiguredLocale"));

            assertEquals(EXPECTED_ISO3_LANGUAGE, invokeStringMethod(jni, baseVM, useVaList, localeObj,
                    "java/util/Locale", "getISO3Language"));
            assertEquals(EXPECTED_ISO3_COUNTRY, invokeStringMethod(jni, baseVM, useVaList, localeObj,
                    "java/util/Locale", "getISO3Country"));

            CapturedEvent iso3Lang = findLastEvent(sink.events, "android_locale", "Locale.getISO3Language");
            assertNotNull(iso3Lang);
            assertEquals("json-config", iso3Lang.source);
            assertEquals("languageTag=zh-Hans-CN,result=" + EXPECTED_ISO3_LANGUAGE,
                    String.valueOf(iso3Lang.value));

            CapturedEvent iso3Country = findLastEvent(sink.events, "android_locale", "Locale.getISO3Country");
            assertNotNull(iso3Country);
            assertEquals("json-config", iso3Country.source);
            assertEquals("languageTag=zh-Hans-CN,result=" + EXPECTED_ISO3_COUNTRY,
                    String.valueOf(iso3Country.value));

            int iso3LangAfterMarker = countEvents(sink.events, "android_locale", "Locale.getISO3Language");
            int iso3CountryAfterMarker = countEvents(sink.events, "android_locale", "Locale.getISO3Country");
            DvmObject<?> ordinary = vm.resolveClass("java/util/Locale")
                    .newObject(Locale.forLanguageTag("zh-Hans-CN"));
            try {
                invokeStringMethod(jni, baseVM, useVaList, ordinary,
                        "java/util/Locale", "getISO3Language");
                fail("expected UnsupportedOperationException for ordinary Locale getISO3Language");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("Locale->getISO3Language"));
            }
            try {
                invokeStringMethod(jni, baseVM, useVaList, ordinary,
                        "java/util/Locale", "getISO3Country");
                fail("expected UnsupportedOperationException for ordinary Locale getISO3Country");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("Locale->getISO3Country"));
            }
            assertEquals(iso3LangAfterMarker,
                    countEvents(sink.events, "android_locale", "Locale.getISO3Language"));
            assertEquals(iso3CountryAfterMarker,
                    countEvents(sink.events, "android_locale", "Locale.getISO3Country"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentLocale(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
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

            DvmObject<?> ordinary = vm.resolveClass("java/util/Locale")
                    .newObject(Locale.forLanguageTag("zh-Hans-CN"));
            try {
                invokeStringMethod(jni, baseVM, useVaList, ordinary,
                        "java/util/Locale", "getISO3Language");
                fail("expected UnsupportedOperationException for getISO3Language without android.locale");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("Locale->getISO3Language"));
            }
            try {
                invokeStringMethod(jni, baseVM, useVaList, ordinary,
                        "java/util/Locale", "getISO3Country");
                fail("expected UnsupportedOperationException for getISO3Country without android.locale");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("Locale->getISO3Country"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_locale sidecar without android.locale: " + e.api,
                        "android_locale".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static DvmObject<?> invokeStaticObject(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   String className, String methodName, String args) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, dvmClass, signature, new TestVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, dvmClass, signature, new TestVarArg(vm, method));
    }

    private static String invokeStringMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                             DvmObject<?> receiver, String className, String methodName) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, STRING_NO_ARGS, false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, receiver, signature, new TestVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, receiver, signature, new TestVarArg(vm, method));
        }
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
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
