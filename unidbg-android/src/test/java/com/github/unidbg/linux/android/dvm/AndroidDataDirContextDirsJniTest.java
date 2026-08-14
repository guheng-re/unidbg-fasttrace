package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
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
 * Coverage for root {@code android.dataDir} wiring of
 * {@code Context}/{@code ContextWrapper}/{@code Application}
 * {@code getDataDir}, fixed dirs, and {@code getDir(String,int)}
 * (VarArg + VaList; only when dataDir explicit and non-null).
 */
public class AndroidDataDirContextDirsJniTest {

    private static final String DATA_DIR = "/data/user/0/com.demo.app";

    private static final String CONFIGURED_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"dataDir\":\"" + DATA_DIR + "\""
            + "}"
            + "}";

    private static final String ABSENT_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    private static final String NULL_DATA_DIR_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"dataDir\":null"
            + "}"
            + "}";

    private static final String[] RECEIVER_CLASSES = {
            "android/content/Context",
            "android/content/ContextWrapper",
            "android/app/Application"
    };

    @Test
    public void testDataDirContextDirsVarArg32() throws Exception {
        runConfigured(false, false);
    }

    @Test
    public void testDataDirContextDirsVaList64() throws Exception {
        runConfigured(true, true);
    }

    @Test
    public void testDataDirContextDirsAbsentAndNullVarArg32() throws Exception {
        runAbsentAndNull(false, false);
    }

    @Test
    public void testDataDirContextDirsAbsentAndNullVaList64() throws Exception {
        runAbsentAndNull(true, true);
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        assertTrue(config.isAndroidDataDirConfigured());
        assertEquals(DATA_DIR, config.getAndroidDataDir());

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

            for (String receiver : RECEIVER_CLASSES) {
                DvmObject<?> dataDir = invokeGetDataDir(jni, baseVM, useVaList, receiver);
                assertNotNull(dataDir);
                assertTrue(dataDir.getValue() instanceof File);
                assertEquals(new File(DATA_DIR), dataDir.getValue());

                DvmObject<?> files = invokeGetFilesDir(jni, baseVM, useVaList, receiver);
                assertNotNull(files);
                assertTrue(files.getValue() instanceof File);
                assertEquals(new File(DATA_DIR, "files"), files.getValue());

                DvmObject<?> cache = invokeGetCacheDir(jni, baseVM, useVaList, receiver);
                assertNotNull(cache);
                assertTrue(cache.getValue() instanceof File);
                assertEquals(new File(DATA_DIR, "cache"), cache.getValue());

                DvmObject<?> noBackup = invokeGetNoBackupFilesDir(jni, baseVM, useVaList, receiver);
                assertNotNull(noBackup);
                assertTrue(noBackup.getValue() instanceof File);
                assertEquals(new File(DATA_DIR, "no_backup"), noBackup.getValue());

                DvmObject<?> codeCache = invokeGetCodeCacheDir(jni, baseVM, useVaList, receiver);
                assertNotNull(codeCache);
                assertTrue(codeCache.getValue() instanceof File);
                assertEquals(new File(DATA_DIR, "code_cache"), codeCache.getValue());

                // mode ignored; path is app_<name>
                DvmObject<?> getDir = invokeGetDir(jni, baseVM, useVaList, receiver, "plugins", 0);
                assertNotNull(getDir);
                assertTrue(getDir.getValue() instanceof File);
                assertEquals(new File(DATA_DIR, "app_plugins"), getDir.getValue());
                DvmObject<?> getDirModeIgnored = invokeGetDir(jni, baseVM, useVaList, receiver,
                        "plugins", 0x1);
                assertEquals(new File(DATA_DIR, "app_plugins"), getDirModeIgnored.getValue());
            }

            // invalid getDir names → UOE, no extra event
            int eventsBeforeInvalid = sink.events.size();
            String[] invalidNames = new String[]{"", "a/b", "a\\b", "a\nb", "a\rb", "a\0b"};
            for (String bad : invalidNames) {
                try {
                    invokeGetDir(jni, baseVM, useVaList, RECEIVER_CLASSES[0], bad, 0);
                    fail("expected UOE for invalid getDir name: " + bad);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDir"));
                }
            }
            try {
                invokeGetDirNullName(jni, baseVM, useVaList, RECEIVER_CLASSES[0], 0);
                fail("expected UOE for null getDir name");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDir"));
            }
            assertEquals(eventsBeforeInvalid, sink.events.size());

            CapturedEvent dataDirEv = findLastEvent(sink.events, "android_data_dir", "Context.getDataDir");
            assertNotNull(dataDirEv);
            assertEquals("json-config", dataDirEv.source);
            assertEquals("result=" + DATA_DIR, String.valueOf(dataDirEv.value));

            CapturedEvent filesEv = findLastEvent(sink.events, "android_data_dir", "Context.getFilesDir");
            assertNotNull(filesEv);
            assertEquals("json-config", filesEv.source);
            assertEquals("result=" + DATA_DIR + "/files", String.valueOf(filesEv.value));

            CapturedEvent cacheEv = findLastEvent(sink.events, "android_data_dir", "Context.getCacheDir");
            assertNotNull(cacheEv);
            assertEquals("result=" + DATA_DIR + "/cache", String.valueOf(cacheEv.value));

            CapturedEvent noBackupEv = findLastEvent(sink.events, "android_data_dir",
                    "Context.getNoBackupFilesDir");
            assertNotNull(noBackupEv);
            assertEquals("result=" + DATA_DIR + "/no_backup", String.valueOf(noBackupEv.value));

            CapturedEvent codeCacheEv = findLastEvent(sink.events, "android_data_dir",
                    "Context.getCodeCacheDir");
            assertNotNull(codeCacheEv);
            assertEquals("result=" + DATA_DIR + "/code_cache", String.valueOf(codeCacheEv.value));

            CapturedEvent getDirEv = findLastEvent(sink.events, "android_data_dir", "Context.getDir");
            assertNotNull(getDirEv);
            assertEquals("name=plugins,result=" + DATA_DIR + "/app_plugins",
                    String.valueOf(getDirEv.value));

            assertEquals(3, countEvents(sink.events, "android_data_dir", "Context.getDataDir"));
            assertEquals(3, countEvents(sink.events, "android_data_dir", "Context.getFilesDir"));
            assertEquals(3, countEvents(sink.events, "android_data_dir", "Context.getCacheDir"));
            assertEquals(3, countEvents(sink.events, "android_data_dir",
                    "Context.getNoBackupFilesDir"));
            assertEquals(3, countEvents(sink.events, "android_data_dir",
                    "Context.getCodeCacheDir"));
            // 3 receivers × 2 successful getDir (mode 0 and mode 1)
            assertEquals(6, countEvents(sink.events, "android_data_dir", "Context.getDir"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentAndNull(boolean is64Bit, boolean useVaList) throws Exception {
        for (String json : new String[]{ABSENT_JSON, NULL_DATA_DIR_JSON}) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
            assertFalse(config.isAndroidDataDirConfigured());
            assertEquals(null, config.getAndroidDataDir());

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
                        invokeGetDataDir(jni, baseVM, useVaList, receiver);
                        fail("expected UOE for getDataDir: " + json + " " + receiver);
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getDataDir"));
                    }
                    try {
                        invokeGetFilesDir(jni, baseVM, useVaList, receiver);
                        fail("expected UOE for getFilesDir: " + json + " " + receiver);
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getFilesDir"));
                    }
                    try {
                        invokeGetCacheDir(jni, baseVM, useVaList, receiver);
                        fail("expected UOE for getCacheDir: " + json + " " + receiver);
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getCacheDir"));
                    }
                    try {
                        invokeGetNoBackupFilesDir(jni, baseVM, useVaList, receiver);
                        fail("expected UOE for getNoBackupFilesDir: " + json + " " + receiver);
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getNoBackupFilesDir"));
                    }
                    try {
                        invokeGetCodeCacheDir(jni, baseVM, useVaList, receiver);
                        fail("expected UOE for getCodeCacheDir: " + json + " " + receiver);
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getCodeCacheDir"));
                    }
                    try {
                        invokeGetDir(jni, baseVM, useVaList, receiver, "plugins", 0);
                        fail("expected UOE for getDir: " + json + " " + receiver);
                    } catch (UnsupportedOperationException expected) {
                        assertTrue(expected.getMessage() != null
                                && expected.getMessage().contains("getDir"));
                    }
                }
                for (CapturedEvent e : sink.events) {
                    assertFalse("android_data_dir".equals(e.kind));
                }
            } finally {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                    emulator.close();
                }
            }
        }
    }

    private static DvmObject<?> invokeGetDataDir(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 String className) {
        return invokeNoArgObject(jni, vm, useVaList, className, "getDataDir", "()Ljava/io/File;");
    }

    private static DvmObject<?> invokeGetFilesDir(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  String className) {
        return invokeNoArgObject(jni, vm, useVaList, className, "getFilesDir", "()Ljava/io/File;");
    }

    private static DvmObject<?> invokeGetCacheDir(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  String className) {
        return invokeNoArgObject(jni, vm, useVaList, className, "getCacheDir", "()Ljava/io/File;");
    }

    private static DvmObject<?> invokeGetNoBackupFilesDir(AbstractJni jni, BaseVM vm,
                                                          boolean useVaList, String className) {
        return invokeNoArgObject(jni, vm, useVaList, className, "getNoBackupFilesDir",
                "()Ljava/io/File;");
    }

    private static DvmObject<?> invokeGetCodeCacheDir(AbstractJni jni, BaseVM vm,
                                                      boolean useVaList, String className) {
        return invokeNoArgObject(jni, vm, useVaList, className, "getCodeCacheDir",
                "()Ljava/io/File;");
    }

    private static DvmObject<?> invokeGetDir(AbstractJni jni, BaseVM vm, boolean useVaList,
                                             String className, String name, int mode) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmObject<?> receiver = dvmClass.newObject(null);
        DvmMethod method = new DvmMethod(dvmClass, "getDir",
                "(Ljava/lang/String;I)Ljava/io/File;", false);
        int nameHash = vm.addLocalObject(new StringObject(vm, name));
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, method.getSignature(),
                    new TestGetDirVaList(vm, method, nameHash, mode));
        }
        return jni.callObjectMethod(vm, receiver, method.getSignature(),
                new TestGetDirVarArg(vm, method, nameHash, mode));
    }

    private static DvmObject<?> invokeGetDirNullName(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                     String className, int mode) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmObject<?> receiver = dvmClass.newObject(null);
        DvmMethod method = new DvmMethod(dvmClass, "getDir",
                "(Ljava/lang/String;I)Ljava/io/File;", false);
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, method.getSignature(),
                    new TestGetDirVaList(vm, method, 0, mode));
        }
        return jni.callObjectMethod(vm, receiver, method.getSignature(),
                new TestGetDirVarArg(vm, method, 0, mode));
    }

    private static DvmObject<?> invokeNoArgObject(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  String className, String methodName,
                                                  String argsAndReturn) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmObject<?> receiver = dvmClass.newObject(null);
        DvmMethod method = new DvmMethod(dvmClass, methodName, argsAndReturn, false);
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, method.getSignature(),
                    new TestNoArgVaList(vm, method));
        }
        return jni.callObjectMethod(vm, receiver, method.getSignature(),
                new TestNoArgVarArg(vm, method));
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

    private static final class TestNoArgVarArg extends VarArg {
        TestNoArgVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }

    private static final class TestNoArgVaList extends VaList {
        TestNoArgVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }

    private static final class TestGetDirVarArg extends VarArg {
        TestGetDirVarArg(BaseVM vm, DvmMethod method, int nameHash, int mode) {
            super(vm, method);
            args.add(nameHash);
            args.add(mode);
        }
    }

    private static final class TestGetDirVaList extends VaList {
        TestGetDirVaList(BaseVM vm, DvmMethod method, int nameHash, int mode) {
            super(vm, method);
            args.add(nameHash);
            args.add(mode);
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
