package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.Module;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.ElfLibraryFile;
import com.github.unidbg.linux.android.ElfLibraryRawFile;
import com.github.unidbg.linux.android.dvm.apk.Apk;
import com.github.unidbg.linux.android.dvm.apk.ApkFactory;
import com.github.unidbg.linux.android.dvm.apk.AssetResolver;
import com.github.unidbg.linux.android.dvm.array.LongArray;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.spi.LibraryFile;
import net.dongliu.apk.parser.bean.CertificateMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;

public abstract class BaseVM implements VM, DvmClassFactory {

    private static final Logger log = LoggerFactory.getLogger(BaseVM.class);

    public static boolean valueOf(int value) {
        if (value == VM.JNI_TRUE) {
            return true;
        } else if (value == VM.JNI_FALSE) {
            return false;
        } else {
            throw new IllegalStateException("Invalid boolean value=" + value);
        }
    }

    final Map<Integer, DvmClass> classMap = new HashMap<>();

    Jni jni;

    DvmObject<?> throwable;

    boolean verbose, verboseMethodOperation, verboseFieldOperation;

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public void setVerboseMethodOperation(boolean verboseMethodOperation) {
        this.verboseMethodOperation = verboseMethodOperation;
    }

    @Override
    public void setVerboseFieldOperation(boolean verboseFieldOperation) {
        this.verboseFieldOperation = verboseFieldOperation;
    }

    @Override
    public void throwException(DvmObject<?> throwable) {
        this.throwable = throwable;
    }

    @Override
    public final void setJni(Jni jni) {
        this.jni = jni;
    }

    private final AndroidEmulator emulator;
    private final Apk apk;

    final Set<String> notFoundClassSet = new HashSet<>();

    @Override
    public void addNotFoundClass(String className) {
        notFoundClassSet.add(className);
    }

    BaseVM(AndroidEmulator emulator, File apkFile) {
        this.emulator = emulator;
        this.apk = apkFile == null ? null : ApkFactory.createApk(apkFile);
    }

    final static class ObjRef {
        final DvmObject<?> obj;
        final boolean weak;
        ObjRef(DvmObject<?> obj, boolean weak) {
            this.obj = obj;
            this.weak = weak;
            this.refCount = 1;
        }
        int refCount;
        @Override
        public String toString() {
            return String.valueOf(obj);
        }
    }

    final Map<Integer, ObjRef> globalObjectMap = new HashMap<>();
    final Map<Integer, ObjRef> weakGlobalObjectMap = new HashMap<>();
    final Map<Integer, ObjRef> localObjectMap = new HashMap<>();

    private DvmClassFactory dvmClassFactory;

    @Override
    public void setDvmClassFactory(DvmClassFactory factory) {
        this.dvmClassFactory = factory;
    }

    private HashFunction hashFunction = Hasher.Default;

    @Override
    public void setHashFunction(HashFunction hashFunction) {
        if (hashFunction == null) {
            throw new NullPointerException("hashFunction == null");
        }
        if (!classMap.isEmpty()) {
            throw new IllegalStateException("Must set hash function before resolving any class");
        }
        this.hashFunction = hashFunction;
    }

    public final int hash(String className) {
        return hashFunction.hash(className);
    }

    /**
     * JNI {@code jmethodID}/{@code jfieldID} values are global. GetMethodID on
     * an interface (e.g. {@code Map.entrySet}) must still resolve when the
     * receiver is a concrete class ({@code HashMap}) that was not constructed
     * with that interface as a declared parent.
     */
    private final Map<Integer, DvmMethod> instanceMethodIds = new HashMap<Integer, DvmMethod>();
    private final Map<Integer, DvmMethod> staticMethodIds = new HashMap<Integer, DvmMethod>();
    private final Map<Integer, DvmField> instanceFieldIds = new HashMap<Integer, DvmField>();
    private final Map<Integer, DvmField> staticFieldIds = new HashMap<Integer, DvmField>();

    void registerInstanceMethod(int hash, DvmMethod method) {
        instanceMethodIds.put(hash, method);
    }

    void registerStaticMethod(int hash, DvmMethod method) {
        staticMethodIds.put(hash, method);
    }

    void registerInstanceField(int hash, DvmField field) {
        instanceFieldIds.put(hash, field);
    }

    void registerStaticField(int hash, DvmField field) {
        staticFieldIds.put(hash, field);
    }

    DvmMethod findInstanceMethod(int hash) {
        return instanceMethodIds.get(hash);
    }

    DvmMethod findStaticMethod(int hash) {
        return staticMethodIds.get(hash);
    }

    DvmField findInstanceField(int hash) {
        return instanceFieldIds.get(hash);
    }

    DvmField findStaticField(int hash) {
        return staticFieldIds.get(hash);
    }

    /**
     * When a well-known JDK type is first resolved without parents, attach the
     * standard super/interface so interface {@code jmethodID}s work on the
     * concrete receiver. Only applied on first create.
     */
    private DvmClass[] inferDefaultParents(String className) {
        if (className == null || "java/lang/Object".equals(className) || "java/lang/Class".equals(className)) {
            return null;
        }
        if ("java/util/HashMap".equals(className)
                || "java/util/LinkedHashMap".equals(className)
                || "java/util/TreeMap".equals(className)
                || "java/util/concurrent/ConcurrentHashMap".equals(className)) {
            return new DvmClass[] {
                    resolveClass("java/util/AbstractMap"),
                    resolveClass("java/util/Map")
            };
        }
        if ("java/util/AbstractMap".equals(className)) {
            return new DvmClass[] { resolveClass("java/lang/Object"), resolveClass("java/util/Map") };
        }
        if ("java/util/HashSet".equals(className)
                || "java/util/LinkedHashSet".equals(className)
                || "java/util/TreeSet".equals(className)) {
            return new DvmClass[] {
                    resolveClass("java/util/AbstractSet"),
                    resolveClass("java/util/Set")
            };
        }
        if ("java/util/AbstractSet".equals(className)) {
            return new DvmClass[] { resolveClass("java/lang/Object"), resolveClass("java/util/Set") };
        }
        if ("java/util/ArrayList".equals(className)
                || "java/util/LinkedList".equals(className)
                || "java/util/Vector".equals(className)
                || "java/util/Stack".equals(className)
                || "java/util/concurrent/CopyOnWriteArrayList".equals(className)) {
            return new DvmClass[] {
                    resolveClass("java/util/AbstractList"),
                    resolveClass("java/util/List")
            };
        }
        if ("java/util/AbstractList".equals(className)) {
            return new DvmClass[] {
                    resolveClass("java/util/AbstractCollection"),
                    resolveClass("java/util/List")
            };
        }
        if ("java/util/AbstractCollection".equals(className)) {
            return new DvmClass[] {
                    resolveClass("java/lang/Object"),
                    resolveClass("java/util/Collection")
            };
        }
        return null;
    }

    @Override
    public final DvmClass resolveClass(String className, DvmClass... interfaceClasses) {
        className = className.replace('.', '/');
        int hash = this.hash(className);
        DvmClass dvmClass = classMap.get(hash);
        DvmClass superClass = null;
        if (interfaceClasses != null && interfaceClasses.length > 0) {
            superClass = interfaceClasses[0];
            interfaceClasses = Arrays.copyOfRange(interfaceClasses, 1, interfaceClasses.length);
        }
        if (dvmClass == null) {
            if (superClass == null && (interfaceClasses == null || interfaceClasses.length == 0)) {
                DvmClass[] inferred = inferDefaultParents(className);
                if (inferred != null && inferred.length > 0) {
                    superClass = inferred[0];
                    interfaceClasses = inferred.length > 1
                            ? Arrays.copyOfRange(inferred, 1, inferred.length)
                            : new DvmClass[0];
                }
            }
            if (dvmClassFactory != null) {
                dvmClass = dvmClassFactory.createClass(this, className, superClass, interfaceClasses);
            }
            if (dvmClass == null) {
                dvmClass = this.createClass(this, className, superClass, interfaceClasses);
            }
            DvmClass oldClass = classMap.put(hash, dvmClass);
            if (oldClass != null && !oldClass.getClassName().equals(className)) {
                throw new IllegalStateException("Hash collision: " + oldClass.getClassName() + " and " + className + " have the same hash=0x" + Integer.toHexString(hash));
            }
        }
        addGlobalObject(dvmClass);
        return dvmClass;
    }

    private DvmObject<?> dexCache;
    private DvmObject<?> dexFileCookie;
    private MemoryBlock mappedClassesDex;
    private MemoryBlock fakeArtDexFile;
    private MemoryBlock fakeArtDexLocation;

    /**
     * ART {@code java.lang.Class.dexCache}. One stub per VM; packers walk this
     * to find the loaded DexFile.
     */
    public DvmObject<?> getOrCreateDexCache() {
        if (dexCache == null) {
            dexCache = resolveClass("java/lang/DexCache").newObject("DexCache");
        }
        return dexCache;
    }

    /**
     * ART {@code dalvik.system.DexFile.mCookie} as {@code long[]}.
     *
     * Real ART O+ stores {@code [0]=OatFile*} (often null) and {@code [1+]=DexFile*}.
     * Pre-O and many native walkers treat {@code [0]} itself as {@code DexFile*}
     * and load {@code begin_} at {@code +8}. A null oat slot then becomes a
     * READ at address 0x8. Both slots therefore hold the same fake
     * {@code DexFile*} so either convention sees mapped {@code begin_}/{@code size_}.
     */
    public DvmObject<?> getOrCreateDexFileCookie() {
        if (dexFileCookie != null) {
            return dexFileCookie;
        }
        long dexFilePtr = allocateFakeArtDexFile();
        // Same pointer in both slots: oat-skip walkers use [1], [0]-as-DexFile* walkers use [0].
        dexFileCookie = new LongArray(this, new long[]{dexFilePtr, dexFilePtr});
        addObject(dexFileCookie, true, false);
        return dexFileCookie;
    }

    /**
     * Guest pointer to the fake ART {@code DexFile} (vptr / {@code begin_} /
     * {@code size_}), or 0 if this VM has no {@code classes.dex}.
     */
    public long getArtDexFilePointer() {
        getOrCreateDexFileCookie();
        return fakeArtDexFile != null ? fakeArtDexFile.getPointer().peer : 0L;
    }

    private long allocateFakeArtDexFile() {
        byte[] dex = unzip("classes.dex");
        if (dex == null || dex.length < 0x70 || dex[0] != 'd' || dex[1] != 'e' || dex[2] != 'x') {
            return 0L;
        }
        mappedClassesDex = emulator.getMemory().malloc(dex.length, true);
        mappedClassesDex.getPointer().write(0, dex, 0, dex.length);
        long begin = mappedClassesDex.getPointer().peer;
        String location = "/data/app/" + getPackageName() + "-1/base.apk";
        fakeArtDexLocation = emulator.getMemory().malloc(location.length() + 1, true);
        fakeArtDexLocation.getPointer().setString(0, location);
        // vptr, begin_, size_, libc++ location_, checksum, header_==begin_
        fakeArtDexFile = emulator.getMemory().malloc(0x80, true);
        fakeArtDexFile.getPointer().setLong(0, 0L);
        fakeArtDexFile.getPointer().setLong(8, begin);
        fakeArtDexFile.getPointer().setLong(16, dex.length);
        fakeArtDexFile.getPointer().setLong(24, fakeArtDexLocation.getPointer().peer);
        fakeArtDexFile.getPointer().setLong(32, location.length());
        fakeArtDexFile.getPointer().setLong(40, location.length());
        int checksum = (dex[8] & 0xff) | ((dex[9] & 0xff) << 8)
                | ((dex[10] & 0xff) << 16) | ((dex[11] & 0xff) << 24);
        fakeArtDexFile.getPointer().setInt(48, checksum);
        fakeArtDexFile.getPointer().setLong(56, begin);
        return fakeArtDexFile.getPointer().peer;
    }

    @Override
    public DvmClass createClass(BaseVM vm, String className, DvmClass superClass, DvmClass[] interfaceClasses) {
        return new DvmClass(vm, className, superClass, interfaceClasses);
    }

    final int addObject(DvmObject<?> object, boolean global, boolean weak) {
        int hash = object.hashCode();
        if (log.isDebugEnabled()) {
            log.debug("addObject hash=0x{}, global={}", Long.toHexString(hash), global);
        }
        Object value = object.getValue();
        if (value instanceof DvmAwareObject) {
            ((DvmAwareObject) value).initializeDvm(emulator, this, object);
        }
        if (global) {
            ObjRef old = weak ? weakGlobalObjectMap.get(hash) : globalObjectMap.get(hash);
            if (old == null) {
                old = new ObjRef(object, weak);
            } else {
                old.refCount++;
            }
            if (weak) {
                weakGlobalObjectMap.put(hash, old);
            } else {
                globalObjectMap.put(hash, old);
            }
        } else {
            localObjectMap.put(hash, new ObjRef(object, weak));
        }
        return hash;
    }

    @Override
    public final int addLocalObject(DvmObject<?> object) {
        if (object == null) {
            return JNI_NULL;
        }

        return addObject(object, false, false);
    }

    @Override
    public final int addGlobalObject(DvmObject<?> object) {
        if (object == null) {
            return JNI_NULL;
        }

        return addObject(object, true, false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T extends DvmObject<?>> T getObject(int hash) {
        ObjRef ref;
        if (localObjectMap.containsKey(hash)) {
            ref = localObjectMap.get(hash);
        } else if(globalObjectMap.containsKey(hash)) {
            ref = globalObjectMap.get(hash);
        } else {
            ref = weakGlobalObjectMap.get(hash);
        }
        return ref == null ? null : (T) ref.obj;
    }

    @Override
    public final DvmClass findClass(String className) {
        return classMap.get(this.hash(className));
    }

    final void deleteLocalRefs() {
        for (ObjRef ref : localObjectMap.values()) {
            ref.obj.onDeleteRef();
        }
        localObjectMap.clear();

        if (throwable != null) {
            throwable.onDeleteRef();
            throwable = null;
        }
    }

    final void checkVersion(int version) {
        if (version != JNI_VERSION_1_1 &&
                version != JNI_VERSION_1_2 &&
                version != JNI_VERSION_1_4 &&
                version != JNI_VERSION_1_6 &&
                version != JNI_VERSION_1_8) {
            if (log.isTraceEnabled()) {
                emulator.attach().debug("Illegal JNI version: 0x" + Integer.toHexString(version));
            }
            throw new IllegalStateException("Illegal JNI version: 0x" + Integer.toHexString(version));
        }
    }

    abstract byte[] loadLibraryData(Apk apk, String soName);

    @Override
    public LibraryFile findLibrary(String soName) {
        if (apk == null) {
            throw new UnsupportedOperationException();
        }

        ApkLibraryFile libraryFile = findLibrary(apk, soName);
        if (libraryFile == null) {
            File split = new File(apk.getParentFile(), emulator.is64Bit() ? "config.arm64_v8a.apk" : "config.armeabi_v7a.apk");
            if (split.canRead()) {
                libraryFile = findLibrary(ApkFactory.createApk(split), soName);
            }
        }
        return libraryFile;
    }

    @Override
    public final DalvikModule loadLibrary(String libname, boolean forceCallInit) {
        String soName = "lib" + libname + ".so";
        LibraryFile libraryFile = findLibrary(soName);
        if (libraryFile == null) {
            throw new IllegalStateException("load library failed: " + libname);
        }
        Module module = emulator.getMemory().load(libraryFile, forceCallInit);
        return new DalvikModule(this, module);
    }

    @Override
    public final DalvikModule loadLibrary(String libname, byte[] raw, boolean forceCallInit) {
        if (raw == null || raw.length == 0) {
            throw new IllegalArgumentException();
        }
        Module module = emulator.getMemory().load(new ElfLibraryRawFile(libname, raw, emulator.is64Bit()), forceCallInit);
        return new DalvikModule(this, module);
    }

    private ApkLibraryFile findLibrary(Apk apk, String soName) {
        byte[] libData = loadLibraryData(apk, soName);
        if (libData == null) {
            return null;
        }

        String packageName = getPackageName();
        return new ApkLibraryFile(this, apk, soName, libData, packageName == null ? apk.getPackageName() : packageName, emulator.is64Bit());
    }

    @Override
    public CertificateMeta[] getSignatures() {
        return apk == null ? null : apk.getSignatures();
    }

    @Override
    public String getPackageName() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config == null ? (apk == null ? null : apk.getPackageName()) : config.getAndroidPackageName(apk == null ? null : apk.getPackageName());
    }

    @Override
    public String getManifestXml() {
        return apk == null ? null : apk.getManifestXml();
    }

    @Override
    public byte[] openAsset(String fileName) {
        if (assetResolver != null) {
            byte[] bytes = assetResolver.resolveAsset(fileName);
            if (bytes != null) {
                return bytes;
            }
        }

        return apk == null ? null : apk.openAsset(fileName);
    }

    @Override
    public byte[] unzip(String path) {
        if (path.length() > 1 && path.charAt(0) == '/') {
            path = path.substring(1);
        }
        return apk == null ? null : apk.getFileData(path);
    }

    private AssetResolver assetResolver;

    @Override
    public void setAssetResolver(AssetResolver assetResolver) {
        this.assetResolver = assetResolver;
    }

    @Override
    public final String getVersionName() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config == null ? (apk == null ? null : apk.getVersionName()) : config.getVersionName(apk == null ? null : apk.getVersionName());
    }

    @Override
    public long getVersionCode() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config == null ? (apk == null ? 0 : apk.getVersionCode()) : config.getVersionCode(apk == null ? 0 : apk.getVersionCode());
    }

    @Override
    public final DalvikModule loadLibrary(File elfFile, boolean forceCallInit) {
        Module module = emulator.getMemory().load(new ElfLibraryFile(elfFile, emulator.is64Bit()), forceCallInit);
        return new DalvikModule(this, module);
    }

    @Override
    public final void printMemoryInfo() {
        System.gc();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();
        Map<Integer, ObjRef> map = new HashMap<>(globalObjectMap);
        for (Integer key : classMap.keySet()) {
            map.remove(key);
        }
        System.err.println("globalObjectSize=" + globalObjectMap.size() + ", localObjectSize=" + localObjectMap.size() + ", weakGlobalObjectSize=" + weakGlobalObjectMap.size() + ", classSize=" + classMap.size() + ", globalObjectSize=" + map.size());
        System.err.println("heap: " + memoryUsage(heap) + ", nonHeap: " + memoryUsage(nonHeap));
    }

    private String toMB(long memory) {
        return (memory * 100 / (1024 * 1024)) / 100F + "MB";
    }

    private String memoryUsage(MemoryUsage usage) {
        return "init=" + toMB(usage.getInit()) + ", used="
                + toMB(usage.getUsed()) + ", committed="
                + toMB(usage.getCommitted()) + ", max="
                + toMB(usage.getMax());
    }

    @Override
    public void callJNI_OnLoad(Emulator<?> emulator, Module module) {
        new DalvikModule(this, module).callJNI_OnLoad(emulator);
    }

    @Override
    public Emulator<?> getEmulator() {
        return emulator;
    }
}
