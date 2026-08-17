package agent.generated.cff_strict_20260712;

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
        installInstrumentation();
        // source-position traceCode 将在 run() 中按调用插入点启动；其它 instrumentation 已在 init_array 前安装。
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
        traceAiInstallCounterfactualIntervention("counterfactual-cand-algorithm-0001", (0x12003178L - module.base), "memory-at-register", Arm64Const.UC_ARM64_REG_X26, "x26", 0L, "313731383030303030303030303635343535303562353537313739313035333531343335353130353835393431346434343432373664613436383063333361336231383330653630636164636333376430", "777a6c504b717a7a4d4b785a585150625a7831336c62707962554c4c516e4265357846504c39424437494758313471573649375a73417459435a455667653763487579676d623470517475504579526f53", 1, (0x12003694L - module.base), "memory-at-register", Arm64Const.UC_ARM64_REG_X24, "x24", 0L, 108, "little");
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
private final Map<String, Boolean> traceAiCounterfactualApplied = new HashMap<String, Boolean>();
private final Map<String, Long> traceAiCounterfactualMemoryAddress = new HashMap<String, Long>();

private void traceAiInstallCounterfactualIntervention(final String instrumentationId, final long interventionOffset, final String targetKind, final int targetRegister, final String targetRegisterName, final long fixedMemoryAddress, final String expectedBeforeHex, final String replacementHex, final long invocationIndex, final long outputOffset, final String outputKind, final int outputRegister, final String outputRegisterName, final long outputFixedAddress, final int outputLength, final String endian) {
    emulator.attach().addBreakPoint(module, interventionOffset, new BreakPointCallback() {
        @Override
        public boolean onHit(Emulator<?> emulator, long address) {
            long hitIndex = observationEvents.nextHitIndex(instrumentationId + ":intervention");
            emitObservation("counterfactual.intervention.hit", instrumentationId, "\"hitIndex\":" + hitIndex + ",\"runtimeAddress\":" + traceAiJson(traceAiHex(address)) + ",\"interventionKind\":" + traceAiJson(targetKind));
            if (hitIndex != invocationIndex) {
                return true;
            }
            try {
                byte[] expected = traceAiBytesFromHex(expectedBeforeHex);
                byte[] replacement = traceAiBytesFromHex(replacementHex);
                if (expected.length != replacement.length) {
                    throw new IllegalStateException("replacement length differs from expected input length");
                }
                byte[] before;
                byte[] readback;
                if ("register-value".equals(targetKind)) {
                    long registerValue = emulator.getContext().getLongByReg(targetRegister);
                    before = traceAiLongToBytes(registerValue, expected.length, endian);
                    long expectedRegisterValue = traceAiBytesToLong(expected, endian);
                    if (registerValue != expectedRegisterValue) {
                        throw new IllegalStateException("register original value mismatch");
                    }
                    long replacementRegisterValue = traceAiBytesToLong(replacement, endian);
                    emulator.getBackend().reg_write(targetRegister, Long.valueOf(replacementRegisterValue));
                    long readbackRegisterValue = emulator.getContext().getLongByReg(targetRegister);
                    if (readbackRegisterValue != replacementRegisterValue) {
                        throw new IllegalStateException("register intervention readback mismatch");
                    }
                    readback = traceAiLongToBytes(readbackRegisterValue, replacement.length, endian);
                } else {
                    long memoryAddress = "fixed-memory".equals(targetKind) ? fixedMemoryAddress : emulator.getContext().getLongByReg(targetRegister);
                    Pointer pointer = UnidbgPointer.pointer(emulator, memoryAddress);
                    if (pointer == null || memoryAddress == 0L) {
                        throw new IllegalStateException("intervention memory pointer is null");
                    }
                    traceAiCounterfactualMemoryAddress.put(instrumentationId, Long.valueOf(memoryAddress));
                    before = pointer.getByteArray(0, expected.length);
                    if (!traceAiBytesEqual(before, expected)) {
                        throw new IllegalStateException("memory original value mismatch");
                    }
                    pointer.write(0, replacement, 0, replacement.length);
                    readback = pointer.getByteArray(0, replacement.length);
                }
                if (!traceAiBytesEqual(readback, replacement)) {
                    throw new IllegalStateException("intervention readback mismatch");
                }
                traceAiCounterfactualApplied.put(instrumentationId, Boolean.TRUE);
                emitObservation("counterfactual.intervention.applied", instrumentationId, "\"hitIndex\":" + hitIndex + ",\"interventionKind\":" + traceAiJson(targetKind) + ",\"register\":" + traceAiJson(targetRegisterName) + ",\"beforeValueHex\":" + traceAiJson(traceAiBytesToHex(before)) + ",\"replacementHex\":" + traceAiJson(replacementHex.toLowerCase()) + ",\"readbackValueHex\":" + traceAiJson(traceAiBytesToHex(readback)));
            } catch (Throwable t) {
                emitObservation("counterfactual.intervention.error", instrumentationId, "\"hitIndex\":" + hitIndex + ",\"interventionKind\":" + traceAiJson(targetKind) + ",\"error\":{\"type\":" + traceAiJson(t.getClass().getName()) + ",\"message\":" + traceAiJson(String.valueOf(t.getMessage())) + "}");
            }
            return true;
        }
    });
    emulator.attach().addBreakPoint(module, outputOffset, new BreakPointCallback() {
        @Override
        public boolean onHit(Emulator<?> emulator, long address) {
            if (!Boolean.TRUE.equals(traceAiCounterfactualApplied.get(instrumentationId))) {
                return true;
            }
            try {
                byte[] output;
                String effectiveKind = outputKind;
                if ("same-as-intervention".equals(effectiveKind)) {
                    effectiveKind = targetKind;
                }
                if ("register-value".equals(effectiveKind)) {
                    int register = "same-as-intervention".equals(outputKind) ? targetRegister : outputRegister;
                    output = traceAiLongToBytes(emulator.getContext().getLongByReg(register), outputLength, endian);
                } else {
                    long memoryAddress;
                    if ("same-as-intervention".equals(outputKind)) {
                        Long storedAddress = traceAiCounterfactualMemoryAddress.get(instrumentationId);
                        memoryAddress = storedAddress == null ? 0L : storedAddress.longValue();
                    } else {
                        memoryAddress = "fixed-memory".equals(effectiveKind) ? outputFixedAddress : emulator.getContext().getLongByReg(outputRegister);
                    }
                    Pointer pointer = UnidbgPointer.pointer(emulator, memoryAddress);
                    if (pointer == null || memoryAddress == 0L) {
                        throw new IllegalStateException("output memory pointer is null");
                    }
                    output = pointer.getByteArray(0, outputLength);
                }
                emitObservation("counterfactual.output", instrumentationId, "\"runtimeAddress\":" + traceAiJson(traceAiHex(address)) + ",\"outputKind\":" + traceAiJson(outputKind) + ",\"register\":" + traceAiJson(outputRegisterName) + ",\"observedOutputHex\":" + traceAiJson(traceAiBytesToHex(output)) + ",\"byteLength\":" + output.length);
                traceAiCounterfactualApplied.put(instrumentationId, Boolean.FALSE);
            } catch (Throwable t) {
                emitObservation("counterfactual.output.error", instrumentationId, "\"runtimeAddress\":" + traceAiJson(traceAiHex(address)) + ",\"outputKind\":" + traceAiJson(outputKind) + ",\"error\":{\"type\":" + traceAiJson(t.getClass().getName()) + ",\"message\":" + traceAiJson(String.valueOf(t.getMessage())) + "}");
            }
            return true;
        }
    });
}

private static byte[] traceAiBytesFromHex(String value) {
    String text = value == null ? "" : value.trim();
    if ((text.length() & 1) != 0) {
        throw new IllegalArgumentException("hex length must be even");
    }
    byte[] result = new byte[text.length() / 2];
    for (int i = 0; i < result.length; i++) {
        result[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
    }
    return result;
}

private static boolean traceAiBytesEqual(byte[] left, byte[] right) {
    if (left == null || right == null || left.length != right.length) return false;
    for (int i = 0; i < left.length; i++) if (left[i] != right[i]) return false;
    return true;
}

private static long traceAiBytesToLong(byte[] data, String endian) {
    if (data.length > 8) throw new IllegalArgumentException("register value exceeds 8 bytes");
    long value = 0L;
    if ("big".equals(endian)) {
        for (byte item : data) value = (value << 8) | (item & 0xffL);
    } else {
        for (int i = data.length - 1; i >= 0; i--) value = (value << 8) | (data[i] & 0xffL);
    }
    return value;
}

private static byte[] traceAiLongToBytes(long value, int length, String endian) {
    if (length < 1 || length > 8) throw new IllegalArgumentException("register capture length must be 1..8");
    byte[] result = new byte[length];
    for (int i = 0; i < length; i++) {
        int index = "big".equals(endian) ? length - 1 - i : i;
        result[index] = (byte) ((value >>> (i * 8)) & 0xffL);
    }
    return result;
}

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
