// Copyright (c) 2024 SoHook File Descriptor Leak Detection
// File descriptor tracker implementation

#include "fd_tracker.h"

#include <android/log.h>
#include <bytehook.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define LOG_TAG "FdTracker"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 全局状态
static bool g_initialized = false;
static bool g_debug = false;
static pthread_mutex_t g_mutex = PTHREAD_MUTEX_INITIALIZER;

// 统计信息
static fd_stats_t g_stats = {0};

// 文件描述符记录链表
static fd_record_t *g_fd_list = NULL;

// 防止重入的标志
static __thread bool g_in_hook = false;

// 追踪调用深度，用于去重（防止 fopen->open 重复记录）
static __thread int g_open_depth = 0;
static __thread int g_close_depth = 0;

// bytehook stub数组
#define MAX_HOOKS 64
static bytehook_stub_t g_stubs[MAX_HOOKS];
static int g_stub_count = 0;

// ============================================
// 内部辅助函数
// ============================================

// 添加文件描述符记录
static void add_fd_record(int fd, const char *pathname, int flags) {
  pthread_mutex_lock(&g_mutex);
  g_in_hook = true;

  fd_record_t *record = (fd_record_t *)malloc(sizeof(fd_record_t));
  if (record == NULL) {
    LOGE("Failed to allocate fd_record");
    g_in_hook = false;
    pthread_mutex_unlock(&g_mutex);
    return;
  }

  record->fd = fd;
  record->flags = flags;
  strncpy(record->path, pathname ? pathname : "<unknown>", sizeof(record->path) - 1);
  record->path[sizeof(record->path) - 1] = '\0';

  // 添加到链表头部
  record->next = g_fd_list;
  g_fd_list = record;

  // 更新统计
  g_stats.total_open_count++;
  g_stats.current_open_count++;

  if (g_debug) {
    LOGD("FD opened: fd=%d, path=%s, flags=0x%x", fd, record->path, flags);
  }

  g_in_hook = false;
  pthread_mutex_unlock(&g_mutex);
}

// 移除文件描述符记录
static void remove_fd_record(int fd) {
  pthread_mutex_lock(&g_mutex);
  g_in_hook = true;

  fd_record_t *prev = NULL;
  fd_record_t *curr = g_fd_list;

  while (curr != NULL) {
    if (curr->fd == fd) {
      // 从链表中移除
      if (prev == NULL) {
        g_fd_list = curr->next;
      } else {
        prev->next = curr->next;
      }

      if (g_debug) {
        LOGD("FD closed: fd=%d, path=%s", fd, curr->path);
      }

      free(curr);

      // 更新统计
      g_stats.total_close_count++;
      if (g_stats.current_open_count > 0) {
        g_stats.current_open_count--;
      }

      g_in_hook = false;
      pthread_mutex_unlock(&g_mutex);
      return;
    }
    prev = curr;
    curr = curr->next;
  }

  // 未找到记录，可能是在hook之前打开的
  if (g_debug) {
    LOGD("FD closed (not tracked): fd=%d", fd);
  }

  g_in_hook = false;
  pthread_mutex_unlock(&g_mutex);
}

// ============================================
// Hook函数实现
// ============================================

int open_proxy(const char *pathname, int flags, ...) {
  // 增加打开深度
  g_open_depth++;
  
  // 处理可变参数（mode）
  mode_t mode = 0;
  if (flags & O_CREAT) {
    va_list args;
    va_start(args, flags);
    mode = va_arg(args, mode_t);
    va_end(args);
  }

  // 调用原始函数
  int fd;
  if (flags & O_CREAT) {
    fd = BYTEHOOK_CALL_PREV(open_proxy, int (*)(const char *, int, mode_t), pathname, flags, mode);
  } else {
    fd = BYTEHOOK_CALL_PREV(open_proxy, int (*)(const char *, int), pathname, flags);
  }

  // 只在深度为1时记录（避免 fopen->open 重复记录）
  if (fd >= 0 && g_initialized && !g_in_hook && g_open_depth == 1) {
    add_fd_record(fd, pathname, flags);
  }

  // 减少打开深度
  g_open_depth--;

  BYTEHOOK_POP_STACK();
  return fd;
}

int close_proxy(int fd) {
  // 增加关闭深度
  g_close_depth++;
  
  // 只在深度为1时移除记录（避免 fclose->close 重复记录）
  if (g_initialized && !g_in_hook && g_close_depth == 1) {
    remove_fd_record(fd);
  }

  // 调用原始函数
  int result = BYTEHOOK_CALL_PREV(close_proxy, int (*)(int), fd);

  // 减少关闭深度
  g_close_depth--;

  BYTEHOOK_POP_STACK();
  return result;
}

FILE* fopen_proxy(const char *pathname, const char *mode) {
  // 增加打开深度，防止底层的open重复记录
  g_open_depth++;
  
  // 调用原始函数（内部会调用open）
  FILE* fp = BYTEHOOK_CALL_PREV(fopen_proxy, FILE* (*)(const char *, const char *), pathname, mode);

  // 在顶层（深度为1）记录文件
  if (fp != NULL && g_initialized && !g_in_hook && g_open_depth == 1) {
    int fd = fileno(fp);
    if (fd >= 0) {
      // 根据mode确定flags
      int flags = 0;
      if (strchr(mode, 'r') && strchr(mode, '+')) {
        flags = O_RDWR;
      } else if (strchr(mode, 'r')) {
        flags = O_RDONLY;
      } else if (strchr(mode, 'w') || strchr(mode, 'a')) {
        flags = O_WRONLY;
        if (strchr(mode, '+')) {
          flags = O_RDWR;
        }
      }
      add_fd_record(fd, pathname, flags);
    }
  }

  // 减少打开深度
  g_open_depth--;

  BYTEHOOK_POP_STACK();
  return fp;
}

int fclose_proxy(FILE *fp) {
  // 增加关闭深度，防止底层的close重复记录
  g_close_depth++;
  
  // 在顶层（深度为1）移除记录
  if (fp != NULL && g_initialized && !g_in_hook && g_close_depth == 1) {
    int fd = fileno(fp);
    if (fd >= 0) {
      remove_fd_record(fd);
    }
  }

  // 调用原始函数（内部会调用close）
  int result = BYTEHOOK_CALL_PREV(fclose_proxy, int (*)(FILE *), fp);

  // 减少关闭深度
  g_close_depth--;

  BYTEHOOK_POP_STACK();
  return result;
}

// ============================================
// 公共API实现
// ============================================

int fd_tracker_init(bool debug) {
  pthread_mutex_lock(&g_mutex);

  if (g_initialized) {
    LOGW("FD tracker already initialized");
    pthread_mutex_unlock(&g_mutex);
    return 0;
  }

  g_debug = debug;
  g_initialized = true;

  // 初始化统计信息
  memset(&g_stats, 0, sizeof(fd_stats_t));

  LOGI("FD tracker initialized (debug=%d)", debug);
  pthread_mutex_unlock(&g_mutex);
  return 0;
}

int fd_tracker_hook(const char **so_names, int count) {
  if (!g_initialized) {
    LOGE("FD tracker not initialized");
    return -1;
  }

  if (so_names == NULL || count <= 0) {
    LOGE("Invalid parameters");
    return -1;
  }

  pthread_mutex_lock(&g_mutex);

  for (int i = 0; i < count; i++) {
    if (so_names[i] == NULL) {
      continue;
    }

    if (g_stub_count >= MAX_HOOKS - 6) {
      LOGE("Too many hooks, max=%d, current=%d", MAX_HOOKS, g_stub_count);
      pthread_mutex_unlock(&g_mutex);
      return -1;
    }

    // Hook open
    bytehook_stub_t stub_open =
        bytehook_hook_single(so_names[i], NULL, "open", (void *)open_proxy, NULL, NULL);
    if (stub_open != NULL) {
      g_stubs[g_stub_count++] = stub_open;
      LOGI("Hooked open in %s", so_names[i]);
    } else {
      LOGE("Failed to hook open in %s", so_names[i]);
    }
    bytehook_stub_t stub___open_real =
        bytehook_hook_single(so_names[i], NULL, "__open_real", (void *)open_proxy, NULL, NULL);
    if (stub___open_real != NULL) {
      g_stubs[g_stub_count++] = stub___open_real;
      LOGI("Hooked __open_real in %s", so_names[i]);
    } else {
      LOGE("Failed to hook __open_real in %s", so_names[i]);
    }
    bytehook_stub_t stub___open_2 =
        bytehook_hook_single(so_names[i], NULL, "__open_2", (void *)open_proxy, NULL, NULL);
    if (stub___open_2 != NULL) {
      g_stubs[g_stub_count++] = stub___open_2;
      LOGI("Hooked __open_2 in %s", so_names[i]);
    } else {
      LOGE("Failed to __open_2 open in %s", so_names[i]);
    }


    // Hook close
    bytehook_stub_t stub_close =
        bytehook_hook_single(so_names[i], NULL, "close", (void *)close_proxy, NULL, NULL);
    if (stub_close != NULL) {
      g_stubs[g_stub_count++] = stub_close;
      LOGI("Hooked close in %s", so_names[i]);
    } else {
      LOGE("Failed to hook close in %s", so_names[i]);
    }

    // Hook fopen
    bytehook_stub_t stub_fopen =
        bytehook_hook_single(so_names[i], NULL, "fopen", (void *)fopen_proxy, NULL, NULL);
    if (stub_fopen != NULL) {
      g_stubs[g_stub_count++] = stub_fopen;
      LOGI("Hooked fopen in %s", so_names[i]);
    } else {
      LOGE("Failed to hook fopen in %s", so_names[i]);
    }

    // Hook fclose
    bytehook_stub_t stub_fclose =
        bytehook_hook_single(so_names[i], NULL, "fclose", (void *)fclose_proxy, NULL, NULL);
    if (stub_fclose != NULL) {
      g_stubs[g_stub_count++] = stub_fclose;
      LOGI("Hooked fclose in %s", so_names[i]);
    } else {
      LOGE("Failed to hook fclose in %s", so_names[i]);
    }
  }

  pthread_mutex_unlock(&g_mutex);
  return 0;
}

int fd_tracker_unhook(const char **so_names, int count) {
  if (!g_initialized) {
    LOGE("FD tracker not initialized");
    return -1;
  }

  if (so_names == NULL || count <= 0) {
    LOGE("Invalid parameters");
    return -1;
  }

  pthread_mutex_lock(&g_mutex);

  // bytehook不支持按so名称unhook，只能unhook所有
  // 这里简化处理，记录日志
  for (int i = 0; i < count; i++) {
    if (so_names[i] != NULL) {
      LOGI("Unhook requested for %s (will unhook all)", so_names[i]);
    }
  }

  pthread_mutex_unlock(&g_mutex);
  return fd_tracker_unhook_all();
}

int fd_tracker_unhook_all(void) {
  if (!g_initialized) {
    LOGE("FD tracker not initialized");
    return -1;
  }

  pthread_mutex_lock(&g_mutex);

  // Unhook所有stub
  for (int i = 0; i < g_stub_count; i++) {
    if (g_stubs[i] != NULL) {
      bytehook_unhook(g_stubs[i]);
      g_stubs[i] = NULL;
    }
  }
  g_stub_count = 0;

  LOGI("All FD hooks removed");
  pthread_mutex_unlock(&g_mutex);
  return 0;
}

char *fd_tracker_get_leak_report(void) {
  if (!g_initialized) {
    LOGE("FD tracker not initialized");
    return NULL;
  }

  pthread_mutex_lock(&g_mutex);

  // 计算需要的缓冲区大小
  size_t buffer_size = 4096;  // 初始大小
  char *report = (char *)malloc(buffer_size);
  if (report == NULL) {
    LOGE("Failed to allocate report buffer");
    pthread_mutex_unlock(&g_mutex);
    return NULL;
  }

  int offset = 0;
  offset += snprintf(report + offset, buffer_size - offset,
                     "=== File Descriptor Leak Report ===\n");
  offset += snprintf(report + offset, buffer_size - offset,
                     "Total opened: %llu\n", (unsigned long long)g_stats.total_open_count);
  offset += snprintf(report + offset, buffer_size - offset,
                     "Total closed: %llu\n", (unsigned long long)g_stats.total_close_count);
  offset += snprintf(report + offset, buffer_size - offset,
                     "Currently open: %llu\n\n", (unsigned long long)g_stats.current_open_count);

  if (g_stats.current_open_count > 0) {
    offset += snprintf(report + offset, buffer_size - offset, "Open file descriptors:\n");

    fd_record_t *curr = g_fd_list;
    int index = 1;
    while (curr != NULL) {
      // 检查缓冲区是否足够
      if (buffer_size - offset < 512) {
        size_t new_size = buffer_size * 2;
        char *new_report = (char *)realloc(report, new_size);
        if (new_report == NULL) {
          LOGE("Failed to reallocate report buffer");
          free(report);
          pthread_mutex_unlock(&g_mutex);
          return NULL;
        }
        report = new_report;
        buffer_size = new_size;
      }

      offset += snprintf(report + offset, buffer_size - offset,
                         "[%d] FD=%d, Path=%s, Flags=0x%x\n",
                         index++, curr->fd, curr->path, curr->flags);
      curr = curr->next;
    }
  } else {
    offset += snprintf(report + offset, buffer_size - offset, "No file descriptor leaks detected.\n");
  }

  pthread_mutex_unlock(&g_mutex);
  return report;
}

int fd_tracker_dump_leak_report(const char *file_path) {
  if (file_path == NULL) {
    LOGE("Invalid file path");
    return -1;
  }

  char *report = fd_tracker_get_leak_report();
  if (report == NULL) {
    return -1;
  }

  FILE *fp = fopen(file_path, "w");
  if (fp == NULL) {
    LOGE("Failed to open file: %s", file_path);
    free(report);
    return -1;
  }

  fprintf(fp, "%s", report);
  fclose(fp);
  free(report);

  LOGI("Leak report dumped to: %s", file_path);
  return 0;
}

void fd_tracker_get_stats(fd_stats_t *stats) {
  if (stats == NULL) {
    return;
  }

  pthread_mutex_lock(&g_mutex);
  memcpy(stats, &g_stats, sizeof(fd_stats_t));
  pthread_mutex_unlock(&g_mutex);
}

void fd_tracker_reset_stats(void) {
  pthread_mutex_lock(&g_mutex);
  memset(&g_stats, 0, sizeof(fd_stats_t));
  LOGI("FD stats reset");
  pthread_mutex_unlock(&g_mutex);
}

char *fd_tracker_get_leaks_json(void) {
  if (!g_initialized) {
    LOGE("FD tracker not initialized");
    return NULL;
  }

  pthread_mutex_lock(&g_mutex);

  // 计算需要的缓冲区大小
  size_t buffer_size = 4096;
  char *json = (char *)malloc(buffer_size);
  if (json == NULL) {
    LOGE("Failed to allocate JSON buffer");
    pthread_mutex_unlock(&g_mutex);
    return NULL;
  }

  int offset = 0;
  offset += snprintf(json + offset, buffer_size - offset, "[");

  fd_record_t *curr = g_fd_list;
  bool first = true;
  while (curr != NULL) {
    // 检查缓冲区是否足够
    if (buffer_size - offset < 512) {
      size_t new_size = buffer_size * 2;
      char *new_json = (char *)realloc(json, new_size);
      if (new_json == NULL) {
        LOGE("Failed to reallocate JSON buffer");
        free(json);
        pthread_mutex_unlock(&g_mutex);
        return NULL;
      }
      json = new_json;
      buffer_size = new_size;
    }

    if (!first) {
      offset += snprintf(json + offset, buffer_size - offset, ",");
    }
    first = false;

    offset += snprintf(json + offset, buffer_size - offset,
                       "{\"fd\":%d,\"path\":\"%s\",\"flags\":%d}",
                       curr->fd, curr->path, curr->flags);
    curr = curr->next;
  }

  offset += snprintf(json + offset, buffer_size - offset, "]");

  pthread_mutex_unlock(&g_mutex);
  return json;
}
