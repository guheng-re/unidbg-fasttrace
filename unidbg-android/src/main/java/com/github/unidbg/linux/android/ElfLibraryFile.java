package com.github.unidbg.linux.android;

import com.github.unidbg.Emulator;
import com.github.unidbg.Utils;
import com.github.unidbg.spi.LibraryFile;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ElfLibraryFile implements LibraryFile {

    private final File elfFile;
    private final boolean is64Bit;
    private final String mapPath;

    public ElfLibraryFile(File elfFile, boolean is64Bit) {
        this(elfFile, is64Bit, null);
    }

    /** {@code mapPath} is the pathname shown in {@code /proc/self/maps} (and {@link #getPath()}). */
    public ElfLibraryFile(File elfFile, boolean is64Bit, String mapPath) {
        this.elfFile = elfFile;
        this.is64Bit = is64Bit;
        this.mapPath = mapPath;
    }

    @Override
    public long getFileSize() {
        return elfFile.length();
    }

    @Override
    public String getName() {
        return elfFile.getName();
    }

    @Override
    public String getMapRegionName() {
        return getPath();
    }

    @Override
    public LibraryFile resolveLibrary(Emulator<?> emulator, String soName) {
        File file = new File(elfFile.getParentFile(), soName);
        return file.canRead() ? new ElfLibraryFile(file, is64Bit) : null;
    }

    @Override
    public ByteBuffer mapBuffer() throws IOException {
        return Utils.mapBuffer(elfFile);
    }

    @Override
    public String getPath() {
        if (mapPath != null && !mapPath.isEmpty()) {
            return mapPath;
        }
        String name = getName();
        if (name.endsWith(".so")) {
            return "/system/" + (is64Bit ? "lib64/" : "lib/") + name;
        } else {
            return "/system/bin/" + name;
        }
    }

}
