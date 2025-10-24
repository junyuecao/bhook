// Copyright (c) 2024 SoHook Memory Leak Detection
// Hash table for memory record storage

#ifndef MEMORY_HASH_TABLE_H
#define MEMORY_HASH_TABLE_H

#include <pthread.h>
#include <stdbool.h>
#include <stddef.h>

#include "memory_tracker.h"

#ifdef __cplusplus
extern "C" {
#endif

// 哈希表配置
#define HASH_TABLE_SIZE 10007  // 质数，减少冲突

// 哈希桶结构（分段锁）
typedef struct hash_bucket {
  memory_record_t *head;
  pthread_mutex_t lock;
} hash_bucket_t;

// 哈希表接口

/**
 * 初始化哈希表
 * @return 0 成功，-1 失败
 */
int hash_table_init(void);

/**
 * 清理哈希表，释放所有资源
 * @param free_func 用于释放 memory_record_t 的函数指针
 */
void hash_table_cleanup(void (*free_func)(void *));

/**
 * 添加内存记录到哈希表
 * @param record 内存记录指针
 * @return 0 成功，-1 失败
 */
int hash_table_add(memory_record_t *record);

/**
 * 从哈希表中移除内存记录
 * @param ptr 内存地址
 * @param free_func 用于释放 memory_record_t 的函数指针
 * @return 找到的记录（需要调用者获取 size 后释放），NULL 表示未找到
 */
memory_record_t *hash_table_remove(void *ptr);

/**
 * 遍历哈希表中的所有记录
 * @param callback 回调函数，返回 false 停止遍历
 * @param user_data 用户数据
 */
void hash_table_foreach(bool (*callback)(memory_record_t *record, void *user_data), void *user_data);

/**
 * 获取哈希表统计信息
 * @param total_records 总记录数（输出参数）
 * @param max_bucket_size 最大桶大小（输出参数）
 * @param avg_bucket_size 平均桶大小（输出参数）
 */
void hash_table_get_stats(size_t *total_records, size_t *max_bucket_size, double *avg_bucket_size);

#ifdef __cplusplus
}
#endif

#endif  // MEMORY_HASH_TABLE_H
