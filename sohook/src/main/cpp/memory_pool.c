// Copyright (c) 2024 SoHook Memory Leak Detection
// Memory pool implementation

#include "memory_pool.h"

#include <android/log.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "MemoryPool"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// 内存池配置
#define POOL_CHUNK_SIZE 1024  // 每个块包含 1024 个 record

// Chunk 结构
typedef struct pool_chunk {
  memory_record_t records[POOL_CHUNK_SIZE];
  _Atomic uint32_t allocated;  // 已分配的数量
  struct pool_chunk *next;
} pool_chunk_t;

// 全局状态
static pool_chunk_t *g_pool_head = NULL;
static pthread_mutex_t g_pool_mutex = PTHREAD_MUTEX_INITIALIZER;
static void *(*g_malloc_func)(size_t) = NULL;

// 初始化内存池
void pool_init(void *(*malloc_func)(size_t)) {
  g_malloc_func = malloc_func ? malloc_func : malloc;
  LOGI("Memory pool initialized");
}

// 从内存池分配一个 record
memory_record_t *pool_alloc_record(void) {
  pthread_mutex_lock(&g_pool_mutex);
  
  // 查找有空闲空间的 chunk
  pool_chunk_t *chunk = g_pool_head;
  while (chunk != NULL) {
    uint32_t allocated = atomic_load_explicit(&chunk->allocated, memory_order_relaxed);
    if (allocated < POOL_CHUNK_SIZE) {
      // 尝试原子递增
      uint32_t old_val = allocated;
      if (atomic_compare_exchange_weak_explicit(&chunk->allocated, &old_val, allocated + 1,
                                                 memory_order_relaxed, memory_order_relaxed)) {
        pthread_mutex_unlock(&g_pool_mutex);
        return &chunk->records[allocated];
      }
    }
    chunk = chunk->next;
  }
  
  // 没有空闲空间，分配新的 chunk
  pool_chunk_t *new_chunk = (pool_chunk_t *)g_malloc_func(sizeof(pool_chunk_t));
  if (new_chunk == NULL) {
    pthread_mutex_unlock(&g_pool_mutex);
    return NULL;
  }
  
  memset(new_chunk, 0, sizeof(pool_chunk_t));
  atomic_store_explicit(&new_chunk->allocated, 1, memory_order_relaxed);
  new_chunk->next = g_pool_head;
  g_pool_head = new_chunk;
  
  pthread_mutex_unlock(&g_pool_mutex);
  return &new_chunk->records[0];
}

// 释放一个 record（简化实现：不回收）
void pool_free_record(memory_record_t *record) {
  // 简单实现：不回收，让内存池持续增长
  // 更复杂的实现可以维护一个空闲列表
  // 对于内存泄漏检测工具，这是可接受的
  (void)record;  // 标记为未使用
}

// 清理内存池
void pool_cleanup(void (*free_func)(void *)) {
  pthread_mutex_lock(&g_pool_mutex);
  
  void (*cleanup_free)(void *) = free_func ? free_func : free;
  
  pool_chunk_t *chunk = g_pool_head;
  size_t chunk_count = 0;
  while (chunk != NULL) {
    pool_chunk_t *next = chunk->next;
    cleanup_free(chunk);
    chunk = next;
    chunk_count++;
  }
  
  g_pool_head = NULL;
  pthread_mutex_unlock(&g_pool_mutex);
  
  LOGI("Memory pool cleaned up: %zu chunks freed", chunk_count);
}

// 获取内存池统计信息
void pool_get_stats(size_t *total_chunks, size_t *total_allocated) {
  pthread_mutex_lock(&g_pool_mutex);
  
  size_t chunks = 0;
  size_t allocated = 0;
  
  pool_chunk_t *chunk = g_pool_head;
  while (chunk != NULL) {
    chunks++;
    allocated += atomic_load_explicit(&chunk->allocated, memory_order_relaxed);
    chunk = chunk->next;
  }
  
  pthread_mutex_unlock(&g_pool_mutex);
  
  if (total_chunks) *total_chunks = chunks;
  if (total_allocated) *total_allocated = allocated;
}
