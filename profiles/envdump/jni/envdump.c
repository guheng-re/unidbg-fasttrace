#define _GNU_SOURCE
#include <jni.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/auxv.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/statfs.h>
#include <sys/system_properties.h>
#include <sys/types.h>
#include <sys/utsname.h>
#include <unistd.h>

#define MAX_FILE (2 * 1024 * 1024)

static jbyteArray bytes_of(JNIEnv *env, const void *p, int n) {
    jbyteArray a;
    if (!env || n < 0) {
        return NULL;
    }
    a = (*env)->NewByteArray(env, n);
    if (!a) {
        return NULL;
    }
    if (n > 0 && p) {
        (*env)->SetByteArrayRegion(env, a, 0, n, (const jbyte *) p);
    }
    return a;
}

static jstring jstr(JNIEnv *env, const char *s) {
    if (!env || !s) {
        return NULL;
    }
    return (*env)->NewStringUTF(env, s);
}

JNIEXPORT jbyteArray JNICALL
Java_com_huiyan_envdump_Native_readFile(JNIEnv *env, jclass cls, jstring jpath) {
    const char *path;
    int fd;
    off_t sz;
    char *buf;
    jbyteArray out;
    ssize_t n;
    (void) cls;
    if (!jpath) {
        return NULL;
    }
    path = (*env)->GetStringUTFChars(env, jpath, NULL);
    if (!path) {
        return NULL;
    }
    fd = open(path, O_RDONLY | O_CLOEXEC);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    if (fd < 0) {
        return NULL;
    }
    sz = lseek(fd, 0, SEEK_END);
    if (sz <= 0 || sz > MAX_FILE) {
        /* proc/sysfs often report size 0 */
        lseek(fd, 0, SEEK_SET);
        buf = (char *) malloc(MAX_FILE);
        if (!buf) {
            close(fd);
            return NULL;
        }
        n = read(fd, buf, MAX_FILE);
        close(fd);
        if (n <= 0) {
            free(buf);
            return NULL;
        }
        out = bytes_of(env, buf, (int) n);
        free(buf);
        return out;
    }
    if (lseek(fd, 0, SEEK_SET) < 0) {
        close(fd);
        return NULL;
    }
    buf = (char *) malloc((size_t) sz + 1);
    if (!buf) {
        close(fd);
        return NULL;
    }
    n = read(fd, buf, (size_t) sz);
    close(fd);
    if (n < 0) {
        free(buf);
        return NULL;
    }
    out = bytes_of(env, buf, (int) n);
    free(buf);
    return out;
}

JNIEXPORT jobjectArray JNICALL
Java_com_huiyan_envdump_Native_listDir(JNIEnv *env, jclass cls, jstring jpath) {
    const char *path;
    DIR *d;
    struct dirent *e;
    char **names = NULL;
    int cap = 0, n = 0, i;
    jobjectArray arr;
    jclass strCls;
    (void) cls;
    if (!jpath) {
        return NULL;
    }
    path = (*env)->GetStringUTFChars(env, jpath, NULL);
    if (!path) {
        return NULL;
    }
    d = opendir(path);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    if (!d) {
        return NULL;
    }
    while ((e = readdir(d)) != NULL) {
        if (strcmp(e->d_name, ".") == 0 || strcmp(e->d_name, "..") == 0) {
            continue;
        }
        if (n >= cap) {
            cap = cap ? cap * 2 : 32;
            names = (char **) realloc(names, (size_t) cap * sizeof(char *));
            if (!names) {
                closedir(d);
                return NULL;
            }
        }
        names[n] = strdup(e->d_name);
        n++;
    }
    closedir(d);
    strCls = (*env)->FindClass(env, "java/lang/String");
    arr = (*env)->NewObjectArray(env, n, strCls, NULL);
    for (i = 0; i < n; i++) {
        (*env)->SetObjectArrayElement(env, arr, i, jstr(env, names[i]));
        free(names[i]);
    }
    free(names);
    return arr;
}

JNIEXPORT jstring JNICALL
Java_com_huiyan_envdump_Native_statJson(JNIEnv *env, jclass cls, jstring jpath) {
    const char *path;
    struct stat st;
    char buf[256];
    (void) cls;
    if (!jpath) {
        return NULL;
    }
    path = (*env)->GetStringUTFChars(env, jpath, NULL);
    if (!path) {
        return NULL;
    }
    if (stat(path, &st) != 0) {
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return NULL;
    }
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    snprintf(buf, sizeof(buf),
            "{\"size\":%lld,\"mtime\":%lld,\"mode\":%u,\"uid\":%u,\"gid\":%u}",
            (long long) st.st_size, (long long) st.st_mtime,
            (unsigned) st.st_mode, (unsigned) st.st_uid, (unsigned) st.st_gid);
    return jstr(env, buf);
}

JNIEXPORT jstring JNICALL
Java_com_huiyan_envdump_Native_statfsJson(JNIEnv *env, jclass cls, jstring jpath) {
    const char *path;
    struct statfs st;
    char buf[320];
    (void) cls;
    if (!jpath) {
        return NULL;
    }
    path = (*env)->GetStringUTFChars(env, jpath, NULL);
    if (!path) {
        return NULL;
    }
    if (statfs(path, &st) != 0) {
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return NULL;
    }
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    snprintf(buf, sizeof(buf),
            "{\"bsize\":%lld,\"blocks\":%lld,\"bfree\":%lld,\"bavail\":%lld,\"files\":%lld,\"ffree\":%lld}",
            (long long) st.f_bsize, (long long) st.f_blocks, (long long) st.f_bfree,
            (long long) st.f_bavail, (long long) st.f_files, (long long) st.f_ffree);
    return jstr(env, buf);
}

static void esc(char *dst, size_t cap, const char *src) {
    size_t o = 0;
    if (!dst || cap == 0) {
        return;
    }
    while (src && *src && o + 2 < cap) {
        if (*src == '"' || *src == '\\') {
            dst[o++] = '\\';
        }
        if (*src == '\n' || *src == '\r') {
            src++;
            continue;
        }
        dst[o++] = *src++;
    }
    dst[o] = 0;
}

JNIEXPORT jstring JNICALL
Java_com_huiyan_envdump_Native_unameJson(JNIEnv *env, jclass cls) {
    struct utsname u;
    char s[128], n[128], r[256], v[512], m[128], buf[1200];
    (void) cls;
    if (uname(&u) != 0) {
        return NULL;
    }
    esc(s, sizeof(s), u.sysname);
    esc(n, sizeof(n), u.nodename);
    esc(r, sizeof(r), u.release);
    esc(v, sizeof(v), u.version);
    esc(m, sizeof(m), u.machine);
    snprintf(buf, sizeof(buf),
            "{\"sysname\":\"%s\",\"nodename\":\"%s\",\"release\":\"%s\",\"version\":\"%s\",\"machine\":\"%s\"}",
            s, n, r, v, m);
    return jstr(env, buf);
}

JNIEXPORT jint JNICALL
Java_com_huiyan_envdump_Native_getpid(JNIEnv *env, jclass cls) {
    (void) env;
    (void) cls;
    return (jint) getpid();
}

JNIEXPORT jint JNICALL
Java_com_huiyan_envdump_Native_getppid(JNIEnv *env, jclass cls) {
    (void) env;
    (void) cls;
    return (jint) getppid();
}

JNIEXPORT jint JNICALL
Java_com_huiyan_envdump_Native_getuid(JNIEnv *env, jclass cls) {
    (void) env;
    (void) cls;
    return (jint) getuid();
}

JNIEXPORT jint JNICALL
Java_com_huiyan_envdump_Native_getgid(JNIEnv *env, jclass cls) {
    (void) env;
    (void) cls;
    return (jint) getgid();
}

JNIEXPORT jstring JNICALL
Java_com_huiyan_envdump_Native_getprop(JNIEnv *env, jclass cls, jstring jkey) {
    const char *key;
    char val[PROP_VALUE_MAX];
    int n;
    (void) cls;
    if (!jkey) {
        return NULL;
    }
    key = (*env)->GetStringUTFChars(env, jkey, NULL);
    if (!key) {
        return NULL;
    }
    n = __system_property_get(key, val);
    (*env)->ReleaseStringUTFChars(env, jkey, key);
    if (n <= 0) {
        return NULL;
    }
    val[PROP_VALUE_MAX - 1] = 0;
    return jstr(env, val);
}

JNIEXPORT jobjectArray JNICALL
Java_com_huiyan_envdump_Native_environ(JNIEnv *env, jclass cls) {
    extern char **environ;
    int n = 0, i;
    jclass strCls;
    jobjectArray arr;
    (void) cls;
    if (environ) {
        while (environ[n]) {
            n++;
        }
    }
    strCls = (*env)->FindClass(env, "java/lang/String");
    arr = (*env)->NewObjectArray(env, n, strCls, NULL);
    for (i = 0; i < n; i++) {
        (*env)->SetObjectArrayElement(env, arr, i, jstr(env, environ[i]));
    }
    return arr;
}

JNIEXPORT jbyteArray JNICALL
Java_com_huiyan_envdump_Native_readUrandom(JNIEnv *env, jclass cls, jint n) {
    int fd;
    char *buf;
    jbyteArray out;
    ssize_t r;
    (void) cls;
    if (n <= 0 || n > 64) {
        return NULL;
    }
    fd = open("/dev/urandom", O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        return NULL;
    }
    buf = (char *) malloc((size_t) n);
    if (!buf) {
        close(fd);
        return NULL;
    }
    r = read(fd, buf, (size_t) n);
    close(fd);
    if (r != n) {
        free(buf);
        return NULL;
    }
    out = bytes_of(env, buf, n);
    free(buf);
    return out;
}

JNIEXPORT jlong JNICALL
Java_com_huiyan_envdump_Native_getauxval(JNIEnv *env, jclass cls, jint type) {
    (void) env;
    (void) cls;
    return (jlong) getauxval((unsigned long) type);
}

JNIEXPORT jstring JNICALL
Java_com_huiyan_envdump_Native_rlimitJson(JNIEnv *env, jclass cls, jint res) {
    struct rlimit rl;
    char buf[128];
    (void) cls;
    if (getrlimit((int) res, &rl) != 0) {
        return NULL;
    }
    snprintf(buf, sizeof(buf), "{\"soft\":%lld,\"hard\":%lld}",
            (long long) rl.rlim_cur, (long long) rl.rlim_max);
    return jstr(env, buf);
}
