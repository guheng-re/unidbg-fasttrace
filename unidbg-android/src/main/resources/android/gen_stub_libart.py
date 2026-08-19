#!/usr/bin/env python3
"""Generate a minimal AArch64 ET_DYN stub for /system/lib64/libart.so.

The stub is a real ELF (PHDR + DYNAMIC + SysV hash + dynsym) so:
  * it appears in /proc/self/maps as /system/lib64/libart.so
  * dlopen("libart.so") / dlsym work
  * native ELF walkers (PT_DYNAMIC / DT_SYMTAB) resolve the same names

Exports:
  * art::Runtime::instance_  — pointer to a zeroed fake Runtime object
  * DexFile::OpenMemory / OpenCommon / Open and Dalvik dvmRawDexFileOpenArray
    — one shared stub that builds {vptr=0, begin_=x0, size_=x1} in a small pool
"""
from __future__ import print_function

import os
import struct
import sys

R_AARCH64_RELATIVE = 1027
STB_GLOBAL = 1
STT_FUNC = 2
STT_OBJECT = 1
SHN_ABS_FAKE = 1  # non-zero; walkers skip shndx==0

INSTANCE = "_ZN3art7Runtime9instance_E"

FUNC_SYMS = [
    "_ZN3art7DexFile10OpenMemoryEPKhmRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPS9_",
    "_ZN3art7DexFile10OpenMemoryEPKhmRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPKNS_7OatFileEPS9_",
    "_ZN3art7DexFile10OpenMemoryEPKhmRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPKNS_10OatDexFileEPS9_",
    "_ZN3art7DexFile10OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapES2_PS9_",
    "_ZN3art13DexFileLoader10OpenCommonEPKhmS2_mRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPKNS_10OatDexFileEbbPS9_NS3_10unique_ptrINS_16DexFileContainerENS3_14default_deleteISH_EEEEPNS0_12VerifyResultE",
    "_ZN3art13DexFileLoader10OpenCommonENSt3__110shared_ptrINS_16DexFileContainerEEEPKhmRKNS1_12basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEENS1_8optionalIjEEPKNS_10OatDexFileEbbPSC_PNS_22DexFileLoaderErrorCodeE",
    "_ZNK3art16ArtDexFileLoader4OpenEPKhmRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPKNS_10OatDexFileEbbPS9_",
    "_ZN3art7DexFile10OpenCommonEPKhmRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPKNS_10OatDexFileEbbPS9_PNS0_12VerifyResultE",
    "_Z22dvmRawDexFileOpenArrayPhjPP10RawDexFile",
    "_ZN3art7DexFile4OpenEPKcS2_PNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEPNS3_6vectorIPKS0_NS7_ISD_EEEE",
    "_ZN3art7DexFile4OpenEPKcS2_PNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEPNS3_6vectorINS3_10unique_ptrIKS0_NS3_14default_deleteISD_EEEENS7_ISG_EEEE",
    "_ZN3art7DexFile4OpenEPKcRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEbPS9_PNS3_6vectorINS3_10unique_ptrIKS0_NS3_14default_deleteISF_EEEENS7_ISI_EEEE",
    "_ZNK3art16ArtDexFileLoader4OpenEPKcRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEbbPS9_PNS3_6vectorINS3_10unique_ptrIKNS_7DexFileENS3_14default_deleteISG_EEEENS7_ISJ_EEEE",
    "_ZN3art11ClassLinker19OpenDexFilesFromOatEPKcS2_PNSt3__16vectorINS3_12basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEENS8_ISA_EEEEPNS4_IPKNS_7DexFileENS8_ISG_EEEE",
    "_ZN3art11ClassLinker19OpenDexFilesFromOatEPKcS2_PNSt3__16vectorINS3_12basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEENS8_ISA_EEEE",
    "_ZN3art14OatFileManager19OpenDexFilesFromOatEPKcS2_P8_jobjectP13_jobjectArrayPPKNS_7OatFileEPNSt3__16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEE",
    "_ZN3art14OatFileManager19OpenDexFilesFromOatEPKcP8_jobjectP13_jobjectArrayPPKNS_7OatFileEPNSt3__16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEE",
    "_ZN3art7DexFile4OpenEPKhmRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPKNS_10OatDexFileEbbPS9_",
    "_ZNK3art16ArtDexFileLoader4OpenEPKhmRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPKNS_10OatDexFileEbbPS9_NS3_10unique_ptrINS_16DexFileContainerENS3_14default_deleteISH_EEEE",
]


def u16(v):
    return struct.pack("<H", v)


def u32(v):
    return struct.pack("<I", v & 0xFFFFFFFF)


def u64(v):
    return struct.pack("<Q", v & 0xFFFFFFFFFFFFFFFF)


def adr(rd, offset):
    """A64 ADR Rd, #offset (byte offset from this insn, signed 21-bit)."""
    if offset < 0:
        offset += 1 << 21
    immlo = offset & 3
    immhi = (offset >> 2) & 0x7FFFF
    return u32(0x10000000 | (immlo << 29) | (immhi << 5) | rd)


def insn_openmemory_stub(stub_va, pool_next_va, pool_va):
    """Shared OpenMemory: DexFile {vptr=0, begin_=x0, size_=x1} from a 32-slot pool."""
    # 0x00 adr x8, pool_next
    # 0x04 ldr w9, [x8]
    # 0x08 cmp w9, #32
    # 0x0c b.hs fail            -> 0x34
    # 0x10 add w10, w9, #1
    # 0x14 str w10, [x8]
    # 0x18 adr x8, pool
    # 0x1c add x8, x8, x9, lsl #7
    # 0x20 str xzr, [x8]
    # 0x24 str x0, [x8, #8]
    # 0x28 str x1, [x8, #16]
    # 0x2c mov x0, x8
    # 0x30 ret
    # 0x34 mov x0, xzr
    # 0x38 ret
    out = bytearray()
    out += adr(8, pool_next_va - (stub_va + 0x00))
    out += u32(0xB9400109)  # ldr w9, [x8]
    out += u32(0x7100813F)  # cmp w9, #32
    out += u32(0x54000142)  # b.hs +10 insn = +0x28 -> 0x34
    out += u32(0x1100052A)  # add w10, w9, #1
    out += u32(0xB900010A)  # str w10, [x8]
    out += adr(8, pool_va - (stub_va + 0x18))
    out += u32(0x8B090D08)  # add x8, x8, x9, lsl #7
    out += u32(0xF900011F)  # str xzr, [x8]
    out += u32(0xF9000500)  # str x0, [x8, #8]
    out += u32(0xF9000901)  # str x1, [x8, #16]
    out += u32(0xAA0803E0)  # mov x0, x8
    out += u32(0xD65F03C0)  # ret
    out += u32(0xAA1F03E0)  # mov x0, xzr
    out += u32(0xD65F03C0)  # ret
    assert len(out) == 0x3C
    return bytes(out)


def elf_hash(name):
    h = 0
    for c in name.encode("ascii"):
        h = ((h << 4) + c) & 0xFFFFFFFF
        g = h & 0xF0000000
        if g:
            h ^= g >> 24
        h &= ~g
    return h


def align(n, a):
    return (n + a - 1) & ~(a - 1)


def build():
    names = [""] + [INSTANCE] + FUNC_SYMS + ["libart.so"]
    dynstr = b""
    offs = []
    for n in names:
        offs.append(len(dynstr))
        dynstr += n.encode("ascii") + b"\x00"
    soname_off = offs[len(names) - 1]

    nsyms = 1 + 1 + len(FUNC_SYMS)  # undef + instance + funcs
    dynsym_sz = nsyms * 24
    nbucket = 32
    hash_sz = 8 + 4 * (nbucket + nsyms)
    rela_sz = 24  # one RELATIVE for instance_
    stub_sz = 0x3C
    runtime_sz = 0x500
    pool_slots = 32
    pool_sz = pool_slots * 0x80

    ehdr_sz = 64
    phnum = 3
    phdr_sz = 56 * phnum
    rx_off = 0
    stub_off = align(ehdr_sz + phdr_sz, 16)
    rx_end = stub_off + stub_sz
    rw_off = align(rx_end, 16)
    rw_va = 0x1000

    dyn_off = rw_off
    dyn_va = rw_va
    # 10 tags + DT_NULL
    dyn_count = 11
    dyn_sz = dyn_count * 16

    hash_off = align(dyn_off + dyn_sz, 8)
    hash_va = rw_va + (hash_off - rw_off)
    sym_off = align(hash_off + hash_sz, 8)
    sym_va = rw_va + (sym_off - rw_off)
    str_off = align(sym_off + dynsym_sz, 8)
    str_va = rw_va + (str_off - rw_off)
    rela_off = align(str_off + len(dynstr), 8)
    rela_va = rw_va + (rela_off - rw_off)
    inst_off = align(rela_off + rela_sz, 8)
    inst_va = rw_va + (inst_off - rw_off)
    runtime_off = align(inst_off + 8, 8)
    runtime_va = rw_va + (runtime_off - rw_off)
    pool_next_off = align(runtime_off + runtime_sz, 8)
    pool_next_va = rw_va + (pool_next_off - rw_off)
    pool_off = align(pool_next_off + 4, 8)
    pool_va = rw_va + (pool_off - rw_off)
    rw_end = pool_off + pool_sz
    shoff = align(rw_end, 8)
    shentsize = 64
    shnum = 1  # SHT_NULL only; jelf rejects e_shnum==0
    file_sz = shoff + shentsize * shnum

    stub_va = stub_off  # first PT_LOAD va=0
    stub = insn_openmemory_stub(stub_va, pool_next_va, pool_va)

    # SysV hash
    buckets = [0] * nbucket
    chains = [0] * nsyms
    # symbol names for hash: 0 unused, 1 instance, 2.. funcs
    hash_names = [""] + [INSTANCE] + FUNC_SYMS
    for i, n in enumerate(hash_names):
        if i == 0:
            continue
        h = elf_hash(n) % nbucket
        chains[i] = buckets[h]
        buckets[h] = i
    hash_blob = u32(nbucket) + u32(nsyms)
    for b in buckets:
        hash_blob += u32(b)
    for c in chains:
        hash_blob += u32(c)
    assert len(hash_blob) == hash_sz

    def sym(name_off, info, shndx, value, size):
        return u32(name_off) + bytes([info, 0]) + u16(shndx) + u64(value) + u64(size)

    dynsym = sym(0, 0, 0, 0, 0)
    dynsym += sym(offs[1], (STB_GLOBAL << 4) | STT_OBJECT, SHN_ABS_FAKE, inst_va, 8)
    for i, _n in enumerate(FUNC_SYMS):
        dynsym += sym(offs[2 + i], (STB_GLOBAL << 4) | STT_FUNC, SHN_ABS_FAKE, stub_va, stub_sz)
    assert len(dynsym) == dynsym_sz

    rela = u64(inst_va) + u64(R_AARCH64_RELATIVE) + u64(runtime_va)

    def dt(tag, val):
        return u64(tag) + u64(val)

    dynamic = b"".join([
        dt(14, soname_off),   # DT_SONAME
        dt(5, str_va),        # DT_STRTAB
        dt(6, sym_va),        # DT_SYMTAB
        dt(10, len(dynstr)),  # DT_STRSZ
        dt(11, 24),           # DT_SYMENT
        dt(4, hash_va),       # DT_HASH
        dt(7, rela_va),       # DT_RELA
        dt(8, rela_sz),       # DT_RELASZ
        dt(9, 24),            # DT_RELAENT
        dt(1, 0),             # unused slot kept as DT_NEEDED=0 skipped? use DT_NULL only at end
    ])
    # I accidentally used DT_NEEDED 0. Rebuild cleanly.
    dynamic = b"".join([
        dt(14, soname_off),
        dt(5, str_va),
        dt(6, sym_va),
        dt(10, len(dynstr)),
        dt(11, 24),
        dt(4, hash_va),
        dt(7, rela_va),
        dt(8, rela_sz),
        dt(9, 24),
        dt(0x6ffffffb, 1),  # DT_FLAGS_1 = DF_1_NOW (harmless)
        dt(0, 0),
    ])
    assert len(dynamic) == dyn_sz

    def phdr(p_type, p_flags, off, va, filesz, memsz, align_v):
        return (u32(p_type) + u32(p_flags) + u64(off) + u64(va) + u64(va)
                + u64(filesz) + u64(memsz) + u64(align_v))

    phdrs = (
        phdr(1, 5, 0, 0, rx_end, rx_end, 0x1000)  # PT_LOAD RX
        + phdr(1, 6, rw_off, rw_va, rw_end - rw_off, rw_end - rw_off, 0x1000)  # PT_LOAD RW
        + phdr(2, 6, dyn_off, dyn_va, dyn_sz, dyn_sz, 8)  # PT_DYNAMIC
    )
    assert len(phdrs) == phdr_sz

    ehdr = (
        b"\x7fELF"
        + bytes([2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0])
        + u16(3)       # ET_DYN
        + u16(183)     # EM_AARCH64
        + u32(1)
        + u64(0)       # e_entry
        + u64(64)      # e_phoff
        + u64(shoff)   # e_shoff
        + u32(0)
        + u16(64)
        + u16(56)
        + u16(phnum)
        + u16(shentsize)
        + u16(shnum)
        + u16(0)
    )
    assert len(ehdr) == 64

    buf = bytearray(file_sz)
    def put(off, blob):
        buf[off:off + len(blob)] = blob

    put(0, ehdr)
    put(64, phdrs)
    put(stub_off, stub)
    put(dyn_off, dynamic)
    put(hash_off, hash_blob)
    put(sym_off, dynsym)
    put(str_off, dynstr)
    put(rela_off, rela)
    # instance_ left 0; RELATIVE reloc fills it with load_base+runtime_va
    # runtime / pool / pool_next already zero
    # SHT_NULL (64 zero bytes) already present
    return bytes(buf)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out = os.path.join(here, "sdk23", "lib64", "libart.so")
    data = build()
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "wb") as f:
        f.write(data)
    print("wrote", out, "bytes", len(data))
    return 0


if __name__ == "__main__":
    sys.exit(main())
