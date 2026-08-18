package com.github.unidbg.virtualmodule.android;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.Arm64Svc;
import com.github.unidbg.arm.ArmSvc;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.memory.SvcMemory;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.sun.jna.Pointer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Presence-gated {@code AImageReader}/{@code AImage} feed for Model B.
 * Symbols live on {@code libmediandk.so} and are registered only when
 * {@code android.cameras.streams} is present. Bytes come from the same
 * {@code previewHex}/{@code previewFile}/{@code jpegHex}/{@code jpegFile} rows
 * as the Java ImageReader path. No host camera, no HAL.
 */
final class CameraNdkImageSupport {

    static final int AMEDIA_OK = 0;
    static final int AMEDIA_ERROR_INVALID_OBJECT = -10003;
    static final int AMEDIA_ERROR_INVALID_PARAMETER = -10004;
    static final int AMEDIA_IMGREADER_NO_BUFFER_AVAILABLE = -30001;

    static final int AIMAGE_FORMAT_NV21 = 17;
    static final int AIMAGE_FORMAT_YUV_420_888 = 0x23;
    static final int AIMAGE_FORMAT_JPEG = 0x100;

    private final Map<Long, Reader> readers = new LinkedHashMap<Long, Reader>();
    private final Map<Long, Image> images = new LinkedHashMap<Long, Image>();

    void registerSymbols(Emulator<?> emulator, SvcMemory svcMemory,
                         Map<String, UnidbgPointer> symbols, boolean is64Bit) {
        put(svcMemory, symbols, is64Bit, "AImageReader_new", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return imageReaderNewFromRegs(e);
            }
        });
        put(svcMemory, symbols, is64Bit, "AImageReader_delete", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                imageReaderDelete(peer(e, 0));
                return 0L;
            }
        });
        put(svcMemory, symbols, is64Bit, "AImageReader_getWindow", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return imageReaderGetWindow(e, peer(e, 0), e.getContext().getPointerArg(1));
            }
        });
        put(svcMemory, symbols, is64Bit, "AImageReader_getWidth", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return imageReaderGetInt(e, peer(e, 0), e.getContext().getPointerArg(1), 0);
            }
        });
        put(svcMemory, symbols, is64Bit, "AImageReader_getHeight", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return imageReaderGetInt(e, peer(e, 0), e.getContext().getPointerArg(1), 1);
            }
        });
        put(svcMemory, symbols, is64Bit, "AImageReader_getFormat", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return imageReaderGetInt(e, peer(e, 0), e.getContext().getPointerArg(1), 2);
            }
        });
        put(svcMemory, symbols, is64Bit, "AImageReader_getMaxImages", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return imageReaderGetInt(e, peer(e, 0), e.getContext().getPointerArg(1), 3);
            }
        });
        put(svcMemory, symbols, is64Bit, "AImageReader_acquireLatestImage", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return imageReaderAcquire(e, peer(e, 0), e.getContext().getPointerArg(1), true);
            }
        });
        put(svcMemory, symbols, is64Bit, "AImageReader_acquireNextImage", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return imageReaderAcquire(e, peer(e, 0), e.getContext().getPointerArg(1), false);
            }
        });
        put(svcMemory, symbols, is64Bit, "AImageReader_setImageListener", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return imageReaderSetListener(peer(e, 0));
            }
        });
        put(svcMemory, symbols, is64Bit, "AImage_delete", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                imageDelete(peer(e, 0));
                return 0L;
            }
        });
        put(svcMemory, symbols, is64Bit, "AImage_getWidth", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return imageGetInt(e, peer(e, 0), e.getContext().getPointerArg(1), 0);
            }
        });
        put(svcMemory, symbols, is64Bit, "AImage_getHeight", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return imageGetInt(e, peer(e, 0), e.getContext().getPointerArg(1), 1);
            }
        });
        put(svcMemory, symbols, is64Bit, "AImage_getFormat", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return imageGetInt(e, peer(e, 0), e.getContext().getPointerArg(1), 2);
            }
        });
        put(svcMemory, symbols, is64Bit, "AImage_getNumberOfPlanes", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return imageGetInt(e, peer(e, 0), e.getContext().getPointerArg(1), 3);
            }
        });
        put(svcMemory, symbols, is64Bit, "AImage_getTimestamp", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return imageGetTimestamp(peer(e, 0), e.getContext().getPointerArg(1), e);
            }
        });
        put(svcMemory, symbols, is64Bit, "AImage_getPlaneData", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                RegisterContext ctx = e.getContext();
                return imageGetPlaneData(e, peer(e, 0), ctx.getIntArg(1),
                        ctx.getPointerArg(2), ctx.getPointerArg(3));
            }
        });
        put(svcMemory, symbols, is64Bit, "AImage_getPlaneRowStride", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return imageGetPlaneInt(peer(e, 0), e.getContext().getIntArg(1),
                        e.getContext().getPointerArg(2), true);
            }
        });
        put(svcMemory, symbols, is64Bit, "AImage_getPlanePixelStride", new Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return imageGetPlaneInt(peer(e, 0), e.getContext().getIntArg(1),
                        e.getContext().getPointerArg(2), false);
            }
        });
    }

    int imageReaderNew(Emulator<?> emulator, int width, int height, int format, int maxImages,
                       Pointer readerOut) {
        if (emulator == null || readerOut == null || maxImages < 1) {
            return AMEDIA_ERROR_INVALID_PARAMETER;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null || !config.isAndroidCamerasConfigured()
                || !config.getAndroidCamerasConfig().isStreamsConfigured()) {
            return AMEDIA_ERROR_INVALID_PARAMETER;
        }
        TraceEnvironmentConfig.AndroidCameraStreamConfig stream =
                findStream(config.getAndroidCamerasConfig(), width, height, format);
        if (stream == null) {
            return AMEDIA_ERROR_INVALID_PARAMETER;
        }
        byte[] frame = format == AIMAGE_FORMAT_JPEG
                ? config.resolveCameraJpeg(stream) : config.resolveCameraPreview(stream);
        if (frame == null) {
            return AMEDIA_ERROR_INVALID_PARAMETER;
        }
        MemoryBlock handle = emulator.getMemory().malloc(8, true);
        Reader reader = new Reader(handle, stream.getCameraId(), width, height, format,
                maxImages, frame, splitPlanes(format, width, height, frame));
        readers.put(Long.valueOf(handle.getPointer().peer), reader);
        readerOut.setPointer(0, handle.getPointer());
        TraceEnvironmentEventSink.emit(emulator, "android_camera",
                "AImageReader_new",
                "cameraId=" + stream.getCameraId() + ",width=" + width + ",height=" + height
                        + ",format=" + format + ",bytes=" + frame.length,
                "json-config", "创建配置的 NDK ImageReader");
        return AMEDIA_OK;
    }

    int imageReaderGetWindow(Emulator<?> emulator, long readerPeer, Pointer windowOut) {
        Reader reader = readers.get(Long.valueOf(readerPeer));
        if (reader == null || windowOut == null) {
            return AMEDIA_ERROR_INVALID_PARAMETER;
        }
        if (reader.window == null) {
            reader.window = emulator.getMemory().malloc(8, true);
        }
        windowOut.setPointer(0, reader.window.getPointer());
        return AMEDIA_OK;
    }

    int imageReaderGetInt(Emulator<?> emulator, long readerPeer, Pointer out, int field) {
        Reader reader = readers.get(Long.valueOf(readerPeer));
        if (reader == null || out == null) {
            return AMEDIA_ERROR_INVALID_PARAMETER;
        }
        int value = field == 0 ? reader.width
                : field == 1 ? reader.height
                : field == 2 ? reader.format : reader.maxImages;
        out.setInt(0, value);
        return AMEDIA_OK;
    }

    int imageReaderAcquire(Emulator<?> emulator, long readerPeer, Pointer imageOut, boolean latest) {
        Reader reader = readers.get(Long.valueOf(readerPeer));
        if (reader == null || imageOut == null) {
            return AMEDIA_ERROR_INVALID_PARAMETER;
        }
        MemoryBlock handle = emulator.getMemory().malloc(8, true);
        Image image = new Image(handle, reader);
        images.put(Long.valueOf(handle.getPointer().peer), image);
        imageOut.setPointer(0, handle.getPointer());
        TraceEnvironmentEventSink.emit(emulator, "android_camera",
                latest ? "AImageReader_acquireLatestImage" : "AImageReader_acquireNextImage",
                "cameraId=" + reader.cameraId + ",bytes=" + reader.frame.length,
                "json-config", "读取配置的 NDK ImageReader 帧");
        return AMEDIA_OK;
    }

    int imageReaderSetListener(long readerPeer) {
        return readers.containsKey(Long.valueOf(readerPeer))
                ? AMEDIA_OK : AMEDIA_ERROR_INVALID_PARAMETER;
    }

    void imageReaderDelete(long readerPeer) {
        Reader reader = readers.remove(Long.valueOf(readerPeer));
        if (reader == null) {
            return;
        }
        Iterator<Map.Entry<Long, Image>> it = images.entrySet().iterator();
        while (it.hasNext()) {
            Image image = it.next().getValue();
            if (image.reader == reader) {
                image.free();
                it.remove();
            }
        }
        reader.free();
    }

    int imageGetInt(Emulator<?> emulator, long imagePeer, Pointer out, int field) {
        Image image = images.get(Long.valueOf(imagePeer));
        if (image == null || out == null) {
            return AMEDIA_ERROR_INVALID_PARAMETER;
        }
        Reader reader = image.reader;
        int value = field == 0 ? reader.width
                : field == 1 ? reader.height
                : field == 2 ? reader.format : reader.planes.length;
        out.setInt(0, value);
        return AMEDIA_OK;
    }

    int imageGetTimestamp(long imagePeer, Pointer out, Emulator<?> emulator) {
        if (images.get(Long.valueOf(imagePeer)) == null || out == null) {
            return AMEDIA_ERROR_INVALID_PARAMETER;
        }
        if (emulator != null && emulator.is32Bit()) {
            out.setLong(0, 0L);
        } else {
            out.setLong(0, 0L);
        }
        return AMEDIA_OK;
    }

    int imageGetPlaneData(Emulator<?> emulator, long imagePeer, int planeIdx,
                          Pointer dataOut, Pointer lengthOut) {
        Image image = images.get(Long.valueOf(imagePeer));
        if (image == null || dataOut == null || lengthOut == null
                || planeIdx < 0 || planeIdx >= image.reader.planes.length) {
            return AMEDIA_ERROR_INVALID_PARAMETER;
        }
        Plane plane = image.reader.planes[planeIdx];
        if (plane.block == null) {
            int need = Math.max(plane.data.length, 1);
            plane.block = emulator.getMemory().malloc(need, true);
            if (plane.data.length > 0) {
                plane.block.getPointer().write(0, plane.data, 0, plane.data.length);
            }
        }
        dataOut.setPointer(0, plane.block.getPointer());
        lengthOut.setInt(0, plane.data.length);
        return AMEDIA_OK;
    }

    int imageGetPlaneInt(long imagePeer, int planeIdx, Pointer out, boolean rowStride) {
        Image image = images.get(Long.valueOf(imagePeer));
        if (image == null || out == null
                || planeIdx < 0 || planeIdx >= image.reader.planes.length) {
            return AMEDIA_ERROR_INVALID_PARAMETER;
        }
        Plane plane = image.reader.planes[planeIdx];
        out.setInt(0, rowStride ? plane.rowStride : plane.pixelStride);
        return AMEDIA_OK;
    }

    void imageDelete(long imagePeer) {
        Image image = images.remove(Long.valueOf(imagePeer));
        if (image != null) {
            image.free();
        }
    }

    void release() {
        List<Long> imageKeys = new ArrayList<Long>(images.keySet());
        for (int i = 0; i < imageKeys.size(); i++) {
            imageDelete(imageKeys.get(i).longValue());
        }
        List<Long> readerKeys = new ArrayList<Long>(readers.keySet());
        for (int i = 0; i < readerKeys.size(); i++) {
            imageReaderDelete(readerKeys.get(i).longValue());
        }
    }

    int readerCountForTest() {
        return readers.size();
    }

    int imageCountForTest() {
        return images.size();
    }

    private long imageReaderNewFromRegs(Emulator<?> emulator) {
        RegisterContext ctx = emulator.getContext();
        return imageReaderNew(emulator, ctx.getIntArg(0), ctx.getIntArg(1),
                ctx.getIntArg(2), ctx.getIntArg(3), ctx.getPointerArg(4));
    }

    static TraceEnvironmentConfig.AndroidCameraStreamConfig findStream(
            TraceEnvironmentConfig.AndroidCamerasConfig cameras, int width, int height, int format) {
        List<TraceEnvironmentConfig.AndroidCameraStreamConfig> streams = cameras.getStreams();
        for (int i = 0; i < streams.size(); i++) {
            TraceEnvironmentConfig.AndroidCameraStreamConfig stream = streams.get(i);
            if (stream.getWidth() != width || stream.getHeight() != height) {
                continue;
            }
            if (format == AIMAGE_FORMAT_JPEG && stream.isJpegConfigured()) {
                return stream;
            }
            if ((format == AIMAGE_FORMAT_NV21 || format == AIMAGE_FORMAT_YUV_420_888)
                    && stream.isPreviewConfigured()) {
                return stream;
            }
        }
        return null;
    }

    static Plane[] splitPlanes(int format, int width, int height, byte[] frame) {
        if (format == AIMAGE_FORMAT_YUV_420_888 && width >= 2 && height >= 2
                && (width & 1) == 0 && (height & 1) == 0 && frame != null) {
            int ySize = width * height;
            int chromaCount = ySize / 4;
            if (frame.length >= ySize + chromaCount * 2) {
                byte[] y = Arrays.copyOfRange(frame, 0, ySize);
                byte[] u = new byte[chromaCount];
                byte[] v = new byte[chromaCount];
                int chroma = ySize;
                for (int i = 0; i < chromaCount; i++) {
                    v[i] = frame[chroma + i * 2];
                    u[i] = frame[chroma + i * 2 + 1];
                }
                return new Plane[] {
                        new Plane(y, width, 1),
                        new Plane(u, width / 2, 1),
                        new Plane(v, width / 2, 1)
                };
            }
        }
        byte[] copy = frame == null ? new byte[0] : Arrays.copyOf(frame, frame.length);
        int rowStride = format == AIMAGE_FORMAT_JPEG ? copy.length : width;
        return new Plane[] { new Plane(copy, rowStride, 1) };
    }

    private static long peer(Emulator<?> emulator, int index) {
        Pointer pointer = emulator.getContext().getPointerArg(index);
        return pointer == null ? 0L : UnidbgPointer.nativeValue(pointer);
    }

    static void put(SvcMemory svcMemory, Map<String, UnidbgPointer> symbols, boolean is64Bit,
                    String name, Handler handler) {
        symbols.put(name, svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return handler.handle(emulator);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return handler.handle(emulator);
            }
        }));
    }

    interface Handler {
        long handle(Emulator<?> emulator);
    }

    static final class Plane {
        final byte[] data;
        final int rowStride;
        final int pixelStride;
        MemoryBlock block;

        Plane(byte[] data, int rowStride, int pixelStride) {
            this.data = data;
            this.rowStride = rowStride;
            this.pixelStride = pixelStride;
        }
    }

    private static final class Reader {
        final MemoryBlock handle;
        final int cameraId;
        final int width;
        final int height;
        final int format;
        final int maxImages;
        final byte[] frame;
        final Plane[] planes;
        MemoryBlock window;

        Reader(MemoryBlock handle, int cameraId, int width, int height, int format,
               int maxImages, byte[] frame, Plane[] planes) {
            this.handle = handle;
            this.cameraId = cameraId;
            this.width = width;
            this.height = height;
            this.format = format;
            this.maxImages = maxImages;
            this.frame = frame;
            this.planes = planes;
        }

        void free() {
            if (window != null) {
                window.free();
                window = null;
            }
            for (int i = 0; i < planes.length; i++) {
                if (planes[i].block != null) {
                    planes[i].block.free();
                    planes[i].block = null;
                }
            }
            handle.free();
        }
    }

    private static final class Image {
        final MemoryBlock handle;
        final Reader reader;

        Image(MemoryBlock handle, Reader reader) {
            this.handle = handle;
            this.reader = reader;
        }

        void free() {
            handle.free();
        }
    }
}
