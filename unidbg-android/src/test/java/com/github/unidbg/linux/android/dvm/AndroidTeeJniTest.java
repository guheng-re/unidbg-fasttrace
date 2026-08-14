package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AndroidTeeJniTest {

    private static final String TEE_TRUSTED_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"tee\":{"
            + "\"available\":true,"
            + "\"securityLevel\":\"TRUSTED_ENVIRONMENT\","
            + "\"marker\":\"TRACEAI_TEE_MARKER_V1\""
            + "}}"
            + "}";

    private static final String TEE_KEY_META_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"tee\":{"
            + "\"marker\":\"TRACEAI_TEE_MARKER_V1\","
            + "\"keyBlobHex\":\"DEADBEEF\","
            + "\"keyAlgorithm\":\"AES\","
            + "\"keyFormat\":\"RAW\""
            + "}}"
            + "}";

    @Test
    public void testTeeJniVarArg32() throws Exception {
        runTeeJni(false, false);
    }

    @Test
    public void testTeeJniVaList64() throws Exception {
        runTeeJni(true, true);
    }

    @Test
    public void testKeyAlgorithmFormatConfiguredVarArg32() throws Exception {
        runKeyAlgorithmFormat(false, false);
    }

    @Test
    public void testKeyAlgorithmFormatConfiguredVaList64() throws Exception {
        runKeyAlgorithmFormat(true, true);
    }

    @Test
    public void testKeyAlgorithmFormatProvenanceStaleMissingVarArg32() throws Exception {
        runKeyAlgorithmFormatProvenanceStaleMissing(false, false);
    }

    @Test
    public void testKeyAlgorithmFormatProvenanceStaleMissingVaList64() throws Exception {
        runKeyAlgorithmFormatProvenanceStaleMissing(true, true);
    }

    @Test
    public void testKeyInfoCrossVmRejectedVarArg32() throws Exception {
        runKeyInfoCrossVmRejected(false, false);
    }

    @Test
    public void testKeyInfoCrossVmRejectedVaList64() throws Exception {
        runKeyInfoCrossVmRejected(true, true);
    }

    @Test
    public void testKeyGetEncodedCrossVmRejectedVarArg32() throws Exception {
        runKeyGetEncodedCrossVmRejected(false, false);
    }

    @Test
    public void testKeyGetEncodedCrossVmRejectedVaList64() throws Exception {
        runKeyGetEncodedCrossVmRejected(true, true);
    }

    /**
     * KeyInfo marker from VM A (KeyFactory.getKeySpec) must not authorize KeyInfo getters on
     * VM B (different tee config). VM B gets UOE and no sidecar; VM A still returns the
     * original marker / securityLevel / inside results.
     */
    private static void runKeyInfoCrossVmRejected(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(TEE_TRUSTED_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"packageName\":\"com.other.app\","
                + "\"tee\":{"
                + "\"available\":true,"
                + "\"securityLevel\":\"SOFTWARE\","
                + "\"marker\":\"OTHER_TEE_MARKER\""
                + "}}"
                + "}");
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configB)
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

            DvmObject<?> keyFactoryA = vmA.resolveClass("java/security/KeyFactory").newObject(null);
            DvmObject<?> dummyKeyA = vmA.resolveClass("java/security/Key").newObject(null);
            DvmClass keyInfoClassA = vmA.resolveClass("android/security/keystore/KeyInfo");
            DvmObject<?> keyInfoA = invokeGetKeySpec(jniA, baseA, useVaList,
                    keyFactoryA, dummyKeyA, keyInfoClassA);
            assertNotNull(keyInfoA);
            assertEquals("TRACEAI_TEE_MARKER_V1",
                    invokeGetKeystoreAlias(jniA, baseA, useVaList, keyInfoA));
            assertEquals(1, invokeGetSecurityLevel(jniA, baseA, useVaList, keyInfoA));
            assertTrue(invokeIsInsideSecureHardware(jniA, baseA, useVaList, keyInfoA));

            sinkB.events.clear();
            try {
                invokeGetKeystoreAlias(jniB, baseB, useVaList, keyInfoA);
                fail("expected UOE for cross-VM KeyInfo.getKeystoreAlias");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getKeystoreAlias"));
            }
            try {
                invokeGetSecurityLevel(jniB, baseB, useVaList, keyInfoA);
                fail("expected UOE for cross-VM KeyInfo.getSecurityLevel");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSecurityLevel"));
            }
            try {
                invokeIsInsideSecureHardware(jniB, baseB, useVaList, keyInfoA);
                fail("expected UOE for cross-VM KeyInfo.isInsideSecureHardware");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isInsideSecureHardware"));
            }
            assertTrue("cross-VM KeyInfo must not emit sidecar on VM B", sinkB.events.isEmpty());

            assertEquals("TRACEAI_TEE_MARKER_V1",
                    invokeGetKeystoreAlias(jniA, baseA, useVaList, keyInfoA));
            assertEquals(1, invokeGetSecurityLevel(jniA, baseA, useVaList, keyInfoA));
            assertTrue(invokeIsInsideSecureHardware(jniA, baseA, useVaList, keyInfoA));
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
     * ConfiguredTeeKey from VM A (KeyStore.getKey) must not authorize Key.getEncoded on VM B
     * (different keyBlobHex). VM B gets UOE and no sidecar; VM A still returns A's blob.
     */
    private static void runKeyGetEncodedCrossVmRejected(boolean is64Bit, boolean useVaList) throws Exception {
        final byte[] blobA = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"packageName\":\"com.demo.app\","
                + "\"tee\":{"
                + "\"keyBlobHex\":\"DEADBEEF\""
                + "}}"
                + "}");
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"packageName\":\"com.other.app\","
                + "\"tee\":{"
                + "\"keyBlobHex\":\"CAFEBABE\""
                + "}}"
                + "}");
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configB)
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

            DvmObject<?> keyStoreA = vmA.resolveClass("java/security/KeyStore").newObject(null);
            DvmObject<?> keyA = invokeGetKey(jniA, baseA, useVaList, keyStoreA, "alias-a");
            assertNotNull(keyA);
            assertArrayEquals(blobA, invokeGetEncoded(jniA, baseA, useVaList, keyA));

            sinkB.events.clear();
            try {
                invokeGetEncoded(jniB, baseB, useVaList, keyA);
                fail("expected UOE for cross-VM Key.getEncoded");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getEncoded"));
            }
            assertEquals("cross-VM Key.getEncoded must not emit sidecar on VM B",
                    0, countEvents(sinkB.events, "tee", "Key.getEncoded"));

            assertArrayEquals(blobA, invokeGetEncoded(jniA, baseA, useVaList, keyA));
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

    private static void runTeeJni(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TEE_TRUSTED_JSON);
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

            DvmObject<?> keyFactory = vm.resolveClass("java/security/KeyFactory").newObject(null);
            DvmObject<?> dummyKey = vm.resolveClass("java/security/Key").newObject(null);
            DvmClass keyInfoClass = vm.resolveClass("android/security/keystore/KeyInfo");

            // getKeySpec → marker KeyInfo
            DvmObject<?> keyInfo = invokeGetKeySpec(jni, baseVM, useVaList, keyFactory, dummyKey, keyInfoClass);
            assertNotNull(keyInfo);
            assertEquals("android/security/keystore/KeyInfo", keyInfo.getObjectType().getClassName());

            // TRUSTED_ENVIRONMENT → 1, inside secure hardware true, marker alias
            assertEquals(1, invokeGetSecurityLevel(jni, baseVM, useVaList, keyInfo));
            assertTrue(invokeIsInsideSecureHardware(jni, baseVM, useVaList, keyInfo));
            assertEquals("TRACEAI_TEE_MARKER_V1",
                    invokeGetKeystoreAlias(jni, baseVM, useVaList, keyInfo));

            // three security level mappings
            assertSecurityLevelMapping(is64Bit, useVaList, "SOFTWARE", 0, false);
            assertSecurityLevelMapping(is64Bit, useVaList, "TRUSTED_ENVIRONMENT", 1, true);
            assertSecurityLevelMapping(is64Bit, useVaList, "STRONGBOX", 2, true);

            // empty tee object: getKeySpec works; field getters UOE
            TraceEnvironmentConfig emptyTee = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\",\"tee\":{}}}");
            AndroidEmulator emptyEmu = null;
            try {
                emptyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(emptyTee)
                        .build();
                VM emptyVm = emptyEmu.createDalvikVM();
                AbstractJni emptyJni = new AbstractJni() {
                };
                emptyVm.setJni(emptyJni);
                BaseVM emptyBase = (BaseVM) emptyVm;
                DvmObject<?> emptyKf = emptyVm.resolveClass("java/security/KeyFactory").newObject(null);
                DvmObject<?> emptyKey = emptyVm.resolveClass("java/security/Key").newObject(null);
                DvmClass emptyKeyInfoClass = emptyVm.resolveClass("android/security/keystore/KeyInfo");
                DvmObject<?> emptyKeyInfo = invokeGetKeySpec(emptyJni, emptyBase, useVaList,
                        emptyKf, emptyKey, emptyKeyInfoClass);
                assertNotNull(emptyKeyInfo);
                try {
                    invokeGetSecurityLevel(emptyJni, emptyBase, useVaList, emptyKeyInfo);
                    fail("expected UOE for missing securityLevel on empty tee");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getSecurityLevel"));
                }
                try {
                    invokeIsInsideSecureHardware(emptyJni, emptyBase, useVaList, emptyKeyInfo);
                    fail("expected UOE for missing securityLevel on isInsideSecureHardware");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("isInsideSecureHardware"));
                }
                try {
                    invokeGetKeystoreAlias(emptyJni, emptyBase, useVaList, emptyKeyInfo);
                    fail("expected UOE for missing marker on getKeystoreAlias");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getKeystoreAlias"));
                }
            } finally {
                if (emptyEmu != null) {
                    emptyEmu.close();
                }
            }

            // field missing while tee present: securityLevel only without marker
            TraceEnvironmentConfig levelOnly = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"tee\":{\"securityLevel\":\"SOFTWARE\"}}}");
            AndroidEmulator levelEmu = null;
            try {
                levelEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(levelOnly)
                        .build();
                VM levelVm = levelEmu.createDalvikVM();
                AbstractJni levelJni = new AbstractJni() {
                };
                levelVm.setJni(levelJni);
                BaseVM levelBase = (BaseVM) levelVm;
                DvmObject<?> levelKf = levelVm.resolveClass("java/security/KeyFactory").newObject(null);
                DvmObject<?> levelKey = levelVm.resolveClass("java/security/Key").newObject(null);
                DvmClass levelKeyInfoClass = levelVm.resolveClass("android/security/keystore/KeyInfo");
                DvmObject<?> levelKeyInfo = invokeGetKeySpec(levelJni, levelBase, useVaList,
                        levelKf, levelKey, levelKeyInfoClass);
                assertEquals(0, invokeGetSecurityLevel(levelJni, levelBase, useVaList, levelKeyInfo));
                assertFalse(invokeIsInsideSecureHardware(levelJni, levelBase, useVaList, levelKeyInfo));
                try {
                    invokeGetKeystoreAlias(levelJni, levelBase, useVaList, levelKeyInfo);
                    fail("expected UOE without marker");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("marker"));
                }
            } finally {
                if (levelEmu != null) {
                    levelEmu.close();
                }
            }

            // unrelated KeyInfo (no marker) stays UOE
            DvmObject<?> unrelated = vm.resolveClass("android/security/keystore/KeyInfo").newObject(null);
            try {
                invokeGetSecurityLevel(jni, baseVM, useVaList, unrelated);
                fail("expected UOE for unrelated KeyInfo.getSecurityLevel");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSecurityLevel"));
            }
            try {
                invokeIsInsideSecureHardware(jni, baseVM, useVaList, unrelated);
                fail("expected UOE for unrelated KeyInfo.isInsideSecureHardware");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isInsideSecureHardware"));
            }
            try {
                invokeGetKeystoreAlias(jni, baseVM, useVaList, unrelated);
                fail("expected UOE for unrelated KeyInfo.getKeystoreAlias");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getKeystoreAlias"));
            }

            // non-KeyInfo target class: notHandled → UOE (no tee KeySpec handling)
            DvmClass stringClass = vm.resolveClass("java/lang/String");
            try {
                invokeGetKeySpec(jni, baseVM, useVaList, keyFactory, dummyKey, stringClass);
                fail("expected UOE for non-KeyInfo getKeySpec target");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getKeySpec"));
            }

            // missing tee node: getKeySpec notHandled → UOE
            TraceEnvironmentConfig noTee = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\"}}");
            AndroidEmulator noTeeEmu = null;
            try {
                noTeeEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noTee)
                        .build();
                VM noTeeVm = noTeeEmu.createDalvikVM();
                AbstractJni noTeeJni = new AbstractJni() {
                };
                noTeeVm.setJni(noTeeJni);
                BaseVM noTeeBase = (BaseVM) noTeeVm;
                DvmObject<?> noTeeKf = noTeeVm.resolveClass("java/security/KeyFactory").newObject(null);
                DvmObject<?> noTeeKey = noTeeVm.resolveClass("java/security/Key").newObject(null);
                DvmClass noTeeKeyInfoClass = noTeeVm.resolveClass("android/security/keystore/KeyInfo");
                try {
                    invokeGetKeySpec(noTeeJni, noTeeBase, useVaList, noTeeKf, noTeeKey, noTeeKeyInfoClass);
                    fail("expected UOE without android.tee");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getKeySpec"));
                }
            } finally {
                if (noTeeEmu != null) {
                    noTeeEmu.close();
                }
            }

            // ---- strongBoxAvailable → hasSystemFeature fallback ----
            // true: no-version + version 0 / negative hit; version 1 miss
            assertStrongBoxFeature(is64Bit, useVaList, true, true, true, false, true);
            // false: all miss
            assertStrongBoxFeature(is64Bit, useVaList, false, false, false, false, false);

            // other feature name still UOE when only tee strongbox configured (no features node)
            TraceEnvironmentConfig sbOnly = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"tee\":{\"strongBoxAvailable\":true,\"marker\":\"SB\"}}}");
            AndroidEmulator sbEmu = null;
            try {
                sbEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(sbOnly)
                        .build();
                VM sbVm = sbEmu.createDalvikVM();
                AbstractJni sbJni = new AbstractJni() {
                };
                sbVm.setJni(sbJni);
                BaseVM sbBase = (BaseVM) sbVm;
                DvmObject<?> sbPm = sbVm.resolveClass("android/content/pm/PackageManager").newObject(null);
                assertTrue(invokeHasSystemFeature(sbJni, sbBase, useVaList, sbPm,
                        "android.hardware.strongbox_keystore"));
                try {
                    invokeHasSystemFeature(sbJni, sbBase, useVaList, sbPm, "android.hardware.camera");
                    fail("expected UOE for other feature name under tee strongbox fallback only");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("hasSystemFeature"));
                }
            } finally {
                if (sbEmu != null) {
                    sbEmu.close();
                }
            }

            // explicit empty features overrides tee strongBoxAvailable=true → authoritative false
            TraceEnvironmentConfig featuresEmpty = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{"
                    + "\"features\":[],"
                    + "\"tee\":{\"strongBoxAvailable\":true,\"marker\":\"SB\"}"
                    + "}}");
            AndroidEmulator feEmu = null;
            try {
                feEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(featuresEmpty)
                        .build();
                VM feVm = feEmu.createDalvikVM();
                AbstractJni feJni = new AbstractJni() {
                };
                feVm.setJni(feJni);
                BaseVM feBase = (BaseVM) feVm;
                DvmObject<?> fePm = feVm.resolveClass("android/content/pm/PackageManager").newObject(null);
                assertFalse(invokeHasSystemFeature(feJni, feBase, useVaList, fePm,
                        "android.hardware.strongbox_keystore"));
                assertFalse(invokeHasSystemFeatureVersion(feJni, feBase, useVaList, fePm,
                        "android.hardware.strongbox_keystore", 0));
            } finally {
                if (feEmu != null) {
                    feEmu.close();
                }
            }

            // ---- available/keymasterVersion → hardware_keystore fallback ----
            final String hwKeystore = "android.hardware.hardware_keystore";
            // available true + keymasterVersion 41: noVersion true; v40/41 true; v42 false
            assertHardwareKeystoreFeature(is64Bit, useVaList,
                    "\"available\":true,\"keymasterVersion\":41,\"marker\":\"HK\"",
                    true, true, true, false);
            // available false + version 41: all false
            assertHardwareKeystoreFeature(is64Bit, useVaList,
                    "\"available\":false,\"keymasterVersion\":41,\"marker\":\"HK\"",
                    false, false, false, false);
            // only keymasterVersion 41 (effectiveAvailable=true, configuredVersion=41)
            assertHardwareKeystoreFeature(is64Bit, useVaList,
                    "\"keymasterVersion\":41,\"marker\":\"HK\"",
                    true, true, true, false);
            // only available true (configuredVersion=0): noVersion true; v0 true; v1 false; v42 false
            {
                TraceEnvironmentConfig onlyAvail = TraceEnvironmentConfig.parse(
                        "{\"android\":{\"tee\":{\"available\":true,\"marker\":\"HK\"}}}");
                AndroidEmulator onlyAvailEmu = null;
                try {
                    onlyAvailEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                            .setEnvironmentConfig(onlyAvail)
                            .build();
                    VM oVm = onlyAvailEmu.createDalvikVM();
                    AbstractJni oJni = new AbstractJni() {
                    };
                    oVm.setJni(oJni);
                    BaseVM oBase = (BaseVM) oVm;
                    DvmObject<?> oPm = oVm.resolveClass("android/content/pm/PackageManager").newObject(null);
                    assertTrue(invokeHasSystemFeature(oJni, oBase, useVaList, oPm, hwKeystore));
                    assertTrue(invokeHasSystemFeatureVersion(oJni, oBase, useVaList, oPm, hwKeystore, 0));
                    assertFalse(invokeHasSystemFeatureVersion(oJni, oBase, useVaList, oPm, hwKeystore, 1));
                    assertFalse(invokeHasSystemFeatureVersion(oJni, oBase, useVaList, oPm, hwKeystore, 42));
                    assertTrue(invokeHasSystemFeatureVersion(oJni, oBase, useVaList, oPm, hwKeystore, -1));
                } finally {
                    if (onlyAvailEmu != null) {
                        onlyAvailEmu.close();
                    }
                }
            }
            // only available false
            {
                TraceEnvironmentConfig onlyFalse = TraceEnvironmentConfig.parse(
                        "{\"android\":{\"tee\":{\"available\":false,\"marker\":\"HK\"}}}");
                AndroidEmulator onlyFalseEmu = null;
                try {
                    onlyFalseEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                            .setEnvironmentConfig(onlyFalse)
                            .build();
                    VM oVm = onlyFalseEmu.createDalvikVM();
                    AbstractJni oJni = new AbstractJni() {
                    };
                    oVm.setJni(oJni);
                    BaseVM oBase = (BaseVM) oVm;
                    DvmObject<?> oPm = oVm.resolveClass("android/content/pm/PackageManager").newObject(null);
                    assertFalse(invokeHasSystemFeature(oJni, oBase, useVaList, oPm, hwKeystore));
                    assertFalse(invokeHasSystemFeatureVersion(oJni, oBase, useVaList, oPm, hwKeystore, 0));
                } finally {
                    if (onlyFalseEmu != null) {
                        onlyFalseEmu.close();
                    }
                }
            }
            // other feature name UOE when only hardware keystore fields configured
            {
                TraceEnvironmentConfig hkOnly = TraceEnvironmentConfig.parse(
                        "{\"android\":{\"tee\":{\"available\":true,\"keymasterVersion\":41}}}");
                AndroidEmulator hkEmu = null;
                try {
                    hkEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                            .setEnvironmentConfig(hkOnly)
                            .build();
                    VM hkVm = hkEmu.createDalvikVM();
                    AbstractJni hkJni = new AbstractJni() {
                    };
                    hkVm.setJni(hkJni);
                    BaseVM hkBase = (BaseVM) hkVm;
                    DvmObject<?> hkPm = hkVm.resolveClass("android/content/pm/PackageManager").newObject(null);
                    assertTrue(invokeHasSystemFeature(hkJni, hkBase, useVaList, hkPm, hwKeystore));
                    try {
                        invokeHasSystemFeature(hkJni, hkBase, useVaList, hkPm, "android.hardware.camera");
                        fail("expected UOE for other feature under hardware_keystore fallback only");
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("hasSystemFeature"));
                    }
                } finally {
                    if (hkEmu != null) {
                        hkEmu.close();
                    }
                }
            }
            // empty features overrides available=true + keymasterVersion
            {
                TraceEnvironmentConfig hkFe = TraceEnvironmentConfig.parse("{"
                        + "\"android\":{"
                        + "\"features\":[],"
                        + "\"tee\":{\"available\":true,\"keymasterVersion\":41,\"marker\":\"HK\"}"
                        + "}}");
                AndroidEmulator hkFeEmu = null;
                try {
                    hkFeEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                            .setEnvironmentConfig(hkFe)
                            .build();
                    VM hkFeVm = hkFeEmu.createDalvikVM();
                    AbstractJni hkFeJni = new AbstractJni() {
                    };
                    hkFeVm.setJni(hkFeJni);
                    BaseVM hkFeBase = (BaseVM) hkFeVm;
                    DvmObject<?> hkFePm = hkFeVm.resolveClass("android/content/pm/PackageManager")
                            .newObject(null);
                    assertFalse(invokeHasSystemFeature(hkFeJni, hkFeBase, useVaList, hkFePm, hwKeystore));
                    assertFalse(invokeHasSystemFeatureVersion(hkFeJni, hkFeBase, useVaList, hkFePm,
                            hwKeystore, 0));
                    assertFalse(invokeHasSystemFeatureVersion(hkFeJni, hkFeBase, useVaList, hkFePm,
                            hwKeystore, 41));
                } finally {
                    if (hkFeEmu != null) {
                        hkFeEmu.close();
                    }
                }
            }

            // ---- android.tee.keyBlobHex → KeyStore.getKey + Key.getEncoded ----
            final byte[] expectedBlob = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
            TraceEnvironmentConfig blobCfg = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{"
                    + "\"packageName\":\"com.demo.app\","
                    + "\"tee\":{"
                    + "\"available\":true,"
                    + "\"securityLevel\":\"TRUSTED_ENVIRONMENT\","
                    + "\"marker\":\"TRACEAI_TEE_MARKER_V1\","
                    + "\"keyBlobHex\":\"DEADBEEF\""
                    + "}}"
                    + "}");
            AndroidEmulator blobEmu = null;
            try {
                blobEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(blobCfg)
                        .build();
                VM blobVm = blobEmu.createDalvikVM();
                AbstractJni blobJni = new AbstractJni() {
                };
                blobVm.setJni(blobJni);
                BaseVM blobBase = (BaseVM) blobVm;
                DvmObject<?> keyStore = blobVm.resolveClass("java/security/KeyStore").newObject(null);

                // fixed bytes
                DvmObject<?> teeKey = invokeGetKey(blobJni, blobBase, useVaList, keyStore, "demo-alias");
                assertNotNull(teeKey);
                assertEquals("java/security/Key", teeKey.getObjectType().getClassName());
                byte[] encoded = invokeGetEncoded(blobJni, blobBase, useVaList, teeKey);
                assertArrayEquals(expectedBlob, encoded);

                // repeat call isolation: mutate returned array; next getEncoded still original
                encoded[0] = 0;
                byte[] encodedAgain = invokeGetEncoded(blobJni, blobBase, useVaList, teeKey);
                assertArrayEquals(expectedBlob, encodedAgain);

                // different aliases both return same fixed blob
                DvmObject<?> teeKey2 = invokeGetKey(blobJni, blobBase, useVaList, keyStore, "other-alias");
                assertArrayEquals(expectedBlob, invokeGetEncoded(blobJni, blobBase, useVaList, teeKey2));
                assertArrayEquals(expectedBlob, invokeGetEncoded(blobJni, blobBase, useVaList, teeKey));

                // arg0 wrong type → IAE
                try {
                    invokeGetKeyWithArg0(blobJni, blobBase, useVaList, keyStore,
                            blobVm.resolveClass("java/lang/Object").newObject(null));
                    fail("expected IAE for non-StringObject getKey arg0");
                } catch (IllegalArgumentException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("arg0"));
                }
            } finally {
                if (blobEmu != null) {
                    blobEmu.close();
                }
            }

            // missing keyBlobHex: getKey notHandled → UOE
            {
                TraceEnvironmentConfig noBlob = TraceEnvironmentConfig.parse(
                        "{\"android\":{\"tee\":{\"available\":true,\"marker\":\"M\"}}}");
                AndroidEmulator noBlobEmu = null;
                try {
                    noBlobEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                            .setEnvironmentConfig(noBlob)
                            .build();
                    VM noBlobVm = noBlobEmu.createDalvikVM();
                    AbstractJni noBlobJni = new AbstractJni() {
                    };
                    noBlobVm.setJni(noBlobJni);
                    BaseVM noBlobBase = (BaseVM) noBlobVm;
                    DvmObject<?> noBlobKs = noBlobVm.resolveClass("java/security/KeyStore").newObject(null);
                    try {
                        invokeGetKey(noBlobJni, noBlobBase, useVaList, noBlobKs, "any");
                        fail("expected UOE without keyBlobHex");
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getKey"));
                    }
                } finally {
                    if (noBlobEmu != null) {
                        noBlobEmu.close();
                    }
                }
            }

            // unrelated Key (no ConfiguredTeeKey marker) getEncoded → UOE
            DvmObject<?> plainKey = vm.resolveClass("java/security/Key").newObject(null);
            try {
                invokeGetEncoded(jni, baseVM, useVaList, plainKey);
                fail("expected UOE for unrelated Key.getEncoded");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getEncoded"));
            }
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    /**
     * available+keymasterVersion=41 cases: checks noVersion, v40, v41, v42.
     */
    private static void assertHardwareKeystoreFeature(boolean is64Bit, boolean useVaList, String teeFields,
                                                      boolean expectedNoVersion, boolean expectedV40,
                                                      boolean expectedV41, boolean expectedV42)
            throws Exception {
        TraceEnvironmentConfig cfg = TraceEnvironmentConfig.parse(
                "{\"android\":{\"tee\":{" + teeFields + "}}}");
        AndroidEmulator emu = null;
        try {
            emu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(cfg)
                    .build();
            VM vm = emu.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM base = (BaseVM) vm;
            DvmObject<?> pm = vm.resolveClass("android/content/pm/PackageManager").newObject(null);
            final String name = "android.hardware.hardware_keystore";
            assertEquals("noVersion " + teeFields, expectedNoVersion,
                    invokeHasSystemFeature(jni, base, useVaList, pm, name));
            assertEquals("v40 " + teeFields, expectedV40,
                    invokeHasSystemFeatureVersion(jni, base, useVaList, pm, name, 40));
            assertEquals("v41 " + teeFields, expectedV41,
                    invokeHasSystemFeatureVersion(jni, base, useVaList, pm, name, 41));
            assertEquals("v42 " + teeFields, expectedV42,
                    invokeHasSystemFeatureVersion(jni, base, useVaList, pm, name, 42));
        } finally {
            if (emu != null) {
                emu.close();
            }
        }
    }

    /**
     * @param expectedNoVersion expected hasSystemFeature(name)
     * @param expectedV0        hasSystemFeature(name, 0)
     * @param expectedV1        hasSystemFeature(name, 1)
     * @param expectedNeg       hasSystemFeature(name, -1)
     */
    private static void assertStrongBoxFeature(boolean is64Bit, boolean useVaList, boolean strongBoxAvailable,
                                               boolean expectedNoVersion, boolean expectedV0,
                                               boolean expectedV1, boolean expectedNeg) throws Exception {
        TraceEnvironmentConfig cfg = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"tee\":{"
                + "\"strongBoxAvailable\":" + strongBoxAvailable + ","
                + "\"marker\":\"SB_MARKER\""
                + "}}}");
        AndroidEmulator emu = null;
        try {
            emu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(cfg)
                    .build();
            VM vm = emu.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM base = (BaseVM) vm;
            DvmObject<?> pm = vm.resolveClass("android/content/pm/PackageManager").newObject(null);
            final String name = "android.hardware.strongbox_keystore";
            assertEquals("noVersion available=" + strongBoxAvailable, expectedNoVersion,
                    invokeHasSystemFeature(jni, base, useVaList, pm, name));
            assertEquals("v0 available=" + strongBoxAvailable, expectedV0,
                    invokeHasSystemFeatureVersion(jni, base, useVaList, pm, name, 0));
            assertEquals("v1 available=" + strongBoxAvailable, expectedV1,
                    invokeHasSystemFeatureVersion(jni, base, useVaList, pm, name, 1));
            assertEquals("neg available=" + strongBoxAvailable, expectedNeg,
                    invokeHasSystemFeatureVersion(jni, base, useVaList, pm, name, -1));
        } finally {
            if (emu != null) {
                emu.close();
            }
        }
    }

    private static void assertSecurityLevelMapping(boolean is64Bit, boolean useVaList,
                                                   String level, int expectedInt,
                                                   boolean insideSecure) throws Exception {
        TraceEnvironmentConfig cfg = TraceEnvironmentConfig.parse(
                "{\"android\":{\"tee\":{\"securityLevel\":\"" + level + "\",\"marker\":\"M\"}}}");
        AndroidEmulator emu = null;
        try {
            emu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(cfg)
                    .build();
            VM vm = emu.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM base = (BaseVM) vm;
            DvmObject<?> kf = vm.resolveClass("java/security/KeyFactory").newObject(null);
            DvmObject<?> key = vm.resolveClass("java/security/Key").newObject(null);
            DvmClass keyInfoClass = vm.resolveClass("android/security/keystore/KeyInfo");
            DvmObject<?> keyInfo = invokeGetKeySpec(jni, base, useVaList, kf, key, keyInfoClass);
            assertEquals("level=" + level, expectedInt,
                    invokeGetSecurityLevel(jni, base, useVaList, keyInfo));
            assertEquals("insideSecure for " + level, insideSecure,
                    invokeIsInsideSecureHardware(jni, base, useVaList, keyInfo));
        } finally {
            if (emu != null) {
                emu.close();
            }
        }
    }

    private static DvmObject<?> invokeGetKeySpec(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> keyFactory, DvmObject<?> key,
                                                 DvmClass keySpecClass) {
        int keyHash = vm.addLocalObject(key);
        int classHash = vm.addLocalObject(keySpecClass);
        DvmClass dvmClass = keyFactory.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getKeySpec",
                "(Ljava/security/Key;Ljava/lang/Class;)Ljava/security/spec/KeySpec;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, keyFactory, signature,
                    new TestVaList(vm, method, keyHash, classHash));
        }
        return jni.callObjectMethod(vm, keyFactory, signature,
                new TestVarArg(vm, method, keyHash, classHash));
    }

    private static int invokeGetSecurityLevel(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmObject<?> keyInfo) {
        DvmClass dvmClass = keyInfo.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSecurityLevel", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, keyInfo, signature, new TestVaList(vm, method));
        }
        return jni.callIntMethod(vm, keyInfo, signature, new TestVarArg(vm, method));
    }

    private static boolean invokeIsInsideSecureHardware(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                        DvmObject<?> keyInfo) {
        DvmClass dvmClass = keyInfo.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "isInsideSecureHardware", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, keyInfo, signature, new TestVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, keyInfo, signature, new TestVarArg(vm, method));
    }

    private static String invokeGetKeystoreAlias(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> keyInfo) {
        DvmClass dvmClass = keyInfo.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getKeystoreAlias", "()Ljava/lang/String;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, keyInfo, signature, new TestVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, keyInfo, signature, new TestVarArg(vm, method));
        }
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static boolean invokeHasSystemFeature(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  DvmObject<?> packageManager, String featureName) {
        int nameHash = vm.addLocalObject(new StringObject(vm, featureName));
        DvmClass dvmClass = packageManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "hasSystemFeature", "(Ljava/lang/String;)Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, packageManager, signature,
                    new TestVaList(vm, method, nameHash));
        }
        return jni.callBooleanMethod(vm, packageManager, signature,
                new TestVarArg(vm, method, nameHash));
    }

    private static boolean invokeHasSystemFeatureVersion(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                         DvmObject<?> packageManager, String featureName,
                                                         int version) {
        int nameHash = vm.addLocalObject(new StringObject(vm, featureName));
        DvmClass dvmClass = packageManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "hasSystemFeature", "(Ljava/lang/String;I)Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, packageManager, signature,
                    new TestVaList(vm, method, nameHash, version));
        }
        return jni.callBooleanMethod(vm, packageManager, signature,
                new TestVarArg(vm, method, nameHash, version));
    }

    private static DvmObject<?> invokeGetKey(AbstractJni jni, BaseVM vm, boolean useVaList,
                                             DvmObject<?> keyStore, String alias) {
        return invokeGetKeyWithArg0(jni, vm, useVaList, keyStore, new StringObject(vm, alias));
    }

    private static DvmObject<?> invokeGetKeyWithArg0(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                     DvmObject<?> keyStore, DvmObject<?> aliasArg) {
        int aliasHash = vm.addLocalObject(aliasArg);
        int passwordHash = 0; // password ignored
        DvmClass dvmClass = keyStore.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getKey",
                "(Ljava/lang/String;[C)Ljava/security/Key;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, keyStore, signature,
                    new TestVaList(vm, method, aliasHash, passwordHash));
        }
        return jni.callObjectMethod(vm, keyStore, signature,
                new TestVarArg(vm, method, aliasHash, passwordHash));
    }

    private static byte[] invokeGetEncoded(AbstractJni jni, BaseVM vm, boolean useVaList,
                                           DvmObject<?> key) {
        DvmClass dvmClass = key.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getEncoded", "()[B", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, key, signature, new TestVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, key, signature, new TestVarArg(vm, method));
        }
        assertTrue(result instanceof ByteArray);
        return ((ByteArray) result).getValue();
    }

    private static void runKeyAlgorithmFormat(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TEE_KEY_META_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> keyStore = vm.resolveClass("java/security/KeyStore").newObject(null);
            DvmObject<?> teeKey = invokeGetKey(jni, baseVM, useVaList, keyStore, "demo-alias");

            assertEquals("AES", invokeGetAlgorithm(jni, baseVM, useVaList, teeKey));
            CapturedEvent algoEv = findLastEvent(sink.events, "tee", "Key.getAlgorithm");
            assertNotNull(algoEv);
            assertEquals("json-config", algoEv.source);
            assertEquals("field=keyAlgorithm,result=AES", String.valueOf(algoEv.value));
            assertNotNull(algoEv.note);
            assertFalse(algoEv.note.isEmpty());

            assertEquals("RAW", invokeGetFormat(jni, baseVM, useVaList, teeKey));
            CapturedEvent formatEv = findLastEvent(sink.events, "tee", "Key.getFormat");
            assertNotNull(formatEv);
            assertEquals("json-config", formatEv.source);
            assertEquals("field=keyFormat,result=RAW", String.valueOf(formatEv.value));

            assertEquals(1, countEvents(sink.events, "tee", "Key.getAlgorithm"));
            assertEquals(1, countEvents(sink.events, "tee", "Key.getFormat"));

            // independent presence: only keyAlgorithm
            TraceEnvironmentConfig algoOnly = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"tee\":{"
                    + "\"keyBlobHex\":\"AABB\","
                    + "\"keyAlgorithm\":\"HmacSHA256\""
                    + "}}}");
            AndroidEmulator algoEmu = null;
            CapturingSink algoSink = new CapturingSink();
            try {
                algoEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(algoOnly)
                        .build();
                TraceEnvironmentEventSink.register(algoEmu, algoSink);
                VM algoVm = algoEmu.createDalvikVM();
                AbstractJni algoJni = new AbstractJni() {
                };
                algoVm.setJni(algoJni);
                BaseVM algoBase = (BaseVM) algoVm;
                DvmObject<?> algoKs = algoVm.resolveClass("java/security/KeyStore").newObject(null);
                DvmObject<?> algoKey = invokeGetKey(algoJni, algoBase, useVaList, algoKs, "a");
                assertEquals("HmacSHA256", invokeGetAlgorithm(algoJni, algoBase, useVaList, algoKey));
                try {
                    invokeGetFormat(algoJni, algoBase, useVaList, algoKey);
                    fail("expected UOE when keyFormat omitted");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getFormat"));
                }
                assertEquals(0, countEvents(algoSink.events, "tee", "Key.getFormat"));
            } finally {
                if (algoEmu != null) {
                    TraceEnvironmentEventSink.unregister(algoEmu, algoSink);
                    algoEmu.close();
                }
            }

            // independent presence: only keyFormat
            TraceEnvironmentConfig formatOnly = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"tee\":{"
                    + "\"keyBlobHex\":\"CCDD\","
                    + "\"keyFormat\":\"X.509\""
                    + "}}}");
            AndroidEmulator formatEmu = null;
            CapturingSink formatSink = new CapturingSink();
            try {
                formatEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(formatOnly)
                        .build();
                TraceEnvironmentEventSink.register(formatEmu, formatSink);
                VM formatVm = formatEmu.createDalvikVM();
                AbstractJni formatJni = new AbstractJni() {
                };
                formatVm.setJni(formatJni);
                BaseVM formatBase = (BaseVM) formatVm;
                DvmObject<?> formatKs = formatVm.resolveClass("java/security/KeyStore").newObject(null);
                DvmObject<?> formatKey = invokeGetKey(formatJni, formatBase, useVaList, formatKs, "f");
                assertEquals("X.509", invokeGetFormat(formatJni, formatBase, useVaList, formatKey));
                try {
                    invokeGetAlgorithm(formatJni, formatBase, useVaList, formatKey);
                    fail("expected UOE when keyAlgorithm omitted");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getAlgorithm"));
                }
                assertEquals(0, countEvents(formatSink.events, "tee", "Key.getAlgorithm"));
            } finally {
                if (formatEmu != null) {
                    TraceEnvironmentEventSink.unregister(formatEmu, formatSink);
                    formatEmu.close();
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runKeyAlgorithmFormatProvenanceStaleMissing(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TEE_KEY_META_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> keyStore = vm.resolveClass("java/security/KeyStore").newObject(null);
            DvmObject<?> teeKey = invokeGetKey(jni, baseVM, useVaList, keyStore, "live");

            // plain Key
            DvmObject<?> plain = vm.resolveClass("java/security/Key").newObject(null);
            try {
                invokeGetAlgorithm(jni, baseVM, useVaList, plain);
                fail("expected UOE for plain Key.getAlgorithm");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAlgorithm"));
            }
            try {
                invokeGetFormat(jni, baseVM, useVaList, plain);
                fail("expected UOE for plain Key.getFormat");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getFormat"));
            }
            assertEquals(0, countEvents(sink.events, "tee", "Key.getAlgorithm"));
            assertEquals(0, countEvents(sink.events, "tee", "Key.getFormat"));

            // live marker still works
            assertEquals("AES", invokeGetAlgorithm(jni, baseVM, useVaList, teeKey));
            assertEquals("RAW", invokeGetFormat(jni, baseVM, useVaList, teeKey));
            assertEquals(1, countEvents(sink.events, "tee", "Key.getAlgorithm"));
            assertEquals(1, countEvents(sink.events, "tee", "Key.getFormat"));

            // stale: re-parse same JSON → new config identity
            emulator.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(TEE_KEY_META_JSON));
            try {
                invokeGetAlgorithm(jni, baseVM, useVaList, teeKey);
                fail("expected UOE for stale Key.getAlgorithm");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAlgorithm"));
            }
            try {
                invokeGetFormat(jni, baseVM, useVaList, teeKey);
                fail("expected UOE for stale Key.getFormat");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getFormat"));
            }
            assertEquals(1, countEvents(sink.events, "tee", "Key.getAlgorithm"));
            assertEquals(1, countEvents(sink.events, "tee", "Key.getFormat"));

            // config removed / tee missing
            emulator.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse("{\"android\":{\"packageName\":\"com.demo.app\"}}"));
            try {
                invokeGetAlgorithm(jni, baseVM, useVaList, teeKey);
                fail("expected UOE after tee removed");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAlgorithm"));
            }
            assertEquals(1, countEvents(sink.events, "tee", "Key.getAlgorithm"));

            // keyBlobHex absent: getKey notHandled; meta fields alone do not mint markers
            TraceEnvironmentConfig noBlob = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"tee\":{"
                    + "\"keyAlgorithm\":\"AES\","
                    + "\"keyFormat\":\"RAW\""
                    + "}}}");
            AndroidEmulator noBlobEmu = null;
            CapturingSink noBlobSink = new CapturingSink();
            try {
                noBlobEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noBlob)
                        .build();
                TraceEnvironmentEventSink.register(noBlobEmu, noBlobSink);
                VM noBlobVm = noBlobEmu.createDalvikVM();
                AbstractJni noBlobJni = new AbstractJni() {
                };
                noBlobVm.setJni(noBlobJni);
                BaseVM noBlobBase = (BaseVM) noBlobVm;
                DvmObject<?> noBlobKs = noBlobVm.resolveClass("java/security/KeyStore").newObject(null);
                try {
                    invokeGetKey(noBlobJni, noBlobBase, useVaList, noBlobKs, "x");
                    fail("expected UOE without keyBlobHex");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getKey"));
                }
                DvmObject<?> plainNoBlob = noBlobVm.resolveClass("java/security/Key").newObject(null);
                try {
                    invokeGetAlgorithm(noBlobJni, noBlobBase, useVaList, plainNoBlob);
                    fail("expected UOE without marker for getAlgorithm");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getAlgorithm"));
                }
                assertEquals(0, countEvents(noBlobSink.events, "tee", "Key.getAlgorithm"));
                assertEquals(0, countEvents(noBlobSink.events, "tee", "Key.getFormat"));
            } finally {
                if (noBlobEmu != null) {
                    TraceEnvironmentEventSink.unregister(noBlobEmu, noBlobSink);
                    noBlobEmu.close();
                }
            }

            // cross-VM: marker from other VM not live
            TraceEnvironmentConfig crossCfg = TraceEnvironmentConfig.parse(TEE_KEY_META_JSON);
            AndroidEmulator crossEmu = null;
            CapturingSink crossSink = new CapturingSink();
            try {
                crossEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(crossCfg)
                        .build();
                TraceEnvironmentEventSink.register(crossEmu, crossSink);
                VM crossVm = crossEmu.createDalvikVM();
                AbstractJni crossJni = new AbstractJni() {
                };
                crossVm.setJni(crossJni);
                BaseVM crossBase = (BaseVM) crossVm;
                DvmObject<?> crossKs = crossVm.resolveClass("java/security/KeyStore").newObject(null);
                DvmObject<?> foreignKey = invokeGetKey(crossJni, crossBase, useVaList, crossKs, "foreign");
                // call foreign marker through original jni/vm → not live
                try {
                    invokeGetAlgorithm(jni, baseVM, useVaList, foreignKey);
                    fail("expected UOE for cross-VM Key.getAlgorithm");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getAlgorithm"));
                }
                assertEquals(1, countEvents(sink.events, "tee", "Key.getAlgorithm"));
                assertEquals(0, countEvents(crossSink.events, "tee", "Key.getAlgorithm"));
            } finally {
                if (crossEmu != null) {
                    TraceEnvironmentEventSink.unregister(crossEmu, crossSink);
                    crossEmu.close();
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static String invokeGetAlgorithm(AbstractJni jni, BaseVM vm, boolean useVaList,
                                             DvmObject<?> key) {
        DvmClass dvmClass = key.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getAlgorithm", "()Ljava/lang/String;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, key, signature, new TestVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, key, signature, new TestVarArg(vm, method));
        }
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static String invokeGetFormat(AbstractJni jni, BaseVM vm, boolean useVaList,
                                          DvmObject<?> key) {
        DvmClass dvmClass = key.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getFormat", "()Ljava/lang/String;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, key, signature, new TestVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, key, signature, new TestVarArg(vm, method));
        }
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static CapturedEvent findLastEvent(List<CapturedEvent> events, String kind, String api) {
        CapturedEvent found = null;
        for (CapturedEvent e : events) {
            if (kind.equals(e.kind) && api.equals(e.api)) {
                found = e;
            }
        }
        return found;
    }

    private static int countEvents(List<CapturedEvent> events, String kind, String api) {
        int n = 0;
        for (CapturedEvent e : events) {
            if (kind.equals(e.kind) && api.equals(e.api)) {
                n++;
            }
        }
        return n;
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
        public void emitEnvironmentEvent(String kind, String api, Object value, String source, String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }

    private static final class TestVarArg extends VarArg {
        TestVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int objectHash0, int int1) {
            super(vm, method);
            args.add(objectHash0);
            args.add(int1);
        }
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVaList(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
        }

        TestVaList(BaseVM vm, DvmMethod method, int objectHash0, int int1) {
            super(vm, method);
            args.add(objectHash0);
            args.add(int1);
        }
    }
}
