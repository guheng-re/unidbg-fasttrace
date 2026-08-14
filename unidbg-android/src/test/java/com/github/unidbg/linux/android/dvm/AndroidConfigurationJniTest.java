package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AndroidConfigurationJniTest {

    private static final String CONFIGURATION_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"configuration\":{"
            + "\"orientation\":2,"
            + "\"screenLayout\":34,"
            + "\"uiMode\":17,"
            + "\"fontScale\":1.25,"
            + "\"densityDpi\":420,"
            + "\"screenWidthDp\":360,"
            + "\"screenHeightDp\":640,"
            + "\"smallestScreenWidthDp\":360,"
            + "\"keyboard\":2,"
            + "\"navigation\":2,"
            + "\"keyboardHidden\":1,"
            + "\"hardKeyboardHidden\":2,"
            + "\"navigationHidden\":1"
            + "}"
            + "}"
            + "}";

    private static final String CONFIGURATION_DEFAULT_DENSITY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"configuration\":{"
            + "\"orientation\":1"
            + "}"
            + "}"
            + "}";

    private static final String CONFIGURATION_SCREEN_DP_ONLY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"configuration\":{"
            + "\"screenWidthDp\":411,"
            + "\"screenHeightDp\":731,"
            + "\"smallestScreenWidthDp\":411"
            + "}"
            + "}"
            + "}";

    private static final String CONFIGURATION_KEYBOARD_NAV_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"configuration\":{"
            + "\"keyboard\":1,"
            + "\"navigation\":4"
            + "}"
            + "}"
            + "}";

    private static final String CONFIGURATION_HIDDEN_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"configuration\":{"
            + "\"keyboardHidden\":2,"
            + "\"hardKeyboardHidden\":1,"
            + "\"navigationHidden\":2"
            + "}"
            + "}"
            + "}";

    private static final String CONFIGURATION_CROSS_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"configuration\":{"
            + "\"orientation\":2,"
            + "\"fontScale\":1.25,"
            + "\"densityDpi\":420"
            + "},"
            + "\"locale\":{"
            + "\"languageTag\":\"zh-Hans-CN\""
            + "}"
            + "}"
            + "}";

    private static final String CONFIGURATION_CROSS_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"configuration\":{"
            + "\"orientation\":3,"
            + "\"fontScale\":2.0,"
            + "\"densityDpi\":160"
            + "},"
            + "\"locale\":{"
            + "\"languageTag\":\"en-US\""
            + "}"
            + "}"
            + "}";

    private static final String GET_CONFIGURATION =
            "()Landroid/content/res/Configuration;";
    private static final String LOCALE_FIELD =
            "android/content/res/Configuration->locale:Ljava/util/Locale;";

    @Test
    public void testConfigurationVarArg32() throws Exception {
        runConfiguredConfiguration(false, false);
    }

    @Test
    public void testConfigurationVaList64() throws Exception {
        runConfiguredConfiguration(true, true);
    }

    @Test
    public void testConfigurationDensityDefaultVarArg32() throws Exception {
        runDensityDefault(false, false);
    }

    @Test
    public void testConfigurationDensityDefaultVaList64() throws Exception {
        runDensityDefault(true, true);
    }

    @Test
    public void testConfigurationScreenDpVarArg32() throws Exception {
        runScreenDpFields(false, false);
    }

    @Test
    public void testConfigurationScreenDpVaList64() throws Exception {
        runScreenDpFields(true, true);
    }

    @Test
    public void testConfigurationScreenDpDefaultVarArg32() throws Exception {
        runScreenDpDefaults(false, false);
    }

    @Test
    public void testConfigurationScreenDpDefaultVaList64() throws Exception {
        runScreenDpDefaults(true, true);
    }

    @Test
    public void testConfigurationKeyboardNavigationVarArg32() throws Exception {
        runKeyboardNavigation(false, false);
    }

    @Test
    public void testConfigurationKeyboardNavigationVaList64() throws Exception {
        runKeyboardNavigation(true, true);
    }

    @Test
    public void testConfigurationKeyboardNavigationDefaultVarArg32() throws Exception {
        runKeyboardNavigationDefaults(false, false);
    }

    @Test
    public void testConfigurationKeyboardNavigationDefaultVaList64() throws Exception {
        runKeyboardNavigationDefaults(true, true);
    }

    @Test
    public void testConfigurationHiddenInputVarArg32() throws Exception {
        runHiddenInputFields(false, false);
    }

    @Test
    public void testConfigurationHiddenInputVaList64() throws Exception {
        runHiddenInputFields(true, true);
    }

    @Test
    public void testConfigurationHiddenInputDefaultVarArg32() throws Exception {
        runHiddenInputDefaults(false, false);
    }

    @Test
    public void testConfigurationHiddenInputDefaultVaList64() throws Exception {
        runHiddenInputDefaults(true, true);
    }

    @Test
    public void testConfigurationAbsentVarArg32() throws Exception {
        runAbsentConfiguration(false, false);
    }

    @Test
    public void testConfigurationAbsentVaList64() throws Exception {
        runAbsentConfiguration(true, true);
    }

    @Test
    public void testConfigurationCrossVmVarArg32() throws Exception {
        runCrossVmRejected(false, false);
    }

    @Test
    public void testConfigurationCrossVmVaList64() throws Exception {
        runCrossVmRejected(true, true);
    }

    private static void runConfiguredConfiguration(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURATION_JSON);
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

            DvmObject<?> resources = vm.resolveClass("android/content/res/Resources").newObject(null);
            DvmObject<?> configuration = invokeGetConfiguration(jni, baseVM, useVaList, resources);
            assertNotNull(configuration);
            assertNotNull(configuration.getValue());
            assertTrue(configuration.getValue().getClass().getName().contains("ConfiguredConfiguration"));

            assertEquals(2, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->orientation:I"));
            assertEquals(34, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->screenLayout:I"));
            assertEquals(17, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->uiMode:I"));
            assertEquals(420, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->densityDpi:I"));
            assertEquals(360, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->screenWidthDp:I"));
            assertEquals(640, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->screenHeightDp:I"));
            assertEquals(360, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->smallestScreenWidthDp:I"));
            assertEquals(2, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->keyboard:I"));
            assertEquals(2, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->navigation:I"));
            assertEquals(1, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->keyboardHidden:I"));
            assertEquals(2, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->hardKeyboardHidden:I"));
            assertEquals(1, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->navigationHidden:I"));
            assertEquals(1.25f, jni.getFloatField(baseVM, configuration,
                    "android/content/res/Configuration->fontScale:F"), 0f);

            // unknown field on marker
            try {
                jni.getFloatField(baseVM, configuration,
                        "android/content/res/Configuration->fontWeightAdjustment:F");
                fail("expected UnsupportedOperationException for unsupported float field");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("fontWeightAdjustment"));
            }

            // provenance isolation: null-value / unrelated Configuration must not hit config path
            DvmObject<?> unrelated = vm.resolveClass("android/content/res/Configuration").newObject(null);
            assertNull(unrelated.getValue());
            try {
                jni.getIntField(baseVM, unrelated,
                        "android/content/res/Configuration->orientation:I");
                fail("expected UnsupportedOperationException for orientation on unrelated Configuration");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("orientation"));
            }
            try {
                jni.getIntField(baseVM, unrelated,
                        "android/content/res/Configuration->densityDpi:I");
                fail("expected UnsupportedOperationException for densityDpi on unrelated Configuration");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("densityDpi"));
            }
            try {
                jni.getIntField(baseVM, unrelated,
                        "android/content/res/Configuration->screenWidthDp:I");
                fail("expected UnsupportedOperationException for screenWidthDp on unrelated Configuration");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("screenWidthDp"));
            }
            try {
                jni.getIntField(baseVM, unrelated,
                        "android/content/res/Configuration->keyboard:I");
                fail("expected UnsupportedOperationException for keyboard on unrelated Configuration");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("keyboard"));
            }
            try {
                jni.getIntField(baseVM, unrelated,
                        "android/content/res/Configuration->navigation:I");
                fail("expected UnsupportedOperationException for navigation on unrelated Configuration");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("navigation"));
            }
            try {
                jni.getIntField(baseVM, unrelated,
                        "android/content/res/Configuration->keyboardHidden:I");
                fail("expected UOE for keyboardHidden on unrelated Configuration");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("keyboardHidden"));
            }
            try {
                jni.getFloatField(baseVM, unrelated,
                        "android/content/res/Configuration->fontScale:F");
                fail("expected UnsupportedOperationException for fontScale on unrelated Configuration");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("fontScale"));
            }

            // sidecar
            CapturedEvent getCfg = findLastEvent(sink.events, "android_configuration",
                    "Resources.getConfiguration");
            assertNotNull(getCfg);
            assertEquals("json-config", getCfg.source);
            assertTrue(String.valueOf(getCfg.value).contains("orientation=2"));
            assertTrue(String.valueOf(getCfg.value).contains("screenLayout=34"));
            assertTrue(String.valueOf(getCfg.value).contains("uiMode=17"));
            assertTrue(String.valueOf(getCfg.value).contains("fontScale=1.25"));
            assertTrue(String.valueOf(getCfg.value).contains("densityDpi=420"));
            assertTrue(String.valueOf(getCfg.value).contains("screenWidthDp=360"));
            assertTrue(String.valueOf(getCfg.value).contains("screenHeightDp=640"));
            assertTrue(String.valueOf(getCfg.value).contains("smallestScreenWidthDp=360"));
            assertTrue(String.valueOf(getCfg.value).contains("keyboard=2"));
            assertTrue(String.valueOf(getCfg.value).contains("navigation=2"));
            assertTrue(String.valueOf(getCfg.value).contains("keyboardHidden=1"));
            assertTrue(String.valueOf(getCfg.value).contains("hardKeyboardHidden=2"));
            assertTrue(String.valueOf(getCfg.value).contains("navigationHidden=1"));

            CapturedEvent orientationEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.orientation");
            assertNotNull(orientationEv);
            assertEquals("json-config", orientationEv.source);
            assertTrue(String.valueOf(orientationEv.value).contains("field=orientation"));
            assertTrue(String.valueOf(orientationEv.value).contains("result=2"));

            CapturedEvent densityEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.densityDpi");
            assertNotNull(densityEv);
            assertEquals("json-config", densityEv.source);
            assertEquals("field=densityDpi,result=420", String.valueOf(densityEv.value));
            assertNotNull(densityEv.note);
            assertTrue(densityEv.note.length() > 0);

            CapturedEvent widthEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.screenWidthDp");
            assertNotNull(widthEv);
            assertEquals("field=screenWidthDp,result=360", String.valueOf(widthEv.value));
            CapturedEvent heightEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.screenHeightDp");
            assertNotNull(heightEv);
            assertEquals("field=screenHeightDp,result=640", String.valueOf(heightEv.value));
            CapturedEvent smallestEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.smallestScreenWidthDp");
            assertNotNull(smallestEv);
            assertEquals("field=smallestScreenWidthDp,result=360", String.valueOf(smallestEv.value));

            CapturedEvent keyboardEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.keyboard");
            assertNotNull(keyboardEv);
            assertEquals("json-config", keyboardEv.source);
            assertEquals("field=keyboard,result=2", String.valueOf(keyboardEv.value));
            CapturedEvent navigationEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.navigation");
            assertNotNull(navigationEv);
            assertEquals("json-config", navigationEv.source);
            assertEquals("field=navigation,result=2", String.valueOf(navigationEv.value));

            CapturedEvent khEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.keyboardHidden");
            assertNotNull(khEv);
            assertEquals("json-config", khEv.source);
            assertEquals("field=keyboardHidden,result=1", String.valueOf(khEv.value));
            CapturedEvent hkhEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.hardKeyboardHidden");
            assertNotNull(hkhEv);
            assertEquals("field=hardKeyboardHidden,result=2", String.valueOf(hkhEv.value));
            CapturedEvent nhEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.navigationHidden");
            assertNotNull(nhEv);
            assertEquals("field=navigationHidden,result=1", String.valueOf(nhEv.value));

            CapturedEvent fontEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.fontScale");
            assertNotNull(fontEv);
            assertTrue(String.valueOf(fontEv.value).contains("result=1.25"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runDensityDefault(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURATION_DEFAULT_DENSITY_JSON);
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

            DvmObject<?> resources = vm.resolveClass("android/content/res/Resources").newObject(null);
            DvmObject<?> configuration = invokeGetConfiguration(jni, baseVM, useVaList, resources);
            assertNotNull(configuration);
            assertEquals(0, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->densityDpi:I"));

            CapturedEvent getCfg = findLastEvent(sink.events, "android_configuration",
                    "Resources.getConfiguration");
            assertNotNull(getCfg);
            assertTrue(String.valueOf(getCfg.value).contains("densityDpi=0"));

            CapturedEvent densityEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.densityDpi");
            assertNotNull(densityEv);
            assertEquals("field=densityDpi,result=0", String.valueOf(densityEv.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runScreenDpFields(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURATION_SCREEN_DP_ONLY_JSON);
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

            DvmObject<?> resources = vm.resolveClass("android/content/res/Resources").newObject(null);
            DvmObject<?> configuration = invokeGetConfiguration(jni, baseVM, useVaList, resources);
            assertNotNull(configuration);
            assertEquals(411, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->screenWidthDp:I"));
            assertEquals(731, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->screenHeightDp:I"));
            assertEquals(411, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->smallestScreenWidthDp:I"));
            // other fields keep defaults
            assertEquals(0, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->densityDpi:I"));
            assertEquals(1, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->orientation:I"));

            CapturedEvent widthEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.screenWidthDp");
            assertNotNull(widthEv);
            assertEquals("json-config", widthEv.source);
            assertEquals("field=screenWidthDp,result=411", String.valueOf(widthEv.value));
            CapturedEvent heightEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.screenHeightDp");
            assertNotNull(heightEv);
            assertEquals("field=screenHeightDp,result=731", String.valueOf(heightEv.value));
            CapturedEvent smallestEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.smallestScreenWidthDp");
            assertNotNull(smallestEv);
            assertEquals("field=smallestScreenWidthDp,result=411", String.valueOf(smallestEv.value));

            CapturedEvent getCfg = findLastEvent(sink.events, "android_configuration",
                    "Resources.getConfiguration");
            assertNotNull(getCfg);
            assertTrue(String.valueOf(getCfg.value).contains("screenWidthDp=411"));
            assertTrue(String.valueOf(getCfg.value).contains("screenHeightDp=731"));
            assertTrue(String.valueOf(getCfg.value).contains("smallestScreenWidthDp=411"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runScreenDpDefaults(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURATION_DEFAULT_DENSITY_JSON);
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

            DvmObject<?> resources = vm.resolveClass("android/content/res/Resources").newObject(null);
            DvmObject<?> configuration = invokeGetConfiguration(jni, baseVM, useVaList, resources);
            assertEquals(0, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->screenWidthDp:I"));
            assertEquals(0, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->screenHeightDp:I"));
            assertEquals(0, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->smallestScreenWidthDp:I"));

            CapturedEvent getCfg = findLastEvent(sink.events, "android_configuration",
                    "Resources.getConfiguration");
            assertNotNull(getCfg);
            assertTrue(String.valueOf(getCfg.value).contains("screenWidthDp=0"));
            assertTrue(String.valueOf(getCfg.value).contains("screenHeightDp=0"));
            assertTrue(String.valueOf(getCfg.value).contains("smallestScreenWidthDp=0"));

            CapturedEvent widthEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.screenWidthDp");
            assertNotNull(widthEv);
            assertEquals("field=screenWidthDp,result=0", String.valueOf(widthEv.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runKeyboardNavigation(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURATION_KEYBOARD_NAV_JSON);
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

            DvmObject<?> resources = vm.resolveClass("android/content/res/Resources").newObject(null);
            DvmObject<?> configuration = invokeGetConfiguration(jni, baseVM, useVaList, resources);
            assertNotNull(configuration);
            assertEquals(1, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->keyboard:I"));
            assertEquals(4, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->navigation:I"));
            // other fields keep defaults
            assertEquals(1, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->orientation:I"));
            assertEquals(0, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->densityDpi:I"));

            CapturedEvent keyboardEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.keyboard");
            assertNotNull(keyboardEv);
            assertEquals("json-config", keyboardEv.source);
            assertEquals("field=keyboard,result=1", String.valueOf(keyboardEv.value));
            assertNotNull(keyboardEv.note);
            assertTrue(keyboardEv.note.length() > 0);
            CapturedEvent navigationEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.navigation");
            assertNotNull(navigationEv);
            assertEquals("json-config", navigationEv.source);
            assertEquals("field=navigation,result=4", String.valueOf(navigationEv.value));

            CapturedEvent getCfg = findLastEvent(sink.events, "android_configuration",
                    "Resources.getConfiguration");
            assertNotNull(getCfg);
            assertTrue(String.valueOf(getCfg.value).contains("keyboard=1"));
            assertTrue(String.valueOf(getCfg.value).contains("navigation=4"));

            // plain Configuration: notHandled
            DvmObject<?> plain = vm.resolveClass("android/content/res/Configuration").newObject(null);
            try {
                jni.getIntField(baseVM, plain,
                        "android/content/res/Configuration->keyboard:I");
                fail("expected UOE for keyboard on plain Configuration");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("keyboard"));
            }
            try {
                jni.getIntField(baseVM, plain,
                        "android/content/res/Configuration->navigation:I");
                fail("expected UOE for navigation on plain Configuration");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("navigation"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runKeyboardNavigationDefaults(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURATION_DEFAULT_DENSITY_JSON);
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

            DvmObject<?> resources = vm.resolveClass("android/content/res/Resources").newObject(null);
            DvmObject<?> configuration = invokeGetConfiguration(jni, baseVM, useVaList, resources);
            assertEquals(0, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->keyboard:I"));
            assertEquals(0, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->navigation:I"));

            CapturedEvent getCfg = findLastEvent(sink.events, "android_configuration",
                    "Resources.getConfiguration");
            assertNotNull(getCfg);
            assertTrue(String.valueOf(getCfg.value).contains("keyboard=0"));
            assertTrue(String.valueOf(getCfg.value).contains("navigation=0"));

            CapturedEvent keyboardEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.keyboard");
            assertNotNull(keyboardEv);
            assertEquals("field=keyboard,result=0", String.valueOf(keyboardEv.value));
            CapturedEvent navigationEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.navigation");
            assertNotNull(navigationEv);
            assertEquals("field=navigation,result=0", String.valueOf(navigationEv.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runHiddenInputFields(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURATION_HIDDEN_JSON);
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

            DvmObject<?> resources = vm.resolveClass("android/content/res/Resources").newObject(null);
            DvmObject<?> configuration = invokeGetConfiguration(jni, baseVM, useVaList, resources);
            assertNotNull(configuration);
            assertEquals(2, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->keyboardHidden:I"));
            assertEquals(1, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->hardKeyboardHidden:I"));
            assertEquals(2, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->navigationHidden:I"));
            // no leak: keyboard/navigation stay defaults
            assertEquals(0, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->keyboard:I"));
            assertEquals(0, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->navigation:I"));

            CapturedEvent khEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.keyboardHidden");
            assertNotNull(khEv);
            assertEquals("json-config", khEv.source);
            assertEquals("field=keyboardHidden,result=2", String.valueOf(khEv.value));
            CapturedEvent hkhEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.hardKeyboardHidden");
            assertNotNull(hkhEv);
            assertEquals("field=hardKeyboardHidden,result=1", String.valueOf(hkhEv.value));
            CapturedEvent nhEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.navigationHidden");
            assertNotNull(nhEv);
            assertEquals("field=navigationHidden,result=2", String.valueOf(nhEv.value));

            CapturedEvent getCfg = findLastEvent(sink.events, "android_configuration",
                    "Resources.getConfiguration");
            assertNotNull(getCfg);
            assertTrue(String.valueOf(getCfg.value).contains("keyboardHidden=2"));
            assertTrue(String.valueOf(getCfg.value).contains("hardKeyboardHidden=1"));
            assertTrue(String.valueOf(getCfg.value).contains("navigationHidden=2"));

            DvmObject<?> plain = vm.resolveClass("android/content/res/Configuration").newObject(null);
            try {
                jni.getIntField(baseVM, plain,
                        "android/content/res/Configuration->keyboardHidden:I");
                fail("expected UOE for keyboardHidden on plain Configuration");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("keyboardHidden"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runHiddenInputDefaults(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURATION_DEFAULT_DENSITY_JSON);
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

            DvmObject<?> resources = vm.resolveClass("android/content/res/Resources").newObject(null);
            DvmObject<?> configuration = invokeGetConfiguration(jni, baseVM, useVaList, resources);
            assertEquals(0, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->keyboardHidden:I"));
            assertEquals(0, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->hardKeyboardHidden:I"));
            assertEquals(0, jni.getIntField(baseVM, configuration,
                    "android/content/res/Configuration->navigationHidden:I"));

            CapturedEvent getCfg = findLastEvent(sink.events, "android_configuration",
                    "Resources.getConfiguration");
            assertNotNull(getCfg);
            assertTrue(String.valueOf(getCfg.value).contains("keyboardHidden=0"));
            assertTrue(String.valueOf(getCfg.value).contains("hardKeyboardHidden=0"));
            assertTrue(String.valueOf(getCfg.value).contains("navigationHidden=0"));

            CapturedEvent khEv = findLastEvent(sink.events, "android_configuration",
                    "Configuration.keyboardHidden");
            assertNotNull(khEv);
            assertEquals("field=keyboardHidden,result=0", String.valueOf(khEv.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentConfiguration(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
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

            DvmObject<?> resources = vm.resolveClass("android/content/res/Resources").newObject(null);
            if (useVaList) {
                DvmObject<?> configuration = invokeGetConfiguration(jni, baseVM, true, resources);
                assertNotNull(configuration);
                assertNull(configuration.getValue());
                try {
                    jni.getIntField(baseVM, configuration,
                            "android/content/res/Configuration->orientation:I");
                    fail("expected UnsupportedOperationException for orientation without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("orientation"));
                }
                try {
                    jni.getIntField(baseVM, configuration,
                            "android/content/res/Configuration->densityDpi:I");
                    fail("expected UnsupportedOperationException for densityDpi without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("densityDpi"));
                }
                try {
                    jni.getIntField(baseVM, configuration,
                            "android/content/res/Configuration->screenWidthDp:I");
                    fail("expected UnsupportedOperationException for screenWidthDp without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("screenWidthDp"));
                }
                try {
                    jni.getIntField(baseVM, configuration,
                            "android/content/res/Configuration->screenHeightDp:I");
                    fail("expected UnsupportedOperationException for screenHeightDp without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("screenHeightDp"));
                }
                try {
                    jni.getIntField(baseVM, configuration,
                            "android/content/res/Configuration->smallestScreenWidthDp:I");
                    fail("expected UnsupportedOperationException for smallestScreenWidthDp without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("smallestScreenWidthDp"));
                }
                try {
                    jni.getIntField(baseVM, configuration,
                            "android/content/res/Configuration->keyboard:I");
                    fail("expected UnsupportedOperationException for keyboard without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("keyboard"));
                }
                try {
                    jni.getIntField(baseVM, configuration,
                            "android/content/res/Configuration->navigation:I");
                    fail("expected UnsupportedOperationException for navigation without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("navigation"));
                }
                try {
                    jni.getIntField(baseVM, configuration,
                            "android/content/res/Configuration->keyboardHidden:I");
                    fail("expected UOE for keyboardHidden without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("keyboardHidden"));
                }
                try {
                    jni.getFloatField(baseVM, configuration,
                            "android/content/res/Configuration->fontScale:F");
                    fail("expected UnsupportedOperationException for fontScale without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("fontScale"));
                }
            } else {
                try {
                    invokeGetConfiguration(jni, baseVM, false, resources);
                    fail("expected UnsupportedOperationException for VarArg getConfiguration without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getConfiguration"));
                }
            }
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runCrossVmRejected(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(CONFIGURATION_CROSS_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(CONFIGURATION_CROSS_B_JSON);
        AndroidEmulator emuA = null;
        AndroidEmulator emuB = null;
        CapturingSink sinkB = new CapturingSink();
        try {
            emuA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            emuB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configB)
                    .build();
            TraceEnvironmentEventSink.register(emuB, sinkB);

            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            VM vmA = emuA.createDalvikVM();
            VM vmB = emuB.createDalvikVM();
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> resourcesA = vmA.resolveClass("android/content/res/Resources").newObject(null);
            DvmObject<?> configurationA = invokeGetConfiguration(jniA, baseA, useVaList, resourcesA);
            assertNotNull(configurationA);
            assertTrue(configurationA.getValue().getClass().getName().contains("ConfiguredConfiguration"));

            try {
                jniB.getIntField(baseB, configurationA,
                        "android/content/res/Configuration->orientation:I");
                fail("expected UOE for cross-VM Configuration.orientation");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("orientation"));
            }
            try {
                jniB.getFloatField(baseB, configurationA,
                        "android/content/res/Configuration->fontScale:F");
                fail("expected UOE for cross-VM Configuration.fontScale");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("fontScale"));
            }
            try {
                jniB.getObjectField(baseB, configurationA, LOCALE_FIELD);
                fail("expected UOE for cross-VM Configuration.locale");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("locale"));
            }
            for (CapturedEvent e : sinkB.events) {
                assertFalse("leak to VM B during reject: " + e.api,
                        "android_configuration".equals(e.kind)
                                || "android_locale".equals(e.kind));
            }

            DvmObject<?> resourcesB = vmB.resolveClass("android/content/res/Resources").newObject(null);
            DvmObject<?> configurationB = invokeGetConfiguration(jniB, baseB, useVaList, resourcesB);
            assertNotNull(configurationB);
            assertEquals(3, jniB.getIntField(baseB, configurationB,
                    "android/content/res/Configuration->orientation:I"));
            assertEquals(2.0f, jniB.getFloatField(baseB, configurationB,
                    "android/content/res/Configuration->fontScale:F"), 0f);
            DvmObject<?> localeB = jniB.getObjectField(baseB, configurationB, LOCALE_FIELD);
            assertNotNull(localeB);
            assertTrue(localeB.getValue().getClass().getName().contains("ConfiguredLocale"));

            for (CapturedEvent e : sinkB.events) {
                String value = String.valueOf(e.value);
                assertFalse("VM B sidecar must not contain VM A configuration: " + value,
                        value.contains("zh-Hans-CN")
                                || value.contains("orientation=2")
                                || value.contains("fontScale=1.25")
                                || value.contains("densityDpi=420")
                                || value.contains("field=orientation,result=2")
                                || value.contains("result=1.25")
                                || value.contains("result=420"));
            }
            CapturedEvent orientationB = findLastEvent(sinkB.events, "android_configuration",
                    "Configuration.orientation");
            assertNotNull(orientationB);
            assertEquals("field=orientation,result=3", String.valueOf(orientationB.value));
            CapturedEvent fontB = findLastEvent(sinkB.events, "android_configuration",
                    "Configuration.fontScale");
            assertNotNull(fontB);
            assertTrue(String.valueOf(fontB.value).contains("result=2.0"));
            CapturedEvent localeFieldB = findLastEvent(sinkB.events, "android_configuration",
                    "Configuration.locale");
            assertNotNull(localeFieldB);
            assertEquals("languageTag=en-US", String.valueOf(localeFieldB.value));
        } finally {
            if (emuA != null) {
                emuA.close();
            }
            if (emuB != null) {
                TraceEnvironmentEventSink.unregister(emuB, sinkB);
                emuB.close();
            }
        }
    }

    private static DvmObject<?> invokeGetConfiguration(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> resources) {
        DvmClass dvmClass = vm.resolveClass("android/content/res/Resources");
        DvmMethod method = new DvmMethod(dvmClass, "getConfiguration", GET_CONFIGURATION, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, resources, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, resources, signature, new TestVarArg(vm, method));
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

    private static final class TestVarArg extends VarArg {
        TestVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
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
