// Copyright (c) 2024 SoHook Memory Leak Detection
// Leak JSON generation

#ifndef SOHOOK_LEAK_JSON_H
#define SOHOOK_LEAK_JSON_H

#include <stdbool.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// 最大调用栈深度
#define MAX_BACKTRACE_SIZE 16

/**
 * 获取内存泄漏列表（JSON 格式）- 原始数据
 * @return JSON 字符串，调用者需要 free
 */
char *leak_json_get_leaks(void);

/**
 * 获取聚合后的内存泄漏列表（JSON 格式）- 按堆栈聚合并排序
 * @return JSON 字符串，调用者需要 free
 * 返回格式: [{"count":10,"totalSize":1024,"backtrace":["func1","func2"]}, ...]
 */
char *leak_json_get_leaks_aggregated(void);

/**
 * 清理符号缓存
 */
void leak_json_clear_symbol_cache(void);

#ifdef __cplusplus
}
#endif

#endif  // SOHOOK_LEAK_JSON_H
