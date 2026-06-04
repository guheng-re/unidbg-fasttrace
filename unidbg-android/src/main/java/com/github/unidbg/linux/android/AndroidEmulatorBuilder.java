package com.github.unidbg.linux.android;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.EmulatorBuilder;
import com.github.unidbg.env.TraceEnvironmentConfig;

public class AndroidEmulatorBuilder extends EmulatorBuilder<AndroidEmulator> {

    public static AndroidEmulatorBuilder for32Bit() {
        return new AndroidEmulatorBuilder(false);
    }

    public static AndroidEmulatorBuilder for64Bit() {
        return new AndroidEmulatorBuilder(true);
    }

    protected AndroidEmulatorBuilder(boolean is64Bit) {
        super(is64Bit);
    }

    @Override
    public AndroidEmulator build() {
        TraceEnvironmentConfig environmentConfig = resolveEnvironmentConfig();
        return is64Bit ? new AndroidARM64Emulator(processName, rootDir, backendFactories, environmentConfig) : new AndroidARMEmulator(processName, rootDir, backendFactories, environmentConfig);
    }

}
