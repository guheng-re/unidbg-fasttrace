package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.DeviceFingerprintProfile;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.dvm.ArrayListObject;
import com.github.unidbg.linux.android.dvm.api.SystemService;
import com.github.unidbg.linux.android.dvm.array.ArrayObject;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.linux.android.dvm.wrapper.DvmInteger;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Camera1 Model B: {@code Camera.open} plus configured NV21 preview / JPEG still delivery.
 */
public class AndroidCameraStreamsJniTest {

    private static final String STREAMS_JSON = "{"
            + "\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
            + "\"cameraId\":0,\"width\":2,\"height\":2,"
            + "\"previewHex\":\"000102030405\",\"jpegHex\":\"ffd8ffd9\"}]}}}";

    private static final String OPEN_ONLY_JSON =
            "{\"android\":{\"cameras\":{\"count\":1}}}";

    private static final String CAMERA2_JSON = "{"
            + "\"android\":{\"cameras\":{\"count\":1,"
            + "\"infos\":[{\"facing\":1,\"orientation\":270}],"
            + "\"streams\":[{\"cameraId\":0,\"width\":2,\"height\":2,"
            + "\"previewHex\":\"000102030405\",\"jpegHex\":\"ffd8ffd9\"}]}}}";

    @Test
    public void testOpenPreviewAndTakePicture() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(STREAMS_JSON);
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass cameraClass = vm.resolveClass("android/hardware/Camera");

            DvmMethod open = new DvmMethod(cameraClass, "open", "()Landroid/hardware/Camera;", true);
            DvmObject<?> camera = jni.callStaticObjectMethod(baseVM, cameraClass, open.getSignature(),
                    new TestNoArgVarArg(baseVM, open));
            assertNotNull(camera);

            DvmMethod getParameters = new DvmMethod(cameraClass, "getParameters",
                    "()Landroid/hardware/Camera$Parameters;", false);
            DvmObject<?> parameters = jni.callObjectMethod(baseVM, camera, getParameters.getSignature(),
                    new TestNoArgVarArg(baseVM, getParameters));
            assertNotNull(parameters);
            DvmMethod getPreviewSize = new DvmMethod(vm.resolveClass("android/hardware/Camera$Parameters"),
                    "getPreviewSize", "()Landroid/hardware/Camera$Size;", false);
            DvmObject<?> size = jni.callObjectMethod(baseVM, parameters, getPreviewSize.getSignature(),
                    new TestNoArgVarArg(baseVM, getPreviewSize));
            assertEquals(2, jni.getIntField(baseVM, size, "android/hardware/Camera$Size->width:I"));
            assertEquals(2, jni.getIntField(baseVM, size, "android/hardware/Camera$Size->height:I"));
            DvmMethod getFormat = new DvmMethod(vm.resolveClass("android/hardware/Camera$Parameters"),
                    "getPreviewFormat", "()I", false);
            assertEquals(17, jni.callIntMethod(baseVM, parameters, getFormat.getSignature(),
                    new TestNoArgVarArg(baseVM, getFormat)));

            List<DvmObject<?>> previewInbox = new ArrayList<DvmObject<?>>();
            DvmObject<?> previewCb = vm.resolveClass("android/hardware/Camera$PreviewCallback")
                    .newObject(previewInbox);
            DvmMethod setPreview = new DvmMethod(cameraClass, "setPreviewCallback",
                    "(Landroid/hardware/Camera$PreviewCallback;)V", false);
            jni.callVoidMethod(baseVM, camera, setPreview.getSignature(),
                    new TestObjectVarArg(baseVM, setPreview, previewCb));
            DvmMethod startPreview = new DvmMethod(cameraClass, "startPreview", "()V", false);
            jni.callVoidMethod(baseVM, camera, startPreview.getSignature(),
                    new TestNoArgVarArg(baseVM, startPreview));
            assertEquals(1, previewInbox.size());
            assertTrue(previewInbox.get(0) instanceof ByteArray);
            assertArrayEquals(new byte[]{0, 1, 2, 3, 4, 5},
                    ((ByteArray) previewInbox.get(0)).getValue());

            List<DvmObject<?>> jpegInbox = new ArrayList<DvmObject<?>>();
            DvmObject<?> jpegCb = vm.resolveClass("android/hardware/Camera$PictureCallback")
                    .newObject(jpegInbox);
            DvmMethod takePicture = new DvmMethod(cameraClass, "takePicture",
                    "(Landroid/hardware/Camera$ShutterCallback;"
                            + "Landroid/hardware/Camera$PictureCallback;"
                            + "Landroid/hardware/Camera$PictureCallback;)V", false);
            jni.callVoidMethod(baseVM, camera, takePicture.getSignature(),
                    new TestThreeObjectVarArg(baseVM, takePicture, null, null, jpegCb));
            assertEquals(1, jpegInbox.size());
            assertArrayEquals(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9},
                    ((ByteArray) jpegInbox.get(0)).getValue());

            DvmMethod release = new DvmMethod(cameraClass, "release", "()V", false);
            jni.callVoidMethod(baseVM, camera, release.getSignature(),
                    new TestNoArgVarArg(baseVM, release));
        } finally {
            emulator.close();
        }
    }

    @Test
    public void testOpenAbsentDoesNotTakeOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{\"android\":{}}");
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            BaseVM baseVM = (BaseVM) vm;
            DvmClass cameraClass = vm.resolveClass("android/hardware/Camera");
            DvmMethod open = new DvmMethod(cameraClass, "open", "()Landroid/hardware/Camera;", true);
            try {
                jni.callStaticObjectMethod(baseVM, cameraClass, open.getSignature(),
                        new TestNoArgVarArg(baseVM, open));
                fail();
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage().contains("open"));
            }
        } finally {
            emulator.close();
        }
    }

    @Test
    public void testOpenWithoutStreamsPreviewDoesNotTakeOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(OPEN_ONLY_JSON);
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass cameraClass = vm.resolveClass("android/hardware/Camera");
            DvmMethod open = new DvmMethod(cameraClass, "open", "(I)Landroid/hardware/Camera;", true);
            DvmObject<?> camera = jni.callStaticObjectMethod(baseVM, cameraClass, open.getSignature(),
                    new TestIntVarArg(baseVM, open, 0));
            assertNotNull(camera);
            DvmMethod startPreview = new DvmMethod(cameraClass, "startPreview", "()V", false);
            try {
                jni.callVoidMethod(baseVM, camera, startPreview.getSignature(),
                        new TestNoArgVarArg(baseVM, startPreview));
                fail();
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage().contains("startPreview"));
            }
        } finally {
            emulator.close();
        }
    }

    @Test
    public void testCamera2IdListCharacteristicsAndOpen() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CAMERA2_JSON);
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = new SystemService(vm, SystemService.CAMERA_SERVICE);

            DvmMethod getIds = new DvmMethod(vm.resolveClass("android/hardware/camera2/CameraManager"),
                    "getCameraIdList", "()[Ljava/lang/String;", false);
            ArrayObject ids = (ArrayObject) jni.callObjectMethod(baseVM, manager, getIds.getSignature(),
                    new TestNoArgVarArg(baseVM, getIds));
            assertEquals(1, ids.length());
            assertEquals("0", ((StringObject) ids.getValue()[0]).getValue());

            DvmClass characteristicsClass = vm.resolveClass(
                    "android/hardware/camera2/CameraCharacteristics");
            DvmObject<?> facingKey = jni.getStaticObjectField(baseVM, characteristicsClass,
                    "android/hardware/camera2/CameraCharacteristics->LENS_FACING:"
                            + "Landroid/hardware/camera2/CameraCharacteristics$Key;");
            DvmMethod getChars = new DvmMethod(vm.resolveClass("android/hardware/camera2/CameraManager"),
                    "getCameraCharacteristics",
                    "(Ljava/lang/String;)Landroid/hardware/camera2/CameraCharacteristics;", false);
            DvmObject<?> characteristics = jni.callObjectMethod(baseVM, manager, getChars.getSignature(),
                    new TestObjectVarArg(baseVM, getChars, new StringObject(vm, "0")));
            DvmMethod getKey = new DvmMethod(characteristicsClass, "get",
                    "(Landroid/hardware/camera2/CameraCharacteristics$Key;)Ljava/lang/Object;", false);
            DvmObject<?> facing = jni.callObjectMethod(baseVM, characteristics, getKey.getSignature(),
                    new TestObjectVarArg(baseVM, getKey, facingKey));
            assertTrue(facing instanceof DvmInteger);
            assertEquals(1, ((DvmInteger) facing).getValue().intValue());

            List<DvmObject<?>> opened = new ArrayList<DvmObject<?>>();
            DvmObject<?> callback = vm.resolveClass(
                    "android/hardware/camera2/CameraDevice$StateCallback").newObject(opened);
            DvmMethod openCamera = new DvmMethod(vm.resolveClass("android/hardware/camera2/CameraManager"),
                    "openCamera",
                    "(Ljava/lang/String;Landroid/hardware/camera2/CameraDevice$StateCallback;"
                            + "Landroid/os/Handler;)V", false);
            jni.callVoidMethod(baseVM, manager, openCamera.getSignature(),
                    new TestThreeObjectVarArg(baseVM, openCamera, new StringObject(vm, "0"),
                            callback, null));
            assertEquals(1, opened.size());
            DvmMethod getId = new DvmMethod(vm.resolveClass("android/hardware/camera2/CameraDevice"),
                    "getId", "()Ljava/lang/String;", false);
            assertEquals("0", ((StringObject) jni.callObjectMethod(baseVM, opened.get(0),
                    getId.getSignature(), new TestNoArgVarArg(baseVM, getId))).getValue());
        } finally {
            emulator.close();
        }
    }

    @Test
    public void testImageReaderJpegFeed() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CAMERA2_JSON);
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass readerClass = vm.resolveClass("android/media/ImageReader");
            DvmMethod newInstance = new DvmMethod(readerClass, "newInstance",
                    "(IIII)Landroid/media/ImageReader;", true);
            DvmObject<?> reader = jni.callStaticObjectMethod(baseVM, readerClass,
                    newInstance.getSignature(),
                    new TestFourIntVarArg(baseVM, newInstance, 2, 2, 256, 2));
            assertNotNull(reader);

            List<DvmObject<?>> available = new ArrayList<DvmObject<?>>();
            DvmObject<?> listener = vm.resolveClass(
                    "android/media/ImageReader$OnImageAvailableListener").newObject(available);
            DvmMethod setListener = new DvmMethod(readerClass, "setOnImageAvailableListener",
                    "(Landroid/media/ImageReader$OnImageAvailableListener;Landroid/os/Handler;)V",
                    false);
            jni.callVoidMethod(baseVM, reader, setListener.getSignature(),
                    new TestObjectVarArg(baseVM, setListener, listener));
            assertEquals(1, available.size());

            DvmMethod acquire = new DvmMethod(readerClass, "acquireLatestImage",
                    "()Landroid/media/Image;", false);
            DvmObject<?> image = jni.callObjectMethod(baseVM, reader, acquire.getSignature(),
                    new TestNoArgVarArg(baseVM, acquire));
            assertEquals(256, jni.callIntMethod(baseVM, image,
                    "android/media/Image->getFormat()I",
                    new TestNoArgVarArg(baseVM, new DvmMethod(
                            vm.resolveClass("android/media/Image"), "getFormat", "()I", false))));
            DvmMethod getPlanes = new DvmMethod(vm.resolveClass("android/media/Image"),
                    "getPlanes", "()[Landroid/media/Image$Plane;", false);
            ArrayObject planes = (ArrayObject) jni.callObjectMethod(baseVM, image,
                    getPlanes.getSignature(), new TestNoArgVarArg(baseVM, getPlanes));
            assertEquals(1, planes.length());
            DvmMethod getBuffer = new DvmMethod(vm.resolveClass("android/media/Image$Plane"),
                    "getBuffer", "()Ljava/nio/ByteBuffer;", false);
            DvmObject<?> buffer = jni.callObjectMethod(baseVM, planes.getValue()[0],
                    getBuffer.getSignature(), new TestNoArgVarArg(baseVM, getBuffer));
            assertEquals(4, jni.callIntMethod(baseVM, buffer, "java/nio/ByteBuffer->remaining()I",
                    new TestNoArgVarArg(baseVM, new DvmMethod(
                            vm.resolveClass("java/nio/ByteBuffer"), "remaining", "()I", false))));
            ByteArray bytes = (ByteArray) jni.callObjectMethod(baseVM, buffer,
                    "java/nio/ByteBuffer->array()[B",
                    new TestNoArgVarArg(baseVM, new DvmMethod(
                            vm.resolveClass("java/nio/ByteBuffer"), "array", "()[B", false)));
            assertArrayEquals(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9},
                    bytes.getValue());
        } finally {
            emulator.close();
        }
    }

    @Test
    public void testCaptureSessionFeedsImage() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CAMERA2_JSON);
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = new SystemService(vm, SystemService.CAMERA_SERVICE);

            List<DvmObject<?>> opened = new ArrayList<DvmObject<?>>();
            DvmObject<?> openCb = vm.resolveClass(
                    "android/hardware/camera2/CameraDevice$StateCallback").newObject(opened);
            DvmMethod openCamera = new DvmMethod(vm.resolveClass("android/hardware/camera2/CameraManager"),
                    "openCamera",
                    "(Ljava/lang/String;Landroid/hardware/camera2/CameraDevice$StateCallback;"
                            + "Landroid/os/Handler;)V", false);
            jni.callVoidMethod(baseVM, manager, openCamera.getSignature(),
                    new TestThreeObjectVarArg(baseVM, openCamera, new StringObject(vm, "0"),
                            openCb, null));
            DvmObject<?> device = opened.get(0);

            DvmClass readerClass = vm.resolveClass("android/media/ImageReader");
            DvmMethod newInstance = new DvmMethod(readerClass, "newInstance",
                    "(IIII)Landroid/media/ImageReader;", true);
            DvmObject<?> reader = jni.callStaticObjectMethod(baseVM, readerClass,
                    newInstance.getSignature(),
                    new TestFourIntVarArg(baseVM, newInstance, 2, 2, 256, 2));
            DvmMethod getSurface = new DvmMethod(readerClass, "getSurface",
                    "()Landroid/view/Surface;", false);
            DvmObject<?> surface = jni.callObjectMethod(baseVM, reader, getSurface.getSignature(),
                    new TestNoArgVarArg(baseVM, getSurface));

            List<DvmObject<?>> surfaces = new ArrayList<DvmObject<?>>();
            surfaces.add(surface);
            ArrayListObject output = new ArrayListObject(vm, surfaces);
            List<DvmObject<?>> configured = new ArrayList<DvmObject<?>>();
            DvmObject<?> sessionCb = vm.resolveClass(
                    "android/hardware/camera2/CameraCaptureSession$StateCallback")
                    .newObject(configured);
            DvmMethod createSession = new DvmMethod(
                    vm.resolveClass("android/hardware/camera2/CameraDevice"),
                    "createCaptureSession",
                    "(Ljava/util/List;Landroid/hardware/camera2/CameraCaptureSession$StateCallback;"
                            + "Landroid/os/Handler;)V", false);
            jni.callVoidMethod(baseVM, device, createSession.getSignature(),
                    new TestThreeObjectVarArg(baseVM, createSession, output, sessionCb, null));
            assertEquals(1, configured.size());
            DvmObject<?> session = configured.get(0);

            DvmMethod createRequest = new DvmMethod(
                    vm.resolveClass("android/hardware/camera2/CameraDevice"),
                    "createCaptureRequest",
                    "(I)Landroid/hardware/camera2/CaptureRequest$Builder;", false);
            DvmObject<?> builder = jni.callObjectMethod(baseVM, device, createRequest.getSignature(),
                    new TestIntVarArg(baseVM, createRequest, 2));
            DvmMethod addTarget = new DvmMethod(
                    vm.resolveClass("android/hardware/camera2/CaptureRequest$Builder"),
                    "addTarget",
                    "(Landroid/view/Surface;)Landroid/hardware/camera2/CaptureRequest$Builder;",
                    false);
            jni.callObjectMethod(baseVM, builder, addTarget.getSignature(),
                    new TestObjectVarArg(baseVM, addTarget, surface));
            DvmMethod build = new DvmMethod(
                    vm.resolveClass("android/hardware/camera2/CaptureRequest$Builder"),
                    "build", "()Landroid/hardware/camera2/CaptureRequest;", false);
            DvmObject<?> request = jni.callObjectMethod(baseVM, builder, build.getSignature(),
                    new TestNoArgVarArg(baseVM, build));

            List<DvmObject<?>> available = new ArrayList<DvmObject<?>>();
            DvmObject<?> imageListener = vm.resolveClass(
                    "android/media/ImageReader$OnImageAvailableListener").newObject(available);
            DvmMethod setListener = new DvmMethod(readerClass, "setOnImageAvailableListener",
                    "(Landroid/media/ImageReader$OnImageAvailableListener;Landroid/os/Handler;)V",
                    false);
            jni.callVoidMethod(baseVM, reader, setListener.getSignature(),
                    new TestObjectVarArg(baseVM, setListener, imageListener));
            available.clear();

            List<DvmObject<?>> completed = new ArrayList<DvmObject<?>>();
            DvmObject<?> captureCb = vm.resolveClass(
                    "android/hardware/camera2/CameraCaptureSession$CaptureCallback")
                    .newObject(completed);
            DvmMethod capture = new DvmMethod(
                    vm.resolveClass("android/hardware/camera2/CameraCaptureSession"),
                    "capture",
                    "(Landroid/hardware/camera2/CaptureRequest;"
                            + "Landroid/hardware/camera2/CameraCaptureSession$CaptureCallback;"
                            + "Landroid/os/Handler;)I", false);
            assertEquals(1, jni.callIntMethod(baseVM, session, capture.getSignature(),
                    new TestThreeObjectVarArg(baseVM, capture, request, captureCb, null)));
            assertEquals(1, available.size());
            assertEquals(1, completed.size());

            DvmMethod acquire = new DvmMethod(readerClass, "acquireLatestImage",
                    "()Landroid/media/Image;", false);
            DvmObject<?> image = jni.callObjectMethod(baseVM, reader, acquire.getSignature(),
                    new TestNoArgVarArg(baseVM, acquire));
            assertEquals(256, jni.callIntMethod(baseVM, image,
                    "android/media/Image->getFormat()I",
                    new TestNoArgVarArg(baseVM, new DvmMethod(
                            vm.resolveClass("android/media/Image"), "getFormat", "()I", false))));
        } finally {
            emulator.close();
        }
    }

    @Test
    public void testImageReaderYuv420888ThreePlanes() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CAMERA2_JSON);
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass readerClass = vm.resolveClass("android/media/ImageReader");
            DvmMethod newInstance = new DvmMethod(readerClass, "newInstance",
                    "(IIII)Landroid/media/ImageReader;", true);
            DvmObject<?> reader = jni.callStaticObjectMethod(baseVM, readerClass,
                    newInstance.getSignature(),
                    new TestFourIntVarArg(baseVM, newInstance, 2, 2, 35, 2));
            assertNotNull(reader);

            DvmMethod acquire = new DvmMethod(readerClass, "acquireLatestImage",
                    "()Landroid/media/Image;", false);
            DvmObject<?> image = jni.callObjectMethod(baseVM, reader, acquire.getSignature(),
                    new TestNoArgVarArg(baseVM, acquire));
            assertEquals(35, jni.callIntMethod(baseVM, image,
                    "android/media/Image->getFormat()I",
                    new TestNoArgVarArg(baseVM, new DvmMethod(
                            vm.resolveClass("android/media/Image"), "getFormat", "()I", false))));
            DvmMethod getPlanes = new DvmMethod(vm.resolveClass("android/media/Image"),
                    "getPlanes", "()[Landroid/media/Image$Plane;", false);
            ArrayObject planes = (ArrayObject) jni.callObjectMethod(baseVM, image,
                    getPlanes.getSignature(), new TestNoArgVarArg(baseVM, getPlanes));
            assertEquals(3, planes.length());

            DvmClass planeClass = vm.resolveClass("android/media/Image$Plane");
            DvmMethod getBuffer = new DvmMethod(planeClass, "getBuffer",
                    "()Ljava/nio/ByteBuffer;", false);
            DvmMethod rowStride = new DvmMethod(planeClass, "getRowStride", "()I", false);
            DvmMethod pixelStride = new DvmMethod(planeClass, "getPixelStride", "()I", false);
            DvmMethod remaining = new DvmMethod(vm.resolveClass("java/nio/ByteBuffer"),
                    "remaining", "()I", false);
            DvmMethod array = new DvmMethod(vm.resolveClass("java/nio/ByteBuffer"),
                    "array", "()[B", false);

            assertEquals(2, jni.callIntMethod(baseVM, planes.getValue()[0],
                    rowStride.getSignature(), new TestNoArgVarArg(baseVM, rowStride)));
            assertEquals(1, jni.callIntMethod(baseVM, planes.getValue()[0],
                    pixelStride.getSignature(), new TestNoArgVarArg(baseVM, pixelStride)));
            assertEquals(1, jni.callIntMethod(baseVM, planes.getValue()[1],
                    rowStride.getSignature(), new TestNoArgVarArg(baseVM, rowStride)));
            assertEquals(1, jni.callIntMethod(baseVM, planes.getValue()[1],
                    pixelStride.getSignature(), new TestNoArgVarArg(baseVM, pixelStride)));
            assertEquals(1, jni.callIntMethod(baseVM, planes.getValue()[2],
                    rowStride.getSignature(), new TestNoArgVarArg(baseVM, rowStride)));

            DvmObject<?> yBuf = jni.callObjectMethod(baseVM, planes.getValue()[0],
                    getBuffer.getSignature(), new TestNoArgVarArg(baseVM, getBuffer));
            DvmObject<?> uBuf = jni.callObjectMethod(baseVM, planes.getValue()[1],
                    getBuffer.getSignature(), new TestNoArgVarArg(baseVM, getBuffer));
            DvmObject<?> vBuf = jni.callObjectMethod(baseVM, planes.getValue()[2],
                    getBuffer.getSignature(), new TestNoArgVarArg(baseVM, getBuffer));
            assertEquals(4, jni.callIntMethod(baseVM, yBuf, remaining.getSignature(),
                    new TestNoArgVarArg(baseVM, remaining)));
            assertEquals(1, jni.callIntMethod(baseVM, uBuf, remaining.getSignature(),
                    new TestNoArgVarArg(baseVM, remaining)));
            assertEquals(1, jni.callIntMethod(baseVM, vBuf, remaining.getSignature(),
                    new TestNoArgVarArg(baseVM, remaining)));
            assertArrayEquals(new byte[]{0, 1, 2, 3},
                    ((ByteArray) jni.callObjectMethod(baseVM, yBuf, array.getSignature(),
                            new TestNoArgVarArg(baseVM, array))).getValue());
            assertArrayEquals(new byte[]{5},
                    ((ByteArray) jni.callObjectMethod(baseVM, uBuf, array.getSignature(),
                            new TestNoArgVarArg(baseVM, array))).getValue());
            assertArrayEquals(new byte[]{4},
                    ((ByteArray) jni.callObjectMethod(baseVM, vBuf, array.getSignature(),
                            new TestNoArgVarArg(baseVM, array))).getValue());
        } finally {
            emulator.close();
        }
    }

    @Test
    public void testPreviewFileOverlayFeed() throws Exception {
        File dir = Files.createTempDirectory("traceai-camera-jni-overlay-").toFile();
        File preview = new File(dir, "files/data/local/tmp/preview.nv21");
        File jpeg = new File(dir, "files/data/local/tmp/still.jpg");
        assertTrue(preview.getParentFile().mkdirs());
        Files.write(preview.toPath(), new byte[]{0, 1, 2, 3, 4, 5});
        Files.write(jpeg.toPath(), new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9});
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
                + "\"cameraId\":0,\"width\":2,\"height\":2,"
                + "\"previewFile\":\"/data/local/tmp/preview.nv21\","
                + "\"jpegFile\":\"/data/local/tmp/still.jpg\"}]}}}"
                ).getBytes(StandardCharsets.UTF_8));
        TraceEnvironmentConfig config = DeviceFingerprintProfile.load(json).getEnvironmentConfig();
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass cameraClass = vm.resolveClass("android/hardware/Camera");
            DvmMethod open = new DvmMethod(cameraClass, "open", "()Landroid/hardware/Camera;", true);
            DvmObject<?> camera = jni.callStaticObjectMethod(baseVM, cameraClass, open.getSignature(),
                    new TestNoArgVarArg(baseVM, open));

            List<DvmObject<?>> previewInbox = new ArrayList<DvmObject<?>>();
            DvmObject<?> previewCb = vm.resolveClass("android/hardware/Camera$PreviewCallback")
                    .newObject(previewInbox);
            DvmMethod setPreview = new DvmMethod(cameraClass, "setPreviewCallback",
                    "(Landroid/hardware/Camera$PreviewCallback;)V", false);
            jni.callVoidMethod(baseVM, camera, setPreview.getSignature(),
                    new TestObjectVarArg(baseVM, setPreview, previewCb));
            DvmMethod startPreview = new DvmMethod(cameraClass, "startPreview", "()V", false);
            jni.callVoidMethod(baseVM, camera, startPreview.getSignature(),
                    new TestNoArgVarArg(baseVM, startPreview));
            assertEquals(1, previewInbox.size());
            assertArrayEquals(new byte[]{0, 1, 2, 3, 4, 5},
                    ((ByteArray) previewInbox.get(0)).getValue());

            List<DvmObject<?>> jpegInbox = new ArrayList<DvmObject<?>>();
            DvmObject<?> jpegCb = vm.resolveClass("android/hardware/Camera$PictureCallback")
                    .newObject(jpegInbox);
            DvmMethod takePicture = new DvmMethod(cameraClass, "takePicture",
                    "(Landroid/hardware/Camera$ShutterCallback;"
                            + "Landroid/hardware/Camera$PictureCallback;"
                            + "Landroid/hardware/Camera$PictureCallback;)V", false);
            jni.callVoidMethod(baseVM, camera, takePicture.getSignature(),
                    new TestThreeObjectVarArg(baseVM, takePicture, null, null, jpegCb));
            assertEquals(1, jpegInbox.size());
            assertArrayEquals(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9},
                    ((ByteArray) jpegInbox.get(0)).getValue());
        } finally {
            emulator.close();
        }
    }

    @Test
    public void testCamera2AbsentDoesNotTakeOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{\"android\":{}}");
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = new SystemService(vm, SystemService.CAMERA_SERVICE);
            DvmMethod getIds = new DvmMethod(vm.resolveClass("android/hardware/camera2/CameraManager"),
                    "getCameraIdList", "()[Ljava/lang/String;", false);
            try {
                jni.callObjectMethod(baseVM, manager, getIds.getSignature(),
                        new TestNoArgVarArg(baseVM, getIds));
                fail();
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage().contains("getCameraIdList"));
            }
        } finally {
            emulator.close();
        }
    }

    private static final class TestNoArgVarArg extends VarArg {
        TestNoArgVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }

    private static final class TestIntVarArg extends VarArg {
        TestIntVarArg(BaseVM vm, DvmMethod method, int value) {
            super(vm, method);
            args.add(Integer.valueOf(value));
        }
    }

    private static final class TestObjectVarArg extends VarArg {
        TestObjectVarArg(BaseVM vm, DvmMethod method, DvmObject<?> object) {
            super(vm, method);
            args.add(Integer.valueOf(object == null ? 0 : object.hashCode()));
            if (object != null) {
                vm.addLocalObject(object);
            }
        }
    }

    private static final class TestFourIntVarArg extends VarArg {
        TestFourIntVarArg(BaseVM vm, DvmMethod method, int a, int b, int c, int d) {
            super(vm, method);
            args.add(Integer.valueOf(a));
            args.add(Integer.valueOf(b));
            args.add(Integer.valueOf(c));
            args.add(Integer.valueOf(d));
        }
    }

    private static final class TestThreeObjectVarArg extends VarArg {
        TestThreeObjectVarArg(BaseVM vm, DvmMethod method,
                              DvmObject<?> a, DvmObject<?> b, DvmObject<?> c) {
            super(vm, method);
            args.add(Integer.valueOf(a == null ? 0 : a.hashCode()));
            args.add(Integer.valueOf(b == null ? 0 : b.hashCode()));
            args.add(Integer.valueOf(c == null ? 0 : c.hashCode()));
            if (a != null) {
                vm.addLocalObject(a);
            }
            if (b != null) {
                vm.addLocalObject(b);
            }
            if (c != null) {
                vm.addLocalObject(c);
            }
        }
    }
}
