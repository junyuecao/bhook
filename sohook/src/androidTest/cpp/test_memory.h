// Copyright (c) 2024 SoHook Memory Leak Detection
// Test memory allocation library header

#ifndef SOHOOK_TEST_MEMORY_H
#define SOHOOK_TEST_MEMORY_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 分配指定大小的内存
 * @param size 要分配的字节数
 * @return 分配的内存指针，失败返回NULL
 */
void* test_malloc(size_t size);

/**
 * 释放之前分配的内存
 * @param ptr 要释放的内存指针
 */
void test_free(void* ptr);

/**
 * 重新分配内存
 * @param ptr 原内存指针
 * @param size 新的大小
 * @return 新的内存指针
 */
void* test_realloc(void* ptr, size_t size);

/**
 * 分配并清零内存
 * @param nmemb 元素个数
 * @param size 每个元素的大小
 * @return 分配的内存指针
 */
void* test_calloc(size_t nmemb, size_t size);

/**
 * 分配指定次数和大小的内存（用于测试）
 * @param count 分配次数
 * @param size 每次分配的大小
 * @return 最后一次分配的指针
 */
void* test_alloc_multiple(int count, size_t size);

/**
 * 释放多个指针（用于测试）
 * @param ptrs 指针数组
 * @param count 指针数量
 */
void test_free_multiple(void** ptrs, int count);

/**
 * 故意泄漏内存（用于测试泄漏检测）
 * @param size 泄漏的大小
 * @return 泄漏的内存指针（不应该被释放）
 */
void* test_leak_memory(size_t size);

#ifdef __cplusplus
}
#endif

#endif // SOHOOK_TEST_MEMORY_H
