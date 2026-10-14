package com.github.unidbg.spi;

import com.github.unidbg.LibraryResolver;
import com.github.unidbg.Module;
import com.github.unidbg.ModuleListener;
import com.github.unidbg.Symbol;
import com.github.unidbg.hook.HookListener;
import com.github.unidbg.pointer.UnidbgPointer;

import java.io.File;
import java.util.Collection;
import java.util.Map;

@SuppressWarnings("unused")
public interface Loader {

    void setLibraryResolver(LibraryResolver libraryResolver);

    void disableCallInitFunction();

    void setCallInitFunction(boolean callInit);

    Module load(File elfFile);
    Module load(File elfFile, boolean forceCallInit);

    Module load(LibraryFile libraryFile);
    Module load(LibraryFile libraryFile, boolean forceCallInit);

    /**
     * Map PT_LOAD into emulator memory without linking: no DT_NEEDED, no reloc, no
     * {@code .init_array} / JNI_OnLoad. The module is registered in
     * {@link #getLoadedModules()} so the next {@code /proc/self/maps} open includes it.
     * Already-loaded SONAMEs are returned as-is (will not replace libc etc.).
     */
    default Module mapNoLink(LibraryFile libraryFile) {
        throw new UnsupportedOperationException("mapNoLink");
    }

    Module findModuleByAddress(long address);
    Module findModule(String name);

    Module dlopen(String filename);
    Module dlopen(String filename, boolean calInit);
    boolean dlclose(long handle);
    Symbol dlsym(long handle, String symbol);

    void addModuleListener(ModuleListener listener);

    void addHookListener(HookListener listener);

    Collection<Module> getLoadedModules();

    String getMaxLengthLibraryName();
    long getMaxSizeOfLibrary();

    /**
     * 加载虚拟模块
     */
    Module loadVirtualModule(String name, final Map<String, UnidbgPointer> symbols);

}
