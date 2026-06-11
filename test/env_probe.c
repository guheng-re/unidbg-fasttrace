#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/system_properties.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/utsname.h>
#include <time.h>
#include <unistd.h>

#ifndef __NR_gettid
#if defined(__aarch64__)
#define __NR_gettid 178
#else
#define __NR_gettid 224
#endif
#endif

#ifndef __NR_getrandom
#if defined(__aarch64__)
#define __NR_getrandom 278
#else
#define __NR_getrandom 384
#endif
#endif

#define PROBE_RANDOM_SIZE 16
#define PROBE_DEVICE_RANDOM_SIZE 4
#define PROBE_TEXT_SIZE 512
#define PROBE_ESCAPED_TEXT_SIZE 2048
#define PROBE_PROP_TEXT_SIZE 128

typedef struct probe_report {
    char *out;
    size_t out_size;
    size_t used;
    int truncated;
} probe_report_t;

typedef struct property_callback_result {
    char name[PROBE_PROP_TEXT_SIZE];
    char value[PROBE_PROP_TEXT_SIZE];
    uint32_t serial;
} property_callback_result_t;

static void appendf(probe_report_t *report, const char *format, ...) {
    if (report->out_size == 0 || report->used >= report->out_size) {
        report->truncated = 1;
        return;
    }

    va_list ap;
    va_start(ap, format);
    int written = vsnprintf(report->out + report->used, report->out_size - report->used, format, ap);
    va_end(ap);

    if (written < 0) {
        return;
    }
    if ((size_t) written >= report->out_size - report->used) {
        report->used = report->out_size - 1;
        report->out[report->used] = '\0';
        report->truncated = 1;
        return;
    }
    report->used += (size_t) written;
}

static void copy_text(char *dst, size_t dst_size, const char *src) {
    if (dst_size == 0) {
        return;
    }
    if (src == NULL) {
        dst[0] = '\0';
        return;
    }
    snprintf(dst, dst_size, "%s", src);
}

static void bytes_to_hex(const uint8_t *data, size_t data_size, char *out, size_t out_size) {
    static const char hex[] = "0123456789abcdef";
    if (out_size == 0) {
        return;
    }

    size_t pos = 0;
    for (size_t i = 0; i < data_size && pos + 2 < out_size; i++) {
        out[pos++] = hex[(data[i] >> 4) & 0xf];
        out[pos++] = hex[data[i] & 0xf];
    }
    out[pos] = '\0';
}

static void escape_text(const char *src, ssize_t src_size, char *out, size_t out_size) {
    if (out_size == 0) {
        return;
    }

    size_t pos = 0;
    for (ssize_t i = 0; i < src_size && pos + 1 < out_size; i++) {
        unsigned char ch = (unsigned char) src[i];
        const char *replacement = NULL;

        if (ch == '\n') {
            replacement = "\\n";
        } else if (ch == '\r') {
            replacement = "\\r";
        } else if (ch == '\t') {
            replacement = "\\t";
        } else if (ch == '\\') {
            replacement = "\\\\";
        }

        if (replacement != NULL) {
            size_t len = strlen(replacement);
            if (pos + len >= out_size) {
                break;
            }
            memcpy(out + pos, replacement, len);
            pos += len;
        } else if (ch >= 0x20 && ch <= 0x7e) {
            out[pos++] = (char) ch;
        } else {
            if (pos + 4 >= out_size) {
                break;
            }
            snprintf(out + pos, out_size - pos, "\\x%02x", ch);
            pos += 4;
        }
    }
    out[pos] = '\0';
}

static void append_hex_read(probe_report_t *report, const char *key, const char *path, size_t count) {
    uint8_t buffer[PROBE_RANDOM_SIZE];
    char hex[PROBE_RANDOM_SIZE * 2 + 1];
    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        appendf(report, "%s.error=open:%d\n", key, errno);
        return;
    }

    ssize_t read_count = read(fd, buffer, count);
    int saved_errno = errno;
    close(fd);

    if (read_count < 0) {
        appendf(report, "%s.error=read:%d\n", key, saved_errno);
        return;
    }

    bytes_to_hex(buffer, (size_t) read_count, hex, sizeof(hex));
    appendf(report, "%s=%s\n", key, hex);
}

static void append_text_file(probe_report_t *report, const char *key, const char *path) {
    char buffer[PROBE_TEXT_SIZE];
    char escaped[PROBE_ESCAPED_TEXT_SIZE];
    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        appendf(report, "%s.error=open:%d\n", key, errno);
        return;
    }

    ssize_t read_count = read(fd, buffer, sizeof(buffer) - 1);
    int saved_errno = errno;
    close(fd);

    if (read_count < 0) {
        appendf(report, "%s.error=read:%d\n", key, saved_errno);
        return;
    }

    buffer[read_count] = '\0';
    escape_text(buffer, read_count, escaped, sizeof(escaped));
    appendf(report, "%s=%s\n", key, escaped);
}

static void property_read_callback(void *cookie, const char *name, const char *value, uint32_t serial) {
    property_callback_result_t *result = (property_callback_result_t *) cookie;
    copy_text(result->name, sizeof(result->name), name);
    copy_text(result->value, sizeof(result->value), value);
    result->serial = serial;
}

static void append_process(probe_report_t *report) {
    appendf(report, "process.pid=%d\n", getpid());
    appendf(report, "process.ppid=%d\n", getppid());
    appendf(report, "process.tid=%ld\n", syscall(__NR_gettid));
    appendf(report, "process.uid=%d\n", getuid());
    appendf(report, "process.euid=%d\n", geteuid());
    appendf(report, "process.gid=%d\n", getgid());
    appendf(report, "process.egid=%d\n", getegid());
}

static void append_time(probe_report_t *report) {
    struct timeval tv;
    struct timespec realtime;
    struct timespec monotonic;

    if (gettimeofday(&tv, NULL) == 0) {
        appendf(report, "time.gettimeofday.sec=%lld\n", (long long) tv.tv_sec);
        appendf(report, "time.gettimeofday.usec=%lld\n", (long long) tv.tv_usec);
    } else {
        appendf(report, "time.gettimeofday.error=%d\n", errno);
    }

    if (clock_gettime(CLOCK_REALTIME, &realtime) == 0) {
        appendf(report, "time.clock_realtime.sec=%lld\n", (long long) realtime.tv_sec);
        appendf(report, "time.clock_realtime.nsec=%lld\n", (long long) realtime.tv_nsec);
    } else {
        appendf(report, "time.clock_realtime.error=%d\n", errno);
    }

    if (clock_gettime(CLOCK_MONOTONIC, &monotonic) == 0) {
        appendf(report, "time.clock_monotonic.sec=%lld\n", (long long) monotonic.tv_sec);
        appendf(report, "time.clock_monotonic.nsec=%lld\n", (long long) monotonic.tv_nsec);
    } else {
        appendf(report, "time.clock_monotonic.error=%d\n", errno);
    }
}

static void append_random(probe_report_t *report) {
    uint8_t buffer[PROBE_RANDOM_SIZE];
    char hex[PROBE_RANDOM_SIZE * 2 + 1];
    ssize_t read_count = syscall(__NR_getrandom, buffer, sizeof(buffer), 0);
    if (read_count < 0) {
        appendf(report, "random.getrandom.error=%d\n", errno);
    } else {
        bytes_to_hex(buffer, (size_t) read_count, hex, sizeof(hex));
        appendf(report, "random.getrandom=%s\n", hex);
    }

    append_hex_read(report, "random.random", "/dev/random", PROBE_DEVICE_RANDOM_SIZE);
    append_hex_read(report, "random.urandom", "/dev/urandom", PROBE_DEVICE_RANDOM_SIZE);
    append_hex_read(report, "random.srandom", "/dev/srandom", PROBE_DEVICE_RANDOM_SIZE);
}

static void append_uname(probe_report_t *report) {
    struct utsname name;
    if (uname(&name) != 0) {
        appendf(report, "uname.error=%d\n", errno);
        return;
    }

    appendf(report, "uname.sysname=%s\n", name.sysname);
    appendf(report, "uname.nodename=%s\n", name.nodename);
    appendf(report, "uname.release=%s\n", name.release);
    appendf(report, "uname.version=%s\n", name.version);
    appendf(report, "uname.machine=%s\n", name.machine);
#ifdef _GNU_SOURCE
    appendf(report, "uname.domainname=%s\n", name.domainname);
#endif
}

static void append_properties(probe_report_t *report) {
    char value[PROP_VALUE_MAX];

    memset(value, 0, sizeof(value));
    int len = __system_property_get("ro.hardware", value);
    appendf(report, "property.ro.hardware=%s\n", len > 0 ? value : "");

    memset(value, 0, sizeof(value));
    len = __system_property_get("ro.build.version.sdk", value);
    appendf(report, "property.ro.build.version.sdk=%s\n", len > 0 ? value : "");

    memset(value, 0, sizeof(value));
    len = __system_property_get("ro.product.model", value);
    appendf(report, "property.ro.product.model=%s\n", len > 0 ? value : "");

    const prop_info *pi = __system_property_find("ro.build.version.sdk");
    appendf(report, "property.find.ro.build.version.sdk=%d\n", pi != NULL ? 1 : 0);
    if (pi != NULL) {
        property_callback_result_t callback_result;
        memset(&callback_result, 0, sizeof(callback_result));
        __system_property_read_callback(pi, property_read_callback, &callback_result);
        appendf(report, "property.callback.name=%s\n", callback_result.name);
        appendf(report, "property.callback.value=%s\n", callback_result.value);
        appendf(report, "property.callback.serial=%u\n", callback_result.serial);
    }
}

__attribute__((visibility("default")))
int trace_env_probe(char *out, size_t out_size) {
    if (out == NULL || out_size == 0) {
        return -1;
    }

    probe_report_t report;
    report.out = out;
    report.out_size = out_size;
    report.used = 0;
    report.truncated = 0;
    report.out[0] = '\0';

    append_process(&report);
    append_time(&report);
    append_random(&report);
    append_uname(&report);
    append_text_file(&report, "file.proc_cpuinfo", "/proc/cpuinfo");
    append_text_file(&report, "file.proc_meminfo", "/proc/meminfo");
    append_text_file(&report, "file.proc_version", "/proc/version");
    append_properties(&report);

    return report.truncated ? -2 : (int) report.used;
}
