// Copyright (c) 2024 SoHook Memory Leak Detection
// Memory tracker implementation

#include "memory_tracker.h"

#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "bytehook.h"
#include "memory_hash_table.h"
#include "memory_pool.h"
#include "leak_report.h"
#include "memory_stats.h"
#include "backtrace.h"

#define LOG_TAG "SoHook-Tracker"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 全局状态
static bool g_initialized = false;
static bool g_debug = false;
static bool g_backtrace_enabled = false;

// Hook 管理锁
static pthread_mutex_t g_hook_mutex = PTHREAD_MUTEX_INITIALIZER;

// bytehook stub数组
#define MAX_HOOKS 64
static bytehook_stub_t g_malloc_stubs[MAX_HOOKS];
static bytehook_stub_t g_calloc_stubs[MAX_HOOKS];
static bytehook_stub_t g_realloc_stubs[MAX_HOOKS];
static bytehook_stub_t g_free_stubs[MAX_HOOKS];
static int g_hook_count = 0;

// 防止递归调用的标志
static __thread bool g_in_hook = false;

// 原始函数指针
static void *(*original_malloc)(size_t) = NULL;
static void (*original_free)(void *) = NULL;

// 添加内存记录（哈希表版本）
static void add_memory_record(void *ptr, size_t size) {
  if (g_in_hook) return;
  
  // 设置标志，防止递归
  g_in_hook = true;

  // 从内存池分配 record（避免频繁调用 malloc）
  memory_record_t *record = pool_alloc_record();
  if (record == NULL) {
    g_in_hook = false;
    return;
  }

  record->ptr = ptr;
  record->size = size;
  record->next = NULL;
  
  // 可选的栈回溯（如果启用）
  if (g_backtrace_enabled) {
    record->backtrace_size = backtrace_capture(record->backtrace, 16);
    if (g_debug) {
      LOGD("Captured backtrace: %d frames for ptr=%p", record->backtrace_size, ptr);
    }
  } else {
    record->backtrace_size = 0;
  }

  // 使用哈希表添加记录
  if (hash_table_add(record) != 0) {
    pool_free_record(record);
    g_in_hook = false;
    return;
  }

  // 更新统计信息
  memory_stats_update_alloc(size);

  g_in_hook = false;

  if (g_debug) {
    LOGD("malloc: ptr=%p, size=%zu", ptr, size);
  }
}

// 移除内存记录（哈希表版本 - O(1) 平均复杂度）
static void remove_memory_record(void *ptr) {
  if (g_in_hook || ptr == NULL) return;
  
  g_in_hook = true;

  // 使用哈希表移除记录
  memory_record_t *record = hash_table_remove(ptr);
  
  if (record != NULL) {
    // 找到了记录
    size_t freed_size = record->size;

    // 释放记录到内存池（而不是调用 free）
    pool_free_record(record);

    // 更新统计信息
    memory_stats_update_free(freed_size);
    
    // 在调用任何可能触发malloc的函数之前重置标志
    g_in_hook = false;

    if (g_debug) {
      LOGD("free: ptr=%p, size=%zu", ptr, freed_size);
    }
    return;
  }

  // 未找到记录（可能是未被追踪的内存）
  g_in_hook = false;
}

// malloc hook函数
void *malloc_proxy(size_t size) {
  if (g_debug && !g_in_hook) {
    LOGD("malloc_proxy called: size=%zu", size);
  }
  
  void *result = BYTEHOOK_CALL_PREV(malloc_proxy, void *(*)(size_t), size);
  
  if (result != NULL && !g_in_hook) {
    add_memory_record(result, size);
  }
  
  BYTEHOOK_POP_STACK();
  return result;
}

// calloc hook函数
void *calloc_proxy(size_t nmemb, size_t size) {
  void *result = BYTEHOOK_CALL_PREV(calloc_proxy, void *(*)(size_t, size_t), nmemb, size);
  if (result != NULL && !g_in_hook) {
    add_memory_record(result, nmemb * size);
  }
  BYTEHOOK_POP_STACK();
  return result;
}

// realloc hook函数
void *realloc_proxy(void *ptr, size_t size) {
  if (ptr != NULL && !g_in_hook) {
    remove_memory_record(ptr);
  }

  void *result = BYTEHOOK_CALL_PREV(realloc_proxy, void *(*)(void *, size_t), ptr, size);

  if (result != NULL && !g_in_hook) {
    add_memory_record(result, size);
  }

  BYTEHOOK_POP_STACK();
  return result;
}

// free hook函数
void free_proxy(void *ptr) {
  if (ptr != NULL && !g_in_hook) {
    remove_memory_record(ptr);
  }
  BYTEHOOK_CALL_PREV(free_proxy, void (*)(void *), ptr);
  BYTEHOOK_POP_STACK();
}

// 初始化内存追踪器
int memory_tracker_init(bool debug, bool enable_backtrace) {
  if (g_initialized) {
    LOGW("Memory tracker already initialized");
    return 0;
  }

  g_debug = debug;
  g_backtrace_enabled = enable_backtrace;
  
  if (enable_backtrace) {
    LOGW("Backtrace enabled - this will significantly impact performance!");
  }
  bytehook_set_debug(debug);

  // 获取原始的malloc/free函数指针，用于内部分配
  original_malloc = dlsym(RTLD_DEFAULT, "malloc");
  original_free = dlsym(RTLD_DEFAULT, "free");
  
  if (original_malloc == NULL || original_free == NULL) {
    LOGE("Failed to get original malloc/free");
    return -1;
  }

  // 初始化哈希表
  if (hash_table_init() != 0) {
    LOGE("Hash table init failed");
    return -1;
  }
  
  // 初始化内存池
  pool_init(original_malloc);
  
  // 初始化统计模块
  memory_stats_init();

  // 初始化bytehook
  int ret = bytehook_init(BYTEHOOK_MODE_AUTOMATIC, debug);
  if (ret != BYTEHOOK_STATUS_CODE_OK) {
    LOGE("bytehook_init failed: %d", ret);
    hash_table_cleanup(original_free != NULL ? original_free : free);
    return ret;
  }

  g_initialized = true;
  LOGI("Memory tracker initialized (debug=%d)", debug);
  return 0;
}

// Hook回调函数
static void hook_callback(bytehook_stub_t task_stub, int status_code, const char *caller_path_name,
                         const char *sym_name, void *new_func, void *prev_func, void *arg) {
  (void)task_stub;
  (void)new_func;
  (void)prev_func;
  (void)arg;
  
  if (status_code == BYTEHOOK_STATUS_CODE_OK) {
    LOGI("Hook success: %s in %s", sym_name, caller_path_name ? caller_path_name : "unknown");
  } else {
    LOGW("Hook failed: %s in %s, status=%d", sym_name, caller_path_name ? caller_path_name : "unknown", status_code);
  }
}

// 开始追踪指定so库的内存分配
int memory_tracker_hook(const char **so_names, int count) {
  if (!g_initialized) {
    LOGE("Memory tracker not initialized");
    return -1;
  }

  if (so_names == NULL || count <= 0 || count > MAX_HOOKS) {
    LOGE("Invalid parameters: so_names=%p, count=%d", so_names, count);
    return -1;
  }

  pthread_mutex_lock(&g_hook_mutex);

  for (int i = 0; i < count && g_hook_count < MAX_HOOKS; i++) {
    const char *so_name = so_names[i];
    if (so_name == NULL) continue;

    LOGI("=== Hooking memory functions in: %s ===", so_name);
    LOGI("Using bytehook_hook_single - caller: %s", so_name);

    // Hook malloc - hook libsample.so调用libc.so的malloc
    LOGI("Calling bytehook_hook_single for malloc...");
    g_malloc_stubs[g_hook_count] =
        bytehook_hook_single(so_name, NULL, "malloc", (void *)malloc_proxy, hook_callback, NULL);
    if (g_malloc_stubs[g_hook_count] == NULL) {
      LOGE("  malloc hook FAILED - stub is NULL");
    } else {
      LOGI("  malloc stub: %p", g_malloc_stubs[g_hook_count]);
    }

    // Hook calloc
    LOGI("Calling bytehook_hook_single for calloc...");
    g_calloc_stubs[g_hook_count] =
        bytehook_hook_single(so_name, NULL, "calloc", (void *)calloc_proxy, hook_callback, NULL);
    if (g_calloc_stubs[g_hook_count] == NULL) {
      LOGE("  calloc hook FAILED - stub is NULL");
    } else {
      LOGI("  calloc stub: %p", g_calloc_stubs[g_hook_count]);
    }

    // Hook realloc
    LOGI("Calling bytehook_hook_single for realloc...");
    g_realloc_stubs[g_hook_count] =
        bytehook_hook_single(so_name, NULL, "realloc", (void *)realloc_proxy, hook_callback, NULL);
    if (g_realloc_stubs[g_hook_count] == NULL) {
      LOGE("  realloc hook FAILED - stub is NULL");
    } else {
      LOGI("  realloc stub: %p", g_realloc_stubs[g_hook_count]);
    }

    // Hook free
    LOGI("Calling bytehook_hook_single for free...");
    g_free_stubs[g_hook_count] = bytehook_hook_single(so_name, NULL, "free", (void *)free_proxy, hook_callback, NULL);
    if (g_free_stubs[g_hook_count] == NULL) {
      LOGE("  free hook FAILED - stub is NULL");
    } else {
      LOGI("  free stub: %p", g_free_stubs[g_hook_count]);
    }

    g_hook_count++;
  }

  pthread_mutex_unlock(&g_hook_mutex);
  LOGI("Hooked %d libraries", count);
  return 0;
}

// 停止追踪指定so库的内存分配
int memory_tracker_unhook(const char **so_names, int count) {
  if (!g_initialized) {
    LOGE("Memory tracker not initialized");
    return -1;
  }

  pthread_mutex_lock(&g_hook_mutex);

  // 简化实现：unhook所有
  for (int i = 0; i < g_hook_count; i++) {
    if (g_malloc_stubs[i] != NULL) {
      bytehook_unhook(g_malloc_stubs[i]);
      g_malloc_stubs[i] = NULL;
    }
    if (g_calloc_stubs[i] != NULL) {
      bytehook_unhook(g_calloc_stubs[i]);
      g_calloc_stubs[i] = NULL;
    }
    if (g_realloc_stubs[i] != NULL) {
      bytehook_unhook(g_realloc_stubs[i]);
      g_realloc_stubs[i] = NULL;
    }
    if (g_free_stubs[i] != NULL) {
      bytehook_unhook(g_free_stubs[i]);
      g_free_stubs[i] = NULL;
    }
  }

  g_hook_count = 0;
  pthread_mutex_unlock(&g_hook_mutex);

  LOGI("Unhooked memory tracking");
  return 0;
}

// 停止追踪所有已hook的so库
int memory_tracker_unhook_all(void) {
  if (!g_initialized) {
    LOGE("Memory tracker not initialized");
    return -1;
  }

  pthread_mutex_lock(&g_hook_mutex);

  LOGI("Unhooking all tracked libraries (count=%d)", g_hook_count);

  // Unhook所有已注册的stubs
  for (int i = 0; i < g_hook_count; i++) {
    if (g_malloc_stubs[i] != NULL) {
      bytehook_unhook(g_malloc_stubs[i]);
      g_malloc_stubs[i] = NULL;
    }
    if (g_calloc_stubs[i] != NULL) {
      bytehook_unhook(g_calloc_stubs[i]);
      g_calloc_stubs[i] = NULL;
    }
    if (g_realloc_stubs[i] != NULL) {
      bytehook_unhook(g_realloc_stubs[i]);
      g_realloc_stubs[i] = NULL;
    }
    if (g_free_stubs[i] != NULL) {
      bytehook_unhook(g_free_stubs[i]);
      g_free_stubs[i] = NULL;
    }
  }

  g_hook_count = 0;
  pthread_mutex_unlock(&g_hook_mutex);

  LOGI("All hooks removed successfully");
  return 0;
}

// 导出函数供 leak_report 模块使用
bool leak_report_is_initialized(void) {
  return g_initialized;
}

void *leak_report_get_original_malloc(void) {
  return original_malloc;
}

void leak_report_get_original_free(void *ptr) {
  if (original_free != NULL) {
    original_free(ptr);
  } else {
    free(ptr);
  }
}

void leak_report_get_stats(memory_stats_t *stats) {
  memory_tracker_get_stats(stats);
}

// 获取内存泄漏报告（委托给 leak_report 模块）
char *memory_tracker_get_leak_report(void) {
  return leak_report_generate();
}

// 将内存泄漏报告导出到文件（委托给 leak_report 模块）
int memory_tracker_dump_leak_report(const char *file_path) {
  return leak_report_dump(file_path);
}

// 获取内存统计信息（委托给 memory_stats 模块）
void memory_tracker_get_stats(memory_stats_t *stats) {
  memory_stats_get(stats);
}

// 重置统计信息
void memory_tracker_reset_stats(void) {
  // 清空哈希表中的所有记录（不需要 free，因为使用内存池）
  hash_table_cleanup(NULL);  // 传 NULL，不释放 record
  
  // 重新初始化哈希表
  hash_table_init();
  
  // 清理内存池
  pool_cleanup(original_free);

  // 重置统计信息（委托给 memory_stats 模块）
  memory_stats_reset();
}

// 启用或禁用栈回溯
void memory_tracker_set_backtrace_enabled(bool enable) {
  if (enable && !g_backtrace_enabled) {
    LOGW("Enabling backtrace - this will significantly impact performance!");
  } else if (!enable && g_backtrace_enabled) {
    LOGI("Disabling backtrace");
  }
  g_backtrace_enabled = enable;
}

// 检查栈回溯是否启用
bool memory_tracker_is_backtrace_enabled(void) {
  return g_backtrace_enabled;
}

// JSON 生成的上下文结构
typedef struct {
  char *buffer;
  size_t buffer_size;
  size_t buffer_used;
  int leak_count;
  bool first;
} json_context_t;

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
      
      // 获取符号信息
      Dl_info info;
      if (dladdr(record->backtrace[j], &info) && info.dli_sname) {
        ctx->buffer_used += snprintf(ctx->buffer + ctx->buffer_used, 
                                      ctx->buffer_size - ctx->buffer_used,
                                      "\"%s+%ld\"",
                                      info.dli_sname,
                                      (long)((char *)record->backtrace[j] - (char *)info.dli_saddr));
      } else {
        ctx->buffer_used += snprintf(ctx->buffer + ctx->buffer_used, 
                                      ctx->buffer_size - ctx->buffer_used,
                                      "\"%p\"", record->backtrace[j]);
      }
    }
  }

  ctx->buffer_used += snprintf(ctx->buffer + ctx->buffer_used, 
                                ctx->buffer_size - ctx->buffer_used, "]}");
  ctx->leak_count++;

  return true; // 继续遍历
}

// 获取内存泄漏列表（JSON 格式）
char *memory_tracker_get_leaks_json(void) {
  if (!g_initialized) {
    LOGE("Memory tracker not initialized");
    return NULL;
  }

  // 初始化 JSON 上下文
  json_context_t ctx;
  ctx.buffer_size = 4096;
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

  // 遍历哈希表获取所有泄漏记录
  hash_table_foreach(json_callback, &ctx);

  // 结束 JSON 数组
  ctx.buffer_used += snprintf(ctx.buffer + ctx.buffer_used, 
                               ctx.buffer_size - ctx.buffer_used, "]");

  if (g_debug) {
    LOGD("Generated JSON with %d leaks, size=%zu", ctx.leak_count, ctx.buffer_used);
  }

  return ctx.buffer;
}
