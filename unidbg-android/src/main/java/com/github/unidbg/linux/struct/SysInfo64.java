package com.github.unidbg.linux.struct;

import com.github.unidbg.pointer.UnidbgStructure;
import com.sun.jna.Pointer;

import java.util.Arrays;
import java.util.List;

/**
 * Linux {@code struct sysinfo} on 64-bit ({@code __kernel_long_t}/{@code __kernel_ulong_t}
 * are 8 bytes). Packed size is 112: {@code procs}/{@code pad} are followed by 4-byte
 * alignment before {@code totalhigh}; {@code _f} is empty on LP64.
 */
public class SysInfo64 extends UnidbgStructure {

    public SysInfo64(Pointer p) {
        super(p);
    }

    public long uptime;
    public long[] loads = new long[3];
    public long totalRam;
    public long freeRam;
    public long sharedRam;
    public long bufferRam;
    public long totalSwap;
    public long freeSwap;
    public short procs;
    public short pad;
    public long totalHigh;
    public long freeHigh;
    public int mem_unit;

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("uptime", "loads", "totalRam", "freeRam", "sharedRam", "bufferRam",
                "totalSwap", "freeSwap", "procs", "pad", "totalHigh", "freeHigh", "mem_unit");
    }
}
