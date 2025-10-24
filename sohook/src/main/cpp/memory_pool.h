// Copyright (c) 2024 SoHook Memory Leak Detection
// Memory pool for fast allocation of memory_record_t

#ifndef SOHOOK_MEMORY_POOL_H
#define SOHOOK_MEMORY_POOL_H

#include "memory_tracker.h"
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 初始化内存池
 * @param malloc_func 用于分配 chunk 的 malloc 函数（可以是 original_malloc）
 */
void pool_init(void *(*malloc_func)(size_t));

/**
 * 从内存池分配一个 memory_record_t
 * @return 分配的 record 指针，失败返回 NULL
 */
memory_record_t *pool_alloc_record(void);

/**
 * 释放一个 memory_record_t 到内存池
 * @param record 要释放的 record
 * 注意：当前实现不回收，只是标记
 */
void pool_free_record(memory_record_t *record);

/**
 * 清理内存池，释放所有 chunk
 * @param free_func 用于释放 chunk 的 free 函数（可以是 original_free）
 */
void pool_cleanup(void (*free_func)(void *));

/**
 * 获取内存池统计信息
 * @param total_chunks 输出：总 chunk 数量
 * @param total_allocated 输出：总已分配 record 数量
 */
void pool_get_stats(size_t *total_chunks, size_t *total_allocated);

#ifdef __cplusplus
}
#endif

#endif  // SOHOOK_MEMORY_POOL_H
