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
 * Coverage for {@code filesystem.systemDirectories} wiring of static
 * {@code Environment.getRootDirectory} / {@code getDataDirectory} /
 * {@code getDownloadCacheDirectory} / {@code getStorageDirectory}
 * (VarArg + VaList; per-field explicit only; no host directory / FileIO creation).
 */
public class AndroidSystemDirectoriesJniTest {

    private static final String ENVIRONMENT_CLASS = "android/os/Environment";
    private static final String FILE_CLASS = "java/io/File";

    private static final String FULL_JSON = "{"
            + "\"filesystem\":{"
            + "\"systemDirectories\":{"
            + "\"rootDirectory\":\"/system\","
            + "\"dataDirectory\":\"/data\","
            + "\"downloadCacheDirectory\":\"/cache\","
            + "\"storageDirectory\":\"/storage\""
            + "}"
            + "}"
            + "}";

    private static final String PARTIAL_ROOT_JSON = "{"
            + "\"filesystem\":{"
            + "\"systemDirectories\":{"
            + "\"rootDirectory\":\"/system\""
            + "}"
            + "}"
            + "}";

    private static final String EMPTY_NODE_JSON = "{"
            + "\"filesystem\":{\"systemDirectories\":{}}"
            + "}";

    private static final String ABSENT_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testSystemDirectoriesConfiguredVarArg32() throws Exception {
        runConfigured(false, false);
    }

    @Test
    public void testSystemDirectoriesConfiguredVaList64() throws Exception {
        runConfigured(true, true);
    }

    @Test
    public void testSystemDirectoriesPartialAndAbsenceVarArg32() throws Exception {
        runPartialAndAbsence(false, false);
    }

    @Test
    public void testSystemDirectoriesPartialAndAbsenceVaList64() throws Exception {
        runPartialAndAbsence(true, true);
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_JSON);
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

            DvmObject<?> root = invokeGetRootDirectory(jni, baseVM, useVaList);
            assertNotNull(root);
            assertEquals(FILE_CLASS, root.getObjectType().getClassName());
            assertTrue(root.getValue() instanceof File);
            assertEquals(new File("/system"), root.getValue());

            DvmObject<?> data = invokeGetDataDirectory(jni, baseVM, useVaList);
            assertNotNull(data);
            assertEquals(new File("/data"), data.getValue());

            DvmObject<?> cache = invokeGetDownloadCacheDirectory(jni, baseVM, useVaList);
            assertNotNull(cache);
            assertEquals(new File("/cache"), cache.getValue());

            DvmObject<?> storage = invokeGetStorageDirectory(jni, baseVM, useVaList);
            assertNotNull(storage);
            assertEquals(new File("/storage"), storage.getValue());

            CapturedEvent rootEv = findLastEvent(sink.events, "filesystem_system_directories",
                    "Environment.getRootDirectory");
            assertNotNull(rootEv);
            assertEquals("json-config", rootEv.source);
            assertEquals("field=rootDirectory,result=/system", String.valueOf(rootEv.value));

            CapturedEvent dataEv = findLastEvent(sink.events, "filesystem_system_directories",
                    "Environment.getDataDirectory");
            assertNotNull(dataEv);
            assertEquals("field=dataDirectory,result=/data", String.valueOf(dataEv.value));

            CapturedEvent cacheEv = findLastEvent(sink.events, "filesystem_system_directories",
                    "Environment.getDownloadCacheDirectory");
            assertNotNull(cacheEv);
            assertEquals("field=downloadCacheDirectory,result=/cache",
                    String.valueOf(cacheEv.value));

            CapturedEvent storageEv = findLastEvent(sink.events, "filesystem_system_directories",
                    "Environment.getStorageDirectory");
            assertNotNull(storageEv);
            assertEquals("field=storageDirectory,result=/storage",
                    String.valueOf(storageEv.value));

            assertEquals(1, countEvents(sink.events, "filesystem_system_directories",
                    "Environment.getRootDirectory"));
            assertEquals(1, countEvents(sink.events, "filesystem_system_directories",
                    "Environment.getDataDirectory"));
            assertEquals(1, countEvents(sink.events, "filesystem_system_directories",
                    "Environment.getDownloadCacheDirectory"));
            assertEquals(1, countEvents(sink.events, "filesystem_system_directories",
                    "Environment.getStorageDirectory"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runPartialAndAbsence(boolean is64Bit, boolean useVaList) throws Exception {
        // partial: only root configured → data/cache/storage keep UOE fallback
        TraceEnvironmentConfig partial = TraceEnvironmentConfig.parse(PARTIAL_ROOT_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(partial)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> root = invokeGetRootDirectory(jni, baseVM, useVaList);
            assertEquals(new File("/system"), root.getValue());
            assertEquals(1, countEvents(sink.events, "filesystem_system_directories",
                    "Environment.getRootDirectory"));

            try {
                invokeGetDataDirectory(jni, baseVM, useVaList);
                fail("expected UOE for unconfigured dataDirectory");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDataDirectory"));
            }
            try {
                invokeGetDownloadCacheDirectory(jni, baseVM, useVaList);
                fail("expected UOE for unconfigured downloadCacheDirectory");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDownloadCacheDirectory"));
            }
            try {
                invokeGetStorageDirectory(jni, baseVM, useVaList);
                fail("expected UOE for unconfigured storageDirectory");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStorageDirectory"));
            }
            assertEquals(0, countEvents(sink.events, "filesystem_system_directories",
                    "Environment.getDataDirectory"));
            assertEquals(0, countEvents(sink.events, "filesystem_system_directories",
                    "Environment.getDownloadCacheDirectory"));
            assertEquals(0, countEvents(sink.events, "filesystem_system_directories",
                    "Environment.getStorageDirectory"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        // empty node and fully absent: all four UOE, no events
        for (String json : new String[]{EMPTY_NODE_JSON, ABSENT_JSON}) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
            sink = new CapturingSink();
            emulator = null;
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
                try {
                    invokeGetRootDirectory(jni, baseVM, useVaList);
                    fail("expected UOE for getRootDirectory: " + json);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getRootDirectory"));
                }
                try {
                    invokeGetDataDirectory(jni, baseVM, useVaList);
                    fail("expected UOE for getDataDirectory: " + json);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDataDirectory"));
                }
                try {
                    invokeGetDownloadCacheDirectory(jni, baseVM, useVaList);
                    fail("expected UOE for getDownloadCacheDirectory: " + json);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDownloadCacheDirectory"));
                }
                try {
                    invokeGetStorageDirectory(jni, baseVM, useVaList);
                    fail("expected UOE for getStorageDirectory: " + json);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getStorageDirectory"));
                }
                for (CapturedEvent e : sink.events) {
                    assertFalse("filesystem_system_directories".equals(e.kind));
                }
            } finally {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                    emulator.close();
                }
            }
        }
    }

    private static DvmObject<?> invokeGetRootDirectory(AbstractJni jni, BaseVM vm,
                                                       boolean useVaList) {
        return invokeNoArgStaticObject(jni, vm, useVaList, "getRootDirectory",
                "()Ljava/io/File;");
    }

    private static DvmObject<?> invokeGetDataDirectory(AbstractJni jni, BaseVM vm,
                                                       boolean useVaList) {
        return invokeNoArgStaticObject(jni, vm, useVaList, "getDataDirectory",
                "()Ljava/io/File;");
    }

    private static DvmObject<?> invokeGetDownloadCacheDirectory(AbstractJni jni, BaseVM vm,
                                                                boolean useVaList) {
        return invokeNoArgStaticObject(jni, vm, useVaList, "getDownloadCacheDirectory",
                "()Ljava/io/File;");
    }

    private static DvmObject<?> invokeGetStorageDirectory(AbstractJni jni, BaseVM vm,
                                                          boolean useVaList) {
        return invokeNoArgStaticObject(jni, vm, useVaList, "getStorageDirectory",
                "()Ljava/io/File;");
    }

    private static DvmObject<?> invokeNoArgStaticObject(AbstractJni jni, BaseVM vm,
                                                        boolean useVaList, String name,
                                                        String argsAndReturn) {
        DvmClass envClass = vm.resolveClass(ENVIRONMENT_CLASS);
        DvmMethod method = new DvmMethod(envClass, name, argsAndReturn, true);
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, envClass, method.getSignature(),
                    new TestNoArgVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, envClass, method.getSignature(),
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
