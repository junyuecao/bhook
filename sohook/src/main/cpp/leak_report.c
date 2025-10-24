#include "leak_report.h"
#include "memory_tracker.h"
#include "memory_hash_table.h"

#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define LOG_TAG "LeakReport"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 初始报告缓冲区大小
#define INITIAL_REPORT_BUFFER_SIZE 4096

// 最大泄漏记录数
#define MAX_LEAKS_IN_REPORT 100

// 外部函数声明（从 memory_tracker.c）
extern bool leak_report_is_initialized(void);
extern void *leak_report_get_original_malloc(void);
extern void leak_report_get_original_free(void *);
extern void leak_report_get_stats(memory_stats_t *stats);

// 用于遍历的回调数据结构
typedef struct {
  char **report;
  size_t *buffer_size;
  int *offset;
  int *leak_count;
  int max_leaks;
  void *(*malloc_func)(size_t);
  void (*free_func)(void *);
} leak_report_context_t;

// 格式化单个栈帧
static int format_backtrace_frame(char *buffer, size_t size, void *addr, int index) {
  Dl_info info;
  if (dladdr(addr, &info) && info.dli_sname) {
    // 成功解析到符号名
    const char *symbol = info.dli_sname;
    const char *lib = info.dli_fname ? strrchr(info.dli_fname, '/') : NULL;
    lib = lib ? lib + 1 : info.dli_fname;

    // 计算偏移
    ptrdiff_t offset = (char *)addr - (char *)info.dli_saddr;
    
    return snprintf(buffer, size, "    #%d: %p %s+%td (%s)\n", 
                    index, addr, symbol, offset, lib ? lib : "?");
  } else {
    // 无法解析，只显示地址
    return snprintf(buffer, size, "    #%d: %p\n", index, addr);
  }
}

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
      // 计算符号解析后的长度（符号名最长256，库名最长256，加上格式化字符）
      // 格式: "    #%d: %p %s+%td (%s)\n"
      backtrace_needed += 512;  // 预留足够空间
    }
  }
  
  int needed = base_needed + backtrace_needed;
  
  // 检查缓冲区是否足够
  if ((size_t)*ctx->offset >= *ctx->buffer_size || 
      *ctx->buffer_size - *ctx->offset < (size_t)needed + 1) {
    // 需要扩展缓冲区，确保新大小足够
    size_t new_size = *ctx->buffer_size * 2;
    while (new_size - *ctx->offset < (size_t)needed + 1) {
      new_size *= 2;
    }
    
    char *new_report = NULL;
    if (ctx->malloc_func != NULL) {
      new_report = (char *)ctx->malloc_func(new_size);
      if (new_report != NULL) {
        memcpy(new_report, *ctx->report, *ctx->offset);
        if (ctx->free_func != NULL) {
          ctx->free_func(*ctx->report);
        }
      }
    }
    
    if (new_report == NULL) {
      LOGE("Failed to expand report buffer");
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
      // 确保有足够空间
      if ((size_t)*ctx->offset >= *ctx->buffer_size - 512) {
        LOGW("Buffer nearly full, stopping backtrace output");
        break;
      }
      
      // 格式化栈帧
      size_t remaining = *ctx->buffer_size - *ctx->offset;
      int written = format_backtrace_frame(*ctx->report + *ctx->offset, remaining, 
                                          record->backtrace[i], i);
      
      if (written > 0 && (size_t)written < remaining) {
        *ctx->offset += written;
      } else {
        LOGW("Buffer overflow prevented at frame %d", i);
        break;
      }
    }
  } else {
    LOGD("No backtrace for leak #%d (backtrace_size=%d)", 
         *ctx->leak_count + 1, record->backtrace_size);
  }
  
  (*ctx->leak_count)++;
  return true;  // 继续遍历
}

// 生成内存泄漏报告
char *leak_report_generate(void) {
  if (!leak_report_is_initialized()) {
    return strdup("Memory tracker not initialized");
  }

  // 获取原始 malloc/free 函数
  void *(*malloc_func)(size_t) = leak_report_get_original_malloc();
  void (*free_func)(void *) = (void (*)(void *))leak_report_get_original_free;
  
  // 先分配缓冲区
  size_t buffer_size = INITIAL_REPORT_BUFFER_SIZE;
  char *report = NULL;
  if (malloc_func != NULL) {
    report = (char *)malloc_func(buffer_size);
  } else {
    report = (char *)malloc(buffer_size);
  }
  
  if (report == NULL) {
    LOGE("Failed to allocate report buffer");
    return NULL;
  }

  // 获取统计信息
  memory_stats_t stats;
  leak_report_get_stats(&stats);

  // 写入报告头部
  int offset = 0;
  offset += snprintf(report + offset, buffer_size - offset,
                     "=== Memory Leak Report ===\n"
                     "Total Allocations: %llu (%llu bytes)\n"
                     "Total Frees: %llu (%llu bytes)\n"
                     "Current Leaks: %llu (%llu bytes)\n\n",
                     (unsigned long long)stats.total_alloc_count,
                     (unsigned long long)stats.total_alloc_size,
                     (unsigned long long)stats.total_free_count,
                     (unsigned long long)stats.total_free_size,
                     (unsigned long long)stats.current_alloc_count,
                     (unsigned long long)stats.current_alloc_size);

  // 使用哈希表遍历接口
  int leak_count = 0;
  leak_report_context_t ctx = {
    .report = &report,
    .buffer_size = &buffer_size,
    .offset = &offset,
    .leak_count = &leak_count,
    .max_leaks = MAX_LEAKS_IN_REPORT,
    .malloc_func = malloc_func,
    .free_func = free_func
  };
  
  hash_table_foreach(leak_report_callback, &ctx);

  return report;
}

// 将泄漏报告导出到文件
int leak_report_dump(const char *file_path) {
  if (!leak_report_is_initialized() || file_path == NULL) {
    LOGE("Invalid parameters for leak_report_dump");
    return -1;
  }

  char *report = leak_report_generate();
  if (report == NULL) {
    LOGE("Failed to generate leak report");
    return -1;
  }

  int fd = open(file_path, O_CREAT | O_WRONLY | O_CLOEXEC | O_TRUNC, 
                S_IRUSR | S_IWUSR);
  if (fd < 0) {
    LOGE("Failed to open file: %s", file_path);
    void (*free_func)(void *) = (void (*)(void *))leak_report_get_original_free;
    if (free_func != NULL) {
      free_func(report);
    } else {
      free(report);
    }
    return -1;
  }

  ssize_t written = write(fd, report, strlen(report));
  close(fd);
  
  void (*free_func)(void *) = (void (*)(void *))leak_report_get_original_free;
  if (free_func != NULL) {
    free_func(report);
  } else {
    free(report);
  }

  if (written < 0) {
    LOGE("Failed to write to file: %s", file_path);
    return -1;
  }

  LOGI("Leak report dumped to: %s (%zd bytes)", file_path, written);
  return 0;
}
