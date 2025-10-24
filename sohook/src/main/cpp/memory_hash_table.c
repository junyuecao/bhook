// Copyright (c) 2024 SoHook Memory Leak Detection
// Hash table implementation for memory record storage

#include "memory_hash_table.h"

#include <android/log.h>
#include <stdint.h>
#include <string.h>

#define LOG_TAG "MemHashTable"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 哈希表全局实例
static hash_bucket_t g_hash_table[HASH_TABLE_SIZE];
static bool g_initialized = false;

// 哈希函数：将指针地址映射到桶索引
static inline size_t hash_ptr(void *ptr) {
  uintptr_t addr = (uintptr_t)ptr;
  // 右移3位忽略8字节对齐，减少冲突
  return (addr >> 3) % HASH_TABLE_SIZE;
}

// 初始化哈希表
int hash_table_init(void) {
  if (g_initialized) {
    LOGW("Hash table already initialized");
    return 0;
  }

  for (int i = 0; i < HASH_TABLE_SIZE; i++) {
    g_hash_table[i].head = NULL;
    pthread_mutex_init(&g_hash_table[i].lock, NULL);
  }

  g_initialized = true;
  LOGI("Hash table initialized with %d buckets", HASH_TABLE_SIZE);
  return 0;
}

// 清理哈希表
void hash_table_cleanup(void (*free_func)(void *)) {
  if (!g_initialized) {
    return;
  }

  for (int i = 0; i < HASH_TABLE_SIZE; i++) {
    hash_bucket_t *bucket = &g_hash_table[i];
    pthread_mutex_lock(&bucket->lock);

    memory_record_t *curr = bucket->head;
    while (curr != NULL) {
      memory_record_t *next = curr->next;
      if (free_func != NULL) {
        free_func(curr);
      }
      curr = next;
    }

    bucket->head = NULL;
    pthread_mutex_unlock(&bucket->lock);
    pthread_mutex_destroy(&bucket->lock);
  }

  g_initialized = false;
  LOGI("Hash table cleaned up");
}

// 添加记录到哈希表
int hash_table_add(memory_record_t *record) {
  if (!g_initialized || record == NULL) {
    return -1;
  }

  // 计算哈希值，定位到具体的桶
  size_t bucket_idx = hash_ptr(record->ptr);
  hash_bucket_t *bucket = &g_hash_table[bucket_idx];

  // 使用 trylock 避免死锁
  if (pthread_mutex_trylock(&bucket->lock) != 0) {
    return -1;  // 无法获取锁
  }

  // 插入到桶的链表头部 O(1)
  record->next = bucket->head;
  bucket->head = record;

  pthread_mutex_unlock(&bucket->lock);
  return 0;
}

// 从哈希表中移除记录
memory_record_t *hash_table_remove(void *ptr) {
  if (!g_initialized || ptr == NULL) {
    return NULL;
  }

  // 计算哈希值，直接定位到桶 O(1)
  size_t bucket_idx = hash_ptr(ptr);
  hash_bucket_t *bucket = &g_hash_table[bucket_idx];

  // 使用 trylock 避免死锁
  if (pthread_mutex_trylock(&bucket->lock) != 0) {
    return NULL;  // 无法获取锁
  }

  memory_record_t *prev = NULL;
  memory_record_t *curr = bucket->head;

  // 在桶内查找 O(1) 平均（桶内元素很少）
  while (curr != NULL) {
    if (curr->ptr == ptr) {
      // 找到了，从链表中移除
      if (prev == NULL) {
        bucket->head = curr->next;
      } else {
        prev->next = curr->next;
      }

      pthread_mutex_unlock(&bucket->lock);
      return curr;  // 返回记录，由调用者释放
    }
    prev = curr;
    curr = curr->next;
  }

  // 未找到记录
  pthread_mutex_unlock(&bucket->lock);
  return NULL;
}

// 遍历哈希表中的所有记录
void hash_table_foreach(bool (*callback)(memory_record_t *record, void *user_data), void *user_data) {
  if (!g_initialized || callback == NULL) {
    return;
  }

  for (int i = 0; i < HASH_TABLE_SIZE; i++) {
    hash_bucket_t *bucket = &g_hash_table[i];
    pthread_mutex_lock(&bucket->lock);

    memory_record_t *curr = bucket->head;
    while (curr != NULL) {
      memory_record_t *next = curr->next;  // 保存 next，防止 callback 修改链表
      
      if (!callback(curr, user_data)) {
        pthread_mutex_unlock(&bucket->lock);
        return;  // 停止遍历
      }
      
      curr = next;
    }

    pthread_mutex_unlock(&bucket->lock);
  }
}

// 获取哈希表统计信息
void hash_table_get_stats(size_t *total_records, size_t *max_bucket_size, double *avg_bucket_size) {
  if (!g_initialized) {
    if (total_records) *total_records = 0;
    if (max_bucket_size) *max_bucket_size = 0;
    if (avg_bucket_size) *avg_bucket_size = 0.0;
    return;
  }

  size_t total = 0;
  size_t max_size = 0;

  for (int i = 0; i < HASH_TABLE_SIZE; i++) {
    hash_bucket_t *bucket = &g_hash_table[i];
    pthread_mutex_lock(&bucket->lock);

    size_t bucket_size = 0;
    memory_record_t *curr = bucket->head;
    while (curr != NULL) {
      bucket_size++;
      curr = curr->next;
    }

    total += bucket_size;
    if (bucket_size > max_size) {
      max_size = bucket_size;
    }

    pthread_mutex_unlock(&bucket->lock);
  }

  if (total_records) *total_records = total;
  if (max_bucket_size) *max_bucket_size = max_size;
  if (avg_bucket_size) *avg_bucket_size = (double)total / HASH_TABLE_SIZE;
}
