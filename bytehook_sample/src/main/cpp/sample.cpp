#include "sample.h"

#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunsafe-buffer-usage"
#pragma clang diagnostic ignored "-Wunsafe-buffer-usage-in-libc-call"
#define SAMPLE_TAG "bytehook_tag"

// 日志宏
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, SAMPLE_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, SAMPLE_TAG, __VA_ARGS__)

#pragma clang optimize off
void sample_test_strlen(int benchmark) {
  size_t len;
  if (!benchmark) LOGI("sample pre strlen");
  // 使用字符串字面量,保证以null结尾,strlen是安全的
  len = strlen(benchmark ? "1" : "bytehook manual test");  // NOLINT: 字符串字面量,已保证安全
  if (!benchmark) LOGI("sample post strlen = %zu", len);
}
#pragma clang optimize on

// 用于测试内存泄漏检测的函数
static void *g_test_ptrs[100] = {NULL};
static int g_test_ptr_count = 0;

// 禁用优化，确保malloc/free不会被内联
#pragma clang optimize off
void sample_alloc_memory(int count) {
  LOGI("sample_alloc_memory: allocating %d blocks", count);
  
  for (int i = 0; i < count && g_test_ptr_count < 100; i++) {
    // 分配不同大小的内存块
    size_t size = 64 + ((size_t)i * 16);
    void *ptr = malloc(size);
    
    if (ptr != NULL) {
      // 写入一些数据，确保内存被使用
      memset(ptr, 0xAB, size);
      g_test_ptrs[g_test_ptr_count++] = ptr;
      LOGI("  allocated %zu bytes at %p", size, ptr);
    } else {
      LOGE("  failed to allocate %zu bytes", size);
    }
  }
  
  LOGI("sample_alloc_memory: total allocated blocks = %d", g_test_ptr_count);
}

void sample_free_memory(int count) {
  LOGI("sample_free_memory: freeing %d blocks", count);
  
  int freed = 0;
  for (int i = 0; i < count && g_test_ptr_count > 0; i++) {
    // 先访问数组元素,再递减索引,避免负数索引
    int index = g_test_ptr_count - 1;
    void *ptr = g_test_ptrs[index];
    
    if (ptr != NULL) {
      free(ptr);
      g_test_ptrs[index] = NULL;
      freed++;
      LOGI("  freed block at %p", ptr);
    }
    
    g_test_ptr_count--;
  }
  
  LOGI("sample_free_memory: freed %d blocks, remaining = %d", freed, g_test_ptr_count);
}

void sample_free_all_memory(void) {
  LOGI("sample_free_all_memory: freeing all blocks");
  
  for (int i = 0; i < g_test_ptr_count; i++) {
    if (g_test_ptrs[i] != NULL) {
      free(g_test_ptrs[i]);
      g_test_ptrs[i] = NULL;
    }
  }
  
  LOGI("sample_free_all_memory: freed %d blocks", g_test_ptr_count);
  g_test_ptr_count = 0;
}
#pragma clang optimize on

#pragma clang diagnostic pop
