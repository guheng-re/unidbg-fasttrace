package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.dvm.api.ApplicationInfo;
import com.github.unidbg.linux.android.dvm.api.PackageInfo;
import com.github.unidbg.linux.android.dvm.api.Signature;
import com.github.unidbg.linux.android.dvm.array.ArrayObject;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AndroidPackagesJniTest {

    private static final String PACKAGES_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"versionName\":\"apk-fallback\","
            + "\"versionCode\":999,"
            + "\"apkPath\":\"/data/app/~~fallback/com.demo.app/base.apk\","
            + "\"dataDir\":\"/data/user/0/com.demo.app\","
            + "\"packages\":["
            + "{\"packageName\":\"com.demo.app\","
            + "\"versionName\":\"1.2.3\","
            + "\"versionCode\":42,"
            + "\"sourceDir\":\"/data/app/~~fixed/com.demo.app-fixed/base.apk\","
            + "\"dataDir\":\"/data/user/0/com.demo.app\","
            + "\"uid\":10001,"
            + "\"enabled\":false,"
            + "\"systemApp\":true,"
            + "\"installerPackageName\":\"com.traceai.installer\","
            + "\"initiatingPackageName\":\"com.traceai.initiator\","
            + "\"originatingPackageName\":\"com.traceai.originator\","
            + "\"firstInstallTimeMillis\":1718000000000,"
            + "\"lastUpdateTimeMillis\":1718000001000,"
            + "\"permissions\":{"
            + "\"android.permission.INTERNET\":true,"
            + "\"android.permission.CAMERA\":false,"
            + "\"com.traceai.permission.CUSTOM\":true},"
            + "\"signaturesHex\":[\"01020304\",\"AABB\"],"
            + "\"signingCertificateHistoryHex\":[\"FFEEDDCC\",\"0011\"]},"
            + "{\"packageName\":\"com.traceai.marker\","
            + "\"versionName\":\"TRACEAI_PKG_VERSION_MARKER_V1\","
            + "\"versionCode\":0,"
            + "\"sourceDir\":\"/data/app/TRACEAI_SOURCE_MARKER_V1/base.apk\","
            + "\"dataDir\":\"/data/user/0/com.traceai.marker\","
            + "\"systemApp\":false,"
            + "\"installerPackageName\":\"com.traceai.installer\","
            + "\"initiatingPackageName\":\"com.traceai.initiator\","
            + "\"originatingPackageName\":\"com.traceai.originator\","
            + "\"firstInstallTimeMillis\":" + Long.MAX_VALUE + ","
            + "\"lastUpdateTimeMillis\":" + Long.MAX_VALUE + ","
            + "\"permissions\":{},"
            + "\"signaturesHex\":[]},"
            + "{\"packageName\":\"com.demo.shared\","
            + "\"uid\":10001,"
            + "\"signaturesHex\":[\"0F0E\"],"
            + "\"signingCertificateHistoryHex\":[\"AA\",\"BB\",\"CC\"]},"
            + "{\"packageName\":\"com.other.uid\","
            + "\"uid\":10002,"
            + "\"signingCertificateHistoryHex\":[\"1122\"]},"
            + "{\"packageName\":\"com.tie.a\","
            + "\"uid\":10005,"
            + "\"signaturesHex\":[\"99\"]},"
            + "{\"packageName\":\"com.tie.b\","
            + "\"uid\":10005,"
            + "\"signaturesHex\":[\"88\"]},"
            + "{\"packageName\":\"com.nosig.uid\","
            + "\"uid\":10006},"
            + "{\"packageName\":\"com.null.version\","
            + "\"versionName\":null},"
            + "{\"packageName\":\"com.null.paths\","
            + "\"sourceDir\":null,"
            + "\"dataDir\":null,"
            + "\"installerPackageName\":null,"
            + "\"initiatingPackageName\":null,"
            + "\"originatingPackageName\":null},"
            + "{\"packageName\":\"com.only.name\"}"
            + "]}"
            + "}";

    @Test
    public void testPackagesJniVarArg32() throws Exception {
        runPackagesJni(false, false);
    }

    @Test
    public void testPackagesJniVaList64() throws Exception {
        runPackagesJni(true, true);
    }

    /**
     * InstallSourceInfo marker from VM A must not authorize getters on VM B (all three names).
     * VM A continues to work; VM B gets UOE and no android_package InstallSourceInfo getter events.
     */
    @Test
    public void testInstallSourceInfoCrossVmRejectedVarArg32() throws Exception {
        runInstallSourceInfoCrossVmRejected(false, false);
    }

    @Test
    public void testInstallSourceInfoCrossVmRejectedVaList64() throws Exception {
        runInstallSourceInfoCrossVmRejected(true, true);
    }

    private static void runInstallSourceInfoCrossVmRejected(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PACKAGES_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> pmA = vmA.resolveClass("android/content/pm/PackageManager").newObject(null);
            DvmObject<?> srcA = invokeGetInstallSourceInfo(jniA, baseA, useVaList, pmA, "com.demo.app");
            assertNotNull(srcA);

            // Owner A still succeeds for all three getters
            assertEquals("com.traceai.installer",
                    invokeInstallSourceString(jniA, baseA, useVaList, srcA, "getInstallingPackageName"));
            assertEquals("com.traceai.initiator",
                    invokeInstallSourceString(jniA, baseA, useVaList, srcA, "getInitiatingPackageName"));
            assertEquals("com.traceai.originator",
                    invokeInstallSourceString(jniA, baseA, useVaList, srcA, "getOriginatingPackageName"));

            String[] getters = {
                    "getInstallingPackageName",
                    "getInitiatingPackageName",
                    "getOriginatingPackageName"
            };
            for (String getter : getters) {
                try {
                    invokeInstallSourceString(jniB, baseB, useVaList, srcA, getter);
                    fail("expected UOE for cross-VM InstallSourceInfo." + getter);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains(getter));
                }
            }
            for (CapturedEvent e : sinkB.events) {
                assertFalse("leak InstallSourceInfo getter event to VM B: " + e.api,
                        "android_package".equals(e.kind)
                                && e.api != null
                                && e.api.startsWith("InstallSourceInfo."));
            }

            // Control: A still works after B rejection
            assertEquals("com.traceai.installer",
                    invokeInstallSourceString(jniA, baseA, useVaList, srcA, "getInstallingPackageName"));
            assertEquals("com.traceai.originator",
                    invokeInstallSourceString(jniA, baseA, useVaList, srcA, "getOriginatingPackageName"));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    /**
     * ApplicationInfo marker from VM A must not authorize field reads on VM B
     * (packageName / sourceDir / uid / enabled). VM A continues to work; VM B gets UOE
     * and no sidecar events.
     */
    @Test
    public void testApplicationInfoCrossVmRejectedVarArg32() throws Exception {
        runApplicationInfoCrossVmRejected(false, false);
    }

    @Test
    public void testApplicationInfoCrossVmRejectedVaList64() throws Exception {
        runApplicationInfoCrossVmRejected(true, true);
    }

    private static void runApplicationInfoCrossVmRejected(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PACKAGES_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> pmA = vmA.resolveClass("android/content/pm/PackageManager").newObject(null);
            DvmObject<?> appA = invokeGetApplicationInfo(jniA, baseA, useVaList, pmA, "com.demo.app", 0);
            assertNotNull(appA);

            // Owner A still succeeds for configured fields
            assertEquals("com.demo.app", getAppStringField(jniA, baseA, appA, "packageName"));
            assertEquals("/data/app/~~fixed/com.demo.app-fixed/base.apk",
                    getAppStringField(jniA, baseA, appA, "sourceDir"));
            assertEquals(10001, getAppIntField(jniA, baseA, appA, "uid"));
            assertEquals(false, getAppBooleanField(jniA, baseA, appA, "enabled"));

            sinkB.events.clear();
            try {
                getAppStringField(jniB, baseB, appA, "packageName");
                fail("expected UOE for cross-VM ApplicationInfo.packageName");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ApplicationInfo->packageName"));
            }
            try {
                getAppStringField(jniB, baseB, appA, "sourceDir");
                fail("expected UOE for cross-VM ApplicationInfo.sourceDir");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ApplicationInfo->sourceDir"));
            }
            try {
                getAppIntField(jniB, baseB, appA, "uid");
                fail("expected UOE for cross-VM ApplicationInfo.uid");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ApplicationInfo->uid"));
            }
            try {
                getAppBooleanField(jniB, baseB, appA, "enabled");
                fail("expected UOE for cross-VM ApplicationInfo.enabled");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ApplicationInfo->enabled"));
            }
            assertTrue("cross-VM ApplicationInfo must not emit sidecar on VM B",
                    sinkB.events.isEmpty());

            // Control: A still works after B rejection
            assertEquals("com.demo.app", getAppStringField(jniA, baseA, appA, "packageName"));
            assertEquals("/data/app/~~fixed/com.demo.app-fixed/base.apk",
                    getAppStringField(jniA, baseA, appA, "sourceDir"));
            assertEquals(10001, getAppIntField(jniA, baseA, appA, "uid"));
            assertEquals(false, getAppBooleanField(jniA, baseA, appA, "enabled"));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    /**
     * PackageInfo marker from VM A (getPackageInfo and a getInstalledPackages element)
     * must not authorize field reads on VM B (packageName / versionCode / firstInstallTime /
     * signatures). VM A continues to work; VM B gets UOE and no sidecar events.
     */
    @Test
    public void testPackageInfoCrossVmRejectedVarArg32() throws Exception {
        runPackageInfoCrossVmRejected(false, false);
    }

    @Test
    public void testPackageInfoCrossVmRejectedVaList64() throws Exception {
        runPackageInfoCrossVmRejected(true, true);
    }

    private static void runPackageInfoCrossVmRejected(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PACKAGES_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> pmA = vmA.resolveClass("android/content/pm/PackageManager").newObject(null);
            DvmObject<?> infoFromGet = invokeGetPackageInfo(jniA, baseA, useVaList, pmA, "com.demo.app", 0);
            assertNotNull(infoFromGet);
            DvmObject<?> pkgListObj = invokeGetInstalledList(jniA, baseA, useVaList, pmA,
                    "getInstalledPackages", 0);
            assertTrue(pkgListObj instanceof ArrayListObject);
            DvmObject<?> infoFromList = invokeListGet(jniA, baseA, useVaList,
                    (ArrayListObject) pkgListObj, 0);
            assertNotNull(infoFromList);

            DvmObject<?>[] markers = {infoFromGet, infoFromList};
            String[] origins = {"getPackageInfo", "getInstalledPackages"};
            for (int i = 0; i < markers.length; i++) {
                DvmObject<?> marker = markers[i];
                String origin = origins[i];

                // Owner A still succeeds for configured fields
                assertEquals("com.demo.app", getStringField(jniA, baseA, marker, "packageName"));
                assertEquals(42, getIntField(jniA, baseA, marker, "versionCode"));
                assertEquals(1718000000000L, getLongField(jniA, baseA, marker, "firstInstallTime"));
                DvmObject<?> sigsA = getSignaturesField(jniA, baseA, marker);
                assertTrue(sigsA instanceof ArrayObject);
                assertEquals(2, ((ArrayObject) sigsA).length());

                sinkB.events.clear();
                try {
                    getStringField(jniB, baseB, marker, "packageName");
                    fail("expected UOE for cross-VM PackageInfo.packageName via " + origin);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("PackageInfo->packageName"));
                }
                try {
                    getIntField(jniB, baseB, marker, "versionCode");
                    fail("expected UOE for cross-VM PackageInfo.versionCode via " + origin);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("PackageInfo->versionCode"));
                }
                try {
                    getLongField(jniB, baseB, marker, "firstInstallTime");
                    fail("expected UOE for cross-VM PackageInfo.firstInstallTime via " + origin);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("PackageInfo->firstInstallTime"));
                }
                try {
                    getSignaturesField(jniB, baseB, marker);
                    fail("expected UOE for cross-VM PackageInfo.signatures via " + origin);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("PackageInfo->signatures"));
                }
                assertTrue("cross-VM PackageInfo via " + origin + " must not emit sidecar on VM B",
                        sinkB.events.isEmpty());

                // Control: A still works after B rejection
                assertEquals("com.demo.app", getStringField(jniA, baseA, marker, "packageName"));
                assertEquals(42, getIntField(jniA, baseA, marker, "versionCode"));
                assertEquals(1718000000000L, getLongField(jniA, baseA, marker, "firstInstallTime"));
                DvmObject<?> sigsAfter = getSignaturesField(jniA, baseA, marker);
                assertTrue(sigsAfter instanceof ArrayObject);
                assertEquals(2, ((ArrayObject) sigsAfter).length());
            }
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    /**
     * SigningInfo marker from VM A (via PackageInfo.signingInfo) must not authorize
     * getApkContentsSigners / getSigningCertificateHistory / hasMultipleSigners on VM B.
     * VM A continues to return the original signing config; VM B gets UOE and no sidecar.
     */
    @Test
    public void testSigningInfoCrossVmRejectedVarArg32() throws Exception {
        runSigningInfoCrossVmRejected(false, false);
    }

    @Test
    public void testSigningInfoCrossVmRejectedVaList64() throws Exception {
        runSigningInfoCrossVmRejected(true, true);
    }

    private static void runSigningInfoCrossVmRejected(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PACKAGES_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> pmA = vmA.resolveClass("android/content/pm/PackageManager").newObject(null);
            DvmObject<?> infoA = invokeGetPackageInfo(jniA, baseA, useVaList, pmA, "com.demo.app", 0);
            assertNotNull(infoA);
            DvmObject<?> signingA = getSigningInfoField(jniA, baseA, infoA);
            assertNotNull(signingA);

            // Owner A still succeeds for the original signing config (multi-signer demo app)
            assertTrue(invokeHasMultipleSigners(jniA, baseA, useVaList, signingA));
            DvmObject<?> contentsA = invokeGetApkContentsSigners(jniA, baseA, useVaList, signingA);
            assertTrue(contentsA instanceof ArrayObject);
            assertEquals(2, ((ArrayObject) contentsA).length());
            assertNull(invokeGetSigningCertificateHistory(jniA, baseA, useVaList, signingA));

            sinkB.events.clear();
            try {
                invokeGetApkContentsSigners(jniB, baseB, useVaList, signingA);
                fail("expected UOE for cross-VM SigningInfo.getApkContentsSigners");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getApkContentsSigners"));
            }
            try {
                invokeGetSigningCertificateHistory(jniB, baseB, useVaList, signingA);
                fail("expected UOE for cross-VM SigningInfo.getSigningCertificateHistory");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSigningCertificateHistory"));
            }
            try {
                invokeHasMultipleSigners(jniB, baseB, useVaList, signingA);
                fail("expected UOE for cross-VM SigningInfo.hasMultipleSigners");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasMultipleSigners"));
            }
            assertTrue("cross-VM SigningInfo must not emit sidecar on VM B",
                    sinkB.events.isEmpty());

            // Control: A still returns the original signing config after B rejection
            assertTrue(invokeHasMultipleSigners(jniA, baseA, useVaList, signingA));
            DvmObject<?> contentsAfter = invokeGetApkContentsSigners(jniA, baseA, useVaList, signingA);
            assertTrue(contentsAfter instanceof ArrayObject);
            assertEquals(2, ((ArrayObject) contentsAfter).length());
            assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04},
                    invokeSignatureToByteArray(jniA, baseA, useVaList,
                            ((ArrayObject) contentsAfter).getValue()[0]));
            assertArrayEquals(new byte[]{(byte) 0xAA, (byte) 0xBB},
                    invokeSignatureToByteArray(jniA, baseA, useVaList,
                            ((ArrayObject) contentsAfter).getValue()[1]));
            assertNull(invokeGetSigningCertificateHistory(jniA, baseA, useVaList, signingA));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static void runPackagesJni(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PACKAGES_JSON);
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> pm = vm.resolveClass("android/content/pm/PackageManager").newObject(null);

            // configured query + fields
            DvmObject<?> info = invokeGetPackageInfo(jni, baseVM, useVaList, pm, "com.demo.app", 0x40);
            assertNotNull(info);
            assertEquals("android/content/pm/PackageInfo", info.getObjectType().getClassName());
            assertEquals("com.demo.app", getStringField(jni, baseVM, info, "packageName"));
            assertEquals("1.2.3", getStringField(jni, baseVM, info, "versionName"));
            assertEquals(42, getIntField(jni, baseVM, info, "versionCode"));
            assertEquals(1718000000000L, getLongField(jni, baseVM, info, "firstInstallTime"));
            assertEquals(1718000001000L, getLongField(jni, baseVM, info, "lastUpdateTime"));
            assertEquals("com.traceai.installer",
                    invokeGetInstallerPackageName(jni, baseVM, useVaList, pm, "com.demo.app"));

            // ---- PackageInfo.signatures from signaturesHex ----
            DvmObject<?> demoSigsObj = getSignaturesField(jni, baseVM, info);
            assertTrue(demoSigsObj instanceof ArrayObject);
            ArrayObject demoSigs = (ArrayObject) demoSigsObj;
            assertEquals(2, demoSigs.length());
            DvmObject<?>[] demoSigItems = demoSigs.getValue();
            assertTrue(demoSigItems[0] instanceof Signature);
            assertTrue(demoSigItems[1] instanceof Signature);
            Signature demoSig0 = (Signature) demoSigItems[0];
            Signature demoSig1 = (Signature) demoSigItems[1];
            // order + toByteArray / toCharsString / hashCode
            assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04},
                    invokeSignatureToByteArray(jni, baseVM, useVaList, demoSig0));
            assertEquals("01020304",
                    invokeSignatureToCharsString(jni, baseVM, useVaList, demoSig0));
            assertEquals(Arrays.hashCode(new byte[]{0x01, 0x02, 0x03, 0x04}),
                    invokeSignatureHashCode(jni, baseVM, useVaList, demoSig0));
            assertArrayEquals(new byte[]{(byte) 0xAA, (byte) 0xBB},
                    invokeSignatureToByteArray(jni, baseVM, useVaList, demoSig1));
            assertEquals("aabb",
                    invokeSignatureToCharsString(jni, baseVM, useVaList, demoSig1));
            assertEquals(Arrays.hashCode(new byte[]{(byte) 0xAA, (byte) 0xBB}),
                    invokeSignatureHashCode(jni, baseVM, useVaList, demoSig1));

            // marker version strings + Long.MAX install times + installer marker
            DvmObject<?> markerInfo = invokeGetPackageInfo(jni, baseVM, useVaList, pm,
                    "com.traceai.marker", 0);
            assertEquals("TRACEAI_PKG_VERSION_MARKER_V1",
                    getStringField(jni, baseVM, markerInfo, "versionName"));
            assertEquals(0, getIntField(jni, baseVM, markerInfo, "versionCode"));
            assertEquals(Long.MAX_VALUE, getLongField(jni, baseVM, markerInfo, "firstInstallTime"));
            assertEquals(Long.MAX_VALUE, getLongField(jni, baseVM, markerInfo, "lastUpdateTime"));
            assertEquals("com.traceai.installer",
                    invokeGetInstallerPackageName(jni, baseVM, useVaList, pm, "com.traceai.marker"));
            // explicit empty signaturesHex → empty ArrayObject
            DvmObject<?> markerSigsObj = getSignaturesField(jni, baseVM, markerInfo);
            assertTrue(markerSigsObj instanceof ArrayObject);
            assertEquals(0, ((ArrayObject) markerSigsObj).length());

            // signaturesHex field missing on marker package entry → UOE
            DvmObject<?> onlyNameInfo = invokeGetPackageInfo(jni, baseVM, useVaList, pm,
                    "com.only.name", 0);
            try {
                getSignaturesField(jni, baseVM, onlyNameInfo);
                fail("expected UnsupportedOperationException for missing signaturesHex");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PackageInfo->signatures"));
            }

            // unrelated Signature (byte[] constructor) is isolated from package config
            Signature unrelatedSig = new Signature(vm, new byte[]{0x7f, 0x00});
            assertArrayEquals(new byte[]{0x7f, 0x00},
                    invokeSignatureToByteArray(jni, baseVM, useVaList, unrelatedSig));
            assertEquals("7f00",
                    invokeSignatureToCharsString(jni, baseVM, useVaList, unrelatedSig));
            assertEquals(Arrays.hashCode(new byte[]{0x7f, 0x00}),
                    invokeSignatureHashCode(jni, baseVM, useVaList, unrelatedSig));
            // defensive copy: mutating input after construction must not change Signature data
            byte[] mutateSource = new byte[]{0x11, 0x22};
            Signature copySig = new Signature(vm, mutateSource);
            mutateSource[0] = 0x33;
            assertArrayEquals(new byte[]{0x11, 0x22},
                    invokeSignatureToByteArray(jni, baseVM, useVaList, copySig));
            // configured signatures still present after unrelated Signature use
            assertEquals(2, ((ArrayObject) getSignaturesField(jni, baseVM, info)).length());

            // ---- PackageInfo.signingInfo (ConfiguredSigningInfo) ----
            // multi-signer: hasMultiple true, history null (Android multi-signer semantics)
            DvmObject<?> demoSigningInfo = getSigningInfoField(jni, baseVM, info);
            assertNotNull(demoSigningInfo);
            assertEquals("android/content/pm/SigningInfo", demoSigningInfo.getObjectType().getClassName());
            assertTrue(invokeHasMultipleSigners(jni, baseVM, useVaList, demoSigningInfo));
            DvmObject<?> demoContents = invokeGetApkContentsSigners(jni, baseVM, useVaList, demoSigningInfo);
            assertTrue(demoContents instanceof ArrayObject);
            assertEquals(2, ((ArrayObject) demoContents).length());
            assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04},
                    invokeSignatureToByteArray(jni, baseVM, useVaList,
                            ((ArrayObject) demoContents).getValue()[0]));
            assertArrayEquals(new byte[]{(byte) 0xAA, (byte) 0xBB},
                    invokeSignatureToByteArray(jni, baseVM, useVaList,
                            ((ArrayObject) demoContents).getValue()[1]));
            assertNull(invokeGetSigningCertificateHistory(jni, baseVM, useVaList, demoSigningInfo));

            // empty signaturesHex: SigningInfo present, multi=false, empty contents, history falls back to current empty
            DvmObject<?> markerSigningInfo = getSigningInfoField(jni, baseVM, markerInfo);
            assertNotNull(markerSigningInfo);
            assertFalse(invokeHasMultipleSigners(jni, baseVM, useVaList, markerSigningInfo));
            DvmObject<?> markerContents = invokeGetApkContentsSigners(jni, baseVM, useVaList, markerSigningInfo);
            assertTrue(markerContents instanceof ArrayObject);
            assertEquals(0, ((ArrayObject) markerContents).length());
            DvmObject<?> markerHistory = invokeGetSigningCertificateHistory(jni, baseVM, useVaList,
                    markerSigningInfo);
            assertTrue(markerHistory instanceof ArrayObject);
            assertEquals(0, ((ArrayObject) markerHistory).length());

            // single signer + history order
            DvmObject<?> sharedInfo = invokeGetPackageInfo(jni, baseVM, useVaList, pm, "com.demo.shared", 0);
            DvmObject<?> sharedSigningInfo = getSigningInfoField(jni, baseVM, sharedInfo);
            assertFalse(invokeHasMultipleSigners(jni, baseVM, useVaList, sharedSigningInfo));
            DvmObject<?> sharedContents = invokeGetApkContentsSigners(jni, baseVM, useVaList, sharedSigningInfo);
            assertTrue(sharedContents instanceof ArrayObject);
            assertEquals(1, ((ArrayObject) sharedContents).length());
            assertArrayEquals(new byte[]{0x0f, 0x0e},
                    invokeSignatureToByteArray(jni, baseVM, useVaList,
                            ((ArrayObject) sharedContents).getValue()[0]));
            DvmObject<?> sharedHistory = invokeGetSigningCertificateHistory(jni, baseVM, useVaList,
                    sharedSigningInfo);
            assertTrue(sharedHistory instanceof ArrayObject);
            ArrayObject sharedHistoryArr = (ArrayObject) sharedHistory;
            assertEquals(3, sharedHistoryArr.length());
            assertArrayEquals(new byte[]{(byte) 0xAA},
                    invokeSignatureToByteArray(jni, baseVM, useVaList, sharedHistoryArr.getValue()[0]));
            assertArrayEquals(new byte[]{(byte) 0xBB},
                    invokeSignatureToByteArray(jni, baseVM, useVaList, sharedHistoryArr.getValue()[1]));
            assertArrayEquals(new byte[]{(byte) 0xCC},
                    invokeSignatureToByteArray(jni, baseVM, useVaList, sharedHistoryArr.getValue()[2]));

            // history-only package: SigningInfo present; contents/multi UOE; history ordered
            DvmObject<?> historyOnlyInfo = invokeGetPackageInfo(jni, baseVM, useVaList, pm, "com.other.uid", 0);
            DvmObject<?> historyOnlySigningInfo = getSigningInfoField(jni, baseVM, historyOnlyInfo);
            assertNotNull(historyOnlySigningInfo);
            try {
                invokeGetApkContentsSigners(jni, baseVM, useVaList, historyOnlySigningInfo);
                fail("expected UnsupportedOperationException without signaturesHex for getApkContentsSigners");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getApkContentsSigners"));
            }
            try {
                invokeHasMultipleSigners(jni, baseVM, useVaList, historyOnlySigningInfo);
                fail("expected UnsupportedOperationException without signaturesHex for hasMultipleSigners");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasMultipleSigners"));
            }
            DvmObject<?> historyOnlyHist = invokeGetSigningCertificateHistory(jni, baseVM, useVaList,
                    historyOnlySigningInfo);
            assertTrue(historyOnlyHist instanceof ArrayObject);
            assertEquals(1, ((ArrayObject) historyOnlyHist).length());
            assertArrayEquals(new byte[]{0x11, 0x22},
                    invokeSignatureToByteArray(jni, baseVM, useVaList,
                            ((ArrayObject) historyOnlyHist).getValue()[0]));

            // both sig fields missing → signingInfo UOE
            try {
                getSigningInfoField(jni, baseVM, onlyNameInfo);
                fail("expected UnsupportedOperationException without signaturesHex/history for signingInfo");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PackageInfo->signingInfo"));
            }

            // unrelated SigningInfo stays UOE (provenance isolation)
            DvmObject<?> unrelatedSigningInfo = vm.resolveClass("android/content/pm/SigningInfo")
                    .newObject(null);
            try {
                invokeGetApkContentsSigners(jni, baseVM, useVaList, unrelatedSigningInfo);
                fail("expected UnsupportedOperationException for unrelated SigningInfo.getApkContentsSigners");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getApkContentsSigners"));
            }
            try {
                invokeHasMultipleSigners(jni, baseVM, useVaList, unrelatedSigningInfo);
                fail("expected UnsupportedOperationException for unrelated SigningInfo.hasMultipleSigners");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasMultipleSigners"));
            }
            try {
                invokeGetSigningCertificateHistory(jni, baseVM, useVaList, unrelatedSigningInfo);
                fail("expected UnsupportedOperationException for unrelated SigningInfo.getSigningCertificateHistory");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSigningCertificateHistory"));
            }

            // ---- PackageManager.hasSigningCertificate(String, byte[], int) ----
            final int CERT_INPUT_RAW_X509 = 0;
            final int CERT_INPUT_SHA256 = 1;
            byte[] currentRaw = new byte[]{0x01, 0x02, 0x03, 0x04};
            byte[] historyRaw = new byte[]{(byte) 0xFF, (byte) 0xEE, (byte) 0xDD, (byte) 0xCC};
            // current raw hit
            assertTrue(invokeHasSigningCertificate(jni, baseVM, useVaList, pm,
                    "com.demo.app", currentRaw, CERT_INPUT_RAW_X509));
            // history raw hit
            assertTrue(invokeHasSigningCertificate(jni, baseVM, useVaList, pm,
                    "com.demo.app", historyRaw, CERT_INPUT_RAW_X509));
            // current SHA-256 hit
            assertTrue(invokeHasSigningCertificate(jni, baseVM, useVaList, pm,
                    "com.demo.app", sha256(currentRaw), CERT_INPUT_SHA256));
            // history SHA-256 hit
            assertTrue(invokeHasSigningCertificate(jni, baseVM, useVaList, pm,
                    "com.demo.app", sha256(historyRaw), CERT_INPUT_SHA256));
            // mismatch
            assertFalse(invokeHasSigningCertificate(jni, baseVM, useVaList, pm,
                    "com.demo.app", new byte[]{0x00}, CERT_INPUT_RAW_X509));
            assertFalse(invokeHasSigningCertificate(jni, baseVM, useVaList, pm,
                    "com.demo.app", sha256(new byte[]{0x00}), CERT_INPUT_SHA256));
            // unknown type
            try {
                invokeHasSigningCertificate(jni, baseVM, useVaList, pm,
                        "com.demo.app", currentRaw, 2);
                fail("expected IllegalArgumentException for unknown inputType");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("inputType"));
            }
            // missing signatures/history config on package
            try {
                invokeHasSigningCertificate(jni, baseVM, useVaList, pm,
                        "com.only.name", currentRaw, CERT_INPUT_RAW_X509);
                fail("expected UnsupportedOperationException without signature config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasSigningCertificate")
                        && expected.getMessage().contains("com.only.name"));
            }
            // missing package
            try {
                invokeHasSigningCertificate(jni, baseVM, useVaList, pm,
                        "com.absent.app", currentRaw, CERT_INPUT_RAW_X509);
                fail("expected UnsupportedOperationException for absent package");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasSigningCertificate")
                        && expected.getMessage().contains("com.absent.app"));
            }
            // history-only package: history raw/sha256 hit; current-only certs miss
            assertTrue(invokeHasSigningCertificate(jni, baseVM, useVaList, pm,
                    "com.other.uid", new byte[]{0x11, 0x22}, CERT_INPUT_RAW_X509));
            assertTrue(invokeHasSigningCertificate(jni, baseVM, useVaList, pm,
                    "com.other.uid", sha256(new byte[]{0x11, 0x22}), CERT_INPUT_SHA256));
            assertFalse(invokeHasSigningCertificate(jni, baseVM, useVaList, pm,
                    "com.other.uid", currentRaw, CERT_INPUT_RAW_X509));

            // ---- PackageManager.hasSigningCertificate(int, byte[], int) UID overload ----
            // uid 10001: shared has longer history (3) than demo (2) → select com.demo.shared
            assertTrue(invokeHasSigningCertificateUid(jni, baseVM, useVaList, pm,
                    10001, new byte[]{0x0f, 0x0e}, CERT_INPUT_RAW_X509));
            assertTrue(invokeHasSigningCertificateUid(jni, baseVM, useVaList, pm,
                    10001, new byte[]{(byte) 0xCC}, CERT_INPUT_RAW_X509));
            // demo-only current cert is not used when shared is selected
            assertFalse(invokeHasSigningCertificateUid(jni, baseVM, useVaList, pm,
                    10001, currentRaw, CERT_INPUT_RAW_X509));
            // hit/miss SHA-256 on selected package
            assertTrue(invokeHasSigningCertificateUid(jni, baseVM, useVaList, pm,
                    10001, sha256(new byte[]{0x0f, 0x0e}), CERT_INPUT_SHA256));
            assertFalse(invokeHasSigningCertificateUid(jni, baseVM, useVaList, pm,
                    10001, sha256(new byte[]{0x00}), CERT_INPUT_SHA256));
            // tie rank (both history-missing, signatures size 1): keep config order → com.tie.a
            assertTrue(invokeHasSigningCertificateUid(jni, baseVM, useVaList, pm,
                    10005, new byte[]{(byte) 0x99}, CERT_INPUT_RAW_X509));
            assertFalse(invokeHasSigningCertificateUid(jni, baseVM, useVaList, pm,
                    10005, new byte[]{(byte) 0x88}, CERT_INPUT_RAW_X509));
            // unknown uid → false
            assertFalse(invokeHasSigningCertificateUid(jni, baseVM, useVaList, pm,
                    99999, currentRaw, CERT_INPUT_RAW_X509));
            // matching uid but no signature config → UOE
            try {
                invokeHasSigningCertificateUid(jni, baseVM, useVaList, pm,
                        10006, currentRaw, CERT_INPUT_RAW_X509);
                fail("expected UnsupportedOperationException for uid with no signing material");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasSigningCertificate")
                        && expected.getMessage().contains("10006"));
            }
            // String overload still selects exact package (not affected by UID newest-signed)
            assertTrue(invokeHasSigningCertificate(jni, baseVM, useVaList, pm,
                    "com.demo.app", currentRaw, CERT_INPUT_RAW_X509));

            // explicit null versionName → Java null (configured presence)
            DvmObject<?> nullVn = invokeGetPackageInfo(jni, baseVM, useVaList, pm,
                    "com.null.version", 0);
            assertEquals("com.null.version", getStringField(jni, baseVM, nullVn, "packageName"));
            assertNull(getObjectField(jni, baseVM, nullVn, "versionName"));
            // versionCode omitted on that entry → UOE (must not fall through to APK 999)
            try {
                getIntField(jni, baseVM, nullVn, "versionCode");
                fail("expected UnsupportedOperationException for missing versionCode");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PackageInfo->versionCode"));
            }
            // install times omitted → UOE
            try {
                getLongField(jni, baseVM, nullVn, "firstInstallTime");
                fail("expected UnsupportedOperationException for missing firstInstallTime");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PackageInfo->firstInstallTime"));
            }
            try {
                getLongField(jni, baseVM, nullVn, "lastUpdateTime");
                fail("expected UnsupportedOperationException for missing lastUpdateTime");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PackageInfo->lastUpdateTime"));
            }
            // installerPackageName omitted → UOE
            try {
                invokeGetInstallerPackageName(jni, baseVM, useVaList, pm, "com.null.version");
                fail("expected UnsupportedOperationException for missing installerPackageName");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInstallerPackageName")
                        && expected.getMessage().contains("com.null.version"));
            }

            // explicit null installerPackageName → Java null
            assertNull(invokeGetInstallerPackageName(jni, baseVM, useVaList, pm, "com.null.paths"));

            // ---- InstallSourceInfo (modern install source) ----
            DvmObject<?> srcInfo = invokeGetInstallSourceInfo(jni, baseVM, useVaList, pm, "com.demo.app");
            assertNotNull(srcInfo);
            assertEquals("android/content/pm/InstallSourceInfo", srcInfo.getObjectType().getClassName());
            assertEquals("com.traceai.installer",
                    invokeInstallSourceString(jni, baseVM, useVaList, srcInfo, "getInstallingPackageName"));
            assertEquals("com.traceai.initiator",
                    invokeInstallSourceString(jni, baseVM, useVaList, srcInfo, "getInitiatingPackageName"));
            assertEquals("com.traceai.originator",
                    invokeInstallSourceString(jni, baseVM, useVaList, srcInfo, "getOriginatingPackageName"));

            // marker installer/initiator/originator values
            DvmObject<?> markerSrc = invokeGetInstallSourceInfo(jni, baseVM, useVaList, pm, "com.traceai.marker");
            assertEquals("com.traceai.installer",
                    invokeInstallSourceString(jni, baseVM, useVaList, markerSrc, "getInstallingPackageName"));
            assertEquals("com.traceai.initiator",
                    invokeInstallSourceString(jni, baseVM, useVaList, markerSrc, "getInitiatingPackageName"));
            assertEquals("com.traceai.originator",
                    invokeInstallSourceString(jni, baseVM, useVaList, markerSrc, "getOriginatingPackageName"));

            // explicit null installer/initiating/originating → Java null
            DvmObject<?> nullSrc = invokeGetInstallSourceInfo(jni, baseVM, useVaList, pm, "com.null.paths");
            assertNull(invokeInstallSourceString(jni, baseVM, useVaList, nullSrc, "getInstallingPackageName"));
            assertNull(invokeInstallSourceString(jni, baseVM, useVaList, nullSrc, "getInitiatingPackageName"));
            assertNull(invokeInstallSourceString(jni, baseVM, useVaList, nullSrc, "getOriginatingPackageName"));

            // missing installer/initiating/originating fields on package entry → UOE
            DvmObject<?> onlyNameSrc = invokeGetInstallSourceInfo(jni, baseVM, useVaList, pm, "com.only.name");
            try {
                invokeInstallSourceString(jni, baseVM, useVaList, onlyNameSrc, "getInstallingPackageName");
                fail("expected UnsupportedOperationException for missing installing package name");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInstallingPackageName"));
            }
            try {
                invokeInstallSourceString(jni, baseVM, useVaList, onlyNameSrc, "getInitiatingPackageName");
                fail("expected UnsupportedOperationException for missing initiating package name");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInitiatingPackageName"));
            }
            try {
                invokeInstallSourceString(jni, baseVM, useVaList, onlyNameSrc, "getOriginatingPackageName");
                fail("expected UnsupportedOperationException for missing originating package name");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getOriginatingPackageName")
                        && expected.getMessage().contains("com.only.name"));
            }

            // absent package for getInstallSourceInfo
            try {
                invokeGetInstallSourceInfo(jni, baseVM, useVaList, pm, "com.absent.app");
                fail("expected UnsupportedOperationException for absent getInstallSourceInfo package");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInstallSourceInfo")
                        && expected.getMessage().contains("com.absent.app"));
            }

            // unrelated InstallSourceInfo provenance → UOE, no json-config handling
            DvmObject<?> unrelatedSrc = vm.resolveClass("android/content/pm/InstallSourceInfo")
                    .newObject("evil.source");
            try {
                invokeInstallSourceString(jni, baseVM, useVaList, unrelatedSrc, "getInstallingPackageName");
                fail("expected UnsupportedOperationException for unrelated InstallSourceInfo");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInstallingPackageName"));
            }
            try {
                invokeInstallSourceString(jni, baseVM, useVaList, unrelatedSrc, "getOriginatingPackageName");
                fail("expected UnsupportedOperationException for unrelated InstallSourceInfo originating");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getOriginatingPackageName"));
            }
            // configured marker still works after unrelated regression
            assertEquals("com.traceai.installer",
                    invokeInstallSourceString(jni, baseVM, useVaList, srcInfo, "getInstallingPackageName"));
            assertEquals("com.traceai.originator",
                    invokeInstallSourceString(jni, baseVM, useVaList, srcInfo, "getOriginatingPackageName"));

            // package with both versionName and versionCode omitted → UOE for both (no APK fallback)
            DvmObject<?> onlyName = invokeGetPackageInfo(jni, baseVM, useVaList, pm,
                    "com.only.name", 0);
            assertEquals("com.only.name", getStringField(jni, baseVM, onlyName, "packageName"));
            try {
                getObjectField(jni, baseVM, onlyName, "versionName");
                fail("expected UnsupportedOperationException for omitted versionName");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PackageInfo->versionName"));
            }
            try {
                getIntField(jni, baseVM, onlyName, "versionCode");
                fail("expected UnsupportedOperationException for omitted versionCode on only-name pkg");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PackageInfo->versionCode"));
            }
            try {
                getLongField(jni, baseVM, onlyName, "firstInstallTime");
                fail("expected UnsupportedOperationException for omitted firstInstallTime");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PackageInfo->firstInstallTime"));
            }
            try {
                invokeGetInstallerPackageName(jni, baseVM, useVaList, pm, "com.only.name");
                fail("expected UnsupportedOperationException for omitted installer on only-name");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInstallerPackageName"));
            }

            // absent package blocks old arbitrary-package fallback
            try {
                invokeGetPackageInfo(jni, baseVM, useVaList, pm, "com.absent.app", 0);
                fail("expected UnsupportedOperationException for absent configured package");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPackageInfo")
                        && expected.getMessage().contains("com.absent.app"));
            }
            try {
                invokeGetInstallerPackageName(jni, baseVM, useVaList, pm, "com.absent.app");
                fail("expected UnsupportedOperationException for absent installer package");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInstallerPackageName")
                        && expected.getMessage().contains("com.absent.app"));
            }

            // empty packages array blocks fallback
            TraceEnvironmentConfig emptyCfg = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\",\"packages\":[]}}");
            AndroidEmulator emptyEmu = null;
            try {
                emptyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(emptyCfg)
                        .build();
                VM emptyVm = emptyEmu.createDalvikVM();
                AbstractJni emptyJni = new AbstractJni() {
                };
                emptyVm.setJni(emptyJni);
                BaseVM emptyBase = (BaseVM) emptyVm;
                DvmObject<?> emptyPm = emptyVm.resolveClass("android/content/pm/PackageManager").newObject(null);
                try {
                    invokeGetPackageInfo(emptyJni, emptyBase, useVaList, emptyPm, "com.demo.app", 0);
                    fail("expected UnsupportedOperationException for empty packages array");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getPackageInfo")
                            && expected.getMessage().contains("com.demo.app"));
                }
            } finally {
                if (emptyEmu != null) {
                    emptyEmu.close();
                }
            }

            // node absent: old behavior (returns api.PackageInfo for any name)
            TraceEnvironmentConfig noPkgCfg = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\",\"versionName\":\"apk-fallback\",\"versionCode\":999}}");
            AndroidEmulator noPkgEmu = null;
            try {
                noPkgEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noPkgCfg)
                        .build();
                VM noPkgVm = noPkgEmu.createDalvikVM();
                AbstractJni noPkgJni = new AbstractJni() {
                };
                noPkgVm.setJni(noPkgJni);
                BaseVM noPkgBase = (BaseVM) noPkgVm;
                DvmObject<?> noPkgPm = noPkgVm.resolveClass("android/content/pm/PackageManager").newObject(null);
                DvmObject<?> oldInfo = invokeGetPackageInfo(noPkgJni, noPkgBase, useVaList, noPkgPm,
                        "com.any.package", 0);
                assertTrue(oldInfo instanceof PackageInfo);
                assertEquals("com.any.package", ((PackageInfo) oldInfo).getPackageName());
                // old versionName/versionCode for matching app package still work
                DvmObject<?> selfInfo = invokeGetPackageInfo(noPkgJni, noPkgBase, useVaList, noPkgPm,
                        "com.demo.app", 0);
                assertEquals("apk-fallback", getStringField(noPkgJni, noPkgBase, selfInfo, "versionName"));
                assertEquals(999, getIntField(noPkgJni, noPkgBase, selfInfo, "versionCode"));
            } finally {
                if (noPkgEmu != null) {
                    noPkgEmu.close();
                }
            }

            // unrelated PackageInfo provenance uses old behavior (not marker fields)
            PackageInfo unrelated = new PackageInfo(vm, "com.demo.app", 0);
            assertEquals("apk-fallback", getStringField(jni, baseVM, unrelated, "versionName"));
            assertEquals(999, getIntField(jni, baseVM, unrelated, "versionCode"));
            // packageName field is not implemented on old PackageInfo path
            try {
                getStringField(jni, baseVM, unrelated, "packageName");
                fail("expected UnsupportedOperationException for unrelated PackageInfo.packageName");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PackageInfo->packageName"));
            }
            // install times not on old PackageInfo path
            try {
                getLongField(jni, baseVM, unrelated, "firstInstallTime");
                fail("expected UnsupportedOperationException for unrelated firstInstallTime");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PackageInfo->firstInstallTime"));
            }
            // configured marker still works after unrelated regression
            assertEquals("1.2.3", getStringField(jni, baseVM, info, "versionName"));
            assertEquals(42, getIntField(jni, baseVM, info, "versionCode"));
            assertEquals(1718000000000L, getLongField(jni, baseVM, info, "firstInstallTime"));
            assertEquals("com.traceai.installer",
                    invokeGetInstallerPackageName(jni, baseVM, useVaList, pm, "com.demo.app"));

            // node absent: getInstallerPackageName / getInstallSourceInfo stay UOE (no historical handler)
            TraceEnvironmentConfig noInstallerCfg = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\"}}");
            AndroidEmulator noInstallerEmu = null;
            try {
                noInstallerEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noInstallerCfg)
                        .build();
                VM noInstallerVm = noInstallerEmu.createDalvikVM();
                AbstractJni noInstallerJni = new AbstractJni() {
                };
                noInstallerVm.setJni(noInstallerJni);
                BaseVM noInstallerBase = (BaseVM) noInstallerVm;
                DvmObject<?> noInstallerPm = noInstallerVm.resolveClass("android/content/pm/PackageManager")
                        .newObject(null);
                try {
                    invokeGetInstallerPackageName(noInstallerJni, noInstallerBase, useVaList, noInstallerPm,
                            "com.demo.app");
                    fail("expected UnsupportedOperationException without packages for getInstallerPackageName");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getInstallerPackageName"));
                }
                try {
                    invokeGetInstallSourceInfo(noInstallerJni, noInstallerBase, useVaList, noInstallerPm,
                            "com.demo.app");
                    fail("expected UnsupportedOperationException without packages for getInstallSourceInfo");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getInstallSourceInfo"));
                }
            } finally {
                if (noInstallerEmu != null) {
                    noInstallerEmu.close();
                }
            }

            // ---- ApplicationInfo via getApplicationInfo ----
            DvmObject<?> appInfo = invokeGetApplicationInfo(jni, baseVM, useVaList, pm, "com.demo.app", 0);
            assertNotNull(appInfo);
            assertEquals("android/content/pm/ApplicationInfo", appInfo.getObjectType().getClassName());
            assertEquals("com.demo.app",
                    getAppStringField(jni, baseVM, appInfo, "packageName"));
            assertEquals("/data/app/~~fixed/com.demo.app-fixed/base.apk",
                    getAppStringField(jni, baseVM, appInfo, "sourceDir"));
            assertEquals("/data/app/~~fixed/com.demo.app-fixed/base.apk",
                    getAppStringField(jni, baseVM, appInfo, "publicSourceDir"));
            assertEquals("/data/user/0/com.demo.app",
                    getAppStringField(jni, baseVM, appInfo, "dataDir"));
            // scalars: uid 10001, enabled false, systemApp true → flags=1
            assertEquals(10001, getAppIntField(jni, baseVM, appInfo, "uid"));
            assertEquals(false, getAppBooleanField(jni, baseVM, appInfo, "enabled"));
            assertEquals(1, getAppIntField(jni, baseVM, appInfo, "flags"));

            // marker paths + systemApp false → flags=0; uid/enabled omitted → UOE
            DvmObject<?> markerApp = invokeGetApplicationInfo(jni, baseVM, useVaList, pm,
                    "com.traceai.marker", 0x80);
            assertEquals("/data/app/TRACEAI_SOURCE_MARKER_V1/base.apk",
                    getAppStringField(jni, baseVM, markerApp, "sourceDir"));
            assertEquals("/data/app/TRACEAI_SOURCE_MARKER_V1/base.apk",
                    getAppStringField(jni, baseVM, markerApp, "publicSourceDir"));
            assertEquals("/data/user/0/com.traceai.marker",
                    getAppStringField(jni, baseVM, markerApp, "dataDir"));
            assertEquals(0, getAppIntField(jni, baseVM, markerApp, "flags"));
            try {
                getAppIntField(jni, baseVM, markerApp, "uid");
                fail("expected UnsupportedOperationException for omitted ApplicationInfo.uid");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ApplicationInfo->uid"));
            }
            try {
                getAppBooleanField(jni, baseVM, markerApp, "enabled");
                fail("expected UnsupportedOperationException for omitted ApplicationInfo.enabled");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ApplicationInfo->enabled"));
            }

            // explicit null sourceDir/dataDir → Java null
            DvmObject<?> nullPaths = invokeGetApplicationInfo(jni, baseVM, useVaList, pm,
                    "com.null.paths", 0);
            assertEquals("com.null.paths", getAppStringField(jni, baseVM, nullPaths, "packageName"));
            assertNull(getAppObjectField(jni, baseVM, nullPaths, "sourceDir"));
            assertNull(getAppObjectField(jni, baseVM, nullPaths, "publicSourceDir"));
            assertNull(getAppObjectField(jni, baseVM, nullPaths, "dataDir"));

            // missing sourceDir/dataDir/scalars → UOE without current-app fallback
            DvmObject<?> onlyNameApp = invokeGetApplicationInfo(jni, baseVM, useVaList, pm,
                    "com.only.name", 0);
            assertEquals("com.only.name", getAppStringField(jni, baseVM, onlyNameApp, "packageName"));
            try {
                getAppObjectField(jni, baseVM, onlyNameApp, "sourceDir");
                fail("expected UnsupportedOperationException for omitted ApplicationInfo.sourceDir");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ApplicationInfo->sourceDir"));
            }
            try {
                getAppObjectField(jni, baseVM, onlyNameApp, "publicSourceDir");
                fail("expected UnsupportedOperationException for omitted ApplicationInfo.publicSourceDir");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ApplicationInfo->publicSourceDir"));
            }
            try {
                getAppObjectField(jni, baseVM, onlyNameApp, "dataDir");
                fail("expected UnsupportedOperationException for omitted ApplicationInfo.dataDir");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ApplicationInfo->dataDir"));
            }
            try {
                getAppIntField(jni, baseVM, onlyNameApp, "uid");
                fail("expected UnsupportedOperationException for omitted ApplicationInfo.uid");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ApplicationInfo->uid"));
            }
            try {
                getAppIntField(jni, baseVM, onlyNameApp, "flags");
                fail("expected UnsupportedOperationException for omitted ApplicationInfo.flags");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ApplicationInfo->flags"));
            }
            try {
                getAppBooleanField(jni, baseVM, onlyNameApp, "enabled");
                fail("expected UnsupportedOperationException for omitted ApplicationInfo.enabled");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ApplicationInfo->enabled"));
            }

            // absent package blocks getApplicationInfo fallback
            try {
                invokeGetApplicationInfo(jni, baseVM, useVaList, pm, "com.absent.app", 0);
                fail("expected UnsupportedOperationException for absent ApplicationInfo package");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getApplicationInfo")
                        && expected.getMessage().contains("com.absent.app"));
            }

            // empty packages array blocks getApplicationInfo
            TraceEnvironmentConfig emptyAppCfg = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\",\"packages\":[]}}");
            AndroidEmulator emptyAppEmu = null;
            try {
                emptyAppEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(emptyAppCfg)
                        .build();
                VM emptyAppVm = emptyAppEmu.createDalvikVM();
                AbstractJni emptyAppJni = new AbstractJni() {
                };
                emptyAppVm.setJni(emptyAppJni);
                BaseVM emptyAppBase = (BaseVM) emptyAppVm;
                DvmObject<?> emptyAppPm = emptyAppVm.resolveClass("android/content/pm/PackageManager")
                        .newObject(null);
                try {
                    invokeGetApplicationInfo(emptyAppJni, emptyAppBase, useVaList, emptyAppPm,
                            "com.demo.app", 0);
                    fail("expected UnsupportedOperationException for empty packages getApplicationInfo");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getApplicationInfo")
                            && expected.getMessage().contains("com.demo.app"));
                }
            } finally {
                if (emptyAppEmu != null) {
                    emptyAppEmu.close();
                }
            }

            // node absent: preserve historical behavior (VaList has current-package-only handler;
            // VarArg has no historical PackageManager.getApplicationInfo and stays UOE).
            TraceEnvironmentConfig noAppPkgCfg = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\","
                            + "\"apkPath\":\"/data/app/~~fallback/com.demo.app/base.apk\","
                            + "\"dataDir\":\"/data/user/0/com.demo.app\"}}");
            AndroidEmulator noAppPkgEmu = null;
            try {
                noAppPkgEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noAppPkgCfg)
                        .build();
                VM noAppPkgVm = noAppPkgEmu.createDalvikVM();
                AbstractJni noAppPkgJni = new AbstractJni() {
                };
                noAppPkgVm.setJni(noAppPkgJni);
                BaseVM noAppPkgBase = (BaseVM) noAppPkgVm;
                DvmObject<?> noAppPm = noAppPkgVm.resolveClass("android/content/pm/PackageManager")
                        .newObject(null);
                if (useVaList) {
                    DvmObject<?> oldAppInfo = invokeGetApplicationInfo(noAppPkgJni, noAppPkgBase, true,
                            noAppPm, "com.demo.app", 0);
                    assertTrue(oldAppInfo instanceof ApplicationInfo);
                    assertEquals("/data/app/~~fallback/com.demo.app/base.apk",
                            getAppStringField(noAppPkgJni, noAppPkgBase, oldAppInfo, "sourceDir"));
                    try {
                        invokeGetApplicationInfo(noAppPkgJni, noAppPkgBase, true, noAppPm,
                                "com.other.app", 0);
                        fail("expected UnsupportedOperationException for non-current package without packages node");
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getApplicationInfo"));
                    }
                } else {
                    try {
                        invokeGetApplicationInfo(noAppPkgJni, noAppPkgBase, false, noAppPm,
                                "com.demo.app", 0);
                        fail("expected UnsupportedOperationException without packages on VarArg path");
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getApplicationInfo"));
                    }
                }
            } finally {
                if (noAppPkgEmu != null) {
                    noAppPkgEmu.close();
                }
            }

            // unrelated ApplicationInfo provenance uses old current-app field behavior
            ApplicationInfo unrelatedApp = new ApplicationInfo(vm);
            assertEquals("com.demo.app",
                    getAppStringField(jni, baseVM, unrelatedApp, "packageName"));
            assertEquals("/data/app/~~fallback/com.demo.app/base.apk",
                    getAppStringField(jni, baseVM, unrelatedApp, "sourceDir"));
            assertEquals("/data/user/0/com.demo.app",
                    getAppStringField(jni, baseVM, unrelatedApp, "dataDir"));
            // unrelated has no historical uid/enabled/flags field wiring → UOE
            try {
                getAppIntField(jni, baseVM, unrelatedApp, "uid");
                fail("expected UnsupportedOperationException for unrelated ApplicationInfo.uid");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ApplicationInfo->uid"));
            }
            try {
                getAppBooleanField(jni, baseVM, unrelatedApp, "enabled");
                fail("expected UnsupportedOperationException for unrelated ApplicationInfo.enabled");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ApplicationInfo->enabled"));
            }
            // configured ApplicationInfo marker still works after unrelated regression
            assertEquals("/data/app/~~fixed/com.demo.app-fixed/base.apk",
                    getAppStringField(jni, baseVM, appInfo, "sourceDir"));
            assertEquals(10001, getAppIntField(jni, baseVM, appInfo, "uid"));
            assertEquals(false, getAppBooleanField(jni, baseVM, appInfo, "enabled"));
            assertEquals(1, getAppIntField(jni, baseVM, appInfo, "flags"));

            // ---- installed lists: order, size/get, field readers, flags propagation ----
            final int listFlags = 0x40;
            DvmObject<?> pkgListObj = invokeGetInstalledList(jni, baseVM, useVaList, pm,
                    "getInstalledPackages", listFlags);
            assertTrue(pkgListObj instanceof ArrayListObject);
            ArrayListObject pkgList = (ArrayListObject) pkgListObj;
            assertEquals(10, pkgList.size());
            DvmObject<?> listPkg0 = invokeListGet(jni, baseVM, useVaList, pkgList, 0);
            DvmObject<?> listPkg1 = invokeListGet(jni, baseVM, useVaList, pkgList, 1);
            assertEquals("com.demo.app", getStringField(jni, baseVM, listPkg0, "packageName"));
            assertEquals("1.2.3", getStringField(jni, baseVM, listPkg0, "versionName"));
            assertEquals(42, getIntField(jni, baseVM, listPkg0, "versionCode"));
            assertEquals("com.traceai.marker", getStringField(jni, baseVM, listPkg1, "packageName"));
            assertEquals("TRACEAI_PKG_VERSION_MARKER_V1",
                    getStringField(jni, baseVM, listPkg1, "versionName"));
            // flags stored on marker appear in field sidecar path via subsequent field reads
            assertEquals(0, getIntField(jni, baseVM, listPkg1, "versionCode"));

            DvmObject<?> appListObj = invokeGetInstalledList(jni, baseVM, useVaList, pm,
                    "getInstalledApplications", listFlags);
            assertTrue(appListObj instanceof ArrayListObject);
            ArrayListObject appList = (ArrayListObject) appListObj;
            assertEquals(10, appList.size());
            DvmObject<?> listApp0 = invokeListGet(jni, baseVM, useVaList, appList, 0);
            DvmObject<?> listApp1 = invokeListGet(jni, baseVM, useVaList, appList, 1);
            assertEquals("com.demo.app", getAppStringField(jni, baseVM, listApp0, "packageName"));
            assertEquals("/data/app/~~fixed/com.demo.app-fixed/base.apk",
                    getAppStringField(jni, baseVM, listApp0, "sourceDir"));
            assertEquals(10001, getAppIntField(jni, baseVM, listApp0, "uid"));
            assertEquals(false, getAppBooleanField(jni, baseVM, listApp0, "enabled"));
            assertEquals(1, getAppIntField(jni, baseVM, listApp0, "flags"));
            assertEquals("com.traceai.marker", getAppStringField(jni, baseVM, listApp1, "packageName"));
            assertEquals(0, getAppIntField(jni, baseVM, listApp1, "flags"));

            // explicit empty packages array → empty handled lists
            TraceEnvironmentConfig emptyListCfg = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\",\"packages\":[]}}");
            AndroidEmulator emptyListEmu = null;
            try {
                emptyListEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(emptyListCfg)
                        .build();
                VM emptyListVm = emptyListEmu.createDalvikVM();
                AbstractJni emptyListJni = new AbstractJni() {
                };
                emptyListVm.setJni(emptyListJni);
                BaseVM emptyListBase = (BaseVM) emptyListVm;
                DvmObject<?> emptyListPm = emptyListVm.resolveClass("android/content/pm/PackageManager")
                        .newObject(null);
                DvmObject<?> emptyPkgs = invokeGetInstalledList(emptyListJni, emptyListBase, useVaList,
                        emptyListPm, "getInstalledPackages", 0);
                assertTrue(emptyPkgs instanceof ArrayListObject);
                assertEquals(0, ((ArrayListObject) emptyPkgs).size());
                DvmObject<?> emptyApps = invokeGetInstalledList(emptyListJni, emptyListBase, useVaList,
                        emptyListPm, "getInstalledApplications", 0);
                assertTrue(emptyApps instanceof ArrayListObject);
                assertEquals(0, ((ArrayListObject) emptyApps).size());
            } finally {
                if (emptyListEmu != null) {
                    emptyListEmu.close();
                }
            }

            // node absent: installed-list paths stay UOE (no historical handlers)
            TraceEnvironmentConfig noListCfg = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\"}}");
            AndroidEmulator noListEmu = null;
            try {
                noListEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noListCfg)
                        .build();
                VM noListVm = noListEmu.createDalvikVM();
                AbstractJni noListJni = new AbstractJni() {
                };
                noListVm.setJni(noListJni);
                BaseVM noListBase = (BaseVM) noListVm;
                DvmObject<?> noListPm = noListVm.resolveClass("android/content/pm/PackageManager")
                        .newObject(null);
                try {
                    invokeGetInstalledList(noListJni, noListBase, useVaList, noListPm,
                            "getInstalledPackages", 0);
                    fail("expected UnsupportedOperationException without packages for getInstalledPackages");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getInstalledPackages"));
                }
                try {
                    invokeGetInstalledList(noListJni, noListBase, useVaList, noListPm,
                            "getInstalledApplications", 0);
                    fail("expected UnsupportedOperationException without packages for getInstalledApplications");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getInstalledApplications"));
                }
            } finally {
                if (noListEmu != null) {
                    noListEmu.close();
                }
            }

            // ---- permissions checkPermission / checkSelfPermission ----
            // granted
            assertEquals(0, invokeCheckPermission(jni, baseVM, useVaList, pm,
                    "android.permission.INTERNET", "com.demo.app"));
            // denied configured false
            assertEquals(-1, invokeCheckPermission(jni, baseVM, useVaList, pm,
                    "android.permission.CAMERA", "com.demo.app"));
            // custom granted
            assertEquals(0, invokeCheckPermission(jni, baseVM, useVaList, pm,
                    "com.traceai.permission.CUSTOM", "com.demo.app"));
            // absent from authoritative map → denied
            assertEquals(-1, invokeCheckPermission(jni, baseVM, useVaList, pm,
                    "android.permission.READ_SMS", "com.demo.app"));
            // explicit empty permissions map → denied for any permission
            assertEquals(-1, invokeCheckPermission(jni, baseVM, useVaList, pm,
                    "android.permission.INTERNET", "com.traceai.marker"));
            // missing permissions node → UOE
            try {
                invokeCheckPermission(jni, baseVM, useVaList, pm,
                        "android.permission.INTERNET", "com.only.name");
                fail("expected UnsupportedOperationException for missing permissions node");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("checkPermission"));
            }
            // absent package → UOE
            try {
                invokeCheckPermission(jni, baseVM, useVaList, pm,
                        "android.permission.INTERNET", "com.absent.app");
                fail("expected UnsupportedOperationException for absent package checkPermission");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("checkPermission")
                        && expected.getMessage().contains("com.absent.app"));
            }
            // checkSelfPermission uses current packageName (com.demo.app)
            DvmObject<?> appCtx = vm.resolveClass("android/app/Application").newObject(null);
            assertEquals(0, invokeCheckSelfPermission(jni, baseVM, useVaList, appCtx,
                    "android.permission.INTERNET"));
            assertEquals(-1, invokeCheckSelfPermission(jni, baseVM, useVaList, appCtx,
                    "android.permission.CAMERA"));
            assertEquals(-1, invokeCheckSelfPermission(jni, baseVM, useVaList, appCtx,
                    "android.permission.READ_SMS"));
            DvmObject<?> ctx = vm.resolveClass("android/content/Context").newObject(null);
            assertEquals(0, invokeCheckSelfPermission(jni, baseVM, useVaList, ctx,
                    "android.permission.INTERNET"));

            // ---- UID mappings: getPackagesForUid / getNameForUid ----
            // two packages sharing UID 10001 preserve config order
            DvmObject<?> uid10001Pkgs = invokeGetPackagesForUid(jni, baseVM, useVaList, pm, 10001);
            assertTrue(uid10001Pkgs instanceof ArrayObject);
            ArrayObject uid10001Arr = (ArrayObject) uid10001Pkgs;
            assertEquals(2, uid10001Arr.length());
            assertTrue(uid10001Arr.getValue()[0] instanceof StringObject);
            assertTrue(uid10001Arr.getValue()[1] instanceof StringObject);
            assertEquals("com.demo.app", ((StringObject) uid10001Arr.getValue()[0]).getValue());
            assertEquals("com.demo.shared", ((StringObject) uid10001Arr.getValue()[1]).getValue());
            DvmObject<?> uid10001Name = invokeGetNameForUid(jni, baseVM, useVaList, pm, 10001);
            assertTrue(uid10001Name instanceof StringObject);
            assertEquals("com.demo.app", ((StringObject) uid10001Name).getValue());
            // another UID
            DvmObject<?> uid10002Pkgs = invokeGetPackagesForUid(jni, baseVM, useVaList, pm, 10002);
            assertTrue(uid10002Pkgs instanceof ArrayObject);
            ArrayObject uid10002Arr = (ArrayObject) uid10002Pkgs;
            assertEquals(1, uid10002Arr.length());
            assertEquals("com.other.uid", ((StringObject) uid10002Arr.getValue()[0]).getValue());
            DvmObject<?> uid10002Name = invokeGetNameForUid(jni, baseVM, useVaList, pm, 10002);
            assertTrue(uid10002Name instanceof StringObject);
            assertEquals("com.other.uid", ((StringObject) uid10002Name).getValue());
            // no match → handled Java null (must not fall through to current-package fallback)
            assertNull(invokeGetPackagesForUid(jni, baseVM, useVaList, pm, 99999));
            assertNull(invokeGetNameForUid(jni, baseVM, useVaList, pm, 99999));
            // packages missing uid field never match (com.only.name / com.null.* have no uid)
            // already covered by 99999; also ensure random uid does not return current package
            assertNull(invokeGetPackagesForUid(jni, baseVM, useVaList, pm, 0));
            assertNull(invokeGetNameForUid(jni, baseVM, useVaList, pm, 0));
            // explicit empty packages → handled null
            TraceEnvironmentConfig emptyUidCfg = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\",\"packages\":[]}}");
            AndroidEmulator emptyUidEmu = null;
            try {
                emptyUidEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(emptyUidCfg)
                        .build();
                VM emptyUidVm = emptyUidEmu.createDalvikVM();
                AbstractJni emptyUidJni = new AbstractJni() {
                };
                emptyUidVm.setJni(emptyUidJni);
                BaseVM emptyUidBase = (BaseVM) emptyUidVm;
                DvmObject<?> emptyUidPm = emptyUidVm.resolveClass("android/content/pm/PackageManager")
                        .newObject(null);
                assertNull(invokeGetPackagesForUid(emptyUidJni, emptyUidBase, useVaList, emptyUidPm, 10001));
                assertNull(invokeGetNameForUid(emptyUidJni, emptyUidBase, useVaList, emptyUidPm, 10001));
            } finally {
                if (emptyUidEmu != null) {
                    emptyUidEmu.close();
                }
            }
            // node absent: old getPackagesForUid any-uid current-package fallback (VarArg only)
            TraceEnvironmentConfig noUidNodeCfg = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\"}}");
            AndroidEmulator noUidNodeEmu = null;
            try {
                noUidNodeEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noUidNodeCfg)
                        .build();
                VM noUidNodeVm = noUidNodeEmu.createDalvikVM();
                AbstractJni noUidNodeJni = new AbstractJni() {
                };
                noUidNodeVm.setJni(noUidNodeJni);
                BaseVM noUidNodeBase = (BaseVM) noUidNodeVm;
                DvmObject<?> noUidNodePm = noUidNodeVm.resolveClass("android/content/pm/PackageManager")
                        .newObject(null);
                if (!useVaList) {
                    // historical VarArg path: any uid returns [current package]
                    DvmObject<?> oldPkgs = invokeGetPackagesForUid(noUidNodeJni, noUidNodeBase, false,
                            noUidNodePm, 12345);
                    assertTrue(oldPkgs instanceof ArrayObject);
                    ArrayObject oldArr = (ArrayObject) oldPkgs;
                    assertEquals(1, oldArr.length());
                    assertEquals("com.demo.app", ((StringObject) oldArr.getValue()[0]).getValue());
                } else {
                    // no historical VaList getPackagesForUid → UOE
                    try {
                        invokeGetPackagesForUid(noUidNodeJni, noUidNodeBase, true, noUidNodePm, 12345);
                        fail("expected UnsupportedOperationException without packages on VaList getPackagesForUid");
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getPackagesForUid"));
                    }
                }
                // getNameForUid has no historical handler either path
                try {
                    invokeGetNameForUid(noUidNodeJni, noUidNodeBase, useVaList, noUidNodePm, 12345);
                    fail("expected UnsupportedOperationException without packages for getNameForUid");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getNameForUid"));
                }
            } finally {
                if (noUidNodeEmu != null) {
                    noUidNodeEmu.close();
                }
            }

            // zero-arg unrelated regression
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmClass appClass = app.getObjectType();
            DvmMethod getPackageName = new DvmMethod(appClass, "getPackageName", "()Ljava/lang/String;", false);
            if (useVaList) {
                DvmObject<?> pkg = jni.callObjectMethodV(baseVM, app, getPackageName.getSignature(),
                        new TestVaList(baseVM, getPackageName));
                assertTrue(pkg instanceof StringObject);
                assertEquals("com.demo.app", ((StringObject) pkg).getValue());
            } else {
                DvmObject<?> pkg = jni.callObjectMethod(baseVM, app, getPackageName.getSignature(),
                        new TestVarArg(baseVM, getPackageName));
                assertTrue(pkg instanceof StringObject);
                assertEquals("com.demo.app", ((StringObject) pkg).getValue());
            }
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static DvmObject<?> invokeGetPackageInfo(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                     DvmObject<?> packageManager, String packageName, int flags) {
        return invokePackageManagerQuery(jni, vm, useVaList, packageManager, "getPackageInfo",
                "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;", packageName, flags);
    }

    private static DvmObject<?> invokeGetApplicationInfo(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                         DvmObject<?> packageManager, String packageName,
                                                         int flags) {
        return invokePackageManagerQuery(jni, vm, useVaList, packageManager, "getApplicationInfo",
                "(Ljava/lang/String;I)Landroid/content/pm/ApplicationInfo;", packageName, flags);
    }

    private static String invokeGetInstallerPackageName(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                        DvmObject<?> packageManager, String packageName) {
        int nameHash = vm.addLocalObject(new StringObject(vm, packageName));
        DvmClass dvmClass = packageManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getInstallerPackageName",
                "(Ljava/lang/String;)Ljava/lang/String;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, packageManager, signature,
                    new TestVaList(vm, method, nameHash));
        } else {
            result = jni.callObjectMethod(vm, packageManager, signature,
                    new TestVarArg(vm, method, nameHash));
        }
        if (result == null) {
            return null;
        }
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static DvmObject<?> invokeGetInstallSourceInfo(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                           DvmObject<?> packageManager, String packageName) {
        int nameHash = vm.addLocalObject(new StringObject(vm, packageName));
        DvmClass dvmClass = packageManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getInstallSourceInfo",
                "(Ljava/lang/String;)Landroid/content/pm/InstallSourceInfo;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, packageManager, signature,
                    new TestVaList(vm, method, nameHash));
        }
        return jni.callObjectMethod(vm, packageManager, signature,
                new TestVarArg(vm, method, nameHash));
    }

    private static String invokeInstallSourceString(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                    DvmObject<?> installSourceInfo, String methodName) {
        DvmClass dvmClass = installSourceInfo.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, "()Ljava/lang/String;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, installSourceInfo, signature,
                    new TestVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, installSourceInfo, signature,
                    new TestVarArg(vm, method));
        }
        if (result == null) {
            return null;
        }
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static DvmObject<?> invokePackageManagerQuery(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                          DvmObject<?> packageManager, String methodName,
                                                          String args, String packageName, int flags) {
        int nameHash = vm.addLocalObject(new StringObject(vm, packageName));
        DvmClass dvmClass = packageManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, packageManager, signature,
                    new TestVaList(vm, method, nameHash, flags));
        }
        return jni.callObjectMethod(vm, packageManager, signature,
                new TestVarArg(vm, method, nameHash, flags));
    }

    private static DvmObject<?> invokeGetInstalledList(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> packageManager, String methodName,
                                                       int flags) {
        DvmClass dvmClass = packageManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, "(I)Ljava/util/List;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, packageManager, signature,
                    new TestVaList(vm, method, flags));
        }
        return jni.callObjectMethod(vm, packageManager, signature,
                new TestVarArg(vm, method, flags));
    }

    private static DvmObject<?> invokeListGet(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              ArrayListObject list, int index) {
        if (useVaList) {
            DvmClass dvmClass = list.getObjectType();
            DvmMethod method = new DvmMethod(dvmClass, "get", "(I)Ljava/lang/Object;", false);
            return jni.callObjectMethodV(vm, list, method.getSignature(),
                    new TestVaList(vm, method, index));
        }
        return list.getValue().get(index);
    }

    private static DvmObject<?> invokeGetPackagesForUid(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                        DvmObject<?> packageManager, int uid) {
        DvmClass dvmClass = packageManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getPackagesForUid", "(I)[Ljava/lang/String;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, packageManager, signature, new TestVaList(vm, method, uid));
        }
        return jni.callObjectMethod(vm, packageManager, signature, new TestVarArg(vm, method, uid));
    }

    private static DvmObject<?> invokeGetNameForUid(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                    DvmObject<?> packageManager, int uid) {
        DvmClass dvmClass = packageManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getNameForUid", "(I)Ljava/lang/String;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, packageManager, signature, new TestVaList(vm, method, uid));
        }
        return jni.callObjectMethod(vm, packageManager, signature, new TestVarArg(vm, method, uid));
    }

    private static int invokeCheckPermission(AbstractJni jni, BaseVM vm, boolean useVaList,
                                             DvmObject<?> packageManager, String permission,
                                             String packageName) {
        int permissionHash = vm.addLocalObject(new StringObject(vm, permission));
        int packageHash = vm.addLocalObject(new StringObject(vm, packageName));
        DvmClass dvmClass = packageManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "checkPermission",
                "(Ljava/lang/String;Ljava/lang/String;)I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, packageManager, signature,
                    new TestVaList(vm, method, permissionHash, packageHash));
        }
        return jni.callIntMethod(vm, packageManager, signature,
                new TestVarArg(vm, method, permissionHash, packageHash));
    }

    private static int invokeCheckSelfPermission(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> context, String permission) {
        int permissionHash = vm.addLocalObject(new StringObject(vm, permission));
        DvmClass dvmClass = context.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "checkSelfPermission",
                "(Ljava/lang/String;)I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, context, signature,
                    new TestVaList(vm, method, permissionHash));
        }
        return jni.callIntMethod(vm, context, signature,
                new TestVarArg(vm, method, permissionHash));
    }

    private static DvmObject<?> getSignaturesField(AbstractJni jni, BaseVM vm, DvmObject<?> packageInfo) {
        return jni.getObjectField(vm, packageInfo,
                "android/content/pm/PackageInfo->signatures:[Landroid/content/pm/Signature;");
    }

    private static DvmObject<?> getSigningInfoField(AbstractJni jni, BaseVM vm, DvmObject<?> packageInfo) {
        return jni.getObjectField(vm, packageInfo,
                "android/content/pm/PackageInfo->signingInfo:Landroid/content/pm/SigningInfo;");
    }

    private static DvmObject<?> invokeGetApkContentsSigners(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                            DvmObject<?> signingInfo) {
        DvmClass dvmClass = signingInfo.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getApkContentsSigners",
                "()[Landroid/content/pm/Signature;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, signingInfo, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, signingInfo, signature, new TestVarArg(vm, method));
    }

    private static DvmObject<?> invokeGetSigningCertificateHistory(AbstractJni jni, BaseVM vm,
                                                                   boolean useVaList,
                                                                   DvmObject<?> signingInfo) {
        DvmClass dvmClass = signingInfo.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSigningCertificateHistory",
                "()[Landroid/content/pm/Signature;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, signingInfo, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, signingInfo, signature, new TestVarArg(vm, method));
    }

    private static boolean invokeHasMultipleSigners(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                    DvmObject<?> signingInfo) {
        DvmClass dvmClass = signingInfo.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "hasMultipleSigners", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, signingInfo, signature, new TestVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, signingInfo, signature, new TestVarArg(vm, method));
    }

    private static boolean invokeHasSigningCertificate(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> packageManager, String packageName,
                                                       byte[] cert, int inputType) {
        int nameHash = vm.addLocalObject(new StringObject(vm, packageName));
        int certHash = vm.addLocalObject(new ByteArray(vm, cert));
        DvmClass dvmClass = packageManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "hasSigningCertificate",
                "(Ljava/lang/String;[BI)Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, packageManager, signature,
                    new TestVaList(vm, method, nameHash, certHash, inputType));
        }
        return jni.callBooleanMethod(vm, packageManager, signature,
                new TestVarArg(vm, method, nameHash, certHash, inputType));
    }

    private static boolean invokeHasSigningCertificateUid(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                          DvmObject<?> packageManager, int uid,
                                                          byte[] cert, int inputType) {
        int certHash = vm.addLocalObject(new ByteArray(vm, cert));
        DvmClass dvmClass = packageManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "hasSigningCertificate", "(I[BI)Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, packageManager, signature,
                    new TestVaList(vm, method, uid, certHash, inputType));
        }
        return jni.callBooleanMethod(vm, packageManager, signature,
                new TestVarArg(vm, method, uid, certHash, inputType));
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] invokeSignatureToByteArray(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                     DvmObject<?> signatureObj) {
        DvmClass dvmClass = signatureObj.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "toByteArray", "()[B", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, signatureObj, signature, new TestVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, signatureObj, signature, new TestVarArg(vm, method));
        }
        assertTrue(result instanceof ByteArray);
        return ((ByteArray) result).getValue();
    }

    private static String invokeSignatureToCharsString(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> signatureObj) {
        DvmClass dvmClass = signatureObj.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "toCharsString", "()Ljava/lang/String;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, signatureObj, signature, new TestVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, signatureObj, signature, new TestVarArg(vm, method));
        }
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static int invokeSignatureHashCode(AbstractJni jni, BaseVM vm, boolean useVaList,
                                               DvmObject<?> signatureObj) {
        DvmClass dvmClass = signatureObj.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "hashCode", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, signatureObj, signature, new TestVaList(vm, method));
        }
        return jni.callIntMethod(vm, signatureObj, signature, new TestVarArg(vm, method));
    }

    private static String getStringField(AbstractJni jni, BaseVM vm, DvmObject<?> target, String fieldName) {
        DvmObject<?> result = getObjectField(jni, vm, target, fieldName);
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static DvmObject<?> getObjectField(AbstractJni jni, BaseVM vm, DvmObject<?> target, String fieldName) {
        return jni.getObjectField(vm, target,
                "android/content/pm/PackageInfo->" + fieldName + ":Ljava/lang/String;");
    }

    private static String getAppStringField(AbstractJni jni, BaseVM vm, DvmObject<?> target, String fieldName) {
        DvmObject<?> result = getAppObjectField(jni, vm, target, fieldName);
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static DvmObject<?> getAppObjectField(AbstractJni jni, BaseVM vm, DvmObject<?> target,
                                                  String fieldName) {
        return jni.getObjectField(vm, target,
                "android/content/pm/ApplicationInfo->" + fieldName + ":Ljava/lang/String;");
    }

    private static int getAppIntField(AbstractJni jni, BaseVM vm, DvmObject<?> target, String fieldName) {
        return jni.getIntField(vm, target, "android/content/pm/ApplicationInfo->" + fieldName + ":I");
    }

    private static boolean getAppBooleanField(AbstractJni jni, BaseVM vm, DvmObject<?> target, String fieldName) {
        return jni.getBooleanField(vm, target, "android/content/pm/ApplicationInfo->" + fieldName + ":Z");
    }

    private static int getIntField(AbstractJni jni, BaseVM vm, DvmObject<?> target, String fieldName) {
        return jni.getIntField(vm, target, "android/content/pm/PackageInfo->" + fieldName + ":I");
    }

    private static long getLongField(AbstractJni jni, BaseVM vm, DvmObject<?> target, String fieldName) {
        return jni.getLongField(vm, target, "android/content/pm/PackageInfo->" + fieldName + ":J");
    }

    private static final class TestVarArg extends VarArg {
        TestVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int int0) {
            super(vm, method);
            args.add(int0);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int objectHash0, int int1) {
            super(vm, method);
            args.add(objectHash0);
            args.add(int1);
        }

        /** Three int args: (object,object,int) or (int,object,int) overloads. */
        TestVarArg(BaseVM vm, DvmMethod method, int arg0, int arg1, int arg2) {
            super(vm, method);
            args.add(arg0);
            args.add(arg1);
            args.add(arg2);
        }
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVaList(BaseVM vm, DvmMethod method, int int0) {
            super(vm, method);
            args.add(int0);
        }

        TestVaList(BaseVM vm, DvmMethod method, int objectHash0, int int1) {
            super(vm, method);
            args.add(objectHash0);
            args.add(int1);
        }

        /** Three int args: (object,object,int) or (int,object,int) overloads. */
        TestVaList(BaseVM vm, DvmMethod method, int arg0, int arg1, int arg2) {
            super(vm, method);
            args.add(arg0);
            args.add(arg1);
            args.add(arg2);
        }
    }

    private static final class CapturedEvent {
        final String kind;
        final String api;
        final Object value;
        final String source;
        final String note;

        CapturedEvent(String kind, String api, Object value, String source, String note) {
            this.kind = kind;
            this.api = api;
            this.value = value;
            this.source = source;
            this.note = note;
        }
    }

    private static final class CapturingSink implements TraceEnvironmentEventSink {
        final List<CapturedEvent> events = new ArrayList<CapturedEvent>();

        @Override
        public void emitEnvironmentEvent(String kind, String api, Object value, String source,
                                         String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}
