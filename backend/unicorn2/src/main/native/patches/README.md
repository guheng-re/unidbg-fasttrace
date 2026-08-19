# Windows Unicorn2 QHT / VirtualAlloc patches

These patches apply to [zhkl0228/unicorn](https://github.com/zhkl0228/unicorn) branch `unicorn2` (base `548eda4`).

## Symptom

On Windows, `unicorn.dll` could `EXCEPTION_ACCESS_VIOLATION` inside `uc_emu_start` while walking `qht_bucket.next` (offset `0x30`). The host address often looks like Java heap (`0x1f……`). It is not a guest `UC_ERR_FETCH_UNMAPPED`.

## Cause

QEMU hash table overflow buckets were allocated with `qemu_memalign` → `VirtualAlloc(NULL, 64, MEM_COMMIT, …)` and freed with `qemu_vfree` → `VirtualFree`. On Windows:

- `VirtualAlloc` without `MEM_RESERVE` (and with a 64-byte size) is not a standalone 64-byte mapping.
- `VirtualFree` of that pointer can smash or unmap an unrelated reservation.
- TB-hash `AUTO_RESIZE` then calls `qht_chain_destroy`, which follows a stale `next` into unmapped host RAM.

39-bit Android ARM64 maps plus a large number of translated blocks (long strings, JNI init, nativeSig) make the table grow and hit this path.

## Fix

- `qemu/util/oslib-win32.c`: `VirtualAlloc(..., MEM_RESERVE | MEM_COMMIT, PAGE_READWRITE)`.
- `qemu/util/qht.c`: overflow buckets use `_aligned_malloc` / `_aligned_free` on Windows; skip unaligned / smashed `next` pointers.

## Apply

From a clean unicorn tree:

```text
git apply backend/unicorn2/src/main/native/patches/0001-win32-qht-overflow-bucket.patch
```

Then rebuild `windows_64/unicorn.dll`:

- MSVC (no Docker): `backend/unicorn2/src/main/native/build-windows-msvc.ps1`
- Docker MinGW against a local tree: `backend/unicorn2/src/main/native/build-windows-local.ps1`
- Docker that clones upstream: `./build.sh windows_64` (the Dockerfile now applies this patch after clone)
