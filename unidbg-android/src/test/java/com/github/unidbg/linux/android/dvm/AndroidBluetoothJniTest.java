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

/**
 * 覆盖 {@code network.bluetooth} 对 {@code BluetoothAdapter.getDefaultAdapter}/
 * {@code getName}/{@code getAddress}/{@code isEnabled}/{@code getState}/{@code getScanMode}
 * 以及 {@code Context.BLUETOOTH_SERVICE} / {@code getSystemService} /
 * {@code BluetoothManager.getAdapter} 的 JNI 接线；含跨 {@code BaseVM} marker 拒绝。
 */
public class AndroidBluetoothJniTest {

    private static final String BLUETOOTH_ADAPTER_CLASS = "android/bluetooth/BluetoothAdapter";
    private static final String BLUETOOTH_MANAGER_CLASS = "android/bluetooth/BluetoothManager";

    private static final String FULL_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"},"
            + "\"network\":{\"bluetooth\":{"
            + "\"name\":\"TRACEAI_BT_V1\","
            + "\"address\":\"AA:BB:CC:DD:EE:FF\","
            + "\"enabled\":true,"
            + "\"state\":12,"
            + "\"scanMode\":21"
            + "}}"
            + "}";

    private static final String NULL_STRINGS_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"},"
            + "\"network\":{\"bluetooth\":{"
            + "\"name\":null,"
            + "\"address\":null,"
            + "\"enabled\":false"
            + "}}"
            + "}";

    private static final String EMPTY_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"},"
            + "\"network\":{\"bluetooth\":{}}"
            + "}";

    private static final String ABSENT_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    private static final String NAME_ONLY_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"},"
            + "\"network\":{\"bluetooth\":{\"name\":\"only-name\"}}"
            + "}";

    private static final String STATE_SCAN_ONLY_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"},"
            + "\"network\":{\"bluetooth\":{\"state\":10,\"scanMode\":20}}"
            + "}";

    private static final String CROSS_A_NAME = "TRACEAI_BT_VM_A";
    private static final String CROSS_A_ADDRESS = "aa:bb:cc:dd:ee:a1";
    private static final String CROSS_B_NAME = "TRACEAI_BT_VM_B";
    private static final String CROSS_B_ADDRESS = "11:22:33:44:55:66";

    private static final String CROSS_A_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"},"
            + "\"network\":{\"bluetooth\":{"
            + "\"name\":\"TRACEAI_BT_VM_A\","
            + "\"address\":\"AA:BB:CC:DD:EE:A1\","
            + "\"enabled\":true,"
            + "\"state\":12,"
            + "\"scanMode\":23,"
            + "\"discovering\":true"
            + "}}"
            + "}";

    private static final String CROSS_B_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"},"
            + "\"network\":{\"bluetooth\":{"
            + "\"name\":\"TRACEAI_BT_VM_B\","
            + "\"address\":\"11:22:33:44:55:66\","
            + "\"enabled\":false,"
            + "\"state\":10,"
            + "\"scanMode\":20,"
            + "\"discovering\":false"
            + "}}"
            + "}";

    @Test
    public void testBluetoothFullVarArg32() throws Exception {
        runFull(false, false);
    }

    @Test
    public void testBluetoothFullVaList64() throws Exception {
        runFull(true, true);
    }

    @Test
    public void testBluetoothExplicitNullVarArg32() throws Exception {
        runExplicitNulls(false, false);
    }

    @Test
    public void testBluetoothExplicitNullVaList64() throws Exception {
        runExplicitNulls(true, true);
    }

    @Test
    public void testBluetoothAbsentAndEmptyVarArg32() throws Exception {
        runAbsentAndPartial(false, false);
    }

    @Test
    public void testBluetoothAbsentAndEmptyVaList64() throws Exception {
        runAbsentAndPartial(true, true);
    }

    @Test
    public void testBluetoothManagerGetAdapterVarArg32() throws Exception {
        runBluetoothManagerGetAdapter(false, false);
    }

    @Test
    public void testBluetoothManagerGetAdapterVaList64() throws Exception {
        runBluetoothManagerGetAdapter(true, true);
    }

    @Test
    public void testBluetoothIsDiscoveringVarArg32() throws Exception {
        runIsDiscovering(false, false);
    }

    @Test
    public void testBluetoothIsDiscoveringVaList64() throws Exception {
        runIsDiscovering(true, true);
    }

    @Test
    public void testBluetoothTypedGetSystemServiceVarArg32() throws Exception {
        runTypedGetSystemService(false, false);
    }

    @Test
    public void testBluetoothTypedGetSystemServiceVaList64() throws Exception {
        runTypedGetSystemService(true, true);
    }

    @Test
    public void testBluetoothContextTypedGetSystemServiceVarArg32() throws Exception {
        runWithJson(false, false, FULL_JSON, new ContextTypedGetSystemServiceRunner());
    }

    @Test
    public void testBluetoothContextTypedGetSystemServiceVaList64() throws Exception {
        runWithJson(true, true, FULL_JSON, new ContextTypedGetSystemServiceRunner());
    }

    @Test
    public void testBluetoothCrossVmRejectedVarArg32() throws Exception {
        runCrossVmRejected(false, false);
    }

    @Test
    public void testBluetoothCrossVmRejectedVaList64() throws Exception {
        runCrossVmRejected(true, true);
    }

    private static void runFull(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_JSON);
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

            DvmClass adapterClass = vm.resolveClass(BLUETOOTH_ADAPTER_CLASS);
            DvmObject<?> adapter = invokeStaticGetDefaultAdapter(jni, baseVM, useVaList, adapterClass);
            assertNotNull(adapter);
            assertEquals(BLUETOOTH_ADAPTER_CLASS, adapter.getObjectType().getClassName());

            assertEquals("TRACEAI_BT_V1",
                    invokeNoArgString(jni, baseVM, useVaList, adapter, "getName"));
            assertEquals("aa:bb:cc:dd:ee:ff",
                    invokeNoArgString(jni, baseVM, useVaList, adapter, "getAddress"));
            assertTrue(invokeNoArgBoolean(jni, baseVM, useVaList, adapter, "isEnabled"));
            assertEquals(12, invokeNoArgInt(jni, baseVM, useVaList, adapter, "getState"));
            assertEquals(21, invokeNoArgInt(jni, baseVM, useVaList, adapter, "getScanMode"));

            CapturedEvent getDefault = findLastEvent(sink.events, "network_bluetooth",
                    "BluetoothAdapter.getDefaultAdapter");
            assertNotNull(getDefault);
            assertEquals("json-config", getDefault.source);
            assertEquals("BluetoothAdapter", String.valueOf(getDefault.value));
            assertNotNull(getDefault.note);
            assertFalse(getDefault.note.isEmpty());

            assertEvent(sink, "BluetoothAdapter.getName", "key=name,value=TRACEAI_BT_V1");
            assertEvent(sink, "BluetoothAdapter.getAddress", "key=address,value=aa:bb:cc:dd:ee:ff");
            assertEvent(sink, "BluetoothAdapter.isEnabled", "key=enabled,value=true");
            assertEvent(sink, "BluetoothAdapter.getState", "key=state,value=12");
            assertEvent(sink, "BluetoothAdapter.getScanMode", "key=scanMode,value=21");

            assertEquals(1, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.getDefaultAdapter"));
            assertEquals(1, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.getName"));
            assertEquals(1, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.getAddress"));
            assertEquals(1, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.isEnabled"));
            assertEquals(1, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.getState"));
            assertEquals(1, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.getScanMode"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runExplicitNulls(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NULL_STRINGS_JSON);
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

            DvmClass adapterClass = vm.resolveClass(BLUETOOTH_ADAPTER_CLASS);
            DvmObject<?> adapter = invokeStaticGetDefaultAdapter(jni, baseVM, useVaList, adapterClass);
            assertNotNull(adapter);

            assertNull(invokeNoArgObject(jni, baseVM, useVaList, adapter,
                    "getName", "()Ljava/lang/String;"));
            assertNull(invokeNoArgObject(jni, baseVM, useVaList, adapter,
                    "getAddress", "()Ljava/lang/String;"));
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, adapter, "isEnabled"));

            assertEvent(sink, "BluetoothAdapter.getName", "key=name,value=null");
            assertEvent(sink, "BluetoothAdapter.getAddress", "key=address,value=null");
            assertEvent(sink, "BluetoothAdapter.isEnabled", "key=enabled,value=false");
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentAndPartial(boolean is64Bit, boolean useVaList) throws Exception {
        // node absent
        runWithJson(is64Bit, useVaList, ABSENT_JSON, new AbsentRunner());
        // empty node: getDefaultAdapter UOE, no events
        runWithJson(is64Bit, useVaList, EMPTY_JSON, new EmptyRunner());
        // name only: adapter ok, getName ok, getAddress/isEnabled/getState/getScanMode UOE
        runWithJson(is64Bit, useVaList, NAME_ONLY_JSON, new NameOnlyRunner());
        // state+scanMode only: getState/getScanMode ok; name/address/enabled UOE
        runWithJson(is64Bit, useVaList, STATE_SCAN_ONLY_JSON, new StateScanOnlyRunner());
    }

    private static void runBluetoothManagerGetAdapter(boolean is64Bit, boolean useVaList)
            throws Exception {
        // full config: BLUETOOTH_SERVICE + getSystemService + getAdapter + getter
        runWithJson(is64Bit, useVaList, FULL_JSON, new ManagerGetAdapterRunner());
        // absent node: getSystemService still returns manager; getAdapter UOE, no event
        runWithJson(is64Bit, useVaList, ABSENT_JSON, new ManagerGetAdapterAbsentRunner());
        // empty bluetooth: getAdapter UOE
        runWithJson(is64Bit, useVaList, EMPTY_JSON, new ManagerGetAdapterEmptyRunner());
    }

    private static final String DISCOVERING_TRUE_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"},"
            + "\"network\":{\"bluetooth\":{\"discovering\":true}}"
            + "}";

    private static void runIsDiscovering(boolean is64Bit, boolean useVaList) throws Exception {
        // configured true
        runWithJson(is64Bit, useVaList, DISCOVERING_TRUE_JSON, new DiscoveringTrueRunner());
        // node present, field omitted → default false (name-only marker)
        runWithJson(is64Bit, useVaList, NAME_ONLY_JSON, new DiscoveringDefaultFalseRunner());
        // missing node → no marker path; plain adapter UOE + no event
        runWithJson(is64Bit, useVaList, ABSENT_JSON, new DiscoveringAbsentAndPlainRunner());
    }

    private static void runTypedGetSystemService(boolean is64Bit, boolean useVaList) throws Exception {
        // full: typed Class lookup → manager → getAdapter → getName
        runWithJson(is64Bit, useVaList, FULL_JSON, new TypedGetSystemServiceRunner());
        // absent bluetooth: manager still returned; getAdapter UOE, no network_bluetooth
        runWithJson(is64Bit, useVaList, ABSENT_JSON, new TypedGetSystemServiceAbsentRunner());
        // unrelated class / invalid arg → UOE, no network_bluetooth
        runWithJson(is64Bit, useVaList, FULL_JSON, new TypedGetSystemServiceRejectRunner());
    }

    private interface Scenario {
        void run(AbstractJni jni, BaseVM baseVM, VM vm, boolean useVaList, CapturingSink sink)
                throws Exception;
    }

    private static final class AbsentRunner implements Scenario {
        @Override
        public void run(AbstractJni jni, BaseVM baseVM, VM vm, boolean useVaList, CapturingSink sink) {
            DvmClass adapterClass = vm.resolveClass(BLUETOOTH_ADAPTER_CLASS);
            try {
                invokeStaticGetDefaultAdapter(jni, baseVM, useVaList, adapterClass);
                fail("expected UOE for getDefaultAdapter without bluetooth");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDefaultAdapter"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected network_bluetooth event: " + e.api,
                        "network_bluetooth".equals(e.kind));
            }
        }
    }

    private static final class ManagerGetAdapterRunner implements Scenario {
        @Override
        public void run(AbstractJni jni, BaseVM baseVM, VM vm, boolean useVaList, CapturingSink sink) {
            DvmClass contextClass = vm.resolveClass("android/content/Context");
            DvmObject<?> serviceName = jni.getStaticObjectField(baseVM, contextClass,
                    "android/content/Context->BLUETOOTH_SERVICE:Ljava/lang/String;");
            assertTrue(serviceName instanceof StringObject);
            assertEquals(SystemService.BLUETOOTH_SERVICE, ((StringObject) serviceName).getValue());
            assertEquals("bluetooth", ((StringObject) serviceName).getValue());

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> manager = invokeGetSystemService(jni, baseVM, useVaList, app,
                    ((StringObject) serviceName).getValue());
            assertNotNull(manager);
            assertTrue(manager instanceof SystemService);
            assertEquals(BLUETOOTH_MANAGER_CLASS, manager.getObjectType().getClassName());
            assertEquals(SystemService.BLUETOOTH_SERVICE, manager.getValue());

            DvmObject<?> adapter = invokeNoArgObject(jni, baseVM, useVaList, manager,
                    "getAdapter", "()Landroid/bluetooth/BluetoothAdapter;");
            assertNotNull(adapter);
            assertEquals(BLUETOOTH_ADAPTER_CLASS, adapter.getObjectType().getClassName());
            assertEquals("TRACEAI_BT_V1",
                    invokeNoArgString(jni, baseVM, useVaList, adapter, "getName"));

            assertEvent(sink, "BluetoothManager.getAdapter", "BluetoothAdapter");
            assertEquals(1, countEvents(sink.events, "network_bluetooth",
                    "BluetoothManager.getAdapter"));
            assertEvent(sink, "BluetoothAdapter.getName", "key=name,value=TRACEAI_BT_V1");

            // non-SystemService BluetoothManager receiver → UOE, no extra getAdapter event
            int before = countEvents(sink.events, "network_bluetooth",
                    "BluetoothManager.getAdapter");
            DvmObject<?> plainManager = vm.resolveClass(BLUETOOTH_MANAGER_CLASS).newObject(null);
            try {
                invokeNoArgObject(jni, baseVM, useVaList, plainManager,
                        "getAdapter", "()Landroid/bluetooth/BluetoothAdapter;");
                fail("expected UOE for getAdapter on non-SystemService BluetoothManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAdapter"));
            }
            assertEquals(before, countEvents(sink.events, "network_bluetooth",
                    "BluetoothManager.getAdapter"));
        }
    }

    private static final class ManagerGetAdapterAbsentRunner implements Scenario {
        @Override
        public void run(AbstractJni jni, BaseVM baseVM, VM vm, boolean useVaList, CapturingSink sink) {
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> manager = invokeGetSystemService(jni, baseVM, useVaList, app, "bluetooth");
            assertNotNull(manager);
            assertTrue(manager instanceof SystemService);
            try {
                invokeNoArgObject(jni, baseVM, useVaList, manager,
                        "getAdapter", "()Landroid/bluetooth/BluetoothAdapter;");
                fail("expected UOE for getAdapter without network.bluetooth");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAdapter"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected network_bluetooth event: " + e.api,
                        "network_bluetooth".equals(e.kind));
            }
        }
    }

    private static final class ManagerGetAdapterEmptyRunner implements Scenario {
        @Override
        public void run(AbstractJni jni, BaseVM baseVM, VM vm, boolean useVaList, CapturingSink sink) {
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> manager = invokeGetSystemService(jni, baseVM, useVaList, app, "bluetooth");
            assertNotNull(manager);
            try {
                invokeNoArgObject(jni, baseVM, useVaList, manager,
                        "getAdapter", "()Landroid/bluetooth/BluetoothAdapter;");
                fail("expected UOE for getAdapter with empty bluetooth");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAdapter"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected network_bluetooth event: " + e.api,
                        "network_bluetooth".equals(e.kind));
            }
        }
    }

    private static final class DiscoveringTrueRunner implements Scenario {
        @Override
        public void run(AbstractJni jni, BaseVM baseVM, VM vm, boolean useVaList, CapturingSink sink) {
            DvmClass adapterClass = vm.resolveClass(BLUETOOTH_ADAPTER_CLASS);
            DvmObject<?> adapter = invokeStaticGetDefaultAdapter(jni, baseVM, useVaList, adapterClass);
            assertNotNull(adapter);
            assertTrue(invokeNoArgBoolean(jni, baseVM, useVaList, adapter, "isDiscovering"));
            assertEvent(sink, "BluetoothAdapter.isDiscovering", "key=discovering,value=true");
            assertEquals(1, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.isDiscovering"));
        }
    }

    private static final class DiscoveringDefaultFalseRunner implements Scenario {
        @Override
        public void run(AbstractJni jni, BaseVM baseVM, VM vm, boolean useVaList, CapturingSink sink) {
            DvmClass adapterClass = vm.resolveClass(BLUETOOTH_ADAPTER_CLASS);
            DvmObject<?> adapter = invokeStaticGetDefaultAdapter(jni, baseVM, useVaList, adapterClass);
            assertNotNull(adapter);
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, adapter, "isDiscovering"));
            assertEvent(sink, "BluetoothAdapter.isDiscovering", "key=discovering,value=false");
            assertEquals(1, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.isDiscovering"));
        }
    }

    private static final class DiscoveringAbsentAndPlainRunner implements Scenario {
        @Override
        public void run(AbstractJni jni, BaseVM baseVM, VM vm, boolean useVaList, CapturingSink sink) {
            // plain non-marker adapter: UOE, no event
            DvmObject<?> plain = vm.resolveClass(BLUETOOTH_ADAPTER_CLASS).newObject(null);
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, plain, "isDiscovering");
                fail("expected UOE for isDiscovering on plain adapter");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isDiscovering"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected network_bluetooth event: " + e.api,
                        "network_bluetooth".equals(e.kind));
            }
        }
    }

    private static final class TypedGetSystemServiceRunner implements Scenario {
        @Override
        public void run(AbstractJni jni, BaseVM baseVM, VM vm, boolean useVaList, CapturingSink sink) {
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmClass managerClass = vm.resolveClass(BLUETOOTH_MANAGER_CLASS);
            DvmObject<?> manager = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, managerClass);
            assertNotNull(manager);
            assertTrue(manager instanceof SystemService);
            assertEquals(BLUETOOTH_MANAGER_CLASS, manager.getObjectType().getClassName());
            assertEquals(SystemService.BLUETOOTH_SERVICE, manager.getValue());

            DvmObject<?> adapter = invokeNoArgObject(jni, baseVM, useVaList, manager,
                    "getAdapter", "()Landroid/bluetooth/BluetoothAdapter;");
            assertNotNull(adapter);
            assertEquals(BLUETOOTH_ADAPTER_CLASS, adapter.getObjectType().getClassName());
            assertEquals("TRACEAI_BT_V1",
                    invokeNoArgString(jni, baseVM, useVaList, adapter, "getName"));

            assertEvent(sink, "BluetoothManager.getAdapter", "BluetoothAdapter");
            assertEvent(sink, "BluetoothAdapter.getName", "key=name,value=TRACEAI_BT_V1");
            assertEquals(1, countEvents(sink.events, "network_bluetooth",
                    "BluetoothManager.getAdapter"));
        }
    }

    /**
     * Direct coverage of {@code android/content/Context->getSystemService(Ljava/lang/Class;)}.
     * Typed lookup itself must not emit {@code network_bluetooth}; only getAdapter/getName do.
     */
    private static final class ContextTypedGetSystemServiceRunner implements Scenario {
        @Override
        public void run(AbstractJni jni, BaseVM baseVM, VM vm, boolean useVaList, CapturingSink sink) {
            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            DvmClass managerClass = vm.resolveClass(BLUETOOTH_MANAGER_CLASS);
            int eventsBeforeLookup = sink.events.size();
            DvmObject<?> manager = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, managerClass);
            assertNotNull(manager);
            assertTrue(manager instanceof SystemService);
            assertEquals(BLUETOOTH_MANAGER_CLASS, manager.getObjectType().getClassName());
            assertEquals(SystemService.BLUETOOTH_SERVICE, manager.getValue());
            // typed Context lookup emits no network_bluetooth
            for (int i = eventsBeforeLookup; i < sink.events.size(); i++) {
                assertFalse("unexpected network_bluetooth event on typed Context lookup: "
                                + sink.events.get(i).api,
                        "network_bluetooth".equals(sink.events.get(i).kind));
            }
            assertEquals(0, countEvents(sink.events, "network_bluetooth",
                    "BluetoothManager.getAdapter"));

            DvmObject<?> adapter = invokeNoArgObject(jni, baseVM, useVaList, manager,
                    "getAdapter", "()Landroid/bluetooth/BluetoothAdapter;");
            assertNotNull(adapter);
            assertEquals(BLUETOOTH_ADAPTER_CLASS, adapter.getObjectType().getClassName());
            assertEquals("TRACEAI_BT_V1",
                    invokeNoArgString(jni, baseVM, useVaList, adapter, "getName"));

            assertEvent(sink, "BluetoothManager.getAdapter", "BluetoothAdapter");
            assertEvent(sink, "BluetoothAdapter.getName", "key=name,value=TRACEAI_BT_V1");
            assertEquals(1, countEvents(sink.events, "network_bluetooth",
                    "BluetoothManager.getAdapter"));
            assertEquals(1, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.getName"));
        }
    }

    private static final class TypedGetSystemServiceAbsentRunner implements Scenario {
        @Override
        public void run(AbstractJni jni, BaseVM baseVM, VM vm, boolean useVaList, CapturingSink sink) {
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmClass managerClass = vm.resolveClass(BLUETOOTH_MANAGER_CLASS);
            // typed lookup does not require network.bluetooth
            DvmObject<?> manager = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, managerClass);
            assertNotNull(manager);
            assertTrue(manager instanceof SystemService);
            try {
                invokeNoArgObject(jni, baseVM, useVaList, manager,
                        "getAdapter", "()Landroid/bluetooth/BluetoothAdapter;");
                fail("expected UOE for getAdapter without network.bluetooth");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAdapter"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected network_bluetooth event: " + e.api,
                        "network_bluetooth".equals(e.kind));
            }
        }
    }

    private static final class TypedGetSystemServiceRejectRunner implements Scenario {
        @Override
        public void run(AbstractJni jni, BaseVM baseVM, VM vm, boolean useVaList, CapturingSink sink) {
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            // unrelated DvmClass not in resolveLimitedTypedSystemService
            // (WifiManager typed lookup is covered in AndroidWifiJniTest)
            DvmClass cameraClass = vm.resolveClass("android/hardware/Camera");
            try {
                invokeGetSystemServiceClass(jni, baseVM, useVaList, app, cameraClass);
                fail("expected UOE for getSystemService(Class) with unrelated class");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSystemService"));
            }
            // invalid non-Class object argument
            try {
                invokeGetSystemServiceClassObject(jni, baseVM, useVaList, app,
                        new StringObject(baseVM, "not-a-class"));
                fail("expected UOE for getSystemService(Class) with non-DvmClass arg");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSystemService"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected network_bluetooth event: " + e.api,
                        "network_bluetooth".equals(e.kind));
            }
        }
    }

    private static final class EmptyRunner implements Scenario {
        @Override
        public void run(AbstractJni jni, BaseVM baseVM, VM vm, boolean useVaList, CapturingSink sink) {
            DvmClass adapterClass = vm.resolveClass(BLUETOOTH_ADAPTER_CLASS);
            try {
                invokeStaticGetDefaultAdapter(jni, baseVM, useVaList, adapterClass);
                fail("expected UOE for getDefaultAdapter with empty bluetooth");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDefaultAdapter"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected network_bluetooth event: " + e.api,
                        "network_bluetooth".equals(e.kind));
            }
        }
    }

    private static final class NameOnlyRunner implements Scenario {
        @Override
        public void run(AbstractJni jni, BaseVM baseVM, VM vm, boolean useVaList, CapturingSink sink) {
            DvmClass adapterClass = vm.resolveClass(BLUETOOTH_ADAPTER_CLASS);
            DvmObject<?> adapter = invokeStaticGetDefaultAdapter(jni, baseVM, useVaList, adapterClass);
            assertNotNull(adapter);
            assertEquals("only-name",
                    invokeNoArgString(jni, baseVM, useVaList, adapter, "getName"));
            try {
                invokeNoArgObject(jni, baseVM, useVaList, adapter,
                        "getAddress", "()Ljava/lang/String;");
                fail("expected UOE for getAddress without address field");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAddress"));
            }
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, adapter, "isEnabled");
                fail("expected UOE for isEnabled without enabled field");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isEnabled"));
            }
            try {
                invokeNoArgInt(jni, baseVM, useVaList, adapter, "getState");
                fail("expected UOE for getState without state field");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getState"));
            }
            try {
                invokeNoArgInt(jni, baseVM, useVaList, adapter, "getScanMode");
                fail("expected UOE for getScanMode without scanMode field");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getScanMode"));
            }
            assertEquals(1, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.getDefaultAdapter"));
            assertEquals(1, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.getName"));
            assertEquals(0, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.getAddress"));
            assertEquals(0, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.isEnabled"));
            assertEquals(0, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.getState"));
            assertEquals(0, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.getScanMode"));
        }
    }

    private static final class StateScanOnlyRunner implements Scenario {
        @Override
        public void run(AbstractJni jni, BaseVM baseVM, VM vm, boolean useVaList, CapturingSink sink) {
            DvmClass adapterClass = vm.resolveClass(BLUETOOTH_ADAPTER_CLASS);
            DvmObject<?> adapter = invokeStaticGetDefaultAdapter(jni, baseVM, useVaList, adapterClass);
            assertNotNull(adapter);
            assertEquals(10, invokeNoArgInt(jni, baseVM, useVaList, adapter, "getState"));
            assertEquals(20, invokeNoArgInt(jni, baseVM, useVaList, adapter, "getScanMode"));
            try {
                invokeNoArgObject(jni, baseVM, useVaList, adapter,
                        "getName", "()Ljava/lang/String;");
                fail("expected UOE for getName without name field");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, adapter, "isEnabled");
                fail("expected UOE for isEnabled without enabled field");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isEnabled"));
            }
            assertEvent(sink, "BluetoothAdapter.getState", "key=state,value=10");
            assertEvent(sink, "BluetoothAdapter.getScanMode", "key=scanMode,value=20");
            assertEquals(1, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.getState"));
            assertEquals(1, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.getScanMode"));
            assertEquals(0, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.getName"));
            assertEquals(0, countEvents(sink.events, "network_bluetooth",
                    "BluetoothAdapter.isEnabled"));
        }
    }

    private static void runCrossVmRejected(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(CROSS_A_JSON);
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

            DvmClass adapterClassA = vmA.resolveClass(BLUETOOTH_ADAPTER_CLASS);
            DvmObject<?> adapterA = invokeStaticGetDefaultAdapter(jniA, baseA, useVaList, adapterClassA);
            assertNotNull(adapterA);
            assertTrue(adapterA.getValue().getClass().getName().contains("ConfiguredBluetoothAdapter"));

            int eventsBeforeReject = sinkB.events.size();
            try {
                invokeNoArgString(jniB, baseB, useVaList, adapterA, "getName");
                fail("expected UnsupportedOperationException for cross-VM getName");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            try {
                invokeNoArgBoolean(jniB, baseB, useVaList, adapterA, "isEnabled");
                fail("expected UnsupportedOperationException for cross-VM isEnabled");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isEnabled"));
            }
            try {
                invokeNoArgInt(jniB, baseB, useVaList, adapterA, "getState");
                fail("expected UnsupportedOperationException for cross-VM getState");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getState"));
            }
            for (int i = eventsBeforeReject; i < sinkB.events.size(); i++) {
                CapturedEvent e = sinkB.events.get(i);
                assertFalse("unexpected network_bluetooth during reject: " + e.api,
                        "network_bluetooth".equals(e.kind));
            }
            assertNoABluetoothValues(sinkB.events);

            DvmClass adapterClassB = vmB.resolveClass(BLUETOOTH_ADAPTER_CLASS);
            DvmObject<?> adapterB = invokeStaticGetDefaultAdapter(jniB, baseB, useVaList, adapterClassB);
            assertNotNull(adapterB);
            assertEquals(CROSS_B_NAME, invokeNoArgString(jniB, baseB, useVaList, adapterB, "getName"));
            assertEquals(CROSS_B_ADDRESS, invokeNoArgString(jniB, baseB, useVaList, adapterB, "getAddress"));
            assertFalse(invokeNoArgBoolean(jniB, baseB, useVaList, adapterB, "isEnabled"));
            assertFalse(invokeNoArgBoolean(jniB, baseB, useVaList, adapterB, "isDiscovering"));
            assertEquals(10, invokeNoArgInt(jniB, baseB, useVaList, adapterB, "getState"));
            assertEquals(20, invokeNoArgInt(jniB, baseB, useVaList, adapterB, "getScanMode"));

            assertNoABluetoothValues(sinkB.events);
            assertEvent(sinkB, "BluetoothAdapter.getName", "key=name,value=" + CROSS_B_NAME);
            assertEvent(sinkB, "BluetoothAdapter.isEnabled", "key=enabled,value=false");
            assertEvent(sinkB, "BluetoothAdapter.getState", "key=state,value=10");
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

    private static void assertNoABluetoothValues(List<CapturedEvent> events) {
        for (CapturedEvent e : events) {
            String blob = String.valueOf(e.kind) + "|" + String.valueOf(e.api)
                    + "|" + String.valueOf(e.value) + "|" + String.valueOf(e.note);
            assertFalse("VM B sidecar must not contain VM A bluetooth name: " + blob,
                    blob.contains(CROSS_A_NAME));
            assertFalse("VM B sidecar must not contain VM A bluetooth address: " + blob,
                    blob.contains(CROSS_A_ADDRESS) || blob.contains("AA:BB:CC:DD:EE:A1"));
            assertFalse("VM B sidecar must not contain VM A enabled=true: " + blob,
                    blob.contains("key=enabled,value=true"));
            assertFalse("VM B sidecar must not contain VM A state=12: " + blob,
                    blob.contains("key=state,value=12"));
            assertFalse("VM B sidecar must not contain VM A scanMode=23: " + blob,
                    blob.contains("key=scanMode,value=23"));
            assertFalse("VM B sidecar must not contain VM A discovering=true: " + blob,
                    blob.contains("key=discovering,value=true"));
        }
    }

    private static void runWithJson(boolean is64Bit, boolean useVaList, String json, Scenario scenario)
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
            scenario.run(jni, (BaseVM) vm, vm, useVaList, sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertEvent(CapturingSink sink, String api, String expectedValue) {
        CapturedEvent ev = findLastEvent(sink.events, "network_bluetooth", api);
        assertNotNull(ev);
        assertEquals("json-config", ev.source);
        assertEquals(expectedValue, String.valueOf(ev.value));
        assertNotNull(ev.note);
        assertFalse(ev.note.isEmpty());
    }

    private static DvmObject<?> invokeStaticGetDefaultAdapter(AbstractJni jni, BaseVM vm,
                                                              boolean useVaList, DvmClass adapterClass) {
        DvmMethod method = new DvmMethod(adapterClass, "getDefaultAdapter",
                "()Landroid/bluetooth/BluetoothAdapter;", true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, adapterClass, signature, new TestVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, adapterClass, signature, new TestVarArg(vm, method));
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
        return invokeGetSystemServiceClassObject(jni, vm, useVaList, app, serviceClass);
    }

    private static DvmObject<?> invokeGetSystemServiceClassObject(AbstractJni jni, BaseVM vm,
                                                                  boolean useVaList, DvmObject<?> app,
                                                                  DvmObject<?> classArg) {
        int classHash = vm.addLocalObject(classArg);
        DvmClass dvmClass = app.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/Class;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, app, signature, new TestVaList(vm, method, classHash));
        }
        return jni.callObjectMethod(vm, app, signature, new TestVarArg(vm, method, classHash));
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
