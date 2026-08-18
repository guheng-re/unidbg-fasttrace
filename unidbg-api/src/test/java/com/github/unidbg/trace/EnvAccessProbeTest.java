package com.github.unidbg.trace;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnvAccessProbeTest {

    @After
    public void reset() {
        EnvAccessProbe.clearOverride();
    }

    @Test
    public void testDefaultEnabled() {
        EnvAccessProbe.clearOverride();
        String previous = System.getProperty(EnvAccessProbe.SYSTEM_PROPERTY);
        try {
            System.clearProperty(EnvAccessProbe.SYSTEM_PROPERTY);
            assertTrue(EnvAccessProbe.isEnabled());
            System.setProperty(EnvAccessProbe.SYSTEM_PROPERTY, "false");
            assertFalse(EnvAccessProbe.isEnabled());
            System.setProperty(EnvAccessProbe.SYSTEM_PROPERTY, "true");
            assertTrue(EnvAccessProbe.isEnabled());
        } finally {
            if (previous == null) {
                System.clearProperty(EnvAccessProbe.SYSTEM_PROPERTY);
            } else {
                System.setProperty(EnvAccessProbe.SYSTEM_PROPERTY, previous);
            }
        }
    }

    @Test
    public void testInterestingPathAndJni() {
        assertTrue(EnvAccessProbe.isInterestingPath("/proc/cpuinfo"));
        assertTrue(EnvAccessProbe.isInterestingPath("/sys/class/net/wlan0/address"));
        assertTrue(EnvAccessProbe.isInterestingPath("/dev/urandom"));
        assertFalse(EnvAccessProbe.isInterestingPath("/data/app/base.apk"));
        assertTrue(EnvAccessProbe.isInterestingJniSignature(
                "android/os/Build->MODEL:Ljava/lang/String;"));
        assertTrue(EnvAccessProbe.isInterestingJniSignature(
                "android/net/wifi/WifiManager->getConnectionInfo()Landroid/net/wifi/WifiInfo;"));
        assertFalse(EnvAccessProbe.isInterestingJniSignature(
                "java/util/zip/GZIPOutputStream->write([B)V"));
        assertTrue(EnvAccessProbe.isNetworkIoctl(0x8927L));
        assertFalse(EnvAccessProbe.isNetworkIoctl(0x5401L));
    }

    @Test
    public void testOverrideBeatsProperty() {
        System.setProperty(EnvAccessProbe.SYSTEM_PROPERTY, "false");
        try {
            EnvAccessProbe.setEnabled(true);
            assertTrue(EnvAccessProbe.isEnabled());
            EnvAccessProbe.setEnabled(false);
            assertFalse(EnvAccessProbe.isEnabled());
        } finally {
            EnvAccessProbe.clearOverride();
            System.clearProperty(EnvAccessProbe.SYSTEM_PROPERTY);
        }
    }
}
