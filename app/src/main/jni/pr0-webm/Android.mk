LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

TARGET_PLATFORM := android-14

LOCAL_MODULE := pr0-webm-jni
LOCAL_SRC_FILES := pr0-webm-jni.c
LOCAL_C_INCLUDES := libvpx/vpx
LOCAL_STATIC_LIBRARIES := vpx

include $(BUILD_SHARED_LIBRARY)
