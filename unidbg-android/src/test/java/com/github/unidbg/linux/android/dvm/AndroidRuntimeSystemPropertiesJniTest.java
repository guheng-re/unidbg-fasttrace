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
 * Coverage for {@code android.runtime.systemProperties} + static
 * {@code System.getProperty(String)} / {@code System.getProperty(String,String)} /
 * {@code System.getProperties() Properties}
 * (VarArg + VaList; configured keys only; no host fallback; map omits JSON null).
 */
public class AndroidRuntimeSystemPropertiesJniTest {

    private static final String SYSTEM_CLASS = "java/lang/System";
    private static final String GET_PROP_ONE =
            "java/lang/System->getProperty(Ljava/lang/String;)Ljava/lang/String;";
    private static final String GET_PROP_TWO =
            "java/lang/System->getProperty(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
    private static final String GET_PROPERTIES_MAP =
            "java/lang/System->getProperties()Ljava/util/Properties;";

    private static final String PROPS_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"systemProperties\":{"
            + "\"os.name\":\"Linux\","
            + "\"java.vm.name\":null,"
            + "\"custom.empty\":\"\""
            + "}}"
            + "}"
            + "}";

    private static final String EMPTY_PROPS_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"systemProperties\":{}}"
            + "}"
            + "}";

    private static final String NO_RUNTIME_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    private static final String ENV_ONLY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"environmentVariables\":{\"PATH\":\"/system/bin\"}}"
            + "}"
            + "}";

    @Test
    public void testGetPropertyConfiguredStringVarArg32() throws Exception {
        runConfiguredOneArg(false, false, "os.name", "Linux");
    }

    @Test
    public void testGetPropertyConfiguredStringVaList64() throws Exception {
        runConfiguredOneArg(true, true, "os.name", "Linux");
    }

    @Test
    public void testGetPropertyConfiguredNullOneArgVarArg32() throws Exception {
        runConfiguredOneArg(false, false, "java.vm.name", null);
    }

    @Test
    public void testGetPropertyConfiguredNullOneArgVaList64() throws Exception {
        runConfiguredOneArg(true, true, "java.vm.name", null);
    }

    @Test
    public void testGetPropertyConfiguredEmptyStringVarArg32() throws Exception {
        runConfiguredOneArg(false, false, "custom.empty", "");
    }

    @Test
    public void testGetPropertyTwoArgDefaultWhenNullVarArg32() throws Exception {
        runConfiguredTwoArgDefault(false, false);
    }

    @Test
    public void testGetPropertyTwoArgDefaultWhenNullVaList64() throws Exception {
        runConfiguredTwoArgDefault(true, true);
    }

    @Test
    public void testGetPropertyTwoArgIgnoresDefaultWhenPresentVarArg32() throws Exception {
        runConfiguredTwoArgPresent(false, false);
    }

    @Test
    public void testGetPropertyTwoArgIgnoresDefaultWhenPresentVaList64() throws Exception {
        runConfiguredTwoArgPresent(true, true);
    }

    @Test
    public void testGetPropertyMissingKeyUoeVarArg32() throws Exception {
        runMissingKeyAndNode(false, false);
    }

    @Test
    public void testGetPropertyMissingKeyUoeVaList64() throws Exception {
        runMissingKeyAndNode(true, true);
    }

    @Test
    public void testGetPropertyIsolationVarArg32() throws Exception {
        runIsolation(false, false);
    }

    @Test
    public void testGetPropertyIsolationVaList64() throws Exception {
        runIsolation(true, true);
    }

    @Test
    public void testGetPropertiesMapSizeAndGetVarArg32() throws Exception {
        runGetPropertiesMap(false, false);
    }

    @Test
    public void testGetPropertiesMapSizeAndGetVaList64() throws Exception {
        runGetPropertiesMap(true, true);
    }

    @Test
    public void testGetPropertiesMapEmptyVarArg32() throws Exception {
        runGetPropertiesMapEmpty(false, false);
    }

    @Test
    public void testGetPropertiesMapEmptyVaList64() throws Exception {
        runGetPropertiesMapEmpty(true, true);
    }

    @Test
    public void testGetPropertiesMapAbsenceVarArg32() throws Exception {
        runGetPropertiesMapAbsence(false, false);
    }

    @Test
    public void testGetPropertiesMapAbsenceVaList64() throws Exception {
        runGetPropertiesMapAbsence(true, true);
    }

    @Test
    public void testGetPropertiesMapFreshSnapshotAndSidecarVarArg32() throws Exception {
        runGetPropertiesMapFreshAndSidecar(false, false);
    }

    @Test
    public void testGetPropertiesMapFreshSnapshotAndSidecarVaList64() throws Exception {
        runGetPropertiesMapFreshAndSidecar(true, true);
    }

    private static void runConfiguredOneArg(boolean is64Bit, boolean useVaList, String key,
                                            String expected) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROPS_JSON);
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

            DvmObject<?> result = invokeGetPropertyOne(jni, baseVM, useVaList, systemClass, key);
            if (expected == null) {
                assertNull(result);
            } else {
                assertTrue(result instanceof StringObject);
                assertEquals(expected, ((StringObject) result).getValue());
            }

            CapturedEvent ev = findLastEvent(sink.events, "android_runtime", "System.getProperty");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("key=" + key + ",result=" + expected, String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("os.version"));
            assertEquals(1, countEvents(sink.events, "android_runtime", "System.getProperty"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredTwoArgDefault(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROPS_JSON);
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

            // configured null → return default string
            DvmObject<?> result = invokeGetPropertyTwo(jni, baseVM, useVaList, systemClass,
                    "java.vm.name", "fallback-vm");
            assertTrue(result instanceof StringObject);
            assertEquals("fallback-vm", ((StringObject) result).getValue());

            CapturedEvent ev = findLastEvent(sink.events, "android_runtime", "System.getProperty");
            assertNotNull(ev);
            // sidecar reports configured value (null), not the default / other keys
            assertEquals("key=java.vm.name,result=null", String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("Linux"));
            assertFalse(String.valueOf(ev.value).contains("fallback-vm"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredTwoArgPresent(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROPS_JSON);
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

            DvmObject<?> result = invokeGetPropertyTwo(jni, baseVM, useVaList, systemClass,
                    "os.name", "ignored-default");
            assertTrue(result instanceof StringObject);
            assertEquals("Linux", ((StringObject) result).getValue());

            CapturedEvent ev = findLastEvent(sink.events, "android_runtime", "System.getProperty");
            assertNotNull(ev);
            assertEquals("key=os.name,result=Linux", String.valueOf(ev.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runMissingKeyAndNode(boolean is64Bit, boolean useVaList) throws Exception {
        // missing key under configured map → UOE
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROPS_JSON);
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
                invokeGetPropertyOne(jni, baseVM, useVaList, systemClass, "absent.key");
                fail("expected UOE for unconfigured key");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProperty"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_runtime event for missing key: " + e.api,
                        "android_runtime".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        // empty map configured → UOE for any key
        config = TraceEnvironmentConfig.parse(EMPTY_PROPS_JSON);
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
                invokeGetPropertyOne(jni, baseVM, useVaList, systemClass, "os.name");
                fail("expected UOE for empty systemProperties");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProperty"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        // node missing → UOE
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
                invokeGetPropertyTwo(jni, baseVM, useVaList, systemClass, "os.name", "def");
                fail("expected UOE without runtime.systemProperties");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProperty"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected event without config: " + e.api,
                        "android_runtime".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIsolation(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROPS_JSON);
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

            // null key arg → UOE
            try {
                invokeGetPropertyOneNullKey(jni, baseVM, useVaList, systemClass);
                fail("expected UOE for null key");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProperty"));
            }

            // wrong signature → UOE
            try {
                DvmMethod method = new DvmMethod(systemClass, "getenv",
                        "(Ljava/lang/String;)Ljava/lang/String;", true);
                String signature = method.getSignature();
                int hash = baseVM.addLocalObject(new StringObject(baseVM, "PATH"));
                if (useVaList) {
                    jni.callStaticObjectMethodV(baseVM, systemClass, signature,
                            new TestObjectVaList(baseVM, method, hash));
                } else {
                    jni.callStaticObjectMethod(baseVM, systemClass, signature,
                            new TestObjectVarArg(baseVM, method, hash));
                }
                fail("expected UOE for getenv");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getenv"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_runtime on isolation: " + e.api,
                        "android_runtime".equals(e.kind));
            }

            // getProperties map overload works when systemProperties is configured
            DvmObject<?> mapObj = invokeGetPropertiesMap(jni, baseVM, useVaList, systemClass);
            assertNotNull(mapObj);
            assertTrue(mapObj.getValue() instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) mapObj.getValue();
            assertEquals(2, map.size());
            assertEquals("Linux", map.get("os.name"));
            for (CapturedEvent e : sink.events) {
                if ("android_runtime".equals(e.kind) && "System.getProperties".equals(e.api)) {
                    assertEquals("count=2", String.valueOf(e.value));
                    assertFalse(String.valueOf(e.value).contains("os.name"));
                }
            }

            // control: configured key still works
            DvmObject<?> ok = invokeGetPropertyOne(jni, baseVM, useVaList, systemClass, "os.name");
            assertTrue(ok instanceof StringObject);
            assertEquals("Linux", ((StringObject) ok).getValue());
            assertEquals(1, countEvents(sink.events, "android_runtime", "System.getProperty"));
            assertEquals(1, countEvents(sink.events, "android_runtime", "System.getProperties"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetPropertiesMap(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROPS_JSON);
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

            DvmObject<?> mapObj = invokeGetPropertiesMap(jni, baseVM, useVaList, systemClass);
            assertNotNull(mapObj);
            assertTrue(mapObj.getValue() instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) mapObj.getValue();
            // java.vm.name null omitted; os.name + custom.empty remain (JSON order)
            assertEquals(2, map.size());
            assertEquals("Linux", map.get("os.name"));
            assertEquals("", map.get("custom.empty"));
            assertFalse(map.containsKey("java.vm.name"));
            assertNull(map.get("java.vm.name"));
            Iterator<String> it = map.keySet().iterator();
            assertEquals("os.name", it.next());
            assertEquals("custom.empty", it.next());
            assertFalse(it.hasNext());

            CapturedEvent ev = findLastEvent(sink.events, "android_runtime", "System.getProperties");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("count=2", String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("os.name"));
            assertFalse(String.valueOf(ev.value).contains("Linux"));
            assertFalse(String.valueOf(ev.value).contains("custom.empty"));
            assertFalse(String.valueOf(ev.value).contains("java.vm.name"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetPropertiesMapEmpty(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(EMPTY_PROPS_JSON);
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

            DvmObject<?> mapObj = invokeGetPropertiesMap(jni, baseVM, useVaList, systemClass);
            assertNotNull(mapObj);
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) mapObj.getValue();
            assertTrue(map.isEmpty());
            assertEquals(0, map.size());
            CapturedEvent ev = findLastEvent(sink.events, "android_runtime", "System.getProperties");
            assertNotNull(ev);
            assertEquals("count=0", String.valueOf(ev.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetPropertiesMapAbsence(boolean is64Bit, boolean useVaList)
            throws Exception {
        for (String json : new String[]{NO_RUNTIME_JSON, ENV_ONLY_JSON}) {
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
                    invokeGetPropertiesMap(jni, baseVM, useVaList, systemClass);
                    fail("expected UOE for getProperties when systemProperties absent");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getProperties"));
                }
                for (CapturedEvent e : sink.events) {
                    assertFalse("android_runtime".equals(e.kind)
                            && "System.getProperties".equals(e.api));
                }
            } finally {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                    emulator.close();
                }
            }
        }
    }

    private static void runGetPropertiesMapFreshAndSidecar(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROPS_JSON);
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

            DvmObject<?> mapObj1 = invokeGetPropertiesMap(jni, baseVM, useVaList, systemClass);
            @SuppressWarnings("unchecked")
            Map<String, String> m1 = (Map<String, String>) mapObj1.getValue();
            assertEquals(2, m1.size());
            m1.put("MUTATION", "should-not-persist");
            assertTrue(m1.containsKey("MUTATION"));

            DvmObject<?> mapObj2 = invokeGetPropertiesMap(jni, baseVM, useVaList, systemClass);
            @SuppressWarnings("unchecked")
            Map<String, String> m2 = (Map<String, String>) mapObj2.getValue();
            assertEquals(2, m2.size());
            assertFalse(m2.containsKey("MUTATION"));
            assertTrue(mapObj1.getValue() != mapObj2.getValue());

            assertEquals(2, countEvents(sink.events, "android_runtime", "System.getProperties"));
            for (CapturedEvent e : sink.events) {
                if ("System.getProperties".equals(e.api)) {
                    String v = String.valueOf(e.value);
                    assertTrue(v.startsWith("count="));
                    assertFalse(v.contains("os.name"));
                    assertFalse(v.contains("MUTATION"));
                    assertFalse(v.contains("Linux"));
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static DvmObject<?> invokeGetPropertyOne(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                     DvmClass systemClass, String key) {
        DvmMethod method = new DvmMethod(systemClass, "getProperty",
                "(Ljava/lang/String;)Ljava/lang/String;", true);
        assertEquals(GET_PROP_ONE, method.getSignature());
        int hash = vm.addLocalObject(new StringObject(vm, key));
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, systemClass, method.getSignature(),
                    new TestObjectVaList(vm, method, hash));
        }
        return jni.callStaticObjectMethod(vm, systemClass, method.getSignature(),
                new TestObjectVarArg(vm, method, hash));
    }

    private static DvmObject<?> invokeGetPropertyOneNullKey(AbstractJni jni, BaseVM vm,
                                                            boolean useVaList,
                                                            DvmClass systemClass) {
        DvmMethod method = new DvmMethod(systemClass, "getProperty",
                "(Ljava/lang/String;)Ljava/lang/String;", true);
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, systemClass, method.getSignature(),
                    new TestObjectVaList(vm, method, 0));
        }
        return jni.callStaticObjectMethod(vm, systemClass, method.getSignature(),
                new TestObjectVarArg(vm, method, 0));
    }

    private static DvmObject<?> invokeGetPropertyTwo(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                     DvmClass systemClass, String key,
                                                     String defaultValue) {
        DvmMethod method = new DvmMethod(systemClass, "getProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", true);
        assertEquals(GET_PROP_TWO, method.getSignature());
        int keyHash = vm.addLocalObject(new StringObject(vm, key));
        int defHash = vm.addLocalObject(new StringObject(vm, defaultValue));
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, systemClass, method.getSignature(),
                    new TestTwoObjectVaList(vm, method, keyHash, defHash));
        }
        return jni.callStaticObjectMethod(vm, systemClass, method.getSignature(),
                new TestTwoObjectVarArg(vm, method, keyHash, defHash));
    }

    private static DvmObject<?> invokeGetPropertiesMap(AbstractJni jni, BaseVM vm,
                                                       boolean useVaList, DvmClass systemClass) {
        DvmMethod method = new DvmMethod(systemClass, "getProperties",
                "()Ljava/util/Properties;", true);
        assertEquals(GET_PROPERTIES_MAP, method.getSignature());
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
