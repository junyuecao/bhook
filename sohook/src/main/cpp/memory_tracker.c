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
#include <unwind.h>

#include "bytehook.h"
#include "memory_hash_table.h"
#include "memory_pool.h"

#define LOG_TAG "MemoryTracker"
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

// 混合统计策略：
// - total_* 使用原子操作实时更新（用于性能测试）
// - current_* 延迟计算（遍历哈希表）
static _Atomic uint64_t g_total_alloc_count = 0;
static _Atomic uint64_t g_total_alloc_size = 0;
static _Atomic uint64_t g_total_free_count = 0;
static _Atomic uint64_t g_total_free_size = 0;

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

// 栈回溯辅助结构
struct BacktraceState {
  void **current;
  void **end;
};

// 栈回溯回调
static _Unwind_Reason_Code unwind_callback(struct _Unwind_Context *context, void *arg) {
  struct BacktraceState *state = (struct BacktraceState *)arg;
  uintptr_t pc = _Unwind_GetIP(context);
  if (pc && state->current < state->end) {
    *state->current++ = (void *)pc;
  }
  return state->current < state->end ? _URC_NO_REASON : _URC_END_OF_STACK;
}

// 捕获当前调用栈
static int capture_backtrace(void **buffer, int max_frames) {
  struct BacktraceState state = {buffer, buffer + max_frames};
  _Unwind_Backtrace(unwind_callback, &state);
  return state.current - buffer;
}

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
    record->backtrace_size = capture_backtrace(record->backtrace, 16);
    if (g_debug) {
      LOGD("Captured backtrace: %d frames for ptr=%p", record->backtrace_size, ptr);
    }
  } else {
    record->backtrace_size = 0;
  }

  // 使用哈希表添加记录
  if (hash_table_add(record) != 0) {
    // 添加失败，释放记录到内存池
    pool_free_record(record);
    g_in_hook = false;
    return;
  }

  // 只更新 total 统计（原子操作，无锁）
  atomic_fetch_add_explicit(&g_total_alloc_count, 1, memory_order_relaxed);
  atomic_fetch_add_explicit(&g_total_alloc_size, size, memory_order_relaxed);

  // 在调用任何可能触发malloc的函数之前重置标志
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

    // 只更新 total 统计（原子操作，无锁）
    atomic_fetch_add_explicit(&g_total_free_count, 1, memory_order_relaxed);
    atomic_fetch_add_explicit(&g_total_free_size, freed_size, memory_order_relaxed);
    
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

// 用于计算当前统计的回调数据结构
typedef struct {
  uint64_t current_count;
  uint64_t current_size;
} stats_calc_context_t;

// 统计计算回调函数
static bool stats_calc_callback(memory_record_t *record, void *user_data) {
  stats_calc_context_t *ctx = (stats_calc_context_t *)user_data;
  ctx->current_count++;
  ctx->current_size += record->size;
  return true;  // 继续遍历
}

// 计算当前统计（遍历哈希表）
static void calculate_current_stats(uint64_t *count, uint64_t *size) {
  stats_calc_context_t ctx = {0};
  hash_table_foreach(stats_calc_callback, &ctx);
  *count = ctx.current_count;
  *size = ctx.current_size;
}

// 用于遍历的回调数据结构
typedef struct {
  char **report;
  size_t *buffer_size;
  int *offset;
  int *leak_count;
  int max_leaks;
} leak_report_context_t;

// 遍历回调函数
static bool leak_report_callback(memory_record_t *record, void *user_data) {
  leak_report_context_t *ctx = (leak_report_context_t *)user_data;
  
  if (*ctx->leak_count >= ctx->max_leaks) {
    return false;  // 停止遍历
  }
  
  // 计算需要的空间（包括栈回溯）
  int base_needed = snprintf(NULL, 0, "Leak #%d: ptr=%p, size=%zu\n",
                            *ctx->leak_count + 1, record->ptr, record->size);
  
  int backtrace_needed = 0;
  if (record->backtrace_size > 0) {
    backtrace_needed = snprintf(NULL, 0, "  Backtrace (%d frames):\n", record->backtrace_size);
    for (int i = 0; i < record->backtrace_size; i++) {
      backtrace_needed += snprintf(NULL, 0, "    #%d: %p\n", i, record->backtrace[i]);
    }
  }
  
  int needed = base_needed + backtrace_needed;
  
  // 检查缓冲区是否足够
  if (*ctx->buffer_size - *ctx->offset < (size_t)needed + 1) {
    // 需要扩展缓冲区
    size_t new_size = *ctx->buffer_size * 2;
    char *new_report = NULL;
    if (original_malloc != NULL) {
      new_report = (char *)original_malloc(new_size);
      if (new_report != NULL) {
        memcpy(new_report, *ctx->report, *ctx->offset);
        original_free(*ctx->report);
      }
    }
    
    if (new_report == NULL) {
      return false;  // 扩展失败，停止遍历
    }
    
    *ctx->report = new_report;
    *ctx->buffer_size = new_size;
  }
  
  // 写入基本信息
  *ctx->offset += snprintf(*ctx->report + *ctx->offset, *ctx->buffer_size - *ctx->offset,
                          "Leak #%d: ptr=%p, size=%zu\n",
                          *ctx->leak_count + 1, record->ptr, record->size);
  
  // 写入栈回溯信息（如果有）
  if (record->backtrace_size > 0) {
    LOGD("Writing backtrace for leak #%d: %d frames", *ctx->leak_count + 1, record->backtrace_size);
    *ctx->offset += snprintf(*ctx->report + *ctx->offset, *ctx->buffer_size - *ctx->offset,
                            "  Backtrace (%d frames):\n", record->backtrace_size);
    for (int i = 0; i < record->backtrace_size; i++) {
      // 尝试解析符号
      Dl_info info;
      if (dladdr(record->backtrace[i], &info) && info.dli_sname) {
        // 成功解析到符号名
        const char *symbol = info.dli_sname;
        const char *lib = info.dli_fname ? strrchr(info.dli_fname, '/') : NULL;
        lib = lib ? lib + 1 : info.dli_fname;
        
        // 计算偏移
        ptrdiff_t offset = (char *)record->backtrace[i] - (char *)info.dli_saddr;
        
        *ctx->offset += snprintf(*ctx->report + *ctx->offset, *ctx->buffer_size - *ctx->offset,
                                "    #%d: %p %s+%td (%s)\n", 
                                i, record->backtrace[i], symbol, offset, lib ? lib : "?");
      } else {
        // 无法解析，只显示地址
        *ctx->offset += snprintf(*ctx->report + *ctx->offset, *ctx->buffer_size - *ctx->offset,
                                "    #%d: %p\n", i, record->backtrace[i]);
      }
    }
  } else {
    LOGD("No backtrace for leak #%d (backtrace_size=%d)", *ctx->leak_count + 1, record->backtrace_size);
  }
  
  (*ctx->leak_count)++;
  return true;  // 继续遍历
}

// 获取内存泄漏报告
char *memory_tracker_get_leak_report(void) {
  if (!g_initialized) {
    return strdup("Memory tracker not initialized");
  }

  // 先分配缓冲区
  size_t buffer_size = 4096;
  char *report = NULL;
  if (original_malloc != NULL) {
    report = (char *)original_malloc(buffer_size);
  } else {
    report = (char *)malloc(buffer_size);
  }
  
  if (report == NULL) {
    return NULL;
  }

  // 读取 total 统计（原子操作）
  uint64_t total_alloc_count = atomic_load_explicit(&g_total_alloc_count, memory_order_relaxed);
  uint64_t total_alloc_size = atomic_load_explicit(&g_total_alloc_size, memory_order_relaxed);
  uint64_t total_free_count = atomic_load_explicit(&g_total_free_count, memory_order_relaxed);
  uint64_t total_free_size = atomic_load_explicit(&g_total_free_size, memory_order_relaxed);
  
  // 计算 current 统计（遍历哈希表）
  uint64_t current_count, current_size;
  calculate_current_stats(&current_count, &current_size);

  int offset = 0;
  offset += snprintf(report + offset, buffer_size - offset,
                     "=== Memory Leak Report ===\n"
                     "Total Allocations: %llu (%llu bytes)\n"
                     "Total Frees: %llu (%llu bytes)\n"
                     "Current Leaks: %llu (%llu bytes)\n\n",
                     (unsigned long long)total_alloc_count,
                     (unsigned long long)total_alloc_size,
                     (unsigned long long)total_free_count,
                     (unsigned long long)total_free_size,
                     (unsigned long long)current_count,
                     (unsigned long long)current_size);

  // 使用哈希表遍历接口
  int leak_count = 0;
  leak_report_context_t ctx = {
    .report = &report,
    .buffer_size = &buffer_size,
    .offset = &offset,
    .leak_count = &leak_count,
    .max_leaks = 100
  };
  
  hash_table_foreach(leak_report_callback, &ctx);

  return report;
}

// 将内存泄漏报告导出到文件
int memory_tracker_dump_leak_report(const char *file_path) {
  if (!g_initialized || file_path == NULL) {
    return -1;
  }

  char *report = memory_tracker_get_leak_report();
  if (report == NULL) {
    return -1;
  }

  int fd = open(file_path, O_CREAT | O_WRONLY | O_CLOEXEC | O_TRUNC, S_IRUSR | S_IWUSR);
  if (fd < 0) {
    LOGE("Failed to open file: %s", file_path);
    if (original_free != NULL) {
      original_free(report);
    } else {
      free(report);
    }
    return -1;
  }

  ssize_t written = write(fd, report, strlen(report));
  close(fd);
  
  if (original_free != NULL) {
    original_free(report);
  } else {
    free(report);
  }

  if (written < 0) {
    LOGE("Failed to write to file: %s", file_path);
    return -1;
  }

  LOGI("Leak report dumped to: %s", file_path);
  return 0;
}

// 获取内存统计信息
void memory_tracker_get_stats(memory_stats_t *stats) {
  if (stats == NULL) return;

  // 读取 total 统计（原子操作）
  stats->total_alloc_count = atomic_load_explicit(&g_total_alloc_count, memory_order_relaxed);
  stats->total_alloc_size = atomic_load_explicit(&g_total_alloc_size, memory_order_relaxed);
  stats->total_free_count = atomic_load_explicit(&g_total_free_count, memory_order_relaxed);
  stats->total_free_size = atomic_load_explicit(&g_total_free_size, memory_order_relaxed);
  
  // 计算 current 统计（遍历哈希表）
  calculate_current_stats(&stats->current_alloc_count, &stats->current_alloc_size);
}

// 重置统计信息
void memory_tracker_reset_stats(void) {
  // 清空哈希表中的所有记录（不需要 free，因为使用内存池）
  hash_table_cleanup(NULL);  // 传 NULL，不释放 record
  
  // 重新初始化哈希表
  hash_table_init();
  
  // 清理内存池
  pool_cleanup(original_free);

  // 重置原子统计
  atomic_store_explicit(&g_total_alloc_count, 0, memory_order_relaxed);
  atomic_store_explicit(&g_total_alloc_size, 0, memory_order_relaxed);
  atomic_store_explicit(&g_total_free_count, 0, memory_order_relaxed);
  atomic_store_explicit(&g_total_free_size, 0, memory_order_relaxed);
  
  LOGI("Memory stats reset");
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
