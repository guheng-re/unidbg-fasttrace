package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.dvm.array.ArrayObject;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 覆盖已实现的 {@code filesystem.externalStorage} 对静态
 * {@code android.os.Environment} API 的 JNI 接线：无参目录 / 状态 / 是否模拟 / 是否可移除 /
 * {@code getExternalStoragePublicDirectory(String)}，
 * 实例 {@code Context}/{@code ContextWrapper}/{@code Application}
 * {@code getExternalFilesDir(String)} / {@code getExternalFilesDirs(String)} /
 * {@code getExternalCacheDir()} / {@code getExternalCacheDirs()} /
 * {@code getObbDir()} / {@code getObbDirs()} /
 * {@code getExternalMediaDirs()}，
 * 以及 File 参数重载（仅匹配主目录）与节点缺失 / 无包名 / 错配 / null 时的 UOE/无事件行为。
 */
public class AndroidExternalStorageJniTest {

    private static final String ENVIRONMENT_CLASS = "android/os/Environment";
    private static final String FILE_CLASS = "java/io/File";
    private static final String PACKAGE_NAME = "com.demo.app";

    private static final String[] RECEIVER_CLASSES = {
            "android/content/Context",
            "android/content/ContextWrapper",
            "android/app/Application"
    };

    private static final String EXPECTED_DIRECTORY = "/storage/0123-4567";
    private static final String EXPECTED_STATE = "mounted_ro";
    private static final String EXPECTED_EXTERNAL_FILES_BASE =
            EXPECTED_DIRECTORY + "/Android/data/" + PACKAGE_NAME + "/files";
    private static final String EXPECTED_EXTERNAL_CACHE =
            EXPECTED_DIRECTORY + "/Android/data/" + PACKAGE_NAME + "/cache";
    private static final String EXPECTED_OBB_DIR =
            EXPECTED_DIRECTORY + "/Android/obb/" + PACKAGE_NAME;
    private static final String EXPECTED_MEDIA_DIR =
            EXPECTED_DIRECTORY + "/Android/media/" + PACKAGE_NAME;

    /** 显式配置四字段（非默认值，便于断言路径与布尔差异） */
    private static final String CONFIGURED_JSON = "{"
            + "\"filesystem\":{"
            + "\"externalStorage\":{"
            + "\"directory\":\"/storage/0123-4567\","
            + "\"state\":\"mounted_ro\","
            + "\"emulated\":false,"
            + "\"removable\":true"
            + "}"
            + "}"
            + "}";

    /** externalStorage + packageName（外部 files/cache 需要包名） */
    private static final String CONFIGURED_WITH_PACKAGE_JSON = "{"
            + "\"android\":{\"packageName\":\"" + PACKAGE_NAME + "\"},"
            + "\"filesystem\":{"
            + "\"externalStorage\":{"
            + "\"directory\":\"/storage/0123-4567\","
            + "\"state\":\"mounted_ro\","
            + "\"emulated\":false,"
            + "\"removable\":true"
            + "}"
            + "}"
            + "}";

    /** 有 externalStorage 但无 packageName */
    private static final String CONFIGURED_NO_PACKAGE_JSON = CONFIGURED_JSON;

    /** 无 externalStorage 节点 */
    private static final String ABSENT_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testExternalStorageConfiguredVarArg32() throws Exception {
        runConfigured(false, false);
    }

    @Test
    public void testExternalStorageConfiguredVaList64() throws Exception {
        runConfigured(true, true);
    }

    @Test
    public void testExternalStorageAbsentVarArg32() throws Exception {
        runAbsent(false, false);
    }

    @Test
    public void testExternalStorageAbsentVaList64() throws Exception {
        runAbsent(true, true);
    }

    @Test
    public void testExternalStoragePublicDirectoryVarArg32() throws Exception {
        runPublicDirectory(false, false);
    }

    @Test
    public void testExternalStoragePublicDirectoryVaList64() throws Exception {
        runPublicDirectory(true, true);
    }

    @Test
    public void testExternalFilesDirVarArg32() throws Exception {
        runExternalFilesDir(false, false);
    }

    @Test
    public void testExternalFilesDirVaList64() throws Exception {
        runExternalFilesDir(true, true);
    }

    @Test
    public void testExternalFilesDirAbsentNoPackageVarArg32() throws Exception {
        runExternalFilesDirAbsentAndNoPackage(false, false);
    }

    @Test
    public void testExternalFilesDirAbsentNoPackageVaList64() throws Exception {
        runExternalFilesDirAbsentAndNoPackage(true, true);
    }

    @Test
    public void testExternalMediaDirsVarArg32() throws Exception {
        runExternalMediaDirs(false, false);
    }

    @Test
    public void testExternalMediaDirsVaList64() throws Exception {
        runExternalMediaDirs(true, true);
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
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

            // 无参 getExternalStorageDirectory → java.io.File(directory)
            DvmObject<?> directoryObj = invokeGetExternalStorageDirectory(jni, baseVM, useVaList);
            assertNotNull(directoryObj);
            assertEquals(FILE_CLASS, directoryObj.getObjectType().getClassName());
            assertTrue(directoryObj.getValue() instanceof File);
            // 与生产 new File(directory) 同一构造；避免 Windows 路径分隔符导致字符串不相等
            assertEquals(new File(EXPECTED_DIRECTORY), directoryObj.getValue());

            // 无参 getExternalStorageState → StringObject(state)
            DvmObject<?> stateObj = invokeGetExternalStorageState(jni, baseVM, useVaList);
            assertNotNull(stateObj);
            assertTrue(stateObj instanceof StringObject);
            assertEquals(EXPECTED_STATE, ((StringObject) stateObj).getValue());

            // 无参布尔
            assertFalse(invokeIsExternalStorageEmulated(jni, baseVM, useVaList));
            assertTrue(invokeIsExternalStorageRemovable(jni, baseVM, useVaList));

            CapturedEvent directoryEv = findLastEvent(sink.events, "filesystem_external_storage",
                    "Environment.getExternalStorageDirectory");
            assertNotNull(directoryEv);
            assertEquals("json-config", directoryEv.source);
            assertEquals("field=directory,result=" + EXPECTED_DIRECTORY,
                    String.valueOf(directoryEv.value));
            assertNotNull(directoryEv.note);
            assertFalse(directoryEv.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "filesystem_external_storage",
                    "Environment.getExternalStorageDirectory"));

            CapturedEvent stateEv = findLastEvent(sink.events, "filesystem_external_storage",
                    "Environment.getExternalStorageState");
            assertNotNull(stateEv);
            assertEquals("json-config", stateEv.source);
            assertEquals("field=state,result=" + EXPECTED_STATE, String.valueOf(stateEv.value));
            assertEquals(1, countEvents(sink.events, "filesystem_external_storage",
                    "Environment.getExternalStorageState"));

            CapturedEvent emulatedEv = findLastEvent(sink.events, "filesystem_external_storage",
                    "Environment.isExternalStorageEmulated");
            assertNotNull(emulatedEv);
            assertEquals("json-config", emulatedEv.source);
            assertEquals("field=emulated,result=false", String.valueOf(emulatedEv.value));
            assertEquals(1, countEvents(sink.events, "filesystem_external_storage",
                    "Environment.isExternalStorageEmulated"));

            CapturedEvent removableEv = findLastEvent(sink.events, "filesystem_external_storage",
                    "Environment.isExternalStorageRemovable");
            assertNotNull(removableEv);
            assertEquals("json-config", removableEv.source);
            assertEquals("field=removable,result=true", String.valueOf(removableEv.value));
            assertEquals(1, countEvents(sink.events, "filesystem_external_storage",
                    "Environment.isExternalStorageRemovable"));

            // File 参数重载：仅匹配配置主目录 new File(directory)
            DvmObject<?> primaryFile = vm.resolveClass(FILE_CLASS)
                    .newObject(new File(EXPECTED_DIRECTORY));

            DvmObject<?> stateWithFile = invokeGetExternalStorageStateWithFile(
                    jni, baseVM, useVaList, primaryFile);
            assertNotNull(stateWithFile);
            assertTrue(stateWithFile instanceof StringObject);
            assertEquals(EXPECTED_STATE, ((StringObject) stateWithFile).getValue());
            CapturedEvent stateFileEv = findLastEvent(sink.events, "filesystem_external_storage",
                    "Environment.getExternalStorageState");
            assertNotNull(stateFileEv);
            assertEquals("json-config", stateFileEv.source);
            assertEquals("field=state,path=" + EXPECTED_DIRECTORY + ",result=" + EXPECTED_STATE,
                    String.valueOf(stateFileEv.value));
            assertEquals(2, countEvents(sink.events, "filesystem_external_storage",
                    "Environment.getExternalStorageState"));

            assertFalse(invokeIsExternalStorageEmulatedWithFile(jni, baseVM, useVaList, primaryFile));
            CapturedEvent emulatedFileEv = findLastEvent(sink.events, "filesystem_external_storage",
                    "Environment.isExternalStorageEmulated");
            assertNotNull(emulatedFileEv);
            assertEquals("json-config", emulatedFileEv.source);
            assertEquals("field=emulated,path=" + EXPECTED_DIRECTORY + ",result=false",
                    String.valueOf(emulatedFileEv.value));
            assertEquals(2, countEvents(sink.events, "filesystem_external_storage",
                    "Environment.isExternalStorageEmulated"));

            assertTrue(invokeIsExternalStorageRemovableWithFile(jni, baseVM, useVaList, primaryFile));
            CapturedEvent removableFileEv = findLastEvent(sink.events, "filesystem_external_storage",
                    "Environment.isExternalStorageRemovable");
            assertNotNull(removableFileEv);
            assertEquals("json-config", removableFileEv.source);
            assertEquals("field=removable,path=" + EXPECTED_DIRECTORY + ",result=true",
                    String.valueOf(removableFileEv.value));
            assertEquals(2, countEvents(sink.events, "filesystem_external_storage",
                    "Environment.isExternalStorageRemovable"));

            // 非主目录 File / null → UOE 且不发额外 sidecar
            int eventsBeforeReject = sink.events.size();
            DvmObject<?> otherFile = vm.resolveClass(FILE_CLASS)
                    .newObject(new File("/storage/other"));
            try {
                invokeGetExternalStorageStateWithFile(jni, baseVM, useVaList, otherFile);
                fail("expected UnsupportedOperationException for non-primary getExternalStorageState(File)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getExternalStorageState"));
            }
            try {
                invokeIsExternalStorageEmulatedWithFile(jni, baseVM, useVaList, otherFile);
                fail("expected UnsupportedOperationException for non-primary isExternalStorageEmulated(File)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isExternalStorageEmulated"));
            }
            try {
                invokeIsExternalStorageRemovableWithFile(jni, baseVM, useVaList, otherFile);
                fail("expected UnsupportedOperationException for non-primary isExternalStorageRemovable(File)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isExternalStorageRemovable"));
            }
            try {
                invokeGetExternalStorageStateWithFile(jni, baseVM, useVaList, null);
                fail("expected UnsupportedOperationException for null getExternalStorageState(File)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getExternalStorageState"));
            }
            try {
                invokeIsExternalStorageEmulatedWithFile(jni, baseVM, useVaList, null);
                fail("expected UnsupportedOperationException for null isExternalStorageEmulated(File)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isExternalStorageEmulated"));
            }
            try {
                invokeIsExternalStorageRemovableWithFile(jni, baseVM, useVaList, null);
                fail("expected UnsupportedOperationException for null isExternalStorageRemovable(File)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isExternalStorageRemovable"));
            }
            assertEquals(eventsBeforeReject, sink.events.size());
            assertEquals(2, countEvents(sink.events, "filesystem_external_storage",
                    "Environment.getExternalStorageState"));
            assertEquals(2, countEvents(sink.events, "filesystem_external_storage",
                    "Environment.isExternalStorageEmulated"));
            assertEquals(2, countEvents(sink.events, "filesystem_external_storage",
                    "Environment.isExternalStorageRemovable"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runPublicDirectory(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
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

            DvmObject<?> publicDir = invokeGetExternalStoragePublicDirectory(
                    jni, baseVM, useVaList, "DCIM");
            assertNotNull(publicDir);
            assertTrue(publicDir.getValue() instanceof File);
            assertEquals(new File(EXPECTED_DIRECTORY, "DCIM"), publicDir.getValue());

            CapturedEvent ev = findLastEvent(sink.events, "filesystem_external_storage",
                    "Environment.getExternalStoragePublicDirectory");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("type=DCIM,result=" + EXPECTED_DIRECTORY + "/DCIM",
                    String.valueOf(ev.value));
            assertEquals(1, countEvents(sink.events, "filesystem_external_storage",
                    "Environment.getExternalStoragePublicDirectory"));

            // invalid types → UOE, no extra event
            int eventsBefore = sink.events.size();
            String[] invalid = new String[]{"", "a/b", "a\\b", "a\nb", "a\rb", "a\0b"};
            for (String bad : invalid) {
                try {
                    invokeGetExternalStoragePublicDirectory(jni, baseVM, useVaList, bad);
                    fail("expected UOE for invalid public directory type: " + bad);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getExternalStoragePublicDirectory"));
                }
            }
            try {
                invokeGetExternalStoragePublicDirectoryNullType(jni, baseVM, useVaList);
                fail("expected UOE for null public directory type");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getExternalStoragePublicDirectory"));
            }
            assertEquals(eventsBefore, sink.events.size());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runExternalFilesDir(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_WITH_PACKAGE_JSON);
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
            assertEquals(PACKAGE_NAME, baseVM.getPackageName());

            File expectedBase = new File(new File(new File(new File(EXPECTED_DIRECTORY, "Android"),
                    "data"), PACKAGE_NAME), "files");
            File expectedTyped = new File(expectedBase, "Documents");
            File expectedCache = new File(new File(new File(new File(EXPECTED_DIRECTORY, "Android"),
                    "data"), PACKAGE_NAME), "cache");
            File expectedObb = new File(new File(new File(EXPECTED_DIRECTORY, "Android"), "obb"),
                    PACKAGE_NAME);

            for (String receiver : RECEIVER_CLASSES) {
                DvmObject<?> base = invokeGetExternalFilesDir(jni, baseVM, useVaList, receiver, null);
                assertNotNull(base);
                assertTrue(base.getValue() instanceof File);
                assertEquals(expectedBase, base.getValue());

                DvmObject<?> typed = invokeGetExternalFilesDir(jni, baseVM, useVaList, receiver,
                        "Documents");
                assertNotNull(typed);
                assertTrue(typed.getValue() instanceof File);
                assertEquals(expectedTyped, typed.getValue());

                ArrayObject filesDirsBase = invokeGetExternalFilesDirs(jni, baseVM, useVaList,
                        receiver, null);
                assertNotNull(filesDirsBase);
                assertEquals(1, filesDirsBase.length());
                assertNotNull(filesDirsBase.getValue()[0]);
                assertTrue(filesDirsBase.getValue()[0].getValue() instanceof File);
                assertEquals(expectedBase, filesDirsBase.getValue()[0].getValue());

                ArrayObject filesDirsTyped = invokeGetExternalFilesDirs(jni, baseVM, useVaList,
                        receiver, "Documents");
                assertNotNull(filesDirsTyped);
                assertEquals(1, filesDirsTyped.length());
                assertEquals(expectedTyped, filesDirsTyped.getValue()[0].getValue());

                DvmObject<?> cache = invokeGetExternalCacheDir(jni, baseVM, useVaList, receiver);
                assertNotNull(cache);
                assertTrue(cache.getValue() instanceof File);
                assertEquals(expectedCache, cache.getValue());

                ArrayObject cacheDirs = invokeGetExternalCacheDirs(jni, baseVM, useVaList, receiver);
                assertNotNull(cacheDirs);
                assertEquals(1, cacheDirs.length());
                assertNotNull(cacheDirs.getValue()[0]);
                assertTrue(cacheDirs.getValue()[0].getValue() instanceof File);
                assertEquals(expectedCache, cacheDirs.getValue()[0].getValue());

                DvmObject<?> obb = invokeGetObbDir(jni, baseVM, useVaList, receiver);
                assertNotNull(obb);
                assertTrue(obb.getValue() instanceof File);
                assertEquals(expectedObb, obb.getValue());

                ArrayObject obbDirs = invokeGetObbDirs(jni, baseVM, useVaList, receiver);
                assertNotNull(obbDirs);
                assertEquals(1, obbDirs.length());
                assertNotNull(obbDirs.getValue()[0]);
                assertTrue(obbDirs.getValue()[0].getValue() instanceof File);
                assertEquals(expectedObb, obbDirs.getValue()[0].getValue());
            }

            CapturedEvent baseEv = findLastEvent(sink.events, "filesystem_external_storage",
                    "Context.getExternalFilesDir");
            assertNotNull(baseEv);
            assertEquals("json-config", baseEv.source);
            // last singular files success is typed Documents
            assertEquals("type=Documents,result=" + EXPECTED_EXTERNAL_FILES_BASE + "/Documents",
                    String.valueOf(baseEv.value));
            // 3 receivers × (null type + typed)
            assertEquals(6, countEvents(sink.events, "filesystem_external_storage",
                    "Context.getExternalFilesDir"));

            CapturedEvent filesDirsEv = findLastEvent(sink.events, "filesystem_external_storage",
                    "Context.getExternalFilesDirs");
            assertNotNull(filesDirsEv);
            assertEquals("json-config", filesDirsEv.source);
            assertEquals("count=1,type=Documents,result=" + EXPECTED_EXTERNAL_FILES_BASE
                            + "/Documents",
                    String.valueOf(filesDirsEv.value));
            // 3 receivers × (null type + typed)
            assertEquals(6, countEvents(sink.events, "filesystem_external_storage",
                    "Context.getExternalFilesDirs"));
            boolean sawFilesDirsBase = false;
            for (CapturedEvent e : sink.events) {
                if ("filesystem_external_storage".equals(e.kind)
                        && "Context.getExternalFilesDirs".equals(e.api)
                        && ("count=1,result=" + EXPECTED_EXTERNAL_FILES_BASE)
                        .equals(String.valueOf(e.value))) {
                    sawFilesDirsBase = true;
                    break;
                }
            }
            assertTrue(sawFilesDirsBase);

            CapturedEvent cacheEv = findLastEvent(sink.events, "filesystem_external_storage",
                    "Context.getExternalCacheDir");
            assertNotNull(cacheEv);
            assertEquals("json-config", cacheEv.source);
            assertEquals("result=" + EXPECTED_EXTERNAL_CACHE, String.valueOf(cacheEv.value));
            assertEquals(3, countEvents(sink.events, "filesystem_external_storage",
                    "Context.getExternalCacheDir"));

            CapturedEvent cacheDirsEv = findLastEvent(sink.events, "filesystem_external_storage",
                    "Context.getExternalCacheDirs");
            assertNotNull(cacheDirsEv);
            assertEquals("json-config", cacheDirsEv.source);
            assertEquals("count=1,result=" + EXPECTED_EXTERNAL_CACHE,
                    String.valueOf(cacheDirsEv.value));
            assertEquals(3, countEvents(sink.events, "filesystem_external_storage",
                    "Context.getExternalCacheDirs"));

            CapturedEvent obbEv = findLastEvent(sink.events, "filesystem_external_storage",
                    "Context.getObbDir");
            assertNotNull(obbEv);
            assertEquals("json-config", obbEv.source);
            assertEquals("result=" + EXPECTED_OBB_DIR, String.valueOf(obbEv.value));
            assertEquals(3, countEvents(sink.events, "filesystem_external_storage",
                    "Context.getObbDir"));

            CapturedEvent obbDirsEv = findLastEvent(sink.events, "filesystem_external_storage",
                    "Context.getObbDirs");
            assertNotNull(obbDirsEv);
            assertEquals("json-config", obbDirsEv.source);
            assertEquals("count=1,result=" + EXPECTED_OBB_DIR, String.valueOf(obbDirsEv.value));
            assertEquals(3, countEvents(sink.events, "filesystem_external_storage",
                    "Context.getObbDirs"));

            // find a null-type event
            boolean sawBaseResult = false;
            for (CapturedEvent e : sink.events) {
                if ("filesystem_external_storage".equals(e.kind)
                        && "Context.getExternalFilesDir".equals(e.api)
                        && ("result=" + EXPECTED_EXTERNAL_FILES_BASE).equals(String.valueOf(e.value))) {
                    sawBaseResult = true;
                    break;
                }
            }
            assertTrue(sawBaseResult);

            int eventsBefore = sink.events.size();
            String[] invalid = new String[]{"", "a/b", "a\\b", "a\nb", "a\rb", "a\0b"};
            for (String bad : invalid) {
                try {
                    invokeGetExternalFilesDir(jni, baseVM, useVaList, RECEIVER_CLASSES[0], bad);
                    fail("expected UOE for invalid getExternalFilesDir type: " + bad);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getExternalFilesDir"));
                }
                try {
                    invokeGetExternalFilesDirs(jni, baseVM, useVaList, RECEIVER_CLASSES[0], bad);
                    fail("expected UOE for invalid getExternalFilesDirs type: " + bad);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getExternalFilesDirs"));
                }
            }
            assertEquals(eventsBefore, sink.events.size());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runExternalFilesDirAbsentAndNoPackage(boolean is64Bit, boolean useVaList)
            throws Exception {
        for (String json : new String[]{ABSENT_JSON, CONFIGURED_NO_PACKAGE_JSON}) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
            AndroidEmulator emulator = null;
            CapturingSink sink = new CapturingSink();
            try {
                emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                        : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                TraceEnvironmentEventSink.register(emulator, sink);
                VM vm = emulator.createDalvikVM();
                AbstractJni jni = new AbstractJni() {
                };
                vm.setJni(jni);
                BaseVM baseVM = (BaseVM) vm;

                for (String receiver : RECEIVER_CLASSES) {
                    try {
                        invokeGetExternalFilesDir(jni, baseVM, useVaList, receiver, null);
                        fail("expected UOE for getExternalFilesDir: " + json + " " + receiver);
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getExternalFilesDir"));
                    }
                    try {
                        invokeGetExternalFilesDir(jni, baseVM, useVaList, receiver, "Documents");
                        fail("expected UOE for getExternalFilesDir typed: " + json + " " + receiver);
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getExternalFilesDir"));
                    }
                    try {
                        invokeGetExternalFilesDirs(jni, baseVM, useVaList, receiver, null);
                        fail("expected UOE for getExternalFilesDirs: " + json + " " + receiver);
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getExternalFilesDirs"));
                    }
                    try {
                        invokeGetExternalCacheDir(jni, baseVM, useVaList, receiver);
                        fail("expected UOE for getExternalCacheDir: " + json + " " + receiver);
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getExternalCacheDir"));
                    }
                    try {
                        invokeGetExternalCacheDirs(jni, baseVM, useVaList, receiver);
                        fail("expected UOE for getExternalCacheDirs: " + json + " " + receiver);
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getExternalCacheDirs"));
                    }
                    try {
                        invokeGetObbDir(jni, baseVM, useVaList, receiver);
                        fail("expected UOE for getObbDir: " + json + " " + receiver);
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getObbDir"));
                    }
                    try {
                        invokeGetObbDirs(jni, baseVM, useVaList, receiver);
                        fail("expected UOE for getObbDirs: " + json + " " + receiver);
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getObbDirs"));
                    }
                }
                for (CapturedEvent e : sink.events) {
                    assertFalse(("Context.getExternalFilesDir".equals(e.api)
                            || "Context.getExternalFilesDirs".equals(e.api)
                            || "Context.getExternalCacheDir".equals(e.api)
                            || "Context.getExternalCacheDirs".equals(e.api)
                            || "Context.getObbDir".equals(e.api)
                            || "Context.getObbDirs".equals(e.api))
                            && "filesystem_external_storage".equals(e.kind));
                }
            } finally {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                    emulator.close();
                }
            }
        }
    }

    private static void runExternalMediaDirs(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_WITH_PACKAGE_JSON);
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
            assertEquals(PACKAGE_NAME, baseVM.getPackageName());

            File expectedMedia = new File(new File(new File(EXPECTED_DIRECTORY, "Android"), "media"),
                    PACKAGE_NAME);

            for (String receiver : RECEIVER_CLASSES) {
                ArrayObject mediaDirs = invokeGetExternalMediaDirs(jni, baseVM, useVaList, receiver);
                assertNotNull(mediaDirs);
                assertEquals(1, mediaDirs.length());
                assertNotNull(mediaDirs.getValue()[0]);
                assertTrue(mediaDirs.getValue()[0].getValue() instanceof File);
                assertEquals(expectedMedia, mediaDirs.getValue()[0].getValue());

                ArrayObject again = invokeGetExternalMediaDirs(jni, baseVM, useVaList, receiver);
                assertTrue(mediaDirs != again);
                assertEquals(expectedMedia, again.getValue()[0].getValue());
            }

            CapturedEvent mediaDirsEv = findLastEvent(sink.events, "filesystem_external_storage",
                    "Context.getExternalMediaDirs");
            assertNotNull(mediaDirsEv);
            assertEquals("json-config", mediaDirsEv.source);
            assertEquals("count=1,result=" + EXPECTED_MEDIA_DIR, String.valueOf(mediaDirsEv.value));
            assertNotNull(mediaDirsEv.note);
            assertFalse(mediaDirsEv.note.isEmpty());
            // 3 receivers × 2 calls (fresh array)
            assertEquals(6, countEvents(sink.events, "filesystem_external_storage",
                    "Context.getExternalMediaDirs"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        for (String json : new String[]{ABSENT_JSON, CONFIGURED_NO_PACKAGE_JSON}) {
            TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse(json);
            AndroidEmulator missingEmulator = null;
            CapturingSink missingSink = new CapturingSink();
            try {
                missingEmulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                        : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(missing)
                        .build();
                TraceEnvironmentEventSink.register(missingEmulator, missingSink);
                VM vm = missingEmulator.createDalvikVM();
                AbstractJni jni = new AbstractJni() {
                };
                vm.setJni(jni);
                BaseVM baseVM = (BaseVM) vm;

                for (String receiver : RECEIVER_CLASSES) {
                    try {
                        invokeGetExternalMediaDirs(jni, baseVM, useVaList, receiver);
                        fail("expected UOE for getExternalMediaDirs: " + json + " " + receiver);
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getExternalMediaDirs"));
                    }
                }
                for (CapturedEvent e : missingSink.events) {
                    assertFalse("Context.getExternalMediaDirs".equals(e.api)
                            && "filesystem_external_storage".equals(e.kind));
                }
            } finally {
                if (missingEmulator != null) {
                    TraceEnvironmentEventSink.unregister(missingEmulator, missingSink);
                    missingEmulator.close();
                }
            }
        }
    }

    private static void runAbsent(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ABSENT_JSON);
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

            try {
                invokeGetExternalStorageDirectory(jni, baseVM, useVaList);
                fail("expected UnsupportedOperationException for getExternalStorageDirectory without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getExternalStorageDirectory"));
            }
            try {
                invokeGetExternalStorageState(jni, baseVM, useVaList);
                fail("expected UnsupportedOperationException for getExternalStorageState without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getExternalStorageState"));
            }
            try {
                invokeIsExternalStorageEmulated(jni, baseVM, useVaList);
                fail("expected UnsupportedOperationException for isExternalStorageEmulated without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isExternalStorageEmulated"));
            }
            try {
                invokeIsExternalStorageRemovable(jni, baseVM, useVaList);
                fail("expected UnsupportedOperationException for isExternalStorageRemovable without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isExternalStorageRemovable"));
            }

            // File 重载在节点缺失时同样 UOE（含主目录匹配路径）
            DvmObject<?> fileArg = vm.resolveClass(FILE_CLASS).newObject(new File(EXPECTED_DIRECTORY));
            try {
                invokeGetExternalStorageStateWithFile(jni, baseVM, useVaList, fileArg);
                fail("expected UnsupportedOperationException for getExternalStorageState(File) without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getExternalStorageState"));
            }
            try {
                invokeIsExternalStorageEmulatedWithFile(jni, baseVM, useVaList, fileArg);
                fail("expected UnsupportedOperationException for isExternalStorageEmulated(File) without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isExternalStorageEmulated"));
            }
            try {
                invokeIsExternalStorageRemovableWithFile(jni, baseVM, useVaList, fileArg);
                fail("expected UnsupportedOperationException for isExternalStorageRemovable(File) without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isExternalStorageRemovable"));
            }
            try {
                invokeGetExternalStoragePublicDirectory(jni, baseVM, useVaList, "DCIM");
                fail("expected UnsupportedOperationException for getExternalStoragePublicDirectory without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getExternalStoragePublicDirectory"));
            }
            try {
                invokeGetExternalFilesDir(jni, baseVM, useVaList, RECEIVER_CLASSES[0], null);
                fail("expected UnsupportedOperationException for getExternalFilesDir without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getExternalFilesDir"));
            }
            try {
                invokeGetExternalFilesDirs(jni, baseVM, useVaList, RECEIVER_CLASSES[0], null);
                fail("expected UnsupportedOperationException for getExternalFilesDirs without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getExternalFilesDirs"));
            }
            try {
                invokeGetExternalCacheDir(jni, baseVM, useVaList, RECEIVER_CLASSES[0]);
                fail("expected UnsupportedOperationException for getExternalCacheDir without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getExternalCacheDir"));
            }
            try {
                invokeGetExternalCacheDirs(jni, baseVM, useVaList, RECEIVER_CLASSES[0]);
                fail("expected UnsupportedOperationException for getExternalCacheDirs without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getExternalCacheDirs"));
            }
            try {
                invokeGetObbDir(jni, baseVM, useVaList, RECEIVER_CLASSES[0]);
                fail("expected UnsupportedOperationException for getObbDir without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getObbDir"));
            }
            try {
                invokeGetObbDirs(jni, baseVM, useVaList, RECEIVER_CLASSES[0]);
                fail("expected UnsupportedOperationException for getObbDirs without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getObbDirs"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected filesystem_external_storage event when config absent: " + e.api,
                        "filesystem_external_storage".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    // ---------- 调用辅助 ----------

    private static DvmObject<?> invokeGetExternalStorageDirectory(AbstractJni jni, BaseVM vm,
                                                                  boolean useVaList) {
        DvmClass dvmClass = vm.resolveClass(ENVIRONMENT_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getExternalStorageDirectory",
                "()Ljava/io/File;", true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, dvmClass, signature, new TestVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, dvmClass, signature, new TestVarArg(vm, method));
    }

    private static DvmObject<?> invokeGetExternalStorageState(AbstractJni jni, BaseVM vm,
                                                              boolean useVaList) {
        DvmClass dvmClass = vm.resolveClass(ENVIRONMENT_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getExternalStorageState",
                "()Ljava/lang/String;", true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, dvmClass, signature, new TestVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, dvmClass, signature, new TestVarArg(vm, method));
    }

    private static DvmObject<?> invokeGetExternalStorageStateWithFile(AbstractJni jni, BaseVM vm,
                                                                      boolean useVaList,
                                                                      DvmObject<?> fileArg) {
        int fileHash = fileArg == null ? 0 : vm.addLocalObject(fileArg);
        DvmClass dvmClass = vm.resolveClass(ENVIRONMENT_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getExternalStorageState",
                "(Ljava/io/File;)Ljava/lang/String;", true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, dvmClass, signature,
                    new TestVaList(vm, method, fileHash));
        }
        return jni.callStaticObjectMethod(vm, dvmClass, signature,
                new TestVarArg(vm, method, fileHash));
    }

    private static boolean invokeIsExternalStorageEmulated(AbstractJni jni, BaseVM vm,
                                                           boolean useVaList) {
        DvmClass dvmClass = vm.resolveClass(ENVIRONMENT_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isExternalStorageEmulated", "()Z", true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticBooleanMethodV(vm, dvmClass, signature, new TestVaList(vm, method));
        }
        return jni.callStaticBooleanMethod(vm, dvmClass, signature, new TestVarArg(vm, method));
    }

    private static boolean invokeIsExternalStorageRemovable(AbstractJni jni, BaseVM vm,
                                                            boolean useVaList) {
        DvmClass dvmClass = vm.resolveClass(ENVIRONMENT_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isExternalStorageRemovable", "()Z", true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticBooleanMethodV(vm, dvmClass, signature, new TestVaList(vm, method));
        }
        return jni.callStaticBooleanMethod(vm, dvmClass, signature, new TestVarArg(vm, method));
    }

    private static boolean invokeIsExternalStorageEmulatedWithFile(AbstractJni jni, BaseVM vm,
                                                                   boolean useVaList,
                                                                   DvmObject<?> fileArg) {
        int fileHash = fileArg == null ? 0 : vm.addLocalObject(fileArg);
        DvmClass dvmClass = vm.resolveClass(ENVIRONMENT_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isExternalStorageEmulated",
                "(Ljava/io/File;)Z", true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticBooleanMethodV(vm, dvmClass, signature,
                    new TestVaList(vm, method, fileHash));
        }
        return jni.callStaticBooleanMethod(vm, dvmClass, signature,
                new TestVarArg(vm, method, fileHash));
    }

    private static boolean invokeIsExternalStorageRemovableWithFile(AbstractJni jni, BaseVM vm,
                                                                    boolean useVaList,
                                                                    DvmObject<?> fileArg) {
        int fileHash = fileArg == null ? 0 : vm.addLocalObject(fileArg);
        DvmClass dvmClass = vm.resolveClass(ENVIRONMENT_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isExternalStorageRemovable",
                "(Ljava/io/File;)Z", true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticBooleanMethodV(vm, dvmClass, signature,
                    new TestVaList(vm, method, fileHash));
        }
        return jni.callStaticBooleanMethod(vm, dvmClass, signature,
                new TestVarArg(vm, method, fileHash));
    }

    private static DvmObject<?> invokeGetExternalStoragePublicDirectory(AbstractJni jni, BaseVM vm,
                                                                        boolean useVaList,
                                                                        String type) {
        int typeHash = vm.addLocalObject(new StringObject(vm, type));
        DvmClass dvmClass = vm.resolveClass(ENVIRONMENT_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getExternalStoragePublicDirectory",
                "(Ljava/lang/String;)Ljava/io/File;", true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, dvmClass, signature,
                    new TestVaList(vm, method, typeHash));
        }
        return jni.callStaticObjectMethod(vm, dvmClass, signature,
                new TestVarArg(vm, method, typeHash));
    }

    private static DvmObject<?> invokeGetExternalStoragePublicDirectoryNullType(AbstractJni jni,
                                                                                BaseVM vm,
                                                                                boolean useVaList) {
        DvmClass dvmClass = vm.resolveClass(ENVIRONMENT_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getExternalStoragePublicDirectory",
                "(Ljava/lang/String;)Ljava/io/File;", true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, dvmClass, signature,
                    new TestVaList(vm, method, 0));
        }
        return jni.callStaticObjectMethod(vm, dvmClass, signature,
                new TestVarArg(vm, method, 0));
    }

    /**
     * @param type {@code null} → null JNI arg (base external files dir); non-null → StringObject
     */
    private static DvmObject<?> invokeGetExternalFilesDir(AbstractJni jni, BaseVM vm,
                                                          boolean useVaList, String className,
                                                          String type) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmObject<?> receiver = dvmClass.newObject(null);
        DvmMethod method = new DvmMethod(dvmClass, "getExternalFilesDir",
                "(Ljava/lang/String;)Ljava/io/File;", false);
        int typeHash = type == null ? 0 : vm.addLocalObject(new StringObject(vm, type));
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, method.getSignature(),
                    new TestVaList(vm, method, typeHash));
        }
        return jni.callObjectMethod(vm, receiver, method.getSignature(),
                new TestVarArg(vm, method, typeHash));
    }

    /**
     * @param type {@code null} → null JNI arg (base external files dirs); non-null → StringObject
     */
    private static ArrayObject invokeGetExternalFilesDirs(AbstractJni jni, BaseVM vm,
                                                          boolean useVaList, String className,
                                                          String type) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmObject<?> receiver = dvmClass.newObject(null);
        DvmMethod method = new DvmMethod(dvmClass, "getExternalFilesDirs",
                "(Ljava/lang/String;)[Ljava/io/File;", false);
        int typeHash = type == null ? 0 : vm.addLocalObject(new StringObject(vm, type));
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, receiver, method.getSignature(),
                    new TestVaList(vm, method, typeHash));
        } else {
            result = jni.callObjectMethod(vm, receiver, method.getSignature(),
                    new TestVarArg(vm, method, typeHash));
        }
        assertTrue(result instanceof ArrayObject);
        return (ArrayObject) result;
    }

    private static DvmObject<?> invokeGetExternalCacheDir(AbstractJni jni, BaseVM vm,
                                                          boolean useVaList, String className) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmObject<?> receiver = dvmClass.newObject(null);
        DvmMethod method = new DvmMethod(dvmClass, "getExternalCacheDir",
                "()Ljava/io/File;", false);
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, method.getSignature(),
                    new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, receiver, method.getSignature(),
                new TestVarArg(vm, method));
    }

    private static ArrayObject invokeGetExternalCacheDirs(AbstractJni jni, BaseVM vm,
                                                          boolean useVaList, String className) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmObject<?> receiver = dvmClass.newObject(null);
        DvmMethod method = new DvmMethod(dvmClass, "getExternalCacheDirs",
                "()[Ljava/io/File;", false);
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, receiver, method.getSignature(),
                    new TestVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, receiver, method.getSignature(),
                    new TestVarArg(vm, method));
        }
        assertTrue(result instanceof ArrayObject);
        return (ArrayObject) result;
    }

    private static DvmObject<?> invokeGetObbDir(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                String className) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmObject<?> receiver = dvmClass.newObject(null);
        DvmMethod method = new DvmMethod(dvmClass, "getObbDir", "()Ljava/io/File;", false);
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, method.getSignature(),
                    new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, receiver, method.getSignature(),
                new TestVarArg(vm, method));
    }

    private static ArrayObject invokeGetObbDirs(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                String className) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmObject<?> receiver = dvmClass.newObject(null);
        DvmMethod method = new DvmMethod(dvmClass, "getObbDirs", "()[Ljava/io/File;", false);
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, receiver, method.getSignature(),
                    new TestVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, receiver, method.getSignature(),
                    new TestVarArg(vm, method));
        }
        assertTrue(result instanceof ArrayObject);
        return (ArrayObject) result;
    }

    private static ArrayObject invokeGetExternalMediaDirs(AbstractJni jni, BaseVM vm,
                                                          boolean useVaList, String className) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmObject<?> receiver = dvmClass.newObject(null);
        DvmMethod method = new DvmMethod(dvmClass, "getExternalMediaDirs",
                "()[Ljava/io/File;", false);
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, receiver, method.getSignature(),
                    new TestVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, receiver, method.getSignature(),
                    new TestVarArg(vm, method));
        }
        assertTrue(result instanceof ArrayObject);
        return (ArrayObject) result;
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

    private static final class TestVarArg extends VarArg {
        TestVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
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
