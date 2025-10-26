// Copyright (c) 2024 SoHook Memory Leak Detection
// JNI bindings for C++ memory test functions (Static Registration)

#include <jni.h>
#include "test_memory_cpp.h"

// JNI静态注册：Java_包名_类名_方法名
// 包名：com.sohook.test
// 类名：TestMemoryCpp

// 测试 new/delete
JNIEXPORT jlong JNICALL
Java_com_sohook_test_TestMemoryCpp_nativeNew(JNIEnv* env, jclass clazz, jlong size) {
    (void)env;
    (void)clazz;
    void* ptr = test_cpp_new((size_t)size);
    return (jlong)(uintptr_t)ptr;
}

JNIEXPORT void JNICALL
Java_com_sohook_test_TestMemoryCpp_nativeDelete(JNIEnv* env, jclass clazz, jlong ptr) {
    (void)env;
    (void)clazz;
    test_cpp_delete((void*)(uintptr_t)ptr);
}

// 测试 new[]/delete[]
JNIEXPORT jlong JNICALL
Java_com_sohook_test_TestMemoryCpp_nativeNewArray(JNIEnv* env, jclass clazz, jlong size) {
    (void)env;
    (void)clazz;
    void* ptr = test_cpp_new_array((size_t)size);
    return (jlong)(uintptr_t)ptr;
}

JNIEXPORT void JNICALL
Java_com_sohook_test_TestMemoryCpp_nativeDeleteArray(JNIEnv* env, jclass clazz, jlong ptr) {
    (void)env;
    (void)clazz;
    test_cpp_delete_array((void*)(uintptr_t)ptr);
}

// 测试对象
JNIEXPORT jlong JNICALL
Java_com_sohook_test_TestMemoryCpp_nativeNewObject(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    void* ptr = test_cpp_new_object();
    return (jlong)(uintptr_t)ptr;
}

JNIEXPORT void JNICALL
Java_com_sohook_test_TestMemoryCpp_nativeDeleteObject(JNIEnv* env, jclass clazz, jlong ptr) {
    (void)env;
    (void)clazz;
    test_cpp_delete_object((void*)(uintptr_t)ptr);
}

// 测试对象数组
JNIEXPORT jlong JNICALL
Java_com_sohook_test_TestMemoryCpp_nativeNewObjectArray(JNIEnv* env, jclass clazz, jint count) {
    (void)env;
    (void)clazz;
    void* ptr = test_cpp_new_object_array(count);
    return (jlong)(uintptr_t)ptr;
}

JNIEXPORT void JNICALL
Java_com_sohook_test_TestMemoryCpp_nativeDeleteObjectArray(JNIEnv* env, jclass clazz, jlong ptr) {
    (void)env;
    (void)clazz;
    test_cpp_delete_object_array((void*)(uintptr_t)ptr);
}

// 测试多次分配
JNIEXPORT jlong JNICALL
Java_com_sohook_test_TestMemoryCpp_nativeNewMultiple(JNIEnv* env, jclass clazz, jint count, jlong size) {
    (void)env;
    (void)clazz;
    void* ptr = test_cpp_new_multiple(count, (size_t)size);
    return (jlong)(uintptr_t)ptr;
}

// 测试多个对象
JNIEXPORT jlongArray JNICALL
Java_com_sohook_test_TestMemoryCpp_nativeNewObjects(JNIEnv* env, jclass clazz, jint count) {
    (void)clazz;
    void** ptrs = test_cpp_new_objects(count);
    
    jlongArray result = (*env)->NewLongArray(env, count);
    if (result == NULL) {
        return NULL;
    }
    
    jlong* elements = (*env)->GetLongArrayElements(env, result, NULL);
    for (int i = 0; i < count; i++) {
        elements[i] = (jlong)(uintptr_t)ptrs[i];
    }
    (*env)->ReleaseLongArrayElements(env, result, elements, 0);
    
    // 释放指针数组本身（但不释放对象）
    test_cpp_delete((void*)ptrs);
    
    return result;
}

JNIEXPORT void JNICALL
Java_com_sohook_test_TestMemoryCpp_nativeDeleteObjects(JNIEnv* env, jclass clazz, jlongArray ptrs) {
    (void)clazz;
    if (ptrs == NULL) {
        return;
    }
    
    jsize count = (*env)->GetArrayLength(env, ptrs);
    jlong* elements = (*env)->GetLongArrayElements(env, ptrs, NULL);
    
    void** ptr_array = (void**)test_cpp_new(count * sizeof(void*));
    for (int i = 0; i < count; i++) {
        ptr_array[i] = (void*)(uintptr_t)elements[i];
    }
    
    test_cpp_delete_objects(ptr_array, count);
    
    (*env)->ReleaseLongArrayElements(env, ptrs, elements, 0);
}

// 测试泄漏
JNIEXPORT jlong JNICALL
Java_com_sohook_test_TestMemoryCpp_nativeLeakNew(JNIEnv* env, jclass clazz, jlong size) {
    (void)env;
    (void)clazz;
    void* ptr = test_cpp_leak_new((size_t)size);
    return (jlong)(uintptr_t)ptr;
}

JNIEXPORT jlong JNICALL
Java_com_sohook_test_TestMemoryCpp_nativeLeakObject(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    void* ptr = test_cpp_leak_object();
    return (jlong)(uintptr_t)ptr;
}

JNIEXPORT jlong JNICALL
Java_com_sohook_test_TestMemoryCpp_nativeLeakObjectArray(JNIEnv* env, jclass clazz, jint count) {
    (void)env;
    (void)clazz;
    void* ptr = test_cpp_leak_object_array(count);
    return (jlong)(uintptr_t)ptr;
}
