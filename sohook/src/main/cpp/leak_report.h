#ifndef LEAK_REPORT_H
#define LEAK_REPORT_H

#include <stddef.h>

/**
 * 生成内存泄漏报告
 * 
 * 遍历所有未释放的内存记录，生成详细的泄漏报告
 * 包括地址、大小、调用栈等信息
 * 
 * @return 报告字符串，调用者需要使用 free() 释放
 *         如果失败返回 NULL 或错误信息字符串
 */
char *leak_report_generate(void);

/**
 * 将泄漏报告导出到文件
 * 
 * @param file_path 文件路径
 * @return 0 表示成功，-1 表示失败
 */
int leak_report_dump(const char *file_path);

#endif // LEAK_REPORT_H
