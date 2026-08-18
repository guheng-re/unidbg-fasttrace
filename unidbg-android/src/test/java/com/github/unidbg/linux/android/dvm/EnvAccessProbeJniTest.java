package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.trace.EnvAccessProbe;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unconfigured fingerprint JNI / file reads emit {@code env_probe} when the probe is on.
 */
public class EnvAccessProbeJniTest {

    @After
    public void resetProbe() {
        EnvAccessProbe.clearOverride();
    }

    @Test
    public void testUnconfiguredBuildAndProcEmitProbe() throws Exception {
        EnvAccessProbe.setEnabled(true);
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{\"android\":{}}");
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        CapturingSink sink = new CapturingSink();
        try {
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            BaseVM baseVM = (BaseVM) vm;
            DvmClass build = vm.resolveClass("android/os/Build");
            try {
                jni.getStaticObjectField(baseVM, build, "android/os/Build->MODEL:Ljava/lang/String;");
                fail();
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage().contains("MODEL"));
            }
            emulator.getFileSystem().open("/proc/cpuinfo", 0);

            assertTrue(hasProbe(sink, "android/os/Build->MODEL:Ljava/lang/String;"));
            assertTrue(hasProbe(sink, "open(\"/proc/cpuinfo\")"));
        } finally {
            TraceEnvironmentEventSink.unregister(emulator, sink);
            emulator.close();
        }
    }

    @Test
    public void testProbeCanBeDisabled() throws Exception {
        EnvAccessProbe.setEnabled(false);
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{\"android\":{}}");
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        CapturingSink sink = new CapturingSink();
        try {
            TraceEnvironmentEventSink.register(emulator, sink);
            emulator.getFileSystem().open("/proc/cpuinfo", 0);
            assertFalse(hasProbe(sink, "open(\"/proc/cpuinfo\")"));
        } finally {
            TraceEnvironmentEventSink.unregister(emulator, sink);
            emulator.close();
        }
    }

    private static boolean hasProbe(CapturingSink sink, String apiFragment) {
        for (int i = 0; i < sink.events.size(); i++) {
            CapturedEvent e = sink.events.get(i);
            if (EnvAccessProbe.KIND.equals(e.kind) && e.api != null && e.api.contains(apiFragment)) {
                return true;
            }
        }
        return false;
    }

    private static final class CapturedEvent {
        final String kind;
        final String api;

        CapturedEvent(String kind, String api) {
            this.kind = kind;
            this.api = api;
        }
    }

    private static final class CapturingSink implements TraceEnvironmentEventSink {
        final List<CapturedEvent> events = new ArrayList<CapturedEvent>();

        @Override
        public void emitEnvironmentEvent(String kind, String api, Object value, String source, String note) {
            events.add(new CapturedEvent(kind, api));
        }
    }
}
