package com.github.unidbg.linux.file;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

public class RandomFileIO extends DriverFileIO {
    static int num = 0;
    private static final Logger log = LoggerFactory.getLogger(RandomFileIO.class);

    private final Emulator<?> emulator;
    private final TraceEnvironmentConfig environmentConfig;

    public RandomFileIO(Emulator<?> emulator, String path) {
        super(emulator, IOConstants.O_RDONLY, path);
        this.emulator = emulator;
        this.environmentConfig = TraceEnvironmentConfig.get(emulator);
    }

    @Override
    public int read(Backend backend, Pointer buffer, int count) {
        byte[] configured = environmentConfig == null ? null : environmentConfig.getRandomBytes(getRandomKey(), count);
        if (configured != null) {
            buffer.write(0, configured, 0, configured.length);
            log.info("[随机点] RandomFileIO.read path={}, count={}, bytes={}", getPath(), count, toHex(configured));
            TraceEnvironmentEventSink.emit(emulator, "random", "read(\"" + getPath() + "\")", configured,
                    "json-config", "读取随机设备 " + getPath());
            return count;
        }

        int total = 0;
        byte[] buf = new byte[Math.min(0x1000, count)];
        buf = new byte[]{(byte) (num), (byte) 0x00, 0x00, (byte) (0xf0 + num)};

        num += 1;
        // randBytes(buf);
        log.info("[随机点] RandomFileIO.read, 请关注, 这里被固定了！ count={}, bytes={}", count, toHex(buf));

        Pointer pointer = buffer;
        while (total < count) {
            int read = Math.min(buf.length, count - total);
            pointer.write(0, buf, 0, read);
            total += read;
            pointer = pointer.share(read);
        }
        TraceEnvironmentEventSink.emit(emulator, "random", "read(\"" + getPath() + "\")", buf,
                "unidbg-default", "读取随机设备 " + getPath());
        return total;
    }

    private String getRandomKey() {
        if ("/dev/random".equals(getPath())) {
            return "devRandomHex";
        }
        if ("/dev/srandom".equals(getPath())) {
            return "devSrandomHex";
        }
        return "devUrandomHex";
    }

    public static String toHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02X ", b));
        }
        return hexString.toString().trim();
    }

    protected void randBytes(byte[] bytes) {
        ThreadLocalRandom.current().nextBytes(bytes);
    }
}
