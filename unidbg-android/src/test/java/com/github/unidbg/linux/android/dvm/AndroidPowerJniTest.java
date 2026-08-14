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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AndroidPowerJniTest {

    /** Independent flags; rebootingUserspaceSupported custom true */
    private static final String POWER_FULL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"power\":{"
            + "\"interactive\":false,"
            + "\"powerSaveMode\":true,"
            + "\"deviceIdleMode\":false,"
            + "\"deviceLightIdleMode\":true,"
            + "\"lowPowerStandbyEnabled\":false,"
            + "\"sustainedPerformanceModeSupported\":true,"
            + "\"rebootingUserspaceSupported\":true"
            + "}"
            + "}"
            + "}";

    private static final String POWER_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"power\":{}"
            + "}"
            + "}";

    private static final String NO_POWER_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    private static final String POWER_IGNORING_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"power\":{\"ignoringBatteryOptimizations\":true}"
            + "}"
            + "}";

    private static final String IS_IGNORING_BATTERY_OPTIMIZATIONS_ARGS = "(Ljava/lang/String;)Z";

    @Test
    public void testPowerFullVarArg32() throws Exception {
        runFullPower(false, false);
    }

    @Test
    public void testPowerFullVaList64() throws Exception {
        runFullPower(true, true);
    }

    @Test
    public void testPowerEmptyDefaultsVarArg32() throws Exception {
        runEmptyPowerDefaults(false, false);
    }

    @Test
    public void testPowerEmptyDefaultsVaList64() throws Exception {
        runEmptyPowerDefaults(true, true);
    }

    @Test
    public void testPowerAbsentVarArg32() throws Exception {
        runAbsentPower(false, false);
    }

    @Test
    public void testPowerAbsentVaList64() throws Exception {
        runAbsentPower(true, true);
    }

    @Test
    public void testIgnoringBatteryOptimizationsVarArg32() throws Exception {
        runIgnoringBatteryOptimizations(false, false);
    }

    @Test
    public void testIgnoringBatteryOptimizationsVaList64() throws Exception {
        runIgnoringBatteryOptimizations(true, true);
    }

    @Test
    public void testIgnoringBatteryOptimizationsNullArgVarArg32() throws Exception {
        runIgnoringBatteryOptimizationsNullArg(false, false);
    }

    @Test
    public void testIgnoringBatteryOptimizationsNullArgVaList64() throws Exception {
        runIgnoringBatteryOptimizationsNullArg(true, true);
    }

    @Test
    public void testIgnoringBatteryOptimizationsAbsentVarArg32() throws Exception {
        runIgnoringBatteryOptimizationsAbsent(false, false);
    }

    @Test
    public void testIgnoringBatteryOptimizationsAbsentVaList64() throws Exception {
        runIgnoringBatteryOptimizationsAbsent(true, true);
    }

    @Test
    public void testPowerTypedGetSystemServiceVarArg32() throws Exception {
        runTypedGetSystemService(false, false);
    }

    @Test
    public void testPowerTypedGetSystemServiceVaList64() throws Exception {
        runTypedGetSystemService(true, true);
    }

    private static void runIgnoringBatteryOptimizations(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(POWER_IGNORING_JSON);
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

            DvmObject<?> powerManager = vm.resolveClass("android/os/PowerManager").newObject(null);

            assertTrue(invokeIsIgnoringBatteryOptimizations(jni, baseVM, useVaList, powerManager,
                    "com.demo.app"));
            CapturedEvent matchEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isIgnoringBatteryOptimizations");
            assertNotNull(matchEv);
            assertEquals("json-config", matchEv.source);
            assertEquals("field=ignoringBatteryOptimizations,packageName=com.demo.app,result=true",
                    String.valueOf(matchEv.value));
            assertNotNull(matchEv.note);
            assertFalse(matchEv.note.isEmpty());

            assertFalse(invokeIsIgnoringBatteryOptimizations(jni, baseVM, useVaList, powerManager,
                    "com.other.app"));
            CapturedEvent otherEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isIgnoringBatteryOptimizations");
            assertNotNull(otherEv);
            assertEquals("json-config", otherEv.source);
            assertEquals("field=ignoringBatteryOptimizations,packageName=com.other.app,result=false",
                    String.valueOf(otherEv.value));
            assertTrue(String.valueOf(otherEv.value).endsWith("result=false"));

            assertEquals(2, countEvents(sink.events, "android_power",
                    "PowerManager.isIgnoringBatteryOptimizations"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIgnoringBatteryOptimizationsNullArg(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(POWER_IGNORING_JSON);
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

            DvmObject<?> powerManager = vm.resolveClass("android/os/PowerManager").newObject(null);
            try {
                invokeIsIgnoringBatteryOptimizations(jni, baseVM, useVaList, powerManager, null);
                fail("expected UnsupportedOperationException for null package argument");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isIgnoringBatteryOptimizations"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_power event for null package arg: " + e.api,
                        "android_power".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIgnoringBatteryOptimizationsAbsent(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_POWER_JSON);
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

            DvmObject<?> powerManager = vm.resolveClass("android/os/PowerManager").newObject(null);
            try {
                invokeIsIgnoringBatteryOptimizations(jni, baseVM, useVaList, powerManager,
                        "com.demo.app");
                fail("expected UnsupportedOperationException for isIgnoringBatteryOptimizations without power");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isIgnoringBatteryOptimizations"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_power event when power absent: " + e.api,
                        "android_power".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runFullPower(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(POWER_FULL_JSON);
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

            DvmObject<?> powerManager = vm.resolveClass("android/os/PowerManager").newObject(null);
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isInteractive"));
            assertTrue(invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isPowerSaveMode"));
            // isScreenOn aliases interactive
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isScreenOn"));
            // three independent idle/standby values: false / true / false
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isDeviceIdleMode"));
            assertTrue(invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isDeviceLightIdleMode"));
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isLowPowerStandbyEnabled"));
            // custom sustainedPerformanceModeSupported=true (not derived)
            assertTrue(invokeNoArgBoolean(jni, baseVM, useVaList, powerManager,
                    "isSustainedPerformanceModeSupported"));
            // custom rebootingUserspaceSupported=true (fixed capability marker, no reboot)
            assertTrue(invokeNoArgBoolean(jni, baseVM, useVaList, powerManager,
                    "isRebootingUserspaceSupported"));

            // unrelated PowerManager method stays unsupported
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isAmbientDisplayAvailable");
                fail("expected UnsupportedOperationException for isAmbientDisplayAvailable");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PowerManager->isAmbientDisplayAvailable"));
            }

            CapturedEvent interactiveEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isInteractive");
            assertNotNull(interactiveEv);
            assertEquals("json-config", interactiveEv.source);
            assertEquals("field=interactive,result=false", String.valueOf(interactiveEv.value));
            assertNotNull(interactiveEv.note);
            assertFalse(interactiveEv.note.isEmpty());

            CapturedEvent saveEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isPowerSaveMode");
            assertNotNull(saveEv);
            assertEquals("json-config", saveEv.source);
            assertEquals("field=powerSaveMode,result=true", String.valueOf(saveEv.value));
            assertNotNull(saveEv.note);
            assertFalse(saveEv.note.isEmpty());

            CapturedEvent screenOnEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isScreenOn");
            assertNotNull(screenOnEv);
            assertEquals("json-config", screenOnEv.source);
            assertEquals("field=interactive,result=false", String.valueOf(screenOnEv.value));
            assertNotNull(screenOnEv.note);
            assertFalse(screenOnEv.note.isEmpty());

            CapturedEvent idleEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isDeviceIdleMode");
            assertNotNull(idleEv);
            assertEquals("json-config", idleEv.source);
            assertEquals("field=deviceIdleMode,result=false", String.valueOf(idleEv.value));
            assertNotNull(idleEv.note);
            assertFalse(idleEv.note.isEmpty());

            CapturedEvent lightIdleEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isDeviceLightIdleMode");
            assertNotNull(lightIdleEv);
            assertEquals("json-config", lightIdleEv.source);
            assertEquals("field=deviceLightIdleMode,result=true", String.valueOf(lightIdleEv.value));
            assertNotNull(lightIdleEv.note);
            assertFalse(lightIdleEv.note.isEmpty());

            CapturedEvent standbyEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isLowPowerStandbyEnabled");
            assertNotNull(standbyEv);
            assertEquals("json-config", standbyEv.source);
            assertEquals("field=lowPowerStandbyEnabled,result=false", String.valueOf(standbyEv.value));
            assertNotNull(standbyEv.note);
            assertFalse(standbyEv.note.isEmpty());

            CapturedEvent sustainedEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isSustainedPerformanceModeSupported");
            assertNotNull(sustainedEv);
            assertEquals("json-config", sustainedEv.source);
            assertEquals("field=sustainedPerformanceModeSupported,result=true",
                    String.valueOf(sustainedEv.value));
            assertNotNull(sustainedEv.note);
            assertFalse(sustainedEv.note.isEmpty());

            CapturedEvent rebootEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isRebootingUserspaceSupported");
            assertNotNull(rebootEv);
            assertEquals("json-config", rebootEv.source);
            assertEquals("field=rebootingUserspaceSupported,result=true",
                    String.valueOf(rebootEv.value));
            assertNotNull(rebootEv.note);
            assertFalse(rebootEv.note.isEmpty());

            assertEquals(1, countEvents(sink.events, "android_power", "PowerManager.isInteractive"));
            assertEquals(1, countEvents(sink.events, "android_power", "PowerManager.isPowerSaveMode"));
            assertEquals(1, countEvents(sink.events, "android_power", "PowerManager.isScreenOn"));
            assertEquals(1, countEvents(sink.events, "android_power", "PowerManager.isDeviceIdleMode"));
            assertEquals(1, countEvents(sink.events, "android_power", "PowerManager.isDeviceLightIdleMode"));
            assertEquals(1, countEvents(sink.events, "android_power", "PowerManager.isLowPowerStandbyEnabled"));
            assertEquals(1, countEvents(sink.events, "android_power",
                    "PowerManager.isSustainedPerformanceModeSupported"));
            assertEquals(1, countEvents(sink.events, "android_power",
                    "PowerManager.isRebootingUserspaceSupported"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runEmptyPowerDefaults(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(POWER_EMPTY_JSON);
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

            DvmObject<?> powerManager = vm.resolveClass("android/os/PowerManager").newObject(null);
            assertTrue(invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isInteractive"));
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isPowerSaveMode"));
            // default interactive=true → isScreenOn true
            assertTrue(invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isScreenOn"));
            // default all idle/standby/sustained/rebooting flags false
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isDeviceIdleMode"));
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isDeviceLightIdleMode"));
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isLowPowerStandbyEnabled"));
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, powerManager,
                    "isSustainedPerformanceModeSupported"));
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, powerManager,
                    "isRebootingUserspaceSupported"));

            CapturedEvent interactiveEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isInteractive");
            assertNotNull(interactiveEv);
            assertEquals("json-config", interactiveEv.source);
            assertEquals("field=interactive,result=true", String.valueOf(interactiveEv.value));

            CapturedEvent saveEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isPowerSaveMode");
            assertNotNull(saveEv);
            assertEquals("json-config", saveEv.source);
            assertEquals("field=powerSaveMode,result=false", String.valueOf(saveEv.value));

            CapturedEvent screenOnEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isScreenOn");
            assertNotNull(screenOnEv);
            assertEquals("json-config", screenOnEv.source);
            assertEquals("field=interactive,result=true", String.valueOf(screenOnEv.value));

            CapturedEvent idleEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isDeviceIdleMode");
            assertNotNull(idleEv);
            assertEquals("json-config", idleEv.source);
            assertEquals("field=deviceIdleMode,result=false", String.valueOf(idleEv.value));
            assertNotNull(idleEv.note);
            assertFalse(idleEv.note.isEmpty());

            CapturedEvent lightIdleEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isDeviceLightIdleMode");
            assertNotNull(lightIdleEv);
            assertEquals("json-config", lightIdleEv.source);
            assertEquals("field=deviceLightIdleMode,result=false", String.valueOf(lightIdleEv.value));
            assertNotNull(lightIdleEv.note);
            assertFalse(lightIdleEv.note.isEmpty());

            CapturedEvent standbyEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isLowPowerStandbyEnabled");
            assertNotNull(standbyEv);
            assertEquals("json-config", standbyEv.source);
            assertEquals("field=lowPowerStandbyEnabled,result=false", String.valueOf(standbyEv.value));
            assertNotNull(standbyEv.note);
            assertFalse(standbyEv.note.isEmpty());

            CapturedEvent sustainedEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isSustainedPerformanceModeSupported");
            assertNotNull(sustainedEv);
            assertEquals("json-config", sustainedEv.source);
            assertEquals("field=sustainedPerformanceModeSupported,result=false",
                    String.valueOf(sustainedEv.value));
            assertNotNull(sustainedEv.note);
            assertFalse(sustainedEv.note.isEmpty());

            CapturedEvent rebootEv = findLastEvent(sink.events, "android_power",
                    "PowerManager.isRebootingUserspaceSupported");
            assertNotNull(rebootEv);
            assertEquals("json-config", rebootEv.source);
            assertEquals("field=rebootingUserspaceSupported,result=false",
                    String.valueOf(rebootEv.value));
            assertNotNull(rebootEv.note);
            assertFalse(rebootEv.note.isEmpty());

            assertEquals(1, countEvents(sink.events, "android_power", "PowerManager.isInteractive"));
            assertEquals(1, countEvents(sink.events, "android_power", "PowerManager.isPowerSaveMode"));
            assertEquals(1, countEvents(sink.events, "android_power", "PowerManager.isScreenOn"));
            assertEquals(1, countEvents(sink.events, "android_power", "PowerManager.isDeviceIdleMode"));
            assertEquals(1, countEvents(sink.events, "android_power", "PowerManager.isDeviceLightIdleMode"));
            assertEquals(1, countEvents(sink.events, "android_power", "PowerManager.isLowPowerStandbyEnabled"));
            assertEquals(1, countEvents(sink.events, "android_power",
                    "PowerManager.isSustainedPerformanceModeSupported"));
            assertEquals(1, countEvents(sink.events, "android_power",
                    "PowerManager.isRebootingUserspaceSupported"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentPower(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_POWER_JSON);
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

            DvmObject<?> powerManager = vm.resolveClass("android/os/PowerManager").newObject(null);
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isInteractive");
                fail("expected UnsupportedOperationException for isInteractive without power config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PowerManager->isInteractive"));
            }
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isPowerSaveMode");
                fail("expected UnsupportedOperationException for isPowerSaveMode without power config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PowerManager->isPowerSaveMode"));
            }
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isScreenOn");
                fail("expected UnsupportedOperationException for isScreenOn without power config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PowerManager->isScreenOn"));
            }
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isDeviceIdleMode");
                fail("expected UnsupportedOperationException for isDeviceIdleMode without power config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PowerManager->isDeviceIdleMode"));
            }
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isDeviceLightIdleMode");
                fail("expected UnsupportedOperationException for isDeviceLightIdleMode without power config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PowerManager->isDeviceLightIdleMode"));
            }
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isLowPowerStandbyEnabled");
                fail("expected UnsupportedOperationException for isLowPowerStandbyEnabled without power config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PowerManager->isLowPowerStandbyEnabled"));
            }
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, powerManager,
                        "isSustainedPerformanceModeSupported");
                fail("expected UnsupportedOperationException for isSustainedPerformanceModeSupported without power config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains(
                        "PowerManager->isSustainedPerformanceModeSupported"));
            }
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, powerManager,
                        "isRebootingUserspaceSupported");
                fail("expected UnsupportedOperationException for isRebootingUserspaceSupported without power config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains(
                        "PowerManager->isRebootingUserspaceSupported"));
            }
            // unrelated method also stays UOE
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, powerManager, "isAmbientDisplayAvailable");
                fail("expected UnsupportedOperationException for isAmbientDisplayAvailable");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains(
                        "PowerManager->isAmbientDisplayAvailable"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_power event when config absent: " + e.api,
                        "android_power".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runTypedGetSystemService(boolean is64Bit, boolean useVaList) throws Exception {
        runTypedConfigured(is64Bit, useVaList);
        runTypedAbsent(is64Bit, useVaList);
    }

    private static void runTypedConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(POWER_FULL_JSON);
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
            DvmClass powerClass = vm.resolveClass("android/os/PowerManager");

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            int eventsBeforeApp = sink.events.size();
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, powerClass);
            assertPowerManagerMarker(fromApp);
            assertNoPowerOrThermalSince(sink, eventsBeforeApp);
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, fromApp, "isInteractive"));

            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            int eventsBeforeCtx = sink.events.size();
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, powerClass);
            assertPowerManagerMarker(fromCtx);
            assertNoPowerOrThermalSince(sink, eventsBeforeCtx);
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, fromCtx, "isInteractive"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runTypedAbsent(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_POWER_JSON);
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
            DvmClass powerClass = vm.resolveClass("android/os/PowerManager");

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, powerClass);
            assertPowerManagerMarker(fromApp);
            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, powerClass);
            assertPowerManagerMarker(fromCtx);
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, fromApp, "isInteractive");
                fail("expected UOE for isInteractive without android.power");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PowerManager->isInteractive"));
            }
            try {
                invokeNoArgBoolean(jni, baseVM, useVaList, fromCtx, "isInteractive");
                fail("expected UOE for isInteractive without android.power");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PowerManager->isInteractive"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected sidecar on typed lookup: " + e.api,
                        "android_power".equals(e.kind) || "android_thermal".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertPowerManagerMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals("android/os/PowerManager", manager.getObjectType().getClassName());
        assertEquals(SystemService.POWER_SERVICE, manager.getValue());
    }

    private static void assertNoPowerOrThermalSince(CapturingSink sink, int fromIndex) {
        for (int i = fromIndex; i < sink.events.size(); i++) {
            String kind = sink.events.get(i).kind;
            assertFalse("unexpected sidecar on typed lookup: " + sink.events.get(i).api,
                    "android_power".equals(kind) || "android_thermal".equals(kind));
        }
    }

    private static DvmObject<?> invokeGetSystemServiceClass(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                            DvmObject<?> receiver, DvmClass serviceClass) {
        int classHash = vm.addLocalObject(serviceClass);
        DvmClass dvmClass = receiver.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/Class;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, signature,
                    new TestVaList(vm, method, classHash));
        }
        return jni.callObjectMethod(vm, receiver, signature,
                new TestVarArg(vm, method, classHash));
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

    private static boolean invokeIsIgnoringBatteryOptimizations(AbstractJni jni, BaseVM vm,
                                                                boolean useVaList,
                                                                DvmObject<?> powerManager,
                                                                String packageName) {
        int packageHash = packageName == null ? 0 : vm.addLocalObject(new StringObject(vm, packageName));
        DvmClass dvmClass = powerManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "isIgnoringBatteryOptimizations",
                IS_IGNORING_BATTERY_OPTIMIZATIONS_ARGS, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, powerManager, signature,
                    new TestVaList(vm, method, packageHash));
        }
        return jni.callBooleanMethod(vm, powerManager, signature,
                new TestVarArg(vm, method, packageHash));
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
