// Copyright (c) 2024 SoHook Memory Leak Detection
// JNI wrapper for test memory library

#include <jni.h>
#include <android/log.h>
#include "test_memory.h"

#define LOG_TAG "TestMemoryJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// JNI: 分配内存
JNIEXPORT jlong JNICALL
Java_com_sohook_TestMemoryHelper_nativeAlloc(JNIEnv *env, jclass clazz, jlong size) {
    (void)env;
    (void)clazz;
    
    void* ptr = test_malloc((size_t)size);
    return (jlong)(uintptr_t)ptr;
}

// JNI: 释放内存
JNIEXPORT void JNICALL
Java_com_sohook_TestMemoryHelper_nativeFree(JNIEnv *env, jclass clazz, jlong ptr) {
    (void)env;
    (void)clazz;
    
    test_free((void*)(uintptr_t)ptr);
}

// JNI: 分配多次
JNIEXPORT jlong JNICALL
Java_com_sohook_TestMemoryHelper_nativeAllocMultiple(JNIEnv *env, jclass clazz, jint count, jlong size) {
    (void)env;
    (void)clazz;
    
    void* ptr = test_alloc_multiple((int)count, (size_t)size);
    return (jlong)(uintptr_t)ptr;
}

// JNI: 故意泄漏
JNIEXPORT jlong JNICALL
Java_com_sohook_TestMemoryHelper_nativeLeakMemory(JNIEnv *env, jclass clazz, jlong size) {
    (void)env;
    (void)clazz;
    
    void* ptr = test_leak_memory((size_t)size);
    return (jlong)(uintptr_t)ptr;
}

// JNI: calloc
JNIEXPORT jlong JNICALL
Java_com_sohook_TestMemoryHelper_nativeCalloc(JNIEnv *env, jclass clazz, jlong nmemb, jlong size) {
    (void)env;
    (void)clazz;
    
    void* ptr = test_calloc((size_t)nmemb, (size_t)size);
    return (jlong)(uintptr_t)ptr;
}

// JNI: realloc
JNIEXPORT jlong JNICALL
Java_com_sohook_TestMemoryHelper_nativeRealloc(JNIEnv *env, jclass clazz, jlong ptr, jlong size) {
    (void)env;
    (void)clazz;
    
    void* new_ptr = test_realloc((void*)(uintptr_t)ptr, (size_t)size);
    return (jlong)(uintptr_t)new_ptr;
}

// JNI_OnLoad
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    
    LOGD("TestMemory library loaded");
    return JNI_VERSION_1_6;
}
