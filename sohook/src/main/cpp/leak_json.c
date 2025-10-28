// Copyright (c) 2024 SoHook Memory Leak Detection
// Leak JSON generation implementation

#include "leak_json.h"

#include <android/log.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "memory_hash_table.h"
#include "memory_tracker.h"

#define LOG_TAG "SoHook-LeakJSON"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 外部函数声明
extern void *(*original_malloc)(size_t);
extern void (*original_free)(void *);
extern bool g_debug;
extern bool g_backtrace_enabled;

// dladdr 符号缓存（简单的哈希表）
#define SYMBOL_CACHE_SIZE 4096
typedef struct symbol_cache_entry {
  void *addr;
  char symbol[256];  // 符号名 + 偏移
  struct symbol_cache_entry *next;
} symbol_cache_entry_t;

static symbol_cache_entry_t *g_symbol_cache[SYMBOL_CACHE_SIZE] = {NULL};
static pthread_mutex_t g_symbol_cache_mutex = PTHREAD_MUTEX_INITIALIZER;

// 符号缓存辅助函数
static inline size_t symbol_cache_hash(void *addr) {
  return ((uintptr_t)addr >> 3) % SYMBOL_CACHE_SIZE;
}

// 查找符号缓存（无锁，仅读取）
static const char *symbol_cache_lookup(void *addr) {
  size_t idx = symbol_cache_hash(addr);
  symbol_cache_entry_t *entry = g_symbol_cache[idx];

  while (entry != NULL) {
    if (entry->addr == addr) {
      return entry->symbol;
    }
    entry = entry->next;
  }

  return NULL;
}

// 添加到符号缓存
static void symbol_cache_add(void *addr, const char *symbol) {
  size_t idx = symbol_cache_hash(addr);

  pthread_mutex_lock(&g_symbol_cache_mutex);

  // 再次检查是否已存在（双重检查）
  symbol_cache_entry_t *entry = g_symbol_cache[idx];
  while (entry != NULL) {
    if (entry->addr == addr) {
      pthread_mutex_unlock(&g_symbol_cache_mutex);
      return;  // 已存在
    }
    entry = entry->next;
  }

  // 创建新条目
  symbol_cache_entry_t *new_entry = (symbol_cache_entry_t *)original_malloc(sizeof(symbol_cache_entry_t));
  if (new_entry != NULL) {
    new_entry->addr = addr;
    strncpy(new_entry->symbol, symbol, sizeof(new_entry->symbol) - 1);
    new_entry->symbol[sizeof(new_entry->symbol) - 1] = '\0';
    new_entry->next = g_symbol_cache[idx];
    g_symbol_cache[idx] = new_entry;
  }

  pthread_mutex_unlock(&g_symbol_cache_mutex);
}

// 清理符号缓存
void leak_json_clear_symbol_cache(void) {
  pthread_mutex_lock(&g_symbol_cache_mutex);

  for (int i = 0; i < SYMBOL_CACHE_SIZE; i++) {
    symbol_cache_entry_t *entry = g_symbol_cache[i];
    while (entry != NULL) {
      symbol_cache_entry_t *next = entry->next;
      original_free(entry);
      entry = next;
    }
    g_symbol_cache[i] = NULL;
  }

  pthread_mutex_unlock(&g_symbol_cache_mutex);
}

// JSON 生成的上下文结构
typedef struct {
  char *buffer;
  size_t buffer_size;
  size_t buffer_used;
  int leak_count;
  bool first;
} json_context_t;

// 堆栈聚合组
typedef struct stack_group {
  void *backtrace[MAX_BACKTRACE_SIZE];
  int backtrace_size;
  int count;
  size_t total_size;
  struct stack_group *next;
} stack_group_t;

// 堆栈聚合哈希表
#define STACK_GROUP_HASH_SIZE 1024
typedef struct {
  stack_group_t *groups[STACK_GROUP_HASH_SIZE];
  int group_count;
} stack_aggregator_t;

// 计算堆栈的哈希值
static inline size_t hash_backtrace(void **backtrace, int size) {
  size_t hash = 0;
  for (int i = 0; i < size; i++) {
    hash = hash * 31 + (uintptr_t)backtrace[i];
  }
  return hash % STACK_GROUP_HASH_SIZE;
}

// 比较两个堆栈是否相同
static inline bool backtrace_equals(void **bt1, int size1, void **bt2, int size2) {
  if (size1 != size2) return false;
  for (int i = 0; i < size1; i++) {
    if (bt1[i] != bt2[i]) return false;
  }
  return true;
}

// 添加记录到聚合器
static void aggregator_add(stack_aggregator_t *agg, memory_record_t *record) {
  size_t hash = hash_backtrace(record->backtrace, record->backtrace_size);

  // 查找是否已存在相同堆栈
  stack_group_t *group = agg->groups[hash];
  while (group != NULL) {
    if (backtrace_equals(group->backtrace, group->backtrace_size,
                         record->backtrace, record->backtrace_size)) {
      // 找到相同堆栈，累加
      group->count++;
      group->total_size += record->size;
      return;
    }
    group = group->next;
  }

  // 未找到，创建新组
  stack_group_t *new_group = (stack_group_t *)original_malloc(sizeof(stack_group_t));
  if (new_group == NULL) return;

  memcpy(new_group->backtrace, record->backtrace, record->backtrace_size * sizeof(void *));
  new_group->backtrace_size = record->backtrace_size;
  new_group->count = 1;
  new_group->total_size = record->size;
  new_group->next = agg->groups[hash];
  agg->groups[hash] = new_group;
  agg->group_count++;
}

// 聚合回调函数
static bool aggregator_callback(memory_record_t *record, void *user_data) {
  stack_aggregator_t *agg = (stack_aggregator_t *)user_data;
  aggregator_add(agg, record);
  return true;
}

// 用于 hash_table_foreach 的回调函数
static bool json_callback(memory_record_t *record, void *user_data) {
  json_context_t *ctx = (json_context_t *)user_data;

  // 检查缓冲区空间（预留 1KB 用于单条记录）
  if (ctx->buffer_used + 1024 > ctx->buffer_size) {
    ctx->buffer_size *= 2;
    char *new_buffer = (char *)original_malloc(ctx->buffer_size);
    if (new_buffer == NULL) {
      return false; // 停止遍历
    }
    memcpy(new_buffer, ctx->buffer, ctx->buffer_used);
    original_free(ctx->buffer);
    ctx->buffer = new_buffer;
  }

  // 添加逗号分隔符
  if (!ctx->first) {
    ctx->buffer_used += snprintf(ctx->buffer + ctx->buffer_used,
                                  ctx->buffer_size - ctx->buffer_used, ",");
  }
  ctx->first = false;

  // 添加记录
  ctx->buffer_used += snprintf(ctx->buffer + ctx->buffer_used,
                                ctx->buffer_size - ctx->buffer_used,
                                "{\"ptr\":\"%p\",\"size\":%zu,\"timestamp\":0,\"backtrace\":[",
                                record->ptr, record->size);

  // 添加调用栈
  if (g_backtrace_enabled && record->backtrace_size > 0) {
    for (int j = 0; j < record->backtrace_size; j++) {
      if (j > 0) {
        ctx->buffer_used += snprintf(ctx->buffer + ctx->buffer_used,
                                      ctx->buffer_size - ctx->buffer_used, ",");
      }

      // 先查找缓存
      const char *cached_symbol = symbol_cache_lookup(record->backtrace[j]);
      if (cached_symbol != NULL) {
        // 缓存命中，直接使用
        ctx->buffer_used += snprintf(ctx->buffer + ctx->buffer_used,
                                      ctx->buffer_size - ctx->buffer_used,
                                      "\"%s\"", cached_symbol);
      } else {
        // 缓存未命中，调用 dladdr
        Dl_info info;
        char symbol_buf[256];

        if (dladdr(record->backtrace[j], &info) && info.dli_sname) {
          snprintf(symbol_buf, sizeof(symbol_buf), "%s+%ld",
                   info.dli_sname,
                   (long)((char *)record->backtrace[j] - (char *)info.dli_saddr));

          ctx->buffer_used += snprintf(ctx->buffer + ctx->buffer_used,
                                        ctx->buffer_size - ctx->buffer_used,
                                        "\"%s\"", symbol_buf);

          // 添加到缓存
          symbol_cache_add(record->backtrace[j], symbol_buf);
        } else {
          snprintf(symbol_buf, sizeof(symbol_buf), "%p", record->backtrace[j]);

          ctx->buffer_used += snprintf(ctx->buffer + ctx->buffer_used,
                                        ctx->buffer_size - ctx->buffer_used,
                                        "\"%s\"", symbol_buf);

          // 添加到缓存
          symbol_cache_add(record->backtrace[j], symbol_buf);
        }
      }
    }
  }

  ctx->buffer_used += snprintf(ctx->buffer + ctx->buffer_used,
                                ctx->buffer_size - ctx->buffer_used, "]}");
  ctx->leak_count++;

  return true; // 继续遍历
}

// 获取内存泄漏列表（JSON 格式）
char *leak_json_get_leaks(void) {
  // 初始化 JSON 上下文
  json_context_t ctx;
  ctx.buffer_size = 8 * 1024 * 1024;  // 8MB，避免频繁扩容（30000个泄漏约6MB）
  ctx.buffer_used = 0;
  ctx.leak_count = 0;
  ctx.first = true;

  ctx.buffer = (char *)original_malloc(ctx.buffer_size);
  if (ctx.buffer == NULL) {
    LOGE("Failed to allocate buffer for JSON");
    return NULL;
  }

  // 开始 JSON 数组
  ctx.buffer_used += snprintf(ctx.buffer, ctx.buffer_size, "[");

  // 遍历哈希表获取所有泄漏记录（添加耗时统计）
  struct timespec start_time, end_time;
  clock_gettime(CLOCK_MONOTONIC, &start_time);

  hash_table_foreach(json_callback, &ctx);

  clock_gettime(CLOCK_MONOTONIC, &end_time);
  long elapsed_ms = (end_time.tv_sec - start_time.tv_sec) * 1000 +
                    (end_time.tv_nsec - start_time.tv_nsec) / 1000000;

  if (g_debug || elapsed_ms > 10) {  // 超过10ms时打印警告
    LOGI("hash_table_foreach took %ld ms, processed %d leaks", elapsed_ms, ctx.leak_count);
  }

  // 结束 JSON 数组
  ctx.buffer_used += snprintf(ctx.buffer + ctx.buffer_used,
                               ctx.buffer_size - ctx.buffer_used, "]");

  if (g_debug) {
    LOGD("Generated JSON with %d leaks, size=%zu", ctx.leak_count, ctx.buffer_used);
  }

  return ctx.buffer;
}

// 获取聚合后的内存泄漏列表（JSON 格式）- 按堆栈聚合
char *leak_json_get_leaks_aggregated(void) {
  struct timespec start_time, end_time;
  clock_gettime(CLOCK_MONOTONIC, &start_time);

  // 初始化聚合器
  stack_aggregator_t agg;
  memset(&agg, 0, sizeof(agg));

  // 遍历哈希表进行聚合
  hash_table_foreach(aggregator_callback, &agg);

  clock_gettime(CLOCK_MONOTONIC, &end_time);
  long aggregate_ms = (end_time.tv_sec - start_time.tv_sec) * 1000 +
                      (end_time.tv_nsec - start_time.tv_nsec) / 1000000;

  if (g_debug || aggregate_ms > 10) {
    LOGI("Aggregation took %ld ms, %d unique stacks", aggregate_ms, agg.group_count);
  }

  // 收集所有组到数组中
  stack_group_t **groups = (stack_group_t **)original_malloc(agg.group_count * sizeof(stack_group_t *));
  if (groups == NULL) {
    LOGE("Failed to allocate groups array");
    return NULL;
  }

  int idx = 0;
  for (int i = 0; i < STACK_GROUP_HASH_SIZE; i++) {
    stack_group_t *group = agg.groups[i];
    while (group != NULL) {
      groups[idx++] = group;
      group = group->next;
    }
  }

  // 按 total_size 降序排序（冒泡排序，简单够用）
  for (int i = 0; i < agg.group_count - 1; i++) {
    for (int j = 0; j < agg.group_count - i - 1; j++) {
      if (groups[j]->total_size < groups[j + 1]->total_size) {
        stack_group_t *temp = groups[j];
        groups[j] = groups[j + 1];
        groups[j + 1] = temp;
      }
    }
  }

  // 生成 JSON
  size_t buffer_size = 1024 * 1024;  // 1MB 足够聚合后的数据
  char *buffer = (char *)original_malloc(buffer_size);
  if (buffer == NULL) {
    LOGE("Failed to allocate JSON buffer");
    original_free(groups);
    return NULL;
  }

  size_t buffer_used = 0;
  buffer_used += snprintf(buffer, buffer_size, "[");

  for (int i = 0; i < agg.group_count; i++) {
    stack_group_t *group = groups[i];

    if (i > 0) {
      buffer_used += snprintf(buffer + buffer_used, buffer_size - buffer_used, ",");
    }

    buffer_used += snprintf(buffer + buffer_used, buffer_size - buffer_used,
                            "{\"count\":%d,\"totalSize\":%zu,\"backtrace\":[",
                            group->count, group->total_size);

    // 添加调用栈
    if (g_backtrace_enabled && group->backtrace_size > 0) {
      for (int j = 0; j < group->backtrace_size; j++) {
        if (j > 0) {
          buffer_used += snprintf(buffer + buffer_used, buffer_size - buffer_used, ",");
        }

        // 查找缓存
        const char *cached_symbol = symbol_cache_lookup(group->backtrace[j]);
        if (cached_symbol != NULL) {
          buffer_used += snprintf(buffer + buffer_used, buffer_size - buffer_used,
                                  "\"%s\"", cached_symbol);
        } else {
          // 缓存未命中，调用 dladdr
          Dl_info info;
          char symbol_buf[256];

          if (dladdr(group->backtrace[j], &info) && info.dli_sname) {
            snprintf(symbol_buf, sizeof(symbol_buf), "%s+%ld",
                     info.dli_sname,
                     (long)((char *)group->backtrace[j] - (char *)info.dli_saddr));

            buffer_used += snprintf(buffer + buffer_used, buffer_size - buffer_used,
                                    "\"%s\"", symbol_buf);

            symbol_cache_add(group->backtrace[j], symbol_buf);
          } else {
            snprintf(symbol_buf, sizeof(symbol_buf), "%p", group->backtrace[j]);

            buffer_used += snprintf(buffer + buffer_used, buffer_size - buffer_used,
                                    "\"%s\"", symbol_buf);

            symbol_cache_add(group->backtrace[j], symbol_buf);
          }
        }
      }
    }

    buffer_used += snprintf(buffer + buffer_used, buffer_size - buffer_used, "]}");
  }

  buffer_used += snprintf(buffer + buffer_used, buffer_size - buffer_used, "]");

  // 清理聚合器
  for (int i = 0; i < STACK_GROUP_HASH_SIZE; i++) {
    stack_group_t *group = agg.groups[i];
    while (group != NULL) {
      stack_group_t *next = group->next;
      original_free(group);
      group = next;
    }
  }
  original_free(groups);

  clock_gettime(CLOCK_MONOTONIC, &end_time);
  long total_ms = (end_time.tv_sec - start_time.tv_sec) * 1000 +
                  (end_time.tv_nsec - start_time.tv_nsec) / 1000000;

  if (g_debug) {
    LOGD("Generated aggregated JSON: %d groups, size=%zu, total_time=%ldms",
         agg.group_count, buffer_used, total_ms);
  }

  return buffer;
}
