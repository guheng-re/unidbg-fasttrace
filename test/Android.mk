LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := trace_env_probe
LOCAL_SRC_FILES := env_probe.c
LOCAL_CFLAGS += -std=c11 -Wall -Wextra -Wno-unused-parameter
include $(BUILD_SHARED_LIBRARY)
