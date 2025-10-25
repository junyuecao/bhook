// Copyright (c) 2024 SoHook Memory Leak Detection
// Test memory allocation library implementation

#include "test_memory.h"
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define LOG_TAG "TestMemory"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// 分配内存
void* test_malloc(size_t size) {
    LOGD("test_malloc: allocating %zu bytes", size);
    void* ptr = malloc(size);
    LOGD("test_malloc: allocated at %p", ptr);
    return ptr;
}

// 释放内存
void test_free(void* ptr) {
    LOGD("test_free: freeing %p", ptr);
    free(ptr);
}

// 重新分配内存
void* test_realloc(void* ptr, size_t size) {
    LOGD("test_realloc: reallocating %p to %zu bytes", ptr, size);
    void* new_ptr = realloc(ptr, size);
    LOGD("test_realloc: reallocated to %p", new_ptr);
    return new_ptr;
}

// 分配并清零内存
void* test_calloc(size_t nmemb, size_t size) {
    LOGD("test_calloc: allocating %zu * %zu bytes", nmemb, size);
    void* ptr = calloc(nmemb, size);
    LOGD("test_calloc: allocated at %p", ptr);
    return ptr;
}

// 分配多次
void* test_alloc_multiple(int count, size_t size) {
    LOGI("test_alloc_multiple: allocating %d times, %zu bytes each", count, size);
    void* last_ptr = NULL;
    
    for (int i = 0; i < count; i++) {
        last_ptr = malloc(size);
        if (last_ptr) {
            // 写入一些数据，确保内存被使用
            memset(last_ptr, i & 0xFF, size);
        }
    }
    
    LOGI("test_alloc_multiple: completed, last ptr = %p", last_ptr);
    return last_ptr;
}

// 释放多个指针
void test_free_multiple(void** ptrs, int count) {
    LOGI("test_free_multiple: freeing %d pointers", count);
    
    if (ptrs == NULL) {
        LOGD("test_free_multiple: ptrs is NULL");
        return;
    }
    
    for (int i = 0; i < count; i++) {
        if (ptrs[i] != NULL) {
            free(ptrs[i]);
            ptrs[i] = NULL;
        }
    }
    
    LOGI("test_free_multiple: completed");
}

// 故意泄漏内存
void* test_leak_memory(size_t size) {
    LOGI("test_leak_memory: intentionally leaking %zu bytes", size);
    void* ptr = malloc(size);
    LOGI("test_leak_memory: leaked at %p (DO NOT FREE)", ptr);
    // 故意不释放，用于测试泄漏检测
    return ptr;
}
