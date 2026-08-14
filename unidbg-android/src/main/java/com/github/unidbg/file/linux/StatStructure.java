package com.github.unidbg.file.linux;

import com.github.unidbg.pointer.UnidbgStructure;
import com.sun.jna.Pointer;

public abstract class StatStructure extends UnidbgStructure {

    public StatStructure(Pointer p) {
        super(p);
    }

    public long st_dev;
    public long st_ino;
    public int st_mode;
    public int st_nlink;
    public int st_uid;
    public int st_gid;
    public long st_rdev;
    public long st_size;
    public int st_blksize;
    public long st_blocks;

    /**
     * Convert epoch milliseconds to {@code tv_sec} via {@link Math#floorDiv}(millis, 1000).
     * Correct for negative millis (unlike truncating division).
     */
    protected static long millisToSeconds(long millis) {
        return Math.floorDiv(millis, 1000L);
    }

    /**
     * Convert epoch milliseconds plus an extra nanosecond adjustment to {@code tv_nsec}
     * in {@code [0, 999_999_999]}, including for negative millis:
     * {@code floorMod(millis, 1000) * 1_000_000 + floorMod(tvNsec, 1_000_000)}.
     */
    protected static long millisToNanos(long millis, long tvNsec) {
        return Math.floorMod(millis, 1000L) * 1000000L
                + Math.floorMod(tvNsec, 1000000L);
    }

    /**
     * @param st_atim millis
     */
    public abstract void setSt_atim(long st_atim, long tv_nsec);

    /**
     * @param st_mtim millis
     */
    public abstract void setSt_mtim(long st_mtim, long tv_nsec);

    /**
     * @param st_ctim millis
     */
    public abstract void setSt_ctim(long st_ctim, long tv_nsec);

    /**
     * @param lastModified millis
     */
    public final void setLastModification(long lastModified) {
        setLastModification(lastModified, 0L);
    }

    /**
     * @param lastModified millis
     */
    public final void setLastModification(long lastModified, long tv_nsec) {
        setSt_atim(lastModified, tv_nsec);
        setSt_mtim(lastModified, tv_nsec);
        setSt_ctim(lastModified, tv_nsec);
    }

    public void setSt_ino(long st_ino) {
        this.st_ino = st_ino;
    }

}
