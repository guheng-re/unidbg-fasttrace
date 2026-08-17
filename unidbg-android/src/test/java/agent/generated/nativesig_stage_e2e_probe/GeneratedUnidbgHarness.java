package agent.generated.nativesig_stage_e2e_probe;

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

    private static final String APK_FILE_PATH = "D:\\project\\TraceAIagent_v3\\tests\\nativeSig\\滴滴车主.apk";
    private static final String SO_FILE_PATH = "D:\\project\\TraceAIagent_v3\\tests\\nativeSig\\libdidiwsg.so";
    private static final String PROCESS_NAME = "com.sdu.didi.gsui";
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
        vm = emulator.createDalvikVM(new File(APK_FILE_PATH));
        vm.setVerbose(false);
        memory.disableCallInitFunction();
        dm = vm.loadLibrary(new File(SO_FILE_PATH), false);
        module = dm.getModule();
        vm.setJni(this);
        emulator.getSyscallHandler().addIOResolver(this);
        // 当前运行未启用构造期 instrumentation。
        // 当前运行未启用 trace hook。
        try {
            traceAiCallPendingInitFunctions(true);
        } finally {
            memory.setCallInitFunction(true);
        }
        dm.callJNI_OnLoad(emulator);
    }

    public String run() {
        try {
        DvmObject<?> context = vm.resolveClass("android.content.Context").newObject(null);
        DvmObject<?> object = vm.resolveClass("com.didi.security.wireless.SecurityLib").newObject(null);
        StringObject nativeUpdate_str = new StringObject(vm, "0");
        StringObject nativeUpdate2_str = new StringObject(vm, "01q8gLV04Gl/etNrf+xjq1Uf+HQJ5/R+GodTyQzhxNw0eq5OQDEqgcw93k5GLcEwFJRpRijzUcqbefvO1RiVGFSaHKJGzg9C6tNOLEWvqqHJqnPudf/u7rMZgwFTW+QoxBT7bOkucwe1PPYZF0p908MsBdrOjFRyVUdWkQ5VUojbS&&sYEnzyF4+Zoq2t2fsqp0a3pv7kCDvheCCjivJrWpSNk");
        StringObject nativeCollect_str = new StringObject(vm, "hd.xiaojukeji.com/d");
        long arg1 = 1744781882714L;
        String arg2 = "18912346543";
        byte[] arg3 = new byte[] {
                118, 49, 46, 48, 46, 48, 105, 112, 118, 54, 49, 54, 56, 54, 102, 55,
                51, 55, 52, 55, 51, 51, 100, 54, 56, 54, 52, 50, 101, 55, 56, 54,
                57, 54, 49, 54, 102, 54, 97, 55, 53, 54, 98, 54, 53, 54, 97, 54,
                57, 50, 101, 54, 51, 54, 102, 54, 100, 50, 53, 51, 50, 51, 48, 54,
                100, 55, 51, 54, 55, 54, 55, 54, 49, 55, 52, 54, 53, 50, 101, 55,
                56, 54, 57, 54, 49, 54, 102, 54, 97, 55, 53, 54, 98, 54, 53, 54,
                97, 54, 57, 50, 101, 54, 51, 54, 102, 54, 100, 50, 53, 51, 50, 51,
                48, 54, 102, 54, 54, 54, 54, 54, 99, 54, 57, 54, 101, 54, 53, 50,
                100, 55, 48, 54, 98, 54, 55, 50, 101, 54, 52, 54, 57, 54, 52, 54,
                57, 50, 101, 54, 51, 54, 101, 50, 53, 51, 50, 51, 48, 54, 53, 55,
                48, 54, 49, 55, 51, 55, 51, 55, 48, 54, 102, 55, 50, 55, 52, 50,
                101, 54, 52, 54, 57, 54, 52, 54, 57, 55, 52, 54, 49, 55, 56, 54,
                57, 50, 101, 54, 51, 54, 102, 54, 100, 50, 101, 54, 51, 54, 101
        };

        object.callJniMethodObject(emulator, "nativeInit(Landroid/content/Context;)I", context);
        object.callJniMethodObject(emulator, "nativeUpdate(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", nativeUpdate_str, null, null, null);
        object.callJniMethodObject(emulator, "nativeUpdate2(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", null, null, null, nativeUpdate2_str);
        object.callJniMethodObject(emulator, "nativeCollect(Ljava/lang/String;)Ljava/lang/String;", nativeCollect_str);

        DvmObject<?> object1 = object.callJniMethodObject(emulator, "nativeSig(Landroid/content/Context;JLjava/lang/String;[B)Ljava/lang/String;", context, arg1, arg2, arg3);
        return traceAiStringifyResult(object1 == null ? null : object1.getValue());
        } finally {
            stopTraceHooks();
        }
    }

@Override
public DvmObject<?> callObjectMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
    if (signature.equals("android/content/Context->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;")) {
        return vm.resolveClass("android/content/SharedPreferences").newObject(varArg.getObjectArg(0));
    }
    if (signature.equals("android/content/SharedPreferences->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")) {
        return new StringObject(vm, "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");
    }
    if (signature.equals("android/content/Context->getPackageCodePath()Ljava/lang/String;")) {
        return new StringObject(vm, "/data/app/com.sdu.didi.gsui-mGlj-PzyfIpeLiDCnY-Qcw==/base.apk");
    }
    if (signature.equals("android/content/Context->getFilesDir()Ljava/io/File;")) {
        return vm.resolveClass("java/io/File").newObject("/data/data/com.sdu.didi.gsui/files/");
    }
    if (signature.equals("java/io/File->getCanonicalPath()Ljava/lang/String;")) {
        return new StringObject(vm, "/data/data/com.sdu.didi.gsui/files");
    }
    return super.callObjectMethod(vm, dvmObject, signature, varArg);
}

@Override
public DvmObject<?> callStaticObjectMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
    if (signature.equals("com/didi/security/wireless/SecurityLib->getFeature(I)Ljava/lang/Object;") && varArg.getIntArg(0) == 8197) {
        return new StringObject(vm, "com.sdu.didi.gsui");
    }
    if (signature.equals("com/didi/security/wireless/SecurityLib->getFeature(I)Ljava/lang/Object;") && varArg.getIntArg(0) == 8198) {
        return new StringObject(vm, "9.0.14");
    }
    if (signature.equals("com/didi/security/wireless/SecurityLib->getFeature(I)Ljava/lang/Object;") && varArg.getIntArg(0) == 8195) {
        return new StringObject(vm, "Android");
    }
    if (signature.equals("com/didi/security/wireless/SecurityLib->getFeature(I)Ljava/lang/Object;") && varArg.getIntArg(0) == 8196) {
        return new StringObject(vm, "10");
    }
    if (signature.equals("java/lang/System->getProperty(Ljava/lang/String;)Ljava/lang/String;")) {
        return null;
    }
    if (signature.equals("android/os/ServiceManager->getService(Ljava/lang/String;)Landroid/os/IBinder;")) {
        return vm.resolveClass("android/os/IBinder").newObject(null);
    }
    return super.callStaticObjectMethod(vm, dvmClass, signature, varArg);
}

@Override
public boolean callStaticBooleanMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
    if (signature.equals("com/didi/security/wireless/SecurityLib->ApolloGetToggle(Landroid/content/Context;Ljava/lang/String;Z)Z")) {
        return false;
    }
    if (signature.equals("com/didi/security/wireless/StatUtils->isNetworkAvailable(Landroid/content/Context;)Z")) {
        return true;
    }
    return super.callStaticBooleanMethod(vm, dvmClass, signature, varArg);
}

@Override
public int callStaticIntMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
    if (signature.equals("com/didi/security/wireless/SecurityLib->getUserMode()I")) {
        return 0;
    }
    return super.callStaticIntMethod(vm, dvmClass, signature, varArg);
}

@Override
public FileResult<AndroidFileIO> resolve(Emulator<AndroidFileIO> emulator, String pathname, int oflags) {
    if (pathname.equals("/data/app/com.sdu.didi.gsui-mGlj-PzyfIpeLiDCnY-Qcw==/base.apk")) {
        File file = new File("D:\\project\\TraceAIagent_v3\\tests\\nativeSig\\滴滴车主.apk");
        return FileResult.<AndroidFileIO>success(new SimpleFileIO(oflags, file, pathname));
    }
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
