package com.github.unidbg;

import com.github.unidbg.arm.ARMEmulator;
import com.github.unidbg.arm.backend.BackendFactory;
import com.github.unidbg.env.DeviceFingerprintProfile;
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
    protected DeviceFingerprintProfile environmentProfile;

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

    public EmulatorBuilder<T> setEnvironmentProfile(File environmentProfileFile) {
        if (environmentProfileFile == null) {
            return this;
        }
        this.environmentProfile = DeviceFingerprintProfile.load(environmentProfileFile);
        return this;
    }

    public EmulatorBuilder<T> setEnvironmentProfile(String environmentProfilePath) {
        if (environmentProfilePath == null) {
            return this;
        }
        return setEnvironmentProfile(new File(environmentProfilePath));
    }

    protected TraceEnvironmentConfig resolveEnvironmentConfig() {
        if (environmentConfig != null) {
            return environmentConfig;
        }
        if (environmentProfile != null) {
            return environmentProfile.getEnvironmentConfig();
        }
        TraceEnvironmentConfig fromConfig = TraceEnvironmentConfig.fromSystemProperty();
        if (fromConfig != null) {
            return fromConfig;
        }
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.fromSystemProperty();
        return profile == null ? null : profile.getEnvironmentConfig();
    }

    protected final List<BackendFactory> backendFactories = new ArrayList<>(5);

    public EmulatorBuilder<T> addBackendFactory(BackendFactory backendFactory) {
        this.backendFactories.add(backendFactory);
        return this;
    }

    /**
     * Prefer in-tree Unicorn2 when the caller did not pick a backend.
     * Unicorn1 (Maven {@code unicorn_java.dll}) mishandles some ARM64
     * taken {@code B.cond} paths under {@code UC_HOOK_CODE}, which is
     * exactly what full-module {@code traceCode}/{@code traceCodeText} installs.
     */
    protected void addDefaultBackendIfNeeded() {
        if (!backendFactories.isEmpty()) {
            return;
        }
        if ("unicorn1".equalsIgnoreCase(System.getProperty("unidbg.backend"))) {
            return;
        }
        try {
            Class<?> clazz = Class.forName("com.github.unidbg.arm.backend.Unicorn2Factory");
            backendFactories.add((BackendFactory) clazz.getConstructor(boolean.class).newInstance(Boolean.TRUE));
        } catch (Throwable ignored) {
            // unidbg-unicorn2 not on the classpath; BackendFactory falls back to Unicorn1.
        }
    }

    public abstract T build();

}
