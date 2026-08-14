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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AndroidConfigurationLocaleFieldJniTest {

    private static final String BOTH_NODES_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"configuration\":{},"
            + "\"locale\":{"
            + "\"languageTag\":\"zh-Hans-CN\""
            + "}"
            + "}"
            + "}";

    private static final String CONFIGURATION_ONLY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"configuration\":{}"
            + "}"
            + "}";

    private static final String GET_CONFIGURATION =
            "()Landroid/content/res/Configuration;";
    private static final String LOCALE_FIELD =
            "android/content/res/Configuration->locale:Ljava/util/Locale;";
    private static final String STRING_NO_ARGS = "()Ljava/lang/String;";

    @Test
    public void testConfigurationLocaleFieldVarArg32() throws Exception {
        runConfiguredLocaleField(false, false);
    }

    @Test
    public void testConfigurationLocaleFieldVaList64() throws Exception {
        runConfiguredLocaleField(true, true);
    }

    @Test
    public void testConfigurationLocaleFieldAbsentLocaleVarArg32() throws Exception {
        runAbsentLocale(false, false);
    }

    @Test
    public void testConfigurationLocaleFieldAbsentLocaleVaList64() throws Exception {
        runAbsentLocale(true, true);
    }

    private static void runConfiguredLocaleField(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(BOTH_NODES_JSON);
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

            DvmObject<?> resources = vm.resolveClass("android/content/res/Resources").newObject(null);
            DvmObject<?> configuration = invokeGetConfiguration(jni, baseVM, useVaList, resources);
            assertNotNull(configuration);
            assertTrue(configuration.getValue().getClass().getName().contains("ConfiguredConfiguration"));

            DvmObject<?> localeObj = jni.getObjectField(baseVM, configuration, LOCALE_FIELD);
            assertNotNull(localeObj);
            assertFalse("Configuration.locale must not carry raw host Locale",
                    localeObj.getValue() instanceof Locale);
            assertTrue(localeObj.getValue().getClass().getName().contains("ConfiguredLocale"));

            assertEquals("zh", invokeStringMethod(jni, baseVM, useVaList, localeObj,
                    "java/util/Locale", "getLanguage"));
            assertEquals("CN", invokeStringMethod(jni, baseVM, useVaList, localeObj,
                    "java/util/Locale", "getCountry"));
            assertEquals("Hans", invokeStringMethod(jni, baseVM, useVaList, localeObj,
                    "java/util/Locale", "getScript"));
            assertEquals("zh-Hans-CN", invokeStringMethod(jni, baseVM, useVaList, localeObj,
                    "java/util/Locale", "toLanguageTag"));

            CapturedEvent localeField = findLastEvent(sink.events, "android_configuration",
                    "Configuration.locale");
            assertNotNull(localeField);
            assertEquals("json-config", localeField.source);
            assertEquals("languageTag=zh-Hans-CN", String.valueOf(localeField.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentLocale(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURATION_ONLY_JSON);
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

            DvmObject<?> resources = vm.resolveClass("android/content/res/Resources").newObject(null);
            DvmObject<?> configuration = invokeGetConfiguration(jni, baseVM, useVaList, resources);
            assertNotNull(configuration);
            try {
                jni.getObjectField(baseVM, configuration, LOCALE_FIELD);
                fail("expected UnsupportedOperationException for Configuration.locale without android.locale");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("locale"));
            }
            assertNull(findLastEvent(sink.events, "android_configuration", "Configuration.locale"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static DvmObject<?> invokeGetConfiguration(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> resources) {
        DvmClass dvmClass = vm.resolveClass("android/content/res/Resources");
        DvmMethod method = new DvmMethod(dvmClass, "getConfiguration", GET_CONFIGURATION, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, resources, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, resources, signature, new TestVarArg(vm, method));
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
