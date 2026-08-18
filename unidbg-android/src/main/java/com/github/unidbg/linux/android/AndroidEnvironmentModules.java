package com.github.unidbg.linux.android;

import com.github.unidbg.Emulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.virtualmodule.android.AndroidModule;
import com.github.unidbg.virtualmodule.android.CameraNdkModule;
import com.github.unidbg.virtualmodule.android.MediaNdkModule;

/**
 * Registers generic NDK virtual modules when the matching environment node is present.
 * Callers do not special-case a harness: {@code android.sensors} loads {@code libandroid.so},
 * {@code android.drm} or {@code android.cameras.streams} loads {@code libmediandk.so},
 * {@code android.cameras} loads {@code libcamera2ndk.so}.
 */
public final class AndroidEnvironmentModules {

    private AndroidEnvironmentModules() {
    }

    public static void registerConfigured(Emulator<?> emulator, VM vm) {
        if (emulator == null || vm == null) {
            return;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null) {
            return;
        }
        if (config.isAndroidSensorsConfigured()) {
            new AndroidModule(emulator, vm).register(emulator.getMemory());
        }
        if (config.isAndroidDrmConfigured() || MediaNdkModule.hasConfiguredCameraStreams(config)) {
            new MediaNdkModule(emulator, vm).register(emulator.getMemory());
        }
        if (config.isAndroidCamerasConfigured()) {
            new CameraNdkModule(emulator, vm).register(emulator.getMemory());
        }
    }
}
