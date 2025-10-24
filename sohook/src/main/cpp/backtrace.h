#ifndef BACKTRACE_H
#define BACKTRACE_H

#include <stddef.h>

/**
 * 捕获当前调用栈
 * 
 * 使用 libunwind 捕获当前线程的调用栈
 * 
 * @param buffer 存储栈帧地址的缓冲区
 * @param max_frames 最大帧数
 * @return 实际捕获的帧数
 */
int backtrace_capture(void **buffer, int max_frames);

#endif // BACKTRACE_H
