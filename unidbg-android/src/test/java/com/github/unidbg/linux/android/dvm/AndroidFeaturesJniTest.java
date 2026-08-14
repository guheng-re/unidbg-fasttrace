package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.dvm.array.ArrayObject;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AndroidFeaturesJniTest {

    private static final String FEATURES_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"features\":["
            + "{\"name\":\"android.hardware.camera\",\"version\":2},"
            + "{\"name\":\"android.hardware.wifi\"},"
            + "{\"name\":\"com.traceai.feature.CUSTOM\",\"version\":0}"
            + "]}"
            + "}";

    @Test
    public void testFeaturesJniVarArg32() throws Exception {
        runFeaturesJni(false, false);
    }

    @Test
    public void testFeaturesJniVaList64() throws Exception {
        runFeaturesJni(true, true);
    }

    @Test
    public void testFeatureInfoCrossVmRejectedVarArg32() throws Exception {
        runFeatureInfoCrossVmRejected(false, false);
    }

    @Test
    public void testFeatureInfoCrossVmRejectedVaList64() throws Exception {
        runFeatureInfoCrossVmRejected(true, true);
    }

    /**
     * FeatureInfo marker from VM A must not authorize name / version on VM B.
     * VM A continues to read camera + version=2; VM B gets UOE and an empty sidecar.
     */
    private static void runFeatureInfoCrossVmRejected(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FEATURES_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
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

            DvmObject<?> pmA = vmA.resolveClass("android/content/pm/PackageManager").newObject(null);
            DvmObject<?> featuresObj = invokeGetSystemAvailableFeatures(jniA, baseA, useVaList, pmA);
            assertTrue(featuresObj instanceof ArrayObject);
            DvmObject<?>[] featureItems = ((ArrayObject) featuresObj).getValue();
            assertNotNull(featureItems);
            assertTrue(featureItems.length > 0);
            DvmObject<?> markerA = featureItems[0];

            try {
                getFeatureName(jniB, baseB, markerA);
                fail("expected UnsupportedOperationException for cross-VM FeatureInfo.name");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("FeatureInfo->name"));
            }
            try {
                getFeatureVersion(jniB, baseB, markerA);
                fail("expected UnsupportedOperationException for cross-VM FeatureInfo.version");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("FeatureInfo->version"));
            }
            assertTrue(sinkB.events.isEmpty());

            assertEquals("android.hardware.camera", getFeatureName(jniA, baseA, markerA));
            assertEquals(2, getFeatureVersion(jniA, baseA, markerA));
        } finally {
            if (emulatorA != null) {
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static void runFeaturesJni(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FEATURES_JSON);
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

            DvmObject<?> pm = vm.resolveClass("android/content/pm/PackageManager").newObject(null);

            // present / absent (no-version)
            assertTrue(invokeHasSystemFeature(jni, baseVM, useVaList, pm,
                    "android.hardware.camera"));
            assertTrue(invokeHasSystemFeature(jni, baseVM, useVaList, pm,
                    "android.hardware.wifi"));
            assertTrue(invokeHasSystemFeature(jni, baseVM, useVaList, pm,
                    "com.traceai.feature.CUSTOM"));
            assertFalse(invokeHasSystemFeature(jni, baseVM, useVaList, pm,
                    "android.hardware.nfc"));

            // version overload: equal / lower requested / higher requested
            assertTrue(invokeHasSystemFeatureVersion(jni, baseVM, useVaList, pm,
                    "android.hardware.camera", 2));
            assertTrue(invokeHasSystemFeatureVersion(jni, baseVM, useVaList, pm,
                    "android.hardware.camera", 1));
            assertTrue(invokeHasSystemFeatureVersion(jni, baseVM, useVaList, pm,
                    "android.hardware.camera", 0));
            assertFalse(invokeHasSystemFeatureVersion(jni, baseVM, useVaList, pm,
                    "android.hardware.camera", 3));

            // missing version in config treated as 0
            assertTrue(invokeHasSystemFeatureVersion(jni, baseVM, useVaList, pm,
                    "android.hardware.wifi", 0));
            assertFalse(invokeHasSystemFeatureVersion(jni, baseVM, useVaList, pm,
                    "android.hardware.wifi", 1));
            // configured version 0
            assertTrue(invokeHasSystemFeatureVersion(jni, baseVM, useVaList, pm,
                    "com.traceai.feature.CUSTOM", 0));
            assertFalse(invokeHasSystemFeatureVersion(jni, baseVM, useVaList, pm,
                    "com.traceai.feature.CUSTOM", 1));
            // negative requested: 0 >= -1 when feature exists
            assertTrue(invokeHasSystemFeatureVersion(jni, baseVM, useVaList, pm,
                    "android.hardware.wifi", -1));
            assertTrue(invokeHasSystemFeatureVersion(jni, baseVM, useVaList, pm,
                    "android.hardware.camera", -5));
            // absent feature stays false for version overload
            assertFalse(invokeHasSystemFeatureVersion(jni, baseVM, useVaList, pm,
                    "android.hardware.nfc", 0));
            assertFalse(invokeHasSystemFeatureVersion(jni, baseVM, useVaList, pm,
                    "android.hardware.nfc", -1));

            // ---- getSystemAvailableFeatures array + FeatureInfo fields ----
            DvmObject<?> featuresObj = invokeGetSystemAvailableFeatures(jni, baseVM, useVaList, pm);
            assertTrue(featuresObj instanceof ArrayObject);
            ArrayObject featuresArr = (ArrayObject) featuresObj;
            assertEquals(3, featuresArr.length());
            DvmObject<?>[] featureItems = featuresArr.getValue();
            assertNotNull(featureItems);
            assertEquals(3, featureItems.length);

            assertEquals("android/content/pm/FeatureInfo",
                    featureItems[0].getObjectType().getClassName());
            assertEquals("android.hardware.camera", getFeatureName(jni, baseVM, featureItems[0]));
            assertEquals(2, getFeatureVersion(jni, baseVM, featureItems[0]));

            assertEquals("android.hardware.wifi", getFeatureName(jni, baseVM, featureItems[1]));
            // omitted version projects as 0
            assertEquals(0, getFeatureVersion(jni, baseVM, featureItems[1]));

            assertEquals("com.traceai.feature.CUSTOM", getFeatureName(jni, baseVM, featureItems[2]));
            assertEquals(0, getFeatureVersion(jni, baseVM, featureItems[2]));

            // unrelated FeatureInfo (no marker) stays UOE / no json-config path
            DvmObject<?> unrelated = vm.resolveClass("android/content/pm/FeatureInfo").newObject(null);
            try {
                getFeatureName(jni, baseVM, unrelated);
                fail("expected UnsupportedOperationException for unrelated FeatureInfo.name");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("FeatureInfo->name"));
            }
            try {
                getFeatureVersion(jni, baseVM, unrelated);
                fail("expected UnsupportedOperationException for unrelated FeatureInfo.version");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("FeatureInfo->version"));
            }

            // explicit empty features list → false hasSystemFeature + empty available array
            TraceEnvironmentConfig emptyCfg = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\",\"features\":[]}}");
            AndroidEmulator emptyEmu = null;
            try {
                emptyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(emptyCfg)
                        .build();
                VM emptyVm = emptyEmu.createDalvikVM();
                AbstractJni emptyJni = new AbstractJni() {
                };
                emptyVm.setJni(emptyJni);
                BaseVM emptyBase = (BaseVM) emptyVm;
                DvmObject<?> emptyPm = emptyVm.resolveClass("android/content/pm/PackageManager")
                        .newObject(null);
                assertFalse(invokeHasSystemFeature(emptyJni, emptyBase, useVaList, emptyPm,
                        "android.hardware.camera"));
                assertFalse(invokeHasSystemFeatureVersion(emptyJni, emptyBase, useVaList, emptyPm,
                        "android.hardware.camera", 0));
                DvmObject<?> emptyFeatures = invokeGetSystemAvailableFeatures(emptyJni, emptyBase,
                        useVaList, emptyPm);
                assertTrue(emptyFeatures instanceof ArrayObject);
                assertEquals(0, ((ArrayObject) emptyFeatures).length());
            } finally {
                if (emptyEmu != null) {
                    emptyEmu.close();
                }
            }

            // missing features node → old UOE
            TraceEnvironmentConfig noNodeCfg = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\"}}");
            AndroidEmulator noNodeEmu = null;
            try {
                noNodeEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noNodeCfg)
                        .build();
                VM noNodeVm = noNodeEmu.createDalvikVM();
                AbstractJni noNodeJni = new AbstractJni() {
                };
                noNodeVm.setJni(noNodeJni);
                BaseVM noNodeBase = (BaseVM) noNodeVm;
                DvmObject<?> noNodePm = noNodeVm.resolveClass("android/content/pm/PackageManager")
                        .newObject(null);
                try {
                    invokeHasSystemFeature(noNodeJni, noNodeBase, useVaList, noNodePm,
                            "android.hardware.camera");
                    fail("expected UnsupportedOperationException without features node");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("hasSystemFeature"));
                }
                try {
                    invokeHasSystemFeatureVersion(noNodeJni, noNodeBase, useVaList, noNodePm,
                            "android.hardware.camera", 1);
                    fail("expected UnsupportedOperationException without features for version overload");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("hasSystemFeature"));
                }
                try {
                    invokeGetSystemAvailableFeatures(noNodeJni, noNodeBase, useVaList, noNodePm);
                    fail("expected UnsupportedOperationException without features for getSystemAvailableFeatures");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getSystemAvailableFeatures"));
                }
            } finally {
                if (noNodeEmu != null) {
                    noNodeEmu.close();
                }
            }

            // zero-arg unrelated regression
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
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static DvmObject<?> invokeGetSystemAvailableFeatures(AbstractJni jni, BaseVM vm,
                                                                 boolean useVaList,
                                                                 DvmObject<?> packageManager) {
        DvmClass dvmClass = packageManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemAvailableFeatures",
                "()[Landroid/content/pm/FeatureInfo;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, packageManager, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, packageManager, signature, new TestVarArg(vm, method));
    }

    private static String getFeatureName(AbstractJni jni, BaseVM vm, DvmObject<?> featureInfo) {
        DvmObject<?> result = jni.getObjectField(vm, featureInfo,
                "android/content/pm/FeatureInfo->name:Ljava/lang/String;");
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static int getFeatureVersion(AbstractJni jni, BaseVM vm, DvmObject<?> featureInfo) {
        return jni.getIntField(vm, featureInfo, "android/content/pm/FeatureInfo->version:I");
    }

    private static boolean invokeHasSystemFeature(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  DvmObject<?> packageManager, String featureName) {
        int nameHash = vm.addLocalObject(new StringObject(vm, featureName));
        DvmClass dvmClass = packageManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "hasSystemFeature", "(Ljava/lang/String;)Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, packageManager, signature,
                    new TestVaList(vm, method, nameHash));
        }
        return jni.callBooleanMethod(vm, packageManager, signature,
                new TestVarArg(vm, method, nameHash));
    }

    private static boolean invokeHasSystemFeatureVersion(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                         DvmObject<?> packageManager, String featureName,
                                                         int version) {
        int nameHash = vm.addLocalObject(new StringObject(vm, featureName));
        DvmClass dvmClass = packageManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "hasSystemFeature", "(Ljava/lang/String;I)Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, packageManager, signature,
                    new TestVaList(vm, method, nameHash, version));
        }
        return jni.callBooleanMethod(vm, packageManager, signature,
                new TestVarArg(vm, method, nameHash, version));
    }

    private static final class TestVarArg extends VarArg {
        TestVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int objectHash0, int int1) {
            super(vm, method);
            args.add(objectHash0);
            args.add(int1);
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

        TestVaList(BaseVM vm, DvmMethod method, int objectHash0, int int1) {
            super(vm, method);
            args.add(objectHash0);
            args.add(int1);
        }
    }

    private static final class CapturingSink implements TraceEnvironmentEventSink {
        final List<String> events = new ArrayList<String>();

        @Override
        public void emitEnvironmentEvent(String kind, String api, Object value, String source,
                                         String note) {
            events.add(kind + ":" + api);
        }
    }
}
