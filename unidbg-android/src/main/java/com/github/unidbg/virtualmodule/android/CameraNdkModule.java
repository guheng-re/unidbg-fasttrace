package com.github.unidbg.virtualmodule.android;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.memory.SvcMemory;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.github.unidbg.virtualmodule.VirtualModule;
import com.sun.jna.Pointer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Presence-gated {@code libcamera2ndk.so} subset for Model B: list / open / close
 * plus capture-session stubs. Frames themselves are fed by
 * {@link CameraNdkImageSupport} on {@code libmediandk.so}. No host camera, no HAL.
 */
public class CameraNdkModule extends VirtualModule<VM> {

    public static final int ACAMERA_OK = 0;
    public static final int ACAMERA_ERROR_INVALID_PARAMETER = -10001;

    private final Map<Long, MemoryBlock> managers = new LinkedHashMap<Long, MemoryBlock>();
    private final Map<Long, Device> devices = new LinkedHashMap<Long, Device>();
    private final Map<Long, MemoryBlock> idLists = new LinkedHashMap<Long, MemoryBlock>();
    private final Map<Long, List<MemoryBlock>> idListExtras = new LinkedHashMap<Long, List<MemoryBlock>>();
    private final Map<Long, MemoryBlock> stubs = new LinkedHashMap<Long, MemoryBlock>();

    public CameraNdkModule(Emulator<?> emulator, VM vm) {
        super(emulator, vm, "libcamera2ndk.so");
    }

    @Override
    protected void onInitialize(Emulator<?> emulator, VM extra, Map<String, UnidbgPointer> symbols) {
        boolean is64Bit = emulator.is64Bit();
        SvcMemory svc = emulator.getSvcMemory();
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACameraManager_create", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return managerCreate(e);
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACameraManager_delete", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                managerDelete(peer(e, 0));
                return 0L;
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACameraManager_getCameraIdList", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return getCameraIdList(e, peer(e, 0), e.getContext().getPointerArg(1));
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACameraManager_deleteCameraIdList", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                deleteCameraIdList(peer(e, 0));
                return 0L;
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACameraManager_openCamera", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                RegisterContext ctx = e.getContext();
                return openCamera(e, peer(e, 0), readCString(ctx.getPointerArg(1)),
                        ctx.getPointerArg(3));
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACameraDevice_close", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return deviceClose(peer(e, 0));
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACameraDevice_getId", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return deviceGetId(peer(e, 0));
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACameraDevice_createCaptureRequest", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return stubOut(e, peer(e, 0), e.getContext().getPointerArg(2), devices);
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACameraDevice_createCaptureSession", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return stubOut(e, peer(e, 0), e.getContext().getPointerArg(3), devices);
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACaptureSessionOutputContainer_create", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return stubCreate(e, e.getContext().getPointerArg(0));
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACaptureSessionOutputContainer_free", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                stubFree(peer(e, 0));
                return 0L;
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACaptureSessionOutputContainer_add", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return stubs.containsKey(Long.valueOf(peer(e, 0)))
                        ? ACAMERA_OK : ACAMERA_ERROR_INVALID_PARAMETER;
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACaptureSessionOutput_create", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return stubCreate(e, e.getContext().getPointerArg(1));
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACaptureSessionOutput_free", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                stubFree(peer(e, 0));
                return 0L;
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACaptureRequest_addTarget", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return stubs.containsKey(Long.valueOf(peer(e, 0)))
                        ? ACAMERA_OK : ACAMERA_ERROR_INVALID_PARAMETER;
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACaptureRequest_free", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                stubFree(peer(e, 0));
                return 0L;
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACameraCaptureSession_setRepeatingRequest", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return sessionOk(e, peer(e, 0), "ACameraCaptureSession_setRepeatingRequest");
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACameraCaptureSession_capture", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return sessionOk(e, peer(e, 0), "ACameraCaptureSession_capture");
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACameraCaptureSession_stopRepeating", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                return sessionOk(e, peer(e, 0), "ACameraCaptureSession_stopRepeating");
            }
        });
        CameraNdkImageSupport.put(svc, symbols, is64Bit, "ACameraCaptureSession_close", new CameraNdkImageSupport.Handler() {
            @Override
            public long handle(Emulator<?> e) {
                stubFree(peer(e, 0));
                return ACAMERA_OK;
            }
        });
    }

    public long managerCreate(Emulator<?> emulator) {
        if (!camerasPresent(emulator)) {
            return 0L;
        }
        MemoryBlock handle = emulator.getMemory().malloc(8, true);
        managers.put(Long.valueOf(handle.getPointer().peer), handle);
        TraceEnvironmentEventSink.emit(emulator, "android_camera",
                "ACameraManager_create",
                "count=" + cameraCount(emulator),
                "json-config", "创建配置的 NDK CameraManager");
        return handle.getPointer().peer;
    }

    public void managerDelete(long peer) {
        MemoryBlock handle = managers.remove(Long.valueOf(peer));
        if (handle != null) {
            handle.free();
        }
    }

    public int getCameraIdList(Emulator<?> emulator, long managerPeer, Pointer listOut) {
        if (!managers.containsKey(Long.valueOf(managerPeer)) || listOut == null
                || !camerasPresent(emulator)) {
            return ACAMERA_ERROR_INVALID_PARAMETER;
        }
        int count = cameraCount(emulator);
        int pointerSize = emulator.getPointerSize();
        MemoryBlock list = emulator.getMemory().malloc(pointerSize * 2, true);
        MemoryBlock extrasOwner = list;
        List<MemoryBlock> extras = new ArrayList<MemoryBlock>();
        extras.add(list);
        if (count > 0) {
            MemoryBlock ptrs = emulator.getMemory().malloc(count * pointerSize, true);
            extras.add(ptrs);
            MemoryBlock strings = emulator.getMemory().malloc(count * 4, true);
            extras.add(strings);
            Pointer stringBase = strings.getPointer();
            int offset = 0;
            for (int i = 0; i < count; i++) {
                String id = Integer.toString(i);
                byte[] utf8 = id.getBytes(StandardCharsets.UTF_8);
                stringBase.write(offset, utf8, 0, utf8.length);
                stringBase.setByte(offset + utf8.length, (byte) 0);
                ptrs.getPointer().setPointer((long) i * pointerSize, stringBase.share(offset));
                offset += utf8.length + 1;
            }
            list.getPointer().setInt(0, count);
            list.getPointer().setPointer(pointerSize, ptrs.getPointer());
        } else {
            list.getPointer().setInt(0, 0);
            list.getPointer().setPointer(pointerSize, null);
        }
        idLists.put(Long.valueOf(list.getPointer().peer), extrasOwner);
        idListExtras.put(Long.valueOf(list.getPointer().peer), extras);
        listOut.setPointer(0, list.getPointer());
        TraceEnvironmentEventSink.emit(emulator, "android_camera",
                "ACameraManager_getCameraIdList",
                "count=" + count,
                "json-config", "读取配置的 NDK 摄像头 id 列表");
        return ACAMERA_OK;
    }

    public void deleteCameraIdList(long listPeer) {
        List<MemoryBlock> extras = idListExtras.remove(Long.valueOf(listPeer));
        idLists.remove(Long.valueOf(listPeer));
        if (extras == null) {
            return;
        }
        for (int i = 0; i < extras.size(); i++) {
            extras.get(i).free();
        }
    }

    public int openCamera(Emulator<?> emulator, long managerPeer, String cameraId, Pointer deviceOut) {
        if (!managers.containsKey(Long.valueOf(managerPeer)) || deviceOut == null
                || cameraId == null || !camerasPresent(emulator)) {
            return ACAMERA_ERROR_INVALID_PARAMETER;
        }
        int id;
        try {
            id = Integer.parseInt(cameraId);
        } catch (NumberFormatException e) {
            return ACAMERA_ERROR_INVALID_PARAMETER;
        }
        if (id < 0 || id >= cameraCount(emulator)) {
            return ACAMERA_ERROR_INVALID_PARAMETER;
        }
        MemoryBlock handle = emulator.getMemory().malloc(8, true);
        byte[] idBytes = cameraId.getBytes(StandardCharsets.UTF_8);
        MemoryBlock idBlock = emulator.getMemory().malloc(idBytes.length + 1, true);
        idBlock.getPointer().write(0, idBytes, 0, idBytes.length);
        idBlock.getPointer().setByte(idBytes.length, (byte) 0);
        devices.put(Long.valueOf(handle.getPointer().peer), new Device(handle, idBlock, id));
        deviceOut.setPointer(0, handle.getPointer());
        TraceEnvironmentEventSink.emit(emulator, "android_camera",
                "ACameraManager_openCamera",
                "cameraId=" + id,
                "json-config", "打开配置的 NDK CameraDevice");
        return ACAMERA_OK;
    }

    public int deviceClose(long devicePeer) {
        Device device = devices.remove(Long.valueOf(devicePeer));
        if (device == null) {
            return ACAMERA_ERROR_INVALID_PARAMETER;
        }
        device.free();
        return ACAMERA_OK;
    }

    public long deviceGetId(long devicePeer) {
        Device device = devices.get(Long.valueOf(devicePeer));
        return device == null ? 0L : device.idBlock.getPointer().peer;
    }

    public int stubCreate(Emulator<?> emulator, Pointer out) {
        if (out == null || !camerasPresent(emulator)) {
            return ACAMERA_ERROR_INVALID_PARAMETER;
        }
        MemoryBlock handle = emulator.getMemory().malloc(8, true);
        stubs.put(Long.valueOf(handle.getPointer().peer), handle);
        out.setPointer(0, handle.getPointer());
        return ACAMERA_OK;
    }

    public void stubFree(long peer) {
        MemoryBlock handle = stubs.remove(Long.valueOf(peer));
        if (handle != null) {
            handle.free();
        }
    }

    public void release() {
        List<Long> deviceKeys = new ArrayList<Long>(devices.keySet());
        for (int i = 0; i < deviceKeys.size(); i++) {
            deviceClose(deviceKeys.get(i).longValue());
        }
        List<Long> listKeys = new ArrayList<Long>(idLists.keySet());
        for (int i = 0; i < listKeys.size(); i++) {
            deleteCameraIdList(listKeys.get(i).longValue());
        }
        List<Long> managerKeys = new ArrayList<Long>(managers.keySet());
        for (int i = 0; i < managerKeys.size(); i++) {
            managerDelete(managerKeys.get(i).longValue());
        }
        List<Long> stubKeys = new ArrayList<Long>(stubs.keySet());
        for (int i = 0; i < stubKeys.size(); i++) {
            stubFree(stubKeys.get(i).longValue());
        }
    }

    int managerCountForTest() {
        return managers.size();
    }

    int deviceCountForTest() {
        return devices.size();
    }

    private int stubOut(Emulator<?> emulator, long ownerPeer, Pointer out,
                        Map<Long, Device> owners) {
        if (!owners.containsKey(Long.valueOf(ownerPeer))) {
            return ACAMERA_ERROR_INVALID_PARAMETER;
        }
        return stubCreate(emulator, out);
    }

    private int sessionOk(Emulator<?> emulator, long sessionPeer, String api) {
        if (!stubs.containsKey(Long.valueOf(sessionPeer))) {
            return ACAMERA_ERROR_INVALID_PARAMETER;
        }
        TraceEnvironmentEventSink.emit(emulator, "android_camera",
                api,
                "images=0",
                "json-config", "配置的 NDK 捕获会话");
        return ACAMERA_OK;
    }

    private static boolean camerasPresent(Emulator<?> emulator) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config != null && config.isAndroidCamerasConfigured();
    }

    private static int cameraCount(Emulator<?> emulator) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config == null || !config.isAndroidCamerasConfigured()
                ? 0 : config.getAndroidCamerasConfig().getCount();
    }

    private static long peer(Emulator<?> emulator, int index) {
        Pointer pointer = emulator.getContext().getPointerArg(index);
        return pointer == null ? 0L : UnidbgPointer.nativeValue(pointer);
    }

    private static String readCString(Pointer pointer) {
        return pointer == null ? null : pointer.getString(0);
    }

    private static final class Device {
        final MemoryBlock handle;
        final MemoryBlock idBlock;
        final int cameraId;

        Device(MemoryBlock handle, MemoryBlock idBlock, int cameraId) {
            this.handle = handle;
            this.idBlock = idBlock;
            this.cameraId = cameraId;
        }

        void free() {
            idBlock.free();
            handle.free();
        }
    }
}
