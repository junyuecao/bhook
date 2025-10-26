// Copyright (c) 2024 SoHook File Descriptor Leak Detection
// Test file descriptor operations header

#ifndef SOHOOK_TEST_FD_H
#define SOHOOK_TEST_FD_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 打开一个文件
 * @param path 文件路径
 * @param flags 打开标志
 * @return 文件描述符，失败返回-1
 */
int test_open_file(const char* path, int flags);

/**
 * 关闭文件描述符
 * @param fd 文件描述符
 * @return 0表示成功，-1表示失败
 */
int test_close_file(int fd);

/**
 * 使用fopen打开文件
 * @param path 文件路径
 * @param mode 打开模式
 * @return FILE指针，失败返回NULL
 */
void* test_fopen_file(const char* path, const char* mode);

/**
 * 使用fclose关闭文件
 * @param fp FILE指针
 * @return 0表示成功，EOF表示失败
 */
int test_fclose_file(void* fp);

/**
 * 打开多个文件（用于测试）
 * @param path_prefix 文件路径前缀
 * @param count 打开文件数量
 * @param fds 输出的文件描述符数组
 * @return 成功打开的文件数量
 */
int test_open_multiple(const char* path_prefix, int count, int* fds);

/**
 * 关闭多个文件
 * @param fds 文件描述符数组
 * @param count 数量
 * @return 成功关闭的文件数量
 */
int test_close_multiple(int* fds, int count);

/**
 * 故意泄漏文件描述符（用于测试泄漏检测）
 * @param path 文件路径
 * @return 泄漏的文件描述符
 */
int test_leak_fd(const char* path);

/**
 * 故意泄漏FILE指针（用于测试泄漏检测）
 * @param path 文件路径
 * @return 泄漏的FILE指针
 */
void* test_leak_file(const char* path);

/**
 * 写入文件并关闭
 * @param path 文件路径
 * @param data 要写入的数据
 * @param size 数据大小
 * @return 0表示成功，-1表示失败
 */
int test_write_and_close(const char* path, const char* data, int size);

/**
 * 读取文件并关闭
 * @param path 文件路径
 * @param buffer 读取缓冲区
 * @param size 缓冲区大小
 * @return 读取的字节数，失败返回-1
 */
int test_read_and_close(const char* path, char* buffer, int size);

#ifdef __cplusplus
}
#endif

#endif // SOHOOK_TEST_FD_H
