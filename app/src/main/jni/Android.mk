LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

include libvpx/build/make/Android.mk
include libvpx/third_party/libwebm/Android.mk
include pr0-webm/Android.mk
