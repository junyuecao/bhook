#include "sample.h"

#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>
#include <pthread.h>
#include <time.h>

#pragma clang diagnostic push
#define SAMPLE_TAG "bytehook_tag"
#define PERF_TAG "SamplePerf"

// 日志宏
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, SAMPLE_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, SAMPLE_TAG, __VA_ARGS__)
#define PERF_LOGI(...) __android_log_print(ANDROID_LOG_INFO, PERF_TAG, __VA_ARGS__)

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
#define MAX_TEST_PTRS 10000
static void *g_test_ptrs[MAX_TEST_PTRS] = {NULL};
static int g_test_ptr_count = 0;

// 禁用优化，确保malloc/free不会被内联
#pragma clang optimize off
void sample_alloc_memory(int count) {
  LOGI("sample_alloc_memory: allocating %d blocks", count);
  
  for (int i = 0; i < count && g_test_ptr_count < MAX_TEST_PTRS; i++) {
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

// ============================================================================
// C++ new/delete 测试函数
// ============================================================================

// 测试类
class TestObject {
public:
    int data[10];
    
    TestObject() {
        LOGI("TestObject constructor called");
        for (int i = 0; i < 10; i++) {
            data[i] = i;
        }
    }
    
    ~TestObject() {
        LOGI("TestObject destructor called");
    }
};

// 使用 operator new 分配内存
void sample_alloc_with_new(int count) {
  LOGI("sample_alloc_with_new: allocating %d blocks using operator new", count);
  
  for (int i = 0; i < count && g_test_ptr_count < MAX_TEST_PTRS; i++) {
    // 使用 operator new 分配
    size_t size = 128 + ((size_t)i * 32);
    void *ptr = ::operator new(size);
    
    if (ptr != NULL) {
      // 写入一些数据
      memset(ptr, 0xCD, size);
      g_test_ptrs[g_test_ptr_count++] = ptr;
      LOGI("  allocated %zu bytes at %p (operator new)", size, ptr);
    } else {
      LOGE("  failed to allocate %zu bytes (operator new)", size);
    }
  }
  
  LOGI("sample_alloc_with_new: total allocated blocks = %d", g_test_ptr_count);
}

// 使用 operator new[] 分配数组
void sample_alloc_with_new_array(int count) {
  LOGI("sample_alloc_with_new_array: allocating %d arrays using operator new[]", count);
  
  for (int i = 0; i < count && g_test_ptr_count < MAX_TEST_PTRS; i++) {
    // 使用 operator new[] 分配
    size_t size = 256 + ((size_t)i * 64);
    void *ptr = ::operator new[](size);
    
    if (ptr != NULL) {
      // 写入一些数据
      memset(ptr, 0xEF, size);
      g_test_ptrs[g_test_ptr_count++] = ptr;
      LOGI("  allocated %zu bytes at %p (operator new[])", size, ptr);
    } else {
      LOGE("  failed to allocate %zu bytes (operator new[])", size);
    }
  }
  
  LOGI("sample_alloc_with_new_array: total allocated blocks = %d", g_test_ptr_count);
}

// 使用 new 创建对象
void sample_alloc_objects(int count) {
  LOGI("sample_alloc_objects: creating %d TestObjects using new", count);
  
  for (int i = 0; i < count && g_test_ptr_count < MAX_TEST_PTRS; i++) {
    // 使用 new 创建对象
    TestObject *obj = new TestObject();
    
    if (obj != NULL) {
      g_test_ptrs[g_test_ptr_count++] = obj;
      LOGI("  created TestObject at %p", (void *)obj);
    } else {
      LOGE("  failed to create TestObject");
    }
  }
  
  LOGI("sample_alloc_objects: total created objects = %d", g_test_ptr_count);
}

// 使用 new[] 创建对象数组
void sample_alloc_object_arrays(int count) {
  LOGI("sample_alloc_object_arrays: creating %d TestObject arrays using new[]", count);
  
  for (int i = 0; i < count && g_test_ptr_count < MAX_TEST_PTRS; i++) {
    // 使用 new[] 创建对象数组
    int array_size = 3 + (i % 5);  // 3-7 个对象
    TestObject *arr = new TestObject[array_size];
    
    if (arr != NULL) {
      g_test_ptrs[g_test_ptr_count++] = arr;
      LOGI("  created TestObject[%d] at %p", array_size, (void *)arr);
    } else {
      LOGE("  failed to create TestObject array");
    }
  }
  
  LOGI("sample_alloc_object_arrays: total created arrays = %d", g_test_ptr_count);
}
#pragma clang optimize on

// ============================================================================
// 性能测试代码 - 测试被hook后的malloc/free性能
// ============================================================================

// 获取当前时间(微秒)
static inline uint64_t get_time_us(void) {
  struct timeval tv;
  gettimeofday(&tv, NULL);
  return (uint64_t)tv.tv_sec * 1000000 + (unsigned long)tv.tv_usec;
}

// 快速性能基准测试
#pragma clang optimize off
void sample_quick_benchmark(int iterations) {
  PERF_LOGI("╔════════════════════════════════════════╗");
  PERF_LOGI("║  Quick Benchmark (Hooked malloc/free) ║");
  PERF_LOGI("╚════════════════════════════════════════╝");
  PERF_LOGI("Running benchmark with %d iterations...", iterations);
  
  void **ptrs = (void **)malloc((unsigned long)iterations * sizeof(void *));
  if (ptrs == NULL) {
    LOGE("Failed to allocate test array");
    return;
  }

  // 测试malloc性能
  uint64_t malloc_start = get_time_us();
  for (int i = 0; i < iterations; i++) {
    ptrs[i] = malloc(64);
  }
  uint64_t malloc_end = get_time_us();

  // 测试free性能
  uint64_t free_start = get_time_us();
  for (int i = 0; i < iterations; i++) {
    free(ptrs[i]);
  }
  uint64_t free_end = get_time_us();

  free(ptrs);

  uint64_t malloc_time = malloc_end - malloc_start;
  uint64_t free_time = free_end - free_start;

  PERF_LOGI("╔════════════════════════════════════════╗");
  PERF_LOGI("║  Benchmark Results                     ║");
  PERF_LOGI("╠════════════════════════════════════════╣");
  PERF_LOGI("║  Iterations: %d", iterations);
  PERF_LOGI("║  Malloc: %.2f ms (%.3f μs/op)", 
       (double)malloc_time / 1000.0, (double)malloc_time / iterations);
  PERF_LOGI("║  Free:   %.2f ms (%.3f μs/op)", 
       (double)free_time / 1000.0, (double)free_time / iterations);
  PERF_LOGI("║  Total:  %.2f ms", (malloc_time + free_time) / 1000.0);
  PERF_LOGI("╚════════════════════════════════════════╝");
  PERF_LOGI("");
  PERF_LOGI("This measures malloc/free performance");
  PERF_LOGI("WITH memory tracking hook active.");
}

// 测试1: 批量分配后释放 (测试最坏情况)
static void test_bulk_alloc_then_free(int count) {
  PERF_LOGI("[Test] Bulk Alloc Then Free (%d blocks)", count);
  
  void **ptrs = (void **)malloc(count * sizeof(void *));
  if (ptrs == NULL) {
    LOGE("Failed to allocate test array");
    return;
  }

  // 先全部分配
  uint64_t alloc_start = get_time_us();
  for (int i = 0; i < count; i++) {
    ptrs[i] = malloc(64 + i % 128);  // 变化大小
  }
  uint64_t alloc_end = get_time_us();

  // 再全部释放 (这时链表最长,测试最坏情况)
  uint64_t free_start = get_time_us();
  for (int i = 0; i < count; i++) {
    free(ptrs[i]);
  }
  uint64_t free_end = get_time_us();

  free(ptrs);

  uint64_t alloc_time = alloc_end - alloc_start;
  uint64_t free_time = free_end - free_start;
  uint64_t total_time = alloc_time + free_time;

  PERF_LOGI("  Alloc phase: %.2f ms (%.3f μs/op)", 
       alloc_time / 1000.0, (double)alloc_time / count);
  PERF_LOGI("  Free phase:  %.2f ms (%.3f μs/op)", 
       free_time / 1000.0, (double)free_time / count);
  PERF_LOGI("  Total:       %.2f ms (%.3f μs/op)", 
       total_time / 1000.0, (double)total_time / (count * 2));
  PERF_LOGI("  Throughput:  %.0f ops/sec", 
       (count * 2) * 1000000.0 / total_time);
}

// 测试2: 随机分配和释放
static void test_random_alloc_free(int count) {
  PERF_LOGI("[Test] Random Alloc/Free (%d operations)", count);
  
  #define MAX_ACTIVE 1000
  void *active_ptrs[MAX_ACTIVE] = {NULL};
  int active_count = 0;

  uint64_t start = get_time_us();

  srand((unsigned int)time(NULL));
  for (int i = 0; i < count; i++) {
    // 50%概率分配, 50%概率释放
    if (active_count == 0 || (active_count < MAX_ACTIVE && rand() % 2 == 0)) {
      // 分配
      void *ptr = malloc(32 + rand() % 256);
      if (ptr != NULL && active_count < MAX_ACTIVE) {
        active_ptrs[active_count++] = ptr;
      }
    } else {
      // 释放
      int idx = rand() % active_count;
      free(active_ptrs[idx]);
      active_ptrs[idx] = active_ptrs[--active_count];
    }
  }

  // 清理剩余内存
  for (int i = 0; i < active_count; i++) {
    free(active_ptrs[i]);
  }

  uint64_t end = get_time_us();
  uint64_t total_time = end - start;

  PERF_LOGI("  Total time:  %.2f ms", total_time / 1000.0);
  PERF_LOGI("  Avg time:    %.3f μs/op", (double)total_time / count);
  PERF_LOGI("  Throughput:  %.0f ops/sec", count * 1000000.0 / total_time);
}

// 测试3: 多线程并发测试
typedef struct {
  int thread_id;
  int operations;
  uint64_t elapsed_us;
} thread_test_arg_t;

static void *thread_test_func(void *arg) {
  thread_test_arg_t *test_arg = (thread_test_arg_t *)arg;
  
  uint64_t start = get_time_us();

  void *ptrs[10];
  for (int i = 0; i < test_arg->operations; i++) {
    // 分配10个
    for (int j = 0; j < 10; j++) {
      ptrs[j] = malloc(64);
    }
    // 释放10个
    for (int j = 0; j < 10; j++) {
      free(ptrs[j]);
    }
  }

  uint64_t end = get_time_us();
  test_arg->elapsed_us = end - start;

  PERF_LOGI("  Thread %d completed: %.2f ms", 
       test_arg->thread_id, test_arg->elapsed_us / 1000.0);
  
  return NULL;
}

static void test_multithread(int num_threads, int ops_per_thread) {
  PERF_LOGI("[Test] Multi-thread (%d threads, %d ops each)", 
       num_threads, ops_per_thread);
  
  pthread_t threads[32];
  thread_test_arg_t args[32];

  if (num_threads > 32) num_threads = 32;

  uint64_t start = get_time_us();

  // 启动线程
  for (int i = 0; i < num_threads; i++) {
    args[i].thread_id = i;
    args[i].operations = ops_per_thread;
    args[i].elapsed_us = 0;
    pthread_create(&threads[i], NULL, thread_test_func, &args[i]);
  }

  // 等待完成
  for (int i = 0; i < num_threads; i++) {
    pthread_join(threads[i], NULL);
  }

  uint64_t end = get_time_us();
  uint64_t total_time = end - start;

  // 计算平均线程时间
  uint64_t avg_thread_time = 0;
  for (int i = 0; i < num_threads; i++) {
    avg_thread_time += args[i].elapsed_us;
  }
  avg_thread_time /= num_threads;

  int total_ops = num_threads * ops_per_thread * 10 * 2;
  PERF_LOGI("  Wall time:       %.2f ms", total_time / 1000.0);
  PERF_LOGI("  Avg thread time: %.2f ms", avg_thread_time / 1000.0);
  PERF_LOGI("  Total ops:       %d", total_ops);
  PERF_LOGI("  Throughput:      %.0f ops/sec", 
       total_ops * 1000000.0 / total_time);
}

// 完整性能测试套件
void sample_run_perf_tests(void) {
  PERF_LOGI("╔════════════════════════════════════════╗");
  PERF_LOGI("║  Memory Tracker Performance Test      ║");
  PERF_LOGI("║  (Testing HOOKED malloc/free)          ║");
  PERF_LOGI("╚════════════════════════════════════════╝");
  PERF_LOGI("");

  // 测试1: 批量分配后释放
  PERF_LOGI("\n[Test 1] Bulk Alloc Then Free");
  test_bulk_alloc_then_free(5000);
  sleep(1);

  // 测试2: 随机分配释放
  PERF_LOGI("\n[Test 2] Random Pattern");
  test_random_alloc_free(10000);
  sleep(1);

  // 测试3: 多线程测试
  PERF_LOGI("\n[Test 3] Multi-thread");
  test_multithread(4, 2500);

  PERF_LOGI("\n╔════════════════════════════════════════╗");
  PERF_LOGI("║  Performance Test Completed            ║");
  PERF_LOGI("╚════════════════════════════════════════╝");
  PERF_LOGI("");
  PERF_LOGI("All allocations were tracked by SoHook.");
  PERF_LOGI("Check memory stats to see the results.");
}
#pragma clang optimize on

// FD泄漏测试函数
#pragma clang optimize off
void sample_leak_file_descriptors(int count, const char *path_prefix) {
  LOGI("sample_leak_file_descriptors: leaking %d file descriptors", count);
  
  for (int i = 0; i < count; i++) {
    char path[256];
    snprintf(path, sizeof(path), "%s_open_%d.tmp", path_prefix, i);
    
    // 使用open打开文件但不关闭（故意泄漏）
    int fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
    if (fd >= 0) {
      // 写入一些数据
      const char *data = "FD leak test data\n";
      write(fd, data, strlen(data));
      // 故意不调用 close(fd)，造成泄漏
      LOGI("Leaked FD %d for file: %s", fd, path);
    } else {
      LOGE("Failed to open file: %s", path);
    }
  }
}

void sample_leak_file_pointers(int count, const char *path_prefix) {
  LOGI("sample_leak_file_pointers: leaking %d FILE pointers", count);
  
  for (int i = 0; i < count; i++) {
    char path[256];
    snprintf(path, sizeof(path), "%s_fopen_%d.tmp", path_prefix, i);
    
    // 使用fopen打开文件但不关闭（故意泄漏）
    FILE *fp = fopen(path, "w");
    if (fp != NULL) {
      // 写入一些数据
      fprintf(fp, "FILE* leak test data\n");
      fflush(fp);
      // 故意不调用 fclose(fp)，造成泄漏
      int fd = fileno(fp);
      LOGI("Leaked FILE* (FD %d) for file: %s", fd, path);
    } else {
      LOGE("Failed to fopen file: %s", path);
    }
  }
}
#pragma clang optimize on

#pragma clang diagnostic pop
