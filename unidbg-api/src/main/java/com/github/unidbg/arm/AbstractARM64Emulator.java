package com.github.unidbg.arm;

import capstone.api.Disassembler;
import capstone.api.DisassemblerFactory;
import capstone.api.Instruction;
import com.alibaba.fastjson.util.IOUtils;
import com.github.unidbg.AbstractEmulator;
import com.github.unidbg.Family;
import com.github.unidbg.Module;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.arm.backend.BackendFactory;
import com.github.unidbg.arm.backend.EventMemHook;
import com.github.unidbg.arm.backend.UnHook;
import com.github.unidbg.arm.context.BackendArm64RegisterContext;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.debugger.Debugger;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.NewFileIO;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.spi.Dlfcn;
import com.github.unidbg.spi.SyscallHandler;
import com.github.unidbg.thread.Entry;
import com.github.unidbg.thread.Function64;
import com.github.unidbg.unix.UnixSyscallHandler;
import com.github.unidbg.unwind.SimpleARM64Unwinder;
import com.github.unidbg.unwind.Unwinder;
import com.sun.jna.Pointer;
import keystone.Keystone;
import keystone.KeystoneArchitecture;
import keystone.KeystoneEncoded;
import keystone.KeystoneMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.Arm64Const;
import unicorn.UnicornConst;

import java.io.File;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractARM64Emulator<T extends NewFileIO> extends AbstractEmulator<T> implements ARMEmulator<T> {

    private static final Logger log = LoggerFactory.getLogger(AbstractARM64Emulator.class);

    protected final Memory memory;
    private final UnixSyscallHandler<T> syscallHandler;

    private static final long IOS_LR = 0x7ffff0000L;
    private static final long IOS_SVC_BASE = 0xfffe0000L;

    private final long returnAddress;
    private final Dlfcn dlfcn;

    public AbstractARM64Emulator(String processName, File rootDir, Family family, Collection<BackendFactory> backendFactories, String... envs) {
        this(processName, rootDir, family, backendFactories, null, envs);
    }

    public AbstractARM64Emulator(String processName, File rootDir, Family family, Collection<BackendFactory> backendFactories, TraceEnvironmentConfig environmentConfig, String... envs) {
        super(true, processName, svcBaseFor(family), 0x10000, rootDir, family, backendFactories, environmentConfig);
        this.returnAddress = lrFor(family);

        backend.switchUserMode();

        backend.hook_add_new(new EventMemHook() {
            @Override
            public boolean hook(Backend backend, long address, int size, long value, Object user, UnmappedType unmappedType) {
                if (unmappedType == UnmappedType.Fetch && tryRepairTruncatedFetch(backend, address)) {
                    return true;
                }
                log.warn("{} memory failed: address=0x{}, size={}, value=0x{}", unmappedType, Long.toHexString(address), size, Long.toHexString(value));
                if (LoggerFactory.getLogger(AbstractEmulator.class).isDebugEnabled()) {
                    attach().debug(unmappedType + " memory failed: address=0x" + Long.toHexString(address) + ", size=" + size);
                }
                return false;
            }
            @Override
            public void onAttach(UnHook unHook) {
            }
            @Override
            public void detach() {
                throw new UnsupportedOperationException();
            }
        }, UnicornConst.UC_HOOK_MEM_READ_UNMAPPED | UnicornConst.UC_HOOK_MEM_WRITE_UNMAPPED | UnicornConst.UC_HOOK_MEM_FETCH_UNMAPPED, null);

        this.syscallHandler = createSyscallHandler(svcMemory);

        backend.enableVFP();
        this.memory = createMemory(syscallHandler, envs);
        this.dlfcn = createDyld(svcMemory);
        this.memory.addHookListener(dlfcn);

        backend.hook_add_new(syscallHandler, this);

        setupTraps();
    }

    private static long svcBaseFor(Family family) {
        return family == Family.Android64 ? AndroidArm64Addresses.SVC_BASE : IOS_SVC_BASE;
    }

    private static long lrFor(Family family) {
        return family == Family.Android64 ? AndroidArm64Addresses.LR : IOS_LR;
    }

    private Disassembler arm64DisassemblerCache;
    private final Map<Long, Instruction[]> disassembleCache = new HashMap<>();

    private synchronized Disassembler createArm64Disassembler() {
        if (arm64DisassemblerCache == null) {
            this.arm64DisassemblerCache = DisassemblerFactory.createArm64Disassembler();
            this.arm64DisassemblerCache.setDetail(true);
        }
        return arm64DisassemblerCache;
    }

    /**
     * Packers sometimes {@code br} a 32-bit tail of a 39-bit VAS code address
     * ({@code 0x71000011dc} stored as {@code 0x11dc}). Map a trampoline page
     * in the low 4G so the fetch can continue at the reconstructed RX VA.
     * Never maps the NULL page.
     */
    private boolean tryRepairTruncatedFetch(Backend backend, long address) {
        if (getFamily() != Family.Android64 || memory == null) {
            return false;
        }
        if (address == 0L || address > 0xffffffffL || (address & 3L) != 0L) {
            return false;
        }
        long page = address & ~0xfffL;
        if (page == 0L) {
            return false;
        }
        long reconstructed = reconstructTruncatedCodeAddress(address);
        if (reconstructed == 0L || reconstructed == address) {
            return false;
        }
        try {
            byte[] insn = backend.mem_read(reconstructed, 4);
            if (insn == null || insn.length < 4
                    || (insn[0] | insn[1] | insn[2] | insn[3]) == 0) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        try {
            try {
                backend.mem_map(page, 0x1000, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_EXEC);
            } catch (Exception ignored) {
                // page already mapped by a previous trampoline
            }
            backend.mem_write(address, encodeAbsBranchX16(reconstructed));
            log.info("Repaired truncated FETCH 0x{} -> 0x{}",
                    Long.toHexString(address), Long.toHexString(reconstructed));
            return true;
        } catch (Exception e) {
            log.warn("Failed to repair truncated FETCH 0x{}: {}", Long.toHexString(address), e.toString());
            return false;
        }
    }

    private long reconstructTruncatedCodeAddress(long address32) {
        long found = 0L;
        for (com.github.unidbg.memory.MemoryMap map : memory.getMemoryMap()) {
            if ((map.prot & UnicornConst.UC_PROT_EXEC) == 0) {
                continue;
            }
            long candidate = (map.base & ~0xffffffffL) | (address32 & 0xffffffffL);
            if (candidate >= map.base && candidate < map.base + map.size) {
                if (found != 0L && found != candidate) {
                    return 0L;
                }
                found = candidate;
            }
        }
        return found;
    }

    private static byte[] encodeAbsBranchX16(long target) {
        ByteBuffer buf = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(movWide(true, false, 0, (int) (target & 0xffffL), 16));
        buf.putInt(movWide(false, true, 1, (int) ((target >>> 16) & 0xffffL), 16));
        buf.putInt(movWide(false, true, 2, (int) ((target >>> 32) & 0xffffL), 16));
        buf.putInt(movWide(false, true, 3, (int) ((target >>> 48) & 0xffffL), 16));
        buf.putInt(0xd61f0200); // br x16
        return buf.array();
    }

    /** A64 MOVZ/MOVK Xd, #imm16, LSL #(hw*16). */
    private static int movWide(boolean movz, boolean movk, int hw, int imm16, int rd) {
        int opc = movz ? 0b10 : 0b11;
        return (1 << 31) | (opc << 29) | (0b100101 << 23) | ((hw & 3) << 21)
                | ((imm16 & 0xffff) << 5) | (rd & 0x1f);
    }

    protected void setupTraps() {
        int size = getPageAlign();
        try {
            backend.mem_map(returnAddress, size, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_EXEC);
        } catch (RuntimeException e) {
            if (getFamily() == Family.Android64) {
                throw new IllegalStateException(AndroidArm64Addresses.unicornRequiredMessage(returnAddress), e);
            }
            throw e;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int code = Arm64Svc.assembleSvc(0);
        for (int i = 0; i < size; i += 4) {
            buffer.putInt(code); // svc #0
        }
        memory.pointer(returnAddress).write(buffer.array());
    }

    @Override
    protected RegisterContext createRegisterContext(Backend backend) {
        return new BackendArm64RegisterContext(backend, this);
    }

    @Override
    public Dlfcn getDlfcn() {
        return dlfcn;
    }

    @Override
    protected final byte[] assemble(Iterable<String> assembly) {
        try (Keystone keystone = new Keystone(KeystoneArchitecture.Arm64, KeystoneMode.LittleEndian)) {
            KeystoneEncoded encoded = keystone.assemble(assembly);
            return encoded.getMachineCode();
        }
    }

    @Override
    protected Debugger createConsoleDebugger() {
        return new SimpleARM64Debugger(this) {
            @Override
            protected void dumpClass(String className) {
                AbstractARM64Emulator.this.dumpClass(className);
            }
            @Override
            protected void searchClass(String keywords) {
                AbstractARM64Emulator.this.searchClass(keywords);
            }

            @Override
            protected void dumpGPBProtobufMsg(String className) {
                AbstractARM64Emulator.this.dumpGPBProtobufMsg(className);
            }
        };
    }

    @Override
    protected void closeInternal() {
        syscallHandler.destroy();

        IOUtils.close(arm64DisassemblerCache);
        disassembleCache.clear();
    }

    @Override
    public Module loadLibrary(File libraryFile) {
        return memory.load(libraryFile);
    }

    @Override
    public Module loadLibrary(File libraryFile, boolean forceCallInit) {
        return memory.load(libraryFile, forceCallInit);
    }

    @Override
    public Memory getMemory() {
        return memory;
    }

    @Override
    public SyscallHandler<T> getSyscallHandler() {
        return syscallHandler;
    }

    @Override
    public final void showRegs() {
        this.showRegs((int[]) null);
    }

    @Override
    public final void showRegs(int... regs) {
        ARM.showRegs64(this, regs);
    }

    @Override
    public Instruction[] printAssemble(PrintStream out, long address, int size, int maxLengthLibraryName, InstructionVisitor visitor) {
        Instruction[] insns = disassembleCache.get(address);
        byte[] currentCode = backend.mem_read(address, size);
        boolean needUpdateCache = false;
        if (insns != null) {
            byte[] cachedCode = new byte[size];
            int offset = 0;
            for (Instruction insn : insns) {
                byte[] insnBytes = insn.getBytes();
                System.arraycopy(insnBytes, 0, cachedCode, offset, insnBytes.length);
                offset += insnBytes.length;
            }

            if (!Arrays.equals(currentCode, cachedCode)) {
                needUpdateCache = true;
            }
        } else {
            needUpdateCache = true;
        }
        if (needUpdateCache) {
            insns = disassemble(address, currentCode, false, 0);
            disassembleCache.put(address, insns);
        }
        printAssemble(out, insns, address, maxLengthLibraryName, visitor);
        return insns;
    }

    @Override
    public Instruction[] disassemble(long address, int size, long count) {
        byte[] code = backend.mem_read(address, size);
        return createArm64Disassembler().disasm(code, address, count);
    }

    @Override
    public Instruction[] disassemble(long address, byte[] code, boolean thumb, long count) {
        if (thumb) {
            throw new IllegalStateException();
        }
        return createArm64Disassembler().disasm(code, address, count);
    }

    private void printAssemble(PrintStream out, Instruction[] insns, long address, int maxLengthLibraryName, InstructionVisitor visitor) {
        StringBuilder builder = new StringBuilder();
        for (Instruction ins : insns) {
            if(visitor != null) {
                visitor.visitLast(builder);
            }
            builder.append('\n');
            builder.append(dateFormat.format(new Date()));
            builder.append(ARM.assembleDetail(this, ins, address, false, maxLengthLibraryName));
            if (visitor != null) {
                visitor.visit(builder, ins);
            }
            address += ins.getSize();
        }
        out.print(builder);
    }

    @Override
    public int getPointerSize() {
        return 8;
    }

    @Override
    protected int getPageAlignInternal() {
        return PAGE_ALIGN;
    }

    @Override
    public Number eFunc(long begin, Number... arguments) {
        return runMainForResult(new Function64(getPid(), begin, returnAddress, isPaddingArgument(), arguments));
    }

    @Override
    public Number eEntry(long begin, long sp) {
        return runMainForResult(new Entry(getPid(), begin, returnAddress, sp));
    }

    @Override
    public Pointer getStackPointer() {
        return UnidbgPointer.register(this, Arm64Const.UC_ARM64_REG_SP);
    }

    @Override
    public Unwinder getUnwinder() {
        return new SimpleARM64Unwinder(this);
    }

    @Override
    public long getReturnAddress() {
        return returnAddress;
    }
}
