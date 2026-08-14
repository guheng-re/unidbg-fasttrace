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

public class AndroidDisplayMetricsJniTest {

    private static final String DISPLAY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"display\":{"
            + "\"widthPixels\":1440,"
            + "\"heightPixels\":3200,"
            + "\"densityDpi\":560,"
            + "\"scaledDensity\":3.5,"
            + "\"xdpi\":513.0,"
            + "\"ydpi\":512.5"
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
            + "\"scaledDensity\":3.5"
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
            + "\"scaledDensity\":1.25"
            + "}"
            + "}"
            + "}";

    private static final String GET_DISPLAY_METRICS =
            "()Landroid/util/DisplayMetrics;";

    @Test
    public void testDisplayMetricsVarArg32() throws Exception {
        runConfiguredDisplayMetrics(false, false);
    }

    @Test
    public void testDisplayMetricsVaList64() throws Exception {
        runConfiguredDisplayMetrics(true, true);
    }

    @Test
    public void testDisplayMetricsAbsentVarArg32() throws Exception {
        runAbsentDisplayMetrics(false, false);
    }

    @Test
    public void testDisplayMetricsAbsentVaList64() throws Exception {
        runAbsentDisplayMetrics(true, true);
    }

    @Test
    public void testDisplayMetricsCrossVmVarArg32() throws Exception {
        runCrossVmRejected(false, false);
    }

    @Test
    public void testDisplayMetricsCrossVmVaList64() throws Exception {
        runCrossVmRejected(true, true);
    }

    private static void runConfiguredDisplayMetrics(boolean is64Bit, boolean useVaList) throws Exception {
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

            DvmObject<?> resources = vm.resolveClass("android/content/res/Resources").newObject(null);
            DvmObject<?> metrics = invokeGetDisplayMetrics(jni, baseVM, useVaList, resources);
            assertNotNull(metrics);
            assertNotNull(metrics.getValue());
            // provenance: private ConfiguredDisplayMetrics marker, not a raw host metrics object
            String valueClass = metrics.getValue().getClass().getName();
            assertTrue("expected ConfiguredDisplayMetrics marker, was " + valueClass,
                    valueClass.contains("ConfiguredDisplayMetrics"));

            assertEquals(1440, jni.getIntField(baseVM, metrics,
                    "android/util/DisplayMetrics->widthPixels:I"));
            assertEquals(3200, jni.getIntField(baseVM, metrics,
                    "android/util/DisplayMetrics->heightPixels:I"));
            assertEquals(560, jni.getIntField(baseVM, metrics,
                    "android/util/DisplayMetrics->densityDpi:I"));
            assertEquals(560 / 160f, jni.getFloatField(baseVM, metrics,
                    "android/util/DisplayMetrics->density:F"), 0f);
            assertEquals(3.5f, jni.getFloatField(baseVM, metrics,
                    "android/util/DisplayMetrics->scaledDensity:F"), 0f);
            assertEquals(513.0f, jni.getFloatField(baseVM, metrics,
                    "android/util/DisplayMetrics->xdpi:F"), 0f);
            assertEquals(512.5f, jni.getFloatField(baseVM, metrics,
                    "android/util/DisplayMetrics->ydpi:F"), 0f);

            // unsupported field on marker
            try {
                jni.getIntField(baseVM, metrics, "android/util/DisplayMetrics->noncompatWidthPixels:I");
                fail("expected UnsupportedOperationException for unsupported int field on marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("noncompatWidthPixels"));
            }
            try {
                jni.getFloatField(baseVM, metrics, "android/util/DisplayMetrics->noncompatDensity:F");
                fail("expected UnsupportedOperationException for unsupported float field on marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("noncompatDensity"));
            }

            // unrelated object isolation: PackageInfo versionCode path still works; DisplayMetrics
            // fields on non-marker still unsupported
            DvmObject<?> unrelated = vm.resolveClass("android/content/pm/PackageInfo").newObject(null);
            try {
                jni.getIntField(baseVM, unrelated, "android/util/DisplayMetrics->widthPixels:I");
                fail("expected UnsupportedOperationException for DisplayMetrics field on unrelated object");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("widthPixels"));
            }
            try {
                jni.getFloatField(baseVM, unrelated, "android/util/DisplayMetrics->density:F");
                fail("expected UnsupportedOperationException for density on unrelated object");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("density"));
            }

            // sidecar shape
            CapturedEvent getMetrics = findLastEvent(sink.events, "android_display",
                    "Resources.getDisplayMetrics");
            assertNotNull(getMetrics);
            assertEquals("json-config", getMetrics.source);
            assertTrue(String.valueOf(getMetrics.value).contains("width=1440"));
            assertTrue(String.valueOf(getMetrics.value).contains("height=3200"));
            assertTrue(String.valueOf(getMetrics.value).contains("densityDpi=560"));
            assertTrue(String.valueOf(getMetrics.value).contains("density=" + (560 / 160f)));

            CapturedEvent widthEv = findLastEvent(sink.events, "android_display",
                    "DisplayMetrics.widthPixels");
            assertNotNull(widthEv);
            assertEquals("json-config", widthEv.source);
            assertTrue(String.valueOf(widthEv.value).contains("field=widthPixels"));
            assertTrue(String.valueOf(widthEv.value).contains("result=1440"));

            CapturedEvent densityEv = findLastEvent(sink.events, "android_display",
                    "DisplayMetrics.density");
            assertNotNull(densityEv);
            assertTrue(String.valueOf(densityEv.value).contains("field=density"));
            assertTrue(String.valueOf(densityEv.value).contains("result=" + (560 / 160f)));

            CapturedEvent scaledEv = findLastEvent(sink.events, "android_display",
                    "DisplayMetrics.scaledDensity");
            assertNotNull(scaledEv);
            assertTrue(String.valueOf(scaledEv.value).contains("result=3.5"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentDisplayMetrics(boolean is64Bit, boolean useVaList) throws Exception {
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

            DvmObject<?> resources = vm.resolveClass("android/content/res/Resources").newObject(null);
            try {
                invokeGetDisplayMetrics(jni, baseVM, useVaList, resources);
                fail("expected UnsupportedOperationException for getDisplayMetrics without android.display");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDisplayMetrics"));
            }

            // fields remain unsupported without a marker object
            DvmObject<?> fakeMetrics = vm.resolveClass("android/util/DisplayMetrics").newObject(null);
            try {
                jni.getIntField(baseVM, fakeMetrics, "android/util/DisplayMetrics->widthPixels:I");
                fail("expected UnsupportedOperationException for widthPixels without config marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("widthPixels"));
            }
            try {
                jni.getFloatField(baseVM, fakeMetrics, "android/util/DisplayMetrics->density:F");
                fail("expected UnsupportedOperationException for density without config marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("density"));
            }
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
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

            DvmObject<?> resourcesA = vmA.resolveClass("android/content/res/Resources").newObject(null);
            DvmObject<?> metricsA = invokeGetDisplayMetrics(jniA, baseA, useVaList, resourcesA);
            assertNotNull(metricsA);
            assertTrue(metricsA.getValue().getClass().getName().contains("ConfiguredDisplayMetrics"));

            int eventsBeforeReject = sinkB.events.size();
            try {
                jniB.getIntField(baseB, metricsA, "android/util/DisplayMetrics->widthPixels:I");
                fail("expected UnsupportedOperationException for cross-VM DisplayMetrics.widthPixels");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("widthPixels"));
            }
            try {
                jniB.getFloatField(baseB, metricsA, "android/util/DisplayMetrics->density:F");
                fail("expected UnsupportedOperationException for cross-VM DisplayMetrics.density");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("density"));
            }
            for (int i = eventsBeforeReject; i < sinkB.events.size(); i++) {
                CapturedEvent e = sinkB.events.get(i);
                assertFalse("unexpected android_display during reject: " + e.api,
                        "android_display".equals(e.kind));
            }
            assertNoADisplayValues(sinkB.events);

            DvmObject<?> resourcesB = vmB.resolveClass("android/content/res/Resources").newObject(null);
            DvmObject<?> metricsB = invokeGetDisplayMetrics(jniB, baseB, useVaList, resourcesB);
            assertNotNull(metricsB);
            assertEquals(720, jniB.getIntField(baseB, metricsB,
                    "android/util/DisplayMetrics->widthPixels:I"));
            assertEquals(240 / 160f, jniB.getFloatField(baseB, metricsB,
                    "android/util/DisplayMetrics->density:F"), 0f);

            assertNoADisplayValues(sinkB.events);
            CapturedEvent widthB = findLastEvent(sinkB.events, "android_display",
                    "DisplayMetrics.widthPixels");
            assertNotNull(widthB);
            assertEquals("field=widthPixels,result=720", String.valueOf(widthB.value));
            CapturedEvent densityB = findLastEvent(sinkB.events, "android_display",
                    "DisplayMetrics.density");
            assertNotNull(densityB);
            assertTrue(String.valueOf(densityB.value).contains("result=" + (240 / 160f)));
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
                            || value.contains("result=1440")
                            || value.contains("result=560")
                            || value.contains("result=" + (560 / 160f))
                            || value.contains("result=3.5"));
        }
    }

    private static DvmObject<?> invokeGetDisplayMetrics(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                        DvmObject<?> resources) {
        DvmClass dvmClass = vm.resolveClass("android/content/res/Resources");
        DvmMethod method = new DvmMethod(dvmClass, "getDisplayMetrics", GET_DISPLAY_METRICS, false);
        String signature = method.getSignature();
        if (useVaList) {
            TestVaList vaList = new TestVaList(vm, method);
            return jni.callObjectMethodV(vm, resources, signature, vaList);
        }
        TestVarArg varArg = new TestVarArg(vm, method);
        return jni.callObjectMethod(vm, resources, signature, varArg);
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
