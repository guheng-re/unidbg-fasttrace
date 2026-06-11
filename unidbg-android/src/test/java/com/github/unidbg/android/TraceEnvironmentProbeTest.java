package com.github.unidbg.android;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Module;
import com.github.unidbg.TraceHook;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.virtualmodule.android.SystemProperties;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class TraceEnvironmentProbeTest {

    private static final int REPORT_SIZE = 16 * 1024;
    private static final File PROJECT_ROOT = findProjectRoot();
    private static final File CONFIG_FILE = new File(PROJECT_ROOT, "example/trace-env.example.json");

    @Test
    public void testTraceEnvironmentProbe32() throws Exception {
        String report = runProbe(false, "armeabi-v7a");
        assertCommonReport(report);
        assertContains(report, "uname.machine=armv7l");
    }

    @Test
    public void testTraceEnvironmentProbe64() throws Exception {
        String report = runProbe(true, "arm64-v8a");
        assertCommonReport(report);
        assertContains(report, "uname.machine=aarch64");
    }

    @Test
    public void testTraceEnvironmentSidecar64() throws Exception {
        File traceFile = new File(PROJECT_ROOT, "target/trace-env-sidecar-test.log");
        deleteIfExists(traceFile);
        deleteIfExists(new File(traceFile.getAbsolutePath() + ".env.jsonl"));

        String report = runProbe(true, "arm64-v8a", traceFile);
        assertCommonReport(report);

        File sidecarFile = new File(traceFile.getAbsolutePath() + ".env.jsonl");
        assertTrue("missing trace file: " + traceFile.getAbsolutePath(), traceFile.isFile());
        assertTrue("missing sidecar file: " + sidecarFile.getAbsolutePath(), sidecarFile.isFile());

        String sidecar = readText(sidecarFile);
        assertContains(sidecar, "\"kind\":\"process_identity\"");
        assertContains(sidecar, "\"kind\":\"time\"");
        assertContains(sidecar, "\"kind\":\"random\"");
        assertContains(sidecar, "\"kind\":\"linux_identity\"");
        assertContains(sidecar, "\"kind\":\"linux_file\"");
        assertContains(sidecar, "\"kind\":\"android_property\"");
        assertContains(sidecar, "\"source\":\"json-config\"");
        assertContains(sidecar, "libtrace_env_probe.so");

        String traceNeedle = firstTargetTraceNeedle(sidecar);
        assertTrue("sidecar target traceNeedle missing\n" + sidecar, traceNeedle != null && !traceNeedle.isEmpty());
        assertContains(readText(traceFile), traceNeedle);
    }

    private static String runProbe(boolean is64Bit, String abi) throws Exception {
        return runProbe(is64Bit, abi, null);
    }

    private static String runProbe(boolean is64Bit, String abi, File traceFile) throws Exception {
        File library = new File(PROJECT_ROOT, "test/libs/" + abi + "/libtrace_env_probe.so");
        assertTrue("missing probe library, run test/build.ps1 first: " + library.getAbsolutePath(), library.isFile());
        assertTrue("missing environment config: " + CONFIG_FILE.getAbsolutePath(), CONFIG_FILE.isFile());

        AndroidEmulator emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                .setEnvironmentConfig(CONFIG_FILE)
                .build();
        MemoryBlock output = null;
        TraceHook traceHook = null;
        try {
            Memory memory = emulator.getMemory();
            memory.setLibraryResolver(new AndroidResolver(23));
            new SystemProperties(emulator, null).register(memory);

            Module module = emulator.loadLibrary(library, true);
            if (traceFile != null) {
                File parent = traceFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    assertTrue("create trace parent failed: " + parent.getAbsolutePath(), parent.mkdirs());
                }
                traceHook = emulator.traceCodeText(new String[]{"libtrace_env_probe.so"}, traceFile.getAbsolutePath());
            }
            output = memory.malloc(REPORT_SIZE, true);
            Number ret = module.callFunction(emulator, "trace_env_probe", output.getPointer(), REPORT_SIZE);
            assertTrue("trace_env_probe failed: " + ret, ret.intValue() > 0);
            if (traceHook != null) {
                traceHook.stopTrace();
                traceHook = null;
            }

            String report = output.getPointer().getString(0);
            System.out.println("trace_env_probe " + abi + " report:\n" + report);
            return report;
        } finally {
            if (traceHook != null) {
                traceHook.stopTrace();
            }
            if (output != null) {
                output.free();
            }
            emulator.close();
        }
    }

    private static void assertCommonReport(String report) {
        assertContains(report, "process.pid=12345");
        assertContains(report, "process.ppid=1");
        assertContains(report, "process.tid=12345");
        assertContains(report, "process.uid=1000");
        assertContains(report, "process.euid=1000");
        assertContains(report, "process.gid=1000");
        assertContains(report, "process.egid=1000");
        assertContains(report, "time.gettimeofday.sec=1718000000");
        assertContains(report, "time.gettimeofday.usec=0");
        assertContains(report, "time.clock_realtime.sec=1718000000");
        assertContains(report, "time.clock_monotonic.sec=123");
        assertContains(report, "time.clock_monotonic.nsec=456789000");
        assertContains(report, "random.getrandom=00112233445566778899aabbccddeeff");
        assertContains(report, "random.random=000000f0");
        assertContains(report, "random.urandom=010000f1");
        assertContains(report, "random.srandom=020000f2");
        assertContains(report, "uname.sysname=Linux");
        assertContains(report, "uname.release=5.4.210-qgki-g991c3066d5a8");
        assertContains(report, "file.proc_cpuinfo=Processor\\t: AArch64 Processor rev 2 (aarch64)\\nHardware\\t: raven\\n");
        assertContains(report, "file.proc_meminfo=MemTotal:        4096000 kB\\nMemFree:          512000 kB\\n");
        assertContains(report, "file.proc_version=Linux version 5.4.210-qgki-g991c3066d5a8\\n");
        assertContains(report, "property.ro.hardware=raven");
        assertContains(report, "property.ro.build.version.sdk=31");
        assertContains(report, "property.ro.product.model=Pixel 6");
        assertContains(report, "property.find.ro.build.version.sdk=1");
        assertContains(report, "property.callback.name=ro.build.version.sdk");
        assertContains(report, "property.callback.value=31");
    }

    private static void assertContains(String report, String expected) {
        assertTrue("missing expected text: " + expected + "\nreport:\n" + report, report.contains(expected));
    }

    private static String firstTargetTraceNeedle(String sidecar) {
        for (String line : sidecar.split("\\R")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            JSONObject object = JSON.parseObject(line);
            JSONObject target = object.getJSONObject("target");
            if (target != null) {
                return target.getString("traceNeedle");
            }
        }
        return null;
    }

    private static String readText(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void deleteIfExists(File file) throws IOException {
        if (file.isFile()) {
            Files.delete(file.toPath());
        }
    }

    private static File findProjectRoot() {
        File dir = new File("").getAbsoluteFile();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (new File(dir, "example/trace-env.example.json").isFile() && new File(dir, "test/Android.mk").isFile()) {
                return dir;
            }
            dir = dir.getParentFile();
        }
        throw new IllegalStateException("cannot find unidbg-fasttrace project root from " + new File("").getAbsolutePath());
    }
}
