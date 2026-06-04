package com.github.unidbg;

import com.github.unidbg.arm.ARMEmulator;
import com.github.unidbg.arm.backend.BackendFactory;
import com.github.unidbg.env.TraceEnvironmentConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class EmulatorBuilder<T extends ARMEmulator<?>> {

    protected final boolean is64Bit;

    protected EmulatorBuilder(boolean is64Bit) {
        this.is64Bit = is64Bit;
    }

    protected String processName;

    public EmulatorBuilder<T> setProcessName(String processName) {
        this.processName = processName;
        return this;
    }

    protected File rootDir;

    public EmulatorBuilder<T> setRootDir(File rootDir) {
        this.rootDir = rootDir;
        return this;
    }

    protected TraceEnvironmentConfig environmentConfig;

    public EmulatorBuilder<T> setEnvironmentConfig(TraceEnvironmentConfig environmentConfig) {
        this.environmentConfig = environmentConfig;
        return this;
    }

    public EmulatorBuilder<T> setEnvironmentConfig(File environmentConfigFile) {
        this.environmentConfig = environmentConfigFile == null ? null : TraceEnvironmentConfig.load(environmentConfigFile);
        return this;
    }

    public EmulatorBuilder<T> setEnvironmentConfig(String environmentConfigPath) {
        this.environmentConfig = environmentConfigPath == null ? null : TraceEnvironmentConfig.load(environmentConfigPath);
        return this;
    }

    protected TraceEnvironmentConfig resolveEnvironmentConfig() {
        return environmentConfig != null ? environmentConfig : TraceEnvironmentConfig.fromSystemProperty();
    }

    protected final List<BackendFactory> backendFactories = new ArrayList<>(5);

    public EmulatorBuilder<T> addBackendFactory(BackendFactory backendFactory) {
        this.backendFactories.add(backendFactory);
        return this;
    }

    public abstract T build();

}
