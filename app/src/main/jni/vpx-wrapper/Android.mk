LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := vpx-wrapper
LOCAL_C_INCLUDES := libvpx libvpx/third_party/libyuv/include
LOCAL_STATIC_LIBRARIES := vpx
LOCAL_ARM_NEON := true
LOCAL_LDLIBS += -ljnigraphics

LOCAL_SRC_FILES := vpx-wrapper.c
LOCAL_SRC_FILES += \
	../libvpx/third_party/libyuv/source/convert_argb.cc \
	../libvpx/third_party/libyuv/source/row_common.cc \
	../libvpx/third_party/libyuv/source/row_any.cc \
	../libvpx/third_party/libyuv/source/row_neon.cc \
	../libvpx/third_party/libyuv/source/planar_functions.cc \
	../libvpx/third_party/libyuv/source/cpu_id.cc \


include $(BUILD_SHARED_LIBRARY)
