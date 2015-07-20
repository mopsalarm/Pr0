LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := coffeecatch
LOCAL_SRC_FILES := coffeecatch/coffeecatch.c coffeecatch/coffeejni.c

include $(BUILD_STATIC_LIBRARY)
