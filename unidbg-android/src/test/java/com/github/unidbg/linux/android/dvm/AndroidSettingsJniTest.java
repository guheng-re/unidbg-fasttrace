package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.sun.jna.Pointer;
import org.junit.Test;
import unicorn.Arm64Const;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AndroidSettingsJniTest {

    private static final String SETTINGS_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"settings\":{"
            + "\"secure\":{"
            + "\"android_id\":\"a1b2c3d4e5f67890\","
            + "\"explicit_null\":null,"
            + "\"development_settings_enabled\":\"1\","
            + "\"null_int\":null,"
            + "\"bad_int\":\"abc\","
            + "\"animator_duration_scale\":\"0.0\","
            + "\"null_long\":null,"
            + "\"bad_long\":\"abc\","
            + "\"overflow_long\":\"9223372036854775808\""
            + "},"
            + "\"system\":{"
            + "\"screen_brightness\":\"128\","
            + "\"screen_off_timeout\":\"30000\","
            + "\"font_scale\":\"1.25\","
            + "\"null_float\":null,"
            + "\"bad_float\":\"NaN\","
            + "\"long_beyond_int\":\"3000000000\","
            + "\"long_max\":\"9223372036854775807\""
            + "},"
            + "\"global\":{"
            + "\"adb_enabled\":\"0\","
            + "\"adb_enabled_int\":\"0\","
            + "\"transition_animation_scale\":\"0.75\","
            + "\"long_min\":\"-9223372036854775808\","
            + "\"long_value\":\"42\""
            + "}"
            + "}"
            + "}"
            + "}";

    private static final String GET_STRING_ARGS =
            "(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;";
    private static final String GET_INT_NO_DEFAULT =
            "(Landroid/content/ContentResolver;Ljava/lang/String;)I";
    private static final String GET_INT_WITH_DEFAULT =
            "(Landroid/content/ContentResolver;Ljava/lang/String;I)I";
    private static final String GET_FLOAT_NO_DEFAULT =
            "(Landroid/content/ContentResolver;Ljava/lang/String;)F";
    private static final String GET_FLOAT_WITH_DEFAULT =
            "(Landroid/content/ContentResolver;Ljava/lang/String;F)F";
    private static final String GET_LONG_NO_DEFAULT =
            "(Landroid/content/ContentResolver;Ljava/lang/String;)J";
    private static final String GET_LONG_WITH_DEFAULT =
            "(Landroid/content/ContentResolver;Ljava/lang/String;J)J";

    @Test
    public void testSettingsGetStringVarArg32() throws Exception {
        runSettingsGetString(false, false);
    }

    @Test
    public void testSettingsGetStringVaList64() throws Exception {
        runSettingsGetString(true, true);
    }

    @Test
    public void testSettingsGetIntVarArg32() throws Exception {
        runSettingsGetInt(false, false);
    }

    @Test
    public void testSettingsGetIntVaList64() throws Exception {
        runSettingsGetInt(true, true);
    }

    @Test
    public void testSettingsGetFloatVarArg32() throws Exception {
        runSettingsGetFloat(false, false);
    }

    @Test
    public void testSettingsGetFloatVarArg64() throws Exception {
        runSettingsGetFloat(true, false);
    }

    @Test
    public void testSettingsGetFloatVaList32() throws Exception {
        runSettingsGetFloat(false, true);
    }

    @Test
    public void testSettingsGetFloatVaList64() throws Exception {
        runSettingsGetFloat(true, true);
    }

    @Test
    public void testSettingsGetFloatMethodA32() throws Exception {
        runSettingsGetFloatMethodA(false);
    }

    @Test
    public void testSettingsGetFloatMethodA64() throws Exception {
        runSettingsGetFloatMethodA(true);
    }

    @Test
    public void testSettingsGetLongVarArg32() throws Exception {
        runSettingsGetLong(false, false);
    }

    @Test
    public void testSettingsGetLongVaList64() throws Exception {
        runSettingsGetLong(true, true);
    }

    private static void runSettingsGetString(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SETTINGS_JSON);
        AndroidEmulator emulator = null;
        VM vm = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> resolver = vm.resolveClass("android/content/ContentResolver").newObject(null);
            int resolverHash = baseVM.addLocalObject(resolver);

            assertSettingsString(jni, baseVM, useVaList, "android/provider/Settings$Secure",
                    "android_id", "a1b2c3d4e5f67890", resolverHash);
            assertSettingsString(jni, baseVM, useVaList, "android/provider/Settings$System",
                    "screen_brightness", "128", resolverHash);
            assertSettingsString(jni, baseVM, useVaList, "android/provider/Settings$Global",
                    "adb_enabled", "0", resolverHash);

            // explicit JSON null
            DvmObject<?> explicitNull = invokeGetString(jni, baseVM, useVaList,
                    "android/provider/Settings$Secure", "explicit_null", resolverHash);
            assertNull(explicitNull);

            // missing key -> not handled -> UnsupportedOperationException
            try {
                invokeGetString(jni, baseVM, useVaList, "android/provider/Settings$Secure", "missing_key",
                        resolverHash);
                fail("expected UnsupportedOperationException for missing key");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("Settings$Secure->getString"));
            }

            // wrong arg1 type
            try {
                invokeGetStringWithArg1(jni, baseVM, useVaList, "android/provider/Settings$Secure",
                        resolverHash, resolverHash);
                fail("expected IllegalArgumentException for wrong arg1");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage() != null && e.getMessage().contains("arg1"));
                assertTrue(e.getMessage().contains("Settings$Secure->getString"));
            }

            // zero-arg regressions: must not IndexOutOfBoundsException via eager getObjectArg(1)
            if (!useVaList) {
                DvmClass activityThread = vm.resolveClass("android/app/ActivityThread");
                DvmMethod currentPackageName = new DvmMethod(activityThread, "currentPackageName",
                        "()Ljava/lang/String;", true);
                TestVarArg zeroArgs = new TestVarArg(baseVM, currentPackageName);
                DvmObject<?> pkg = jni.callStaticObjectMethod(baseVM, activityThread,
                        currentPackageName.getSignature(), zeroArgs);
                assertTrue(pkg instanceof StringObject);
                assertEquals("com.demo.app", ((StringObject) pkg).getValue());
            } else {
                DvmClass localeClass = vm.resolveClass("java/util/Locale");
                DvmMethod getDefault = new DvmMethod(localeClass, "getDefault", "()Ljava/util/Locale;", true);
                TestVaList zeroArgs = new TestVaList(baseVM, getDefault);
                DvmObject<?> locale = jni.callStaticObjectMethodV(baseVM, localeClass,
                        getDefault.getSignature(), zeroArgs);
                assertNotNull(locale);
                assertTrue(locale.getValue() instanceof Locale);
            }
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runSettingsGetInt(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SETTINGS_JSON);
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

            DvmObject<?> resolver = vm.resolveClass("android/content/ContentResolver").newObject(null);
            int resolverHash = baseVM.addLocalObject(resolver);

            assertEquals(1, invokeGetInt(jni, baseVM, useVaList, "android/provider/Settings$Secure",
                    "development_settings_enabled", resolverHash, null));
            assertEquals(30000, invokeGetInt(jni, baseVM, useVaList, "android/provider/Settings$System",
                    "screen_off_timeout", resolverHash, null));
            assertEquals(0, invokeGetInt(jni, baseVM, useVaList, "android/provider/Settings$Global",
                    "adb_enabled_int", resolverHash, null));

            // explicit null + default overload -> default value
            assertEquals(77, invokeGetInt(jni, baseVM, useVaList, "android/provider/Settings$Secure",
                    "null_int", resolverHash, 77));

            // explicit null without default -> notHandled -> UnsupportedOperationException
            try {
                invokeGetInt(jni, baseVM, useVaList, "android/provider/Settings$Secure",
                        "null_int", resolverHash, null);
                fail("expected UnsupportedOperationException for explicit null without default");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("Settings$Secure->getInt"));
            }

            // missing key always notHandled, even with default overload
            try {
                invokeGetInt(jni, baseVM, useVaList, "android/provider/Settings$Secure",
                        "missing_int_key", resolverHash, 99);
                fail("expected UnsupportedOperationException for missing key with default");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("Settings$Secure->getInt"));
            }

            // bad parseable value -> IllegalArgumentException with key/value
            try {
                invokeGetInt(jni, baseVM, useVaList, "android/provider/Settings$Secure",
                        "bad_int", resolverHash, null);
                fail("expected IllegalArgumentException for bad_int");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage() != null);
                assertTrue(e.getMessage().contains("bad_int"));
                assertTrue(e.getMessage().contains("abc"));
            }

            // zero-arg regression on int path: must not IndexOutOfBoundsException
            DvmClass processClass = vm.resolveClass("android/os/Process");
            DvmMethod myPid = new DvmMethod(processClass, "myPid", "()I", true);
            int pid;
            if (!useVaList) {
                pid = jni.callStaticIntMethod(baseVM, processClass, myPid.getSignature(),
                        new TestVarArg(baseVM, myPid));
            } else {
                pid = jni.callStaticIntMethodV(baseVM, processClass, myPid.getSignature(),
                        new TestVaList(baseVM, myPid));
            }
            assertEquals(emulator.getPid(), pid);
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    /**
     * getFloat is wired through both callStaticFloatMethod(VarArg) and
     * callStaticFloatMethodV(VaList); each 32/64 bit run executes the identical assertion set
     * on both paths to prove parity.
     */
    private static void runSettingsGetFloat(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SETTINGS_JSON);
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

            DvmObject<?> resolver = vm.resolveClass("android/content/ContentResolver").newObject(null);
            int resolverHash = baseVM.addLocalObject(resolver);

            assertEquals(1.25f, invokeGetFloat(jni, baseVM, useVaList, "android/provider/Settings$System",
                    "font_scale", resolverHash, null), 0f);
            assertEquals(0.0f, invokeGetFloat(jni, baseVM, useVaList, "android/provider/Settings$Secure",
                    "animator_duration_scale", resolverHash, null), 0f);
            assertEquals(0.75f, invokeGetFloat(jni, baseVM, useVaList, "android/provider/Settings$Global",
                    "transition_animation_scale", resolverHash, null), 0f);

            // sidecar shape for a successful read
            CapturedEvent last = findLastEvent(sink.events, "android_setting", "Settings.system.getFloat");
            assertNotNull(last);
            assertEquals("json-config", last.source);
            assertTrue(String.valueOf(last.value).contains("key=font_scale"));
            assertTrue(String.valueOf(last.value).contains("config=1.25"));
            assertTrue(String.valueOf(last.value).contains("result=1.25"));

            // explicit null + default overload -> default value
            assertEquals(2.5f, invokeGetFloat(jni, baseVM, useVaList, "android/provider/Settings$System",
                    "null_float", resolverHash, 2.5f), 0f);

            // explicit null without default -> notHandled -> UnsupportedOperationException
            try {
                invokeGetFloat(jni, baseVM, useVaList, "android/provider/Settings$System",
                        "null_float", resolverHash, null);
                fail("expected UnsupportedOperationException for explicit null float without default");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("Settings$System->getFloat"));
            }

            // missing key always notHandled, even with default overload
            try {
                invokeGetFloat(jni, baseVM, useVaList, "android/provider/Settings$System",
                        "missing_float_key", resolverHash, 9.9f);
                fail("expected UnsupportedOperationException for missing float key with default");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("Settings$System->getFloat"));
            }

            // NaN config value -> IllegalArgumentException with key/value
            try {
                invokeGetFloat(jni, baseVM, useVaList, "android/provider/Settings$System",
                        "bad_float", resolverHash, null);
                fail("expected IllegalArgumentException for bad_float NaN");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage() != null);
                assertTrue(e.getMessage().contains("bad_float"));
                assertTrue(e.getMessage().contains("NaN"));
            }

            // wrong arg1 type
            try {
                invokeGetFloatWithArg1(jni, baseVM, useVaList, "android/provider/Settings$System",
                        resolverHash, resolverHash, null);
                fail("expected IllegalArgumentException for wrong arg1");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage() != null && e.getMessage().contains("arg1"));
                assertTrue(e.getMessage().contains("Settings$System->getFloat"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    /**
     * Exercises the real JNIEnv CallStaticFloatMethodA slot (jvalue/JValueList ->
     * callStaticFloatMethodV) for a configured Settings.System.getFloat("font_scale") call.
     * 32-bit returns the float bits in R0; 64-bit returns the float in Q0.
     */
    private static void runSettingsGetFloatMethodA(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SETTINGS_JSON);
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

            DvmObject<?> resolver = vm.resolveClass("android/content/ContentResolver").newObject(null);
            int resolverHash = baseVM.addLocalObject(resolver);
            StringObject keyObj = new StringObject(vm, "font_scale");
            int keyHash = baseVM.addLocalObject(keyObj);

            DvmClass settingsSystem = vm.resolveClass("android/provider/Settings$System");
            int methodId = settingsSystem.getStaticMethodID("getFloat", GET_FLOAT_NO_DEFAULT);

            // jvalue[2]: ContentResolver ref, key String ref (8-byte slots holding ref hashes)
            UnidbgPointer jvalue = emulator.getSvcMemory().allocate(16, "jvalue");
            jvalue.setPointer(0, UnidbgPointer.pointer(emulator, resolverHash));
            jvalue.setPointer(8, UnidbgPointer.pointer(emulator, keyHash));

            UnidbgPointer env = (UnidbgPointer) vm.getJNIEnv();
            UnidbgPointer slot = (UnidbgPointer) env.getPointer(0).getPointer(is64Bit ? 0x448 : 0x224);
            // ARM32 registers are single words: pointer args must be ints, not Longs
            // (ARM.initArgs expands Longs into two register slots). Use plain if/else —
            // a ternary would promote the int to long and silently re-expand it.
            final Number envArg;
            final Number jvalueArg;
            if (is64Bit) {
                envArg = env.toUIntPeer();
                jvalueArg = jvalue.toUIntPeer();
            } else {
                envArg = (int) env.toUIntPeer();
                jvalueArg = (int) jvalue.toUIntPeer();
            }
            Number ret = emulator.eFunc(slot.toUIntPeer(),
                    envArg, settingsSystem.hashCode(), methodId, jvalueArg);

            final float result;
            if (is64Bit) {
                byte[] q0 = emulator.getBackend().reg_read_vector(Arm64Const.UC_ARM64_REG_Q0);
                result = ByteBuffer.wrap(q0).order(ByteOrder.LITTLE_ENDIAN).getFloat(0);
            } else {
                result = Float.intBitsToFloat(ret.intValue());
            }
            assertEquals(1.25f, result, 0f);
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runSettingsGetLong(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SETTINGS_JSON);
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

            DvmObject<?> resolver = vm.resolveClass("android/content/ContentResolver").newObject(null);
            int resolverHash = baseVM.addLocalObject(resolver);

            // all namespaces + values beyond int range and long boundaries
            assertEquals(42L, invokeGetLong(jni, baseVM, useVaList, "android/provider/Settings$Global",
                    "long_value", resolverHash, null));
            assertEquals(3000000000L, invokeGetLong(jni, baseVM, useVaList, "android/provider/Settings$System",
                    "long_beyond_int", resolverHash, null));
            assertEquals(Long.MAX_VALUE, invokeGetLong(jni, baseVM, useVaList, "android/provider/Settings$System",
                    "long_max", resolverHash, null));
            assertEquals(Long.MIN_VALUE, invokeGetLong(jni, baseVM, useVaList, "android/provider/Settings$Global",
                    "long_min", resolverHash, null));

            // explicit null + default overload -> default value
            assertEquals(99L, invokeGetLong(jni, baseVM, useVaList, "android/provider/Settings$Secure",
                    "null_long", resolverHash, 99L));

            // explicit null without default -> notHandled -> UnsupportedOperationException
            try {
                invokeGetLong(jni, baseVM, useVaList, "android/provider/Settings$Secure",
                        "null_long", resolverHash, null);
                fail("expected UnsupportedOperationException for explicit null long without default");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("Settings$Secure->getLong"));
            }

            // missing key always notHandled, even with default overload
            try {
                invokeGetLong(jni, baseVM, useVaList, "android/provider/Settings$Secure",
                        "missing_long_key", resolverHash, 88L);
                fail("expected UnsupportedOperationException for missing long key with default");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("Settings$Secure->getLong"));
            }

            // bad parseable value -> IllegalArgumentException with namespace/key/value
            try {
                invokeGetLong(jni, baseVM, useVaList, "android/provider/Settings$Secure",
                        "bad_long", resolverHash, null);
                fail("expected IllegalArgumentException for bad_long");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage() != null);
                assertTrue(e.getMessage().contains("secure"));
                assertTrue(e.getMessage().contains("bad_long"));
                assertTrue(e.getMessage().contains("abc"));
            }

            // overflow beyond long range
            try {
                invokeGetLong(jni, baseVM, useVaList, "android/provider/Settings$Secure",
                        "overflow_long", resolverHash, null);
                fail("expected IllegalArgumentException for overflow_long");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage() != null);
                assertTrue(e.getMessage().contains("secure"));
                assertTrue(e.getMessage().contains("overflow_long"));
                assertTrue(e.getMessage().contains("9223372036854775808"));
            }

            // wrong arg1 type
            try {
                invokeGetLongWithArg1(jni, baseVM, useVaList, "android/provider/Settings$Secure",
                        resolverHash, resolverHash, null);
                fail("expected IllegalArgumentException for wrong arg1");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage() != null && e.getMessage().contains("arg1"));
                assertTrue(e.getMessage().contains("Settings$Secure->getLong"));
            }

            // zero-arg regression: must not IndexOutOfBoundsException via eager getObjectArg(1)
            DvmClass systemClass = vm.resolveClass("java/lang/System");
            DvmMethod currentTimeMillis = new DvmMethod(systemClass, "currentTimeMillis", "()J", true);
            if (!useVaList) {
                TestVarArg zeroArgs = new TestVarArg(baseVM, currentTimeMillis);
                try {
                    jni.callStaticLongMethod(baseVM, systemClass, currentTimeMillis.getSignature(), zeroArgs);
                    fail("expected UnsupportedOperationException for System.currentTimeMillis on VarArg path");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("currentTimeMillis"));
                }
            } else {
                TestVaList zeroArgs = new TestVaList(baseVM, currentTimeMillis);
                try {
                    jni.callStaticLongMethodV(baseVM, systemClass, currentTimeMillis.getSignature(), zeroArgs);
                    fail("expected UnsupportedOperationException for System.currentTimeMillis on VaList path");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("currentTimeMillis"));
                }
            }

            // sidecar shape for a successful read
            CapturedEvent last = findLastEvent(sink.events, "android_setting", "Settings.system.getLong");
            assertNotNull(last);
            assertEquals("json-config", last.source);
            assertTrue(String.valueOf(last.value).contains("key=long_max"));
            assertTrue(String.valueOf(last.value).contains("config=9223372036854775807"));
            assertTrue(String.valueOf(last.value).contains("result=" + Long.MAX_VALUE));
            assertFalse(sink.events.isEmpty());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
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

    private static void assertSettingsString(AbstractJni jni, BaseVM vm, boolean useVaList,
                                             String className, String key,
                                             String expected, int resolverHash) {
        DvmObject<?> result = invokeGetString(jni, vm, useVaList, className, key, resolverHash);
        assertTrue(result instanceof StringObject);
        assertEquals(expected, ((StringObject) result).getValue());
    }

    private static DvmObject<?> invokeGetString(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                String className, String key, int resolverHash) {
        StringObject keyObj = new StringObject(vm, key);
        int keyHash = vm.addLocalObject(keyObj);
        return invokeGetStringWithArg1(jni, vm, useVaList, className, resolverHash, keyHash);
    }

    private static DvmObject<?> invokeGetStringWithArg1(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                        String className, int resolverHash, int arg1Hash) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, "getString", GET_STRING_ARGS, true);
        String signature = method.getSignature();
        if (useVaList) {
            TestVaList vaList = new TestVaList(vm, method, resolverHash, arg1Hash);
            return jni.callStaticObjectMethodV(vm, dvmClass, signature, vaList);
        }
        TestVarArg varArg = new TestVarArg(vm, method, resolverHash, arg1Hash);
        return jni.callStaticObjectMethod(vm, dvmClass, signature, varArg);
    }

    /**
     * @param defaultValue null means use no-default overload; non-null means with-default overload
     */
    private static int invokeGetInt(AbstractJni jni, BaseVM vm, boolean useVaList,
                                    String className, String key, int resolverHash, Integer defaultValue) {
        StringObject keyObj = new StringObject(vm, key);
        int keyHash = vm.addLocalObject(keyObj);
        DvmClass dvmClass = vm.resolveClass(className);
        boolean withDefault = defaultValue != null;
        DvmMethod method = new DvmMethod(dvmClass, "getInt",
                withDefault ? GET_INT_WITH_DEFAULT : GET_INT_NO_DEFAULT, true);
        String signature = method.getSignature();
        if (useVaList) {
            TestVaList vaList = withDefault
                    ? new TestVaList(vm, method, resolverHash, keyHash, defaultValue)
                    : new TestVaList(vm, method, resolverHash, keyHash);
            return jni.callStaticIntMethodV(vm, dvmClass, signature, vaList);
        }
        TestVarArg varArg = withDefault
                ? new TestVarArg(vm, method, resolverHash, keyHash, defaultValue)
                : new TestVarArg(vm, method, resolverHash, keyHash);
        return jni.callStaticIntMethod(vm, dvmClass, signature, varArg);
    }

    /**
     * @param defaultValue null means use no-default overload; non-null means with-default overload
     */
    private static float invokeGetFloat(AbstractJni jni, BaseVM vm, boolean useVaList,
                                        String className, String key, int resolverHash, Float defaultValue) {
        StringObject keyObj = new StringObject(vm, key);
        int keyHash = vm.addLocalObject(keyObj);
        return invokeGetFloatWithArg1(jni, vm, useVaList, className, resolverHash, keyHash, defaultValue);
    }

    private static float invokeGetFloatWithArg1(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                String className, int resolverHash, int arg1Hash,
                                                Float defaultValue) {
        DvmClass dvmClass = vm.resolveClass(className);
        boolean withDefault = defaultValue != null;
        DvmMethod method = new DvmMethod(dvmClass, "getFloat",
                withDefault ? GET_FLOAT_WITH_DEFAULT : GET_FLOAT_NO_DEFAULT, true);
        String signature = method.getSignature();
        if (useVaList) {
            TestVaList vaList = withDefault
                    ? new TestVaList(vm, method, resolverHash, arg1Hash, defaultValue.floatValue())
                    : new TestVaList(vm, method, resolverHash, arg1Hash);
            return jni.callStaticFloatMethodV(vm, dvmClass, signature, vaList);
        }
        TestVarArg varArg = withDefault
                ? new TestVarArg(vm, method, resolverHash, arg1Hash, defaultValue.floatValue())
                : new TestVarArg(vm, method, resolverHash, arg1Hash);
        return jni.callStaticFloatMethod(vm, dvmClass, signature, varArg);
    }

    /**
     * @param defaultValue null means use no-default overload; non-null means with-default overload
     */
    private static long invokeGetLong(AbstractJni jni, BaseVM vm, boolean useVaList,
                                      String className, String key, int resolverHash, Long defaultValue) {
        StringObject keyObj = new StringObject(vm, key);
        int keyHash = vm.addLocalObject(keyObj);
        return invokeGetLongWithArg1(jni, vm, useVaList, className, resolverHash, keyHash, defaultValue);
    }

    private static long invokeGetLongWithArg1(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              String className, int resolverHash, int arg1Hash,
                                              Long defaultValue) {
        DvmClass dvmClass = vm.resolveClass(className);
        boolean withDefault = defaultValue != null;
        DvmMethod method = new DvmMethod(dvmClass, "getLong",
                withDefault ? GET_LONG_WITH_DEFAULT : GET_LONG_NO_DEFAULT, true);
        String signature = method.getSignature();
        if (useVaList) {
            TestVaList vaList = withDefault
                    ? new TestVaList(vm, method, resolverHash, arg1Hash, defaultValue.longValue())
                    : new TestVaList(vm, method, resolverHash, arg1Hash);
            return jni.callStaticLongMethodV(vm, dvmClass, signature, vaList);
        }
        TestVarArg varArg = withDefault
                ? new TestVarArg(vm, method, resolverHash, arg1Hash, defaultValue.longValue())
                : new TestVarArg(vm, method, resolverHash, arg1Hash);
        return jni.callStaticLongMethod(vm, dvmClass, signature, varArg);
    }

    private static final class TestVarArg extends VarArg {
        TestVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int hash0, int hash1) {
            super(vm, method);
            args.add(hash0);
            args.add(hash1);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int hash0, int hash1, int int2) {
            super(vm, method);
            args.add(hash0);
            args.add(hash1);
            args.add(int2);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int hash0, int hash1, float float2) {
            super(vm, method);
            args.add(hash0);
            args.add(hash1);
            args.add(float2);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int hash0, int hash1, long long2) {
            super(vm, method);
            args.add(hash0);
            args.add(hash1);
            args.add(long2);
        }
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVaList(BaseVM vm, DvmMethod method, int hash0, int hash1) {
            super(vm, method);
            args.add(hash0);
            args.add(hash1);
        }

        TestVaList(BaseVM vm, DvmMethod method, int hash0, int hash1, int int2) {
            super(vm, method);
            args.add(hash0);
            args.add(hash1);
            args.add(int2);
        }

        TestVaList(BaseVM vm, DvmMethod method, int hash0, int hash1, float float2) {
            super(vm, method);
            args.add(hash0);
            args.add(hash1);
            args.add(float2);
        }

        TestVaList(BaseVM vm, DvmMethod method, int hash0, int hash1, long long2) {
            super(vm, method);
            args.add(hash0);
            args.add(hash1);
            args.add(long2);
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
        public void emitEnvironmentEvent(String kind, String api, Object value, String source, String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}
