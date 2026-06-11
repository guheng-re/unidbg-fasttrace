package com.github.unidbg;

import capstone.api.Instruction;
import com.github.unidbg.arm.Cpsr;
import com.github.unidbg.arm.backend.Backend;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.util.Locale;
import java.util.Map;

public final class RegAccessPrinter {

    private final long address;
    private final short[] accessRegs;
    private boolean forWriteRegs;
    private final int[] oldValueRegIds;
    private final long[] oldValues;
    private int oldValueCount;
    
    private final Map<Short, Integer> unicornRegIds;
    private final Map<Short, String> regNames;

    public RegAccessPrinter(long address, short[] accessRegs, Map<Short, Integer> unicornRegIds, Map<Short, String> regNames, boolean forWriteRegs, Backend backend) {
        this.address = address;
        this.accessRegs = accessRegs;
        this.unicornRegIds = unicornRegIds;
        this.regNames = regNames;
        this.forWriteRegs = forWriteRegs;
        this.oldValueRegIds = forWriteRegs ? new int[accessRegs.length] : new int[0];
        this.oldValues = forWriteRegs ? new long[accessRegs.length] : new long[0];

        if (forWriteRegs && backend != null) {
            for (short reg : accessRegs) {
                Integer regIdBoxed = unicornRegIds.get(reg);
                int regId = regIdBoxed != null ? regIdBoxed : 0;
                if (isComparableScalarRegister(regId)) {
                    try {
                        long val = backend.reg_read(regId).longValue();
                        oldValueRegIds[oldValueCount] = regId;
                        oldValues[oldValueCount] = val;
                        oldValueCount++;
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public void print(Emulator<?> emulator, Backend backend, StringBuilder builder, long address) {
        if (this.address != address) {
            return;
        }
        for (short reg : accessRegs) {
            Integer regIdBoxed = unicornRegIds.get(reg);
            int regId = regIdBoxed != null ? regIdBoxed : 0;
            String regName = regNames.get(reg);
            if (regName == null) regName = "unk";
            
            int oldValueIndex = forWriteRegs ? findOldValueIndex(regId) : -1;
            if (oldValueIndex >= 0) {
                try {
                    long currentVal = backend.reg_read(regId).longValue();
                    if (currentVal == oldValues[oldValueIndex]) {
                        continue;
                    }
                } catch (Exception ignored) {}
            }
            if (emulator.is32Bit()) {
                if ((regId >= ArmConst.UC_ARM_REG_R0 && regId <= ArmConst.UC_ARM_REG_R12) ||
                        regId == ArmConst.UC_ARM_REG_LR || regId == ArmConst.UC_ARM_REG_SP ||
                        regId == ArmConst.UC_ARM_REG_CPSR) {
                    if (forWriteRegs) {
                        builder.append(" =>");
                        forWriteRegs = false;
                    }
                    if (regId == ArmConst.UC_ARM_REG_CPSR) {
                        Cpsr cpsr = Cpsr.getArm(backend);
                        builder.append(String.format(Locale.US, " cpsr: N=%d, Z=%d, C=%d, V=%d",
                                cpsr.isNegative() ? 1 : 0,
                                cpsr.isZero() ? 1 : 0,
                                cpsr.hasCarry() ? 1 : 0,
                                cpsr.isOverflow() ? 1 : 0));
                    } else {
                        int value = backend.reg_read(regId).intValue();
                        builder.append(' ').append(regName).append("=0x").append(Long.toHexString(value & 0xffffffffL));
                    }
                } else if (regId >= ArmConst.UC_ARM_REG_D0 && regId <= ArmConst.UC_ARM_REG_D31) {
                    if (forWriteRegs) {
                        builder.append(" =>");
                        forWriteRegs = false;
                    }
                    try {
                        byte[] vector = backend.reg_read_vector(regId);
                        builder.append("\n        ").append(regName).append("=").append(formatVectorHex(vector));
                    } catch (Exception ignored) {}
                } else if (regId >= ArmConst.UC_ARM_REG_S0 && regId <= ArmConst.UC_ARM_REG_S31) {
                    if (forWriteRegs) {
                        builder.append("\n      => ");
                        forWriteRegs = false;
                    }
                    int value = backend.reg_read(regId).intValue();
                    builder.append("\n        ").append(regName).append("=0x").append(Long.toHexString(value & 0xffffffffL));
                }
            } else {
                if ((regId >= Arm64Const.UC_ARM64_REG_X0 && regId <= Arm64Const.UC_ARM64_REG_X28) ||
                        (regId >= Arm64Const.UC_ARM64_REG_X29 && regId <= Arm64Const.UC_ARM64_REG_SP)) {
                    if (forWriteRegs) {
                        builder.append(" =>");
                        forWriteRegs = false;
                    }
                    if (regId == Arm64Const.UC_ARM64_REG_NZCV) {
                        Cpsr cpsr = Cpsr.getArm64(backend);
                        if (cpsr.isA32()) {
                            builder.append(String.format(Locale.US, " cpsr: N=%d, Z=%d, C=%d, V=%d",
                                    cpsr.isNegative() ? 1 : 0,
                                    cpsr.isZero() ? 1 : 0,
                                    cpsr.hasCarry() ? 1 : 0,
                                    cpsr.isOverflow() ? 1 : 0));
                        } else {
                            builder.append(String.format(Locale.US, " nzcv: N=%d, Z=%d, C=%d, V=%d",
                                    cpsr.isNegative() ? 1 : 0,
                                    cpsr.isZero() ? 1 : 0,
                                    cpsr.hasCarry() ? 1 : 0,
                                    cpsr.isOverflow() ? 1 : 0));
                        }
                    } else {
                        long value = backend.reg_read(regId).longValue();
                        builder.append(' ').append(regName).append("=0x").append(Long.toHexString(value));
                    }
                } else if (regId >= Arm64Const.UC_ARM64_REG_W0 && regId <= Arm64Const.UC_ARM64_REG_W30) {
                    if (forWriteRegs) {
                        builder.append(" =>");
                        forWriteRegs = false;
                    }
                    int value = backend.reg_read(regId).intValue();
                    builder.append(' ').append(regName).append("=0x").append(Long.toHexString(value & 0xffffffffL));
                } else if (regId >= Arm64Const.UC_ARM64_REG_Q0 && regId <= Arm64Const.UC_ARM64_REG_Q31 || regId >= Arm64Const.UC_ARM64_REG_V0 && regId <= Arm64Const.UC_ARM64_REG_V31) {
                    if (forWriteRegs) {
                        builder.append(" =>");
                        forWriteRegs = false;
                    }
                    try {
                        byte[] vector = backend.reg_read_vector(regId);
                        builder.append(' ').append(regName).append("=0x").append(formatVectorHex(vector));
                    } catch (Exception ignored) {}
                } else if (regId >= Arm64Const.UC_ARM64_REG_D0 && regId <= Arm64Const.UC_ARM64_REG_D31) {
                    if (forWriteRegs) {
                        builder.append(" =>");
                        forWriteRegs = false;
                    }
                    try {
                        long value = backend.reg_read(regId).longValue();
                        builder.append(' ').append(regName).append("=0x").append(Long.toHexString(value));
                    } catch (Exception ignored) {}
                } else if (regId >= Arm64Const.UC_ARM64_REG_S0 && regId <= Arm64Const.UC_ARM64_REG_S31) {
                    if (forWriteRegs) {
                        builder.append(" =>");
                        forWriteRegs = false;
                    }
                    try {
                        int value = backend.reg_read(regId).intValue();
                        builder.append(' ').append(regName).append("=0x").append(Long.toHexString(value & 0xffffffffL));
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private int findOldValueIndex(int regId) {
        for (int i = 0; i < oldValueCount; i++) {
            if (oldValueRegIds[i] == regId) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isComparableScalarRegister(int regId) {
        return regId != 0 &&
                regId != ArmConst.UC_ARM_REG_CPSR &&
                regId != Arm64Const.UC_ARM64_REG_NZCV &&
                !isArmSimdFpRegister(regId) &&
                !isArm64SimdFpRegister(regId);
    }

    private static boolean isArmSimdFpRegister(int regId) {
        return (regId >= ArmConst.UC_ARM_REG_D0 && regId <= ArmConst.UC_ARM_REG_D31) ||
                (regId >= ArmConst.UC_ARM_REG_Q0 && regId <= ArmConst.UC_ARM_REG_Q15) ||
                (regId >= ArmConst.UC_ARM_REG_S0 && regId <= ArmConst.UC_ARM_REG_S31);
    }

    private static boolean isArm64SimdFpRegister(int regId) {
        return (regId >= Arm64Const.UC_ARM64_REG_B0 && regId <= Arm64Const.UC_ARM64_REG_B31) ||
                (regId >= Arm64Const.UC_ARM64_REG_H0 && regId <= Arm64Const.UC_ARM64_REG_H31) ||
                (regId >= Arm64Const.UC_ARM64_REG_S0 && regId <= Arm64Const.UC_ARM64_REG_S31) ||
                (regId >= Arm64Const.UC_ARM64_REG_D0 && regId <= Arm64Const.UC_ARM64_REG_D31) ||
                (regId >= Arm64Const.UC_ARM64_REG_Q0 && regId <= Arm64Const.UC_ARM64_REG_Q31) ||
                (regId >= Arm64Const.UC_ARM64_REG_V0 && regId <= Arm64Const.UC_ARM64_REG_V31);
    }

    private static String formatVectorHex(byte[] vector) {
        if (vector == null || vector.length == 0) return "0";
        StringBuilder hex = new StringBuilder();
        boolean allZero = true;
        boolean leading = true;
        for (int i = vector.length - 1; i >= 0; i--) {
            byte b = vector[i];
            if (b != 0) allZero = false;
            
            if (!allZero) {
                 if (leading) {
                     hex.append(Integer.toHexString(b & 0xFF));
                     leading = false;
                 } else {
                     hex.append(String.format("%02x", b & 0xFF));
                 }
            }
        }
        if (allZero) return "0";
        return hex.toString();
    }

}
