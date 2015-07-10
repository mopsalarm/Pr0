LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

TARGET_PLATFORM := android-14

LOCAL_MODULE := vpx-wrapper
LOCAL_SRC_FILES := vpx-wrapper.c
LOCAL_C_INCLUDES := libvpx/vpx
LOCAL_STATIC_LIBRARIES := vpx jnigraphics

include $(BUILD_SHARED_LIBRARY)
