package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Coverage for {@code android.runtime.environmentVariables} + static
 * {@code System.getenv(String)} / {@code System.getenv() Map} (VarArg + VaList;
 * configured keys only; no host fallback; independent of systemProperties / linux.environ).
 */
public class AndroidRuntimeEnvironmentVariablesJniTest {

    private static final String SYSTEM_CLASS = "java/lang/System";
    private static final String GETENV_ONE =
            "java/lang/System->getenv(Ljava/lang/String;)Ljava/lang/String;";
    private static final String GETENV_MAP =
            "java/lang/System->getenv()Ljava/util/Map;";

    private static final String ENV_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"environmentVariables\":{"
            + "\"PATH\":\"/system/bin\","
            + "\"TMPDIR\":null,"
            + "\"EMPTY\":\"\""
            + "}}"
            + "}"
            + "}";

    private static final String BOTH_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{"
            + "\"systemProperties\":{\"PATH\":\"not-env\"},"
            + "\"environmentVariables\":{\"PATH\":\"/vendor/bin\"}"
            + "}"
            + "}"
            + "}";

    private static final String WITH_LINUX_ENVIRON_JSON = "{"
            + "\"linux\":{\"environ\":[\"PATH=/linux/environ\"]},"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"environmentVariables\":{"
            + "\"PATH\":\"/android/runtime\""
            + "}}"
            + "}"
            + "}";

    private static final String EMPTY_ENV_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"environmentVariables\":{}}"
            + "}"
            + "}";

    private static final String NO_RUNTIME_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    private static final String PROPS_ONLY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"systemProperties\":{\"PATH\":\"prop-only\"}}"
            + "}"
            + "}";

    @Test
    public void testGetenvConfiguredStringVarArg32() throws Exception {
        runConfiguredGetenv(false, false, "PATH", "/system/bin");
    }

    @Test
    public void testGetenvConfiguredStringVaList64() throws Exception {
        runConfiguredGetenv(true, true, "PATH", "/system/bin");
    }

    @Test
    public void testGetenvConfiguredNullVarArg32() throws Exception {
        runConfiguredGetenv(false, false, "TMPDIR", null);
    }

    @Test
    public void testGetenvConfiguredNullVaList64() throws Exception {
        runConfiguredGetenv(true, true, "TMPDIR", null);
    }

    @Test
    public void testGetenvConfiguredEmptyStringVarArg32() throws Exception {
        runConfiguredGetenv(false, false, "EMPTY", "");
    }

    @Test
    public void testGetenvMapSizeAndGetVarArg32() throws Exception {
        runGetenvMap(false, false);
    }

    @Test
    public void testGetenvMapSizeAndGetVaList64() throws Exception {
        runGetenvMap(true, true);
    }

    @Test
    public void testGetenvMapEmptyVarArg32() throws Exception {
        runGetenvMapEmpty(false, false);
    }

    @Test
    public void testGetenvMapEmptyVaList64() throws Exception {
        runGetenvMapEmpty(true, true);
    }

    @Test
    public void testGetenvMapAbsenceVarArg32() throws Exception {
        runGetenvMapAbsence(false, false);
    }

    @Test
    public void testGetenvMapAbsenceVaList64() throws Exception {
        runGetenvMapAbsence(true, true);
    }

    @Test
    public void testGetenvMapFreshSnapshotAndSidecarVarArg32() throws Exception {
        runGetenvMapFreshAndSidecar(false, false);
    }

    @Test
    public void testGetenvMapFreshSnapshotAndSidecarVaList64() throws Exception {
        runGetenvMapFreshAndSidecar(true, true);
    }

    @Test
    public void testGetenvIndependentOfSystemPropertiesVarArg32() throws Exception {
        runIndependence(false, false);
    }

    @Test
    public void testGetenvIndependentOfSystemPropertiesVaList64() throws Exception {
        runIndependence(true, true);
    }

    @Test
    public void testGetenvIndependentOfLinuxEnvironVarArg32() throws Exception {
        runLinuxEnvironIndependence(false, false);
    }

    @Test
    public void testGetenvIndependentOfLinuxEnvironVaList64() throws Exception {
        runLinuxEnvironIndependence(true, true);
    }

    @Test
    public void testGetenvMissingKeyAndNodeVarArg32() throws Exception {
        runMissing(false, false);
    }

    @Test
    public void testGetenvMissingKeyAndNodeVaList64() throws Exception {
        runMissing(true, true);
    }

    @Test
    public void testGetenvIsolationVarArg32() throws Exception {
        runIsolation(false, false);
    }

    @Test
    public void testGetenvIsolationVaList64() throws Exception {
        runIsolation(true, true);
    }

    private static void runConfiguredGetenv(boolean is64Bit, boolean useVaList, String key,
                                            String expected) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ENV_JSON);
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
            DvmClass systemClass = vm.resolveClass(SYSTEM_CLASS);

            DvmObject<?> result = invokeGetenv(jni, baseVM, useVaList, systemClass, key);
            if (expected == null) {
                assertNull(result);
            } else {
                assertTrue(result instanceof StringObject);
                assertEquals(expected, ((StringObject) result).getValue());
            }

            CapturedEvent ev = findLastEvent(sink.events, "android_runtime", "System.getenv");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("key=" + key + ",result=" + expected, String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("HOME"));
            assertEquals(1, countEvents(sink.events, "android_runtime", "System.getenv"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIndependence(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(BOTH_JSON);
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
            DvmClass systemClass = vm.resolveClass(SYSTEM_CLASS);

            DvmObject<?> env = invokeGetenv(jni, baseVM, useVaList, systemClass, "PATH");
            assertTrue(env instanceof StringObject);
            assertEquals("/vendor/bin", ((StringObject) env).getValue());

            // getProperty PATH is independent (systemProperties value)
            DvmMethod propMethod = new DvmMethod(systemClass, "getProperty",
                    "(Ljava/lang/String;)Ljava/lang/String;", true);
            int hash = baseVM.addLocalObject(new StringObject(baseVM, "PATH"));
            DvmObject<?> prop;
            if (useVaList) {
                prop = jni.callStaticObjectMethodV(baseVM, systemClass, propMethod.getSignature(),
                        new TestObjectVaList(baseVM, propMethod, hash));
            } else {
                prop = jni.callStaticObjectMethod(baseVM, systemClass, propMethod.getSignature(),
                        new TestObjectVarArg(baseVM, propMethod, hash));
            }
            assertTrue(prop instanceof StringObject);
            assertEquals("not-env", ((StringObject) prop).getValue());

            assertEquals(1, countEvents(sink.events, "android_runtime", "System.getenv"));
            assertEquals(1, countEvents(sink.events, "android_runtime", "System.getProperty"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        // systemProperties only: getenv PATH UOE
        config = TraceEnvironmentConfig.parse(PROPS_ONLY_JSON);
        sink = new CapturingSink();
        emulator = null;
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
            DvmClass systemClass = vm.resolveClass(SYSTEM_CLASS);
            try {
                invokeGetenv(jni, baseVM, useVaList, systemClass, "PATH");
                fail("expected UOE for getenv when only systemProperties configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getenv"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected System.getenv event: " + e.api,
                        "System.getenv".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runLinuxEnvironIndependence(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(WITH_LINUX_ENVIRON_JSON);
        assertTrue(config.isLinuxEnvironConfigured());
        assertEquals("PATH=/linux/environ", config.getLinuxEnviron().get(0));

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
            DvmClass systemClass = vm.resolveClass(SYSTEM_CLASS);

            DvmObject<?> env = invokeGetenv(jni, baseVM, useVaList, systemClass, "PATH");
            assertTrue(env instanceof StringObject);
            // Java getenv uses android.runtime, not linux.environ
            assertEquals("/android/runtime", ((StringObject) env).getValue());
            CapturedEvent ev = findLastEvent(sink.events, "android_runtime", "System.getenv");
            assertNotNull(ev);
            assertEquals("key=PATH,result=/android/runtime", String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("/linux/environ"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runMissing(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ENV_JSON);
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
            DvmClass systemClass = vm.resolveClass(SYSTEM_CLASS);

            try {
                invokeGetenv(jni, baseVM, useVaList, systemClass, "HOME");
                fail("expected UOE for unconfigured env key");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getenv"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected event for missing key: " + e.api,
                        "android_runtime".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        config = TraceEnvironmentConfig.parse(EMPTY_ENV_JSON);
        sink = new CapturingSink();
        emulator = null;
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
            DvmClass systemClass = vm.resolveClass(SYSTEM_CLASS);
            try {
                invokeGetenv(jni, baseVM, useVaList, systemClass, "PATH");
                fail("expected UOE for empty environmentVariables");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getenv"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        config = TraceEnvironmentConfig.parse(NO_RUNTIME_JSON);
        sink = new CapturingSink();
        emulator = null;
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
            DvmClass systemClass = vm.resolveClass(SYSTEM_CLASS);
            try {
                invokeGetenv(jni, baseVM, useVaList, systemClass, "PATH");
                fail("expected UOE without environmentVariables");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getenv"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIsolation(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ENV_JSON);
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
            DvmClass systemClass = vm.resolveClass(SYSTEM_CLASS);

            try {
                DvmMethod method = new DvmMethod(systemClass, "getenv",
                        "(Ljava/lang/String;)Ljava/lang/String;", true);
                if (useVaList) {
                    jni.callStaticObjectMethodV(baseVM, systemClass, method.getSignature(),
                            new TestObjectVaList(baseVM, method, 0));
                } else {
                    jni.callStaticObjectMethod(baseVM, systemClass, method.getSignature(),
                            new TestObjectVarArg(baseVM, method, 0));
                }
                fail("expected UOE for null key");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getenv"));
            }

            // map overload works when environmentVariables is configured
            DvmObject<?> mapObj = invokeGetenvMap(jni, baseVM, useVaList, systemClass);
            assertNotNull(mapObj);
            assertTrue(mapObj.getValue() instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) mapObj.getValue();
            assertEquals(2, map.size());
            assertEquals("/system/bin", map.get("PATH"));

            for (CapturedEvent e : sink.events) {
                if ("android_runtime".equals(e.kind) && "System.getenv".equals(e.api)
                        && String.valueOf(e.value).startsWith("count=")) {
                    assertFalse(String.valueOf(e.value).contains("PATH"));
                }
            }

            DvmObject<?> ok = invokeGetenv(jni, baseVM, useVaList, systemClass, "PATH");
            assertTrue(ok instanceof StringObject);
            assertEquals("/system/bin", ((StringObject) ok).getValue());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetenvMap(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ENV_JSON);
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
            DvmClass systemClass = vm.resolveClass(SYSTEM_CLASS);

            DvmObject<?> mapObj = invokeGetenvMap(jni, baseVM, useVaList, systemClass);
            assertNotNull(mapObj);
            assertTrue(mapObj.getValue() instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) mapObj.getValue();
            // TMPDIR null omitted; PATH + EMPTY remain (JSON order)
            assertEquals(2, map.size());
            assertEquals("/system/bin", map.get("PATH"));
            assertEquals("", map.get("EMPTY"));
            assertFalse(map.containsKey("TMPDIR"));
            assertNull(map.get("TMPDIR"));
            // order: PATH then EMPTY
            Iterator<String> it = map.keySet().iterator();
            assertEquals("PATH", it.next());
            assertEquals("EMPTY", it.next());
            assertFalse(it.hasNext());

            CapturedEvent ev = findLastEvent(sink.events, "android_runtime", "System.getenv");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("count=2", String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("PATH"));
            assertFalse(String.valueOf(ev.value).contains("/system"));
            assertFalse(String.valueOf(ev.value).contains("EMPTY"));
            assertFalse(String.valueOf(ev.value).contains("TMPDIR"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetenvMapEmpty(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(EMPTY_ENV_JSON);
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
            DvmClass systemClass = vm.resolveClass(SYSTEM_CLASS);

            DvmObject<?> mapObj = invokeGetenvMap(jni, baseVM, useVaList, systemClass);
            assertNotNull(mapObj);
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) mapObj.getValue();
            assertTrue(map.isEmpty());
            assertEquals(0, map.size());
            CapturedEvent ev = findLastEvent(sink.events, "android_runtime", "System.getenv");
            assertNotNull(ev);
            assertEquals("count=0", String.valueOf(ev.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetenvMapAbsence(boolean is64Bit, boolean useVaList) throws Exception {
        for (String json : new String[]{NO_RUNTIME_JSON, PROPS_ONLY_JSON}) {
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
                DvmClass systemClass = vm.resolveClass(SYSTEM_CLASS);
                try {
                    invokeGetenvMap(jni, baseVM, useVaList, systemClass);
                    fail("expected UOE for getenv() map when environmentVariables absent");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getenv"));
                }
                for (CapturedEvent e : sink.events) {
                    assertFalse("android_runtime".equals(e.kind) && "System.getenv".equals(e.api));
                }
            } finally {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                    emulator.close();
                }
            }
        }
    }

    private static void runGetenvMapFreshAndSidecar(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ENV_JSON);
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
            DvmClass systemClass = vm.resolveClass(SYSTEM_CLASS);

            DvmObject<?> mapObj1 = invokeGetenvMap(jni, baseVM, useVaList, systemClass);
            @SuppressWarnings("unchecked")
            Map<String, String> m1 = (Map<String, String>) mapObj1.getValue();
            assertEquals(2, m1.size());
            m1.put("MUTATION", "should-not-persist");
            assertTrue(m1.containsKey("MUTATION"));

            DvmObject<?> mapObj2 = invokeGetenvMap(jni, baseVM, useVaList, systemClass);
            @SuppressWarnings("unchecked")
            Map<String, String> m2 = (Map<String, String>) mapObj2.getValue();
            assertEquals(2, m2.size());
            assertFalse(m2.containsKey("MUTATION"));
            assertTrue(mapObj1.getValue() != mapObj2.getValue());

            assertEquals(2, countEvents(sink.events, "android_runtime", "System.getenv"));
            for (CapturedEvent e : sink.events) {
                if ("System.getenv".equals(e.api)) {
                    String v = String.valueOf(e.value);
                    assertTrue(v.startsWith("count="));
                    assertFalse(v.contains("PATH"));
                    assertFalse(v.contains("MUTATION"));
                    assertFalse(v.contains("/system"));
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static DvmObject<?> invokeGetenv(AbstractJni jni, BaseVM vm, boolean useVaList,
                                             DvmClass systemClass, String key) {
        DvmMethod method = new DvmMethod(systemClass, "getenv",
                "(Ljava/lang/String;)Ljava/lang/String;", true);
        assertEquals(GETENV_ONE, method.getSignature());
        int hash = vm.addLocalObject(new StringObject(vm, key));
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, systemClass, method.getSignature(),
                    new TestObjectVaList(vm, method, hash));
        }
        return jni.callStaticObjectMethod(vm, systemClass, method.getSignature(),
                new TestObjectVarArg(vm, method, hash));
    }

    private static DvmObject<?> invokeGetenvMap(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                DvmClass systemClass) {
        DvmMethod method = new DvmMethod(systemClass, "getenv", "()Ljava/util/Map;", true);
        assertEquals(GETENV_MAP, method.getSignature());
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, systemClass, method.getSignature(),
                    new TestNoArgVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, systemClass, method.getSignature(),
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

    private static final class TestObjectVarArg extends VarArg {
        TestObjectVarArg(BaseVM vm, DvmMethod method, int objectHash) {
            super(vm, method);
            args.add(objectHash);
        }
    }

    private static final class TestObjectVaList extends VaList {
        TestObjectVaList(BaseVM vm, DvmMethod method, int objectHash) {
            super(vm, method);
            args.add(objectHash);
        }
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
        public void emitEnvironmentEvent(String kind, String api, Object value, String source,
                                         String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}
