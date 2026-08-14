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
 * Coverage for {@code android.identifiers.androidId} via
 * {@code Settings.Secure.getString(resolver, "android_id")} (VarArg 32 + VaList 64).
 */
public class AndroidIdentifiersAndroidIdJniTest {

    private static final String SECURE_CLASS = "android/provider/Settings$Secure";
    private static final String GET_STRING_SIG =
            "android/provider/Settings$Secure->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;";
    private static final String SYSTEM_GET_STRING_SIG =
            "android/provider/Settings$System->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;";

    private static final String ANDROID_ID_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"identifiers\":{\"androidId\":\"9774D56D682E549C\"}"
            + "}"
            + "}";

    private static final String ANDROID_ID_LOWER_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"identifiers\":{\"androidId\":\"aabbccddeeff0011\"}"
            + "}"
            + "}";

    private static final String IDENTIFIERS_NO_ANDROID_ID_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"identifiers\":{\"limitAdTracking\":false}"
            + "}"
            + "}";

    private static final String NO_IDENTIFIERS_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testAndroidIdSecureGetStringVarArg32() throws Exception {
        runConfigured(false, false, ANDROID_ID_JSON, "9774d56d682e549c");
    }

    @Test
    public void testAndroidIdSecureGetStringVaList64() throws Exception {
        runConfigured(true, true, ANDROID_ID_JSON, "9774d56d682e549c");
    }

    @Test
    public void testAndroidIdNormalizationLowerVarArg32() throws Exception {
        runConfigured(false, false, ANDROID_ID_LOWER_JSON, "aabbccddeeff0011");
    }

    @Test
    public void testAndroidIdMissingConfigVarArg32() throws Exception {
        runMissing(false, false, NO_IDENTIFIERS_JSON);
        runMissing(false, false, IDENTIFIERS_NO_ANDROID_ID_JSON);
    }

    @Test
    public void testAndroidIdMissingConfigVaList64() throws Exception {
        runMissing(true, true, NO_IDENTIFIERS_JSON);
        runMissing(true, true, IDENTIFIERS_NO_ANDROID_ID_JSON);
    }

    @Test
    public void testAndroidIdOtherKeyAndWrongSignatureVarArg32() throws Exception {
        runOtherKeyAndWrongSignature(false, false);
    }

    @Test
    public void testAndroidIdOtherKeyAndWrongSignatureVaList64() throws Exception {
        runOtherKeyAndWrongSignature(true, true);
    }

    @Test
    public void testAndroidIdCrossVmIsolationVarArg32() throws Exception {
        runCrossVmIsolation(false, false);
    }

    @Test
    public void testAndroidIdCrossVmIsolationVaList64() throws Exception {
        runCrossVmIsolation(true, true);
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList, String json,
                                      String expectedNormalized) throws Exception {
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
            DvmClass secureClass = vm.resolveClass(SECURE_CLASS);
            DvmObject<?> resolver = vm.resolveClass("android/content/ContentResolver").newObject(null);

            DvmObject<?> result = invokeSecureGetString(jni, baseVM, useVaList, secureClass,
                    resolver, "android_id");
            assertTrue(result instanceof StringObject);
            assertEquals(expectedNormalized, ((StringObject) result).getValue());

            CapturedEvent ev = findLastEvent(sink.events, "android_identifier",
                    "Settings.Secure.getString");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("key=android_id,result=" + expectedNormalized, String.valueOf(ev.value));
            assertEquals(1, countEvents(sink.events, "android_identifier",
                    "Settings.Secure.getString"));
            // no android_setting event for this path
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_setting: " + e.api,
                        "android_setting".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runMissing(boolean is64Bit, boolean useVaList, String json)
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
            DvmClass secureClass = vm.resolveClass(SECURE_CLASS);
            DvmObject<?> resolver = vm.resolveClass("android/content/ContentResolver").newObject(null);

            try {
                invokeSecureGetString(jni, baseVM, useVaList, secureClass, resolver, "android_id");
                fail("expected UOE without identifiers.androidId");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getString"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_identifier event: " + e.api,
                        "android_identifier".equals(e.kind)
                                && "Settings.Secure.getString".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runOtherKeyAndWrongSignature(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ANDROID_ID_JSON);
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
            DvmClass secureClass = vm.resolveClass(SECURE_CLASS);
            DvmObject<?> resolver = vm.resolveClass("android/content/ContentResolver").newObject(null);

            // other Secure key → not handled by androidId path (and no settings config) → UOE
            try {
                invokeSecureGetString(jni, baseVM, useVaList, secureClass, resolver,
                        "android_id_extra");
                fail("expected UOE for non-android_id key");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getString"));
            }

            // Settings.System.getString not wired for identifiers.androidId
            DvmClass systemClass = vm.resolveClass("android/provider/Settings$System");
            try {
                invokeGetString(jni, baseVM, useVaList, systemClass, SYSTEM_GET_STRING_SIG,
                        resolver, "android_id");
                fail("expected UOE for Settings.System.getString");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getString"));
            }

            // null / non-String key: identifiers path notHandled (no event, no fallback).
            // Settings getString path may then throw IAE on bad arg1 type, or UOE if unconfigured.
            try {
                invokeSecureGetStringNullKey(jni, baseVM, useVaList, secureClass, resolver);
                fail("expected failure for null key");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("StringObject"));
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getString"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_identifier event: " + e.api,
                        "android_identifier".equals(e.kind)
                                && "Settings.Secure.getString".equals(e.api));
            }

            // control
            DvmObject<?> ok = invokeSecureGetString(jni, baseVM, useVaList, secureClass,
                    resolver, "android_id");
            assertTrue(ok instanceof StringObject);
            assertEquals("9774d56d682e549c", ((StringObject) ok).getValue());
            assertEquals(1, countEvents(sink.events, "android_identifier",
                    "Settings.Secure.getString"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runCrossVmIsolation(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(ANDROID_ID_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(NO_IDENTIFIERS_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configB)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmClass secureA = vmA.resolveClass(SECURE_CLASS);
            DvmObject<?> resolverA = vmA.resolveClass("android/content/ContentResolver").newObject(null);
            DvmObject<?> idA = invokeSecureGetString(jniA, baseA, useVaList, secureA, resolverA,
                    "android_id");
            assertTrue(idA instanceof StringObject);
            assertEquals("9774d56d682e549c", ((StringObject) idA).getValue());
            assertEquals(1, countEvents(sinkA.events, "android_identifier",
                    "Settings.Secure.getString"));

            DvmClass secureB = vmB.resolveClass(SECURE_CLASS);
            DvmObject<?> resolverB = vmB.resolveClass("android/content/ContentResolver").newObject(null);
            try {
                invokeSecureGetString(jniB, baseB, useVaList, secureB, resolverB, "android_id");
                fail("expected UOE on VM B without androidId");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getString"));
            }
            for (CapturedEvent e : sinkB.events) {
                assertFalse("leak to VM B: " + e.api,
                        "android_identifier".equals(e.kind)
                                && "Settings.Secure.getString".equals(e.api));
            }
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static DvmObject<?> invokeSecureGetString(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                      DvmClass secureClass, DvmObject<?> resolver,
                                                      String key) {
        return invokeGetString(jni, vm, useVaList, secureClass, GET_STRING_SIG, resolver, key);
    }

    private static DvmObject<?> invokeGetString(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                DvmClass settingsClass, String signature,
                                                DvmObject<?> resolver, String key) {
        DvmMethod method = new DvmMethod(settingsClass, "getString",
                "(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;", true);
        assertEquals(signature, method.getSignature());
        int resolverHash = vm.addLocalObject(resolver);
        int keyHash = vm.addLocalObject(new StringObject(vm, key));
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, settingsClass, signature,
                    new TestTwoObjectVaList(vm, method, resolverHash, keyHash));
        }
        return jni.callStaticObjectMethod(vm, settingsClass, signature,
                new TestTwoObjectVarArg(vm, method, resolverHash, keyHash));
    }

    private static DvmObject<?> invokeSecureGetStringNullKey(AbstractJni jni, BaseVM vm,
                                                             boolean useVaList,
                                                             DvmClass secureClass,
                                                             DvmObject<?> resolver) {
        DvmMethod method = new DvmMethod(secureClass, "getString",
                "(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;", true);
        int resolverHash = vm.addLocalObject(resolver);
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, secureClass, method.getSignature(),
                    new TestTwoObjectVaList(vm, method, resolverHash, 0));
        }
        return jni.callStaticObjectMethod(vm, secureClass, method.getSignature(),
                new TestTwoObjectVarArg(vm, method, resolverHash, 0));
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

    private static final class TestTwoObjectVarArg extends VarArg {
        TestTwoObjectVarArg(BaseVM vm, DvmMethod method, int hash0, int hash1) {
            super(vm, method);
            args.add(hash0);
            args.add(hash1);
        }
    }

    private static final class TestTwoObjectVaList extends VaList {
        TestTwoObjectVaList(BaseVM vm, DvmMethod method, int hash0, int hash1) {
            super(vm, method);
            args.add(hash0);
            args.add(hash1);
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
        public void emitEnvironmentEvent(String kind, String api, Object value, String source,
                                         String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}
