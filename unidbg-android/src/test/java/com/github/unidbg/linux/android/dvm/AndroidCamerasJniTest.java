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

/**
 * Coverage for {@code android.cameras} + static {@code Camera.getNumberOfCameras()I} and
 * {@code Camera.getCameraInfo(ILandroid/hardware/Camera$CameraInfo;)V} plus
 * {@code Camera$CameraInfo} facing/orientation int fields and optional
 * {@code canDisableShutterSound} boolean field (VarArg + VaList).
 */
public class AndroidCamerasJniTest {

    private static final String GET_NUMBER_OF_CAMERAS_SIGNATURE =
            "android/hardware/Camera->getNumberOfCameras()I";
    private static final String GET_CAMERA_INFO_SIGNATURE =
            "android/hardware/Camera->getCameraInfo(ILandroid/hardware/Camera$CameraInfo;)V";
    private static final String CAMERA_INFO_CLASS = "android/hardware/Camera$CameraInfo";
    private static final String FACING_FIELD = "android/hardware/Camera$CameraInfo->facing:I";
    private static final String ORIENTATION_FIELD =
            "android/hardware/Camera$CameraInfo->orientation:I";
    private static final String CAN_DISABLE_SHUTTER_SOUND_FIELD =
            "android/hardware/Camera$CameraInfo->canDisableShutterSound:Z";

    private static final String CAMERAS_TWO_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"cameras\":{\"count\":2}"
            + "}"
            + "}";

    private static final String CAMERAS_ZERO_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"cameras\":{\"count\":0}"
            + "}"
            + "}";

    private static final String CAMERAS_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"cameras\":{}"
            + "}"
            + "}";

    private static final String CAMERAS_INFOS_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"cameras\":{"
            + "\"count\":2,"
            + "\"infos\":["
            + "{\"facing\":0,\"orientation\":90},"
            + "{\"facing\":1,\"orientation\":270}"
            + "]"
            + "}"
            + "}"
            + "}";

    private static final String CAMERAS_SHUTTER_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"cameras\":{"
            + "\"count\":2,"
            + "\"infos\":["
            + "{\"facing\":0,\"orientation\":90,\"canDisableShutterSound\":true},"
            + "{\"facing\":1,\"orientation\":270,\"canDisableShutterSound\":false}"
            + "]"
            + "}"
            + "}"
            + "}";

    private static final String CAMERAS_SHUTTER_MIXED_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"cameras\":{"
            + "\"count\":2,"
            + "\"infos\":["
            + "{\"facing\":0,\"orientation\":90,\"canDisableShutterSound\":true},"
            + "{\"facing\":1,\"orientation\":270}"
            + "]"
            + "}"
            + "}"
            + "}";

    private static final String NO_CAMERAS_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testGetNumberOfCamerasTwoVarArg32() throws Exception {
        runConfiguredCount(false, false, CAMERAS_TWO_JSON, 2);
    }

    @Test
    public void testGetNumberOfCamerasTwoVaList64() throws Exception {
        runConfiguredCount(true, true, CAMERAS_TWO_JSON, 2);
    }

    @Test
    public void testGetNumberOfCamerasZeroVarArg32() throws Exception {
        runConfiguredCount(false, false, CAMERAS_ZERO_JSON, 0);
    }

    @Test
    public void testGetNumberOfCamerasZeroVaList64() throws Exception {
        runConfiguredCount(true, true, CAMERAS_ZERO_JSON, 0);
    }

    @Test
    public void testGetNumberOfCamerasEmptyDefaultVarArg32() throws Exception {
        runConfiguredCount(false, false, CAMERAS_EMPTY_JSON, 0);
    }

    @Test
    public void testGetNumberOfCamerasEmptyDefaultVaList64() throws Exception {
        runConfiguredCount(true, true, CAMERAS_EMPTY_JSON, 0);
    }

    @Test
    public void testGetNumberOfCamerasAbsentVarArg32() throws Exception {
        runAbsentCount(false, false);
    }

    @Test
    public void testGetNumberOfCamerasAbsentVaList64() throws Exception {
        runAbsentCount(true, true);
    }

    @Test
    public void testGetCameraInfoAndFieldsVarArg32() throws Exception {
        runGetCameraInfoSuccess(false, false);
    }

    @Test
    public void testGetCameraInfoAndFieldsVaList64() throws Exception {
        runGetCameraInfoSuccess(true, true);
    }

    @Test
    public void testGetCameraInfoFreshOutputsVarArg32() throws Exception {
        runGetCameraInfoFreshOutputs(false, false);
    }

    @Test
    public void testGetCameraInfoFreshOutputsVaList64() throws Exception {
        runGetCameraInfoFreshOutputs(true, true);
    }

    @Test
    public void testGetCameraInfoBadIndexAndOutputVarArg32() throws Exception {
        runGetCameraInfoBadIndexAndOutput(false, false);
    }

    @Test
    public void testGetCameraInfoBadIndexAndOutputVaList64() throws Exception {
        runGetCameraInfoBadIndexAndOutput(true, true);
    }

    @Test
    public void testGetCameraInfoMissingInfosVarArg32() throws Exception {
        runGetCameraInfoMissingInfos(false, false);
    }

    @Test
    public void testGetCameraInfoMissingInfosVaList64() throws Exception {
        runGetCameraInfoMissingInfos(true, true);
    }

    @Test
    public void testGetCameraInfoAbsentCamerasVarArg32() throws Exception {
        runGetCameraInfoAbsentCameras(false, false);
    }

    @Test
    public void testGetCameraInfoAbsentCamerasVaList64() throws Exception {
        runGetCameraInfoAbsentCameras(true, true);
    }

    @Test
    public void testCameraInfoProvenanceAndStaleVarArg32() throws Exception {
        runCameraInfoProvenanceAndStale(false, false);
    }

    @Test
    public void testCameraInfoProvenanceAndStaleVaList64() throws Exception {
        runCameraInfoProvenanceAndStale(true, true);
    }

    @Test
    public void testCanDisableShutterSoundTrueFalseVarArg32() throws Exception {
        runCanDisableShutterSoundTrueFalse(false, false);
    }

    @Test
    public void testCanDisableShutterSoundTrueFalseVaList64() throws Exception {
        runCanDisableShutterSoundTrueFalse(true, true);
    }

    @Test
    public void testCanDisableShutterSoundAbsentIsolationVarArg32() throws Exception {
        runCanDisableShutterSoundAbsentIsolation(false, false);
    }

    @Test
    public void testCanDisableShutterSoundAbsentIsolationVaList64() throws Exception {
        runCanDisableShutterSoundAbsentIsolation(true, true);
    }

    private static void runConfiguredCount(boolean is64Bit, boolean useVaList, String json,
                                           int expected) throws Exception {
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
            DvmClass cameraClass = vm.resolveClass("android/hardware/Camera");

            assertEquals(expected, invokeGetNumberOfCameras(jni, baseVM, useVaList, cameraClass));

            CapturedEvent ev = findLastEvent(sink.events, "android_camera",
                    "Camera.getNumberOfCameras");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=count,result=" + expected, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_camera",
                    "Camera.getNumberOfCameras"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentCount(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_CAMERAS_JSON);
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
            DvmClass cameraClass = vm.resolveClass("android/hardware/Camera");

            try {
                invokeGetNumberOfCameras(jni, baseVM, useVaList, cameraClass);
                fail("expected UnsupportedOperationException without cameras config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getNumberOfCameras"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_camera event when config absent: " + e.api,
                        "android_camera".equals(e.kind));
            }
            assertEquals(0, countEvents(sink.events, "android_camera",
                    "Camera.getNumberOfCameras"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetCameraInfoSuccess(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CAMERAS_INFOS_JSON);
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
            DvmClass cameraClass = vm.resolveClass("android/hardware/Camera");
            DvmClass infoClass = vm.resolveClass(CAMERA_INFO_CLASS);

            assertEquals(2, invokeGetNumberOfCameras(jni, baseVM, useVaList, cameraClass));

            DvmObject<?> info0 = infoClass.newObject(null);
            invokeGetCameraInfo(jni, baseVM, useVaList, cameraClass, 0, info0);
            assertEquals(0, jni.getIntField(baseVM, info0, FACING_FIELD));
            assertEquals(90, jni.getIntField(baseVM, info0, ORIENTATION_FIELD));

            CapturedEvent infoEv = findLastEvent(sink.events, "android_camera",
                    "Camera.getCameraInfo");
            assertNotNull(infoEv);
            assertEquals("json-config", infoEv.source);
            assertEquals("cameraId=0,facing=0,orientation=90", String.valueOf(infoEv.value));
            assertNotNull(infoEv.note);
            assertFalse(infoEv.note.isEmpty());

            CapturedEvent facingEv = findLastEvent(sink.events, "android_camera",
                    "CameraInfo.facing");
            assertNotNull(facingEv);
            assertEquals("field=facing,result=0", String.valueOf(facingEv.value));
            assertEquals("json-config", facingEv.source);
            CapturedEvent orientEv = findLastEvent(sink.events, "android_camera",
                    "CameraInfo.orientation");
            assertNotNull(orientEv);
            assertEquals("field=orientation,result=90", String.valueOf(orientEv.value));

            DvmObject<?> info1 = infoClass.newObject(null);
            invokeGetCameraInfo(jni, baseVM, useVaList, cameraClass, 1, info1);
            assertEquals(1, jni.getIntField(baseVM, info1, FACING_FIELD));
            assertEquals(270, jni.getIntField(baseVM, info1, ORIENTATION_FIELD));
            CapturedEvent infoEv1 = findLastEvent(sink.events, "android_camera",
                    "Camera.getCameraInfo");
            assertNotNull(infoEv1);
            assertEquals("cameraId=1,facing=1,orientation=270", String.valueOf(infoEv1.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetCameraInfoFreshOutputs(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CAMERAS_INFOS_JSON);
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
            DvmClass cameraClass = vm.resolveClass("android/hardware/Camera");
            DvmClass infoClass = vm.resolveClass(CAMERA_INFO_CLASS);

            DvmObject<?> a = infoClass.newObject(null);
            DvmObject<?> b = infoClass.newObject(null);
            invokeGetCameraInfo(jni, baseVM, useVaList, cameraClass, 0, a);
            invokeGetCameraInfo(jni, baseVM, useVaList, cameraClass, 0, b);
            assertTrue("each getCameraInfo must write distinct markers",
                    a.getValue() != b.getValue());
            assertEquals(0, jni.getIntField(baseVM, a, FACING_FIELD));
            assertEquals(0, jni.getIntField(baseVM, b, FACING_FIELD));
            assertEquals(2, countEvents(sink.events, "android_camera",
                    "Camera.getCameraInfo"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetCameraInfoBadIndexAndOutput(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CAMERAS_INFOS_JSON);
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
            DvmClass cameraClass = vm.resolveClass("android/hardware/Camera");
            DvmClass infoClass = vm.resolveClass(CAMERA_INFO_CLASS);

            DvmObject<?> info = infoClass.newObject(null);
            try {
                invokeGetCameraInfo(jni, baseVM, useVaList, cameraClass, -1, info);
                fail("expected UOE for negative cameraId");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getCameraInfo"));
            }
            try {
                invokeGetCameraInfo(jni, baseVM, useVaList, cameraClass, 2, info);
                fail("expected UOE for out-of-range cameraId");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getCameraInfo"));
            }
            try {
                invokeGetCameraInfo(jni, baseVM, useVaList, cameraClass, 0, null);
                fail("expected UOE for null CameraInfo");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getCameraInfo"));
            }
            DvmObject<?> wrong = vm.resolveClass("java/lang/Object").newObject(null);
            try {
                invokeGetCameraInfo(jni, baseVM, useVaList, cameraClass, 0, wrong);
                fail("expected UOE for wrong output type");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getCameraInfo"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_camera event on bad getCameraInfo: " + e.api,
                        "android_camera".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetCameraInfoMissingInfos(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CAMERAS_TWO_JSON);
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
            DvmClass cameraClass = vm.resolveClass("android/hardware/Camera");
            DvmObject<?> info = vm.resolveClass(CAMERA_INFO_CLASS).newObject(null);

            assertEquals(2, invokeGetNumberOfCameras(jni, baseVM, useVaList, cameraClass));
            try {
                invokeGetCameraInfo(jni, baseVM, useVaList, cameraClass, 0, info);
                fail("expected UOE when infos missing");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getCameraInfo"));
            }
            assertEquals(0, countEvents(sink.events, "android_camera",
                    "Camera.getCameraInfo"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetCameraInfoAbsentCameras(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_CAMERAS_JSON);
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
            DvmClass cameraClass = vm.resolveClass("android/hardware/Camera");
            DvmObject<?> info = vm.resolveClass(CAMERA_INFO_CLASS).newObject(null);

            try {
                invokeGetCameraInfo(jni, baseVM, useVaList, cameraClass, 0, info);
                fail("expected UOE without cameras config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getCameraInfo"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_camera event: " + e.api,
                        "android_camera".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runCameraInfoProvenanceAndStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CAMERAS_INFOS_JSON);
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
            DvmClass cameraClass = vm.resolveClass("android/hardware/Camera");
            DvmClass infoClass = vm.resolveClass(CAMERA_INFO_CLASS);

            // plain CameraInfo → UOE on fields
            DvmObject<?> plain = infoClass.newObject(null);
            try {
                jni.getIntField(baseVM, plain, FACING_FIELD);
                fail("expected UOE for plain CameraInfo facing");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("facing"));
            }

            DvmObject<?> filled = infoClass.newObject(null);
            invokeGetCameraInfo(jni, baseVM, useVaList, cameraClass, 0, filled);
            int beforeFacingEvents = countEvents(sink.events, "android_camera",
                    "CameraInfo.facing");
            assertEquals(0, jni.getIntField(baseVM, filled, FACING_FIELD));

            // other field on live marker → UOE, no new event for that field
            try {
                jni.getIntField(baseVM, filled,
                        "android/hardware/Camera$CameraInfo->canDisableShutterSound:I");
                fail("expected UOE for other CameraInfo field");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("canDisableShutterSound"));
            }

            // stale: re-parse same JSON → new config instance identities
            TraceEnvironmentConfig config2 = TraceEnvironmentConfig.parse(CAMERAS_INFOS_JSON);
            emulator.set(TraceEnvironmentConfig.KEY, config2);
            try {
                jni.getIntField(baseVM, filled, FACING_FIELD);
                fail("expected UOE for stale ConfiguredCameraInfo");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("facing"));
            }
            assertEquals(beforeFacingEvents + 1,
                    countEvents(sink.events, "android_camera", "CameraInfo.facing"));

            // restore and control path still works with a fresh getCameraInfo
            emulator.set(TraceEnvironmentConfig.KEY, config);
            DvmObject<?> fresh = infoClass.newObject(null);
            invokeGetCameraInfo(jni, baseVM, useVaList, cameraClass, 1, fresh);
            assertEquals(1, jni.getIntField(baseVM, fresh, FACING_FIELD));
            assertEquals(270, jni.getIntField(baseVM, fresh, ORIENTATION_FIELD));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runCanDisableShutterSoundTrueFalse(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CAMERAS_SHUTTER_JSON);
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
            DvmClass cameraClass = vm.resolveClass("android/hardware/Camera");
            DvmClass infoClass = vm.resolveClass(CAMERA_INFO_CLASS);

            DvmObject<?> info0 = infoClass.newObject(null);
            invokeGetCameraInfo(jni, baseVM, useVaList, cameraClass, 0, info0);
            CapturedEvent infoEv = findLastEvent(sink.events, "android_camera",
                    "Camera.getCameraInfo");
            assertNotNull(infoEv);
            assertEquals("cameraId=0,facing=0,orientation=90", String.valueOf(infoEv.value));
            assertEquals(0, jni.getIntField(baseVM, info0, FACING_FIELD));
            assertEquals(90, jni.getIntField(baseVM, info0, ORIENTATION_FIELD));
            assertTrue(jni.getBooleanField(baseVM, info0, CAN_DISABLE_SHUTTER_SOUND_FIELD));

            CapturedEvent trueEv = findLastEvent(sink.events, "android_camera",
                    "CameraInfo.canDisableShutterSound");
            assertNotNull(trueEv);
            assertEquals("json-config", trueEv.source);
            assertEquals("field=canDisableShutterSound,result=true", String.valueOf(trueEv.value));
            assertNotNull(trueEv.note);
            assertFalse(trueEv.note.isEmpty());

            DvmObject<?> info1 = infoClass.newObject(null);
            invokeGetCameraInfo(jni, baseVM, useVaList, cameraClass, 1, info1);
            assertEquals(1, jni.getIntField(baseVM, info1, FACING_FIELD));
            assertEquals(270, jni.getIntField(baseVM, info1, ORIENTATION_FIELD));
            assertFalse(jni.getBooleanField(baseVM, info1, CAN_DISABLE_SHUTTER_SOUND_FIELD));
            CapturedEvent falseEv = findLastEvent(sink.events, "android_camera",
                    "CameraInfo.canDisableShutterSound");
            assertNotNull(falseEv);
            assertEquals("json-config", falseEv.source);
            assertEquals("field=canDisableShutterSound,result=false", String.valueOf(falseEv.value));
            assertEquals(2, countEvents(sink.events, "android_camera",
                    "CameraInfo.canDisableShutterSound"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runCanDisableShutterSoundAbsentIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig mixed = TraceEnvironmentConfig.parse(CAMERAS_SHUTTER_MIXED_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(mixed)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass cameraClass = vm.resolveClass("android/hardware/Camera");
            DvmClass infoClass = vm.resolveClass(CAMERA_INFO_CLASS);

            DvmObject<?> configured = infoClass.newObject(null);
            invokeGetCameraInfo(jni, baseVM, useVaList, cameraClass, 0, configured);
            assertTrue(jni.getBooleanField(baseVM, configured, CAN_DISABLE_SHUTTER_SOUND_FIELD));
            assertEquals(1, countEvents(sink.events, "android_camera",
                    "CameraInfo.canDisableShutterSound"));

            DvmObject<?> omitted = infoClass.newObject(null);
            invokeGetCameraInfo(jni, baseVM, useVaList, cameraClass, 1, omitted);
            assertEquals(1, jni.getIntField(baseVM, omitted, FACING_FIELD));
            assertEquals(270, jni.getIntField(baseVM, omitted, ORIENTATION_FIELD));
            try {
                jni.getBooleanField(baseVM, omitted, CAN_DISABLE_SHUTTER_SOUND_FIELD);
                fail("expected UOE when canDisableShutterSound key is absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("canDisableShutterSound"));
            }
            assertEquals(1, countEvents(sink.events, "android_camera",
                    "CameraInfo.canDisableShutterSound"));

            // plain CameraInfo
            DvmObject<?> plain = infoClass.newObject(null);
            try {
                jni.getBooleanField(baseVM, plain, CAN_DISABLE_SHUTTER_SOUND_FIELD);
                fail("expected UOE for plain CameraInfo canDisableShutterSound");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("canDisableShutterSound"));
            }

            // wrong signature / other field on live marker
            try {
                jni.getIntField(baseVM, configured,
                        "android/hardware/Camera$CameraInfo->canDisableShutterSound:I");
                fail("expected UOE for canDisableShutterSound int field");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("canDisableShutterSound"));
            }
            try {
                jni.getBooleanField(baseVM, configured,
                        "android/hardware/Camera$CameraInfo->otherFlag:Z");
                fail("expected UOE for other CameraInfo boolean field");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("otherFlag"));
            }
            assertEquals(1, countEvents(sink.events, "android_camera",
                    "CameraInfo.canDisableShutterSound"));

            // stale: re-parse same JSON → new config instance identities
            emulator.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(CAMERAS_SHUTTER_MIXED_JSON));
            try {
                jni.getBooleanField(baseVM, configured, CAN_DISABLE_SHUTTER_SOUND_FIELD);
                fail("expected UOE for stale ConfiguredCameraInfo canDisableShutterSound");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("canDisableShutterSound"));
            }
            assertEquals(1, countEvents(sink.events, "android_camera",
                    "CameraInfo.canDisableShutterSound"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        // both entries omit the key (existing infos JSON)
        TraceEnvironmentConfig noShutter = TraceEnvironmentConfig.parse(CAMERAS_INFOS_JSON);
        AndroidEmulator noShutterEmu = null;
        CapturingSink noShutterSink = new CapturingSink();
        try {
            noShutterEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(noShutter)
                    .build();
            TraceEnvironmentEventSink.register(noShutterEmu, noShutterSink);
            VM noShutterVm = noShutterEmu.createDalvikVM();
            AbstractJni noShutterJni = new AbstractJni() {
            };
            noShutterVm.setJni(noShutterJni);
            BaseVM noShutterBase = (BaseVM) noShutterVm;
            DvmClass noShutterCamera = noShutterVm.resolveClass("android/hardware/Camera");
            DvmObject<?> noShutterInfo = noShutterVm.resolveClass(CAMERA_INFO_CLASS).newObject(null);
            invokeGetCameraInfo(noShutterJni, noShutterBase, useVaList, noShutterCamera, 0, noShutterInfo);
            assertEquals(0, noShutterJni.getIntField(noShutterBase, noShutterInfo, FACING_FIELD));
            try {
                noShutterJni.getBooleanField(noShutterBase, noShutterInfo,
                        CAN_DISABLE_SHUTTER_SOUND_FIELD);
                fail("expected UOE when infos omit canDisableShutterSound");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("canDisableShutterSound"));
            }
            assertEquals(0, countEvents(noShutterSink.events, "android_camera",
                    "CameraInfo.canDisableShutterSound"));
        } finally {
            if (noShutterEmu != null) {
                TraceEnvironmentEventSink.unregister(noShutterEmu, noShutterSink);
                noShutterEmu.close();
            }
        }

        // foreign / other-VM marker
        TraceEnvironmentConfig foreignCfg = TraceEnvironmentConfig.parse(CAMERAS_SHUTTER_JSON);
        AndroidEmulator emuA = null;
        AndroidEmulator emuB = null;
        CapturingSink sinkB = new CapturingSink();
        try {
            emuA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(foreignCfg)
                    .build();
            emuB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(foreignCfg)
                    .build();
            TraceEnvironmentEventSink.register(emuB, sinkB);
            VM vmA = emuA.createDalvikVM();
            VM vmB = emuB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;
            DvmClass cameraA = vmA.resolveClass("android/hardware/Camera");
            DvmObject<?> foreignInfo = vmA.resolveClass(CAMERA_INFO_CLASS).newObject(null);
            invokeGetCameraInfo(jniA, baseA, useVaList, cameraA, 0, foreignInfo);
            try {
                jniB.getBooleanField(baseB, foreignInfo, CAN_DISABLE_SHUTTER_SOUND_FIELD);
                fail("expected UOE for foreign ConfiguredCameraInfo canDisableShutterSound");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("canDisableShutterSound"));
            }
            assertEquals(0, countEvents(sinkB.events, "android_camera",
                    "CameraInfo.canDisableShutterSound"));
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

    private static int invokeGetNumberOfCameras(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                DvmClass cameraClass) {
        DvmMethod method = new DvmMethod(cameraClass, "getNumberOfCameras", "()I", true);
        String signature = method.getSignature();
        assertEquals(GET_NUMBER_OF_CAMERAS_SIGNATURE, signature);
        if (useVaList) {
            return jni.callStaticIntMethodV(vm, cameraClass, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callStaticIntMethod(vm, cameraClass, signature,
                new TestNoArgVarArg(vm, method));
    }

    private static void invokeGetCameraInfo(AbstractJni jni, BaseVM vm, boolean useVaList,
                                            DvmClass cameraClass, int cameraId,
                                            DvmObject<?> outInfo) {
        DvmMethod method = new DvmMethod(cameraClass, "getCameraInfo",
                "(ILandroid/hardware/Camera$CameraInfo;)V", true);
        String signature = method.getSignature();
        assertEquals(GET_CAMERA_INFO_SIGNATURE, signature);
        int objectHash = outInfo == null ? 0 : vm.addLocalObject(outInfo);
        if (useVaList) {
            jni.callStaticVoidMethodV(vm, cameraClass, signature,
                    new TestIntObjectVaList(vm, method, cameraId, objectHash));
        } else {
            jni.callStaticVoidMethod(vm, cameraClass, signature,
                    new TestIntObjectVarArg(vm, method, cameraId, objectHash));
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

    private static int countEvents(List<CapturedEvent> events, String kind, String api) {
        int n = 0;
        for (CapturedEvent e : events) {
            if (kind.equals(e.kind) && api.equals(e.api)) {
                n++;
            }
        }
        return n;
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

    private static final class TestIntObjectVarArg extends VarArg {
        TestIntObjectVarArg(BaseVM vm, DvmMethod method, int int0, int objectHash) {
            super(vm, method);
            args.add(int0);
            args.add(objectHash);
        }
    }

    private static final class TestIntObjectVaList extends VaList {
        TestIntObjectVaList(BaseVM vm, DvmMethod method, int int0, int objectHash) {
            super(vm, method);
            args.add(int0);
            args.add(objectHash);
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
