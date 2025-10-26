// Copyright (c) 2024 SoHook Memory Leak Detection
// Test C++ memory allocation header

#ifndef SOHOOK_TEST_MEMORY_CPP_H
#define SOHOOK_TEST_MEMORY_CPP_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 测试 operator new
 * @param size 分配大小
 * @return 分配的内存指针
 */
void* test_cpp_new(size_t size);

/**
 * 测试 operator delete
 * @param ptr 要释放的指针
 */
void test_cpp_delete(void* ptr);

/**
 * 测试 operator new[]
 * @param size 分配大小
 * @return 分配的内存指针
 */
void* test_cpp_new_array(size_t size);

/**
 * 测试 operator delete[]
 * @param ptr 要释放的指针
 */
void test_cpp_delete_array(void* ptr);

/**
 * 测试创建单个对象（new）
 * @return 对象指针
 */
void* test_cpp_new_object();

/**
 * 测试删除单个对象（delete）
 * @param ptr 对象指针
 */
void test_cpp_delete_object(void* ptr);

/**
 * 测试创建对象数组（new[]）
 * @param count 对象数量
 * @return 数组指针
 */
void* test_cpp_new_object_array(int count);

/**
 * 测试删除对象数组（delete[]）
 * @param ptr 数组指针
 */
void test_cpp_delete_object_array(void* ptr);

/**
 * 测试多次分配（new）
 * @param count 分配次数
 * @param size 每次分配大小
 * @return 最后一次分配的指针
 */
void* test_cpp_new_multiple(int count, size_t size);

/**
 * 测试创建多个对象
 * @param count 对象数量
 * @return 对象指针数组
 */
void** test_cpp_new_objects(int count);

/**
 * 测试删除多个对象
 * @param ptrs 对象指针数组
 * @param count 对象数量
 */
void test_cpp_delete_objects(void** ptrs, int count);

/**
 * 故意泄漏内存（new）- 用于测试泄漏检测
 * @param size 泄漏大小
 * @return 泄漏的指针
 */
void* test_cpp_leak_new(size_t size);

/**
 * 故意泄漏对象 - 用于测试泄漏检测
 * @return 泄漏的对象指针
 */
void* test_cpp_leak_object();

/**
 * 故意泄漏对象数组 - 用于测试泄漏检测
 * @param count 对象数量
 * @return 泄漏的数组指针
 */
void* test_cpp_leak_object_array(int count);

#ifdef __cplusplus
}
#endif

#endif // SOHOOK_TEST_MEMORY_CPP_H
