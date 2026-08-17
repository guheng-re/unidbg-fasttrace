package com.github.unidbg.virtualmodule.android;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.Arm64Svc;
import com.github.unidbg.arm.ArmSvc;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.arm.backend.BackendException;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.dvm.DvmObject;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.linux.android.dvm.api.Asset;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.memory.SvcMemory;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.github.unidbg.virtualmodule.VirtualModule;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AndroidModule extends VirtualModule<VM> {

    private static final Logger log = LoggerFactory.getLogger(AndroidModule.class);

    public AndroidModule(Emulator<?> emulator, VM vm) {
        super(emulator, vm, "libandroid.so");
    }

    @Override
    protected void onInitialize(Emulator<?> emulator, final VM vm, Map<String, UnidbgPointer> symbols) {
        boolean is64Bit = emulator.is64Bit();
        SvcMemory svcMemory = emulator.getSvcMemory();
        symbols.put("AAssetManager_fromJava", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return fromJava(emulator, vm);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return fromJava(emulator, vm);
            }
        }));
        symbols.put("AAssetManager_open", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return open(emulator, vm, assetMap);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return open(emulator, vm, assetMap);
            }
        }));
        symbols.put("AAsset_close", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return close(emulator, vm);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return close(emulator, vm);
            }
        }));
        symbols.put("AAsset_getBuffer", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return getBuffer(emulator, vm);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return getBuffer(emulator, vm);
            }
        }));
        symbols.put("AAsset_getLength", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return getLength(emulator, vm);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return getLength(emulator, vm);
            }
        }));
        symbols.put("AAsset_read", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return read(emulator, vm);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return read(emulator, vm);
            }
        }));

        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null && config.isAndroidSensorsConfigured()) {
            registerSensorSymbols(emulator, svcMemory, symbols, is64Bit);
        }
    }

    private MemoryBlock sensorManagerHandle;
    private MemoryBlock sensorArena;

    private void registerSensorSymbols(Emulator<?> emulator, SvcMemory svcMemory,
                                       Map<String, UnidbgPointer> symbols, boolean is64Bit) {
        symbols.put("ASensorManager_getInstance", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return getSensorManager(emulator);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return getSensorManager(emulator);
            }
        }));
        symbols.put("ASensorManager_getInstanceForPackage", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return getSensorManager(emulator);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return getSensorManager(emulator);
            }
        }));
        symbols.put("ASensorManager_getSensorList", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return getSensorList(emulator);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return getSensorList(emulator);
            }
        }));
        symbols.put("ASensorManager_getDefaultSensor", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return getDefaultSensor(emulator);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return getDefaultSensor(emulator);
            }
        }));
        symbols.put("ASensor_getName", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return sensorGetName(emulator);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return sensorGetName(emulator);
            }
        }));
        symbols.put("ASensor_getVendor", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return sensorGetVendor(emulator);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return sensorGetVendor(emulator);
            }
        }));
        symbols.put("ASensor_getType", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return sensorGetType(emulator);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return sensorGetType(emulator);
            }
        }));
        symbols.put("ASensor_getResolution", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return sensorGetResolutionBits(emulator);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return sensorGetResolutionBits(emulator);
            }
        }));
    }

    public long getSensorManager(Emulator<?> emulator) {
        if (sensorManagerHandle == null) {
            sensorManagerHandle = emulator.getMemory().malloc(16, true);
        }
        return sensorManagerHandle.getPointer().peer;
    }

    public int getSensorList(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        Pointer listOut = context.getPointerArg(1);
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null || !config.isAndroidSensorsConfigured()) {
            if (listOut != null) {
                listOut.setPointer(0, null);
            }
            return 0;
        }
        List<UnidbgPointer> sensors = ensureSensorArena(emulator, config);
        if (listOut != null) {
            listOut.setPointer(0, sensors.isEmpty() ? null : sensorListBase);
        }
        TraceEnvironmentEventSink.emit(emulator, "android_sensor",
                "ASensorManager_getSensorList",
                "count=" + sensors.size(),
                "json-config",
                "读取配置的 NDK 传感器列表");
        return sensors.size();
    }

    long getDefaultSensor(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        int type = context.getIntArg(1);
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null || !config.isAndroidSensorsConfigured()) {
            return 0L;
        }
        List<UnidbgPointer> sensors = ensureSensorArena(emulator, config);
        TraceEnvironmentConfig.AndroidSensorsConfig sensorsConfig = config.getAndroidSensorsConfig();
        for (int i = 0; i < sensors.size(); i++) {
            int sensorType = sensors.get(i).getInt(0);
            if (sensorType == type) {
                TraceEnvironmentEventSink.emit(emulator, "android_sensor",
                        "ASensorManager_getDefaultSensor",
                        "sensorType=" + type + ",result=" + type,
                        "json-config",
                        "读取配置的 NDK 默认传感器");
                return sensors.get(i).peer;
            }
        }
        TraceEnvironmentEventSink.emit(emulator, "android_sensor",
                "ASensorManager_getDefaultSensor",
                "sensorType=" + type + ",result=null",
                "json-config",
                "读取配置的 NDK 默认传感器");
        return 0L;
    }

    long sensorGetName(Emulator<?> emulator) {
        Pointer sensor = emulator.getContext().getPointerArg(0);
        if (sensor == null) {
            return 0L;
        }
        Pointer name = sensor.getPointer(nameOffset(emulator));
        return name == null ? 0L : UnidbgPointer.nativeValue(name);
    }

    long sensorGetVendor(Emulator<?> emulator) {
        Pointer sensor = emulator.getContext().getPointerArg(0);
        if (sensor == null) {
            return 0L;
        }
        Pointer vendor = sensor.getPointer(vendorOffset(emulator));
        return vendor == null ? 0L : UnidbgPointer.nativeValue(vendor);
    }

    long sensorGetType(Emulator<?> emulator) {
        Pointer sensor = emulator.getContext().getPointerArg(0);
        return sensor == null ? 0L : sensor.getInt(0) & 0xffffffffL;
    }

    /**
     * {@code ASensor_getResolution} is an AAPCS float: write {@code S0}/{@code Q0}
     * (ARM64) or {@code s0} (ARM32). {@code X0}/{@code R0} still receive the IEEE bits
     * because the SVC dispatcher only stores the long return.
     */
    public long sensorGetResolutionBits(Emulator<?> emulator) {
        Pointer sensor = emulator.getContext().getPointerArg(0);
        if (sensor == null) {
            writeFloatReturn(emulator, 0f);
            return 0L;
        }
        float resolution = sensor.getFloat(8);
        writeFloatReturn(emulator, resolution);
        return Float.floatToIntBits(resolution) & 0xffffffffL;
    }

    static void writeFloatReturn(Emulator<?> emulator, float value) {
        if (emulator == null) {
            return;
        }
        Backend backend = emulator.getBackend();
        int bits = Float.floatToIntBits(value);
        byte[] vector = new byte[16];
        vector[0] = (byte) bits;
        vector[1] = (byte) (bits >>> 8);
        vector[2] = (byte) (bits >>> 16);
        vector[3] = (byte) (bits >>> 24);
        if (emulator.is64Bit()) {
            backend.reg_write(Arm64Const.UC_ARM64_REG_S0, bits & 0xffffffffL);
            backend.reg_write_vector(Arm64Const.UC_ARM64_REG_Q0, vector);
        } else {
            backend.reg_write(ArmConst.UC_ARM_REG_S0, bits);
            backend.reg_write_vector(ArmConst.UC_ARM_REG_D0, vector);
        }
    }

    private UnidbgPointer sensorListBase;
    private final List<UnidbgPointer> cachedSensors = new ArrayList<UnidbgPointer>();

    private UnidbgPointer sensorListBase(Emulator<?> emulator) {
        return sensorListBase;
    }

    private List<UnidbgPointer> ensureSensorArena(Emulator<?> emulator, TraceEnvironmentConfig config) {
        if (sensorArena != null) {
            return cachedSensors;
        }
        TraceEnvironmentConfig.AndroidSensorsConfig sensors = config.getAndroidSensorsConfig();
        List<Integer> types = sensors.getTypes();
        boolean is64 = emulator.is64Bit();
        int ptrSize = pointerSize(emulator);
        int sensorSize = is64 ? 32 : 20;
        int n = types.size();
        int stringsBytes = 0;
        List<byte[]> names = new ArrayList<byte[]>(n);
        List<byte[]> vendors = new ArrayList<byte[]>(n);
        for (int i = 0; i < n; i++) {
            int type = types.get(i).intValue();
            String name = sensors.isNameConfigured(type) ? sensors.getName(type) : ("sensor-" + type);
            String vendor = sensors.isVendorConfigured(type) ? sensors.getVendor(type) : "";
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            byte[] vendorBytes = vendor.getBytes(StandardCharsets.UTF_8);
            names.add(nameBytes);
            vendors.add(vendorBytes);
            stringsBytes += nameBytes.length + 1 + vendorBytes.length + 1;
        }
        int listBytes = n * ptrSize;
        int sensorsBytes = n * sensorSize;
        int total = listBytes + sensorsBytes + stringsBytes + 16;
        sensorArena = emulator.getMemory().malloc(Math.max(total, 16), true);
        UnidbgPointer base = sensorArena.getPointer();
        sensorListBase = base;
        int stringCursor = listBytes + sensorsBytes;
        cachedSensors.clear();
        for (int i = 0; i < n; i++) {
            int type = types.get(i).intValue();
            UnidbgPointer sensor = base.share(listBytes + (long) i * sensorSize, sensorSize);
            sensor.setInt(0, type);
            int minDelay = sensors.isMinDelayMicrosConfigured(type)
                    ? sensors.getMinDelayMicros(type).intValue() : 0;
            sensor.setInt(4, minDelay);
            float resolution = sensors.isResolutionConfigured(type)
                    ? sensors.getResolution(type).floatValue() : 0f;
            sensor.setFloat(8, resolution);
            byte[] nameBytes = names.get(i);
            byte[] vendorBytes = vendors.get(i);
            UnidbgPointer namePtr = base.share(stringCursor, nameBytes.length + 1L);
            namePtr.write(0, nameBytes, 0, nameBytes.length);
            namePtr.setByte(nameBytes.length, (byte) 0);
            stringCursor += nameBytes.length + 1;
            UnidbgPointer vendorPtr = base.share(stringCursor, vendorBytes.length + 1L);
            vendorPtr.write(0, vendorBytes, 0, vendorBytes.length);
            vendorPtr.setByte(vendorBytes.length, (byte) 0);
            stringCursor += vendorBytes.length + 1;
            sensor.setPointer(nameOffset(emulator), namePtr);
            sensor.setPointer(vendorOffset(emulator), vendorPtr);
            base.setPointer((long) i * ptrSize, sensor);
            cachedSensors.add(sensor);
        }
        return cachedSensors;
    }

    private static int pointerSize(Emulator<?> emulator) {
        return emulator.is64Bit() ? 8 : 4;
    }

    private static int nameOffset(Emulator<?> emulator) {
        return emulator.is64Bit() ? 16 : 12;
    }

    private static int vendorOffset(Emulator<?> emulator) {
        return emulator.is64Bit() ? 24 : 16;
    }

    private static long fromJava(Emulator<?> emulator, VM vm) {
        RegisterContext context = emulator.getContext();
        Pointer env = context.getPointerArg(0);
        UnidbgPointer assetManager = context.getPointerArg(1);
        DvmObject<?> obj = vm.getObject(assetManager.toIntPeer());
        if (log.isDebugEnabled()) {
            log.debug("AAssetManager_fromJava env={}, assetManager={}, LR={}", env, obj.getObjectType(), context.getLRPointer());
        }
        return assetManager.peer;
    }

    private final Map<String, byte[]> assetMap = new HashMap<>(1);

    public void addAsset(String name, byte[] bytes) {
        assetMap.put(name, bytes);
    }

    private static long open(Emulator<?> emulator, VM vm, Map<String, byte[]> assetMap) {
        RegisterContext context = emulator.getContext();
        Pointer amgr = context.getPointerArg(0);
        String filename = context.getPointerArg(1).getString(0);
        int mode = context.getIntArg(2);
        if (log.isDebugEnabled()) {
            log.debug("AAssetManager_open amgr={}, filename={}, mode={}, LR={}", amgr, filename, mode, context.getLRPointer());
        }
        final int AASSET_MODE_UNKNOWN = 0;
        final int AASSET_MODE_RANDOM = 1;
        final int AASSET_MODE_STREAMING = 2;
        final int AASSET_MODE_BUFFER = 3;
        if (mode == AASSET_MODE_STREAMING || AASSET_MODE_BUFFER == mode ||
                mode == AASSET_MODE_UNKNOWN || mode == AASSET_MODE_RANDOM) {
            byte[] data = assetMap.containsKey(filename) ? assetMap.get(filename) : vm.openAsset(filename);
            if (data == null) {
                return 0L;
            }
            Asset asset = new Asset(vm, filename);
            asset.open(emulator, data);
            return vm.addLocalObject(asset);
        }
        throw new BackendException("filename=" + filename + ", mode=" + mode + ", LR=" + context.getLRPointer());
    }

    private static long close(Emulator<?> emulator, VM vm) {
        RegisterContext context = emulator.getContext();
        UnidbgPointer pointer = context.getPointerArg(0);
        Asset asset = vm.getObject(pointer.toIntPeer());
        asset.close();
        if (log.isDebugEnabled()) {
            log.debug("AAsset_close pointer={}, LR={}", pointer, context.getLRPointer());
        }
        return 0;
    }

    private static long getBuffer(Emulator<?> emulator, VM vm) {
        RegisterContext context = emulator.getContext();
        UnidbgPointer pointer = context.getPointerArg(0);
        Asset asset = vm.getObject(pointer.toIntPeer());
        UnidbgPointer buffer = asset.getBuffer();
        if (log.isDebugEnabled()) {
            log.debug("AAsset_getBuffer pointer={}, buffer={}, LR={}", pointer, buffer, context.getLRPointer());
        }
        return buffer.peer;
    }

    private static long getLength(Emulator<?> emulator, VM vm) {
        RegisterContext context = emulator.getContext();
        UnidbgPointer pointer = context.getPointerArg(0);
        Asset asset = vm.getObject(pointer.toIntPeer());
        int length = asset.getLength();
        if (log.isDebugEnabled()) {
            log.debug("AAsset_getLength pointer={}, length={}, LR={}", pointer, length, context.getLRPointer());
        }
        return length;
    }

    private static long read(Emulator<?> emulator, VM vm) {
        RegisterContext context = emulator.getContext();
        UnidbgPointer pointer = context.getPointerArg(0);
        Pointer buf = context.getPointerArg(1);
        int count = context.getIntArg(2);
        Asset asset = vm.getObject(pointer.toIntPeer());
        byte[] bytes = asset.read(count);
        if (log.isDebugEnabled()) {
            log.debug("AAsset_read pointer={}, buf={}, count={}, LR={}", pointer, buf, count, context.getLRPointer());
        }
        buf.write(0, bytes, 0, bytes.length);
        return bytes.length;
    }

}
