package agent.generated.nativesig_primary_e2e_20260815_045843;

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
        installInstrumentation();
        // source-position traceCode 将在 run() 中按调用插入点启动；其它 instrumentation 已在 init_array 前安装。
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
        traceAiInstallRegisterMemoryProbe("obs_blob_memcpy_src", (0x121cd994L - module.base), false, new RegisterMemoryProbeSpec("obs_blob_memcpy_src", "", "", Arm64Const.UC_ARM64_REG_X1, "x1", 20, 0, "", 4096), new RegisterSpec[] {new RegisterSpec("x0", Arm64Const.UC_ARM64_REG_X0), new RegisterSpec("x1", Arm64Const.UC_ARM64_REG_X1), new RegisterSpec("x2", Arm64Const.UC_ARM64_REG_X2), new RegisterSpec("x3", Arm64Const.UC_ARM64_REG_X3), new RegisterSpec("x4", Arm64Const.UC_ARM64_REG_X4), new RegisterSpec("x5", Arm64Const.UC_ARM64_REG_X5), new RegisterSpec("x6", Arm64Const.UC_ARM64_REG_X6), new RegisterSpec("x7", Arm64Const.UC_ARM64_REG_X7), new RegisterSpec("lr", Arm64Const.UC_ARM64_REG_LR), new RegisterSpec("sp", Arm64Const.UC_ARM64_REG_SP), new RegisterSpec("pc", Arm64Const.UC_ARM64_REG_PC)});
        traceAiInstallRegisterMemoryProbe("obs_copy1_src", (0x121ccd30L - module.base), false, new RegisterMemoryProbeSpec("obs_copy1_src", "", "", Arm64Const.UC_ARM64_REG_X1, "x1", 4, 0, "", 4096), new RegisterSpec[] {new RegisterSpec("x0", Arm64Const.UC_ARM64_REG_X0), new RegisterSpec("x1", Arm64Const.UC_ARM64_REG_X1), new RegisterSpec("x2", Arm64Const.UC_ARM64_REG_X2), new RegisterSpec("x3", Arm64Const.UC_ARM64_REG_X3), new RegisterSpec("x4", Arm64Const.UC_ARM64_REG_X4), new RegisterSpec("x5", Arm64Const.UC_ARM64_REG_X5), new RegisterSpec("x6", Arm64Const.UC_ARM64_REG_X6), new RegisterSpec("x7", Arm64Const.UC_ARM64_REG_X7), new RegisterSpec("lr", Arm64Const.UC_ARM64_REG_LR), new RegisterSpec("sp", Arm64Const.UC_ARM64_REG_SP), new RegisterSpec("pc", Arm64Const.UC_ARM64_REG_PC)});
        traceAiInstallRegisterMemoryProbe("obs_copy2_src", (0x121cf6e8L - module.base), false, new RegisterMemoryProbeSpec("obs_copy2_src", "", "", Arm64Const.UC_ARM64_REG_X1, "x1", 2, 0, "", 4096), new RegisterSpec[] {new RegisterSpec("x0", Arm64Const.UC_ARM64_REG_X0), new RegisterSpec("x1", Arm64Const.UC_ARM64_REG_X1), new RegisterSpec("x2", Arm64Const.UC_ARM64_REG_X2), new RegisterSpec("x3", Arm64Const.UC_ARM64_REG_X3), new RegisterSpec("x4", Arm64Const.UC_ARM64_REG_X4), new RegisterSpec("x5", Arm64Const.UC_ARM64_REG_X5), new RegisterSpec("x6", Arm64Const.UC_ARM64_REG_X6), new RegisterSpec("x7", Arm64Const.UC_ARM64_REG_X7), new RegisterSpec("lr", Arm64Const.UC_ARM64_REG_LR), new RegisterSpec("sp", Arm64Const.UC_ARM64_REG_SP), new RegisterSpec("pc", Arm64Const.UC_ARM64_REG_PC)});
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


private final Map<String, String> traceAiActiveCallsiteHits = new HashMap<String, String>();

private void traceAiInstallBreakpoint(final String instrumentationId, final String kind, final long offset, final int register, final String registerName, final RegisterSpec[] registerSpecs, final int length, final String returnKind, final long expectedValue, final long fixedMemoryAddress) {
    emulator.attach().addBreakPoint(module, offset, new BreakPointCallback() {
        @Override
        public boolean onHit(Emulator<?> emulator, long address) {
            long hitIndex = observationEvents.nextHitIndex(instrumentationId);
            String breakpoint = traceAiBreakpointJson(kind, offset, address, hitIndex);
            long registerValue = emulator.getContext().getLongByReg(register);
            emitObservation("breakpoint.hit", instrumentationId, "\"breakpoint\":" + breakpoint + ",\"registers\":" + traceAiRegistersJson(emulator, registerSpecs));
            if ("memoryReadOnBreakpoint".equals(kind)) {
                if (fixedMemoryAddress != 0L) {
                    traceAiEmitBreakpointMemory(instrumentationId, breakpoint, "fixedAddress", "fixedAddress", fixedMemoryAddress, length);
                } else {
                    traceAiEmitBreakpointMemory(instrumentationId, breakpoint, "register", registerName, registerValue, length);
                }
                return true;
            }
            if ("breakpointWithRegisterEquals".equals(kind)) {
                return registerValue != expectedValue;
            }
            return true;
        }
    });
}

private String traceAiRegistersJson(Emulator<?> emulator, RegisterSpec[] specs) {
    StringBuilder builder = new StringBuilder();
    builder.append('{');
    for (int i = 0; i < specs.length; i++) {
        if (i > 0) {
            builder.append(',');
        }
        RegisterSpec spec = specs[i];
        long value = emulator.getContext().getLongByReg(spec.reg);
        builder.append(traceAiJson(spec.name)).append(':').append(traceAiJson(traceAiHex(value)));
    }
    builder.append('}');
    return builder.toString();
}

private void traceAiEmitBreakpointMemory(String instrumentationId, String breakpoint, String addressSource, String registerName, long memoryAddress, int requestedLength) {
    try {
        if (memoryAddress == 0L) {
            throw new IllegalStateException("memory address is null");
        }
        Pointer pointer = UnidbgPointer.pointer(emulator, memoryAddress);
        if (pointer == null || traceAiPointerAddress(pointer) == 0L) {
            throw new IllegalStateException("pointer is null");
        }
        byte[] data = pointer.getByteArray(0, requestedLength);
        emitObservation("breakpoint.memory", instrumentationId, "\"breakpoint\":" + breakpoint + ",\"memory\":{\"register\":" + traceAiJson(registerName) + ",\"addressSource\":" + traceAiJson(addressSource) + ",\"address\":" + traceAiJson(traceAiHex(traceAiPointerAddress(pointer))) + ",\"requestedLength\":" + requestedLength + ",\"length\":" + data.length + ",\"byteLength\":" + data.length + ",\"encoding\":\"hex\",\"dataHex\":" + traceAiJson(traceAiBytesToHex(data)) + "}");
    } catch (Throwable t) {
        emitObservation("breakpoint.memory.error", instrumentationId, "\"breakpoint\":" + breakpoint + ",\"memory\":{\"register\":" + traceAiJson(registerName) + ",\"addressSource\":" + traceAiJson(addressSource) + ",\"address\":" + traceAiJson(traceAiHex(memoryAddress)) + ",\"requestedLength\":" + requestedLength + "},\"error\":{\"type\":" + traceAiJson(t.getClass().getName()) + ",\"message\":" + traceAiJson(String.valueOf(t.getMessage())) + "}");
    }
}

private void traceAiInstallCallsiteProbe(final String instrumentationId, final long enterValue, final boolean enterAbsolute, final long leaveValue, final boolean leaveAbsolute, final RegisterSpec[] registerSpecs, final RegisterMemoryProbeSpec[] enterMemoryProbes, final RegisterMemoryProbeSpec[] leaveMemoryProbes) {
    traceAiAddBreakPoint(enterValue, enterAbsolute, new BreakPointCallback() {
        @Override
        public boolean onHit(Emulator<?> emulator, long address) {
            long hitIndex = observationEvents.nextHitIndex(instrumentationId);
            String callsiteHitId = instrumentationId + ":" + hitIndex;
            traceAiActiveCallsiteHits.put(traceAiCallsiteKey(instrumentationId), callsiteHitId);
            String callsite = traceAiCallsiteJson("callsiteProbe", enterValue, enterAbsolute, address, "enter", hitIndex, callsiteHitId);
            emitObservation("callsite.enter", instrumentationId, "\"callsite\":" + callsite + ",\"callsiteHitId\":" + traceAiJson(callsiteHitId) + ",\"registers\":" + traceAiRegistersJson(emulator, registerSpecs));
            for (RegisterMemoryProbeSpec spec : enterMemoryProbes) {
                traceAiEmitRegisterMemoryProbe(spec, "enter", callsite, callsiteHitId, "");
            }
            return true;
        }
    });
    traceAiAddBreakPoint(leaveValue, leaveAbsolute, new BreakPointCallback() {
        @Override
        public boolean onHit(Emulator<?> emulator, long address) {
            String key = traceAiCallsiteKey(instrumentationId);
            String callsiteHitId = traceAiActiveCallsiteHits.remove(key);
            if (callsiteHitId == null || callsiteHitId.length() == 0) {
                long syntheticHit = observationEvents.nextHitIndex(instrumentationId + ":leave");
                callsiteHitId = instrumentationId + ":leave:" + syntheticHit;
            }
            long hitIndex = observationEvents.nextHitIndex(instrumentationId + ":leave-event");
            String callsite = traceAiCallsiteJson("callsiteProbe", leaveValue, leaveAbsolute, address, "leave", hitIndex, callsiteHitId);
            emitObservation("callsite.leave", instrumentationId, "\"callsite\":" + callsite + ",\"callsiteHitId\":" + traceAiJson(callsiteHitId) + ",\"registers\":" + traceAiRegistersJson(emulator, registerSpecs));
            for (RegisterMemoryProbeSpec spec : leaveMemoryProbes) {
                traceAiEmitRegisterMemoryProbe(spec, "leave", callsite, callsiteHitId, "");
            }
            return true;
        }
    });
}

private void traceAiInstallRegisterMemoryProbe(final String instrumentationId, final long breakpointValue, final boolean absoluteAddress, final RegisterMemoryProbeSpec spec, final RegisterSpec[] registerSpecs) {
    traceAiAddBreakPoint(breakpointValue, absoluteAddress, new BreakPointCallback() {
        @Override
        public boolean onHit(Emulator<?> emulator, long address) {
            long hitIndex = observationEvents.nextHitIndex(instrumentationId);
            String breakpoint = traceAiBreakpointJson("registerMemoryProbe", absoluteAddress ? address - module.base : breakpointValue, address, hitIndex);
            emitObservation("breakpoint.hit", instrumentationId, "\"breakpoint\":" + breakpoint + ",\"registers\":" + traceAiRegistersJson(emulator, registerSpecs));
            traceAiEmitRegisterMemoryProbe(spec, "breakpoint", "", "", "\"breakpoint\":" + breakpoint);
            return true;
        }
    });
}

private void traceAiInstallRangeWriteProbeBreakpoint(final String instrumentationId, final long offset, final int baseRegister, final String baseRegisterName, final int length, final int lengthRegister, final String lengthRegisterName, final int maxLength) {
    emulator.attach().addBreakPoint(module, offset, new BreakPointCallback() {
        @Override
        public boolean onHit(Emulator<?> emulator, long address) {
            long begin = emulator.getContext().getLongByReg(baseRegister);
            int requestedLength = traceAiRequestedLength(length, lengthRegister, lengthRegisterName, maxLength);
            if (begin == 0L || requestedLength <= 0) {
                String targetRange = traceAiRangeJson(begin, begin, baseRegisterName);
                traceAiEmitRangeWriteError(instrumentationId, targetRange, begin, 0, 0L, new IllegalStateException("rangeWriteProbe base register is null/zero or length is not positive"));
                return true;
            }
            long end = begin + requestedLength;
            traceAiStartRangeWriteProbe(instrumentationId, begin, end, traceAiRangeJson(begin, end, baseRegisterName));
            return true;
        }
    });
}

private void traceAiAddBreakPoint(long value, boolean absoluteAddress, BreakPointCallback callback) {
    if (absoluteAddress) {
        emulator.attach().addBreakPoint(value, callback);
    } else {
        emulator.attach().addBreakPoint(module, value, callback);
    }
}

private String traceAiCallsiteKey(String instrumentationId) {
    return instrumentationId + ":" + Thread.currentThread().getName();
}

private String traceAiCallsiteJson(String kind, long value, boolean absoluteAddress, long address, String phase, long hitIndex, String callsiteHitId) {
    long offset = absoluteAddress ? address - module.base : value;
    return "{\"kind\":" + traceAiJson(kind) + ",\"module\":" + traceAiJson(module.name) + ",\"offset\":" + traceAiJson(traceAiHex(offset)) + ",\"address\":" + traceAiJson(traceAiHex(address)) + ",\"phase\":" + traceAiJson(phase) + ",\"hitIndex\":" + hitIndex + ",\"callsiteHitId\":" + traceAiJson(callsiteHitId) + "}";
}

private String traceAiRegisterMemoryProbeJson(RegisterMemoryProbeSpec spec, String phase, String callsiteHitId, long address) {
    return "{\"kind\":\"registerMemoryProbe\",\"module\":" + traceAiJson(module.name) + ",\"register\":" + traceAiJson(spec.registerName) + ",\"phase\":" + traceAiJson(phase) + ",\"callsiteProbeId\":" + traceAiJson(spec.callsiteProbeId) + ",\"callsiteHitId\":" + traceAiJson(callsiteHitId) + ",\"address\":" + traceAiJson(traceAiHex(address)) + "}";
}

private void traceAiEmitRegisterMemoryProbe(RegisterMemoryProbeSpec spec, String phase, String callsiteJson, String callsiteHitId, String extraPayload) {
    String registerMemory = traceAiRegisterMemoryProbeJson(spec, phase, callsiteHitId, emulator.getContext().getPCPointer() == null ? 0L : traceAiPointerAddress(emulator.getContext().getPCPointer()));
    int requestedLength = traceAiRequestedLength(spec.length, spec.lengthRegister, spec.lengthRegisterName, spec.maxLength);
    long memoryAddress = emulator.getContext().getLongByReg(spec.register);
    String relatedPayload = extraPayload == null || extraPayload.length() == 0 ? "" : extraPayload + ",";
    if (callsiteJson != null && callsiteJson.length() > 0) {
        relatedPayload = "\"callsite\":" + callsiteJson + ",\"callsiteHitId\":" + traceAiJson(callsiteHitId) + ",";
    }
    try {
        if (memoryAddress == 0L || requestedLength <= 0) {
            throw new IllegalStateException("memory address is null/zero or requested length is not positive");
        }
        Pointer pointer = UnidbgPointer.pointer(emulator, memoryAddress);
        if (pointer == null || traceAiPointerAddress(pointer) == 0L) {
            throw new IllegalStateException("pointer is null");
        }
        byte[] data = pointer.getByteArray(0, requestedLength);
        emitObservation("registerMemory.memory", spec.instrumentationId, relatedPayload + "\"registerMemory\":" + registerMemory + ",\"memory\":{\"register\":" + traceAiJson(spec.registerName) + ",\"address\":" + traceAiJson(traceAiHex(traceAiPointerAddress(pointer))) + ",\"requestedLength\":" + requestedLength + ",\"length\":" + data.length + ",\"byteLength\":" + data.length + ",\"encoding\":\"hex\",\"dataHex\":" + traceAiJson(traceAiBytesToHex(data)) + "}");
    } catch (Throwable t) {
        emitObservation("registerMemory.memory.error", spec.instrumentationId, relatedPayload + "\"registerMemory\":" + registerMemory + ",\"memory\":{\"register\":" + traceAiJson(spec.registerName) + ",\"address\":" + traceAiJson(traceAiHex(memoryAddress)) + ",\"requestedLength\":" + requestedLength + "},\"error\":{\"type\":" + traceAiJson(t.getClass().getName()) + ",\"message\":" + traceAiJson(String.valueOf(t.getMessage())) + "}");
    }
}

private int traceAiRequestedLength(int fixedLength, int lengthRegister, String lengthRegisterName, int maxLength) {
    long requested = fixedLength > 0 ? fixedLength : 0L;
    if (requested <= 0L && lengthRegisterName != null && lengthRegisterName.length() > 0) {
        requested = emulator.getContext().getLongByReg(lengthRegister);
    }
    if (requested <= 0L) {
        return 0;
    }
    long capped = maxLength > 0 ? Math.min(requested, maxLength) : requested;
    return (int) Math.min(capped, Integer.MAX_VALUE);
}

private void traceAiStartRangeWriteProbe(final String instrumentationId, final long begin, final long end, final String targetRange) {
    traceHooks.add(emulator.traceWrite(begin, end, new TraceWriteListener() {
        @Override
        public boolean onWrite(Emulator<?> emulator, long address, int size, long value) {
            traceAiEmitRangeWrite(instrumentationId, targetRange, address, size, value);
            return true;
        }
    }));
}

private void traceAiEmitRangeWrite(String instrumentationId, String targetRange, long address, int size, long value) {
    try {
        if (address == 0L || size <= 0) {
            throw new IllegalStateException("write address is null/zero or size is not positive");
        }
        Pointer pointer = UnidbgPointer.pointer(emulator, address);
        if (pointer == null || traceAiPointerAddress(pointer) == 0L) {
            throw new IllegalStateException("pointer is null");
        }
        byte[] data = pointer.getByteArray(0, size);
        String dataHex = traceAiBytesToHex(data);
        long writerAddress = traceAiPointerAddress(emulator.getContext().getPCPointer());
        long lrAddress = traceAiPointerAddress(emulator.getContext().getLRPointer());
        String coveredRange = traceAiCoveredRangeJson(address, address + size, size);
        emitObservation("rangeWrite.write", instrumentationId, "\"rangeWrite\":{\"kind\":\"rangeWriteProbe\",\"module\":" + traceAiJson(module.name) + "},\"targetRange\":" + targetRange + ",\"coveredRange\":" + coveredRange + ",\"writer\":{\"address\":" + traceAiJson(traceAiHex(writerAddress)) + ",\"offset\":" + traceAiJson(traceAiHex(writerAddress - module.base)) + ",\"lr\":" + traceAiJson(traceAiHex(lrAddress)) + "},\"writerAddress\":" + traceAiJson(traceAiHex(writerAddress)) + ",\"writerOffset\":" + traceAiJson(traceAiHex(writerAddress - module.base)) + ",\"value\":" + traceAiJson(traceAiHex(value)) + ",\"memory\":{\"address\":" + traceAiJson(traceAiHex(traceAiPointerAddress(pointer))) + ",\"requestedLength\":" + size + ",\"length\":" + data.length + ",\"byteLength\":" + data.length + ",\"encoding\":\"hex\",\"dataHex\":" + traceAiJson(dataHex) + "},\"byteLength\":" + data.length + ",\"dataHex\":" + traceAiJson(dataHex));
    } catch (Throwable t) {
        traceAiEmitRangeWriteError(instrumentationId, targetRange, address, size, value, t);
    }
}

private void traceAiEmitRangeWriteError(String instrumentationId, String targetRange, long address, int size, long value, Throwable t) {
    long writerAddress = traceAiPointerAddress(emulator.getContext().getPCPointer());
    String coveredRange = traceAiCoveredRangeJson(address, address + Math.max(size, 0), size);
    emitObservation("rangeWrite.write.error", instrumentationId, "\"rangeWrite\":{\"kind\":\"rangeWriteProbe\",\"module\":" + traceAiJson(module.name) + "},\"targetRange\":" + targetRange + ",\"coveredRange\":" + coveredRange + ",\"writer\":{\"address\":" + traceAiJson(traceAiHex(writerAddress)) + ",\"offset\":" + traceAiJson(traceAiHex(writerAddress - module.base)) + "},\"writerAddress\":" + traceAiJson(traceAiHex(writerAddress)) + ",\"writerOffset\":" + traceAiJson(traceAiHex(writerAddress - module.base)) + ",\"value\":" + traceAiJson(traceAiHex(value)) + ",\"memory\":{\"address\":" + traceAiJson(traceAiHex(address)) + ",\"requestedLength\":" + size + "},\"error\":{\"type\":" + traceAiJson(t.getClass().getName()) + ",\"message\":" + traceAiJson(String.valueOf(t.getMessage())) + "}");
}

private String traceAiRangeJson(long begin, long end, String source) {
    long length = Math.max(0L, end - begin);
    return "{\"address\":" + traceAiJson(traceAiHex(begin)) + ",\"start\":" + traceAiJson(traceAiHex(begin)) + ",\"end\":" + traceAiJson(traceAiHex(end)) + ",\"length\":" + length + ",\"source\":" + traceAiJson(source) + "}";
}

private String traceAiCoveredRangeJson(long begin, long end, int size) {
    return "{\"address\":" + traceAiJson(traceAiHex(begin)) + ",\"start\":" + traceAiJson(traceAiHex(begin)) + ",\"end\":" + traceAiJson(traceAiHex(end)) + ",\"size\":" + size + "}";
}

private void traceAiRegisterJniFunctionTableHook(final String instrumentationId, final String symbol) {
    try {
        int tableOffset = traceAiJniFunctionTableOffset(symbol);
        Pointer env = vm.getJNIEnv();
        if (env == null || traceAiPointerAddress(env) == 0L) {
            throw new IllegalStateException("JNIEnv 指针为空");
        }
        Pointer functionTable = env.getPointer(0);
        if (functionTable == null || traceAiPointerAddress(functionTable) == 0L) {
            throw new IllegalStateException("JNIEnv 函数表为空");
        }
        final Pointer previousEntry = functionTable.getPointer(tableOffset);
        final Pointer replacementEntry = traceAiCreateJniStringSvc(instrumentationId, symbol, tableOffset, previousEntry);
        functionTable.setPointer(tableOffset, replacementEntry);
    } catch (Throwable t) {
        traceAiEmitJniHookError(instrumentationId, symbol, t);
    }
}

private int traceAiJniFunctionTableOffset(String symbol) {
    if ("NewString".equals(symbol)) {
        return emulator.is64Bit() ? 0x518 : 0x28c;
    }
    if ("NewStringUTF".equals(symbol)) {
        return emulator.is64Bit() ? 0x538 : 0x29c;
    }
    throw new IllegalArgumentException("不支持的 JNIEnv 函数：" + symbol);
}

private Pointer traceAiCreateJniStringSvc(final String instrumentationId, final String symbol, final int tableOffset, final Pointer previousEntry) {
    SvcMemory svcMemory = emulator.getSvcMemory();
    if (emulator.is64Bit()) {
        return svcMemory.registerSvc(new Arm64Svc("traceAiJniStringHook") {
            @Override
            public long handle(Emulator<?> emulator) {
                return traceAiHandleJniStringHook(emulator, instrumentationId, symbol, tableOffset, previousEntry);
            }
        });
    }
    return svcMemory.registerSvc(new ArmSvc("traceAiJniStringHook") {
        @Override
        public long handle(Emulator<?> emulator) {
            return traceAiHandleJniStringHook(emulator, instrumentationId, symbol, tableOffset, previousEntry);
        }
    });
}

private long traceAiHandleJniStringHook(Emulator<?> emulator, String instrumentationId, String symbol, int tableOffset, Pointer previousEntry) {
    RegisterContext context = emulator.getContext();
    try {
        String value;
        if ("NewString".equals(symbol)) {
            Pointer unicodeChars = context.getPointerArg(1);
            int len = context.getIntArg(2);
            if (unicodeChars == null) {
                if (len == 0) {
                    return VM.JNI_NULL;
                }
                throw new IllegalStateException("NewString unicodeChars 为空");
            }
            ByteBuffer buffer = ByteBuffer.wrap(unicodeChars.getByteArray(0, len * 2));
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            StringBuilder builder = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                builder.append(buffer.getChar());
            }
            value = builder.toString();
        } else if ("NewStringUTF".equals(symbol)) {
            Pointer bytes = context.getPointerArg(1);
            if (bytes == null) {
                return VM.JNI_NULL;
            }
            value = bytes.getString(0);
        } else {
            throw new IllegalArgumentException("不支持的 JNIEnv 函数：" + symbol);
        }
        return traceAiEmitJniStringAndAddLocalObject(instrumentationId, symbol, tableOffset, previousEntry, value);
    } catch (Throwable t) {
        traceAiEmitJniHookError(instrumentationId, symbol, t);
        return VM.JNI_NULL;
    }
}

private long traceAiEmitJniStringAndAddLocalObject(String instrumentationId, String symbol, int tableOffset, Pointer previousEntry, String value) {
    long lrAddress = traceAiPointerAddress(emulator.getContext().getLRPointer());
    String enterHook = traceAiHookJson("jniFunctionTable", "JNIEnv", symbol, "enter");
    String target = traceAiTargetJson(lrAddress);
    String args = "[{\"index\":0,\"name\":\"value\",\"type\":\"string\",\"value\":" + traceAiJson(value) + "}]";
    String jniFunction = "{\"tableOffset\":" + traceAiJson(traceAiHex(tableOffset)) + ",\"entryAddress\":" + traceAiJson(traceAiHex(traceAiPointerAddress(previousEntry))) + ",\"decodedString\":" + traceAiJson(value) + "}";
    emitObservation("hook.enter", instrumentationId, "\"hook\":" + enterHook + ",\"target\":" + target + ",\"args\":" + args + ",\"jniFunction\":" + jniFunction);
    int localRef = vm.addLocalObject(new StringObject(vm, value));
    String leaveHook = traceAiHookJson("jniFunctionTable", "JNIEnv", symbol, "leave");
    emitObservation("hook.leave", instrumentationId, "\"hook\":" + leaveHook + ",\"returnValue\":{\"type\":\"jobject\",\"value\":" + traceAiJson(traceAiHex(localRef)) + "}");
    return localRef;
}

private void traceAiEmitJniHookError(String instrumentationId, String symbol, Throwable t) {
    String hook = traceAiHookJson("jniFunctionTable", "JNIEnv", symbol, "enter");
    emitObservation("hook.error", instrumentationId, "\"hook\":" + hook + ",\"error\":{\"type\":" + traceAiJson(t.getClass().getName()) + ",\"message\":" + traceAiJson(String.valueOf(t.getMessage())) + "}");
}

private void traceAiRegisterFunctionHook(IxHook xHook, final String instrumentationId, final String library, final String symbol, final HookArgSpec[] argSpecs) {
    xHook.register(library, symbol, new ReplaceCallback() {
        @Override
        public HookStatus onCall(Emulator<?> emulator, HookContext context, long originFunction) {
            Pointer lrPointer = context.getLRPointer();
            long lrAddress = traceAiPointerAddress(lrPointer);
            String hook = traceAiHookJson("xhookImport", library, symbol, "enter");
            String target = traceAiTargetJson(lrAddress);
            String args = traceAiHookArgsJson(context, argSpecs);
            emitObservation("hook.enter", instrumentationId, "\"hook\":" + hook + ",\"target\":" + target + ",\"args\":" + args);
            for (HookArgSpec spec : argSpecs) {
                if ("bytes".equals(spec.capture)) {
                    traceAiEmitHookMemory(instrumentationId, hook, context, spec);
                }
            }
            context.push(Long.valueOf(lrAddress));
            return HookStatus.RET(emulator, originFunction);
        }

        @Override
        public void postCall(Emulator<?> emulator, HookContext context) {
            String hook = traceAiHookJson("xhookImport", library, symbol, "leave");
            emitObservation("hook.leave", instrumentationId, "\"hook\":" + hook + ",\"returnValue\":{\"type\":\"unknown\",\"value\":\"unknown\"}");
        }
    }, true);
}

private String traceAiHookArgsJson(HookContext context, HookArgSpec[] specs) {
    StringBuilder builder = new StringBuilder();
    builder.append('[');
    for (int i = 0; i < specs.length; i++) {
        if (i > 0) {
            builder.append(',');
        }
        HookArgSpec spec = specs[i];
        builder.append('{');
        builder.append("\"index\":").append(spec.index).append(',');
        builder.append("\"name\":").append(traceAiJson(spec.name)).append(',');
        builder.append("\"type\":").append(traceAiJson(spec.type)).append(',');
        builder.append("\"value\":").append(traceAiJson(traceAiReadHookArgValue(context, spec)));
        builder.append('}');
    }
    builder.append(']');
    return builder.toString();
}

private String traceAiReadHookArgValue(HookContext context, HookArgSpec spec) {
    if ("pointer".equals(spec.type) || "string".equals(spec.type)) {
        Pointer pointer = context.getPointerArg(spec.index);
        if ("string".equals(spec.type) && pointer != null) {
            try {
                return pointer.getString(0);
            } catch (Throwable ignored) {
                return traceAiHex(traceAiPointerAddress(pointer));
            }
        }
        return traceAiHex(traceAiPointerAddress(pointer));
    }
    return Long.toString(context.getLongArg(spec.index));
}

private void traceAiEmitHookMemory(String instrumentationId, String hook, HookContext context, HookArgSpec spec) {
    Pointer pointer = context.getPointerArg(spec.index);
    long address = traceAiPointerAddress(pointer);
    int requestedLength = traceAiHookMemoryLength(context, spec);
    if (pointer == null || address == 0L || requestedLength <= 0) {
        emitObservation("hook.memory.error", instrumentationId, "\"hook\":" + hook + ",\"memory\":{\"argIndex\":" + spec.index + ",\"argName\":" + traceAiJson(spec.name) + ",\"address\":" + traceAiJson(traceAiHex(address)) + ",\"requestedLength\":" + requestedLength + "},\"error\":{\"type\":\"InvalidMemoryRequest\",\"message\":\"pointer is null/zero or requested length is not positive\"}");
        return;
    }
    try {
        byte[] data = pointer.getByteArray(0, requestedLength);
        emitObservation("hook.memory", instrumentationId, "\"hook\":" + hook + ",\"memory\":{\"argIndex\":" + spec.index + ",\"argName\":" + traceAiJson(spec.name) + ",\"address\":" + traceAiJson(traceAiHex(address)) + ",\"dataHex\":" + traceAiJson(traceAiBytesToHex(data)) + ",\"requestedLength\":" + requestedLength + ",\"length\":" + data.length + ",\"byteLength\":" + data.length + ",\"encoding\":\"hex\"}");
    } catch (Throwable t) {
        emitObservation("hook.memory.error", instrumentationId, "\"hook\":" + hook + ",\"memory\":{\"argIndex\":" + spec.index + ",\"argName\":" + traceAiJson(spec.name) + ",\"address\":" + traceAiJson(traceAiHex(address)) + ",\"requestedLength\":" + requestedLength + "},\"error\":{\"type\":" + traceAiJson(t.getClass().getName()) + ",\"message\":" + traceAiJson(String.valueOf(t.getMessage())) + "}");
    }
}

private int traceAiHookMemoryLength(HookContext context, HookArgSpec spec) {
    long requested = spec.length >= 0 ? spec.length : 0L;
    if (spec.lengthFromArg >= 0) {
        requested = context.getLongArg(spec.lengthFromArg);
    }
    if (requested <= 0L) {
        return 0;
    }
    long capped = spec.maxLength > 0 ? Math.min(requested, spec.maxLength) : requested;
    return (int) Math.min(capped, Integer.MAX_VALUE);
}

private String traceAiBreakpointJson(String kind, long offset, long address, long hitIndex) {
    return "{\"kind\":" + traceAiJson(kind) + ",\"module\":" + traceAiJson(module.name) + ",\"offset\":" + traceAiJson(traceAiHex(offset)) + ",\"address\":" + traceAiJson(traceAiHex(address)) + ",\"hitIndex\":" + hitIndex + "}";
}

private String traceAiHookJson(String hookMode, String library, String symbol, String phase) {
    return "{\"hookMode\":" + traceAiJson(hookMode) + ",\"library\":" + traceAiJson(library) + ",\"symbol\":" + traceAiJson(symbol) + ",\"phase\":" + traceAiJson(phase) + "}";
}

private String traceAiTargetJson(long lrAddress) {
    String offset = lrAddress == 0L ? "unknown" : traceAiHex(lrAddress - module.base);
    return "{\"module\":" + traceAiJson(module.name) + ",\"address\":" + traceAiJson(traceAiHex(lrAddress)) + ",\"offset\":" + traceAiJson(offset) + ",\"lr\":" + traceAiJson(traceAiHex(lrAddress)) + "}";
}

private static long traceAiPointerAddress(Pointer pointer) {
    if (pointer == null) {
        return 0L;
    }
    if (pointer instanceof UnidbgPointer) {
        return ((UnidbgPointer) pointer).peer;
    }
    return UnidbgPointer.nativeValue(pointer);
}

private static String traceAiHex(long value) {
    return "0x" + Long.toHexString(value);
}

private static String traceAiBytesToHex(byte[] data) {
    StringBuilder builder = new StringBuilder(data.length * 2);
    for (byte b : data) {
        builder.append(String.format("%02x", b & 0xff));
    }
    return builder.toString();
}

private static String traceAiJson(String value) {
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

private static final class HookArgSpec {
    private final int index;
    private final String name;
    private final String type;
    private final String capture;
    private final int lengthFromArg;
    private final int length;
    private final int maxLength;

    private HookArgSpec(int index, String name, String type, String capture, int lengthFromArg, int length, int maxLength) {
        this.index = index;
        this.name = name;
        this.type = type;
        this.capture = capture == null ? "" : capture;
        this.lengthFromArg = lengthFromArg;
        this.length = length;
        this.maxLength = maxLength;
    }
}

private static final class RegisterSpec {
    private final String name;
    private final int reg;

    private RegisterSpec(String name, int reg) {
        this.name = name;
        this.reg = reg;
    }
}

private static final class RegisterMemoryProbeSpec {
    private final String instrumentationId;
    private final String callsiteProbeId;
    private final String phase;
    private final int register;
    private final String registerName;
    private final int length;
    private final int lengthRegister;
    private final String lengthRegisterName;
    private final int maxLength;

    private RegisterMemoryProbeSpec(String instrumentationId, String callsiteProbeId, String phase, int register, String registerName, int length, int lengthRegister, String lengthRegisterName, int maxLength) {
        this.instrumentationId = instrumentationId;
        this.callsiteProbeId = callsiteProbeId == null ? "" : callsiteProbeId;
        this.phase = phase == null ? "" : phase;
        this.register = register;
        this.registerName = registerName;
        this.length = length;
        this.lengthRegister = lengthRegister;
        this.lengthRegisterName = lengthRegisterName == null ? "" : lengthRegisterName;
        this.maxLength = maxLength;
    }
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
