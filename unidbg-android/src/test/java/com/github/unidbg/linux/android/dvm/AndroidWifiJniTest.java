package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.dvm.api.SystemService;
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

public class AndroidWifiJniTest {

    /** 192.168.50.23 as Android WifiInfo little-endian int: a|(b<<8)|(c<<16)|(d<<24). */
    private static final int IP_192_168_50_23 = 192 | (168 << 8) | (50 << 16) | (23 << 24);

    /** WifiManager.WIFI_STATE_ENABLED */
    private static final int WIFI_STATE_ENABLED = 3;
    /** WifiManager.WIFI_STATE_DISABLED */
    private static final int WIFI_STATE_DISABLED = 1;

    private static final String WIFI_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"},"
            + "\"network\":{\"wifi\":{"
            + "\"enabled\":false,"
            + "\"state\":1,"
            + "\"ssid\":\"TRACEAI_WIFI_SSID_V1\","
            + "\"bssid\":null,"
            + "\"macAddress\":\"AA:BB:CC:DD:EE:FF\","
            + "\"ipv4\":\"192.168.50.23\","
            + "\"rssi\":-55,"
            + "\"linkSpeedMbps\":433,"
            + "\"frequencyMhz\":5180,"
            + "\"networkId\":-1"
            + "}}"
            + "}";

    @Test
    public void testWifiJniVarArg32() throws Exception {
        runWifiJni(false, false);
    }

    @Test
    public void testWifiJniVaList64() throws Exception {
        runWifiJni(true, true);
    }

    @Test
    public void testWifiStateIndependentVarArg32() throws Exception {
        runWifiStateIndependent(false, false);
    }

    @Test
    public void testWifiStateIndependentVaList64() throws Exception {
        runWifiStateIndependent(true, true);
    }

    @Test
    public void testWifiStateMissingFieldVarArg32() throws Exception {
        runWifiStateMissingField(false, false);
    }

    @Test
    public void testWifiStateMissingFieldVaList64() throws Exception {
        runWifiStateMissingField(true, true);
    }

    @Test
    public void testWifiTypedGetSystemServiceVarArg32() throws Exception {
        runWifiTypedGetSystemService(false, false);
    }

    @Test
    public void testWifiTypedGetSystemServiceVaList64() throws Exception {
        runWifiTypedGetSystemService(true, true);
    }

    private static void runWifiJni(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(WIFI_JSON);
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

            DvmObject<?> wifiManager = vm.resolveClass("android/net/wifi/WifiManager").newObject(null);

            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, wifiManager, "isWifiEnabled"));

            assertEquals(WIFI_STATE_DISABLED,
                    invokeNoArgInt(jni, baseVM, useVaList, wifiManager, "getWifiState"));
            CapturedEvent stateEv = findLastEvent(sink.events, "network_wifi",
                    "WifiManager.getWifiState");
            assertNotNull(stateEv);
            assertEquals("json-config", stateEv.source);
            assertEquals("key=state,result=" + WIFI_STATE_DISABLED, String.valueOf(stateEv.value));
            assertNotNull(stateEv.note);
            assertFalse(stateEv.note.isEmpty());

            DvmObject<?> connectionInfo = invokeNoArgObject(jni, baseVM, useVaList, wifiManager,
                    "getConnectionInfo", "()Landroid/net/wifi/WifiInfo;");
            assertNotNull(connectionInfo);
            assertEquals("android/net/wifi/WifiInfo", connectionInfo.getObjectType().getClassName());

            assertEquals("TRACEAI_WIFI_SSID_V1",
                    invokeNoArgString(jni, baseVM, useVaList, connectionInfo, "getSSID"));
            assertNull(invokeNoArgObject(jni, baseVM, useVaList, connectionInfo,
                    "getBSSID", "()Ljava/lang/String;"));
            assertEquals("aa:bb:cc:dd:ee:ff",
                    invokeNoArgString(jni, baseVM, useVaList, connectionInfo, "getMacAddress"));

            // integer getters
            assertEquals(IP_192_168_50_23,
                    invokeNoArgInt(jni, baseVM, useVaList, connectionInfo, "getIpAddress"));
            assertEquals(-55, invokeNoArgInt(jni, baseVM, useVaList, connectionInfo, "getRssi"));
            assertEquals(433, invokeNoArgInt(jni, baseVM, useVaList, connectionInfo, "getLinkSpeed"));
            assertEquals(5180, invokeNoArgInt(jni, baseVM, useVaList, connectionInfo, "getFrequency"));
            assertEquals(-1, invokeNoArgInt(jni, baseVM, useVaList, connectionInfo, "getNetworkId"));

            // null ipv4 handled as 0; missing int key -> UOE
            TraceEnvironmentConfig sparse = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\"},"
                    + "\"network\":{\"wifi\":{\"enabled\":true,\"ipv4\":null}}}"
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
                DvmObject<?> sparseWm = sparseVm.resolveClass("android/net/wifi/WifiManager").newObject(null);
                assertTrue(invokeNoArgBoolean(sparseJni, sparseBase, useVaList, sparseWm, "isWifiEnabled"));
                DvmObject<?> sparseInfo = invokeNoArgObject(sparseJni, sparseBase, useVaList, sparseWm,
                        "getConnectionInfo", "()Landroid/net/wifi/WifiInfo;");
                assertNotNull(sparseInfo);
                assertEquals(0, invokeNoArgInt(sparseJni, sparseBase, useVaList, sparseInfo, "getIpAddress"));
                try {
                    invokeNoArgObject(sparseJni, sparseBase, useVaList, sparseInfo,
                            "getSSID", "()Ljava/lang/String;");
                    fail("expected UnsupportedOperationException for missing ssid");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("WifiInfo->getSSID"));
                }
                try {
                    invokeNoArgInt(sparseJni, sparseBase, useVaList, sparseInfo, "getRssi");
                    fail("expected UnsupportedOperationException for missing rssi");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("WifiInfo->getRssi"));
                }
                try {
                    invokeNoArgInt(sparseJni, sparseBase, useVaList, sparseWm, "getWifiState");
                    fail("expected UnsupportedOperationException for missing state");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("WifiManager->getWifiState"));
                }
            } finally {
                if (sparseEmu != null) {
                    sparseEmu.close();
                }
            }

            // no wifi config at all
            TraceEnvironmentConfig noWifi = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\"}}");
            AndroidEmulator noWifiEmu = null;
            CapturingSink noWifiSink = new CapturingSink();
            try {
                noWifiEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noWifi)
                        .build();
                TraceEnvironmentEventSink.register(noWifiEmu, noWifiSink);
                VM noWifiVm = noWifiEmu.createDalvikVM();
                AbstractJni noWifiJni = new AbstractJni() {
                };
                noWifiVm.setJni(noWifiJni);
                BaseVM noWifiBase = (BaseVM) noWifiVm;
                DvmObject<?> noWm = noWifiVm.resolveClass("android/net/wifi/WifiManager").newObject(null);
                try {
                    invokeNoArgObject(noWifiJni, noWifiBase, useVaList, noWm,
                            "getConnectionInfo", "()Landroid/net/wifi/WifiInfo;");
                    fail("expected UnsupportedOperationException without wifi config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("WifiManager->getConnectionInfo"));
                }
                try {
                    invokeNoArgBoolean(noWifiJni, noWifiBase, useVaList, noWm, "isWifiEnabled");
                    fail("expected UnsupportedOperationException for isWifiEnabled without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("WifiManager->isWifiEnabled"));
                }
                try {
                    invokeNoArgInt(noWifiJni, noWifiBase, useVaList, noWm, "getWifiState");
                    fail("expected UnsupportedOperationException for getWifiState without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("WifiManager->getWifiState"));
                }
                for (CapturedEvent e : noWifiSink.events) {
                    assertFalse("unexpected network_wifi event when wifi absent: " + e.api,
                            "network_wifi".equals(e.kind));
                }
            } finally {
                if (noWifiEmu != null) {
                    TraceEnvironmentEventSink.unregister(noWifiEmu, noWifiSink);
                    noWifiEmu.close();
                }
            }

            // zero-arg unrelated regression (object + int)
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmClass appClass = app.getObjectType();
            DvmMethod getPackageName = new DvmMethod(appClass, "getPackageName", "()Ljava/lang/String;", false);
            if (useVaList) {
                DvmObject<?> pkg = jni.callObjectMethodV(baseVM, app, getPackageName.getSignature(),
                        new TestVaList(baseVM, getPackageName));
                assertTrue(pkg instanceof StringObject);
                assertEquals("com.demo.app", ((StringObject) pkg).getValue());
            } else {
                DvmObject<?> pkg = jni.callObjectMethod(baseVM, app, getPackageName.getSignature(),
                        new TestVarArg(baseVM, getPackageName));
                assertTrue(pkg instanceof StringObject);
                assertEquals("com.demo.app", ((StringObject) pkg).getValue());
            }
            StringObject strObj = new StringObject(baseVM, "hello");
            DvmMethod hashCode = new DvmMethod(strObj.getObjectType(), "hashCode", "()I", false);
            if (useVaList) {
                assertEquals("hello".hashCode(),
                        jni.callIntMethodV(baseVM, strObj, hashCode.getSignature(),
                                new TestVaList(baseVM, hashCode)));
            } else {
                assertEquals("hello".hashCode(),
                        jni.callIntMethod(baseVM, strObj, hashCode.getSignature(),
                                new TestVarArg(baseVM, hashCode)));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    /** enabled=false with state=ENABLED(3): state not inferred from enabled. */
    private static void runWifiStateIndependent(boolean is64Bit, boolean useVaList) throws Exception {
        String json = "{"
                + "\"android\":{\"packageName\":\"com.demo.app\"},"
                + "\"network\":{\"wifi\":{\"enabled\":false,\"state\":3}}"
                + "}";
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
            DvmObject<?> wifiManager = vm.resolveClass("android/net/wifi/WifiManager").newObject(null);

            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, wifiManager, "isWifiEnabled"));
            assertEquals(WIFI_STATE_ENABLED,
                    invokeNoArgInt(jni, baseVM, useVaList, wifiManager, "getWifiState"));
            CapturedEvent stateEv = findLastEvent(sink.events, "network_wifi",
                    "WifiManager.getWifiState");
            assertNotNull(stateEv);
            assertEquals("json-config", stateEv.source);
            assertEquals("key=state,result=" + WIFI_STATE_ENABLED, String.valueOf(stateEv.value));
            assertNotNull(stateEv.note);
            assertFalse(stateEv.note.isEmpty());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runWifiStateMissingField(boolean is64Bit, boolean useVaList) throws Exception {
        String json = "{"
                + "\"android\":{\"packageName\":\"com.demo.app\"},"
                + "\"network\":{\"wifi\":{\"enabled\":true}}"
                + "}";
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
            DvmObject<?> wifiManager = vm.resolveClass("android/net/wifi/WifiManager").newObject(null);

            assertTrue(invokeNoArgBoolean(jni, baseVM, useVaList, wifiManager, "isWifiEnabled"));
            int eventsBefore = sink.events.size();
            try {
                invokeNoArgInt(jni, baseVM, useVaList, wifiManager, "getWifiState");
                fail("expected UOE for getWifiState when state field absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("WifiManager->getWifiState"));
            }
            for (int i = eventsBefore; i < sink.events.size(); i++) {
                assertFalse("unexpected network_wifi getWifiState event when state absent",
                        "network_wifi".equals(sink.events.get(i).kind)
                                && "WifiManager.getWifiState".equals(sink.events.get(i).api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    /**
     * Typed {@code Application}/{@code Context.getSystemService(WifiManager.class)} returns the
     * same {@code SystemService("wifi")} marker as the string route. Bluetooth typed lookup stays
     * in {@link AndroidBluetoothJniTest}.
     */
    private static void runWifiTypedGetSystemService(boolean is64Bit, boolean useVaList)
            throws Exception {
        runWifiTypedConfigured(is64Bit, useVaList);
        runWifiTypedAbsent(is64Bit, useVaList);
        runWifiTypedUnrelated(is64Bit, useVaList);
    }

    private static void runWifiTypedConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(WIFI_JSON);
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
            DvmClass wifiClass = vm.resolveClass("android/net/wifi/WifiManager");

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> fromString = invokeGetSystemService(jni, baseVM, useVaList, app, "wifi");
            int eventsBeforeApp = sink.events.size();
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, wifiClass);
            assertWifiManagerMarker(fromApp);
            assertEquals(fromString.getObjectType().getClassName(), fromApp.getObjectType().getClassName());
            assertEquals(fromString.getValue(), fromApp.getValue());
            assertNoNetworkWifiSince(sink, eventsBeforeApp);

            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, fromApp, "isWifiEnabled"));
            assertEquals(WIFI_STATE_DISABLED,
                    invokeNoArgInt(jni, baseVM, useVaList, fromApp, "getWifiState"));
            CapturedEvent enabledEv = findLastEvent(sink.events, "network_wifi",
                    "WifiManager.isWifiEnabled");
            assertNotNull(enabledEv);
            assertEquals("json-config", enabledEv.source);
            CapturedEvent stateEv = findLastEvent(sink.events, "network_wifi",
                    "WifiManager.getWifiState");
            assertNotNull(stateEv);
            assertEquals("json-config", stateEv.source);
            assertEquals("key=state,result=" + WIFI_STATE_DISABLED, String.valueOf(stateEv.value));

            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            int eventsBeforeCtx = sink.events.size();
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, wifiClass);
            assertWifiManagerMarker(fromCtx);
            assertNoNetworkWifiSince(sink, eventsBeforeCtx);
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, fromCtx, "isWifiEnabled"));
            assertEquals(WIFI_STATE_DISABLED,
                    invokeNoArgInt(jni, baseVM, useVaList, fromCtx, "getWifiState"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runWifiTypedAbsent(boolean is64Bit, boolean useVaList) throws Exception {
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
            DvmClass wifiClass = vm.resolveClass("android/net/wifi/WifiManager");
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> manager = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, wifiClass);
            assertWifiManagerMarker(manager);
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, manager, "isWifiEnabled");
                fail("expected UOE for isWifiEnabled without wifi config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("WifiManager->isWifiEnabled"));
            }
            try {
                invokeNoArgInt(jni, baseVM, useVaList, manager, "getWifiState");
                fail("expected UOE for getWifiState without wifi config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("WifiManager->getWifiState"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected network_wifi event when wifi absent: " + e.api,
                        "network_wifi".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runWifiTypedUnrelated(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(WIFI_JSON);
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
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmClass unrelated = vm.resolveClass("android/app/NotificationManager");
            try {
                invokeGetSystemServiceClass(jni, baseVM, useVaList, app, unrelated);
                fail("expected UOE for getSystemService(Class) with unrelated class");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSystemService"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected network_wifi event on unrelated typed lookup: " + e.api,
                        "network_wifi".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertWifiManagerMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals("android/net/wifi/WifiManager", manager.getObjectType().getClassName());
        assertEquals(SystemService.WIFI_SERVICE, manager.getValue());
    }

    private static void assertNoNetworkWifiSince(CapturingSink sink, int fromIndex) {
        for (int i = fromIndex; i < sink.events.size(); i++) {
            assertFalse("unexpected network_wifi event on typed lookup: " + sink.events.get(i).api,
                    "network_wifi".equals(sink.events.get(i).kind));
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

    private static String invokeNoArgString(AbstractJni jni, BaseVM vm, boolean useVaList,
                                            DvmObject<?> target, String methodName) {
        DvmObject<?> result = invokeNoArgObject(jni, vm, useVaList, target, methodName,
                "()Ljava/lang/String;");
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static DvmObject<?> invokeNoArgObject(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  DvmObject<?> target, String methodName, String args) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, target, signature, new TestVarArg(vm, method));
    }

    private static boolean invokeNoArgBoolean(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmObject<?> target, String methodName) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestVarArg(vm, method));
    }

    private static int invokeNoArgInt(AbstractJni jni, BaseVM vm, boolean useVaList,
                                      DvmObject<?> target, String methodName) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature, new TestVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, signature, new TestVarArg(vm, method));
    }

    private static DvmObject<?> invokeGetSystemService(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> app, String serviceName) {
        int nameHash = vm.addLocalObject(new StringObject(vm, serviceName));
        DvmClass dvmClass = app.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/String;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, app, signature, new TestVaList(vm, method, nameHash));
        }
        return jni.callObjectMethod(vm, app, signature, new TestVarArg(vm, method, nameHash));
    }

    private static DvmObject<?> invokeGetSystemServiceClass(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                            DvmObject<?> app, DvmClass serviceClass) {
        int classHash = vm.addLocalObject(serviceClass);
        DvmClass dvmClass = app.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/Class;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, app, signature, new TestVaList(vm, method, classHash));
        }
        return jni.callObjectMethod(vm, app, signature, new TestVarArg(vm, method, classHash));
    }

    private static final class TestVarArg extends VarArg {
        TestVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
        }
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVaList(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
        }
    }
}
