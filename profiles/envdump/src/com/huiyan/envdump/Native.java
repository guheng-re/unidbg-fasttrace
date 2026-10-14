package com.huiyan.envdump;

public final class Native {
    static {
        System.loadLibrary("envdump");
    }

    private Native() {
    }

    public static native byte[] readFile(String path);

    public static native String[] listDir(String path);

    public static native String statJson(String path);

    public static native String statfsJson(String path);

    public static native String unameJson();

    public static native int getpid();

    public static native int getppid();

    public static native int getuid();

    public static native int getgid();

    public static native String getprop(String key);

    public static native String[] environ();

    public static native byte[] readUrandom(int n);

    public static native long getauxval(int type);

    public static native String rlimitJson(int resource);
}
