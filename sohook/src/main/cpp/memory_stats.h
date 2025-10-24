#ifndef MEMORY_STATS_H
#define MEMORY_STATS_H

#include <stdint.h>
#include <stddef.h>

// 内存统计信息结构（与 memory_tracker.h 中的定义一致）
typedef struct {
  uint64_t total_alloc_count;    // 总分配次数
  uint64_t total_alloc_size;     // 总分配大小（字节）
  uint64_t total_free_count;     // 总释放次数
  uint64_t total_free_size;      // 总释放大小（字节）
  uint64_t current_alloc_count;  // 当前未释放的分配次数
  uint64_t current_alloc_size;   // 当前未释放的内存大小（字节）
} memory_stats_t;

/**
 * 初始化统计模块
 */
void memory_stats_init(void);

/**
 * 更新分配统计
 * @param size 分配的大小
 */
void memory_stats_update_alloc(size_t size);

/**
 * 更新释放统计
 * @param size 释放的大小
 */
void memory_stats_update_free(size_t size);

/**
 * 获取统计信息
 * @param stats 输出参数，统计信息
 */
void memory_stats_get(memory_stats_t *stats);

/**
 * 重置统计信息
 */
void memory_stats_reset(void);

#endif // MEMORY_STATS_H
