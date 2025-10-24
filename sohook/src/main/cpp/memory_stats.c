#include "memory_stats.h"
#include "memory_hash_table.h"

#include <android/log.h>
#include <stdatomic.h>
#include <string.h>

#define LOG_TAG "MemoryStats"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// 混合统计策略：
// - total_* 使用原子操作实时更新（用于性能测试）
// - current_* 延迟计算（遍历哈希表）
static _Atomic uint64_t g_total_alloc_count = 0;
static _Atomic uint64_t g_total_alloc_size = 0;
static _Atomic uint64_t g_total_free_count = 0;
static _Atomic uint64_t g_total_free_size = 0;

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

// 初始化统计模块
void memory_stats_init(void) {
  atomic_store_explicit(&g_total_alloc_count, 0, memory_order_relaxed);
  atomic_store_explicit(&g_total_alloc_size, 0, memory_order_relaxed);
  atomic_store_explicit(&g_total_free_count, 0, memory_order_relaxed);
  atomic_store_explicit(&g_total_free_size, 0, memory_order_relaxed);
}

// 更新分配统计
void memory_stats_update_alloc(size_t size) {
  atomic_fetch_add_explicit(&g_total_alloc_count, 1, memory_order_relaxed);
  atomic_fetch_add_explicit(&g_total_alloc_size, size, memory_order_relaxed);
}

// 更新释放统计
void memory_stats_update_free(size_t size) {
  atomic_fetch_add_explicit(&g_total_free_count, 1, memory_order_relaxed);
  atomic_fetch_add_explicit(&g_total_free_size, size, memory_order_relaxed);
}

// 获取统计信息
void memory_stats_get(memory_stats_t *stats) {
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
void memory_stats_reset(void) {
  atomic_store_explicit(&g_total_alloc_count, 0, memory_order_relaxed);
  atomic_store_explicit(&g_total_alloc_size, 0, memory_order_relaxed);
  atomic_store_explicit(&g_total_free_count, 0, memory_order_relaxed);
  atomic_store_explicit(&g_total_free_size, 0, memory_order_relaxed);
  
  LOGI("Memory stats reset");
}
