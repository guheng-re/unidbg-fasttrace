package com.github.unidbg.trace;

import com.github.unidbg.Emulator;
import com.github.unidbg.env.TraceEnvironmentConfig;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public interface TraceEnvironmentEventSink {

    final class Holder {
        private static final Map<Emulator<?>, TraceEnvironmentEventSink> SINKS =
                Collections.synchronizedMap(new WeakHashMap<Emulator<?>, TraceEnvironmentEventSink>());

        private Holder() {
        }
    }

    static void register(Emulator<?> emulator, TraceEnvironmentEventSink sink) {
        if (emulator == null || sink == null) {
            return;
        }
        Holder.SINKS.put(emulator, sink);
    }

    static void unregister(Emulator<?> emulator, TraceEnvironmentEventSink sink) {
        if (emulator == null) {
            return;
        }
        synchronized (Holder.SINKS) {
            TraceEnvironmentEventSink current = Holder.SINKS.get(emulator);
            if (current == sink || sink == null) {
                Holder.SINKS.remove(emulator);
            }
        }
    }

    static TraceEnvironmentEventSink current(Emulator<?> emulator) {
        return emulator == null ? null : Holder.SINKS.get(emulator);
    }

    static void emit(Emulator<?> emulator, String kind, String api, Object value, String source, String note) {
        if ("json-config".equals(source)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
            if (config != null && config.isProfileLoaded()) {
                source = "profile-json";
            }
        }
        EnvAccessProbe.logEmit(kind, api, value, source, note);
        TraceEnvironmentEventSink sink = current(emulator);
        if (sink == null) {
            return;
        }
        try {
            sink.emitEnvironmentEvent(kind, api, value, source, note);
        } catch (Throwable ignored) {
        }
    }

    void emitEnvironmentEvent(String kind, String api, Object value, String source, String note);
}
