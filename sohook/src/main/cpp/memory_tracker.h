// Copyright (c) 2024 SoHook Memory Leak Detection
// Memory tracker header file

#ifndef SOHOOK_MEMORY_TRACKER_H
#define SOHOOK_MEMORY_TRACKER_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// 内存统计信息结构
typedef struct {
  uint64_t total_alloc_count;    // 总分配次数
  uint64_t total_alloc_size;     // 总分配大小（字节）
  uint64_t total_free_count;     // 总释放次数
  uint64_t total_free_size;      // 总释放大小（字节）
  uint64_t current_alloc_count;  // 当前未释放的分配次数
  uint64_t current_alloc_size;   // 当前未释放的内存大小（字节）
} memory_stats_t;

// 内存分配记录结构
typedef struct memory_record {
  void *ptr;                     // 分配的内存地址
  size_t size;                   // 分配的大小
  void *backtrace[16];           // 调用栈（最多16帧）
  int backtrace_size;            // 调用栈深度
  struct memory_record *next;    // 链表下一个节点（用于哈希表冲突链）
} memory_record_t;

/**
 * 初始化内存追踪器
 * @param debug 是否开启调试模式
 * @param enable_backtrace 是否启用栈回溯（会影响性能）
 * @return 0表示成功，其他值表示失败
 */
int memory_tracker_init(bool debug, bool enable_backtrace);

/**
 * 开始追踪指定so库的内存分配
 * @param so_names so库名称数组
 * @param count 数组长度
 * @return 0表示成功，其他值表示失败
 */
int memory_tracker_hook(const char **so_names, int count);

/**
 * 停止追踪指定so库的内存分配
 * @param so_names so库名称数组
 * @param count 数组长度
 * @return 0表示成功，其他值表示失败
 */
int memory_tracker_unhook(const char **so_names, int count);

/**
 * 获取内存泄漏报告
 * @return 报告字符串，调用者需要free
 */
char *memory_tracker_get_leak_report(void);

/**
 * 将内存泄漏报告导出到文件
 * @param file_path 文件路径
 * @return 0表示成功，其他值表示失败
 */
int memory_tracker_dump_leak_report(const char *file_path);

/**
 * 获取内存统计信息
 * @param stats 输出参数，统计信息
 */
void memory_tracker_get_stats(memory_stats_t *stats);

/**
 * 重置统计信息
 */
void memory_tracker_reset_stats(void);

/**
 * 启用或禁用栈回溯
 * @param enable true 启用，false 禁用
 * 注意：启用栈回溯会显著影响性能（约 10-20x 慢）
 */
void memory_tracker_set_backtrace_enabled(bool enable);

/**
 * 检查栈回溯是否启用
 * @return true 已启用，false 未启用
 */
bool memory_tracker_is_backtrace_enabled(void);

/**
 * malloc的hook函数
 */
void *malloc_proxy(size_t size);

/**
 * calloc的hook函数
 */
void *calloc_proxy(size_t nmemb, size_t size);

/**
 * realloc的hook函数
 */
void *realloc_proxy(void *ptr, size_t size);

/**
 * free的hook函数
 */
void free_proxy(void *ptr);

#ifdef __cplusplus
}
#endif

#endif  // SOHOOK_MEMORY_TRACKER_H
