LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libelf
LOCAL_SRC_FILES := prebuilts/$(TARGET_ARCH_ABI)/libelf.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/prebuilts/include/
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := nativelibanalyzer
LOCAL_SHARED_LIBRARIES += libelf

LOCAL_CFLAGS := -O3 -ffast-math -std=c++14 -frtti -fexceptions # -g for VTune analysis with source comparison

ifeq ($(TARGET_ARCH_ABI),x86)
	LOCAL_CFLAGS += -mtune=atom -mssse3 -mfpmath=sse
endif

ifeq ($(TARGET_ARCH_ABI),x86_64)
    LOCAL_CFLAGS += -mtune=slm -maes -msse4.2
endif

LOCAL_LDLIBS := -llog

LOCAL_SRC_FILES := nativelibanalyzer.cpp nativelibanalyzer_jni.cpp # main.cpp

include $(BUILD_SHARED_LIBRARY)
