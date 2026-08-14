package com.github.unidbg.linux.file;

import com.github.unidbg.file.linux.IOConstants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ByteArrayFileIOPathTest {

    @Test
    public void testGetPathAndToString() {
        String path = "/proc/trace-stat-test";
        ByteArrayFileIO io = new ByteArrayFileIO(IOConstants.O_RDONLY, path, new byte[]{'a', 'b'});
        assertEquals(path, io.getPath());
        assertEquals(path, io.toString());
        assertEquals(io.getPath(), io.toString());
    }
}
