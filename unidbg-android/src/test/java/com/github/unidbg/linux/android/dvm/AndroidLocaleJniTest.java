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

public class AndroidLocaleJniTest {

    private static final String LOCALE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"locale\":{"
            + "\"languageTag\":\"zh-Hans-CN\","
            + "\"timezoneId\":\"Asia/Shanghai\""
            + "}"
            + "}"
            + "}";

    private static final String LOCALE_JSON_B = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"locale\":{"
            + "\"languageTag\":\"en-US\","
            + "\"timezoneId\":\"America/New_York\""
            + "}"
            + "}"
            + "}";

    private static final String LOCALE_GET_DEFAULT = "()Ljava/util/Locale;";
    private static final String TIMEZONE_GET_DEFAULT = "()Ljava/util/TimeZone;";
    private static final String STRING_NO_ARGS = "()Ljava/lang/String;";

    @Test
    public void testAndroidLocaleVarArg32() throws Exception {
        runConfiguredLocale(false, false);
    }

    @Test
    public void testAndroidLocaleVaList64() throws Exception {
        runConfiguredLocale(true, true);
    }

    @Test
    public void testAndroidLocaleAbsentVarArg32() throws Exception {
        runAbsentLocale(false, false);
    }

    @Test
    public void testAndroidLocaleAbsentVaList64() throws Exception {
        runAbsentLocale(true, true);
    }

    @Test
    public void testAndroidLocaleCrossVmVarArg32() throws Exception {
        runCrossVmRejected(false, false);
    }

    @Test
    public void testAndroidLocaleCrossVmVaList64() throws Exception {
        runCrossVmRejected(true, true);
    }

    private static void runConfiguredLocale(boolean is64Bit, boolean useVaList) throws Exception {
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

            Locale expected = Locale.forLanguageTag("zh-Hans-CN");
            assertEquals("zh", invokeStringMethod(jni, baseVM, useVaList, localeObj,
                    "java/util/Locale", "getLanguage"));
            assertEquals("CN", invokeStringMethod(jni, baseVM, useVaList, localeObj,
                    "java/util/Locale", "getCountry"));
            assertEquals("Hans", invokeStringMethod(jni, baseVM, useVaList, localeObj,
                    "java/util/Locale", "getScript"));
            assertEquals("", invokeStringMethod(jni, baseVM, useVaList, localeObj,
                    "java/util/Locale", "getVariant"));
            assertEquals("zh-Hans-CN", invokeStringMethod(jni, baseVM, useVaList, localeObj,
                    "java/util/Locale", "toLanguageTag"));
            assertEquals(expected.toString(), invokeStringMethod(jni, baseVM, useVaList, localeObj,
                    "java/util/Locale", "toString"));

            DvmObject<?> tzObj = invokeStaticObject(jni, baseVM, useVaList,
                    "java/util/TimeZone", "getDefault", TIMEZONE_GET_DEFAULT);
            assertNotNull(tzObj);
            assertEquals("Asia/Shanghai", invokeStringMethod(jni, baseVM, useVaList, tzObj,
                    "java/util/TimeZone", "getID"));

            // provenance isolation: unsupported method on marker must throw UOE (no raw Locale cast)
            try {
                invokeStringMethod(jni, baseVM, useVaList, localeObj,
                        "java/util/Locale", "getDisplayName");
                fail("expected UnsupportedOperationException for unsupported Locale method on marker");
            } catch (UnsupportedOperationException expectedEx) {
                assertTrue(expectedEx.getMessage() != null
                        && expectedEx.getMessage().contains("getDisplayName"));
            }
            try {
                invokeStringMethod(jni, baseVM, useVaList, tzObj,
                        "java/util/TimeZone", "getDisplayName");
                fail("expected UnsupportedOperationException for unsupported TimeZone method on marker");
            } catch (UnsupportedOperationException expectedEx) {
                assertTrue(expectedEx.getMessage() != null
                        && expectedEx.getMessage().contains("getDisplayName"));
            }

            // sidecar shape
            CapturedEvent localeDefault = findLastEvent(sink.events, "android_locale", "Locale.getDefault");
            assertNotNull(localeDefault);
            assertEquals("json-config", localeDefault.source);
            assertTrue(String.valueOf(localeDefault.value).contains("languageTag=zh-Hans-CN"));
            assertTrue(String.valueOf(localeDefault.value).contains("timezoneId=Asia/Shanghai"));

            CapturedEvent lang = findLastEvent(sink.events, "android_locale", "Locale.getLanguage");
            assertNotNull(lang);
            assertEquals("json-config", lang.source);
            assertTrue(String.valueOf(lang.value).contains("languageTag=zh-Hans-CN"));
            assertTrue(String.valueOf(lang.value).contains("result=zh"));

            CapturedEvent script = findLastEvent(sink.events, "android_locale", "Locale.getScript");
            assertNotNull(script);
            assertTrue(String.valueOf(script.value).contains("result=Hans"));

            CapturedEvent tag = findLastEvent(sink.events, "android_locale", "Locale.toLanguageTag");
            assertNotNull(tag);
            assertTrue(String.valueOf(tag.value).contains("result=zh-Hans-CN"));

            CapturedEvent tzDefault = findLastEvent(sink.events, "android_locale", "TimeZone.getDefault");
            assertNotNull(tzDefault);
            assertEquals("json-config", tzDefault.source);
            assertTrue(String.valueOf(tzDefault.value).contains("timezoneId=Asia/Shanghai"));

            CapturedEvent tzId = findLastEvent(sink.events, "android_locale", "TimeZone.getID");
            assertNotNull(tzId);
            assertTrue(String.valueOf(tzId.value).contains("timezoneId=Asia/Shanghai"));
            assertTrue(String.valueOf(tzId.value).contains("result=Asia/Shanghai"));
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
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            if (useVaList) {
                // VaList Locale.getDefault still returns host Locale when android.locale absent
                DvmObject<?> localeObj = invokeStaticObject(jni, baseVM, true,
                        "java/util/Locale", "getDefault", LOCALE_GET_DEFAULT);
                assertNotNull(localeObj);
                assertTrue(localeObj.getValue() instanceof Locale);
                Locale host = (Locale) localeObj.getValue();
                assertEquals(host.getLanguage(), invokeStringMethod(jni, baseVM, true, localeObj,
                        "java/util/Locale", "getLanguage"));
                assertEquals(host.getCountry(), invokeStringMethod(jni, baseVM, true, localeObj,
                        "java/util/Locale", "getCountry"));
            } else {
                // VarArg Locale.getDefault remains unsupported when android.locale absent
                try {
                    invokeStaticObject(jni, baseVM, false,
                            "java/util/Locale", "getDefault", LOCALE_GET_DEFAULT);
                    fail("expected UnsupportedOperationException for VarArg Locale.getDefault without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("Locale->getDefault"));
                }
            }

            // TimeZone paths remain unsupported when android.locale absent
            try {
                invokeStaticObject(jni, baseVM, useVaList,
                        "java/util/TimeZone", "getDefault", TIMEZONE_GET_DEFAULT);
                fail("expected UnsupportedOperationException for TimeZone.getDefault without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("TimeZone->getDefault"));
            }
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runCrossVmRejected(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(LOCALE_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(LOCALE_JSON_B);
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

            DvmObject<?> localeA = invokeStaticObject(jniA, baseA, useVaList,
                    "java/util/Locale", "getDefault", LOCALE_GET_DEFAULT);
            expectStringMethodUoe(jniB, baseB, useVaList, localeA,
                    "java/util/Locale", "getLanguage", "Locale->getLanguage",
                    "cross-VM Locale getLanguage");
            expectStringMethodUoe(jniB, baseB, useVaList, localeA,
                    "java/util/Locale", "toLanguageTag", "Locale->toLanguageTag",
                    "cross-VM Locale toLanguageTag");
            expectStringMethodUoe(jniB, baseB, useVaList, localeA,
                    "java/util/Locale", "getISO3Language", "Locale->getISO3Language",
                    "cross-VM Locale getISO3Language");
            for (CapturedEvent e : sinkB.events) {
                assertFalse("leak to VM B: " + e.api, "android_locale".equals(e.kind));
                String value = String.valueOf(e.value);
                assertFalse("VM B sidecar must not contain VM A locale: " + value,
                        value.contains("zh-Hans-CN") || value.contains("zho")
                                || value.contains("Asia/Shanghai"));
            }

            DvmObject<?> localeB = invokeStaticObject(jniB, baseB, useVaList,
                    "java/util/Locale", "getDefault", LOCALE_GET_DEFAULT);
            assertEquals("en", invokeStringMethod(jniB, baseB, useVaList, localeB,
                    "java/util/Locale", "getLanguage"));
            assertEquals("en-US", invokeStringMethod(jniB, baseB, useVaList, localeB,
                    "java/util/Locale", "toLanguageTag"));
            assertEquals("eng", invokeStringMethod(jniB, baseB, useVaList, localeB,
                    "java/util/Locale", "getISO3Language"));
            for (CapturedEvent e : sinkB.events) {
                String value = String.valueOf(e.value);
                assertFalse("VM B sidecar must not contain VM A locale: " + value,
                        value.contains("zh-Hans-CN") || value.contains("zho")
                                || value.contains("Asia/Shanghai"));
            }
            CapturedEvent langB = findLastEvent(sinkB.events, "android_locale", "Locale.getLanguage");
            assertNotNull(langB);
            assertTrue(String.valueOf(langB.value).contains("languageTag=en-US"));
            assertTrue(String.valueOf(langB.value).contains("result=en"));
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

    private static void expectStringMethodUoe(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmObject<?> receiver, String className, String methodName,
                                              String messageNeedle, String label) {
        try {
            invokeStringMethod(jni, vm, useVaList, receiver, className, methodName);
            fail("expected UnsupportedOperationException for " + label);
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains(messageNeedle));
        }
    }

    private static DvmObject<?> invokeStaticObject(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   String className, String methodName, String args) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, true);
        String signature = method.getSignature();
        if (useVaList) {
            TestVaList vaList = new TestVaList(vm, method);
            return jni.callStaticObjectMethodV(vm, dvmClass, signature, vaList);
        }
        TestVarArg varArg = new TestVarArg(vm, method);
        return jni.callStaticObjectMethod(vm, dvmClass, signature, varArg);
    }

    private static String invokeStringMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                             DvmObject<?> receiver, String className, String methodName) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, STRING_NO_ARGS, false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            TestVaList vaList = new TestVaList(vm, method);
            result = jni.callObjectMethodV(vm, receiver, signature, vaList);
        } else {
            TestVarArg varArg = new TestVarArg(vm, method);
            result = jni.callObjectMethod(vm, receiver, signature, varArg);
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
