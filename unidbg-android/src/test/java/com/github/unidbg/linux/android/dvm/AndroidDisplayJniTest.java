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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AndroidDisplayJniTest {

    private static final String DISPLAY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"display\":{"
            + "\"widthPixels\":1440,"
            + "\"heightPixels\":3200,"
            + "\"densityDpi\":560,"
            + "\"scaledDensity\":3.5,"
            + "\"xdpi\":513.0,"
            + "\"ydpi\":512.5,"
            + "\"refreshRate\":90.0,"
            + "\"rotation\":1,"
            + "\"modeId\":2"
            + "}"
            + "}"
            + "}";

    private static final String DISPLAY_CROSS_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"display\":{"
            + "\"widthPixels\":1440,"
            + "\"heightPixels\":3200,"
            + "\"densityDpi\":560,"
            + "\"scaledDensity\":3.5,"
            + "\"xdpi\":513.0,"
            + "\"ydpi\":512.5,"
            + "\"refreshRate\":90.0,"
            + "\"rotation\":1,"
            + "\"modeId\":2"
            + "}"
            + "}"
            + "}";

    private static final String DISPLAY_CROSS_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"display\":{"
            + "\"widthPixels\":720,"
            + "\"heightPixels\":1280,"
            + "\"densityDpi\":240,"
            + "\"scaledDensity\":1.25,"
            + "\"xdpi\":240.0,"
            + "\"ydpi\":240.0,"
            + "\"refreshRate\":60.0,"
            + "\"rotation\":0,"
            + "\"modeId\":1"
            + "}"
            + "}"
            + "}";

    private static final String GET_DEFAULT_DISPLAY = "()Landroid/view/Display;";
    private static final String GET_MODE = "()Landroid/view/Display$Mode;";
    private static final String INT_NO_ARGS = "()I";
    private static final String FLOAT_NO_ARGS = "()F";

    @Test
    public void testDisplayModeVarArg32() throws Exception {
        runConfiguredDisplay(false, false);
    }

    @Test
    public void testDisplayModeVaList64() throws Exception {
        runConfiguredDisplay(true, true);
    }

    @Test
    public void testDisplayModeAbsentVarArg32() throws Exception {
        runAbsentDisplay(false, false);
    }

    @Test
    public void testDisplayModeAbsentVaList64() throws Exception {
        runAbsentDisplay(true, true);
    }

    @Test
    public void testDisplayGetMetricsVarArg32() throws Exception {
        runGetMetrics(false, false);
    }

    @Test
    public void testDisplayGetMetricsVaList64() throws Exception {
        runGetMetrics(true, true);
    }

    @Test
    public void testDisplayCrossVmVarArg32() throws Exception {
        runCrossVmRejected(false, false);
    }

    @Test
    public void testDisplayCrossVmVaList64() throws Exception {
        runCrossVmRejected(true, true);
    }

    @Test
    public void testDisplayModeCrossVmVarArg32() throws Exception {
        runDisplayModeCrossVmRejected(false, false);
    }

    @Test
    public void testDisplayModeCrossVmVaList64() throws Exception {
        runDisplayModeCrossVmRejected(true, true);
    }

    private static void runConfiguredDisplay(boolean is64Bit, boolean useVaListForObjectInt) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(DISPLAY_JSON);
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

            DvmObject<?> windowManager = vm.resolveClass("android/view/WindowManager").newObject(null);
            DvmObject<?> display = invokeObjectMethod(jni, baseVM, useVaListForObjectInt, windowManager,
                    "android/view/WindowManager", "getDefaultDisplay", GET_DEFAULT_DISPLAY);
            assertNotNull(display);
            assertTrue(display.getValue().getClass().getName().contains("ConfiguredDisplay"));

            assertEquals(1, invokeIntMethod(jni, baseVM, useVaListForObjectInt, display,
                    "android/view/Display", "getRotation"));
            // legacy Display.getWidth/getHeight map to widthPixels/heightPixels
            assertEquals(1440, invokeIntMethod(jni, baseVM, useVaListForObjectInt, display,
                    "android/view/Display", "getWidth"));
            assertEquals(3200, invokeIntMethod(jni, baseVM, useVaListForObjectInt, display,
                    "android/view/Display", "getHeight"));
            // float only available via callFloatMethodV
            assertEquals(90.0f, invokeFloatMethodV(jni, baseVM, display,
                    "android/view/Display", "getRefreshRate"), 0f);

            DvmObject<?> mode = invokeObjectMethod(jni, baseVM, useVaListForObjectInt, display,
                    "android/view/Display", "getMode", GET_MODE);
            assertNotNull(mode);
            assertTrue(mode.getValue().getClass().getName().contains("ConfiguredDisplayMode"));

            assertEquals(2, invokeIntMethod(jni, baseVM, useVaListForObjectInt, mode,
                    "android/view/Display$Mode", "getModeId"));
            assertEquals(1440, invokeIntMethod(jni, baseVM, useVaListForObjectInt, mode,
                    "android/view/Display$Mode", "getPhysicalWidth"));
            assertEquals(3200, invokeIntMethod(jni, baseVM, useVaListForObjectInt, mode,
                    "android/view/Display$Mode", "getPhysicalHeight"));
            assertEquals(90.0f, invokeFloatMethodV(jni, baseVM, mode,
                    "android/view/Display$Mode", "getRefreshRate"), 0f);

            // unsupported methods on markers
            try {
                invokeObjectMethod(jni, baseVM, useVaListForObjectInt, display,
                        "android/view/Display", "getName", "()Ljava/lang/String;");
                fail("expected UnsupportedOperationException for unsupported Display method");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null && expected.getMessage().contains("getName"));
            }
            try {
                invokeIntMethod(jni, baseVM, useVaListForObjectInt, mode,
                        "android/view/Display$Mode", "hashCode");
                fail("expected UnsupportedOperationException for unsupported Mode method");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null && expected.getMessage().contains("hashCode"));
            }

            // unrelated object isolation
            DvmObject<?> unrelated = vm.resolveClass("android/view/Display").newObject(null);
            try {
                invokeIntMethod(jni, baseVM, useVaListForObjectInt, unrelated,
                        "android/view/Display", "getRotation");
                fail("expected UnsupportedOperationException for getRotation on unrelated Display");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null && expected.getMessage().contains("getRotation"));
            }
            try {
                invokeIntMethod(jni, baseVM, useVaListForObjectInt, unrelated,
                        "android/view/Display", "getWidth");
                fail("expected UnsupportedOperationException for getWidth on unrelated Display");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null && expected.getMessage().contains("getWidth"));
            }
            try {
                invokeIntMethod(jni, baseVM, useVaListForObjectInt, unrelated,
                        "android/view/Display", "getHeight");
                fail("expected UnsupportedOperationException for getHeight on unrelated Display");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null && expected.getMessage().contains("getHeight"));
            }
            try {
                invokeFloatMethodV(jni, baseVM, unrelated, "android/view/Display", "getRefreshRate");
                fail("expected UnsupportedOperationException for getRefreshRate on unrelated Display");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null && expected.getMessage().contains("getRefreshRate"));
            }

            // sidecar
            CapturedEvent def = findLastEvent(sink.events, "android_display", "WindowManager.getDefaultDisplay");
            assertNotNull(def);
            assertEquals("json-config", def.source);
            assertTrue(String.valueOf(def.value).contains("modeId=2"));
            assertTrue(String.valueOf(def.value).contains("refreshRate=90.0"));
            assertTrue(String.valueOf(def.value).contains("rotation=1"));

            CapturedEvent rot = findLastEvent(sink.events, "android_display", "Display.getRotation");
            assertNotNull(rot);
            assertTrue(String.valueOf(rot.value).contains("result=1"));

            CapturedEvent widthEv = findLastEvent(sink.events, "android_display", "Display.getWidth");
            assertNotNull(widthEv);
            assertEquals("json-config", widthEv.source);
            assertEquals("field=widthPixels,result=1440", String.valueOf(widthEv.value));
            assertNotNull(widthEv.note);

            CapturedEvent heightEv = findLastEvent(sink.events, "android_display", "Display.getHeight");
            assertNotNull(heightEv);
            assertEquals("json-config", heightEv.source);
            assertEquals("field=heightPixels,result=3200", String.valueOf(heightEv.value));
            assertNotNull(heightEv.note);

            CapturedEvent rr = findLastEvent(sink.events, "android_display", "Display.getRefreshRate");
            assertNotNull(rr);
            assertTrue(String.valueOf(rr.value).contains("result=90.0"));

            CapturedEvent modeEv = findLastEvent(sink.events, "android_display", "Display.getMode");
            assertNotNull(modeEv);
            assertTrue(String.valueOf(modeEv.value).contains("modeId=2"));
            assertTrue(String.valueOf(modeEv.value).contains("width=1440"));
            assertTrue(String.valueOf(modeEv.value).contains("height=3200"));

            CapturedEvent modeId = findLastEvent(sink.events, "android_display", "Display.Mode.getModeId");
            assertNotNull(modeId);
            assertTrue(String.valueOf(modeId.value).contains("result=2"));

            CapturedEvent physW = findLastEvent(sink.events, "android_display", "Display.Mode.getPhysicalWidth");
            assertNotNull(physW);
            assertTrue(String.valueOf(physW.value).contains("result=1440"));

            CapturedEvent modeRr = findLastEvent(sink.events, "android_display", "Display.Mode.getRefreshRate");
            assertNotNull(modeRr);
            assertTrue(String.valueOf(modeRr.value).contains("result=90.0"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetMetrics(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(DISPLAY_JSON);
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

            DvmObject<?> windowManager = vm.resolveClass("android/view/WindowManager").newObject(null);
            DvmObject<?> display = invokeObjectMethod(jni, baseVM, useVaList, windowManager,
                    "android/view/WindowManager", "getDefaultDisplay", GET_DEFAULT_DISPLAY);
            assertNotNull(display);
            assertTrue(display.getValue().getClass().getName().contains("ConfiguredDisplay"));

            DvmObject<?> metrics = vm.resolveClass("android/util/DisplayMetrics").newObject(null);
            assertNull(metrics.getValue());
            invokeVoidMethod(jni, baseVM, useVaList, display, "android/view/Display", "getMetrics",
                    "(Landroid/util/DisplayMetrics;)V", metrics);
            assertNotNull(metrics.getValue());
            assertTrue(metrics.getValue().getClass().getName().contains("ConfiguredDisplayMetrics"));
            assertDisplayMetricsFields(jni, baseVM, metrics);

            DvmObject<?> realMetrics = vm.resolveClass("android/util/DisplayMetrics").newObject(null);
            invokeVoidMethod(jni, baseVM, useVaList, display, "android/view/Display", "getRealMetrics",
                    "(Landroid/util/DisplayMetrics;)V", realMetrics);
            assertNotNull(realMetrics.getValue());
            assertTrue(realMetrics.getValue().getClass().getName().contains("ConfiguredDisplayMetrics"));
            // v1 same profile as getMetrics
            assertDisplayMetricsFields(jni, baseVM, realMetrics);

            CapturedEvent getMetricsEv = findLastEvent(sink.events, "android_display", "Display.getMetrics");
            assertNotNull(getMetricsEv);
            assertEquals("json-config", getMetricsEv.source);
            assertEquals("width=1440,height=3200,densityDpi=560", String.valueOf(getMetricsEv.value));
            assertNotNull(getMetricsEv.note);
            assertFalse(getMetricsEv.note.isEmpty());

            CapturedEvent getRealEv = findLastEvent(sink.events, "android_display", "Display.getRealMetrics");
            assertNotNull(getRealEv);
            assertEquals("json-config", getRealEv.source);
            assertEquals("width=1440,height=3200,densityDpi=560", String.valueOf(getRealEv.value));
            assertNotNull(getRealEv.note);
            assertFalse(getRealEv.note.isEmpty());

            // null / wrong-type output on configured display → UOE, no extra event
            int eventsBeforeReject = sink.events.size();
            try {
                invokeVoidMethod(jni, baseVM, useVaList, display, "android/view/Display", "getMetrics",
                        "(Landroid/util/DisplayMetrics;)V", null);
                fail("expected UOE for getMetrics with null output");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMetrics"));
            }
            DvmObject<?> notMetrics = vm.resolveClass("java/lang/Object").newObject(null);
            try {
                invokeVoidMethod(jni, baseVM, useVaList, display, "android/view/Display", "getRealMetrics",
                        "(Landroid/util/DisplayMetrics;)V", notMetrics);
                fail("expected UOE for getRealMetrics with wrong output type");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getRealMetrics"));
            }
            assertEquals(eventsBeforeReject, sink.events.size());

            // non-marker Display → UOE, no event for getMetrics
            int eventsBeforePlain = sink.events.size();
            DvmObject<?> plainDisplay = vm.resolveClass("android/view/Display").newObject(null);
            DvmObject<?> plainOut = vm.resolveClass("android/util/DisplayMetrics").newObject(null);
            try {
                invokeVoidMethod(jni, baseVM, useVaList, plainDisplay, "android/view/Display", "getMetrics",
                        "(Landroid/util/DisplayMetrics;)V", plainOut);
                fail("expected UOE for getMetrics on non-marker Display");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMetrics"));
            }
            assertNull(plainOut.getValue());
            for (int i = eventsBeforePlain; i < sink.events.size(); i++) {
                assertFalse("unexpected android_display event on non-marker getMetrics",
                        "android_display".equals(sink.events.get(i).kind)
                                && String.valueOf(sink.events.get(i).api).contains("getMetrics"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertDisplayMetricsFields(AbstractJni jni, BaseVM vm, DvmObject<?> metrics) {
        assertEquals(1440, jni.getIntField(vm, metrics, "android/util/DisplayMetrics->widthPixels:I"));
        assertEquals(3200, jni.getIntField(vm, metrics, "android/util/DisplayMetrics->heightPixels:I"));
        assertEquals(560, jni.getIntField(vm, metrics, "android/util/DisplayMetrics->densityDpi:I"));
        assertEquals(560 / 160f, jni.getFloatField(vm, metrics, "android/util/DisplayMetrics->density:F"), 0f);
        assertEquals(3.5f, jni.getFloatField(vm, metrics, "android/util/DisplayMetrics->scaledDensity:F"), 0f);
        assertEquals(513.0f, jni.getFloatField(vm, metrics, "android/util/DisplayMetrics->xdpi:F"), 0f);
        assertEquals(512.5f, jni.getFloatField(vm, metrics, "android/util/DisplayMetrics->ydpi:F"), 0f);
    }

    private static void runCrossVmRejected(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(DISPLAY_CROSS_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(DISPLAY_CROSS_B_JSON);
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

            DvmObject<?> windowManagerA = vmA.resolveClass("android/view/WindowManager").newObject(null);
            DvmObject<?> displayA = invokeObjectMethod(jniA, baseA, useVaList, windowManagerA,
                    "android/view/WindowManager", "getDefaultDisplay", GET_DEFAULT_DISPLAY);
            assertNotNull(displayA);
            assertTrue(displayA.getValue().getClass().getName().contains("ConfiguredDisplay"));

            int eventsBeforeReject = sinkB.events.size();
            try {
                invokeIntMethod(jniB, baseB, useVaList, displayA,
                        "android/view/Display", "getWidth");
                fail("expected UnsupportedOperationException for cross-VM Display.getWidth");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getWidth"));
            }
            try {
                invokeFloatMethodV(jniB, baseB, displayA,
                        "android/view/Display", "getRefreshRate");
                fail("expected UnsupportedOperationException for cross-VM Display.getRefreshRate");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getRefreshRate"));
            }
            try {
                invokeObjectMethod(jniB, baseB, useVaList, displayA,
                        "android/view/Display", "getMode", GET_MODE);
                fail("expected UnsupportedOperationException for cross-VM Display.getMode");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMode"));
            }
            DvmObject<?> metricsOut = vmB.resolveClass("android/util/DisplayMetrics").newObject(null);
            assertNull(metricsOut.getValue());
            try {
                invokeVoidMethod(jniB, baseB, useVaList, displayA, "android/view/Display", "getMetrics",
                        "(Landroid/util/DisplayMetrics;)V", metricsOut);
                fail("expected UnsupportedOperationException for cross-VM Display.getMetrics");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMetrics"));
            }
            assertNull(metricsOut.getValue());
            DvmObject<?> realMetricsOut = vmB.resolveClass("android/util/DisplayMetrics").newObject(null);
            assertNull(realMetricsOut.getValue());
            try {
                invokeVoidMethod(jniB, baseB, useVaList, displayA, "android/view/Display", "getRealMetrics",
                        "(Landroid/util/DisplayMetrics;)V", realMetricsOut);
                fail("expected UnsupportedOperationException for cross-VM Display.getRealMetrics");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getRealMetrics"));
            }
            assertNull(realMetricsOut.getValue());
            for (int i = eventsBeforeReject; i < sinkB.events.size(); i++) {
                CapturedEvent e = sinkB.events.get(i);
                assertFalse("unexpected android_display during reject: " + e.api,
                        "android_display".equals(e.kind));
            }
            assertNoADisplayValues(sinkB.events);

            DvmObject<?> windowManagerB = vmB.resolveClass("android/view/WindowManager").newObject(null);
            DvmObject<?> displayB = invokeObjectMethod(jniB, baseB, useVaList, windowManagerB,
                    "android/view/WindowManager", "getDefaultDisplay", GET_DEFAULT_DISPLAY);
            assertNotNull(displayB);
            assertTrue(displayB.getValue().getClass().getName().contains("ConfiguredDisplay"));
            assertEquals(720, invokeIntMethod(jniB, baseB, useVaList, displayB,
                    "android/view/Display", "getWidth"));
            assertEquals(1280, invokeIntMethod(jniB, baseB, useVaList, displayB,
                    "android/view/Display", "getHeight"));
            assertEquals(0, invokeIntMethod(jniB, baseB, useVaList, displayB,
                    "android/view/Display", "getRotation"));
            assertEquals(60.0f, invokeFloatMethodV(jniB, baseB, displayB,
                    "android/view/Display", "getRefreshRate"), 0f);

            assertNoADisplayValues(sinkB.events);
            CapturedEvent widthB = findLastEvent(sinkB.events, "android_display", "Display.getWidth");
            assertNotNull(widthB);
            assertEquals("field=widthPixels,result=720", String.valueOf(widthB.value));
            CapturedEvent rrB = findLastEvent(sinkB.events, "android_display", "Display.getRefreshRate");
            assertNotNull(rrB);
            assertTrue(String.valueOf(rrB.value).contains("result=60.0"));
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

    private static void runDisplayModeCrossVmRejected(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(DISPLAY_CROSS_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(DISPLAY_CROSS_B_JSON);
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

            DvmObject<?> windowManagerA = vmA.resolveClass("android/view/WindowManager").newObject(null);
            DvmObject<?> displayA = invokeObjectMethod(jniA, baseA, useVaList, windowManagerA,
                    "android/view/WindowManager", "getDefaultDisplay", GET_DEFAULT_DISPLAY);
            DvmObject<?> modeA = invokeObjectMethod(jniA, baseA, useVaList, displayA,
                    "android/view/Display", "getMode", GET_MODE);
            assertNotNull(modeA);
            assertTrue(modeA.getValue().getClass().getName().contains("ConfiguredDisplayMode"));

            int eventsBeforeReject = sinkB.events.size();
            try {
                invokeIntMethod(jniB, baseB, useVaList, modeA,
                        "android/view/Display$Mode", "getModeId");
                fail("expected UnsupportedOperationException for cross-VM Display.Mode.getModeId");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getModeId"));
            }
            try {
                invokeFloatMethodV(jniB, baseB, modeA,
                        "android/view/Display$Mode", "getRefreshRate");
                fail("expected UnsupportedOperationException for cross-VM Display.Mode.getRefreshRate");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getRefreshRate"));
            }
            for (int i = eventsBeforeReject; i < sinkB.events.size(); i++) {
                CapturedEvent e = sinkB.events.get(i);
                assertFalse("unexpected android_display during reject: " + e.api,
                        "android_display".equals(e.kind));
            }
            assertNoADisplayValues(sinkB.events);

            DvmObject<?> windowManagerB = vmB.resolveClass("android/view/WindowManager").newObject(null);
            DvmObject<?> displayB = invokeObjectMethod(jniB, baseB, useVaList, windowManagerB,
                    "android/view/WindowManager", "getDefaultDisplay", GET_DEFAULT_DISPLAY);
            DvmObject<?> modeB = invokeObjectMethod(jniB, baseB, useVaList, displayB,
                    "android/view/Display", "getMode", GET_MODE);
            assertNotNull(modeB);
            assertTrue(modeB.getValue().getClass().getName().contains("ConfiguredDisplayMode"));
            assertEquals(1, invokeIntMethod(jniB, baseB, useVaList, modeB,
                    "android/view/Display$Mode", "getModeId"));
            assertEquals(720, invokeIntMethod(jniB, baseB, useVaList, modeB,
                    "android/view/Display$Mode", "getPhysicalWidth"));
            assertEquals(1280, invokeIntMethod(jniB, baseB, useVaList, modeB,
                    "android/view/Display$Mode", "getPhysicalHeight"));
            assertEquals(60.0f, invokeFloatMethodV(jniB, baseB, modeB,
                    "android/view/Display$Mode", "getRefreshRate"), 0f);

            assertNoADisplayValues(sinkB.events);
            CapturedEvent modeIdB = findLastEvent(sinkB.events, "android_display", "Display.Mode.getModeId");
            assertNotNull(modeIdB);
            assertEquals("field=modeId,result=1", String.valueOf(modeIdB.value));
            CapturedEvent rrB = findLastEvent(sinkB.events, "android_display", "Display.Mode.getRefreshRate");
            assertNotNull(rrB);
            assertTrue(String.valueOf(rrB.value).contains("result=60.0"));
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

    private static void assertNoADisplayValues(List<CapturedEvent> events) {
        for (CapturedEvent e : events) {
            String value = String.valueOf(e.value);
            assertFalse("VM B sidecar must not contain VM A display configuration: " + value,
                    value.contains("width=1440")
                            || value.contains("height=3200")
                            || value.contains("densityDpi=560")
                            || value.contains("field=widthPixels,result=1440")
                            || value.contains("field=heightPixels,result=3200")
                            || value.contains("field=rotation,result=1")
                            || value.contains("modeId=2")
                            || value.contains("refreshRate=90.0")
                            || value.contains("result=90.0")
                            || value.contains("result=1440")
                            || value.contains("result=3200"));
        }
    }

    private static void runAbsentDisplay(boolean is64Bit, boolean useVaList) throws Exception {
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

            DvmObject<?> windowManager = vm.resolveClass("android/view/WindowManager").newObject(null);
            if (useVaList) {
                // VaList still returns legacy Display with null value
                DvmObject<?> display = invokeObjectMethod(jni, baseVM, true, windowManager,
                        "android/view/WindowManager", "getDefaultDisplay", GET_DEFAULT_DISPLAY);
                assertNotNull(display);
                assertNull(display.getValue());
                try {
                    invokeIntMethod(jni, baseVM, true, display, "android/view/Display", "getRotation");
                    fail("expected UnsupportedOperationException for getRotation without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getRotation"));
                }
                try {
                    invokeIntMethod(jni, baseVM, true, display, "android/view/Display", "getWidth");
                    fail("expected UnsupportedOperationException for getWidth without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getWidth"));
                }
                try {
                    invokeIntMethod(jni, baseVM, true, display, "android/view/Display", "getHeight");
                    fail("expected UnsupportedOperationException for getHeight without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getHeight"));
                }
                try {
                    invokeFloatMethodV(jni, baseVM, display, "android/view/Display", "getRefreshRate");
                    fail("expected UnsupportedOperationException for getRefreshRate without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getRefreshRate"));
                }
                try {
                    invokeObjectMethod(jni, baseVM, true, display,
                            "android/view/Display", "getMode", GET_MODE);
                    fail("expected UnsupportedOperationException for getMode without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getMode"));
                }
            } else {
                // VarArg getDefaultDisplay remains unsupported when android.display absent
                try {
                    invokeObjectMethod(jni, baseVM, false, windowManager,
                            "android/view/WindowManager", "getDefaultDisplay", GET_DEFAULT_DISPLAY);
                    fail("expected UnsupportedOperationException for VarArg getDefaultDisplay without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDefaultDisplay"));
                }
            }
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static DvmObject<?> invokeObjectMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   DvmObject<?> receiver, String className,
                                                   String methodName, String args) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, receiver, signature, new TestVarArg(vm, method));
    }

    private static void invokeVoidMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                         DvmObject<?> receiver, String className, String methodName,
                                         String args, DvmObject<?> objectArg0) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, false);
        String signature = method.getSignature();
        int objectHash = objectArg0 == null ? 0 : vm.addLocalObject(objectArg0);
        if (useVaList) {
            jni.callVoidMethodV(vm, receiver, signature, new TestVaList(vm, method, objectHash));
        } else {
            jni.callVoidMethod(vm, receiver, signature, new TestVarArg(vm, method, objectHash));
        }
    }

    private static int invokeIntMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                       DvmObject<?> receiver, String className, String methodName) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, INT_NO_ARGS, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, receiver, signature, new TestVaList(vm, method));
        }
        return jni.callIntMethod(vm, receiver, signature, new TestVarArg(vm, method));
    }

    private static float invokeFloatMethodV(AbstractJni jni, BaseVM vm, DvmObject<?> receiver,
                                            String className, String methodName) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, FLOAT_NO_ARGS, false);
        return jni.callFloatMethodV(vm, receiver, method.getSignature(), new TestVaList(vm, method));
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
