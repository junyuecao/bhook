#pragma once

#ifdef __cplusplus
extern "C" {
#endif

void sample_test_strlen(int benchmark);

// 内存分配测试函数
void sample_alloc_memory(int count);
void sample_free_memory(int count);
void sample_free_all_memory(void);

#ifdef __cplusplus
}
#endif
