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

/**
 * Coverage for {@code android.accessibility} + {@code AccessibilityManager.isEnabled()Z} /
 * {@code isTouchExplorationEnabled()Z} / {@code isHighContrastTextEnabled()Z}
 * (SystemService accessibility marker only; VarArg + VaList).
 */
public class AndroidAccessibilityJniTest {

    private static final String ACCESSIBILITY_MANAGER_CLASS =
            "android/view/accessibility/AccessibilityManager";

    private static final String ACC_TRUE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"accessibility\":{\"enabled\":true}"
            + "}"
            + "}";

    private static final String ACC_FALSE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"accessibility\":{\"enabled\":false}"
            + "}"
            + "}";

    private static final String ACC_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"accessibility\":{}"
            + "}"
            + "}";

    private static final String TOUCH_TRUE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"accessibility\":{\"touchExplorationEnabled\":true}"
            + "}"
            + "}";

    private static final String TOUCH_FALSE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"accessibility\":{\"touchExplorationEnabled\":false}"
            + "}"
            + "}";

    private static final String ENABLED_TRUE_TOUCH_FALSE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"accessibility\":{\"enabled\":true,\"touchExplorationEnabled\":false}"
            + "}"
            + "}";

    private static final String HIGH_TRUE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"accessibility\":{\"highContrastTextEnabled\":true}"
            + "}"
            + "}";

    private static final String HIGH_FALSE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"accessibility\":{\"highContrastTextEnabled\":false}"
            + "}"
            + "}";

    private static final String THREE_INDEP_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"accessibility\":{"
            + "\"enabled\":true,"
            + "\"touchExplorationEnabled\":false,"
            + "\"highContrastTextEnabled\":true"
            + "}"
            + "}"
            + "}";

    private static final String NO_ACC_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testIsEnabledTrueVarArg32() throws Exception {
        runConfiguredIsEnabled(false, false, ACC_TRUE_JSON, true);
    }

    @Test
    public void testIsEnabledTrueVaList64() throws Exception {
        runConfiguredIsEnabled(true, true, ACC_TRUE_JSON, true);
    }

    @Test
    public void testIsEnabledFalseVarArg32() throws Exception {
        runConfiguredIsEnabled(false, false, ACC_FALSE_JSON, false);
    }

    @Test
    public void testIsEnabledFalseVaList64() throws Exception {
        runConfiguredIsEnabled(true, true, ACC_FALSE_JSON, false);
    }

    @Test
    public void testIsEnabledDefaultFalseVarArg32() throws Exception {
        runConfiguredIsEnabled(false, false, ACC_EMPTY_JSON, false);
    }

    @Test
    public void testIsEnabledDefaultFalseVaList64() throws Exception {
        runConfiguredIsEnabled(true, true, ACC_EMPTY_JSON, false);
    }

    @Test
    public void testIsEnabledAbsentVarArg32() throws Exception {
        runAbsentIsEnabled(false, false);
    }

    @Test
    public void testIsEnabledAbsentVaList64() throws Exception {
        runAbsentIsEnabled(true, true);
    }

    @Test
    public void testIsTouchExplorationEnabledTrueVarArg32() throws Exception {
        runConfiguredIsTouchExplorationEnabled(false, false, TOUCH_TRUE_JSON, true);
    }

    @Test
    public void testIsTouchExplorationEnabledTrueVaList64() throws Exception {
        runConfiguredIsTouchExplorationEnabled(true, true, TOUCH_TRUE_JSON, true);
    }

    @Test
    public void testIsTouchExplorationEnabledFalseVarArg32() throws Exception {
        runConfiguredIsTouchExplorationEnabled(false, false, TOUCH_FALSE_JSON, false);
    }

    @Test
    public void testIsTouchExplorationEnabledFalseVaList64() throws Exception {
        runConfiguredIsTouchExplorationEnabled(true, true, TOUCH_FALSE_JSON, false);
    }

    @Test
    public void testIsTouchExplorationEnabledDefaultFalseVarArg32() throws Exception {
        runConfiguredIsTouchExplorationEnabled(false, false, ACC_EMPTY_JSON, false);
    }

    @Test
    public void testIsTouchExplorationEnabledDefaultFalseVaList64() throws Exception {
        runConfiguredIsTouchExplorationEnabled(true, true, ACC_EMPTY_JSON, false);
    }

    @Test
    public void testIsTouchExplorationEnabledIndependentOfEnabledVarArg32() throws Exception {
        runIndependenceEnabledVsTouch(false, false);
    }

    @Test
    public void testIsTouchExplorationEnabledIndependentOfEnabledVaList64() throws Exception {
        runIndependenceEnabledVsTouch(true, true);
    }

    @Test
    public void testIsTouchExplorationEnabledAbsentVarArg32() throws Exception {
        runAbsentIsTouchExplorationEnabled(false, false);
    }

    @Test
    public void testIsTouchExplorationEnabledAbsentVaList64() throws Exception {
        runAbsentIsTouchExplorationEnabled(true, true);
    }

    @Test
    public void testIsHighContrastTextEnabledTrueVarArg32() throws Exception {
        runConfiguredIsHighContrastTextEnabled(false, false, HIGH_TRUE_JSON, true);
    }

    @Test
    public void testIsHighContrastTextEnabledTrueVaList64() throws Exception {
        runConfiguredIsHighContrastTextEnabled(true, true, HIGH_TRUE_JSON, true);
    }

    @Test
    public void testIsHighContrastTextEnabledFalseVarArg32() throws Exception {
        runConfiguredIsHighContrastTextEnabled(false, false, HIGH_FALSE_JSON, false);
    }

    @Test
    public void testIsHighContrastTextEnabledFalseVaList64() throws Exception {
        runConfiguredIsHighContrastTextEnabled(true, true, HIGH_FALSE_JSON, false);
    }

    @Test
    public void testIsHighContrastTextEnabledDefaultFalseVarArg32() throws Exception {
        runConfiguredIsHighContrastTextEnabled(false, false, ACC_EMPTY_JSON, false);
    }

    @Test
    public void testIsHighContrastTextEnabledDefaultFalseVaList64() throws Exception {
        runConfiguredIsHighContrastTextEnabled(true, true, ACC_EMPTY_JSON, false);
    }

    @Test
    public void testIsHighContrastTextEnabledIndependentVarArg32() throws Exception {
        runIndependenceThreeFields(false, false);
    }

    @Test
    public void testIsHighContrastTextEnabledIndependentVaList64() throws Exception {
        runIndependenceThreeFields(true, true);
    }

    @Test
    public void testIsHighContrastTextEnabledAbsentVarArg32() throws Exception {
        runAbsentIsHighContrastTextEnabled(false, false);
    }

    @Test
    public void testIsHighContrastTextEnabledAbsentVaList64() throws Exception {
        runAbsentIsHighContrastTextEnabled(true, true);
    }

    @Test
    public void testPlainAndOtherSystemServiceIsolationVarArg32() throws Exception {
        runPlainAndOtherIsolation(false, false);
    }

    @Test
    public void testPlainAndOtherSystemServiceIsolationVaList64() throws Exception {
        runPlainAndOtherIsolation(true, true);
    }

    @Test
    public void testAccessibilityServiceNameAndValueVarArg32() throws Exception {
        runServiceNameAndValue(false, false);
    }

    @Test
    public void testAccessibilityServiceNameAndValueVaList64() throws Exception {
        runServiceNameAndValue(true, true);
    }

    @Test
    public void testEnabledServiceListStillEmptyWhenConfigured() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ACC_TRUE_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveAccessibilitySystemService(jni, baseVM, true, vm);
            assertTrue(invokeIsEnabled(jni, baseVM, true, manager));

            // getEnabledAccessibilityServiceList remains fixed empty list (unchanged)
            DvmClass dvmClass = vm.resolveClass(ACCESSIBILITY_MANAGER_CLASS);
            DvmMethod method = new DvmMethod(dvmClass, "getEnabledAccessibilityServiceList",
                    "(I)Ljava/util/List;", false);
            String signature = method.getSignature();
            DvmObject<?> list = jni.callObjectMethodV(baseVM, manager, signature,
                    new TestIntVaList(baseVM, method, 0));
            assertTrue(list instanceof ArrayListObject);
            assertTrue(((ArrayListObject) list).getValue().isEmpty());

            // only isEnabled sidecar; no service-list event
            assertEquals(1, countEvents(sink.events, "android_accessibility",
                    "AccessibilityManager.isEnabled"));
            for (CapturedEvent e : sink.events) {
                if ("android_accessibility".equals(e.kind)) {
                    assertEquals("AccessibilityManager.isEnabled", e.api);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredIsEnabled(boolean is64Bit, boolean useVaList,
                                               String json, boolean expected) throws Exception {
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

            DvmObject<?> manager = resolveAccessibilitySystemService(jni, baseVM, useVaList, vm);
            assertEquals(expected, invokeIsEnabled(jni, baseVM, useVaList, manager));

            CapturedEvent ev = findLastEvent(sink.events, "android_accessibility",
                    "AccessibilityManager.isEnabled");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=enabled,result=" + expected, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertTrue(ev.note.contains("无障碍"));
            assertEquals(1, countEvents(sink.events, "android_accessibility",
                    "AccessibilityManager.isEnabled"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentIsEnabled(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_ACC_JSON);
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

            // getSystemService still yields accessibility marker without android.accessibility
            DvmObject<?> manager = resolveAccessibilitySystemService(jni, baseVM, useVaList, vm);
            assertTrue(manager instanceof SystemService);
            try {
                invokeIsEnabled(jni, baseVM, useVaList, manager);
                fail("expected UnsupportedOperationException without accessibility config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isEnabled"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_accessibility event when config absent: " + e.api,
                        "android_accessibility".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runPlainAndOtherIsolation(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ACC_TRUE_JSON);
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

            // plain AccessibilityManager (not SystemService marker) → UOE, no event
            DvmObject<?> plain = vm.resolveClass(ACCESSIBILITY_MANAGER_CLASS).newObject(null);
            try {
                invokeIsEnabled(jni, baseVM, useVaList, plain);
                fail("expected UOE for isEnabled on plain AccessibilityManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isEnabled"));
            }

            // other SystemService (wifi) must not hit accessibility path
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> wifi = invokeGetSystemService(jni, baseVM, useVaList, app, "wifi");
            assertTrue(wifi instanceof SystemService);
            try {
                invokeIsEnabled(jni, baseVM, useVaList, wifi);
                fail("expected UOE for isEnabled on non-accessibility SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isEnabled"));
            }

            // wrong signature on accessibility marker → UOE, no event
            DvmObject<?> accessibility = resolveAccessibilitySystemService(jni, baseVM, useVaList, vm);
            try {
                DvmClass dvmClass = accessibility.getObjectType();
                DvmMethod method = new DvmMethod(dvmClass, "isCaptioningEnabled", "()Z", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callBooleanMethodV(baseVM, accessibility, signature,
                            new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callBooleanMethod(baseVM, accessibility, signature,
                            new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for isCaptioningEnabled");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isCaptioningEnabled"));
            }

            // plain receiver for isTouchExplorationEnabled → UOE, no event
            try {
                invokeIsTouchExplorationEnabled(jni, baseVM, useVaList, plain);
                fail("expected UOE for isTouchExplorationEnabled on plain AccessibilityManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isTouchExplorationEnabled"));
            }

            // plain receiver for isHighContrastTextEnabled → UOE, no event
            try {
                invokeIsHighContrastTextEnabled(jni, baseVM, useVaList, plain);
                fail("expected UOE for isHighContrastTextEnabled on plain AccessibilityManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isHighContrastTextEnabled"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_accessibility event on isolation paths: " + e.api,
                        "android_accessibility".equals(e.kind));
            }

            // control: real accessibility SystemService still works after isolation checks
            assertTrue(invokeIsEnabled(jni, baseVM, useVaList, accessibility));
            assertEquals(1, countEvents(sink.events, "android_accessibility",
                    "AccessibilityManager.isEnabled"));
            // default touchExplorationEnabled=false under node with only enabled=true
            assertFalse(invokeIsTouchExplorationEnabled(jni, baseVM, useVaList, accessibility));
            assertEquals(1, countEvents(sink.events, "android_accessibility",
                    "AccessibilityManager.isTouchExplorationEnabled"));
            // default highContrastTextEnabled=false under node with only enabled=true
            assertFalse(invokeIsHighContrastTextEnabled(jni, baseVM, useVaList, accessibility));
            assertEquals(1, countEvents(sink.events, "android_accessibility",
                    "AccessibilityManager.isHighContrastTextEnabled"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredIsTouchExplorationEnabled(boolean is64Bit, boolean useVaList,
                                                               String json, boolean expected)
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

            DvmObject<?> manager = resolveAccessibilitySystemService(jni, baseVM, useVaList, vm);
            assertEquals(expected, invokeIsTouchExplorationEnabled(jni, baseVM, useVaList, manager));

            CapturedEvent ev = findLastEvent(sink.events, "android_accessibility",
                    "AccessibilityManager.isTouchExplorationEnabled");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=touchExplorationEnabled,result=" + expected, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertTrue(ev.note.contains("触摸探索"));
            assertEquals(1, countEvents(sink.events, "android_accessibility",
                    "AccessibilityManager.isTouchExplorationEnabled"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIndependenceEnabledVsTouch(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ENABLED_TRUE_TOUCH_FALSE_JSON);
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

            DvmObject<?> manager = resolveAccessibilitySystemService(jni, baseVM, useVaList, vm);
            assertTrue(invokeIsEnabled(jni, baseVM, useVaList, manager));
            assertFalse(invokeIsTouchExplorationEnabled(jni, baseVM, useVaList, manager));

            CapturedEvent e0 = findLastEvent(sink.events, "android_accessibility",
                    "AccessibilityManager.isEnabled");
            assertNotNull(e0);
            assertEquals("field=enabled,result=true", String.valueOf(e0.value));
            CapturedEvent e1 = findLastEvent(sink.events, "android_accessibility",
                    "AccessibilityManager.isTouchExplorationEnabled");
            assertNotNull(e1);
            assertEquals("field=touchExplorationEnabled,result=false", String.valueOf(e1.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentIsTouchExplorationEnabled(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_ACC_JSON);
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

            DvmObject<?> manager = resolveAccessibilitySystemService(jni, baseVM, useVaList, vm);
            assertTrue(manager instanceof SystemService);
            try {
                invokeIsTouchExplorationEnabled(jni, baseVM, useVaList, manager);
                fail("expected UnsupportedOperationException without accessibility config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isTouchExplorationEnabled"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_accessibility event when config absent: " + e.api,
                        "android_accessibility".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredIsHighContrastTextEnabled(boolean is64Bit, boolean useVaList,
                                                               String json, boolean expected)
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

            DvmObject<?> manager = resolveAccessibilitySystemService(jni, baseVM, useVaList, vm);
            assertEquals(expected, invokeIsHighContrastTextEnabled(jni, baseVM, useVaList, manager));

            CapturedEvent ev = findLastEvent(sink.events, "android_accessibility",
                    "AccessibilityManager.isHighContrastTextEnabled");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=highContrastTextEnabled,result=" + expected,
                    String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertTrue(ev.note.contains("高对比度"));
            assertEquals(1, countEvents(sink.events, "android_accessibility",
                    "AccessibilityManager.isHighContrastTextEnabled"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIndependenceThreeFields(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(THREE_INDEP_JSON);
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

            DvmObject<?> manager = resolveAccessibilitySystemService(jni, baseVM, useVaList, vm);
            assertTrue(invokeIsEnabled(jni, baseVM, useVaList, manager));
            assertFalse(invokeIsTouchExplorationEnabled(jni, baseVM, useVaList, manager));
            assertTrue(invokeIsHighContrastTextEnabled(jni, baseVM, useVaList, manager));

            CapturedEvent e0 = findLastEvent(sink.events, "android_accessibility",
                    "AccessibilityManager.isEnabled");
            assertNotNull(e0);
            assertEquals("field=enabled,result=true", String.valueOf(e0.value));
            CapturedEvent e1 = findLastEvent(sink.events, "android_accessibility",
                    "AccessibilityManager.isTouchExplorationEnabled");
            assertNotNull(e1);
            assertEquals("field=touchExplorationEnabled,result=false", String.valueOf(e1.value));
            CapturedEvent e2 = findLastEvent(sink.events, "android_accessibility",
                    "AccessibilityManager.isHighContrastTextEnabled");
            assertNotNull(e2);
            assertEquals("field=highContrastTextEnabled,result=true", String.valueOf(e2.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentIsHighContrastTextEnabled(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_ACC_JSON);
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

            DvmObject<?> manager = resolveAccessibilitySystemService(jni, baseVM, useVaList, vm);
            assertTrue(manager instanceof SystemService);
            try {
                invokeIsHighContrastTextEnabled(jni, baseVM, useVaList, manager);
                fail("expected UnsupportedOperationException without accessibility config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isHighContrastTextEnabled"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_accessibility event when config absent: " + e.api,
                        "android_accessibility".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runServiceNameAndValue(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ACC_TRUE_JSON);
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

            DvmClass contextClass = vm.resolveClass("android/content/Context");
            DvmObject<?> serviceName = jni.getStaticObjectField(baseVM, contextClass,
                    "android/content/Context->ACCESSIBILITY_SERVICE:Ljava/lang/String;");
            assertTrue(serviceName instanceof StringObject);
            assertEquals(SystemService.ACCESSIBILITY_SERVICE, ((StringObject) serviceName).getValue());
            assertEquals("accessibility", ((StringObject) serviceName).getValue());

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> manager = invokeGetSystemService(jni, baseVM, useVaList, app,
                    ((StringObject) serviceName).getValue());
            assertNotNull(manager);
            assertTrue(manager instanceof SystemService);
            assertEquals(ACCESSIBILITY_MANAGER_CLASS, manager.getObjectType().getClassName());
            assertEquals(SystemService.ACCESSIBILITY_SERVICE, manager.getValue());

            assertTrue(invokeIsEnabled(jni, baseVM, useVaList, manager));
            CapturedEvent ev = findLastEvent(sink.events, "android_accessibility",
                    "AccessibilityManager.isEnabled");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=enabled,result=true", String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static DvmObject<?> resolveAccessibilitySystemService(AbstractJni jni, BaseVM vm,
                                                                  boolean useVaList, VM dalvikVm) {
        DvmObject<?> app = dalvikVm.resolveClass("android/app/Application").newObject(null);
        return invokeGetSystemService(jni, vm, useVaList, app, "accessibility");
    }

    private static DvmObject<?> invokeGetSystemService(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> app, String serviceName) {
        int nameHash = vm.addLocalObject(new StringObject(vm, serviceName));
        DvmClass dvmClass = app.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/String;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, app, signature, new TestObjectVaList(vm, method, nameHash));
        }
        return jni.callObjectMethod(vm, app, signature, new TestObjectVarArg(vm, method, nameHash));
    }

    private static boolean invokeIsEnabled(AbstractJni jni, BaseVM vm, boolean useVaList,
                                           DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(ACCESSIBILITY_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isEnabled", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeIsTouchExplorationEnabled(AbstractJni jni, BaseVM vm,
                                                           boolean useVaList, DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(ACCESSIBILITY_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isTouchExplorationEnabled", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeIsHighContrastTextEnabled(AbstractJni jni, BaseVM vm,
                                                           boolean useVaList, DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(ACCESSIBILITY_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isHighContrastTextEnabled", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
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

    private static final class TestIntVaList extends VaList {
        TestIntVaList(BaseVM vm, DvmMethod method, int intArg) {
            super(vm, method);
            args.add(intArg);
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
        public void emitEnvironmentEvent(String kind, String api, Object value, String source, String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}
