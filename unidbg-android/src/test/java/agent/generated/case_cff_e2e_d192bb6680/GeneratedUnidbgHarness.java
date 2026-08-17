package agent.generated.case_cff_e2e_d192bb6680;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.Module;
import com.github.unidbg.TraceHook;
import com.github.unidbg.arm.Arm64Svc;
import com.github.unidbg.arm.ArmSvc;
import com.github.unidbg.arm.HookStatus;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.debugger.BreakPointCallback;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.IOResolver;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.hook.HookContext;
import com.github.unidbg.hook.ReplaceCallback;
import com.github.unidbg.hook.xhook.IxHook;
import com.github.unidbg.listener.TraceWriteListener;
import com.github.unidbg.linux.LinuxModule;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.XHookImpl;
import com.github.unidbg.linux.android.dvm.AbstractJni;
import com.github.unidbg.linux.android.dvm.BaseVM;
import com.github.unidbg.linux.android.dvm.DalvikModule;
import com.github.unidbg.linux.android.dvm.DvmClass;
import com.github.unidbg.linux.android.dvm.DvmObject;
import com.github.unidbg.linux.android.dvm.StringObject;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.linux.android.dvm.VarArg;
import com.github.unidbg.linux.file.ByteArrayFileIO;
import com.github.unidbg.linux.file.SimpleFileIO;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.memory.SvcMemory;
import com.github.unidbg.pointer.UnidbgPointer;
import com.sun.jna.Pointer;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GeneratedUnidbgHarness extends AbstractJni implements IOResolver<AndroidFileIO> {

    private static final String APK_FILE_PATH = "";
    private static final String SO_FILE_PATH = "D:\\project\\TraceAIagent_v3\\tests\\case_unidbg_001_CFF\\libs\\arm64-v8a\\libtraceai_case_002.so";
    private static final String PROCESS_NAME = "com.traceai.case002";
    private static final String TRACEAI_RUN_ID = System.getProperty("traceai.run.id", "manual-run");
    private static final String OBSERVATION_EVENTS_PATH = System.getProperty("traceai.observation.events", "");

    private final AndroidEmulator emulator;
    private final VM vm;
    private final Module module;
    private final Memory memory;
    private final DalvikModule dm;
    private final ObservationEventWriter observationEvents;
    private final List<TraceHook> traceHooks = new ArrayList<TraceHook>();

    public GeneratedUnidbgHarness() throws IOException {
        observationEvents = new ObservationEventWriter(OBSERVATION_EVENTS_PATH, TRACEAI_RUN_ID);
        emulator = AndroidEmulatorBuilder.for64Bit()
                .setProcessName(PROCESS_NAME)
                .build();
        memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));
        vm = emulator.createDalvikVM();
        vm.setVerbose(false);
        memory.disableCallInitFunction();
        dm = vm.loadLibrary(new File(SO_FILE_PATH), false);
        module = dm.getModule();
        // 当前 IR 未要求 setJni。
        // 当前 IR 未要求 IOResolver。
        // 当前运行未启用构造期 instrumentation。
        // 当前运行未启用 trace hook。
        try {
            traceAiCallPendingInitFunctions(true);
        } finally {
            memory.setCallInitFunction(true);
        }
        // 当前 IR 未要求 callJNI_OnLoad。
    }

    public String run() {
        try {
        // 当前 IR 未定义对象。



        DvmObject<?> result = vm.resolveClass("com/traceai/case002/NativeEntry").callStaticJniMethodObject(emulator, "process(Ljava/lang/String;)Ljava/lang/String;", new StringObject(vm, "TraceAI case input"));
        return traceAiStringifyResult(result == null ? null : result.getValue());
        } finally {
            stopTraceHooks();
        }
    }

// 当前 IR 未定义 JNI override。

@Override
public FileResult<AndroidFileIO> resolve(Emulator<AndroidFileIO> emulator, String pathname, int oflags) {
    return null;
}

    private void installInstrumentation() {
        // 当前 IR 未启用构造期 instrumentation。
    }

    private void emitObservation(String eventType, String instrumentationId, String payloadJson) {
        observationEvents.emit(eventType, instrumentationId, payloadJson);
    }

    private void close() throws IOException {
        observationEvents.close();
    }

    private void traceAiCallPendingInitFunctions(boolean forceTargetInit) throws IOException {
        List<Module> pendingModules = new ArrayList<Module>(memory.getLoadedModules());
        for (Module pendingModule : pendingModules) {
            if (!(pendingModule instanceof LinuxModule)) {
                continue;
            }
            boolean mustCallInit = pendingModule == module ? forceTargetInit : pendingModule.isForceCallInit();
            traceAiCallInitFunction((LinuxModule) pendingModule, mustCallInit);
        }
    }

    private void traceAiCallInitFunction(LinuxModule pendingModule, boolean mustCallInit) throws IOException {
        try {
            Method method = LinuxModule.class.getDeclaredMethod("callInitFunction", Emulator.class, boolean.class);
            method.setAccessible(true);
            method.invoke(pendingModule, emulator, mustCallInit);
            pendingModule.initFunctionList.clear();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IOException("手动执行 init_array 失败: " + pendingModule.name, cause);
        } catch (ReflectiveOperationException e) {
            throw new IOException("无法访问 init_array 调用入口: " + pendingModule.name, e);
        }
    }

    private static final class ObservationEventWriter implements AutoCloseable {
        private final BufferedWriter writer;
        private final String runId;
        private boolean firstEvent = true;
        private boolean closed = false;
        private long seq;
        private final Map<String, Long> hitIndexes = new HashMap<String, Long>();

        private ObservationEventWriter(String path, String runId) throws IOException {
            this.runId = runId == null || runId.isEmpty() ? "manual-run" : runId;
            if (path == null || path.isEmpty()) {
                this.writer = null;
                return;
            }
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            this.writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8));
            writer.write("{\"schemaVersion\":\"traceai-observation-events/v1\",\"events\":[");
        }

        private synchronized void emit(String eventType, String instrumentationId, String payloadJson) {
            if (writer == null) {
                return;
            }
            try {
                if (!firstEvent) {
                    writer.write(",");
                }
                firstEvent = false;
                long currentSeq = ++seq;
                writer.write("{\"schemaVersion\":\"traceai-observation-event/v1\"");
                writer.write(",\"runId\":");
                writer.write(json(runId));
                writer.write(",\"seq\":");
                writer.write(Long.toString(currentSeq));
                writer.write(",\"eventType\":");
                writer.write(json(eventType));
                writer.write(",\"instrumentationId\":");
                writer.write(json(instrumentationId));
                writer.write(",\"sourceMarker\":");
                writer.write(json("instrumentation:" + instrumentationId));
                writer.write(",\"timeNanos\":");
                writer.write(Long.toString(System.nanoTime()));
                writer.write(",\"thread\":");
                writer.write(json(Thread.currentThread().getName()));
                if (payloadJson != null && !payloadJson.isEmpty()) {
                    writer.write(",");
                    writer.write(payloadJson);
                }
                writer.write("}");
                writer.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public synchronized void close() throws IOException {
            if (writer == null || closed) {
                return;
            }
            writer.write("]}");
            writer.newLine();
            writer.close();
            closed = true;
        }

        private synchronized long nextHitIndex(String instrumentationId) {
            Long current = hitIndexes.get(instrumentationId);
            long next = current == null ? 1L : current.longValue() + 1L;
            hitIndexes.put(instrumentationId, Long.valueOf(next));
            return next;
        }

        private static String json(String value) {
            if (value == null) {
                return "null";
            }
            StringBuilder builder = new StringBuilder(value.length() + 2);
            builder.append('"');
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if (ch == '"' || ch == '\\') {
                    builder.append('\\').append(ch);
                } else if (ch == '\n') {
                    builder.append("\\n");
                } else if (ch == '\r') {
                    builder.append("\\r");
                } else if (ch == '\t') {
                    builder.append("\\t");
                } else if (ch < 0x20) {
                    builder.append(String.format("\\u%04x", (int) ch));
                } else {
                    builder.append(ch);
                }
            }
            builder.append('"');
            return builder.toString();
        }

        private static String hex(byte[] data) {
            StringBuilder builder = new StringBuilder(data.length * 2);
            for (byte b : data) {
                builder.append(String.format("%02x", b & 0xff));
            }
            return builder.toString();
        }

    }

private void startTraceHooks() {
    // 当前 IR 未启用 trace hook。
}

private void stopTraceHooks() {
    for (int i = traceHooks.size() - 1; i >= 0; i--) {
        TraceHook hook = traceHooks.get(i);
        if (hook != null) {
            hook.stopTrace();
        }
    }
    traceHooks.clear();
}

private static String traceAiStringifyResult(Object value) {
    return value == null ? "null" : String.valueOf(value);
}


    public static void main(String[] args) throws Exception {
        GeneratedUnidbgHarness harness = new GeneratedUnidbgHarness();
        try {
            String result = harness.run();
            System.out.println("__TRACEAI_RESULT__=" + result);
        } finally {
            harness.close();
        }
    }
}
