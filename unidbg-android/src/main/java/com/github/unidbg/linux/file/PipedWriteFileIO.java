package com.github.unidbg.linux.file;

import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.BaseAndroidFileIO;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.utils.Inspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PipedOutputStream;

public class PipedWriteFileIO extends BaseAndroidFileIO implements AndroidFileIO {

    private static final Logger log = LoggerFactory.getLogger(PipedWriteFileIO.class);

    private final int writefd;
    private final PipedOutputStream outputStream;

    public PipedWriteFileIO(PipedOutputStream outputStream, int writefd) {
        super(IOConstants.O_WRONLY);

        this.outputStream = outputStream;
        this.writefd = writefd;
    }

    @Override
    public int write(byte[] data) {
        try {
            if (log.isDebugEnabled()) {
                log.debug(Inspector.inspectString(data, "write fd=" + writefd));
            }
            outputStream.write(data);
            return data.length;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        try {
            outputStream.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int fstat(com.github.unidbg.Emulator<?> emulator,
                     com.github.unidbg.file.linux.StatStructure stat) {
        AndroidFileOwner.fillAnon(emulator, stat, com.github.unidbg.unix.IO.S_IFIFO | 0600, getPath());
        return 0;
    }

    @Override
    public String getPath() {
        return "pipe:[" + writefd + "]";
    }

    @Override
    public String toString() {
        return "PipedWrite: " + writefd;
    }
}
