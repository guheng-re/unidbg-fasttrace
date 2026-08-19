package com.github.unidbg.linux.android;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.Arm64Hook;
import com.github.unidbg.arm.ArmHook;
import com.github.unidbg.arm.HookStatus;
import com.github.unidbg.arm.backend.BackendException;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.hook.HookListener;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.memory.SvcMemory;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SystemPropertyHook implements HookListener {

    private static final Logger log = LoggerFactory.getLogger(SystemPropertyHook.class);

    public static final int PROP_VALUE_MAX = 92;

    private final Emulator<?> emulator;

    public SystemPropertyHook(Emulator<?> emulator) {
        this.emulator = emulator;
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null && config.hasAndroidProperties()) {
            this.propertyProvider = new JsonSystemPropertyProvider(emulator, config);
        }
    }

    @Override
    public long hook(SvcMemory svcMemory, String libraryName, String symbolName, final long old) {
        if ("libc.so".equals(libraryName)) {
            if ("__system_property_get".equals(symbolName)) {
                log.debug("Hook {}", symbolName);
                if (emulator.is64Bit()) {
                    return svcMemory.registerSvc(new Arm64Hook() {
                        @Override
                        protected HookStatus hook(Emulator<?> emulator) {
                            RegisterContext context = emulator.getContext();
                            int index = 0;
                            Pointer pointer = context.getPointerArg(index);
                            String key = pointer.getString(0);
                            return __system_property_get(old, key, index);
                        }
                    }).peer;
                } else {
                    return svcMemory.registerSvc(new ArmHook() {
                        @Override
                        protected HookStatus hook(Emulator<?> emulator) {
                            RegisterContext context = emulator.getContext();
                            int index = 0;
                            Pointer pointer = context.getPointerArg(index);
                            String key = pointer.getString(0);
                            return __system_property_get(old, key, index);
                        }
                    }).peer;
                }
            }
            if ("__system_property_read".equals(symbolName)) {
                log.debug("Hook {}", symbolName);
                if (emulator.is64Bit()) {
                    return svcMemory.registerSvc(new Arm64Hook() {
                        @Override
                        protected HookStatus hook(Emulator<?> emulator) {
                            RegisterContext context = emulator.getContext();
                            Pointer pi = context.getPointerArg(0);
                            if (pi == null) {
                                return HookStatus.LR(emulator, 0);
                            }
                            String key = pi.share(PROP_VALUE_MAX + 4).getString(0);
                            return __system_property_read(old, key);
                        }
                    }).peer;
                } else {
                    return svcMemory.registerSvc(new ArmHook() {
                        @Override
                        protected HookStatus hook(Emulator<?> emulator) {
                            RegisterContext context = emulator.getContext();
                            Pointer pi = context.getPointerArg(0);
                            if (pi == null) {
                                return HookStatus.LR(emulator, 0);
                            }
                            String key = pi.share(PROP_VALUE_MAX + 4).getString(0);
                            return __system_property_read(old, key);
                        }
                    }).peer;
                }
            }
            if ("__system_property_find".equals(symbolName)) {
                log.debug("Hook {}", symbolName);
                if (emulator.is64Bit()) {
                    return svcMemory.registerSvc(new Arm64Hook() {
                        @Override
                        protected HookStatus hook(Emulator<?> emulator) {
                            RegisterContext context = emulator.getContext();
                            Pointer name = context.getPointerArg(0);
                            String key = name.getString(0);
                            if (log.isDebugEnabled()) {
                                log.debug("__system_property_find key={}, LR={}", key, context.getLRPointer());
                            }
                            if (log.isTraceEnabled()) {
                                emulator.attach().debug("__system_property_find key=" + key);
                            }
                            if (propertyProvider != null) {
                                Pointer replace = propertyProvider.__system_property_find(key);
                                if (replace != null) {
                                    emitPropertyEvent("__system_property_find", key, propertyProvider.getProperty(key), propertyEventSource());
                                    return HookStatus.LR(emulator, UnidbgPointer.nativeValue(replace));
                                }
                            }
                            emitPropertyEvent("__system_property_find", key, null, "fallback");
                            return HookStatus.RET(emulator, old);
                        }
                    }).peer;
                } else {
                    return svcMemory.registerSvc(new ArmHook() {
                        @Override
                        protected HookStatus hook(Emulator<?> emulator) {
                            RegisterContext context = emulator.getContext();
                            Pointer name = context.getPointerArg(0);
                            String key = name.getString(0);
                            if (log.isDebugEnabled()) {
                                log.debug("__system_property_find key={}, LR={}", key, context.getLRPointer());
                            }
                            if (log.isTraceEnabled()) {
                                emulator.attach().debug("__system_property_find key=" + key);
                            }
                            if (propertyProvider != null) {
                                Pointer replace = propertyProvider.__system_property_find(key);
                                if (replace != null) {
                                    emitPropertyEvent("__system_property_find", key, propertyProvider.getProperty(key), propertyEventSource());
                                    return HookStatus.LR(emulator, UnidbgPointer.nativeValue(replace));
                                }
                            }
                            emitPropertyEvent("__system_property_find", key, null, "fallback");
                            return HookStatus.RET(emulator, old);
                        }
                    }).peer;
                }
            }
        }
        return 0;
    }

    private HookStatus __system_property_read(long old, String key) {
        RegisterContext context = emulator.getContext();
        if (propertyProvider != null) {
            String value = propertyProvider.getProperty(key);
            if (value != null) {
                log.debug("__system_property_read key={}, value={}", key, value);
                // AOSP: int __system_property_read(const prop_info *pi, char *name, char *value)
                // libc __system_property_get and atrace pass name == NULL.
                Pointer namePointer = context.getPointerArg(1);
                if (namePointer != null && key != null) {
                    byte[] keyData = key.getBytes(StandardCharsets.UTF_8);
                    namePointer.write(0, Arrays.copyOf(keyData, keyData.length + 1), 0, keyData.length + 1);
                }
                emitPropertyEvent("__system_property_read", key, value, propertyEventSource());
                Pointer valuePointer = context.getPointerArg(2);
                if (valuePointer == null) {
                    byte[] data = value.getBytes(StandardCharsets.UTF_8);
                    return HookStatus.LR(emulator, data.length);
                }
                return writePropertyValue(valuePointer, key, value);
            }
        }

        log.debug("__system_property_read key={}", key);
        emitPropertyEvent("__system_property_read", key, null, "fallback");
        return HookStatus.RET(emulator, old);
    }

    private HookStatus __system_property_get(long old, String key, int index) {
        RegisterContext context = emulator.getContext();
        if (propertyProvider != null) {
            String value = propertyProvider.getProperty(key);
            if (value != null) {
                log.debug("__system_property_get key={}, value={}", key, value);
                emitPropertyEvent("__system_property_get", key, value, propertyEventSource());
                return writePropertyValue(context.getPointerArg(index + 1), key, value);
            }
        }

        log.debug("__system_property_get key={}", key);
        emitPropertyEvent("__system_property_get", key, null, "fallback");
        return HookStatus.RET(emulator, old);
    }

    private void emitPropertyEvent(String api, String key, String value, String source) {
        String text = value == null ? "key=" + key : key + "=" + value;
        TraceEnvironmentEventSink.emit(emulator, "android_property", api, text, source,
                "读取 Android 设备属性 " + key);
    }

    private String propertyEventSource() {
        return propertyProvider instanceof JsonSystemPropertyProvider ? "json-config" : "fallback";
    }

    private HookStatus writePropertyValue(Pointer pointer, String key, String value) {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        if (data.length >= PROP_VALUE_MAX) {
            throw new BackendException("invalid property value length: key=" + key + ", value=" + value);
        }

        byte[] newData = Arrays.copyOf(data, data.length + 1);
        pointer.write(0, newData, 0, newData.length);
        return HookStatus.LR(emulator, data.length);
    }

    private SystemPropertyProvider propertyProvider;

    public void setPropertyProvider(SystemPropertyProvider propertyProvider) {
        this.propertyProvider = propertyProvider;
    }

    private static class JsonSystemPropertyProvider implements SystemPropertyProvider {

        private final Emulator<?> emulator;
        private final TraceEnvironmentConfig config;
        private final Map<String, MemoryBlock> propertyMap = new HashMap<>();

        private JsonSystemPropertyProvider(Emulator<?> emulator, TraceEnvironmentConfig config) {
            this.emulator = emulator;
            this.config = config;
        }

        @Override
        public String getProperty(String key) {
            return config.getAndroidProperty(key);
        }

        @Override
        public Pointer __system_property_find(String key) {
            String value = getProperty(key);
            if (value == null) {
                return null;
            }
            MemoryBlock block = propertyMap.get(key);
            if (block == null) {
                block = createPropertyBlock(key, value);
                propertyMap.put(key, block);
            }
            return block.getPointer();
        }

        private MemoryBlock createPropertyBlock(String key, String value) {
            byte[] valueData = value.getBytes(StandardCharsets.UTF_8);
            if (valueData.length >= PROP_VALUE_MAX) {
                throw new BackendException("invalid property value length: key=" + key + ", value=" + value);
            }
            byte[] keyData = key.getBytes(StandardCharsets.UTF_8);
            MemoryBlock block = emulator.getMemory().malloc(PROP_VALUE_MAX + 4 + keyData.length + 1, true);
            Pointer pointer = block.getPointer();
            pointer.setInt(0, valueData.length << 24);
            pointer.write(4, Arrays.copyOf(valueData, valueData.length + 1), 0, valueData.length + 1);
            pointer.write(PROP_VALUE_MAX + 4L, Arrays.copyOf(keyData, keyData.length + 1), 0, keyData.length + 1);
            return block;
        }
    }

}
