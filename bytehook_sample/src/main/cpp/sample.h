#pragma once

#ifdef __cplusplus
extern "C" {
#endif

void sample_test_strlen(int benchmark);

// 内存分配测试函数 (C malloc/free)
void sample_alloc_memory(int count);
void sample_free_memory(int count);
void sample_free_all_memory(void);

// C++ new/delete 测试函数
void sample_alloc_with_new(int count);
void sample_alloc_with_new_array(int count);
void sample_alloc_objects(int count);
void sample_alloc_object_arrays(int count);

// 性能测试函数
void sample_run_perf_tests(void);
void sample_quick_benchmark(int iterations);

#ifdef __cplusplus
}
#endif
