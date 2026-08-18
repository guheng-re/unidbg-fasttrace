package com.github.unidbg.virtualmodule.android;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.memory.MemoryBlock;
import com.sun.jna.Pointer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * NDK Model B: AImageReader feed + ACameraManager list/open. Uses the same
 * package-visible helpers the SVC path calls.
 */
public class CameraNdkModuleTest {

    private static final String STREAMS_JSON = "{"
            + "\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
            + "\"cameraId\":0,\"width\":2,\"height\":2,"
            + "\"previewHex\":\"000102030405\",\"jpegHex\":\"ffd8ffd9\"}]}}}";

    private static final String COUNT_ONLY_JSON =
            "{\"android\":{\"cameras\":{\"count\":2}}}";

    @Test
    public void testImageReaderYuvAndJpegOn32And64() throws Exception {
        runImageReader(false);
        runImageReader(true);
    }

    private static void runImageReader(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(STREAMS_JSON);
        AndroidEmulator emulator = null;
        MediaNdkModule media = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            media = new MediaNdkModule(emulator, emulator.createDalvikVM());
            assertTrue(media.isImageReaderApiRegisteredForTest());
            CameraNdkImageSupport images = media.imageSupportForTest();
            assertNotNull(images);

            MemoryBlock readerOut = emulator.getMemory().malloc(16, true);
            blocks.add(readerOut);
            assertEquals(CameraNdkImageSupport.AMEDIA_OK,
                    images.imageReaderNew(emulator, 2, 2,
                            CameraNdkImageSupport.AIMAGE_FORMAT_YUV_420_888, 2,
                            readerOut.getPointer()));
            long readerPeer = pointerPeer(readerOut.getPointer().getPointer(0));

            MemoryBlock imageOut = emulator.getMemory().malloc(16, true);
            blocks.add(imageOut);
            assertEquals(CameraNdkImageSupport.AMEDIA_OK,
                    images.imageReaderAcquire(emulator, readerPeer, imageOut.getPointer(), true));
            long imagePeer = pointerPeer(imageOut.getPointer().getPointer(0));

            MemoryBlock intOut = emulator.getMemory().malloc(8, true);
            blocks.add(intOut);
            assertEquals(CameraNdkImageSupport.AMEDIA_OK,
                    images.imageGetInt(emulator, imagePeer, intOut.getPointer(), 2));
            assertEquals(CameraNdkImageSupport.AIMAGE_FORMAT_YUV_420_888, intOut.getPointer().getInt(0));
            assertEquals(CameraNdkImageSupport.AMEDIA_OK,
                    images.imageGetInt(emulator, imagePeer, intOut.getPointer(), 3));
            assertEquals(3, intOut.getPointer().getInt(0));

            assertEquals(CameraNdkImageSupport.AMEDIA_OK,
                    images.imageGetPlaneInt(imagePeer, 0, intOut.getPointer(), true));
            assertEquals(2, intOut.getPointer().getInt(0));
            assertEquals(CameraNdkImageSupport.AMEDIA_OK,
                    images.imageGetPlaneInt(imagePeer, 1, intOut.getPointer(), false));
            assertEquals(1, intOut.getPointer().getInt(0));

            MemoryBlock dataOut = emulator.getMemory().malloc(16, true);
            MemoryBlock lenOut = emulator.getMemory().malloc(8, true);
            blocks.add(dataOut);
            blocks.add(lenOut);
            assertEquals(CameraNdkImageSupport.AMEDIA_OK,
                    images.imageGetPlaneData(emulator, imagePeer, 0,
                            dataOut.getPointer(), lenOut.getPointer()));
            assertEquals(4, lenOut.getPointer().getInt(0));
            assertArrayEquals(new byte[]{0, 1, 2, 3},
                    dataOut.getPointer().getPointer(0).getByteArray(0, 4));
            assertEquals(CameraNdkImageSupport.AMEDIA_OK,
                    images.imageGetPlaneData(emulator, imagePeer, 1,
                            dataOut.getPointer(), lenOut.getPointer()));
            assertEquals(1, lenOut.getPointer().getInt(0));
            assertArrayEquals(new byte[]{5},
                    dataOut.getPointer().getPointer(0).getByteArray(0, 1));
            assertEquals(CameraNdkImageSupport.AMEDIA_OK,
                    images.imageGetPlaneData(emulator, imagePeer, 2,
                            dataOut.getPointer(), lenOut.getPointer()));
            assertArrayEquals(new byte[]{4},
                    dataOut.getPointer().getPointer(0).getByteArray(0, 1));

            images.imageDelete(imagePeer);
            images.imageReaderDelete(readerPeer);
            assertEquals(0, images.readerCountForTest());
            assertEquals(0, images.imageCountForTest());

            assertEquals(CameraNdkImageSupport.AMEDIA_OK,
                    images.imageReaderNew(emulator, 2, 2,
                            CameraNdkImageSupport.AIMAGE_FORMAT_JPEG, 1,
                            readerOut.getPointer()));
            readerPeer = pointerPeer(readerOut.getPointer().getPointer(0));
            assertEquals(CameraNdkImageSupport.AMEDIA_OK,
                    images.imageReaderAcquire(emulator, readerPeer, imageOut.getPointer(), false));
            imagePeer = pointerPeer(imageOut.getPointer().getPointer(0));
            assertEquals(CameraNdkImageSupport.AMEDIA_OK,
                    images.imageGetInt(emulator, imagePeer, intOut.getPointer(), 3));
            assertEquals(1, intOut.getPointer().getInt(0));
            assertEquals(CameraNdkImageSupport.AMEDIA_OK,
                    images.imageGetPlaneData(emulator, imagePeer, 0,
                            dataOut.getPointer(), lenOut.getPointer()));
            assertEquals(4, lenOut.getPointer().getInt(0));
            assertArrayEquals(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9},
                    dataOut.getPointer().getPointer(0).getByteArray(0, 4));

            assertEquals(CameraNdkImageSupport.AMEDIA_ERROR_INVALID_PARAMETER,
                    images.imageReaderNew(emulator, 8, 8,
                            CameraNdkImageSupport.AIMAGE_FORMAT_YUV_420_888, 1,
                            readerOut.getPointer()));
        } finally {
            teardown(media, null, emulator, blocks);
        }
    }

    @Test
    public void testImageReaderAbsentWithoutStreams() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(COUNT_ONLY_JSON);
        AndroidEmulator emulator = null;
        MediaNdkModule media = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            media = new MediaNdkModule(emulator, emulator.createDalvikVM());
            assertFalse(media.isImageReaderApiRegisteredForTest());
        } finally {
            teardown(media, null, emulator, null);
        }
    }

    @Test
    public void testCameraManagerIdListAndOpen() throws Exception {
        runManager(false);
        runManager(true);
    }

    private static void runManager(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(STREAMS_JSON);
        AndroidEmulator emulator = null;
        CameraNdkModule camera = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            camera = new CameraNdkModule(emulator, emulator.createDalvikVM());
            long manager = camera.managerCreate(emulator);
            assertNotEquals(0L, manager);
            assertEquals(1, camera.managerCountForTest());

            MemoryBlock listOut = emulator.getMemory().malloc(16, true);
            blocks.add(listOut);
            assertEquals(CameraNdkModule.ACAMERA_OK,
                    camera.getCameraIdList(emulator, manager, listOut.getPointer()));
            Pointer list = listOut.getPointer().getPointer(0);
            assertNotNull(list);
            assertEquals(1, list.getInt(0));
            Pointer ids = list.getPointer(emulator.getPointerSize());
            assertEquals("0", ids.getPointer(0).getString(0));

            MemoryBlock deviceOut = emulator.getMemory().malloc(16, true);
            blocks.add(deviceOut);
            assertEquals(CameraNdkModule.ACAMERA_OK,
                    camera.openCamera(emulator, manager, "0", deviceOut.getPointer()));
            long devicePeer = pointerPeer(deviceOut.getPointer().getPointer(0));
            assertEquals(1, camera.deviceCountForTest());
            long idPeer = camera.deviceGetId(devicePeer);
            assertNotEquals(0L, idPeer);
            assertEquals("0", com.github.unidbg.pointer.UnidbgPointer.pointer(emulator, idPeer)
                    .getString(0));
            assertEquals(CameraNdkModule.ACAMERA_ERROR_INVALID_PARAMETER,
                    camera.openCamera(emulator, manager, "9", deviceOut.getPointer()));
            assertEquals(CameraNdkModule.ACAMERA_OK, camera.deviceClose(devicePeer));
            assertEquals(0, camera.deviceCountForTest());

            MemoryBlock sessionOut = emulator.getMemory().malloc(16, true);
            blocks.add(sessionOut);
            assertEquals(CameraNdkModule.ACAMERA_OK,
                    camera.stubCreate(emulator, sessionOut.getPointer()));
            camera.stubFree(pointerPeer(sessionOut.getPointer().getPointer(0)));

            camera.deleteCameraIdList(pointerPeer(list));
            camera.managerDelete(manager);
            assertEquals(0, camera.managerCountForTest());
        } finally {
            teardown(null, camera, emulator, blocks);
        }
    }

    @Test
    public void testManagerAbsentWithoutCamerasNode() throws Exception {
        AndroidEmulator emulator = null;
        CameraNdkModule camera = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit()
                    .setEnvironmentConfig(TraceEnvironmentConfig.parse("{\"android\":{}}"))
                    .build();
            camera = new CameraNdkModule(emulator, emulator.createDalvikVM());
            assertEquals(0L, camera.managerCreate(emulator));
        } finally {
            teardown(null, camera, emulator, null);
        }
    }

    private static long pointerPeer(Pointer pointer) {
        return pointer == null ? 0L : com.github.unidbg.pointer.UnidbgPointer.nativeValue(pointer);
    }

    private static void teardown(MediaNdkModule media, CameraNdkModule camera,
                                 AndroidEmulator emulator, List<MemoryBlock> blocks) {
        if (media != null) {
            media.release();
        }
        if (camera != null) {
            camera.release();
        }
        if (blocks != null) {
            for (int i = 0; i < blocks.size(); i++) {
                try {
                    blocks.get(i).free();
                } catch (Exception ignored) {
                    // teardown
                }
            }
            blocks.clear();
        }
        if (emulator != null) {
            try {
                emulator.close();
            } catch (Exception ignored) {
                // teardown
            }
        }
    }
}
