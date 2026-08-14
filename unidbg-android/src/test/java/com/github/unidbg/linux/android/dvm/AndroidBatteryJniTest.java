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

public class AndroidBatteryJniTest {

    /** BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER */
    private static final int BATTERY_PROPERTY_CHARGE_COUNTER = 1;
    /** BatteryManager.BATTERY_PROPERTY_CURRENT_NOW */
    private static final int BATTERY_PROPERTY_CURRENT_NOW = 2;
    /** BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE */
    private static final int BATTERY_PROPERTY_CURRENT_AVERAGE = 3;
    /** BatteryManager.BATTERY_PROPERTY_CAPACITY */
    private static final int BATTERY_PROPERTY_CAPACITY = 4;
    /** BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER */
    private static final int BATTERY_PROPERTY_ENERGY_COUNTER = 5;
    /** BatteryManager.BATTERY_PROPERTY_STATUS */
    private static final int BATTERY_PROPERTY_STATUS = 6;

    private static final String BATTERY_VALUE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"battery\":{\"capacityPercent\":42,\"charging\":true}"
            + "}"
            + "}";

    private static final String BATTERY_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"battery\":{}"
            + "}"
            + "}";

    private static final String BATTERY_CHARGE_COUNTER_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"battery\":{\"capacityPercent\":42,\"charging\":true,\"chargeCounterUah\":2500000}"
            + "}"
            + "}";

    private static final String BATTERY_CURRENT_NOW_POS_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"battery\":{\"capacityPercent\":42,\"charging\":true,\"currentNowUa\":350000}"
            + "}"
            + "}";

    private static final String BATTERY_CURRENT_NOW_NEG_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"battery\":{\"capacityPercent\":42,\"charging\":false,\"currentNowUa\":-450000}"
            + "}"
            + "}";

    private static final String BATTERY_CURRENT_AVG_POS_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"battery\":{\"capacityPercent\":42,\"charging\":true,\"currentAverageUa\":280000}"
            + "}"
            + "}";

    private static final String BATTERY_CURRENT_AVG_NEG_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"battery\":{\"capacityPercent\":42,\"charging\":false,\"currentAverageUa\":-320000}"
            + "}"
            + "}";

    private static final String BATTERY_ENERGY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"battery\":{\"capacityPercent\":42,\"charging\":true,\"energyCounterNwh\":55000000000}"
            + "}"
            + "}";

    private static final String BATTERY_ENERGY_MAX_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"battery\":{\"capacityPercent\":42,\"energyCounterNwh\":9223372036854775807}"
            + "}"
            + "}";

    private static final String BATTERY_ENERGY_ZERO_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"battery\":{\"capacityPercent\":42,\"energyCounterNwh\":0}"
            + "}"
            + "}";

    private static final String BATTERY_STATUS_CHARGING_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"battery\":{\"capacityPercent\":42,\"charging\":false,\"status\":2}"
            + "}"
            + "}";

    private static final String BATTERY_STATUS_FULL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"battery\":{\"capacityPercent\":42,\"status\":5}"
            + "}"
            + "}";

    private static final String BATTERY_CHARGE_TIME_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"battery\":{\"capacityPercent\":42,\"charging\":false,"
            + "\"status\":3,\"chargeTimeRemainingMillis\":3600000}"
            + "}"
            + "}";

    private static final String BATTERY_CHARGE_TIME_UNKNOWN_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"battery\":{\"capacityPercent\":42,\"charging\":true,"
            + "\"chargeTimeRemainingMillis\":-1}"
            + "}"
            + "}";

    private static final String NO_BATTERY_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testBatteryValueVarArg32() throws Exception {
        runConfiguredBattery(false, false, BATTERY_VALUE_JSON, 42, true);
    }

    @Test
    public void testBatteryValueVaList64() throws Exception {
        runConfiguredBattery(true, true, BATTERY_VALUE_JSON, 42, true);
    }

    @Test
    public void testBatteryEmptyDefaultVarArg32() throws Exception {
        runConfiguredBattery(false, false, BATTERY_EMPTY_JSON, 73, false);
    }

    @Test
    public void testBatteryEmptyDefaultVaList64() throws Exception {
        runConfiguredBattery(true, true, BATTERY_EMPTY_JSON, 73, false);
    }

    @Test
    public void testBatteryAbsentVarArg32() throws Exception {
        runAbsentBattery(false, false);
    }

    @Test
    public void testBatteryAbsentVaList64() throws Exception {
        runAbsentBattery(true, true);
    }

    @Test
    public void testBatteryTypedGetSystemServiceVarArg32() throws Exception {
        runTypedGetSystemService(false, false);
    }

    @Test
    public void testBatteryTypedGetSystemServiceVaList64() throws Exception {
        runTypedGetSystemService(true, true);
    }

    @Test
    public void testChargeCounterUahVarArg32() throws Exception {
        runConfiguredChargeCounter(false, false, BATTERY_CHARGE_COUNTER_JSON, 2500000);
    }

    @Test
    public void testChargeCounterUahVaList64() throws Exception {
        runConfiguredChargeCounter(true, true, BATTERY_CHARGE_COUNTER_JSON, 2500000);
    }

    @Test
    public void testChargeCounterUahAbsentFieldVarArg32() throws Exception {
        runChargeCounterAbsentField(false, false, BATTERY_VALUE_JSON);
    }

    @Test
    public void testChargeCounterUahAbsentFieldVaList64() throws Exception {
        runChargeCounterAbsentField(true, true, BATTERY_VALUE_JSON);
    }

    @Test
    public void testCurrentNowUaPositiveVarArg32() throws Exception {
        runConfiguredCurrentNow(false, false, BATTERY_CURRENT_NOW_POS_JSON, 350000);
    }

    @Test
    public void testCurrentNowUaPositiveVaList64() throws Exception {
        runConfiguredCurrentNow(true, true, BATTERY_CURRENT_NOW_POS_JSON, 350000);
    }

    @Test
    public void testCurrentNowUaNegativeVarArg32() throws Exception {
        runConfiguredCurrentNow(false, false, BATTERY_CURRENT_NOW_NEG_JSON, -450000);
    }

    @Test
    public void testCurrentNowUaNegativeVaList64() throws Exception {
        runConfiguredCurrentNow(true, true, BATTERY_CURRENT_NOW_NEG_JSON, -450000);
    }

    @Test
    public void testCurrentNowUaAbsentFieldVarArg32() throws Exception {
        runCurrentNowAbsentField(false, false, BATTERY_VALUE_JSON);
    }

    @Test
    public void testCurrentNowUaAbsentFieldVaList64() throws Exception {
        runCurrentNowAbsentField(true, true, BATTERY_VALUE_JSON);
    }

    @Test
    public void testCurrentAverageUaPositiveVarArg32() throws Exception {
        runConfiguredCurrentAverage(false, false, BATTERY_CURRENT_AVG_POS_JSON, 280000);
    }

    @Test
    public void testCurrentAverageUaPositiveVaList64() throws Exception {
        runConfiguredCurrentAverage(true, true, BATTERY_CURRENT_AVG_POS_JSON, 280000);
    }

    @Test
    public void testCurrentAverageUaNegativeVarArg32() throws Exception {
        runConfiguredCurrentAverage(false, false, BATTERY_CURRENT_AVG_NEG_JSON, -320000);
    }

    @Test
    public void testCurrentAverageUaNegativeVaList64() throws Exception {
        runConfiguredCurrentAverage(true, true, BATTERY_CURRENT_AVG_NEG_JSON, -320000);
    }

    @Test
    public void testCurrentAverageUaAbsentFieldVarArg32() throws Exception {
        runCurrentAverageAbsentField(false, false, BATTERY_VALUE_JSON);
    }

    @Test
    public void testCurrentAverageUaAbsentFieldVaList64() throws Exception {
        runCurrentAverageAbsentField(true, true, BATTERY_VALUE_JSON);
    }

    @Test
    public void testEnergyCounterNwhVarArg32() throws Exception {
        runConfiguredEnergyCounter(false, false, BATTERY_ENERGY_JSON, 55000000000L);
    }

    @Test
    public void testEnergyCounterNwhVaList64() throws Exception {
        runConfiguredEnergyCounter(true, true, BATTERY_ENERGY_JSON, 55000000000L);
    }

    @Test
    public void testEnergyCounterNwhZeroVarArg32() throws Exception {
        runConfiguredEnergyCounter(false, false, BATTERY_ENERGY_ZERO_JSON, 0L);
    }

    @Test
    public void testEnergyCounterNwhMaxVaList64() throws Exception {
        runConfiguredEnergyCounter(true, true, BATTERY_ENERGY_MAX_JSON, 9223372036854775807L);
    }

    @Test
    public void testEnergyCounterNwhAbsentFieldVarArg32() throws Exception {
        runEnergyCounterAbsentField(false, false, BATTERY_VALUE_JSON);
    }

    @Test
    public void testEnergyCounterNwhAbsentFieldVaList64() throws Exception {
        runEnergyCounterAbsentField(true, true, BATTERY_VALUE_JSON);
    }

    @Test
    public void testStatusChargingVarArg32() throws Exception {
        runConfiguredStatus(false, false, BATTERY_STATUS_CHARGING_JSON, 2);
    }

    @Test
    public void testStatusChargingVaList64() throws Exception {
        runConfiguredStatus(true, true, BATTERY_STATUS_CHARGING_JSON, 2);
    }

    @Test
    public void testStatusFullVarArg32() throws Exception {
        runConfiguredStatus(false, false, BATTERY_STATUS_FULL_JSON, 5);
    }

    @Test
    public void testStatusFullVaList64() throws Exception {
        runConfiguredStatus(true, true, BATTERY_STATUS_FULL_JSON, 5);
    }

    @Test
    public void testStatusAbsentFieldVarArg32() throws Exception {
        runStatusAbsentField(false, false, BATTERY_VALUE_JSON);
    }

    @Test
    public void testStatusAbsentFieldVaList64() throws Exception {
        runStatusAbsentField(true, true, BATTERY_VALUE_JSON);
    }

    @Test
    public void testChargeTimeRemainingMillisVarArg32() throws Exception {
        runConfiguredChargeTimeRemaining(false, false, BATTERY_CHARGE_TIME_JSON, 3600000L);
    }

    @Test
    public void testChargeTimeRemainingMillisVaList64() throws Exception {
        runConfiguredChargeTimeRemaining(true, true, BATTERY_CHARGE_TIME_JSON, 3600000L);
    }

    @Test
    public void testChargeTimeRemainingMillisUnknownVarArg32() throws Exception {
        runConfiguredChargeTimeRemaining(false, false, BATTERY_CHARGE_TIME_UNKNOWN_JSON, -1L);
    }

    @Test
    public void testChargeTimeRemainingMillisUnknownVaList64() throws Exception {
        runConfiguredChargeTimeRemaining(true, true, BATTERY_CHARGE_TIME_UNKNOWN_JSON, -1L);
    }

    @Test
    public void testChargeTimeRemainingMillisAbsentFieldVarArg32() throws Exception {
        runChargeTimeRemainingAbsentField(false, false, BATTERY_VALUE_JSON);
    }

    @Test
    public void testChargeTimeRemainingMillisAbsentFieldVaList64() throws Exception {
        runChargeTimeRemainingAbsentField(true, true, BATTERY_VALUE_JSON);
    }

    private static void runConfiguredBattery(boolean is64Bit, boolean useVaList,
                                             String json, int expectedCapacity,
                                             boolean expectedCharging) throws Exception {
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

            DvmObject<?> batteryManager = vm.resolveClass("android/os/BatteryManager").newObject(null);
            assertEquals(expectedCapacity,
                    invokeGetIntProperty(jni, baseVM, useVaList, batteryManager, BATTERY_PROPERTY_CAPACITY));

            CapturedEvent capacityEv = findLastEvent(sink.events, "android_battery",
                    "BatteryManager.getIntProperty");
            assertNotNull(capacityEv);
            assertEquals("json-config", capacityEv.source);
            assertEquals("propertyId=4,field=capacityPercent,result=" + expectedCapacity,
                    String.valueOf(capacityEv.value));
            assertNotNull(capacityEv.note);
            assertFalse(capacityEv.note.isEmpty());

            long expectedCapacityLong = expectedCapacity;
            assertEquals(expectedCapacityLong,
                    invokeGetLongProperty(jni, baseVM, useVaList, batteryManager, BATTERY_PROPERTY_CAPACITY));

            CapturedEvent longCapacityEv = findLastEvent(sink.events, "android_battery",
                    "BatteryManager.getLongProperty");
            assertNotNull(longCapacityEv);
            assertEquals("json-config", longCapacityEv.source);
            assertEquals("propertyId=4,field=capacityPercent,result=" + expectedCapacityLong,
                    String.valueOf(longCapacityEv.value));
            assertNotNull(longCapacityEv.note);
            assertFalse(longCapacityEv.note.isEmpty());

            assertEquals(expectedCharging,
                    invokeIsCharging(jni, baseVM, useVaList, batteryManager));
            CapturedEvent chargingEv = findLastEvent(sink.events, "android_battery",
                    "BatteryManager.isCharging");
            assertNotNull(chargingEv);
            assertEquals("json-config", chargingEv.source);
            assertEquals("field=charging,result=" + expectedCharging,
                    String.valueOf(chargingEv.value));
            assertNotNull(chargingEv.note);
            assertFalse(chargingEv.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_battery", "BatteryManager.isCharging"));

            int eventsBeforeUnsupported = sink.events.size();
            // propertyId 1/2/3 without optional keys + other ids: no android_battery event
            for (int propertyId : new int[] {1, 2, 3, 5, 6}) {
                try {
                    invokeGetIntProperty(jni, baseVM, useVaList, batteryManager, propertyId);
                    fail("expected UnsupportedOperationException for propertyId=" + propertyId);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("BatteryManager->getIntProperty"));
                }
                try {
                    invokeGetLongProperty(jni, baseVM, useVaList, batteryManager, propertyId);
                    fail("expected UnsupportedOperationException for getLongProperty propertyId="
                            + propertyId);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("BatteryManager->getLongProperty"));
                }
            }
            for (int i = eventsBeforeUnsupported; i < sink.events.size(); i++) {
                assertFalse("unexpected android_battery event for non-capacity property",
                        "android_battery".equals(sink.events.get(i).kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredChargeCounter(boolean is64Bit, boolean useVaList,
                                                   String json, int expectedCounter)
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

            DvmObject<?> batteryManager = vm.resolveClass("android/os/BatteryManager").newObject(null);

            // capacity path preserved
            assertEquals(42, invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                    BATTERY_PROPERTY_CAPACITY));

            assertEquals(expectedCounter, invokeGetIntProperty(jni, baseVM, useVaList,
                    batteryManager, BATTERY_PROPERTY_CHARGE_COUNTER));
            CapturedEvent intEv = findLastEvent(sink.events, "android_battery",
                    "BatteryManager.getIntProperty");
            assertNotNull(intEv);
            assertEquals("json-config", intEv.source);
            assertEquals("propertyId=1,field=chargeCounterUah,result=" + expectedCounter,
                    String.valueOf(intEv.value));
            assertNotNull(intEv.note);
            assertFalse(intEv.note.isEmpty());

            long expectedLong = expectedCounter;
            assertEquals(expectedLong, invokeGetLongProperty(jni, baseVM, useVaList,
                    batteryManager, BATTERY_PROPERTY_CHARGE_COUNTER));
            CapturedEvent longEv = findLastEvent(sink.events, "android_battery",
                    "BatteryManager.getLongProperty");
            assertNotNull(longEv);
            assertEquals("json-config", longEv.source);
            assertEquals("propertyId=1,field=chargeCounterUah,result=" + expectedLong,
                    String.valueOf(longEv.value));
            assertNotNull(longEv.note);
            assertFalse(longEv.note.isEmpty());

            // other propertyIds still UOE / no new events for them (currentNow/avg not configured)
            int eventsBefore = sink.events.size();
            for (int propertyId : new int[] {2, 3, 5}) {
                try {
                    invokeGetIntProperty(jni, baseVM, useVaList, batteryManager, propertyId);
                    fail("expected UOE for propertyId=" + propertyId);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getIntProperty"));
                }
            }
            for (int i = eventsBefore; i < sink.events.size(); i++) {
                assertFalse("unexpected android_battery for unsupported propertyId",
                        "android_battery".equals(sink.events.get(i).kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredCurrentNow(boolean is64Bit, boolean useVaList,
                                                String json, int expectedCurrent)
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

            DvmObject<?> batteryManager = vm.resolveClass("android/os/BatteryManager").newObject(null);

            // capacity path preserved (propertyId 4)
            assertEquals(42, invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                    BATTERY_PROPERTY_CAPACITY));

            assertEquals(expectedCurrent, invokeGetIntProperty(jni, baseVM, useVaList,
                    batteryManager, BATTERY_PROPERTY_CURRENT_NOW));
            CapturedEvent intEv = findLastEvent(sink.events, "android_battery",
                    "BatteryManager.getIntProperty");
            assertNotNull(intEv);
            assertEquals("json-config", intEv.source);
            assertEquals("propertyId=2,field=currentNowUa,result=" + expectedCurrent,
                    String.valueOf(intEv.value));
            assertNotNull(intEv.note);
            assertFalse(intEv.note.isEmpty());

            long expectedLong = expectedCurrent;
            assertEquals(expectedLong, invokeGetLongProperty(jni, baseVM, useVaList,
                    batteryManager, BATTERY_PROPERTY_CURRENT_NOW));
            CapturedEvent longEv = findLastEvent(sink.events, "android_battery",
                    "BatteryManager.getLongProperty");
            assertNotNull(longEv);
            assertEquals("json-config", longEv.source);
            assertEquals("propertyId=2,field=currentNowUa,result=" + expectedLong,
                    String.valueOf(longEv.value));
            assertNotNull(longEv.note);
            assertFalse(longEv.note.isEmpty());

            // chargeCounter / currentAverage still UOE when not configured; other ids UOE
            int eventsBefore = sink.events.size();
            for (int propertyId : new int[] {1, 3, 5}) {
                try {
                    invokeGetIntProperty(jni, baseVM, useVaList, batteryManager, propertyId);
                    fail("expected UOE for propertyId=" + propertyId);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getIntProperty"));
                }
                try {
                    invokeGetLongProperty(jni, baseVM, useVaList, batteryManager, propertyId);
                    fail("expected UOE for getLongProperty propertyId=" + propertyId);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getLongProperty"));
                }
            }
            for (int i = eventsBefore; i < sink.events.size(); i++) {
                assertFalse("unexpected android_battery for unsupported propertyId",
                        "android_battery".equals(sink.events.get(i).kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredCurrentAverage(boolean is64Bit, boolean useVaList,
                                                    String json, int expectedAverage)
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

            DvmObject<?> batteryManager = vm.resolveClass("android/os/BatteryManager").newObject(null);

            // capacity path preserved (propertyId 4)
            assertEquals(42, invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                    BATTERY_PROPERTY_CAPACITY));

            assertEquals(expectedAverage, invokeGetIntProperty(jni, baseVM, useVaList,
                    batteryManager, BATTERY_PROPERTY_CURRENT_AVERAGE));
            CapturedEvent intEv = findLastEvent(sink.events, "android_battery",
                    "BatteryManager.getIntProperty");
            assertNotNull(intEv);
            assertEquals("json-config", intEv.source);
            assertEquals("propertyId=3,field=currentAverageUa,result=" + expectedAverage,
                    String.valueOf(intEv.value));
            assertNotNull(intEv.note);
            assertFalse(intEv.note.isEmpty());

            long expectedLong = expectedAverage;
            assertEquals(expectedLong, invokeGetLongProperty(jni, baseVM, useVaList,
                    batteryManager, BATTERY_PROPERTY_CURRENT_AVERAGE));
            CapturedEvent longEv = findLastEvent(sink.events, "android_battery",
                    "BatteryManager.getLongProperty");
            assertNotNull(longEv);
            assertEquals("json-config", longEv.source);
            assertEquals("propertyId=3,field=currentAverageUa,result=" + expectedLong,
                    String.valueOf(longEv.value));
            assertNotNull(longEv.note);
            assertFalse(longEv.note.isEmpty());

            // chargeCounter / currentNow still UOE when not configured; other ids UOE
            int eventsBefore = sink.events.size();
            for (int propertyId : new int[] {1, 2, 5}) {
                try {
                    invokeGetIntProperty(jni, baseVM, useVaList, batteryManager, propertyId);
                    fail("expected UOE for propertyId=" + propertyId);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getIntProperty"));
                }
                try {
                    invokeGetLongProperty(jni, baseVM, useVaList, batteryManager, propertyId);
                    fail("expected UOE for getLongProperty propertyId=" + propertyId);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getLongProperty"));
                }
            }
            for (int i = eventsBefore; i < sink.events.size(); i++) {
                assertFalse("unexpected android_battery for unsupported propertyId",
                        "android_battery".equals(sink.events.get(i).kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runCurrentAverageAbsentField(boolean is64Bit, boolean useVaList, String json)
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

            DvmObject<?> batteryManager = vm.resolveClass("android/os/BatteryManager").newObject(null);
            // capacity still works without currentAverageUa
            assertEquals(42, invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                    BATTERY_PROPERTY_CAPACITY));
            try {
                invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                        BATTERY_PROPERTY_CURRENT_AVERAGE);
                fail("expected UOE for propertyId=3 when currentAverageUa absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getIntProperty"));
            }
            try {
                invokeGetLongProperty(jni, baseVM, useVaList, batteryManager,
                        BATTERY_PROPERTY_CURRENT_AVERAGE);
                fail("expected UOE for getLongProperty propertyId=3 when currentAverageUa absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLongProperty"));
            }
            // only capacity event, no currentAverage events
            assertEquals(1, countEvents(sink.events, "android_battery",
                    "BatteryManager.getIntProperty"));
            for (CapturedEvent e : sink.events) {
                if ("android_battery".equals(e.kind)
                        && String.valueOf(e.value).contains("currentAverageUa")) {
                    fail("unexpected currentAverageUa event when field absent: " + e.value);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredEnergyCounter(boolean is64Bit, boolean useVaList,
                                                   String json, long expectedEnergy)
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

            DvmObject<?> batteryManager = vm.resolveClass("android/os/BatteryManager").newObject(null);

            // capacity path preserved (propertyId 4)
            assertEquals(42, invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                    BATTERY_PROPERTY_CAPACITY));

            // getLongProperty propertyId=5 returns energy
            assertEquals(expectedEnergy, invokeGetLongProperty(jni, baseVM, useVaList,
                    batteryManager, BATTERY_PROPERTY_ENERGY_COUNTER));
            CapturedEvent longEv = findLastEvent(sink.events, "android_battery",
                    "BatteryManager.getLongProperty");
            assertNotNull(longEv);
            assertEquals("json-config", longEv.source);
            assertEquals("propertyId=5,field=energyCounterNwh,result=" + expectedEnergy,
                    String.valueOf(longEv.value));
            assertNotNull(longEv.note);
            assertFalse(longEv.note.isEmpty());

            // getIntProperty propertyId=5 remains UOE even when energy configured (no truncation)
            int eventsBeforeInt5 = sink.events.size();
            try {
                invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                        BATTERY_PROPERTY_ENERGY_COUNTER);
                fail("expected UOE for getIntProperty propertyId=5 even when energyCounterNwh set");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getIntProperty"));
            }
            for (int i = eventsBeforeInt5; i < sink.events.size(); i++) {
                assertFalse("unexpected android_battery for getIntProperty propertyId=5",
                        "android_battery".equals(sink.events.get(i).kind));
            }

            // other optional ids still UOE when not configured (incl. status int-only 6)
            int eventsBefore = sink.events.size();
            for (int propertyId : new int[] {1, 2, 3, 6}) {
                try {
                    invokeGetIntProperty(jni, baseVM, useVaList, batteryManager, propertyId);
                    fail("expected UOE for propertyId=" + propertyId);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getIntProperty"));
                }
                try {
                    invokeGetLongProperty(jni, baseVM, useVaList, batteryManager, propertyId);
                    fail("expected UOE for getLongProperty propertyId=" + propertyId);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getLongProperty"));
                }
            }
            for (int i = eventsBefore; i < sink.events.size(); i++) {
                assertFalse("unexpected android_battery for unsupported propertyId",
                        "android_battery".equals(sink.events.get(i).kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredStatus(boolean is64Bit, boolean useVaList,
                                            String json, int expectedStatus)
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

            DvmObject<?> batteryManager = vm.resolveClass("android/os/BatteryManager").newObject(null);

            // capacity path preserved (propertyId 4)
            assertEquals(42, invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                    BATTERY_PROPERTY_CAPACITY));

            // getIntProperty propertyId=6 returns status
            assertEquals(expectedStatus, invokeGetIntProperty(jni, baseVM, useVaList,
                    batteryManager, BATTERY_PROPERTY_STATUS));
            CapturedEvent intEv = findLastEvent(sink.events, "android_battery",
                    "BatteryManager.getIntProperty");
            assertNotNull(intEv);
            assertEquals("json-config", intEv.source);
            assertEquals("propertyId=6,field=status,result=" + expectedStatus,
                    String.valueOf(intEv.value));
            assertNotNull(intEv.note);
            assertFalse(intEv.note.isEmpty());

            // getLongProperty propertyId=6 remains UOE even when status configured
            int eventsBeforeLong6 = sink.events.size();
            try {
                invokeGetLongProperty(jni, baseVM, useVaList, batteryManager,
                        BATTERY_PROPERTY_STATUS);
                fail("expected UOE for getLongProperty propertyId=6 even when status set");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLongProperty"));
            }
            for (int i = eventsBeforeLong6; i < sink.events.size(); i++) {
                assertFalse("unexpected android_battery for getLongProperty propertyId=6",
                        "android_battery".equals(sink.events.get(i).kind));
            }

            // other optional ids still UOE when not configured
            int eventsBefore = sink.events.size();
            for (int propertyId : new int[] {1, 2, 3, 5}) {
                try {
                    invokeGetIntProperty(jni, baseVM, useVaList, batteryManager, propertyId);
                    fail("expected UOE for propertyId=" + propertyId);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getIntProperty"));
                }
                try {
                    invokeGetLongProperty(jni, baseVM, useVaList, batteryManager, propertyId);
                    fail("expected UOE for getLongProperty propertyId=" + propertyId);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getLongProperty"));
                }
            }
            for (int i = eventsBefore; i < sink.events.size(); i++) {
                assertFalse("unexpected android_battery for unsupported propertyId",
                        "android_battery".equals(sink.events.get(i).kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runStatusAbsentField(boolean is64Bit, boolean useVaList, String json)
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

            DvmObject<?> batteryManager = vm.resolveClass("android/os/BatteryManager").newObject(null);
            // capacity still works without status
            assertEquals(42, invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                    BATTERY_PROPERTY_CAPACITY));
            try {
                invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                        BATTERY_PROPERTY_STATUS);
                fail("expected UOE for getIntProperty propertyId=6 when status absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getIntProperty"));
            }
            try {
                invokeGetLongProperty(jni, baseVM, useVaList, batteryManager,
                        BATTERY_PROPERTY_STATUS);
                fail("expected UOE for getLongProperty propertyId=6 when status absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLongProperty"));
            }
            // only capacity int event, no status events
            assertEquals(1, countEvents(sink.events, "android_battery",
                    "BatteryManager.getIntProperty"));
            for (CapturedEvent e : sink.events) {
                if ("android_battery".equals(e.kind)
                        && String.valueOf(e.value).contains("field=status")) {
                    fail("unexpected status event when field absent: " + e.value);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredChargeTimeRemaining(boolean is64Bit, boolean useVaList,
                                                         String json, long expectedMillis)
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

            DvmObject<?> batteryManager = vm.resolveClass("android/os/BatteryManager").newObject(null);

            // capacity / isCharging preserved (not inferred from charge time)
            assertEquals(42, invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                    BATTERY_PROPERTY_CAPACITY));
            boolean expectedCharging = config.getAndroidBatteryConfig().isCharging();
            assertEquals(expectedCharging,
                    invokeIsCharging(jni, baseVM, useVaList, batteryManager));

            assertEquals(expectedMillis, invokeComputeChargeTimeRemaining(jni, baseVM, useVaList,
                    batteryManager));
            CapturedEvent longEv = findLastEvent(sink.events, "android_battery",
                    "BatteryManager.computeChargeTimeRemaining");
            assertNotNull(longEv);
            assertEquals("json-config", longEv.source);
            assertEquals("field=chargeTimeRemainingMillis,result=" + expectedMillis,
                    String.valueOf(longEv.value));
            assertNotNull(longEv.note);
            assertFalse(longEv.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_battery",
                    "BatteryManager.computeChargeTimeRemaining"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runChargeTimeRemainingAbsentField(boolean is64Bit, boolean useVaList,
                                                          String json) throws Exception {
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

            DvmObject<?> batteryManager = vm.resolveClass("android/os/BatteryManager").newObject(null);
            assertEquals(42, invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                    BATTERY_PROPERTY_CAPACITY));
            int eventsBefore = sink.events.size();
            try {
                invokeComputeChargeTimeRemaining(jni, baseVM, useVaList, batteryManager);
                fail("expected UOE for computeChargeTimeRemaining when field absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("computeChargeTimeRemaining"));
            }
            for (int i = eventsBefore; i < sink.events.size(); i++) {
                assertFalse("unexpected computeChargeTimeRemaining event when field absent",
                        "android_battery".equals(sink.events.get(i).kind)
                                && "BatteryManager.computeChargeTimeRemaining"
                                .equals(sink.events.get(i).api));
            }
            for (CapturedEvent e : sink.events) {
                if ("android_battery".equals(e.kind)
                        && String.valueOf(e.value).contains("chargeTimeRemainingMillis")) {
                    fail("unexpected chargeTimeRemainingMillis event when field absent: " + e.value);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runEnergyCounterAbsentField(boolean is64Bit, boolean useVaList, String json)
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

            DvmObject<?> batteryManager = vm.resolveClass("android/os/BatteryManager").newObject(null);
            // capacity still works without energyCounterNwh
            assertEquals(42, invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                    BATTERY_PROPERTY_CAPACITY));
            try {
                invokeGetLongProperty(jni, baseVM, useVaList, batteryManager,
                        BATTERY_PROPERTY_ENERGY_COUNTER);
                fail("expected UOE for getLongProperty propertyId=5 when energyCounterNwh absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLongProperty"));
            }
            try {
                invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                        BATTERY_PROPERTY_ENERGY_COUNTER);
                fail("expected UOE for getIntProperty propertyId=5 when energyCounterNwh absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getIntProperty"));
            }
            // only capacity int event, no energy events
            assertEquals(1, countEvents(sink.events, "android_battery",
                    "BatteryManager.getIntProperty"));
            assertEquals(0, countEvents(sink.events, "android_battery",
                    "BatteryManager.getLongProperty"));
            for (CapturedEvent e : sink.events) {
                if ("android_battery".equals(e.kind)
                        && String.valueOf(e.value).contains("energyCounterNwh")) {
                    fail("unexpected energyCounterNwh event when field absent: " + e.value);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runCurrentNowAbsentField(boolean is64Bit, boolean useVaList, String json)
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

            DvmObject<?> batteryManager = vm.resolveClass("android/os/BatteryManager").newObject(null);
            // capacity still works without currentNowUa
            assertEquals(42, invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                    BATTERY_PROPERTY_CAPACITY));
            try {
                invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                        BATTERY_PROPERTY_CURRENT_NOW);
                fail("expected UOE for propertyId=2 when currentNowUa absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getIntProperty"));
            }
            try {
                invokeGetLongProperty(jni, baseVM, useVaList, batteryManager,
                        BATTERY_PROPERTY_CURRENT_NOW);
                fail("expected UOE for getLongProperty propertyId=2 when currentNowUa absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLongProperty"));
            }
            // only capacity event, no currentNow events
            assertEquals(1, countEvents(sink.events, "android_battery",
                    "BatteryManager.getIntProperty"));
            for (CapturedEvent e : sink.events) {
                if ("android_battery".equals(e.kind)
                        && String.valueOf(e.value).contains("currentNowUa")) {
                    fail("unexpected currentNowUa event when field absent: " + e.value);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runChargeCounterAbsentField(boolean is64Bit, boolean useVaList, String json)
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

            DvmObject<?> batteryManager = vm.resolveClass("android/os/BatteryManager").newObject(null);
            // capacity still works without chargeCounterUah
            assertEquals(42, invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                    BATTERY_PROPERTY_CAPACITY));
            try {
                invokeGetIntProperty(jni, baseVM, useVaList, batteryManager,
                        BATTERY_PROPERTY_CHARGE_COUNTER);
                fail("expected UOE for propertyId=1 when chargeCounterUah absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getIntProperty"));
            }
            try {
                invokeGetLongProperty(jni, baseVM, useVaList, batteryManager,
                        BATTERY_PROPERTY_CHARGE_COUNTER);
                fail("expected UOE for getLongProperty propertyId=1 when chargeCounterUah absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLongProperty"));
            }
            // only capacity event, no charge-counter events
            assertEquals(1, countEvents(sink.events, "android_battery",
                    "BatteryManager.getIntProperty"));
            for (CapturedEvent e : sink.events) {
                if ("android_battery".equals(e.kind)
                        && String.valueOf(e.value).contains("chargeCounterUah")) {
                    fail("unexpected chargeCounterUah event when field absent: " + e.value);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentBattery(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_BATTERY_JSON);
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

            DvmObject<?> batteryManager = vm.resolveClass("android/os/BatteryManager").newObject(null);
            try {
                invokeGetIntProperty(jni, baseVM, useVaList, batteryManager, BATTERY_PROPERTY_CAPACITY);
                fail("expected UnsupportedOperationException without battery config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("BatteryManager->getIntProperty"));
            }
            try {
                invokeGetLongProperty(jni, baseVM, useVaList, batteryManager, BATTERY_PROPERTY_CAPACITY);
                fail("expected UnsupportedOperationException without battery config for getLongProperty");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("BatteryManager->getLongProperty"));
            }
            try {
                invokeIsCharging(jni, baseVM, useVaList, batteryManager);
                fail("expected UnsupportedOperationException without battery config for isCharging");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("BatteryManager->isCharging"));
            }
            try {
                invokeComputeChargeTimeRemaining(jni, baseVM, useVaList, batteryManager);
                fail("expected UOE without battery config for computeChargeTimeRemaining");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("computeChargeTimeRemaining"));
            }
            for (int propertyId : new int[] {1, 2, 3, 5, 6}) {
                try {
                    invokeGetIntProperty(jni, baseVM, useVaList, batteryManager, propertyId);
                    fail("expected UnsupportedOperationException for propertyId=" + propertyId);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("BatteryManager->getIntProperty"));
                }
                try {
                    invokeGetLongProperty(jni, baseVM, useVaList, batteryManager, propertyId);
                    fail("expected UnsupportedOperationException for getLongProperty propertyId="
                            + propertyId);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("BatteryManager->getLongProperty"));
                }
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_battery event when config absent: " + e.api,
                        "android_battery".equals(e.kind));
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
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(BATTERY_VALUE_JSON);
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
            DvmClass batteryClass = vm.resolveClass("android/os/BatteryManager");

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            int eventsBeforeApp = sink.events.size();
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, batteryClass);
            assertBatteryManagerMarker(fromApp);
            assertNoAndroidBatterySince(sink, eventsBeforeApp);
            assertEquals(42, invokeGetIntProperty(jni, baseVM, useVaList, fromApp, BATTERY_PROPERTY_CAPACITY));

            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            int eventsBeforeCtx = sink.events.size();
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, batteryClass);
            assertBatteryManagerMarker(fromCtx);
            assertNoAndroidBatterySince(sink, eventsBeforeCtx);
            assertTrue(invokeIsCharging(jni, baseVM, useVaList, fromCtx));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runTypedAbsent(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_BATTERY_JSON);
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
            DvmClass batteryClass = vm.resolveClass("android/os/BatteryManager");

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, batteryClass);
            assertBatteryManagerMarker(fromApp);
            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, batteryClass);
            assertBatteryManagerMarker(fromCtx);
            try {
                invokeGetIntProperty(jni, baseVM, useVaList, fromApp, BATTERY_PROPERTY_CAPACITY);
                fail("expected UOE for getIntProperty without android.battery");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("BatteryManager->getIntProperty"));
            }
            try {
                invokeIsCharging(jni, baseVM, useVaList, fromCtx);
                fail("expected UOE for isCharging without android.battery");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("BatteryManager->isCharging"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_battery sidecar on typed lookup: " + e.api,
                        "android_battery".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertBatteryManagerMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals("android/os/BatteryManager", manager.getObjectType().getClassName());
        assertEquals(SystemService.BATTERY_SERVICE, manager.getValue());
    }

    private static void assertNoAndroidBatterySince(CapturingSink sink, int fromIndex) {
        for (int i = fromIndex; i < sink.events.size(); i++) {
            assertFalse("unexpected android_battery sidecar on typed lookup: " + sink.events.get(i).api,
                    "android_battery".equals(sink.events.get(i).kind));
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

    private static int invokeGetIntProperty(AbstractJni jni, BaseVM vm, boolean useVaList,
                                            DvmObject<?> target, int propertyId) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getIntProperty", "(I)I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature, new TestVaList(vm, method, propertyId));
        }
        return jni.callIntMethod(vm, target, signature, new TestVarArg(vm, method, propertyId));
    }

    private static long invokeGetLongProperty(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmObject<?> target, int propertyId) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getLongProperty", "(I)J", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callLongMethodV(vm, target, signature, new TestLongVaList(vm, method, propertyId));
        }
        return jni.callLongMethod(vm, target, signature, new TestLongVarArg(vm, method, propertyId));
    }

    private static boolean invokeIsCharging(AbstractJni jni, BaseVM vm, boolean useVaList,
                                            DvmObject<?> target) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "isCharging", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static long invokeComputeChargeTimeRemaining(AbstractJni jni, BaseVM vm,
                                                         boolean useVaList, DvmObject<?> target) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "computeChargeTimeRemaining", "()J", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callLongMethodV(vm, target, signature, new TestNoArgLongVaList(vm, method));
        }
        return jni.callLongMethod(vm, target, signature, new TestNoArgLongVarArg(vm, method));
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
        TestVarArg(BaseVM vm, DvmMethod method, int int0) {
            super(vm, method);
            args.add(int0);
        }
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method, int int0) {
            super(vm, method);
            args.add(int0);
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

    /** VarArg for long-return methods such as getLongProperty(I)J (arg0 is still int propertyId). */
    private static final class TestLongVarArg extends VarArg {
        TestLongVarArg(BaseVM vm, DvmMethod method, int int0) {
            super(vm, method);
            args.add(int0);
        }
    }

    /** VaList for long-return methods such as getLongProperty(I)J (arg0 is still int propertyId). */
    private static final class TestLongVaList extends VaList {
        TestLongVaList(BaseVM vm, DvmMethod method, int int0) {
            super(vm, method);
            args.add(int0);
        }
    }

    /** No-arg VarArg for long-return methods such as computeChargeTimeRemaining()J. */
    private static final class TestNoArgLongVarArg extends VarArg {
        TestNoArgLongVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }

    /** No-arg VaList for long-return methods such as computeChargeTimeRemaining()J. */
    private static final class TestNoArgLongVaList extends VaList {
        TestNoArgLongVaList(BaseVM vm, DvmMethod method) {
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
