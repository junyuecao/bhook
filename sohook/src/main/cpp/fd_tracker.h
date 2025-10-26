// Copyright (c) 2024 SoHook File Descriptor Leak Detection
// File descriptor tracker header file

#ifndef SOHOOK_FD_TRACKER_H
#define SOHOOK_FD_TRACKER_H

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// 文件描述符统计信息结构
typedef struct {
  uint64_t total_open_count;    // 总打开次数
  uint64_t total_close_count;   // 总关闭次数
  uint64_t current_open_count;  // 当前未关闭的文件描述符数量
} fd_stats_t;

// 文件描述符记录结构
typedef struct fd_record {
  int fd;                        // 文件描述符
  char path[256];                // 文件路径
  int flags;                     // 打开标志
  struct fd_record *next;        // 链表下一个节点
} fd_record_t;

/**
 * 初始化文件描述符追踪器（随sohook一起初始化）
 * @param debug 是否开启调试模式
 * @return 0表示成功，其他值表示失败
 */
int fd_tracker_init(bool debug);

/**
 * 开始追踪指定so库的文件操作
 * @param so_names so库名称数组
 * @param count 数组长度
 * @return 0表示成功，其他值表示失败
 */
int fd_tracker_hook(const char **so_names, int count);

/**
 * 停止追踪指定so库的文件操作
 * @param so_names so库名称数组
 * @param count 数组长度
 * @return 0表示成功，其他值表示失败
 */
int fd_tracker_unhook(const char **so_names, int count);

/**
 * 停止追踪所有已hook的so库
 * @return 0表示成功，其他值表示失败
 */
int fd_tracker_unhook_all(void);

/**
 * 获取文件描述符泄漏报告
 * @return 报告字符串，调用者需要free
 */
char *fd_tracker_get_leak_report(void);

/**
 * 将文件描述符泄漏报告导出到文件
 * @param file_path 文件路径
 * @return 0表示成功，其他值表示失败
 */
int fd_tracker_dump_leak_report(const char *file_path);

/**
 * 获取文件描述符统计信息
 * @param stats 输出参数，统计信息
 */
void fd_tracker_get_stats(fd_stats_t *stats);

/**
 * 重置统计信息
 */
void fd_tracker_reset_stats(void);

/**
 * 获取文件描述符泄漏列表（JSON 格式）
 * @return JSON 字符串，调用者需要 free
 */
char *fd_tracker_get_leaks_json(void);

/**
 * open的hook函数
 */
int open_proxy(const char *pathname, int flags, ...);

/**
 * close的hook函数
 */
int close_proxy(int fd);

#ifdef __cplusplus
}
#endif

#endif  // SOHOOK_FD_TRACKER_H
