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

/**
 * Coverage for top-level {@code graphics} v1: static {@code GLES10/20/30.glGetString(I)}
 * and {@code EGL14.eglQueryString(EGLDisplay,I)}. No native libGLES/libEGL.
 */
public class GraphicsJniTest {

    private static final int GL_VENDOR = 0x1F00;
    private static final int GL_RENDERER = 0x1F01;
    private static final int GL_VERSION = 0x1F02;
    private static final int GL_EXTENSIONS = 0x1F03;
    private static final int GL_SHADING_LANGUAGE_VERSION = 0x8B8C;
    private static final int EGL_VENDOR = 0x3053;
    private static final int EGL_VERSION = 0x3054;
    private static final int EGL_EXTENSIONS = 0x3055;

    private static final String GRAPHICS_FULL_JSON = "{"
            + "\"graphics\":{"
            + "\"vendor\":\"Qualcomm\","
            + "\"renderer\":\"Adreno (TM) 730\","
            + "\"version\":\"OpenGL ES 3.2\","
            + "\"shadingLanguageVersion\":\"OpenGL ES GLSL ES 3.20\","
            + "\"extensions\":[\"GL_OES_EGL_image\",\"GL_EXT_texture_format_BGRA8888\"],"
            + "\"eglVendor\":\"Android\","
            + "\"eglVersion\":\"1.5 Android META-EGL\","
            + "\"eglExtensions\":[\"EGL_KHR_image_base\",\"EGL_ANDROID_image_native_buffer\"]"
            + "}"
            + "}";

    private static final String GRAPHICS_EMPTY_JSON = "{\"graphics\":{}}";
    private static final String GRAPHICS_EMPTY_EXTENSIONS_JSON =
            "{\"graphics\":{\"extensions\":[],\"eglExtensions\":[]}}";
    private static final String NO_GRAPHICS_JSON = "{\"android\":{\"packageName\":\"com.demo.app\"}}";

    @Test
    public void testGraphicsGles10VendorVarArg32() throws Exception {
        runGlesGetString(false, false, "android/opengl/GLES10", "GLES10.glGetString",
                GL_VENDOR, "Qualcomm", "vendor");
    }

    @Test
    public void testGraphicsGles20RendererVaList64() throws Exception {
        runGlesGetString(true, true, "android/opengl/GLES20", "GLES20.glGetString",
                GL_RENDERER, "Adreno (TM) 730", "renderer");
    }

    @Test
    public void testGraphicsGles30VersionVarArg32() throws Exception {
        runGlesGetString(false, false, "android/opengl/GLES30", "GLES30.glGetString",
                GL_VERSION, "OpenGL ES 3.2", "version");
    }

    @Test
    public void testGraphicsGles20ShadingLanguageVaList64() throws Exception {
        runGlesGetString(true, true, "android/opengl/GLES20", "GLES20.glGetString",
                GL_SHADING_LANGUAGE_VERSION, "OpenGL ES GLSL ES 3.20", "shadingLanguageVersion");
    }

    @Test
    public void testGraphicsGles10ExtensionsVarArg32() throws Exception {
        runGlesGetString(false, false, "android/opengl/GLES10", "GLES10.glGetString",
                GL_EXTENSIONS, "GL_OES_EGL_image GL_EXT_texture_format_BGRA8888", "extensions");
    }

    @Test
    public void testGraphicsEmptyExtensionsVaList64() throws Exception {
        runConfiguredString(true, true, GRAPHICS_EMPTY_EXTENSIONS_JSON,
                "android/opengl/GLES20", "glGetString", "(I)Ljava/lang/String;",
                new TestIntFactory(GL_EXTENSIONS), "GLES20.glGetString",
                "", "name=" + GL_EXTENSIONS + ",field=extensions,resultLength=0");
    }

    @Test
    public void testGraphicsEglVendorVarArg32() throws Exception {
        runEglQueryString(false, false, EGL_VENDOR, "Android", "eglVendor");
    }

    @Test
    public void testGraphicsEglVersionVaList64() throws Exception {
        runEglQueryString(true, true, EGL_VERSION, "1.5 Android META-EGL", "eglVersion");
    }

    @Test
    public void testGraphicsEglExtensionsVarArg32() throws Exception {
        runEglQueryString(false, false, EGL_EXTENSIONS,
                "EGL_KHR_image_base EGL_ANDROID_image_native_buffer", "eglExtensions");
    }

    @Test
    public void testGraphicsUnknownNameUoeVarArg32() throws Exception {
        runGlesUoe(false, false, GRAPHICS_FULL_JSON, "android/opengl/GLES10", 0x9999);
    }

    @Test
    public void testGraphicsGles31NotInV1SubsetUoe() throws Exception {
        runGlesUoe(false, false, GRAPHICS_FULL_JSON, "android/opengl/GLES31", GL_VENDOR);
    }

    @Test
    public void testGraphicsOmittedFieldUoeVaList64() throws Exception {
        runGlesUoe(true, true, "{\"graphics\":{\"vendor\":\"Qualcomm\"}}",
                "android/opengl/GLES20", GL_RENDERER);
    }

    @Test
    public void testGraphicsEmptyObjectUoeVarArg32() throws Exception {
        runGlesUoe(false, false, GRAPHICS_EMPTY_JSON, "android/opengl/GLES10", GL_VENDOR);
    }

    @Test
    public void testGraphicsAbsentUoeVaList64() throws Exception {
        runGlesUoe(true, true, NO_GRAPHICS_JSON, "android/opengl/GLES20", GL_VENDOR);
    }

    @Test
    public void testGraphicsParseContract() {
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(NO_GRAPHICS_JSON);
        assertFalse(omitted.isGraphicsConfigured());
        assertNull(omitted.getGraphicsConfig());

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(GRAPHICS_EMPTY_JSON);
        assertTrue(empty.isGraphicsConfigured());
        assertFalse(empty.getGraphicsConfig().isVendorConfigured());
        assertFalse(empty.getGraphicsConfig().isExtensionsConfigured());
        assertNull(empty.getGraphicsConfig().getExtensionsJoined());

        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse(GRAPHICS_FULL_JSON);
        assertEquals("Qualcomm", full.getGraphicsConfig().getVendor());
        assertEquals("Adreno (TM) 730", full.getGraphicsConfig().getRenderer());
        assertEquals("OpenGL ES 3.2", full.getGraphicsConfig().getVersion());
        assertEquals("OpenGL ES GLSL ES 3.20", full.getGraphicsConfig().getShadingLanguageVersion());
        assertEquals("GL_OES_EGL_image GL_EXT_texture_format_BGRA8888",
                full.getGraphicsConfig().getExtensionsJoined());
        assertEquals("Android", full.getGraphicsConfig().getEglVendor());
        assertEquals("1.5 Android META-EGL", full.getGraphicsConfig().getEglVersion());
        assertEquals("EGL_KHR_image_base EGL_ANDROID_image_native_buffer",
                full.getGraphicsConfig().getEglExtensionsJoined());
        assertEquals(2, full.getGraphicsConfig().getExtensions().size());

        TraceEnvironmentConfig emptyExt = TraceEnvironmentConfig.parse(GRAPHICS_EMPTY_EXTENSIONS_JSON);
        assertTrue(emptyExt.getGraphicsConfig().isExtensionsConfigured());
        assertEquals("", emptyExt.getGraphicsConfig().getExtensionsJoined());
        assertEquals("", emptyExt.getGraphicsConfig().getEglExtensionsJoined());
    }

    @Test
    public void testGraphicsDoesNotInterceptNonOpenglStaticObjectMethod() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(GRAPHICS_FULL_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for32Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass dvmClass = vm.resolveClass("android/os/Build");
            DvmMethod method = new DvmMethod(dvmClass, "getSerial", "()Ljava/lang/String;", true);
            String signature = method.getSignature();
            try {
                jni.callStaticObjectMethod(baseVM, dvmClass, signature,
                        new TestIntVarArg(baseVM, method, 0));
                fail("expected UOE for non-opengl static method");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null && expected.getMessage().contains("getSerial"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("graphics".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testGraphicsParseRejects() {
        assertParseInvalid("{\"graphics\":[]}", "graphics");
        assertParseInvalid("{\"graphics\":1}", "graphics");
        assertParseInvalid("{\"graphics\":{\"extra\":1}}", "graphics.extra");
        assertParseInvalid("{\"graphics\":{\"vendor\":\"\"}}", "graphics.vendor");
        assertParseInvalid("{\"graphics\":{\"vendor\":\"a\\nb\"}}", "graphics.vendor");
        assertParseInvalid("{\"graphics\":{\"extensions\":\"GL_OES_EGL_image\"}}",
                "graphics.extensions");
        assertParseInvalid("{\"graphics\":{\"extensions\":[\"GL OES\"]}}",
                "graphics.extensions[0]");
        assertParseInvalid("{\"graphics\":{\"extensions\":[\"GL_A\",\"GL_A\"]}}",
                "graphics.extensions[1]");
        assertParseInvalid("{\"graphics\":{\"eglExtensions\":1}}", "graphics.eglExtensions");
    }

    private static void runGlesGetString(boolean is64Bit, boolean useVaList, String className,
                                         String api, int name, String expected, String field)
            throws Exception {
        runConfiguredString(is64Bit, useVaList, GRAPHICS_FULL_JSON, className, "glGetString",
                "(I)Ljava/lang/String;", new TestIntFactory(name), api, expected,
                "name=" + name + ",field=" + field + ",resultLength=" + expected.length());
    }

    private static void runEglQueryString(boolean is64Bit, boolean useVaList, int name,
                                          String expected, String field) throws Exception {
        runConfiguredString(is64Bit, useVaList, GRAPHICS_FULL_JSON, "android/opengl/EGL14",
                "eglQueryString", "(Landroid/opengl/EGLDisplay;I)Ljava/lang/String;",
                new TestObjectIntFactory(name), "EGL14.eglQueryString", expected,
                "name=" + name + ",field=" + field + ",resultLength=" + expected.length());
    }

    private static void runConfiguredString(boolean is64Bit, boolean useVaList, String json,
                                            String className, String methodName, String args,
                                            ArgFactory argFactory, String api, String expected,
                                            String sidecarValue) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
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
            DvmClass dvmClass = vm.resolveClass(className);
            DvmMethod method = new DvmMethod(dvmClass, methodName, args, true);
            String signature = method.getSignature();
            DvmObject<?> value = useVaList
                    ? jni.callStaticObjectMethodV(baseVM, dvmClass, signature,
                            argFactory.vaList(baseVM, method))
                    : jni.callStaticObjectMethod(baseVM, dvmClass, signature,
                            argFactory.varArg(baseVM, method));
            assertNotNull(value);
            assertEquals(expected, ((StringObject) value).getValue());
            CapturedEvent ev = findLastEvent(sink.events, "graphics", api);
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals(sidecarValue, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGlesUoe(boolean is64Bit, boolean useVaList, String json,
                                   String className, int name) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
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
            DvmClass dvmClass = vm.resolveClass(className);
            DvmMethod method = new DvmMethod(dvmClass, "glGetString", "(I)Ljava/lang/String;", true);
            String signature = method.getSignature();
            try {
                if (useVaList) {
                    jni.callStaticObjectMethodV(baseVM, dvmClass, signature,
                            new TestIntVaList(baseVM, method, name));
                } else {
                    jni.callStaticObjectMethod(baseVM, dvmClass, signature,
                            new TestIntVarArg(baseVM, method, name));
                }
                fail("expected UOE for " + signature);
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null && expected.getMessage().contains("glGetString"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("graphics".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertParseInvalid(String json, String pathFragment) {
        try {
            TraceEnvironmentConfig.parse(json);
            fail("expected parse failure for " + json);
        } catch (IllegalArgumentException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage() != null && expected.getMessage().contains(pathFragment));
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

    private interface ArgFactory {
        VarArg varArg(BaseVM vm, DvmMethod method);

        VaList vaList(BaseVM vm, DvmMethod method);
    }

    private static final class TestIntFactory implements ArgFactory {
        private final int value;

        TestIntFactory(int value) {
            this.value = value;
        }

        @Override
        public VarArg varArg(BaseVM vm, DvmMethod method) {
            return new TestIntVarArg(vm, method, value);
        }

        @Override
        public VaList vaList(BaseVM vm, DvmMethod method) {
            return new TestIntVaList(vm, method, value);
        }
    }

    private static final class TestObjectIntFactory implements ArgFactory {
        private final int name;

        TestObjectIntFactory(int name) {
            this.name = name;
        }

        @Override
        public VarArg varArg(BaseVM vm, DvmMethod method) {
            return new TestObjectIntVarArg(vm, method, 0, name);
        }

        @Override
        public VaList vaList(BaseVM vm, DvmMethod method) {
            return new TestObjectIntVaList(vm, method, 0, name);
        }
    }

    private static final class TestIntVarArg extends VarArg {
        TestIntVarArg(BaseVM vm, DvmMethod method, int value) {
            super(vm, method);
            args.add(Integer.valueOf(value));
        }
    }

    private static final class TestIntVaList extends VaList {
        TestIntVaList(BaseVM vm, DvmMethod method, int value) {
            super(vm, method);
            args.add(Integer.valueOf(value));
        }
    }

    private static final class TestObjectIntVarArg extends VarArg {
        TestObjectIntVarArg(BaseVM vm, DvmMethod method, int objectHash, int name) {
            super(vm, method);
            args.add(Integer.valueOf(objectHash));
            args.add(Integer.valueOf(name));
        }
    }

    private static final class TestObjectIntVaList extends VaList {
        TestObjectIntVaList(BaseVM vm, DvmMethod method, int objectHash, int name) {
            super(vm, method);
            args.add(Integer.valueOf(objectHash));
            args.add(Integer.valueOf(name));
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
