LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

include libvpx/build/make/Android.mk
include vpx-wrapper/Android.mk
